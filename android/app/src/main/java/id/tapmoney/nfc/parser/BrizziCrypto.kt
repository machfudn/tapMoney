package id.tapmoney.nfc.parser

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object BrizziCrypto {

    private const val MASTER_SEED_PART1 = "C152153D5807784C721A433B5B59636D"
    private const val MASTER_SEED_PART2 = "C152153D5807784C"
    private const val ENC_KEY1 = "8DC0DC40FE1DC582CF7099E2AACFBC10"
    private const val ENC_KEY2 = "3C37029CA595FE4E7E62FCB2F7909B2C"
    private const val CONST_IV_B = "0000030080000000"
    private const val ZERO_IV = "0000000000000000"
    private const val RND_A_CONST = "1122334455667788"

    fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len - 1) {
            val byteVal = hex.substring(i, i + 2).toInt(16) and 0xFF
            data[i / 2] = byteVal.toByte()
            i += 2
        }
        return data
    }

    fun bytesToHex(bytes: ByteArray?): String {
        if (bytes == null) return ""
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val hex = Integer.toHexString(b.toInt() and 0xFF)
            if (hex.length == 1) sb.append('0')
            sb.append(hex)
        }
        return sb.toString().uppercase()
    }

    fun xorBytes(a: ByteArray, b: ByteArray): ByteArray {
        if (b.isEmpty()) return a
        val result = ByteArray(a.size)
        var bIdx = 0
        for (i in a.indices) {
            result[i] = (a[i].toInt() xor b[bIdx].toInt()).toByte()
            bIdx++
            if (bIdx >= b.size) bIdx = 0
        }
        return result
    }

    fun des3CbcEncrypt(dataHex: String, keyHex: String, ivHex: String): ByteArray? {
        val expandedKey = when (keyHex.length) {
            32 -> keyHex + keyHex.substring(0, 16)
            16 -> keyHex + keyHex + keyHex
            48 -> keyHex
            else -> "000000000000000000000000000000000000000000000000"
        }
        return try {
            val keySpec = SecretKeySpec(hexToBytes(expandedKey), "DESede")
            val ivSpec = IvParameterSpec(hexToBytes(ivHex))
            val cipher = Cipher.getInstance("DESede/CBC/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            cipher.doFinal(hexToBytes(dataHex))
        } catch (_: Exception) {
            null
        }
    }

    fun des3CbcDecrypt(data: ByteArray): ByteArray? {
        val keyStr = MASTER_SEED_PART1 + MASTER_SEED_PART2
        return try {
            val keySpec = SecretKeySpec(hexToBytes(keyStr), "DESede")
            val ivSpec = IvParameterSpec(hexToBytes(ZERO_IV))
            val cipher = Cipher.getInstance("DESede/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            cipher.doFinal(data)
        } catch (_: Exception) {
            null
        }
    }

    fun desEcbDecrypt(dataHex: String, keyHex: String): ByteArray? {
        return try {
            val keySpec = SecretKeySpec(hexToBytes(keyHex), "DES")
            val cipher = Cipher.getInstance("DES/ECB/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            cipher.doFinal(hexToBytes(dataHex))
        } catch (_: Exception) {
            null
        }
    }

    fun desEdeDedDecrypt(dataHex: String, keyHex: String): ByteArray? {
        if (keyHex.length < 32) return null
        val k1 = keyHex.substring(0, 16)
        val k2 = keyHex.substring(16, 32)
        return try {
            val step1 = bytesToHex(desEcbDecrypt(dataHex, k1))
            val keySpec2 = SecretKeySpec(hexToBytes(k2), "DES")
            val cipher2 = Cipher.getInstance("DES/ECB/NoPadding")
            cipher2.init(Cipher.ENCRYPT_MODE, keySpec2)
            val step2 = cipher2.doFinal(hexToBytes(step1))
            desEcbDecrypt(bytesToHex(step2), k1)
        } catch (_: Exception) {
            null
        }
    }

    fun computeAuthResponse(pan: String, uidHex: String, rndBEncHex: String): String? {
        return try {
            val cardString = (pan + uidHex + "FF").uppercase()
            if (cardString.length < 32) return null
            val cardDataHex = cardString.substring(0, 32)

            val decKey1 = des3CbcDecrypt(hexToBytes(ENC_KEY1)) ?: return null
            val key1Str = bytesToHex(decKey1).substring(0, 32)

            val intermediateBytes = des3CbcEncrypt(cardDataHex, key1Str, ZERO_IV) ?: return null
            val intermediateHex = bytesToHex(intermediateBytes).substring(0, 32)

            val decKey2 = des3CbcDecrypt(hexToBytes(ENC_KEY2)) ?: return null
            val key2Str = bytesToHex(decKey2).substring(0, 32)

            val sessionKeyBytes = des3CbcEncrypt(intermediateHex, key2Str, CONST_IV_B) ?: return null
            val sessionKeyHex = bytesToHex(sessionKeyBytes).substring(0, 32)

            val decRndBBytes = desEdeDedDecrypt(rndBEncHex, sessionKeyHex) ?: return null
            val decRndBHex = bytesToHex(xorBytes(decRndBBytes, hexToBytes(ZERO_IV)))
            if (decRndBHex.length < 16) return null

            val rotatedRndB = decRndBHex.substring(2, 16) + decRndBHex.substring(0, 2)

            val rndAXor = xorBytes(hexToBytes(RND_A_CONST), hexToBytes(rndBEncHex))
            val encRndABytes = desEdeDedDecrypt(bytesToHex(rndAXor), sessionKeyHex) ?: return null
            val encRndAHex = bytesToHex(encRndABytes)

            val rndBXor = xorBytes(hexToBytes(rotatedRndB), hexToBytes(encRndAHex))
            val encRndBBytes = desEdeDedDecrypt(bytesToHex(rndBXor), sessionKeyHex) ?: return null
            val encRndBHex = bytesToHex(encRndBBytes)

            (encRndAHex + encRndBHex).uppercase()
        } catch (_: Exception) {
            null
        }
    }
}
