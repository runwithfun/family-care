@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.runwithfun.familycare.records

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSError
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.fileHandleForReadingFromURL
import platform.Foundation.writeToURL
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIPresentationController
import platform.UIKit.UIViewController
import platform.UIKit.presentationController
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.darwin.NSObject

@Composable
internal actual fun rememberLocalRecordPdfPicker(onResult: (Result<LocalRecordPdf?>) -> Unit): () -> Unit {
    val host = LocalUIViewController.current
    val scope = rememberCoroutineScope()
    val resultHandler = rememberUpdatedState(onResult)
    val session = remember(host, scope) { IosRecordPdfPicker(host, scope) { resultHandler.value(it) } }
    DisposableEffect(session) { onDispose(session::dispose) }
    return remember(session) { session::present }
}

private class IosRecordPdfPicker(
    private val host: UIViewController,
    private val scope: CoroutineScope,
    private val onResult: (Result<LocalRecordPdf?>) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol, UIAdaptivePresentationControllerDelegateProtocol {
    private var controller: UIDocumentPickerViewController? = null
    private var readingPdf = false
    private var disposed = false

    fun present() {
        if (disposed || readingPdf) return
        controller?.let { previous ->
            if (previous.viewIfLoaded?.window != null || previous.isBeingPresented() || previous.isBeingDismissed()) {
                return
            }
            detach(previous)
        }
        try {
            val presenter = generateSequence(host) { it.presentedViewController }.last()
            check(presenter.viewIfLoaded?.window != null &&
                !presenter.isBeingPresented() && !presenter.isBeingDismissed()
            ) { "The system PDF picker has no visible, stable host." }
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypePDF), asCopy = true)
            controller = picker
            picker.allowsMultipleSelection = false
            picker.delegate = this
            picker.presentationController?.delegate = this
            presenter.presentViewController(picker, animated = true) {
                if (controller == picker) picker.presentationController?.delegate = this
            }
        } catch (error: Exception) {
            controller?.let(::detach)
            onResult(Result.failure(error))
        }
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        if (this.controller != controller || disposed) return
        detach(controller)
        val url = didPickDocumentsAtURLs.singleOrNull() as? NSURL
        if (url == null) {
            onResult(Result.failure(IllegalStateException("The system picker returned no single PDF URL.")))
            return
        }
        readingPdf = true
        scope.launch {
            val result = try {
                Result.success(withContext(Dispatchers.IO) { readSelectedPdf(url) })
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (error: Exception) {
                Result.failure(error)
            } finally {
                readingPdf = false
            }
            if (!disposed) onResult(result)
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        if (this.controller != controller || disposed) return
        detach(controller)
        onResult(Result.success(null))
    }

    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
        val picker = controller ?: return
        if (presentationController.presentedViewController == picker) documentPickerWasCancelled(picker)
    }

    fun dispose() {
        disposed = true
        controller?.let { picker ->
            detach(picker)
            picker.dismissViewControllerAnimated(false, completion = null)
        }
    }

    private fun detach(picker: UIDocumentPickerViewController) {
        picker.delegate = null
        picker.presentationController?.delegate = null
        controller = null
    }
}

private fun readSelectedPdf(url: NSURL): LocalRecordPdf {
    require(url.isFileURL()) { "The system PDF picker returned a non-file URL: $url." }
    val accessed = url.startAccessingSecurityScopedResource()
    try {
        var result: Result<ByteArray>? = null
        val coordinated = fileOperation("Could not coordinate access to the selected PDF at $url") { error ->
            NSFileCoordinator(filePresenter = null).coordinateReadingItemAtURL(url, 0uL, error) { coordinatedUrl ->
                result = runCatching { readBoundedRecordPdf(checkNotNull(coordinatedUrl)) }
            }
            result
        }
        return LocalRecordPdf(url.lastPathComponent?.takeIf { it.isNotBlank() } ?: "Document.pdf", coordinated.getOrThrow())
    } finally {
        if (accessed) url.stopAccessingSecurityScopedResource()
    }
}

