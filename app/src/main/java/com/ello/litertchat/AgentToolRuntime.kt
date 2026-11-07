package com.ello.litertchat

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel

/** Existing device-side, approval-gated tools used by the cloud planner. */
internal class AgentToolRuntime(context: Context, private val openBrowser: () -> Unit) {
    private val appContext = context.applicationContext
    private val skills = ElloSkillStore(appContext)
    private val scripts = SkillScriptExecutor(appContext)
    private val mcp = ElloMcpConnection(appContext)
    private val apiAnything = ApiAnythingReadClient(appContext, openBrowser)
    private val google = GoogleAgentTools(appContext, mcp.approvalRequests)
    private val permissionRequests = Channel<AndroidPermissionRequest>(Channel.BUFFERED)
    private val secretRequests = Channel<SkillSecretRequest>(Channel.BUFFERED)
    private val intentActions = ElloIntentActions(appContext, permissionRequests)

    val mcpApprovalRequests get() = mcp.approvalRequests
    val androidPermissionRequests get() = permissionRequests
    val skillSecretRequests get() = secretRequests

    suspend fun loadSavedMcp() = mcp.loadSaved()
    fun getSkills(): List<ElloSkill> = skills.allSkills()
    fun setSkillSelected(name: String, selected: Boolean) = skills.setSelected(name, selected)
    fun addSkillMarkdown(markdown: String): ElloSkill = skills.addMarkdown(markdown)
    fun addRemoteSkillMarkdown(markdown: String, baseUrl: String): ElloSkill = skills.addRemoteMarkdown(markdown, baseUrl)
    suspend fun addLocalSkillFolder(directoryUri: android.net.Uri): ElloSkill = skills.addLocalSkillFolder(directoryUri)
    fun getMcpServerUrl(): String = mcp.savedUrl()
    fun getMcpToolSummary(): String = mcp.toolsSummary()
    fun getApiAnythingSummary(): String = apiAnything.siteSummary()
    fun isApiAnythingSharedCatalogEnabled(): Boolean = apiAnything.isSharedCatalogEnabled()
    fun setApiAnythingSharedCatalogEnabled(enabled: Boolean) = apiAnything.setSharedCatalogEnabled(enabled)
    suspend fun configureMcpServer(url: String): List<Pair<String, String>> = mcp.configure(url)

