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

// Korean Hangul 2-set (dubeolsik) composition automaton. Hangul input is
// ALGORITHMIC (no dictionary): leading consonant (choseong) + vowel
// (jungseong) + optional trailing consonant (jongseong) compose into a
// precomposed syllable U+AC00 + (cho*21 + jung)*28 + jong.
//
// The composer consumes compatibility-jamo codepoints (U+3131..U+3163, what
// the dubeolsik keys emit) and yields committed UTF-8 text plus a live
// composing syllable. Header-only so the host unit test can exercise it
// without the Android build.

#ifndef GAMMAOS_NANO_OSK_HANGUL_H
#define GAMMAOS_NANO_OSK_HANGUL_H

#include <cstdint>
#include <string>

namespace android {
namespace hangul {

// Classification of one compatibility jamo, indexed by (cp - 0x3131), 0..50.
// cho/jong are the choseong/jongseong indices for a consonant (-1 if it has no
// such form); jung is the jungseong index for a vowel (-1 if not a vowel).
struct JamoInfo { int cho; int jong; int jung; };

inline const JamoInfo* jamoTable() {
    // 51 entries for U+3131..U+3163. cho: 0..18, jong: 0..27 (0=none unused
    // here, -1 means "no jongseong form"), jung: 0..20.
    static const JamoInfo t[51] = {
        /*3131 g  */ { 0,  1, -1}, /*3132 gg */ { 1,  2, -1},
        /*3133 gs */ {-1,  3, -1}, /*3134 n  */ { 2,  4, -1},
        /*3135 nj */ {-1,  5, -1}, /*3136 nh */ {-1,  6, -1},
        /*3137 d  */ { 3,  7, -1}, /*3138 dd */ { 4, -1, -1},
        /*3139 r  */ { 5,  8, -1}, /*313A rg */ {-1,  9, -1},
        /*313B rm */ {-1, 10, -1}, /*313C rb */ {-1, 11, -1},
        /*313D rs */ {-1, 12, -1}, /*313E rt */ {-1, 13, -1},
        /*313F rp */ {-1, 14, -1}, /*3140 rh */ {-1, 15, -1},
        /*3141 m  */ { 6, 16, -1}, /*3142 b  */ { 7, 17, -1},
        /*3143 bb */ { 8, -1, -1}, /*3144 bs */ {-1, 18, -1},
        /*3145 s  */ { 9, 19, -1}, /*3146 ss */ {10, 20, -1},
        /*3147 ng */ {11, 21, -1}, /*3148 j  */ {12, 22, -1},
        /*3149 jj */ {13, -1, -1}, /*314A c  */ {14, 23, -1},
        /*314B k  */ {15, 24, -1}, /*314C t  */ {16, 25, -1},
        /*314D p  */ {17, 26, -1}, /*314E h  */ {18, 27, -1},
        /*314F a  */ {-1, -1,  0}, /*3150 ae */ {-1, -1,  1},
        /*3151 ya */ {-1, -1,  2}, /*3152 yae*/ {-1, -1,  3},
        /*3153 eo */ {-1, -1,  4}, /*3154 e  */ {-1, -1,  5},
        /*3155 yeo*/ {-1, -1,  6}, /*3156 ye */ {-1, -1,  7},
        /*3157 o  */ {-1, -1,  8}, /*3158 wa */ {-1, -1,  9},
        /*3159 wae*/ {-1, -1, 10}, /*315A oe */ {-1, -1, 11},
        /*315B yo */ {-1, -1, 12}, /*315C u  */ {-1, -1, 13},
        /*315D weo*/ {-1, -1, 14}, /*315E we */ {-1, -1, 15},
        /*315F wi */ {-1, -1, 16}, /*3160 yu */ {-1, -1, 17},
        /*3161 eu */ {-1, -1, 18}, /*3162 yi */ {-1, -1, 19},
        /*3163 i  */ {-1, -1, 20},
    };
    return t;
}

// Compose two jungseong (vowel) indices into a compound, or -1.
inline int combineJung(int a, int b) {
    if (a == 8  && b == 0)  return 9;   // o + a   -> wa
    if (a == 8  && b == 1)  return 10;  // o + ae  -> wae
    if (a == 8  && b == 20) return 11;  // o + i   -> oe
    if (a == 13 && b == 4)  return 14;  // u + eo  -> weo
    if (a == 13 && b == 5)  return 15;  // u + e   -> we
    if (a == 13 && b == 20) return 16;  // u + i   -> wi
    if (a == 18 && b == 20) return 19;  // eu + i  -> yi
    return -1;
}

// Compose current jongseong with an added consonant's single jongseong index
// into a compound jongseong, or -1.
inline int combineJong(int cur, int add) {
    if (cur == 1  && add == 19) return 3;   // g + s   -> gs
    if (cur == 4  && add == 22) return 5;   // n + j   -> nj
    if (cur == 4  && add == 27) return 6;   // n + h   -> nh
    if (cur == 8  && add == 1)  return 9;   // r + g   -> rg
    if (cur == 8  && add == 16) return 10;  // r + m   -> rm
    if (cur == 8  && add == 17) return 11;  // r + b   -> rb
    if (cur == 8  && add == 19) return 12;  // r + s   -> rs
    if (cur == 8  && add == 25) return 13;  // r + t   -> rt
    if (cur == 8  && add == 26) return 14;  // r + p   -> rp
    if (cur == 8  && add == 27) return 15;  // r + h   -> rh
    if (cur == 17 && add == 19) return 18;  // b + s   -> bs
    return -1;
}

// Decompose a jongseong into the part that stays as a trailing consonant and
// the choseong that splits off to start a new syllable when a vowel follows.
struct JongSplit { int remainJong; int movedCho; };
inline JongSplit splitJong(int jong) {
    switch (jong) {
        case 3:  return { 1,  9};  // gs -> g + s
        case 5:  return { 4, 12};  // nj -> n + j
        case 6:  return { 4, 18};  // nh -> n + h
        case 9:  return { 8,  0};  // rg -> r + g
        case 10: return { 8,  6};  // rm -> r + m
        case 11: return { 8,  7};  // rb -> r + b
        case 12: return { 8,  9};  // rs -> r + s
        case 13: return { 8, 16};  // rt -> r + t
        case 14: return { 8, 17};  // rp -> r + p
        case 15: return { 8, 18};  // rh -> r + h
        case 18: return {17,  9};  // bs -> b + s
        // simple jongseong -> moves entirely to choseong
        case 1:  return {0, 0};  case 2:  return {0, 1};  case 4:  return {0, 2};
        case 7:  return {0, 3};  case 8:  return {0, 5};  case 16: return {0, 6};
        case 17: return {0, 7};  case 19: return {0, 9};  case 20: return {0, 10};
        case 21: return {0, 11}; case 22: return {0, 12}; case 23: return {0, 14};
        case 24: return {0, 15}; case 25: return {0, 16}; case 26: return {0, 17};
        case 27: return {0, 18};
        default: return {0, -1};
    }
}

inline std::string encodeUtf8(uint32_t cp) {
    std::string s;
    if (cp < 0x80) s += (char)cp;
    else if (cp < 0x800) { s += (char)(0xC0 | (cp >> 6)); s += (char)(0x80 | (cp & 0x3F)); }
    else { s += (char)(0xE0 | (cp >> 12)); s += (char)(0x80 | ((cp >> 6) & 0x3F)); s += (char)(0x80 | (cp & 0x3F)); }
    return s;
}

// Compatibility-jamo codepoint for a lone choseong index (to display/commit a
// leading consonant that has no vowel yet).
inline uint32_t choToCompat(int cho) {
    static const uint32_t t[19] = {
        0x3131, 0x3132, 0x3134, 0x3137, 0x3138, 0x3139, 0x3141, 0x3142,
        0x3143, 0x3145, 0x3146, 0x3147, 0x3148, 0x3149, 0x314A, 0x314B,
        0x314C, 0x314D, 0x314E,
    };
    return (cho >= 0 && cho < 19) ? t[cho] : 0;
}

class Composer {
public:
    void reset() { cho_ = -1; jung_ = -1; jong_ = 0; }
    bool isComposing() const { return cho_ >= 0 || jung_ >= 0; }

