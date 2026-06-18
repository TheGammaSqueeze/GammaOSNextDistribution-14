#include "NanoVideo.h"

#include <GLES2/gl2ext.h>      // GL_TEXTURE_EXTERNAL_OES
#include <fcntl.h>
#include <time.h>
#include <unistd.h>
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

static int64_t monoNs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

// ---------------------------------------------------------------------------
// MPEG-TS descramble: HDHomeRun / ATSC .ts captures carry a CA_descriptor (tag
// 0x09) in the PMT even though the payload is clear (transport_scrambling_control
// is 0). Android's ATSParser sees that descriptor and reports every track as
// "...scrambled", so the file will not decode. We feed the extractor a custom data
// source that, on every read, rewrites any CA descriptor tag in the PMT to 0xFF (an
// ignored tag) and recomputes the section CRC32, leaving lengths untouched. The
// stream is then seen as clear and the MPEG-2 video decodes on the HW path.
namespace {
constexpr int kTsPkt = 188;

uint32_t mpegCrc32(const uint8_t* d, int n) {     // MPEG-2 systems CRC (poly 0x04C11DB7)
    uint32_t crc = 0xFFFFFFFFu;
    for (int i = 0; i < n; i++) {
        crc ^= (uint32_t)d[i] << 24;
        for (int b = 0; b < 8; b++)
            crc = (crc & 0x80000000u) ? ((crc << 1) ^ 0x04C11DB7u) : (crc << 1);
    }
    return crc;
}

// Clean one TS packet on the PMT pid (188 bytes). Two jobs:
//  1. The real PMT (PUSI + table_id 0x02): rewrite any CA descriptor tag to 0xFF and
//     fix the section CRC, so Android sees a clear (non-scrambled) program.
//  2. ANY other packet on the PMT pid (a foreign table - HDHomeRun multiplexes a
//     proprietary table_id 0xc0 onto the PMT pid - or a continuation): rewrite its PID
//     to 0x1FFF (a null packet). Android's ATSParser parses every PUSI packet on the
//     PMT pid as a PMT and bails with "PMT data error!" the moment it sees a non-0x02
//     table_id, so those packets must not reach it. Returns true if the packet changed.
bool patchPmtPacket(uint8_t* p, int pmtPid) {
    if (p[0] != 0x47) return false;
    int pid = ((p[1] & 0x1f) << 8) | p[2];
    if (pid != pmtPid) return false;
    // Identify the real, single-packet PMT (PUSI + table_id 0x02 + section fits).
    bool isPmt = false;
    int off = 0, secEnd = 0, secLen = 0;
    if ((p[1] >> 6) & 1) {                              // PUSI
        int afc = (p[3] >> 4) & 3;
        if (afc != 0 && afc != 2) {                     // has payload
            off = 4;
            if (afc == 3) off += 1 + p[4];              // adaptation field
            if (off + 1 <= kTsPkt) {
                off += 1 + p[off];                      // pointer_field
                if (off + 3 <= kTsPkt && p[off] == 0x02) {
                    secLen = ((p[off + 1] & 0x0f) << 8) | p[off + 2];
                    secEnd = off + 3 + secLen;          // includes the 4 CRC bytes
                    if (secEnd <= kTsPkt && secLen >= 13) isPmt = true;
                }
            }
        }
    }
    if (!isPmt) {
        // Foreign table / continuation on the PMT pid -> turn it into a null packet so
        // ATSParser only ever parses the real PMT on this pid.
        p[1] = (uint8_t)((p[1] & 0xE0) | 0x1F);
        p[2] = 0xFF;
        return true;
    }
    int pil = ((p[off + 10] & 0x0f) << 8) | p[off + 11];
    int pp = off + 12, piEnd = pp + pil;
    int crcStart = secEnd - 4;
    if (piEnd > crcStart) return false;                // malformed
    bool changed = false;
    while (pp + 2 <= piEnd) {                           // program-level descriptors
        int len = p[pp + 1];
        if (p[pp] == 0x09) { p[pp] = 0xFF; changed = true; }
        pp += 2 + len;
    }
    int es = piEnd;                                     // ES loop
    while (es + 5 <= crcStart) {
        int esil = ((p[es + 3] & 0x0f) << 8) | p[es + 4];
        int d = es + 5, dEnd = d + esil;
        if (dEnd > crcStart) break;
        while (d + 2 <= dEnd) {                         // ES-level descriptors
            int len = p[d + 1];
            if (p[d] == 0x09) { p[d] = 0xFF; changed = true; }
            d += 2 + len;
        }
        es = dEnd;
    }
    if (changed) {
        uint32_t crc = mpegCrc32(p + off, crcStart - off);
        p[crcStart]     = (crc >> 24) & 0xFF;
        p[crcStart + 1] = (crc >> 16) & 0xFF;
        p[crcStart + 2] = (crc >> 8) & 0xFF;
        p[crcStart + 3] = crc & 0xFF;
    }
    return changed;
}

// Data source userdata: a dup'd fd + the file size + the PMT pid to patch.
struct TsPatch { int fd; off64_t size; int pmtPid; };

ssize_t tsReadAt(void* u, off64_t offset, void* buffer, size_t size) {
    TsPatch* t = (TsPatch*)u;
    if (size == 0) return 0;
    if (offset >= t->size) return -1;                  // EOF (API: -1 at end of stream)
    // Read the ENCLOSING 188-aligned window and patch every complete packet in it, then
    // copy out the requested sub-range. This guarantees every byte the extractor sees
    // comes from a fully-patched packet even when its reads are not packet-aligned - the
    // earlier "patch only packets fully inside this read" let foreign-table packets that
    // straddled a read boundary slip through, leaving ATSParser to bail with "PMT data
    // error".
    off64_t aStart = (offset / kTsPkt) * kTsPkt;
    off64_t aEnd = ((offset + (off64_t)size + kTsPkt - 1) / kTsPkt) * kTsPkt;
    if (aEnd > t->size) aEnd = ((t->size + kTsPkt - 1) / kTsPkt) * kTsPkt;
    size_t span = (size_t)(aEnd - aStart);
    std::vector<uint8_t> tmp(span);
    ssize_t got = pread(t->fd, tmp.data(), span, aStart);
    if (got <= 0) return got == 0 ? -1 : got;
    for (off64_t p = 0; p + kTsPkt <= got; p += kTsPkt)
        patchPmtPacket(tmp.data() + (size_t)p, t->pmtPid);
    off64_t skip = offset - aStart;
    if (skip >= got) return -1;
    size_t avail = (size_t)(got - skip);
    size_t n = avail < size ? avail : size;
    memcpy(buffer, tmp.data() + (size_t)skip, n);
    return (ssize_t)n;
}
ssize_t tsGetSize(void* u) { return (ssize_t)((TsPatch*)u)->size; }
void    tsClose(void* /*u*/) {}   // fd is freed in NanoVideo::freeTsSource after the source is deleted

// Sniff a bounded prefix: returns true if `fd` is an MPEG-TS whose PMT carries a CA
// descriptor (so Android would flag it scrambled), and sets outPmtPid.
bool tsNeedsDescramble(int fd, int& outPmtPid) {
    static const int kScan = kTsPkt * 4000;            // ~752 KB prefix
    std::vector<uint8_t> buf(kScan);
    ssize_t n = pread(fd, buf.data(), kScan, 0);
    if (n < kTsPkt * 8) return false;
    if (buf[0] != 0x47 || buf[kTsPkt] != 0x47 || buf[2 * kTsPkt] != 0x47) return false;  // need TS sync @0
    // PAT (pid 0) -> first program's PMT pid.
    int pmtPid = -1;
    for (ssize_t i = 0; i + kTsPkt <= n && pmtPid < 0; i += kTsPkt) {
        uint8_t* p = &buf[i];
        if (p[0] != 0x47) continue;
        if ((((p[1] & 0x1f) << 8) | p[2]) != 0) continue;     // PAT pid 0
        if (((p[1] >> 6) & 1) == 0) continue;                  // PUSI
        int afc = (p[3] >> 4) & 3, off = 4;
        if (afc == 3) off += 1 + p[4]; else if (afc != 1) continue;
        off += 1 + p[off];                                     // pointer_field
        if (off + 8 > kTsPkt) continue;
        int sl = ((p[off + 1] & 0x0f) << 8) | p[off + 2];
        int end = off + 3 + sl - 4;                            // before CRC
        if (end > kTsPkt) end = kTsPkt;
        for (int j = off + 8; j + 4 <= end; j += 4) {
            int pn = (p[j] << 8) | p[j + 1];
            int ppid = ((p[j + 2] & 0x1f) << 8) | p[j + 3];
            if (pn != 0) { pmtPid = ppid; break; }
        }
    }
    if (pmtPid < 0) return false;
    // Scan PMT packets for a CA descriptor (program-level or ES-level).
    for (ssize_t i = 0; i + kTsPkt <= n; i += kTsPkt) {
        uint8_t* p = &buf[i];
        if (p[0] != 0x47) continue;
        if ((((p[1] & 0x1f) << 8) | p[2]) != pmtPid) continue;
        if (((p[1] >> 6) & 1) == 0) continue;
        int afc = (p[3] >> 4) & 3, off = 4;
        if (afc == 3) off += 1 + p[4]; else if (afc != 1) continue;
        off += 1 + p[off];
        if (off + 12 > kTsPkt) continue;
        if (p[off] != 0x02) continue;
        int secLen = ((p[off + 1] & 0x0f) << 8) | p[off + 2];
        int secEnd = off + 3 + secLen;
        if (secEnd > kTsPkt) continue;
        int pil = ((p[off + 10] & 0x0f) << 8) | p[off + 11];
        int pp = off + 12, piEnd = pp + pil, crcStart = secEnd - 4;
        if (piEnd > crcStart) continue;
        while (pp + 2 <= piEnd) { if (p[pp] == 0x09) { outPmtPid = pmtPid; return true; } pp += 2 + p[pp + 1]; }
        int es = piEnd;
        while (es + 5 <= crcStart) {
            int esil = ((p[es + 3] & 0x0f) << 8) | p[es + 4];
            int d = es + 5, dEnd = d + esil;
            if (dEnd > crcStart) break;
            while (d + 2 <= dEnd) { if (p[d] == 0x09) { outPmtPid = pmtPid; return true; } d += 2 + p[d + 1]; }
            es = dEnd;
        }
    }
    return false;
}

// Build a descrambling custom data source for an already-detected TS fd. Returns the
// AMediaDataSource (owns a dup of fd via the TsPatch userdata), or nullptr on failure.
AMediaDataSource* makeTsDataSource(int fd, off64_t size, int pmtPid, void** outUserdata) {
    int dfd = dup(fd);
    if (dfd < 0) return nullptr;
    TsPatch* t = new TsPatch{dfd, size, pmtPid};
    AMediaDataSource* ds = AMediaDataSource_new();
    if (!ds) { ::close(dfd); delete t; return nullptr; }
    AMediaDataSource_setUserdata(ds, t);
    AMediaDataSource_setReadAt(ds, tsReadAt);
    AMediaDataSource_setGetSize(ds, tsGetSize);
    AMediaDataSource_setClose(ds, tsClose);
    *outUserdata = t;
    return ds;
}
}  // namespace

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
        ds = makeTsDataSource(fd, (off64_t)len, pmtPid, &ud);
        st = ds ? AMediaExtractor_setDataSourceCustom(ex, ds) : AMEDIA_ERROR_UNKNOWN;
    } else {
        st = AMediaExtractor_setDataSourceFd(ex, fd, 0, len);
    }
    ::close(fd);
    auto cleanup = [&]() {
        AMediaExtractor_delete(ex);
        if (ds) AMediaDataSource_delete(ds);
        if (ud) { TsPatch* t = (TsPatch*)ud; if (t->fd >= 0) ::close(t->fd); delete t; }
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

bool NanoVideo::open(const std::string& path) {
    if (mOpen) release();

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
        mDataSource = makeTsDataSource(fd, (off64_t)len, pmtPid, &mTsPatch);
        st = mDataSource ? AMediaExtractor_setDataSourceCustom(mEx, mDataSource) : AMEDIA_ERROR_UNKNOWN;
        if (st == AMEDIA_OK) LOGV("TS descramble active (PMT pid %d) for %s", pmtPid, path.c_str());
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

void NanoVideo::decodeLoop() {
    bool sawInputEos = false;
    int hardErr = 0;   // consecutive hard codec errors -> back off + park (never hot-loop)
    while (!mQuit.load()) {
        if (!mPlaying.load() && !mSeekPending.load()) {
            { std::lock_guard<std::mutex> lk(mClockMx); mClockBaseNs = 0; }   // re-anchor on resume
            usleep(8000);
            continue;
        }
        if (mSeekPending.exchange(false)) {
            double t = mSeekTarget.load();
            AMediaExtractor_seekTo(mEx, (int64_t)(t * 1e6), AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC);
            AMediaCodec_flush(mCodec);
            sawInputEos = false; mEnded = false;
            { std::lock_guard<std::mutex> lk(mClockMx); mClockBaseNs = 0; }
        }

        // Feed one input sample.
        if (!sawInputEos) {
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
                }
            }
        }

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
                  int64_t targetNs = mClockBaseNs + (int64_t)((pts - mClockBasePts) * 1e9);
                  waitNs = targetNs - now; }
                if (waitNs > 0 && waitNs < 1000000000LL) usleep((useconds_t)(waitNs / 1000));
                mPosSec = pts;
            }
            AMediaCodec_releaseOutputBuffer(mCodec, outIdx, render);
            hardErr = 0;
            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
                mEnded = true;
                while (!mQuit.load() && mEnded.load() && !mSeekPending.load()) usleep(16000);
            }
        } else if (outIdx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat* of = AMediaCodec_getOutputFormat(mCodec);
            int32_t w = 0, h = 0;
            if (AMediaFormat_getInt32(of, AMEDIAFORMAT_KEY_WIDTH, &w) && w > 0) mWidth = w;
            if (AMediaFormat_getInt32(of, AMEDIAFORMAT_KEY_HEIGHT, &h) && h > 0) mHeight = h;
            if (mConsumer != nullptr) mConsumer->setDefaultBufferSize(mWidth, mHeight);
            AMediaFormat_delete(of);
            hardErr = 0;
        } else if (outIdx == AMEDIACODEC_INFO_TRY_AGAIN_LATER
                   || outIdx == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
            hardErr = 0;   // benign: the dequeue timeout already paced us
        } else {
            // Hard codec error (e.g. the component went to a released/error state). Back off
            // so we never burn CPU hot-looping a dead codec, and park after ~1s of failures.
            if (hardErr == 0) LOGE("decode output error %zd; backing off", (ssize_t)outIdx);
            usleep(20000);
            if (++hardErr > 50) {
                LOGE("codec unrecoverable; parking decode worker");
                mEnded = true;
                while (!mQuit.load() && !mSeekPending.load()) usleep(50000);
                hardErr = 0;
            }
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
    float vw = (float)mWidth, vh = (float)mHeight;
    float s;
    switch (fitMode) {
        case 1:  s = fmaxf(rw / vw, rh / vh); break;
        case 2:  s = 1.0f; break;
        case 3:  s = fmaxf(rw / vw, rh / vh) * 1.33f; break;
        case 4:  s = 2.0f; break;
        default: s = fminf(rw / vw, rh / vh); break;
    }
    float dw = vw * s, dh = vh * s;
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
    if (sec < 0.0) sec = 0.0;
    if (mDurationSec > 0.0 && sec > mDurationSec) sec = mDurationSec;
    mSeekTarget = sec; mSeekPending = true;
}

