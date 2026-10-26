package com.example.surplayer

import android.net.Uri
import org.json.JSONObject

data class Track(
    val uri: Uri,
    val title: String,
    var artist: String = "Unknown artist",
    var artworkUri: String = "",
    var lyrics: String = "",
    var favorite: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("uri", uri.toString())
        put("title", title)
        put("artist", artist)
        put("artworkUri", artworkUri)
        put("lyrics", lyrics)
        put("favorite", favorite)
    }

    companion object {
        fun fromJson(json: JSONObject): Track = Track(
            uri = Uri.parse(json.getString("uri")),
            title = json.optString("title", "Audio track"),
            artist = json.optString("artist", "Unknown artist"),
            artworkUri = json.optString("artworkUri", ""),
            lyrics = json.optString("lyrics", ""),
            favorite = json.optBoolean("favorite", false)
        )
    }
}
