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

bool NanoVideo::open(const std::string& path) {
    if (mOpen) release();
    mDisplayAspect.store(0.0f);   // non-fed extractor path: square pixels (no anamorphic source here)

    int fd = ::open(path.c_str(), O_RDONLY);
    if (fd < 0) { LOGE("open fd failed: %s", path.c_str()); return false; }
    off_t len = lseek(fd, 0, SEEK_END);
    lseek(fd, 0, SEEK_SET);

    mEx = AMediaExtractor_new();
    // Scrambled-TS (CA descriptor in the PMT but clear payload): feed a custom data
    // source that patches the CA descriptor out so the extractor sees a clear stream.
    int pmtPid = -1;
    media_status_t st;
    if (tsNeedsDescramble(fd, pmtPid)) {
        // Video-only feed: strip the audio PIDs so the system extractor's ATSParser never
        // parses the audio (its AC-3/E-AC-3 access-unit parser crashes on seek for some
        // HDHomeRun captures). Audio is decoded separately by NanoTsDemux.
        mDataSource = tsMakeDataSource(fd, (off64_t)len, pmtPid, &mTsPatch, /*stripAudio=*/true);
        st = mDataSource ? AMediaExtractor_setDataSourceCustom(mEx, mDataSource) : AMEDIA_ERROR_UNKNOWN;
        if (st == AMEDIA_OK) LOGV("TS descramble active (PMT pid %d, audio stripped) for %s", pmtPid, path.c_str());
    } else {
        st = AMediaExtractor_setDataSourceFd(mEx, fd, 0, len);
    }
    ::close(fd);   // the extractor / our data source keep their own dup
    if (st != AMEDIA_OK) { LOGE("setDataSource failed (%d)", st); release(); return false; }

    // Find the first video track + read its format.
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
    if (mVideoTrack < 0 || !fmt) { LOGE("no video track in %s", path.c_str()); release(); return false; }

    int32_t w = 0, h = 0; int64_t durUs = 0;
    AMediaFormat_getInt32(fmt, AMEDIAFORMAT_KEY_WIDTH, &w);
    AMediaFormat_getInt32(fmt, AMEDIAFORMAT_KEY_HEIGHT, &h);
    AMediaFormat_getInt64(fmt, AMEDIAFORMAT_KEY_DURATION, &durUs);
    mWidth = w > 0 ? w : 1; mHeight = h > 0 ? h : 1;
    mDurationSec = durUs > 0 ? durUs / 1e6 : 0.0;

    AMediaExtractor_selectTrack(mEx, mVideoTrack);

    // GL output: an external-OES texture latched from a BufferQueue via GLConsumer; the
    // codec renders into the matching Surface (producer side).
    glGenTextures(1, &mTexId);
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

    // Copy the mime now; it points into fmt and would dangle after the delete below.
    std::string mimeStr = mime;

    // Codec configured to render directly into the surface (HW path, zero CPU copy).
    mCodec = AMediaCodec_createDecoderByType(mimeStr.c_str());
    if (!mCodec) { LOGE("createDecoderByType(%s) failed", mimeStr.c_str()); AMediaFormat_delete(fmt); release(); return false; }
    media_status_t cs = AMediaCodec_configure(mCodec, fmt, mSurface.get(), nullptr, 0);
    AMediaFormat_delete(fmt);
    if (cs != AMEDIA_OK) { LOGE("codec configure failed (%d)", cs); release(); return false; }
    if (AMediaCodec_start(mCodec) != AMEDIA_OK) { LOGE("codec start failed"); release(); return false; }

    mQuit = false; mEnded = false; mPlaying = true; mPosSec = 0.0;
    { std::lock_guard<std::mutex> lk(mClockMx); mClockBaseNs = 0; mClockBasePts = 0.0; }
    mOpen = true;
    mWorker = std::thread(&NanoVideo::decodeLoop, this);
    LOGV("opened %s (%dx%d, %.1fs, %s)", path.c_str(), mWidth, mHeight, mDurationSec, mimeStr.c_str());
    return true;
}

