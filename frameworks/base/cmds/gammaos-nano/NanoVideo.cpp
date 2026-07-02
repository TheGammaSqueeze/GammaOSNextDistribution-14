#include "NanoVideo.h"
#include "NanoTsDescramble.h"  // shared MPEG-TS descramble data source
#include "NanoHls.h"           // in-process HTTP/HLS fetcher (IPTV streaming)

#include <GLES2/gl2ext.h>      // GL_TEXTURE_EXTERNAL_OES
#include <fcntl.h>
#include <time.h>
#include <unistd.h>
#include <chrono>
#include <cstring>

#include <gui/BufferQueue.h>
#include <gui/IGraphicBufferConsumer.h>
#include <gui/IGraphicBufferProducer.h>

#include <android/log.h>
#include <cutils/properties.h>  // persist.gammaos.nano.vid.mpeg2avoffms (MPEG-2 .ts A/V presentation skew)
#define LOGV(...) __android_log_print(ANDROID_LOG_INFO, "nanovideo", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "nanovideo", __VA_ARGS__)

using android::BufferQueue;
using android::GLConsumer;
using android::IGraphicBufferConsumer;
using android::IGraphicBufferProducer;
using android::Surface;
using android::sp;
using android::tsNeedsDescramble;
using android::tsMakeDataSource;
using android::tsFreeDataSource;

static int64_t monoNs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

// MPEG-TS descramble helpers (tsNeedsDescramble / tsMakeDataSource / tsFreeDataSource)
// now live in NanoTsDescramble.{h,cpp}, shared with the audio path (NanoAudio).

static std::string codecShortName(const char* mime) {
    if (!mime) return "";
    std::string m = mime;
    if (m == "video/avc")        return "AVC";
    if (m == "video/hevc")       return "HEVC";
    if (m == "video/x-vnd.on2.vp8") return "VP8";
    if (m == "video/x-vnd.on2.vp9") return "VP9";
    if (m == "video/av01")       return "AV1";
    if (m == "video/mp4v-es")    return "MPEG4";
    if (m == "audio/mp4a-latm")  return "AAC";
    if (m == "audio/mpeg")       return "MP3";
    if (m == "audio/ac3")        return "AC3";
    if (m == "audio/raw")        return "PCM";
    size_t sl = m.find('/');
    std::string s = (sl != std::string::npos) ? m.substr(sl + 1) : m;
    for (auto& c : s) if (c >= 'a' && c <= 'z') c -= 32;
    return s;
}

bool NanoVideo::probe(const std::string& path, Meta& out) {
    int fd = ::open(path.c_str(), O_RDONLY);
    if (fd < 0) return false;
    off_t len = lseek(fd, 0, SEEK_END);
    lseek(fd, 0, SEEK_SET);
    AMediaExtractor* ex = AMediaExtractor_new();
    // Scrambled-TS files (CA descriptor in the PMT) need the descramble data source or
    // the extractor reports the tracks as "...scrambled". Use it for the probe too so
    // the library shows the real codec/size, not SCRAMBLED.
    AMediaDataSource* ds = nullptr; void* ud = nullptr; int pmtPid = -1;
    media_status_t st;
    if (tsNeedsDescramble(fd, pmtPid)) {
        ds = tsMakeDataSource(fd, (off64_t)len, pmtPid, &ud);
        st = ds ? AMediaExtractor_setDataSourceCustom(ex, ds) : AMEDIA_ERROR_UNKNOWN;
    } else {
        st = AMediaExtractor_setDataSourceFd(ex, fd, 0, len);
    }
    ::close(fd);
    auto cleanup = [&]() {
        AMediaExtractor_delete(ex);
        tsFreeDataSource(ds, ud);   // after the extractor (it read through the source)
    };
    if (st != AMEDIA_OK) { cleanup(); return false; }
    bool gotVideo = false;
    size_t n = AMediaExtractor_getTrackCount(ex);
    for (size_t i = 0; i < n; i++) {
        AMediaFormat* f = AMediaExtractor_getTrackFormat(ex, i);
        const char* mime = nullptr;
        if (AMediaFormat_getString(f, AMEDIAFORMAT_KEY_MIME, &mime) && mime) {
            int64_t durUs = 0;
            if (AMediaFormat_getInt64(f, AMEDIAFORMAT_KEY_DURATION, &durUs) && durUs / 1e6 > out.durationSec)
                out.durationSec = durUs / 1e6;
            if (!strncmp(mime, "video/", 6)) {
                int32_t w = 0, h = 0;
                AMediaFormat_getInt32(f, AMEDIAFORMAT_KEY_WIDTH, &w);
                AMediaFormat_getInt32(f, AMEDIAFORMAT_KEY_HEIGHT, &h);
                if (w > 0) out.width = w;
                if (h > 0) out.height = h;
                out.vcodec = codecShortName(mime);
                gotVideo = true;
            } else if (!strncmp(mime, "audio/", 6) && out.acodec.empty()) {
                out.acodec = codecShortName(mime);
            }
        }
        AMediaFormat_delete(f);
    }
    cleanup();
    return gotVideo;
}

bool NanoVideo::probeUrl(const std::string& url, Meta& out) {
    AMediaExtractor* ex = AMediaExtractor_new();
    media_status_t st = AMediaExtractor_setDataSource(ex, url.c_str());
    if (st != AMEDIA_OK) { AMediaExtractor_delete(ex); return false; }
    bool gotVideo = false;
    size_t n = AMediaExtractor_getTrackCount(ex);
    for (size_t i = 0; i < n; i++) {
        AMediaFormat* f = AMediaExtractor_getTrackFormat(ex, i);
        const char* mime = nullptr;
        if (AMediaFormat_getString(f, AMEDIAFORMAT_KEY_MIME, &mime) && mime) {
            int64_t durUs = 0;
            if (AMediaFormat_getInt64(f, AMEDIAFORMAT_KEY_DURATION, &durUs) && durUs / 1e6 > out.durationSec)
                out.durationSec = durUs / 1e6;
            if (!strncmp(mime, "video/", 6)) {
                int32_t w = 0, h = 0;
                AMediaFormat_getInt32(f, AMEDIAFORMAT_KEY_WIDTH, &w);
                AMediaFormat_getInt32(f, AMEDIAFORMAT_KEY_HEIGHT, &h);
                if (w > 0) out.width = w;
                if (h > 0) out.height = h;
                out.vcodec = codecShortName(mime);
                gotVideo = true;
            } else if (!strncmp(mime, "audio/", 6) && out.acodec.empty()) {
                out.acodec = codecShortName(mime);
            }
        }
        AMediaFormat_delete(f);
    }
    AMediaExtractor_delete(ex);
    return gotVideo;
}

// Synchronous wrapper (openBegin + openAsyncRun on the calling thread). Post-refactor the
// render-thread player never calls this; it is kept only for probing / non-render callers.
bool NanoVideo::open(const std::string& path) {
    if (!openBegin(0, 0)) return false;
    return openAsyncRun(path);
}

// RENDER THREAD (EGL current): allocate the GL output pipeline only, sized by hint. The
// blocking open work runs later on a worker via openAsyncRun*, reusing this GL state.
bool NanoVideo::openBegin(int wHint, int hHint) {
    // Defensive soft reset if reused (in the real flow vidBeginOpen always passes a fresh
    // object, so this never has a live worker to join on the render thread).
    if (mOpen || mCodec || mEx || mWorker.joinable()) resetForReopen();
    mDisplayAspect.store(0.0f);
    mCancel.store(false);
    mFed = false;
    mVideoTrack = -1;
    mDurationSec = 0.0;
    mWidth = wHint > 0 ? wHint : 1;
    mHeight = hHint > 0 ? hHint : 1;

    glGenTextures(1, &mTexId);
    if (!mTexId) { LOGE("openBegin: glGenTextures failed"); return false; }
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTexId);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);

    sp<IGraphicBufferProducer> producer;
    sp<IGraphicBufferConsumer> consumer;
    BufferQueue::createBufferQueue(&producer, &consumer);
    mConsumer = new GLConsumer(consumer, mTexId, GL_TEXTURE_EXTERNAL_OES, true, false);
    mConsumer->setName(android::String8("NanoVideo"));
    mConsumer->setDefaultBufferSize(mWidth, mHeight);
    mSurface = new Surface(producer);
    return true;
}

