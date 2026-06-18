// NanoMenuVideo.cpp - the Video library glue: nano_video.json model + persist +
// recursive scan + folder import, the Video-column content, and the Sort By (Y).
// Mirrors the Music library (NanoMenuMusic.cpp); HW playback is in NanoVideo + the
// player screen. The web XMB has no video library/import/sort (its video items are
// flat demo entries); these are a nano addition (user request) on top of a 1:1
// player UI. Everything is lazy: parsed on first Video focus, nothing at boot.
#include "NanoMenu.h"
#include "NanoMenuPS3.h"
#include "NanoVideo.h"
#include "NanoDvbSub.h"
#include "NanoTsDescramble.h"
#include "NanoJson.h"

#include <algorithm>
#include <cstdint>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <math.h>
#include <map>
#include <set>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <thread>
#include <unistd.h>
#include <utility>

#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaFormat.h>

#include <cutils/properties.h>

#include <android/log.h>
#define VLOGI(...) __android_log_print(ANDROID_LOG_INFO,  "GammaOSNano", __VA_ARGS__)
#define VLOGW(...) __android_log_print(ANDROID_LOG_WARN,  "GammaOSNano", __VA_ARGS__)

namespace android {

// ---- small path helpers (file-local; music has its own statics) ------------
static std::string vBaseName(const std::string& path) {
    size_t s = path.find_last_of('/');
    return s == std::string::npos ? path : path.substr(s + 1);
}
static std::string vStripExt(const std::string& name) {
    size_t d = name.find_last_of('.');
    return d == std::string::npos ? name : name.substr(0, d);
}
static bool isVideoExt(const std::string& nameLower) {
    size_t dot = nameLower.rfind('.');
    if (dot == std::string::npos) return false;
    std::string e = nameLower.substr(dot);
    return e == ".mp4" || e == ".m4v" || e == ".mkv" || e == ".webm" ||
           e == ".mov" || e == ".3gp" || e == ".avi" || e == ".ts"   ||
           e == ".mpg" || e == ".mpeg" || e == ".mp2t";
}
// MPEG-TS containers we demux in-process (multi-audio + captions + low-memory). The real
// gate is NanoTsDemux::open() (it verifies TS sync), so a mislabelled file falls back.
static bool vidFileIsTs(const std::string& path) {
    std::string l = path;
    for (auto& c : l) if (c >= 'A' && c <= 'Z') c += 32;
    size_t dot = l.rfind('.');
    if (dot == std::string::npos) return false;
    std::string e = l.substr(dot);
    return e == ".ts" || e == ".m2ts" || e == ".mts" || e == ".trp" || e == ".mp2t";
}
// Friendly audio-track label from an ISO-639 language code (the .ts carries no track name).
static std::string vidLangName(const std::string& code, size_t idx) {
    static const struct { const char* c; const char* n; } M[] = {
        {"eng","English"}, {"spa","Spanish"}, {"fra","French"}, {"fre","French"},
        {"deu","German"},  {"ger","German"},  {"ita","Italian"}, {"por","Portuguese"},
        {"jpn","Japanese"},{"kor","Korean"},  {"chi","Chinese"}, {"zho","Chinese"},
        {"rus","Russian"}, {"ara","Arabic"},  {"hin","Hindi"},   {"nld","Dutch"},
    };
    for (auto& m : M) if (code == m.c) return m.n;
    if (!code.empty() && code != "und") return code;
    char b[16]; snprintf(b, sizeof(b), "Audio %zu", idx + 1); return b;
}
static void vScanDirRecursive(const std::string& dir, std::vector<std::string>& out, int depth) {
    if (depth > 8) return;
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
        if (isVideoExt(lower)) out.push_back(child);
    }
    closedir(d);
    std::sort(subdirs.begin(), subdirs.end(),
              [](const std::string& a, const std::string& b){ return strcasecmp(a.c_str(), b.c_str()) < 0; });
    for (const auto& s : subdirs) vScanDirRecursive(s, out, depth + 1);
}

// ---------------------------------------------------------------------------
// nano_video.json persistence (mirrors nano_music.json: atomic write, mtime reload).
// ---------------------------------------------------------------------------
int64_t NanoMenu::videoConfigStamp() const {
    struct stat st;
    if (stat("/data/system/nano_video.json", &st) != 0) return -1;
    return (int64_t)st.st_mtim.tv_sec * 1000000000LL + st.st_mtim.tv_nsec;
}

bool NanoMenu::loadVideoConfig() {
    const char* path = "/data/system/nano_video.json";
    int fd = open(path, O_RDONLY);
    if (fd < 0) { mVideoCfgStamp = -1; return false; }
    std::string content;
    struct stat st;
    if (fstat(fd, &st) == 0 && st.st_size > 0 && st.st_size < 8 * 1024 * 1024) {
        content.resize(st.st_size);
        ssize_t rd = read(fd, &content[0], st.st_size);
        if (rd > 0) content.resize(rd); else content.clear();
    }
    close(fd);
    mVideoCfgStamp = videoConfigStamp();
    if (content.empty()) return false;

    njson::Value root;
    if (!njson::parse(content, &root) || !root.isObject()) return false;

    mVideoFolders.clear();
    mVideos.clear();
    mVideoPlaylists.clear();
    mVideoCfgVersion = root.find("version") ? (int)root.find("version")->asNumber(0) : 0;

    if (const njson::Value* folders = root.find("folders"); folders && folders->isArray())
        for (const auto& f : folders->arr) if (f.isString()) mVideoFolders.push_back(f.str);

    if (const njson::Value* vids = root.find("videos"); vids && vids->isArray())
        for (const auto& v : vids->arr) {
            if (!v.isObject()) continue;
            VideoItem it;
            it.file = v.getString("file");
            if (it.file.empty()) continue;
            it.name = v.getString("n");
            it.vcodec = v.getString("vc");
            it.acodec = v.getString("ac");
            it.durationSec = v.find("dur") ? v.find("dur")->asNumber(0) : 0;
            it.w = v.getInt("w", 0);
            it.h = v.getInt("h", 0);
            it.sz = v.find("sz") ? (int64_t)v.find("sz")->asNumber(0) : 0;
            it.mtime = v.find("mtime") ? (int64_t)v.find("mtime")->asNumber(0) : 0;
            it.resumeSec = v.find("pos") ? v.find("pos")->asNumber(0) : 0;   // Resume position
            mVideos.push_back(std::move(it));
        }

    if (const njson::Value* pls = root.find("playlists"); pls && pls->isArray())
        for (const auto& p : pls->arr) {
            if (!p.isObject()) continue;
            VideoPlaylist pl; pl.name = p.getString("name");
            if (pl.name.empty()) continue;
            if (const njson::Value* files = p.find("files"); files && files->isArray())
                for (const auto& f : files->arr) if (f.isString()) pl.files.push_back(f.str);
            mVideoPlaylists.push_back(std::move(pl));
        }
    VLOGI("NanoMenu: loaded video library (%zu folders, %zu videos, %zu playlists)",
          mVideoFolders.size(), mVideos.size(), mVideoPlaylists.size());
    return true;
}

void NanoMenu::saveVideoConfig() {
    njson::Value root = njson::Value::makeObject();
    root.set("version") = njson::Value::makeNumber(kVideoMetaVersion);
    njson::Value folders = njson::Value::makeArray();
    for (const auto& f : mVideoFolders) folders.arr.push_back(njson::Value::makeString(f));
    root.set("folders") = std::move(folders);
    njson::Value vids = njson::Value::makeArray();
    for (const auto& it : mVideos) {
        njson::Value v = njson::Value::makeObject();
        v.set("file") = njson::Value::makeString(it.file);
        v.set("n") = njson::Value::makeString(it.name);
        v.set("vc") = njson::Value::makeString(it.vcodec);
        v.set("ac") = njson::Value::makeString(it.acodec);
        v.set("dur") = njson::Value::makeNumber(it.durationSec);
        v.set("w") = njson::Value::makeNumber(it.w);
        v.set("h") = njson::Value::makeNumber(it.h);
        v.set("sz") = njson::Value::makeNumber((double)it.sz);
        v.set("mtime") = njson::Value::makeNumber((double)it.mtime);
        if (it.resumeSec > 0.0) v.set("pos") = njson::Value::makeNumber((double)(int64_t)it.resumeSec);   // whole seconds
        vids.arr.push_back(std::move(v));
    }
    root.set("videos") = std::move(vids);
    njson::Value pls = njson::Value::makeArray();
    for (const auto& pl : mVideoPlaylists) {
        njson::Value p = njson::Value::makeObject();
        p.set("name") = njson::Value::makeString(pl.name);
        njson::Value files = njson::Value::makeArray();
        for (const auto& f : pl.files) files.arr.push_back(njson::Value::makeString(f));
        p.set("files") = std::move(files);
        pls.arr.push_back(std::move(p));
    }
    root.set("playlists") = std::move(pls);
    std::string text = njson::serialize(root, true);

    const char* path = "/data/system/nano_video.json";
    const char* tmp = "/data/system/nano_video.json.tmp";
    int fd = open(tmp, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) { VLOGW("NanoMenu: cannot write %s (errno %d)", tmp, errno); return; }
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
    mVideoCfgVersion = kVideoMetaVersion;
    mVideoCfgStamp = videoConfigStamp();
    VLOGI("NanoMenu: wrote nano_video.json (%zu videos)", mVideos.size());
}

// ---------------------------------------------------------------------------
// Lazy load + scan.
// ---------------------------------------------------------------------------
void NanoMenu::videoOnCatFocus() {
    if (mVideoLoaded) return;
    if (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()
        && mPs3Cats[mPs3CatIdx].name == "Video") {
        videoEnsureLoaded();
    }
}

void NanoMenu::videoEnsureLoaded() {
    if (mVideoLoaded) return;
    mVideoLoaded = true;
    loadVideoConfig();
    videoSortApply();
    mVideoCatsStale = true;
    if (!mVideoScanRunning) videoScanAsync();   // always: default media dirs are scanned too
}

// Standard media subdirectories scanned on every storage medium, in addition to
// the user's imported folders, so media on internal storage or any inserted SD/USB
// card is found automatically. Only directories that actually exist are returned, so
// an unmounted volume or absent folder simply contributes nothing.
std::vector<std::string> NanoMenu::nanoDefaultMediaDirs(int kind) const {
    std::vector<const char*> subs;
    if (kind == 0)      subs = {"DCIM", "Pictures"};          // photo
    else if (kind == 1) subs = {"DCIM", "Movies"};            // video
    else                subs = {"Music"};                     // music
    // Storage roots: internal + every mounted external volume under /storage. The
    // internal root is "/storage/emulated/0" (NOT "/sdcard"): the folder picker stores
    // user folders with that prefix, so using the same prefix lets the merge below
    // string-dedup a default dir against an identical user folder (the two are the
    // same directory via different paths and would otherwise be scanned twice).
    // Skip the "emulated" pool dir, the "self" symlink namespace, and the synthetic
    // "00000000-0000-0000-0000-*" volume ids (vold's internal-primary view + its
    // pre-created public mount points; the internal one duplicates the emulated view
    // and the rest are empty placeholders). A real SD/USB card mounts under its own
    // volume serial / UUID.
    std::vector<std::string> roots;
    roots.push_back("/storage/emulated/0");
    if (DIR* d = opendir("/storage")) {
        struct dirent* e;
        while ((e = readdir(d)) != nullptr) {
            if (e->d_name[0] == '.') continue;
            std::string n = e->d_name;
            if (n == "emulated" || n == "self") continue;
            if (n.rfind("00000000-0000-0000-0000-", 0) == 0) continue;   // synthetic/internal
            roots.push_back("/storage/" + n);
        }
        closedir(d);
    }
    std::vector<std::string> out;
    for (const auto& r : roots) {
        for (const char* s : subs) {
            std::string p = r + "/" + s;
            struct stat st;
            if (stat(p.c_str(), &st) == 0 && S_ISDIR(st.st_mode)) out.push_back(p);
        }
    }
    return out;
}

// User-imported folders merged with the existing default media dirs (deduped).
std::vector<std::string> NanoMenu::nanoMediaScanDirs(int kind, const std::vector<std::string>& userFolders) const {
    std::vector<std::string> out = userFolders;
    for (const auto& d : nanoDefaultMediaDirs(kind))
        if (std::find(out.begin(), out.end(), d) == out.end()) out.push_back(d);
    return out;
}

bool NanoMenu::videoStorageReady() const {
    // Defaults live on storage that mounts at/after boot, so always wait for boot.
    char bc[PROPERTY_VALUE_MAX] = {0};
    property_get("sys.boot_completed", bc, "0");
    if (bc[0] != '1') return false;
    std::vector<std::string> dirs = nanoMediaScanDirs(1, mVideoFolders);
    if (dirs.empty()) return true;   // nothing to scan (worker guards against wiping)
    for (const auto& f : dirs) {
        DIR* d = opendir(f.c_str());
        if (d) { closedir(d); return true; }
    }
    return false;
}

void NanoMenu::videoScanAsync() {
    if (mVideoScanRunning) return;
    if (!videoStorageReady()) { mVideoScanPending = true; return; }
    mVideoScanPending = false;
    mVideoScanRunning = true;
    std::thread(&NanoMenu::videoScanThreadFunc, this).detach();
}

void NanoMenu::videoRefresh() {
    videoEnsureLoaded();
    videoScanAsync();
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == VIDEO_FOLDER)
        buildVideoFoldersScreen(mPs3Stack.back());
}

void NanoMenu::videoScanThreadFunc() {
    std::vector<std::string> folders = nanoMediaScanDirs(1, mVideoFolders);   // user folders + default media dirs
    bool forceReprobe = (mVideoCfgVersion < kVideoMetaVersion);
    std::vector<VideoItem> cacheVec = mVideos;
    std::map<std::string, const VideoItem*> cache;
    if (!forceReprobe) for (const auto& v : cacheVec) cache[v.file] = &v;
    // Resume positions must survive a re-probe / metaVersion bump (cache is empty
    // when forceReprobe), so carry them by path unconditionally.
    std::map<std::string, double> resumeCarry;
    for (const auto& v : cacheVec) if (v.resumeSec > 0.0) resumeCarry[v.file] = v.resumeSec;

    std::vector<std::string> files;
    for (const auto& f : folders) vScanDirRecursive(f, files, 0);
    // Storage-not-ready guard: never publish an empty result that would wipe the
    // saved library when the source is merely not mounted yet. Publish empty only
    // when every scan dir is openable (mounted + genuinely empty), or there are no
    // scan dirs and nothing was saved before.
    if (files.empty()) {
        bool safe;
        if (folders.empty()) safe = cacheVec.empty();
        else { safe = true; for (const auto& f : folders) { DIR* d = opendir(f.c_str()); if (!d) safe = false; else closedir(d); } }
        if (!safe) {
            mVideoScanRunning = false; mVideoScanPending = true;
            VLOGW("NanoMenu: video scan found nothing + a source is unreadable; deferring");
            return;
        }
    }
    std::sort(files.begin(), files.end());
    files.erase(std::unique(files.begin(), files.end()), files.end());

    std::vector<VideoItem> results;
    results.reserve(files.size());
    for (const auto& path : files) {
        struct stat st;
        int64_t mt = 0; int64_t sz = 0;
        if (stat(path.c_str(), &st) == 0) {
            mt = (int64_t)st.st_mtim.tv_sec * 1000000000LL + st.st_mtim.tv_nsec;
            sz = (int64_t)st.st_size;
        }
        auto it = cache.find(path);
        if (it != cache.end() && it->second->mtime == mt && mt != 0) {
            results.push_back(*it->second);   // unchanged: reuse (no re-probe)
            continue;
        }
        VideoItem v;
        v.file = path; v.mtime = mt; v.sz = sz;
        NanoVideo::Meta meta;
        if (NanoVideo::probe(path, meta)) {
            v.durationSec = meta.durationSec;
            v.w = meta.width; v.h = meta.height;
            v.vcodec = meta.vcodec; v.acodec = meta.acodec;
        }
        v.name = vStripExt(vBaseName(path));
        auto rc = resumeCarry.find(path);
        if (rc != resumeCarry.end()) v.resumeSec = rc->second;   // keep Resume across re-probe
        results.push_back(std::move(v));
    }
    {
        std::lock_guard<std::mutex> lk(mVideoScanMutex);
        mVideoScanResults = std::move(results);
        mVideoScanReady = true;
    }
    mVideoScanRunning = false;
    VLOGI("NanoMenu: video scan finished (%zu files)", files.size());
}

void NanoMenu::videoDrainScanResults() {
    if (!mVideoScanReady) return;
    {
        std::lock_guard<std::mutex> lk(mVideoScanMutex);
        mVideos = std::move(mVideoScanResults);
        mVideoScanResults.clear();
        mVideoScanReady = false;
    }
    videoSortApply();
    saveVideoConfig();
    mVideoCatsStale = true;
}

// ---------------------------------------------------------------------------
// Sort By (Y): name / date (mtime) / duration.
// ---------------------------------------------------------------------------
bool NanoMenu::videoSortLess(int a, int b) const {
    if (a < 0 || a >= (int)mVideos.size() || b < 0 || b >= (int)mVideos.size()) return a < b;
    const VideoItem& x = mVideos[a]; const VideoItem& y = mVideos[b];
    auto nameLess = [&]() { return strcasecmp(x.name.c_str(), y.name.c_str()) < 0; };
    if (mVideoSortField == 1) {            // date (file mtime)
        if (x.mtime != y.mtime) return mVideoSortDir == 0 ? (x.mtime > y.mtime) : (x.mtime < y.mtime);
        return nameLess();
    }
    if (mVideoSortField == 2) {            // duration
        if (x.durationSec != y.durationSec)
            return mVideoSortDir == 0 ? (x.durationSec > y.durationSec) : (x.durationSec < y.durationSec);
        return nameLess();
    }
    return nameLess();                      // name: always ascending
}

void NanoMenu::videoSortApply() {
    std::vector<int> order(mVideos.size());
    for (size_t i = 0; i < mVideos.size(); i++) order[i] = (int)i;
    std::sort(order.begin(), order.end(), [&](int a, int b){ return videoSortLess(a, b); });
    std::vector<VideoItem> sorted;
    sorted.reserve(mVideos.size());
    for (int i : order) sorted.push_back(std::move(mVideos[i]));
    mVideos = std::move(sorted);
}

std::string NanoMenu::videoSortLabelCur() const {
    switch (mVideoSortField) {
        case 1:  return std::string("Date") + (mVideoSortDir == 0 ? " (newest)" : " (oldest)");
        case 2:  return std::string("Length") + (mVideoSortDir == 0 ? " (longest)" : " (shortest)");
        default: return "Title";
    }
}

void NanoMenu::videoSortCycleY() {
    // 4 modes: Title, Date newest, Date oldest, Length longest.
    static const int kField[4] = {0, 1, 1, 2};
    static const int kDir[4]   = {1, 0, 1, 0};
    int cur = 0;
    for (int i = 0; i < 4; i++)
        if (kField[i] == mVideoSortField && (mVideoSortField == 0 || kDir[i] == mVideoSortDir)) { cur = i; break; }
    int nx = (cur + 1) % 4;
    mVideoSortField = kField[nx]; mVideoSortDir = kDir[nx];
    videoSortApply();
    mVideoCatsStale = true;
    photoShowBanner(videoSortLabelCur());   // reuse the photo banner overlay
}

