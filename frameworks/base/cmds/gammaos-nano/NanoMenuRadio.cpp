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

// GammaOS Nano - Internet Radio station browser (a nano addition, Music category).
//
// The audio analog of the IPTV browser (see NanoMenuIptv.cpp). Stations are parsed from a
// community-maintained m3u (default the Pulham "Internet Radio HQ URL" list). The raw m3u is
// downloaded once via device curl, cached under /data, and refreshed every 24h; a failed refresh
// keeps the existing cache so the list stays usable offline. The user can override the playlist
// URL from Settings > Music Settings. Stations are grouped by the m3u group-title; a list with no
// group-titles becomes a single "All Stations" group split into alphabetical buckets so the long
// list stays navigable. Activating a station streams its URL through the MUSIC player
// (openRadioStation) so it gets the Now-Playing screen, visualizers and background playback.
// First use is gated by a content disclaimer; the entry can be hidden from Settings > Music
// Settings.

#include "NanoMenu.h"
#include "NanoMenuPS3.h"
#include "NanoI18n.h"      // trDyn() runtime translation of hardcoded UI strings

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
#define RADLOGI(...) __android_log_print(ANDROID_LOG_INFO, "GammaOSNano", __VA_ARGS__)
#define RADLOGW(...) __android_log_print(ANDROID_LOG_WARN, "GammaOSNano", __VA_ARGS__)

