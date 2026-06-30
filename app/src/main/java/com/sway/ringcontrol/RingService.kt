package com.sway.ringcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri

class RingService : Service() {
    private var ringtone: Ringtone? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    companion object {
        private var lastTriggerTime = 0L
        private var lastTriggerSender = ""

        /**
         * Safely restores audio settings from SharedPreferences if they were overridden.
         * Can be called from anywhere (e.g. CallReceiver) to ensure cleanup.
         */
        fun restoreAudioState(context: Context) {
            val sharedPrefs = context.getSharedPreferences("RingControlPrefs", MODE_PRIVATE)
            if (sharedPrefs.getBoolean("is_overridden", false)) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val oldMode = sharedPrefs.getInt("old_ringer_mode", AudioManager.RINGER_MODE_NORMAL)
                val oldRing = sharedPrefs.getInt("old_ring_vol", 0)
                val oldNotif = sharedPrefs.getInt("old_notif_vol", 0)
                val oldMusic = sharedPrefs.getInt("old_music_vol", audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
                val oldDnd = sharedPrefs.getInt("old_dnd_filter", NotificationManager.INTERRUPTION_FILTER_ALL)

                try {
                    audioManager.ringerMode = oldMode
                    audioManager.setStreamVolume(AudioManager.STREAM_RING, oldRing, 0)
                    audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, oldNotif, 0)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, oldMusic, 0)
                    
                    if (notificationManager.isNotificationPolicyAccessGranted) {
                        notificationManager.setInterruptionFilter(oldDnd)
                    }
                    
                    sharedPrefs.edit { putBoolean("is_overridden", false) }
                } catch (e: Exception) {
                    Log.e("RingControl", "Restore error", e)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_CAR) {
            return START_NOT_STICKY
        }

        val ringtoneUriStr = intent?.getStringExtra("ringtone_uri")
        val vibrationPattern = intent?.getStringExtra("vibration_pattern") ?: "Default"
        val isSms = intent?.getBooleanExtra("is_sms", false) ?: false
        val alwaysRing = intent?.getBooleanExtra("always_ring", false) ?: false
        val sender = intent?.getStringExtra("sender_id") ?: ""

        // 2. Anti-Spam Cooldown (Only for SMS)
        val currentTime = System.currentTimeMillis()
        if (isSms && (sender == lastTriggerSender) && ((currentTime - lastTriggerTime) < 5000)) {
            return START_NOT_STICKY
        }
        lastTriggerTime = currentTime
        lastTriggerSender = sender

        // STOP everything currently playing before starting new alert
        cleanupAlerts()

        startForeground(1, createNotification())

        if (alwaysRing) {
            saveAndApplyLoudSettings()
        }

        if (isSms) {
            playSmsAlert(ringtoneUriStr)
        } else {
            playCallRingtone(ringtoneUriStr)
        }
        
        startVibration(vibrationPattern)

        if (isSms) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                {
                    stopSelf()
                },
                6000,
            )
        }

        return START_NOT_STICKY
    }

    private fun playCallRingtone(uriStr: String?) {
        try {
            val uri = uriStr?.toUri() ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)
            
            // If headphones are in, we use USAGE_MEDIA to ensure it's heard in the headset 
            // even if the ringer mode is silent (as we won't override it in that case).
            val usage = if (isHeadsetConnected()) {
                AudioAttributes.USAGE_MEDIA
            } else {
                AudioAttributes.USAGE_NOTIFICATION_RINGTONE
            }

            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone?.play()
        } catch (e: Exception) {
            Log.e("RingControl", "Call audio error", e)
        }
    }

    private fun playSmsAlert(uriStr: String?) {
        try {
            val uri = uriStr?.toUri() ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            val usage = if (isHeadsetConnected()) {
                AudioAttributes.USAGE_MEDIA
            } else {
                AudioAttributes.USAGE_NOTIFICATION
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@RingService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("RingControl", "SMS audio error", e)
        }
    }

    private fun isHeadsetConnected(): Boolean {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
    }

    private fun saveAndApplyLoudSettings() {
        val sharedPrefs = getSharedPreferences("RingControlPrefs", MODE_PRIVATE)
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (!sharedPrefs.getBoolean("is_overridden", false)) {
            sharedPrefs.edit {
                putInt("old_ringer_mode", audioManager.ringerMode)
                putInt("old_ring_vol", audioManager.getStreamVolume(AudioManager.STREAM_RING))
                putInt("old_notif_vol", audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
                putInt("old_music_vol", audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
                putInt("old_dnd_filter", notificationManager.currentInterruptionFilter)
                putBoolean("is_overridden", true)
            }
        }

        if (notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }

        if (isHeadsetConnected()) {
            // When headphones are connected, DO NOT change ringer mode to NORMAL.
            // This prevents the system from blasting the ringtone through the external speaker.
            // Instead, we ensure the Media volume is high enough to be heard in the headphones.
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 0.8).toInt(),
                0
            )
        } else {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            audioManager.setStreamVolume(AudioManager.STREAM_RING, audioManager.getStreamMaxVolume(AudioManager.STREAM_RING), 0)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION), 0)
        }
    }

    private fun startVibration(pattern: String) {
        val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibrator = vibratorManager.defaultVibrator
        vibrator?.cancel()

        val timings: LongArray = when (pattern) {
            "Pulse" -> longArrayOf(0, 500, 500)
            "Heartbeat" -> longArrayOf(0, 200, 200, 200, 600)
            "SOS" -> longArrayOf(0, 200, 200, 200, 200, 200, 200, 600, 200, 600, 200, 600, 200, 200, 200, 200, 200, 200)
            "Rapid" -> longArrayOf(0, 100, 100)
            else -> longArrayOf(0, 1000, 1000)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(timings, 0))
    }

    private fun cleanupAlerts() {
        ringtone?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
    }

    private fun createNotification(): Notification {
        val channelId = "RingServiceChannel"
        val channel = NotificationChannel(channelId, "Active Alert", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setContentTitle("RingControl")
            .setContentText("Prioritized alert active...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .build()
    }

    override fun onDestroy() {
        cleanupAlerts()
        restoreAudioState(this)
        super.onDestroy()
    }
}