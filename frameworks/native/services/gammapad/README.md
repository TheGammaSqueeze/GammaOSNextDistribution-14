# GammaPad — Native Gamepad Multiplexer Daemon

GammaPad is a native Android daemon that grabs physical gamepad/joystick input devices, applies a configurable transformation pipeline, and exposes a single unified virtual gamepad via uinput. It runs as `system:input` and is controlled entirely through persistent system properties.

## Architecture

```
Physical HID Controllers
  ├── /dev/input/event0  (Xbox 360 pad)
  ├── /dev/input/event1  (built-in gamepad)
  └── ...
        │
        │  EVIOCGRAB (exclusive)
        ▼
  ┌─────────────────────────────────────────────┐
  │  GamepadManager                             │
  │  ┌────────────────────┐                     │
  │  │  Device Discovery  │ ← inotify hotplug  │
  │  │  Capability Merge  │                     │
  │  └────────┬───────────┘                     │
  │           ▼                                 │
  │  ┌────────────────────┐                     │
  │  │  KeyLayoutParser   │ ← .kl files        │
  │  │  Axis Roles        │                     │
  │  │  Heuristic Remap   │                     │
  │  └────────┬───────────┘                     │
  │           ▼                                 │
  │  ┌────────────────────┐                     │
  │  │  InputTransformer  │                     │
  │  │  • .kl mapping     │                     │
  │  │  • Normalization   │                     │
  │  │  • Axis-to-button  │                     │
  │  │  • Calibration     │                     │
  │  │  • DPAD/analog     │                     │
  │  │  • Button remap    │                     │
  │  │  • Blacklists      │                     │
  │  └────────┬───────────┘                     │
  │           ▼                                 │
  │  ┌────────────────────┐                     │
  │  │  VirtualGamepad    │ ← uinput           │
  │  └────────┬───────────┘                     │
  │           │                                 │
  │  ┌────────────────────┐                     │
  │  │  ForceFeedback     │ ← FF_RUMBLE        │
  │  │  • Native forward  │                     │
  │  │  • PWM bridge      │                     │
  │  └────────────────────┘                     │
  └─────────────────────────────────────────────┘
        │
        ▼
  /dev/input/eventN  (virtual: "Xbox Wireless Controller")
        │
        ▼
  Android InputFlinger → Apps
```

### Source Files

| File | Description |
|------|-------------|
| `main.cpp` | Daemon entry point, signal handling |
| `gammapad.rc` | init.rc service definition |
| `GamepadManager.h/cpp` | Core: device grab/release, epoll loop, hotplug, config reload, virtual device lifecycle |
| `InputTransformer.h/cpp` | Event transformation pipeline: .kl mapping, normalization, calibration, remapping, axis-to-button, DPAD conversion, blacklists |
| `KeyLayoutParser.h/cpp` | Parses Android `.kl` files for axis/key mapping, heuristic remapping, collision resolution |
| `VirtualGamepad.h/cpp` | uinput virtual device creation with custom identity, axes, and buttons |
| `ForceFeedback.h/cpp` | Force feedback: native forwarding to physical devices + PWM vibration bridge fallback |
| `Android.bp` | Build rule (`cc_binary`, links `libbase`, `liblog`) |

### Settings UI (LineageParts)

| File | Description |
|------|-------------|
| `GamepadSettings.java` | Main preference screen: toggles, presets, remapping, calibration, blacklists |
| `GamepadRemapDialogFragment.java` | Interactive button/axis remap dialog with live input detection |
| `GamepadCalibrationDialogFragment.java` | Multi-step calibration wizard for sticks and triggers |
| `GamepadTestFragment.java` | Live input test view showing buttons, sticks, triggers, and HAT axis DPAD |
| `gamepad_settings.xml` | Preference layout |
| `gamepad_strings.xml` | All UI strings |

## Service Lifecycle

Defined in `gammapad.rc`:

```
service gammapad /system/bin/gammapad
    disabled
    user system
    group system input
    seclabel u:r:gammapad:s0
    class main

on property:persist.gammaos.gamepad.enable=1 && property:sys.boot_completed=1
    start gammapad

on property:persist.gammaos.gamepad.enable=0
    stop gammapad
```

The daemon starts automatically when `persist.gammaos.gamepad.enable` is set to `1` after boot completes, and stops when set to `0`.

## System Properties Reference

All properties use the `persist.gammaos.gamepad.` prefix and survive reboots.

