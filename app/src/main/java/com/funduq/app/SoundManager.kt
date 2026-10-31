package com.funduq.app

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator

object SoundManager {
    var isMuted: Boolean = false
    private var toneGen: ToneGenerator? = null
    private var bgmPlayer: MediaPlayer? = null

    init {
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (_: Exception) {}
    }

    // 1. Suara "Tring" / Nada Menang (Jawaban Benar)
    fun playSuccess() {
        if (isMuted) return
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
        } catch (_: Exception) {}
    }

    // 2. Suara "Tetot" / Nada Rendah (Jawaban Salah)
    fun playWrong() {
        if (isMuted) return
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_NACK, 260)
        } catch (_: Exception) {}
    }

    // 3. Suara Fanfare Selesai Unit / Pendaftaran
    fun playFanfare() {
        if (isMuted) return
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 350)
        } catch (_: Exception) {}
    }

    // 4. Mulai Backsound Instrumen (Ambient Loop)
    fun startBgm(context: Context) {
        if (isMuted || bgmPlayer?.isPlaying == true) return
        try {
            // Jika Anda sudah memasukkan file audio arabian di res/raw/arabian_bgm.mp3:
            val resId = context.resources.getIdentifier("arabian_bgm", "raw", context.packageName)
            if (resId != 0) {
                bgmPlayer = MediaPlayer.create(context, resId).apply {
                    isLooping = true
                    setVolume(0.25f, 0.25f)
                    start()
                }
            }
        } catch (_: Exception) {}
    }

    fun stopBgm() {
        try {
            bgmPlayer?.stop()
            bgmPlayer?.release()
            bgmPlayer = null
        } catch (_: Exception) {}
    }

    // Toggle Mute / Unmute
    fun toggleMute(context: Context): Boolean {
        isMuted = !isMuted
        if (isMuted) {
            bgmPlayer?.pause()
        } else {
            if (bgmPlayer != null) {
                bgmPlayer?.start()
            } else {
                startBgm(context)
            }
        }
        return isMuted
    }
}