// ---------------------------------------------------------------------------
// Video-column content: scanned video files (flat), appended below the firmware
// stub items, mirroring the Music album list.
// ---------------------------------------------------------------------------
void NanoMenu::buildVideoColumnItems(std::vector<Ps3Item>& out) {
    GLuint filmNmap = nmapForIcon(4);   // video category glyph as the row bevel
    for (size_t i = 0; i < mVideos.size(); i++) {
        const VideoItem& v = mVideos[i];
        Ps3Item it; it.label = v.name; it.kind = PS3_VIDEO_FILE; it.a = (int)i;
        it.payloadStr = v.file;
        std::string sub = v.vcodec.empty() ? std::string() : v.vcodec;
        if (v.w > 0 && v.h > 0) {
            char wh[32]; snprintf(wh, sizeof(wh), "%s%dx%d", sub.empty() ? "" : "  ", v.w, v.h);
            sub += wh;
        }
        it.value = sub;
        it.iconTex = 0; it.nmapTex = filmNmap; it.iconR = it.iconG = it.iconB = 1.0f;
        out.push_back(it);
    }
}

// ---------------------------------------------------------------------------
// Video playlists (nano addition; the web video section has none) - mirror the music
// + photo playlist machinery: a Playlists screen (Create + each playlist), a per-playlist
// file submenu, create/add, and the column "Add to Playlist" chooser. Persisted in
// nano_video.json (user-only, preserved across rescans).
// ---------------------------------------------------------------------------
void NanoMenu::videoCreatePlaylist(const std::string& name) {
    if (name.empty()) return;
    for (const auto& pl : mVideoPlaylists) if (pl.name == name) return;   // dedup by name
    VideoPlaylist pl; pl.name = name;
    mVideoPlaylists.push_back(std::move(pl));
    saveVideoConfig();
}
void NanoMenu::videoAddToPlaylist(int plIdx, const std::string& file) {
    if (plIdx < 0 || plIdx >= (int)mVideoPlaylists.size() || file.empty()) return;
    auto& files = mVideoPlaylists[plIdx].files;
    if (std::find(files.begin(), files.end(), file) == files.end()) files.push_back(file);
    saveVideoConfig();
}
void NanoMenu::buildVideoPlaylistsScreen(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.title = "Playlists"; out.screenKind = GS_NONE;
    { Ps3Item it; it.label = "Create New Playlist"; it.kind = PS3_VIDEO_PL_NEW;
      it.iconTex = 0; it.nmapTex = nmapForIcon(50); it.iconR = it.iconG = it.iconB = 1.0f;
      out.items.push_back(it); }
    for (size_t p = 0; p < mVideoPlaylists.size(); p++) {
        Ps3Item it; it.label = mVideoPlaylists[p].name; it.kind = PS3_VIDEO_PLAYLIST; it.a = (int)p;
        size_t n = mVideoPlaylists[p].files.size();
        char v[32]; snprintf(v, sizeof(v), "%zu %s", n, n == 1 ? "Video" : "Videos"); it.value = v;
        it.iconTex = 0; it.nmapTex = nmapForIcon(62); it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
}
void NanoMenu::buildVideoPlaylistSubmenu(int plIdx, Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = GS_NONE;
    if (plIdx < 0 || plIdx >= (int)mVideoPlaylists.size()) { out.title = "Playlist"; return; }
    out.title = mVideoPlaylists[plIdx].name;
    GLuint filmNmap = nmapForIcon(4);
    for (const auto& f : mVideoPlaylists[plIdx].files) {
        int vi = -1;
        for (size_t i = 0; i < mVideos.size(); i++) if (mVideos[i].file == f) { vi = (int)i; break; }
        if (vi < 0) continue;   // file no longer in the library
        const VideoItem& v = mVideos[vi];
        Ps3Item it; it.label = v.name; it.kind = PS3_VIDEO_FILE; it.a = vi; it.payloadStr = v.file;
        std::string sub = v.vcodec;
        if (v.w > 0 && v.h > 0) { char wh[32]; snprintf(wh, sizeof(wh), "%s%dx%d", sub.empty() ? "" : "  ", v.w, v.h); sub += wh; }
        it.value = sub;
        it.iconTex = 0; it.nmapTex = filmNmap; it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
}
void NanoMenu::vidOpenAddChooser(const std::string& file) {
    mVidPlChooserFile = file;
    mVidPlChooserOpts.clear();
    mVidPlChooserOpts.push_back("New Playlist...");
    for (const auto& p : mVideoPlaylists) mVidPlChooserOpts.push_back(p.name);
    mVidPlChooserSel = 0;
    mVidPlChooserActive = true;
}
void NanoMenu::vidPlChooserMove(int dir) {
    if (!mVidPlChooserActive) return;
    int n = (int)mVidPlChooserOpts.size(); if (n <= 0) return;
    mVidPlChooserSel = (mVidPlChooserSel + dir + n) % n;
}
void NanoMenu::vidPlChooserCancel() { mVidPlChooserActive = false; }
void NanoMenu::vidPlChooserSelect() {
    if (!mVidPlChooserActive) return;
    std::string file = mVidPlChooserFile;
    int sel = mVidPlChooserSel;
    mVidPlChooserActive = false;
    if (sel == 0) {
        openOskForPassword("Enter a name for the playlist",
            [this, file](const std::string& nm) {
                if (nm.empty()) return;
                videoCreatePlaylist(nm);
                videoAddToPlaylist((int)mVideoPlaylists.size() - 1, file);
                photoShowBanner("Added to the playlist");
            });
        mOskPasswordMode = false; mOskPlaintext = true;   // a playlist name is plain text, not masked
    } else {
        videoAddToPlaylist(sel - 1, file);
        photoShowBanner("Added to the playlist");
    }
}
void NanoMenu::drawVidPlChooser() {
    float t = mVidPlChooserAnim; if (t < 0.004f) return;
    int W = mWidth, H = mHeight;
    drawQuad(0, 0, (float)W, (float)H, 0, 0, 0, 0.5f * t);
    float fs = ps3::fontScale(26.0f), lh = H * 0.058f;
    int n = (int)mVidPlChooserOpts.size();
    float mw = measureText("Add to Playlist", fs);
    for (auto& o : mVidPlChooserOpts) mw = fmaxf(mw, measureText(o.c_str(), fs));
    float pw = mw + W * 0.08f, ph = lh * (n + 1) + H * 0.05f;
    float px = (W - pw) * 0.5f, py = (H - ph) * 0.5f;
    drawQuad(px, py, pw, ph, 0.07f, 0.08f, 0.10f, 0.92f * t);
    float cx = W * 0.5f, titleY = py + H * 0.05f;
    float tw = measureText("Add to Playlist", fs);
    drawText("Add to Playlist", cx - tw * 0.5f, ps3::baselineToTopY(titleY, fs), fs, 1, 1, 1, 0.95f * t);
    for (int i = 0; i < n; i++) {
        float oy = titleY + lh * (i + 1);
        bool sel = (i == mVidPlChooserSel);
        if (sel) drawQuad(px + W * 0.02f, oy - lh * 0.42f, pw - W * 0.04f, lh * 0.82f, 1, 1, 1, 0.18f * t);
        float ow = measureText(mVidPlChooserOpts[i].c_str(), fs);
        drawText(mVidPlChooserOpts[i].c_str(), cx - ow * 0.5f, ps3::baselineToTopY(oy, fs), fs,
                 sel ? 1.0f : 0.82f, sel ? 1.0f : 0.82f, sel ? 1.0f : 0.85f, 0.95f * t);
    }
}

// ---------------------------------------------------------------------------
// Folder import: the video folders screen + the picker reuse (mFolderPickTarget=3).
// ---------------------------------------------------------------------------
void NanoMenu::buildVideoFoldersScreen(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.title = "Video Folders"; out.screenKind = VIDEO_FOLDER;
    { Ps3Item it; it.label = "Add Folder..."; it.kind = PS3_GS_ADDFOLDER;
      it.iconTex = 0; it.nmapTex = nmapForIcon(50); it.iconR = it.iconG = it.iconB = 1.0f;
      out.items.push_back(it); }
    { Ps3Item it; it.label = mVideoScanRunning ? "Refreshing..." : "Refresh";
      it.kind = PS3_VIDEO_REFRESH;
      it.value = mVideoScanRunning ? "" : "Rescan video folders";
      it.iconTex = 0; it.nmapTex = nmapForIcon(8); it.iconR = it.iconG = it.iconB = 1.0f;
      out.items.push_back(it); }
    for (size_t i = 0; i < mVideoFolders.size(); i++) {
        int cnt = 0;
        for (const auto& v : mVideos)
            if (v.file.compare(0, mVideoFolders[i].size(), mVideoFolders[i]) == 0) cnt++;
        Ps3Item it; it.label = mVideoFolders[i]; it.kind = PS3_VIDEO_FOLDER_ROW; it.a = (int)i;
        char val[24]; snprintf(val, sizeof(val), "%d videos", cnt); it.value = val;
        it.iconTex = 0; it.nmapTex = nmapForIcon(62); it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
    if (mVideoFolders.empty()) {
        Ps3Item it; it.label = mVideoScanRunning ? "Scanning..." : "No video folders added yet";
        it.kind = PS3_GS_FIELD; it.a = -1;
        it.iconTex = 0; it.nmapTex = 0; it.iconR = it.iconG = it.iconB = 0.55f;
        out.items.push_back(it);
    }
}

void NanoMenu::videoOpenFolders() {
    videoEnsureLoaded();
    mFolderPickTarget = 3;   // route the folder browser's "Select" to the video library
    std::vector<Ps3Item> parentSnap = mPs3Stack.empty() ? std::vector<Ps3Item>() : mPs3Stack.back().items;
    int parentSel = mPs3Stack.empty() ? 0 : mPs3Stack.back().sel;
    Ps3Level lvl; buildVideoFoldersScreen(lvl);
    mPs3Stack.push_back(lvl);
    mPs3SubParentItems = std::move(parentSnap);
    mPs3SubParentIdx = parentSel; mPs3SubChildItems = mPs3Stack.back().items;
    mPs3SubDir = 1; mPs3SubAnimStart = mEffectTime; mPs3SubAnim = 0.0f;
    mPs3AnimItem = 0.0f; mPs3ItemAnimStart = -1.0f;
}

void NanoMenu::videoFolderSelect(const std::string& path) {
    if (path.empty()) return;
    for (const auto& f : mVideoFolders) if (f == path) {
        if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_FOLDERBROWSE) mPs3Stack.pop_back();
        return;
    }
    mVideoFolders.push_back(path);
    VLOGI("NanoMenu: added video folder %s", path.c_str());
    saveVideoConfig();
    videoScanAsync();
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_FOLDERBROWSE) mPs3Stack.pop_back();
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == VIDEO_FOLDER)
        buildVideoFoldersScreen(mPs3Stack.back());
}

void NanoMenu::videoRemoveFolder(int idx) {
    if (idx < 0 || idx >= (int)mVideoFolders.size()) return;
    mVideoFolders.erase(mVideoFolders.begin() + idx);
    saveVideoConfig();
    videoScanAsync();
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == VIDEO_FOLDER)
        buildVideoFoldersScreen(mPs3Stack.back());
}

// ===========================================================================
// Multiple audio tracks + subtitles (web audioTracks / subList / vidParseCues).
// Audio: enumerate all audio tracks; switching re-opens mVidAudio on that exact
// extractor track. Subtitles: embedded text (mov_text, mime text/3gpp-tt) read as
// cues, plus external .srt/.vtt sidecars parsed off disk. Bitmap subs (DVB/PGS) are
// NOT supported - the Android NDK has no decoder + the extractors drop those tracks.
// ===========================================================================
static const char* vidAudCodecBadge(const char* mime) {
    if (!mime) return "Audio";
    if (!strcmp(mime, "audio/mpeg")) return "MP3";
    if (!strncmp(mime, "audio/mp4a", 10) || !strcmp(mime, "audio/aac")) return "AAC";
    if (!strcmp(mime, "audio/flac")) return "FLAC";
    if (!strcmp(mime, "audio/raw"))  return "PCM";
    if (!strcmp(mime, "audio/vorbis")) return "Vorbis";
    if (!strcmp(mime, "audio/opus"))   return "Opus";
    if (!strcmp(mime, "audio/ac3"))    return "AC3";
    if (!strcmp(mime, "audio/eac3"))   return "EAC3";
    return "Audio";
}
// Only TEXT subtitle mimes are renderable (we read the cue text directly). Bitmap
// subtitle mimes (dvb/pgs/vobsub) are deliberately excluded.
static bool vidIsTextSubMime(const char* mime) {
    if (!mime) return false;
    return !strcmp(mime, "text/3gpp-tt") ||        // mov_text / tx3g (mp4)
           !strcmp(mime, "application/x-subrip") ||
           !strcmp(mime, "text/x-subrip") ||
           !strcmp(mime, "application/x-media-subtitle/srt") ||
           !strcmp(mime, "text/vtt");
}

// SRT/VTT cue parser, 1:1 with the web vidParseCues/vidParseTime.
std::vector<NanoMenu::VidCue> NanoMenu::vidParseSrt(const std::string& textIn) {
    std::vector<VidCue> cues;
    std::string text = textIn;
    text.erase(std::remove(text.begin(), text.end(), '\r'), text.end());
    auto trim = [](std::string s) {
        size_t a = s.find_first_not_of(" \t\n"); size_t b = s.find_last_not_of(" \t\n");
        return a == std::string::npos ? std::string() : s.substr(a, b - a + 1);
    };
    auto parseTime = [](std::string s) -> double {
        size_t a = s.find_first_not_of(" \t"); size_t b = s.find_last_not_of(" \t");
        if (a == std::string::npos) return 0.0; s = s.substr(a, b - a + 1);
        for (auto& c : s) if (c == ',') c = '.';
        std::vector<std::string> p; size_t pos = 0, col;
        while ((col = s.find(':', pos)) != std::string::npos) { p.push_back(s.substr(pos, col - pos)); pos = col + 1; }
        p.push_back(s.substr(pos));
        double h = 0, m = 0, sec = 0;
        if (p.size() == 3) { h = atof(p[0].c_str()); m = atof(p[1].c_str()); sec = atof(p[2].c_str()); }
        else if (p.size() == 2) { m = atof(p[0].c_str()); sec = atof(p[1].c_str()); }
        else { sec = atof(p[0].c_str()); }
        return h * 3600.0 + m * 60.0 + sec;
    };
    size_t i = 0, n = text.size();
    while (i < n) {
        size_t dbl = text.find("\n\n", i);
        std::string blk = text.substr(i, (dbl == std::string::npos ? n : dbl) - i);
        i = (dbl == std::string::npos) ? n : dbl + 2;
        while (i < n && text[i] == '\n') i++;
        std::vector<std::string> lines; size_t lp = 0, nl;
        while ((nl = blk.find('\n', lp)) != std::string::npos) { lines.push_back(blk.substr(lp, nl - lp)); lp = nl + 1; }
        lines.push_back(blk.substr(lp));
        int ai = -1;
        for (size_t j = 0; j < lines.size(); j++) if (lines[j].find("-->") != std::string::npos) { ai = (int)j; break; }
        if (ai < 0) continue;
        size_t arrow = lines[ai].find("-->");
        std::string endRest = lines[ai].substr(arrow + 3);
        { size_t a = endRest.find_first_not_of(" \t"); if (a != std::string::npos) endRest = endRest.substr(a);
          size_t sp = endRest.find_first_of(" \t"); if (sp != std::string::npos) endRest = endRest.substr(0, sp); }
        double start = parseTime(lines[ai].substr(0, arrow)), end = parseTime(endRest);
        std::string txt;
        for (size_t j = ai + 1; j < lines.size(); j++) { if (!txt.empty()) txt += "\n"; txt += lines[j]; }
        std::string clean; bool intag = false;
        for (char c : txt) { if (c == '<') intag = true; else if (c == '>') intag = false;
                             else if (!intag) clean += (c == '|' ? '\n' : c); }
        clean = trim(clean);
        if (!clean.empty() && end > start) { VidCue cu; cu.t = start; cu.d = end - start; cu.text = clean; cues.push_back(cu); }
    }
    return cues;
}

// Read an embedded TEXT subtitle track (mov_text: U16-BE length + UTF-8) into cues.
// Per-sample durations are derived from the next sample's start (web does the same).
void NanoMenu::vidReadEmbeddedCues(const std::string& file, int trackIdx, std::vector<VidCue>& out) {
    int fd = ::open(file.c_str(), O_RDONLY);
    if (fd < 0) return;
    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size <= 0) { ::close(fd); return; }
    AMediaExtractor* ex = AMediaExtractor_new();
    if (AMediaExtractor_setDataSourceFd(ex, fd, 0, st.st_size) == AMEDIA_OK) {
        AMediaExtractor_selectTrack(ex, trackIdx);
        std::vector<std::pair<double, std::string>> raw;
        std::vector<uint8_t> buf(8192);
        for (int guard = 0; guard < 100000; guard++) {
            ssize_t n = AMediaExtractor_readSampleData(ex, buf.data(), buf.size());
            if (n < 0) break;
            int64_t pts = AMediaExtractor_getSampleTime(ex);
            double start = pts >= 0 ? (double)pts / 1e6 : 0.0;
            std::string txt;
            if (n >= 2) {
                int len = (buf[0] << 8) | buf[1];          // mov_text 2-byte BE length prefix
                if (len > 0 && 2 + len <= n) txt.assign((char*)buf.data() + 2, len);
                else if (len == 0) txt.clear();            // clear-cue sample (gap)
                else txt.assign((char*)buf.data(), n);     // raw text fallback (subrip-style)
            }
            raw.push_back({start, txt});
            if (!AMediaExtractor_advance(ex)) break;
        }
        for (size_t k = 0; k < raw.size(); k++) {
            if (raw[k].second.empty()) continue;
            double end = (k + 1 < raw.size()) ? raw[k + 1].first : raw[k].first + 3.0;
            if (end <= raw[k].first) end = raw[k].first + 3.0;
            VidCue c; c.t = raw[k].first; c.d = end - raw[k].first; c.text = raw[k].second;
            out.push_back(c);
        }
    }
    AMediaExtractor_delete(ex);
    ::close(fd);
}

// ===========================================================================
// Chapter parsing (Scene Search). The NDK AMediaExtractor does not surface
// chapters, so parse the container directly: MP4/MOV via the QuickTime chapter
// text track (referenced by the video track's tref/chap) with a Nero `chpl`
// fallback, and Matroska/WebM via the EBML Chapters element. File-offset reads
// only (pread), no whole-file load, nothing resident after parsing.
// ===========================================================================
namespace {

// Standalone chapter item the free parsers fill (NanoMenu::VidChapter is private).
struct ChapItem { double t = 0.0; std::string title; };

static bool vidPreadAll(int fd, int64_t off, void* buf, size_t len) {
    uint8_t* p = (uint8_t*)buf; size_t got = 0;
    while (got < len) {
        ssize_t n = pread(fd, p + got, len - got, off + (int64_t)got);
        if (n <= 0) return false;
        got += (size_t)n;
    }
    return true;
}
static inline uint16_t vidRdU16(const uint8_t* p) { return (uint16_t)((p[0] << 8) | p[1]); }
static inline uint32_t vidRdU32(const uint8_t* p) {
    return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 8) | p[3];
}
static inline uint64_t vidRdU64(const uint8_t* p) {
    uint64_t v = 0; for (int i = 0; i < 8; i++) v = (v << 8) | p[i]; return v;
}

