# GammaOS Nano OSK: Complete Map of the Current Implementation

Scope: this document maps the *current* on-screen keyboard (OSK) inside the
GammaOS Nano standalone binary at
`/work/GammaOSNextDistribution-A14/frameworks/base/cmds/gammaos-nano/`. It is
the exact replacement surface for a Leanback-style native OSK rewrite. Every
caller contract that the rest of the binary depends on is enumerated so a new
OSK can drop in without touching the callers.

All file paths below are absolute. Line numbers are from the working tree at
the time of analysis (branch `develop`).

---

## 0. Files involved

| File | Role in OSK |
| --- | --- |
| `NanoMenu.h` | Declares OSK methods (lines 214-221, 270-275) and all `mOsk*` members (lines 649-656, 676-683) |
| `NanoMenuUtils.h` | The static keyboard layout `kOskLayout[5][10]` plus `kOskRows`/`kOskCols` (lines 33-41), and `containsInsensitive()` used by search |
| `NanoMenuShaders.h` | `FONT_CHAR_W = 8`, `FONT_CHAR_H = 16` (lines 64-65), used by all OSK metrics |
| `NanoMenuXmb.cpp` | Core OSK logic: `openOsk`/`closeOsk`/`oskType`/`oskBackspace`/`oskConfirm`/`updateSearchResults`/`renderOsk` (lines 1487-1645); plus OSK cursor movement inside `handleLeft`/`handleRight` (lines 1037-1084) |
| `NanoMenuInput.cpp` | Input dispatch: face-button + dpad routing while OSK active (`handleSelect`, `handleBack`, `handleUp`, `handleDown`, and the `pollInput` switch) |
| `NanoMenuSettings.cpp` | Password mode: `openOskForPassword` (line 1065), `maskPassword` (line 1079), `renderPasswordPromptOverlay` (line 1403); Wi-Fi password caller (line 726) |
| `NanoMenuSettingsTree.cpp` | Text-edit caller of `openOskForPassword` with plaintext flag (line 954) |
| `NanoMenuSettingsRender.cpp` | Renders the password band when the OSK is active inside the settings tree (line 186) |
| `NanoMenuRender.cpp` | Calls `renderOsk()` in the overall `render()` ordering (lines 994, 1002) |
| `NanoMenu.cpp` | Constructor initializers for OSK members (lines 147-148) |

---

## 1. Current OSK data model (all `mOsk*` members)

Declared in `NanoMenu.h`. There are two clusters of OSK state: the "search"
cluster (lines 676-683) and the "password" cluster (lines 649-656).

Search/core OSK cluster (`NanoMenu.h:676-683`):

```cpp
bool mOskActive;           // OSK is visible and receiving input
bool mOskShift;            // true = uppercase letters (A-Z); false = lowercase
std::string mOskQuery;     // Current search query  (ALSO the typed buffer in password mode)
int mOskCursorX;           // OSK grid cursor column
int mOskCursorY;           // OSK grid cursor row
std::vector<SearchResult> mSearchResults;
int mSearchSelectedIndex;
bool mSearchActive;        // Search results being displayed
```

Password cluster (`NanoMenu.h:649-656`):

```cpp
bool mOskPasswordMode;     // OSK types into a masked field; Enter fires callback
bool mOskPlaintext;        // password mode but render the buffer unmasked (settings text edit)
std::string mOskPasswordPrompt;                       // label shown above the field
std::function<void(const std::string&)> mOskPasswordCallback;  // fired on Enter with raw text
```

Field-by-field meaning:

- `mOskActive` (`bool`): the single "is the OSK visible and grabbing input"
  flag. Every input handler in the binary checks this FIRST before doing its
  own thing. This is the master gate. Initialized `false` in the constructor
  (`NanoMenu.cpp:148`).
- `mOskShift` (`bool`): `true` means letter keys produce uppercase A-Z, `false`
  means lowercase. Digits/symbols are unaffected (only `'A'..'Z'` get `+32`).
  Toggled by L1. Both `openOsk()` and `openOskForPassword()` reset it to `true`
  (start uppercase). Initialized `true` (`NanoMenu.cpp:148`).
- `mOskQuery` (`std::string`): THE typed buffer. In search mode it is the live
  search string; in password mode it is the raw password/text. Hard cap of
  64 chars enforced in `oskType()` (`NanoMenuXmb.cpp:1517`).
- `mOskCursorX` / `mOskCursorY` (`int`): the selected cell in the 10-wide by
  5-tall grid. Both clamped to `[0, kOskCols-1]` / `[0, kOskRows-1]`. Reset to
  0 on every open. Initialized 0/0 (`NanoMenu.cpp:148`).
- `mSearchResults` (`std::vector<SearchResult>`): matches from the XMB game
  library. `SearchResult` holds `{int sysIdx; int gameIdx;}` (used at
  `NanoMenuXmb.cpp:1559` as `mSearchResults.push_back({s, g})`). Capped at 100
  results (`NanoMenuXmb.cpp:1560`).
- `mSearchSelectedIndex` (`int`): cursor inside the results list once the user
  confirms a search.
- `mSearchActive` (`bool`): true when the search-results list is being shown in
  the XMB vertical column. Decoupled from `mOskActive`: the OSK can close while
  results stay visible.
