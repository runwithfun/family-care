package com.runwithfun.familycare.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

@Composable
internal actual fun rememberOnboardingPhotoPicker(onResult: (OnboardingPhotoResult) -> Unit): (() -> Unit)? = null

internal actual fun decodeOnboardingPhoto(bytes: ByteArray): ImageBitmap? = try {
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
} catch (error: Exception) {
    System.err.println(error.stackTraceToString())
    null
}
