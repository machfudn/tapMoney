package id.tapmoney.nfc

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TxDates {
    val JAKARTA: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")

    fun formatDate(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = JAKARTA
        return sdf.format(date)
    }

    fun formatTime(date: Date): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
        sdf.timeZone = JAKARTA
        return sdf.format(date)
    }

    fun parseBcdDateTime(ddMMyy: String, hhmmss: String): Date? {
        return try {
            val d = ddMMyy.substring(0, 2).toInt()
            val m = ddMMyy.substring(2, 4).toInt() - 1
            val y = ddMMyy.substring(4, 6).toInt() + 2000
            val h = hhmmss.substring(0, 2).toInt()
            val min = hhmmss.substring(2, 4).toInt()
            val s = hhmmss.substring(4, 6).toInt()

            val cal = Calendar.getInstance(JAKARTA)
            cal.set(y, if (m < 0) 0 else m, if (d < 1) 1 else d, h, min, s)
            cal.set(Calendar.MILLISECOND, 0)
            cal.time
        } catch (_: Exception) {
            null
        }
    }

    fun parseHexDateTime(dateHex: String, timeHex: String): Date? {
        return try {
            val d = Integer.parseInt(dateHex.substring(0, 2), 16)
            val m = Integer.parseInt(dateHex.substring(2, 4), 16) - 1
            val y = Integer.parseInt(dateHex.substring(4, 6), 16) + 2000
            val h = Integer.parseInt(timeHex.substring(0, 2), 16)
            val min = Integer.parseInt(timeHex.substring(2, 4), 16)
            val s = Integer.parseInt(timeHex.substring(4, 6), 16)

            val cal = Calendar.getInstance(JAKARTA)
            cal.set(y, if (m < 0) 0 else m, if (d < 1) 1 else d, h, min, s)
            cal.set(Calendar.MILLISECOND, 0)
            cal.time
        } catch (_: Exception) {
            null
        }
    }

    fun parseFlazzSeconds(seconds: Long): Date? {
        return try {
            if (seconds <= 0) return null
            val cal = Calendar.getInstance(JAKARTA)
            cal.set(1980, 0, 1, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.SECOND, seconds.toInt())
            cal.time
        } catch (_: Exception) {
            null
        }
    }
}
