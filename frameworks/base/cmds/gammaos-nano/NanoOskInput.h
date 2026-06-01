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

// Concrete per-script OskInputMethod implementations. Header-only; included by
// the single OSK TU (NanoOsk.cpp).

#ifndef GAMMAOS_NANO_OSK_INPUT_H
#define GAMMAOS_NANO_OSK_INPUT_H

#include <string>
#include <vector>

#include <cstring>

#include "NanoOsk.h"        // OskInputMethod, OskBuffer
#include "NanoOskHangul.h"  // hangul::Composer
#include "NanoOskPinyin.h"  // kPySyl / kPyHanzi pinyin -> Hanzi table
#include "NanoOskKana.h"    // kOskKana romaji -> hiragana table
#include "NanoOskKanji.h"   // kKjEntry hiragana-reading -> kanji candidates

namespace android {

// Korean Hangul (dubeolsik). Composes jamo into precomposed syllables via the
// algorithmic automaton. Deterministic: no candidate list.
class HangulInput : public OskInputMethod {
public:
    OskScriptDir direction() const override { return OSK_LTR; }
    bool wantsCandidates() const override { return false; }

    bool onCodepoint(uint32_t cp, OskBuffer& buf) override {
        std::string committed = composer_.input(cp);
        if (!committed.empty()) insertAtCaret(buf, committed);
        return true;  // always consumed (still composing, or just committed)
    }
    bool onBackspace(OskBuffer& /*buf*/) override {
        if (composer_.isComposing()) { composer_.backspace(); return true; }
        return false; // nothing composing -> keyboard deletes from the buffer
    }
    const std::vector<std::string>& candidates() const override { return empty_; }
    void chooseCandidate(int, OskBuffer&) override {}
    std::string composingText() const override { return composer_.composing(); }
    void commitComposing(OskBuffer& buf) override {
        std::string c = composer_.flush();
        if (!c.empty()) insertAtCaret(buf, c);
    }
    void reset() override { composer_.reset(); }

private:
    static void insertAtCaret(OskBuffer& buf, const std::string& s) {
        int c = *buf.caret;
        if (c < 0) c = 0;
        if (c > (int)buf.text->size()) c = (int)buf.text->size();
        buf.text->insert((size_t)c, s);
        *buf.caret = c + (int)s.size();
    }
    hangul::Composer composer_;
    std::vector<std::string> empty_;
};

// Chinese Pinyin. The user types toneless pinyin on a Latin QWERTY; letters
// accumulate into a composing string and we surface Hanzi candidates from the
// generated NanoOskPinyin.h table (longest valid syllable that is a prefix of
// what has been typed). Choosing a candidate commits that Hanzi and consumes
// just its syllable's letters, so multi-syllable input flows naturally
// (e.g. "nihao" -> pick 你 -> remaining "hao" -> pick 好). No phrase model:
// candidates are single characters, frequency-ordered.
class PinyinInput : public OskInputMethod {
public:
    OskScriptDir direction() const override { return OSK_LTR; }
    bool wantsCandidates() const override { return true; }

