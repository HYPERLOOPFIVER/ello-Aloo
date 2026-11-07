package com.ello.litertchat

import android.content.Context
import android.util.Base64
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.io.File
import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime
import java.util.Locale
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal const val GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send"
internal const val GMAIL_MODIFY_SCOPE = "https://www.googleapis.com/auth/gmail.modify"
internal const val GMAIL_SETTINGS_SCOPE = "https://www.googleapis.com/auth/gmail.settings.basic"
internal const val CALENDAR_EVENTS_SCOPE = "https://www.googleapis.com/auth/calendar.events.owned"
internal const val GOOGLE_TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"
internal const val GOOGLE_DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
internal const val GOOGLE_DRIVE_READ_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
internal const val GOOGLE_SHEETS_SCOPE = GOOGLE_DRIVE_FILE_SCOPE

/** Short-lived Google tokens are encrypted with an Android Keystore key and never sent to Firestore. */
internal object GoogleApiAccess {
    private const val TOKEN_LIFETIME_MS = 50 * 60 * 1000L
    private const val TOKEN_PREFS = "ello_google_access"
    private const val KEY_ALIAS = "ello_google_access_aes"
    @Volatile private var gmailToken: String? = null
    @Volatile private var gmailGrantedAt: Long = 0L
    @Volatile private var calendarToken: String? = null
    @Volatile private var calendarGrantedAt: Long = 0L
    @Volatile private var tasksToken: String? = null
    @Volatile private var tasksGrantedAt: Long = 0L
    @Volatile private var driveToken: String? = null
    @Volatile private var driveGrantedAt: Long = 0L
    @Volatile private var sheetsToken: String? = null
    @Volatile private var sheetsGrantedAt: Long = 0L
    @Volatile private var grantedScopes: Set<String> = emptySet()

    @Synchronized fun saveGmailToken(context: Context, token: String, scopes: Set<String>) {
        saveToken(context, "gmail", token, scopes)
        gmailToken = token
        gmailGrantedAt = System.currentTimeMillis()
        grantedScopes = grantedScopes + scopes
    }

    @Synchronized fun saveCalendarToken(context: Context, token: String, scopes: Set<String>) {
        saveToken(context, "calendar", token, scopes)
        calendarToken = token
        calendarGrantedAt = System.currentTimeMillis()
        grantedScopes = grantedScopes + scopes
    }

    @Synchronized fun saveTasksToken(context: Context, token: String, scopes: Set<String>) {
        saveToken(context, "tasks", token, scopes)
        tasksToken = token
        tasksGrantedAt = System.currentTimeMillis()
        grantedScopes = grantedScopes + scopes
    }

    @Synchronized fun saveDriveToken(context: Context, token: String, scopes: Set<String>) {
        saveToken(context, "drive", token, scopes)
        driveToken = token
        driveGrantedAt = System.currentTimeMillis()
        grantedScopes = grantedScopes + scopes
    }

    @Synchronized fun saveSheetsToken(context: Context, token: String, scopes: Set<String>) {
        saveToken(context, "sheets", token, scopes)
        sheetsToken = token
        sheetsGrantedAt = System.currentTimeMillis()
        grantedScopes = grantedScopes + scopes
    }

    @Synchronized fun allGrantedScopes(context: Context): Set<String> {
        restoreTokens(context)
        return grantedScopes
    }
    @Synchronized fun savePickedDriveFiles(context: Context, ids: Set<String>) {
        if (ids.isEmpty()) return
        val prefs = context.applicationContext.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet("drive_file_ids", emptySet())?.toSet().orEmpty()
        prefs.edit()
            .putBoolean("drive_picker_connected", true)
            .putStringSet("drive_file_ids", existing + ids.filter { it.matches(Regex("^[A-Za-z0-9_-]{10,200}$")) })
            .commit()
    }

    @Synchronized fun registerElloCreatedDriveFile(context: Context, id: String) {
        savePickedDriveFiles(context, setOf(id))
    }

    @Synchronized fun isDriveFileAvailableToEllo(context: Context, id: String): Boolean {
        restoreTokens(context)
        val prefs = context.applicationContext.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet("drive_file_ids", emptySet())?.contains(id) == true
    }

