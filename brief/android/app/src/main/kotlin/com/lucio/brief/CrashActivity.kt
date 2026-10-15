package com.lucio.brief

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Schermata di emergenza: mostra l'errore che ha impedito l'avvio e
 * permette di copiarlo. Costruita a mano, senza layout XML né librerie,
 * per non dipendere da nulla che possa essere a sua volta rotto.
 */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val report = intent.getStringExtra(EXTRA_REPORT) ?: "Nessun dettaglio disponibile."

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(32, 48, 32, 32)
        }

        root.addView(TextView(this).apply {
            text = "BRIEF non è riuscito ad avviarsi"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        })

        root.addView(TextView(this).apply {
            text = "Questo è il motivo esatto. Copialo e invialo allo sviluppatore."
            setTextColor(Color.parseColor("#BBBBBB"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, 16, 0, 16)
        })

        val details = TextView(this).apply {
            text = report
            setTextColor(Color.parseColor("#FF8A80"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        root.addView(
            ScrollView(this).apply { addView(details) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        root.addView(Button(this).apply {
            text = "Copia il rapporto"
            gravity = Gravity.CENTER
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("BRIEF", report))
                Toast.makeText(this@CrashActivity, "Rapporto copiato", Toast.LENGTH_SHORT).show()
            }
        })

        setContentView(root)
    }

    companion object {
        const val EXTRA_REPORT = "rapporto"
    }
}
