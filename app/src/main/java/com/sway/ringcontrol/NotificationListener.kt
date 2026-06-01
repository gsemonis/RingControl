package com.sway.ringcontrol

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.edit

class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val category = sbn.notification.category
        
        // Listen to messaging apps (Google, Samsung, or anything categorized as a message)
        val isMessagingApp = sbn.packageName.contains("messaging") || 
                            sbn.packageName.contains("mms") || 
                            category == Notification.CATEGORY_MESSAGE

        if (isMessagingApp && title.isNotEmpty()) {
            Log.d("RingControl", "Message Notification Detected | From: $title | App: ${sbn.packageName}")
            checkAndOverride(title)
        }
    }

    private fun checkAndOverride(senderInfo: String) {
        val sharedPrefs = getSharedPreferences("RingControlPrefs", Context.MODE_PRIVATE)
        val selectedNumbers = sharedPrefs.getStringSet("selected_numbers", emptySet()) ?: emptySet()
        
        // 1. Try to find a match by Number (stripping formatting)
        val cleanIncoming = senderInfo.replace("\\D".toRegex(), "")
        
        var matchedNumber: String? = null
        
        // Search whitelist for a match
        for (savedNumber in selectedNumbers) {
            val cleanSaved = savedNumber.replace("\\D".toRegex(), "")
            
            // Match by digits (if sender info contains digits)
            if (cleanIncoming.isNotEmpty() && cleanSaved.isNotEmpty()) {
                if (cleanSaved.contains(cleanIncoming) || cleanIncoming.contains(cleanSaved)) {
                    matchedNumber = savedNumber
                    break
                }
            }
            
            // Match by Name (Google Messages often puts the Contact Name in the title)
            val associatedName = sharedPrefs.getString("name_$savedNumber", "")
            if (!associatedName.isNullOrEmpty() && associatedName.equals(senderInfo, ignoreCase = true)) {
                matchedNumber = savedNumber
                break
            }
        }

        if (matchedNumber != null) {
            Log.d("RingControl", "!!! MATCH FOUND !!! Whitelisted contact matched: $senderInfo")
            triggerAlert(matchedNumber)
        } else {
            Log.d("RingControl", "No match found in whitelist for: $senderInfo")
        }
    }

    private fun triggerAlert(matchedNumber: String) {
        val sharedPrefs = getSharedPreferences("RingControlPrefs", Context.MODE_PRIVATE)
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        try {
            val alwaysRing = sharedPrefs.getBoolean("sms_always_ring_$matchedNumber", false)
            
            if (alwaysRing) {
                // Save current state for restoration
                val currentRingerMode = audioManager.ringerMode
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                val currentDndFilter = notificationManager.currentInterruptionFilter

                sharedPrefs.edit {
                    putInt("old_ringer_mode", currentRingerMode)
                    putInt("old_volume", currentVolume)
                    putInt("old_dnd_filter", currentDndFilter)
                    putBoolean("is_overridden", true)
                }

                // Bypass DND
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    notificationManager.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
                }

                // Force Loud
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                val maxNotif = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, maxNotif, 0)
            }

            // Start RingService to play custom sound/vibration
            val soundUri = sharedPrefs.getString("sms_ring_uri_$matchedNumber", null)
            val vibPattern = sharedPrefs.getString("sms_vib_$matchedNumber", "Default")
            
            val serviceIntent = Intent(this, RingService::class.java).apply {
                putExtra("ringtone_uri", soundUri)
                putExtra("vibration_pattern", vibPattern)
                putExtra("is_sms", true)
            }
            
            startForegroundService(serviceIntent)
        } catch (e: Exception) {
            Log.e("RingControl", "Error triggering alert", e)
        }
    }
}