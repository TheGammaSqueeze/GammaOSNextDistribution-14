# Leanback IME: Locale to Layout Mapping and Language Handling

Source tree: `/work/GammaOSNextDistribution-A14/packages/inputmethods/LeanbackIME/`

This document describes how the Android TV Leanback on-screen keyboard maps the
device locale to a keyboard layout resource, lists the IME subtypes, explains the
runtime selection path (and the absence of any in-keyboard language switch), and
gives a concrete recommendation for the native C++ reimplementation in the GammaOS
Nano renderer.

All findings are taken directly from the source. No em-dashes or en-dashes are used
anywhere in this report; only ASCII hyphen.

---

## 0. TL;DR for the reimplementation

- Language selection is 100 percent driven by `java.util.Locale.getDefault()` (the
  system/app UI locale). There is NO in-keyboard language switch key, NO globe key,
  and the IME subtype is never consulted for layout selection.
- There are 16 distinct alphabetic ("ABC") layout files and 6 distinct symbol layout
  files (one symbol file, sym_fr.xml, is unused). All are Latin script. Every one is a
  Latin QWERTY / QWERTZ / AZERTY variant.
- There are NO non-Latin keyboard layouts at all. No Arabic, no CJK, no Cyrillic,
  no Thai, no Hebrew, no Greek, no Indic. Any locale that is not explicitly matched
  (including ja, ko, zh, ar, ru, th, he, el, hi, and everything else) falls through
  to the default `qwerty_eu` + `sym_eu` Latin layout.
- The single dispatch function is `LeanbackKeyboardContainer.initKeyboards()` at
  `src/com/android/inputmethod/leanback/LeanbackKeyboardContainer.java:650`. It is a
  linear if/else-if chain over `LeanbackLocales` arrays, evaluated once at construction.

---

## 1. Complete locale to keyboard-layout-resource map

### 1.1 The matcher

The matcher is `isMatch(Locale locale, Locale[] list)` at
`LeanbackKeyboardContainer.java:707-721`:

```java
private boolean isMatch(Locale locale, Locale[] list) {
    for (Locale compare : list) {
        // comparison language is either blank or they match
        if (TextUtils.isEmpty(compare.getLanguage()) ||
                TextUtils.equals(locale.getLanguage(), compare.getLanguage())) {
            // comparison country is either blank or they match
            if (TextUtils.isEmpty(compare.getCountry()) ||
                        TextUtils.equals(locale.getCountry(), compare.getCountry())) {
                return true;
            }
        }
    }
    return false;
}
```

Semantics, per `compare` entry in the list:

- If the entry's language is empty, the language test passes for ANY input language.
- Otherwise the input language must equal the entry's language exactly (case sensitive
  ASCII compare on the two-letter code, e.g. "en", "de", "fr").
- If the entry's country is empty, the country test passes for ANY input country.
- Otherwise the input country must equal the entry's country exactly (e.g. "GB", "ES").

Important consequence: an entry like `new Locale("es", "")` (OTHER_SPANISH) matches
EVERY Spanish locale regardless of country. So the ORDER of the if/else chain matters,
because the first matching group wins. Spain Spanish (`es_ES`) must be tested before
generic Spanish (`es_*`) or it would never be reached. The chain is hand-ordered most
specific first.

### 1.2 The dispatch chain (the authoritative map)

`LeanbackKeyboardContainer.initKeyboards()`, `LeanbackKeyboardContainer.java:650-705`.
Evaluated top to bottom; first match wins; `Locale locale = Locale.getDefault()`.

