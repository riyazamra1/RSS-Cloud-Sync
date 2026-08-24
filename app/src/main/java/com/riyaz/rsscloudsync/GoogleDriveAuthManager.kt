package com.riyaz.rsscloudsync

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task

object GoogleDriveAuthManager {
    const val PROVIDER = "Google Drive"
    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"

    fun signInClient(context: Context) = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_SCOPE))
            .build()
    )

    fun accountFromResult(task: Task<GoogleSignInAccount>): GoogleSignInAccount? = try {
        task.getResult(ApiException::class.java)
    } catch (_: Exception) {
        null
    }

    fun currentAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun accessToken(context: Context, account: GoogleSignInAccount): String {
        val accountObject = account.account ?: throw IllegalStateException("Google account is unavailable")
        return try {
            GoogleAuthUtil.getToken(context, accountObject, "oauth2:$DRIVE_SCOPE")
        } catch (e: UserRecoverableAuthException) {
            throw e
        }
    }

    fun switchAccount(context: Context) {
        signInClient(context).signOut()
        context.getSharedPreferences("rss_cloud_sync", Context.MODE_PRIVATE)
            .edit()
            .remove("google_drive_account_email")
            .remove("google_drive_account_name")
            .remove("google_drive_target_folder_id")
            .remove("google_drive_target_folder_name")
            .apply()
    }

    fun clearConnection(context: Context) {
        switchAccount(context)
        val prefs = context.getSharedPreferences("rss_cloud_sync", Context.MODE_PRIVATE)
        val connected = (prefs.getStringSet("connected_cloud_providers", emptySet()) ?: emptySet()).toMutableSet()
        connected.remove(PROVIDER)
        prefs.edit().putStringSet("connected_cloud_providers", connected).apply()
    }
}
