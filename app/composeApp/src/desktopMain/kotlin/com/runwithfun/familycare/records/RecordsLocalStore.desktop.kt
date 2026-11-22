package com.runwithfun.familycare.records

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.util.prefs.Preferences

@Composable
actual fun rememberRecordsLocalStore(): RecordsLocalStore = remember {
    val preferences = Preferences.userRoot().node("com/runwithfun/familycare/records")
    object : RecordsLocalStore {
        override fun read(key: String): String? = preferences.get(key, null)

        override fun write(key: String, value: String?) {
            if (value == null) {
                preferences.remove(key)
            } else {
                preferences.put(key, value)
            }
            preferences.flush()
        }
    }
}