- `mOskPasswordMode` (`bool`): when true, the OSK is being used to collect a
  password/text value rather than a search. Keystrokes still land in
  `mOskQuery` but the HUD masks them (unless `mOskPlaintext`), and Enter fires
  `mOskPasswordCallback` instead of running a search. Initialized `false`
  (`NanoMenu.cpp:147`).
- `mOskPlaintext` (`bool`): a sub-mode of password mode. When true, the typed
  buffer is rendered unmasked. Set only by the settings text-edit caller
  (`NanoMenuSettingsTree.cpp:961`) so the user can see/edit an existing string
  (e.g. a hostname). Note: it is NOT set in the constructor's initializer list,
  it is set to `false` defensively in `openOskForPassword()`
  (`NanoMenuSettings.cpp:1068`) on each open.
- `mOskPasswordPrompt` (`std::string`): the label drawn above the masked field
  (e.g. `Wi-Fi password for "MySSID"`). Empty defaults to literal `"Password"`
  in the grid renderer (`NanoMenuXmb.cpp:1590`).
- `mOskPasswordCallback` (`std::function<void(const std::string&)>`): invoked
  exactly once on confirm with the final `mOskQuery`. Moved out and cleared
  before invocation so it can never double-fire. On cancel it is cleared
  WITHOUT being invoked.

Layout constants (`NanoMenuUtils.h:33-41`):

```cpp
static const int kOskRows = 5;
static const int kOskCols = 10;
static const char kOskLayout[5][10] = {
    {'0','1','2','3','4','5','6','7','8','9'},
    {'A','B','C','D','E','F','G','H','I','J'},
    {'K','L','M','N','O','P','Q','R','S','T'},
    {'U','V','W','X','Y','Z','.','-','_','@'},
    {'!','#','$','%','&','*','+','=','?',' '},
};
```

50 keys total in a fixed 5x10 grid. There is NO dedicated Backspace key, NO
Enter key, NO Shift key, and NO Space key in the grid (space is the last cell
of row 4, value `' '`, displayed as `_`). Backspace/Enter/Shift are physical
gamepad buttons (X/Start/L1). The lowercase rendering is derived at draw time:
the stored layout is always uppercase and `+32` is applied to `'A'..'Z'` when
`mOskShift` is false. So lowercase typing reuses the same grid.

There is NO separate cursor/caret position inside `mOskQuery`; typing always
appends and backspace always pops the last char. There is no text-field
left/right caret movement; dpad Left/Right move the *grid* cursor, not a caret.

---

## 2. Entry points and modes

There are exactly TWO public ways to open the OSK, plus internal re-show logic.

### 2.1 Search OSK -- `openOsk()`  (`NanoMenuXmb.cpp:1487-1499`)

Resets everything to a clean search:

```cpp
void NanoMenu::openOsk() {
    mOskActive = true;
    mOskShift = true;        // start uppercase; L1 toggles to lowercase
    mOskPasswordMode = false;
    mOskPasswordPrompt.clear();
    mOskPasswordCallback = nullptr;
    mOskQuery.clear();
    mOskCursorX = 0;
    mOskCursorY = 0;
    mSearchResults.clear();
    mSearchSelectedIndex = 0;
    mSearchActive = false;
}
```

Who opens it: only ONE call site -- `NanoMenuInput.cpp:843`, the Y button while
in XMB mode and not already showing search/OSK (see section 3). It is a
search-only entry. `mOskPasswordMode` is forced false.

What confirm does (`oskConfirm`, search branch, `NanoMenuXmb.cpp:1543-1549`):
sets `mOskActive=false`, and if the query is non-empty sets
`mSearchActive=true`, resets `mSearchSelectedIndex=0`, and calls
`updateSearchResults()`. If empty, `mSearchActive=false`. Note the typed text
search already runs incrementally on every keystroke (`oskType`/`oskBackspace`
call `updateSearchResults()` live when not in password mode), so confirm mostly
"locks in" the list and hands focus to the results column.

What cancel does (`closeOsk`, search branch, `NanoMenuXmb.cpp:1511-1513`): if
`mOskQuery` is empty, also clears `mSearchActive`. If the query is non-empty,
the search results stay on screen (OSK hides, list remains).

`updateSearchResults()` (`NanoMenuXmb.cpp:1552-1565`): clears results; if query
empty, returns. Otherwise iterates every XMB system and every game display
name, pushing `{sysIdx, gameIdx}` for any name where
`containsInsensitive(name, mOskQuery)` is true (case-insensitive substring,
defined in `NanoMenuUtils.h:44`). Caps at 100 results. Then sets
`mSearchActive = !mSearchResults.empty() || !mOskQuery.empty()` (so a query with
zero matches still shows the search state with an empty list).

### 2.2 Password / text OSK -- `openOskForPassword(prompt, onSubmit)`  (`NanoMenuSettings.cpp:1065-1077`)

```cpp
void NanoMenu::openOskForPassword(const std::string& prompt,
                                  std::function<void(const std::string&)> onSubmit) {
    mOskPasswordMode = true;
    mOskPlaintext = false;
    mOskPasswordPrompt = prompt;
    mOskPasswordCallback = std::move(onSubmit);
    mOskQuery.clear();
    mOskCursorX = 0;
    mOskCursorY = 0;
    mOskShift = true;
    mOskActive = true;
    mDisplayDirty = true;
}
```

