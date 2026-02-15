package jp.masatonasu.wakemusic

import java.util.concurrent.atomic.AtomicBoolean

object NowPlaying {
    val isPlaying = AtomicBoolean(false)
    @Volatile var title: String? = null
}
