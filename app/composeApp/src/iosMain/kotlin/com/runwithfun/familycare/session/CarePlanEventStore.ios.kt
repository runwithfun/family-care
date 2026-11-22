@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.runwithfun.familycare.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

@Composable
internal actual fun rememberCarePlanEventStore(): CarePlanEventStore =
    remember { IosCarePlanEventStore(NSUserDefaults.standardUserDefaults) }

private class IosCarePlanEventStore(
    private val defaults: NSUserDefaults,
) : CarePlanEventStore {
    override fun load(): String? = defaults.stringForKey(CarePlanEventsKey)

    override fun save(value: String) {
        val previous = defaults.objectForKey(CarePlanEventsKey)
        defaults.setObject(value, forKey = CarePlanEventsKey)
        if (defaults.synchronize()) return

        val failure = IllegalStateException("Could not persist care-plan events on this device")
        runCatching {
            if (previous == null) {
                defaults.removeObjectForKey(CarePlanEventsKey)
            } else {
                defaults.setObject(previous, forKey = CarePlanEventsKey)
            }
            check(defaults.synchronize()) { "Could not restore previous care-plan events" }
        }.exceptionOrNull()?.let(failure::addSuppressed)
        throw failure
    }
}
