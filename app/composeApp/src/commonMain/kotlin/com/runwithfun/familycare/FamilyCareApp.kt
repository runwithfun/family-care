package com.runwithfun.familycare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.AgeStage
import com.runwithfun.familycare.domain.FamilyRole
import com.runwithfun.familycare.ui.ActivityPrintScreen
import com.runwithfun.familycare.ui.GrowingUpScreen

private val DemoColors = lightColorScheme(
    primary = Color(0xFF9C566C),
    primaryContainer = Color(0xFFF3D6DE),
    onPrimaryContainer = Color(0xFF161616),
    background = Color.White,
    surface = Color.White,
    onSurface = Color(0xFF161616),
)

private sealed interface AppScreen {
    data object Home : AppScreen
    data object GrowingUp : AppScreen
    data class Activity(val stage: AgeStage) : AppScreen
}

@Composable
fun FamilyCareApp() {
    val plan = remember { DemoFamily.createPlan() }
    val people = plan.people.associateBy { it.id }
    val child = people.getValue(DemoFamily.childId)
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }

    MaterialTheme(colorScheme = DemoColors) {
        Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            when (val current = screen) {
                AppScreen.Home -> {
                    Column(
                        modifier = Modifier
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
                        Button(onClick = { screen = AppScreen.GrowingUp }) {
                            Text("Growing up")
                        }
                        HorizontalDivider()
                        Text("Today", style = MaterialTheme.typography.headlineSmall)
                        for (event in plan.eventsOn(DemoFamily.today)) {
                            val person = people.getValue(event.personId)
                            val owner =
                                if (person.role == FamilyRole.MOTHER) "For you" else person.displayName
                            val details =
                                listOfNotNull(owner, event.time?.toString()).joinToString(" · ")
                            Column {
                                Text(event.title, style = MaterialTheme.typography.titleMedium)
                                Text(details, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                AppScreen.GrowingUp -> GrowingUpScreen(
                    plan = plan,
                    onOpenActivity = { stage -> screen = AppScreen.Activity(stage) },
                    onBack = { screen = AppScreen.Home },
                )

                is AppScreen.Activity -> ActivityPrintScreen(
                    stage = current.stage,
                    childName = child.displayName,
                    onBack = { screen = AppScreen.GrowingUp },
                )
            }
        }
    }
}
