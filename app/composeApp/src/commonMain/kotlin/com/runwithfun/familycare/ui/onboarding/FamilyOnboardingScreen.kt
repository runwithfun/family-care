package com.runwithfun.familycare.ui.onboarding

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.ui.home.ProfileAvatar

private val PinkWash = Color(0xFFFBEEF2)

@Composable
fun FamilyOnboardingScreen(
    illustration: (@Composable () -> Unit)? = null,
    saveFailed: Boolean = false,
    onComplete: (String, ByteArray?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("Maya") }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var photoFailed by remember { mutableStateOf(false) }
    var photoPending by remember { mutableStateOf(false) }
    val scroll = remember(showProfile) { ScrollState(0) }

    val choosePhoto = rememberOnboardingPhotoPicker { result ->
        photoPending = result == OnboardingPhotoResult.Loading
        when (result) {
            OnboardingPhotoResult.Loading -> Unit
            is OnboardingPhotoResult.Selected -> {
                val image = decodeOnboardingPhoto(result.bytes)
                if (image != null) {
                    photo = result.bytes
                    preview = image
                }
                photoFailed = image == null
            }
            OnboardingPhotoResult.Failed -> photoFailed = true
            OnboardingPhotoResult.Cancelled -> Unit
        }
    }
    val focus = LocalFocusManager.current
    val complete: () -> Unit = {
        val childName = name.trim()
        if (childName.isNotEmpty() && !photoPending) {
            focus.clearFocus()
            onComplete(childName, photo)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.surface, contentColor = colors.onSurface) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 520.dp)
                    .fillMaxWidth().fillMaxHeight().padding(horizontal = 24.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .verticalScroll(scroll).padding(top = 22.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (showProfile) {
                        TextButton(
                            onClick = {
                                focus.clearFocus()
                                showProfile = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurface),
                        ) {
                            Text("Back")
                        }
                        Text(
                            "Create a child profile",
                            color = colors.onSurface,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProfileAvatar(preview, modifier = Modifier.size(78.dp))
                            if (choosePhoto != null) {
                                Column {
                                    TextButton(
                                        onClick = {
                                            photoFailed = false
                                            photoPending = true
                                            choosePhoto()
                                        },
                                        enabled = !photoPending,
                                        colors = ButtonDefaults.textButtonColors(contentColor = colors.secondary),
                                        modifier = Modifier.heightIn(min = 44.dp),
                                    ) {
                                        Text(if (photo == null) "Add photo" else "Change photo")
                                    }
                                    if (photo != null) {
                                        TextButton(
                                            onClick = {
                                                photo = null
                                                preview = null
                                                photoFailed = false
                                            },
                                            enabled = !photoPending,
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
                        if (photoPending) {
                            Text(
                                "Preparing photo…",
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (photoFailed) {
                            Text(
                                "Couldn't open that photo. Choose another or continue.",
                                color = colors.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Child's name") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { complete() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.onSurface,
                                unfocusedTextColor = colors.onSurface,
                                focusedBorderColor = colors.secondary,
                                focusedLabelColor = colors.secondary,
                                cursorColor = colors.secondary,
                            ),
                        )
                        if (saveFailed) {
                            Text(
                                "Couldn't save the profile. Try again.",
                                color = colors.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        Text(
                            "Family Care",
                            color = colors.secondary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1.2f)
                                .clip(RoundedCornerShape(28.dp)).background(PinkWash),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (illustration != null) {
                                illustration()
                            } else {
                                ProfileAvatar(null, modifier = Modifier.size(116.dp))
                            }
                        }
                        Text(
                            "Care for both of you",
                            color = colors.onSurface,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Keep appointments, records, and follow-ups in one family plan.",
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                Button(
                    onClick = if (showProfile) complete else ({ showProfile = true }),
                    enabled = !showProfile || (name.isNotBlank() && !photoPending),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).heightIn(min = 54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                        disabledContainerColor = colors.surfaceVariant,
                        disabledContentColor = colors.onSurfaceVariant,
                    ),
                ) {
                    Text(
                        if (showProfile) "Open day plan" else "Continue",
                        modifier = Modifier.padding(vertical = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
fun rememberFamilyProfilePhoto(bytes: ByteArray?): ImageBitmap? =
    remember(bytes) { bytes?.let(::decodeOnboardingPhoto) }
