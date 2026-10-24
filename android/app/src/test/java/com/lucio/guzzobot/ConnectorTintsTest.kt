package com.lucio.guzzobot

import com.lucio.guzzobot.data.mcp.McpCatalog
import com.lucio.guzzobot.ui.components.ConnectorTints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorTintsTest {

    @Test
    fun `lo stesso connettore ha sempre la stessa tinta`() {
        val first = ConnectorTints.stableHash("github")
        assertEquals(first, ConnectorTints.stableHash("github"))
        // Non è hashCode di String, che non è garantito fra versioni della JVM.
        assertTrue(first >= 0)
    }

    @Test
    fun `connettori diversi non finiscono tutti sulla stessa tinta`() {
        val tints = McpCatalog.entries.map { ConnectorTints.stableHash(it.id) % 6 }
        assertTrue("tinte distinte: ${tints.distinct().size}", tints.distinct().size >= 3)
    }

    @Test
    fun `un id vuoto non fa saltare il conto`() {
        assertTrue(ConnectorTints.stableHash("") >= 0)
    }
}
