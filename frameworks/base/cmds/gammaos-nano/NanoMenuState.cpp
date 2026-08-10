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

// Main-menu state: item list build, display-string rebuild, and loaders for
// the Recently Played / Applications submenus. Extracted from NanoMenu.cpp
// with no behavior changes.

#define LOG_TAG "GammaOSNano"

#include <algorithm>
#include <string>
#include <vector>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>

#include <android-base/properties.h>
#include <cutils/properties.h>
#include <utils/Log.h>

#include "NanoMenu.h"
#include "NanoMenuShaders.h"
#include "NanoMenuStrings.h"

namespace android {

// ---------------------------------------------------------------------------
// Minimal JSON string value extractor for playlist parsing
// ---------------------------------------------------------------------------

static std::string extractJsonString(const std::string& json, const std::string& key,
                                     size_t searchStart, size_t searchEnd) {
    std::string needle = "\"" + key + "\"";
    size_t keyPos = json.find(needle, searchStart);
    if (keyPos == std::string::npos || keyPos > searchEnd) return "";
    size_t colonPos = json.find(':', keyPos + needle.size());
    if (colonPos == std::string::npos || colonPos > searchEnd) return "";
    size_t qStart = json.find('"', colonPos + 1);
    if (qStart == std::string::npos || qStart > searchEnd) return "";
    size_t qEnd = qStart + 1;
    while (qEnd < json.size() && qEnd <= searchEnd) {
        if (json[qEnd] == '"' && json[qEnd - 1] != '\\') break;
        qEnd++;
    }
    if (qEnd > json.size()) return "";
    return json.substr(qStart + 1, qEnd - qStart - 1);
}

// ---------------------------------------------------------------------------
// Menu build + display string rebuild
// ---------------------------------------------------------------------------

void NanoMenu::buildMenu() {
    // Reset launch_app to default so RetroArch launches work after returning
    // from a non-RetroArch app launched via the Applications submenu.
    //
    // CRITICAL: only the HOME nano (the launcher) may touch launch_app. The
    // resident OVERLAY process also calls buildMenu() at boot_completed, and if
    // it set launch_app here it would land right as RootWindowContainer makes its
    // minimal-boot home-launch decision -> RetroArch auto-launches on every boot.
    // The overlay is never the launcher, so it must leave launch_app alone.
    if (!mOverlayMode) {
        property_set("sys.gammaos.nano.launch_app", "com.retroarch.aarch64");
    }

    mMenuItems.clear();
    mMenuItems.push_back({tr(STR_MENU_RETROARCH)});
    mMenuItems.push_back({tr(STR_MENU_RECENTLY_PLAYED)});
    mMenuItems.push_back({tr(STR_MENU_APPLICATIONS)});
    mMenuItems.push_back({tr(STR_MENU_BOOT_ANDROID)});
    mMenuItems.push_back({tr(STR_MENU_RECOVERY)});
    mMenuItems.push_back({tr(STR_MENU_SAFE_MODE)});
    mMenuItems.push_back({tr(STR_MENU_REBOOT)});
    mMenuItems.push_back({tr(STR_MENU_POWER_OFF)});
    mSelectedIndex = 0;
    mMenuState = MENU_MAIN;
    mRecentSelectedIndex = 0;
    mAppSelectedIndex = 0;

    // If returning from a game launched via Recently Played, go straight back
    char returnRecent[PROPERTY_VALUE_MAX] = {};
    property_get("sys.gammaos.nano.return_recent", returnRecent, "0");
    if (!strcmp(returnRecent, "1")) {
        property_set("sys.gammaos.nano.return_recent", "0");
        mSelectedIndex = 1; // "Recently Played"
        // Try loading playlist; if storage isn't ready yet, the render loop
        // will keep polling and auto-load when available
        if (mStorageReady) {
            loadRecentPlaylist();
        }
        mMenuState = MENU_RECENT;
        mRecentSelectedIndex = 0;
        ALOGD("NanoMenu: returning to Recently Played after game exit");
    }
    // If returning from an app launched via Applications, go straight back
    char returnApps[PROPERTY_VALUE_MAX] = {};
    property_get("sys.gammaos.nano.return_apps", returnApps, "0");
    if (!strcmp(returnApps, "1")) {
        property_set("sys.gammaos.nano.return_apps", "0");
        mSelectedIndex = 2; // "Applications"
        if (mStorageReady) {
            loadInstalledApps();
        }
        mMenuState = MENU_APPS;
        mAppSelectedIndex = 0;
        ALOGD("NanoMenu: returning to Applications after app exit");
    }
    mDisplayDirty = true;
}

void NanoMenu::rebuildDisplayItems() {
    mDisplayItems.clear();
    if (mMenuState == MENU_RECENT) {
        mTitle = "Recently Played";
        for (const auto& entry : mRecentEntries) {
            std::string item = entry.label;
            if (item.empty()) {
                size_t slash = entry.romPath.rfind('/');
                item = (slash != std::string::npos)
                    ? entry.romPath.substr(slash + 1) : entry.romPath;
            }
            // Strip file extension
            size_t dot = item.rfind('.');
            if (dot != std::string::npos && dot > 0) item = item.substr(0, dot);
            // Append system + core
            if (!entry.coreName.empty() && entry.coreName != "DETECT") {
                std::string coreName = entry.coreName;
                size_t pStart = coreName.rfind('(');
                size_t pEnd = coreName.rfind(')');
                if (pStart != std::string::npos && pEnd != std::string::npos
                    && pEnd > pStart) {
                    coreName = coreName.substr(pStart + 1, pEnd - pStart - 1);
                }
                std::string sysName;
                if (!entry.dbName.empty()) {
                    sysName = entry.dbName;
                    size_t pipe = sysName.find('|');
                    if (pipe != std::string::npos) sysName = sysName.substr(0, pipe);
                } else if (pStart != std::string::npos && pStart > 0) {
                    sysName = entry.coreName.substr(0, pStart);
                    while (!sysName.empty() && sysName.back() == ' ')
                        sysName.pop_back();
                }
                if (!sysName.empty()) {
                    item += "  [" + sysName + " - " + coreName + "]";
                } else {
                    item += "  [" + coreName + "]";
                }
            }
            mDisplayItems.push_back(item);
        }
        mDisplayItems.push_back(tr(STR_BACK));
        if (!mStorageReady) {
            mSubtitle = tr(STR_PLEASE_WAIT_STORAGE);
        } else if (mRecentEntries.empty()) {
            mSubtitle = tr(STR_NO_RECENT_GAMES);
        } else {
            char buf[64];
            snprintf(buf, sizeof(buf), "%zu game%s", mRecentEntries.size(),
                     mRecentEntries.size() == 1 ? "" : "s");
            mSubtitle = buf;
        }
        mFooter = tr(STR_FOOTER_RECENT);
    } else if (mMenuState == MENU_APPS) {
        mTitle = tr(STR_MENU_APPLICATIONS);
        for (const auto& app : mAppEntries) {
            mDisplayItems.push_back(app.label);
        }
        mDisplayItems.push_back(tr(STR_BACK));
        if (!mStorageReady) {
            mSubtitle = tr(STR_PLEASE_WAIT_STORAGE);
        } else if (mAppEntries.empty()) {
            mSubtitle = tr(STR_NO_INSTALLED_APPS);
        } else {
            char buf[64];
            snprintf(buf, sizeof(buf), "%zu app%s", mAppEntries.size(),
                     mAppEntries.size() == 1 ? "" : "s");
            mSubtitle = buf;
        }
        mFooter = tr(STR_FOOTER_APPS);
    } else {
        mTitle = tr(STR_APP_TITLE);
        for (const auto& item : mMenuItems) {
            mDisplayItems.push_back(item.label);
        }
        mSubtitle = tr(STR_APP_VERSION);
        char buf[200];
        snprintf(buf, sizeof(buf),
                 "DPAD: Nav | A: Select | X: FX [%s] | Y: FX | L1: XMB | R1: QR",
                 kEffectNames[mCurrentEffect]);
        mFooter = buf;
    }
    mDisplayDirty = false;
}

// ---------------------------------------------------------------------------
// Submenu loaders
// ---------------------------------------------------------------------------

void NanoMenu::loadRecentPlaylist() {
    mRecentEntries.clear();
    mRecentLoaded = false;
    // Read from raw filesystem path (bypasses FUSE, works before FUSE mount).
    // /data/media/0 is the backing store for emulated storage — accessible
    // after CE unlock even before FUSE is mounted.
    const char* path = "/data/media/0/RetroArch/playlists/builtin/content_history.lpl";
    int fd = open(path, O_RDONLY);
    if (fd < 0) {
        ALOGW("NanoMenu: cannot open %s: %s", path, strerror(errno));
        // mRecentLoaded stays false — UI will show "storage not ready" message
        return;
    }
    mRecentLoaded = true;
    struct stat st;
    if (fstat(fd, &st) < 0 || st.st_size <= 0 || st.st_size > 64 * 1024 * 1024) {
        close(fd);
        return;
    }
    std::string content(st.st_size, '\0');
    ssize_t bytesRead = read(fd, &content[0], st.st_size);
    close(fd);
    if (bytesRead <= 0) return;
    content.resize(bytesRead);

    // Find "items" array
    size_t itemsPos = content.find("\"items\"");
    if (itemsPos == std::string::npos) return;
    size_t arrayStart = content.find('[', itemsPos);
    if (arrayStart == std::string::npos) return;

    // Find matching ]
    size_t arrayEnd = std::string::npos;
    int depth = 0;
    bool inStr = false;
    for (size_t i = arrayStart; i < content.size(); i++) {
        char c = content[i];
        if (c == '"' && (i == 0 || content[i - 1] != '\\')) inStr = !inStr;
        if (!inStr) {
            if (c == '[') depth++;
            if (c == ']') { depth--; if (depth == 0) { arrayEnd = i; break; } }
        }
    }
    if (arrayEnd == std::string::npos) return;

    // Parse each item object
    size_t pos = arrayStart + 1;
    static const int MAX_RECENT = 8;
    while (pos < arrayEnd && (int)mRecentEntries.size() < MAX_RECENT) {
        size_t objStart = content.find('{', pos);
        if (objStart == std::string::npos || objStart > arrayEnd) break;
        // Find matching }
        int objDepth = 0;
        bool objInStr = false;
        size_t objEnd = std::string::npos;
        for (size_t i = objStart; i <= arrayEnd; i++) {
            char c = content[i];
            if (c == '"' && (i == 0 || content[i - 1] != '\\')) objInStr = !objInStr;
            if (!objInStr) {
                if (c == '{') objDepth++;
                if (c == '}') { objDepth--; if (objDepth == 0) { objEnd = i; break; } }
            }
        }
        if (objEnd == std::string::npos) break;

        RecentEntry entry;
        entry.label = extractJsonString(content, "label", objStart, objEnd);
        entry.romPath = extractJsonString(content, "path", objStart, objEnd);
        entry.corePath = extractJsonString(content, "core_path", objStart, objEnd);
        entry.coreName = extractJsonString(content, "core_name", objStart, objEnd);
        entry.dbName = extractJsonString(content, "db_name", objStart, objEnd);

        if (!entry.romPath.empty() && entry.romPath != "DETECT") {
            if (entry.label.empty()) {
                // Use filename as label
                size_t slash = entry.romPath.rfind('/');
                entry.label = (slash != std::string::npos)
                    ? entry.romPath.substr(slash + 1) : entry.romPath;
            }
            mRecentEntries.push_back(entry);
        }
        pos = objEnd + 1;
    }
    ALOGD("NanoMenu: loaded %zu recent entries from %s", mRecentEntries.size(), path);
}

void NanoMenu::loadInstalledApps() {
    mAppEntries.clear();
    mAppsLoaded = false;

    // "Show all apps" mode: list every launchable app (like the normal launcher),
    // including pre-installed system apps such as the Camera, sourced from the
    // framework's launcher-activity cache (nano_activities.txt). The default mode
    // below only lists user-installed apps. Toggled by the Applications X-menu row
    // (persist.gammaos.nano.apps.showall). Dedup by package so each app is one tile.
    if (property_get_bool("persist.gammaos.nano.apps.showall", false)) {
        ensureActivityList();   // fills mActivityEntries from nano_activities.txt
        for (const auto& a : mActivityEntries) {
            bool dup = false;
            for (const auto& e : mAppEntries)
                if (e.packageName == a.packageName) { dup = true; break; }
            if (dup) continue;
            AppEntry app;
            app.packageName = a.packageName;
            app.label = a.label;
            mAppEntries.push_back(std::move(app));
        }
        mAppsLoaded = true;
        return;
    }

    // Parse /data/system/packages.list — the authoritative package database.
    // Format: <pkg> <uid> <debug> <dataDir> <seinfo> <gids> <prof> <ver> <hasCode> <installer>
    // The last field is the installer marker: "@system"/"@product" for preinstalled
    // apps, "@null" for user apps with no installer, or the installer's package name for
    // store-installed apps. User-installed apps are everything that is not "@system" or
    // "@product" (see the filter below).
    const char* path = "/data/system/packages.list";
    int fd = open(path, O_RDONLY);
    if (fd < 0) {
        ALOGW("NanoMenu: cannot open %s: %s", path, strerror(errno));
        return;
    }
    mAppsLoaded = true;
    struct stat st;
    if (fstat(fd, &st) < 0 || st.st_size <= 0 || st.st_size > 64 * 1024 * 1024) {
        close(fd);
        return;
    }
    std::string content(st.st_size, '\0');
    ssize_t bytesRead = read(fd, &content[0], st.st_size);
    close(fd);
    if (bytesRead <= 0) return;
    content.resize(bytesRead);

    // Parse line by line
    size_t pos = 0;
    while (pos < content.size()) {
        size_t eol = content.find('\n', pos);
        if (eol == std::string::npos) eol = content.size();
        std::string line = content.substr(pos, eol - pos);
        pos = eol + 1;
        if (line.empty()) continue;

        // Extract package name (first field, space-delimited)
        size_t space = line.find(' ');
        if (space == std::string::npos) continue;
        std::string pkgName = line.substr(0, space);

        // GammaOS: always surface the system Files app (DocumentsUI) so it is reachable with a
        // controller from Applications, even though it is a system / com.android.* package that
        // the filters below would otherwise skip. Its launcher is force-enabled in
        // DocumentsUI PreBootReceiver so the launch handshake resolves.
        const bool forceInclude = (pkgName == "com.android.documentsui");

        if (forceInclude) {
            // AOSP disables the Files launcher on S+, so selecting it in Applications would not
            // resolve the LAUNCHER intent. Enable it once (backgrounded so it never blocks the
            // menu); the enabled state persists, making this effectively a one-time fix.
            static bool sEnsuredFilesLauncher = false;
            if (!sEnsuredFilesLauncher) {
                sEnsuredFilesLauncher = true;
                system("pm enable com.android.documentsui/com.android.documentsui.LauncherActivity "
                       ">/dev/null 2>&1 &");
            }
        }

        if (!forceInclude) {
            // Include user-installed apps and exclude only preinstalled-partition apps.
            // The last whitespace field of packages.list is the installer marker written
            // by PackageManager (Settings.writePackageListLPr): "@system" if the app is a
            // system app, else "@product" if it is a /product app, else the installer's
            // package name when one is recorded, else "@null" only when there is no
            // installer of record. So:
            //   "@system" / "@product" -> preinstalled OS app (hide),
            //   "@null"                -> user app with no installer (adb / plain
            //                             `pm install` / sideload / restore) -> show,
            //   "<pkg.name>"           -> user app installed by a store that records
            //                             itself as installer (Aurora, Play, F-Droid,
            //                             some MiXplorer sessions) -> show.
            // The old check kept ONLY "@null", so any app installed through a store was
            // wrongly hidden from the default list (e.g. Netflix via Aurora), yet still
            // showed under "Show All". Exclude the two partition markers instead so every
            // genuinely user-installed app appears, however it was installed. Both markers
            // are needed: isSystem() (FLAG_SYSTEM) and isProduct() (PRIVATE_FLAG_PRODUCT)
            // are independent, so a /product app that lacks FLAG_SYSTEM is written as
            // "@product", not "@system".
            size_t lastSpace = line.rfind(' ');
            std::string installer =
                    (lastSpace == std::string::npos) ? std::string()
                                                     : line.substr(lastSpace + 1);
            while (!installer.empty()
                   && (installer.back() == '\r' || installer.back() == '\n'
                       || installer.back() == ' ' || installer.back() == '\t')) {
                installer.pop_back();
            }
            if (installer == "@system" || installer == "@product") continue;

            // Skip system-like and internal packages
            if (pkgName.find("com.android.") == 0) continue;
            if (pkgName.find("org.lineageos.") == 0) continue;
            if (pkgName.find("com.gammaos.") == 0) continue;
            if (pkgName.find("com.topjohnwu.") == 0) continue;
        }

        // Build a human-readable label from the package name:
        // take the last segment and capitalize first letter
        std::string label = pkgName;
        size_t lastDot = pkgName.rfind('.');
        if (lastDot != std::string::npos && lastDot + 1 < pkgName.size()) {
            label = pkgName.substr(lastDot + 1);
            if (!label.empty() && label[0] >= 'a' && label[0] <= 'z') {
                label[0] -= 32; // capitalize
            }
        }
        // Replace underscores with spaces for readability
        for (char& c : label) {
            if (c == '_') c = ' ';
        }
        AppEntry app;
        app.packageName = pkgName;
        app.label = label;
        mAppEntries.push_back(app);
    }
    // Resolve actual app labels from the cache written by SystemServer.
    // Format: "package.name|Human Label\n"
    {
        const char* labelPath = "/data/system/nano_app_labels.txt";
        int labelFd = open(labelPath, O_RDONLY);
        if (labelFd >= 0) {
            struct stat labelSt;
            if (fstat(labelFd, &labelSt) == 0 && labelSt.st_size > 0
                    && labelSt.st_size < 64 * 1024 * 1024) {
                std::string labelContent(labelSt.st_size, '\0');
                ssize_t labelRead = read(labelFd, &labelContent[0], labelSt.st_size);
                if (labelRead > 0) {
                    labelContent.resize(labelRead);
                    size_t lpos = 0;
                    while (lpos < labelContent.size()) {
                        size_t leol = labelContent.find('\n', lpos);
                        if (leol == std::string::npos) leol = labelContent.size();
                        std::string lline = labelContent.substr(lpos, leol - lpos);
                        lpos = leol + 1;
                        size_t lpipe = lline.find('|');
                        if (lpipe == std::string::npos) continue;
                        std::string lpkg = lline.substr(0, lpipe);
                        std::string llabel = lline.substr(lpipe + 1);
                        if (llabel.empty()) continue;
                        for (auto& app : mAppEntries) {
                            if (app.packageName == lpkg) {
                                app.label = llabel;
                                break;
                            }
                        }
                    }
                }
            }
            close(labelFd);
        } else {
            ALOGW("NanoMenu: label cache not available yet: %s", strerror(errno));
        }
    }

    // GammaOS: the system Files app keeps a friendly label regardless of the label cache.
    for (auto& app : mAppEntries) {
        if (app.packageName == "com.android.documentsui") {
            app.label = "Files";
        }
    }

    // Sort alphabetically by label
    std::sort(mAppEntries.begin(), mAppEntries.end(),
              [](const AppEntry& a, const AppEntry& b) {
                  return a.label < b.label;
              });
    ALOGD("NanoMenu: loaded %zu installed apps from packages.list", mAppEntries.size());
}

// Load the installed-browser list the framework curated (SystemServer.writeNanoBrowserCache:
// every ACTION_VIEW http/https handler). Format: "pkg|Label|pkg/Activity\n". Unlike
// loadInstalledApps this does NOT filter com.android.* / com.gammaos.* / system apps, because
// real browsers (Chrome, the shipped GammaBrowser) live under those prefixes. The component is
// the flattened ACTION_VIEW activity so launchUrl can build a resolvable VIEW intent for ANY
// browser. If the file is absent (first boot, before SystemServer writes it) seed the single
// shipped GammaBrowser entry so the picker + launch always have a valid fallback.
void NanoMenu::loadInstalledBrowsers() {
    mBrowserEntries.clear();
    mBrowsersLoaded = true;
    const char* path = "/data/system/nano_browsers.txt";
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd >= 0) {
        struct stat st;
        if (fstat(fd, &st) == 0 && st.st_size > 0 && st.st_size < 64 * 1024 * 1024) {
            std::string content(st.st_size, '\0');
            ssize_t n = read(fd, &content[0], st.st_size);
            if (n > 0) {
                content.resize(n);
                size_t pos = 0;
                while (pos < content.size()) {
                    size_t eol = content.find('\n', pos);
                    if (eol == std::string::npos) eol = content.size();
                    std::string line = content.substr(pos, eol - pos);
                    pos = eol + 1;
                    if (line.empty()) continue;
                    size_t p1 = line.find('|');
                    if (p1 == std::string::npos) continue;
                    size_t p2 = line.find('|', p1 + 1);
                    BrowserEntry b;
                    b.packageName = line.substr(0, p1);
                    if (p2 == std::string::npos) {
                        b.label = line.substr(p1 + 1);
                    } else {
                        b.label = line.substr(p1 + 1, p2 - p1 - 1);
                        b.component = line.substr(p2 + 1);
                    }
                    if (b.packageName.empty() || b.label.empty()) continue;
                    mBrowserEntries.push_back(std::move(b));
                }
            }
        }
        close(fd);
    }
    if (mBrowserEntries.empty()) {
        BrowserEntry b;
        b.packageName = "com.gammaos.browser";
        b.label = "GammaBrowser";
        b.component = "com.gammaos.browser/.MainActivity";
        mBrowserEntries.push_back(std::move(b));
    }
    ALOGD("NanoMenu: loaded %zu browsers from nano_browsers.txt", mBrowserEntries.size());
}

