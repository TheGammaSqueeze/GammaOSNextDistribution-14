# Leanback IME: Style, Dimensions, Layout, Suggestions, Service Lifecycle, Password Handling

Scope: this document covers the visual styling, dimensions, overall input-area structure, suggestions strip, IME service lifecycle, and password/secure-field handling of the AOSP Leanback IME, as it exists in this tree. It is written to drive a 1:1 native C++ / OpenGL ES reimplementation inside the GammaOS Nano binary, driven by D-pad/gamepad input and typing into a plain std::string (no Android InputConnection).

All file paths are absolute. All measurements are quoted from source with units as written.

Source root: `/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/`

---

## 1. Dimensions and Colors (exact values)

### 1.1 Dimensions
From `/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/res/values/dimens.xml`:

| Resource name | Value | Comment in source | Notes for native port |
| --- | --- | --- | --- |
| `key_height` | `28dp` | height of keyboard keys | base cell height of every grid key |
| `key_width` | `28dp` | width of keyboard keys | base cell width of every grid key |
| `space_key_width` | `268dp` | "7x regular key + 6*horz_margin" -> 7*28 + 6*12 = 196 + 72 = 268 | space bar spans 7 key cells plus the gaps |
| `keyboard_horizontal_gap` | `12dp` | horizontal gap of the keyboard | gap between adjacent keys in a row |
| `keyboard_vertical_gap` | `8dp` | vertical gap of the keyboard | gap between rows |
| `recognizer_size` | `96dp` | size of the recognizer (voice) view | voice overlay, omit if no mic |
| `selector_size` | `24dp` | size of the focus selector | the moving focus highlight is 24dp square |
| `keyboard_top_spacing` | `6dp` | top gap when suggestion is enabled | padding above the key grid (paddingTop on @id/keyboard) |
| `keyboard_bottom_spacing` | `28dp` | gap below keyboard | paddingBottom on root_ime |
| `action_button_size` | `64dp` | action button size | used by recognizer mic button, not the enter button |
| `enter_key_height` | `32dp` | | height of the Enter/action button at right of grid |
| `enter_key_font_size` | `18sp` | | Enter/action button text size |
| `enter_key_padding_horizontal` | `16dp` | | left+right padding of Enter button |
| `mode_change_key_font_size` | `7sp` | | small font for mode-change function key label |
| `candidate_font_size` | `18sp` | candidate (suggestion) text size | suggestion chip text size |
| `candidate_padding_horizontal` | `16dp` | | left+right padding inside each suggestion chip |
| `candidate_margin_horizontal` | `4dp` | | left+right margin between suggestion chips |
| `candidate_height` | `28dp` | | height of the suggestion chip button (Button @id/text) |
| `candidate_scroll_view_horz_spacing` | `56dip` | | left+right margin of the whole suggestions scroll strip |
| `key_font_size` | `18sp` | keyboard key font size | main glyph size on keys |
| `function_key_mode_change_font_size` | `16sp` | font size of mode-change function keys | |
| `resize_move_distance` | `12dp` | min movement distance to change cursor size | trackpad/free-movement only, not needed for D-pad |

Key derived geometry for the native renderer:
- A full QWERTY row in the main keyboard is 11 columns (see section 2). Row pixel width with gaps = 11*key_width + 10*horizontal_gap = 11*28 + 10*12 = 308 + 120 = 428dp. (Last row uses fewer, wider keys; the space key is 268dp.)
- The space key width of 268dp is explicitly documented as 7 regular keys plus 6 horizontal margins.
- Vertical stride per row = key_height + vertical_gap = 28 + 8 = 36dp.

### 1.2 Colors
From `/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/res/values/color.xml`:

| Resource name | ARGB hex | Role |
| --- | --- | --- |
| `search_mic_levels_guideline` | `#FFCCCCCC` | voice mic level guideline (omit, no voice) |
| `search_mic_background` | `#FFFFFFFF` | voice mic background (omit) |
| `keyboard_background` | `#FF384248` | the whole IME background panel (root_ime background). Dark blue-grey. |
| `key_text_default` | `#FFEEEEEE` | default key glyph/label color, near-white |
| `ime_selector_focus_color` | `#66EEEEEE` | focus selector tint, 40% alpha white (0x66 = 102/255 ~= 40%) |
| `ime_selector_color` | `#26EEEEEE` | non-focus selector tint, ~15% alpha white (0x26 = 38/255 ~= 15%) |
| `candidate_background` | `#FF263238` | suggestions strip background bar. Darker blue-grey than keyboard. |
| `candidate_font_color` | `#FF30C6B4` | suggestion chip text color. Teal/cyan. |
| `enter_key_font_color` | `#FF30C6B4` | Enter/action button text color. Same teal as suggestions. |

