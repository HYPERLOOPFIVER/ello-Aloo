package com.ello.litertchat

import android.content.Context
import android.os.StatFs
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class SkillExecutionOutput(
    val resultText: String = "",
    val imagePath: String = "",
    val imageName: String = "",
    val webviewUrl: String = "",
    val webviewAspect: Float = 1.333f,
    val webviewIframe: Boolean = false,
)

/** Small Gallery-shaped skill store. Skills are prompt modules, not separately trained models. */
internal data class ElloSkill(
    val name: String,
    val description: String,
    val instructions: String,
    val selected: Boolean = true,
    val assetDirectory: String = "",
    val skillUrl: String = "",
    val localDirectory: String = "",
    val requireSecret: Boolean = false,
    val requireSecretDescription: String = "",
)

internal class ElloSkillStore(private val context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("ello_agent_skills", Context.MODE_PRIVATE)

    fun selectedSkills(): List<ElloSkill> = allSkills().filter { it.selected }

    fun allSkills(): List<ElloSkill> {
        val saved = runCatching { org.json.JSONArray(preferences.getString(KEY_SKILLS, "[]")) }.getOrNull()
        val persisted = if (saved == null) emptyList() else buildList {
            for (index in 0 until saved.length()) {
                val item = saved.optJSONObject(index) ?: continue
                add(ElloSkill(item.optString("name"), item.optString("description"), item.optString("instructions"), item.optBoolean("selected", true), item.optString("assetDirectory"), item.optString("skillUrl"), item.optString("localDirectory"), item.optBoolean("requireSecret"), item.optString("requireSecretDescription")))
            }
        }
        val builtIns = loadBuiltInSkills()
        val savedByName = persisted.associateBy { it.name.lowercase() }
        return (builtIns.map { builtIn ->
            val savedSkill = savedByName[builtIn.name.lowercase()]
            if (savedSkill == null) builtIn else builtIn.copy(selected = savedSkill.selected)
        } + persisted.filter { it.assetDirectory.isEmpty() && builtIns.none { builtIn -> builtIn.name.equals(it.name, true) } })
    }

    fun setSelected(name: String, selected: Boolean) {
        save(allSkills().map { if (it.name == name) it.copy(selected = selected) else it })
    }

    fun addMarkdown(markdown: String): ElloSkill {
        val skill = parseSkill(markdown)
        require(allSkills().none { it.name.equals(skill.name, ignoreCase = true) }) { "A skill named '${skill.name}' already exists." }
        save(allSkills() + skill)
        return skill
    }

    fun addRemoteMarkdown(markdown: String, baseUrl: String): ElloSkill {
        val skill = parseSkill(markdown).copy(skillUrl = baseUrl)
        require(allSkills().none { it.name.equals(skill.name, ignoreCase = true) }) { "A skill named '${skill.name}' already exists." }
        save(allSkills() + skill)
        return skill
    }

    suspend fun addLocalSkillFolder(directoryUri: android.net.Uri): ElloSkill = withContext(Dispatchers.IO) {
        val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, directoryUri)
            ?: error("Could not open the selected skill folder.")
        val skillFile = root.findFile("SKILL.md")?.takeIf { it.isFile }
            ?: error("Selected folder must contain SKILL.md.")
        val markdown = context.contentResolver.openInputStream(skillFile.uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Could not read SKILL.md.")
        val parsed = parseSkill(markdown)
        require(allSkills().none { it.name.equals(parsed.name, ignoreCase = true) }) { "A skill named '${parsed.name}' already exists." }
        val slug = parsed.name.lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-')
        val destination = File(context.filesDir, "agent-skills/$slug")
        if (destination.exists()) destination.deleteRecursively()
        destination.mkdirs()
        fun copyDirectory(source: androidx.documentfile.provider.DocumentFile, target: File) {
            target.mkdirs()
            source.listFiles().forEach { child ->
                val targetChild = File(target, child.name ?: return@forEach)
                if (child.isDirectory) copyDirectory(child, targetChild)
                else context.contentResolver.openInputStream(child.uri)?.use { input -> targetChild.outputStream().use(input::copyTo) }
            }
        }
        copyDirectory(root, destination)
        val skill = parsed.copy(localDirectory = "agent-skills/$slug")
        save(allSkills() + skill)
        skill
    }

    fun getScriptSource(skill: ElloSkill, scriptName: String): String {
        val fileName = scriptName.substringAfterLast('/').ifBlank { "index.html" }
        require(fileName.matches(Regex("[A-Za-z0-9_.-]{1,120}"))) { "Invalid skill script name." }
        return when {
            skill.localDirectory.isNotBlank() -> File(context.filesDir, "${skill.localDirectory}/scripts/$fileName").readText()
            skill.assetDirectory.isNotBlank() -> appAsset(skill.assetDirectory, fileName)
            skill.skillUrl.isNotBlank() -> "${skill.skillUrl.trimEnd('/')}/scripts/$fileName"
            else -> error("This skill does not include an executable script.")
        }
    }

    fun saveSkillResultImage(base64: String): String {
        val bytes = android.util.Base64.decode(base64.substringAfter("base64,", base64), android.util.Base64.DEFAULT)
        val outputDir = File(context.filesDir, "agent-skill-results").apply { mkdirs() }
        return File(outputDir, "${System.nanoTime()}.png").apply { writeBytes(bytes) }.absolutePath
    }

    fun webviewUrl(skill: ElloSkill, path: String): String {
        if (path.startsWith("https://", true) || path.startsWith("http://", true)) return path
        val base = when {
            skill.localDirectory.isNotBlank() -> "https://ello.local/skills/${skill.localDirectory}/assets/"
            skill.assetDirectory.isNotBlank() -> "https://ello.local/skills/${skill.assetDirectory}/assets/"
            skill.skillUrl.isNotBlank() -> "${skill.skillUrl.trimEnd('/')}/assets/"
            else -> return path
        }
        return base + path.removePrefix("/")
    }

    fun readSecret(skillName: String): String? = runCatching {
        val packed = preferences.getString("secret_${skillName.lowercase()}", null) ?: return null
        val data = android.util.Base64.decode(packed, android.util.Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8)
    }.getOrNull()

    fun saveSecret(skillName: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val packed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        preferences.edit().putString("secret_${skillName.lowercase()}", android.util.Base64.encodeToString(packed, android.util.Base64.NO_WRAP)).apply()
    }

    private fun secretKey(): SecretKey {
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(SECRET_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(SECRET_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
            generateKey()
        }
    }

    private fun appAsset(directory: String, fileName: String): String {
        val path = "skills/$directory/scripts/$fileName"
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }

    private fun loadBuiltInSkills(): List<ElloSkill> {
        val directories = runCatching { context.assets.list("skills").orEmpty().toList() }.getOrDefault(emptyList())
        return directories.mapNotNull { directory ->
            runCatching {
                val markdown = context.assets.open("skills/$directory/SKILL.md").bufferedReader().use { it.readText() }
                val parts = markdown.split("---", limit = 3)
                require(parts.size == 3)
                val metadata = parts[1].lines().mapNotNull { line ->
                    val separator = line.indexOf(':')
                    if (separator <= 0) null else line.substring(0, separator).trim() to line.substring(separator + 1).trim()
                }.toMap()
                val name = metadata["name"].orEmpty()
                val description = metadata["description"].orEmpty()
                require(name.isNotBlank() && description.isNotBlank())
                val fields = parseFrontMatter(parts[1])
                ElloSkill(
                    name = name,
                    description = description,
                    instructions = parts[2].trim(),
                    selected = !name.equals("learn-something-new", ignoreCase = true),
                    assetDirectory = directory,
                    requireSecret = fields["require-secret"].equals("true", true),
                    requireSecretDescription = fields["require-secret-description"].orEmpty(),
                )
            }.getOrNull()
        }
    }

    private fun save(skills: List<ElloSkill>) {
        val array = org.json.JSONArray()
        skills.forEach { skill ->
            array.put(org.json.JSONObject().put("name", skill.name).put("description", skill.description).put("instructions", skill.instructions).put("selected", skill.selected).put("assetDirectory", skill.assetDirectory).put("skillUrl", skill.skillUrl).put("localDirectory", skill.localDirectory).put("requireSecret", skill.requireSecret).put("requireSecretDescription", skill.requireSecretDescription))
        }
        preferences.edit().putString(KEY_SKILLS, array.toString()).apply()
    }

    private companion object {
        const val KEY_SKILLS = "skills"
        const val SECRET_KEY_ALIAS = "ello_agent_skill_secrets"
        val DEFAULT_SKILLS = emptyList<ElloSkill>()
    }

    private fun parseSkill(markdown: String): ElloSkill {
        val sections = markdown.split("---", limit = 3)
        require(sections.size == 3) { "SKILL.md needs a front-matter block between --- lines." }
        val metadata = parseFrontMatter(sections[1])
        val name = metadata["name"].orEmpty().trim()
        val description = metadata["description"].orEmpty().trim()
        val instructions = sections[2].trim()
        require(name.isNotBlank() && description.isNotBlank() && instructions.isNotBlank()) { "SKILL.md must include name, description, and instructions." }
        return ElloSkill(name, description, instructions, requireSecret = metadata["require-secret"].equals("true", true), requireSecretDescription = metadata["require-secret-description"].orEmpty())
    }

    private fun parseFrontMatter(frontMatter: String): Map<String, String> = frontMatter.lines().mapNotNull { line ->
        val separator = line.indexOf(':')
        if (separator <= 0) null else line.substring(0, separator).trim() to line.substring(separator + 1).trim()
    }.toMap()
}

/** Local skill tool dispatcher used by the authenticated cloud planner. */
internal class ElloSkillToolset(
    private val skills: ElloSkillStore,
    private val scripts: SkillScriptExecutor,
    private val mcp: ElloMcpConnection,
    private val intentActions: ElloIntentActions,
    private val requestSecret: suspend (ElloSkill) -> String,
    private val onSkillOutput: (SkillExecutionOutput) -> Unit,
) {
    fun loadSkill(
        skillName: String
    ): Map<String, String> {
        val skill = skills.selectedSkills().firstOrNull { it.name.equals(skillName, ignoreCase = true) }
            ?: return mapOf("error" to "Skill not found or disabled")
        return mapOf("skill_name" to skill.name, "skill_instructions" to skill.instructions)
    }
    fun runJs(
        skillName: String,
        scriptName: String,
        data: String,
    ): Map<String, String> = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
        val skill = skills.selectedSkills().firstOrNull { it.name.equals(skillName, ignoreCase = true) }
            ?: return@runBlocking mapOf("error" to "Skill not found or disabled", "status" to "failed")
        val source = runCatching { skills.getScriptSource(skill, scriptName) }
            .getOrElse { return@runBlocking mapOf("error" to (it.localizedMessage ?: "Skill script is unavailable"), "status" to "failed") }
        val secret = if (skill.requireSecret) skills.readSecret(skill.name) ?: requestSecret(skill) else ""
        if (skill.requireSecret && secret.isBlank()) return@runBlocking mapOf("error" to "Secret entry was cancelled.", "status" to "failed")
        runCatching { scripts.execute(skill, scriptName, source, data, secret) }
            .fold({ raw ->
                runCatching { org.json.JSONObject(raw) }.fold({ result ->
                    val error = result.optString("error")
                    if (error.isNotBlank()) return@fold mapOf("error" to error, "status" to "failed")
                    val image = result.optJSONObject("image")?.optString("base64").orEmpty()
                    val imagePath = if (image.isNotBlank()) runCatching { skills.saveSkillResultImage(image) }.getOrDefault("") else ""
                    val webview = result.optJSONObject("webview")
                    val url = webview?.optString("url").orEmpty().takeIf { it.isNotBlank() }?.let { skills.webviewUrl(skill, it) }.orEmpty()
                    val aspect = webview?.optDouble("aspectRatio", 1.333)?.toFloat()?.takeIf { it > 0f } ?: 1.333f
                    val iframe = webview?.optBoolean("iframe", false) ?: false
                    val resultValue = result.opt("result")
                    val resultText = if (resultValue == null || resultValue === org.json.JSONObject.NULL) "" else resultValue.toString()
                    val text = resultText.ifBlank { if (imagePath.isNotBlank()) "Generated image." else if (url.isNotBlank()) "Generated interactive view." else raw }
                    onSkillOutput(SkillExecutionOutput(text, imagePath, "${skill.name} result.png", url, aspect, iframe))
                    mapOf("result" to text, "image_generated" to imagePath.isNotBlank().toString(), "interactive_view_generated" to url.isNotBlank().toString(), "status" to "succeeded")
                }, { mapOf("result" to raw, "status" to "succeeded") })
            }, { mapOf("error" to (it.localizedMessage ?: "Skill script failed"), "status" to "failed") })
    }
    fun runMcpTool(
        toolName: String,
        input: String,
    ): Map<String, String> = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
        runCatching { mcp.callTool(toolName, input) }
            .fold({ mapOf("result" to it, "status" to "succeeded") }, { mapOf("error" to (it.localizedMessage ?: "MCP call failed"), "status" to "failed") })
    }
    fun runIntent(
        intent: String,
        parameters: String,
    ): Map<String, String> = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
        runCatching { intentActions.run(intent, parameters) }
            .fold({ mapOf("result" to it, "status" to "succeeded") }, { mapOf("error" to (it.localizedMessage ?: "Android action failed"), "status" to "failed") })
    }
}