// WORKER THREAD: the blocking part of open() - extractor + codec - reusing the GL from
// openBegin. Polls mCancel between steps; on failure returns false WITHOUT GL teardown
// (the owner frees on the render thread via releaseAsync/finishRelease). Spawns the decode
// worker only on full success.
bool NanoVideo::openAsyncRun(const std::string& path) {
    if (mCodec || mEx || mFed) resetForReopen();   // fed->normal fallback reuse on the same object
    mDisplayAspect.store(0.0f);   // non-fed extractor path: square pixels

    int fd = ::open(path.c_str(), O_RDONLY);
    if (fd < 0) { LOGE("open fd failed: %s", path.c_str()); return false; }
    off_t len = lseek(fd, 0, SEEK_END);
    lseek(fd, 0, SEEK_SET);

    mEx = AMediaExtractor_new();
    int pmtPid = -1;
    media_status_t st;
    if (tsNeedsDescramble(fd, pmtPid)) {
        mDataSource = tsMakeDataSource(fd, (off64_t)len, pmtPid, &mTsPatch, /*stripAudio=*/true);
        st = mDataSource ? AMediaExtractor_setDataSourceCustom(mEx, mDataSource) : AMEDIA_ERROR_UNKNOWN;
        if (st == AMEDIA_OK) LOGV("TS descramble active (PMT pid %d, audio stripped) for %s", pmtPid, path.c_str());
    } else {
        st = AMediaExtractor_setDataSourceFd(mEx, fd, 0, len);
    }
    ::close(fd);   // the extractor / our data source keep their own dup
    if (st != AMEDIA_OK) { LOGE("setDataSource failed (%d)", st); return false; }
    if (mCancel.load()) return false;

    size_t nTracks = AMediaExtractor_getTrackCount(mEx);
    const char* mime = nullptr;
    AMediaFormat* fmt = nullptr;
    for (size_t i = 0; i < nTracks; i++) {
        AMediaFormat* f = AMediaExtractor_getTrackFormat(mEx, i);
        const char* m = nullptr;
        if (AMediaFormat_getString(f, AMEDIAFORMAT_KEY_MIME, &m) && m && !strncmp(m, "video/", 6)) {
            mVideoTrack = (int)i; fmt = f; mime = m; break;
        }
        AMediaFormat_delete(f);
    }
    if (mVideoTrack < 0 || !fmt) { LOGE("no video track in %s", path.c_str()); return false; }

    int32_t w = 0, h = 0; int64_t durUs = 0;
    AMediaFormat_getInt32(fmt, AMEDIAFORMAT_KEY_WIDTH, &w);
    AMediaFormat_getInt32(fmt, AMEDIAFORMAT_KEY_HEIGHT, &h);
    AMediaFormat_getInt64(fmt, AMEDIAFORMAT_KEY_DURATION, &durUs);
    mWidth = w > 0 ? w : 1; mHeight = h > 0 ? h : 1;
    mDurationSec = durUs > 0 ? durUs / 1e6 : 0.0;
    if (mConsumer != nullptr) mConsumer->setDefaultBufferSize(mWidth, mHeight);   // resize from hint (IPC, not GL)

    AMediaExtractor_selectTrack(mEx, mVideoTrack);
    std::string mimeStr = mime;   // copy before fmt is deleted

    mCodec = AMediaCodec_createDecoderByType(mimeStr.c_str());
    if (!mCodec) { LOGE("createDecoderByType(%s) failed", mimeStr.c_str()); AMediaFormat_delete(fmt); return false; }
    if (mCancel.load()) { AMediaFormat_delete(fmt); return false; }
    media_status_t cs = AMediaCodec_configure(mCodec, fmt, mSurface.get(), nullptr, 0);
    AMediaFormat_delete(fmt);
    if (cs != AMEDIA_OK) { LOGE("codec configure failed (%d)", cs); return false; }
    if (mCancel.load()) return false;
    if (AMediaCodec_start(mCodec) != AMEDIA_OK) { LOGE("codec start failed"); return false; }
    if (mCancel.load()) return false;

    mQuit = false; mEnded = false; mPlaying = true; mPosSec = 0.0;
    mFirstFrameReady.store(false); mExtractorRecreate = 0;
    { std::lock_guard<std::mutex> lk(mClockMx); mClockBaseNs = 0; mClockBasePts = 0.0; mFirstFramePts = 0.0; }
    mOpen = true;
    mWorker = std::thread(&NanoVideo::decodeLoop, this);   // spawn LAST, only on full success
    LOGV("opened %s (%dx%d, %.1fs, %s)", path.c_str(), mWidth, mHeight, mDurationSec, mimeStr.c_str());
    return true;
}

// Non-GL soft reset of codec/extractor/fed state for the in-worker fed->normal fallback on
// the SAME object (keeps the GL state from openBegin). Joins a live decode worker - safe on
// the worker thread (never called on the render thread in the real flow).
void NanoVideo::resetForReopen() {
    mCancel.store(false);
    if (mWorker.joinable()) {
        mQuit = true; mFedCv.notify_all();
        if (mCodec) AMediaCodec_stop(mCodec);
        mWorker.join();
    }
    if (mCodec) { AMediaCodec_delete(mCodec); mCodec = nullptr; }
    if (mEx) { AMediaExtractor_delete(mEx); mEx = nullptr; }
    freeTsSource();
    freeHls();
    mFed = false; mFedEos = false; mFedFlush = false;
    { std::lock_guard<std::mutex> lk(mFedMx); mFedQ.clear(); }
    mQuit = false; mEnded = false;
}

// Render thread: cancel an in-flight openAsyncRun*. Sets mCancel (checked between blocking
// steps) and unblocks an in-flight NanoHls fetch. mHls is atomic so the acquire load never
// sees a torn/half-constructed pointer (the worker publishes it release-after-construct).
void NanoVideo::requestOpenCancel() {
    mCancel.store(true, std::memory_order_relaxed);
    android::NanoHls* h = mHls.load(std::memory_order_acquire);
    if (h) h->requestStop();
}

// Open a network stream (http/https, incl. HLS .m3u8). Mirrors open() but points the
// extractor at the URL; the platform extractor fetches the manifest + segments. Live
// streams report duration 0 so seek() is a no-op (mDurationSec <= 0). No descramble /
// fed path here: IPTV streams decode natively through the system extractor + HW codec.
bool NanoVideo::openUrl(const std::string& url) {
    if (!openBegin(0, 0)) return false;
    return openAsyncRunUrl(url);
}

