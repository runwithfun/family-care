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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.AgeStage
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.growing.rememberGrowingNotes
import com.runwithfun.familycare.records.RecordsScreen
import com.runwithfun.familycare.records.rememberRecordsController
import com.runwithfun.familycare.resources.Res
import com.runwithfun.familycare.resources.care_intro
import com.runwithfun.familycare.session.rememberCareSession
import com.runwithfun.familycare.ui.FamilyCareTheme
import com.runwithfun.familycare.ui.ActivityPrintScreen
import com.runwithfun.familycare.ui.CalendarScreen
import com.runwithfun.familycare.ui.GrowingUpScreen
import com.runwithfun.familycare.ui.TaskDetailScreen
import com.runwithfun.familycare.ui.VisitDetailScreen
import com.runwithfun.familycare.ui.assistant.LocalAssistantScreen
import com.runwithfun.familycare.ui.assistant.rememberAssistantActions
import com.runwithfun.familycare.ui.assistant.rememberAssistantConversation
import com.runwithfun.familycare.ui.home.HomeScreen
import com.runwithfun.familycare.ui.home.ProfileAvatar
import com.runwithfun.familycare.ui.home.childAge
import com.runwithfun.familycare.ui.onboarding.FamilyOnboardingScreen
import com.runwithfun.familycare.ui.onboarding.FamilyProfile
import com.runwithfun.familycare.ui.onboarding.FamilyProfileState
import com.runwithfun.familycare.ui.onboarding.rememberFamilyProfilePhoto
import com.runwithfun.familycare.ui.onboarding.rememberFamilyProfileState
import com.runwithfun.familycare.ui.settings.EditProfileScreen
import com.runwithfun.familycare.ui.settings.SettingsScreen
import org.jetbrains.compose.resources.painterResource

private enum class AppTab(val label: String) {
    HOME("Home"),
    RECORDS("Records"),
    GROWING_UP("Growing up"),
    ASSISTANT("Assistant"),
}

private sealed interface HomeDetail {
    data object Profile : HomeDetail
    data object Settings : HomeDetail
    data class EditProfile(val returnTo: HomeDetail) : HomeDetail
    data object Calendar : HomeDetail
    data class VisitEditor(val visitId: String, val fromCalendar: Boolean) : HomeDetail
    data class TaskEditor(val taskId: String?, val date: CareDate, val fromCalendar: Boolean) : HomeDetail
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
                onComplete = { name, birthDate, notes, photo ->
                    family.save(name, birthDate = birthDate, notes = notes, photoBytes = photo)
                },
            )
        } else {
            FamilyCareNavigation(profile, family)
        }
    }
}

