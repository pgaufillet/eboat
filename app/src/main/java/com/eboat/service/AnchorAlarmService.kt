package com.eboat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.eboat.MainActivity
import com.eboat.R

class AnchorAlarmService : Service() {

    companion object {
        const val CHANNEL_ANCHOR = "anchor_watch"
        const val CHANNEL_ALARM = "anchor_alarm"
        const val NOTIFICATION_WATCH_ID = 1001
        const val NOTIFICATION_ALARM_ID = 1002

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AnchorAlarmService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AnchorAlarmService::class.java))
        }

        fun triggerAlarm(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            createChannels(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val notification = NotificationCompat.Builder(context, CHANNEL_ALARM)
                .setSmallIcon(R.drawable.ic_boat)
                .setContentTitle("ALARME MOUILLAGE")
                .setContentText("Le bateau a d\u00e9riv\u00e9 hors du cercle de mouillage !")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSound(alarmSound)
                .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .build()

            nm.notify(NOTIFICATION_ALARM_ID, notification)
        }

        fun clearAlarm(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.cancel(NOTIFICATION_ALARM_ID)
        }

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)

            val watchChannel = NotificationChannel(
                CHANNEL_ANCHOR, "Veille de mouillage",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Notification active pendant la veille de mouillage" }

            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM, "Alarme de mouillage",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarme sonore si le bateau d\u00e9rive"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                setSound(alarmSound, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }

            nm.createNotificationChannels(listOf(watchChannel, alarmChannel))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels(this)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ANCHOR)
            .setSmallIcon(R.drawable.ic_boat)
            .setContentTitle("Veille de mouillage active")
            .setContentText("Surveillance de la position du bateau")
            .setOngoing(true)
            .setContentIntent(pi)
            .build()

        startForeground(NOTIFICATION_WATCH_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
