// NanoTsDemux - single-pass in-process MPEG-TS demuxer. See NanoTsDemux.h.
#include "NanoTsDemux.h"

#include "NanoAudio.h"
#include "NanoVideo.h"
#include "NanoHls.h"

#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <cstring>
#include <algorithm>

#include <android/log.h>
#define TLOGI(...) __android_log_print(ANDROID_LOG_INFO,  "GammaOSNano", __VA_ARGS__)
#define TLOGW(...) __android_log_print(ANDROID_LOG_WARN,  "GammaOSNano", __VA_ARGS__)

namespace android {

namespace {
constexpr int kPkt = 188;

// cc_data display-order reorder depth. Well above the MPEG-2 decode/display gap (a couple of
// B-frames) so the oldest buffered picture is final before it is decoded; ~0.5s of latency at
// 30fps, imperceptible for captions.
constexpr size_t kCcReorderWindow = 16;

// 33-bit PCR base (90kHz) from the 6 PCR bytes; returns seconds (ignores the 27MHz ext).
double pcrSeconds(const uint8_t* b) {
    uint64_t base = ((uint64_t)b[0] << 25) | ((uint64_t)b[1] << 17) |
                    ((uint64_t)b[2] << 9)  | ((uint64_t)b[3] << 1)  | (b[4] >> 7);
    return (double)base / 90000.0;
}
}  // namespace

NanoTsDemux::~NanoTsDemux() { close(); }

// Scan a buffer for the TS sync alignment (offset where 0x47 repeats every 188 bytes).
static int findAlign(const uint8_t* buf, size_t n) {
    for (int off = 0; off < kPkt && off + 3 * kPkt < (int)n; off++) {
        if (buf[off] == 0x47 && buf[off + kPkt] == 0x47 && buf[off + 2 * kPkt] == 0x47)
            return off;
    }
    return -1;
}

bool NanoTsDemux::open(const std::string& path) {
    close();
    mPath = path;
    // Per-title state reset. This single NanoTsDemux instance is reused (close()+open())
    // for every .ts title, but close()/stop()/workerFunc do NOT clear the PTS origin, the
    // AC-3 stream rate, or the CEA-608 presence probe. Without this, the 2nd and later .ts
    // titles inherit the 1st title's mPtsBaseUs (so position()/the seek bar/Resume are pinned
    // near 0 or wildly offset, and the audio-master slew fights a bogus video clock) and its
    // exhausted caption probe (mCcProbe==300 -> the new title's captions are never detected,
    // or it falsely reports the previous title as having captions). Safe to reset here: close()
    // has already joined any prior worker, so nothing else touches these members.
    mPtsBaseUs = -1;
    mStreamRate = 48000;
    mCcSeen.store(false);
    mCcProbe = 0;
    int fd = ::open(path.c_str(), O_RDONLY);
    if (fd < 0) return false;
    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size <= 0) { ::close(fd); return false; }
    mFileSize = st.st_size;

    // Bounded head read for PSI + alignment + first PCR.
    const size_t kHead = (size_t)kPkt * 4000;   // ~752 KB
    std::vector<uint8_t> head(kHead);
    ssize_t n = pread64(fd, head.data(), kHead, 0);
    if (n < kPkt * 8) { ::close(fd); return false; }
    mAlign = findAlign(head.data(), (size_t)n);
    if (mAlign < 0) { ::close(fd); return false; }

    if (!parsePsi(head.data(), (size_t)n)) { ::close(fd); return false; }
    extractVideoCsd(head.data(), (size_t)n);   // H.264 SPS/PPS + dims (no-op for MPEG-2)

    // Bitrate from a LOCAL pair of PCRs in the head (first and last PCR within the head
    // window). End-to-end (head-PCR vs tail-PCR) is unreliable here: HDHomeRun/ATSC
    // captures have PCR discontinuities, so the span would be garbage (e.g. 70 min for a
    // 47 min show). A local span of a second or two is essentially the instantaneous CBR.
    mFirstPcr = -1.0;
    off64_t firstPcrByte = mAlign, lastPcrByte = mAlign;
    double lastHeadPcr = -1.0;
    for (ssize_t i = mAlign; i + kPkt <= n; i += kPkt) {
        const uint8_t* p = &head[i];
        if (p[0] != 0x47) continue;
        if ((((p[1] & 0x1f) << 8) | p[2]) != mPcrPid) continue;
        int afc = (p[3] >> 4) & 3;
        if ((afc & 2) == 0 || p[4] == 0) continue;
        if (!(p[5] & 0x10)) continue;
        double pcr = pcrSeconds(&p[6]);
        if (mFirstPcr < 0) { mFirstPcr = pcr; firstPcrByte = i; }
        lastHeadPcr = pcr; lastPcrByte = i;
    }
    if (mFirstPcr >= 0 && lastHeadPcr > mFirstPcr && lastPcrByte > firstPcrByte) {
        mBytesPerSec = (double)(lastPcrByte - firstPcrByte) / (lastHeadPcr - mFirstPcr);
        mDurationSec = mBytesPerSec > 0 ? (double)mFileSize / mBytesPerSec : 0.0;
    } else {
        mBytesPerSec = 0.0;
        mDurationSec = 0.0;
    }

