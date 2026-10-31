package id.tapmoney.nfc.parser

import android.nfc.Tag
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.nfc.ApduChannel

interface EmoneyParser {
    val parserName: String
    fun canHandle(tag: Tag): Boolean
    fun parse(tag: Tag, channel: ApduChannel?, log: StringBuilder): CardBalanceResult?
}
