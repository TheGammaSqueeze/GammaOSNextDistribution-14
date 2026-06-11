/*
 * Copyright (C) 2026 GammaOS
 */

#define LOG_TAG "DrasticNano.Overlay"

#include "OverlayMenu.h"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <ctime>

#include <aidl/android/hardware/health/BatteryStatus.h>
#include <aidl/android/hardware/health/IHealth.h>
#include <android/binder_manager.h>
#include <cutils/properties.h>
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
    "Save States", "Video", "Audio", "Controls",
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
constexpr float kRowSelScale    = 1.77f;
constexpr float kRowBaseScale   = 1.50f;
constexpr float kBatTextScale   = 1.425f;
constexpr float kBatIconScale   = 1.5f;    // battery-body dimension multiplier
constexpr float kFooterScale    = 1.35f;   // help hint strip at bottom
                                           // fits on 640-wide panels
// Row text fills more of the viewport now, so the content gutters
// shrink from 16%/84% to 5%/95%.
constexpr float kContentLeftFrac  = 0.05f;
constexpr float kContentRightFrac = 0.95f;
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
    if (mRunner) mRunner->pauseToggle(false);
    ALOGI("OverlayMenu: closed");
}

void OverlayMenu::close() {
    if (mOpen) closeMenu();
}

void OverlayMenu::toast(const std::string& msg, int64_t ms) {
    mToast = msg;
    mToastUntilMs = android::elapsedRealtime() + ms;
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

void OverlayMenu::update(const drastic_input::InputActions& a,
                        drastic_input::InputState* input) {
    // Short-press BACK toggles menu open/close regardless of state.
    if (a.menuToggle) {
        if (mOpen) closeMenu();
        else       openMenu();
        return;
    }

    if (!mOpen) {
        // Forward mid-game side-effects of action remaps. The input
        // layer surfaces Quick Save / Quick Load / Reset-via-menu as
        // edge flags; treat them as implicit overlay commands even
        // when the menu isn't visible.
        if (mRunner) {
            if (a.actQuickSave) mRunner->saveStateSlot(0);
            if (a.actQuickLoad) mRunner->loadStateSlot(0);
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

    // Normal navigation.
    if (a.navPrevTab) {
        mSection = (Section)((mSection + kSec_COUNT - 1) % kSec_COUNT);
        rebuildRows();
    }
    if (a.navNextTab) {
        mSection = (Section)((mSection + 1) % kSec_COUNT);
        rebuildRows();
    }
    if (a.navUp) {
        mCursor[mSection]--;
        if (mCursor[mSection] < 0)
            mCursor[mSection] = (int)mRows.size() - 1;
    }
    if (a.navDown) {
        mCursor[mSection]++;
        if (mCursor[mSection] >= (int)mRows.size())
            mCursor[mSection] = 0;
    }
    if (a.navLeft || a.navRight) {
        int cur = mCursor[mSection];
        if (cur >= 0 && cur < (int)mRows.size() && mRows[cur].onAdjust) {
            mRows[cur].onAdjust(a.navRight ? +1 : -1);
            rebuildRows();
        }
    }
    if (a.navAccept) {
        int cur = mCursor[mSection];
        if (cur >= 0 && cur < (int)mRows.size() && mRows[cur].onAccept) {
            mRows[cur].onAccept();
            rebuildRows();
        }
    }
    if (a.navCancel) {
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
    default: break;
    }
    if (mCursor[mSection] >= (int)mRows.size()) {
        mCursor[mSection] = (int)mRows.size() - 1;
    }
    if (mCursor[mSection] < 0) mCursor[mSection] = 0;
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

    for (int slot = 0; slot < 9; slot++) {
        char label[64];
        snprintf(label, sizeof(label), "Save to Slot %d%s",
                 slot, slotFileExists(slot) ? " (overwrite)" : "");
        RowAction r;
        r.label = label;
        r.onAccept = [this, slot]() {
            if (!mRunner) return;
            if (mRunner->saveStateSlot(slot)) {
                char msg[64];
                snprintf(msg, sizeof(msg), "Saved slot %d", slot);
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
    for (int slot = 0; slot < 9; slot++) {
        char label[64];
        snprintf(label, sizeof(label), "Load from Slot %d", slot);
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
                snprintf(msg, sizeof(msg), "Loaded slot %d", slot);
                toast(msg);
            } else {
                toast("Load failed");
            }
        };
        mRows.push_back(std::move(r));
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
    auto addBool = [&](const char* label, bool& field,
                       bool requiresRestart) {
        RowAction r;
        r.label = label;
        r.value = field ? "On" : "Off";
        if (requiresRestart) r.value += "  [restart]";
        r.onAccept = [this, &field]() {
            field = !field;
            mDirty = true;
        };
        r.onAdjust = [this, &field](int) {
            field = !field;
            mDirty = true;
        };
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
    addBool("Hi-res 3D",           mPrefs.hires3d,      true);
    addBool("Threaded 3D",         mPrefs.threaded3d,   true);
    addBool("Disable Edge Marking",mPrefs.disableEdge,  true);
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
        r.value += "  [restart]";
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
        RowAction r;
        r.label = "Audio Latency";
        r.value = std::to_string(mPrefs.audioLatency) + "  [restart]";
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
        r.value = mPrefs.micEnabled ? "On  [restart]" : "Off  [restart]";
        r.onAccept = [this]() {
            mPrefs.micEnabled = !mPrefs.micEnabled;
            mDirty = true;
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
    float scale = 1.0f * sf;
    float w = gfx.measure(msg.c_str(), scale) + 28.0f * sf;
    float h = gfx.fontLineH() * scale + 14.0f * sf;
    float vw = (float)gfx.viewportW();
    float vh = (float)gfx.viewportH();
    float x = (vw - w) / 2.0f;
    float y = vh * 0.82f;
    gfx.fillRect(x, y, w, h, rgba(0.02f, 0.03f, 0.05f, 0.82f));
    gfx.fillRect(x, y + h - 2.0f * sf, w, 2.0f * sf,
                 rgba(0.40f, 0.75f, 1.0f, 0.9f));
    gfx.text(msg.c_str(), x + 14.0f * sf,
             y + (h - gfx.fontLineH() * scale) / 2.0f, scale,
             rgba(1, 1, 1, 1));
}

void OverlayMenu::draw(drastic_gfx::OverlayGfx& gfx) {
    float vw = (float)gfx.viewportW();
    float vh = (float)gfx.viewportH();
    float sf = clampf(fminf(vw / 1080.0f, vh / 720.0f), kSfMin, kSfMax);

    if (!mOpen) {
        // Brief toasts still render even when closed (quick save, etc.).
        if (mToast.empty() ||
            android::elapsedRealtime() > mToastUntilMs) return;
        drawToast(gfx, mToast, sf);
        return;
    }

    // Full-screen scrim at 75% opacity: the game stays faintly visible
    // through the dark but the overlay dominates.
    gfx.fillRect(0, 0, vw, vh, rgba(0, 0, 0, 0.75f));

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
    drawList(gfx, vw, listTop, listBottom - listTop, sf);

    drawFooter(gfx, vw, vh, sf);

    if (!mToast.empty() && android::elapsedRealtime() <= mToastUntilMs) {
        drawToast(gfx, mToast, sf);
    }
}

void OverlayMenu::drawCategoryBar(drastic_gfx::OverlayGfx& gfx,
                                  float vw, float barY, float sf) {
    // At 3x body scale, four labels don't fit on a 640-wide panel, so
    // we take a Vita-XMB-style approach: the active category renders as
    // a big centered title, with a short accent underline and a row of
    // pagination dots below for positional context. L/R still cycles
    // through them; the dots tell the user where they are.
    const char* name = kSectionNames[mSection];
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
        Color fg = active ? rgba(1.0f, 1.0f, 1.0f, 1.0f)
                          : rgba(0.65f, 0.66f, 0.72f, 0.70f);
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

        gfx.text(r.label.c_str(), contentLeft, txtY, sc, fg);
        if (!r.value.empty()) {
            float vWidth = gfx.measure(r.value.c_str(), sc);
            gfx.text(r.value.c_str(),
                     contentRight - vWidth, txtY, sc, fg);
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
    float fw = gfx.measure(hint, footScale);
    float fy = vh - gfx.fontLineH() * footScale - 10.0f * sf;
    gfx.text(hint, (vw - fw) / 2.0f, fy, footScale,
             rgba(0.55f, 0.58f, 0.70f, 0.85f));
}

} // namespace drastic_overlay
} // namespace android