// WORKER THREAD: network fetch (NanoHls) + extractor + codec, reusing the GL from openBegin.
// Publishes mHls (release) AFTER construction but BEFORE start(), so a concurrent
// requestOpenCancel (acquire load) always sees a fully-constructed object and can unblock the
// in-flight fetch. Polls mCancel between steps; spawns the decode worker only on full success.
bool NanoVideo::openAsyncRunUrl(const std::string& url) {
    if (mCodec || mEx || mFed) resetForReopen();
    mDisplayAspect.store(0.0f);
    if (mCancel.load()) return false;

    // Native AMediaExtractor cannot fetch http(s) URLs, so fetch in-process (curl + HLS).
    android::NanoHls* h = new android::NanoHls(url);
    if (mCancel.load()) { delete h; return false; }     // not published yet: free locally
    mHls.store(h, std::memory_order_release);            // publish for requestOpenCancel
    if (mCancel.load()) h->requestStop();                // canceled during publish: bail start fast
    if (!h->start()) { LOGE("NanoHls start failed: %s", url.c_str()); return false; }  // owner freeHls deletes it
    if (mCancel.load()) return false;

    mEx = AMediaExtractor_new();
    media_status_t st = AMediaExtractor_setDataSourceCustom(mEx, h->dataSource());
    if (st != AMEDIA_OK) { LOGE("setDataSourceCustom(url) failed (%d): %s", st, url.c_str()); return false; }
    if (mCancel.load()) return false;

    size_t nTracks = AMediaExtractor_getTrackCount(mEx);
    const char* mime = nullptr;
    AMediaFormat* fmt = nullptr;
    for (size_t i = 0; i < nTracks; i++) {
        AMediaFormat* f = AMediaExtractor_getTrackFormat(mEx, i);
        const char* m = nullptr;
        if (AMediaFormat_getString(f, AMEDIAFORMAT_KEY_MIME, &m) && m && !strncmp(m, "video/", 6)) {
            mVideoTrack = (int)i; fmt = f; mime = m; break;
        }
        AMediaFormat_delete(f);
    }
    if (mVideoTrack < 0 || !fmt) { LOGE("no video track in url %s", url.c_str()); return false; }

    int32_t w = 0, h2 = 0; int64_t durUs = 0;
    AMediaFormat_getInt32(fmt, AMEDIAFORMAT_KEY_WIDTH, &w);
    AMediaFormat_getInt32(fmt, AMEDIAFORMAT_KEY_HEIGHT, &h2);
    AMediaFormat_getInt64(fmt, AMEDIAFORMAT_KEY_DURATION, &durUs);
    mWidth = w > 0 ? w : 1; mHeight = h2 > 0 ? h2 : 1;
    mDurationSec = durUs > 0 ? durUs / 1e6 : 0.0;   // 0 for live
    if (mConsumer != nullptr) mConsumer->setDefaultBufferSize(mWidth, mHeight);

    AMediaExtractor_selectTrack(mEx, mVideoTrack);
    std::string mimeStr = mime;

    mCodec = AMediaCodec_createDecoderByType(mimeStr.c_str());
    if (!mCodec) { LOGE("createDecoderByType(%s) failed", mimeStr.c_str()); AMediaFormat_delete(fmt); return false; }
    if (mCancel.load()) { AMediaFormat_delete(fmt); return false; }
    media_status_t cs = AMediaCodec_configure(mCodec, fmt, mSurface.get(), nullptr, 0);
    AMediaFormat_delete(fmt);
    if (cs != AMEDIA_OK) { LOGE("url codec configure failed (%d)", cs); return false; }
    if (mCancel.load()) return false;
    if (AMediaCodec_start(mCodec) != AMEDIA_OK) { LOGE("url codec start failed"); return false; }
    if (mCancel.load()) return false;

    mQuit = false; mEnded = false; mPlaying = true; mPosSec = 0.0;
    mFirstFrameReady.store(false); mExtractorRecreate = 0;
    { std::lock_guard<std::mutex> lk(mClockMx); mClockBaseNs = 0; mClockBasePts = 0.0; mFirstFramePts = 0.0; }
    mOpen = true;
    mWorker = std::thread(&NanoVideo::decodeLoop, this);
    LOGV("opened url %s (%dx%d, %.1fs, %s)", url.c_str(), mWidth, mHeight, mDurationSec, mimeStr.c_str());
    return true;
}

// Open in fed mode: codec + GL output set up by mime/size, input pushed by feedVideo().
bool NanoVideo::openFed(const std::string& mime, int width, int height, AMediaFormat* srcFmt) {
    if (!openBegin(width, height)) return false;
    return openAsyncRunFed(mime, width, height, srcFmt);
}

// WORKER THREAD: fed-mode codec setup (demuxer pushes input via feedVideo), reusing the GL
// from openBegin. On ANY failure after createFedDecoder, delete+null mCodec and clear mFed
// BEFORE returning so a fed->normal fallback (resetForReopen + openAsyncRun on the same
// object) starts from a clean state. Spawns the decode worker only on full success.
bool NanoVideo::openAsyncRunFed(const std::string& mime, int width, int height, AMediaFormat* srcFmt) {
    if (mCodec || mEx) resetForReopen();
    mFed = true; mFedMime = mime; mFedRecreate = 0;
    // MPEG-2 .ts presents video behind audio: the HW MPEG-2 decode pipeline + GLConsumer latch + DRM
    // scanout land the picture later than the AAudio HAL renders the (already-consumed) audio the master
    // clock counts, a constant offset that 480i29.97 on a 60Hz panel makes visible. Pull the picture
    // forward by a tunable skew, scoped to video/mpeg2 ONLY (0 for AVI mp4v / live HLS avc / the extractor
    // path, so no other format changes). Live-tunable (ms) for ear calibration, then bake the default.
    mMpeg2AvOffsetSec = (mime == "video/mpeg2")
        ? (double)property_get_int32("persist.gammaos.nano.vid.mpeg2avoffms", 0) / 1000.0
        : 0.0;
    mDisplayAspect.store(0.0f);   // square pixels until the demuxer parses an anamorphic DAR
    if (srcFmt) {   // prefer the real decoded size from the extractor format
        int32_t w = 0, h = 0;
        if (AMediaFormat_getInt32(srcFmt, AMEDIAFORMAT_KEY_WIDTH, &w) && w > 0) width = w;
        if (AMediaFormat_getInt32(srcFmt, AMEDIAFORMAT_KEY_HEIGHT, &h) && h > 0) height = h;
    }
    mWidth = width > 0 ? width : 1; mHeight = height > 0 ? height : 1;
    mDurationSec = 0.0;
    if (mConsumer != nullptr) mConsumer->setDefaultBufferSize(mWidth, mHeight);
    if (mCancel.load()) { mFed = false; return false; }

    mCodec = createFedDecoder(false);
    if (!mCodec) { LOGE("openFed create decoder(%s) failed", mime.c_str()); mFed = false; return false; }
    if (mCancel.load()) { AMediaCodec_delete(mCodec); mCodec = nullptr; mFed = false; return false; }
    media_status_t cs;
    if (srcFmt) {
        AMediaFormat_setString(srcFmt, AMEDIAFORMAT_KEY_MIME, mime.c_str());
        cs = AMediaCodec_configure(mCodec, srcFmt, mSurface.get(), nullptr, 0);
    } else {
        AMediaFormat* fmt = AMediaFormat_new();
        AMediaFormat_setString(fmt, AMEDIAFORMAT_KEY_MIME, mime.c_str());
        AMediaFormat_setInt32(fmt, AMEDIAFORMAT_KEY_WIDTH, mWidth);
        AMediaFormat_setInt32(fmt, AMEDIAFORMAT_KEY_HEIGHT, mHeight);
        cs = AMediaCodec_configure(mCodec, fmt, mSurface.get(), nullptr, 0);
        AMediaFormat_delete(fmt);
    }
    if (cs != AMEDIA_OK) { LOGE("openFed configure failed (%d)", cs); AMediaCodec_delete(mCodec); mCodec = nullptr; mFed = false; return false; }
    if (mCancel.load()) { AMediaCodec_delete(mCodec); mCodec = nullptr; mFed = false; return false; }
    if (AMediaCodec_start(mCodec) != AMEDIA_OK) { LOGE("openFed codec start failed"); AMediaCodec_delete(mCodec); mCodec = nullptr; mFed = false; return false; }
    if (mCancel.load()) { AMediaCodec_delete(mCodec); mCodec = nullptr; mFed = false; return false; }

    mQuit = false; mEnded = false; mPlaying = true; mPosSec = 0.0;
    mFedEos = false; mFedFlush = false;
    mFirstFrameReady.store(false);
    { std::lock_guard<std::mutex> lk(mFedMx); mFedQ.clear(); }
    { std::lock_guard<std::mutex> lk(mClockMx); mClockBaseNs = 0; mClockBasePts = 0.0; mFirstFramePts = 0.0; }
    mOpen = true;
    mWorker = std::thread(&NanoVideo::decodeLoop, this);
    LOGV("openFed %s (%dx%d hint)", mime.c_str(), mWidth, mHeight);
    return true;
}

bool NanoVideo::feedVideo(const uint8_t* es, size_t len, int64_t ptsUs) {
    if (!mFed || len == 0) return !mQuit.load();
    std::unique_lock<std::mutex> lk(mFedMx);
    mFedCv.wait(lk, [this]{ return mFedQ.size() < kFedQMax || mQuit.load() || mFedFlush.load(); });
    if (mQuit.load()) return false;
    if (mFedFlush.load()) return true;     // drop: a seek is in flight, this AU is stale
    FedAu au; au.es.assign(es, es + len); au.ptsUs = ptsUs;
    mFedQ.push_back(std::move(au));
    lk.unlock();
    mFedCv.notify_all();
    return true;
}

void NanoVideo::feedVideoEos() { mFedEos.store(true); mFedCv.notify_all(); }

void NanoVideo::setClockFn(std::function<double()> fn) {
    std::lock_guard<std::mutex> lk(mClockMx);
    mClockFn = std::move(fn);
}