| Order | Match group (LeanbackLocales) | Locales matched (lang_country) | ABC layout (R.xml.*) | Symbol layout (R.xml.*) |
|-------|-------------------------------|--------------------------------|----------------------|--------------------------|
| 1  | `QWERTY_GB`    | en_GB                                   | `qwerty_en_gb` | `sym_en_gb` |
| 2  | `QWERTY_IN`    | en_IN                                   | `qwerty_en_in` | `sym_en_in` |
| 3  | `QWERTY_ES_EU` | es_ES, gl_ES, eu_ES                     | `qwerty_es_eu` | `sym_eu`    |
| 4  | `QWERTY_ES_US` | es_* (any other Spanish, country blank) | `qwerty_es_us` | `sym_us`    |
| 5  | `QWERTY_AZ`    | az_* (any country)                      | `qwerty_az`    | `sym_eu`    |
| 6  | `QWERTY_CA`    | ca_* (any country)                      | `qwerty_ca`    | `sym_eu`    |
| 7  | `QWERTY_DA`    | da_* (any country)                      | `qwerty_da`    | `sym_eu`    |
| 8  | `QWERTY_ET`    | et_* (any country)                      | `qwerty_et`    | `sym_eu`    |
| 9  | `QWERTY_FI`    | fi_* (any country)                      | `qwerty_fi`    | `sym_eu`    |
| 10 | `QWERTY_NB`    | nb_* (any country)                      | `qwerty_nb`    | `sym_us`    |
| 11 | `QWERTY_SV`    | sv_* (any country)                      | `qwerty_sv`    | `sym_eu`    |
| 12 | `QWERTY_US`    | en_* (any other English), fr_CA         | `qwerty_us`    | `sym_us`    |
| 13 | `QWERTZ_CH`    | de_CH, it_CH                            | `qwertz_ch`    | `sym_eu`    |
| 14 | `QWERTZ`       | de_*, hr_*, cs_*, fr_CH, it_CH, hu_*, sr_*, sl_*, sq_* | `qwertz` | `sym_eu` |
| 15 | `AZERTY`       | fr_* (any other French), nl_BE          | `azerty`       | `sym_azerty` |
| 16 | (else / default) | everything not matched above          | `qwerty_eu`    | `sym_eu`    |

In addition, EVERY branch ends by setting the number keyboard unconditionally
(`LeanbackKeyboardContainer.java:704`): `mNumKeyboard = new Keyboard(mContext, R.xml.number)`.
There is one shared `number` layout for all locales.

### 1.3 Source of the locale arrays

All groups are defined in
`src/com/android/inputmethod/leanback/LeanbackLocales.java`. Exact contents:

QWERTY group:
- `QWERTY_GB` (line 39): `en_GB`
- `QWERTY_IN` (line 43): `en_IN`
- `QWERTY_ES_EU` (lines 49-50): `es_ES`, `gl_ES`, `eu_ES`
- `QWERTY_ES_US` (line 54): `es` (empty country)
- `QWERTY_AZ` (line 58): `az`
- `QWERTY_CA` (line 62): `ca`
- `QWERTY_DA` (line 66): `da`
- `QWERTY_ET` (line 70): `et`
- `QWERTY_FI` (line 74): `fi`
- `QWERTY_NB` (line 78): `nb`
- `QWERTY_SV` (line 82): `sv`
- `QWERTY_US` (line 87): `en` (Locale.ENGLISH, empty country), `fr_CA` (Locale.CANADA_FRENCH)

QWERTZ group:
- `QWERTZ_CH` (line 95): `de_CH`, `it_CH`
- `QWERTZ` (lines 106-107): `de` (empty country), `hr`, `cs`, `fr_CH`, `it_CH`, `hu`, `sr`, `sl`, `sq`

AZERTY group:
- `AZERTY` (line 115): `fr` (Locale.FRENCH, empty country), `nl_BE`

Note on `it_CH`: Swiss Italian appears in BOTH `QWERTZ_CH` and `QWERTZ`. Because
`QWERTZ_CH` is tested first (order 13), Swiss Italian resolves to `qwertz_ch` and the
later `QWERTZ` membership is dead for that exact locale.

Note on `fr_CA`: Canadian French is the only French variant routed to QWERTY (US).
It is in `QWERTY_US` (order 12), which is tested before the `AZERTY` group (order 15).
Generic French `fr` (and `fr_FR`, `fr_BE`, etc.) reaches `AZERTY`. Swiss French `fr_CH`
is captured earlier by `QWERTZ` (order 14) because the QWERTZ list contains the explicit
`fr_CH` entry, so it never reaches AZERTY.

