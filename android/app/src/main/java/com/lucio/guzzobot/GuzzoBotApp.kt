package com.lucio.guzzobot

import android.app.Application
import com.lucio.guzzobot.data.agent.AgentEngine
import com.lucio.guzzobot.data.crash.CrashGuard
import com.lucio.guzzobot.data.crash.CrashStore
import com.lucio.guzzobot.data.model.ModelRefresher
import com.lucio.guzzobot.work.ModelRefreshWorker
import com.lucio.guzzobot.data.files.AttachmentStore
import com.lucio.guzzobot.data.mcp.McpClient
import com.lucio.guzzobot.data.mcp.McpOAuth
import com.lucio.guzzobot.data.local.GuzzoBotDatabase
import com.lucio.guzzobot.data.media.AudioPlayer
import com.lucio.guzzobot.data.media.AudioRecorder
import com.lucio.guzzobot.data.media.DeviceListener
import com.lucio.guzzobot.data.media.DeviceSpeaker
import com.lucio.guzzobot.data.prefs.SecretStore
import com.lucio.guzzobot.data.prefs.SettingsStore
import com.lucio.guzzobot.data.remote.ChatApi
import com.lucio.guzzobot.data.repo.ChatRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.serialization.json.Json

/** Dipendenze costruite a mano: l'app è piccola e non serve un framework DI. */
class AppContainer(private val app: Application) {
    val appContext: android.content.Context get() = app

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }
    private val database = GuzzoBotDatabase.create(app)
    val repository = ChatRepository(database.chatDao())
    val taskDao = database.taskDao()
    val projectDao = database.projectDao()
    val artifactDao = database.artifactDao()
    val settingsStore = SettingsStore(app, json)
    val secretStore = SecretStore(app)
    val chatApi = ChatApi(json)
    val attachmentStore = AttachmentStore(app)
    val audioRecorder = AudioRecorder(app)
    val audioPlayer = AudioPlayer()
    val deviceSpeaker = DeviceSpeaker(app)
    val deviceListener = DeviceListener(app)
    val agentEngine = AgentEngine(repository, chatApi, json, artifactDao)
    val modelRefresher = ModelRefresher(chatApi, settingsStore, secretStore)
    val mcpClient = McpClient(json)
    val mcpOAuth = McpOAuth(json)
}

class GuzzoBotApp : Application() {
    lateinit var container: AppContainer
        private set

    /** L'ultimo crash, se c'è stato: lo si offre da condividere al lancio dopo. */
    val crashes: CrashStore by lazy { CrashStore(java.io.File(filesDir, "crash/ultimo.txt")) }

    override fun onCreate() {
        super.onCreate()
        // Per primo: un crash in quello che segue va annotato anche lui.
        CrashGuard.install(crashes)
        // Carica i font che PDFBox usa per estrarre il testo dai PDF.
        PDFBoxResourceLoader.init(this)
        container = AppContainer(this)
        // Gli elenchi dei modelli invecchiano: una volta a settimana si ricontrollano.
        ModelRefreshWorker.ensureScheduled(this)
    }
}
