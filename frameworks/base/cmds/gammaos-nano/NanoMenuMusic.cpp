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

// NanoMenu music glue: the nano_music.json library model + persist + scan, the
// "Search for Media Servers" folder import (reusing the Game Systems folder
// picker), and the Music-column content (albums / playlists / tracks). The
// Now-Playing screen + control panel live further down (Phase 3). 1:1 source of
// truth: /work/ps3/xmb-app/index.html (mpPlaylists, the music DATA category).

#define LOG_TAG "GammaOSNano"

#include "NanoMenu.h"
#include "NanoMenuPS3.h"
#include "NanoMenuPS3Bg.h"
#include "NanoMenuDrm.h"   // sDrmGlRotation / sDrmRotationDeg for the title clip scissor
#include "NanoMenuMusicCanyon.h"
#include "NanoMenuMusicGlobe.h"
#include "NanoMenuShaders.h"   // kEffectNames/kActiveEffects for the extra wallpaper-effect visualizers
#include "NanoJson.h"

#include <dirent.h>
#include <fcntl.h>
#include <cerrno>
#include <unistd.h>
#include <cutils/properties.h>   // property_get for the storage-ready gate
#include <sys/stat.h>
#include <string.h>
#include <strings.h>
#include <algorithm>
#include <cstdio>
#include <cmath>
#include <cstdlib>
#include <map>
#include <set>
#include <thread>
#include <utils/Log.h>

namespace android {

// DRM GL rotation matrix (NanoMenuDrm.cpp); the Canyon composite maps logical NDC
// to the physical panel through it, like the globe and wave composites.
extern float sDrmRotMat[4];

// Audio file extensions the scanner accepts (lowercased compare).
static bool isAudioExt(const std::string& nameLower) {
    static const char* kExts[] = { ".mp3", ".flac", ".m4a", ".aac", ".ogg",
                                   ".oga", ".opus", ".wav", ".wma" };
    size_t dot = nameLower.rfind('.');
    if (dot == std::string::npos) return false;
    std::string ext = nameLower.substr(dot);
    for (const char* e : kExts) if (ext == e) return true;
    return false;
}

static std::string baseName(const std::string& path) {
    size_t sl = path.rfind('/');
    return sl == std::string::npos ? path : path.substr(sl + 1);
}
static std::string parentName(const std::string& path) {
    size_t sl = path.rfind('/');
    if (sl == std::string::npos || sl == 0) return "";
    size_t sl2 = path.rfind('/', sl - 1);
    return path.substr(sl2 + 1, sl - sl2 - 1);
}
static std::string stripExt(const std::string& name) {
    size_t dot = name.rfind('.');
    return dot == std::string::npos ? name : name.substr(0, dot);
}

// ---------------------------------------------------------------------------
// nano_music.json persistence (mirrors nano_systems.json: atomic write, mtime
// cross-process reload).
// ---------------------------------------------------------------------------
int64_t NanoMenu::musicConfigStamp() const {
    struct stat st;
    if (stat("/data/system/nano_music.json", &st) != 0) return -1;
    return (int64_t)st.st_mtim.tv_sec * 1000000000LL + st.st_mtim.tv_nsec;
}

bool NanoMenu::loadMusicConfig() {
    const char* path = "/data/system/nano_music.json";
    int fd = open(path, O_RDONLY);
    if (fd < 0) { mMusicCfgStamp = -1; return false; }
    std::string content;
    struct stat st;
    if (fstat(fd, &st) == 0 && st.st_size > 0 && st.st_size < 8 * 1024 * 1024) {
        content.resize(st.st_size);
        ssize_t rd = read(fd, &content[0], st.st_size);
        if (rd > 0) content.resize(rd); else content.clear();
    }
    close(fd);
    mMusicCfgStamp = musicConfigStamp();
    if (content.empty()) return false;

    njson::Value root;
    if (!njson::parse(content, &root) || !root.isObject()) return false;

    mMusicFolders.clear();
    mMusicTracks.clear();
    mMusicPlaylists.clear();

    mMusicCfgVersion = root.find("version") ? (int)root.find("version")->asNumber(0) : 0;

    if (const njson::Value* folders = root.find("folders"); folders && folders->isArray())
        for (const auto& f : folders->arr) if (f.isString()) mMusicFolders.push_back(f.str);

    if (const njson::Value* tracks = root.find("tracks"); tracks && tracks->isArray())
        for (const auto& t : tracks->arr) {
            if (!t.isObject()) continue;
            MusicTrack mt;
            mt.file = t.getString("file");
            if (mt.file.empty()) continue;
            mt.title = t.getString("n");
            mt.artist = t.getString("a");
            mt.album = t.getString("st");
            mt.codec = t.getString("codec");
            mt.durationSec = t.find("dur") ? t.find("dur")->asNumber(0) : 0;
            mt.trackNo = t.getInt("track", 0);
            mt.mtime = t.find("mtime") ? (int64_t)t.find("mtime")->asNumber(0) : 0;
            mt.albumHidden = t.getInt("ah", 0) != 0;
            mMusicTracks.push_back(std::move(mt));
        }

    if (const njson::Value* pls = root.find("playlists"); pls && pls->isArray())
        for (const auto& p : pls->arr) {
            if (!p.isObject()) continue;
            MusicPlaylist pl;
            pl.name = p.getString("name");
            if (pl.name.empty()) continue;
            if (const njson::Value* fs = p.find("files"); fs && fs->isArray())
                for (const auto& f : fs->arr) if (f.isString()) pl.files.push_back(f.str);
            pl.m3uPath = p.getString("m3u");
            mMusicPlaylists.push_back(std::move(pl));
        }
    ALOGI("NanoMenu: loaded music library (%zu folders, %zu tracks, %zu playlists)",
          mMusicFolders.size(), mMusicTracks.size(), mMusicPlaylists.size());
    musicRemapQueueAfterReload();   // keep a minimized-playing queue valid across the reload
    return true;
}

void NanoMenu::saveMusicConfig() {
    njson::Value root = njson::Value::makeObject();
    root.set("version") = njson::Value::makeNumber(kMusicMetaVersion);
    njson::Value folders = njson::Value::makeArray();
    for (const auto& f : mMusicFolders) folders.arr.push_back(njson::Value::makeString(f));
    root.set("folders") = std::move(folders);
    njson::Value tracks = njson::Value::makeArray();
    for (const auto& t : mMusicTracks) {
        njson::Value v = njson::Value::makeObject();
        v.set("file") = njson::Value::makeString(t.file);
        v.set("n") = njson::Value::makeString(t.title);
        v.set("a") = njson::Value::makeString(t.artist);
        v.set("st") = njson::Value::makeString(t.album);
        v.set("codec") = njson::Value::makeString(t.codec);
        v.set("dur") = njson::Value::makeNumber(t.durationSec);
        v.set("track") = njson::Value::makeNumber(t.trackNo);
        v.set("mtime") = njson::Value::makeNumber((double)t.mtime);
        if (t.albumHidden) v.set("ah") = njson::Value::makeNumber(1);
        tracks.arr.push_back(std::move(v));
    }
    root.set("tracks") = std::move(tracks);
    njson::Value pls = njson::Value::makeArray();
    for (const auto& p : mMusicPlaylists) {
        njson::Value v = njson::Value::makeObject();
        v.set("name") = njson::Value::makeString(p.name);
        njson::Value fs = njson::Value::makeArray();
        for (const auto& f : p.files) fs.arr.push_back(njson::Value::makeString(f));
        v.set("files") = std::move(fs);
        if (!p.m3uPath.empty()) v.set("m3u") = njson::Value::makeString(p.m3uPath);
        pls.arr.push_back(std::move(v));
    }
    root.set("playlists") = std::move(pls);
    std::string text = njson::serialize(root, true);

    const char* path = "/data/system/nano_music.json";
    const char* tmp = "/data/system/nano_music.json.tmp";
    int fd = open(tmp, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) { ALOGW("NanoMenu: cannot write %s (errno %d)", tmp, errno); return; }
    size_t off = 0; bool ok = true;
    while (off < text.size()) {
        ssize_t w = write(fd, text.c_str() + off, text.size() - off);
        if (w <= 0) { ok = false; break; }
        off += (size_t)w;
    }
    fsync(fd); close(fd);
    if (!ok) { unlink(tmp); return; }
    if (rename(tmp, path) != 0) { unlink(tmp); return; }
    (void)chown(path, 0, 0);
    (void)chmod(path, 0644);
    mMusicCfgVersion = kMusicMetaVersion;   // on-disk now matches the current parser
    mMusicCfgStamp = musicConfigStamp();
    ALOGD("NanoMenu: wrote nano_music.json (%zu tracks)", mMusicTracks.size());
}

// ---------------------------------------------------------------------------
// Lazy load: parse the config once, init the audio engine (no stream yet), and
// kick a scan if folders exist. Self-guarded; safe to call from every entry.
// ---------------------------------------------------------------------------
// Lazy-load the music library the first time the cursor lands on the Music
// category, so albums parsed from nano_music.json appear in the column without
// touching anything at boot (the lazy-load requirement). No-op off Music or once
// loaded.
void NanoMenu::musicOnCatFocus() {
    if (mMusicLoaded) return;
    if (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()
        && mPs3Cats[mPs3CatIdx].name == "Music") {
        musicEnsureLoaded();
    }
}

void NanoMenu::musicEnsureLoaded() {
    if (mMusicLoaded) return;
    mMusicLoaded = true;
    loadMusicConfig();
    mMusicPlayer.init();
    // Rebuild the Music column at the next settled root so the albums parsed from
    // nano_music.json appear immediately, independent of any rescan that follows.
    mMusicCatsStale = true;
    if (!mMusicScanRunning) musicScanAsync();   // always: default media dirs are scanned too
}

// ---------------------------------------------------------------------------
// Scanner: recurse every imported folder, collect audio files, probe metadata
// (mtime-cached against the current library), publish under a mutex.
// ---------------------------------------------------------------------------
static bool isM3uExt(const std::string& nameLower) {
    size_t dot = nameLower.rfind('.');
    if (dot == std::string::npos) return false;
    std::string ext = nameLower.substr(dot);
    return ext == ".m3u" || ext == ".m3u8";
}

static void scanDirRecursive(const std::string& dir,
                             std::vector<std::string>& outFiles, int depth,
                             std::vector<std::string>* outM3u = nullptr) {
    if (depth > 8) return;   // sane recursion bound
    DIR* d = opendir(dir.c_str());
    if (!d) return;
    std::vector<std::string> subdirs;
    struct dirent* e;
    while ((e = readdir(d)) != nullptr) {
        if (e->d_name[0] == '.') continue;
        std::string child = dir + "/" + e->d_name;
        struct stat st;
        if (stat(child.c_str(), &st) != 0) continue;
        if (S_ISDIR(st.st_mode)) { subdirs.push_back(child); continue; }
        if (!S_ISREG(st.st_mode) || st.st_size == 0) continue;
        std::string lower = e->d_name;
        for (auto& c : lower) if (c >= 'A' && c <= 'Z') c += 32;
        if (isAudioExt(lower)) outFiles.push_back(child);
        else if (outM3u && isM3uExt(lower)) outM3u->push_back(child);
    }
    closedir(d);
    std::sort(subdirs.begin(), subdirs.end(),
              [](const std::string& a, const std::string& b){ return strcasecmp(a.c_str(), b.c_str()) < 0; });
    for (const auto& s : subdirs) scanDirRecursive(s, outFiles, depth + 1, outM3u);
}

// Parse an .m3u/.m3u8 into a playlist. Resolves each entry relative to the m3u's
// directory (absolute paths kept as-is), keeps only entries that exist on disk and
// are audio. Skips blank lines and #EXTM3U/#EXTINF comment lines.
bool NanoMenu::parseM3u(const std::string& m3uPath, NanoMenu::MusicPlaylist& out) {
    FILE* f = fopen(m3uPath.c_str(), "rb");
    if (!f) return false;
    std::string dir = m3uPath.substr(0, m3uPath.rfind('/') + 1);
    char line[4096];
    while (fgets(line, sizeof(line), f)) {
        std::string s(line);
        // trim trailing CR/LF/space and leading space
        while (!s.empty() && (s.back() == '\n' || s.back() == '\r' || s.back() == ' ' || s.back() == '\t')) s.pop_back();
        size_t b = s.find_first_not_of(" \t");
        if (b == std::string::npos) continue;
        s = s.substr(b);
        if (s.empty() || s[0] == '#') continue;      // comment / directive
        for (auto& c : s) if (c == '\\') c = '/';     // windows separators
        std::string path = (s[0] == '/') ? s : (dir + s);
        std::string lower = path; for (auto& c : lower) if (c >= 'A' && c <= 'Z') c += 32;
        if (!isAudioExt(lower)) continue;
        struct stat st;
        if (stat(path.c_str(), &st) != 0 || !S_ISREG(st.st_mode)) continue;
        out.files.push_back(path);
    }
    fclose(f);
    out.m3uPath = m3uPath;
    out.name = stripExt(baseName(m3uPath));
    return !out.files.empty();
}

// True when the imported music folders are actually reachable. External storage
// (FUSE /storage/emulated/0) is not mounted at early boot, so a scan kicked before
// it is ready would see an empty tree and could wipe the saved library. We wait
// for boot completion AND at least one configured folder to be openable.
bool NanoMenu::musicStorageReady() const {
    // Defaults live on storage that mounts at/after boot, so always wait for boot.
    char bc[PROPERTY_VALUE_MAX] = {0};
    property_get("sys.boot_completed", bc, "0");
    if (bc[0] != '1') return false;
    std::vector<std::string> dirs = nanoMediaScanDirs(2, mMusicFolders);
    if (dirs.empty()) return true;   // nothing to scan (worker guards against wiping)
    for (const auto& f : dirs) {
        DIR* d = opendir(f.c_str());
        if (d) { closedir(d); return true; }
    }
    return false;
}

void NanoMenu::musicScanAsync() {
    if (mMusicScanRunning) return;
    if (!musicStorageReady()) {   // defer until external storage mounts (retried each frame)
        mMusicScanPending = true;
        ALOGI("NanoMenu: music scan deferred (storage not ready yet)");
        return;
    }
    mMusicScanPending = false;
    mMusicScanRunning = true;
    std::thread(&NanoMenu::musicScanThreadFunc, this).detach();
}

// User-triggered rescan (the Refresh row in the Music Folders screen).
void NanoMenu::musicRefresh() {
    musicEnsureLoaded();
    musicScanAsync();
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == MUSIC_FOLDER)
        buildMusicFoldersScreen(mPs3Stack.back());
}

