# GammaOS Per-Display (Dual-Screen) Volume Control

OEM integration guide for the framework changes that give each physical display its own
media volume on dual-screen devices such as the RG DS.

Feature title in the git history: `[DUALSTACK] Add per screen volume control`.
Everything below is on the `develop` branch and is verified against the current tree.

---

## 1. What it does

On a normal Android device there is one media volume (`STREAM_MUSIC`) shared by the whole
system. On a dual-screen device that is wrong: a game on the top screen and a video on the
bottom screen should be adjustable independently.

This feature makes each **physical display** hold its own `STREAM_MUSIC` UI level (0..15, the
same index range as the standard media stream). On the RG DS:

- display `0` is the primary / bottom screen
- display `2` is the secondary / top screen

Behaviour, end to end:

- **A volume dialog per screen.** SystemUI shows an independent volume panel on each eligible
  physical display, each rendered with that display's own `Context` so it lands on the right
  panel.
- **Volume keys** run the normal path (`PhoneWindowManager.handleVolumeKey` ->
  `AudioService.setStreamVolume(STREAM_MUSIC, ...)`) and drive the per-display map.
- **The map is persisted** to `Settings.Global["gammaos_audio_display_volume_map"]` as
  `displayId=level;displayId=level` (for example `0=15;2=8`) and survives reboots.
- **Audio is not re-routed per screen.** There is still one shared output device (speaker or
  headset). Per-display volume is realised as a **per-UID amplitude attenuation** layered on top
  of the global `STREAM_MUSIC` level. The system-wide `STREAM_MUSIC` index is the **maximum** of
  the per-display values; any display below that maximum has its apps attenuated by the exact dB
  difference.
- **Attribution is dynamic.** Which display's level applies to a playing app is decided by asking
  WindowManager which display that app (UID) is currently visible on.

If the feature is off (the default), the system behaves exactly like stock Android: one global
media volume, one volume dialog on the default display.

---

## 2. Turning it on and off (start here)

The feature is controlled by **one system property** and is **off by default**. It is enabled
when, and only when, both of these hold:

1. `persist.gammaos.audio.multivolume` is `true` (or `1`), AND
2. `sys.gammaos.dualstack.active` is `false` / unset.

```
# enable on a dual-screen device (persist across reboots via product build.prop or setprop)
setprop persist.gammaos.audio.multivolume 1

# disable (return to stock single-volume behaviour)
setprop persist.gammaos.audio.multivolume 0
```

Both the framework model (`AudioService`) and the UI (`SystemUI`) listen for
`SystemProperties` changes, so toggling the property takes effect **live, without a reboot**.

Notes for integrators:

- Set `persist.gammaos.audio.multivolume=1` in the **product `build.prop`** of a dual-screen SKU.
  Leave it unset (default false) on single-screen devices.
- `sys.gammaos.dualstack.active` is a runtime override. It is set when the device boots into a
  dual-stack presentation mode where a single mirrored/extended volume is wanted, and it forces the
  feature off even if `multivolume` is true. Do not leave it set during normal per-display use.
- **There is no "auto-enable when a second display appears".** The property is the switch. Display
  hotplug only decides which displays get a map entry and a dialog *while the property is on*.
- The predicate is enforced in two places that must stay in agreement:
  - `AudioService.GammaMultiDisplayVolumeController.updateEnabledState()` (`propEnabled && !dualStackActive`)
  - `SystemUI GammaMultiVolumeStore.isMultiVolumeEnabled()` (same predicate)

---

## 3. Requirements

- **Two or more physical displays.** The RG DS has two DSI panels enumerated as display `0` and
  display `2`. Other dual-screen SKUs may enumerate different logical display ids; the map is keyed
  by Android `displayId`, so confirm the ids on your hardware (`dumpsys display`, or read the map
  after moving the SystemUI slider on each screen).
- **`WindowManagerInternal.getTopVisibleDisplayIdForUid(int uid)`** must be present (it is, in this
  tree). It is what maps a playing app to a display. If it is missing or returns
  `Display.INVALID_DISPLAY`, players fall back to the last-known display and then to display `0`.
