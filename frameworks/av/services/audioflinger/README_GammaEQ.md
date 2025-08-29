# GammaEQ — Real-Time System-Wide Audio Enhancement

GammaEQ is a set of **fast-path safe** audio enhancements for Android's AudioFlinger, integrated into `FastMixer.cpp` and `Threads.cpp`.  
It provides **system-wide EQ, stereo widening, and high-frequency enhancement** (crystalizer-like), controllable at runtime via `setprop` without restarting `audioserver`.

---

## ✨ Features

- **Speaker PEQ** — 5-coefficient biquad filter for tailored EQ curves (clarity, warmth, bass boost, etc.).
- **CrystalizerLite** — RetroArch-inspired high-frequency transient enhancer for added clarity and detail.
- **StereoWidenerHB** — Mid/side high-band widening for a more spacious stereo image.
- **Real-time tuning** — Controlled entirely through Android system properties.
- **Hot-reload** — Changes apply instantly (if `*.seq` live-reload is enabled).

---

## 📌 End User Guide

GammaEQ exposes its settings via `persist.sys.spk.*` system properties.  
You can adjust them live via `adb shell setprop` commands.

### 1. Speaker PEQ (Parametric EQ)
```
persist.sys.spk.peq            [0|1] Enable/disable PEQ
persist.sys.spk.peq.b0         Biquad coefficient b0
persist.sys.spk.peq.b1         Biquad coefficient b1
persist.sys.spk.peq.b2         Biquad coefficient b2
persist.sys.spk.peq.a1         Biquad coefficient a1
persist.sys.spk.peq.a2         Biquad coefficient a2
persist.sys.spk.peq.seq        Integer sequence number to force reload
```

Example — Clarity boost:
```bash
adb shell setprop persist.sys.spk.peq 1
adb shell setprop persist.sys.spk.peq.b0 2.12115
adb shell setprop persist.sys.spk.peq.b1 -2.26746
adb shell setprop persist.sys.spk.peq.b2 0.65830
adb shell setprop persist.sys.spk.peq.a1 0
adb shell setprop persist.sys.spk.peq.a2 0
adb shell setprop persist.sys.spk.peq.seq $((`getprop persist.sys.spk.peq.seq`+1))
```

---

### 2. CrystalizerLite (Treble & Clarity Enhancement)
```
persist.sys.spk.cryst          [0|1] Enable/disable
persist.sys.spk.cryst.amount   0.0–5.0 Strength of enhancement
persist.sys.spk.cryst.mix      0.0–1.0 Wet/dry mix
persist.sys.spk.cryst.pregain_db  Pre-gain in dB (headroom before processing)
persist.sys.spk.cryst.postgain_db Post-gain in dB (final loudness)
persist.sys.spk.cryst.hz       High-pass corner frequency (Hz) for enhancement
persist.sys.spk.cryst.seq      Sequence number to force reload
```

Example — Air boost for speakers:
```bash
adb shell setprop persist.sys.spk.cryst 1
adb shell setprop persist.sys.spk.cryst.amount 2.4
adb shell setprop persist.sys.spk.cryst.mix 0.85
adb shell setprop persist.sys.spk.cryst.hz 11000
adb shell setprop persist.sys.spk.cryst.pregain_db -4
adb shell setprop persist.sys.spk.cryst.postgain_db 0
adb shell setprop persist.sys.spk.cryst.seq $((`getprop persist.sys.spk.cryst.seq`+1))
```

---

### 3. StereoWidenerHB (High-Band Stereo Width)
```
persist.sys.spk.wide           [0|1] Enable/disable
persist.sys.spk.wide.mix       0.0–1.0 Amount of widening
persist.sys.spk.wide.hpf       High-pass corner frequency (Hz) for side channel widening
persist.sys.spk.wide.seq       Sequence number to force reload
```

Example — Wide airy soundstage:
```bash
adb shell setprop persist.sys.spk.wide 1
adb shell setprop persist.sys.spk.wide.mix 0.85
adb shell setprop persist.sys.spk.wide.hpf 5000
adb shell setprop persist.sys.spk.wide.seq $((`getprop persist.sys.spk.wide.seq`+1))
```

---

## 🛠 Developer Guide

GammaEQ is implemented directly inside `FastMixer.cpp` to ensure **zero additional latency** and full compatibility with Android's fast audio path.  
All processing is done in floating point, with conversions for PCM16/24/32 handled via `audio_utils/primitives.h`.

### Integration Points
- **Processing chain** runs **after mixing** but **before sink write** in `FastMixer::onWork()`.
- The following static processor structs are used:
  - `SpeakerBiquad` — single biquad filter (IIR, Direct Form I)
  - `CrystalizerLite` — 1st-order HPF + non-linear transient emphasis
  - `StereoWidenerHB` — mid/side HPF widening
- Property reload is handled by `maybeReload*()` functions, which check `.seq` or elapsed time.

### Property Reloading
- All tunables are read from `persist.sys.spk.*` props.
- Reload occurs every ~1s or immediately if `.seq` is incremented.
- Developers can adjust the reload interval in the `maybeReload*()` helper functions.

### Audio Formats Supported
- PCM_FLOAT (direct processing)
- PCM_16_BIT, PCM_24_BIT_PACKED, PCM_32_BIT (converted to float for processing, then back)

### Extending GammaEQ
- Add new processor struct in `FastMixer.cpp` with:
  - `process()` method (float interleaved frames)
  - `loadProps()` method for sysprop bindings
  - `maybeReload()` to handle live updates
- Append to the chain in `onWork()` in the desired order.

---

## ⚠️ Notes
- GammaEQ applies **only** to `AUDIO_DEVICE_OUT_SPEAKER` routes in the current implementation.
- Overly high values for `amount` or `mix` in Crystalizer may cause harshness or hiss.
- Very low `wide.hpf` can smear stereo imaging in bass/midrange-heavy content.

---

## 📄 License
GammaEQ enhancements are integrated into AOSP/LineageOS code under the **Apache License 2.0**.
