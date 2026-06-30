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
#include <condition_variable>
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
    // autoLoadSlot is the save-state slot drastic's startGame loads on
    // boot: >= 0 loads that slot (the drastic-android-mod auto-resume
    // uses slot 9, its autosave slot), < 0 boots fresh. Default 0
    // preserves the legacy callers.
    bool init(const std::string& cacheDir,
              const std::string& romPath,
              const std::string& libsDir = std::string(),
              bool soundEnabled = false,
              long configBitsOverride = 0,
              int autosaveIntervalSeconds = 0,
              const std::string& initialShader = std::string(),
              int autoLoadSlot = 0);

    // Tear down. pauseSystem + quitSystem + dlclose.
    void shutdown();

    bool isInitialized() const { return mInitialized; }

    // True once drastic's emulation core has produced at least one
    // frame (the shadow framebuffer is live). Used by drastic-nano to
    // defer a launch-time auto-load of a save state until the core is
    // actually running.
    bool isFrameReady() const { return mShadowReady.load(); }

    // Monotonic count of emulated frames the core has actually produced (the
    // pixel-pull handshake increments it once per DS frame). NOTE: this counter
    // only advances while the legacy pixel-pull thread runs; on the current
    // renderDsToOffscreen() path that thread is stopped so this stays frozen.
    // The RetroAchievements integration therefore does NOT drive do_frame off
    // this; it ticks once per render-loop vblank via NanoRetroAchievements::
    // onRenderFrame() (~60Hz, the DS rate).
    int emulatedFrames() const { return mFrameCounter.load(); }
    // Block until the emulated-frame count moves past lastCount (a new frame was
    // produced) or timeoutMs elapses; returns the current count. Lets a consumer
    // wake promptly per frame instead of polling.
    int waitForFrameAfter(int lastCount, int timeoutMs);

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

    // Render ONE DS screen through the loaded .dfx shader at the exact slot
    // size, straight into targetFbo at GL viewport (vx,vy,vw,vh). The shader
    // pass list is (re)sized to (vw,vh) so prescale/LCD grids land on the
    // final on-screen pixels -- the way stock DraStic renders each screen at
    // its real size -- instead of shading a fixed shared offscreen that the
    // layout path then re-samples (which blurs the grid and breaks asymmetric
    // big+small slots). which: 0 = top screen, 1 = bottom screen. Returns
    // false when the shader path is inactive; the caller then falls back to a
    // re-sampled blit (renderTopScreen / renderBottomScreen).
    bool renderSlotShaded(int which, unsigned int targetFbo,
                          int vx, int vy, int vw, int vh);

    // Push the DRM rotation matrix (same 2x2 that NanoMenu uses for
    // its own shaders). Mirrors LibretroRunner::setRotationMatrix.
    // Must be called before each render pass if the rotation changes.
    void setRotationMatrix(const float mat[4]) {
        for (int i = 0; i < 4; i++) mRotationMatrix[i] = mat[i];
    }

    // Composite an arbitrary RGBA texture across the whole current viewport,
    // transformed by the given 2x2 NDC matrix. Used by the single-panel DRM
    // layout path: the DS screens are laid out into a logical-size offscreen
    // with no rotation, then that offscreen is drawn into the panel-native FBO
    // here, rotated by the install matrix -- so the layout math stays in the
    // logical (landscape) space and only this final quad maps it onto a rotated
    // (portrait) panel, avoiding the stretch a direct rotated layout produces.
    // Saves and restores the rotation matrix so it does not disturb the loop.
    void blitFullTexture(unsigned int tex, const float rotMat[4]) {
        float saved[4] = {mRotationMatrix[0], mRotationMatrix[1],
                          mRotationMatrix[2], mRotationMatrix[3]};
        for (int i = 0; i < 4; i++) mRotationMatrix[i] = rotMat[i];
        drawDsQuad(tex, 0.0f, 1.0f, 1.0f, 0.0f);
        for (int i = 0; i < 4; i++) mRotationMatrix[i] = saved[i];
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

    // Write drastic's autosave (the reserved slot 9 the
    // drastic-android-mod auto-resumes from). Unlike saveStateSlot this
    // is allowed to use slot 9, so a graceful exit can persist progress
    // that the next launch then auto-loads. Returns true if the JNI
    // save was invoked. The write is processed by drastic's worker
    // thread; the caller should leave a brief window before quitSystem.
    bool saveAutosave();

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

    // Apply a freshly-built config word (from DrasticPrefs::
    // applyConfigBitsFrom) to the running emulator with no relaunch, the
    // way the real drastic app applies in-game video/audio changes.
    // callerBits must be the FULL config word so invariant bits (_m0,
    // sound, etc) are never cleared. Re-asserts the current fast-forward
    // state on top, then runs the same converter-clobber repair
    // (applyMasterStatePatch) as setFastForward. Touches no GL state, so
    // it is safe to call from the overlay/input path.
    void applyVideoConfigLive(long callerBits);

    // Request a live DS-texture re-dim after _Hires3D changed. Safe to
    // call from any thread (sets a flag); the actual GL work runs on the
    // render thread. Idempotent: a no-op when the size already matches.
    void requestDsReDim() { mPendingDsReDim.store(true); }

    // ---- Cheat API (thin wrappers over drastic's cheat JNI exports) ----
    // True when the cheat symbols resolved so the overlay can hide the
    // tab if libdrastic lacks them. Cheats are auto-loaded by startGame;
    // these getters only return data after the game is booted
    // (isFrameReady). All cheat indices are GLOBAL (0..cheatCount-1).
    bool hasCheatApi() const {
        return mGetCheatCount && mGetCheatName && mSetCheatEnabled
            && mUpdateCheats;
    }
    int  cheatCount();
    int  cheatFolderCount();
    std::string cheatName(int idx);
    std::string cheatNote(int idx);
    std::string cheatFolderName(int folder);
    bool cheatEnabled(int idx);
    bool cheatFolderMultiSelect(int folder);
    int  cheatFolderId(int idx);
    void setCheatEnabled(int idx, bool on);
    // updateCheats(1): write cheats/<gamecode>.cht + schedule live
    // re-apply. Call ONCE after a batch of setCheatEnabled, on page close.
    void applyCheats();

    // Custom cheats.
    bool hasCustomCheatApi() const {
        return mGetCustomCheatCount && mAddCustomCheat && mGetCustomCheatData;
    }
    int  customCheatCount();
    std::string customCheatName(int idx);
    bool customCheatEnabled(int idx);
    void setCustomCheatEnabled(int idx, bool on);
    std::vector<int> customCheatData(int idx);
    void removeCustomCheat(int idx);
    // Returns drastic's status (0 = ok, non-zero = error/duplicate).
    int  addCustomCheat(const std::string& name,
                        const std::vector<int>& words, bool enabled);
    // >= 0 if an identical custom cheat already exists.
    int  findCustomCheat(const std::vector<int>& words);

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

    // ---- RetroAchievements memory access ----
    // Expose DraStic's emulated DS Main RAM so an in-process achievement
    // runtime can read console memory directly (libdrastic is loaded into
    // this process, so no IPC is needed). The 4 MB ARM9 Main RAM is reached
    // through the master struct: master = soBase + 0x14c000; the live context
    // pointer is *(master); the DS memory-region descriptor is at
    // context + 0x35d9930, and its first field is the pointer to the 4 MB Main
    // RAM. Bytes are little-endian contiguous on this LE host, so the direct
    // 8/16/32-bit reads an achievement trigger needs are valid.
    //
    // Only call after isFrameReady() is true (the descriptor is populated
    // during DraStic's system init, which completes before the first frame).
    struct DsMainRam {
        uint8_t* base = nullptr;    // start of the 4 MB Main RAM, null if unresolved
        uint32_t mask = 0x3FFFFF;   // address mask for retail DS (4 MB)
        bool valid() const { return base != nullptr; }
    };
    DsMainRam dsMainRam();

    // DS ARM9 Data TCM (16 KB). Some RetroAchievements DS sets read DTCM (the
    // RetroAchievements address space flattens it to 0x1000000..0x1003FFF).
    // DraStic keeps it in the same memory-region table as Main RAM: the table
    // reached at context + 0x35d9930 holds the Main RAM pointer at offset 0 and
    // the Data TCM pointer at offset 0x18 (the two entries in between are the
    // ARM9 code TCMs, which hold ARM instructions; the DTCM entry holds the ARM9
    // stack and fast data, full of 0x02xxxxxx and 0x040000xx addresses, which is
    // how it was identified). Resolved through the live context pointer like
    // dsMainRam(), so it survives any re-anchoring of the descriptor block. Only
    // valid after isFrameReady().
    struct DsDataTcm {
        uint8_t* base = nullptr;    // start of the 16 KB Data TCM, null if unresolved
        uint32_t mask = 0x3FFF;     // 16 KB
        bool valid() const { return base != nullptr; }
    };
    DsDataTcm dsDataTcm();

    // The 16-bit emulated-frame counter from the master struct (master + 0x4b0).
    // On the in-process renderDsToOffscreen() path this counter is dead/frozen
    // (reads 0), so the RetroAchievements integration does NOT tick off it; it
    // drives do_frame off the render-loop vblank tick (onRenderFrame, ~60Hz).
    // Returns 0 when the base is unresolved.
    uint16_t dsEmulatedFrameCounter();

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
    // Wakes any consumer blocked in waitForFrameAfter() when a new pixel-pull
    // frame is produced. Note the RetroAchievements integration does not use
    // this: it drives do_frame off the render-loop vblank tick (onRenderFrame),
    // and this counter is frozen on the renderDsToOffscreen() path.
    std::condition_variable mFrameCv;
    std::mutex mFrameCvMutex;

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

    // Cheat JNI API. Preloaded cheats are auto-loaded inside startGame
    // from usrcheat.dat keyed by the ROM game code (no explicit load
    // call). The [B getters return a jbyteArray handle decoded via
    // fakejni::getByteArrayData. setCheatEnabled only flips an in-memory
    // byte; updateCheats(1) writes cheats/<gamecode>.cht and schedules a
    // live re-apply (so enabled cheats persist across ROM loads).
    typedef int           (*getCheatCount_t)(void* env, void* cls);
    typedef int           (*getCheatFolderCount_t)(void* env, void* cls);
    typedef void*         (*getCheatName_t)(void* env, void* cls, int idx);
    typedef void*         (*getCheatNote_t)(void* env, void* cls, int idx);
    typedef void*         (*getCheatFolderName_t)(void* env, void* cls, int f);
    typedef unsigned char (*getCheatEnabled_t)(void* env, void* cls, int idx);
    typedef unsigned char (*getCheatFolderMultiSelect_t)(void* env, void* cls, int f);
    typedef int           (*getCheatFolderId_t)(void* env, void* cls, int idx);
    typedef void          (*setCheatEnabled_t)(void* env, void* cls, int idx,
                                               unsigned char on);
    typedef void          (*updateCheats_t)(void* env, void* cls,
                                            unsigned char save);
    // Custom (user) cheats.
    typedef int           (*getCustomCheatCount_t)(void* env, void* cls);
    typedef void*         (*getCustomCheatName_t)(void* env, void* cls, int idx);
    typedef unsigned char (*getCustomCheatEnabled_t)(void* env, void* cls, int idx);
    typedef void          (*setCustomCheatEnabled_t)(void* env, void* cls,
                                                     int idx, unsigned char on);
    typedef void*         (*getCustomCheatData_t)(void* env, void* cls, int idx);
    typedef void          (*removeCustomCheat_t)(void* env, void* cls, int idx);
    typedef int           (*addCustomCheat_t)(void* env, void* cls,
                                              void* nameJStr, void* dataIntArr,
                                              int count, unsigned char enabled);
    typedef int           (*findCustomCheat_t)(void* env, void* cls,
                                               void* dataIntArr, int count);

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
    getCheatCount_t              mGetCheatCount = nullptr;
    getCheatFolderCount_t        mGetCheatFolderCount = nullptr;
    getCheatName_t               mGetCheatName = nullptr;
    getCheatNote_t               mGetCheatNote = nullptr;
    getCheatFolderName_t         mGetCheatFolderName = nullptr;
    getCheatEnabled_t            mGetCheatEnabled = nullptr;
    getCheatFolderMultiSelect_t  mGetCheatFolderMultiSelect = nullptr;
    getCheatFolderId_t           mGetCheatFolderId = nullptr;
    setCheatEnabled_t            mSetCheatEnabled = nullptr;
    updateCheats_t               mUpdateCheats = nullptr;
    getCustomCheatCount_t        mGetCustomCheatCount = nullptr;
    getCustomCheatName_t         mGetCustomCheatName = nullptr;
    getCustomCheatEnabled_t      mGetCustomCheatEnabled = nullptr;
    setCustomCheatEnabled_t      mSetCustomCheatEnabled = nullptr;
    getCustomCheatData_t         mGetCustomCheatData = nullptr;
    removeCustomCheat_t          mRemoveCustomCheat = nullptr;
    addCustomCheat_t             mAddCustomCheat = nullptr;
    findCustomCheat_t            mFindCustomCheat = nullptr;
    int                  mAutoLoadSlot = 0;   // startGame boot-load slot
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

    // Fast-forward frame blending (motion blur). During FF the producer
    // free-runs and we sample only the latest slot each 60Hz vblank, so
    // fast motion strobes (capped frameskip). mFfBlendProgram is a copy
    // of the blit shader that cross-fades the current offscreen frame
    // with the previous one (mFfPrevTex) so the motion reads as fluid
    // instead of a hard frame swap. All of this is reached ONLY when
    // mFastForwardOn is true, so 1x rendering is byte-identical. The
    // prev texture and program are allocated lazily on the first FF
    // frame. Gated by persist.gammaos.drastic_nano.ff_blend (default 1),
    // weighted by persist.gammaos.drastic_nano.ff_blend_alpha (0..100,
    // default 50 = equal average; higher = sharper/less trail).
    unsigned int mFfBlendProgram = 0;
    int  mFfBlendPosLoc = -1;
    int  mFfBlendTexLoc = -1;
    int  mFfBlendSamplerLoc = -1;
    int  mFfBlendPrevSamplerLoc = -1;
    int  mFfBlendAmountLoc = -1;
    int  mFfBlendSatLoc = -1;
    int  mFfBlendGradLoc = -1;
    int  mFfBlendRotLoc = -1;
    int  mFfBlendUvRectLoc = -1;
    unsigned int mFfPrevTex = 0;    // holds the previous FF frame
    bool mFfPrevValid = false;      // a genuine prior frame was captured
    bool mFfBlendThisFrame = false; // drawDsQuad should blend this frame
    float mFfBlendAlpha = 0.5f;     // weight of the CURRENT frame

    // Live volume tracking so fast-forward can mute and restore exactly
    // the user's volume. mCurVolume mirrors the last setVolumeRuntime;
    // mPreFfVolume caches it across an FF-mute (-1 = not muted).
    int  mCurVolume = 40;
    int  mPreFfVolume = -1;

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

    // Same walk as patchFinalPassFbo() but redirects the final pass to an
    // arbitrary FBO (used by renderSlotShaded to render the shader straight
    // into the layout offscreen). The no-arg version above forwards to this
    // with mOffscreenFbo and additionally invalidates the slot-shade cache.
    void patchFinalPassFbo(unsigned int targetFbo);

    // Inverse of patchFinalPassFbo: walk the current pass list and
    // reset the final pass.fbo field back to 0. Called before a
    // runtime shader swap so fxLoad's internal teardown cannot delete
    // our mOffscreenFbo via glDeleteFramebuffers on the patched value.
    // Without this, switching shaders in the overlay menu leaves
    // mOffscreenFbo as a stale GL name, and subsequent renders bind
    // an invalid FBO so the displays stop updating.
    void unpatchFinalPassFbo();

    // Rewrite the 13 GPU fast-path feature-flag scalars in drastic's
    // master struct to the real-app values that fix the BG-layer
    // priority rendering bug (affects every DS game, 2D and 3D alike).
    // Applied once ~250ms after startGame from init(), and re-applied
    // immediately after any runtime applyConfig() call (e.g. the
    // fast-forward toggle), because applyConfig's config converter
    // resets these fields back to the fallback-mode defaults. Reads the
    // master base from the loaded .so and is a no-op when the
    // persist.gammaos.nano.drastic_master_patch gate is "0". reason is
    // logged alongside the rewrite count. Returns the number of fields
    // that actually drifted and were rewritten.
    int applyMasterStatePatch(const char* reason);

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

    // renderSlotShaded cache: the slot rect the shader pass list is currently
    // sized/placed for (fxRender ignores its own viewport args -- the final
    // pass uses fxSetup's viewport, so the slot ORIGIN must go through
    // fxSetup), and the FBO its final pass is patched to. Sentinels force a
    // re-fxSetup + re-patch on the next call. Invalidated by the no-arg
    // patchFinalPassFbo() (used by init / hi-res redim / shader swap), so a
    // pass list rebuilt at mOffscreenW/H is never mistaken for slot-sized.
    int mSlotShadeX = -1;
    int mSlotShadeY = -1;
    int mSlotShadeW = -1;
    int mSlotShadeH = -1;
    unsigned int mSlotShadeFbo = 0;

    // Dedicated VBO for renderSlotShaded so the shared mFxVbo (used by the
    // RG DS / offscreen path) is never re-bound or re-laid-out. Same 24-pos +
    // 24-uv layout fxLoad expects (pos at 0, uv at 192); verts 0..5 are a full
    // quad with V-flipped UVs (DS-top at the slot top, matching the drawDsQuad
    // orientation), verts 6..11 a degenerate zero-area quad, verts 18..23 the
    // full-NDC standard-UV quad for intermediate passes.
    unsigned int mSlotVbo = 0;

    // DS texture geometry. drastic uploads each DS screen via
    // glTexSubImage2D at (0,0) with 256x192 (native) or 512x384 (_Hires3D);
    // the DS textures and fxSetup texW/texH must match the live upload size
    // or a smaller native frame fills only the top-left corner. Sized from
    // the _Hires3D bit and re-dimmed live by redimDsTextures().
    int mDsTexW = 0;
    int mDsTexH = 0;
    // Set from any thread (overlay/input) when _Hires3D toggled; consumed
    // on the render thread at the top of renderDsToOffscreen() so the GL
    // re-dim runs on the render thread.
    std::atomic<bool> mPendingDsReDim{false};

    // _Hires3D is config bit 41. mBaseConfigBits is the live base set by
    // init() and applyVideoConfigLive(), so it reflects the current toggle.
    bool dsHiresEnabled() const {
        return (mBaseConfigBits & 0x20000000000L) != 0;
    }
    static void dsTexDims(bool hires, int* w, int* h) {
        *w = hires ? 512 : 256;
        *h = hires ? 384 : 192;
    }
    // Render-thread only: re-create the DS textures + re-run fxSetup +
    // re-patch the final pass FBO for the current _Hires3D size. Mirrors
    // setShaderRuntime's GL resequence. No-op when the size is unchanged.
    void redimDsTextures();

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
