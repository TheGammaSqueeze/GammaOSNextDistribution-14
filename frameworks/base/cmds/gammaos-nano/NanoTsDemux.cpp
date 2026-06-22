// NanoTsDemux - single-pass in-process MPEG-TS demuxer. See NanoTsDemux.h.
#include "NanoTsDemux.h"

#include "NanoAudio.h"
#include "NanoVideo.h"

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

    // PAT -> first program's PMT pid.
    mPmtPid = -1;
    for (ssize_t i = mAlign; i + kPkt <= n && mPmtPid < 0; i += kPkt) {
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
    if (mPmtPid < 0) { ::close(fd); return false; }

    // PMT -> PCR pid, video pid, audio tracks (with language).
    mAudio.clear(); mVideoPid = -1; mPcrPid = -1;
    for (ssize_t i = mAlign; i + kPkt <= n; i += kPkt) {
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
            while (d + 2 <= dEnd) {                            // ES descriptors
                int dt = p[d], dl = p[d + 1];
                if (dt == 0x0a && dl >= 3)                     // ISO_639_language
                    lang.assign((const char*)&p[d + 2], 3);
                d += 2 + dl;
            }
            bool isVideo = (stype == 0x01 || stype == 0x02 || stype == 0x1b || stype == 0x24);
            bool isAc3   = (stype == 0x81);                    // ATSC AC-3
            if (isVideo && mVideoPid < 0) { mVideoPid = epid; mVideoStreamType = stype; }
            if (isAc3) { AudioTrack t; t.pid = epid; t.streamType = stype; t.lang = lang; mAudio.push_back(t); }
            es = dEnd;
        }
        break;
    }
    if (mVideoPid < 0 && mAudio.empty()) { ::close(fd); return false; }

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

void NanoTsDemux::close() {
    stop();
    if (mFd >= 0) { ::close(mFd); mFd = -1; }
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

bool NanoTsDemux::start(NanoVideo* video, NanoAudioPlayer* audio, int audioIndex, double startSec) {
    if (mFd < 0) return false;
    stop();
    mVideoSink = video;
    mSink = audio;
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
    if (mWorker.joinable()) mWorker.join();
    mAudioPes.buf.clear(); mAudioPes.started = false;
    mVideoPes.buf.clear(); mVideoPes.started = false;
    mAc3Buf.clear();
    mAc3.free();                  // release the liba52 state (no idle audio-decoder footprint when closed)
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

void NanoTsDemux::emitAudioPes(const uint8_t* pes, size_t len, int64_t /*ptsUs*/) {
    const uint8_t* es = nullptr; size_t esLen = 0; int64_t pts = -1;
    if (!pesPayload(pes, len, &es, &esLen, &pts)) return;
    // accumulate ES bytes and decode whole AC-3 frames.
    mAc3Buf.insert(mAc3Buf.end(), es, es + esLen);
    std::vector<int16_t> pcm;
    int rate = mStreamRate;
    int consumed = mAc3.decode(mAc3Buf.data(), (int)mAc3Buf.size(), pcm, rate);
    if (consumed > 0) mAc3Buf.erase(mAc3Buf.begin(), mAc3Buf.begin() + consumed);
    if (rate > 0) mStreamRate = rate;
    if (!pcm.empty() && mSink) {
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
        // Start feeding the codec at a sequence header (00 00 01 B3) so a cold MPEG-2
        // decoder gets a clean, decodable first access unit (mid-GOP pictures can fault it).
        if (!mVideoStartedFeed) {
            bool hasSeq = false; size_t seqAt = 0;
            for (size_t i = 0; i + 4 <= esLen; i++)
                if (es[i] == 0 && es[i + 1] == 0 && es[i + 2] == 1 && es[i + 3] == 0xB3) { hasSeq = true; seqAt = i; break; }
            if (!hasSeq) return;            // drop pre-sequence-header pictures
            // MPEG-2 sequence header: aspect_ratio_information is the high nibble of the byte
            // after horizontal(12)+vertical(12). 2=DAR 4:3, 3=16:9, 4=2.21:1 (1/other = square
            // pixels). SD broadcast is usually 720x480/576 anamorphic; tell the sink its true
            // display shape so the picture is not stretched (the web XMB gets this from the
            // browser). Parse once per feed start (cheap; same value re-applied after a seek).
            // Only for MPEG-1/2 (0xB3 is their sequence-header start code) so a stray 00 00 01 B3
            // byte run inside an AVC/HEVC stream cannot set a bogus DAR.
            if ((mVideoStreamType == 0x01 || mVideoStreamType == 0x02) && seqAt + 8 <= esLen) {
                int arc = es[seqAt + 7] >> 4;
                float dar = (arc == 2) ? (4.0f / 3.0f)
                          : (arc == 3) ? (16.0f / 9.0f)
                          : (arc == 4) ? 2.21f : 0.0f;
                mVideoSink->setDisplayAspect(dar);
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
        if (ps >= 0) { flushAudioPes(); mAc3Buf.clear(); mAc3.reset(); mSelPid.store(ps); }

        // pending seek (reposition the single read pointer + flush BOTH sinks so A/V
        // resume together at the new position).
        double sk = mPendSeek.exchange(-1.0);
        if (sk >= 0) {
            pos = estimateByteForTime(sk);
            partial = 0;
            mAudioPes.buf.clear(); mAudioPes.started = false;
            mVideoPes.buf.clear(); mVideoPes.started = false; mVideoStartedFeed = false;
            mAc3Buf.clear(); mAc3.reset();
            mCea608.reset(); mCcReorder.clear(); { std::lock_guard<std::mutex> lk(mCueMx); mCues.clear(); }   // captions re-decode from here
            if (mSink) mSink->seekFed(sk);
            if (mVideoSink) mVideoSink->flushFed(sk);
            TLOGI("NanoTsDemux: seek %.2f -> byte %lld (bps=%.0f)", sk, (long long)pos, mBytesPerSec);
        }

        ssize_t got = pread64(mFd, buf.data() + partial, kChunk - partial, pos);
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