internal actual fun recordPdfSha256(bytes: ByteArray): String {
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    digest.usePinned { pinnedDigest ->
        val destination = pinnedDigest.addressOf(0).reinterpret<UByteVar>()
        val result = if (bytes.isEmpty()) {
            CC_SHA256(null, 0u, destination)
        } else {
            bytes.usePinned { CC_SHA256(it.addressOf(0), bytes.size.toUInt(), destination) }
        }
        checkNotNull(result) { "CommonCrypto could not calculate SHA-256 for the PDF." }
    }
    return digest.recordPdfDigestHex()
}

@Composable
internal actual fun rememberImportedPdfStorage(): ImportedPdfStorage = remember { IosImportedPdfStorage() }

private class IosImportedPdfStorage : ImportedPdfStorage {
    private val manager = NSFileManager.defaultManager

    override fun write(hash: String, bytes: ByteArray) {
        requireRecordPdfHash(hash)
        requireRecordPdfDigest(hash, bytes)
        val directory = directory()
        fileOperation("Could not create the local PDF store at $directory") { error ->
            Unit.takeIf { manager.createDirectoryAtURL(directory, true, null, error) }
        }
        fileOperation("Could not exclude the local PDF store from backups at $directory") { error ->
            Unit.takeIf { directory.setResourceValue(true, NSURLIsExcludedFromBackupKey, error) }
        }
        val destination = checkNotNull(directory.URLByAppendingPathComponent("$hash.pdf"))
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        fileOperation("Could not atomically save the PDF at $destination") { error ->
            Unit.takeIf { data.writeToURL(destination, options = NSDataWritingAtomic, error = error) }
        }
    }

    override fun read(hash: String): ByteArray? {
        val url = pdfUrl(hash)
        if (!manager.fileExistsAtPath(checkNotNull(url.path))) return null
        return readBoundedRecordPdf(url).also { requireRecordPdfDigest(hash, it) }
    }

    override fun delete(hash: String) {
        val url = pdfUrl(hash)
        val path = checkNotNull(url.path)
        if (!manager.fileExistsAtPath(path)) return
        try {
            fileOperation("Could not remove the local PDF at $url") { error ->
                Unit.takeIf { manager.removeItemAtURL(url, error) }
            }
        } catch (failure: Exception) {
            if (manager.fileExistsAtPath(path)) throw failure
        }
    }

    private fun directory(): NSURL {
        val support = manager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).firstOrNull() as? NSURL
        return checkNotNull(support?.URLByAppendingPathComponent("record-pdfs", isDirectory = true)) {
            "The app's private Application Support location is missing."
        }
    }

    private fun pdfUrl(hash: String): NSURL {
        requireRecordPdfHash(hash)
        return checkNotNull(directory().URLByAppendingPathComponent("$hash.pdf"))
    }
}

private fun readBoundedRecordPdf(url: NSURL): ByteArray {
    val handle = fileOperation("Could not open the local PDF at $url") { error ->
        NSFileHandle.fileHandleForReadingFromURL(url, error)
    }
    val read = runCatching {
        val data = fileOperation("Could not read the local PDF at $url") { error ->
            handle.readDataUpToLength(MaxLocalRecordPdfBytes.toULong() + 1uL, error)
        }
        val size = data.length.toInt()
        requireRecordPdfSize(size)
        checkNotNull(data.bytes).readBytes(size)
    }
    val close = runCatching {
        fileOperation("Could not close the local PDF at $url") { error ->
            Unit.takeIf { handle.closeAndReturnError(error) }
        }
    }
    close.exceptionOrNull()?.let { failure ->
        val readFailure = read.exceptionOrNull() ?: throw failure
        readFailure.addSuppressed(failure)
    }
    return read.getOrThrow()
}

private fun <T : Any> fileOperation(
    description: String,
    operation: (CPointer<ObjCObjectVar<NSError?>>) -> T?,
): T = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    error.value = null
    val result = operation(error.ptr)
    checkNotNull(result) { "$description: ${error.value}" }
}
