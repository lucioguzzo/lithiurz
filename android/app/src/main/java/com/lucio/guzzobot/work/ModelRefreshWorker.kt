package com.lucio.guzzobot.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lucio.guzzobot.GuzzoBotApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Una volta a settimana ricontrolla quali modelli i provider servono davvero. */
class ModelRefreshWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as GuzzoBotApp).container
        val settings = container.settingsStore.settings.first()
        runCatching { container.modelRefresher.refresh(settings) }
        return Result.success()
    }

    companion object {
        private const val NAME = "guzzobot_models"

        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<ModelRefreshWorker>(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
