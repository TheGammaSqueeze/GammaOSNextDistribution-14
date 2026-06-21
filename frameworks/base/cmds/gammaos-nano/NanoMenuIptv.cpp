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

// GammaOS Nano - IPTV live channel browser (a nano addition).
//
// Channels are parsed from the community-maintained Free-TV/IPTV playlist
// (https://github.com/Free-TV/IPTV). The raw m3u8 is downloaded once via device curl,
// cached under /data, and refreshed every 24h; a failed refresh keeps the existing cache
// so the list stays usable offline. The browser is a two-level tree: Group (the m3u
// group-title - a country or content group) -> alphabetical sub-bucket (only for large
// groups) -> Channels, which keeps long lists navigable. Activating a channel streams its
// HLS URL through the video player (openIptvStream). Channels that point at YouTube/Twitch
// pages (not direct streams this device can play) are filtered out. First use is gated by a
// content disclaimer; the entry can be hidden from Settings > Video Settings.

#include "NanoMenu.h"
#include "NanoMenuPS3.h"

#include <algorithm>
#include <cctype>
#include <map>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <thread>
#include <time.h>
#include <unistd.h>

#include <android/log.h>
#include <cutils/properties.h>
#define ILOGI(...) __android_log_print(ANDROID_LOG_INFO, "GammaOSNano", __VA_ARGS__)
#define ILOGW(...) __android_log_print(ANDROID_LOG_WARN, "GammaOSNano", __VA_ARGS__)

