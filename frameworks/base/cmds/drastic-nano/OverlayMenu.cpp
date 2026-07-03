/*
 * Copyright (C) 2026 GammaOS
 */

#define LOG_TAG "DrasticNano.Overlay"

#include "OverlayMenu.h"
#include "DsScreenLayout.h"   // presetCount()/presetName() for the Layout Preset row
#include "NanoI18n.h"   // trDyn() shared nano UI translations
#include "NanoRetroAchievements.h"   // RaUiEvent

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <ctime>

#include <aidl/android/hardware/health/BatteryStatus.h>
#include <aidl/android/hardware/health/IHealth.h>
#include <android/binder_manager.h>
#include <cutils/properties.h>

#include "NanoBacklight.h"
#include "NanoSliderHud.h"   // shared volume/brightness slider spec (gammaos-nano)
#include <utils/Log.h>
#include <utils/SystemClock.h>

#include "DrasticRunner.h"
#include "NanoMenuDrm.h"

namespace android {
namespace drastic_overlay {

using drastic_gfx::Color;
using drastic_gfx::rgba;

namespace {
constexpr const char* kSectionNames[] = {
    "Save States", "Video", "Audio", "Controls", "Cheats", "Achievements",
};
// XMB-style layout constants. Coordinates scale with sf =
// min(vw/1080, vh/720), matching the nano XMB scaling so the overlay
// looks at home on the same display. Reference viewport: 1080x720 logical.
constexpr float kSfMin = 0.45f;
constexpr float kSfMax = 2.0f;
constexpr float kCatBarTopFrac  = 0.06f;   // where the category title sits
// Body scales. Tripled from the initial pass per user feedback so the
// overlay is readable on small handheld panels. Footer intentionally
// stays modest so the hint strip fits the screen width.
constexpr float kCatActiveSc    = 2.025f;  // active category title scale
// kRowSelScale / kRowBaseScale moved to OverlayMenu.h so the Achievements page
// (OverlayMenuRa.cpp) sizes its rows the same way the normal list does.
constexpr float kBatTextScale   = 1.425f;
constexpr float kBatIconScale   = 1.5f;    // battery-body dimension multiplier
constexpr float kFooterScale    = 1.35f;   // help hint strip at bottom
                                           // fits on 640-wide panels
// Row text fills more of the viewport now, so the content gutters
// shrink from 16%/84% to 5%/95%.
constexpr float kContentLeftFrac  = 0.05f;
constexpr float kContentRightFrac = 0.95f;

// Compress an XMB scale factor above the small-panel regime. The overlay's
// per-element multipliers are tuned for handheld panels whose natural sf is
// ~0.5 to 0.7; without this a 960- or 1080-line panel (natural sf > 1) renders
// the menu oversized. Below the knee the value passes through unchanged; above
// it the excess is scaled down, so the menu keeps a consistent on-screen
// fraction from a 480p panel up through 1080p.
inline float scaleForViewport(float sf) {
    constexpr float kKnee  = 0.65f;   // small-panel sf ceiling, passed through
    constexpr float kSlope = 0.40f;   // growth rate applied past the knee
    return (sf > kKnee) ? (kKnee + (sf - kKnee) * kSlope) : sf;
}
} // anonymous namespace

OverlayMenu::OverlayMenu() {}
OverlayMenu::~OverlayMenu() {}

void OverlayMenu::init(DrasticRunner* runner,
                       const drastic_prefs::Prefs& prefs,
                       uid_t appUid, gid_t appGid,
                       std::string xmlPath,
                       std::string savestatesDir,
                       std::string romPath,
                       std::string shadersDir) {
    mRunner = runner;
    mPrefs = prefs;
    mSavedPrefs = prefs;
    mAppUid = appUid;
    mAppGid = appGid;
    mXmlPath = std::move(xmlPath);
    mSavestatesDir = std::move(savestatesDir);
    mShadersDir = std::move(shadersDir);

    // Rom basename: last path component, no extension.
    size_t slash = romPath.find_last_of('/');
    std::string name = (slash == std::string::npos)
            ? romPath : romPath.substr(slash + 1);
    size_t dot = name.find_last_of('.');
    mRomBase = (dot == std::string::npos) ? name : name.substr(0, dot);
    ALOGI("OverlayMenu::init rom=%s savestates=%s shaders=%s xml=%s",
          mRomBase.c_str(), mSavestatesDir.c_str(), mShadersDir.c_str(),
          mXmlPath.c_str());

    scanShaders();
}

bool OverlayMenu::isCapturingKey() const { return mCaptureKey; }

void OverlayMenu::scanShaders() {
    mShaders.clear();
    DIR* d = opendir(mShadersDir.c_str());
    if (!d) {
        ALOGW("OverlayMenu::scanShaders: cannot open %s",
              mShadersDir.c_str());
        return;
    }
    struct dirent* e;
    while ((e = readdir(d)) != nullptr) {
        const char* n = e->d_name;
        size_t len = strlen(n);
        if (len < 5) continue;
        if (strcasecmp(n + len - 4, ".dfx") != 0) continue;
        std::string s(n, n + len - 4);
        mShaders.push_back(s);
    }
    closedir(d);
    std::sort(mShaders.begin(), mShaders.end());
    ALOGI("OverlayMenu::scanShaders: %zu shaders", mShaders.size());
}

bool OverlayMenu::slotFileExists(int slot) const {
    if (mRomBase.empty() || mSavestatesDir.empty()) return false;
    char path[512];
    snprintf(path, sizeof(path), "%s/%s_%d.dss",
             mSavestatesDir.c_str(), mRomBase.c_str(), slot);
    struct stat st;
    return stat(path, &st) == 0 && st.st_size > 0;
}

void OverlayMenu::openMenu() {
    if (mOpen) return;
    mOpen = true;
    mSavedPrefs = mPrefs;
    // Re-enumerate cheats fresh each open (cheap; the model caches names so
    // per-input rebuilds don't re-allocate).
    mCheatModelValid = false;
    navRelease();   // clear any stale held-direction from a prior session
    if (mRunner) mRunner->pauseToggle(true);
    rebuildRows();
    ALOGI("OverlayMenu: opened");
}

void OverlayMenu::closeMenu() {
    if (!mOpen) return;
    if (mDirty) writePrefsSafe();
    mOpen = false;
    mCaptureKey = false;
    mCaptureActionIdx = -1;
    // Drop any leaderboard drill-in so reopening starts on the detail/list view.
    mRaOpenLbId = 0; mRaBottomScroll = 0.0f; mRaScrollVel = 0.0f;
    mRaView = 0;   // single-screen drill-in returns to the achievement list
    if (mRunner) {
        mRunner->pauseToggle(false);
        // Re-assert the live config on the now-running emulator. Live
        // changes made while the overlay had the game paused (e.g.
        // Threaded 3D) are applied through the converter again here, after
        // unpause, so the running emulation reliably picks them up.
        if (mDirty) applyConfigLive();
        // Flush cheat enables: updateCheats(1) writes the per-game .cht and
        // schedules a live re-apply. Batched here (once) rather than per
        // toggle. Persists across ROM loads (drastic reloads the .cht at
        // startGame).
        if (mCheatsDirty) {
            mRunner->applyCheats();
            mCheatsDirty = false;
        }
    }
    ALOGI("OverlayMenu: closed");
}

void OverlayMenu::close() {
    if (mOpen) closeMenu();
}

void OverlayMenu::toast(const std::string& msg, int64_t ms) {
    // Localize static toast text. Dynamic snprintf'd toasts (e.g. "Saved slot 3")
    // trDyn their format string at the call site instead, since the filled-in
    // value would not match a key; a non-key message here passes through as-is.
    mToast = trDyn(msg.c_str());
    mToastUntilMs = android::elapsedRealtime() + ms;
}

void OverlayMenu::onRaUiEvent(const RaUiEvent& ev) {
    // Every RetroAchievements message uses the rich top-right banner (no more
    // plain toasts). Accent colour cues the kind: gold for unlocks, green for
    // sign-in / success, red for errors, blue for informational placards.
    switch (ev.kind) {
        case RaUiEvent::Unlock:
            // Badge + title + points + chime (chime played by the RA client).
            // The badge image arrives asynchronously and is attached by
            // drawAchievementBanner once decoded.
            showBanner(trDyn("ACHIEVEMENT UNLOCKED"), ev.title, ev.subtitle,
                       (int)ev.points, ev.id, 0.96f, 0.80f, 0.28f, ev.badgeUrl);
            break;
        case RaUiEvent::Mastery:
            showBanner(trDyn("GAME MASTERED"),
                       ev.title.empty() ? trDyn("Congratulations!") : ev.title,
                       ev.subtitle, -1, 0, 0.96f, 0.80f, 0.28f);
            break;
        case RaUiEvent::GamePlacard:
            showBanner(trDyn("RETROACHIEVEMENTS"),
                       ev.title.empty() ? trDyn("RetroAchievements") : ev.title,
                       ev.subtitle, -1, 0, 0.36f, 0.62f, 0.96f);
            break;
        case RaUiEvent::Login:
            if (ev.ok)
                showBanner(trDyn("SIGNED IN"),
                           ev.title.empty() ? trDyn("RetroAchievements") : ev.title,
                           trDyn("Achievements are now active."), -1, 0, 0.34f, 0.80f, 0.46f);
            else
                showBanner(trDyn("RETROACHIEVEMENTS"), trDyn("Sign-in failed"),
                           ev.subtitle, -1, 0, 0.93f, 0.36f, 0.34f);
            break;
        case RaUiEvent::LeaderboardSubmitted:
            showBanner(trDyn("LEADERBOARD"), ev.title, ev.subtitle, -1, 0, 0.36f, 0.62f, 0.96f);
            break;
        case RaUiEvent::ServerError:
            showBanner(trDyn("RETROACHIEVEMENTS"), trDyn("Server error"), ev.subtitle,
                       -1, 0, 0.93f, 0.36f, 0.34f);
            break;
        case RaUiEvent::ChallengeShow:
            // An achievement is primed (its trigger conditions are active): keep
            // its badge so drawRaIndicators can show it over the game. Fetch the
            // badge only when this id is newly primed, not on every repeat event:
            // the indicator draws from the on-disk cache (warmed by prefetch), so
            // re-enqueuing per event just front-queues a disk read + PNG decode
            // that the banner-only badge consumer then discards.
            if (ev.id) {
                bool isNew = mRaChallenge.find(ev.id) == mRaChallenge.end();
                mRaChallenge[ev.id] = ev.badgeUrl;
                if (isNew && mRa && !ev.badgeUrl.empty())
                    mRa->enqueueBadgeDownload(ev.id, ev.badgeUrl);
            }
            break;
        case RaUiEvent::ChallengeHide:
            mRaChallenge.erase(ev.id);
            break;
        case RaUiEvent::ProgressShow:
            // Measured-progress update for an achievement (e.g. "23/50"): show a
            // brief popup. ev.subtitle is the measured string, ev.title the name.
            if (ev.id) {
                bool idChanged = (mRaProgressId != ev.id);
                mRaProgressId = ev.id;
                mRaProgressText = ev.subtitle.empty()
                                      ? ev.title
                                      : (ev.title + "    " + ev.subtitle);
                mRaProgressBadgeUrl = ev.badgeUrl;
                mRaProgressUntilMs = android::elapsedRealtime() + 4000;  // safety tail
                // PROGRESS_INDICATOR_UPDATE fires on every measured-value change
                // (e.g. each ring collected), so only fetch the badge when the
                // achievement itself changes, not on every value tick.
                if (idChanged && mRa && !ev.badgeUrl.empty())
                    mRa->enqueueBadgeDownload(ev.id, ev.badgeUrl);
            }
            break;
        case RaUiEvent::ProgressHide:
            mRaProgressId = 0;
            mRaProgressText.clear();
            break;
        default:
            break;
    }
}

void OverlayMenu::showBanner(const std::string& header, const std::string& title,
                             const std::string& desc, int points, uint32_t achId,
                             float ar, float ag, float ab, const std::string& badgeUrl) {
    // Queue the banner so simultaneous unlocks (a level can pop several at once)
    // are shown one after another instead of overwriting each other. If nothing
    // is on screen, start it immediately.
    BannerSpec b;
    b.header = header; b.title = title; b.desc = desc; b.badgeUrl = badgeUrl;
    b.points = points; b.achId = achId;
    b.accent[0] = ar; b.accent[1] = ag; b.accent[2] = ab;
    if (mBannerQueue.size() > 32) mBannerQueue.pop_front();   // sanity cap
    mBannerQueue.push_back(std::move(b));
    if (!mBannerActive) startNextBanner();
}

void OverlayMenu::startNextBanner() {
    if (mBannerQueue.empty()) { mBannerActive = false; return; }
    BannerSpec b = std::move(mBannerQueue.front());
    mBannerQueue.pop_front();
    mBannerHeader  = b.header;
    mBannerTitle   = b.title;
    mBannerDesc    = b.desc;
    mBannerPoints  = b.points;
    mBannerAchId   = b.achId;
    mBannerAccent[0] = b.accent[0];
    mBannerAccent[1] = b.accent[1];
    mBannerAccent[2] = b.accent[2];
    mBannerStartMs = android::elapsedRealtime();
    mBannerDurMs   = 6000;
    mBannerActive  = true;
    // Re-request the badge for this now-current banner. An earlier banner's
    // per-frame badge drain may have already consumed and discarded this one's
    // decoded image, so ask again; it is served instantly from the on-disk
    // cache (no network) and re-queued for drawAchievementBanner to pick up.
    if (mRa && mBannerAchId != 0 && !b.badgeUrl.empty())
        mRa->enqueueBadgeDownload(mBannerAchId, b.badgeUrl);
    // A leftover badge texture from the previous banner is freed lazily in
    // drawAchievementBanner once it no longer matches mBannerAchId.
}

// How long to hold an unlock banner open waiting for its badge to download, and
// the minimum on-screen time once a late badge finally arrives.
static constexpr int64_t kBannerBadgeWaitMaxMs = 12000;
static constexpr int64_t kBannerBadgeTailMs     = 2500;

void OverlayMenu::drawAchievementBanner(drastic_gfx::OverlayGfx& gfx, float sf) {
    using drastic_gfx::rgba;

    // Drain decoded badges (never let the queue back up). Keep one only if it
    // belongs to the banner currently on screen. Render thread -> GL upload OK.
    if (mRa) {
        uint32_t id = 0; std::vector<uint8_t> px; int bw = 0, bh = 0;
        while (mRa->popBadge(&id, &px, &bw, &bh)) {
            if (mBannerActive && id == mBannerAchId && !px.empty() &&
                bw > 0 && bh > 0 &&
                !(mBannerBadgeTex && mBannerBadgeTexAchId == id)) {
                // Already holding this banner's texture means a duplicate decode
                // arrived; skip it so we never destroy and re-create the same GL
                // texture in one frame (texture churn the rk GPU driver dislikes).
                if (mBannerBadgeTex) gfx.destroyTexture(mBannerBadgeTex);
                mBannerBadgeTex = gfx.createImageTexture(px.data(), bw, bh);
                mBannerBadgeTexAchId = id;
                // The badge may have arrived late (a freshly fetched icon on a
                // slow link). Keep it on screen for a minimum tail so it is
                // actually seen, not flashed as the banner expires.
                const int64_t el = android::elapsedRealtime() - mBannerStartMs;
                if (mBannerDurMs - el < kBannerBadgeTailMs)
                    mBannerDurMs = el + kBannerBadgeTailMs;
            }
        }
    }

    if (!mBannerActive) {
        if (mBannerBadgeTex) {
            gfx.destroyTexture(mBannerBadgeTex);
            mBannerBadgeTex = 0; mBannerBadgeTexAchId = 0;
        }
        return;
    }
    if (mBannerBadgeTex && mBannerBadgeTexAchId != mBannerAchId) {
        gfx.destroyTexture(mBannerBadgeTex);
        mBannerBadgeTex = 0; mBannerBadgeTexAchId = 0;
    }

    const int64_t now = android::elapsedRealtime();
    const int64_t t = now - mBannerStartMs;
    // Effective on-screen time. When more banners are queued (several unlocks at
    // once) each shows for a 3s minimum then advances, so a burst clears quickly;
    // a lone banner keeps its full time, held longer only while still waiting for
    // a late badge to arrive (bounded so it never lingers forever).
    const bool backlog = !mBannerQueue.empty();
    const bool awaitingBadge =
        (mBannerAchId != 0 && mBannerBadgeTex == 0 && t < kBannerBadgeWaitMaxMs && !backlog);
    const int64_t effDur = backlog       ? (int64_t) 3000
                         : awaitingBadge ? kBannerBadgeWaitMaxMs
                                         : mBannerDurMs;
    if (t >= effDur) {
        if (mBannerBadgeTex) {
            gfx.destroyTexture(mBannerBadgeTex);
            mBannerBadgeTex = 0; mBannerBadgeTexAchId = 0;
        }
        mBannerActive = false;
        startNextBanner();   // show the next queued unlock, if any
        return;
    }

    const float vw    = (float)gfx.viewportW();
    const float lineH = (float)gfx.fontLineH();
    const float basePx = (float)gfx.fontBasePx();
    auto scaleFor = [&](float px) { return px / basePx; };

    const bool  hasBadge = (mBannerAchId != 0);
    const float pad     = fmaxf(5.0f, lineH * 0.42f);
    const float margin  = lineH * 0.5f;
    const float badgeSz = lineH * 2.0f;
    // Smaller text than before (the previous sizes overflowed the banner).
    const float headPx  = lineH * 0.52f;
    const float titlePx = lineH * 0.76f;
    const float descPx  = lineH * 0.56f;

    // A slightly wider banner so most text fits without scrolling.
    float textColW = lineH * 11.0f;
    float bannerW  = pad + (hasBadge ? badgeSz + pad : 0.0f) + textColW + pad;
    const float maxW = vw - 2.0f * margin;
    if (bannerW > maxW) {
        bannerW = maxW;
        textColW = bannerW - (pad * 2.0f + (hasBadge ? badgeSz + pad : 0.0f));
    }
    const float bannerH = (hasBadge ? badgeSz : lineH * 1.9f) + 2.0f * pad;
    const float radius  = lineH * 0.4f;

    // Slide in / hold / slide out, with a matching alpha fade.
    auto easeOut = [](float p) { float q = 1.0f - p; return 1.0f - q * q * q; };
    auto clamp01 = [](float v) { return fminf(fmaxf(v, 0.0f), 1.0f); };
    float vis = 1.0f;
    const float inMs = 240.0f, outMs = 320.0f;
    if (t < inMs) vis = easeOut(clamp01((float)t / inMs));
    // Fade/slide out against the effective end time (effDur, computed above):
    // the 3s backlog cut, the badge-wait cap, or the full duration, so the
    // slide-out always plays against the time the banner actually leaves.
    const float remain = (float)(effDur - t);
    if (remain < outMs) vis = fminf(vis, clamp01(remain / outMs));
    const float alpha = vis;

    const float xRest = vw - bannerW - margin;
    const float x = xRest + (1.0f - vis) * (bannerW + margin);   // slides in from the right
    const float y = margin;

    const Color accent = rgba(mBannerAccent[0], mBannerAccent[1], mBannerAccent[2], alpha);
    gfx.roundedRect(x - 2.0f, y - 2.0f, bannerW + 4.0f, bannerH + 4.0f,
                    radius + 2.0f, rgba(mBannerAccent[0], mBannerAccent[1],
                                        mBannerAccent[2], alpha * 0.95f));
    gfx.roundedRect(x, y, bannerW, bannerH, radius,
                    rgba(0.06f, 0.07f, 0.11f, alpha * 0.97f));

    float colX = x + pad;
    if (hasBadge) {
        const float bx = x + pad;
        const float by = y + (bannerH - badgeSz) * 0.5f;
        if (mBannerBadgeTex) gfx.drawImage(mBannerBadgeTex, bx, by, badgeSz, badgeSz, alpha);
        else gfx.roundedRect(bx, by, badgeSz, badgeSz, radius * 0.6f,
                             rgba(0.16f, 0.17f, 0.22f, alpha));
        colX = bx + badgeSz + pad;
    }
    const float headScale  = scaleFor(headPx);
    const float titleScale = scaleFor(titlePx);
    const float descScale  = scaleFor(descPx);

    // A robust ticker for text that overflows the column: wrap with a gap and
    // window a fitting substring (no GL clipping needed, works at any opacity).
    auto rowText = [&](const std::string& s, float scale, float rowY, float colW,
                       Color col) {
        if (s.empty()) return;
        if (gfx.measure(s.c_str(), scale) <= colW) {
            gfx.text(s.c_str(), colX, rowY, scale, col);
            return;
        }
        std::string scroll = s + "     ";
        int n = (int)scroll.size();
        int shift = (int)(((t / 220) % n + n) % n);
        std::string rot = scroll.substr(shift) + scroll.substr(0, shift);
        std::string visStr;
        for (size_t i = 0; i < rot.size(); i++) {
            std::string cand = visStr; cand += rot[i];
            if (gfx.measure(cand.c_str(), scale) > colW) break;
            visStr = cand;
        }
        gfx.text(visStr.c_str(), colX, rowY, scale, col);
    };

    // Header row (accent) with an optional "+N" points chip on the right.
    float headColW = textColW;
    const float yHead = y + pad * 0.55f;
    if (mBannerPoints >= 0) {
        char pts[24];
        snprintf(pts, sizeof(pts), "+%d", mBannerPoints);
        const float ptw = gfx.measure(pts, headScale);
        gfx.text(pts, x + bannerW - pad - ptw, yHead, headScale, accent);
        headColW = textColW - ptw - pad;
    }
    rowText(mBannerHeader, headScale, yHead, headColW, accent);

    const float yTitle = yHead + headPx + 3.0f * sf;
    rowText(mBannerTitle, titleScale, yTitle, textColW, rgba(1.0f, 1.0f, 1.0f, alpha));

    if (!mBannerDesc.empty()) {
        const float yDesc = yTitle + titlePx + 3.0f * sf;
        rowText(mBannerDesc, descScale, yDesc, textColW, rgba(0.80f, 0.82f, 0.88f, alpha));
    }
}

namespace {
// IHealth is Android's framework-level battery source of truth (the same
// service BatteryService reads). Cached so we don't re-resolve the binder
// once per second; dropped on any call failure so we retransition to sysfs
// and re-resolve next tick.
bool queryHealthHal(int* outPercent, bool* outCharging) {
    using aidl::android::hardware::health::BatteryStatus;
    using aidl::android::hardware::health::IHealth;

    static std::shared_ptr<IHealth> sHal;
    if (!sHal) {
        ndk::SpAIBinder binder(AServiceManager_checkService(
                "android.hardware.health.IHealth/default"));
        if (!binder.get()) return false;
        sHal = IHealth::fromBinder(binder);
        if (!sHal) return false;
    }

    int32_t cap = -1;
    auto s1 = sHal->getCapacity(&cap);
    if (!s1.isOk()) {
        sHal.reset();
        return false;
    }
    if (cap < 0) cap = 0;
    if (cap > 100) cap = 100;
    *outPercent = cap;

    BatteryStatus status = BatteryStatus::UNKNOWN;
    auto s2 = sHal->getChargeStatus(&status);
    if (s2.isOk()) {
        *outCharging = (status == BatteryStatus::CHARGING
                        || status == BatteryStatus::FULL);
    }
    return true;
}
} // anonymous namespace

void OverlayMenu::refreshBattery() {
    int64_t now = android::elapsedRealtime();
    if (now < mBatteryNextPollMs && mBatteryPercent >= 0) return;
    mBatteryNextPollMs = now + 1000; // refresh at most 1/s

    int pct = -1;
    bool charging = false;
    if (queryHealthHal(&pct, &charging)) {
        mBatteryPercent = pct;
        mBatteryCharging = charging;
        return;
    }

    // Fallback: read the standard power_supply sysfs nodes. The ::close
    // qualifier is deliberate -- OverlayMenu has a member close() method
    // that would otherwise shadow the POSIX one inside member functions.
    int fd = ::open("/sys/class/power_supply/battery/capacity", O_RDONLY);
    if (fd >= 0) {
        char buf[16] = {};
        ssize_t n = ::read(fd, buf, sizeof(buf) - 1);
        ::close(fd);
        if (n > 0) {
            mBatteryPercent = atoi(buf);
            if (mBatteryPercent < 0) mBatteryPercent = 0;
            if (mBatteryPercent > 100) mBatteryPercent = 100;
        }
    }

    mBatteryCharging = false;
    fd = ::open("/sys/class/power_supply/battery/status", O_RDONLY);
    if (fd >= 0) {
        char buf[32] = {};
        ::read(fd, buf, sizeof(buf) - 1);
        ::close(fd);
        if (strncmp(buf, "Charging", 8) == 0
                || strncmp(buf, "Full", 4) == 0) {
            mBatteryCharging = true;
        }
    }
}

void OverlayMenu::drawBatteryIndicator(drastic_gfx::OverlayGfx& gfx,
                                       float vw, float sf) {
    if (mBatteryPercent < 0) return; // nothing readable

    int pct = mBatteryPercent;
    if (pct < 0) pct = 0;
    if (pct > 100) pct = 100;

    char txt[24];
    if (mBatteryCharging) {
        snprintf(txt, sizeof(txt), "+%d%%", pct);
    } else {
        snprintf(txt, sizeof(txt), "%d%%", pct);
    }

    Color col;
    if (mBatteryCharging) {
        col = rgba(0.25f, 0.90f, 0.35f, 1.0f);
    } else if (pct <= 15) {
        col = rgba(0.95f, 0.25f, 0.25f, 1.0f);
    } else if (pct <= 30) {
        col = rgba(0.95f, 0.75f, 0.15f, 1.0f);
    } else {
        col = rgba(0.92f, 0.92f, 0.96f, 1.0f);
    }

    const float textScale = kBatTextScale * sf;
    const float tw = gfx.measure(txt, textScale);
    const float tH = (float)gfx.fontLineH() * textScale;

    const float bodyW = 28.0f * sf * kBatIconScale;
    const float bodyH = 14.0f * sf * kBatIconScale;
    const float capW  = 3.0f  * sf * kBatIconScale;
    const float capH  = 8.0f  * sf * kBatIconScale;
    const float border = 2.0f * sf * kBatIconScale;
    const float innerPad = 2.0f * sf * kBatIconScale;
    const float gap = 6.0f * sf * kBatIconScale;

    float hudW = bodyW + capW + gap + tw;
    float margin = 18.0f * sf;
    float rowY = margin;
    float rowH = bodyH > tH ? bodyH : tH;

    float hudX = margin;
    float bodyX = hudX;
    float bodyY = rowY + (rowH - bodyH) / 2.0f;
    float capX  = bodyX + bodyW;
    float capY  = bodyY + (bodyH - capH) / 2.0f;

    gfx.outline(bodyX, bodyY, bodyW, bodyH, border, col);

    float fillMaxW = bodyW - 2 * innerPad;
    float fillW = fillMaxW * ((float)pct / 100.0f);
    if (fillW < 0.0f) fillW = 0.0f;
    gfx.fillRect(bodyX + innerPad, bodyY + innerPad,
                 fillW, bodyH - 2 * innerPad, col);
    gfx.fillRect(capX, capY, capW, capH, col);

    float tx = capX + capW + gap;
    float ty = rowY + (rowH - tH) / 2.0f;
    gfx.text(txt, tx, ty, textScale, col);
}

void OverlayMenu::drawTimeIndicator(drastic_gfx::OverlayGfx& gfx,
                                    float vw, float sf) {
    time_t now = time(nullptr);
    struct tm local;
    if (!localtime_r(&now, &local)) return;

    char txt[16];
    strftime(txt, sizeof(txt), "%H:%M", &local);

    const Color col = rgba(0.92f, 0.92f, 0.96f, 1.0f);
    const float textScale = kBatTextScale * sf;
    const float tw = gfx.measure(txt, textScale);
    const float tH = (float)gfx.fontLineH() * textScale;
    const float margin = 18.0f * sf;

    float rowY = margin;
    float tx = vw - margin - tw;
    float ty = rowY;
    gfx.text(txt, tx, ty, textScale, col);
}

void OverlayMenu::writePrefsSafe() {
    if (!drastic_prefs::writePrefs(mXmlPath, mPrefs, mAppUid, mAppGid)) {
        toast("Save failed");
        return;
    }
    mDirty = false;
    toast("Saved");
}

void OverlayMenu::commitAndMaybeRelaunch() {
    // Write first, then fire relaunch if the diff requires it.
    if (mDirty) writePrefsSafe();
    if (drastic_prefs::requiresRelaunch(mSavedPrefs, mPrefs)) {
        mRelaunch = true;
    }
}

// Hold-to-repeat navigation, matching the PS3 XMB (NanoMenuInput.cpp):
// 300ms initial delay, then a 200ms base interval that accelerates
// geometrically (divide by 1.4 each repeat) down to a 50ms floor.
static constexpr int64_t kNavInitialDelayMs = 300;
static constexpr int64_t kNavSlowIntervalMs = 200;
static constexpr int64_t kNavMinIntervalMs  = 50;
static constexpr float   kNavAccelMult       = 1.4f;

void OverlayMenu::handleNavUp() {
    if (mRows.empty()) return;
    const int n = (int)mRows.size();
    int c = mCursor[mSection];
    // Skip section-header rows (e.g. the Achievements "- Unlocked -" labels):
    // they are not selectable, so the cursor lands on the next real row.
    for (int k = 0; k < n; k++) {
        c = (c - 1 + n) % n;
        if (mRows[c].tag != kRowHeader) break;
    }
    mCursor[mSection] = c;
}
void OverlayMenu::handleNavDown() {
    if (mRows.empty()) return;
    const int n = (int)mRows.size();
    int c = mCursor[mSection];
    for (int k = 0; k < n; k++) {
        c = (c + 1) % n;
        if (mRows[c].tag != kRowHeader) break;
    }
    mCursor[mSection] = c;
}
void OverlayMenu::adjustCurrent(int dir) {
    int cur = mCursor[mSection];
    if (cur >= 0 && cur < (int)mRows.size() && mRows[cur].onAdjust) {
        mRows[cur].onAdjust(dir);
        rebuildRows();
    }
}
void OverlayMenu::fireNav(NavDir dir) {
    if (mOsk.active()) {
        switch (dir) {
        case NavDir::Up:    mOsk.moveCursor(0, -1); break;
        case NavDir::Down:  mOsk.moveCursor(0, +1); break;
        case NavDir::Left:  mOsk.moveCursor(-1, 0); break;
        case NavDir::Right: mOsk.moveCursor(+1, 0); break;
        case NavDir::None:  break;
        }
        return;
    }
    switch (dir) {
    case NavDir::Up:    handleNavUp();     break;
    case NavDir::Down:  handleNavDown();   break;
    case NavDir::Left:  adjustCurrent(-1); break;
    case NavDir::Right: adjustCurrent(+1); break;
    case NavDir::None:  break;
    }
}
void OverlayMenu::navPress(NavDir dir) {
    if (dir == NavDir::None || dir == mNavHeldDir) return;
    fireNav(dir);   // a tap always moves exactly one step
    mNavHeldDir = dir;
    mNavLastRepeatMs = android::elapsedRealtime();
    mNavRepeatCount = 0;
}
void OverlayMenu::navRelease() {
    mNavHeldDir = NavDir::None;
    mNavLastRepeatMs = 0;
    mNavRepeatCount = 0;
}
void OverlayMenu::tickNavRepeat() {
    if (mNavHeldDir == NavDir::None) return;
    int64_t now = android::elapsedRealtime();
    int64_t interval;
    if (mNavRepeatCount == 0) {
        interval = kNavInitialDelayMs;
    } else {
        interval = (int64_t)((float)kNavSlowIntervalMs /
                             powf(kNavAccelMult, (float)(mNavRepeatCount - 1)));
        if (interval < kNavMinIntervalMs) interval = kNavMinIntervalMs;
    }
    if (now - mNavLastRepeatMs < interval) return;
    fireNav(mNavHeldDir);
    mNavLastRepeatMs = now;
    mNavRepeatCount++;
}

void OverlayMenu::update(const drastic_input::InputActions& a,
                        drastic_input::InputState* input) {
    // Debug: force the Achievements section open for headless menu shots (this
    // platform cannot inject controller input). Gated; default off.
    {
        char md[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.drastic_nano.menu_dbg", md, "0");
        if (md[0] == '1' && (!mOpen || mSection != kSec_Achievements)) {
            mOpen = true;
            mSection = kSec_Achievements;
            rebuildRows();
        }
    }
    // Short-press BACK toggles menu open/close regardless of state.
    if (a.menuToggle) {
        if (mOpen) {
            closeMenu();
        } else if (mRa && mRa->hardcoreActive() && !mRa->canPauseNow()) {
            // RetroAchievements hardcore throttles pause spam: opening the overlay
            // pauses the emulator, so consult rc_client_can_pause first. When it
            // refuses (the player paused too recently) keep playing instead of
            // opening. Gated on the actually-active hardcore state, not the load
            // window, so the menu always opens while RetroAchievements is loading.
            toast("Pausing is limited in hardcore. Keep playing.");
        } else {
            openMenu();
        }
        return;
    }

    if (!mOpen) {
        // Forward mid-game side-effects of action remaps. The input
        // layer surfaces Quick Save / Quick Load / Reset-via-menu as
        // edge flags; treat them as implicit overlay commands even
        // when the menu isn't visible.
        if (mRunner) {
            if (a.actQuickSave) mRunner->saveStateSlot(0);
            // Quick-load is a save-state load, disabled in hardcore.
            if (a.actQuickLoad && !mRaHardcore) mRunner->loadStateSlot(0);
        }
        return;
    }

    // Capture-key path (controls rebind): consume any android keycode
    // the input layer pushed up and write it into the current action
    // slot.
    if (mCaptureKey) {
        if (a.capturedAndroidKc != 0 && mCaptureActionIdx >= 0) {
            mPrefs.keymap[0][mCaptureActionIdx] = a.capturedAndroidKc;
            mDirty = true;
            mCaptureKey = false;
            mCaptureActionIdx = -1;
            // Rebuild reverse-lookup in the input layer.
            if (input) drastic_input::applyPrefs(input, mPrefs);
            rebuildRows();
            toast("Bound");
        } else if (a.navCancel) {
            mCaptureKey = false;
            mCaptureActionIdx = -1;
            toast("Cancelled");
        }
        return;
    }

    // On-screen keyboard active: route all navigation to it. The
    // hold-to-repeat scheduler drives the key cursor (fireNav forwards to
    // mOsk.moveCursor); A presses the focused key, B backspaces / cancels.
    if (mOsk.active()) {
        NavDir held = NavDir::None;
        if (a.navUpHeld)         held = NavDir::Up;
        else if (a.navDownHeld)  held = NavDir::Down;
        else if (a.navLeftHeld)  held = NavDir::Left;
        else if (a.navRightHeld) held = NavDir::Right;
        if (held != mNavHeldDir) {
            if (held == NavDir::None) navRelease();
            else                      navPress(held);
        } else {
            tickNavRepeat();
        }
        if (a.navAccept) mOsk.activate();
        if (a.navCancel) mOsk.onBackspace();
        if (a.navPrevTab) mOsk.toggleShift();   // L = Shift
        if (a.navNextTab) mOsk.toggleSym();     // R = Sym page

        // Touchscreen: the keyboard is on the bottom DS panel, which is the
        // touch panel. Real finger taps (input->touchReal, never the analog-
        // stick stylus) press the key under the finger on the press edge;
        // sliding while held just moves the focus. touchDs is 0..255 / 0..191.
        if (input) {
            if (!mOskTouchInit) {
                mOskTouchFlipX = property_get_bool(
                        "persist.gammaos.drastic_nano.osk_touch_flipx", false);
                mOskTouchFlipY = property_get_bool(
                        "persist.gammaos.drastic_nano.osk_touch_flipy", false);
                mOskTouchInit = true;
            }
            bool down = input->touchReal && !mPrevOskTouch;
            if (input->touchReal) {
                float nx = (float)input->touchDsX / 256.0f;
                float ny = (float)input->touchDsY / 192.0f;
                if (mOskTouchFlipX) nx = 1.0f - nx;
                if (mOskTouchFlipY) ny = 1.0f - ny;
                if (down) mOsk.touchTap(nx, ny);
                else      mOsk.touchMove(nx, ny);
            }
            mPrevOskTouch = input->touchReal;
        }
        return;
    }

    // Achievements section auto-refresh: the RA client loads login + the
    // achievement set on its own thread, so a section opened mid-load would
    // otherwise cache a stale "Loading..." / "no achievements". Rebuild when the
    // RA UI generation advances (login resolved, game loaded, snapshot ready),
    // and also whenever the login or game-active state diverges from what the
    // current rows were built for, so the list always converges to reality even
    // if a generation bump and the state flags are observed slightly apart.
    if (mSection == kSec_Achievements && mRa) {
        if (mRa->uiGeneration() != mRaUiGen ||
            mRa->isLoggedIn()   != mRaShownLoggedIn ||
            mRa->gameActive()   != mRaShownActive) {
            rebuildRows();   // rebuildAchievements re-syncs the baselines
        }
    }

    // Bottom-screen RA panel touch: a finger drag scrolls the leaderboard list.
    // The bottom DS panel is the touch panel; only real finger touches count.
    if (wantsRaBottomPanel() && input) {
        if (!mOskTouchInit) {
            mOskTouchFlipX = property_get_bool(
                    "persist.gammaos.drastic_nano.osk_touch_flipx", false);
            mOskTouchFlipY = property_get_bool(
                    "persist.gammaos.drastic_nano.osk_touch_flipy", false);
            mOskTouchInit = true;
        }
        if (input->touchReal) {
            float nx = (float)input->touchDsX / 256.0f;
            float ny = (float)input->touchDsY / 192.0f;
            if (mOskTouchFlipX) nx = 1.0f - nx;
            if (mOskTouchFlipY) ny = 1.0f - ny;
            raBottomTouch(!mPrevRaTouch, true, nx, ny);
        } else {
            raBottomTouch(false, false, 0.0f, 0.0f);
            // Inertial scrolling: a flick keeps gliding and decays with friction,
            // like a phone list.
            if (fabsf(mRaScrollVel) > 0.3f) {
                mRaBottomScroll += mRaScrollVel;
                mRaScrollVel *= 0.90f;
                if (mRaBottomScroll < 0.0f) { mRaBottomScroll = 0.0f; mRaScrollVel = 0.0f; }
                if (mRaBottomScroll > mRaBottomMaxScroll) {
                    mRaBottomScroll = mRaBottomMaxScroll; mRaScrollVel = 0.0f;
                }
            } else {
                mRaScrollVel = 0.0f;
            }
        }
        mPrevRaTouch = input->touchReal;
    } else if (!mSingleScreen) {
        // Dual-screen only: left the bottom panel (closed overlay or another
        // section), so drop the drill-in and reset scroll state, matching
        // closeMenu(). On a single-screen device this branch must NOT run every
        // frame -- wantsRaBottomPanel() is always false there, and clobbering
        // mRaOpenLbId each frame would reset the rankings drill-in the moment
        // raSingleAccept opens it (the "Loading rankings..." that never resolves).
        // The single-screen drill-in owns mRaOpenLbId/mRaView via raSingle* and
        // the tab-switch / closeMenu resets.
        mPrevRaTouch = false;
        mRaOpenLbId = 0;
        mRaBottomScroll = 0.0f;
        mRaScrollVel = 0.0f;
    }

    // Normal navigation.
    if (a.navPrevTab) {
        mRaView = 0;   // leave any single-screen RA drill-in when switching tabs
        mSection = (Section)((mSection + kSec_COUNT - 1) % kSec_COUNT);
        rebuildRows();
    }
    if (a.navNextTab) {
        mRaView = 0;
        mSection = (Section)((mSection + 1) % kSec_COUNT);
        rebuildRows();
    }
    // Hold-to-repeat scroll/adjust (PS3 XMB method): edge-detect the held
    // dpad level, fire one step on press, then auto-repeat with geometric
    // acceleration so long cheat lists are easy to traverse.
    NavDir held = NavDir::None;
    if (a.navUpHeld)         held = NavDir::Up;
    else if (a.navDownHeld)  held = NavDir::Down;
    else if (a.navLeftHeld)  held = NavDir::Left;
    else if (a.navRightHeld) held = NavDir::Right;
    const bool raSub =
            (mSingleScreen && mSection == kSec_Achievements && mRaView != 0);
    if (raSub) {
        // Single-screen RA drill-in: edge-triggered step (short lists / page
        // scroll), routed to the active view instead of the list cursor.
        if (held != mNavHeldDir) {
            if (held != NavDir::None) raSingleNav(held);
            mNavHeldDir = held;
        }
    } else if (held != mNavHeldDir) {
        if (held == NavDir::None) navRelease();
        else                      navPress(held);   // fires one step now
    } else {
        tickNavRepeat();
    }
    if (a.navAccept) {
        // The single-screen RA drill-in claims Accept first (open detail /
        // rankings); only a non-achievement row falls through to its onAccept.
        if (!(mSingleScreen && mSection == kSec_Achievements && raSingleAccept())) {
            int cur = mCursor[mSection];
            if (cur >= 0 && cur < (int)mRows.size() && mRows[cur].onAccept) {
                mRows[cur].onAccept();
                rebuildRows();
            }
        }
    }
    if (a.navCancel) {
        // In a single-screen RA sub-view, Cancel steps back one level instead
        // of closing the menu.
        if (!(mSingleScreen && mSection == kSec_Achievements && raSingleCancel()))
            closeMenu();
    }

    // Keep the input layer in sync with any deadzone / analog-touch
    // change made via the Controls tab this frame. Cheap: rebuilds
    // the 29-entry keymap lookup.
    if (input && mDirty) {
        drastic_input::applyPrefs(input, mPrefs);
    }
}

void OverlayMenu::rebuildRows() {
    mRows.clear();
    switch (mSection) {
    case kSec_Save:     rebuildSave();     break;
    case kSec_Video:    rebuildVideo();    break;
    case kSec_Audio:    rebuildAudio();    break;
    case kSec_Controls: rebuildControls(); break;
    case kSec_Cheats:   rebuildCheats();   break;
    case kSec_Achievements: rebuildAchievements(); break;
    default: break;
    }
    if (mCursor[mSection] >= (int)mRows.size()) {
        mCursor[mSection] = (int)mRows.size() - 1;
    }
    if (mCursor[mSection] < 0) mCursor[mSection] = 0;
    // Never rest the cursor on a non-selectable section header (the rich
    // Achievements list groups rows under "- Unlocked -" / "- Locked -").
    {
        int n = (int)mRows.size();
        for (int k = 0; k < n && mCursor[mSection] < n &&
                        mRows[mCursor[mSection]].tag == kRowHeader; k++) {
            mCursor[mSection] = (mCursor[mSection] + 1) % n;
        }
    }
}

void OverlayMenu::rebuildSave() {
    // Game lifecycle rows at the top of the (default) Save States
    // section, so they are the first thing the user sees on opening
    // the overlay.

    // Auto-load save state on launch. A nano-launcher behaviour (not a
    // real drastic setting), persisted in a system property so it
    // survives reboots without polluting drastic's own prefs XML. When
    // on, the next launch of a game restores its most recent save slot
    // (see the auto-load hook in main.cpp's run loop).
    {
        bool autoLoad = property_get_bool(
                "persist.gammaos.drastic_nano.autoload", true);
        RowAction r;
        r.label = "Auto Load State on Launch";
        r.value = autoLoad ? "On" : "Off";
        auto toggle = [this]() {
            bool cur = property_get_bool(
                    "persist.gammaos.drastic_nano.autoload", true);
            property_set("persist.gammaos.drastic_nano.autoload",
                         cur ? "0" : "1");
            toast(cur ? "Auto load: Off" : "Auto load: On");
            rebuildRows();   // refresh the On/Off value
        };
        r.onAccept = toggle;
        r.onAdjust = [toggle](int) { toggle(); };
        mRows.push_back(std::move(r));
    }

    // Restart Game: reboot the ROM from the title. We do NOT use
    // drastic's in-process soft reset (resetDS): the boot-race longjmp
    // patch at libdrastic+0x17304 (applied at init so a reset-style
    // longjmp cannot fire before setjmp populates the jmp_buf) also
    // neuters resetDS's own loop-restart longjmp, so the soft reset
    // half-completes and freezes the game. Instead request a fresh
    // relaunch: main.cpp skips the slot-9 autosave, sets boot_fresh, and
    // fires the relaunch handshake (gammaos-nano re-launches the ROM,
    // which boots fresh because boot_fresh forces auto-load off).
    {
        RowAction r;
        r.label = "Restart Game";
        r.onAccept = [this]() {
            mRestartFresh = true;
            closeMenu();
            toast("Restarting...");
        };
        mRows.push_back(std::move(r));
    }

    // Exit Game: graceful exit back to the XMB, identical teardown to a
    // back-button hold (DrasticRunner autosave -> session_done -> the
    // XMB restarts). main.cpp polls exitAppRequested().
    {
        RowAction r;
        r.label = "Exit Game";
        r.onAccept = [this]() {
            mExitApp = true;
            closeMenu();
        };
        mRows.push_back(std::move(r));
    }

    // Power Off / Reboot: the in-DRM power controls. gammaos-nano is stopped
    // during a DRM session so its overlay Quick Menu is unreachable; these give
    // the same graceful save + power action from inside the game. main.cpp saves
    // slot 9 (and arms Quick Resume when enabled) before the power action.
    {
        RowAction r;
        r.label = "Power Off";
        r.onAccept = [this]() {
            mPowerOff = true;
            closeMenu();
            toast("Powering off...");
        };
        mRows.push_back(std::move(r));
    }
    {
        RowAction r;
        r.label = "Reboot";
        r.onAccept = [this]() {
            mReboot = true;
            closeMenu();
            toast("Rebooting...");
        };
        mRows.push_back(std::move(r));
    }

    for (int slot = 0; slot < 9; slot++) {
        char label[64];
        snprintf(label, sizeof(label), trDyn("Save to Slot %d"), slot);
        RowAction r;
        r.label = label;
        if (slotFileExists(slot)) r.label += trDyn(" (overwrite)");
        r.onAccept = [this, slot]() {
            if (!mRunner) return;
            if (mRunner->saveStateSlot(slot)) {
                char msg[64];
                snprintf(msg, sizeof(msg), trDyn("Saved slot %d"), slot);
                toast(msg);
                // Fix up ownership of the new .dss so the real drastic
                // app can read it later.
                if (mAppUid != 0) {
                    char path[512];
                    snprintf(path, sizeof(path), "%s/%s_%d.dss",
                             mSavestatesDir.c_str(),
                             mRomBase.c_str(), slot);
                    chown(path, mAppUid, mAppGid);
                    chmod(path, 0660);
                }
            } else {
                toast("Save failed");
            }
        };
        mRows.push_back(std::move(r));
    }
    // RetroAchievements hardcore forbids loading save states (saving is fine).
    if (mRaHardcore) {
        RowAction r;
        r.label = "Load State disabled (RetroAchievements hardcore)";
        mRows.push_back(std::move(r));
    }
    for (int slot = 0; !mRaHardcore && slot < 9; slot++) {
        char label[64];
        snprintf(label, sizeof(label), trDyn("Load from Slot %d"), slot);
        RowAction r;
        r.label = label;
        r.value = slotFileExists(slot) ? "ready" : "empty";
        r.onAccept = [this, slot]() {
            if (!mRunner) return;
            if (!slotFileExists(slot)) {
                toast("Empty slot");
                return;
            }
            if (mRunner->loadStateSlot(slot)) {
                char msg[64];
                snprintf(msg, sizeof(msg), trDyn("Loaded slot %d"), slot);
                toast(msg);
            } else {
                toast("Load failed");
            }
        };
        mRows.push_back(std::move(r));
    }
}

void OverlayMenu::buildCheatModel() {
    mCheatFolders.clear();
    mCheatModelValid = true;
    if (!mRunner || !mRunner->hasCheatApi()) return;

    int folderCount = mRunner->cheatFolderCount();
    int cheatTotal  = mRunner->cheatCount();
    for (int f = 0; f < folderCount; f++) {
        CheatFolder cf;
        cf.name = mRunner->cheatFolderName(f);
        cf.multiSelect = mRunner->cheatFolderMultiSelect(f);
        mCheatFolders.push_back(std::move(cf));
    }
    // Group cheats under their folderId; out-of-range -> synthetic Assorted.
    std::vector<int> leftover;
    for (int i = 0; i < cheatTotal; i++) {
        int fid = mRunner->cheatFolderId(i);
        if (fid >= 0 && fid < folderCount) {
            mCheatFolders[fid].children.push_back(i);
            mCheatFolders[fid].childNames.push_back(mRunner->cheatName(i));
        } else {
            leftover.push_back(i);
        }
    }
    if (!leftover.empty()) {
        CheatFolder cf;
        cf.name = "Assorted";
        cf.multiSelect = true;
        for (int g : leftover) {
            cf.children.push_back(g);
            cf.childNames.push_back(mRunner->cheatName(g));
        }
        mCheatFolders.push_back(std::move(cf));
    }

    // Cache custom cheat names (parallel to index) so per-input rebuilds
    // don't re-allocate byte[]s.
    mCustomCheatNames.clear();
    if (mRunner->hasCustomCheatApi()) {
        int cc = mRunner->customCheatCount();
        for (int i = 0; i < cc; i++) {
            mCustomCheatNames.push_back(mRunner->customCheatName(i));
        }
    }
}

bool OverlayMenu::cheatMatchesFilter(const std::string& name) const {
    if (mCheatFilter.empty()) return true;
    std::string lo;
    lo.reserve(name.size());
    for (char c : name) lo.push_back((char)std::tolower((unsigned char)c));
    return lo.find(mCheatFilter) != std::string::npos;
}

void OverlayMenu::openCheatSearch() {
    mOsk.open("Search cheats", mCheatFilter, DrasticOsk::Mode::Text,
              [this](const std::string& q) {
                  std::string lo;
                  lo.reserve(q.size());
                  for (char c : q)
                      lo.push_back((char)std::tolower((unsigned char)c));
                  mCheatFilter = lo;
                  rebuildRows();
              });
}

namespace {
// Parse an Action Replay code string into a flat int[] of 32-bit words
// (consecutive address,value pairs). Mirrors CheatEditor: split on
// whitespace, strip each token to hex digits, parse base-16. Returns empty
// on an odd token count (invalid).
std::vector<int> parseArCode(const std::string& s) {
    std::vector<uint32_t> words;
    std::string tok;
    auto flush = [&]() {
        if (tok.empty()) return;
        std::string hex;
        for (char c : tok) if (std::isxdigit((unsigned char)c)) hex.push_back(c);
        if (!hex.empty()) {
            uint32_t v = (uint32_t)strtoull(hex.c_str(), nullptr, 16);
            words.push_back(v);
        }
        tok.clear();
    };
    for (char c : s) {
        if (c == ' ' || c == '\n' || c == '\r' || c == '\t') flush();
        else tok.push_back(c);
    }
    flush();
    std::vector<int> out;
    if (words.size() % 2 != 0) return out;   // odd = invalid
    for (uint32_t w : words) out.push_back((int)w);
    return out;
}
} // namespace

void OverlayMenu::addCustomCheatFlow() {
    if (!mRunner || !mRunner->hasCustomCheatApi()) return;
    mOsk.open("New cheat name", "", DrasticOsk::Mode::Text,
              [this](const std::string& name) {
        if (name.empty()) { toast("Cancelled"); return; }
        std::string cheatName = name;
        mOsk.open("AR code (hex, e.g. 94000130 FCFF0000)", "",
                  DrasticOsk::Mode::Hex,
                  [this, cheatName](const std::string& code) {
            std::vector<int> words = parseArCode(code);
            if (words.empty()) { toast("Invalid cheat code"); return; }
            int rc = mRunner->addCustomCheat(cheatName, words, true);
            if (rc == 0) {
                mRunner->applyCheats();
                mCheatsDirty = false;   // applyCheats already flushed
                toast("Cheat added");
            } else {
                toast("Add failed (already exists?)");
            }
            mCheatModelValid = false;   // re-enumerate to show the new cheat
            rebuildRows();
        });
    });
}

void OverlayMenu::adjustVolume(int dir) {
    // The Android system volume is the single authority: PhoneWindowManager sets
    // every audible stream and publishes persist.gammaos.nano.volume/volmax from the
    // SAME physical VOL key we just read (we do not grab input), and the DS core's
    // output goes through STREAM_MUSIC so that level already controls it. So this is
    // DISPLAY-ONLY -- we do NOT touch the DS core's internal mixer (pinned at max in
    // main). Mirror gammaos-nano's slider: re-sync the base from PWM's published
    // index at the start of a burst, then move optimistically for instant feedback
    // while PWM catches up and re-publishes. This path only runs on the DRM backend;
    // in SF mode the overlay draws the slider and main does not call us.
    char vmax[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.volmax", vmax, "");
    if (vmax[0]) { int m = atoi(vmax); if (m > 0) mSysVolMax = m; }
    if (mVolHudTimer <= 0) {   // burst start: re-sync from PWM's real index
        char cur[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.volume", cur, "");
        if (cur[0]) mSysVol = atoi(cur);
    }
    mSysVol += dir;
    if (mSysVol < 0)          mSysVol = 0;
    if (mSysVol > mSysVolMax) mSysVol = mSysVolMax;
    mVolHudTimer = 90;   // ~1.5s at 60fps
}

void OverlayMenu::adjustBrightness(int dir) {
    if (!mBrightInit) {
        mBrightLevel = property_get_int32(
                "persist.gammaos.nano.brightness", 128);
        mBrightInit = true;
    }
    int b = mBrightLevel + 16 * dir;
    if (b < 8)   b = 8;       // never fully dark via the keys
    if (b > 255) b = 255;
    mBrightLevel = b;
    android::nanobl::nanoBacklightSet(mBrightLevel);
    char buf[16];
    snprintf(buf, sizeof(buf), "%d", mBrightLevel);
    property_set("persist.gammaos.nano.brightness", buf);
    mBrightHudTimer = 90;
}

void OverlayMenu::drawHud(drastic_gfx::OverlayGfx& gfx) {
    if (mVolHudTimer <= 0 && mBrightHudTimer <= 0) return;
    const float vw = (float)gfx.viewportW();
    const float vh = (float)gfx.viewportH();

    // Adapter onto the shared NanoSliderHud spec (same one gammaos-nano uses),
    // translating its pixel-height text API to OverlayGfx's scale-of-base-px.
    struct GfxBackend {
        drastic_gfx::OverlayGfx& g;
        float basePx;
        void rect(float x, float y, float w, float h,
                  float r, float gr, float b, float a) {
            g.fillRect(x, y, w, h, drastic_gfx::rgba(r, gr, b, a));
        }
        void text(const char* s, float x, float y, float pxH,
                  float r, float gr, float b, float a) {
            g.text(s, x, y, pxH / basePx, drastic_gfx::rgba(r, gr, b, a));
        }
        float measure(const char* s, float pxH) {
            return g.measure(s, pxH / basePx);
        }
    } be{gfx, (float)gfx.fontBasePx()};

    int slot = 0;
    if (mBrightHudTimer > 0) {
        mBrightHudTimer--;
        nano_slider::draw(be, vw, vh, nano_slider::kBrightness,
                          mBrightLevel * 100 / 255, slot++);
    }
    if (mVolHudTimer > 0) {
        mVolHudTimer--;
        // Show the real system volume (set by adjustVolume from
        // persist.gammaos.nano.volume), not the DS core's internal mixer, so the
        // slider matches the actual output level.
        int pct = (mSysVolMax > 0) ? (mSysVol * 100 / mSysVolMax) : 0;
        nano_slider::draw(be, vw, vh, nano_slider::kVolume, pct, slot);
    }
}

void OverlayMenu::rebuildAchievements() {
    if (!mRa) {
        RowAction r;
        r.label = "RetroAchievements unavailable";
        mRows.push_back(std::move(r));
        return;
    }
    // Stay in sync with the RA client so the per-frame watcher only rebuilds on
    // a real change, not on the rebuild this very call performs.
    mRaUiGen = mRa->uiGeneration();
    mRaShownLoggedIn = mRa->isLoggedIn();
    mRaShownActive = mRa->gameActive();

    // Master On/Off toggle, always first. Default OFF when the prop is unset.
    const bool raOn = mRa->isEnabled();
    {
        RowAction r;
        r.label = "RetroAchievements";
        r.value = raOn ? "On" : "Off";
        auto toggle = [this]() {
            bool cur = mRa->isEnabled();
            mRa->setEnabled(!cur);
            toast(cur ? "RetroAchievements: Off" : "RetroAchievements: On");
            rebuildRows();   // refresh the On/Off value (and the rows below) in place
        };
        r.onAccept = toggle;
        r.onAdjust = [toggle](int) { toggle(); };
        mRows.push_back(std::move(r));
    }
    if (!raOn) {
        RowAction r;
        r.label = "RetroAchievements is off";
        r.tag = kRowLocked;
        mRows.push_back(std::move(r));
        return;
    }

    if (!mRa->isLoggedIn()) {
        {
            RowAction r;
            r.label = "Log In to RetroAchievements";
            r.onAccept = [this]() { startRaLogin(); };
            mRows.push_back(std::move(r));
        }
        {
            RowAction r;
            r.label = "Enter your RetroAchievements account to track achievements.";
            mRows.push_back(std::move(r));
        }
        return;
    }
    {
        RowAction r;
        r.label = "Account";
        r.value = mRa->userDisplayName();
        mRows.push_back(std::move(r));
    }
    {
        // Hardcore toggle. Enabling hardcore restarts the game from the title:
        // the RetroAchievements convention is that a hardcore run starts clean,
        // with no pre-hardcore progress carried in. The restart is raised by the
        // RA client thread when it applies the enable (so it fires for both this
        // menu toggle and the ra_test_hardcore debug hook), and main.cpp turns it
        // into the same fresh process relaunch as the "Restart Game" menu item.
        // drastic's in-process soft reset (resetDS) is neutered by the boot-race
        // longjmp patch and only half-completes, which freezes the game, so a
        // real restart must relaunch the process. The relaunch teardown frees the
        // Achievements bottom-panel textures first (main.cpp calls freeRaTextures
        // before gfx.shutdown), so tearing the GL/DRM context down does not wedge
        // the GPU driver. The relaunched process reads
        // persist.gammaos.drastic_nano.ra_hardcore=1 and boots fresh into
        // hardcore. Disabling hardcore drops to softcore live (no restart). In
        // hardcore, save-state loading, cheats, fast-forward and auto-resume are
        // disabled. The server only credits hardcore unlocks once the client is a
        // registered emulator; this build's User-Agent is not on RA's recognized
        // list yet (the 0-point "Warning: Unknown Emulator" entry), which is why
        // the row below flags pending approval.
        RowAction r;
        r.label = "Hardcore Mode";
        r.value = mRa->hardcorePref() ? "On" : "Off";
        auto toggle = [this]() {
            if (!mRa) return;
            const bool newOn = !mRa->hardcorePref();
            mRa->setHardcorePref(newOn);   // persist + apply; the client thread
                                           // raises the restart on enable
            closeMenu();
            toast(newOn ? "Hardcore on, restarting..." : "Hardcore Mode off");
        };
        r.onAccept = toggle;
        r.onAdjust = [toggle](int /*dir*/) { toggle(); };
        mRows.push_back(std::move(r));
    }
    {
        // RetroAchievements hardcore-compliance note: a new client only becomes
        // eligible for hardcore credit after RA approval and a ~6-month timeline
        // from release, so make users aware before they rely on it.
        RowAction r;
        r.label = "Hardcore credit pending RA approval (~6-month eligibility)";
        r.tag = kRowLocked;
        mRows.push_back(std::move(r));
    }
    // Single-screen devices have no bottom DS panel for leaderboards, so give
    // them a drill-in entry (dual-panel devices show leaderboards on screen 2).
    if (mSingleScreen && !mRa->leaderboardSnapshot().empty()) {
        RowAction r;
        r.label = "View Leaderboards";
        r.onAccept = [this]() { mRaView = 2; mLbCursor = 0; };
        mRows.push_back(std::move(r));
    }
    // The snapshot may be the live set (gameActive) or, when the network load
    // has not finished, the on-disk cache from a previous sync (so the list is
    // usable on a poor or absent link instead of stuck on "Loading...").
    const bool live = mRa->gameActive();
    std::vector<NanoRetroAchievements::AchievementInfo> list = mRa->achievementSnapshot();
    if (list.empty()) {
        // Nothing cached yet and the live load has not arrived.
        RowAction r;
        r.label = live ? "This game has no achievements" : "Loading achievements...";
        mRows.push_back(std::move(r));
        return;
    }
    if (!live) {
        // Showing the cached set; unlock state is from the last sync and will
        // update once the network load completes.
        RowAction r;
        r.label = "Offline - showing last synced achievements";
        r.tag = kRowLocked;
        mRows.push_back(std::move(r));
    }
    // Unlocked achievements (recently unlocked + older) grouped together at the
    // top in gold; then the locked ones grouped by their progress bucket, dim.
    bool anyUnlocked = false;
    for (const auto& a : list) if (a.unlocked) { anyUnlocked = true; break; }
    if (anyUnlocked) {
        RowAction hdr; hdr.label = std::string("- ") + trDyn("Unlocked") + " -"; hdr.tag = kRowHeader;
        mRows.push_back(std::move(hdr));
        for (const auto& a : list) {
            if (!a.unlocked) continue;
            RowAction r;
            r.label = a.title;
            r.tag = kRowUnlocked;
            r.detail = a.description;   // shown for the selected row
            r.raAchId = a.id;
            char val[40];
            snprintf(val, sizeof(val), "%u %s", a.points, trDyn("pts"));
            r.value = val;
            mRows.push_back(std::move(r));
        }
    }
    std::string curBucket;
    for (const auto& a : list) {
        if (a.unlocked) continue;
        const std::string b = a.bucket.empty() ? std::string("Locked") : a.bucket;
        if (b != curBucket) {
            curBucket = b;
            RowAction hdr;
            std::string shown = a.bucket.empty() ? trDyn("Locked") : b;
            hdr.label = std::string("- ") + shown + " -";
            hdr.tag = kRowHeader;
            mRows.push_back(std::move(hdr));
        }
        RowAction r;
        r.label = a.title;
        r.tag = kRowLocked;
        r.detail = a.description;
        r.raAchId = a.id;
        char val[72];
        // Show measured progress (e.g. "23/50") for measured achievements so the
        // Measured flag is visible in the list, not only as a gameplay popup.
        if (!a.measuredProgress.empty())
            snprintf(val, sizeof(val), "%s    %u %s",
                     a.measuredProgress.c_str(), a.points, trDyn("pts"));
        else
            snprintf(val, sizeof(val), "%u %s", a.points, trDyn("pts"));
        r.value = val;
        mRows.push_back(std::move(r));
    }
}

void OverlayMenu::startRaLogin() {
    // Chained on-screen keyboards: username, then password, then request login.
    mOsk.open("RetroAchievements username", "", DrasticOsk::Mode::Text,
        [this](const std::string& user) {
            if (user.empty()) return;
            std::string u = user;
            mOsk.open("RetroAchievements password", "", DrasticOsk::Mode::Text,
                [this, u](const std::string& pass) {
                    if (pass.empty() || !mRa) return;
                    mRa->requestLogin(u, pass);
                    toast("Logging in to RetroAchievements...", 2500);
                });
        });
}

void OverlayMenu::rebuildCheats() {
    if (!mRunner || !mRunner->hasCheatApi()) {
        RowAction r;
        r.label = "Cheats not available";
        mRows.push_back(std::move(r));
        return;
    }
    // RetroAchievements hardcore forbids gameplay-altering cheats.
    if (mRaHardcore) {
        RowAction r;
        r.label = "Cheats disabled (RetroAchievements hardcore)";
        mRows.push_back(std::move(r));
        return;
    }
    if (!mCheatModelValid) buildCheatModel();

    // Search row (opens the keyboard to filter by name).
    {
        RowAction r;
        r.label = "Search";
        r.value = mCheatFilter.empty() ? "(all)" : mCheatFilter;
        r.onAccept = [this]() { openCheatSearch(); };
        mRows.push_back(std::move(r));
    }

    // Show filter: All / Enabled / Disabled. Cycle with A or Left/Right.
    {
        static const char* const kShow[] = {"All", "Enabled", "Disabled"};
        RowAction r;
        r.label = "Show";
        r.value = kShow[mCheatShow % 3];
        r.onAccept = [this]() { mCheatShow = (mCheatShow + 1) % 3; };
        r.onAdjust = [this](int dir) {
            mCheatShow = (mCheatShow + (dir > 0 ? 1 : 2)) % 3;
        };
        mRows.push_back(std::move(r));
    }

    // Compute whether any cheat is currently enabled (cheap byte reads) so
    // the All-Cheats row can offer the right action.
    int total = 0;
    bool anyOn = false;
    for (const auto& cf : mCheatFolders) {
        total += (int)cf.children.size();
        for (int g : cf.children)
            if (mRunner->cheatEnabled(g)) { anyOn = true; break; }
    }
    if (mRunner->hasCustomCheatApi()) {
        int cc = (int)mCustomCheatNames.size();
        for (int i = 0; i < cc && !anyOn; i++)
            if (mRunner->customCheatEnabled(i)) anyOn = true;
    }

    // Enable / disable all toggle.
    if (total > 0 || !mCustomCheatNames.empty()) {
        RowAction r;
        r.label = "All Cheats";
        r.value = anyOn ? "Disable All" : "Enable All";
        bool turnOn = !anyOn;
        r.onAccept = [this, turnOn]() {
            int n = mRunner->cheatCount();
            for (int i = 0; i < n; i++) mRunner->setCheatEnabled(i, turnOn);
            int cc = mRunner->hasCustomCheatApi()
                    ? mRunner->customCheatCount() : 0;
            for (int i = 0; i < cc; i++)
                mRunner->setCustomCheatEnabled(i, turnOn);
            mCheatsDirty = true;
            toast(turnOn ? "All cheats enabled" : "All cheats disabled");
        };
        mRows.push_back(std::move(r));
    }

    // Preloaded cheats, folder-grouped, filtered by the search query.
    bool anyShown = false;
    for (size_t fi = 0; fi < mCheatFolders.size(); fi++) {
        const CheatFolder& cf = mCheatFolders[fi];
        if (cf.children.empty()) continue;
        // Collect matching children first so we can skip empty folders.
        // Honor both the name filter and the Show (all/enabled/disabled)
        // filter.
        std::vector<size_t> match;
        for (size_t ci = 0; ci < cf.children.size(); ci++) {
            const std::string& nm = ci < cf.childNames.size()
                    ? cf.childNames[ci] : std::string();
            if (!cheatMatchesFilter(nm)) continue;
            if (mCheatShow != 0) {
                bool on = mRunner->cheatEnabled(cf.children[ci]);
                if (mCheatShow == 1 && !on) continue;   // Enabled only
                if (mCheatShow == 2 && on)  continue;   // Disabled only
            }
            match.push_back(ci);
        }
        if (match.empty()) continue;
        anyShown = true;
        {
            RowAction h;
            h.label = cf.name.empty() ? "Cheats" : cf.name;
            h.value = cf.multiSelect ? "" : "(one)";
            mRows.push_back(std::move(h));
        }
        for (size_t ci : match) {
            int g = cf.children[ci];
            RowAction r;
            r.label = "  " + (ci < cf.childNames.size() ? cf.childNames[ci]
                                                        : std::string("?"));
            r.value = mRunner->cheatEnabled(g) ? "On" : "Off";
            int folderModelIdx = (int)fi;
            r.onAccept = [this, g, folderModelIdx]() {
                toggleCheat(g, folderModelIdx);
            };
            mRows.push_back(std::move(r));
        }
    }
    if (total == 0) {
        RowAction r;
        r.label = "No cheats for this game";
        mRows.push_back(std::move(r));
    } else if (!anyShown && !mCheatFilter.empty()) {
        RowAction r;
        r.label = std::string(trDyn("No matches for")) + " \"" + mCheatFilter + "\"";
        mRows.push_back(std::move(r));
    }

    // Custom (user) cheats: an Add row + a toggle row per cached custom
    // cheat (filtered). Toggling persists with the preloaded set on close.
    if (mRunner->hasCustomCheatApi()) {
        {
            RowAction h;
            h.label = "Custom Cheats";
            mRows.push_back(std::move(h));
        }
        {
            RowAction r;
            r.label = "  Add custom cheat...";
            r.onAccept = [this]() { addCustomCheatFlow(); };
            mRows.push_back(std::move(r));
        }
        for (size_t i = 0; i < mCustomCheatNames.size(); i++) {
            if (!cheatMatchesFilter(mCustomCheatNames[i])) continue;
            if (mCheatShow != 0) {
                bool on = mRunner->customCheatEnabled((int)i);
                if (mCheatShow == 1 && !on) continue;
                if (mCheatShow == 2 && on)  continue;
            }
            int idx = (int)i;
            RowAction r;
            r.label = "  " + mCustomCheatNames[i];
            r.value = mRunner->customCheatEnabled(idx) ? "On" : "Off";
            // A toggles enable. (Removal is intentionally not wired to the
            // auto-repeating Left/Right here: it would delete the whole
            // custom list while held. A confirm-gated remove is a follow-up;
            // for now custom cheats can be managed in the real drastic app,
            // which shares the same per-game .cht file.)
            r.onAccept = [this, idx]() {
                mRunner->setCustomCheatEnabled(
                        idx, !mRunner->customCheatEnabled(idx));
                mCheatsDirty = true;
            };
            mRows.push_back(std::move(r));
        }
    }
}

void OverlayMenu::toggleCheat(int g, int folderModelIdx) {
    if (!mRunner) return;
    bool now = mRunner->cheatEnabled(g);
    // Enabling inside a radio-group ("select one") folder: disable siblings
    // first, since native setCheatEnabled does not enforce it.
    if (!now && folderModelIdx >= 0 &&
        folderModelIdx < (int)mCheatFolders.size() &&
        !mCheatFolders[folderModelIdx].multiSelect) {
        int cleared = 0;
        for (int sib : mCheatFolders[folderModelIdx].children) {
            if (sib != g && mRunner->cheatEnabled(sib)) {
                mRunner->setCheatEnabled(sib, false);
                cleared++;
            }
        }
        if (cleared > 0) toast("Other cheats in this folder were disabled");
    }
    mRunner->setCheatEnabled(g, !now);
    mCheatsDirty = true;
    // Values refresh on the rebuildRows() the caller runs after onAccept.
}

void OverlayMenu::applyConfigLive() {
    // Rebuild the full drastic config word from the current prefs and push
    // it to the running emulator. applyVideoConfigLive re-asserts the
    // fast-forward state and the GPU fast-path master-state patch, so the
    // change takes effect on the next emulated frame with no relaunch.
    if (mRunner) {
        mRunner->applyVideoConfigLive(
                drastic_prefs::applyConfigBitsFrom(mPrefs));
        // If _Hires3D changed, the DS textures + fxSetup must be re-dimmed
        // on the render thread. Cheap and idempotent (redimDsTextures
        // early-returns when the size is unchanged), so request it on every
        // live config change rather than tracking the hires bit here.
        mRunner->requestDsReDim();
    }
}

void OverlayMenu::rebuildVideo() {
    // Shader picker.
    {
        RowAction r;
        r.label = "Shader";
        r.value = mPrefs.currentFx;
        r.onAdjust = [this](int dir) {
            if (mShaders.empty()) return;
            int idx = 0;
            for (int i = 0; i < (int)mShaders.size(); i++) {
                if (mShaders[i] == mPrefs.currentFx) { idx = i; break; }
            }
            idx = (idx + dir + (int)mShaders.size())
                   % (int)mShaders.size();
            mPrefs.currentFx = mShaders[idx];
            mDirty = true;
            if (mRunner) {
                std::string path = mShadersDir + "/" +
                                   mPrefs.currentFx + ".dfx";
                if (!mRunner->setShaderRuntime(path)) {
                    toast("Shader load failed");
                }
            }
        };
        mRows.push_back(std::move(r));
    }
    // Screen Layout presets (advanced_drastic). These place the two DS screens
    // within a single SurfaceFlinger window or single panel; the render loop
    // re-reads the properties every frame, so the change applies the instant the
    // row is adjusted. On a two-panel session they are inert except for Swap.
    {
        RowAction r;
        r.label = "Screen Layout";
        static const char* const kVals[]   = {"auto", "horizontal",
                                              "vertical", "single"};
        static const char* const kLabels[] = {"Auto", "Side by Side",
                                              "Stacked", "Single Screen"};
        static const int kCount = 4;
        auto curIdx = []() {
            char cur[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.drastic_nano.orientation", cur, "auto");
            for (int i = 0; i < kCount; i++)
                if (strcmp(cur, kVals[i]) == 0) return i;
            return 0;
        };
        r.value = kLabels[curIdx()];
        r.onAdjust = [curIdx](int dir) {
            int idx = (curIdx() + dir + kCount) % kCount;
            property_set("persist.gammaos.drastic_nano.orientation", kVals[idx]);
        };
        mRows.push_back(std::move(r));
    }
    {
        // Layout Preset (advanced_drastic / drastic_layout): a fixed handheld
        // arrangement (Full Screen, Side by Side, picture-in-picture, Big+Small,
        // Stacked...) that OVERRIDES the parametric Screen Layout/Scaling above.
        // "Off" keeps the parametric layout. Live: the render loop re-reads it each
        // frame. Cycles Off (-1) .. presetCount-1.
        RowAction r;
        r.label = "Layout Preset";
        const int n = drastic_nano::presetCount();
        int idx;
        {
            char cur[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.drastic_nano.layout_preset", cur, "-1");
            idx = atoi(cur);
        }
        r.value = (idx >= 0 && idx < n) ? drastic_nano::presetName(idx) : "Off";
        r.onAdjust = [n](int dir) {
            char cur[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.drastic_nano.layout_preset", cur, "-1");
            int i = atoi(cur) + dir;
            if (i < -1) i = n - 1; else if (i >= n) i = -1;
            char buf[16];
            snprintf(buf, sizeof(buf), "%d", i);
            property_set("persist.gammaos.drastic_nano.layout_preset", buf);
        };
        mRows.push_back(std::move(r));
    }
    {
        // PiP Opacity: how see-through the picture-in-picture INSET screen is, so
        // the big screen shows through where they overlap. Only affects the PiP
        // Tiny/Small presets. Live (the render loop re-reads it each frame).
        RowAction r;
        r.label = "PiP Opacity";
        static const char* const kAVals[]   = {"100", "80", "60", "40"};
        static const char* const kALabels[] = {"Opaque", "80%", "60%", "40%"};
        static const int kACount = 4;
        auto curIdx = []() {
            char cur[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.drastic_nano.pip_alpha", cur, "100");
            for (int i = 0; i < kACount; i++)
                if (strcmp(cur, kAVals[i]) == 0) return i;
            return 0;
        };
        r.value = kALabels[curIdx()];
        r.onAdjust = [curIdx](int dir) {
            int idx = (curIdx() + dir + kACount) % kACount;
            property_set("persist.gammaos.drastic_nano.pip_alpha", kAVals[idx]);
        };
        mRows.push_back(std::move(r));
    }
    {
        // PiP Corner: which corner the overlapping picture-in-picture INSET
        // screen sits in (default Bottom Right). Only affects the PiP presets
        // when the inset overlaps the big screen (a 4:3 / portrait panel); on a
        // wide panel the inset sits side by side and the corner has no effect.
        // Live (the render loop re-reads it each frame).
        RowAction r;
        r.label = "PiP Corner";
        static const char* const kCVals[]   = {"br", "bl", "tr", "tl"};
        static const char* const kCLabels[] = {"Bottom Right", "Bottom Left",
                                               "Top Right", "Top Left"};
        static const int kCCount = 4;
        auto curIdx = []() {
            char cur[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.drastic_nano.pip_corner", cur, "br");
            for (int i = 0; i < kCCount; i++)
                if (strcmp(cur, kCVals[i]) == 0) return i;
            return 0;
        };
        r.value = kCLabels[curIdx()];
        r.onAdjust = [curIdx](int dir) {
            int idx = (curIdx() + dir + kCCount) % kCCount;
            property_set("persist.gammaos.drastic_nano.pip_corner", kCVals[idx]);
        };
        mRows.push_back(std::move(r));
    }
    {
        // Display Rotation: rotates the WHOLE single-panel output (the DS layout
        // AND the overlay) on top of the panel's install orientation, so the
        // console can be held in portrait ("hold it tall"). Real-time: the DRM
        // render loop re-reads the property each frame and re-lays everything out.
        RowAction r;
        r.label = "Display Rotation";
        static const char* const kRVals[]   = {"0", "90", "180", "270"};
        static const char* const kRLabels[] = {"Normal", "90", "180", "270"};
        static const int kRCount = 4;
        auto curIdx = []() {
            char cur[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.drastic_nano.display_rotate", cur, "0");
            for (int i = 0; i < kRCount; i++)
                if (strcmp(cur, kRVals[i]) == 0) return i;
            return 0;
        };
        r.value = kRLabels[curIdx()];
        r.onAdjust = [curIdx](int dir) {
            int idx = (curIdx() + dir + kRCount) % kRCount;
            property_set("persist.gammaos.drastic_nano.display_rotate", kRVals[idx]);
        };
        mRows.push_back(std::move(r));
    }
    {
        RowAction r;
        r.label = "Screen Scaling";
        static const char* const kVals[]   = {"stretch", "none", "1x2x", "2x1x"};
        static const char* const kLabels[] = {"Stretch", "Native",
                                              "Small + Big", "Big + Small"};
        static const int kCount = 4;
        auto curIdx = []() {
            char cur[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.drastic_nano.scaling", cur, "stretch");
            for (int i = 0; i < kCount; i++)
                if (strcmp(cur, kVals[i]) == 0) return i;
            return 0;
        };
        r.value = kLabels[curIdx()];
        r.onAdjust = [curIdx](int dir) {
            int idx = (curIdx() + dir + kCount) % kCount;
            property_set("persist.gammaos.drastic_nano.scaling", kVals[idx]);
        };
        mRows.push_back(std::move(r));
    }
    {
        // Screen Gap: separation between the two DS screens, mainly for stacked
        // (top/bottom) layouts so the two screens read as distinct. Stored as a
        // percent of the leading screen's stacking dimension; the layout folds
        // it into the fit so both screens still fit. Off keeps them touching.
        RowAction r;
        r.label = "Screen Gap";
        static const char* const kGVals[]   = {"0", "8", "16", "25"};
        static const char* const kGLabels[] = {"Off", "Small", "Medium", "Large"};
        static const int kGCount = 4;
        auto curIdx = []() {
            char cur[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.drastic_nano.screen_gap", cur, "0");
            for (int i = 0; i < kGCount; i++)
                if (strcmp(cur, kGVals[i]) == 0) return i;
            return 0;
        };
        r.value = kGLabels[curIdx()];
        r.onAdjust = [curIdx](int dir) {
            int idx = (curIdx() + dir + kGCount) % kGCount;
            property_set("persist.gammaos.drastic_nano.screen_gap", kGVals[idx]);
        };
        mRows.push_back(std::move(r));
    }
    {
        RowAction r;
        r.label = "Swap Screens";
        r.value = property_get_bool("persist.gammaos.drastic_nano.swap", false)
                          ? "On" : "Off";
        auto flip = []() {
            bool cur = property_get_bool(
                    "persist.gammaos.drastic_nano.swap", false);
            property_set("persist.gammaos.drastic_nano.swap", cur ? "0" : "1");
        };
        r.onAccept = flip;
        r.onAdjust = [flip](int) { flip(); };
        mRows.push_back(std::move(r));
    }
    auto addBool = [&](const char* label, bool& field,
                       bool requiresRestart) {
        RowAction r;
        r.label = label;
        r.value = trDyn(field ? "On" : "Off");
        // Live settings apply immediately; the few that genuinely need a
        // relaunch are tagged so the user knows it lands on next launch.
        if (requiresRestart) r.value += trDyn("  (next launch)");
        auto flip = [this, &field, requiresRestart]() {
            field = !field;
            mDirty = true;
            if (!requiresRestart) applyConfigLive();
        };
        r.onAccept = flip;
        r.onAdjust = [flip](int) { flip(); };
        mRows.push_back(std::move(r));
    };
    // Performance profile (live). Cycles Max -> Stock -> Powersave
    // and re-fires the corresponding setclock service via
    // ctl.start, matching the triggers in
    // /vendor/etc/init/init.gammaos_power.rc. We also write the
    // persist property so the chosen profile survives a reboot
    // and so any future trigger evaluations see the right value.
    // This is a live knob (no [restart] tag): the governors change
    // immediately.
    {
        RowAction r;
        r.label = "Performance";
        static const char* const kModes[] = {"max", "stock", "powersave"};
        static const char* const kLabels[] = {"Max", "Stock", "Powersave"};
        static const char* const kSvcs[]   = {"setclock_max",
                                              "setclock_stock",
                                              "setclock_powersave"};
        static const int kModeCount = 3;
        auto currentIdx = []() {
            char cur[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.performance_mode", cur, "max");
            for (int i = 0; i < kModeCount; i++) {
                if (strcmp(cur, kModes[i]) == 0) return i;
            }
            return 0;
        };
        int idx = currentIdx();
        r.value = kLabels[idx];
        r.onAdjust = [currentIdx](int dir) {
            int idx = currentIdx();
            idx = (idx + dir + kModeCount) % kModeCount;
            property_set("persist.gammaos.performance_mode", kModes[idx]);
            property_set("ctl.start", kSvcs[idx]);
            ALOGI("drastic-nano: overlay switched to %s", kModes[idx]);
        };
        mRows.push_back(std::move(r));
    }
    // All three apply live. Hi-res 3D changes the internal 3D render
    // resolution; applyConfigLive() pushes the bit and then requests a
    // render-thread DS-texture re-dim (redimDsTextures) so the textures and
    // fxSetup match the new 256x192 / 512x384 upload size. Threaded 3D and
    // Disable Edge Marking are pure config bits the rasterizer re-reads
    // each frame.
    addBool("Hi-res 3D",           mPrefs.hires3d,      false);
    addBool("Threaded 3D",         mPrefs.threaded3d,   false);
    addBool("Disable Edge Marking",mPrefs.disableEdge,  false);
    // Frame Sync: live toggle. Updates the DRM flip-path global
    // immediately so the next submitted frame picks up the new
    // behavior. No restart needed -- the ring already has the spare
    // slot for the delayed primary flip whether the flag is on or off.
    {
        RowAction r;
        r.label = "Frame Sync";
        r.value = mPrefs.frameSync ? "On" : "Off";
        auto toggle = [this]() {
            mPrefs.frameSync = !mPrefs.frameSync;
            android::sDrmFrameSync = mPrefs.frameSync;
            mDirty = true;
        };
        r.onAccept = toggle;
        r.onAdjust = [toggle](int) { toggle(); };
        mRows.push_back(std::move(r));
    }
    // Frameskip type.
    {
        RowAction r;
        r.label = "Frameskip";
        r.value = (mPrefs.frameskipType == 1) ? "Auto"
                  : ("Fixed " + std::to_string(mPrefs.frameskipValue));
        r.onAdjust = [this](int dir) {
            if (mPrefs.frameskipType == 1) {
                // from Auto, Left -> fixed N, Right -> fixed 0
                mPrefs.frameskipType = 0;
                mPrefs.frameskipValue = (dir > 0) ? 0 : 9;
            } else {
                int v = mPrefs.frameskipValue + dir;
                if (v < 0) { mPrefs.frameskipType = 1; v = 0; }
                else if (v > 9) { v = 9; }
                mPrefs.frameskipValue = v;
            }
            mDirty = true;
            applyConfigLive();
        };
        mRows.push_back(std::move(r));
    }
    // Restart button.
    if (drastic_prefs::requiresRelaunch(mSavedPrefs, mPrefs)) {
        RowAction r;
        r.label = "Restart to apply changes";
        r.onAccept = [this]() {
            commitAndMaybeRelaunch();
        };
        mRows.push_back(std::move(r));
    }
}

void OverlayMenu::rebuildAudio() {
    // Volume (live).
    {
        RowAction r;
        r.label = "Volume";
        r.value = std::to_string(mPrefs.volume) + "/10";
        r.onAdjust = [this](int dir) {
            int v = mPrefs.volume + dir;
            if (v < 0) v = 0; if (v > 10) v = 10;
            mPrefs.volume = v;
            mDirty = true;
            if (mRunner) mRunner->setVolumeRuntime(v * 10);
        };
        mRows.push_back(std::move(r));
    }
    {
        // Audio Latency sizes the OpenSL buffer queue, which drastic
        // reads only once when it creates the audio engine at startGame,
        // so it cannot change live and lands on the next launch.
        RowAction r;
        r.label = "Audio Latency";
        r.value = std::to_string(mPrefs.audioLatency) + trDyn("  (next launch)");
        r.onAdjust = [this](int dir) {
            int v = mPrefs.audioLatency + dir;
            if (v < 0) v = 0; if (v > 4) v = 4;
            mPrefs.audioLatency = v;
            mDirty = true;
        };
        mRows.push_back(std::move(r));
    }
    {
        RowAction r;
        r.label = "Microphone";
        r.value = mPrefs.micEnabled ? "On" : "Off";
        r.onAccept = [this]() {
            mPrefs.micEnabled = !mPrefs.micEnabled;
            mDirty = true;
            applyConfigLive();
        };
        mRows.push_back(std::move(r));
    }
    {
        RowAction r;
        r.label = "Mic Level";
        r.value = std::to_string(mPrefs.micLevel);
        r.onAdjust = [this](int dir) {
            int v = mPrefs.micLevel + dir;
            if (v < 0) v = 0; if (v > 2) v = 2;
            mPrefs.micLevel = v;
            mDirty = true;
            applyConfigLive();
        };
        mRows.push_back(std::move(r));
    }
    if (drastic_prefs::requiresRelaunch(mSavedPrefs, mPrefs)) {
        RowAction r;
        r.label = "Restart to apply changes";
        r.onAccept = [this]() { commitAndMaybeRelaunch(); };
        mRows.push_back(std::move(r));
    }
}

void OverlayMenu::rebuildControls() {
    {
        RowAction r;
        r.label = "Restore Defaults";
        r.value = "";
        r.onAccept = [this]() {
            // Sane gamepad defaults, slot numbering per drastic's real
            // action enum (see DrasticPrefs.h kNumActions comment).
            // Unmapped slots stay -1 so the user can bind them later.
            int def[drastic_prefs::kNumActions];
            for (int i = 0; i < drastic_prefs::kNumActions; i++) {
                def[i] = -1;
            }
            def[0]  = 99;   // X      <- BUTTON_X (BTN_NORTH)
            def[1]  = 100;  // Y      <- BUTTON_Y (BTN_WEST)
            def[2]  = 97;   // B      <- BUTTON_B (BTN_EAST)
            def[3]  = 96;   // A      <- BUTTON_A (BTN_SOUTH)
            def[4]  = 103;  // R      <- BUTTON_R1 (BTN_TR)
            def[5]  = 102;  // L      <- BUTTON_L1 (BTN_TL)
            def[6]  = 108;  // Start  <- BUTTON_START
            def[7]  = 109;  // Select <- BUTTON_SELECT
            def[12] = 19;   // D-Pad Up    <- KEYCODE_DPAD_UP
            def[13] = 22;   // D-Pad Right <- KEYCODE_DPAD_RIGHT
            def[14] = 20;   // D-Pad Down  <- KEYCODE_DPAD_DOWN
            def[15] = 21;   // D-Pad Left  <- KEYCODE_DPAD_LEFT
            def[16] = 104;  // Screen Swap  <- BUTTON_L2 (BTN_TL2)
            def[17] = 105;  // Fast Forward <- BUTTON_R2 (BTN_TR2)
            def[20] = 4;    // Menu   <- KEYCODE_BACK
            def[28] = 107;  // Stylus Touch <- BUTTON_THUMBR (R3)
            for (int a = 0; a < drastic_prefs::kNumActions; a++) {
                mPrefs.keymap[0][a] = def[a];
            }
            mDirty = true;
            toast("Defaults restored");
        };
        mRows.push_back(std::move(r));
    }
    {
        RowAction r;
        r.label = "Analog Stick -> Stylus";
        r.value = mPrefs.analogTouch ? "On" : "Off";
        r.onAccept = [this]() {
            mPrefs.analogTouch = !mPrefs.analogTouch;
            mDirty = true;
        };
        mRows.push_back(std::move(r));
    }
    {
        RowAction r;
        r.label = "Analog Deadzone";
        char buf[32];
        snprintf(buf, sizeof(buf), "%.2f", mPrefs.analogDeadzone);
        r.value = buf;
        r.onAdjust = [this](int dir) {
            float v = mPrefs.analogDeadzone + dir * 0.05f;
            if (v < 0.0f) v = 0.0f; if (v > 0.5f) v = 0.5f;
            mPrefs.analogDeadzone = v;
            mDirty = true;
        };
        mRows.push_back(std::move(r));
    }
    {
        // Portrait Controls: turn the D-Pad + ABXY by this amount so they stay
        // natural when the console is physically held tall. Independent of the
        // screen's Display Rotation (a separate Video setting) so you can rotate
        // the controls without rotating the picture, or vice versa. The input
        // layer reads this property every frame. Off disables the remap.
        RowAction r;
        r.label = "Portrait Controls";
        static const char* const kPVals[]   = {"0", "90", "180", "270"};
        static const char* const kPLabels[] = {"Off", "90", "180", "270"};
        static const int kPCount = 4;
        auto curIdx = []() {
            char cur[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.drastic_nano.portrait_controls", cur, "0");
            for (int i = 0; i < kPCount; i++)
                if (strcmp(cur, kPVals[i]) == 0) return i;
            return 0;
        };
        r.value = kPLabels[curIdx()];
        r.onAdjust = [curIdx](int dir) {
            int idx = (curIdx() + dir + kPCount) % kPCount;
            property_set("persist.gammaos.drastic_nano.portrait_controls", kPVals[idx]);
        };
        mRows.push_back(std::move(r));
    }
    {
        // Portrait Layout: which control drives the direction while Portrait
        // Controls is on. "Right Stick" keeps the D-Pad as the D-Pad and adds
        // the right stick as a second D-Pad. "D-Pad as Face" flips it the other
        // way: the physical D-Pad presses ABXY and the LEFT stick steers, so the
        // console can be held the opposite way up.
        RowAction r;
        r.label = "Portrait Layout";
        static const char* const kLVals[]   = {"0", "1"};
        static const char* const kLLabels[] = {"Right Stick", "D-Pad as Face"};
        static const int kLCount = 2;
        auto curIdx = []() {
            char cur[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.drastic_nano.portrait_layout", cur, "0");
            for (int i = 0; i < kLCount; i++)
                if (strcmp(cur, kLVals[i]) == 0) return i;
            return 0;
        };
        r.value = kLLabels[curIdx()];
        r.onAdjust = [curIdx](int dir) {
            int idx = (curIdx() + dir + kLCount) % kLCount;
            property_set("persist.gammaos.drastic_nano.portrait_layout", kLVals[idx]);
        };
        mRows.push_back(std::move(r));
    }
    // Only expose slots drastic-nano actually handles; the rest stay
    // in the XML untouched (so drastic-app-level bindings the user
    // set up elsewhere aren't clobbered).
    static const int kKnownActionSlots[] = {
        0, 1, 2, 3, 4, 5, 6, 7,        // X Y B A R L Start Select
        12, 13, 14, 15,                // D-Pad Up Right Down Left
        16, 17,                        // Screen Swap / Fast Forward
        20,                            // Menu
        28,                            // Stylus Touch
    };
    for (int a : kKnownActionSlots) {
        RowAction r;
        r.label = drastic_prefs::actionName(a);
        int kc = mPrefs.keymap[0][a];
        r.value = drastic_prefs::androidKeycodeLabel(kc);
        r.onAccept = [this, a]() {
            mCaptureKey = true;
            mCaptureActionIdx = a;
            toast("Press key to bind...");
        };
        r.onAdjust = [this, a](int dir) {
            if (dir < 0) {
                // Left = clear binding
                mPrefs.keymap[0][a] = -1;
                mDirty = true;
            }
        };
        mRows.push_back(std::move(r));
    }
}

// ------------------------------------------------------------------
// Rendering
// ------------------------------------------------------------------

static float clampf(float v, float lo, float hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

// Compose a soft scrim + a small dark-band across the top and bottom of
// the screen. The scrim keeps text readable while the band anchors the
// category row and footer without reintroducing a hard panel frame.
static void drawToast(drastic_gfx::OverlayGfx& gfx,
                      const std::string& msg, float sf) {
    if (msg.empty()) return;
    const char* m = trDyn(msg.c_str());
    float scale = 1.0f * sf;
    float w = gfx.measure(m, scale) + 28.0f * sf;
    float h = gfx.fontLineH() * scale + 14.0f * sf;
    float vw = (float)gfx.viewportW();
    float vh = (float)gfx.viewportH();
    float x = (vw - w) / 2.0f;
    float y = vh * 0.82f;
    gfx.fillRect(x, y, w, h, rgba(0.02f, 0.03f, 0.05f, 0.82f));
    gfx.fillRect(x, y + h - 2.0f * sf, w, 2.0f * sf,
                 rgba(0.40f, 0.75f, 1.0f, 0.9f));
    gfx.text(m, x + 14.0f * sf,
             y + (h - gfx.fontLineH() * scale) / 2.0f, scale,
             rgba(1, 1, 1, 1));
}

void OverlayMenu::draw(drastic_gfx::OverlayGfx& gfx) {
    float vw = (float)gfx.viewportW();
    float vh = (float)gfx.viewportH();
    float sf = clampf(fminf(vw / 1080.0f, vh / 720.0f), kSfMin, kSfMax);
    // The per-element multipliers (kCatActiveSc, kRowSelScale, ...) are tuned
    // for the small DRM handheld panels the overlay first shipped on, where
    // the natural sf sits around 0.5 to 0.7. On a larger, higher-resolution
    // panel -- a SurfaceFlinger handheld presenting a single 960- or 1080-line
    // window -- sf climbs past 1.0 and the menu grows out of proportion: an
    // oversized title with only a few rows visible and the footer hint clipped
    // at the screen edges. Compress the portion of sf above the small-panel
    // regime so the menu holds a consistent on-screen fraction from a 480p
    // panel up through 1080p. Panels at or below the knee are left untouched,
    // so the existing small-panel look does not regress.
    sf = scaleForViewport(sf);

    if (!mOpen) {
        // The volume/brightness HUD and brief toasts still render (and the
        // HUD timers still tick) while the menu is closed -- the user
        // adjusts volume/brightness during gameplay.
        drawHud(gfx);
        // The achievement unlock banner shows during gameplay (top-right).
        drawAchievementBanner(gfx, sf);
        // Challenge (primed) and progress (measured) indicators over the game.
        drawRaIndicators(gfx, sf);
        if (mToast.empty() ||
            android::elapsedRealtime() > mToastUntilMs) return;
        drawToast(gfx, mToast, sf);
        return;
    }

    // Full-screen scrim: darker so the overlay dominates and the game
    // recedes (still faintly visible behind the dark).
    gfx.fillRect(0, 0, vw, vh, rgba(0, 0, 0, 0.88f));

    float catBarY = vh * kCatBarTopFrac;
    drawCategoryBar(gfx, vw, catBarY, sf);

    refreshBattery();
    drawBatteryIndicator(gfx, vw, sf);
    drawTimeIndicator(gfx, vw, sf);

    // The list sits below the category title (accent + dots row) and
    // above the footer. The category block is roughly the title
    // height, an accent gap, and the dots row.
    float categoryBlockH = gfx.fontLineH() * kCatActiveSc * sf
                         + 34.0f * sf;   // accent + dots strip
    float listTop = catBarY + categoryBlockH;
    float footerBandH = gfx.fontLineH() * kFooterScale * sf + 18.0f * sf;
    float listBottom = vh - footerBandH - 12.0f * sf;

    // In the Achievements section, reserve a band above the footer to show the
    // selected achievement's description (RetroAchievements sends one per
    // achievement). Larger text and up to three wrapped lines so it is readable
    // on small panels (e.g. 640x480).
    const bool raSubView =
            (mSingleScreen && mSection == kSec_Achievements && mRaView != 0);

    if (raSubView) {
        const float cxL = vw * kContentLeftFrac;
        const float cxR = vw * kContentRightFrac;
        drawRaSingle(gfx, cxL, listTop, cxR - cxL, listBottom - listTop, sf);
    } else if (mSection == kSec_Achievements) {
        // Rich RetroAchievements list (badge + title + description + unlock date
        // / rarity + points). Each row carries its own description, so there is
        // no separate detail band and the list gets the full content region.
        drawAchievementsList(gfx, vw, listTop, listBottom - listTop, sf);
    } else {
        drawList(gfx, vw, listTop, listBottom - listTop, sf);
    }

    drawFooter(gfx, vw, vh, sf);

    // The on-screen keyboard is NOT drawn here: it renders on the bottom DS
    // screen (drawOsk, called by main.cpp against the secondary FBO) so it
    // does not cover the cheats menu on the top screen.

    // Volume/brightness HUD sits above the menu too.
    drawHud(gfx);
    drawAchievementBanner(gfx, sf);

    if (!mToast.empty() && android::elapsedRealtime() <= mToastUntilMs) {
        drawToast(gfx, mToast, sf);
    }
}

void OverlayMenu::drawOsk(drastic_gfx::OverlayGfx& gfx) {
    if (!mOsk.active()) return;
    // Scrim behind the keyboard on this (bottom) screen, darkening the
    // paused DS frame so the keyboard reads clearly.
    float vw = (float)gfx.viewportW();
    float vh = (float)gfx.viewportH();
    gfx.fillRect(0, 0, vw, vh, rgba(0, 0, 0, 0.72f));
    mOsk.render(gfx);
}


void OverlayMenu::drawCategoryBar(drastic_gfx::OverlayGfx& gfx,
                                  float vw, float barY, float sf) {
    // At 3x body scale, four labels don't fit on a 640-wide panel, so
    // we take a Vita-XMB-style approach: the active category renders as
    // a big centered title, with a short accent underline and a row of
    // pagination dots below for positional context. L/R still cycles
    // through them; the dots tell the user where they are.
    const char* name = trDyn(kSectionNames[mSection]);
    float scale = kCatActiveSc * sf;
    float tw = gfx.measure(name, scale);
    float tx = (vw - tw) / 2.0f;
    gfx.text(name, tx, barY, scale, rgba(1.0f, 1.0f, 1.0f, 1.0f));

    float accentH = 4.0f * sf;
    float accentW = tw * 0.55f;
    float accentY = barY + gfx.fontLineH() * scale + 6.0f * sf;
    gfx.fillRect((vw - accentW) / 2.0f, accentY, accentW, accentH,
                 rgba(0.35f, 0.75f, 1.0f, 0.95f));

    float dotSize = 10.0f * sf;
    float dotGap  = 20.0f * sf;
    float dotsW = (float)kSec_COUNT * dotSize
                + (float)(kSec_COUNT - 1) * dotGap;
    float dotsX = (vw - dotsW) / 2.0f;
    float dotsY = accentY + accentH + 10.0f * sf;
    for (int i = 0; i < kSec_COUNT; i++) {
        Color c = (i == mSection)
                ? rgba(1.0f, 1.0f, 1.0f, 0.95f)
                : rgba(1.0f, 1.0f, 1.0f, 0.30f);
        gfx.fillRect(dotsX + i * (dotSize + dotGap), dotsY,
                     dotSize, dotSize, c);
    }
}

void OverlayMenu::drawList(drastic_gfx::OverlayGfx& gfx, float vw,
                           float listY, float listH, float sf) {
    // Row height is sized off the selected-row scale so that when the
    // cursor slides to a row the layout doesn't jump.
    float rowH = gfx.fontLineH() * kRowSelScale * sf + 6.0f * sf;
    int visibleRows = (int)(listH / rowH);
    if (visibleRows < 4) visibleRows = 4;

    int cur = mCursor[mSection];
    int scroll = mScroll[mSection];
    if (cur < scroll) scroll = cur;
    if (cur >= scroll + visibleRows) scroll = cur - visibleRows + 1;
    if (scroll < 0) scroll = 0;
    mScroll[mSection] = scroll;

    int rows = (int)mRows.size();
    int last = scroll + visibleRows;
    if (last > rows) last = rows;

    // At 3x row scale, longer labels (e.g. "Analog Stick -> Stylus")
    // plus their value strings would clip against the old 16/84%
    // gutters, so we pull them in to 5/95% to preserve the
    // label-left / value-right alignment without truncation.
    float contentLeft  = vw * kContentLeftFrac;
    float contentRight = vw * kContentRightFrac;

    float rowY = listY;
    for (int i = scroll; i < last; i++) {
        const auto& r = mRows[i];
        bool active = (i == cur);
        float sc = (active ? kRowSelScale : kRowBaseScale) * sf;
        // Colour-code the Achievements list: unlocked gold, locked dim, section
        // headers in accent blue. Other sections use tag 0 (default grey).
        Color fg;
        if (active) {
            fg = rgba(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            switch (r.tag) {
                case kRowUnlocked: fg = rgba(0.99f, 0.83f, 0.32f, 0.95f); break;
                case kRowLocked:   fg = rgba(0.56f, 0.58f, 0.65f, 0.55f); break;
                case kRowHeader:   fg = rgba(0.45f, 0.74f, 1.00f, 0.92f); break;
                default:           fg = rgba(0.65f, 0.66f, 0.72f, 0.70f); break;
            }
        }
        float txtH = gfx.fontLineH() * sc;
        float txtY = rowY + (rowH - txtH) / 2.0f;

        if (active) {
            // Left-edge accent bar in the XMB blue tint, proportional
            // to the (now-3x) text height so it stays visible.
            float accentW = 6.0f * sf;
            float accentH = txtH * 1.1f;
            float accentX = contentLeft - 12.0f * sf - accentW;
            if (accentX < 4.0f * sf) accentX = 4.0f * sf;
            float accentY = txtY + (txtH - accentH) / 2.0f;
            gfx.fillRect(accentX, accentY, accentW, accentH,
                         rgba(0.35f, 0.75f, 1.0f, 0.95f));
        }

        gfx.text(trDyn(r.label.c_str()), contentLeft, txtY, sc, fg);
        float vWidth = 0.0f;
        if (!r.value.empty()) {
            const char* rv = trDyn(r.value.c_str());
            vWidth = gfx.measure(rv, sc);
            gfx.text(rv, contentRight - vWidth, txtY, sc, fg);
        }
        // Unlocked marker: a small gold star drawn as geometry (the font has no
        // U+2605), placed just left of the points value.
        if (r.tag == kRowUnlocked) {
            float starR = txtH * 0.30f;
            float starCx = contentRight - vWidth - 9.0f * sf - starR;
            float starCy = txtY + txtH * 0.5f;
            gfx.star(starCx, starCy, starR,
                     active ? rgba(1.0f, 1.0f, 1.0f, 1.0f)
                            : rgba(0.99f, 0.83f, 0.32f, 0.95f));
        }

        rowY += rowH;
    }

    // Scroll indicator. Only draw when there are hidden rows.
    // Sits in the right-edge margin, past the text column, so it no
    // longer overlaps right-aligned values like "empty".
    if (rows > visibleRows) {
        float trackW = 3.0f * sf;
        float trackX = vw - 10.0f * sf - trackW;
        float trackH = listH - 8.0f * sf;
        float trackY = listY + 4.0f * sf;
        gfx.fillRect(trackX, trackY, trackW, trackH,
                     rgba(1, 1, 1, 0.08f));
        float thumbH = trackH * ((float)visibleRows / (float)rows);
        if (thumbH < 12.0f * sf) thumbH = 12.0f * sf;
        float maxScroll = (float)(rows - visibleRows);
        float pos = (maxScroll > 0.0f)
                  ? (float)scroll / maxScroll : 0.0f;
        float thumbY = trackY + (trackH - thumbH) * pos;
        gfx.fillRect(trackX, thumbY, trackW, thumbH,
                     rgba(1, 1, 1, 0.55f));
    }
}

void OverlayMenu::drawFooter(drastic_gfx::OverlayGfx& gfx, float vw,
                             float vh, float sf) {
    float footScale = kFooterScale * sf;
    const char* hint;
    if (mCaptureKey) {
        hint = "Press any key to bind     B: cancel";
    } else {
        hint = "L/R: tabs     Up/Down: move     A: select     "
               "Left/Right: adjust     B: close";
    }
    hint = trDyn(hint);
    float fw = gfx.measure(hint, footScale);
    float fy = vh - gfx.fontLineH() * footScale - 10.0f * sf;
    gfx.text(hint, (vw - fw) / 2.0f, fy, footScale,
             rgba(0.55f, 0.58f, 0.70f, 0.85f));
}

} // namespace drastic_overlay
} // namespace android
