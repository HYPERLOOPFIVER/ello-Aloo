package com.ello.litertchat

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal data class GmailPerson(
    val name: String,
    val email: String,
    val messageCount: Int,
    val lastSeen: String,
)

/** Builds a small people list from Gmail message headers only; message bodies are never read. */
internal object GmailPeopleRepository {
    private const val CACHE_LIFETIME_MS = 10 * 60 * 1000L
    @Volatile private var cachedPeople: List<GmailPerson>? = null
    @Volatile private var cachedAt: Long = 0L
    @Volatile private var cachedAccount: String = ""
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
    private val metadataExecutor = Executors.newFixedThreadPool(4)
    private val mailboxRegex = Regex("(?:\\\"([^\\\"]*)\\\"|([^<]*?))?\\s*<([^<>]+)>|([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,})", RegexOption.IGNORE_CASE)
    private val automatedAddress = Regex("^(no[-_.]?reply|mailer[-_.]?daemon|notifications?|newsletter|updates?|alerts?)([+._-].*)?$", RegexOption.IGNORE_CASE)

    fun fetchRecent(context: Context, forceRefresh: Boolean = false): List<GmailPerson> {
        val token = GoogleApiAccess.requireGmailToken(context)
        val ownAddress = gmailGet(token, "https://gmail.googleapis.com/gmail/v1/users/me/profile".toHttpUrl())
            .optString("emailAddress").trim().lowercase(Locale.ROOT)
        check(ownAddress.isNotBlank()) { "Gmail did not return the connected account address." }
        cachedPeople?.takeIf {
            !forceRefresh && cachedAccount == ownAddress && System.currentTimeMillis() - cachedAt < CACHE_LIFETIME_MS
        }?.let { return it }
        // List newest-first without Gmail search syntax. This also works with the
        // narrower gmail.metadata scope, where Gmail disallows the `q` parameter.
        val listUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages".toHttpUrl().newBuilder()
            .addQueryParameter("maxResults", "100")
            .build()
        val messages = gmailGet(token, listUrl).optJSONArray("messages") ?: return emptyList()
        val futures = (0 until messages.length()).mapNotNull { index ->
            val id = messages.optJSONObject(index)?.optString("id")?.takeIf { it.matches(Regex("^[A-Za-z0-9_-]{1,200}$")) }
                ?: return@mapNotNull null
            metadataExecutor.submit<JSONObject> {
                val url = "https://gmail.googleapis.com/gmail/v1/users/me/messages".toHttpUrl().newBuilder()
                    .addPathSegment(id)
                    .addQueryParameter("format", "metadata")
                    .addQueryParameter("metadataHeaders", "From")
                    .addQueryParameter("metadataHeaders", "To")
                    .addQueryParameter("metadataHeaders", "Cc")
                    .addQueryParameter("metadataHeaders", "Date")
                    .build()
                gmailGet(token, url)
            }
        }

        data class SeenPerson(var name: String, val email: String, var count: Int, var lastSeen: String)
        val people = linkedMapOf<String, SeenPerson>()
        futures.forEach { future ->
            val message = future.get(30, TimeUnit.SECONDS)
            val headers = message.optJSONObject("payload")?.optJSONArray("headers") ?: return@forEach
            val fields = (0 until headers.length()).mapNotNull { index ->
                headers.optJSONObject(index)?.let { it.optString("name").lowercase(Locale.ROOT) to it.optString("value") }
            }.toMap()
            val date = fields["date"].orEmpty()
            val participants = listOf("from", "to", "cc").flatMap { field -> parseMailboxes(fields[field].orEmpty()) }
                .distinctBy { it.second }
            participants.forEach participantLoop@{ (headerName, email) ->
                if (email == ownAddress || automatedAddress.containsMatchIn(email.substringBefore('@'))) return@participantLoop
                val existing = people[email]
                val displayName = headerName.takeUnless { it.isBlank() || it.startsWith("=?") }
                    ?.trim()?.trim('"', '\'')
                    ?.takeIf(String::isNotBlank)
                    ?: email.substringBefore('@').replace(Regex("[._-]+"), " ").split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                if (existing == null) people[email] = SeenPerson(displayName, email, 1, date)
                else {
                    existing.count++
                    if (existing.name == email.substringBefore('@')) existing.name = displayName
                }
            }
        }
        val result = people.values
            .sortedWith(compareByDescending<SeenPerson> { it.count }.thenBy { it.name.lowercase(Locale.ROOT) })
            .take(20)
            .map { GmailPerson(it.name, it.email, it.count, it.lastSeen) }
        cachedPeople = result
        cachedAt = System.currentTimeMillis()
        cachedAccount = ownAddress
        return result
    }

    private fun parseMailboxes(header: String): List<Pair<String, String>> = mailboxRegex.findAll(header).mapNotNull { match ->
        val email = (match.groups[3]?.value ?: match.groups[4]?.value).orEmpty().trim().trim('<', '>').lowercase(Locale.ROOT)
        if (!email.contains('@')) return@mapNotNull null
        val name = (match.groups[1]?.value ?: match.groups[2]?.value).orEmpty().trim().trim('"', '\'', ',')
        name to email
    }.toList()

    private fun gmailGet(token: String, url: okhttp3.HttpUrl): JSONObject {
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) {
                val message = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
                message?.takeIf(String::isNotBlank) ?: "Gmail people could not be loaded (HTTP ${response.code})."
            }
            return if (body.isBlank()) JSONObject() else JSONObject(body)
        }
    }
}
