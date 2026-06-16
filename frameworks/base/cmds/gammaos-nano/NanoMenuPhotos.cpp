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

// NanoMenu photo glue: the nano_photo.json library model + persist + scan, the
// "Search for Media Servers" folder import (reusing the Game Systems folder
// picker, target = 2), the Photo-column groups (By Month / Year / Album / All),
// the in-album thumbnail grid, the full-screen photo viewer and its control
// panel. The control panel mirrors the Music player's MP_CP look (icon spacing,
// focus enlarge, breathing halo, drop shadow, SELECT pill) so the in-photo menu
// matches the music player exactly. 1:1 source of truth:
// /work/ps3/xmb-app/index.html (Photo DATA category, photoViewer, drawPhotoGrid,
// PV_CP / drawPvPanel, drawPvInfo). Big photos are decoded scaled-on-decode via
// AImageDecoder (libjnigraphics) so the low-RAM panel handles 12-16MP shots.

#define LOG_TAG "GammaOSNano"

#include "NanoMenu.h"
#include "NanoMenuPS3.h"
#include "NanoMenuDrm.h"   // sDrmGlRotation / sDrmRotationDeg for the wallpaper crop scissor
#include "NanoJson.h"

#include <android/imagedecoder.h>
#include <android/bitmap.h>
#include <GLES2/gl2.h>

#include <dirent.h>
#include <fcntl.h>
#include <cerrno>
#include <unistd.h>
#include <cutils/properties.h>
#include <sys/stat.h>
#include <string.h>
#include <strings.h>
#include <algorithm>
#include <cstdio>
#include <cmath>
#include <cstdlib>
#include <ctime>
#include <map>
#include <set>
#include <thread>
#include <utils/Log.h>

namespace android {

// ---- layout helpers (mirror NanoMenuMusic.cpp; web V.W/V.H == ps3::VW/VH) ----
static inline float PXP(float f) { return ps3::devX(ps3::XCF(ps3::VW * f)); }   // abs X (web XCF)
static inline float PXD(float f) { return ps3::devS(ps3::XCF(ps3::VW * f)); }   // X delta (web XCF)
static inline float PYP(float f) { return ps3::devY(ps3::VH * f); }            // abs Y
static inline float PSZ(float f) { return ps3::devS(ps3::VH * f); }            // size from VH frac
static inline float PFS(float px){ return ps3::fontScale(px); }                // font scale for NNpx
// Photo UI scale: double the control-panel grid on small panels (<=768) for
// readability, exactly like the music control panel (mpUiScale).
static inline float pvUiScale(int w, int h) { return ((w < h ? w : h) <= 768) ? 2.0f : 1.0f; }

// Image extensions the scanner accepts (AImageDecoder handles all of these).
static bool isPhotoExt(const std::string& nameLower) {
    static const char* kExts[] = { ".jpg", ".jpeg", ".png", ".webp", ".bmp",
                                   ".gif", ".heic", ".heif" };
    size_t dot = nameLower.rfind('.');
    if (dot == std::string::npos) return false;
    std::string ext = nameLower.substr(dot);
    for (const char* e : kExts) if (ext == e) return true;
    return false;
}

static std::string photoBaseName(const std::string& path) {
    size_t s = path.rfind('/');
    return s == std::string::npos ? path : path.substr(s + 1);
}
static std::string photoStripExt(const std::string& name) {
    size_t d = name.rfind('.');
    return d == std::string::npos ? name : name.substr(0, d);
}

// ---------------------------------------------------------------------------
// Image decode (AImageDecoder, scaled-on-decode). Returns a GL texture and its
// decoded dimensions; 0 on any failure.
// ---------------------------------------------------------------------------
bool NanoMenu::photoProbeDims(const std::string& path, int* w, int* h, int64_t* sz) {
    if (sz) {
        struct stat st;
        *sz = (stat(path.c_str(), &st) == 0) ? (int64_t)st.st_size : 0;
    }
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) return false;
    AImageDecoder* dec = nullptr;
    int r = AImageDecoder_createFromFd(fd, &dec);
    if (r != ANDROID_IMAGE_DECODER_SUCCESS || !dec) { close(fd); return false; }
    const AImageDecoderHeaderInfo* hi = AImageDecoder_getHeaderInfo(dec);
    int sw = AImageDecoderHeaderInfo_getWidth(hi);
    int sh = AImageDecoderHeaderInfo_getHeight(hi);
    AImageDecoder_delete(dec);
    close(fd);
    if (sw <= 0 || sh <= 0) return false;
    if (w) *w = sw; if (h) *h = sh;
    return true;
}

GLuint NanoMenu::photoDecodeTex(const std::string& path, int maxDim, int* outW, int* outH) {
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) return 0;
    AImageDecoder* dec = nullptr;
    int r = AImageDecoder_createFromFd(fd, &dec);
    if (r != ANDROID_IMAGE_DECODER_SUCCESS || !dec) { close(fd); return 0; }
    const AImageDecoderHeaderInfo* hi = AImageDecoder_getHeaderInfo(dec);
    int sw = AImageDecoderHeaderInfo_getWidth(hi);
    int sh = AImageDecoderHeaderInfo_getHeight(hi);
    if (sw <= 0 || sh <= 0) { AImageDecoder_delete(dec); close(fd); return 0; }
    AImageDecoder_setAndroidBitmapFormat(dec, ANDROID_BITMAP_FORMAT_RGBA_8888);
    AImageDecoder_setUnpremultipliedRequired(dec, true);   // photos are opaque; keep colours linear
    int tw = sw, th = sh;
    int longSide = sw > sh ? sw : sh;
    if (maxDim > 0 && longSide > maxDim) {
        float s = (float)maxDim / (float)longSide;
        tw = (int)(sw * s + 0.5f); th = (int)(sh * s + 0.5f);
        if (tw < 1) tw = 1; if (th < 1) th = 1;
        AImageDecoder_setTargetSize(dec, tw, th);
    }
    size_t stride = AImageDecoder_getMinimumStride(dec);
    size_t bufSize = stride * (size_t)th;
    std::vector<uint8_t> buf(bufSize);
    r = AImageDecoder_decodeImage(dec, buf.data(), stride, bufSize);
    AImageDecoder_delete(dec);
    close(fd);
    if (r != ANDROID_IMAGE_DECODER_SUCCESS) return 0;
    const uint8_t* px = buf.data();
    std::vector<uint8_t> packed;
    if (stride != (size_t)tw * 4) {   // pack rows tightly for glTexImage2D
        packed.resize((size_t)tw * th * 4);
        for (int y = 0; y < th; y++)
            memcpy(&packed[(size_t)y * tw * 4], &buf[(size_t)y * stride], (size_t)tw * 4);
        px = packed.data();
    }
    GLuint tex = 0;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, tw, th, 0, GL_RGBA, GL_UNSIGNED_BYTE, px);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    if (outW) *outW = tw; if (outH) *outH = th;
    return tex;
}

// ---------------------------------------------------------------------------
// Config persistence (nano_photo.json, atomic write + mtime reload).
// ---------------------------------------------------------------------------
int64_t NanoMenu::photoConfigStamp() const {
    struct stat st;
    if (stat("/data/system/nano_photo.json", &st) != 0) return -1;
    return (int64_t)st.st_mtim.tv_sec * 1000000000LL + st.st_mtim.tv_nsec;
}

bool NanoMenu::loadPhotoConfig() {
    const char* path = "/data/system/nano_photo.json";
    int fd = open(path, O_RDONLY);
    if (fd < 0) { mPhotoCfgStamp = -1; return false; }
    std::string content;
    char b[4096]; ssize_t n;
    while ((n = read(fd, b, sizeof(b))) > 0) content.append(b, n);
    close(fd);
    mPhotoCfgStamp = photoConfigStamp();
    njson::Value root;
    if (!njson::parse(content, &root) || !root.isObject()) return false;
    mPhotoFolders.clear();
    mPhotos.clear();
    mPhotoPlaylists.clear();
    mPhotoCfgVersion = root.find("version") ? (int)root.find("version")->asNumber(0) : 0;
    if (const njson::Value* folders = root.find("folders"); folders && folders->isArray())
        for (const auto& f : folders->arr) if (f.isString()) mPhotoFolders.push_back(f.str);
    if (const njson::Value* photos = root.find("photos"); photos && photos->isArray())
        for (const auto& p : photos->arr) {
            if (!p.isObject()) continue;
            PhotoItem it;
            it.file = p.getString("file");
            it.name = p.getString("n");
            it.date = p.getString("date");
            it.w = p.getInt("w", 0);
            it.h = p.getInt("h", 0);
            it.sz = p.find("sz") ? (int64_t)p.find("sz")->asNumber(0) : 0;
            it.mtime = p.find("mtime") ? (int64_t)p.find("mtime")->asNumber(0) : 0;
            mPhotos.push_back(std::move(it));
        }
    if (const njson::Value* pls = root.find("playlists"); pls && pls->isArray())
        for (const auto& p : pls->arr) {
            if (!p.isObject()) continue;
            PhotoPlaylist pl;
            pl.name = p.getString("name");
            if (const njson::Value* fs = p.find("files"); fs && fs->isArray())
                for (const auto& f : fs->arr) if (f.isString()) pl.files.push_back(f.str);
            mPhotoPlaylists.push_back(std::move(pl));
        }
    ALOGI("NanoMenu: loaded photo library (%zu folders, %zu photos, %zu playlists)",
          mPhotoFolders.size(), mPhotos.size(), mPhotoPlaylists.size());
    return true;
}

