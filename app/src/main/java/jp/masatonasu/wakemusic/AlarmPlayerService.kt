package jp.masatonasu.wakemusic

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class AlarmPlayerService : Service() {

    private val tag = "AlarmPlayerService"

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var playlist: List<android.net.Uri> = emptyList()
    private var index: Int = 0
    private var player: MediaPlayer? = null

    private var failCount: Int = 0
    private var lastPlayed: android.net.Uri? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        NotificationHelper.ensureChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPlayback("action_stop")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startPlayback(intent)
                return START_STICKY
            }
            else -> {
                startPlayback(intent)
                return START_STICKY
            }
        }
    }

    private fun startPlayback(intent: Intent?) {
        acquireWakeLock()
        requestAudioFocus()
        applyPresetAlarmVolume()

        playlist = buildPlaylist()
        if (playlist.isEmpty()) {
            // Fallback: system alarm sound
            val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (fallback != null) playlist = listOf(fallback)
        }

        index = 0
        failCount = 0
        NowPlaying.isPlaying.set(true)
        NowPlaying.title = "WakeMusic"

        // 1) Foreground service notification (silent / low importance)
        startForegroundCompat(buildPlaybackNotification("再生準備中…"))

        // 2) Alarm-start control notification (high importance) with Stop button
        postOrUpdateAlarmNotification("再生準備中…")

        // 3) Try to bring the stop UI to front (may be restricted by OEM/OS)
        showRingingUi()
        playCurrent()
    }

    private fun buildPlaylist(): List<android.net.Uri> {
        // Prefer cache if valid
        val tree = AlarmStore.getMusicTreeUri(this) ?: return emptyList()
        val cached = AlarmStore.getCache(this)
        val uris = when {
            cached != null && cached.treeUri == tree -> cached.uris
            else -> {
                val scanned = runCatching { MusicRepository.scanFolder(this, tree) }.getOrNull().orEmpty()
                if (scanned.isNotEmpty()) AlarmStore.setCache(this, tree, scanned)
                scanned
            }
        }
        if (uris.isEmpty()) return emptyList()
        return uris.shuffled()
    }

    private fun playCurrent() {
        val uri = playlist.getOrNull(index)
        if (uri == null) {
            stopPlayback("playlist_empty")
            stopSelf()
            return
        }

        val title = MusicRepository.resolveDisplayName(this, uri)?.takeIf { it.isNotBlank() } ?: "WakeMusic"
        NowPlaying.title = title
        NowPlaying.isPlaying.set(true)
        sendNowPlayingBroadcast(title, true)
        updateNotifications(title)

        releasePlayer()
        val mp = MediaPlayer()
        player = mp

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        mp.setAudioAttributes(attrs)

        mp.setOnPreparedListener {
            failCount = 0
            lastPlayed = uri
            runCatching { it.start() }
            NowPlaying.isPlaying.set(true)
            sendNowPlayingBroadcast(NowPlaying.title ?: "WakeMusic", true)
        }

        mp.setOnCompletionListener { playNext() }

        mp.setOnErrorListener { _, what, extra ->
            Log.e(tag, "MediaPlayer error what=$what extra=$extra")
            handlePlaybackFailure("mediaplayer_error")
            true
        }

        try {
            val afd = contentResolver.openAssetFileDescriptor(uri, "r")
            if (afd != null) {
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            } else {
                mp.setDataSource(this, uri)
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e(tag, "Failed to set data source: $uri", e)
            handlePlaybackFailure("setDataSource_failed")
        }
    }

    private fun playNext() {
        if (playlist.isEmpty()) {
            stopPlayback("playlist_empty_next")
            stopSelf()
            return
        }

        val nextIndex = (index + 1) % playlist.size
        index = nextIndex

        // If we wrapped around, reshuffle for variety.
        if (index == 0 && playlist.size >= 2) {
            val last = lastPlayed
            var shuffled = playlist.shuffled()

            // Avoid repeating the same track twice in a row when possible.
            if (last != null && shuffled.firstOrNull() == last) {
                shuffled = shuffled.drop(1) + shuffled.first()
            }
            playlist = shuffled
        }

        playCurrent()
    }

    private fun handlePlaybackFailure(reason: String) {
        failCount += 1

        // If we keep failing, fall back to system alarm so the user is never left in silence.
        val maxTries = maxOf(playlist.size, 5)
        if (failCount >= maxTries) {
            Log.w(tag, "Too many failures ($failCount). Falling back to system alarm. reason=$reason")
            val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            playlist = if (fallback != null) listOf(fallback) else emptyList()
            index = 0
            failCount = 0
        } else {
            Log.w(tag, "Playback failure ($failCount/$maxTries): $reason -> next")
            index = (index + 1) % playlist.size
        }

        playCurrent()
    }

    private fun stopPlayback(reason: String) {
        Log.i(tag, "stopPlayback: $reason")
        NowPlaying.isPlaying.set(false)
        sendNowPlayingBroadcast(NowPlaying.title ?: "WakeMusic", false)
        releasePlayer()
        abandonAudioFocus()
        releaseWakeLock()
        cancelAlarmNotification()
        StopOverlay.hide(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun releasePlayer() {
        val p = player ?: return
        runCatching { p.stop() }
        runCatching { p.reset() }
        runCatching { p.release() }
        player = null
    }

    private fun showRingingUi() {
        val i = Intent(this, RingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { startActivity(i) }
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_PLAYBACK_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIF_PLAYBACK_ID, notification)
        }
    }

    private fun updateNotifications(title: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_PLAYBACK_ID, buildPlaybackNotification(title))
        nm.notify(NOTIF_ALARM_ID, buildAlarmNotification(title))

        // Optional: show a small overlay STOP button if the user has granted permission.
        if (NowPlaying.isPlaying.get()) {
            StopOverlay.show(this)
        } else {
            StopOverlay.hide(this)
        }
    }

    private fun postOrUpdateAlarmNotification(title: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ALARM_ID, buildAlarmNotification(title))
    }

    private fun cancelAlarmNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        runCatching { nm.cancel(NOTIF_ALARM_ID) }
    }

    private fun buildPlaybackNotification(title: String): android.app.Notification {
        val openIntent = Intent(this, RingingActivity::class.java)
        val stopIntent = Intent(this, AlarmPlayerService::class.java).apply { action = ACTION_STOP }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

        val openPi = PendingIntent.getActivity(this, 2001, openIntent, flags)
        val stopPi = PendingIntent.getService(this, 2002, stopIntent, flags)

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_PLAYBACK_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("WakeMusic")
            .setContentText(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.stop), stopPi)
            .build()
    }

    private fun buildAlarmNotification(title: String): android.app.Notification {
        val openIntent = Intent(this, RingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val stopIntent = Intent(this, AlarmPlayerService::class.java).apply { action = ACTION_STOP }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

        val openPi = PendingIntent.getActivity(this, 2101, openIntent, flags)
        val stopPi = PendingIntent.getService(this, 2102, stopIntent, flags)

        // Full-screen intent: OEM/OS may restrict, but even when blocked, this notification + Stop button remains.
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ALARM_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("WakeMusic")
            .setContentText(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(openPi)
            .setFullScreenIntent(openPi, true)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.stop), stopPi)
            .build()
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { /* ignore */ }
                .build()
            audioFocusRequest = req
            runCatching { am.requestAudioFocus(req) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { am.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) }
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { runCatching { am.abandonAudioFocusRequest(it) } }
        } else {
            @Suppress("DEPRECATION")
            runCatching { am.abandonAudioFocus(null) }
        }
        audioFocusRequest = null
    }

    private fun applyPresetAlarmVolume() {
        val am = audioManager ?: return
        val percent = AlarmStore.getAlarmVolumePercent(this).coerceIn(0, 100)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)
        val target = kotlin.math.round(max.toFloat() * (percent / 100f)).toInt().coerceIn(0, max)
        runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0) }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeMusic:AlarmWakeLock").apply {
            setReferenceCounted(false)
            runCatching { acquire(10 * 60 * 1000L) }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { wl ->
            if (wl.isHeld) runCatching { wl.release() }
        }
        wakeLock = null
    }

    private fun sendNowPlayingBroadcast(title: String, playing: Boolean) {
        val i = Intent(ACTION_NOW_PLAYING).apply {
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_PLAYING, playing)
        }
        sendBroadcast(i)
    }

    override fun onDestroy() {
        stopPlayback("onDestroy")
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "jp.masatonasu.wakemusic.action.START"
        const val ACTION_STOP = "jp.masatonasu.wakemusic.action.STOP"

        const val EXTRA_SOURCE = "extra_source"

        const val ACTION_NOW_PLAYING = "jp.masatonasu.wakemusic.action.NOW_PLAYING"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PLAYING = "extra_playing"

        const val NOTIF_PLAYBACK_ID = 1001
        const val NOTIF_ALARM_ID = 1002
    }
}