Notes:
- There is no per-state (normal/pressed/focused) key background color list in `color.xml`. The pressed/focused visuals are produced by (a) the `key_selector` nine-patch drawable (`res/drawable-*/key_selector.9.png`, a stretchable highlight) moved/scaled over the focused key, tinted by `ime_selector_focus_color` / `ime_selector_color`, and (b) the scale animations in `config.xml`/`integers.xml` (below). Key text color is a single value (`key_text_default` = `#FFEEEEEE`); there is no separate pressed text color.
- "Selection / focus color" for the native port: focused = `#66EEEEEE` (40% white) over the dark `#FF384248` background, idle/non-focused selector = `#26EEEEEE` (15% white). Suggestions text and the action button text are the teal `#FF30C6B4`.
- Special keys (space, shift, mode-change, delete, left/right) do NOT have distinct background colors; they share the keyboard background and selector. They are distinguished by icons (drawables `ic_ime_space`, `ic_ime_shift_off/on`, `ic_ime_symbols`, `ic_ime_delete`, `ic_ime_left_arrow`, `ic_ime_right_arrow`) rather than color.

### 1.3 Fractional and animation constants
From `/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/res/values/config.xml`:

| Name | Value | Role |
| --- | --- | --- |
| `clicked_scale` (fraction) | `88%` | the focused key/selector scales DOWN to 0.88 on click |
| `focused_scale` (fraction) | `120%` | selector overshoot scale on focus (1.20) |
| `alpha_in` (fraction) | `100%` | active keyboard / voice alpha |
| `alpha_out` (fraction) | `0%` | inactive keyboard / voice alpha |

From `/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/res/values/integers.xml`:

| Name | Value | Role |
| --- | --- | --- |
| `clicked_anim_duration` | `100` (ms) | duration of a key-click animation |
| `unfocused_anim_delay` | `30` (ms) | delay before reversing the click animation |
| `voice_anim_duration` | `300` (ms) | voice UI in/out (omit) |
| `inactive_mini_kb_alpha` | `40` (0-255) | alpha of inactive keys when the accent/mini popup keyboard is open (~16%) |

Native port guidance: on D-pad center press of a key, scale the focus selector to 0.88 over 100ms then reverse after 30ms. On focus move, the selector overshoots to 1.20 then settles. When an accent popup is open, dim the underlying keyboard keys to alpha 40/255.

---

## 2. Overall Input-Area Layout Structure

Two layouts compose the input area. The root is `root_leanback.xml`, which `<include>`s `input_leanback.xml` for the actual keyboard grid.

### 2.1 root_leanback.xml (the outer panel)
`/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/res/layout/root_leanback.xml`

- Root is a `RelativeLayout` id `@+id/root_ime`:
  - `layout_width=match_parent`, `layout_height=wrap_content`
  - `layout_gravity=bottom|center_horizontal`, `gravity=bottom|center_horizontal`
  - `background=@color/keyboard_background` (`#FF384248`)
  - `paddingBottom=@dimen/keyboard_bottom_spacing` (28dp)
  - `clipChildren=false`, `clipToPadding=false`, `focusable=true`
  - The whole IME docks to the bottom of the screen, centered horizontally. It is NOT fullscreen (`onEvaluateFullscreenMode()` returns false, see section 4).

Children top-to-bottom:

1. `@+id/candidate_background` (a `View`): the darker bar (`#FF263238`) drawn behind the suggestions strip. It is pinned `alignParentTop`, `alignParentLeft`, `alignParentRight`, and `alignBottom=@+id/suggestions_container`, so it exactly covers the suggestions row width-wise (full panel width) and height.

2. `@+id/suggestions_container` (a `HorizontalScrollView`): the suggestions strip.
   - `layout_height=@dimen/key_height` (28dp)
   - `alignParentTop`, `centerHorizontal`
   - `layout_marginLeft` and `layout_marginRight` = `@dimen/candidate_scroll_view_horz_spacing` (56dip each side)
   - `scrollbars=none`, `fillViewport=false`, `gravity=center`
   - Contains `@+id/suggestions` (a horizontal `LinearLayout`, `gravity=center`, `showDividers=middle`, `divider=@null`). The individual suggestion chips (`candidate.xml`) are added/removed here at runtime.

3. `@+id/keyboard` (a `LinearLayout`): the key grid container.
   - `layout_width=match_parent`, `layout_below=@+id/suggestions_container`, `centerHorizontal`
   - `paddingTop=@dimen/keyboard_top_spacing` (6dp)
   - `gravity=center`
   - `<include layout="@layout/input_leanback" />` provides the actual keyboard.
   - IMPORTANT reflow rule: when suggestions are disabled (password/number/no-suggestions, see section 5), `onStartInputView()` in `LeanbackKeyboardContainer.java` (lines 741-752) removes the `ALIGN_PARENT_TOP` rule vs adds it and toggles the suggestions container + background to `GONE`/`VISIBLE`. So with suggestions off, the keyboard grid moves up to the top of the panel and the suggestion strip + its background disappear entirely; the panel shrinks vertically by `key_height` (28dp). This is the single most important responsive behavior to replicate.

4. `<include layout="@layout/selector"/>`: the floating focus highlight (see section 2.4). It is a sibling of the keyboard so it can be moved/animated over any key or suggestion.