// Reload the browser list only when the framework bumps browsers_generation (rare: boot +
// package add/remove), so the picker + launchUrl stay current without re-reading every call.
void NanoMenu::ensureBrowserList() {
    int gen = property_get_int32("sys.gammaos.nano.browsers_generation", 0);
    if (!mBrowsersLoaded || gen != mBrowsersGen) {
        mBrowsersGen = gen;
        loadInstalledBrowsers();
    }
}

// Launchable activities for the gamepad "Launch Activity" remap-action picker.
// Parsed from /data/system/nano_activities.txt ("pkg|Label|pkg/Activity" per line),
// written by SystemServer.writeNanoActivityCache. One row per activity (not deduped),
// so the user can target a specific component; the daemon launches it via "-n".
void NanoMenu::loadInstalledActivities() {
    mActivityEntries.clear();
    mActivitiesLoaded = true;
    const char* path = "/data/system/nano_activities.txt";
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd >= 0) {
        struct stat st;
        if (fstat(fd, &st) == 0 && st.st_size > 0 && st.st_size < 64 * 1024 * 1024) {
            std::string content(st.st_size, '\0');
            ssize_t n = read(fd, &content[0], st.st_size);
            if (n > 0) {
                content.resize(n);
                size_t pos = 0;
                while (pos < content.size()) {
                    size_t eol = content.find('\n', pos);
                    if (eol == std::string::npos) eol = content.size();
                    std::string line = content.substr(pos, eol - pos);
                    pos = eol + 1;
                    if (line.empty()) continue;
                    size_t p1 = line.find('|');
                    if (p1 == std::string::npos) continue;
                    size_t p2 = line.find('|', p1 + 1);
                    if (p2 == std::string::npos) continue;
                    ActivityEntry a;
                    a.packageName = line.substr(0, p1);
                    a.label = line.substr(p1 + 1, p2 - p1 - 1);
                    a.component = line.substr(p2 + 1);
                    if (a.packageName.empty() || a.component.empty()) continue;
                    if (a.label.empty()) a.label = a.packageName;
                    mActivityEntries.push_back(std::move(a));
                }
            }
        }
        close(fd);
    }
    ALOGD("NanoMenu: loaded %zu activities from nano_activities.txt", mActivityEntries.size());
}

