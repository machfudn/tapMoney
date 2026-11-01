package id.tapmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun parseMarkdownInline(
    text: String,
    textColor: Color,
    codeBgColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = text.length
        while (i < len) {
            when {
                // Bold: **text**
                i + 1 < len && text[i] == '*' && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Inline code: `code`
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBgColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        ) {
                            append(" " + text.substring(i + 1, end) + " ")
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Italic: *text* (single *)
                text[i] == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end != -1 && end > i + 1 && text[end - 1] != '*') {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = textColor)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

@Composable
fun MarkdownContent(
    markdown: String,
    textColor: Color,
    textSubtleColor: Color,
    headerColor: Color,
    surfaceColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val lines = markdown.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                continue
            }

            when {
                // Header 1: # Title
                trimmed.startsWith("# ") -> {
                    Text(
                        text = parseMarkdownInline(trimmed.removePrefix("# "), headerColor, surfaceColor),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = headerColor
                    )
                }
                // Header 2: ## Title
                trimmed.startsWith("## ") -> {
                    Text(
                        text = parseMarkdownInline(trimmed.removePrefix("## "), headerColor, surfaceColor),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = headerColor
                    )
                }
                // Header 3: ### Title
                trimmed.startsWith("### ") -> {
                    Text(
                        text = parseMarkdownInline(trimmed.removePrefix("### "), headerColor, surfaceColor),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = headerColor
                    )
                }
                // Bullet list item: * Item or - Item
                trimmed.startsWith("* ") || trimmed.startsWith("- ") -> {
                    val leadingSpaces = line.takeWhile { it == ' ' }.length
                    val isSubItem = leadingSpaces >= 2
                    val rawContent = if (trimmed.startsWith("* ")) trimmed.removePrefix("* ") else trimmed.removePrefix("- ")
                    val bullet = if (isSubItem) "  ◦ " else "• "
                    val bulletColor = if (isSubItem) textSubtleColor else headerColor

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = bullet,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = bulletColor
                        )
                        Text(
                            text = parseMarkdownInline(rawContent, if (isSubItem) textSubtleColor else textColor, surfaceColor),
                            fontSize = 11.sp,
                            color = if (isSubItem) textSubtleColor else textColor,
                            lineHeight = 15.sp
                        )
                    }
                }
                // Regular paragraph / plain text
                else -> {
                    Text(
                        text = parseMarkdownInline(trimmed, textSubtleColor, surfaceColor),
                        fontSize = 11.sp,
                        color = textSubtleColor,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
