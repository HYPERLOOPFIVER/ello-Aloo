package com.ello.litertchat

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

/** Runs supported public, tier-one JSON, HTML, and embedded-JSON reads bundled by API Anything on-device. */
internal class ApiAnythingReadClient(context: Context, private val openBrowser: () -> Unit) {
    private val appContext = context.applicationContext
    private val assets = appContext.assets
    private val registryPrefs = appContext.getSharedPreferences("api_anything_settings", Context.MODE_PRIVATE)
    private val registrySites = ConcurrentHashMap<String, JSONObject>()
    private val registryDirectory = File(appContext.filesDir, "api_anything/registry")
    private val learnedDirectory = File(appContext.filesDir, "api_anything/learned")
    private val registryClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val registryBaseUrl = "https://api-anything-registry.forth-site.workers.dev"
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val bundledSites: Map<String, JSONObject> by lazy {
        assets.list("api_anything/sites").orEmpty()
            .filter { it.endsWith(".json", true) }
            .mapNotNull { filename -> runCatching {
                val site = assets.open("api_anything/sites/$filename").bufferedReader().use { JSONObject(it.readText()) }
                site.optString("name").lowercase() to site
            }.getOrNull() }
            .toMap()
    }

    private fun sites(): Map<String, JSONObject> {
        val persisted = runCatching {
            registryDirectory.listFiles { file -> file.isFile && file.extension.equals("json", true) }
                .orEmpty().mapNotNull { file -> runCatching {
                    val site = JSONObject(file.readText())
                    val name = site.optString("name").lowercase()
                    if (!name.matches(Regex("[a-z0-9][a-z0-9._-]{0,63}"))) null else name to site
                }.getOrNull() }.toMap()
        }.getOrDefault(emptyMap())
        val learned = runCatching {
            learnedDirectory.listFiles { file -> file.isFile && file.extension.equals("json", true) }
                .orEmpty().mapNotNull { file -> runCatching {
                    val site = JSONObject(file.readText())
                    val name = site.optString("name").lowercase()
                    if (!name.matches(Regex("[a-z0-9][a-z0-9._-]{0,63}"))) null else name to site
                }.getOrNull() }.toMap()
        }.getOrDefault(emptyMap())
        val shared = (persisted + registrySites).toMutableMap()
        learned.forEach { (name, site) ->
            val marked = JSONObject(site.toString())
            val markedOps = JSONArray()
            val ops = marked.optJSONArray("operations") ?: JSONArray()
            for (index in 0 until ops.length()) {
                val op = ops.optJSONObject(index) ?: continue
                markedOps.put(JSONObject(op.toString()).put("_elloLocalLearned", true))
            }
            marked.put("operations", markedOps)
            shared[name] = shared[name]?.let { mergeSiteSpecs(it, marked) } ?: marked
        }
        val combined = shared.toMutableMap()
        bundledSites.forEach { (name, bundled) ->
            val remote = shared[name]
            combined[name] = if (remote != null && isSharedCatalogEnabled()) mergeSiteSpecs(bundled, remote) else bundled
        }
        return combined
    }

    private fun mergeSiteSpecs(local: JSONObject, remote: JSONObject): JSONObject {
        val localDomain = local.optString("domain").ifBlank { runCatching { local.optString("baseUrl").toHttpUrl().host }.getOrDefault("") }
        val remoteDomain = remote.optString("domain").ifBlank { runCatching { remote.optString("baseUrl").toHttpUrl().host }.getOrDefault("") }
        val sameDomain = localDomain.equals(remoteDomain, true) ||
            localDomain.endsWith(".$remoteDomain", true) || remoteDomain.endsWith(".$localDomain", true)
        if (!sameDomain) return local
        val merged = JSONObject(local.toString())
        val operations = JSONArray()
        val byName = linkedMapOf<String, JSONObject>()
        fun addOps(site: JSONObject) {
            val list = site.optJSONArray("operations") ?: JSONArray()
            for (i in 0 until list.length()) {
                val op = list.optJSONObject(i) ?: continue
                val opName = op.optString("name").lowercase()
                if (opName.isBlank()) continue
                val existing = byName[opName]
                if (existing == null || op.optBoolean("_elloLocalLearned")) byName[opName] = JSONObject(op.toString())
            }
        }
        addOps(local)
        addOps(remote)
        byName.values.forEach(operations::put)
        merged.put("operations", operations)
        return merged
    }

    fun isSharedCatalogEnabled(): Boolean = registryPrefs.getBoolean("shared_catalog_enabled", false)

    fun setSharedCatalogEnabled(enabled: Boolean) {
        registryPrefs.edit().putBoolean("shared_catalog_enabled", enabled).apply()
    }

    fun siteSummary(): String = sites().values.joinToString(", ") { it.optString("name") }

