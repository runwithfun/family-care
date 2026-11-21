package com.runwithfun.familycare.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

private val Paper = Color(0xFFFFFDFC)
private val Pink = Color(0xFFF3D6DE)
private val PinkWash = Color(0xFFFBEEF2)
private val Graphite = Color(0xFF242020)
private val SecondaryInk = Color(0xFF675C60)
private val Rose = Color(0xFF8A455C)

@Composable
fun FamilyOnboardingScreen(
    illustration: (@Composable () -> Unit)? = null,
    onComplete: (String, ByteArray?) -> Unit,
) {
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("Maya") }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var photoFailed by remember { mutableStateOf(false) }
    val scroll = remember(showProfile) { ScrollState(0) }

    val choosePhoto = rememberOnboardingPhotoPicker { result ->
        if (result is OnboardingPhotoResult.Selected) {
            val image = decodeOnboardingPhoto(result.bytes)
            if (image != null) {
                photo = result.bytes
                preview = image
            }
            photoFailed = image == null
        } else {
            photoFailed = true
        }
    }
    val focus = LocalFocusManager.current
    val complete: () -> Unit = {
        val childName = name.trim()
        if (childName.isNotEmpty()) {
            focus.clearFocus()
            onComplete(childName, photo)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Paper, contentColor = Graphite) {
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
                            colors = ButtonDefaults.textButtonColors(contentColor = Graphite),
                        ) {
                            Text("Back")
                        }
                        Text(
                            "Create a child profile",
                            color = Graphite,
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
                                        onClick = choosePhoto,
                                        colors = ButtonDefaults.textButtonColors(contentColor = Rose),
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
                                            colors = ButtonDefaults.textButtonColors(contentColor = SecondaryInk),
                                            modifier = Modifier.heightIn(min = 44.dp),
                                        ) {
                                            Text("Remove photo")
                                        }
                                    } else {
                                        Text(
                                            "Optional",
                                            modifier = Modifier.padding(start = 12.dp),
                                            color = SecondaryInk,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                        if (photoFailed) {
                            Text(
                                "Couldn't open that photo. Choose another or continue.",
                                color = Rose,
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
                                focusedTextColor = Graphite,
                                unfocusedTextColor = Graphite,
                                focusedBorderColor = Rose,
                                focusedLabelColor = Rose,
                                cursorColor = Rose,
                            ),
                        )
                    } else {
                        Text(
                            "Family Care",
                            color = Rose,
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
                            color = Graphite,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Keep appointments, records, and follow-ups in one family plan.",
                            color = SecondaryInk,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                Button(
                    onClick = if (showProfile) complete else ({ showProfile = true }),
                    enabled = !showProfile || name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).heightIn(min = 54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Pink,
                        contentColor = Graphite,
                        disabledContainerColor = Color(0xFFF3E8EB),
                        disabledContentColor = SecondaryInk,
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

@Composable
private fun ProfileAvatar(image: ImageBitmap?, modifier: Modifier) {
    Box(modifier = modifier.clip(CircleShape).background(Pink)) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Selected child photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(Rose, radius = size.minDimension * .15f, center = Offset(center.x, size.height * .36f))
                drawRoundRect(
                    color = Rose,
                    topLeft = Offset(size.width * .23f, size.height * .56f),
                    size = Size(size.width * .54f, size.height * .28f),
                    cornerRadius = CornerRadius(size.minDimension * .15f),
                )
            }
        }
    }
}
