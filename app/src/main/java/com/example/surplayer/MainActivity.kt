package com.example.surplayer

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var store: LibraryStore
    private val tracks = mutableListOf<Track>()

    private var showFavorites = false
    private var currentIndex = -1
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private lateinit var status: TextView
    private lateinit var currentTitle: TextView
    private lateinit var artwork: ImageView
    private lateinit var lyricsView: TextView
    private lateinit var list: LinearLayout
    private lateinit var playButton: Button
    private lateinit var repeatButton: Button
    private lateinit var searchInput: EditText

    private var repeatOne = false

    private val handler = Handler(Looper.getMainLooper())

    private val lyricTicker = object : Runnable {
        override fun run() {
            if (controller != null && ::lyricsView.isInitialized) {
                updateSyncedLyrics()
            }
            handler.postDelayed(this, 350L)
        }
    }

    private val audioPicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris?.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            if (tracks.none { it.uri == uri }) {
                tracks.add(
                    Track(uri, queryName(uri) ?: "Audio track")
                )
            }
        }

        persist()
        renderList()

        if (::status.isInitialized) {
            status.text = "${tracks.size} ગીતો લાઇબ્રેરીમાં"
        }
    }

    private val lyricsPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && currentIndex in tracks.indices) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            val text = runCatching {
                contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() } ?: ""
            }.getOrDefault("")

            tracks[currentIndex].lyrics = text
            persist()
            updateNowPlaying()
            toast("Lyrics સાચવાઈ ગયા")
        }
    }

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && currentIndex in tracks.indices) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            tracks[currentIndex].artworkUri = uri.toString()
            persist()
            updateNowPlaying()
            toast("કવર ફોટો સાચવાયો")
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && Build.VERSION.SDK_INT >= 33) {
            // Notification permission ન મળે તો પણ એપ વાપરી શકાય છે.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        store = LibraryStore(this)
        tracks.addAll(store.load())

        buildUi()

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        connectPlayback()
        handler.post(lyricTicker)
    }

    private fun connectPlayback() {
        val token = SessionToken(
            this,
            ComponentName(this, PlaybackService::class.java)
        )

        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future

        future.addListener({
            try {
                val c = future.get()
                controller = c

                c.addListener(object : Player.Listener {

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updateNowPlaying()
                    }

                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int
                    ) {
                        val idx = c.currentMediaItemIndex

                        if (idx in tracks.indices) {
                            currentIndex = idx
                        }

                        updateNowPlaying()
                        renderList()
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        repeatOne = repeatMode == Player.REPEAT_MODE_ONE
                        updateRepeatButton()
                    }
                })

                updateNowPlaying()

            } catch (e: Exception) {
                if (::status.isInitialized) {
                    status.text =
                        "Player શરૂ ન થયો: ${e.message ?: "અજ્ઞાત ભૂલ"}"
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 10)
            setBackgroundColor(0xFF10131A.toInt())
        }

        val heading = TextView(this).apply {
            text = "🎵 Sur Player"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
        }
        root.addView(heading, matchWrap())

        status = TextView(this).apply {
            text = "તમારા ફોનનાં ગીતો ઉમેરો"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFFB7C1D1.toInt())
            setPadding(0, 4, 0, 8)
        }
        root.addView(status, matchWrap())

        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        addRow.addView(
            makeButton("＋ ગીતો ઉમેરો") {
                audioPicker.launch(arrayOf("audio/*"))
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        addRow.addView(
            makeButton(
                if (showFavorites) "બધાં ગીતો" else "♥ Favorites"
            ) {
                showFavorites = !showFavorites
                renderList()
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        root.addView(addRow, matchWrap())

        searchInput = EditText(this).apply {
            hint = "ગીત શોધો..."
            textSize = 15f
            setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF8994A7.toInt())
            addTextChangedListener(
                SimpleTextWatcher { renderList() }
            )
        }
        root.addView(searchInput, matchWrap())

        artwork = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF222A38.toInt())
            contentDescription = "ગીતનું કવર"
        }

        root.addView(
            artwork,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(145)
            ).apply {
                topMargin = dp(8)
            }
        )

        currentTitle = TextView(this).apply {
            text = "હમણાં કોઈ ગીત પસંદ નથી"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 10, 0, 2)
        }
        root.addView(currentTitle, matchWrap())

        lyricsView = TextView(this).apply {
            text = "Lyrics અહીં દેખાશે"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFFB7C1D1.toInt())
            setPadding(8, 4, 8, 8)
            minHeight = dp(42)
        }
        root.addView(lyricsView, matchWrap())

        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }

        controls.addView(
            makeButton("⏮") {
                controller?.seekToPreviousMediaItem()
            }
        )

        playButton = makeButton("▶ Play") {
            val c = controller ?: return@makeButton

            if (c.isPlaying) {
                c.pause()
            } else {
                if (c.mediaItemCount == 0 && tracks.isNotEmpty()) {
                    playTrack(0)
                } else {
                    c.play()
                }
            }

            updateNowPlaying()
        }
        controls.addView(playButton)

        controls.addView(
            makeButton("⏭") {
                controller?.seekToNextMediaItem()
            }
        )

        root.addView(controls, matchWrap())

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        repeatButton = makeButton("🔁 Repeat all") {
            val c = controller ?: return@makeButton

            repeatOne = !repeatOne
            c.repeatMode = if (repeatOne) {
                Player.REPEAT_MODE_ONE
            } else {
                Player.REPEAT_MODE_ALL
            }

            updateRepeatButton()
        }

        tools.addView(
            repeatButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        tools.addView(
            makeButton("Lyrics") {
                if (currentIndex in tracks.indices) {
                    lyricsPicker.launch(
                        arrayOf("text/plain", "application/octet-stream")
                    )
                } else {
                    toast("પહેલાં ગીત પસંદ કરો")
                }
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        tools.addView(
            makeButton("કવર ફોટો") {
                if (currentIndex in tracks.indices) {
                    imagePicker.launch(arrayOf("image/*"))
                } else {
                    toast("પહેલાં ગીત પસંદ કરો")
                }
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        root.addView(tools, matchWrap())

        val scroll = ScrollView(this)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(list)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
        renderList()
        updateRepeatButton()
    }

    private fun renderList() {
        if (!::list.isInitialized) return

        list.removeAllViews()

        val query = if (::searchInput.isInitialized) {
            searchInput.text.toString()
                .trim()
                .lowercase(Locale.getDefault())
        } else {
            ""
        }

        val items = tracks.withIndex().filter { (_, track) ->
            (!showFavorites || track.favorite) &&
                (
                    query.isBlank() ||
                    track.title.lowercase(Locale.getDefault()).contains(query) ||
                    track.artist.lowercase(Locale.getDefault()).contains(query)
                )
        }

        if (items.isEmpty()) {
            val empty = TextView(this).apply {
                text = when {
                    showFavorites ->
                        "હજુ Favorites નથી. ગીતની બાજુમાં ♥ દબાવો."
                    query.isNotBlank() ->
                        "કોઈ ગીત મળ્યું નથી."
                    else ->
                        "હજુ ગીતો ઉમેર્યાં નથી. ઉપર ＋ ગીતો ઉમેરો દબાવો."
                }

                setTextColor(0xFFB7C1D1.toInt())
                textSize = 14f
                setPadding(8, 18, 8, 18)
            }

            list.addView(empty)
        }

        items.forEach { indexed ->
            val realIndex = indexed.index
            val track = indexed.value

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(2, 2, 2, 2)
            }

            val name = TextView(this).apply {
                text = (if (realIndex == currentIndex) "▶ " else "") +
                    track.title +
                    if (track.favorite) "  ♥" else ""

                textSize = 14f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(5, 10, 4, 10)
                setOnClickListener {
                    playTrack(realIndex)
                }
            }

            row.addView(
                name,
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            row.addView(
                smallButton(if (track.favorite) "♥" else "♡") {
                    track.favorite = !track.favorite
                    persist()
                    renderList()
                }
            )

            row.addView(smallButton("↑") {
                if (realIndex > 0) {
                    val item = tracks.removeAt(realIndex)
                    tracks.add(realIndex - 1, item)

                    if (currentIndex == realIndex) {
                        currentIndex--
                    } else if (currentIndex == realIndex - 1) {
                        currentIndex++
                    }

                    persist()
                    syncQueue()
                    renderList()
                }
            })

            row.addView(smallButton("↓") {
                if (realIndex < tracks.lastIndex) {
                    val item = tracks.removeAt(realIndex)
                    tracks.add(realIndex + 1, item)

                    if (currentIndex == realIndex) {
                        currentIndex++
                    } else if (currentIndex == realIndex + 1) {
                        currentIndex--
                    }

                    persist()
                    syncQueue()
                    renderList()
                }
            })

            row.addView(smallButton("✕") {
                val wasCurrent = realIndex == currentIndex

                tracks.removeAt(realIndex)

                if (wasCurrent) {
                    controller?.stop()
                    controller?.clearMediaItems()
                    currentIndex = -1
                } else if (realIndex < currentIndex) {
                    currentIndex--
                }

                persist()
                syncQueue()
                renderList()
                updateNowPlaying()
            })

            list.addView(row)

            list.addView(
                View(this).apply {
                    setBackgroundColor(0xFF293140.toInt())
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1
                )
            )
        }
    }

    private fun playTrack(index: Int) {
        if (index !in tracks.indices) return

        currentIndex = index

        val items = tracks.map { track ->
            MediaItem.Builder()
                .setUri(track.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .build()
                )
                .build()
        }

        controller?.setMediaItems(items, index, 0L)

        controller?.repeatMode = if (repeatOne) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_ALL
        }

        controller?.prepare()
        controller?.play()

        updateNowPlaying()
        renderList()
    }

    private fun syncQueue() {
        val c = controller ?: return

        if (tracks.isEmpty()) {
            c.stop()
            c.clearMediaItems()
            currentIndex = -1
            return
        }

        val safeIndex = currentIndex.coerceIn(0, tracks.lastIndex)

        val items = tracks.map { track ->
            MediaItem.Builder()
                .setUri(track.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .build()
                )
                .build()
        }

        c.setMediaItems(items, safeIndex, 0L)

        c.repeatMode = if (repeatOne) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_ALL
        }

        c.prepare()
    }

    private fun updateNowPlaying() {
        val c = controller

        if (c != null && c.currentMediaItemIndex in tracks.indices) {
            currentIndex = c.currentMediaItemIndex
            val track = tracks[currentIndex]

            currentTitle.text = "🎶 ${track.title}"
            playButton.text = if (c.isPlaying) "⏸ Pause" else "▶ Play"
            status.text = if (c.isPlaying) {
                "હમણાં ગીત વાગી રહ્યું છે"
            } else {
                "ગીત તૈયાર છે"
            }

            artwork.setImageDrawable(null)

            if (track.artworkUri.isNotBlank()) {
                runCatching {
                    contentResolver
                        .openInputStream(Uri.parse(track.artworkUri))
                        ?.use {
                            artwork.setImageBitmap(
                                BitmapFactory.decodeStream(it)
                            )
                        }
                }
            } else {
                artwork.setImageResource(
                    android.R.drawable.ic_media_play
                )
            }

            updateSyncedLyrics()

        } else {
            currentTitle.text = "હમણાં કોઈ ગીત પસંદ નથી"
            lyricsView.text = "ગીત પસંદ કરો અને Lyrics ઉમેરો"
            artwork.setImageResource(android.R.drawable.ic_media_play)
            playButton.text = "▶ Play"
        }
    }

    private data class LyricLine(
        val timeMs: Long,
        val text: String
    )

    private fun updateSyncedLyrics() {
        val c = controller ?: return

        if (currentIndex !in tracks.indices) return

        val lyrics = tracks[currentIndex].lyrics

        if (lyrics.isBlank()) {
            lyricsView.text = "આ ગીત માટે Lyrics ઉમેરો"
            return
        }

        val lines = lyrics.lineSequence().mapNotNull { line ->
            val match = Regex(
                """\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?](.*)"""
            ).matchEntire(line.trim())

            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val fracText = match.groupValues[3]

                val fraction = when (fracText.length) {
                    1 -> (fracText.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracText.toLongOrNull() ?: 0L) * 10L
                    else -> fracText.toLongOrNull() ?: 0L
                }

                LyricLine(
                    (min * 60L + sec) * 1000L + fraction,
                    match.groupValues[4].trim()
                )
            } else {
                null
            }
        }.toList()

        if (lines.isEmpty()) {
            lyricsView.text = lyrics
            return
        }

        val pos = c.currentPosition
        val active = lines.indexOfLast { it.timeMs <= pos }

        lyricsView.text = if (active >= 0) {
            val start = (active - 1).coerceAtLeast(0)
            val end = (active + 3).coerceAtMost(lines.lastIndex)

            lines.subList(start, end + 1).mapIndexed { offset, line ->
                val actualIndex = start + offset
                (if (actualIndex == active) "▶ " else "") + line.text
            }.joinToString("\n")
        } else {
            lines.take(3).joinToString("\n") { it.text }
        }
    }

    private fun updateRepeatButton() {
        if (::repeatButton.isInitialized) {
            repeatButton.text = if (repeatOne) {
                "🔂 Repeat one"
            } else {
                "🔁 Repeat all"
            }
        }
    }

    private fun persist() {
        store.save(tracks)
    }

    private fun queryName(uri: Uri): String? {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }

        return null
    }

    private fun makeButton(
        label: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            textSize = 11f
            setOnClickListener { action() }
        }
    }

    private fun smallButton(
        label: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            textSize = 11f
            minWidth = 0
            minimumWidth = 0
            setPadding(2, 0, 2, 0)
            setOnClickListener { action() }
        }
    }

    private fun matchWrap(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        handler.removeCallbacks(lyricTicker)
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        controller = null
        super.onDestroy()
    }
}
