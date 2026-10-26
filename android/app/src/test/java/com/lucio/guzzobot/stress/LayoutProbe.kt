package com.lucio.guzzobot.stress

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isRoot

/**
 * Cerca quello che un utente vedrebbe rotto senza che niente vada in crash:
 * testi stretti fino a una lettera per riga, pulsanti schiacciati a zero.
 */
object LayoutProbe {

    fun problems(compose: ComposeTestRule): List<String> {
        val found = mutableListOf<String>()
        val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
        roots.forEach { root ->
            val screen = root.boundsInRoot
            fun walk(node: SemanticsNode) {
                val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")?.trim().orEmpty()
                val box = node.boundsInRoot
                // Fuori dallo schermo (menu chiuso, elenchi scorrevoli) non conta.
                val onScreen = box.left >= screen.left - 1 && box.right <= screen.right + 1 && box.top < screen.bottom
                if (onScreen && text.length >= 4 && box.width in 0.5f..(text.length.coerceAtMost(12) * 2.5f)) {
                    found += "testo schiacciato (${box.width.toInt()}px): «${text.take(40)}»"
                }
                // A zero perché tagliato da un contenitore che scorre è solo più
                // in basso: ci si arriva. Fuori da un contenitore che scorre,
                // invece, non lo raggiunge nessuno.
                if (text.length >= 2 && box.width < 0.5f && box.height < 0.5f && !insideScroll(node)) {
                    found += "irraggiungibile: «${text.take(40)}»"
                }
                node.children.forEach(::walk)
            }
            walk(root)
        }
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            val box = node.boundsInRoot
            val label = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")
                ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
                ?: return@forEach
            if ((box.width < 0.5f || box.height < 0.5f) && !insideScroll(node)) {
                found += "pulsante irraggiungibile: «${label.trim().take(40)}»"
            }
        }
        return found.distinct()
    }

    private fun insideScroll(node: SemanticsNode): Boolean {
        var current: SemanticsNode? = node.parent
        while (current != null) {
            if (current.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null ||
                current.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null
            ) {
                return true
            }
            current = current.parent
        }
        return false
    }
}
