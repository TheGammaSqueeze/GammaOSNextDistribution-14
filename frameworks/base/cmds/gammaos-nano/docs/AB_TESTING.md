# ES-DE engine A/B testing against real ES-DE

The nano ES-DE engine is validated by comparing it, screen for screen, against **real ES-DE**
(`org.es_de.frontend`) running the same theme on a second identical device. This document is the
procedure: how to build and deploy nano, how to capture both sides, and - the part that is easy to
get wrong - how to drive real ES-DE with synthetic controller input.

## The two devices

- **Target** - the device nano is built for and deployed to. Runs the nano home; its ES-DE engine is
  what we are validating. Captured with nano's own screenshot path.
- **Control** - an identical panel running **real ES-DE** on the same theme (Art Book Next). This is
  the ground truth. Never flashed. It also runs the nano overlay on top of ES-DE, which has to be
  moved out of the way to see and drive ES-DE.

Keep both on the same theme, variant, colour scheme, aspect ratio and font size so a difference is a
real engine difference and not a config difference.

## Building and deploying nano (target)

Build just the binary and hot-swap it over a bind mount, no image reflash:

    m gammaos-nano
    adb root
    adb push <out>/system/bin/gammaos-nano /data/local/tmp/gammaos-nano-<sha>
    adb shell 'nsenter -t 1 -m -- umount -l /system/bin/gammaos-nano;
               nsenter -t 1 -m -- mount --bind /data/local/tmp/gammaos-nano-<sha> /system/bin/gammaos-nano'
    adb shell setprop ctl.restart gammaos-nano

Then **verify the running binary actually changed** before trusting any test:

    adb shell 'sha1sum /proc/$(pgrep -x gammaos-nano)/exe'   # must equal the built sha

The system_server watchdog can respawn the original binary and bypass the bind; if the sha did not
change, re-run the swap, and if it still will not stick, fall back to a full flash. **Never `pkill`
the home process** - it races init's own stop, leaves an unreapable zombie, and deadlocks init so
that reboots hang (recover only with a kernel `sysrq` reboot, which on this Allwinner part can drop
the device into FEL and then needs a physical power cycle). Use `ctl.restart` only.

## Selecting the theme on nano

nano picks its ES-DE theme set from **`persist.gammaos.nano.esde.themeset`** (the installed directory
name under `/data/system/nano_esde_themes/`, default `slate-es-de`) - note it is `.themeset`, **not**
`.theme`. The permutation props are read alongside it: `persist.gammaos.nano.esde.variant`,
`.colorscheme`, `.fontsize`, and `.aspectratio` (default `automatic`). Changing `themeset` reloads the
theme **live** on the next frame; changing only a variant/colour/font/aspect on the same set does not
retrigger a reload from adb (the set name is unchanged), so drive those from the in-engine options menu
(`setprop sys.gammaos.nano.nav menu` then cycle the row), which applies them live. `.gotosys=<system>`
boots straight into that system's gamelist.

## Capturing

- **Target (nano):** `setprop sys.gammaos.nano.shot 1`, then pull `/data/local/tmp/nano_shot.ppm`
  (raw PPM, do **not** rotate) and `convert` it to PNG. This is the only capture that works on a **true
  DRM-direct** panel (nano opens `card0` for KMS scanout; SurfaceFlinger/`screencap` see black there).

- **Whether `screencap`/`screenrecord` work depends on the panel's compositing path.** Check the nano
  process's DRI fd: `renderD128` only (no `card0`) means nano renders into a SurfaceFlinger surface, so
  ordinary `adb exec-out screencap -p` **and** `adb shell screenrecord` capture nano's UI directly. A
  `card0` fd means DRM-direct - use the PPM path above. The 6c00 test panel is `renderD128`
  (SF-composited), so on it `screenrecord` records the live nano home at 60fps
  (`screenrecord --time-limit N --bit-rate 16000000 /data/local/tmp/x.mp4`, max 180s) - the way the
  3-minute theme demo was captured.
