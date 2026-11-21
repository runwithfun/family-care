package com.runwithfun.familycare.ui.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

@Composable
internal actual fun rememberAssistantActionStorage(): AssistantActionStorage = remember {
    val defaults = NSUserDefaults.standardUserDefaults
    object : AssistantActionStorage {
        override fun read(): String? = defaults.stringForKey(ConfirmedActionKey)

        override fun write(actionId: String) {
            defaults.setObject(actionId, forKey = ConfirmedActionKey)
        }
    }
}
