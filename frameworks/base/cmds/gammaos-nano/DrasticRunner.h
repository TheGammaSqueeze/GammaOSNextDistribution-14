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
#include <chrono>
#include <mutex>
#include <vector>
#include <dlfcn.h>
#include <utils/Log.h>

namespace android {

class DrasticRunner {
public:
    DrasticRunner();
    ~DrasticRunner();

    // Initialize: dlopen the libs from libsDir (falls back to cacheDir
    // when libsDir is empty), set up FakeJNI rooted at cacheDir, call
    // JNI_OnLoad + onInit(null, versionCode, sdkInt), apply a config
    // (either the built-in default or configBitsOverride when non-zero),
    // and startGame(romPath).
    //
    // cacheDir is the drastic virtual-FS root for FakeJNI's DraStic/ and
    // User/ prefix resolution (e.g. /data/system/nano_cache/drastic).
    // libsDir, if non-empty, points to the directory containing
    // libdrastic_arm64.so / libdrastic_cpu.so. romPath is absolute.
    // When soundEnabled is true, the _SoundEnabled bit (31) is forced on.
    // configBitsOverride, when non-zero, replaces the compiled-in default
    // applyConfig bitmask (so drastic-nano can thread user-XML-derived
    // settings in). The _SoundEnabled bit is still forced by soundEnabled.
    // autosaveIntervalSeconds, when non-zero, is passed to setAutosaveInterval
    // (0 = keep drastic's default).
    // initialShader, when non-empty, is the basename of the .dfx to load
    // during initSurface (e.g. "Linear", "Scanline"). Empty = "Linear".
    //
    // Returns true if all the above completed without crashing and
    // drastic's main DS CPU thread is live.
    bool init(const std::string& cacheDir,
              const std::string& romPath,
              const std::string& libsDir = std::string(),
              bool soundEnabled = false,
              long configBitsOverride = 0,
              int autosaveIntervalSeconds = 0,
              const std::string& initialShader = std::string());

    // Tear down. pauseSystem + quitSystem + dlclose.
    void shutdown();

    bool isInitialized() const { return mInitialized; }

    // Phase 4: GL surface bring-up. Must be called on the thread that
    // owns the EGL context (NanoMenu render thread). Sets up drastic's
    // GL pipeline via fxSetup for the renderFrame path, creates an
    // offscreen FBO for portrait-mode dual-screen compositing, and
    // compiles the blit shader for display output. Subsequent calls
    // are no-ops. dualDisplay=true creates a portrait FBO (WxH*2)
    // for split-screen output to two displays.
    void initSurface(int viewportW, int viewportH, bool dualDisplay);

    // Render both DS screens into the offscreen FBO via drastic's
    // renderFrame. Must be called ONCE per frame, BEFORE any
    // renderTopScreen/renderBottomScreen calls. Replaces the old
    // updatePixels() + getScreenBuffers path with drastic's native
    // GL compositing, which includes hi-res 3D and all rendering
    // layers.
    void renderDsToOffscreen();

    // Legacy pixel-pull path (getScreenBuffers). Still functional
    // but missing some rendering layers. Use renderDsToOffscreen()
    // for full-fidelity output.
    void updatePixels();

    // Draw the top DS screen filling the current viewport. Blits
    // from the top half of the offscreen FBO (if renderDsToOffscreen
    // was called) with saturation/gradient overlay.
    void renderTopScreen(float saturation, float gradient);

    // Draw the bottom DS screen filling the current viewport.
    void renderBottomScreen(float saturation, float gradient);

    // Draw BOTH DS screens (stacked portrait) into the current
    // viewport. For single-display devices where both screens need
    // to appear on one panel.
    void renderBothScreens(float saturation, float gradient);

    // Push the DRM rotation matrix (same 2x2 that NanoMenu uses for
    // its own shaders). Mirrors LibretroRunner::setRotationMatrix.
    // Must be called before each render pass if the rotation changes.
    void setRotationMatrix(const float mat[4]) {
        for (int i = 0; i < 4; i++) mRotationMatrix[i] = mat[i];
    }

    // Inject gamepad button state into drastic's running core so the
    // user can play the game during the QR preview window. The bitmask
    // uses drastic's internal bit order (NOT the DS hardware KEYINPUT
    // order), active-high. Build the mask with the kDsBtn* constants
    // below — never hardcode hex values.
    //
    // The render thread should call this every frame with the latest
    // accumulated state (the running DS CPU reads from master+0x48c
    // on its own thread — see nano_drastic_qr.md Phase 7 notes).
    //
    // No-op if drastic isn't initialized or updateInput failed to load.
    void setInput(int bitmask);

    // Like setInput but also forwards touch state. touchX/touchY are
    // in DS screen coordinates (0..255 x, 0..191 y). touchHeld is the
    // "finger down" flag drastic uses to gate the touch state consumer.
    void setInputWithTouch(int bitmask, int touchX, int touchY,
                           bool touchHeld);

