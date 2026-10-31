package id.tapmoney.nfc

import android.nfc.Tag
import id.tapmoney.model.CardBalanceResult

object NfcEngine {
    fun readCard(tag: Tag, settings: id.tapmoney.model.UserSettings? = null): CardBalanceResult? =
        CardParser.parse(tag, settings)

    fun parseDetailed(tag: Tag, settings: id.tapmoney.model.UserSettings? = null): CardParser.Outcome =
        CardParser.parseDetailed(tag, settings)

    fun isSuccess(resp: ByteArray?): Boolean = resp?.isSuccess() == true

    fun bytesToHex(bytes: ByteArray): String = bytes.toHexString()

    fun hexToBytes(hex: String): ByteArray = hex.hexToBytes()

    fun extractDigitsFromBcd(data: ByteArray, prefixes: Set<String> = emptySet()): String? =
        id.tapmoney.nfc.extractDigitsFromBcd(data, prefixes)

    fun reverseHex(bytes: ByteArray): String = id.tapmoney.nfc.reverseHex(bytes.toHexString())
}