// Reload the activity list only when the framework bumps activities_generation
// (boot + package add/remove), so the picker stays current without re-reading.
void NanoMenu::ensureActivityList() {
    int gen = property_get_int32("sys.gammaos.nano.activities_generation", 0);
    if (!mActivitiesLoaded || gen != mActivitiesGen) {
        mActivitiesGen = gen;
        loadInstalledActivities();
    }
}

// Parsed from /data/system/nano_pkg_activities.txt ("pkg|ActivityLabel|pkg/Activity" per line),
// written by SystemServer.writeNanoPackageActivities: EVERY activity of every launchable app (not
// just launchers), grouped by package in label order. Feeds the slide "Launch Target" app ->
// activity picker so the user can target any activity. Rides activities_generation.
void NanoMenu::loadPackageActivities() {
    mPkgActivityEntries.clear();
    mPkgActivitiesLoaded = true;
    const char* path = "/data/system/nano_pkg_activities.txt";
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd >= 0) {
        struct stat st;
        if (fstat(fd, &st) == 0 && st.st_size > 0 && st.st_size < 64 * 1024 * 1024) {
            std::string content(st.st_size, '\0');
            ssize_t n = read(fd, &content[0], st.st_size);
            if (n > 0) {
                content.resize(n);
                size_t pos = 0;
                while (pos < content.size()) {
                    size_t eol = content.find('\n', pos);
                    if (eol == std::string::npos) eol = content.size();
                    std::string line = content.substr(pos, eol - pos);
                    pos = eol + 1;
                    if (line.empty()) continue;
                    size_t p1 = line.find('|');
                    if (p1 == std::string::npos) continue;
                    size_t p2 = line.find('|', p1 + 1);
                    if (p2 == std::string::npos) continue;
                    ActivityEntry a;
                    a.packageName = line.substr(0, p1);
                    a.label = line.substr(p1 + 1, p2 - p1 - 1);
                    a.component = line.substr(p2 + 1);
                    if (a.packageName.empty() || a.component.empty()) continue;
                    if (a.label.empty()) a.label = a.component;
                    mPkgActivityEntries.push_back(std::move(a));
                }
            }
        }
        close(fd);
    }
    ALOGD("NanoMenu: loaded %zu package activities from nano_pkg_activities.txt", mPkgActivityEntries.size());
}