namespace android {

static const char* kRadioDefaultUrl =
    "https://github.com/Pulham/Internet-Radio-HQ-URL-playlists/raw/refs/heads/main/Radio%20Stations.m3u";
static const char* kRadioCache     = "/data/system/nano_radio.m3u";
static const char* kRadioCacheTmp  = "/data/system/nano_radio.m3u.tmp";
static const char* kRadioUrlMarker = "/data/system/nano_radio.url";   // URL the cache was fetched from

static const long  kRadioMaxAgeSec = 24 * 60 * 60;   // refresh cadence
static const int   kRadioFetchTimeoutSec = 30;
static const size_t kRadioMaxStations = 20000;       // sanity bound
static const int   kRadioFlatMax = 80;               // a group up to this size lists stations directly
static const int   kRadioChunk = 60;                 // larger groups split into alpha buckets of ~this size

// The playlist URL: a user override from Settings > Music Settings if set + valid, else the
// default Pulham list. (persist.gammaos.nano.radio.url, OSK-entered.)
static std::string radioResolveUrl() {
    char buf[PROPERTY_VALUE_MAX] = {0};
    property_get("persist.gammaos.nano.radio.url", buf, "");
    std::string u = buf;
    size_t a = u.find_first_not_of(" \t\r\n");
    size_t b = u.find_last_not_of(" \t\r\n");
    u = (a == std::string::npos) ? std::string() : u.substr(a, b - a + 1);
    if (u.compare(0, 7, "http://") == 0 || u.compare(0, 8, "https://") == 0) return u;
    return kRadioDefaultUrl;
}
static std::string radioReadMarker() {
    int fd = open(kRadioUrlMarker, O_RDONLY);
    if (fd < 0) return std::string();
    char buf[4096] = {0};
    ssize_t n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    std::string s = (n > 0) ? std::string(buf, n) : std::string();
    size_t e = s.find_last_not_of(" \t\r\n");
    return (e == std::string::npos) ? std::string() : s.substr(0, e + 1);
}
static void radioWriteMarker(const std::string& url) {
    int fd = open(kRadioUrlMarker, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) return;
    (void)!write(fd, url.c_str(), url.size());
    close(fd);
    (void)chmod(kRadioUrlMarker, 0644);
}

static bool radioCacheStale() {
    struct stat st;
    if (stat(kRadioCache, &st) != 0) return true;
    return (time(nullptr) - st.st_mtime) >= kRadioMaxAgeSec;
}
static bool radioCacheExists() {
    struct stat st;
    return stat(kRadioCache, &st) == 0 && st.st_size > 0;
}

// curl the playlist into `dst`. True only if a non-empty, m3u-looking file was produced.
bool NanoMenu::radioDownloadIndex(const std::string& dst) {
    std::string url = radioResolveUrl();
    if (url.find('\'') != std::string::npos) return false;   // never let a quote break the shell command
    std::string cmd = "/system/bin/curl -s -L --max-time " + std::to_string(kRadioFetchTimeoutSec)
        + " -A 'gammaos-nano-radio' -o '" + dst + "' '" + url + "' 2>/dev/null";
    FILE* f = popen(cmd.c_str(), "r");
    if (!f) return false;
    char drain[256];
    while (fread(drain, 1, sizeof(drain), f) > 0) { /* drain */ }
    pclose(f);

    int fd = open(dst.c_str(), O_RDONLY);
    if (fd < 0) return false;
    struct stat st;
    bool ok = (fstat(fd, &st) == 0 && st.st_size > 16);
    if (ok) {
        char head[512] = {0};
        ssize_t n = read(fd, head, sizeof(head) - 1);
        // accept either an #EXTM3U/#EXTINF playlist or a bare list of http URLs
        ok = (n > 0 && (strstr(head, "#EXTM3U") || strstr(head, "#EXTINF")
                        || strstr(head, "http://") || strstr(head, "https://")));
    }
    close(fd);
    return ok;
}

static std::string radioAttr(const std::string& line, const char* attr) {
    std::string key = std::string(attr) + "=\"";
    size_t p = line.find(key);
    if (p == std::string::npos) return std::string();
    size_t s = p + key.size();
    size_t e = line.find('"', s);
    return (e == std::string::npos) ? std::string() : line.substr(s, e - s);
}

static bool rciLess(const std::string& a, const std::string& b) {
    size_t n = std::min(a.size(), b.size());
    for (size_t i = 0; i < n; i++) {
        char ca = (char)tolower((unsigned char)a[i]);
        char cb = (char)tolower((unsigned char)b[i]);
        if (ca != cb) return ca < cb;
    }
    return a.size() < b.size();
}

// Leading display character of a name (uppercased; non-ASCII kept as-is for the label).
static std::string radioLeadChar(const std::string& s) {
    if (s.empty()) return "#";
    unsigned char c = (unsigned char)s[0];
    if (c < 0x80) { char u = (char)toupper(c); return std::string(1, u); }
    int n = (c >= 0xF0) ? 4 : (c >= 0xE0) ? 3 : (c >= 0xC0) ? 2 : 1;
    if ((int)s.size() < n) n = (int)s.size();
    return s.substr(0, n);
}

// Trim whitespace and a leading list-decoration "- " (the Pulham list prefixes every name with
// " - "), plus surrounding quotes.
static std::string radioCleanName(std::string s) {
    size_t a = s.find_first_not_of(" \t");
    size_t b = s.find_last_not_of(" \t\r");
    s = (a == std::string::npos) ? std::string() : s.substr(a, b - a + 1);
    while (!s.empty() && (s[0] == '-' || s[0] == ' ' || s[0] == '\t')) {
        // strip a leading "- " decoration but keep a real name that merely starts with '-'
        if (s[0] == '-' && s.size() >= 2 && s[1] == ' ') s = s.substr(2);
        else if (s[0] == ' ' || s[0] == '\t') s = s.substr(1);
        else break;
    }
    if (s.size() >= 2 && s.front() == '"' && s.back() == '"') s = s.substr(1, s.size() - 2);
    return s;
}

// Parse the cached playlist into a flat station list + a group/bucket tree. group-title is the
// top-level group (when present); otherwise everything goes into a single "All Stations" group. A
// large group is split into alphabetical buckets so its list stays navigable. Station display name
// = text after the first comma following the last quoted attribute.
bool NanoMenu::radioParseM3u(const std::string& path,
                             std::vector<RadioStation>& outSt,
                             std::vector<RadioCat>& outCat) {
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

    outSt.clear();
    outCat.clear();
    std::map<std::string, std::vector<int>> groups;   // group-title -> station indices

    std::string pName, pGroup;
    bool havePending = false;
    size_t pos = 0, len = text.size();
    while (pos < len && outSt.size() < kRadioMaxStations) {
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
                std::string group = radioAttr(line, "group-title");
                std::string name;
                size_t lastQuote = line.rfind('"');
                size_t comma = (lastQuote != std::string::npos) ? line.find(',', lastQuote) : line.find(',');
                if (comma != std::string::npos) name = line.substr(comma + 1);
                pName = radioCleanName(name); pGroup = group; havePending = true;
            }
            continue;
        }
        if (line.compare(0, 7, "http://") != 0 && line.compare(0, 8, "https://") != 0) continue;
        // A bare URL line with no preceding #EXTINF: derive a name from the URL host.
        std::string name = havePending ? pName : std::string();
        std::string group = havePending ? pGroup : std::string();
        havePending = false;
        if (name.empty()) {
            std::string h = line;
            size_t s = h.find("://"); if (s != std::string::npos) h = h.substr(s + 3);
            size_t sl = h.find('/');  if (sl != std::string::npos) h = h.substr(0, sl);
            name = h.empty() ? line : h;
        }
        if (group.empty()) group = "All Stations";

