package com.ello.litertchat

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** User-entered form details. Values are encrypted with a non-exportable Android Keystore key. */
internal object PersonalFormVault {
    private const val PREFS = "ello_personal_vault"
    private const val ENTRY_INDEX = "entries"
    private const val KEY_ALIAS = "ello_personal_form_vault_v1"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private val forbiddenLabel = Regex(
        "(?i)(password|passcode|one.?time|otp|verification.?code|credit.?card|debit.?card|card.?number|cardholder|cvv|cvc|security.?code|bank.?account|routing.?number|social.?security|passport|tax.?id|national.?id|driver.?license|aadhaar|secret|token)",
    )
    private val sensitiveValue = Regex(
        "(?i)(password|passcode|one.?time.?code|verification.?code|credit.?card|card.?number|cvv|cvc|bank.?account|routing.?number|social.?security|passport.?number|national.?id|driver.?license|aadhaar|api.?key|access.?token)",
    )

    data class Entry(val id: String, val label: String)

    fun entries(context: Context): List<Entry> {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(ENTRY_INDEX, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            if (!id.matches(Regex("[a-f0-9-]{36}"))) return@mapNotNull null
            val record = prefs.getString("entry_$id", null)
            val label = record?.let { runCatching { JSONObject(decrypt(it, getOrCreateKey())).optString("label") }.getOrNull() }
            Entry(id, label?.takeIf(String::isNotBlank) ?: "Encrypted detail ${id.take(4)}")
        }
    }

    fun save(context: Context, id: String?, labelRaw: String, valueRaw: String): Entry {
        val app = context.applicationContext
        val label = labelRaw.trim().replace(Regex("\\s+"), " ").take(60)
        val value = valueRaw.trim().take(2000)
        require(label.length in 2..60) { "Give this saved detail a clear label between 2 and 60 characters." }
        require(!forbiddenLabel.containsMatchIn(label)) { "Payment details, credentials, verification codes, and government identifiers cannot be saved as browser-fill details." }
        require(!sensitiveValue.containsMatchIn(value) && !Regex("(?<!\\d)\\d{13,19}(?!\\d)").containsMatchIn(value)) {
            "This vault is for ordinary booking details. It cannot store passwords, payment-card data, or government identifiers."
        }
        require(value.isNotBlank()) { "Enter a value to save." }
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = entries(app).toMutableList()
        require(current.none { it.id != id && it.label.equals(label, true) }) { "A saved detail already uses that label." }
        val savedId = id ?: UUID.randomUUID().toString()
        val existing = current.indexOfFirst { it.id == savedId }
        val entry = Entry(savedId, label)
        if (existing >= 0) current[existing] = entry else current += entry
        val encrypted = encrypt(JSONObject().put("label", label).put("value", value).toString(), getOrCreateKey())
        prefs.edit()
            .putString("entry_$savedId", encrypted)
            .putString(ENTRY_INDEX, JSONArray().apply {
                current.takeLast(30).forEach { put(JSONObject().put("id", it.id)) }
            }.toString())
            .apply()
        return entry
    }

    fun remove(context: Context, id: String) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val remaining = entries(app).filterNot { it.id == id }
        prefs.edit().remove("entry_$id").putString(ENTRY_INDEX, JSONArray().apply {
            remaining.forEach { put(JSONObject().put("id", it.id)) }
        }.toString()).apply()
    }

    fun resolve(context: Context, profileField: String): Pair<String, String>? {
        val app = context.applicationContext
        val normalized = profileField.trim().lowercase().replace(Regex("\\s+"), " ")
        val standardKey = when (normalized) {
            "name" -> "displayName"
            "email" -> "email"
            "phone" -> "phone"
            "home_address", "home address" -> "homeAddress"
            "work_address", "work address" -> "workAddress"
            else -> null
        }
        if (standardKey != null) {
            val value = app.getSharedPreferences("ello_user_profile", Context.MODE_PRIVATE).getString(standardKey, "").orEmpty().trim()
            require(value.isNotBlank()) { "No saved ${normalized.replace('_', ' ')} is available. Add it in Profile first." }
            return normalized to value
        }
        val entry = entries(app).firstOrNull { it.label.equals(profileField.trim(), true) }
            ?: error("No saved form detail is labeled '${profileField.take(60)}'. Add that exact label in Profile first.")
        val encrypted = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("entry_${entry.id}", null)
            ?: error("The saved value for '${entry.label}' is missing. Edit it in Profile and save again.")
        val record = runCatching { JSONObject(decrypt(encrypted, getOrCreateKey())) }.getOrElse {
            error("The saved value for '${entry.label}' could not be decrypted. Edit it in Profile and save again.")
        }
        return entry.label to record.optString("value")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String, key: SecretKey): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String, key: SecretKey): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > IV_SIZE) { "Encrypted value is incomplete." }
        val iv = payload.copyOfRange(0, IV_SIZE)
        val ciphertext = payload.copyOfRange(IV_SIZE, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
