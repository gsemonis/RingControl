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
            val blacklistedNumbers = sharedPrefs.getStringSet("blacklisted_numbers", emptySet()) ?: emptySet()
            
            // --- BLACKLIST CHECK (The "Ghost" Logic) ---
            // We use a more flexible name match for group chats (e.g., "John Smith (Work)")
            val isBlacklisted = isPersonInSet(title, blacklistedNumbers, sharedPrefs)
            
            if (isBlacklisted) {
                Log.d("RingControl", "BLACKLIST: Killing notification from $title to prevent Android Auto pop-up.")
                // 1. Dismiss the notification instantly
                cancelNotification(sbn.key)
                
                // 2. On modern Android, we also "snooze" or silence the stream briefly
                // Note: Standard dismissal on phone usually clears the Android Auto projected UI.
                return 
            }

            // --- WHITELIST CHECK ---
            checkAndOverride(title)
        }
    }

    private fun isPersonInSet(senderInfo: String, set: Set<String>, sharedPrefs: android.content.SharedPreferences): Boolean {
        for (savedNumber in set) {
            val contactName = sharedPrefs.getString("name_$savedNumber", "") ?: ""
            val customName = sharedPrefs.getString("custom_name_$savedNumber", "") ?: ""
            
            // USE STRICT EQUALS instead of contains to prevent false positives
            if ((contactName.isNotEmpty() && contactName.equals(senderInfo, ignoreCase = true)) ||
                (customName.isNotEmpty() && customName.equals(senderInfo, ignoreCase = true))) {
                return true
            }
            
            // Number matching (last 10 digits) - only if senderInfo looks like a number
            val cleanIncoming = senderInfo.replace("\\D".toRegex(), "")
            val cleanSaved = savedNumber.replace("\\D".toRegex(), "")
            if ((cleanIncoming.length >= 7) && (cleanSaved.length >= 7) && (cleanIncoming.takeLast(10) == cleanSaved.takeLast(10))) {
                return true
            }
        }
        return false
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