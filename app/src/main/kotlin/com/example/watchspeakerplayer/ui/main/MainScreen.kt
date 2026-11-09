package com.example.watchspeakerplayer.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalScrollCaptureInProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.example.watchspeakerplayer.data.AudioTrackItem
import com.example.watchspeakerplayer.player.SpeakerPlayerState
import com.example.watchspeakerplayer.ui.theme.WatchSpeakerPlayerTheme

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit = {},
  modifier: Modifier = Modifier,
  viewModel: WatchPlayerViewModel = viewModel(),
) {
  val context = LocalContext.current
  val tracks by viewModel.tracks.collectAsStateWithLifecycle()
  val playerState by viewModel.playerState.collectAsStateWithLifecycle()
  val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

  val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    Manifest.permission.READ_MEDIA_AUDIO
  } else {
    Manifest.permission.READ_EXTERNAL_STORAGE
  }

  var hasPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    hasPermission = granted
    viewModel.rescanTracks()
  }

  val openDocumentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      runCatching {
        context.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
      }
      viewModel.importAudioUri(uri, autoPlay = true)
    }
  }

  WatchSpeakerPlayerScreen(
    tracks = tracks,
    playerState = playerState,
    isScanning = isScanning,
    hasStoragePermission = hasPermission,
    onTogglePlayPause = viewModel::togglePlayPause,
    onPreviousTrack = viewModel::previousTrack,
    onNextTrack = viewModel::nextTrack,
    onSeekBackward = { viewModel.seekBy(-5000L) },
    onSeekForward = { viewModel.seekBy(5000L) },
    onVolumeDown = { viewModel.adjustVolume(-1) },
    onVolumeUp = { viewModel.adjustVolume(1) },
    onSelectTrack = viewModel::playTrack,
    onToggleForceSpeaker = viewModel::setForceWatchSpeaker,
    onToggleSpeakerOverrideStream = viewModel::setUseSpeakerOverrideStream,
    onToggleLooping = viewModel::setLooping,
    onRequestStoragePermission = { permissionLauncher.launch(audioPermission) },
    onImportFile = {
      runCatching {
        openDocumentLauncher.launch(arrayOf("audio/*"))
      }
    },
    onOpenSoundSettings = {
      runCatching {
        context.startActivity(
          Intent(Settings.ACTION_SOUND_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        )
      }
    },
    onRescan = viewModel::rescanTracks,
    modifier = modifier,
  )
}

