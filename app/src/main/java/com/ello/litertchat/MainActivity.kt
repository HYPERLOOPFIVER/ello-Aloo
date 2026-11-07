package com.ello.litertchat

import android.content.Context
import android.graphics.BitmapFactory
import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.os.IBinder
import android.view.Surface as AndroidSurface
import android.view.TextureView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceResponse
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import org.json.JSONObject
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import android.widget.VideoView
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.io.FileOutputStream

// Ello graphite and soft-green design system.
private val MuseCanvas = ElloPalette.Canvas
private val MuseSurface = ElloPalette.Surface
private val MuseSurfaceRaised = ElloPalette.SurfaceRaised
private val MuseBorder = ElloPalette.Border
private val MuseAssistantBubble = ElloPalette.SurfaceRaised
private val MuseAssistantText = ElloPalette.Text
private val MuseUserBubble = ElloPalette.AccentSurface
private val MuseUserText = ElloPalette.Text
private val MuseMuted = ElloPalette.Muted
private val MuseIconGray = ElloPalette.Muted
private val MusePdfRed = Color(0xFFFF2D55)
private val MuseAccent = ElloPalette.Accent
private val ElloWelcomeBackground = ElloPalette.Canvas
private val ElloWelcomeBlue = ElloPalette.Accent
private val ElloWelcomeInk = ElloPalette.Text

private enum class WelcomeInfoSheet(val title: String, val body: String) {
    Privacy(
        "Privacy policy",
        "Ello keeps your profile and chat notes on this phone and syncs profile details with Firebase.\n\nYou can delete saved chats from the chat list. Logging out clears the local profile, chats, and Google access. Uninstalling Ello removes its saved app data from this phone."
    ),
    Terms(
        "Terms of use",
        "Ello provides profile, chat, feed, and connected-service screens. Firebase provides sign-in and profile sync. Use Ello lawfully and only add files you have the right to use."
    ),
}

// Theme Aliases
private val Ink = MuseCanvas
private val Panel = MuseSurface
private val Elevated = MuseBorder
private val Muted = MuseMuted
private val Accent = MuseAccent
private val White = MuseAssistantText
private val ChatCanvas = MuseCanvas
private val ChatInk = MuseAssistantText
private val ChatMuted = MuseMuted
private val ElloBubble = MuseAssistantBubble
private val UserBubble = MuseUserBubble
private val ElloMint = MuseUserBubble
private const val MAX_ATTACHMENT_BYTES = 15L * 1024 * 1024
private const val MAX_ATTACHMENT_TEXT_CHARS = 10_000
private const val MAX_PDF_PAGES = 40
private const val MAX_ATTACHMENTS_PER_MESSAGE = 3
@Immutable
data class ChatAttachment(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val extractedText: String,
    val localPath: String = "",
)

@Immutable
data class ChatLine(
    val id: Long,
    val user: Boolean,
    val body: String,
    val timing: String = "",
    val attachments: List<ChatAttachment> = emptyList(),
    val skillWebviewUrl: String = "",
    val skillWebviewAspect: Float = 1.333f,
    val skillWebviewIframe: Boolean = false,
    val geminiContentsJson: String = "",
    val resumeContentsJson: String = "",
    val taskTitle: String = "",
    val taskSteps: List<String> = emptyList(),
    val completedTaskSteps: Set<Int> = emptySet(),
)