Signature in `NanoMenu.h:272-273`:
`void openOskForPassword(const std::string& prompt, std::function<void(const std::string&)> onSubmit);`

What confirm does (`oskConfirm`, password branch, `NanoMenuXmb.cpp:1532-1541`):

```cpp
if (mOskPasswordMode) {
    auto cb = std::move(mOskPasswordCallback);
    std::string pw = mOskQuery;
    mOskPasswordMode = false;
    mOskPlaintext = false;
    mOskPasswordPrompt.clear();
    mOskPasswordCallback = nullptr;
    mOskQuery.clear();
    if (cb) cb(pw);
    return;
}
```

Important contract details: the callback is moved out, the buffer copied, ALL
password state torn down, and only THEN is `cb(pw)` invoked. So inside the
callback the OSK is already fully closed and it is safe for the callback to
re-open the OSK. `mOskActive` was set false at the very top of `oskConfirm()`
(`NanoMenuXmb.cpp:1531`).

What cancel does (`closeOsk`, password branch, `NanoMenuXmb.cpp:1503-1510`):

```cpp
if (mOskPasswordMode) {
    mOskPasswordMode = false;
    mOskPlaintext = false;
    mOskPasswordPrompt.clear();
    mOskPasswordCallback = nullptr;
    mOskQuery.clear();
    return;
}
```

Cancel does NOT invoke the callback. The pending value is discarded silently.

#### Caller A: Wi-Fi password  (`NanoMenuSettings.cpp:723-735`)

In `handleWifiScreenSelect()`, when the user picks an unsaved secure network
(`e.security != 0 && e.security != 4`, i.e. WEP/WPA2/WPA3), the SSID and
security are stashed in `mWifiPendingSsid` / `mWifiPendingSecurity`
(`NanoMenu.h:632-633`), then:

```cpp
std::string prompt = "Wi-Fi password for \"" + e.ssid + "\"";
openOskForPassword(prompt,
    [this](const std::string& pw) {
        if (pw.empty()) {
            mWifiStatusMsg = "Cancelled";
            mWifiStatusMsgUntilMs = <now+2000ms>;
            return;
        }
        addAndConnectWifi(mWifiPendingSsid, mWifiPendingSecurity, pw);
    });
```

Contract on confirm: the callback receives the raw password and calls
`addAndConnectWifi(ssid, security, pw)`. An empty string is treated as
"cancelled" by the callback itself (shows a 2 s status message). Note: because
real cancel (`closeOsk`) never invokes the callback, the only path that yields
an empty `pw` to this callback is the user pressing Start/Enter with an empty
field. So "cancel" semantics here are partly implemented in the callback and
partly in `closeOsk`.

#### Caller B: Settings tree text edit  (`NanoMenuSettingsTree.cpp:950-962`)

In `handleSettingsTreeSelect()`, for a `SettingNodeType::kText` node:

```cpp
mSettingsEditNodeIdx = nodeIdx;
std::string prompt = node.label;
std::string cur = getSettingsCachedValue(nodeIdx);
openOskForPassword(prompt, [this](const std::string& val) {
    if (mSettingsEditNodeIdx >= 0) {
        settingsSetTextValue(mSettingsEditNodeIdx, val);
        mSettingsEditNodeIdx = -1;
    }
});
mOskQuery = cur;        // pre-fill with current value
mOskPlaintext = true;   // show unmasked
```

This is the one case that uses the password OSK as a *general text editor*: it
pre-populates `mOskQuery` with the current value AFTER calling
`openOskForPassword` (which clears it), and flips `mOskPlaintext=true` so the
text is shown, not masked. Contract on confirm: callback writes the new string
via `settingsSetTextValue(nodeIdx, val)`.

### 2.3 Internal re-show (NOT a public entry point)

`NanoMenuInput.cpp:838-844` (Y button in XMB): if `mOskActive`, close it; else
if `mSearchActive` (results showing, OSK hidden) just set `mOskActive = true`
to re-show the keyboard over the existing query/results WITHOUT resetting
`mOskQuery`; else call `openOsk()` for a fresh search. So "Y" toggles the
keyboard back up to refine an existing search.

### 2.4 Setup wizard

The setup wizard does NOT have its own OSK opener. It reuses the Wi-Fi screen,
which routes A/B through the normal Wi-Fi handlers, which call
`openOskForPassword` (caller A). `NanoMenuInput.cpp:784-785` explicitly lets
WiFi/BT sub-screens during setup use the normal OSK handlers. There are no
`openOsk`/`mOskActive` references inside `NanoMenuSetupWizard.cpp` (verified by
grep). The password band over the setup wizard is drawn by
`renderPasswordPromptOverlay()` called from the setup render path
(`NanoMenuSettingsRender.cpp:186` is the settings-tree path; the wizard's Wi-Fi
step draws it via `renderWifiScreen` -> `renderPasswordPromptOverlay` at
`NanoMenuSettings.cpp:1268-1270`).

### 2.5 No other callers

