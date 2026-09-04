package com.riyaz.rsscloudsync

import android.content.SharedPreferences

object SyncPairStore {
    private const val KEY = "sync_pairs"

    fun all(prefs: SharedPreferences): List<SyncPair> = (prefs.getStringSet(KEY, emptySet()) ?: emptySet()).mapNotNull(::decode)

    fun save(prefs: SharedPreferences, pair: SyncPair): String {
        val id = pair.id.ifBlank { java.util.UUID.randomUUID().toString() }
        val saved = pair.copy(id = id)
        val encoded = encode(saved)
        val set = (prefs.getStringSet(KEY, emptySet()) ?: emptySet()).toMutableSet()
        val iterator = set.iterator()
        while (iterator.hasNext()) {
            if (decode(iterator.next())?.id == id) iterator.remove()
        }
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
            .putString("schedule_mode", pair.scheduleMode)
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
        val iterator = set.iterator()
        while (iterator.hasNext()) {
            if (decode(iterator.next())?.id == id) iterator.remove()
        }
        val editor = prefs.edit().putStringSet(KEY, set)
        if (prefs.getString("active_pair_id", null) == id) editor.remove("active_pair_id")
        editor.apply()
    }

    private fun encode(pair: SyncPair): String = listOf(
        pair.id, pair.name, pair.provider, pair.accountEmail, pair.remoteFolderId, pair.remoteFolderName,
        pair.localFolderUri, pair.direction, pair.scheduleMode, pair.enabled, pair.excludeHidden,
        pair.excludeSubfolders, pair.deleteEmpty, pair.selectedFiles.joinToString(",")
    ).joinToString("|")

    private fun decode(value: String): SyncPair? = runCatching {
        val p = value.split("|")
        if (p.size < 14) return null
        SyncPair(
            id = p[0], name = p[1], provider = p[2], accountEmail = p[3], remoteFolderId = p[4],
            remoteFolderName = p[5], localFolderUri = p[6], direction = p[7], scheduleMode = p[8],
            enabled = p[9].toBoolean(), excludeHidden = p[10].toBoolean(), excludeSubfolders = p[11].toBoolean(),
            deleteEmpty = p[12].toBoolean(), selectedFiles = if (p[13].isBlank()) emptySet() else p[13].split(",").toSet()
        )
    }.getOrNull()
}
