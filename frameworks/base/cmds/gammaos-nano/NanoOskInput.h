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

// Japanese romaji -> hiragana (wapuro). Deterministic, no candidate list (like
// HangulInput): completed kana commit straight into the buffer and the pending
// un-converted romaji tail is the composing text. Layers sokuon (small tsu from
// a doubled consonant) and syllabic-n on top of the kOskKana table. Kanji
// conversion is out of scope (no Japanese dictionary in the tree), so output is
// hiragana, which is valid Japanese text.
class KanaInput : public OskInputMethod {
public:
    OskScriptDir direction() const override { return OSK_LTR; }
    bool wantsCandidates() const override { return false; }

    bool onCodepoint(uint32_t cp, OskBuffer& buf) override {
        if (cp >= 'A' && cp <= 'Z') cp += 32;
        bool romaji = (cp >= 'a' && cp <= 'z') || cp == '-' || cp == '.' || cp == ',';
        if (romaji) {
            if (pending_.size() < 16) pending_ += (char)cp;
            process(buf);
            return true;
        }
        if (!pending_.empty()) commitComposing(buf);   // flush, then keyboard inserts cp
        return false;
    }
    bool onBackspace(OskBuffer& /*buf*/) override {
        if (pending_.empty()) return false;
        pending_.pop_back();
        return true;
    }
    const std::vector<std::string>& candidates() const override { return empty_; }
    void chooseCandidate(int, OskBuffer&) override {}
    std::string composingText() const override { return pending_; }
    void commitComposing(OskBuffer& buf) override {
        process(buf);
        if (pending_ == "n") { insertAtCaret(buf, "\xe3\x82\x93"); pending_.clear(); }  // lone n -> ん
        else if (!pending_.empty()) { insertAtCaret(buf, pending_); pending_.clear(); }
    }
    void reset() override { pending_.clear(); }

private:
    static bool isVowel(char c) { return c=='a'||c=='i'||c=='u'||c=='e'||c=='o'; }
    static bool isCons(char c)  { return c>='a' && c<='z' && !isVowel(c); }
    // Exact-match binary search for the first `len` chars of `s` in kOskKana.
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
    // Any table romaji strictly longer than r that begins with r? (=> wait.)
    static bool isPrefixOfLonger(const std::string& r) {
        for (int i = 0; i < kOskKanaCount; i++) {
            const char* k = kOskKana[i].romaji;
            if (std::strlen(k) > r.size() &&
                std::strncmp(k, r.c_str(), r.size()) == 0) return true;
        }
        return false;
    }
    void process(OskBuffer& buf) {
        for (;;) {
            if (pending_.empty()) return;
            // Sokuon: a doubled consonant (not n) -> っ, then drop the first.
            if (pending_.size() >= 2 && pending_[0] == pending_[1] &&
                isCons(pending_[0]) && pending_[0] != 'n') {
                insertAtCaret(buf, "\xe3\x81\xa3");        // っ
                pending_.erase(0, 1); continue;
            }
            // Syllabic n: 'n' + (consonant != y) or 'nn' -> ん, drop one n.
            if (pending_[0] == 'n' && pending_.size() >= 2) {
                char b = pending_[1];
                if (b == 'n' || (isCons(b) && b != 'y')) {
                    insertAtCaret(buf, "\xe3\x82\x93");    // ん
                    pending_.erase(0, 1); continue;
                }
            }
            // If the pending romaji could still grow into a longer kana, wait.
            if (isPrefixOfLonger(pending_)) return;
            // Convert the longest table-prefix (max kana romaji length is 4).
            int maxL = (int)pending_.size(); if (maxL > 4) maxL = 4;
            bool conv = false;
            for (int L = maxL; L >= 1; L--) {
                const char* k = exactKana(pending_.c_str(), (size_t)L);
                if (k) { insertAtCaret(buf, k); pending_.erase(0, (size_t)L); conv = true; break; }
            }
            if (conv) continue;
            // First char can't start any kana -> emit it raw to avoid a stall.
            if (!isPrefixOfLonger(pending_.substr(0, 1)) && !exactKana(pending_.c_str(), 1)) {
                insertAtCaret(buf, pending_.substr(0, 1));
                pending_.erase(0, 1); continue;
            }
            return;   // it is a prefix of a kana; wait for more input
        }
    }
    static void insertAtCaret(OskBuffer& buf, const std::string& s) {
        int c = *buf.caret;
        if (c < 0) c = 0;
        if (c > (int)buf.text->size()) c = (int)buf.text->size();
        buf.text->insert((size_t)c, s);
        *buf.caret = c + (int)s.size();
    }
    std::string pending_;
    std::vector<std::string> empty_;
};

} // namespace android

#endif // GAMMAOS_NANO_OSK_INPUT_H
