package com.sway.ringcontrol

import android.content.SharedPreferences

/**
 * Data class representing the minimal info needed to match a contact.
 * Used to avoid redundant SharedPreferences lookups during matching.
 */
data class ContactMatchInfo(
    val phoneNumber: String,
    val contactName: String?,
    val customMatchName: String?
)

/**
 * Utility class containing the core matching logic for RingControl.
 * This is extracted from Receivers to allow for isolated Unit Testing.
 */
object RingControlLogic {

    /**
     * Builds a list of ContactMatchInfo from SharedPreferences for efficient matching.
     */
    fun buildMatchList(numbers: Set<String>, sharedPrefs: SharedPreferences): List<ContactMatchInfo> {
        return numbers.map { number ->
            ContactMatchInfo(
                phoneNumber = number,
                contactName = sharedPrefs.getString("name_$number", null),
                customMatchName = sharedPrefs.getString("custom_name_$number", null)
            )
        }
    }

    /**
     * Checks if the sender information matches any contact in the provided list.
     * 
     * @param senderInfo The raw string from a notification title or phone intent.
     * @param matchList The pre-fetched list of contact info.
     * @return The matched phone number if found, null otherwise.
     */
    fun findWhitelistedMatch(
        senderInfo: String?,
        matchList: List<ContactMatchInfo>
    ): String? {
        if (senderInfo.isNullOrEmpty()) return null

        val cleanIncoming = senderInfo.replace("\\D".toRegex(), "")

        for (contact in matchList) {
            val cleanSaved = contact.phoneNumber.replace("\\D".toRegex(), "")

            // 1. Strict Name Match (Case Insensitive)
            if ((!contact.customMatchName.isNullOrEmpty() && contact.customMatchName.equals(senderInfo, ignoreCase = true)) ||
                (!contact.contactName.isNullOrEmpty() && contact.contactName.equals(senderInfo, ignoreCase = true))) {
                return contact.phoneNumber
            }

            // 2. Strict 10-Digit Number Match
            if (cleanIncoming.isNotEmpty() && cleanSaved.isNotEmpty() && (cleanIncoming.length >= 7) && (cleanSaved.length >= 7)) {
                val endIncoming = cleanIncoming.takeLast(10)
                val endSaved = cleanSaved.takeLast(10)
                if (endIncoming == endSaved) {
                    return contact.phoneNumber
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
        senderInfo: String?,
        whitelist: List<ContactMatchInfo>,
        blacklist: List<ContactMatchInfo>,
        isGlobalSilenceEnabled: Boolean
    ): Boolean {
        if (senderInfo.isNullOrEmpty()) {
            // If we don't know who is calling, follow the global silence setting
            return isGlobalSilenceEnabled
        }

        // If they are explicitly blacklisted, always silence
        if (findWhitelistedMatch(senderInfo, blacklist) != null) {
            return true
        }

        // If they are whitelisted, never silence
        if (findWhitelistedMatch(senderInfo, whitelist) != null) {
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