    @Synchronized fun gmailConnected(context: Context): Boolean {
        restoreTokens(context)
        return grantedScopes.containsAll(setOf(GMAIL_SEND_SCOPE, GMAIL_MODIFY_SCOPE))
    }
    @Synchronized fun calendarConnected(context: Context): Boolean { restoreTokens(context); return grantedScopes.contains(CALENDAR_EVENTS_SCOPE) }
    @Synchronized fun tasksConnected(context: Context): Boolean { restoreTokens(context); return grantedScopes.contains(GOOGLE_TASKS_SCOPE) }
    @Synchronized fun driveConnected(context: Context): Boolean {
        restoreTokens(context)
        return grantedScopes.contains(GOOGLE_DRIVE_READ_SCOPE)
    }
    @Synchronized fun sheetsConnected(context: Context): Boolean { restoreTokens(context); return grantedScopes.contains(GOOGLE_SHEETS_SCOPE) }
    @Synchronized fun clearAll(context: Context) {
        gmailToken = null
        gmailGrantedAt = 0L
        calendarToken = null
        calendarGrantedAt = 0L
        tasksToken = null
        tasksGrantedAt = 0L
        driveToken = null
        driveGrantedAt = 0L
        sheetsToken = null
        sheetsGrantedAt = 0L
        grantedScopes = emptySet()
        context.applicationContext.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Synchronized fun requireGmailToken(context: Context): String = requireServiceToken(context, "Gmail")
    @Synchronized fun requireGmailSettingsToken(context: Context): String {
        val token = requireGmailToken(context)
        check(allGrantedScopes(context).contains(GMAIL_SETTINGS_SCOPE)) {
            "Gmail mailbox access is connected, but settings access is not. Open Settings → Google services and tools → Gmail → Refresh access to enable filters, signatures, and vacation replies."
        }
        return token
    }
    @Synchronized fun requireCalendarToken(context: Context): String = requireServiceToken(context, "Google Calendar")
    @Synchronized fun requireTasksToken(context: Context): String = requireServiceToken(context, "Google Tasks")
    @Synchronized fun requireDriveToken(context: Context): String = requireServiceToken(context, "Google Drive")
    @Synchronized fun requireSheetsToken(context: Context): String = requireServiceToken(context, "Google Sheets")

    @Synchronized fun requireConnectorToken(context: Context, service: String): String = when (service.trim().lowercase()) {
        "gmail" -> requireGmailToken(context)
        "calendar" -> requireCalendarToken(context)
        "tasks" -> requireTasksToken(context)
        "drive" -> requireDriveToken(context)
        "sheets" -> requireSheetsToken(context)
        else -> throw IllegalArgumentException("Connect Gmail, Calendar, Tasks, Drive, or Sheets first.")
    }

    @Synchronized fun connectorScopes(context: Context): Set<String> = allGrantedScopes(context)

    private fun requireServiceToken(context: Context, service: String): String {
        restoreTokens(context)
        val (token, savedAt, requestedScopes) = when (service) {
            "Gmail" -> Triple(gmailToken, gmailGrantedAt, setOf(GMAIL_SEND_SCOPE, GMAIL_MODIFY_SCOPE))
            "Google Calendar" -> Triple(calendarToken, calendarGrantedAt, setOf(CALENDAR_EVENTS_SCOPE))
            "Google Tasks" -> Triple(tasksToken, tasksGrantedAt, setOf(GOOGLE_TASKS_SCOPE))
            "Google Drive" -> Triple(driveToken, driveGrantedAt, setOf(GOOGLE_DRIVE_READ_SCOPE))
            else -> Triple(sheetsToken, sheetsGrantedAt, setOf(GOOGLE_SHEETS_SCOPE))
        }
        if (valid(token, savedAt) && grantedScopes.containsAll(requestedScopes)) return token!!
        check(grantedScopes.containsAll(requestedScopes)) {
            "$service is not connected. Connect it from Settings → Google services and tools."
        }
        return try {
            val request = AuthorizationRequest.builder().setRequestedScopes(requestedScopes.map(::Scope)).build()
            val result = runBlocking(Dispatchers.IO) {
                Identity.getAuthorizationClient(context.applicationContext).authorize(request).await()
            }
            check(!result.hasResolution()) {
                "$service needs your approval again. Open Settings → Google services and tools to reconnect it."
            }
            val freshToken = result.accessToken?.takeIf(String::isNotBlank)
                ?: error("Google did not return a refreshed $service access token. Reconnect it in Settings.")
            when (service) {
                "Gmail" -> saveGmailToken(context, freshToken, result.grantedScopes.toSet())
                "Google Calendar" -> saveCalendarToken(context, freshToken, result.grantedScopes.toSet())
                "Google Tasks" -> saveTasksToken(context, freshToken, result.grantedScopes.toSet())
                "Google Drive" -> saveDriveToken(context, freshToken, result.grantedScopes.toSet())
                else -> saveSheetsToken(context, freshToken, result.grantedScopes.toSet())
            }
            freshToken
        } catch (failure: Exception) {
            throw IllegalStateException(
                "$service access needs attention. Open Settings → Google services and tools and reconnect it. ${failure.message.orEmpty()}",
                failure,
            )
        }
    }

    private fun saveToken(context: Context, service: String, token: String, scopes: Set<String>) {
        val encrypted = encrypt(token)
        val prefs = context.applicationContext.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        val existingScopes = prefs.getStringSet("granted_scopes", emptySet())?.toSet().orEmpty()
        prefs.edit()
            .putString("${service}_token", encrypted)
            .putLong("${service}_saved_at", System.currentTimeMillis())
            .putStringSet("granted_scopes", existingScopes + scopes + grantedScopes)
            .commit()
    }

    private fun restoreTokens(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        grantedScopes = prefs.getStringSet("granted_scopes", emptySet())?.toSet() ?: emptySet()
        if (gmailToken == null) {
            gmailToken = decrypt(prefs.getString("gmail_token", null))
            gmailGrantedAt = prefs.getLong("gmail_saved_at", 0L)
        }
        if (calendarToken == null) {
            calendarToken = decrypt(prefs.getString("calendar_token", null))
            calendarGrantedAt = prefs.getLong("calendar_saved_at", 0L)
        }
        if (tasksToken == null) {
            tasksToken = decrypt(prefs.getString("tasks_token", null))
            tasksGrantedAt = prefs.getLong("tasks_saved_at", 0L)
        }
        if (driveToken == null) {
            driveToken = decrypt(prefs.getString("drive_token", null))
            driveGrantedAt = prefs.getLong("drive_saved_at", 0L)
        }
        if (sheetsToken == null) {
            sheetsToken = decrypt(prefs.getString("sheets_token", null))
            sheetsGrantedAt = prefs.getLong("sheets_saved_at", 0L)
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String?): String? = runCatching {
        val packed = Base64.decode(value ?: return null, Base64.NO_WRAP)
        require(packed.size > GCM_IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, packed.copyOfRange(0, GCM_IV_BYTES)))
        String(cipher.doFinal(packed.copyOfRange(GCM_IV_BYTES, packed.size)), Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
        }.generateKey()
    }

    private fun valid(token: String?, grantedAt: Long) =
        !token.isNullOrBlank() && System.currentTimeMillis() - grantedAt < TOKEN_LIFETIME_MS

    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
}

