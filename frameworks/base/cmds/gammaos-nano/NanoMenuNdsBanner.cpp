// DSi theme: DS ROM banner icon + title. See NanoNdsBanner.h for the parser; this file is
// the cache, the lazy GL upload and the thread plumbing around it.
#include "NanoMenu.h"

#include <cutils/properties.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdlib.h>
#include <thread>
#include <utils/SystemClock.h>
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

// A ROM's identity for the in-memory result: the file that was parsed, not just its path. A
// path whose size or mtime changed (a copy that finished after the first scan, a replaced file)
// is parsed again; the disk cache is keyed the same way.
static uint64_t ndsIdentOf(const struct stat& st) { return ((uint64_t)st.st_size << 20) ^ (uint64_t)st.st_mtime; }

// Path-only copy of the last record parsed for a ROM path: "<dir>/p_<hash of path>.bin".
// On a fresh boot the ROM's storage (a card through vold, the FUSE view of internal
// storage) comes up well after nano, so the size/mtime key cannot even be formed and
// the carousel showed the generic cartridge until the volume arrived. The path copy is
// served in that window; once the file is reachable its identity is checked as usual
// and a changed file is parsed again.
static std::string pathCachePathFor(const std::string& rom) {
    char key[64];
    snprintf(key, sizeof(key), "p_%016llx", (unsigned long long)fnv1a64(rom));
    return std::string(kNdsBannerCacheDir) + "/" + key + ".bin";
}

// Read one record file. Returns true on a hit (a valid banner, or a known-bad "NDSX" mark).
static bool readBannerRecord(const std::string& cp, std::string& title, std::vector<uint8_t>& rgba) {
    bool hit = false;
    int fd = open(cp.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) return false;
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
    return hit;
}

