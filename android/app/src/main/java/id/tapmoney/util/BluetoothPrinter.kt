package id.tapmoney.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.ui.receiptText
import java.util.UUID

object BluetoothPrinter {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun printPaired(adapter: BluetoothAdapter?, card: CardBalanceResult, isEnglish: Boolean = false): Result<String> {
        return runCatching {
            val device: BluetoothDevice = adapter?.bondedDevices?.firstOrNull()
                ?: error(if (isEnglish) "No paired Bluetooth printer found" else "Tidak ada printer Bluetooth yang sudah dipasangkan")
            adapter.cancelDiscovery()
            val payload = ReceiptFormatter.buildEscPosPayload(card, isEnglish)
            device.createRfcommSocketToServiceRecord(sppUuid).use { socket ->
                socket.connect()
                socket.outputStream.use { output ->
                    output.write(payload)
                    output.flush()
                }
            }
            device.name ?: device.address
        }
    }
}
