package id.tapmoney.util

import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardTransactionItem
import id.tapmoney.ui.formatCardNumber
import id.tapmoney.ui.formatRupiah
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ReceiptFormatter {

    const val DEFAULT_LINE_WIDTH = 32

    fun formatDateTimeWib(timestamp: Long, isEnglish: Boolean = false): String {
        val pattern = "dd MMM yyyy HH:mm:ss"
        val locale = if (isEnglish) Locale.US else Locale("id", "ID")
        val sdf = SimpleDateFormat(pattern, locale)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        return "${sdf.format(Date(timestamp))} WIB"
    }

    fun center(text: String, width: Int = DEFAULT_LINE_WIDTH): String {
        if (text.length >= width) return text.take(width)
        val leftPad = (width - text.length) / 2
        val rightPad = width - text.length - leftPad
        return " ".repeat(leftPad) + text + " ".repeat(rightPad)
    }

    fun twoColumn(left: String, right: String, width: Int = DEFAULT_LINE_WIDTH): String {
        val totalLen = left.length + right.length
        return if (totalLen + 1 <= width) {
            val spaceCount = width - totalLen
            left + " ".repeat(spaceCount) + right
        } else {
            val maxRight = (width - left.length - 1).coerceAtLeast(0)
            if (maxRight > 3) {
                left + " " + right.take(maxRight)
            } else {
                "$left\n${right.take(width).padStart(width)}"
            }
        }
    }

    fun resolveEndingBalances(card: CardBalanceResult): List<CardTransactionItem> {
        val items = card.history
        if (items.isEmpty()) return emptyList()
        val sorted = items.sortedWith(compareByDescending<CardTransactionItem> { "${it.date} ${it.time}" }).toMutableList()

        if (sorted[0].balance == null && card.balance > 0L) {
            sorted[0] = sorted[0].copy(balance = card.balance)
        }

        // Backward propagation (newer to older)
        for (i in 1 until sorted.size) {
            if (sorted[i].balance == null) {
                val prev = sorted[i - 1]
                if (prev.balance != null) {
                    val isPrevPayment = !prev.type.contains("Top", true) && !prev.type.contains("Isi", true)
                    val computed = if (isPrevPayment) {
                        prev.balance!! + prev.amount
                    } else {
                        prev.balance!! - prev.amount
                    }
                    if (computed in 0L..100_000_000L) {
                        sorted[i] = sorted[i].copy(balance = computed)
                    }
                }
            }
        }

        // Forward propagation (older to newer) if latest balance was null
        for (i in (sorted.size - 2) downTo 0) {
            if (sorted[i].balance == null) {
                val nextOlder = sorted[i + 1]
                if (nextOlder.balance != null) {
                    val isCurrentPayment = !sorted[i].type.contains("Top", true) && !sorted[i].type.contains("Isi", true)
                    val computed = if (isCurrentPayment) {
                        nextOlder.balance!! - sorted[i].amount
                    } else {
                        nextOlder.balance!! + sorted[i].amount
                    }
                    if (computed in 0L..100_000_000L) {
                        sorted[i] = sorted[i].copy(balance = computed)
                    }
                }
            }
        }

        return sorted
    }

    fun formatTextReceipt(
        card: CardBalanceResult,
        isEnglish: Boolean = false,
        maxTransactions: Int = 5,
        lineWidth: Int = DEFAULT_LINE_WIDTH
    ): String {
        val sb = StringBuilder()
        val sepDouble = "=".repeat(lineWidth)
        val sepSingle = "-".repeat(lineWidth)

        sb.append(sepDouble).append("\n")
        sb.append(center("TAPMONEY INFO", lineWidth)).append("\n")
        val subTitle = if (isEnglish) "OFFICIAL BALANCE RECEIPT" else "STRUK INFORMASI SALDO"
        sb.append(center(subTitle, lineWidth)).append("\n")
        sb.append(sepDouble).append("\n")

        val cardLabel = if (isEnglish) "Card" else "Kartu"
        val numberLabel = if (isEnglish) "Number" else "Nomor"
        val timeLabel = if (isEnglish) "Time" else "Waktu"

        sb.append(twoColumn("$cardLabel:", card.cardType, lineWidth)).append("\n")
        sb.append(twoColumn("$numberLabel:", formatCardNumber(card.cardNumber, mask = true), lineWidth)).append("\n")
        sb.append(twoColumn("$timeLabel:", formatDateTimeWib(card.timestamp, isEnglish), lineWidth)).append("\n")
        sb.append(sepSingle).append("\n")

        val balanceTitle = if (isEnglish) "CURRENT BALANCE:" else "SALDO SAAT INI:"
        sb.append(center(balanceTitle, lineWidth)).append("\n")
        sb.append(center(formatRupiah(card.balance), lineWidth)).append("\n")
        sb.append(sepSingle).append("\n")

        val historyTitle = if (isEnglish) "RECENT TRANSACTIONS:" else "RIWAYAT TRANSAKSI:"
        sb.append(center(historyTitle, lineWidth)).append("\n")
        sb.append(sepSingle).append("\n")

        val resolvedHistory = resolveEndingBalances(card)
        if (resolvedHistory.isEmpty()) {
            val emptyMsg = if (isEnglish) "(No history recorded)" else "(Tidak ada riwayat)"
            sb.append(center(emptyMsg, lineWidth)).append("\n")
        } else {
            resolvedHistory.take(maxTransactions).forEach { tx ->
                val isTopUp = tx.type.contains("Top", true) || tx.type.contains("Isi", true)
                val displayType = if (isTopUp) "Top Up" else (if (isEnglish) "Payment" else "Pembayaran")
                val dateTime = "${tx.date} ${tx.time}"
                sb.append(twoColumn(dateTime, displayType, lineWidth)).append("\n")
                val sign = if (isTopUp) "+" else "-"
                val amtLabel = if (isEnglish) "  Amount" else "  Nominal"
                sb.append(twoColumn(amtLabel, "$sign ${formatRupiah(tx.amount)}", lineWidth)).append("\n")

                if (tx.balance != null) {
                    val balLabel = if (isEnglish) "  Ending Bal" else "  Saldo Akhir"
                    sb.append(twoColumn(balLabel, formatRupiah(tx.balance), lineWidth)).append("\n")
                }
                val unknownText = if (isEnglish) "Unknown" else "Tidak dikenali"
                val locName = id.tapmoney.data.TerminalCatalog.resolveLocation(tx.terminalId, unknownLabel = unknownText)
                val termLabel = if (isEnglish) "  Location" else "  Lokasi"
                sb.append(twoColumn(termLabel, locName, lineWidth)).append("\n")
                sb.append(sepSingle).append("\n")
            }
        }

        val thanks = if (isEnglish) "THANK YOU" else "TERIMA KASIH"
        val notice = if (isEnglish) "Keep receipt as proof" else "Simpan struk sebagai bukti"
        sb.append(center(thanks, lineWidth)).append("\n")
        sb.append(center(notice, lineWidth)).append("\n")
        sb.append(sepDouble)

        return sb.toString()
    }

    fun buildEscPosPayload(
        card: CardBalanceResult,
        isEnglish: Boolean = false,
        maxTransactions: Int = 5,
        lineWidth: Int = DEFAULT_LINE_WIDTH
    ): ByteArray {
        val out = ByteArrayOutputStream()

        val esc: Byte = 0x1B
        val gs: Byte = 0x1D
        val lf: Byte = 0x0A

        fun cmd(vararg bytes: Byte) = out.write(byteArrayOf(*bytes))
        fun text(str: String) = out.write(str.toByteArray(Charsets.UTF_8))
        fun line(str: String = "") {
            if (str.isNotEmpty()) text(str)
            out.write(byteArrayOf(0x0D, 0x0A))
        }

        val sepDouble = "=".repeat(lineWidth)
        val sepSingle = "-".repeat(lineWidth)

        // 1. Initialize Printer
        cmd(esc, 0x40)

        // 2. Header (Centered, Double Height/Width, Bold)
        cmd(esc, 0x61, 0x01)
        cmd(gs, 0x21, 0x11)
        cmd(esc, 0x45, 0x01)
        line("TAPMONEY INFO")

        cmd(gs, 0x21, 0x00)
        cmd(esc, 0x45, 0x00)
        val subTitle = if (isEnglish) "OFFICIAL BALANCE RECEIPT" else "STRUK INFORMASI SALDO"
        line(subTitle)
        line(sepDouble)

        // 3. Card Meta (Left Align)
        cmd(esc, 0x61, 0x00)
        val cardLabel = if (isEnglish) "Card" else "Kartu"
        val numberLabel = if (isEnglish) "Number" else "Nomor"
        val timeLabel = if (isEnglish) "Time" else "Waktu"

        line(twoColumn("$cardLabel:", card.cardType, lineWidth))
        line(twoColumn("$numberLabel:", formatCardNumber(card.cardNumber, mask = true), lineWidth))
        line(twoColumn("$timeLabel:", formatDateTimeWib(card.timestamp, isEnglish), lineWidth))
        line(sepSingle)

        // 4. Balance Callout (Centered)
        cmd(esc, 0x61, 0x01)
        val balanceTitle = if (isEnglish) "CURRENT BALANCE:" else "SALDO SAAT INI:"
        line(balanceTitle)

        cmd(gs, 0x21, 0x11)
        cmd(esc, 0x45, 0x01)
        line(formatRupiah(card.balance))

        cmd(gs, 0x21, 0x00)
        cmd(esc, 0x45, 0x00)
        line(sepSingle)

        // 5. Transaction History (Left Align)
        cmd(esc, 0x61, 0x00)
        val historyTitle = if (isEnglish) "RECENT TRANSACTIONS:" else "RIWAYAT TRANSAKSI:"
        line(historyTitle)
        line(sepSingle)

        val resolvedHistory = resolveEndingBalances(card)
        if (resolvedHistory.isEmpty()) {
            val emptyMsg = if (isEnglish) "  (No history recorded)" else "  (Tidak ada riwayat)"
            line(emptyMsg)
            line(sepSingle)
        } else {
            resolvedHistory.take(maxTransactions).forEach { tx ->
                val dateTime = "${tx.date} ${tx.time}"
                val isTopUp = tx.type.contains("Top", true) || tx.type.contains("Isi", true)
                val displayType = if (isTopUp) "Top Up" else (if (isEnglish) "Payment" else "Pembayaran")
                line(twoColumn(dateTime, displayType, lineWidth))

                val sign = if (isTopUp) "+" else "-"
                val amtLabel = if (isEnglish) "  Amount" else "  Nominal"
                line(twoColumn(amtLabel, "$sign ${formatRupiah(tx.amount)}", lineWidth))

                if (tx.balance != null) {
                    val balLabel = if (isEnglish) "  Ending Bal" else "  Saldo Akhir"
                    line(twoColumn(balLabel, formatRupiah(tx.balance), lineWidth))
                }
                val unknownText = if (isEnglish) "Unknown" else "Tidak dikenali"
                val locName = id.tapmoney.data.TerminalCatalog.resolveLocation(tx.terminalId, unknownLabel = unknownText)
                val termLabel = if (isEnglish) "  Location" else "  Lokasi"
                line(twoColumn(termLabel, locName, lineWidth))
                line(sepSingle)
            }
        }

        // 6. Footer (Centered)
        cmd(esc, 0x61, 0x01)
        val thanks = if (isEnglish) "THANK YOU" else "TERIMA KASIH"
        val notice = if (isEnglish) "Keep receipt as proof" else "Simpan struk sebagai bukti"
        line(thanks)
        line(notice)
        line(sepDouble)

        // 7. Feed lines & Cut Paper
        cmd(lf, lf, lf, lf)
        cmd(gs, 0x56, 0x42, 0x00)

        return out.toByteArray()
    }
}