- No special kernel, audio HAL, or DTB support is needed. This is entirely a framework feature; the
  audio HAL sees ordinary `STREAM_MUSIC` volume plus per-player client gains.

---

## 4. Architecture

Four cooperating layers.

```
  Volume key -> PhoneWindowManager.handleVolumeKey -> AudioService.setStreamVolume(STREAM_MUSIC)
                                                          |  (also publishes persist.gammaos.nano.volume / .volmax)
  SystemUI slider -> VolumeDialogController ------------>-+
  App: settings put global ...volume_map -(ContentObserver)-+
                                                          v    |
                        GammaMultiDisplayVolumeController  (AudioService.java)
                          mDisplayToVolumeIndex : SparseIntArray   displayId -> UI level (0..15)
                          onSet/onAdjustStreamVolume -> pin every display to the new global level
                          persistDisplayMapLocked -> Settings.Global["gammaos_audio_display_volume_map"]
                          handleSettingsChanged <-----------------------------------+  (echo-suppressed)
                          reconcileGlobalStreamVolume: global STREAM_MUSIC = max(map); clamp to safe ceiling
                          applyPlayerAttenuations(active players):
                             for each active media UID:
                               displayId = WindowManagerInternal.getTopVisibleDisplayIdForUid(uid)
                               desired   = map[displayId]
                               gain      = 10 ^ ((dB(desired) - dB(globalMax)) / 20)
                               PlaybackActivityMonitor.setGammaDisplayVolumeForUid(uid, gain)
                                                          |
                                                          v
                        PlaybackActivityMonitor: mGammaUidToClientVolume[uid] = gain
                          apc.getPlayerProxy().setVolume(gain)  (media/game/assistant/unknown only)
                                                          |
                                                          v
                                            audio hardware renders attenuated
```

SystemUI dialog spawning runs in parallel:

```
  VolumeModule.provideVolumeDialog -> new GammaMultiDisplayVolumeDialog(context, dialogFactory)
  GammaMultiDisplayVolumeDialog.refreshDialogs()  (on init / DisplayListener add-remove / SystemProperties change):
     if GammaMultiVolumeStore.isMultiVolumeEnabled():
        for each display passing shouldShowOnDisplay(d):  factory.create(createDisplayContext(d)).init(...)
     else:
        one dialog on Display.DEFAULT_DISPLAY
```

---

## 5. The framework changes (what to carry when you merge or port)

All paths are relative to the repo root. Carry every file in this section together; they are one
feature.

### 5.1 AudioService: the per-display model, persistence, attenuation

`frameworks/base/services/core/java/com/android/server/audio/AudioService.java`

- Constants:
  - `GAMMA_PROP_MULTI_VOLUME_ENABLED = "persist.gammaos.audio.multivolume"`
  - `GAMMA_PROP_DUALSTACK_ACTIVE = "sys.gammaos.dualstack.active"`
  - `GAMMA_SETTING_DISPLAY_VOLUME_MAP = "gammaos_audio_display_volume_map"`
- Inner class **`GammaMultiDisplayVolumeController`**, all state guarded by its own `mLock`:
  - `mDisplayToVolumeIndex : SparseIntArray` - the per-display level map (`displayId -> 0..15`).
  - `mUidToLastDisplayId : SparseIntArray` - fallback cache for attribution.
  - `mLastWrittenMap`, `mInternalStreamVolumeUpdate` - echo-suppression and re-entrancy guards.
  - `updateEnabledState()` / `enable()` / `disable()` - the property gate and lifecycle.
  - `onAdjustStreamVolume()` / `onSetStreamVolume()` - hooks that pin every display entry to the new
    global level on any `STREAM_MUSIC` change (see the gotcha in 8.2).
  - `onPlaybackConfigChange()` - re-applies attenuation when players start or stop.
  - `onDisplayAdded()` / `onDisplayRemoved()` - keep the map in step with hotplug.
  - `handleSettingsChanged()` - reacts to external writes of the setting (this is how apps drive it).
  - `reconcileGlobalStreamVolume()` - drives the global `STREAM_MUSIC` to `max(map)` and clamps each
    entry down to the safe-volume ceiling.
  - `persistDisplayMapLocked()` - the single persist choke point; writes the setting under the
    system identity (see 8.3).
  - `resolveDisplayIdForUid()` - calls `WindowManagerInternal.getTopVisibleDisplayIdForUid()`, with
    the cache and display-0 fallbacks.
  - `applyPlayerAttenuations()` / `computeMusicAttenuationGain()` - collect the active media UIDs
    (`isMediaUsage`: `USAGE_MEDIA`, `USAGE_GAME`, `USAGE_ASSISTANT`) and set each one's per-UID gain
    from the dB delta between its display's level and the global.
  - `serializeDisplayVolumeMap()` / `parseDisplayVolumeMap()` - the `id=vol;id=vol` codec.

