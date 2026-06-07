package com.sway.ringcontrol

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

/**
 * Service that allows the app to intercept and block calls before the phone rings.
 */
class CallScreeningServiceImpl : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val incomingNumber = callDetails.handle?.schemeSpecificPart ?: return
        val sharedPrefs = getSharedPreferences("RingControlPrefs", MODE_PRIVATE)
        val blacklistedNumbers = sharedPrefs.getStringSet("blacklisted_numbers", emptySet()) ?: emptySet()

        // Reuse our existing matching logic
        val isBlacklisted = RingControlLogic.findWhitelistedMatch(incomingNumber, blacklistedNumbers, sharedPrefs) != null

        val response = CallResponse.Builder()
        if (isBlacklisted) {
            Log.d("RingControl", "SCREENING: Silencing blacklisted call from $incomingNumber")
            response.apply {
                setDisallowCall(true)      // Don't let the call through
                setSkipCallLog(false)      // Still show in log so user knows it happened
                setSkipNotification(true)  // DO NOT show a notification
                setRejectCall(true)        // Reject it (send to voicemail)
            }
        }

        respondToCall(callDetails, response.build())
    }
}