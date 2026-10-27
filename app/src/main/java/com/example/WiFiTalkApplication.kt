package com.example

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.example.audio.VoipAudioEngine
import com.example.crypto.SecurityManager
import com.example.db.AppDatabase
import com.example.net.SignalingClient
import com.example.notification.NotificationHelper

class WiFiTalkApplication : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var securityManager: SecurityManager
        private set
    lateinit var notificationHelper: NotificationHelper
        private set
    lateinit var voipAudioEngine: VoipAudioEngine
        private set
    lateinit var signalingClient: SignalingClient
        private set
    lateinit var prefs: SharedPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        securityManager = SecurityManager(this)
        notificationHelper = NotificationHelper(this)
        voipAudioEngine = VoipAudioEngine(this)
        signalingClient = SignalingClient()
        prefs = getSharedPreferences("wifi_talk_prefs", Context.MODE_PRIVATE)
    }

    fun getDisplayName(): String {
        return prefs.getString("display_name", "") ?: ""
    }

    fun setDisplayName(name: String) {
        prefs.edit().putString("display_name", name).apply()
    }

    fun isBackgroundServiceEnabled(): Boolean {
        return prefs.getBoolean("bg_service_enabled", true)
    }

    fun setBackgroundServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("bg_service_enabled", enabled).apply()
    }

    companion object {
        lateinit var instance: WiFiTalkApplication
            private set
    }
}