void NanoVideo::flushFed(double newBaseSec) {
    mFedFlushBase.store(newBaseSec);
    { std::lock_guard<std::mutex> lk(mFedMx); mFedQ.clear(); }
    mFedFlush.store(true);
    mFedCv.notify_all();
}

// Worker: take the next access unit, waiting up to timeoutMs. false = none (timeout/quit/flush).
bool NanoVideo::popFedAu(FedAu& out, int timeoutMs) {
    std::unique_lock<std::mutex> lk(mFedMx);
    if (mFedQ.empty()) {
        mFedCv.wait_for(lk, std::chrono::milliseconds(timeoutMs),
                        [this]{ return !mFedQ.empty() || mQuit.load() || mFedFlush.load(); });
    }
    if (mFedQ.empty()) return false;
    out = std::move(mFedQ.front());
    mFedQ.pop_front();
    lk.unlock();
    mFedCv.notify_all();           // wake feedVideo (space freed)
    return true;
}

// Pick the fed-mode decoder for the current mime. The Allwinner MPEG-2 HW decoder
// intermittently wedges at cold start (no output, no error) ~half the time, so broadcast
// MPEG-2 (SD) decodes on the reliable software decoder; AVC/HEVC keep the HW decoder.
// forceSw bypasses HW entirely (the recreate fallback for a faulted HW AVC/HEVC codec).
AMediaCodec* NanoVideo::createFedDecoder(bool forceSw) {
    // Default: the platform decoder for the mime (HW), created exactly as the reliable
    // extractor-driven open() path does. Do NOT speculatively try a software codec first - on
    // this device createCodecByName("c2.android...") fails (the swcodec service is unreachable
    // from the launcher process) and that failed create leaves the codec2 client in a state
    // that makes the subsequent HW codec wedge at cold start. Software is only attempted as a
    // last-ditch recreate fallback (forceSw) for a genuinely faulted HW AVC/HEVC codec.
    if (forceSw) {
        const char* swName = nullptr;
        if (mFedMime == "video/mpeg2") swName = "c2.android.mpeg2.decoder";
        else if (mFedMime == "video/avc")  swName = "c2.android.avc.decoder";
        else if (mFedMime == "video/hevc") swName = "c2.android.hevc.decoder";
        else if (mFedMime == "video/mp4v-es") swName = "c2.android.mpeg4.decoder";
        if (swName) { AMediaCodec* c = AMediaCodec_createCodecByName(swName); if (c) return c; }
    }
    return AMediaCodec_createDecoderByType(mFedMime.c_str());
}

// Rebuild a faulted fed-mode codec. A fresh codec usually recovers; the first couple of
// attempts use the normal picker (SW for MPEG-2, HW for AVC/HEVC), later attempts force SW.
// Returns false when out of options. Runs on the decode worker.
bool NanoVideo::recreateFedCodec() {
    if (mCodec) { AMediaCodec_delete(mCodec); mCodec = nullptr; }   // faulted: delete, do not stop
    mFedRecreate++;
    mCodec = createFedDecoder(/*forceSw=*/mFedRecreate > 2);
    if (!mCodec) return false;
    AMediaFormat* fmt = AMediaFormat_new();
    AMediaFormat_setString(fmt, AMEDIAFORMAT_KEY_MIME, mFedMime.c_str());
    AMediaFormat_setInt32(fmt, AMEDIAFORMAT_KEY_WIDTH, mWidth);
    AMediaFormat_setInt32(fmt, AMEDIAFORMAT_KEY_HEIGHT, mHeight);
    media_status_t cs = AMediaCodec_configure(mCodec, fmt, mSurface.get(), nullptr, 0);
    AMediaFormat_delete(fmt);
    if (cs != AMEDIA_OK || AMediaCodec_start(mCodec) != AMEDIA_OK) {
        if (mCodec) { AMediaCodec_delete(mCodec); mCodec = nullptr; }
        return false;
    }
    LOGE("recreateFedCodec attempt %d (%s)", mFedRecreate, mFedRecreate > 2 ? "forced-sw" : "default");
    return true;
}

// Extractor-path counterpart to recreateFedCodec. The Allwinner HW decoder (notably 1080p AVC)
// intermittently cold-starts into a faulted (-10000) OR no-output state on the extractor-driven
// path (mp4/mov/live), and unlike the fed path there was no recovery, so the picture buffered
// forever. Rebuild the codec on the SAME extractor (its track format carries csd-0/csd-1), re-seek
// to the current position, and resume. HW first; software only as a last-ditch retry (the swcodec
// create can itself wedge on this device, so it is gated to later attempts like the fed path).
// Runs on the decode worker.
bool NanoVideo::recreateExtractorCodec() {
    if (!mEx || mVideoTrack < 0) return false;
    if (mCodec) { AMediaCodec_delete(mCodec); mCodec = nullptr; }   // faulted: delete, do not stop
    mExtractorRecreate++;
    AMediaFormat* fmt = AMediaExtractor_getTrackFormat(mEx, mVideoTrack);
    if (!fmt) return false;
    const char* mime = nullptr;
    AMediaFormat_getString(fmt, AMEDIAFORMAT_KEY_MIME, &mime);
    AMediaCodec* c = nullptr;
    // Force the software decoder ONLY for small (<= ~PAL/480p) content. On this A53 the c2.android
    // software decoder CANNOT do 720p/1080p at realtime (it runs ~0.5x AND pins all cores, starving
    // system_server) - for hi-res the HW decoder is the only viable path, so keep rebuilding HW even
    // when it cold-start-wedges rather than dropping to a software decoder that cannot keep up.
    bool swOk = (int64_t)mWidth * mHeight > 0 && (int64_t)mWidth * mHeight <= 720 * 576;
    if (mExtractorRecreate > 2 && mime && swOk) {    // last resort: force the software decoder (SD only)
        const std::string m = mime;
        const char* sw = (m == "video/avc")   ? "c2.android.avc.decoder"
                       : (m == "video/hevc")  ? "c2.android.hevc.decoder"
                       : (m == "video/mpeg2") ? "c2.android.mpeg2.decoder"
                       : (m == "video/mp4v-es") ? "c2.android.mpeg4.decoder" : nullptr;
        if (sw) c = AMediaCodec_createCodecByName(sw);
    }
    if (!c && mime) c = AMediaCodec_createDecoderByType(mime);
    if (!c) { AMediaFormat_delete(fmt); return false; }
    media_status_t cs = AMediaCodec_configure(c, fmt, mSurface.get(), nullptr, 0);
    AMediaFormat_delete(fmt);
    if (cs != AMEDIA_OK || AMediaCodec_start(c) != AMEDIA_OK) { AMediaCodec_delete(c); return false; }
    mCodec = c;
    AMediaExtractor_seekTo(mEx, (int64_t)(mPosSec.load() * 1e6), AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC);
    LOGE("recreateExtractorCodec attempt %d (%s)", mExtractorRecreate, mExtractorRecreate > 2 ? "forced-sw" : "default");
    return true;
}