// --- MP4/MOV box helpers ---------------------------------------------------
// Find the first child box of fourcc within [start,end); returns its payload range.
static bool mp4FindBox(int fd, int64_t start, int64_t end, const char* fourcc,
                       int64_t& payOff, int64_t& payLen) {
    int64_t o = start;
    while (o + 8 <= end) {
        uint8_t hdr[16];
        if (!vidPreadAll(fd, o, hdr, 8)) return false;
        uint64_t sz = vidRdU32(hdr); int hdrLen = 8;
        if (sz == 1) { if (!vidPreadAll(fd, o + 8, hdr + 8, 8)) return false; sz = vidRdU64(hdr + 8); hdrLen = 16; }
        else if (sz == 0) sz = (uint64_t)(end - o);
        if (sz < (uint64_t)hdrLen || o + (int64_t)sz > end) break;
        if (!memcmp(hdr + 4, fourcc, 4)) { payOff = o + hdrLen; payLen = (int64_t)sz - hdrLen; return true; }
        o += (int64_t)sz;
    }
    return false;
}
// Collect the payload ranges of every child box of fourcc within [start,end).
static void mp4EachBox(int fd, int64_t start, int64_t end, const char* fourcc,
                       std::vector<std::pair<int64_t, int64_t>>& out) {
    int64_t o = start;
    while (o + 8 <= end) {
        uint8_t hdr[16];
        if (!vidPreadAll(fd, o, hdr, 8)) return;
        uint64_t sz = vidRdU32(hdr); int hdrLen = 8;
        if (sz == 1) { if (!vidPreadAll(fd, o + 8, hdr + 8, 8)) return; sz = vidRdU64(hdr + 8); hdrLen = 16; }
        else if (sz == 0) sz = (uint64_t)(end - o);
        if (sz < (uint64_t)hdrLen || o + (int64_t)sz > end) return;
        if (!memcmp(hdr + 4, fourcc, 4)) out.push_back({o + hdrLen, (int64_t)sz - hdrLen});
        o += (int64_t)sz;
    }
}

// Read the QuickTime chapter text track (sample text = U16-BE length + UTF-8),
// times from stts/mdhd, sample file offsets from stsc/stco|co64/stsz.
static bool mp4ChapterTrack(int fd, int64_t moovOff, int64_t moovEnd,
                            std::vector<ChapItem>& out) {
    std::vector<std::pair<int64_t, int64_t>> traks;
    mp4EachBox(fd, moovOff, moovEnd, "trak", traks);
    if (traks.empty()) return false;
    std::vector<uint32_t> trackIds(traks.size(), 0);
    std::vector<uint32_t> chapIds;
    for (size_t i = 0; i < traks.size(); i++) {
        int64_t tOff = traks[i].first, tEnd = tOff + traks[i].second, off, len;
        if (mp4FindBox(fd, tOff, tEnd, "tkhd", off, len) && len >= 24) {
            uint8_t b[24];
            if (vidPreadAll(fd, off, b, 24)) trackIds[i] = (b[0] == 1) ? vidRdU32(b + 20) : vidRdU32(b + 12);
        }
        int64_t trefOff, trefLen;
        if (mp4FindBox(fd, tOff, tEnd, "tref", trefOff, trefLen)) {
            int64_t chapOff, chapLen;
            if (mp4FindBox(fd, trefOff, trefOff + trefLen, "chap", chapOff, chapLen)) {
                for (int64_t k = 0; k + 4 <= chapLen; k += 4) {
                    uint8_t b[4]; if (vidPreadAll(fd, chapOff + k, b, 4)) chapIds.push_back(vidRdU32(b));
                }
            }
        }
    }
    int chapTrak = -1;
    for (uint32_t cid : chapIds) {
        for (size_t i = 0; i < traks.size() && chapTrak < 0; i++) if (trackIds[i] == cid) chapTrak = (int)i;
        if (chapTrak >= 0) break;
    }
    if (chapTrak < 0) return false;

    int64_t tOff = traks[chapTrak].first, tEnd = tOff + traks[chapTrak].second;
    int64_t mdiaOff, mdiaLen; if (!mp4FindBox(fd, tOff, tEnd, "mdia", mdiaOff, mdiaLen)) return false;
    int64_t mdiaEnd = mdiaOff + mdiaLen;
    uint32_t timescale = 1000;
    int64_t mdhdOff, mdhdLen;
    if (mp4FindBox(fd, mdiaOff, mdiaEnd, "mdhd", mdhdOff, mdhdLen)) {
        uint8_t b[24]; if (vidPreadAll(fd, mdhdOff, b, 24)) timescale = (b[0] == 1) ? vidRdU32(b + 20) : vidRdU32(b + 12);
    }
    if (timescale == 0) timescale = 1000;
    int64_t minfOff, minfLen; if (!mp4FindBox(fd, mdiaOff, mdiaEnd, "minf", minfOff, minfLen)) return false;
    int64_t stblOff, stblLen; if (!mp4FindBox(fd, minfOff, minfOff + minfLen, "stbl", stblOff, stblLen)) return false;
    int64_t stblEnd = stblOff + stblLen;

    std::vector<double> times;
    int64_t off, len;
    if (mp4FindBox(fd, stblOff, stblEnd, "stts", off, len)) {
        uint8_t hb[8];
        if (vidPreadAll(fd, off, hb, 8)) {
            uint32_t n = vidRdU32(hb + 4); uint64_t cum = 0;
            for (uint32_t e = 0; e < n && e < 100000; e++) {
                uint8_t eb[8]; if (!vidPreadAll(fd, off + 8 + (int64_t)e * 8, eb, 8)) break;
                uint32_t cnt = vidRdU32(eb), delta = vidRdU32(eb + 4);
                for (uint32_t s = 0; s < cnt && times.size() < 100000; s++) { times.push_back((double)cum / timescale); cum += delta; }
            }
        }
    }
    std::vector<uint32_t> sizes;
    if (mp4FindBox(fd, stblOff, stblEnd, "stsz", off, len)) {
        uint8_t hb[12];
        if (vidPreadAll(fd, off, hb, 12)) {
            uint32_t uniform = vidRdU32(hb + 4), cnt = vidRdU32(hb + 8);
            if (uniform != 0) { for (uint32_t s = 0; s < cnt && s < 100000; s++) sizes.push_back(uniform); }
            else for (uint32_t s = 0; s < cnt && s < 100000; s++) {
                uint8_t sb[4]; if (!vidPreadAll(fd, off + 12 + (int64_t)s * 4, sb, 4)) break; sizes.push_back(vidRdU32(sb));
            }
        }
    }
    std::vector<uint64_t> chunkOff;
    if (mp4FindBox(fd, stblOff, stblEnd, "stco", off, len)) {
        uint8_t hb[8];
        if (vidPreadAll(fd, off, hb, 8)) { uint32_t n = vidRdU32(hb + 4);
            for (uint32_t e = 0; e < n && e < 100000; e++) { uint8_t eb[4]; if (!vidPreadAll(fd, off + 8 + (int64_t)e * 4, eb, 4)) break; chunkOff.push_back(vidRdU32(eb)); } }
    } else if (mp4FindBox(fd, stblOff, stblEnd, "co64", off, len)) {
        uint8_t hb[8];
        if (vidPreadAll(fd, off, hb, 8)) { uint32_t n = vidRdU32(hb + 4);
            for (uint32_t e = 0; e < n && e < 100000; e++) { uint8_t eb[8]; if (!vidPreadAll(fd, off + 8 + (int64_t)e * 8, eb, 8)) break; chunkOff.push_back(vidRdU64(eb)); } }
    }
    struct Stsc { uint32_t first, spc; };
    std::vector<Stsc> stsc;
    if (mp4FindBox(fd, stblOff, stblEnd, "stsc", off, len)) {
        uint8_t hb[8];
        if (vidPreadAll(fd, off, hb, 8)) { uint32_t n = vidRdU32(hb + 4);
            for (uint32_t e = 0; e < n && e < 100000; e++) { uint8_t eb[12]; if (!vidPreadAll(fd, off + 8 + (int64_t)e * 12, eb, 12)) break; stsc.push_back({vidRdU32(eb), vidRdU32(eb + 4)}); } }
    }
    // Resolve each sample's absolute file offset.
    std::vector<int64_t> sampleOff;
    if (!chunkOff.empty() && !stsc.empty() && !sizes.empty()) {
        size_t si = 0;
        for (size_t ci = 0; ci < chunkOff.size() && si < sizes.size(); ci++) {
            uint32_t spc = stsc.back().spc;
            for (size_t e = 0; e < stsc.size(); e++) {
                uint32_t fc = stsc[e].first, nextfc = (e + 1 < stsc.size()) ? stsc[e + 1].first : 0xffffffffu;
                if ((uint32_t)(ci + 1) >= fc && (uint32_t)(ci + 1) < nextfc) { spc = stsc[e].spc; break; }
            }
            int64_t o = (int64_t)chunkOff[ci];
            for (uint32_t s = 0; s < spc && si < sizes.size(); s++) { sampleOff.push_back(o); o += sizes[si]; si++; }
        }
    }
    size_t ns = sampleOff.size();
    if (times.size() < ns) ns = times.size();
    if (sizes.size() < ns) ns = sizes.size();
    for (size_t s = 0; s < ns; s++) {
        uint32_t sz = sizes[s]; if (sz < 2 || sz > 4096) continue;
        std::vector<uint8_t> buf(sz);
        if (!vidPreadAll(fd, sampleOff[s], buf.data(), sz)) continue;
        uint32_t tl = vidRdU16(buf.data()); if (tl > sz - 2) tl = sz - 2;
        std::string title((char*)buf.data() + 2, tl);
        // QuickTime text samples are UTF-8 (ffmpeg/Handbrake); honour a UTF-16-BE BOM if present.
        if (tl >= 2 && (uint8_t)title[0] == 0xFE && (uint8_t)title[1] == 0xFF) {
            std::string u8;
            for (size_t k = 2; k + 1 < title.size(); k += 2) {
                unsigned c = ((unsigned char)title[k] << 8) | (unsigned char)title[k + 1];
                if (c < 0x80) u8 += (char)c;
                else if (c < 0x800) { u8 += (char)(0xC0 | (c >> 6)); u8 += (char)(0x80 | (c & 0x3F)); }
                else { u8 += (char)(0xE0 | (c >> 12)); u8 += (char)(0x80 | ((c >> 6) & 0x3F)); u8 += (char)(0x80 | (c & 0x3F)); }
            }
            title = u8;
        } else if (title.size() >= 3 && (uint8_t)title[0] == 0xEF && (uint8_t)title[1] == 0xBB && (uint8_t)title[2] == 0xBF) {
            title = title.substr(3);   // strip a UTF-8 BOM
        }
        ChapItem ch; ch.t = times[s]; ch.title = title; out.push_back(ch);
    }
    return !out.empty();
}

// Nero chpl fallback: moov/udta/chpl. u8 ver, u24 flags, u32 reserved, u8 count,
// then per chapter: u64 start (100ns units) + u8 title-len + UTF-8 title.
static bool mp4Chpl(int fd, int64_t moovOff, int64_t moovEnd, std::vector<ChapItem>& out) {
    int64_t udtaOff, udtaLen;
    if (!mp4FindBox(fd, moovOff, moovEnd, "udta", udtaOff, udtaLen)) return false;
    int64_t chplOff, chplLen;
    if (!mp4FindBox(fd, udtaOff, udtaOff + udtaLen, "chpl", chplOff, chplLen) || chplLen < 9) return false;
    uint8_t hb[9]; if (!vidPreadAll(fd, chplOff, hb, 9)) return false;
    int count = hb[8];
    int64_t p = chplOff + 9, end = chplOff + chplLen;
    for (int i = 0; i < count && p + 9 <= end; i++) {
        uint8_t eb[9]; if (!vidPreadAll(fd, p, eb, 9)) break;
        uint64_t start100ns = vidRdU64(eb); int len2 = eb[8]; p += 9;
        std::string title;
        if (len2 > 0 && p + len2 <= end) { std::vector<uint8_t> tb(len2); if (vidPreadAll(fd, p, tb.data(), len2)) title.assign((char*)tb.data(), len2); }
        p += len2;
        ChapItem ch; ch.t = (double)start100ns / 1e7; ch.title = title; out.push_back(ch);
    }
    return !out.empty();
}

static bool vidParseMp4Chapters(int fd, int64_t fsize, std::vector<ChapItem>& out) {
    int64_t moovOff, moovLen;
    if (!mp4FindBox(fd, 0, fsize, "moov", moovOff, moovLen)) return false;
    int64_t moovEnd = moovOff + moovLen;
    if (mp4ChapterTrack(fd, moovOff, moovEnd, out)) return true;   // QuickTime chapter track
    out.clear();
    return mp4Chpl(fd, moovOff, moovEnd, out);                     // Nero chpl fallback
}

// --- Matroska/WebM EBML helpers --------------------------------------------
// Read an EBML variable-length integer at *pos (within [pos,end)). For IDs keep
// the length-marker bits; for sizes strip them. Advances *pos. Returns -1 on error.
static int64_t ebmlVint(int fd, int64_t& pos, int64_t end, bool keepMarker, bool* unknownSize = nullptr) {
    if (unknownSize) *unknownSize = false;
    if (pos >= end) return -1;
    uint8_t first; if (!vidPreadAll(fd, pos, &first, 1)) return -1;
    int len = 0; uint8_t mask = 0x80;
    for (len = 1; len <= 8; len++) { if (first & mask) break; mask >>= 1; }
    if (len > 8 || pos + len > end) return -1;
    uint8_t b[8]; if (!vidPreadAll(fd, pos, b, len)) return -1;
    pos += len;
    uint64_t v = keepMarker ? b[0] : (uint64_t)(b[0] & (mask - 1));
    bool allOnes = (uint64_t)(b[0] & (mask - 1)) == (uint64_t)(mask - 1);
    for (int i = 1; i < len; i++) { v = (v << 8) | b[i]; if (b[i] != 0xFF) allOnes = false; }
    if (unknownSize && !keepMarker && allOnes) *unknownSize = true;
    return (int64_t)v;
}

// Recursively walk Chapters > EditionEntry > ChapterAtom, emitting one chapter
// per ChapterAtom (ChapterTimeStart in ns + ChapterDisplay/ChapterString UTF-8).
static void mkvParseChapterTree(int fd, int64_t start, int64_t end, std::vector<ChapItem>& out, int depth) {
    if (depth > 8) return;
    int64_t pos = start;
    while (pos < end) {
        int64_t id = ebmlVint(fd, pos, end, true);
        if (id < 0) break;
        bool unk = false;
        int64_t size = ebmlVint(fd, pos, end, false, &unk);
        if (size < 0) break;
        int64_t dStart = pos, dEnd = unk ? end : pos + size;
        if (dEnd > end || dEnd < dStart) dEnd = end;
        if (id == 0x45B9) {                       // EditionEntry
            mkvParseChapterTree(fd, dStart, dEnd, out, depth + 1);
        } else if (id == 0xB6) {                  // ChapterAtom
            double t = -1.0; std::string title;
            int64_t p = dStart;
            while (p < dEnd) {
                int64_t cid = ebmlVint(fd, p, dEnd, true);
                if (cid < 0) break;
                int64_t csz = ebmlVint(fd, p, dEnd, false);
                if (csz < 0) break;
                int64_t cs = p, ce = p + csz; if (ce > dEnd || ce < cs) ce = dEnd;
                if (cid == 0x91) {                // ChapterTimeStart (ns)
                    int n = (int)(ce - cs); if (n > 8) n = 8;
                    uint8_t bb[8]; uint64_t v = 0;
                    if (n > 0 && vidPreadAll(fd, cs, bb, n)) { for (int i = 0; i < n; i++) v = (v << 8) | bb[i]; t = (double)v / 1e9; }
                } else if (cid == 0x80) {         // ChapterDisplay
                    int64_t q = cs;
                    while (q < ce) {
                        int64_t did = ebmlVint(fd, q, ce, true);
                        if (did < 0) break;
                        int64_t dsz = ebmlVint(fd, q, ce, false);
                        if (dsz < 0) break;
                        int64_t ds = q, de = q + dsz; if (de > ce || de < ds) de = ce;
                        if (did == 0x85 && dsz > 0 && dsz < 4096 && title.empty()) {   // ChapterString UTF-8
                            std::vector<uint8_t> tb(dsz);
                            if (vidPreadAll(fd, ds, tb.data(), dsz)) title.assign((char*)tb.data(), dsz);
                        }
                        q = de;
                    }
                }
                p = ce;
            }
            if (t >= 0.0) { ChapItem ch; ch.t = t; ch.title = title; out.push_back(ch); }
            mkvParseChapterTree(fd, dStart, dEnd, out, depth + 1);   // nested sub-chapters
        }
        pos = dEnd;
    }
}

static bool vidParseMkvChapters(int fd, int64_t fsize, std::vector<ChapItem>& out) {
    // Top level: find Segment (0x18538067).
    int64_t pos = 0, segStart = -1, segEnd = fsize;
    while (pos + 4 < fsize) {
        int64_t id = ebmlVint(fd, pos, fsize, true);
        if (id < 0) break;
        bool unk = false;
        int64_t size = ebmlVint(fd, pos, fsize, false, &unk);
        if (size < 0) break;
        int64_t dStart = pos, dEnd = unk ? fsize : pos + size;
        if (dEnd > fsize || dEnd < dStart) dEnd = fsize;
        if (id == 0x18538067) { segStart = dStart; segEnd = dEnd; break; }
        pos = dEnd;
    }
    if (segStart < 0) return false;
    // Within Segment, find Chapters (0x1043A770). Skip Clusters/other top-level
    // elements by their declared size (ffmpeg/mkvmerge write Chapters in the header).
    pos = segStart;
    int64_t chOff = -1, chEnd = -1;
    for (int guard = 0; guard < 100000 && pos + 4 < segEnd; guard++) {
        int64_t id = ebmlVint(fd, pos, segEnd, true);
        if (id < 0) break;
        bool unk = false;
        int64_t size = ebmlVint(fd, pos, segEnd, false, &unk);
        if (size < 0) break;
        int64_t dStart = pos, dEnd = unk ? segEnd : pos + size;
        if (dEnd > segEnd || dEnd < dStart) { if (unk) break; dEnd = segEnd; }
        if (id == 0x1043A770) { chOff = dStart; chEnd = dEnd; break; }
        pos = dEnd;
    }
    if (chOff < 0) return false;
    mkvParseChapterTree(fd, chOff, chEnd, out, 0);
    return !out.empty();
}

}  // namespace

void NanoMenu::vidParseChapters(const std::string& file) {
    mVidChapters.clear();
    int fd = ::open(file.c_str(), O_RDONLY);
    if (fd < 0) return;
    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size <= 16) { ::close(fd); return; }
    int64_t fsize = st.st_size;
    std::vector<ChapItem> tmp;
    uint8_t magic[8];
    if (vidPreadAll(fd, 0, magic, 8)) {
        if (magic[0] == 0x1A && magic[1] == 0x45 && magic[2] == 0xDF && magic[3] == 0xA3)
            vidParseMkvChapters(fd, fsize, tmp);     // Matroska / WebM
        else
            vidParseMp4Chapters(fd, fsize, tmp);     // MP4 / MOV / M4V
    }
    ::close(fd);
    std::sort(tmp.begin(), tmp.end(), [](const ChapItem& a, const ChapItem& b) { return a.t < b.t; });
    if (tmp.size() > 200) tmp.resize(200);
    for (const ChapItem& c : tmp) { VidChapter v; v.t = c.t; v.title = c.title; mVidChapters.push_back(v); }
    VLOGI("NanoMenu: vidParseChapters %s -> %zu chapters", file.c_str(), mVidChapters.size());
}

