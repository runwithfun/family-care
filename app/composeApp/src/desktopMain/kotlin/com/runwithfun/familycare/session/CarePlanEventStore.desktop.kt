package com.runwithfun.familycare.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.util.prefs.Preferences

@Composable
internal actual fun rememberCarePlanEventStore(): CarePlanEventStore = remember {
    val preferences = Preferences.userRoot().node("com/runwithfun/familycare")
    object : CarePlanEventStore {
        override fun load(): String? = preferences.get(CarePlanEventsKey, null)

        override fun save(value: String) {
            preferences.put(CarePlanEventsKey, value)
            preferences.flush()
        }
    }
}