void NanoVideo::decodeLoop() {
    bool sawInputEos = false;
    int hardErr = 0;   // consecutive hard codec errors -> back off + park (never hot-loop)
    bool queuedAny = false;          // at least one input AU has been queued since (re)start/flush
    int64_t lastProgressNs = monoNs(); // wall time of the last decoded frame (stall watchdog)
    int inFail = 0;                  // consecutive loops with NO progress at EITHER stage (never-primed wedge)
    int64_t firstFeedNs = monoNs();  // (re)start wall time; bounds the never-primed recovery
    double prevPts = -1.0;           // last rendered frame PTS; detects genuine stream PTS jumps vs ahead-of-audio
    double lastAclkVal = -1.0;       // audio-clock velocity sample anchor (value); detects a stalled/crawling clock
    int64_t lastAclkAdvNs = monoNs();// wall time of the last velocity sample
    bool   aclkStalled = false;      // audio clock frozen OR crawling (<0.25x) = the inline-audio worker is starved
    int64_t seekGraceUntilNs = 0;    // after a seek the audio ring drains + re-buffers (legitimately >250ms);
                                     // suppress the stall guard until then so it does not misread the re-buffer
                                     // as a stalled clock and race the picture ahead. 0 at open so the initial-open
                                     // bootstrap guard is unaffected; only a seek arms the grace.
    while (!mQuit.load()) {
        if (!mPlaying.load() && !mSeekPending.load() && !mFedFlush.load()) {
            { std::lock_guard<std::mutex> lk(mClockMx); mClockBaseNs = 0; }   // re-anchor on resume
            usleep(8000);
            continue;
        }
        if (mFed) {
            if (mFedFlush.exchange(false)) {              // seek: drop queued input, flush, re-anchor
                if (mCodec) AMediaCodec_flush(mCodec);    // null-safe: a recovery park may have left mCodec null
                { std::lock_guard<std::mutex> lk(mFedMx); mFedQ.clear(); }
                sawInputEos = false; mEnded = false; mFedEos = false;
                queuedAny = false; lastProgressNs = monoNs();   // need fresh input before output again
                { std::lock_guard<std::mutex> lk(mClockMx); mClockBaseNs = 0; mClockBasePts = mFedFlushBase.load(); }
                lastAclkVal = -1.0; lastAclkAdvNs = monoNs(); aclkStalled = false;
                seekGraceUntilNs = monoNs() + 3000000000LL;   // audio re-buffers after the seek; do not race the picture
                mFedCv.notify_all();
            }
        } else if (mSeekPending.exchange(false)) {
            double t = mSeekTarget.load();
            AMediaExtractor_seekTo(mEx, (int64_t)(t * 1e6), AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC);
            if (mCodec) AMediaCodec_flush(mCodec);        // null-safe: a recovery park may have left mCodec null
            sawInputEos = false; mEnded = false;
            queuedAny = false; lastProgressNs = monoNs();
            { std::lock_guard<std::mutex> lk(mClockMx); mClockBaseNs = 0; }
            lastAclkVal = -1.0; lastAclkAdvNs = monoNs(); aclkStalled = false;
            seekGraceUntilNs = monoNs() + 3000000000LL;       // audio re-buffers after the seek; do not race the picture
        }

        // Feed one input access unit (fed: from the demuxer queue; else: from the extractor).
        if (!sawInputEos) {
            if (mFed) {
                FedAu au; bool have = popFedAu(au, 3);
                if (have || mFedEos.load()) {
                    ssize_t inIdx = AMediaCodec_dequeueInputBuffer(mCodec, 2000);
                    if (inIdx >= 0) {
                        if (have) {
                            size_t cap = 0;
                            uint8_t* buf = AMediaCodec_getInputBuffer(mCodec, inIdx, &cap);
                            size_t n = (buf && cap < au.es.size()) ? cap : au.es.size();
                            if (buf && n) memcpy(buf, au.es.data(), n);
                            AMediaCodec_queueInputBuffer(mCodec, inIdx, 0, buf ? n : 0,
                                                         au.ptsUs < 0 ? 0 : (uint64_t)au.ptsUs, 0);
                            queuedAny = true; inFail = 0;
                        } else {
                            AMediaCodec_queueInputBuffer(mCodec, inIdx, 0, 0, 0, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                            sawInputEos = true;
                        }
                    } else if (have) {                    // no input buffer free; retry this AU next loop
                        inFail++;                         // an AU is waiting but no buffer: count toward a wedge
                        std::lock_guard<std::mutex> lk(mFedMx);
                        mFedQ.push_front(std::move(au));
                    }
                }
            } else {
                ssize_t inIdx = AMediaCodec_dequeueInputBuffer(mCodec, 2000);
                if (inIdx >= 0) {
                    inFail = 0;                           // obtained an input buffer: codec is alive at input
                    size_t cap = 0;
                    uint8_t* buf = AMediaCodec_getInputBuffer(mCodec, inIdx, &cap);
                    ssize_t sz = buf ? AMediaExtractor_readSampleData(mEx, buf, cap) : -1;
                    if (sz < 0) {
                        AMediaCodec_queueInputBuffer(mCodec, inIdx, 0, 0, 0, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                        sawInputEos = true;
                    } else {
                        int64_t pts = AMediaExtractor_getSampleTime(mEx);
                        AMediaCodec_queueInputBuffer(mCodec, inIdx, 0, sz, pts, 0);
                        AMediaExtractor_advance(mEx);
                        queuedAny = true;
                    }
                } else {
                    inFail++;                             // no input buffer (released/wedged codec, or backpressure)
                }
            }
        }

        // Never-primed wedge recovery (symptom A): a released/errored/NULL mCodec fails the input
        // dequeue forever, so queuedAny never flips and the short-circuit below would spin at 3ms,
        // bypassing the output-side hard-error recovery and the stall watchdog (both reached only
        // after queuedAny is true). Trigger ONLY while still un-primed (queuedAny == false): once
        // primed, a wedged codec is owned by the output recovery (hardErr) + the stall watchdog.
        // Backed off (20ms) so it can never hot-loop; parks on exhaustion (null OR released).
        if (!queuedAny && (mCodec == nullptr ||
                           (inFail > 50 && (monoNs() - firstFeedNs) > 3000000000LL))) {
            usleep(20000);
            bool rebuilt = mFed ? (mFedRecreate < 4 && recreateFedCodec())
                                : (mExtractorRecreate < 4 && recreateExtractorCodec());
            if (rebuilt) {
                if (mFed) { std::lock_guard<std::mutex> lk(mFedMx); mFedQ.clear(); mFedCv.notify_all(); }
                sawInputEos = false; mEnded = false; hardErr = 0;
                inFail = 0; queuedAny = false; firstFeedNs = monoNs(); lastProgressNs = monoNs();
                { std::lock_guard<std::mutex> lk(mClockMx); mClockBaseNs = 0; }
                continue;
            }
            // Out of rebuild attempts and still cannot prime: park (covers BOTH a null mCodec and a
            // released-but-non-null codec at exhaustion). Wakes on quit/seek/flush; never a 3ms spin.
            LOGE("codec never primed and unrecoverable; parking decode worker (fed=%d)", (int)mFed);
            mEnded = true;
            while (!mQuit.load() && !mSeekPending.load() && !mFedFlush.load()) usleep(50000);
            inFail = 0; hardErr = 0; firstFeedNs = monoNs();
            continue;
        }

        // Do not dequeue output before any input has been queued: the Allwinner MPEG-2 OMX
        // decoder can BLOCK dequeueOutputBuffer (ignoring the timeout) when it has been started
        // but never fed, which would freeze this worker (and back-pressure the demuxer into a
        // full stop). Wait for the demuxer to deliver the first access unit first.
        if (!queuedAny && !sawInputEos) { usleep(3000); continue; }

        // Drain one output buffer, pacing its render to the wall clock by PTS.
        AMediaCodecBufferInfo info;
        ssize_t outIdx = AMediaCodec_dequeueOutputBuffer(mCodec, &info, 4000);
        if (outIdx >= 0) {
            double pts = info.presentationTimeUs / 1e6;
            bool render = info.size > 0 && !(info.flags & AMEDIACODEC_BUFFER_FLAG_CODEC_CONFIG);
            if (render) {
                // Step 1 start-together: record the first decoded frame + its PTS so the host
                // can un-mute audio anchored to this origin. The frame is still rendered normally
                // (no gating/drop): the picture starts at frame 0 as before, audio joins it.
                if (!mFirstFrameReady.load(std::memory_order_acquire)) {
                    { std::lock_guard<std::mutex> lk(mClockMx); mFirstFramePts = pts; }
                    mFirstFrameReady.store(true, std::memory_order_release);
                }
                int64_t now = monoNs();
                int64_t waitNs = 0;
                int64_t dbgWaitNs = 0; int dbgReason = 0; double dbgPrev = -1.0;  // clkdbg pacing detail
                { std::lock_guard<std::mutex> lk(mClockMx);
                  if (mClockBaseNs == 0) { mClockBaseNs = now; mClockBasePts = pts; dbgReason = 1; }
                  // Frames pace to the wall clock by PTS delta (smooth, native frame rate). When an
                  // audio clock is present (A/V), SLEW the video timeline to it - the audio is the
                  // master - by re-anchoring only when the wall-clock-predicted position diverges
                  // from the audio position beyond a small window. This keeps lip-sync without the
                  // per-frame gating that, when the decode runs ahead of audio playback, throttled
                  // the picture to the cap rate (the half-frame-rate bug).
                  double aclk = mClockFn ? mClockFn() : -1.0;
                  // MPEG-2 .ts presentation skew: advance the clock the pacer chases so each picture
                  // displays mMpeg2AvOffsetSec earlier (compensates the video display path landing later
                  // than the audio HAL). Gated on mFed so a reused decoder opening a later non-fed
                  // (.mp4/.mov) title can never inherit it; 0 for every non-mpeg2 fed open.
                  if (aclk >= 0.0 && mFed) aclk += mMpeg2AvOffsetSec;
                  if (aclk >= 0.0) {
                      // Audio-clock stall/crawl guard. Sample the clock VELOCITY over ~1s: a clock
                      // advancing far slower than wall time (frozen 0x OR crawling, e.g. the inline-audio
                      // .ts worker starved when the picture holds for it -> fed queue fills -> worker
                      // blocks on feedVideo -> audio not fed -> the clock crawls at ~0.02x: a self-
                      // sustaining "constant buffering" stall) is treated as absent: pace to wall clock +
                      // render now so the fed queue drains, the worker unblocks, the audio ring refills and
                      // the clock recovers; the slew below re-locks once it advances at ~1x. A legit cold-
                      // start backlog keeps a HEALTHY ~1x clock (vel ~1) so this does NOT fire there - it
                      // only catches a genuinely stalled clock. Healthy A/V is unaffected (.ts/.mov/.mp4).
                      if (lastAclkVal < 0.0) { lastAclkVal = aclk; lastAclkAdvNs = now; }   // first sample
                      else if (now - lastAclkAdvNs > 1000000000LL) {                        // re-sample ~1s
                          double vel = (aclk - lastAclkVal) / ((double)(now - lastAclkAdvNs) / 1e9);
                          aclkStalled = (vel < 0.25);
                          lastAclkVal = aclk; lastAclkAdvNs = now;
                      }
                      if (aclkStalled && now > seekGraceUntilNs) {
                          aclk = -1.0; mClockBaseNs = now; mClockBasePts = pts;   // render now, pace wall-clock
                      }
                  }
                  if (aclk >= 0.0) {
                      double predicted = mClockBasePts + (double)(now - mClockBaseNs) / 1e9;
                      if (predicted - aclk > 0.10 || aclk - predicted > 0.10) {
                          mClockBaseNs = now; mClockBasePts = aclk;
                      }
                  }
                  int64_t targetNs = mClockBaseNs + (int64_t)((pts - mClockBasePts) * 1e9);
                  waitNs = targetNs - now;
                  // Re-anchor (render now) ONLY on a genuine stream PTS discontinuity (this frame's
                  // PTS jumps >0.5s vs the PREVIOUS frame - MPEG-TS/HDHomeRun splices) or when the
                  // picture has fallen BEHIND by >0.5s (stall / backward jump - catch up). A large
                  // POSITIVE waitNs with a CONTINUOUS PTS just means the picture decoded ahead of the
                  // audio (cold-start backlog): it must WAIT for the audio, not render now. Rendering
                  // an ahead frame immediately is exactly what raced the picture to ~2x the audio.
                  bool ptsJump = (prevPts >= 0.0 && (pts - prevPts > 0.5 || prevPts - pts > 0.5));
                  dbgWaitNs = waitNs; dbgPrev = prevPts;
                  if (ptsJump || waitNs < -500000000LL) {
                      dbgReason = ptsJump ? 2 : 3;
                      mClockBaseNs = now; mClockBasePts = pts; waitNs = 0;
                  }
                }
                prevPts = pts;
                // Hold an early frame for the audio in short steps so quit/seek/flush stay responsive.
                // Steady state waits ~one frame; a one-time cold-start backlog may hold a bit longer,
                // then the audio catches up and pacing settles to 1x (no race).
                while (waitNs > 0 && !mQuit.load() && !mSeekPending.load() && !mFedFlush.load()) {
                    int64_t step = waitNs > 30000000LL ? 30000000LL : waitNs;
                    usleep((useconds_t)(step / 1000));
                    waitNs -= step;
                }
                mPosSec = pts;
                // clkdbg (symptom-C diagnosis): once per ~2s, log how the picture clock tracks wall
                // time and the audio clock, so a 2x picture can be pinned to the audio clock vs the
                // PTS pacing. Cheap (1 line / 2s during playback); remove once C is fixed.
                static int64_t sClkDbgNs = 0;
                if (now - sClkDbgNs > 2000000000LL) {
                    sClkDbgNs = now;
                    double a = mClockFn ? mClockFn() : -1.0;
                    LOGV("clkdbg: pts=%.3f aclk=%.3f basePts=%.3f wait=%.3f reason=%d prev=%.3f fed=%d off=%.3f",
                         pts, a, mClockBasePts, (double)dbgWaitNs / 1e9, dbgReason, dbgPrev, (int)mFed, mMpeg2AvOffsetSec);
                }
            }
            AMediaCodec_releaseOutputBuffer(mCodec, outIdx, render);
            hardErr = 0; inFail = 0;
            int64_t nowOk = monoNs();
            if ((mExtractorRecreate || mFedRecreate) && (nowOk - lastProgressNs) < 1000000000LL) {
                // a frame decoded < 1s after the previous one == a sustained healthy run; restore the
                // rebuild budget so a later unrelated fault can still recover (only after real output).
                mExtractorRecreate = 0; mFedRecreate = 0;
            }
            lastProgressNs = nowOk;       // the codec is alive and producing output
            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
                mEnded = true;
                while (!mQuit.load() && mEnded.load() && !mSeekPending.load() && !mFedFlush.load()) usleep(16000);
            }
        } else if (outIdx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat* of = AMediaCodec_getOutputFormat(mCodec);
            int32_t w = 0, h = 0;
            if (AMediaFormat_getInt32(of, AMEDIAFORMAT_KEY_WIDTH, &w) && w > 0) mWidth = w;
            if (AMediaFormat_getInt32(of, AMEDIAFORMAT_KEY_HEIGHT, &h) && h > 0) mHeight = h;
            if (mConsumer != nullptr) mConsumer->setDefaultBufferSize(mWidth, mHeight);
            AMediaFormat_delete(of);
            hardErr = 0; inFail = 0;
            lastProgressNs = monoNs();
        } else if (outIdx == AMEDIACODEC_INFO_TRY_AGAIN_LATER
                   || outIdx == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
            hardErr = 0; inFail = 0;   // benign: the dequeue timeout already paced us
        } else {
            // Hard codec error (e.g. the component went to a released/error state). Back off
            // so we never burn CPU hot-looping a dead codec, and park after ~1s of failures.
            if (hardErr == 0) LOGE("decode output error %zd; backing off", (ssize_t)outIdx);
            usleep(20000);
            if (++hardErr > 50) {
                // Fed mode: a faulted codec (the Allwinner MPEG-2 cold-start glitch) is
                // recoverable by rebuilding it - the fresh codec resyncs at the next in-stream
                // sequence header. Retry HW, then the software decoder, before giving up.
                if (mFed && mFedRecreate < 4 && recreateFedCodec()) {
                    { std::lock_guard<std::mutex> lk(mFedMx); mFedQ.clear(); }
                    mFedCv.notify_all();
                    sawInputEos = false; mEnded = false; hardErr = 0;
                    { std::lock_guard<std::mutex> lk(mClockMx); mClockBaseNs = 0; }
                    continue;
                }
                // Extractor path: same cold-start fault recovery (1080p AVC -10000 etc.).
                if (!mFed && mExtractorRecreate < 4 && recreateExtractorCodec()) {
                    sawInputEos = false; mEnded = false; hardErr = 0;
                    queuedAny = false; lastProgressNs = monoNs();
                    { std::lock_guard<std::mutex> lk(mClockMx); mClockBaseNs = 0; }
                    continue;
                }
                LOGE("codec unrecoverable; parking decode worker");
                mEnded = true;
                while (!mQuit.load() && !mSeekPending.load() && !mFedFlush.load()) usleep(50000);
                hardErr = 0;
            }
        }

        // Stall watchdog: the Allwinner decoder intermittently cold-starts into a state where it
        // neither errors nor produces output (dequeueOutputBuffer just returns TRY_AGAIN forever
        // while input backs up). hardErr never trips, so rebuild the codec if input has been
        // flowing but NO frame has decoded for a few seconds. Applies to BOTH the fed path
        // (MPEG-2/.ts) and the extractor path (mp4/mov/live, notably 1080p AVC), the latter being
        // why bbb-style files could buffer forever with no recovery.
        // First-frame window: the extractor HW path (1080p AVC) can be slow to cold-start under
        // fresh-boot CPU contention, so give it 7s before declaring a stall (the fed/.ts path keeps
        // 4s). Killing a slow-but-healthy HW codec early just churns rebuilds and ends on the (much
        // slower, CPU-pinning) software decoder - the opposite of what we want for hi-res.
        int64_t stallNs = mFed ? 4000000000LL : 7000000000LL;
        if (queuedAny && !mEnded.load() && mPlaying.load() &&
            (monoNs() - lastProgressNs) > stallNs) {
            bool rebuilt = mFed ? (mFedRecreate < 4 && recreateFedCodec())
                                : (mExtractorRecreate < 4 && recreateExtractorCodec());
            LOGE("no decoded output for %llds; codec stalled, rebuilt=%d (fed=%d)",
                 (long long)(stallNs / 1000000000LL), (int)rebuilt, (int)mFed);
            if (rebuilt) {
                if (mFed) { std::lock_guard<std::mutex> lk(mFedMx); mFedQ.clear(); mFedCv.notify_all(); }
                sawInputEos = false; mEnded = false; hardErr = 0;
                queuedAny = false; inFail = 0;
                // Give EVERY rebuilt HW codec the full stall window (7s extractor / 4s fed) to emit
                // its first frame - on this device the HW decoder is the only viable hi-res path, so
                // we'd rather wait for it than churn rebuilds toward a software decoder that cannot
                // keep up. lastProgressNs resets to now so the fresh codec gets the whole window.
                lastProgressNs = monoNs();
                { std::lock_guard<std::mutex> lk(mClockMx); mClockBaseNs = 0; }
                continue;
            }
            lastProgressNs = monoNs();   // out of rebuild attempts: stop hammering, keep trying to drain
        }
    }
}

static GLuint compileShader(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0; glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) { char log[512]; glGetShaderInfoLog(s, sizeof(log), nullptr, log); LOGE("shader: %s", log); glDeleteShader(s); return 0; }
    return s;
}

