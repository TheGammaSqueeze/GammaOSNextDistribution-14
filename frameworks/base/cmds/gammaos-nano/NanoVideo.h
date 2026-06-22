// NanoVideo - hardware video decode to a GL_TEXTURE_EXTERNAL_OES texture for the
// PS3 XMB video player. AMediaExtractor + AMediaCodec (NDK) decode on a worker
// thread and render into a Surface backed by a GLConsumer; the render thread
// latches the newest frame with updateFrame() and samples it via samplerExternalOES.
//
// Lazy by construction: nothing is allocated until open(); release() tears the whole
// pipeline down (decoder, codec, BufferQueue, GL texture, worker thread) so an idle
// player holds no video resources. open()/updateFrame()/release() must run on the
// render thread (the one that owns the EGL context); the worker thread touches only
// the codec/extractor, never GL.
#pragma once

#include <GLES2/gl2.h>
#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <deque>
#include <functional>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include <gui/GLConsumer.h>
#include <gui/Surface.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaDataSource.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaFormat.h>
#include <utils/StrongPointer.h>

namespace android { class NanoHls; }   // IPTV HTTP/HLS in-process fetcher (openUrl)

class NanoVideo {
public:
    NanoVideo() = default;
    ~NanoVideo() { release(); }

    // Lightweight metadata probe (extractor only, no codec/decode) for the library:
    // duration (s), pixel size, and the video codec short name (e.g. "AVC"/"HEVC").
    struct Meta { double durationSec = 0.0; int width = 0; int height = 0; std::string vcodec; std::string acodec; };
    static bool probe(const std::string& path, Meta& out);
    // Probe a network URL (http/https, incl. HLS .m3u8). Same fields as probe(); duration
    // is 0 for live streams. Opens an extractor on the URL (one network connection), reads
    // the track formats, closes. Blocks while the manifest/first segment is fetched.
    static bool probeUrl(const std::string& url, Meta& out);

    // Open + start decoding (render thread, EGL context current). false = failed.
    // Thin SYNCHRONOUS wrapper kept only for the in-worker .ts/.avi fed-fail fallback and
    // probing; the render-thread player now uses the openBegin + openAsyncRun split below.
    bool open(const std::string& path);
    // Open + start decoding from a network URL (http/https, incl. HLS .m3u8). The platform
    // extractor fetches the manifest/segments transparently. Live streams report duration 0
    // (no seek). Render thread, EGL current. false = failed.
    bool openUrl(const std::string& url);

    // ---- Async open split (no UI freeze on warmup) ----
    // The blocking open work (network setDataSource/NanoHls, extractor build, codec
    // create/configure/start) cannot run on the render thread - it would freeze the UI and
    // trip the render watchdog. open() is therefore split into:
    //   openBegin(wHint,hHint)  - RENDER THREAD, EGL current: GL allocation ONLY (texture +
    //                             BufferQueue + GLConsumer + Surface), sized by hint.
    //   openAsyncRun*(...)      - WORKER THREAD: all the blocking work, reusing the GL from
    //                             openBegin, polling mCancel between steps; spawns the decode
    //                             worker only on full success.
    // openBegin must run first; the worker then re-sizes the GLConsumer (an IGraphicBuffer
    // IPC, not GL) to the real dimensions before the decoder dequeues. On worker failure the
    // owner frees the (partial) object on the render thread via releaseAsync()/finishRelease().
    bool openBegin(int wHint, int hHint);
    bool openAsyncRun(const std::string& path);
    bool openAsyncRunUrl(const std::string& url);
    bool openAsyncRunFed(const std::string& mime, int width, int height, AMediaFormat* srcFmt = nullptr);
    // Render-thread cancel lever for an in-flight openAsyncRun*: sets mCancel (checked between
    // blocking steps) and unblocks an in-flight NanoHls network read. Race-free against the
    // worker publishing mHls (atomic acquire/release).
    void requestOpenCancel();
    bool openCanceled() const { return mCancel.load(std::memory_order_relaxed); }

