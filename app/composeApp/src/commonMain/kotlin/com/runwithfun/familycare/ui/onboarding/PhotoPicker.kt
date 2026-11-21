package com.runwithfun.familycare.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

internal const val MaxOnboardingPhotoBytes = 8 * 1024 * 1024

internal sealed interface OnboardingPhotoResult {
    data class Selected(val bytes: ByteArray) : OnboardingPhotoResult
    data object Failed : OnboardingPhotoResult
}

@Composable
internal expect fun rememberOnboardingPhotoPicker(onResult: (OnboardingPhotoResult) -> Unit): (() -> Unit)?

internal expect fun decodeOnboardingPhoto(bytes: ByteArray): ImageBitmap?
