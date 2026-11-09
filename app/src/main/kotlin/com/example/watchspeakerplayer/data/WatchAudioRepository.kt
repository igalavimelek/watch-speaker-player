package com.example.watchspeakerplayer.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Discovers, imports, and manages audio files stored on the watch (MediaStore, Music/Download
 * folders, app-specific Music directory, and built-in demo WAV tracks).
 */
class WatchAudioRepository(private val context: Context) {

  private val supportedExtensions = setOf("mp3", "wav", "m4a", "ogg", "flac", "aac", "opus", "mp4")

  fun getAppMusicDir(): File {
    return context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
      ?: File(context.filesDir, "Music").apply { mkdirs() }
  }

  suspend fun loadAllTracks(): List<AudioTrackItem> = withContext(Dispatchers.IO) {
    val tracks = mutableListOf<AudioTrackItem>()
    val seenPathsOrUris = mutableSetOf<String>()

    val appMusicDir = getAppMusicDir()
    val samplePairs = SampleWaveGenerator.ensureSampleTracks(appMusicDir)
    val sampleFileNames = samplePairs.map { it.first.name }.toSet()

    // 1. Add user-imported or adb-pushed audio files in the app's Music directory first
    appMusicDir.listFiles()
      ?.filter { it.isFile && !sampleFileNames.contains(it.name) && isSupportedAudioFile(it) }
      ?.sortedBy { it.name.lowercase() }
      ?.forEach { file ->
        val uri = Uri.fromFile(file)
        seenPathsOrUris.add(file.absolutePath)
        seenPathsOrUris.add(uri.toString())
        tracks.add(
          AudioTrackItem(
            id = "app_file_${file.name}",
            title = file.nameWithoutExtension.replace('_', ' '),
            subtitle = "App Music Folder • ${file.extension.uppercase()}",
            uri = uri,
            durationMs = readDurationMs(file.absolutePath),
            isBuiltInSample = false,
          )
        )
      }

    // 2. Query MediaStore.Audio for audio files on the watch (Music, Downloads, Alarms, etc.)
    runCatching {
      val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
      )
      val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
      context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        sortOrder,
      )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

        while (cursor.moveToNext()) {
          val id = cursor.getLong(idCol)
          val displayName = cursor.getString(nameCol) ?: continue
          if (sampleFileNames.contains(displayName)) continue

          val rawTitle = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
            ?: displayName.substringBeforeLast('.')
          val rawArtist = cursor.getString(artistCol)?.takeIf {
            it.isNotBlank() && it != "<unknown>"
          } ?: "Watch Storage"
          val duration = cursor.getLong(durationCol).coerceAtLeast(0L)
          val filePath = if (dataCol >= 0) cursor.getString(dataCol) else null
          val contentUri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            id,
          )

          if (filePath != null && seenPathsOrUris.contains(filePath)) continue
          if (!seenPathsOrUris.add(contentUri.toString())) continue
          if (filePath != null) seenPathsOrUris.add(filePath)

          tracks.add(
            AudioTrackItem(
              id = "mediastore_$id",
              title = rawTitle,
              subtitle = rawArtist,
              uri = contentUri,
              durationMs = duration,
              isBuiltInSample = false,
            )
          )
        }
      }
    }

    // 3. Direct directory scan fallback for /sdcard/Music and /sdcard/Download
    val publicDirs = listOfNotNull(
      Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
      Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
      Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
      Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_ALARMS),
      Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_NOTIFICATIONS),
      Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES),
    )
    for (dir in publicDirs) {
      runCatching {
        dir.listFiles()
          ?.filter { it.isFile && isSupportedAudioFile(it) && !seenPathsOrUris.contains(it.absolutePath) }
          ?.sortedBy { it.name.lowercase() }
          ?.forEach { file ->
            seenPathsOrUris.add(file.absolutePath)
            val uri = Uri.fromFile(file)
            tracks.add(
              AudioTrackItem(
                id = "public_${dir.name}_${file.name}",
                title = file.nameWithoutExtension.replace('_', ' '),
                subtitle = "${dir.name} • ${file.extension.uppercase()}",
                uri = uri,
                durationMs = readDurationMs(file.absolutePath),
                isBuiltInSample = false,
              )
            )
          }
      }
    }

    // 4. Built-in synthesized WAV tracks so speaker playback works out of the box
    samplePairs.forEach { (file, spec) ->
      val uri = Uri.fromFile(file)
      tracks.add(
        AudioTrackItem(
          id = "sample_${spec.fileName}",
          title = spec.displayTitle,
          subtitle = spec.subtitle,
          uri = uri,
          durationMs = spec.durationSeconds * 1000L,
          isBuiltInSample = true,
        )
      )
    }

    tracks
  }

  suspend fun importExternalAudioUri(sourceUri: Uri): AudioTrackItem? = withContext(Dispatchers.IO) {
    runCatching {
      val displayName = queryDisplayName(sourceUri)
        ?: "Imported_Audio_${System.currentTimeMillis()}.mp3"
      val sanitizedName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
      val destFile = File(getAppMusicDir(), sanitizedName)

      context.contentResolver.openInputStream(sourceUri)?.use { input ->
        FileOutputStream(destFile).use { output ->
          input.copyTo(output)
        }
      } ?: return@withContext null

      val uri = Uri.fromFile(destFile)
      AudioTrackItem(
        id = "app_file_${destFile.name}",
        title = destFile.nameWithoutExtension.replace('_', ' '),
        subtitle = "Imported • ${destFile.extension.uppercase().ifBlank { "AUDIO" }}",
        uri = uri,
        durationMs = readDurationMs(destFile.absolutePath),
        isBuiltInSample = false,
      )
    }.getOrNull()
  }

  private fun queryDisplayName(uri: Uri): String? {
    return runCatching {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
          if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
          } else {
            null
          }
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
  }

  private fun isSupportedAudioFile(file: File): Boolean {
    return file.extension.lowercase() in supportedExtensions
  }

  private fun readDurationMs(path: String): Long {
    val retriever = MediaMetadataRetriever()
    return try {
      retriever.setDataSource(path)
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Exception) {
      0L
    } finally {
      runCatching { retriever.release() }
    }
  }
}