        int si = (int)outSt.size();
        RadioStation s; s.name = name; s.url = line; s.group = group;
        outSt.push_back(std::move(s));
        groups[group].push_back(si);
    }

    if (outSt.empty()) return false;

    for (auto& gKv : groups) {
        RadioCat c; c.title = gKv.first; c.total = (int)gKv.second.size();
        std::vector<int>& sts = gKv.second;
        std::sort(sts.begin(), sts.end(),
                  [&](int a, int d) { return rciLess(outSt[a].name, outSt[d].name); });
        if ((int)sts.size() <= kRadioFlatMax) {
            RadioBucket bk; bk.name = ""; bk.stations = std::move(sts);
            c.buckets.push_back(std::move(bk));
        } else {
            for (size_t i = 0; i < sts.size(); i += kRadioChunk) {
                size_t end = std::min(sts.size(), i + (size_t)kRadioChunk);
                RadioBucket bk;
                std::string fa = radioLeadChar(outSt[sts[i]].name);
                std::string fb = radioLeadChar(outSt[sts[end - 1]].name);
                bk.name = (fa == fb) ? fa : (fa + " - " + fb);
                bk.stations.assign(sts.begin() + i, sts.begin() + end);
                c.buckets.push_back(std::move(bk));
            }
        }
        outCat.push_back(std::move(c));
    }
    std::sort(outCat.begin(), outCat.end(),
              [](const RadioCat& a, const RadioCat& b) { return rciLess(a.title, b.title); });
    return true;
}

void NanoMenu::radioPublish(std::vector<RadioStation>& st, std::vector<RadioCat>& cat) {
    {
        std::lock_guard<std::mutex> lk(mRadioMutex);
        mRadioStations = std::move(st);
        mRadioCats = std::move(cat);
        mRadioStatus.clear();
    }
    mRadioReady.store(true);
    mRadioDirty.store(true);
}

void NanoMenu::radioFetchThreadFunc() {
    // Refresh when the cache is missing/old OR the user changed the playlist URL (override).
    bool urlChanged = (radioResolveUrl() != radioReadMarker());
    bool stale = urlChanged || radioCacheStale();
    bool downloaded = false;
    if (stale) {
        if (radioDownloadIndex(kRadioCacheTmp)) {
            if (rename(kRadioCacheTmp, kRadioCache) == 0) {
                (void)chown(kRadioCache, 0, 0);
                (void)chmod(kRadioCache, 0644);
                radioWriteMarker(radioResolveUrl());
                downloaded = true;
                RADLOGI("NanoMenu: radio playlist refreshed%s", urlChanged ? " (URL changed)" : "");
            } else {
                unlink(kRadioCacheTmp);
            }
        } else {
            unlink(kRadioCacheTmp);   // keep the existing cache on a failed refresh
            RADLOGW("NanoMenu: radio refresh failed (keeping cached list)");
        }
    }

    if (!mRadioReady.load() || downloaded) {
        if (radioCacheExists()) {
            std::vector<RadioStation> st;
            std::vector<RadioCat> cat;
            if (radioParseM3u(kRadioCache, st, cat)) {
                RADLOGI("NanoMenu: radio loaded %zu stations in %zu groups", st.size(), cat.size());
                radioPublish(st, cat);
            } else if (!mRadioReady.load()) {
                std::lock_guard<std::mutex> lk(mRadioMutex);
                mRadioStatus = "Could not read the station list.";
                mRadioDirty.store(true);
            }
        } else if (!mRadioReady.load()) {
            std::lock_guard<std::mutex> lk(mRadioMutex);
            mRadioStatus = "No network connection. Connect to the Internet and try again.";
            mRadioDirty.store(true);
        }
    }
    mRadioScanRunning.store(false);
}

