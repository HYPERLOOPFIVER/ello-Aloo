package com.ello.litertchat

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

internal data class OnboardingMessage(
    val sender: String,
    val text: String,
    val reaction: String? = null,
)

internal data class ConnectedGoogleAccount(
    val displayName: String,
    val email: String,
    val photoUrl: String?,
    val grantedScopes: List<String>,
)

internal data class SavedOnboardingProfile(
    val displayName: String,
    val email: String,
    val phone: String,
    val mascotName: String,
    val googleScopes: Set<String>,
)

internal object FirebaseOnboarding {
    private const val APP_NAME = "ello-onboarding"
    private const val FIREBASE_API_KEY = "AIzaSyCGPQcJCyjX6KwZ_z17T-j7Der5mM1HheU"
    private const val FIREBASE_APP_ID = "1:616056491630:web:acb24cf8febb117172a07f"
    private const val PROJECT_ID = "ello-744e4"
    private const val SENDER_ID = "616056491630"

    internal fun firebaseApp(context: Context): FirebaseApp {
        FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }?.let { return it }
        val options = FirebaseOptions.Builder()
            .setApiKey(FIREBASE_API_KEY)
            .setApplicationId(FIREBASE_APP_ID)
            .setProjectId(PROJECT_ID)
            .setGcmSenderId(SENDER_ID)
            .setStorageBucket("ello-744e4.firebasestorage.app")
            .build()
        return FirebaseApp.initializeApp(context, options, APP_NAME)
    }

    /** Reads onboarding data for the current Firebase identity without creating a new identity. */
    suspend fun loadSavedProfile(context: Context): SavedOnboardingProfile? {
        val user = FirebaseAuth.getInstance(firebaseApp(context)).currentUser ?: return null
        val snapshot = FirebaseFirestore.getInstance(firebaseApp(context))
            .collection("users")
            .document(user.uid)
            .get()
            .await()
        if (!snapshot.exists()) return null
        val localProfile = context.applicationContext.getSharedPreferences("ello_user_profile", Context.MODE_PRIVATE)
        val profile = SavedOnboardingProfile(
            displayName = snapshot.getString("displayName")?.takeIf(String::isNotBlank)
                ?: localProfile.getString("displayName", "").orEmpty(),
            email = snapshot.getString("email")?.takeIf(String::isNotBlank)
                ?: user.email?.takeIf(String::isNotBlank)
                ?: localProfile.getString("email", "").orEmpty(),
            phone = snapshot.getString("phone")?.takeIf(String::isNotBlank)
                ?: localProfile.getString("phone", "").orEmpty(),
            mascotName = snapshot.getString("mascotName")?.takeIf(String::isNotBlank)
                ?: localProfile.getString("mascotName", "").orEmpty(),
            googleScopes = (snapshot.get("googleGrantedScopes") as? List<*>)
                ?.mapNotNull { it as? String }
                ?.toSet()
                .orEmpty(),
        )
        localProfile.edit()
            .putString("displayName", profile.displayName)
            .putString("email", profile.email)
            .putString("phone", profile.phone)
            .putString("mascotName", profile.mascotName)
            .apply()
        return profile
    }

    /** Syncs editable onboarding profile fields to the current user's Firestore document. */
    suspend fun saveProfileDetail(context: Context, key: String, value: String) {
        require(key == "displayName" || key == "phone") { "This profile field is stored on this phone only." }
        val user = FirebaseAuth.getInstance(firebaseApp(context)).currentUser
            ?: error("Your profile is not connected yet. Your changes are saved on this phone.")
        FirebaseFirestore.getInstance(firebaseApp(context))
            .collection("users")
            .document(user.uid)
            .set(
                mapOf(key to value, "updatedAt" to FieldValue.serverTimestamp()),
                com.google.firebase.firestore.SetOptions.merge(),
            )
            .await()
    }

    /** Signs out this device's anonymous/profile session without deleting its remote Firestore record. */
    fun signOut(context: Context) {
        FirebaseAuth.getInstance(firebaseApp(context)).signOut()
    }

    suspend fun signInWithGoogleIdToken(context: Context, idToken: String): FirebaseUser {
        val auth = FirebaseAuth.getInstance(firebaseApp(context))
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val current = auth.currentUser
        val result = if (current?.isAnonymous == true) {
            current.linkWithCredential(credential).await()
        } else {
            auth.signInWithCredential(credential).await()
        }
        return result.user ?: error("Firebase sign-in did not return a user.")
    }

    /** Creates a stable Firebase identity before collecting onboarding data. */
    suspend fun ensureUser(context: Context): FirebaseUser {
        val auth = FirebaseAuth.getInstance(firebaseApp(context))
        auth.currentUser?.let { return it }
        return auth.signInAnonymously().await().user
            ?: error("Firebase sign-in did not return a user.")
    }

    /** Stores onboarding under the authenticated UID so later user data shares this identity. */
    suspend fun saveUserName(context: Context, name: String): String {
        val cleanName = name.trim().take(40)
        require(cleanName.isNotEmpty()) { "Enter your name to continue." }
        val user = ensureUser(context)
        val userRef = FirebaseFirestore.getInstance(firebaseApp(context))
            .collection("users")
            .document(user.uid)
        val profile = mutableMapOf<String, Any>(
            "uid" to user.uid,
            "displayName" to cleanName,
            "email" to (user.email ?: ""),
            "phone" to "",
            "onboardingComplete" to false,
            "updatedAt" to FieldValue.serverTimestamp(),
            "nameCapturedAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
        )
        userRef.set(profile, com.google.firebase.firestore.SetOptions.merge()).await()
        context.applicationContext.getSharedPreferences("ello_user_profile", Context.MODE_PRIVATE)
            .edit()
            .putString("displayName", cleanName)
            .apply()
        return user.uid
    }

    /** Saves the profile phone number without sending or verifying a code. */
    suspend fun savePhoneNumber(context: Context, phone: String) {
        val cleanPhone = phone.filter { it.isDigit() || it == '+' }
        require(cleanPhone.count(Char::isDigit) in 8..15) { "Enter a valid phone number with country code." }
        val user = ensureUser(context)
        FirebaseFirestore.getInstance(firebaseApp(context))
            .collection("users")
            .document(user.uid)
            .set(
                mapOf(
                    "uid" to user.uid,
                    "phone" to cleanPhone,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
            .await()
        context.applicationContext.getSharedPreferences("ello_user_profile", Context.MODE_PRIVATE)
            .edit()
            .putString("phone", cleanPhone)
            .apply()
    }

    /** Saves the user's chosen mascot name on the same profile and UID as their name. */
    suspend fun saveMascotName(context: Context, name: String) {
        val cleanName = name.trim().take(24)
        require(cleanName.isNotEmpty()) { "Choose a name for your Ello." }
        val user = FirebaseAuth.getInstance(firebaseApp(context)).currentUser
            ?: error("Your profile is not ready yet. Please try again.")
        FirebaseFirestore.getInstance(firebaseApp(context))
            .collection("users")
            .document(user.uid)
            .set(
                mapOf(
                    "uid" to user.uid,
                    "mascotName" to cleanName,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
            .await()
        context.applicationContext.getSharedPreferences("ello_user_profile", Context.MODE_PRIVATE)
            .edit()
            .putString("mascotName", cleanName)
            .apply()
    }

    suspend fun saveConnectedGoogleScopes(context: Context, scopes: Set<String>) {
        val user = FirebaseAuth.getInstance(firebaseApp(context)).currentUser ?: return
        val gmailConnected = scopes.any { it.contains("gmail.send", ignoreCase = true) }
        val calendarConnected = scopes.any { it.contains("calendar.events.owned", ignoreCase = true) }
        val tasksConnected = scopes.any { it.contains("/auth/tasks", ignoreCase = true) }
        val driveFileConnected = scopes.any { it.contains("drive.file", ignoreCase = true) }
        FirebaseFirestore.getInstance(firebaseApp(context))
            .collection("users")
            .document(user.uid)
            .set(
                mapOf(
                    "uid" to user.uid,
                    "googleGrantedScopes" to scopes.toList(),
                    "googleMailConnected" to gmailConnected,
                    "googleCalendarConnected" to calendarConnected,
                    "googleTasksConnected" to tasksConnected,
                    "googleDriveFileConnected" to driveFileConnected,
                    "googleSheetsConnected" to driveFileConnected,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
            .await()
    }

    suspend fun markOnboardingComplete(context: Context) {
        val user = ensureUser(context)
        FirebaseFirestore.getInstance(firebaseApp(context))
            .collection("users")
            .document(user.uid)
            .set(
                mapOf(
                    "uid" to user.uid,
                    "onboardingComplete" to true,
                    "onboardingCompletedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
            .await()
    }

    suspend fun saveCompletedOnboarding(
        context: Context,
        name: String,
        account: ConnectedGoogleAccount,
        messages: List<OnboardingMessage>,
    ) {
        val user = FirebaseAuth.getInstance(firebaseApp(context)).currentUser
            ?: error("Sign in with Google before saving onboarding.")
        val transcript = messages.mapIndexed { index, message ->
            mapOf(
                "order" to index,
                "sender" to message.sender,
                "text" to message.text,
                "reaction" to message.reaction,
            )
        }
        val profile = mapOf(
            "uid" to user.uid,
            "displayName" to name,
            "email" to (user.email ?: account.email),
            "photoUrl" to account.photoUrl,
            "googleDisplayName" to account.displayName,
            "googleMailConnected" to account.grantedScopes.any { it.contains("gmail", ignoreCase = true) },
            "googleCalendarConnected" to account.grantedScopes.any { it.contains("calendar", ignoreCase = true) },
            "googleTasksConnected" to account.grantedScopes.any { it.contains("/auth/tasks", ignoreCase = true) },
            "googleDriveFileConnected" to account.grantedScopes.any { it.contains("drive.file", ignoreCase = true) },
            "googleSheetsConnected" to account.grantedScopes.any { it.contains("drive.file", ignoreCase = true) },
            "googleGrantedScopes" to account.grantedScopes,
            "onboardingMessages" to transcript,
            "onboardingComplete" to true,
            "onboardingCompletedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        FirebaseFirestore.getInstance(firebaseApp(context))
            .collection("users")
            .document(user.uid)
            .set(profile, com.google.firebase.firestore.SetOptions.merge())
            .await()
    }
}