void NanoMenu::musicScanThreadFunc() {
    // Snapshot the inputs so the worker never races the render thread.
    std::vector<std::string> folders = nanoMediaScanDirs(2, mMusicFolders);   // user folders + default media dirs
    // mtime cache: path -> existing track (carry metadata over if unchanged). When
    // the on-disk library predates the current metadata-parser version, drop the
    // cache so every track re-probes once and picks up the real container tags.
    bool forceReprobe = (mMusicCfgVersion < kMusicMetaVersion);
    std::vector<MusicTrack> cacheVec = mMusicTracks;
    std::map<std::string, const MusicTrack*> cache;
    if (!forceReprobe) for (const auto& t : cacheVec) cache[t.file] = &t;

    std::vector<std::string> files;
    std::vector<std::string> m3uFiles;
    for (const auto& f : folders) scanDirRecursive(f, files, 0, &m3uFiles);
    // Storage-not-ready guard: never publish an empty result that would wipe the
    // saved library when a source is merely not mounted yet. Publish empty only when
    // every scan dir is openable (mounted + genuinely empty), or there are no scan
    // dirs and nothing was saved before.
    if (files.empty()) {
        bool safe;
        if (folders.empty()) safe = cacheVec.empty();
        else {
            safe = true;
            for (const auto& f : folders) {
                DIR* d = opendir(f.c_str());
                if (!d) safe = false; else closedir(d);
            }
        }
        if (!safe) {
            mMusicScanRunning = false;
            mMusicScanPending = true;
            ALOGW("NanoMenu: music scan found no files and a source is unreadable; "
                  "deferring instead of wiping the library (storage not ready)");
            return;
        }
    }
    // Dedup absolute paths.
    std::sort(files.begin(), files.end());
    files.erase(std::unique(files.begin(), files.end()), files.end());

    std::vector<MusicTrack> results;
    results.reserve(files.size());
    for (const auto& path : files) {
        struct stat st;
        int64_t mt = (stat(path.c_str(), &st) == 0)
                         ? (int64_t)st.st_mtim.tv_sec * 1000000000LL + st.st_mtim.tv_nsec : 0;
        auto it = cache.find(path);
        if (it != cache.end() && it->second->mtime == mt && mt != 0) {
            results.push_back(*it->second);    // unchanged: reuse cached metadata (no re-probe)
            continue;
        }
        MusicTrack tr;
        tr.file = path;
        tr.mtime = mt;
        NanoAudioPlayer::Meta meta;
        if (NanoAudioPlayer::probe(path, meta)) {
            tr.title = meta.title;
            tr.artist = meta.artist;
            tr.album = meta.album;
            tr.codec = meta.codec;
            tr.durationSec = meta.durationSec;
        }
        if (tr.title.empty()) tr.title = stripExt(baseName(path));
        if (tr.artist.empty()) tr.artist = "Unknown Artist";
        if (tr.album.empty()) { tr.album = parentName(path); if (tr.album.empty()) tr.album = "Unknown Album"; }
        results.push_back(std::move(tr));
    }
    // Parse .m3u/.m3u8 into playlists (regenerated each scan) and note their directories
    // so the folder-fallback album is suppressed for those dirs (shown via the playlist).
    std::sort(m3uFiles.begin(), m3uFiles.end());
    m3uFiles.erase(std::unique(m3uFiles.begin(), m3uFiles.end()), m3uFiles.end());
    std::vector<MusicPlaylist> m3uPlaylists;
    std::set<std::string> m3uDirs;
    for (const auto& mp : m3uFiles) {
        MusicPlaylist pl;
        if (parseM3u(mp, pl)) {
            m3uDirs.insert(mp.substr(0, mp.rfind('/') + 1));
            m3uPlaylists.push_back(std::move(pl));
        }
    }
    // Recompute albumHidden fresh each scan (an m3u may have been added or removed).
    for (auto& tr : results) {
        std::string d = tr.file.substr(0, tr.file.rfind('/') + 1);
        tr.albumHidden = (m3uDirs.count(d) > 0);
    }
    {
        std::lock_guard<std::mutex> lk(mMusicScanMutex);
        mMusicScanResults = std::move(results);
        mMusicScanPlaylists = std::move(m3uPlaylists);
        mMusicScanReady = true;
    }
    mMusicScanRunning = false;
    ALOGI("NanoMenu: music scan finished (%zu files, %zu m3u playlists)", files.size(), m3uFiles.size());
}

void NanoMenu::musicDrainScanResults() {
    if (!mMusicScanReady) return;
    std::vector<MusicPlaylist> m3uPlaylists;
    {
        std::lock_guard<std::mutex> lk(mMusicScanMutex);
        mMusicTracks = std::move(mMusicScanResults);
        mMusicScanResults.clear();
        m3uPlaylists = std::move(mMusicScanPlaylists);
        mMusicScanPlaylists.clear();
        mMusicScanReady = false;
    }
    // Keep the user-created playlists; replace the m3u-derived ones with this scan's.
    std::vector<MusicPlaylist> merged;
    for (auto& p : mMusicPlaylists) if (p.m3uPath.empty()) merged.push_back(std::move(p));
    for (auto& p : m3uPlaylists) merged.push_back(std::move(p));
    mMusicPlaylists = std::move(merged);
    saveMusicConfig();
    mMusicCatsStale = true;   // rebuild the Music column at the settled root
    musicRemapQueueAfterReload();
}

// mMusicTracks is replaced wholesale by a finished rescan or an external reload (the other
// nano process editing nano_music.json); these run at the settled XMB root, which is exactly
// when a track can be playing minimized in the background. The play queue still holds indices
// into the OLD vector, so re-resolve them by file path: the right track keeps playing, the
// now-playing display and auto-advance stay correct, queued files that vanished are dropped,
// and mMpIdx follows the file that is currently playing. The live decoder is untouched (it
// already has its file open); this only repairs the queue bookkeeping.
void NanoMenu::musicRemapQueueAfterReload() {
    if (mMpQueue.empty() || mMpQueueFiles.size() != mMpQueue.size()) return;
    std::map<std::string, int> idxByFile;
    for (int i = 0; i < (int)mMusicTracks.size(); i++) idxByFile[mMusicTracks[i].file] = i;
    std::vector<int> newQueue;
    std::vector<std::string> newFiles;
    int newIdx = -1, survivingBeforeCur = 0;
    for (int i = 0; i < (int)mMpQueueFiles.size(); i++) {
        auto it = idxByFile.find(mMpQueueFiles[i]);
        if (it == idxByFile.end()) continue;            // file gone from the library
        if (i == mMpIdx) newIdx = (int)newQueue.size();
        else if (i < mMpIdx) survivingBeforeCur++;
        newQueue.push_back(it->second);
        newFiles.push_back(mMpQueueFiles[i]);
    }
    if (newQueue.empty()) return;                       // keep the old queue + the live decoder
    bool sizeChanged = (newQueue.size() != mMpQueue.size());
    if (newIdx < 0) newIdx = survivingBeforeCur;        // the playing file was removed: next survivor
    if (newIdx >= (int)newQueue.size()) newIdx = (int)newQueue.size() - 1;
    mMpQueue = std::move(newQueue);
    mMpQueueFiles = std::move(newFiles);
    mMpIdx = newIdx;
    if (sizeChanged) mpRebuildOrder();                  // queue positions changed; rebuild the order
}

// ---------------------------------------------------------------------------
// Album grouping (derived from mMusicTracks).
// ---------------------------------------------------------------------------
int64_t NanoMenu::musicAlbumNewestMtime(const std::string& album) const {
    int64_t newest = 0;
    for (const auto& t : mMusicTracks)
        if (!t.albumHidden && t.album == album && t.mtime > newest) newest = t.mtime;
    return newest;
}

