package com.runwithfun.familycare.ui.onboarding

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberFamilyProfileStore(): FamilyProfileStore = DesktopFamilyProfileStore

private object DesktopFamilyProfileStore : FamilyProfileStore {
    private var profile: String? = null

    override fun load(): String? = profile

    override fun save(value: String) {
        profile = value
    }
}
