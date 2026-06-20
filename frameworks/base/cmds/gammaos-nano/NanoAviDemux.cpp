// NanoAviDemux - RIFF/AVI container parser (see NanoAviDemux.h). Android-free so it
// host-compiles for unit testing: define NANOAVI_TEST and build with g++ to run the
// self-check main against an .avi file.
#include "NanoAviDemux.h"

#include <algorithm>
#include <cctype>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>

namespace {
inline uint16_t rd16(const uint8_t* p) { return (uint16_t)(p[0] | (p[1] << 8)); }
inline uint32_t rd32(const uint8_t* p) {
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}
inline bool tag4(const uint8_t* p, const char* s) { return p[0]==s[0]&&p[1]==s[1]&&p[2]==s[2]&&p[3]==s[3]; }
// AVI chunk ids encode the stream number as two ASCII digits ('00','01',..,'0a'..).
inline int streamNumOf(const uint8_t* ck) {
    auto hex = [](uint8_t c)->int {
        if (c>='0'&&c<='9') return c-'0';
        if (c>='a'&&c<='f') return c-'a'+10;
        if (c>='A'&&c<='F') return c-'A'+10;
        return -1;
    };
    int hi = hex(ck[0]), lo = hex(ck[1]);
    if (hi < 0 || lo < 0) return -1;
    return hi*16 + lo;
}
} // namespace

bool NanoAviDemux::readAt(int64_t off, void* buf, size_t n) const {
    if (mFd < 0 || off < 0) return false;
    uint8_t* d = (uint8_t*)buf;
    size_t got = 0;
    while (got < n) {
        ssize_t r = pread(mFd, d + got, n - got, off + got);
        if (r <= 0) return false;
        got += (size_t) r;
    }
    return true;
}

bool NanoAviDemux::open(const std::string& path) {
    close();
    mFd = ::open(path.c_str(), O_RDONLY | O_CLOEXEC);
    if (mFd < 0) return false;
    off_t sz = lseek(mFd, 0, SEEK_END);
    if (sz <= 0) { close(); return false; }
    mFileSize = sz;

    uint8_t hdr[12];
    if (!readAt(0, hdr, 12) || !tag4(hdr, "RIFF") || !tag4(hdr + 8, "AVI ")) { close(); return false; }

    // Walk the top-level RIFF chunks.
    int64_t pos = 12;
    while (pos + 8 <= mFileSize) {
        uint8_t ch[12];
        if (!readAt(pos, ch, 8)) break;
        uint32_t csize = rd32(ch + 4);
        if (tag4(ch, "LIST")) {
            if (!readAt(pos + 8, ch + 8, 4)) break;
            if (tag4(ch + 8, "hdrl")) {
                parseHdrl(pos + 12, csize >= 4 ? csize - 4 : 0);
            } else if (tag4(ch + 8, "movi")) {
                mMoviPos = pos + 12;                 // payload after the 'movi' fourcc
                mMoviSize = csize >= 4 ? csize - 4 : 0;
            }
        } else if (tag4(ch, "idx1")) {
            parseIdx1(pos + 8, csize);
        }
        pos += 8 + (int64_t) csize + (csize & 1);    // chunks are word-aligned
    }

    if (!mVideo.present && !mAudio.present) { close(); return false; }

    // If idx1 was missing/unusable, scan the movi region directly.
    if (mSamples.empty()) scanMovi();
    if (mSamples.empty()) { close(); return false; }

    // Compute presentation times: video from fps, audio from the average byte rate
    // (accurate for CBR; the standard AVI approximation). Track per-stream running
    // sample index + audio cumulative bytes.
    int64_t vIdx = 0; int64_t aBytes = 0;
    for (auto& s : mSamples) {
        if (s.kind == STREAM_VIDEO) {
            s.frameIndex = vIdx;
            s.ptsSec = (mVideo.fps > 0.0) ? (double) vIdx / mVideo.fps : 0.0;
            vIdx++;
        } else if (s.kind == STREAM_AUDIO) {
            s.frameIndex = 0;
            s.ptsSec = (mAudio.avgBytesPerSec > 0) ? (double) aBytes / mAudio.avgBytesPerSec : 0.0;
            aBytes += s.size;
        }
    }

    // Duration: prefer the video timeline, else the audio.
    if (mVideo.present && mVideo.fps > 0.0 && vIdx > 0)
        mDurationSec = (double) vIdx / mVideo.fps;
    else if (mAudio.present && mAudio.avgBytesPerSec > 0 && aBytes > 0)
        mDurationSec = (double) aBytes / mAudio.avgBytesPerSec;
    return true;
}

