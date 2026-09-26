package dev.terminox.core

import android.system.Os
import java.io.File

/** Shell profile and helper commands installed into Debian on every app start. */
object Scripts {
    fun install(env: Env) {
        if (!env.isInstalled) return
        env.ipcDir.mkdirs()
        runCatching { Os.chmod(env.ipcDir.path, 511) }
        write(env, "etc/profile.d/terminox.sh", PROFILE, 420)
        write(env, "usr/local/bin/terminox-ipc", IPC, 493)
        write(env, "usr/local/bin/terminox-lock", LOCK, 493)
        write(env, "usr/local/bin/terminox-unlock", UNLOCK, 493)
        write(env, "usr/local/bin/ter-music", MUSIC, 493)
        write(env, "usr/local/bin/terminox-help", HELP, 493)
    }

    private fun write(env: Env, path: String, text: String, mode: Int) {
        val f = File(env.rootfs, path)
        f.parentFile?.mkdirs()
        if (f.exists() && f.readText() == text) return
        f.delete()
        f.writeText(text)
        runCatching { Os.chmod(f.path, mode) }
    }

    private val PROFILE = """
        export PS1='\[\e[1;36m\]\u\[\e[0m\]@\[\e[1;32m\]terminox\[\e[0m\] \[\e[1;34m\]\w\[\e[0m\] \$ '
        export HISTCONTROL=ignoreboth
        alias ls='ls --color=auto'
        alias ll='ls -lah --color=auto'
        alias pkg='apt'
        # Used by the AI assistant: runs a command visibly and saves its output + exit code.
        __tx() {
          local id="${'$'}1"
          { export DEBIAN_FRONTEND=noninteractive; eval "${'$'}2"; } 2>&1 | tee "/tmp/.tx-${'$'}id.out"
          echo "${'$'}{PIPESTATUS[0]}" > "/tmp/.tx-${'$'}id.rc"
        }
        if [ -z "${'$'}TERMINOX_GREETED" ]; then
          export TERMINOX_GREETED=1
          printf '\e[1;36mterminox\e[0m · Debian on your phone. Type \e[1mterminox-help\e[0m to start.\n'
        fi
    """.trimIndent() + "\n"

    private val IPC = """
        #!/bin/sh
        # Sends a command to the Terminox app: one argument per line.
        d=/run/terminox
        t="${'$'}d/.tmp.${'$'}${'$'}"
        printf '%s\n' "${'$'}@" > "${'$'}t" && mv "${'$'}t" "${'$'}d/cmd.${'$'}${'$'}.${'$'}(date +%s%N)"
    """.trimIndent() + "\n"

    private val LOCK = """
        #!/bin/sh
        terminox-ipc lock
        printf '\e[1;32m🔒 terminox-lock\e[0m: sessions keep running in the background, screen off included.\n   Run \e[1mterminox-unlock\e[0m to release.\n'
    """.trimIndent() + "\n"

    private val UNLOCK = """
        #!/bin/sh
        terminox-ipc unlock
        printf '\e[1;33m🔓 terminox-unlock\e[0m: background lock released.\n'
    """.trimIndent() + "\n"

    private val HELP = """
        #!/bin/sh
        cat <<'TXT'
        Terminox runs a real Debian system (no root needed).

          apt update / apt install X   install anything from Debian's mirrors
          pkg install X                same as apt
          terminox-lock                keep everything running in the background
          terminox-unlock              release the background lock
          ter-music [song artist]      music player with live lyrics
          /sdcard                      phone storage (if permission granted)
        TXT
    """.trimIndent() + "\n"