### 1.4 Which language codes hit which ABC layout (flattened)

This is the complete enumeration of every two-letter language the chain recognizes,
with the effective layout. Anything not in this list goes to the default `qwerty_eu`.

| Language code | Country qualifier | ABC layout | Symbol layout |
|---------------|-------------------|------------|----------------|
| en | GB                 | qwerty_en_gb | sym_en_gb |
| en | IN                 | qwerty_en_in | sym_en_in |
| en | any other / blank  | qwerty_us    | sym_us    |
| es | ES                 | qwerty_es_eu | sym_eu    |
| gl | ES                 | qwerty_es_eu | sym_eu    |
| eu | ES                 | qwerty_es_eu | sym_eu    |
| es | any other / blank  | qwerty_es_us | sym_us    |
| az | any                | qwerty_az    | sym_eu    |
| ca | any                | qwerty_ca    | sym_eu    |
| da | any                | qwerty_da    | sym_eu    |
| et | any                | qwerty_et    | sym_eu    |
| fi | any                | qwerty_fi    | sym_eu    |
| nb | any                | qwerty_nb    | sym_us    |
| sv | any                | qwerty_sv    | sym_eu    |
| fr | CA                 | qwerty_us    | sym_us    |
| fr | CH                 | qwertz       | sym_eu    |
| fr | any other / blank  | azerty       | sym_azerty |
| de | CH                 | qwertz_ch    | sym_eu    |
| de | any other / blank  | qwertz       | sym_eu    |
| it | CH                 | qwertz_ch    | sym_eu    |
| hr | any                | qwertz       | sym_eu    |
| cs | any                | qwertz       | sym_eu    |
| hu | any                | qwertz       | sym_eu    |
| sr | any                | qwertz       | sym_eu    |
| sl | any                | qwertz       | sym_eu    |
| sq | any                | qwertz       | sym_eu    |
| nl | BE                 | azerty       | sym_azerty |
| (everything else, including it_* non-CH, nl_* non-BE, pt, pl, ru, ja, ko, zh, ar, th, he, el, hi, tr, vi, id, uk, ...) | any | qwerty_eu | sym_eu |

Caveat on `it` and `nl`: only the Swiss / Belgian country variants are special.
Generic Italian (`it_IT`) and generic Dutch (`nl_NL`) are NOT matched by any group
(the bare `it` / `nl` language is only present with a country qualifier), so they fall
to the default `qwerty_eu`. Generic Serbian `sr` is matched to QWERTZ regardless of
script, even though Serbian is commonly written in Cyrillic; Leanback always gives it
the Latin QWERTZ layout.

### 1.5 The resource files that exist on disk

`res/xml/` directory listing (relevant keyboard files):

ABC layouts (16 files, all referenced by the chain):
`azerty.xml`, `qwerty_az.xml`, `qwerty_ca.xml`, `qwerty_da.xml`, `qwerty_en_gb.xml`,
`qwerty_en_in.xml`, `qwerty_es_eu.xml`, `qwerty_es_us.xml`, `qwerty_et.xml`,
`qwerty_eu.xml`, `qwerty_fi.xml`, `qwerty_nb.xml`, `qwerty_sv.xml`, `qwerty_us.xml`,
`qwertz.xml`, `qwertz_ch.xml`. The chain references all 16 distinct ABC resources.
`sym_fr.xml` below is a separate SYMBOL file that is unused.

Symbol layouts (6 files): `sym_us.xml`, `sym_eu.xml`, `sym_en_gb.xml`,
`sym_en_in.xml`, `sym_azerty.xml`, `sym_fr.xml`.

Number layout (1 file): `number.xml`.