- **Control (ES-DE):** the nano overlay draws on top, so briefly stop it, then screencap:

      adb shell setprop ctl.stop gammaos-nano-overlay   # reveals ES-DE for ~1-3s
      adb exec-out screencap -p > ctl.png               # do this within that window

  The overlay auto-restarts (a watchdog), so capture promptly.

  **Black-screencap gotcha (after the nano DRM home has run):** the nano home renders DRM-direct
  and leaves the Android display powered **OFF** when it stops, so `screencap` of the ES-DE app
  comes back all black (`mean=0`) and the SDL app cycles `onResume`/`onPause`/`onStop` with
  `mCurrentFocus=null` because it never gets a visible surface. Fix: stop both nano services
  (`ctl.stop gammaos-nano-overlay; ctl.stop gammaos-nano`), then wake the panel and re-foreground
  ES-DE before capturing:

      adb shell 'setprop ctl.stop gammaos-nano-overlay; setprop ctl.stop gammaos-nano; sleep 1
                 input keyevent KEYCODE_WAKEUP; sleep 1
                 am start -n org.es_de.frontend/.MainActivity'
      # confirm dumpsys window | grep mCurrentFocus shows the ES-DE window (not null), then screencap

  Also suppress the overlay respawn while capturing (`setprop persist.gammaos.nano.overlay 0;
  setprop sys.gammaos.nano.overlay_ran 0`) so it does not pop back over ES-DE, and restore
  `persist.gammaos.nano.overlay 1` afterwards. ES-DE must be past its "Searching for games..."
  splash (~15-30 s) or you grab the splash.

  **Video-vs-still confound:** a theme's gamelist image is often a `<video name="game-art">` whose
  `imageType` (e.g. `cover`) is only the *static fallback*. The control PLAYS the game's scraped
  video when one exists (so the region shows moving gameplay), while nano has no per-game video and
  shows the static cover. Pick a game with **no** `.mp4` under `downloaded_media/<system>/videos/`
  (so the control also shows the static cover) before diffing that region, or you are comparing a
  video frame against a cover. Verified 4:3 art-book-next `gamelist-list-metadata-cover` this way:
  Thin Ice Rescue (no video) gives a clean cover-vs-cover match; Dangan/Hermano have videos.

## Driving real ES-DE (the non-obvious part)

ES-DE (SDL) takes input through Android's InputReader, **not** by reading `/dev/input` directly, and
the nano overlay isolates the foreground app's input with the `sys.gammaos.nano.drop_input` prop (it
does not `EVIOCGRAB`). So plain `adb shell input keyevent` does nothing - it is dropped. To drive
ES-DE, stop the overlay, clear `drop_input`, and inject events onto the **Xbox controller** device
(`gammapad-virtual`, usually `/dev/input/event4`):

    adb shell '
      setprop ctl.stop gammaos-nano-overlay
      setprop sys.gammaos.nano.drop_input 0
      sleep 1
      # D-pad right (ABS_HAT0X); then release, with a SYN after each:
      sendevent /dev/input/event4 3 16 1; sendevent /dev/input/event4 0 0 0
      sendevent /dev/input/event4 3 16 0; sendevent /dev/input/event4 0 0 0
    '

Event map for the virtual Xbox pad:

| Action  | type code value       | note                    |
|---------|-----------------------|-------------------------|
| left    | `3 16 -1`             | ABS_HAT0X               |
| right   | `3 16 1`              | ABS_HAT0X               |
| up      | `3 17 -1`             | ABS_HAT0Y               |
| down    | `3 17 1`              | ABS_HAT0Y               |
| A / confirm | `1 304 1` / `1 304 0` | BTN_SOUTH           |
| B / back    | `1 305 1` / `1 305 0` | BTN_EAST            |
| Start / menu | `1 315 1` / `1 315 0` | BTN_START          |

