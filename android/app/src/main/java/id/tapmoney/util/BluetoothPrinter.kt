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
    fun printPaired(adapter: BluetoothAdapter?, card: CardBalanceResult): Result<String> {
        return runCatching {
            val device: BluetoothDevice = adapter?.bondedDevices?.firstOrNull()
                ?: error("Tidak ada printer Bluetooth yang sudah dipasangkan")
            adapter.cancelDiscovery()
            device.createRfcommSocketToServiceRecord(sppUuid).use { socket ->
                socket.connect()
                socket.outputStream.use { output ->
                    output.write(byteArrayOf(0x1B, 0x40))
                    output.write(receiptText(card).replace("\n", "\r\n").toByteArray(Charsets.UTF_8))
                    output.write(byteArrayOf(0x0A, 0x0A, 0x0A))
                    output.flush()
                }
            }
            device.name ?: device.address
        }
    }
}
