package id.tapmoney.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardTransactionItem
import id.tapmoney.ui.formatCardNumber
import id.tapmoney.ui.formatRupiah
import java.io.File
import java.io.FileOutputStream

object ReceiptExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    private const val COLOR_BLACK = 0xFF111827.toInt()
    private const val COLOR_WHITE = 0xFFFFFFFF.toInt()
    private const val COLOR_YELLOW = 0xFFFFE600.toInt()
    private const val COLOR_MUTED_BG = 0xFFF9FAFB.toInt()
    private const val COLOR_BORDER_LIGHT = 0xFFE5E7EB.toInt()
    private const val COLOR_GREEN = 0xFF059669.toInt()
    private const val COLOR_RED = 0xFFDC2626.toInt()
    private const val COLOR_GRAY_TEXT = 0xFF6B7280.toInt()

    fun createPdf(context: Context, card: CardBalanceResult, isEnglish: Boolean = false): android.net.Uri {
        val document = PdfDocument()

        val maxFirstPageRows = 12
        val maxSubsequentPageRows = 22

        val history = card.history
        val totalPages = if (history.size <= maxFirstPageRows) {
            1
        } else {
            1 + ((history.size - maxFirstPageRows + maxSubsequentPageRows - 1) / maxSubsequentPageRows)
        }

        var currentHistoryIndex = 0

        for (pageIndex in 1..totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val isFirstPage = pageIndex == 1
            val isLastPage = pageIndex == totalPages

            val rowsForThisPage = if (isFirstPage) {
                history.drop(currentHistoryIndex).take(maxFirstPageRows)
            } else {
                history.drop(currentHistoryIndex).take(maxSubsequentPageRows)
            }
            currentHistoryIndex += rowsForThisPage.size

            drawPage(
                canvas = canvas,
                card = card,
                pageIndex = pageIndex,
                totalPages = totalPages,
                rows = rowsForThisPage,
                isEnglish = isEnglish,
                isFirstPage = isFirstPage,
                isLastPage = isLastPage
            )

            document.finishPage(page)
        }

        val file = File(context.cacheDir, "tapmoney-${card.timestamp}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun drawPage(
        canvas: Canvas,
        card: CardBalanceResult,
        pageIndex: Int,
        totalPages: Int,
        rows: List<CardTransactionItem>,
        isEnglish: Boolean,
        isFirstPage: Boolean,
        isLastPage: Boolean
    ) {
        val cardLeft = 36f
        val cardTop = 36f
        val cardRight = 559f
        val cardBottom = 806f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.style = Paint.Style.FILL
        paint.color = COLOR_BLACK
        canvas.drawRect(cardLeft + 5f, cardTop + 5f, cardRight + 5f, cardBottom + 5f, paint)

        paint.color = COLOR_WHITE
        canvas.drawRect(cardLeft, cardTop, cardRight, cardBottom, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = COLOR_BLACK
        canvas.drawRect(cardLeft, cardTop, cardRight, cardBottom, paint)

        var currentY = cardTop

        if (isFirstPage) {
            val bannerHeight = 64f
            paint.style = Paint.Style.FILL
            paint.color = COLOR_YELLOW
            canvas.drawRect(cardLeft, currentY, cardRight, currentY + bannerHeight, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.color = COLOR_BLACK
            canvas.drawLine(cardLeft, currentY + bannerHeight, cardRight, currentY + bannerHeight, paint)

            paint.style = Paint.Style.FILL
            paint.color = COLOR_BLACK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 20f
            canvas.drawText("TAPMONEY INFO", cardLeft + 16f, currentY + 30f, paint)

            paint.textSize = 8.5f
            val subTitle = if (isEnglish) "OFFICIAL ELECTRONIC MONEY RECEIPT" else "STRUK INFORMASI SALDO ELEKTRONIK"
            canvas.drawText(subTitle, cardLeft + 16f, currentY + 48f, paint)

            val badgeWidth = 110f
            val badgeHeight = 26f
            val badgeX = cardRight - badgeWidth - 16f
            val badgeY = currentY + 19f
            paint.color = COLOR_BLACK
            canvas.drawRoundRect(RectF(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight), 4f, 4f, paint)

            paint.color = COLOR_WHITE
            paint.textSize = 8.5f
            paint.textAlign = Paint.Align.CENTER
            val badgeText = if (isEnglish) "NFC VERIFIED" else "TERVERIFIKASI NFC"
            canvas.drawText(badgeText, badgeX + badgeWidth / 2f, badgeY + 17f, paint)
            paint.textAlign = Paint.Align.LEFT

            currentY += bannerHeight

            val metaHeight = 28f
            paint.color = COLOR_MUTED_BG
            canvas.drawRect(cardLeft, currentY, cardRight, currentY + metaHeight, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = COLOR_BORDER_LIGHT
            canvas.drawLine(cardLeft, currentY + metaHeight, cardRight, currentY + metaHeight, paint)

            paint.style = Paint.Style.FILL
            paint.color = COLOR_GRAY_TEXT
            paint.textSize = 8f
            paint.typeface = Typeface.DEFAULT
            val dateLabel = if (isEnglish) "GENERATED AT: " else "WAKTU CETAK: "
            val dateVal = ReceiptFormatter.formatDateTimeWib(card.timestamp, isEnglish)
            canvas.drawText(dateLabel + dateVal, cardLeft + 16f, currentY + 18f, paint)

            paint.textAlign = Paint.Align.RIGHT
            val sourceText = if (isEnglish) "SOURCE: SMARTCARD CHIP NFC" else "SUMBER: CHIP NFC SMARTCARD"
            canvas.drawText(sourceText, cardRight - 16f, currentY + 18f, paint)
            paint.textAlign = Paint.Align.LEFT

            currentY += metaHeight + 14f

            val summaryTop = currentY
            val summaryHeight = 92f

            paint.color = COLOR_GRAY_TEXT
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val cardLabel = if (isEnglish) "CARD TYPE / ISSUER" else "JENIS KARTU / PENERBIT"
            canvas.drawText(cardLabel, cardLeft + 16f, summaryTop + 14f, paint)

            paint.color = COLOR_BLACK
            paint.textSize = 14f
            canvas.drawText(card.cardType, cardLeft + 16f, summaryTop + 34f, paint)

            paint.color = COLOR_GRAY_TEXT
            paint.textSize = 8f
            val panLabel = if (isEnglish) "CARD NUMBER (PAN)" else "NOMOR KARTU (PAN)"
            canvas.drawText(panLabel, cardLeft + 16f, summaryTop + 54f, paint)

            paint.color = COLOR_BLACK
            paint.textSize = 12f
            paint.typeface = Typeface.MONOSPACE
            val panFormatted = formatCardNumber(card.cardNumber, mask = false)
            canvas.drawText(panFormatted, cardLeft + 16f, summaryTop + 72f, paint)

            paint.typeface = Typeface.DEFAULT
            paint.textSize = 8.5f
            paint.color = COLOR_GRAY_TEXT
            val issuerStr = if (isEnglish) "Issuer: ${card.issuer.displayName}" else "Penerbit: ${card.issuer.displayName}"
            canvas.drawText(issuerStr, cardLeft + 16f, summaryTop + 88f, paint)

            val boxWidth = 200f
            val boxHeight = 84f
            val boxLeft = cardRight - boxWidth - 16f
            val boxTop = summaryTop + 4f

            paint.color = COLOR_BLACK
            paint.style = Paint.Style.FILL
            canvas.drawRect(boxLeft + 3f, boxTop + 3f, boxLeft + boxWidth + 3f, boxTop + boxHeight + 3f, paint)

            paint.color = COLOR_YELLOW
            canvas.drawRect(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = COLOR_BLACK
            canvas.drawRect(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight, paint)

            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 8f
            val balanceTitle = if (isEnglish) "CURRENT BALANCE" else "SALDO SAAT INI"
            canvas.drawText(balanceTitle, boxLeft + boxWidth / 2f, boxTop + 20f, paint)

            paint.textSize = 18f
            canvas.drawText(formatRupiah(card.balance), boxLeft + boxWidth / 2f, boxTop + 48f, paint)

            paint.textSize = 7.5f
            paint.color = COLOR_BLACK
            val verifiedStatus = if (isEnglish) "STORED VALUE ON CHIP" else "TERCATAT PADA CHIP"
            canvas.drawText(verifiedStatus, boxLeft + boxWidth / 2f, boxTop + 68f, paint)

            paint.textAlign = Paint.Align.LEFT
            currentY = summaryTop + summaryHeight + 14f

            paint.color = COLOR_BLACK
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val secTitle = if (isEnglish) "TRANSACTION HISTORY" else "RIWAYAT TRANSAKSI"
            canvas.drawText(secTitle, cardLeft + 16f, currentY + 12f, paint)

            val countBadge = "[ ${card.history.size} ${if (isEnglish) "TRANSACTIONS" else "TRANSAKSI"} ]"
            paint.textSize = 8.5f
            paint.color = COLOR_GRAY_TEXT
            canvas.drawText(countBadge, cardLeft + 180f, currentY + 12f, paint)

            currentY += 22f
        } else {
            val bannerHeight = 38f
            paint.style = Paint.Style.FILL
            paint.color = COLOR_MUTED_BG
            canvas.drawRect(cardLeft, currentY, cardRight, currentY + bannerHeight, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = COLOR_BLACK
            canvas.drawLine(cardLeft, currentY + bannerHeight, cardRight, currentY + bannerHeight, paint)

            paint.style = Paint.Style.FILL
            paint.color = COLOR_BLACK
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val contTitle = if (isEnglish) {
                "TAPMONEY INFO - TRANSACTION HISTORY CONTINUED (PAGE $pageIndex OF $totalPages)"
            } else {
                "TAPMONEY INFO - LANJUTAN RIWAYAT TRANSAKSI (HALAMAN $pageIndex DARI $totalPages)"
            }
            canvas.drawText(contTitle, cardLeft + 16f, currentY + 24f, paint)

            currentY += bannerHeight + 10f
        }

        val headerHeight = 22f
        paint.style = Paint.Style.FILL
        paint.color = COLOR_BLACK
        canvas.drawRect(cardLeft + 16f, currentY, cardRight - 16f, currentY + headerHeight, paint)

        paint.color = COLOR_WHITE
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val col1X = cardLeft + 24f
        val col2X = cardLeft + 130f
        val col3X = cardLeft + 290f
        val col4X = cardLeft + 395f
        val col5X = cardRight - 24f

        canvas.drawText(if (isEnglish) "DATE & TIME" else "TANGGAL & WAKTU", col1X, currentY + 15f, paint)
        canvas.drawText(if (isEnglish) "TYPE" else "TIPE", col2X, currentY + 15f, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(if (isEnglish) "AMOUNT" else "NOMINAL", col3X, currentY + 15f, paint)
        canvas.drawText(if (isEnglish) "BALANCE" else "SISA SALDO", col4X, currentY + 15f, paint)
        canvas.drawText(if (isEnglish) "LOCATION" else "LOKASI", col5X, currentY + 15f, paint)
        paint.textAlign = Paint.Align.LEFT

        currentY += headerHeight

        if (rows.isEmpty()) {
            paint.color = COLOR_MUTED_BG
            paint.style = Paint.Style.FILL
            canvas.drawRect(cardLeft + 16f, currentY, cardRight - 16f, currentY + 45f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = COLOR_BORDER_LIGHT
            canvas.drawRect(cardLeft + 16f, currentY, cardRight - 16f, currentY + 45f, paint)

            paint.style = Paint.Style.FILL
            paint.color = COLOR_GRAY_TEXT
            paint.textSize = 9f
            paint.textAlign = Paint.Align.CENTER
            val emptyMsg = if (isEnglish) {
                "No transaction history recorded on this card chip."
            } else {
                "Tidak ada catatan riwayat transaksi pada chip kartu ini."
            }
            canvas.drawText(emptyMsg, (cardLeft + cardRight) / 2f, currentY + 28f, paint)
            paint.textAlign = Paint.Align.LEFT
            currentY += 45f
        } else {
            val rowHeight = 24f
            rows.forEachIndexed { idx, tx ->
                val rowTop = currentY
                val rowBottom = currentY + rowHeight

                paint.style = Paint.Style.FILL
                paint.color = if (idx % 2 == 0) COLOR_WHITE else COLOR_MUTED_BG
                canvas.drawRect(cardLeft + 16f, rowTop, cardRight - 16f, rowBottom, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.5f
                paint.color = COLOR_BORDER_LIGHT
                canvas.drawLine(cardLeft + 16f, rowBottom, cardRight - 16f, rowBottom, paint)

                paint.style = Paint.Style.FILL
                paint.color = COLOR_BLACK
                paint.typeface = Typeface.DEFAULT
                paint.textSize = 8f
                canvas.drawText("${tx.date} ${tx.time}", col1X, rowTop + 16f, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(tx.type, col2X, rowTop + 16f, paint)

                val isTopUp = tx.type.contains("Top", true) || tx.type.contains("Isi", true)
                paint.color = if (isTopUp) COLOR_GREEN else COLOR_RED
                paint.textAlign = Paint.Align.RIGHT
                val amtStr = "${if (isTopUp) "+ " else "- "}${formatRupiah(tx.amount)}"
                canvas.drawText(amtStr, col3X, rowTop + 16f, paint)

                paint.color = COLOR_BLACK
                paint.typeface = Typeface.DEFAULT
                val balStr = tx.balance?.let(::formatRupiah) ?: "-"
                canvas.drawText(balStr, col4X, rowTop + 16f, paint)

                paint.color = COLOR_GRAY_TEXT
                paint.typeface = Typeface.DEFAULT
                paint.textSize = 7.5f
                val unknownText = if (isEnglish) "Unknown" else "Tidak dikenali"
                val termStr = id.tapmoney.data.TerminalCatalog.resolveLocation(tx.terminalId, unknownLabel = unknownText)
                canvas.drawText(termStr, col5X, rowTop + 16f, paint)

                paint.textAlign = Paint.Align.LEFT
                currentY += rowHeight
            }
        }

        val footerHeight = 60f
        val footerTop = cardBottom - footerHeight

        paint.style = Paint.Style.FILL
        paint.color = COLOR_MUTED_BG
        canvas.drawRect(cardLeft, footerTop, cardRight, cardBottom, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = COLOR_BLACK
        canvas.drawLine(cardLeft, footerTop, cardRight, footerTop, paint)

        paint.style = Paint.Style.FILL
        paint.color = COLOR_GRAY_TEXT
        paint.textSize = 7f
        paint.typeface = Typeface.DEFAULT

        val noticeLine1 = if (isEnglish) {
            "This official receipt was generated automatically by TapMoney Info using direct NFC chip communication."
        } else {
            "Struk ini diterbitkan otomatis oleh TapMoney Info melalui komunikasi langsung sensor NFC dengan chip kartu."
        }
        val noticeLine2 = if (isEnglish) {
            "Card balance and transaction logs reflect stored-value data on the physical chip at scan time."
        } else {
            "Nilai saldo dan log transaksi mencerminkan data tersimpan (stored-value) pada chip fisik saat pemindaian."
        }
        canvas.drawText(noticeLine1, cardLeft + 16f, footerTop + 18f, paint)
        canvas.drawText(noticeLine2, cardLeft + 16f, footerTop + 30f, paint)

        paint.color = COLOR_BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8f
        val refId = "REF ID: TM-${card.timestamp}-${card.cardNumber.takeLast(4)}"
        canvas.drawText(refId, cardLeft + 16f, footerTop + 48f, paint)

        paint.textAlign = Paint.Align.RIGHT
        val pageStr = if (isEnglish) "PAGE $pageIndex OF $totalPages" else "HALAMAN $pageIndex DARI $totalPages"
        canvas.drawText(pageStr, cardRight - 16f, footerTop + 48f, paint)
        paint.textAlign = Paint.Align.LEFT
    }
}
