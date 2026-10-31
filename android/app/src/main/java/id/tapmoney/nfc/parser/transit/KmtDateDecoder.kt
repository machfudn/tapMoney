package id.tapmoney.nfc.parser.transit

import id.tapmoney.nfc.TxDates
import java.util.Calendar
import java.util.Date

object KmtDateDecoder {
    // 2000-01-01 01:00:00 WIB (UTC = 1999-12-31 18:00:00 = 946663200s)
    private const val EPOCH1_SECONDS = 946663200L
    // 2000-01-01 07:00:00 WIB (UTC = 2000-01-01 00:00:00 = 946684800s)
    private const val EPOCH2_SECONDS = 946684800L
    // 2019-01-01 00:00:00 WIB (UTC = 2018-12-31 17:00:00 = 1546275600s)
    private const val TRANSITION_SECONDS = 1546275600L

    fun decode(bytes: ByteArray, offset: Int = 0): Pair<String, String>? {
        if (bytes.size < offset + 4) return null
        val fullOffset = ((bytes[offset].toLong() and 0xFF) shl 24) or
                ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
                (bytes[offset + 3].toLong() and 0xFF)

        if (fullOffset == 0L) return null

        var targetSeconds = EPOCH2_SECONDS + fullOffset
        if (targetSeconds < TRANSITION_SECONDS) {
            targetSeconds = EPOCH1_SECONDS + fullOffset
        }

        val date = Date(targetSeconds * 1000L)
        val cal = Calendar.getInstance(TxDates.JAKARTA).apply { time = date }
        return Pair(TxDates.formatDate(cal.time), TxDates.formatTime(cal.time))
    }
}
