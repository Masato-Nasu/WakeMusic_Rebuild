package jp.masatonasu.wakemusic

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import jp.masatonasu.wakemusic.databinding.ActivityMainBinding
import androidx.documentfile.provider.DocumentFile
import android.app.TimePickerDialog

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var adapter: AlarmListAdapter

    private lateinit var audioManager: AudioManager
    private var maxAlarmVol: Int = 1

    private val requestPostNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && Build.VERSION.SDK_INT >= 33) {
            Toast.makeText(this, "通知が許可されていないと、アラームが鳴らない場合があります。", Toast.LENGTH_LONG).show()
        }
    }

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult

        // Persist SAF permission (READ/WRITE only)
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
        }.onFailure {
            // Some devices only grant READ; try again with READ only.
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        AlarmStore.setMusicTreeUri(this, uri)
        Toast.makeText(this, "音楽フォルダを設定しました", Toast.LENGTH_SHORT).show()
        refreshUi("フォルダ設定")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)
        @Suppress("DEPRECATION")
        volumeControlStream = AudioManager.STREAM_ALARM

        ensureNotificationPermissionIfNeeded()

        adapter = AlarmListAdapter(
            onToggle = { item, enabled ->
                val updated = item.copy(enabled = enabled)
                updateAlarm(updated)
                if (enabled) AlarmScheduler.schedule(this, updated) else AlarmScheduler.cancel(this, updated.id)
                refreshUi("切替")
            },
            onDelete = { item ->
                AlarmScheduler.cancel(this, item.id)
                deleteAlarm(item.id)
                refreshUi("削除")
            }
        )

        binding.rvAlarms.layoutManager = LinearLayoutManager(this)
        binding.rvAlarms.adapter = adapter

        binding.btnChooseFolder.setOnClickListener {
            pickFolder.launch(null)
        }

        binding.btnScan.setOnClickListener {
            scanFolderAndCache()
        }

        binding.btnAddAlarm.setOnClickListener {
            showTimePickerAddAlarm()
        }

        binding.btnTestPlay.setOnClickListener {
            val i = Intent(this, AlarmPlayerService::class.java).apply {
                action = AlarmPlayerService.ACTION_START
                putExtra(AlarmPlayerService.EXTRA_SOURCE, "test")
            }
            ContextCompat.startForegroundService(this, i)
        }

        binding.btnStop.setOnClickListener {
            val i = Intent(this, AlarmPlayerService::class.java).apply {
                action = AlarmPlayerService.ACTION_STOP
            }
            startService(i)
        }

        val initPercent = AlarmStore.getAlarmVolumePercent(this)
        binding.seekVolume.progress = initPercent
        binding.tvVolume.text = "音量: ${initPercent}%"
        applyAlarmStreamPercent(initPercent, showUi = false)

        binding.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                if (!fromUser) return
                AlarmStore.setAlarmVolumePercent(this@MainActivity, value)
                binding.tvVolume.text = "音量: ${value}%"
                applyAlarmStreamPercent(value, showUi = true)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        refreshUi("起動")
    }

    override fun onResume() {
        super.onResume()
        refreshUi("resume")
    }

    private fun ensureNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun showTimePickerAddAlarm() {
        val now = java.util.Calendar.getInstance()
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = now.get(java.util.Calendar.MINUTE)

        val dlg = TimePickerDialog(
            this,
            { _, h, m ->
                val id = (System.currentTimeMillis() % Int.MAX_VALUE).toLong()
                val newItem = AlarmItem(id = id, hour = h, minute = m, enabled = true)
                val effective = addOrReplaceAlarm(newItem)
                AlarmScheduler.schedule(this, effective)
                refreshUi("追加")
            },
            hour,
            minute,
            true
        )
        dlg.show()
    }

    private fun addOrReplaceAlarm(item: AlarmItem): AlarmItem {
        val list = AlarmStore.loadAlarms(this).toMutableList()
        val existingIdx = list.indexOfFirst { it.hour == item.hour && it.minute == item.minute }
        val effective = if (existingIdx >= 0) {
            val existing = list[existingIdx]
            val updated = existing.copy(enabled = item.enabled)
            list[existingIdx] = updated
            updated
        } else {
            list.add(item)
            item
        }
        AlarmStore.saveAlarms(this, list)
        return effective
    }

    private fun updateAlarm(item: AlarmItem) {
        val list = AlarmStore.loadAlarms(this).toMutableList()
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) {
            list[idx] = item
            AlarmStore.saveAlarms(this, list)
        }
    }

    private fun deleteAlarm(id: Long) {
        val list = AlarmStore.loadAlarms(this).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list.removeAt(idx)
            AlarmStore.saveAlarms(this, list)
        }
    }

    private fun scanFolderAndCache() {
        val tree = AlarmStore.getMusicTreeUri(this)
        if (tree == null) {
            Toast.makeText(this, "先に音楽フォルダを選んでください", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "スキャン中…（曲が多いと時間がかかります）", Toast.LENGTH_SHORT).show()

        Thread {
            val list = runCatching { MusicRepository.scanFolder(this, tree) }.getOrNull().orEmpty()
            if (list.isNotEmpty()) {
                AlarmStore.setCache(this, tree, list)
            }
            runOnUiThread {
                Toast.makeText(this, "スキャン完了: ${list.size} 曲", Toast.LENGTH_SHORT).show()
                refreshUi("scan")
            }
        }.start()
    }

    private fun refreshUi(reason: String) {
        val tree = AlarmStore.getMusicTreeUri(this)
        val folderName = tree?.let { DocumentFile.fromTreeUri(this, it)?.name } ?: "未設定"

        val cache = AlarmStore.getCache(this)
        val cacheCount = cache?.uris?.size ?: 0

        val alarms = AlarmStore.loadAlarms(this)
        adapter.submitList(alarms)

        binding.tvStatus.text = "状態: フォルダ=$folderName / 曲=$cacheCount / アラーム=${alarms.size}"

        val sb = StringBuilder()
        sb.append("debug:").append("\n")
        sb.append("reason=").append(reason).append("\n")
        sb.append("treeUri=").append(tree?.toString() ?: "null").append("\n")
        sb.append("cacheCount=").append(cacheCount).append("\n")
        sb.append("alarms=").append(alarms.joinToString { it.formatTime() + if (it.enabled) "" else "(OFF)" }).append("\n")

        binding.tvDebug.text = sb.toString()
    }

    private fun applyAlarmStreamPercent(percent: Int, showUi: Boolean) {
        val p = percent.coerceIn(0, 100)
        val target = kotlin.math.round(maxAlarmVol.toFloat() * (p / 100f)).toInt().coerceIn(0, maxAlarmVol)
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, target, flags) }
    }
}