std::vector<std::string> NanoMenu::musicAlbumNames() const {
    std::vector<std::string> names;
    std::set<std::string> seen;
    for (const auto& t : mMusicTracks)
        if (!t.albumHidden && seen.insert(t.album).second) names.push_back(t.album);
    auto nameLess = [](const std::string& a, const std::string& b){ return strcasecmp(a.c_str(), b.c_str()) < 0; };
    if (mMusicSortField == 1) {            // date: by the album's newest track mtime
        std::sort(names.begin(), names.end(), [&](const std::string& a, const std::string& b){
            int64_t ma = musicAlbumNewestMtime(a), mb = musicAlbumNewestMtime(b);
            if (ma != mb) return mMusicSortDir == 0 ? (ma > mb) : (ma < mb);
            return nameLess(a, b);
        });
    } else if (mMusicSortField == 2) {     // track count
        std::sort(names.begin(), names.end(), [&](const std::string& a, const std::string& b){
            size_t ca = musicAlbumTrackIndices(a).size(), cb = musicAlbumTrackIndices(b).size();
            if (ca != cb) return mMusicSortDir == 0 ? (ca > cb) : (ca < cb);
            return nameLess(a, b);
        });
    } else {                                // name: always ascending
        std::sort(names.begin(), names.end(), nameLess);
    }
    return names;
}

std::string NanoMenu::musicSortLabelCur() const {
    switch (mMusicSortField) {
        case 1:  return std::string("Date") + (mMusicSortDir == 0 ? " (newest)" : " (oldest)");
        case 2:  return std::string("Tracks") + (mMusicSortDir == 0 ? " (most)" : " (fewest)");
        default: return "Title";
    }
}

void NanoMenu::musicSortCycleY() {
    // 4 modes: Title, Date newest, Date oldest, Tracks most. Mirrors the video cycle.
    static const int kField[4] = {0, 1, 1, 2};
    static const int kDir[4]   = {1, 0, 1, 0};
    int cur = 0;
    for (int i = 0; i < 4; i++)
        if (kField[i] == mMusicSortField && (mMusicSortField == 0 || kDir[i] == mMusicSortDir)) { cur = i; break; }
    int nx = (cur + 1) % 4;
    mMusicSortField = kField[nx]; mMusicSortDir = kDir[nx];
    mMusicCatsStale = true;                 // rebuild the Music column in the new order
    photoShowBanner(musicSortLabelCur());   // reuse the shared sort banner overlay
}

std::vector<int> NanoMenu::musicAlbumTrackIndices(const std::string& album) const {
    std::vector<int> idx;
    for (size_t i = 0; i < mMusicTracks.size(); i++)
        if (!mMusicTracks[i].albumHidden && mMusicTracks[i].album == album) idx.push_back((int)i);
    // Keep tracks in FILE order (sort by path, the order the files appear on disk -
    // e.g. "01 - ...", "02 - ...") rather than re-sorting by the parsed tag title.
    // The displayed text comes from the metadata tags; only the ordering stays
    // as-is on disk (user 2026-06-15).
    std::sort(idx.begin(), idx.end(), [this](int a, int b){
        return strcasecmp(mMusicTracks[a].file.c_str(), mMusicTracks[b].file.c_str()) < 0;
    });
    return idx;
}

// ---------------------------------------------------------------------------
// Music-column content: the album folders prepended ahead of the firmware items
// (mirrors how Game content is prepended in buildPs3Cats).
// ---------------------------------------------------------------------------
void NanoMenu::buildMusicColumnItems(std::vector<Ps3Item>& out) {
    GLuint folderNmap = nmapForIcon(62);
    std::vector<std::string> albums = musicAlbumNames();
    for (size_t a = 0; a < albums.size(); a++) {
        std::vector<int> tr = musicAlbumTrackIndices(albums[a]);
        Ps3Item it; it.label = albums[a]; it.kind = PS3_MUSIC_ALBUM; it.a = (int)a;
        char v[32]; snprintf(v, sizeof(v), "%zu Tracks", tr.size()); it.value = v;
        it.iconTex = 0; it.nmapTex = folderNmap; it.iconR = it.iconG = it.iconB = 1.0f;
        out.push_back(it);
    }
}

void NanoMenu::buildMusicAlbumSubmenu(int albumIdx, Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = 0;
    std::vector<std::string> albums = musicAlbumNames();
    if (albumIdx < 0 || albumIdx >= (int)albums.size()) { out.title = "Album"; return; }
    out.title = albums[albumIdx];
    GLuint noteNmap = nmapForIcon(37);
    for (int ti : musicAlbumTrackIndices(albums[albumIdx])) {
        const MusicTrack& t = mMusicTracks[ti];
        Ps3Item it; it.label = t.title; it.kind = PS3_MUSIC_TRACK; it.a = ti;
        it.desc = t.artist + " / " + t.album;
        it.iconTex = 0; it.nmapTex = noteNmap; it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
}

void NanoMenu::buildMusicPlaylistsScreen(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.title = "Playlists"; out.screenKind = 0;
    GLuint noteNmap = nmapForIcon(37);
    { Ps3Item it; it.label = "Create New Playlist"; it.kind = PS3_MUSIC_PL_NEW;
      it.iconTex = 0; it.nmapTex = nmapForIcon(50); it.iconR = it.iconG = it.iconB = 1.0f;
      out.items.push_back(it); }
    for (size_t p = 0; p < mMusicPlaylists.size(); p++) {
        Ps3Item it; it.label = mMusicPlaylists[p].name; it.kind = PS3_MUSIC_PLAYLIST; it.a = (int)p;
        char v[32]; snprintf(v, sizeof(v), "%zu Tracks", mMusicPlaylists[p].files.size()); it.value = v;
        it.iconTex = 0; it.nmapTex = noteNmap; it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
}

void NanoMenu::buildMusicPlaylistSubmenu(int plIdx, Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = 0;
    if (plIdx < 0 || plIdx >= (int)mMusicPlaylists.size()) { out.title = "Playlist"; return; }
    const MusicPlaylist& pl = mMusicPlaylists[plIdx];
    out.title = pl.name;
    GLuint noteNmap = nmapForIcon(37);
    for (const auto& file : pl.files) {
        int ti = -1;
        for (size_t i = 0; i < mMusicTracks.size(); i++) if (mMusicTracks[i].file == file) { ti = (int)i; break; }
        if (ti < 0) continue;
        const MusicTrack& t = mMusicTracks[ti];
        Ps3Item it; it.label = t.title; it.kind = PS3_MUSIC_TRACK; it.a = ti;
        it.desc = t.artist + " / " + t.album;
        it.iconTex = 0; it.nmapTex = noteNmap; it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
}

// ---------------------------------------------------------------------------
// Folder import: the music folders screen + the picker reuse (mFolderPickTarget).
// ---------------------------------------------------------------------------
void NanoMenu::buildMusicFoldersScreen(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.title = "Music Folders"; out.screenKind = MUSIC_FOLDER;
    { Ps3Item it; it.label = "Add Folder..."; it.kind = PS3_GS_ADDFOLDER;
      it.iconTex = 0; it.nmapTex = nmapForIcon(50); it.iconR = it.iconG = it.iconB = 1.0f;
      out.items.push_back(it); }
    { Ps3Item it; it.label = mMusicScanRunning ? "Refreshing..." : "Refresh";
      it.kind = PS3_MUSIC_REFRESH;
      it.value = mMusicScanRunning ? "" : "Rescan music folders";
      it.iconTex = 0; it.nmapTex = nmapForIcon(8); it.iconR = it.iconG = it.iconB = 1.0f;
      out.items.push_back(it); }
    for (size_t i = 0; i < mMusicFolders.size(); i++) {
        int cnt = 0;
        for (const auto& t : mMusicTracks)
            if (t.file.compare(0, mMusicFolders[i].size(), mMusicFolders[i]) == 0) cnt++;
        Ps3Item it; it.label = mMusicFolders[i]; it.kind = PS3_MUSIC_FOLDER_ROW; it.a = (int)i;
        char v[24]; snprintf(v, sizeof(v), "%d tracks", cnt); it.value = v;
        it.iconTex = 0; it.nmapTex = nmapForIcon(62); it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
    if (mMusicFolders.empty()) {
        Ps3Item it; it.label = mMusicScanRunning ? "Scanning..." : "No music folders added yet";
        it.kind = PS3_GS_FIELD; it.a = -1;
        it.iconTex = 0; it.nmapTex = 0; it.iconR = it.iconG = it.iconB = 0.55f;
        out.items.push_back(it);
    }
}

void NanoMenu::musicOpenFolders() {
    musicEnsureLoaded();
    mFolderPickTarget = 1;   // route the folder browser's "Select" to the music library
    std::vector<Ps3Item> parentSnap = mPs3Stack.empty() ? std::vector<Ps3Item>() : mPs3Stack.back().items;
    int parentSel = mPs3Stack.empty() ? 0 : mPs3Stack.back().sel;
    Ps3Level lvl; buildMusicFoldersScreen(lvl);
    mPs3Stack.push_back(lvl);
    mPs3SubParentItems = std::move(parentSnap);
    mPs3SubParentIdx = parentSel; mPs3SubChildItems = mPs3Stack.back().items;
    mPs3SubDir = 1; mPs3SubAnimStart = mEffectTime; mPs3SubAnim = 0.0f;
    mPs3AnimItem = 0.0f; mPs3ItemAnimStart = -1.0f;
}

void NanoMenu::musicFolderSelect(const std::string& path) {
    if (path.empty()) return;
    for (const auto& f : mMusicFolders) if (f == path) {
        if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_FOLDERBROWSE) mPs3Stack.pop_back();
        return;
    }
    mMusicFolders.push_back(path);
    ALOGI("NanoMenu: added music folder %s", path.c_str());
    saveMusicConfig();
    musicScanAsync();
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_FOLDERBROWSE) mPs3Stack.pop_back();
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == MUSIC_FOLDER)
        buildMusicFoldersScreen(mPs3Stack.back());
}

void NanoMenu::musicRemoveFolder(int idx) {
    if (idx < 0 || idx >= (int)mMusicFolders.size()) return;
    mMusicFolders.erase(mMusicFolders.begin() + idx);
    saveMusicConfig();
    musicScanAsync();
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == MUSIC_FOLDER)
        buildMusicFoldersScreen(mPs3Stack.back());
}

// ---------------------------------------------------------------------------
// Playlists (mpCreatePlaylist / mpAddTracksToPlaylist).
// ---------------------------------------------------------------------------
void NanoMenu::musicCreatePlaylist(const std::string& name) {
    if (name.empty()) return;
    for (const auto& p : mMusicPlaylists) if (p.name == name) return;   // dedup by name
    MusicPlaylist pl; pl.name = name;
    mMusicPlaylists.push_back(std::move(pl));
    saveMusicConfig();
}

void NanoMenu::musicAddTrackToPlaylist(int plIdx, const std::string& file) {
    if (plIdx < 0 || plIdx >= (int)mMusicPlaylists.size() || file.empty()) return;
    auto& files = mMusicPlaylists[plIdx].files;
    for (const auto& f : files) if (f == file) return;   // dedup
    files.push_back(file);
    saveMusicConfig();
}

// ---------------------------------------------------------------------------
// Playback: open the player on a list of track items, repeat/shuffle/order, and
// the per-frame auto-advance. The Now-Playing UI (renderMusicPlayer, control
// panel) is added in Phase 3; this owns the audio + the queue.
// ---------------------------------------------------------------------------
void NanoMenu::openMusicPlayer(const std::vector<Ps3Item>& list, int listSel) {
    musicEnsureLoaded();
    // Build the queue from every track row in the current list; remember where the
    // selected row lands so playback starts there.
    mMpQueue.clear();
    mMpQueueFiles.clear();
    int start = 0;
    for (int i = 0; i < (int)list.size(); i++) {
        if (list[i].kind != PS3_MUSIC_TRACK) continue;
        if (i == listSel) start = (int)mMpQueue.size();
        mMpQueue.push_back(list[i].a);   // mMusicTracks index
        mMpQueueFiles.push_back((list[i].a >= 0 && list[i].a < (int)mMusicTracks.size())
                                ? mMusicTracks[list[i].a].file : std::string());
    }
    if (mMpQueue.empty()) return;
    mMpIsRadio = false;        // a local-track session (clears any prior radio session)
    mMpRadioQueue.clear();
    mMpIdx = (start >= 0 && start < (int)mMpQueue.size()) ? start : 0;
    mMpRepeat = 0;
    mMpShuffle = false;
    mpRebuildOrder();
    mMpActive = true;
    mpPlayCurrent();
}

