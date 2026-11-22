@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.runwithfun.familycare.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

@Composable
internal actual fun rememberFamilyProfileStore(): FamilyProfileStore =
    remember { IosFamilyProfileStore(NSUserDefaults.standardUserDefaults) }

private class IosFamilyProfileStore(
    private val defaults: NSUserDefaults,
) : FamilyProfileStore {
    override fun load(): String? = defaults.stringForKey(FamilyProfileKey)

    override fun save(value: String) {
        val previous = defaults.objectForKey(FamilyProfileKey)
        defaults.setObject(value, forKey = FamilyProfileKey)
        if (defaults.synchronize()) return

        val failure = IllegalStateException("Could not persist the family profile on this device")
        runCatching {
            if (previous == null) {
                defaults.removeObjectForKey(FamilyProfileKey)
            } else {
                defaults.setObject(previous, forKey = FamilyProfileKey)
            }
            check(defaults.synchronize()) { "Could not restore the previous family profile" }
        }.exceptionOrNull()?.let(failure::addSuppressed)
        throw failure
    }
}
