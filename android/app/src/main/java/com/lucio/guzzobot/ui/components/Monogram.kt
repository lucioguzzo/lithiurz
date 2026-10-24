package com.lucio.guzzobot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Il segno di un connettore.
 *
 * Non è il logo del servizio, ed è una scelta. I marchi di GitHub, Zapier,
 * Notion e gli altri sono loro, ciascuno con le proprie regole d'uso:
 * impacchettarli dentro l'APK significherebbe ridistribuirli, e verificare
 * dieci licenze diverse per dieci immaginette non è un buon affare. Scaricarli
 * a ogni avvio sarebbe peggio: una chiamata a dieci domini solo per disegnare
 * un elenco, e un elenco grigio quando si è offline.
 *
 * Resta un monogramma: l'iniziale su una tinta stabile, riconoscibile a colpo
 * d'occhio, che non chiede niente a nessuno e funziona senza rete.
 */
@Composable
fun Monogram(
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Int = 34,
) {
    // Su fondo scuro la tinta va tenuta più presente, su fondo chiaro più
    // lieve: senza questo i monogrammi spariscono in uno dei due temi.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3).dp))
            .background(tint.copy(alpha = if (dark) 0.26f else 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.take(2).uppercase(),
            color = tint,
            fontSize = (size / 2.6f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Le tinte dei connettori: stabili, distinguibili, nessuna presa dal marchio. */
object ConnectorTints {

    private val palette = listOf(
        Color(0xFFCC6B49), // terracotta
        Color(0xFF4F7CAC), // blu polvere
        Color(0xFF6A8E5E), // verde salvia
        Color(0xFFB4894A), // ocra
        Color(0xFF8A6BA8), // prugna
        Color(0xFF4E9A91), // verde acqua
    )

    /**
     * La tinta si ricava dal nome, così resta la stessa fra un avvio e
     * l'altro e due connettori vicini non finiscono quasi mai uguali.
     */
    fun of(id: String): Color = palette[(stableHash(id) % palette.size)]

    /** Un hash stabile: hashCode di String cambia fra versioni della JVM. */
    internal fun stableHash(value: String): Int {
        var hash = 7
        value.forEach { char -> hash = (hash * 31 + char.code) and 0x7FFFFFFF }
        return hash
    }
}
