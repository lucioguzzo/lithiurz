package com.lucio.lith.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val linkColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Paragraph -> Text(
                    text = inline(block.text, codeBackground, linkColor),
                    style = MaterialTheme.typography.bodyLarge,
                    color = color,
                )

                is MdBlock.Heading -> Text(
                    text = inline(block.text, codeBackground, linkColor),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp)
                        else -> MaterialTheme.typography.titleMedium
                    },
                    fontWeight = if (block.level > 1) FontWeight.SemiBold else FontWeight.Normal,
                    color = color,
                    modifier = Modifier.padding(top = 6.dp),
                )

                is MdBlock.Code -> CodeBlock(block)

                is MdBlock.Quote -> Row(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .width(3.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                            .padding(vertical = 2.dp),
                    ) {}
                    Text(
                        text = inline(block.text, codeBackground, linkColor),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }

                is MdBlock.Listing -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    block.items.forEach { item ->
                        Row(modifier = Modifier.padding(start = (item.indent * 16).dp)) {
                            Text(
                                text = item.marker ?: "•",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(if (item.marker != null) 26.dp else 18.dp),
                            )
                            Text(
                                text = inline(item.text, codeBackground, linkColor),
                                style = MaterialTheme.typography.bodyLarge,
                                color = color,
                            )
                        }
                    }
                }

                MdBlock.Rule -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun CodeBlock(block: MdBlock.Code) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = block.language?.lowercase() ?: "codice",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { clipboard.setText(AnnotatedString(block.code)) }) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = block.code,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
        )
    }
}

private val URL_PATTERN = Regex("https?://[^\\s<>()\\[\\]]+")

/** Converte grassetto, corsivo, codice inline, barrato e link in una AnnotatedString. */
internal fun inline(text: String, codeBackground: Color, linkColor: Color): AnnotatedString =
    buildAnnotatedString { appendInline(text, codeBackground, linkColor) }

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInline(
    text: String,
    codeBackground: Color,
    linkColor: Color,
) {
    var i = 0
    while (i < text.length) {
        val rest = text.substring(i)
        when {
            rest.startsWith("\\") && rest.length > 1 -> {
                append(rest[1])
                i += 2
            }

            rest.startsWith("`") -> {
                val end = text.indexOf('`', i + 1)
                if (end == -1) {
                    append('`')
                    i++
                } else {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            fontSize = 14.sp,
                        ),
                    ) { append(text.substring(i + 1, end)) }
                    i = end + 1
                }
            }

            rest.startsWith("[") -> {
                val close = text.indexOf(']', i)
                val open = if (close == -1) -1 else text.indexOf('(', close)
                val end = if (open == -1) -1 else text.indexOf(')', open)
                if (close == -1 || open != close + 1 || end == -1) {
                    append('[')
                    i++
                } else {
                    val label = text.substring(i + 1, close)
                    val url = text.substring(open + 1, end).trim()
                    withLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                        ),
                    ) { appendInline(label, codeBackground, linkColor) }
                    i = end + 1
                }
            }

            rest.startsWith("**") || rest.startsWith("__") -> {
                val marker = rest.take(2)
                val end = text.indexOf(marker, i + 2)
                if (end == -1) {
                    append(marker)
                    i += 2
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendInline(text.substring(i + 2, end), codeBackground, linkColor)
                    }
                    i = end + 2
                }
            }

            rest.startsWith("~~") -> {
                val end = text.indexOf("~~", i + 2)
                if (end == -1) {
                    append("~~")
                    i += 2
                } else {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        appendInline(text.substring(i + 2, end), codeBackground, linkColor)
                    }
                    i = end + 2
                }
            }

            (rest.startsWith("*") || rest.startsWith("_")) && rest.length > 1 && !rest[1].isWhitespace() -> {
                val marker = rest.first()
                val end = text.indexOf(marker, i + 1)
                if (end == -1) {
                    append(marker)
                    i++
                } else {
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        appendInline(text.substring(i + 1, end), codeBackground, linkColor)
                    }
                    i = end + 1
                }
            }

            rest.startsWith("http://") || rest.startsWith("https://") -> {
                val match = URL_PATTERN.find(rest)
                if (match != null && match.range.first == 0) {
                    val url = match.value.trimEnd('.', ',', ';', ':')
                    withLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                        ),
                    ) { append(url) }
                    i += url.length
                } else {
                    append(rest.first())
                    i++
                }
            }

            else -> {
                append(rest.first())
                i++
            }
        }
    }
}
