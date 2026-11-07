package com.ello.litertchat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/** On-device people explicitly saved by the user/Ello and discovered in Gmail headers. */
internal data class SavedPerson(
    val id: String,
    val name: String,
    val email: String = "",
    val phone: String = "",
    val relationship: String = "",
    val note: String = "",
    val sources: Set<String> = emptySet(),
    val sourceContext: String = "",
    val messageCount: Int = 0,
    val lastSeen: String = "",
)

internal object ProfilePeopleStore {
    private const val PREFS = "ello_user_profile"
    private const val KEY = "linkedPeople"
    private const val HIDDEN_KEY = "hiddenGmailPeople"

    fun load(context: Context): List<SavedPerson> = read(context)
        .sortedWith(compareBy<SavedPerson> { it.name.lowercase(Locale.ROOT) })

    fun mergeGmail(context: Context, gmailPeople: List<GmailPerson>): List<SavedPerson> {
        val current = read(context).toMutableList()
        val hidden = hiddenEmails(context)
        val seenIds = mutableSetOf<String>()
        gmailPeople.filterNot { hidden.contains(it.email.trim().lowercase(Locale.ROOT)) }.forEach { gmail ->
            val matchIndex = current.indexOfFirst { matches(it, gmail.email, gmail.name) }
            val existing = current.getOrNull(matchIndex)
            val id = existing?.id ?: UUID.randomUUID().toString()
            seenIds += id
            val sources = existing?.sources.orEmpty() + "gmail"
            val keepUserName = existing != null && ("user" in existing.sources || "conversation" in existing.sources)
            val merged = SavedPerson(
                id = id,
                name = if (keepUserName && existing!!.name.isNotBlank()) existing.name else gmail.name,
                email = existing?.email?.takeIf { "user" in sources && it.isNotBlank() } ?: gmail.email,
                phone = existing?.phone.orEmpty(),
                relationship = existing?.relationship.orEmpty(),
                note = existing?.note.orEmpty(),
                sources = sources,
                sourceContext = existing?.sourceContext?.ifBlank { "Found in Gmail" } ?: "Found in Gmail",
                messageCount = gmail.messageCount,
                lastSeen = gmail.lastSeen,
            )
            if (matchIndex >= 0) current[matchIndex] = merged else current += merged
        }
        // Drop stale Gmail-only rows; preserve people also saved by the user or from chat.
        val retained = current.filter { person ->
            "gmail" !in person.sources || "gmail" in person.sources && "user" in person.sources ||
                "conversation" in person.sources || person.id in seenIds
        }
        return write(context, retained)
    }

    fun upsertUserPerson(
        context: Context,
        name: String,
        email: String = "",
        phone: String = "",
        relationship: String = "",
        note: String = "",
        existingId: String? = null,
    ): List<SavedPerson> = upsert(context, name, email, phone, relationship, note, "user", existingId)

    fun upsertConversationPerson(
        context: Context,
        name: String,
        email: String = "",
        phone: String = "",
        relationship: String = "",
        note: String = "",
        sourceContext: String = "",
    ): SavedPerson {
        require(name.trim().isNotBlank()) { "A person's name is required." }
        upsert(context, name, email, phone, relationship, note, "conversation", sourceContext = sourceContext)
        return read(context).first { matches(it, email, name) }
    }

    fun delete(context: Context, id: String): List<SavedPerson> {
        val current = read(context)
        val removed = current.firstOrNull { it.id == id }
        if (removed != null && "gmail" in removed.sources && removed.email.isNotBlank()) {
            val hidden = hiddenEmails(context) + removed.email.lowercase(Locale.ROOT)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(HIDDEN_KEY, hidden).apply()
        }
        val remaining = current.filterNot { it.id == id }
        return write(context, remaining)
    }

