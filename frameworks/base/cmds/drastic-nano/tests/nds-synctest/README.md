# DS screen sync and input latency test ROM

A bare-metal DS program (no devkitPro, only `arm-none-eabi-gcc`) for checking that a dual-screen
device shows both DS screens in step and for measuring how many frames a button press or a touch
takes to show up on screen. Built the same way as `../nds-mictest`.

Build: `./build.sh [out.nds] [any-ds-rom.nds]` (the optional ROM only lends its header logo).
Install: copy `gammaos-synctest.nds` to `/sdcard/ROMs/nds/`. After a rebuild delete the
drastic-nano autosave `/data/media/0/drastic-nano/savestates/gammaos-synctest_9.dss`, otherwise
the next launch resumes the previous build's state.

## What it shows

Both screens draw exactly the same picture in the same frame and are blitted to their displays
by DMA inside the same vblank, so on a correctly synced pair of panels the two big numbers always
match. Film both panels with a high speed camera and compare the digits to see any offset.

- The six digit number is the vblank counter (60 per second on a DS).
- `KEY` is the frame in which the latest button press (any button, X and Y included) was first
  seen, `+N` how many frames have passed since. The background turns red for 5 seconds after the
  last button activity.
- `TOUCH` is the frame of the latest pen-down, `+N` frames since. The background turns green
  for 5 seconds after the pen was last down. With both active it is amber.
- `presses` / `touches` count the events, `last` and `held` name the button, `raw` shows the
  touchscreen controller reading and the bottom screen draws a marker at a crude mapping of it.
- `missed vblanks` counts frames the drawing loop did not finish in time (measured with a free
  running timer), so a slow frame cannot hide behind a counter that simply stopped.
- `arm7 ok` means the ARM7 is running and publishing the touch and X/Y state.

Filming the panel together with the hand or stylus gives the input to display latency directly:
the frame number shown when the finger lands, against the frame number `KEY`/`TOUCH` latches.

## How it works

Both 2D engines run mode 5 with BG2 as a 256x256 direct colour bitmap (engine A in VRAM bank A
for the top screen, engine B in bank C for the bottom). Pixels carry bit 15, the alpha bit of a
direct colour bitmap; without it the whole layer is transparent. The ARM9 draws into two main
RAM back buffers and copies both with DMA3 right after the vblank starts. The ARM7 polls the
pen-down line (KEYXY bit 6) and the X/Y buttons as fast as it can, reads the touch position from
the TSC2046 while the pen is down, and bumps a sequence number on every pen-down edge so a tap
shorter than a frame still counts. The shared block is `SyncCtrl` at `0x02300000`.

The text uses the 8x8 VGA console font (Lat15-VGA8 from console-setup, public domain),
generated into `font8x8.h` by `genfont.py`.
