package com.kimrosil.pitchgrid

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class AudioToneTileService : TileService() {

    companion object {
        private var sharedAudioEngine: AudioSynthesisEngine? = null

        fun isGlobalPlaying(): Boolean = sharedAudioEngine?.isPlaying() == true

        @Synchronized
        fun togglePlayback(freq: Double = 440.0, waveform: WaveformType = WaveformType.SINE): Boolean {
            if (sharedAudioEngine == null) {
                sharedAudioEngine = AudioSynthesisEngine()
            }
            val engine = sharedAudioEngine!!
            if (engine.isPlaying()) {
                engine.stop()
                return false
            } else {
                engine.setFrequency(freq)
                engine.setWaveform(waveform)
                engine.setVolume(0.5f)
                engine.start()
                return true
            }
        }

        @Synchronized
        fun stopGlobal() {
            sharedAudioEngine?.stop()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isNowPlaying = togglePlayback()
        updateTileState(isNowPlaying)
    }

    private fun updateTileState(active: Boolean = isGlobalPlaying()) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (active) "PitchGrid: 440Hz" else "PitchGrid Tone"
        tile.updateTile()
    }
}