Popup / accent "mini keyboards" (long-press popups), used by the ABC layouts via
`android:popupKeyboard="@xml/..."`:
`accent_a.xml accent_c.xml accent_d.xml accent_e.xml accent_g.xml accent_i.xml
accent_k.xml accent_l.xml accent_n.xml accent_o.xml accent_s.xml accent_t.xml
accent_u.xml accent_y.xml accent_z.xml` plus the digit/symbol popups
`shift_0.xml ... shift_9.xml`, `shift_4_en_gb.xml`, `shift_4_en_in.xml`,
`shift_4_eu.xml`, and letter-symbol popups
`shift_aa shift_ae shift_ao shift_b shift_cc shift_f shift_h shift_j shift_m
shift_n_es shift_nn_es shift_oo shift_ox shift_p shift_q shift_r shift_uu
shift_v shift_w shift_x shift_y`. Every ABC layout file uses these popups (confirmed:
all 16 qwerty/azerty/qwertz files contain `popupKeyboard` references).

DEAD RESOURCE: `sym_fr.xml` exists on disk but is never referenced by any source
file. A grep for `sym_fr` across `src/` and `res/` returns only
`LeanbackKeyboardContainer.java`, and inspection of that file shows it references
`sym_azerty`, `sym_us`, `sym_eu`, `sym_en_gb`, `sym_en_in` but NOT `sym_fr`. French
(AZERTY) uses `sym_azerty`, which is byte-for-byte equivalent in label content to
`sym_fr` (both put euro on the 4th symbol key and a bullet where US has apostrophe).
Do not port `sym_fr`; it is unreachable.

---

## 2. IME subtypes from method.xml

File: `res/xml/method.xml`. Complete content of the subtype declaration:

```xml
<input-method xmlns:android="http://schemas.android.com/apk/res/android"
    android:isDefault="@bool/im_is_default">
    <subtype android:label="@string/subtype_generic"
            android:imeSubtypeLocale="en_US"
            android:imeSubtypeMode="keyboard"
    />
</input-method>
```

There is exactly ONE subtype:

