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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.FamilyRole

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
    val plan = remember { DemoFamily.createPlan() }
    val people = plan.people.associateBy { it.id }
    val child = people.getValue(DemoFamily.childId)

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
                Text(child.displayName, style = MaterialTheme.typography.displaySmall)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        "Demo profile",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                HorizontalDivider()
                Text("Today", style = MaterialTheme.typography.headlineSmall)
                for (event in plan.eventsOn(DemoFamily.today)) {
                    val person = people.getValue(event.personId)
                    val owner = if (person.role == FamilyRole.MOTHER) "For you" else person.displayName
                    val details = listOfNotNull(owner, event.time?.toString()).joinToString(" · ")
                    Column {
                        Text(event.title, style = MaterialTheme.typography.titleMedium)
                        Text(details, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
