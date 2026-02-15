package jp.masatonasu.wakemusic

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

object AlarmStore {
    private const val PREFS = "wakemusic_prefs"

    private const val KEY_MUSIC_TREE_URI = "music_tree_uri"
    private const val KEY_ALARM_VOLUME_PERCENT = "alarm_volume_percent"
    private const val KEY_ALARMS = "alarms"

    private const val KEY_CACHE_TREE_URI = "cache_tree_uri"
    private const val KEY_CACHE_URIS = "cache_uris"
    private const val KEY_CACHE_TS = "cache_ts"

    private const val DEFAULT_ALARM_VOLUME_PERCENT = 90

    fun setMusicTreeUri(context: Context, uri: Uri?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) {
            putString(KEY_MUSIC_TREE_URI, uri?.toString())
        }
        // Invalidate cache when folder changes
        clearCache(context)
    }

    fun getMusicTreeUri(context: Context): Uri? {
        val s = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MUSIC_TREE_URI, null)
            ?: return null
        return runCatching { Uri.parse(s) }.getOrNull()
    }

    fun setAlarmVolumePercent(context: Context, percent: Int) {
        val p = percent.coerceIn(0, 100)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) {
            putInt(KEY_ALARM_VOLUME_PERCENT, p)
        }
    }

    fun getAlarmVolumePercent(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_ALARM_VOLUME_PERCENT, DEFAULT_ALARM_VOLUME_PERCENT)
            .coerceIn(0, 100)
    }

    /**
     * alarms encoding: id|hour|minute|enabled, id|hour|minute|enabled, ...
     */
    fun loadAlarms(context: Context): List<AlarmItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ALARMS, "")
            .orEmpty()
            .trim()
        if (raw.isEmpty()) return emptyList()

        val list = mutableListOf<AlarmItem>()
        raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { token ->
                val p = token.split("|")
                if (p.size < 4) return@forEach
                val id = p[0].toLongOrNull() ?: return@forEach
                val hour = p[1].toIntOrNull() ?: return@forEach
                val minute = p[2].toIntOrNull() ?: return@forEach
                val enabled = p[3].toBooleanStrictOrNull() ?: true
                list.add(
                    AlarmItem(
                        id = id,
                        hour = hour.coerceIn(0, 23),
                        minute = minute.coerceIn(0, 59),
                        enabled = enabled
                    )
                )
            }

        return list.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
    }

    fun saveAlarms(context: Context, alarms: List<AlarmItem>) {
        val encoded = alarms.joinToString(",") { a ->
            "${a.id}|${a.hour}|${a.minute}|${a.enabled}"
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) {
            putString(KEY_ALARMS, encoded)
        }
    }

    fun setCache(context: Context, treeUri: Uri, uris: List<Uri>) {
        val text = uris.joinToString("\n") { it.toString() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) {
            putString(KEY_CACHE_TREE_URI, treeUri.toString())
            putString(KEY_CACHE_URIS, text)
            putLong(KEY_CACHE_TS, System.currentTimeMillis())
        }
    }

    fun getCache(context: Context): CachedPlaylist? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val tree = sp.getString(KEY_CACHE_TREE_URI, null) ?: return null
        val listText = sp.getString(KEY_CACHE_URIS, null) ?: return null
        val ts = sp.getLong(KEY_CACHE_TS, 0L)
        if (listText.isBlank()) return null
        val uris = listText.split("\n")
            .mapNotNull { runCatching { Uri.parse(it.trim()) }.getOrNull() }
            .filterNotNull()
        if (uris.isEmpty()) return null
        return CachedPlaylist(Uri.parse(tree), uris, ts)
    }

    fun clearCache(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) {
            remove(KEY_CACHE_TREE_URI)
            remove(KEY_CACHE_URIS)
            remove(KEY_CACHE_TS)
        }
    }

    data class CachedPlaylist(
        val treeUri: Uri,
        val uris: List<Uri>,
        val timestampMillis: Long,
    )
}
