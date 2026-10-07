package com.cardioresp.monitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.cardioresp.monitor.R
import com.cardioresp.monitor.presentation.MainActivity

object AcquisitionNotification {
    const val CHANNEL_ID = "acquisition"
    const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW // nessun suono: non deve disturbare il soggetto
        ).apply { description = context.getString(R.string.notification_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Notifica permanente. Usa il cronometro di sistema (setUsesChronometer): la durata si
     * aggiorna da sola SENZA ripubblicare la notifica, quindi zero lavoro extra durante l'acquisizione.
     */
    fun build(context: Context, startedAtWallMs: Long): Notification {
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            context, 1,
            Intent(context, AcquisitionService::class.java).setAction(AcquisitionService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(startedAtWallMs)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp)
            .addAction(0, context.getString(R.string.stop), stop)
            .build()
    }
}