Vertical stacking summary (top to bottom) when suggestions ON:
```
[ candidate_background bar  (#FF263238) ]
[ suggestions strip (HorizontalScrollView, 28dp tall, 56dp side margins) ]
[ keyboard grid container (paddingTop 6dp) ]
[ paddingBottom 28dp ]
```
When suggestions OFF: the top two rows collapse and the grid sits at the panel top.

There is NO text/edit field and NO "abandon" field inside this IME layout. The IME never renders the text being typed; that is owned by the host app's EditText. For the Nano native port, which types into a std::string with no host EditText, you MUST add your own text-preview field above the keyboard. This is a deliberate addition, not a 1:1 element, because the original IME relies on the application drawing the field.

### 2.2 input_leanback.xml (the keyboard grid + action button + voice)
`/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/res/layout/input_leanback.xml`

- Root `RelativeLayout`, `width=match_parent`, `height=wrap_content`, `gravity=bottom`, `clipChildren=false`, `focusable=false`.

Children:

1. `@+id/keyboard` (`LinearLayout`, horizontal, `layout_centerHorizontal=true`): wraps the grid view, centered.
   - Contains `com.android.inputmethod.leanback.LeanbackKeyboardView` id `@+id/main_keyboard` with custom attributes from the `leanbackime` namespace:
     - `leanbackime:columnCount="11"`
     - `leanbackime:rowCount="5"`
   - So the main keyboard is an 11 column by 5 row grid. (Confirmed by `qwerty_us.xml`: 4 character rows of 11 keys + 1 bottom function row.)

2. A `FrameLayout` aligned top/bottom to `@id/keyboard`, centered horizontally, holding:
   - `com.android.inputmethod.leanback.voice.RecognizerView` id `@+id/voice`, size `recognizer_size` (96dp), `visibility=invisible`. (Voice; out of scope.)

3. A `FrameLayout` aligned top/bottom to `@id/keyboard`, `alignParentRight`, `layout_toRightOf=@id/keyboard`, holding:
   - `@+id/enter` (a `Button`), the action/Enter key to the RIGHT of the grid:
     - `layout_height=@dimen/enter_key_height` (32dp)
     - `paddingLeft/Right=@dimen/enter_key_padding_horizontal` (16dp)
     - `text=@string/label_next_key` (default "Next"; replaced at runtime per IME action, see section 4)
     - `textColor=@color/enter_key_font_color` (`#FF30C6B4` teal)
     - `textSize=@dimen/enter_key_font_size` (18sp)
     - `fontFamily=sans-serif-regular`, `background=@null` (no chrome)

Layout note: the Enter/action button sits to the RIGHT of the grid, vertically centered with the grid, not below it. The voice mic sits centered over the grid (hidden unless voice active). So horizontally: [ key grid ] [ Enter button ]. There are no other action buttons.

### 2.3 The main grid contents
`/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/res/xml/qwerty_us.xml` (the default US layout; `keyHeight=@dimen/key_height`=28dp, `keyWidth=@dimen/key_width`=28dp, `verticalGap=8dp`, `horizontalGap=12dp`):

- Row 1 (`rowEdgeFlags=top`): `1 2 3 4 5 6 7 8 9 0` then Delete (code `-5`, icon `ic_ime_delete`). Digits 1-0 are codes 49-57,48; each has a `popupKeyboard` (shift_1..shift_0) for symbols.
- Row 2: `q w e r t y u i o p` then `@` (code 64). Letters have accent/shift popups.
- Row 3: `a s d f g h j k l _ &` (codes a..l, `_`=95, `&`=38).
- Row 4: `z x c v b n m , . - ?` (codes z..m, `,`=44, `.`=46, `-`=45, `?`=63).
- Row 5 (`rowEdgeFlags=bottom`): Mode-change (code `-2`, icon `ic_ime_symbols`), Shift (code `-1`, icon `ic_ime_shift_off`), Space (code 32, icon `ic_ime_space`, width `space_key_width`=268dp), Left cursor (code `-3`, icon `ic_ime_left_arrow`), Right cursor (code `-4`, icon `ic_ime_right_arrow`).

Special key codes (from `LeanbackKeyboardView.java`):
- `ASCII_SPACE = 32`, `ASCII_PERIOD = 46`
- `KEYCODE_SHIFT = -1`, `KEYCODE_SYM_TOGGLE = -2`, `KEYCODE_LEFT = -3`, `KEYCODE_RIGHT = -4`, `KEYCODE_DELETE = -5`, `KEYCODE_CAPS_LOCK = -6`, `KEYCODE_VOICE = -7`, `KEYCODE_DISMISS_MINI_KEYBOARD = -8`
- Shift states: `SHIFT_OFF = 0`, `SHIFT_ON = 1`, `SHIFT_LOCKED = 2`.

(Keyboard layout/key geometry detail is the subject of a separate analysis doc; included here only for the structure of region 3.)

