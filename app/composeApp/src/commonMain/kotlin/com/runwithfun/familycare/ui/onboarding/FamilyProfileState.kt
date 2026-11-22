package com.runwithfun.familycare.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.runwithfun.familycare.domain.CareDate
import kotlin.io.encoding.Base64

data class FamilyProfile(
    val childName: String,
    val birthDate: CareDate? = null,
    val notes: String = "",
    val photoBytes: ByteArray? = null,
)

class FamilyProfileState internal constructor(private val store: FamilyProfileStore) {
    var profile: FamilyProfile? by mutableStateOf(loadProfile())
        private set

    var saveFailed by mutableStateOf(false)
        private set

    fun save(
        childName: String,
        birthDate: CareDate? = null,
        notes: String = "",
        photoBytes: ByteArray? = null,
    ): Boolean {
        val next = try {
            val name = childName.trim()
            require(name.isNotEmpty()) { "The profile name is empty." }
            val cleanedNotes = notes.trim()
            require(cleanedNotes.length <= MaxProfileNotesLength) {
                "The profile notes are too long: ${cleanedNotes.length} characters"
            }
            require(photoBytes == null || photoBytes.size in 1..MaxOnboardingPhotoBytes) {
                "The profile photo size is outside the accepted range: ${photoBytes?.size} bytes"
            }
            FamilyProfile(
                childName = name,
                birthDate = birthDate,
                notes = cleanedNotes,
                photoBytes = photoBytes?.copyOf(),
            ).also { store.save(encodeProfile(it)) }
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
internal const val MaxProfileNotesLength = 280

internal interface FamilyProfileStore {
    fun load(): String?
    fun save(value: String)
}

@Composable
internal expect fun rememberFamilyProfileStore(): FamilyProfileStore

internal fun encodeProfile(profile: FamilyProfile): String = buildString {
    append("v2|")
    append(Base64.encode(profile.childName.encodeToByteArray()))
    append('|')
    append(profile.birthDate?.toString().orEmpty())
    append('|')
    if (profile.notes.isNotEmpty()) {
        append(Base64.encode(profile.notes.encodeToByteArray()))
    }
    append('|')
    profile.photoBytes?.let { append(Base64.encode(it)) }
}

internal fun decodeProfile(encoded: String): FamilyProfile {
    if (encoded.startsWith("v2|")) {
        val parts = encoded.split('|', limit = 5)
        require(parts.size == 5) { "The stored profile record is incomplete." }
        val name = Base64.decode(parts[1]).decodeToString(throwOnInvalidSequence = true)
        require(name.isNotBlank()) { "The stored profile name is empty." }
        val birthDate = parts[2].takeIf { it.isNotBlank() }?.let(::parseCareDate)
        val notes = if (parts[3].isEmpty()) {
            ""
        } else {
            Base64.decode(parts[3]).decodeToString(throwOnInvalidSequence = true)
        }
        require(notes.length <= MaxProfileNotesLength) { "The stored profile notes are too long." }
        val photo = decodePhotoField(parts[4])
        return FamilyProfile(name, birthDate, notes, photo)
    }

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
    val photo = decodePhotoField(encoded.substring(photoStart, photoEnd))
    return FamilyProfile(name, birthDate = null, notes = "", photoBytes = photo)
}

private fun decodePhotoField(field: String): ByteArray? {
    if (field.isEmpty()) return null
    val maxEncodedPhotoLength = (MaxOnboardingPhotoBytes + 2) / 3 * 4
    require(field.length <= maxEncodedPhotoLength) {
        "The stored profile photo is too large: ${field.length} characters"
    }
    val photo = Base64.decode(field)
    require(photo.size in 1..MaxOnboardingPhotoBytes) { "The stored profile photo size is invalid." }
    return photo
}

private fun parseCareDate(value: String): CareDate {
    val parts = value.split('-')
    require(parts.size == 3) { "The stored birth date is invalid: $value" }
    return CareDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
}

private fun logProfileError(message: String, error: Throwable) {
    println("$message\n${error.stackTraceToString()}")
}
