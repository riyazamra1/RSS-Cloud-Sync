package com.riyaz.rsscloudsync

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Stores independent folder-pair configurations while keeping the existing sync engine preferences compatible. */
object SyncPairStore {
    private const val KEY = "sync_pairs"
    private const val TIER_KEY = "membership_tier"

    data class Pair(
        val id: String,
        val name: String,
        val provider: String,
        val accountEmail: String,
        val remoteFolderId: String,
        val remoteFolderName: String,
        val localFolderUri: String,
        val direction: String,
        val enabled: Boolean,
        val excludeHidden: Boolean,
        val excludeSubfolders: Boolean,
        val deleteEmpty: Boolean,
        val selectedFiles: Set<String>
    )

    fun isPremium(prefs: SharedPreferences): Boolean =
        prefs.getString(TIER_KEY, "FREE").equals("PREMIUM", ignoreCase = true)

    fun all(prefs: SharedPreferences): List<Pair> {
        val raw = prefs.getStringSet(KEY, emptySet()) ?: emptySet()
        return raw.mapNotNull { decode(it) }.sortedBy { it.name.lowercase() }
    }

    fun migrateLegacyIfNeeded(prefs: SharedPreferences): List<Pair> {
        val current = all(prefs)
        if (current.isNotEmpty()) return current
        val hasLegacy = prefs.contains("folder_pair_name") || prefs.contains("sync_folder_uri") || prefs.contains("google_drive_target_folder_id")
        if (!hasLegacy) return emptyList()
        saveCurrent(prefs, null)
        return all(prefs)
    }

    fun canCreate(prefs: SharedPreferences): Boolean = isPremium(prefs) || all(prefs).isEmpty()

    fun saveCurrent(prefs: SharedPreferences, existingId: String?): String {
        val id = existingId ?: UUID.randomUUID().toString()
        val pair = Pair(
            id = id,
            name = prefs.getString("folder_pair_name", "My Folder Pair")?.trim().orEmpty().ifBlank { "My Folder Pair" },
            provider = prefs.getString("selected_cloud_provider", "") ?: "",
            accountEmail = prefs.getString("google_drive_account_email", "") ?: "",
            remoteFolderId = prefs.getString("google_drive_target_folder_id", "") ?: "",
            remoteFolderName = prefs.getString("google_drive_target_folder_name", "") ?: "",
            localFolderUri = prefs.getString("sync_folder_uri", "") ?: "",
            direction = prefs.getString("sync_direction", "Two-way Sync") ?: "Two-way Sync",
            enabled = prefs.getBoolean("folder_pair_enabled", true),
            excludeHidden = prefs.getBoolean("exclude_hidden_files", true),
            excludeSubfolders = prefs.getBoolean("exclude_subfolders", false),
            deleteEmpty = prefs.getBoolean("delete_empty_subfolders", false),
            selectedFiles = prefs.getStringSet("selected_local_files", emptySet()) ?: emptySet()
        )
        val encoded = encode(pair)
        val set = (prefs.getStringSet(KEY, emptySet()) ?: emptySet()).toMutableSet()
        set.removeIf { decode(it)?.id == id }
        set.add(encoded)
        prefs.edit().putStringSet(KEY, set).putString("active_pair_id", id).apply()
        return id
    }

    fun load(prefs: SharedPreferences, id: String): Boolean {
        val pair = all(prefs).firstOrNull { it.id == id } ?: return false
        prefs.edit()
            .putString("active_pair_id", pair.id)
            .putString("folder_pair_name", pair.name)
            .putString("selected_cloud_provider", pair.provider)
            .putString("google_drive_account_email", pair.accountEmail)
            .putString("google_drive_target_folder_id", pair.remoteFolderId)
            .putString("google_drive_target_folder_name", pair.remoteFolderName)
            .putString("sync_folder_uri", pair.localFolderUri)
            .putString("sync_direction", pair.direction)
            .putBoolean("folder_pair_enabled", pair.enabled)
            .putBoolean("exclude_hidden_files", pair.excludeHidden)
            .putBoolean("exclude_subfolders", pair.excludeSubfolders)
            .putBoolean("delete_empty_subfolders", pair.deleteEmpty)
            .putStringSet("selected_local_files", pair.selectedFiles)
            .apply()
        return true
    }

    fun delete(prefs: SharedPreferences, id: String) {
        val set = (prefs.getStringSet(KEY, emptySet()) ?: emptySet()).toMutableSet()
        set.removeIf { decode(it)?.id == id }
        val editor = prefs.edit().putStringSet(KEY, set)
        if (prefs.getString("active_pair_id", null) == id) editor.remove("active_pair_id")
        editor.apply()
    }

    fun count(prefs: SharedPreferences): Int = all(prefs).size

    private fun encode(pair: Pair): String = JSONObject().apply {
        put("id", pair.id); put("name", pair.name); put("provider", pair.provider); put("accountEmail", pair.accountEmail)
        put("remoteFolderId", pair.remoteFolderId); put("remoteFolderName", pair.remoteFolderName); put("localFolderUri", pair.localFolderUri)
        put("direction", pair.direction); put("enabled", pair.enabled); put("excludeHidden", pair.excludeHidden)
        put("excludeSubfolders", pair.excludeSubfolders); put("deleteEmpty", pair.deleteEmpty); put("selectedFiles", JSONArray(pair.selectedFiles.toList()))
    }.toString()

    private fun decode(raw: String): Pair? = try {
        val o = JSONObject(raw)
        val files = mutableSetOf<String>()
        val array = o.optJSONArray("selectedFiles")
        if (array != null) for (i in 0 until array.length()) files += array.optString(i)
        Pair(o.optString("id"), o.optString("name", "My Folder Pair"), o.optString("provider"), o.optString("accountEmail"),
            o.optString("remoteFolderId"), o.optString("remoteFolderName"), o.optString("localFolderUri"), o.optString("direction", "Two-way Sync"),
            o.optBoolean("enabled", true), o.optBoolean("excludeHidden", true), o.optBoolean("excludeSubfolders", false), o.optBoolean("deleteEmpty", false), files)
    } catch (_: Exception) { null }
}
