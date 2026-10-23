package com.lucio.guzzobot.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lucio.guzzobot.data.local.ScheduledTaskEntity
import java.util.concurrent.TimeUnit

/**
 * I compiti ricorrenti passano da WorkManager: sopravvivono al riavvio, ma il
 * sistema decide il momento esatto — la frequenza è garantita, l'orario no, e
 * con il telefono in Doze slitta.
 */
object TaskScheduler {

    fun sync(context: Context, task: ScheduledTaskEntity) {
        val manager = WorkManager.getInstance(context)
        val name = name(task.id)
        if (!task.enabled) {
            manager.cancelUniqueWork(name)
            return
        }
        val request = PeriodicWorkRequestBuilder<AgentWorker>(
            task.everyHours.coerceIn(1, 24 * 7).toLong(),
            TimeUnit.HOURS,
        )
            .setInputData(workDataOf(AgentWorker.KEY_TASK to task.id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        manager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context, id: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(name(id))
    }

    fun runNow(context: Context, id: Long) {
        val request = OneTimeWorkRequestBuilder<AgentWorker>()
            .setInputData(workDataOf(AgentWorker.KEY_TASK to id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    private fun name(id: Long) = "guzzobot_task_$id"
}