    mFd = fd;
    TLOGI("NanoTsDemux: open %s pmt=%d pcr=%d video=%d audio=%zu dur=%.1fs",
          path.c_str(), mPmtPid, mPcrPid, mVideoPid, mAudio.size(), mDurationSec);
    for (size_t k = 0; k < mAudio.size(); k++)
        TLOGI("NanoTsDemux:   audio[%zu] pid=%d lang=%s", k, mAudio[k].pid,
              mAudio[k].lang.empty() ? "?" : mAudio[k].lang.c_str());
    return true;
}

// Shared PAT -> PMT parse over a head buffer (used by both the file open and the live HLS
// open). Sets mPmtPid, mPcrPid, mVideoPid/mVideoStreamType and the audio track list. The
// head must already be aligned (mAlign found). Returns false if no program is parseable.
bool NanoTsDemux::parsePsi(const uint8_t* head, size_t n) {
    // PAT -> first program's PMT pid.
    mPmtPid = -1;
    for (size_t i = mAlign; i + kPkt <= n && mPmtPid < 0; i += kPkt) {
        const uint8_t* p = &head[i];
        if (p[0] != 0x47) continue;
        if ((((p[1] & 0x1f) << 8) | p[2]) != 0) continue;     // PAT pid 0
        if (((p[1] >> 6) & 1) == 0) continue;                  // PUSI
        int afc = (p[3] >> 4) & 3, off = 4;
        if (afc == 3) off += 1 + p[4]; else if (afc != 1) continue;
        off += 1 + p[off];                                     // pointer_field
        if (off + 8 > kPkt) continue;
        if (p[off] != 0x00) continue;                          // table_id PAT
        int sl = ((p[off + 1] & 0x0f) << 8) | p[off + 2];
        int end = off + 3 + sl - 4;
        if (end > kPkt) end = kPkt;
        for (int j = off + 8; j + 4 <= end; j += 4) {
            int pn = (p[j] << 8) | p[j + 1];
            int ppid = ((p[j + 2] & 0x1f) << 8) | p[j + 3];
            if (pn != 0) { mPmtPid = ppid; break; }
        }
    }
    if (mPmtPid < 0) return false;

    // PMT -> PCR pid, video pid, audio tracks (with language).
    mAudio.clear(); mVideoPid = -1; mPcrPid = -1; mVideoStreamType = 0;
    for (size_t i = mAlign; i + kPkt <= n; i += kPkt) {
        const uint8_t* p = &head[i];
        if (p[0] != 0x47) continue;
        if ((((p[1] & 0x1f) << 8) | p[2]) != mPmtPid) continue;
        if (((p[1] >> 6) & 1) == 0) continue;
        int afc = (p[3] >> 4) & 3, off = 4;
        if (afc == 3) off += 1 + p[4]; else if (afc != 1) continue;
        off += 1 + p[off];
        if (off + 12 > kPkt) continue;
        if (p[off] != 0x02) continue;                          // table_id PMT
        int secLen = ((p[off + 1] & 0x0f) << 8) | p[off + 2];
        int secEnd = off + 3 + secLen;
        if (secEnd > kPkt) continue;                           // single-packet PMT
        mPcrPid = ((p[off + 8] & 0x1f) << 8) | p[off + 9];
        int pil = ((p[off + 10] & 0x0f) << 8) | p[off + 11];
        int es = off + 12 + pil, crcStart = secEnd - 4;
        while (es + 5 <= crcStart) {
            int stype = p[es];
            int epid = ((p[es + 1] & 0x1f) << 8) | p[es + 2];
            int esil = ((p[es + 3] & 0x0f) << 8) | p[es + 4];
            int d = es + 5, dEnd = d + esil;
            if (dEnd > crcStart) break;
            std::string lang;
            bool descAc3 = false;                              // AC-3 via registration/descriptor (DVB)
            while (d + 2 <= dEnd) {                            // ES descriptors
                int dt = p[d], dl = p[d + 1];
                if (dt == 0x0a && dl >= 3)                     // ISO_639_language
                    lang.assign((const char*)&p[d + 2], 3);
                if (dt == 0x6a || dt == 0x7a) descAc3 = true;  // DVB AC-3 / E-AC-3 descriptor
                d += 2 + dl;
            }
            bool isVideo = (stype == 0x01 || stype == 0x02 || stype == 0x1b || stype == 0x24);
            bool isAc3   = (stype == 0x81) || descAc3;         // ATSC AC-3, or DVB-flagged
            bool isAac   = (stype == 0x0f);                    // ISO/IEC 13818-7 AAC (ADTS)
            if (isVideo && mVideoPid < 0) { mVideoPid = epid; mVideoStreamType = stype; }
            if (isAc3 || isAac) {
                AudioTrack t; t.pid = epid; t.streamType = isAc3 ? 0x81 : 0x0f; t.lang = lang;
                mAudio.push_back(t);
            }
            es = dEnd;
        }
        break;
    }
    return (mVideoPid >= 0 || !mAudio.empty());
}

