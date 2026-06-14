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

// Input device discovery + hotplug, event polling, and the D-pad/face-button
// dispatch table used by the non-XMB menu. XMB-specific input
// (handleLeft/handleRight, OSK handling) lives in NanoMenuXmb.cpp.
//
// Extracted from NanoMenu.cpp — behavior unchanged.

#define LOG_TAG "GammaOSNano"

#include <dirent.h>
#include <fcntl.h>
#include <poll.h>
#include <unistd.h>
#include <inttypes.h>
#include <cinttypes>
#include <math.h>
#include <string.h>
#include <errno.h>

#include <linux/input.h>
#include <sys/inotify.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <time.h>

#include <GLES2/gl2.h>
#include <EGL/egl.h>

#include <android-base/properties.h>
#include <cutils/properties.h>
#include <sys/system_properties.h>   // prop_info, __system_property_find
// Exported by libc but declared only in the internal <sys/_system_properties.h>;
// used to detect the test-nav prop changing via its serial (cheap pointer-deref)
// instead of a full name lookup every pollInput().
extern "C" uint32_t __system_property_serial(const prop_info* __pi);
#include <utils/Log.h>
#include <utils/SystemClock.h>

#include "NanoBacklight.h"
#include "NanoMenu.h"
#include "NanoMenuDrm.h"
#include "NanoMenuShaders.h"
#include "NanoMenuUtils.h"

namespace android {

// ---------------------------------------------------------------------------
// Input device discovery + hotplug
// ---------------------------------------------------------------------------

void NanoMenu::openInputDevices() {
    DIR* dir = opendir("/dev/input");
    if (!dir) { ALOGE("Cannot open /dev/input"); return; }
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (strncmp(entry->d_name, "event", 5) != 0) continue;
        char path[PATH_MAX];
        snprintf(path, sizeof(path), "/dev/input/%s", entry->d_name);
        if (mOpenedDevices.count(entry->d_name)) continue;
        int fd = open(path, O_RDONLY | O_NONBLOCK);
        if (fd >= 0) {
            // Exclusive grab: prevent Android InputReader from stealing events.
            // Gated by property — disable when preload is off to avoid input
            // ownership issues during app transitions.
            if (android::base::GetBoolProperty("persist.gammaos.nano.grab_input", false)) {
                if (ioctl(fd, EVIOCGRAB, 1) < 0) {
                    ALOGW("EVIOCGRAB failed for %s: %s", path, strerror(errno));
                }
                ALOGD("Opened + grabbed input device: %s", path);
            } else {
                ALOGD("Opened input device (no grab): %s", path);
            }
            mInputFds.push_back(fd);
            mOpenedDevices.insert(entry->d_name);
        }
    }
    closedir(dir);

    // Set up inotify to detect hotplugged and replaced input devices.
    // IN_DELETE is needed because gammapad may destroy+recreate device nodes
    // to seize them; we must detect the deletion, drop our stale fd, and
    // re-open+grab when the replacement IN_CREATE arrives.
    mInotifyFd = inotify_init1(IN_NONBLOCK | IN_CLOEXEC);
    if (mInotifyFd >= 0) {
        inotify_add_watch(mInotifyFd, "/dev/input", IN_CREATE | IN_DELETE);
        ALOGD("Watching /dev/input for hotplug");
    }
}

void NanoMenu::checkInputHotplug() {
    if (mInotifyFd < 0) return;
    char buf[512] __attribute__((aligned(__alignof__(struct inotify_event))));
    ssize_t len = read(mInotifyFd, buf, sizeof(buf));
    if (len > 0) {
        for (char* ptr = buf; ptr < buf + len; ) {
            auto* ev = reinterpret_cast<struct inotify_event*>(ptr);
            if (ev->len > 0 && strncmp(ev->name, "event", 5) == 0) {
                if (ev->mask & IN_DELETE) {
                    // Device node was removed (gammapad hides+recreates devices).
                    // Close our stale fd and forget it so we re-grab on IN_CREATE.
                    //
                    // BUG fix: only match the fd whose path corresponds to the
                    // deleted device. The previous code OR'd against any
                    // "(deleted)" fd, which closed the wrong fd when multiple
                    // devices were being torn down nearly simultaneously
                    // (gammapad's swap of event12 races with EventHub removing
                    // event10, etc). The result was that nano permanently lost
                    // the Xbox controller because event12's fd got closed by
                    // event10's IN_DELETE event, leaving stale state in
                    // mOpenedDevices that suppressed IN_CREATE re-opening.
                    if (mOpenedDevices.count(ev->name)) {
                        char path[PATH_MAX];
                        snprintf(path, sizeof(path), "/dev/input/%s", ev->name);
                        for (auto it = mInputFds.begin(); it != mInputFds.end(); ++it) {
                            char fdPath[PATH_MAX];
                            char procLink[64];
                            snprintf(procLink, sizeof(procLink), "/proc/self/fd/%d", *it);
                            ssize_t rl = readlink(procLink, fdPath, sizeof(fdPath) - 1);
                            if (rl > 0) {
                                fdPath[rl] = '\0';
                                // Match only by exact device name. The path may
                                // also have " (deleted)" appended once the kernel
                                // marks it removed; tolerate that suffix.
                                char wantPath[PATH_MAX];
                                snprintf(wantPath, sizeof(wantPath), "/dev/input/%s", ev->name);
                                size_t wantLen = strlen(wantPath);
                                if (strncmp(fdPath, wantPath, wantLen) == 0 &&
                                        (fdPath[wantLen] == '\0' ||
                                         fdPath[wantLen] == ' ')) {
                                    ioctl(*it, EVIOCGRAB, 0);
                                    close(*it);
                                    mInputFds.erase(it);
                                    break;
                                }
                            }
                        }
                        mOpenedDevices.erase(ev->name);
                        ALOGI("Device removed, dropped stale fd: %s", path);
                    }
                } else if ((ev->mask & IN_CREATE) && !mOpenedDevices.count(ev->name)) {
                    // Small delay for the device node to be fully ready
                    usleep(100000); // 100ms
                    char path[PATH_MAX];
                    snprintf(path, sizeof(path), "/dev/input/%s", ev->name);
                    int fd = open(path, O_RDONLY | O_NONBLOCK);
                    if (fd >= 0) {
                        // Home-mode grab only (persist.gammaos.nano.grab_input).
                        // The overlay does NOT grab: it isolates the app's input
                        // via the framework drop_input prop, not EVIOCGRAB.
                        if (android::base::GetBoolProperty("persist.gammaos.nano.grab_input", false)) {
                            if (ioctl(fd, EVIOCGRAB, 1) < 0) {
                                ALOGW("EVIOCGRAB failed for hotplugged %s: %s", path, strerror(errno));
                            }
                        }
                        mInputFds.push_back(fd);
                        mOpenedDevices.insert(ev->name);
                        ALOGI("Hotplugged + grabbed input device: %s", path);
                    }
                }
            }
            ptr += sizeof(struct inotify_event) + ev->len;
        }
    }

    // GammaOS: defensive sweep for missed devices. The inotify handler above
    // can lose track of devices when multiple are torn down/recreated nearly
    // simultaneously (typical with gammapad swaps). Periodically rescan
    // /dev/input and reconcile against mOpenedDevices: open anything missing,
    // and drop any fd that has gone "(deleted)" without a matching IN_DELETE.
    // Ratelimited to once per second to keep cost negligible.
    static int64_t sLastSweepNs = 0;
    int64_t nowNs = systemTime(SYSTEM_TIME_MONOTONIC);
    if (nowNs - sLastSweepNs < 1000000000LL) return;
    sLastSweepNs = nowNs;

    // (1) Drop any of our fds that point to a deleted inode. This handles
    // the case where gammapad recreated a device under the same name without
    // us seeing the IN_DELETE.
    for (auto it = mInputFds.begin(); it != mInputFds.end(); ) {
        char fdPath[PATH_MAX];
        char procLink[64];
        snprintf(procLink, sizeof(procLink), "/proc/self/fd/%d", *it);
        ssize_t rl = readlink(procLink, fdPath, sizeof(fdPath) - 1);
        if (rl > 0) {
            fdPath[rl] = '\0';
            if (strstr(fdPath, "(deleted)")) {
                // Recover the device name from the path so we can also
                // erase it from mOpenedDevices and let the rescan re-open it.
                const char* base = strrchr(fdPath, '/');
                if (base) {
                    base++;
                    char nameOnly[64];
                    size_t i = 0;
                    while (base[i] && base[i] != ' ' && i < sizeof(nameOnly) - 1) {
                        nameOnly[i] = base[i];
                        i++;
                    }
                    nameOnly[i] = '\0';
                    mOpenedDevices.erase(nameOnly);
                    ALOGI("Sweep: dropped stale fd %s", fdPath);
                }
                ioctl(*it, EVIOCGRAB, 0);
                close(*it);
                it = mInputFds.erase(it);
                continue;
            }
        }
        ++it;
    }

    // (2) Open any /dev/input/event* that we don't currently have.
    DIR* dir = opendir("/dev/input");
    if (!dir) return;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (strncmp(entry->d_name, "event", 5) != 0) continue;
        if (mOpenedDevices.count(entry->d_name)) continue;
        char path[PATH_MAX];
        snprintf(path, sizeof(path), "/dev/input/%s", entry->d_name);
        int fd = open(path, O_RDONLY | O_NONBLOCK);
        if (fd >= 0) {
            // Home-mode grab only; the overlay isolates via drop_input, not grab.
            if (android::base::GetBoolProperty("persist.gammaos.nano.grab_input", false)) {
                if (ioctl(fd, EVIOCGRAB, 1) < 0) {
                    ALOGW("EVIOCGRAB failed for swept %s: %s", path, strerror(errno));
                }
            }
            mInputFds.push_back(fd);
            mOpenedDevices.insert(entry->d_name);
            ALOGI("Sweep: opened previously-missed input device %s", path);
        }
    }
    closedir(dir);
}

