package jp.masatonasu.wakemusic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import jp.masatonasu.wakemusic.databinding.ActivityRingingBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RingingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRingingBinding
    private val handler = Handler(Looper.getMainLooper())
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val tick = object : Runnable {
        override fun run() {
            binding.tvNow.text = clockFormat.format(Date())
            binding.tvTrack.text = "再生中: ${NowPlaying.title ?: "WakeMusic"}"
            handler.postDelayed(this, 1000L)
        }
    }

    private val nowPlayingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AlarmPlayerService.ACTION_NOW_PLAYING) return
            val title = intent.getStringExtra(AlarmPlayerService.EXTRA_TITLE)
            val playing = intent.getBooleanExtra(AlarmPlayerService.EXTRA_PLAYING, false)
            NowPlaying.title = title
            NowPlaying.isPlaying.set(playing)
            binding.tvTrack.text = "再生中: ${title ?: "WakeMusic"}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show above lockscreen + turn on screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        @Suppress("DEPRECATION")
        volumeControlStream = AudioManager.STREAM_ALARM

        binding = ActivityRingingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStop.setOnClickListener {
            val i = Intent(this, AlarmPlayerService::class.java).apply { action = AlarmPlayerService.ACTION_STOP }
            startService(i)
            finish()
        }

        binding.tvNow.text = clockFormat.format(Date())
        binding.tvTrack.text = "再生中: ${NowPlaying.title ?: "WakeMusic"}"
    }

    override fun onResume() {
        super.onResume()
        registerNowPlayingReceiver()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        unregisterReceiverSafe()
    }

    private fun registerNowPlayingReceiver() {
        val filter = IntentFilter(AlarmPlayerService.ACTION_NOW_PLAYING)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(nowPlayingReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(nowPlayingReceiver, filter)
        }
    }

    private fun unregisterReceiverSafe() {
        runCatching { unregisterReceiver(nowPlayingReceiver) }
    }
}
