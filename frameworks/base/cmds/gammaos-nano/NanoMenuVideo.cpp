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
#include <string.h>
#include <sys/stat.h>
#include <thread>
#include <unistd.h>

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
    VLOGI("NanoMenu: loaded video library (%zu folders, %zu videos)",
          mVideoFolders.size(), mVideos.size());
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

    // Stop background music so the video owns the audio path (web 12350); video audio
    // output itself is added next - the picture plays now.
    if (mMusicPlayer.isPlaying()) mMusicPlayer.pause();

    mVidActive = true;
    mVidPlaying = true;
    mVidScreenMode = 0;
    mVidOsd = false;
    mVidHintUntil = mEffectTime + 4.0f;     // show the OSD bar for 4s on open
    mVidTransientUntil = 0.0f; mVidDispModeUntil = 0.0f;
}

void NanoMenu::closeVideoPlayer() {
    // Begin the leave fade; the decoder is freed once mVidEnterT reaches 0 (videoTick),
    // so the last frame fades out instead of cutting to black.
    mVidActive = false;
}

void NanoMenu::vidShowTransient(const std::string& text, float ms) {
    mVidTransient = text; mVidTransientUntil = mEffectTime + ms / 1000.0f;
}

void NanoMenu::vidTogglePlay() {
    if (!mVideoTest) return;
    mVidPlaying = !mVidPlaying;
    if (mVidPlaying) mVideoTest->play(); else mVideoTest->pause();
    mVidHintUntil = mEffectTime + 1.5f;
}

void NanoMenu::vidSeek(double deltaSec) {
    if (!mVideoTest) return;
    double p = mVideoTest->position() + deltaSec;
    mVideoTest->seek(p);
    mVidHintUntil = mEffectTime + 1.5f;
}

void NanoMenu::vidStepTitle(int dir) {
    if (mVidList.empty() || !mVideoTest) return;
    int n = (int)mVidList.size();
    mVidIdx = (mVidIdx + dir % n + n) % n;
    int vi = mVidList[mVidIdx];
    if (vi < 0 || vi >= (int)mVideos.size()) return;
    mVideoTest->release();
    if (!mVideoTest->open(mVideos[vi].file)) return;
    mVidPlaying = true;
    mVidHintUntil = mEffectTime + 1.5f;
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
    }
    // End of stream: auto-advance to the next video, or stop on the last one.
    if (mVidActive && mVideoTest && mVidPlaying && mVideoTest->ended()) {
        if (mVidIdx < (int)mVidList.size() - 1) vidStepTitle(1);
        else { mVidPlaying = false; mVideoTest->pause(); }
    }
}

bool NanoMenu::renderVideoPlayer() {
    videoTick();
    if (mVidEnterT <= 0.001f && !mVidActive) return false;
    if (!mVideoTest) { if (mVidEnterT <= 0.001f) return false; }

    int W = mWidth, H = mHeight;
    float et = mVidEnterT;
    drawQuad(0, 0, (float)W, (float)H, 0.0f, 0.0f, 0.0f, 1.0f);   // black backdrop

    // Layer 1: the video frame (Normal = aspect-fit; other screen modes come with the
    // control panel). updateFrame latches the newest decoded frame on the render thread.
    if (mVideoTest) {
        mVideoTest->updateFrame();
        int fit = (mVidScreenMode == 1 || mVidScreenMode == 3) ? 1 : 0;   // FullScreen/Zoom = fill
        mVideoTest->draw(W, H, 0.0f, 0.0f, (float)W, (float)H, et, fit);
    }
    if (!mVideoTest) return et > 0.001f;   // exit fade: black only

    double pos = mVideoTest->position(), dur = mVideoTest->duration();
    float bx = W * 0.10f, bw = W * 0.80f, by = H * 0.90f, bh = H * 0.006f;

    // Layer 3: title (top-left) + the seek bar + times. The bar auto-hides via
    // mVidHintUntil unless the Display OSD toggle keeps it on.
    float hintA = fminf(1.0f, fmaxf(0.0f, (mVidHintUntil - mEffectTime)) / 0.6f) * et;
    float barA = mVidOsd ? et : hintA;
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

    // Layer 8: help-hint pill (bottom-right) while the OSD bar is showing.
    if (hintA > 0.01f) {
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
    return true;
}

}  // namespace android
