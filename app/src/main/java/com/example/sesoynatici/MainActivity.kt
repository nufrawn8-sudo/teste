package com.example.sesoynatici

import android.Manifest
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class MainActivity : ComponentActivity() {

    data class Track(val uri: Uri, val name: String)

    private lateinit var view: PlayerView
    private lateinit var player: ExoPlayer
    private var tracks = listOf<Track>()
    private var pendingUri: Uri? = null
    private val handler = Handler(Looper.getMainLooper())

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            pendingUri?.let { start(it) }
        }

    private val pickLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) finish() else askPermissionThenStart(uri)
        }

    private val ticker = object : Runnable {
        override fun run() {
            val d = player.duration
            if (!view.dragging && d > 0) {
                view.progress = (player.currentPosition.toFloat() / d).coerceIn(0f, 1f)
            }
            handler.postDelayed(this, 50)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        view = PlayerView(this)
        setContentView(view)
        WindowInsetsControllerCompat(window, view).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.topInset = b.top.toFloat()
            view.bottomInset = b.bottom.toFloat()
            insets
        }

        player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                updateTrack()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                view.playing = isPlaying
            }
        })

        view.onDone = { finish() }
        view.onToggle = { if (player.isPlaying) player.pause() else player.play() }
        view.onSeek = { p ->
            val d = player.duration
            if (d > 0) player.seekTo((p * d).toLong())
        }
        view.onSwipe = { dir ->
            if (dir > 0) player.seekToNextMediaItem() else player.seekToPreviousMediaItem()
        }
        view.onShare = { share() }
        view.onList = { showList() }

        handler.post(ticker)

        val uri = intent?.data
        if (uri == null) pickLauncher.launch(arrayOf("audio/*")) else askPermissionThenStart(uri)
    }

    private fun audioPermission() =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(this, audioPermission()) == PackageManager.PERMISSION_GRANTED

    private fun askPermissionThenStart(uri: Uri) {
        if (hasPermission()) {
            start(uri)
        } else {
            pendingUri = uri
            permLauncher.launch(audioPermission())
        }
    }

    private fun start(uri: Uri) {
        val (list, index) = buildPlaylist(uri)
        tracks = list
        player.setMediaItems(list.map { MediaItem.fromUri(it.uri) }, index, 0L)
        player.prepare()
        player.play()
        updateTrack()
    }

    private fun updateTrack() {
        val idx = player.currentMediaItemIndex
        view.title = "${idx + 1} of ${tracks.size}"
        view.progress = 0f
        view.peaks = FloatArray(0)
        val track = tracks.getOrNull(idx) ?: return
        Thread {
            val p = WaveformExtractor.extract(this, track.uri)
            runOnUiThread {
                if (player.currentMediaItemIndex == idx) view.peaks = p
            }
        }.start()
    }

    private fun displayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) return it.getString(0) }
        } catch (_: Exception) {}
        return uri.lastPathSegment ?: "Şarkı"
    }

    private data class Row(val id: Long, val name: String, val path: String)

    private fun buildPlaylist(uri: Uri): Pair<List<Track>, Int> {
        val single = Pair(listOf(Track(uri, displayName(uri))), 0)
        if (!hasPermission()) return single
        try {
            val rows = mutableListOf<Row>()
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val proj = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.RELATIVE_PATH
            )
            contentResolver.query(collection, proj, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    rows.add(Row(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: ""))
                }
            }
            val current = if (uri.authority == MediaStore.AUTHORITY) {
                val id = ContentUris.parseId(uri)
                rows.firstOrNull { it.id == id }
            } else {
                val name = displayName(uri)
                rows.firstOrNull { it.name == name }
            } ?: return single

            val siblings = rows.filter { it.path == current.path }
                .sortedBy { it.name.lowercase() }
            val list = siblings.map {
                Track(ContentUris.withAppendedId(collection, it.id), it.name)
            }
            val index = siblings.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
            return Pair(list, index)
        } catch (e: Exception) {
            return single
        }
    }

    private fun share() {
        val track = tracks.getOrNull(player.currentMediaItemIndex) ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, track.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, null))
    }

    private fun showList() {
        val names = tracks.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setItems(names) { _, i -> player.seekTo(i, 0L) }
            .show()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        player.release()
        super.onDestroy()
    }
}
