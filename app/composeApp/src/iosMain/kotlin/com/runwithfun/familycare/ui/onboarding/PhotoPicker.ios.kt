@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.runwithfun.familycare.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import org.jetbrains.skia.Image
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreGraphics.CGImageRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSProgress
import platform.Foundation.NSURL
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithURL
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.UIKit.modalInPresentation
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val ImageType = "public.image"
private const val AvatarMaxPixels = 1024

@Composable
internal actual fun rememberOnboardingPhotoPicker(
    onResult: (OnboardingPhotoResult) -> Unit,
): (() -> Unit)? {
    val host = LocalUIViewController.current
    val currentResult = rememberUpdatedState(onResult)
    val session = remember(host) { OnboardingPhotoSession(host) }
    DisposableEffect(session) { onDispose { session.dispose() } }
    return { session.present { currentResult.value(it) } }
}

internal actual fun decodeOnboardingPhoto(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }
        .onFailure(::logPhotoFailure)
        .getOrNull()

private class OnboardingPhotoSession(
    private val host: UIViewController,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    private var controller: PHPickerViewController? = null
    private var progress: NSProgress? = null
    private var resultCallback: ((OnboardingPhotoResult) -> Unit)? = null

    fun present(onResult: (OnboardingPhotoResult) -> Unit) {
        if (resultCallback != null) return
        val presenter = generateSequence(host) { it.presentedViewController }.last()
        if (presenter.view.window == null) {
            logPhotoFailure(IllegalStateException("The photo picker has no visible host"))
            onResult(OnboardingPhotoResult.Failed)
            return
        }
        val config = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1
        }
        val picker = PHPickerViewController(configuration = config)
        resultCallback = onResult
        controller = picker
        picker.delegate = this
        picker.modalInPresentation = true
        presenter.presentViewController(picker, animated = true, completion = null)
    }

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        if (controller !== picker) return
        picker.delegate = null
        picker.dismissViewControllerAnimated(true) {
            controller = null
            if (resultCallback != null) readSelection(didFinishPicking)
        }
    }

    private fun readSelection(results: List<*>) {
        if (results.isEmpty()) {
            finish(OnboardingPhotoResult.Cancelled)
            return
        }
        val provider = (results.firstOrNull() as? PHPickerResult)?.itemProvider
        if (provider == null || !provider.hasItemConformingToTypeIdentifier(ImageType)) {
            logPhotoFailure(IllegalStateException("Photos did not provide the selected image"))
            finish(OnboardingPhotoResult.Failed)
            return
        }
        progress = provider.loadFileRepresentationForTypeIdentifier(ImageType) { url, error ->
            val result = runCatching {
                // The provider removes this file after the completion handler returns.
                check(error == null) { "Photos could not provide the selected image: $error" }
                val file = checkNotNull(url) { "Photos returned no local file for the selected image" }
                OnboardingPhotoResult.Selected(encodeAvatar(file))
            }.onFailure(::logPhotoFailure).getOrElse { OnboardingPhotoResult.Failed }
            dispatch_async(dispatch_get_main_queue()) { finish(result) }
        }
    }

    fun dispose() {
        resultCallback = null
        progress?.cancel()
        progress = null
        controller?.delegate = null
        controller?.dismissViewControllerAnimated(false, completion = null)
        controller = null
    }

    private fun finish(result: OnboardingPhotoResult) {
        val callback = resultCallback
        resultCallback = null
        progress = null
        callback?.invoke(result)
    }
}

private fun encodeAvatar(url: NSURL): ByteArray {
    val sourceUrl = checkNotNull(CFBridgingRetain(url))
    val source = try {
        checkNotNull(CGImageSourceCreateWithURL(sourceUrl.reinterpret(), null)) {
            "ImageIO could not open the selected photo"
        }
    } finally {
        CFRelease(sourceUrl)
    }
    try {
        val thumbnail = memScoped {
            val options = checkNotNull(CFDictionaryCreateMutable(
                null, 3, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
            ))
            try {
                val pixels = alloc<IntVar> { value = AvatarMaxPixels }
                val size = checkNotNull(CFNumberCreate(null, kCFNumberIntType, pixels.ptr))
                try {
                    CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, size)
                    CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
                    CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
                    checkNotNull(CGImageSourceCreateThumbnailAtIndex(source, 0uL, options)) {
                        "ImageIO could not downsample the selected photo"
                    }
                } finally {
                    CFRelease(size)
                }
            } finally {
                CFRelease(options)
            }
        }
        try {
            val jpeg = checkNotNull(UIImageJPEGRepresentation(UIImage.imageWithCGImage(thumbnail), 0.85)) {
                "Could not encode the selected photo"
            }
            check(jpeg.length in 1uL..MaxOnboardingPhotoBytes.toULong()) {
                "Encoded photo size is outside the accepted range: ${jpeg.length} bytes"
            }
            return checkNotNull(jpeg.bytes).readBytes(jpeg.length.toInt())
        } finally {
            CGImageRelease(thumbnail)
        }
    } finally {
        CFRelease(source)
    }
}

private fun logPhotoFailure(error: Throwable) {
    println(error.stackTraceToString())
}
