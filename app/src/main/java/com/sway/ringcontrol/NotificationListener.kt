package com.sway.ringcontrol

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Service that observes incoming notifications to support RCS, Facebook Messenger, and modern chat apps.
 */
class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val packageName = sbn.packageName
        
        val monitoredApps = listOf(
            "com.google.android.apps.messaging",
            "com.facebook.orca",
            "com.whatsapp",
            "com.samsung.android.messaging",
            "com.sway.ringcontrol",
        )

        if (monitoredApps.contains(packageName) && !title.isNullOrEmpty()) {
            val sharedPrefs = getSharedPreferences("RingControlPrefs", MODE_PRIVATE)
            
            // Prefetch for performance
            val whitelistedNums = sharedPrefs.getStringSet("selected_numbers", emptySet()) ?: emptySet()
            val blacklistedNums = sharedPrefs.getStringSet("blacklisted_numbers", emptySet()) ?: emptySet()
            val whitelist = RingControlLogic.buildMatchList(whitelistedNums, sharedPrefs)
            val blacklist = RingControlLogic.buildMatchList(blacklistedNums, sharedPrefs)
            val isGlobalSilence = sharedPrefs.getBoolean("global_silence", false)
            
            // 1. Blacklist Check (Always Dismiss)
            if (RingControlLogic.findWhitelistedMatch(title, blacklist) != null) {
                Log.d("RingControl", "BLACK-LISTED: Dismissing notification from $title")
                cancelNotification(sbn.key)
                return
            }

            // 2. Whitelist Check (Trigger Override)
            val matchedNumber = RingControlLogic.findWhitelistedMatch(title, whitelist)
            if (matchedNumber != null) {
                triggerOverride(matchedNumber)
            } else if (isGlobalSilence) {
                // If Global Silence is on, IMMEDIATELY dismiss the notification.
                // This fulfills the user's request for "no notifications even on the phone".
                Log.d("RingControl", "GLOBAL SILENCE: Dismissing non-whitelisted notification from $title")
                cancelNotification(sbn.key)
            }
        }
    }

    private fun triggerOverride(matchedNumber: String) {
        val sharedPrefs = getSharedPreferences("RingControlPrefs", MODE_PRIVATE)
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
