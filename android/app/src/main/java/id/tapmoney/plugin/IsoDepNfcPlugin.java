package id.tapmoney.plugin;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.NfcF;
import android.os.Bundle;
import android.util.Log;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONException;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@CapacitorPlugin(name = "IsoDepNfc")
public class IsoDepNfcPlugin extends Plugin {

    private static final String TAG = "IsoDepNfcPlugin";

    private NfcAdapter adapter;
    private final AtomicReference<Tag> currentTag = new AtomicReference<>(null);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object transceiveLock = new Object();

    private IsoDep connectedIsoDep;
    private NfcF connectedNfcF;
    private Tag activeTag;

    private boolean isScanning = false;

    private final NfcAdapter.ReaderCallback readerCallback = this::onTagDiscovered;

    @Override
    public void load() {
        adapter = NfcAdapter.getDefaultAdapter(getContext());
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        closeConnections();
        executor.shutdownNow();
    }

    @Override
    protected void handleOnPause() {
        super.handleOnPause();
        if (isScanning) {
            disableReaderMode();
        }
    }

    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        if (isScanning) {
            enableReaderMode();
        }
    }

    @PluginMethod
    public void isSupported(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("supported", adapter != null);
        ret.put("enabled", adapter != null && adapter.isEnabled());
        call.resolve(ret);
    }

    @PluginMethod
    public void startScanning(PluginCall call) {
        if (adapter == null) {
            call.reject("Hardware NFC tidak tersedia di perangkat ini.", "NO_NFC");
            return;
        }
        if (!adapter.isEnabled()) {
            call.reject("NFC dalam keadaan mati.", "NFC_DISABLED");
            return;
        }

        isScanning = true;
        enableReaderMode();
        call.resolve();
    }

    @PluginMethod
    public void stopScanning(PluginCall call) {
        isScanning = false;
        disableReaderMode();
        closeConnections();
        call.resolve();
    }

    @PluginMethod
    public void transceive(PluginCall call) {
        JSArray dataArray = call.getArray("data");
        if (dataArray == null || dataArray.length() == 0) {
            call.reject("data APDU harus berupa array bytes yang tidak kosong.");
            return;
        }

        Tag tag = currentTag.get();
        if (tag == null) {
            call.reject("Kartu NFC tidak ditemukan. Tempelkan kartu terlebih dahulu.");
            return;
        }

        final Integer timeout = call.getInt("timeout", 10000);
        final byte[] apduBytes;
        try {
            apduBytes = jsArrayToBytes(dataArray);
        } catch (IllegalArgumentException e) {
            call.reject(e.getMessage());
            return;
        }

        executor.execute(() -> {
            synchronized (transceiveLock) {
                try {
                    boolean isNfcF = Arrays.asList(tag.getTechList()).contains(NfcF.class.getName());
                    boolean isIsoDep = Arrays.asList(tag.getTechList()).contains(IsoDep.class.getName());

                    if (isIsoDep) {
                        if (connectedIsoDep == null || activeTag != tag || !connectedIsoDep.isConnected()) {
                            closeConnections();
                            connectedIsoDep = IsoDep.get(tag);
                            if (connectedIsoDep == null) {
                                call.reject("Tag tidak mendukung IsoDep.");
                                return;
                            }
                            connectedIsoDep.setTimeout(timeout != null ? timeout : 4000);
                            connectedIsoDep.connect();
                            activeTag = tag;
                        }

                        byte[] resp = connectedIsoDep.transceive(apduBytes);
                        JSObject result = new JSObject();
                        result.put("response", bytesToJsArray(resp));
                        call.resolve(result);
                    } else if (isNfcF) {
                        if (connectedNfcF == null || activeTag != tag || !connectedNfcF.isConnected()) {
                            closeConnections();
                            connectedNfcF = NfcF.get(tag);
                            if (connectedNfcF == null) {
                                call.reject("Tag tidak mendukung NfcF.");
                                return;
                            }
                            connectedNfcF.setTimeout(timeout != null ? timeout : 4000);
                            connectedNfcF.connect();
                            activeTag = tag;
                        }

                        byte[] resp = connectedNfcF.transceive(apduBytes);
                        JSObject result = new JSObject();
                        result.put("response", bytesToJsArray(resp));
                        call.resolve(result);
                    } else {
                        call.reject("Teknologi kartu tidak didukung (harus IsoDep atau NfcF).");
                    }
                        } catch (IOException e) {
                    closeConnections();
                    call.reject("Transceive gagal / kartu terlepas: " + e.getMessage(), e);
                } catch (Exception e) {
                    closeConnections();
                    call.reject("Kesalahan internal transceive: " + e.getMessage(), e);
                }
            }
        });
    }

    @PluginMethod
    public void closeTag(PluginCall call) {
        closeConnections();
        call.resolve();
    }

    private void enableReaderMode() {
        Activity activity = getActivity();
        if (activity == null || adapter == null) return;

        int flags = NfcAdapter.FLAG_READER_NFC_A |
                    NfcAdapter.FLAG_READER_NFC_B |
                    NfcAdapter.FLAG_READER_NFC_F |
                    NfcAdapter.FLAG_READER_NFC_V |
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS;

        activity.runOnUiThread(() -> {
            try {
                adapter.enableReaderMode(activity, readerCallback, flags, null);
            } catch (IllegalStateException e) {
                Log.w(TAG, "Gagal mengaktifkan reader mode", e);
            }
        });
    }

    private void disableReaderMode() {
        Activity activity = getActivity();
        if (activity == null || adapter == null) return;

        activity.runOnUiThread(() -> {
            try {
                adapter.disableReaderMode(activity);
            } catch (IllegalStateException e) {
                Log.w(TAG, "Gagal menonaktifkan reader mode", e);
            }
        });
    }

    private void onTagDiscovered(Tag tag) {
        if (tag == null) return;

        Tag prev = currentTag.getAndSet(tag);
        if (prev != null && prev != tag) {
            closeConnections();
        }

        JSObject event = new JSObject();
        event.put("id", bytesToJsArray(tag.getId()));
        JSArray techTypes = new JSArray();
        for (String tech : tag.getTechList()) techTypes.put(tech);
        event.put("techTypes", techTypes);

        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> notifyListeners("cardDiscovered", event, true));
        }
    }

    private void closeConnections() {
        synchronized (transceiveLock) {
            if (connectedIsoDep != null) {
                try {
                    if (connectedIsoDep.isConnected()) connectedIsoDep.close();
                } catch (IOException ignored) {}
                connectedIsoDep = null;
            }
            if (connectedNfcF != null) {
                try {
                    if (connectedNfcF.isConnected()) connectedNfcF.close();
                } catch (IOException ignored) {}
                connectedNfcF = null;
            }
            activeTag = null;
        }
    }

    private static byte[] jsArrayToBytes(JSArray array) throws IllegalArgumentException {
        try {
            byte[] bytes = new byte[array.length()];
            for (int i = 0; i < array.length(); i++) {
                int value = array.getInt(i);
                if (value < 0 || value > 255) {
                    throw new IllegalArgumentException("Nilai byte harus 0..255");
                }
                bytes[i] = (byte) value;
            }
            return bytes;
        } catch (JSONException e) {
            throw new IllegalArgumentException("Payload byte tidak valid", e);
        }
    }

    private static JSArray bytesToJsArray(byte[] bytes) {
        JSArray array = new JSArray();
        if (bytes != null) {
            for (byte b : bytes) {
                array.put(b & 0xFF);
            }
        }
        return array;
    }
}
