// NanoCtrArt - per game art of 3DS ROMs for the 3DS theme: the SMDH icon shown on the grid and
// the CBMD banner (3D model + sound) shown on the top screen, from NanoCtrRom.
//
// ROMs are parsed on one worker thread (it exits after a short idle, so a still home has no
// thread) through NanoCtrRom::loadOrExtract, whose per ROM disk cache makes later boots cheap.
// Icons are uploaded on the render thread and kept in a small LRU (48x48 each); the banner of
// only the most recently asked ROM is kept in memory (its model pack and decoded sound).
#pragma once

#include <GLES2/gl2.h>

#include <condition_variable>
#include <cstdint>
#include <deque>
#include <list>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

#include "NanoCtrRom.h"

namespace android {
namespace ctr {

// A title's CBMD banner: the model pack (CPK1) and its sound.
struct RomBanner {
    std::string rom;
    std::vector<uint8_t> modelPack;
    // The language slot's own model (Kirby Planet Robobot keeps its title logo there), drawn
    // next to the common model; empty for most banners.
    std::vector<uint8_t> languagePack;
    RomSound sound;
    bool hasSound = false;
};

class RomArt {
public:
    explicit RomArt(std::string cacheDir);
    ~RomArt();

    // ROMs this cache handles (.3ds .cci .cxi .cia).
    static bool isCtrRom(const std::string& path);

    // Render thread. The SMDH icon (48x48) of a ROM, 0 until parsed (or when the ROM cannot be
    // read: encrypted, damaged). The first call queues the parse.
    GLuint icon(const std::string& rom);
    // The CBMD banner of a ROM, null until parsed (or when it has none). Queues the parse.
    // `pending` (optional) tells a banner still being parsed apart from a ROM without one.
    std::shared_ptr<const RomBanner> banner(const std::string& rom, bool* pending = nullptr);
    // Something finished since the last call (the grid redraws the art that arrived).
    bool landed();

    void releaseGl();   // GL context loss: icons are uploaded again on demand

private:
    struct Result {
        std::vector<uint8_t> icon;   // RGBA 48x48, empty when none
        std::shared_ptr<RomBanner> banner;
        bool failed = false;
    };
    void worker();
    void request(const std::string& rom, bool wantBanner);

    std::string mCacheDir;
    std::mutex mMu;
    std::condition_variable mCv;
    std::deque<std::pair<std::string, bool>> mQueue;      // rom, wants the banner
    std::unordered_map<std::string, Result> mDone;        // worker results awaiting the render thread
    std::unordered_map<std::string, int> mState;          // 1 queued, 2 parsed (icon state)
    std::string mBannerWanted;                            // the ROM whose banner to keep
    std::shared_ptr<RomBanner> mBanner;                   // the kept banner (render thread copy)
    std::unordered_map<std::string, bool> mBannerTried;   // banner parsed (true) for these ROMs
    std::thread mThread;
    bool mRunning = false;
    bool mStop = false;
    bool mLanded = false;
    // render thread: icon textures, least recently used first
    std::list<std::pair<std::string, GLuint>> mIconLru;
    std::unordered_map<std::string, std::list<std::pair<std::string, GLuint>>::iterator> mIcons;
};

}  // namespace ctr
}  // namespace android
