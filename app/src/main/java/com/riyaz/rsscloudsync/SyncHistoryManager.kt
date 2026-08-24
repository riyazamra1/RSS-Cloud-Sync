package com.riyaz.rsscloudsync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Lightweight persistent sync history. Keeps the newest 50 records. */
object SyncHistoryManager {
    private const val PREFS = "rss_cloud_sync"
    private const val KEY = "sync_history"
    private const val MAX_ITEMS = 50

    data class Entry(
        val timestamp: Long,
        val direction: String,
        val filesProcessed: Int,
        val filesChanged: Int,
        val bytesTransferred: Long,
        val durationMs: Long,
        val success: Boolean,
        val message: String
    )

    fun add(context: Context, entry: Entry) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = try { JSONArray(prefs.getString(KEY, "[]")) } catch (_: Exception) { JSONArray() }
        val out = JSONArray()
        out.put(toJson(entry))
        for (i in 0 until minOf(old.length(), MAX_ITEMS - 1)) out.put(old.optJSONObject(i))
        prefs.edit().putString(KEY, out.toString()).apply()
    }

    fun get(context: Context): List<Entry> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = try { JSONArray(prefs.getString(KEY, "[]")) } catch (_: Exception) { JSONArray() }
        return buildList {
            for (i in 0 until array.length()) array.optJSONObject(i)?.let { add(fromJson(it)) }
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun toJson(e: Entry) = JSONObject().apply {
        put("timestamp", e.timestamp)
        put("direction", e.direction)
        put("filesProcessed", e.filesProcessed)
        put("filesChanged", e.filesChanged)
        put("bytesTransferred", e.bytesTransferred)
        put("durationMs", e.durationMs)
        put("success", e.success)
        put("message", e.message)
    }

    private fun fromJson(o: JSONObject) = Entry(
        o.optLong("timestamp"), o.optString("direction"),
        o.optInt("filesProcessed"), o.optInt("filesChanged"),
        o.optLong("bytesTransferred"), o.optLong("durationMs"),
        o.optBoolean("success"), o.optString("message")
    )
}