class MainActivity : ComponentActivity() {
    private val boundService = kotlinx.coroutines.flow.MutableStateFlow<ChatRuntimeService?>(null)
    internal val chatService: kotlinx.coroutines.flow.StateFlow<ChatRuntimeService?> = boundService
    private var serviceBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            boundService.value = (binder as? ChatRuntimeService.LocalBinder)?.service()
        }
        override fun onServiceDisconnected(name: ComponentName?) { boundService.value = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent { ElloApp() }
    }

    override fun onStart() {
        super.onStart()
        serviceBound = bindService(Intent(this, ChatRuntimeService::class.java), serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
            boundService.value = null
        }
        super.onStop()
    }
}

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
private fun ElloApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as MainActivity
    val service by activity.chatService.collectAsState()
    val emptyRuntime = remember { kotlinx.coroutines.flow.MutableStateFlow(ChatRuntimeSnapshot()) }
    val runtime by (service?.snapshot ?: emptyRuntime).collectAsState()
    val browserState by LocalBrowserSession.browserState.collectAsState()
    val messages = runtime.messages
    var draft by rememberSaveable { mutableStateOf("") }
    var threadSearch by rememberSaveable { mutableStateOf("") }
    var selectedDestination by rememberSaveable { mutableStateOf(2) }
    var showSettings by remember { mutableStateOf(false) }
    var sharedApiCatalogEnabled by remember {
        mutableStateOf(context.getSharedPreferences("api_anything_settings", Context.MODE_PRIVATE).getBoolean("shared_catalog_enabled", false))
    }
    var showModelManager by remember { mutableStateOf(false) }
    var showSkillsManager by remember { mutableStateOf(false) }
    var showSkillUrlDialog by remember { mutableStateOf(false) }
    var skillUrl by rememberSaveable { mutableStateOf("") }
    var mcpServerUrl by rememberSaveable { mutableStateOf("") }
    var mcpConnectionBusy by remember { mutableStateOf(false) }
    var pendingMcpApproval by remember { mutableStateOf<McpApprovalRequest?>(null) }
    var pendingAndroidPermission by remember { mutableStateOf<AndroidPermissionRequest?>(null) }
    var pendingSkillSecret by remember { mutableStateOf<SkillSecretRequest?>(null) }
    var skillSecretValue by remember { mutableStateOf("") }
    var skills by remember { mutableStateOf(emptyList<ElloSkill>()) }
    var showGoogleServices by remember { mutableStateOf(false) }
    var welcomeInfoSheet by remember { mutableStateOf<WelcomeInfoSheet?>(null) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    var isShy by remember { mutableStateOf(false) }
    LaunchedEffect(isShy) {
        if (isShy) {
            kotlinx.coroutines.delay(3500)
            isShy = false
        }
    }
    val currentMascotRes = when {
        isShy -> R.raw.ello_shy
        else -> R.raw.ello_waving
    }
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val googleServicesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val deleteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val logoutSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarState = remember { SnackbarHostState() }
    var pendingGoogleService by remember { mutableStateOf<String?>(null) }
    var googleConnectBusy by remember { mutableStateOf(false) }
    var googleConnectionsVersion by remember { mutableStateOf(0) }
    val gmailConnected = remember(googleConnectionsVersion) { GoogleApiAccess.gmailConnected(context) }
    val calendarConnected = remember(googleConnectionsVersion) { GoogleApiAccess.calendarConnected(context) }
    val tasksConnected = remember(googleConnectionsVersion) { GoogleApiAccess.tasksConnected(context) }
    val driveConnected = GoogleApiAccess.driveConnected(context)
    val sheetsConnected = remember(googleConnectionsVersion) { GoogleApiAccess.sheetsConnected(context) }
    val googleAuthorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val serviceName = pendingGoogleService
        pendingGoogleService = null
        googleConnectBusy = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null && serviceName != null) {
            runCatching {
                Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(result.data)
            }.onSuccess { authorizationResult ->
                                runCatching { saveGoogleAuthorization(context, serviceName, authorizationResult) }
                    .onSuccess {
                        googleConnectionsVersion++
                        scope.launch {
                            runCatching {
                                FirebaseOnboarding.saveConnectedGoogleScopes(context, GoogleApiAccess.allGrantedScopes(context))
                            }.onFailure {
                                snackbarState.showSnackbar("Google is connected on this phone, but its status could not be synced to your profile.")
                            }
                        }
                        scope.launch { snackbarState.showSnackbar("$serviceName connected. Ello can now use it in chat.") }
                    }
                    .onFailure { failure ->
                        scope.launch { snackbarState.showSnackbar(failure.localizedMessage ?: "Google did not provide access to $serviceName.") }
                    }
            }.onFailure { failure ->
                scope.launch { snackbarState.showSnackbar(failure.localizedMessage ?: "Could not connect $serviceName.") }
            }
        } else if (result.resultCode != Activity.RESULT_CANCELED) {
            scope.launch { snackbarState.showSnackbar("Google did not finish connecting. Try again.") }
        }
    }
    val agentPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingAndroidPermission?.let { request ->
            request.answer.complete(granted)
            pendingAndroidPermission = null
        }
    }
    fun connectGoogleService(serviceName: String) {
        if (googleConnectBusy) return
        googleConnectBusy = true
        pendingGoogleService = serviceName
        val requestedScopes = when (serviceName) {
            "Gmail" -> listOf(GMAIL_SEND_SCOPE, GMAIL_MODIFY_SCOPE, GMAIL_SETTINGS_SCOPE)
            "Google Calendar" -> listOf(CALENDAR_EVENTS_SCOPE)
            "Google Tasks" -> listOf(GOOGLE_TASKS_SCOPE)
            "Google Drive" -> listOf(GOOGLE_DRIVE_FILE_SCOPE, GOOGLE_DRIVE_READ_SCOPE)
            "Google Sheets" -> listOf(GOOGLE_DRIVE_FILE_SCOPE)
            else -> error("Unknown Google service: $serviceName")
        }
        scope.launch {
            runCatching {
                val request = AuthorizationRequest.builder()
                    .setRequestedScopes(requestedScopes.map(::Scope))
                    .apply {
                        if (serviceName == "Google Drive") {
                            setOptOutIncludingGrantedScopes(true)
                            setPrompt(AuthorizationRequest.Prompt.CONSENT)
                            addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_OAUTH_TRIGGER, "true")
                            addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_ALLOW_MULTIPLE, "true")
                            addResourceParameter(
                                AuthorizationRequest.ResourceParameter.PICKER_MIMETYPES,
                                "application/vnd.google-apps.document,application/vnd.google-apps.spreadsheet,application/pdf,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            )
                        }
                    }
                    .build()
                Identity.getAuthorizationClient(activity).authorize(request).await()
            }.onSuccess { authorizationResult ->
                if (authorizationResult.hasResolution()) {
                    runCatching {
                        googleAuthorizationLauncher.launch(
                            IntentSenderRequest.Builder(authorizationResult.pendingIntent!!).build(),
                        )
                    }.onFailure { failure ->
                        pendingGoogleService = null
                        googleConnectBusy = false
                        scope.launch { snackbarState.showSnackbar(failure.localizedMessage ?: "Could not open Google permission dialog.") }
                    }
                } else {
                    pendingGoogleService = null
                    googleConnectBusy = false
                    runCatching { saveGoogleAuthorization(context, serviceName, authorizationResult) }
                        .onSuccess {
                            googleConnectionsVersion++
                            scope.launch {
                                runCatching {
                                    FirebaseOnboarding.saveConnectedGoogleScopes(context, GoogleApiAccess.allGrantedScopes(context))
                                }
                                snackbarState.showSnackbar("$serviceName connected. Ello can now use it in chat.")
                            }
                        }
                        .onFailure { failure ->
                            scope.launch { snackbarState.showSnackbar(failure.localizedMessage ?: "Google did not provide access to $serviceName.") }
                        }
                }
            }.onFailure { failure ->
                pendingGoogleService = null
                googleConnectBusy = false
                snackbarState.showSnackbar(failure.localizedMessage ?: "Could not connect $serviceName.")
            }
        }
    }
    val hapticView = LocalView.current
    var onboardingWelcomeVisible by remember { mutableStateOf(true) }
    var onboardingChatVisible by remember { mutableStateOf(false) }
    var onboardingNamingVisible by remember { mutableStateOf(false) }
    var onboardingConnectVisible by remember { mutableStateOf(false) }
    var onboardingPhoneVisible by remember { mutableStateOf(false) }
    var onboardingPhoneSaving by remember { mutableStateOf(false) }
    var onboardingPhone by rememberSaveable { mutableStateOf("") }
    var onboardingDisplayName by rememberSaveable { mutableStateOf("") }
    var onboardingCompleteVisible by remember { mutableStateOf(false) }
    val isFirstRunWelcome = onboardingWelcomeVisible
    val showWelcomeHeader = onboardingWelcomeVisible
    val activeScreenKey = when {
        onboardingWelcomeVisible -> "welcome"
        onboardingChatVisible -> "introduction"
        onboardingNamingVisible -> "mascot-name"
        onboardingConnectVisible -> "connect-services"
        onboardingPhoneVisible -> "phone-number"
        onboardingCompleteVisible -> "ready"
        else -> "destination-$selectedDestination"
    }
    var previousScreenKey by remember { mutableStateOf(activeScreenKey) }
    LaunchedEffect(activeScreenKey) {
        if (previousScreenKey != activeScreenKey) {
            hapticView.performElloHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
            previousScreenKey = activeScreenKey
        }
    }
    val pendingAttachments = remember { mutableStateListOf<ChatAttachment>() }
    var attachmentLoading by remember { mutableStateOf(false) }
    LaunchedEffect(drawerState) {
        var hasObservedState = false
        snapshotFlow { drawerState.currentValue }.distinctUntilChanged().collect {
            if (hasObservedState) hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
            hasObservedState = true
        }
    }

    fun importDocument(uri: Uri) {
        if (attachmentLoading) return
        attachmentLoading = true
        scope.launch {
            try {
                val attachment = withContext(Dispatchers.IO) {
                    val mimeType = context.contentResolver.getType(uri).orEmpty()
                    require(mimeType == "application/pdf" || mimeType == "text/plain" || mimeType.startsWith("image/") || mimeType in setOf("audio/wav", "audio/x-wav", "audio/wave")) {
                        "Choose a PDF, text file, image, or WAV audio clip."
                    }
                    var name = if (mimeType == "application/pdf") "Document.pdf" else "Notes.txt"
                    var declaredSize = 0L
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) declaredSize = cursor.getLong(sizeIndex)
                        }
                    }
                    require(declaredSize <= MAX_ATTACHMENT_BYTES || declaredSize == 0L) {
                        "Choose a file smaller than 15 MB."
                    }
                    val temp = File(context.cacheDir, "ello-attachment-${System.nanoTime()}.tmp")
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(temp).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var total = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= MAX_ATTACHMENT_BYTES) { "Choose a file smaller than 15 MB." }
                                    output.write(buffer, 0, count)
                                }
                                output.fd.sync()
                            }
                        } ?: error("Could not open that file.")
                        val extractedText = if (mimeType == "application/pdf") {
                            PDFBoxResourceLoader.init(context.applicationContext)
                            PDDocument.load(temp).use { document ->
                                require(document.numberOfPages <= MAX_PDF_PAGES) {
                                    "This PDF has more than $MAX_PDF_PAGES pages. Split it into smaller files first."
                                }
                                PDFTextStripper().getText(document).take(MAX_ATTACHMENT_TEXT_CHARS)
                            }
                        } else if (mimeType == "text/plain") {
                            temp.readText(Charsets.UTF_8).take(MAX_ATTACHMENT_TEXT_CHARS)
                        } else ""
                        require(extractedText.isNotBlank() || mimeType.startsWith("image/") || mimeType.startsWith("audio/")) {
                            "No selectable text was found. Scanned PDFs need OCR, which is not enabled yet."
                        }
                        val mediaDir = File(context.filesDir, "chat-attachments").apply { mkdirs() }
                        val saved = File(mediaDir, "${System.nanoTime()}-${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
                        temp.copyTo(saved, overwrite = true)
                        ChatAttachment(name, mimeType, saved.length(), extractedText, saved.absolutePath)
                    } finally {
                        temp.delete()
                    }
                }
                if (pendingAttachments.size < MAX_ATTACHMENTS_PER_MESSAGE) pendingAttachments.add(attachment)
                else snackbarState.showSnackbar("Attach up to $MAX_ATTACHMENTS_PER_MESSAGE files to one message.")
                hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
            } catch (e: Exception) {
                snackbarState.showSnackbar(e.message ?: "Could not read that file.")
            } finally {
                attachmentLoading = false
            }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importDocument(uri)
    }

    val skillPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val markdown = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Could not read the selected SKILL.md file.")
                }
                service?.addSkillMarkdown(markdown) ?: error("Chat service is not ready yet.")
                skills = service?.getSkills().orEmpty()
                snackbarState.showSnackbar("Skill added.")
            }.onFailure { snackbarState.showSnackbar(it.localizedMessage ?: "Could not import that skill.") }
        }
    }
    val skillFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                service?.addLocalSkillFolder(uri) ?: error("Chat service is not ready yet.")
                skills = service?.getSkills().orEmpty()
                snackbarState.showSnackbar("Skill folder imported.")
            }.onFailure { snackbarState.showSnackbar(it.localizedMessage ?: "Could not import that skill folder.") }
        }
    }

    LaunchedEffect(service) {
        val requests = service?.mcpApprovalRequests ?: return@LaunchedEffect
        for (request in requests) {
            pendingMcpApproval = request
            request.answer.await()
            if (pendingMcpApproval === request) pendingMcpApproval = null
        }
    }
    LaunchedEffect(service) {
        val requests = service?.androidPermissionRequests ?: return@LaunchedEffect
        for (request in requests) {
            pendingAndroidPermission = request
            request.answer.await()
            if (pendingAndroidPermission === request) pendingAndroidPermission = null
        }
    }
    LaunchedEffect(service) {
        val requests = service?.skillSecretRequests ?: return@LaunchedEffect
        for (request in requests) {
            pendingSkillSecret = request
            skillSecretValue = ""
            request.answer.await()
            if (pendingSkillSecret === request) pendingSkillSecret = null
        }
    }

    LaunchedEffect(Unit) {
        val onboardingPreferences = context.getSharedPreferences("ello_onboarding", Context.MODE_PRIVATE)
        val profilePreferences = context.getSharedPreferences("ello_user_profile", Context.MODE_PRIVATE)
        val onboardingComplete = onboardingPreferences.getBoolean("complete", false)
        val savedName = profilePreferences.getString("displayName", null)?.trim().orEmpty()
        val savedMascotName = profilePreferences.getString("mascotName", null)?.trim().orEmpty()
        onboardingDisplayName = savedName
        if (onboardingComplete) {
            onboardingWelcomeVisible = false
        } else if (savedName.isNotEmpty()) {
            onboardingWelcomeVisible = false
            onboardingDisplayName = savedName
            onboardingNamingVisible = savedMascotName.isEmpty()
            onboardingConnectVisible = savedMascotName.isNotEmpty()
        }
        runCatching { FirebaseOnboarding.loadSavedProfile(context) }
            .getOrNull()
            ?.displayName
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { onboardingDisplayName = it }
    }

    fun sendMessage() {
        val text = draft.trim()
        if ((text.isEmpty() && pendingAttachments.isEmpty()) || attachmentLoading) return
        draft = ""
        service?.sendMessage(text, pendingAttachments.toList())
        pendingAttachments.clear()
        hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun finishOnboarding(phoneNumber: String) {
        onboardingPhoneSaving = true
        onboardingPhone = phoneNumber
        context.getSharedPreferences("ello_onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("complete", true).apply()
        onboardingConnectVisible = false
        onboardingPhoneVisible = false
        onboardingCompleteVisible = false
        context.getSharedPreferences("ello_user_profile", Context.MODE_PRIVATE)
            .edit().putString("phone", phoneNumber).apply()
        scope.launch {
            runCatching {
                FirebaseOnboarding.savePhoneNumber(context, phoneNumber)
                FirebaseOnboarding.markOnboardingComplete(context)
            }
                .onFailure { snackbarState.showSnackbar("Onboarding is saved on this phone. Your profile could not sync to Firestore yet.") }
            onboardingPhoneSaving = false
        }
    }

    BackHandler(enabled = onboardingChatVisible) {
        onboardingChatVisible = false
        onboardingWelcomeVisible = true
    }
    BackHandler(enabled = onboardingPhoneVisible) {
        onboardingPhoneVisible = false
        onboardingConnectVisible = true
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ElloPalette.Accent,
            onPrimary = ElloPalette.AccentText,
            background = ChatCanvas,
            onBackground = ChatInk,
            surface = Panel,
            onSurface = ChatInk,
            onSurfaceVariant = ChatMuted,
            secondary = ElloPalette.AccentStrong,
            surfaceVariant = ElloPalette.SurfaceRaised,
            outline = ElloPalette.Border,
            error = ElloPalette.Error,
        ),
        shapes = Shapes(
            small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(30.dp),
        ),
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !onboardingWelcomeVisible && !onboardingChatVisible && !onboardingNamingVisible && !onboardingConnectVisible && !onboardingPhoneVisible && !onboardingCompleteVisible,
            scrimColor = Color.Black.copy(alpha = 0.18f),
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MuseSurface,
                    drawerTonalElevation = 0.dp,
                ) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 8.dp, top = 26.dp, end = 4.dp, bottom = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ElloAnimatedMascot(
                                rawResId = R.raw.ello_waving,
                                modifier = Modifier.size(42.dp).clip(CircleShape).background(MuseCanvas),
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text("Ello", color = MuseAssistantText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                Text("Ello · frontend preview", color = MuseMuted, fontSize = 11.5.sp)
                            }
                        }
                        val chatService = service
                        Surface(
                            onClick = {
                                hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                if (chatService == null) {
                                    scope.launch { snackbarState.showSnackbar("Ello is still starting. Try again in a moment.") }
                                } else {
                                    draft = ""
                                    pendingAttachments.clear()
                                    chatService.createNewChat()
                                    scope.launch { drawerState.close() }
                                }
                            },
                            color = MuseSurface,
                            contentColor = MuseAssistantText,
                            shadowElevation = 6.dp,
                            border = BorderStroke(1.dp, MuseBorder),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, null, tint = MuseAssistantText, modifier = Modifier.size(20.dp))
                                Text("New chat", color = MuseAssistantText, fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
                            }
                        }
                        HorizontalDivider(color = MuseBorder)
                        Surface(
                            color = MuseSurfaceRaised,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Search, null, tint = MuseMuted, modifier = Modifier.size(18.dp))
                                BasicTextField(
                                    value = threadSearch,
                                    onValueChange = { value -> hapticView.performElloTypingHaptic(threadSearch, value); threadSearch = value },
                                    modifier = Modifier.weight(1f).padding(horizontal = 9.dp, vertical = 12.dp),
                                    singleLine = true,
                                    textStyle = TextStyle(color = MuseAssistantText, fontSize = 13.sp),
                                    decorationBox = { inner ->
                                        Box(contentAlignment = Alignment.CenterStart) {
                                            if (threadSearch.isEmpty()) Text("Search chats", color = MuseMuted, fontSize = 13.sp)
                                            inner()
                                        }
                                    },
                                )
                                if (threadSearch.isNotEmpty()) {
                                    IconButton(onClick = { hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP); threadSearch = "" }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Default.Close, "Clear chat search", tint = MuseMuted, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                        Text("CHATS", color = MuseMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
                        val filteredThreads = runtime.threads.filter { thread ->
                            threadSearch.isBlank() || thread.title.contains(threadSearch, ignoreCase = true) ||
                                thread.messages.any { it.body.contains(threadSearch, ignoreCase = true) }
                        }
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            items(filteredThreads, key = { it.id }) { thread ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NavigationDrawerItem(
                                        label = { Text(thread.title, maxLines = 1, color = MuseAssistantText, fontSize = 13.sp) },
                                        selected = thread.id == runtime.activeThreadId,
                                        onClick = {
                                            hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                            service?.openChat(thread.id)
                                            scope.launch { drawerState.close() }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp),
                                    )
                                    IconButton(
                                        onClick = { hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP); pendingDeleteId = thread.id },
                                        enabled = true,
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, "Delete ${thread.title}", tint = MuseMuted, modifier = Modifier.size(19.dp))
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MuseBorder)
                        NavigationDrawerItem(
                            label = { Text("Settings", color = MuseAssistantText, fontSize = 13.sp) },
                            selected = false,
                            icon = { Icon(Icons.Default.Tune, null, tint = MuseMuted) },
                            onClick = {
                                hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                showSettings = true
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(top = 8.dp),
                            shape = RoundedCornerShape(14.dp),
                        )
                        NavigationDrawerItem(
                            label = { Text("Logout", color = ElloPalette.Error, fontSize = 13.sp) },
                            selected = false,
                            icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = ElloPalette.Error) },
                            onClick = {
                                hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                showLogoutConfirmation = true
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(top = 2.dp),
                            shape = RoundedCornerShape(14.dp),
                        )
                        Text("Firebase-connected frontend", color = MuseMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 16.dp))
                    }
                }
            },
        ) {
        Scaffold(
            containerColor = when {
                onboardingWelcomeVisible -> ElloWelcomeBackground
                onboardingChatVisible -> ElloWelcomeBackground
                onboardingNamingVisible -> ElloPalette.Canvas
                onboardingConnectVisible -> ElloWelcomeBackground
                onboardingPhoneVisible -> ElloWelcomeBackground
                onboardingCompleteVisible -> ElloWelcomeBackground
                else -> MuseCanvas
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarState) },
            topBar = {
                if (selectedDestination == 4 || onboardingChatVisible || onboardingNamingVisible || onboardingConnectVisible || onboardingPhoneVisible) {
                    Spacer(Modifier.height(0.dp))
                } else if (onboardingCompleteVisible) {
                    Spacer(Modifier.height(0.dp))
                } else if (showWelcomeHeader) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(ElloWelcomeBackground)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .height(54.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "ELLO",
                            color = ElloWelcomeInk,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                        )
                    }
                } else Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (showWelcomeHeader) ElloWelcomeBackground else MuseCanvas)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).clickable {
                            hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                            scope.launch { drawerState.open() }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.width(19.dp).height(2.dp).clip(CircleShape).background(MuseAssistantText))
                            Box(Modifier.width(19.dp).height(2.dp).clip(CircleShape).background(MuseAssistantText))
                        }
                    }

                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier.clickable {
                                hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                isShy = true
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            ElloAnimatedMascot(
                                rawResId = currentMascotRes,
                                modifier = Modifier.size(52.dp).clip(CircleShape).background(MuseCanvas),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    if (onboardingDisplayName.isBlank()) "Ello" else "Ello · ${onboardingDisplayName.trim()}",
                                    color = MuseAssistantText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.size(44.dp))
                }
            },
            bottomBar = {
                if (!isFirstRunWelcome && !onboardingChatVisible && !onboardingNamingVisible && !onboardingConnectVisible && !onboardingPhoneVisible && !onboardingCompleteVisible) {
                val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                Box(
                    Modifier.fillMaxWidth().then(
                        if (imeVisible) Modifier.imePadding()
                        else Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(82.dp),
                    ),
                ) {
                    if (selectedDestination == 2) {
                    Column(
                        Modifier.align(if (imeVisible) Alignment.BottomCenter else Alignment.TopCenter)
                        .offset(y = if (imeVisible) (-18).dp else (-75).dp)
                            .fillMaxWidth()
                            .widthIn(max = 620.dp)
                            .padding(horizontal = 18.dp)
                            .padding(bottom = if (imeVisible) 8.dp else 0.dp),
                    ) {
                    if (pendingAttachments.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pendingAttachments.forEachIndexed { index, attachment ->
                                PendingAttachmentCard(attachment) {
                                    pendingAttachments.removeAt(index)
                                    hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            }
                        }
                    }
                    // Ello's floating message composer
                    Surface(
                        shape = RoundedCornerShape(30.dp),
                        color = MuseSurface.copy(alpha = 0.78f),
                        border = BorderStroke(1.dp, MuseBorder.copy(alpha = 0.72f)),
                        shadowElevation = 12.dp,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            var showAttachmentMenu by remember { mutableStateOf(false) }
                            val focusManager = LocalFocusManager.current

                            // Left Plus Button
                            Box {
                                IconButton(
                                    onClick = {
                                        showAttachmentMenu = true
                                        hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                    },
                                    enabled = !attachmentLoading,
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    if (attachmentLoading) {
                                        CircularProgressIndicator(Modifier.size(17.dp), color = MuseIconGray, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Add, contentDescription = "Attach", tint = MuseIconGray, modifier = Modifier.size(21.dp))
                                    }
                                }
                                androidx.compose.material3.DropdownMenu(
                                    expanded = showAttachmentMenu,
                                    onDismissRequest = { showAttachmentMenu = false },
                                ) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("PDF or text file") },
                                        leadingIcon = { Icon(Icons.Default.AttachFile, null, tint = MuseAssistantText) },
                                        onClick = {
                                            hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                            showAttachmentMenu = false
                                            documentPicker.launch(arrayOf("application/pdf", "text/plain"))
                                        },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Image") },
                                        leadingIcon = { Icon(Icons.Default.ImageIcon, null, tint = MuseAssistantText) },
                                        onClick = { showAttachmentMenu = false; documentPicker.launch(arrayOf("image/*")) },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Audio clip") },
                                        leadingIcon = { Icon(Icons.Default.Mic, null, tint = MuseAssistantText) },
                                        onClick = { showAttachmentMenu = false; documentPicker.launch(arrayOf("audio/*")) },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Google services") },
                                        leadingIcon = { Icon(Icons.Default.Apps, null, tint = MuseAssistantText) },
                                        onClick = {
                                            showAttachmentMenu = false
                                            showGoogleServices = true
                                            hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                        },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Open private browser") },
                                        leadingIcon = { Icon(Icons.Default.Search, null, tint = MuseAssistantText) },
                                        onClick = {
                                            showAttachmentMenu = false
                                            service?.setBrowserVisible(true)
                                            hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                        },
                                    )
                                }
                            }

                            // Middle Text Input
                            BasicTextField(
                                value = draft,
                                onValueChange = { value -> hapticView.performElloTypingHaptic(draft, value); draft = value },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                textStyle = TextStyle(color = Color(0xFFF8F6FF), fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(ElloPalette.Accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP); focusManager.clearFocus(); sendMessage() }),
                                maxLines = 4,
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (draft.isEmpty()) Text("Message", color = MuseMuted, fontSize = 15.sp)
                                        inner()
                                    }
                                },
                            )

                            // Frontend-only message preview action.
                            Surface(
                                onClick = { hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP); focusManager.clearFocus(); sendMessage() },
                                enabled = (draft.isNotBlank() || pendingAttachments.isNotEmpty()) && !attachmentLoading,
                                shape = CircleShape,
                                color = if ((draft.isNotBlank() || pendingAttachments.isNotEmpty()) && !attachmentLoading) MuseAccent else MuseBorder,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        "Send",
                                        tint = if ((draft.isNotBlank() || pendingAttachments.isNotEmpty()) && !attachmentLoading) ElloPalette.AccentText else MuseMuted,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                            }
                        }
                    }
                    }
                    }
                    if (!imeVisible) {
                        PillNavigationBar(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            selectedIndex = selectedDestination,
                            onSelect = { index ->
                                if (index == selectedDestination) {
                                    hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                                selectedDestination = index
                            },
                        )
                    }
                }
                }
            },
        ) { insets ->
            val listState = rememberLazyListState()
            val latestMessageCount by rememberUpdatedState(messages.size)
            val latestTextLength by rememberUpdatedState(messages.lastOrNull()?.body?.length ?: 0)
            LaunchedEffect(listState) {
                snapshotFlow { latestMessageCount }
                    .distinctUntilChanged()
                    .collect { count ->
                        if (count > 0) listState.animateScrollToItem(count - 1)
                    }
            }
            LaunchedEffect(listState) {
                snapshotFlow { latestTextLength }
                    .distinctUntilChanged()
                    .sample(120)
                    .collect {
                        val layout = listState.layoutInfo
                        val nearBottom = layout.totalItemsCount == 0 ||
                            layout.visibleItemsInfo.lastOrNull()?.index?.let { it >= layout.totalItemsCount - 2 } == true
                        val lastIndex = layout.totalItemsCount - 1
                        if (nearBottom && lastIndex >= 0) listState.scrollToItem(lastIndex)
                    }
            }
            if (isFirstRunWelcome) {
                OnboardingWelcomeScreen(
                    modifier = Modifier.fillMaxSize().padding(insets),
                    onPrivacy = { welcomeInfoSheet = WelcomeInfoSheet.Privacy },
                    onTerms = { welcomeInfoSheet = WelcomeInfoSheet.Terms },
                    onHaptic = { hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP) },
                    onNext = {
                        onboardingWelcomeVisible = false
                        onboardingChatVisible = true
                    },
                )
            } else if (onboardingCompleteVisible) {
                Box(
                    Modifier.fillMaxSize().padding(insets).background(ElloWelcomeBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Ello", color = MuseAssistantText, fontSize = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
                }
            } else if (onboardingChatVisible) {
                OnboardingIntroductionScreen(
                    modifier = Modifier.fillMaxSize().padding(insets),
                    onNameSaved = { savedName ->
                        onboardingDisplayName = savedName
                        onboardingChatVisible = false
                        onboardingNamingVisible = true
                    },
                )
            } else if (onboardingNamingVisible) {
                OnboardingNamingScreen(
                    modifier = Modifier.fillMaxSize().padding(insets),
                    userName = onboardingDisplayName,
                    onMascotNamed = {
                        onboardingNamingVisible = false
                        onboardingConnectVisible = true
                    },
                )
            } else if (onboardingConnectVisible) {
                OnboardingConnectServicesScreen(
                    modifier = Modifier.fillMaxSize().padding(insets),
                    gmailConnected = gmailConnected,
                    calendarConnected = calendarConnected,
                    tasksConnected = tasksConnected,
                    driveConnected = driveConnected,
                    sheetsConnected = sheetsConnected,
                    connectingService = if (googleConnectBusy) pendingGoogleService else null,
                    onConnectGmail = { connectGoogleService("Gmail") },
                    onConnectCalendar = { connectGoogleService("Google Calendar") },
                    onConnectTasks = { connectGoogleService("Google Tasks") },
                    onConnectDrive = { connectGoogleService("Google Drive") },
                    onConnectSheets = { connectGoogleService("Google Sheets") },
                    onContinue = { onboardingConnectVisible = false; onboardingPhoneVisible = true },
                    onSkip = { onboardingConnectVisible = false; onboardingPhoneVisible = true },
                )
            } else if (onboardingPhoneVisible) {
                OnboardingPhoneScreen(
                    modifier = Modifier.fillMaxSize().padding(insets),
                    initialPhone = onboardingPhone,
                    busy = onboardingPhoneSaving,
                    onContinue = ::finishOnboarding,
                )
            } else {
            Box(Modifier.fillMaxSize().padding(insets)) {
            Column(Modifier.fillMaxSize()) {
                if (selectedDestination == 4) {
                    ProfileScreen(
                        gmailConnected = gmailConnected,
                        calendarConnected = calendarConnected,
                        tasksConnected = tasksConnected,
                        driveConnected = driveConnected,
                        sheetsConnected = sheetsConnected,
                        activeGoal = null,
                        activeGoalStatus = null,
                        onProfileNameChanged = { onboardingDisplayName = it },
                        onManageServices = { showGoogleServices = true },
                    )
                } else if (selectedDestination == 0) {
                    FeedPage(Modifier.fillMaxSize())
                } else if (selectedDestination != 2) {
                    Box(
                        Modifier.fillMaxSize().background(MuseCanvas),
                        contentAlignment = Alignment.Center,
                    ) {
                        TypewriterScreenTitle(selectedDestination)
                    }
                } else {
                if (!isFirstRunWelcome) Text(
                    "Chat notes are saved on this phone · Profile sync uses Firebase",
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp),
                )

                if (messages.isEmpty()) {
                    WelcomePanel(
                        displayName = onboardingDisplayName,
                        onPrompt = { draft = it },
                        onHaptic = { hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP) },
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(messages, key = { item -> item.id }) { message ->
                            MessageRow(message)
                        }
                        if (browserState.url.isNotBlank()) {
                            item(key = "browser-page") {
                                Surface(
                                    onClick = { service?.setBrowserVisible(true); hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP) },
                                    color = MuseCanvas,
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, MuseBorder),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text("Ello’s browser", color = MuseAssistantText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                browserState.title.ifBlank { browserState.url },
                                                color = MuseMuted,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                            )
                                        }
                                        Text(if (browserState.loading) "View live page  ›" else "View page  ›", color = ElloWelcomeBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
            }
            }
        }
        }
    }

    if (showLogoutConfirmation) {
        ModalBottomSheet(
            onDismissRequest = { showLogoutConfirmation = false },
            sheetState = logoutSheetState,
            containerColor = MuseSurface,
            contentColor = MuseAssistantText,
            scrimColor = Color.Black.copy(alpha = 0.18f),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            tonalElevation = 0.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MuseMuted.copy(alpha = 0.55f)) },
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Start Ello over?", color = MuseAssistantText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "This clears your chats, name, and Google access from this phone, then returns to onboarding.",
                    color = MuseMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP); showLogoutConfirmation = false }) { Text("Cancel", color = MuseAssistantText) }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = {
                            hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                            showLogoutConfirmation = false
                            scope.launch {
                                runCatching {
                                    service?.clearLocalUserData()
                                    FirebaseOnboarding.signOut(context)
                                    context.getSharedPreferences("ello_user_profile", Context.MODE_PRIVATE).edit().clear().commit()
                                    context.getSharedPreferences("ello_profile_context", Context.MODE_PRIVATE).edit().clear().commit()
                                    context.getSharedPreferences("ello_onboarding", Context.MODE_PRIVATE).edit().clear().commit()
                                    context.getSharedPreferences("litert_chat_settings", Context.MODE_PRIVATE).edit().clear().commit()
                                    GoogleApiAccess.clearAll(context)
                                }.onSuccess {
                                    draft = ""
                                    threadSearch = ""
                                    pendingAttachments.clear()
                                    onboardingDisplayName = ""
                                    selectedDestination = 2
                                    onboardingWelcomeVisible = true
                                    onboardingChatVisible = false
                                    onboardingNamingVisible = false
                                    onboardingConnectVisible = false
                                    onboardingCompleteVisible = false
                                    googleConnectionsVersion++
                                    hapticView.performElloHaptic(HapticFeedbackConstants.CONFIRM)
                                    snackbarState.showSnackbar("Your profile and chats were cleared.")
                                }.onFailure { failure ->
                                    snackbarState.showSnackbar(failure.localizedMessage ?: "Could not clear Ello’s local data.")
                                }
                            }
                        },
                        color = ElloPalette.ErrorSurface,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("Logout", color = ElloPalette.Error, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { threadId ->
        val thread = runtime.threads.firstOrNull { it.id == threadId }
        ModalBottomSheet(
            onDismissRequest = { pendingDeleteId = null },
            sheetState = deleteSheetState,
            containerColor = MuseSurface,
            contentColor = MuseAssistantText,
            scrimColor = Color.Black.copy(alpha = 0.18f),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            tonalElevation = 0.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MuseMuted.copy(alpha = 0.55f)) },
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Delete this chat?", color = MuseAssistantText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("${thread?.title ?: "This chat"} will be removed from this phone.", color = MuseMuted, fontSize = 14.sp, lineHeight = 20.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP); pendingDeleteId = null }) { Text("Cancel", color = MuseAssistantText) }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = {
                            service?.deleteChat(threadId)
                            pendingDeleteId = null
                            hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                        },
                        color = ElloPalette.ErrorSurface,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("Delete", color = ElloPalette.Error, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                    }
                }
            }
        }
    }

    if (runtime.browserVisible) {
        ElloBrowserDialog(
            subtitle = "Private · on this phone",
            onDismiss = { service?.setBrowserVisible(false) },
            onHide = { service?.setBrowserVisible(false) },
        )
    }

    welcomeInfoSheet?.let { info ->
        val welcomeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { welcomeInfoSheet = null },
            sheetState = welcomeSheetState,
            containerColor = MuseSurface,
            contentColor = MuseAssistantText,
            scrimColor = Color.Black.copy(alpha = 0.18f),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            tonalElevation = 0.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MuseMuted.copy(alpha = 0.55f)) },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(info.title, color = MuseAssistantText, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text(info.body, color = MuseMuted, fontSize = 14.sp, lineHeight = 21.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { welcomeInfoSheet = null }) {
                        hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                        Text("Done", color = ElloWelcomeBlue)
                    }
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = settingsSheetState,
            containerColor = MuseSurface,
            contentColor = MuseAssistantText,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = MuseMuted.copy(alpha = 0.55f)) },
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("App settings", color = MuseAssistantText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Your profile syncs with Firebase. Chat notes and attachments stay on this phone.",
                    color = MuseMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Surface(
                    onClick = { showSettings = false; showModelManager = true },
                    color = MuseCanvas,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
                        Text("Cloud AI · Gemini", color = MuseAssistantText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Ello sends chat turns to Gemini through the authenticated Firebase backend.", color = MuseMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Surface(
                    onClick = { skills = service?.getSkills().orEmpty(); mcpServerUrl = service?.getMcpServerUrl().orEmpty(); showSettings = false; showSkillsManager = true },
                    color = MuseCanvas,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
                        Text("Agent skills", color = MuseAssistantText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("${skills.count { it.selected }} enabled · add SKILL.md instructions", color = MuseMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Surface(
                    onClick = { hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP); showSettings = false; showGoogleServices = true },
                    color = MuseCanvas,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Apps, null, tint = MuseAssistantText, modifier = Modifier.size(19.dp))
                        Text("Connected services", color = MuseAssistantText, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        Text("›", color = MuseMuted, fontSize = 22.sp)
                    }
                }
                Surface(
                    color = MuseCanvas,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("API Anything shared catalog", color = MuseAssistantText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "When enabled, site-search words go to API Anything. Ello pulls only public HTTPS read specs and runs them on this phone. Personal profile values and site logins are not sent to the catalog.",
                                color = MuseMuted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Switch(
                            checked = sharedApiCatalogEnabled,
                            onCheckedChange = { enabled ->
                                sharedApiCatalogEnabled = enabled
                                context.getSharedPreferences("api_anything_settings", Context.MODE_PRIVATE)
                                    .edit().putBoolean("shared_catalog_enabled", enabled).apply()
                                hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                            },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP); showSettings = false }) {
                        Text("Done", color = MuseAssistantText)
                    }
                }
            }
        }
    }
    if (showModelManager) {
        ModalBottomSheet(
            onDismissRequest = { showModelManager = false },
            containerColor = MuseSurface,
            contentColor = MuseAssistantText,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = MuseMuted.copy(alpha = 0.55f)) },
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Gemini Cloud AI", color = MuseAssistantText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Chat and agent planning run on Gemini 3.8 Flash through Ello’s Firebase Function. No local model is downloaded or loaded for chat.", color = MuseMuted, fontSize = 13.sp, lineHeight = 19.sp)
                Text("Cloud access needs the Gemini API key configured as the GEMINI_API_KEY Firebase Function secret and an internet connection.", color = MuseMuted, fontSize = 12.sp, lineHeight = 17.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showModelManager = false }) { Text("Done", color = MuseAssistantText) }
                }
            }
        }
    }
    if (showSkillsManager) {
        ModalBottomSheet(
            onDismissRequest = { showSkillsManager = false },
            containerColor = MuseSurface,
            contentColor = MuseAssistantText,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = MuseMuted.copy(alpha = 0.55f)) },
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Agent skills", color = MuseAssistantText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Ello shares enabled skill names with Gemini, then loads a skill’s full instructions only when the model selects it.", color = MuseMuted, fontSize = 12.sp, lineHeight = 18.sp)
                skills.forEach { skill ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            val selected = !skill.selected
                            service?.setSkillSelected(skill.name, selected)
                            skills = skills.map { if (it.name == skill.name) it.copy(selected = selected) else it }
                        }.padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = skill.selected, onCheckedChange = { selected ->
                            service?.setSkillSelected(skill.name, selected)
                            skills = skills.map { if (it.name == skill.name) it.copy(selected = selected) else it }
                        })
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(skill.name, color = MuseAssistantText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(skill.description, color = MuseMuted, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { skillPicker.launch(arrayOf("text/markdown", "text/plain")) }) { Text("Import SKILL.md", color = MuseAssistantText) }
                    TextButton(onClick = { showSkillUrlDialog = true }) { Text("Add from URL", color = MuseAssistantText) }
                }
                TextButton(onClick = { skillFolderPicker.launch(null) }) { Text("Import skill folder (SKILL.md + scripts)", color = MuseAssistantText) }
                HorizontalDivider(color = MuseBorder)
                Text("MCP server", color = MuseAssistantText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Connect up to eight HTTPS Streamable HTTP endpoints, one URL per line. Ello asks you to approve every tool call.", color = MuseMuted, fontSize = 11.sp, lineHeight = 16.sp)
                OutlinedTextField(value = mcpServerUrl, onValueChange = { mcpServerUrl = it }, label = { Text("MCP server URLs") }, minLines = 1, maxLines = 4, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        mcpConnectionBusy = true
                        scope.launch {
                            runCatching { service?.configureMcpServer(mcpServerUrl) ?: error("Chat service is not ready yet.") }
                                .onSuccess { tools -> snackbarState.showSnackbar("MCP connected · ${tools.size} tools available") }
                                .onFailure { snackbarState.showSnackbar(it.localizedMessage ?: "Could not connect to MCP server.") }
                            mcpConnectionBusy = false
                        }
                    },
                    enabled = !mcpConnectionBusy && mcpServerUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (mcpConnectionBusy) "Connecting…" else "Connect MCP server") }
            }
        }
    }
    if (showSkillUrlDialog) {
        AlertDialog(
            onDismissRequest = { showSkillUrlDialog = false },
            title = { Text("Add skill from URL", color = MuseAssistantText) },
            text = {
                OutlinedTextField(
                    value = skillUrl,
                    onValueChange = { skillUrl = it },
                    label = { Text("HTTPS skill folder URL") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val baseUrl = skillUrl.trim().removeSuffix("/SKILL.md").trimEnd('/')
                    showSkillUrlDialog = false
                    scope.launch {
                        runCatching {
                            require(baseUrl.startsWith("https://", ignoreCase = true)) { "Use a secure HTTPS skill URL." }
                            val markdown = withContext(Dispatchers.IO) {
                                java.net.URL("$baseUrl/SKILL.md").openStream().bufferedReader().use { it.readText() }
                            }
                            service?.addRemoteSkillMarkdown(markdown, baseUrl) ?: error("Chat service is not ready yet.")
                            skills = service?.getSkills().orEmpty()
                            skillUrl = ""
                            snackbarState.showSnackbar("Skill added.")
                        }.onFailure { snackbarState.showSnackbar(it.localizedMessage ?: "Could not add that skill.") }
                    }
                }) { Text("Add", color = MuseAssistantText) }
            },
            dismissButton = { TextButton(onClick = { showSkillUrlDialog = false }) { Text("Cancel", color = MuseMuted) } },
            containerColor = MuseSurface,
        )
    }
    pendingMcpApproval?.let { request ->
        AlertDialog(
            onDismissRequest = { request.answer.complete(false); pendingMcpApproval = null },
            title = { Text("Review before continuing", color = MuseAssistantText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(request.toolName, color = MuseAssistantText, fontWeight = FontWeight.SemiBold)
                    Text(request.input.take(1200), color = MuseMuted, fontSize = 12.sp, lineHeight = 17.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { request.answer.complete(true); pendingMcpApproval = null }) { Text("Allow once", color = MuseAssistantText) }
            },
            dismissButton = {
                TextButton(onClick = { request.answer.complete(false); pendingMcpApproval = null }) { Text("Deny", color = MuseMuted) }
            },
            containerColor = MuseSurface,
        )
    }
    pendingAndroidPermission?.let { request ->
        AlertDialog(
            onDismissRequest = { request.answer.complete(false); pendingAndroidPermission = null },
            title = { Text("Ello needs permission", color = MuseAssistantText) },
            text = { Text(request.explanation, color = MuseMuted, fontSize = 13.sp, lineHeight = 18.sp) },
            confirmButton = {
                TextButton(onClick = { agentPermissionLauncher.launch(request.permission) }) { Text("Continue", color = MuseAssistantText) }
            },
            dismissButton = {
                TextButton(onClick = { request.answer.complete(false); pendingAndroidPermission = null }) { Text("Deny", color = MuseMuted) }
            },
            containerColor = MuseSurface,
        )
    }
    pendingSkillSecret?.let { request ->
        AlertDialog(
            onDismissRequest = { request.answer.complete(""); pendingSkillSecret = null; skillSecretValue = "" },
            title = { Text("Skill needs a secret", color = MuseAssistantText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${request.skillName}: ${request.description}", color = MuseMuted, fontSize = 13.sp, lineHeight = 18.sp)
                    OutlinedTextField(
                        value = skillSecretValue,
                        onValueChange = { skillSecretValue = it },
                        label = { Text("API key or token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Text("Ello encrypts this secret using Android Keystore before saving it on this device.", color = MuseMuted, fontSize = 11.sp, lineHeight = 15.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { request.answer.complete(skillSecretValue); pendingSkillSecret = null; skillSecretValue = "" }) { Text("Save and continue", color = MuseAssistantText) }
            },
            dismissButton = {
                TextButton(onClick = { request.answer.complete(""); pendingSkillSecret = null; skillSecretValue = "" }) { Text("Cancel", color = MuseMuted) }
            },
            containerColor = MuseSurface,
        )
    }
    if (showGoogleServices) {
        val gmailConnected = remember(googleConnectionsVersion) { GoogleApiAccess.gmailConnected(context) }
        val calendarConnected = remember(googleConnectionsVersion) { GoogleApiAccess.calendarConnected(context) }
        val tasksConnected = remember(googleConnectionsVersion) { GoogleApiAccess.tasksConnected(context) }
        val driveConnected = GoogleApiAccess.driveConnected(context)
        val sheetsConnected = remember(googleConnectionsVersion) { GoogleApiAccess.sheetsConnected(context) }
        GoogleServicesSheet(
            gmailConnected = gmailConnected,
            calendarConnected = calendarConnected,
            tasksConnected = tasksConnected,
            driveConnected = driveConnected,
            sheetsConnected = sheetsConnected,
            connecting = googleConnectBusy,
            onDismiss = { showGoogleServices = false },
            onConnectGmail = { connectGoogleService("Gmail") },
            onConnectCalendar = { connectGoogleService("Google Calendar") },
            onConnectTasks = { connectGoogleService("Google Tasks") },
            onConnectDrive = { connectGoogleService("Google Drive") },
            onConnectSheets = { connectGoogleService("Google Sheets") },
        )
    }
}

private fun saveGoogleAuthorization(context: Context, serviceName: String, result: com.google.android.gms.auth.api.identity.AuthorizationResult) {
    val token = result.accessToken?.takeIf { it.isNotBlank() }
        ?: error("Google did not return an access token.")
    val scopes = result.grantedScopes.toSet()
    when (serviceName) {
        "Gmail" -> GoogleApiAccess.saveGmailToken(context, token, scopes)
        "Google Calendar" -> GoogleApiAccess.saveCalendarToken(context, token, scopes)
        "Google Tasks" -> GoogleApiAccess.saveTasksToken(context, token, scopes)
        "Google Drive" -> GoogleApiAccess.saveDriveToken(context, token, scopes)
        "Google Sheets" -> GoogleApiAccess.saveSheetsToken(context, token, scopes)
        else -> error("Unknown Google service: $serviceName")
    }
    if (serviceName == "Google Drive") {
        val pickedIds = result.getTokenResponseParams()?.getString("picked_file_ids")
            .orEmpty().split(',').map(String::trim).filter(String::isNotEmpty).toSet()
        GoogleApiAccess.savePickedDriveFiles(context, pickedIds)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoogleServicesSheet(
    gmailConnected: Boolean,
    calendarConnected: Boolean,
    tasksConnected: Boolean,
    driveConnected: Boolean,
    sheetsConnected: Boolean,
    connecting: Boolean,
    onDismiss: () -> Unit,
    onConnectGmail: () -> Unit,
    onConnectCalendar: () -> Unit,
    onConnectTasks: () -> Unit,
    onConnectDrive: () -> Unit,
    onConnectSheets: () -> Unit,
) {
    val hapticView = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MuseSurface,
        contentColor = MuseAssistantText,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = MuseMuted.copy(alpha = 0.55f)) },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(color = ElloPalette.AccentSurface, shape = CircleShape) {
                Text("GOOGLE SERVICES", color = MuseAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
            }
            Text("Make room for\nmore helpfulness.", color = MuseAssistantText, fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Connect the tools you want Ello to use. Google will show exactly what you’re allowing before access is granted.",
                color = MuseMuted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
            GoogleServiceRow(
                title = "Gmail",
                detail = "Search, read, send, organize, and manage Gmail rules and signatures when you ask Ello.",
                connected = gmailConnected,
                connecting = connecting,
                onConnect = onConnectGmail,
            )
            GoogleServiceRow(
                title = "Google Calendar",
                detail = "Find upcoming events and create events in your primary calendar.",
                connected = calendarConnected,
                connecting = connecting,
                onConnect = onConnectCalendar,
            )
            GoogleServiceRow("Google Tasks", "Read and manage your tasks when you ask Ello.", tasksConnected, connecting, onConnectTasks)
            GoogleServiceRow("Google Drive", "Search and read files in your Drive when you ask Ello.", driveConnected, connecting, onConnectDrive)
            GoogleServiceRow("Google Sheets", "Create spreadsheets or work with sheets selected in Drive.", sheetsConnected, connecting, onConnectSheets)
            Text(
                "Google access tokens are encrypted on this phone with Android Keystore. Ello saves granted-scope metadata to your Firebase profile, never the tokens.",
                color = MuseMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP); onDismiss() }) { Text("Done", color = MuseAccent) }
            }
        }
    }
}

@Composable
private fun GoogleServiceRow(
    title: String,
    detail: String,
    connected: Boolean,
    connecting: Boolean,
    onConnect: () -> Unit,
) {
    val hapticView = LocalView.current
    Surface(
        color = MuseSurface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MuseBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(if (title == "Gmail") Color(0xFFFFF0EE) else ElloPalette.AccentSurface), contentAlignment = Alignment.Center) {
                    Icon(if (title == "Gmail") Icons.Default.Email else Icons.Default.CalendarMonth, null, tint = if (title == "Gmail") Color(0xFFEA4335) else Color(0xFF4285F4), modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, color = MuseAssistantText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
                    Text(detail, color = MuseMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(start = 12.dp, top = 3.dp))
                }
                if (connected) {
                    Text("Connected", color = MuseAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Button(
                onClick = { hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP); onConnect() },
                enabled = !connecting,
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MuseAccent, contentColor = ElloPalette.AccentText),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (connecting) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = ElloPalette.AccentText, strokeWidth = 2.dp)
                    Text("Connecting…", modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text(when { title == "Google Drive" -> "Choose Drive files"; connected -> "Refresh access"; else -> "Connect $title" }, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ElloBrowserDialog(subtitle: String, onDismiss: () -> Unit, onHide: () -> Unit) {
    val browser by LocalBrowserSession.browserState.collectAsState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var address by rememberSaveable { mutableStateOf("") }
    var addressFocused by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(browser.url) {
        if (!addressFocused && browser.url.isNotBlank()) address = browser.url
    }

    fun openAddress() {
        val requestedAddress = address.trim()
        if (requestedAddress.isBlank()) return
        val hasScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(requestedAddress)
        val target = if (!hasScheme && (requestedAddress.contains(Regex("\\s")) || !requestedAddress.contains('.'))) {
            "https://www.google.com/search?q=${Uri.encode(requestedAddress)}"
        } else requestedAddress
        keyboard?.hide()
        focusManager.clearFocus()
        scope.launch {
            runCatching { LocalBrowserSession.navigate(target) }
                .onFailure { LocalBrowserSession.showLoadError(it.localizedMessage ?: "Could not open this page.") }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MuseCanvas) {
            Column(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Browser", color = MuseAssistantText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(subtitle, color = MuseMuted, fontSize = 12.sp, maxLines = 1)
                    }
                    TextButton(onClick = onHide) { Text("Hide", color = ElloWelcomeBlue, fontWeight = FontWeight.SemiBold) }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(onClick = { LocalBrowserSession.goBack() }, enabled = browser.canGoBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = if (browser.canGoBack) MuseAssistantText else MuseMuted, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { LocalBrowserSession.goForward() }, enabled = browser.canGoForward) {
                        Icon(Icons.Default.ArrowForward, "Forward", tint = if (browser.canGoForward) MuseAssistantText else MuseMuted, modifier = Modifier.size(20.dp))
                    }
                    Surface(
                        modifier = Modifier.weight(1f).height(48.dp),
                        color = MuseSurface,
                        shape = RoundedCornerShape(15.dp),
                        border = BorderStroke(1.dp, MuseBorder),
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, "Search", tint = MuseMuted, modifier = Modifier.size(18.dp).padding(end = 1.dp))
                            BasicTextField(
                                value = address,
                                onValueChange = { address = it },
                                singleLine = true,
                                textStyle = TextStyle(color = MuseAssistantText, fontSize = 13.sp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { openAddress() }),
                                modifier = Modifier.weight(1f).onFocusChanged { addressFocused = it.isFocused },
                                decorationBox = { inner ->
                                    if (address.isBlank()) Text("Search or enter website", color = MuseMuted, fontSize = 13.sp, maxLines = 1)
                                    inner()
                                },
                            )
                            TextButton(onClick = { openAddress() }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                                Text("Go", color = ElloWelcomeBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    IconButton(
                        onClick = { if (browser.loading) LocalBrowserSession.stop() else LocalBrowserSession.reload() },
                        enabled = browser.url.isNotBlank(),
                    ) {
                        Icon(
                            if (browser.loading) Icons.Default.Stop else Icons.Default.Refresh,
                            if (browser.loading) "Stop loading" else "Reload",
                            tint = MuseAssistantText,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }

                if (browser.loading) {
                    LinearProgressIndicator(
                        progress = { browser.progress.coerceIn(0, 100) / 100f },
                        color = ElloWelcomeBlue,
                        trackColor = MuseBorder,
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                    )
                } else {
                    Spacer(Modifier.fillMaxWidth().height(3.dp).background(MuseBorder))
                }

                browser.error?.let { message ->
                    Row(
                        Modifier.fillMaxWidth().background(ElloPalette.ErrorSurface).padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(message, color = ElloPalette.Error, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { LocalBrowserSession.reload() }) {
                            Text("Retry", color = ElloPalette.Error, fontSize = 12.sp)
                        }
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f).background(MuseCanvas),
                    factory = { ctx -> LocalBrowserSession.create(ctx) },
                    update = { LocalBrowserSession.attach(it) },
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Pages and sign-ins stay on this phone", color = MuseMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("Ello pauses before sending", color = MuseMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun OnboardingWelcomeScreen(
    modifier: Modifier = Modifier,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    onHaptic: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier
            .background(ElloWelcomeBackground)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val mascotSize = minOf(320.dp, maxWidth)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ElloAnimatedMascot(
                    rawResId = R.raw.ello_waving,
                    modifier = Modifier.size(mascotSize).aspectRatio(1f).clip(RoundedCornerShape(112.dp)).background(ElloWelcomeBackground),
                )
                Text(
                    "ELLO · FIREBASE FRONTEND",
                    color = ElloPalette.Accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.2.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "A little more ease,\nin everything you do.",
                    color = ElloWelcomeInk,
                    fontSize = 27.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "A simple place for your Ello profile and chats.",
                    color = MuseMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
        Button(
            onClick = { onHaptic(); onNext() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = ElloPalette.Accent, contentColor = ElloPalette.AccentText),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp),
        ) {
            Text("Start Ello", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "A product of the ZEYPHRA family",
            color = ElloWelcomeInk.copy(alpha = 0.9f),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
        )
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        ) {
            TextButton(onClick = { onHaptic(); onPrivacy() }) {
                Text("Privacy policy", color = ElloWelcomeInk.copy(alpha = 0.82f), fontSize = 12.sp)
            }
            Text("·", color = ElloWelcomeInk.copy(alpha = 0.6f))
            TextButton(onClick = { onHaptic(); onTerms() }) {
                Text("Terms", color = ElloWelcomeInk.copy(alpha = 0.82f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun WelcomePanel(
    displayName: String,
    onPrompt: (String) -> Unit,
    onHaptic: () -> Unit,
) {
    val cleanDisplayName = displayName.trim().takeIf { it.isNotEmpty() }
    Column(
        Modifier.fillMaxSize().background(MuseCanvas).padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (cleanDisplayName == null) "What would you like to save?" else "Hi, $cleanDisplayName. What would you like to save?",
                    color = MuseAssistantText,
                    fontSize = 26.sp,
                    lineHeight = 33.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Write a note or draft and keep it in your chat.",
                    color = MuseMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 22.dp),
                )
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    listOf("A note about my day", "An idea to remember", "A message draft").forEach { prompt ->
                        Surface(
                            onClick = { onHaptic(); onPrompt(prompt) },
                            color = MuseSurface,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MuseBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(prompt, color = MuseAssistantText, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageRow(line: ChatLine, isStreaming: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (line.user) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            horizontalAlignment = if (line.user) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 330.dp).then(
                if (line.user) Modifier else Modifier.weight(1f, fill = false),
            ),
        ) {
            line.attachments.forEach { attachment ->
                MessageAttachmentCard(attachment)
            }
            if (line.user) {
                Surface(
                    color = MuseAssistantBubble,
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(
                        line.body,
                        color = MuseUserText,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 17.dp, vertical = 12.dp),
                    )
                }
            } else {
                if (line.skillWebviewUrl.isNotBlank()) {
                    SkillResultWebView(line.skillWebviewUrl, line.skillWebviewAspect, line.skillWebviewIframe)
                }
                if (line.taskSteps.isNotEmpty()) {
                    Surface(
                        color = MuseSurface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MuseBorder),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(line.taskTitle.ifBlank { "Task plan" }, color = MuseAssistantText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("${line.completedTaskSteps.size.coerceAtMost(line.taskSteps.size)}/${line.taskSteps.size}", color = MuseMuted, fontSize = 11.sp)
                            }
                            line.taskSteps.forEachIndexed { index, step ->
                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(if (index in line.completedTaskSteps) "✓" else "○", color = if (index in line.completedTaskSteps) MuseAccent else MuseMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(step, color = if (index in line.completedTaskSteps) MuseMuted else MuseAssistantText, fontSize = 12.sp, lineHeight = 17.sp)
                                }
                            }
                        }
                    }
                }
                val context = LocalContext.current
                val annotatedBody = if (isStreaming) {
                    buildAnnotatedString { append(line.body.ifBlank { " " }) }
                } else remember(line.body) {
                    val body = line.body.ifBlank { " " }
                    val matches = Regex("(?:https?://|mailto:)[^\\s)\\]}>]+", RegexOption.IGNORE_CASE).findAll(body).toList()
                    buildAnnotatedString {
                        var cursor = 0
                        matches.forEach { match ->
                            append(body.substring(cursor, match.range.first))
                            val candidate = match.value
                            val url = candidate.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')
                            if (url.isNotEmpty()) {
                                withLink(
                                    LinkAnnotation.Url(
                                        url,
                                        TextLinkStyles(SpanStyle(color = MuseAccent, textDecoration = TextDecoration.Underline)),
                                    ),
                                ) { append(if (url.startsWith("mailto:", true)) "✉ " + url.removePrefix("mailto:").substringBefore('?') else url) }
                                append(candidate.substring(url.length))
                            } else append(candidate)
                            cursor = match.range.last + 1
                        }
                        append(body.substring(cursor))
                    }
                }
                Text(
                    text = annotatedBody,
                    style = TextStyle(color = MuseAssistantText, fontSize = 15.5.sp, lineHeight = 23.sp),
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 3.dp),
                )
                val mailtoMatches = remember(line.body, isStreaming) {
                    if (isStreaming) emptyList()
                    else {
                        val mailtoRegex = Regex("mailto:[^\\s)\\]}>]+", RegexOption.IGNORE_CASE)
                        mailtoRegex.findAll(line.body).map { it.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}') }.distinct().take(3).toList()
                    }
                }
                if (mailtoMatches.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        mailtoMatches.forEach { mailUri ->
                            val cleanTarget = mailUri.removePrefix("mailto:").substringBefore('?')
                            Surface(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(mailUri))
                                        context.startActivity(intent)
                                    }
                                },
                                color = ElloPalette.AccentSurface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, ElloPalette.Accent.copy(alpha = 0.35f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Default.Email, contentDescription = null, tint = MuseAccent, modifier = Modifier.size(16.dp))
                                    Text("Open Email Inquiry ($cleanTarget)", color = MuseAccent, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                if (line.timing.isNotBlank()) {
                    Text(
                        line.timing,
                        color = MuseMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TypewriterScreenTitle(screen: Int) {
    val title = when (screen) {
        0 -> "Feed"
        1 -> "Ideas"
        3 -> "Services"
        4 -> "Profile"
        else -> "Chat"
    }
    Text(
        text = title,
        color = MuseAssistantText,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun PillNavigationBar(
    modifier: Modifier = Modifier,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val destinations = listOf(
        "Feed" to com.composables.icons.lucide.R.drawable.lucide_ic_newspaper,
        "Ideas" to com.composables.icons.lucide.R.drawable.lucide_ic_lightbulb,
        "Chat" to com.composables.icons.lucide.R.drawable.lucide_ic_message_circle,
        "Services" to com.composables.icons.lucide.R.drawable.lucide_ic_grid_2x2,
        "Profile" to com.composables.icons.lucide.R.drawable.lucide_ic_user,
    )
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 360.dp).height(64.dp),
            shape = CircleShape,
            color = MuseSurface,
            border = BorderStroke(1.dp, MuseBorder),
            shadowElevation = 8.dp,
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().height(32.dp)
                        .padding(horizontal = 2.dp, vertical = 1.dp).clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(MuseSurfaceRaised, MuseSurface.copy(alpha = 0.42f)))),
                )
                Box(
                    Modifier.fillMaxSize().padding(1.dp).border(
                        BorderStroke(1.dp, Brush.verticalGradient(listOf(MuseBorder, MuseBorder.copy(alpha = 0.38f)))),
                        CircleShape,
                    ),
                )
                BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp)) {
                    val cellWidth = maxWidth / destinations.size
                    val sliderOffset by animateDpAsState(
                        targetValue = cellWidth * selectedIndex + 1.dp,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
                        label = "glass tab slider",
                    )
                    Box(
                        Modifier.offset(x = sliderOffset).align(Alignment.CenterStart)
                            .width(cellWidth - 2.dp).height(50.dp).clip(RoundedCornerShape(25.dp))
                            .background(Brush.verticalGradient(listOf(ElloPalette.AccentSurface, ElloPalette.SurfaceRaised)))
                            .border(BorderStroke(1.dp, MuseBorder), RoundedCornerShape(25.dp)),
                    )
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        destinations.forEachIndexed { index, destination ->
                            val (label, iconRes) = destination
                            val selected = selectedIndex == index
                            val iconTint by animateColorAsState(
                                targetValue = if (selected) MuseAccent else MuseMuted,
                                animationSpec = spring(stiffness = 500f),
                                label = "$label tab tint",
                            )
                            val labelAlpha by animateFloatAsState(
                                targetValue = if (selected) 1f else 0f,
                                animationSpec = spring(stiffness = 500f),
                                label = "$label tab label",
                            )
                            Box(
                                Modifier.weight(1f).fillMaxHeight().clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onSelect(index) },
                                ),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Icon(painterResource(iconRes), contentDescription = label, tint = iconTint, modifier = Modifier.size(24.dp))
                                        Spacer(Modifier.height(1.dp))
                                        Text(label, modifier = Modifier.alpha(labelAlpha), color = iconTint, fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ElloBadge(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MuseSurfaceRaised, shape = CircleShape, border = BorderStroke(1.dp, MuseBorder)) {
        Box(contentAlignment = Alignment.Center) {
            Text("E", color = MuseAssistantText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun ElloAnimatedMascot(
    rawResId: Int,
    modifier: Modifier = Modifier,
    contentDescription: String = "Ello mascot",
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var currentSurface by remember { mutableStateOf<AndroidSurface?>(null) }
    var hasVideoFrame by remember(rawResId) { mutableStateOf(false) }
    val mediaPlayer = remember {
        MediaPlayer().apply {
            setVolume(0f, 0f)
        }
    }

    LaunchedEffect(rawResId, currentSurface) {
        val surface = currentSurface ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                mediaPlayer.reset()
                // reset() clears playback configuration, so re-enable looping for every setup.
                mediaPlayer.isLooping = true
                context.resources.openRawResourceFd(rawResId)?.use { afd ->
                    mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                mediaPlayer.setSurface(surface)
                mediaPlayer.prepare()
                mediaPlayer.start()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                mediaPlayer.stop()
                mediaPlayer.reset()
                mediaPlayer.release()
            }
            currentSurface?.release()
        }
    }

    Box(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ello_chilling_transparent_still),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().alpha(if (hasVideoFrame) 0f else 1f),
        )
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    // The mascot clips have an opaque graphite background, so allowing a
                    // transparent texture only adds an unnecessary GPU composition pass.
                    isOpaque = true
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                            currentSurface?.release()
                            currentSurface = AndroidSurface(st)
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            currentSurface?.release()
                            currentSurface = null
                            hasVideoFrame = false
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                            if (!hasVideoFrame) hasVideoFrame = true
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize().alpha(if (hasVideoFrame) 1f else 0f),
        )
    }
}

@Composable
private fun PendingAttachmentCard(attachment: ChatAttachment, onRemove: () -> Unit) {
    val hapticView = LocalView.current
    Surface(
        color = MuseSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MuseBorder),
        shadowElevation = 0.5.dp,
        modifier = Modifier.widthIn(max = 245.dp),
    ) {
        Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            FileTypeBadge(attachment.mimeType)
            Column(Modifier.weight(1f, fill = false).padding(start = 8.dp)) {
                Text(attachment.name, color = MuseAssistantText, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text("${fileSize(attachment.sizeBytes)} · ready", color = MuseMuted, fontSize = 9.5.sp)
            }
            IconButton(onClick = { hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP); onRemove() }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "Remove ${attachment.name}", tint = MuseMuted, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun SkillResultWebView(url: String, aspectRatio: Float, iframe: Boolean) {
    val context = LocalContext.current
    val ratio = aspectRatio.coerceIn(0.5f, 3f)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        val height = (maxWidth / ratio).coerceIn(150.dp, 520.dp)
        AndroidView(
            factory = { viewContext ->
                WebView(viewContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): WebResourceResponse? {
                            val requested = request?.url ?: return null
                            if (!requested.host.equals("ello.local", true)) return null
                            val assetPath = URLDecoder.decode(requested.path.orEmpty().trimStart('/'), "UTF-8")
                            if (!assetPath.startsWith("skills/")) return WebResourceResponse("text/plain", "UTF-8", 404, "Not found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                            val bytes = runCatching {
                                if (assetPath.contains("/agent-skills/")) File(context.filesDir, assetPath.removePrefix("skills/")).readBytes()
                                else context.assets.open(assetPath).use { it.readBytes() }
                            }.getOrNull() ?: return WebResourceResponse("text/plain", "UTF-8", 404, "Not found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                            val mime = when {
                                assetPath.endsWith(".html", true) -> "text/html"
                                assetPath.endsWith(".js", true) -> "application/javascript"
                                assetPath.endsWith(".css", true) -> "text/css"
                                assetPath.endsWith(".svg", true) -> "image/svg+xml"
                                assetPath.endsWith(".png", true) -> "image/png"
                                assetPath.endsWith(".jpg", true) || assetPath.endsWith(".jpeg", true) -> "image/jpeg"
                                else -> "application/octet-stream"
                            }
                            return WebResourceResponse(mime, "UTF-8", ByteArrayInputStream(bytes))
                        }
                    }
                }
            },
            update = { webView ->
                val previousUrl = webView.tag as? String
                if (previousUrl != url) {
                    webView.tag = url
                    if (iframe) {
                        val safeUrl = org.json.JSONObject.quote(url)
                        webView.loadDataWithBaseURL("https://ello.local/", "<html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='margin:0'><iframe src=$safeUrl style='border:0;width:100%;height:100vh' allow='fullscreen'></iframe></body></html>", "text/html", "UTF-8", null)
                    } else webView.loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(18.dp)),
        )
    }
}

@Composable
private fun MessageAttachmentCard(attachment: ChatAttachment) {
    if (attachment.mimeType.startsWith("image/") && attachment.localPath.isNotBlank()) {
        val bitmap = remember(attachment.localPath) { BitmapFactory.decodeFile(attachment.localPath)?.asImageBitmap() }
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = attachment.name,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(18.dp)),
            )
        }
        return
    }
    val isPdf = attachment.mimeType == "application/pdf"
    Surface(
        color = MuseSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MuseBorder),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column {
            // Compact document preview card
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MuseSurfaceRaised)
                    .padding(12.dp)
            ) {
                Surface(
                    color = MuseSurfaceRaised,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MuseBorder),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(16.dp).clip(CircleShape).background(MuseAccent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("L", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                if (isPdf) "LINCOLN MIDDLE SCHOOL · GRADE 7 SCIENCE" else "DOCUMENT PREVIEW",
                                color = MuseMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                        Text(
                            text = attachment.name.removeSuffix(".pdf").removeSuffix(".txt").replace("_", " "),
                            color = MuseAssistantText,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 8.dp),
                        ) {
                            Surface(color = ElloPalette.AccentSurface, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    "Monterey Bay Aquarium",
                                    color = MuseAccent,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Surface(color = MuseSurfaceRaised, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    "Fri, Oct 23 2026",
                                    color = MuseMuted,
                                    fontSize = 9.5.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Surface(color = MuseSurfaceRaised, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    "Depart 8:15 AM",
                                    color = MuseMuted,
                                    fontSize = 9.5.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        val previewLines = attachment.extractedText.lines()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .take(3)
                            .joinToString(" ")
                        Text(
                            if (previewLines.isNotBlank()) previewLines
                            else "I give permission for my child, named below, to attend the field trip described above under school supervision for the full duration of the trip.",
                            color = MuseMuted,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            maxLines = 3,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(MuseBorder))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("STUDENT: Verified", color = MuseMuted, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold)
                                Text("STATUS: FILLED", color = MuseAccent, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // File Info Bottom Row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FileTypeBadge(attachment.mimeType)
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text(attachment.name, color = MuseAssistantText, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(if (isPdf) "PDF" else "Text file", color = MuseMuted, fontSize = 11.5.sp)
                }
                Text("···", color = MuseMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 4.dp))
            }
        }
    }
}

@Composable
private fun FileTypeBadge(mimeType: String) {
    val isPdf = mimeType == "application/pdf"
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPdf) MusePdfRed else MuseAccent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (isPdf) "PDF" else "TXT",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

private fun fileSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hapticView = LocalView.current
    Surface(onClick = { hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP); onClick() }, enabled = label !in setOf("Starting…", "Downloading…"), color = MuseAccent, shape = RoundedCornerShape(26.dp), modifier = modifier) {
        Text(label, color = ElloPalette.AccentText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 15.dp))
    }
}

private fun Double.format(digits: Int): String = "%1.${digits}f".format(this)