### 2.4 selector.xml (the focus highlight)
`/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/res/layout/selector.xml`
- `FrameLayout` id `@+id/selector`, `width/height=@dimen/selector_size` (24dp), holding an `ImageView` id `@+id/key_selector` with `background=@drawable/key_selector` (the nine-patch highlight), `scaleType=centerInside`. This is the single moving highlight; it is repositioned/scaled to the focused key or suggestion. Replicate as a single quad in GL tinted by `ime_selector_focus_color`.

### 2.5 candidate.xml (one suggestion chip)
`/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/res/layout/candidate.xml`
- `RelativeLayout`, `layout_marginLeft/Right=@dimen/candidate_margin_horizontal` (4dp), `paddingLeft/Right=@dimen/candidate_padding_horizontal` (16dp), `focusable=true`.
- Child `Button` id `@+id/text`: `height=@dimen/candidate_height` (28dp), `textSize=@dimen/candidate_font_size` (18sp), `textColor=@color/candidate_font_color` (`#FF30C6B4`), `background=transparent`, `singleLine=true`, `ellipsize=none`, `textAllCaps=false`, `gravity=center`, `fontFamily=sans-serif-condensed`.

### 2.6 recognizer_view.xml (voice; out of scope)
`/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/res/layout/recognizer_view.xml`: a 96dp square `<merge>` with a `BitmapSoundLevelView` (mic level visualizer, `minLevelRadius=28dip`) and a 64dp mic `ImageView` (`ic_voice_available`). Entirely voice-related; omit in the Nano port (voice is disabled, see `VOICE_SUPPORTED` gate in section 4).

### 2.7 Reflow / aspect-ratio guidance for the native overlay
- The IME is inherently bottom-docked, horizontally centered, `wrap_content` height. It does not stretch to full width except for the background bars. The grid is a fixed-dp 11-col layout centered with the Enter button to its right.
- For different aspect ratios / portrait, the original simply keeps the fixed-dp grid centered at the bottom. There is no portrait-specific layout (no `layout-port`, no `layout-land` variants exist; only the single `res/layout/` set). So the native port can keep a fixed logical grid and center it; on narrow/portrait screens scale the whole panel uniformly to fit width.
- The one dynamic reflow is suggestions on/off collapsing the top strip (section 2.1 item 3). Implement that toggle.

---

## 3. Suggestions Strip

### 3.1 How candidates are generated
`/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/src/com/android/inputmethod/leanback/LeanbackSuggestionsFactory.java`

Despite the class comment ("get suggestions from LatinIme's suggestion engine based on the current composing word"), the actual implementation does NOT run any predictive/dictionary engine. There are three modes (`createSuggestions()`, lines 109-119):
- `MODE_DEFAULT` (0): produces NO suggestions. `createSuggestions()` only clears and returns; the list stays empty.
- `MODE_DOMAIN` (1): populates the list from the `common_domains` string array (NOT from a dictionary file). Used for email-address fields.
- `MODE_AUTO_COMPLETE` (2): set when `TYPE_TEXT_FLAG_AUTO_COMPLETE` is present, but `createSuggestions()` produces nothing extra for it; suggestions in this mode come purely from app-provided completions via `onDisplayCompletions`.

Mode selection in `onStartInput(EditorInfo)` (lines 54-71):
- Default = `MODE_DEFAULT`.
- If `inputType & TYPE_TEXT_FLAG_AUTO_COMPLETE` -> `MODE_AUTO_COMPLETE`.
- If class is `TYPE_CLASS_TEXT` and variation is `TYPE_TEXT_VARIATION_EMAIL_ADDRESS` or `TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS` -> `MODE_DOMAIN`.

`common_domains` array, from `/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/res/values/strings.xml` (lines 41-45):
```
"@gmail.com"
"@yahoo.com"
"@hotmail.com"
```
(Quoted with leading `@` and the surrounding double quotes are XML string literal markers; the effective values are `@gmail.com`, `@yahoo.com`, `@hotmail.com`.)

About `res/raw/domain_en.dict`:
- File `/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/res/raw/domain_en.dict` exists (117 bytes, a small binary dictionary header followed by: `aol.com comcast.net gmail.com hotmail.com msn.com outlook.com verizon.net yahoo.com`).
- It is NOT referenced anywhere in `LeanbackSuggestionsFactory.java` or elsewhere in `src/` (grep for `domain_en` / `R.raw` returns nothing in the service path). It is vestigial. The domain suggestions actually shown come from the `common_domains` string array, which only lists gmail/yahoo/hotmail. So the dict file is dead data for this code path; do not port it.

App-provided completions: `onDisplayCompletions(CompletionInfo[])` (lines 78-95) rebuilds suggestions (calls `createSuggestions()` first) and inserts the app's completion texts at the FRONT of the list, capped at `mNumSuggestions` (= `MAX_SUGGESTIONS = 10`, from `LeanbackImeService.java:53`). Empty completion text terminates insertion.

`shouldSuggestionsAmend()` returns true only in `MODE_DOMAIN` (line 97-99); this drives the "@-anchored" commit behavior in the service (section 4).

