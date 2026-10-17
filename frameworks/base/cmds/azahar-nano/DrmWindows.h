// DrmWindows: the DRM-direct backend's panels for azahar-nano.
//
// One in-process BufferQueue per panel. The core's Vulkan or GL renderer presents
// into the producer side (a plain Surface / ANativeWindow); the consumer side is
// a presenter thread that imports every GraphicBuffer once as a DRM framebuffer
// (PRIME dma-buf import plus the gralloc modifier, like gammaos-nano) and
// atomic-flips it on the panel's primary plane with the buffer's acquire fence
// as the plane input fence. Each CRTC flips independently at its own vblank;
// a buffer that arrives while a flip is pending replaces the queued one. No
// compositor is in the path and no CPU copy happens.
#pragma once

#include <array>
#include <atomic>
#include <cstdint>
#include <map>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>

#include <xf86drmMode.h>

#include <gui/BufferItemConsumer.h>
#include <gui/Surface.h>
#include <ui/GraphicBuffer.h>
#include <utils/StrongPointer.h>

struct ANativeWindow;

namespace azahar_nano {

// Height of the HUD strip along the panel's logical top edge (px).
constexpr uint32_t kHudStrip = 128;
constexpr uint32_t kHudTop = 1, kHudBottom = 2;

struct DrmPanel {
    uint32_t connId = 0;
    uint32_t crtcId = 0;
    uint32_t crtcIndex = 0;
    uint32_t planeId = 0;
    uint32_t modeBlob = 0;
    uint32_t width = 0;     // panel native (scanout) size
    uint32_t height = 0;
    bool isDsi = false;
    int portIndex = 0;     // position among the connected connectors (SurfaceFlinger's port number)
    int mountDeg = 0;      // how the panel is mounted (persist.gsf.rot.<port>), 0 or 180 handled
    double refreshHz = 60.0;   // from the mode timings
    uint32_t bufW = 0, bufH = 0;   // game buffer size (render_scale)
    uint64_t bufUsage = 0;
    bool loggedUbwc = false;
    uint32_t retirePlaneId = 0, retireFbProp = 0, retireCrtcProp = 0;   // plane to switch off
    float renderScale = 1.0f;
    // Two verified game buffer sizes: plain, and the one used while an LCD shader is on
    // (at least 3 output pixels per emulated pixel). setLcdOutput() switches between them.
    uint32_t plainW = 0, plainH = 0, lcdW = 0, lcdH = 0;
    // plane props
    uint32_t pFbId = 0, pCrtcId = 0, pSrcX = 0, pSrcY = 0, pSrcW = 0, pSrcH = 0;
    uint32_t pCrtcX = 0, pCrtcY = 0, pCrtcW = 0, pCrtcH = 0, pInFence = 0, pRotation = 0;
    uint64_t rotationValue = 0;   // DRM rotation bitmask to apply (0 = leave)
    // crtc / connector props
    uint32_t cActive = 0, cModeId = 0, connCrtcId = 0;