// Open a network stream (http/https, incl. HLS .m3u8). Mirrors open() but points the
// extractor at the URL; the platform extractor fetches the manifest + segments. Live
// streams report duration 0 so seek() is a no-op (mDurationSec <= 0). No descramble /
// fed path here: IPTV streams decode natively through the system extractor + HW codec.
bool NanoVideo::openUrl(const std::string& url) {
    if (mOpen) release();
    mDisplayAspect.store(0.0f);

    // Native AMediaExtractor cannot fetch http(s) URLs (no Java MediaHTTPService -> UNSUPPORTED),
    // so fetch the stream in-process (curl + HLS) and feed the bytes via a custom data source.
    mHls = new android::NanoHls(url);
    if (!mHls->start()) { LOGE("NanoHls start failed: %s", url.c_str()); release(); return false; }
    mEx = AMediaExtractor_new();
    media_status_t st = AMediaExtractor_setDataSourceCustom(mEx, mHls->dataSource());
    if (st != AMEDIA_OK) { LOGE("setDataSourceCustom(url) failed (%d): %s", st, url.c_str()); release(); return false; }

    // First video track + its format.
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
    if (mVideoTrack < 0 || !fmt) { LOGE("no video track in url %s", url.c_str()); release(); return false; }

    int32_t w = 0, h = 0; int64_t durUs = 0;
    AMediaFormat_getInt32(fmt, AMEDIAFORMAT_KEY_WIDTH, &w);
    AMediaFormat_getInt32(fmt, AMEDIAFORMAT_KEY_HEIGHT, &h);
    AMediaFormat_getInt64(fmt, AMEDIAFORMAT_KEY_DURATION, &durUs);
    mWidth = w > 0 ? w : 1; mHeight = h > 0 ? h : 1;
    mDurationSec = durUs > 0 ? durUs / 1e6 : 0.0;   // 0 for live

    AMediaExtractor_selectTrack(mEx, mVideoTrack);

    glGenTextures(1, &mTexId);
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
    mConsumer->setName(android::String8("NanoVideoUrl"));
    mConsumer->setDefaultBufferSize(mWidth, mHeight);
    mSurface = new Surface(producer);

    std::string mimeStr = mime;
    mCodec = AMediaCodec_createDecoderByType(mimeStr.c_str());
    if (!mCodec) { LOGE("createDecoderByType(%s) failed", mimeStr.c_str()); AMediaFormat_delete(fmt); release(); return false; }
    media_status_t cs = AMediaCodec_configure(mCodec, fmt, mSurface.get(), nullptr, 0);
    AMediaFormat_delete(fmt);
    if (cs != AMEDIA_OK) { LOGE("url codec configure failed (%d)", cs); release(); return false; }
    if (AMediaCodec_start(mCodec) != AMEDIA_OK) { LOGE("url codec start failed"); release(); return false; }

    mQuit = false; mEnded = false; mPlaying = true; mPosSec = 0.0;
    { std::lock_guard<std::mutex> lk(mClockMx); mClockBaseNs = 0; mClockBasePts = 0.0; }
    mOpen = true;
    mWorker = std::thread(&NanoVideo::decodeLoop, this);
    LOGV("opened url %s (%dx%d, %.1fs, %s)", url.c_str(), mWidth, mHeight, mDurationSec, mimeStr.c_str());
    return true;
}

