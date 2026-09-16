// DSi theme: DS ROM banner icon + title. See NanoNdsBanner.h for the parser; this file is
// the cache, the lazy GL upload and the thread plumbing around it.
#include "NanoMenu.h"

#include <cutils/properties.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <thread>
#include <log/log.h>

namespace android {

static const char* const kNdsBannerCacheDir = "/data/system/nano_cache/nds_banner";

static uint64_t fnv1a64(const std::string& s) {
    uint64_t h = 1469598103934665603ULL;
    for (unsigned char c : s) { h ^= c; h *= 1099511628211ULL; }
    return h;
}
static bool endsNoCase(const std::string& s, const char* suf) {
    const size_t n = strlen(suf);
    if (s.size() < n) return false;
    for (size_t i = 0; i < n; i++) { char a = s[s.size() - n + i]; if (a >= 'A' && a <= 'Z') a = (char)(a + 32); if (a != suf[i]) return false; }
    return true;
}
static bool isDsRomPath(const std::string& p) { return endsNoCase(p, ".nds") || endsNoCase(p, ".zip"); }

bool NanoMenu::ndsRomTitleEnabled() {
    return property_get_bool("persist.gammaos.nano.nds.romtitle", true);
}

bool NanoMenu::ndsIsDsRomItem(const Ps3Item& it, std::string* romPath) {
    std::string rp;
    if (it.kind == PS3_ROM) {
        if (it.a < 0 || it.a >= (int)mXmbSystems.size()) return false;
        const XmbSystem& sys = mXmbSystems[it.a];
        std::string dir = sys.romDir; for (auto& c : dir) if (c >= 'A' && c <= 'Z') c = (char)(c + 32);
        if (dir != "nds" && sys.shortname != "NDS") return false;
        if (it.b < 0 || it.b >= (int)sys.roms.size()) return false;
        rp = sys.roms[it.b];
    } else if (it.kind == PS3_RECENT) {
        if (it.a < 0 || it.a >= (int)mXmbRecent.size()) return false;
        if (mXmbRecent[it.a].systemName != "NDS") return false;
        rp = mXmbRecent[it.a].romPath;
    } else {
        return false;
    }
    if (!isDsRomPath(rp)) return false;
    if (romPath) *romPath = rp;
    return true;
}

// Disk cache record: "NDSB" + u32 title length + title + 4096 bytes RGBA, or "NDSX" for a
// ROM that did not validate (so a bad file is not re-parsed every boot). Keyed by path,
// size and mtime, so a replaced file re-parses.
static std::string cachePathFor(const std::string& rom, const struct stat& st) {
    char key[64];
    snprintf(key, sizeof(key), "%016llx", (unsigned long long)fnv1a64(rom + "|" + std::to_string((long long)st.st_size) + "|" + std::to_string((long long)st.st_mtime)));
    return std::string(kNdsBannerCacheDir) + "/" + key + ".bin";
}

void NanoMenu::ndsBannerLoad(const std::string& rom) {
    {
        std::lock_guard<std::mutex> lk(mNdsBannerMu);
        if (mNdsBannerTitle.count(rom)) return;          // already parsed (or in flight elsewhere)
        mNdsBannerTitle[rom] = "";                       // claim it; filled below
    }
    std::string title;
    std::vector<uint8_t> rgba;
    struct stat st{};
    if (stat(rom.c_str(), &st) == 0 && S_ISREG(st.st_mode)) {
        const std::string cp = cachePathFor(rom, st);
        bool hit = false;
        int fd = open(cp.c_str(), O_RDONLY | O_CLOEXEC);
        if (fd >= 0) {
            uint8_t magic[4] = {};
            if (read(fd, magic, 4) == 4) {
                if (!memcmp(magic, "NDSX", 4)) hit = true;                       // known bad
                else if (!memcmp(magic, "NDSB", 4)) {
                    uint32_t tl = 0;
                    if (read(fd, &tl, 4) == 4 && tl <= 256) {
                        std::string t(tl, '\0');
                        std::vector<uint8_t> px(32 * 32 * 4);
                        if ((tl == 0 || read(fd, &t[0], tl) == (ssize_t)tl) && read(fd, px.data(), px.size()) == (ssize_t)px.size()) {
                            title = t; rgba = std::move(px); hit = true;
                        }
                    }
                }
            }
            close(fd);
        }
        if (!hit) {
            NdsBannerInfo info;
            const bool ok = ndsReadBanner(rom, info);
            if (ok) { title = info.title; rgba = std::move(info.rgba); }
            mkdir("/data/system/nano_cache", 0755);
            mkdir(kNdsBannerCacheDir, 0755);
            const std::string tmp = cp + ".tmp";
            int wfd = open(tmp.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
            if (wfd >= 0) {
                bool wok;
                if (ok && rgba.size() == 32 * 32 * 4) {
                    const uint32_t tl = (uint32_t)title.size();
                    wok = write(wfd, "NDSB", 4) == 4 && write(wfd, &tl, 4) == 4
                          && (tl == 0 || write(wfd, title.data(), tl) == (ssize_t)tl)
                          && write(wfd, rgba.data(), rgba.size()) == (ssize_t)rgba.size();
                } else {
                    wok = write(wfd, "NDSX", 4) == 4;
                }
                close(wfd);
                if (wok) rename(tmp.c_str(), cp.c_str()); else unlink(tmp.c_str());
            }
            ALOGD("ndsbanner: %s -> %s title='%s'", rom.c_str(), ok ? "ok" : "rejected", title.c_str());
        }
    }
    std::lock_guard<std::mutex> lk(mNdsBannerMu);
    mNdsBannerTitle[rom] = title;
    if (!rgba.empty()) mNdsBannerPix[rom] = std::move(rgba);
}

// Queue every DS ROM of a list for the worker (no I/O here: this is called from the scan
// thread and from the render thread's name re-apply). Known and queued paths are skipped.
// The DSi theme selection is read from the persisted property here rather than mNdsTheme:
// the cached game list (and the first name apply) is restored before the theme flags are
// initialised, and that is exactly the moment the banners should start parsing.
static bool ndsThemeSelected() {
    static int cached = -1;
    if (cached < 0) cached = (property_get_int32("persist.gammaos.nano.ndstheme", 0) == 1) ? 1 : 0;
    return cached == 1;
}

void NanoMenu::ndsBannerPrefetch(const std::vector<std::string>& roms) {
    if (!ndsThemeSelected()) return;
    std::lock_guard<std::mutex> lk(mNdsBannerMu);
    bool added = false;
    for (const auto& r : roms) {
        if (!isDsRomPath(r) || mNdsBannerTitle.count(r)) continue;
        if (std::find(mNdsBannerQueue.begin(), mNdsBannerQueue.end(), r) != mNdsBannerQueue.end()) continue;
        mNdsBannerQueue.push_back(r); added = true;
    }
    if (added) { ndsBannerStartWorkerLocked(); mNdsBannerCv.notify_one(); }
}

void NanoMenu::ndsBannerStartWorkerLocked() {
    if (mNdsBannerWorkerUp) return;
    mNdsBannerWorkerUp = true;
    std::thread([this]() {
        nanoThreadNormalPriority();
        setpriority(PRIO_PROCESS, (int)syscall(SYS_gettid), 10);
        for (;;) {
            std::string job;
            {
                std::unique_lock<std::mutex> lk(mNdsBannerMu);
                mNdsBannerCv.wait(lk, [this] { return !mNdsBannerQueue.empty(); });
                job = mNdsBannerQueue.front(); mNdsBannerQueue.pop_front();
            }
            ndsBannerLoad(job);
            mNdsBannerLanded.store(true);
        }
    }).detach();
}

// Once per frame on the render thread: when the worker has finished a batch, re-derive the
// DS systems' display names (banner titles now known) and rebuild the carousel once.
void NanoMenu::ndsBannerTick() {
    if (!ndsThemeSelected() || !mNdsBannerLanded.load()) return;
    {
        std::lock_guard<std::mutex> lk(mNdsBannerMu);
        if (!mNdsBannerQueue.empty()) return;   // let the batch finish: one re-sort, not one per ROM
    }
    mNdsBannerLanded.store(false);
    if (!ndsRomTitleEnabled()) return;
    for (auto& sys : mXmbSystems) {
        std::string dir = sys.romDir; for (auto& c : dir) if (c >= 'A' && c <= 'Z') c = (char)(c + 32);
        if (dir == "nds" || sys.shortname == "NDS") applyRomNameOverrides(sys);
    }
    applyRomNameOverridesToRecents();
    mPs3CatsStale = true;
    mDisplayDirty = true;
}

std::string NanoMenu::ndsBannerTitleFor(const std::string& rom) {
    std::lock_guard<std::mutex> lk(mNdsBannerMu);
    auto it = mNdsBannerTitle.find(rom);
    return it == mNdsBannerTitle.end() ? std::string() : it->second;
}

static GLuint ndsUploadIcon(const std::vector<uint8_t>& px) {
    GLuint tex = 0; glGenTextures(1, &tex); glBindTexture(GL_TEXTURE_2D, tex);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 32, 32, 0, GL_RGBA, GL_UNSIGNED_BYTE, px.data());
    // Pixel art: keep the 32x32 crisp when scaled onto the tile.
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    return tex;
}

GLuint NanoMenu::ndsBannerTex(const std::string& rom) {
    auto it = mNdsBannerTex.find(rom);
    if (it != mNdsBannerTex.end()) return it->second.tex;
    bool known = false, queue = false;
    std::vector<uint8_t> px;
    {
        std::lock_guard<std::mutex> lk(mNdsBannerMu);
        auto t = mNdsBannerTitle.find(rom);
        known = (t != mNdsBannerTitle.end());
        auto p = mNdsBannerPix.find(rom);
        if (p != mNdsBannerPix.end()) { px = std::move(p->second); mNdsBannerPix.erase(p); }
        if (!known) {
            // Not prefetched (recent entry, theme switched live): hand it to the worker so a
            // zip never inflates on the render thread.
            if (std::find(mNdsBannerQueue.begin(), mNdsBannerQueue.end(), rom) == mNdsBannerQueue.end())
                mNdsBannerQueue.push_back(rom);
            queue = true;
            ndsBannerStartWorkerLocked();
        }
    }
    if (queue) { mNdsBannerCv.notify_one(); return 0; }
    if (px.size() != 32 * 32 * 4) {
        // Parsed but no icon (rejected file): remember the miss so the lookup stops here.
        // A parse still in flight on the worker has no title entry yet, so it is retried.
        if (known) mNdsBannerTex[rom] = NdsBannerTexEntry{};
        return 0;
    }
    if (mNdsBannerTex.size() >= 512) ndsBannerFreeAll();
    NdsBannerTexEntry e; e.tex = ndsUploadIcon(px);
    mNdsBannerTex[rom] = e;
    return e.tex;
}

void NanoMenu::ndsBannerFreeAll() {
    for (auto& kv : mNdsBannerTex) if (kv.second.tex) glDeleteTextures(1, &kv.second.tex);
    mNdsBannerTex.clear();
}

} // namespace android
