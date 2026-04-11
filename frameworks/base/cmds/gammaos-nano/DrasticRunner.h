/*
 * Copyright (C) 2026 GammaOS
 *
 * In-process drastic quick resume runner for NanoMenu. Mirrors the
 * LibretroRunner pattern: dlopen the native library directly from
 * the DE cache, set up a fake JNIEnv to satisfy drastic's filesystem
 * callbacks, drive its lifecycle (onInit -> applyConfig -> startGame
 * -> fxSetup), and render frames via renderFrame into NanoMenu's
 * current EGL context.
 *
 * Phase 3 scope: boot drastic to a running state (init through
 * startGame). Rendering + input loop is layered on in Phase 4+.
 */

#pragma once

#include <string>
#include <thread>
#include <atomic>
#include <mutex>
#include <vector>
#include <dlfcn.h>
#include <utils/Log.h>

namespace android {

class DrasticRunner {
public:
    DrasticRunner();
    ~DrasticRunner();

    // Initialize: dlopen the libs from cacheDir, set up FakeJNI,
    // call JNI_OnLoad + onInit(null, versionCode, sdkInt), apply a
    // default-safe config, and startGame(romPath).
    //
    // cacheDir is the drastic cache root (e.g.
    // /data/system/nano_cache/drastic). romPath is the absolute
    // path to the .nds file inside that cache.
    //
    // Returns true if all the above completed without crashing and
    // drastic's main DS CPU thread is live.
    bool init(const std::string& cacheDir,
              const std::string& romPath);

    // Tear down. pauseSystem + quitSystem + dlclose.
    void shutdown();

    bool isInitialized() const { return mInitialized; }

    // Phase 4: GL surface bring-up. Must be called on the thread that
    // owns the EGL context (NanoMenu render thread). Creates our own
    // quad shader + two DS screen textures. Subsequent calls are
    // no-ops.
    void initSurface(int viewportW, int viewportH);

    // Pull the latest DS framebuffer pixels from drastic and upload
    // to the top/bottom GL textures. Should be called ONCE per frame,
    // BEFORE any draw calls, on the EGL-context thread. After this,
    // renderTopScreen/renderBottomScreen can be called as many times
    // as needed in the same frame (e.g. once per display pass).
    void updatePixels();

    // Draw the top DS screen filling the current viewport. Applies
    // saturation (0 = grayscale, 1 = full color) and gradient
    // (0 = none, 1 = strong dark gradient at bottom) just like
    // LibretroRunner's QR overlay shader.
    void renderTopScreen(float saturation, float gradient);

    // Draw the bottom DS screen filling the current viewport.
    void renderBottomScreen(float saturation, float gradient);

    // Push the DRM rotation matrix (same 2x2 that NanoMenu uses for
    // its own shaders). Mirrors LibretroRunner::setRotationMatrix.
    // Must be called before each render pass if the rotation changes.
    void setRotationMatrix(const float mat[4]) {
        for (int i = 0; i < 4; i++) mRotationMatrix[i] = mat[i];
    }

    // Singleton accessor. Stored as a file-scope pointer inside the
    // .cpp; set in init(), never cleared. NanoMenu's render loop uses
    // this to call initSurface/renderOneFrame without the smoke test
    // having to plumb an instance pointer through.
    static DrasticRunner* getInstance();

private:
    // Resolve a JNI export symbol from the arm64 handle with logging.
    // Defined inline here because member templates need the definition
    // at the point of instantiation.
    template <typename T>
    bool loadSym(T& out, const char* name) {
        out = (T)dlsym(mArm64Handle, name);
        if (!out) {
            ALOGE("DrasticRunner: dlsym(%s) failed: %s", name, dlerror());
            return false;
        }
        return true;
    }

    void* mCpuHandle = nullptr;
    void* mArm64Handle = nullptr;
    bool  mInitialized = false;

    // startGame is drastic's emulator main loop -- it does NOT return.
    // We launch it on a dedicated thread so our caller (NanoMenu main)
    // can continue with its own render loop. The thread is detached;
    // drastic's worker threads keep running until process teardown.
    std::thread mStartGameThread;
    std::atomic<bool> mStartGameLaunched{false};

    // Background pixel-pull thread. drastic's getScreenBuffers blocks
    // internally until the DS CPU thread has produced a frame -- the
    // first frame after startGame can take 15+ seconds on ROMs with
    // a slow BIOS/boot sequence (Pokemon BW2). To keep the render
    // loop responsive we pull frames on a background thread into a
    // mutex-protected shadow buffer pair; the render thread just
    // uploads the shadow to GL textures (non-blocking).
    std::thread mPixelPullThread;
    std::atomic<bool> mPixelPullRunning{false};
    std::vector<int> mTopShadow;
    std::vector<int> mBotShadow;
    std::mutex mShadowMutex;
    std::atomic<bool> mShadowReady{false};
    std::atomic<int> mFrameCounter{0};