void NanoMenu::radioFetchAsync() {
    bool expected = false;
    if (!mRadioScanRunning.compare_exchange_strong(expected, true)) return;
    std::thread(&NanoMenu::radioFetchThreadFunc, this).detach();
}

void NanoMenu::radioEnsureLoaded() {
    radioFetchAsync();
    if (!mRadioReady.load()) {
        std::lock_guard<std::mutex> lk(mRadioMutex);
        if (mRadioStatus.empty()) mRadioStatus = "Loading station list...";
    }
}

// Synchronous variant for global search: parse the on-disk cache inline if not loaded so this
// search includes stations even without first opening the browser, then kick the 24h refresh.
void NanoMenu::radioEnsureLoadedSync() {
    if (!mRadioReady.load() && radioCacheExists()) {
        std::vector<RadioStation> st;
        std::vector<RadioCat> cat;
        if (radioParseM3u(kRadioCache, st, cat)) radioPublish(st, cat);
    }
    radioFetchAsync();
}

// Render thread: live toggle-hide for the Music column + rebuild the open stations screen.
void NanoMenu::radioDrain() {
    int en = property_get_bool("persist.gammaos.nano.radio", true) ? 1 : 0;
    if (mRadioEnabledCache < 0) mRadioEnabledCache = en;
    else if (en != mRadioEnabledCache) { mRadioEnabledCache = en; mPs3CatsStale = true; }

    if (!mRadioDirty.load()) return;
    if (mPs3Stack.empty()) return;
    Ps3Level& top = mPs3Stack.back();
    if (top.screenKind != RADIO_STATIONS) return;   // keep the flag set until the screen is on top
    mRadioDirty.store(false);
    int keep = top.sel;
    buildRadioRootScreen(top);
    if (keep >= 0 && keep < (int)top.items.size()) top.sel = keep;
}

// Internet Radio root screen. Collapses sensibly: a single group with a single bucket lists its
// stations directly; a single group with several buckets lists the buckets; multiple groups list
// the groups. (screenKind RADIO_STATIONS so radioDrain rebuilds it as the worker publishes.)
void NanoMenu::buildRadioRootScreen(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.title = "Internet Radio"; out.screenKind = RADIO_STATIONS;
    GLuint nm = nmapForIcon(3);
    std::lock_guard<std::mutex> lk(mRadioMutex);
    if (mRadioCats.empty()) {
        Ps3Item it; it.label = mRadioStatus.empty() ? "Loading station list..." : mRadioStatus;
        it.kind = PS3_GS_FIELD; it.a = -1;
        it.iconTex = 0; it.nmapTex = 0; it.iconR = it.iconG = it.iconB = 0.6f;
        out.items.push_back(it);
        return;
    }
    if (mRadioCats.size() == 1) {
        const RadioCat& c = mRadioCats[0];
        if (c.buckets.size() == 1) {
            for (int si : c.buckets[0].stations) {
                if (si < 0 || si >= (int)mRadioStations.size()) continue;
                const RadioStation& s = mRadioStations[si];
                Ps3Item it; it.label = s.name; it.kind = PS3_RADIO_STATION; it.a = si;
                it.payloadStr = s.url; it.desc = c.title; it.value = "Live";
                it.iconTex = 0; it.nmapTex = nm; it.iconR = it.iconG = it.iconB = 1.0f;
                out.items.push_back(std::move(it));
            }
        } else {
            for (size_t k = 0; k < c.buckets.size(); k++) {
                Ps3Item it; it.label = c.buckets[k].name.empty() ? "All" : c.buckets[k].name;
                it.kind = PS3_RADIO_BUCKET; it.a = 0; it.b = (int)k;
                int n = (int)c.buckets[k].stations.size();
                char v[32]; snprintf(v, sizeof(v), "%d %s", n, trDyn(n == 1 ? "Station" : "Stations")); it.value = v;
                it.iconTex = 0; it.nmapTex = nm; it.iconR = it.iconG = it.iconB = 1.0f;
                out.items.push_back(std::move(it));
            }
        }
        return;
    }
    for (size_t c = 0; c < mRadioCats.size(); c++) {
        Ps3Item it; it.label = mRadioCats[c].title; it.kind = PS3_RADIO_GROUP; it.a = (int)c;
        int n = mRadioCats[c].total;
        char v[32]; snprintf(v, sizeof(v), "%d %s", n, trDyn(n == 1 ? "Station" : "Stations")); it.value = v;
        it.iconTex = 0; it.nmapTex = nm; it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(std::move(it));
    }
}

