package com.runwithfun.familycare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DemoColors = lightColorScheme(
    primary = Color(0xFF9C566C),
    primaryContainer = Color(0xFFF3D6DE),
    onPrimaryContainer = Color(0xFF161616),
    background = Color.White,
    surface = Color.White,
    onSurface = Color(0xFF161616),
)

@Composable
fun FamilyCareApp() {
    MaterialTheme(colorScheme = DemoColors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Family Care", style = MaterialTheme.typography.titleMedium)
                Text("Maya", style = MaterialTheme.typography.displaySmall)
                Text("Demo profile", style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                Text("Today", style = MaterialTheme.typography.headlineSmall)
                Text("Well-baby visit", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