    // "Fed" mode: the picture is decoded from access units pushed by an external demuxer
    // (NanoTsDemux) instead of NanoVideo's own AMediaExtractor. This is how a .ts plays:
    // ONE in-process demuxer feeds both this video codec and the audio decoder from a
    // single read pointer, so A/V stay in lockstep (no second extractor, no system
    // MPEG2TSExtractor, no byte-position guesswork). openFed sets up the codec + GL output
    // by mime (e.g. "video/mpeg2") with a size hint (corrected by FORMAT_CHANGED); the
    // worker pulls input from feedVideo(). Render thread, EGL current.
    // srcFmt (optional): the real track format from an AMediaExtractor (csd, colour aspects,
    // etc). Configuring the HW decoder with the full format - exactly as the extractor-driven
    // open() path does - makes the Allwinner MPEG-2 decoder cold-start reliably; a bare
    // mime+size format intermittently wedges it. Ownership stays with the caller.
    bool openFed(const std::string& mime, int width, int height, AMediaFormat* srcFmt = nullptr);
    // Push one coded picture (elementary-stream access unit) + its PTS (us). Blocks while
    // the bounded input queue is full (back-pressure paces the demuxer); returns false if
    // the player is tearing down. Call from the demuxer worker.
    bool feedVideo(const uint8_t* es, size_t len, int64_t ptsUs);
    void feedVideoEos();            // no more access units are coming
    void flushFed(double newBaseSec); // drop queued input + flush the codec + re-anchor (seek)
    bool isFed() const { return mFed; }

    // Audio-master pacing: when set, the decode worker holds each frame until this clock
    // (the audio playback position, s) reaches the frame PTS, so the picture tracks the audio
    // (the demuxer feeds both off one read pointer; the audio plays continuously and the video
    // follows it). Cleared (nullptr) to fall back to wall-clock-by-PTS pacing. Set by the demuxer.
    void setClockFn(std::function<double()> fn);

    void release();                 // full SYNC teardown (idempotent; render thread; may block)
    // Async teardown: AMediaCodec_stop() can block for seconds on this OMX decoder,
    // which would freeze the render thread and trip the render watchdog. releaseAsync()
    // stops+joins the decode worker (fast) and hands the blocking codec/extractor
    // teardown to a detached thread; the render thread polls releaseAsyncDone() and
    // calls finishRelease() (GL cleanup, needs the EGL context) once it completes.
    void releaseAsync();
    bool releaseAsyncDone() const { return !mAsyncReleasing || mAsyncDone.load(); }
    void finishRelease();
    bool isOpen() const { return mOpen; }

    // Transport.
    void play();
    void pause();
    bool isPlaying() const { return mPlaying.load(); }
    void seek(double sec);          // best-effort seek (worker re-syncs)
    double position() const;        // current playback time (s)
    double duration() const { return mDurationSec; }
    bool ended() const { return mEnded.load(); }

    // Step 1 A/V start-together: the host holds audio muted until the picture has decoded its
    // first frame, then un-mutes audio anchored to firstFramePts() so both start together in one
    // PTS domain (fixes the live raw-TS-vs-0-based catch-up). The video decode/pace path itself
    // is unchanged (it renders from frame 0 as before); these just observe the first frame.
    bool firstFrameReady() const { return mFirstFrameReady.load(); }
    double firstFramePts() const; // PTS (s) of the first decoded frame (the shared origin)

    // Render thread: latch the most-recently decoded frame into the OES texture.
    // Returns true if a new frame became current this call.
    bool updateFrame();
    GLuint texId() const { return mTexId; }                 // GL_TEXTURE_EXTERNAL_OES
    void getTransform(float m[16]);                          // UV transform from the decoder
    int width() const { return mWidth; }
    int height() const { return mHeight; }

    // Set the intended display aspect ratio (DAR = display width / height) for anamorphic
    // content (e.g. SD broadcast MPEG-2 with non-square pixels). The browser the web XMB
    // draws applies the sample aspect ratio automatically; we replicate that so the frame
    // is shown at its true shape, not the coded pixel grid. <= 0 means "use the coded
    // ratio" (square pixels), so square-pixel content is drawn exactly as before.
    void setDisplayAspect(float dar) { mDisplayAspect.store(dar > 0.0f ? dar : 0.0f); }

    // Render thread: draw the current frame into the given screen-space rect (device px),
    // aspect-fit inside it, applying the decoder UV transform. fitMode: 0 = fit (letterbox),
    // 1 = fill (crop), 2 = stretch. alpha multiplies the output.
    void draw(int screenW, int screenH, float rx, float ry, float rw, float rh,
              float alpha, int fitMode = 0);

private:
    void decodeLoop();              // worker thread
    bool ensureProgram();           // lazily compile the samplerExternalOES program
    // Non-GL soft reset of the codec/extractor/fed state on the SAME object (keeps the GL
    // texture/Consumer/Surface from openBegin), for the in-worker fed->normal fallback.
    // Joins the decode worker if one is live (worker thread only - never the render thread).
    void resetForReopen();

    bool mOpen = false;
    std::atomic<bool> mPlaying{false};
    std::atomic<bool> mEnded{false};
    std::atomic<bool> mQuit{false};
    std::atomic<bool> mCancel{false};   // cancel an in-flight openAsyncRun* (separate from mQuit)