    fun learnOperation(siteName: String, operationName: String, holdoutArgsJson: String): Map<String, String> {
        val site = siteName.trim().lowercase()
        val operation = operationName.trim()
        require(site.matches(Regex("[a-z0-9][a-z0-9._-]{0,63}"))) { "Use a simple API Anything site slug such as example-com." }
        require(operation.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}"))) { "Use a short operation name such as search or listEvents." }
        val samples = LocalBrowserSession.apiAnythingCaptureSamples()
        require(samples.size == 2) { "Capture two examples first: start capture, run a search, stop; then repeat with a different example." }
        val (pageOne, argsOne, urlsOne) = samples[0]
        val (pageTwo, argsTwo, urlsTwo) = samples[1]
        val holdoutArgs = runCatching { JSONObject(holdoutArgsJson) }.getOrElse { error("Holdout arguments must be a JSON object.") }
        require(argsOne.keys().asSequence().toSet() == argsTwo.keys().asSequence().toSet()) { "The two capture examples must use the same argument names." }
        val argNames = argsOne.keys().asSequence().toList()
        require(argNames.isNotEmpty() && argNames.all { argsOne.opt(it) is String && argsTwo.opt(it) is String && argsOne.optString(it) != argsTwo.optString(it) }) {
            "Each sample argument must be text and must have a different value in the two captures."
        }
        val sensitiveValue = Regex("(?i)(password|passwd|token|secret|authorization|session|credential|card.?number|cvv|aadhaar|passport|ssn)")
        require(holdoutArgs.length() == argNames.size && argNames.all {
            val value = holdoutArgs.optString(it)
            holdoutArgs.opt(it) is String && value.length >= 3 && !value.contains('@') && !sensitiveValue.containsMatchIn(value) &&
                !Regex("\\+?\\d[\\d ().-]{8,}\\d").containsMatchIn(value) &&
                value != argsOne.optString(it) && value != argsTwo.optString(it)
        }) {
            "The holdout must provide a new text value for every learned argument, different from both captured examples."
        }
        require(runCatching { java.net.URI(pageOne).host }.getOrNull().equals(runCatching { java.net.URI(pageTwo).host }.getOrNull(), true)) {
            "Both API Anything examples must come from the same website."
        }
        val candidates = mutableListOf<Pair<JSONObject, JSONArray>>()
        for (firstUrl in urlsOne) {
            val first = runCatching { firstUrl.toHttpUrl() }.getOrNull() ?: continue
            for (secondUrl in urlsTwo) {
                val second = runCatching { secondUrl.toHttpUrl() }.getOrNull() ?: continue
                if (first.scheme != second.scheme || first.host != second.host || first.port != second.port ||
                    first.encodedPathSegments.size != second.encodedPathSegments.size ||
                    first.queryParameterNames != second.queryParameterNames) continue
                val slots = JSONArray()
                val matchedLocations = mutableSetOf<String>()
                var valid = true
                for (param in argNames) {
                    val valueOne = argsOne.optString(param)
                    val valueTwo = argsTwo.optString(param)
                    val queryMatches = first.queryParameterNames.filter { key ->
                        first.queryParameterValues(key).contains(valueOne) && second.queryParameterValues(key).contains(valueTwo)
                    }.map { "query:" + it }
                    val pathMatches = first.pathSegments.indices.filter { index ->
                        first.pathSegments[index] == valueOne && second.pathSegments[index] == valueTwo
                    }.map { "path:" + it }
                    val locations = queryMatches + pathMatches
                    if (locations.size != 1 || !matchedLocations.add(locations.single())) { valid = false; break }
                    slots.put(JSONObject().put("param", param).put("at", JSONArray().put(locations.single())))
                }
                if (!valid) continue
                val changedLocations = buildSet {
                    first.queryParameterNames.filter { key ->
                        first.queryParameterValues(key) != second.queryParameterValues(key)
                    }.forEach { add("query:" + it) }
                    first.pathSegments.indices.filter { index ->
                        first.pathSegments[index] != second.pathSegments[index]
                    }.forEach { add("path:" + it) }
                }
                if (changedLocations != matchedLocations || slots.length() != argNames.size) continue
                candidates += JSONObject().put("method", "GET").put("url", first.toString()) to slots
            }
        }
        require(candidates.size == 1) {
            if (candidates.isEmpty()) "Ello could not find one public JSON GET request whose query or path values match both examples. Use a different public search flow or check the sample values."
            else "More than one request matched the examples. Narrow the capture to one search action and try again."
        }
        val requestSpec = candidates.single().first
        val slots = candidates.single().second
        val matchPathSegments = requestSpec.getString("url").toHttpUrl().encodedPathSegments.toMutableList()
        for (slotIndex in 0 until slots.length()) {
            val slot = slots.optJSONObject(slotIndex) ?: continue
            val at = slot.optJSONArray("at")?.optString(0).orEmpty()
            if (at.startsWith("path:")) {
                val pathIndex = at.substringAfter(':').toIntOrNull() ?: continue
                if (pathIndex in matchPathSegments.indices) matchPathSegments[pathIndex] = "*"
            }
        }
        val stableMatchPath = "/" + matchPathSegments.joinToString("/")
        val triggerPage = runCatching {
            val page = pageOne.toHttpUrl()
            require(page.isHttps && page.username.isBlank() && page.password.isBlank())
            val safePath = page.encodedPath.ifBlank { "/" }
            require(safePath.length <= 512 && !UNSAFE_TRIGGER_PATH.containsMatchIn(safePath))
            "${page.scheme}://${page.host}$safePath"
        }.getOrElse { error("The captured page is not a safe HTTPS trigger page.") }
        val holdoutOperation = JSONObject()
            .put("name", operation)
            .put("_elloLocalLearned", true)
            .put("description", "Read a public JSON result learned from two logged-out site examples and validated with a fresh holdout.")
            .put("request", requestSpec)
            .put("slots", slots)
            .put("readOnly", true)
            .put("learnedLoggedIn", false)
            .put("minTier", 1)
            .put("match", JSONObject().put("method", "GET").put("host", requestSpec.getString("url").toHttpUrl().host).put("path", stableMatchPath))
            .put("trigger", JSONObject().put("url", triggerPage))
            .put("response", JSONObject().put("format", "json"))
            .put("params", JSONArray().apply {
                for (param in argNames) put(JSONObject()
                    .put("name", param).put("type", "string").put("required", true)
                    .put("example", argsOne.optString(param)))
            })
        require(isCallable(holdoutOperation)) { "The captured request could not be represented as a public API Anything read." }
        val holdoutRequest = buildRequest(holdoutOperation, holdoutArgs)
        require(holdoutRequest.url.isHttps && holdoutRequest.method == "GET" && holdoutRequest.headers.size == 0) {
            "The learned holdout request must be an unsigned HTTPS GET."
        }
        val probe = client.newCall(holdoutRequest).execute().use { response ->
            require(response.isSuccessful) { "The independent holdout returned HTTP ${response.code}; the operation was not saved." }
            val contentType = response.header("content-type").orEmpty()
            require(contentType.contains("json", true)) { "The holdout did not return JSON; the operation was not saved." }
            val bytes = response.body?.source()?.readByteArray(MAX_RESPONSE_BYTES + 1L) ?: ByteArray(0)
            require(bytes.size <= MAX_RESPONSE_BYTES) { "The holdout response is too large; the operation was not saved." }
            val text = bytes.toString(Charsets.UTF_8)
            val parsed = runCatching { JSONTokener(text).nextValue() }.getOrNull()
                ?: error("The independent holdout returned invalid JSON; the operation was not saved.")
            require(!isEmptyResult(parsed)) { "The independent holdout returned no data; the operation was not saved." }
            parsed
        }
        val host = requestSpec.getString("url").toHttpUrl().host
        val pageHost = runCatching { java.net.URI(pageOne).host.orEmpty() }.getOrDefault("")
        require(pageHost.isNotBlank() && (host == pageHost || host.endsWith(".$pageHost") || pageHost.endsWith(".$host"))) {
            "The captured request is outside the website used for learning."
        }
        val learnedSite = JSONObject()
            .put("name", site)
            .put("displayName", site.replace('-', ' ').replaceFirstChar { it.uppercase() })
            .put("description", "Locally learned logged-out public reads")
            .put("domain", host)
            .put("baseUrl", "https://$host")
            .put("operations", JSONArray())
        val existing = runCatching { JSONObject(File(learnedDirectory, "$site.json").readText()) }.getOrNull()
        val merged = existing?.takeIf { it.optString("domain").equals(host, true) } ?: learnedSite
        val operations = merged.optJSONArray("operations") ?: JSONArray()
        val retained = JSONArray()
        for (i in 0 until operations.length()) {
            val current = operations.optJSONObject(i) ?: continue
            if (!current.optString("name").equals(operation, true)) retained.put(current)
        }
        retained.put(holdoutOperation)
        merged.put("operations", retained)
        learnedDirectory.mkdirs()
        val file = File(learnedDirectory, "$site.json")
        val temporary = File(learnedDirectory, "$site.json.tmp")
        temporary.writeText(merged.toString())
        require(temporary.renameTo(file)) { "The API Anything operation passed its holdout, but could not be saved on this phone." }
        LocalBrowserSession.clearApiAnythingCaptureSamples()
        val sampleCount = when (probe) { is JSONObject -> probe.length(); is JSONArray -> probe.length(); else -> 1 }
        return mapOf("site" to site, "operation" to operation, "holdout_items" to sampleCount.toString(), "storage" to "on this phone only; not shared", "status" to "succeeded")
    }

