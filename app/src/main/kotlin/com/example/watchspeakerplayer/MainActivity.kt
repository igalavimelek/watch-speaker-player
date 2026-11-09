package com.example.watchspeakerplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.watchspeakerplayer.ui.main.WatchPlayerViewModel
import com.example.watchspeakerplayer.ui.theme.WatchSpeakerPlayerTheme

class MainActivity : ComponentActivity() {
  private val playerViewModel: WatchPlayerViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handleIncomingAudioIntent(intent)

    setContent {
      WatchSpeakerPlayerTheme {
        MainNavigation(viewModel = playerViewModel)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIncomingAudioIntent(intent)
  }

  private fun handleIncomingAudioIntent(intent: Intent?) {
    if (intent?.action == Intent.ACTION_VIEW) {
      val audioUri = intent.data ?: return
      playerViewModel.importAudioUri(audioUri, autoPlay = true)
    }
  }
}