    fun executeCloudTool(
        name: String,
        arguments: org.json.JSONObject,
        onSkillOutput: (SkillExecutionOutput) -> Unit,
        userRequest: String = "",
    ): Map<String, String> {
        val tools = ElloSkillToolset(skills, scripts, mcp, intentActions, ::requestSecret, onSkillOutput)
        return when (name) {
            "load_skill" -> tools.loadSkill(arguments.optString("skill_name"))
            "run_js" -> tools.runJs(arguments.optString("skill_name"), arguments.optString("script_name"), arguments.optString("data", "{}"))
            "run_mcp_tool" -> tools.runMcpTool(arguments.optString("tool_name"), arguments.optString("input", "{}"))
            "run_intent" -> tools.runIntent(arguments.optString("intent"), arguments.optString("parameters", "{}"))
            "api_anything_find_sites" -> apiCall { apiAnything.findSites(arguments.optString("query")) }
            "api_anything_list_operations" -> apiCall { apiAnything.listOperations(arguments.optString("site")) }
            "api_anything_call_read" -> apiCall {
                apiAnything.callRead(arguments.optString("site"), arguments.optString("operation"), arguments.optString("args_json", "{}"))
            }
            "api_anything_capture_start" -> browserAction {
                LocalBrowserSession.startApiAnythingCapture(arguments.optString("args_json", "{}"), arguments.optBoolean("reset", false))
            }
            "api_anything_capture_stop" -> apiCall { LocalBrowserSession.stopApiAnythingCapture() }
            "api_anything_learn_operation" -> apiCall {
                apiAnything.learnOperation(arguments.optString("site"), arguments.optString("operation"), arguments.optString("holdout_args_json", "{}"))
            }
            "get_profile_memory" -> apiCall { AgentProfileMemory.read(appContext, arguments.optString("category")) }
            "remember_profile_memory" -> apiCall {
                AgentProfileMemory.remember(appContext, arguments.optString("category"), arguments.optString("memory"))
            }
            "forget_profile_memory" -> apiCall {
                AgentProfileMemory.forget(appContext, arguments.optString("category"), arguments.optString("memory"))
            }
            "google_gmail_search", "google_gmail_read", "google_gmail_send", "google_gmail_send_to_contact", "google_gmail_reply",
            "google_calendar_list", "google_calendar_create", "google_calendar_update", "google_calendar_delete",
            "google_tasks_list_lists", "google_tasks_list", "google_tasks_create", "google_tasks_update", "google_tasks_delete" -> google.execute(name, arguments)
            "browser_search" -> browserAction {
                require(arguments.optString("query").isNotBlank()) { "Enter what you want Ello to search for." }
                openBrowser()
                val query = java.net.URLEncoder.encode(arguments.optString("query").take(300), Charsets.UTF_8.name())
                val url = LocalBrowserSession.navigate("https://www.google.com/search?q=$query")
                mapOf("result" to "Opened web search results at $url. Take a browser snapshot to inspect them.", "status" to "succeeded")
            }
            "browser_navigate" -> browserAction {
                openBrowser()
                val requestedUrl = arguments.optString("url")
                require(requestedUrl.trim().startsWith("https://", true)) { "For your security, Ello's agent browser opens HTTPS websites only." }
                val url = LocalBrowserSession.navigate(requestedUrl)
                mapOf("result" to "Opened $url. Take a browser snapshot to inspect the page.", "status" to "succeeded")
            }
            "browser_snapshot" -> browserAction {
                openBrowser()
                mapOf("page" to LocalBrowserSession.agentSnapshot(), "status" to "succeeded")
            }
            "browser_wait" -> browserAction {
                openBrowser()
                LocalBrowserSession.agentWait(arguments.optString("text"), arguments.optInt("timeout_ms", 4_000))
            }
            "browser_click" -> browserAction {
                val ref = arguments.optString("ref")
                require(ref.matches(Regex("[0-9]{1,3}"))) { "Use a current element reference from the latest browser snapshot." }
                val snapshot = org.json.JSONObject(LocalBrowserSession.agentSnapshot())
                val elements = snapshot.optJSONArray("elements") ?: org.json.JSONArray()
                val element = (0 until elements.length()).mapNotNull(elements::optJSONObject).firstOrNull { it.optString("ref") == ref }
                    ?: error("That page element is no longer available. Take a fresh browser snapshot.")
                val label = element.optString("label").ifBlank { element.optString("tag") }.take(120)
                val pageUri = runCatching { java.net.URI(snapshot.optString("url")) }.getOrNull()
                val host = pageUri?.host.orEmpty()
                val transactionPage = pageUri?.path.orEmpty().contains(Regex("checkout|payment|booking|reservation|appointment|order", RegexOption.IGNORE_CASE)) ||
                    Regex("book( an)? appointment|reservation|checkout|place (an )?order|confirm (your )?(booking|appointment)", RegexOption.IGNORE_CASE)
                        .containsMatchIn(snapshot.optString("title") + " " + snapshot.optString("text").take(2500))
                val formSubmit = element.optBoolean("formSubmit")
                val preview = if (formSubmit || transactionPage || BROWSER_CONFIRM_ACTION.containsMatchIn(label)) {
                    runCatching { org.json.JSONObject(LocalBrowserSession.agentActionPreview(ref)) }.getOrDefault(org.json.JSONObject())
                } else org.json.JSONObject()
                val method = preview.optString("method").uppercase()
                val likelyFinalStep = transactionPage && BROWSER_FINAL_STEP.containsMatchIn(label)
                val requiresReview = BROWSER_CONFIRM_ACTION.containsMatchIn(label) ||
                    formSubmit && (transactionPage || method == "POST") || likelyFinalStep
                if (requiresReview) {
                    val fields = preview.optJSONArray("fields") ?: org.json.JSONArray()
                    val fieldSummary = (0 until minOf(fields.length(), 8)).mapNotNull(fields::optJSONObject)
                        .joinToString("\n") { "${it.optString("label").take(70)}: ${it.optString("value").take(100)}" }
                    val context = preview.optString("context").take(260)
                    val formDetails = buildString {
                        if (preview.has("destination")) append("\nForm: ${preview.optString("method")} ${preview.optString("destination")}")
                        if (fieldSummary.isNotBlank()) append("\nFields:\n$fieldSummary")
                        if (context.isNotBlank()) append("\nPage summary: $context")
                    }
                    val input = "Website: $host\nPage: ${snapshot.optString("title").take(120)}\nAction: ${element.optString("tag")} “$label”$formDetails\nReview these details before allowing the website action."
                    val allowed = kotlinx.coroutines.runBlocking(Dispatchers.IO) { askMcpApproval(mcp.approvalRequests, "Review website action", input) }
                    if (!allowed) return@browserAction mapOf("result" to "The user declined this website action.", "status" to "denied")
                }
                LocalBrowserSession.agentClick(ref)
            }
            "browser_fill" -> browserAction {
                LocalBrowserSession.agentFill(arguments.optString("ref"), arguments.optString("value"))
            }
            "browser_fill_profile" -> browserAction {
                val field = arguments.optString("profile_field").trim()
                require(field.isNotBlank()) { "Specify the exact profile field or saved form-detail label." }
                val (_, value) = PersonalFormVault.resolve(appContext, field)
                    ?: error("No saved value is available. Add it in Profile first.")
                LocalBrowserSession.agentFill(arguments.optString("ref"), value)
            }
            "payment_methods_list" -> apiCall {
                require(PAYMENT_REQUEST_INTENT.containsMatchIn(userRequest)) {
                    "Saved payment methods can be listed only during a checkout or booking the user requested."
                }
                val methods = org.json.JSONArray().apply {
                    PaymentCardVault.entries(appContext).forEach { method ->
                        put(org.json.JSONObject().put("label", method.label).put("last_four", method.lastFour))
                    }
                }
                mapOf("methods" to methods.toString(), "status" to "succeeded")
            }
            "browser_fill_payment" -> browserAction {
                require(PAYMENT_REQUEST_INTENT.containsMatchIn(userRequest)) {
                    "Ello can fill a saved payment method only during a checkout or booking the user requested. Ask the user to make that request in chat first."
                }
                val cardLabel = arguments.optString("payment_method").trim()
                val field = arguments.optString("field").trim()
                require(cardLabel.isNotBlank() && field in PAYMENT_FIELDS) { "Choose an exact saved payment method and a supported card field." }
                val value = PaymentCardVault.resolve(appContext, cardLabel, field)
                LocalBrowserSession.agentFillPayment(arguments.optString("ref"), value, field)
            }
            "browser_select" -> browserAction {
                LocalBrowserSession.agentSelect(arguments.optString("ref"), arguments.optString("choice"))
            }
            "browser_scroll" -> browserAction {
                val direction = arguments.optString("direction")
                require(direction in setOf("up", "down")) { "Direction must be up or down." }
                LocalBrowserSession.agentScroll(direction)
            }
            "browser_back" -> browserAction { LocalBrowserSession.agentBack() }
            else -> mapOf("error" to "Unsupported Ello tool.", "status" to "failed")
        }
    }

