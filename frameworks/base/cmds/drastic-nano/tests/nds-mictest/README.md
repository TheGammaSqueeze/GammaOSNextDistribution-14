# DS microphone test ROM

A bare-metal DS program (no devkitPro, only `arm-none-eabi-gcc`) that records up to 30 seconds
from the microphone and plays it back, and checks the recording for the faults that make a
microphone sound distorted or crackly. It runs on real hardware (DS, DS Lite, DSi and 3DS in DS
mode) as well as in emulators, so the two can be compared directly.

Build: `./build.sh out.nds [any-ds-rom.nds]` (the optional ROM only lends its header logo).

## Controls

| Button | Action |
| --- | --- |
| A | record (A or B stops early) |
| B | stop |
| START | play the recording |
| SELECT | play a 1 kHz test tone through the same output |
| LEFT / RIGHT | sample rate: 47605, 32728, 21819, 16364, 8182 Hz |
| UP / DOWN | microphone amplifier gain: 20x, 40x, 80x, 160x |

The level bar turns red and shows CLIP when the input reaches full scale.

## How it captures

The ARM7 reads the microphone through the touchscreen controller's AUX input in 12-bit mode
(command 0xE4, the sequence libnds' `micReadData12` uses), paced by timer 0 polled through IF so
the period is exact. Playback uses sound channel 0 as 16-bit PCM with its timer at exactly half
the capture period, so it plays at the rate it was recorded. Before playback the recording is
converted in place to signed 16-bit with its DC offset removed.

## What it reports after a recording

- Rec: length and sample count. DC: the mean raw value. range: lowest and highest raw value.
- RMS and levels: loudness, and how many distinct 12-bit values occurred.
- Clipped: samples at 0 or 4095.
- Held runs / longest: runs of 16 or more identical samples, which is what stale or repeated
  input data looks like.
- Jumps: steps of more than a quarter of full scale between neighbouring samples (clicks, pops).
- late: sample deadlines the ARM7 met late (should be 0).
- Low 4 bits: whether the data really is 12-bit or 8-bit data padded out.

## Reading the recording out of an emulator

The shared block is at `0x02300000` (`MicCtrl` in `mictest.h`) and the recording at
`0x02040000`, so a harness can copy both out of an emulator's memory. In drastic-nano DS main RAM
is `*(ctx + 0x35d9930)` with `ctx = *(libdrastic + 0x14c000)`. After the analysis the buffer holds
signed 16-bit samples, `samples` of them, at `MIC_CLOCK / recPeriod` Hz.
