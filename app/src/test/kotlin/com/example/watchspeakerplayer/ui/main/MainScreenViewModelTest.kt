package com.example.watchspeakerplayer.ui.main

import com.example.watchspeakerplayer.data.DataRepository
import com.example.watchspeakerplayer.data.SampleWaveGenerator
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.file.Files

class MainScreenViewModelTest {
  @Test
  fun uiState_initiallyLoading() = runTest {
    val viewModel = MainScreenViewModel(FakeMyModelRepository())
    assertEquals(viewModel.uiState.first(), MainScreenUiState.Loading)
  }

  @Test
  fun sampleWaveGenerator_createsValidWavFiles() {
    val tempDir = Files.createTempDirectory("watch_wav_test").toFile()
    try {
      val generated = SampleWaveGenerator.ensureSampleTracks(tempDir)
      assertEquals(3, generated.size)
      generated.forEach { (file, _) ->
        assertTrue(file.exists())
        assertTrue(file.length() > 44L)
        val header = file.inputStream().use { input ->
          val bytes = ByteArray(12)
          input.read(bytes)
          String(bytes, Charsets.US_ASCII)
        }
        assertTrue(header.startsWith("RIFF"))
        assertTrue(header.endsWith("WAVE"))
      }
    } finally {
      tempDir.deleteRecursively()
    }
  }
}

private class FakeMyModelRepository : DataRepository {
  override val data: Flow<List<String>> = flow { emit(listOf("Sample")) }
}
