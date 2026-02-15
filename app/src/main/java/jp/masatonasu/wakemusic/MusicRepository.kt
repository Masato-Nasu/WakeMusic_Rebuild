package jp.masatonasu.wakemusic

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile

object MusicRepository {

    /**
     * Enumerate audio files under selected SAF folder (including subfolders).
     */
    fun scanFolder(context: Context, treeUri: Uri): List<Uri> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val out = mutableListOf<Uri>()

        fun walk(doc: DocumentFile) {
            if (doc.isDirectory) {
                doc.listFiles().forEach { walk(it) }
            } else {
                val name = doc.name.orEmpty()
                val type = doc.type.orEmpty()
                val isAudio =
                    type.startsWith("audio/") ||
                        name.endsWith(".mp3", true) ||
                        name.endsWith(".m4a", true) ||
                        name.endsWith(".wav", true) ||
                        name.endsWith(".ogg", true) ||
                        name.endsWith(".flac", true) ||
                        name.endsWith(".aac", true) ||
                        name.endsWith(".opus", true)

                if (isAudio) out.add(doc.uri)
            }
        }

        walk(root)
        return out
    }

    fun resolveDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                }
        }.getOrNull()
    }
}
