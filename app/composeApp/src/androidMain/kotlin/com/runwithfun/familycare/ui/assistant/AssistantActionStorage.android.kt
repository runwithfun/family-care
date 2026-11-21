package com.runwithfun.familycare.ui.assistant

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberAssistantActionStorage(): AssistantActionStorage {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        val preferences = context.getSharedPreferences("family-care-assistant", Context.MODE_PRIVATE)
        object : AssistantActionStorage {
            override fun read(): String? = preferences.getString(ConfirmedActionKey, null)

            override fun write(actionId: String) {
                check(preferences.edit().putString(ConfirmedActionKey, actionId).commit()) {
                    "Assistant action could not be written to device preferences"
                }
            }
        }
    }
}
