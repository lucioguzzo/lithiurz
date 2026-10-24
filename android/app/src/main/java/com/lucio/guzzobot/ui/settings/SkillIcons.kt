package com.lucio.guzzobot.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.ShortText
import androidx.compose.ui.graphics.vector.ImageVector
import com.lucio.guzzobot.data.skills.Skill
import com.lucio.guzzobot.data.skills.SkillCategory

/**
 * Un segno per ogni competenza.
 *
 * Quelle fornite con l'app hanno il loro, scelto per quello che fanno; una
 * competenza scritta a mano eredita quello del suo ambito, così l'elenco
 * resta leggibile anche quando si riempie di roba propria.
 */
object SkillIcons {

    private val byId = mapOf(
        "proofread" to Icons.Outlined.Spellcheck,
        "clearer" to Icons.Outlined.ShortText,
        "email" to Icons.Outlined.MailOutline,
        "summary" to Icons.Outlined.Summarize,
        "explain-simple" to Icons.Outlined.Lightbulb,
        "flashcards" to Icons.Outlined.Style,
        "translate" to Icons.Outlined.Translate,
        "code-review" to Icons.Outlined.BugReport,
        "explain-code" to Icons.Outlined.Code,
        "tests" to Icons.Outlined.Science,
        "commit" to Icons.Outlined.Commit,
        "documents" to Icons.Outlined.ReceiptLong,
        "shopping" to Icons.Outlined.ShoppingCart,
        "meeting" to Icons.Outlined.Groups,
        "spreadsheet" to Icons.Outlined.Analytics,
    )

    private val byCategory = mapOf(
        SkillCategory.SCRITTURA to Icons.Outlined.Description,
        SkillCategory.STUDIO to Icons.AutoMirrored.Outlined.MenuBook,
        SkillCategory.CODICE to Icons.Outlined.Code,
        SkillCategory.CASA to Icons.Outlined.ReceiptLong,
        SkillCategory.ANALISI to Icons.Outlined.Analytics,
    )

    fun of(skill: Skill): ImageVector =
        byId[skill.id] ?: byCategory[skill.category] ?: Icons.Outlined.Description
}
