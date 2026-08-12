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
#include "NanoI18n.h"      // trDyn() runtime translation of hardcoded UI strings
#include "NanoMenuPS3.h"
#include "NanoMenuDrm.h"   // sDrmGlRotation / sDrmRotationDeg for the wallpaper crop scissor
#include "NanoJson.h"

#include <android/imagedecoder.h>
#include <android/bitmap.h>
#include <android/rect.h>
#include <GLES2/gl2.h>

#include <dirent.h>
#include <fcntl.h>
#include <cerrno>
#include <unistd.h>
#include <cutils/properties.h>
#include <utils/SystemClock.h>   // android::uptimeMillis() for touch-gesture timing
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
// When the control panel is summoned by a screen tap the tiny controller-sized
// icons are hard to hit, so enlarge the whole panel uniformly (icons AND their
// spacing scale together, so the layout does not reflow and the tap hit-test
// stays aligned). Controller input keeps the original size.
static const float PV_TOUCH_PANEL_SCALE = 1.55f;

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

// ---- CPU image decode (no GL) + GL upload + disk thumbnail cache --------------
// photoDecodeRGBACpu: decode `path` to tightly-packed RGBA, longest side <= maxDim
// (0 = native). NO GL calls, so it is safe on the scan / async decode worker.
static bool photoDecodeRGBACpu(const std::string& path, int maxDim,
                               int* outW, int* outH, std::vector<uint8_t>& out) {
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) return false;
    AImageDecoder* dec = nullptr;
    if (AImageDecoder_createFromFd(fd, &dec) != ANDROID_IMAGE_DECODER_SUCCESS || !dec) { close(fd); return false; }
    const AImageDecoderHeaderInfo* hi = AImageDecoder_getHeaderInfo(dec);
    int sw = AImageDecoderHeaderInfo_getWidth(hi), sh = AImageDecoderHeaderInfo_getHeight(hi);
    if (sw <= 0 || sh <= 0) { AImageDecoder_delete(dec); close(fd); return false; }
    AImageDecoder_setAndroidBitmapFormat(dec, ANDROID_BITMAP_FORMAT_RGBA_8888);
    AImageDecoder_setUnpremultipliedRequired(dec, true);   // photos are opaque
    int tw = sw, th = sh, longSide = sw > sh ? sw : sh;
    if (maxDim > 0 && longSide > maxDim) {
        float s = (float)maxDim / (float)longSide;
        tw = (int)(sw * s + 0.5f); th = (int)(sh * s + 0.5f);
        if (tw < 1) tw = 1; if (th < 1) th = 1;
        AImageDecoder_setTargetSize(dec, tw, th);
    }
    size_t stride = AImageDecoder_getMinimumStride(dec);
    std::vector<uint8_t> buf(stride * (size_t)th);
    int r = AImageDecoder_decodeImage(dec, buf.data(), stride, buf.size());
    AImageDecoder_delete(dec); close(fd);
    if (r != ANDROID_IMAGE_DECODER_SUCCESS) return false;
    out.resize((size_t)tw * th * 4);
    if (stride == (size_t)tw * 4) memcpy(out.data(), buf.data(), out.size());
    else for (int y = 0; y < th; y++) memcpy(&out[(size_t)y * tw * 4], &buf[(size_t)y * stride], (size_t)tw * 4);
    *outW = tw; *outH = th;
    return true;
}

// CPU centre-square crop decode (no GL), S x S RGBA, for the group-folder cover.
static bool photoDecodeCropRGBACpu(const std::string& path, int S, std::vector<uint8_t>& out) {
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) return false;
    AImageDecoder* dec = nullptr;
    if (AImageDecoder_createFromFd(fd, &dec) != ANDROID_IMAGE_DECODER_SUCCESS || !dec) { close(fd); return false; }
    const AImageDecoderHeaderInfo* hi = AImageDecoder_getHeaderInfo(dec);
    int sw = AImageDecoderHeaderInfo_getWidth(hi), sh = AImageDecoderHeaderInfo_getHeight(hi);
    bool ok = false;
    if (sw > 0 && sh > 0) {
        int tw, th;
        if (sw >= sh) { th = S; tw = (int)((float)S * sw / sh + 0.5f); }
        else          { tw = S; th = (int)((float)S * sh / sw + 0.5f); }
        if (tw < S) tw = S; if (th < S) th = S;
        AImageDecoder_setAndroidBitmapFormat(dec, ANDROID_BITMAP_FORMAT_RGBA_8888);
        AImageDecoder_setUnpremultipliedRequired(dec, true);
        AImageDecoder_setTargetSize(dec, tw, th);
        ARect crop; crop.left = (tw - S) / 2; crop.top = (th - S) / 2;
        crop.right = crop.left + S; crop.bottom = crop.top + S;
        AImageDecoder_setCrop(dec, crop);
        size_t stride = AImageDecoder_getMinimumStride(dec);
        std::vector<uint8_t> buf(stride * (size_t)S);
        if (AImageDecoder_decodeImage(dec, buf.data(), stride, buf.size()) == ANDROID_IMAGE_DECODER_SUCCESS) {
            out.resize((size_t)S * S * 4);
            if (stride == (size_t)S * 4) memcpy(out.data(), buf.data(), out.size());
            else for (int y = 0; y < S; y++) memcpy(&out[(size_t)y * S * 4], &buf[(size_t)y * stride], (size_t)S * 4);
            ok = true;
        }
    }
    AImageDecoder_delete(dec); close(fd);
    return ok;
}

static GLuint uploadRGBATex(const uint8_t* px, int w, int h) {
    if (!px || w <= 0 || h <= 0) return 0;
    GLuint tex = 0; glGenTextures(1, &tex); glBindTexture(GL_TEXTURE_2D, tex);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, px);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    return tex;
}

// ---- persistent disk thumbnail cache (RGB565 blobs) --------------------------
// Re-opening an album used to re-decode every 12-16MP source. The disk cache turns
// that into a tiny RGB565 read + upload. Keyed by path+mtime+size so a changed
// source auto-invalidates. RGB565 raw (not JPEG) so there is NO new dependency and
// zero decode on load. (issue: album loading slow)
static const char* kThumbCacheDir = "/data/system/nano_thumb_cache";
static const int   kThumbCacheVer = 1;
static bool sThumbCacheDirReady = false;
static void ensureThumbCacheDir() {
    if (sThumbCacheDirReady) return;
    mkdir(kThumbCacheDir, 0755);
    chmod(kThumbCacheDir, 0755);
    sThumbCacheDirReady = true;
}
static std::string photoCacheFile(const std::string& file, int64_t mtime, int64_t sz, char kind) {
    char meta[1200];
    snprintf(meta, sizeof(meta), "%c%d|%s|%lld|%lld", kind, kThumbCacheVer,
             file.c_str(), (long long)mtime, (long long)sz);
    uint64_t h = 1469598103934665603ULL;             // FNV-1a 64
    for (const char* p = meta; *p; ++p) { h ^= (uint8_t)*p; h *= 1099511628211ULL; }
    char nm[96]; snprintf(nm, sizeof(nm), "%s/%016llx.ntc", kThumbCacheDir, (unsigned long long)h);
    return nm;
}
static bool photoCacheWrite565(const std::string& file, const uint8_t* rgba, int w, int h) {
    if (!rgba || w <= 0 || h <= 0 || w > 4096 || h > 4096) return false;
    ensureThumbCacheDir();
    std::vector<uint8_t> blob(8 + (size_t)w * h * 2);
    blob[0]='N'; blob[1]='T'; blob[2]='C'; blob[3]='5';
    blob[4]=(uint8_t)(w & 0xff); blob[5]=(uint8_t)((w>>8)&0xff);
    blob[6]=(uint8_t)(h & 0xff); blob[7]=(uint8_t)((h>>8)&0xff);
    uint16_t* dst = reinterpret_cast<uint16_t*>(&blob[8]);
    for (int i = 0; i < w * h; i++) {
        uint8_t r = rgba[(size_t)i*4], g = rgba[(size_t)i*4+1], b = rgba[(size_t)i*4+2];
        dst[i] = (uint16_t)(((r & 0xf8) << 8) | ((g & 0xfc) << 3) | (b >> 3));
    }
    std::string tmp = file + ".tmp";
    int fd = open(tmp.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) return false;
    bool ok = write(fd, blob.data(), blob.size()) == (ssize_t)blob.size();
    close(fd);
    if (!ok) { unlink(tmp.c_str()); return false; }
    if (rename(tmp.c_str(), file.c_str()) != 0) { unlink(tmp.c_str()); return false; }
    return true;
}
static GLuint photoCacheRead565(const std::string& file, float* outAR) {
    int fd = open(file.c_str(), O_RDONLY);
    if (fd < 0) return 0;
    uint8_t hdr[8];
    if (read(fd, hdr, 8) != 8 || hdr[0]!='N'||hdr[1]!='T'||hdr[2]!='C'||hdr[3]!='5') { close(fd); return 0; }
    int w = hdr[4] | (hdr[5]<<8), h = hdr[6] | (hdr[7]<<8);
    if (w <= 0 || h <= 0 || w > 4096 || h > 4096) { close(fd); return 0; }
    std::vector<uint8_t> px((size_t)w * h * 2);
    bool ok = read(fd, px.data(), px.size()) == (ssize_t)px.size();
    close(fd);
    if (!ok) return 0;
    utimensat(AT_FDCWD, file.c_str(), nullptr, 0);   // LRU touch (read = recently used)
    GLuint tex = 0; glGenTextures(1, &tex); glBindTexture(GL_TEXTURE_2D, tex);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 2);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, w, h, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, px.data());
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    if (outAR) *outAR = (h > 0) ? (float)w / (float)h : 1.0f;
    return tex;
}
// --- Video custom icon cache (Change Icon). Reuses the photo thumb-cache dir + RGB565 blob
// format, keyed on the video PATH only (kind 'v', mtime/sz 0) so a user-set icon survives an
// mtime/size change. Defined here so they can reach the file-static photoCache* helpers. ---
bool NanoMenu::videoIconWrite(const std::string& videoPath, const uint8_t* rgba, int w, int h) {
    if (videoPath.empty()) return false;
    return photoCacheWrite565(photoCacheFile(videoPath, 0, 0, 'v'), rgba, w, h);
}
GLuint NanoMenu::videoIconRead(const std::string& videoPath) {
    if (videoPath.empty()) return 0;
    return photoCacheRead565(photoCacheFile(videoPath, 0, 0, 'v'), nullptr);
}
// True if this video already has a 'v' poster blob on disk. Defined here (with the rest of the video-icon
// helpers) so it can reach the file-static photoCacheFile; the auto-thumbnail machine calls it to decide
// which videos still need a one-off frame decode.
bool NanoMenu::videoIconExists(const std::string& videoPath) {
    if (videoPath.empty()) return false;
    return access(photoCacheFile(videoPath, 0, 0, 'v').c_str(), F_OK) == 0;
}
// LRU-bound the cache dir to ~96 MB (oldest-mtime first). Runs on the scan thread.
static void photoThumbCacheGc() {
    DIR* d = opendir(kThumbCacheDir); if (!d) return;
    struct Ent { std::string path; off_t sz; time_t mt; };
    std::vector<Ent> ents; long long total = 0;
    struct dirent* e;
    while ((e = readdir(d)) != nullptr) {
        if (e->d_name[0] == '.') continue;
        std::string p = std::string(kThumbCacheDir) + "/" + e->d_name;
        struct stat st; if (stat(p.c_str(), &st) != 0 || !S_ISREG(st.st_mode)) continue;
        ents.push_back({p, st.st_size, st.st_mtime}); total += (long long)st.st_size;
    }
    closedir(d);
    const long long cap = 96LL * 1024 * 1024;
    if (total <= cap) return;
    std::sort(ents.begin(), ents.end(), [](const Ent& a, const Ent& b){ return a.mt < b.mt; });
    for (auto& en : ents) { if (total <= cap) break; if (unlink(en.path.c_str()) == 0) total -= (long long)en.sz; }
}

GLuint NanoMenu::photoDecodeTex(const std::string& path, int maxDim, int* outW, int* outH) {
    int w = 0, h = 0; std::vector<uint8_t> px;
    if (!photoDecodeRGBACpu(path, maxDim, &w, &h, px)) return 0;
    GLuint tex = uploadRGBATex(px.data(), w, h);
    if (outW) *outW = w; if (outH) *outH = h;
    return tex;
}

// Decode an in-memory image (any AImageDecoder format) to a GL texture, longest
// side capped at maxDim. Shared by embedded-art extraction.
static GLuint decodeBufferTex(const uint8_t* data, size_t len, int maxDim) {
    AImageDecoder* dec = nullptr;
    if (AImageDecoder_createFromBuffer(data, len, &dec) != ANDROID_IMAGE_DECODER_SUCCESS || !dec) return 0;
    const AImageDecoderHeaderInfo* hi = AImageDecoder_getHeaderInfo(dec);
    int sw = AImageDecoderHeaderInfo_getWidth(hi), sh = AImageDecoderHeaderInfo_getHeight(hi);
    GLuint tex = 0;
    if (sw > 0 && sh > 0) {
        AImageDecoder_setAndroidBitmapFormat(dec, ANDROID_BITMAP_FORMAT_RGBA_8888);
        AImageDecoder_setUnpremultipliedRequired(dec, true);
        int tw = sw, th = sh, longSide = sw > sh ? sw : sh;
        if (maxDim > 0 && longSide > maxDim) {
            float s = (float)maxDim / (float)longSide;
            tw = (int)(sw * s + 0.5f); th = (int)(sh * s + 0.5f);
            if (tw < 1) tw = 1; if (th < 1) th = 1;
            AImageDecoder_setTargetSize(dec, tw, th);
        }
        size_t stride = AImageDecoder_getMinimumStride(dec);
        std::vector<uint8_t> buf(stride * (size_t)th);
        if (AImageDecoder_decodeImage(dec, buf.data(), stride, buf.size()) == ANDROID_IMAGE_DECODER_SUCCESS) {
            const uint8_t* px = buf.data();
            std::vector<uint8_t> packed;
            if (stride != (size_t)tw * 4) {
                packed.resize((size_t)tw * th * 4);
                for (int y = 0; y < th; y++) memcpy(&packed[(size_t)y * tw * 4], &buf[(size_t)y * stride], (size_t)tw * 4);
                px = packed.data();
            }
            glGenTextures(1, &tex); glBindTexture(GL_TEXTURE_2D, tex);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, tw, th, 0, GL_RGBA, GL_UNSIGNED_BYTE, px);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        }
    }
    AImageDecoder_delete(dec);
    return tex;
}