// Enumerate the file's audio tracks + embedded text-subtitle tracks, then probe for
// external .srt/.vtt sidecars next to it. Cheap (text + extractor walk); nothing resident.
void NanoMenu::vidBuildTracks(const std::string& file) {
    mVidAudTracks.clear();
    mVidSubTracks.clear();
    if (mVidTsVideoFmt) { AMediaFormat_delete(mVidTsVideoFmt); mVidTsVideoFmt = nullptr; }
    std::vector<int> embSubIdx;
    std::vector<std::string> embSubName;
    int fd = ::open(file.c_str(), O_RDONLY);
    if (fd >= 0) {
        struct stat st;
        if (fstat(fd, &st) == 0 && st.st_size > 0) {
            AMediaExtractor* ex = AMediaExtractor_new();
            // Scrambled-flagged .ts (CA descriptor in the PMT): feed the descramble data
            // source. Strip audio (the demuxer enumerates + decodes the .ts audio; the
            // system extractor's AC-3 parser can crash on these captures). Freed after.
            AMediaDataSource* tsDs = nullptr; void* tsUd = nullptr; int tsPmt = -1;
            media_status_t dst;
            if (tsNeedsDescramble(fd, tsPmt)) {
                tsDs = tsMakeDataSource(fd, (off64_t)st.st_size, tsPmt, &tsUd, /*stripAudio=*/true);
                dst = tsDs ? AMediaExtractor_setDataSourceCustom(ex, tsDs) : AMEDIA_ERROR_UNKNOWN;
            } else {
                dst = AMediaExtractor_setDataSourceFd(ex, fd, 0, st.st_size);
            }
            if (dst == AMEDIA_OK) {
                size_t nt = AMediaExtractor_getTrackCount(ex);
                for (size_t i = 0; i < nt; i++) {
                    AMediaFormat* f = AMediaExtractor_getTrackFormat(ex, i);
                    const char* mime = nullptr;
                    if (f && AMediaFormat_getString(f, AMEDIAFORMAT_KEY_MIME, &mime) && mime) {
                        const char* lang = nullptr;
                        AMediaFormat_getString(f, AMEDIAFORMAT_KEY_LANGUAGE, &lang);
                        VLOGI("NanoMenu: vid track %zu mime=%s lang=%s", i, mime, lang ? lang : "?");
                        bool haveLang = lang && *lang && strcmp(lang, "und");
                        if (!strncmp(mime, "audio/", 6)) {
                            VidAudTrk t; t.idx = (int)i;
                            std::string nm = haveLang ? lang : "";
                            std::string codec = vidAudCodecBadge(mime);
                            if (nm.empty()) { char b[24]; snprintf(b, sizeof(b), "Audio %zu  %s", mVidAudTracks.size() + 1, codec.c_str()); nm = b; }
                            else nm += std::string("  ") + codec;
                            t.name = nm;
                            mVidAudTracks.push_back(t);
                        } else if (vidIsTextSubMime(mime)) {
                            std::string nm = haveLang ? lang : "";
                            if (nm.empty()) { char b[24]; snprintf(b, sizeof(b), "Track %zu", embSubIdx.size() + 1); nm = b; }
                            embSubIdx.push_back((int)i); embSubName.push_back(nm);
                        } else if (!strncmp(mime, "video/", 6) && !mVidTsVideoFmt) {
                            mVidTsVideoFmt = f; f = nullptr;   // keep the real video format for openFed
                        }
                    }
                    if (f) AMediaFormat_delete(f);
                }
            }
            AMediaExtractor_delete(ex);
            tsFreeDataSource(tsDs, tsUd);   // after the extractor that used it
        }
        ::close(fd);
    }
    // embedded text-sub cues (separate extractor pass per track)
    for (size_t s = 0; s < embSubIdx.size(); s++) {
        VidSubTrk t; t.external = false; t.embIdx = embSubIdx[s]; t.name = embSubName[s];
        vidReadEmbeddedCues(file, embSubIdx[s], t.cues);
        if (!t.cues.empty()) mVidSubTracks.push_back(std::move(t));
    }
    // external sidecars: <basename without ext> + .srt / .vtt
    std::string base = file; size_t dot = base.find_last_of('.');
    if (dot != std::string::npos) base = base.substr(0, dot);
    static const char* kExt[] = {".srt", ".vtt"};
    for (const char* e : kExt) {
        std::string sp = base + e;
        struct stat st;
        if (stat(sp.c_str(), &st) == 0 && st.st_size > 0 && st.st_size < 4 * 1024 * 1024) {
            int sfd = ::open(sp.c_str(), O_RDONLY);
            if (sfd < 0) continue;
            std::string content; content.resize(st.st_size);
            ssize_t rd = read(sfd, &content[0], st.st_size);
            ::close(sfd);
            if (rd <= 0) continue;
            content.resize(rd);
            VidSubTrk t; t.external = true; t.file = sp;
            t.name = std::string("Sidecar (") + (e + 1) + ")";
            t.cues = vidParseSrt(content);
            if (!t.cues.empty()) mVidSubTracks.push_back(std::move(t));
        }
    }
    // DVB bitmap subtitles (MPEG-TS). Cheap probe only (PAT/PMT scan); the full
    // timeline is decoded lazily when the user selects the track (vidDvbSelect).
    {
        int dvbPid = -1;
        if (NanoDvbSub::probe(file, dvbPid)) {
            VidSubTrk t; t.dvb = true; t.dvbPid = dvbPid; t.file = file;
            t.name = "DVB Subtitle";
            mVidSubTracks.push_back(std::move(t));
        }
    }
    VLOGI("NanoMenu: vidBuildTracks %s -> %zu audio, %zu subtitle tracks",
          file.c_str(), mVidAudTracks.size(), mVidSubTracks.size());
}

// Start decoding a DVB subtitle track off the render thread (a large .ts streams
// for seconds; a synchronous decode would trip the render watchdog). Lazy: the
// decoder + its timeline live only between selection and deselect/close.
void NanoMenu::vidDvbSelect(const std::string& file, int pid) {
    vidDvbFree();
    mVidDvbPath = file; mVidDvbPid = pid;
    mVidDvbReady.store(false); mVidDvbLoading.store(true); mVidDvbAbort.store(false);
    mVidDvb = new NanoDvbSub();
    NanoDvbSub* dvb = mVidDvb;
    std::string path = file; int p = pid;
    mVidDvbThread = std::thread([this, dvb, path, p]() {
        dvb->open(path, p, &mVidDvbAbort);
        mVidDvbReady.store(true);
        mVidDvbLoading.store(false);
    });
}

// Join the worker and free the decoder + the uploaded region texture. Idempotent.
void NanoMenu::vidDvbFree() {
    mVidDvbAbort.store(true);                   // unblock a long streaming decode
    if (mVidDvbThread.joinable()) mVidDvbThread.join();
    if (mVidDvb) { mVidDvb->close(); delete mVidDvb; mVidDvb = nullptr; }
    if (mVidDvbTex) { glDeleteTextures(1, &mVidDvbTex); mVidDvbTex = 0; }
    mVidDvbTexRegion = -1;
    mVidDvbReady.store(false); mVidDvbLoading.store(false);
    mVidDvbPath.clear(); mVidDvbPid = -1;
}

const std::vector<NanoMenu::VidCue>* NanoMenu::vidActiveSubCues() const {
    if (mVidSubCur < 0 || mVidSubCur >= (int)mVidSubTracks.size()) return nullptr;
    const VidSubTrk& t = mVidSubTracks[mVidSubCur];
    if (t.cea608) {
        // Live line-21 captions: pull the demuxer's current cue snapshot (cheap, bounded list).
        std::vector<NanoTsDemux::Cue> tmp;
        const_cast<NanoMenu*>(this)->mVidTsDemux.copyCea608Cues(tmp);
        mVidCcCues.clear();
        for (const auto& c : tmp) mVidCcCues.push_back({ c.startSec, c.endSec - c.startSec, c.text });
        return &mVidCcCues;
    }
    return &t.cues;
}

// Open audio for a NON-.ts title via mVidAudio's own extractor (called by vidOpenTitle for
// the normal path). The .ts path opens audio through the demuxer in vidOpenTitle instead.
void NanoMenu::vidOpenTitleAudio(const std::string& file) {
    mVidHasAudio = false;
    if (!mVidAudTracks.empty()) {
        mVidHasAudio = mVidAudio.open(file, mVidAudTracks[0].idx);
        if (mVidHasAudio) mVidAudio.setVolume(mVidVolume); else mVidAudio.release();
    }
}

// Open a title's picture + audio. mVideoTest must already be allocated. For .ts the single
// in-process demuxer feeds BOTH the HW video codec (fed mode) and the audio (liba52) from
// one read pointer, so A/V stay locked with no system extractor; everything else uses the
// normal AMediaExtractor path. Returns false only if the picture could not be opened.
bool NanoMenu::vidOpenTitle(const std::string& file, int w, int h) {
    mVidTsMode = false; mVidTsAudio = false; mVidHasAudio = false;
    mVidTsDemux.close();

    // Debug A/B toggle: persist.gammaos.nano.vid.nofed=1 forces the extractor-driven open() path
    // for .ts (instead of the single-demuxer fed codec) to compare HW MPEG-2 cold-start reliability.
    bool noFed = property_get_bool("persist.gammaos.nano.vid.nofed", 0);
    bool isTs = !noFed && vidFileIsTs(file) && mVidTsDemux.open(file) && mVidTsDemux.videoPid() >= 0;
    // The in-process demuxer exists only for what the system extractor cannot do: AC-3 audio
    // (no device decoder, decoded via liba52), multi-audio, CEA-608 captions and scrambled
    // MPEG-2 - i.e. the MPEG-2 + AC-3 broadcast profile. It feeds ONLY MPEG-2 video and
    // enumerates ONLY AC-3 audio, so a clean H.264/HEVC .ts (or an MPEG-2 .ts without AC-3)
    // would play black with no sound through it. Those decode natively on the system
    // extractor (including HW AAC), so hand them to the normal open() path instead.
    if (isTs && (strcmp(mVidTsDemux.videoMime(), "video/mpeg2") != 0 || mVidTsDemux.audioTracks().empty())) {
        mVidTsDemux.close();
        isTs = false;
    }
    if (!isTs) {
        if (!mVideoTest->open(file)) return false;
    }

    // Subtitles (sidecars, embedded text, DVB). For .ts vidBuildTracks strips audio so the
    // system extractor (used only here, briefly, for sub enumeration) never crashes; it is
    // NOT used during playback. Audio it enumerates is replaced by the demuxer below.
    // CRUCIAL for .ts: this enumeration opens the SLOW system MPEG2TSExtractor (seconds), so it
    // runs BEFORE the fed HW codec is created/started. Leaving the Allwinner MPEG-2 decoder
    // started-but-unfed for those seconds intermittently wedges it at cold start (it then never
    // produces output and never errors). Create + feed the codec back-to-back instead.
    vidBuildTracks(file);
    if (!isTs) vidParseChapters(file);   // TS broadcast has no chapter track
    mVidAudCur = 0; mVidSubCur = -1;

    if (isTs) {
        int vw = w > 0 ? w : 720, vh = h > 0 ? h : 480;     // size hint; FORMAT_CHANGED corrects it
        bool fedOk = mVideoTest->openFed(mVidTsDemux.videoMime(), vw, vh, mVidTsVideoFmt);
        if (mVidTsVideoFmt) { AMediaFormat_delete(mVidTsVideoFmt); mVidTsVideoFmt = nullptr; }  // consumed by configure
        if (!fedOk) {
            mVidTsDemux.close();
            if (!mVideoTest->open(file)) return false;       // fall back to the normal path
        } else {
            mVidTsMode = true;
            mVidAudTracks.clear();
            const auto& ats = mVidTsDemux.audioTracks();
            for (size_t k = 0; k < ats.size(); k++) {
                VidAudTrk t; t.idx = (int)k;                  // idx = ordinal into the demux audio list
                t.name = vidLangName(ats[k].lang, k) + "  AC-3";
                mVidAudTracks.push_back(t);
            }
            if (!ats.empty() && mVidAudio.openFed(48000, 2)) { // AC-3 -> stereo 48k fed ring
                mVidAudio.setVolume(mVidVolume);
                mVidTsAudio = true; mVidHasAudio = true;
            }
            // One worker feeds the picture (mVideoTest) + the selected audio from one read pointer,
            // starting immediately so the codec is never idle after start.
            mVidTsDemux.start(mVideoTest, mVidHasAudio ? &mVidAudio : nullptr, 0, 0.0);
        }
    }
    if (!mVidTsMode) {
        vidOpenTitleAudio(file);
        // Audio-master pacing for the separate-extractor path too: the picture slews to the
        // audio playback clock so A/V stay locked (and re-sync after a seek, which re-anchors
        // the audio clock). Gated on isPlaying so scan/seek/pause fall back to wall-clock pacing.
        if (mVidHasAudio && mVideoTest) {
            NanoAudioPlayer* a = &mVidAudio;
            mVideoTest->setClockFn([a]{ return a->isPlaying() ? a->position() : -1.0; });
        }
    }
    return true;
}

// Tear down the title's audio + picture demuxer. Stops the demux worker (which feeds
// mVideoTest) FIRST so the picture can then be freed safely, then releases mVidAudio.
void NanoMenu::vidCloseTitleAudio() {
    // Drop the picture's audio-clock fn BEFORE the demuxer/audio are torn down so the video
    // worker stops reading a clock whose backing audio is going away (.ts: the demuxer also
    // clears it in stop(); this covers the separate-extractor path).
    if (mVideoTest) mVideoTest->setClockFn(nullptr);
    if (mVidTsMode || mVidTsAudio) { mVidTsDemux.close(); mVidTsMode = false; mVidTsAudio = false; }
    mVidAudio.release();
    mVidHasAudio = false;
}

// Seek, routed to the demuxer for .ts (repositions the single read pointer + flushes both
// the video codec and the audio ring) or to mVidAudio's own extractor otherwise.
void NanoMenu::vidAudioSeek(double sec) {
    if (mVidTsMode) mVidTsDemux.seek(sec);
    else if (mVidHasAudio) mVidAudio.seek(sec);
}

// Duration (s): the demuxer's PCR estimate for .ts (NanoVideo fed mode has none), else the
// extractor's value.
double NanoMenu::vidDuration() const {
    if (mVidTsMode) return mVidTsDemux.duration();
    return mVideoTest ? mVideoTest->duration() : 0.0;
}

// Switch the active audio track. For .ts this is an O(1) PID re-route on the demuxer's
// shared PCR clock; otherwise re-open mVidAudio on that extractor track (web vidSetAudioTrack).
void NanoMenu::vidSetAudioTrack(int ordinal) {
    if (ordinal < 0 || ordinal >= (int)mVidAudTracks.size()) return;
    mVidAudCur = ordinal;
    if (mVidTsAudio) {
        // O(1) PID re-route on the shared read pointer: the ~0.8s of already-buffered old-track
        // audio drains, then the new track flows, in sync (no seek -> no video disturbance, and
        // no byte-estimate jump on discontinuity captures). Brief changeover, like the web aux.
        mVidTsDemux.selectAudio(ordinal);
        mVidAudio.setVolume(mVidVolume);
    } else {
        if (mVidList.empty() || mVidIdx < 0 || mVidIdx >= (int)mVidList.size()) return;
        int vi = mVidList[mVidIdx];
        if (vi < 0 || vi >= (int)mVideos.size()) return;
        double pos = mVideoTest ? mVideoTest->position() : 0.0;
        mVidAudio.release();
        mVidHasAudio = mVidAudio.open(mVideos[vi].file, mVidAudTracks[ordinal].idx);
        mVidAudioStarted = false;
        if (mVidHasAudio) { mVidAudio.setVolume(mVidVolume); if (pos > 0.0) mVidAudio.seek(pos); }
    }
    mVidDispMode = std::string("Audio: ") + mVidAudTracks[ordinal].name;
    mVidDispModeUntil = mEffectTime + 1.8f;
}

// ===========================================================================
// Video player screen (R4 V2) - full-screen 1:1 playback (web drawVideoPlayer).
// HW decode via NanoVideo (mVideoTest). Lazy: decoder created on open, freed on
// close. The control panel / scene-search / options come next.
// ===========================================================================
static std::string vFmtTime(double s) {
    if (s < 0) s = 0;
    int t = (int)s;                         // floor (web Math.floor), not round, so 12.7s reads 0:12
    int h = t / 3600, m = (t % 3600) / 60, sec = t % 60;
    char b[24];
    if (h > 0) snprintf(b, sizeof(b), "%d:%02d:%02d", h, m, sec);
    else       snprintf(b, sizeof(b), "%d:%02d", m, sec);
    return b;
}

void NanoMenu::openVideoPlayer(const std::vector<Ps3Item>& list, int listSel, int resumeChoice) {
    videoEnsureLoaded();
    // Queue = every video file in the current list, starting on the selected one.
    mVidList.clear();
    int start = 0;
    for (size_t i = 0; i < list.size(); i++) {
        if (list[i].kind != PS3_VIDEO_FILE) continue;
        if ((int)i == listSel) start = (int)mVidList.size();
        mVidList.push_back(list[i].a);
    }
    if (mVidList.empty()) return;
    if (start < 0 || start >= (int)mVidList.size()) start = 0;
    mVidIdx = start;

    int vi = mVidList[mVidIdx];
    if (vi < 0 || vi >= (int)mVideos.size()) return;
    vidCloseTitleAudio();   // stop any prior demux/audio (it feeds mVideoTest) before freeing it
    if (mVideoTest) { vidAsyncFree(mVideoTest); mVideoTest = nullptr; }   // never block the render thread on teardown
    mVideoTest = new NanoVideo();
    // The open + track-enumeration + audio-open below can make synchronous media-service
    // binder calls that block for seconds when those services are cold/contended; exempt
    // the render watchdog for the duration so a slow open never aborts nano.
    mVidOpening.store(true, std::memory_order_relaxed);
    // Stop background music so the video owns the audio path (web 12350).
    if (mMusicPlayer.isPlaying()) mMusicPlayer.pause();
    // Open the title (.ts -> single in-process demuxer feeds picture + audio in lockstep;
    // else the normal AMediaExtractor path). Audio starts in videoTick on the first frame.
    if (!vidOpenTitle(mVideos[vi].file, mVideos[vi].w, mVideos[vi].h)) {
        delete mVideoTest; mVideoTest = nullptr;
        mVidOpening.store(false, std::memory_order_relaxed); return;
    }
    mVidAudioStarted = false;
    mVidOpening.store(false, std::memory_order_relaxed);

    mVidActive = true;
    mVidPlaying = true;
    mVidScreenMode = 0;
    mVidOsd = false;
    mVidHintUntil = mEffectTime + 4.0f;     // show the OSD bar for 4s on open
    mVidTransientUntil = 0.0f; mVidDispModeUntil = 0.0f;
    // fresh transport + panel state
    mVidRate = 1.0; mVidStopped = false; mVidRepeat = 0; mVidAbA = mVidAbB = -1.0;
    mVidScanLastTick = -1.0;
    mVidLastPos = -1.0; mVidLastPosT = mEffectTime; mVidBuffering = false;
    mVidCpOpen = mVidCpClosing = mVidSubOpen = false; mVidGoToOpen = false;
    mVidSceneOpen = mVidSceneClosing = false;
    // Resume: offer Resume / Play-from-beginning when this title has a saved position
    // (>5s in and not within 5s of the end). Hold playback until the user chooses.
    mVidResumeAsk = false; mVidResumeSel = 0; mVidResumeAskSec = 0.0;
    mVidResumeDirty = false; mVidResumeSaveT = mEffectTime;
    mVidDlgActive = false; mVidDlgBusyUntil = 0.0f;   // clear any stale confirm/info/busy modal
    {
        double rs = mVideos[vi].resumeSec;
        bool resumable = (rs > 0.0);   // web shows Resume whenever a point was saved (vidCaptureResume gates it)
        if (resumeChoice == 1 && resumable) {        // option-menu "Resume": seek now, no prompt
            if (mVideoTest) mVideoTest->seek(rs);
            if (mVidHasAudio) vidAudioSeek(rs);
            mVidHintUntil = mEffectTime + 1.5f;
        } else if (resumeChoice == 0) {              // option-menu "Play from Beginning": start at 0 (bookmark already cleared)
        } else if (resumeChoice < 0 && resumable) {  // direct Enter: prompt Resume / Play from beginning
            mVidResumeAsk = true; mVidResumeAskSec = rs; mVidPlaying = false;   // wait for the choice
        }
    }
}

