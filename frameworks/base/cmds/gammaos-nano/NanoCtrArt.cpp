// NanoCtrArt - see NanoCtrArt.h.
#include "NanoCtrArt.h"

#include <sys/stat.h>

#include <algorithm>
#include <chrono>
#include <cstring>

#include <log/log.h>

#undef LOG_TAG
#define LOG_TAG "NanoCtr"

namespace android {
namespace ctr {

namespace {
const size_t kMaxIcons = 160;              // 48x48 RGBA each (9 KB)
const auto kIdleExit = std::chrono::seconds(3);
const size_t kMaxQueued = 48;               // parses waiting at most (newest first)
}  // namespace

RomArt::RomArt(std::string cacheDir) : mCacheDir(std::move(cacheDir)) {
    mkdir(mCacheDir.c_str(), 0771);
}

RomArt::~RomArt() {
    {
        std::lock_guard<std::mutex> lk(mMu);
        mStop = true;
    }
    mCv.notify_all();
    if (mThread.joinable()) mThread.join();
}

bool RomArt::isCtrRom(const std::string& path) {
    const size_t dot = path.rfind('.');
    if (dot == std::string::npos) return false;
    std::string ext = path.substr(dot + 1);
    for (char& c : ext) c = (char)tolower((unsigned char)c);
    return ext == "3ds" || ext == "cci" || ext == "cxi" || ext == "cia";
}

void RomArt::request(const std::string& rom, bool wantBanner) {
    std::lock_guard<std::mutex> lk(mMu);
    for (auto& q : mQueue)
        if (q.first == rom) { q.second = q.second || wantBanner; return; }
    // the newest request first: the focused title before the rest of the screen
    mQueue.emplace_front(rom, wantBanner);
    // a fast scroll through a large library asks for far more than it shows: what is still
    // waiting beyond a few screens is dropped (it is asked for again when it comes back)
    while (mQueue.size() > kMaxQueued) {
        mState.erase(mQueue.back().first);
        mQueue.pop_back();
    }
    if (!mRunning) {
        if (mThread.joinable()) mThread.join();
        mRunning = true;
        mThread = std::thread(&RomArt::worker, this);
    }
    mCv.notify_one();
}

void RomArt::worker() {
    std::unique_lock<std::mutex> lk(mMu);
    while (!mStop) {
        if (mQueue.empty()) {
            if (!mCv.wait_for(lk, kIdleExit, [this] { return mStop || !mQueue.empty(); })) break;
            continue;
        }
        auto job = mQueue.front();
        mQueue.pop_front();
        lk.unlock();
        RomAssets a;
        std::string err;
        Result r;
        if (loadOrExtract(job.first, mCacheDir, &a, &err, "USA", "English")) {
            r.icon = std::move(a.icon48);
            r.noBanner = !(a.hasBanner && !a.modelPack.empty());
            if (!r.noBanner) {
                r.banner = std::make_shared<RomBanner>();
                r.banner->rom = job.first;
                if (job.second) {
                    r.banner->modelPack = std::move(a.modelPack);
                    r.banner->languagePack = std::move(a.languageModelPack);
                    r.banner->hasSound = a.hasSound;
                    if (a.hasSound) r.banner->sound = std::move(a.sound);
                }
            }
        } else {
            r.failed = true;
            ALOGW("ctr: no 3DS art for %s: %s", job.first.c_str(), err.c_str());
        }
        lk.lock();
        // a banner only matters while it is still the one wanted
        if (r.banner && (!job.second || job.first != mBannerWanted)) {
            r.banner->modelPack.clear();
            r.banner->languagePack.clear();
        }
        // keep a banner a newer icon-only parse would drop
        auto old = mDone.find(job.first);
        if (old != mDone.end() && old->second.banner && !old->second.banner->modelPack.empty() &&
            (!r.banner || r.banner->modelPack.empty()))
            r.banner = old->second.banner;
        mDone[job.first] = std::move(r);
        mLanded = true;
    }
    mRunning = false;
}

GLuint RomArt::icon(const std::string& rom) {
    auto f = mIcons.find(rom);
    if (f != mIcons.end()) {
        mIconLru.splice(mIconLru.end(), mIconLru, f->second);   // most recently used
        return f->second->second;
    }
    std::vector<uint8_t> px;
    bool done = false, queue = false;
    {
        std::lock_guard<std::mutex> lk(mMu);
        auto d = mDone.find(rom);
        if (d != mDone.end()) {
            done = true;
            px.swap(d->second.icon);   // uploaded below: the pixels are not kept
        } else if (!mState.count(rom)) {
            mState[rom] = 1;
            queue = true;
        }
    }
    if (queue) request(rom, false);
    if (!done) return 0;
    GLuint tex = 0;
    if (px.size() == 48u * 48u * 4u) {
        glGenTextures(1, &tex);
        glBindTexture(GL_TEXTURE_2D, tex);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 48, 48, 0, GL_RGBA, GL_UNSIGNED_BYTE, px.data());
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }
    // the texture (or the miss) is kept so the ROM is not parsed again
    mIconLru.emplace_back(rom, tex);
    mIcons[rom] = std::prev(mIconLru.end());
    while (mIconLru.size() > kMaxIcons) {
        auto& old = mIconLru.front();
        if (old.second) glDeleteTextures(1, &old.second);
        mIcons.erase(old.first);
        {
            std::lock_guard<std::mutex> lk(mMu);
            mState.erase(old.first);
            mDone.erase(old.first);
        }
        mIconLru.pop_front();
    }
    return tex;
}

std::shared_ptr<const RomBanner> RomArt::banner(const std::string& rom, bool* pending) {
    if (pending) *pending = false;
    for (size_t i = 0; i < mBanners.size(); i++) {
        if (mBanners[i]->rom != rom) continue;
        auto b = mBanners[i];
        mBanners.erase(mBanners.begin() + i);
        mBanners.push_back(b);   // most recently used
        return b;
    }
    std::shared_ptr<RomBanner> got;
    bool ask = false;
    {
        std::lock_guard<std::mutex> lk(mMu);
        mBannerWanted = rom;
        auto d = mDone.find(rom);
        if (d != mDone.end()) {
            if (d->second.failed || d->second.noBanner) return nullptr;   // known: none
            if (d->second.banner && !d->second.banner->modelPack.empty()) {
                got = d->second.banner;
                d->second.banner.reset();   // the render thread's list holds it now
            }
        }
        if (!got) {
            // not parsed, or parsed for its icon only: parse (again) with the banner
            bool queued = false;
            for (const auto& q : mQueue) if (q.first == rom && q.second) queued = true;
            ask = !queued;
            if (pending) *pending = true;
        }
    }
    if (got) {
        mBanners.push_back(got);
        while (mBanners.size() > 3) mBanners.erase(mBanners.begin());
        return got;
    }
    if (ask) request(rom, true);
    return nullptr;
}

bool RomArt::landed() {
    std::lock_guard<std::mutex> lk(mMu);
    const bool l = mLanded;
    mLanded = false;
    return l;
}

void RomArt::releaseGl() {
    for (auto& e : mIconLru) if (e.second) glDeleteTextures(1, &e.second);
    mIconLru.clear();
    mIcons.clear();
    mBanners.clear();
    std::lock_guard<std::mutex> lk(mMu);
    mState.clear();
    mDone.clear();
}

}  // namespace ctr
}  // namespace android
