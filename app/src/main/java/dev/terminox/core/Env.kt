package dev.terminox.core

import android.content.Context
import android.os.Build
import java.io.File

/** Filesystem layout and the proot command line that boots Debian. */
class Env(context: Context) {
    val files: File = context.filesDir
    val rootfs = File(files, "debian")
    val tmp = File(files, "tmp")
    val fakeProc = File(files, "fakeproc")
    val installedMarker = File(files, ".installed")
    private val libDir = File(context.applicationInfo.nativeLibraryDir)
    val proot = File(libDir, "libproot.so")
    private val loader = File(libDir, "libproot-loader.so")
    private val loader32 = File(libDir, "libproot-loader32.so")

    val isInstalled get() = installedMarker.exists() && File(rootfs, "bin/bash").exists()

    /** Debian architecture name for this device's primary ABI. */
    val debianArch: String = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "arm64"
        "armeabi-v7a" -> "armhf"
        "x86_64" -> "amd64"
        else -> "arm64"
    }

    /** argv (argv[0] first) for a login shell, or for `command` when given. */
    fun shellArgs(command: String? = null): Array<String> {
        val args = mutableListOf(
            proot.absolutePath,
            "--kill-on-exit",
            "--link2symlink",
            "-0",
            "-L",
            "-r", rootfs.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "${File(rootfs, "tmp").absolutePath}:/dev/shm",
        )
        // Android hides some /proc files from apps; substitute harmless fakes.
        fakeProc.listFiles()?.forEach { f ->
            if (!File("/proc/${f.name}").canRead()) args += listOf("-b", "${f.absolutePath}:/proc/${f.name}")
        }
        for (p in listOf("/sdcard", "/storage")) if (File(p).canRead()) args += listOf("-b", p)
        args += listOf("-w", "/root", "/usr/bin/env", "-i") + guestEnv()
        args += if (command == null) listOf("/bin/bash", "-l") else listOf("/bin/bash", "-lc", command)
        return args.toTypedArray()
    }

    private fun guestEnv() = listOf(
        "HOME=/root",
        "USER=root",
        "TERM=xterm-256color",
        "COLORTERM=truecolor",
        "LANG=C.UTF-8",
        "TMPDIR=/tmp",
        "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
    )

    /** Environment for the proot process itself (host side). */
    fun hostEnv(): Array<String> {
        tmp.mkdirs()
        val env = mutableListOf(
            "PROOT_TMP_DIR=${tmp.absolutePath}",
            "PROOT_LOADER=${loader.absolutePath}",
            "PATH=/system/bin",
            "ANDROID_ROOT=${System.getenv("ANDROID_ROOT") ?: "/system"}",
            "ANDROID_DATA=${System.getenv("ANDROID_DATA") ?: "/data"}",
        )
        if (loader32.exists()) env += "PROOT_LOADER_32=${loader32.absolutePath}"
        return env.toTypedArray()
    }
}