// Store the playing title's current position for Resume (kept only when >5s in and
// not within 5s of the end; otherwise cleared so a finished/near-start video resets).
void NanoMenu::vidCaptureResume() {
    if (!mVideoTest || mVidList.empty() || mVidIdx < 0 || mVidIdx >= (int)mVidList.size()) return;
    int vi = mVidList[mVidIdx];
    if (vi < 0 || vi >= (int)mVideos.size()) return;
    double p = mVideoTest->position(), dur = vidDuration();
    // web: keep a resume point when 3s < pos < dur-5 AND pos < 97% (drop a near-finished title).
    double rs = (dur > 0.0 && p > 3.0 && p < dur - 5.0 && p < dur * 0.97) ? p : 0.0;
    if (mVideos[vi].resumeSec != rs) { mVideos[vi].resumeSec = rs; mVidResumeDirty = true; }
}

// Apply the highlighted Resume-prompt choice (0 = Resume, 1 = Play from beginning).
void NanoMenu::vidResumeConfirm() {
    if (!mVidResumeAsk) return;
    mVidResumeAsk = false;
    if (mVidResumeSel == 0) {                       // Resume
        if (mVideoTest) mVideoTest->seek(mVidResumeAskSec);
        if (mVidHasAudio) vidAudioSeek(mVidResumeAskSec);
        mVidHintUntil = mEffectTime + 1.5f;
    } else {                                        // Play from beginning
        if (mVidIdx >= 0 && mVidIdx < (int)mVidList.size()) {
            int vi = mVidList[mVidIdx];
            if (vi >= 0 && vi < (int)mVideos.size() && mVideos[vi].resumeSec != 0.0) {
                mVideos[vi].resumeSec = 0.0; mVidResumeDirty = true;
            }
        }
    }
    mVidPlaying = true;
}

void NanoMenu::closeVideoPlayer() {
    // Capture the Resume position before the leave fade (the decoder is still alive here;
    // it is freed later in videoTick once the fade completes).
    vidCaptureResume();
    if (mVidResumeDirty) { saveVideoConfig(); mVidResumeDirty = false; }
    // Begin the leave fade; the decoder is freed once mVidEnterT reaches 0 (videoTick),
    // so the last frame fades out instead of cutting to black.
    mVidActive = false;
}

// Immediate, idempotent full teardown of the video decoder + player state. Used where
// there is no time for the leave fade: sleep/power-press, occlusion by a foreground app,
// and process shutdown. Joins the worker, frees codec/extractor/surface/OES texture.
// Hand a decoder to async teardown and queue it for reaping. The render thread NEVER
// blocks: the worker join + OMX stop run on the decoder's own background thread.
void NanoMenu::vidAsyncFree(NanoVideo* v) {
    if (!v) return;
    v->releaseAsync();
    mVidDying.push_back(v);
}

// Free any queued decoder whose background teardown has completed. Runs on the render
// thread (the GL teardown in finishRelease needs the EGL context). Never blocks: it
// only finishes decoders that already signalled done.
void NanoMenu::vidReapDying() {
    for (size_t i = 0; i < mVidDying.size();) {
        if (mVidDying[i]->releaseAsyncDone()) {
            mVidDying[i]->finishRelease();
            delete mVidDying[i];
            mVidDying.erase(mVidDying.begin() + i);
        } else {
            ++i;
        }
    }
}

void NanoMenu::videoHardFree(bool sync) {
    vidCaptureResume();   // persist the Resume position before tearing the decoder down
    if (mVidResumeDirty) { saveVideoConfig(); mVidResumeDirty = false; }
    vidCloseTitleAudio();   // stop the demuxer FIRST (it feeds mVideoTest) before freeing it
    if (mVideoTest) {
        if (sync) { mVideoTest->release(); delete mVideoTest; mVideoTest = nullptr; }
        else { vidAsyncFree(mVideoTest); mVideoTest = nullptr; }   // OMX stop off the render thread (watchdog)
    }
    // Synchronous path (process shutdown): drain every queued async teardown too. Each
    // finishRelease joins its background thread (blocking is acceptable at dtor) then
    // frees the GL state. Reap the already-done ones first, then force the rest.
    if (sync) {
        for (NanoVideo* v : mVidDying) { v->finishRelease(); delete v; }
        mVidDying.clear();
    }
    mVidActive = false; mVidPlaying = false;
    mVidResumeAsk = false;
    mVidEnterRaw = 0.0f; mVidEnterT = 0.0f;
    mVidCpOpen = mVidCpClosing = mVidSubOpen = mVidGoToOpen = false;
    mVidSceneOpen = mVidSceneClosing = false;
    mVidAudTracks.clear(); mVidSubTracks.clear(); mVidChapters.clear(); mVidAudCur = 0; mVidSubCur = -1;
    if (mVidTsVideoFmt) { AMediaFormat_delete(mVidTsVideoFmt); mVidTsVideoFmt = nullptr; }
    vidDvbFree();
}

void NanoMenu::vidShowTransient(const std::string& text, float ms) {
    mVidTransient = text; mVidTransientUntil = mEffectTime + ms / 1000.0f;
}

void NanoMenu::vidTogglePlay() {
    if (!mVideoTest) return;
    // web vidPlayToggle: forces rate 1, un-stops (restart from 0 if stopped), flips playing.
    mVidRate = 1.0; mVidTransientUntil = 0.0f;
    if (mVidStopped) { mVidStopped = false; mVideoTest->seek(0.0); if (mVidHasAudio) vidAudioSeek(0.0); }
    mVidPlaying = !mVidPlaying;
    if (mVidPlaying) mVideoTest->play(); else mVideoTest->pause();
    // audio play/pause is reconciled in videoTick (single source of truth)
    mVidHintUntil = mEffectTime + 1.5f;
}

void NanoMenu::vidSeek(double deltaSec) {
    if (!mVideoTest) return;
    mVidRate = 1.0;   // a manual seek cancels any scan (web vidSeek)
    double dur = vidDuration();
    double p = mVideoTest->position() + deltaSec;
    if (p < 0.0) p = 0.0;
    if (dur > 0.0 && p > dur - 0.05) p = dur - 0.05;   // keep inside the stream (no EOS trip)
    mVideoTest->seek(p);
    if (mVidHasAudio) vidAudioSeek(p);
    mVidHintUntil = mEffectTime + 1.5f;
}

void NanoMenu::vidStepTitle(int dir) {
    if (mVidList.empty() || !mVideoTest) return;
    vidCaptureResume();   // persist the OUTGOING title's position before we leave it
    int n = (int)mVidList.size();
    mVidIdx = ((mVidIdx + dir) % n + n) % n;
    int vi = mVidList[mVidIdx];
    if (vi < 0 || vi >= (int)mVideos.size()) return;
    // Stop the outgoing title's demuxer FIRST (it feeds mVideoTest), THEN async-release the
    // old decoder (its OMX stop must not block the render thread; reaped by vidReapDying) and
    // open the new one. The open/track binder calls can block; exempt the watchdog.
    vidCloseTitleAudio();
    vidAsyncFree(mVideoTest);
    mVideoTest = new NanoVideo();
    mVidOpening.store(true, std::memory_order_relaxed);
    mVidSceneOpen = false; mVidSceneClosing = false;
    if (!vidOpenTitle(mVideos[vi].file, mVideos[vi].w, mVideos[vi].h)) {
        delete mVideoTest; mVideoTest = nullptr;
        mVidOpening.store(false, std::memory_order_relaxed); return;
    }
    mVidAudioStarted = false;
    mVidOpening.store(false, std::memory_order_relaxed);
    // web vidStepTitle resets rate / stopped / play state.
    mVidRate = 1.0; mVidStopped = false; mVidPlaying = true;
    mVidAbA = mVidAbB = -1.0;
    mVidHintUntil = mEffectTime + 1.5f;
    // Auto-advance never prompts: silently resume the new title from its saved position.
    {
        double rs = mVideos[vi].resumeSec, dur = vidDuration();
        if (rs > 5.0 && (dur <= 0.0 || rs < dur - 5.0)) {
            mVideoTest->seek(rs);   // no-op in fed mode; the demux seek below handles .ts
            vidAudioSeek(rs);
        }
    }
}

// ---- transport extras (web vidStop/vidScan/vidSlow/vidStepFrame/vidFlash) -----
void NanoMenu::vidStop() {
    if (!mVideoTest) return;
    mVideoTest->pause();
    mVideoTest->seek(0.0);
    if (mVidHasAudio) { mVidAudio.pause(); vidAudioSeek(0.0); }
    mVidPlaying = false; mVidStopped = true; mVidRate = 1.0;
    mVidTransientUntil = 0.0f;
    mVidHintUntil = mEffectTime + 1.5f;
    // Explicit stop+rewind clears the Resume bookmark for this title.
    if (mVidIdx >= 0 && mVidIdx < (int)mVidList.size()) {
        int vi = mVidList[mVidIdx];
        if (vi >= 0 && vi < (int)mVideos.size() && mVideos[vi].resumeSec != 0.0) {
            mVideos[vi].resumeSec = 0.0; mVidResumeDirty = true;
        }
    }
}

void NanoMenu::vidScan(int dir) {
    if (!mVideoTest) return;
    static const double kMag[4] = {1.5, 10.0, 30.0, 120.0};
    double cur = mVidRate;
    bool sameDir = (dir > 0 && cur > 1.0) || (dir < 0 && cur < -1.0);
    double mag = 1.5;
    if (sameDir) {
        double a = cur < 0 ? -cur : cur; int idx = 0;
        for (int i = 0; i < 4; i++) if (a >= kMag[i] - 0.01) idx = i;
        mag = kMag[(idx + 1) % 4];            // step up, wrapping back to 1.5
    }
    mVidRate = dir > 0 ? mag : -mag;
    mVidStopped = false; mVidPlaying = true;
    mVidScanLastTick = -1.0; mVidScanPos = mVideoTest->position();
    mVideoTest->pause();                       // timer-driven from here (videoTick)
    char b[48];
    snprintf(b, sizeof(b), "%s  x %g", dir > 0 ? "Fast Forward" : "Fast Reverse", mag);
    vidShowTransient(b, 1400.0f);
    mVidHintUntil = mEffectTime + 1.5f;
}

void NanoMenu::vidSlow(int dir) {
    if (!mVideoTest) return;
    mVidRate = dir > 0 ? 0.5 : -0.5;
    mVidStopped = false; mVidPlaying = true;
    mVidScanLastTick = -1.0; mVidScanPos = mVideoTest->position();
    mVideoTest->pause();
    vidShowTransient(dir > 0 ? "Slow (Forward)" : "Slow (Reverse)", 1400.0f);
    mVidHintUntil = mEffectTime + 1.5f;
}

void NanoMenu::vidStepFrame(int dir) {
    if (!mVideoTest) return;
    mVidPlaying = false; mVidRate = 1.0; mVideoTest->pause();
    double dur = vidDuration();
    double p = mVideoTest->position() + (dir > 0 ? 1.0 : -1.0) / 30.0;
    if (p < 0.0) p = 0.0;
    if (dur > 0.0 && p > dur - 0.02) p = dur - 0.02;
    mVideoTest->seek(p);
    if (mVidHasAudio) { mVidAudio.pause(); vidAudioSeek(p); }   // frame step keeps audio paused on the frame
    mVidHintUntil = mEffectTime + 1.5f;
}

void NanoMenu::vidFlash(int dir) {
    if (!mVideoTest) return;
    mVidRate = 1.0;
    double dur = vidDuration();
    double p = mVideoTest->position() + (dir > 0 ? 15.0 : -15.0);
    if (p < 0.0) p = 0.0;
    if (dur > 0.0 && p > dur - 0.05) p = dur - 0.05;
    mVideoTest->seek(p);
    if (mVidHasAudio) vidAudioSeek(p);
    vidShowTransient(dir > 0 ? "Instant Advance" : "Instant Replay", 1200.0f);
    mVidHintUntil = mEffectTime + 1.5f;
}

void NanoMenu::vidBeginning() {
    if (!mVideoTest) return;
    if (mVideoTest->position() > 2.0) {
        mVidRate = 1.0; mVideoTest->seek(0.0);
        vidShowTransient("Return to Beginning", 1000.0f);
        mVidHintUntil = mEffectTime + 1.5f;
    } else {
        vidStepTitle(-1);
    }
}

void NanoMenu::videoTick() {
    vidReapDying();   // free any async-released decoder whose background teardown finished
    // Change Icon busy dialog auto-advances to the result (web vidCreateIcon ~650ms timer).
    if (mVidDlgActive && mVidDlgKind == 2 && mEffectTime >= mVidDlgBusyUntil)
        vidDlgInfo("The icon has been changed.");
    float dt = mFrameDt; if (dt < 0.0f || dt > 0.2f) dt = 0.016f;
    // 400ms smoothstep enter/leave (web vidEnterT). Eases toward 1 while active, 0 when
    // closing; the decoder is freed once fully faded out.
    float target = mVidActive ? 1.0f : 0.0f;
    float step = (dt * 1000.0f) / 400.0f;
    if (mVidEnterRaw < target) mVidEnterRaw = fminf(target, mVidEnterRaw + step);
    else if (mVidEnterRaw > target) mVidEnterRaw = fmaxf(target, mVidEnterRaw - step);
    mVidEnterT = mVidEnterRaw * mVidEnterRaw * (3.0f - 2.0f * mVidEnterRaw);
    if (!mVidActive && mVidEnterRaw <= 0.001f && mVideoTest) {
        // Async teardown: the worker join + OMX stop run on a bg thread so the render
        // loop never blocks (vidReapDying frees it once done). Stop the demuxer FIRST (it
        // feeds mVideoTest) before handing the decoder to async teardown.
        vidCloseTitleAudio();
        vidAsyncFree(mVideoTest); mVideoTest = nullptr;
        mVidCpOpen = mVidCpClosing = mVidSubOpen = mVidGoToOpen = false;
        mVidSceneOpen = mVidSceneClosing = false;
        mVidAudTracks.clear(); mVidSubTracks.clear(); mVidChapters.clear(); mVidAudCur = 0; mVidSubCur = -1;
        vidDvbFree();
    }
    // While leaving (fading out) keep the audio quiet even before the decoder is freed.
    if (!mVidActive && mVidHasAudio && mVidAudio.isPlaying()) mVidAudio.pause();
    if (!mVidActive) mVidBuffering = false;   // no spinner during the leave fade
    if (!mVidActive || !mVideoTest) return;

    // Timer-driven scan/slow: NanoVideo only plays at 1x, so any non-1x rate pauses
    // native playback and advances the position by rate*dt each frame (web vidTick).
    if (mVidPlaying && mVidRate != 1.0) {
        double now = mEffectTime;
        double sdt = (mVidScanLastTick < 0.0) ? 0.016 : (now - mVidScanLastTick);
        if (sdt > 0.1) sdt = 0.1;
        mVidScanLastTick = now;
        double dur = vidDuration();
        // Advance a COMMANDED clock (decoder position lags + snaps to keyframes, so re-basing
        // off it would stall the scan), then seek the picture to it (web vidTick accumulator).
        mVidScanPos += mVidRate * sdt;
        if (mVidScanPos <= 0.0) {              // hit the start: resume normal play
            mVidRate = 1.0; mVideoTest->seek(0.0); mVideoTest->play();
            mVidTransientUntil = 0.0f;
        } else if (dur > 0.0 && mVidScanPos >= dur - 0.05) {
            // hit the end: resume at the tail so natural EOS triggers vidOnEnded below
            mVidRate = 1.0; mVideoTest->seek(dur - 0.05); mVideoTest->play();
        } else {
            mVideoTest->seek(mVidScanPos);
        }
    } else {
        mVidScanLastTick = -1.0;
        // Reconcile native playback with the play/pause state at 1x.
        bool wantNative = mVidPlaying && mVidRate == 1.0;
        if (wantNative && !mVideoTest->isPlaying()) mVideoTest->play();
        else if (!wantNative && mVideoTest->isPlaying()) mVideoTest->pause();
    }

    // Audio (the video's own track) follows the picture: held until the first frame lands,
    // then plays at normal speed, pauses during scan/slow/stop/pause, and resnaps when it
    // drifts > 0.3s (web vidSyncAux). The picture is the master clock.
    if (mVidHasAudio) {
        // The AUDIO is the master clock and the picture follows it (NanoVideo slews its frame
        // pacing to mVidAudio's position via the clock fn set at open, for BOTH the .ts demuxer
        // and the separate-extractor path). So here we only run/pause the audio with playback:
        // play it as soon as we want sound (the clock only arms on real PCM, so the AAudio HAL
        // cold-start hides in the decode warmup instead of baking a startup lip-sync skew), and
        // pause during scan/slow/stop/pause. No reseeking against the picture - the picture is
        // the follower, and a seek already re-anchors the audio clock which the picture tracks.
        bool wantAudio = mVidPlaying && mVidRate == 1.0 && !mVidStopped;
        if (wantAudio) {
            if (!mVidAudio.isPlaying()) mVidAudio.play();
            mVidAudioStarted = true;
        } else if (mVidAudio.isPlaying()) {
            mVidAudio.pause();
        }
    }

    // Buffering: the picture is playing at 1x but the decoded position has not advanced for
    // a moment (warmup / post-seek refill / stall) -> show the spinner (web Layer 2).
    if (mVidPlaying && mVidRate == 1.0 && !mVidStopped) {
        double p = mVideoTest->position();
        if (mVidLastPos < 0.0 || fabs(p - mVidLastPos) > 1e-4) {
            mVidLastPos = p; mVidLastPosT = mEffectTime; mVidBuffering = false;
        } else if (mEffectTime - mVidLastPosT > 0.4f) {
            mVidBuffering = true;
        }
    } else {
        mVidBuffering = false; mVidLastPos = -1.0;
    }

    // Resume: live-capture the position while playing + debounced save (~12s) so a
    // crash/sleep loses at most the last interval. Piggybacks the per-frame position read.
    if (mVidPlaying && mVidRate == 1.0 && !mVidStopped && !mVidResumeAsk) {
        vidCaptureResume();
        if (mVidResumeDirty && (mVidResumeSaveT < 0.0 || mEffectTime - mVidResumeSaveT > 12.0f)) {
            saveVideoConfig(); mVidResumeDirty = false; mVidResumeSaveT = mEffectTime;
        }
    }

    // A-B repeat: loop back to A once playback passes B.
    if (mVidRepeat == 3 && mVidAbA >= 0.0 && mVidAbB > mVidAbA
        && mVideoTest->position() >= mVidAbB) {
        mVideoTest->seek(mVidAbA);
        if (mVidHasAudio) vidAudioSeek(mVidAbA);
    }

    // End of stream: repeat / auto-advance / stop (web vidOnEnded).
    if (mVidPlaying && mVideoTest->ended()) {
        if (mVidRepeat == 1 || mVidRepeat == 2) {          // Repeat On / Title Repeat
            mVideoTest->seek(0.0); mVideoTest->play();
            if (mVidHasAudio) { vidAudioSeek(0.0); mVidAudio.play(); }
        } else if (mVidRepeat == 4) {                       // Folder Repeat: next title, wraps the list (web vidOnEnded 12361)
            vidStepTitle(1);
        } else if (mVidIdx < (int)mVidList.size() - 1) {    // auto-advance
            vidStepTitle(1);
        } else {
            mVidPlaying = false; mVidStopped = true; mVideoTest->pause();
            vidCaptureResume();   // finished at the tail -> clears the Resume bookmark
        }
    }
}

