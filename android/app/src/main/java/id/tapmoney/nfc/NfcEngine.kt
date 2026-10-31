package id.tapmoney.nfc

import android.nfc.Tag
import id.tapmoney.model.CardBalanceResult

object NfcEngine {
    fun readCard(tag: Tag): CardBalanceResult? = CardParser.parse(tag)

    fun parseDetailed(tag: Tag): CardParser.Outcome = CardParser.parseDetailed(tag)

    fun isSuccess(resp: ByteArray?): Boolean = resp?.isSuccess() == true

    fun bytesToHex(bytes: ByteArray): String = bytes.toHexString()

    fun hexToBytes(hex: String): ByteArray = hex.hexToBytes()

    fun extractDigitsFromBcd(data: ByteArray, prefixes: Set<String> = emptySet()): String? =
        id.tapmoney.nfc.extractDigitsFromBcd(data, prefixes)

    fun reverseHex(bytes: ByteArray): String = id.tapmoney.nfc.reverseHex(bytes.toHexString())
}