// Decode an in-memory image to RGBA pixels rather than to a texture.
//
// Same work as decodeBufferTex minus the GL calls, because the album-art worker runs off the
// render thread and a GL context belongs to exactly one thread. The worker produces pixels; the
// render thread uploads them (mpDrainAlbumArt).
static bool decodeBufferRGBA(const uint8_t* data, size_t len, int maxDim,
                             int* outW, int* outH, std::vector<uint8_t>* out) {
    AImageDecoder* dec = nullptr;
    if (AImageDecoder_createFromBuffer(data, len, &dec) != ANDROID_IMAGE_DECODER_SUCCESS || !dec)
        return false;
    const AImageDecoderHeaderInfo* hi = AImageDecoder_getHeaderInfo(dec);
    int sw = AImageDecoderHeaderInfo_getWidth(hi), sh = AImageDecoderHeaderInfo_getHeight(hi);
    bool ok = false;
    if (sw > 0 && sh > 0) {
        AImageDecoder_setAndroidBitmapFormat(dec, ANDROID_BITMAP_FORMAT_RGBA_8888);
        AImageDecoder_setUnpremultipliedRequired(dec, true);
        int tw = sw, th = sh, longSide = sw > sh ? sw : sh;
        if (maxDim > 0 && longSide > maxDim) {
            float s = (float)maxDim / (float)longSide;
            tw = (int)(sw * s + 0.5f); th = (int)(sh * s + 0.5f);
            if (tw < 1) tw = 1; if (th < 1) th = 1;
            AImageDecoder_setTargetSize(dec, tw, th);
        }
        size_t stride = AImageDecoder_getMinimumStride(dec);
        std::vector<uint8_t> buf(stride * (size_t)th);
        if (AImageDecoder_decodeImage(dec, buf.data(), stride, buf.size())
                == ANDROID_IMAGE_DECODER_SUCCESS) {
            out->resize((size_t)tw * th * 4);
            for (int y = 0; y < th; y++)
                memcpy(&(*out)[(size_t)y * tw * 4], &buf[(size_t)y * stride], (size_t)tw * 4);
            *outW = tw; *outH = th;
            ok = true;
        }
    }
    AImageDecoder_delete(dec);
    return ok;
}

// The ID3v2 APIC cover as pixels. Same parse as musicEmbeddedArt below; kept separate so the
// worker can call it without a GL context.
bool NanoMenu::musicEmbeddedArtPixels(const std::string& path, int maxDim,
                                      int* outW, int* outH, std::vector<uint8_t>* out) {
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) return false;
    unsigned char hdr[10];
    if (read(fd, hdr, 10) != 10 || hdr[0] != 'I' || hdr[1] != 'D' || hdr[2] != '3') {
        close(fd); return false;
    }
    int ver = hdr[3];
    int tagSize = (hdr[6] << 21) | (hdr[7] << 14) | (hdr[8] << 7) | hdr[9];
    // A tag is pulled over the network on a share, so cap it far tighter than the 30MB the
    // texture path allowed: a cover worth showing at 256px is never megabytes.
    if (tagSize <= 10 || tagSize > 4 * 1024 * 1024) { close(fd); return false; }
    std::vector<unsigned char> tag(tagSize);
    ssize_t got = read(fd, tag.data(), tagSize);
    close(fd);
    if (got != (ssize_t)tagSize) return false;
    size_t i = 0;
    while (i + 10 <= (size_t)tagSize) {
        const unsigned char* f = &tag[i];
        if (f[0] == 0) break;
        char id[5] = {(char)f[0], (char)f[1], (char)f[2], (char)f[3], 0};
        uint32_t fsize = (ver == 4) ? ((f[4] << 21) | (f[5] << 14) | (f[6] << 7) | f[7])
                                    : ((f[4] << 24) | (f[5] << 16) | (f[6] << 8) | f[7]);
        size_t fdata = i + 10;
        if (fsize == 0 || fdata + fsize > (size_t)tagSize) break;
        if (!strcmp(id, "APIC")) {
            const unsigned char* d = &tag[fdata];
            size_t n = fsize, p = 0;
            unsigned char enc = (p < n) ? d[p++] : 0;
            while (p < n && d[p] != 0) p++; if (p < n) p++;
            if (p < n) p++;
            if (enc == 1 || enc == 2) { while (p + 1 < n && !(d[p] == 0 && d[p + 1] == 0)) p += 2; p += 2; }
            else { while (p < n && d[p] != 0) p++; if (p < n) p++; }
            if (p < n) return decodeBufferRGBA(d + p, n - p, maxDim, outW, outH, out);
            break;
        }
        i = fdata + fsize;
    }
    return false;
}

// Extract the embedded ID3v2 APIC cover from an MP3 (so music folders show the
// album art even when there is no cover file beside the tracks).
GLuint NanoMenu::musicEmbeddedArt(const std::string& path, int maxDim) {
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) return 0;
    unsigned char hdr[10];
    if (read(fd, hdr, 10) != 10 || hdr[0] != 'I' || hdr[1] != 'D' || hdr[2] != '3') { close(fd); return 0; }
    int ver = hdr[3];   // 3 = v2.3, 4 = v2.4
    int tagSize = (hdr[6] << 21) | (hdr[7] << 14) | (hdr[8] << 7) | hdr[9];   // synchsafe
    if (tagSize <= 10 || tagSize > 30 * 1024 * 1024) { close(fd); return 0; }
    std::vector<unsigned char> tag(tagSize);
    ssize_t got = read(fd, tag.data(), tagSize);
    close(fd);
    if (got != (ssize_t)tagSize) return 0;
    GLuint tex = 0;
    size_t i = 0;
    while (i + 10 <= (size_t)tagSize) {
        const unsigned char* f = &tag[i];
        if (f[0] == 0) break;   // padding
        char id[5] = {(char)f[0], (char)f[1], (char)f[2], (char)f[3], 0};
        uint32_t fsize = (ver == 4) ? ((f[4] << 21) | (f[5] << 14) | (f[6] << 7) | f[7])
                                    : ((f[4] << 24) | (f[5] << 16) | (f[6] << 8) | f[7]);
        size_t fdata = i + 10;
        if (fsize == 0 || fdata + fsize > (size_t)tagSize) break;
        if (!strcmp(id, "APIC")) {
            const unsigned char* d = &tag[fdata];
            size_t n = fsize, p = 0;
            unsigned char enc = (p < n) ? d[p++] : 0;     // text encoding
            while (p < n && d[p] != 0) p++; if (p < n) p++;   // skip mime (latin1, null-term)
            if (p < n) p++;                                    // picture type byte
            if (enc == 1 || enc == 2) { while (p + 1 < n && !(d[p] == 0 && d[p + 1] == 0)) p += 2; p += 2; }
            else { while (p < n && d[p] != 0) p++; if (p < n) p++; }   // skip description
            if (p < n) tex = decodeBufferTex(d + p, n - p, maxDim);
            break;
        }
        i = fdata + fsize;
    }
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
    mPhotoCfgLoadErr = false;
    int fd = open(path, O_RDONLY);
    if (fd < 0) { mPhotoCfgStamp = -1; return false; }   // absent: clean start, safe to save
    std::string content;
    char b[4096]; ssize_t n;
    bool readErr = false;
    while ((n = read(fd, b, sizeof(b))) > 0) content.append(b, n);
    if (n < 0) readErr = true;                            // read failed mid-stream: content is partial
    close(fd);
    mPhotoCfgStamp = photoConfigStamp();
    if (readErr) {
        mPhotoCfgLoadErr = true;                          // never let a save clobber the on-disk file
        ALOGW("NanoMenu: nano_photo.json read failed; refusing to overwrite (no data loss)");
        return false;
    }
    njson::Value root;
    if (!njson::parse(content, &root) || !root.isObject()) {
        if (!content.empty()) {                           // corrupt/partial (not just an empty file): keep it
            mPhotoCfgLoadErr = true;
            ALOGW("NanoMenu: nano_photo.json parse failed; refusing to overwrite (no data loss)");
        }
        return false;
    }
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
    // Never clobber good on-disk data if the last load failed (see saveScrapeIndex).
    if (mPhotoCfgLoadErr) {
        ALOGW("NanoMenu: NOT saving nano_photo.json - prior load failed (avoiding data loss)");
        return;
    }
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
    // Defaults live on storage that mounts at/after boot, so always wait for boot.
    char bc[PROPERTY_VALUE_MAX] = {0};
    property_get("sys.boot_completed", bc, "0");
    if (bc[0] != '1') return false;
    std::vector<std::string> dirs = nanoMediaScanDirs(0, mPhotoFolders);
    if (dirs.empty()) return true;   // nothing to scan (worker guards against wiping)
    for (const auto& f : dirs) {
        // A scan folder on a network share counts as ready without being stat-ed. This runs from
        // the main loop every frame while a scan is pending, so probing a share here is a round
        // trip to the server per frame - and the mount existing is already the answer.
        if (f.rfind("/mnt/shares/", 0) == 0) return true;
        struct stat st;
        if (stat(f.c_str(), &st) == 0 && S_ISDIR(st.st_mode)) return true;
    }
    return false;
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
    if (!mPhotoScanRunning) photoScanAsync();   // always: default media dirs are scanned too
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
    std::vector<std::string> folders = nanoMediaScanDirs(0, mPhotoFolders);   // user folders + default media dirs
    bool forceReprobe = (mPhotoCfgVersion < kPhotoMetaVersion);
    std::vector<PhotoItem> cacheVec = mPhotos;
    std::map<std::string, const PhotoItem*> cache;
    if (!forceReprobe) for (const auto& it : cacheVec) cache[it.file] = &it;

    std::vector<std::string> files;
    for (const auto& f : folders) scanPhotosRecursive(f, files, 0);
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
            mPhotoScanRunning = false; mPhotoScanPending = true;
            ALOGI("NanoMenu: photo scan found nothing + a source is unreadable; deferring");
            return;
        }
    }
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
    // Snapshot the cache keys before `results` is moved, so the warm pass below can
    // run without touching shared state.
    struct WarmKey { std::string file; int64_t mtime; int64_t sz; };
    std::vector<WarmKey> warm; warm.reserve(results.size());
    for (auto& p : results) warm.push_back({p.file, p.mtime, p.sz});
    {
        std::lock_guard<std::mutex> lk(mPhotoScanMutex);
        mPhotoScanResults = std::move(results);
        mPhotoScanReady = true;
    }
    mPhotoScanRunning = false;
    ALOGI("NanoMenu: photo scan finished (%zu images)", files.size());
    // Warm the on-disk thumbnail cache off the render thread so the FIRST album open
    // after a scan is fast too (decode + RGB565-write each missing thumb). Throttled
    // so it never competes with the live wave render; skips already-cached entries.
    for (auto& wk : warm) {
        if (mPhotoScanRunning) break;   // a newer scan started; defer to it
        std::string cf = photoCacheFile(wk.file, wk.mtime, wk.sz, 't');
        if (access(cf.c_str(), F_OK) == 0) continue;
        int dw = 0, dh = 0; std::vector<uint8_t> px;
        if (photoDecodeRGBACpu(wk.file, 256, &dw, &dh, px))
            photoCacheWrite565(cf, px.data(), dw, dh);
        usleep(4000);
    }
    photoThumbCacheGc();
}

void NanoMenu::photoDrainScanResults() {
    if (!mPhotoScanReady) return;
    {
        std::lock_guard<std::mutex> lk(mPhotoScanMutex);
        mPhotos = std::move(mPhotoScanResults);
        mPhotoScanResults.clear();
        mPhotoScanReady = false;
    }
    photoFreeCovers();   // photo indices changed -> the cover cache is stale
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
      it.iconTex = iconTexForIcon(50); it.nmapTex = nmapForIcon(50); it.iconR = it.iconG = it.iconB = 1.0f;
      out.items.push_back(it); }
    { Ps3Item it; it.label = mPhotoScanRunning ? "Refreshing..." : "Refresh";
      it.kind = PS3_PHOTO_REFRESH;
      it.value = mPhotoScanRunning ? "" : "Rescan photo folders";
      it.iconTex = iconTexForIcon(8); it.nmapTex = nmapForIcon(8); it.iconR = it.iconG = it.iconB = 1.0f;
      out.items.push_back(it); }
    for (size_t i = 0; i < mPhotoFolders.size(); i++) {
        int cnt = 0;
        for (const auto& p : mPhotos)
            if (p.file.compare(0, mPhotoFolders[i].size(), mPhotoFolders[i]) == 0) cnt++;
        Ps3Item it; it.label = mPhotoFolders[i]; it.kind = PS3_PHOTO_FOLDER_ROW; it.a = (int)i;
        char v[24]; snprintf(v, sizeof(v), "%d images", cnt); it.value = v;
        it.iconTex = iconTexForIcon(62); it.nmapTex = nmapForIcon(62); it.iconR = it.iconG = it.iconB = 1.0f;
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
    // Sort By the current field+dir (web photoSortBy: film/import date desc/asc, name).
    std::sort(order.begin(), order.end(), [&](int a, int b) { return photoSortLess(a, b); });
    static const char* MON[12] = {"Jan","Feb","Mar","Apr","May","Jun",
                                  "Jul","Aug","Sep","Oct","Nov","Dec"};
    std::vector<PhotoGroup> out;
    if (order.empty()) return out;
    // Folder view (Y toggle): bucket by the photo's parent directory, preserving the sorted order
    // within each folder. Overrides the date/album grouping while it is on.
    if (mPhotoFolderView) {
        auto dirOf = [](const std::string& p){ size_t s = p.rfind('/'); return s == std::string::npos ? std::string() : p.substr(0, s); };
        auto baseOf = [](const std::string& p){ size_t s = p.rfind('/'); return s == std::string::npos ? p : p.substr(s + 1); };
        std::map<std::string, std::vector<int>> m; std::vector<std::string> keys;
        for (int i : order) { std::string d = dirOf(mPhotos[i].file);
            if (m.find(d) == m.end()) keys.push_back(d); m[d].push_back(i); }
        for (const auto& k : keys) { PhotoGroup g; g.name = baseOf(k); if (g.name.empty()) g.name = "/"; g.idx = m[k]; out.push_back(g); }
        return out;
    }
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
        it.b = groups[a].idx.empty() ? -1 : groups[a].idx[0];   // cover = the group's first photo
        size_t n = groups[a].idx.size();
        char v[32]; snprintf(v, sizeof(v), "%zu %s", n, n == 1 ? "Image" : "Images"); it.value = v;
        it.iconTex = iconTexForIcon(62); it.nmapTex = folderNmap; it.iconR = it.iconG = it.iconB = 1.0f;
        out.push_back(it);
    }
}

// Compare two photo indices by the current Sort By field+dir (web photoSortBy).
// field 0 = film(EXIF) date, 1 = import(file mtime) date, 2 = image name.
bool NanoMenu::photoSortLess(int a, int b) const {
    if (a < 0 || a >= (int)mPhotos.size() || b < 0 || b >= (int)mPhotos.size()) return a < b;
    auto nameLess = [&]() {
        const std::string& x = mPhotos[a].name, &y = mPhotos[b].name;
        if (x.size() != y.size()) return x.size() < y.size();   // numeric-aware for digit names
        return strcasecmp(x.c_str(), y.c_str()) < 0;
    };
    if (mPhotoSortField == 2) return nameLess();   // Image Name: always ascending
    if (mPhotoSortField == 1) {                    // Import Date: file mtime
        if (mPhotos[a].mtime != mPhotos[b].mtime)
            return mPhotoSortDir == 0 ? (mPhotos[a].mtime > mPhotos[b].mtime)
                                      : (mPhotos[a].mtime < mPhotos[b].mtime);
        return nameLess();
    }
    const std::string& x = mPhotos[a].date, &y = mPhotos[b].date;   // Film Date: EXIF/capture date
    if (x != y) return mPhotoSortDir == 0 ? (x > y) : (x < y);
    return nameLess();
}

