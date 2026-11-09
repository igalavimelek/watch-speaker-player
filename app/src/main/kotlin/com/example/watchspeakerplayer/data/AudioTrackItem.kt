package com.example.watchspeakerplayer.data

import android.net.Uri

/**
 * Represents an audio file available for playback on the watch speaker.
 */
data class AudioTrackItem(
  val id: String,
  val title: String,
  val subtitle: String,
  val uri: Uri,
  val durationMs: Long = 0L,
  val isBuiltInSample: Boolean = false,
)
