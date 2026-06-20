package com.sway.ringcontrol

import android.content.SharedPreferences

/**
 * Utility class containing the core matching logic for RingControl.
 * This is extracted from Receivers to allow for isolated Unit Testing.
 */
object RingControlLogic {

    /**
     * Checks if the sender information matches any contact in the whitelist.
     * 
     * @param senderInfo The raw string from a notification title or phone intent.
     * @param whitelistedNumbers The set of phone numbers saved in preferences.
     * @param sharedPrefs Access to stored names and custom matching names.
     * @return The matched phone number if found, null otherwise.
     */
    fun findWhitelistedMatch(
        senderInfo: String,
        whitelistedNumbers: Set<String>,
        sharedPrefs: SharedPreferences,
    ): String? {
        val cleanIncoming = senderInfo.replace("\\D".toRegex(), "")

        for (savedNumber in whitelistedNumbers) {
            val cleanSaved = savedNumber.replace("\\D".toRegex(), "")
            val customMatchName = sharedPrefs.getString("custom_name_$savedNumber", "")
            val contactName = sharedPrefs.getString("name_$savedNumber", "")

            // 1. Strict Name Match (Case Insensitive)
            if ((!customMatchName.isNullOrEmpty() && customMatchName.equals(senderInfo, ignoreCase = true)) ||
                (!contactName.isNullOrEmpty() && contactName.equals(senderInfo, ignoreCase = true))) {
                return savedNumber
            }

            // 2. Strict 10-Digit Number Match
            if ((cleanIncoming.length >= 7) && (cleanSaved.length >= 7)) {
                val endIncoming = cleanIncoming.takeLast(10)
                val endSaved = cleanSaved.takeLast(10)
                if (endIncoming == endSaved) {
                    return savedNumber
                }
            }
        }
        return null
    }

    /**
     * Checks if a contact should be silenced based on the global silence flag
     * and the contact's presence in the whitelist or blacklist.
     */
    fun shouldSilence(
        senderInfo: String,
        whitelistedNumbers: Set<String>,
        blacklistedNumbers: Set<String>,
        isGlobalSilenceEnabled: Boolean,
        sharedPrefs: SharedPreferences
    ): Boolean {
        // If they are explicitly blacklisted, always silence
        if (findWhitelistedMatch(senderInfo, blacklistedNumbers, sharedPrefs) != null) {
            return true
        }

        // If they are whitelisted, never silence
        if (findWhitelistedMatch(senderInfo, whitelistedNumbers, sharedPrefs) != null) {
            return false
        }

        // If not whitelisted and global silence is ON, silence them
        return isGlobalSilenceEnabled
    }

    /**
     * Normalizes a phone number to digits only for comparison.
     */
    fun cleanNumber(number: String): String {
        return number.replace("\\D".toRegex(), "")
    }
}