### 5.2 PlaybackActivityMonitor: per-UID client gain

`frameworks/base/services/core/java/com/android/server/audio/PlaybackActivityMonitor.java`

- `mGammaUidToClientVolume : SparseArray<Float>` - per-UID gain in `[0.0, 1.0]`.
- `setGammaDisplayVolumeForUid(uid, gain)` - store and apply to every active player of the UID.
- `clearGammaDisplayVolumes()` - reset all tracked UIDs to gain 1.0 (used by `disable()`).
- `applyGammaClientVolumeToPlayerLocked(apc, gain)` - `apc.getPlayerProxy().setVolume(gain)`, applied
  per player of the UID, guarded by `shouldApplyGammaVolume` (usage `USAGE_MEDIA`, `USAGE_GAME`,
  `USAGE_ASSISTANT`, or `USAGE_UNKNOWN`) and skipped for a call-muted player. Note AudioService only
  *selects* which UIDs to attenuate using `isMediaUsage` (media / game / assistant, section 5.1), so
  `USAGE_UNKNOWN` only rides along on a UID that is already attenuated for a media player.
- `trackPlayer()` immediately applies any stored gain to a newly started player, so audio that
  begins after the level was set is attenuated correctly.

### 5.3 WindowManager: which display owns a UID

- `frameworks/base/services/core/java/com/android/server/wm/WindowManagerInternal.java` - new
  abstract `public abstract int getTopVisibleDisplayIdForUid(int uid);`.
- `frameworks/base/services/core/java/com/android/server/wm/WindowManagerService.java` - the
  implementation. It walks, top to bottom under the global lock: (1) the topmost visible window
  owned by the UID, (2) a resumed activity of the UID, (3) any requested-visible activity of the
  UID, returning that display, else `Display.INVALID_DISPLAY`.

### 5.4 SystemUI: one volume dialog per display

`frameworks/base/packages/SystemUI/src/com/android/systemui/volume/`

- **`GammaMultiDisplayVolumeDialog.java`** (new) - implements the `VolumeDialog` interface and hosts
  a `VolumeDialogImpl` per display. Registers a `DisplayManager.DisplayListener` and a
  `SystemProperties` change callback, both of which call `refreshDialogs()`. `shouldShowOnDisplay()`
  accepts the default display always, plus any display with `FLAG_PRESENTATION` or `FLAG_TRUSTED`.
  Each child dialog is built from `mContext.createDisplayContext(display)` so it renders on the
  correct panel. A nested `interface DialogFactory { VolumeDialogImpl create(Context displayContext); }`
  lets the Dagger module supply a fully-wired factory.
- **`GammaMultiVolumeStore.java`** (new) - the shared gate and setting helper used by SystemUI:
  `isMultiVolumeEnabled()`, `getVolumeForDisplay()`, `setVolumeForDisplay()`, and the constants
  `PROP_ENABLED`, `PROP_DUALSTACK_ACTIVE`, `SETTING_KEY` (same three strings as AudioService).
- **`VolumeDialogImpl.java`** - two changes: a constructor that is friendly to the per-display
  factory, and per-display **Dumpable namespacing**: `mDumpableName = "VolumeDialogImpl-" +
  mContext.getDisplayId()` registered in the constructor and **unregistered in `destroy()`**. Without
  this, the second display's instance throws `IllegalArgumentException` from `DumpManager` and
  crash-loops SystemUI (see 8.4).