    bool onCodepoint(uint32_t cp, OskBuffer& buf) override {
        if (cp >= 'A' && cp <= 'Z') cp += 32;          // fold to lowercase
        if (cp >= 'a' && cp <= 'z') {
            if (composing_.size() < 24) composing_ += (char)cp;
            recompute();
            return true;                               // consumed into composing
        }
        // A non-letter while composing: commit the best candidate first, then let
        // the keyboard insert the non-letter (digit / punctuation) after it.
        if (!composing_.empty()) {
            commitBest(buf);
            recompute();
        }
        return false;
    }
    bool onBackspace(OskBuffer& /*buf*/) override {
        if (composing_.empty()) return false;          // keyboard deletes buffer
        composing_.pop_back();
        recompute();
        return true;
    }
    const std::vector<std::string>& candidates() const override { return candidates_; }
    void chooseCandidate(int index, OskBuffer& buf) override {
        int used = 0;
        const OskPySyl* s = longestPrefix(composing_, used);
        if (!s || index < 0 || index >= s->count) return;
        insertAtCaret(buf, hanziUtf8(kPyHanzi[s->off + index]));
        composing_.erase(0, (size_t)used);             // consume that syllable
        recompute();
    }
    std::string composingText() const override { return composing_; }
    void commitComposing(OskBuffer& buf) override {
        // Greedily commit the top candidate of each syllable; if a remainder has
        // no match, insert it as raw pinyin so nothing is silently lost.
        while (!composing_.empty()) {
            int used = 0;
            const OskPySyl* s = longestPrefix(composing_, used);
            if (!s) { insertAtCaret(buf, composing_); composing_.clear(); break; }
            insertAtCaret(buf, hanziUtf8(kPyHanzi[s->off]));
            composing_.erase(0, (size_t)used);
        }
        candidates_.clear();
    }
    void reset() override { composing_.clear(); candidates_.clear(); }

private:
    static std::string hanziUtf8(uint32_t cp) {        // BMP CJK -> 3-byte UTF-8
        std::string s;
        s += (char)(0xE0 | (cp >> 12));
        s += (char)(0x80 | ((cp >> 6) & 0x3F));
        s += (char)(0x80 | (cp & 0x3F));
        return s;
    }
    // Exact-match binary search for the first `len` chars of `s` in kPySyl
    // (which is sorted by syllable).
    static const OskPySyl* findSyl(const char* s, size_t len) {
        int lo = 0, hi = kPySylCount - 1;
        while (lo <= hi) {
            int m = (lo + hi) / 2;
            int c = std::strncmp(kPySyl[m].syl, s, len);
            if (c == 0) {
                size_t mlen = std::strlen(kPySyl[m].syl);
                if (mlen == len) return &kPySyl[m];
                c = (mlen < len) ? -1 : 1;             // shorter sorts first
            }
            if (c < 0) lo = m + 1; else hi = m - 1;
        }
        return nullptr;
    }
    // Longest valid syllable that is a prefix of `py` (max pinyin syllable = 6).
    static const OskPySyl* longestPrefix(const std::string& py, int& used) {
        int n = (int)py.size();
        if (n > 6) n = 6;
        for (int len = n; len >= 1; len--) {
            const OskPySyl* s = findSyl(py.c_str(), (size_t)len);
            if (s) { used = len; return s; }
        }
        used = 0;
        return nullptr;
    }
    void recompute() {
        candidates_.clear();
        if (composing_.empty()) return;
        int used = 0;
        const OskPySyl* s = longestPrefix(composing_, used);
        if (!s) return;
        for (int i = 0; i < s->count; i++)
            candidates_.push_back(hanziUtf8(kPyHanzi[s->off + i]));
    }
    void commitBest(OskBuffer& buf) {
        int used = 0;
        const OskPySyl* s = longestPrefix(composing_, used);
        if (s) { insertAtCaret(buf, hanziUtf8(kPyHanzi[s->off])); composing_.erase(0, (size_t)used); }
        else   { insertAtCaret(buf, composing_); composing_.clear(); }
    }
    static void insertAtCaret(OskBuffer& buf, const std::string& s) {
        int c = *buf.caret;
        if (c < 0) c = 0;
        if (c > (int)buf.text->size()) c = (int)buf.text->size();
        buf.text->insert((size_t)c, s);
        *buf.caret = c + (int)s.size();
    }
    std::string composing_;
    std::vector<std::string> candidates_;
};

// Japanese romaji -> hiragana -> kanji. The romaji is converted to a hiragana
// READING (wapuro rules: sokuon from a doubled consonant, syllabic n); that
// reading stays in the composing region and the candidate bar offers its kanji
// conversions (from the SKK-derived kKjEntry table) plus the plain hiragana and
// its katakana. Choosing a candidate commits it; committing without choosing
// (space/enter) commits the plain hiragana. No morphological analysis: lookup
// is whole-reading, like the Pinyin engine.
class JapaneseInput : public OskInputMethod {
public:
    OskScriptDir direction() const override { return OSK_LTR; }
    bool wantsCandidates() const override { return true; }

