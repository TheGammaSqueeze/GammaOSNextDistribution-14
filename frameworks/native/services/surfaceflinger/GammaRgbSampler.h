#pragma once

#include <atomic>
#include <array>
#include <vector>
#include <memory>
#include <thread>
#include <string>

namespace android {

class SurfaceFlinger;

// Lightweight, isolated sampler that:
//  - Pulls color from HWC Displayed Content Sampling (if available) on primary display (id 0).
//  - Optionally falls back to a tiny RenderEngine readback (pre-BFI/shaders) if allowed by prop.
//  - Applies the gammargb.c color decision pipeline (ported, tunable via props).
//  - Publishes #RRGGBB into sys.gammaos.primary.rgb_hex.
class GammaRgbSampler {
public:
    explicit GammaRgbSampler(SurfaceFlinger* flinger);
    ~GammaRgbSampler();

    void start();       // non-blocking; starts internal thread if enabled by prop
    void stop();        // join thread
    void onBootFinished(); // optional: (re)arm after boot complete
    // On-demand fast sample that bypasses the sampler thread's sleep, intended to
    // be called just before post-FX are applied (pre-FX sampling point).
    // If 'primaryOnly' is true, ignore non-primary outputs (default).
    void sampleNow(bool primaryOnly = true);

private:
    void threadMain();
    bool refreshProps(); // reads sysprops to (re)configure

    // HWC Displayed Content Sampling path
    bool enableHwcDcsIfAvailable();
    bool pullHwcSampleOnce(int& outR, int& outG, int& outB);

    // RenderEngine readback fallback (guarded by prop)
    bool pullReReadbackOnce(int& outR, int& outG, int& outB);

    // Color logic pulled from gammargb.c (scalar port)
    void processHistogramToRgb(const std::vector<uint64_t>& r,
                               const std::vector<uint64_t>& g,
                               const std::vector<uint64_t>& b,
                               int& outR, int& outG, int& outB) const;
    void postAdjustWithBrightness(int& r, int& g, int& b) const;
    static std::string toHex(int r, int g, int b);
    float readScreenBrightnessScalar() const;
    bool tryGrabPreFxRGB(int& R, int& G, int& B, bool primaryOnly);
    void publishHexIfChanged(const std::string& hex);

    // brightness helpers (simple polling; never blocks SF)
    void findBrightnessNodeOnce();
    int  readBrightnessNow() const;

private:
    SurfaceFlinger* const mFlinger;
    std::thread mThread;
    std::atomic<bool> mRun{false};

    // props
    std::atomic<bool> mEnabled{false};
    std::atomic<bool> mDebug{false};
    std::atomic<int>  mFps{6};
    std::atomic<bool> mUseHwc{true};
    std::atomic<bool> mUseRe{false};
    std::atomic<int>  mSamplePx{64};
    std::atomic<bool> mScaleWithBrightness{false};
    float mBacklightExp = 1.0f;
    float mSatBoost     = 1.4f;
    int   mGrayTol      = 4;
    int   mWhiteAvg     = 200;
    int   mBlackAvg     = 3;
    int   mBoostThresh  = 1;
    float mMaxBoost     = 1.0f;
    float mGrayBlend    = 0.92f;
    int   mMinLedFloor  = 3;
    int   mBrightOverrideThresh = 3;

    // fade output between samples (publish sys.gammaos.primary.rgb_hex at high rate)
    std::atomic<bool> mFadeEnable{true};     // persist.gammaos.rgb.fade.enable
    std::atomic<int>  mFadeFps{60};          // persist.gammaos.rgb.fade.fps
    // track last published RGB to interpolate
    int mLastR{0}, mLastG{0}, mLastB{0};
    // state
    std::string mLastHex;
    mutable int mBrightnessFd{-1};
    std::string mBrightnessPath;
    std::atomic<bool> mPreFxEnable{true};  // persist.gammaos.rgb.sample.pre_fx
};

} // namespace android