// ---------------------------------------------------------------------------
// Menu navigation: Back / Select / Up / Down
// (Left/Right live in NanoMenuXmb.cpp because they drive the XMB system axis.)
// ---------------------------------------------------------------------------

void NanoMenu::handleBack() {
    // GammaOS Nano: back cancels any queued launch the user armed
    // before the system was ready. Without this, mLaunchPending
    // would still re-fire handleSelect() once isLaunchReady() flips.
    cancelPendingLaunch();
    if (mOskActive) {
        closeOsk();
        return;
    }
    if (mMenuState == MENU_WIFI) {
        closeWifiScreen();
        if (mSetupWizardActive) mMenuState = MENU_SETUP_WIZARD;
        return;
    }
    if (mMenuState == MENU_BT) {
        closeBtScreen();
        if (mSetupWizardActive) mMenuState = MENU_SETUP_WIZARD;
        return;
    }
    if (mMenuState == MENU_SETTINGS) { handleSettingsTreeBack(); return; }
    if (mPs3Xmb) { ps3XmbBack(); return; }
    if (mXmbMode) {
        if (mSearchActive) {
            mSearchActive = false;
            mOskQuery.clear();
            mSearchResults.clear();
        } else {
            // Exit XMB mode back to text menu
            mXmbMode = false;
            property_set("persist.gammaos.nano.xmb_mode", "0");
            mMenuState = MENU_MAIN;
            mDisplayDirty = true;
        }
        return;
    }
    if (mMenuState == MENU_RECENT) {
        mMenuState = MENU_MAIN;
        mRecentSelectedIndex = 0;
        mMenuScrollTop = 0;
        mDisplayDirty = true;
    } else if (mMenuState == MENU_APPS) {
        mMenuState = MENU_MAIN;
        mAppSelectedIndex = 0;
        mMenuScrollTop = 0;
        mDisplayDirty = true;
    }
}

