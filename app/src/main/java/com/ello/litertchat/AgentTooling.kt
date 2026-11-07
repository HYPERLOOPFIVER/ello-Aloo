package com.ello.litertchat

import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.provider.CalendarContract
import android.util.Log
import android.content.pm.PackageManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import java.util.UUID
import androidx.core.content.ContextCompat
import android.Manifest
import android.os.Build

internal data class McpApprovalRequest(
    val toolName: String,
    val input: String,
    val answer: CompletableDeferred<Boolean> = CompletableDeferred(),
)

internal data class AndroidPermissionRequest(
    val permission: String,
    val explanation: String,
    val answer: CompletableDeferred<Boolean> = CompletableDeferred(),
)

internal data class SkillSecretRequest(
    val skillName: String,
    val description: String,
    val answer: CompletableDeferred<String> = CompletableDeferred(),
)

/** Gallery-style Streamable HTTP MCP connection with per-call user approval. */
internal class ElloMcpConnection(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("ello_agent_mcp", Context.MODE_PRIVATE)
    private val httpClient = HttpClient(Android) { install(SSE) }
    private val mutex = Mutex()
    private data class ToolTarget(val client: Client, val serverUrl: String, val remoteName: String)
    private var clients: Map<String, Client> = emptyMap()
    private var targets: Map<String, ToolTarget> = emptyMap()
    @Volatile private var toolNames: List<Pair<String, String>> = emptyList()
    val approvalRequests = Channel<McpApprovalRequest>(Channel.BUFFERED)

    fun savedUrl(): String = preferences.getString(KEY_URLS, null)?.let { raw ->
        runCatching { org.json.JSONArray(raw).let { array -> (0 until array.length()).joinToString("\n") { array.getString(it) } } }.getOrDefault("")
    } ?: preferences.getString(KEY_URL, "").orEmpty()
    fun toolsSummary(): String = toolNames.joinToString("\n") { (name, description) -> "- $name: $description" }

    suspend fun configure(url: String): List<Pair<String, String>> = mutex.withLock {
        val normalized = url.lineSequence().map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }.distinct().toList()
        require(normalized.size <= 8) { "Connect up to 8 MCP servers." }
        require(normalized.all { it.startsWith("https://", ignoreCase = true) && it.length <= 2048 }) { "Each MCP server must be a valid HTTPS URL (one per line)." }
        val newClients = linkedMapOf<String, Client>()
        val newTargets = linkedMapOf<String, ToolTarget>()
        val listedTools = mutableListOf<Pair<String, String>>()
        try {
            normalized.forEachIndexed { index, serverUrl ->
                val next = Client(Implementation(name = "ello-android", version = "1.0"))
                next.connect(StreamableHttpClientTransport(client = httpClient, url = serverUrl))
                newClients[serverUrl] = next
                next.listTools().tools.forEach { tool ->
                    val publicName = if (normalized.size == 1) tool.name else "${index + 1}_${tool.name}"
                    newTargets[publicName] = ToolTarget(next, serverUrl, tool.name)
                    listedTools += publicName to "[${serverUrl.substringAfter("//").substringBefore('/')} ] ${tool.description.orEmpty()}\nInput schema: ${tool.inputSchema}".trim()
                }
            }
        } catch (error: Throwable) {
            newClients.values.forEach { runCatching { it.close() } }
            throw error
        }
        val previous = clients.values.toList()
        clients = newClients
        targets = newTargets
        toolNames = listedTools
        preferences.edit().putString(KEY_URLS, org.json.JSONArray(normalized).toString()).remove(KEY_URL).apply()
        previous.forEach { runCatching { it.close() } }
        toolNames
    }

    suspend fun loadSaved() {
        val url = savedUrl()
        if (url.isNotBlank() && clients.isEmpty()) runCatching { configure(url) }
            .onFailure { Log.w(TAG, "Saved MCP server is unavailable", it) }
    }

    suspend fun callTool(name: String, input: String): String {
        val request = McpApprovalRequest(name, input)
        approvalRequests.send(request)
        val allowed = withTimeout(120_000L) { request.answer.await() }
        if (!allowed) throw SecurityException("Permission denied by user.")
        return mutex.withLock {
            val target = targets[name] ?: error("MCP tool '$name' is no longer available. Reconnect the server in Agent Skills settings.")
            val arguments = runCatching { Json.parseToJsonElement(input) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
            val response = target.client.callTool(
                request = CallToolRequest(CallToolRequestParams(name = target.remoteName, arguments = arguments))
            )
            val content = response.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
            if (response.isError == true) "MCP tool failed: $content" else content.ifBlank { "Tool completed." }
        }
    }

    fun close() {
        val oldClients = clients.values.toList()
        clients = emptyMap()
        targets = emptyMap()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { oldClients.forEach { runCatching { it.close() } } }
        httpClient.close()
    }

    private companion object {
        const val TAG = "ElloMcp"
        const val KEY_URL = "server_url"
        const val KEY_URLS = "server_urls"
    }
}

internal suspend fun requestAndroidPermission(
    requests: Channel<AndroidPermissionRequest>,
    permission: String,
    explanation: String,
): Boolean {
    val request = AndroidPermissionRequest(permission, explanation)
    requests.send(request)
    return withTimeout(120_000L) { request.answer.await() }
}

