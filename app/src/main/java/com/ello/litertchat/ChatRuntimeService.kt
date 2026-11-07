package com.ello.litertchat

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Local chat history and browser visibility state used by the existing UI. */
internal data class ChatThread(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<ChatLine> = emptyList(),
)

internal data class ChatRuntimeSnapshot(
    val messages: List<ChatLine> = emptyList(),
    val threads: List<ChatThread> = emptyList(),
    val activeThreadId: String = "",
    val error: String? = null,
    val browserVisible: Boolean = false,
)

internal class ChatRuntimeService : Service() {
    inner class LocalBinder : Binder() {
        fun service(): ChatRuntimeService = this@ChatRuntimeService
    }

    private val binder = LocalBinder()
    private val preferences by lazy { getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE) }
    private val mutableSnapshot = MutableStateFlow(ChatRuntimeSnapshot())
    val snapshot: StateFlow<ChatRuntimeSnapshot> = mutableSnapshot.asStateFlow()
    private val aiScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeAgentRuns = AtomicInteger(0)
    private val restoredTasksStarted = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var isAgentForeground = false
    private lateinit var agentTools: AgentToolRuntime

    override fun onCreate() {
        super.onCreate()
        agentTools = AgentToolRuntime(applicationContext) { setBrowserVisible(true) }
        val loaded = loadSavedSnapshot()
        val recovered = loaded.copy(threads = loaded.threads.map { thread ->
            val messages = thread.messages.map { line -> recoverInterruptedLine(line) }
            if (messages == thread.messages) thread else thread.copy(messages = messages, updatedAt = System.currentTimeMillis())
        })
        mutableSnapshot.value = recovered
        if (recovered != loaded) persist(recovered)
        val resumableCount = findInterruptedTasks(recovered).size
        if (resumableCount > 0) {
            activeAgentRuns.set(resumableCount)
            showAgentForeground()
        }
        aiScope.launch {
            runCatching { agentTools.loadSavedMcp() }
            resumeInterruptedTasks()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_AGENT_RUN) showAgentForeground()
        return if (intent?.action == ACTION_AGENT_RUN || activeAgentRuns.get() > 0) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        agentTools.close()
        aiScope.cancel()
        super.onDestroy()
    }

    fun getSkills(): List<ElloSkill> = agentTools.getSkills()
    fun setSkillSelected(name: String, selected: Boolean) = agentTools.setSkillSelected(name, selected)
    fun addSkillMarkdown(markdown: String): ElloSkill = agentTools.addSkillMarkdown(markdown)
    fun addRemoteSkillMarkdown(markdown: String, baseUrl: String): ElloSkill = agentTools.addRemoteSkillMarkdown(markdown, baseUrl)
    suspend fun addLocalSkillFolder(directoryUri: android.net.Uri): ElloSkill = agentTools.addLocalSkillFolder(directoryUri)
    val mcpApprovalRequests get() = agentTools.mcpApprovalRequests
    val androidPermissionRequests get() = agentTools.androidPermissionRequests
    val skillSecretRequests get() = agentTools.skillSecretRequests
    fun getMcpServerUrl(): String = agentTools.getMcpServerUrl()
    suspend fun configureMcpServer(url: String): List<Pair<String, String>> = agentTools.configureMcpServer(url)

    fun sendMessage(text: String, attachments: List<ChatAttachment> = emptyList()) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) return
        val current = mutableSnapshot.value
        val thread = current.threads.firstOrNull { it.id == current.activeThreadId } ?: return
        beginAgentRun()
        val userText = buildString {
            append(trimmed.ifBlank { "Please review the attached file." })
            attachments.filter { it.extractedText.isNotBlank() }.forEach { attachment ->
                append("\n\n[Attached file: ${attachment.name}]\n${attachment.extractedText}")
            }
        }
        val userCloudContent = JSONObject()
            .put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", userText)))
        val initialContents = buildConversationContents(thread.messages, userCloudContent)
        val line = ChatLine(
            id = System.nanoTime(),
            user = true,
            body = trimmed.ifBlank { "Please review the attached file." },
            attachments = attachments,
            geminiContentsJson = JSONArray().put(userCloudContent).toString(),
        )
        val messages = thread.messages + line
        val updatedThread = thread.copy(
            title = if (thread.messages.isEmpty()) {
                trimmed.lineSequence().firstOrNull()?.take(MAX_TITLE_LENGTH)?.ifBlank {
                    attachments.firstOrNull()?.name?.take(MAX_TITLE_LENGTH) ?: "New chat"
                } ?: "New chat"
            } else thread.title,
            updatedAt = System.currentTimeMillis(),
            messages = messages,
        )
        val assistant = ChatLine(
            id = System.nanoTime() + 1,
            user = false,
            body = "Thinking…",
            resumeContentsJson = initialContents.toString(),
        )
        val withAssistant = messages + assistant
        val threadWithAssistant = updatedThread.copy(messages = withAssistant)
        publish(current.copy(messages = withAssistant, threads = replaceThread(current.threads, threadWithAssistant), error = null))

        launchAgentTurn(thread.id, assistant.id, initialContents, JSONArray(), 0)
    }

    private fun buildConversationContents(previousMessages: List<ChatLine>, userContent: JSONObject): JSONArray {
        val contents = JSONArray()
        previousMessages.takeLast(4).forEach { savedLine ->
            val saved = runCatching { JSONArray(savedLine.geminiContentsJson) }.getOrNull()
            if (saved != null) {
                for (index in 0 until saved.length()) contents.put(saved.getJSONObject(index))
            } else {
                contents.put(JSONObject()
                    .put("role", if (savedLine.user) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", savedLine.body))))
            }
        }
        contents.put(userContent)
        return contents
    }

    private fun launchAgentTurn(
        threadId: String,
        assistantId: Long,
        contents: JSONArray,
        assistantContents: JSONArray,
        previousToolCalls: Int,
    ) {
        val startedAt = System.currentTimeMillis()
        aiScope.launch {
            try {
                var finalText = ""
                var toolCalls = previousToolCalls
                for (round in 0 until MAX_AGENT_ROUNDS) {
                    val response = GeminiCloudAgent(this@ChatRuntimeService).generate(
                        contents = contents,
                        skills = agentTools.getSkills(),
                        mcpTools = agentTools.getMcpToolSummary(),
                        apiAnythingSites = agentTools.getApiAnythingSummary(),
                    )
                    val modelContent = response.getJSONObject("content")
                    val parts = modelContent.optJSONArray("parts") ?: JSONArray()
                    val calls = buildList {
                        for (index in 0 until parts.length()) {
                            val functionCall = parts.optJSONObject(index)?.optJSONObject("functionCall") ?: continue
                            add(functionCall)
                        }
                    }
                    if (calls.size > 4 || toolCalls + calls.size > MAX_AGENT_TOOL_CALLS) {
                        finalText = "I reached the action-step limit. I’ve stopped safely; tell me if you want me to continue."
                        break
                    }
                    contents.put(modelContent)
                    assistantContents.put(modelContent)
                    if (calls.isNotEmpty()) {
                        updateAssistant(
                            threadId,
                            assistantId,
                            "Working on: ${calls.first().optString("name").replace('_', ' ')}…",
                            geminiContentsJson = assistantContents.toString(),
                            resumeContentsJson = contents.toString(),
                            durable = true,
                        )
                    }
                    if (calls.isEmpty()) {
                        finalText = (0 until parts.length()).mapNotNull { index ->
                            parts.optJSONObject(index)?.optString("text")?.takeIf(String::isNotBlank)
                        }.joinToString("\n").trim()
                        break
                    }
                    for (call in calls) {
                        toolCalls++
                        val name = call.optString("name")
                        updateAssistant(
                            threadId,
                            assistantId,
                            "Working on: ${name.replace('_', ' ')}…",
                            geminiContentsJson = assistantContents.toString(),
                            resumeContentsJson = contents.toString(),
                            durable = true,
                        )
                        val arguments = call.optJSONObject("args") ?: JSONObject()
                        val result = when (name) {
                            "agent_plan_task" -> recordTaskPlan(threadId, assistantId, arguments)
                            "agent_update_task_progress" -> completeTaskStep(threadId, assistantId, arguments.optInt("step_index", -1))
                            else -> agentTools.executeCloudTool(
                                name = name,
                                arguments = arguments,
                                onSkillOutput = { output -> updateAssistantSkillOutput(threadId, assistantId, output) },
                                userRequest = latestUserRequest(contents),
                            )
                        }
                        val callId = call.optString("id")
                        require(callId.isNotBlank()) { "Gemini returned a tool call without its required ID." }
                        val functionResponse = JSONObject().put(
                            "functionResponse",
                            JSONObject().put("id", callId).put("name", name).put("response", JSONObject().apply {
                                result.forEach { (key, value) -> put(key, value) }
                            }),
                        )
                        val responseContent = JSONObject()
                            .put("role", "user")
                            .put("parts", JSONArray().put(functionResponse))
                        contents.put(responseContent)
                        assistantContents.put(responseContent)
                        updateAssistant(
                            threadId,
                            assistantId,
                            "Working on: ${name.replace('_', ' ')}…",
                            geminiContentsJson = assistantContents.toString(),
                            resumeContentsJson = contents.toString(),
                            durable = true,
                        )
                    }
                }
                if (finalText.isBlank()) {
                    finalText = if (toolCalls > 0) "I reached the action-step limit. I’ve stopped safely; tell me if you want me to continue." else "Gemini did not return a response. Please try again."
                }
                updateAssistant(
                    threadId,
                    assistantId,
                    finalText,
                    geminiContentsJson = assistantContents.toString(),
                    resumeContentsJson = "",
                )
                val elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000.0).coerceAtLeast(0.1)
                markAssistantDone(threadId, assistantId, "%.1f s · Gemini Cloud · $toolCalls actions".format(java.util.Locale.US, elapsedSeconds))
            } catch (error: Throwable) {
                Log.e("ElloChatRuntime", "Gemini cloud response failed", error)
                val message = error.localizedMessage?.takeIf(String::isNotBlank) ?: "Gemini could not respond. Check your connection and try again."
                updateAssistant(threadId, assistantId, "Ello could not reach Gemini: $message", resumeContentsJson = "")
            } finally {
                finishAgentRun()
            }
        }
    }

    private fun findInterruptedTasks(snapshot: ChatRuntimeSnapshot): List<Pair<ChatThread, ChatLine>> =
        snapshot.threads.flatMap { thread ->
            thread.messages.filter { line ->
                !line.user && line.body == INTERRUPTED_TASK_TEXT && line.resumeContentsJson.isNotBlank() &&
                    runCatching { JSONArray(line.resumeContentsJson) }.isSuccess
            }.map { thread to it }
        }.sortedBy { it.first.updatedAt }

    private fun resumeInterruptedTasks() {
        if (!restoredTasksStarted.compareAndSet(false, true)) return
        val tasks = findInterruptedTasks(mutableSnapshot.value)
        if (tasks.isEmpty()) return
        tasks.forEach { (thread, line) ->
            val contents = runCatching { JSONArray(line.resumeContentsJson) }.getOrNull() ?: return@forEach
            val incremental = runCatching { JSONArray(line.geminiContentsJson) }.getOrElse { JSONArray() }
            val calls = countFunctionCalls(incremental)
            launchAgentTurn(thread.id, line.id, contents, incremental, calls)
        }
    }

    private fun countFunctionCalls(history: JSONArray): Int {
        var count = 0
        for (i in 0 until history.length()) {
            val parts = history.optJSONObject(i)?.optJSONArray("parts") ?: continue
            for (j in 0 until parts.length()) if (parts.optJSONObject(j)?.has("functionCall") == true) count++
        }
        return count
    }

    private fun beginAgentRun() {
        activeAgentRuns.incrementAndGet()
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, ChatRuntimeService::class.java).setAction(ACTION_AGENT_RUN),
            )
        } catch (error: Exception) {
            activeAgentRuns.decrementAndGet()
            Log.e("ElloChatRuntime", "Could not start foreground task notification", error)
        }
    }

    private fun showAgentForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(AGENT_CHANNEL_ID, "Ello tasks", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows when Ello is carrying out a task you started."
                },
            )
        }
        val openApp = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, AGENT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Ello is working on your task")
            .setContentText("Tap to return to your chat and review any requested approvals.")
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, AGENT_NOTIFICATION_ID, notification, type)
        isAgentForeground = true
    }

    private fun finishAgentRun() {
        val remaining = activeAgentRuns.updateAndGet { count -> (count - 1).coerceAtLeast(0) }
        if (remaining != 0) return
        if (isAgentForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isAgentForeground = false
        }
        stopSelf()
    }

    private fun recoverInterruptedLine(line: ChatLine): ChatLine {
        if (line.user || (line.body != "Thinking…" && !line.body.startsWith("Working on:"))) return line
        val incrementalHistory = runCatching { JSONArray(line.geminiContentsJson) }.getOrNull() ?: JSONArray()
        val canResumeAutomatically = line.resumeContentsJson.isNotBlank()
        val resumeHistory = runCatching { JSONArray(line.resumeContentsJson) }.getOrNull() ?: JSONArray()
        val inspectionHistory = if (canResumeAutomatically) resumeHistory else incrementalHistory
        val pendingToolCalls = mutableListOf<Pair<String, String>>()
        for (index in 0 until inspectionHistory.length()) {
            val item = inspectionHistory.optJSONObject(index) ?: continue
            val parts = item.optJSONArray("parts") ?: continue
            for (partIndex in 0 until parts.length()) {
                val part = parts.optJSONObject(partIndex) ?: continue
                part.optJSONObject("functionCall")?.let { call ->
                    val name = call.optString("name").takeIf(String::isNotBlank)
                    if (name != null) pendingToolCalls.add(call.optString("id") to name)
                }
                part.optJSONObject("functionResponse")?.let { response ->
                    val responseId = response.optString("id")
                    val responseName = response.optString("name")
                    val pendingIndex = if (responseId.isNotBlank()) {
                        pendingToolCalls.indexOfFirst { it.first == responseId }
                    } else {
                        pendingToolCalls.indexOfFirst { it.second == responseName }
                    }
                    if (pendingIndex >= 0) pendingToolCalls.removeAt(pendingIndex)
                }
            }
        }
        if (pendingToolCalls.isNotEmpty()) {
            val parts = JSONArray()
            pendingToolCalls.forEach { (callId, name) ->
                val response = JSONObject()
                    .put("name", name)
                    .put("response", JSONObject()
                        .put("status", "interrupted")
                        .put("error", "Ello stopped before it recorded this action's result. The action may have completed. Inspect the website or account state before retrying or repeating it."))
                if (callId.isNotBlank()) response.put("id", callId)
                parts.put(JSONObject().put("functionResponse", response))
            }
            val interruptedResponse = JSONObject().put("role", "user").put("parts", parts)
            incrementalHistory.put(interruptedResponse)
            if (canResumeAutomatically) resumeHistory.put(interruptedResponse)
        }
        return line.copy(
            body = INTERRUPTED_TASK_TEXT,
            geminiContentsJson = incrementalHistory.toString(),
            resumeContentsJson = if (canResumeAutomatically) resumeHistory.toString() else "",
        )
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w("ElloChatRuntime", "Foreground task reached the Android service time limit")
        activeAgentRuns.set(0)
        if (isAgentForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isAgentForeground = false
        }
        stopSelf(startId)
    }

    fun createNewChat() {
        val thread = newThread()
        publish(ChatRuntimeSnapshot(messages = emptyList(), threads = listOf(thread) + mutableSnapshot.value.threads, activeThreadId = thread.id))
    }

    fun openChat(threadId: String) {
        val current = mutableSnapshot.value
        val thread = current.threads.firstOrNull { it.id == threadId } ?: return
        publish(current.copy(messages = thread.messages, activeThreadId = thread.id, error = null))
    }

    fun deleteChat(threadId: String) {
        val current = mutableSnapshot.value
        val remaining = current.threads.filterNot { it.id == threadId }.toMutableList()
        if (remaining.isEmpty()) remaining += newThread()
        val active = if (current.activeThreadId == threadId) remaining.first() else remaining.firstOrNull { it.id == current.activeThreadId } ?: remaining.first()
        publish(current.copy(messages = active.messages, threads = remaining, activeThreadId = active.id))
    }

    fun setBrowserVisible(visible: Boolean) {
        mutableSnapshot.value = mutableSnapshot.value.copy(browserVisible = visible)
    }

    fun clearError() { mutableSnapshot.value = mutableSnapshot.value.copy(error = null) }

    private fun updateAssistant(
        threadId: String,
        messageId: Long,
        text: String,
        append: Boolean = false,
        geminiContentsJson: String? = null,
        resumeContentsJson: String? = null,
        durable: Boolean = false,
    ) {
        val current = mutableSnapshot.value
        val thread = current.threads.firstOrNull { it.id == threadId } ?: return
        val updatedMessages = thread.messages.map { line ->
            if (line.id == messageId) line.copy(
                body = if (append) line.body.takeUnless { it == "Thinking…" }.orEmpty() + text else text,
                geminiContentsJson = geminiContentsJson ?: line.geminiContentsJson,
                resumeContentsJson = resumeContentsJson ?: line.resumeContentsJson,
            ) else line
        }
        val updatedThread = thread.copy(messages = updatedMessages, updatedAt = System.currentTimeMillis())
        publish(
            current.copy(threads = replaceThread(current.threads, updatedThread), messages = if (current.activeThreadId == threadId) updatedMessages else current.messages),
            persistSnapshot = !append || durable,
            durable = durable,
        )
    }

    private fun markAssistantDone(threadId: String, messageId: Long, timing: String) {
        val current = mutableSnapshot.value
        val thread = current.threads.firstOrNull { it.id == threadId } ?: return
        val updatedMessages = thread.messages.map { line ->
            if (line.id == messageId) line.copy(timing = timing, resumeContentsJson = "") else line
        }
        val updatedThread = thread.copy(messages = updatedMessages)
        publish(current.copy(threads = replaceThread(current.threads, updatedThread), messages = if (current.activeThreadId == threadId) updatedMessages else current.messages))
    }

    private fun updateAssistantSkillOutput(threadId: String, messageId: Long, output: SkillExecutionOutput) {
        val current = mutableSnapshot.value
        val thread = current.threads.firstOrNull { it.id == threadId } ?: return
        val updatedMessages = thread.messages.map { line ->
            if (line.id != messageId) line else {
                val attachments = if (output.imagePath.isNotBlank()) line.attachments + ChatAttachment(
                    name = output.imageName,
                    mimeType = "image/png",
                    sizeBytes = runCatching { java.io.File(output.imagePath).length() }.getOrDefault(0L),
                    extractedText = "",
                    localPath = output.imagePath,
                ) else line.attachments
                line.copy(
                    attachments = attachments,
                    skillWebviewUrl = output.webviewUrl.ifBlank { line.skillWebviewUrl },
                    skillWebviewAspect = output.webviewAspect,
                    skillWebviewIframe = output.webviewIframe,
                )
            }
        }
        val updatedThread = thread.copy(messages = updatedMessages, updatedAt = System.currentTimeMillis())
        publish(current.copy(threads = replaceThread(current.threads, updatedThread), messages = if (current.activeThreadId == threadId) updatedMessages else current.messages))
    }

    private fun recordTaskPlan(threadId: String, messageId: Long, arguments: JSONObject): Map<String, String> {
        val title = arguments.optString("title").trim().replace(Regex("\\s+"), " ").take(80)
        val rawSteps = arguments.optJSONArray("steps") ?: error("A task plan needs an ordered steps array.")
        require(title.isNotBlank() && rawSteps.length() in 2..8) { "A task plan needs a short title and two to eight steps." }
        val steps = (0 until rawSteps.length()).map { index ->
            rawSteps.optString(index).trim().replace(Regex("\\s+"), " ").take(140)
                .also { require(it.isNotBlank()) { "Task plan steps cannot be blank." } }
        }.distinct()
        require(steps.size == rawSteps.length()) { "Task plan steps must be distinct." }
        updateAssistantTaskPlan(threadId, messageId, title, steps)
        return mapOf("status" to "succeeded", "result" to "Saved a ${steps.size}-step checklist in the chat. Begin step 1 and update the checklist only after a step succeeds.")
    }

    private fun completeTaskStep(threadId: String, messageId: Long, stepIndex: Int): Map<String, String> {
        val line = mutableSnapshot.value.threads.firstOrNull { it.id == threadId }?.messages?.firstOrNull { it.id == messageId }
            ?: error("The task message is no longer available.")
        require(line.taskSteps.isNotEmpty()) { "There is no saved checklist for this task." }
        require(stepIndex in line.taskSteps.indices) { "That step index is outside the saved task plan." }
        val expected = line.taskSteps.indices.firstOrNull { it !in line.completedTaskSteps }
            ?: error("Every task step is already complete.")
        require(stepIndex == expected) { "Complete task steps in plan order. The next step is ${expected + 1}." }
        val completed = line.completedTaskSteps + stepIndex
        updateAssistantTaskProgress(threadId, messageId, completed)
        val result = if (completed.size == line.taskSteps.size) "All checklist steps are marked complete." else "Marked step ${stepIndex + 1} complete. Continue with step ${stepIndex + 2}."
        return mapOf("status" to "succeeded", "result" to result)
    }

    private fun latestUserRequest(contents: JSONArray): String {
        for (index in contents.length() - 1 downTo 0) {
            val message = contents.optJSONObject(index) ?: continue
            if (message.optString("role") != "user") continue
            val parts = message.optJSONArray("parts") ?: continue
            val text = (0 until parts.length()).mapNotNull { partIndex ->
                parts.optJSONObject(partIndex)?.opt("text") as? String
            }.joinToString("\n").trim()
            if (text.isNotBlank()) return text.take(6000)
        }
        return ""
    }

    private fun updateAssistantTaskPlan(threadId: String, messageId: Long, title: String, steps: List<String>) {
        val current = mutableSnapshot.value
        val thread = current.threads.firstOrNull { it.id == threadId } ?: return
        val updatedMessages = thread.messages.map { line ->
            if (line.id == messageId) line.copy(taskTitle = title, taskSteps = steps, completedTaskSteps = emptySet()) else line
        }
        val updatedThread = thread.copy(messages = updatedMessages, updatedAt = System.currentTimeMillis())
        publish(current.copy(threads = replaceThread(current.threads, updatedThread), messages = if (current.activeThreadId == threadId) updatedMessages else current.messages), durable = true)
    }

    private fun updateAssistantTaskProgress(threadId: String, messageId: Long, completed: Set<Int>) {
        val current = mutableSnapshot.value
        val thread = current.threads.firstOrNull { it.id == threadId } ?: return
        val updatedMessages = thread.messages.map { line ->
            if (line.id == messageId) line.copy(completedTaskSteps = completed) else line
        }
        val updatedThread = thread.copy(messages = updatedMessages, updatedAt = System.currentTimeMillis())
        publish(current.copy(threads = replaceThread(current.threads, updatedThread), messages = if (current.activeThreadId == threadId) updatedMessages else current.messages), durable = true)
    }

    suspend fun clearLocalUserData() {
        preferences.edit().remove(KEY_THREADS).remove(KEY_ACTIVE_THREAD).remove(KEY_MESSAGES).apply()
        val thread = newThread()
        mutableSnapshot.value = ChatRuntimeSnapshot(messages = emptyList(), threads = listOf(thread), activeThreadId = thread.id)
    }

    private fun publish(snapshot: ChatRuntimeSnapshot, persistSnapshot: Boolean = true, durable: Boolean = false) {
        val sortedThreads = snapshot.threads.sortedByDescending { it.updatedAt }.take(MAX_THREADS)
        val active = sortedThreads.firstOrNull { it.id == snapshot.activeThreadId } ?: sortedThreads.firstOrNull() ?: newThread()
        val normalized = snapshot.copy(
            messages = active.messages,
            threads = if (sortedThreads.isEmpty()) listOf(active) else sortedThreads,
            activeThreadId = active.id,
        )
        mutableSnapshot.value = normalized
        if (persistSnapshot) persist(normalized, durable)
    }

    private fun replaceThread(threads: List<ChatThread>, updated: ChatThread): List<ChatThread> =
        (listOf(updated) + threads.filterNot { it.id == updated.id }).sortedByDescending { it.updatedAt }

    private fun newThread() = ChatThread(UUID.randomUUID().toString(), "New chat", System.currentTimeMillis())

    private fun persist(snapshot: ChatRuntimeSnapshot, durable: Boolean = false) {
        val array = JSONArray()
        snapshot.threads.forEach { thread ->
            val messages = JSONArray()
            thread.messages.forEach { line ->
                val attachments = JSONArray()
                line.attachments.forEach { attachment ->
                    attachments.put(JSONObject()
                        .put("name", attachment.name)
                        .put("mimeType", attachment.mimeType)
                        .put("sizeBytes", attachment.sizeBytes)
                        .put("extractedText", attachment.extractedText)
                        .put("localPath", attachment.localPath))
                }
                messages.put(JSONObject()
                    .put("id", line.id)
                    .put("user", line.user)
                    .put("body", line.body)
                    .put("timing", line.timing)
                    .put("skillWebviewUrl", line.skillWebviewUrl)
                    .put("skillWebviewAspect", line.skillWebviewAspect.toDouble())
                    .put("skillWebviewIframe", line.skillWebviewIframe)
                    .put("geminiContentsJson", line.geminiContentsJson)
                    .put("resumeContentsJson", line.resumeContentsJson)
                    .put("taskTitle", line.taskTitle)
                    .put("taskSteps", JSONArray().apply { line.taskSteps.forEach(::put) })
                    .put("completedTaskSteps", JSONArray().apply { line.completedTaskSteps.sorted().forEach(::put) })
                    .put("attachments", attachments))
            }
            array.put(JSONObject()
                .put("id", thread.id)
                .put("title", thread.title)
                .put("updatedAt", thread.updatedAt)
                .put("messages", messages))
        }
        val editor = preferences.edit()
            .putString(KEY_THREADS, array.toString())
            .putString(KEY_ACTIVE_THREAD, snapshot.activeThreadId)
        if (durable) editor.commit() else editor.apply()
    }

    private fun loadSavedSnapshot(): ChatRuntimeSnapshot {
        val threads = runCatching {
            val array = JSONArray(preferences.getString(KEY_THREADS, "[]"))
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val messages = item.optJSONArray("messages") ?: JSONArray()
                    add(ChatThread(
                        id = item.optString("id", "legacy-$index"),
                        title = item.optString("title", "Chat"),
                        updatedAt = item.optLong("updatedAt", index.toLong()),
                        messages = buildList {
                            for (messageIndex in 0 until messages.length()) {
                                val message = messages.getJSONObject(messageIndex)
                                val savedAttachments = message.optJSONArray("attachments") ?: JSONArray()
                                add(ChatLine(
                                    id = message.optLong("id", messageIndex.toLong()),
                                    user = message.optBoolean("user"),
                                    body = message.optString("body"),
                                    timing = message.optString("timing"),
                                    skillWebviewUrl = message.optString("skillWebviewUrl"),
                                    skillWebviewAspect = message.optDouble("skillWebviewAspect", 1.333).toFloat(),
                                    skillWebviewIframe = message.optBoolean("skillWebviewIframe"),
                                    geminiContentsJson = message.optString("geminiContentsJson"),
                                    resumeContentsJson = message.optString("resumeContentsJson"),
                                    taskTitle = message.optString("taskTitle"),
                                    taskSteps = message.optJSONArray("taskSteps")?.let { steps ->
                                        (0 until steps.length()).mapNotNull { stepIndex -> steps.optString(stepIndex).takeIf(String::isNotBlank) }
                                    }.orEmpty(),
                                    completedTaskSteps = message.optJSONArray("completedTaskSteps")?.let { completed ->
                                        (0 until completed.length()).mapNotNull { stepIndex -> completed.optInt(stepIndex, -1).takeIf { it >= 0 } }.toSet()
                                    }.orEmpty(),
                                    attachments = buildList {
                                        for (attachmentIndex in 0 until savedAttachments.length()) {
                                            val attachment = savedAttachments.getJSONObject(attachmentIndex)
                                            add(ChatAttachment(
                                                name = attachment.optString("name"),
                                                mimeType = attachment.optString("mimeType", "application/octet-stream"),
                                                sizeBytes = attachment.optLong("sizeBytes"),
                                                extractedText = attachment.optString("extractedText"),
                                                localPath = attachment.optString("localPath"),
                                            ))
                                        }
                                    },
                                ))
                            }
                        },
                    ))
                }
            }
        }.getOrDefault(emptyList())

        val legacyMessages = if (threads.isEmpty()) runCatching {
            val array = JSONArray(preferences.getString(KEY_MESSAGES, "[]"))
            buildList {
                for (index in 0 until array.length()) {
                    val message = array.getJSONObject(index)
                    add(ChatLine(message.optLong("id", index.toLong()), message.optBoolean("user"), message.optString("body"), message.optString("timing")))
                }
            }
        }.getOrDefault(emptyList()) else emptyList()

        val allThreads = (threads.ifEmpty {
            listOf(ChatThread(
                id = UUID.randomUUID().toString(),
                title = legacyMessages.firstOrNull { it.user }?.body?.lineSequence()?.first()?.take(MAX_TITLE_LENGTH) ?: "New chat",
                updatedAt = System.currentTimeMillis(),
                messages = legacyMessages,
            ))
        }).sortedByDescending { it.updatedAt }
        val active = allThreads.firstOrNull { it.id == preferences.getString(KEY_ACTIVE_THREAD, null) } ?: allThreads.first()
        return ChatRuntimeSnapshot(messages = active.messages, threads = allThreads, activeThreadId = active.id)
    }

    companion object {
        private const val PREFERENCES_NAME = "litert_chat_settings"
        private const val KEY_MESSAGES = "saved_messages"
        private const val KEY_THREADS = "saved_threads"
        private const val KEY_ACTIVE_THREAD = "active_thread_id"
        private const val ACTION_AGENT_RUN = "com.ello.litertchat.ACTION_AGENT_RUN"
        private const val INTERRUPTED_TASK_TEXT = "Ello was interrupted while working. It will check the current state before continuing."
        private const val AGENT_CHANNEL_ID = "ello_agent_tasks"
        private const val AGENT_NOTIFICATION_ID = 4107
        private const val MAX_AGENT_ROUNDS = 12
        private const val MAX_AGENT_TOOL_CALLS = 24
        private const val MAX_THREADS = 40
        private const val MAX_TITLE_LENGTH = 42
    }
}
