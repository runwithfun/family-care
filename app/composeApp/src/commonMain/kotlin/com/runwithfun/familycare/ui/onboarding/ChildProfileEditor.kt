package com.runwithfun.familycare.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.ui.home.ProfileAvatar

data class ChildProfileDraft(
    val name: String,
    val birthDateText: String,
    val notes: String,
    val photoBytes: ByteArray?,
    val photoPreview: ImageBitmap?,
)

@Composable
fun rememberChildProfileDraft(
    initialName: String = "Maya",
    initialBirthDate: CareDate? = CareDate(2026, 9, 28),
    initialNotes: String = "",
    initialPhoto: ByteArray? = null,
): ChildProfileDraftState {
    val preview = remember(initialPhoto) { initialPhoto?.let(::decodeOnboardingPhoto) }
    return remember(initialName, initialBirthDate, initialNotes, initialPhoto) {
        ChildProfileDraftState(
            name = initialName,
            birthDateText = initialBirthDate?.toString().orEmpty(),
            notes = initialNotes,
            photoBytes = initialPhoto,
            photoPreview = preview,
        )
    }
}

class ChildProfileDraftState(
    name: String,
    birthDateText: String,
    notes: String,
    photoBytes: ByteArray?,
    photoPreview: ImageBitmap?,
) {
    var name by mutableStateOf(name)
    var birthDateText by mutableStateOf(birthDateText)
    var notes by mutableStateOf(notes)
    var photoBytes by mutableStateOf(photoBytes)
    var photoPreview by mutableStateOf(photoPreview)
    var photoFailed by mutableStateOf(false)
    var photoPending by mutableStateOf(false)
    var birthDateError by mutableStateOf<String?>(null)

    internal fun applyPhotoResult(result: OnboardingPhotoResult) {
        photoPending = result == OnboardingPhotoResult.Loading
        when (result) {
            OnboardingPhotoResult.Loading, OnboardingPhotoResult.Cancelled -> photoFailed = false
            is OnboardingPhotoResult.Selected -> {
                val image = decodeOnboardingPhoto(result.bytes)
                if (image != null) {
                    photoBytes = result.bytes
                    photoPreview = image
                }
                photoFailed = image == null
            }
            OnboardingPhotoResult.Failed -> photoFailed = true
        }
    }

    fun clearPhoto() {
        photoBytes = null
        photoPreview = null
        photoFailed = false
        photoPending = false
    }

    fun validatedBirthDate(): CareDate? {
        val raw = birthDateText.trim()
        if (raw.isEmpty()) {
            birthDateError = "Add the child's date of birth (YYYY-MM-DD)."
            return null
        }
        return try {
            val parts = raw.split('-')
            require(parts.size == 3) { "invalid" }
            CareDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()).also {
                birthDateError = null
            }
        } catch (_: Exception) {
            birthDateError = "Use a valid date as YYYY-MM-DD."
            null
        }
    }

    fun toDraft(): ChildProfileDraft = ChildProfileDraft(
        name = name.trim(),
        birthDateText = birthDateText.trim(),
        notes = notes.trim(),
        photoBytes = photoBytes,
        photoPreview = photoPreview,
    )
}

@Composable
fun ChildProfileFields(
    state: ChildProfileDraftState,
    modifier: Modifier = Modifier,
    onSubmit: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val photoPicker = rememberOnboardingPhotoPicker(state::applyPhotoResult)
    DisposableEffect(state) {
        onDispose {
            if (state.photoPending) state.applyPhotoResult(OnboardingPhotoResult.Cancelled)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileAvatar(state.photoPreview, modifier = Modifier.size(78.dp))
            if (photoPicker != null) {
                Column {
                    TextButton(
                        onClick = photoPicker.launch,
                        enabled = !state.photoPending,
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.secondary),
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) {
                        Text(if (state.photoBytes == null) "Add photo" else "Change photo")
                    }
                    if (state.photoPending) {
                        TextButton(
                            onClick = photoPicker.cancel,
                            colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurfaceVariant),
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) {
                            Text("Cancel photo")
                        }
                    } else if (state.photoBytes != null) {
                        TextButton(
                            onClick = state::clearPhoto,
                            colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurfaceVariant),
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) {
                            Text("Remove photo")
                        }
                    } else {
                        Text(
                            "Optional",
                            modifier = Modifier.padding(start = 12.dp),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        if (state.photoPending) {
            Text(
                "Preparing photo…",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.photoFailed) {
            Text(
                "Couldn't open that photo. Choose another or continue.",
                color = colors.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedTextField(
            value = state.name,
            onValueChange = { state.name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Child's name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            colors = profileFieldColors(),
        )
        OutlinedTextField(
            value = state.birthDateText,
            onValueChange = {
                state.birthDateText = it
                state.birthDateError = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Date of birth (YYYY-MM-DD)") },
            supportingText = {
                Text(state.birthDateError ?: "Used for age stages and the day plan.")
            },
            isError = state.birthDateError != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            colors = profileFieldColors(),
        )
        OutlinedTextField(
            value = state.notes,
            onValueChange = { if (it.length <= MaxProfileNotesLength) state.notes = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Notes about the child (optional)") },
            supportingText = {
                Text("${state.notes.length}/$MaxProfileNotesLength")
            },
            minLines = 2,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                focus.clearFocus()
                onSubmit()
            }),
            colors = profileFieldColors(),
        )
    }
}

@Composable
private fun profileFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.secondary,
    focusedLabelColor = MaterialTheme.colorScheme.secondary,
    cursorColor = MaterialTheme.colorScheme.secondary,
)
