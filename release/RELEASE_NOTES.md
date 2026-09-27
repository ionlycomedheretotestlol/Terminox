**Terminox is a Hyprland-style tiling terminal for Android that runs real Debian. You don't need root.**

## Features

- **Real Linux:** Debian 13 (trixie), booted with a bundled PRoot. You're root inside it, and `apt install` (or `pkg install`) pulls from Debian's mirrors.
- **Tiling window manager:** dwindle, master, grid and Niri-style scrolling column layouts, with springy animations. Drag windows to swap them, float them, or resize them.
- **Liquid glass:** frosted windows over an image, looping video or animated aurora wallpaper, plus a rotating gradient border on the focused window.
- **The blob (AI agent):** it's powered by Gemini (3.8 Flash, falling back to 3.7 and then 3.6). It types commands, reads the output and installs missing tools. It asks before running anything destructive. You add your own free key in **Settings › AI**.
- **Music player:** search for a song and play it with time-synced lyrics behind your windows, or run `ter-music` in the terminal.
- **Background sessions:** `terminox-lock` keeps every session alive with the screen off, and `terminox-unlock` turns that off.
- **Settings:** 7 desktop themes (Hyprland, Niri, Catppuccin, Tokyo Night, Rosé Pine, Nord, Sway), 6 terminal color schemes, and controls for gaps, rounding, borders, blur and animations. Pinch to zoom the text.
- **Phone storage:** after you allow access, your phone's storage is at `/sdcard` inside Debian.
- **Intro:** a beat-synced trailer with a soundtrack generated in code.

## Install

1. Download **Terminox-1.0.0.apk** below.
2. Open it on your phone. Allow "Install unknown apps" for your browser or file manager when Android asks.
3. Launch Terminox and follow the intro. The first launch downloads Debian (about 90 MB), so use Wi-Fi.

Requires Android 7.0+ on arm64-v8a, armeabi-v7a or x86_64.

This build is signed with a debug key. If you later install a build signed with a different key, uninstall this one first.
