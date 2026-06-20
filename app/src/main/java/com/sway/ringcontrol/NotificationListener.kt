package com.sway.ringcontrol

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Service that observes incoming notifications to support RCS, Facebook Messenger, and modern chat apps.
 * Now optimized for Android Auto/Car Mode suppression.
 */
class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val packageName = sbn.packageName
        
        // Define apps we want to monitor
        val monitoredApps = listOf(
            "com.google.android.apps.messaging",
            "com.facebook.orca",
            "com.whatsapp",
            "com.samsung.android.messaging",
            "com.sway.ringcontrol", // Added for in-app testing
        )

        if (monitoredApps.contains(packageName) && title.isNotEmpty()) {
            val sharedPrefs = getSharedPreferences("RingControlPrefs", MODE_PRIVATE)
            val whitelistedNumbers = sharedPrefs.getStringSet("selected_numbers", emptySet()) ?: emptySet()
            val blacklistedNumbers = sharedPrefs.getStringSet("blacklisted_numbers", emptySet()) ?: emptySet()
            val isGlobalSilence = sharedPrefs.getBoolean("global_silence", false)
            
            // --- SILENCE CHECK ---
            val shouldSilence = RingControlLogic.shouldSilence(title, whitelistedNumbers, blacklistedNumbers, isGlobalSilence, sharedPrefs)
            
            if (shouldSilence) {
                Log.d("RingControl", "SILENCING: Dismissing notification from $title")
                // IMMEDIATELY dismiss the notification
                cancelNotification(sbn.key)
                return 
            }

            // --- WHITELIST CHECK ---
            checkAndOverride(title)
        }
    }

    private fun checkAndOverride(senderInfo: String) {
        val sharedPrefs = getSharedPreferences("RingControlPrefs", MODE_PRIVATE)
        val whitelistedNumbers = sharedPrefs.getStringSet("selected_numbers", emptySet()) ?: emptySet()
        
        val matchedNumber = RingControlLogic.findWhitelistedMatch(senderInfo, whitelistedNumbers, sharedPrefs)

        if (matchedNumber != null) {
            val alwaysRing = sharedPrefs.getBoolean("sms_always_ring_$matchedNumber", false)
            val soundUri = sharedPrefs.getString("sms_ring_uri_$matchedNumber", null)
            val vibPattern = sharedPrefs.getString("sms_vib_$matchedNumber", "Default")
            
            val serviceIntent = Intent(this, RingService::class.java).apply {
                putExtra("ringtone_uri", soundUri)
                putExtra("vibration_pattern", vibPattern)
                putExtra("always_ring", alwaysRing)
                putExtra("is_sms", true)
                putExtra("sender_id", matchedNumber)
            }
            startForegroundService(serviceIntent)
        }
    }
}