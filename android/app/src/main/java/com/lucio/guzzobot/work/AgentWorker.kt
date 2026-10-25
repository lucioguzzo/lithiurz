package com.lucio.guzzobot.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lucio.guzzobot.GuzzoBotApp
import com.lucio.guzzobot.MainActivity
import com.lucio.guzzobot.R
import com.lucio.guzzobot.data.agent.AgentLimits
import com.lucio.guzzobot.data.skills.SkillCatalog
import com.lucio.guzzobot.data.skills.SkillTools
import com.lucio.guzzobot.data.agent.ImageMaker
import com.lucio.guzzobot.data.agent.ToolHub
import com.lucio.guzzobot.data.model.Roles
import com.lucio.guzzobot.data.workspace.WorkspaceFactory
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Esegue un compito pianificato senza nessuno davanti allo schermo. Per questo
 * il permesso di scrivere è una scelta esplicita del compito: se manca,
 * l'agente legge, analizza e riferisce.
 *
 * WorkManager ferma un worker dopo dieci minuti, quindi il budget qui è più
 * stretto che in chat: se non basta, l'agente lo dice e si riprende dall'app.
 */
class AgentWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK, -1L)
        val container = (applicationContext as GuzzoBotApp).container
        val task = container.taskDao.task(taskId) ?: return Result.success()
        if (!task.enabled) return Result.success()

        val settings = container.settingsStore.settings.first()
        val provider = settings.activeProvider
        val bundle = WorkspaceFactory.create(
            context = applicationContext,
            settings = settings,
            secrets = container.secretStore,
            json = container.json,
        )
        if (provider == null || bundle == null) {
            container.taskDao.recordRun(taskId, System.currentTimeMillis(), "configurazione mancante")
            return Result.success()
        }

        val stamp = SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date())
        val conversationId = container.repository.createConversation(
            providerId = provider.id,
            model = settings.activeModel,
            title = "${task.title} · $stamp",
        )
        container.repository.addMessage(conversationId, Roles.USER, task.goal)

        val run = container.agentEngine.run(
            conversationId = conversationId,
            goal = task.goal,
            provider = provider,
            apiKey = container.secretStore.apiKey(provider.id),
            model = settings.activeModel,
            settings = settings,
            hub = ToolHub(
                bundle = bundle,
                servers = settings.mcpServers,
                mcpClient = container.mcpClient,
                secrets = container.secretStore,
                json = container.json,
                skills = settings.skills,
                skillLoader = SkillTools.Loader { id ->
                    SkillCatalog.find(settings.skills, id)?.let { skill ->
                        container.repository.setSkill(conversationId, skill.id)
                        "Competenza «${skill.name}» attiva. Segui queste istruzioni:\n\n" +
                            skill.instructions
                    }
                },
                images = ImageMaker.create(
                    settings = settings,
                    conversationId = conversationId,
                    chatApi = container.chatApi,
                    secrets = container.secretStore,
                    attachments = container.attachmentStore,
                    repository = container.repository,
                    artifacts = container.artifactDao,
                ),
            ),
            limits = AgentLimits(maxSteps = 25, maxRounds = 18, maxMinutes = 8),
            approve = { task.mayWrite },
        )
        container.taskDao.recordRun(taskId, System.currentTimeMillis(), run.outcome.name)
        notify(task.title, run.closingNote().ifBlank { "Nessun esito" })
        return Result.success()
    }

    private fun notify(title: String, text: String) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    applicationContext.getString(R.string.tasks),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val open = PendingIntent.getActivity(
            applicationContext,
            1,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setContentTitle("${applicationContext.getString(R.string.task_done)}: $title")
            .setContentText(text.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(600)))
            .setSmallIcon(R.drawable.ic_spark)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(title.hashCode(), notification)
        }
    }

    companion object {
        const val KEY_TASK = "task_id"
        private const val CHANNEL = "guzzobot_tasks"
    }
}