// Extract the H.264 SPS/PPS (and frame dimensions) from the head so the fed HW decoder can
// be configured on a live URL where there is no system extractor to probe the track format.
// HLS segments are independently decodable, so SPS+PPS+IDR sit at the segment start, in the
// head. Reassemble the video PID's bytes (TS payloads concatenated; the PES headers stay
// inline but have forbidden_zero=1 so the NAL scan skips them) and pull the first SPS/PPS.
namespace {
// Minimal Annex-B + exp-golomb SPS parser for coded width/height (best-effort; 0 on failure).
struct BitRdr {
    const uint8_t* d; size_t n; size_t bit = 0;
    BitRdr(const uint8_t* p, size_t len) : d(p), n(len) {}
    int u1() { if (bit >= n * 8) return 0; int b = (d[bit >> 3] >> (7 - (bit & 7))) & 1; bit++; return b; }
    uint32_t u(int k) { uint32_t v = 0; while (k--) v = (v << 1) | u1(); return v; }
    uint32_t ue() { int z = 0; while (bit < n * 8 && u1() == 0 && z < 32) z++; return (z ? ((1u << z) - 1 + u(z)) : 0); }
    int32_t se() { uint32_t k = ue(); return (k & 1) ? (int32_t)((k + 1) >> 1) : -(int32_t)(k >> 1); }
};
// Strip emulation-prevention 0x03 bytes from a NAL RBSP.
static std::vector<uint8_t> unescapeRbsp(const uint8_t* p, size_t n) {
    std::vector<uint8_t> o; o.reserve(n);
    for (size_t i = 0; i < n; i++) {
        if (i + 2 < n && p[i] == 0 && p[i + 1] == 0 && p[i + 2] == 3) { o.push_back(0); o.push_back(0); i += 2; }
        else o.push_back(p[i]);
    }
    return o;
}
static void parseSpsWh(const uint8_t* sps, size_t len, int& w, int& h) {
    if (len < 4) return;
    std::vector<uint8_t> rb = unescapeRbsp(sps + 1, len - 1);   // skip the 1-byte NAL header
    BitRdr r(rb.data(), rb.size());
    int profile = r.u(8); r.u(8); r.u(8);                       // profile, constraints, level
    r.ue();                                                     // seq_parameter_set_id
    if (profile == 100 || profile == 110 || profile == 122 || profile == 244 || profile == 44 ||
        profile == 83 || profile == 86 || profile == 118 || profile == 128 || profile == 138 ||
        profile == 139 || profile == 134 || profile == 135) {
        int chroma = r.ue();
        if (chroma == 3) r.u1();                                // separate_colour_plane_flag
        r.ue(); r.ue();                                         // bit_depth_luma/chroma minus8
        r.u1();                                                 // qpprime_y_zero_transform_bypass
        if (r.u1()) {                                           // seq_scaling_matrix_present
            int lists = (chroma != 3) ? 8 : 12;
            for (int i = 0; i < lists; i++) {
                if (r.u1()) {                                   // scaling_list_present
                    int sz = (i < 6) ? 16 : 64, last = 8, next = 8;
                    for (int j = 0; j < sz; j++) {
                        if (next != 0) { int delta = r.se(); next = (last + delta + 256) % 256; }
                        last = (next == 0) ? last : next;
                    }
                }
            }
        }
    }
    r.ue();                                                     // log2_max_frame_num_minus4
    int poc = r.ue();
    if (poc == 0) r.ue();                                       // log2_max_pic_order_cnt_lsb_minus4
    else if (poc == 1) {
        r.u1(); r.se(); r.se();
        int num = r.ue();
        for (int i = 0; i < num; i++) r.se();
    }
    r.ue();                                                     // max_num_ref_frames
    r.u1();                                                     // gaps_in_frame_num_value_allowed
    int wMbs = r.ue() + 1;                                      // pic_width_in_mbs_minus1
    int hMap = r.ue() + 1;                                      // pic_height_in_map_units_minus1
    int frameMbsOnly = r.u1();
    if (!frameMbsOnly) r.u1();                                  // mb_adaptive_frame_field
    r.u1();                                                     // direct_8x8_inference
    int cl = 0, cr = 0, ct = 0, cb = 0;
    if (r.u1()) { cl = r.ue(); cr = r.ue(); ct = r.ue(); cb = r.ue(); }   // frame_cropping
    int width = wMbs * 16;
    int height = (2 - frameMbsOnly) * hMap * 16;
    // crop units: 4:2:0 -> 2 horiz, 2*(2-frameMbsOnly) vert (approximate, the common case)
    width  -= (cl + cr) * 2;
    height -= (ct + cb) * 2 * (2 - frameMbsOnly);
    if (width > 0 && width <= 8192 && height > 0 && height <= 8192) { w = width; h = height; }
}
}  // namespace

