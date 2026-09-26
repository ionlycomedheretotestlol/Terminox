package dev.terminox.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.tukaani.xz.XZInputStream
import java.io.File
import java.util.concurrent.TimeUnit

/** Downloads the official Debian rootfs and prepares it for proot. */
class Installer(private val env: Env) {
    data class Progress(val step: String, val fraction: Float, val log: List<String> = emptyList(), val error: String? = null)

    private val _progress = MutableStateFlow(Progress("Waiting", 0f))
    val progress = _progress.asStateFlow()

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun post(step: String, fraction: Float, line: String? = null) {
        val p = _progress.value
        _progress.value = p.copy(step = step, fraction = fraction, log = if (line != null) (p.log + line).takeLast(200) else p.log, error = null)
    }

    suspend fun install(release: String = "trixie") = withContext(Dispatchers.IO) {
        try {
            post("Finding the freshest Debian", 0.02f, "arch: ${env.debianArch}")
            val url = latestImage(release)
            post("Found image", 0.04f, url)

            val archive = File(env.files, "rootfs.tar.xz")
            download(url, archive)

            post("Unpacking Debian", 0.62f, "extracting to ${env.rootfs}")
            env.rootfs.deleteRecursively()
            env.rootfs.mkdirs()
            var count = 0
            XZInputStream(archive.inputStream().buffered(1 shl 20)).use { xz ->
                Tar.extract(xz, env.rootfs) { name ->
                    if (++count % 400 == 0) post("Unpacking Debian", 0.62f + minOf(count / 30000f, 0.3f), name)
                }
            }
            archive.delete()
            post("Configuring", 0.94f, "$count files unpacked")
            configure()
            env.installedMarker.writeText(url)
            post("Done", 1f, "Debian is ready")
        } catch (t: Throwable) {
            _progress.value = _progress.value.copy(error = t.message ?: t.toString())
        }
    }

    /** Reads the linuxcontainers.org image index and returns the newest rootfs URL. */
    private fun latestImage(release: String): String {
        val base = "https://images.linuxcontainers.org"
        val index = http.newCall(Request.Builder().url("$base/meta/1.0/index-system").build()).execute().use {
            if (!it.isSuccessful) error("Image index: HTTP ${it.code}")
            it.body!!.string()
        }
        val path = index.lineSequence()
            .map { it.split(';') }
            .firstOrNull { it.size >= 6 && it[0] == "debian" && it[1] == release && it[2] == env.debianArch && it[3] == "default" }
            ?.get(5) ?: error("No Debian $release image for ${env.debianArch}")
        return "$base${path}rootfs.tar.xz"
    }

    private fun download(url: String, out: File) {
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("Download: HTTP ${resp.code}")
            val body = resp.body!!
            val total = body.contentLength().takeIf { it > 0 } ?: 90_000_000L
            var read = 0L
            var lastMb = -1L
            body.byteStream().use { input ->
                out.outputStream().buffered(1 shl 20).use { os ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        read += n
                        val mb = read / 1_000_000
                        if (mb != lastMb) {
                            lastMb = mb
                            post("Downloading Debian", 0.05f + 0.55f * read / total, "${mb} / ${total / 1_000_000} MB")
                        }
                    }
                }
            }
        }
    }

    private fun configure() {
        val r = env.rootfs
        fun write(path: String, text: String, mode: Int = 420) {
            val f = File(r, path)
            f.parentFile?.mkdirs()
            f.delete()
            f.writeText(text)
            runCatching { android.system.Os.chmod(f.path, mode) }
        }
        File(r, "tmp").apply { mkdirs(); setWritable(true, false) }
        write("etc/resolv.conf", "nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        write("etc/hosts", "127.0.0.1 localhost\n::1 localhost ip6-localhost ip6-loopback\n127.0.1.1 terminox\n")
        write("etc/hostname", "terminox\n")
        // proot fakes root, so apt must not try to drop privileges to _apt.
        write("etc/apt/apt.conf.d/99terminox", "APT::Sandbox::User \"root\";\nAcquire::Retries \"3\";\n")
        write("etc/profile.d/terminox.sh", PROFILE)
        write("usr/local/bin/terminox-help", HELP, 493)

        // Harmless stand-ins for /proc files Android hides from apps.
        env.fakeProc.mkdirs()
        File(env.fakeProc, "loadavg").writeText("0.12 0.10 0.08 1/100 1000\n")
        File(env.fakeProc, "uptime").writeText("${android.os.SystemClock.elapsedRealtime() / 1000}.00 0.00\n")
        File(env.fakeProc, "version").writeText("Linux version ${System.getProperty("os.version")} (terminox) #1 SMP PREEMPT\n")
        File(env.fakeProc, "stat").writeText(
            "cpu  1000 0 1000 100000 0 0 0 0 0 0\n" +
            (0 until Runtime.getRuntime().availableProcessors()).joinToString("") { "cpu$it 250 0 250 25000 0 0 0 0 0 0\n" } +
            "intr 0\nctxt 0\nbtime 0\nprocesses 1\nprocs_running 1\nprocs_blocked 0\n"
        )
        File(env.fakeProc, "vmstat").writeText("nr_free_pages 100000\npgpgin 0\npgpgout 0\npswpin 0\npswpout 0\n")
    }

    companion object {
        private val PROFILE = """
            export PS1='\[\e[1;36m\]\u\[\e[0m\]@\[\e[1;32m\]terminox\[\e[0m\] \[\e[1;34m\]\w\[\e[0m\] \$ '
            alias ls='ls --color=auto'
            alias ll='ls -lah --color=auto'
            alias pkg='apt'
            if [ -z "${'$'}TERMINOX_GREETED" ]; then
              export TERMINOX_GREETED=1
              printf '\e[1;36mterminox\e[0m · Debian on your phone. Type \e[1mterminox-help\e[0m to start.\n'
            fi
        """.trimIndent() + "\n"

        private val HELP = """
            #!/bin/sh
            cat <<'TXT'
            Terminox runs a real Debian system (no root needed).

              apt update                 refresh package lists
              apt install neofetch       install anything from Debian's mirrors
              pkg install ...            same as apt (alias)
              /sdcard                    your phone storage (if permission granted)
            TXT
        """.trimIndent() + "\n"
    }
}
