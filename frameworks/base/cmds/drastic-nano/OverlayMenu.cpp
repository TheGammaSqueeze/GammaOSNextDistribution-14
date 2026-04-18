/*
 * Copyright (C) 2026 GammaOS
 */

#define LOG_TAG "DrasticNano.Overlay"

#include "OverlayMenu.h"

#include <dirent.h>
#include <errno.h>
#include <string.h>
#include <sys/stat.h>

#include <algorithm>
#include <cstdio>

#include <utils/Log.h>
#include <utils/SystemClock.h>

#include "DrasticRunner.h"
#include "NanoMenuDrm.h"

namespace android {
namespace drastic_overlay {

using drastic_gfx::Color;
using drastic_gfx::rgba;

namespace {
constexpr int kRowHeightPx = 28;
constexpr int kPanelMarginFrac = 10; // 1/N of viewport as margin
constexpr const char* kSectionNames[] = {
    "Save States", "Video", "Audio", "Controls",
};
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
    {
        RowAction r;
        r.label = "Reset DS";
        r.onAccept = [this]() {
            if (mRunner) mRunner->resetSystem();
            toast("Reset");
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

void OverlayMenu::draw(drastic_gfx::OverlayGfx& gfx) {
    if (!mOpen) {
        // Brief toasts still render even when closed (e.g. quick save).
        if (mToast.empty() ||
            android::elapsedRealtime() > mToastUntilMs) return;
        float w = gfx.measure(mToast.c_str(), 1.0f) + 24.0f;
        float x = (gfx.viewportW() - w) / 2.0f;
        float y = gfx.viewportH() * 0.85f;
        gfx.panel(x, y, w, gfx.fontLineH() + 12.0f,
                  rgba(0.05f, 0.05f, 0.1f, 0.7f),
                  rgba(0.6f, 0.6f, 0.7f, 0.8f));
        gfx.text(mToast.c_str(), x + 12.0f, y + 6.0f, 1.0f,
                 rgba(1, 1, 1, 1));
        return;
    }

    float vw = (float)gfx.viewportW();
    float vh = (float)gfx.viewportH();
    float mx = vw / kPanelMarginFrac;
    float my = vh / kPanelMarginFrac;
    float px = mx;
    float py = my;
    float pw = vw - 2 * mx;
    float ph = vh - 2 * my;

    // Backdrop scrim over the whole screen.
    gfx.fillRect(0, 0, vw, vh, rgba(0, 0, 0, 0.45f));
    gfx.panel(px, py, pw, ph,
              rgba(0.08f, 0.08f, 0.12f, 0.88f),
              rgba(0.45f, 0.55f, 0.75f, 0.95f));

    drawTabs(gfx, px, py, pw);
    drawBody(gfx, px, py + kRowHeightPx + 12,
             pw, ph - kRowHeightPx - 24);

    // Toast overlay on top of menu.
    if (!mToast.empty() && android::elapsedRealtime() <= mToastUntilMs) {
        float w = gfx.measure(mToast.c_str(), 1.0f) + 24.0f;
        float tx = (vw - w) / 2.0f;
        float ty = vh * 0.85f;
        gfx.panel(tx, ty, w, gfx.fontLineH() + 12.0f,
                  rgba(0.05f, 0.05f, 0.1f, 0.8f),
                  rgba(0.8f, 0.8f, 0.9f, 0.9f));
        gfx.text(mToast.c_str(), tx + 12.0f, ty + 6.0f, 1.0f,
                 rgba(1, 1, 1, 1));
    }
}

void OverlayMenu::drawTabs(drastic_gfx::OverlayGfx& gfx, float panelX,
                           float panelY, float panelW) {
    float x = panelX + 16.0f;
    float y = panelY + 8.0f;
    float tabH = kRowHeightPx;
    for (int i = 0; i < kSec_COUNT; i++) {
        const char* name = kSectionNames[i];
        float tabW = gfx.measure(name, 1.1f) + 28.0f;
        bool active = (i == mSection);
        Color bg   = active ? rgba(0.20f, 0.30f, 0.50f, 0.85f)
                            : rgba(0.15f, 0.15f, 0.20f, 0.60f);
        Color edge = active ? rgba(0.9f, 0.9f, 1.0f, 1.0f)
                            : rgba(0.4f, 0.4f, 0.5f, 1.0f);
        gfx.panel(x, y, tabW, tabH, bg, edge);
        Color fg = active ? rgba(1, 1, 1, 1) : rgba(0.7f, 0.7f, 0.8f, 1);
        gfx.text(name, x + 14.0f, y + 4.0f, 1.1f, fg);
        x += tabW + 8.0f;
    }
}

void OverlayMenu::drawBody(drastic_gfx::OverlayGfx& gfx, float panelX,
                           float panelY, float panelW, float panelH) {
    // Ensure cursor is visible within scroll window.
    int visibleRows = (int)(panelH / kRowHeightPx);
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

    float rowY = panelY + 4.0f;
    for (int i = scroll; i < last; i++) {
        const auto& r = mRows[i];
        bool active = (i == cur);
        if (active) {
            gfx.fillRect(panelX + 8.0f, rowY,
                         panelW - 16.0f, kRowHeightPx,
                         rgba(0.18f, 0.32f, 0.52f, 0.85f));
        }
        Color fg = active ? rgba(1.0f, 1.0f, 1.0f, 1.0f)
                          : rgba(0.85f, 0.85f, 0.9f, 1.0f);
        gfx.text(r.label.c_str(), panelX + 20.0f, rowY + 4.0f, 1.0f, fg);
        if (!r.value.empty()) {
            float w = gfx.measure(r.value.c_str(), 1.0f);
            gfx.text(r.value.c_str(),
                     panelX + panelW - 24.0f - w,
                     rowY + 4.0f, 1.0f, fg);
        }
        rowY += kRowHeightPx;
    }

    // Footer hint strip.
    float footY = panelY + panelH - kRowHeightPx + 2.0f;
    gfx.fillRect(panelX + 8.0f, footY - 2.0f,
                 panelW - 16.0f, 1.0f,
                 rgba(0.4f, 0.4f, 0.5f, 0.7f));
    const char* hint;
    if (mCaptureKey) hint = "Press any key to bind, B to cancel";
    else             hint = "L/R tabs   Up/Down move   A select   Left/Right adjust   B close";
    gfx.text(hint, panelX + 16.0f, footY + 2.0f, 0.85f,
             rgba(0.7f, 0.75f, 0.9f, 1.0f));
}

} // namespace drastic_overlay
} // namespace android
