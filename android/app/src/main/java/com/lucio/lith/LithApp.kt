package com.lucio.lith

import android.app.Application
import com.lucio.lith.data.local.LithDatabase
import com.lucio.lith.data.prefs.SecretStore
import com.lucio.lith.data.prefs.SettingsStore
import com.lucio.lith.data.remote.ChatApi
import com.lucio.lith.data.repo.ChatRepository
import kotlinx.serialization.json.Json

/** Dipendenze costruite a mano: l'app è piccola e non serve un framework DI. */
class AppContainer(app: Application) {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }
    private val database = LithDatabase.create(app)
    val repository = ChatRepository(database.chatDao())
    val settingsStore = SettingsStore(app, json)
    val secretStore = SecretStore(app)
    val chatApi = ChatApi(json)
}

class LithApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
