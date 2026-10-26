package com.example.surplayer

import android.content.Context
import org.json.JSONArray

class LibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("sur_player_library", Context.MODE_PRIVATE)

    fun load(): MutableList<Track> {
        val raw = prefs.getString("tracks", "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val result = mutableListOf<Track>()
        for (i in 0 until array.length()) {
            runCatching { result.add(Track.fromJson(array.getJSONObject(i))) }
        }
        return result
    }

    fun save(tracks: List<Track>) {
        val array = JSONArray()
        tracks.forEach { array.put(it.toJson()) }
        prefs.edit().putString("tracks", array.toString()).apply()
    }
}
