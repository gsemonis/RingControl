package com.sway.ringcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.net.toUri

class RingService : Service() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ringtoneUriStr = intent?.getStringExtra("ringtone_uri")
        val vibrationPattern = intent?.getStringExtra("vibration_pattern") ?: "Default"
        val isSms = intent?.getBooleanExtra("is_sms", false) ?: false

        startForeground(1, createNotification())

        playRingtone(ringtoneUriStr, isSms)
        startVibration(vibrationPattern)

        // If it's an SMS, stop after a few seconds automatically
        if (isSms) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                stopSelf()
            }, 5000) // SMS alert duration
        }

        return START_NOT_STICKY
    }

    private fun playRingtone(uriStr: String?, isSms: Boolean) {
        try {
            val defaultUri = if (isSms) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) 
                             else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val uri = uriStr?.toUri() ?: defaultUri
            
            ringtone = RingtoneManager.getRingtone(this, uri)
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(if (isSms) AudioAttributes.USAGE_NOTIFICATION else AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone?.play()
        } catch (e: Exception) {
            Log.e("RingService", "Error playing ringtone", e)
        }
    }

    private fun startVibration(pattern: String) {
        val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibrator = vibratorManager.defaultVibrator
        
        // Stop any current vibration to allow our custom one to take priority
        vibrator?.cancel()

        val timings: LongArray = when (pattern) {
            "Pulse" -> longArrayOf(0, 500, 500)
            "Heartbeat" -> longArrayOf(0, 200, 200, 200, 600)
            "SOS" -> longArrayOf(0, 200, 200, 200, 200, 200, 200, 600, 200, 600, 200, 600, 200, 200, 200, 200, 200, 200)
            "Rapid" -> longArrayOf(0, 100, 100)
            else -> longArrayOf(0, 1000, 1000) // Default
        }

        val effect = VibrationEffect.createWaveform(timings, 0) // 0 means repeat
        vibrator?.vibrate(effect)
    }

    private fun createNotification(): Notification {
        val channelId = "RingServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Custom Call Ringing",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return Notification.Builder(this, channelId)
            .setContentTitle("RingControl Active")
            .setContentText("Playing custom ringtone and vibration...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .build()
    }

    override fun onDestroy() {
        ringtone?.stop()
        vibrator?.cancel()
        super.onDestroy()
    }
}