void NanoMenu::savePhotoConfig() {
    njson::Value root = njson::Value::makeObject();
    root.set("version") = njson::Value::makeNumber(kPhotoMetaVersion);
    njson::Value folders = njson::Value::makeArray();
    for (const auto& f : mPhotoFolders) folders.arr.push_back(njson::Value::makeString(f));
    root.set("folders") = std::move(folders);
    njson::Value photos = njson::Value::makeArray();
    for (const auto& it : mPhotos) {
        njson::Value v = njson::Value::makeObject();
        v.set("file") = njson::Value::makeString(it.file);
        v.set("n") = njson::Value::makeString(it.name);
        v.set("date") = njson::Value::makeString(it.date);
        v.set("w") = njson::Value::makeNumber(it.w);
        v.set("h") = njson::Value::makeNumber(it.h);
        v.set("sz") = njson::Value::makeNumber((double)it.sz);
        v.set("mtime") = njson::Value::makeNumber((double)it.mtime);
        photos.arr.push_back(std::move(v));
    }
    root.set("photos") = std::move(photos);
    njson::Value pls = njson::Value::makeArray();
    for (const auto& p : mPhotoPlaylists) {
        njson::Value v = njson::Value::makeObject();
        v.set("name") = njson::Value::makeString(p.name);
        njson::Value fs = njson::Value::makeArray();
        for (const auto& f : p.files) fs.arr.push_back(njson::Value::makeString(f));
        v.set("files") = std::move(fs);
        pls.arr.push_back(std::move(v));
    }
    root.set("playlists") = std::move(pls);
    std::string text = njson::serialize(root, true);
    const char* path = "/data/system/nano_photo.json";
    const char* tmp = "/data/system/nano_photo.json.tmp";
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
    mPhotoCfgVersion = kPhotoMetaVersion;
    mPhotoCfgStamp = photoConfigStamp();
    ALOGD("NanoMenu: wrote nano_photo.json (%zu photos)", mPhotos.size());
}

// ---------------------------------------------------------------------------
// Lazy load + storage gate + refresh (mirror musicEnsureLoaded / musicOnCatFocus).
// ---------------------------------------------------------------------------
bool NanoMenu::photoStorageReady() const {
    for (const auto& f : mPhotoFolders) {
        struct stat st;
        if (stat(f.c_str(), &st) == 0 && S_ISDIR(st.st_mode)) return true;
    }
    return mPhotoFolders.empty();
}

void NanoMenu::photoOnCatFocus() {
    if (mPhotoLoaded) return;
    if (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()
        && mPs3Cats[mPs3CatIdx].name == "Photo")
        photoEnsureLoaded();
}

void NanoMenu::photoEnsureLoaded() {
    if (mPhotoLoaded) return;
    mPhotoLoaded = true;
    loadPhotoConfig();
    mPhotoCatsStale = true;
    if (!mPhotoFolders.empty() && !mPhotoScanRunning) photoScanAsync();
}

void NanoMenu::photoRefresh() {
    photoEnsureLoaded();
    if (!mPhotoScanRunning) photoScanAsync();
}

// ---------------------------------------------------------------------------
// Scanner: recurse imported folders, collect images, probe dims (mtime-cached).
// ---------------------------------------------------------------------------
static void scanPhotosRecursive(const std::string& dir,
                                std::vector<std::string>& outFiles, int depth) {
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
        if (isPhotoExt(lower)) outFiles.push_back(child);
    }
    closedir(d);
    std::sort(subdirs.begin(), subdirs.end(),
              [](const std::string& a, const std::string& b){ return strcasecmp(a.c_str(), b.c_str()) < 0; });
    for (const auto& s : subdirs) scanPhotosRecursive(s, outFiles, depth + 1);
}

static std::string photoDateFromMtime(int64_t mtimeNs) {
    time_t t = (time_t)(mtimeNs / 1000000000LL);
    struct tm tmv;
    if (!localtime_r(&t, &tmv)) return "";
    char b[24];
    snprintf(b, sizeof(b), "%04d-%02d-%02d %02d:%02d",
             tmv.tm_year + 1900, tmv.tm_mon + 1, tmv.tm_mday, tmv.tm_hour, tmv.tm_min);
    return b;
}

void NanoMenu::photoScanAsync() {
    if (mPhotoScanRunning) return;
    if (!photoStorageReady()) { mPhotoScanPending = true; return; }
    mPhotoScanPending = false;
    mPhotoScanRunning = true;
    std::thread([this]() { photoScanThreadFunc(); }).detach();
}

void NanoMenu::photoScanThreadFunc() {
    std::vector<std::string> folders = mPhotoFolders;
    bool forceReprobe = (mPhotoCfgVersion < kPhotoMetaVersion);
    std::vector<PhotoItem> cacheVec = mPhotos;
    std::map<std::string, const PhotoItem*> cache;
    if (!forceReprobe) for (const auto& it : cacheVec) cache[it.file] = &it;

    std::vector<std::string> files;
    for (const auto& f : folders) scanPhotosRecursive(f, files, 0);
    std::sort(files.begin(), files.end());
    files.erase(std::unique(files.begin(), files.end()), files.end());

    std::vector<PhotoItem> results;
    results.reserve(files.size());
    for (const auto& path : files) {
        struct stat st;
        int64_t mt = (stat(path.c_str(), &st) == 0)
                         ? (int64_t)st.st_mtim.tv_sec * 1000000000LL + st.st_mtim.tv_nsec : 0;
        auto it = cache.find(path);
        if (it != cache.end() && it->second->mtime == mt && mt != 0) {
            results.push_back(*it->second);     // unchanged: reuse cached dims/date
            continue;
        }
        PhotoItem p;
        p.file = path;
        p.mtime = mt;
        p.name = photoStripExt(photoBaseName(path));
        p.date = photoDateFromMtime(mt);
        int w = 0, h = 0; int64_t sz = 0;
        if (photoProbeDims(path, &w, &h, &sz)) { p.w = w; p.h = h; p.sz = sz; }
        else { struct stat s2; p.sz = (stat(path.c_str(), &s2) == 0) ? (int64_t)s2.st_size : 0; }
        results.push_back(std::move(p));
    }
    {
        std::lock_guard<std::mutex> lk(mPhotoScanMutex);
        mPhotoScanResults = std::move(results);
        mPhotoScanReady = true;
    }
    mPhotoScanRunning = false;
    ALOGI("NanoMenu: photo scan finished (%zu images)", files.size());
}

void NanoMenu::photoDrainScanResults() {
    if (!mPhotoScanReady) return;
    {
        std::lock_guard<std::mutex> lk(mPhotoScanMutex);
        mPhotos = std::move(mPhotoScanResults);
        mPhotoScanResults.clear();
        mPhotoScanReady = false;
    }
    savePhotoConfig();
    mPhotoCatsStale = true;
}

// ---------------------------------------------------------------------------
// Folder import (Search for Media Servers) - reuses the Game Systems folder
// picker by setting mFolderPickTarget = 2.
// ---------------------------------------------------------------------------
void NanoMenu::photoOpenFolders() {
    photoEnsureLoaded();
    mFolderPickTarget = 2;
    std::vector<Ps3Item> parentSnap = mPs3Stack.empty() ? std::vector<Ps3Item>() : mPs3Stack.back().items;
    int parentSel = mPs3Stack.empty() ? 0 : mPs3Stack.back().sel;
    Ps3Level lvl; buildPhotoFoldersScreen(lvl);
    mPs3Stack.push_back(lvl);
    mPs3SubParentItems = std::move(parentSnap);
    mPs3SubParentIdx = parentSel; mPs3SubChildItems = mPs3Stack.back().items;
    mPs3SubDir = 1; mPs3SubAnimStart = mEffectTime; mPs3SubAnim = 0.0f;
    mPs3AnimItem = 0.0f; mPs3ItemAnimStart = -1.0f;
}

void NanoMenu::buildPhotoFoldersScreen(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.title = "Photo Folders"; out.screenKind = PHOTO_FOLDER;
    { Ps3Item it; it.label = "Add Folder..."; it.kind = PS3_GS_ADDFOLDER;
      it.iconTex = 0; it.nmapTex = nmapForIcon(50); it.iconR = it.iconG = it.iconB = 1.0f;
      out.items.push_back(it); }
    { Ps3Item it; it.label = mPhotoScanRunning ? "Refreshing..." : "Refresh";
      it.kind = PS3_PHOTO_REFRESH;
      it.value = mPhotoScanRunning ? "" : "Rescan photo folders";
      it.iconTex = 0; it.nmapTex = nmapForIcon(8); it.iconR = it.iconG = it.iconB = 1.0f;
      out.items.push_back(it); }
    for (size_t i = 0; i < mPhotoFolders.size(); i++) {
        int cnt = 0;
        for (const auto& p : mPhotos)
            if (p.file.compare(0, mPhotoFolders[i].size(), mPhotoFolders[i]) == 0) cnt++;
        Ps3Item it; it.label = mPhotoFolders[i]; it.kind = PS3_PHOTO_FOLDER_ROW; it.a = (int)i;
        char v[24]; snprintf(v, sizeof(v), "%d images", cnt); it.value = v;
        it.iconTex = 0; it.nmapTex = nmapForIcon(62); it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
    if (mPhotoFolders.empty()) {
        Ps3Item it; it.label = mPhotoScanRunning ? "Scanning..." : "No photo folders added yet";
        it.kind = PS3_GS_FIELD; it.a = -1;
        it.iconTex = 0; it.nmapTex = 0; it.iconR = it.iconG = it.iconB = 0.55f;
        out.items.push_back(it);
    }
}

void NanoMenu::photoFolderSelect(const std::string& path) {
    photoEnsureLoaded();
    if (path.empty()) return;
    if (std::find(mPhotoFolders.begin(), mPhotoFolders.end(), path) == mPhotoFolders.end())
        mPhotoFolders.push_back(path);
    savePhotoConfig();
    // pop the folder browser back to the photo folders screen, then rebuild it
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_FOLDERBROWSE) mPs3Stack.pop_back();
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == PHOTO_FOLDER)
        buildPhotoFoldersScreen(mPs3Stack.back());
    photoScanAsync();
}

void NanoMenu::photoRemoveFolder(int idx) {
    if (idx < 0 || idx >= (int)mPhotoFolders.size()) return;
    mPhotoFolders.erase(mPhotoFolders.begin() + idx);
    savePhotoConfig();
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == PHOTO_FOLDER)
        buildPhotoFoldersScreen(mPs3Stack.back());
    photoScanAsync();
}

// ---------------------------------------------------------------------------
// Grouping + column content (1:1 web regroupContent photo branch).
// ---------------------------------------------------------------------------
std::string NanoMenu::fmtPhotoDate(const std::string& iso) {
    // "YYYY-MM-DD HH:MM" -> "D/M/YYYY H:MM" (day/month/hour no leading zero)
    if (iso.size() < 10) return iso;
    int y = atoi(iso.substr(0, 4).c_str());
    int mo = atoi(iso.substr(5, 2).c_str());
    int da = atoi(iso.substr(8, 2).c_str());
    char b[40];
    if (iso.size() >= 16) {
        int hh = atoi(iso.substr(11, 2).c_str());
        std::string mm = iso.substr(14, 2);
        snprintf(b, sizeof(b), "%d/%d/%d  %d:%s", da, mo, y, hh, mm.c_str());
    } else {
        snprintf(b, sizeof(b), "%d/%d/%d", da, mo, y);
    }
    return b;
}