/** Runs Gallery-format skill scripts in an isolated, hidden Android WebView. */
internal class SkillScriptExecutor(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var webView: WebView? = null

    suspend fun execute(skill: ElloSkill, scriptName: String, source: String, data: String, secret: String): String = withTimeout(60_000L) {
        suspendCancellableCoroutine { continuation ->
            mainHandler.post {
                try {
                    val view = getOrCreateWebView()
                    val bridge = ResultBridge { value ->
                        if (continuation.isActive) continuation.resume(value)
                    }
                    view.removeJavascriptInterface("ElloSkillBridge")
                    view.addJavascriptInterface(bridge, "ElloSkillBridge")
                    view.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): WebResourceResponse? {
                            val url = request?.url ?: return null
                            if (url.host != "ello.local") return null
                            val path = URLDecoder.decode(url.path.orEmpty(), "UTF-8").trimStart('/')
                            val skillAssetPrefix = if (skill.localDirectory.isNotBlank()) "skills/${skill.localDirectory}/assets/" else "skills/${skill.assetDirectory}/assets/"
                            if (!path.startsWith(skillAssetPrefix)) return null
                            val assetPath = if (skill.localDirectory.isNotBlank()) path.removePrefix("skills/") else path
                            val bytes = skillsAsset(skill, assetPath) ?: return WebResourceResponse("text/plain", "UTF-8", 404, "Not found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                            val mime = when {
                                assetPath.endsWith(".svg") -> "image/svg+xml"
                                assetPath.endsWith(".js") -> "application/javascript"
                                assetPath.endsWith(".css") -> "text/css"
                                else -> "text/html"
                            }
                            return WebResourceResponse(mime, "UTF-8", ByteArrayInputStream(bytes))
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            val quotedData = org.json.JSONObject.quote(data.ifBlank { "{}" })
                            val quotedSecret = org.json.JSONObject.quote(secret)
                            val script = """
                                (async function() {
                                  try {
                                    if (typeof window.ai_edge_gallery_get_result !== 'function') throw new Error('Skill script does not define ai_edge_gallery_get_result');
                                    const value = await window.ai_edge_gallery_get_result($quotedData, $quotedSecret);
                                    ElloSkillBridge.onResult(typeof value === 'string' ? value : JSON.stringify(value ?? {}));
                                  } catch (e) { ElloSkillBridge.onResult(JSON.stringify({error: String(e)})); }
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(script, null)
                        }
                    }
                    val base = "https://ello.local/skills/${skill.localDirectory.ifBlank { skill.assetDirectory.ifBlank { skill.name.lowercase().replace(Regex("[^a-z0-9-]"), "-") } }}/scripts/"
                    if (source.startsWith("https://", ignoreCase = true)) {
                        view.loadUrl(source)
                    } else {
                        val html = if (scriptName.endsWith(".js", true)) "<html><body><script>$source</script></body></html>" else source
                        view.loadDataWithBaseURL(base, html, "text/html", "UTF-8", null)
                    }
                } catch (error: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
            continuation.invokeOnCancellation { mainHandler.post { webView?.stopLoading() } }
        }
    }

    fun close() {
        mainHandler.post { webView?.let { it.stopLoading(); it.destroy() }; webView = null }
    }

    private fun skillsAsset(skill: ElloSkill, path: String): ByteArray? = runCatching {
        if (skill.localDirectory.isNotBlank()) File(appContext.filesDir, path).readBytes()
        else appContext.assets.open(path).use { it.readBytes() }
    }.getOrNull()

    private fun getOrCreateWebView(): WebView = webView ?: WebView(appContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        webView = this
    }

    private class ResultBridge(private val onResult: (String) -> Unit) {
        @JavascriptInterface
        fun onResult(value: String) = onResult.invoke(value)
    }
}
