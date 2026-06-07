package com.sway.ringcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class CallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            var incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            if (TelephonyManager.EXTRA_STATE_RINGING == state) {
                if (incomingNumber == null) {
                    incomingNumber = getLastIncomingNumber(context)
                }

                incomingNumber?.let { num ->
                    val sharedPrefs = context.getSharedPreferences("RingControlPrefs", Context.MODE_PRIVATE)
                    val blacklistedNumbers = sharedPrefs.getStringSet("blacklisted_numbers", emptySet()) ?: emptySet()
                    
                    // If blacklisted, do NOTHING (effectively silencing the call as the phone was silent/vibrate)
                    if (RingControlLogic.findWhitelistedMatch(num, blacklistedNumbers, sharedPrefs) != null) {
                        Log.d("RingControl", "BLACKLIST CALL MATCH: Suppressing alert for $num")
                        return
                    }

                    checkAndOverride(context, num) 
                }
            } else if ((TelephonyManager.EXTRA_STATE_OFFHOOK == state) || (TelephonyManager.EXTRA_STATE_IDLE == state)) {
                context.stopService(Intent(context, RingService::class.java))
            }
        }
    }

    private fun getLastIncomingNumber(context: Context): String? {
        val cursor = context.contentResolver.query(
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
        
        val matchedNumber = RingControlLogic.findWhitelistedMatch(incomingNumber, selectedNumbers, sharedPrefs)

        if (matchedNumber != null) {
            val alwaysRing = sharedPrefs.getBoolean("always_ring_$matchedNumber", true)
            val ringtoneUri = sharedPrefs.getString("ring_uri_$matchedNumber", null)
            val vibPattern = sharedPrefs.getString("vib_$matchedNumber", "Default")
            
            val serviceIntent = Intent(context, RingService::class.java).apply {
                putExtra("ringtone_uri", ringtoneUri)
                putExtra("vibration_pattern", vibPattern)
                putExtra("always_ring", alwaysRing)
                putExtra("is_sms", false)
            }
            context.startForegroundService(serviceIntent)
        }
    }
}