std::string NanoMenu::fmtFileSize(int64_t b) {
    char buf[24];
    if (b >= 1024 * 1024) snprintf(buf, sizeof(buf), "%lld MB", (long long)(b / (1024 * 1024)));
    else if (b >= 1024)   snprintf(buf, sizeof(buf), "%lld KB", (long long)(b / 1024));
    else                  snprintf(buf, sizeof(buf), "%lld B", (long long)b);
    return buf;
}

std::vector<NanoMenu::PhotoGroup> NanoMenu::photoGroups() const {
    std::vector<int> order(mPhotos.size());
    for (size_t i = 0; i < mPhotos.size(); i++) order[i] = (int)i;
    // Sort by date ascending, then by name (matches the web grp_all default order).
    std::sort(order.begin(), order.end(), [&](int a, int b) {
        const std::string& x = mPhotos[a].date; const std::string& y = mPhotos[b].date;
        if (x != y) return x < y;
        return strcasecmp(mPhotos[a].name.c_str(), mPhotos[b].name.c_str()) < 0;
    });
    static const char* MON[12] = {"Jan","Feb","Mar","Apr","May","Jun",
                                  "Jul","Aug","Sep","Oct","Nov","Dec"};
    std::vector<PhotoGroup> out;
    if (order.empty()) return out;
    int mode = mPhotoGroupIdx;
    if (mode == 3) { PhotoGroup g; g.name = "All Photos"; g.idx = order; out.push_back(g); return out; }
    if (mode == 2) { PhotoGroup g; g.name = "Unknown"; g.idx = order; out.push_back(g); return out; }
    // By Month / By Year: bucket by the date prefix (keys stay ascending by encounter).
    std::map<std::string, std::vector<int>> m;
    std::vector<std::string> keys;
    for (int i : order) {
        const std::string& d = mPhotos[i].date;
        std::string k;
        if (d.size() >= (mode == 0 ? 7u : 4u)) k = d.substr(0, mode == 0 ? 7 : 4);
        else k = "Unknown";
        if (m.find(k) == m.end()) keys.push_back(k);
        m[k].push_back(i);
    }
    for (const auto& k : keys) {
        PhotoGroup g;
        if (k == "Unknown") g.name = "Unknown";
        else if (mode == 0) {
            int mm = atoi(k.substr(5, 2).c_str());
            if (mm < 1) mm = 1; if (mm > 12) mm = 12;
            g.name = std::string(MON[mm - 1]) + " " + k.substr(0, 4);
        } else g.name = k;
        g.idx = m[k];
        out.push_back(g);
    }
    return out;
}

void NanoMenu::buildPhotoColumnItems(std::vector<Ps3Item>& out) {
    GLuint folderNmap = nmapForIcon(62);
    std::vector<PhotoGroup> groups = photoGroups();
    for (size_t a = 0; a < groups.size(); a++) {
        Ps3Item it; it.label = groups[a].name; it.kind = PS3_PHOTO_ALBUM; it.a = (int)a;
        size_t n = groups[a].idx.size();
        char v[32]; snprintf(v, sizeof(v), "%zu %s", n, n == 1 ? "Image" : "Images"); it.value = v;
        it.iconTex = 0; it.nmapTex = folderNmap; it.iconR = it.iconG = it.iconB = 1.0f;
        out.push_back(it);
    }
}

void NanoMenu::photoCycleGroup() {
    static const char* kModeNames[4] = {"By Month", "By Year", "By Album", "All"};
    mPhotoGroupIdx = (mPhotoGroupIdx + 1) % 4;
    mPhotoCatsStale = true;
    // brief banner reusing the music banner overlay would need mMpActive; instead
    // just rebuild the column - the new groups appear immediately.
    ALOGI("NanoMenu: photo group -> %s", kModeNames[mPhotoGroupIdx]);
}

// ---------------------------------------------------------------------------
// Thumbnail grid (screenKind PHOTO_GRID, modeled on renderIconGridPicker).
// ---------------------------------------------------------------------------
static const int PG_COLS = 5;

GLuint NanoMenu::photoThumb(int photoIdx) {
    auto it = mPhotoThumbCache.find(photoIdx);
    if (it != mPhotoThumbCache.end()) return it->second;
    if (photoIdx < 0 || photoIdx >= (int)mPhotos.size()) return 0;
    int w = 0, h = 0;
    GLuint tex = photoDecodeTex(mPhotos[photoIdx].file, 256, &w, &h);
    mPhotoThumbCache[photoIdx] = tex;
    mPhotoThumbAR[photoIdx] = (tex && h > 0) ? (float)w / (float)h : 1.0f;
    return tex;
}

void NanoMenu::photoThumbEvict() {
    // Bound the thumb cache to ~64 entries, dropping those furthest from the cursor.
    const size_t kCap = 64;
    if (mPhotoThumbCache.size() <= kCap) return;
    int center = (mPhotoGridCursor >= 0 && mPhotoGridCursor < (int)mPhotoGridList.size())
                     ? mPhotoGridList[mPhotoGridCursor] : 0;
    while (mPhotoThumbCache.size() > kCap) {
        auto worst = mPhotoThumbCache.end(); int worstD = -1;
        for (auto i = mPhotoThumbCache.begin(); i != mPhotoThumbCache.end(); ++i) {
            int d = abs(i->first - center);
            if (d > worstD) { worstD = d; worst = i; }
        }
        if (worst == mPhotoThumbCache.end()) break;
        if (worst->second) glDeleteTextures(1, &worst->second);
        mPhotoThumbAR.erase(worst->first);
        mPhotoThumbCache.erase(worst);
    }
}

void NanoMenu::photoFreeThumbs() {
    for (auto& kv : mPhotoThumbCache) if (kv.second) glDeleteTextures(1, &kv.second);
    mPhotoThumbCache.clear();
    mPhotoThumbAR.clear();
}

void NanoMenu::openPhotoGrid(const std::vector<int>& list, const std::string& title, int fromPl) {
    mPhotoGridList = list;
    mPhotoGridCursor = 0;
    mPhotoGridTop = 0;
    mPhotoGridTitle = title;
    mPhotoGridAnim = 0.0f;
    mPhotoGridFocusStart = -1.0f;
    mPhotoGridCursorPrev = -1;
    mPhotoGridFromPl = fromPl;
    Ps3Level lvl;
    lvl.title = title;
    lvl.sel = 0;
    lvl.screenKind = PHOTO_GRID;
    mPs3Stack.push_back(lvl);
}

void NanoMenu::closePhotoGrid() {
    photoFreeThumbs();
}

void NanoMenu::photoGridNav(int dx, int dy) {
    int n = (int)mPhotoGridList.size();
    if (n <= 0) return;
    int col = mPhotoGridCursor % PG_COLS;
    int prev = mPhotoGridCursor;
    bool moved = false;
    if (dx < 0) {
        if (col == 0) {   // LEFT at the leftmost column exits the grid (web pgGridMove)
            closePhotoGrid();
            if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == PHOTO_GRID) mPs3Stack.pop_back();
            return;
        }
        if (mPhotoGridCursor - 1 >= 0) { mPhotoGridCursor--; moved = true; }
    } else if (dx > 0) {
        if (col == PG_COLS - 1) return;
        if (mPhotoGridCursor + 1 < n) { mPhotoGridCursor++; moved = true; }
    } else if (dy < 0) {
        if (mPhotoGridCursor - PG_COLS >= 0) { mPhotoGridCursor -= PG_COLS; moved = true; }
    } else if (dy > 0) {
        if (mPhotoGridCursor + PG_COLS < n) { mPhotoGridCursor += PG_COLS; moved = true; }
    }
    if (moved) {
        mPhotoGridCursorPrev = prev;
        mPhotoGridFocusStart = mEffectTime;
        // keep the focused row visible (same row count the renderer lays out)
        float ts = fmaxf(1.0f, (float)mHeight / 768.0f);
        float margin = mWidth * 0.055f;
        float cell = (mWidth - margin * 2.0f) / PG_COLS;
        float top = 132.0f * ts;
        int visRows = (cell > 0) ? (int)((mHeight - top - 70.0f * ts) / cell) : 3;
        if (visRows < 1) visRows = 1;
        int curRow = mPhotoGridCursor / PG_COLS;
        if (curRow < mPhotoGridTop) mPhotoGridTop = curRow;
        if (curRow >= mPhotoGridTop + visRows) mPhotoGridTop = curRow - visRows + 1;
        if (mPhotoGridTop < 0) mPhotoGridTop = 0;
    }
}

void NanoMenu::photoGridSelect() {
    if (mPhotoGridCursor < 0 || mPhotoGridCursor >= (int)mPhotoGridList.size()) return;
    openPhotoViewer(mPhotoGridList, mPhotoGridCursor);
}