std::string NanoMenu::photoSortLabelCur() const {
    const char* arrow = trDyn((mPhotoSortDir == 0) ? " (newest)" : " (oldest)");
    switch (mPhotoSortField) {
        case 2:  return trDyn("Image Name");
        case 1:  return std::string(trDyn("Import Date")) + arrow;
        default: return std::string(trDyn("Film Date")) + arrow;
    }
}

// Re-group the Photo column with the new order, and re-sort the open album grid in place.
void NanoMenu::photoApplySort() {
    mPhotoCatsStale = true;
    if (!mPhotoGridList.empty()) {
        std::sort(mPhotoGridList.begin(), mPhotoGridList.end(),
                  [&](int a, int b) { return photoSortLess(a, b); });
        mPhotoGridCursor = 0; mPhotoGridTop = 0;
    }
}

void NanoMenu::photoSetSort(int field, int dir) {
    mPhotoSortField = field;
    mPhotoSortDir = (field == 2) ? 1 : dir;   // name forced ascending
    photoApplySort();
    photoShowBanner(photoSortLabelCur());
}

// Y on the grid/folder: step through the 5 firmware Sort By options + show a banner.
// Order: Film Date desc, Film Date asc, Import Date desc, Import Date asc, Image Name.
void NanoMenu::photoSortCycleY() {
    static const int kField[5] = {0, 0, 1, 1, 2};
    static const int kDir[5]   = {0, 1, 0, 1, 1};
    // Folder View is the terminal step of the Y cycle (user request: "folder view toggled by Y").
    // The cycle is: 5 sort orders -> Folder View -> back to the first sort order.
    if (mPhotoFolderView) {                       // leave folder view -> first sort order
        mPhotoFolderView = false;
        property_set("persist.gammaos.nano.photo.folderview", "0");
        photoSetSort(kField[0], kDir[0]);         // rebuilds + shows the sort banner
        return;
    }
    int cur = 0;
    for (int i = 0; i < 5; i++)
        if (kField[i] == mPhotoSortField && (mPhotoSortField == 2 || kDir[i] == mPhotoSortDir)) { cur = i; break; }
    if (cur == 4) { photoToggleFolderView(); return; }   // last sort order -> enter folder view
    int nx = cur + 1;
    photoSetSort(kField[nx], kDir[nx]);
}

void NanoMenu::photoShowBanner(const std::string& text) {
    mPhotoBanner = text; mPhotoBannerStart = mEffectTime;
}

// Transient confirmation toast (Setting changed / Pinned to Home / Background apps
// closed / sort + folder labels). Modelled on the drastic-nano RetroAchievements
// unlock banner: a compact card in the top-right that slides in from the edge,
// holds, then slides out with a fade. Long labels marquee-scroll instead of
// blowing up the card. Sized off the smaller screen dimension + user font scale so
// it adapts to any resolution, aspect and orientation, and it is themed per active
// home theme (XMB glass / DSi panel / Minima). No-op when inactive.
void NanoMenu::drawPhotoBanner() {
    if (mPhotoBannerStart < 0.0f || mPhotoBanner.empty()) return;
    float el = (mEffectTime - mPhotoBannerStart) * 1000.0f;
    const float life = 2600.0f;   // a touch longer so an overflowing label can marquee once
    if (el >= life) { mPhotoBannerStart = -1.0f; mDisplayDirty = true; return; }
    // Keep repainting while the toast is up so it animates + auto-dismisses on the static DSi / Minima
    // themes (they only redraw on mDisplayDirty; the XMB wave repaints anyway).
    mDisplayDirty = true;

    // Draw with the standard font, NOT the caller's theme text state. The DSi carousel renders with
    // mNdsFontPref=true (DSVec faces); measureText/drawText for the toast string then return ~0 width,
    // so the toast collapsed to an unreadable sliver in the DSi theme (the Y-sort banner bug). Force the
    // default font for the measure AND the draw so widths and glyphs match, then restore.
    const bool prevFont = mNdsFontPref; mNdsFontPref = false;
    const int prevOutline = mTextOutlineMode; mTextOutlineMode = 1;
    setUiBlend();

    const float W = (float)mWidth, H = (float)mHeight;
    const float mn = fminf(W, H);
    const char* txt = mPhotoBanner.c_str();

    // Slide-in / hold / slide-out with a matching alpha fade (ease-out cubic).
    auto easeOut = [](float p){ float q = 1.0f - p; return 1.0f - q * q * q; };
    auto clamp01 = [](float v){ return fminf(fmaxf(v, 0.0f), 1.0f); };
    const float inMs = 240.0f, outMs = 320.0f;
    float vis = 1.0f;
    if (el < inMs) vis = easeOut(clamp01(el / inMs));
    const float remain = life - el;
    if (remain < outMs) vis = fminf(vis, clamp01(remain / outMs));
    const float alpha = vis;

    // Compact drastic-nano RA-card sizing. CRUCIAL: ps3::fontScale() returns a drawText *scale
    // multiplier* (~2-4), NOT a pixel height. Card geometry must be in PIXELS, so derive the on-screen
    // pixel height (basePx, incl. the user Font Size uf) and size the card from that; fs is used only for
    // measureText/drawText. (The old code used the scale as pixels, so the card collapsed to a ~7px sliver
    // that looked like a stray underline under full-height text.)
    const float uf     = ps3::gFontScale;                     // user Font Size (drawText/measureText apply it)
    const float basePx = fmaxf(mn * 0.030f, 20.0f);           // pre-user-scale text height in device px
    const float fs     = ps3::fontScale(basePx);              // scale for that pixel height
    const float fpx    = basePx * uf;                         // actual rendered text pixel height
    const float padX   = fpx * 0.80f;
    const float padY   = fpx * 0.52f;
    const float margin = fmaxf(mn * 0.03f, 16.0f);            // keep clear of any overscan edge

    // Text column caps the card at ~44% of the width; anything longer marquees inside the card.
    const float textColW = fmaxf(fpx * 3.0f, W * 0.44f - padX * 2.0f);
    const float fullTw = measureText(txt, fs);
    const float colW = fminf(fullTw, textColW);
    const float cardW = padX + colW + padX;
    const float cardH = fpx + padY * 2.0f;

    // Per-theme palette: card fill, ink, accent (left edge) and a thin frame.
    float fR, fG, fB, fA;            // fill
    float iR, iG, iB;               // ink
    float aR, aG, aB;               // accent
    float frR, frG, frB, frA;       // frame
    if (mNdsTheme) {
        NdsPal p = ndsPal();
        fR = fG = fB = p.field; fA = 0.98f;             // light near-white / dark near-black panel
        iR = iG = iB = p.ink;                            // dark ink on light, light ink on dark
        aR = p.headR; aG = p.headG; aB = p.headB;        // DSi teal accent
        frR = frG = frB = mNdsDark ? 0.42f : p.edge; frA = 0.85f;
    } else if (mMinimaTheme) {
        float ar, ag, ab; minimaAccent(ar, ag, ab);
        fR = 0.09f; fG = 0.10f; fB = 0.12f; fA = 0.94f;  // clean dark card reads on any Minima bg
        iR = iG = iB = 0.97f;
        aR = ar; aG = ag; aB = ab;
        frR = 0.90f; frG = 0.92f; frB = 1.0f; frA = 0.22f;
    } else {
        // XMB: dark glass card with the theme accent (custom colour if the user set one).
        float ar = 0.45f, ag = 0.78f, ab = 1.0f;
        customAccentRGB(ar, ag, ab);
        fR = 0.06f; fG = 0.07f; fB = 0.11f; fA = 0.92f;
        iR = iG = iB = 1.0f;
        aR = ar; aG = ag; aB = ab;
        frR = 0.90f; frG = 0.93f; frB = 1.0f; frA = 0.22f;
    }

    (void)frR; (void)frG; (void)frB; (void)frA; (void)padY;

    // Final bar geometry + text metrics. On the XMB home the clock/status bar publishes its exact rect
    // (mPs3ClockBar*, refreshed every frame by drawPs3Clock); mirror it so the toast is a matching bar
    // directly BELOW the clock - same right edge, width, height and corner - which is the cleanest,
    // most native placement. Elsewhere (video player, DSi/Minima themes, clock hidden) fall back to the
    // compact top-right card sized to the text.
    const bool clockBarFresh = mPs3Xmb && !mNdsTheme && !mMinimaTheme
                               && mPs3ClockBarStamp >= 0.0f && (mEffectTime - mPs3ClockBarStamp) < 0.5f
                               && (mPs3ClockBarR - mPs3ClockBarL) > mn * 0.10f
                               && (mPs3ClockBarBot - mPs3ClockBarTop) > 4.0f;
    float bx, by, bW, bH, bRad, tPadX, bFs, tColW, baseY;
    if (clockBarFresh) {
        // COVER the clock bar at its exact rect; slide in from the right. Text uses the clock's own font
        // size and baseline so it lines up perfectly (vertically centred) with where the clock time sits.
        bW = mPs3ClockBarR - mPs3ClockBarL;
        bH = mPs3ClockBarBot - mPs3ClockBarTop;
        by = mPs3ClockBarTop;
        bx = mPs3ClockBarL + (1.0f - vis) * (bW + bH);
        bRad = mPs3ClockBarCorner;
        bFs   = ps3::fontScale(ps3::CLOCK_SIZE);
        tPadX = bH * 0.42f;
        tColW = bW - tPadX * 2.0f;
        baseY = mPs3ClockBarBaseY;
    } else {
        bW = cardW; bH = cardH; bRad = fpx * 0.42f;
        bx = (W - cardW - margin) + (1.0f - vis) * (cardW + margin);
        by = fmaxf(mn * 0.130f, 64.0f);
        bFs = fs; tPadX = padX; tColW = colW;
        baseY = by + bH * 0.5f + fpx * 0.34f;
    }

    // Frosted-glass body: an accent-tinted rounded frame at the OUTER extent (== the clock rect when
    // covering, so the toast is exactly the clock bar's size), a dark glass base opaque enough to hide
    // the clock underneath, a translucent light sheen over the top for the frosted look, and a bright
    // top edge. drawRoundedRect is rotation-aware (uRotation = sDrmRotMat) exactly like drawText.
    const float bwF = fmaxf(2.0f, bH * 0.05f);
    float oX, oY, oW, oH, iX, iY, iW, iH;
    if (clockBarFresh) { oX = bx; oY = by; oW = bW; oH = bH;
                         iX = bx + bwF; iY = by + bwF; iW = bW - bwF * 2.0f; iH = bH - bwF * 2.0f; }
    else               { oX = bx - bwF; oY = by - bwF; oW = bW + bwF * 2.0f; oH = bH + bwF * 2.0f;
                         iX = bx; iY = by; iW = bW; iH = bH; }
    const float iRad = fmaxf(1.0f, bRad - bwF);
    drawRoundedRect(oX, oY, oW, oH, bRad, aR, aG, aB, 0.90f * alpha);                              // accent frame
    drawRoundedRect(iX, iY, iW, iH, iRad, fR, fG, fB, fA * alpha);                                 // dark glass base
    // Frosted sheen: a light wash over the whole body + a brighter band across the top half (fake the
    // glassy gradient without a real blur), then a crisp bright top edge line.
    drawRoundedRect(iX, iY, iW, iH, iRad, 1.0f, 1.0f, 1.0f, 0.05f * alpha);
    drawRoundedRect(iX, iY, iW, iH * 0.50f, iRad, 1.0f, 1.0f, 1.0f, 0.07f * alpha);
    drawRoundedRect(iX + iRad * 0.6f, iY + bwF * 0.6f, iW - iRad * 1.2f,
                    fmaxf(1.5f, bH * 0.035f), fmaxf(1.0f, bH * 0.025f), 1.0f, 1.0f, 1.0f, 0.22f * alpha);

    // Marquee: when the label overflows its column, window a fitting substring that scrolls, wrapping
    // with a gap (mirrors the drastic-nano toast ticker; no GL clip needed, works at any opacity).
    std::string shown = txt;
    if (measureText(txt, bFs) > tColW + 0.5f) {
        std::string scroll = std::string(txt) + "     ";
        int n = (int)scroll.size();
        int shift = (int)((((long)(el / 220.0f)) % n + n) % n);
        std::string rot = scroll.substr(shift) + scroll.substr(0, shift);
        std::string vs;
        for (size_t i = 0; i < rot.size(); i++) {
            std::string cand = vs; cand += rot[i];
            if (measureText(cand.c_str(), bFs) > tColW) break;
            vs = cand;
        }
        shown = vs;
    }
    const float tx = bx + tPadX;
    drawText(shown.c_str(), tx, ps3::baselineToTopY(baseY, bFs), bFs, iR, iG, iB, alpha);

    mNdsFontPref = prevFont;
    mTextOutlineMode = prevOutline;
}
void NanoMenu::photoCycleGroup() {
    static const char* kModeNames[4] = {"By Month", "By Year", "By Album", "All"};
    mPhotoGroupIdx = (mPhotoGroupIdx + 1) % 4;
    mPhotoCatsStale = true;
    photoShowBanner(trDyn(kModeNames[mPhotoGroupIdx]));
    ALOGI("NanoMenu: photo group -> %s", kModeNames[mPhotoGroupIdx]);
}
// Set a specific group-content mode (option-menu Group Content submenu).
void NanoMenu::photoSetGroup(int mode) {
    if (mode < 0 || mode > 3) return;
    static const char* kModeNames[4] = {"By Month", "By Year", "By Album", "All"};
    mPhotoGroupIdx = mode;
    mPhotoCatsStale = true;
    photoShowBanner(trDyn(kModeNames[mPhotoGroupIdx]));
}
// Y on the Photo column: toggle folder view (group by parent directory vs the date/album grouping),
// persist it, rebuild the column, and flash a banner. Sort stays in the Triangle option menu.
void NanoMenu::photoToggleFolderView() {
    mPhotoFolderView = !mPhotoFolderView;
    property_set("persist.gammaos.nano.photo.folderview", mPhotoFolderView ? "1" : "0");
    mPhotoCatsStale = true;
    photoShowBanner(trDyn(mPhotoFolderView ? "Folder View" : "Grouped View"));
}

// ---------------------------------------------------------------------------
// Thumbnail grid (screenKind PHOTO_GRID, modeled on renderIconGridPicker).
// ---------------------------------------------------------------------------
static const int PG_COLS = 5;