void NanoAviDemux::close() {
    stop();
    if (mFd >= 0) ::close(mFd);
    mFd = -1; mFileSize = 0; mMoviPos = 0; mMoviSize = 0;
    mHadIdx1 = false; mIdxOffsetsAbsolute = false; mStreamCount = 0;
    mVideo = VideoInfo(); mAudio = AudioInfo(); mDurationSec = 0.0;
    mSamples.clear();
    for (int i = 0; i < 64; i++) mStreamKind[i] = STREAM_NONE;
}

bool NanoAviDemux::parseHdrl(int64_t pos, uint32_t size) {
    int64_t end = pos + size;
    int streamIndex = 0;
    while (pos + 8 <= end) {
        uint8_t ch[12];
        if (!readAt(pos, ch, 8)) break;
        uint32_t csize = rd32(ch + 4);
        if (tag4(ch, "avih")) {
            uint8_t a[56];
            size_t n = std::min<uint32_t>(csize, sizeof(a));
            if (readAt(pos + 8, a, n) && n >= 40) {
                mStreamCount = (int) rd32(a + 24);     // dwStreams
                if (!mVideo.width)  mVideo.width  = (int) rd32(a + 32);  // dwWidth (fallback)
                if (!mVideo.height) mVideo.height = (int) rd32(a + 36);  // dwHeight
            }
        } else if (tag4(ch, "LIST")) {
            if (readAt(pos + 8, ch + 8, 4) && tag4(ch + 8, "strl")) {
                parseStrl(pos + 12, csize >= 4 ? csize - 4 : 0, streamIndex);
                streamIndex++;
            }
        }
        pos += 8 + (int64_t) csize + (csize & 1);
    }
    return true;
}

bool NanoAviDemux::parseStrl(int64_t pos, uint32_t size, int streamIndex) {
    int64_t end = pos + size;
    StreamKind kind = STREAM_NONE;
    char fccType[5] = {0}, fccHandler[5] = {0};
    uint32_t dwScale = 1, dwRate = 0; int64_t dwLength = 0;
    while (pos + 8 <= end) {
        uint8_t ch[8];
        if (!readAt(pos, ch, 8)) break;
        uint32_t csize = rd32(ch + 4);
        if (tag4(ch, "strh")) {
            uint8_t h[56];
            size_t n = std::min<uint32_t>(csize, sizeof(h));
            if (readAt(pos + 8, h, n) && n >= 40) {
                memcpy(fccType, h, 4);
                memcpy(fccHandler, h + 4, 4);
                dwScale = rd32(h + 20);
                dwRate  = rd32(h + 24);
                dwLength = (int64_t) rd32(h + 32);
                if (!memcmp(fccType, "vids", 4)) kind = STREAM_VIDEO;
                else if (!memcmp(fccType, "auds", 4)) kind = STREAM_AUDIO;
            }
        } else if (tag4(ch, "strf")) {
            std::vector<uint8_t> f(csize);
            if (csize && readAt(pos + 8, f.data(), csize)) {
                if (kind == STREAM_VIDEO && csize >= 40) {       // BITMAPINFOHEADER
                    mVideo.present = true;
                    mVideo.streamIndex = streamIndex;
                    mVideo.width  = (int) rd32(&f[4]);
                    mVideo.height = (int) rd32(&f[8]);
                    char comp[5] = {0}; memcpy(comp, &f[16], 4);
                    // Prefer biCompression; fall back to the strh handler (some muxers
                    // leave biCompression zero and put the codec in fccHandler).
                    const char* src = (comp[0] || comp[1] || comp[2] || comp[3]) ? comp : fccHandler;
                    for (int i = 0; i < 4 && src[i]; i++) mVideo.fourcc[i] = (char) tolower((unsigned char) src[i]);
                    if (csize > 40) mVideo.csd.assign(f.begin() + 40, f.end());   // VOL extradata
                    mVideo.fps = (dwScale > 0) ? (double) dwRate / dwScale : 0.0;
                    mVideo.frames = dwLength;
                } else if (kind == STREAM_AUDIO && csize >= 16) {  // WAVEFORMATEX
                    mAudio.present = true;
                    mAudio.streamIndex = streamIndex;
                    mAudio.formatTag    = rd16(&f[0]);
                    mAudio.channels     = rd16(&f[2]);
                    mAudio.sampleRate   = (int) rd32(&f[4]);
                    mAudio.avgBytesPerSec = (int) rd32(&f[8]);
                    mAudio.blockAlign   = rd16(&f[12]);
                    mAudio.bitsPerSample= rd16(&f[14]);
                    if (csize >= 18) {
                        int cb = rd16(&f[16]);
                        if (cb > 0 && (size_t)(18 + cb) <= csize)
                            mAudio.csd.assign(f.begin() + 18, f.begin() + 18 + cb);
                    }
                }
            }
        }
        pos += 8 + (int64_t) csize + (csize & 1);
    }
    if (streamIndex >= 0 && streamIndex < 64) mStreamKind[streamIndex] = kind;
    return true;
}

