package com.runwithfun.familycare.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.io.encoding.Base64

data class FamilyProfile(val childName: String, val photoBytes: ByteArray?)

class FamilyProfileState internal constructor(private val store: FamilyProfileStore) {
    var profile: FamilyProfile? by mutableStateOf(loadProfile())
        private set

    var saveFailed by mutableStateOf(false)
        private set

    fun save(childName: String, photoBytes: ByteArray?): Boolean {
        val next = try {
            val name = childName.trim()
            require(name.isNotEmpty()) { "The profile name is empty." }
            require(photoBytes == null || photoBytes.size in 1..MaxOnboardingPhotoBytes) {
                "The profile photo size is outside the accepted range: ${photoBytes?.size} bytes"
            }
            FamilyProfile(name, photoBytes?.copyOf()).also { store.save(encodeProfile(it)) }
        } catch (error: Exception) {
            logProfileError("Could not save the family profile.", error)
            saveFailed = true
            return false
        }
        profile = next
        saveFailed = false
        return true
    }

    private fun loadProfile(): FamilyProfile? = try {
        store.load()?.let(::decodeProfile)
    } catch (error: Exception) {
        logProfileError("Could not read the family profile.", error)
        null
    }
}

@Composable
fun rememberFamilyProfileState(): FamilyProfileState {
    val store = rememberFamilyProfileStore()
    return remember(store) { FamilyProfileState(store) }
}

internal const val FamilyProfileKey = "familycare.profile"

internal interface FamilyProfileStore {
    fun load(): String?
    fun save(value: String)
}

@Composable
internal expect fun rememberFamilyProfileStore(): FamilyProfileStore

private fun encodeProfile(profile: FamilyProfile): String = buildString {
    append(Base64.encode(profile.childName.encodeToByteArray()))
    append(':')
    profile.photoBytes?.let { append(Base64.encode(it)) }
}

private fun decodeProfile(encoded: String): FamilyProfile {
    val currentSeparator = encoded.indexOf(':')
    val separator = if (currentSeparator >= 0) currentSeparator else encoded.indexOf('\n')
    require(separator >= 0) { "The stored profile record is missing its separator." }
    val name = Base64.decode(encoded.substring(0, separator)).decodeToString(throwOnInvalidSequence = true)
    require(name.isNotBlank()) { "The stored profile name is empty." }

    val photoStart = separator + 1
    // Android's preference serializer can indent the legacy value after its trailing newline.
    val photoEnd = if (currentSeparator >= 0) {
        encoded.length
    } else {
        maxOf(photoStart, encoded.indexOfLast { !it.isWhitespace() } + 1)
    }
    val photoLength = photoEnd - photoStart
    val maxEncodedPhotoLength = (MaxOnboardingPhotoBytes + 2) / 3 * 4
    require(photoLength <= maxEncodedPhotoLength) { "The stored profile photo is too large: $photoLength characters" }
    val photo = if (photoLength == 0) null else Base64.decode(encoded.substring(photoStart, photoEnd))
    require(photo == null || photo.size in 1..MaxOnboardingPhotoBytes) { "The stored profile photo size is invalid." }
    return FamilyProfile(name, photo)
}

private fun logProfileError(message: String, error: Throwable) {
    println("$message\n${error.stackTraceToString()}")
}