void NanoMenu::renderPhotoGrid() {
    int W = mWidth, H = mHeight;
    float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;
    mPhotoGridAnim += (1.0f - mPhotoGridAnim) * (1.0f - expf(-13.0f * dt));
    if (mPhotoGridAnim > 0.999f) mPhotoGridAnim = 1.0f;
    float a = mPhotoGridAnim;
    float slide = (1.0f - a) * 24.0f;
    // dark scrim over the wave background
    drawQuad(0, 0, (float)W, (float)H, 0.04f, 0.05f, 0.06f, 0.88f * a);

    float ts = fmaxf(1.0f, (float)H / 768.0f);
    float margin = W * 0.055f;
    float top = 132.0f * ts + slide;
    float gridW = W - margin * 2.0f;
    float cell = gridW / PG_COLS;
    float thumbW = cell * 0.84f, thumbH = thumbW * 9.0f / 16.0f;   // 16:9 thumbnail box
    int visRows = (int)((H - top - 70.0f * ts) / cell);
    if (visRows < 1) visRows = 1;

    // title + count
    drawText(mPhotoGridTitle.c_str(), margin, 34.0f * ts + slide, 1.7f * ts, 1.0f, 1.0f, 1.0f, a);
    char info[64];
    snprintf(info, sizeof(info), "%zu %s", mPhotoGridList.size(),
             mPhotoGridList.size() == 1 ? "image" : "images");
    drawText(info, margin, 84.0f * ts + slide, 1.0f * ts, 0.75f, 0.85f, 0.95f, a);

    // focus grow tween (1.0 -> 1.36, easeOutCubic, mirrors the web drawPhotoGrid)
    float fe = 1.0f;
    if (mPhotoGridFocusStart >= 0.0f) {
        fe = fminf(1.0f, (mEffectTime - mPhotoGridFocusStart) / 0.18f);
        fe = 1.0f - powf(1.0f - fe, 3.0f);
        if (fe >= 1.0f) mPhotoGridFocusStart = -1.0f;
    }
    float pulse = 0.5f + 0.5f * cosf(mEffectTime * 2.0f * 3.14159f / 1.5f);

    int first = mPhotoGridTop * PG_COLS;
    int last = first + PG_COLS * visRows;
    int n = (int)mPhotoGridList.size();
    int wantDecode = -1;   // decode at most one uncached visible thumb per frame
    for (int idx = first; idx < last && idx < n; idx++) {
        int gi = idx - first;
        int cx = gi % PG_COLS, cy = gi / PG_COLS;
        float ccx = margin + cx * cell + cell * 0.5f;
        float ccy = top + cy * cell + cell * 0.5f;
        bool sel = (idx == mPhotoGridCursor);
        float sc = 1.0f;
        if (sel) sc = 1.0f + 0.36f * fe;
        else if (idx == mPhotoGridCursorPrev && mPhotoGridFocusStart >= 0.0f) sc = 1.36f - 0.36f * fe;
        float w = thumbW * sc, h = thumbH * sc;
        float x = ccx - w * 0.5f, y = ccy - h * 0.5f;
        int pIdx = mPhotoGridList[idx];
        GLuint tex = 0;
        auto cit = mPhotoThumbCache.find(pIdx);
        if (cit != mPhotoThumbCache.end()) tex = cit->second;
        else if (wantDecode < 0) wantDecode = pIdx;
        // opaque base (drop-shadow substitute): a dark card behind the thumb
        drawQuad(x - 2, y - 2, w + 4, h + 4, 0.0f, 0.0f, 0.0f, (sel ? 0.8f : 0.6f) * a);
        if (tex) drawIconTex(tex, x, y, w, h, 1.0f, 1.0f, 1.0f, (sel ? 1.0f : 0.92f) * a);
        else     drawQuad(x, y, w, h, 0.10f, 0.11f, 0.13f, 0.9f * a);   // placeholder until decoded
        if (sel) {
            // crisp white frame + a soft breathing outline (two faint expanded frames)
            float ow = fmaxf(2.0f, 3.0f * ts);
            float ga = (0.45f + 0.25f * pulse) * a;
            drawQuad(x - ow, y - ow, w + 2 * ow, ow, 1, 1, 1, ga * 0.5f);
            drawQuad(x - ow, y + h, w + 2 * ow, ow, 1, 1, 1, ga * 0.5f);
            drawQuad(x - ow, y, ow, h, 1, 1, 1, ga * 0.5f);
            drawQuad(x + w, y, ow, h, 1, 1, 1, ga * 0.5f);
            // inner crisp frame
            float iw2 = fmaxf(1.5f, 2.0f * ts);
            drawQuad(x, y, w, iw2, 1, 1, 1, 0.95f * a);
            drawQuad(x, y + h - iw2, w, iw2, 1, 1, 1, 0.95f * a);
            drawQuad(x, y, iw2, h, 1, 1, 1, 0.95f * a);
            drawQuad(x + w - iw2, y, iw2, h, 1, 1, 1, 0.95f * a);
        }
    }
    if (wantDecode >= 0) { photoThumb(wantDecode); photoThumbEvict(); }

    // focused caption (filename + date) under the grid
    if (mPhotoGridCursor >= 0 && mPhotoGridCursor < n) {
        const PhotoItem& p = mPhotos[mPhotoGridList[mPhotoGridCursor]];
        float ns = 1.25f * ts;
        float nw = measureText(p.name.c_str(), ns);
        drawText(p.name.c_str(), (W - nw) * 0.5f, (float)H - 60.0f * ts, ns, 1.0f, 1.0f, 1.0f, a);
        std::string dt = fmtPhotoDate(p.date);
        float ds = 0.95f * ts;
        float dw = measureText(dt.c_str(), ds);
        drawText(dt.c_str(), (W - dw) * 0.5f, (float)H - 32.0f * ts, ds, 0.8f, 0.85f, 0.92f, a);
    }
}

// ---------------------------------------------------------------------------
// Full-screen photo viewer (firmware photoviewer_plugin.rco).
// ---------------------------------------------------------------------------
GLuint NanoMenu::pvTex(int photoIdx, int* w, int* h) {
    auto it = mPvTexCache.find(photoIdx);
    if (it != mPvTexCache.end()) {
        if (w) *w = mPvTexW[photoIdx]; if (h) *h = mPvTexH[photoIdx];
        return it->second;
    }
    if (photoIdx < 0 || photoIdx >= (int)mPhotos.size()) return 0;
    int dw = 0, dh = 0;
    int maxDim = (mWidth > mHeight ? mWidth : mHeight);
    if (maxDim < 1024) maxDim = 1024;       // never decode below a crisp display size
    if (maxDim > 1920) maxDim = 1920;
    GLuint tex = photoDecodeTex(mPhotos[photoIdx].file, maxDim, &dw, &dh);
    mPvTexCache[photoIdx] = tex;
    mPvTexW[photoIdx] = dw; mPvTexH[photoIdx] = dh;
    if (w) *w = dw; if (h) *h = dh;
    return tex;
}

void NanoMenu::pvPrefetch() {
    // decode current + immediate neighbours; drop anything outside that window
    if (mPvList.empty()) return;
    std::set<int> keep;
    for (int d = -1; d <= 1; d++) {
        int i = mPvIdx + d;
        if (i < 0 || i >= (int)mPvList.size()) continue;
        keep.insert(mPvList[i]);
    }
    for (auto i = mPvTexCache.begin(); i != mPvTexCache.end(); ) {
        if (keep.count(i->first)) { ++i; continue; }
        if (i->second) glDeleteTextures(1, &i->second);
        mPvTexW.erase(i->first); mPvTexH.erase(i->first);
        i = mPvTexCache.erase(i);
    }
}

void NanoMenu::pvFreeTextures() {
    for (auto& kv : mPvTexCache) if (kv.second) glDeleteTextures(1, &kv.second);
    mPvTexCache.clear(); mPvTexW.clear(); mPvTexH.clear();
}

void NanoMenu::openPhotoViewer(const std::vector<int>& list, int idx) {
    if (list.empty()) return;
    mPvList = list;
    mPvIdx = (idx < 0 || idx >= (int)list.size()) ? 0 : idx;
    mPvRot = 0; mPvZoom = 1.0f; mPvPanX = 0.0f; mPvPanY = 0.0f;
    mPvHintUntil = mEffectTime + 4.0f;
    mPvActive = true;
    mPvPanel = false; mPvCpClosing = false; mPvInfo = false; mPvCpSub = false;
    mPvSlideshow = false; mPvPaused = false; mPvWpMode = false; mPvTrimMode = false;
    mPvTrans = false;
    if (mPvEffect.empty()) mPvEffect = "Normal";
    pvTex(mPvList[mPvIdx], nullptr, nullptr);
}

void NanoMenu::closePhotoViewer() {
    mPvActive = false;
    mPvPanel = false; mPvCpClosing = false; mPvSlideshow = false; mPvInfo = false;
    mPvWpMode = false; mPvTrimMode = false; mPvCpSub = false; mPvPlChooserActive = false;
    // Textures + list are kept so renderPhotoViewer can draw the exit fade-to-black;
    // photoTick frees them once mPvEnterRaw eases to ~0.
}

void NanoMenu::pvStep(int d) {
    if (mPvList.empty()) return;
    const std::string& eff = mPvEffect.empty() ? std::string("Normal") : mPvEffect;
    if (eff == "Slide" || eff == "Fade") {
        mPvTrans = true; mPvTransFrom = mPvIdx; mPvTransStart = mEffectTime;
        mPvTransEffect = eff; mPvTransDir = d >= 0 ? 1 : -1;
    } else {
        mPvTrans = false;
    }
    int n = (int)mPvList.size();
    mPvIdx = ((mPvIdx + d) % n + n) % n;
    mPvRot = 0; mPvZoom = 1.0f; mPvPanX = 0.0f; mPvPanY = 0.0f;
    mPvHintUntil = mEffectTime + 1.5f;
    pvTex(mPvList[mPvIdx], nullptr, nullptr);
}