    // buffer queue
    android::sp<android::IGraphicBufferProducer> producer;
    android::sp<android::BufferItemConsumer> consumer;
    android::sp<android::Surface> surface;
    android::sp<android::BufferItemConsumer::FrameAvailableListener> listener;
    // Overlay source: while active the panel scans out these frames instead of
    // the core's (the in-game menu; the core is paused meanwhile).
    android::sp<android::IGraphicBufferProducer> ovProducer;
    android::sp<android::BufferItemConsumer> ovConsumer;
    android::sp<android::Surface> ovSurface;
    android::sp<android::BufferItemConsumer::FrameAvailableListener> ovListener;
    std::shared_ptr<std::atomic<bool>> ovActive = std::make_shared<std::atomic<bool>>(false);
    bool onScreenIsOverlay = false;
    bool pendingIsOverlay = false;
    // HUD: a second plane on the same CRTC, alpha blended over the game (frame
    // rate counter). Fed by its own small BufferQueue; the latest buffer is kept on
    // the plane until a newer one arrives.
    uint32_t hudPlaneId = 0, hudW = 0, hudH = 0;
    uint32_t hPFbId = 0, hPCrtcId = 0, hPSrcX = 0, hPSrcY = 0, hPSrcW = 0, hPSrcH = 0;
    uint32_t hPCrtcX = 0, hPCrtcY = 0, hPCrtcW = 0, hPCrtcH = 0, hPAlpha = 0, hPBlend = 0, hPZpos = 0;
    uint64_t hudBlendCoverage = 0;   // blend_op enum value for coverage blending
    uint64_t hudAlphaMax = 255, hudZpos = 1;
    android::sp<android::IGraphicBufferProducer> hudProducer;
    android::sp<android::BufferItemConsumer> hudConsumer;
    android::sp<android::Surface> hudSurface;
    android::sp<android::BufferItemConsumer::FrameAvailableListener> hudListener;
    std::map<uint64_t, uint32_t> hudFbCache;
    android::BufferItem hudOnScreen, hudPending;
    bool hudHasOnScreen = false, hudHasPending = false;
    std::shared_ptr<std::atomic<bool>> hudActive = std::make_shared<std::atomic<bool>>(false);
    // Strips of the HUD buffer the plane scans out (kHudTop | kHudBottom).
    std::shared_ptr<std::atomic<uint32_t>> hudRegions = std::make_shared<std::atomic<uint32_t>>(1u);
    bool hudCleared = true;
    bool hudInCommit = false;   // the current commit carries HUD plane state
    bool hudBroken = false;     // the kernel refused the HUD plane: stay off
    // presentation state
    std::map<uint64_t, uint32_t> fbCache;      // GraphicBuffer id -> fb_id
    android::BufferItem onScreen;              // buffer currently scanned out
    android::BufferItem pending;               // buffer in the pending flip
    bool hasOnScreen = false, hasPending = false;
    bool flipPending = false;
    uint64_t flips = 0, drops = 0, stale = 0, longGaps = 0;
    int64_t periodSumNs = 0;
    uint64_t periodCount = 0;
    std::shared_ptr<std::atomic<double>> measuredPeriodNs = std::make_shared<std::atomic<double>>(0.0);   // 0 until 64 consecutive flips
    uint32_t hPRotation = 0;
    int64_t lastFlipNs = 0, maxGapNs = 0;
    int64_t commitNs = 0, latSumNs = 0, latMaxNs = 0, queuedNs = 0;
    int64_t acquiredNs = 0, ioctlNs = 0, signalNs = 0;
    int pickIndex = 0, readyCount = 0;
    int wakeFd = -1;
    std::shared_ptr<std::atomic<uint64_t>> availCount = std::make_shared<std::atomic<uint64_t>>(0);
    std::shared_ptr<std::atomic<int64_t>> lastAvailNs = std::make_shared<std::atomic<int64_t>>(0);
    int64_t cbNs = 0;
    std::shared_ptr<std::array<std::atomic<uint32_t>, 5>> cadence =
        std::make_shared<std::array<std::atomic<uint32_t>, 5>>();
    std::shared_ptr<std::mutex> mu = std::make_shared<std::mutex>();  // shared so the panel stays copyable
    bool geometrySet = false;
    uint32_t srcW = 0, srcH = 0;
    uint64_t latCount = 0;
};

class DrmWindows {
public:
    // Opens the DRM device, takes master, enumerates the panels and sets their
    // modes. Returns the number of panels (0 on failure).
    int create();
    void teardown();

