package com.sway.ringcontrol

import android.app.Application
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for RingControlActivity.
 * Handles contact fetching and state management to keep the Activity clean.
 */
class RingControlViewModel(application: Application) : AndroidViewModel(application) {
    
    var contacts by mutableStateOf<List<RingControlContactData>>(emptyList())
        private set
    
    var isLoading by mutableStateOf(false)
        private set

    /**
     * Fetches contacts from the phone asynchronously.
     */
    fun refreshContacts() {
        viewModelScope.launch {
            isLoading = true
            contacts = fetchPhoneContacts(getApplication())
            isLoading = false
        }
    }

    private suspend fun fetchPhoneContacts(context: Context): List<RingControlContactData> {
        return withContext(Dispatchers.IO) {
            val contactMap = mutableMapOf<String, MutableList<String>>()
            val nameMap = mutableMapOf<String, String>()
            val contentResolver = context.contentResolver
            
            val cursor: Cursor? = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)

                while (it.moveToNext()) {
                    if (nameIndex >= 0 && numberIndex >= 0 && idIndex >= 0) {
                        val name = it.getString(nameIndex) ?: "Unknown"
                        val number = it.getString(numberIndex)?.replace("\\s".toRegex(), "") ?: ""
                        val id = it.getString(idIndex) ?: ""
                        if (number.isNotEmpty()) {
                            contactMap.getOrPut(id) { mutableListOf() }.add(number)
                            nameMap[id] = name
                        }
                    }
                }
            }
            contactMap.map { (id, numbers) ->
                RingControlContactData(id, nameMap[id] ?: "Unknown", numbers.distinct())
            }.sortedBy { it.name }
        }
    }
}