void NanoMenu::ensurePackageActivities() {
    int gen = property_get_int32("sys.gammaos.nano.activities_generation", 0);
    if (!mPkgActivitiesLoaded || gen != mPkgActivitiesGen) {
        mPkgActivitiesGen = gen;
        loadPackageActivities();
    }
}

// Human label for a browser package (the "Default Browser" row's value column). Falls back
// to the bare package name, then "GammaBrowser" for the shipped default.
std::string NanoMenu::browserLabelForPkg(const std::string& pkg) {
    ensureBrowserList();
    for (const auto& b : mBrowserEntries)
        if (b.packageName == pkg) return b.label;
    if (pkg == "com.gammaos.browser" || pkg.empty()) return "GammaBrowser";
    return pkg;
}

// Read the on-demand app Information file the framework wrote for our request. The
// first line must be "req|<nonce>" matching what we asked for (so a stale reply from
// an earlier request is ignored); everything after the first newline is the body.
bool NanoMenu::readNanoAppInfo(const std::string& nonce, std::string& bodyOut) {
    int fd = open("/data/system/nano_app_info.txt", O_RDONLY | O_CLOEXEC);
    if (fd < 0) return false;
    struct stat st;
    if (fstat(fd, &st) < 0 || st.st_size <= 0 || st.st_size > 64 * 1024 * 1024) { close(fd); return false; }
    std::string content(st.st_size, '\0');
    ssize_t n = read(fd, &content[0], st.st_size);
    close(fd);
    if (n <= 0) return false;
    content.resize(n);
    size_t nl = content.find('\n');
    if (nl == std::string::npos) return false;
    if (content.compare(0, nl, std::string("req|") + nonce) != 0) return false;  // stale reply
    bodyOut = content.substr(nl + 1);
    return true;
}

} // namespace android