    // Park drastic's worker threads without tearing down the loaded
    // libraries. Matches shutdown()'s pauseSystem call but skips the
    // destructive quitSystem step, so the state is recoverable if we
    // later want to resume. Used when QR is cancelled by the user and
    // we fall through to the NanoMenu XMB instead of handing off.
    void pauseDrastic();

    // Toggle drastic's internal pause flag. Idempotent. When paused,
    // drastic's DS CPU thread stops advancing; renderDsToOffscreen is
    // still safe to call (it reads the latched last frame).
    // Used by the overlay menu to freeze gameplay while the user
    // navigates.
    void pauseToggle(bool pause);

    // Mid-session save / load. slot is 0..8 (9 is drastic's reserved
    // autosave slot -- callers must not pass 9). Writes to / reads
    // from <cacheDir>/savestates/<rom_basename>_<slot>.dss. On the
    // live drastic-nano data path cacheDir points at the real
    // /data/user/0/com.dsemu.drastic/files/DraStic so the real app
    // picks up our saves on its next launch. Returns true if the
    // underlying JNI call was invoked (not whether drastic actually
    // produced a valid state -- drastic has no ABI for that).
    bool saveStateSlot(int slot);
    bool loadStateSlot(int slot);

    // Reset the emulated DS (power cycle). Equivalent to the "Reset"
    // menu action in the real drastic app. Safe to call at any time
    // once init() has succeeded.
    void resetSystem();

    // Live audio volume. Range is drastic's internal 0..100; the
    // overlay UI 0..10 must multiply by 10 before calling.
    void setVolumeRuntime(int vol0to100);

    // Toggle drastic's runtime fast-forward lever (applyConfig bit 29
    // = 0x20000000 = V, per drastic-android-mod disasm at libdrastic
    // +0x1b26c). Removes the per-frame wait cap inside drastic's
    // internal frame pacer so the DS CPU runs as fast as the emulator
    // can grind. Idempotent: callable every frame with no penalty
    // (drastic's applyConfig converter short-circuits unchanged state
    // internally). No-op until drastic is initialized.
    void setFastForward(bool on);

    // Swap the active video filter (.dfx). absDfxPath must point at
    // a readable .dfx file. Pauses drastic briefly, calls fxLoad +
    // fxSetup with the stored tex dimensions, re-captures the drastic
    // program ID, resumes. Must be called on the render thread
    // (the thread that owns the EGL context). Returns false on any
    // failure (file unreadable, symbols missing, render not yet live).
    bool setShaderRuntime(const std::string& absDfxPath);

    // Query the tex dims used when fxSetup was last invoked. The
    // overlay uses this to keep shader swaps consistent.
    void getFxTexDims(int* outW, int* outH) const {
        if (outW) *outW = mFxTexW;
        if (outH) *outH = mFxTexH;
    }

    // Drastic button bitmask — NOT the DS hardware KEYINPUT order.
    // Source: n0/i.smali `R:[I` array cross-checked with the updateInput
    // disasm at 0x1a5d8 by the drastic-android-mod session. See
    // nano-startup-timing-answers.md Phase 7 for the dialogue.
    //
    // Polarity: active-high (1 = pressed). Drastic inverts internally
    // before servicing the ARM9's REG_KEYINPUT MMIO read.
    //
    // Bits 12..30 are reserved / trap doors (screen-swap, fast forward,
    // quick save/load, radial menu, microphone, etc.) and must NEVER be
    // set directly from the nano side — they route through Java
    // interface callbacks that our fake-JNI layer does not implement.
    //
    // Bit 31 is drastic's pointer-down indicator (touchscreen tap). The
    // setInputWithTouch() method packages that bit with the touchHeld
    // flag. Callers to setInput() stay out of bit 31 entirely.
    static constexpr int kDsBtnUp     = 1 << 0;   // 0x001
    static constexpr int kDsBtnDown   = 1 << 1;   // 0x002
    static constexpr int kDsBtnLeft   = 1 << 2;   // 0x004
    static constexpr int kDsBtnRight  = 1 << 3;   // 0x008
    static constexpr int kDsBtnA      = 1 << 4;   // 0x010
    static constexpr int kDsBtnB      = 1 << 5;   // 0x020
    static constexpr int kDsBtnX      = 1 << 6;   // 0x040
    static constexpr int kDsBtnY      = 1 << 7;   // 0x080
    static constexpr int kDsBtnL      = 1 << 8;   // 0x100
    static constexpr int kDsBtnR      = 1 << 9;   // 0x200
    static constexpr int kDsBtnStart  = 1 << 10;  // 0x400
    static constexpr int kDsBtnSelect = 1 << 11;  // 0x800

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
    // Load base of libdrastic_arm64.so (from dladdr on JNI_OnLoad). Used to
    // read the GL program ID out of drastic's BSS at base + 0x3f2dbe0 after
    // fxSetup (fxSetup's worker always ends with glUseProgram(0), so
    // glGetIntegerv(GL_CURRENT_PROGRAM) is useless for capturing it).
    uint8_t* mArm64Base = nullptr;
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
    typedef int  (*saveState_t)(void* env, void* cls, int slot);
    typedef int  (*loadState_t)(void* env, void* cls, int slot);
    typedef void (*resetDS_t)  (void* env, void* cls);