// Internet Radio: open the music player on a live-station queue (the audio analog of
// openIptvStream). The queue is every station row in the current list; playback starts at the
// selected row. mMpIsRadio routes mpPlayCurrent/render/step down the live-stream path (no seek
// bar, no auto-advance; L/R step stations). Background playback + visualizers come for free.
void NanoMenu::openRadioStation(const std::vector<Ps3Item>& list, int listSel) {
    std::vector<RadioStation> q;
    int start = 0;
    for (int i = 0; i < (int)list.size(); i++) {
        if (list[i].kind != PS3_RADIO_STATION) continue;
        if (i == listSel) start = (int)q.size();
        RadioStation s; s.name = list[i].label; s.url = list[i].payloadStr; s.group = list[i].desc;
        q.push_back(std::move(s));
    }
    if (q.empty()) return;
    mMpRadioQueue = std::move(q);
    mMpQueue.clear(); mMpQueueFiles.clear();   // the radio session does not use the track queue
    mMpIsRadio = true;
    mMpIdx = (start >= 0 && start < (int)mMpRadioQueue.size()) ? start : 0;
    mMpRepeat = 0;
    mMpShuffle = false;
    mpRebuildOrder();
    mMpActive = true;
    mMpEnterT = 0.0f;          // play the presence fade-in like a fresh open
    mpPlayCurrent();
}

void NanoMenu::closeMusicPlayer() {
    mMpActive = false;
    mMpSeekPending = false;
    // Tear the decoder down on the audio worker (serialized with any in-flight op so
    // they never race on the decode thread). Drop pending commands first so a stale
    // play/open can't fire after close.
    if (mMpAudioStarted) {
        { std::lock_guard<std::mutex> lk(mMpAudioMutex);
          mMpAudioQueue.clear();
          mMpAudioQueue.push_back({MpAudioCmd::Release, 0.0, std::string()}); }
        mMpAudioCv.notify_one();
    } else {
        mMusicPlayer.release();   // worker never started; safe to release directly
    }
    mMpQueue.clear(); mMpQueueFiles.clear(); mMpOrder.clear(); mMpIdx = 0;
    mMpIsRadio = false; mMpRadioQueue.clear();   // end any Internet Radio session
    mpFreeArt();              // free the cached album-art texture
    ps3canyon::shutdown();    // free the Canyon GL objects (lazy-reloaded next time)
    ps3mpglobe::shutdown();   // free the Globe GL objects
    mMpCanyonAlpha = 0.0f;
    mMpGlobeAlpha = 0.0f;
}

// Hide the Now-Playing screen but KEEP the audio playing in the background (the
// queue + track index persist), so the user can leave the player - and, in the
// overlay, resume their app - with the music still going. The clock-bar indicator
// shows it is playing and the Quick Menu "Resume Audio Player" reopens this screen.
// Only the visualizer GL is released (it re-inits on resume); the audio is untouched.
void NanoMenu::minimizeMusicPlayer() {
    mMpActive = false;
    mMpCpOpen = false; mMpCpClosing = false; mMpVolSub = false;
    // Do NOT shut down the Canyon/Globe GL here: let musicTick ramp their alpha to 0 over
    // the ~1.0s leave fade (matching the bar) so the visualizer fades out instead of
    // cutting, then free the GL once faded (Guard A in musicTick). If the loop is occluded
    // by a foreground app mid-fade, the park point frees it (Guard B / freeMusicVisGl), so
    // the GL never lingers behind a running app.
}

// Free the Canyon/Globe visualizer GL immediately (idempotent; ready()-gated). Called at
// the occlusion park point so a leave fade interrupted by a foreground app cannot leave
// the visualizer GL allocated while the app runs.
void NanoMenu::freeMusicVisGl() {
    if (ps3canyon::ready())  { ps3canyon::shutdown();  mMpCanyonAlpha = 0.0f; }
    if (ps3mpglobe::ready()) { ps3mpglobe::shutdown(); mMpGlobeAlpha  = 0.0f; }
}

// Reopen the Now-Playing screen on the live queue (Quick Menu "Resume Audio Player").
void NanoMenu::resumeMusicPlayer() {
    if (mMpQueue.empty() && !(mMpIsRadio && !mMpRadioQueue.empty())) return;
    if (!mMpIsRadio) musicEnsureLoaded();
    mMpActive = true;
    mMpEnterT = 0.0f;                 // replay the presence fade-in
    if (mMpVis == 1) ps3canyon::init();   // canyon was freed on minimize
    else if (mMpVis == 2) ps3mpglobe::init();
}

// Enqueue an audio-control command for the worker thread. Render-thread-safe and
// non-blocking: the worker runs the (possibly blocking) NanoAudio op so the render
// loop never stalls on the audio server / codec. Lazy-starts the worker.
void NanoMenu::mpAudioCmd(MpAudioCmd cmd, double arg, const std::string& path, bool radio) {
    if (!mMpAudioStarted) {
        mMpAudioStarted = true;
        std::thread(&NanoMenu::mpAudioWorker, this).detach();
    }
    {
        std::lock_guard<std::mutex> lk(mMpAudioMutex);
        // Coalesce repeated Seek / OpenPlay so rapid scrubbing or track-skipping does
        // not pile up decoder restarts on the worker (only the latest target matters).
        if ((cmd == MpAudioCmd::Seek || cmd == MpAudioCmd::OpenPlay) &&
            !mMpAudioQueue.empty() && mMpAudioQueue.back().cmd == cmd) {
            mMpAudioQueue.back().arg = arg;
            mMpAudioQueue.back().path = path;
            mMpAudioQueue.back().radio = radio;
        } else {
            mMpAudioQueue.push_back({cmd, arg, path, radio});
        }
    }
    mMpAudioCv.notify_one();
}

void NanoMenu::mpAudioWorker() {
    for (;;) {
        MpAudioReq req;
        {
            std::unique_lock<std::mutex> lk(mMpAudioMutex);
            mMpAudioCv.wait(lk, [this] { return !mMpAudioQueue.empty(); });
            req = mMpAudioQueue.front();
            mMpAudioQueue.pop_front();
        }
        switch (req.cmd) {
            case MpAudioCmd::Play:  mMusicPlayer.play();  break;
            case MpAudioCmd::Pause: mMusicPlayer.pause(); break;
            case MpAudioCmd::Stop:  mMusicPlayer.stop();  break;
            case MpAudioCmd::Seek:  mMusicPlayer.seek(req.arg); break;
            case MpAudioCmd::OpenPlay:
                if (!req.path.empty() && mMusicPlayer.open(req.path, -1, req.radio)) mMusicPlayer.play();
                break;
            case MpAudioCmd::Release: mMusicPlayer.release(); break;
        }
    }
}

void NanoMenu::mpPlayCurrent() {
    mMpSeekPending = false;   // drop any in-flight scrub so it can't apply to the new track
    mMpAdvancing = true;      // a track is loading (async); suppress auto-advance until ended() clears
    mMpRadioErrShown = false; // re-arm the radio open-error message for this station
    if (mMpIsRadio) {         // Internet Radio: open the station URL as a continuous stream
        if (mMpIdx < 0 || mMpIdx >= (int)mMpRadioQueue.size()) return;
        mpAudioCmd(MpAudioCmd::OpenPlay, 0.0, mMpRadioQueue[mMpIdx].url, /*radio=*/true);
        return;
    }
    if (mMpIdx < 0 || mMpIdx >= (int)mMpQueue.size()) return;
    int ti = mMpQueue[mMpIdx];
    if (ti < 0 || ti >= (int)mMusicTracks.size()) return;
    mpAudioCmd(MpAudioCmd::OpenPlay, 0.0, mMusicTracks[ti].file);   // open()+play() off the render thread
}

void NanoMenu::mpRebuildOrder() {
    mMpOrder.clear();
    int n = mMpIsRadio ? (int)mMpRadioQueue.size() : (int)mMpQueue.size();
    for (int i = 0; i < n; i++) mMpOrder.push_back(i);
    if (mMpShuffle && n > 1) {
        for (int i = n - 1; i > 0; i--) { int j = rand() % (i + 1); std::swap(mMpOrder[i], mMpOrder[j]); }
        // Force the current track first so play continues from where you are.
        auto it = std::find(mMpOrder.begin(), mMpOrder.end(), mMpIdx);
        if (it != mMpOrder.end()) std::iter_swap(mMpOrder.begin(), it);
    }
}

void NanoMenu::mpStep(int dir, bool isAuto) {
    int n = (int)mMpOrder.size();
    if (n == 0) return;
    int pos = 0;
    for (int i = 0; i < n; i++) if (mMpOrder[i] == mMpIdx) { pos = i; break; }
    pos += dir;
    if (pos < 0) pos = n - 1;                       // manual wrap backward
    else if (pos >= n) {
        if (isAuto) {
            if (mMpRepeat == 0) { mpAudioCmd(MpAudioCmd::Pause); return; }   // off: stop at end
            pos = 0;                                                // all: loop (+ reshuffle)
            if (mMpShuffle) { mMpIdx = mMpOrder[0]; mpRebuildOrder(); pos = 0; }
        } else {
            pos = 0;                                                // manual wrap forward
        }
    }
    mMpIdx = mMpOrder[pos];
    mpPlayCurrent();
}

void NanoMenu::mpNext() { mpStep(1, false); }
void NanoMenu::mpPrev() {
    if (mMusicPlayer.position() > 3.0) { mpAudioCmd(MpAudioCmd::Seek, 0.0); return; }   // restart current
    mpStep(-1, false);
}

