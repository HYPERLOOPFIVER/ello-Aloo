package com.ello.litertchat

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val IntroBlue = ElloPalette.Accent
private val IntroBackground = ElloPalette.Canvas
private val NamingBlue = ElloPalette.Canvas
private val IntroWhite = ElloPalette.Text
private const val INTRO_TEXT = "Hi, I’m Ello. I love a warm cup of coffee and getting lost in a good book. When you’re ready, tell me what’s on your mind."

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun OnboardingIntroductionScreen(
    modifier: Modifier = Modifier,
    onNameSaved: (String) -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var typedText by rememberSaveable { mutableStateOf("") }
    var introFinished by rememberSaveable { mutableStateOf(false) }
    var showNameSheet by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var savingName by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val messageOffset by animateDpAsState(
        targetValue = if (introFinished) (-82).dp else 112.dp,
        animationSpec = tween(durationMillis = 850),
        label = "intro-text-position",
    )

    BackHandler(enabled = showNameSheet) { }

    LaunchedEffect(Unit) {
        INTRO_TEXT.forEach { character ->
            typedText += character
            if (!character.isWhitespace()) view.performElloHaptic(HapticFeedbackConstants.CLOCK_TICK)
            delay(
                when (character) {
                    '.', '!', '?' -> 390L
                    ',', ';', ':' -> 250L
                    ' ' -> 74L
                    else -> 82L
                },
            )
        }
        introFinished = true
        delay(1_050)
        showNameSheet = true
    }

    fun saveName() {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || savingName) return
        saveError = null
        savingName = true
        view.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
        scope.launch {
            runCatching { FirebaseOnboarding.saveUserName(context, cleanName) }
                .onSuccess {
                    view.performElloHaptic(HapticFeedbackConstants.CONFIRM)
                    showNameSheet = false
                    onNameSaved(cleanName)
                }
                .onFailure { failure ->
                    view.performElloHaptic(HapticFeedbackConstants.REJECT)
                    saveError = failure.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "I couldn’t save that yet. Check your connection and try again."
                }
            savingName = false
        }
    }

    if (showNameSheet) {
        ModalBottomSheet(
            onDismissRequest = { },
            sheetState = sheetState,
            sheetGesturesEnabled = false,
            containerColor = ElloPalette.Surface,
            contentColor = ElloPalette.Text,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            dragHandle = null,
            properties = ModalBottomSheetProperties(
                shouldDismissOnBackPress = false,
                shouldDismissOnClickOutside = false,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(38.dp).height(4.dp).background(ElloPalette.Border, CircleShape))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        color = ElloPalette.AccentSurface,
                        shape = CircleShape,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ello_chilling_transparent_still),
                            contentDescription = "Ello mascot",
                            modifier = Modifier.padding(3.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(
                            "What’s your name?",
                            color = ElloPalette.Text,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Ello will use your name to greet you in chat.",
                            color = ElloPalette.Muted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { value -> val next = value.take(40); view.performElloTypingHaptic(name, next); name = next; saveError = null },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Your name", color = ElloPalette.Muted) },
                    singleLine = true,
                    enabled = !savingName,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IntroBlue,
                        unfocusedBorderColor = ElloPalette.Border,
                        focusedContainerColor = ElloPalette.SurfaceRaised,
                        unfocusedContainerColor = ElloPalette.SurfaceRaised,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveName() }),
                )
                if (saveError != null) {
                    Text(saveError!!, color = ElloPalette.Error, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Button(
                    onClick = { saveName() },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = IntroBlue, contentColor = ElloPalette.AccentText),
                    enabled = name.trim().isNotEmpty() && !savingName,
                ) {
                    if (savingName) {
                        CircularProgressIndicator(Modifier.size(19.dp), color = ElloPalette.Accent, strokeWidth = 2.dp)
                        Text("Saving your name", modifier = Modifier.padding(start = 10.dp), fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Continue", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(IntroBackground)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        val mascotSize = minOf(maxWidth * 0.76f, 292.dp)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visible = !introFinished,
                modifier = Modifier.align(Alignment.Center).offset(y = (-82).dp),
                enter = fadeIn(animationSpec = tween(320)),
                exit = fadeOut(animationSpec = tween(460)) + scaleOut(
                    targetScale = 0.88f,
                    animationSpec = tween(460),
                ),
            ) {
                TransparentElloMascot(Modifier.size(mascotSize))
            }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = messageOffset)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = typedText + if (introFinished) "" else "▍",
                    color = IntroWhite,
                    fontSize = 19.sp,
                    lineHeight = 29.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun OnboardingNamingScreen(
    modifier: Modifier = Modifier,
    userName: String,
    onMascotNamed: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var typedText by rememberSaveable { mutableStateOf("") }
    var introFinished by rememberSaveable { mutableStateOf(false) }
    var showNameSheet by rememberSaveable { mutableStateOf(false) }
    var mascotName by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val messageOffset by animateDpAsState(
        targetValue = if (introFinished) (-82).dp else 112.dp,
        animationSpec = tween(durationMillis = 850),
        label = "mascot-name-intro-text-position",
    )
    val intro = "Now, give me a name! This is the name for your Ello companion. Your own name stays saved separately."

    BackHandler(enabled = showNameSheet) { }

    LaunchedEffect(Unit) {
        intro.forEach { character ->
            typedText += character
            if (!character.isWhitespace()) view.performElloHaptic(HapticFeedbackConstants.CLOCK_TICK)
            delay(
                when (character) {
                    '.', '!', '?' -> 390L
                    ',', ';', ':' -> 250L
                    ' ' -> 74L
                    else -> 82L
                },
            )
        }
        introFinished = true
        delay(1_050)
        showNameSheet = true
    }

    fun saveMascotName() {
        val cleanName = mascotName.trim()
        if (cleanName.isEmpty() || saving) return
        saveError = null
        saving = true
        view.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
        scope.launch {
            runCatching { FirebaseOnboarding.saveMascotName(context, cleanName) }
                .onSuccess {
                    view.performElloHaptic(HapticFeedbackConstants.CONFIRM)
                    onMascotNamed()
                }
                .onFailure { failure ->
                    view.performElloHaptic(HapticFeedbackConstants.REJECT)
                    saveError = failure.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "I couldn’t save that yet. Check your connection and try again."
                }
            saving = false
        }
    }

    if (showNameSheet) {
        ModalBottomSheet(
            onDismissRequest = { },
            sheetState = sheetState,
            sheetGesturesEnabled = false,
            containerColor = ElloPalette.Surface,
            contentColor = ElloPalette.Text,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            dragHandle = null,
            properties = ModalBottomSheetProperties(
                shouldDismissOnBackPress = false,
                shouldDismissOnClickOutside = false,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(38.dp).height(4.dp).background(ElloPalette.Border, CircleShape))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        color = ElloPalette.AccentSurface,
                        shape = CircleShape,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ello_chilling_transparent_still),
                            contentDescription = "Ello mascot",
                            modifier = Modifier.padding(3.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(
                            "Name your Ello",
                            color = ElloPalette.Text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Pick a name for your Ello companion${userName.trim().takeIf { it.isNotEmpty() }?.let { ", $it" }.orEmpty()}. Your name is saved separately.",
                            color = ElloPalette.Muted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = mascotName,
                    onValueChange = { value -> val next = value.take(24); view.performElloTypingHaptic(mascotName, next); mascotName = next; saveError = null },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Your Ello’s name", color = ElloPalette.Muted) },
                    singleLine = true,
                    enabled = !saving,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IntroBlue,
                        unfocusedBorderColor = ElloPalette.Border,
                        focusedContainerColor = ElloPalette.SurfaceRaised,
                        unfocusedContainerColor = ElloPalette.SurfaceRaised,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveMascotName() }),
                )
                if (saveError != null) {
                    Text(saveError!!, color = ElloPalette.Error, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Button(
                    onClick = { saveMascotName() },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = IntroBlue, contentColor = ElloPalette.AccentText),
                    enabled = mascotName.trim().isNotEmpty() && !saving,
                ) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(19.dp), color = ElloPalette.Accent, strokeWidth = 2.dp)
                        Text("Saving your Ello’s name", modifier = Modifier.padding(start = 10.dp), fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Continue", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(NamingBlue)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        val mascotSize = minOf(maxWidth * 0.76f, 292.dp)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visible = !introFinished,
                modifier = Modifier.align(Alignment.Center).offset(y = (-82).dp),
                enter = fadeIn(animationSpec = tween(320)),
                exit = fadeOut(animationSpec = tween(460)) + scaleOut(
                    targetScale = 0.88f,
                    animationSpec = tween(460),
                ),
            ) {
                ElloAnimatedMascot(
                    rawResId = R.raw.ello_naming,
                    modifier = Modifier.size(mascotSize).clip(RoundedCornerShape(80.dp)).background(NamingBlue),
                    contentDescription = "Ello making a friendly pose",
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = messageOffset)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = typedText + if (introFinished) "" else "▍",
                    color = Color.White,
                    fontSize = 19.sp,
                    lineHeight = 29.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun TransparentElloMascot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val stillDrawable = remember(context) {
        context.resources.getDrawable(R.drawable.ello_chilling_transparent_still, context.theme)
    }
    val animatedDrawable: Drawable? = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeDrawable(
                ImageDecoder.createSource(context.resources, R.raw.ello_chilling_transparent),
            ).also { drawable ->
                (drawable as? AnimatedImageDrawable)?.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            }
        } else null
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            ImageView(viewContext).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setImageDrawable(animatedDrawable ?: stillDrawable)
                (drawable as? AnimatedImageDrawable)?.start()
            }
        },
        update = { imageView ->
            if (imageView.drawable !== (animatedDrawable ?: stillDrawable)) {
                imageView.setImageDrawable(animatedDrawable ?: stillDrawable)
                (imageView.drawable as? AnimatedImageDrawable)?.start()
            }
        },
        onRelease = { imageView ->
            (imageView.drawable as? AnimatedImageDrawable)?.stop()
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun OnboardingConnectServicesScreen(
    modifier: Modifier = Modifier,
    gmailConnected: Boolean,
    calendarConnected: Boolean,
    tasksConnected: Boolean,
    driveConnected: Boolean,
    sheetsConnected: Boolean,
    connectingService: String?,
    onConnectGmail: () -> Unit,
    onConnectCalendar: () -> Unit,
    onConnectTasks: () -> Unit,
    onConnectDrive: () -> Unit,
    onConnectSheets: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val view = LocalView.current
    var search by remember { mutableStateOf("") }
    var showConnected by remember { mutableStateOf(false) }
    val anyConnected = gmailConnected || calendarConnected || tasksConnected || driveConnected || sheetsConnected
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    BoxWithConstraints(modifier.fillMaxSize().background(IntroBackground)) {
        val screenHeight = maxHeight
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(screenHeight * 0.40f), contentAlignment = Alignment.Center) {
                val artSize = minOf(screenHeight * 0.40f, 310.dp)
                ElloAnimatedMascot(rawResId = R.raw.ello_x_gmail_and_connections, modifier = Modifier.size(artSize), contentDescription = "Ello mascot")
            }
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = ElloPalette.Surface,
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).padding(top = 13.dp)) {
                    Box(Modifier.align(Alignment.CenterHorizontally).width(38.dp).height(4.dp).clip(CircleShape).background(ElloPalette.Border))
                    Row(Modifier.fillMaxWidth().padding(top = 17.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Connect your services", color = ElloPalette.Text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (anyConnected) {
                            TextButton(onClick = { view.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP); showConnected = true }) {
                                Text("Connected", color = IntroBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = search,
                        onValueChange = { value -> view.performElloTypingHaptic(search, value); search = value },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Search services", color = ElloPalette.Muted, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = ElloPalette.Muted, modifier = Modifier.size(19.dp)) },
                        shape = RoundedCornerShape(15.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = ElloPalette.SurfaceRaised,
                            focusedContainerColor = ElloPalette.SurfaceRaised,
                            unfocusedBorderColor = ElloPalette.Border,
                            focusedBorderColor = IntroBlue,
                        ),
                    )
                    LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 7.dp)) {
                        if ("gmail".contains(search.trim(), ignoreCase = true)) item {
                            ServiceConnectorButton("Gmail", "Search, read, send and organize mail", Icons.Default.Email, Color(0xFFEA4335), gmailConnected, connectingService == "Gmail", connectingService == null, { view.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP); onConnectGmail() })
                        }
                        if ("google calendar calendar".contains(search.trim(), ignoreCase = true)) item {
                            ServiceConnectorButton("Google Calendar", "Find and add events when you ask", Icons.Default.CalendarMonth, Color(0xFF4285F4), calendarConnected, connectingService == "Google Calendar", connectingService == null, { view.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP); onConnectCalendar() })
                        }
                        if ("google tasks tasks".contains(search.trim(), ignoreCase = true)) item {
                            ServiceConnectorButton("Google Tasks", "Read and manage tasks with Ello", Icons.Default.CalendarMonth, Color(0xFF4285F4), tasksConnected, connectingService == "Google Tasks", connectingService == null, { view.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP); onConnectTasks() })
                        }
                        if ("google drive drive".contains(search.trim(), ignoreCase = true)) item {
                            ServiceConnectorButton("Google Drive", "Search and read files in your Drive", Icons.Default.Email, Color(0xFF0F9D58), driveConnected, connectingService == "Google Drive", connectingService == null, { view.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP); onConnectDrive() })
                        }
                        if ("google sheets sheets spreadsheet spreadsheets".contains(search.trim(), ignoreCase = true)) item {
                            ServiceConnectorButton("Google Sheets", "Create sheets or update selected ones", Icons.Default.CalendarMonth, Color(0xFF188038), sheetsConnected, connectingService == "Google Sheets", connectingService == null, { view.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP); onConnectSheets() })
                        }
                    }
                    Button(
                        onClick = { view.performElloHaptic(HapticFeedbackConstants.CONFIRM); if (anyConnected) onContinue() else onSkip() },
                        enabled = connectingService == null,
                        modifier = Modifier.fillMaxWidth().height(52.dp).padding(bottom = 2.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IntroBlue, contentColor = ElloPalette.AccentText),
                    ) {
                        Text(if (anyConnected) "Continue" else "Skip for now", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = { view.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP); onSkip() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("You can connect services later", color = ElloPalette.Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    if (showConnected) {
        ModalBottomSheet(
            onDismissRequest = { showConnected = false },
            sheetState = sheetState,
            containerColor = ElloPalette.Surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = { Box(Modifier.padding(vertical = 12.dp).width(38.dp).height(4.dp).clip(CircleShape).background(ElloPalette.Border)) },
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
                Text("Connected services", color = ElloPalette.Text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
                if (gmailConnected) ConnectedServiceRow("Gmail", Icons.Default.Email, Color(0xFFEA4335))
                if (calendarConnected) ConnectedServiceRow("Google Calendar", Icons.Default.CalendarMonth, Color(0xFF4285F4))
                if (tasksConnected) ConnectedServiceRow("Google Tasks", Icons.Default.CalendarMonth, Color(0xFF4285F4))
                if (driveConnected) ConnectedServiceRow("Google Drive", Icons.Default.Email, Color(0xFF0F9D58))
                if (sheetsConnected) ConnectedServiceRow("Google Sheets", Icons.Default.CalendarMonth, Color(0xFF188038))
            }
        }
    }
}

@Composable
private fun ConnectedServiceRow(title: String, icon: ImageVector, tint: Color) {
    Surface(color = ElloPalette.SurfaceRaised, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, ElloPalette.Border), modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
            Text(title, color = ElloPalette.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(start = 12.dp))
            Text("Connected", color = ElloPalette.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ServiceConnectorButton(
    title: String,
    detail: String,
    icon: ImageVector,
    iconTint: Color,
    connected: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled && (!connected || title == "Google Drive"),
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        contentColor = ElloPalette.Text,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(84.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(iconTint.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(19.dp))
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    title,
                    color = ElloPalette.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detail,
                    color = ElloPalette.Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.width(82.dp), contentAlignment = Alignment.CenterEnd) {
                when {
                    busy -> CircularProgressIndicator(Modifier.size(18.dp), color = IntroBlue, strokeWidth = 2.dp)
                    connected && title != "Google Drive" -> Text("✓  Connected", color = ElloPalette.Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    connected -> Surface(color = ElloPalette.AccentSurface, shape = RoundedCornerShape(50)) { Text("Add files", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = ElloPalette.Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                    else -> Surface(color = ElloPalette.AccentSurface, shape = RoundedCornerShape(50)) { Text("Connect", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = ElloPalette.Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                }
            }
        }
    }
}