### 3.2 How suggestions are shown / focused / committed
- Display: the service calls `mKeyboardController.updateSuggestions(mSuggestionsFactory.getSuggestions())`, which forwards to the container, which inflates one `candidate.xml` chip per string into the `@+id/suggestions` LinearLayout. Visibility of the whole strip is governed by `mSuggestionsEnabled` (section 2.1, item 3).
- Focus: D-pad UP from the top key row moves focus into the suggestions strip. In `LeanbackKeyboardContainer.getBestFocus` (lines 972-979), when `y < keyboardTop && count > 0 && mSuggestionsEnabled`, it requests focus on the matching suggestion child. Focus type is `KeyFocus.TYPE_SUGGESTION`.
- Commit: in `LeanbackKeyboardController.handleKeyUpEvent` (lines 548-557), on `KEYCODE_ENTER` (which `getSimplifiedKey` maps from DPAD_CENTER / NUMPAD_ENTER / BUTTON_A), if the current focus type is `TYPE_SUGGESTION`, it fires `onEntry(ENTRY_TYPE_SUGGESTION, 0, suggestionText)` and then `ENTRY_TYPE_DISMISS`. (Note: pressing center while focused on a suggestion both commits the suggestion AND dismisses; see section 4.)
- Auto-clear: `LeanbackImeService.clearSuggestionsDelayed()` posts `MSG_SUGGESTIONS_CLEAR` with `SUGGESTIONS_CLEAR_DELAY = 1000` ms; after typing/backspace, suggestions clear after 1s unless `shouldSuggestionsAmend()` (domain mode) keeps them.

### 3.3 Commit semantics (from service `handleTextEntry`, lines 308-325)
For `ENTRY_TYPE_SUGGESTION` / `ENTRY_TYPE_VOICE`:
- If NOT domain mode (`!shouldSuggestionsAmend()`): delete all text before+after cursor and commit the suggestion as the entire field value.
- If domain mode: find the `@` location (or end of text), set cursor there, delete everything after, then commit the domain string. This appends the chosen domain after the local-part (e.g., user types `bob`, picks `@gmail.com`, result `bob@gmail.com`).
- Sets `mEnterSpaceBeforeCommitting = true`, then FALLS THROUGH to `ENTRY_TYPE_ACTION` which calls `sendDefaultEditorAction(false)`. So committing a suggestion also fires the editor action (e.g., submits the field). In the native port with no InputConnection, "commit suggestion" = replace buffer (or append domain) and trigger your Done/submit callback.

### 3.4 In-scope decision for the native port
- The full suggestion ENGINE is trivial to port because there is none: only a fixed 3-entry domain list and app-provided completions. In the Nano standalone binary there is no host app supplying `CompletionInfo`, so `MODE_AUTO_COMPLETE` and `onDisplayCompletions` are inert.
- Recommendation: port ONLY the domain auto-complete (`@gmail.com` / `@yahoo.com` / `@hotmail.com`, optionally extended from the `domain_en.dict` list: aol.com, comcast.net, gmail.com, hotmail.com, msn.com, outlook.com, verizon.net, yahoo.com) for email fields, gated on whether the native field is flagged email. Everything else produces no suggestions, so for general text the strip is empty and effectively absent. Difficulty: LOW. The strip UI (one teal chip row, D-pad-up focus, center to commit) is the only real work, and it can be optional.
- If you do NOT need email-domain completion at all, you can omit the entire suggestions strip and always run in the "suggestions off" reflow (grid at panel top). This matches password/number behavior and simplifies the layout.

---

## 4. Service Lifecycle and Behavior

`/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/src/com/android/inputmethod/leanback/service/LeanbackImeService.java`
Class extends `InputMethodService`. Key constants: `MAX_SUGGESTIONS = 10` (line 53); `MSG_SUGGESTIONS_CLEAR = 123`, `SUGGESTIONS_CLEAR_DELAY = 1000` (lines 55-56); broadcast actions `IME_OPEN = "com.android.inputmethod.leanback.action.IME_OPEN"`, `IME_CLOSE = "...IME_CLOSE"` (lines 58-59).

### 4.1 Lifecycle methods
- Constructor (lines 91-95): tries `enableHardwareAcceleration()`.
- `onInitializeInterface()` (106-111): creates `LeanbackKeyboardController`, `mSuggestionsFactory = new LeanbackSuggestionsFactory(this, MAX_SUGGESTIONS)`, `mEnterSpaceBeforeCommitting = false`.
- `onCreateInputView()` (113-118): gets the controller view, `requestFocus()`.
- `onStartInput(EditorInfo attribute, boolean restarting)` (157-163): resets `mEnterSpaceBeforeCommitting`, calls `mSuggestionsFactory.onStartInput(attribute)` (sets suggestion mode, section 3.1) and `mKeyboardController.onStartInput(attribute)` (which calls container `setImeOptions`, section 4.2). Comment notes this does NOT fire when reopening the keyboard on the same field.
- `onStartInputView(EditorInfo info, boolean restarting)` (124-143): calls controller `onStartInputView()` (applies layout: suggestions visibility, keyboard, action-button text, shift state, section 2.1/4.2), broadcasts `IME_OPEN`. If suggestions enabled, builds suggestions and re-posts current editor text (delete surrounding + re-commit) to trigger completions.
- `onFinishInputView(boolean)` (146-151): broadcasts `IME_CLOSE`, clears suggestions.
- `onShowInputRequested(...)` (169-172): always returns true (always show on focus).
- `onEvaluateInputViewShown()` (178-181): always true (show even with a hardware keyboard connected).
- `onEvaluateFullscreenMode()` (183-188): returns false (TV has display area; never fullscreen-extract). This is why the IME is a bottom-docked panel, not a fullscreen editor.
- `onHideIme()` (341-343): `requestHideSelf(0)`.