bool NanoVideo::ensureProgram() {
    if (mProg) return true;
    // uRotation: the composed panel rotation+flip matrix (sDrmRotMat), the
    // same mat2 every UI vertex shader applies. Without it the decoded frame
    // was the ONLY on-screen draw in DRM mode that ignored the panel
    // orientation: on a rotated+flipped panel (RG Vita Pro) video landed 90
    // degrees off, stretched to the wrong axis AND mirrored. Identity is
    // uploaded for the SF/overlay and non-rotated paths, so they are
    // bit-identical to before.
    static const char* VS =
        "attribute vec2 aPos;\n"
        "attribute vec2 aTex;\n"
        "uniform mat4 uST;\n"
        "uniform mat2 uRotation;\n"
        "varying vec2 vTex;\n"
        "void main(){ vTex = (uST * vec4(aTex, 0.0, 1.0)).xy; gl_Position = vec4(uRotation * aPos, 0.0, 1.0); }\n";
    static const char* FS =
        "#extension GL_OES_EGL_image_external : require\n"
        "precision mediump float;\n"
        "uniform samplerExternalOES uTex;\n"
        "uniform float uAlpha;\n"
        "varying vec2 vTex;\n"
        "void main(){ vec4 c = texture2D(uTex, vTex); gl_FragColor = vec4(c.rgb, c.a * uAlpha); }\n";
    GLuint vs = compileShader(GL_VERTEX_SHADER, VS);
    GLuint fs = compileShader(GL_FRAGMENT_SHADER, FS);
    if (!vs || !fs) { if (vs) glDeleteShader(vs); if (fs) glDeleteShader(fs); return false; }
    mProg = glCreateProgram();
    glAttachShader(mProg, vs); glAttachShader(mProg, fs);
    glBindAttribLocation(mProg, 0, "aPos");
    glBindAttribLocation(mProg, 1, "aTex");
    glLinkProgram(mProg);
    glDeleteShader(vs); glDeleteShader(fs);
    GLint ok = 0; glGetProgramiv(mProg, GL_LINK_STATUS, &ok);
    if (!ok) { char log[512]; glGetProgramInfoLog(mProg, sizeof(log), nullptr, log); LOGE("link: %s", log); glDeleteProgram(mProg); mProg = 0; return false; }
    mLocPos = 0; mLocTex = 1;
    mLocST = glGetUniformLocation(mProg, "uST");
    mLocAlpha = glGetUniformLocation(mProg, "uAlpha");
    mLocRot = glGetUniformLocation(mProg, "uRotation");
    GLint loc = glGetUniformLocation(mProg, "uTex");
    // Seed uRotation with identity right after link: an unset mat2 uniform
    // reads as zeros in GLES2, which would collapse the quad to a point.
    static const float kIdentity[4] = {1.0f, 0.0f, 0.0f, 1.0f};
    glUseProgram(mProg);
    glUniform1i(loc, 0);
    if (mLocRot >= 0) glUniformMatrix2fv(mLocRot, 1, GL_FALSE, kIdentity);
    glUseProgram(0);
    return true;
}