    fun findSites(query: String): Map<String, String> {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank).distinct()
        require(terms.isNotEmpty()) { "Say what website or type of information you want to search." }
        val localMatches = sites().values.mapNotNull { site ->
            val name = site.optString("name")
            val domain = runCatching { java.net.URI(site.optString("baseUrl")).host.orEmpty() }.getOrDefault("")
            val description = site.optString("description")
            val haystack = "$name ${site.optString("displayName")} $domain $description".lowercase()
            val score = terms.sumOf { term -> when {
                name.equals(term, true) || domain.equals(term, true) -> 8
                name.contains(term) || domain.contains(term) -> 4
                haystack.contains(term) -> 1
                else -> 0
            } }
            if (score == 0) null else JSONObject()
                .put("site", name)
                .put("domain", domain)
                .put("description", description.take(260))
                .put("score", score)
                .put("read_operations", countCallable(site))
        }.sortedWith(compareByDescending<JSONObject> { it.optInt("score") }.thenBy { it.optString("site") }).take(10)
        if (!isSharedCatalogEnabled()) return mapOf(
            "sites" to JSONArray(localMatches).toString(),
            "catalog" to "on-device catalog only; enable the shared catalog in App settings to search API Anything online",
            "status" to "succeeded",
        )
        val remote = runCatching { searchSharedCatalog(query) }.getOrElse {
            return mapOf("sites" to JSONArray(localMatches).toString(), "registry_error" to (it.localizedMessage ?: "Shared registry unavailable."), "status" to "succeeded")
        }
        val byName = linkedMapOf<String, JSONObject>()
        (localMatches + remote).forEach { match ->
            val name = match.optString("site").lowercase()
            val prior = byName[name]
            if (prior == null) {
                byName[name] = match
            } else {
                val operations = JSONArray()
                val opNames = mutableSetOf<String>()
                listOf(prior.optJSONArray("operations"), match.optJSONArray("operations")).forEach { list ->
                    for (i in 0 until (list?.length() ?: 0)) {
                        val op = list?.optJSONObject(i) ?: continue
                        if (opNames.add(op.optString("name").lowercase())) operations.put(JSONObject(op.toString()))
                    }
                }
                prior.put("operations", operations)
                prior.put("read_operations", maxOf(prior.optInt("read_operations"), operations.length()))
                prior.put("score", maxOf(prior.optInt("score"), match.optInt("score")))
                if (match.optBoolean("shared")) prior.put("shared", true)
            }
        }
        val combined = byName.values.toList()
            .sortedWith(compareByDescending<JSONObject> { it.optInt("score") }.thenBy { it.optString("site") }).take(10)
        return mapOf("sites" to JSONArray(combined).toString(), "catalog" to "on-device and API Anything shared catalog", "status" to "succeeded")
    }

    private fun searchSharedCatalog(query: String): List<JSONObject> {
        val url = ("$registryBaseUrl/v1/search?q=" + java.net.URLEncoder.encode(query.trim().take(200), Charsets.UTF_8.name()) + "&limit=10")
        val request = Request.Builder().url(url).get().header("Accept", "application/json").build()
        val response = registryClient.newCall(request).execute().use { result ->
            require(result.isSuccessful) { "API Anything shared registry returned HTTP ${result.code}." }
            val body = result.body ?: error("API Anything shared registry returned an empty response.")
            require(body.contentLength() in -1..(2L * 1024 * 1024)) { "API Anything shared catalog response is too large." }
            body.string()
        }
        val matches = JSONObject(response).optJSONArray("matches") ?: JSONArray()
        return (0 until matches.length()).mapNotNull { index ->
            val match = matches.optJSONObject(index) ?: return@mapNotNull null
            val name = match.optString("name")
            val domain = match.optString("domain")
            if (!name.matches(Regex("[a-z0-9][a-z0-9._-]{0,63}")) || !domain.matches(Regex("[a-z0-9.-]{1,253}"))) return@mapNotNull null
            val operations = JSONArray()
            val remoteOps = match.optJSONArray("operations") ?: JSONArray()
            for (i in 0 until remoteOps.length()) {
                val op = remoteOps.optJSONObject(i) ?: continue
                val status = op.optString("status")
                if (status !in setOf("live", "candidate", "degraded")) continue
                val remoteParams = op.optJSONArray("params") ?: JSONArray()
                val params = JSONArray()
                for (p in 0 until minOf(remoteParams.length(), 16)) {
                    val param = remoteParams.optJSONObject(p) ?: continue
                    params.put(JSONObject().put("name", param.optString("name")).put("type", param.optString("type", "string"))
                        .put("required", param.optBoolean("required", true)).put("description", param.optString("description"))
                        .put("hint", param.optString("hint")).put("example", param.opt("example") ?: JSONObject.NULL))
                }
                operations.put(JSONObject().put("name", op.optString("name")).put("status", status)
                    .put("confirmations", op.optInt("confirmations")).put("signature", op.optString("signature"))
                    .put("description", op.optString("description").take(500)).put("params", params))
            }
            if (operations.length() == 0) return@mapNotNull null
            val score = operations.length() * 2 + match.optJSONArray("matched")?.length().orZero()
            JSONObject().put("site", name).put("domain", domain).put("description", match.optString("description").take(260))
                .put("score", score).put("read_operations", operations.length()).put("shared", true).put("operations", operations)
        }
    }

    private fun Int?.orZero(): Int = this ?: 0

    fun listOperations(siteName: String): Map<String, String> {
        val name = siteName.trim().lowercase()
        val installed = sites()[name]
        val cachedFile = File(registryDirectory, "$name.json")
        val sharedSpecNeedsRefresh = installed == null || !cachedFile.exists() || cachedFile.lastModified() < System.currentTimeMillis() - 6 * 60 * 60 * 1000L
        if (isSharedCatalogEnabled() && sharedSpecNeedsRefresh) runCatching { pullSharedSite(name) }
        val site = sites()[name] ?: installed ?: error("Site '$name' is not in Ello's API Anything catalog.")
        val ops = site.optJSONArray("operations") ?: JSONArray()
        val available = JSONArray()
        for (i in 0 until ops.length()) {
            val op = ops.optJSONObject(i) ?: continue
            val params = op.optJSONArray("params") ?: JSONArray()
            val publicParams = JSONArray()
            for (p in 0 until params.length()) {
                val param = params.optJSONObject(p) ?: continue
                publicParams.put(JSONObject()
                    .put("name", param.optString("name"))
                    .put("type", param.optString("type", "string"))
                    .put("required", param.optBoolean("required", true))
                    .put("description", param.optString("description"))
                    .put("example", param.opt("example") ?: JSONObject.NULL))
            }
            available.put(JSONObject()
                .put("name", op.optString("name"))
                .put("description", op.optString("description").take(600))
                .put("method", op.optJSONObject("request")?.optString("method"))
                .put("response", op.optJSONObject("response")?.optString("format"))
                .put("supported", isCallable(op))
                .put("max_tier", if (isCallable(op)) 2 else JSONObject.NULL)
                .put("registry_status", op.optJSONObject("version")?.optString("status").orEmpty().ifBlank {
                    if (op.optBoolean("_elloLocalLearned")) "local-learned" else "bundled"
                })
                .put("trigger_url", safeTriggerUrl(op, site))
                .put("params", publicParams)
                .put("links", op.optJSONArray("links") ?: JSONArray())
                .put("paging", op.optJSONObject("paging") ?: JSONObject.NULL))
        }
        return mapOf(
            "site" to site.optString("name"),
            "operations" to available.toString(),
            "status" to "succeeded",
        )
    }

    private fun pullSharedSite(rawName: String): JSONObject {
        require(isSharedCatalogEnabled()) {
            "That site is not in Ello's on-device catalog. Enable the API Anything shared catalog in App settings to pull public site specs."
        }
        val name = rawName.trim().lowercase()
        require(name.matches(Regex("[a-z0-9][a-z0-9._-]{0,63}"))) { "That API Anything site name is invalid." }
        val request = Request.Builder().url("$registryBaseUrl/v1/sites/$name").get().header("Accept", "application/json").build()
        val raw = registryClient.newCall(request).execute().use { response ->
            require(response.isSuccessful) { if (response.code == 404) "No shared API Anything spec exists for '$name'." else "API Anything shared registry returned HTTP ${response.code}." }
            val body = response.body ?: error("API Anything shared registry returned an empty site spec.")
            require(body.contentLength() in -1..(2L * 1024 * 1024)) { "API Anything site spec is too large to install." }
            body.string()
        }
        val served = JSONObject(raw)
        require(served.optString("name").equals(name, true)) { "The shared registry returned a different site name." }
        val baseUrl = runCatching { served.optString("baseUrl").toHttpUrl() }.getOrElse { error("The shared site spec has an invalid base URL.") }
        require(baseUrl.isHttps && baseUrl.username.isBlank() && baseUrl.password.isBlank()) { "Shared API Anything specs must use an HTTPS base URL without embedded credentials." }
        val domain = served.optString("domain").lowercase()
        val baseHost = baseUrl.host.lowercase()
        require(domain.matches(Regex("[a-z0-9.-]{1,253}")) &&
            (baseHost == domain || baseHost.endsWith(".$domain"))) { "The shared site's base URL does not match its registered domain." }
        val operations = JSONArray()
        val servedOperations = served.optJSONArray("operations") ?: JSONArray()
        for (index in 0 until servedOperations.length()) {
            val entry = servedOperations.optJSONObject(index) ?: continue
            val status = entry.optString("status")
            if (status !in setOf("live", "candidate", "degraded")) continue
            val operation = entry.optJSONObject("operation")?.let { JSONObject(it.toString()) } ?: continue
            if (!isSafeSharedOperation(operation, domain)) continue
            operation.put("version", JSONObject().put("hash", entry.optString("hash")).put("status", status))
            operations.put(operation)
        }
        require(operations.length() > 0) { "The shared site has no public HTTPS read operations supported by Ello." }
        val site = JSONObject()
            .put("name", name)
            .put("displayName", served.optString("displayName", name))
            .put("description", served.optString("description"))
            .put("domain", domain)
            .put("baseUrl", baseUrl.toString())
            .put("operations", operations)
            .put("sharedSource", registryBaseUrl)
        registryDirectory.mkdirs()
        val target = File(registryDirectory, "$name.json")
        val temporary = File(registryDirectory, "$name.json.tmp")
        temporary.writeText(site.toString())
        require(temporary.renameTo(target)) { "Could not save the shared API Anything spec on this phone." }
        registrySites[name] = site
        return site
    }

    private fun isSafeSharedOperation(op: JSONObject, domain: String): Boolean {
        if (!op.optBoolean("readOnly", false) || op.optBoolean("learnedLoggedIn", true)) return false
        if (op.optInt("minTier", 3) !in 1..2) return false
        val request = op.optJSONObject("request") ?: return false
        val method = request.optString("method").uppercase()
        if (method !in setOf("GET", "POST")) return false
        if (method == "GET" && request.optString("body").isNotBlank()) return false
        if (request.optString("body").length > MAX_REQUEST_BODY_CHARS) return false
        val url = runCatching { request.optString("url").toHttpUrl() }.getOrNull() ?: return false
        if (!url.isHttps || url.username.isNotBlank() || url.password.isNotBlank()) return false
        val host = url.host.lowercase()
        if (host != domain && !host.endsWith(".$domain")) return false
        val forbiddenQueryName = Regex("(?i)(?:^|[_-])(token|secret|password|passwd|api.?key|auth|authorization|session|credential)(?:$|[_-])")
        if (url.queryParameterNames.any(forbiddenQueryName::containsMatchIn)) return false
        val queryValues = url.queryParameterNames.flatMap { key -> url.queryParameterValues(key).filterNotNull() }
        if (queryValues.any { value -> value.contains('@') || Regex("\\+?\\d[\\d ().-]{8,}\\d").containsMatchIn(value) }) return false
        val headers = request.optJSONObject("headers") ?: JSONObject()
        val forbiddenHeader = Regex("(?i)(cookie|authorization|proxy-authorization|api.?key|token|secret|password)")
        if (headers.keys().asSequence().any(forbiddenHeader::containsMatchIn)) return false
        val slots = op.optJSONArray("slots") ?: JSONArray()
        val paramNames = (0 until (op.optJSONArray("params")?.length() ?: 0))
            .mapNotNull { op.optJSONArray("params")?.optJSONObject(it)?.optString("name") }.toSet()
        for (i in 0 until slots.length()) {
            val slot = slots.optJSONObject(i) ?: return false
            val param = slot.optString("param")
            if (param.isBlank() || param !in paramNames || slot.has("ref") || slot.has("from") || slot.has("gen")) return false
            val slotRoot = slot.optJSONArray("at")?.optString(0).orEmpty()
            if (slotRoot == "header" || slotRoot == "form") return false
            if (method == "GET" && (slotRoot == "body" || slotRoot.startsWith("json:"))) return false
        }
        return isCallable(op)
    }

    fun callRead(siteName: String, operationName: String, rawArgs: String): Map<String, String> {
        val site = requireSite(siteName)
        val operations = site.optJSONArray("operations") ?: JSONArray()
        val op = (0 until operations.length()).mapNotNull(operations::optJSONObject)
            .firstOrNull { it.optString("name").equals(operationName, true) }
            ?: error("That API Anything operation is not in Ello's installed catalog.")
        require(isCallable(op)) {
            "That operation needs browser execution, a signed-in session, or unsupported response parsing. Use Ello's browser for this site."
        }
        val args = runCatching { JSONObject(rawArgs) }.getOrElse { error("Operation arguments must be a JSON object.") }
        validateArgs(op, args)
        val request = buildRequest(op, args)
        var tier = 1
        var httpStatus: Int
        var responseHeaders: okhttp3.Headers?
        var response = client.newCall(request).execute().use { result ->
            httpStatus = result.code
            responseHeaders = result.headers
            val body = result.body ?: error("The website returned an empty response.")
            val output = ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                var count: Int
                while (input.read(buffer).also { count = it } >= 0) {
                    if (output.size() + count > MAX_RESPONSE_BYTES) error("The website response is larger than Ello's API read limit.")
                    output.write(buffer, 0, count)
                }
            }
            output.toString(Charsets.UTF_8.name())
        }
        if (httpStatus in BROWSER_FALLBACK_STATUSES) {
            val browserResponse = runCatching {
                openBrowser()
                runBlocking(Dispatchers.IO) { JSONObject(LocalBrowserSession.apiAnythingBrowserRead(request, MAX_RESPONSE_BYTES)) }
            }.getOrNull()
            if (browserResponse != null && !browserResponse.has("error")) {
                val browserText = browserResponse.optString("text")
                if (browserText.length > MAX_RESPONSE_BYTES) error("The website response is larger than Ello's API read limit.")
                httpStatus = browserResponse.optInt("status", httpStatus)
                responseHeaders = okhttp3.Headers.headersOf("content-type", browserResponse.optString("contentType"))
                response = browserText
                tier = 2
            }
        }
        if (httpStatus !in 200..299) {
            val diagnosis = explainGate(httpStatus, response, responseHeaders)
            error("${site.optString("displayName", siteName)} returned HTTP $httpStatus${diagnosis?.let { ": $it" }.orEmpty()}. Use the visible browser to inspect the site response.")
        }
        val responseSpec = op.optJSONObject("response") ?: JSONObject()
        if (responseSpec.optString("format") == "html" && responseSpec.optJSONObject("html") == null) {
            val actualContentType = responseHeaders?.get("content-type").orEmpty()
            require(actualContentType.contains("text/plain", true) && !Regex("^\\s*<(?:!doctype|html|head|body)\\b", RegexOption.IGNORE_CASE).containsMatchIn(response)) {
                "The site returned HTML where this API Anything operation expected plain text. Inspect it in Ello's browser."
            }
        }
        val data: Any? = when (responseSpec.optString("format")) {
            "json" -> {
                val json = response.removePrefix(responseSpec.optString("xssiPrefix"))
                runCatching { JSONTokener(json).nextValue() }
                    .getOrElse { error("The site's response was not the JSON this operation expects. Use Ello's browser instead.") }
            }
            "html" -> {
                val recipe = responseSpec.optJSONObject("html")
                if (recipe == null) response else extractHtml(response, recipe, request.url.toString())
            }
            "embedded" -> {
                val regex = responseSpec.optJSONObject("embedded")?.optString("regex").orEmpty()
                require(regex.isNotBlank()) { "This API Anything embedded-JSON operation is missing its extraction pattern." }
                val match = runCatching { Regex(regex).find(response) }
                    .getOrElse { error("This API Anything embedded-JSON pattern is invalid.") }
                    ?: error("The page did not contain the embedded JSON this operation expects.")
                val start = match.groups[1]?.range?.first
                    ?: error("This API Anything pattern did not capture embedded JSON.")
                runCatching { JSONTokener(response.substring(start)).nextValue() }
                    .getOrElse { error("The page's embedded data was not valid JSON.") }
            }
            else -> error("This API Anything response format is not supported on this phone yet. Use Ello's browser instead.")
        }
        val extractPath = responseSpec.optString("extract").trim()
        val extracted = if (extractPath.isBlank()) data else data?.let { getPath(it, extractPath) }
        if (extractPath.isNotBlank() && extracted == null) {
            error("The site's response no longer matches the API Anything field path '$extractPath'. Inspect the current page in Ello's browser.")
        }
        val picked = project(extracted, responseSpec.optJSONArray("pick"))
        val pagination = paginationInfo(op, data, args)
        var resultNote: String? = null
        if (isEmptyResult(picked)) {
            val gate = explainGate(httpStatus, response, responseHeaders)
            if (gate != null) error("The site returned a page gate ($gate), not usable results. Open it in Ello's browser and continue there.")
            if (responseSpec.optString("format") != "json") {
                val emptySelector = responseSpec.optJSONObject("html")?.optString("empty").orEmpty()
                val showedEmptyState = emptySelector.isNotBlank() && runCatching { Jsoup.parse(response).selectFirst(emptySelector) != null }.getOrDefault(false)
                resultNote = if (showedEmptyState) "The page showed its configured no-results indicator."
                else "No rows were extracted; this could mean no matches or a changed page layout. Check the visible browser before concluding there are no results."
            }
        }
        val serialized = when (picked) {
            is JSONObject -> picked.toString()
            is JSONArray -> picked.toString()
            else -> JSONObject.wrap(picked)?.toString() ?: "null"
        }
        return mapOf(
            "site" to site.optString("name"),
            "operation" to op.optString("name"),
            "data" to serialized.take(MAX_RESULT_CHARS),
            "tier" to tier.toString(),
            "truncated" to (serialized.length > MAX_RESULT_CHARS).toString(),
            "note" to resultNote.orEmpty(),
            "status" to "succeeded",
        ) + pagination
    }

    private fun paginationInfo(op: JSONObject, completeResponse: Any?, args: JSONObject): Map<String, String> {
        val paging = op.optJSONObject("paging") ?: return emptyMap()
        val param = paging.optString("param").trim()
        if (param.isBlank()) return emptyMap()
        val nextExpression = paging.optString("next").trim()
        if (nextExpression.isBlank()) {
            val paramSpec = (0 until (op.optJSONArray("params")?.length() ?: 0))
                .mapNotNull { op.optJSONArray("params")?.optJSONObject(it) }
                .firstOrNull { it.optString("name") == param }
            val current = if (args.has(param)) args.optString(param) else paramSpec?.optString("default").orEmpty().ifBlank { "1" }
            val nextPage = current.toIntOrNull()?.plus(1)?.takeIf { it > 0 }?.toString()
            return buildMap {
                put("next_page_param", param)
                nextPage?.let { put("next_page", it) }
                put("has_more", "unknown")
            }
        }
        val rawPath = nextExpression.substringBefore('~')
        val raw = completeResponse?.let { getPath(it, rawPath) }
            ?: return mapOf("next_page_param" to param, "has_more" to "unknown")
        if (raw === JSONObject.NULL || raw == false || raw == "") return mapOf("next_page_param" to param, "has_more" to "false")
        val next = if (nextExpression.contains('~') && raw is String) {
            Regex(nextExpression.substringAfter('~')).find(raw)?.let { match -> match.groups[1]?.value ?: match.value }
        } else raw
        return when (next) {
            is String -> if (next.isBlank()) mapOf("next_page_param" to param, "has_more" to "false") else
                mapOf("next_page_param" to param, "next_page" to next, "has_more" to "true")
            is Number -> mapOf("next_page_param" to param, "next_page" to next.toString(), "has_more" to "true")
            else -> mapOf("next_page_param" to param, "has_more" to "unknown")
        }
    }

    private fun explainGate(status: Int, body: String, headers: okhttp3.Headers?): String? {
        val sample = body.take(200_000)
        val title = sample.take(20_000)
        val headerNames = headers?.names().orEmpty()
        if (headers?.get("cf-mitigated").equals("challenge", true) ||
            Regex("<title[^>]*>\\s*Just a moment\\.\\.\\.|cf-chl-|_cf_chl_opt|Attention Required! \\| Cloudflare", RegexOption.IGNORE_CASE).containsMatchIn(sample)
        ) return "Cloudflare verification challenge"
        if (Regex("bm-verify|/_sec/cp_challenge|errors\\.edgesuite\\.net|captcha-delivery\\.com|px-captcha|Press & Hold|awsWafCookieDomainList|gokuProps|Incapsula incident|<script id=[\"']anubis-main|Making sure you(?:'|&#39;)re not a bot|name=[\"']?js_challenge", RegexOption.IGNORE_CASE).containsMatchIn(sample)) {
            return "anti-automation challenge"
        }
        if (Regex("<title[^>]*>[^<]*(prove your humanity|human verification|just a moment|attention required|are you a (human|robot)|verify you are (a )?human|robot check)", RegexOption.IGNORE_CASE).containsMatchIn(title)) {
            return "human verification challenge"
        }
        if (Regex("log ?in to continue|sign ?in to continue|authentication required|session (has )?expired|type=[\"']password", RegexOption.IGNORE_CASE).containsMatchIn(sample)) {
            return "the site requires a signed-in session"
        }
        return when (status) {
            401 -> "the site requires authentication"
            403 -> "the site refused this unsigned request"
            429 -> "the site is rate limiting requests${headers?.get("retry-after")?.let { "; retry after $it" }.orEmpty()}"
            202, 405, 503 -> "the site may be applying an access challenge"
            else -> if (headerNames.any { it.equals("cf-mitigated", true) }) "the response was marked as a challenge" else null
        }
    }

    private fun isEmptyResult(value: Any?): Boolean = when (value) {
        null, JSONObject.NULL -> true
        is JSONArray -> value.length() == 0
        is JSONObject -> value.length() == 0
        is String -> value.isBlank()
        else -> false
    }

    private fun countCallable(site: JSONObject): Int {
        val operations = site.optJSONArray("operations") ?: return 0
        return (0 until operations.length()).count { index -> operations.optJSONObject(index)?.let(::isCallable) == true }
    }

    private fun isCallable(op: JSONObject): Boolean {
        if (!op.optBoolean("readOnly", false) || op.optBoolean("learnedLoggedIn", true) || op.optInt("minTier", 3) !in 1..2) return false
        val response = op.optJSONObject("response") ?: return false
        val supportedFormat = response.optString("format") == "json" ||
            (response.optString("format") == "html" && (response.optJSONObject("html") != null || response.optString("contentType").contains("text/plain", true))) ||
            (response.optString("format") == "embedded" && response.optJSONObject("embedded")?.optString("regex").orEmpty().isNotBlank())
        if (!supportedFormat) return false
        val slots = op.optJSONArray("slots") ?: JSONArray()
        for (i in 0 until slots.length()) {
            val slot = slots.optJSONObject(i) ?: continue
            if (slot.has("ref") && !slot.optString("ref").startsWith("cookie:", true)) return false
            if (slot.has("ref") && !isPublicCsrfCookie(slot.optString("ref").substringAfter(':'))) return false
            if (slot.has("gen")) return false
            if (slot.has("from")) {
                val from = slot.optJSONObject("from") ?: return false
                val lookup = from.optJSONObject("request") ?: return false
                if (!lookup.optString("method").equals("GET", true)) return false
                val mainHost = runCatching { op.getJSONObject("request").optString("url").toHttpUrl().host }.getOrNull()
                val lookupHost = runCatching { lookup.optString("url").toHttpUrl().host }.getOrNull()
                if (mainHost.isNullOrBlank() || mainHost != lookupHost) return false
                val lookupSlots = from.optJSONArray("slots") ?: JSONArray()
                for (j in 0 until lookupSlots.length()) {
                    val lookupSlot = lookupSlots.optJSONObject(j) ?: continue
                    if (!lookupSlot.has("param") || lookupSlot.has("ref") || lookupSlot.has("from") || lookupSlot.has("gen") ||
                        lookupSlot.optJSONArray("at")?.optString(0) == "b64") return false
                }
                if (from.optString("path").isBlank()) return false
            }
            if (slot.optJSONArray("at")?.optString(0) == "b64") return false
        }
        return true
    }

    private fun safeTriggerUrl(op: JSONObject, site: JSONObject): String {
        val raw = op.optJSONObject("trigger")?.optString("url").orEmpty()
        val trigger = runCatching { raw.toHttpUrl() }.getOrNull() ?: return ""
        val siteDomain = site.optString("domain").ifBlank {
            runCatching { site.optString("baseUrl").toHttpUrl().host }.getOrDefault("")
        }.lowercase()
        if (!trigger.isHttps || trigger.username.isNotBlank() || trigger.password.isNotBlank() ||
            (trigger.host != siteDomain && !trigger.host.endsWith(".$siteDomain") && !siteDomain.endsWith(".${trigger.host}"))) return ""
        if (trigger.encodedPath.length > 512 || UNSAFE_TRIGGER_PATH.containsMatchIn(trigger.encodedPath)) return ""
        return "${trigger.scheme}://${trigger.host}${trigger.encodedPath.ifBlank { "/" }}"
    }

    private fun isPublicCsrfCookie(name: String): Boolean =
        name.trim().matches(Regex("(?i)(?:csrf|xsrf|_csrf|csrf_token|xsrf_token|calypso_csrf_token|requestverificationtoken)"))

    private fun requireSite(siteName: String): JSONObject = sites()[siteName.trim().lowercase()]
        ?: error("Site '$siteName' is not in Ello's API Anything catalog. Use api_anything_find_sites first.")

    private fun validateArgs(op: JSONObject, args: JSONObject) {
        val params = op.optJSONArray("params") ?: JSONArray()
        for (i in 0 until params.length()) {
            val p = params.optJSONObject(i) ?: continue
            val name = p.optString("name")
            if (!args.has(name) || args.isNull(name)) {
                if (p.optBoolean("required", true) && !p.has("default")) error("Missing required argument '$name'.")
                if (p.has("default")) args.put(name, p.opt("default"))
                continue
            }
            val raw = args.opt(name) ?: error("Argument '$name' is missing.")
            val value = when (raw) {
                is String -> raw
                else -> raw.toString()
            }
            require(value.length <= MAX_ARG_LENGTH) { "Argument '$name' is too long." }
            val pattern = p.optString("pattern")
            if (pattern.isNotBlank()) require(runCatching { Regex(pattern).matches(value) }.getOrDefault(false)) {
                "Argument '$name' does not match the operation's expected format ${p.optString("hint")}."
            }
            val enum = p.optJSONArray("enum")
            if (enum != null) require((0 until enum.length()).any { enum.optString(it) == value }) { "Argument '$name' must be one of the operation's listed values." }
            if (p.has("min") || p.has("max")) {
                val number = value.toDoubleOrNull() ?: error("Argument '$name' must be a number.")
                require(!p.has("min") || number >= p.optDouble("min")) { "Argument '$name' is below the allowed minimum." }
                require(!p.has("max") || number <= p.optDouble("max")) { "Argument '$name' is above the allowed maximum." }
            }
        }
    }

    private fun buildRequest(op: JSONObject, args: JSONObject): Request {
        return buildRequest(op.getJSONObject("request"), op.optJSONArray("slots") ?: JSONArray(), args)
    }

    private fun buildRequest(spec: JSONObject, slots: JSONArray, args: JSONObject): Request {
        val headers = linkedMapOf<String, String>()
        val cookies = linkedMapOf<String, String>()
        val sourceHeaders = spec.optJSONObject("headers") ?: JSONObject()
        sourceHeaders.keys().forEach { name ->
            if (!name.equals("cookie", true) && !name.equals("authorization", true)) headers[name] = sourceHeaders.optString(name)
        }
        var url = spec.optString("url")
        var body = spec.optString("body").takeIf { spec.has("body") }
        for (i in 0 until slots.length()) {
            val slot = slots.optJSONObject(i) ?: continue
            val param = slot.optString("param")
            val reference = slot.optString("ref")
            val value: Any? = when {
                reference.startsWith("cookie:", true) -> {
                    val cookieName = reference.substringAfter(':').trim()
                    require(isPublicCsrfCookie(cookieName)) { "This API Anything operation refers to a cookie that Ello does not expose to its public-read runner." }
                    val cookieUrl = spec.optString("url").toHttpUrl().newBuilder().query(null).fragment(null).build().toString()
                    val cookieHeader = CookieManager.getInstance().getCookie(cookieUrl).orEmpty()
                    val cookieValue = cookieHeader.split(';').map(String::trim)
                        .firstOrNull { it.substringBefore('=').equals(cookieName, true) }
                        ?.substringAfter('=')
                    require(!cookieValue.isNullOrBlank()) { "API Anything needs the $cookieName site cookie. Open this site in Ello's browser, then retry." }
                    cookies[cookieName] = cookieValue
                    cookieValue
                }
                slot.has("from") -> {
                    val from = slot.getJSONObject("from")
                    val lookupRequest = buildRequest(
                        from.getJSONObject("request"),
                        from.optJSONArray("slots") ?: JSONArray(),
                        args,
                    )
                    val lookedUp = fetchJson(lookupRequest)
                    getPath(lookedUp, slot.optString("path"))
                        ?: error("The API Anything lookup response did not contain the required value.")
                }
                param.isNotBlank() -> args.opt(param)
                else -> continue
            }
            if (value == null || value === JSONObject.NULL) {
                if (slot.has("from")) error("The API Anything lookup returned no value for the next request.")
                continue
            }
            val at = slot.optJSONArray("at") ?: error("Invalid API Anything slot.")
            require(at.length() in 1..4) { "Unsupported slot path." }
            val root = at.optString(0)
            val rendered = renderTemplate(value, slot)
            when {
                root.startsWith("query:") -> {
                    val name = root.substringAfter(':')
                    val current = url.toHttpUrl()
                    url = current.newBuilder().setQueryParameter(name, rendered).build().toString()
                }
                root.startsWith("path:") -> {
                    val index = root.substringAfter(':').toIntOrNull() ?: error("Invalid path slot.")
                    val current = url.toHttpUrl()
                    val segments = current.encodedPathSegments.toMutableList()
                    require(index in segments.indices) { "Path slot is outside the saved request URL." }
                    val replacement = encodePath(rendered)
                    segments[index] = replacement
                    url = current.newBuilder().encodedPath("/" + segments.joinToString("/")).build().toString()
                }
                root.startsWith("header:") -> headers[root.substringAfter(':')] = rendered
                root.startsWith("json:") || root == "body" -> {
                    val pointer = if (root == "body") at.optString(1).takeIf { it.startsWith("json:") }?.substringAfter(':') else root.substringAfter(':')
                    if (pointer == null && root == "body") {
                        body = if (slot.has("template")) rendered else value.toString()
                    } else {
                        val rootJson = runCatching { JSONTokener(body ?: "{}").nextValue() }.getOrElse { JSONObject() }
                        val fullPointer = if (root == "body" && pointer == null) "" else pointer.orEmpty()
                        setJsonPointer(rootJson, fullPointer, if (slot.has("template")) rendered else value)
                        body = rootJson.toString()
                    }
                }
                else -> error("API Anything slot type '$root' is not supported by the phone runner.")
            }
        }
        if (cookies.isNotEmpty()) headers["Cookie"] = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        require(url.startsWith("https://", true)) { "API Anything catalog operations must use HTTPS." }
        val request = Request.Builder().url(url)
        headers.forEach { (key, value) -> request.header(key, value) }
        val method = spec.optString("method").uppercase()
        if (method == "GET") request.get()
        else if (method == "POST") {
            val mediaType = headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value?.toMediaTypeOrNull()
                ?: "application/json; charset=utf-8".toMediaTypeOrNull()
            request.post((body ?: "").toRequestBody(mediaType))
        } else error("Only GET and POST API Anything reads are supported.")
        return request.build()
    }

    private fun fetchJson(request: Request): Any {
        require(request.method == "GET" && request.url.isHttps) { "API Anything lookup requests must be HTTPS GET reads." }
        return client.newCall(request).execute().use { response ->
            val body = response.body ?: error("The API Anything lookup returned an empty response.")
            val output = ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buffer = ByteArray(8 * 1024)
                var count: Int
                while (input.read(buffer).also { count = it } >= 0) {
                    if (output.size() + count > MAX_RESPONSE_BYTES) error("The API Anything lookup response is too large.")
                    output.write(buffer, 0, count)
                }
            }
            val text = output.toString(Charsets.UTF_8.name()).trimStart().removePrefix(")]}'").trimStart()
            if (!response.isSuccessful) {
                val gate = explainGate(response.code, text, response.headers)
                error("The API Anything lookup failed with HTTP ${response.code}${gate?.let { ": $it" }.orEmpty()}.")
            }
            runCatching { JSONTokener(text).nextValue() }
                .getOrElse { error("The API Anything lookup returned invalid JSON.") }
        }
    }

    private fun renderTemplate(value: Any, slot: JSONObject): String {
        val text = transformed(value, slot.optString("transform"))
        if (!slot.has("template")) return text
        val replacement = when (slot.optString("escape")) {
            "url" -> java.net.URLEncoder.encode(text, Charsets.UTF_8.name()).replace("+", "%20")
            "json" -> JSONObject.quote(text)
            else -> text
        }
        return slot.optString("template").replace("{${slot.optString("param")}}", replacement)
    }

    private fun transformed(value: Any, transform: String): String {
        val text = value.toString()
        return when (transform) {
            "url-decode" -> runCatching { java.net.URLDecoder.decode(text, Charsets.UTF_8.name()) }.getOrDefault(text)
            "strip-quotes" -> text.removeSurrounding("\"")
            else -> text
        }
    }

    private fun encodePath(value: String): String = okhttp3.HttpUrl.Builder().scheme("https").host("ello.invalid").addPathSegment(value).build().encodedPathSegments.last()

    private fun setJsonPointer(root: Any, pointer: String, value: Any) {
        val tokens = pointer.split('/').drop(1).map { it.replace("~1", "/").replace("~0", "~") }
        if (tokens.isEmpty()) error("Replacing a whole JSON request body through a JSON slot is unsupported.")
        var current: Any = root
        for (token in tokens.dropLast(1)) current = when (current) {
            is JSONObject -> current.opt(token) ?: error("JSON request slot was not found.")
            is JSONArray -> current.opt(token.toIntOrNull() ?: error("Invalid JSON array slot.")) ?: error("JSON request slot was not found.")
            else -> error("JSON request slot does not point to a container.")
        }
        val last = tokens.last()
        when (current) {
            is JSONObject -> current.put(last, value)
            is JSONArray -> current.put(last.toIntOrNull() ?: error("Invalid JSON array slot."), value)
            else -> error("JSON request slot does not point to a container.")
        }
    }

    private fun getPath(root: Any, path: String): Any? {
        if (path.isBlank()) return root
        val tokens = mutableListOf<Any>()
        val tokenRegex = Regex("\\[(\\d+|\\*)\\]|\\[\"((?:[^\"\\\\]|\\\\.)*)\"\\]|([^.[\\]]+)")
        for (match in tokenRegex.findAll(path)) {
            val bracket = match.groups[1]?.value
            when {
                bracket == "*" -> tokens += "*"
                bracket != null -> tokens += (bracket.toIntOrNull() ?: return null)
                match.groups[2] != null -> {
                    val key = runCatching { JSONTokener("\"${match.groups[2]!!.value}\"").nextValue() as String }.getOrNull() ?: return null
                    tokens += key
                }
                else -> tokens += (match.groups[3]?.value ?: return null)
            }
        }
        if (tokens.isEmpty()) return root

        fun resolve(value: Any?, index: Int): Any? {
            if (index >= tokens.size) return value
            val token = tokens[index]
            if (value is String && value.trimStart().let { it.startsWith("{") || it.startsWith("[") }) {
                val decoded = runCatching { JSONTokener(value).nextValue() }.getOrNull()
                if (decoded != null) return resolve(decoded, index)
            }
            if (token == "*") {
                val array = value as? JSONArray ?: return null
                val result = JSONArray()
                for (i in 0 until array.length()) {
                    when (val nested = resolve(array.opt(i), index + 1)) {
                        null, JSONObject.NULL -> Unit
                        is JSONArray -> for (j in 0 until nested.length()) result.put(nested.opt(j))
                        else -> result.put(nested)
                    }
                }
                return result
            }
            val next = when (value) {
                is JSONObject -> {
                    val key = token as? String ?: return null
                    if (!value.has(key)) return null
                    value.opt(key)
                }
                is JSONArray -> {
                    val item = token as? Int ?: return null
                    if (item !in 0 until value.length()) return null
                    value.opt(item)
                }
                else -> return null
            }
            return resolve(next, index + 1)
        }
        return resolve(root, 0)
    }

    /** Implements API Anything's public HTML recipe: CSS-selected rows, text or @attribute fields. */
    private fun extractHtml(body: String, recipe: JSONObject, baseUrl: String): JSONArray {
        val document = Jsoup.parse(body, baseUrl)
        document.select("script,style,noscript,template,[hidden]").remove()
        document.select("[style]").filter { element ->
            Regex("(?:^|;)\\s*(?:display\\s*:\\s*none|visibility\\s*:\\s*hidden|visibility\\s*:\\s*collapse)", RegexOption.IGNORE_CASE)
                .containsMatchIn(element.attr("style"))
        }.forEach { it.remove() }
        val itemSelector = recipe.optString("items").trim()
        require(itemSelector.isNotBlank()) { "The API Anything HTML recipe has no item selector." }
        val fields = recipe.optJSONObject("fields") ?: error("The API Anything HTML recipe has no field selectors.")
        val rows = JSONArray()
        for (item in document.select(itemSelector).take(50)) {
            val row = JSONObject()
            val names = fields.keys()
            while (names.hasNext()) {
                val name = names.next()
                val field = fields.optString(name).trim()
                val all = field.startsWith("all:")
                val selector = if (all) field.removePrefix("all:").trim() else field
                val attributeMatch = Regex("^(.*?)@([\\w:-]+)$").matchEntire(selector)
                val css = attributeMatch?.groupValues?.get(1)?.trim().orEmpty().ifBlank { if (attributeMatch != null) "" else selector }
                val attribute = attributeMatch?.groupValues?.get(2)
                val selected = if (css.isBlank()) listOf(item) else item.select(css)
                if (all) {
                    val values = JSONArray()
                    selected.mapNotNull { extractFieldValue(it, attribute) }.forEach(values::put)
                    row.put(name, values)
                } else {
                    selected.firstOrNull()?.let { element ->
                        extractFieldValue(element, attribute)?.let { row.put(name, it) }
                    }
                }
            }
            rows.put(row)
        }
        return rows
    }

    private fun extractFieldValue(element: org.jsoup.nodes.Element, attribute: String?): String? {
        val raw = if (attribute == null) element.text().replace(Regex("\\s+"), " ").trim() else {
            if (!element.hasAttr(attribute)) return null
            if (attribute.equals("href", true) || attribute.equals("src", true)) element.absUrl(attribute).ifBlank { element.attr(attribute) }
            else element.attr(attribute)
        }
        return raw.takeIf(String::isNotBlank)
    }

    private fun project(value: Any?, picks: JSONArray?): Any? {
        if (picks == null || picks.length() == 0) return value
        val items = if (value is JSONArray) value else JSONArray().put(value)
        val out = JSONArray()
        for (i in 0 until minOf(items.length(), 25)) {
            val item = items.opt(i)
            val row = JSONObject()
            for (j in 0 until picks.length()) {
                val pick = picks.optString(j)
                val name = pick.substringBefore('=')
                val pathAndPattern = if (pick.contains('=')) pick.substringAfter('=') else pick
                val path = pathAndPattern.substringBefore('~')
                val found = getPath(item ?: continue, path)
                val value = if (pathAndPattern.contains('~') && found is String) {
                    Regex(pathAndPattern.substringAfter('~')).find(found)?.let { match -> match.groups[1]?.value ?: match.value }
                } else found
                value?.let { row.put(name, it) }
            }
            out.put(if (row.length() == 0) item else row)
        }
        return if (value is JSONArray) out else out.opt(0)
    }

    private companion object {
        val BROWSER_FALLBACK_STATUSES = setOf(202, 403, 405, 503)
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        const val MAX_RESULT_CHARS = 12_000
        const val MAX_ARG_LENGTH = 2_000
        const val MAX_REQUEST_BODY_CHARS = 64 * 1024
        val UNSAFE_TRIGGER_PATH = Regex("(?i)(?:^|/)(login|log-in|signin|sign-in|auth|account|checkout|payment|order|booking|reservation|appointment)(?:/|$)")
    }
}