void NanoTsDemux::extractVideoCsd(const uint8_t* head, size_t n) {
    mVidSps.clear(); mVidPps.clear(); mVideoW = mVideoH = 0;
    if (mVideoStreamType != 0x1b) return;   // H.264 only (HEVC csd extraction deferred)
    // Concatenate the video PID's TS payloads from the head.
    std::vector<uint8_t> es; es.reserve(96 * 1024);
    for (size_t i = mAlign; i + kPkt <= n && es.size() < 96 * 1024; i += kPkt) {
        const uint8_t* p = &head[i];
        if (p[0] != 0x47) continue;
        if ((((p[1] & 0x1f) << 8) | p[2]) != mVideoPid) continue;
        int afc = (p[3] >> 4) & 3;
        if (afc == 0 || afc == 2) continue;
        int off = 4;
        if (afc == 3) off += 1 + p[4];
        if (off >= kPkt) continue;
        es.insert(es.end(), p + off, p + kPkt);
    }
    // Walk Annex-B NAL units; capture the first SPS (type 7) and PPS (type 8).
    static const uint8_t kStart[4] = {0, 0, 0, 1};
    auto findStart = [&](size_t from, size_t& scLen) -> size_t {
        for (size_t k = from; k + 3 <= es.size(); k++) {
            if (es[k] == 0 && es[k + 1] == 0 && es[k + 2] == 1) { scLen = 3; return k; }
            if (k + 4 <= es.size() && es[k] == 0 && es[k + 1] == 0 && es[k + 2] == 0 && es[k + 3] == 1) { scLen = 4; return k; }
        }
        return es.size();
    };
    size_t scLen = 0; size_t s = findStart(0, scLen);
    while (s < es.size()) {
        size_t nalStart = s + scLen;
        size_t scLen2 = 0; size_t next = findStart(nalStart, scLen2);
        if (nalStart < es.size()) {
            int forbidden = es[nalStart] & 0x80;
            int type = es[nalStart] & 0x1f;
            size_t nalLen = next - nalStart;
            if (!forbidden && nalLen > 0) {
                if (type == 7 && mVidSps.empty()) {
                    mVidSps.assign(kStart, kStart + 4);
                    mVidSps.insert(mVidSps.end(), es.begin() + nalStart, es.begin() + next);
                    parseSpsWh(&es[nalStart], nalLen, mVideoW, mVideoH);
                } else if (type == 8 && mVidPps.empty()) {
                    mVidPps.assign(kStart, kStart + 4);
                    mVidPps.insert(mVidPps.end(), es.begin() + nalStart, es.begin() + next);
                }
            }
        }
        if (!mVidSps.empty() && !mVidPps.empty()) break;
        s = next; scLen = scLen2;
    }
}

bool NanoTsDemux::videoCsd(std::vector<uint8_t>& sps, std::vector<uint8_t>& pps) const {
    sps = mVidSps; pps = mVidPps;
    return !mVidSps.empty();
}

ssize_t NanoTsDemux::readSrc(uint8_t* buf, size_t len, off64_t pos) {
    if (mHls) return mHls->readAt(pos, buf, len);
    if (mFd >= 0) return pread64(mFd, buf, len, pos);
    return -1;
}

// Live HLS open: parse the program from the streaming byte source (NanoHls). See the header.
bool NanoTsDemux::openFromHls(NanoHls* hls) {
    close();
    if (!hls) return false;
    auto fail = [&]() -> bool { hls->requestStop(); delete hls; mHls = nullptr; mLive = false; return false; };
    mLive = true; mPath = "(live)";
    mPtsBaseUs = -1; mStreamRate = 48000; mCcSeen.store(false); mCcProbe = 0;
    mHls = hls;
    // Pre-buffer so the head holds a full PAT/PMT (+ H.264 SPS/PPS at the segment start).
    for (int i = 0; i < 800 && hls->produced() < 256 * 1024; i++) usleep(10000);   // up to ~8s
    const size_t kHead = (size_t)kPkt * 4000;   // ~752 KB
    std::vector<uint8_t> head(kHead);
    ssize_t n = readSrc(head.data(), kHead, 0);
    if (n < kPkt * 8) return fail();
    mAlign = findAlign(head.data(), (size_t)n);
    if (mAlign < 0) return fail();
    if (!parsePsi(head.data(), (size_t)n)) return fail();
    extractVideoCsd(head.data(), (size_t)n);
    mFileSize = 0; mBytesPerSec = 0.0; mDurationSec = 0.0;   // live: no duration, no seek
    TLOGI("NanoTsDemux: openFromHls video=%d(%s %dx%d) audio=%zu sps=%zu pps=%zu",
          mVideoPid, videoMime(), mVideoW, mVideoH, mAudio.size(), mVidSps.size(), mVidPps.size());
    for (size_t k = 0; k < mAudio.size(); k++)
        TLOGI("NanoTsDemux:   audio[%zu] pid=%d type=0x%02x lang=%s", k, mAudio[k].pid,
              mAudio[k].streamType, mAudio[k].lang.empty() ? "?" : mAudio[k].lang.c_str());
    return true;
}

void NanoTsDemux::close() {
    stop();
    if (mFd >= 0) { ::close(mFd); mFd = -1; }
    if (mHls) { mHls->requestStop(); delete mHls; mHls = nullptr; }
    mLive = false;
    mVidSps.clear(); mVidPps.clear(); mVideoW = mVideoH = 0;
    mAudio.clear();
    mVideoPid = mPcrPid = mPmtPid = -1;
    mDurationSec = 0.0;
    { std::lock_guard<std::mutex> lk(mCueMx); mCues.clear(); }
}

const char* NanoTsDemux::videoMime() const {
    switch (mVideoStreamType) {
        case 0x1b: return "video/avc";
        case 0x24: return "video/hevc";
        case 0x10: return "video/mp4v-es";
        default:   return "video/mpeg2";    // 0x01/0x02 MPEG-1/2 (the common ATSC case)
    }
}