void NanoMenu::handleSelect() {
    if (mOskActive) {
        oskAPress();
        return;
    }
    if (mMenuState == MENU_WIFI)     { handleWifiScreenSelect();   return; }
    if (mMenuState == MENU_BT)       { handleBtScreenSelect();     return; }
    if (mMenuState == MENU_SETTINGS) { handleSettingsTreeSelect();  return; }
    if (mPs3Xmb) { ps3XmbSelect(); return; }
    if (mXmbMode) {
        if (isOnSettingsColumn()) {
            openSettingsTree();
            return;
        }
        launchXmbGame();
        return;
    }
    if (mMenuState == MENU_RECENT) {
        int numEntries = (int)mRecentEntries.size();
        // Last item is "< Back"
        if (mRecentSelectedIndex >= numEntries) {
            handleBack();
            return;
        }
        // GammaOS Nano: gate launches until the system can actually
        // accept them. See NanoMenuSystem.cpp / isLaunchReady().
        if (!isLaunchReady()) {
            ALOGI("NanoMenu: recent launch deferred -- boot not ready");
            showLaunchBusyToast();
            return;
        }
        // Launch the selected game directly into RetroArch
        const auto& entry = mRecentEntries[mRecentSelectedIndex];
        ALOGI("NanoMenu: launching game: %s core: %s",
              entry.romPath.c_str(), entry.corePath.c_str());
        setLaunchRomPath(entry.romPath);
        android::base::SetProperty("sys.gammaos.nano.launch_core", entry.corePath);
        // Track launched package so NanoMenu can force-stop it on next restart
        {
            char launchApp[PROPERTY_VALUE_MAX] = {};
            property_get("sys.gammaos.nano.launch_app", launchApp, "com.retroarch.aarch64");
            android::base::SetProperty("sys.gammaos.nano.launched_pkg", launchApp);
        }
        // Trigger DE cache populate (ROM first, then delta sync everything)
        property_set("sys.gammaos.nano.cache_ready", "0");
        property_set("sys.gammaos.nano.cache_op", "populate");
        // Prime Quick Resume now — the persist write has time to flush to disk
        // while the game runs. ShutdownThread may update ROM/core from the
        // playlist if the user loaded a different game, but this ensures the
        // flag survives even if the reboot races the persist write.
        if (mQuickResumeEnabled) {
            setQrRomPath(entry.romPath);
            android::base::SetProperty("persist.gammaos.nano.qr_core", entry.corePath);
            property_set("persist.gammaos.nano.qr_prepared", "1");
        }
        // Flag so next nano menu restart returns to Recently Played
        property_set("sys.gammaos.nano.return_recent", "1");
        property_set("service.bootanim.nano_retroarch", "1");
        // Tell InputDispatcher to drop events immediately — prevents a fast
        // double-press A from queuing a second event before the transition.
        property_set("sys.gammaos.nano.drop_input", "1");
        // Set a timestamp fence — InputDispatcher drops any events with
        // eventTime <= this value, covering the race where the A-DOWN was
        // queued before drop_input was set.
        struct timespec ts;
        clock_gettime(CLOCK_MONOTONIC, &ts);
        int64_t fenceNs = (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
        char buf[32];
        snprintf(buf, sizeof(buf), "%" PRId64, fenceNs);
        property_set("sys.gammaos.nano.drop_fence_ns", buf);
        // Don't exit yet — wait for the select key to be released so the
        // key-up event passes through Android's InputReader before RetroArch
        // gets focus. Otherwise the A press leaks to RetroArch as a phantom input.
        mWaitForRelease = true;
        return;
    }

    if (mMenuState == MENU_APPS) {
        int numApps = (int)mAppEntries.size();
        // Last item is "< Back"
        if (mAppSelectedIndex >= numApps) {
            handleBack();
            return;
        }
        // GammaOS Nano: gate -- see MENU_RECENT branch above.
        if (!isLaunchReady()) {
            ALOGI("NanoMenu: app launch deferred -- boot not ready");
            showLaunchBusyToast();
            return;
        }
        // Launch the selected app
        const auto& app = mAppEntries[mAppSelectedIndex];
        ALOGI("NanoMenu: launching app: %s", app.packageName.c_str());
        android::base::SetProperty("sys.gammaos.nano.launch_app", app.packageName);
        // Track launched package so NanoMenu can force-stop it on next restart
        android::base::SetProperty("sys.gammaos.nano.launched_pkg", app.packageName);
        // Clear any ROM/core properties so RootWindowContainer uses generic launch
        setLaunchRomPath("");
        android::base::SetProperty("sys.gammaos.nano.launch_core", "");
        // Clear any stale Quick Resume priming: the user is launching a
        // non-drastic/non-retroarch app, so a leftover qr_prepared=1 from an
        // earlier game would otherwise auto-resume that game on the next
        // nano start instead of returning the user to Applications.
        property_set("persist.gammaos.nano.qr_prepared", "0");
        android::base::SetProperty("persist.gammaos.nano.qr_core", "");
        // Flag so next nano menu restart returns to Applications
        property_set("sys.gammaos.nano.return_apps", "1");
        property_set("service.bootanim.nano_retroarch", "1");
        property_set("sys.gammaos.nano.drop_input", "1");
        mWaitForRelease = true;
        return;
    }

    // Main menu - dispatch by index (order matches buildMenu)
    // 0=RetroArch 1=RecentlyPlayed 2=Applications 3=BootAndroid
    // 4=Recovery 5=SafeMode 6=Reboot 7=PowerOff
    ALOGD("Select item %d: %s", mSelectedIndex, mMenuItems[mSelectedIndex].label.c_str());
    if (mSelectedIndex == 0) { // RetroArch
        // GammaOS Nano: gate -- see MENU_RECENT branch above.
        if (!isLaunchReady()) {
            ALOGI("NanoMenu: RetroArch launch deferred -- boot not ready");
            showLaunchBusyToast();
            return;
        }
        property_set("service.bootanim.nano_retroarch", "1");
        property_set("sys.gammaos.nano.drop_input", "1");
        mWaitForRelease = true;
    } else if (mSelectedIndex == 1) { // Recently Played
        if (!mStorageReady) return; // greyed out, ignore
        // Load playlist from RetroArch's content_history.lpl (needs CE unlock)
        loadRecentPlaylist();
        mMenuState = MENU_RECENT;
        mRecentSelectedIndex = 0;
        mMenuScrollTop = 0;
        mDisplayDirty = true;
    } else if (mSelectedIndex == 2) { // Applications
        if (!mStorageReady) return; // greyed out, ignore
        loadInstalledApps();
        mMenuState = MENU_APPS;
        mAppSelectedIndex = 0;
        mMenuScrollTop = 0;
        mDisplayDirty = true;
    } else if (mSelectedIndex == 3) { // Boot Android
        // Full Android needs a clean boot.  Dispatch via nano_action so
        // init (which has powerctl_prop access) handles the reboot.
        // Clear QR priming: the user is leaving nano for full Android,
        // not resuming a game.
        property_set("persist.gammaos.nano.qr_prepared", "0");
        android::base::SetProperty("persist.gammaos.nano.qr_core", "");
        property_set("service.bootanim.nano_action", "android");
    } else if (mSelectedIndex == 4) { // Recovery Mode
        property_set("persist.gammaos.nano.qr_prepared", "0");
        android::base::SetProperty("persist.gammaos.nano.qr_core", "");
        property_set("service.bootanim.nano_action", "recovery");
    } else if (mSelectedIndex == 5) { // Safe Mode
        property_set("persist.gammaos.nano.qr_prepared", "0");
        android::base::SetProperty("persist.gammaos.nano.qr_core", "");
        property_set("service.bootanim.nano_action", "safemode");
    } else if (mSelectedIndex == 6) { // Reboot
        prepareShutdown("reboot");
    } else if (mSelectedIndex == 7) { // Power Off
        prepareShutdown("shutdown");
    }
}

void NanoMenu::handleUp() {
    // GammaOS Nano: navigating cancels any queued launch.
    cancelPendingLaunch();
    if (mSetupWizardActive && mMenuState == MENU_SETUP_WIZARD && !mPs3WizActive) {
        handleSetupUp(); return;
    }
    if (mOskActive) {
        oskMoveCursor(NavDir::Up);
        return;
    }
    if (mMenuState == MENU_WIFI)     { handleWifiScreenUp();     return; }
    if (mMenuState == MENU_BT)       { handleBtScreenUp();       return; }
    if (mMenuState == MENU_SETTINGS) { handleSettingsTreeUp();    return; }
    if (mPs3Xmb) { ps3XmbUp(); return; }
    if (mXmbMode) {
        if (mSearchActive) {
            if (mSearchSelectedIndex > 0) mSearchSelectedIndex--;
        } else if (isOnSettingsColumn()) {
            if (mSettingsSelectedIndex > 0) mSettingsSelectedIndex--;
            mDisplayDirty = true;
        } else {
            if (mXmbGameIndex > 0) mXmbGameIndex--;
        }
        return;
    }
    if (mMenuState == MENU_RECENT) {
        if (mRecentSelectedIndex > 0) mRecentSelectedIndex--;
    } else if (mMenuState == MENU_APPS) {
        if (mAppSelectedIndex > 0) mAppSelectedIndex--;
    } else {
        if (mSelectedIndex > 0) {
            mSelectedIndex--;
            // Skip greyed-out items when storage isn't ready
            if (!mStorageReady && mSelectedIndex < (int)mMenuItems.size()) {
                const auto& lbl = mMenuItems[mSelectedIndex].label;
                if ((mSelectedIndex == 1 || mSelectedIndex == 2)
                    && mSelectedIndex > 0) {
                    mSelectedIndex--;
                    // Check again for the other greyed item
                    if (!mStorageReady && mSelectedIndex < (int)mMenuItems.size()) {
                        const auto& lbl2 = mMenuItems[mSelectedIndex].label;
                        if ((mSelectedIndex == 1 || mSelectedIndex == 2)
                            && mSelectedIndex > 0) {
                            mSelectedIndex--;
                        }
                    }
                }
            }
        }
    }
}

void NanoMenu::handleDown() {
    // GammaOS Nano: navigating cancels any queued launch.
    cancelPendingLaunch();
    if (mSetupWizardActive && mMenuState == MENU_SETUP_WIZARD && !mPs3WizActive) {
        handleSetupDown(); return;
    }
    if (mOskActive) {
        oskMoveCursor(NavDir::Down);
        return;
    }
    if (mMenuState == MENU_WIFI)     { handleWifiScreenDown();     return; }
    if (mMenuState == MENU_BT)       { handleBtScreenDown();       return; }
    if (mMenuState == MENU_SETTINGS) { handleSettingsTreeDown();    return; }
    if (mPs3Xmb) { ps3XmbDown(); return; }
    if (mXmbMode) {
        if (mSearchActive) {
            int maxIdx = (int)mSearchResults.size() - 1;
            if (mSearchSelectedIndex < maxIdx) mSearchSelectedIndex++;
        } else if (isOnSettingsColumn()) {
            int maxIdx = (int)mSettingsItems.size() - 1;
            if (mSettingsSelectedIndex < maxIdx) mSettingsSelectedIndex++;
            mDisplayDirty = true;
        } else if (mXmbSystemIndex == -1) {
            // Recently Played
            int maxIdx = (int)mXmbRecent.size() - 1;
            if (mXmbGameIndex < maxIdx) mXmbGameIndex++;
        } else {
            int selSys = mXmbSystemIndex;
            if (selSys >= 0 && selSys < (int)mXmbSystems.size()) {
                int maxIdx = (int)mXmbSystems[selSys].roms.size() - 1;
                if (mXmbGameIndex < maxIdx) mXmbGameIndex++;
            }
        }
        return;
    }
    if (mMenuState == MENU_RECENT) {
        int maxIdx = (int)mRecentEntries.size(); // "< Back" is at this index
        if (mRecentSelectedIndex < maxIdx) mRecentSelectedIndex++;
    } else if (mMenuState == MENU_APPS) {
        int maxIdx = (int)mAppEntries.size(); // "< Back" is at this index
        if (mAppSelectedIndex < maxIdx) mAppSelectedIndex++;
    } else {
        int last = (int)mMenuItems.size() - 1;
        if (mSelectedIndex < last) {
            mSelectedIndex++;
            // Skip greyed-out items when storage isn't ready
            if (!mStorageReady && mSelectedIndex < (int)mMenuItems.size()) {
                const auto& lbl = mMenuItems[mSelectedIndex].label;
                if ((mSelectedIndex == 1 || mSelectedIndex == 2)
                    && mSelectedIndex < last) {
                    mSelectedIndex++;
                    // Check again for the other greyed item
                    if (!mStorageReady && mSelectedIndex < (int)mMenuItems.size()) {
                        const auto& lbl2 = mMenuItems[mSelectedIndex].label;
                        if ((mSelectedIndex == 1 || mSelectedIndex == 2)
                            && mSelectedIndex < last) {
                            mSelectedIndex++;
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Hold-to-repeat navigation.
//
// Users expect that holding a dpad direction (or pushing a stick past the
// deadzone and keeping it there) will scroll long lists continuously, not
// just move a single step. We model this as an edge-triggered "press"
// that records which direction is held, plus a per-frame tick that fires
// the matching handle* function on an accelerating cadence.
//
// Cadence:
//   - Initial delay before the first repeat fires.
//   - Interval starts slow and shortens with each repeat, floored at a
//     minimum so the scroll stays controllable on very long lists.
//
// navPress() always fires the first handle* call synchronously so a tap
// (press then immediate release) still moves exactly one step.
// ---------------------------------------------------------------------------

// Match the PS3 firmware/web key-repeat (index.html INPUT_* constants): an
// initial delay, then a base interval that accelerates GEOMETRICALLY (divide by
// the accel multiplier each repeat) down to a floor. The geometric ramp gives a
// smooth increase in speed while holding (the linear decrement juddered).
static constexpr int64_t kNavInitialDelayMs    = 300;   // INPUT_INITIAL_DELAY_MS
static constexpr int64_t kNavSlowIntervalMs    = 200;   // INPUT_REPEAT_MS (base)
static constexpr int64_t kNavMinIntervalMs     = 50;    // INPUT_MIN_REPEAT_MS
static constexpr float   kNavAccelMult         = 1.4f;  // INPUT_ACCEL_MULT

void NanoMenu::navPress(NavDir dir) {
    if (dir == NavDir::None) return;
    mLastInputMs = android::uptimeMillis();   // dpad/HAT/stick = user activity
    // Idempotent: if this direction is already the held one, don't re-fire.
    // Guards against duplicate events (e.g. HAT re-reporting same value)
    // from double-stepping the selection.
    if (dir == mNavHeldDir) return;
    // Fire the step immediately; a brief tap should always move one slot.
    switch (dir) {
    case NavDir::Up:    handleUp();    break;
    case NavDir::Down:  handleDown();  break;
    case NavDir::Left:  handleLeft();  break;
    case NavDir::Right: handleRight(); break;
    case NavDir::None:  break;
    }
    mNavHeldDir      = dir;
    mNavHeldStartMs  = android::uptimeMillis();
    mNavLastRepeatMs = mNavHeldStartMs;
    mNavRepeatCount  = 0;
}

void NanoMenu::navRelease(NavDir dir) {
    // dir == None means "whichever direction is currently held" — used
    // by axis handlers that can't tell press-up from press-down on the
    // same axis without extra state.
    if (dir == NavDir::None || dir == mNavHeldDir) {
        mNavHeldDir      = NavDir::None;
        mNavHeldStartMs  = 0;
        mNavLastRepeatMs = 0;
        mNavRepeatCount  = 0;
    }
}

void NanoMenu::tickNavRepeat() {
    if (mNavHeldDir == NavDir::None) return;
    // Suppress auto-repeat while waiting for the launch key to be released
    // (the launch path consumes events specially and shouldn't receive
    // synthetic scroll steps). OSK and WiFi/BT screens all dispatch
    // through the handle* functions, so they benefit from auto-repeat
    // just like the XMB list.
    if (mWaitForRelease) return;

    const int64_t now = android::uptimeMillis();

    // Firmware/web schedule (index.html processKeyRepeat): the first repeat waits
    // the full initial delay, the SECOND is a full slow step, and only THEN does
    // the geometric acceleration begin -> intervals 300, 200, 143, 102, 73, 52,
    // 50... This deliberate slow start is the "debounce" feel; using count (not
    // count-1) in the exponent skipped the 200ms step and burst to top speed a
    // step early.
    int64_t interval;
    if (mNavRepeatCount == 0) {
        interval = kNavInitialDelayMs;                 // 300ms before the first repeat
    } else {
        interval = (int64_t)((float)kNavSlowIntervalMs /
                             powf(kNavAccelMult, (float)(mNavRepeatCount - 1)));
        if (interval < kNavMinIntervalMs) interval = kNavMinIntervalMs;
    }
    if (now - mNavLastRepeatMs < interval) return;

    switch (mNavHeldDir) {
    case NavDir::Up:    handleUp();    break;
    case NavDir::Down:  handleDown();  break;
    case NavDir::Left:  handleLeft();  break;
    case NavDir::Right: handleRight(); break;
    case NavDir::None:  break;
    }
    mLastInputMs     = now;   // a held direction is ongoing user activity
    mNavLastRepeatMs = now;
    mNavRepeatCount++;
}

// ---------------------------------------------------------------------------
// Event loop: drain every input fd, dispatch to navigation / power / OSK.
// ---------------------------------------------------------------------------

bool NanoMenu::enterDrmSleep() {
    // Blank our DRM-owned panels: clear the slot-0 AHB FBOs (what drmFrameEnd
    // scans out) and turn every backlight off, so the wake-time recommit
    // relights onto black rather than a stale frame.
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    if (sDrmZeroCopy && sAhbRingPrimary[0].glFbo) {
        glBindFramebuffer(GL_FRAMEBUFFER, sAhbRingPrimary[0].glFbo);
        glClear(GL_COLOR_BUFFER_BIT);
        if (sAhbRingSecondary[0].glFbo) {
            glBindFramebuffer(GL_FRAMEBUFFER, sAhbRingSecondary[0].glFbo);
            glClear(GL_COLOR_BUFFER_BIT);
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    } else {
        glClear(GL_COLOR_BUFFER_BIT);
    }
    drmFrameEnd(mDisplay, mSurface);
    nanobl::nanoBacklightSet(0);
    setBrightnessViaHal(0);

    // Drive the WHOLE device into a real PowerManager suspend (not just a
    // blanked busy-poll): the nano-dosleep init service injects KEYCODE_SLEEP
    // (nano's bootstrap mount namespace cannot run app_process directly) so
    // PowerManager runs its normal goToSleep -> doze -> suspend. Before
    // boot_completed PowerManager is not ready, so fall back to the legacy
    // blank + 60s-then-shutdown.
    bool pmSleep = property_get_bool("sys.boot_completed", false);
    if (pmSleep) {
        ALOGI("NanoMenu: services up -> PowerManager system sleep");
        property_set("sys.gammaos.nano.dosleep", "1");
    }

    bool asleep = true;
    int64_t sleepStart = android::uptimeMillis();
    while (asleep) {
        // Block on the input fds so the CPU can idle / suspend (a busy poll
        // would keep it awake and defeat the suspend). With PowerManager
        // engaged, block indefinitely: the system suspends and this thread
        // freezes here until a wake source fires. Otherwise cap the wait at
        // the remaining 60s budget.
        struct pollfd pfds[16];
        int nf = 0;
        for (int fd : mInputFds) {
            if (fd >= 0 && nf < 16) { pfds[nf].fd = fd; pfds[nf].events = POLLIN; nf++; }
        }
        int timeoutMs = -1;
        if (!pmSleep) {
            int64_t left = 60000 - (android::uptimeMillis() - sleepStart);
            if (left <= 0) {
                ALOGI("NanoMenu: sleep timeout, shutting down");
                prepareShutdown("shutdown");
                return false;
            }
            timeoutMs = (int)left;
        }
        poll(pfds, nf, timeoutMs);
        struct input_event wake;
        for (int wfd : mInputFds) {
            while (read(wfd, &wake, sizeof(wake)) == sizeof(wake)) {
                // Wake on a power-button press OR the lid opening (SW_LID -> 0).
                if (wake.type == EV_KEY && wake.code == KEY_POWER
                    && wake.value == 1) {
                    asleep = false;
                    // The waking press bypasses the pollInput EV_KEY stamp
                    // (this loop consumes it), so stamp here or the menu could
                    // wake straight into the idle frame rate.
                    mLastInputMs = android::uptimeMillis();
                } else if (wake.type == EV_SW && wake.code == SW_LID
                           && wake.value == 0) {
                    asleep = false;
                    mLastInputMs = android::uptimeMillis();
                }
            }
        }
    }
    // Woke. If we put PowerManager to sleep, wake it too (it never saw the
    // wake source, so it will not auto-wake) via an injected KEYCODE_WAKEUP.
    if (pmSleep) {
        property_set("sys.gammaos.nano.dosleep", "0");
        property_set("sys.gammaos.nano.dowake", "1");
        ALOGI("NanoMenu: waking PowerManager (KEYCODE_WAKEUP)");
    }
    usleep(200000);
    { struct input_event d; for (int dfd : mInputFds) {
        while (read(dfd, &d, sizeof(d)) == sizeof(d)) {} } }
    // Kernel resume re-enables the CRTCs with NO planes attached (rockchip
    // vop2 confirmed; every legacy page flip then EBUSYs forever and both
    // panels stay black behind a lit backlight). Re-commit the full modeset
    // + reset the flip bookkeeping BEFORE relighting so the panels come back
    // showing content. pollInput runs on the render thread, which owns all
    // DRM/GL state, so this is race-free. Idempotent and harmless on the
    // legacy pre-boot_completed path that never suspended.
    drmResumeRecommit();
    {
        int sysfs_val = mBrightness * mMaxBrightness / 255;
        if (sysfs_val < 1) sysfs_val = 1;
        nanobl::nanoBacklightSet(mBrightness);
        setBrightnessViaHal(sysfs_val);
    }
    ALOGI("NanoMenu: woke up");
    return true;
}

void NanoMenu::pollInput() {
    // Overlay launch transition: while a launch is pending (the overlay is held up
    // until the new app resumes), FREEZE the XMB - drain and ignore all input so the
    // user cannot keep navigating the menu while the app is starting (which looked
    // like the launch had not registered). The overlay dismisses onto the app once
    // it is ready (overlayPoll). Draining keeps stale presses from flushing later.
    if (mOverlayMode && mOverlayLaunchPending) {
        struct input_event dev;
        for (int fd : mInputFds) {
            if (fd < 0) continue;
            while (read(fd, &dev, sizeof(dev)) == (ssize_t)sizeof(dev)) { /* discard */ }
        }
        property_set("sys.gammaos.nano.nav", "");   // swallow scripted nav too
        return;
    }
    // Home (non-overlay) launch fade-out: once the launching select has been
    // released (mLaunchFadeStart stamped in the wait-for-release handler below),
    // hold the hand-off to the app until the XMB has faded to black (render() draws
    // the ramp), then exit. Makes launching a game/app a smooth fade, not a cut.
    if (mLaunchFadeStart > 0 && (int64_t)uptimeMillis() - mLaunchFadeStart >= 260) {
        mExitRequested = true;
    }
    // Test navigation hook: `setprop sys.gammaos.nano.nav <action>` injects one
    // nav action (left/right/up/down/enter/back) then clears the prop. The
    // device analog of the web app's simulateInput, used for scripted on-device
    // 1:1 verification. No effect when the prop is empty (one cheap read/frame).
    {
        // The hook is empty for the entire life of a normal session. Reading the
        // property's serial (a cheap pointer-deref) and only doing the full read +
        // dispatch when it advances keeps this at ~one integer compare per frame
        // instead of a full name lookup. The serial bumps on every set (including
        // the self-clear below), so a real injection is never missed. We recapture
        // the serial after any dispatch+clear so the bookkeeping does not chase our
        // own write. The prop is created lazily on first set, so retry find while null.
        static const prop_info* sNavPi = nullptr;
        static uint32_t sNavSerial = 0;
        if (!sNavPi) sNavPi = __system_property_find("sys.gammaos.nano.nav");
        if (sNavPi && __system_property_serial(sNavPi) != sNavSerial) {
        char navbuf[PROPERTY_VALUE_MAX];
        if (property_get("sys.gammaos.nano.nav", navbuf, "") > 0 && navbuf[0]) {
            mLastInputMs = android::uptimeMillis();   // scripted nav = activity
            if      (!strcmp(navbuf, "left"))  handleLeft();
            else if (!strcmp(navbuf, "right")) handleRight();
            else if (!strcmp(navbuf, "up"))    handleUp();
            else if (!strcmp(navbuf, "down"))  handleDown();
            else if (!strcmp(navbuf, "enter")) handleSelect();
            else if (!strcmp(navbuf, "back"))  handleBack();
            // OSK scripting for 1:1 verification: `type:<text>` inserts each ASCII
            // character at the caret, `submit` commits the on-screen keyboard.
            else if (!strncmp(navbuf, "type:", 5)) {
                if (mOskActive) for (const char* p = navbuf + 5; *p; ++p) oskType(*p);
            }
            else if (!strcmp(navbuf, "submit")) { if (mOskActive) oskConfirm(); }
            // Game Systems list scripting: l1/r1 reorder the selected system,
            // x toggles its enabled state (the physical L1/R1/X buttons do the
            // same; the nav hook only injects dpad/A/B so these widen it).
            else if (!strcmp(navbuf, "l1") || !strcmp(navbuf, "r1") || !strcmp(navbuf, "x")) {
                if (mPs3Xmb && ps3TopScreenKind() == GS_LIST && !mPs3Stack.empty()) {
                    auto& its = mPs3Stack.back().items; int sel = mPs3Stack.back().sel;
                    if (sel >= 0 && sel < (int)its.size() && its[sel].kind == PS3_GS_SYSTEM_ROW) {
                        if (!strcmp(navbuf, "l1"))      gsReorderSystem(its[sel].a, -1);
                        else if (!strcmp(navbuf, "r1")) gsReorderSystem(its[sel].a, +1);
                        else                            gsToggleSystem(its[sel].a);
                    }
                }
            }
            property_set("sys.gammaos.nano.nav", "");
        }
        // Recapture the serial AFTER the (possible) self-clear so the next frame is
        // a single integer compare and we do not re-fire on our own write.
        sNavSerial = __system_property_serial(sNavPi);
        }
    }
    struct input_event ev;
    for (int fd : mInputFds) {
        while (read(fd, &ev, sizeof(ev)) == sizeof(ev)) {
            // Any button edge counts as user activity for the idle frame-rate
            // timer. EV_KEY only fires on real state changes, so this cannot
            // be kept alive by analog-stick noise (sticks go through navPress
            // which is edge-triggered past the deadzone).
            if (ev.type == EV_KEY) mLastInputMs = android::uptimeMillis();
            // Cold-boot intro: any button press skips to the end of the sequence
            // and is CONSUMED here (so the same press does not also navigate or
            // launch once the XMB appears). All events are swallowed during boot.
            if (mPs3BootActive) {
                if (ev.type == EV_KEY && ev.value == 1) ps3BootSkip();
                continue;
            }
            // Wait-for-release: after a launch is triggered, keep running
            // until the select key is released. This ensures Android's
            // InputReader sees the full press-release cycle before RetroArch
            // gets focus, preventing phantom A-button presses.
            if (mWaitForRelease) {
                if (ev.type == EV_KEY && ev.value == 0
                    && (ev.code == KEY_ENTER || ev.code == BTN_SOUTH)) {
                    // Don't hand off immediately: start the launch fade-out
                    // (render() fades the XMB to black). The exit fires once the
                    // fade completes (the mLaunchFadeStart check at the top of
                    // pollInput), so the game/app launch fades out instead of a cut.
                    if (mLaunchFadeStart == 0) {
                        ALOGD("NanoMenu: select released, fading out then launching");
                        mLaunchFadeStart = uptimeMillis();
                    }
                }
                continue; // discard all other events while waiting
            }
            // Track SELECT button state; in XMB mode, press refreshes game lists
            if (ev.type == EV_KEY && ev.code == BTN_SELECT) {
                if (ev.value == 1) {
                    if (mOskActive) oskCycleLanguage(1);   // Select cycles language
                    else if (mXmbMode) forceRescanAllSystems();
                }
                mSelectHeld = (ev.value != 0);
            }
            if (ev.type == EV_KEY && ev.code == BTN_START) {
                mStartHeld = (ev.value != 0);
            }
            // Power button handling
            if (ev.type == EV_KEY && ev.code == KEY_POWER) {
                // Overlay XMB: PhoneWindowManager OWNS the power button entirely -
                // it detects nano mode and TOGGLES the overlay (show/hide) on a
                // power-hold. nano must not act on power here (acting on the open,
                // ungrabbed power fd would race PWM's gesture). Ignore it; dismiss
                // is via PWM's toggle or the gamepad Back (overlayResume).
                if (mOverlayMode) {
                    continue;
                }
                if (ev.value == 1) {
                    mPowerPressTime = android::uptimeMillis();
                    // Immediately start polling for long press in a tight loop
                    // so we can shutdown before the hardware cuts power
                    bool shutdown = false;
                    for (int poll = 0; poll < 40; poll++) { // 40 * 50ms = 2s
                        usleep(50000);
                        // Check if key was released
                        struct input_event pe;
                        bool released = false;
                        for (int pfd : mInputFds) {
                            while (read(pfd, &pe, sizeof(pe)) == sizeof(pe)) {
                                if (pe.type == EV_KEY && pe.code == KEY_POWER
                                    && pe.value == 0) {
                                    released = true;
                                }
                            }
                        }
                        if (released) break;
                        if (android::uptimeMillis() - mPowerPressTime > 1500) {
                            // 1.5s hold: trigger shutdown before hardware kills us
                            ALOGI("NanoMenu: power hold 1.5s, shutting down");
                            shutdown = true;
                            break;
                        }
                    }
                    if (shutdown) {
                        mPowerPressTime = 0;
                        prepareShutdown("shutdown");
                        continue;
                    }
                    // Key was released before 1.5s — short press = sleep.
                    mPowerPressTime = 0;
                    ALOGI("NanoMenu: power short press, sleeping");
                    if (!enterDrmSleep()) return;
                }
                continue;
            }
            // Lid (hall-effect) switch, DRM-direct home only. Closing the lid
            // blanks + recommits our own DRM panel around the suspend (opening
            // wakes, handled inside enterDrmSleep's wait loop). The FRAMEWORK
            // still drives the actual system sleep on the lid (its goToSleep ->
            // sys.screen.state=off -> force_sleep -> sleep.sh -> echo mem); the
            // two cooperate. In SF mode (no DRM master) the framework owns the
            // whole lid flow, so we do nothing. Overlay mode also defers to it.
            if (ev.type == EV_SW && ev.code == SW_LID
                && sDrmActive && !mOverlayMode) {
                if (ev.value != 0) {   // lid closed
                    ALOGI("NanoMenu: lid closed, sleeping");
                    if (!enterDrmSleep()) return;
                }
                continue;
            }
            // Directional key release clears hold-to-repeat state so the
            // auto-scroll tick stops. Only directional keys matter here;
            // other keys don't participate in the repeat scheduler.
            if (ev.type == EV_KEY && ev.value == 0) {
                switch (ev.code) {
                case KEY_UP:    navRelease(NavDir::Up);    break;
                case KEY_DOWN:  navRelease(NavDir::Down);  break;
                case KEY_LEFT:  navRelease(NavDir::Left);  break;
                case KEY_RIGHT: navRelease(NavDir::Right); break;
                case BTN_SOUTH: if (mOskActive) oskARelease(); break;
                default: break;
                }
            }
            if (ev.type == EV_KEY && (ev.value == 1 || ev.value == 2)) {
                // Volume keys: SELECT+VOL = brightness, VOL alone = volume
                if (ev.code == KEY_VOLUMEUP || ev.code == KEY_VOLUMEDOWN) {
                    if (mSelectHeld) {
                        adjustBrightness(ev.code == KEY_VOLUMEUP ? 1 : -1);
                    } else if (ev.value == 1) {
                        adjustVolume(ev.code == KEY_VOLUMEUP ? 1 : -1);
                    }
                    continue;
                }
                if (ev.value == 1) {
                    // Setup wizard intercepts all input when active.
                    // WiFi/BT sub-screens during setup still use the
                    // normal OSK + WiFi/BT handlers since the setup
                    // wizard routes A/B through them. Only the top-level
                    // Start button and step navigation is different.
                    if (mSetupWizardActive && !mOskActive
                        && mMenuState != MENU_WIFI && mMenuState != MENU_BT) {
                        // The network step runs the PS3 net wizard; route its
                        // buttons to the wiz* handlers (Select/Back already chain
                        // through ps3XmbSelect/Back -> wizConfirm/wizBack; Up/Down/
                        // Left/Right via navPress; X = re-scan the AP list).
                        if (mPs3WizActive) {
                            switch (ev.code) {
                            case BTN_SOUTH: case KEY_ENTER: handleSelect(); break;
                            case BTN_EAST:  case KEY_BACK:  handleBack();   break;
                            case KEY_UP:    navPress(NavDir::Up);    break;
                            case KEY_DOWN:  navPress(NavDir::Down);  break;
                            case KEY_LEFT:  navPress(NavDir::Left);  break;
                            case KEY_RIGHT: navPress(NavDir::Right); break;
                            case BTN_NORTH: wizRescan(); break;
                            // Start skips the wifi / bluetooth step entirely.
                            case BTN_START: handleSetupStart(); break;
                            default: break;
                            }
                            continue;
                        }
                        switch (ev.code) {
                        case KEY_UP:    navPress(NavDir::Up);   break;
                        case KEY_DOWN:  navPress(NavDir::Down); break;
                        case BTN_SOUTH: case KEY_ENTER:
                            handleSetupSelect(); break;
                        case BTN_EAST: case KEY_BACK:
                            handleSetupBack(); break;
                        case KEY_LEFT:  navPress(NavDir::Left);  break;
                        case KEY_RIGHT: navPress(NavDir::Right); break;
                        case BTN_START:
                            handleSetupStart(); break;
                        default: break;
                        }
                        continue;
                    }
                    // Only handle menu nav on initial press, not repeat.
                    // For directional keys we route through navPress() so the
                    // hold-to-repeat tick can drive continuous scrolling while
                    // the key stays down; the release is handled separately
                    // (ev.value == 0 branch below).
                    switch (ev.code) {
                    case KEY_UP:
                        navPress(NavDir::Up); break;
                    case KEY_DOWN:
                        navPress(NavDir::Down); break;
                    case BTN_SOUTH:
                        handleSelect(); break;
                    case KEY_ENTER:
                        if (mOskActive) oskConfirm();
                        else handleSelect();
                        break;
                    case BTN_START:
                        // Gamepad Start: confirm the OSK query / password.
                        // The OSK help text advertises "Start:Submit" and
                        // "Start:Search" so this must also trigger submit
                        // on devices that don't map the physical Start
                        // button to KEY_ENTER.
                        if (mOskActive) oskConfirm();
                        else if (mSetupWizardActive) handleSetupStart();
                        break;
                    case BTN_EAST: case KEY_BACK:
                        if (mSetupWizardActive) handleSetupBack();
                        else handleBack();
                        break;
                    case KEY_LEFT:
                        navPress(NavDir::Left); break;
                    case KEY_RIGHT:
                        navPress(NavDir::Right); break;
                    case BTN_WEST: // Y button (Nintendo layout: BTN_WEST = Y)
                        if (mMenuState == MENU_WIFI) { handleWifiScreenY(); break; }
                        if (mMenuState == MENU_BT)   { handleBtScreenY();   break; }
                        // Icon grid picker: Y opens the name-filter OSK.
                        if (mPs3Xmb && ps3TopScreenKind() == GS_ICONGRID) {
                            openOskForPassword("Filter Icons", [this](const std::string& v) {
                                mIconGridFilter = v;   // strcasestr makes the match case-insensitive
                                applyIconGridFilter();
                            });
                            mOskPasswordMode = false; mOskPlaintext = true;
                            mOskQuery = mIconGridFilter; mOsk.caret = (int)mOskQuery.size();
                            break;
                        }
                        // Emulator picker: Y opens the platform/emulator filter OSK.
                        if (mPs3Xmb && ps3TopScreenKind() == GS_EMUPICK) {
                            openOskForPassword("Filter Emulators", [this](const std::string& v) {
                                mEmuPickFilter = v;   // strcasestr makes it case-insensitive
                                if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_EMUPICK) {
                                    buildEmulatorPicker(mPs3Stack.back());
                                    mPs3Stack.back().sel = 0;
                                }
                            });
                            mOskPasswordMode = false; mOskPlaintext = true;
                            mOskQuery = mEmuPickFilter; mOsk.caret = (int)mOskQuery.size();
                            break;
                        }
                        // Game Systems list: Y removes a custom system (built-ins only disable).
                        if (mPs3Xmb && ps3TopScreenKind() == GS_LIST) {
                            auto& its = mPs3Stack.back().items; int sel = mPs3Stack.back().sel;
                            if (sel >= 0 && sel < (int)its.size() && its[sel].kind == PS3_GS_SYSTEM_ROW) {
                                int si = its[sel].a;
                                if (si >= 0 && si < (int)mXmbSystems.size() && !mXmbSystems[si].builtin)
                                    gsOpenRemoveConfirm(si);
                            }
                            break;
                        }
                        // Scan-folders screen: Y removes the selected scan source.
                        if (mPs3Xmb && ps3TopScreenKind() == GS_FOLDER) {
                            auto& its = mPs3Stack.back().items; int sel = mPs3Stack.back().sel;
                            if (sel >= 0 && sel < (int)its.size() && its[sel].kind == PS3_GS_SCANSRC)
                                gsRemoveScanSource(its[sel].a);
                            break;
                        }
                        if (mXmbMode) {
                            // Y: search in XMB mode
                            if (mOskActive) {
                                closeOsk();
                            } else if (mSearchActive) {
                                mOskActive = true;
                            } else {
                                openOsk();
                            }
                        } else {
                            // Y: cycle wallpaper in list mode
                            sActiveEffectIdx = (sActiveEffectIdx + 1) % kNumActiveEffects;
                            mCurrentEffect = kActiveEffects[sActiveEffectIdx];
                            if (mCurrentEffect >= 1 && mCurrentEffect <= 10) initEffects();
                            mDisplayDirty = true;
                            ALOGD("Effect: %d (%s)", mCurrentEffect, kEffectNames[mCurrentEffect]);
                            { char buf[16]; snprintf(buf, sizeof(buf), "%d", mCurrentEffect);
                              property_set("persist.gammaos.nano.wallpaper", buf); }
                        }
                        break;
                    case BTN_NORTH: // X button (Nintendo layout: BTN_NORTH = X)
                        if (mOskActive) { oskBackspace(); break; }
                        if (mPs3WizActive) { wizRescan(); break; }   // X: re-scan on the AP list
                        if (mMenuState == MENU_WIFI) { handleWifiScreenX(); break; }
                        if (mMenuState == MENU_BT)   { handleBtScreenX();   break; }
                        // Game Systems list: X toggles the selected system's enabled state.
                        if (mPs3Xmb && ps3TopScreenKind() == GS_LIST) {
                            auto& its = mPs3Stack.back().items; int sel = mPs3Stack.back().sel;
                            if (sel >= 0 && sel < (int)its.size() && its[sel].kind == PS3_GS_SYSTEM_ROW)
                                gsToggleSystem(its[sel].a);
                            break;
                        }
                        // X (Square) cycles the wallpaper in EVERY state, exactly like
                        // the home XMB - including the in-game overlay. It never quits
                        // the running game (quitting is the back-long-press clean exit);
                        // quitting on X stranded the user with the app gone and no way
                        // back. Falls through to the wallpaper cycle below.
                        // X: cycle wallpaper/FX
                        sActiveEffectIdx = (sActiveEffectIdx + 1) % kNumActiveEffects;
                        mCurrentEffect = kActiveEffects[sActiveEffectIdx];
                        if (mCurrentEffect >= 1 && mCurrentEffect <= 10) initEffects();
                        mDisplayDirty = true;
                        ALOGD("Effect: %d (%s)", mCurrentEffect, kEffectNames[mCurrentEffect]);
                        { char buf[16]; snprintf(buf, sizeof(buf), "%d", mCurrentEffect);
                          property_set("persist.gammaos.nano.wallpaper", buf); }
                        break;
                    case BTN_TL: case KEY_L:
                        if (mOskActive) {
                            oskToggleShift();
                            break;
                        }
                        // Game Systems list: L1 moves the selected system up.
                        if (mPs3Xmb && ps3TopScreenKind() == GS_LIST) {
                            auto& its = mPs3Stack.back().items; int sel = mPs3Stack.back().sel;
                            if (sel >= 0 && sel < (int)its.size() && its[sel].kind == PS3_GS_SYSTEM_ROW)
                                gsReorderSystem(its[sel].a, -1);
                            break;
                        }
                        // Shut any open Settings sub-screen before leaving XMB
                        // so its scan thread exits instead of churning in bg.
                        if (mMenuState == MENU_WIFI) closeWifiScreen();
                        else if (mMenuState == MENU_BT) closeBtScreen();
                        mXmbMode = !mXmbMode;
                        property_set("persist.gammaos.nano.xmb_mode",
                                     mXmbMode ? "1" : "0");
                        if (!mXmbMode) {
                            mMenuState = MENU_MAIN;
                            mSearchActive = false;
                            mOskActive = false;
                        }
                        mDisplayDirty = true;
                        ALOGD("XMB Mode: %s", mXmbMode ? "ON" : "OFF");
                        break;
                    case BTN_TR: case KEY_R:
                        if (mOskActive) {
                            oskToggleSym();   // R1 toggles ABC <-> SYM inside the OSK
                            break;
                        }
                        // Game Systems list: R1 moves the selected system down.
                        if (mPs3Xmb && ps3TopScreenKind() == GS_LIST) {
                            auto& its = mPs3Stack.back().items; int sel = mPs3Stack.back().sel;
                            if (sel >= 0 && sel < (int)its.size() && its[sel].kind == PS3_GS_SYSTEM_ROW)
                                gsReorderSystem(its[sel].a, +1);
                            break;
                        }
                        mQuickResumeEnabled = !mQuickResumeEnabled;
                        property_set("persist.gammaos.nano.quick_resume",
                                     mQuickResumeEnabled ? "1" : "0");
                        mDisplayDirty = true;
                        ALOGD("Quick Resume: %s", mQuickResumeEnabled ? "ON" : "OFF");
                        break;
                    default: break;
                    }
                }
            }
            if (ev.type == EV_ABS) {
                if (ev.code == ABS_HAT0X) {
                    // HAT axes only emit a release as value==0. We don't
                    // know from just the axis whether Left or Right was
                    // held, so pass NavDir::None to release whichever is
                    // currently active.
                    if (ev.value < 0)      navPress(NavDir::Left);
                    else if (ev.value > 0) navPress(NavDir::Right);
                    else                   navRelease(NavDir::None);
                } else if (ev.code == ABS_HAT0Y) {
                    if (ev.value < 0)      navPress(NavDir::Up);
                    else if (ev.value > 0) navPress(NavDir::Down);
                    else                   navRelease(NavDir::None);
                } else if (ev.code == ABS_X) {
                    // Left stick X: horizontal navigation
                    int threshold = 29490; // 90% of 32767
                    if (ev.value < -threshold && !mStickXTriggered) {
                        navPress(NavDir::Left);
                        mStickXTriggered = true;
                    } else if (ev.value > threshold && !mStickXTriggered) {
                        navPress(NavDir::Right);
                        mStickXTriggered = true;
                    } else if (ev.value > -threshold && ev.value < threshold) {
                        if (mStickXTriggered) navRelease(NavDir::None);
                        mStickXTriggered = false;
                    }
                } else if (ev.code == ABS_Y) {
                    // Left stick Y: signed range -32768..32767, 90% deadzone
                    // Threshold naturally filters touchscreen ABS_Y (max ~960)
                    int threshold = 29490; // 90% of 32767
                    if (ev.value < -threshold && !mStickYTriggered) {
                        navPress(NavDir::Up);
                        mStickYTriggered = true;
                    } else if (ev.value > threshold && !mStickYTriggered) {
                        navPress(NavDir::Down);
                        mStickYTriggered = true;
                    } else if (ev.value > -threshold && ev.value < threshold) {
                        if (mStickYTriggered) navRelease(NavDir::None);
                        mStickYTriggered = false;
                    }
                }
            }
        }
    }
    // Fire accelerating repeats while a direction remains held. Must run
    // every frame, not only when events arrive, because held axes stop
    // emitting events once settled.
    tickNavRepeat();
}

} // namespace android
