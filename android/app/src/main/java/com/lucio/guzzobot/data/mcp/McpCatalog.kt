package com.lucio.guzzobot.data.mcp

/** Come un connettore chiede di essere autenticato. */
enum class McpAuth {
    /** Aperto: si aggiunge e funziona. */
    NONE,

    /** Un token personale in un'intestazione: si incolla una volta. */
    TOKEN,

    /**
     * OAuth 2.1 con registrazione dinamica del client. GUZZOBOT non lo fa
     * ancora: questi connettori sono elencati, ma non si possono attivare.
     */
    OAUTH,
}

enum class McpCategory { AUTOMAZIONE, SVILUPPO, CONOSCENZA, PRODUTTIVITA, AFFARI }

/**
 * Una voce del catalogo: un server MCP remoto che esiste davvero, con
 * l'indirizzo giusto e il modo giusto di autenticarsi.
 */
data class McpEntry(
    val id: String,
    val name: String,
    val summary: String,
    val url: String,
    val auth: McpAuth,
    val category: McpCategory,
    /** Dove si prende il token, quando serve. */
    val tokenUrl: String? = null,
    /** La documentazione del fornitore, per chi vuole leggere prima. */
    val docsUrl: String? = null,
    val authHeader: String = "Authorization",
)

/**
 * I connettori pronti.
 *
 * Due regole che tengono l'elenco onesto. Primo: solo indirizzi verificati
 * sulla documentazione del fornitore — un URL inventato è peggio di una voce
 * mancante. Secondo: solo trasporto "streamable HTTP", quello che il client
 * dell'app sa parlare; i server che espongono soltanto il vecchio HTTP+SSE
 * non funzionerebbero e non vengono spacciati per pronti.
 *
 * I connettori OAuth sono elencati ma non attivabili: la registrazione
 * dinamica del client e il giro di autorizzazione nel browser sono un pezzo
 * che l'app non ha ancora. Starebbero qui a fare finta, e si è preferito
 * dirlo.
 */
object McpCatalog {

    val entries: List<McpEntry> = listOf(
        McpEntry(
            id = "zapier",
            name = "Zapier",
            // È la scorciatoia che conta: un solo connettore e dentro ci sono
            // Gmail, Calendar, Slack, Fogli, Drive e migliaia di altre app,
            // perché l'autenticazione verso ciascuna la tiene Zapier.
            summary = "Gmail, Calendar, Slack, Fogli, Drive e migliaia di altre " +
                "app, con un solo token",
            url = "https://mcp.zapier.com/api/v1/connect",
            auth = McpAuth.TOKEN,
            category = McpCategory.AUTOMAZIONE,
            tokenUrl = "https://mcp.zapier.com",
            docsUrl = "https://docs.zapier.com/mcp/overview",
        ),
        McpEntry(
            id = "github",
            name = "GitHub",
            summary = "Repository, issue, pull request e ricerca nel codice",
            url = "https://api.githubcopilot.com/mcp/",
            auth = McpAuth.TOKEN,
            category = McpCategory.SVILUPPO,
            tokenUrl = "https://github.com/settings/personal-access-tokens",
            docsUrl = "https://github.com/github/github-mcp-server",
        ),
        McpEntry(
            id = "deepwiki",
            name = "DeepWiki",
            summary = "La documentazione di qualunque repository pubblico, spiegata",
            url = "https://mcp.deepwiki.com/mcp",
            auth = McpAuth.NONE,
            category = McpCategory.CONOSCENZA,
            docsUrl = "https://deepwiki.com",
        ),
        McpEntry(
            id = "huggingface",
            name = "Hugging Face",
            summary = "Modelli, dataset e spazi: cerca e leggi le schede",
            url = "https://huggingface.co/mcp",
            auth = McpAuth.TOKEN,
            category = McpCategory.CONOSCENZA,
            tokenUrl = "https://huggingface.co/settings/tokens",
        ),
        McpEntry(
            id = "apify",
            name = "Apify",
            summary = "Estrazione dati dal web con gli attori di Apify",
            url = "https://mcp.apify.com",
            auth = McpAuth.TOKEN,
            category = McpCategory.CONOSCENZA,
            tokenUrl = "https://console.apify.com/settings/integrations",
        ),
        McpEntry(
            id = "tally",
            name = "Tally",
            summary = "Moduli e risposte raccolte",
            url = "https://api.tally.so/mcp",
            auth = McpAuth.TOKEN,
            category = McpCategory.PRODUTTIVITA,
            tokenUrl = "https://tally.so/settings/api",
        ),
        // Da qui sotto: elencati, non attivabili. Serve OAuth.
        McpEntry(
            id = "notion",
            name = "Notion",
            summary = "Pagine, database e appunti",
            url = "https://mcp.notion.com/mcp",
            auth = McpAuth.OAUTH,
            category = McpCategory.PRODUTTIVITA,
            docsUrl = "https://developers.notion.com/docs/mcp",
        ),
        McpEntry(
            id = "linear",
            name = "Linear",
            summary = "Issue, progetti e cicli di sviluppo",
            url = "https://mcp.linear.app/mcp",
            auth = McpAuth.OAUTH,
            category = McpCategory.SVILUPPO,
            docsUrl = "https://linear.app/docs/mcp",
        ),
        McpEntry(
            id = "stripe",
            name = "Stripe",
            summary = "Pagamenti, clienti e fatture",
            url = "https://mcp.stripe.com",
            auth = McpAuth.OAUTH,
            category = McpCategory.AFFARI,
            docsUrl = "https://docs.stripe.com/mcp",
        ),
        McpEntry(
            id = "sentry",
            name = "Sentry",
            summary = "Errori e tracce delle applicazioni",
            url = "https://mcp.sentry.dev/mcp",
            auth = McpAuth.OAUTH,
            category = McpCategory.SVILUPPO,
            docsUrl = "https://docs.sentry.io/product/sentry-mcp/",
        ),
    )

    /** Quelli che si possono attivare adesso. */
    fun ready(): List<McpEntry> = entries.filter { it.auth != McpAuth.OAUTH }

    /** Quelli che aspettano l'autorizzazione OAuth. */
    fun pending(): List<McpEntry> = entries.filter { it.auth == McpAuth.OAUTH }

    fun byCategory(): Map<McpCategory, List<McpEntry>> =
        entries.groupBy { it.category }

    /** La voce diventa un connettore configurato. */
    fun toServer(entry: McpEntry): McpServer = McpServer(
        id = entry.id,
        name = entry.name,
        url = entry.url,
        authHeader = entry.authHeader,
        enabled = true,
    )
}