// Write one record file atomically (temp + rename).
static bool writeBannerRecord(const std::string& cp, bool ok, const std::string& title, const std::vector<uint8_t>& rgba) {
    const std::string tmp = cp + ".tmp";
    int wfd = open(tmp.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    if (wfd < 0) return false;
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
    return wok;
}
static const int64_t kNdsBannerRetryMs = 5000;   // storage that is not up yet: try again after this

void NanoMenu::ndsBannerLoad(const std::string& rom) {
    {
        std::lock_guard<std::mutex> lk(mNdsBannerMu);
        if (mNdsBannerTitle.count(rom)) return;          // already parsed (or in flight elsewhere)
        mNdsBannerTitle[rom] = "";                       // claim it; filled below
    }
    std::string title;
    std::vector<uint8_t> rgba;
    struct stat st{};
    if (stat(rom.c_str(), &st) != 0) {
        // Not reachable right now: the volume it lives on (a card through vold, or the FUSE view
        // of internal storage) comes up well after nano on a fresh boot. Serve the last record
        // cached for this path so the icon and title show from the first frame; the identity is
        // left unknown (0), so ndsBannerWantsParseLocked re-checks the file once it is reachable
        // and a replaced file is parsed again. With no path record this is not a result: drop
        // the claim and let the next prefetch or draw try again shortly.
        std::string t;
        std::vector<uint8_t> px;
        if (readBannerRecord(pathCachePathFor(rom), t, px)) {
            std::lock_guard<std::mutex> lk(mNdsBannerMu);
            mNdsBannerTitle[rom] = t;
            mNdsBannerIdent[rom] = 0;
            if (!px.empty()) mNdsBannerPix[rom] = std::move(px);
            return;
        }
        std::lock_guard<std::mutex> lk(mNdsBannerMu);
        mNdsBannerTitle.erase(rom);
        mNdsBannerRetryAt[rom] = (int64_t)uptimeMillis() + kNdsBannerRetryMs;
        return;
    }
    if (S_ISREG(st.st_mode)) {
        const std::string cp = cachePathFor(rom, st);
        const std::string pp = pathCachePathFor(rom);
        bool hit = readBannerRecord(cp, title, rgba);
        if (hit) {
            // Keep the path copy current for the next boot (a cheap 4 KB write, once per
            // record; skipped when it is already this record).
            struct stat ps{};
            if (stat(pp.c_str(), &ps) != 0 || ps.st_size != (off_t)(rgba.empty() ? 4 : 8 + title.size() + rgba.size()))
                writeBannerRecord(pp, !rgba.empty(), title, rgba);
        } else {
            NdsBannerInfo info;
            const bool ok = ndsReadBanner(rom, info);
            if (ok) { title = info.title; rgba = std::move(info.rgba); }
            mkdir("/data/system/nano_cache", 0755);
            mkdir(kNdsBannerCacheDir, 0755);
            writeBannerRecord(cp, ok, title, rgba);
            writeBannerRecord(pp, ok, title, rgba);
            ALOGD("ndsbanner: %s -> %s title='%s'", rom.c_str(), ok ? "ok" : "rejected", title.c_str());
        }
    }
    std::lock_guard<std::mutex> lk(mNdsBannerMu);
    mNdsBannerTitle[rom] = title;
    mNdsBannerIdent[rom] = ndsIdentOf(st);
    mNdsBannerRetryAt.erase(rom);
    if (!rgba.empty()) mNdsBannerPix[rom] = std::move(rgba);
}

// Called with mNdsBannerMu held. True when the path may be queued now: not known, not queued,
// not in its retry hold-off. A known path whose file changed underneath (size or mtime) is
// forgotten first, so the new file is parsed and its old icon dropped from the render cache.
bool NanoMenu::ndsBannerWantsParseLocked(const std::string& rom) {
    auto it = mNdsBannerTitle.find(rom);
    if (it != mNdsBannerTitle.end()) {
        auto id = mNdsBannerIdent.find(rom);
        if (id == mNdsBannerIdent.end()) return false;   // in flight
        struct stat st{};
        if (stat(rom.c_str(), &st) != 0 || ndsIdentOf(st) == id->second) return false;
        // Served from the path copy while the storage was down (identity 0): now that the
        // file is reachable, re-parse only if its record is not the cached one (the keyed
        // record will hit for an unchanged file, so this is one stat and one open).
        if (id->second == 0) {
            std::string t; std::vector<uint8_t> px;
            if (readBannerRecord(cachePathFor(rom, st), t, px) && t == it->second) {
                id->second = ndsIdentOf(st);
                return false;
            }
        }
        mNdsBannerTitle.erase(it); mNdsBannerIdent.erase(id); mNdsBannerPix.erase(rom);
        mNdsBannerTexDrop.push_back(rom);
    }
    auto r = mNdsBannerRetryAt.find(rom);
    if (r != mNdsBannerRetryAt.end() && (int64_t)uptimeMillis() < r->second) return false;
    return std::find(mNdsBannerQueue.begin(), mNdsBannerQueue.end(), rom) == mNdsBannerQueue.end();
}

// Queue every DS ROM of a list for the worker (no I/O here: this is called from the scan
// thread and from the render thread's name re-apply). Known and queued paths are skipped.
// The DSi theme selection is read from the persisted property here rather than mNdsTheme:
// the cached game list (and the first name apply) is restored before the theme flags are
// initialised, and that is exactly the moment the banners should start parsing.
static bool ndsThemeSelected() {
    // Cached only once the property has a value: at the earliest boot ticks the persist
    // properties are not loaded yet and property_get returns "", which must not be taken as
    // "not the DSi theme" for the rest of the process.
    static int cached = -1;
    if (cached < 0) {
        char v[PROPERTY_VALUE_MAX] = {};
        if (property_get("persist.gammaos.nano.ndstheme", v, "") > 0 && v[0]) cached = (atoi(v) == 1) ? 1 : 0;
        else return false;
    }
    return cached == 1;
}

void NanoMenu::ndsBannerPrefetch(const std::vector<std::string>& roms) {
    if (!ndsThemeSelected()) return;
    std::lock_guard<std::mutex> lk(mNdsBannerMu);
    bool added = false;
    for (const auto& r : roms) {
        if (!isDsRomPath(r) || !ndsBannerWantsParseLocked(r)) continue;
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
    if (!ndsThemeSelected()) return;
    {   // icons of files that changed underneath: forget the upload so the new parse shows
        std::vector<std::string> drop;
        { std::lock_guard<std::mutex> lk(mNdsBannerMu); drop.swap(mNdsBannerTexDrop); }
        for (const auto& r : drop) {
            auto t = mNdsBannerTex.find(r);
            if (t == mNdsBannerTex.end()) continue;
            if (t->second.tex) glDeleteTextures(1, &t->second.tex);
            mNdsBannerTex.erase(t);
        }
    }
    if (!mNdsBannerLanded.load()) return;
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
            // Not prefetched (recent entry, theme switched live, or its storage was not up at
            // the first try): hand it to the worker so a zip never inflates on the render
            // thread. An unreachable path is retried at most every few seconds.
            if (ndsBannerWantsParseLocked(rom)) { mNdsBannerQueue.push_back(rom); ndsBannerStartWorkerLocked(); }
            queue = true;
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
