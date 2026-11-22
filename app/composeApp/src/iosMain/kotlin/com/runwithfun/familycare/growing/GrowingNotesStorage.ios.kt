@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.runwithfun.familycare.growing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

@Composable
internal actual fun rememberGrowingNotesStorage(): GrowingNotesStorage = remember {
    val defaults = NSUserDefaults.standardUserDefaults
    object : GrowingNotesStorage {
        override fun read(): String? = defaults.stringForKey(GrowingNotesKey)

        override fun write(value: String?) {
            if (value == null) {
                defaults.removeObjectForKey(GrowingNotesKey)
            } else {
                defaults.setObject(value, forKey = GrowingNotesKey)
            }
            check(defaults.synchronize()) { "iOS preferences could not persist the growing-up observations" }
        }
    }
}