void NanoMenu::musicTick() {
    // Drive the XMB Waves background morph: target the music visualizer only while the
    // player is open on the Waves visualizer (vis 0). ps3bg ramps the blend internally
    // (~1s) so the leave transition still plays after the player closes; setting it
    // every frame (here, before the !mMpActive early-out) covers enter AND leave.
    ps3bg::setMusicVisTarget((mMpActive && mMpVis == 0) ? 1.0f : 0.0f);
    float dt = mFrameDt;
    if (dt < 0.0f || dt > 0.2f) dt = 0.016f;
    // Player ENTER/LEAVE cross-fade, 1:1 with the web (drawBG mpEnterRaw/mpChromeRaw):
    // the now-playing bar fades over ~1.0s (linear progress -> smoothstep) while the XMB
    // chrome fades over ~0.4s (mMpChromeT, scaled into the cold-boot reveal in
    // renderPs3Xmb). Enter: bar IN, chrome OUT. Leave via minimize (Circle keeps the
    // audio + queue, so the bar can fade out live): bar OUT, chrome IN. A full close
    // clears the queue and snaps to the instant branch (the bar cannot fade without the
    // track data).
    if (mMpActive) {
        mMpEnterRaw = fminf(1.0f, mMpEnterRaw + dt / 1.0f);   // bar IN  ~1.0s
        mMpChromeT  = fmaxf(0.0f, mMpChromeT  - dt / 0.4f);   // chrome OUT ~0.4s
    } else if (!mMpQueue.empty()) {
        mMpEnterRaw = fmaxf(0.0f, mMpEnterRaw - dt / 1.0f);   // bar OUT ~1.0s (minimize)
        mMpChromeT  = fminf(1.0f, mMpChromeT  + dt / 0.4f);   // chrome IN ~0.4s
    } else {
        mMpEnterRaw = 0.0f;                                   // closed (queue cleared): instant
        mMpChromeT  = 1.0f;
    }
    mMpEnterT = mMpEnterRaw * mMpEnterRaw * (3.0f - 2.0f * mMpEnterRaw);   // smoothstep
    float fi = mMpFullInfo ? 1.0f : 0.0f;
    mMpFullInfoT += (fi - mMpFullInfoT) * fminf(1.0f, dt * 8.0f);
    // Waves<->Canyon visualizer crossfade: ramp the Canyon alpha toward 1 while the
    // Canyon is the active visualizer, 0 otherwise, over ~0.5s. As it ramps up the
    // wave morph (above) ramps down (vis != 0), so they dissolve into each other.
    // 0.5s for the active Square-cycle crossfade (web mpVisXfade); 1.0s on leave so the
    // visualizer fades out over the same arc as the bar (mMpEnterT leave fade).
    float cStep = dt / (mMpActive ? 0.5f : 1.0f);
    float canyonTarget = (mMpActive && mMpVis == 1) ? 1.0f : 0.0f;
    if (mMpCanyonAlpha < canyonTarget) mMpCanyonAlpha = fminf(canyonTarget, mMpCanyonAlpha + cStep);
    else if (mMpCanyonAlpha > canyonTarget) mMpCanyonAlpha = fmaxf(canyonTarget, mMpCanyonAlpha - cStep);
    float globeTarget = (mMpActive && mMpVis == 2) ? 1.0f : 0.0f;
    if (mMpGlobeAlpha < globeTarget) mMpGlobeAlpha = fminf(globeTarget, mMpGlobeAlpha + cStep);
    else if (mMpGlobeAlpha > globeTarget) mMpGlobeAlpha = fmaxf(globeTarget, mMpGlobeAlpha - cStep);
    // Leave fade complete: free a faded visualizer's GL so nothing lingers in the
    // background (lazy - re-inits on the next Square cycle). ready()-gated => idempotent.
    if (!mMpActive) {
        if (mMpCanyonAlpha <= 0.0f && ps3canyon::ready()) ps3canyon::shutdown();
        if (mMpGlobeAlpha  <= 0.0f && ps3mpglobe::ready()) ps3mpglobe::shutdown();
    }

    // The following run whether or not the Now-Playing screen is shown, so playback
    // keeps going while the player is minimized into the background.
    // Quick Menu "Resume Audio Player" visibility: rebuild the categories when audio
    // starts or stops so the item appears/disappears.
    bool audioLoaded = (!mMpQueue.empty() || (mMpIsRadio && !mMpRadioQueue.empty()))
                       && (mMusicPlayer.isPlaying() || mMusicPlayer.isPaused());
    if (audioLoaded != mMusicResumeShown) { mMusicResumeShown = audioLoaded; mPs3CatsStale = true; }
    // Commit a debounced scrub seek once input has settled (~0.22s). The heavy seek
    // (it joins+restarts the decoder) runs on the audio worker, so the render thread
    // never blocks; debouncing also avoids per-press churn while scrubbing.
    if (mMpSeekPending && (mEffectTime - mMpSeekInputT) >= 0.22f) {
        mMpSeekPending = false;
        mpAudioCmd(MpAudioCmd::Seek, mMpSeekTarget);
    }
    // Auto-advance at end of track (repeat-one replays). Runs even when minimized.
    // mMpAdvancing gates against the async open: ended() stays true until the worker
    // loads the next track, so only fire once per end (cleared when ended() clears).
    if (mMpAdvancing && !mMusicPlayer.ended()) mMpAdvancing = false;
    if (!mMpQueue.empty() && mMusicPlayer.ended() && !mMpAdvancing) {
        mMpAdvancing = true;
        if (mMpRepeat == 2) mpPlayCurrent();
        else mpStep(1, true);
    }
    // Internet Radio: a station the platform extractor cannot open (some Ogg/FLAC/HE-AAC raw
    // streams) would otherwise sit at a silent 0:00 - surface a clear one-shot message so the
    // user knows to try another station (Left/Right) or back out.
    if (mMpIsRadio && mMpActive && !mMpAdvancing && !mMpRadioErrShown && mMusicPlayer.openFailed()) {
        mMpRadioErrShown = true;
        mpShowMsg("Could not open this station.", 1800.0f, 0);
    }

    if (!mMpActive) return;
    // Full-screen message chain (Deleting... -> Delete completed. -> mpNext).
    if (mMpMsgStart >= 0.0f && (mEffectTime - mMpMsgStart) >= mMpMsgDur / 1000.0f) {
        int then = mMpMsgThen; mMpMsgStart = -1.0f; mMpMsg.clear(); mMpMsgThen = 0;
        if (then == 1) mpShowMsg("Delete completed.", 900.0f, 2);
        else if (then == 2) mpNext();
    }
}

// ---------------------------------------------------------------------------
// Now-Playing render (1:1 web drawMusicPlayer) + the TRIANGLE control panel.
// Virtual VW/VH == the web V.W/V.H; map with ps3::devX/devY/devS + XCF, and
// ps3::fontScale/baselineToTopY for fonts at a web baseline. The wave background
// is already drawn by render() before renderPs3Xmb; this draws the bar over it.
// ---------------------------------------------------------------------------
static inline float DXP(float f) { return ps3::devX(ps3::XCF(ps3::VW * f)); }   // abs X (web XCF)
static inline float DXD(float f) { return ps3::devS(ps3::XCF(ps3::VW * f)); }   // X delta (web XCF)
static inline float DYP(float f) { return ps3::devY(ps3::VH * f); }            // abs Y
static inline float SZ(float f)  { return ps3::devS(ps3::VH * f); }            // size from VH frac
static inline float FSZ(float px){ return ps3::fontScale(px); }                // font scale for NNpx
static inline float TOPY(float baseFrac, float px) {
    return ps3::baselineToTopY(ps3::devY(ps3::VH * baseFrac), ps3::fontScale(px));
}
// Music-player UI scale: double every element on small panels (shorter side
// <=768px, i.e. the Brick and below) for readability; 1x on larger displays.
static inline float mpUiScale(int w, int h) { return ((w < h ? w : h) <= 768) ? 2.0f : 1.0f; }
// Baseline-Y drawText helper: position by device baseline-Y (not a normalized
// fraction) so the scaled bottom bar can lay text out relative to the jacket.


static std::string mpFmtTime(double sec) {
    if (sec < 0 || !(sec == sec)) sec = 0;
    int s = (int)sec; int h = s / 3600; int m = (s % 3600) / 60; int ss = s % 60;
    char b[16]; snprintf(b, sizeof(b), "%02d:%02d:%02d", h, m, ss); return b;
}

// MP_CP control-panel table (web index.html:12089). act + grid (gx,gy) + the
// normal/shadow/focus audioplayer icon indices.
struct MpCp { const char* act; const char* label; float gx; float gy; int n; int s; int f; };
static const MpCp kMpCp[] = {
    {"vol",     "Volume Control",  -2,  1, 35, 49, 63},
    {"vis",     "Visual Player",   -1,  1, 29, 43, 57},
    {"addpl",   "Add to Playlist",  0,  1, 31, 45, 59},
    {"del",     "Delete",           1,  1, 30, 44, 58},
    {"disp",    "Display",          2,  1, 34, 48, 62},
    {"prev",    "Previous",        -3,  0, 36, 50, 64},
    {"next",    "Next",            -2,  0, 37, 51, 65},
    {"rew",     "Fast Reverse",    -1,  0, 41, 55, 69},
    {"ff",      "Fast Forward",     0,  0, 42, 56, 70},
    {"play",    "Play",             1,  0, 38, 52, 66},
    {"pause",   "Pause",            2,  0, 39, 53, 67},
    {"stop",    "Stop",             3,  0, 40, 54, 68},
    {"repeat",  "Repeat",        -0.6f, -1, 32, 46, 60},
    {"shuffle", "Shuffle",         0.8f, -1, 33, 47, 61},
};
static const int kMpCpCount = (int)(sizeof(kMpCp) / sizeof(kMpCp[0]));
static int mpCpDefault() { for (int i = 0; i < kMpCpCount; i++) if (!strcmp(kMpCp[i].act, "play")) return i; return 0; }

void NanoMenu::mpShowMsg(const std::string& text, float durMs, int then) {
    mMpMsg = text; mMpMsgStart = mEffectTime; mMpMsgDur = durMs; mMpMsgThen = then;
}

// The extra music visualizers are the full-screen procedural wallpaper effects:
// Plasma, Fire, Aurora, Ripple, Checkerboard, Spiral and the XMB ribbon. These are
// stateless shaders, so they switch cleanly with no particle-pool conflict against
// the home wallpaper. mMpVis 3.. select these in order, so Square cycles XMB Waves,
// Canyon, Globe, then each effect. (The particle effects 1..10 stay home wallpapers
// only - they would fight the shared particle pool with the home wallpaper.)
static const int kMpExtraVis[] = {11, 16, 17, 18, 19, 20, 21};
static const int kMpExtraVisCount = (int)(sizeof(kMpExtraVis) / sizeof(kMpExtraVis[0]));

int NanoMenu::mpVisCount() const { return 3 + kMpExtraVisCount; }

int NanoMenu::mpVisEffectId() const {
    int i = mMpVis - 3;
    if (i < 0 || i >= kMpExtraVisCount) return 0;
    return kMpExtraVis[i];
}

void NanoMenu::mpCycleVis() {
    mMpVis = (mMpVis + 1) % mpVisCount();   // 0 Waves, 1 Canyon, 2 Globe, 3.. wallpaper effects
    if (mMpVis == 0)      mMpBanner = "XMB Waves";
    else if (mMpVis == 1) mMpBanner = "Canyon";
    else if (mMpVis == 2) mMpBanner = "Globe";
    else                  mMpBanner = kEffectNames[mpVisEffectId()];
    mMpBannerStart = mEffectTime;
    // Lazy-init the Canyon on first switch to it, and restart its flythrough.
    if (mMpVis == 1) { ps3canyon::init(); ps3canyon::reset(); }
    // Lazy-init the real Globe renderer and restart its scene cycle.
    else if (mMpVis == 2) { ps3mpglobe::init(); ps3mpglobe::reset(); }
    // Particle wallpaper effects (1..10) need their pool seeded when selected.
    else if (mMpVis >= 3) { int e = mpVisEffectId(); if (e >= 1 && e <= 10) initEffects(); }
}