off64_t NanoTsDemux::estimateByteForTime(double sec) const {
    if (mBytesPerSec <= 0 || sec <= 0) return mAlign;
    off64_t b = (off64_t)(sec * mBytesPerSec);
    if (b >= mFileSize) b = mFileSize - kPkt;
    // align to a packet boundary relative to mAlign.
    b -= (b - mAlign) % kPkt;
    if (b < mAlign) b = mAlign;
    return b;
}

bool NanoTsDemux::start(NanoVideo* video, NanoAudioPlayer* audio, int audioIndex, double startSec,
                        bool audioPreOpened) {
    if (mFd < 0 && !mHls) return false;
    stop();
    mVideoSink = video;
    mSink = audio;
    mAudioFedOpen = audioPreOpened;   // live (false): the demuxer opens the fed ring lazily
    // Arm gate: the caller already opened+prerolled the ring (file path) so the host may arm as
    // soon as the first frame lands; live defers until ensureAudioFed() finishes the lazy open.
    mLiveAudioReady.store(audio == nullptr || audioPreOpened);
    mSelPid.store((audioIndex >= 0 && audioIndex < (int)mAudio.size()) ? mAudio[audioIndex].pid : -1);
    mPendSelPid.store(-1);
    mPendSeek.store(-1.0);
    mStop.store(false);
    // Audio-master pacing: the picture follows the audio playback clock so the two stay in
    // lip-sync (both come off one read pointer; the audio plays continuously, the video tracks
    // it). Only when there is an audio sink; otherwise NanoVideo paces to the wall clock.
    if (mVideoSink) {
        // Gate on isPlaying() so scan/seek/pause (audio stopped) falls back to wall-clock pacing
        // instead of freezing the picture against a halted audio clock.
        if (mSink) { NanoAudioPlayer* a = mSink; mVideoSink->setClockFn([a]{ return (a->isPlaying() && a->clockArmed()) ? a->position() : -1.0; }); }
        else mVideoSink->setClockFn(nullptr);
    }
    mWorker = std::thread(&NanoTsDemux::workerFunc, this, startSec);
    return true;
}

void NanoTsDemux::stop() {
    mStop.store(true);
    if (mVideoSink) mVideoSink->setClockFn(nullptr);   // drop the audio-clock fn before audio is freed
    if (mVideoSink) mVideoSink->flushFed(0.0);   // unblock a feedVideo waiting on a full queue
    // Live: the worker may be blocked in NanoHls::readAt at the live frontier; unblock it (returns
    // EOS) before joining, or the join deadlocks. Only when a worker is actually running, so start()'s
    // pre-stop never kills a freshly-adopted source.
    if (mHls && mWorker.joinable()) mHls->requestStop();
    if (mWorker.joinable()) mWorker.join();
    mAudioPes.buf.clear(); mAudioPes.started = false;
    mVideoPes.buf.clear(); mVideoPes.started = false;
    mAc3Buf.clear(); mAacBuf.clear();
    mAc3.free();                  // release the liba52 state (no idle audio-decoder footprint when closed)
    mAac.free();                  // release the AAC AMediaCodec (nothing resident when closed)
    mAudioFedOpen = false;
    mLiveAudioReady.store(false);
    mCea608.reset();             // drop caption state + cues (nothing resident when closed)
    mCcReorder.clear();
    { std::lock_guard<std::mutex> lk(mCueMx); mCues.clear(); }
    mSink = nullptr;
    mVideoSink = nullptr;
}

void NanoTsDemux::selectAudio(int audioIndex) {
    if (audioIndex < 0 || audioIndex >= (int)mAudio.size()) return;
    mPendSelPid.store(mAudio[audioIndex].pid);
}

void NanoTsDemux::seek(double targetSec) {
    if (targetSec < 0) targetSec = 0;
    if (mVideoSink) mVideoSink->flushFed(targetSec);  // unblock a (possibly paused) feedVideo
    mPendSeek.store(targetSec);
}

void NanoTsDemux::setCea608(bool enable, int ccChannel) {
    mCcChannel.store(ccChannel);
    mCcEnable.store(enable);
    mCcGen.fetch_add(1);   // force the worker to (re)configure + reset the decoder cleanly
}

bool NanoTsDemux::hasCea608(int /*ccChannel*/) { return mCcSeen.load(); }

void NanoTsDemux::copyCea608Cues(std::vector<Cue>& out) {
    std::lock_guard<std::mutex> lk(mCueMx);
    out = mCues;
}

// ---- PES helpers ----

// Strip the PES header, returning a pointer/len into the ES payload + the PTS (us, -1
// if absent). Returns false if `pes` is not a valid PES start.
static bool pesPayload(const uint8_t* pes, size_t len, const uint8_t** es, size_t* esLen, int64_t* ptsUs) {
    if (len < 9 || pes[0] != 0 || pes[1] != 0 || pes[2] != 1) return false;
    int hdrLen = pes[8];
    int64_t pts = -1;
    if ((pes[7] & 0x80) && len >= (size_t)(9 + 5)) {           // PTS present
        const uint8_t* q = pes + 9;
        int64_t v = ((int64_t)(q[0] & 0x0e) << 29) |
                    ((int64_t)q[1] << 22) | ((int64_t)(q[2] & 0xfe) << 14) |
                    ((int64_t)q[3] << 7)  | ((int64_t)(q[4] >> 1));
        pts = v * 100 / 9;                                      // 90kHz -> us
    }
    size_t off = 9 + (size_t)hdrLen;
    if (off > len) return false;
    *es = pes + off; *esLen = len - off; *ptsUs = pts;
    return true;
}