@Composable
fun WatchSpeakerPlayerScreen(
  tracks: List<AudioTrackItem>,
  playerState: SpeakerPlayerState,
  isScanning: Boolean,
  hasStoragePermission: Boolean,
  onTogglePlayPause: () -> Unit,
  onPreviousTrack: () -> Unit,
  onNextTrack: () -> Unit,
  onSeekBackward: () -> Unit,
  onSeekForward: () -> Unit,
  onVolumeDown: () -> Unit,
  onVolumeUp: () -> Unit,
  onSelectTrack: (AudioTrackItem) -> Unit,
  onToggleForceSpeaker: (Boolean) -> Unit,
  onToggleSpeakerOverrideStream: (Boolean) -> Unit,
  onToggleLooping: (Boolean) -> Unit,
  onRequestStoragePermission: () -> Unit,
  onImportFile: () -> Unit,
  onOpenSoundSettings: () -> Unit,
  onRescan: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val columnState = rememberTransformingLazyColumnState()
  val transformationSpec = rememberTransformationSpec()

  AppScaffold(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
  ) {
    ScreenScaffold(
      scrollState = columnState,
      scrollIndicator = {
        if (!LocalScrollCaptureInProgress.current) {
          ScrollIndicator(state = columnState)
        }
      },
      edgeButton = {
        EdgeButton(
          onClick = onRescan,
          modifier = Modifier.scrollable(
            state = columnState,
            orientation = Orientation.Vertical,
            reverseDirection = true,
            overscrollEffect = rememberOverscrollEffect(),
          ),
        ) {
          Text(if (isScanning) "Scanning..." else "Rescan")
        }
      },
    ) { contentPadding ->
      TransformingLazyColumn(
        state = columnState,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize(),
      ) {
        // 1. Header
        item(key = "header_now_playing") {
          ListHeader(
            transformation = SurfaceTransformation(transformationSpec),
            modifier = Modifier
              .fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
          ) {
            Text(
              text = if (playerState.hasBuiltInSpeaker) {
                "Watch Speaker Ready"
              } else {
                "No Built-in Speaker"
              },
              color = MaterialTheme.colorScheme.primary,
              style = MaterialTheme.typography.titleSmall,
              textAlign = TextAlign.Center,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        // 2. Now Playing & Transport Controls Card
        item(key = "now_playing_card") {
          Card(
            onClick = onTogglePlayPause,
            transformation = SurfaceTransformation(transformationSpec),
            modifier = Modifier
              .fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              val activeTrack = playerState.currentTrack ?: tracks.firstOrNull()
              Text(
                text = activeTrack?.title ?: "Tap a Track Below",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )

              val progress = if (playerState.durationMs > 0L) {
                (playerState.currentPositionMs.toFloat() / playerState.durationMs.toFloat())
                  .coerceIn(0f, 1f)
              } else {
                0f
              }

              Text(
                text = "${formatMillis(playerState.currentPositionMs)} / ${formatMillis(playerState.durationMs)} • Vol ${playerState.volumePercent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
              )

              // Primary Transport Row: Prev | Play/Pause with circular progress | Next
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                FilledTonalButton(
                  onClick = onPreviousTrack,
                ) {
                  Text("Prev")
                }

                Box(contentAlignment = Alignment.Center) {
                  CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 4.dp,
                  )
                  Button(
                    onClick = onTogglePlayPause,
                  ) {
                    Text(
                      text = if (playerState.isPlaying) "Pause" else "Play",
                      style = MaterialTheme.typography.labelMedium,
                    )
                  }
                }

                FilledTonalButton(
                  onClick = onNextTrack,
                ) {
                  Text("Next")
                }
              }

              // Secondary Row: -5s | Vol- | Vol+ | +5s
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                OutlinedButton(onClick = onSeekBackward) {
                  Text("-5s")
                }
                OutlinedButton(onClick = onVolumeDown) {
                  Text("Vol-")
                }
                OutlinedButton(onClick = onVolumeUp) {
                  Text("Vol+")
                }
                OutlinedButton(onClick = onSeekForward) {
                  Text("+5s")
                }
              }

              Text(
                text = playerState.statusMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        // 3. Audio Files Header
        item(key = "tracks_header") {
          ListHeader(
            transformation = SurfaceTransformation(transformationSpec),
            modifier = Modifier
              .fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
          ) {
            Text(
              text = "Audio Files (${tracks.size})",
              style = MaterialTheme.typography.titleSmall,
              textAlign = TextAlign.Center,
            )
          }
        }

        // 4. Audio Track Buttons
        items(
          items = tracks,
          key = { it.id },
        ) { track ->
          val isSelected = playerState.currentTrack?.id == track.id
          val prefix = when {
            isSelected && playerState.isPlaying -> "▶ "
            isSelected -> "⏸ "
            else -> ""
          }
          FilledTonalButton(
            onClick = { onSelectTrack(track) },
            transformation = SurfaceTransformation(transformationSpec),
            modifier = Modifier
              .fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
            label = {
              Text(
                text = "$prefix${track.title}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            secondaryLabel = {
              val durText = if (track.durationMs > 0L) " • ${formatMillis(track.durationMs)}" else ""
              Text(
                text = "${track.subtitle}$durText",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
          )
        }

        // 5. Speaker Routing & Settings Header
        item(key = "speaker_settings_header") {
          ListHeader(
            transformation = SurfaceTransformation(transformationSpec),
            modifier = Modifier
              .fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
          ) {
            Text(
              text = "Watch Speaker Output",
              style = MaterialTheme.typography.titleSmall,
              textAlign = TextAlign.Center,
            )
          }
        }

        // Force Watch Speaker Toggle (TYPE_BUILTIN_SPEAKER)
        item(key = "switch_force_speaker") {
          SwitchButton(
            checked = playerState.forceWatchSpeaker,
            onCheckedChange = onToggleForceSpeaker,
            transformation = SurfaceTransformation(transformationSpec),
            modifier = Modifier
              .fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
            label = {
              Text(
                text = "Force Watch Speaker",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            },
            secondaryLabel = {
              Text(
                text = "Route directly to TYPE_BUILTIN_SPEAKER",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            },
          )
        }

        // Speaker Override Stream Toggle (Wear OS 5 Media-Suppression Bypass)
        item(key = "switch_speaker_stream") {
          SwitchButton(
            checked = playerState.useSpeakerOverrideStream,
            onCheckedChange = onToggleSpeakerOverrideStream,
            transformation = SurfaceTransformation(transformationSpec),
            modifier = Modifier
              .fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
            label = {
              Text(
                text = "Wear OS 5 Speaker Bypass",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            },
            secondaryLabel = {
              Text(
                text = if (playerState.useSpeakerOverrideStream) {
                  "Direct Speaker Stream (Never Muted)"
                } else {
                  "Standard Media Stream"
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            },
          )
        }

        // Loop Track Toggle
        item(key = "switch_loop") {
          SwitchButton(
            checked = playerState.isLooping,
            onCheckedChange = onToggleLooping,
            transformation = SurfaceTransformation(transformationSpec),
            modifier = Modifier
              .fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
            label = {
              Text(
                text = "Loop Current File",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            secondaryLabel = {
              Text(
                text = if (playerState.isLooping) "Repeating continuously" else "Play once",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
          )
        }

        // 6. File Import & Permissions Actions
        item(key = "library_actions_header") {
          ListHeader(
            transformation = SurfaceTransformation(transformationSpec),
            modifier = Modifier
              .fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
          ) {
            Text(
              text = "Manage Files",
              style = MaterialTheme.typography.titleSmall,
              textAlign = TextAlign.Center,
            )
          }
        }

        if (!hasStoragePermission) {
          item(key = "btn_permission") {
            Button(
              onClick = onRequestStoragePermission,
              transformation = SurfaceTransformation(transformationSpec),
              modifier = Modifier
                .fillMaxWidth()
                .transformedHeight(this, transformationSpec)
                .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
              label = {
                Text(
                  text = "Allow Watch Audio Access",
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              },
              secondaryLabel = {
                Text(
                  text = "Scan /sdcard/Music & Downloads",
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              },
            )
          }
        }

        item(key = "btn_import") {
          FilledTonalButton(
            onClick = onImportFile,
            transformation = SurfaceTransformation(transformationSpec),
            modifier = Modifier
              .fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
            label = {
              Text(
                text = "Pick Audio File",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            secondaryLabel = {
              Text(
                text = "Import MP3 / WAV / M4A / OGG",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
          )
        }

        item(key = "btn_sound_settings") {
          OutlinedButton(
            onClick = onOpenSoundSettings,
            transformation = SurfaceTransformation(transformationSpec),
            modifier = Modifier
              .fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
            label = {
              Text(
                text = "Watch Sound Settings",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
          )
        }
      }
    }
  }
}

/**
 * Overload retained for compatibility with template UI tests.
 */
@Composable
internal fun MainScreen(data: List<String>, modifier: Modifier = Modifier) {
  Column(modifier = modifier) {
    data.forEach { Greeting(it) }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(
    text = "Hello $name!",
    modifier = modifier.padding(8.dp),
    style = MaterialTheme.typography.bodyLarge,
  )
}

private fun formatMillis(ms: Long): String {
  val totalSec = (ms / 1000L).coerceAtLeast(0L)
  val min = totalSec / 60L
  val sec = totalSec % 60L
  return "%d:%02d".format(min, sec)
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun WatchSpeakerPlayerScreenPreview() {
  WatchSpeakerPlayerTheme {
    WatchSpeakerPlayerScreen(
      tracks = listOf(
        AudioTrackItem(
          id = "1",
          title = "Horizon Chimes",
          subtitle = "Built-in WAV • Harmonic Arpeggio",
          uri = android.net.Uri.EMPTY,
          durationMs = 6000L,
          isBuiltInSample = true,
        ),
        AudioTrackItem(
          id = "2",
          title = "Pulse Synth Beat",
          subtitle = "Built-in WAV • Rhythmic Synth",
          uri = android.net.Uri.EMPTY,
          durationMs = 6000L,
          isBuiltInSample = true,
        ),
      ),
      playerState = SpeakerPlayerState(
        isPlaying = true,
        currentPositionMs = 2400L,
        durationMs = 6000L,
        volumePercent = 80,
      ),
      isScanning = false,
      hasStoragePermission = true,
      onTogglePlayPause = {},
      onPreviousTrack = {},
      onNextTrack = {},
      onSeekBackward = {},
      onSeekForward = {},
      onVolumeDown = {},
      onVolumeUp = {},
      onSelectTrack = {},
      onToggleForceSpeaker = {},
      onToggleSpeakerOverrideStream = {},
      onToggleLooping = {},
      onRequestStoragePermission = {},
      onImportFile = {},
      onOpenSoundSettings = {},
      onRescan = {},
    )
  }
}
