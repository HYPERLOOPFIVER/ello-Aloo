package com.ello.litertchat

import android.content.Context

/** Explicit user memories live in the existing on-device Profile context store. */
internal object AgentProfileMemory {
    private const val PREFS = "ello_profile_context"
    private val categoryKeys = mapOf(
        "preferences" to "preferences",
        "rules" to "agentRules",
        "important_details" to "pastMistakes",
        "goals" to "goals",
        "routines" to "proactiveContext",
    )

    fun read(context: Context, category: String): Map<String, String> {
        val key = categoryKeys[category.trim().lowercase()]
            ?: error("Choose a memory category listed in the tool description.")
        val value = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, "").orEmpty().lines()
            .filter(String::isNotBlank)
            .filterNot(SENSITIVE::containsMatchIn)
            .joinToString("\n")
            .take(5000)
        return if (value.isBlank()) mapOf("memory" to "No shareable notes in this category.", "status" to "succeeded")
        else mapOf("memory" to value, "status" to "succeeded")
    }

    fun remember(context: Context, category: String, memory: String): Map<String, String> {
        val key = categoryKeys[category.trim().lowercase()]
            ?: error("Choose a memory category listed in the tool description.")
        val clean = memory.trim().replace(Regex("\\s+"), " ").take(1000)
        require(clean.isNotBlank()) { "Provide the exact preference or detail you want Ello to remember." }
        require(!SENSITIVE.containsMatchIn(clean)) { "Ello cannot store passwords, verification codes, payment-card data, or government identifiers as agent memory." }
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(key, "").orEmpty().lines().map(String::trim).filter(String::isNotBlank)
        val updated = (existing.filterNot { it.equals(clean, ignoreCase = true) } + clean).takeLast(MAX_NOTES)
        require(updated.joinToString("\n").length <= MAX_CATEGORY_CHARS) {
            "This category is full. Remove an old note in Profile before saving another one."
        }
        prefs.edit().putString(key, updated.joinToString("\n")).apply()
        return mapOf("result" to "Saved on this phone in Profile → ${categoryLabel(category)}.", "status" to "succeeded")
    }

    fun forget(context: Context, category: String, memory: String): Map<String, String> {
        val key = categoryKeys[category.trim().lowercase()]
            ?: error("Choose a memory category listed in the tool description.")
        val target = memory.trim().replace(Regex("\\s+"), " ")
        require(target.isNotBlank()) { "Provide the exact note you want Ello to forget." }
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(key, "").orEmpty().lines().map(String::trim).filter(String::isNotBlank)
        val remaining = existing.filterNot { it.equals(target, ignoreCase = true) }
        if (remaining.size == existing.size) return mapOf("result" to "No exact matching note was found in Profile.", "status" to "not_found")
        prefs.edit().putString(key, remaining.joinToString("\n")).apply()
        return mapOf("result" to "Removed that note from Profile on this phone.", "status" to "succeeded")
    }

    private fun categoryLabel(category: String): String = when (category.trim().lowercase()) {
        "preferences" -> "Your preferences"
        "rules" -> "Personal notes"
        "important_details" -> "Important details"
        "goals" -> "Goals"
        else -> "Routines"
    }

    private val SENSITIVE = Regex(
        "(?i)(password|passcode|one.?time.?code|verification.?code|cvv|cvc|credit.?card|card.?number|bank.?account|routing.?number|social.?security|passport.?number|national.?id|driver.?license|aadhaar)",
    )
    private const val MAX_NOTES = 20
    private const val MAX_CATEGORY_CHARS = 5000
}