A full grep confirms: `openOsk` has exactly one external caller
(`NanoMenuInput.cpp:843`), and `openOskForPassword` has exactly two external
callers (`NanoMenuSettings.cpp:726`, `NanoMenuSettingsTree.cpp:954`). There is
no touch path, no Android `InputConnection`, no IME. The typed text lives only
in `mOskQuery` (a plain `std::string`).

---

## 3. Input dispatch path while the OSK is active

Input is read raw from `/dev/input/event*` in `NanoMenu::pollInput()`
(`NanoMenuInput.cpp:658-949`) as Linux `struct input_event`. The relevant key
codes are the standard `linux/input.h` codes. Button-to-action mapping uses the
Nintendo physical layout convention noted in the code comments:

| Linux code | Physical button | OSK behaviour |
| --- | --- | --- |
| `BTN_SOUTH` / `KEY_ENTER` | A (and Enter) | `BTN_SOUTH` -> `handleSelect()`; `KEY_ENTER` -> `oskConfirm()` if OSK active else `handleSelect()` |
| `BTN_EAST` / `KEY_BACK` | B | `handleBack()` -> `closeOsk()` if OSK active (cancel) |
| `BTN_NORTH` | X | `oskBackspace()` if OSK active |
| `BTN_WEST` | Y | toggles OSK (close / re-show / `openOsk()`) -- see 2.3 |
| `BTN_START` | Start | `oskConfirm()` if OSK active (submit/search) |
| `BTN_TL` / `KEY_L` | L1 | toggle `mOskShift` if OSK active |
| `BTN_TR` / `KEY_R` | R1 | NOT intercepted by OSK; always toggles Quick Resume |
| `KEY_UP` / `BTN`(HAT0Y<0) / stick Y< | Dpad Up | `handleUp()` -> `mOskCursorY--` |
| `KEY_DOWN` / HAT0Y>0 / stick Y> | Dpad Down | `handleDown()` -> `mOskCursorY++` (clamp `kOskRows-1`) |
| `KEY_LEFT` / HAT0X<0 / stick X< | Dpad Left | `handleLeft()` -> `mOskCursorX--` |
| `KEY_RIGHT` / HAT0X>0 / stick X> | Dpad Right | `handleRight()` -> `mOskCursorX++` (clamp `kOskCols-1`) |

The exact dispatch sites:

**A button = type the selected key.** `BTN_SOUTH` -> `handleSelect()`
(`NanoMenuInput.cpp:811-812`). In `handleSelect()` the OSK is checked first
(`NanoMenuInput.cpp:292-297`):

```cpp
void NanoMenu::handleSelect() {
    if (mOskActive) {
        char ch = kOskLayout[mOskCursorY][mOskCursorX];
        if (!mOskShift && ch >= 'A' && ch <= 'Z') ch += 32;
        oskType(ch);
        return;
    }
    ...
}
```

So A reads the grid cell under the cursor, applies the shift transform, and
calls `oskType(ch)`. `oskType` (`NanoMenuXmb.cpp:1516-1521`) appends to
`mOskQuery` if under 64 chars and runs `updateSearchResults()` live unless in
password mode.

Note an important detail: `KEY_ENTER` is mapped to `oskConfirm()` when OSK
active (`NanoMenuInput.cpp:813-816`), but `BTN_SOUTH` is mapped to
`handleSelect()` -> type. So on a keyboard, Enter submits; on a gamepad, A
types. This is why a separate physical Start button exists for "submit".

**B button = cancel.** `BTN_EAST` / `KEY_BACK` -> `handleBack()`
(`NanoMenuInput.cpp:826-828`, with a setup-wizard override). `handleBack()`
checks OSK first (`NanoMenuInput.cpp:249-252`):

```cpp
if (mOskActive) {
    closeOsk();
    return;
}
```

**X button = backspace.** `BTN_NORTH` (`NanoMenuInput.cpp:856-857`):
`if (mOskActive) { oskBackspace(); break; }`. `oskBackspace`
(`NanoMenuXmb.cpp:1523-1528`) pops the last char and re-runs search live unless
password mode.

**Y button = toggle keyboard.** `BTN_WEST` (`NanoMenuInput.cpp:834-855`). In
XMB mode: if `mOskActive` -> `closeOsk()`; else if `mSearchActive` ->
`mOskActive = true` (re-show over existing query); else `openOsk()`. (Outside
XMB, Y cycles the wallpaper effect, and in MENU_BT it unpairs -- those branches
are guarded so they don't fire when the OSK is up because the XMB branch is
taken first when `mXmbMode` is set, but note: the OSK is only ever opened from
XMB mode anyway.)

**Start = submit.** `BTN_START` (`NanoMenuInput.cpp:817-825`):
`if (mOskActive) oskConfirm();`. The OSK help text advertises
`Start:Submit` / `Start:Search`, and the comment notes this exists for devices
that do not map physical Start to `KEY_ENTER`.

**L1 = shift toggle.** `BTN_TL` / `KEY_L` (`NanoMenuInput.cpp:869-874`):

```cpp
if (mOskActive) {
    mOskShift = !mOskShift;
    mDisplayDirty = true;
    break;
}
```

If the OSK is NOT active, L1 toggles XMB mode itself, so the OSK guard is
essential.

