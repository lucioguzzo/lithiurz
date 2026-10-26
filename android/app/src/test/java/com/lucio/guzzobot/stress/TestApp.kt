package com.lucio.guzzobot.stress

import com.lucio.guzzobot.AppContainer
import com.lucio.guzzobot.GuzzoBotApp
import com.lucio.guzzobot.data.prefs.SecretStore
import androidx.work.testing.WorkManagerTestInitHelper

/**
 * L'app vera, meno quello che sulla JVM non esiste: il Keystore hardware
 * (le chiavi finiscono in preferenze normali) e l'avvio automatico di
 * WorkManager, che qui va fatto a mano con lo strumento dei test.
 */
class TestApp : GuzzoBotApp() {
    override fun createContainer() =
        AppContainer(this, SecretStore(getSharedPreferences("test_keys", MODE_PRIVATE)))

    override fun scheduleWork() {
        WorkManagerTestInitHelper.initializeTestWorkManager(this)
        super.scheduleWork()
    }
}
