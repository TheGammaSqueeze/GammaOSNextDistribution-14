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

// RG DS Plus: a short tap of the Menu key while the home is in front returns to the last thing the
// home launched, a game or an Android app.
//
// Who handles the Menu key where:
//   - a game or app is running: PhoneWindowManager.nanoMenuGoHome takes the tap to the launcher
//     (it does nothing when app_launched is not 1, i.e. when the home is already in front);
//   - the bottom Control Center parked behind an app: ccPollAllAppsKey takes a hold of 600 ms or more;
//   - the home in front (the DRM-direct home, the SurfaceFlinger-hosted home and the overlay
//     launcher all run pollInput): this file takes a tap.
// A tap while the home is in front never reaches the framework's go-home as an action, and the
// tap that starts a game here is not one the framework may then turn around into an exit: the
// home stamps sys.gammaos.nano.menu_resume_ms and nanoMenuGoHome leaves taps alone for a few
// seconds after it.
//
// What counts as "last": every launch the home arms leaves a record in
// /data/system/nano_last_launch.txt (kind, ROM path or package, optional launch intent). The
// record outlives a home restart; sys.gammaos.nano.last_launch, set with it, is cleared by a
// reboot, so nothing is resumed on the first tap after boot. Kill switch:
// persist.gammaos.nano.menu_resume=0 (default on for ro.gammaos.device=anbernicrgdsplus only).

#define LOG_TAG "GammaOSNano"

#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include <string>
#include <vector>

#include <android-base/properties.h>
#include <cutils/properties.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>

#include "NanoMenu.h"
#include "NanoMenuUtils.h"