bool NanoMenu::renderVideoPlayer() {
    videoTick();
    if (mVidEnterT <= 0.001f && !mVidActive) return false;
    if (!mVideoTest) { if (mVidEnterT <= 0.001f) return false; }

    int W = mWidth, H = mHeight;
    float et = mVidEnterT;
    drawQuad(0, 0, (float)W, (float)H, 0.0f, 0.0f, 0.0f, 1.0f);   // black backdrop

    // Layer 1: the video frame at the chosen Screen Mode (0 Normal .. 4 Double Scale,
    // mapped 1:1 in NanoVideo::draw). updateFrame latches the newest decoded frame.
    if (mVideoTest) {
        mVideoTest->updateFrame();
        mVideoTest->draw(W, H, 0.0f, 0.0f, (float)W, (float)H, et, mVidScreenMode);
    }
    if (!mVideoTest) return et > 0.001f;   // exit fade: black only

    // Layer 1b: active subtitle cue (embedded text track or external sidecar), web 12739-12753.
    {
        const std::vector<VidCue>* cues = vidActiveSubCues();
        if (cues) {
            double tc = mVideoTest->position();
            const std::string* txt = nullptr;
            for (const auto& c : *cues) if (tc >= c.t && tc < c.t + c.d) { txt = &c.text; break; }
            if (txt && !txt->empty()) {
                float fs = ps3::fontScale(40.0f);   // web round(CH*0.040)
                float lh = H * 0.05f, ow = fmaxf(1.5f, H * 0.004f);
                std::vector<std::string> ls; size_t lp = 0, nl; const std::string& s = *txt;
                while ((nl = s.find('\n', lp)) != std::string::npos) { ls.push_back(s.substr(lp, nl - lp)); lp = nl + 1; }
                ls.push_back(s.substr(lp));
                float y0s = H * 0.855f - (float)(ls.size() - 1) * lh;   // multi-line stacks upward
                for (size_t li = 0; li < ls.size(); li++) {
                    if (ls[li].empty()) continue;
                    float yy = y0s + (float)li * lh;
                    float tw = measureText(ls[li].c_str(), fs);
                    float x = W * 0.5f - tw * 0.5f, topy = ps3::baselineToTopY(yy, fs);
                    for (int oy = -1; oy <= 1; oy++) for (int ox = -1; ox <= 1; ox++) {   // emulated dark outline
                        if (!ox && !oy) continue;
                        drawText(ls[li].c_str(), x + ox * ow, topy + oy * ow, fs, 0, 0, 0, 0.9f * et);
                    }
                    drawText(ls[li].c_str(), x, topy, fs, 1.0f, 1.0f, 1.0f, 0.98f * et);
                }
            }
        }
    }

    // Layer 1b (DVB): bitmap subtitle regions (NanoDvbSub), scaled from the DVB
    // display canvas to the screen. Decoded off-thread; drawn only once ready. The
    // region bitmap is cached in mVidDvbTex and only re-uploaded when it changes.
    if (mVidSubCur >= 0 && mVidSubCur < (int)mVidSubTracks.size() &&
        mVidSubTracks[mVidSubCur].dvb && mVidDvb && mVidDvbReady.load()) {
        double tc = mVideoTest->position();
        const std::vector<NanoDvbSub::Region>& regions = mVidDvb->regions();
        int dw = mVidDvb->displayWidth(), dh = mVidDvb->displayHeight();
        if (dw < 1) dw = 720;
        if (dh < 1) dh = 576;
        float sx = (float)W / (float)dw, sy = (float)H / (float)dh;
        for (int ri = 0; ri < (int)regions.size(); ri++) {
            const NanoDvbSub::Region& r = regions[ri];
            if (tc < r.startSec || tc >= r.endSec) continue;
            if (r.w <= 0 || r.h <= 0 || (int)r.rgba.size() < r.w * r.h * 4) continue;
            if (mVidDvbTexRegion != ri || !mVidDvbTex) {
                if (!mVidDvbTex) glGenTextures(1, &mVidDvbTex);
                glBindTexture(GL_TEXTURE_2D, mVidDvbTex);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, r.w, r.h, 0, GL_RGBA, GL_UNSIGNED_BYTE, r.rgba.data());
                mVidDvbTexRegion = ri;
            }
            drawIconTex(mVidDvbTex, r.x * sx, r.y * sy, r.w * sx, r.h * sy, 1.0f, 1.0f, 1.0f, et);
        }
    }

    // Layer 2: buffering spinner (warmup / post-seek refill / stall) - icon 114 rotating at
    // centre + "Buffering..." below it (web drawVideoPlayer 12754-12756).
    if (mVidBuffering) {
        GLuint sp = vidIcon(114);
        if (sp) {
            float sz = H * 0.06f, ccx = W * 0.5f, ccy = H * 0.5f;
            float ang = fmodf(mEffectTime * 1.66667f, 2.0f * 3.14159265f);   // web rotate(now/600), ~3.8s/rev
            float ca = cosf(ang), sn = sinf(ang), hw = sz * 0.5f, hh = sz * 0.5f;
            float lx[4] = {-hw, hw, hw, -hw}, ly[4] = {-hh, -hh, hh, hh};
            float u[4] = {0, 1, 1, 0}, v[4] = {0, 0, 1, 1};
            const int order[6] = {0, 1, 2, 0, 2, 3};
            GLfloat verts[12], uvs[12], cols[24];
            for (int k = 0; k < 6; k++) {
                int c = order[k];
                float rx = lx[c] * ca - ly[c] * sn, ry = lx[c] * sn + ly[c] * ca;
                verts[k * 2] = ((ccx + rx) / W) * 2.0f - 1.0f;
                verts[k * 2 + 1] = 1.0f - ((ccy + ry) / H) * 2.0f;
                uvs[k * 2] = u[c]; uvs[k * 2 + 1] = v[c];
                cols[k * 4] = cols[k * 4 + 1] = cols[k * 4 + 2] = 1.0f; cols[k * 4 + 3] = et * 0.9f;
            }
            glUseProgram(mTextProgram);
            if (mTextLocSharp >= 0) glUniform1f(mTextLocSharp, 0.0f);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, sp);
            glUniform1i(mTextLocTexture, 0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glVertexAttribPointer(mTextLocPosition, 2, GL_FLOAT, GL_FALSE, 0, verts);
            glEnableVertexAttribArray(mTextLocPosition);
            glVertexAttribPointer(mTextLocTexCoord, 2, GL_FLOAT, GL_FALSE, 0, uvs);
            glEnableVertexAttribArray(mTextLocTexCoord);
            glVertexAttribPointer(mTextLocColor, 4, GL_FLOAT, GL_FALSE, 0, cols);
            glEnableVertexAttribArray(mTextLocColor);
            glDrawArrays(GL_TRIANGLES, 0, 6);
            glDisableVertexAttribArray(mTextLocPosition);
            glDisableVertexAttribArray(mTextLocTexCoord);
            glDisableVertexAttribArray(mTextLocColor);
        }
        float tfs = ps3::fontScale(24.0f);
        const char* bt = "Buffering...";
        float bwid = measureText(bt, tfs);
        drawText(bt, W * 0.5f - bwid * 0.5f, ps3::baselineToTopY(H * 0.5f + H * 0.07f, tfs),
                 tfs, 1.0f, 1.0f, 1.0f, 0.9f * et);
    }

    double pos = mVideoTest->position(), dur = vidDuration();
    float bx = W * 0.10f, bw = W * 0.80f, by = H * 0.90f, bh = H * 0.006f;

    // Layer 3: title (top-left) + the seek bar + times. The bar auto-hides via
    // mVidHintUntil unless the Display OSD toggle or the control panel keeps it on.
    bool panelUp = mVidCpOpen || mVidCpClosing;
    float hintA = fminf(1.0f, fmaxf(0.0f, (mVidHintUntil - mEffectTime)) / 0.6f) * et;
    float barA = (mVidOsd || panelUp) ? et : hintA;
    // Title is shown with the bar.
    if (barA > 0.01f) {
        const VideoItem& v = mVideos[mVidList[mVidIdx]];
        std::string title = v.name; if (!mVidPlaying) title += "   (Paused)";
        float tfs = ps3::fontScale(26.0f);
        drawText(title.c_str(), bx, ps3::baselineToTopY(H * 0.10f, tfs), tfs, 1.0f, 1.0f, 1.0f, 0.95f * barA);
    }
    if (barA > 0.01f && dur > 0.0) {
        float frac = (float)(pos / dur); if (frac < 0) frac = 0; if (frac > 1) frac = 1;
        drawQuad(bx, by, bw, bh, 1.0f, 1.0f, 1.0f, 0.25f * barA);             // track
        drawQuad(bx, by, bw * frac, bh, 1.0f, 1.0f, 1.0f, 0.95f * barA);      // fill
        // Chapter markers (web 12766): white ticks at each chapter time.
        if (mVidChapters.size() > 1) {
            for (const auto& ch : mVidChapters) {
                float cf = (float)(ch.t / dur); if (cf < 0) cf = 0; if (cf > 1) cf = 1;
                drawQuad(bx + bw * cf - 1.0f, by - bh, 2.0f, bh * 3.0f, 1.0f, 1.0f, 1.0f, 0.65f * barA);
            }
        }
        // A-B Repeat markers + loop-region highlight (web 12768-12774).
        if (mVidRepeat == 3 && (mVidAbA >= 0.0 || mVidAbB >= 0.0)) {
            float af = mVidAbA >= 0.0 ? (float)(mVidAbA / dur) : -1.0f;
            float bf = mVidAbB >= 0.0 ? (float)(mVidAbB / dur) : -1.0f;
            if (af > 1) af = 1; if (bf > 1) bf = 1;
            if (af >= 0.0f && bf >= 0.0f)
                drawQuad(bx + bw * af, by, bw * (bf - af), bh, 0.47f, 0.784f, 1.0f, 0.45f * barA);   // loop region
            float lfs = ps3::fontScale(18.0f);
            if (af >= 0.0f) {
                drawQuad(bx + bw * af - 1.0f, by - bh, 2.0f, bh * 3.0f, 0.47f, 0.784f, 1.0f, 0.95f * barA);
                float aw = measureText("A", lfs);
                drawText("A", bx + bw * af - aw * 0.5f, ps3::baselineToTopY(by - bh * 2.5f, lfs), lfs, 0.47f, 0.784f, 1.0f, 0.95f * barA);
            }
            if (bf >= 0.0f) {
                drawQuad(bx + bw * bf - 1.0f, by - bh, 2.0f, bh * 3.0f, 0.47f, 0.784f, 1.0f, 0.95f * barA);
                float bwid = measureText("B", lfs);
                drawText("B", bx + bw * bf - bwid * 0.5f, ps3::baselineToTopY(by - bh * 2.5f, lfs), lfs, 0.47f, 0.784f, 1.0f, 0.95f * barA);
            }
        }
        ps3FillCircle(bx + bw * frac, by + bh * 0.5f, H * 0.008f, 1.0f, 1.0f, 1.0f, 0.95f * barA);   // knob (web arc)
        float fs = ps3::fontScale(20.0f);
        std::string el = vFmtTime(pos), tot = vFmtTime(dur);
        drawText(el.c_str(), bx, ps3::baselineToTopY(by - H * 0.012f, fs), fs, 0.96f, 0.96f, 0.96f, barA);
        float tw = measureText(tot.c_str(), fs);
        drawText(tot.c_str(), bx + bw - tw, ps3::baselineToTopY(by - H * 0.012f, fs), fs, 0.96f, 0.96f, 0.96f, barA);
    }

    // Layer 4: transient flash (top-center) - FF/Rewind/etc.
    if (!mVidTransient.empty() && mEffectTime < mVidTransientUntil) {
        float a = fminf(1.0f, (mVidTransientUntil - mEffectTime) / 0.4f) * et;
        float fs = ps3::fontScale(45.0f);
        float tw = measureText(mVidTransient.c_str(), fs);
        drawText(mVidTransient.c_str(), (W - tw) * 0.5f, ps3::baselineToTopY(H * 0.14f, fs), fs, 1.0f, 1.0f, 1.0f, 0.95f * a);
    }

    // Layer 5: screen-mode pill (bottom-left), fading like the transient.
    if (!mVidDispMode.empty() && mEffectTime < mVidDispModeUntil) {
        float a = fminf(1.0f, (mVidDispModeUntil - mEffectTime) / 0.4f) * et;
        float fs = ps3::fontScale(28.0f);
        drawText(mVidDispMode.c_str(), W * 0.057f, ps3::baselineToTopY(H * 0.82f, fs),
                 fs, 1.0f, 1.0f, 1.0f, 0.95f * a);
    }

    // Layer 8: help-hint pill (bottom-right) while the OSD bar shows and no panel is up.
    if (hintA > 0.01f && !panelUp) {
        float fs = ps3::fontScale(21.0f);
        const char* l1 = "Triangle: Control Panel";
        const char* l2 = "Circle: Home Menu";
        float w1 = measureText(l1, fs), w2 = measureText(l2, fs);
        float tw = fmaxf(w1, w2);
        float padx = W * 0.018f;
        float ph = H * 0.068f, pw = tw + padx * 2.0f;            // web pill height ~0.068*CH
        float px = W - pw - W * 0.03f, py = H * 0.86f;           // web py = CH*0.86
        drawQuad(px, py, pw, ph, 0.235f, 0.235f, 0.26f, 0.72f * hintA);
        drawText(l1, px + padx, ps3::baselineToTopY(py + H * 0.030f, fs), fs, 1.0f, 1.0f, 1.0f, 0.95f * hintA);
        drawText(l2, px + padx, ps3::baselineToTopY(py + H * 0.056f, fs), fs, 1.0f, 1.0f, 1.0f, 0.95f * hintA);
    }

    // Layer 9: the control panel (200ms open/close, web drawVideoPanel).
    if (mVidCpOpen) drawVideoPanel(-1.0f);
    else if (mVidCpClosing) {
        float p = fminf(1.0f, (mEffectTime - mVidCpCloseStart) / 0.2f);
        drawVideoPanel(1.0f - p);
        if (p >= 1.0f) mVidCpClosing = false;
    }

    // Layer 10: the Scene Search grid + Go To picker over everything (web 12823-12824).
    if (mVidSceneOpen) drawVideoScene(-1.0f);
    else if (mVidSceneClosing) {
        float p = fminf(1.0f, (mEffectTime - mVidSceneCloseStart) / 0.2f);
        drawVideoScene(p);
        if (p >= 1.0f) mVidSceneClosing = false;
    }
    if (mVidGoToOpen) drawVideoGoTo();
    // Layer 11: the Resume / Play-from-beginning prompt (shown on open over everything).
    if (mVidResumeAsk) drawVideoResume(et);
    // Layer 12: confirm/info/busy modal (Delete, Change Icon, errors), over everything.
    if (mVidDlgActive) drawVideoDialog(et);
    return true;
}

// ===========================================================================
// Video control panel (web VIDEO_CP / drawVideoPanel). Same look as the music
// panel (drawMpOpt) but the video grid layout, 1.5x focus, the videoplayer icons,
// and the screen-mode / repeat / volume / AV-settings submenus.
// ===========================================================================
struct VidCp { const char* act; const char* label; int ic; int gx; int gy; };
static const VidCp kVidCp[] = {
    {"scene",      "Scene Search",        18,  1, 0},
    {"goto",       "Go To",               19,  2, 0},
    {"audio",      "Audio Options",        3,  3, 0},
    {"subtitle",   "Subtitle Options",    22,  4, 0},
    {"volume",     "Volume Control",       2,  5, 0},
    {"screenmode", "Screen Mode",          1,  6, 0},
    {"chgicon",    "Change Icon",         24,  7, 0},
    {"del",        "Delete",               4,  8, 0},
    {"showinfo",   "Display",              0,  9, 0},
    {"beginning",  "Return to Beginning",  9,  0, 1},
    {"next",       "Next",                10,  1, 1},
    {"frev",       "Fast Reverse",        11,  2, 1},
    {"ffwd",       "Fast Forward",        12,  3, 1},
    {"play",       "Play",                 6,  4, 1},
    {"pause",      "Pause",                8,  5, 1},
    {"stop",       "Stop",                 7,  6, 1},
    {"flashr",     "Instant Replay",      15,  7, 1},
    {"flashf",     "Instant Advance",     16,  8, 1},
    {"srev",       "Slow (Reverse)",      20,  9, 1},
    {"sfwd",       "Slow (Forward)",      13, 10, 1},
    {"stepb",      "Frame Reverse",       21, 11, 1},
    {"stepf",      "Frame Advance",       14, 12, 1},
    {"repeat",     "Repeat",              17,  6, 2},
};
static const int kVidCpCount = (int)(sizeof(kVidCp) / sizeof(kVidCp[0]));
static int vidCpDefault() {
    for (int i = 0; i < kVidCpCount; i++) if (!strcmp(kVidCp[i].act, "play")) return i;
    return 0;
}
static const char* kVidScreenModes[] = {"Normal", "Full Screen", "Original", "Zoom", "Double Scale"};
static const char* kVidRepeatModes[] = {"Repeat Off", "Repeat On", "Title Repeat", "A-B Repeat", "Folder Repeat"};

void NanoMenu::vidPanelToggle() { if (mVidCpOpen) vidPanelClose(); else vidPanelOpen(); }

void NanoMenu::vidPanelOpen() {
    if (mVidCpOpen) return;
    mVidCpOpen = true; mVidCpClosing = false; mVidSubOpen = false;
    mVidCpSel = vidCpDefault(); mVidCpSelPrev = mVidCpSel;
    mVidCpAnimStart = mEffectTime; mVidCpFocusStart = mEffectTime;
    mVidCpPressStart = -1.0f; mVidCpPressSel = -1;
    mVidHintUntil = 0.0f;
    for (int i = 0; i < kVidCpCount; i++) vidIcon(kVidCp[i].ic);   // warm glyphs
}

void NanoMenu::vidPanelClose() {
    if (!mVidCpOpen && !mVidSubOpen) return;
    mVidSubOpen = false;
    mVidCpOpen = false; mVidCpClosing = true; mVidCpCloseStart = mEffectTime;
}

void NanoMenu::vidPanelBack() {
    if (mVidSubOpen) { mVidSubOpen = false; return; }
    vidPanelClose();
}

