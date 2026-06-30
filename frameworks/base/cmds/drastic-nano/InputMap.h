/*
 * Copyright (C) 2026 GammaOS
 *
 * Input plumbing for drastic-nano. Replaces and extends the earlier
 * pollInput/scanInputDevices logic inlined in main.cpp. Adds:
 *
 *   - analog stick axes (ABS_X/Y + ABS_RX/RY)
 *   - analog triggers (ABS_Z / ABS_RZ) and digital (BTN_TL2/TR2)
 *   - thumb buttons (BTN_THUMBL / BTN_THUMBR, aka L3/R3)
 *   - a keycode -> action-index map derived from the user's drastic
 *     SharedPreferences (_KeyMapConfigs_0_*)
 *   - short-press / long-press BACK detection
 *   - a frame-level InputActions output struct so the render loop
 *     doesn't have to know about evdev codes
 *
 * The struct layout (plain C++ POD) is deliberately header-visible so
 * main.cpp can hold an InputState on the stack without having to heap-
 * allocate it. Everything else lives in InputMap.cpp.
 */

#pragma once

#include <cstdint>
#include <string>
#include <unordered_map>
#include <vector>

#include "DrasticPrefs.h"

namespace android {
namespace drastic_input {

// DS stylus coordinates derived from the touchscreen / analog stick.
struct TouchState {
    int  x;       // 0..255
    int  y;       // 0..191
    bool held;    // true while the pointer is "down"
};

struct InputState {
    // Gamepad-ish event devices (keys / D-pad / analog axes).
    std::vector<int> fds;
    // Latched DS button bitmask (bits 0..11 in DrasticRunner::kDsBtn*).
    int dsBtnMask = 0;

    // Short/long-press BACK tracking.
    bool backWasDown = false;
    int64_t backPressStartMs = 0;

    // Short/long-press POWER tracking (short release = sleep, hold =
    // toggle the in-game menu). powerHoldFired latches the hold action
    // one-shot so it does not re-fire every frame while held, and the
    // release after a fired hold is ignored.
    bool powerWasDown = false;
    int64_t powerPressStartMs = 0;
    bool powerHoldFired = false;

    // Lid (hall-effect) switch state, EV_SW/SW_LID. value 1 = closed.
    // Tracked as a level so a close edge sleeps and an open edge wakes.
    bool lidClosed = false;

    // Touchscreen state. A unit can expose more than one touch node for
    // the same panel (for example a MediaTek "mtk-tpd" alongside the
    // vendor controller "hyn_ts"); every touch-capable node is opened and
    // drained, since only one of them actually streams events and which one
    // varies by kernel. touchPanelW/H hold the panel's ABS range, which is
    // identical across a unit's touch nodes.
    std::vector<int> touchFds;
    int touchPanelW = 0;
    int touchPanelH = 0;
    int touchDsX = 0;
    int touchDsY = 0;
    bool touchHeld = false;
    // True only for a REAL finger on the panel (set by drainTouch). Unlike
    // touchHeld it is never set by the analog-stick-as-stylus path, so the
    // overlay keyboard can take real taps without the stick double-triggering.
    bool touchReal = false;
    int touchPendingX = 0;
    int touchPendingY = 0;
    bool touchPendingValid = false;

    // Analog axes -- raw signed values, auto-centered from EVIOCGABS.
    struct Axis { int raw = 0; int min = 0; int max = 0; bool seen = false; };
    Axis axLX, axLY, axRX, axRY;
    // Triggers are typically unsigned 0..max.
    Axis axLZ, axRZ;
    // Digital trigger / thumb press state.
    bool btnL2 = false, btnR2 = false;
    bool btnL3 = false, btnR3 = false;
    // Held state for the fast-forward action. Tracked as held (not
    // edge) because drastic's fast-forward lever is "hold the button
    // to run at uncapped emu speed" -- pollInputMap copies this into
    // InputActions::actFastFwd each frame.
    bool btnFastFwd = false;

    // Held state for drastic's "Stylus Touch" action (slot 28). Acts
    // as a momentary stylus tap at the current cursor position: while
    // held, the stylus is "down" at (touchDsX, touchDsY) even if no
    // real touchscreen finger is present. Lets the player drive the
    // stylus via LS (analogTouch mode) + R3 tap. NOTE: the default R3
    // binding now toggles the virtual cursor below; this momentary mode
    // survives only for a button explicitly remapped to action 28.
    bool stylusBtnHeld = false;

    // Virtual touch cursor. Toggled by the "Touch Cursor" action (R3 by
    // default). While active a pointer is drawn over the DS bottom screen
    // and moved by the D-Pad / left stick (X held = faster, Y held =
    // slower); A taps/holds a touch at its position. The cursor-control
    // buttons (D-Pad, A, X, Y) are then consumed so they do not also reach
    // the game. Position is kept in DS-native units so it injects directly.
    bool  cursorMode = false;
    float cursorX = 128.0f;            // DS native 0..255
    float cursorY = 96.0f;             // DS native 0..191
    bool  cursorToggleWasDown = false; // edge-detect the toggle button
    // Last hat axis value for debounce: emit nav events only on
    // transitions (pad stays at the same hat value across many
    // EV_ABS events; firing nav every time scrolls the menu in a
    // single flick).
    int  hat0xPrev = 0;
    int  hat0yPrev = 0;

