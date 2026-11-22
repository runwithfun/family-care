package com.runwithfun.familycare.growing

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberGrowingNotesStorage(): GrowingNotesStorage {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        val preferences = context.getSharedPreferences(GrowingNotesKey, Context.MODE_PRIVATE)
        object : GrowingNotesStorage {
            override fun read(): String? = preferences.getString(GrowingNotesKey, null)

            override fun write(value: String?) {
                check(preferences.edit().putString(GrowingNotesKey, value).commit()) {
                    "Android preferences could not persist the growing-up observations"
                }
            }
        }
    }
}
