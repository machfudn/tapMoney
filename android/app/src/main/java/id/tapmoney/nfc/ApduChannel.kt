package id.tapmoney.nfc

import android.nfc.tech.IsoDep

fun interface ApduChannel {
    fun transceive(command: ByteArray): ByteArray
}

fun IsoDep.asChannel(): ApduChannel = ApduChannel { transceive(it) }

fun ByteArray.toHexString(): String {
    val hexChars = "0123456789ABCDEF"
    val result = StringBuilder(size * 2)
    for (b in this) {
        val i = b.toInt() and 0xFF
        result.append(hexChars[i ushr 4])
        result.append(hexChars[i and 0x0F])
    }
    return result.toString()
}

fun String.hexToBytes(): ByteArray {
    val clean = replace("[^0-9A-Fa-f]".toRegex(), "")
    require(clean.length % 2 == 0) { "Hex APDU must contain complete bytes" }
    val result = ByteArray(clean.length / 2)
    for (i in result.indices) {
        result[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
    return result
}

fun ByteArray.isSuccess(): Boolean {
    if (size < 2) return false
    val sw1 = this[size - 2].toInt() and 0xFF
    val sw2 = this[size - 1].toInt() and 0xFF
    return (sw1 == 0x90 || sw1 == 0x91) && sw2 == 0x00
}

fun ByteArray.isDesfireSuccess(): Boolean = hasStatus(0x91, 0x00)

fun ByteArray.hasStatus(sw1: Int, sw2: Int): Boolean {
    if (size < 2) return false
    val actualSw1 = this[size - 2].toInt() and 0xFF
    val actualSw2 = this[size - 1].toInt() and 0xFF
    return actualSw1 == sw1 && actualSw2 == sw2
}

fun ByteArray.payload(): ByteArray = if (size >= 2) sliceArray(0 until size - 2) else ByteArray(0)

fun selectApplet(channel: ApduChannel, aid: ByteArray): Boolean {
    val selectCmd = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00) +
            byteArrayOf(aid.size.toByte()) +
            aid +
            byteArrayOf(0x00)
    return try {
        val resp = channel.transceive(selectCmd)
        resp.isSuccess()
    } catch (_: Exception) {
        false
    }
}

fun reverseHex(hex: String): String {
    val reversed = StringBuilder()
    for (i in hex.length - 2 downTo 0 step 2) {
        reversed.append(hex.substring(i, i + 2))
    }
    return reversed.toString()
}

fun extractDigitsFromBcd(data: ByteArray, prefixes: Set<String> = emptySet()): String? {
    for (offset in data.indices) {
        val end = minOf(data.size, offset + 10)
        val digits = buildString {
            for (index in offset until end) {
                val value = data[index].toInt() and 0xFF
                val hi = value ushr 4
                val lo = value and 0x0F
                if (hi > 9 || lo > 9) break
                append(hi).append(lo)
            }
        }
        if (digits.length >= 16) {
            val candidate = digits.take(16)
            if (prefixes.isEmpty() || prefixes.any(candidate::startsWith)) return candidate
        }
    }
    return null
}

fun extractDigitsFromAscii(data: ByteArray, prefixes: Set<String> = emptySet()): String? {
    val ascii = String(data, Charsets.US_ASCII)
    for (offset in 0..ascii.length - 16) {
        val candidate = ascii.substring(offset, offset + 16)
        if (candidate.all { it.isDigit() }) {
            if (prefixes.isEmpty() || prefixes.any(candidate::startsWith)) return candidate
        }
    }
    return null
}