namespace android {

static const char* kIptvUrl       = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8";
static const char* kIptvCache     = "/data/system/nano_iptv_ftv.m3u8";
static const char* kIptvCacheTmp  = "/data/system/nano_iptv_ftv.m3u8.tmp";
static const char* kIptvCacheOld  = "/data/system/nano_iptv.m3u";   // legacy iptv-org cache (removed on first run)
static const long  kIptvMaxAgeSec = 24 * 60 * 60;   // refresh cadence
static const int   kIptvFetchTimeoutSec = 30;
static const size_t kIptvMaxChannels = 60000;       // sanity bound
static const int   kIptvFlatMax = 150;              // groups up to this size list channels directly
static const int   kIptvChunk = 110;               // larger groups split into alpha buckets of ~this size

// True if the cache is absent or older than the 24h refresh window.
static bool iptvCacheStale() {
    struct stat st;
    if (stat(kIptvCache, &st) != 0) return true;
    return (time(nullptr) - st.st_mtime) >= kIptvMaxAgeSec;
}
static bool iptvCacheExists() {
    struct stat st;
    return stat(kIptvCache, &st) == 0 && st.st_size > 0;
}

// curl the playlist into `dst`. True only if a non-empty, m3u-looking file was produced.
bool NanoMenu::iptvDownloadIndex(const std::string& dst) {
    std::string cmd = "/system/bin/curl -s -L --max-time " + std::to_string(kIptvFetchTimeoutSec)
        + " -A 'gammaos-nano-iptv' -o '" + dst + "' '" + kIptvUrl + "' 2>/dev/null";
    FILE* f = popen(cmd.c_str(), "r");
    if (!f) return false;
    char drain[256];
    while (fread(drain, 1, sizeof(drain), f) > 0) { /* drain */ }
    pclose(f);

    int fd = open(dst.c_str(), O_RDONLY);
    if (fd < 0) return false;
    struct stat st;
    bool ok = (fstat(fd, &st) == 0 && st.st_size > 64);
    if (ok) {
        char head[512] = {0};
        ssize_t n = read(fd, head, sizeof(head) - 1);
        ok = (n > 0 && (strstr(head, "#EXTM3U") || strstr(head, "#EXTINF")));
    }
    close(fd);
    return ok;
}

static std::string iptvAttr(const std::string& line, const char* attr) {
    std::string key = std::string(attr) + "=\"";
    size_t p = line.find(key);
    if (p == std::string::npos) return std::string();
    size_t s = p + key.size();
    size_t e = line.find('"', s);
    return (e == std::string::npos) ? std::string() : line.substr(s, e - s);
}

static bool ciLess(const std::string& a, const std::string& b) {
    size_t n = std::min(a.size(), b.size());
    for (size_t i = 0; i < n; i++) {
        char ca = (char)tolower((unsigned char)a[i]);
        char cb = (char)tolower((unsigned char)b[i]);
        if (ca != cb) return ca < cb;
    }
    return a.size() < b.size();
}

// Leading display character of a name (uppercased; non-ASCII kept as-is for the label).
static std::string iptvLeadChar(const std::string& s) {
    if (s.empty()) return "#";
    unsigned char c = (unsigned char)s[0];
    if (c < 0x80) { char u = (char)toupper(c); return std::string(1, u); }
    // multi-byte (CJK/Cyrillic/...): keep the whole first UTF-8 code point
    int n = (c >= 0xF0) ? 4 : (c >= 0xE0) ? 3 : (c >= 0xC0) ? 2 : 1;
    if ((int)s.size() < n) n = (int)s.size();
    return s.substr(0, n);
}

// Streams this device cannot play: YouTube / Twitch are web pages, not direct HLS.
static bool iptvUnplayable(const std::string& url) {
    return url.find("youtube.com") != std::string::npos
        || url.find("youtu.be")    != std::string::npos
        || url.find("twitch.tv")   != std::string::npos;
}

// Parse the cached playlist into a flat channel list + a group/sub tree. The group-title is
// the top-level group (country or content group); a large group is split into alphabetical
// sub-buckets so its channel list stays navigable. Channel display name = text after the
// first comma following the last quoted attribute (names may contain commas).
bool NanoMenu::iptvParseM3u(const std::string& path,
                            std::vector<IptvChannel>& outCh,
                            std::vector<IptvCat>& outCat) {
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) return false;
    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size <= 0 || st.st_size > 64 * 1024 * 1024) { close(fd); return false; }
    std::string text;
    text.resize(st.st_size);
    ssize_t rd = read(fd, &text[0], st.st_size);
    close(fd);
    if (rd <= 0) return false;
    text.resize(rd);

    outCh.clear();
    outCat.clear();
    std::map<std::string, std::vector<int>> groups;   // group-title -> channel indices

    std::string pName, pGroup;
    bool havePending = false;
    size_t pos = 0, len = text.size();
    while (pos < len && outCh.size() < kIptvMaxChannels) {
        size_t eol = text.find('\n', pos);
        if (eol == std::string::npos) eol = len;
        std::string line = text.substr(pos, eol - pos);
        pos = eol + 1;
        while (!line.empty() && (line.back() == '\r' || line.back() == ' ' || line.back() == '\t')) line.pop_back();
        size_t b = line.find_first_not_of(" \t");
        if (b == std::string::npos) continue;
        if (b > 0) line = line.substr(b);
        if (line.empty()) continue;

        if (line[0] == '#') {
            if (line.compare(0, 8, "#EXTINF:") == 0) {
                std::string group = iptvAttr(line, "group-title");
                if (group.empty()) group = "Other";
                std::string name;
                size_t lastQuote = line.rfind('"');
                size_t comma = (lastQuote != std::string::npos) ? line.find(',', lastQuote) : line.find(',');
                if (comma != std::string::npos) name = line.substr(comma + 1);
                size_t ns = name.find_first_not_of(" \t");
                size_t ne = name.find_last_not_of(" \t");
                name = (ns == std::string::npos) ? std::string() : name.substr(ns, ne - ns + 1);
                pName = name; pGroup = group; havePending = true;
            }
            continue;
        }
        if (!havePending) continue;
        havePending = false;
        if (pName.empty()) continue;
        if (line.compare(0, 7, "http://") != 0 && line.compare(0, 8, "https://") != 0) continue;
        if (iptvUnplayable(line)) continue;   // skip YouTube/Twitch page links

        int ci = (int)outCh.size();
        IptvChannel ch; ch.name = pName; ch.url = line; ch.group = pGroup;
        outCh.push_back(std::move(ch));
        groups[pGroup].push_back(ci);
    }

    if (outCh.empty()) return false;

    for (auto& gKv : groups) {
        IptvCat c; c.title = gKv.first; c.total = (int)gKv.second.size();
        std::vector<int>& chans = gKv.second;
        std::sort(chans.begin(), chans.end(),
                  [&](int a, int d) { return ciLess(outCh[a].name, outCh[d].name); });
        if ((int)chans.size() <= kIptvFlatMax) {
            IptvCountry sub; sub.name = ""; sub.channels = std::move(chans);
            c.countries.push_back(std::move(sub));
        } else {
            for (size_t i = 0; i < chans.size(); i += kIptvChunk) {
                size_t end = std::min(chans.size(), i + (size_t)kIptvChunk);
                IptvCountry sub;
                std::string fa = iptvLeadChar(outCh[chans[i]].name);
                std::string fb = iptvLeadChar(outCh[chans[end - 1]].name);
                sub.name = (fa == fb) ? fa : (fa + " - " + fb);
                sub.channels.assign(chans.begin() + i, chans.begin() + end);
                c.countries.push_back(std::move(sub));
            }
        }
        outCat.push_back(std::move(c));
    }
    std::sort(outCat.begin(), outCat.end(),
              [](const IptvCat& a, const IptvCat& b) { return ciLess(a.title, b.title); });
    return true;
}