    private fun upsert(
        context: Context,
        name: String,
        email: String,
        phone: String,
        relationship: String,
        note: String,
        source: String,
        existingId: String? = null,
        sourceContext: String = "",
    ): List<SavedPerson> {
        val cleanName = name.trim().take(120)
        require(cleanName.isNotBlank()) { "A person's name is required." }
        val cleanEmail = email.trim().take(254)
        val current = read(context).toMutableList()
        val index = current.indexOfFirst { person ->
            if (existingId != null && person.id == existingId) true else matches(person, cleanEmail, cleanName)
        }
        val old = current.getOrNull(index)
        if (source == "user" && existingId != null && old != null && "gmail" in old.sources &&
            old.email.isNotBlank() && !old.email.equals(cleanEmail, ignoreCase = true)
        ) {
            val hidden = hiddenEmails(context) + old.email.lowercase(Locale.ROOT)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(HIDDEN_KEY, hidden).apply()
        }
        val updated = SavedPerson(
            id = old?.id ?: UUID.randomUUID().toString(),
            name = cleanName,
            email = if (source == "user" && existingId != null) cleanEmail else cleanEmail.ifBlank { old?.email.orEmpty() },
            phone = if (source == "user" && existingId != null) phone.trim().take(60) else phone.trim().take(60).ifBlank { old?.phone.orEmpty() },
            relationship = if (source == "user" && existingId != null) relationship.trim().take(120) else relationship.trim().take(120).ifBlank { old?.relationship.orEmpty() },
            note = if (source == "user" && existingId != null) note.trim().take(1000) else note.trim().take(1000).ifBlank { old?.note.orEmpty() },
            sources = old?.sources.orEmpty() + source,
            sourceContext = sourceContext.trim().take(500).ifBlank { old?.sourceContext.orEmpty() }.ifBlank { if (source == "conversation") "Learned in conversation" else "Added by you" },
            messageCount = old?.messageCount ?: 0,
            lastSeen = old?.lastSeen.orEmpty(),
        )
        if (index >= 0) current[index] = updated else current += updated
        if (updated.email.isNotBlank()) {
            val hidden = hiddenEmails(context).toMutableSet()
            hidden.remove(updated.email.lowercase(Locale.ROOT))
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(HIDDEN_KEY, hidden).apply()
        }
        return write(context, current)
    }

    private fun hiddenEmails(context: Context): Set<String> = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getStringSet(HIDDEN_KEY, emptySet())
        .orEmpty()

    private fun matches(person: SavedPerson, email: String, name: String): Boolean {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        if (normalizedEmail.isNotBlank()) {
            if (person.email.equals(normalizedEmail, ignoreCase = true)) return true
            if (person.email.isNotBlank()) return false
        }
        return person.name.trim().equals(name.trim(), ignoreCase = true)
    }

    private fun read(context: Context): List<SavedPerson> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name").trim()
                if (name.isBlank()) return@mapNotNull null
                val sourcesJson = item.optJSONArray("sources")
                val sources = if (sourcesJson == null) {
                    setOf(item.optString("source").ifBlank { "user" })
                } else (0 until sourcesJson.length()).map { sourcesJson.optString(it) }.filter(String::isNotBlank).toSet()
                SavedPerson(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    email = item.optString("email"),
                    phone = item.optString("phone"),
                    relationship = item.optString("relationship"),
                    note = item.optString("note"),
                    sources = sources,
                    sourceContext = item.optString("sourceContext"),
                    messageCount = item.optInt("messageCount"),
                    lastSeen = item.optString("lastSeen"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun write(context: Context, people: List<SavedPerson>): List<SavedPerson> {
        val array = JSONArray()
        people.forEach { person ->
            array.put(JSONObject()
                .put("id", person.id)
                .put("name", person.name)
                .put("email", person.email)
                .put("phone", person.phone)
                .put("relationship", person.relationship)
                .put("note", person.note)
                .put("sources", JSONArray(person.sources.toList()))
                .put("sourceContext", person.sourceContext)
                .put("messageCount", person.messageCount)
                .put("lastSeen", person.lastSeen))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
        return people.sortedWith(compareBy<SavedPerson> { it.name.lowercase(Locale.ROOT) })
    }
}
