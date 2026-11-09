package com.example.watchspeakerplayer.ui.main

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchspeakerplayer.data.AudioTrackItem
import com.example.watchspeakerplayer.data.WatchAudioRepository
import com.example.watchspeakerplayer.player.SpeakerPlayerState
import com.example.watchspeakerplayer.player.WatchSpeakerPlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coordinates the watch audio library and speaker playback state for Wear OS 5.
 */
class WatchPlayerViewModel(application: Application) : AndroidViewModel(application) {
  private val repository = WatchAudioRepository(application.applicationContext)
  private val playerController = WatchSpeakerPlayerController(
    context = application.applicationContext,
    scope = viewModelScope,
    onTrackCompleted = ::advanceTrackOnCompletion,
  )

  private val _tracks = MutableStateFlow<List<AudioTrackItem>>(emptyList())
  val tracks: StateFlow<List<AudioTrackItem>> = _tracks.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  val playerState: StateFlow<SpeakerPlayerState> = playerController.state

  val appMusicPath: String
    get() = repository.getAppMusicDir().absolutePath

  init {
    rescanTracks()
  }

  fun rescanTracks() {
    viewModelScope.launch {
      _isScanning.value = true
      playerController.refreshAudioDevices()
      playerController.refreshVolumeState()
      val loaded = repository.loadAllTracks()
      _tracks.value = loaded
      _isScanning.value = false
    }
  }

  fun playTrack(track: AudioTrackItem) {
    playerController.playTrack(track)
  }

  fun togglePlayPause() {
    val defaultTrack = playerState.value.currentTrack ?: _tracks.value.firstOrNull()
    playerController.togglePlayPause(defaultTrack)
  }

  fun nextTrack() {
    val currentList = _tracks.value
    if (currentList.isEmpty()) return
    val currentId = playerState.value.currentTrack?.id
    val currentIndex = currentList.indexOfFirst { it.id == currentId }
    val nextIndex = if (currentIndex in currentList.indices) {
      (currentIndex + 1) % currentList.size
    } else {
      0
    }
    playerController.playTrack(currentList[nextIndex])
  }

  fun previousTrack() {
    val currentList = _tracks.value
    if (currentList.isEmpty()) return
    if (playerState.value.currentPositionMs > 3000L) {
      playerController.seekBy(-playerState.value.currentPositionMs)
      return
    }
    val currentId = playerState.value.currentTrack?.id
    val currentIndex = currentList.indexOfFirst { it.id == currentId }
    val prevIndex = if (currentIndex > 0) {
      currentIndex - 1
    } else {
      currentList.lastIndex
    }
    playerController.playTrack(currentList[prevIndex])
  }

  fun seekBy(deltaMs: Long) {
    playerController.seekBy(deltaMs)
  }

  fun adjustVolume(direction: Int) {
    playerController.adjustVolume(direction)
  }

  fun setForceWatchSpeaker(enabled: Boolean) {
    playerController.setForceWatchSpeaker(enabled)
  }

  fun setUseSpeakerOverrideStream(enabled: Boolean) {
    playerController.setUseSpeakerOverrideStream(enabled)
  }

  fun setLooping(enabled: Boolean) {
    playerController.setLooping(enabled)
  }

  fun importAudioUri(uri: Uri, autoPlay: Boolean = true) {
    viewModelScope.launch {
      _isScanning.value = true
      val imported = repository.importExternalAudioUri(uri)
      val loaded = repository.loadAllTracks()
      _tracks.value = loaded
      _isScanning.value = false
      if (imported != null && autoPlay) {
        playerController.playTrack(imported)
      }
    }
  }

  private fun advanceTrackOnCompletion() {
    // Advance to next track only for multi-track playlists when not looping
    val currentList = _tracks.value
    val currentId = playerState.value.currentTrack?.id ?: return
    val idx = currentList.indexOfFirst { it.id == currentId }
    if (idx in 0 until currentList.lastIndex) {
      playerController.playTrack(currentList[idx + 1])
    }
  }

  override fun onCleared() {
    super.onCleared()
    playerController.release()
  }
}
