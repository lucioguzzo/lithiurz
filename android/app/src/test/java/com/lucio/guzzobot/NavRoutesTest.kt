package com.lucio.guzzobot

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Una rotta dichiarata ma non registrata compila senza un fiato e fa
 * crashare l'app al primo tocco: è successo con la modalità JARVIS (6.2).
 * Il compilatore non se ne accorge, quindi se ne accorge questo test.
 */
class NavRoutesTest {

    private val source = File("src/main/java/com/lucio/guzzobot/ui/Nav.kt").readText()

    private val routes: List<String> = Regex("""object Routes \{(.*?)\n}""", RegexOption.DOT_MATCHES_ALL)
        .find(source)!!.groupValues[1]
        .let { body -> Regex("""const val (\w+) =""").findAll(body).map { it.groupValues[1] }.toList() }

    @Test
    fun `l'elenco delle rotte si legge`() {
        assertTrue(routes.size >= 10)
        assertTrue("VOICE" in routes)
    }

    @Test
    fun `ogni rotta ha la sua schermata`() {
        val missing = routes.filterNot { name ->
            "composable(Routes.$name)" in source ||
                "route = \"\${Routes.$name}/" in source
        }
        assertTrue("Rotte senza schermata: $missing", missing.isEmpty())
    }
}
