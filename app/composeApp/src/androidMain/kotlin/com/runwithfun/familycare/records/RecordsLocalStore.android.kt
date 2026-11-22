package com.runwithfun.familycare.records

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.IOException

private const val PrefsName = "familycare.records"

@Composable
actual fun rememberRecordsLocalStore(): RecordsLocalStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        AndroidRecordsLocalStore(context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE))
    }
}

private class AndroidRecordsLocalStore(
    private val preferences: SharedPreferences,
) : RecordsLocalStore {
    override fun read(key: String): String? = preferences.getString(key, null)

    override fun write(key: String, value: String?) {
        val previous = read(key)
        try {
            val editor = preferences.edit()
            if (value == null) editor.remove(key) else editor.putString(key, value)
            if (!editor.commit()) {
                throw IOException("SharedPreferences could not write Records key $key.")
            }
        } catch (failure: Exception) {
            try {
                val rollback = preferences.edit()
                if (previous == null) rollback.remove(key) else rollback.putString(key, previous)
                if (!rollback.commit()) {
                    throw IOException("SharedPreferences could not restore Records key $key.")
                }
            } catch (rollbackError: Exception) {
                failure.addSuppressed(rollbackError)
            }
            throw failure
        }
    }
}