    // Derived: which Android keycode is currently held? Tracked for
    // the "capture key" overlay mode (controls remap).
    int lastKeyDownAndroidKc = 0;
    bool lastKeyDownConsumed = true;

    // User-supplied keymap snapshot (player 0 only). Index: action
    // index 0..28. Value: Android keycode, or -1 for unmapped.
    int keymapPlayer0[drastic_prefs::kNumActions];
    // Reverse lookup: android keycode -> action index.
    std::unordered_map<int, int> keycodeToAction;

    // Options derived from prefs.
    bool  analogTouchEnabled = false;
    float analogDeadzone = 0.15f;
};

// Per-frame output of pollInputMap. All flags are "this frame" (edge-
// triggered); held state is reflected in dsBtnMask and touch fields.
struct InputActions {
    int  dsBtnMask = 0;   // DS buttons to forward to drastic
    int  touchX = 0;
    int  touchY = 0;
    bool touchHeld = false;
    // touchX/touchY are already FINAL DS-native coordinates (the virtual
    // cursor), so the render loop must inject them directly and skip the
    // real-panel digitizer->layout remap. False for an ordinary panel touch.
    bool touchDirect = false;

    // Navigation (only set when overlayOpen is true).
    bool navUp = false;
    bool navDown = false;
    bool navLeft = false;
    bool navRight = false;
    bool navAccept = false;  // A
    bool navCancel = false;  // B
    bool navNextTab = false; // R / R1
    bool navPrevTab = false; // L / L1

    // Held LEVEL of the dpad/stick directions (true the whole time the
    // direction is down, not just on the press edge). The overlay
    // edge-detects these to drive PS3-XMB-style hold-to-repeat scrolling
    // (navPress/navRelease/tickNavRepeat). Always populated, even while a
    // game is running.
    bool navUpHeld = false;
    bool navDownHeld = false;
    bool navLeftHeld = false;
    bool navRightHeld = false;

    // In-app volume / brightness adjust (from the volume keys: VOL alone =
    // volume, SELECT+VOL = brightness). -1 / 0 / +1. Drives the slider HUDs
    // since the SurfaceFlinger system HUDs never appear on the DRM path.
    int volAdjust = 0;
    int brightAdjust = 0;

    // BACK toggle: short-press = open/close drastic's own in-game menu.
    bool menuToggle = false;
    // Long-press BACK >= kBackHoldMs = exit drastic-nano.
    bool exitRequested = false;
    // Short POWER press (released before powerHoldMs) = system sleep.
    // Also set on a lid-close edge (hall sensor) so closing the lid
    // sleeps exactly like a short power press.
    bool sleepRequested = false;
    // POWER held >= powerHoldMs = raise the in-game overlay menu
    // (one-shot edge).
    bool xmbOverlayRequested = false;

    // Special drastic actions triggered by the action-index remap.
    bool actFastFwd    = false;
    bool actQuickSave  = false;
    bool actQuickLoad  = false;
    bool actSwapScreens = false;
    bool actToggleMic  = false;

    // When true (overlay in capture-key mode), the last keydown
    // Android keycode is exposed here. The overlay consumes it by
    // inspecting capturedAndroidKc != 0 and then setting state's
    // lastKeyDownConsumed = true.
    int capturedAndroidKc = 0;
};

// Populate state->fds and state->touchFds by walking /dev/input.
// Reads EVIOCGABS for each detected axis so deadzone/scaling work.
void scanInputDevices(InputState* st);

// Install keymap from the prefs: copy keymap[0] into keymapPlayer0
// and rebuild keycodeToAction. Also copies analogTouch / analogDeadzone.
void applyPrefs(InputState* st, const drastic_prefs::Prefs& p);

// Drain all pending evdev events, update st, and produce actions for
// this frame. overlayOpen gates whether DS input is passed through
// (false = route buttons to drastic; true = route to overlay nav).
// captureKey gates whether an arbitrary keydown is captured for
// controls remap (overrides normal routing for one event).
//
// shortBackMs is the short-press threshold (release before this = open
// menu). longBackMs is the long-press threshold (held this long = exit).
// powerHoldMs splits the POWER gestures: release before it requests
// sleep, holding past it toggles the in-game menu.
void pollInputMap(InputState* st,
                  bool overlayOpen,
                  bool captureKey,
                  int64_t shortBackMs,
                  int64_t longBackMs,
                  int64_t powerHoldMs,
                  InputActions* out);

// Close all fds owned by state and reset the vector.
void closeInputDevices(InputState* st);

// Exposed for the overlay's "Controls" section: re-scan the stick
// calibration on the fly if the user's stick drifts. No-op today
// beyond re-running EVIOCGABS on whatever fds are already open.
void recalibrateAxes(InputState* st);

} // namespace drastic_input
} // namespace android