void NanoMenu::buildRadioBucketSubmenu(int catIdx, Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = GS_NONE;
    GLuint nm = nmapForIcon(3);
    std::lock_guard<std::mutex> lk(mRadioMutex);
    if (catIdx < 0 || catIdx >= (int)mRadioCats.size()) { out.title = "Internet Radio"; return; }
    const RadioCat& c = mRadioCats[catIdx];
    out.title = c.title;
    for (size_t k = 0; k < c.buckets.size(); k++) {
        Ps3Item it; it.label = c.buckets[k].name.empty() ? "All" : c.buckets[k].name;
        it.kind = PS3_RADIO_BUCKET; it.a = catIdx; it.b = (int)k;
        int n = (int)c.buckets[k].stations.size();
        char v[32]; snprintf(v, sizeof(v), "%d %s", n, trDyn(n == 1 ? "Station" : "Stations")); it.value = v;
        it.iconTex = 0; it.nmapTex = nm; it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(std::move(it));
    }
}

void NanoMenu::buildRadioStationSubmenu(int catIdx, int bucketIdx, Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = GS_NONE;
    GLuint nm = nmapForIcon(3);
    std::lock_guard<std::mutex> lk(mRadioMutex);
    if (catIdx < 0 || catIdx >= (int)mRadioCats.size()) { out.title = "Internet Radio"; return; }
    const RadioCat& c = mRadioCats[catIdx];
    if (bucketIdx < 0 || bucketIdx >= (int)c.buckets.size()) { out.title = c.title; return; }
    const RadioBucket& bk = c.buckets[bucketIdx];
    out.title = bk.name.empty() ? c.title : (c.title + " - " + bk.name);
    for (int si : bk.stations) {
        if (si < 0 || si >= (int)mRadioStations.size()) continue;
        const RadioStation& s = mRadioStations[si];
        Ps3Item it; it.label = s.name; it.kind = PS3_RADIO_STATION; it.a = si;
        it.payloadStr = s.url;
        it.desc = c.title;
        it.value = "Live";
        it.iconTex = 0; it.nmapTex = nm; it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(std::move(it));
    }
    if (out.items.empty()) {
        Ps3Item it; it.label = "No stations"; it.kind = PS3_GS_FIELD; it.a = -1;
        it.iconR = it.iconG = it.iconB = 0.6f;
        out.items.push_back(it);
    }
}

// Disclaimer-passed entry: ensure the list is loading/loaded and slide in the stations screen.
void NanoMenu::radioOpen() {
    radioEnsureLoaded();
    std::vector<Ps3Item> ps = ps3CurItems();
    int pSel = ps3CurSel();
    Ps3Level lvl; buildRadioRootScreen(lvl); mPs3Stack.push_back(lvl);
    mPs3SubParentItems = ps; mPs3SubParentIdx = pSel; mPs3SubChildItems = mPs3Stack.back().items;
    mPs3SubDir = 1; mPs3SubAnimStart = mEffectTime; mPs3SubAnim = 0.0f;
    mPs3AnimItem = 0.0f; mPs3ItemAnimStart = -1.0f;
}

} // namespace android