    // Phase 4 GL entry points.
    typedef int  (*fxLoad_t)(void* env, void* cls,
                             void* shaderPathJStr, int arg2, int arg3);
    typedef void (*fxSetup_t)(void* env, void* cls,
                              int texW, int texH, int a, int b,
                              int viewW, int viewH);
    // renderFrame(II Z) -- p, q, L. (Option A legacy, unused with
    // getScreenBuffers approach.)
    typedef void (*renderFrame_t)(void* env, void* cls, int p, int q,
                                  unsigned char flag);
    // fxRender(I I I I I I I I I Z) — the shader-enabled composite
    // render call. Drastic's own render code at
    // DraSticGlView$j.smali:1989-2098 flips to this signature when the
    // K (no-shader) flag is false. Unlike renderFrame, fxRender walks
    // the .dfx pass list and binds the per-pass GL programs, producing
    // the actual shader output.
    //
    // Stock signature (per drastic-android-mod disasm at 0x1d1d8):
    //   topTex   — GL tex id for the DS top screen
    //   botTex   — GL tex id for the DS bottom screen (or 0 for single-screen)
    //   k0,k6,k18 — fixed constants drastic uses for source-rect metadata
    //              (these MUST be 0, 6, 18 respectively -- not runtime geometry)
    //   outX,outY,outW,outH — destination rect in viewport coords
    //   isBottom — stock passes 0 (JNI_FALSE) for the both-screens mode
    typedef void (*fxRender_t)(void* env, void* cls,
                               int topTex, int botTex,
                               int k0, int k6, int k18,
                               int outX, int outY, int outW, int outH,
                               unsigned char isBottom);
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
    saveState_t          mSaveState = nullptr;
    loadState_t          mLoadState = nullptr;
    resetDS_t            mResetDS = nullptr;
    fxLoad_t            mFxLoad = nullptr;
    fxSetup_t           mFxSetup = nullptr;
    renderFrame_t       mRenderFrame = nullptr;
    fxRender_t          mFxRender = nullptr;
    signalScreen_t      mSignalScreen = nullptr;
    waitScreen_t        mWaitScreen = nullptr;
    updateFrame_t       mUpdateFrame = nullptr;
    updateInput_t       mUpdateInput = nullptr;
    getScreenBuffers_t  mGetScreenBuffers = nullptr;

    // Legacy getScreenBuffers pixel arrays (kept for fallback).
    void* mTopArr = nullptr;   // jintArray of 256*192 pixels
    void* mBotArr = nullptr;

    // Offscreen FBO for renderFrame compositing. drastic renders
    // both screens (portrait stacked) into this FBO, then we blit
    // the top/bottom halves to the respective display FBOs.
    unsigned int mOffscreenFbo = 0;
    unsigned int mOffscreenTex = 0;
    int  mOffscreenW = 0;
    int  mOffscreenH = 0;
    bool mDualDisplay = false;
    bool mUseRenderFrame = false;

    // Textures that drastic's renderFrame uploads DS framebuffer
    // data into (via glTexSubImage2D inside renderFrame).
    unsigned int mDsTopTex = 0;
    unsigned int mDsBotTex = 0;

    // Legacy getScreenBuffers textures (for fallback path).
    unsigned int mTopTex = 0;  // GLuint
    unsigned int mBotTex = 0;

    // Drastic's GL program ID (set by fxSetup, captured before we
    // compile our own shaders). Must be rebound before renderFrame.
    unsigned int mDrasticGlProgram = 0;

    // Blit shader: samples the offscreen texture (or legacy DS
    // textures) and applies saturation/gradient overlay.
    unsigned int mQuadProgram = 0;
    int  mQuadPosLoc = -1;
    int  mQuadTexLoc = -1;
    int  mQuadSamplerLoc = -1;
    int  mQuadSatLoc = -1;
    int  mQuadGradLoc = -1;
    int  mQuadRotLoc = -1;
    int  mQuadUvRectLoc = -1;
    unsigned int mQuadVbo = 0;