**R1** (`BTN_TR` / `KEY_R`, `NanoMenuInput.cpp:890-896`) is NOT OSK-aware; it
always toggles Quick Resume. This is the one face/shoulder button that "leaks"
through while the OSK is open. A faithful reimplementation can keep this
behaviour or, if a Leanback OSK wants R1 for something (e.g. clear field),
that is a free design choice since nothing depends on R1 being inert in OSK
mode.

**Dpad cursor movement.** All four directions are guarded by `mOskActive`:

- Up: `handleUp` `NanoMenuInput.cpp:463-466` -> `if (mOskCursorY > 0) mOskCursorY--;`
- Down: `handleDown` `NanoMenuInput.cpp:514-517` -> `if (mOskCursorY < kOskRows - 1) mOskCursorY++;`
- Left: `handleLeft` `NanoMenuXmb.cpp:1040-1043` -> `if (mOskCursorX > 0) mOskCursorX--;`
- Right: `handleRight` `NanoMenuXmb.cpp:1064-1068` -> `maxCol = kOskCols-1; if (mOskCursorX < maxCol) mOskCursorX++;`

There is NO wraparound (cursor stops at edges). Directional input flows through
`navPress()`/`navRelease()`/`tickNavRepeat()` (`NanoMenuInput.cpp:595-652`),
which gives hold-to-repeat auto-scroll; the OSK cursor benefits from that for
free since the repeat just re-invokes `handleUp`/etc.

**How control returns.** Each handler that consumes an OSK event `return`s (or
`break`s out of the switch) immediately after, so no downstream menu logic
runs. There is no event-consumed boolean returned anywhere -- the `mOskActive`
check at the top of each handler IS the dispatch gate. The render loop simply
keeps drawing the OSK every frame until a button clears `mOskActive`
(`closeOsk`/`oskConfirm`, or the Y-toggle, or one of the launch paths in
`launchXmbGame`).

**Volume / brightness / power still work while OSK is up.** `KEY_VOLUMEUP/DOWN`
(with SELECT for brightness) and `KEY_POWER` are handled BEFORE the OSK switch
in `pollInput` (`NanoMenuInput.cpp:682-777`), so they are never blocked by the
OSK. A reimplementation must preserve this ordering.

---

## 4. Render ordering

`renderOsk()` is defined at `NanoMenuXmb.cpp:1567-1645` and guards on
`if (!mOskActive) return;` (line 1568). It is called from
`NanoMenu::render()` in `NanoMenuRender.cpp` in TWO places:

1. Setup wizard path (`NanoMenuRender.cpp:992-994`):
   ```cpp
   if (mSetupWizardActive) {
       renderSetupWizard();
       renderOsk();
   }
   ```
2. XMB path (`NanoMenuRender.cpp:995-1003`):
   ```cpp
   } else if (mXmbMode) {
       renderXmb();
       if (mMenuState == MENU_WIFI) renderWifiScreen();
       else if (mMenuState == MENU_BT) renderBtScreen();
       else if (mMenuState == MENU_SETTINGS) renderSettingsTree();
       // OSK is drawn last so the password keyboard sits on top of the
       // Wi-Fi / BT overlays (otherwise renderWifiScreen overdraws it).
       renderOsk();
   }
   ```

Key ordering contract: `renderOsk()` is the LAST thing drawn in the XMB
branch, AFTER `renderWifiScreen()` / `renderBtScreen()` / `renderSettingsTree()`.
This is deliberate (comment at `NanoMenuXmb.cpp:1900-1902` and
`NanoMenuRender.cpp:1000-1001`): the password OSK must sit ON TOP of the Wi-Fi
network list, otherwise the network list overdraws the keyboard. A
reimplementation MUST keep the OSK as the final overlay in this draw order.

There is a separate, smaller overlay: `renderPasswordPromptOverlay()`
(`NanoMenuSettings.cpp:1403-1422`) draws a top band showing the prompt label and
the masked/plaintext value. It is invoked from:
- `renderWifiScreen()` (`NanoMenuSettings.cpp:1268-1270`):
  `if (mOskActive && mOskPasswordMode) renderPasswordPromptOverlay();`
- `renderSettingsTree`'s render (`NanoMenuSettingsRender.cpp:186-188`):
  `if (mOskActive && !mOskPasswordMode) renderPasswordPromptOverlay();`
  (note: this branch is `!mOskPasswordMode`, used for the settings tree's own
  in-context display).

Note the prompt is ALSO drawn inline by `renderOsk()` itself on the query line
in password mode (`NanoMenuXmb.cpp:1589-1592`), so the prompt appears both as a
top band and on the OSK query line. A faithful Leanback rewrite needs to keep
showing the prompt + masked value somewhere; the exact top-band overlay is
cosmetic and free to redesign.

### renderOsk() drawing details (for visual parity)

Metrics (`NanoMenuXmb.cpp:1570-1583`):
- `sf = fminf(width/1080, height/720)`, floored at 0.5.
- `oskScale = 2.5f * sf`.
- `charW = FONT_CHAR_W * oskScale * 3.0f` (wide horizontal cell spacing;
  `FONT_CHAR_W = 8`).
- `charH = FONT_CHAR_H * oskScale` (`FONT_CHAR_H = 16`).
- `pad = 15.0f * sf`.
- grid panel: `gridW = kOskCols*charW + 2*pad`,
  `gridH = (kOskRows+1)*(charH + 8*sf) + 2*pad` (the +1 row is the query line).