bool NanoAviDemux::parseIdx1(int64_t pos, uint32_t size) {
    uint32_t n = size / 16;
    if (!n) return false;
    std::vector<uint8_t> buf(size);
    if (!readAt(pos, buf.data(), size)) return false;

    // idx1 chunk offsets are classically relative to the 'movi' fourcc (i.e. mMoviPos-4)
    // but some muxers write absolute file offsets. Detect by checking the first entry's
    // ckid against the bytes at each candidate base.
    auto probe = [&](int64_t base)->bool {
        uint32_t off = rd32(&buf[8]);
        uint8_t got[4];
        if (!readAt(base + (int64_t) off, got, 4)) return false;
        return tag4(got, (const char*)&buf[0]);
    };
    int64_t relBase = mMoviPos - 4;       // chunk header sits at relBase + offset
    int64_t base;
    if (probe(relBase))      { base = relBase; mIdxOffsetsAbsolute = false; }
    else if (probe(0))       { base = 0;       mIdxOffsetsAbsolute = true; }
    else return false;                    // index unusable -> caller falls back to scan

    mSamples.reserve(n);
    for (uint32_t i = 0; i < n; i++) {
        const uint8_t* e = &buf[i * 16];
        int snum = streamNumOf(e);
        StreamKind kind = (snum >= 0 && snum < 64) ? mStreamKind[snum] : STREAM_NONE;
        if (kind == STREAM_NONE) {
            // map by suffix as a fallback
            if (e[2]=='d' && (e[3]=='c'||e[3]=='b')) kind = STREAM_VIDEO;
            else if (e[2]=='w' && e[3]=='b')         kind = STREAM_AUDIO;
            else continue;                // skip 'rec ', 'ix..', palette, etc
        }
        uint32_t flags = rd32(e + 4);
        uint32_t coff  = rd32(e + 8);     // offset of the chunk HEADER (relative to base)
        uint32_t clen  = rd32(e + 12);
        Sample s;
        s.kind = kind;
        s.streamIndex = snum;
        s.offset = base + (int64_t) coff + 8;   // payload = header + 8 (ckid+size)
        s.size = clen;
        s.keyframe = (flags & 0x10) != 0;        // AVIIF_KEYFRAME
        if (s.offset + (int64_t) s.size <= mFileSize && s.size > 0)
            mSamples.push_back(s);
    }
    mHadIdx1 = !mSamples.empty();
    return mHadIdx1;
}

