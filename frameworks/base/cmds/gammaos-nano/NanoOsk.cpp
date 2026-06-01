/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Native on-screen keyboard runtime. A reimplementation of the Android TV
// Leanback IME keyboard, driven by D-pad / gamepad input into a UTF-8
// std::string, extended with a pluggable per-script input-method layer.
//
// Layout DATA is the generated NanoOskLayouts.h (kOskKb[] / kOskPopups[]),
// included by THIS translation unit only (the static-const tables must not be
// duplicated across TUs). Runtime state is NanoMenu::mOsk (NanoOskState).

#define LOG_TAG "GammaOSNano"

#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

#include <utils/Log.h>

#include "NanoMenu.h"
#include "NanoOsk.h"
#include "NanoOskLayouts.h"
#include "NanoOskLayoutsExtra.h"  // non-Latin keyboards (Korean, ...)
#include "NanoOskArabic.h"        // native Arabic contextual-shaping table
#include "NanoOskInput.h"         // HangulInput and other per-script engines
#include "NanoMenuShaders.h"   // FONT_CHAR_W / FONT_CHAR_H
#include "NanoMenuStrings.h"   // tr(), nanoGetLocale(), nanoGetLocaleInfo()

namespace android {

// ---------------------------------------------------------------------------
// File-local helpers
// ---------------------------------------------------------------------------
namespace {

constexpr int64_t kLongPressMs   = 350;   // hold A to open accent popup
constexpr int64_t kDoubleTapMs   = 250;   // shift double-click -> caps lock
constexpr float   kBoxAspect     = 2.488372f; // 428:172 Leanback grid aspect
constexpr float   kKeyFrac       = 0.065421f;  // 28/428 (one key cell)
constexpr size_t  kBufferCap     = 256;   // committed buffer byte cap

int64_t nowMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

// --- UTF-8 ---

std::string utf8Encode(uint32_t cp) {
    std::string s;
    if (cp < 0x80) {
        s += (char)cp;
    } else if (cp < 0x800) {
        s += (char)(0xC0 | (cp >> 6));
        s += (char)(0x80 | (cp & 0x3F));
    } else if (cp < 0x10000) {
        s += (char)(0xE0 | (cp >> 12));
        s += (char)(0x80 | ((cp >> 6) & 0x3F));
        s += (char)(0x80 | (cp & 0x3F));
    } else {
        s += (char)(0xF0 | (cp >> 18));
        s += (char)(0x80 | ((cp >> 12) & 0x3F));
        s += (char)(0x80 | ((cp >> 6) & 0x3F));
        s += (char)(0x80 | (cp & 0x3F));
    }
    return s;
}

// Byte offset of the start of the codepoint ending at byte `pos` (i.e. the
// previous codepoint boundary), for backspace / caret-left.
int utf8PrevStart(const std::string& s, int pos) {
    if (pos <= 0) return 0;
    int i = pos - 1;
    while (i > 0 && (((unsigned char)s[i] & 0xC0) == 0x80)) i--;
    return i;
}

// Byte offset just after the codepoint starting at byte `pos`, for caret-right.
int utf8NextStart(const std::string& s, int pos) {
    int n = (int)s.size();
    if (pos >= n) return n;
    int i = pos + 1;
    while (i < n && (((unsigned char)s[i] & 0xC0) == 0x80)) i++;
    return i;
}

uint32_t utf8DecodeAt(const std::string& s, int pos, int& advance) {
    int n = (int)s.size();
    if (pos >= n) { advance = 0; return 0; }
    unsigned char c = (unsigned char)s[pos];
    uint32_t cp; int len;
    if (c < 0x80) { cp = c; len = 1; }
    else if ((c & 0xE0) == 0xC0) { cp = c & 0x1F; len = 2; }
    else if ((c & 0xF0) == 0xE0) { cp = c & 0x0F; len = 3; }
    else if ((c & 0xF8) == 0xF0) { cp = c & 0x07; len = 4; }
    else { cp = c; len = 1; }
    for (int k = 1; k < len && pos + k < n; k++)
        cp = (cp << 6) | ((unsigned char)s[pos + k] & 0x3F);
    advance = len;
    return cp;
}

int utf8Len(const std::string& s) {
    int n = 0;
    for (size_t i = 0; i < s.size(); i++)
        if (((unsigned char)s[i] & 0xC0) != 0x80) n++;
    return n;
}

// --- Native Arabic contextual shaping (replaces ICU u_shapeArabic) ---
// Table-driven: each Arabic letter resolves to one of four presentation forms
// (isolated/final/initial/medial) based on whether it connects to its logical
// neighbours, plus the lam-alef ligature. Data lives in NanoOskArabic.h. Output
// stays in logical order; the RTL renderer reverses it for visual order.

const OskArShape* arShapeFor(uint32_t cp) {
    int lo = 0, hi = kOskArShapeCount - 1;     // table is sorted by base
    while (lo <= hi) {
        int m = (lo + hi) / 2;
        if (kOskArShape[m].base == cp) return &kOskArShape[m];
        if (kOskArShape[m].base < cp) lo = m + 1; else hi = m - 1;
    }
    return nullptr;
}
// Can cp connect to the letter AFTER it (joins on its left side)?
bool arJoinsLeft(uint32_t cp) { const OskArShape* s = arShapeFor(cp); return s && s->joinsLeft; }
// Can cp connect to the letter BEFORE it (joins on its right side)?
bool arJoinsRight(uint32_t cp) { const OskArShape* s = arShapeFor(cp); return s && s->joinsRight; }

std::string shapeArabic(const std::string& s) {
    if (s.empty()) return s;
    // Decode to codepoints.
    std::vector<uint32_t> cps;
    for (int i = 0; i < (int)s.size(); ) {
        int adv = 0;
        cps.push_back(utf8DecodeAt(s, i, adv));
        i += (adv > 0 ? adv : 1);
    }
    const int n = (int)cps.size();
    std::vector<uint32_t> out;
    out.reserve(n);
    for (int i = 0; i < n; i++) {
        uint32_t cp = cps[i];
        // Lam (U+0644) + alef variant -> single ligature glyph.
        if (cp == 0x0644 && i + 1 < n) {
            const OskArLamAlef* lig = nullptr;
            for (int k = 0; k < kOskArLamAlefCount; k++)
                if (kOskArLamAlef[k].alef == cps[i + 1]) { lig = &kOskArLamAlef[k]; break; }
            if (lig) {
                bool joinPrev = (i > 0) && arJoinsLeft(cps[i - 1]);  // lam joins right
                uint32_t g = joinPrev ? lig->fin : lig->iso;
                out.push_back(g ? g : cp);
                i++;                 // consume the alef
                continue;
            }
        }
        const OskArShape* sh = arShapeFor(cp);
        if (!sh) { out.push_back(cp); continue; }
        uint32_t prev = (i > 0)     ? cps[i - 1] : 0;
        uint32_t next = (i + 1 < n) ? cps[i + 1] : 0;
        bool joinPrev = arJoinsLeft(prev) && sh->joinsRight;
        bool joinNext = sh->joinsLeft && arJoinsRight(next);
        uint32_t g;
        if (joinPrev && joinNext) g = sh->med ? sh->med : (sh->fin ? sh->fin : sh->iso);
        else if (joinPrev)        g = sh->fin ? sh->fin : sh->iso;
        else if (joinNext)        g = sh->ini ? sh->ini : sh->iso;
        else                      g = sh->iso;
        out.push_back(g ? g : cp);
    }
    std::string r;
    for (uint32_t c : out) r += utf8Encode(c);
    return r;
}

// Reverse the codepoint order of a UTF-8 string (for pure-RTL visual layout).
std::string utf8Reverse(const std::string& s) {
    std::string out;
    out.reserve(s.size());
    int i = (int)s.size();
    while (i > 0) {
        int start = i - 1;
        while (start > 0 && (((unsigned char)s[start] & 0xC0) == 0x80)) start--;
        out.append(s, (size_t)start, (size_t)(i - start));
        i = start;
    }
    return out;
}

// Best-effort Latin uppercase for case folding. Covers ASCII, Latin-1
// Supplement, and the odd->even pairing of Latin Extended-A that the accent
// popups use. Replaced by ICU u_toupper when the RTL/ICU phase lands.
uint32_t toUpperCp(uint32_t cp) {
    if (cp >= 'a' && cp <= 'z') return cp - 32;
    if (cp >= 0xE0 && cp <= 0xFE && cp != 0xF7) return cp - 0x20; // a-grave..thorn
    if (cp == 0xFF) return 0x178;                                  // y-diaeresis -> Y
    if (cp >= 0x100 && cp <= 0x17F && (cp & 1)) return cp - 1;     // ext-A odd -> even
    if (cp >= 0x0430 && cp <= 0x044F) return cp - 0x20;            // Cyrillic а-я -> А-Я
    if (cp == 0x0451) return 0x0401;                              // ё -> Ё
    if (cp == 0x03C2) return 0x03A3;                              // final sigma ς -> Σ
    if (cp >= 0x03B1 && cp <= 0x03C9) return cp - 0x20;           // Greek α-ω -> Α-Ω
    return cp;
}

std::string utf8Upper(const std::string& s) {
    std::string out;
    int i = 0, n = (int)s.size();
    while (i < n) {
        int adv;
        uint32_t cp = utf8DecodeAt(s, i, adv);
        if (adv <= 0) break;
        out += utf8Encode(toUpperCp(cp));
        i += adv;
    }
    return out;
}

// The glyph drawn on an icon key (state-dependent for shift). The mode key's
// ABC/SYM text is already baked per-page into the table (GLYPH_SYMBOLS vs
// GLYPH_ALPHABET) by the generator, so the page is not needed here.
const char* glyphFor(OskGlyph g, OskShift shift) {
    switch (g) {
        case GLYPH_DELETE:    return "\xE2\x8C\xAB";              // U+232B
        case GLYPH_SHIFT_OFF: return (shift == SHIFT_LOCKED) ? "\xE2\x87\xAA"  // U+21EA caps
                                                             : "\xE2\x87\xA7"; // U+21E7 shift
        case GLYPH_LEFT:      return "\xE2\x86\x90";              // U+2190
        case GLYPH_RIGHT:     return "\xE2\x86\x92";              // U+2192
        case GLYPH_SYMBOLS:   return "?!#";
        case GLYPH_ALPHABET:  return "ABC";
        case GLYPH_SPACE:     return "";   // drawn as a bar
        default:              return "";
    }
}

// Compute the aspect-aware keyboard panel geometry. Pure function of the
// surface size and the measured action-button label width, so it is
// deterministic and host-testable.
OskBox oskComputeBox(int W, int H, float actLabelPx) {
    OskBox b{};
    float sf = fminf((float)W / 1080.0f, (float)H / 720.0f);
    if (sf < 0.5f) sf = 0.5f;
    b.sf = sf;

    float aspect = (H > 0) ? (float)W / (float)H : 1.6f;
    b.portrait = aspect < 0.85f;
    b.actBelow = b.portrait;

    float wFrac = (aspect >= 1.6f) ? 0.78f
                : (aspect >= 1.2f) ? 0.86f
                : (aspect >= 0.85f) ? 0.90f : 0.96f;

    float gap        = 12.0f * sf;
    float actPadH    = 16.0f * sf;
    float previewH   = FONT_CHAR_H * 2.0f * sf + 10.0f * sf;
    float footerH    = FONT_CHAR_H * 1.4f * sf + 6.0f * sf;
    float bottomPad  = 16.0f * sf;
    float panelPad   = 10.0f * sf;

    b.actW = actLabelPx + 2.0f * actPadH;
    b.actH = 36.0f * sf;

    float availW = (float)W * wFrac;
    float kbW = b.actBelow ? availW : (availW - b.actW - gap);

    // Clamp key size for legibility / to avoid a comically large grid.
    float keyPx = kbW * kKeyFrac;
    float minKeyPx = 22.0f * sf, maxKeyPx = 64.0f * sf;
    if (keyPx > maxKeyPx) {
        kbW = maxKeyPx / kKeyFrac;
    } else if (keyPx < minKeyPx) {
        kbW = minKeyPx / kKeyFrac;
        float capW = (float)W * 0.98f - (b.actBelow ? 0.0f : (b.actW + gap));
        if (kbW > capW) kbW = capW;
    }
    float kbH = kbW / kBoxAspect;

    // Inner content stack height (preview + keyboard [+ action-below] + footer),
    // all of which live INSIDE the panel padding. Vertical budget keeps the
    // field/content above the keyboard visible; portrait gets a taller budget.
    float rowGap = 8.0f * sf;
    float extra  = b.actBelow ? (gap + b.actH) : 0.0f;
    float innerH = previewH + rowGap + kbH + extra + rowGap + footerH;
    float budget = (b.portrait ? 0.85f : 0.66f) * (float)H - 2.0f * panelPad;
    if (innerH > budget && innerH > 0.0f) {
        float scale = budget / innerH;
        kbW *= scale; kbH *= scale;
        innerH = previewH + rowGap + kbH + extra + rowGap + footerH;
    }
    b.keyPx = kbW * kKeyFrac;
    b.kbW = kbW;
    b.kbH = kbH;

    float blockW = b.actBelow ? kbW : (kbW + gap + b.actW);
    b.panelW = blockW + 2.0f * panelPad;
    b.panelH = innerH + 2.0f * panelPad;
    b.panelX = ((float)W - b.panelW) / 2.0f;
    b.panelY = (float)H - bottomPad - b.panelH;
    if (b.panelY < 4.0f * sf) b.panelY = 4.0f * sf;   // never run off the top

    // Lay out top-down inside the panel.
    b.kbX = ((float)W - blockW) / 2.0f;
    b.previewX = b.kbX;
    b.previewY = b.panelY + panelPad;
    b.kbY = b.previewY + previewH + rowGap;
    if (!b.actBelow) {
        b.actX = b.kbX + kbW + gap;
        b.actY = b.kbY + kbH / 2.0f - b.actH / 2.0f;
        b.footerY = b.kbY + kbH + rowGap;
    } else {
        b.actX = ((float)W - b.actW) / 2.0f;
        b.actY = b.kbY + kbH + gap;
        b.footerY = b.actY + b.actH + rowGap;
    }
    return b;
}

inline void keyRect(const OskBox& b, float fx, float fw, float fy, float fh,
                    float& x, float& y, float& w, float& h) {
    x = b.kbX + fx * b.kbW;
    y = b.kbY + fy * b.kbH;
    w = fw * b.kbW;
    h = fh * b.kbH;
}

// Korean dubeolsik shift: base consonant/vowel -> tense/compound jamo.
uint32_t koreanShiftJamo(uint32_t cp) {
    switch (cp) {
        case 0x3142: return 0x3143; // b  -> bb
        case 0x3148: return 0x3149; // j  -> jj
        case 0x3137: return 0x3138; // d  -> dd
        case 0x3131: return 0x3132; // g  -> gg
        case 0x3145: return 0x3146; // s  -> ss
        case 0x3150: return 0x3152; // ae -> yae
        case 0x3154: return 0x3156; // e  -> ye
        default: return cp;
    }
}

// Per-script input-method singletons (own their composing state for the run).
HangulInput* oskHangulInput() { static HangulInput inst; return &inst; }
PinyinInput* oskPinyinInput() { static PinyinInput inst; return &inst; }
KanaInput*   oskKanaInput()   { static KanaInput inst;   return &inst; }

} // anonymous namespace

// ---------------------------------------------------------------------------
// Language -> layout (faithful LeanbackKeyboardContainer.initKeyboards chain)
// ---------------------------------------------------------------------------
OskLayoutChoice oskPickLayout(const char* code, const char* region) {
    const char* c = code ? code : "";
    const char* r = region ? region : "";
    auto M = [&](const char* lang, const char* country) -> bool {
        if (lang[0] && strcmp(c, lang) != 0) return false;
        if (country[0] && strcmp(r, country) != 0) return false;
        return true;
    };
    if (M("en", "GB")) return { OSK_KB_QWERTY_EN_GB, OSK_KB_SYM_EN_GB };
    if (M("en", "IN")) return { OSK_KB_QWERTY_EN_IN, OSK_KB_SYM_EN_IN };
    if (M("es", "ES") || M("gl", "ES") || M("eu", "ES"))
        return { OSK_KB_QWERTY_ES_EU, OSK_KB_SYM_EU };
    if (M("es", ""))   return { OSK_KB_QWERTY_ES_US, OSK_KB_SYM_US };
    if (M("az", ""))   return { OSK_KB_QWERTY_AZ, OSK_KB_SYM_EU };
    if (M("ca", ""))   return { OSK_KB_QWERTY_CA, OSK_KB_SYM_EU };
    if (M("da", ""))   return { OSK_KB_QWERTY_DA, OSK_KB_SYM_EU };
    if (M("et", ""))   return { OSK_KB_QWERTY_ET, OSK_KB_SYM_EU };
    if (M("fi", ""))   return { OSK_KB_QWERTY_FI, OSK_KB_SYM_EU };
    if (M("nb", ""))   return { OSK_KB_QWERTY_NB, OSK_KB_SYM_US };
    if (M("sv", ""))   return { OSK_KB_QWERTY_SV, OSK_KB_SYM_EU };
    if (M("en", "") || M("fr", "CA")) return { OSK_KB_QWERTY_US, OSK_KB_SYM_US };
    if (M("de", "CH") || M("it", "CH")) return { OSK_KB_QWERTZ_CH, OSK_KB_SYM_EU };
    if (M("de", "") || M("hr", "") || M("cs", "") || M("fr", "CH") ||
        M("it", "CH") || M("hu", "") || M("sr", "") || M("sl", "") || M("sq", ""))
        return { OSK_KB_QWERTZ, OSK_KB_SYM_EU };
    if (M("fr", "") || M("nl", "BE")) return { OSK_KB_AZERTY, OSK_KB_SYM_AZERTY };
    return { OSK_KB_QWERTY_EU, OSK_KB_SYM_EU };
}

// In-keyboard language switch: a small curated cycle (D-pad friendly).
namespace {
struct LangCycleEntry { const char* code; const char* region; };
const LangCycleEntry kLangCycle[] = {
    { "en", "US" }, { "ko", "KR" }, { "zh", "CN" }, { "ja", "JP" }, { "ru", "RU" }, { "el", "GR" },
    { "he", "IL" }, { "ar", "SA" }, { "th", "TH" }, { "fr", "FR" },
    { "de", "DE" }, { "es", "ES" }, { "en", "GB" }, { "da", "DK" },
    { "fi", "FI" }, { "sv", "SE" }, { "nb", "NO" },
};
const int kLangCycleCount = (int)(sizeof(kLangCycle) / sizeof(kLangCycle[0]));
} // namespace

// ---------------------------------------------------------------------------
// Layout / state helpers
// ---------------------------------------------------------------------------

const OskKeyboard* NanoMenu::oskCurrentKb() const {
    const OskKeyboard* kb = (mOsk.page == PAGE_ABC) ? mOsk.abcKb : mOsk.symKb;
    return kb ? kb : &kOskKb[OSK_KB_QWERTY_EU];
}

void NanoMenu::oskApplyLocale() {
    const char* code; const char* region;
    if (mOsk.langCycleIndex >= 0 && mOsk.langCycleIndex < kLangCycleCount) {
        code   = kLangCycle[mOsk.langCycleIndex].code;
        region = kLangCycle[mOsk.langCycleIndex].region;
    } else {
        const LocaleInfo& li = nanoGetLocaleInfo(nanoGetLocale());
        code = li.code; region = li.regionCode;
    }
    oskSetLanguage(code, region);
}

void NanoMenu::oskSetLanguage(const char* code, const char* region) {
    auto setLabel = [&](const char* s) {
        size_t i = 0;
        for (; s[i] && i < sizeof(mOsk.langLabel) - 1; i++) mOsk.langLabel[i] = s[i];
        mOsk.langLabel[i] = 0;
    };
    mOsk.arabicShape = false;
    // Non-Latin scripts: own layout + composing input method.
    if (strcmp(code, "ko") == 0) {
        mOsk.abcKb = &kKb_korean;
        mOsk.symKb = &kOskKb[OSK_KB_SYM_US];
        mOsk.im = oskHangulInput();
        mOsk.im->reset();
        mOsk.dir = OSK_LTR;
        setLabel("\xed\x95\x9c");           // 한
        return;
    }
    if (strcmp(code, "zh") == 0) {
        // Pinyin is typed on a Latin QWERTY; the engine surfaces Hanzi candidates.
        OskLayoutChoice c = oskPickLayout("en", "US");
        mOsk.abcKb = &kOskKb[c.abcId];
        mOsk.symKb = &kOskKb[c.symId];
        mOsk.im = oskPinyinInput();
        mOsk.im->reset();
        mOsk.dir = OSK_LTR;
        setLabel("\xe4\xb8\xad");            // 中
        return;
    }
    if (strcmp(code, "ja") == 0) {
        // Japanese romaji typed on a Latin QWERTY -> hiragana (no candidates).
        OskLayoutChoice c = oskPickLayout("en", "US");
        mOsk.abcKb = &kOskKb[c.abcId];
        mOsk.symKb = &kOskKb[c.symId];
        mOsk.im = oskKanaInput();
        mOsk.im->reset();
        mOsk.dir = OSK_LTR;
        setLabel("\xe3\x81\x82");            // あ
        return;
    }
    if (strcmp(code, "ru") == 0) {
        mOsk.abcKb = &kKb_russian;
        mOsk.symKb = &kOskKb[OSK_KB_SYM_US];
        mOsk.im = nullptr; mOsk.dir = OSK_LTR;
        setLabel("RU"); return;
    }
    if (strcmp(code, "el") == 0) {
        mOsk.abcKb = &kKb_greek;
        mOsk.symKb = &kOskKb[OSK_KB_SYM_US];
        mOsk.im = nullptr; mOsk.dir = OSK_LTR;
        setLabel("EL"); return;
    }
    if (strcmp(code, "he") == 0) {
        mOsk.abcKb = &kKb_hebrew;
        mOsk.symKb = &kOskKb[OSK_KB_SYM_US];
        mOsk.im = nullptr; mOsk.dir = OSK_RTL;   // Hebrew is right-to-left
        setLabel("HE"); return;
    }
    if (strcmp(code, "th") == 0) {
        mOsk.abcKb = &kKb_thai;
        mOsk.symKb = &kOskKb[OSK_KB_SYM_US];
        mOsk.im = nullptr; mOsk.dir = OSK_LTR;
        setLabel("TH"); return;
    }
    if (strcmp(code, "ar") == 0) {
        mOsk.abcKb = &kKb_arabic;
        mOsk.symKb = &kOskKb[OSK_KB_SYM_US];
        mOsk.im = nullptr; mOsk.dir = OSK_RTL; mOsk.arabicShape = true;
        setLabel("AR"); return;
    }
    // Latin (Leanback chain), direct input.
    OskLayoutChoice c = oskPickLayout(code, region);
    mOsk.abcKb = &kOskKb[c.abcId];
    mOsk.symKb = &kOskKb[c.symId];
    mOsk.im = nullptr;
    mOsk.dir = OSK_LTR;
    char up[3] = { code[0], code[0] ? code[1] : (char)0, 0 };
    if (up[0] >= 'a' && up[0] <= 'z') up[0] -= 32;
    if (up[1] >= 'a' && up[1] <= 'z') up[1] -= 32;
    setLabel(up[0] ? up : "EN");
}

OskBox NanoMenu::oskLayoutBox() {
    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;
    const char* actLabel = mOskPasswordMode ? "Done" : "Search";
    float actLabelPx = measureText(actLabel, 1.7f * sf);
    return oskComputeBox(mWidth, mHeight, actLabelPx);
}

// Clamp focus indices into the current page (defensive after page swaps).
static void clampFocus(const OskKeyboard* kb, int& row, int& col) {
    if (row < 0) row = 0;
    if (row >= kb->rowCount) row = kb->rowCount - 1;
    if (col < 0) col = 0;
    if (col >= kb->rows[row].keyCount) col = kb->rows[row].keyCount - 1;
}

// ---------------------------------------------------------------------------
// Buffer operations (UTF-8, caret aware)
// ---------------------------------------------------------------------------

void NanoMenu::oskInsertCp(uint32_t cp) {
    if (mOsk.im) {
        OskBuffer buf{ &mOskQuery, &mOsk.caret };
        if (mOsk.im->onCodepoint(cp, buf)) {
            mOsk.candidates = mOsk.im->candidates();
            if (!mOskPasswordMode) updateSearchResults();
            mDisplayDirty = true;
            return;
        }
    }
    std::string enc = utf8Encode(cp);
    if (mOskQuery.size() + enc.size() > kBufferCap) return;
    if (mOsk.caret < 0) mOsk.caret = 0;
    if (mOsk.caret > (int)mOskQuery.size()) mOsk.caret = (int)mOskQuery.size();
    mOskQuery.insert((size_t)mOsk.caret, enc);
    mOsk.caret += (int)enc.size();
    if (!mOskPasswordMode) updateSearchResults();
    mDisplayDirty = true;
}

void NanoMenu::oskType(char c) {
    oskInsertCp((uint32_t)(unsigned char)c);
}

void NanoMenu::oskBackspace() {
    if (mOsk.im) {
        OskBuffer buf{ &mOskQuery, &mOsk.caret };
        if (mOsk.im->onBackspace(buf)) {
            mOsk.candidates = mOsk.im->candidates();
            if (!mOskPasswordMode) updateSearchResults();
            mDisplayDirty = true;
            return;
        }
    }
    if (mOsk.caret <= 0 || mOskQuery.empty()) { mDisplayDirty = true; return; }
    if (mOsk.caret > (int)mOskQuery.size()) mOsk.caret = (int)mOskQuery.size();
    int start = utf8PrevStart(mOskQuery, mOsk.caret);
    mOskQuery.erase((size_t)start, (size_t)(mOsk.caret - start));
    mOsk.caret = start;
    if (!mOskPasswordMode) updateSearchResults();
    mDisplayDirty = true;
}

void NanoMenu::oskCaretLeft() {
    if (mOsk.caret > 0) mOsk.caret = utf8PrevStart(mOskQuery, mOsk.caret);
    mDisplayDirty = true;
}

void NanoMenu::oskCaretRight() {
    if (mOsk.caret < (int)mOskQuery.size())
        mOsk.caret = utf8NextStart(mOskQuery, mOsk.caret);
    mDisplayDirty = true;
}

// ---------------------------------------------------------------------------
// State machine: shift / caps / symbol page / language
// ---------------------------------------------------------------------------

void NanoMenu::oskToggleShift() {
    int64_t now = nowMs();
    if (now - mOsk.lastShiftClickMs < kDoubleTapMs) {
        mOsk.shift = SHIFT_LOCKED;                 // double-click -> caps lock
    } else if (mOsk.shift == SHIFT_OFF) {
        mOsk.shift = SHIFT_ON;
    } else {
        mOsk.shift = SHIFT_OFF;                     // single click from on/locked
    }
    mOsk.lastShiftClickMs = now;
    mDisplayDirty = true;
}

void NanoMenu::oskToggleCaps() {
    mOsk.shift = (mOsk.shift == SHIFT_LOCKED) ? SHIFT_OFF : SHIFT_LOCKED;
    mDisplayDirty = true;
}

void NanoMenu::oskToggleSym() {
    mOsk.page = (mOsk.page == PAGE_ABC) ? PAGE_SYM : PAGE_ABC;
    mOsk.miniOpen = false;
    mOsk.inAction = false;
    const OskKeyboard* kb = oskCurrentKb();
    clampFocus(kb, mOsk.focusRow, mOsk.focusCol);
    mDisplayDirty = true;
}

void NanoMenu::oskCycleLanguage(int dir) {
    if (mOsk.im) {                       // commit any in-progress composition
        OskBuffer buf{ &mOskQuery, &mOsk.caret };
        mOsk.im->commitComposing(buf);
        mOsk.candidates.clear();
        mOsk.inCandidateBar = false;
    }
    int idx = mOsk.langCycleIndex;
    if (idx < 0) idx = 0; else idx += dir;
    if (idx < 0) idx = kLangCycleCount - 1;
    if (idx >= kLangCycleCount) idx = 0;
    mOsk.langCycleIndex = idx;
    mOsk.page = PAGE_ABC;
    mOsk.shift = SHIFT_OFF;
    oskApplyLocale();
    clampFocus(oskCurrentKb(), mOsk.focusRow, mOsk.focusCol);
    if (!mOskPasswordMode) updateSearchResults();
    mDisplayDirty = true;
}

// ---------------------------------------------------------------------------
// Accent / shift popup mini-keyboard
// ---------------------------------------------------------------------------

void NanoMenu::oskOpenPopup(const OskKey& key) {
    if (key.popupIndex < 0 || key.popupIndex >= kOskPopupCount) return;
    const OskPopup& p = kOskPopups[key.popupIndex];
    if (p.keyCount <= 0) return;
    mOsk.miniOpen = true;
    mOsk.miniPopupIndex = key.popupIndex;
    mOsk.miniOriginRow = mOsk.focusRow;
    mOsk.miniOriginCol = mOsk.focusCol;
    int rowKeys = oskCurrentKb()->rows[mOsk.focusRow].keyCount;
    int base = mOsk.focusCol;
    if (base + p.keyCount > rowKeys) base = rowKeys - p.keyCount;
    if (base < 0) base = 0;
    mOsk.miniBaseCol = base;
    mOsk.miniFocus = 0;
    mDisplayDirty = true;
}

void NanoMenu::oskClosePopup() {
    mOsk.miniOpen = false;
    mOsk.miniPopupIndex = -1;
    mOsk.focusRow = mOsk.miniOriginRow;
    mOsk.focusCol = mOsk.miniOriginCol;
    mDisplayDirty = true;
}

void NanoMenu::oskCommitPopupCell() {
    if (!mOsk.miniOpen || mOsk.miniPopupIndex < 0) return;
    const OskPopup& p = kOskPopups[mOsk.miniPopupIndex];
    int idx = mOsk.miniFocus;
    if (idx < 0 || idx >= p.keyCount) { oskClosePopup(); return; }
    const OskKey& cell = p.keys[idx];
    uint32_t cp = (uint32_t)cell.code;
    if (cell.caseFoldable && mOsk.shift != SHIFT_OFF) cp = toUpperCp(cp);
    oskClosePopup();
    if (cp > 0) {
        oskInsertCp(cp);
        if (mOsk.shift == SHIFT_ON) mOsk.shift = SHIFT_OFF;
    }
    mOsk.pressStartMs = nowMs();
}

// ---------------------------------------------------------------------------
// Key activation + A press/release + per-frame tick (long-press, animation)
// ---------------------------------------------------------------------------

void NanoMenu::oskActivateKey(const OskKey& key) {
    switch (key.code) {
        case OSK_SHIFT:       oskToggleShift(); return;
        case OSK_MODE_CHANGE: oskToggleSym();   return;
        case OSK_LEFT:        oskCaretLeft();   return;
        case OSK_RIGHT:       oskCaretRight();  return;
        case OSK_DELETE:      oskBackspace();   return;
        case OSK_CAPS_LOCK:   oskToggleCaps();  return;
        case OSK_VOICE:       return;
        default: break;
    }
    if (key.code > 0) {
        uint32_t cp = (uint32_t)key.code;
        if (key.caseFoldable && mOsk.shift != SHIFT_OFF) cp = toUpperCp(cp);
        else if (mOsk.shift != SHIFT_OFF && cp >= 0x3131 && cp <= 0x3163)
            cp = koreanShiftJamo(cp);   // dubeolsik tense/compound jamo
        oskInsertCp(cp);
        if (mOsk.shift == SHIFT_ON) mOsk.shift = SHIFT_OFF; // one-shot shift
        mOsk.pressStartMs = nowMs();
    }
}

void NanoMenu::oskAPress() {
    if (mOsk.miniOpen)        { oskCommitPopupCell(); return; }
    if (mOsk.inCandidateBar)  {
        if (mOsk.im && mOsk.candFocus >= 0) {
            OskBuffer buf{ &mOskQuery, &mOsk.caret };
            mOsk.im->chooseCandidate(mOsk.candFocus, buf);
            mOsk.candidates = mOsk.im->candidates();
            mOsk.inCandidateBar = mOsk.candidates.empty() ? false : mOsk.inCandidateBar;
            if (mOsk.candidates.empty()) mOsk.inCandidateBar = false;
            mOsk.candFocus = 0;
            if (!mOskPasswordMode) updateSearchResults();
            mDisplayDirty = true;
        }
        return;
    }
    if (mOsk.inAction)        { oskConfirm(); return; }

    const OskKeyboard* kb = oskCurrentKb();
    clampFocus(kb, mOsk.focusRow, mOsk.focusCol);
    const OskKey& key = kb->rows[mOsk.focusRow].keys[mOsk.focusCol];
    bool deferred = (key.code > 0 && key.popupIndex >= 0 && key.glyph == GLYPH_NONE);
    if (deferred) {
        mOsk.aDownMs = nowMs();
        mOsk.aLongFired = false;
    } else {
        oskActivateKey(key);
        mOsk.aDownMs = 0;
    }
}

void NanoMenu::oskARelease() {
    if (mOsk.aDownMs != 0 && !mOsk.aLongFired) {
        // Short tap on a popup-bearing key: commit the base character.
        const OskKeyboard* kb = oskCurrentKb();
        clampFocus(kb, mOsk.focusRow, mOsk.focusCol);
        oskActivateKey(kb->rows[mOsk.focusRow].keys[mOsk.focusCol]);
    }
    mOsk.aDownMs = 0;
    mOsk.aLongFired = false;
}

void NanoMenu::oskTick() {
    if (!mOskActive) return;
    if (mOsk.aDownMs != 0 && !mOsk.aLongFired && !mOsk.miniOpen) {
        if (nowMs() - mOsk.aDownMs > kLongPressMs) {
            const OskKeyboard* kb = oskCurrentKb();
            clampFocus(kb, mOsk.focusRow, mOsk.focusCol);
            const OskKey& key = kb->rows[mOsk.focusRow].keys[mOsk.focusCol];
            if (key.popupIndex >= 0) {
                oskOpenPopup(key);
                mOsk.aLongFired = true;
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Navigation: geometric nearest-in-direction
// ---------------------------------------------------------------------------

void NanoMenu::oskMoveCursor(NavDir dir) {
    if (!mOskActive) return;

    // Popup: navigate within the run; leaving it dismisses.
    if (mOsk.miniOpen) {
        const OskPopup& p = kOskPopups[mOsk.miniPopupIndex];
        if (dir == NavDir::Left) {
            if (mOsk.miniFocus > 0) mOsk.miniFocus--; else oskClosePopup();
        } else if (dir == NavDir::Right) {
            if (mOsk.miniFocus < p.keyCount - 1) mOsk.miniFocus++; else oskClosePopup();
        } else {
            oskClosePopup();
        }
        mDisplayDirty = true;
        return;
    }

    // Candidate bar (Phase B+).
    if (mOsk.inCandidateBar) {
        int n = (int)mOsk.candidates.size();
        if (dir == NavDir::Left)  { if (mOsk.candFocus > 0) mOsk.candFocus--; }
        else if (dir == NavDir::Right) { if (mOsk.candFocus < n - 1) mOsk.candFocus++; }
        else if (dir == NavDir::Down)  { mOsk.inCandidateBar = false; }
        mDisplayDirty = true;
        return;
    }

    const OskKeyboard* kb = oskCurrentKb();
    clampFocus(kb, mOsk.focusRow, mOsk.focusCol);
    OskBox b = oskLayoutBox();

    // Action button focus.
    if (mOsk.inAction) {
        if (dir == NavDir::Left) {
            mOsk.inAction = false;
        } else if (dir == NavDir::Up && !mOsk.candidates.empty()) {
            mOsk.inAction = false; mOsk.inCandidateBar = true; mOsk.candFocus = 0;
        }
        mDisplayDirty = true;
        return;
    }

    const OskRow& row = kb->rows[mOsk.focusRow];
    const OskKey& cur = row.keys[mOsk.focusCol];
    float cx, cy, cw, ch;
    keyRect(b, cur.fx, cur.fw, row.fy, row.fh, cx, cy, cw, ch);
    float ccx = cx + cw / 2.0f, ccy = cy + ch / 2.0f;
    bool curSpace = (cur.code == 32);

    float probeX = ccx, probeY = ccy;
    if (dir == NavDir::Left) {
        if (cur.edgeLeft) return;                       // no wrap at left edge
        probeX = cx - ch * 0.5f; probeY = ccy;
    } else if (dir == NavDir::Right) {
        if (cur.edgeRight) { mOsk.inAction = true; mDisplayDirty = true; return; }
        probeX = cx + cw + ch * 0.5f; probeY = ccy;
    } else if (dir == NavDir::Up) {
        probeY = ccy - ch * 1.25f;
        probeX = (curSpace && mOsk.rememberedX >= 0) ? mOsk.rememberedX : ccx;
        if (probeY < b.kbY && !mOsk.candidates.empty()) {
            mOsk.inCandidateBar = true; mOsk.candFocus = 0; mDisplayDirty = true; return;
        }
    } else { // Down
        probeY = ccy + ch * 1.25f;
        probeX = (curSpace && mOsk.rememberedX >= 0) ? mOsk.rememberedX : ccx;
    }

    // Nearest key center to the probe point, constrained to the move side
    // for vertical moves so we never re-select within the same row.
    int bestR = mOsk.focusRow, bestC = mOsk.focusCol;
    float bestD = 1e18f;
    for (int r = 0; r < kb->rowCount; r++) {
        const OskRow& rr = kb->rows[r];
        for (int c = 0; c < rr.keyCount; c++) {
            if (r == mOsk.focusRow && c == mOsk.focusCol) continue;
            const OskKey& k = rr.keys[c];
            float kx, ky, kw, kh;
            keyRect(b, k.fx, k.fw, rr.fy, rr.fh, kx, ky, kw, kh);
            float kcx = kx + kw / 2.0f, kcy = ky + kh / 2.0f;
            if (dir == NavDir::Up   && kcy >= ccy - 1.0f) continue;
            if (dir == NavDir::Down && kcy <= ccy + 1.0f) continue;
            float dx = kcx - probeX, dy = kcy - probeY;
            float d = dx * dx + dy * dy;
            if (d < bestD) { bestD = d; bestR = r; bestC = c; }
        }
    }
    mOsk.focusRow = bestR;
    mOsk.focusCol = bestC;

    // Remember the column for vertical travel across the space bar.
    const OskKey& nk = kb->rows[bestR].keys[bestC];
    if (nk.code != 32) {
        float nx, ny, nw, nh;
        keyRect(b, nk.fx, nk.fw, kb->rows[bestR].fy, kb->rows[bestR].fh, nx, ny, nw, nh);
        mOsk.rememberedX = nx + nw / 2.0f;
    }
    mDisplayDirty = true;
}

// ---------------------------------------------------------------------------
// Lifecycle: open / close / confirm / password
// ---------------------------------------------------------------------------

void NanoMenu::openOsk() {
    mOskActive = true;
    mOskPasswordMode = false;
    mOskPlaintext = false;
    mOskPasswordPrompt.clear();
    mOskPasswordCallback = nullptr;
    mOskQuery.clear();
    mOsk.resetForOpen();
    oskApplyLocale();
    mOsk.caret = 0;
    mSearchResults.clear();
    mSearchSelectedIndex = 0;
    mSearchActive = false;
    mDisplayDirty = true;
}

void NanoMenu::closeOsk() {
    if (mOsk.miniOpen) { oskClosePopup(); mDisplayDirty = true; return; }
    if (mOsk.im) { mOsk.im->reset(); mOsk.candidates.clear(); }
    mOsk.closing = true;   // fade out (renderOsk finalizes mOskActive=false)
    if (mOskPasswordMode) {
        mOskPasswordMode = false;
        mOskPlaintext = false;
        mOskPasswordPrompt.clear();
        mOskPasswordCallback = nullptr;
        mOskQuery.clear();
        mOsk.caret = 0;
        mDisplayDirty = true;
        return;
    }
    if (mOskQuery.empty()) mSearchActive = false;
    mDisplayDirty = true;
}

void NanoMenu::oskConfirm() {
    if (mOsk.im) {
        OskBuffer buf{ &mOskQuery, &mOsk.caret };
        mOsk.im->commitComposing(buf);
        mOsk.im->reset();
        mOsk.candidates.clear();
        mOsk.inCandidateBar = false;
    }
    mOsk.closing = true;   // fade out (renderOsk finalizes mOskActive=false)
    if (mOskPasswordMode) {
        auto cb = std::move(mOskPasswordCallback);
        std::string pw = mOskQuery;
        mOskPasswordMode = false;
        mOskPlaintext = false;
        mOskPasswordPrompt.clear();
        mOskPasswordCallback = nullptr;
        mOskQuery.clear();
        mOsk.caret = 0;
        mDisplayDirty = true;
        if (cb) cb(pw);
        return;
    }
    if (!mOskQuery.empty()) {
        mSearchActive = true;
        mSearchSelectedIndex = 0;
        updateSearchResults();
    } else {
        mSearchActive = false;
    }
    mDisplayDirty = true;
}

void NanoMenu::openOskForPassword(const std::string& prompt,
                                  std::function<void(const std::string&)> onSubmit) {
    mOskPasswordMode = true;
    mOskPlaintext = false;
    mOskPasswordPrompt = prompt;
    mOskPasswordCallback = std::move(onSubmit);
    mOskQuery.clear();
    mOsk.resetForOpen();
    oskApplyLocale();
    mOsk.caret = 0;
    mOskActive = true;
    mDisplayDirty = true;
}

std::string NanoMenu::maskPassword(const std::string& s) {
    // One mask glyph per codepoint (not per byte) so multibyte input masks 1:1.
    std::string out;
    out.reserve(s.size());
    for (size_t i = 0; i < s.size(); i++)
        if (((unsigned char)s[i] & 0xC0) != 0x80) out += '*';
    return out;
}

// ---------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------

void NanoMenu::renderOsk() {
    if (!mOskActive) return;
    oskTick();   // long-press popup + animation clock (render runs every frame)

    // --- Show/hide fade ---
    float decay = 1.0f - expf(-16.0f * mFrameDt);
    if (mOsk.closing) {
        mOsk.anim += (0.0f - mOsk.anim) * decay;
        if (mOsk.anim < 0.02f) {           // fully hidden -> finalize
            mOskActive = false; mOsk.closing = false; mOsk.anim = 0.0f;
            return;
        }
    } else {
        mOsk.anim += (1.0f - mOsk.anim) * decay;
        if (mOsk.anim > 0.999f) mOsk.anim = 1.0f;
    }
    const float fade = mOsk.anim;

    OskBox b = oskLayoutBox();
    const OskKeyboard* kb = oskCurrentKb();
    clampFocus(kb, mOsk.focusRow, mOsk.focusCol);

    // --- Palette (osk.png-inspired dark glass) ---
    const float panelRad = 20.0f * b.sf;
    const float keyRad   = 9.0f * b.sf;
    const float keyGap   = 3.0f * b.sf;     // inset of the key cap inside its cell
    // key cap fill
    const float kBgR = 0.16f, kBgG = 0.19f, kBgB = 0.24f, kBgA = 1.0f;
    // light text on dark keys; dark text on the white focused key
    const float kTxtR = 0.88f, kTxtG = 0.90f, kTxtB = 0.95f;
    const float kFocTxtR = 0.08f, kFocTxtG = 0.10f, kFocTxtB = 0.14f;
    // accent (action / active shift)
    const float accR = 0.27f, accG = 0.52f, accB = 0.96f;

    // --- Frosted-glass panel (captures the XMB behind, blurs + darkens it) ---
    if (captureGlass(b.panelX, b.panelY, b.panelW, b.panelH)) {
        drawFrostedGlass(b.panelX, b.panelY, b.panelW, b.panelH, panelRad,
                         0.50f, 0.54f, 0.64f, 1.0f, fade);
    } else {
        drawRoundedRect(b.panelX, b.panelY, b.panelW, b.panelH, panelRad,
                        0.12f, 0.14f, 0.18f, 0.92f * fade);
    }

    // --- Candidate bar (Phase B+): only when an engine produced candidates. ---
    if (!mOsk.candidates.empty()) {
        float chipScale = 1.7f * b.sf;
        float cx = b.previewX;
        float cy = b.panelY + 4.0f * b.sf;
        for (int i = 0; i < (int)mOsk.candidates.size() && cx < b.panelX + b.panelW; i++) {
            const std::string& cand = mOsk.candidates[i];
            float tw = measureText(cand.c_str(), chipScale);
            bool sel = (mOsk.inCandidateBar && i == mOsk.candFocus);
            if (sel)
                drawRoundedRect(cx - 6.0f * b.sf, cy - 3.0f * b.sf,
                                tw + 12.0f * b.sf, FONT_CHAR_H * chipScale + 6.0f * b.sf,
                                7.0f * b.sf, 1.0f, 1.0f, 1.0f, 0.92f * fade);
            drawText(cand.c_str(), cx, cy, chipScale,
                     sel ? kFocTxtR : accR, sel ? kFocTxtG : accG,
                     sel ? kFocTxtB : accB, fade);
            cx += tw + 18.0f * b.sf;
        }
    }

    // --- Text preview / query line with caret ---
    {
        std::string label, value;
        float pr, pg, pb;
        if (mOskPasswordMode) {
            label = (mOskPasswordPrompt.empty() ? "Password" : mOskPasswordPrompt) + ": ";
            value = mOskPlaintext ? mOskQuery : maskPassword(mOskQuery);
            pr = 1.0f; pg = 0.78f; pb = 0.40f;
        } else {
            label = "Search: ";
            value = mOskQuery;
            pr = 0.45f; pg = 0.78f; pb = 1.0f;
        }
        std::string composing = mOsk.im ? mOsk.im->composingText() : std::string();
        float ps = 2.0f * b.sf;
        float y = b.previewY;
        float blink = 0.55f + 0.45f * sinf((float)nowMs() * 0.006f);
        drawText(label.c_str(), b.previewX, y, ps, pr, pg, pb, fade);
        float lw = measureText(label.c_str(), ps);
        if (mOsk.dir == OSK_RTL) {
            // Right-to-left: shape (Arabic) then put into visual order, right-
            // aligned; caret sits at the logical end, i.e. to the LEFT of the text.
            std::string base = mOsk.arabicShape ? shapeArabic(value) : value;
            std::string vis = utf8Reverse(base);
            if (!composing.empty()) vis = utf8Reverse(composing) + vis;
            float vw = measureText(vis.c_str(), ps);
            float rightEdge = b.panelX + b.panelW - 70.0f * b.sf; // room for badge
            float minx = b.previewX + lw + 12.0f * b.sf;
            float vx = rightEdge - vw;
            if (vx < minx) vx = minx;
            drawText(vis.c_str(), vx, y, ps, 1.0f, 1.0f, 1.0f, fade);
            drawQuad(vx - 4.0f * b.sf, y, 2.0f * b.sf, FONT_CHAR_H * ps,
                     1.0f, 1.0f, 1.0f, blink * fade);
        } else {
            int caret = mOsk.caret;
            if (caret < 0) caret = 0;
            if (caret > (int)value.size()) caret = (int)value.size();
            std::string before = value.substr(0, (size_t)caret);
            std::string after = value.substr((size_t)caret);
            float x = b.previewX + lw;
            if (!before.empty()) {
                drawText(before.c_str(), x, y, ps, 1.0f, 1.0f, 1.0f, fade);
                x += measureText(before.c_str(), ps);
            }
            if (!composing.empty()) {
                drawText(composing.c_str(), x, y, ps, 0.6f, 0.9f, 1.0f, fade);
                float uw = measureText(composing.c_str(), ps);
                drawQuad(x, y + FONT_CHAR_H * ps, uw, 2.0f * b.sf, 0.6f, 0.9f, 1.0f, 0.9f * fade);
                x += uw;
            }
            drawQuad(x, y, 2.0f * b.sf, FONT_CHAR_H * ps, 1.0f, 1.0f, 1.0f, blink * fade);
            if (!after.empty())
                drawText(after.c_str(), x + 2.0f * b.sf, y, ps, 1.0f, 1.0f, 1.0f, fade);
        }
    }

    // Language badge (top-right of the panel).
    {
        float ls = 1.5f * b.sf;
        float lw = measureText(mOsk.langLabel, ls);
        drawText(mOsk.langLabel, b.panelX + b.panelW - lw - 12.0f * b.sf,
                 b.previewY, ls, 0.45f, 0.78f, 1.0f, 0.9f * fade);
    }

    // Helper: draw a single key cap (rounded) + its label/glyph.
    auto drawKeyCap = [&](const OskKey& key, float x, float y, float w, float h,
                          bool focused, float dim) {
        float bx = x + keyGap, by = y + keyGap, bw = w - 2 * keyGap, bh = h - 2 * keyGap;
        bool isAccentShift = (key.code == OSK_SHIFT && mOsk.shift != SHIFT_OFF);
        // cap fill (remember the colour so the backspace icon's cut-out matches)
        float capR, capG, capB, capA;
        if (focused)            { capR = 0.96f; capG = 0.97f; capB = 0.99f; capA = 0.97f; }
        else if (isAccentShift) { capR = accR;  capG = accG;  capB = accB;  capA = 0.85f; }
        else                    { capR = kBgR;  capG = kBgG;  capB = kBgB;  capA = kBgA; }
        drawRoundedRect(bx, by, bw, bh, keyRad, capR, capG, capB, capA * dim * fade);
        // glyph for the space bar: a slim rounded bar
        if (key.glyph == GLYPH_SPACE) {
            float barW = bw * 0.5f, barH = 3.0f * b.sf;
            float tc = focused ? kFocTxtR : kTxtR;
            drawRoundedRect(bx + (bw - barW) / 2.0f, by + bh * 0.60f, barW, barH,
                            barH * 0.5f, tc, tc, tc, 0.9f * dim * fade);
            return;
        }
        std::string text;
        float scale;
        float tr, tg, tb;
        if (key.glyph != GLYPH_NONE) {
            text = (key.glyph == GLYPH_DELETE) ? std::string("\xc3\x97") // U+00D7 multiply -> backspace
                 : glyphFor(key.glyph, mOsk.shift);
            bool isMode = (key.glyph == GLYPH_SYMBOLS || key.glyph == GLYPH_ALPHABET);
            scale = (isMode ? 1.35f : 1.7f) * b.sf;
            if (isAccentShift) { tr = 1.0f; tg = 1.0f; tb = 1.0f; }
            else if (focused)  { tr = kFocTxtR; tg = kFocTxtG; tb = kFocTxtB; }
            else               { tr = kTxtR; tg = kTxtG; tb = kTxtB; }
        } else {
            text = key.label ? key.label : "";
            if (key.caseFoldable && mOsk.shift != SHIFT_OFF) text = utf8Upper(text);
            scale = (utf8Len(text) >= 2 ? 1.35f : 1.85f) * b.sf;
            if (focused) { tr = kFocTxtR; tg = kFocTxtG; tb = kFocTxtB; }
            else         { tr = kTxtR; tg = kTxtG; tb = kTxtB; }
        }
        // Real backspace icon: left-pointing arrowhead + body + a cut-out X.
        if (key.glyph == GLYPH_DELETE) {
            float cx = bx + bw / 2.0f, cy = by + bh / 2.0f, s = bh * 0.30f;
            drawTriangle(cx - s * 0.95f, cy, cx - s * 0.15f, cy - s * 0.78f,
                         cx - s * 0.15f, cy + s * 0.78f, tr, tg, tb, dim * fade);
            drawRoundedRect(cx - s * 0.15f, cy - s * 0.55f, s * 1.15f, s * 1.10f,
                            s * 0.22f, tr, tg, tb, dim * fade);
            float xs = 0.85f * b.sf;
            float xw = measureText("\xc3\x97", xs);
            drawText("\xc3\x97", cx + s * 0.18f - xw / 2.0f,
                     cy - FONT_CHAR_H * xs / 2.0f, xs, capR, capG, capB, dim * fade);
            return;
        }
        float tw = measureText(text.c_str(), scale);
        float th = FONT_CHAR_H * scale;
        drawText(text.c_str(), bx + (bw - tw) / 2.0f, by + (bh - th) / 2.0f,
                 scale, tr, tg, tb, dim * fade);
        // number-row superscript (US-style shifted symbol on digit keys)
        if (key.code >= '0' && key.code <= '9' && key.glyph == GLYPH_NONE) {
            static const char kSup[] = ")!@#$%^&*(";
            char sup[2] = { kSup[key.code - '0'], 0 };
            float ss = 1.0f * b.sf;
            float sw = measureText(sup, ss);
            drawText(sup, bx + bw - sw - 4.0f * b.sf, by + 3.0f * b.sf, ss,
                     focused ? kFocTxtR : 0.60f, focused ? kFocTxtG : 0.64f,
                     focused ? kFocTxtB : 0.72f, 0.85f * dim * fade);
        }
    };

    // --- Key caps (dark rounded) ---
    float dimAll = mOsk.miniOpen ? 0.28f : 1.0f;
    for (int r = 0; r < kb->rowCount; r++) {
        const OskRow& row = kb->rows[r];
        for (int c = 0; c < row.keyCount; c++) {
            const OskKey& key = row.keys[c];
            float x, y, w, h;
            keyRect(b, key.fx, key.fw, row.fy, row.fh, x, y, w, h);
            bool focused = (!mOsk.inAction && !mOsk.miniOpen &&
                            r == mOsk.focusRow && c == mOsk.focusCol);
            drawKeyCap(key, x, y, w, h, focused, dimAll);
        }
    }

    // --- Popup overlay (drawn on top of the dimmed grid) ---
    if (mOsk.miniOpen) {
        const OskPopup& p = kOskPopups[mOsk.miniPopupIndex];
        const OskRow& orow = kb->rows[mOsk.miniOriginRow];
        for (int i = 0; i < p.keyCount; i++) {
            const OskKey& cell = p.keys[i];
            float col = (float)(mOsk.miniBaseCol + i);
            float fx = col * (kKeyFrac + 0.028037f);
            float x, y, w, h;
            keyRect(b, fx, cell.fw, orow.fy, orow.fh, x, y, w, h);
            float bx = x + keyGap, by = y + keyGap, bw = w - 2 * keyGap, bh = h - 2 * keyGap;
            bool sel = (i == mOsk.miniFocus);
            if (sel) drawRoundedRect(bx, by, bw, bh, keyRad, 0.96f, 0.97f, 0.99f, 0.97f * fade);
            else     drawRoundedRect(bx, by, bw, bh, keyRad, 0.20f, 0.24f, 0.30f, 0.96f * fade);
            std::string text = cell.label ? cell.label : "";
            if (cell.caseFoldable && mOsk.shift != SHIFT_OFF) text = utf8Upper(text);
            float scale = 1.85f * b.sf;
            float tw = measureText(text.c_str(), scale);
            float th = FONT_CHAR_H * scale;
            if (sel) drawText(text.c_str(), bx + (bw - tw) / 2.0f, by + (bh - th) / 2.0f,
                              scale, kFocTxtR, kFocTxtG, kFocTxtB, fade);
            else     drawText(text.c_str(), bx + (bw - tw) / 2.0f, by + (bh - th) / 2.0f,
                              scale, kTxtR, kTxtG, kTxtB, fade);
        }
    }

    // --- Action button (Done / Search): accent rounded key ---
    {
        const char* actLabel = mOskPasswordMode ? "Done" : "Search";
        float scale = 1.6f * b.sf;
        bool foc = mOsk.inAction;
        if (foc) drawRoundedRect(b.actX, b.actY, b.actW, b.actH, keyRad,
                                 0.96f, 0.97f, 0.99f, 0.97f * fade);
        else     drawRoundedRect(b.actX, b.actY, b.actW, b.actH, keyRad,
                                 accR, accG, accB, 0.92f * fade);
        float tw = measureText(actLabel, scale);
        float th = FONT_CHAR_H * scale;
        if (foc) drawText(actLabel, b.actX + (b.actW - tw) / 2.0f, b.actY + (b.actH - th) / 2.0f,
                          scale, kFocTxtR, kFocTxtG, kFocTxtB, fade);
        else     drawText(actLabel, b.actX + (b.actW - tw) / 2.0f, b.actY + (b.actH - th) / 2.0f,
                          scale, 1.0f, 1.0f, 1.0f, fade);
    }

    // --- Footer / help line ---
    {
        float fScale = 1.35f * b.sf;
        const char* footer = mOskPasswordMode
            ? "A:Key  X:Back  L:Shift  R:Sym  Sel:Lang  Start:Submit  B:Cancel"
            : "A:Key  X:Back  L:Shift  R:Sym  Sel:Lang  Start:Search  B:Cancel";
        float fw = measureText(footer, fScale);
        drawText(footer, b.panelX + b.panelW / 2.0f - fw / 2.0f, b.footerY, fScale,
                 0.58f, 0.60f, 0.68f, 0.80f * fade);
    }
}

} // namespace android