void NanoMenu::vidPanelMove(int dx, int dy) {
    if (!mVidCpOpen) return;
    if (mVidSubOpen) {
        if (mVidSubKind == 2) {   // Volume Control: Left/Right steps the -4..+4 bar + live-applies (web vidPanelMove 12660-12670)
            if (dx != 0) {
                mVidVolLevel += (dx > 0 ? 1 : -1);
                if (mVidVolLevel < -4) mVidVolLevel = -4;
                if (mVidVolLevel > 4) mVidVolLevel = 4;
                mVidVolume = (float)(mVidVolLevel + 4) / 8.0f;   // -4 -> 0.0, 0 -> 0.5 (Normal), +4 -> 1.0
                if (mVidHasAudio) mVidAudio.setVolume(mVidVolume);
            }
            return;
        }
        if (dy != 0 && !mVidSubOpts.empty()) {
            int n = (int)mVidSubOpts.size();
            mVidSubSel = (mVidSubSel + (dy > 0 ? 1 : -1) + n) % n;
        }
        return;
    }
    const VidCp& cur = kVidCp[mVidCpSel]; int best = -1; float bestd = 1e9f;
    for (int i = 0; i < kVidCpCount; i++) {
        if (i == mVidCpSel) continue;
        const VidCp& b = kVidCp[i];
        int ddx = b.gx - cur.gx, ddy = b.gy - cur.gy;
        if (dx > 0 && ddx <= 0) continue; if (dx < 0 && ddx >= 0) continue;
        if (dy > 0 && ddy <= 0) continue; if (dy < 0 && ddy >= 0) continue;
        float adx = (float)(ddx < 0 ? -ddx : ddx), ady = (float)(ddy < 0 ? -ddy : ddy);
        float along = dx ? adx : ady, perp = dx ? ady : adx;
        float d = along + perp * 3.0f;
        if (d < bestd) { bestd = d; best = i; }
    }
    if (best >= 0) { mVidCpSelPrev = mVidCpSel; mVidCpFocusStart = mEffectTime; mVidCpSel = best; }
}

void NanoMenu::vidSubBuild(int kind) {
    mVidSubKind = kind; mVidSubOpts.clear(); mVidSubSel = 0;
    switch (kind) {
        case 0: mVidSubLabel = "Screen Mode";
            for (const char* s : kVidScreenModes) mVidSubOpts.push_back(s);
            mVidSubSel = mVidScreenMode; break;
        case 1: mVidSubLabel = "Repeat";
            for (const char* s : kVidRepeatModes) mVidSubOpts.push_back(s);
            mVidSubSel = mVidRepeat; break;
        case 2: mVidSubLabel = "Volume Control";   // -4..+4 live segment bar (web cpSub.kind=='volume'),
            break;                                 // rendered by drawVideoPanel + stepped by vidPanelMove (no list)
        case 4: mVidSubLabel = "Audio Options";   // one row per audio track
            for (const auto& a : mVidAudTracks) mVidSubOpts.push_back(a.name);
            mVidSubSel = (mVidAudCur >= 0 && mVidAudCur < (int)mVidAudTracks.size()) ? mVidAudCur : 0; break;
        case 5: mVidSubLabel = "Subtitle Options";   // Off + each subtitle track
            // .ts line-21 captions: once the demuxer has actually seen GA94 cc_data, offer the
            // four caption channels (added once; no clutter for streams without captions).
            if (mVidTsMode && mVidTsDemux.hasCea608(0)) {
                bool have = false;
                for (const auto& t : mVidSubTracks) if (t.cea608) { have = true; break; }
                if (!have) {
                    static const char* kCcNames[4] = {
                        "Closed Captions (CC1)", "Closed Captions (CC2)",
                        "Closed Captions (CC3)", "Closed Captions (CC4)" };
                    for (int c = 0; c < 4; c++) {
                        VidSubTrk t; t.cea608 = true; t.ccChannel = c; t.name = kCcNames[c];
                        mVidSubTracks.push_back(t);
                    }
                }
            }
            mVidSubOpts.push_back("Off");
            for (const auto& t : mVidSubTracks) mVidSubOpts.push_back(t.name + (t.external ? "  (External)" : ""));
            mVidSubSel = mVidSubCur + 1; break;
    }
}

void NanoMenu::vidSubConfirm() {
    int sel = mVidSubSel;
    switch (mVidSubKind) {
        case 0:   // screen mode
            mVidScreenMode = sel;
            mVidDispMode = kVidScreenModes[sel]; mVidDispModeUntil = mEffectTime + 1.8f;
            mVidSubOpen = false; break;
        case 1:   // repeat
            if (sel == 3) {   // progressive A-B: set A, then B (needs >0.5s gap), then clear
                double p = mVideoTest ? mVideoTest->position() : 0.0;
                if (mVidAbA < 0.0) { mVidAbA = p; mVidAbB = -1.0; mVidRepeat = 3;
                                     mVidDispMode = "A-B Repeat: Point A set"; }
                else if (mVidAbB < 0.0) {            // B not set yet: accept if past A, else re-prompt A (web)
                    if (p > mVidAbA + 0.5) { mVidAbB = p; mVidDispMode = "A-B Repeat: Point B set"; }
                    else                   { mVidDispMode = "A-B Repeat: Point A set"; }
                }
                else { mVidAbA = mVidAbB = -1.0; mVidRepeat = 0; mVidDispMode = "A-B Repeat Off"; }
                mVidDispModeUntil = mEffectTime + 1.8f;
            } else { mVidRepeat = sel; mVidAbA = mVidAbB = -1.0;
                     mVidDispMode = kVidRepeatModes[sel]; mVidDispModeUntil = mEffectTime + 1.8f; }   // web: dispMode = mode name
            mVidSubOpen = false; break;
        case 2:   // volume: applied live by the -4..+4 segment bar in vidPanelMove; confirm just closes
            mVidSubOpen = false; break;
        case 4:   // audio track
            vidSetAudioTrack(sel); mVidSubOpen = false; break;
        case 5: { // subtitle track (row 0 = Off)
            mVidSubCur = sel - 1;
            if (mVidSubCur >= 0 && mVidSubCur < (int)mVidSubTracks.size()) {
                const VidSubTrk& t = mVidSubTracks[mVidSubCur];
                if (t.dvb) vidDvbSelect(t.file, t.dvbPid);   // lazy background decode
                else vidDvbFree();                            // leaving a DVB track
                // Live line-21 captions: turn the demuxer's CEA-608 decode on for this channel
                // (off for any non-CC track so it stops scanning user_data).
                if (mVidTsMode) mVidTsDemux.setCea608(t.cea608, t.ccChannel);
                mVidDispMode = std::string("Subtitle: ") + t.name + (t.external ? " (External)" : "");
            } else {
                mVidSubCur = -1; vidDvbFree();
                if (mVidTsMode) mVidTsDemux.setCea608(false, 0);
                mVidDispMode = "Subtitle: Off";
            }
            mVidDispModeUntil = mEffectTime + 1.8f;
            mVidSubOpen = false; } break;
    }
}

void NanoMenu::vidPanelActivate() {
    if (!mVidCpOpen) return;
    if (mVidSubOpen) { vidSubConfirm(); return; }
    mVidCpPressStart = mEffectTime; mVidCpPressSel = mVidCpSel;
    const char* a = kVidCp[mVidCpSel].act;
    if (!strcmp(a, "play"))            { if (!mVidPlaying || mVidStopped) vidTogglePlay(); }
    else if (!strcmp(a, "pause"))      { if (mVidPlaying && !mVidStopped) vidTogglePlay(); }
    else if (!strcmp(a, "stop"))       vidStop();
    else if (!strcmp(a, "ffwd"))       vidScan(+1);
    else if (!strcmp(a, "frev"))       vidScan(-1);
    else if (!strcmp(a, "sfwd"))       vidSlow(+1);
    else if (!strcmp(a, "srev"))       vidSlow(-1);
    else if (!strcmp(a, "stepf"))      vidStepFrame(+1);
    else if (!strcmp(a, "stepb"))      vidStepFrame(-1);
    else if (!strcmp(a, "flashf"))     vidFlash(+1);
    else if (!strcmp(a, "flashr"))     vidFlash(-1);
    else if (!strcmp(a, "beginning"))  vidBeginning();
    else if (!strcmp(a, "next"))       vidStepTitle(1);
    else if (!strcmp(a, "showinfo"))   { mVidOsd = !mVidOsd; mVidCpOpen = false; mVidCpClosing = false; }   // web: instant close
    else if (!strcmp(a, "screenmode")) { vidSubBuild(0); mVidSubOpen = true; }
    else if (!strcmp(a, "repeat"))     { vidSubBuild(1); mVidSubOpen = true; }
    else if (!strcmp(a, "volume"))     { vidSubBuild(2); mVidSubOpen = true; }
    else if (!strcmp(a, "goto"))       vidGoToOpen();
    else if (!strcmp(a, "scene"))      vidSceneOpen();
    else if (!strcmp(a, "audio")) {
        if (mVidAudTracks.empty()) { vidPanelClose(); vidDlgInfo("There is no audio."); }
        else { vidSubBuild(4); mVidSubOpen = true; }
    }
    else if (!strcmp(a, "subtitle")) {
        // Line-21 captions live behind the demuxer (added by vidSubBuild), not in
        // mVidSubTracks yet, so a .ts with only CC must not bail on the empty check.
        bool haveCc = mVidTsMode && mVidTsDemux.hasCea608(0);
        if (mVidSubTracks.empty() && !haveCc) { vidPanelClose(); vidDlgInfo("There are no subtitle options available."); }
        else { vidSubBuild(5); mVidSubOpen = true; }
    }
    else if (!strcmp(a, "del")) {   // web del: confirm (default No) -> "Delete completed." (a no-op like the web)
        vidPanelClose();
        vidDlgConfirm("The title that is currently playing will be deleted.\nAre you sure you want to continue?", 1);
    }
    else if (!strcmp(a, "chgicon")) {
        double rem = mVideoTest ? (vidDuration() - mVideoTest->position()) : 0.0;
        vidPanelClose();
        if (rem < 15.0) vidDlgInfo("You cannot create an icon less than 15 seconds in length.");
        else vidDlgConfirm("15 seconds of video starting from this scene will be set as the icon.\n"
                           "If an icon has already been set, it will be overwritten.\nDo you want to continue?", 2);
    }
}

void NanoMenu::drawVideoPanel(float closeT) {
    int W = mWidth, H = mHeight;
    float t = (closeT >= 0.0f) ? closeT
            : (mVidCpAnimStart >= 0.0f ? fminf(1.0f, (mEffectTime - mVidCpAnimStart) / 0.2f) : 1.0f);
    if (t < 0) t = 0;
    float A = t * mVidEnterT;                              // overall panel alpha
    float x0 = W * 0.1589f - (1.0f - t) * W * 0.012f;
    float y0 = H * 0.4148f;
    float colW = W * 0.03526f, rowH = H * 0.061f, sz = H * 0.060f;
    float pulse = 0.5f + 0.5f * sinf(mEffectTime * 2.0f * 3.14159265f / 1.5f);

    for (int i = 0; i < kVidCpCount; i++) {
        const VidCp& b = kVidCp[i];
        bool focus = (i == mVidCpSel);
        float cx = x0 + b.gx * colW, cy = y0 + b.gy * rowH;
        float baseScale = focus ? 1.5f : 1.0f;
        if (mVidCpFocusStart >= 0.0f) {
            float fe = fminf(1.0f, (mEffectTime - mVidCpFocusStart) / 0.14f);
            float fk = 1.0f - powf(1.0f - fe, 3.0f);
            if (focus) baseScale = 1.0f + 0.5f * fk;
            else if (i == mVidCpSelPrev) baseScale = 1.5f - 0.5f * fk;
        }
        float ps = baseScale, flash = 0.0f;
        if (mVidCpPressSel == i && mVidCpPressStart >= 0.0f) {
            float e = (mEffectTime - mVidCpPressStart) / 0.24f;
            if (e < 1.0f) { float s = sinf(e * 3.14159265f); ps *= 1.0f - 0.18f * s; flash = s * 0.55f; }
        }
        GLuint tex = vidIcon(b.ic); float ar = vidIconAR(b.ic);
        auto glyph = [&](float dx, float dy, float r, float g, float bl, float al, float scl) {
            if (!tex) return;
            float hh = sz * scl, ww = hh * ar;
            drawIconTex(tex, cx - ww * 0.5f + dx, cy - hh * 0.5f + dy, ww, hh, r, g, bl, A * al);
        };
        if (focus) {
            glyph(0, 0, 0.86f, 0.92f, 1.0f, (0.22f + 0.18f * pulse) * 0.6f, ps * 1.18f);  // breathing halo
            glyph(0, 0, 1, 1, 1, 1.0f, ps);                                               // crisp glyph
        } else {
            glyph(sz * 0.03f, sz * 0.04f, 0, 0, 0, 0.5f, ps);   // drop shadow
            glyph(0, 0, 1, 1, 1, 0.85f, ps);                    // dimmed glyph
        }
        if (flash > 0.0f) glyph(0, 0, 1, 1, 1, flash, ps);      // activate brightness pop
    }

    // focused label (left-aligned) + the SELECT/START button-hint pills
    if (mVidCpSel >= 0 && mVidCpSel < kVidCpCount) {
        const VidCp& b = kVidCp[mVidCpSel];
        std::string lab = b.label;
        if (!strcmp(b.act, "ffwd") && mVidRate > 1.0) {
            char x[40]; snprintf(x, sizeof(x), "Fast Forward  x %g", mVidRate); lab = x;
        } else if (!strcmp(b.act, "frev") && mVidRate < -1.0) {
            char x[40]; snprintf(x, sizeof(x), "Fast Reverse  x %g", -mVidRate); lab = x;
        } else if (!strcmp(b.act, "beginning")) {
            lab = (mVideoTest && mVideoTest->position() > 2.0) ? "Return to Beginning" : "Previous";
        }
        float lx = x0 + colW * 0.1f, ly = y0 + 2.9f * rowH;
        float ls = ps3::fontScale(24.0f);
        drawText(lab.c_str(), lx, ps3::baselineToTopY(ly, ls), ls, 0.96f, 0.96f, 0.96f, A);
        if (!mVidSubOpen) {
            const char* pill = nullptr;
            if (!strcmp(b.act, "showinfo")) pill = "SELECT";
            else if (!strcmp(b.act, "play")) pill = "START";
            if (pill) {
                float lw = measureText(lab.c_str(), ls);
                float pillH = H * 0.030f, gap = W * 0.006f;
                float pfs = ps3::fontScale(13.8f), pillTxtW = measureText(pill, pfs);
                float pw = pillTxtW + W * 0.012f;
                float px = lx + lw + gap, py = ly - pillH * 0.82f;
                auto cap = [&](float qx, float qy, float qw, float qh, float r, float g, float bl, float a) {
                    float rr = qh * 0.5f;
                    drawQuad(qx + rr, qy, qw - 2.0f * rr, qh, r, g, bl, a);
                    ps3FillCircle(qx + rr, qy + rr, rr, r, g, bl, a);
                    ps3FillCircle(qx + qw - rr, qy + rr, rr, r, g, bl, a);
                };
                float bw = 1.2f;
                cap(px - bw, py - bw, pw + 2.0f * bw, pillH + 2.0f * bw, 225/255.f, 225/255.f, 225/255.f, 0.7f * A);
                cap(px, py, pw, pillH, 150/255.f, 150/255.f, 150/255.f, 0.55f * A);
                drawText(pill, px + pw * 0.5f - pillTxtW * 0.5f, ps3::baselineToTopY(py + pillH * 0.56f, pfs),
                         pfs, 1, 1, 1, 0.95f * A);
            }
        }
    }

    // Volume Control: the -4..+4 nine-segment live bar (web drawVideoPanel 12818-12834), mirroring
    // the music player's drawMpVolMeter. Stepped by Left/Right (vidPanelMove), applied live.
    if (mVidSubOpen && mVidSubKind == 2) {
        float sx = x0 + colW * 0.1f, sy = y0 + 3.7f * rowH;
        float fs = ps3::fontScale(26.0f), fpx = ps3::emPx(fs);   // web fs = round(CH*0.026)
        int lvl = mVidVolLevel;
        char nm[8];
        if (lvl == 0)      snprintf(nm, sizeof(nm), "Normal");
        else if (lvl > 0)  snprintf(nm, sizeof(nm), "+%d", lvl);
        else               snprintf(nm, sizeof(nm), "%d", lvl);
        drawQuad(sx - fpx * 0.6f, sy - fpx * 1.5f, W * 0.20f, fpx * 3.6f, 0, 0, 0, 0.55f * A);   // backplate
        drawText(nm, sx, ps3::baselineToTopY(sy - fpx * 0.4f, fs), fs, 1, 1, 1, A);              // level name
        float segW = W * 0.0095f, gp = W * 0.004f, hh = H * 0.028f;
        float my = sy + fpx * 0.3f, x0s = sx + fpx * 0.9f;
        float mw = measureText("-", fs);
        drawText("-", x0s - W * 0.008f - mw, ps3::baselineToTopY(my + hh * 0.85f, fs), fs, 1, 1, 1, A);
        int filled = lvl + 5;                                  // -4 -> 1 seg, 0 -> 5, +4 -> 9
        for (int i = 0; i < 9; i++) {
            float fx = x0s + i * (segW + gp);
            if (i < filled) drawQuad(fx, my, segW, hh, 0.470f, 0.882f, 1.0f, 0.95f * A);   // rgba(120,225,255)
            else            drawQuad(fx, my, segW, hh, 1.0f, 1.0f, 1.0f, 0.20f * A);
        }
        drawText("+", x0s + 9 * (segW + gp) + W * 0.004f, ps3::baselineToTopY(my + hh * 0.85f, fs), fs, 1, 1, 1, A);
    }

    // submenu plate + rows (web drawVideoPanel submenu, 12685-12701): plate is sized to the
    // widest option, rows centred in their highlight band.
    if (mVidSubOpen && !mVidSubOpts.empty()) {
        float sx = x0 + colW * 0.1f, sy = y0 + 3.5f * rowH;
        float lh = H * 0.045f, fs = ps3::fontScale(26.0f);
        float fpx = ps3::emPx(fs);              // device-px font size (web 'fs' inset unit)
        int n = (int)mVidSubOpts.size();
        float mw = 0.0f;
        for (int i = 0; i < n; i++) { float w = measureText(mVidSubOpts[i].c_str(), fs); if (w > mw) mw = w; }
        drawQuad(sx - fpx * 0.6f, sy - lh * 0.7f, mw + fpx * 1.6f, lh * n + lh * 0.3f, 0, 0, 0, 0.55f * A);
        for (int i = 0; i < n; i++) {
            float oy = sy + i * lh + lh * 0.1f;  // row centre (web textBaseline='middle' at oy)
            bool sel = (i == mVidSubSel);
            if (sel) drawQuad(sx - fpx * 0.4f, oy - lh * 0.45f, mw + fpx * 0.8f, lh * 0.9f, 1, 1, 1, 0.20f * A);
            float c = sel ? 1.0f : 0.88f;
            drawText(mVidSubOpts[i].c_str(), sx, ps3::baselineToTopY(oy + fpx * 0.35f, fs),
                     fs, c, c, c, (sel ? 1.0f : 0.85f) * A);
        }
    }
}

