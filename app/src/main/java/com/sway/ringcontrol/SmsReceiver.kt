package com.sway.ringcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (message in messages) {
                val senderNumber = message.displayOriginatingAddress
                if (senderNumber != null) {
                    checkAndOverride(context, senderNumber)
                }
            }
        }
    }

    private fun checkAndOverride(context: Context, senderNumber: String) {
        val sharedPrefs = context.getSharedPreferences("RingControlPrefs", Context.MODE_PRIVATE)
        val whitelistedNumbers = sharedPrefs.getStringSet("selected_numbers", emptySet()) ?: emptySet()
        val blacklistedNumbers = sharedPrefs.getStringSet("blacklisted_numbers", emptySet()) ?: emptySet()

        // 1. CHECK BLACKLIST FIRST
        if (RingControlLogic.findWhitelistedMatch(senderNumber, blacklistedNumbers, sharedPrefs) != null) {
            // KILL the broadcast. This prevents the default SMS app from seeing it
            // Note: This only works for standard SMS, not RCS/Chat.
            abortBroadcast()
            return
        }

        val matchedNumber = RingControlLogic.findWhitelistedMatch(senderNumber, whitelistedNumbers, sharedPrefs)

        if (matchedNumber != null) {
            val alwaysRing = sharedPrefs.getBoolean("sms_always_ring_$matchedNumber", false)
            val soundUri = sharedPrefs.getString("sms_ring_uri_$matchedNumber", null)
            val vibPattern = sharedPrefs.getString("sms_vib_$matchedNumber", "Default")
            
            val serviceIntent = Intent(context, RingService::class.java).apply {
                putExtra("ringtone_uri", soundUri)
                putExtra("vibration_pattern", vibPattern)
                putExtra("always_ring", alwaysRing)
                putExtra("is_sms", true)
                putExtra("sender_id", matchedNumber)
            }
            context.startForegroundService(serviceIntent)
        }
    }
}