- **`dagger/VolumeModule.java`** - `provideVolumeDialog(...)` now returns
  `new GammaMultiDisplayVolumeDialog(context, factory)` instead of a bare `VolumeDialogImpl`. The
  factory lambda captures every dependency and takes the per-display `Context` as its only argument.

### 5.5 PhoneWindowManager: publish the master level for HUDs

`frameworks/base/services/core/java/com/android/server/policy/PhoneWindowManager.java` -
`handleVolumeKey` publishes `persist.gammaos.nano.volume` (current index) and
`persist.gammaos.nano.volmax` (max index) so a fullscreen app HUD can show the master level without
polling AudioService. These are read-only for apps.

---

## 6. Data model and persistence

- **Key:** `Settings.Global["gammaos_audio_display_volume_map"]`. Not `Settings.Secure`, not a file.
- **Format:** ASCII, semicolon-separated `displayId=level` pairs, for example `0=15;2=8`.
- **Level:** the `STREAM_MUSIC` **UI index** `0..volMax` (typically `0..15`), the same range as the
  standard media stream. It is **not** a 0..100 percentage. The parser skips empty, malformed, or
  out-of-range entries; there is no checksum or compression.
- **Global relationship:** after `reconcileGlobalStreamVolume`, the system-wide `STREAM_MUSIC` index
  equals `max(per-display levels)`. Raising one display raises the global; any display below the
  global is attenuated by the dB difference.
- **Writer identity:** every system-server write goes through `persistDisplayMapLocked` under
  `Binder.clearCallingIdentity()`, because it can run inside a SystemUI-initiated binder transaction.
- **Echo suppression:** `mLastWrittenMap` lets `handleSettingsChanged` ignore the ContentObserver
  callback caused by the server's own write.

### Boot behaviour (important)

On `enable()` the map is seeded **from** the just-restored global `STREAM_MUSIC` (which Android
restores from `volume_music_speaker`), and `enable()` deliberately does **not** call
`reconcileGlobalStreamVolume`. This direction matters: if the map were loaded from its own stale
persisted value and then reconciled, a `max-of-displays` value from a previous session would
overwrite the user's restored media volume on every boot. Reconciliation only runs on later events
(an external settings write, a display add or remove).

---

## 7. The contract for a device app

A launcher, overlay, or settings app on a GammaOS dual-screen device uses this contract. This is
exactly what the nano Control Center and drastic-nano overlay do.

**Read the master (system-wide) level - read only:**

```
getprop persist.gammaos.nano.volume    # current STREAM_MUSIC index
getprop persist.gammaos.nano.volmax    # max index (e.g. 15)
```

These are published by `PhoneWindowManager`. Do not write them.

**Read the per-display levels:**

```
settings get global gammaos_audio_display_volume_map     # e.g. "0=15;2=8"
```

Parse `displayId=level`. On the RG DS, `0` is the bottom screen and `2` is the top screen; fall back
to the master level for a display with no entry. Programmatic equivalent:
`GammaMultiVolumeStore.getVolumeForDisplay(context, displayId, fallback)`.

**Set the per-display levels:**

```
settings put global gammaos_audio_display_volume_map '0=15;2=5'
```

AudioService's ContentObserver picks it up, reconciles `STREAM_MUSIC = max(...)`, and applies the
per-player attenuation live. Levels must be in `0..volMax`; out-of-range entries are rejected by both
the writer and the AudioService parser. Debounce writes while a slider is being dragged and flush on
release, because each `settings put` is a binder round-trip.

