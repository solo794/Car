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

## The community's own tested recipe (alternative to the ADB route)

Confirmed directly on the forum thread (dongfeng-aeolus.ru, topic 422) by
the site admin, who reports installing this on two of their own cars in
about an hour. Pre-made flash-drive archives, no manual script typing:

- Flash drive #1 (installs ES File Explorer):
  `https://dongfeng-aeolus.ru/wp-content/uploads/2025/12/USB1.zip`
- Flash drive #2 (auto-launches ES + carries BackButton.apk,
  GKeyboard.apk, `panels.apk` [a minimalist launcher], WiFiManager.apk):
  `https://dongfeng-aeolus.ru/wp-content/uploads/2025/12/USB2.zip`
- Flash drive #3 (removes ES afterwards, once no longer needed):
  `https://dongfeng-aeolus.ru/wp-content/uploads/2025/12/USB3.zip`

Extract only the `update/` folder from each zip to the **root** of a
separate FAT32 USB drive (ignore any `__MACOSX` folder in the zip).

Steps as posted by the forum:
1. Power on the head unit, plug in flash drive #1, wait for it to reboot.
2. **Pull the drive out while it's rebooting** (not before, not after).
3. Once it's back up on the normal launcher, plug in flash drive #2 and
   wait — ES File Explorer opens itself. Grant it storage access, then
   browse to the same drive and install the four APKs in
   `update/soft/` one at a time.
4. `panels.apk` is a ready-made minimalist launcher — useful as an
   immediate proof that "installing our own launcher and setting it as
   Home" is possible on this unit at all, before spending more effort
   wiring up this project's own APK the same way.
5. Once this project's own `app-debug.apk` needs installing, it can be
   dropped on the same USB drive and installed the same way through ES
   File Explorer, then set as the Home app.
6. Flash drive #3 is optional cleanup — removes ES File Explorer once
   it's no longer needed (the forum links a Telegram post explaining why
   one might want to, not yet reviewed here).

This path needs no `adb` at all, at the cost of being less useful for
follow-up work like `adb logcat`-based HVAC broadcast discovery — the
`openADB` route above is still worth doing afterwards for that reason.

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
