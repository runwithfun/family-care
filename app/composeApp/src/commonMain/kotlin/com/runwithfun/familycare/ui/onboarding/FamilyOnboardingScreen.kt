package com.runwithfun.familycare.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.ui.home.ProfileAvatar

private val PinkWash = Color(0xFFFBEEF2)

@Composable
fun FamilyOnboardingScreen(
    illustration: (@Composable () -> Unit)? = null,
    saveFailed: Boolean = false,
    onOpenDemo: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Surface(modifier = Modifier.fillMaxSize(), color = colors.surface, contentColor = colors.onSurface) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 520.dp)
                    .fillMaxWidth().fillMaxHeight().padding(horizontal = 24.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(top = 22.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
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
                if (saveFailed) {
                    Text(
                        "Couldn't open the demo. Try again.",
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = colors.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = onOpenDemo,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).heightIn(min = 54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                    ),
                ) {
                    Text(
                        "Open demo",
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