Send the stop, the `drop_input 0` and the events in **one** `adb shell` so they land inside the brief
overlay-down window - a stop *loop* churns InputReader and fails. Follow each button/axis with a SYN
(`0 0 0`). Restore afterwards with `setprop sys.gammaos.nano.drop_input 1` (the overlay re-asserts it
on restart anyway). This is enough to walk ES-DE through the system view, a gamelist, the main menu
and every submenu for a side-by-side comparison.

Do **not** send confirm onto a game entry in a gamelist - it launches the emulator. (If one does
launch, `am force-stop <pkg>` it; note the launch stops the ONESHOT nano home, and on the 6c00 setup
the home does **not** auto-respawn on app exit - a reboot is the reliable recovery, after which re-bind
the latest binary since bind-mounts are lost on reboot.)

**Known limitation on the current 6c00 setup - the control is only reliably A/B-able on views that
render on direct launch.** When ES-DE is launched standalone with the nano services stopped, injected
input (both `sendevent` on event4 and `input keyevent`) has been observed **not** to move the ES-DE
cursor (SDL reads through the Android InputQueue and the events do not reach it in that configuration),
and ES-DE does **not** fully render a **gamelist's** detail elements (the big image + metadata panel)
on a direct `StartupView=gamelist` boot - it needs a navigation event to "enter" the view. So the
dependable A/B surfaces are the **system view** and any **grid/carousel primary** (both populate on
startup); the standalone gamelist detail `<image>`/`<video>` and the options **menu** cannot be driven
here. Diff those startup-rendered surfaces, and verify gamelist detail elements from nano + the ES-DE
source instead. (The `sendevent` recipe above is the intended path when ES-DE runs *under* the live
nano overlay; keep it for that case.)

## Cycling the control's config

Real ES-DE reads its settings at launch, so to A/B a different variant/colour scheme/aspect/font,
edit `/storage/emulated/0/ES-DE/settings/es_settings.xml` (`ThemeVariant`, `ThemeColorScheme`,
`ThemeAspectRatio`, `ThemeFontSize`) and relaunch:

    adb shell 'am force-stop org.es_de.frontend; am start -n org.es_de.frontend/.MainActivity'

Back up the file first and restore it after. Set `ApplicationUpdaterFrequency` to `never` so the
launch-time update dialog does not block the view. ES-DE takes ~20-25s to scan and reach the system
view; capture after that.

## What to compare

Step both devices through the same sequence and compare each screen: the system-view carousel and
its slide, the gamelist (list and grid variants) with the cover, rating, metadata and the scrolling
description, and the options menu with every picker. Watch the animations, not just the static
frames. A difference in layout math, animation timing, scaling, colour or text is an engine bug -
trace it to the ES-DE source and fix it there.

## Overlay-diff, not eyeballing

Do not judge parity from a side-by-side montage. Capture nano and the control at the same resolution,
align them on the same system/game, and produce a **difference image** so divergence is unmissable:

    convert control.png nano.png -compose difference -composite -auto-level diff.png   # bright where they differ
    convert control.png nano.png -average blend.png                                    # 50/50 overlay
    compare -metric AE control.png nano.png diff.png                                   # pixel count + map

Inspect the diff, fix the single largest divergence, rebuild, re-diff, and repeat until the diff is
essentially empty. To pin a coordinate, trim an element and read its bounding box:

    convert shot.png -crop <region> +repage -fuzz 40% -trim +repage -format "%wx%h+%X+%Y\n" info:

so you can assert "pill left x = 218 on both" numerically instead of by eye. When the two apps have
different ROM sets (they usually do), drive both to the same system before diffing, or diff only the
chrome (header, bars, help, grid cells) and note that the media content legitimately differs. Keep
the diff artifacts under `/work/esde_verify/` and post them to the progress channel as you go.
