package id.tapmoney.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

object NeoColors {
    val Yellow = Color(0xFFFFE500)
    val Blue = Color(0xFF00A3FF)
    val Green = Color(0xFF00E575)
    val Red = Color(0xFFFF3B30)
    val CyanDate = Color(0xFF0284C7)
    val Border = Color(0xFF000000)
    val Shadow = Color(0xFF000000)

    val LightBg = Color(0xFFF5F5F0)
    val DarkBg = Color(0xFF141416)

    val LightSurface = Color(0xFFFFFFFF)
    val DarkSurface = Color(0xFF222226)

    val LightSurfaceAlt = Color(0xFFF9F9F7)
    val DarkSurfaceAlt = Color(0xFF2C2C32)

    val LightText = Color(0xFF141416)
    val DarkText = Color(0xFFF4F4F6)

    val LightTextSubtle = Color(0xFF71717A)
    val DarkTextSubtle = Color(0xFFA1A1AA)
}

fun Modifier.neoShadow(
    offset: Dp = 3.dp,
    shadowColor: Color = NeoColors.Shadow,
    cornerRadius: Dp = 8.dp
): Modifier = this.drawBehind {
    drawRoundRect(
        color = shadowColor,
        topLeft = Offset(offset.toPx(), offset.toPx()),
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx())
    )
}

fun Modifier.neoCard(
    bgColor: Color,
    borderColor: Color = NeoColors.Border,
    borderWidth: Dp = 2.dp,
    shadowOffset: Dp = 3.dp,
    cornerRadius: Dp = 8.dp
): Modifier = this
    .neoShadow(offset = shadowOffset, shadowColor = borderColor, cornerRadius = cornerRadius)
    .background(color = bgColor, shape = RoundedCornerShape(cornerRadius))
    .border(width = borderWidth, color = borderColor, shape = RoundedCornerShape(cornerRadius))

fun formatRupiah(amount: Long): String {
    val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    format.maximumFractionDigits = 0
    return format.format(amount)
}

fun formatCardNumber(num: String, mask: Boolean = false): String {
    val clean = num.replace(" ", "")
    val isAllDigits = clean.length in 12..19 && clean.all { it.isDigit() }
    if (isAllDigits) {
        val spaced = clean.chunked(4).joinToString(" ")
        if (!mask) return spaced
        val first4 = clean.take(4)
        val last4 = clean.takeLast(4)
        val middle = if (clean.length == 16) "**** ****" else "*".repeat(clean.length - 8)
        return "$first4 $middle $last4"
    }
    // If it's a chip UID (hardware serial with hex letters or short length)
    return "UID: " + clean.chunked(2).joinToString(":")
}