void NanoMenu::renderMusicPlayer() {
    // Resolve the current item's display data: a local track for the Internet Radio session
    // (synthesized from the station + the live stream's codec), else the real library track.
    MusicTrack radioT;
    const MusicTrack* tp = nullptr;
    int ti = -1;
    const bool live = mMpIsRadio;
    if (live) {
        if (mMpRadioQueue.empty()) { mMpActive = false; return; }
        if (mMpIdx < 0 || mMpIdx >= (int)mMpRadioQueue.size()) return;
        const RadioStation& s = mMpRadioQueue[mMpIdx];
        radioT.title = s.name;
        radioT.artist = s.group.empty() ? "Internet Radio" : s.group;
        NanoAudioPlayer::Meta m = mMusicPlayer.meta();
        radioT.codec = m.codec;
        tp = &radioT;
    } else {
        if (mMpQueue.empty()) { mMpActive = false; return; }
        ti = (mMpIdx >= 0 && mMpIdx < (int)mMpQueue.size()) ? mMpQueue[mMpIdx] : -1;
        if (ti < 0 || ti >= (int)mMusicTracks.size()) return;
        tp = &mMusicTracks[ti];
    }
    const MusicTrack& t = *tp;
    float enter = mMpEnterT;
    bool panelUp = mMpCpOpen || mMpCpClosing;

    // Canyon visualizer: drawn over the (wave) background and under the Now-Playing
    // bar, cross-faded in by mMpCanyonAlpha. The wave morph fades out as this fades
    // in (musicTick), so the two visualizers dissolve into each other. Audio-reactive
    // via the live FFT bands (bass -> camera speed, mid -> tonemap exposure).
    if (mMpCanyonAlpha > 0.001f) {
        NanoAudioPlayer::Bands ab; mMusicPlayer.getBands(ab);
        nanoaudio::Bands cb; cb.bass = ab.bass; cb.mid = ab.mid; cb.treble = ab.treble;
        ps3canyon::render(mWidth, mHeight, sDrmRotMat, mMpCanyonAlpha, mFrameDt, cb);
    }
    // Globe visualizer: the real XMB web globe (NanoMenuMusicGlobe / ps3mpglobe) - a
    // ray-marched earth + atmosphere + sun + bloom, replaying the real firmware scene
    // camera paths. Drawn over the (faded) background / Canyon and under the bar,
    // cross-faded by mMpGlobeAlpha. Audio-reactive bands feed the renderer.
    if (mMpGlobeAlpha > 0.001f) {
        NanoAudioPlayer::Bands gb; mMusicPlayer.getBands(gb);
        nanoaudio::Bands cb; cb.bass = gb.bass; cb.mid = gb.mid; cb.treble = gb.treble;
        ps3mpglobe::render(mWidth, mHeight, sDrmRotMat, mMpGlobeAlpha, mFrameDt, cb);
    }

    mTextOutlineMode = 1;

    // The Now-Playing bar (jacket / title / artist / seek cluster) renders at 1.5x on
    // small panels (<=768) for readability; the control-panel icon grid keeps the 2x
    // bump (user request). The info-cluster left edge (clX below) widens at 1.5x so the
    // bigger elapsed/total time strings do not overlap on the seek line.
    float mpUi = ((mWidth < mHeight ? mWidth : mHeight) <= 768) ? 1.5f : 1.0f;

    // jacket cover: bottom edge fixed at 0.912 (the 1x web position) so the bar grows
    // UPWARD when scaled. Title/artist baselines are taken relative to the jacket so
    // their spacing scales too (exactly 0.866 / 0.900 at 1x).
    float jsz = SZ(0.085f * mpUi), ax = DXP(0.066f), ay = DYP(0.912f) - jsz;
    // Album art: per-track image, else per-folder cover, else the note placeholder.
    // Internet Radio has no local file - always the note placeholder.
    GLuint jac = (ti >= 0) ? mpTrackArt(ti) : 0; if (!jac) jac = mpJacket();
    if (jac) drawIconTex(jac, ax, ay, jsz, jsz, 1.0f, 1.0f, 1.0f, enter);

    float tx = ax + jsz + DXD(0.013f * mpUi);
    float titleBaseY = ay + jsz * 0.46f;     // == devY(0.866) at 1x
    float artistBaseY = ay + jsz * 0.86f;    // == devY(0.900) at 1x

    // Right info cluster geometry (used to clip the title): wider at the 1.5x bar so
    // the larger elapsed/total time strings fit without overlapping. Defined here so
    // the title never runs into it.
    float clX = DXP(mpUi > 1.2f ? 0.62f : 0.738f), clEnd = DXP(0.940f);

    // The title AND the artist/album subtitle both clip to the band LEFT of the
    // right info-cluster and MARQUEE-BOUNCE (smooth ping-pong) when wider than it,
    // so neither spills into the time/codec/seek cluster (user: the subtitle was
    // overlapping the seek bar with real, long metadata). 1:1 with the web title
    // marquee, now applied to both lines.
    float titleRight = mMpFullInfo ? (clX - DXD(0.015f)) : DXP(0.955f);
    float bandW = titleRight - tx;
    // Smooth ping-pong offset for text wider than maxW (web HOLD/SPEED easing).
    auto marqueeOff = [&](float w, float maxW) -> float {
        if (w <= maxW || maxW <= 0) return 0.0f;
        float over = w - maxW;
        const float HOLD = 1100.0f, SPEED = 55.0f;   // ms hold at each end, virtual px/s
        float scrollT = fmaxf(350.0f, over / fmaxf(1.0f, ps3::devS(SPEED * mpUi)) * 1000.0f);
        float cyc = HOLD + scrollT + HOLD + scrollT;
        float tt = fmodf(mEffectTime * 1000.0f, cyc);
        float p;
        if (tt < HOLD) p = 0.0f;
        else if (tt < HOLD + scrollT) { float u = (tt - HOLD) / scrollT; p = u * u * (3 - 2 * u); }
        else if (tt < HOLD + scrollT + HOLD) p = 1.0f;
        else { float u = (tt - HOLD - scrollT - HOLD) / scrollT; p = 1.0f - u * u * (3 - 2 * u); }
        return over * p;
    };
    // Rotation-aware horizontal clip band [bx, bx+bw] over the full screen height
    // (the web ctx.clip), so a marquee's hold-at-start never spills past the band.
    auto clipBand = [&](float bx, float bw) {
        int lx = (int)bx, ly = 0, lw = (int)bw, lh = (int)mHeight;
        if (lw < 0) lw = 0;
        int sx, sy, sw, sh;
        switch (sDrmGlRotation ? sDrmRotationDeg : 0) {
        case 90:  sx = ly; sy = (int)mWidth - lx - lw; sw = lh; sh = lw; break;
        case 180: sx = (int)mWidth - lx - lw; sy = (int)mHeight - ly - lh; sw = lw; sh = lh; break;
        case 270: sx = (int)mHeight - ly - lh; sy = lx; sw = lh; sh = lw; break;
        default:  sx = lx; sy = ly; sw = lw; sh = lh; break;
        }
        glEnable(GL_SCISSOR_TEST); glScissor(sx, sy, sw, sh);
    };

    // title (marquee bounce when too wide), clipped to the band
    float titleScale = FSZ(32.0f * mpUi);
    float toff = marqueeOff(measureText(t.title.c_str(), titleScale), bandW);
    clipBand(tx, bandW);
    drawText(t.title.c_str(), tx - toff, ps3::baselineToTopY(titleBaseY, titleScale),
             titleScale, 1.0f, 1.0f, 1.0f, 0.95f * enter);
    glDisable(GL_SCISSOR_TEST);

    // artist / album (70%) - same marquee + clip so it never overlaps the seek bar.
    // Internet Radio shows just the station group (no "/ album").
    std::string sub = live ? (t.artist.empty() ? "Internet Radio" : t.artist)
                           : (t.artist.empty() ? "-" : t.artist) + " / " + (t.album.empty() ? "-" : t.album);
    float subScale = FSZ(19.0f * mpUi);
    float soff = marqueeOff(measureText(sub.c_str(), subScale), bandW);
    clipBand(tx, bandW);
    drawText(sub.c_str(), tx - soff, ps3::baselineToTopY(artistBaseY, subScale),
             subScale, 1.0f, 1.0f, 1.0f, 0.70f * enter);
    glDisable(GL_SCISSOR_TEST);

    // full-info cluster: counter + codec on a top line, elapsed (left) and total
    // (right) flanking a full-width seek bar - the HH:MM:SS strings never collide
    // with the codec badge and the whole thing fits when doubled.
    if (mMpFullInfoT > 0.001f) {
        float fa = enter * mMpFullInfoT;
        double dur = mMusicPlayer.duration();
        // While scrubbing, preview the pending target so the time + bar track the
        // press instantly (the real seek commits after input settles).
        double cur = mMpSeekPending ? mMpSeekTarget : mMusicPlayer.position();
        float lineTop  = ay + jsz * 0.22f;   // counter + codec
        float lineTime = ay + jsz * 0.60f;   // elapsed / total
        float seekY    = ay + jsz * 0.88f;   // seek bar
        // counter N/M right-aligned at clEnd
        int qn = live ? (int)mMpRadioQueue.size() : (int)mMpQueue.size();
        char cnt[24]; snprintf(cnt, sizeof(cnt), "%d / %d", mMpIdx + 1, qn);
        float cs = FSZ(17.0f * mpUi); float cnw = measureText(cnt, cs);
        drawText(cnt, clEnd - cnw, ps3::baselineToTopY(lineTop, cs), cs, 1.0f, 1.0f, 1.0f, 0.70f * fa);
        // codec badge left-aligned at clX (top line, off the time row, never overlaps it)
        static const struct { const char* c; int i; } kCodec[] = {
            {"MP3",24},{"ATRAC",22},{"AAC",23},{"PCM",25},{"CD",26},{"WMA",27},{"FLAC",25},{"OGG",23},{"OPUS",23}};
        int cIdx = 24; for (auto& e : kCodec) if (t.codec == e.c) { cIdx = e.i; break; }
        GLuint cIc = mpIcon(cIdx);
        if (cIc) { float ch = SZ(0.024f * mpUi); float cbw = ch * mpIconAR(cIdx);
                   drawIconTex(cIc, clX, lineTop - ch, cbw, ch, 1, 1, 1, fa); }
        if (live) {
            // Internet Radio is a live stream: no elapsed/total and no seek bar. Show how long
            // we have been listening (left) and a "LIVE" badge (right), with a static full-width
            // bar so the layout matches the local-track player.
            float ts = FSZ(22.0f * mpUi);
            std::string el = mpFmtTime(cur);
            drawText(el.c_str(), clX, ps3::baselineToTopY(lineTime, ts), ts, 1.0f, 1.0f, 1.0f, 0.80f * fa);
            const char* liveStr = "LIVE";
            float lw = measureText(liveStr, ts);
            drawText(liveStr, clEnd - lw, ps3::baselineToTopY(lineTime, ts), ts, 1.0f, 0.45f, 0.45f, 0.90f * fa);
            float sx = clX, sw = clEnd - clX, shh = SZ(0.012f * mpUi);
            drawQuad(sx, seekY, sw, shh, 70/255.0f, 70/255.0f, 70/255.0f, 0.95f * fa);
            drawQuad(sx, seekY, sw, shh, 245/255.0f, 90/255.0f, 90/255.0f, 0.35f * fa);   // live tint
        } else {
            // elapsed (left) / total (right) on the time line
            float ts = FSZ(22.0f * mpUi);
            std::string el = mpFmtTime(cur);
            std::string tot = (dur > 0 ? mpFmtTime(dur) : "--:--:--");
            drawText(el.c_str(), clX, ps3::baselineToTopY(lineTime, ts), ts, 1.0f, 1.0f, 1.0f, 0.80f * fa);
            float totw = measureText(tot.c_str(), ts);
            drawText(tot.c_str(), clEnd - totw, ps3::baselineToTopY(lineTime, ts), ts, 1.0f, 1.0f, 1.0f, 0.80f * fa);
            // full-width seek bar
            float sx = clX, sw = clEnd - clX, shh = SZ(0.012f * mpUi);
            drawQuad(sx, seekY, sw, shh, 70/255.0f, 70/255.0f, 70/255.0f, 0.95f * fa);
            drawQuad(sx, seekY, sw, fmaxf(1.0f, SZ(0.0015f * mpUi)), 150/255.0f, 150/255.0f, 150/255.0f, 0.85f * fa);
            float frac = dur > 0 ? (float)(cur / dur) : 0.0f; if (frac < 0) frac = 0; if (frac > 1) frac = 1;
            if (frac > 0) drawQuad(sx, seekY, fmaxf(2.0f, sw * frac), shh, 245/255.0f, 245/255.0f, 245/255.0f, 0.95f * fa);
        }
    }

    if (panelUp) drawMpStatusRow(ax, enter);

    // visualizer-name banner (1500ms, fade 150 in / 300 out, top-left)
    if (mMpBannerStart >= 0.0f) {
        float el = (mEffectTime - mMpBannerStart) * 1000.0f;
        if (el > 1500.0f) mMpBannerStart = -1.0f;
        else {
            float a = el < 150.0f ? el / 150.0f : (el > 1200.0f ? (1500.0f - el) / 300.0f : 1.0f);
            if (a < 0) a = 0;
            drawText(mMpBanner.c_str(), DXP(0.045f), TOPY(0.11f, 24.0f * mpUi), FSZ(24.0f * mpUi), 1.0f, 1.0f, 1.0f, a);
        }
    }

    // control panel
    if (mMpCpOpen) drawMpOpt(-1.0f);
    else if (mMpCpClosing) {
        float p = (mEffectTime - mMpCpCloseStart) / 0.2f;
        if (p >= 1.0f) mMpCpClosing = false; else drawMpOpt(1.0f - p);
    }

    // full-screen message (Deleting... / Delete completed.)
    if (mMpMsgStart >= 0.0f) {
        float el = (mEffectTime - mMpMsgStart) * 1000.0f;
        float fade = fminf(1.0f, el / 150.0f) * fminf(1.0f, fmaxf(0.0f, (mMpMsgDur - el)) / 200.0f);
        drawQuad(0, 0, (float)mWidth, (float)mHeight, 0, 0, 0, 0.45f * fade);
        float ms = FSZ(28.0f * mpUi); float mw = measureText(mMpMsg.c_str(), ms);
        drawText(mMpMsg.c_str(), (mWidth - mw) * 0.5f, TOPY(0.5f, 28.0f * mpUi), ms, 1.0f, 1.0f, 1.0f, fade);
    }
    // Add-to-Playlist chooser modal (drawn on top of everything in the player).
    if (mMpPlChooserActive || mMpPlChooserAnim > 0.004f) drawMpPlChooser();
    mTextOutlineMode = 0;
}

