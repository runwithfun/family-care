package com.runwithfun.familycare.session

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.IOException

@Composable
internal actual fun rememberCarePlanEventStore(): CarePlanEventStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        AndroidCarePlanEventStore(context.getSharedPreferences("family-care-plan-events", Context.MODE_PRIVATE))
    }
}

private class AndroidCarePlanEventStore(
    private val preferences: SharedPreferences,
) : CarePlanEventStore {
    override fun load(): String? = preferences.getString(CarePlanEventsKey, null)

    override fun save(value: String) {
        val previous = load()
        try {
            if (!preferences.edit().putString(CarePlanEventsKey, value).commit()) {
                throw IOException("SharedPreferences could not write care-plan events.")
            }
        } catch (failure: Exception) {
            try {
                if (!preferences.edit().putString(CarePlanEventsKey, previous).commit()) {
                    throw IOException("SharedPreferences could not restore previous care-plan events.")
                }
            } catch (rollbackError: Exception) {
                failure.addSuppressed(rollbackError)
            }
            throw failure
        }
    }
}