// Draw one photo fit-to-screen with rotation / zoom / pan, at a given alpha and
// horizontal screen offset (for the Slide transition). Mirrors web drawPhoto.
void NanoMenu::renderPhotoViewer() {
    if (mPvList.empty()) { mPvActive = false; return; }
    int W = mWidth, H = mHeight;
    float dt = mFrameDt; if (dt < 0.0f || dt > 0.2f) dt = 0.016f;
    // slideshow auto-advance
    if (mPvSlideshow && !mPvPaused && mEffectTime * 1000.0f > mPvSlideNext) {
        pvStep(1);
        mPvSlideNext = mEffectTime * 1000.0f + mPvSlideMs;
    }
    // enter / exit fade from black (~0.4s, smoothstep) - eased toward 1 while active
    float target = mPvActive ? 1.0f : 0.0f;
    float step = (dt * 1000.0f) / 400.0f;
    if (mPvEnterRaw < target) mPvEnterRaw = fminf(target, mPvEnterRaw + step);
    else if (mPvEnterRaw > target) mPvEnterRaw = fmaxf(target, mPvEnterRaw - step);
    mPvEnterT = mPvEnterRaw * mPvEnterRaw * (3.0f - 2.0f * mPvEnterRaw);
    float et = mPvEnterT;

    drawQuad(0, 0, (float)W, (float)H, 0.0f, 0.0f, 0.0f, 1.0f);   // black backdrop

    // textured-quad with rotation about screen centre + pan, mapped to NDC.
    auto drawPhoto = [&](int photoIdx, int rot, float zoom, float alpha, float dxPx,
                         float panX, float panY) {
        if (alpha <= 0.001f) return;
        int iw = 0, ih = 0;
        GLuint tex = pvTex(photoIdx, &iw, &ih);
        if (!tex || iw <= 0 || ih <= 0) return;
        bool swap = (rot == 90 || rot == 270);
        float bw = swap ? (float)ih : (float)iw;
        float bh = swap ? (float)iw : (float)ih;
        float fit = fminf((float)W / bw, (float)H / bh) * (zoom <= 0 ? 1.0f : zoom);
        float dw = iw * fit, dh = ih * fit;
        // pan clamp to the rotated on-screen bounding box
        float dispW = swap ? dh : dw, dispH = swap ? dw : dh;
        float maxX = fmaxf(0.0f, (dispW - W) * 0.5f), maxY = fmaxf(0.0f, (dispH - H) * 0.5f);
        float pX = fmaxf(-maxX, fminf(maxX, panX));
        float pY = fmaxf(-maxY, fminf(maxY, panY));
        if (photoIdx == mPvList[mPvIdx]) { mPvPanX = pX; mPvPanY = pY; }
        float cx = W * 0.5f + dxPx + pX, cy = H * 0.5f + pY;
        float ang = rot * 3.14159265f / 180.0f;
        float ca = cosf(ang), sa = sinf(ang);
        // local corners (centred quad of size dw x dh) with UVs; rotate then translate
        float hw = dw * 0.5f, hh = dh * 0.5f;
        float lx[4] = { -hw,  hw,  hw, -hw };
        float ly[4] = { -hh, -hh,  hh,  hh };
        float u[4]  = {  0.0f, 1.0f, 1.0f, 0.0f };
        float v[4]  = {  0.0f, 0.0f, 1.0f, 1.0f };
        float sx[4], sy[4];
        for (int i = 0; i < 4; i++) {
            float rx = lx[i] * ca - ly[i] * sa;
            float ry = lx[i] * sa + ly[i] * ca;
            sx[i] = cx + rx; sy[i] = cy + ry;
        }
        auto ndcX = [&](float x){ return (x / W) * 2.0f - 1.0f; };
        auto ndcY = [&](float y){ return 1.0f - (y / H) * 2.0f; };
        GLfloat verts[12], uvs[12], cols[24];
        const int order[6] = {0, 1, 2, 0, 2, 3};
        for (int k = 0; k < 6; k++) {
            int c = order[k];
            verts[k * 2] = ndcX(sx[c]); verts[k * 2 + 1] = ndcY(sy[c]);
            uvs[k * 2] = u[c]; uvs[k * 2 + 1] = v[c];
            cols[k * 4] = 1.0f; cols[k * 4 + 1] = 1.0f; cols[k * 4 + 2] = 1.0f; cols[k * 4 + 3] = alpha;
        }
        glUseProgram(mTextProgram);
        if (mTextLocSharp >= 0) glUniform1f(mTextLocSharp, 0.0f);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, tex);
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
    };

    // Change Effect transition (Slide/Fade) when stepping photos
    float tp = 1.0f;
    if (mPvTrans) {
        tp = fminf(1.0f, (mEffectTime - mPvTransStart) / 0.65f);
        if (tp >= 1.0f) mPvTrans = false;
    }
    if (mPvTrans && tp < 1.0f) {
        float e = tp * tp * (3.0f - 2.0f * tp);
        if (mPvTransEffect == "Slide") {
            drawPhoto(mPvList[mPvTransFrom], 0, 1.0f, et, -e * W * mPvTransDir, 0, 0);
            drawPhoto(mPvList[mPvIdx], mPvRot, mPvZoom, et, (1.0f - e) * W * mPvTransDir, 0, 0);
        } else {   // Fade
            drawPhoto(mPvList[mPvTransFrom], 0, 1.0f, et * (1.0f - e), 0, 0, 0);
            drawPhoto(mPvList[mPvIdx], mPvRot, mPvZoom, et * e, 0, 0, 0);
        }
    } else {
        drawPhoto(mPvList[mPvIdx], mPvRot, mPvZoom, et, 0, mPvPanX, mPvPanY);
    }

    pvPrefetch();

    // bottom-right auto-hiding help hint (two rows): Control Panel / Home Menu
    float hintA = fmaxf(0.0f, fminf(1.0f, (mPvHintUntil - mEffectTime) / 0.6f)) * et;
    if (hintA > 0.01f) {
        float fs = PFS(20.0f);
        const char* t1 = "Triangle : Control Panel";
        const char* t2 = "PS : Home Menu";
        float tw = fmaxf(measureText(t1, fs), measureText(t2, fs));
        float padX = PSZ(0.012f), padY = PSZ(0.012f), lh = PSZ(0.034f);
        float pillW = tw + padX * 2.0f, pillH = lh * 2.0f + padY * 2.0f - lh * 0.4f;
        float px = (float)W - PXD(0.028f) - pillW, py = (float)H - PSZ(0.074f) - pillH;
        drawQuad(px, py, pillW, pillH, 64/255.0f, 64/255.0f, 68/255.0f, 0.82f * hintA);
        float y1 = py + padY, y2 = y1 + lh;
        drawText(t1, px + padX, ps3::baselineToTopY(y1 + fs * 0.0f + PSZ(0.018f), fs), fs, 1, 1, 1, 0.95f * hintA);
        drawText(t2, px + padX, ps3::baselineToTopY(y2 + fs * 0.0f + PSZ(0.018f), fs), fs, 1, 1, 1, 0.95f * hintA);
    }

    if (mPvWpMode || mPvTrimMode) drawPvWallpaperSel();   // Set as Wallpaper / Trimming range selector
    if (mPvInfo) drawPvInfo();
    if (mPvPanel) drawPvPanel(-1.0f);
    else if (mPvCpClosing) {
        float p = (mEffectTime - mPvCpCloseStart) / 0.2f;
        if (p >= 1.0f) mPvCpClosing = false; else drawPvPanel(1.0f - p);
    }
    if (mPvDispModeUntil > mEffectTime) drawPvDispModePill();
    if (mPvPlChooserActive || mPvPlChooserAnim > 0.004f) drawPvPlChooser();
    // transient full-screen message (Delete / 2D-3D / wallpaper-set), music-style
    if (mPvMsgStart >= 0.0f) {
        float el = (mEffectTime - mPvMsgStart) * 1000.0f;
        if (el >= mPvMsgDur) mPvMsgStart = -1.0f;
        else {
            float fade = fminf(1.0f, el / 150.0f) * fminf(1.0f, fmaxf(0.0f, (mPvMsgDur - el)) / 200.0f);
            drawQuad(0, 0, (float)W, (float)H, 0, 0, 0, 0.45f * fade);
            float ms = PFS(28.0f); float mw = measureText(mPvMsg.c_str(), ms);
            drawText(mPvMsg.c_str(), (W - mw) * 0.5f,
                     ps3::baselineToTopY(PYP(0.5f), ms), ms, 1.0f, 1.0f, 1.0f, fade);
        }
    }
}

// ---------------------------------------------------------------------------
// EXIF "Display" info overlay (1:1 web drawPvInfo).
// ---------------------------------------------------------------------------
static const char* kPvExifFields[] = {
    "Date taken", "Manufacturer name", "Model name", "Image size", "MaxApertureValue",
    "Lens focal length", "Shutter speed", "F number", "Exposure correction value",
    "Exposure program", "MeteringMode", "ISO", "White balance mode", "Flash",
    "Colour space", "Exif version"
};
static const int kPvExifCount = (int)(sizeof(kPvExifFields) / sizeof(kPvExifFields[0]));

void NanoMenu::drawPvInfo() {
    if (mPvList.empty()) return;
    const PhotoItem& p = mPhotos[mPvList[mPvIdx]];
    int W = mWidth, H = mHeight;
    float labelX = PXP(0.7037f), valueX = PXP(0.7161f);
    float top = PYP(0.176f), rowH = PSZ(0.037f);
    float fs = PFS(22.5f);
    drawQuad(PXP(0.567f), top - rowH * 0.7f, (float)W - PXP(0.567f),
             rowH * kPvExifCount + rowH * 0.5f, 0, 0, 0, 0.78f * mPvEnterT);
    for (int i = 0; i < kPvExifCount; i++) {
        float y = top + i * rowH;
        const char* f = kPvExifFields[i];
        std::string val = "-";
        if (!strcmp(f, "Date taken")) val = fmtPhotoDate(p.date);
        else if (!strcmp(f, "Image size")) val = (p.w && p.h)
                 ? (std::to_string(p.w) + " x " + std::to_string(p.h)) : "-";
        else if (!strcmp(f, "Exposure program") || !strcmp(f, "MeteringMode")
                 || !strcmp(f, "White balance mode") || !strcmp(f, "Colour space")) val = "Unknown";
        float lw = measureText(f, fs);
        drawText(f, labelX - lw, ps3::baselineToTopY(y, fs), fs, 0.88f, 0.88f, 0.90f, 0.92f * mPvEnterT);
        drawText(val.c_str(), valueX, ps3::baselineToTopY(y, fs), fs, 1.0f, 1.0f, 1.0f, 0.95f * mPvEnterT);
    }
    (void)H;
}

void NanoMenu::drawPvDispModePill() {
    if (mPvList.empty()) return;
    const PhotoItem& p = mPhotos[mPvList[mPvIdx]];
    int W = mWidth, H = mHeight;
    float fit = (p.w > 0 && p.h > 0) ? fminf(ps3::VW / p.w, ps3::VH / p.h) : 1.0f;
    int pct = (int)(fit * (mPvZoom <= 0 ? 1.0f : mPvZoom) * 100.0f + 0.5f);
    char label[40];
    snprintf(label, sizeof(label), "%s %d%%", (mPvDispModeName.empty() ? "Normal" : mPvDispModeName.c_str()), pct);
    float fade = fmaxf(0.0f, fminf(1.0f, (mPvDispModeUntil - mEffectTime) / 0.4f));
    float fs = PFS(27.0f);
    float tw = measureText(label, fs);
    float pad = PXD(0.012f), gx = PXD(0.020f), gap = PXD(0.008f);
    float px = 0.057f * W, py = 0.794f * H, ph = 0.048f * H;
    float pw = pad + gx + gap + tw + pad;
    drawQuad(px, py, pw, ph, 0, 0, 0, 0.72f * fade);
    // screen glyph (two inner bars)
    float ix = px + pad, iy = py + ph * 0.30f, ih = ph * 0.40f;
    drawQuad(ix, iy, gx, fmaxf(1.5f, ph * 0.04f), 0.92f, 0.92f, 0.92f, 0.95f * fade);
    drawQuad(ix, iy + ih, gx, fmaxf(1.5f, ph * 0.04f), 0.92f, 0.92f, 0.92f, 0.95f * fade);
    drawQuad(ix + gx * 0.16f, iy, fmaxf(2.0f, gx * 0.08f), ih, 0.92f, 0.92f, 0.92f, 0.95f * fade);
    drawQuad(ix + gx * 0.78f, iy, fmaxf(2.0f, gx * 0.08f), ih, 0.92f, 0.92f, 0.92f, 0.95f * fade);
    drawText(label, ix + gx + gap, ps3::baselineToTopY(py + ph * 0.5f + PSZ(0.012f), fs), fs,
             0.96f, 0.96f, 0.96f, 0.96f * fade);
}

