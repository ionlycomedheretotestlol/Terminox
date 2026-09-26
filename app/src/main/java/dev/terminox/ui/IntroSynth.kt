package dev.terminox.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Procedural soundtrack for the intro, locked to [IntroTimeline]:
 * calm pad → arp → drums → riser → DROP at the root moment → outro → held chord.
 */
class IntroSynth {
    @Volatile private var running = false
    @Volatile private var fadeOut = false

    fun start() {
        if (running) return
        running = true
        Thread({ play() }, "intro-synth").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    fun stop() { fadeOut = true }

    private fun hz(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    private fun play() {
        val rate = 44100
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(minBuf, rate / 8 * 2))
            .build()
        track.play()

        val T = IntroTimeline
        val chords = arrayOf(intArrayOf(57, 60, 64), intArrayOf(53, 57, 60), intArrayOf(48, 52, 55), intArrayOf(55, 59, 62))
        // Drop lead: one note per 8th, -1 = rest (4 bars).
        val lead = intArrayOf(
            81, -1, 84, 81, -1, 88, 86, 84,   // Am
            77, -1, 81, 77, -1, 84, 81, 79,   // F
            76, -1, 79, 76, -1, 84, 83, 79,   // C
            79, -1, 83, 79, -1, 86, 84, 83,   // G
        )
        val rnd = Random(3)
        var lp = 0.0
        var master = 1.0
        val buf = ShortArray(1024)
        var n = 0L
        val beat = T.BEAT.toDouble()
        val bar = T.BAR.toDouble()

        while (running && master > 0.001) {
            val tNow = n / rate.toDouble()
            if (tNow > T.END + 9) break
            for (i in buf.indices) {
                val t = n / rate.toDouble()
                var s = 0.0
                val final = t >= T.END
                val barIdx = (t / bar).toInt()
                val chord = if (final) intArrayOf(57, 60, 64, 71) else chords[barIdx % 4]
                val tc = if (final) t - T.END else t - barIdx * bar
                val tb = t % beat
                val drop = t >= T.DROP && t < T.OUTRO
                val pump = if (drop) 0.35 + 0.65 * min(1.0, tb / 0.22) else 1.0

                // pad (fades in over the opening; quieter under the drop's lead)
                val padEnv = if (final) min(1.0, tc / 0.5) * exp(-tc / 5.0)
                else min(1.0, tc / 0.25) * min(1.0, (bar - tc) / 0.1) * min(1.0, t / 3.0)
                val padGain = if (drop) 0.035 else 0.05
                for (note in chord) {
                    val f = hz(note)
                    for (h in 1..4) s += pump * padEnv * padGain / h * (sin(2 * PI * f * h * t) + sin(2 * PI * f * 1.005 * h * t))
                }

                if (!final) {
                    val eighth = beat / 2
                    val k = (t / eighth).toInt()
                    val te = t - k * eighth

                    // arp from the setup onward
                    if (t >= T.SETUP && t < T.OUTRO) {
                        val up = if (t >= T.BLOB) 24 else 12
                        val f = hz(chord[k % chord.size] + up)
                        val ph = (f * t) % 1.0
                        s += pump * 0.09 * (4 * abs(ph - 0.5) - 1) * exp(-te * 10)
                    }
                    // kick + hats from the spawn; drums cut for the last beats of the riser
                    val drumsOn = (t >= T.SPAWN && t < T.RISER + 3.0) || drop
                    if (drumsOn) {
                        val kf = 42 + (if (drop) 120 else 90) * exp(-tb * 32)
                        s += (if (drop) 0.75 else 0.5) * sin(2 * PI * kf * tb) * exp(-tb * (if (drop) 7 else 9))
                        val th = (t + beat / 2) % beat
                        s += 0.06 * (rnd.nextDouble() * 2 - 1) * exp(-th * 55)
                    }
                    // clap on 2 & 4
                    if ((t >= T.BLOB && t < T.RISER) || drop) {
                        val tt = (t + beat) % (2 * beat)
                        s += 0.16 * (rnd.nextDouble() * 2 - 1) * exp(-tt * 22)
                    }
                    // bass on the eighths
                    if ((t >= T.ARRANGE && t < T.RISER + 3.0) || drop) {
                        val bf = hz(chord[0] - 24)
                        val ph = (bf * t) % 1.0
                        s += pump * 0.22 * (2 * ph - 1) * exp(-te * 5) * 0.8
                    }
                    // riser: noise swell + rising tone + accelerating snare
                    if (t >= T.RISER && t < T.DROP) {
                        val r = (t - T.RISER) / (T.DROP - T.RISER)
                        s += 0.10 * r * r * (rnd.nextDouble() * 2 - 1)
                        s += 0.08 * r * sin(2 * PI * (200 + 1800 * r * r) * t)
                        val interval = beat / (1 + 7 * r).toInt().coerceAtLeast(1)
                        val ts = t % interval
                        if (t < T.DROP - beat * 0.5) s += 0.18 * (rnd.nextDouble() * 2 - 1) * exp(-ts * 40)
                    }
                    // the drop: detuned supersaw lead
                    if (drop) {
                        val note = lead[k % lead.size]
                        if (note > 0) {
                            val f = hz(note)
                            var saw = 0.0
                            for (d in doubleArrayOf(0.993, 1.0, 1.007)) saw += 2 * ((f * d * t) % 1.0) - 1
                            s += pump * 0.07 * saw * min(1.0, te / 0.01) * exp(-te * 3)
                        }
                        if (t - T.DROP < 1.2) s += 0.35 * (rnd.nextDouble() * 2 - 1) * exp(-(t - T.DROP) * 3) // impact
                    }
                } else if (tc > T.MORPH_AT && tc < T.MORPH_AT + 1.5) {
                    val tt = tc - T.MORPH_AT
                    s += 0.12 * sin(2 * PI * hz(88) * tt) * exp(-tt * 4) + 0.08 * sin(2 * PI * hz(95) * tt) * exp(-tt * 3)
                }

                // brighter filter during the drop
                val cutoff = if (drop) 0.6 else if (t >= T.OUTRO) 0.2 else 0.35
                lp += cutoff * (s - lp)
                if (fadeOut) master *= 0.99985
                buf[i] = (tanh(lp * 1.3) * 0.8 * master * 32767).toInt().coerceIn(-32768, 32767).toShort()
                n++
            }
            track.write(buf, 0, buf.size)
        }
        running = false
        runCatching { track.stop() }
        track.release()
    }
}