bool NanoAviDemux::scanMovi() {
    if (!mMoviPos || !mMoviSize) return false;
    int64_t pos = mMoviPos;
    int64_t end = std::min<int64_t>(mMoviPos + mMoviSize, mFileSize);
    while (pos + 8 <= end) {
        uint8_t ch[12];
        if (!readAt(pos, ch, 8)) break;
        uint32_t csize = rd32(ch + 4);
        if (tag4(ch, "LIST")) {                   // 'rec ' grouping: descend
            pos += 12;
            continue;
        }
        int snum = streamNumOf(ch);
        StreamKind kind = (snum >= 0 && snum < 64) ? mStreamKind[snum] : STREAM_NONE;
        if (kind == STREAM_NONE) {
            if (ch[2]=='d' && (ch[3]=='c'||ch[3]=='b')) kind = STREAM_VIDEO;
            else if (ch[2]=='w' && ch[3]=='b')          kind = STREAM_AUDIO;
        }
        if (kind != STREAM_NONE && csize > 0 && pos + 8 + (int64_t) csize <= mFileSize) {
            Sample s;
            s.kind = kind; s.streamIndex = snum;
            s.offset = pos + 8; s.size = csize;
            s.keyframe = (kind == STREAM_VIDEO);   // unknown without idx1; assume keyframe
            mSamples.push_back(s);
        }
        pos += 8 + (int64_t) csize + (csize & 1);
    }
    return !mSamples.empty();
}

bool NanoAviDemux::readSample(int i, std::vector<uint8_t>& out) {
    if (i < 0 || i >= (int) mSamples.size()) return false;
    const Sample& s = mSamples[i];
    out.resize(s.size);
    return s.size == 0 || readAt(s.offset, out.data(), s.size);
}

int NanoAviDemux::seekSampleForTime(double targetSec) const {
    int best = 0;
    for (int i = 0; i < (int) mSamples.size(); i++) {
        const Sample& s = mSamples[i];
        if (s.kind != STREAM_VIDEO || !s.keyframe) continue;
        if (s.ptsSec <= targetSec) best = i; else break;
    }
    return best;
}

const char* NanoAviDemux::videoMime() const {
    if (!mVideo.present) return nullptr;
    const char* f = mVideo.fourcc;
    auto is = [&](const char* s){ return f[0]==s[0]&&f[1]==s[1]&&f[2]==s[2]&&f[3]==s[3]; };
    // MPEG-4 ASP family -> the standard MPEG-4 mime (Allwinner mpeg4/divx/xvid HW decoders).
    if (is("xvid")||is("divx")||is("dx50")||is("dx40")||is("mp4v")||is("fmp4")||is("3iv2")||
        is("m4s2")||is("mp4s")||is("blz0")||is("xvix")||is("div5")||is("divf")||is("rmp4")||
        is("sedg")||is("geox")||is("mvxm"))
        return "video/mp4v-es";
    // Motion-JPEG (mjpg/jpeg/dmb1) is intentionally left unsupported: this SoC has no runtime
    // MJPEG video MediaCodec. The vendor media_codecs.xml lists OMX.allwinner.video.decoder.mjpeg
    // (mime "video/jpeg") but it is not registered at runtime (createDecoderByType and
    // createCodecByName both fail), so we return nullptr and the caller falls back cleanly
    // rather than spinning up a decoder that cannot exist.
    return nullptr;    // unsupported (mjpeg: no HW codec; div3/MS-MPEG4v3; h264-in-avi rare) -> caller falls back
}

bool NanoAviDemux::videoCsd(std::vector<uint8_t>& out) {
    out.clear();
    if (mVideo.csd.size() >= 4) { out = mVideo.csd; return true; }
    // ffmpeg-muxed AVIs carry the MPEG-4 VOL in the first frame, not strf. Extract the
    // headers before the first VOP start code (00 00 01 B6) as csd-0.
    for (int i = 0; i < (int) mSamples.size(); i++) {
        if (mSamples[i].kind != STREAM_VIDEO) continue;
        std::vector<uint8_t> f;
        if (!readSample(i, f) || f.size() < 8) return false;
        for (size_t p = 0; p + 4 <= f.size(); p++) {
            if (f[p]==0 && f[p+1]==0 && f[p+2]==1 && f[p+3]==0xB6) {   // VOP start
                if (p > 0) { out.assign(f.begin(), f.begin() + p); return true; }
                return false;        // VOP first, no leading VOL
            }
        }
        return false;                // no VOP marker found in the first frame
    }
    return false;
}

