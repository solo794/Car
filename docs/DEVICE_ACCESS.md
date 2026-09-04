# Getting this launcher onto a real Dongfeng Aeolus / Shine head unit

This unit ships with no Google Play Services and a password-protected
Engineering Mode, which makes the "normal" Android sideloading paths
(Developer Options → USB debugging → `adb install`) hard to reach. The
Russian Dongfeng Aeolus owner community (forum: dongfeng-aeolus.ru,
Telegram: `@aeolusshinegs`, `@GONGFENGSHINEGS`, `@DONGFENG_RB`) found and
published a much simpler route, and their scripts are mirrored at
`github.com/tcse/dongfeng-aeolus.ru`. This doc records how it works so
anyone continuing this project doesn't have to re-discover it.

## The core mechanism: `update/custom_rep.sh`

The head unit's firmware has a built-in "check a plugged-in USB drive for
a software update" routine. If a USB drive (FAT32, ≤32GB) has a file at:

```
update/custom_rep.sh
```

at its **root**, the unit runs that shell script **as root/system**
during its update-check — no Developer Options, no USB-debugging
authorization dialog, and no Engineering Mode password required. This is
the same official update hook the OEM itself uses to push firmware
updates; the community is just pointing it at their own script instead.

Confirmed capabilities from the scripts published in that repo:
- `mount -o remount,rw /system` — the hook already has permission to
  remount `/system` writable.
- `pm install` / `pm uninstall`, `am start`, `am force-stop` — normal
  package management.
- `stop adbd` / `start adbd` and `otg_control.sh` — control over the ADB
  daemon and the USB OTG mode.
- Writing into `/system/priv-app/...` with `chown root:root` /
  `chmod 644` — i.e. installing or replacing a **system app**, which is
  exactly what's needed to swap out the stock launcher
  (`com.qinggan.app.launcher`, "Qinggan" being the automotive
  Android platform vendor used here).

### ⚠️ Risk

This is exactly as powerful as root — a bad script can break the head
unit. The community's own README warns: use at your own risk, the
authors take no responsibility for hardware damage, and if the car is
still under warranty, check with the dealer first. Same caveat applies
here.

## Fastest path: enable ADB with no password needed

The repo ships a script at `Others/openADB/update/custom_rep.sh`:

```sh
#!/system/bin/sh
log -p e -t FIX "Hello open adb start"
otg_control.sh 1
stop adbd
sleep 2
start adbd
log -p e -t FIX "Hello open adb end"
```

Steps:
1. Format a spare USB drive as FAT32 (≤32GB).
2. Create a folder named `update` at the drive's root.
3. Save the script above as `custom_rep.sh` inside that folder, so the
   path on the drive is `update/custom_rep.sh`. Save it with **Unix (LF)
   line endings** — a plain Windows Notepad save can add `\r` characters
   that break the shebang/shell parsing. (Simplest: download the raw
   file directly from GitHub rather than retyping it.)
4. Insert the USB drive into the car's USB port and give it a minute (a
   reboot may happen automatically, matching the pattern in the other
   `custom_rep.sh` scripts in this repo).
5. Connect the same USB port to a PC with `adb` installed and run
   `adb devices`. It should now show the head unit with no on-screen
   "Allow USB debugging?" prompt needed.

If nothing shows up, power-cycle the head unit once — some units only
process `/update/` on the next boot rather than immediately on insert.

## Once ADB works

- `adb install -r app-debug.apk` — install this launcher like any normal
  app, no system-partition edits needed yet.
- `adb shell am start -a android.settings.HOME_SETTINGS` — opens
  Android's built-in "choose Home app" screen directly, bypassing
  whatever restricted settings UI the OEM shows. Pick this launcher and
  set it as the default ("Always").
- `adb logcat` while operating the real HVAC/AC hardware buttons — the
  way to discover the actual broadcast action names so this launcher's
  climate widget can send real commands instead of only tracking local
  state (see the main README's "climate widget" caveat).
- `adb shell` (+ `su` if available) — for anything requiring true root,
  e.g. writing this launcher into `/system/priv-app/` to fully replace
  `com.qinggan.app.launcher` the way the community's own
  "restore stock launcher" script (`custom_rep.sh` variants in this same
  repo) does in reverse.

## Other ready-made hooks in that repo worth knowing about

- `Others/Settingslaunch/update/custom_rep.sh` — force-opens the real
  Android Settings app (`com.android.settings/.Settings`), useful if the
  OEM's own Settings entry point is restricted or missing.
- `Others/Recovery/update`, `Others/temper/update`,
  `Others/Testes_sh/update`, `Others/FreeForm/update` — present in the
  repo but not yet reviewed for this project; check them if the ADB path
  above doesn't pan out.
- `USB#1` / `USB#2` / `USB#3` folders — the community's own step-by-step
  packages (install ES File Explorer, then use it to sideload
  `panels.apk` — their minimalist launcher — plus a keyboard, a
  hardware back-button app, and a WiFi manager) for people who don't
  want to touch ADB at all.