// Open the audio fed ring lazily (live) once the decoder reports a real rate. No-op when the
// caller already opened it (recorded-.ts path: audioPreOpened=true). See NanoTsDemux.h.
void NanoTsDemux::ensureAudioFed(int rate, int channels) {
    if (mAudioFedOpen || !mSink || rate <= 0) return;
    if (mSink->openFed(rate, channels > 0 ? channels : 2)) {
        // Live start-together: hold the audio muted (draining at the live edge) until the picture's
        // first frame arms the shared origin. The host calls armOrigin() once the frame is decoded.
        mSink->setPrerollMute(true, /*drain=*/true);
        mSink->play();
        mAudioFedOpen = true;
        mLiveAudioReady.store(true);   // AFTER preroll+play so the host's arm/un-mute always follows
        TLOGI("NanoTsDemux: live audio fed ring opened %dHz x%d", rate, channels);
    }
}

void NanoTsDemux::emitAudioPes(const uint8_t* pes, size_t len, int64_t /*ptsUs*/) {
    const uint8_t* es = nullptr; size_t esLen = 0; int64_t pts = -1;
    if (!pesPayload(pes, len, &es, &esLen, &pts)) return;
    // Codec of the currently-selected audio PID (AC-3 via liba52, or AAC via AMediaCodec).
    int sel = mSelPid.load();
    int stype = 0x81;
    for (const auto& t : mAudio) if (t.pid == sel) { stype = t.streamType; break; }

    std::vector<int16_t> pcm;
    int rate = 0, chans = 2;
    if (stype == 0x0f) {                                   // AAC (ADTS) -> AMediaCodec
        mAacBuf.insert(mAacBuf.end(), es, es + esLen);
        int consumed = mAac.decode(mAacBuf.data(), (int)mAacBuf.size(), pcm, rate, chans);
        if (consumed > 0) mAacBuf.erase(mAacBuf.begin(), mAacBuf.begin() + consumed);
        if (rate > 0) mStreamRate = rate;
    } else {                                               // AC-3 -> liba52
        mAc3Buf.insert(mAc3Buf.end(), es, es + esLen);
        rate = mStreamRate;
        int consumed = mAc3.decode(mAc3Buf.data(), (int)mAc3Buf.size(), pcm, rate);
        if (consumed > 0) mAc3Buf.erase(mAc3Buf.begin(), mAc3Buf.begin() + consumed);
        if (rate > 0) mStreamRate = rate;
        chans = 2;
    }
    if (rate > 0) ensureAudioFed(rate, chans);
    if (!pcm.empty() && mSink && mAudioFedOpen) {
        size_t off = 0;
        while (off < pcm.size() && !mStop.load() && mPendSeek.load() < 0 && mPendSelPid.load() < 0) {
            size_t w = mSink->feedPcm(pcm.data() + off, pcm.size() - off);
            off += w;
            if (w == 0) usleep(2000);
        }
    }
}

void NanoTsDemux::flushAudioPes() {
    if (mAudioPes.started && !mAudioPes.buf.empty())
        emitAudioPes(mAudioPes.buf.data(), mAudioPes.buf.size(), -1);
    mAudioPes.buf.clear(); mAudioPes.started = false;
}