internal suspend fun askMcpApproval(
    requests: Channel<McpApprovalRequest>,
    toolName: String,
    input: String,
): Boolean {
    val request = McpApprovalRequest(toolName, input)
    requests.send(request)
    return withTimeout(120_000L) { request.answer.await() }
}

/** Android action subset used by Gallery's run_intent skill path, with runtime permission gates. */
internal class ElloIntentActions(
    private val context: Context,
    private val permissionRequests: Channel<AndroidPermissionRequest>,
) {
    suspend fun run(actionName: String, rawParameters: String): String {
        val params = runCatching { JSONObject(rawParameters) }.getOrDefault(JSONObject())
        return when (actionName) {
            "get_current_date_and_time" -> SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss EEEE", Locale.getDefault()).format(Date())
            "send_email" -> {
                val address = params.optString("extra_email").trim()
                if (address.isBlank()) return "Missing recipient address."
                val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:${android.net.Uri.encode(address)}"))
                    .putExtra(Intent.EXTRA_SUBJECT, params.optString("extra_subject"))
                    .putExtra(Intent.EXTRA_TEXT, params.optString("extra_text"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Opened the email app with a draft. The user must review and send it."
            }
            "send_sms" -> {
                val phone = params.optString("phone_number").trim()
                if (phone.isBlank()) return "Missing phone number."
                val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:${android.net.Uri.encode(phone)}"))
                    .putExtra("sms_body", params.optString("sms_body"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Opened the messaging app with a draft. The user must review and send it."
            }
            "create_calendar_event" -> {
                val title = params.optString("title").trim()
                if (title.isBlank()) return "Missing event title."
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val start = runCatching { format.parse(params.optString("begin_time"))?.time }.getOrNull()
                    ?: return "Invalid event start time. Use local ISO date-time format."
                val end = runCatching { format.parse(params.optString("end_time"))?.time }.getOrNull()
                    ?: return "Invalid event end time. Use local ISO date-time format."
                val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, title)
                    .putExtra(CalendarContract.Events.DESCRIPTION, params.optString("description"))
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Opened the calendar app with a draft event. The user must review and save it."
            }
            "read_calendar_events" -> readCalendarEvents(params.optString("date"))
            "schedule_notification" -> scheduleNotification(params)
            else -> "Unsupported Android action: $actionName"
        }
    }

    private suspend fun readCalendarEvents(date: String): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            val granted = requestAndroidPermission(permissionRequests, Manifest.permission.READ_CALENDAR, "Read calendar events for the requested date")
            if (!granted) return "Calendar permission was denied."
        }
        val parsedDate = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date) }.getOrNull()
            ?: return "Invalid date. Use yyyy-MM-dd."
        val start = Calendar.getInstance().apply { time = parsedDate; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_MONTH, 1); add(Calendar.MILLISECOND, -1) }.timeInMillis
        val range = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start); ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.DESCRIPTION, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END)
        val rows = mutableListOf<String>()
        context.contentResolver.query(range, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(0).orEmpty()
                val description = cursor.getString(1).orEmpty()
                val begin = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(cursor.getLong(2)))
                val finish = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(cursor.getLong(3)))
                rows += "$begin–$finish $title${if (description.isBlank()) "" else " — $description"}"
            }
        }
        return if (rows.isEmpty()) "No calendar events found on $date." else rows.joinToString("\n")
    }

    private suspend fun scheduleNotification(params: JSONObject): String {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            val granted = requestAndroidPermission(permissionRequests, Manifest.permission.POST_NOTIFICATIONS, "Allow Ello to show the reminder notification")
            if (!granted) return "Notification permission was denied; no reminder was scheduled."
        }
        val hour = params.optInt("hour", -1)
        val minute = params.optInt("minute", -1)
        if (hour !in 0..23 || minute !in 0..59) return "Invalid time. Provide hour 0–23 and minute 0–59."
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            if (params.has("year")) set(Calendar.YEAR, params.optInt("year"))
            if (params.has("month")) set(Calendar.MONTH, (params.optInt("month") - 1).coerceIn(0, 11))
            if (params.has("day")) set(Calendar.DAY_OF_MONTH, params.optInt("day").coerceIn(1, 31))
        }
        val id = UUID.randomUUID().hashCode()
        val intent = Intent(context, AgentNotificationReceiver::class.java)
            .putExtra("title", params.optString("title", "Ello reminder"))
            .putExtra("message", params.optString("message"))
        val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (params.optBoolean("repeat_daily", false)) {
            alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, pending)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
        }
        return "Reminder scheduled for ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(calendar.time)}."
    }
}

internal class AgentNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "ello_agent_reminders"
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(channelId, "Ello reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val notification = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(context, channelId)
            else android.app.Notification.Builder(context)
        notification.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(intent.getStringExtra("title") ?: "Ello reminder")
            .setContentText(intent.getStringExtra("message").orEmpty())
            .setAutoCancel(true)
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify(UUID.randomUUID().hashCode(), notification.build())
        }
    }
}