    bool onCodepoint(uint32_t cp, OskBuffer& buf) override {
        if (cp >= 'A' && cp <= 'Z') cp += 32;
        if (cp >= 'a' && cp <= 'z') {
            if (pending_.size() < 16) pending_ += (char)cp;
            processKana();
            recompute();
            return true;
        }
        // Non-letter: commit the plain reading first, then keyboard inserts cp.
        if (!reading_.empty() || !pending_.empty()) commitComposing(buf);
        return false;
    }
    bool onBackspace(OskBuffer& /*buf*/) override {
        if (!pending_.empty()) { pending_.pop_back(); recompute(); return true; }
        if (!reading_.empty()) { popLastUtf8(reading_); recompute(); return true; }
        return false;
    }
    const std::vector<std::string>& candidates() const override { return candidates_; }
    void chooseCandidate(int index, OskBuffer& buf) override {
        if (index < 0 || index >= (int)candidates_.size()) return;
        insertAtCaret(buf, candidates_[index]);
        reading_.clear(); pending_.clear(); candidates_.clear();
    }
    std::string composingText() const override {
        // A trailing lone 'n' shows as a provisional ん (resolves if a vowel
        // follows); other un-converted romaji shows as-is.
        if (pending_ == "n") return reading_ + "\xe3\x82\x93";
        return reading_ + pending_;
    }
    void commitComposing(OskBuffer& buf) override {
        flushPending();
        if (!reading_.empty()) insertAtCaret(buf, reading_);   // plain hiragana
        reading_.clear(); candidates_.clear();
    }
    void reset() override { reading_.clear(); pending_.clear(); candidates_.clear(); }

private:
    static bool isVowel(char c) { return c=='a'||c=='i'||c=='u'||c=='e'||c=='o'; }
    static bool isCons(char c)  { return c>='a' && c<='z' && !isVowel(c); }
    static const char* exactKana(const char* s, size_t len) {
        int lo = 0, hi = kOskKanaCount - 1;
        while (lo <= hi) {
            int m = (lo + hi) / 2;
            int c = std::strncmp(kOskKana[m].romaji, s, len);
            if (c == 0) {
                size_t ml = std::strlen(kOskKana[m].romaji);
                if (ml == len) return kOskKana[m].kana;
                c = (ml < len) ? -1 : 1;
            }
            if (c < 0) lo = m + 1; else hi = m - 1;
        }
        return nullptr;
    }
    static bool isPrefixOfLonger(const std::string& r) {
        for (int i = 0; i < kOskKanaCount; i++) {
            const char* k = kOskKana[i].romaji;
            if (std::strlen(k) > r.size() &&
                std::strncmp(k, r.c_str(), r.size()) == 0) return true;
        }
        return false;
    }
    // Convert as much of pending_ as is unambiguous into hiragana appended to
    // reading_. (Same wapuro state machine as before, output redirected.)
    void processKana() {
        for (;;) {
            if (pending_.empty()) return;
            if (pending_.size() >= 2 && pending_[0] == pending_[1] &&
                isCons(pending_[0]) && pending_[0] != 'n') {
                reading_ += "\xe3\x81\xa3";              // っ
                pending_.erase(0, 1); continue;
            }
            if (pending_[0] == 'n' && pending_.size() >= 2) {
                char b = pending_[1];
                if (b == 'n' || (isCons(b) && b != 'y')) {
                    reading_ += "\xe3\x82\x93";          // ん
                    pending_.erase(0, 1); continue;
                }
            }
            if (isPrefixOfLonger(pending_)) return;
            int maxL = (int)pending_.size(); if (maxL > 4) maxL = 4;
            bool conv = false;
            for (int L = maxL; L >= 1; L--) {
                const char* k = exactKana(pending_.c_str(), (size_t)L);
                if (k) { reading_ += k; pending_.erase(0, (size_t)L); conv = true; break; }
            }
            if (conv) continue;
            if (!isPrefixOfLonger(pending_.substr(0, 1)) && !exactKana(pending_.c_str(), 1)) {
                reading_ += pending_.substr(0, 1);
                pending_.erase(0, 1); continue;
            }
            return;
        }
    }
    void flushPending() {
        processKana();
        if (pending_ == "n") { reading_ += "\xe3\x82\x93"; pending_.clear(); }  // ん
        else if (!pending_.empty()) { reading_ += pending_; pending_.clear(); }
    }
    // hiragana reading -> katakana (U+3041..3096 shift +0x60; pass others through).
    static std::string toKatakana(const std::string& s) {
        std::string out; out.reserve(s.size());
        for (size_t i = 0; i < s.size(); ) {
            unsigned char c0 = (unsigned char)s[i];
            if ((c0 & 0xF0) == 0xE0 && i + 2 < s.size()) {
                uint32_t cp = ((c0 & 0x0F) << 12) |
                              (((unsigned char)s[i+1] & 0x3F) << 6) |
                              ((unsigned char)s[i+2] & 0x3F);
                if (cp >= 0x3041 && cp <= 0x3096) cp += 0x60;
                out += (char)(0xE0 | (cp >> 12));
                out += (char)(0x80 | ((cp >> 6) & 0x3F));
                out += (char)(0x80 | (cp & 0x3F));
                i += 3;
            } else { out += s[i]; i++; }
        }
        return out;
    }
    static const OskKjEntry* findReading(const std::string& r) {
        int lo = 0, hi = kKjEntryCount - 1;
        while (lo <= hi) {
            int m = (lo + hi) / 2;
            int c = std::strcmp(kKjEntry[m].reading, r.c_str());
            if (c == 0) return &kKjEntry[m];
            if (c < 0) lo = m + 1; else hi = m - 1;
        }
        return nullptr;
    }
    void recompute() {
        candidates_.clear();
        // Effective reading includes a trailing lone 'n' as a provisional ん so
        // words like "nihon" (にほん) convert before the n is fully resolved.
        std::string eff = reading_;
        if (pending_ == "n") eff += "\xe3\x82\x93";
        if (eff.empty()) return;
        const OskKjEntry* e = findReading(eff);         // kanji conversions first
        if (e)
            for (uint16_t k = 0; k < e->candCount; k++)
                candidates_.push_back(kKjCand[e->candOff + k]);
        candidates_.push_back(eff);                     // plain hiragana
        std::string kata = toKatakana(eff);             // katakana
        if (kata != eff) candidates_.push_back(kata);
    }
    static void popLastUtf8(std::string& s) {
        int i = (int)s.size() - 1;
        while (i > 0 && ((unsigned char)s[i] & 0xC0) == 0x80) i--;
        if (i >= 0) s.erase((size_t)i);
    }
    static void insertAtCaret(OskBuffer& buf, const std::string& s) {
        int c = *buf.caret;
        if (c < 0) c = 0;
        if (c > (int)buf.text->size()) c = (int)buf.text->size();
        buf.text->insert((size_t)c, s);
        *buf.caret = c + (int)s.size();
    }
    std::string reading_;     // confirmed hiragana (the reading being converted)
    std::string pending_;     // un-converted romaji tail
    std::vector<std::string> candidates_;
};

} // namespace android

#endif // GAMMAOS_NANO_OSK_INPUT_H