// Feed one reassembled video PES to the picture sink (the access unit is one coded
// MPEG-2 picture for broadcast TS) and, when captions are on, scan its user_data.
void NanoTsDemux::emitVideoPes(const uint8_t* pes, size_t len) {
    const uint8_t* es = nullptr; size_t esLen = 0; int64_t pts = -1;
    if (!pesPayload(pes, len, &es, &esLen, &pts)) return;
    // Normalize to the stream's PTS origin (broadcast captures start the PTS at an arbitrary
    // 90kHz value, e.g. ~19300s) so position() maps onto [0, duration] and the seek bar +
    // relative seeks + caption timestamps line up with the byte<->time model. The base is
    // captured once at first play and kept across seeks (later bytes carry later PTS).
    double ptsSec = -1.0;
    if (pts >= 0) {
        if (mPtsBaseUs < 0) mPtsBaseUs = pts;
        int64_t o = pts - mPtsBaseUs; if (o < 0) o = 0;   // PTS discontinuity before the base: clamp
        ptsSec = (double)o / 1e6;
    }
    // Captions: decode when a CC track is selected; otherwise a BOUNDED presence probe
    // (first ~300 pictures) so a non-captioned stream costs nothing after a couple seconds.
    if (mCcEnable.load()) {
        scanVideoUserData(es, esLen, ptsSec);
    } else if (!mCcSeen.load() && mCcProbe < 300) {
        scanVideoUserData(es, esLen, ptsSec);
        mCcProbe++;
    }
    if (mVideoSink && esLen) {
        // Start feeding the codec at a clean keyframe so a cold HW decoder gets a decodable first
        // access unit (mid-GOP pictures fault it). MPEG-1/2: a sequence header (00 00 01 B3).
        // H.264: an SPS (NAL 7) or IDR (NAL 5) - the codec is configured with the SPS/PPS csd, so
        // an IDR is decodable. HEVC: a VPS/SPS or IRAP NAL.
        if (!mVideoStartedFeed) {
            bool h264 = (mVideoStreamType == 0x1b);
            bool h265 = (mVideoStreamType == 0x24);
            if (h264 || h265) {
                bool ok = false;
                for (size_t i = 0; i + 4 <= esLen; i++) {
                    if (es[i] == 0 && es[i + 1] == 0 && es[i + 2] == 1) {
                        if (h264) { int t = es[i + 3] & 0x1f; if (t == 5 || t == 7) { ok = true; break; } }
                        else { int t = (es[i + 3] >> 1) & 0x3f;
                               if ((t >= 16 && t <= 23) || t == 32 || t == 33) { ok = true; break; } }
                    }
                }
                if (!ok) return;            // drop until the first keyframe
            } else {
                bool hasSeq = false; size_t seqAt = 0;
                for (size_t i = 0; i + 4 <= esLen; i++)
                    if (es[i] == 0 && es[i + 1] == 0 && es[i + 2] == 1 && es[i + 3] == 0xB3) { hasSeq = true; seqAt = i; break; }
                if (!hasSeq) return;            // drop pre-sequence-header pictures
                // MPEG-2 sequence header: aspect_ratio_information is the high nibble of the byte
                // after horizontal(12)+vertical(12). 2=DAR 4:3, 3=16:9, 4=2.21:1 (1/other = square
                // pixels). SD broadcast is usually 720x480/576 anamorphic; tell the sink its true
                // display shape so the picture is not stretched (the web XMB gets this from the
                // browser). Parse once per feed start (cheap; same value re-applied after a seek).
                if ((mVideoStreamType == 0x01 || mVideoStreamType == 0x02) && seqAt + 8 <= esLen) {
                    int arc = es[seqAt + 7] >> 4;
                    float dar = (arc == 2) ? (4.0f / 3.0f)
                              : (arc == 3) ? (16.0f / 9.0f)
                              : (arc == 4) ? 2.21f : 0.0f;
                    mVideoSink->setDisplayAspect(dar);
                }
            }
            mVideoStartedFeed = true;
        }
        int64_t outPts = (ptsSec >= 0.0) ? (int64_t)(ptsSec * 1e6) : pts;
        mVideoSink->feedVideo(es, esLen, outPts);  // blocks on back-pressure
    }
}

void NanoTsDemux::flushVideoPes() {
    if (mVideoPes.started && !mVideoPes.buf.empty())
        emitVideoPes(mVideoPes.buf.data(), mVideoPes.buf.size());
    mVideoPes.buf.clear(); mVideoPes.started = false;
}

void NanoTsDemux::handlePacket(const uint8_t* p) {
    if (p[0] != 0x47) return;
    int pid = ((p[1] & 0x1f) << 8) | p[2];
    int afc = (p[3] >> 4) & 3;
    if (afc == 0 || afc == 2) return;        // no payload
    int off = 4;
    if (afc == 3) off += 1 + p[4];
    if (off >= kPkt) return;
    bool pusi = (p[1] >> 6) & 1;
    const uint8_t* payload = p + off;
    int plen = kPkt - off;

    int selPid = mSelPid.load();
    if (pid == selPid) {
        if (pusi) { flushAudioPes(); mAudioPes.started = true; }
        if (mAudioPes.started) mAudioPes.buf.insert(mAudioPes.buf.end(), payload, payload + plen);
    } else if (pid == mVideoPid && (mVideoSink || mCcEnable.load() || (!mCcSeen.load() && mCcProbe < 300))) {
        if (pusi) { flushVideoPes(); mVideoPes.started = true; }   // emit the completed picture
        if (mVideoPes.started) mVideoPes.buf.insert(mVideoPes.buf.end(), payload, payload + plen);
    }
}

