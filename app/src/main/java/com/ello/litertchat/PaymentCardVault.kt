package com.ello.litertchat

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.time.YearMonth
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Locally encrypted payment-card profile. Security codes are intentionally never accepted or stored. */
internal object PaymentCardVault {
    private const val PREFS = "ello_payment_vault"
    private const val ENTRY_INDEX = "entries"
    private const val KEY_ALIAS = "ello_payment_card_vault_v1"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128

    data class Entry(val id: String, val label: String, val lastFour: String)
    data class Details(val label: String, val cardholder: String, val number: String, val expiryMonth: String, val expiryYear: String)

    fun entries(context: Context): List<Entry> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = runCatching { JSONArray(prefs.getString(ENTRY_INDEX, "[]")) }.getOrDefault(JSONArray())
        return (0 until ids.length()).mapNotNull { index ->
            val id = ids.optString(index)
            if (!id.matches(Regex("[a-f0-9-]{36}"))) return@mapNotNull null
            val encrypted = prefs.getString("entry_$id", null) ?: return@mapNotNull null
            runCatching {
                val details = JSONObject(decrypt(encrypted, getOrCreateKey(), id))
                Entry(id, details.optString("label"), details.optString("number").takeLast(4))
            }.getOrNull()?.takeIf { it.label.isNotBlank() && it.lastFour.length == 4 }
        }
    }

    fun details(context: Context, id: String): Details? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encrypted = prefs.getString("entry_$id", null) ?: return null
        val data = runCatching { JSONObject(decrypt(encrypted, getOrCreateKey(), id)) }.getOrNull() ?: return null
        return Details(data.optString("label"), data.optString("cardholder"), data.optString("number"), data.optString("month"), data.optString("year"))
    }

    fun save(context: Context, id: String?, labelRaw: String, holderRaw: String, numberRaw: String, expiryRaw: String): Entry {
        val label = labelRaw.trim().replace(Regex("\\s+"), " ").take(40)
        val holder = holderRaw.trim().replace(Regex("\\s+"), " ").take(60)
        val number = numberRaw.filter(Char::isDigit)
        require(label.length in 2..40) { "Give this card a recognizable label." }
        require(label.matches(Regex("[A-Za-z][A-Za-z0-9 _-]{1,39}")) &&
            !Regex("(?i)(password|security|cvv|cvc|account|routing|token|secret|ssn)").containsMatchIn(label) &&
            !Regex("\\d{7,}").containsMatchIn(label)) { "Use a short nickname for this payment method, without contact, account, or card numbers." }
        require(holder.length in 2..60 && holder.all { it.isLetter() || it.isWhitespace() || it in "'.-" }) { "Enter the cardholder name shown on the card." }
        require(number.length in 13..19 && passesLuhn(number)) { "Enter a valid card number." }
        val expiry = parseExpiry(expiryRaw)
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = entries(app)
        require(existing.size < 8 || (id != null && existing.any { it.id == id })) { "Remove a saved payment method before adding another." }
        require(existing.none { it.id != id && it.label.equals(label, true) }) { "A saved payment method already has that label." }
        val savedId = id ?: UUID.randomUUID().toString()
        val payload = JSONObject()
            .put("label", label)
            .put("cardholder", holder)
            .put("number", number)
            .put("month", expiry.first)
            .put("year", expiry.second)
        val encrypted = encrypt(payload.toString(), getOrCreateKey(), savedId)
        val newIds = existing.map { it.id }.toMutableList().apply { if (savedId !in this) add(savedId) }
        check(prefs.edit().putString("entry_$savedId", encrypted)
            .putString(ENTRY_INDEX, JSONArray(newIds.takeLast(8)).toString()).commit()) {
            "The encrypted payment method could not be saved on this phone."
        }
        return Entry(savedId, label, number.takeLast(4))
    }

    fun remove(context: Context, id: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = entries(context).map { it.id }.filterNot { it == id }
        check(prefs.edit().remove("entry_$id").putString(ENTRY_INDEX, JSONArray(ids).toString()).commit()) {
            "The encrypted payment method could not be removed."
        }
    }

    fun resolve(context: Context, label: String, field: String): String {
        val entry = entries(context).firstOrNull { it.label.equals(label.trim(), true) }
            ?: error("No saved payment method has that exact label. Add it in Profile first.")
        val data = details(context, entry.id) ?: error("This payment method could not be decrypted. Edit it in Profile and save again.")
        return when (field) {
            "card_number" -> data.number
            "cardholder" -> data.cardholder
            "expiry" -> "${data.expiryMonth}/${data.expiryYear.takeLast(2)}"
            "expiry_month" -> data.expiryMonth
            "expiry_year" -> data.expiryYear
            else -> error("Choose a supported payment field.")
        }
    }

    private fun parseExpiry(raw: String): Pair<String, String> {
        val match = Regex("^\\s*(0?[1-9]|1[0-2])\\s*[/.-]\\s*(\\d{2}|\\d{4})\\s*$").matchEntire(raw)
            ?: error("Enter the expiry as MM/YY or MM/YYYY.")
        val month = match.groupValues[1].toInt()
        val yearInput = match.groupValues[2]
        val year = if (yearInput.length == 2) 2000 + yearInput.toInt() else yearInput.toInt()
        val thisMonth = YearMonth.now()
        require(YearMonth.of(year, month) >= thisMonth && year <= thisMonth.year + 20) { "This card expiry is invalid or has passed." }
        return month.toString().padStart(2, '0') to year.toString()
    }

    private fun passesLuhn(number: String): Boolean {
        var sum = 0
        var double = false
        for (index in number.indices.reversed()) {
            var digit = number[index].digitToInt()
            if (double) { digit *= 2; if (digit > 9) digit -= 9 }
            sum += digit
            double = !double
        }
        return sum % 10 == 0
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    private fun encrypt(value: String, key: SecretKey, id: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String, key: SecretKey, id: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > IV_SIZE) { "Encrypted card details are incomplete." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, payload.copyOfRange(0, IV_SIZE)))
        cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(payload.copyOfRange(IV_SIZE, payload.size)), Charsets.UTF_8)
    }
}
