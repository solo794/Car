# Car integration research: what "listening to the API" really means here

Question: someone told the project owner that the launcher must "listen to
the API" to integrate with (read and control) the car. This doc separates
what is **confirmed**, what is **inferred**, and what is **unknown**, so the
next step is an experiment, not a guess.

## 1. There is no public car API on this unit

- The head unit is a Qualcomm MSM8953 running a plain **Android 8.1**
  (not Android Automotive). Android Automotive's `CarService` /
  `android.car.*` / Vehicle HAL (the thing with `HVAC_TEMPERATURE_SET`,
  `CONTROL_CAR_CLIMATE`, etc.) is a different OS flavour. It should be
  checked, not assumed: `adb shell pm list features | grep automotive` and
  `adb shell service list | grep -i car`.
- Web searches for Qinggan (the platform vendor behind
  `com.qinggan.app.launcher`) intents, MCU protocol or HVAC messages found
  **nothing public**. Any integration therefore has to be reverse-engineered
  from the unit itself.
- Generic Android head units talk to the car as: Android app <-> vendor
  system service <-> serial link <-> MCU <-> CAN bus. Third-party apps are
  never given the serial link; they only see whatever the vendor service
  chooses to expose (broadcasts, a bound service, settings/properties, or
  log lines).

## 2. Confirmed from the community repo (tcse/dongfeng-aeolus.ru)

`Others/temper/update/custom_rep.sh` and `Others/Testes_sh/update/custom_rep.sh`:

```sh
su -c "pm grant com.example.temper android.permission.READ_LOGS"
```

- **Dongfeng Temper** (a community app that shows the *outside
  temperature* on the head unit) needs `READ_LOGS`. That permission only
  exists to read **logcat**. So the practical "API" the community found for
  reading car data is: *the vendor service prints the value in logcat and the
  app parses the log line.* This is the strongest evidence we have for what
  "listen to the API" means on this unit.
- The same hook grants it via `pm grant` as root, which is why a USB
  `update/custom_rep.sh` is enough (no on-screen prompt, no developer mode).
- `Testes_sh` also sets `enable_freeform_support`, `force_resizable_activities`
  and `policy_control` so **LecoAuto Launcher's PiP/free-form windows work**.
  Relevant to this project's PiP feature: free-form windows need those
  global settings on this firmware.
- Other community tools show other signals are reachable: *Butty* (hardware
  button remapping), *DFM Sidebar* (side app panel), *panels.apk*.

## 3. Inferred (not yet verified on a real unit)

- Temper's value comes from a log tag/line emitted by a Qinggan/MCU service.
  The exact tag and format are **unknown** until we read logcat.
- Other HVAC state (set temperature, fan level, AUTO, recirculation) is
  probably logged or broadcast the same way, because the native HVAC bar
  must receive it from somewhere. Not confirmed.
- `su` seems available on the unit (scripts call `su -c`), or those lines
  run as root already inside the update hook. Either way, root-level
  discovery is possible through the USB hook.

## 4. Unknown (this is what the experiments must answer)

1. Which log tag/lines carry HVAC / temperature / door / speed data?
2. Does the native HVAC bar receive **broadcast intents** (reversible to
   discover) or a **bound service call** (needs the interface, via the APK)?
3. Is there a **write path** a third-party app may use (exported receiver /
   service with no signature permission)? Many OEM services are
   `signature|privileged` only.

## 5. Experiment plan (needs adb or the USB hook)

Read side ("listen"):
1. `adb logcat -v time > hvac.log` while pressing each native AC button and
   changing temp/fan. Diff the log; note tag + format.
2. `adb shell dumpsys activity broadcasts` and
   `adb shell dumpsys activity services | grep -i -E "qinggan|hvac|air|mcu"`.
3. `adb shell getprop | grep -i -E "qinggan|mcu|vehicle|hvac"` and
   `adb shell settings list system|global|secure | grep -i -E "temp|fan|ac"`.
4. Our app gets `READ_LOGS` the same way Temper does:
   `pm grant com.dfshine.launcher android.permission.READ_LOGS`
   (via adb, or a USB `custom_rep.sh`), then parses `logcat` lines in a
   service. READ_LOGS is a development-flag permission: granting it by
   `pm grant` is the supported path, and it is not shown on Play-style stores.

Write side ("control"), in order of preference:
1. **Vendor broadcast / service** found in the stock launcher or HVAC APK:
   `adb shell pm path <pkg>`, `adb pull`, decompile with jadx, grep for
   `Intent("...")`, `sendBroadcast`, AIDL interfaces. If the receiver is
   exported without a signature permission, our widget can call it.
2. **AccessibilityService** that taps the *native* HVAC bar's own buttons
   (it can see and click other windows; no root, no vendor API needed). The
   user enables it once in Android Accessibility settings (reachable through
   the community's `Settingslaunch` hook if the OEM settings hide it). Slower
   and layout-dependent, but works without knowing any private API.
   Identify the target with `adb shell uiautomator dump` and
   `dumpsys window windows | grep -E "mCurrentFocus|Window #"`.
3. `adb shell input tap` only works from an adb/root shell, not from a normal
   app, so it is for experiments, not for the shipped launcher.
4. Direct writes to the MCU serial port or CAN: **out of scope** and unsafe
   (could affect vehicle functions beyond comfort controls).

## 6. Fastest way to unblock

Ask the authors (forum / Telegram `@aeolusshinegs`): *"How does Dongfeng
Temper read the outside temperature? Which logcat tag and line format?
Is there anything similar for the AC state or a broadcast to change it?"*
One answer likely saves days of reverse engineering. All their base
knowledge is stated to be free; paid help is only for personal time.

## 7. What this means for the launcher code

- Add a logcat-based `CarStateReader` (needs `READ_LOGS` via `pm grant`) once
  the tags are known; feed it into the climate widget so the numbers are
  *real*, not the current local-only assumptions.
- Keep the widget's per-button action strings (already in `Prefs`) as the
  broadcast write path, and add an optional AccessibilityService
  implementation as the fallback write path.
- Nothing above is implemented yet; it is blocked on adb/logcat output from
  the real car.