void NanoTsDemux::workerFunc(double startSec) {
    if (mSink) mSink->seekFed(startSec);
    off64_t pos = estimateByteForTime(startSec);
    mAudioPes.buf.clear(); mAudioPes.started = false;
    mVideoPes.buf.clear(); mVideoPes.started = false; mVideoStartedFeed = false;
    mAc3Buf.clear();
    mAc3.init();        // allocate the liba52 state (without this decode() no-ops -> silent audio)
    mAc3.reset();
    mCea608.reset(); mCcGenApplied = -1; mCcReorder.clear();   // captions decode fresh from this start position

    const size_t kChunk = (size_t)kPkt * 64;     // ~12 KB working set
    std::vector<uint8_t> buf(kChunk);
    int partial = 0;                             // bytes carried from a short read

    while (!mStop.load()) {
        // pending audio-track switch (O(1): flush + re-route, keep streaming).
        int ps = mPendSelPid.exchange(-1);
        if (ps >= 0) {
            flushAudioPes();
            mAc3Buf.clear(); mAc3.reset();
            mAacBuf.clear(); mAac.reset();
            mSelPid.store(ps);
        }

        // pending seek (reposition the single read pointer + flush BOTH sinks so A/V resume
        // together at the new position). Live has no byte<->time model and no past window, so
        // seeking is a no-op there (the UI disables it; this guard is belt-and-suspenders).
        double sk = mPendSeek.exchange(-1.0);
        if (sk >= 0 && !mLive) {
            pos = estimateByteForTime(sk);
            partial = 0;
            mAudioPes.buf.clear(); mAudioPes.started = false;
            mVideoPes.buf.clear(); mVideoPes.started = false; mVideoStartedFeed = false;
            mAc3Buf.clear(); mAc3.reset();
            mAacBuf.clear(); mAac.reset();
            mCea608.reset(); mCcReorder.clear(); { std::lock_guard<std::mutex> lk(mCueMx); mCues.clear(); }   // captions re-decode from here
            if (mSink) mSink->seekFed(sk);
            if (mVideoSink) mVideoSink->flushFed(sk);
            TLOGI("NanoTsDemux: seek %.2f -> byte %lld (bps=%.0f)", sk, (long long)pos, mBytesPerSec);
        }

        ssize_t got = readSrc(buf.data() + partial, kChunk - partial, pos);
        if (got <= 0) {                          // EOF: park (the picture may repeat or seek back)
            flushAudioPes();
            flushVideoPes();
            if (mVideoSink) mVideoSink->feedVideoEos();
            while (!mStop.load() && mPendSeek.load() < 0 && mPendSelPid.load() < 0) usleep(10000);
            continue;
        }
        pos += got;
        int total = partial + (int)got;
        // align to a sync byte if needed (first chunk after a seek).
        int start = 0;
        if (buf[0] != 0x47) { int a = findAlign(buf.data(), total); start = a < 0 ? total : a; }
        int i = start;
        for (; i + kPkt <= total; i += kPkt) handlePacket(buf.data() + i);
        // carry the trailing partial packet.
        partial = total - i;
        if (partial > 0 && partial < kPkt) memmove(buf.data(), buf.data() + i, partial);
        else partial = 0;
    }
}

// CEA-608 extraction from the MPEG-2 picture user_data (00 00 01 B2 'GA94' 0x03 cc_data).
// Records presence (mCcSeen) for the track UI; when a CC track is selected (mCcEnable) it
// routes the cc_data through NanoCea608 and publishes the cue snapshot under mCueMx. Worker only.
void NanoTsDemux::scanVideoUserData(const uint8_t* es, size_t len, double ptsSec) {
    for (size_t i = 0; i + 9 <= len; i++) {
        if (es[i] == 0 && es[i + 1] == 0 && es[i + 2] == 1 && es[i + 3] == 0xB2 &&
            es[i + 4] == 'G' && es[i + 5] == 'A' && es[i + 6] == '9' && es[i + 7] == '4' &&
            es[i + 8] == 0x03) {
            mCcSeen.store(true);
            if (mCcEnable.load()) {
                int gen = mCcGen.load();
                if (gen != mCcGenApplied) {
                    mCea608.setChannel(mCcChannel.load());   // (re)select channel + reset the decoder
                    mCcGenApplied = gen;
                    mCcReorder.clear();                      // drop pre-switch pictures
                    std::lock_guard<std::mutex> lk(mCueMx); mCues.clear();
                }
                // Buffer this picture's cc_data with its PTS; decode in display order below.
                // Copy only the cc_data span (header + cc_count*3 + trailing marker), not the
                // whole picture, so the reorder buffer stays tiny.
                const uint8_t* cc = es + i + 9;
                size_t avail = len - (i + 9);
                if (avail >= 2) {
                    int cnt = cc[0] & 0x1f;
                    size_t need = (size_t)(2 + cnt * 3 + 1);
                    if (cnt > 0 && need <= avail) {
                        mCcReorder.push_back({ ptsSec >= 0.0 ? ptsSec : 0.0,
                                               std::vector<uint8_t>(cc, cc + need) });
                        ccReorderFlush(kCcReorderWindow);
                    }
                }
            }
            return;
        }
    }
}

// Drain the cc_data reorder buffer down to `keep` entries, feeding the oldest-by-PTS pictures
// into the line-21 decoder so captions are processed in DISPLAY order. A window well above the
// MPEG-2 reorder depth guarantees the minimum-PTS entry left is final before it is emitted.
void NanoTsDemux::ccReorderFlush(size_t keep) {
    bool changed = false;
    while (mCcReorder.size() > keep) {
        size_t mi = 0;
        for (size_t k = 1; k < mCcReorder.size(); k++)
            if (mCcReorder[k].pts < mCcReorder[mi].pts) mi = k;
        CcUnit u = std::move(mCcReorder[mi]);
        mCcReorder.erase(mCcReorder.begin() + mi);
        if (mCea608.feedGa94(u.data.data(), u.data.size(), u.pts)) changed = true;
    }
    if (changed) {
        std::vector<NanoCea608::Cue> tmp;
        mCea608.copyCues(tmp);
        std::lock_guard<std::mutex> lk(mCueMx);
        mCues.clear();
        for (const auto& c : tmp) mCues.push_back({c.startSec, c.endSec, c.text});
    }
}

} // namespace android