    // Feed one jamo codepoint. Returns committed UTF-8 text (0+ codepoints).
    std::string input(uint32_t jamo) {
        if (jamo < 0x3131 || jamo > 0x3163) {
            // Not a jamo: flush the composing syllable, then pass through.
            std::string out = flush();
            out += encodeUtf8(jamo);
            return out;
        }
        const JamoInfo& j = jamoTable()[jamo - 0x3131];
        std::string out;
        if (j.jung >= 0) {
            // Vowel.
            if (cho_ < 0 && jung_ < 0) {
                // Lone vowel: commit as a standalone jamo.
                out += encodeUtf8(jamo);
            } else if (jung_ < 0) {
                jung_ = j.jung;                    // cho + vowel
            } else if (jong_ == 0) {
                int c = combineJung(jung_, j.jung);
                if (c >= 0) {
                    jung_ = c;                     // compound vowel
                } else {
                    out += flush();
                    out += encodeUtf8(jamo);       // can't extend -> lone vowel
                }
            } else {
                // Trailing consonant splits off to lead the next syllable.
                JongSplit sp = splitJong(jong_);
                int movedCho = sp.movedCho;
                jong_ = sp.remainJong;
                out += commitCurrent();
                cho_ = movedCho; jung_ = j.jung; jong_ = 0;
            }
            return out;
        }
        // Consonant.
        if (cho_ < 0) {
            cho_ = j.cho;                           // start a syllable
            if (cho_ < 0) out += encodeUtf8(jamo);  // jong-only jamo, pass through
        } else if (jung_ < 0) {
            // Two consonants with no vowel: commit the first, start the second.
            out += flush();
            cho_ = j.cho;
            if (cho_ < 0) out += encodeUtf8(jamo);
        } else if (jong_ == 0) {
            if (j.jong >= 0) jong_ = j.jong;        // attach as trailing consonant
            else { out += flush(); cho_ = j.cho; }  // (dd/bb/jj have no jong form)
        } else {
            int c = (j.jong >= 0) ? combineJong(jong_, j.jong) : -1;
            if (c >= 0) {
                jong_ = c;                          // compound trailing consonant
            } else {
                out += flush();                     // start a fresh syllable
                cho_ = j.cho;
                if (cho_ < 0) out += encodeUtf8(jamo);
            }
        }
        return out;
    }