    private fun apiCall(block: () -> Map<String, String>): Map<String, String> = runCatching(block).getOrElse {
        mapOf("error" to (it.localizedMessage ?: "The API Anything read could not be completed."), "status" to "failed")
    }

    private fun browserAction(block: suspend () -> Any): Map<String, String> =
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            runCatching { block() }.fold(
                { value ->
                    when (value) {
                        is Map<*, *> -> value.entries.associate { it.key.toString() to it.value.toString() }
                        is String -> {
                            val parsed = runCatching { org.json.JSONObject(value) }.getOrNull()
                            if (parsed == null) mapOf("result" to value, "status" to "succeeded")
                            else buildMap {
                                parsed.keys().forEach { key -> put(key, parsed.opt(key)?.toString().orEmpty()) }
                                put("status", if (parsed.optBoolean("ok", true)) "succeeded" else "failed")
                            }
                        }
                        else -> mapOf("result" to value.toString(), "status" to "succeeded")
                    }
                },
                { mapOf("error" to (it.localizedMessage ?: "Browser action failed."), "status" to "failed") },
            )
        }

    private suspend fun requestSecret(skill: ElloSkill): String {
        val request = SkillSecretRequest(skill.name, skill.requireSecretDescription)
        secretRequests.send(request)
        val value = kotlinx.coroutines.withTimeout(120_000L) { request.answer.await() }
        if (value.isNotBlank()) skills.saveSecret(skill.name, value)
        return value
    }

    fun close() {
        scripts.close()
        mcp.close()
        permissionRequests.close()
        secretRequests.close()
    }

    private companion object {
        val BROWSER_CONFIRM_ACTION = Regex("\\b(book|booking|reserve|reservation|confirm|pay|payment|checkout|buy|purchase|place order|submit|send|cancel|delete|subscribe|schedule|complete order|finalize booking|confirm appointment|agree|accept|consent|terms and conditions|authorize)\\b", RegexOption.IGNORE_CASE)
        val BROWSER_FINAL_STEP = Regex("\\b(continue|proceed|next|finish|complete|review|place order|book now|confirm)\\b", RegexOption.IGNORE_CASE)
        val PAYMENT_FIELDS = setOf("card_number", "cardholder", "expiry", "expiry_month", "expiry_year")
        val PAYMENT_REQUEST_INTENT = Regex("\\b(book|booking|reserve|reservation|purchase|buy|checkout|pay|payment|place an order)\\b", RegexOption.IGNORE_CASE)
    }
}
