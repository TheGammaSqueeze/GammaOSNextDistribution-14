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

#ifndef GAMMAOS_NANO_MENU_UTILS_H
#define GAMMAOS_NANO_MENU_UTILS_H

#include <string>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>

#include <android-base/properties.h>
#include <cutils/properties.h>

namespace android {

// The OSK keyboard layout tables now live in the generated NanoOskLayouts.h
// (kOskKb[] / kOskPopups[]) and the runtime is NanoOsk.cpp. The old fixed
// 5x10 ASCII grid that used to live here has been removed.

// Case-insensitive substring search
inline bool containsInsensitive(const std::string& haystack, const std::string& needle) {
    if (needle.empty()) return true;
    if (haystack.size() < needle.size()) return false;
    for (size_t i = 0; i <= haystack.size() - needle.size(); i++) {
        bool match = true;
        for (size_t j = 0; j < needle.size(); j++) {
            char a = haystack[i + j], b = needle[j];
            if (a >= 'A' && a <= 'Z') a += 32;
            if (b >= 'A' && b <= 'Z') b += 32;
            if (a != b) { match = false; break; }
        }
        if (match) return true;
    }
    return false;
}

// GammaOS: path props routinely exceed PROP_VALUE_MAX (92 bytes) when
// ROMs live on external SD at /storage/<UUID>/..., so the prop set
// silently fails. Mirror the path into a plain text file alongside so
// readers can fall back when the prop is empty. Empty values unlink
// the file so a stale path can never resurrect in the fallback read.
inline void writePathFile(const char* path, const std::string& value) {
    if (value.empty()) {
        unlink(path);
        return;
    }
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0666);
    if (fd < 0) {
        // File may be owned by a different user (e.g. root/system from
        // nano_cache.sh). Remove and re-create so the calling process
        // owns the new file.
        unlink(path);
        fd = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0666);
    }
    if (fd >= 0) {
        write(fd, value.c_str(), value.size());
        close(fd);
        chmod(path, 0666);
    }
}

inline std::string readPathFile(const char* path) {
    int fd = open(path, O_RDONLY);
    if (fd < 0) return std::string();
    char buf[4096];
    ssize_t n = read(fd, buf, sizeof(buf));
    close(fd);
    if (n <= 0) return std::string();
    std::string s(buf, (size_t)n);
    // Strip trailing whitespace/newlines to tolerate shell-written files.
    while (!s.empty() && (s.back() == '\n' || s.back() == '\r' ||
                          s.back() == ' '  || s.back() == '\t')) {
        s.pop_back();
    }
    return s;
}

inline void setLaunchRomPath(const std::string& romPath) {
    android::base::SetProperty("sys.gammaos.nano.launch_rom", romPath);
    writePathFile("/data/system/nano_launch_rom.txt", romPath);
}

inline void setQrRomPath(const std::string& romPath) {
    android::base::SetProperty("persist.gammaos.nano.qr_rom", romPath);
    writePathFile("/data/system/nano_qr_rom.txt", romPath);
}

inline std::string getQrRomPath() {
    // Prefer the file over the persist prop. External SD paths
    // (e.g. /storage/<UUID>/nds/<long name>.nds) routinely exceed
    // PROP_VALUE_MAX (92 bytes) and the prop write silently fails,
    // leaving a stale short path from a previous session. The file
    // is always written regardless of length, so it's the source of
    // truth. Fall back to the prop only when the file is missing
    // (e.g. first boot, or the file was manually deleted).
    std::string p = readPathFile("/data/system/nano_qr_rom.txt");
    if (!p.empty()) return p;
    return android::base::GetProperty(
            "persist.gammaos.nano.qr_rom", "");
}

// Drastic nano ROM path -- separate from QR ROM path so drastic nano
// and QR drastic can coexist without overwriting each other's state.
// The getter lives in main.cpp (separate compilation unit) where it's
// used for boot-time ROM discovery.
inline void setDrasticNanoRomPath(const std::string& romPath) {
    writePathFile("/data/system/nano_drastic_nano_rom.txt", romPath);
}

// GammaOS: returns true when ALL storage RetroArch needs is mounted.
//
// Two independent gates:
//
// 1. Emulated FUSE (/storage/emulated/0/): RetroArch always needs this
//    for config, saves, and its data dir, regardless of where the ROM
//    lives. On cold boot, vold's FUSE mount is deferred until after
//    user-0 CE storage is unlocked. If RetroArch launches before FUSE
//    is up, it gets ENOENT on its data dir and corrupts its config
//    paths (e.g. save_directory becomes garbage like "hg{/saves").
//
// 2. External SD raw mount (/mnt/media_rw/<UUID>/): only needed when
//    the ROM path is on external storage (/storage/<UUID>/...). vold
//    defers external SD scanning until after the secure keyguard step,
//    so this can take 3-5s after NanoMenu starts.
inline bool isQrRomStorageReady() {
    // Gate 1: emulated FUSE must be ACTUALLY MOUNTED, not just the
    // tmpfs placeholder directory. /storage/emulated/0 exists as an
    // empty tmpfs from very early boot (created by vold), so a plain
    // stat() check passes prematurely and the handoff fires while
    // FUSE is still mounting -- drastic then can't resolve content://
    // URIs and falls back to its home menu instead of loading the
    // game.
    //
    // Probe for /storage/emulated/0/Android: this directory is
    // populated only after FUSE mounts on top of the placeholder
    // and the user's /data/media/0 is visible through it.
    struct stat st;
    if (stat("/storage/emulated/0/Android", &st) != 0
            || !S_ISDIR(st.st_mode))
        return false;

    // Gate 2: if ROM is on external SD, its raw vold mount must exist.
    std::string qrRom = getQrRomPath();
    if (qrRom.empty()) return true;
    if (qrRom.find("/storage/") != 0) return true;
    std::string rest = qrRom.substr(9); // skip "/storage/"
    size_t slash = rest.find('/');
    if (slash == std::string::npos) return true;
    std::string uuid = rest.substr(0, slash);
    if (uuid == "emulated" || uuid == "self") return true;
    std::string rawDir = "/mnt/media_rw/" + uuid;
    if (stat(rawDir.c_str(), &st) != 0) return false;
    return S_ISDIR(st.st_mode);
}

} // namespace android

#endif // GAMMAOS_NANO_MENU_UTILS_H
