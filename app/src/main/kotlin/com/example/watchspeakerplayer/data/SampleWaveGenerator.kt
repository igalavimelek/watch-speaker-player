package com.example.watchspeakerplayer.data

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Generates sample 16-bit PCM WAV files in the app's Music directory so the user can test
 * OnePlus Watch 3 speaker playback immediately out of the box.
 */
object SampleWaveGenerator {
  private const val SAMPLE_RATE = 44100

  data class GeneratedSampleSpec(
    val fileName: String,
    val displayTitle: String,
    val subtitle: String,
    val durationSeconds: Int,
    val generator: (sampleIndex: Int, totalSamples: Int) -> Double,
  )

  fun ensureSampleTracks(targetDir: File): List<Pair<File, GeneratedSampleSpec>> {
    if (!targetDir.exists()) {
      targetDir.mkdirs()
    }

    val specs = listOf(
      GeneratedSampleSpec(
        fileName = "Horizon_Chimes.wav",
        displayTitle = "Horizon Chimes",
        subtitle = "Built-in WAV • Harmonic Arpeggio",
        durationSeconds = 6,
      ) { i, _ ->
        val t = i.toDouble() / SAMPLE_RATE
        // C5, E5, G5, B5, C6 arpeggio notes every 0.5s
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 987.77, 1046.50, 783.99)
        val stepDuration = 0.5
        val noteIndex = ((t / stepDuration).toInt()) % notes.size
        val localT = t % stepDuration
        val freq = notes[noteIndex]
        val envelope = exp(-3.5 * localT) * (1.0 - exp(-80.0 * localT))
        val fundamental = sin(2.0 * PI * freq * t)
        val overtone = 0.35 * sin(2.0 * PI * (freq * 2.0) * t)
        val warmSub = 0.2 * sin(2.0 * PI * (freq * 0.5) * t)
        (fundamental + overtone + warmSub) * envelope * 0.65
      },
      GeneratedSampleSpec(
        fileName = "Pulse_Synth_Beat.wav",
        displayTitle = "Pulse Synth Beat",
        subtitle = "Built-in WAV • Rhythmic Synth",
        durationSeconds = 6,
      ) { i, _ ->
        val t = i.toDouble() / SAMPLE_RATE
        val beatDuration = 0.375 // 160 BPM eighth-note grid
        val step = ((t / beatDuration).toInt()) % 8
        val localT = t % beatDuration
        val bassNotes = doubleArrayOf(329.63, 329.63, 392.00, 440.00, 493.88, 440.00, 392.00, 293.66)
        val freq = bassNotes[step]
        val env = exp(-4.5 * localT) * (1.0 - exp(-100.0 * localT))
        val lead = sin(2.0 * PI * freq * t) + 0.4 * sin(2.0 * PI * freq * 1.5 * t)
        val click = if (localT < 0.025) sin(2.0 * PI * 1400.0 * localT) * exp(-60.0 * localT) else 0.0
        (lead * env * 0.55) + (click * 0.25)
      },
      GeneratedSampleSpec(
        fileName = "Speaker_Range_Test.wav",
        displayTitle = "Speaker Range Check",
        subtitle = "Built-in WAV • 300Hz–1.4kHz Sweep",
        durationSeconds = 5,
      ) { i, total ->
        val t = i.toDouble() / SAMPLE_RATE
        val progress = i.toDouble() / total.coerceAtLeast(1)
        // Instantaneous phase for linear chirp from 300 Hz to 1400 Hz
        val f0 = 300.0
        val f1 = 1400.0
        val duration = total.toDouble() / SAMPLE_RATE
        val k = (f1 - f0) / duration
        val phase = 2.0 * PI * (f0 * t + 0.5 * k * t * t)
        val fade = when {
          progress < 0.05 -> progress / 0.05
          progress > 0.92 -> (1.0 - progress) / 0.08
          else -> 1.0
        }
        sin(phase) * fade * 0.7
      },
    )

    return specs.map { spec ->
      val file = File(targetDir, spec.fileName)
      if (!file.exists() || file.length() < 1024L) {
        writeWavFile(file, spec.durationSeconds, spec.generator)
      }
      file to spec
    }
  }

  private fun writeWavFile(
    file: File,
    durationSeconds: Int,
    sampleGenerator: (sampleIndex: Int, totalSamples: Int) -> Double,
  ) {
    val totalSamples = SAMPLE_RATE * durationSeconds
    val dataSize = totalSamples * 2 // 16-bit mono = 2 bytes per sample
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

    // RIFF header
    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt(36 + dataSize)
    header.put("WAVE".toByteArray(Charsets.US_ASCII))

    // fmt subchunk
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16) // PCM chunk size
    header.putShort(1) // AudioFormat 1 = PCM
    header.putShort(1) // NumChannels = 1 (Mono)
    header.putInt(SAMPLE_RATE)
    header.putInt(SAMPLE_RATE * 2) // ByteRate
    header.putShort(2) // BlockAlign
    header.putShort(16) // BitsPerSample

    // data subchunk
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(dataSize)

    FileOutputStream(file).use { out ->
      out.write(header.array())
      val chunkSamples = 4096
      val buffer = ByteBuffer.allocate(chunkSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
      var i = 0
      while (i < totalSamples) {
        buffer.clear()
        val end = (i + chunkSamples).coerceAtMost(totalSamples)
        while (i < end) {
          val normalized = sampleGenerator(i, totalSamples).coerceIn(-1.0, 1.0)
          val pcm16 = (normalized * Short.MAX_VALUE).toInt().toShort()
          buffer.putShort(pcm16)
          i++
        }
        out.write(buffer.array(), 0, buffer.position())
      }
    }
  }
}
