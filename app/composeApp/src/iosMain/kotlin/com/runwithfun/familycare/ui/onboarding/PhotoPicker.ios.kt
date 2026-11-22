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
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UIPresentationController
import platform.UIKit.UIViewController
import platform.UIKit.modalInPresentation
import platform.UIKit.presentationController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val ImageType = "public.image"
private const val AvatarMaxPixels = 1024

@Composable
internal actual fun rememberOnboardingPhotoPicker(
    onResult: (OnboardingPhotoResult) -> Unit,
): OnboardingPhotoPicker? {
    val host = LocalUIViewController.current
    val currentResult = rememberUpdatedState(onResult)
    val session = remember(host) { OnboardingPhotoSession(host) { currentResult.value(it) } }
    DisposableEffect(session) { onDispose { session.dispose() } }
    return remember(session) { OnboardingPhotoPicker(session::present, session::cancel) }
}

internal actual fun decodeOnboardingPhoto(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }
        .onFailure(::logPhotoFailure)
        .getOrNull()

private class OnboardingPhotoSession(
    private val host: UIViewController,
    private val onResult: (OnboardingPhotoResult) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol, UIAdaptivePresentationControllerDelegateProtocol {
    private var controller: PHPickerViewController? = null
    private var progress: NSProgress? = null
    private var activeRequest: Any? = null

    fun present() {
        val previous = controller
        if (previous != null && previous.viewIfLoaded?.window == null &&
            !previous.isBeingPresented() && !previous.isBeingDismissed()
        ) {
            // Accessibility dismissal can close the sheet without an adaptive delegate callback.
            dispose()
        }
        if (activeRequest != null) return
        val presenter = generateSequence(host) { it.presentedViewController }.last()
        if (presenter.viewIfLoaded?.window == null) {
            logPhotoFailure(IllegalStateException("The photo picker has no visible host"))
            onResult(OnboardingPhotoResult.Failed)
            return
        }
        if (presenter is PHPickerViewController || presenter.isBeingPresented() || presenter.isBeingDismissed()) {
            logPhotoFailure(IllegalStateException("The photo picker host is in a presentation transition"))
            onResult(OnboardingPhotoResult.Failed)
            return
        }

        val config = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1
        }
        val request = Any()
        val picker = PHPickerViewController(configuration = config)
        activeRequest = request
        controller = picker
        picker.delegate = this
        picker.modalPresentationStyle = UIModalPresentationPageSheet
        picker.modalInPresentation = false
        picker.presentationController?.delegate = this
        presenter.presentViewController(picker, animated = true) {
            if (activeRequest === request && controller == picker) {
                picker.presentationController?.delegate = this
            }
        }
    }

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        println("The iOS profile photo picker returned ${didFinishPicking.size} item(s).")
        val request = activeRequest ?: return
        if (controller != picker) return
        if (didFinishPicking.isNotEmpty()) onResult(OnboardingPhotoResult.Loading)
        detachDelegates(picker)
        picker.dismissViewControllerAnimated(true) {
            if (controller == picker) controller = null
            if (activeRequest === request) readSelection(request, didFinishPicking)
        }
    }

    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
        val request = activeRequest ?: return
        val picker = controller ?: return
        if (presentationController.presentedViewController != picker) return
        detachDelegates(picker)
        controller = null
        finish(request, OnboardingPhotoResult.Cancelled)
    }

    private fun readSelection(request: Any, results: List<*>) {
        if (results.isEmpty()) {
            finish(request, OnboardingPhotoResult.Cancelled)
            return
        }
        val provider = (results.firstOrNull() as? PHPickerResult)?.itemProvider
        if (provider == null || !provider.hasItemConformingToTypeIdentifier(ImageType)) {
            logPhotoFailure(IllegalStateException("Photos did not provide the selected image"))
            finish(request, OnboardingPhotoResult.Failed)
            return
        }
        progress = provider.loadFileRepresentationForTypeIdentifier(ImageType) { url, error ->
            val result = runCatching {
                // The provider removes this file after the completion handler returns.
                check(error == null) { "Photos could not provide the selected image: $error" }
                val file = checkNotNull(url) { "Photos returned no local file for the selected image" }
                OnboardingPhotoResult.Selected(encodeAvatar(file))
            }.onFailure(::logPhotoFailure).getOrElse { OnboardingPhotoResult.Failed }
            dispatch_async(dispatch_get_main_queue()) { finish(request, result) }
        }
    }

    fun cancel() {
        if (activeRequest == null) return
        dispose()
        onResult(OnboardingPhotoResult.Cancelled)
    }

    fun dispose() {
        activeRequest = null
        progress?.cancel()
        progress = null
        controller?.let { picker ->
            detachDelegates(picker)
            picker.dismissViewControllerAnimated(false, completion = null)
        }
        controller = null
    }

    private fun finish(request: Any, result: OnboardingPhotoResult) {
        if (activeRequest !== request) return
        activeRequest = null
        progress = null
        onResult(result)
    }

    private fun detachDelegates(picker: PHPickerViewController) {
        picker.delegate = null
        picker.presentationController?.delegate = null
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
