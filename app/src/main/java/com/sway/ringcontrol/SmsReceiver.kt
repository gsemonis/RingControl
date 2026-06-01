package com.sway.ringcontrol

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Telephony
import android.util.Log
import androidx.core.content.edit

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("RingControl", "--- Broadcast Received: ${intent.action} ---")
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            Log.d("RingControl", "Messages found in intent: ${messages.size}")
            for (message in messages) {
                val senderNumber = message.displayOriginatingAddress
                Log.d("RingControl", "SMS Sender: $senderNumber")
                if (senderNumber != null) {
                    checkAndOverride(context, senderNumber)
                }
            }
        }
    }

    private fun checkAndOverride(context: Context, senderNumber: String) {
        val sharedPrefs = context.getSharedPreferences("RingControlPrefs", Context.MODE_PRIVATE)
        val selectedNumbers = sharedPrefs.getStringSet("selected_numbers", emptySet()) ?: emptySet()

        val cleanIncoming = senderNumber.replace("\\D".toRegex(), "")

        val matchedNumber = selectedNumbers.firstOrNull { 
            val cleanSelected = it.replace("\\D".toRegex(), "")
            cleanSelected.isNotEmpty() && cleanIncoming.isNotEmpty() && 
            (cleanSelected.contains(cleanIncoming) || cleanIncoming.contains(cleanSelected))
        }

        if (matchedNumber != null) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            try {
                // Check if Always Ring is enabled for this number
                val alwaysRing = sharedPrefs.getBoolean("sms_always_ring_$matchedNumber", false)
                
                if (alwaysRing) {
                    // Save state
                    val currentRingerMode = audioManager.ringerMode
                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                    val currentDndFilter = notificationManager.currentInterruptionFilter

                    sharedPrefs.edit {
                        putInt("old_ringer_mode", currentRingerMode)
                        putInt("old_volume", currentVolume)
                        putInt("old_dnd_filter", currentDndFilter)
                        putBoolean("is_overridden", true)
                    }

                    // Bypass DND
                    if (notificationManager.isNotificationPolicyAccessGranted) {
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    }

                    // Set Loud for both Ring and Notification streams
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                    val maxNotif = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
                    audioManager.setStreamVolume(AudioManager.STREAM_RING, maxRing, 0)
                    audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, maxNotif, 0)
                }

                // Play Custom SMS sound and vibration
                val soundUri = sharedPrefs.getString("sms_ring_uri_$matchedNumber", null)
                val vibPattern = sharedPrefs.getString("sms_vib_$matchedNumber", "Default")
                
                val serviceIntent = Intent(context, RingService::class.java).apply {
                    putExtra("ringtone_uri", soundUri)
                    putExtra("vibration_pattern", vibPattern)
                    putExtra("is_sms", true)
                }
                
                context.startForegroundService(serviceIntent)
                
                Log.d("RingControl", "!!! MATCH FOUND !!! SMS from $senderNumber. Always Ring: $alwaysRing, Vib: $vibPattern")
            } catch (e: Exception) {
                Log.e("RingControl", "Error overriding audio settings", e)
            }
        } else {
            Log.d("RingControl", "SMS received from $senderNumber but it is NOT whitelisted.")
        }
    }
}