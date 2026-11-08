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

#ifndef GAMMAOS_NANO_BACKLIGHT_H
#define GAMMAOS_NANO_BACKLIGHT_H

// Generic sysfs backlight control shared by gammaos-nano, drastic-nano and
// azahar-nano (header-only so the other binaries pick it up through their
// existing include_dirs without an Android.bp change). NanoBrightness.h
// builds the shared brightness policy and apply path on top of it.
//
// Devices name their backlight nodes inconsistently: panel0-backlight
// (most single-panel boxes), backlight + backlight1 (RG DS dual panel),
// lcd-backlight under /sys/class/leds. Hardcoding one path silently
// no-ops on the others - on the RG DS the old panel0-backlight writes in
// the sleep path never touched either panel. Enumerate whatever exists
// once, cache the per-node max, and drive every node together so dual
// panel devices blank and relight both screens.

#include <dirent.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <string>
#include <vector>

namespace android {
namespace nanobl {

struct BacklightNode {
    std::string brightnessPath;
    int maxBrightness;
};

inline int nanoBacklightReadInt(const char* path, int def) {
    int fd = open(path, O_RDONLY);
    if (fd < 0) return def;
    char buf[32] = {};
    ssize_t n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 0) return def;
    int v = atoi(buf);
    return v > 0 ? v : def;
}

// Enumerated once and cached; backlight class devices do not come and go.
// Never destroyed: the nano-bright thread (NanoBrightness.h) can still read it
// while static destructors run after a return from main().
inline const std::vector<BacklightNode>& nanoBacklightNodes() {
    static const std::vector<BacklightNode>* sNodes = new std::vector<BacklightNode>([] {
        std::vector<BacklightNode> nodes;
        DIR* dir = opendir("/sys/class/backlight");
        if (dir) {
            struct dirent* de;
            while ((de = readdir(dir)) != nullptr) {
                if (de->d_name[0] == '.') continue;
                char base[256];
                snprintf(base, sizeof(base), "/sys/class/backlight/%s",
                         de->d_name);
                char maxPath[300];
                snprintf(maxPath, sizeof(maxPath), "%s/max_brightness", base);
                BacklightNode n;
                n.brightnessPath = std::string(base) + "/brightness";
                n.maxBrightness = nanoBacklightReadInt(maxPath, 255);
                nodes.push_back(std::move(n));
            }
            closedir(dir);
        }
        // Some devices only expose an LED-class backlight.
        const char* led = "/sys/class/leds/lcd-backlight/brightness";
        if (access(led, W_OK) == 0) {
            BacklightNode n;
            n.brightnessPath = led;
            n.maxBrightness = nanoBacklightReadInt(
                    "/sys/class/leds/lcd-backlight/max_brightness", 255);
            nodes.push_back(std::move(n));
        }
        return nodes;
    }());
    return *sNodes;
}

// Drive every backlight node from an Android-range level (0-255), scaling
// against each node's own max. 0 means fully off; any non-zero level is
// clamped to at least 1 in node range so a low setting never blanks.
inline void nanoBacklightSet(int level255) {
    for (const auto& n : nanoBacklightNodes()) {
        int v = 0;
        if (level255 > 0) {
            v = level255 * n.maxBrightness / 255;
            if (v < 1) v = 1;
        }
        int fd = open(n.brightnessPath.c_str(), O_WRONLY);
        if (fd < 0) continue;
        char buf[32];
        int len = snprintf(buf, sizeof(buf), "%d", v);
        write(fd, buf, len);
        close(fd);
    }
}

// True when every backlight node holds what nanoBacklightSet(level255) writes
// (no readable node: true).
inline bool nanoBacklightMatches(int level255) {
    for (const auto& n : nanoBacklightNodes()) {
        int want = 0;
        if (level255 > 0) {
            want = level255 * n.maxBrightness / 255;
            if (want < 1) want = 1;
        }
        int fd = open(n.brightnessPath.c_str(), O_RDONLY | O_CLOEXEC);
        if (fd < 0) continue;
        char buf[32] = {};
        ssize_t got = read(fd, buf, sizeof(buf) - 1);
        close(fd);
        if (got <= 0) continue;
        if (atoi(buf) != want) return false;
    }
    return true;
}

// The lowest raw value over the backlight nodes, -1 when none can be read
// (0: a node is dark).
inline int nanoBacklightReadMin() {
    int lowest = -1;
    for (const auto& n : nanoBacklightNodes()) {
        int fd = open(n.brightnessPath.c_str(), O_RDONLY | O_CLOEXEC);
        if (fd < 0) continue;
        char buf[32] = {};
        ssize_t got = read(fd, buf, sizeof(buf) - 1);
        close(fd);
        if (got <= 0) continue;
        const int v = atoi(buf);
        if (lowest < 0 || v < lowest) lowest = v;
    }
    return lowest;
}

} // namespace nanobl
} // namespace android

#endif // GAMMAOS_NANO_BACKLIGHT_H
