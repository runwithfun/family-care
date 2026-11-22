package com.runwithfun.familycare.ui.onboarding

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.IOException

@Composable
internal actual fun rememberFamilyProfileStore(): FamilyProfileStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        AndroidFamilyProfileStore(context.getSharedPreferences(FamilyProfileKey, Context.MODE_PRIVATE))
    }
}

private class AndroidFamilyProfileStore(private val preferences: SharedPreferences) : FamilyProfileStore {
    override fun load(): String? = preferences.getString(FamilyProfileKey, null)

    override fun save(value: String) {
        val previous = load()
        try {
            if (!preferences.edit().putString(FamilyProfileKey, value).commit()) {
                throw IOException("SharedPreferences could not write the family profile.")
            }
        } catch (failure: Exception) {
            try {
                if (!preferences.edit().putString(FamilyProfileKey, previous).commit()) {
                    throw IOException("SharedPreferences could not restore the previous family profile.")
                }
            } catch (rollbackError: Exception) {
                failure.addSuppressed(rollbackError)
            }
            throw failure
        }
    }
}