// ---------------------------------------------------------------------------
// Photoviewer icons (lazy; /data override + /system/etc fallback).
// ---------------------------------------------------------------------------
GLuint NanoMenu::pvIcon(int n) {
    auto it = mPvIconCache.find(n);
    if (it != mPvIconCache.end()) return it->second;
    char file[40]; snprintf(file, sizeof(file), "icon_%03d.png", n);
    char path[256]; int w = 0, h = 0;
    snprintf(path, sizeof(path), "/data/system/nano_xmb/photoviewer/%s", file);
    GLuint tex = photoDecodeTex(path, 0, &w, &h);
    if (!tex) {
        snprintf(path, sizeof(path), "/system/etc/nano_xmb/photoviewer/%s", file);
        tex = photoDecodeTex(path, 0, &w, &h);
    }
    mPvIconCache[n] = tex;
    mPvIconAR[n] = (tex && h > 0) ? (float)w / (float)h : 1.0f;
    return tex;
}
float NanoMenu::pvIconAR(int n) {
    auto it = mPvIconAR.find(n);
    return it != mPvIconAR.end() ? it->second : 1.0f;
}

// ---------------------------------------------------------------------------
// Control panel (TRIANGLE). Same icon style / spacing / focus treatment as the
// Music MP_CP control panel (drawMpOpt), with the photo control set (PV_CP).
// ---------------------------------------------------------------------------
struct PvCp { const char* act; const char* label; int ic; float gx; float gy; };
// Row 0: Display Mode / Change Effect / Trimming / Add to Playlist / Print /
//        Set as Wallpaper / Delete / Display (EXIF). Row 1: Zoom In/Out, Rotate
//        L/R, Up/Down/Left/Right (pan). Row 2: Previous / Next / Slideshow.
static const PvCp kPvCp[] = {
    {"dispmode", "Display Mode",     8,  0, 0},
    {"effect",   "Change Effect",   21,  1, 0},
    {"trim",     "Trimming",        22,  2, 0},
    {"addpl",    "Add to Playlist",  9,  3, 0},
    {"print",    "Print",           23,  4, 0},
    {"wallpaper","Set as Wallpaper",10,  5, 0},
    {"delete",   "Delete",          20,  6, 0},
    {"showinfo", "Display",          0,  7, 0},
    {"zoomin",   "Zoom In",         11,  0, 1},
    {"zoomout",  "Zoom Out",        12,  1, 1},
    {"rotl",     "Rotate Left",     17,  2, 1},
    {"rotr",     "Rotate Right",    18,  3, 1},
    {"up",       "Up",              13,  4, 1},
    {"down",     "Down",            14,  5, 1},
    {"left",     "Left",            15,  6, 1},
    {"right",    "Right",           16,  7, 1},
    {"prev",     "Previous",         1,  2.5f, 2},
    {"next",     "Next",             2,  3.5f, 2},
    {"play",     "Slideshow",        3,  4.5f, 2},
};
static const int kPvCpCount = (int)(sizeof(kPvCp) / sizeof(kPvCp[0]));
// Running-slideshow panel (Speed / Style; Prev / Next / Pause / Stop; Repeat).
static const PvCp kPvSsCp[] = {
    {"speed",  "Slideshow Speed",  7, 3,    0},
    {"sstyle", "Slideshow Style",  8, 4,    0},
    {"prev",   "Previous",         1, 2,    1},
    {"next",   "Next",             2, 3,    1},
    {"pause",  "Pause",            3, 4,    1},
    {"stop",   "Stop",             5, 5,    1},
    {"repeat", "Repeat",           6, 3.5f, 2},
};
static const int kPvSsCpCount = (int)(sizeof(kPvSsCp) / sizeof(kPvSsCp[0]));

static const PvCp* pvCpTable(bool slideshow, int* count) {
    if (slideshow) { *count = kPvSsCpCount; return kPvSsCp; }
    *count = kPvCpCount; return kPvCp;
}

void NanoMenu::openPvPanel() {
    if (mPvPanel) return;
    int cnt; const PvCp* cp = pvCpTable(mPvSlideshow, &cnt);
    mPvPanel = true; mPvCpClosing = false; mPvCpSub = false;
    mPvCpSel = 0;
    if (mPvSlideshow) { for (int i = 0; i < cnt; i++) if (!strcmp(cp[i].act, "pause")) { mPvCpSel = i; break; } }
    mPvCpAnimStart = mEffectTime; mPvCpFocusStart = mEffectTime; mPvCpSelPrev = mPvCpSel;
    mPvCpPressStart = -1.0f; mPvHintUntil = 0.0f;
}

void NanoMenu::closePvPanel() {
    if (!mPvPanel) return;
    if (mPvCpSub) { mPvCpSub = false; return; }
    mPvPanel = false; mPvCpClosing = true; mPvCpCloseStart = mEffectTime;
}

void NanoMenu::pvPanelBack() {
    if (mPvCpSub) { mPvCpSub = false; return; }
    closePvPanel();
}

void NanoMenu::pvPanelMove(int dx, int dy) {
    if (!mPvPanel) return;
    if (mPvCpSub) {
        if (dy != 0) { int n = (int)mPvCpSubOpts.size(); if (n > 0) mPvCpSubSel = (mPvCpSubSel + dy + n) % n; }
        return;
    }
    int cnt; const PvCp* cp = pvCpTable(mPvSlideshow, &cnt);
    const PvCp& cur = cp[mPvCpSel];
    int best = -1; float bestd = 1e9f;
    for (int i = 0; i < cnt; i++) {
        if (i == mPvCpSel) continue;
        const PvCp& b = cp[i];
        float ddx = b.gx - cur.gx, ddy = b.gy - cur.gy;
        if (dx > 0 && ddx <= 0) continue; if (dx < 0 && ddx >= 0) continue;
        if (dy > 0 && ddy <= 0) continue; if (dy < 0 && ddy >= 0) continue;
        float along = dx != 0 ? fabsf(ddx) : fabsf(ddy);
        float perp  = dx != 0 ? fabsf(ddy) : fabsf(ddx);
        float d = along + perp * 3.0f;
        if (d < bestd) { bestd = d; best = i; }
    }
    if (best >= 0) { mPvCpSelPrev = mPvCpSel; mPvCpFocusStart = mEffectTime; mPvCpSel = best; }
}

void NanoMenu::pvPanelActivate() {
    if (!mPvPanel) return;
    if (mPvCpSub) {   // confirm a submenu selection
        if (mPvCpSubSel >= 0 && mPvCpSubSel < (int)mPvCpSubOpts.size()) {
            const std::string& val = mPvCpSubOpts[mPvCpSubSel];
            if (mPvCpSubKind == "speed") {
                mPvSlideSpeed = val;
                mPvSlideMs = (val == "Slow") ? 7000.0f : (val == "Fast") ? 2000.0f : 4000.0f;
            } else if (mPvCpSubKind == "style") {
                mPvSlideStyle = mPvCpSubSel;
            } else { mPvEffect = val; }   // Change Effect
        }
        mPvCpSub = false; return;
    }
    int cnt; const PvCp* cp = pvCpTable(mPvSlideshow, &cnt);
    mPvCpPressStart = mEffectTime; mPvCpPressSel = mPvCpSel;
    const char* a = cp[mPvCpSel].act;
    if (!strcmp(a, "rotl"))      mPvRot = ((mPvRot - 90) % 360 + 360) % 360;
    else if (!strcmp(a, "rotr")) mPvRot = (mPvRot + 90) % 360;
    else if (!strcmp(a, "zoomin"))  mPvZoom = fminf(8.0f, (mPvZoom <= 0 ? 1.0f : mPvZoom) * 1.25f);
    else if (!strcmp(a, "zoomout")) { mPvZoom = fmaxf(1.0f, (mPvZoom <= 0 ? 1.0f : mPvZoom) / 1.25f);
                                      if (mPvZoom <= 1.0f) { mPvPanX = 0; mPvPanY = 0; } }
    else if (!strcmp(a, "up"))    mPvPanY += mHeight * 0.15f;
    else if (!strcmp(a, "down"))  mPvPanY -= mHeight * 0.15f;
    else if (!strcmp(a, "left"))  mPvPanX += mWidth * 0.15f;
    else if (!strcmp(a, "right")) mPvPanX -= mWidth * 0.15f;
    else if (!strcmp(a, "prev")) pvStep(-1);
    else if (!strcmp(a, "next")) pvStep(1);
    else if (!strcmp(a, "play")) { mPvPanel = false; mPvSlideshow = true; mPvPaused = false;
                                   mPvSlideNext = mEffectTime * 1000.0f + mPvSlideMs; }
    else if (!strcmp(a, "pause")) { mPvPaused = !mPvPaused; if (!mPvPaused) mPvSlideNext = mEffectTime * 1000.0f + mPvSlideMs; }
    else if (!strcmp(a, "stop")) { mPvSlideshow = false; mPvPaused = false; mPvPanel = false; }
    else if (!strcmp(a, "repeat")) { mPvRepeat = !mPvRepeat; }
    else if (!strcmp(a, "showinfo")) { mPvInfo = !mPvInfo; mPvPanel = false; }
    else if (!strcmp(a, "dispmode")) {
        mPvPanel = false;
        mPvDispModeName = (mPvDispModeName == "Zoom") ? "Normal" : "Zoom";
        mPvDispModeUntil = mEffectTime + 3.0f;
    }
    else if (!strcmp(a, "effect")) {
        mPvCpSub = true; mPvCpSubKind = "effect";
        mPvCpSubOpts = {"Normal", "Slide", "Fade"};
        mPvCpSubSel = 0;
        for (int i = 0; i < (int)mPvCpSubOpts.size(); i++) if (mPvCpSubOpts[i] == mPvEffect) { mPvCpSubSel = i; break; }
    }
    else if (!strcmp(a, "speed")) {
        mPvCpSub = true; mPvCpSubKind = "speed";
        mPvCpSubOpts = {"Slow", "Normal", "Fast"};
        mPvCpSubSel = 1;
        for (int i = 0; i < (int)mPvCpSubOpts.size(); i++) if (mPvCpSubOpts[i] == mPvSlideSpeed) { mPvCpSubSel = i; break; }
    }
    else if (!strcmp(a, "sstyle")) {
        mPvCpSub = true; mPvCpSubKind = "style";
        mPvCpSubOpts = {"Normal", "Slide", "Portrait", "Photo Album", "Photo Album 2"};
        mPvCpSubSel = (mPvSlideStyle >= 0 && mPvSlideStyle < 5) ? mPvSlideStyle : 0;
    }
    else if (!strcmp(a, "delete"))    { mPvPanel = false; pvShowDeleteConfirm(); }
    else if (!strcmp(a, "wallpaper")) { mPvPanel = false; mPvWpMode = true; mPvWpZoom = 1.0f; mPvHintUntil = 0.0f; }
    else if (!strcmp(a, "trim"))      { mPvPanel = false; mPvTrimMode = true; mPvWpZoom = 1.0f; mPvHintUntil = 0.0f; }
    else if (!strcmp(a, "print"))     { mPvPanel = false; }   // no printer in this environment (web closes too)
    else if (!strcmp(a, "addpl"))     { mPvPanel = false;
        if (mPvIdx >= 0 && mPvIdx < (int)mPvList.size())
            pvOpenAddChooser(mPhotos[mPvList[mPvIdx]].file); }
}