    int count() const { return (int)mPanels.size(); }
    // Overlay window for panel idx (same size as the panel), created on first use.
    ANativeWindow* overlayWindow(int idx);
    // Switch a panel between the core's frames and the overlay's.
    void setOverlayActive(int idx, bool active);
    // HUD plane: an alpha blended layer the size of the panel. Only the strips
    // selected with setHudRegions are scanned out: kHudTop is the strip along the
    // panel's top edge as seen by the user (the left columns of an upright panel's
    // buffer, the top rows otherwise), kHudBottom the one along the bottom edge.
    // Returns null when the CRTC has no spare plane. w/h are the buffer size.
    ANativeWindow* hudWindow(int idx, uint32_t* w, uint32_t* h);
    void setHudActive(int idx, bool active);
    void setHudRegions(int idx, uint32_t regions);
    ANativeWindow* window(int idx) const;
    uint32_t width(int idx) const { return mPanels[idx].width; }
    uint32_t height(int idx) const { return mPanels[idx].height; }
    // Logical size the core renders at for this panel (the window's default
    // buffer size). Portrait panels get a portrait window; the core rotates.
    uint32_t windowWidth(int idx) const { return mPanels[idx].width; }
    uint32_t windowHeight(int idx) const { return mPanels[idx].height; }
    bool isPortrait(int idx) const { return mPanels[idx].height > mPanels[idx].width; }
    // Page flips shown on a panel so far (the on-screen BLIT rate is its derivative).
    uint64_t flipCount(int idx) const { return mPanels[idx].flips; }
    double refreshHz(int idx) const { return mPanels[idx].refreshHz; }
    float renderScale(int idx) const { return mPanels[idx].renderScale; }
    // GammaOS tweak "native output": before create(), ask for game buffers whose
    // short side is exactly this many pixels (the emulated screen height at the
    // internal resolution), so the emulated image lands 1:1 and the display
    // plane does the one upscale to the panel. 0 = full panel size.
    void setOutputShortSide(int idx, uint32_t px) { if (idx >= 0 && idx < 2) mOutputShortSide[idx] = px; }
    // The same, given the emulated screen at the internal resolution: the buffer keeps the
    // panel's aspect ratio and is the smallest one the screen fits 1:1 (its height on a
    // panel at least as wide as the screen, taller on a 4:3 panel for the 5:3 top screen).
    void setOutputScreen(int idx, uint32_t w, uint32_t h) {
        if (idx >= 0 && idx < 2) { mOutputScreenW[idx] = w; mOutputScreenH[idx] = h; }
    }
    // How the panel is mounted, from the vendor's persist.gsf.rot.<port> (0 or 180): the
    // picture is drawn turned by the renderer since these planes do not rotate.
    int mountRotationDeg(int idx) const { return mPanels[idx].mountDeg; }
    // Short side wanted while an LCD shader is on (0 = same as the plain size), and whether
    // the panel starts in that size. Both before create().
    void setLcdShortSide(int idx, uint32_t px, bool startOn) {
        if (idx >= 0 && idx < 2) { mLcdShortSide[idx] = px; mLcdStart[idx] = startOn; }
    }
    // Live switch between the plain and LCD buffer sizes; true when the size changed (the
    // game's window must then be told it was resized).
    bool setLcdOutput(int idx, bool on);
    // Mean interval between consecutive page flips (ns), 0 until measured.
    double measuredPeriodNs(int idx) const { return mPanels[idx].measuredPeriodNs->load(); }

private:
    bool openDevice();
    bool enumerate();
    bool pickPlane(DrmPanel& p);
    bool loadProps(DrmPanel& p);
    bool modeset();
    bool createQueue(DrmPanel& p, int idx);
    bool createOverlayQueue(DrmPanel& p, int idx);
    bool pickHudPlane(DrmPanel& p);
    bool createHudQueue(DrmPanel& p, int idx);
    uint32_t importHudFb(DrmPanel& p, const android::sp<android::GraphicBuffer>& gb);
    void addHudProps(DrmPanel& p, drmModeAtomicReqPtr req);
    uint32_t importFb(DrmPanel& p, const android::sp<android::GraphicBuffer>& gb);
    // `retire`: a panel moving to another plane tests with its old plane switched off, as the
    // first flip on the new plane does (see testScaledScanout).
    bool testScaledScanout(DrmPanel& p, uint32_t w, uint32_t h, const DrmPanel* retire = nullptr);
    bool switchToScalingPlane(DrmPanel& p);
    bool flip(DrmPanel& p, android::BufferItem& item);
    void eventLoop();
    void panelLoop(int idx);
    void onFlipDone(unsigned int crtcId, int64_t tsNs);
    void captureAll();

    int mFd = -1;
    std::vector<DrmPanel> mPanels;
    std::thread mThread;
    std::atomic<bool> mRun{false};
    std::vector<std::thread> mPanelThreads;
    uint32_t mOutputShortSide[2] = {0, 0};
    uint32_t mOutputScreenW[2] = {0, 0}, mOutputScreenH[2] = {0, 0};
    uint32_t mLcdShortSide[2] = {0, 0};
    bool mLcdStart[2] = {false, false};
    bool mBlockingCommits = true;
    int mDpPostFlipUs = 0;
    bool mShowNewest = true;   // show the newest finished frame, drop older finished ones
    static constexpr size_t kMaxReady = 3;   // acquired, not yet flipped frames per panel
    std::vector<uint32_t> mBlackFbs;
    bool mMinimalFlips = true;
    bool mReserveScalerForFirst = false;   // two panels: the scaling pipe goes to the first (top)
    int mTraceLeft = 0;
};

} // namespace azahar_nano
