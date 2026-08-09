/*
 * Copyright (C) 2026 GammaOS
 */

// RetroAchievements panel rendering and interaction for the in-game overlay,
// split out of OverlayMenu.cpp to keep that file focused on the settings menu
// (mirrors the gammaos-nano NanoMenu* multi-file split). These are member
// functions of OverlayMenu: the bottom-screen detail/leaderboards panel and
// its touch handling, the in-gameplay challenge/progress indicators, the badge
// texture cache, and the single-screen controller-driven RA drill-in.

#define LOG_TAG "DrasticNano.Overlay"

#include "OverlayMenu.h"
#include "NanoRetroAchievements.h"
#include "NanoI18n.h"   // trDyn() for translatable header / menu labels

#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <ctime>
#include <map>
#include <vector>

#include <cutils/properties.h>
#include <utils/SystemClock.h>

namespace android {
namespace drastic_overlay {

using drastic_gfx::Color;
using drastic_gfx::rgba;

// ---------------------------------------------------------------------------
// Bottom-screen RetroAchievements detail + leaderboards panel
// ---------------------------------------------------------------------------

bool OverlayMenu::wantsRaBottomPanel() const {
    // Single-screen devices render the RA detail/leaderboards on the one screen
    // via the drill-in (drawRaSingle), not a second-screen panel.
    return mOpen && mSection == kSec_Achievements && mRa &&
           mRa->isLoggedIn() && !mOsk.active() && !mSingleScreen;
}

void OverlayMenu::drawBottomScrim(drastic_gfx::OverlayGfx& gfx) {
    using drastic_gfx::rgba;
    gfx.fillRect(0, 0, (float)gfx.viewportW(), (float)gfx.viewportH(),
                 rgba(0, 0, 0, 0.72f));
}

void OverlayMenu::drawRaIndicators(drastic_gfx::OverlayGfx& gfx, float /*sf*/) {
    using drastic_gfx::rgba;
    // Only over the running game; while the overlay menu is open it has its own
    // UI (and the achievement list shows the same Measured/Trigger state).
    if (mOpen) return;
    if (mRaChallenge.empty() && (!mRaProgressId || mRaProgressText.empty())) return;

    const float vw     = (float) gfx.viewportW();
    const float vh     = (float) gfx.viewportH();
    const float lineH  = (float) gfx.fontLineH();
    const float basePx = (float) gfx.fontBasePx();
    auto scaleFor = [&](float px) { return px / basePx; };
    const int64_t now = android::elapsedRealtime();

    // Challenge (Trigger) indicators: a stacked column of the currently primed
    // achievements' badges at the left edge, so the player sees which are active.
    // Gated by the "Challenge Indicators" toggle (rebuildAchievements); the
    // progress toast and the top-right unlock banner are independent. Default on.
    if (!mRaChallenge.empty() &&
        property_get_bool("persist.gammaos.drastic_nano.ra_show_challenge_badges", true)) {
        const float sz  = floorf(lineH * 1.7f);
        const float gap = floorf(sz * 0.18f);
        const float x   = floorf(vw * 0.012f);
        float y         = floorf(vh * 0.16f);
        for (const auto& kv : mRaChallenge) {
            gfx.roundedRect(x - 2.0f, y - 2.0f, sz + 4.0f, sz + 4.0f, sz * 0.22f,
                            rgba(0.05f, 0.06f, 0.10f, 0.55f));
            unsigned tex = raBadgeTex(kv.first, gfx);
            if (tex) gfx.drawImage(tex, x, y, sz, sz, 0.95f);
            y += sz + gap;
            if (y + sz > vh * 0.92f) break;   // cap the column
        }
    }

    // Progress (Measured) indicator: a brief centred popup near the bottom with
    // the badge and the measured value (e.g. "Collect 50 rings    23/50").
    // Gated by the "Achievement Progress Toast" toggle (rebuildAchievements);
    // the challenge badges above and the top-right unlock banner are separate and
    // stay on. Default on.
    if (mRaProgressId && now < mRaProgressUntilMs && !mRaProgressText.empty() &&
        property_get_bool("persist.gammaos.drastic_nano.ra_show_progress_toast", true)) {
        const float pad   = fmaxf(5.0f, lineH * 0.40f);
        const float sz    = lineH * 1.55f;
        const float txtPx = lineH * 0.62f;
        const float txtScale = scaleFor(txtPx);
        // Size the scrim to the MEASURED text (badge + text + padding) instead of
        // a fixed width, so long achievement titles are fully covered. Clamp to
        // the panel width; when the text still will not fit, it scrolls (below).
        const float measuredW = gfx.measure(mRaProgressText.c_str(), txtScale);
        const float maxBoxW = vw * 0.94f;
        float boxW = sz + pad * 3.0f + measuredW;
        if (boxW > maxBoxW)          boxW = maxBoxW;
        if (boxW < sz + pad * 2.0f)  boxW = sz + pad * 2.0f;
        const float boxH  = sz + pad * 2.0f;
        const float x     = floorf((vw - boxW) * 0.5f);
        const float y     = floorf(vh * 0.80f);
        float a = 1.0f;
        const int64_t left = mRaProgressUntilMs - now;
        if (left < 400) a = (float) left / 400.0f;     // fade-out tail
        gfx.roundedRect(x, y, boxW, boxH, lineH * 0.35f,
                        rgba(0.05f, 0.06f, 0.10f, a * 0.92f));
        gfx.roundedRect(x, y, boxW, 3.0f, 1.5f, rgba(0.36f, 0.62f, 0.96f, a));
        const float bx = x + pad;
        const float by = y + (boxH - sz) * 0.5f;
        unsigned tex = raBadgeTex(mRaProgressId, gfx);
        if (tex) gfx.drawImage(tex, bx, by, sz, sz, a);
        else     gfx.roundedRect(bx, by, sz, sz, sz * 0.2f,
                                 rgba(0.16f, 0.17f, 0.22f, a));
        const float tx = bx + sz + pad;
        const float ty = y + (boxH - txtPx) * 0.5f;
        // When the text overflows the (clamped) box, marquee it: draw only the
        // substring that fits, sliding over time. There is no scissor API, so the
        // visible window IS the substring (same technique as the bottom-panel
        // leaderboard marquee).
        const float availTextW = (x + boxW - pad) - tx;
        std::string shown = mRaProgressText;
        if (availTextW > 0.0f && measuredW > availTextW) {
            const std::string& s = mRaProgressText;
            const int nch = (int)s.size();
            int maxStart = nch - 1;
            for (int st = 0; st < nch; st++)
                if (gfx.measure(s.c_str() + st, txtScale) <= availTextW) { maxStart = st; break; }
            if (maxStart > 0) {
                const int hold = 3, leg = maxStart + hold, full = leg * 2;
                int p = (int)((now / 260) % full);
                int start = (p < leg) ? (p < maxStart ? p : maxStart)
                                      : ((full - p) < maxStart ? (full - p) : maxStart);
                if (start < 0) start = 0;
                std::string out;
                for (int e = start; e < nch; e++) {
                    std::string cand = s.substr((size_t)start, (size_t)(e - start + 1));
                    if (gfx.measure(cand.c_str(), txtScale) > availTextW) break;
                    out = cand;
                }
                shown = out.empty() ? s.substr((size_t)start, 1) : out;
            }
        }
        gfx.text(shown.c_str(), tx, ty, txtScale, rgba(0.93f, 0.95f, 1.0f, a));
    }
}

void OverlayMenu::freeRaTextures(drastic_gfx::OverlayGfx& gfx) {
    for (auto& kv : mRaBadgeTex) if (kv.second) gfx.destroyTexture(kv.second);
    mRaBadgeTex.clear();
    mRaBadgeMissAt.clear();
    if (mBannerBadgeTex) {
        gfx.destroyTexture(mBannerBadgeTex);
        mBannerBadgeTex = 0; mBannerBadgeTexAchId = 0;
    }
}

unsigned OverlayMenu::raBadgeTex(uint32_t achId, drastic_gfx::OverlayGfx& gfx) {
    if (!achId || !mRa) return 0;
    auto it = mRaBadgeTex.find(achId);
    if (it != mRaBadgeTex.end()) return it->second;   // already uploaded
    // Not cached yet: throttle disk read + decode to ~1Hz per badge so a badge
    // that is still downloading does not cause a file read every frame on the
    // render thread (the client thread keeps warming the cache asynchronously).
    const int64_t now = android::elapsedRealtime();
    auto mt = mRaBadgeMissAt.find(achId);
    if (mt != mRaBadgeMissAt.end() && now - mt->second < 1000) return 0;
    mRaBadgeMissAt[achId] = now;
    std::vector<uint8_t> px; int w = 0, h = 0;
    unsigned tex = 0;
    if (mRa->loadCachedBadge(achId, &px, &w, &h) && !px.empty())
        tex = gfx.createImageTexture(px.data(), w, h);
    if (tex) { mRaBadgeTex[achId] = tex; mRaBadgeMissAt.erase(achId); }
    return tex;
}

void OverlayMenu::raBottomTouch(bool down, bool held, float nx, float ny) {
    if (down) {
        mRaBottomTouchActive = true;
        mRaTouchMoved = false;
        mRaTouchDownX = nx; mRaTouchDownY = ny;
        mRaBottomTouchY = ny;
        mRaScrollVel = 0.0f;   // grabbing the list stops any inertia
        return;
    }
    if (held) {
        if (mRaBottomTouchActive) {
            if (fabsf(ny - mRaTouchDownY) > 0.025f) mRaTouchMoved = true;
            float dpx = (mRaBottomTouchY - ny) * mRaBottomViewH;
            mRaBottomScroll += dpx;
            mRaScrollVel = dpx;   // last per-frame delta becomes the flick velocity
            if (mRaBottomScroll < 0.0f) mRaBottomScroll = 0.0f;
            if (mRaBottomScroll > mRaBottomMaxScroll) mRaBottomScroll = mRaBottomMaxScroll;
        }
        mRaBottomTouchY = ny;
        return;
    }
    // Finger up: a touch that did not drag is a tap (momentum carries a flick).
    if (mRaBottomTouchActive && !mRaTouchMoved) raHandleTap(mRaTouchDownX, mRaTouchDownY);
    mRaBottomTouchActive = false;
}

void OverlayMenu::raHandleTap(float /*nx*/, float ny) {
    const float py = ny * mRaBottomViewH;
    if (mRaOpenLbId != 0) {
        // In the rankings view, tapping the top back-bar returns to the list.
        if (py <= mBackBtnH) { mRaOpenLbId = 0; mRaBottomScroll = 0.0f; }
        return;
    }
    // In the detail/summary view, tapping a leaderboard row opens its rankings.
    // Only taps inside the actual list band count (so a tap on the card above,
    // while the list is scrolled, does not spuriously open leaderboard 0).
    if (py >= mLbHitTop && py <= mLbHitBot && mLbHitRowH > 0.0f && !mLbHitIds.empty()) {
        int idx = (int)((py - mLbHitTop + mRaBottomScroll) / mLbHitRowH);
        if (idx >= 0 && idx < (int)mLbHitIds.size()) {
            // Accept only a row the draw actually painted. The list uses a
            // whole-row clip (a row is drawn iff its top ry is in
            // [mLbHitTop, mLbHitBot - mLbHitTextH]); the pixel scroll offset
            // leaves a partial-row blank gap at the top and bottom of the band
            // whose continuous hit index would otherwise resolve to a clipped,
            // off-screen leaderboard. Reconstruct the row top and require it to
            // be visible, exactly as the draw does.
            float ry = mLbHitTop + (float)idx * mLbHitRowH - mRaBottomScroll;
            if (ry >= mLbHitTop && ry <= mLbHitBot - mLbHitTextH) {
                mRaOpenLbId = mLbHitIds[(size_t)idx];
                mRaBottomScroll = 0.0f;
                if (mRa) mRa->requestLeaderboardEntries(mRaOpenLbId);
            }
        }
    }
}

void OverlayMenu::drawRaBottomPanel(drastic_gfx::OverlayGfx& gfx) {
    using drastic_gfx::rgba;
    const float vw = (float)gfx.viewportW();
    const float vh = (float)gfx.viewportH();
    mRaBottomViewH = vh;
    const float basePx = (float)gfx.fontBasePx();
    auto sc = [&](float px) { return (basePx > 0.0f) ? (px / basePx) : 1.0f; };

    // Background, rich-banner style.
    gfx.fillRect(0, 0, vw, vh, rgba(0.05f, 0.06f, 0.09f, 1.0f));

    const float pad = fmaxf(8.0f, vw * 0.035f);
    const float x = pad;
    const float contentW = vw - pad * 2.0f;
    const float titlePx  = fmaxf(12.0f, vh * 0.095f);
    const float statusPx = fmaxf(10.0f, vh * 0.062f);
    const float descPx   = fmaxf(10.0f, vh * 0.060f);
    const float lbPx     = fmaxf(10.0f, vh * 0.060f);

    // Left-right bouncing marquee window of `s` that fits availW (no clipping
    // needed: it returns the visible substring), used for long titles.
    auto marquee = [&](const std::string& s, float scale, float availW) -> std::string {
        if (s.empty() || gfx.measure(s.c_str(), scale) <= availW) return s;
        const int n = (int)s.size();
        int maxStart = n - 1;
        for (int st = 0; st < n; st++)
            if (gfx.measure(s.c_str() + st, scale) <= availW) { maxStart = st; break; }
        if (maxStart <= 0) return s;
        const int hold = 3, leg = maxStart + hold, full = leg * 2;
        int p = (int)((android::elapsedRealtime() / 260) % full);
        int start = (p < leg) ? (p < maxStart ? p : maxStart)
                              : ((full - p) < maxStart ? (full - p) : maxStart);
        if (start < 0) start = 0;
        std::string out;
        for (int e = start; e < n; e++) {
            std::string cand = s.substr(start, (size_t)(e - start + 1));
            if (gfx.measure(cand.c_str(), scale) > availW) break;
            out = cand;
        }
        return out.empty() ? s.substr((size_t)start, 1) : out;
    };

    // Thin scroll bar on the right edge of a scrollable list region.
    auto drawScrollBar = [&](float regionTop, float regionBot, float contentH) {
        if (mRaBottomMaxScroll <= 0.0f) return;
        const float viewH = regionBot - regionTop;
        if (viewH <= 0.0f || contentH <= 0.0f) return;
        const float barW = fmaxf(3.0f, vw * 0.012f);
        const float barX = vw - barW - 2.0f;
        const float thumbH = fmaxf(viewH * (viewH / contentH), 14.0f);
        const float thumbY =
            regionTop + (viewH - thumbH) * (mRaBottomScroll / mRaBottomMaxScroll);
        gfx.fillRect(barX, regionTop, barW, viewH, rgba(1, 1, 1, 0.05f));
        gfx.fillRect(barX, thumbY, barW, thumbH, rgba(0.55f, 0.70f, 0.98f, 0.55f));
    };

    // ===================== Leaderboard rankings (drill-in) =====================
    if (mRaOpenLbId != 0) {
        const float barH = titlePx * 1.7f;
        mBackBtnH = barH;
        gfx.fillRect(0, 0, vw, barH, rgba(0.10f, 0.12f, 0.17f, 1.0f));
        gfx.text(trDyn("< Back"), x, (barH - statusPx) * 0.5f, sc(statusPx),
                 rgba(0.60f, 0.78f, 1.0f, 0.95f));
        float bw = gfx.measure(trDyn("< Back"), sc(statusPx));
        std::string lbTitle;
        if (mRa) {
            auto lbs2 = mRa->leaderboardSnapshot();
            for (auto& l : lbs2) if (l.id == mRaOpenLbId) { lbTitle = l.title; break; }
        }
        gfx.text(marquee(lbTitle, sc(statusPx), contentW - bw - pad).c_str(),
                 x + bw + pad, (barH - statusPx) * 0.5f, sc(statusPx),
                 rgba(0.99f, 0.83f, 0.32f, 0.95f));

        float y = barH + pad * 0.6f;
        uint32_t haveId = 0; bool loading = false;
        std::vector<NanoRetroAchievements::LeaderboardEntry> entries;
        if (mRa) entries = mRa->leaderboardEntriesSnapshot(&haveId, &loading);
        mLbHitIds.clear();   // taps here only hit the back bar
        if (haveId != mRaOpenLbId || loading) {
            // Nothing scrollable yet: zero the clamp so a drag during the fetch
            // cannot accumulate scroll against the previous view's (stale, larger)
            // max and snap the list to its bottom when the entries arrive.
            mRaBottomMaxScroll = 0.0f; mRaBottomScroll = 0.0f;
            gfx.text(trDyn("Loading rankings..."), x, y, sc(descPx), rgba(0.70f, 0.72f, 0.80f, 0.85f));
        } else if (entries.empty()) {
            mRaBottomMaxScroll = 0.0f; mRaBottomScroll = 0.0f;
            gfx.text(trDyn("No entries yet. Be the first!"), x, y, sc(descPx),
                     rgba(0.60f, 0.62f, 0.70f, 0.75f));
        } else {
            const float rowH = lbPx * 1.55f;
            const float regionTop = y, regionBot = vh - pad;
            const float contentH = (float)entries.size() * rowH;
            mRaBottomMaxScroll = fmaxf(0.0f, contentH - fmaxf(0.0f, regionBot - regionTop));
            if (mRaBottomScroll > mRaBottomMaxScroll) mRaBottomScroll = mRaBottomMaxScroll;
            for (size_t i = 0; i < entries.size(); i++) {
                float ry = regionTop + (float)i * rowH - mRaBottomScroll;
                if (ry < regionTop || ry > regionBot - lbPx) continue;  // whole-row clip
                char rk[16]; snprintf(rk, sizeof(rk), "%u", entries[i].rank);
                gfx.text(rk, x, ry, sc(lbPx), rgba(0.70f, 0.72f, 0.80f, 0.92f));
                gfx.text(entries[i].user.c_str(), x + contentW * 0.16f, ry, sc(lbPx),
                         rgba(0.88f, 0.90f, 0.95f, 0.94f));
                if (!entries[i].score.empty()) {
                    float swid = gfx.measure(entries[i].score.c_str(), sc(lbPx));
                    gfx.text(entries[i].score.c_str(), x + contentW - swid, ry, sc(lbPx),
                             rgba(0.99f, 0.83f, 0.32f, 0.92f));
                }
            }
            drawScrollBar(regionTop, regionBot, contentH);
        }
        return;
    }

    // ===================== Detail / summary view =====================
    float y = pad;
    const RowAction* sel = nullptr;
    int cur = mCursor[kSec_Achievements];
    if (cur >= 0 && cur < (int)mRows.size() && mRows[cur].raAchId) sel = &mRows[cur];
    // No fallback: only show a card when an actual achievement row is selected.

    if (sel) {
        const bool unlocked = (sel->tag == kRowUnlocked);
        const float badgeSz = fmaxf(40.0f, vh * 0.28f);
        unsigned tex = raBadgeTex(sel->raAchId, gfx);
        if (tex) gfx.drawImage(tex, x, y, badgeSz, badgeSz, 1.0f);
        else     gfx.roundedRect(x, y, badgeSz, badgeSz, 6.0f, rgba(1, 1, 1, 0.06f));

        const float tx = x + badgeSz + pad;
        const float tw = contentW - badgeSz - pad;
        gfx.text(marquee(sel->label, sc(titlePx), tw).c_str(), tx, y, sc(titlePx),
                 unlocked ? rgba(0.99f, 0.83f, 0.32f, 0.98f)
                          : rgba(0.92f, 0.93f, 0.97f, 0.96f));
        const float sy = y + titlePx * 1.25f;
        gfx.text(trDyn(unlocked ? "UNLOCKED" : "LOCKED"), tx, sy, sc(statusPx),
                 unlocked ? rgba(0.45f, 0.85f, 0.50f, 0.95f)
                          : rgba(0.60f, 0.62f, 0.70f, 0.80f));
        if (!sel->value.empty()) {
            float pwid = gfx.measure(sel->value.c_str(), sc(statusPx));
            gfx.text(sel->value.c_str(), tx + tw - pwid, sy, sc(statusPx),
                     rgba(0.85f, 0.86f, 0.92f, 0.90f));
        }
        y += badgeSz + pad * 0.5f;
        gfx.fillRect(x, y, contentW, fmaxf(1.0f, vh * 0.004f), rgba(1, 1, 1, 0.10f));
        y += pad * 0.6f;
        if (!sel->detail.empty()) {
            const int maxLines = 4;
            std::vector<std::string> lines(1);
            size_t pos = 0;
            const std::string& d = sel->detail;
            while (pos < d.size() && (int)lines.size() <= maxLines) {
                size_t spn = d.find(' ', pos);
                std::string word = d.substr(
                    pos, spn == std::string::npos ? std::string::npos : spn - pos);
                std::string& ln = lines.back();
                std::string cand = ln.empty() ? word : (ln + " " + word);
                if (ln.empty() || gfx.measure(cand.c_str(), sc(descPx)) <= contentW)
                    ln = cand;
                else if ((int)lines.size() < maxLines) lines.push_back(word);
                else break;
                pos = (spn == std::string::npos) ? d.size() : spn + 1;
            }
            if (pos < d.size() && !lines.empty()) {
                std::string& last = lines.back();
                while (!last.empty() &&
                       gfx.measure((last + "...").c_str(), sc(descPx)) > contentW)
                    last.pop_back();
                last += "...";
            }
            for (auto& ln : lines) {
                gfx.text(ln.c_str(), x, y, sc(descPx), rgba(0.82f, 0.84f, 0.90f, 0.95f));
                y += descPx * 1.25f;
            }
            y += pad * 0.3f;
        }
    } else {
        // Non-achievement row (Account/Hardcore/headers): show a game summary,
        // never a stray achievement card.
        gfx.text(trDyn("Achievements"), x, y, sc(titlePx), rgba(0.92f, 0.93f, 0.97f, 0.96f));
        y += titlePx * 1.3f;
        if (mRa) {
            auto list = mRa->achievementSnapshot();
            int total = 0, unl = 0; uint32_t pts = 0, tot = 0;
            for (const auto& a : list) {
                if (a.id >= 100000000u) continue;   // skip the pseudo entry
                total++; tot += a.points;
                if (a.unlocked) { unl++; pts += a.points; }
            }
            char buf[96];
            snprintf(buf, sizeof(buf), "%d %s %d %s    %u / %u %s",
                     unl, trDyn("of"), total, trDyn("unlocked"), pts, tot, trDyn("points"));
            gfx.text(buf, x, y, sc(statusPx), rgba(0.82f, 0.84f, 0.90f, 0.92f));
            y += statusPx * 1.5f;
        }
    }

    // Divider between the achievement description / summary above and the
    // Leaderboards section below.
    gfx.fillRect(x, y, contentW, fmaxf(1.0f, vh * 0.004f), rgba(1, 1, 1, 0.12f));
    y += pad * 0.7f;

    // ===================== Leaderboards list (tappable) =====================
    gfx.text(trDyn("Leaderboards"), x, y, sc(statusPx), rgba(0.55f, 0.70f, 0.98f, 0.95f));
    y += statusPx * 1.5f;
    std::vector<NanoRetroAchievements::LeaderboardInfo> lbs;
    if (mRa) lbs = mRa->leaderboardSnapshot();
    const float regionTop = y, regionBot = vh - pad;
    const float rowH = lbPx * 1.6f;
    const float contentH = (float)lbs.size() * rowH;
    mRaBottomMaxScroll = fmaxf(0.0f, contentH - fmaxf(0.0f, regionBot - regionTop));
    if (mRaBottomScroll > mRaBottomMaxScroll) mRaBottomScroll = mRaBottomMaxScroll;
    mLbHitTop = regionTop; mLbHitRowH = rowH; mLbHitBot = regionBot;
    mLbHitTextH = lbPx; mLbHitIds.clear();
    for (const auto& l : lbs) mLbHitIds.push_back(l.id);
    if (lbs.empty()) {
        gfx.text(trDyn("No leaderboards for this game."), x, regionTop, sc(descPx),
                 rgba(0.60f, 0.62f, 0.70f, 0.70f));
    }
    const float chW = gfx.measure(">", sc(lbPx));
    for (size_t i = 0; i < lbs.size(); i++) {
        float ry = regionTop + (float)i * rowH - mRaBottomScroll;
        if (ry < regionTop || ry > regionBot - lbPx) continue;   // whole-row clip
        gfx.text(lbs[i].title.c_str(), x, ry, sc(lbPx), rgba(0.86f, 0.88f, 0.93f, 0.92f));
        // Chevron: a touch affordance showing the row opens its rankings.
        gfx.text(">", x + contentW - chW, ry, sc(lbPx), rgba(0.55f, 0.70f, 0.98f, 0.95f));
        if (!lbs[i].value.empty()) {
            float vwid = gfx.measure(lbs[i].value.c_str(), sc(lbPx));
            gfx.text(lbs[i].value.c_str(), x + contentW - chW - pad - vwid, ry, sc(lbPx),
                     rgba(0.99f, 0.83f, 0.32f, 0.92f));
        }
    }
    drawScrollBar(regionTop, regionBot, contentH);
}

// ---------------------------------------------------------------------------
// Single-screen RetroAchievements drill-in (no bottom DS panel)
// ---------------------------------------------------------------------------

bool OverlayMenu::raSingleAccept() {
    if (!mSingleScreen || mSection != kSec_Achievements || !mRa) return false;
    if (mRaView == 1 || mRaView == 3) return true;   // detail / rankings: no-op
    if (mRaView == 2) {                               // leaderboards -> rankings
        auto lbs = mRa->leaderboardSnapshot();
        if (mLbCursor >= 0 && mLbCursor < (int)lbs.size()) {
            mRaOpenLbId = lbs[mLbCursor].id;
            mRa->requestLeaderboardEntries(mRaOpenLbId);
            mRaView = 3;
            mRaViewScroll = 0.0f;
        }
        return true;
    }
    // view 0 (list): drill into the selected achievement's detail card.
    int cur = mCursor[kSec_Achievements];
    if (cur >= 0 && cur < (int)mRows.size() && mRows[cur].raAchId) {
        mRaDetailAchId = mRows[cur].raAchId;
        mRaView = 1;
        mRaViewScroll = 0.0f;
        return true;
    }
    return false;   // non-achievement row: let its onAccept run (Leaderboards, Account...)
}

bool OverlayMenu::raSingleCancel() {
    if (!mSingleScreen || mSection != kSec_Achievements) return false;
    if (mRaView == 3) { mRaView = 2; mRaViewScroll = 0.0f; return true; }
    if (mRaView == 2) { mRaView = 0; return true; }
    if (mRaView == 1) { mRaView = 0; return true; }
    return false;   // view 0: let Cancel close the menu
}

bool OverlayMenu::raSingleNav(NavDir dir) {
    if (!mSingleScreen || mSection != kSec_Achievements || mRaView == 0) return false;
    if (mRaView == 2 && mRa) {
        int n = (int)mRa->leaderboardSnapshot().size();
        if (n > 0) {
            if (dir == NavDir::Up)   mLbCursor = (mLbCursor + n - 1) % n;
            if (dir == NavDir::Down) mLbCursor = (mLbCursor + 1) % n;
        }
        return true;
    }
    if (mRaView == 3) {   // rankings: scroll
        const float step = (mRaViewMaxScroll > 0.0f)
                ? fmaxf(40.0f, mRaViewMaxScroll * 0.14f) : 0.0f;
        if (dir == NavDir::Up)   mRaViewScroll = fmaxf(0.0f, mRaViewScroll - step);
        if (dir == NavDir::Down) mRaViewScroll = fminf(mRaViewMaxScroll, mRaViewScroll + step);
        return true;
    }
    return true;   // view 1 (detail): nothing to scroll, but consume so the list does not move
}

bool OverlayMenu::drawRaSingle(drastic_gfx::OverlayGfx& gfx, float x, float top,
                               float w, float h, float sf) {
    if (!mSingleScreen || mSection != kSec_Achievements || mRaView == 0 || !mRa)
        return false;
    using drastic_gfx::rgba;
    const float basePx = (float)gfx.fontBasePx();
    auto sc = [&](float px) { return (basePx > 0.0f) ? (px / basePx) : 1.0f; };
    const float pad = fmaxf(8.0f, h * 0.03f);
    const float titlePx  = fmaxf(14.0f, h * 0.055f);
    const float statusPx = fmaxf(11.0f, h * 0.040f);
    const float descPx   = fmaxf(11.0f, h * 0.038f);

    // Panel background + a "Back" affordance shared by all three views.
    gfx.fillRect(x, top, w, h, rgba(0.05f, 0.06f, 0.09f, 0.92f));
    const char* backHint = trDyn((mRaView == 3) ? "< Back to Leaderboards" : "< Back");
    gfx.text(backHint, x + pad, top + pad, sc(statusPx), rgba(0.60f, 0.78f, 1.0f, 0.95f));
    float y = top + pad + statusPx * 1.6f;
    const float cx = x + pad;
    const float cw = w - pad * 2.0f;

    auto wrap = [&](const std::string& s, float scale, float availW, int maxLines,
                    std::vector<std::string>* out) {
        out->assign(1, std::string());
        size_t pos = 0;
        while (pos < s.size() && (int)out->size() <= maxLines) {
            size_t spn = s.find(' ', pos);
            std::string word = s.substr(pos, spn == std::string::npos ? std::string::npos : spn - pos);
            std::string& ln = out->back();
            std::string cand = ln.empty() ? word : (ln + " " + word);
            if (ln.empty() || gfx.measure(cand.c_str(), scale) <= availW) ln = cand;
            else if ((int)out->size() < maxLines) out->push_back(word);
            else break;
            pos = (spn == std::string::npos) ? s.size() : spn + 1;
        }
    };

    // ===================== View 1: achievement detail =====================
    if (mRaView == 1) {
        NanoRetroAchievements::AchievementInfo info{};
        bool found = false;
        for (auto& a : mRa->achievementSnapshot())
            if (a.id == mRaDetailAchId) { info = a; found = true; break; }
        if (!found) { mRaView = 0; return true; }

        const float badgeSz = fmaxf(48.0f, h * 0.26f);
        unsigned tex = raBadgeTex(info.id, gfx);
        if (tex) gfx.drawImage(tex, cx, y, badgeSz, badgeSz, 1.0f);
        else     gfx.roundedRect(cx, y, badgeSz, badgeSz, 6.0f, rgba(1, 1, 1, 0.06f));

        const float tx = cx + badgeSz + pad;
        const float tw = cw - badgeSz - pad;
        std::vector<std::string> tl;
        wrap(info.title, sc(titlePx), tw, 2, &tl);
        float ty = y;
        for (auto& ln : tl) {
            gfx.text(ln.c_str(), tx, ty, sc(titlePx),
                     info.unlocked ? rgba(0.99f, 0.83f, 0.32f, 0.98f)
                                   : rgba(0.92f, 0.93f, 0.97f, 0.96f));
            ty += titlePx * 1.18f;
        }
        gfx.text(trDyn(info.unlocked ? "UNLOCKED" : "LOCKED"), tx, ty, sc(statusPx),
                 info.unlocked ? rgba(0.45f, 0.85f, 0.50f, 0.95f)
                               : rgba(0.60f, 0.62f, 0.70f, 0.85f));
        char pts[40]; snprintf(pts, sizeof(pts), "%u %s", info.points, trDyn("pts"));
        float pw = gfx.measure(pts, sc(statusPx));
        gfx.text(pts, tx + tw - pw, ty, sc(statusPx), rgba(0.85f, 0.86f, 0.92f, 0.90f));

        y += badgeSz + pad;
        gfx.fillRect(cx, y, cw, fmaxf(1.0f, h * 0.004f), rgba(1, 1, 1, 0.10f));
        y += pad * 0.8f;

        // Full description (more lines than the list band affords).
        std::vector<std::string> dl;
        wrap(info.description, sc(descPx), cw, 6, &dl);
        for (auto& ln : dl) {
            gfx.text(ln.c_str(), cx, y, sc(descPx), rgba(0.82f, 0.84f, 0.90f, 0.95f));
            y += descPx * 1.3f;
        }
        y += pad * 0.6f;

        // Progress indicator: a bar plus the measured fraction (or "Unlocked").
        float frac = info.unlocked ? 1.0f : 0.0f;
        std::string ptext = info.unlocked ? trDyn("Complete") : "";
        if (!info.unlocked && !info.measuredProgress.empty()) {
            ptext = info.measuredProgress;
            int cur = 0, tot = 0;
            if (sscanf(info.measuredProgress.c_str(), "%d/%d", &cur, &tot) == 2 && tot > 0)
                frac = fminf(1.0f, fmaxf(0.0f, (float)cur / (float)tot));
        }
        if (info.unlocked || !info.measuredProgress.empty()) {
            gfx.text(trDyn("Progress"), cx, y, sc(statusPx), rgba(0.55f, 0.70f, 0.98f, 0.95f));
            if (!ptext.empty()) {
                float vwid = gfx.measure(ptext.c_str(), sc(statusPx));
                gfx.text(ptext.c_str(), cx + cw - vwid, y, sc(statusPx),
                         rgba(0.99f, 0.83f, 0.32f, 0.92f));
            }
            y += statusPx * 1.4f;
            const float barH = fmaxf(8.0f, h * 0.022f);
            gfx.roundedRect(cx, y, cw, barH, barH * 0.5f, rgba(1, 1, 1, 0.08f));
            if (frac > 0.0f)
                gfx.roundedRect(cx, y, fmaxf(barH, cw * frac), barH, barH * 0.5f,
                                info.unlocked ? rgba(0.45f, 0.85f, 0.50f, 0.95f)
                                              : rgba(0.55f, 0.70f, 0.98f, 0.95f));
        }
        return true;
    }

    // ===================== View 2: leaderboards list =====================
    if (mRaView == 2) {
        gfx.text(trDyn("Leaderboards"), cx, y, sc(titlePx), rgba(0.92f, 0.93f, 0.97f, 0.96f));
        y += titlePx * 1.4f;
        auto lbs = mRa->leaderboardSnapshot();
        if (mLbCursor >= (int)lbs.size()) mLbCursor = (int)lbs.size() - 1;
        if (mLbCursor < 0) mLbCursor = 0;
        if (lbs.empty()) {
            gfx.text(trDyn("No leaderboards for this game."), cx, y, sc(descPx),
                     rgba(0.60f, 0.62f, 0.70f, 0.75f));
            return true;
        }
        const float rowH = statusPx * 1.9f;
        // Keep the cursor row in view: scroll the list if it runs past the panel.
        const float regionBot = top + h - pad;
        int visible = (int)((regionBot - y) / rowH);
        if (visible < 1) visible = 1;
        int first = 0;
        if (mLbCursor >= visible) first = mLbCursor - visible + 1;
        for (int i = first; i < (int)lbs.size(); i++) {
            float ry = y + (float)(i - first) * rowH;
            if (ry > regionBot - statusPx) break;
            if (i == mLbCursor)
                gfx.fillRect(x, ry - rowH * 0.18f, w, rowH, rgba(0.35f, 0.55f, 0.95f, 0.22f));
            gfx.text(lbs[i].title.c_str(), cx, ry, sc(statusPx),
                     rgba(0.88f, 0.90f, 0.95f, 0.94f));
            if (!lbs[i].value.empty()) {
                float vwid = gfx.measure(lbs[i].value.c_str(), sc(statusPx));
                gfx.text(lbs[i].value.c_str(), cx + cw - vwid, ry, sc(statusPx),
                         rgba(0.99f, 0.83f, 0.32f, 0.92f));
            }
        }
        return true;
    }

    // ===================== View 3: leaderboard rankings =====================
    {
        std::string lbTitle;
        for (auto& l : mRa->leaderboardSnapshot())
            if (l.id == mRaOpenLbId) { lbTitle = l.title; break; }
        gfx.text(lbTitle.c_str(), cx, y, sc(titlePx), rgba(0.99f, 0.83f, 0.32f, 0.96f));
        y += titlePx * 1.4f;
        uint32_t haveId = 0; bool loading = false;
        auto entries = mRa->leaderboardEntriesSnapshot(&haveId, &loading);
        if (haveId != mRaOpenLbId || loading) {
            mRaViewMaxScroll = 0.0f; mRaViewScroll = 0.0f;
            gfx.text(trDyn("Loading rankings..."), cx, y, sc(descPx), rgba(0.70f, 0.72f, 0.80f, 0.85f));
            return true;
        }
        if (entries.empty()) {
            mRaViewMaxScroll = 0.0f; mRaViewScroll = 0.0f;
            gfx.text(trDyn("No entries yet. Be the first!"), cx, y, sc(descPx),
                     rgba(0.60f, 0.62f, 0.70f, 0.75f));
            return true;
        }
        const float rowH = statusPx * 1.7f;
        const float regionTop = y, regionBot = top + h - pad;
        const float contentH = (float)entries.size() * rowH;
        mRaViewMaxScroll = fmaxf(0.0f, contentH - fmaxf(0.0f, regionBot - regionTop));
        if (mRaViewScroll > mRaViewMaxScroll) mRaViewScroll = mRaViewMaxScroll;
        for (size_t i = 0; i < entries.size(); i++) {
            float ry = regionTop + (float)i * rowH - mRaViewScroll;
            if (ry < regionTop || ry > regionBot - statusPx) continue;
            char rk[16]; snprintf(rk, sizeof(rk), "%u", entries[i].rank);
            gfx.text(rk, cx, ry, sc(statusPx), rgba(0.70f, 0.72f, 0.80f, 0.92f));
            gfx.text(entries[i].user.c_str(), cx + cw * 0.16f, ry, sc(statusPx),
                     rgba(0.88f, 0.90f, 0.95f, 0.94f));
            if (!entries[i].score.empty()) {
                float swid = gfx.measure(entries[i].score.c_str(), sc(statusPx));
                gfx.text(entries[i].score.c_str(), cx + cw - swid, ry, sc(statusPx),
                         rgba(0.99f, 0.83f, 0.32f, 0.92f));
            }
        }
        return true;
    }
}

// ---------------------------------------------------------------------------
// Rich Achievements list (RetroAchievements app style): per-row badge, title,
// description and unlock-date / rarity, with the points on the right, grouped
// by the rc_client bucket headers. Replaces the plain text list.
// ---------------------------------------------------------------------------

void OverlayMenu::drawAchievementsList(drastic_gfx::OverlayGfx& gfx, float vw,
                                       float listY, float listH, float sf) {
    const float basePx = (float)gfx.fontBasePx();
    auto sc = [&](float px) { return (basePx > 0.0f) ? (px / basePx) : 1.0f; };
    const float contentLeft  = vw * 0.05f;
    const float contentRight = vw * 0.95f;
    const float contentW = contentRight - contentLeft;

    // Rich detail (rarity / unlock time / description) keyed by achievement id.
    std::map<uint32_t, NanoRetroAchievements::AchievementInfo> info;
    if (mRa) for (auto& a : mRa->achievementSnapshot()) info[a.id] = a;

    // Match the rest of the menu. The normal list (drawList) renders a row at
    // kRowBaseScale*sf, so size the Achievements rows off the same basis (a
    // multiple of the base font, scaled by the viewport factor sf) rather than a
    // panel-height fraction, which made this page balloon next to the others.
    // The title lands at exactly the normal row text size; description, meta and
    // badge keep their relative proportions. A small floor keeps it legible on
    // tiny panels.
    const float titlePx = fmaxf(12.0f, basePx * kRowBaseScale * sf);
    const float descPx  = titlePx * 0.77f;
    const float metaPx  = titlePx * 0.68f;
    const float badge   = titlePx * 2.7f;
    const float rowPad  = titlePx * 0.34f;
    const float achRowH = badge + rowPad * 1.4f;
    const float hdrRowH = titlePx * 1.7f;
    const float smpRowH = titlePx * 2.0f;

    const int n = (int)mRows.size();
    auto rowH = [&](int i) -> float {
        const RowAction& r = mRows[i];
        if (r.tag == kRowHeader) return hdrRowH;
        if (r.raAchId) return achRowH;
        return smpRowH;
    };

    // Whole-row scroll: mAchTopRow is the first visible row. The cursor moves
    // within the visible window; only advance the top row when the cursor would
    // fall off the bottom, or pull it back when it goes above the top. Only full
    // rows are drawn (see the loop), so nothing partial spills into the footer.
    float total = 0.0f;
    for (int i = 0; i < n; i++) total += rowH(i);
    const int cur = mCursor[kSec_Achievements];
    if (mAchTopRow < 0) mAchTopRow = 0;
    if (mAchTopRow > n - 1) mAchTopRow = (n > 0) ? n - 1 : 0;
    if (cur >= 0 && cur < n) {
        if (cur < mAchTopRow) {
            mAchTopRow = cur;
        } else {
            for (int guard = 0; guard < n && mAchTopRow < cur; guard++) {
                float used = 0.0f; bool fits = false;
                for (int i = mAchTopRow; i < n; i++) {
                    if (used + rowH(i) > listH + 0.5f) break;
                    used += rowH(i);
                    if (i == cur) { fits = true; break; }
                }
                if (fits) break;
                mAchTopRow++;
            }
        }
    }
    float skipped = 0.0f;
    for (int i = 0; i < mAchTopRow && i < n; i++) skipped += rowH(i);

    auto ellipsize = [&](const std::string& s, float scale, float availW) -> std::string {
        if (s.empty() || gfx.measure(s.c_str(), scale) <= availW) return s;
        std::string out;
        for (size_t e = 0; e < s.size(); e++) {
            std::string cand = s.substr(0, e + 1) + "...";
            if (gfx.measure(cand.c_str(), scale) > availW) break;
            out = s.substr(0, e + 1);
        }
        return out.empty() ? s.substr(0, 1) : (out + "...");
    };

    float yCursor = listY;
    for (int i = mAchTopRow; i < n; i++) {
        const RowAction& r = mRows[i];
        const float h = rowH(i);
        if (yCursor + h > listY + listH + 0.5f) break;  // stop before a row would hit the footer
        const float ry = yCursor;
        yCursor += h;   // advance now so the continues below don't skip it
        const bool active = (i == cur);

        if (r.tag == kRowHeader) {
            gfx.text(trDyn(r.label.c_str()), contentLeft, ry + (h - titlePx) * 0.5f,
                     sc(titlePx), rgba(0.45f, 0.74f, 1.0f, 0.92f));
            continue;
        }

        if (active) {
            gfx.fillRect(contentLeft - rowPad, ry, contentW + rowPad * 2.0f, h,
                         rgba(0.35f, 0.55f, 0.95f, 0.20f));
            gfx.fillRect(contentLeft - rowPad, ry, 5.0f * sf, h,
                         rgba(0.35f, 0.75f, 1.0f, 0.95f));
        }

        if (!r.raAchId) {
            // Account / Hardcore / Login / View Leaderboards: simple label+value.
            Color fg = active ? rgba(1, 1, 1, 1)
                     : (r.tag == kRowLocked ? rgba(0.60f, 0.62f, 0.70f, 0.70f)
                                            : rgba(0.85f, 0.87f, 0.93f, 0.90f));
            gfx.text(trDyn(r.label.c_str()), contentLeft, ry + (h - titlePx) * 0.5f,
                     sc(titlePx), fg);
            if (!r.value.empty()) {
                const char* rv = trDyn(r.value.c_str());
                float w = gfx.measure(rv, sc(titlePx));
                gfx.text(rv, contentRight - w, ry + (h - titlePx) * 0.5f, sc(titlePx), fg);
            }
            continue;
        }

        // Rich achievement row: badge + title + description + meta, points right.
        const auto it = info.find(r.raAchId);
        const bool unlocked = (r.tag == kRowUnlocked) ||
                              (it != info.end() && it->second.unlocked);
        unsigned tex = raBadgeTex(r.raAchId, gfx);
        const float bx = contentLeft, by = ry + (h - badge) * 0.5f;
        if (tex) gfx.drawImage(tex, bx, by, badge, badge, unlocked ? 1.0f : 0.5f);
        else     gfx.roundedRect(bx, by, badge, badge, 5.0f, rgba(1, 1, 1, 0.06f));

        const float tx = bx + badge + rowPad * 1.3f;
        uint32_t pts = (it != info.end()) ? it->second.points : 0;
        char pb[24]; snprintf(pb, sizeof(pb), "%u %s", pts, trDyn(pts == 1 ? "point" : "points"));
        const float ptsW = gfx.measure(pb, sc(metaPx));
        gfx.text(pb, contentRight - ptsW, by, sc(metaPx),
                 unlocked ? rgba(0.99f, 0.83f, 0.32f, 0.95f)
                          : rgba(0.70f, 0.72f, 0.80f, 0.85f));
        const float tw = (contentRight - ptsW - rowPad) - tx;

        Color titleFg = active ? rgba(1, 1, 1, 1)
                      : (unlocked ? rgba(0.99f, 0.83f, 0.32f, 0.96f)
                                  : rgba(0.86f, 0.88f, 0.93f, 0.92f));
        gfx.text(ellipsize(r.label, sc(titlePx), tw).c_str(), tx, by, sc(titlePx), titleFg);

        const std::string& desc =
                (it != info.end() && !it->second.description.empty())
                ? it->second.description : r.detail;
        gfx.text(ellipsize(desc, sc(descPx), tw).c_str(), tx, by + titlePx * 1.25f,
                 sc(descPx), rgba(0.74f, 0.76f, 0.83f, 0.88f));

        std::string meta;
        if (it != info.end()) {
            const auto& a = it->second;
            if (unlocked && a.unlockTime > 0) {
                time_t t = (time_t)a.unlockTime;
                struct tm tmv; localtime_r(&t, &tmv);
                char db[40];
                snprintf(db, sizeof(db), "%s %d/%d/%02d", trDyn("Unlocked"),
                         tmv.tm_mon + 1, tmv.tm_mday, (tmv.tm_year + 1900) % 100);
                meta = db;
            } else if (!unlocked && !a.measuredProgress.empty()) {
                meta = a.measuredProgress;
            }
            if (a.rarity > 0.0f) {
                char rb[40]; snprintf(rb, sizeof(rb), "%.1f%% %s", a.rarity, trDyn("of players"));
                meta = meta.empty() ? rb : (meta + "   -   " + rb);
            }
        }
        if (!meta.empty())
            gfx.text(ellipsize(meta, sc(metaPx), tw).c_str(), tx,
                     by + titlePx * 1.25f + descPx * 1.3f, sc(metaPx),
                     rgba(0.55f, 0.70f, 0.98f, 0.80f));
    }

    if (total > listH) {
        const float barW = fmaxf(3.0f, vw * 0.006f);
        const float barX = contentRight + rowPad;
        const float thumbH = fmaxf(listH * (listH / total), 16.0f);
        const float thumbY = listY + (listH - thumbH) *
                             (skipped / fmaxf(1.0f, total - listH));
        gfx.fillRect(barX, listY, barW, listH, rgba(1, 1, 1, 0.05f));
        gfx.fillRect(barX, thumbY, barW, thumbH, rgba(0.55f, 0.70f, 0.98f, 0.50f));
    }
}

}  // namespace drastic_overlay
}  // namespace android