// play-state / transport / repeat / shuffle row above the jacket (panel open).
void NanoMenu::drawMpStatusRow(float ax, float fade) {
    float mpUi = ((mWidth < mHeight ? mWidth : mHeight) <= 768) ? 1.5f : 1.0f;  // match the 1.5x bar jacket
    float jsz = SZ(0.085f * mpUi), ay = DYP(0.912f) - jsz;   // match the scaled jacket
    float h = SZ(0.030f * mpUi);
    float y = ay - jsz * 0.53f;   // centred just above the jacket (== 0.782 at 1x)
    float x = ax + jsz * 0.5f - h * 0.5f;
    float a = 0.95f * fade;
    // native aspect (the repeat/shuffle/one glyphs are non-square pills) + a subtle
    // drop-shadow (offset dark copy; no shadowBlur on GLES2), per web drawMpStatusRow.
    auto ico = [&](int idx, float scale) {
        GLuint ic = mpIcon(idx); if (!ic) return;
        float hh = h * scale, ww = hh * mpIconAR(idx);
        drawIconTex(ic, x + SZ(0.0012f * mpUi), y - hh * 0.5f + SZ(0.0012f * mpUi), ww, hh, 0, 0, 0, 0.6f * a);
        drawIconTex(ic, x, y - hh * 0.5f, ww, hh, 1, 1, 1, a);
        x += ww + DXD(0.008f * mpUi);
    };
    bool paused = mMusicPlayer.isPaused(), stopped = mMusicPlayer.isStopped();
    // base play-state glyph; a transient transport action briefly overrides it
    if (mMpTransientIcon >= 0 && mEffectTime < mMpTransientUntil) ico(mMpTransientIcon, 1.0f);
    else ico(stopped ? 4 : (paused ? 3 : 0), 1.0f);
    x += DXD(0.004f * mpUi);
    if (mMpRepeat == 1) ico(7, 1.0f);                       // repeat ALL = loop
    else if (mMpRepeat == 2) { ico(7, 1.0f); ico(8, 0.75f); }   // repeat ONE = loop + "1"
    if (mMpShuffle) ico(9, 1.0f);                           // shuffle pill
}

void NanoMenu::drawMpOpt(float closeT) {
    float t = (closeT >= 0.0f) ? closeT
            : (mMpCpAnimStart >= 0.0f ? fminf(1.0f, (mEffectTime - mMpCpAnimStart) / 0.2f) : 1.0f);
    if (t < 0) t = 0;
    float mpUi = mpUiScale(mWidth, mHeight);
    float ox = DXP(0.273f) - (1.0f - t) * SZ(0.018f * mpUi);   // slide in from the left
    float oy = DYP(0.441f);
    float cellX = DXD(0.033f * mpUi), cellY = SZ(0.061f * mpUi), ih = SZ(0.046f * mpUi);
    float pulse = 0.5f + 0.5f * cosf(mEffectTime * 2.0f * 3.14159f / 1.5f);   // ~1.5s breathe
    for (int i = 0; i < kMpCpCount; i++) {
        const MpCp& b = kMpCp[i];
        bool focus = (i == mMpCpSel);
        float cx = ox + b.gx * cellX, cy = oy - b.gy * cellY;
        // focus grow-in / shrink-back (~140ms cubic ease-out), web drawMpOpt
        float baseScale = focus ? 1.18f : 1.0f;
        if (mMpCpFocusStart >= 0.0f) {
            float fe = fminf(1.0f, (mEffectTime - mMpCpFocusStart) / 0.14f);
            float fk = 1.0f - powf(1.0f - fe, 3.0f);
            if (focus) baseScale = 1.0f + 0.18f * fk;
            else if (i == mMpCpSelPrev) baseScale = 1.18f - 0.18f * fk;
        }
        // press dip + brightness flash on the activated control (240ms)
        float ps = baseScale, flash = 0.0f;
        if (mMpCpPressSel == i && mMpCpPressStart >= 0.0f) {
            float e = (mEffectTime - mMpCpPressStart) / 0.24f;
            if (e < 1.0f) { float s = sinf(e * 3.14159265f); ps *= 1.0f - 0.18f * s; flash = s * 0.55f; }
        }
        // The web SKIPS the firmware "shadow" plate (b.s, a near-opaque dark square that
        // muddies the visualizer) and draws clean glyphs with a subtle drop-shadow / halo.
        GLuint gN = mpIcon(b.n), gF = mpIcon(b.f);   // also populates the aspect cache
        auto glyph = [&](GLuint tex, int arIdx, float dx, float dy, float r, float g, float bl, float al) {
            if (!tex) return;
            float hh = ih * ps, ww = hh * mpIconAR(arIdx);
            drawIconTex(tex, cx - ww * 0.5f + dx, cy - hh * 0.5f + dy, ww, hh, r, g, bl, t * al);
        };
        if (focus) {
            // breathing halo (faint enlarged focus glyph; no shadowBlur on GLES2) + crisp glyph
            if (gF) {
                float hh = ih * ps * 1.18f, ww = hh * mpIconAR(b.f);
                drawIconTex(gF, cx - ww * 0.5f, cy - hh * 0.5f, ww, hh, 0.86f, 0.92f, 1.0f, t * (0.18f + 0.16f * pulse));
            }
            glyph(gF, b.f, 0, 0, 1, 1, 1, 1.0f);
        } else {
            glyph(gN, b.n, SZ(0.0015f * mpUi), SZ(0.0025f * mpUi), 0, 0, 0, 0.5f);   // drop shadow
            glyph(gN, b.n, 0, 0, 1, 1, 1, 0.85f);                      // dimmed glyph
        }
        if (flash > 0.0f) glyph(gF, b.f, 0, 0, 1, 1, 1, flash);        // activate brightness pop
    }
    // focused-item label + the firmware "SELECT" button-hint pill, centred as a group
    // at the grid origin (web drawMpOpt draws the label + drawMpPill('SELECT') for EVERY
    // focused control). Suppressed while the Volume submeter is open (it draws its own
    // "Volume Control" title at the same spot).
    if (mMpCpSel >= 0 && mMpCpSel < kMpCpCount && !mMpVolSub) {
        const char* lab = kMpCp[mMpCpSel].label;
        float ls = FSZ(20.0f * mpUi), lw = measureText(lab, ls);
        float gap = DXD(0.008f * mpUi), pillW = DXD(0.050f * mpUi), pillH = SZ(0.030f * mpUi);
        float total = lw + gap + pillW;
        float cx = DXP(0.273f), sx = cx - total * 0.5f;
        // baseline below the (scaled) grid's bottom row
        float labBaseY = oy + cellY + SZ(0.060f * mpUi);
        drawText(lab, sx, ps3::baselineToTopY(labBaseY, ls), ls, 1.0f, 1.0f, 1.0f, 0.95f * t);
        // SELECT pill: rounded rect (light border behind a grey fill) + centred glyph,
        // built from a centre quad + two end caps since GLES2 has no rounded-rect path.
        float px = sx + lw + gap;
        float py = labBaseY - pillH * 0.78f;
        float bw = 1.2f * mpUi;
        auto pill = [&](float qx, float qy, float qw, float qh,
                        float cr, float cg, float cb, float ca) {
            float rr = qh * 0.5f;
            drawQuad(qx + rr, qy, qw - 2.0f * rr, qh, cr, cg, cb, ca);
            ps3FillCircle(qx + rr, qy + rr, rr, cr, cg, cb, ca);
            ps3FillCircle(qx + qw - rr, qy + rr, rr, cr, cg, cb, ca);
        };
        pill(px - bw, py - bw, pillW + 2.0f * bw, pillH + 2.0f * bw,
             225/255.0f, 225/255.0f, 225/255.0f, 0.7f * t);                          // border
        pill(px, py, pillW, pillH, 150/255.0f, 150/255.0f, 150/255.0f, 0.55f * t);   // fill
        float fs = FSZ(15.0f * mpUi), fw = measureText("SELECT", fs);
        drawText("SELECT", px + pillW * 0.5f - fw * 0.5f,
                 ps3::baselineToTopY(py + pillH * 0.66f, fs), fs, 1.0f, 1.0f, 1.0f, 0.95f * t);
    }
    if (mMpVolSub) drawMpVolMeter(t);
}

