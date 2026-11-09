package com.example.watchspeakerplayer.player

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.PowerManager
import com.example.watchspeakerplayer.data.AudioTrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SpeakerPlayerState(
  val hasBuiltInSpeaker: Boolean = true,
  val speakerProductName: String = "Watch Speaker",
  val hasBluetoothAudio: Boolean = false,
  val forceWatchSpeaker: Boolean = true,
  val useSpeakerOverrideStream: Boolean = true,
  val isLooping: Boolean = false,
  val isPlaying: Boolean = false,
  val currentTrack: AudioTrackItem? = null,
  val currentPositionMs: Long = 0L,
  val durationMs: Long = 0L,
  val volumePercent: Int = 70,
  val statusMessage: String = "Ready • Watch Speaker Active",
)

/**
 * Manages audio output detection, explicit watch speaker routing (TYPE_BUILTIN_SPEAKER),
 * Wear OS 5 speaker-suppression bypass, volume control, and MediaPlayer lifecycle.
 */
class WatchSpeakerPlayerController(
  private val context: Context,
  private val scope: CoroutineScope,
  private val onTrackCompleted: () -> Unit,
) {
  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private var mediaPlayer: MediaPlayer? = null
  private var progressJob: Job? = null
  private var focusRequest: AudioFocusRequest? = null

  private val _state = MutableStateFlow(SpeakerPlayerState())
  val state: StateFlow<SpeakerPlayerState> = _state.asStateFlow()

  private val deviceCallback = object : AudioDeviceCallback() {
    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
      refreshAudioDevices()
    }

    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
      refreshAudioDevices()
    }
  }

  init {
    refreshAudioDevices()
    refreshVolumeState()
    audioManager.registerAudioDeviceCallback(deviceCallback, null)
  }

  fun findBuiltInSpeakerDevice(): AudioDeviceInfo? {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) {
      return null
    }
    return audioManager
      .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
      .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
  }

  private fun hasConnectedBluetoothOutput(): Boolean {
    val btTypes = setOf(
      AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
      AudioDeviceInfo.TYPE_BLE_HEADSET,
      AudioDeviceInfo.TYPE_BLE_SPEAKER,
      AudioDeviceInfo.TYPE_BLE_BROADCAST,
    )
    return audioManager
      .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
      .any { it.type in btTypes }
  }

  fun refreshAudioDevices() {
    val speakerDevice = findBuiltInSpeakerDevice()
    val hasBt = hasConnectedBluetoothOutput()
    val speakerName = speakerDevice?.productName?.toString()?.takeIf { it.isNotBlank() }
      ?: "Watch Built-in Speaker"

    _state.update { current ->
      current.copy(
        hasBuiltInSpeaker = speakerDevice != null,
        speakerProductName = speakerName,
        hasBluetoothAudio = hasBt,
      )
    }
  }

  private fun activeStreamType(): Int {
    return if (_state.value.useSpeakerOverrideStream) {
      AudioManager.STREAM_ALARM
    } else {
      AudioManager.STREAM_MUSIC
    }
  }

  fun refreshVolumeState() {
    val stream = activeStreamType()
    val max = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
    val current = audioManager.getStreamVolume(stream)
    val pct = ((current.toFloat() / max.toFloat()) * 100f).toInt().coerceIn(0, 100)
    _state.update { it.copy(volumePercent = pct) }
  }

  fun adjustVolume(direction: Int) {
    val stream = activeStreamType()
    val adjustFlag = if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
    runCatching {
      audioManager.adjustStreamVolume(stream, adjustFlag, AudioManager.FLAG_SHOW_UI)
    }
    refreshVolumeState()
  }

  fun setForceWatchSpeaker(enabled: Boolean) {
    _state.update {
      it.copy(
        forceWatchSpeaker = enabled,
        statusMessage = if (enabled) {
          "Pinned to Watch Speaker"
        } else {
          "Using System Default Output"
        }
      )
    }
    mediaPlayer?.let { applySpeakerRouting(it) }
  }

  fun setUseSpeakerOverrideStream(enabled: Boolean) {
    val wasPlaying = _state.value.isPlaying
    val currentTrack = _state.value.currentTrack
    val resumePos = _state.value.currentPositionMs

    _state.update {
      it.copy(
        useSpeakerOverrideStream = enabled,
        statusMessage = if (enabled) {
          "Speaker Direct Stream (Wear OS 5)"
        } else {
          "Standard Media Stream"
        }
      )
    }
    refreshVolumeState()

    if (currentTrack != null && wasPlaying) {
      playTrack(currentTrack, startPositionMs = resumePos)
    }
  }

  fun setLooping(enabled: Boolean) {
    _state.update { it.copy(isLooping = enabled) }
    mediaPlayer?.isLooping = enabled
  }

  private fun applySpeakerRouting(player: MediaPlayer) {
    val speakerDevice = if (_state.value.forceWatchSpeaker) {
      findBuiltInSpeakerDevice()
    } else {
      null
    }
    runCatching {
      player.setPreferredDevice(speakerDevice)
    }
  }

  private fun requestAudioFocus() {
    val usage = if (_state.value.useSpeakerOverrideStream) {
      AudioAttributes.USAGE_ALARM
    } else {
      AudioAttributes.USAGE_MEDIA
    }
    val attrs = AudioAttributes.Builder()
      .setUsage(usage)
      .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
      .build()

    val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
      .setAudioAttributes(attrs)
      .setOnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
          pause()
        }
      }
      .build()

    focusRequest = req
    runCatching { audioManager.requestAudioFocus(req) }
  }

  fun playTrack(track: AudioTrackItem, startPositionMs: Long = 0L) {
    stopAndReleasePlayer()
    requestAudioFocus()
    refreshVolumeState()

    // Ensure stream volume on the watch isn't muted (0) when user taps Play
    val stream = activeStreamType()
    if (audioManager.getStreamVolume(stream) == 0) {
      val max = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
      val target = (max * 0.6f).toInt().coerceAtLeast(1)
      runCatching { audioManager.setStreamVolume(stream, target, 0) }
      refreshVolumeState()
    }

    val usage = if (_state.value.useSpeakerOverrideStream) {
      AudioAttributes.USAGE_ALARM
    } else {
      AudioAttributes.USAGE_MEDIA
    }
    val audioAttributes = AudioAttributes.Builder()
      .setUsage(usage)
      .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
      .build()

    runCatching {
      val player = MediaPlayer()
      player.setAudioAttributes(audioAttributes)
      player.setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
      player.setDataSource(context, track.uri)
      applySpeakerRouting(player)
      player.isLooping = _state.value.isLooping

      player.setOnPreparedListener { mp ->
        applySpeakerRouting(mp)
        val totalDuration = mp.duration.toLong().coerceAtLeast(track.durationMs)
        if (startPositionMs > 0L && startPositionMs < totalDuration) {
          mp.seekTo(startPositionMs.toInt())
        }
        mp.start()
        _state.update {
          it.copy(
            currentTrack = track,
            isPlaying = true,
            currentPositionMs = startPositionMs,
            durationMs = totalDuration,
            statusMessage = if (it.forceWatchSpeaker) {
              "Playing on Watch Speaker"
            } else {
              "Playing Audio"
            },
          )
        }
        startProgressLoop()
      }

      player.setOnCompletionListener { mp ->
        if (!mp.isLooping) {
          stopProgressLoop()
          _state.update {
            it.copy(
              isPlaying = false,
              currentPositionMs = it.durationMs,
              statusMessage = "Finished • ${track.title}",
            )
          }
          onTrackCompleted()
        }
      }

      player.setOnErrorListener { _, what, extra ->
        stopProgressLoop()
        _state.update {
          it.copy(
            isPlaying = false,
            statusMessage = "Playback Error ($what / $extra)",
          )
        }
        true
      }

      mediaPlayer = player
      player.prepareAsync()
    }.onFailure { err ->
      _state.update {
        it.copy(
          isPlaying = false,
          statusMessage = "Failed: ${err.localizedMessage ?: "Cannot open file"}",
        )
      }
    }
  }

  fun togglePlayPause(defaultTrack: AudioTrackItem?) {
    val player = mediaPlayer
    if (player != null) {
      if (player.isPlaying) {
        pause()
      } else {
        requestAudioFocus()
        applySpeakerRouting(player)
        player.start()
        _state.update {
          it.copy(
            isPlaying = true,
            statusMessage = if (it.forceWatchSpeaker) "Playing on Watch Speaker" else "Playing",
          )
        }
        startProgressLoop()
      }
    } else if (_state.value.currentTrack != null) {
      playTrack(_state.value.currentTrack!!)
    } else if (defaultTrack != null) {
      playTrack(defaultTrack)
    }
  }

  fun pause() {
    mediaPlayer?.let { player ->
      if (player.isPlaying) {
        player.pause()
      }
      stopProgressLoop()
      _state.update {
        it.copy(
          isPlaying = false,
          currentPositionMs = player.currentPosition.toLong(),
          statusMessage = "Paused",
        )
      }
    }
  }

  fun seekBy(deltaMs: Long) {
    val player = mediaPlayer ?: return
    val duration = player.duration.toLong().coerceAtLeast(1L)
    val target = (player.currentPosition.toLong() + deltaMs).coerceIn(0L, duration)
    player.seekTo(target.toInt())
    _state.update { it.copy(currentPositionMs = target) }
  }

  private fun startProgressLoop() {
    stopProgressLoop()
    progressJob = scope.launch {
      while (isActive) {
        val player = mediaPlayer
        if (player != null && player.isPlaying) {
          val pos = runCatching { player.currentPosition.toLong() }.getOrDefault(0L)
          val dur = runCatching { player.duration.toLong() }.getOrDefault(_state.value.durationMs)
          _state.update {
            it.copy(
              currentPositionMs = pos,
              durationMs = dur.coerceAtLeast(1L),
            )
          }
        }
        delay(200L)
      }
    }
  }

  private fun stopProgressLoop() {
    progressJob?.cancel()
    progressJob = null
  }

  private fun stopAndReleasePlayer() {
    stopProgressLoop()
    mediaPlayer?.let { player ->
      runCatching {
        if (player.isPlaying) player.stop()
      }
      runCatching { player.release() }
    }
    mediaPlayer = null
  }

  fun release() {
    stopAndReleasePlayer()
    focusRequest?.let { req ->
      runCatching { audioManager.abandonAudioFocusRequest(req) }
    }
    runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
  }
}
