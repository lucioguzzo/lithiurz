package com.lucio.guzzobot

import com.lucio.guzzobot.data.mcp.OAuthDiscovery
import com.lucio.guzzobot.data.mcp.Pkce
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthDiscoveryTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun obj(raw: String) = json.parseToJsonElement(raw).jsonObject

    @Test
    fun `dal 401 si ricava dove stanno i metadati`() {
        val header = """Bearer realm="x", resource_metadata="https://mcp.test/.well-known/oauth-protected-resource""""
        assertEquals(
            "https://mcp.test/.well-known/oauth-protected-resource",
            OAuthDiscovery.resourceMetadataFrom(header),
        )
        // Anche senza virgolette.
        assertEquals(
            "https://mcp.test/meta",
            OAuthDiscovery.resourceMetadataFrom("Bearer resource_metadata=https://mcp.test/meta"),
        )
    }

    @Test
    fun `un 401 senza indicazioni non inventa un indirizzo`() {
        assertNull(OAuthDiscovery.resourceMetadataFrom(null))
        assertNull(OAuthDiscovery.resourceMetadataFrom("Bearer"))
        assertNull(OAuthDiscovery.resourceMetadataFrom("""Bearer error="invalid_token""""))
        // Un indirizzo in chiaro non va seguito.
        assertNull(OAuthDiscovery.resourceMetadataFrom("Bearer resource_metadata=http://mcp.test/m"))
    }

    @Test
    fun `col percorso il well-known va inserito prima, non dopo`() {
        val urls = OAuthDiscovery.resourceMetadataUrls("https://mcp.test/server/mcp")
        assertEquals("https://mcp.test/.well-known/oauth-protected-resource/server/mcp", urls.first())
        // Non tutti i server seguono la specifica: si prova anche l'altra forma.
        assertTrue(urls.contains("https://mcp.test/server/mcp/.well-known/oauth-protected-resource"))
        assertTrue(urls.contains("https://mcp.test/.well-known/oauth-protected-resource"))
    }

    @Test
    fun `senza percorso c'e' un solo indirizzo`() {
        assertEquals(
            listOf("https://mcp.test/.well-known/oauth-protected-resource"),
            OAuthDiscovery.resourceMetadataUrls("https://mcp.test"),
        )
    }

    @Test
    fun `del server di autorizzazione si prova anche la forma OpenID`() {
        val urls = OAuthDiscovery.authServerMetadataUrls("https://auth.test")
        assertTrue(urls.contains("https://auth.test/.well-known/oauth-authorization-server"))
        assertTrue(urls.contains("https://auth.test/.well-known/openid-configuration"))
    }

    @Test
    fun `l'identificativo della risorsa e' in forma canonica`() {
        assertEquals("https://mcp.test/mcp", OAuthDiscovery.canonicalResource("HTTPS://MCP.TEST/mcp"))
        // Niente barra finale.
        assertEquals("https://mcp.test", OAuthDiscovery.canonicalResource("https://mcp.test/"))
        // Il frammento non fa parte dell'identificativo.
        assertEquals("https://mcp.test/mcp", OAuthDiscovery.canonicalResource("https://mcp.test/mcp#x"))
        // La porta sì.
        assertEquals("https://mcp.test:8443", OAuthDiscovery.canonicalResource("https://mcp.test:8443"))
    }

    @Test
    fun `i metadati del server MCP dicono chi autorizza`() {
        val parsed = OAuthDiscovery.parseProtectedResource(
            obj(
                """{"resource":"https://mcp.test/mcp",
                    "authorization_servers":["https://auth.test"],
                    "scopes_supported":["read","write"]}""",
            ),
        )
        assertEquals("https://mcp.test/mcp", parsed?.resource)
        assertEquals(listOf("https://auth.test"), parsed?.authorizationServers)
        assertEquals(listOf("read", "write"), parsed?.scopesSupported)
    }

    @Test
    fun `metadati senza server di autorizzazione non servono a niente`() {
        assertNull(OAuthDiscovery.parseProtectedResource(obj("""{"resource":"https://mcp.test"}""")))
        assertNull(OAuthDiscovery.parseProtectedResource(obj("""{"authorization_servers":[]}""")))
    }

    @Test
    fun `del server di autorizzazione servono almeno due endpoint`() {
        val full = OAuthDiscovery.parseAuthServer(
            obj(
                """{"issuer":"https://auth.test",
                    "authorization_endpoint":"https://auth.test/authorize",
                    "token_endpoint":"https://auth.test/token",
                    "registration_endpoint":"https://auth.test/register",
                    "code_challenge_methods_supported":["S256"]}""",
            ),
        )
        assertEquals("https://auth.test/authorize", full?.authorizationEndpoint)
        assertEquals("https://auth.test/register", full?.registrationEndpoint)
        assertTrue(full!!.supportsPkce)
        // Senza authorize o token non si va da nessuna parte.
        assertNull(OAuthDiscovery.parseAuthServer(obj("""{"token_endpoint":"https://auth.test/t"}""")))
        assertNull(OAuthDiscovery.parseAuthServer(obj("""{"authorization_endpoint":"https://a/a"}""")))
    }

    @Test
    fun `un server che non fa S256 viene riconosciuto`() {
        val weak = OAuthDiscovery.parseAuthServer(
            obj(
                """{"authorization_endpoint":"https://a/a","token_endpoint":"https://a/t",
                    "code_challenge_methods_supported":["plain"]}""",
            ),
        )
        assertFalse(weak!!.supportsPkce)
    }

    @Test
    fun `la richiesta di autorizzazione porta tutto quello che serve`() {
        val server = OAuthDiscovery.AuthServer(
            issuer = "https://auth.test",
            authorizationEndpoint = "https://auth.test/authorize",
            tokenEndpoint = "https://auth.test/token",
        )
        val url = OAuthDiscovery.authorizeUrl(
            server = server,
            clientId = "abc",
            redirectUri = "guzzobot://oauth",
            state = "st4te",
            codeChallenge = "ch4llenge",
            resource = "https://mcp.test/mcp",
            scopes = listOf("read", "write"),
        )
        assertTrue(url, url.startsWith("https://auth.test/authorize?"))
        assertTrue(url, url.contains("response_type=code"))
        assertTrue(url, url.contains("client_id=abc"))
        assertTrue(url, url.contains("code_challenge_method=S256"))
        assertTrue(url, url.contains("state=st4te"))
        // Codificati, altrimenti troncherebbero la query.
        assertTrue(url, url.contains("redirect_uri=guzzobot%3A%2F%2Foauth"))
        assertTrue(url, url.contains("resource=https%3A%2F%2Fmcp.test%2Fmcp"))
        assertTrue(url, url.contains("scope=read+write") || url.contains("scope=read%20write"))
    }

    @Test
    fun `un endpoint che ha gia' una query non viene rotto`() {
        val server = OAuthDiscovery.AuthServer(
            issuer = "https://auth.test",
            authorizationEndpoint = "https://auth.test/authorize?tenant=1",
            tokenEndpoint = "https://auth.test/token",
        )
        val url = OAuthDiscovery.authorizeUrl(server, "a", "guzzobot://o", "s", "c", "https://m")
        assertTrue(url, url.startsWith("https://auth.test/authorize?tenant=1&"))
    }

    @Test
    fun `il ritorno dal browser vale solo se lo stato combacia`() {
        val good = OAuthDiscovery.parseCallback("guzzobot://oauth?code=xyz&state=st4te")
        assertEquals("xyz", good.codeFor("st4te"))
        // Uno stato diverso è una risposta che non è nostra.
        assertNull(good.codeFor("altro"))
    }

    @Test
    fun `un rifiuto dal browser viene riportato per quello che e'`() {
        val denied = OAuthDiscovery.parseCallback(
            "guzzobot://oauth?error=access_denied&error_description=L%27utente%20ha%20rifiutato",
        )
        assertNull(denied.codeFor("qualunque"))
        assertEquals("access_denied: L'utente ha rifiutato", denied.message)
        assertNull(OAuthDiscovery.parseCallback("guzzobot://oauth?code=a&state=b").message)
    }

    @Test
    fun `i token si leggono, e il rinnovo e' facoltativo`() {
        val full = OAuthDiscovery.parseTokens(
            obj("""{"access_token":"at","refresh_token":"rt","expires_in":3600,"scope":"read"}"""),
        )
        assertEquals("at", full?.accessToken)
        assertEquals("rt", full?.refreshToken)
        assertEquals(3600, full?.expiresInSeconds)
        val minimal = OAuthDiscovery.parseTokens(obj("""{"access_token":"at"}"""))
        assertNull(minimal?.refreshToken)
        // Una risposta d'errore non è un token.
        assertNull(OAuthDiscovery.parseTokens(obj("""{"error":"invalid_grant"}""")))
    }

    @Test
    fun `la registrazione dinamica restituisce almeno l'id`() {
        assertEquals("cid" to null, OAuthDiscovery.parseRegistration(obj("""{"client_id":"cid"}""")))
        assertEquals(
            "cid" to "sec",
            OAuthDiscovery.parseRegistration(obj("""{"client_id":"cid","client_secret":"sec"}""")),
        )
        assertNull(OAuthDiscovery.parseRegistration(obj("""{"error":"invalid_client_metadata"}""")))
    }

    @Test
    fun `il segreto PKCE e il suo riscontro rispettano la forma richiesta`() {
        val verifier = Pkce.verifier()
        // Fra 43 e 128 caratteri, solo quelli ammessi.
        assertTrue("${verifier.length}", verifier.length in 43..128)
        assertTrue(verifier, verifier.all { it.isLetterOrDigit() || it in "-._~" })
        val challenge = Pkce.challenge(verifier)
        assertTrue(challenge, challenge.all { it.isLetterOrDigit() || it in "-_" })
        // Stesso segreto, stesso riscontro; segreti diversi, riscontri diversi.
        assertEquals(challenge, Pkce.challenge(verifier))
        assertFalse(challenge == Pkce.challenge(Pkce.verifier()))
        assertFalse(Pkce.state() == Pkce.state())
    }

    @Test
    fun `il riscontro PKCE e' quello dell'esempio della specifica`() {
        // RFC 7636, appendice B: verifier noto, riscontro noto.
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }
}