    // JNI entry points resolved from libdrastic_arm64.so. Types match
    // the Java method signatures in DraSticJNI.smali translated to
    // their JNI C calling convention (JNIEnv*, jclass, ...args).
    typedef void (*onInit_t)(void* env, void* cls, void* ctx, int versionCode, int sdkInt);
    typedef void (*applyConfig_t)(void* env, void* cls, long configBits);
    typedef unsigned char (*startGame_t)(void* env, void* cls,
                                         void* romPathJString,
                                         int arg2, long arg3, int arg4,
                                         unsigned char arg5, long arg6);
    typedef void (*pauseSystem_t)(void* env, void* cls, int flag);
    typedef void (*quitSystem_t)(void* env, void* cls);
    typedef void (*setFirmwareUserdata_t)(void* env, void* cls,
                                          void* nickJStr, int colorArgb);
    typedef void (*setAutosaveInterval_t)(void* env, void* cls,
                                          int intervalSeconds);
    typedef void (*setAudioVolume_t)(void* env, void* cls, int vol);

    // Phase 4 GL entry points.
    typedef void (*fxSetup_t)(void* env, void* cls,
                              int texW, int texH, int a, int b,
                              int viewW, int viewH);
    // renderFrame(II Z) -- p, q, L. (Option A legacy, unused with
    // getScreenBuffers approach.)
    typedef void (*renderFrame_t)(void* env, void* cls, int p, int q,
                                  unsigned char flag);
    typedef void (*signalScreen_t)(void* env, void* cls);
    typedef void (*waitScreen_t)(void* env, void* cls);
    typedef int  (*updateFrame_t)(void* env, void* cls, int a, int b, int c);
    typedef void (*updateInput_t)(void* env, void* cls, int bitmask,
                                  int touchY, int touchX);
    // getScreenBuffers(int[] topDst, int[] botDst) -- writes the DS
    // framebuffer pixels into two caller-provided int arrays. Each
    // array holds 256*192 = 49152 jints (RGBA8888, one pixel per int).
    typedef void (*getScreenBuffers_t)(void* env, void* cls,
                                       void* topArr, void* botArr);

    onInit_t            mOnInit = nullptr;
    applyConfig_t       mApplyConfig = nullptr;
    startGame_t         mStartGame = nullptr;
    pauseSystem_t       mPauseSystem = nullptr;
    quitSystem_t        mQuitSystem = nullptr;
    setFirmwareUserdata_t mSetFirmwareUserdata = nullptr;
    setAutosaveInterval_t mSetAutosaveInterval = nullptr;
    setAudioVolume_t     mSetAudioVolume = nullptr;
    fxSetup_t           mFxSetup = nullptr;
    renderFrame_t       mRenderFrame = nullptr;
    signalScreen_t      mSignalScreen = nullptr;
    waitScreen_t        mWaitScreen = nullptr;
    updateFrame_t       mUpdateFrame = nullptr;
    updateInput_t       mUpdateInput = nullptr;
    getScreenBuffers_t  mGetScreenBuffers = nullptr;

    // Option B rendering state (NanoMenu-thread owned).
    void* mTopArr = nullptr;   // jintArray of 256*192 pixels
    void* mBotArr = nullptr;
    unsigned int mTopTex = 0;  // GLuint
    unsigned int mBotTex = 0;
    unsigned int mQuadProgram = 0;
    int  mQuadPosLoc = -1;
    int  mQuadTexLoc = -1;
    int  mQuadSamplerLoc = -1;
    int  mQuadSatLoc = -1;
    int  mQuadGradLoc = -1;
    int  mQuadRotLoc = -1;
    unsigned int mQuadVbo = 0;

    // 2x2 NDC rotation matrix (column-major, identity by default).
    // Mirrors NanoMenu's sDrmRotMat so we can render the DS quads in
    // the same panel-native orientation as the rest of the UI.
    float mRotationMatrix[4] = {1.0f, 0.0f, 0.0f, 1.0f};

    // Internal: draw one DS screen quad (fullscreen, letterboxed to
    // preserve the 256:192 = 4:3 aspect ratio against the current
    // viewport). `tex` is the top or bottom texture; sat/grad are
    // the overlay uniforms.
    void drawFullscreenDsQuad(unsigned int tex, float saturation, float gradient);

    // Cached fake env / cls for render-thread calls. Set during init()
    // and reused from initSurface / renderOneFrame.
    void* mFakeEnv = nullptr;
    void* mFakeCls = nullptr;

    // initSurface state.
    bool mSurfaceReady = false;
    int  mViewportW = 0;
    int  mViewportH = 0;
};

} // namespace android