// Open in fed mode: codec + GL output set up by mime/size, input pushed by feedVideo().
bool NanoVideo::openFed(const std::string& mime, int width, int height, AMediaFormat* srcFmt) {
    if (mOpen) release();
    mFed = true; mFedMime = mime; mFedRecreate = 0;
    mDisplayAspect.store(0.0f);   // square pixels until the demuxer parses an anamorphic DAR
    if (srcFmt) {   // prefer the real decoded size from the extractor format
        int32_t w = 0, h = 0;
        if (AMediaFormat_getInt32(srcFmt, AMEDIAFORMAT_KEY_WIDTH, &w) && w > 0) width = w;
        if (AMediaFormat_getInt32(srcFmt, AMEDIAFORMAT_KEY_HEIGHT, &h) && h > 0) height = h;
    }
    mWidth = width > 0 ? width : 1; mHeight = height > 0 ? height : 1;
    mDurationSec = 0.0;

    glGenTextures(1, &mTexId);
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
    mConsumer->setName(android::String8("NanoVideoFed"));
    mConsumer->setDefaultBufferSize(mWidth, mHeight);
    mSurface = new Surface(producer);

    mCodec = createFedDecoder(false);
    if (!mCodec) { LOGE("openFed create decoder(%s) failed", mime.c_str()); release(); return false; }
    media_status_t cs;
    if (srcFmt) {
        // Configure with the extractor's full format (csd, colour aspects, ...) for a reliable
        // HW cold start. Force the mime in case the source format omitted/differs.
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
    if (cs != AMEDIA_OK) { LOGE("openFed configure failed (%d)", cs); release(); return false; }
    if (AMediaCodec_start(mCodec) != AMEDIA_OK) { LOGE("openFed codec start failed"); release(); return false; }

    mQuit = false; mEnded = false; mPlaying = true; mPosSec = 0.0;
    mFedEos = false; mFedFlush = false;
    { std::lock_guard<std::mutex> lk(mFedMx); mFedQ.clear(); }
    { std::lock_guard<std::mutex> lk(mClockMx); mClockBaseNs = 0; mClockBasePts = 0.0; }
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

void NanoVideo::decodeLoop() {
    bool sawInputEos = false;
    int hardErr = 0;   // consecutive hard codec errors -> back off + park (never hot-loop)
    bool queuedAny = false;          // at least one input AU has been queued since (re)start/flush
    int64_t lastProgressNs = monoNs(); // wall time of the last decoded frame (stall watchdog)
    while (!mQuit.load()) {
        if (!mPlaying.load() && !mSeekPending.load() && !mFedFlush.load()) {
            { std::lock_guard<std::mutex> lk(mClockMx); mClockBaseNs = 0; }   // re-anchor on resume
            usleep(8000);
            continue;
        }
        if (mFed) {
            if (mFedFlush.exchange(false)) {              // seek: drop queued input, flush, re-anchor
                AMediaCodec_flush(mCodec);
                { std::lock_guard<std::mutex> lk(mFedMx); mFedQ.clear(); }
                sawInputEos = false; mEnded = false; mFedEos = false;
                queuedAny = false; lastProgressNs = monoNs();   // need fresh input before output again
                { std::lock_guard<std::mutex> lk(mClockMx); mClockBaseNs = 0; mClockBasePts = mFedFlushBase.load(); }
                mFedCv.notify_all();
            }
        } else if (mSeekPending.exchange(false)) {
            double t = mSeekTarget.load();
            AMediaExtractor_seekTo(mEx, (int64_t)(t * 1e6), AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC);
            AMediaCodec_flush(mCodec);
            sawInputEos = false; mEnded = false;
            queuedAny = false; lastProgressNs = monoNs();
            { std::lock_guard<std::mutex> lk(mClockMx); mClockBaseNs = 0; }
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
                            queuedAny = true;
                        } else {
                            AMediaCodec_queueInputBuffer(mCodec, inIdx, 0, 0, 0, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                            sawInputEos = true;
                        }
                    } else if (have) {                    // no input buffer free; retry this AU next loop
                        std::lock_guard<std::mutex> lk(mFedMx);
                        mFedQ.push_front(std::move(au));
                    }
                }
            } else {
                ssize_t inIdx = AMediaCodec_dequeueInputBuffer(mCodec, 2000);
                if (inIdx >= 0) {
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
                }
            }
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
                int64_t now = monoNs();
                int64_t waitNs = 0;
                { std::lock_guard<std::mutex> lk(mClockMx);
                  if (mClockBaseNs == 0) { mClockBaseNs = now; mClockBasePts = pts; }
                  // Frames pace to the wall clock by PTS delta (smooth, native frame rate). When an
                  // audio clock is present (A/V), SLEW the video timeline to it - the audio is the
                  // master - by re-anchoring only when the wall-clock-predicted position diverges
                  // from the audio position beyond a small window. This keeps lip-sync without the
                  // per-frame gating that, when the decode runs ahead of audio playback, throttled
                  // the picture to the cap rate (the half-frame-rate bug).
                  double aclk = mClockFn ? mClockFn() : -1.0;
                  if (aclk >= 0.0) {
                      double predicted = mClockBasePts + (double)(now - mClockBaseNs) / 1e9;
                      if (predicted - aclk > 0.10 || aclk - predicted > 0.10) {
                          mClockBaseNs = now; mClockBasePts = aclk;
                      }
                  }
                  int64_t targetNs = mClockBaseNs + (int64_t)((pts - mClockBasePts) * 1e9);
                  waitNs = targetNs - now;
                  // PTS discontinuity (MPEG-TS / HDHomeRun captures jump the PES PTS) or a big
                  // decode stall: re-anchor instead of pacing against the stale base.
                  if (waitNs > 500000000LL || waitNs < -500000000LL) {
                      mClockBaseNs = now; mClockBasePts = pts; waitNs = 0;
                  }
                }
                if (waitNs > 0) usleep((useconds_t)(waitNs / 1000));
                mPosSec = pts;
            }
            AMediaCodec_releaseOutputBuffer(mCodec, outIdx, render);
            hardErr = 0;
            lastProgressNs = monoNs();   // the codec is alive and producing output
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
            hardErr = 0;
            lastProgressNs = monoNs();
        } else if (outIdx == AMEDIACODEC_INFO_TRY_AGAIN_LATER
                   || outIdx == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
            hardErr = 0;   // benign: the dequeue timeout already paced us
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
                LOGE("codec unrecoverable; parking decode worker");
                mEnded = true;
                while (!mQuit.load() && !mSeekPending.load() && !mFedFlush.load()) usleep(50000);
                hardErr = 0;
            }
        }

        // Stall watchdog (fed mode): the Allwinner MPEG-2 decoder intermittently cold-starts into
        // a state where it neither errors nor produces output (dequeueOutputBuffer just returns
        // TRY_AGAIN forever while the input queue backs up). hardErr never trips, so rebuild the
        // codec if input has been flowing but NO frame has decoded for a few seconds.
        if (mFed && queuedAny && !mEnded.load() && mPlaying.load() &&
            (monoNs() - lastProgressNs) > 4000000000LL) {
            LOGE("no decoded output for 4s; codec stalled, rebuilding (attempt %d)", mFedRecreate + 1);
            if (mFedRecreate < 4 && recreateFedCodec()) {
                { std::lock_guard<std::mutex> lk(mFedMx); mFedQ.clear(); }
                mFedCv.notify_all();
                sawInputEos = false; mEnded = false; hardErr = 0;
                queuedAny = false; lastProgressNs = monoNs();
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
    static const char* VS =
        "attribute vec2 aPos;\n"
        "attribute vec2 aTex;\n"
        "uniform mat4 uST;\n"
        "varying vec2 vTex;\n"
        "void main(){ vTex = (uST * vec4(aTex, 0.0, 1.0)).xy; gl_Position = vec4(aPos, 0.0, 1.0); }\n";
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
    GLint loc = glGetUniformLocation(mProg, "uTex");
    glUseProgram(mProg); glUniform1i(loc, 0); glUseProgram(0);
    return true;
}

void NanoVideo::draw(int screenW, int screenH, float rx, float ry, float rw, float rh,
                     float alpha, int fitMode) {
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

void NanoVideo::release() {
    mQuit = true; mPlaying = false; mEnded = false;
    if (mHls) mHls->requestStop();   // unblock the worker's readAt BEFORE joining (avoids deadlock)
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
    if (mHls) { delete mHls; mHls = nullptr; }
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
    mQuit = true; mPlaying = false; mEnded = false; mOpen = false;
    if (mHls) mHls->requestStop();               // unblock the worker's readAt BEFORE the bg join
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
