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
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include <gui/GLConsumer.h>
#include <gui/Surface.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaFormat.h>
#include <utils/StrongPointer.h>

class NanoVideo {
public:
    NanoVideo() = default;
    ~NanoVideo() { release(); }

    // Lightweight metadata probe (extractor only, no codec/decode) for the library:
    // duration (s), pixel size, and the video codec short name (e.g. "AVC"/"HEVC").
    struct Meta { double durationSec = 0.0; int width = 0; int height = 0; std::string vcodec; std::string acodec; };
    static bool probe(const std::string& path, Meta& out);

    // Open + start decoding (render thread, EGL context current). false = failed.
    bool open(const std::string& path);
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

    // Render thread: latch the most-recently decoded frame into the OES texture.
    // Returns true if a new frame became current this call.
    bool updateFrame();
    GLuint texId() const { return mTexId; }                 // GL_TEXTURE_EXTERNAL_OES
    void getTransform(float m[16]);                          // UV transform from the decoder
    int width() const { return mWidth; }
    int height() const { return mHeight; }

    // Render thread: draw the current frame into the given screen-space rect (device px),
    // aspect-fit inside it, applying the decoder UV transform. fitMode: 0 = fit (letterbox),
    // 1 = fill (crop), 2 = stretch. alpha multiplies the output.
    void draw(int screenW, int screenH, float rx, float ry, float rw, float rh,
              float alpha, int fitMode = 0);

private:
    void decodeLoop();              // worker thread
    bool ensureProgram();           // lazily compile the samplerExternalOES program

    bool mOpen = false;
    std::atomic<bool> mPlaying{false};
    std::atomic<bool> mEnded{false};
    std::atomic<bool> mQuit{false};

    AMediaExtractor* mEx = nullptr;
    AMediaCodec* mCodec = nullptr;
    int mVideoTrack = -1;
    int mWidth = 0, mHeight = 0;
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

    // Seek request handed to the worker.
    std::atomic<bool> mSeekPending{false};
    std::atomic<double> mSeekTarget{0.0};
};
