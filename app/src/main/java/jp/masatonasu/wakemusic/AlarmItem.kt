package jp.masatonasu.wakemusic

data class AlarmItem(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
) {
    fun formatTime(): String = String.format("%02d:%02d", hour, minute)
}
