package com.runwithfun.familycare.ui.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.util.prefs.Preferences

@Composable
internal actual fun rememberAssistantActionStorage(): AssistantActionStorage = remember {
    val preferences = Preferences.userRoot().node("com/runwithfun/familycare")
    object : AssistantActionStorage {
        override fun read(): String? = preferences.get(ConfirmedActionKey, null)

        override fun write(actionId: String) {
            preferences.put(ConfirmedActionKey, actionId)
            preferences.flush()
        }
    }
}