- panel position: horizontally centered (`bgX = (width-gridW)/2`),
  bottom-anchored (`bgY = height - gridH - pad`).
- background quad: `drawQuad(bgX, bgY, gridW, gridH, 0,0,0, 0.85)`.

Query line (`NanoMenuXmb.cpp:1585-1598`): in search mode shows
`"Search: " + mOskQuery + "_"` in cyan (`0, 0.85, 1`); in password mode shows
`prompt + ": " + maskPassword(mOskQuery) + "_"` in orange (`1, 0.75, 0.35`).
`queryScale = 2.0f * sf`.

Grid (`NanoMenuXmb.cpp:1600-1625`): for each row/col, the selected cell gets a
highlight quad (`0, 0.35, 0.6, 0.9`); the glyph is `kOskLayout[row][col]` with
the `+32` lowercase transform when `!mOskShift`; space is rendered as `'_'`.
Selected glyph white `(1,1,1)`, unselected gray `(0.7,0.7,0.7)`. `drawText`
draws at `oskScale`.

Shift indicator (`NanoMenuXmb.cpp:1627-1635`): right-aligned `[ABC]`
(green when shift on) / `[abc]` (orange when shift off) at `1.5f * sf`.

Help line (`NanoMenuXmb.cpp:1637-1644`): password mode shows
`"A:Type  X:Backspace  L:Shift  Start:Submit  B/Y:Cancel"`; search mode shows
`"A:Type  X:Backspace  L:Shift  Start:Search  B/Y:Cancel"`. Gray, `1.5f * sf`.

---

## 5. Integration contract (what a new Leanback OSK MUST keep stable)

This is the minimal surface the rest of the binary calls or reads. Anything not
in this list is free to rewrite. Keep these signatures and observable behaviours
identical, and the new OSK drops in.

### 5.1 Public methods that MUST keep their exact signatures

Declared in `NanoMenu.h`:

| Method | Decl | Callers that must keep working |
| --- | --- | --- |
| `void openOsk()` | `NanoMenu.h:215` | `NanoMenuInput.cpp:843` (Y in XMB) |
| `void closeOsk()` | `NanoMenu.h:216` | `NanoMenuInput.cpp:250` (B/back), `NanoMenuInput.cpp:839` (Y toggle) |
| `void oskType(char c)` | `NanoMenu.h:217` | `NanoMenuInput.cpp:295` (A button) |
| `void oskBackspace()` | `NanoMenu.h:218` | `NanoMenuInput.cpp:857` (X button) |
| `void oskConfirm()` | `NanoMenu.h:219` | `NanoMenuInput.cpp:814, 823` (Enter / Start) |
| `void renderOsk()` | `NanoMenu.h:221` | `NanoMenuRender.cpp:994, 1002` |
| `void openOskForPassword(const std::string& prompt, std::function<void(const std::string&)> onSubmit)` | `NanoMenu.h:272-273` | `NanoMenuSettings.cpp:726`, `NanoMenuSettingsTree.cpp:954` |
| `std::string maskPassword(const std::string& s)` | `NanoMenu.h:275` | `NanoMenuXmb.cpp:1591`, `NanoMenuSettings.cpp:1418` (can be folded into the new renderer, only internal callers) |
| `void renderPasswordPromptOverlay()` | `NanoMenu.h:274` | `NanoMenuSettings.cpp:1269`, `NanoMenuSettingsRender.cpp:187` (can be merged into renderOsk; internal callers only) |
| `void updateSearchResults()` | `NanoMenu.h:220` | called internally from `oskType`/`oskBackspace`/`oskConfirm`; no external caller, but it populates `mSearchResults` which the XMB renderer reads |

The four genuinely external entry points the rest of the codebase calls are:
`openOsk()`, `openOskForPassword(prompt, cb)`, `closeOsk()`, `oskConfirm()`,
plus the four character/edit ops driven by the input layer (`oskType`,
`oskBackspace`, and the cursor moves), and `renderOsk()`.

### 5.2 State the rest of the codebase reads/writes directly

These members are touched outside the OSK's own methods and form part of the
contract. A new OSK either keeps these members or provides equivalent
accessors AND updates every external site listed.

- `mOskActive` (bool) -- THE "is the OSK visible / grabbing input" flag.
  Read as a gate at the top of `handleSelect` (`NanoMenuInput.cpp:292`),
  `handleBack` (249), `handleUp` (463), `handleDown` (514), `handleLeft`
  (`NanoMenuXmb.cpp:1040`), `handleRight` (1064), and in `pollInput` at
  lines 784, 814, 823, 838, 857, 870. Written externally by:
  - `NanoMenuInput.cpp:841` (Y re-show: `mOskActive = true`)
  - `NanoMenuInput.cpp:885` (L1 leaving XMB clears it: `mOskActive = false`)
  - `NanoMenuXmb.cpp:1218, 1251, 1408, 1452` (launch paths force it false)
  - render guards: `NanoMenuXmb.cpp:1568, 1893`,
    `NanoMenuSettings.cpp:1268, 1404`, `NanoMenuSettingsRender.cpp:186`
  This is the single most load-bearing piece of state. The new OSK MUST expose
  a bool that means exactly "OSK is up and consuming input", because the launch
  code clears it directly to dismiss the keyboard on game start, and the render
  ordering checks it.

