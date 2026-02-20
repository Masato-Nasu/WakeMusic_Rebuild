package jp.masatonasu.wakemusic

data class AlarmItem(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    /** true のときは「月〜金のみ」鳴らす（週末はスキップ） */
    val weekdaysOnly: Boolean = false,
) {
    fun formatTime(): String = String.format("%02d:%02d", hour, minute)
}
