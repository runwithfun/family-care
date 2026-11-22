@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.runwithfun.familycare.growing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSPOSIXErrorDomain
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDevice
import platform.UIKit.UIModalPresentationPopover
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.ENOENT
import platform.posix.errno
import platform.posix.rmdir
import platform.posix.unlink

@Composable
internal actual fun rememberActivityPdfExport(onComplete: (Result<Boolean>) -> Unit): (String, ByteArray) -> Unit {
    val host = LocalUIViewController.current
    val latestCompletion = rememberUpdatedState(onComplete)
    return remember(host) {
        var exportInProgress = false
        export@{ fileName, bytes ->
            if (exportInProgress) return@export
            exportInProgress = true
            var exportDirectory: String? = null

            fun finish(result: Result<Boolean>) {
                exportDirectory?.let { directory ->
                    runCatching {
                        removeExportPath("$directory/$fileName", ::unlink)
                        removeExportPath(directory, ::rmdir)
                    }.onFailure { cleanupError ->
                        result.exceptionOrNull()?.addSuppressed(cleanupError) ?: cleanupError.printStackTrace()
                    }
                }
                exportInProgress = false
                latestCompletion.value(result)
            }

            runCatching {
                val anchor = host.requireVisibleShareAnchor()
                require(
                    fileName.endsWith(".pdf", ignoreCase = true) &&
                        '/' !in fileName && '\\' !in fileName && '\u0000' !in fileName,
                ) { "Invalid activity PDF filename: $fileName" }
                require(bytes.isNotEmpty()) { "The activity PDF is empty" }

                val tempRoot = NSTemporaryDirectory().trimEnd('/')
                val directoryPath = "$tempRoot/family-care-activity-${NSUUID().UUIDString}"
                val directory = NSURL.fileURLWithPath(directoryPath, isDirectory = true)
                requireFileOperation("Could not create the temporary activity PDF directory") { error ->
                    NSFileManager.defaultManager.createDirectoryAtURL(
                        directory, withIntermediateDirectories = false, attributes = null, error = error,
                    )
                }
                exportDirectory = directoryPath

                val pdfUrl = NSURL.fileURLWithPath("$directoryPath/$fileName", isDirectory = false)
                val data = bytes.usePinned { pinned ->
                    NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                }
                requireFileOperation("Could not write the temporary activity PDF") { error ->
                    data.writeToURL(pdfUrl, options = NSDataWritingAtomic, error = error)
                }

                val sheet = UIActivityViewController(activityItems = listOf(pdfUrl), applicationActivities = null)
                if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
                    sheet.modalPresentationStyle = UIModalPresentationPopover
                }
                sheet.popoverPresentationController?.apply {
                    sourceView = anchor
                    sourceRect = anchor.bounds
                    permittedArrowDirections = 0uL
                }
                sheet.completionWithItemsHandler = { _, completed, _, error ->
                    dispatch_async(dispatch_get_main_queue()) {
                        val result = if (error == null) {
                            Result.success(completed)
                        } else {
                            Result.failure(ActivityPdfExportException("The activity PDF share failed", error))
                        }
                        finish(result)
                    }
                }
                host.presentViewController(sheet, animated = true, completion = null)
            }.onFailure { finish(Result.failure(it)) }
        }
    }
}

private fun UIViewController.requireVisibleShareAnchor(): UIView {
    val anchor = checkNotNull(viewIfLoaded) { "The activity PDF share host view has not loaded" }
    val window = checkNotNull(anchor.window) { "The activity PDF share host is detached from its window" }
    check(
        window.windowScene?.activationState == UISceneActivationStateForegroundActive &&
            generateSequence(anchor) { it.superview }.none { it.hidden || it.alpha <= 0.0 },
    ) { "The activity PDF share host is not visible in an active window" }
    check(generateSequence(this) { it.parentViewController }.none {
        it.presentedViewController != null || it.isBeingPresented() ||
            it.isBeingDismissed() || it.isMovingFromParentViewController()
    }) { "The activity PDF share host is already presenting or transitioning" }
    return anchor
}

private fun requireFileOperation(
    operation: String,
    action: (CPointer<ObjCObjectVar<NSError?>>) -> Boolean,
) = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    error.value = null
    if (!action(error.ptr)) {
        throw error.value?.let { ActivityPdfExportException(operation, it) } ?: IllegalStateException(operation)
    }
}

private fun removeExportPath(path: String, remove: (String?) -> Int) {
    if (remove(path) == 0) return
    val code = errno
    if (code != ENOENT) {
        val error = NSError.errorWithDomain(NSPOSIXErrorDomain, code.toLong(), null)
        throw ActivityPdfExportException("Could not remove $path", error)
    }
}

private class ActivityPdfExportException(operation: String, val nativeError: NSError) : IllegalStateException(
    "$operation: ${nativeError.domain} (${nativeError.code}): ${nativeError.localizedDescription}\n${nativeError.userInfo}",
)