- `mOskQuery` (std::string) -- the typed buffer. Read/written externally by:
  - `NanoMenuInput.cpp:267` (`mOskQuery.clear()` when B exits search results)
  - `NanoMenuSettingsTree.cpp:960` (`mOskQuery = cur;` pre-fill for text edit)
  - read in `renderXmb`'s search header (`NanoMenuXmb.cpp:1895`)
  The new OSK must expose a way to (a) read the typed string, (b) clear it, and
  (c) pre-seed it with an initial value (for the settings text-edit case).

- `mOskShift` (bool) -- toggled directly by L1 at `NanoMenuInput.cpp:871`.
  Read by `handleSelect` (`NanoMenuInput.cpp:294`) to apply case to the typed
  char, and by `renderOsk`. A Leanback OSK that has its own shift key can keep
  this as an internal detail, BUT the L1 handler in `pollInput` writes it
  directly, so either keep the member or move L1 handling into the OSK.

- `mOskPasswordMode` (bool) -- read externally to decide which overlay/branch to
  render: `NanoMenuSettings.cpp:1268` (Wi-Fi overlay shows only in password
  mode), `NanoMenuSettingsRender.cpp:186` (settings overlay shows only when NOT
  password mode), and `NanoMenuSettings.cpp:1418` (mask decision). Keep a flag
  distinguishing "password collection" from "search".

- `mOskPlaintext` (bool) -- set externally at `NanoMenuSettingsTree.cpp:961` to
  request unmasked rendering of a password-mode buffer. Needed for the settings
  text editor.

- `mOskCursorX` / `mOskCursorY` (int) -- the grid cursor. Written by the dpad
  handlers (`NanoMenuInput.cpp:464, 515`; `NanoMenuXmb.cpp:1041, 1066`). If the
  new Leanback OSK uses a different grid geometry, MOVE the cursor logic into
  the OSK and make the dpad handlers call OSK methods (e.g.
  `oskMove(NavDir)`), otherwise these external writes will index the wrong
  layout. This is the main coupling point that a geometry change breaks.

- `mOskPasswordPrompt`, `mOskPasswordCallback` -- only touched inside OSK
  methods after `openOskForPassword` stores them. Fully internal; safe to
  rewrite as long as the callback fires exactly once on confirm with the raw
  string and never on cancel.

- `mSearchResults` / `mSearchActive` / `mSearchSelectedIndex` -- these are the
  SEARCH RESULTS model, not strictly OSK, but `updateSearchResults()` populates
  them and the XMB vertical list renders them. The OSK contract is: on confirm
  with a non-empty query, set `mSearchActive=true` and fill `mSearchResults`
  with `{sysIdx, gameIdx}` matches. The XMB renderer
  (`NanoMenuXmb.cpp:1764-1765, 1837-1842`) and the Up/Down handlers
  (`NanoMenuInput.cpp:471-472, 522-524`) depend on this. Keep this behaviour.

### 5.3 Rendering primitives the OSK depends on (available to the rewrite)

The new OSK can draw with the existing helpers (all `NanoMenu` members):
- `void drawText(const char* str, float px, float py, float scale, float r, float g, float b, float a)` (`NanoMenu.h:402`)
- `float measureText(const char* str, float scale)` (`NanoMenu.h:404`)
- `void drawQuad(float x, float y, float w, float h, float r, float g, float b, float a)` (`NanoMenu.h:406`)
- `void drawIcon(int iconIdx, float x, float y, float size, float r, float g, float b, float a)` (`NanoMenu.h:671`)
- glyph atlas + FreeType faces: `mGlyphCache`, `mGlyphAtlasTex`, `ensureGlyph(codepoint)` (`NanoMenu.h:401`). The atlas already supports any Unicode codepoint (Roboto/DroidSans/Noto CJK/Noto Naskh Arabic/color emoji), so a Leanback layout with extended symbols, accented chars, or emoji is feasible with the existing renderer.
- screen dims: `mWidth`, `mHeight` (`NanoMenu.h:427-428`); per-frame delta `mFrameDt` (`NanoMenu.h:574`) for animation; `mDisplayDirty` (`NanoMenu.h:484`) to force a redraw (already set by `openOskForPassword` and the L1 toggle).
- DRM rotation globals used for scissor: `sDrmGlRotation`, `sDrmRotationDeg` (used in `renderXmb` scissor at `NanoMenuXmb.cpp:1803`); only relevant if the new OSK uses `glScissor`.

### 5.4 What is free to rewrite

- The grid geometry (`kOskLayout`, `kOskRows`, `kOskCols`) -- a Leanback OSK has
  a different, larger key set (letters + numbers + symbol page + space/done/
  delete keys, shift, language toggle). Replace freely, but MOVE the cursor
  movement and "type selected key" logic into the OSK so the external dpad
  handlers stop indexing `kOskLayout` directly (currently `handleSelect`
  indexes `kOskLayout[mOskCursorY][mOskCursorX]` at `NanoMenuInput.cpp:293`).
  Cleanest approach: add `oskMoveCursor(NavDir)` and `oskActivateKey()` and have
  the input handlers call those instead of poking `mOskCursorX/Y` and
  `kOskLayout`.