// ---------------------------------------------------------------------------
// Go To (in-player H:MM:SS seek picker, web vidGoTo* / drawVideoGoTo).
// ---------------------------------------------------------------------------
void NanoMenu::vidGoToOpen() {
    double p = mVideoTest ? mVideoTest->position() : 0.0;
    int t = (int)p;
    mVidGoToH = t / 3600; mVidGoToM = (t % 3600) / 60; mVidGoToS = t % 60; mVidGoToField = 0;
    mVidGoToOpen = true; vidPanelClose();
}
void NanoMenu::vidGoToMove(int dx) {
    mVidGoToField += (dx > 0 ? 1 : -1);
    if (mVidGoToField < 0) mVidGoToField = 0; if (mVidGoToField > 2) mVidGoToField = 2;
}
void NanoMenu::vidGoToAdjust(int dy) {
    int d = dy > 0 ? 1 : -1;
    if (mVidGoToField == 0)      { mVidGoToH += d; if (mVidGoToH < 0) mVidGoToH = 0; if (mVidGoToH > 9) mVidGoToH = 9; }
    else if (mVidGoToField == 1) { mVidGoToM = (mVidGoToM + d + 60) % 60; }
    else                         { mVidGoToS = (mVidGoToS + d + 60) % 60; }
}
void NanoMenu::vidGoToActivate() {
    double target = mVidGoToH * 3600.0 + mVidGoToM * 60.0 + mVidGoToS;
    double dur = mVideoTest ? vidDuration() : 0.0;
    if (dur > 0.0 && target > dur) {
        vidDlgInfo("The range you can specify has been exceeded.");   // web: centred modal over the picker
        return;
    }
    if (mVideoTest) { mVidRate = 1.0; mVideoTest->seek(target); if (mVidPlaying) mVideoTest->play(); }
    mVidGoToOpen = false; mVidHintUntil = mEffectTime + 1.5f;
}
void NanoMenu::vidGoToClose() { mVidGoToOpen = false; }

void NanoMenu::drawVideoGoTo() {
    int W = mWidth, H = mHeight; float et = mVidEnterT;
    drawQuad(0, 0, (float)W, (float)H, 0, 0, 0, 0.72f * et);
    float ts = ps3::fontScale(34.0f);
    const char* title = "Go To"; float tw = measureText(title, ts);
    drawText(title, (W - tw) * 0.5f, ps3::baselineToTopY(H * 0.36f, ts), ts, 1, 1, 1, 0.95f * et);

    char hb[8], mb[8], sb[8];
    snprintf(hb, sizeof(hb), "%d", mVidGoToH);
    snprintf(mb, sizeof(mb), "%02d", mVidGoToM);
    snprintf(sb, sizeof(sb), "%02d", mVidGoToS);
    const char* seg[5] = {hb, " : ", mb, " : ", sb};
    int segField[5] = {0, -1, 1, -1, 2};
    float fh = ps3::fontScale(70.0f);
    float total = 0; for (const char* s : seg) total += measureText(s, fh);
    float x = (W - total) * 0.5f, by = H * 0.52f;
    for (int i = 0; i < 5; i++) {
        float w = measureText(seg[i], fh);
        bool foc = (segField[i] == mVidGoToField);
        if (foc) { float pad = fh * 0.1f; drawQuad(x - pad, by - fh * 0.6f, w + pad * 2.0f, fh * 1.2f, 1, 1, 1, 0.18f * et); }
        float c = foc ? 1.0f : 0.88f;
        drawText(seg[i], x, ps3::baselineToTopY(by + fh * 0.4f, fh), fh, c, c, c, et);
        x += w;
    }
    float fs = ps3::fontScale(20.0f);
    const char* foot = "Up/Down  Adjust     Left/Right  Field     Cross  Enter     Circle  Back";
    float fw = measureText(foot, fs);
    drawText(foot, (W - fw) * 0.5f, ps3::baselineToTopY(H * 0.66f, fs), fs, 1, 1, 1, 0.85f * et);
}

// Resume / Play-from-beginning prompt shown on opening a partly-watched video.
// Drawn in the XMB fullscreen-dialog style (the System Update message dialog): the
// same 1920x1080 virtual space, top/bottom dividers, header title, centred body and
// glowing breathing options (ps3DlgOption) + footer button hints, so it matches the
// firmware message dialogs 1:1 and adapts to any resolution/aspect/orientation.
void NanoMenu::drawVideoResume(float et) {
    int W = mWidth, H = mHeight;
    float A = (et > 0.0f) ? et : 1.0f;
    // Darken the just-opened (paused) video frame behind the dialog so it reads,
    // exactly like the fullscreen XMB dialogs darken the blurred menu behind them.
    drawQuad(0, 0, (float)W, (float)H, 0, 0, 0, 0.78f * A);

    { ps3::LayoutParams lp; lp.panelW = mWidth; lp.panelH = mHeight; lp.uiScale = mPs3UiScale; ps3::layoutCompute(lp); }
    setGlyphAtlasAA(true);
    mTextOutlineRatio = 0.5f;
    float ui = mPs3UiScale; if (ui < 0.5f) ui = 0.5f; if (ui > 2.0f) ui = 2.0f;
    const float S = ps3::gScale / ui;
    const float offX = ps3::gFrameX + (ps3::gFrameW - S * ps3::XCF(ps3::VW)) * 0.5f;
    const float offY = ps3::gFrameY + ps3::gFrameH * 0.5f - S * (ps3::VH * 0.5f);
    auto X  = [&](float vx) { return S * vx + offX; };
    auto XC = [&](float vx) { return S * ps3::XCF(vx) + offX; };
    auto Y  = [&](float vy) { return S * vy + offY; };
    auto DS = [&](float v)  { return S * v; };
    const float fb = ps3DlgFontBoost();
    auto FS = [&](float px) { return S * px * fb / 16.0f; };
    const float VW = ps3::VW;
    const float innerTop = 199.0f, innerBot = 880.0f;

    // Header title + top/bottom frame dividers.
    ps3DlgText("Resume Playback", X(160.0f), Y(187.0f), FS(28.0f), 1.0f, 1.0f, 1.0f, A, 0);
    float divLw = fmaxf(1.0f, DS(1.0f));
    drawQuad(ps3::gFrameX, Y(innerTop), ps3::gFrameW, divLw, 1.0f, 1.0f, 1.0f, 0.55f * A);
    drawQuad(ps3::gFrameX, Y(innerBot), ps3::gFrameW, divLw, 1.0f, 1.0f, 1.0f, 0.55f * A);

    // Body: where playback was last stopped, centred like the confirm dialogs.
    std::string sub = std::string("Last stopped at ") + vFmtTime(mVidResumeAskSec);
    ps3DlgText(sub.c_str(), XC(VW * 0.5f), Y(innerTop + 150.0f), FS(26.0f), 0.95f, 0.95f, 0.95f, A, 1);

    // Options: the selected one carries the XMB active-label breathing glow.
    const char* opt[2] = { "Resume", "Play from beginning" };
    const float optTopV = innerTop + 330.0f, optSpacingV = 64.0f;
    for (int i = 0; i < 2; i++)
        ps3DlgOption(opt[i], XC(VW * 0.5f), Y(optTopV + (float)i * optSpacingV),
                     i == mVidResumeSel, false, A, S);

    // Footer button hints (X selects the highlight, O resumes by default).
    float hintY = Y(909.0f);
    ps3DlgHint(XC(VW * 0.401f), true,  "Select", hintY, S, A);
    ps3DlgHint(XC(VW * 0.629f), false, "Resume", hintY, S, A);
}

// Video-player modal dialog (info / confirm / busy), drawn in the same XMB message-dialog
// style as the Resume prompt. Used for the Delete + Change Icon confirms, the Creating-icon
// busy->result chain, and the no-audio / no-subtitle / Go-To-over-limit errors.
void NanoMenu::vidDlgInfo(const std::string& body) {
    mVidDlgActive = true; mVidDlgKind = 0; mVidDlgBody = body; mVidDlgSel = 0; mVidDlgBusyUntil = 0.0f;
}
void NanoMenu::vidDlgConfirm(const std::string& body, int yesAct) {
    mVidDlgActive = true; mVidDlgKind = 1; mVidDlgBody = body; mVidDlgYesAct = yesAct; mVidDlgSel = 1; mVidDlgBusyUntil = 0.0f;   // default = No (web defaultSel)
}
void NanoMenu::vidDlgActivate() {                  // Cross
    if (!mVidDlgActive) return;
    if (mVidDlgKind == 2) return;                  // busy: no button
    if (mVidDlgKind == 0) { mVidDlgActive = false; return; }   // info: OK dismisses
    if (mVidDlgSel == 0) {                          // confirm -> Yes
        if (mVidDlgYesAct == 1) vidDlgInfo("Delete completed.");
        else if (mVidDlgYesAct == 2) {              // Change Icon: busy -> result (web vidCreateIcon)
            mVidDlgActive = true; mVidDlgKind = 2; mVidDlgBody = "Creating icon...\nPlease wait.";
            mVidDlgBusyUntil = mEffectTime + 0.65f;
        } else mVidDlgActive = false;
    } else mVidDlgActive = false;                   // confirm -> No
}
void NanoMenu::vidDlgBack() {                       // Circle / Back
    if (!mVidDlgActive) return;
    if (mVidDlgKind == 2) return;                  // busy: not dismissable
    mVidDlgActive = false;                          // info OK / confirm cancel (= No)
}

void NanoMenu::drawVideoDialog(float et) {
    int W = mWidth, H = mHeight;
    float A = (et > 0.0f) ? et : 1.0f;
    drawQuad(0, 0, (float)W, (float)H, 0, 0, 0, 0.78f * A);   // darken behind the modal
    { ps3::LayoutParams lp; lp.panelW = mWidth; lp.panelH = mHeight; lp.uiScale = mPs3UiScale; ps3::layoutCompute(lp); }
    setGlyphAtlasAA(true);
    mTextOutlineRatio = 0.5f;
    float ui = mPs3UiScale; if (ui < 0.5f) ui = 0.5f; if (ui > 2.0f) ui = 2.0f;
    const float S = ps3::gScale / ui;
    const float offX = ps3::gFrameX + (ps3::gFrameW - S * ps3::XCF(ps3::VW)) * 0.5f;
    const float offY = ps3::gFrameY + ps3::gFrameH * 0.5f - S * (ps3::VH * 0.5f);
    auto XC = [&](float vx) { return S * ps3::XCF(vx) + offX; };
    auto Y  = [&](float vy) { return S * vy + offY; };
    auto DS = [&](float v)  { return S * v; };
    const float fb = ps3DlgFontBoost();
    auto FS = [&](float px) { return S * px * fb / 16.0f; };
    const float VW = ps3::VW;
    const float innerTop = 199.0f, innerBot = 880.0f;

    float divLw = fmaxf(1.0f, DS(1.0f));
    drawQuad(ps3::gFrameX, Y(innerTop), ps3::gFrameW, divLw, 1.0f, 1.0f, 1.0f, 0.55f * A);
    drawQuad(ps3::gFrameX, Y(innerBot), ps3::gFrameW, divLw, 1.0f, 1.0f, 1.0f, 0.55f * A);

    // Body (centred, multi-line on '\n'). Confirm sits higher to leave room for Yes/No.
    std::vector<std::string> lines; std::string cur;
    for (char c : mVidDlgBody) { if (c == '\n') { lines.push_back(cur); cur.clear(); } else cur.push_back(c); }
    lines.push_back(cur);
    float lh = DS(40.0f);
    float bodyCenterV = (mVidDlgKind == 1) ? (innerTop + 230.0f) : ((innerTop + innerBot) * 0.5f);
    float by = Y(bodyCenterV) - (float)((int)lines.size() - 1) * lh * 0.5f;
    for (auto& ln : lines) { ps3DlgText(ln.c_str(), XC(VW * 0.5f), by, FS(26.0f), 0.95f, 0.95f, 0.95f, A, 1); by += lh; }

    if (mVidDlgKind == 1) {                          // confirm: Yes / No (glowing selected)
        const char* opt[2] = { "Yes", "No" };
        float bxc = XC(VW * 0.5f) - DS(110.0f), byv = Y(innerTop + 470.0f);
        for (int i = 0; i < 2; i++)
            ps3DlgOption(opt[i], bxc + (float)i * DS(220.0f), byv, i == mVidDlgSel, false, A, S);
    }

    float hintY = Y(909.0f);
    if (mVidDlgKind == 0) {
        ps3DlgHint(XC(VW * 0.629f), false, "OK", hintY, S, A);
    } else if (mVidDlgKind == 1) {
        ps3DlgHint(XC(VW * 0.401f), true,  "Enter", hintY, S, A);
        ps3DlgHint(XC(VW * 0.629f), false, "Back", hintY, S, A);
    }
    // busy (kind 2): no button hint (web busy dialogs)
}

// ===========================================================================
// Scene Search (web vidSceneOpen/Move/Activate/Close + drawVideoScene): a
// chapter grid; Cross seeks to the focused chapter, Circle closes. Chapters
// come from vidParseChapters (mp4/mov chpl+chap, mkv EBML). Per the web the
// thumbnails are real frames at the chapter times via authored assets; nano has
// none on-device and live per-chapter HW frame extraction would glitch playback
// on this decoder, so the grid uses the web's gray-placeholder cell look with
// the "Chapter N  M:SS" label. Nothing is allocated here (no extra decode/mem).
// (Web also has an interval grid for chapter-less clips; deferred for nano - its
// own layout breaks past ~8 rows, e.g. a 75-min .ts, and Go To already time-jumps.)
// ===========================================================================
void NanoMenu::vidSceneOpen() {
    if (mVidChapters.empty()) { vidShowTransient("No chapters", 1400.0f); return; }
    double cur = mVideoTest ? mVideoTest->position() : 0.0;
    int sel = 0;
    for (size_t i = 0; i < mVidChapters.size(); i++) if (cur >= mVidChapters[i].t) sel = (int)i;
    mVidSceneSel = sel; mVidSceneSelPrev = sel;
    mVidSceneOpen = true; mVidSceneClosing = false;
    mVidSceneAnimStart = mEffectTime; mVidSceneFocusStart = mEffectTime;
    vidPanelClose();                          // web: v.panel = false
}

void NanoMenu::vidSceneClose() {
    if (!mVidSceneOpen) return;
    mVidSceneOpen = false; mVidSceneClosing = true; mVidSceneCloseStart = mEffectTime;
}

void NanoMenu::vidSceneMove(int dx, int dy) {
    if (!mVidSceneOpen || mVidChapters.empty()) return;
    int n = (int)mVidChapters.size();
    int cols = n < 4 ? n : 4; if (cols < 1) cols = 1;
    int sel = mVidSceneSel;
    if (dx) sel = (sel + dx + n) % n;                          // L/R wrap (web step=1)
    if (dy) sel = (sel + dy * cols + n) % n;                   // U/D by a row, wraps (web step=cols)
    if (sel != mVidSceneSel) { mVidSceneSelPrev = mVidSceneSel; mVidSceneFocusStart = mEffectTime; mVidSceneSel = sel; }
}

void NanoMenu::vidSceneActivate() {
    if (!mVidSceneOpen || mVidChapters.empty()) return;
    int sel = mVidSceneSel;
    if (sel < 0 || sel >= (int)mVidChapters.size()) return;
    double t = mVidChapters[sel].t;
    if (mVideoTest) {
        mVidRate = 1.0;
        mVideoTest->seek(t);
        if (mVidHasAudio) vidAudioSeek(t);
        if (mVidPlaying) mVideoTest->play();
    }
    mVidSceneOpen = false; mVidSceneClosing = true; mVidSceneCloseStart = mEffectTime;
    mVidHintUntil = mEffectTime + 1.5f;
}

void NanoMenu::drawVideoScene(float closeT) {
    int W = mWidth, H = mHeight;
    float t = (closeT >= 0.0f) ? (1.0f - closeT)
            : (mVidSceneAnimStart >= 0.0f ? fminf(1.0f, (mEffectTime - mVidSceneAnimStart) / 0.2f) : 1.0f);
    if (t < 0) t = 0;
    float A = t * mVidEnterT;
    if (A <= 0.001f) return;
    int n = (int)mVidChapters.size();
    if (n <= 0) return;

    // Dim backdrop (web rgba(0,0,0,0.72)).
    drawQuad(0, 0, (float)W, (float)H, 0, 0, 0, 0.72f * A);

    // Title (web round(CH*0.034) at CW*0.10, CH*0.16, left, dark shadow).
    {
        float ts = ps3::fontScale(34.0f);
        float tx = W * 0.10f, topy = ps3::baselineToTopY(H * 0.16f, ts);
        drawText("Scene Search", tx + 2.0f, topy + 2.0f, ts, 0, 0, 0, 0.7f * A);
        drawText("Scene Search", tx, topy, ts, 1.0f, 1.0f, 1.0f, 0.95f * A);
    }

    // Grid: cols = min(n,4); cells tw = CW*0.20, th = tw*9/16, gap = CW*0.03. The
    // web lays everything on one row (its demo data is <=4 chapters); nano wraps to
    // additional rows so files with more chapters do not overlap.
    int cols = n < 4 ? n : 4; if (cols < 1) cols = 1;
    float tw = W * 0.20f, th = tw * 9.0f / 16.0f, gap = W * 0.03f;
    float totalW = cols * tw + (cols - 1) * gap;
    float x0 = (W - totalW) * 0.5f, y0 = H * 0.42f;
    float rowH = th + H * 0.10f;                     // cell + label + spacing
    float lblFs = ps3::fontScale(22.0f);             // web round(CH*0.022)

    for (int i = 0; i < n; i++) {
        int col = i % cols, row = i / cols;
        float cx = x0 + col * (tw + gap), cy = y0 + row * rowH;
        bool sel = (i == mVidSceneSel);

        // Placeholder cell (web fallback rgba(40,40,46,0.9)).
        drawQuad(cx, cy, tw, th, 0.157f, 0.157f, 0.18f, 0.9f * A);

        // Border: 4 thin quads. Selected = bright + a faint outer glow ring.
        float bw = sel ? fmaxf(2.0f, H * 0.004f) : fmaxf(1.0f, H * 0.0022f);
        float br, bg, bb, ba;
        if (sel) { br = bg = bb = 1.0f; ba = 0.95f * A;
                   float g = fmaxf(3.0f, H * 0.012f);   // soft glow ring
                   drawQuad(cx - g, cy - g, tw + 2 * g, bw + 2 * g, 0.86f, 0.92f, 1.0f, 0.18f * A);
                   drawQuad(cx - g, cy + th - bw - g, tw + 2 * g, bw + 2 * g, 0.86f, 0.92f, 1.0f, 0.18f * A);
                   drawQuad(cx - g, cy - g, bw + 2 * g, th + 2 * g, 0.86f, 0.92f, 1.0f, 0.18f * A);
                   drawQuad(cx + tw - bw - g, cy - g, bw + 2 * g, th + 2 * g, 0.86f, 0.92f, 1.0f, 0.18f * A); }
        else { br = bg = bb = 0.7f; ba = 0.5f * A; }
        drawQuad(cx, cy, tw, bw, br, bg, bb, ba);                 // top
        drawQuad(cx, cy + th - bw, tw, bw, br, bg, bb, ba);       // bottom
        drawQuad(cx, cy, bw, th, br, bg, bb, ba);                 // left
        drawQuad(cx + tw - bw, cy, bw, th, br, bg, bb, ba);       // right

        // Label "Chapter N   M:SS" (web cy + th + CH*0.04, left).
        char lbl[64];
        snprintf(lbl, sizeof(lbl), "Chapter %d   %s", i + 1, vFmtTime(mVidChapters[i].t).c_str());
        float lc = sel ? 1.0f : 0.82f;
        drawText(lbl, cx, ps3::baselineToTopY(cy + th + H * 0.034f, lblFs), lblFs, lc, lc, lc, (sel ? 1.0f : 0.85f) * A);   // web cy+th+CH*0.034
    }

    // Footer hint (web "✕ Enter   ○ Back").
    float ffs = ps3::fontScale(20.0f);
    const char* foot = "Cross  Enter      Circle  Back";
    float fw = measureText(foot, ffs);
    drawText(foot, (W - fw) * 0.5f, ps3::baselineToTopY(H * 0.90f, ffs), ffs, 0.92f, 0.92f, 0.92f, 0.9f * A);
}

}  // namespace android