    AMediaExtractor* mEx = nullptr;
    AMediaCodec* mCodec = nullptr;
    // MPEG-TS descramble path: HDHomeRun / ATSC captures carry a CA_descriptor in the
    // PMT even when the payload is clear, so Android's extractor reports the tracks as
    // "...scrambled" and they will not decode. For those files we feed the extractor a
    // custom data source that rewrites the CA descriptor tag out of the PMT on the fly
    // (recomputing the section CRC), so the stream is seen as clear and the MPEG-2 video
    // decodes on the HW path. nullptr for normal files (which use setDataSourceFd).
    AMediaDataSource* mDataSource = nullptr;
    void* mTsPatch = nullptr;        // TsPatch userdata (dup'd fd + PMT pid) for mDataSource
    void freeTsSource();             // delete the data source + free its userdata (after mEx)
    // IPTV: in-process HTTP/HLS fetcher backing the custom data source for openUrl(). Owns
    // the streaming temp file; freed after mEx in release()/releaseAsync (the extractor read
    // through its data source). null for local files.
    // Atomic so requestOpenCancel() (render thread) can read it race-free while the open
    // worker constructs+publishes it (release store after full construction, acquire load here).
    std::atomic<android::NanoHls*> mHls{nullptr};
    void freeHls();
    int mVideoTrack = -1;
    int mWidth = 0, mHeight = 0;
    std::atomic<float> mDisplayAspect{0.0f};   // intended DAR; 0 = use the coded pixel ratio
    double mDurationSec = 0.0;

    // GL output path (libgui): BufferQueue -> GLConsumer(OES tex) + Surface(producer).
    GLuint mTexId = 0;
    android::sp<android::GLConsumer> mConsumer;
    android::sp<android::Surface> mSurface;

    // samplerExternalOES draw program (lazy).
    GLuint mProg = 0;
    GLint mLocPos = -1, mLocTex = -1, mLocST = -1, mLocAlpha = -1;

    std::thread mWorker;

    // Async teardown (releaseAsync/finishRelease): the detached thread that runs the
    // blocking AMediaCodec_stop/delete off the render thread.
    std::thread mReleaseThread;
    bool mAsyncReleasing = false;
    std::atomic<bool> mAsyncDone{false};

    // A/V clock: wall-clock anchor for the first rendered PTS so frames pace to real time.
    mutable std::mutex mClockMx;
    double mClockBasePts = 0.0;     // PTS (s) at the anchor
    int64_t mClockBaseNs = 0;       // CLOCK_MONOTONIC ns at the anchor
    std::atomic<double> mPosSec{0.0};
    std::function<double()> mClockFn;  // audio-master clock (guarded by mClockMx); null = wall-clock

    // Step 1 start-together: set true on the first decoded (render-eligible) frame; mFirstFramePts
    // captures that frame's PTS so the host anchors the audio clock to the same origin.
    std::atomic<bool> mFirstFrameReady{false};
    double mFirstFramePts = 0.0;       // guarded by mClockMx

    // Seek request handed to the worker.
    std::atomic<bool> mSeekPending{false};
    std::atomic<double> mSeekTarget{0.0};

    // ---- fed mode (demuxer-driven .ts video) ----
    bool mFed = false;
    std::string mFedMime;                     // codec mime (for recreate-on-fault)
    int mFedRecreate = 0;                      // recreate attempts (HW retry, then SW fallback)
    AMediaCodec* createFedDecoder(bool forceSw); // pick SW (MPEG-2) or HW (AVC/HEVC) decoder by mime
    bool recreateFedCodec();                   // rebuild a faulted codec; false when out of options
    // Extractor-path (mp4/mov/live) counterpart: the HW decoder (notably 1080p AVC) intermittently
    // cold-starts into a faulted or no-output state on this path too; rebuild on the SAME extractor
    // and re-seek to the current position so playback recovers instead of buffering forever.
    int mExtractorRecreate = 0;
    bool recreateExtractorCodec();
    struct FedAu { std::vector<uint8_t> es; int64_t ptsUs; };
    std::deque<FedAu> mFedQ;                  // bounded input queue (demuxer -> worker)
    std::mutex mFedMx;
    std::condition_variable mFedCv;           // worker waits for input / space
    std::atomic<bool> mFedEos{false};
    std::atomic<bool> mFedFlush{false};       // worker flushes the codec + queue on seek
    std::atomic<double> mFedFlushBase{0.0};
    static constexpr size_t kFedQMax = 24;    // ~0.8s of pictures; back-pressures the demuxer
    bool popFedAu(FedAu& out, int timeoutMs); // worker: take next AU (false on timeout/quit)
};