GLuint NanoMenu::photoThumb(int photoIdx) {
    auto it = mPhotoThumbCache.find(photoIdx);
    if (it != mPhotoThumbCache.end()) return it->second;
    if (photoIdx < 0 || photoIdx >= (int)mPhotos.size()) return 0;
    const PhotoItem& p = mPhotos[photoIdx];
    // Disk-cache fast path: a tiny RGB565 read + upload, no multi-MP decode.
    std::string cf = photoCacheFile(p.file, p.mtime, p.sz, 't');
    float ar = 1.0f;
    GLuint tex = photoCacheRead565(cf, &ar);
    if (tex) { mPhotoThumbCache[photoIdx] = tex; mPhotoThumbAR[photoIdx] = ar; return tex; }
    // Cold: decode the source once, upload, and persist the thumb for next time.
    int w = 0, h = 0; std::vector<uint8_t> px;
    if (photoDecodeRGBACpu(p.file, 256, &w, &h, px)) {
        tex = uploadRGBATex(px.data(), w, h);
        photoCacheWrite565(cf, px.data(), w, h);
    }
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

// Group-folder cover: a 160px centre-square crop of the group's first photo,
// drawn as the column icon (mirrors the Music album art). Cached by photo index;
// dropped on rescan since indices change.
bool NanoMenu::photoDecodeCoverPixels(const std::string& path, int s, std::vector<uint8_t>* out) {
    return photoDecodeCropRGBACpu(path, s, *out);
}
GLuint NanoMenu::photoUploadCover(const uint8_t* px, int w, int h) {
    return uploadRGBATex(px, w, h);
}
void NanoMenu::photoWriteCoverCache(const std::string& file, const uint8_t* px, int w, int h) {
    photoCacheWrite565(file, px, w, h);
}

GLuint NanoMenu::photoGroupCover(int photoIdx) {
    auto cit = mPhotoCoverCache.find(photoIdx);
    if (cit != mPhotoCoverCache.end()) return cit->second;
    GLuint tex = 0;
    if (photoIdx >= 0 && photoIdx < (int)mPhotos.size()) {
        const PhotoItem& p = mPhotos[photoIdx];
        const int S = 160;
        std::string cf = photoCacheFile(p.file, p.mtime, p.sz, 'c');
        tex = photoCacheRead565(cf, nullptr);     // disk-cache fast path (local, always cheap)
        if (tex) {
            mPhotoCoverCache[photoIdx] = tex;
            return tex;
        }
        // Cache miss: decode on the worker, not here. This runs from the Photo column draw, and
        // decoding means reading the whole image - on a share that is a network read on the render
        // thread, which the watchdog turns into an abort rather than a stutter. Returning 0 draws
        // the placeholder until mpDrainAlbumArt() uploads the result. Nothing is written into
        // mPhotoCoverCache yet, so the entry stays "unresolved"; the pending set below stops it
        // being queued again on every frame.
        mpStartArtWorker();
        {
            std::lock_guard<std::mutex> lk(mMpArtLock);
            if (mMpArtPending.insert(p.file).second) {
                MpArtJob job;
                job.album = p.file;      // the pending-set key
                job.track = p.file;      // the worker decodes this path
                job.isPhoto = true;
                job.photoIdx = photoIdx;
                job.cacheFile = cf;
                mMpArtQueue.push_back(std::move(job));
            }
        }
        mMpArtCv.notify_one();
        return 0;
    }
    mPhotoCoverCache[photoIdx] = tex;
    return tex;
}
void NanoMenu::photoFreeCovers() {
    for (auto& kv : mPhotoCoverCache) if (kv.second) glDeleteTextures(1, &kv.second);
    mPhotoCoverCache.clear();
}

// Folder-shaped column icon (silver folder + tab) with the cover photo / album art
// inset in the body - the web XMB photo-folder look. coverTex 0 = plain folder.
void NanoMenu::drawFolderIcon(float ix, float iy, float dsz, float alpha, GLuint coverTex) {
    float a = alpha;
    // Fit the folder inside a centred sub-box so opaque folders never touch/overlap
    // the adjacent rows (glass icons get away with the full box via transparent
    // margins; an opaque folder must be inset to match their visual footprint).
    const float F = 0.74f;
    ix += dsz * (1.0f - F) * 0.5f; iy += dsz * (1.0f - F) * 0.5f; dsz *= F;
    // soft drop shadow under the folder so it reads over the bright wave
    drawRoundedRect(ix + dsz * 0.05f, iy + dsz * 0.20f, dsz * 0.92f, dsz * 0.76f,
                    dsz * 0.07f, 0.0f, 0.0f, 0.0f, mPs3ShadowAlpha * 0.6f * a);
    // tab (behind the body; only its top sliver shows above the body's top edge)
    drawRoundedRect(ix + dsz * 0.07f, iy + dsz * 0.075f, dsz * 0.40f, dsz * 0.17f,
                    dsz * 0.045f, 0.78f, 0.79f, 0.83f, a);
    // body
    float bx = ix + dsz * 0.035f, by = iy + dsz * 0.165f, bw = dsz * 0.93f, bh = dsz * 0.77f;
    drawRoundedRect(bx, by, bw, bh, dsz * 0.06f, 0.85f, 0.86f, 0.90f, a);
    // top highlight strip for a little depth
    drawRoundedRect(bx, by, bw, dsz * 0.05f, dsz * 0.06f, 0.95f, 0.96f, 0.99f, a * 0.55f);
    // cover inset: a centred square (the covers are square crops), nudged into the body
    if (coverTex) {
        float cs = dsz * 0.66f;
        float cx = ix + (dsz - cs) * 0.5f;
        float cy = by + bh * 0.5f - cs * 0.5f + dsz * 0.035f;
        drawQuad(cx - dsz * 0.012f, cy - dsz * 0.012f, cs + dsz * 0.024f, cs + dsz * 0.024f,
                 0.08f, 0.08f, 0.10f, a);                       // thin dark mat
        drawIconTex(coverTex, cx, cy, cs, cs, 1.0f, 1.0f, 1.0f, a);
    }
}

void NanoMenu::openPhotoGrid(const std::vector<int>& list, const std::string& title, int fromPl) {
    mWpVideoPick = false;   // default to photo mode; openVideoWallpaperPicker re-sets it after this call
    mBoxartPick  = false;   // default to a normal browse; openBoxartPicker re-sets it after this call
    mPhotoGridList = list;
    mPhotoGridCursor = 0;
    mPhotoGridTop = 0;
    mPhotoGridScrollY = 0.0f;
    mPhotoGridScrollAnchor = 0.0f;
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
    mWpVideoPick = false;   // a back/abort out of the video picker leaves no dangling flag
    mBoxartPick  = false;   // a back/abort out of the boxart picker leaves no dangling flag
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
        mPhotoGridScrollY = (float)mPhotoGridTop * cell;   // D-pad snaps the smooth scroll to the row
    }
}

void NanoMenu::photoGridSelect() {
    if (mPhotoGridCursor < 0 || mPhotoGridCursor >= (int)mPhotoGridList.size()) return;
    // Video wallpaper picker: no still viewer/crop - apply the chosen video straight away (wallpaperApplyPick
    // detects the video path and starts the looping decoder), then close the grid back to the menu.
    if (mWpVideoPick) {
        int vi = (mPhotoGridCursor >= 0 && mPhotoGridCursor < (int)mWpPickVidList.size())
                     ? mWpPickVidList[mPhotoGridCursor] : -1;
        if (vi >= 0 && vi < (int)mVideos.size()) wallpaperApplyPick(mVideos[vi].file);
        mWpVideoPick = false; mWpPickTarget = -1;
        closePhotoGrid();
        if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == PHOTO_GRID) mPs3Stack.pop_back();
        return;
    }
    // Custom boxart picker: no still viewer/crop - copy the chosen image straight into the game's
    // cover cache (boxartApplyPick), then close the grid back to the option menu, like the video pick.
    if (mBoxartPick) {
        int pi = mPhotoGridList[mPhotoGridCursor];
        if (pi >= 0 && pi < (int)mPhotos.size()) boxartApplyPick(mPhotos[pi].file);
        mBoxartPick = false;
        closePhotoGrid();
        if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == PHOTO_GRID) mPs3Stack.pop_back();
        mDisplayDirty = true;
        return;
    }
    openPhotoViewer(mPhotoGridList, mPhotoGridCursor);
    // Wallpaper picker: skip the photo-viewer options and drop straight into the crop/confirm frame
    // (mirrors the pvOpen "wallpaper" action), so the flow is browse -> pick -> crop -> set.
    if (mWpPickTarget >= 0) { mPvPanel = false; mPvWpMode = true; mPvWpZoom = 1.0f; mPvHintUntil = 0.0f; }
}

// ---- Photo grid touch (tap a thumbnail to open, drag to scroll) ----
// Recompute the same cell layout renderPhotoGrid uses (slide=0 at rest) so a device
// touch maps to a thumbnail index. mPhotoGridTop is the top visible row.
static void pvGridLayout(int W, int H, float& margin, float& top, float& cell, int& visRows) {
    float ts = fmaxf(1.0f, (float)H / 768.0f);
    margin = W * 0.055f;
    top    = 132.0f * ts;
    float gridW = W - margin * 2.0f;
    cell   = gridW / PG_COLS;
    visRows = (int)((H - top - 70.0f * ts) / cell);
    if (visRows < 1) visRows = 1;
}
int NanoMenu::photoGridCellAt(float px, float py) {
    int n = (int)mPhotoGridList.size();
    if (n <= 0) return -1;
    float margin, top, cell; int visRows;
    pvGridLayout(mWidth, mHeight, margin, top, cell, visRows);
    if (py < top) return -1;                                          // title strip, not a cell
    int col = (int)floorf((px - margin) / cell);
    int row = (int)floorf((py - top + mPhotoGridScrollY) / cell);     // absolute row (smooth scroll)
    if (col < 0 || col >= PG_COLS || row < 0) return -1;
    int idx = row * PG_COLS + col;
    if (idx < 0 || idx >= n) return -1;
    return idx;
}
void NanoMenu::photoGridScrollTo(int topRow) {   // snap the pixel scroll to a whole row (D-pad helper)
    float margin, top, cell; int visRows;
    pvGridLayout(mWidth, mHeight, margin, top, cell, visRows);
    mPhotoGridScrollY = (float)topRow * cell;   // the render clamps
    mDisplayDirty = true;
}
void NanoMenu::photoGridScrollDrag(float downPy, float py, int /*unused*/) {
    // Smooth pixel scroll from the anchor captured at touch-down (content follows the
    // finger; finger up -> later rows). The render clamps to [0, maxScrollY].
    mPhotoGridScrollY = mPhotoGridScrollAnchor + (downPy - py);
    mDisplayDirty = true;
}
void NanoMenu::photoGridOpenAt(int idx) {
    if (idx < 0 || idx >= (int)mPhotoGridList.size()) return;
    mPhotoGridCursorPrev = mPhotoGridCursor;
    mPhotoGridCursor = idx;
    mPhotoGridFocusStart = mEffectTime;
    photoGridSelect();
}
// Leave the folder/grid view (mirrors photoGridNav's LEFT-at-column-0 exit). A touch
// tap on the top-left back chevron routes here; the grid is popped off the level stack.
void NanoMenu::photoGridBack() {
    closePhotoGrid();
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == PHOTO_GRID) mPs3Stack.pop_back();
}
// Hit-test the top-left back chevron zone (same layout renderPhotoGrid draws). The
// zone is confined to the header strip above the first thumbnail row so it never
// overlaps a cell tap.
bool NanoMenu::photoGridBackHit(float px, float py) {
    float ts = fmaxf(1.0f, (float)mHeight / 768.0f);
    float margin = mWidth * 0.055f;
    const char* bchev = "\xE2\x80\xB9";
    float chFs = 2.0f * ts;
    float chW = measureText(bchev, chFs);
    // generous touch target around the chevron, still left of a long title
    return px >= 0.0f && px <= margin + chW + 22.0f * ts &&
           py >= 0.0f && py <= 118.0f * ts;
}