void NanoVideo::draw(int screenW, int screenH, float rx, float ry, float rw, float rh,
                     float alpha, int fitMode, const float* rotMat) {
    if (!mOpen || !mTexId || mWidth <= 0 || mHeight <= 0 || alpha <= 0.001f) return;
    if (!ensureProgram()) return;

    // Screen Mode fit (web drawVideoPlayer 12712-12737), fitMode = screenMode index:
    //   0 Normal = contain (min), 1 Full Screen = cover (max), 2 Original = 1:1,
    //   3 Zoom = cover*1.33, 4 Double Scale = 2x.
    // Display dims = the browser's el.videoWidth/Height. For anamorphic content the coded
    // grid (mWidth x mHeight) is stretched to the intended display aspect ratio so the
    // frame is shown at its true shape (the web XMB relies on the browser to do this).
    // Keep the coded height and widen to the DAR; square-pixel content (dar == 0) is
    // unchanged, so the fit math is identical to before for it.
    float vw = (float)mWidth, vh = (float)mHeight;
    float dar = mDisplayAspect.load();
    float dispW = (dar > 0.0f) ? (vh * dar) : vw;
    float dispH = vh;
    float s;
    switch (fitMode) {
        case 1:  s = fmaxf(rw / dispW, rh / dispH); break;
        case 2:  s = 1.0f; break;
        case 3:  s = fmaxf(rw / dispW, rh / dispH) * 1.33f; break;
        case 4:  s = 2.0f; break;
        default: s = fminf(rw / dispW, rh / dispH); break;
    }
    float dw = dispW * s, dh = dispH * s;
    float cx = rx + rw * 0.5f, cy = ry + rh * 0.5f;
    float x0 = cx - dw * 0.5f, x1 = cx + dw * 0.5f;
    float y0 = cy - dh * 0.5f, y1 = cy + dh * 0.5f;
    auto ndcX = [&](float x){ return (x / screenW) * 2.0f - 1.0f; };
    auto ndcY = [&](float y){ return 1.0f - (y / screenH) * 2.0f; };
    // 4 corners: TL, TR, BR, BL. UVs use the GL bottom-left origin (screen-top = v1)
    // because the GLConsumer transform matrix (uST) maps t' = 1 - t for video frames;
    // top-left-origin UVs would render every frame upside down (only hidden on
    // vertically-symmetric content). v: TL=1, TR=1, BR=0, BL=0.
    float px[4] = { x0, x1, x1, x0 };
    float py[4] = { y0, y0, y1, y1 };
    float u[4]  = { 0.0f, 1.0f, 1.0f, 0.0f };
    float v[4]  = { 1.0f, 1.0f, 0.0f, 0.0f };
    const int order[6] = {0, 1, 2, 0, 2, 3};
    GLfloat verts[12], uvs[12];
    for (int k = 0; k < 6; k++) {
        int c = order[k];
        verts[k * 2] = ndcX(px[c]); verts[k * 2 + 1] = ndcY(py[c]);
        uvs[k * 2] = u[c]; uvs[k * 2 + 1] = v[c];
    }
    float st[16]; getTransform(st);

    glUseProgram(mProg);
    glUniformMatrix4fv(mLocST, 1, GL_FALSE, st);
    // Upload the rotation every draw (not only at link): sDrmRotMat can
    // change at runtime (user flip props) and the program can be lazily
    // rebuilt after a GL context loss.
    static const float kId[4] = {1.0f, 0.0f, 0.0f, 1.0f};
    if (mLocRot >= 0)
        glUniformMatrix2fv(mLocRot, 1, GL_FALSE, rotMat ? rotMat : kId);
    if (mLocAlpha >= 0) glUniform1f(mLocAlpha, alpha);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTexId);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glVertexAttribPointer(mLocPos, 2, GL_FLOAT, GL_FALSE, 0, verts);
    glEnableVertexAttribArray(mLocPos);
    glVertexAttribPointer(mLocTex, 2, GL_FLOAT, GL_FALSE, 0, uvs);
    glEnableVertexAttribArray(mLocTex);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glDisableVertexAttribArray(mLocPos);
    glDisableVertexAttribArray(mLocTex);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
    glUseProgram(0);
}

