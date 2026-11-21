package com.runwithfun.familycare.ui.onboarding

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PhotoLogTag = "FamilyCare.Onboarding"
private const val ProfilePhotoDimension = 1024

@Composable
internal actual fun rememberOnboardingPhotoPicker(onResult: (OnboardingPhotoResult) -> Unit): (() -> Unit)? {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    try {
                        val bytes = resolver.openInputStream(uri)?.use { it.readPhotoBytes() }
                            ?: throw IOException("The selected photo could not be opened.")
                        val bitmap = decodeBitmap(bytes)
                        val photo = ByteArrayOutputStream().use { output ->
                            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                                throw IOException("The selected photo could not be encoded.")
                            }
                            output.toByteArray()
                        }
                        OnboardingPhotoResult.Selected(photo)
                    } catch (canceled: CancellationException) {
                        throw canceled
                    } catch (error: Exception) {
                        Log.e(PhotoLogTag, "Could not read the selected photo.", error)
                        OnboardingPhotoResult.Failed
                    }
                }
                onResult(result)
            }
        }
    }
    return {
        try {
            picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        } catch (error: Exception) {
            Log.e(PhotoLogTag, "Could not open the photo picker.", error)
            onResult(OnboardingPhotoResult.Failed)
        }
    }
}

internal actual fun decodeOnboardingPhoto(bytes: ByteArray): ImageBitmap? = try {
    decodeBitmap(bytes).asImageBitmap()
} catch (error: Exception) {
    Log.e(PhotoLogTag, "Could not decode the profile photo.", error)
    null
}

private fun decodeBitmap(bytes: ByteArray): Bitmap {
    require(bytes.isNotEmpty() && bytes.size <= MaxOnboardingPhotoBytes) { "Invalid photo size: ${bytes.size}" }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val largest = maxOf(width, height)
            if (largest > ProfilePhotoDimension) {
                decoder.setTargetSize(
                    maxOf(1, (width.toLong() * ProfilePhotoDimension / largest).toInt()),
                    maxOf(1, (height.toLong() * ProfilePhotoDimension / largest).toInt()),
                )
            }
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val largest = maxOf(bounds.outWidth, bounds.outHeight)
    if (largest <= 0) throw IOException("The photo decoder could not read the image dimensions.")
    var sample = 1
    while (largest / sample > ProfilePhotoDimension) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: throw IOException("The photo decoder could not read the image.")
}

private fun InputStream.readPhotoBytes(): ByteArray = ByteArrayOutputStream().use { output ->
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val count = read(buffer)
        if (count == -1) break
        if (output.size() + count > MaxOnboardingPhotoBytes) {
            throw IOException("The selected photo exceeds $MaxOnboardingPhotoBytes bytes.")
        }
        output.write(buffer, 0, count)
    }
    output.toByteArray()
}