    // The in-progress syllable as UTF-8 (empty if nothing composing).
    std::string composing() const {
        if (cho_ >= 0 && jung_ >= 0)
            return encodeUtf8(0xAC00 + (cho_ * 21 + jung_) * 28 + jong_);
        if (cho_ >= 0) return encodeUtf8(choToCompat(cho_));
        if (jung_ >= 0) return encodeUtf8(0x314F + jung_); // bare vowel (rare)
        return std::string();
    }

    // Remove the most recent jamo from the composing syllable.
    bool backspace() {
        if (jong_ != 0) {
            JongSplit sp = splitJong(jong_);
            // If compound, drop the moved part back to the remaining jong.
            jong_ = (sp.movedCho >= 0 && sp.remainJong != 0) ? sp.remainJong : 0;
            return true;
        }
        if (jung_ >= 0) { jung_ = -1; return true; }
        if (cho_ >= 0)  { cho_ = -1;  return true; }
        return false;
    }

    std::string flush() {
        std::string out = commitCurrent();
        reset();
        return out;
    }

private:
    std::string commitCurrent() const {
        if (cho_ >= 0 && jung_ >= 0)
            return encodeUtf8(0xAC00 + (cho_ * 21 + jung_) * 28 + jong_);
        if (cho_ >= 0) return encodeUtf8(choToCompat(cho_));
        if (jung_ >= 0) return encodeUtf8(0x314F + jung_);
        return std::string();
    }

    int cho_ = -1, jung_ = -1, jong_ = 0;
};

} // namespace hangul
} // namespace android

#endif // GAMMAOS_NANO_OSK_HANGUL_H