    // VBO bound before fxRender. Contains two DS-screen quads (top
    // and bottom) as GL_TRIANGLE_STRIPs of 6 vertices each. Layout:
    //   [0   .. 95 ] = 12x vec2 NDC positions (verts 0..5 = top quad
    //                   at NDC y[-1, 0], verts 6..11 = bot quad at
    //                   NDC y[0, 1]).
    //   [96  .. 191] = 12x vec2 UV coords, full [0,1] per quad,
    //                   Y-flipped so BL = (0,1) in GL texture space.
    // Passed to fxLoad as pos_ptr=0, uv_ptr=96 so the pass runner's
    // glVertexAttribPointer() reads from this VBO (not host memory).
    unsigned int mFxVbo = 0;

    // 2x2 NDC rotation matrix (column-major, identity by default).
    float mRotationMatrix[4] = {1.0f, 0.0f, 0.0f, 1.0f};

    // Internal: draw a quad from a source texture region into the
    // current viewport with saturation/gradient. vMin/vMax control
    // which vertical slice of the source texture is sampled.
    void drawDsQuad(unsigned int tex, float vMin, float vMax,
                    float saturation, float gradient);

    // After fxLoad builds the .dfx pass list in drastic's fx_ctx,
    // walk to the final pass and overwrite its pass.fbo field
    // (struct offset +344) with mOffscreenFbo. This redirects
    // fxRender's visible output from FBO 0 (the EGL surface) into
    // our offscreen FBO, which the renderTop/Bottom/Both paths then
    // sample halves from.
    //
    // Without this patch, fxRender unbinds whatever FBO we bind
    // externally and writes to the pass list's hard-coded target
    // (which is 0 for stock Linear / 2xPrescaleFast shaders). See
    // drastic-quickresume-dialog.md 2026-04-18 01:35 for the
    // disasm evidence and the derivation of the +0xd208 offset.
    void patchFinalPassFbo();

    // Inverse of patchFinalPassFbo: walk the current pass list and
    // reset the final pass.fbo field back to 0. Called before a
    // runtime shader swap so fxLoad's internal teardown cannot delete
    // our mOffscreenFbo via glDeleteFramebuffers on the patched value.
    // Without this, switching shaders in the overlay menu leaves
    // mOffscreenFbo as a stale GL name, and subsequent renders bind
    // an invalid FBO so the displays stop updating.
    void unpatchFinalPassFbo();

    // Cached fake env / cls for render-thread calls. Set during init()
    // and reused from initSurface / renderOneFrame.
    void* mFakeEnv = nullptr;
    void* mFakeCls = nullptr;

    // Cached cacheDir from init(). Used by initSurface to build the
    // absolute shader path for fxLoad: drastic-nano (cacheDir = real
    // drastic files dir) has shaders at <cacheDir>/shaders/, while
    // nano_cache has them at <cacheDir>/system/shaders/.
    std::string mCacheDir;

    // initSurface state.
    bool mSurfaceReady = false;
    int  mViewportW = 0;
    int  mViewportH = 0;

    // Tex dims passed to fxSetup. Preserved so a runtime shader swap
    // (setShaderRuntime) can re-invoke fxSetup with the same geometry.
    int mFxTexW = 0;
    int mFxTexH = 0;

    // Initial shader basename (no ext, no path). Cached from init()
    // so initSurface can build the absolute path. Defaults to "Linear".
    std::string mInitialShader;

    // Track current drastic pause state for idempotent pauseToggle.
    bool mPaused = false;

    // Config bitmask we last handed to applyConfig during init. Used
    // as the base for runtime toggles like fast-forward (OR in bit 29
    // when held, clear it when released). Starts at 0 so the first
    // setFastForward call before init becomes a no-op.
    long mBaseConfigBits = 0;
    // Cached fast-forward state so we don't hammer applyConfig every
    // frame when the user just holds the button.
    bool mFastForwardOn = false;

    // Shader-swap throttle / re-entrance guard. The overlay menu's Shader
    // row calls setShaderRuntime on every navLeft/Right, and drastic's
    // fxLoad does a full teardown+reparse+reallocate each call. Rapid
    // switches (under ~500 ms apart) eventually corrupt scudo heap
    // metadata inside fxLoad -- the 4th consecutive swap SIGABRTs with
    // "invalid chunk state when deallocating". A minimum-interval gate
    // plus a re-entrance atomic prevents the user from driving fxLoad
    // faster than it can settle.
    std::chrono::steady_clock::time_point mLastShaderSwap;
    std::atomic<bool> mShaderSwapInFlight{false};
    // Monotonic swap counter. Logged on every setShaderRuntime call so
    // we can correlate the "crashes after N swaps" mode with the exact
    // fx_ctx state at the moment of failure.
    int mSwapCounter = 0;

    // Dump fx_ctx + pass list to logcat with the given tag. Safe to
    // call before or after fxLoad; walks the list defensively with a
    // cap so a corrupt next pointer cannot loop forever.
    void dumpFxCtxState(const char* when);
};

} // namespace android
