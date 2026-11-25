package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "Auth"

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        Log.w(TAG, "default_web_client_id unavailable for silent sign-in", e)
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Silent auto sign-in skipped: ${e.message}")
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    val activity = context as? Activity
    if (activity == null) {
        onAuthError("Unable to start Google Sign-In from non-activity context")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Google Sign-In failed. Please try again.")
        }
    }
}

/** Minimum age allowed to use the app. */
const val MINIMUM_AGE = 13

private val USERNAME_PATTERN = Regex("^[A-Za-z0-9_.]{3,20}$")

/** Returns an error message for an invalid username, or null when it is valid. */
fun validateUsername(raw: String): String? {
    val name = raw.trim()
    return when {
        name.length < 3 -> "Username must be at least 3 characters."
        name.length > 20 -> "Username must be 20 characters or fewer."
        !USERNAME_PATTERN.matches(name) -> "Use only letters, numbers, dots, and underscores."
        else -> null
    }
}

/**
 * Age in whole years for a birthday given as UTC-midnight millis (what the Material3 DatePicker
 * returns), measured against today's local date. Uses Calendar because minSdk is 24 (no java.time).
 */
fun ageFromBirthdayMillis(birthdayUtcMillis: Long, now: Calendar = Calendar.getInstance()): Int {
    val birth = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = birthdayUtcMillis
    }
    var age = now.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
    val nowMonth = now.get(Calendar.MONTH)
    val birthMonth = birth.get(Calendar.MONTH)
    if (nowMonth < birthMonth ||
        (nowMonth == birthMonth && now.get(Calendar.DAY_OF_MONTH) < birth.get(Calendar.DAY_OF_MONTH))
    ) {
        age--
    }
    return age
}

/**
 * True while a new account is being created. Anonymous sign-in flips the auth state before the
 * username is attached, so the app root keeps showing the auth screen until this clears —
 * otherwise the profile would be created with the default "Polyglot Learner" name.
 */
object AccountSetupGate {
    val isPending = MutableStateFlow(false)
}

/**
 * Creates an account backed by an anonymous Firebase user and attaches [username] as its display
 * name. The birthday is checked on-device only and is never stored. Requires the "Anonymous"
 * provider in Firebase Console → Authentication → Sign-in method. The account can later be
 * upgraded to Google (or another provider) with linkWithCredential() without losing progress.
 */
fun onCreateAccountClicked(
    username: String,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope
) {
    scope.launch {
        AccountSetupGate.isPending.value = true
        try {
            val user = Firebase.auth.signInAnonymously().await().user
                ?: error("Account creation returned no user.")
            try {
                user.updateProfile(
                    userProfileChangeRequest { displayName = username.trim() }
                ).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The account exists; it just falls back to the default name.
                Log.w(TAG, "Account created but saving the username failed", e)
            }
            onAuthSuccess()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Account creation failed", e)
            val msg = e.localizedMessage.orEmpty()
            onAuthError(
                if ("ADMIN_ONLY_OPERATION" in msg || "OPERATION_NOT_ALLOWED" in msg || "restricted" in msg)
                    "Account creation is not enabled. Turn on the Anonymous provider in Firebase Console → Authentication → Sign-in method."
                else msg.ifBlank { "Could not create your account. Please try again." }
            )
        } finally {
            AccountSetupGate.isPending.value = false
        }
    }
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
