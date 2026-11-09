package com.example.watchspeakerplayer

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.watchspeakerplayer.ui.main.MainScreen
import com.example.watchspeakerplayer.ui.main.WatchPlayerViewModel

@Composable
fun MainNavigation(viewModel: WatchPlayerViewModel = viewModel()) {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(
            onItemClick = { navKey -> backStack.add(navKey) },
            viewModel = viewModel,
          )
        }
      },
  )
}