// stop()/seekWorker() are NanoVideo-free so they compile in the host test too; start()
// + workerFunc() reference NanoVideo and are excluded from the host build.
void NanoAviDemux::stop() {
    mStop = true;
    if (mWorker.joinable()) mWorker.join();
    mVideoSink = nullptr;
    mStop = false;
}
void NanoAviDemux::seekWorker(double sec) { mPendSeek.store(sec); }

#ifndef NANOAVI_TEST
#include "NanoVideo.h"
bool NanoAviDemux::start(NanoVideo* video, double startSec) {
    if (!isOpen() || !video) return false;
    stop();
    mStop = false; mPendSeek = -1.0; mVideoSink = video;
    mWorker = std::thread([this, startSec]{ workerFunc(startSec); });
    return true;
}
void NanoAviDemux::workerFunc(double startSec) {
    int i = (startSec > 0.05) ? seekSampleForTime(startSec) : 0;
    std::vector<uint8_t> buf;
    while (!mStop.load()) {
        double sk = mPendSeek.exchange(-1.0);
        if (sk >= 0.0) {
            i = seekSampleForTime(sk);
            if (mVideoSink) mVideoSink->flushFed(sk);
        }
        if (i >= (int) mSamples.size()) { if (mVideoSink) mVideoSink->feedVideoEos(); break; }
        const Sample& s = mSamples[i];
        if (s.kind == STREAM_VIDEO) {
            if (readSample(i, buf) && !buf.empty()) {
                // Blocks while the sink's bounded queue is full (back-pressure); false = teardown.
                if (!mVideoSink->feedVideo(buf.data(), buf.size(), (int64_t)(s.ptsSec * 1e6))) break;
            }
        }
        i++;
    }
}
#endif

#ifdef NANOAVI_TEST
#include <cstdio>
int main(int argc, char** argv) {
    if (argc < 2) { printf("usage: %s file.avi\n", argv[0]); return 2; }
    NanoAviDemux d;
    if (!d.open(argv[1])) { printf("FAIL: not a parseable AVI\n"); return 1; }
    const auto& v = d.video(); const auto& a = d.audio();
    printf("AVI OK  duration=%.3fs  samples=%d  idx1=%s\n",
           d.durationSec(), d.sampleCount(), d.indexFromIdx1() ? "yes" : "scanned");
    if (v.present)
        printf("  VIDEO stream#%d  fourcc=%s  %dx%d  fps=%.3f  frames=%lld  csd=%zuB\n",
               v.streamIndex, v.fourcc, v.width, v.height, v.fps, (long long)v.frames, v.csd.size());
    if (a.present)
        printf("  AUDIO stream#%d  fmt=0x%04x  ch=%d  rate=%d  bits=%d  avgBps=%d  csd=%zuB\n",
               a.streamIndex, a.formatTag, a.channels, a.sampleRate, a.bitsPerSample,
               a.avgBytesPerSec, a.csd.size());
    int vc = 0, ac = 0; double lastV = 0, lastA = 0;
    std::vector<uint8_t> buf; bool ioOk = true;
    for (int i = 0; i < d.sampleCount(); i++) {
        const auto& s = d.sample(i);
        if (s.kind == NanoAviDemux::STREAM_VIDEO) { vc++; lastV = s.ptsSec; }
        else if (s.kind == NanoAviDemux::STREAM_AUDIO) { ac++; lastA = s.ptsSec; }
        if (i < 3 || i == d.sampleCount() - 1)
            printf("  [%d] %s str#%d off=%lld size=%u key=%d pts=%.3f\n", i,
                   s.kind == NanoAviDemux::STREAM_VIDEO ? "V" : "A", s.streamIndex,
                   (long long)s.offset, s.size, s.keyframe, s.ptsSec);
        if (!d.readSample(i, buf)) ioOk = false;
    }
    printf("  videoChunks=%d (lastPts=%.3f)  audioChunks=%d (lastPts=%.3f)  readAll=%s\n",
           vc, lastV, ac, lastA, ioOk ? "ok" : "IO-ERROR");
    int sk = d.seekSampleForTime(d.durationSec() * 0.5);
    printf("  seek(%.2fs) -> sample %d pts=%.3f key=%d\n",
           d.durationSec() * 0.5, sk, d.sample(sk).ptsSec, d.sample(sk).keyframe);
    return ioOk ? 0 : 1;
}
#endif
