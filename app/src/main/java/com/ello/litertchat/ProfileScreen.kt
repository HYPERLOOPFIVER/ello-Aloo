package com.ello.litertchat

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ProfileInk = ElloPalette.Text
private val ProfileMuted = ElloPalette.Muted
private val ProfileBlue = ElloPalette.Accent
private val ProfileRose = ElloPalette.Accent
private val ProfileTeal = ElloPalette.Accent
private val ProfileBorder = ElloPalette.Border
private val ProfileCanvas = ElloPalette.Canvas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    gmailConnected: Boolean,
    calendarConnected: Boolean,
    tasksConnected: Boolean,
    driveConnected: Boolean,
    sheetsConnected: Boolean,
    activeGoal: String?,
    activeGoalStatus: String?,
    onProfileNameChanged: (String) -> Unit,
    onManageServices: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("ello_user_profile", Context.MODE_PRIVATE) }
    val textPrefs = remember(context) { context.getSharedPreferences("ello_profile_context", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val detailKeys = listOf("displayName", "phone", "homeAddress", "workAddress")
    val detailLabels = listOf("Name", "Phone number", "Home address", "Work address")
    var details by remember {
        mutableStateOf(detailKeys.associateWith { key -> prefs.getString(key, "").orEmpty() })
    }
    val contextKeys = listOf("preferences", "agentRules", "pastMistakes", "goals", "proactiveContext")
    val contextTitles = listOf("Your preferences", "Personal notes", "Important details", "Goals", "Routines")
    val contextSubtitles = listOf(
        "Preferences you choose to save", "Notes you choose to keep", "Details you want close at hand", "Outcomes you have shared", "Routines you want to remember",
    )
    var contextValues by remember {
        mutableStateOf(contextKeys.associateWith { key -> textPrefs.getString(key, "").orEmpty() })
    }
    var people by remember { mutableStateOf(ProfilePeopleStore.load(context)) }
    var peopleLoading by remember { mutableStateOf(false) }
    var peopleError by remember { mutableStateOf("") }
    var peopleRefreshKey by rememberSaveable { mutableStateOf(0) }
    var sheetKind by rememberSaveable { mutableStateOf("") }
    var editingKey by rememberSaveable { mutableStateOf("") }
    var sheetTitle by rememberSaveable { mutableStateOf("") }
    var draft by rememberSaveable { mutableStateOf("") }
    var selectedPerson by remember { mutableStateOf<SavedPerson?>(null) }
    var vaultEntries by remember { mutableStateOf(PersonalFormVault.entries(context)) }
    var vaultEditingId by rememberSaveable { mutableStateOf("") }
    var vaultLabel by rememberSaveable { mutableStateOf("") }
    var vaultError by rememberSaveable { mutableStateOf("") }
    var paymentCards by remember { mutableStateOf(PaymentCardVault.entries(context)) }
    var paymentCardId by remember { mutableStateOf("") }
    var paymentCardLabel by remember { mutableStateOf("") }
    var paymentCardholder by remember { mutableStateOf("") }
    var paymentCardNumber by remember { mutableStateOf("") }
    var paymentExpiry by remember { mutableStateOf("") }
    var paymentCardError by remember { mutableStateOf("") }
    var personName by rememberSaveable { mutableStateOf("") }
    var personEmail by rememberSaveable { mutableStateOf("") }
    var personPhone by rememberSaveable { mutableStateOf("") }
    var personRelationship by rememberSaveable { mutableStateOf("") }
    var personNote by rememberSaveable { mutableStateOf("") }
    var profileSyncMessage by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        runCatching { FirebaseOnboarding.loadSavedProfile(context) }
            .onSuccess { cloudProfile ->
                if (cloudProfile != null) {
                    val merged = details.toMutableMap()
                    cloudProfile.displayName.takeIf(String::isNotBlank)?.let { merged["displayName"] = it }
                    cloudProfile.phone.takeIf(String::isNotBlank)?.let { merged["phone"] = it }
                    details = merged
                    if (cloudProfile.displayName.isNotBlank()) onProfileNameChanged(cloudProfile.displayName)
                }
            }
    }

    LaunchedEffect(gmailConnected, peopleRefreshKey) {
        people = ProfilePeopleStore.load(context)
        if (!gmailConnected) {
            peopleLoading = false
            peopleError = ""
        } else {
            peopleLoading = true
            peopleError = ""
            runCatching { withContext(Dispatchers.IO) { GmailPeopleRepository.fetchRecent(context, forceRefresh = peopleRefreshKey > 0) } }
                .onSuccess { people = ProfilePeopleStore.mergeGmail(context, it) }
                .onFailure { peopleError = it.localizedMessage ?: "Could not read people from Gmail." }
            peopleLoading = false
        }
    }

    fun editDetail(key: String, title: String) {
        editingKey = key
        sheetTitle = title
        draft = details[key].orEmpty()
        sheetKind = "detail"
    }

    fun editPerson(person: SavedPerson? = null) {
        selectedPerson = person
        personName = person?.name.orEmpty()
        personEmail = person?.email.orEmpty()
        personPhone = person?.phone.orEmpty()
        personRelationship = person?.relationship.orEmpty()
        personNote = person?.note.orEmpty()
        sheetKind = "person-edit"
    }

    fun editVault(entry: PersonalFormVault.Entry? = null) {
        vaultEditingId = entry?.id.orEmpty()
        vaultLabel = entry?.label.orEmpty()
        draft = entry?.let { runCatching { PersonalFormVault.resolve(context, it.label)?.second }.getOrDefault("") }.orEmpty()
        vaultError = ""
        sheetKind = "vault-edit"
    }

    fun editPaymentCard(entry: PaymentCardVault.Entry? = null) {
        paymentCardId = entry?.id.orEmpty()
        val saved = entry?.let { PaymentCardVault.details(context, it.id) }
        paymentCardLabel = saved?.label.orEmpty()
        paymentCardholder = saved?.cardholder.orEmpty()
        paymentCardNumber = saved?.number.orEmpty()
        paymentExpiry = if (saved == null) "" else "${saved.expiryMonth}/${saved.expiryYear.takeLast(2)}"
        paymentCardError = ""
        sheetKind = "payment-card"
    }

    fun clearPaymentDraft() {
        paymentCardId = ""
        paymentCardLabel = ""
        paymentCardholder = ""
        paymentCardNumber = ""
        paymentExpiry = ""
        paymentCardError = ""
    }

    Column(
        Modifier.fillMaxSize().background(ProfileCanvas).windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text("Profile", color = ProfileInk, fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
        Text("A little more you, in Ello’s corner.", color = ProfileMuted, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp, bottom = 19.dp))

        ProfileHeroCard(
            name = details["displayName"].orEmpty(),
            email = prefs.getString("email", "").orEmpty(),
            onClick = { editDetail("displayName", "Name") },
        )

        Spacer(Modifier.height(22.dp))
        ProfileSectionHeading("YOUR DETAILS", "Personal details you choose to share")
        ProfileCard {
            detailKeys.forEachIndexed { index, key ->
                val title = detailLabels[index]
                ProfileDetailRow(title, details[key].orEmpty(), onClick = { editDetail(key, title) })
                if (index != detailKeys.lastIndex) HorizontalDivider(color = ProfileBorder, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
        ProfileSectionHeading("PERSONAL VAULT", "Extra booking details encrypted on this phone. They are not synced or sent to Gemini.")
        ProfileCard {
            vaultEntries.forEachIndexed { index, entry ->
                ProfileDetailRow(entry.label, "Saved on this phone", onClick = { editVault(entry) })
                if (index != vaultEntries.lastIndex) HorizontalDivider(color = ProfileBorder, modifier = Modifier.padding(horizontal = 16.dp))
            }
            HorizontalDivider(color = ProfileBorder)
            TextButton(onClick = { editVault() }, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) {
                Text("+ Add form detail", color = ProfileBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(24.dp))
        ProfileSectionHeading("PAYMENT METHODS", "Encrypted on this phone. CVV/security codes are never saved or sent to Gemini.")
        ProfileCard {
            paymentCards.forEachIndexed { index, entry ->
                ProfileDetailRow(entry.label, "•••• ${entry.lastFour}", onClick = { editPaymentCard(entry) })
                if (index != paymentCards.lastIndex) HorizontalDivider(color = ProfileBorder, modifier = Modifier.padding(horizontal = 16.dp))
            }
            HorizontalDivider(color = ProfileBorder)
            TextButton(onClick = { editPaymentCard() }, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) {
                Text("+ Add payment method", color = ProfileBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(25.dp))
        ProfileSectionHeading("RELATIONSHIP MANAGER", "People and connections you choose to save")
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { editPerson() },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProfileBlue),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            ) { Text("+ Add a person", color = ElloPalette.AccentText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
            TextButton(
                onClick = { peopleRefreshKey++ },
                enabled = gmailConnected && !peopleLoading,
                modifier = Modifier.height(44.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            ) {
                Icon(Icons.Default.Refresh, null, tint = if (gmailConnected) ProfileBlue else ProfileMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (peopleLoading) "Finding…" else "Refresh Gmail", color = if (gmailConnected) ProfileBlue else ProfileMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        ProfileCard {
            if (people.isNotEmpty()) {
                people.forEachIndexed { index, person ->
                    ProfilePersonRow(person, onClick = { selectedPerson = person; sheetKind = "person-detail" })
                    if (index != people.lastIndex) HorizontalDivider(color = ProfileBorder, modifier = Modifier.padding(horizontal = 16.dp))
                }
            } else if (peopleLoading) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = ProfileBlue, strokeWidth = 2.dp)
                    Text("Finding people in Gmail…", color = ProfileMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 11.dp))
                }
            } else if (peopleError.isNotBlank()) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Couldn’t load Gmail people", color = ProfileInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(peopleError, color = ProfileMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    if (gmailConnected) TextButton(onClick = { peopleRefreshKey++ }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("Try again", color = ProfileBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    } else TextButton(onClick = onManageServices, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("Connect Gmail", color = ProfileBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (gmailConnected) "Your relationship manager is ready" else "No relationships yet", color = ProfileInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (gmailConnected) "People Ello learns about in chats and Gmail will appear here, even before you know every detail." else "People Ello learns about in chat will appear here, even if you only know their name or role.", color = ProfileMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    if (!gmailConnected) TextButton(onClick = onManageServices, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("Connect Gmail", color = ProfileBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (people.isNotEmpty() && peopleError.isNotBlank()) {
                HorizontalDivider(color = ProfileBorder)
                Text("Gmail refresh failed: $peopleError", color = ProfileMuted, fontSize = 11.sp, modifier = Modifier.padding(14.dp))
            }
        }

        Spacer(Modifier.height(25.dp))
        ProfileSectionHeading("CONNECTED SERVICES", "Choose what Ello can access when you ask")
        ProfileCard {
            val services = listOf(
                "Gmail" to gmailConnected, "Google Calendar" to calendarConnected, "Google Tasks" to tasksConnected,
                "Google Drive" to driveConnected, "Google Sheets" to sheetsConnected,
            )
            val connectedCount = services.count { it.second }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = ElloPalette.AccentSurface, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(42.dp)) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Tune, null, tint = ProfileBlue, modifier = Modifier.size(20.dp))
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(if (connectedCount == 0) "No services connected" else "$connectedCount services connected", color = ProfileInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(services.filter { it.second }.joinToString { it.first }.ifBlank { "Gmail, Calendar, Tasks, Drive and Sheets" }, color = ProfileMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
            }
            HorizontalDivider(color = ProfileBorder)
            TextButton(onClick = onManageServices, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) {
                Text("Manage services", color = ProfileBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Default.ArrowForward, null, tint = ProfileBlue, modifier = Modifier.size(15.dp))
            }
        }

        Spacer(Modifier.height(25.dp))
        ProfileSectionHeading("YOUR NOTES", "Details you choose to keep on this phone")
        contextKeys.forEachIndexed { index, key ->
            val value = contextValues[key].orEmpty()
            val entries = value.lines().map(String::trim).filter(String::isNotEmpty)
            ProfileMemoryCard(
                title = contextTitles[index],
                subtitle = contextSubtitles[index],
                preview = entries.take(3).joinToString(" · ").ifBlank { "Add details whenever you like." },
                count = if (key == "goals") entries.size else null,
            )
            Spacer(Modifier.height(10.dp))
        }

        activeGoal?.takeIf { it.isNotBlank() }?.let { goal ->
            Spacer(Modifier.height(8.dp))
            ProfileSectionHeading("CURRENT AGENT TASK", "Live progress from Ello")
            ProfileCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(goal, color = ProfileInk, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = if (activeGoalStatus == "completed") ElloPalette.Accent else ProfileBlue, modifier = Modifier.size(16.dp))
                        Text((activeGoalStatus ?: "in progress").replace('_', ' ').replaceFirstChar { it.uppercase() }, color = ProfileMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 7.dp))
                    }
                }
            }
        }

        Text(profileSyncMessage.ifBlank { "Saved on this phone" }, color = ProfileMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 18.dp, bottom = 20.dp))
    }

    if (sheetKind.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { sheetKind = ""; selectedPerson = null; clearPaymentDraft() },
            sheetState = sheetState,
            containerColor = ElloPalette.Surface,
            contentColor = ProfileInk,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = ProfileMuted.copy(alpha = 0.55f)) },
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                when (sheetKind) {
                    "person-detail" -> {
                        val person = selectedPerson
                        Text(person?.name.orEmpty(), color = ProfileInk, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                        Text(person?.sources.orEmpty().mapNotNull { source -> when (source) { "gmail" -> "Gmail"; "conversation" -> "Learned in chat"; "user" -> "Added by you"; else -> null } }.distinct().joinToString(" · ").ifBlank { "Relationship Manager" }, color = ProfileTeal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        person?.let {
                            if (it.sourceContext.isNotBlank()) ProfileReadOnlyRow("How Ello knows them", it.sourceContext)
                            if (it.relationship.isNotBlank()) ProfileReadOnlyRow("Relationship or role", it.relationship)
                            if (it.email.isNotBlank()) ProfileReadOnlyRow("Email", it.email)
                            if (it.phone.isNotBlank()) ProfileReadOnlyRow("Phone", it.phone)
                            if (it.note.isNotBlank()) ProfileReadOnlyRow("Note", it.note)
                            if ("gmail" in it.sources && it.messageCount > 0) ProfileReadOnlyRow("Recent messages", "Found in ${it.messageCount} recent emails scanned")
                            if ("gmail" in it.sources && it.lastSeen.isNotBlank()) ProfileReadOnlyRow("Most recent message", it.lastSeen)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { people = ProfilePeopleStore.delete(context, it.id); sheetKind = ""; selectedPerson = null }) {
                                    Text("Remove", color = Color(0xFFB54747), fontWeight = FontWeight.SemiBold)
                                }
                                TextButton(onClick = { editPerson(it) }) { Text("Edit", color = ProfileBlue, fontWeight = FontWeight.SemiBold) }
                                if (it.email.isNotBlank()) TextButton(
                                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(it.email)}"))) } },
                                ) { Text("Email", color = ProfileBlue, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                    "person-edit" -> {
                        Text(if (selectedPerson == null) "Add a relationship" else "Edit relationship", color = ProfileInk, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                        Text("Ello can add details later as it learns more in chat or Gmail. You can still correct this entry here.", color = ProfileMuted, fontSize = 13.sp, lineHeight = 19.sp)
                        ProfileInputField("Name", personName, { personName = it })
                        ProfileInputField("Email", personEmail, { personEmail = it })
                        ProfileInputField("Phone", personPhone, { personPhone = it })
                        ProfileInputField("Role or relationship", personRelationship, { personRelationship = it })
                        ProfileInputField("Context or notes", personNote, { personNote = it }, multiline = true)
                        ProfileSaveButton(label = "Save relationship", enabled = personName.isNotBlank()) {
                            if (personName.isNotBlank()) {
                                people = ProfilePeopleStore.upsertUserPerson(context, personName, personEmail, personPhone, personRelationship, personNote, selectedPerson?.id)
                                sheetKind = ""
                            }
                        }
                    }
                    "detail" -> {
                        Text(sheetTitle, color = ProfileInk, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                        Text("Only add details you want to keep on this phone.", color = ProfileMuted, fontSize = 13.sp, lineHeight = 19.sp)
                        ProfileInputField(sheetTitle, draft, { draft = it }, multiline = editingKey in setOf("homeAddress", "workAddress"))
                        ProfileSaveButton(label = "Save") {
                            if (sheetKind == "detail") {
                                val key = editingKey
                                val value = draft.trim()
                                details = details + (key to value)
                                prefs.edit().putString(key, value).apply()
                                if (key == "displayName") onProfileNameChanged(value)
                                if (key == "displayName" || key == "phone") {
                                    scope.launch {
                                        runCatching { FirebaseOnboarding.saveProfileDetail(context, key, value) }
                                            .onSuccess { profileSyncMessage = "Synced to your profile" }
                                            .onFailure { profileSyncMessage = "Saved on this phone; cloud sync is unavailable." }
                                    }
                                }
                            }
                            sheetKind = ""
                        }
                    }
                    "vault-edit" -> {
                        Text(if (vaultEditingId.isBlank()) "Add a form detail" else "Edit form detail", color = ProfileInk, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                        Text("This value is encrypted on this phone. Ello can insert it into a matching website field when you ask.", color = ProfileMuted, fontSize = 13.sp, lineHeight = 19.sp)
                        ProfileInputField("Label shown on the form", vaultLabel, { vaultLabel = it })
                        ProfileInputField("Saved value", draft, { draft = it }, multiline = true)
                        if (vaultError.isNotBlank()) Text(vaultError, color = Color(0xFFB54747), fontSize = 12.sp, lineHeight = 17.sp)
                        ProfileSaveButton(label = "Save encrypted detail", enabled = vaultLabel.isNotBlank() && draft.isNotBlank()) {
                            runCatching { PersonalFormVault.save(context, vaultEditingId.ifBlank { null }, vaultLabel, draft) }
                                .onSuccess {
                                    vaultEntries = PersonalFormVault.entries(context)
                                    sheetKind = ""
                                }
                                .onFailure { vaultError = it.localizedMessage ?: "Could not save this detail." }
                        }
                        if (vaultEditingId.isNotBlank()) {
                            TextButton(onClick = {
                                PersonalFormVault.remove(context, vaultEditingId)
                                vaultEntries = PersonalFormVault.entries(context)
                                sheetKind = ""
                            }, modifier = Modifier.align(Alignment.End)) {
                                Text("Remove saved detail", color = Color(0xFFB54747), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    "payment-card" -> {
                        Text(if (paymentCardId.isBlank()) "Add a payment method" else "Edit payment method", color = ProfileInk, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                        Text("Card number, name, and expiry are encrypted in Android Keystore-backed storage on this phone. CVV/security code is never saved. Ello inserts card fields locally only when you ask and still pauses before the final payment.", color = ProfileMuted, fontSize = 13.sp, lineHeight = 19.sp)
                        ProfileInputField("Card label", paymentCardLabel, { paymentCardLabel = it })
                        ProfileInputField("Name on card", paymentCardholder, { paymentCardholder = it })
                        ProfileInputField("Card number", paymentCardNumber, { paymentCardNumber = it.filter { char -> char.isDigit() || char == ' ' || char == '-' }.take(23) }, secret = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                        ProfileInputField("Expiry (MM/YY)", paymentExpiry, { paymentExpiry = it.take(7) })
                        if (paymentCardError.isNotBlank()) Text(paymentCardError, color = Color(0xFFB54747), fontSize = 12.sp, lineHeight = 17.sp)
                        ProfileSaveButton(label = "Save encrypted payment method", enabled = paymentCardLabel.isNotBlank() && paymentCardholder.isNotBlank() && paymentCardNumber.isNotBlank() && paymentExpiry.isNotBlank()) {
                            runCatching { PaymentCardVault.save(context, paymentCardId.ifBlank { null }, paymentCardLabel, paymentCardholder, paymentCardNumber, paymentExpiry) }
                                .onSuccess {
                                    paymentCards = PaymentCardVault.entries(context)
                                    clearPaymentDraft()
                                    sheetKind = ""
                                }
                                .onFailure { paymentCardError = it.localizedMessage ?: "Could not save this payment method." }
                        }
                        if (paymentCardId.isNotBlank()) {
                            TextButton(onClick = {
                                runCatching { PaymentCardVault.remove(context, paymentCardId) }
                                    .onSuccess {
                                        paymentCards = PaymentCardVault.entries(context)
                                        clearPaymentDraft()
                                        sheetKind = ""
                                    }
                                    .onFailure { paymentCardError = it.localizedMessage ?: "Could not remove this payment method." }
                            }, modifier = Modifier.align(Alignment.End)) {
                                Text("Remove saved payment method", color = Color(0xFFB54747), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSectionHeading(title: String, subtitle: String) {
    Column(Modifier.padding(bottom = 11.dp)) {
        Text(title, color = ProfileInk, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.15.sp)
        Text(subtitle, color = ProfileMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun ProfileHeroCard(name: String, email: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF302B49), Color(0xFF242330), ElloPalette.Surface)))
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = Color.White.copy(alpha = 0.10f), shape = CircleShape, modifier = Modifier.size(58.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(name.trim().firstOrNull()?.uppercase() ?: "E", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
            Text(name.ifBlank { "Set up your profile" }, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(email.ifBlank { "Your personal details and Ello" }, color = Color.White.copy(alpha = 0.86f), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.Edit, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun ProfileCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ElloPalette.SurfaceRaised,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ProfileBorder),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun ProfileDetailRow(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = ProfileMuted, fontSize = 11.sp)
            Text(value.ifBlank { "Add $title" }, color = if (value.isBlank()) ProfileBlue else ProfileInk, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.Edit, null, tint = ProfileMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ProfilePersonRow(person: SavedPerson, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = ProfileRose.copy(alpha = 0.1f), shape = CircleShape, modifier = Modifier.size(46.dp)) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Text(person.name.trim().firstOrNull()?.uppercase() ?: "?", color = ProfileRose, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(person.name, color = ProfileInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            val details = listOf(person.relationship, person.email, person.phone).filter(String::isNotBlank).joinToString(" · ")
            Text(details.ifBlank { "Details will appear as Ello learns more" }, color = ProfileMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val foundFrom = person.sourceContext.ifBlank {
                person.sources.mapNotNull { source -> when (source) { "gmail" -> "Gmail"; "conversation" -> "Chat"; "user" -> "Added by you"; else -> null } }.distinct().joinToString(" · ")
            }
            if (foundFrom.isNotBlank()) Text(foundFrom, color = ProfileTeal, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ArrowForward, null, tint = ProfileMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ProfileMemoryCard(title: String, subtitle: String, preview: String, count: Int?) {
    ProfileCard {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = ProfileInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = ProfileMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 3.dp))
                }
                if (count != null && count > 0) {
                    Surface(color = ProfileTeal.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                        Text("$count", color = ProfileTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
            Text(preview, color = if (preview.startsWith("Ello will")) ProfileMuted else ProfileInk, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 11.dp))
            Text("ELLO MANAGED", color = ProfileRose, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 11.dp))
        }
    }
}

@Composable
private fun ProfileReadOnlyRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(ProfileCanvas).padding(13.dp)) {
        Text(label, color = ProfileMuted, fontSize = 11.sp)
        Text(value, color = ProfileInk, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ProfileInputField(label: String, value: String, onValueChange: (String) -> Unit, multiline: Boolean = false, secret: Boolean = false, keyboardOptions: KeyboardOptions = KeyboardOptions.Default) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = ProfileMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = androidx.compose.ui.text.TextStyle(color = ProfileInk, fontSize = 14.sp, lineHeight = 20.sp),
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            minLines = if (multiline) 3 else 1,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(ProfileCanvas).padding(horizontal = 13.dp, vertical = 12.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) Text("Add ${label.lowercase()}", color = ProfileMuted, fontSize = 14.sp)
                inner()
            },
        )
    }
}

@Composable
private fun ProfileSaveButton(enabled: Boolean = true, label: String, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp), colors = ButtonDefaults.buttonColors(containerColor = ProfileBlue, contentColor = ElloPalette.AccentText)) {
        Text(label, color = ElloPalette.AccentText, fontWeight = FontWeight.SemiBold)
    }
}