void NanoMenu::iptvPublish(std::vector<IptvChannel>& ch, std::vector<IptvCat>& cat) {
    {
        std::lock_guard<std::mutex> lk(mIptvMutex);
        mIptvChannels = std::move(ch);
        mIptvCats = std::move(cat);
        mIptvStatus.clear();
    }
    mIptvReady.store(true);
    mIptvDirty.store(true);
}

void NanoMenu::iptvFetchThreadFunc() {
    unlink(kIptvCacheOld);   // drop the legacy iptv-org cache, if present (one-time)
    bool stale = iptvCacheStale();
    bool downloaded = false;
    if (stale) {
        if (iptvDownloadIndex(kIptvCacheTmp)) {
            if (rename(kIptvCacheTmp, kIptvCache) == 0) {
                (void)chown(kIptvCache, 0, 0);
                (void)chmod(kIptvCache, 0644);
                downloaded = true;
                ILOGI("NanoMenu: IPTV playlist refreshed");
            } else {
                unlink(kIptvCacheTmp);
            }
        } else {
            unlink(kIptvCacheTmp);   // keep the existing cache on a failed refresh
            ILOGW("NanoMenu: IPTV refresh failed (keeping cached list)");
        }
    }

    if (!mIptvReady.load() || downloaded) {
        if (iptvCacheExists()) {
            std::vector<IptvChannel> ch;
            std::vector<IptvCat> cat;
            if (iptvParseM3u(kIptvCache, ch, cat)) {
                ILOGI("NanoMenu: IPTV loaded %zu channels in %zu groups", ch.size(), cat.size());
                iptvPublish(ch, cat);
            } else if (!mIptvReady.load()) {
                std::lock_guard<std::mutex> lk(mIptvMutex);
                mIptvStatus = "Could not read the channel list.";
                mIptvDirty.store(true);
            }
        } else if (!mIptvReady.load()) {
            std::lock_guard<std::mutex> lk(mIptvMutex);
            mIptvStatus = "No network connection. Connect to the Internet and try again.";
            mIptvDirty.store(true);
        }
    }
    mIptvScanRunning.store(false);
}

void NanoMenu::iptvFetchAsync() {
    bool expected = false;
    if (!mIptvScanRunning.compare_exchange_strong(expected, true)) return;
    std::thread(&NanoMenu::iptvFetchThreadFunc, this).detach();
}

void NanoMenu::iptvEnsureLoaded() {
    iptvFetchAsync();   // (re)parses the cache + re-checks the 24h window
    if (!mIptvReady.load()) {
        std::lock_guard<std::mutex> lk(mIptvMutex);
        if (mIptvStatus.empty()) mIptvStatus = "Loading channel list...";
    }
}

// Synchronous variant for global search: if the channel list is not loaded yet, parse it
// inline from the on-disk cache (no network) so this very search includes IPTV channels,
// then kick the background 24h refresh. The one-time parse (~a few hundred ms) runs in the
// caller's own process, so it is immune to the two-process publish race the async path has.
void NanoMenu::iptvEnsureLoadedSync() {
    if (!mIptvReady.load() && iptvCacheExists()) {
        std::vector<IptvChannel> ch;
        std::vector<IptvCat> cat;
        if (iptvParseM3u(kIptvCache, ch, cat)) iptvPublish(ch, cat);
    }
    iptvFetchAsync();   // background refresh (24h-gated); no-op if already running
}

// Render thread: live toggle-hide for the Video column + rebuild the open groups screen.
void NanoMenu::iptvDrain() {
    int en = property_get_bool("persist.gammaos.nano.iptv", true) ? 1 : 0;
    if (mIptvEnabledCache < 0) mIptvEnabledCache = en;
    else if (en != mIptvEnabledCache) { mIptvEnabledCache = en; mPs3CatsStale = true; }

    if (!mIptvDirty.load()) return;
    if (mPs3Stack.empty()) return;
    Ps3Level& top = mPs3Stack.back();
    if (top.screenKind != IPTV_GROUPS) return;   // keep the flag set until the screen is on top
    mIptvDirty.store(false);
    int keep = top.sel;
    buildIptvCategoriesScreen(top);
    if (keep >= 0 && keep < (int)top.items.size()) top.sel = keep;
}