void NanoMenu::renderPhotoGrid() {
    int W = mWidth, H = mHeight;
    float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;
    mPhotoGridAnim += (1.0f - mPhotoGridAnim) * (1.0f - expf(-13.0f * dt));
    if (mPhotoGridAnim > 0.999f) mPhotoGridAnim = 1.0f;
    float a = mPhotoGridAnim;
    float slide = (1.0f - a) * 24.0f;
    // dark scrim over the wave background. In the Minima theme the picker must not reveal the
    // PS3 wave underneath (it renders over renderPs3Xmb via the "other modal" fallback), so lay
    // down an OPAQUE black field - the flat NextUI canvas - instead of a translucent scrim.
    if (mMinimaTheme) drawQuad(0, 0, (float)W, (float)H, 0.0f, 0.0f, 0.0f, 1.0f);
    else              drawQuad(0, 0, (float)W, (float)H, 0.04f, 0.05f, 0.06f, 0.88f * a);

    float ts = fmaxf(1.0f, (float)H / 768.0f);
    float margin = W * 0.055f;
    float top = 132.0f * ts + slide;
    float gridW = W - margin * 2.0f;
    float cell = gridW / PG_COLS;
    float thumbW = cell * 0.84f, thumbH = thumbW * 9.0f / 16.0f;   // 16:9 thumbnail box
    int visRows = (int)((H - top - 70.0f * ts) / cell);
    if (visRows < 1) visRows = 1;

    // back chevron (top-left) + title + count. The chevron is a touch exit for the
    // folder view, styled like the photo-viewer exit glyph (dark shadow + silvery
    // fill). The title indents to sit right of it, reading "< Folder".
    const char* bchev = "\xE2\x80\xB9";   // U+2039, renders in Rodin (U+276E is tofu)
    float chFs = 2.0f * ts;
    float chW = measureText(bchev, chFs);
    float chX = margin, chY = 30.0f * ts + slide;
    float so = fmaxf(1.0f, 2.0f * ts);
    drawText(bchev, chX + so, chY + so, chFs, 0.0f, 0.0f, 0.0f, 0.55f * a);
    drawText(bchev, chX, chY, chFs, 0.86f, 0.92f, 1.0f, 0.96f * a);
    float titleX = margin + chW + 20.0f * ts;
    drawText(mPhotoGridTitle.c_str(), titleX, 34.0f * ts + slide, 1.7f * ts, 1.0f, 1.0f, 1.0f, a);
    char info[64];
    const char* unit = mWpVideoPick ? (mPhotoGridList.size() == 1 ? "video" : "videos")
                                    : (mPhotoGridList.size() == 1 ? "image" : "images");
    snprintf(info, sizeof(info), "%zu %s", mPhotoGridList.size(), unit);
    drawText(info, titleX, 84.0f * ts + slide, 1.0f * ts, 0.75f, 0.85f, 0.95f, a);

    // focus grow tween (1.0 -> 1.36, easeOutCubic, mirrors the web drawPhotoGrid).
    // Duration matches the web V.ITEM_ANIM_MS (200ms), same as the XMB item scroll.
    float fe = 1.0f;
    if (mPhotoGridFocusStart >= 0.0f) {
        fe = fminf(1.0f, (mEffectTime - mPhotoGridFocusStart) / 0.20f);
        fe = 1.0f - powf(1.0f - fe, 3.0f);
        if (fe >= 1.0f) mPhotoGridFocusStart = -1.0f;
    }
    float pulse = 0.5f + 0.5f * cosf(mEffectTime * 2.0f * 3.14159f / 1.5f);

    int n = (int)mPhotoGridList.size();
    int totalRows = (n + PG_COLS - 1) / PG_COLS;
    // Smooth finger scroll: mPhotoGridScrollY is the pixels scrolled from the top. Clamp
    // it, keep the legacy integer top row in sync (D-pad nav settles to it), and draw
    // absolute rows offset by scrollY so a drag moves the grid pixel-for-pixel.
    float maxScrollY = fmaxf(0.0f, (float)(totalRows - visRows) * cell);
    if (mPhotoGridScrollY < 0.0f) mPhotoGridScrollY = 0.0f;
    else if (mPhotoGridScrollY > maxScrollY) mPhotoGridScrollY = maxScrollY;
    float scrollY = mPhotoGridScrollY;
    mPhotoGridTop = (int)floorf(scrollY / cell + 0.5f);
    int firstRow = (int)floorf(scrollY / cell) - 1; if (firstRow < 0) firstRow = 0;
    int lastRow  = (int)floorf((scrollY + ((float)H - top)) / cell) + 1;
    std::vector<int> need;   // uncached visible thumbs, decoded in a budgeted batch
    for (int rr = firstRow; rr <= lastRow; rr++)
      for (int cc = 0; cc < PG_COLS; cc++) {
        int idx = rr * PG_COLS + cc;
        if (idx >= n) break;
        if (idx < 0) continue;
        float ccx = margin + cc * cell + cell * 0.5f;
        float ccy = top + (float)rr * cell + cell * 0.5f - scrollY;
        bool sel = (idx == mPhotoGridCursor);
        float sc = 1.0f;
        if (sel) sc = 1.0f + 0.36f * fe;
        else if (idx == mPhotoGridCursorPrev && mPhotoGridFocusStart >= 0.0f) sc = 1.36f - 0.36f * fe;
        float w = thumbW * sc, h = thumbH * sc;
        float x = ccx - w * 0.5f, y = ccy - h * 0.5f;
        int pIdx = mPhotoGridList[idx];
        GLuint tex = 0;
        if (!mWpVideoPick) {   // photo cell: memoised photo thumb, decoded in the budgeted batch below
            auto cit = mPhotoThumbCache.find(pIdx);
            if (cit != mPhotoThumbCache.end()) tex = cit->second;
            else need.push_back(pIdx);
        } else {               // video cell: the auto-generated poster (kind 'v'), if it has been cached yet
            int vi = (idx < (int)mWpPickVidList.size()) ? mWpPickVidList[idx] : -1;
            if (vi >= 0 && vi < (int)mVideos.size()) tex = videoIconTexCached(mVideos[vi].file);   // 0 until generated
        }
        // opaque base (drop-shadow substitute): a dark card behind the thumb
        drawQuad(x - 2, y - 2, w + 4, h + 4, 0.0f, 0.0f, 0.0f, (sel ? 0.8f : 0.6f) * a);
        // Video picker: the FOCUSED cell plays a live preview of the hovered video (vidPreviewTick owns
        // the single HW decoder here). Falls back to the poster/placeholder until the first frame lands.
        bool drewPreview = (mWpVideoPick && sel) ? drawVidPreviewInto(x, y, w, h) : false;
        if (drewPreview) { /* the live frame fills the cell */ }
        else if (tex) drawIconTex(tex, x, y, w, h, 1.0f, 1.0f, 1.0f, (sel ? 1.0f : 0.92f) * a);
        else     drawQuad(x, y, w, h, 0.10f, 0.11f, 0.13f, 0.9f * a);   // placeholder / video card base
        if (mWpVideoPick) {   // film badge marks a video cell: centred play triangle when there is no poster
            // yet, shrunk to a corner overlay once the generated frame (or live preview) fills the cell.
            if (tex || drewPreview) {
                float tr = fminf(w, h) * 0.11f, cxp = x + w - tr * 1.7f, cyp = y + h - tr * 1.3f;
                drawQuad(cxp - tr * 1.1f, cyp - tr * 1.1f, tr * 2.6f, tr * 2.2f, 0.0f, 0.0f, 0.0f, 0.45f * a);
                drawTriangle(cxp - tr * 0.55f, cyp - tr, cxp - tr * 0.55f, cyp + tr, cxp + tr, cyp,
                             1.0f, 1.0f, 1.0f, (sel ? 0.95f : 0.85f) * a);
            } else {
                float cxp = x + w * 0.5f, cyp = y + h * 0.5f, tr = fminf(w, h) * 0.20f;
                drawTriangle(cxp - tr * 0.55f, cyp - tr, cxp - tr * 0.55f, cyp + tr, cxp + tr, cyp,
                             1.0f, 1.0f, 1.0f, (sel ? 0.95f : 0.8f) * a);
            }
        }
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
    // Decode this frame's missing thumbs: many fast disk-cache reads, but only a
    // couple of cold (source-decode) ones so a not-yet-warmed album still scrolls.
    if (!need.empty()) {
        int fast = 8, cold = 2;
        for (int pIdx : need) {
            if (fast <= 0 && cold <= 0) break;
            const PhotoItem& pp = mPhotos[pIdx];
            std::string cf = photoCacheFile(pp.file, pp.mtime, pp.sz, 't');
            bool cached = (access(cf.c_str(), F_OK) == 0);
            if (cached) { if (fast <= 0) continue; fast--; }
            else        { if (cold <= 0) continue; cold--; }
            photoThumb(pIdx);
        }
        photoThumbEvict();
    }

    // focused caption (name + date/codec) under the grid
    if (mPhotoGridCursor >= 0 && mPhotoGridCursor < n) {
        std::string capName, capSub;
        if (mWpVideoPick) {
            int vi = (mPhotoGridCursor < (int)mWpPickVidList.size()) ? mWpPickVidList[mPhotoGridCursor] : -1;
            if (vi >= 0 && vi < (int)mVideos.size()) { capName = mVideos[vi].name; capSub = mVideos[vi].vcodec; }
        } else {
            const PhotoItem& p = mPhotos[mPhotoGridList[mPhotoGridCursor]];
            capName = p.name; capSub = fmtPhotoDate(p.date);
        }
        float ns = 1.25f * ts;
        float nw = measureText(capName.c_str(), ns);
        drawText(capName.c_str(), (W - nw) * 0.5f, (float)H - 60.0f * ts, ns, 1.0f, 1.0f, 1.0f, a);
        float ds = 0.95f * ts;
        float dw = measureText(capSub.c_str(), ds);
        drawText(capSub.c_str(), (W - dw) * 0.5f, (float)H - 32.0f * ts, ds, 0.8f, 0.85f, 0.92f, a);
    }

    // Scroll indicator on the right edge when the grid overflows (a scrollable hint,
    // so it is obvious there is more below). Track + a proportional thumb, tracking the
    // smooth pixel scroll.
    if (totalRows > visRows) {
        float trackW = fmaxf(3.0f, PXD(0.006f));
        float trackX = (float)W - margin * 0.5f - trackW;
        float trackY = top;
        float trackH = (float)visRows * cell - cell * 0.16f;
        drawQuad(trackX, trackY, trackW, trackH, 1.0f, 1.0f, 1.0f, 0.14f * a);
        float thumbH = trackH * (float)visRows / (float)totalRows;
        if (thumbH < trackW * 3.0f) thumbH = trackW * 3.0f;
        float pos = (maxScrollY > 0.0f) ? scrollY / maxScrollY : 0.0f;
        if (pos < 0.0f) pos = 0.0f; else if (pos > 1.0f) pos = 1.0f;
        drawQuad(trackX, trackY + (trackH - thumbH) * pos, trackW, thumbH, 1.0f, 1.0f, 1.0f, 0.5f * a);
    }
}

// ---------------------------------------------------------------------------
// Full-screen photo viewer (firmware photoviewer_plugin.rco).
// ---------------------------------------------------------------------------
// Non-blocking: return the cached display texture if ready, else enqueue an async
// decode and return 0 (the caller falls back to the thumbnail placeholder). NEVER
// decodes inline, so the render thread stays at a locked 60fps.
GLuint NanoMenu::pvTex(int photoIdx, int* w, int* h) {
    auto it = mPvTexCache.find(photoIdx);
    if (it != mPvTexCache.end()) {
        if (w) *w = mPvTexW[photoIdx]; if (h) *h = mPvTexH[photoIdx];
        return it->second;
    }
    if (w) *w = 0; if (h) *h = 0;
    pvRequestDecode(photoIdx);
    return 0;
}

void NanoMenu::pvStartDecodeWorker() {
    if (mPvDecStarted.load()) return;
    mPvDecStop.store(false);
    mPvDecThread = std::thread([this]{ pvDecodeThreadFunc(); });
    mPvDecStarted.store(true);
}

void NanoMenu::pvStopDecodeWorker() {
    if (!mPvDecStarted.load()) return;
    mPvDecStop.store(true);
    mPvDecCv.notify_all();
    if (mPvDecThread.joinable()) mPvDecThread.join();
    mPvDecStarted.store(false);
}

// Worker thread: pops the request closest to the current focus and decodes it to
// CPU RGBA (NO GL here). Results are uploaded on the render thread by pvDrainDecodes.
void NanoMenu::pvDecodeThreadFunc() {
    for (;;) {
        PvDecReq req;
        {
            std::unique_lock<std::mutex> lk(mPvDecMutex);
            mPvDecCv.wait(lk, [&]{ return mPvDecStop.load() || !mPvDecQueue.empty(); });
            if (mPvDecStop.load()) return;
            int focus = mPvFocusIdx.load();
            auto pick = mPvDecQueue.begin();
            for (auto i = mPvDecQueue.begin(); i != mPvDecQueue.end(); ++i)
                if (i->idx == focus) { pick = i; break; }
            req = *pick; mPvDecQueue.erase(pick);
        }
        if (req.gen != mPvDecGen.load()) {   // stale (viewer reopened / list changed)
            std::lock_guard<std::mutex> lk(mPvDecMutex); mPvDecInFlight.erase(req.idx); continue;
        }
        int w = 0, h = 0; std::vector<uint8_t> px;
        bool ok = photoDecodeRGBACpu(req.path, req.maxDim, &w, &h, px);
        {
            std::lock_guard<std::mutex> lk(mPvDecMutex);
            mPvDecInFlight.erase(req.idx);
            if (ok && req.gen == mPvDecGen.load()) {
                PvDecRes res; res.idx = req.idx; res.w = w; res.h = h; res.gen = req.gen;
                res.px = std::move(px);
                mPvDecDone.push_back(std::move(res));
            }
        }
    }
}

void NanoMenu::pvRequestDecode(int photoIdx) {
    if (photoIdx < 0 || photoIdx >= (int)mPhotos.size()) return;
    if (mPvTexCache.count(photoIdx)) return;
    pvStartDecodeWorker();
    {
        std::lock_guard<std::mutex> lk(mPvDecMutex);
        if (mPvDecInFlight.count(photoIdx)) return;
        mPvDecInFlight.insert(photoIdx);
        PvDecReq r; r.idx = photoIdx; r.path = mPhotos[photoIdx].file;
        r.maxDim = mPvMaxDim; r.gen = mPvDecGen.load();
        mPvDecQueue.push_back(r);
    }
    mPvDecCv.notify_one();
}

// Render/GL thread: upload any finished CPU decodes to GL textures.
void NanoMenu::pvDrainDecodes() {
    std::vector<PvDecRes> done;
    { std::lock_guard<std::mutex> lk(mPvDecMutex); done.swap(mPvDecDone); }
    uint64_t gen = mPvDecGen.load();
    for (auto& r : done) {
        if (r.gen != gen) continue;
        if (mPvTexCache.count(r.idx)) continue;
        GLuint tex = uploadRGBATex(r.px.data(), r.w, r.h);
        mPvTexCache[r.idx] = tex; mPvTexW[r.idx] = r.w; mPvTexH[r.idx] = r.h;
    }
}

void NanoMenu::pvPrefetch() {
    // Request current + direction-window neighbours (async, idempotent) and evict
    // uploaded textures outside the +/-2 window so the cache stays at kPvTexCap.
    if (mPvList.empty()) return;
    int n = (int)mPvList.size();
    std::set<int> keep;
    for (int d = -2; d <= 2; d++) { int i = mPvIdx + d; if (i < 0 || i >= n) continue; keep.insert(mPvList[i]); }
    for (int d = 0; d <= 2; d++) {
        int a = mPvIdx + d, b = mPvIdx - d;
        if (a >= 0 && a < n) pvRequestDecode(mPvList[a]);
        if (b >= 0 && b < n) pvRequestDecode(mPvList[b]);
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
    // Drop any pending async work tied to this viewer session.
    mPvDecGen.fetch_add(1);
    std::lock_guard<std::mutex> lk(mPvDecMutex);
    mPvDecQueue.clear(); mPvDecDone.clear(); mPvDecInFlight.clear();
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
    mPvDragActive = false; mPvDragSettle = false; mPvDragDx = 0.0f; mPvDragCommitDir = 0; mPvPanning = false;
    mPvPinchActive = false;
    if (mPvEffect.empty()) mPvEffect = "Normal";
    // Async decode: never block the render thread. Display long-side, clamped crisp.
    mPvMaxDim = (mWidth > mHeight ? mWidth : mHeight);
    if (mPvMaxDim < 1024) mPvMaxDim = 1024;
    if (mPvMaxDim > 1920) mPvMaxDim = 1920;
    mPvDecGen.fetch_add(1);            // invalidate any results from a prior session
    pvStartDecodeWorker();
    mPvFocusIdx.store(mPvList[mPvIdx]);
    pvRequestDecode(mPvList[mPvIdx]);  // current; neighbours requested by pvPrefetch
}

void NanoMenu::closePhotoViewer() {
    mPvActive = false;
    mPvPanel = false; mPvCpClosing = false; mPvSlideshow = false; mPvInfo = false;
    mPvWpMode = false; mPvTrimMode = false; mPvCpSub = false; mPvPlChooserActive = false;
    // Textures + list are kept so renderPhotoViewer can draw the exit fade-to-black;
    // photoTick frees them once mPvEnterRaw eases to ~0.
}

// Land on a photo with no Slide/Fade transition (used by the interactive touch
// swipe, which does its own finger-tracked slide). Resets view state + prefetch.
void NanoMenu::pvGoTo(int newIdx) {
    if (mPvList.empty()) return;
    int n = (int)mPvList.size();
    mPvIdx = ((newIdx % n) + n) % n;
    mPvRot = 0; mPvZoom = 1.0f; mPvPanX = 0.0f; mPvPanY = 0.0f;
    mPvFocusIdx.store(mPvList[mPvIdx]);
    pvRequestDecode(mPvList[mPvIdx]);
    if (mPvIdx + 1 < n) pvRequestDecode(mPvList[mPvIdx + 1]);
    if (mPvIdx - 1 >= 0) pvRequestDecode(mPvList[mPvIdx - 1]);
}

void NanoMenu::pvStep(int d) {
    if (mPvList.empty()) return;
    mPvDragActive = false; mPvDragSettle = false; mPvDragDx = 0.0f; mPvDragCommitDir = 0; mPvPanning = false;
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
    // NOTE: do NOT re-arm the help hint here. It is shown once on viewer open;
    // re-arming on every step made it flash between each photo / slideshow advance.
    // Async: prioritise the new current photo, and pre-decode the direction-ahead
    // neighbour so the next advance is instant. No synchronous decode here.
    mPvFocusIdx.store(mPvList[mPvIdx]);
    pvRequestDecode(mPvList[mPvIdx]);
    int nb = mPvIdx + (d >= 0 ? 1 : -1);
    if (nb >= 0 && nb < n) pvRequestDecode(mPvList[nb]);
}

// Draw one photo fit-to-screen with rotation / zoom / pan, at a given alpha and
// horizontal screen offset (for the Slide transition). Mirrors web drawPhoto.
void NanoMenu::renderPhotoViewer() {
    if (mPvList.empty()) { mPvActive = false; return; }
    pvDrainDecodes();   // upload any finished async decodes (GL on the render thread)
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
        if (!tex) {   // full image not decoded yet: show the disk-cached thumbnail
            tex = photoThumb(photoIdx);   // 256px, fast (RGB565 disk read)
            if (photoIdx >= 0 && photoIdx < (int)mPhotos.size()) {
                iw = mPhotos[photoIdx].w; ih = mPhotos[photoIdx].h;   // native aspect = same fit rect
            }
        }
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
    // Resolve the interactive swipe offset: live while a finger drags, then eased to
    // the commit (+/-W) or spring-back (0) target on release. When a commit settle
    // finishes, land on the neighbour with no extra Slide transition.
    float ddx = 0.0f; bool showDrag = false;
    if (mPvDragActive) { ddx = mPvDragDx; showDrag = true; }
    else if (mPvDragSettle) {
        float p = (mEffectTime - mPvDragSettleStart) / 0.26f;
        if (p >= 1.0f) {
            mPvDragSettle = false;
            if (mPvDragCommitDir != 0) { pvGoTo(mPvIdx + mPvDragCommitDir); mPvDragCommitDir = 0; }
            mPvDragDx = 0.0f; ddx = 0.0f; showDrag = false;
        } else {
            float e = 1.0f - powf(1.0f - p, 3.0f);
            ddx = mPvDragFrom + (mPvDragTo - mPvDragFrom) * e;
            showDrag = true;
        }
        mDisplayDirty = true;
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
    } else if (showDrag && fabsf(ddx) > 0.5f && (int)mPvList.size() > 1) {
        // current photo follows the finger; the neighbour it is revealing slides in
        // alongside it (next comes from the right, previous from the left).
        int dir = (ddx < 0.0f) ? +1 : -1;
        int n2 = (int)mPvList.size();
        int nb = ((mPvIdx + dir) % n2 + n2) % n2;
        float nbOff = ddx + ((ddx < 0.0f) ? (float)W : -(float)W);
        drawPhoto(mPvList[mPvIdx], mPvRot, mPvZoom, et, ddx, 0, 0);
        drawPhoto(mPvList[nb], 0, 1.0f, et, nbOff, 0, 0);
    } else {
        drawPhoto(mPvList[mPvIdx], mPvRot, mPvZoom, et, 0, mPvPanX, mPvPanY);
    }

    pvPrefetch();

    // Touch exit affordance: a back chevron at the top-left, shown ONLY while the
    // control panel is up (it comes up with the rest of the on-screen icons), styled
    // to match the panel glyphs - a soft dark shadow for contrast on bright photos
    // plus the same silvery fill the panel icons use, rather than a flat box. Tapping
    // it leaves the viewer (pvTouchFrame hit-tests the same zone).
    if (mPvPanel && !mPvWpMode && !mPvTrimMode && !mPvPlChooserActive) {
        float pa = (mPvCpAnimStart >= 0.0f) ? fminf(1.0f, (mEffectTime - mPvCpAnimStart) / 0.2f) : 1.0f;
        float a = pa * et;
        // Full-panel placement (matches pvTouchFrame's hit-test), sized to the short side.
        float shortSide = (float)(mWidth < mHeight ? mWidth : mHeight);
        float afs = (shortSide * 0.11f) / 16.0f;
        const char* arrow = "\xE2\x80\xB9";   // U+2039 single left angle quote (renders in Rodin; U+276E was tofu)
        float aw = measureText(arrow, afs);
        float acx = mWidth * 0.07f, acy = mHeight * 0.075f;
        float ax = acx - aw * 0.5f, ay = ps3::baselineToTopY(acy + 16.0f * afs * 0.30f, afs);
        float so = shortSide * 0.003f;
        drawText(arrow, ax + so, ay + so, afs, 0.0f, 0.0f, 0.0f, 0.55f * a);
        drawText(arrow, ax, ay, afs, 0.86f, 0.92f, 1.0f, 0.96f * a);
    }

    // (The bottom-right controller-button help hint was removed per user request.)

    if (mPvWpMode || mPvTrimMode) drawPvWallpaperSel();   // Set as Wallpaper / Trimming range selector
    if (mPvInfo) drawPvInfo();
    if (mPvPanel) drawPvPanel(-1.0f);
    else if (mPvCpClosing) {
        float p = (mEffectTime - mPvCpCloseStart) / 0.2f;
        if (p >= 1.0f) mPvCpClosing = false; else drawPvPanel(1.0f - p);
    }
    if (mPvDispModeUntil > mEffectTime) drawPvDispModePill();
    if (mPvPlChooserActive || mPvPlChooserAnim > 0.004f) drawPvPlChooser();
    drawPhotoMsg();
}

// Shared transient full-screen message (Delete / Copy / 2D-3D / wallpaper-set),
// music-player style; drawn by the viewer, the grid and the multi-select screen.
void NanoMenu::drawPhotoMsg() {
    if (mPvMsgStart < 0.0f) return;
    int W = mWidth, H = mHeight;
    float el = (mEffectTime - mPvMsgStart) * 1000.0f;
    if (el >= mPvMsgDur) { mPvMsgStart = -1.0f; return; }
    float fade = fminf(1.0f, el / 150.0f) * fminf(1.0f, fmaxf(0.0f, (mPvMsgDur - el)) / 200.0f);
    drawQuad(0, 0, (float)W, (float)H, 0, 0, 0, 0.45f * fade);
    float ms = PFS(28.0f); float mw = measureText(mPvMsg.c_str(), ms);
    drawText(mPvMsg.c_str(), (W - mw) * 0.5f, ps3::baselineToTopY(PYP(0.5f), ms), ms, 1.0f, 1.0f, 1.0f, fade);
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
        if (!strcmp(f, "Date taken")) {
            // The EXIF viewer panel shows seconds (the web appends ":SS", default
            // "00" when the source has none); our mtime dates carry no seconds.
            val = fmtPhotoDate(p.date);
            if (val.size() >= 5 && val.find(':') != std::string::npos)
                val += (p.date.size() >= 19) ? (":" + p.date.substr(17, 2)) : ":00";
        }
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

// Full-panel control-grid layout, shared by the draw (drawPvPanel) and the touch hit-test
// (pvTouchFrame) so they can never drift. Spans the whole panel width and is sized to the short
// side (see ps3::mediaGrid), adapting to any size/aspect instead of the letterboxed XMB frame.
// Photos' cy = oy + (gy-1)*cellY, so oy is placed to centre the row block vertically on the panel.
struct PvLayout { float ox, oy, cellX, cellY, ih, cx, labBaseY; };
static PvLayout pvLayout(int W, int H, const PvCp* cp, int cnt) {
    float gxLo = 1e9f, gxHi = -1e9f, gyLo = 1e9f, gyHi = -1e9f;
    for (int i = 0; i < cnt; i++) {
        if (cp[i].gx < gxLo) gxLo = cp[i].gx; if (cp[i].gx > gxHi) gxHi = cp[i].gx;
        if (cp[i].gy < gyLo) gyLo = cp[i].gy; if (cp[i].gy > gyHi) gyHi = cp[i].gy;
    }
    ps3::MediaGrid g = ps3::mediaGrid(W, H, gxLo, gxHi, gyLo, gyHi);
    PvLayout v;
    v.cellX = g.cellX; v.cellY = g.cellY; v.ih = g.icon; v.ox = g.ox; v.cx = (float)W * 0.5f;
    v.oy = g.centerY - ((gyLo + gyHi) * 0.5f - 1.0f) * g.cellY;   // centre the block (cy = oy + (gy-1)*cellY)
    v.labBaseY = g.centerY + (gyHi - gyLo) * 0.5f * g.cellY + g.icon;   // below the bottom row
    return v;
}

void NanoMenu::openPvPanel(bool byTouch) {
    if (mPvPanel) return;
    mPvPanelTouch = byTouch;
    int cnt; const PvCp* cp = pvCpTable(mPvSlideshow, &cnt);
    mPvPanel = true; mPvCpClosing = false; mPvCpSub = false;
    mPvCpSel = 0;
    if (mPvSlideshow) { for (int i = 0; i < cnt; i++) if (!strcmp(cp[i].act, "pause")) { mPvCpSel = i; break; } }
    mPvCpAnimStart = mEffectTime; mPvCpFocusStart = mEffectTime; mPvCpSelPrev = mPvCpSel;
    mPvCpPressStart = -1.0f; mPvHintUntil = 0.0f;
}

float NanoMenu::pvPanelUi() {
    float ui = pvUiScale(mWidth, mHeight);
    if (mPvPanelTouch) ui *= PV_TOUCH_PANEL_SCALE;
    return ui;
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

// ---------------------------------------------------------------------------
// Photo viewer touch (Gallery style). Swipe left/right = next/previous photo,
// tap = show/hide the control panel, tap a control cell runs it, tap-blank
// dismisses the controls, a firm swipe down exits the viewer. Reuses the shared
// raw->logical mapping (correct on DRM + SF); gestures drive the same pv*
// handlers the D-pad uses. Called from pollInput's SYN_REPORT when mPvActive.
// ---------------------------------------------------------------------------
void NanoMenu::pvTouchFrame() {
    if (!mPvActive) { mXmbTouchTracking = false; mTouchWasDown = mTouchDown; return; }
    float px, py;
    if (!touchLogicalPx(px, py)) { mTouchWasDown = mTouchDown; return; }
    const float SLOP = 16.0f, TAPMAX = 24.0f;
    const int64_t TAPMS = 450;
    int64_t now = android::uptimeMillis();
    bool down = mTouchDown, downEdge = down && !mTouchWasDown, upEdge = !down && mTouchWasDown;

    // Two-finger pinch-to-zoom over the bare photo. Takes precedence over the
    // single-finger drag/pan: while two contacts are active the zoom tracks the
    // ratio of the finger distance to the distance when the second finger landed.
    int nf = (mTouchId[0] >= 0 ? 1 : 0) + (mTouchId[1] >= 0 ? 1 : 0);
    bool pinchable = !mPvPanel && !mPvInfo && !mPvWpMode && !mPvTrimMode && !mPvPlChooserActive;
    if (nf >= 2 && pinchable) {
        float ax, ay, bx, by;
        if (touchMapRaw(mTouchSX[0], mTouchSY[0], ax, ay) &&
            touchMapRaw(mTouchSX[1], mTouchSY[1], bx, by)) {
            float dist = sqrtf((bx - ax) * (bx - ax) + (by - ay) * (by - ay));
            if (!mPvPinchActive) {
                mPvPinchActive = true;
                mPvPinchStartDist = fmaxf(1.0f, dist);
                mPvPinchStartZoom = (mPvZoom <= 0.0f ? 1.0f : mPvZoom);
                mPvDragActive = false; mPvPanning = false;   // cancel any single-finger gesture
            } else {
                float z = mPvPinchStartZoom * (dist / mPvPinchStartDist);
                mPvZoom = fmaxf(1.0f, fminf(8.0f, z));
                if (mPvZoom <= 1.01f) { mPvPanX = 0.0f; mPvPanY = 0.0f; }
                mDisplayDirty = true;
            }
        }
        mXmbTouchTracking = false;   // the leftover finger must not step/tap on lift
        mLastInputMs = now; mTouchWasDown = mTouchDown; return;
    }
    if (mPvPinchActive) {
        // dropped below two fingers: end the pinch and swallow the remaining finger
        // (single-finger drag resumes only after a fresh press).
        mPvPinchActive = false;
        mXmbTouchTracking = false;
        mLastInputMs = now; mTouchWasDown = mTouchDown; return;
    }

    if (downEdge) {
        mXmbTouchTracking = true; mXmbTouchMoved = false;
        mXmbTouchDownMs = now; mXmbTouchDownPX = px; mXmbTouchDownPY = py;
        mXmbTouchLastPX = px; mXmbTouchLastPY = py; mLastInputMs = now;
        mTouchWasDown = mTouchDown; return;
    }
    if (down && mXmbTouchTracking) {
        float dx = px - mXmbTouchDownPX, dy = py - mXmbTouchDownPY;
        if (!mXmbTouchMoved && dx * dx + dy * dy >= SLOP * SLOP) mXmbTouchMoved = true;
        // Interactive gestures over the bare photo (no panel/modal up). When zoomed a
        // single finger pans the photo; otherwise a horizontal drag scrubs the photo
        // toward its neighbour (which follows the finger 1:1 until release).
        if (mXmbTouchMoved && !mPvPanel && !mPvInfo && !mPvWpMode && !mPvTrimMode && !mPvPlChooserActive) {
            if (mPvZoom > 1.01f) {
                mPvPanX += (px - mXmbTouchLastPX);
                mPvPanY += (py - mXmbTouchLastPY);
                mPvPanning = true; mPvDragActive = false;
                mDisplayDirty = true;
            } else if (fabsf(dx) > fabsf(dy)) {
                mPvDragActive = true; mPvDragSettle = false;
                mPvDragDx = dx;
                mDisplayDirty = true;
            }
        }
        mXmbTouchLastPX = px; mXmbTouchLastPY = py; mLastInputMs = now;
        mTouchWasDown = mTouchDown; return;
    }
    if (!(upEdge && mXmbTouchTracking)) { mTouchWasDown = mTouchDown; return; }

    // ---- release: classify the gesture and dispatch by viewer state ----
    mXmbTouchTracking = false;
    mLastInputMs = now;
    mTouchWasDown = mTouchDown;
    float dx = px - mXmbTouchDownPX, dy = py - mXmbTouchDownPY;
    int64_t held = now - mXmbTouchDownMs;
    bool tap = !mXmbTouchMoved && held <= TAPMS && (dx * dx + dy * dy) <= TAPMAX * TAPMAX;
    bool horiz = fabsf(dx) > fabsf(dy);
    float W = (float)mWidth, H = (float)mHeight;

    // A finished interactive swipe settles to the neighbour (dragged past ~22% of the
    // width) or springs back; a finished pan just ends. Both consume the release.
    if (mPvDragActive) {
        mPvDragActive = false;
        float adx = mPvDragDx;
        if (fabsf(adx) > W * 0.22f) {
            mPvDragCommitDir = (adx < 0.0f) ? +1 : -1;
            mPvDragTo = (adx < 0.0f) ? -W : W;
        } else {
            mPvDragCommitDir = 0;
            mPvDragTo = 0.0f;
        }
        mPvDragFrom = adx; mPvDragSettle = true; mPvDragSettleStart = mEffectTime;
        mDisplayDirty = true;
        return;
    }
    if (mPvPanning) { mPvPanning = false; return; }

    // Set-as-wallpaper / trim / add-to-playlist modals keep their D-pad handling.
    if (mPvPlChooserActive || mPvWpMode || mPvTrimMode) return;

    // Top-left back chevron (only while the panel is up): a generous corner zone -> exit.
    // Full-panel placement (matches renderPhotoViewer), a comfortable finger target.
    if (tap && mPvPanel) {
        float acx = mWidth * 0.07f, acy = mHeight * 0.075f, r = fmaxf(mWidth, mHeight) * 0.055f;
        if (fabsf(px - acx) <= r && fabsf(py - acy) <= r) { closePhotoViewer(); return; }
    }

    if (mPvPanel) {
        if (!tap) return;
        int cnt; const PvCp* cp = pvCpTable(mPvSlideshow, &cnt);
        PvLayout vl = pvLayout(mWidth, mHeight, cp, cnt);   // SAME full-panel layout drawPvPanel renders
        float ih = vl.ih;
        if (mPvCpSub) {
            // Change Effect / Slideshow Speed / Style submenu -> shared XMB-style dialog. Tap a
            // row to apply it; tap inside the dialog (not a row) keeps it open; tap outside backs.
            if (!mPvCpSubOpts.empty()) {
                const char* title = trDyn(cp[mPvCpSel].label);
                int row = mediaOptDialogRowAt(title, mPvCpSubOpts, mPvCpSubSel, px, py);
                if (row >= 0) { mPvCpSubSel = row; pvPanelActivate(); return; }   // applies + closes
                if (row == -1) return;                                            // inside dialog, keep open
            }
            pvPanelBack();   // tap outside the dialog -> back to the panel
            return;
        }
        // Hit-test the control-panel cells (device space, nearest within a cell).
        float cellX = vl.cellX, cellY = vl.cellY;
        float ox = vl.ox, oy = vl.oy;
        int best = -1; float bestD = 1e9f;
        for (int i = 0; i < cnt; i++) {
            float cx = ox + cp[i].gx * cellX, cy = oy + (cp[i].gy - 1.0f) * cellY;
            if (fabsf(px - cx) <= cellX * 0.6f && fabsf(py - cy) <= cellY * 0.6f) {
                float d = fabsf(px - cx) + fabsf(py - cy);
                if (d < bestD) { bestD = d; best = i; }
            }
        }
        if (best >= 0) { mPvCpSelPrev = mPvCpSel; mPvCpFocusStart = mEffectTime; mPvCpSel = best; pvPanelActivate(); return; }
        pvPanelBack();   // tap off a cell -> close the panel
        return;
    }
    if (mPvInfo) {
        if (tap) mPvInfo = false;   // dismiss the EXIF overlay (mirrors SELECT)
        return;
    }
    // No overlay: Gallery gestures over the photo. Horizontal swipes are handled
    // interactively (mPvDragActive above); here only the vertical exit + tap remain.
    if (!tap && !horiz && dy > H * 0.18f && mPvZoom <= 1.01f) { closePhotoViewer(); return; }  // firm swipe down = exit
    if (tap)                                                  { openPvPanel(true); return; }   // tap = show controls (enlarged for touch)
}

void NanoMenu::drawPvPanel(float closeT) {
    if (mPvList.empty()) return;
    float t = (closeT >= 0.0f) ? closeT
            : (mPvCpAnimStart >= 0.0f ? fminf(1.0f, (mEffectTime - mPvCpAnimStart) / 0.2f) : 1.0f);
    if (t < 0) t = 0;
    int cnt; const PvCp* cp = pvCpTable(mPvSlideshow, &cnt);
    // Full-panel grid: spans the whole width, sized to the short side, centred (see ps3::mediaGrid
    // + pvLayout). Replaces the letterboxed XMB-frame layout that squeezed the icons on a square panel.
    PvLayout vl = pvLayout(mWidth, mHeight, cp, cnt);
    float cellX = vl.cellX, cellY = vl.cellY, ih = vl.ih;
    float ox = vl.ox - (1.0f - t) * vl.cellX * 0.5f;   // slide in from the left
    float oy = vl.oy;
    float pulse = 0.5f + 0.5f * cosf(mEffectTime * 2.0f * 3.14159f / 1.5f);
    for (int i = 0; i < cnt; i++) {
        const PvCp& b = cp[i];
        bool focus = (i == mPvCpSel);
        float cx = ox + b.gx * cellX, cy = oy + (b.gy - 1.0f) * cellY;   // centre the 3 rows on oy (music scheme)
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
        // Soft semi-transparent dark stroke (8-direction outline) so the silvery glyphs read on
        // ANY backdrop (a bright photo as well as the dark wave), plus a slight drop shadow.
        auto stroke = [&](GLuint tex, int arIdx, float al) {
            float r = ih * 0.045f, d = r * 0.7071f;
            glyph(tex, arIdx,  r, 0, 0, 0, 0, al); glyph(tex, arIdx, -r, 0, 0, 0, 0, al);
            glyph(tex, arIdx, 0,  r, 0, 0, 0, al); glyph(tex, arIdx, 0, -r, 0, 0, 0, al);
            glyph(tex, arIdx,  d, d, 0, 0, 0, al); glyph(tex, arIdx, -d, d, 0, 0, 0, al);
            glyph(tex, arIdx,  d,-d, 0, 0, 0, al); glyph(tex, arIdx, -d,-d, 0, 0, 0, al);
        };
        // Layered soft drop shadow (near dark cast + wider low-alpha falloff), under stroke+glyph.
        auto shadow = [&](GLuint tex, int arIdx, float m) {
            glyph(tex, arIdx, ih * 0.05f, ih * 0.075f, 0, 0, 0, 0.55f * m);   // near, dark
            glyph(tex, arIdx, ih * 0.09f, ih * 0.130f, 0, 0, 0, 0.30f * m);   // wider soft falloff
        };
        if (focus) {
            shadow(g, icn, 1.0f);
            stroke(g, icn, 0.55f);
            if (g) {
                float hh = ih * ps * 1.18f, ww = hh * pvIconAR(icn);
                drawIconTex(g, cx - ww * 0.5f, cy - hh * 0.5f, ww, hh, 0.86f, 0.92f, 1.0f, t * (0.18f + 0.16f * pulse));
            }
            glyph(g, icn, 0, 0, 1, 1, 1, 1.0f);
        } else {
            // Unfocused icons render at half opacity so the focused one stands out.
            shadow(g, icn, 0.5f);
            stroke(g, icn, 0.28f);
            glyph(g, icn, 0, 0, 1, 1, 1, 0.5f);
        }
        if (flash > 0.0f) glyph(g, icn, 0, 0, 1, 1, 1, flash);
    }
    // focused label + button-hint pill, centred at the grid origin (mirrors the web
    // photo viewer panel). The pill shows ONLY on a control with a physical-button
    // shortcut: "Display" (showinfo) is toggled by SELECT, the running-slideshow
    // "Play/Pause" (pause) is toggled by START. Every other control shows the label
    // only. (This differs from the music panel, where the web draws SELECT on every
    // control - so the music panel keeps its unconditional pill.)
    if (mPvCpSel >= 0 && mPvCpSel < cnt) {
        // focused label, centred under the grid. (SELECT/START button-hint pills dropped per
        // user request - meaningless on a touch panel.)
        const char* lab = (!strcmp(cp[mPvCpSel].act, "pause")) ? (mPvPaused ? "Play" : "Pause") : cp[mPvCpSel].label;
        float ls = (ih * 0.42f) / 16.0f, lw = measureText(lab, ls);
        drawText(lab, vl.cx - lw * 0.5f, ps3::baselineToTopY(vl.labBaseY, ls), ls, 1.0f, 1.0f, 1.0f, 0.95f * t);
    }
    // control submenu (Change Effect / Speed / Style) -> the shared XMB-style modal dialog,
    // centred over the icons; pvTouchFrame hit-tests the same geometry.
    if (mPvCpSub && !mPvCpSubOpts.empty()) {
        const char* title = trDyn(cp[mPvCpSel].label);
        drawMediaOptDialog(title, mPvCpSubOpts, mPvCpSubSel, t);
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
    // Real delete: capture the current photo, leave the viewer, and raise the shared Cancel/Delete
    // confirm over the grid/menu. On Delete, applyThemeSetting case 43 unlinks the file and rescans
    // the photo library. (The photo viewer has no yes/no dialog of its own, so we route through the
    // XMB dialog which renders once the viewer is closed.)
    std::string f, name;
    if (mPvIdx >= 0 && mPvIdx < (int)mPvList.size()) {
        int pi = mPvList[mPvIdx];
        if (pi >= 0 && pi < (int)mPhotos.size()) { f = mPhotos[pi].file; name = mPhotos[pi].name; }
    }
    if (f.empty()) return;
    closePhotoViewer();
    mMediaDelPaths.assign(1, f); mMediaDelLib = 1;
    mediaDeleteConfirm(std::string("Delete ") + name,
                       "This permanently deletes the photo from storage.");
}
void NanoMenu::pvShow3D() {
    // 3D display cannot render here; report it the way the firmware fallback does.
    pvShowMsg(trDyn("An error occurred while switching to display in 3D."), 1600.0f);
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
    int curPhoto = mPvList[mPvIdx];
    GLuint tex = pvTex(curPhoto, &iw, &ih);
    if (!tex) {   // full image not ready: cover-fill with the disk-cached thumbnail
        tex = photoThumb(curPhoto);
        if (curPhoto >= 0 && curPhoto < (int)mPhotos.size()) { iw = mPhotos[curPhoto].w; ih = mPhotos[curPhoto].h; }
    }
    if (tex && iw > 0 && ih > 0) {
        // scissor-clip to the crop frame, draw the photo cover-filling it.
        // Composed rotation+flip mapping (see scissorLogicalRect); the old
        // rotation-only switch here also mis-mapped the Y extent on pure
        // 90/270 rotations (case 90 used sy from lx but sx=ly instead of
        // H-ly-lh), so this partial-height rect was wrong even without flips.
        scissorLogicalRect(fx, fy, fw, fh);
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
        wallpaperApplyPick(f);
    }
    pvShowMsg(trDyn("The wallpaper has been set."), 1100.0f);
    // Launched from Theme Settings (picker mode): close the viewer + grid and drop back to the menu so the
    // chosen wallpaper is visible at once. Otherwise (the in-Photos "Set as Wallpaper" action) stay put.
    if (mWpPickTarget >= 0) {
        mWpPickTarget = -1;
        mPvActive = false;
        if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == PHOTO_GRID) { closePhotoGrid(); mPs3Stack.pop_back(); }
    }
}

// Write the chosen image file to the wallpaper prop for the active theme (XMB vs DSi) and target screen
// (0 top, 1 bottom), decode it into the render slot, and apply live. Setting an XMB wallpaper turns the
// wave off by default (materialize the prop so the Theme Settings row agrees). Render-thread only.
void NanoMenu::wallpaperApplyPick(const std::string& file) {
    int tgt = (mWpPickTarget >= 0) ? mWpPickTarget : 0;
    const bool dsi = mNdsTheme;
    const char* prop = dsi ? (tgt == 1 ? "persist.gammaos.nano.wp.dsi.bottom" : "persist.gammaos.nano.wp.dsi.top")
                           : (tgt == 1 ? "persist.gammaos.nano.wp.xmb.bottom" : "persist.gammaos.nano.wp.xmb.top");
    property_set(prop, file.c_str());
    // Any XMB wallpaper (top or bottom) turns the wave off: the wave toggle is global and a wallpaper only
    // shows on its panel while the wave is off, so a bottom-only wallpaper would otherwise stay hidden.
    if (!dsi) { property_set("persist.gammaos.nano.ps3xmb.wave", "0"); mXmbWave = false;
                mPs3BindCache.erase("XMB Wave"); }   // this flips the wave outside the chooser: drop the cached row so it re-reads
    // A video file on the TOP panel drives the single video decoder instead of a still.
    if (tgt == 0 && wpIsVideoPath(file)) {
        if (mWpTexTop) { glDeleteTextures(1, &mWpTexTop); mWpTexTop = 0; } mWpTopW = 0; mWpTopH = 0; mWpPathTop = file;
        mWpTopIsVideo = true;
        wpVideoStart(file);
        mDisplayDirty = true;
        return;
    }
    if (tgt == 0 && mWpTopIsVideo) { wpVideoStop(); mWpTopIsVideo = false; }
    // Decode the chosen still straight into the target slot. Do NOT go through loadWallpaperTextures(): it
    // re-reads the prop we just set, and property_set may not have propagated to this process's read cache
    // yet (a socket round-trip to property_service), so it would read the stale value and the live apply
    // would not show until the next restart.
    GLuint& tex  = (tgt == 1) ? mWpTexBottom : mWpTexTop;
    int&    w    = (tgt == 1) ? mWpBottomW   : mWpTopW;
    int&    h    = (tgt == 1) ? mWpBottomH   : mWpTopH;
    std::string& path = (tgt == 1) ? mWpPathBottom : mWpPathTop;
    if (tex) { glDeleteTextures(1, &tex); tex = 0; }
    w = 0; h = 0; path = file;
    tex = photoDecodeTex(file, 2048, &w, &h);
    if (!tex) path.clear();   // decode failed: fall back to the wave / field
    mDisplayDirty = true;
}

// Theme Settings -> open the Photos album grid to pick a wallpaper for the given target (0 top, 1 bottom).
// Reuses the whole Photos previewer: browse the thumbnail grid, pick a photo, crop, confirm.
void NanoMenu::openWallpaperPicker(int target) {
    mWpPickTarget = target;
    photoEnsureLoaded();
    photoDrainScanResults();   // publish any already-finished scan now, so the grid is built from the
                               // real library rather than the empty pre-scan list.
    std::vector<int> all;
    all.reserve(mPhotos.size());
    for (size_t i = 0; i < mPhotos.size(); i++) all.push_back((int)i);
    openPhotoGrid(all, trDyn("Select Wallpaper"), -1);   // openPhotoGrid clears mWpVideoPick (photo mode)
}

// Game Triangle menu -> open the Photos album grid to pick a custom cover for the focused game (the
// target ROM is already staged in mBoxartPickRom by xmbOptAction). Mirrors openWallpaperPicker, but a
// pick short-circuits straight into boxartApplyPick (no crop/still viewer), like the video picker.
void NanoMenu::openBoxartPicker() {
    mWpPickTarget = -1;        // not a wallpaper pick
    mWpVideoPick = false;      // not a video pick
    photoEnsureLoaded();
    photoDrainScanResults();   // publish any already-finished scan now (see openWallpaperPicker)
    std::vector<int> all;
    all.reserve(mPhotos.size());
    for (size_t i = 0; i < mPhotos.size(); i++) all.push_back((int)i);
    openPhotoGrid(all, trDyn("Select Boxart"), -1);   // openPhotoGrid clears mBoxartPick; set it AFTER
    mBoxartPick = true;
}

// Theme Settings -> open the same album grid but listing the VIDEO library, to pick a video wallpaper for
// the TOP screen (single HW decoder -> top only). Cells are film badges (no offline video thumbnails);
// selecting one short-circuits straight into wallpaperApplyPick -> wpVideoStart (no crop/still viewer).
void NanoMenu::openVideoWallpaperPicker() {
    mWpPickTarget = 0;   // video is top-only
    videoEnsureLoaded();
    videoDrainScanResults();   // publish any already-finished video scan (see openWallpaperPicker)
    std::vector<int> all;                 // grid list = 0..N-1 (grid indices)
    mWpPickVidList.clear();               // parallel: grid index -> mVideos index
    for (size_t i = 0; i < mVideos.size(); i++) { all.push_back((int)mWpPickVidList.size()); mWpPickVidList.push_back((int)i); }
    openPhotoGrid(all, trDyn("Select Video Wallpaper"), -1);   // clears mWpVideoPick; set it AFTER
    mWpVideoPick = true;
}

// Clear the active theme's wallpaper on both screens and bring the wave back (Theme Settings leaf action).
void NanoMenu::clearWallpaper() {
    const bool dsi = mNdsTheme;
    property_set(dsi ? "persist.gammaos.nano.wp.dsi.top"    : "persist.gammaos.nano.wp.xmb.top",    "");
    property_set(dsi ? "persist.gammaos.nano.wp.dsi.bottom" : "persist.gammaos.nano.wp.xmb.bottom", "");
    if (!dsi) { property_set("persist.gammaos.nano.ps3xmb.wave", "1"); mXmbWave = true;
                mPs3BindCache.erase("XMB Wave"); }   // wave restored outside the chooser: drop the cached row so it re-reads
    // Free the textures directly (the just-cleared props may not have propagated to this process's read
    // cache yet, so re-reading them could keep the old wallpaper alive).
    if (mWpTopIsVideo) { wpVideoStop(); mWpTopIsVideo = false; }
    if (mWpTexTop)    { glDeleteTextures(1, &mWpTexTop);    mWpTexTop = 0; }    mWpTopW = 0; mWpTopH = 0; mWpPathTop.clear();
    if (mWpTexBottom) { glDeleteTextures(1, &mWpTexBottom); mWpTexBottom = 0; } mWpBottomW = 0; mWpBottomH = 0; mWpPathBottom.clear();
    mDisplayDirty = true;
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
void NanoMenu::photoDeletePlaylist(int plIdx) {
    if (plIdx < 0 || plIdx >= (int)mPhotoPlaylists.size()) return;
    // Photo playlists live only in nano_photo.json (no backing file), so erase + save.
    mPhotoPlaylists.erase(mPhotoPlaylists.begin() + plIdx);
    savePhotoConfig();
    mPhotoCatsStale = true;   // rebuild the Photo column (Playlists count) at the settled root
}
// Remove one photo (matched by file path) from a playlist. Non-destructive: the image file stays.
void NanoMenu::photoRemoveFromPlaylist(int plIdx, const std::string& file) {
    if (plIdx < 0 || plIdx >= (int)mPhotoPlaylists.size() || file.empty()) return;
    auto& files = mPhotoPlaylists[plIdx].files;
    files.erase(std::remove(files.begin(), files.end(), file), files.end());
    savePhotoConfig();
}
// Reorder a photo within a playlist by swapping it with its neighbour (dir -1 = up/earlier,
// +1 = down/later). Matched by file path so it is robust to entries skipped when the grid built.
void NanoMenu::photoMoveInPlaylist(int plIdx, const std::string& file, int dir) {
    if (plIdx < 0 || plIdx >= (int)mPhotoPlaylists.size() || file.empty() || dir == 0) return;
    auto& files = mPhotoPlaylists[plIdx].files;
    int i = -1;
    for (int k = 0; k < (int)files.size(); k++) if (files[k] == file) { i = k; break; }
    if (i < 0) return;
    int j = i + (dir < 0 ? -1 : 1);
    if (j < 0 || j >= (int)files.size()) return;
    std::swap(files[i], files[j]);
    savePhotoConfig();
}
void NanoMenu::buildPhotoPlaylistsScreen(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.title = "Playlists"; out.screenKind = 0;
    { Ps3Item it; it.label = "Create New Playlist"; it.kind = PS3_PHOTO_PL_NEW;
      it.iconTex = iconTexForIcon(50); it.nmapTex = nmapForIcon(50); it.iconR = it.iconG = it.iconB = 1.0f;
      out.items.push_back(it); }
    for (size_t p = 0; p < mPhotoPlaylists.size(); p++) {
        Ps3Item it; it.label = mPhotoPlaylists[p].name; it.kind = PS3_PHOTO_PLAYLIST; it.a = (int)p;
        size_t n = mPhotoPlaylists[p].files.size();
        char v[32]; snprintf(v, sizeof(v), "%zu %s", n, n == 1 ? "Image" : "Images"); it.value = v;
        it.iconTex = iconTexForIcon(62); it.nmapTex = nmapForIcon(62); it.iconR = it.iconG = it.iconB = 1.0f;
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
    mPvPlChooserOpts.push_back(trDyn("New Playlist..."));
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
                pvShowMsg(trDyn("Added to the playlist"), 900.0f);
            });
        mOskPasswordMode = false; mOskPlaintext = true;   // a playlist name is plain text, not masked
    } else {
        photoAddToPlaylist(sel - 1, file);
        pvShowMsg(trDyn("Added to the playlist"), 900.0f);
    }
}
void NanoMenu::drawPvPlChooser() {
    float t = mPvPlChooserAnim; if (t < 0.004f) return;
    int W = mWidth, H = mHeight;
    drawQuad(0, 0, (float)W, (float)H, 0, 0, 0, 0.5f * t);
    float fs = PFS(26.0f), lh = PSZ(0.058f);
    int n = (int)mPvPlChooserOpts.size();
    const char* plTitle = trDyn("Add to Playlist");
    float mw = measureText(plTitle, fs);
    for (auto& o : mPvPlChooserOpts) mw = fmaxf(mw, measureText(o.c_str(), fs));
    float pw = mw + PXD(0.08f), ph = lh * (n + 1) + PSZ(0.05f);
    float px = (W - pw) * 0.5f, py = (H - ph) * 0.5f;
    drawQuad(px, py, pw, ph, 0.07f, 0.08f, 0.10f, 0.92f * t);
    float cx = W * 0.5f;
    float titleY = py + PSZ(0.05f);
    float tw = measureText(plTitle, fs);
    drawText(plTitle, cx - tw * 0.5f, ps3::baselineToTopY(titleY, fs), fs, 1, 1, 1, 0.95f * t);
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

// ---------------------------------------------------------------------------
// Multi-select (Delete Multiple) checkbox screen (web photoMulti). Copy Multiple was dropped.
// ---------------------------------------------------------------------------
void NanoMenu::photoMultiOpen(int mode) {
    if (mPhotoGridList.empty()) return;
    mPhotoMultiItems = mPhotoGridList;
    mPhotoMultiMode = mode;
    mPhotoMultiSel = (mPhotoGridCursor >= 0 && mPhotoGridCursor < (int)mPhotoMultiItems.size())
                         ? mPhotoGridCursor : 0;
    mPhotoMultiBtn = -1;
    mPhotoMultiChecked.clear();
    mPhotoMultiActive = true;
}
void NanoMenu::photoMultiClose() { mPhotoMultiActive = false; mPhotoMultiItems.clear(); mPhotoMultiChecked.clear(); }
void NanoMenu::photoMultiMove(int d) {
    if (mPhotoMultiBtn >= 0) { mPhotoMultiBtn += d; if (mPhotoMultiBtn < 0) mPhotoMultiBtn = 0; if (mPhotoMultiBtn > 2) mPhotoMultiBtn = 2; return; }
    int n = (int)mPhotoMultiItems.size(); if (n <= 0) return;
    mPhotoMultiSel += d; if (mPhotoMultiSel < 0) mPhotoMultiSel = 0; if (mPhotoMultiSel >= n) mPhotoMultiSel = n - 1;
}
void NanoMenu::photoMultiLR(int d) {
    if (d > 0) { if (mPhotoMultiBtn < 0) mPhotoMultiBtn = 0; }
    else       { if (mPhotoMultiBtn >= 0) mPhotoMultiBtn = -1; }
}
void NanoMenu::photoMultiActivate() {
    if (mPhotoMultiBtn == 0) {        // Select All
        for (int i = 0; i < (int)mPhotoMultiItems.size(); i++) mPhotoMultiChecked.insert(i);
        return;
    }
    if (mPhotoMultiBtn == 1) { mPhotoMultiChecked.clear(); return; }   // Clear All
    if (mPhotoMultiBtn == 2) {        // OK -> confirm
        // Real bulk delete of the checked photos. Copy Multiple was dropped, so only the
        // delete mode reaches here; collect the checked file paths, close the checkbox
        // screen, then run the shared Cancel/Delete confirm (applyThemeSetting case 43 ->
        // unlink + rescan). mPhotoMultiChecked holds indices into mPhotoMultiItems, whose
        // values are photo indices into mPhotos.
        mMediaDelPaths.clear();
        for (int i : mPhotoMultiChecked)
            if (i >= 0 && i < (int)mPhotoMultiItems.size()) {
                int pi = mPhotoMultiItems[i];
                if (pi >= 0 && pi < (int)mPhotos.size()) mMediaDelPaths.push_back(mPhotos[pi].file);
            }
        int n = (int)mMediaDelPaths.size();
        photoMultiClose();
        if (n > 0) {
            mMediaDelLib = 1;
            char body[192];
            snprintf(body, sizeof(body), "This permanently deletes %d photo%s from storage.",
                     n, n == 1 ? "" : "s");
            mediaDeleteConfirm(n == 1 ? std::string("Delete this photo")
                                      : (std::string("Delete ") + std::to_string(n) + " photos"),
                               body);
        }
        return;
    }
    // toggle the focused row
    if (mPhotoMultiChecked.count(mPhotoMultiSel)) mPhotoMultiChecked.erase(mPhotoMultiSel);
    else mPhotoMultiChecked.insert(mPhotoMultiSel);
}
void NanoMenu::renderPhotoMulti() {
    int W = mWidth, H = mHeight;
    drawQuad(0, 0, (float)W, (float)H, 0, 0, 0, 1.0f);
    float ts = fmaxf(1.0f, (float)H / 768.0f);
    // header (only the delete mode is reachable now; Copy Multiple was dropped)
    const char* hdr = "Select images to delete.";
    drawText(hdr, W * 0.10f, 44.0f * ts, 1.2f * ts, 1.0f, 1.0f, 1.0f, 1.0f);
    // scrolling list: checkbox + 16:9 thumb + name + date
    float rowH = 86.0f * ts, listTop = 110.0f * ts;
    int visRows = (int)((H - listTop - 70.0f * ts) / rowH); if (visRows < 1) visRows = 1;
    int n = (int)mPhotoMultiItems.size();
    int top = mPhotoMultiSel - visRows / 2; if (top > n - visRows) top = n - visRows; if (top < 0) top = 0;
    float cbCx = W * 0.07f, thX = W * 0.11f, thW = rowH * 1.55f, thH = thW * 9.0f / 16.0f, nameX = W * 0.11f + thW + W * 0.02f;
    std::vector<int> need;
    for (int r = 0; r < visRows && top + r < n; r++) {
        int i = top + r;
        float cy = listTop + r * rowH + rowH * 0.5f;
        bool focus = (i == mPhotoMultiSel && mPhotoMultiBtn < 0);
        // checkbox
        float cbS = 28.0f * ts, cbx = cbCx - cbS * 0.5f, cby = cy - cbS * 0.5f;
        drawQuad(cbx, cby, cbS, cbS, 0.9f, 0.9f, 0.92f, focus ? 0.95f : 0.6f);
        drawQuad(cbx + 2, cby + 2, cbS - 4, cbS - 4, 0.06f, 0.07f, 0.09f, 1.0f);
        if (mPhotoMultiChecked.count(i)) drawQuad(cbx + 5, cby + 5, cbS - 10, cbS - 10, 0.18f, 0.5f, 0.95f, 1.0f);
        // thumbnail
        int pIdx = mPhotoMultiItems[i];
        GLuint tex = 0; auto cit = mPhotoThumbCache.find(pIdx);
        if (cit != mPhotoThumbCache.end()) tex = cit->second; else need.push_back(pIdx);
        float tx = thX, tyy = cy - thH * 0.5f;
        drawQuad(tx - 1, tyy - 1, thW + 2, thH + 2, 0, 0, 0, focus ? 0.9f : 0.6f);
        if (tex) drawIconTex(tex, tx, tyy, thW, thH, 1, 1, 1, focus ? 1.0f : 0.9f);
        else     drawQuad(tx, tyy, thW, thH, 0.1f, 0.11f, 0.13f, 0.9f);
        // name + date
        if (pIdx >= 0 && pIdx < (int)mPhotos.size()) {
            const PhotoItem& p = mPhotos[pIdx];
            float ns = (focus ? 1.05f : 0.95f) * ts;
            drawText(p.name.c_str(), nameX, cy - 12.0f * ts, ns, focus ? 1.0f : 0.85f, focus ? 1.0f : 0.85f, focus ? 1.0f : 0.86f, 1.0f);
            drawText(fmtPhotoDate(p.date).c_str(), nameX, cy + 14.0f * ts, 0.85f * ts, 0.78f, 0.8f, 0.85f, 0.95f);
        }
    }
    if (!need.empty()) {
        int fast = 8, cold = 2;
        for (int pIdx : need) {
            if (fast <= 0 && cold <= 0) break;
            const PhotoItem& pp = mPhotos[pIdx];
            std::string cf = photoCacheFile(pp.file, pp.mtime, pp.sz, 't');
            bool cached = (access(cf.c_str(), F_OK) == 0);
            if (cached) { if (fast <= 0) continue; fast--; }
            else        { if (cold <= 0) continue; cold--; }
            photoThumb(pIdx);
        }
        photoThumbEvict();
    }
    // side buttons
    const char* btnLabels[3] = {trDyn("Select All"), trDyn("Clear All"), trDyn("OK")};
    float bw = W * 0.13f, bh = 46.0f * ts, bx = W * 0.78f, by0 = H * 0.40f, bpitch = 62.0f * ts;
    for (int b = 0; b < 3; b++) {
        bool sel = (mPhotoMultiBtn == b);
        float by = by0 + b * bpitch;
        drawRoundedRect(bx, by, bw, bh, 6.0f, sel ? 0.55f : 0.32f, sel ? 0.55f : 0.32f, sel ? 0.58f : 0.34f, 0.95f);
        float ls = 1.0f * ts, lw = measureText(btnLabels[b], ls);
        drawText(btnLabels[b], bx + (bw - lw) * 0.5f, by + bh * 0.5f - 12.0f * ts, ls, 1, 1, 1, 0.96f);
    }
    // footer hints
    drawText(trDyn("Enter: Toggle / Select    Back: Cancel"), W * 0.10f, (float)H - 34.0f * ts, 0.9f * ts, 0.78f, 0.82f, 0.9f, 0.95f);
    drawPhotoMsg();
}

} // namespace android