bool NanoVideo::updateFrame() {
    if (!mOpen || mConsumer == nullptr) return false;
    android::status_t r = mConsumer->updateTexImage();
    return r == android::OK;
}

void NanoVideo::getTransform(float m[16]) {
    if (mConsumer != nullptr) mConsumer->getTransformMatrix(m);
    else { for (int i = 0; i < 16; i++) m[i] = 0.0f; m[0] = m[5] = m[10] = m[15] = 1.0f; }
}

void NanoVideo::play() {
    if (!mOpen) return;
    if (mEnded.load()) { seek(0.0); mEnded = false; }
    mPlaying = true;
}
void NanoVideo::pause() { mPlaying = false; }

void NanoVideo::seek(double sec) {
    if (!mOpen) return;
    if (mFed) return;               // fed mode: the demuxer repositions + flushes (flushFed)
    if (sec < 0.0) sec = 0.0;
    if (mDurationSec > 0.0 && sec > mDurationSec) sec = mDurationSec;
    mSeekTarget = sec; mSeekPending = true;
}

double NanoVideo::position() const { return mPosSec.load(); }

double NanoVideo::firstFramePts() const { std::lock_guard<std::mutex> lk(mClockMx); return mFirstFramePts; }

void NanoVideo::release() {
    mCancel.store(true);             // bail any in-flight openAsyncRun* at its next checkpoint
    mQuit = true; mPlaying = false; mEnded = false;
    { android::NanoHls* h = mHls.load(std::memory_order_acquire); if (h) h->requestStop(); }   // unblock readAt BEFORE joining
    mFedCv.notify_all();      // wake the fed worker (and any feedVideo) so it can exit
    // Stop the codec BEFORE joining: the Allwinner MPEG-2 decoder can wedge dequeueOutputBuffer
    // (it ignores the timeout and blocks forever), so the worker would never see mQuit and the
    // join would hang. stop() halts the component and makes the blocked dequeue return, freeing
    // the (single-instance) HW decoder so the next title can open.
    if (mCodec) AMediaCodec_stop(mCodec);
    if (mWorker.joinable()) mWorker.join();
    if (mReleaseThread.joinable()) mReleaseThread.join();   // in case an async release was in flight
    if (mCodec) { AMediaCodec_delete(mCodec); mCodec = nullptr; }
    if (mEx) { AMediaExtractor_delete(mEx); mEx = nullptr; }
    freeTsSource();           // after the extractor (it read through the data source)
    freeHls();                // after the extractor (HLS fetcher backed the custom source)
    mConsumer.clear();        // releases the GL texture image + consumer
    mSurface.clear();
    if (mTexId) { glDeleteTextures(1, &mTexId); mTexId = 0; }
    mVideoTrack = -1; mWidth = mHeight = 0; mDurationSec = 0.0; mPosSec = 0.0;
    mSeekPending = false; mAsyncReleasing = false; mAsyncDone = false;
    mOpen = false;
}

// Delete the TS descramble data source + free its userdata (the dup'd fd). MUST run
// after the extractor is deleted (it reads through the source). nullptr-safe / idempotent.
void NanoVideo::freeTsSource() {
    tsFreeDataSource(mDataSource, mTsPatch);   // delete source + close the dup'd fd
    mDataSource = nullptr; mTsPatch = nullptr;
}
void NanoVideo::freeHls() {
    // Must run AFTER the extractor is deleted (it read through mHls's data source).
    android::NanoHls* h = mHls.exchange(nullptr);
    if (h) delete h;
}

// Render thread: signal the worker to quit, then hand the ENTIRE blocking teardown
// (joining the decode worker + AMediaCodec_stop/delete + AMediaExtractor_delete) to a
// detached thread. The render loop never blocks. Crucially the worker join is done on
// the background thread too, NOT the render thread: the worker can be wedged inside an
// AMediaCodec dequeue while a stop holds the codec lock, so joining it on the render
// thread would freeze the heartbeat and trip the watchdog (the bug this replaces). The
// background thread joins the worker first (it exits within a dequeue timeout once mQuit
// is set, with no concurrent stop to contend the lock), then stops + frees the codec.
void NanoVideo::releaseAsync() {
    if (mAsyncReleasing) return;                 // already tearing down
    mCancel.store(true);                         // bail any in-flight openAsyncRun* checkpoint
    mQuit = true; mPlaying = false; mEnded = false; mOpen = false;
    { android::NanoHls* h = mHls.load(std::memory_order_acquire); if (h) h->requestStop(); }   // unblock readAt BEFORE the bg join
    mFedCv.notify_all();                         // wake the fed worker so the join below is fast
    // Hand the worker thread to the bg teardown. CRUCIAL ordering: mCodec / mEx are NOT
    // touched here - the decode worker is still running and reads mCodec inside its
    // AMediaCodec dequeue calls, so nulling/freeing the codec now would crash it (null
    // deref). The bg thread joins the worker FIRST (it exits on mQuit), and only THEN
    // stops + frees the codec/extractor, after which nothing reads them.
    std::thread worker = std::move(mWorker);
    mAsyncDone.store(false);
    mAsyncReleasing = true;
    mReleaseThread = std::thread([this, w = std::move(worker)]() mutable {
        // Stop the codec FIRST so a worker wedged in dequeueOutputBuffer (the Allwinner MPEG-2
        // cold-start hang ignores the dequeue timeout) is released and the join can complete;
        // otherwise the bg thread hangs forever and the single HW decoder is never freed, so
        // every subsequent title also wedges. The worker only reads mCodec inside dequeue, which
        // stop() unblocks; it then exits on mQuit before we delete the codec below.
        if (mCodec) AMediaCodec_stop(mCodec);
        if (w.joinable()) w.join();              // decode worker fully stopped before we free its codec
        if (mCodec) { AMediaCodec_delete(mCodec); mCodec = nullptr; }
        if (mEx) { AMediaExtractor_delete(mEx); mEx = nullptr; }
        freeTsSource();                          // after the extractor (it read through the source)
        freeHls();                               // after the extractor (HLS fetcher backed the source)
        mAsyncDone.store(true);
    });
}

// Render thread: once releaseAsyncDone(), finish the GL teardown (needs the EGL
// context) and clear all state. The owner deletes the object after this returns.
void NanoVideo::finishRelease() {
    if (mReleaseThread.joinable()) mReleaseThread.join();   // done already -> instant
    mConsumer.clear();
    mSurface.clear();
    if (mTexId) { glDeleteTextures(1, &mTexId); mTexId = 0; }
    mVideoTrack = -1; mWidth = mHeight = 0; mDurationSec = 0.0; mPosSec = 0.0;
    mSeekPending = false; mAsyncReleasing = false;
}

// Shutdown only: give up on a wedged background teardown instead of joining it (which would
// hang forever when AMediaCodec_stop/delete is stuck on a crashed HW decoder). Detaching makes
// mReleaseThread non-joinable so neither this nor ~NanoVideo blocks; the GL teardown is skipped
// (the EGL context dies with the process). The caller must LEAK this object - the detached thread
// is still inside the codec and references it, and the process is exiting so the OS frees it all.
void NanoVideo::abandonRelease() {
    if (mReleaseThread.joinable()) mReleaseThread.detach();
}
