package com.example.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.service.WiFiTalkService

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_CALLS = "wifi_talk_calls_channel"
        const val CHANNEL_MESSAGES = "wifi_talk_messages_channel"
        const val CHANNEL_SERVICE = "wifi_talk_service_channel"

        const val NOTIFICATION_ID_SERVICE = 1001
        const val NOTIFICATION_ID_INCOMING_CALL = 1002
        const val NOTIFICATION_ID_MESSAGE_BASE = 2000
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 1. Ongoing background service channel
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "WiFi Talk Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when WiFi Talk is listening for incoming local calls and messages."
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(serviceChannel)

            // 2. Incoming Calls Channel (High Priority, Ringtone, Heads-up)
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val audioAttr = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val callChannel = NotificationChannel(
                CHANNEL_CALLS,
                "WiFi Talk Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming peer-to-peer VoIP voice calls."
                setSound(ringtoneUri, audioAttr)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(callChannel)

            // 3. Incoming Messages Channel
            val msgChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "WiFi Talk Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming peer-to-peer chat messages."
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(msgChannel)
        }
    }

    fun buildServiceNotification(statusText: String): Notification {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle("WiFi Talk Active")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun showIncomingCallNotification(
        callId: String,
        callerId: String,
        callerName: String
    ) {
        // Activity intent when tapped or full-screen
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("EXTRA_INCOMING_CALL_ID", callId)
            putExtra("EXTRA_CALLER_ID", callerId)
            putExtra("EXTRA_CALLER_NAME", callerName)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            1,
            fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Answer action intent
        val answerIntent = Intent(context, WiFiTalkService::class.java).apply {
            action = WiFiTalkService.ACTION_ANSWER_CALL
            putExtra("EXTRA_CALL_ID", callId)
        }
        val answerPendingIntent = PendingIntent.getService(
            context,
            2,
            answerIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Decline action intent
        val declineIntent = Intent(context, WiFiTalkService::class.java).apply {
            action = WiFiTalkService.ACTION_DECLINE_CALL
            putExtra("EXTRA_CALL_ID", callId)
        }
        val declinePendingIntent = PendingIntent.getService(
            context,
            3,
            declineIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setContentTitle("Incoming Voice Call")
            .setContentText("$callerName is calling via Wi-Fi...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Answer", answerPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_INCOMING_CALL, notification)
    }

    fun cancelIncomingCallNotification() {
        notificationManager.cancel(NOTIFICATION_ID_INCOMING_CALL)
    }

    fun showMessageNotification(
        peerId: String,
        senderName: String,
        messageText: String
    ) {
        val chatIntent = Intent(context, MainActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("EXTRA_OPEN_CHAT_PEER_ID", peerId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            peerId.hashCode(),
            chatIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_MESSAGE_BASE + Math.abs(peerId.hashCode() % 1000), notification)
    }
}