### Core

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enable` | int | `0` | Master toggle. `1` starts the daemon, `0` stops it. |
| `merge` | int | `1` | Merge all physical controllers into one virtual device. |
| `config_version` | int | `0` | Monotonically increasing counter. Bump to trigger live config reload without restart. |
| `devices` | string | _(empty)_ | Semicolon-separated device name substrings to grab. Empty = grab all gamepads. Supports continuation props `devices_2`, `devices_3`, etc. for long values. |

### Virtual Device Identity

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `device_name` | string | `Xbox Wireless Controller` | Name reported by the virtual uinput device. |
| `device_vid` | hex int | `0x045e` | USB Vendor ID for the virtual device. |
| `device_pid` | hex int | `0x0b13` | USB Product ID for the virtual device. |

These control how the virtual gamepad appears to Android and apps. Preset values for common controllers (Xbox 360, Xbox One, PS4, PS5, Switch Pro) are available in the Settings UI.

The PID determines the virtual device's axis layout:
- **PID 0x0b13** (default, Xbox Wireless Controller BT): native layout — right stick on Z/RZ, triggers on GAS/BRAKE. Heuristic remapping is skipped.
- **PID 0x02fd** (Xbox Wireless Controller): standard layout — right stick on RX/RY, triggers on Z/RZ. Heuristic remapping is applied to convert non-standard physical controllers to this layout.

### Button Remapping

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `remap_btn` | string | _(empty)_ | Comma-separated `from:to` pairs of Linux key codes (decimal). Example: `304:305,305:304` swaps A↔B. |

### Axis Remapping

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `remap_axis` | string | _(empty)_ | Comma-separated `from:to` pairs of Linux ABS codes (decimal). Applied at the mAbsMap level after .kl and role resolution. |

### Axis Role Assignment

Override automatic axis-to-role mapping. Each property stores the Linux ABS code of the physical axis that should fill the given role.

| Property | Role | Target ABS Code |
|----------|------|-----------------|
| `role_lx` | Left Stick X | `ABS_X` (0) |
| `role_ly` | Left Stick Y | `ABS_Y` (1) |
| `role_rx` | Right Stick X | `ABS_Z` (2) with PID 0x0b13, `ABS_RX` (3) with PID 0x02fd |
| `role_ry` | Right Stick Y | `ABS_RZ` (5) with PID 0x0b13, `ABS_RY` (4) with PID 0x02fd |
| `role_lt` | Left Trigger | `ABS_BRAKE` (10) with PID 0x0b13, `ABS_Z` (2) with PID 0x02fd |
| `role_rt` | Right Trigger | `ABS_GAS` (9) with PID 0x0b13, `ABS_RZ` (5) with PID 0x02fd |

### Calibration

Per-axis calibration data stored as comma-separated values: `center,min,max,deadzone,sensitivity,invert`.

| Property | Description |
|----------|-------------|
| `cal_axis0` | Left Stick X calibration |
| `cal_axis1` | Left Stick Y calibration |
| `cal_axis2` | ABS_Z calibration (right stick X with PID 0x0b13, left trigger with PID 0x02fd) |
| `cal_axis3` | Right Stick X calibration (ABS_RX, used with PID 0x02fd) |
| `cal_axis4` | Right Stick Y calibration (ABS_RY, used with PID 0x02fd) |
| `cal_axis5` | ABS_RZ calibration (right stick Y with PID 0x0b13, right trigger with PID 0x02fd) |
| `cal_axis9` | ABS_GAS calibration (right trigger with PID 0x0b13) |
| `cal_axis10` | ABS_BRAKE calibration (left trigger with PID 0x0b13) |

Fields:
- **center**: Center offset to subtract from raw values
- **min/max**: Physical range extremes (used for normalization)
- **deadzone**: Dead zone radius (circular for paired stick axes, linear for triggers)
- **sensitivity**: Multiplier (float, default 1.0)
- **invert**: `1` to invert axis, `0` normal

### DPAD / Analog Conversion

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `analog_to_dpad` | int | `0` | Convert left stick to DPAD (HAT0X/HAT0Y). |
| `dpad_to_analog` | int | `0` | Convert DPAD to left stick (ABS_X/ABS_Y). |
| `dpad_threshold` | int | `50` | Threshold percentage (0–100) for analog-to-DPAD conversion. Converted to absolute 0–32767 range internally. |

### Axis-to-Button Triggers

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `axis_btn` | string | _(empty)_ | Comma-separated rules: `axis:btn:on%:off%[:mode]`. Generates a key press when axis exceeds threshold. |

Rule format:
- **axis**: Linux ABS code of the source axis (mapped code, not physical scancode)
- **btn**: Linux BTN code to emit
- **on%**: Press threshold as percentage of axis range
- **off%**: Release threshold (hysteresis) as percentage
- **mode**: `h` = hijack (suppress ABS event), `b` = broadcast both (default)

Example: `2:312:80:60:h` — when ABS_Z reaches 80%, emit BTN_TL2 press; release at 60%; suppress the ABS_Z event.

### Button Blacklists

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `blacklist_vpad` | string | _(empty)_ | Comma-separated hex scan codes to **exclude from the virtual device**. These buttons will not be advertised by the uinput device at all. Example: `0x2f0,0x2f1` removes Ext1 and Ext2. |
| `blacklist_pass` | string | _(empty)_ | Comma-separated hex scan codes to **suppress from event passthrough**. The button exists on the virtual device but events are silently dropped. Applied after button remapping. Example: `0x130` suppresses BTN_A. |

### Force Feedback / Rumble

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `pwm_enable` | int | `1` | Enable PWM vibration bridge fallback (uses device vibrator when controller lacks native FF). |
| `pwm_intensity` | int | `255` | PWM vibration intensity (0–255). |

## Event Processing Pipeline

For each raw `input_event` from a physical device:

1. **SYN passthrough** — `EV_SYN` events are forwarded directly
2. **EV_KEY processing:**
   - Apply `.kl` key mapping (physical scancode → mapped code)
   - Apply user button remap (`remap_btn`)
   - Check passthrough blacklist (`blacklist_pass`) — drop if matched
3. **EV_ABS processing:**
   - Apply `.kl` axis mapping via `mAbsMap` (includes role overrides and axis remaps)
   - Normalize raw value based on per-device physical `absinfo` (handles bipolar triggers, unipolar triggers, and standard axes)
   - Process axis-to-button triggers (`axis_btn`) — may generate extra EV_KEY events
   - Apply calibration (center offset, range normalization, circular/linear deadzone, sensitivity, inversion)
   - Apply DPAD/analog conversion if enabled

## Key Layout (.kl) File Support

GammaPad parses Android `.kl` files at `/system/usr/keylayout/Vendor_XXXX_Product_YYYY.kl` to determine axis and key mappings for specific controller hardware. This happens during device discovery in `KeyLayoutParser::parse()`.

The parser handles:
- `axis` directives: maps physical ABS codes to Android axis constants
- `key` directives: maps physical scan codes to Android key constants
- `split` axes: e.g., `axis 0x02 split 0x0a 0x09` splits a bipolar axis into two unipolar triggers

A heuristic remapping (`applyHeuristicMapping`) handles controllers without `.kl` files by detecting common axis layouts (e.g., right stick on Z/RZ, triggers on GAS/BRAKE) and remapping them to standard Xbox 360 layout. The heuristic is only applied when the virtual device PID is 0x02fd; for PID 0x0b13 (default), axes pass through in their native layout.

## Hot Reload

The daemon polls `persist.gammaos.gamepad.config_version` every second. When the value changes:

1. All physical devices are released (ungrabbed)
2. Config is reloaded from all properties
3. Devices are re-scanned and re-grabbed
4. Global maps are rebuilt (`.kl` + roles + remaps)
5. Virtual device is recreated with updated capabilities

The Settings UI calls `bumpConfigVersion()` after every change to trigger this cycle.

## Hotplug

An `inotify` watch on `/dev/input/` detects device addition/removal. New devices are probed for gamepad characteristics (EV_ABS + EV_KEY with stick axes and BTN_A/BTN_GAMEPAD), checked against the device name filter, grabbed exclusively, and merged into the global capability set. The virtual device is recreated to reflect the new combined capabilities.

## Force Feedback

GammaPad supports two FF paths:

1. **Native forwarding**: If a physical device supports `FF_RUMBLE`, effect uploads and play/stop events are forwarded directly via ioctl.
2. **PWM vibration bridge**: For controllers without native FF, rumble events are sent over a Unix domain socket (`gammapad_vibrate`) to `GammapadVibrationBridge` (a SystemServer service), which triggers the device's built-in vibrator motor.

The virtual device always advertises `FF_RUMBLE` capability so apps can request vibration regardless of the physical controller's capabilities.