void NanoMenu::buildIptvCategoriesScreen(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.title = "IPTV"; out.screenKind = IPTV_GROUPS;
    GLuint nm = nmapForIcon(4);
    std::lock_guard<std::mutex> lk(mIptvMutex);
    if (mIptvCats.empty()) {
        Ps3Item it; it.label = mIptvStatus.empty() ? "Loading channel list..." : mIptvStatus;
        it.kind = PS3_GS_FIELD; it.a = -1;
        it.iconTex = 0; it.nmapTex = 0; it.iconR = it.iconG = it.iconB = 0.6f;
        out.items.push_back(it);
        return;
    }
    for (size_t c = 0; c < mIptvCats.size(); c++) {
        Ps3Item it; it.label = mIptvCats[c].title; it.kind = PS3_IPTV_GROUP; it.a = (int)c;
        int n = mIptvCats[c].total;
        char v[32]; snprintf(v, sizeof(v), "%d %s", n, n == 1 ? "Channel" : "Channels"); it.value = v;
        it.iconTex = 0; it.nmapTex = nm; it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(std::move(it));
    }
}

void NanoMenu::buildIptvCountrySubmenu(int catIdx, Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = GS_NONE;
    GLuint nm = nmapForIcon(4);
    std::lock_guard<std::mutex> lk(mIptvMutex);
    if (catIdx < 0 || catIdx >= (int)mIptvCats.size()) { out.title = "IPTV"; return; }
    const IptvCat& c = mIptvCats[catIdx];
    out.title = c.title;
    for (size_t k = 0; k < c.countries.size(); k++) {
        Ps3Item it; it.label = c.countries[k].name.empty() ? "All" : c.countries[k].name;
        it.kind = PS3_IPTV_COUNTRY; it.a = catIdx; it.b = (int)k;
        int n = (int)c.countries[k].channels.size();
        char v[32]; snprintf(v, sizeof(v), "%d %s", n, n == 1 ? "Channel" : "Channels"); it.value = v;
        it.iconTex = 0; it.nmapTex = nm; it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(std::move(it));
    }
}

void NanoMenu::buildIptvChannelSubmenu(int catIdx, int countryIdx, Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = GS_NONE;
    GLuint nm = nmapForIcon(4);
    std::lock_guard<std::mutex> lk(mIptvMutex);
    if (catIdx < 0 || catIdx >= (int)mIptvCats.size()) { out.title = "IPTV"; return; }
    const IptvCat& c = mIptvCats[catIdx];
    if (countryIdx < 0 || countryIdx >= (int)c.countries.size()) { out.title = c.title; return; }
    const IptvCountry& cy = c.countries[countryIdx];
    out.title = cy.name.empty() ? c.title : (c.title + " - " + cy.name);
    for (int ci : cy.channels) {
        if (ci < 0 || ci >= (int)mIptvChannels.size()) continue;
        const IptvChannel& ch = mIptvChannels[ci];
        Ps3Item it; it.label = ch.name; it.kind = PS3_IPTV_CHANNEL; it.a = ci;
        it.payloadStr = ch.url;
        it.desc = c.title;        // carried into the stream queue / playlist entry
        it.value = "Live";
        it.iconTex = 0; it.nmapTex = nm; it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(std::move(it));
    }
    if (out.items.empty()) {
        Ps3Item it; it.label = "No channels"; it.kind = PS3_GS_FIELD; it.a = -1;
        it.iconR = it.iconG = it.iconB = 0.6f;
        out.items.push_back(it);
    }
}

// Disclaimer-passed entry: ensure the list is loading/loaded and slide in the groups screen.
void NanoMenu::iptvOpen() {
    iptvEnsureLoaded();
    std::vector<Ps3Item> ps = ps3CurItems();
    int pSel = ps3CurSel();
    Ps3Level lvl; buildIptvCategoriesScreen(lvl); mPs3Stack.push_back(lvl);
    mPs3SubParentItems = ps; mPs3SubParentIdx = pSel; mPs3SubChildItems = mPs3Stack.back().items;
    mPs3SubDir = 1; mPs3SubAnimStart = mEffectTime; mPs3SubAnim = 0.0f;
    mPs3AnimItem = 0.0f; mPs3ItemAnimStart = -1.0f;
}

} // namespace android
