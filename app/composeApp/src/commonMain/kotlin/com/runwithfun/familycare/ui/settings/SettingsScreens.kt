package com.runwithfun.familycare.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.ui.onboarding.ChildProfileFields
import com.runwithfun.familycare.ui.onboarding.FamilyProfile
import com.runwithfun.familycare.ui.onboarding.rememberChildProfileDraft

@Composable
fun SettingsScreen(
    profile: FamilyProfile,
    onEditProfile: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Manage the local child profile used by Home, Growing up, and Records. Family Care keeps this data on the device.",
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = colors.surfaceVariant,
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(profile.childName, style = MaterialTheme.typography.titleLarge)
                profile.birthDate?.let {
                    Text("Born $it", color = colors.onSurfaceVariant)
                }
                if (profile.notes.isNotBlank()) {
                    Text(profile.notes, color = colors.onSurfaceVariant)
                }
            }
        }
        Button(
            onClick = onEditProfile,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
            ),
        ) {
            Text("Edit profile")
        }
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 44.dp)) {
            Text("Done")
        }
    }
}

@Composable
fun EditProfileScreen(
    profile: FamilyProfile,
    saveFailed: Boolean,
    onSave: (String, CareDate, String, ByteArray?) -> Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val draft = rememberChildProfileDraft(
        initialName = profile.childName,
        initialBirthDate = profile.birthDate,
        initialNotes = profile.notes,
        initialPhoto = profile.photoBytes,
    )
    var localSaveFailed by remember { mutableStateOf(false) }

    fun submit() {
        val birthDate = draft.validatedBirthDate()
        val name = draft.name.trim()
        if (name.isEmpty() || birthDate == null || draft.photoPending) return
        focus.clearFocus()
        val ok = onSave(name, birthDate, draft.notes.trim(), draft.photoBytes)
        localSaveFailed = !ok
        if (ok) onBack()
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Update the child details used across the day plan.",
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        ChildProfileFields(state = draft, onSubmit = ::submit)
        if (saveFailed || localSaveFailed) {
            Text(
                "Couldn't save the profile. Try again.",
                color = colors.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = ::submit,
            enabled = draft.name.isNotBlank() && !draft.photoPending,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
                disabledContainerColor = colors.surfaceVariant,
                disabledContentColor = colors.onSurfaceVariant,
            ),
        ) {
            Text("Save profile")
        }
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 44.dp)) {
            Text("Cancel")
        }
    }
}