void NanoMenu::drawPvPanel(float closeT) {
    if (mPvList.empty()) return;
    float t = (closeT >= 0.0f) ? closeT
            : (mPvCpAnimStart >= 0.0f ? fminf(1.0f, (mEffectTime - mPvCpAnimStart) / 0.2f) : 1.0f);
    if (t < 0) t = 0;
    float ui = pvUiScale(mWidth, mHeight);
    // Same cell sizing + treatment as drawMpOpt; the photo grid is wider (8 cols)
    // so it is centred on the screen rather than offset to the music origin.
    float cellX = PXD(0.033f * ui), cellY = PSZ(0.061f * ui), ih = PSZ(0.046f * ui);
    int cnt; const PvCp* cp = pvCpTable(mPvSlideshow, &cnt);
    // grid horizontal extent (min/max gx) to centre it
    float gmin = 1e9f, gmax = -1e9f;
    for (int i = 0; i < cnt; i++) { gmin = fminf(gmin, cp[i].gx); gmax = fmaxf(gmax, cp[i].gx); }
    float gcen = (gmin + gmax) * 0.5f;
    float ox = PXP(0.5f) - gcen * cellX - (1.0f - t) * PSZ(0.018f * ui);   // slide-in from the left
    float oy = PYP(0.441f);
    float pulse = 0.5f + 0.5f * cosf(mEffectTime * 2.0f * 3.14159f / 1.5f);
    for (int i = 0; i < cnt; i++) {
        const PvCp& b = cp[i];
        bool focus = (i == mPvCpSel);
        float cx = ox + b.gx * cellX, cy = oy + b.gy * cellY;
        int icn = (!strcmp(b.act, "pause")) ? (mPvPaused ? 3 : 4) : b.ic;
        GLuint g = pvIcon(icn);
        float baseScale = focus ? 1.18f : 1.0f;
        if (mPvCpFocusStart >= 0.0f) {
            float fe = fminf(1.0f, (mEffectTime - mPvCpFocusStart) / 0.14f);
            float fk = 1.0f - powf(1.0f - fe, 3.0f);
            if (focus) baseScale = 1.0f + 0.18f * fk;
            else if (i == mPvCpSelPrev) baseScale = 1.18f - 0.18f * fk;
        }
        float ps = baseScale, flash = 0.0f;
        if (mPvCpPressSel == i && mPvCpPressStart >= 0.0f) {
            float e = (mEffectTime - mPvCpPressStart) / 0.24f;
            if (e < 1.0f) { float s = sinf(e * 3.14159265f); ps *= 1.0f - 0.18f * s; flash = s * 0.55f; }
        }
        auto glyph = [&](GLuint tex, int arIdx, float dx, float dy, float r, float gg, float bl, float al) {
            if (!tex) return;
            float hh = ih * ps, ww = hh * pvIconAR(arIdx);
            drawIconTex(tex, cx - ww * 0.5f + dx, cy - hh * 0.5f + dy, ww, hh, r, gg, bl, t * al);
        };
        if (focus) {
            if (g) {
                float hh = ih * ps * 1.18f, ww = hh * pvIconAR(icn);
                drawIconTex(g, cx - ww * 0.5f, cy - hh * 0.5f, ww, hh, 0.86f, 0.92f, 1.0f, t * (0.18f + 0.16f * pulse));
            }
            glyph(g, icn, 0, 0, 1, 1, 1, 1.0f);
        } else {
            glyph(g, icn, PSZ(0.0015f * ui), PSZ(0.0025f * ui), 0, 0, 0, 0.5f);
            glyph(g, icn, 0, 0, 1, 1, 1, 0.85f);
        }
        if (flash > 0.0f) glyph(g, icn, 0, 0, 1, 1, 1, flash);
    }
    // focused label + SELECT pill, centred at the grid origin (mirrors drawMpOpt)
    if (mPvCpSel >= 0 && mPvCpSel < cnt) {
        const char* lab = (!strcmp(cp[mPvCpSel].act, "pause")) ? (mPvPaused ? "Play" : "Pause") : cp[mPvCpSel].label;
        float ls = PFS(20.0f * ui), lw = measureText(lab, ls);
        float gap = PXD(0.008f * ui), pillW = PXD(0.050f * ui), pillH = PSZ(0.030f * ui);
        float total = lw + (mPvCpSub ? 0.0f : (gap + pillW));
        float ccx = PXP(0.5f), sx = ccx - total * 0.5f;
        float labBaseY = oy + 2.0f * cellY + PSZ(0.060f * ui);
        drawText(lab, sx, ps3::baselineToTopY(labBaseY, ls), ls, 1.0f, 1.0f, 1.0f, 0.95f * t);
        if (!mPvCpSub) {
            float px = sx + lw + gap;
            float py = labBaseY - pillH * 0.78f;
            float bw = 1.2f * ui;
            auto pill = [&](float qx, float qy, float qw, float qh, float cr, float cg, float cb, float ca) {
                float rr = qh * 0.5f;
                drawQuad(qx + rr, qy, qw - 2.0f * rr, qh, cr, cg, cb, ca);
                ps3FillCircle(qx + rr, qy + rr, rr, cr, cg, cb, ca);
                ps3FillCircle(qx + qw - rr, qy + rr, rr, cr, cg, cb, ca);
            };
            pill(px - bw, py - bw, pillW + 2.0f * bw, pillH + 2.0f * bw, 225/255.0f, 225/255.0f, 225/255.0f, 0.7f * t);
            pill(px, py, pillW, pillH, 150/255.0f, 150/255.0f, 150/255.0f, 0.55f * t);
            float fs = PFS(15.0f * ui), fw = measureText("SELECT", fs);
            drawText("SELECT", px + pillW * 0.5f - fw * 0.5f,
                     ps3::baselineToTopY(py + pillH * 0.66f, fs), fs, 1, 1, 1, 0.95f * t);
        }
    }
    // control submenu (Change Effect / Speed / Style) under the label
    if (mPvCpSub && !mPvCpSubOpts.empty()) {
        float fs = PFS(24.0f * ui), lh = PSZ(0.045f * ui);
        float ccx = PXP(0.5f);
        float mw = 0; for (auto& o : mPvCpSubOpts) mw = fmaxf(mw, measureText(o.c_str(), fs));
        float sx = ccx - mw * 0.5f;
        float sy = oy + 2.0f * cellY + PSZ(0.110f * ui);
        drawQuad(sx - fs * 0.5f, sy - lh * 0.5f, mw + fs, lh * mPvCpSubOpts.size() + lh * 0.3f, 0, 0, 0, 0.55f * t);
        for (int i = 0; i < (int)mPvCpSubOpts.size(); i++) {
            float oy2 = sy + i * lh;
            bool sel = (i == mPvCpSubSel);
            if (sel) drawQuad(sx - fs * 0.35f, oy2 - lh * 0.42f, mw + fs * 0.7f, lh * 0.86f, 1, 1, 1, 0.20f * t);
            drawText(mPvCpSubOpts[i].c_str(), sx, ps3::baselineToTopY(oy2 + lh * 0.18f, fs), fs,
                     sel ? 1.0f : 0.88f, sel ? 1.0f : 0.88f, sel ? 1.0f : 0.90f, 0.95f * t);
        }
    }
}

// ---------------------------------------------------------------------------
// Per-frame tick (viewer enter-fade is handled in renderPhotoViewer; this drives
// timers that must run even when the viewer is not the active render path).
// ---------------------------------------------------------------------------
void NanoMenu::photoTick() {
    // add-to-playlist chooser fade (ease toward target)
    float dt0 = mFrameDt; if (dt0 < 0.0f || dt0 > 0.2f) dt0 = 0.016f;
    float ct = mPvPlChooserActive ? 1.0f : 0.0f;
    mPvPlChooserAnim += (ct - mPvPlChooserAnim) * fminf(1.0f, dt0 * 10.0f);
    // Once the exit fade has fully run (renderPhotoViewer eases mPvEnterRaw down; the
    // render dispatch stops calling it below ~0.004), drop the kept textures + list.
    if (!mPvActive && mPvEnterRaw <= 0.004f && (!mPvList.empty() || !mPvTexCache.empty())) {
        mPvEnterRaw = 0.0f; mPvEnterT = 0.0f;
        pvFreeTextures();
        mPvList.clear();
    }
}