**Reading the volume keys yourself (the nano model):** nano reads the volume keys from the evdev
nodes **shared** (it does not `EVIOCGRAB` them), so the same physical key still reaches
`PhoneWindowManager`, which performs the real all-stream volume change, publishes
`persist.gammaos.nano.volume` / `.volmax`, and persists it. An app that reads the keys this way should
only move its own on-screen slider optimistically for instant feedback. Do **not** inject a synthetic
volume key on top of a shared read: `PhoneWindowManager` would apply the change a second time (the
"two steps per press" bug, which is exactly why nano's earlier key injection was removed). A raw
programmatic `setStreamVolume` also syncs the map (the flag gates were removed, see 8.2) but does
**not** refresh the `persist.gammaos.nano.*` publish properties, since those are set by
`PhoneWindowManager`.

If your app instead takes an exclusive `EVIOCGRAB` on the device that emits the volume keys, they will
not reach `PhoneWindowManager` at all and you must drive volume yourself; note that nano deliberately
does not do this.

Consumer references in this tree:

- `frameworks/base/cmds/gammaos-nano/NanoControlCenter.cpp` - Master / Top / Bottom sliders, reads
  the map with `settings get`, writes it with a debounced `settings put '0=%d;2=%d'`.
- `frameworks/base/cmds/gammaos-nano/NanoMenuSystem.cpp` (`adjustVolume`) - reads the volume keys
  shared and only moves its slider optimistically; `PhoneWindowManager` does the real change. It does
  not grab the keys and does not inject synthetic ones (see above).
- `frameworks/base/cmds/drastic-nano/OverlayMenu.cpp` - read-only HUD from `persist.gammaos.nano.volume`
  and `.volmax`.

---

## 8. Gotchas and the bug fixes (do not regress these)

Each of these is a real bug that was fixed. If you re-port the feature onto a different base, keep
the fix.

### 8.1 Seed the map from the global on boot, never reconcile at boot

`enable()` seeds `mDisplayToVolumeIndex` from the live `STREAM_MUSIC` and skips reconciliation. Doing
the reverse pushes a stale persisted `max-of-displays` back onto the stream and resets the user's
media volume on every boot. (Fixed in `89700b24803`.)

### 8.2 Do not gate the volume hooks on FLAG_FROM_KEY / FLAG_SHOW_UI

`onAdjustStreamVolume` and `onSetStreamVolume` sync the map on **every** `STREAM_MUSIC` change. The
original code gated on `FLAG_FROM_KEY` / `FLAG_SHOW_UI`, which excluded legitimate programmatic paths
(media-session adjustments, `cmd audio`, injected key events), so the map drifted out of sync with
`volume_music_speaker` and stale entries survived reboots. (Fixed in `89700b24803`.)

### 8.3 Persist under the system identity

`persistDisplayMapLocked` writes the setting inside `Binder.clearCallingIdentity()`. It is reached
synchronously inside a SystemUI `setStreamVolume` binder transaction; without clearing the identity
the write is attributed to SystemUI's uid with op-package "android", which `SettingsProvider` rejects
("Package android does not belong to <uid>") and crashes SystemUI. The map is a system-owned global
setting, so the system identity is correct. (Fixed in `93eb0949e1e`.)

### 8.4 Namespace the SystemUI Dumpable by display id

Each `VolumeDialogImpl` registers a `DumpManager` dumpable. With a fixed name, the second display's
instance throws `IllegalArgumentException` (duplicate name), which crash-loops SystemUI and blanks
both screens with app launching broken. The name is `"VolumeDialogImpl-" + displayId` and is
unregistered in `destroy()`. (Fixed in `ae2b0f5a46e`; the support had been removed by a TV volume-UI
change, `40246350417`, and this restored it.)

### 8.5 Other invariants

- **Attenuation is a per-UID client gain, not audio routing.** `PlayerProxy.setVolume(gain)` applies
  per UID; all players of one UID on one display share the gain. There is still one output device.
- **Attenuation is scoped to media-like usages.** AudioService selects which UIDs to attenuate using
  `isMediaUsage` (`USAGE_MEDIA`, `USAGE_GAME`, `USAGE_ASSISTANT`); the per-player guard in
  PlaybackActivityMonitor (`shouldApplyGammaVolume`) additionally allows `USAGE_UNKNOWN`, so an
  already-attenuated UID's unknown-usage players follow the same gain. It never fights call-mute, and
  alarms, ringtones, and notifications are not per-display attenuated.
- **Safe volume (HSR) is honoured.** `reconcileGlobalStreamVolume` clamps every per-display entry
  down to the actual global after safe-volume lowers it, so no display can exceed the safe ceiling.
- **Attribution can lag a display transition.** If an app is momentarily off-screen, the resolver
  falls back to the `mUidToLastDisplayId` cache and then display `0`. This is correct for
  foreground-focused control but can be briefly stale while an app moves between screens.
- **Disable cleans up.** Turning the property off calls `clearGammaDisplayVolumes()` (restore gain
  1.0) and restores the global to the default-display level, so no stale attenuation survives a
  toggle.

---

## 9. Testing and verification

```
# 1. enable
setprop persist.gammaos.audio.multivolume 1

# 2. confirm the model is live and see the current map
settings get global gammaos_audio_display_volume_map

# 3. bottom (display 0) loud, top (display 2) quiet
settings put global gammaos_audio_display_volume_map '0=15;2=5'
#    play media on each screen; the top screen's app should be quieter.

# 4. the global STREAM_MUSIC index should now read 15 (the max of the two)
cmd media_session ...    # or check the volume dialog / getprop persist.gammaos.nano.volume

# 5. reboot, then re-read; the map must be unchanged (persistence)
settings get global gammaos_audio_display_volume_map

# 6. disable and confirm stock single-volume behaviour returns
setprop persist.gammaos.audio.multivolume 0
```

Troubleshooting:

- **SystemUI crash-loops or both screens go blank when the feature is on** - the Dumpable
  namespacing fix (8.4) or the `clearCallingIdentity` persist fix (8.3) is missing.
- **Media volume resets to max (or to a stale value) on every boot** - the boot seed direction (8.1)
  or the flag-gate removal (8.2) is missing.
- **Volume keys do nothing inside a fullscreen app** - that app is taking an exclusive grab of the
  volume keys, so `PhoneWindowManager` never sees them. Read the keys shared instead (the nano model)
  or drive volume yourself (see section 7).
- **All audio follows one screen regardless of where the app is** -
  `WindowManagerInternal.getTopVisibleDisplayIdForUid` is returning `INVALID_DISPLAY`; check that the
  WM changes in 5.3 are present.

---

## 10. Commit history

The feature and its fixes on `develop`:

| Commit | Role | Summary |
|--------|------|---------|
| `c52c1fbe4db` | introduce | Adds the whole feature: `GammaMultiDisplayVolumeController` in AudioService, the per-UID gain in `PlaybackActivityMonitor`, `getTopVisibleDisplayIdForUid` in WindowManager, and `GammaMultiDisplayVolumeDialog` + `GammaMultiVolumeStore` + the `VolumeModule` provider swap in SystemUI. |
| `89700b24803` | fix | STREAM_MUSIC persistence across reboots: seed the map from the global on boot, and drop the FLAG gates on the volume hooks so every media-volume change syncs the map. (It also added a NanoMenu synthetic key injection that was later removed, see below.) |
| `ae2b0f5a46e` | fix | Namespace the per-display SystemUI Dumpable by display id and unregister it in `destroy()`, ending the dual-screen crash-loop that blanked normal Android. |
| `93eb0949e1e` | fix | Write the per-display map under `Binder.clearCallingIdentity()` so the slider on a secondary display no longer crashes SystemUI with a SecurityException. |
| `fa25d39d0d2` | consumer | nano Control Center replaces its clock with Master / Top / Bottom volume sliders driven by the map. |

Related, not part of the feature core:

- `40246350417` (TvSystemUI ATV volume slider layout) temporarily removed the multi-display Dumpable
  support from `VolumeDialogImpl`; `ae2b0f5a46e` restored it. The current tree has the full support.
- `9829bb21d88` (nano: fix volume slider not tracking actual volume) removed the NanoMenu synthetic
  volume-key injection that `89700b24803` had added: nano reads the volume keys shared (it does not
  grab them), so `PhoneWindowManager` already applies the change, and the extra injected key
  double-applied it (two steps per press). `adjustVolume` in `NanoMenuSystem.cpp` is now display-only.
- `25b67807f3c` (drastic-nano volume slider follows the system volume) is a consumer-side change that
  lives on a backup branch and is not part of `develop`; the drastic-nano overlay on `develop`
  already reads the master level from `persist.gammaos.nano.volume` / `.volmax`.
