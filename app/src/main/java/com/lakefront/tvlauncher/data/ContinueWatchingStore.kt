package com.lakefront.tvlauncher.data

import android.content.Context
import com.lakefront.tvlauncher.logic.ContinueWatching
import com.lakefront.tvlauncher.logic.MediaEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Snapshot of what was being watched, stored so the row survives the player releasing its
 * media session. Small on purpose: a single JSON array in the launcher's own preference file.
 */
class ContinueWatchingStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): List<MediaEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { toEntry(it) }
            }
        } catch (error: Exception) {
            emptyList()
        }
    }

    fun save(entries: List<MediaEntry>) {
        val array = JSONArray()
        entries.forEach { entry -> array.put(toJson(entry)) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    /** Merges live sessions into the snapshot and writes the result back. */
    fun record(incoming: List<MediaEntry>, nowMs: Long = System.currentTimeMillis()): List<MediaEntry> {
        val merged = ContinueWatching.merge(load(), incoming, nowMs)
        save(merged)
        return merged
    }

    private fun toJson(entry: MediaEntry): JSONObject = JSONObject().apply {
        put("pkg", entry.packageName)
        put("title", entry.title)
        put("subtitle", entry.subtitle ?: JSONObject.NULL)
        put("position", entry.positionMs)
        put("duration", entry.durationMs)
        put("art", entry.artUri ?: JSONObject.NULL)
        put("intent", entry.intentUri ?: JSONObject.NULL)
        put("updated", entry.updatedAtMs)
    }

    private fun toEntry(json: JSONObject): MediaEntry = MediaEntry(
        packageName = json.optString("pkg"),
        title = json.optString("title"),
        subtitle = json.optString("subtitle").takeIf { it.isNotEmpty() && it != "null" },
        positionMs = json.optLong("position"),
        durationMs = json.optLong("duration"),
        artUri = json.optString("art").takeIf { it.isNotEmpty() && it != "null" },
        intentUri = json.optString("intent").takeIf { it.isNotEmpty() && it != "null" },
        updatedAtMs = json.optLong("updated"),
    )

    private companion object {
        const val FILE = "lake_continue_watching"
        const val KEY = "entries"
    }
}
