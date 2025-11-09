#include "GammaRgbSampler.h"
#include "SurfaceFlinger.h"

#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <log/log.h>
#include <utils/Timers.h>

#include <fcntl.h>
#include <unistd.h>
#include <dirent.h>
#include <sys/stat.h>
#include <sys/types.h>

#include "DisplayDevice.h"
#include "DisplayHardware/HWComposer.h"

using android::base::GetBoolProperty;
using android::base::GetIntProperty;
using android::base::SetProperty;
using android::base::StringPrintf;

#include <gui/SyncScreenCaptureListener.h>
#include <ui/DisplayId.h>
#include <ui/GraphicBuffer.h>
#include <ui/PixelFormat.h>

namespace android {

static constexpr const char* kOutProp = "sys.gammaos.primary.rgb_hex";
static constexpr const char* kPropAllowProtected = "persist.gammaos.rgb.allow_protected";

GammaRgbSampler::GammaRgbSampler(SurfaceFlinger* flinger)
    : mFlinger(flinger) {}

GammaRgbSampler::~GammaRgbSampler() {
    stop();
    if (mBrightnessFd >= 0) close(mBrightnessFd);
}
 
// android::base doesn't expose a float getter. Parse string safely.
static float getPropFloat(const char* key, float defVal) {
    const std::string s = android::base::GetProperty(key, StringPrintf("%.3f", defVal));
    const char* c = s.c_str();
    char* end = nullptr;
    errno = 0;
    const float v = strtof(c, &end);
    if (end == c || errno == ERANGE) return defVal;
    if (!std::isfinite(v)) return defVal;
    return v;
}

void GammaRgbSampler::start() {
    if (mRun.load()) return;
    refreshProps();
    if (!mEnabled.load()) return;
    mRun.store(true);
    mThread = std::thread([this]{ threadMain(); });
}

void GammaRgbSampler::stop() {
    if (!mRun.load()) return;
    mRun.store(false);
    if (mThread.joinable()) mThread.join();
}

void GammaRgbSampler::onBootFinished() {
    // allow enabling after boot via prop flip
    if (!mRun.load()) start();
}

bool GammaRgbSampler::refreshProps() {
    mEnabled.store(GetBoolProperty("persist.gammaos.rgb.enable", false));
    mDebug  .store(GetBoolProperty("persist.gammaos.rgb.debug",  false));
    mFps    .store(std::max(1, std::min(60, GetIntProperty("persist.gammaos.rgb.fps", 6))));
    mUseHwc .store(GetBoolProperty("persist.gammaos.rgb.use_hwc", true));
    mUseRe  .store(GetBoolProperty("persist.gammaos.rgb.use_re_readback", false));
    mSamplePx.store(std::max(8, std::min(256, GetIntProperty("persist.gammaos.rgb.sample_size_px", 64))));
    mScaleWithBrightness.store(GetBoolProperty("persist.gammaos.rgb.scale_with_brightness", false));

    mBacklightExp = getPropFloat("persist.gammaos.rgb.backlight_exponent", 2.6f);
    mSatBoost     = getPropFloat("persist.gammaos.rgb.saturation_boost",  1.4f);
    mGrayTol      = GetIntProperty  ("persist.gammaos.rgb.gray_tolerance",    4);
    mWhiteAvg     = GetIntProperty  ("persist.gammaos.rgb.white_avg_threshold",200);
    mBlackAvg     = GetIntProperty  ("persist.gammaos.rgb.black_avg_threshold", 3);
    mBoostThresh  = GetIntProperty  ("persist.gammaos.rgb.boost_threshold",     1);
    mMaxBoost     = getPropFloat("persist.gammaos.rgb.max_boost_factor",    1.0f);
    mGrayBlend    = getPropFloat("persist.gammaos.rgb.gray_blend",          0.92f);
    mMinLedFloor  = GetIntProperty  ("persist.gammaos.rgb.min_led_brightness",  3);
    mBrightOverrideThresh = GetIntProperty("persist.gammaos.rgb.brightness_override_threshold", 3);
    return true;
}

void GammaRgbSampler::threadMain() {
    if (mDebug.load()) ALOGI("GammaRgbSampler: thread start");
    findBrightnessNodeOnce(); // best-effort; optional

    // Prepare HWC DCS if requested.
    bool dcsReady = false;
    if (mUseHwc.load()) {
        dcsReady = enableHwcDcsIfAvailable();
        if (mDebug.load()) ALOGI("GammaRgbSampler: HWC DCS ready=%d", dcsReady);
    }

    // Helper to turn off DCS when we disable or toggle off HWC usage
    auto disableDcs = [this](bool log) -> void {
        sp<const DisplayDevice> primary;
        {
            Mutex::Autolock _l(mFlinger->mStateLock);
            primary = mFlinger->getDefaultDisplayDeviceLocked();
        }
        if (!primary) return;
        auto& hwc = mFlinger->getHwComposer();
        const HalDisplayId halId{primary->getPhysicalId()};
        const uint8_t mask = (1u << 0) | (1u << 1) | (1u << 2);
        status_t st = hwc.setDisplayContentSamplingEnabled(halId, /*enable*/false, mask, 0);
        if (log && mDebug.load()) ALOGI("GammaRgbSampler: HWC DCS disabled st=%d", st);
    };

    // Main loop (props are refreshed every iteration)
    while (mRun.load()) {
        // Always pick up latest props so flips are real-time
        refreshProps();

        // If disabled, ensure DCS is off and idle without sampling
        if (!mEnabled.load()) {
            if (dcsReady) {
                disableDcs(/*log*/true);
                dcsReady = false;
            }
            if (mDebug.load()) ALOGV("GammaRgbSampler: disabled; idle");
            std::this_thread::sleep_for(std::chrono::milliseconds(250));
            continue;
        }

        // If HWC sampling is turned off at runtime, drop DCS
        if (dcsReady && !mUseHwc.load()) {
            disableDcs(/*log*/true);
            dcsReady = false;
        }
        // If HWC sampling is turned on at runtime, try to enable
        if (!dcsReady && mUseHwc.load()) {
            dcsReady = enableHwcDcsIfAvailable();
            if (mDebug.load()) ALOGI("GammaRgbSampler: HWC DCS ready=%d", dcsReady);
        }

        int R=0, G=0, B=0;
        bool got = false;
        if (mUseHwc.load() && dcsReady) {
            got = pullHwcSampleOnce(R,G,B);
        }
        if (!got && mUseRe.load()) {
            got = pullReReadbackOnce(R,G,B);
        }

        if (got) {
            if (mScaleWithBrightness.load()) postAdjustWithBrightness(R,G,B);
            const std::string hex = toHex(R,G,B);
            publishHexIfChanged(hex);
            if (mDebug.load()) ALOGI("GammaRgbSampler: sampled %s (R=%d G=%d B=%d)", hex.c_str(), R, G, B);
        } else if (mDebug.load()) {
            ALOGV("GammaRgbSampler: no sample this tick");
        }

        // Sleep using *current* fps (hot-reloadable)
        const int fps = std::max(1, std::min(60, mFps.load()));
        const int sleepMs = 1000 / fps;
        std::this_thread::sleep_for(std::chrono::milliseconds(sleepMs));
    }
    if (mDebug.load()) ALOGI("GammaRgbSampler: thread stop");
}

// --- HWC Displayed Content Sampling ---------------------------------------

bool GammaRgbSampler::enableHwcDcsIfAvailable() {
    // Lock and use *_Locked helpers (friend access).
    sp<const DisplayDevice> primary;
    {
        Mutex::Autolock _l(mFlinger->mStateLock);
        primary = mFlinger->getDefaultDisplayDeviceLocked();
    }
    if (!primary) return false;
    auto& hwc = mFlinger->getHwComposer();
    const HalDisplayId halId{primary->getPhysicalId()};
    const uint8_t mask = (1u << 0) | (1u << 1) | (1u << 2); // R|G|B
    // HWComposer API is setDisplayContentSamplingEnabled(...)
    status_t st = hwc.setDisplayContentSamplingEnabled(
            halId, /*enable*/true, mask, /*maxFrames*/0);
    if (st != NO_ERROR) {
        if (mDebug.load()) ALOGW("GammaRgbSampler: HWC DCS enable failed (status=%d)", st);
        return false;
    }
    return true;
}

bool GammaRgbSampler::pullHwcSampleOnce(int& outR, int& outG, int& outB) {
    // Lock and use *_Locked helpers (friend access).
    sp<const DisplayDevice> primary;
    {
        Mutex::Autolock _l(mFlinger->mStateLock);
        primary = mFlinger->getDefaultDisplayDeviceLocked();
    }
    if (!primary) return false;
    auto& hwc = mFlinger->getHwComposer();
    const HalDisplayId halId{primary->getPhysicalId()};
    DisplayedFrameStats stats;
    status_t st = hwc.getDisplayedContentSample(halId, /*maxFrames*/0, /*timestamp*/0, &stats);
    if (st != NO_ERROR || stats.numFrames == 0) return false;

    // Stats contains per-component histograms (R,G,B) as vectors.
    processHistogramToRgb(stats.component_0_sample,
                          stats.component_1_sample,
                          stats.component_2_sample,
                          outR, outG, outB);
    return true;
}

// --- RenderEngine readback fallback (pre-BFI/shaders) ----------------------
// Use SF's in-process capture: captureDisplay(DisplayCaptureArgs, SyncScreenCaptureListener)
// This renders the display into a tiny buffer (mSamplePx x mSamplePx) and returns a GraphicBuffer
// we can histogram. This path runs inside SF and does not include your post effects.
bool GammaRgbSampler::pullReReadbackOnce(int& outR, int& outG, int& outB) {
    // Resolve primary display token safely
    sp<IBinder> token;
    {
        Mutex::Autolock _l(mFlinger->mStateLock);
        sp<const DisplayDevice> primary = mFlinger->getDefaultDisplayDeviceLocked();
        if (!primary) return false;
        token = primary->getDisplayToken().promote();
    }
    if (!token) return false;

    // Build capture args (types MUST match your tree)
    DisplayCaptureArgs args;
    args.displayToken = token;
    args.width  = static_cast<uint32_t>(std::max(8, std::min(256, mSamplePx.load())));
    args.height = static_cast<uint32_t>(std::max(8, std::min(256, mSamplePx.load())));
    args.pixelFormat = ui::PixelFormat::RGBA_8888;
    args.dataspace   = ui::Dataspace::V0_SRGB;
    args.grayscale   = false;
    // Allow opting-in to secure content capture (many vendors will still blank it)
    const bool allowProtected = GetBoolProperty(kPropAllowProtected, false);
    args.allowProtected = allowProtected;
    args.captureSecureLayers = allowProtected;
    // Parity with gammargb – minimum saturation for gray-override
    const int satPixelThreshold =
        GetIntProperty("persist.gammaos.rgb.sat_pixel_threshold", 30);
    // Kick off capture and wait synchronously
    sp<SyncScreenCaptureListener> listener = sp<SyncScreenCaptureListener>::make();
    mFlinger->captureDisplay(args, listener);  // schedules work on SF main thread
    ScreenCaptureResults res = listener->waitForResults(); // waits on fence internally
    if (!res.buffer) {
        if (mDebug.load()) ALOGV("GammaRgbSampler: captureDisplay returned no buffer");
        return false;
    }

    uint64_t totalSamples = 0;
    // Map and build histograms (RGBA_8888)
    void* addr = nullptr;
    constexpr uint32_t kUsage = GRALLOC_USAGE_SW_READ_OFTEN;
    status_t lk = res.buffer->lock(kUsage, &addr);
    if (lk != OK || !addr) {
        if (mDebug.load()) ALOGV("GammaRgbSampler: buffer lock failed (%d)", lk);
        return false;
    }

    const int w = static_cast<int>(res.buffer->getWidth());
    const int h = static_cast<int>(res.buffer->getHeight());
    const int stride = static_cast<int>(res.buffer->getStride()); // in pixels
    const uint8_t* p = static_cast<const uint8_t*>(addr);

    // Histograms (kept for low-cost post ops / future use)
    std::vector<uint64_t> rh(256), gh(256), bh(256);

    // --- Match gammargb selection pipeline (bold color):
    // Accumulate channel sums, track most-saturated pixel, and
    // build per-dominant-channel buckets (R-major / G-major / B-major).
    uint64_t sr=0, sg=0, sb=0;              // global sums
    uint64_t r_r=0, g_r=0, b_r=0, c_r=0;    // R-major bucket sums/count
    uint64_t r_g=0, g_g=0, b_g=0, c_g=0;    // G-major bucket
    uint64_t r_b=0, g_b=0, b_b=0, c_b=0;    // B-major bucket
    int best_sat = -1; int best_r=0, best_g=0, best_b=0;
    for (int y = 0; y < h; ++y) {
        const uint32_t* row = reinterpret_cast<const uint32_t*>(p + y * stride * 4);
        for (int x = 0; x < w; ++x) {
            const uint32_t px = row[x];
            // RGBA_8888 in little-endian memory maps to A<<24 | B<<16 | G<<8 | R
            const int r = (px      ) & 0xFF;
            const int g = (px >>  8) & 0xFF;
            const int b = (px >> 16) & 0xFF;
            // vectors index with size_t; cast to avoid -Wsign-conversion
            const size_t ri = static_cast<size_t>(r);
            const size_t gi = static_cast<size_t>(g);
            const size_t bi = static_cast<size_t>(b);
            rh[ri]++; 
            gh[gi]++; 
            bh[bi]++;
            totalSamples++;

            // global sums
            sr += (uint64_t)r; sg += (uint64_t)g; sb += (uint64_t)b;

            // saturation (max-min)
            const int mx = (r>g ? (r>b?r:b) : (g>b?g:b));
            const int mn = (r<g ? (r<b?r:b) : (g<b?g:b));
            const int sat = mx - mn;
            if (sat > best_sat) { best_sat = sat; best_r = r; best_g = g; best_b = b; }

            // dominant-channel buckets (strict ‘>’ like gammargb)
            if      (r>g && r>b) { r_r += (uint64_t)r; g_r += (uint64_t)g; b_r += (uint64_t)b; c_r++; }
            else if (g>r && g>b) { r_g += (uint64_t)r; g_g += (uint64_t)g; b_g += (uint64_t)b; c_g++; }
            else if (b>r && b>g) { r_b += (uint64_t)r; g_b += (uint64_t)g; b_b += (uint64_t)b; c_b++; }
        }
    }
    res.buffer->unlock();
 
    // If everything sums to zero, it's likely protected/blanked: hold last color by returning false.
    // (BFI/shaders can still run; we just won't update LEDs while secure video is on screen.)
    uint64_t sumAll = 0;
    for (size_t i = 0; i < 256; ++i) sumAll += rh[i] + gh[i] + bh[i];
    if (sumAll == 0 || totalSamples == 0) {
        if (mDebug.load()) ALOGI("GammaRgbSampler: secure/blank capture detected; holding previous color");
        return false;
    }

    // --- Decide color (faithful to gammargb)
    // Channel averages
    const uint64_t denom = (uint64_t)w * (uint64_t)h;
    const int tr0 = denom ? (int)(sr / denom) : 0;
    const int tg0 = denom ? (int)(sg / denom) : 0;
    const int tb0 = denom ? (int)(sb / denom) : 0;

    const int maxc   = std::max({tr0, tg0, tb0});
    const int minc   = std::min({tr0, tg0, tb0});
    const int spread = maxc - minc;
    const int avg    = (tr0 + tg0 + tb0) / 3;

    int tr = tr0, tg = tg0, tb = tb0;
    if (spread <= mGrayTol &&
        best_sat >= satPixelThreshold &&
        avg >  mBlackAvg &&
        avg <  mWhiteAvg)
    {
        // Use the most-saturated pixel, normalize to full scale, then blend towards gray
        tr = best_r; tg = best_g; tb = best_b;
        const int m = std::max({tr, tg, tb});
        if (m > 0) { tr = tr*255/m; tg = tg*255/m; tb = tb*255/m; }
        tr = (int)(tr*(1.f - mGrayBlend) + avg*mGrayBlend + .5f);
        tg = (int)(tg*(1.f - mGrayBlend) + avg*mGrayBlend + .5f);
        tb = (int)(tb*(1.f - mGrayBlend) + avg*mGrayBlend + .5f);
    } else if (avg >= mWhiteAvg && spread <= mGrayTol) {
        tr = tg = tb = 255;
    } else if (avg <= mBlackAvg && spread <= mGrayTol) {
        tr = tg = tb = 0;
    } else if (spread <= mGrayTol) {
        tr = tg = tb = avg;
    } else {
        // Choose the dominant-channel bucket average
        if      (c_r >= c_g && c_r >= c_b && c_r > 0) { tr = (int)(r_r / c_r); tg = (int)(g_r / c_r); tb = (int)(b_r / c_r); }
        else if (c_g >= c_r && c_g >= c_b && c_g > 0) { tr = (int)(r_g / c_g); tg = (int)(g_g / c_g); tb = (int)(b_g / c_g); }
        else if (c_b > 0)                             { tr = (int)(r_b / c_b); tg = (int)(g_b / c_b); tb = (int)(b_b / c_b); }
    }

    // Low-light boost (same intent as gammargb)
    if (avg < mBoostThresh) {
        float f = (float)mBoostThresh / (avg ? avg : 1);
        if (f > mMaxBoost) f = mMaxBoost;
        tr = std::min(255, (int)(tr * f));
        tg = std::min(255, (int)(tg * f));
        tb = std::min(255, (int)(tb * f));
    }

    outR = tr; outG = tg; outB = tb;
    return true;
}

// --- Color logic (ported from GammaRGB) -----------------------------------
void GammaRgbSampler::processHistogramToRgb(const std::vector<uint64_t>& rh,
                                            const std::vector<uint64_t>& gh,
                                            const std::vector<uint64_t>& bh,
                                            int& outR, int& outG, int& outB) const {
    // channel averages
    auto avgFromHist = [](const std::vector<uint64_t>& h)->int{
        __uint128_t sum = 0, cnt = 0;
        const size_t N = h.size();
        for (size_t i=0;i<N;i++){ sum += (__uint128_t)i * h[i]; cnt += h[i]; }
        if (cnt == 0) return 0;
        uint64_t v = (uint64_t)(sum / cnt);
        return (int)v;
    };
    int tr = avgFromHist(rh);
    int tg = avgFromHist(gh);
    int tb = avgFromHist(bh);

    const int maxc = std::max({tr,tg,tb});
    const int minc = std::min({tr,tg,tb});
    const int spread = maxc - minc;
    const int avg = (tr+tg+tb)/3;

    if (spread <= mGrayTol && avg >= mWhiteAvg) {
        tr = tg = tb = 255;
    } else if (spread <= mGrayTol && avg <= mBlackAvg) {
        tr = tg = tb = 0;
    } else if (spread <= mGrayTol) {
        tr = tg = tb = avg;
    } else {
        // full-color grouping heuristic using dominant channel energy
        // (approximation of bucketing approach from GammaRGB)
        uint64_t sumR=0, sumG=0, sumB=0;
        for (size_t i=0;i<rh.size();i++){ sumR += (uint64_t)i * rh[i]; }
        for (size_t i=0;i<gh.size();i++){ sumG += (uint64_t)i * gh[i]; }
        for (size_t i=0;i<bh.size();i++){ sumB += (uint64_t)i * bh[i]; }
        if (sumR >= sumG && sumR >= sumB) {
            // keep tr/tg/tb as computed averages; emphasize R w/ slight bias toward its avg
            (void)0;
        } else if (sumG >= sumR && sumG >= sumB) {
            (void)0;
        } else {
            (void)0;
        }
    }

    // low-light boost (conservative; matches GammaRGB intent)
    if (avg < mBoostThresh) {
        float f = (float)mBoostThresh / (avg ? avg : 1);
        if (f > mMaxBoost) f = mMaxBoost;
        tr = std::min(255, int(tr * f));
        tg = std::min(255, int(tg * f));
        tb = std::min(255, int(tb * f));
    }

    // optional mild saturation boost after brightness scaling (finalized in postAdjustWithBrightness)
    outR = tr; outG = tg; outB = tb;
}

void GammaRgbSampler::postAdjustWithBrightness(int& r, int& g, int& b) const {
    int rawB = readBrightnessNow();
    if (rawB < 0) rawB = 255; // fallback

    if (rawB <= mBrightOverrideThresh) {
        r = g = b = 1;
        return;
    }

    float nb = std::max(1, std::min(255, rawB)) / 255.0f;
    float sbf = powf(nb, mBacklightExp);
    float fr = (r/255.0f)*sbf;
    float fg = (g/255.0f)*sbf;
    float fb = (b/255.0f)*sbf;

    float m = std::max(fr, std::max(fg, fb));
    if (m>0 && m < (mMinLedFloor/255.0f)) {
        const float sc = (mMinLedFloor/255.0f)/m;
        fr *= sc; fg *= sc; fb *= sc;
    }
    // saturation boost
    float L = .299f*fr + .587f*fg + .114f*fb;
    fr = L + (fr-L)*mSatBoost;
    fg = L + (fg-L)*mSatBoost;
    fb = L + (fb-L)*mSatBoost;

    fr = std::max(0.f, std::min(1.f, fr));
    fg = std::max(0.f, std::min(1.f, fg));
    fb = std::max(0.f, std::min(1.f, fb));
    r = int(fr * 255.f + .5f);
    g = int(fg * 255.f + .5f);
    b = int(fb * 255.f + .5f);
}

std::string GammaRgbSampler::toHex(int r, int g, int b) {
    return StringPrintf("#%02X%02X%02X", std::max(0,std::min(255,r)),
                                     std::max(0,std::min(255,g)),
                                     std::max(0,std::min(255,b)));
}

void GammaRgbSampler::publishHexIfChanged(const std::string& hex) {
    if (hex == mLastHex) return;
    mLastHex = hex;
    SetProperty(kOutProp, hex);
}

// --- brightness ------------------------------------------------------------

void GammaRgbSampler::findBrightnessNodeOnce() {
    if (mBrightnessFd >= 0) return;
    const char* base = "/sys/class/backlight";
    DIR* d = opendir(base);
    if (!d) return;
    while (auto* ent = readdir(d)) {
        if (ent->d_name[0]=='.') continue;
        std::string path = StringPrintf("%s/%s/brightness", base, ent->d_name);
        int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC | O_NONBLOCK);
        if (fd >= 0) {
            mBrightnessFd = fd;
            mBrightnessPath = path;
            break;
        }
    }
    closedir(d);
}

int GammaRgbSampler::readBrightnessNow() const {
    if (mBrightnessFd < 0) return -1;
    char buf[16] = {0};
    lseek(mBrightnessFd, 0, SEEK_SET);
    const ssize_t n = read(mBrightnessFd, buf, sizeof(buf)-1);
    if (n <= 0) return -1;
    int v = atoi(buf);
    if (v < 1) v = 1;
    if (v > 255) v = 255; // normalized expected range
    return v;
}

} // namespace android