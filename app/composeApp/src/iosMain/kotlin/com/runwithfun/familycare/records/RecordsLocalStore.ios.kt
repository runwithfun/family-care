package com.runwithfun.familycare.records

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

@Composable
actual fun rememberRecordsLocalStore(): RecordsLocalStore =
    remember { IosRecordsLocalStore(NSUserDefaults.standardUserDefaults) }

private class IosRecordsLocalStore(
    private val defaults: NSUserDefaults,
) : RecordsLocalStore {
    override fun read(key: String): String? = defaults.stringForKey(key)

    override fun write(key: String, value: String?) {
        val previous = defaults.objectForKey(key)
        if (value == null) {
            defaults.removeObjectForKey(key)
        } else {
            defaults.setObject(value, forKey = key)
        }
        if (defaults.synchronize()) return

        val failure = IllegalStateException("Could not persist Records key $key on this device")
        runCatching {
            if (previous == null) {
                defaults.removeObjectForKey(key)
            } else {
                defaults.setObject(previous, forKey = key)
            }
            check(defaults.synchronize()) { "Could not restore previous Records value for $key" }
        }.exceptionOrNull()?.let(failure::addSuppressed)
        throw failure
    }
}