- The visual style of `renderOsk` and `renderPasswordPromptOverlay` -- fully
  cosmetic. Only constraint: `renderOsk` stays the last overlay in the XMB draw
  order and guards on `mOskActive`.
- The 64-char cap, the live-search-on-keystroke behaviour, the help/footer text.
- `maskPassword` (trivial `'*'` fill) and whether masking is per-char or
  Leanback-style "reveal last char then mask".

### 5.5 Minimal contract summary (the keep-stable list)

A new Leanback OSK must continue to provide, with identical external behaviour:

1. `openOsk()` -- start a fresh search OSK (clears query, password mode off).
2. `openOskForPassword(prompt, cb)` -- start a masked password/text OSK; on
   confirm fire `cb(rawString)` exactly once and tear down; on cancel never
   fire `cb`. Support pre-seeding the buffer (`mOskQuery = ...`) and an
   unmasked flag (`mOskPlaintext`) for the settings text editor.
3. `closeOsk()` -- cancel/dismiss; clear `mOskActive`; in search mode clear
   `mSearchActive` only if the query is empty.
4. `oskConfirm()` -- submit: password -> fire callback; search -> set
   `mSearchActive` and `updateSearchResults()`.
5. A typed-string accessor (today: the public `mOskQuery` member) readable and
   clearable by `NanoMenuInput.cpp` and `NanoMenuSettingsTree.cpp`.
6. `mOskActive` (or an `isOskVisible()`-style flag) that input handlers gate on
   and that the launch paths can force-clear, and that the renderer checks.
7. `mOskPasswordMode` flag so the Wi-Fi/settings overlays render correctly.
8. `renderOsk()` -- draws the keyboard as the final overlay; no-op when not
   active.
9. An input hook reachable from `pollInput` / `handleSelect` / `handleBack` /
   `handleUp` / `handleDown` / `handleLeft` / `handleRight` so A=type/activate,
   B=cancel, X=backspace, Y=toggle, Start/Enter=confirm, L1=shift, dpad=move.

---

## 6. Quick reference: every OSK-symbol call site (file:line -> action)

`openOsk`:
- `NanoMenuXmb.cpp:1487` def.
- `NanoMenuInput.cpp:843` Y-button fresh search open.

`openOskForPassword`:
- `NanoMenuSettings.cpp:1065` def.
- `NanoMenuSettings.cpp:726` Wi-Fi secure-network password prompt.
- `NanoMenuSettingsTree.cpp:954` settings `kText` node edit (then pre-fills `mOskQuery`, sets `mOskPlaintext`).

`closeOsk`:
- `NanoMenuXmb.cpp:1501` def.
- `NanoMenuInput.cpp:250` B/back cancels OSK.
- `NanoMenuInput.cpp:839` Y toggle (close when already open).

`oskType`:
- `NanoMenuXmb.cpp:1516` def.
- `NanoMenuInput.cpp:295` A button types selected grid key.

`oskBackspace`:
- `NanoMenuXmb.cpp:1523` def.
- `NanoMenuInput.cpp:857` X button.

`oskConfirm`:
- `NanoMenuXmb.cpp:1530` def.
- `NanoMenuInput.cpp:814` KEY_ENTER when OSK active.
- `NanoMenuInput.cpp:823` BTN_START when OSK active.

`renderOsk`:
- `NanoMenuXmb.cpp:1567` def.
- `NanoMenuRender.cpp:994` setup-wizard path.
- `NanoMenuRender.cpp:1002` XMB path (last overlay).

`updateSearchResults`:
- `NanoMenuXmb.cpp:1552` def.
- `NanoMenuXmb.cpp:1519` from oskType, `1526` from oskBackspace, `1546` from oskConfirm.

`mOskActive` external writes: `NanoMenuInput.cpp:841` (true), `885` (false);
`NanoMenuXmb.cpp:1218, 1251, 1408, 1452` (false, launch). Reads/gates listed in 5.2.

`mOskQuery` external: `NanoMenuInput.cpp:267` clear; `NanoMenuSettingsTree.cpp:960` set; `NanoMenuXmb.cpp:1895` read.

`mOskShift` external write: `NanoMenuInput.cpp:871` (L1 toggle). External read: `NanoMenuInput.cpp:294` (case apply).

`mOskCursorX/Y` external writes: `NanoMenuInput.cpp:464, 515`; `NanoMenuXmb.cpp:1041, 1066`. External read: `NanoMenuInput.cpp:293` (index layout).

`mOskPasswordMode` external reads: `NanoMenuSettingsRender.cpp:186`; `NanoMenuSettings.cpp:1268, 1418`.

`mOskPlaintext` external write: `NanoMenuSettingsTree.cpp:961`. Read: `NanoMenuSettings.cpp:1418`.

`maskPassword`: def `NanoMenuSettings.cpp:1079`; callers `NanoMenuXmb.cpp:1591`, `NanoMenuSettings.cpp:1418` (both internal to OSK render).

Constructor init: `NanoMenu.cpp:147-148` (`mOskPasswordMode(false)`,
`mOskActive(false)`, `mOskShift(true)`, `mOskCursorX(0)`, `mOskCursorY(0)`).
Note `mOskPlaintext` is NOT in the initializer list; it is set on each
`openOskForPassword` call.
