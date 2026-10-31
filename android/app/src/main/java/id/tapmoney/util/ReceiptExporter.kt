package id.tapmoney.util

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.ui.formatRupiah
import java.io.File
import java.io.FileOutputStream

object ReceiptExporter {
    fun createPdf(context: Context, card: CardBalanceResult): android.net.Uri {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f }
        val canvas = page.canvas
        var y = 48f
        fun line(value: String) {
            canvas.drawText(value, 36f, y, paint)
            y += 24f
        }
        paint.textSize = 20f
        line("TapMoney Info")
        paint.textSize = 14f
        line("STRUK SALDO UANG ELEKTRONIK")
        y += 12f
        line("Kartu: ${card.cardType}")
        line("Nomor: ${card.cardNumber}")
        line("Saldo: ${formatRupiah(card.balance)}")
        line("Riwayat transaksi")
        card.history.take(25).forEach { tx ->
            line("${tx.date} ${tx.time}  ${tx.type}  ${formatRupiah(tx.amount)}")
            line("Saldo akhir: ${tx.balance?.let(::formatRupiah) ?: "-"}  Terminal: ${tx.terminalId ?: "-"}")
        }
        document.finishPage(page)
        val file = File(context.cacheDir, "tapmoney-${card.timestamp}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
