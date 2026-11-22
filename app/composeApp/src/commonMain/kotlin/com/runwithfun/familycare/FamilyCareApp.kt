package com.runwithfun.familycare

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.AgeStage
import com.runwithfun.familycare.domain.CareEvent
import com.runwithfun.familycare.domain.CarePlan
import com.runwithfun.familycare.domain.FamilyRole
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.resources.Res
import com.runwithfun.familycare.resources.care_intro
import com.runwithfun.familycare.ui.ActivityPrintScreen
import com.runwithfun.familycare.ui.FamilyCareTheme
import com.runwithfun.familycare.ui.GrowingUpScreen
import com.runwithfun.familycare.ui.assistant.LocalAssistantScreen
import com.runwithfun.familycare.ui.assistant.rememberAssistantActions
import com.runwithfun.familycare.ui.home.HomeScreen
import com.runwithfun.familycare.ui.home.ProfileAvatar
import com.runwithfun.familycare.ui.home.childAge
import com.runwithfun.familycare.ui.home.monthAndDay
import com.runwithfun.familycare.ui.home.statusLabel
import com.runwithfun.familycare.ui.onboarding.FamilyOnboardingScreen
import com.runwithfun.familycare.ui.onboarding.rememberFamilyProfilePhoto
import com.runwithfun.familycare.ui.onboarding.rememberFamilyProfileState
import org.jetbrains.compose.resources.painterResource

private enum class AppTab(val label: String) {
    HOME("Home"),
    RECORDS("Records"),
    GROWING_UP("Growing up"),
    ASSISTANT("Assistant"),
}

private sealed interface HomeDetail {
    data object Profile : HomeDetail
    data object Calendar : HomeDetail
    data class Event(val event: CareEvent, val fromCalendar: Boolean = false) : HomeDetail
}

@Composable
fun FamilyCareApp() {
    val family = rememberFamilyProfileState()
    val profile = family.profile
    FamilyCareTheme {
        if (profile == null) {
            FamilyOnboardingScreen(
                illustration = {
                    Image(
                        painter = painterResource(Res.drawable.care_intro),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(258.dp),
                    )
                },
                saveFailed = family.saveFailed,
                onComplete = { name, photo -> family.save(name, photo) },
            )
        } else {
            FamilyCareNavigation(profile.childName, profile.photoBytes)
        }
    }
}

@Composable
private fun FamilyCareNavigation(childName: String, photoBytes: ByteArray?) {
    val photo = rememberFamilyProfilePhoto(photoBytes)
    val plan = remember(childName) { DemoFamily.createPlan(childName) }
    val assistant = rememberAssistantActions(plan)
    val people = remember(plan) { plan.people.associateBy { it.id } }
    val child = people.getValue(DemoFamily.childId)
    var tab by remember { mutableStateOf(AppTab.HOME) }
    var detail by remember { mutableStateOf<HomeDetail?>(null) }
    var day by remember { mutableStateOf(DemoFamily.today) }
    var growingStage by remember { mutableStateOf(AgeStage.NEWBORN_0_2_MONTHS) }
    var growingActivityOpen by remember { mutableStateOf(false) }
    PlatformBackHandler(enabled = detail != null || growingActivityOpen || tab != AppTab.HOME) {
        val eventPage = detail as? HomeDetail.Event
        when {
            growingActivityOpen -> growingActivityOpen = false
            eventPage?.fromCalendar == true -> detail = HomeDetail.Calendar
            detail != null -> detail = null
            else -> tab = AppTab.HOME
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val page = detail) {
                    HomeDetail.Profile -> Page("Profile", onBack = { detail = null }) {
                        ProfileAvatar(photo = photo)
                        Text(child.displayName, style = MaterialTheme.typography.displaySmall)
                        child.birthDate?.let { childAge(it, DemoFamily.today) }?.let { Text(it) }
                    }
                    HomeDetail.Calendar -> Page("Calendar", onBack = { detail = null }) {
                        Text(day.monthAndDay(), style = MaterialTheme.typography.titleMedium)
                        val events = plan.eventsOn(day)
                        if (events.isEmpty()) Text("No events saved for this day.")
                        for (event in events) {
                            TextButton(onClick = { detail = HomeDetail.Event(event, fromCalendar = true) }) {
                                Text(event.title)
                            }
                        }
                    }
                    is HomeDetail.Event -> EventPage(
                        event = page.event,
                        plan = plan,
                        onBack = { detail = if (page.fromCalendar) HomeDetail.Calendar else null },
                    )
                    null -> when (tab) {
                        AppTab.HOME -> HomeScreen(
                            child = child,
                            childName = child.displayName,
                            photo = photo,
                            today = DemoFamily.today,
                            selectedDay = day,
                            people = people,
                            eventsOn = { plan.eventsOn(it) },
                            onSelectDay = { day = it },
                            onOpenProfile = { detail = HomeDetail.Profile },
                            onOpenCalendar = { detail = HomeDetail.Calendar },
                            onOpenEvent = { detail = HomeDetail.Event(it) },
                        )
                        AppTab.RECORDS -> Page("Records") {
                            for (document in plan.documents) {
                                Text(document.original.fileName, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        AppTab.GROWING_UP -> {
                            if (growingActivityOpen) {
                                ActivityPrintScreen(
                                    stage = growingStage,
                                    childName = child.displayName,
                                    onBack = { growingActivityOpen = false },
                                )
                            } else {
                                GrowingUpScreen(
                                    plan = plan,
                                    child = child,
                                    stage = growingStage,
                                    onStageChange = { growingStage = it },
                                    onOpenActivity = { growingActivityOpen = true },
                                )
                            }
                        }
                        AppTab.ASSISTANT -> LocalAssistantScreen(assistant) {
                            day = assistant.task.date
                            tab = AppTab.HOME
                        }
                    }
                }
            }
            AppNavigation(
                current = tab,
                onSelect = {
                    tab = it
                    detail = null
                    growingActivityOpen = false
                },
            )
        }
    }
}

@Composable
private fun EventPage(event: CareEvent, plan: CarePlan, onBack: () -> Unit) {
    Page(event.title, onBack = onBack) {
        val person = plan.people.first { it.id == event.personId }
        Text(if (person.role == FamilyRole.MOTHER) "For you" else person.displayName)
        Text(listOfNotNull(event.date.monthAndDay(), event.time?.toString()).joinToString(" · "))
        event.statusLabel()?.let { Text(it) }
        if (event is Visit) Text(event.provider)
        val source = plan.documents.firstOrNull { it.id == event.sourceDocumentId }
        if (source != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Source document", style = MaterialTheme.typography.labelLarge)
            Text(source.original.fileName)
        }
    }
}

@Composable
private fun Page(
    title: String,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 44.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Back")
            }
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}

@Composable
private fun AppNavigation(current: AppTab, onSelect: (AppTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(25.dp),
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (tab in AppTab.entries) {
                val selected = tab == current
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(21.dp),
                    color = if (selected) colors.primaryContainer else colors.surface,
                    contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    border = if (selected) BorderStroke(1.dp, colors.outline) else null,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 51.dp)
                            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) })
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    ) {
                        Icon(tab.icon(), contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(tab.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

private fun AppTab.icon(): ImageVector = when (this) {
    AppTab.HOME -> Icons.Filled.Home
    AppTab.RECORDS -> Icons.AutoMirrored.Filled.List
    AppTab.GROWING_UP -> Icons.Filled.FavoriteBorder
    AppTab.ASSISTANT -> Icons.Filled.Email
}