void NanoMenu::drawMpVolMeter(float t) {
    float mpUi = mpUiScale(mWidth, mHeight);
    float cx = DXP(0.22f);
    // Stack below the (scaled) panel grid so it never overlaps it.
    float titleBaseY = DYP(0.441f) + SZ(0.061f * mpUi) + SZ(0.055f * mpUi);
    float vts = FSZ(22.0f * mpUi);
    drawText("Volume Control", cx - measureText("Volume Control", vts) * 0.5f,
             ps3::baselineToTopY(titleBaseY, vts), vts, 1, 1, 1, t);
    int lvl = mMpVolLevel;
    char nm[8]; if (lvl == 0) snprintf(nm, sizeof(nm), "Normal"); else snprintf(nm, sizeof(nm), "%+d", lvl);
    float nts = FSZ(18.0f * mpUi), nameBaseY = titleBaseY + SZ(0.040f * mpUi);
    drawText(nm, cx - measureText(nm, nts) * 0.5f, ps3::baselineToTopY(nameBaseY, nts), nts, 1, 1, 1, 0.85f * t);
    int segN = 9; float segW = SZ(0.020f * mpUi), gap = SZ(0.006f * mpUi), hh = SZ(0.024f * mpUi);
    float my = nameBaseY + SZ(0.020f * mpUi);
    float totalW = segN * segW + (segN - 1) * gap, x0 = cx - totalW * 0.5f;
    int filled = lvl + 5;
    for (int i = 0; i < segN; i++) {
        float x = x0 + i * (segW + gap);
        if (i < filled) drawQuad(x, my, segW, hh, 120/255.0f, 225/255.0f, 255/255.0f, 0.95f * t);
        else drawQuad(x, my, segW, hh, 1, 1, 1, 0.20f * t);
    }
    // "-" / "+" end glyphs flanking the bar
    float es = FSZ(18.0f * mpUi), ey = ps3::baselineToTopY(my + hh * 0.9f, es), em = ps3::devS(10.0f * mpUi);
    drawText("-", x0 - em - measureText("-", es), ey, es, 1, 1, 1, t);
    drawText("+", x0 + totalW + em, ey, es, 1, 1, 1, t);
}

// ---- control panel input ----
void NanoMenu::openMpOpt() {
    if (mMpCpOpen) return;
    mMpCpOpen = true; mMpCpClosing = false; mMpVolSub = false;
    mMpCpSel = mpCpDefault(); mMpCpAnimStart = mEffectTime; mMpCpFocusStart = mEffectTime;
}
void NanoMenu::closeMpOpt() {
    if (!mMpCpOpen) return;
    if (mMpVolSub) { mMpVolSub = false; return; }
    mMpCpOpen = false; mMpCpClosing = true; mMpCpCloseStart = mEffectTime;
}
void NanoMenu::mpOptBack() {
    if (mMpVolSub) { mMpVolSub = false; return; }
    closeMpOpt();
}
void NanoMenu::mpOptMove(int dx, int dy) {
    if (!mMpCpOpen) return;
    if (mMpVolSub) {
        if (dx) { mMpVolLevel += (dx > 0 ? 1 : -1); if (mMpVolLevel < -4) mMpVolLevel = -4; if (mMpVolLevel > 4) mMpVolLevel = 4;
                  mMusicPlayer.setVolume((mMpVolLevel + 4) / 8.0f); }
        return;
    }
    const MpCp& cur = kMpCp[mMpCpSel]; int best = -1; float bestd = 1e9f;
    for (int i = 0; i < kMpCpCount; i++) {
        if (i == mMpCpSel) continue; const MpCp& b = kMpCp[i];
        float ddx = b.gx - cur.gx, ddy = b.gy - cur.gy;
        if (dx > 0 && ddx <= 0) continue; if (dx < 0 && ddx >= 0) continue;
        if (dy > 0 && ddy <= 0) continue; if (dy < 0 && ddy >= 0) continue;
        float along = dx != 0 ? fabsf(ddx) : fabsf(ddy);
        float perp = dx != 0 ? fabsf(ddy) : fabsf(ddx);
        float d = along + perp * 3.0f;
        if (d < bestd) { bestd = d; best = i; }
    }
    if (best >= 0) { mMpCpSelPrev = mMpCpSel; mMpCpFocusStart = mEffectTime; mMpCpSel = best; }
}
void NanoMenu::mpOptActivate() {
    if (!mMpCpOpen) return;
    if (mMpVolSub) { mMpVolSub = false; return; }   // X confirms + closes volume submenu
    mMpCpPressStart = mEffectTime; mMpCpPressSel = mMpCpSel;
    const char* a = kMpCp[mMpCpSel].act;
    if (!strcmp(a, "vol")) { mMpVolSub = true; }
    else if (!strcmp(a, "play")) { mpAudioCmd(MpAudioCmd::Play); }
    else if (!strcmp(a, "pause")) { mpAudioCmd(MpAudioCmd::Pause); }
    else if (!strcmp(a, "stop")) { mpAudioCmd(MpAudioCmd::Stop); }
    else if (!strcmp(a, "next")) { mMpTransientIcon = 2; mMpTransientUntil = mEffectTime + 0.9f; mpNext(); }
    else if (!strcmp(a, "prev")) { mMpTransientIcon = 1; mMpTransientUntil = mEffectTime + 0.9f; mpPrev(); }
    else if (!strcmp(a, "rew")) { if (mMpIsRadio) return;   // live stream: no seek
        mMpTransientIcon = 5; mMpTransientUntil = mEffectTime + 0.9f;
        double base = mMpSeekPending ? mMpSeekTarget : mMusicPlayer.position();
        double p = base - 10.0; if (p < 0.0) p = 0.0;
        mMpSeekTarget = p; mMpSeekPending = true; mMpSeekInputT = mEffectTime; }
    else if (!strcmp(a, "ff")) { if (mMpIsRadio) return;   // live stream: no seek
        mMpTransientIcon = 6; mMpTransientUntil = mEffectTime + 0.9f;
        double base = mMpSeekPending ? mMpSeekTarget : mMusicPlayer.position();
        double d = mMusicPlayer.duration(); double np = base + 10.0; if (d > 0.0 && np > d) np = d;
        mMpSeekTarget = np; mMpSeekPending = true; mMpSeekInputT = mEffectTime; }
    else if (!strcmp(a, "repeat")) { mMpRepeat = (mMpRepeat + 1) % 3; }
    else if (!strcmp(a, "shuffle")) { mMpShuffle = !mMpShuffle; mpRebuildOrder(); }
    else if (!strcmp(a, "vis")) { mpCycleVis(); }
    else if (!strcmp(a, "disp")) { mMpFullInfo = !mMpFullInfo; }
    else if (!strcmp(a, "del")) { if (mMpIsRadio) return;   // nothing to delete for a live station
        mpShowMsg("Deleting...", 800.0f, 1); }
    else if (!strcmp(a, "addpl")) {
        // Web mpOpenAddChooser: present an XMB-style chooser to add to an existing
        // playlist or create a new one (rather than jumping straight to the OSK).
        mpOpenAddChooser();
    }
}

// ---------------------------------------------------------------------------
// Add-to-Playlist chooser (player) - mirrors web mpOpenAddChooser (index.html
// 10865): a modal list of "New Playlist..." + the existing playlists, over the
// Now-Playing screen. Select 0 opens the name OSK + creates; select i adds to the
// existing playlist i-1.
// ---------------------------------------------------------------------------
void NanoMenu::mpOpenAddChooser() {
    int ti = (mMpIdx >= 0 && mMpIdx < (int)mMpQueue.size()) ? mMpQueue[mMpIdx] : -1;
    if (ti < 0 || ti >= (int)mMusicTracks.size()) return;
    mMpPlChooserTrack = ti;
    mMpPlChooserOpts.clear();
    mMpPlChooserOpts.push_back("New Playlist...");
    for (const auto& pl : mMusicPlaylists) mMpPlChooserOpts.push_back(pl.name);
    mMpPlChooserSel = mMusicPlaylists.empty() ? 0 : 1;   // default to the first existing (web)
    mMpPlChooserAnim = 0.0f;
    mMpPlChooserActive = true;
    closeMpOpt();   // close the control panel behind the chooser (web pv.panel=false)
}

void NanoMenu::mpPlChooserMove(int dir) {
    int n = (int)mMpPlChooserOpts.size();
    if (n <= 0) return;
    mMpPlChooserSel = (mMpPlChooserSel + dir % n + n) % n;
}

void NanoMenu::mpPlChooserCancel() { mMpPlChooserActive = false; }

void NanoMenu::mpPlChooserSelect() {
    int sel = mMpPlChooserSel, ti = mMpPlChooserTrack;
    std::string file = (ti >= 0 && ti < (int)mMusicTracks.size()) ? mMusicTracks[ti].file : "";
    mMpPlChooserActive = false;
    if (file.empty()) return;
    if (sel == 0) {
        openOskForPassword("Enter a name for the playlist",
            [this, file](const std::string& nm){ musicCreatePlaylist(nm);
                if (!file.empty()) musicAddTrackToPlaylist((int)mMusicPlaylists.size() - 1, file); });
        mOskPasswordMode = false; mOskPlaintext = true;   // a playlist name is plain text, not masked
    } else {
        int pl = sel - 1;
        if (pl >= 0 && pl < (int)mMusicPlaylists.size()) {
            musicAddTrackToPlaylist(pl, file);
            mpShowMsg("Added to playlist", 800.0f, 0);
        }
    }
}

// Centered modal list over the Now-Playing screen: title + "New Playlist..." + the
// existing playlists, the highlighted one with a blue bar.
void NanoMenu::drawMpPlChooser() {
    float target = mMpPlChooserActive ? 1.0f : 0.0f;
    float dt = (mFrameDt > 0.0f && mFrameDt < 0.2f) ? mFrameDt : 0.016f;
    mMpPlChooserAnim += (target - mMpPlChooserAnim) * fminf(1.0f, dt * 12.0f);
    float a = mMpPlChooserAnim;
    if (a <= 0.004f) return;
    int n = (int)mMpPlChooserOpts.size();
    if (n <= 0) return;
    float ui = ((mWidth < mHeight ? mWidth : mHeight) <= 768) ? 1.5f : 1.0f;
    mTextOutlineMode = 1;
    // dim backdrop
    drawQuad(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 0.0f, 0.0f, 0.55f * a);
    float rowH   = SZ(0.052f * ui);
    float titleH = SZ(0.072f * ui);
    float padX   = DXD(0.018f);
    float panelW = DXD(0.44f);
    float panelH = titleH + (float)n * rowH + SZ(0.026f);
    float px = DXP(0.5f) - panelW * 0.5f;
    float py = DYP(0.5f) - panelH * 0.5f;
    drawQuad(px, py, panelW, panelH, 0.10f, 0.12f, 0.16f, 0.92f * a);
    float ts = FSZ(24.0f * ui);
    drawText("Add to Playlist", px + padX, ps3::baselineToTopY(py + titleH * 0.62f, ts),
             ts, 1.0f, 1.0f, 1.0f, 0.95f * a);
    drawQuad(px + padX, py + titleH - SZ(0.004f), panelW - 2.0f * padX,
             fmaxf(1.0f, SZ(0.0015f)), 1.0f, 1.0f, 1.0f, 0.25f * a);
    float os = FSZ(19.0f * ui);
    float ry = py + titleH + SZ(0.006f);
    for (int i = 0; i < n; i++) {
        bool seld = (i == mMpPlChooserSel);
        if (seld)
            drawQuad(px + SZ(0.006f), ry, panelW - SZ(0.012f), rowH,
                     0.30f, 0.52f, 0.96f, 0.55f * a);
        float c = seld ? 1.0f : 0.85f;
        drawText(mMpPlChooserOpts[i].c_str(), px + padX,
                 ps3::baselineToTopY(ry + rowH * 0.64f, os), os,
                 c, c, c, (seld ? 1.0f : 0.80f) * a);
        ry += rowH;
    }
    mTextOutlineMode = 0;
}

} // namespace android
