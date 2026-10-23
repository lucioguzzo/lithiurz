package com.lucio.guzzobot

import android.app.Application
import com.lucio.guzzobot.data.files.AttachmentStore
import com.lucio.guzzobot.data.local.GuzzoBotDatabase
import com.lucio.guzzobot.data.media.AudioPlayer
import com.lucio.guzzobot.data.media.AudioRecorder
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
    val settingsStore = SettingsStore(app, json)
    val secretStore = SecretStore(app)
    val chatApi = ChatApi(json)
    val attachmentStore = AttachmentStore(app)
    val audioRecorder = AudioRecorder(app)
    val audioPlayer = AudioPlayer()
    val deviceSpeaker = DeviceSpeaker(app)
}

class GuzzoBotApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Carica i font che PDFBox usa per estrarre il testo dai PDF.
        PDFBoxResourceLoader.init(this)
        container = AppContainer(this)
    }
}
