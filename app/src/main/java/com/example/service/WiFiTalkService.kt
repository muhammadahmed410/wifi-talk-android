package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.example.WiFiTalkApplication
import com.example.notification.NotificationHelper

class WiFiTalkService : Service() {

    companion object {
        const val ACTION_START = "com.example.wifitalk.action.START"
        const val ACTION_STOP = "com.example.wifitalk.action.STOP"
        const val ACTION_ANSWER_CALL = "com.example.wifitalk.action.ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "com.example.wifitalk.action.DECLINE_CALL"

        fun startService(context: Context) {
            val intent = Intent(context, WiFiTalkService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WiFiTalkService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val tag = "WiFiTalkService"

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "WiFiTalkService onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as WiFiTalkApplication
        val helper = app.notificationHelper

        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_ANSWER_CALL -> {
                // Trigger accept in ViewModel / Repository via broadcast or direct call
                app.notificationHelper.cancelIncomingCallNotification()
                // Forward intent to MainActivity
                val mainIntent = Intent(this, com.example.MainActivity::class.java).apply {
                    action = ACTION_ANSWER_CALL
                    setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(mainIntent)
            }

            ACTION_DECLINE_CALL -> {
                app.notificationHelper.cancelIncomingCallNotification()
                val mainIntent = Intent(this, com.example.MainActivity::class.java).apply {
                    action = ACTION_DECLINE_CALL
                    setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(mainIntent)
            }

            ACTION_START, null -> {
                val notification = helper.buildServiceNotification("Active & ready on local Wi-Fi")
                try {
                    val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    } else {
                        0
                    }
                    ServiceCompat.startForeground(
                        this,
                        NotificationHelper.NOTIFICATION_ID_SERVICE,
                        notification,
                        serviceType
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Failed to start foreground service", e)
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.i(tag, "WiFiTalkService onDestroy")
    }
}