// ---------------------------------------------------------------------------
// Transient message, delete and 2D/3D (music-style full-screen message).
// ---------------------------------------------------------------------------
void NanoMenu::pvShowMsg(const std::string& text, float durMs) {
    mPvMsg = text; mPvMsgStart = mEffectTime; mPvMsgDur = durMs;
}
void NanoMenu::pvShowDeleteConfirm() {
    // Simulated delete (the web does not unlink the real file either); show the
    // same completion message style the music player uses.
    pvShowMsg("Delete completed.", 1100.0f);
}
void NanoMenu::pvShow3D() {
    // 3D display cannot render here; report it the way the firmware fallback does.
    pvShowMsg("An error occurred while switching to display in 3D.", 1600.0f);
}

// ---------------------------------------------------------------------------
// Set as Wallpaper / Trimming range selector (1:1 web drawPvWallpaper).
// ---------------------------------------------------------------------------
void NanoMenu::drawPvWallpaperSel() {
    if (!mPvWpMode && !mPvTrimMode) return;
    if (mPvList.empty()) return;
    int W = mWidth, H = mHeight;
    drawQuad(0, 0, (float)W, (float)H, 0, 0, 0, 1.0f);   // black backdrop over the photo
    if (mPvWpMode) {
        const char* hdr = "Specify the range to use as wallpaper.";
        float fs = PFS(26.0f); float hw = measureText(hdr, fs);
        drawText(hdr, (W - hw) * 0.5f, ps3::baselineToTopY(PYP(0.085f), fs), fs, 0.92f, 0.92f, 0.92f, 0.95f);
    }
    // 16:9 crop frame, photo cover-filled inside it
    float fw = W * 0.62f, fh = fw * 9.0f / 16.0f;
    float fx = (W - fw) * 0.5f, fy = (H - fh) * 0.5f + H * 0.01f;
    int iw = 0, ih = 0;
    GLuint tex = pvTex(mPvList[mPvIdx], &iw, &ih);
    if (tex && iw > 0 && ih > 0) {
        // scissor-clip to the crop frame, draw the photo cover-filling it
        int lx = (int)fx, ly = (int)fy, lw = (int)fw, lh = (int)fh;
        int sx, sy, sw, sh;
        switch (sDrmGlRotation ? sDrmRotationDeg : 0) {
        case 90:  sx = ly; sy = (int)W - lx - lw; sw = lh; sh = lw; break;
        case 180: sx = (int)W - lx - lw; sy = (int)H - ly - lh; sw = lw; sh = lh; break;
        case 270: sx = (int)H - ly - lh; sy = lx; sw = lh; sh = lw; break;
        default:  sx = lx; sy = ly; sw = lw; sh = lh; break;
        }
        glEnable(GL_SCISSOR_TEST); glScissor(sx, sy, sw, sh);
        float z = mPvWpZoom <= 0 ? 1.0f : mPvWpZoom;
        float s = fmaxf(fw / iw, fh / ih) * z;
        float dw = iw * s, dh = ih * s;
        drawIconTex(tex, fx + (fw - dw) * 0.5f, fy + (fh - dh) * 0.5f, dw, dh, 1, 1, 1, 1.0f);
        glDisable(GL_SCISSOR_TEST);
    }
    // crop frame border
    float bw = fmaxf(2.0f, H * 0.003f);
    drawQuad(fx, fy, fw, bw, 1, 1, 1, 0.9f);
    drawQuad(fx, fy + fh - bw, fw, bw, 1, 1, 1, 0.9f);
    drawQuad(fx, fy, bw, fh, 1, 1, 1, 0.9f);
    drawQuad(fx + fw - bw, fy, bw, fh, 1, 1, 1, 0.9f);
    // bottom button hints
    float hs = PFS(24.0f);
    drawText("Enter", W * 0.42f, ps3::baselineToTopY(H * 0.935f, hs), hs, 1, 1, 1, 0.95f);
    drawText("Back", W * 0.58f, ps3::baselineToTopY(H * 0.935f, hs), hs, 1, 1, 1, 0.95f);
}
void NanoMenu::pvWallpaperConfirm() {
    mPvWpMode = false;
    if (mPvIdx >= 0 && mPvIdx < (int)mPvList.size()) {
        const std::string& f = mPhotos[mPvList[mPvIdx]].file;
        property_set("persist.gammaos.nano.photo_wallpaper", f.c_str());
    }
    pvShowMsg("The wallpaper has been set.", 1100.0f);
}

// ---------------------------------------------------------------------------
// Photo playlists + the add-to-playlist chooser.
// ---------------------------------------------------------------------------
void NanoMenu::photoCreatePlaylist(const std::string& name) {
    if (name.empty()) return;
    PhotoPlaylist pl; pl.name = name;
    mPhotoPlaylists.push_back(pl);
    savePhotoConfig();
}
void NanoMenu::photoAddToPlaylist(int plIdx, const std::string& file) {
    if (plIdx < 0 || plIdx >= (int)mPhotoPlaylists.size() || file.empty()) return;
    auto& files = mPhotoPlaylists[plIdx].files;
    if (std::find(files.begin(), files.end(), file) == files.end()) files.push_back(file);
    savePhotoConfig();
}
void NanoMenu::buildPhotoPlaylistsScreen(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.title = "Playlists"; out.screenKind = 0;
    { Ps3Item it; it.label = "Create New Playlist"; it.kind = PS3_PHOTO_PL_NEW;
      it.iconTex = 0; it.nmapTex = nmapForIcon(50); it.iconR = it.iconG = it.iconB = 1.0f;
      out.items.push_back(it); }
    for (size_t p = 0; p < mPhotoPlaylists.size(); p++) {
        Ps3Item it; it.label = mPhotoPlaylists[p].name; it.kind = PS3_PHOTO_PLAYLIST; it.a = (int)p;
        size_t n = mPhotoPlaylists[p].files.size();
        char v[32]; snprintf(v, sizeof(v), "%zu %s", n, n == 1 ? "Image" : "Images"); it.value = v;
        it.iconTex = 0; it.nmapTex = nmapForIcon(62); it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
}
void NanoMenu::buildPhotoPlaylistGridList(int plIdx, std::vector<int>& out, std::string& title) {
    out.clear();
    if (plIdx < 0 || plIdx >= (int)mPhotoPlaylists.size()) { title = "Playlist"; return; }
    title = mPhotoPlaylists[plIdx].name;
    for (const auto& f : mPhotoPlaylists[plIdx].files)
        for (size_t i = 0; i < mPhotos.size(); i++)
            if (mPhotos[i].file == f) { out.push_back((int)i); break; }
}
void NanoMenu::pvOpenAddChooser(const std::string& file) {
    mPvPlChooserFile = file;
    mPvPlChooserOpts.clear();
    mPvPlChooserOpts.push_back("New Playlist...");
    for (const auto& p : mPhotoPlaylists) mPvPlChooserOpts.push_back(p.name);
    mPvPlChooserSel = 0;
    mPvPlChooserActive = true;
}
void NanoMenu::pvPlChooserMove(int dir) {
    if (!mPvPlChooserActive) return;
    int n = (int)mPvPlChooserOpts.size(); if (n <= 0) return;
    mPvPlChooserSel = (mPvPlChooserSel + dir + n) % n;
}
void NanoMenu::pvPlChooserCancel() { mPvPlChooserActive = false; }
void NanoMenu::pvPlChooserSelect() {
    if (!mPvPlChooserActive) return;
    std::string file = mPvPlChooserFile;
    int sel = mPvPlChooserSel;
    mPvPlChooserActive = false;
    if (sel == 0) {
        openOskForPassword("Enter a name for the playlist",
            [this, file](const std::string& nm) {
                if (nm.empty()) return;
                photoCreatePlaylist(nm);
                photoAddToPlaylist((int)mPhotoPlaylists.size() - 1, file);
                pvShowMsg("Added to the playlist", 900.0f);
            });
    } else {
        photoAddToPlaylist(sel - 1, file);
        pvShowMsg("Added to the playlist", 900.0f);
    }
}
void NanoMenu::drawPvPlChooser() {
    float t = mPvPlChooserAnim; if (t < 0.004f) return;
    int W = mWidth, H = mHeight;
    drawQuad(0, 0, (float)W, (float)H, 0, 0, 0, 0.5f * t);
    float fs = PFS(26.0f), lh = PSZ(0.058f);
    int n = (int)mPvPlChooserOpts.size();
    float mw = measureText("Add to Playlist", fs);
    for (auto& o : mPvPlChooserOpts) mw = fmaxf(mw, measureText(o.c_str(), fs));
    float pw = mw + PXD(0.08f), ph = lh * (n + 1) + PSZ(0.05f);
    float px = (W - pw) * 0.5f, py = (H - ph) * 0.5f;
    drawQuad(px, py, pw, ph, 0.07f, 0.08f, 0.10f, 0.92f * t);
    float cx = W * 0.5f;
    float titleY = py + PSZ(0.05f);
    float tw = measureText("Add to Playlist", fs);
    drawText("Add to Playlist", cx - tw * 0.5f, ps3::baselineToTopY(titleY, fs), fs, 1, 1, 1, 0.95f * t);
    for (int i = 0; i < n; i++) {
        float oy = titleY + lh * (i + 1);
        bool sel = (i == mPvPlChooserSel);
        if (sel) drawQuad(px + PXD(0.02f), oy - lh * 0.42f, pw - PXD(0.04f), lh * 0.82f, 1, 1, 1, 0.18f * t);
        float ow = measureText(mPvPlChooserOpts[i].c_str(), fs);
        drawText(mPvPlChooserOpts[i].c_str(), cx - ow * 0.5f, ps3::baselineToTopY(oy, fs), fs,
                 sel ? 1.0f : 0.82f, sel ? 1.0f : 0.82f, sel ? 1.0f : 0.85f, 0.95f * t);
    }
}
void NanoMenu::pvSlideshowStart(const std::vector<int>& list, int idx, int style) {
    openPhotoViewer(list, idx);
    if (!mPvActive) return;
    mPvSlideStyle = style;
    mPvSlideshow = true; mPvPaused = false;
    mPvSlideNext = mEffectTime * 1000.0f + mPvSlideMs;
    mPvHintUntil = 0.0f;
}

} // namespace android