double NanoVideo::position() const { return mPosSec.load(); }

void NanoVideo::release() {
    mQuit = true; mPlaying = false; mEnded = false;
    if (mWorker.joinable()) mWorker.join();
    if (mReleaseThread.joinable()) mReleaseThread.join();   // in case an async release was in flight
    if (mCodec) { AMediaCodec_stop(mCodec); AMediaCodec_delete(mCodec); mCodec = nullptr; }
    if (mEx) { AMediaExtractor_delete(mEx); mEx = nullptr; }
    freeTsSource();           // after the extractor (it read through the data source)
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
    if (mDataSource) { AMediaDataSource_delete(mDataSource); mDataSource = nullptr; }
    if (mTsPatch) {
        TsPatch* t = (TsPatch*)mTsPatch;
        if (t->fd >= 0) ::close(t->fd);
        delete t; mTsPatch = nullptr;
    }
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
    // Hand the worker thread to the bg teardown. CRUCIAL ordering: mCodec / mEx are NOT
    // touched here - the decode worker is still running and reads mCodec inside its
    // AMediaCodec dequeue calls, so nulling/freeing the codec now would crash it (null
    // deref). The bg thread joins the worker FIRST (it exits on mQuit), and only THEN
    // stops + frees the codec/extractor, after which nothing reads them.
    std::thread worker = std::move(mWorker);
    mAsyncDone.store(false);
    mAsyncReleasing = true;
    mReleaseThread = std::thread([this, w = std::move(worker)]() mutable {
        if (w.joinable()) w.join();              // decode worker fully stopped before we free its codec
        if (mCodec) { AMediaCodec_stop(mCodec); AMediaCodec_delete(mCodec); mCodec = nullptr; }
        if (mEx) { AMediaExtractor_delete(mEx); mEx = nullptr; }
        freeTsSource();                          // after the extractor (it read through the source)
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