    private val MUSIC = """
        #!/bin/bash
        # ter-music: search, pick, and watch synced lyrics right in the terminal.
        D=/run/terminox
        q="${'$'}*"
        if [ -z "${'$'}q" ]; then read -rp $'\e[1;36m♪\e[0m Song (and artist): ' q; fi
        [ -z "${'$'}q" ] && exit 0
        rm -f "${'$'}D/music-results" "${'$'}D/music-now"
        terminox-ipc music-search "${'$'}q"
        printf '\e[2mSearching…\e[0m\n'
        for _ in $(seq 150); do [ -f "${'$'}D/music-results" ] && break; sleep 0.1; done
        if [ ! -f "${'$'}D/music-results" ]; then echo "Terminox didn't answer. Is the Music Player enabled in Settings › Advanced?"; exit 1; fi
        mapfile -t R < "${'$'}D/music-results"
        if [ "${'$'}{R[0]}" = "ERROR" ]; then echo "${'$'}{R[1]}"; exit 1; fi
        if [ "${'$'}{#R[@]}" -eq 0 ]; then echo "Nothing found."; exit 1; fi
        printf '\n\e[1;36m  Results\e[0m\n'
        for i in "${'$'}{!R[@]}"; do printf '  \e[1;35m%2d\e[0m  %s\n' $((i+1)) "${'$'}{R[${'$'}i]}"; done
        read -rp $'\n  Pick #: ' n
        [[ "${'$'}n" =~ ^[0-9]+$ ]] || exit 0
        terminox-ipc music-pick "$((n-1))"
        printf '\n  \e[2mOpening the player… if YouTube shows a list, tap the song.\e[0m\n'

        cols() { tput cols 2>/dev/null || echo 60; }
        rows() { tput lines 2>/dev/null || echo 20; }
        center() { local s="${'$'}1" c=${'$'}(cols); local pad=$(( (c - ${'$'}{#s}) / 2 )); ((pad<0)) && pad=0; printf '%*s' "${'$'}pad" ''; }
        cleanup() { tput cnorm; printf '\e[0m'; clear; }
        trap cleanup EXIT
        tput civis
        last=""
        while :; do
          if read -rsn1 -t 0.25 k; then
            case "${'$'}k" in q) terminox-ipc music-stop; break ;; " ") terminox-ipc music-toggle ;; esac
          fi
          [ -f "${'$'}D/music-now" ] || continue
          mapfile -t L < "${'$'}D/music-now"
          # 0 title · 1 artist · 2 progress 0-100 · 3 time · 4 prev · 5 current · 6 next · 7 next2 · 8 state
          key="${'$'}{L[5]}|${'$'}{L[3]}|${'$'}{L[8]}"
          [ "${'$'}key" = "${'$'}last" ] && continue
          last="${'$'}key"
          clear
          h=${'$'}(rows); top=$(( h / 2 - 6 )); ((top<0)) && top=0
          for ((i=0;i<top;i++)); do echo; done
          center "${'$'}{L[0]}"; printf '\e[1;36m%s\e[0m\n' "${'$'}{L[0]}"
          center "${'$'}{L[1]}"; printf '\e[2m%s\e[0m\n\n' "${'$'}{L[1]}"
          center "${'$'}{L[4]}"; printf '\e[2m%s\e[0m\n\n' "${'$'}{L[4]}"
          center "${'$'}{L[5]}"; printf '\e[1;97m%s\e[0m\n\n' "${'$'}{L[5]}"
          center "${'$'}{L[6]}"; printf '\e[2m%s\e[0m\n' "${'$'}{L[6]}"
          center "${'$'}{L[7]}"; printf '\e[2;90m%s\e[0m\n\n' "${'$'}{L[7]}"
          w=$(( $(cols) - 20 )); ((w<10)) && w=10
          f=$(( ${'$'}{L[2]:-0} * w / 100 ))
          bar="$(printf '%*s' "${'$'}f" '' | tr ' ' '━')$(printf '%*s' $((w-f)) '' | tr ' ' '─')"
          center "xxxxxxxxx${'$'}bar"; printf '\e[35m%s\e[0m \e[36m%s\e[0m\n' "${'$'}{L[3]}" "${'$'}bar"
          center "space pause · q quit"; printf '\e[2mspace pause · q quit%s\e[0m' "${'$'}( [ "${'$'}{L[8]}" = paused ] && echo '  (paused)')"
        done
    """.trimIndent() + "\n"
}