- `android:label="@string/subtype_generic"` (a generic display label, not a language).
- `android:imeSubtypeLocale="en_US"` (declares the subtype's nominal locale as US English).
- `android:imeSubtypeMode="keyboard"` (it is a soft keyboard subtype, not a voice subtype).

The `<input-method>` element is marked `android:isDefault="@bool/im_is_default"`, i.e.
whether Leanback IME is the system default IME is controlled by a build bool resource.

Implications:

- Leanback exposes a SINGLE generic subtype. There is no per-language subtype list,
  so the platform's subtype switcher and the language-switch ("globe") key have nothing
  to cycle through. This is consistent with the code, which never reads the subtype.
- The `imeSubtypeLocale="en_US"` is purely a manifest annotation. It is NOT what
  `initKeyboards()` reads; the code reads `Locale.getDefault()` instead. The subtype
  locale and the actual layout can therefore diverge (e.g. system locale de_DE gives a
  QWERTZ layout while the subtype still nominally claims en_US).

---

## 3. Runtime layout selection and the absence of a language switch

### 3.1 Where the locale is read

`LeanbackKeyboardContainer.initKeyboards()`,
`LeanbackKeyboardContainer.java:650-651`:

```java
private void initKeyboards() {
    Locale locale = Locale.getDefault();
    ...
}
```

`Locale.getDefault()` returns the process default locale, which on Android tracks the
top app locale / system locale (the device UI language). It is the ONLY input to layout
selection.

### 3.2 When it runs

`initKeyboards()` is called exactly once, from the `LeanbackKeyboardContainer`
constructor at `LeanbackKeyboardContainer.java:567` (inside the constructor body, right
after the voice animator setup and before inflating the root view). The container is
constructed from `LeanbackKeyboardController` at
`LeanbackKeyboardController.java:155` / `:158-167`, which is itself constructed from
`LeanbackImeService.onInitializeInterface()` at `LeanbackImeService.java:106-111`:

```java
@Override
public void onInitializeInterface() {
    mKeyboardController = new LeanbackKeyboardController(this, mInputListener);
    ...
}
```

So the keyboard layouts are baked in at IME initialization. The three `Keyboard`
objects (`mAbcKeyboard`, `mSymKeyboard`, `mNumKeyboard`, declared at
`LeanbackKeyboardContainer.java:425-427`) are created once and never rebuilt for a
locale change within the IME's lifetime. A locale change would only take effect after
the IME process / interface is re-initialized.

### 3.3 What the subtype path does (nothing for layout)

`android.view.inputmethod.InputMethodSubtype` is imported at
`LeanbackKeyboardContainer.java:49` but is never instantiated or queried anywhere in
the class. There is no call to `getCurrentInputMethodSubtype()`,
`switchInputMethod()`, `getEnabledInputMethodSubtypeList()`, or any subtype API in the
whole `src/` tree (verified by grep). The import is vestigial.

`onStartInput()` -> `setImeOptions()` (`LeanbackKeyboardContainer.java:727-728`,
`:800` onward) only adjusts behavioral flags from the `EditorInfo` input type
(suggestions on/off, voice on/off, auto-space, and which of the three pre-built
keyboards to show first). It selects between `mAbcKeyboard` and `mNumKeyboard` based on
`EditorInfo` input class/variation (number/phone/datetime, password, email/uri), NOT
based on any locale. It never re-runs `initKeyboards()` and never changes which ABC
layout was chosen.

### 3.4 The ABC <-> SYM toggle (not a language switch)

The only keyboard-switching the user can do at runtime is toggling between the alphabet
keyboard and the symbol keyboard, via the mode-change key. In
`LeanbackKeyboardContainer.onModeChangeClick()` at
`LeanbackKeyboardContainer.java:1183-1186`:

```java
if (mMainKeyboardView.getKeyboard().equals(mSymKeyboard)) {
    mMainKeyboardView.setKeyboard(mAbcKeyboard);
} else {
    mMainKeyboardView.setKeyboard(mSymKeyboard);
}
```

This swaps between the already-selected ABC layout and its paired SYM layout. It is NOT
a language switch; both keyboards belong to the same locale chosen at init.

### 3.5 The special key codes (no language/globe key)

From `LeanbackKeyboardView.java:81-90`, the complete set of special key codes that the
keyboards can carry:

- `ASCII_SPACE = 32`
- `ASCII_PERIOD = 46`
- `KEYCODE_SHIFT = -1`
- `KEYCODE_SYM_TOGGLE = -2`  (the ABC/SYM mode change; maps to `Keyboard.KEYCODE_MODE_CHANGE`)
- `KEYCODE_LEFT = -3`  (move text cursor left)
- `KEYCODE_RIGHT = -4`  (move text cursor right)
- `KEYCODE_DELETE = -5`  (backspace; equals `Keyboard.KEYCODE_DELETE`)
- `KEYCODE_CAPS_LOCK = -6`
- `KEYCODE_VOICE = -7`
- `KEYCODE_DISMISS_MINI_KEYBOARD = -8`

There is NO language-switch keycode, no globe keycode, and no subtype-cycle keycode.
The bottom row of every ABC layout (e.g. `qwerty_us.xml:208-232`) contains exactly:
mode-change (`-2`), shift (`-1`), space (`32`), left (`-3`), right (`-4`). No language
key is present in any layout file.

Gamepad/D-pad mapping in `LeanbackKeyboardController` (for reference):
`handleKeyDownEvent`/`handleKeyUpEvent` at `LeanbackKeyboardController.java:417-563`
map BUTTON_X -> delete, BUTTON_Y -> space, BUTTON_L1 -> cursor left, BUTTON_R1 ->
cursor right, THUMBL -> mode change (ABC/SYM), THUMBR -> caps lock, DPAD/A -> commit
focused key, B -> back. None of these is a language switch either.

### 3.6 Explicit conclusion

Language is PURELY system-locale driven via `Locale.getDefault()`, resolved once at IME
init. There is no in-keyboard language switch key, no globe key, and the IME subtype is
never used to pick a layout. To change the keyboard language the user must change the
device/app UI language and let the IME be re-initialized.

---

## 4. Recommendation for the native Nano reimplementation

### 4.1 Scope of what actually has a distinct layout

Distinct ALPHABETIC layouts (all Latin script). There are three base scripts:

- QWERTY family (top row q w e r t y u i o p): qwerty_us, qwerty_eu (default),
  qwerty_en_gb, qwerty_en_in, qwerty_es_us, qwerty_es_eu, qwerty_az, qwerty_ca,
  qwerty_da, qwerty_et, qwerty_fi, qwerty_nb, qwerty_sv.
- QWERTZ family (z and y swapped: top row q w e r t **z** u i o p, bottom y x c v b n m):
  qwertz, qwertz_ch.
- AZERTY family (top row a z e r t y u i o p, second row q s d f g h j k l m): azerty.

NON-Latin scripts (Arabic, CJK, Cyrillic, Thai, Hebrew, Greek, Indic, etc.):
there are ZERO distinct layouts. Leanback has no such resource files. Every non-matched
locale, including ja / ko / zh / ar / ru / th / he / el / hi, falls to the Latin
`qwerty_eu` + `sym_eu`. So a faithful 1:1 port has only Latin keyboards.

The actual key differences between the Latin ABC layouts (verified by extracting every
`keyLabel` from each file) are entirely in:

1. The position of the 6th top-row letter (y vs z) -> QWERTY vs QWERTZ.
2. The whole AZERTY reflow (a z at top-left, q at start of home row, m on home row,
   w on bottom row, apostrophe key on bottom row instead of comma cluster start).
3. Two or three locale-specific extra letters placed where US has `_` and `&`
   (positions after `l` on the home row and the right edge of the third row):
   - qwerty_es_eu / qwerty_es_us: `ñ` (replaces `_`).
   - qwerty_ca: `ç` (replaces `_`).
   - qwerty_nb (Norwegian): `å` at top-right (replaces `@`), `ø` and `æ` on home row.
   - qwerty_sv (Swedish): `å` top-right, `ö` and `ä` on home row.
   - qwerty_da (Danish): `å` top-right, `æ` and `ø` on home row.
   - qwerty_et (Estonian): `ü` top-right, `ö` and `ä` on home row.
   - qwerty_fi (Finnish): `å` top-right, `ö` and `ä` on home row.
   - qwertz_ch (Swiss): `ü` top-right, `ö` and `ä` on home row.
   - azerty: `m` on home row, apostrophe (`'`) where US has comma start on bottom row.
   - qwerty_us / qwerty_eu / qwerty_en_gb / qwerty_en_in / qwerty_az: plain a-z with
     `_` and `&` fillers (these five are letter-identical to each other; they differ
     only in their paired SYM currency key, see below).

The distinct SYMBOL layouts differ ONLY in the currency / a couple of glyph positions:

- sym_us: 4th key `$`, later block `€ ¥ ¢ £`.
- sym_eu: 4th key `€`, later block `$ ¥ ¢ £` (this is the default).
- sym_en_gb: 4th key `£`, later block `€ ¥ ¢ $`.
- sym_en_in: 4th key `₹` (Indian Rupee), later block `€ $ ¢ £`.
- sym_azerty: 4th key `€`, later block `$ ¥ ¢ £`, and a bullet `•` where US has `"`
  cluster, apostrophe pushed to the `< >` row. (sym_fr is identical and unused.)

### 4.2 Recommended native data model

Port THREE physical letter-layout templates (QWERTY, QWERTZ, AZERTY base grids) and
apply a small per-locale "overlay" of substituted keys, rather than 16 separate hard
files. Concretely:

```
enum class AlphaLayout { QWERTY, QWERTZ, AZERTY };
enum class SymLayout   { SYM_US, SYM_EU, SYM_EN_GB, SYM_EN_IN, SYM_AZERTY };

struct LayoutChoice {
    AlphaLayout alpha;
    SymLayout   sym;
    // up to ~3 codepoint overrides for the locale-specific letters
    // (position index in the grid -> Unicode codepoint), e.g. for nb:
    //   topRightSlot = U+00E5 (a-ring), homeRowSlotA = U+00F8, homeRowSlotB = U+00E6
    std::vector<KeyOverride> overrides;
};
```

The Nano FreeType atlas already covers all the Latin diacritics used here
(`ñ ç å ø æ ö ä ü`) plus the symbol glyphs (`° ¿ ¡ ÷ × € ¥ ¢ £ ₹ •`), so no new glyph
work is required for a 1:1 port.

### 4.3 Recommended mapping function (covers ALL languages)

Input is a single current-UI-language string. Accept either a bare ISO 639-1 code
("en", "fr", "de", "ja", "ar") or a BCP-47 / lang_country tag ("zh-CN", "en-GB",
"pt-BR"). Normalize: lowercase the language, uppercase the region, split on `-` or `_`.

This function reproduces the exact Leanback first-match-wins order. Pseudocode:

```cpp
LayoutChoice pickLayout(std::string lang, std::string region) {
    // lang is lowercased 2-letter, region is uppercased 2-letter ("" if none)

    // 1. en_GB
    if (lang == "en" && region == "GB") return { QWERTY, SYM_EN_GB, {} };          // qwerty_en_gb
    // 2. en_IN
    if (lang == "en" && region == "IN") return { QWERTY, SYM_EN_IN, {} };          // qwerty_en_in
    // 3. es/gl/eu _ES  (Spain Spanish, Galician, Basque)
    if ((lang == "es" || lang == "gl" || lang == "eu") && region == "ES")
        return { QWERTY, SYM_EU, { N_TILDE_OVERRIDE } };                            // qwerty_es_eu
    // 4. any other Spanish
    if (lang == "es") return { QWERTY, SYM_US, { N_TILDE_OVERRIDE } };             // qwerty_es_us
    // 5. Azerbaijani
    if (lang == "az") return { QWERTY, SYM_EU, {} };                               // qwerty_az
    // 6. Catalan
    if (lang == "ca") return { QWERTY, SYM_EU, { C_CEDILLA_OVERRIDE } };           // qwerty_ca
    // 7. Danish
    if (lang == "da") return { QWERTY, SYM_EU, { AA, AE, OSLASH } };               // qwerty_da
    // 8. Estonian
    if (lang == "et") return { QWERTY, SYM_EU, { UUML, OUML, AUML } };             // qwerty_et
    // 9. Finnish
    if (lang == "fi") return { QWERTY, SYM_EU, { AA, OUML, AUML } };               // qwerty_fi
    // 10. Norwegian Bokmal
    if (lang == "nb") return { QWERTY, SYM_US, { AA, OSLASH, AE } };               // qwerty_nb
    // 11. Swedish
    if (lang == "sv") return { QWERTY, SYM_EU, { AA, OUML, AUML } };               // qwerty_sv
    // 12. fr_CA, or any other English
    if ((lang == "fr" && region == "CA") || lang == "en")
        return { QWERTY, SYM_US, {} };                                             // qwerty_us
    // 13. de_CH / it_CH
    if ((lang == "de" || lang == "it") && region == "CH")
        return { QWERTZ, SYM_EU, { UUML, OUML, AUML } };                           // qwertz_ch
    // 14. QWERTZ languages: de, hr, cs, fr_CH, it_CH, hu, sr, sl, sq
    if (lang == "de" || lang == "hr" || lang == "cs" ||
        (lang == "fr" && region == "CH") || (lang == "it" && region == "CH") ||
        lang == "hu" || lang == "sr" || lang == "sl" || lang == "sq")
        return { QWERTZ, SYM_EU, {} };                                             // qwertz
    // 15. AZERTY: any other French, or nl_BE
    if (lang == "fr" || (lang == "nl" && region == "BE"))
        return { AZERTY, SYM_AZERTY, {} };                                         // azerty
    // 16. DEFAULT: everything else (ja, ko, zh, ar, ru, th, he, el, hi, pt, pl,
    //     tr, vi, id, it_IT, nl_NL, uk, ...)
    return { QWERTY, SYM_EU, {} };                                                 // qwerty_eu
}
```

Notes for whoever ports this:

- Preserve the ORDER exactly. Spain-Spanish (rule 3) before generic Spanish (rule 4),
  CH variants (rules 13 and 14) before the generic AZERTY French catch (rule 15), and
  the generic-English catch in rule 12 before the QWERTZ block. Reordering changes
  behavior for shared locales (es, fr, it, de).
- Match `Locale.getDefault()` to your "current UI language" string. In Nano, feed the
  same locale you use to pick UI strings (the language picker). If you only have a bare
  language code with no region, every region-qualified rule (1, 2, 3, 12-CA, 13) is
  skipped and you correctly fall through to the language-only rules. For example bare
  "en" -> rule 12 -> qwerty_us; bare "fr" -> rule 15 -> azerty; bare "de" -> rule 14 ->
  qwertz; bare "es" -> rule 4 -> qwerty_es_us.
- For zh-CN / ja / ar / ru / th / he / etc., this returns the default qwerty_eu Latin
  layout, which is exactly what stock Leanback does. If the Nano product wants real
  IME-style input for CJK/Arabic, that is NEW functionality beyond a 1:1 Leanback port
  and must be designed separately (Leanback has nothing to copy here).

### 4.4 Script coverage summary table

| Script / family | Distinct Leanback layout? | Resource(s) | Languages that reach it |
|-----------------|---------------------------|-------------|--------------------------|
| Latin QWERTY    | Yes (the base + 12 variants) | qwerty_us, qwerty_eu, qwerty_en_gb, qwerty_en_in, qwerty_es_us, qwerty_es_eu, qwerty_az, qwerty_ca, qwerty_da, qwerty_et, qwerty_fi, qwerty_nb, qwerty_sv | en, es, gl, eu, az, ca, da, et, fi, nb, sv, fr_CA, plus all default-fallback languages |
| Latin QWERTZ    | Yes | qwertz, qwertz_ch | de, hr, cs, hu, sr, sl, sq, it_CH, de_CH, fr_CH |
| Latin AZERTY    | Yes | azerty | fr (non-CA, non-CH), nl_BE |
| Arabic          | No (falls back to Latin) | (none) | ar -> qwerty_eu |
| CJK (zh/ja/ko)  | No (falls back to Latin) | (none) | zh, ja, ko -> qwerty_eu |
| Cyrillic        | No (falls back to Latin) | (none) | ru, uk, and even sr -> Latin (sr -> qwertz) |
| Thai            | No (falls back to Latin) | (none) | th -> qwerty_eu |
| Hebrew          | No (falls back to Latin) | (none) | he -> qwerty_eu |
| Greek           | No (falls back to Latin) | (none) | el -> qwerty_eu |
| Indic           | No (falls back to Latin) | (none) | hi, bn, ta, ... -> qwerty_eu |

---

## 5. Cross-references and exact line anchors

- `LeanbackLocales.java` (whole file, locale group definitions): lines 36-117.
- `initKeyboards()` dispatch chain: `LeanbackKeyboardContainer.java:650-705`.
- `isMatch()` matcher: `LeanbackKeyboardContainer.java:707-721`.
- Number keyboard (shared): `LeanbackKeyboardContainer.java:704`.
- Keyboard objects declared: `LeanbackKeyboardContainer.java:425-427`.
- `initKeyboards()` call site (once, in constructor): `LeanbackKeyboardContainer.java:567`.
- ABC/SYM toggle: `LeanbackKeyboardContainer.onModeChangeClick()`,
  `LeanbackKeyboardContainer.java:1183-1186`.
- `setImeOptions()` behavioral flags (not layout): `LeanbackKeyboardContainer.java:800-840`.
- IME subtype (single, generic): `res/xml/method.xml:21-27`.
- Controller construction: `LeanbackKeyboardController.java:154-167`.
- IME service init: `LeanbackImeService.onInitializeInterface()`, `LeanbackImeService.java:106-111`.
- Special key codes: `LeanbackKeyboardView.java:81-90`.
- Bottom-row layout (no language key) sample: `qwerty_us.xml:208-232`.
- Keyboard XML format sample (rows, codes, labels, popups): `qwerty_us.xml:17-233`.