@Composable
private fun FamilyCareNavigation(profile: FamilyProfile, family: FamilyProfileState) {
    val photo = rememberFamilyProfilePhoto(profile.photoBytes)
    val plan = remember(profile.childName, profile.birthDate) {
        DemoFamily.createPlan(profile.childName, profile.birthDate)
    }
    val growingNotes = rememberGrowingNotes(plan)
    val session = rememberCareSession(plan)
    val records = rememberRecordsController(plan, saveTask = session::saveTask)
    val assistant = rememberAssistantActions(session)
    val conversation = rememberAssistantConversation(session, records)
    val people = remember(plan) { plan.people.associateBy { it.id } }
    val child = people.getValue(DemoFamily.childId)
    var tabName by rememberSaveable { mutableStateOf(AppTab.HOME.name) }
    val tab = AppTab.valueOf(tabName)
    var recordId by rememberSaveable { mutableStateOf<String?>(null) }
    var activityStageName by rememberSaveable { mutableStateOf<String?>(null) }
    val growingState = rememberSaveableStateHolder()
    var detail by remember { mutableStateOf<HomeDetail?>(null) }
    var day by remember { mutableStateOf(DemoFamily.today) }
    var focusedVisitId by remember { mutableStateOf<String?>(null) }
    PlatformBackHandler(enabled = detail != null || tab != AppTab.HOME) {
        when (val page = detail) {
            is HomeDetail.VisitEditor -> {
                focusedVisitId = page.visitId
                detail = if (page.fromCalendar) HomeDetail.Calendar else null
            }
            is HomeDetail.TaskEditor -> detail = if (page.fromCalendar) HomeDetail.Calendar else null
            is HomeDetail.EditProfile -> detail = page.returnTo
            HomeDetail.Settings -> detail = HomeDetail.Profile
            HomeDetail.Calendar, HomeDetail.Profile -> detail = null
            null -> when {
                activityStageName != null -> activityStageName = null
                tab == AppTab.RECORDS && recordId != null -> recordId = null
                else -> tabName = AppTab.HOME.name
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val page = detail) {
                    HomeDetail.Profile -> Page("Profile", onBack = { detail = null }) {
                        val colors = MaterialTheme.colorScheme
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.surfaceVariant,
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ProfileAvatar(photo = photo, modifier = Modifier.size(112.dp))
                                Text(
                                    child.displayName,
                                    style = MaterialTheme.typography.headlineLarge,
                                    textAlign = TextAlign.Center,
                                )
                                val age = child.birthDate?.let { childAge(it, DemoFamily.today) }
                                if (age != null) Text(age, color = colors.onSurfaceVariant)
                                profile.birthDate?.let {
                                    Text("Born $it", color = colors.onSurfaceVariant)
                                }
                                if (profile.notes.isNotBlank()) {
                                    Text(
                                        profile.notes,
                                        color = colors.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = { detail = HomeDetail.EditProfile(HomeDetail.Profile) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryContainer,
                                contentColor = colors.onPrimaryContainer,
                            ),
                        ) {
                            Text("Edit profile")
                        }
                        TextButton(
                            onClick = { detail = HomeDetail.Settings },
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) {
                            Text("Settings")
                        }
                    }
                    HomeDetail.Settings -> Page("Settings", onBack = { detail = HomeDetail.Profile }) {
                        SettingsScreen(
                            profile = profile,
                            onEditProfile = { detail = HomeDetail.EditProfile(HomeDetail.Settings) },
                            onBack = { detail = HomeDetail.Profile },
                        )
                    }
                    is HomeDetail.EditProfile -> Page("Edit profile", onBack = { detail = page.returnTo }) {
                        EditProfileScreen(
                            profile = profile,
                            saveFailed = family.saveFailed,
                            onSave = { name, birth, notes, photoBytes ->
                                family.save(
                                    childName = name,
                                    birthDate = birth,
                                    notes = notes,
                                    photoBytes = photoBytes,
                                )
                            },
                            onBack = { detail = page.returnTo },
                        )
                    }
                    HomeDetail.Calendar -> CalendarScreen(
                        session = session,
                        selected = day,
                        onSelectDate = {
                            day = it
                            focusedVisitId = null
                        },
                        onOpenVisit = {
                            focusedVisitId = it
                            detail = HomeDetail.VisitEditor(it, fromCalendar = true)
                        },
                        onOpenTask = { detail = HomeDetail.TaskEditor(it, day, fromCalendar = true) },
                        onAddPlanItem = { detail = HomeDetail.TaskEditor(null, it, fromCalendar = true) },
                        onBackHome = { detail = null },
                        focusedVisitId = focusedVisitId,
                    )
                    is HomeDetail.VisitEditor -> VisitDetailScreen(
                        session = session,
                        visitId = page.visitId,
                        onBack = {
                            focusedVisitId = page.visitId
                            detail = if (page.fromCalendar) HomeDetail.Calendar else null
                        },
                        onSaved = {
                            day = it
                            focusedVisitId = page.visitId
                        },
                    )
                    is HomeDetail.TaskEditor -> TaskDetailScreen(
                        session = session,
                        taskId = page.taskId,
                        draftDate = page.date,
                        onBack = { detail = if (page.fromCalendar) HomeDetail.Calendar else null },
                        onSaved = { taskId, date ->
                            day = date
                            detail = HomeDetail.TaskEditor(taskId, date, page.fromCalendar)
                        },
                    )
                    null -> when (tab) {
                        AppTab.HOME -> HomeScreen(
                            child = child,
                            childName = child.displayName,
                            photo = photo,
                            today = DemoFamily.today,
                            selectedDay = day,
                            people = people,
                            eventsOn = { session.eventsOn(it) },
                            onSelectDay = { day = it },
                            onOpenProfile = { detail = HomeDetail.Profile },
                            onOpenCalendar = { detail = HomeDetail.Calendar },
                            onOpenEvent = { event ->
                                detail = when (event) {
                                    is Visit -> {
                                        focusedVisitId = event.id
                                        HomeDetail.VisitEditor(event.id, fromCalendar = false)
                                    }
                                    is Task -> HomeDetail.TaskEditor(event.id, event.date, fromCalendar = false)
                                }
                            },
                        )
                        AppTab.RECORDS -> RecordsScreen(
                            records = records,
                            people = people,
                            selectedDocumentId = recordId,
                            onSelectDocument = { recordId = it },
                            onOpenTask = { detail = HomeDetail.TaskEditor(it.id, it.date, fromCalendar = false) },
                        )
                        AppTab.GROWING_UP -> {
                            val printableStage = activityStageName?.let(AgeStage::valueOf)
                            if (printableStage == null) {
                                growingState.SaveableStateProvider("growing-up") {
                                    GrowingUpScreen(
                                        plan = plan,
                                        child = child,
                                        notes = growingNotes,
                                        onOpenActivity = { activityStageName = it.name },
                                        onSaved = session::notifyChanged,
                                    )
                                }
                            } else {
                                ActivityPrintScreen(printableStage, child.displayName) { activityStageName = null }
                            }
                        }
                        AppTab.ASSISTANT -> LocalAssistantScreen(
                            actions = assistant,
                            conversation = conversation,
                            onSaved = {
                                session.notifyChanged()
                                day = assistant.task.date
                                tabName = AppTab.HOME.name
                            },
                            onOpenCalendar = {
                                day = assistant.visitDate ?: DemoFamily.today
                                tabName = AppTab.HOME.name
                                detail = HomeDetail.Calendar
                            },
                            onOpenTask = { task ->
                                detail = HomeDetail.TaskEditor(task.id, task.date, fromCalendar = false)
                            },
                        )
                    }
                }
            }
            AppNavigation(
                current = tab,
                onSelect = {
                    if (tab != it) activityStageName = null
                    tabName = it.name
                    detail = null
                },
            )
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
        Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
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