### 4.2 Input type -> keyboard variant + suggestion suppression
Driven by `LeanbackKeyboardContainer.setImeOptions(Resources, EditorInfo)`:
`/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/src/com/android/inputmethod/leanback/LeanbackKeyboardContainer.java` lines 800-902.

Defaults at entry (lines 801-806): `mSuggestionsEnabled=true`, `mAutoEnterSpaceEnabled=true`, `mVoiceEnabled=true`, `mInitialMainKeyboard=mAbcKeyboard`, `mEscapeNorthEnabled=false`, `mVoiceKeyDismissesEnabled=false`.

By input type class (`getInputTypeClass` = `inputType & TYPE_MASK_CLASS`):
- `TYPE_CLASS_NUMBER`, `TYPE_CLASS_DATETIME`, `TYPE_CLASS_PHONE` (810-817): `mSuggestionsEnabled=false`, `mVoiceEnabled=false`. NOTE the `// TODO use number keyboard` comment: it still sets `mInitialMainKeyboard = mAbcKeyboard`. So there is NO dedicated number keyboard in use; numeric fields get the same ABC/QWERTY grid with suggestions off. (A `res/xml/number.xml` exists in resources but is not wired in by this code path.)
- `TYPE_CLASS_TEXT` (818-838), by variation (`getInputTypeVariation`):
  - `PASSWORD`, `WEB_PASSWORD`, `VISIBLE_PASSWORD`, `PERSON_NAME` (820-827): `mSuggestionsEnabled=false`, `mVoiceEnabled=false`, ABC keyboard. (PERSON_NAME is grouped with passwords for suggestion suppression.)
  - `EMAIL_ADDRESS`, `URI`, `WEB_EDIT_TEXT`, `WEB_EMAIL_ADDRESS` (828-836): `mSuggestionsEnabled=true`, `mAutoEnterSpaceEnabled=false`, `mVoiceEnabled=false`, ABC keyboard. (Email enables the domain suggestions; auto-enter-space disabled so it doesn't insert spaces in addresses/URLs.)
- Any other class: defaults (suggestions on, ABC keyboard).

Post-adjustments:
- If `mSuggestionsEnabled`, it is AND-ed with `(inputType & TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0` (841-844). So `TYPE_TEXT_FLAG_NO_SUGGESTIONS` always forces suggestions off.
- `mAutoEnterSpaceEnabled = mSuggestionsEnabled && mAutoEnterSpaceEnabled` (845-847).
- Capitalization flags (848-854): `mCapSentences` from `TYPE_TEXT_FLAG_CAP_SENTENCES`; `mCapWords` from `TYPE_TEXT_FLAG_CAP_WORDS` OR variation `PERSON_NAME`; `mCapCharacters` from `TYPE_TEXT_FLAG_CAP_CHARACTERS`. These set the initial shift state in `onStartInputView` (lines 769-775): caps-chars -> `SHIFT_LOCKED`, caps-sentences/words -> `SHIFT_ON`, else `SHIFT_OFF`.
- `privateImeOptions` (856-867): if it contains the escape-north or voice-dismiss private option strings, enable `mEscapeNorthEnabled` / `mVoiceKeyDismissesEnabled`. (TV-specific; escape-north lets D-pad up exit the IME upward.)

So the ONLY keyboard variant actually selected is the ABC/QWERTY one (`mAbcKeyboard`, switchable to `mSymKeyboard` via the mode-change key, lines 1184-1186). Input type only toggles suggestions, voice, auto-space, and capitalization, not a different grid. For the native port: a single QWERTY grid plus a symbols grid; input-type flags only gate the suggestion strip and initial shift/caps state.

### 4.3 The action key (Done/Go/Search/Send/Next)
Label resolution in `setImeOptions` (877-897):
- If `attribute.actionLabel` is non-empty, use it verbatim (app-provided custom label).
- Else by `getImeAction` (`imeOptions & (IME_MASK_ACTION | IME_FLAG_NO_ENTER_ACTION)`):
  - `IME_ACTION_GO` -> `label_go_key` = "Go"
  - `IME_ACTION_NEXT` -> `label_next_key` = "Next"
  - `IME_ACTION_SEARCH` -> `label_search_key` = "Search"
  - `IME_ACTION_SEND` -> `label_send_key` = "Send"
  - default (includes Done/None/unspecified) -> `label_done_key` = "Done"
  Strings from `/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/res/values/strings.xml` lines 30-38.
- The label is applied to `mActionButtonView` in `onStartInputView` (760-767).

Action key behavior: pressing the Enter/action button (or long-pressing center while not on a suggestion -> the controller routes `ENTRY_TYPE_ACTION`) calls `sendDefaultEditorAction(false)` (service line 323), which performs the editor's configured action (Go/Search/Send/Next/Done). For the native port with a std::string, the action button should invoke your submit/confirm callback (and for Next, advance to the next field if you have a form). Default behavior = "commit/confirm and dismiss".

### 4.4 Entry types dispatched to the service (`InputListener`)
`handleTextEntry(int type, int keyCode, CharSequence c)` (lines 262-339) handles, where these constants come from `LeanbackKeyboardController.InputListener`:
- `ENTRY_TYPE_BACKSPACE`: `ic.deleteSurroundingText(1, 0)`; resets auto-space; schedules suggestion clear. Native: pop last char from buffer.
- `ENTRY_TYPE_LEFT` / `ENTRY_TYPE_RIGHT`: move cursor one position via `setSelection`. Native: move your buffer cursor index.
- `ENTRY_TYPE_STRING`: optionally inserts a space first if `mEnterSpaceBeforeCommitting && enableAutoEnterSpace()` and the new key is a letter; commits the char; if the char is `ASCII_PERIOD` (46) sets `mEnterSpaceBeforeCommitting=true` (so the next letter auto-spaces after a period). Native: insert char at cursor; replicate the period-then-space behavior only if you want auto-space.
- `ENTRY_TYPE_SUGGESTION` / `ENTRY_TYPE_VOICE`: section 3.3, then falls through to fire the editor action.
- `ENTRY_TYPE_ACTION`: `sendDefaultEditorAction(false)`.
- `ENTRY_TYPE_DISMISS` (value 7): `ic.performEditorAction(EditorInfo.IME_ACTION_NONE)` -> closes IME without submitting. Native: close overlay.
- `ENTRY_TYPE_VOICE_DISMISS` (value 8): `ic.performEditorAction(EditorInfo.IME_ACTION_GO)`. Voice-only; ignore.

### 4.5 Escape / Back handling
From `/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/src/com/android/inputmethod/leanback/LeanbackKeyboardController.java`:
- `getSimplifiedKey` (589-599): maps `BUTTON_A` / `NUMPAD_ENTER` / `DPAD_CENTER` -> `DPAD_CENTER` (the "select/commit" key), and maps `BUTTON_B` -> `KEYCODE_BACK`. So on a gamepad, A = select, B = back.
- BACK is NEVER trapped. In both `handleKeyDownEvent` (421-424) and `handleKeyUpEvent` (512-514): if `keyCode == KEYCODE_BACK`, cancel any voice recording and `return false` (let the system handle it). The system then dismisses the IME. Native: B / Back closes the overlay (cancel, no commit).
- When voice is visible, key events are swallowed (DPAD_RIGHT / DPAD_CENTER cancel voice recording) (426-432, 516-519). Voice is disabled in this build (see `VOICE_SUPPORTED` gate, line 899-901: if `!VOICE_SUPPORTED` then `mVoiceEnabled=false`), so this path is effectively dead.
- The center/select key (`KEYCODE_ENTER` after simplification) on a suggestion commits it then dismisses (548-557). On a normal key it commits that key.
- "Escape north" (`mEscapeNorthEnabled`) is a private-IME-option behavior allowing D-pad-up to exit the IME upward; only enabled if the app sets the private option. Optional for native.

Gamepad mapping summary (from `handleKeyDownEvent`/`handleKeyUpEvent`):
- DPAD up/down/left/right: move focus (handled on down for repeat).
- A / DPAD_CENTER / NUMPAD_ENTER: select/commit focused key or suggestion.
- B: Back -> dismiss/cancel.
- X (`KEYCODE_BUTTON_X`): delete (backspace) (line 450).
- Y (`KEYCODE_BUTTON_Y`): space (line 453).
- L1 (`KEYCODE_BUTTON_L1`): cursor left (456).
- R1 (`KEYCODE_BUTTON_R1`): cursor right (459).
- L3 / THUMBL (`KEYCODE_BUTTON_THUMBL`): mode change (symbols toggle) (543).
- R3 / THUMBR (`KEYCODE_BUTTON_THUMBR`): caps lock (546).
- ENTER (after long-press of center on the action key): action (Done/Go/etc.).

This gamepad shortcut set is directly portable to the Nano D-pad/gamepad input layer and is the most concrete reusable behavior in this analysis.

---

## 5. Password / Secure Field Handling and Device-Lock Analysis

### 5.1 What the IME does for secure fields
- The ONLY secure-field behavior in the service/container is suggestion + voice suppression. In `LeanbackKeyboardContainer.setImeOptions` (lines 820-827), the password variations `TYPE_TEXT_VARIATION_PASSWORD`, `TYPE_TEXT_VARIATION_WEB_PASSWORD`, `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD` (grouped with `PERSON_NAME`) set `mSuggestionsEnabled=false` and `mVoiceEnabled=false`, and keep the ABC keyboard. There is NO masking, NO special key behavior, NO different layout for passwords.
- Masking of the typed text (showing dots) is the HOST EditText's job, not the IME's. The IME never renders the field, so it never masks. For the Nano native port (which renders its own preview field), you must decide masking yourself: if the field is flagged password, render the preview as bullet/dot characters. The product can choose to mask or not; the original IME imposes nothing.
- `TYPE_TEXT_FLAG_NO_SUGGESTIONS` independently forces suggestions off (lines 841-844), which is the generic "no autocomplete" signal apps set on sensitive fields.
- Accessibility-only password strings exist in `strings.xml` (lines 82-85): `keyboard_headset_required_to_hear_password` ("Plug in a headset to hear password keys spoken.") and `keyboard_password_character_no_headset` ("Dot."). These are TalkBack announcements for password keys when no headset is present; they are accessibility text, not security enforcement. Irrelevant to the native renderer unless you add TalkBack support.

### 5.2 Device-lock / keyguard / secure-IME behavior to intentionally OMIT
- Exhaustive grep across `src/`, `res/`, and `AndroidManifest.xml` for `keyguard`, `deviceLock`, `lockscreen`, `secure`, `PIN`, `isSecure`, `KeyguardManager` returns NOTHING. There is no device-lock, keyguard, PIN, or secure-IME code anywhere in LeanbackIME.
- There is no "secure flag" handling beyond suggestion/voice suppression. The IME does not check `FLAG_SECURE`, does not interact with the lock screen, and does not gate anything behind device authentication.
- Conclusion for the product owner's "no device lock needed": there is literally nothing device-lock-related to remove. The only thing that LOOKS security-adjacent is (a) suggestion/voice suppression on password fields and (b) TalkBack "Dot." announcements. Neither is a device lock. You can omit both freely. If you want password fields to still suppress suggestions and mask the preview, that is a UI choice in your own preview field, fully independent of any lock mechanism.

### 5.3 Native port recommendation for password fields
- Detect password via the field's input-type flags you pass in (mirror `TYPE_TEXT_VARIATION_PASSWORD` / `WEB_PASSWORD` / `VISIBLE_PASSWORD` and `TYPE_TEXT_FLAG_NO_SUGGESTIONS`).
- When password: disable the suggestions strip (use the suggestions-off reflow, section 2.1 item 3), and optionally render the preview field masked (your decision; visible-password variation conventionally shows plain text).
- Do NOT implement any keyguard/lock/secure-display behavior; none exists to match.

---

## 6. Localization / Default-IME note (im_is_default)
- `/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/res/values/bools.xml`: `im_is_default = true` (this IME is the default for the locale).
- `/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/res/values-ja/bools.xml`: `im_is_default = false` (NOT default for Japanese; Japanese ships a different IME). This only affects Android IME selection at the framework level; it has no bearing on the native renderer's behavior. Ignore for the Nano port.
- `res/xml/method.xml` declares a single subtype: locale `en_US`, mode `keyboard`, `isDefault=@bool/im_is_default`. There is one subtype; no per-locale keyboard switching from the framework subtype mechanism.

---

## 7. Concrete Native-Port Checklist (derived)
1. Panel: bottom-docked, centered, background `#FF384248`, paddingBottom 28dp, paddingTop-of-grid 6dp.
2. Grid: 11 cols x 5 rows; key cell 28x28dp; horizontal gap 12dp; vertical gap 8dp; space key 268dp wide; key text 18sp `#FFEEEEEE`. Symbols grid toggled by mode-change key.
3. Focus selector: 24dp nine-patch-style quad, tint `#66EEEEEE` focused / `#26EEEEEE` idle; click scale 0.88 over 100ms reverse after 30ms; focus overshoot 1.20.
4. Action button to the right of grid: 32dp tall, 16dp h-padding, text 18sp teal `#FF30C6B4`; label = app action label or one of Go/Next/Search/Send/Done.
5. Suggestions strip (optional, email-only): 28dp tall, side margins 56dp, chips 18sp teal with 16dp h-padding + 4dp margins, background bar `#FF263238`; reflow collapses it (and the grid rises) when disabled.
6. Add a self-rendered text preview field (NOT in the original layout) above the grid, since there is no host EditText.
7. Suggestions content: only the domain list for email fields; otherwise none. App-completions path is inert in standalone.
8. Gamepad map: A=select, B=back/dismiss, X=delete, Y=space, L1=cursor-left, R1=cursor-right, L3=symbols toggle, R3=caps-lock; D-pad=move focus; D-pad-up into suggestions.
9. Input-type effects: password/personName/number/datetime/phone disable suggestions (and voice); email/uri/web enable domain suggestions but disable auto-space; NO_SUGGESTIONS flag forces off; cap flags set initial shift.
10. Omit entirely: voice/recognizer UI, device-lock/keyguard/secure-IME (none exists), `domain_en.dict` (vestigial), Japanese default-IME bool.
