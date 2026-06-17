// NanoMenuVideo.cpp - the Video library glue: nano_video.json model + persist +
// recursive scan + folder import, the Video-column content, and the Sort By (Y).
// Mirrors the Music library (NanoMenuMusic.cpp); HW playback is in NanoVideo + the
// player screen. The web XMB has no video library/import/sort (its video items are
// flat demo entries); these are a nano addition (user request) on top of a 1:1
// player UI. Everything is lazy: parsed on first Video focus, nothing at boot.
#include "NanoMenu.h"
#include "NanoMenuPS3.h"
#include "NanoVideo.h"
#include "NanoJson.h"

#include <algorithm>
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
    if (!mVideoFolders.empty() && !mVideoScanRunning) videoScanAsync();
}

bool NanoMenu::videoStorageReady() const {
    if (mVideoFolders.empty()) return true;
    char bc[PROPERTY_VALUE_MAX] = {0};
    property_get("sys.boot_completed", bc, "0");
    if (bc[0] != '1') return false;
    for (const auto& f : mVideoFolders) {
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
    std::vector<std::string> folders = mVideoFolders;
    bool forceReprobe = (mVideoCfgVersion < kVideoMetaVersion);
    std::vector<VideoItem> cacheVec = mVideos;
    std::map<std::string, const VideoItem*> cache;
    if (!forceReprobe) for (const auto& v : cacheVec) cache[v.file] = &v;

    std::vector<std::string> files;
    for (const auto& f : folders) vScanDirRecursive(f, files, 0);
    // Storage-not-ready guard: do not publish an empty result that would wipe the
    // library if a configured folder's volume is not mounted yet.
    if (files.empty() && !folders.empty()) {
        bool anyUnreadable = false;
        for (const auto& f : folders) { DIR* d = opendir(f.c_str()); if (!d) anyUnreadable = true; else closedir(d); }
        if (anyUnreadable) {
            mVideoScanRunning = false; mVideoScanPending = true;
            VLOGW("NanoMenu: video scan found nothing + a folder is unreadable; deferring");
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

// Enumerate the file's audio tracks + embedded text-subtitle tracks, then probe for
// external .srt/.vtt sidecars next to it. Cheap (text + extractor walk); nothing resident.
void NanoMenu::vidBuildTracks(const std::string& file) {
    mVidAudTracks.clear();
    mVidSubTracks.clear();
    std::vector<int> embSubIdx;
    std::vector<std::string> embSubName;
    int fd = ::open(file.c_str(), O_RDONLY);
    if (fd >= 0) {
        struct stat st;
        if (fstat(fd, &st) == 0 && st.st_size > 0) {
            AMediaExtractor* ex = AMediaExtractor_new();
            if (AMediaExtractor_setDataSourceFd(ex, fd, 0, st.st_size) == AMEDIA_OK) {
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
                        }
                    }
                    if (f) AMediaFormat_delete(f);
                }
            }
            AMediaExtractor_delete(ex);
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
    VLOGI("NanoMenu: vidBuildTracks %s -> %zu audio, %zu subtitle tracks",
          file.c_str(), mVidAudTracks.size(), mVidSubTracks.size());
}

const std::vector<NanoMenu::VidCue>* NanoMenu::vidActiveSubCues() const {
    if (mVidSubCur < 0 || mVidSubCur >= (int)mVidSubTracks.size()) return nullptr;
    return &mVidSubTracks[mVidSubCur].cues;
}

// Switch the active audio track: re-open mVidAudio on that exact extractor track,
// reseeked to the picture and resumed if playing (web vidSetAudioTrack).
void NanoMenu::vidSetAudioTrack(int ordinal) {
    if (ordinal < 0 || ordinal >= (int)mVidAudTracks.size()) return;
    mVidAudCur = ordinal;
    if (mVidList.empty() || mVidIdx < 0 || mVidIdx >= (int)mVidList.size()) return;
    int vi = mVidList[mVidIdx];
    if (vi < 0 || vi >= (int)mVideos.size()) return;
    double pos = mVideoTest ? mVideoTest->position() : 0.0;
    mVidAudio.release();
    mVidHasAudio = mVidAudio.open(mVideos[vi].file, mVidAudTracks[ordinal].idx);
    mVidAudioStarted = false;
    if (mVidHasAudio) { mVidAudio.setVolume(mVidVolume); if (pos > 0.0) mVidAudio.seek(pos); }
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
    int t = (int)(s + 0.5);
    int h = t / 3600, m = (t % 3600) / 60, sec = t % 60;
    char b[24];
    if (h > 0) snprintf(b, sizeof(b), "%d:%02d:%02d", h, m, sec);
    else       snprintf(b, sizeof(b), "%d:%02d", m, sec);
    return b;
}

void NanoMenu::openVideoPlayer(const std::vector<Ps3Item>& list, int listSel) {
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
    if (mVideoTest) { mVideoTest->release(); delete mVideoTest; mVideoTest = nullptr; }
    mVideoTest = new NanoVideo();
    if (!mVideoTest->open(mVideos[vi].file)) { delete mVideoTest; mVideoTest = nullptr; return; }

    // Stop background music so the video owns the audio path (web 12350).
    if (mMusicPlayer.isPlaying()) mMusicPlayer.pause();

    // Enumerate audio + subtitle tracks (and external sidecars) for this title.
    vidBuildTracks(mVideos[vi].file);
    mVidAudCur = 0; mVidSubCur = -1;
    // Open the chosen audio track in a second HW audio engine; it is started by videoTick
    // once the first picture frame lands (avoids the decode-warmup desync).
    mVidHasAudio = false;
    if (!mVidAudTracks.empty()) {
        mVidHasAudio = mVidAudio.open(mVideos[vi].file, mVidAudTracks[0].idx);
        if (mVidHasAudio) mVidAudio.setVolume(mVidVolume); else mVidAudio.release();
    }
    mVidAudioStarted = false;

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
}

void NanoMenu::closeVideoPlayer() {
    // Begin the leave fade; the decoder is freed once mVidEnterT reaches 0 (videoTick),
    // so the last frame fades out instead of cutting to black.
    mVidActive = false;
}

// Immediate, idempotent full teardown of the video decoder + player state. Used where
// there is no time for the leave fade: sleep/power-press, occlusion by a foreground app,
// and process shutdown. Joins the worker, frees codec/extractor/surface/OES texture.
void NanoMenu::videoHardFree() {
    if (mVideoTest) { mVideoTest->release(); delete mVideoTest; mVideoTest = nullptr; }
    if (mVidHasAudio) { mVidAudio.release(); mVidHasAudio = false; }
    mVidActive = false; mVidPlaying = false;
    mVidEnterRaw = 0.0f; mVidEnterT = 0.0f;
    mVidCpOpen = mVidCpClosing = mVidSubOpen = mVidGoToOpen = false;
    mVidAudTracks.clear(); mVidSubTracks.clear(); mVidAudCur = 0; mVidSubCur = -1;
}

void NanoMenu::vidShowTransient(const std::string& text, float ms) {
    mVidTransient = text; mVidTransientUntil = mEffectTime + ms / 1000.0f;
}

void NanoMenu::vidTogglePlay() {
    if (!mVideoTest) return;
    // web vidPlayToggle: forces rate 1, un-stops (restart from 0 if stopped), flips playing.
    mVidRate = 1.0; mVidTransientUntil = 0.0f;
    if (mVidStopped) { mVidStopped = false; mVideoTest->seek(0.0); if (mVidHasAudio) mVidAudio.seek(0.0); }
    mVidPlaying = !mVidPlaying;
    if (mVidPlaying) mVideoTest->play(); else mVideoTest->pause();
    // audio play/pause is reconciled in videoTick (single source of truth)
    mVidHintUntil = mEffectTime + 1.5f;
}

void NanoMenu::vidSeek(double deltaSec) {
    if (!mVideoTest) return;
    mVidRate = 1.0;   // a manual seek cancels any scan (web vidSeek)
    double dur = mVideoTest->duration();
    double p = mVideoTest->position() + deltaSec;
    if (p < 0.0) p = 0.0;
    if (dur > 0.0 && p > dur - 0.05) p = dur - 0.05;   // keep inside the stream (no EOS trip)
    mVideoTest->seek(p);
    if (mVidHasAudio) mVidAudio.seek(p);
    mVidHintUntil = mEffectTime + 1.5f;
}

void NanoMenu::vidStepTitle(int dir) {
    if (mVidList.empty() || !mVideoTest) return;
    int n = (int)mVidList.size();
    mVidIdx = ((mVidIdx + dir) % n + n) % n;
    int vi = mVidList[mVidIdx];
    if (vi < 0 || vi >= (int)mVideos.size()) return;
    mVideoTest->release();
    if (!mVideoTest->open(mVideos[vi].file)) return;
    // rebuild tracks + re-open the audio for the new title (videoTick starts it on frame 1)
    vidBuildTracks(mVideos[vi].file);
    mVidAudCur = 0; mVidSubCur = -1;
    mVidAudio.release();
    mVidHasAudio = false;
    if (!mVidAudTracks.empty()) {
        mVidHasAudio = mVidAudio.open(mVideos[vi].file, mVidAudTracks[0].idx);
        if (mVidHasAudio) mVidAudio.setVolume(mVidVolume); else mVidAudio.release();
    }
    mVidAudioStarted = false;
    // web vidStepTitle resets rate / stopped / play state.
    mVidRate = 1.0; mVidStopped = false; mVidPlaying = true;
    mVidAbA = mVidAbB = -1.0;
    mVidHintUntil = mEffectTime + 1.5f;
}

// ---- transport extras (web vidStop/vidScan/vidSlow/vidStepFrame/vidFlash) -----
void NanoMenu::vidStop() {
    if (!mVideoTest) return;
    mVideoTest->pause();
    mVideoTest->seek(0.0);
    if (mVidHasAudio) { mVidAudio.pause(); mVidAudio.seek(0.0); }
    mVidPlaying = false; mVidStopped = true; mVidRate = 1.0;
    mVidTransientUntil = 0.0f;
    mVidHintUntil = mEffectTime + 1.5f;
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
    double dur = mVideoTest->duration();
    double p = mVideoTest->position() + (dir > 0 ? 1.0 : -1.0) / 30.0;
    if (p < 0.0) p = 0.0;
    if (dur > 0.0 && p > dur - 0.02) p = dur - 0.02;
    mVideoTest->seek(p);
    if (mVidHasAudio) { mVidAudio.pause(); mVidAudio.seek(p); }   // frame step keeps audio paused on the frame
    mVidHintUntil = mEffectTime + 1.5f;
}

void NanoMenu::vidFlash(int dir) {
    if (!mVideoTest) return;
    mVidRate = 1.0;
    double dur = mVideoTest->duration();
    double p = mVideoTest->position() + (dir > 0 ? 15.0 : -15.0);
    if (p < 0.0) p = 0.0;
    if (dur > 0.0 && p > dur - 0.05) p = dur - 0.05;
    mVideoTest->seek(p);
    if (mVidHasAudio) mVidAudio.seek(p);
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
    float dt = mFrameDt; if (dt < 0.0f || dt > 0.2f) dt = 0.016f;
    // 400ms smoothstep enter/leave (web vidEnterT). Eases toward 1 while active, 0 when
    // closing; the decoder is freed once fully faded out.
    float target = mVidActive ? 1.0f : 0.0f;
    float step = (dt * 1000.0f) / 400.0f;
    if (mVidEnterRaw < target) mVidEnterRaw = fminf(target, mVidEnterRaw + step);
    else if (mVidEnterRaw > target) mVidEnterRaw = fmaxf(target, mVidEnterRaw - step);
    mVidEnterT = mVidEnterRaw * mVidEnterRaw * (3.0f - 2.0f * mVidEnterRaw);
    if (!mVidActive && mVidEnterRaw <= 0.001f && mVideoTest) {
        mVideoTest->release(); delete mVideoTest; mVideoTest = nullptr;
        if (mVidHasAudio) { mVidAudio.release(); mVidHasAudio = false; }
        mVidCpOpen = mVidCpClosing = mVidSubOpen = mVidGoToOpen = false;
        mVidAudTracks.clear(); mVidSubTracks.clear(); mVidAudCur = 0; mVidSubCur = -1;
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
        double dur = mVideoTest->duration();
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
        bool wantAudio = mVidPlaying && mVidRate == 1.0 && !mVidStopped;
        double vp = mVideoTest->position();
        if (wantAudio && vp > 0.0) {
            if (!mVidAudioStarted) { mVidAudio.seek(vp); mVidAudio.play(); mVidAudioStarted = true; }
            else {
                if (!mVidAudio.isPlaying()) mVidAudio.play();
                double ap = mVidAudio.position();
                if (fabs(ap - vp) > 0.3) mVidAudio.seek(vp);
            }
        } else if (!wantAudio && mVidAudio.isPlaying()) {
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

    // A-B repeat: loop back to A once playback passes B.
    if (mVidRepeat == 3 && mVidAbA >= 0.0 && mVidAbB > mVidAbA
        && mVideoTest->position() >= mVidAbB) {
        mVideoTest->seek(mVidAbA);
        if (mVidHasAudio) mVidAudio.seek(mVidAbA);
    }

    // End of stream: repeat / auto-advance / stop (web vidOnEnded).
    if (mVidPlaying && mVideoTest->ended()) {
        if (mVidRepeat == 1 || mVidRepeat == 2) {          // Repeat On / Title Repeat
            mVideoTest->seek(0.0); mVideoTest->play();
            if (mVidHasAudio) { mVidAudio.seek(0.0); mVidAudio.play(); }
        } else if (mVidIdx < (int)mVidList.size() - 1) {    // auto-advance
            vidStepTitle(1);
        } else {
            mVidPlaying = false; mVidStopped = true; mVideoTest->pause();
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

    // Layer 2: buffering spinner (warmup / post-seek refill / stall) - icon 114 rotating at
    // centre + "Buffering..." below it (web drawVideoPlayer 12754-12756).
    if (mVidBuffering) {
        GLuint sp = vidIcon(114);
        if (sp) {
            float sz = H * 0.06f, ccx = W * 0.5f, ccy = H * 0.5f;
            float ang = fmodf(mEffectTime * (2.0f * 3.14159265f / 0.6f), 2.0f * 3.14159265f);
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

    double pos = mVideoTest->position(), dur = mVideoTest->duration();
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
        float kn = H * 0.012f;                                                // knob (square)
        drawQuad(bx + bw * frac - kn * 0.5f, by + bh * 0.5f - kn * 0.5f, kn, kn, 1.0f, 1.0f, 1.0f, 0.95f * barA);
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
        float padx = W * 0.018f, lh = H * 0.034f;
        float ph = lh * 2.0f + H * 0.018f, pw = tw + padx * 2.0f;
        float px = W - pw - W * 0.03f, py = H * 0.80f;
        drawQuad(px, py, pw, ph, 0.235f, 0.235f, 0.26f, 0.72f * hintA);
        drawText(l1, px + padx, ps3::baselineToTopY(py + lh * 0.9f, fs), fs, 1.0f, 1.0f, 1.0f, 0.95f * hintA);
        drawText(l2, px + padx, ps3::baselineToTopY(py + lh * 1.8f, fs), fs, 1.0f, 1.0f, 1.0f, 0.95f * hintA);
    }

    // Layer 9: the control panel (200ms open/close, web drawVideoPanel).
    if (mVidCpOpen) drawVideoPanel(-1.0f);
    else if (mVidCpClosing) {
        float p = fminf(1.0f, (mEffectTime - mVidCpCloseStart) / 0.2f);
        drawVideoPanel(1.0f - p);
        if (p >= 1.0f) mVidCpClosing = false;
    }

    // Layer 10: the Go To picker over everything.
    if (mVidGoToOpen) drawVideoGoTo();
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
    {"avset",      "AV Settings",         23,  6, 0},
    {"screenmode", "Screen Mode",          1,  7, 0},
    {"chgicon",    "Change Icon",         24,  8, 0},
    {"del",        "Delete",               4,  9, 0},
    {"showinfo",   "Display",              0, 10, 0},
    {"beginning",  "Return to Beginning",  9,  0, 1},
    {"next",       "Next",                10,  1, 1},
    {"frev",       "Fast Reverse",        11,  2, 1},
    {"ffwd",       "Fast Forward",        12,  3, 1},
    {"play",       "Play",                 6,  4, 1},
    {"pause",      "Pause",                8,  5, 1},
    {"stop",       "Stop",                 7,  6, 1},
    {"flashr",     "Instant Replay",      15,  7, 1},
    {"flashf",     "Instant Advance",     16,  8, 1},
    {"srev",       "Slow  (Reverse)",     20,  9, 1},
    {"sfwd",       "Slow  (Forward)",     13, 10, 1},
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
static const char* kVidAvSet[]       = {"Block Noise Reduction", "Frame Noise Reduction",
                                        "Mosquito Noise Reduction", "Upscale"};

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
        case 2: mVidSubLabel = "Volume Control"; {
            static const char* v[] = {"100%", "80%", "60%", "40%", "20%", "0%"};
            for (const char* s : v) mVidSubOpts.push_back(s);
            int sel = (int)((1.0f - mVidVolume) / 0.2f + 0.5f);
            if (sel < 0) sel = 0; if (sel > 5) sel = 5; mVidSubSel = sel; } break;
        case 3: mVidSubLabel = "AV Settings"; {
            bool keys[4] = {mVidAvBnr, mVidAvFnr, mVidAvMnr, mVidAvUpscale};
            for (int i = 0; i < 4; i++) {
                std::string s = kVidAvSet[i]; s += "   "; s += keys[i] ? "Automatic" : "Off";
                mVidSubOpts.push_back(s);
            } } break;
        case 4: mVidSubLabel = "Audio Options";   // one row per audio track
            for (const auto& a : mVidAudTracks) mVidSubOpts.push_back(a.name);
            mVidSubSel = (mVidAudCur >= 0 && mVidAudCur < (int)mVidAudTracks.size()) ? mVidAudCur : 0; break;
        case 5: mVidSubLabel = "Subtitle Options";   // Off + each subtitle track
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
                                     mVidDispMode = "A-B Repeat: Point A set"; mVidDispModeUntil = mEffectTime + 1.8f; }
                else if (mVidAbB < 0.0 && p > mVidAbA + 0.5) { mVidAbB = p;
                                     mVidDispMode = "A-B Repeat: Point B set"; mVidDispModeUntil = mEffectTime + 1.8f; }
                else if (mVidAbB >= 0.0) { mVidAbA = mVidAbB = -1.0; mVidRepeat = 0;
                                     mVidDispMode = "A-B Repeat Off"; mVidDispModeUntil = mEffectTime + 1.8f; }
            } else { mVidRepeat = sel; mVidAbA = mVidAbB = -1.0; }
            mVidSubOpen = false; break;
        case 2:   // volume
            mVidVolume = 1.0f - sel * 0.2f;
            if (mVidHasAudio) mVidAudio.setVolume(mVidVolume);
            mVidSubOpen = false; break;
        case 3: { // AV settings: toggle the key, flash the pill, keep the submenu open
            bool* keys[4] = {&mVidAvBnr, &mVidAvFnr, &mVidAvMnr, &mVidAvUpscale};
            *keys[sel] = !*keys[sel];
            mVidDispMode = std::string(kVidAvSet[sel]) + ": " + (*keys[sel] ? "Automatic" : "Off");
            mVidDispModeUntil = mEffectTime + 1.8f;
            vidSubBuild(3); mVidSubSel = sel; } break;
        case 4:   // audio track
            vidSetAudioTrack(sel); mVidSubOpen = false; break;
        case 5: { // subtitle track (row 0 = Off)
            mVidSubCur = sel - 1;
            if (mVidSubCur >= 0 && mVidSubCur < (int)mVidSubTracks.size()) {
                const VidSubTrk& t = mVidSubTracks[mVidSubCur];
                mVidDispMode = std::string("Subtitle: ") + t.name + (t.external ? " (External)" : "");
            } else { mVidSubCur = -1; mVidDispMode = "Subtitle: Off"; }
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
    else if (!strcmp(a, "showinfo"))   { mVidOsd = !mVidOsd; vidPanelClose(); }
    else if (!strcmp(a, "screenmode")) { vidSubBuild(0); mVidSubOpen = true; }
    else if (!strcmp(a, "repeat"))     { vidSubBuild(1); mVidSubOpen = true; }
    else if (!strcmp(a, "volume"))     { vidSubBuild(2); mVidSubOpen = true; }
    else if (!strcmp(a, "avset"))      { vidSubBuild(3); mVidSubOpen = true; }
    else if (!strcmp(a, "goto"))       vidGoToOpen();
    else if (!strcmp(a, "scene"))      vidShowTransient("No chapters", 1400.0f);
    else if (!strcmp(a, "audio")) {
        if (mVidAudTracks.empty()) { vidPanelClose(); vidShowTransient("There is no audio.", 1600.0f); }
        else { vidSubBuild(4); mVidSubOpen = true; }
    }
    else if (!strcmp(a, "subtitle")) {
        if (mVidSubTracks.empty()) { vidPanelClose(); vidShowTransient("There are no subtitle options available.", 1700.0f); }
        else { vidSubBuild(5); mVidSubOpen = true; }
    }
    else if (!strcmp(a, "del")) { vidPanelClose(); vidShowTransient("Delete completed.", 1400.0f); }
    else if (!strcmp(a, "chgicon")) {
        double rem = mVideoTest ? (mVideoTest->duration() - mVideoTest->position()) : 0.0;
        vidPanelClose();
        if (rem < 15.0) vidShowTransient("You cannot create an icon less than 15 seconds in length.", 1800.0f);
        else vidShowTransient("The icon has been changed.", 1600.0f);
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
    double dur = mVideoTest ? mVideoTest->duration() : 0.0;
    if (dur > 0.0 && target > dur) {
        vidShowTransient("The range you can specify has been exceeded.", 1600.0f);
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

}  // namespace android