namespace android {

namespace {

constexpr const char* kLastLaunchFile = "/data/system/nano_last_launch.txt";
constexpr const char* kLastLaunchProp = "sys.gammaos.nano.last_launch";
constexpr const char* kResumeStampProp = "sys.gammaos.nano.menu_resume_ms";

// A press shorter than this is a tap. The same value as PhoneWindowManager.NANO_MENU_HOLD_MS and
// ccPollAllAppsKey's kMenuHoldUs: all three sides of the key must agree on where a tap ends.
constexpr int64_t kMenuTapMaxUs = 600000;
// A press read this long after it happened sat in the fd while the loop was parked or the screen
// was off (a wake press, a press during a modal blocking pollInput). It is not a tap to act on.
constexpr int64_t kMenuPressFreshUs = 500000;
// The intent field must fit the record read buffer with the rest; a longer one is dropped.
constexpr size_t kMaxIntentBytes = 3000;

enum ResumeKind { RESUME_NONE = 0, RESUME_GAME = 1, RESUME_APP = 2 };

// Recents and ROM lists store the FUSE view of a path, but the overlay launcher passes the raw one.
std::string normRomPath(const std::string& p) {
    if (p.compare(0, 14, "/data/media/0/") == 0) return "/sdcard/" + p.substr(14);
    if (p.compare(0, 14, "/mnt/media_rw/") == 0) return "/storage/" + p.substr(14);
    return p;
}

bool resumeEnabled() {
    char dev[PROPERTY_VALUE_MAX] = {};
    property_get("ro.gammaos.device", dev, "");
    return property_get_bool("persist.gammaos.nano.menu_resume", !strcmp(dev, "anbernicrgdsplus"));
}

// Replace the record with a plain tmp + rename: it only has to be whole, not durable. A power cut
// loses at worst the last launch, and the gate prop is gone by then too.
void writeRecord(const char* kind, const std::string& id, const std::string& intent) {
    if (!resumeEnabled()) return;   // inert, down to the file write, off the RG DS Plus
    if (id.empty() || id.find('\n') != std::string::npos) return;
    std::string safeIntent = intent;
    if (safeIntent.size() > kMaxIntentBytes || safeIntent.find('\n') != std::string::npos) safeIntent.clear();
    const std::string rec = std::string(kind) + "\n" + id + "\n" + safeIntent + "\n";
    const std::string tmp = std::string(kLastLaunchFile) + ".tmp." + std::to_string((int)getpid());
    int fd = open(tmp.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    if (fd < 0) return;
    size_t off = 0;
    bool ok = true;
    while (off < rec.size()) {
        const ssize_t w = write(fd, rec.data() + off, rec.size() - off);
        if (w <= 0) { ok = false; break; }
        off += (size_t)w;
    }
    close(fd);
    if (!ok || rename(tmp.c_str(), kLastLaunchFile) != 0) { unlink(tmp.c_str()); return; }
    (void)chmod(kLastLaunchFile, 0644);
    property_set(kLastLaunchProp, kind);
}

// The record, or RESUME_NONE when nothing was launched since this boot or the file is unusable.
int readRecord(std::string* id, std::string* intent) {
    char gate[PROPERTY_VALUE_MAX] = {};
    property_get(kLastLaunchProp, gate, "");
    if (!gate[0]) return RESUME_NONE;
    const std::string s = readPathFile(kLastLaunchFile);
    if (s.empty()) return RESUME_NONE;
    const size_t nl1 = s.find('\n');
    if (nl1 == std::string::npos) return RESUME_NONE;
    const std::string kind = s.substr(0, nl1);
    const size_t nl2 = s.find('\n', nl1 + 1);
    *id = s.substr(nl1 + 1, nl2 == std::string::npos ? std::string::npos : nl2 - nl1 - 1);
    *intent = nl2 == std::string::npos ? std::string() : s.substr(nl2 + 1);
    while (!intent->empty() && (intent->back() == '\n' || intent->back() == '\r')) intent->pop_back();
    if (id->empty()) return RESUME_NONE;
    if (kind == "game") return RESUME_GAME;
    if (kind == "app") return RESUME_APP;
    return RESUME_NONE;
}

}  // namespace

void nanoResumeNoteGame(const std::string& romPath) {
    writeRecord("game", normRomPath(romPath), std::string());
}

void nanoResumeNoteApp(const std::string& pkg, const std::string& intent) {
    // A package name has a dot; the SurfaceFlinger drastic-nano host is part of a game launch.
    if (pkg.find('.') == std::string::npos || pkg == "com.gammaos.drasticsf") return;
    writeRecord("app", pkg, intent);
}

// armAppLaunchTrigger: every home launch that is not a game goes through here, so the app being
// launched is whatever launch_app holds. launchXmbGame sets mResumeInGameLaunch around its whole
// body; it has already noted the ROM, and the emulator package it arms must not replace that.
// Apps that carry their own intent (Settings, a URL) note themselves with the intent afterwards.
void NanoMenu::resumeNoteLaunch() {
    if (mResumeInGameLaunch) return;
    char la[PROPERTY_VALUE_MAX] = {};
    property_get("sys.gammaos.nano.launch_app", la, "");
    if (!la[0]) return;
    nanoResumeNoteApp(la, std::string());
}

// A Menu key edge from the pad (pollInput). Returns true when the edge belongs to the tap logic
// and the caller must not look at it again. The press is only taken when it is fresh, the screen
// is on and the feature is enabled; the release then decides tap or hold on the evdev stamps of
// this fd alone (the same discipline as ccPollAllAppsKey: never one side's clock against the
// other's).
bool NanoMenu::menuResumeKey(int fd, int value, int64_t evUs) {
    char keydev[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.cc.keydev", keydev, "Xbox Wireless Controller");
    const auto itName = mInputFdNames.find(fd);
    if (itName == mInputFdNames.end() || itName->second != keydev) return false;
    if (value == 1) {
        mMenuTapDownUs = 0;
        if (!resumeEnabled()) return false;
        struct timespec rt;
        clock_gettime(CLOCK_REALTIME, &rt);
        const int64_t nowUs = (int64_t)rt.tv_sec * 1000000LL + rt.tv_nsec / 1000;
        if (nowUs - evUs > kMenuPressFreshUs) return true;
        char ss[PROPERTY_VALUE_MAX] = {};
        property_get("sys.screen.state", ss, "on");
        if (!strcmp(ss, "off")) return true;
        mMenuTapDownUs = evUs;
        return true;
    }
    if (value != 0 || mMenuTapDownUs <= 0) return false;
    const int64_t held = evUs - mMenuTapDownUs;
    mMenuTapDownUs = 0;
    if (held < 0 || held >= kMenuTapMaxUs) return true;   // a hold is the Control Center's gesture
    if (!menuResumeAllowed()) return true;
    menuResumeLaunch();
    return true;
}

// The home is idle and in front. A tap is the framework's go-home whenever an app is running
// (app_launched, an exit in progress), so those are exactly the states this must stay out of.
// While a drastic-nano session runs the home is parked (mDrasticParked) and does not poll input;
// once it has handed the panels back the home is idle again, and a quick second tap after leaving
// a DS game goes back into it (the launch code itself waits out drastic-nano's save tail). Modal
// screens (on-screen keyboard, players, dialogs, a system update) are left alone: nobody means to
// start a game out of those. The same goes for the screens of their own on the nav stack (File
// Explorer, the Syncthing and share editors, the photo grid): they keep state that the DSi hop
// below would drop along with the stack.
bool NanoMenu::menuResumeAllowed() {
    if (!mPs3Xmb || mMenuState != MENU_MAIN || mSetupWizardActive || mPs3BootActive) return false;
    if (ndsInModal() || mWifiManageActive || mOtaFlowActive || otaInProgress()) return false;
    if (mPspClockOn || mPspClockReveal > 0.0f) return false;   // the slide clock owns the screen
    if (ps3TopScreenKind() != 0) return false;
    if (mWaitForRelease || mExitRequested || mLaunchFadeStart != 0 || mLaunchPending) return false;
    if (mDrasticNanoPending || mAzaharNanoPending || mOverlayLaunchPending) return false;
    if (mDrasticParked.load(std::memory_order_relaxed)) return false;
    if (mDrmPanelsParked.load(std::memory_order_relaxed)) return false;
    if (property_get_bool("sys.gammaos.nano.app_launched", false)) return false;
    if (property_get_bool("sys.gammaos.nano.killing", false)) return false;
    return true;
}

// Start the last game or app. Returns true when a launch is under way.
bool NanoMenu::menuResumeLaunch() {
    std::string id, intent;
    const int kind = readRecord(&id, &intent);
    if (kind == RESUME_NONE) {
        ALOGI("NanoMenu: Menu tap, nothing was launched since boot");
        return false;
    }
    // Stamp first, before anything can reach the framework: PhoneWindowManager looks at this when
    // it handles the same tap, and must not take the app that is now coming up for one to leave.
    char stamp[32];
    snprintf(stamp, sizeof(stamp), "%lld", (long long)android::uptimeMillis());
    property_set(kResumeStampProp, stamp);

    bool started = false;
    if (kind == RESUME_GAME) {
        const std::string want = normRomPath(id);
        Ps3Item it;
        bool found = false;
        for (size_t i = 0; i < mXmbRecent.size() && !found; i++) {
            if (normRomPath(mXmbRecent[i].romPath) != want) continue;
            it.kind = PS3_RECENT;
            it.a = (int)i;
            it.label = mXmbRecent[i].displayName;
            found = true;
        }
        for (size_t s = 0; s < mXmbSystems.size() && !found; s++) {
            const auto& roms = mXmbSystems[s].roms;
            for (size_t g = 0; g < roms.size(); g++) {
                if (normRomPath(roms[g]) != want) continue;
                it.kind = PS3_ROM;
                it.a = (int)s;
                it.b = (int)g;
                found = true;
                break;
            }
        }
        if (found) {
            ALOGI("NanoMenu: Menu tap, resuming game %s", want.c_str());
            started = menuResumeDispatch(it);
        } else {
            ALOGI("NanoMenu: Menu tap, the last game is gone from the library: %s", want.c_str());
        }
    } else {
        if (!packageInstalled(id)) {
            ALOGI("NanoMenu: Menu tap, the last app is no longer installed: %s", id.c_str());
        } else if (!intent.empty()) {
            // Settings and a browser URL start through their own intent, not a bare launcher start.
            ALOGI("NanoMenu: Menu tap, resuming app %s (with its intent)", id.c_str());
            started = menuResumeLaunchApp(id, intent);
            if (started && mWaitForRelease && !mOverlayMode && mLaunchFadeStart == 0)
                mLaunchFadeStart = uptimeMillis();
        } else {
            Ps3Item it;
            it.kind = PS3_APP;
            it.label = id;
            it.payloadStr = id;
            ALOGI("NanoMenu: Menu tap, resuming app %s", id.c_str());
            started = menuResumeDispatch(it);
        }
    }
    if (!started) property_set(kResumeStampProp, "0");
    mDisplayDirty = true;
    return started;
}

// Launch one item the way the home's own select does, so every theme, the overlay launcher and the
// 3DS grid keep their own launch handling (isLaunchReady gate and toast, missing ROM or emulator
// messages, drastic-nano and azahar-nano hand-offs, the overlay's am-start). The DSi theme first
// moves its carousel onto the card, so the tile lift and white wash play on the card being started.
bool NanoMenu::menuResumeDispatch(const Ps3Item& it) {
    if (mCtrTheme && mCtrHome) return ctrLaunchItem(it);

    // The carousel is put back if the launch is refused (missing ROM or emulator, boot not ready).
    const bool dsi = mNdsTheme;
    const bool savedRoot = mNdsAtRoot;
    const int savedCat = mPs3CatIdx;
    const int savedItem = mPs3ItemIdx;
    const float savedCam = mNdsCamera;
    std::vector<Ps3Level> savedStack;
    if (dsi) savedStack = mPs3Stack;
    auto restoreNav = [&]() {
        mPs3Stack = savedStack;
        mNdsAtRoot = savedRoot;
        mPs3CatIdx = savedCat;
        mPs3ItemIdx = savedItem;
        mPs3SubAnimStart = -1.0f; mPs3SubAnim = 0.0f; mPs3SubDir = 0;
        mPs3ItemAnimStart = -1.0f; mPs3AnimItem = (float)ps3CurSel();
        mNdsCamera = savedCam;
        mNdsScrubbing = false; mNdsFlingVel = 0.0f; mNdsFastScroll = false;
        mDisplayDirty = true;
    };

    bool hopped = false;
    if (dsi) {
        hopped = menuResumeHopDsi(it);
        if (!hopped) restoreNav();
    }
    if (hopped) {
        ndsNavSelect(true);   // the touch select: drills nothing more, launches the focused card
    } else {
        // Select the item from a one-item level pushed for the purpose: ps3XmbSelect acts on the
        // focused item of the current level and these kinds launch without pushing anything.
        Ps3Level lvl;
        lvl.title = it.label;
        lvl.items.push_back(it);
        lvl.sel = 0;
        const size_t depth = mPs3Stack.size();
        mPs3Stack.push_back(lvl);
        ps3XmbSelect();
        if (mPs3Stack.size() > depth) mPs3Stack.erase(mPs3Stack.begin() + depth, mPs3Stack.end());
    }

    const bool armed = mWaitForRelease || mDrasticNanoPending || mAzaharNanoPending || mOverlayLaunchPending;
    if (!armed) {
        if (hopped) restoreNav();
        return false;
    }
    // A key press launch waits for the select key's release to start the fade; this tap has none.
    if (mWaitForRelease && !mOverlayMode && mLaunchFadeStart == 0) mLaunchFadeStart = uptimeMillis();
    return true;
}

// DSi: drill Game > Recently Played (or Game > Applications) and put the selection on the card, with
// the drill animation settled, the way ndsRestoreReturnPath lands on a card. False when the list or
// the card is not there; the nav state is then half-changed and the caller restores it.
bool NanoMenu::menuResumeHopDsi(const Ps3Item& want) {
    int listKind = -1;
    if (want.kind == PS3_RECENT) listKind = PS3_RECENT_LIST;
    else if (want.kind == PS3_APP) listKind = PS3_APP_LIST;
    if (listKind < 0) return false;
    int cat = -1, row = -1;
    for (size_t c = 0; c < mPs3Cats.size() && cat < 0; c++) {
        for (size_t i = 0; i < mPs3Cats[c].items.size(); i++) {
            if (mPs3Cats[c].items[i].kind == listKind) { cat = (int)c; row = (int)i; break; }
        }
    }
    if (cat < 0) return false;

    mPs3Stack.clear();
    mNdsAtRoot = false;
    mPs3CatIdx = cat;
    mPs3ItemIdx = row;
    ps3XmbSelect();   // drill into the list
    if (mPs3Stack.size() != 1) return false;
    Ps3Level& lvl = mPs3Stack.back();
    int sel = -1;
    for (size_t k = 0; k < lvl.items.size(); k++) {
        const Ps3Item& c = lvl.items[k];
        if (c.kind == want.kind && ((want.kind == PS3_RECENT && c.a == want.a) ||
                                    (want.kind == PS3_APP && c.payloadStr == want.payloadStr))) {
            sel = (int)k;
            break;
        }
    }
    if (sel < 0) return false;
    lvl.sel = sel;
    mPs3SubAnimStart = -1.0f; mPs3SubAnim = 0.0f; mPs3SubDir = 0;
    mPs3ItemAnimStart = -1.0f; mPs3AnimItem = (float)sel;
    mNdsCamera = (float)sel;
    mNdsScrubbing = false; mNdsFlingVel = 0.0f; mNdsFastScroll = false;
    mDisplayDirty = true;
    return true;
}

// An app that starts through its own intent: the same hand-off launchUrl makes for a browser URL (in the
// resident overlay launcher, the am start overlayLaunchIntent builds from the same intent).
bool NanoMenu::menuResumeLaunchApp(const std::string& pkg, const std::string& intent) {
    if (!isLaunchReady()) { showLaunchBusyToast(); return false; }
    if (mOverlayMode) return overlayLaunchIntent(pkg, intent);   // the resident launcher: an am start
    property_set("sys.gammaos.nano.launch_app", pkg.c_str());
    property_set("sys.gammaos.nano.launched_pkg", pkg.c_str());
    {
        const char* f = "/data/system/nano_launch_intent.txt";
        int ifd = open(f, O_WRONLY | O_CREAT | O_TRUNC, 0666);
        if (ifd >= 0) { ssize_t w = write(ifd, intent.c_str(), intent.size()); (void)w; close(ifd); chmod(f, 0644); }
        property_set("sys.gammaos.nano.launch_intent", ifd >= 0 ? "file" : "");
    }
    setLaunchRomPath("");
    property_set("sys.gammaos.nano.launch_core", "");
    property_set("persist.gammaos.nano.qr_prepared", "0");
    property_set("persist.gammaos.nano.qr_core", "");
    property_set("sys.gammaos.nano.return_apps", "1");
    armAppLaunchTrigger();
    nanoResumeNoteApp(pkg, intent);   // armAppLaunchTrigger noted the bare package; keep the intent
    property_set("sys.gammaos.nano.drop_input", "1");
    mWaitForRelease = true;
    return true;
}

}  // namespace android
