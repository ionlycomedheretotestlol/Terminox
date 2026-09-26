package dev.terminox.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tiny procedural synth for the intro: Am–F–C–G pads, a triangle arpeggio,
 * kick and hats that build in, then a held chord under "Shall we go?".
 */
class IntroSynth {
    @Volatile private var running = false
    @Volatile private var fadeOut = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ play() }, "intro-synth").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    fun stop() { fadeOut = true }

    private fun play() {
        val rate = 44100
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(minBuf, rate / 5 * 2))
            .build()
        track.play()

        fun hz(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)
        val chords = arrayOf(intArrayOf(57, 60, 64), intArrayOf(53, 57, 60), intArrayOf(48, 52, 55), intArrayOf(55, 59, 62))
        val beat = 60.0 / 96
        val bar = beat * 4
        val songEnd = bar * 8
        val total = songEnd + 9.0
        val rnd = Random(7)
        var lp = 0.0
        var master = 1.0
        val buf = ShortArray(1024)
        var n = 0L

        while (running && n / rate.toDouble() < total && master > 0.001) {
            for (i in buf.indices) {
                val t = n / rate.toDouble()
                var s = 0.0
                val barIdx = (t / bar).toInt()
                val final = t >= songEnd
                val chord = if (final) intArrayOf(57, 60, 64, 71) else chords[barIdx % 4]
                val tc = if (final) t - songEnd else t - barIdx * bar
                val env = if (final) min(1.0, tc / 0.6) * exp(-tc / 5.0) else min(1.0, tc / 0.3) * min(1.0, (bar - tc) / 0.12)

                // pad: soft saw-ish (4 harmonics), slightly detuned
                for (note in chord) {
                    val f = hz(note)
                    for (h in 1..4) s += env * 0.05 / h * (sin(2 * PI * f * h * t) + sin(2 * PI * f * 1.004 * h * t))
                }
                // bass
                s += env * 0.16 * sin(2 * PI * hz(chord[0] - 12) * t)

                if (!final) {
                    // triangle arpeggio, eighth notes
                    val eighth = beat / 2
                    val k = (t / eighth).toInt()
                    val te = t - k * eighth
                    val f = hz(chord[k % chord.size] + 12 + if (k % 8 >= 4) 12 else 0)
                    val ph = (f * t) % 1.0
                    val tri = 4 * kotlin.math.abs(ph - 0.5) - 1
                    s += 0.10 * tri * exp(-te * 9) * min(1.0, t / 2.0)

                    val tb = t % beat
                    if (barIdx >= 2) { // kick
                        val kf = 45 + 90 * exp(-tb * 30)
                        s += 0.5 * sin(2 * PI * kf * tb) * exp(-tb * 9)
                    }
                    if (barIdx >= 4) { // off-beat hats
                        val th = (t + beat / 2) % beat
                        s += 0.05 * (rnd.nextDouble() * 2 - 1) * exp(-th * 60)
                    }
                } else if (tc < 1.5) { // chime when the button appears
                    val tt = tc
                    s += 0.12 * sin(2 * PI * hz(88) * tt) * exp(-tt * 4) + 0.08 * sin(2 * PI * hz(95) * tt) * exp(-tt * 3)
                }

                lp += 0.35 * (s - lp) // gentle low-pass
                if (fadeOut) master *= 0.99985
                val v = (kotlin.math.tanh(lp * 1.2) * 0.8 * master * 32767).toInt()
                buf[i] = v.coerceIn(-32768, 32767).toShort()
                n++
            }
            track.write(buf, 0, buf.size)
        }
        running = false
        runCatching { track.stop() }
        track.release()
    }
}
