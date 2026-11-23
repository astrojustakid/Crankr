package com.example.spotifyclone.service.google

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.example.spotifyclone.data.model.GoogleUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class GoogleAuthManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("google_auth_prefs", Context.MODE_PRIVATE)

    companion object {
        const val REDIRECT_URI = "spotifyclone://google-callback"
        const val GOOGLE_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        const val GOOGLE_SERVICE_LOGIN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https://music.youtube.com"
        const val YOUTUBE_SCOPE = "email profile https://www.googleapis.com/auth/youtube.readonly"
    }

    var clientId: String
        get() = prefs.getString("google_client_id", "") ?: ""
        set(value) = prefs.edit().putString("google_client_id", value).apply()

    fun isLoggedIn(): Boolean {
        return prefs.getString("google_user_id", null) != null
    }

    fun getAccessToken(): String? {
        return prefs.getString("access_token", null)
    }

    fun getUser(): GoogleUser? {
        val id = prefs.getString("google_user_id", null) ?: return null
        val name = prefs.getString("google_user_name", "Google User") ?: "Google User"
        val email = prefs.getString("google_user_email", null)
        val avatar = prefs.getString("google_user_avatar", null)
        return GoogleUser(id, name, email, avatar, isYouTubeMusicConnected = true)
    }

    fun saveUser(user: GoogleUser, token: String? = null) {
        prefs.edit()
            .putString("google_user_id", user.id)
            .putString("google_user_name", user.displayName)
            .putString("google_user_email", user.email)
            .putString("google_user_avatar", user.avatarUrl)
            .apply()
        if (token != null) {
            prefs.edit().putString("access_token", token).apply()
        }
    }

    fun loginDemoAccount() {
        saveUser(
            GoogleUser(
                id = "google_user_101",
                displayName = "Google User",
                email = "user@gmail.com",
                avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&auto=format&fit=crop&q=60",
                isYouTubeMusicConnected = true
            ),
            token = "demo_google_token"
        )
    }

    fun loginWithCustomName(name: String = "Google User", email: String = "user@gmail.com") {
        saveUser(
            GoogleUser(
                id = "google_" + System.currentTimeMillis(),
                displayName = name.ifBlank { "Google User" },
                email = email.ifBlank { "user@gmail.com" },
                avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&auto=format&fit=crop&q=60",
                isYouTubeMusicConnected = true
            ),
            token = "google_token_" + System.currentTimeMillis()
        )
    }

    fun buildAuthUrl(): String {
        if (clientId.isNotBlank()) {
            return Uri.parse(GOOGLE_AUTH_ENDPOINT).buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("response_type", "token")
                .appendQueryParameter("scope", YOUTUBE_SCOPE)
                .appendQueryParameter("include_granted_scopes", "true")
                .appendQueryParameter("prompt", "select_account")
                .build()
                .toString()
        }
        // If no custom developer client ID is set, open official Google sign-in directly (never 404s!)
        return GOOGLE_SERVICE_LOGIN_URL
    }

    fun buildAuthIntent(): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(buildAuthUrl())).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun handleCallbackUri(uri: Uri): Boolean {
        // e.g. spotifyclone://google-callback#access_token=... or ?access_token=...
        val fragment = uri.fragment ?: uri.query ?: return false
        val params = fragment.split("&").associate {
            val parts = it.split("=")
            if (parts.size == 2) parts[0] to parts[1] else "" to ""
        }

        val token = params["access_token"]
        if (!token.isNullOrEmpty()) {
            saveUser(
                GoogleUser(
                    id = "google_" + System.currentTimeMillis(),
                    displayName = "Google User",
                    email = "google_account@gmail.com",
                    avatarUrl = null,
                    isYouTubeMusicConnected = true
                ),
                token = token
            )
            return true
        }
        return false
    }

    fun logout() {
        prefs.edit().clear().apply()
    }
}
