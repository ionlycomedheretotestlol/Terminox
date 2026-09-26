package dev.terminox.core

import android.system.Os
import java.io.File
import java.io.InputStream

/** Minimal streaming tar extractor (ustar, GNU long names, pax paths). */
object Tar {
    private class Entry(val name: String, val mode: Int, val size: Long, val type: Char, val link: String)

    fun extract(input: InputStream, dest: File, onEntry: (String) -> Unit = {}) {
        val header = ByteArray(512)
        var longName: String? = null
        var longLink: String? = null
        val dirs = mutableListOf<Pair<File, Int>>()
        val destPath = dest.canonicalPath
        while (true) {
            if (!readFully(input, header)) break
            if (header.all { it.toInt() == 0 }) break
            val e = parse(header, longName, longLink)
            longName = null; longLink = null
            when (e.type) {
                'L' -> { longName = readString(input, e.size); continue }
                'K' -> { longLink = readString(input, e.size); continue }
                'x' -> {
                    val pax = readString(input, e.size)
                    pax.lineSequence().forEach { line ->
                        val kv = line.substringAfter(' ', "")
                        if (kv.startsWith("path=")) longName = kv.removePrefix("path=")
                        if (kv.startsWith("linkpath=")) longLink = kv.removePrefix("linkpath=")
                    }
                    continue
                }
                'g' -> { skip(input, padded(e.size)); continue }
            }
            val rel = e.name.removePrefix("./").trimEnd('/')
            if (rel.isEmpty()) { skip(input, padded(e.size)); continue }
            val out = File(dest, rel)
            if (!out.canonicalPath.startsWith(destPath)) { skip(input, padded(e.size)); continue }
            onEntry(rel)
            out.parentFile?.mkdirs()
            when (e.type) {
                '5' -> { out.mkdirs(); dirs += out to e.mode }
                '2' -> {
                    out.delete()
                    runCatching { Os.symlink(e.link, out.path) }
                }
                '1' -> {
                    // Hard links are not permitted in app storage: copy the target instead.
                    val target = File(dest, e.link.removePrefix("./"))
                    out.delete()
                    if (target.isFile) { target.copyTo(out, overwrite = true); chmod(out, e.mode) }
                }
                '0', '\u0000', '7' -> {
                    out.delete()
                    out.outputStream().buffered(1 shl 16).use { os ->
                        var left = e.size
                        val buf = ByteArray(1 shl 16)
                        while (left > 0) {
                            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                            if (n < 0) error("Unexpected end of archive")
                            os.write(buf, 0, n); left -= n
                        }
                    }
                    skip(input, padded(e.size) - e.size)
                    chmod(out, e.mode)
                    continue
                }
                else -> {} // devices / fifos are not needed under proot
            }
            skip(input, padded(e.size))
        }
        // Apply directory modes last so read-only dirs don't block their own contents.
        dirs.asReversed().forEach { (d, m) -> chmod(d, m or 0b111_000_000) }
    }

    private fun chmod(f: File, mode: Int) = runCatching { Os.chmod(f.path, mode and 0xFFF or 0b110_000_000) }

    private fun parse(h: ByteArray, longName: String?, longLink: String?): Entry {
        fun str(off: Int, len: Int): String {
            var end = off
            while (end < off + len && h[end].toInt() != 0) end++
            return String(h, off, end - off, Charsets.UTF_8)
        }
        fun num(off: Int, len: Int): Long {
            if (h[off].toInt() and 0x80 != 0) { // base-256
                var v = 0L
                for (i in off + 1 until off + len) v = (v shl 8) or (h[i].toLong() and 0xFF)
                return v
            }
            val s = str(off, len).trim()
            return if (s.isEmpty()) 0 else s.toLong(8)
        }
        val prefix = if (str(257, 6).startsWith("ustar")) str(345, 155) else ""
        val base = str(0, 100)
        val name = longName ?: if (prefix.isNotEmpty()) "$prefix/$base" else base
        return Entry(name, num(100, 8).toInt(), num(124, 12), h[156].toInt().toChar(), longLink ?: str(157, 100))
    }

    private fun padded(size: Long) = (size + 511) / 512 * 512

    private fun readString(input: InputStream, size: Long): String {
        val buf = ByteArray(size.toInt())
        readFully(input, buf)
        skip(input, padded(size) - size)
        return String(buf, Charsets.UTF_8).trimEnd('\u0000')
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) return off > 0 && error("Truncated archive")
            off += n
        }
        return true
    }

    private fun skip(input: InputStream, n: Long) {
        var left = n
        val buf = ByteArray(8192)
        while (left > 0) {
            val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (r < 0) return
            left -= r
        }
    }
}
