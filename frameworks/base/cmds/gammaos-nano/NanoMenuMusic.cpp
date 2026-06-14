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
#include "NanoJson.h"

#include <dirent.h>
#include <fcntl.h>
#include <cerrno>
#include <unistd.h>
#include <sys/stat.h>
#include <string.h>
#include <strings.h>
#include <algorithm>
#include <cstdlib>
#include <map>
#include <set>
#include <thread>
#include <utils/Log.h>

namespace android {

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
            mMusicPlaylists.push_back(std::move(pl));
        }
    ALOGI("NanoMenu: loaded music library (%zu folders, %zu tracks, %zu playlists)",
          mMusicFolders.size(), mMusicTracks.size(), mMusicPlaylists.size());
    return true;
}

void NanoMenu::saveMusicConfig() {
    njson::Value root = njson::Value::makeObject();
    root.set("version") = njson::Value::makeNumber(1);
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
    mMusicCfgStamp = musicConfigStamp();
    ALOGD("NanoMenu: wrote nano_music.json (%zu tracks)", mMusicTracks.size());
}

// ---------------------------------------------------------------------------
// Lazy load: parse the config once, init the audio engine (no stream yet), and
// kick a scan if folders exist. Self-guarded; safe to call from every entry.
// ---------------------------------------------------------------------------
void NanoMenu::musicEnsureLoaded() {
    if (mMusicLoaded) return;
    mMusicLoaded = true;
    loadMusicConfig();
    mMusicPlayer.init();
    if (!mMusicFolders.empty() && !mMusicScanRunning) musicScanAsync();
}

// ---------------------------------------------------------------------------
// Scanner: recurse every imported folder, collect audio files, probe metadata
// (mtime-cached against the current library), publish under a mutex.
// ---------------------------------------------------------------------------
static void scanDirRecursive(const std::string& dir,
                             std::vector<std::string>& outFiles, int depth) {
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
    }
    closedir(d);
    std::sort(subdirs.begin(), subdirs.end(),
              [](const std::string& a, const std::string& b){ return strcasecmp(a.c_str(), b.c_str()) < 0; });
    for (const auto& s : subdirs) scanDirRecursive(s, outFiles, depth + 1);
}

void NanoMenu::musicScanAsync() {
    if (mMusicScanRunning) return;
    mMusicScanRunning = true;
    std::thread(&NanoMenu::musicScanThreadFunc, this).detach();
}

void NanoMenu::musicScanThreadFunc() {
    // Snapshot the inputs so the worker never races the render thread.
    std::vector<std::string> folders = mMusicFolders;
    // mtime cache: path -> existing track (carry metadata over if unchanged).
    std::vector<MusicTrack> cacheVec = mMusicTracks;
    std::map<std::string, const MusicTrack*> cache;
    for (const auto& t : cacheVec) cache[t.file] = &t;

    std::vector<std::string> files;
    for (const auto& f : folders) scanDirRecursive(f, files, 0);
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
    {
        std::lock_guard<std::mutex> lk(mMusicScanMutex);
        mMusicScanResults = std::move(results);
        mMusicScanReady = true;
    }
    mMusicScanRunning = false;
    ALOGI("NanoMenu: music scan finished (%zu files)", files.size());
}

void NanoMenu::musicDrainScanResults() {
    if (!mMusicScanReady) return;
    {
        std::lock_guard<std::mutex> lk(mMusicScanMutex);
        mMusicTracks = std::move(mMusicScanResults);
        mMusicScanResults.clear();
        mMusicScanReady = false;
    }
    saveMusicConfig();
    mMusicCatsStale = true;   // rebuild the Music column at the settled root
}

// ---------------------------------------------------------------------------
// Album grouping (derived from mMusicTracks).
// ---------------------------------------------------------------------------
std::vector<std::string> NanoMenu::musicAlbumNames() const {
    std::vector<std::string> names;
    std::set<std::string> seen;
    for (const auto& t : mMusicTracks)
        if (seen.insert(t.album).second) names.push_back(t.album);
    std::sort(names.begin(), names.end(),
              [](const std::string& a, const std::string& b){ return strcasecmp(a.c_str(), b.c_str()) < 0; });
    return names;
}

std::vector<int> NanoMenu::musicAlbumTrackIndices(const std::string& album) const {
    std::vector<int> idx;
    for (size_t i = 0; i < mMusicTracks.size(); i++)
        if (mMusicTracks[i].album == album) idx.push_back((int)i);
    std::sort(idx.begin(), idx.end(), [this](int a, int b){
        const MusicTrack& ta = mMusicTracks[a]; const MusicTrack& tb = mMusicTracks[b];
        if (ta.trackNo != tb.trackNo && ta.trackNo && tb.trackNo) return ta.trackNo < tb.trackNo;
        return strcasecmp(ta.title.c_str(), tb.title.c_str()) < 0;
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
    int start = 0;
    for (int i = 0; i < (int)list.size(); i++) {
        if (list[i].kind != PS3_MUSIC_TRACK) continue;
        if (i == listSel) start = (int)mMpQueue.size();
        mMpQueue.push_back(list[i].a);   // mMusicTracks index
    }
    if (mMpQueue.empty()) return;
    mMpIdx = (start >= 0 && start < (int)mMpQueue.size()) ? start : 0;
    mMpRepeat = 0;
    mMpShuffle = false;
    mpRebuildOrder();
    mMpActive = true;
    mpPlayCurrent();
}

void NanoMenu::closeMusicPlayer() {
    mMpActive = false;
    mMusicPlayer.release();   // stop stream + free decoder ring (keep the library loaded)
}

void NanoMenu::mpPlayCurrent() {
    if (mMpIdx < 0 || mMpIdx >= (int)mMpQueue.size()) return;
    int ti = mMpQueue[mMpIdx];
    if (ti < 0 || ti >= (int)mMusicTracks.size()) return;
    if (mMusicPlayer.open(mMusicTracks[ti].file)) mMusicPlayer.play();
}

void NanoMenu::mpRebuildOrder() {
    mMpOrder.clear();
    int n = (int)mMpQueue.size();
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
            if (mMpRepeat == 0) { mMusicPlayer.pause(); return; }   // off: stop at end
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
    if (mMusicPlayer.position() > 3.0) { mMusicPlayer.seek(0.0); return; }   // restart current
    mpStep(-1, false);
}

void NanoMenu::musicTick() {
    if (!mMpActive) return;
    if (mMusicPlayer.ended()) {
        if (mMpRepeat == 2) mpPlayCurrent();   // repeat-one: replay
        else mpStep(1, true);                  // auto-advance
    }
}

} // namespace android
