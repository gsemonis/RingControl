package com.sway.ringcontrol

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.edit

class CallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                var incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

                Log.d("CallReceiver", "Phone State: $state")

                if (TelephonyManager.EXTRA_STATE_RINGING == state) {
                    // Log the numbers we have to see what's happening
                    Log.d("CallReceiver", "Incoming Number from intent: $incomingNumber")

                    if (incomingNumber == null) {
                        incomingNumber = getLastIncomingNumber(context)
                        Log.d("CallReceiver", "Attempted CallLog fallback: $incomingNumber")
                    }

                    incomingNumber?.let {
                        checkAndOverride(context, it)
                    }
                } else if ((TelephonyManager.EXTRA_STATE_OFFHOOK == state) || (TelephonyManager.EXTRA_STATE_IDLE == state)) {
                    restoreAudioState(context)
                }
            }
        } catch (e: Exception) {
            Log.e("CallReceiver", "Error in onReceive", e)
        }
    }

    private fun restoreAudioState(context: Context) {
        val sharedPrefs = context.getSharedPreferences("RingControlPrefs", Context.MODE_PRIVATE)
        val isOverridden = sharedPrefs.getBoolean("is_overridden", false)

        if (isOverridden) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val oldRingerMode = sharedPrefs.getInt("old_ringer_mode", AudioManager.RINGER_MODE_NORMAL)
            val oldVolume = sharedPrefs.getInt("old_volume", 0)
            val oldDndFilter = sharedPrefs.getInt("old_dnd_filter", NotificationManager.INTERRUPTION_FILTER_ALL)

            try {
                // Restore DND
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    notificationManager.setInterruptionFilter(oldDndFilter)
                }
                // Restore Mode and Volume
                audioManager.ringerMode = oldRingerMode
                audioManager.setStreamVolume(AudioManager.STREAM_RING, oldVolume, 0)

                sharedPrefs.edit { putBoolean("is_overridden", false) }
                Log.d("CallReceiver", "Restored audio state: Mode=$oldRingerMode, Vol=$oldVolume, DND=$oldDndFilter")
            } catch (e: Exception) {
                Log.e("CallReceiver", "Error restoring audio state", e)
            }
        }
    }

    private fun getLastIncomingNumber(context: Context): String? {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return null
        }
        
        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(
            android.provider.CallLog.Calls.CONTENT_URI,
            arrayOf(android.provider.CallLog.Calls.NUMBER),
            android.provider.CallLog.Calls.TYPE + " = ?",
            arrayOf(android.provider.CallLog.Calls.INCOMING_TYPE.toString()),
            android.provider.CallLog.Calls.DATE + " DESC",
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
                if (index >= 0) return it.getString(index)
            }
        }
        return null
    }

    private fun checkAndOverride(context: Context, incomingNumber: String) {
        val sharedPrefs = context.getSharedPreferences("RingControlPrefs", Context.MODE_PRIVATE)
        val selectedNumbers = sharedPrefs.getStringSet("selected_numbers", emptySet()) ?: emptySet()

        // Normalize both numbers by removing everything except digits
        val cleanIncoming = incomingNumber.replace("\\D".toRegex(), "")

        val isMatched = selectedNumbers.any { 
            val cleanSelected = it.replace("\\D".toRegex(), "")
            // Check if one contains the other (to handle +1 or area code differences)
            cleanSelected.isNotEmpty() && cleanIncoming.isNotEmpty() && 
            (cleanSelected.contains(cleanIncoming) || cleanIncoming.contains(cleanSelected))
        }

        if (isMatched) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            try {
                // Save current state before overriding
                val currentRingerMode = audioManager.ringerMode
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                val currentDndFilter = notificationManager.currentInterruptionFilter

                sharedPrefs.edit {
                    putInt("old_ringer_mode", currentRingerMode)
                    putInt("old_volume", currentVolume)
                    putInt("old_dnd_filter", currentDndFilter)
                    putBoolean("is_overridden", true)
                }

                // 1. Bypass DND if possible
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }

                // 2. Set Volume to MAX and Mode to NORMAL
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                audioManager.setStreamVolume(AudioManager.STREAM_RING, maxVolume, AudioManager.FLAG_SHOW_UI)
                
                Log.d("CallReceiver", "Bypassed silence successfully for $incomingNumber. Saved state: Mode=$currentRingerMode, Vol=$currentVolume, DND=$currentDndFilter")
            } catch (e: Exception) {
                Log.e("CallReceiver", "Error overriding audio settings", e)
            }
        } else {
            Log.d("CallReceiver", "No match found for $incomingNumber in $selectedNumbers")
        }
    }
}