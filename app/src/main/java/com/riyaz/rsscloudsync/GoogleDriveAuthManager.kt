package com.riyaz.rsscloudsync

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task

object GoogleDriveAuthManager {
    const val PROVIDER = "Google Drive"
    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
    const val RC_SIGN_IN = 4201

    fun signInClient(context: Context) = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(DRIVE_SCOPE))
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
        return try {
            GoogleAuthUtil.getToken(context, account.account!!, "oauth2:$DRIVE_SCOPE")
        } catch (e: UserRecoverableAuthException) {
            throw e
        }
    }

    fun clearConnection(context: Context) {
        signInClient(context).signOut()
        context.getSharedPreferences("rss_cloud_sync", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("connected_cloud_providers", emptySet())
            .remove("google_drive_account_email")
            .remove("google_drive_account_name")
            .apply()
    }
}
