// GammaRGB shared colour-selection pipeline.
//
// Single source of truth for turning a small RGBA pixel sample into the LED
// colour that gammargb consumes via persist.gammaos.primary.rgb_hex. Both the
// SurfaceFlinger sampler (GammaRgbSampler, used when SF composites the screen)
// and gammaos-nano (which owns the framebuffer directly in DRM-render mode and
// samples its own AHardwareBuffer) include this header so the two paths produce
// byte-identical LED colours. Keep it dependency-free (no SF/nano types) so it
// can be shared across both modules.
//
// The pixel accumulation and the scene-classification selection are transcribed
// verbatim from the original GammaRgbSampler readback path; only the per-pixel
// property reads were lifted out into GammaRgbParams so the function stays pure.
#ifndef GAMMA_RGB_PROCESS_H
#define GAMMA_RGB_PROCESS_H

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <string>

namespace gammargb {

// All tunables the pipeline needs, read once by the caller from the
// persist.gammaos.rgb.* properties (defaults noted from GammaRgbSampler).
struct GammaRgbParams {
    int   grayTol             = 4;      // gray_tolerance
    int   whiteAvg            = 200;    // white_avg_threshold
    int   blackAvg            = 3;      // black_avg_threshold
    int   boostThresh         = 1;      // boost_threshold
    float maxBoost            = 1.0f;   // max_boost_factor
    int   satPixelThreshold   = 30;     // sat_pixel_threshold
    bool  clusterOverWhite    = true;   // cluster_over_white
    float clusterMinShare     = 0.03f;  // cluster_min_share
    float colorShareOverWhite = 0.03f;  // color_share_over_white
    float bestSatGrayShareMax = 0.05f;  // best_sat_gray_share_max

    // Brightness stage.
    bool  scaleWithBrightness = false;  // scale_with_brightness
    float brightnessScalar    = -1.0f;  // [0..1], or <0 if unavailable (fall back to rawBrightness)
    int   rawBrightness       = 255;    // 0..255 fallback
    int   brightOverrideThresh= 3;      // brightness_override_threshold
    float backlightExp        = 1.0f;   // brightness_curve_exp
    int   minLedFloor         = 3;      // min_led_brightness
    float satBoost            = 1.4f;   // saturation_boost
    int   ledBrightness       = 255;    // led_brightness (static path)
};

static constexpr int kHueBins = 24; // 15-degree bins across 360 degrees

// Select the dominant LED colour from an RGBA8888 (little-endian: byte0=R,
// byte1=G, byte2=B) pixel buffer. The buffer is walked with the given steps so
// the same routine serves a fully-downscaled SF capture (stepX=stepY=1) and a
// full-resolution nano AHB strided down to ~sampleN samples. Returns false when
// the sample is entirely black/blank (caller should hold the previous colour).
inline bool selectFromPixels(const uint8_t* base, int w, int h, int rowStrideBytes,
                             int stepX, int stepY, const GammaRgbParams& P,
                             int& outR, int& outG, int& outB) {
    if (!base || w <= 0 || h <= 0) return false;
    if (stepX < 1) stepX = 1;
    if (stepY < 1) stepY = 1;

    struct HueCluster { uint64_t sumR = 0, sumG = 0, sumB = 0, count = 0; };
    HueCluster clusters[kHueBins];

    uint64_t sr = 0, sg = 0, sb = 0;               // global channel sums
    uint64_t r_r = 0, g_r = 0, b_r = 0, c_r = 0;   // R-major bucket
    uint64_t r_g = 0, g_g = 0, b_g = 0, c_g = 0;   // G-major bucket
    uint64_t r_b = 0, g_b = 0, b_b = 0, c_b = 0;   // B-major bucket
    uint64_t totalColorCount = 0;                  // pixels considered "colored"
    uint64_t totalSamples = 0;                     // pixels actually accumulated
    int best_sat = -1, best_r = 0, best_g = 0, best_b = 0;

    for (int y = 0; y < h; y += stepY) {
        const uint32_t* row =
            reinterpret_cast<const uint32_t*>(base + (size_t)y * (size_t)rowStrideBytes);
        for (int x = 0; x < w; x += stepX) {
            const uint32_t px = row[x];
            const int r = (px)       & 0xFF;
            const int g = (px >> 8)  & 0xFF;
            const int b = (px >> 16) & 0xFF;
            totalSamples++;

            sr += (uint64_t)r; sg += (uint64_t)g; sb += (uint64_t)b;

            const int mx = (r > g ? (r > b ? r : b) : (g > b ? g : b));
            const int mn = (r < g ? (r < b ? r : b) : (g < b ? g : b));
            const int sat = mx - mn;
            if (sat > best_sat) { best_sat = sat; best_r = r; best_g = g; best_b = b; }

            const int lum = (r + g + b) / 3;
            const bool isNearGray = (sat <= P.grayTol);
            const bool isBlackish = (lum <= P.blackAvg);
            const bool isWhitish  = (lum >= P.whiteAvg);

            if (!isNearGray && !isBlackish && !isWhitish && sat >= P.satPixelThreshold) {
                float hue = 0.f;
                const int denom = mx - mn;
                if (denom > 0) {
                    if (mx == r) {
                        hue = 60.f * ((g - b) / (float)denom);
                        if (hue < 0.f) hue += 360.f;
                    } else if (mx == g) {
                        hue = 60.f * ((b - r) / (float)denom + 2.f);
                    } else {
                        hue = 60.f * ((r - g) / (float)denom + 4.f);
                    }
                }
                if (hue < 0.f) hue += 360.f;
                if (hue >= 360.f) hue -= 360.f;

                const float binWidth = 360.f / kHueBins;
                int idx = (int)(hue / binWidth);
                if (idx < 0) idx = 0;
                if (idx >= kHueBins) idx = kHueBins - 1;

                clusters[idx].sumR += (uint64_t)r;
                clusters[idx].sumG += (uint64_t)g;
                clusters[idx].sumB += (uint64_t)b;
                clusters[idx].count++;
                totalColorCount++;
            }

            if      (r > g && r > b) { r_r += (uint64_t)r; g_r += (uint64_t)g; b_r += (uint64_t)b; c_r++; }
            else if (g > r && g > b) { r_g += (uint64_t)r; g_g += (uint64_t)g; b_g += (uint64_t)b; c_g++; }
            else if (b > r && b > g) { r_b += (uint64_t)r; g_b += (uint64_t)g; b_b += (uint64_t)b; c_b++; }
        }
    }

    // Empty capture -> hold previous colour. Note: an all-black frame is NOT
    // treated as blank (SF's sumAll counts pixels, not values); it selects black
    // via the blackAvg branch below, matching the SF sampler exactly.
    if (totalSamples == 0) return false;

    // Dominant hue-cluster candidate.
    int clusterR = 0, clusterG = 0, clusterB = 0;
    uint64_t bestClusterCount = 0;
    for (int i = 0; i < kHueBins; ++i) {
        const uint64_t cnt = clusters[i].count;
        if (cnt == 0) continue;
        if (cnt > bestClusterCount) {
            bestClusterCount = cnt;
            clusterR = (int)(clusters[i].sumR / cnt);
            clusterG = (int)(clusters[i].sumG / cnt);
            clusterB = (int)(clusters[i].sumB / cnt);
        }
    }

    const float totalPixF   = (float)totalSamples;
    const float colorShare  = (float)totalColorCount / totalPixF;
    const float clusterShare = (float)bestClusterCount / totalPixF;

    const uint64_t denom = totalSamples;
    const int tr0 = (int)(sr / denom);
    const int tg0 = (int)(sg / denom);
    const int tb0 = (int)(sb / denom);

    const int maxc   = std::max({tr0, tg0, tb0});
    const int minc   = std::min({tr0, tg0, tb0});
    const int spread = maxc - minc;
    const int avg    = (tr0 + tg0 + tb0) / 3;

    int tr = tr0, tg = tg0, tb = tb0;
    const bool haveDominantCluster =
        (bestClusterCount > 0) && (clusterShare >= P.clusterMinShare);

    if (avg >= P.whiteAvg && spread <= P.grayTol) {
        if (P.clusterOverWhite && haveDominantCluster && colorShare >= P.colorShareOverWhite) {
            tr = clusterR; tg = clusterG; tb = clusterB;
        } else {
            tr = tg = tb = 255;
        }
    } else if (avg <= P.blackAvg && spread <= P.grayTol) {
        tr = tg = tb = 0;
    } else if (spread <= P.grayTol) {
        if (haveDominantCluster) {
            tr = clusterR; tg = clusterG; tb = clusterB;
        } else if (colorShare <= P.bestSatGrayShareMax &&
                   best_sat >= P.satPixelThreshold &&
                   avg > P.blackAvg && avg < P.whiteAvg) {
            tr = best_r; tg = best_g; tb = best_b;
            const int m = std::max({tr, tg, tb});
            if (m > 0) { tr = tr * 255 / m; tg = tg * 255 / m; tb = tb * 255 / m; }
        } else {
            tr = tg = tb = avg;
        }
    } else {
        if (haveDominantCluster) {
            tr = clusterR; tg = clusterG; tb = clusterB;
        } else {
            uint64_t cr = c_r, cg = c_g, cb = c_b;
            if (cr >= cg && cr >= cb && cr > 0) {
                tr = (int)(r_r / cr); tg = (int)(g_r / cr); tb = (int)(b_r / cr);
            } else if (cg >= cr && cg >= cb && cg > 0) {
                tr = (int)(r_g / cg); tg = (int)(g_g / cg); tb = (int)(b_g / cg);
            } else if (cb > 0) {
                tr = (int)(r_b / cb); tg = (int)(g_b / cb); tb = (int)(b_b / cb);
            } else {
                tr = tr0; tg = tg0; tb = tb0;
            }
        }
    }

    if (avg < P.boostThresh) {
        float f = (float)P.boostThresh / (avg ? avg : 1);
        if (f > P.maxBoost) f = P.maxBoost;
        tr = std::min(255, (int)(tr * f));
        tg = std::min(255, (int)(tg * f));
        tb = std::min(255, (int)(tb * f));
    }

    outR = tr; outG = tg; outB = tb;
    return true;
}

// Brightness-follow scaling (persist.gammaos.rgb.scale_with_brightness=1):
// mirrors GammaRgbSampler::postAdjustWithBrightness.
inline void postBrightness(int& r, int& g, int& b, const GammaRgbParams& P) {
    float s = P.brightnessScalar;
    int rawB;
    if (s < 0.f) {
        rawB = (P.rawBrightness < 0) ? 255 : P.rawBrightness;
        s = std::max(0, std::min(255, rawB)) / 255.0f;
    } else {
        rawB = int(s * 255.f + 0.5f);
    }
    if (rawB <= P.brightOverrideThresh) { r = g = b = 1; return; }

    float s_clamped = std::max(0.f, std::min(1.f, s));
    float sbf = powf(s_clamped, P.backlightExp);
    float fr = (r / 255.0f) * sbf;
    float fg = (g / 255.0f) * sbf;
    float fb = (b / 255.0f) * sbf;

    float m = std::max(fr, std::max(fg, fb));
    if (m > 0 && m < (P.minLedFloor / 255.0f)) {
        const float sc = (P.minLedFloor / 255.0f) / m;
        fr *= sc; fg *= sc; fb *= sc;
    }
    float L = .299f * fr + .587f * fg + .114f * fb;
    fr = L + (fr - L) * P.satBoost;
    fg = L + (fg - L) * P.satBoost;
    fb = L + (fb - L) * P.satBoost;

    fr = std::max(0.f, std::min(1.f, fr));
    fg = std::max(0.f, std::min(1.f, fg));
    fb = std::max(0.f, std::min(1.f, fb));
    r = int(fr * 255.f + .5f);
    g = int(fg * 255.f + .5f);
    b = int(fb * 255.f + .5f);
}

// Static LED brightness scaling (scale_with_brightness=0): mirrors
// GammaRgbSampler::applyStaticLedBrightness.
inline void staticBrightness(int& r, int& g, int& b, int ledBrightness) {
    int level = std::max(0, std::min(255, ledBrightness));
    if (level >= 255) return;
    const float scale = level / 255.0f;
    r = std::max(0, std::min(255, int(r * scale)));
    g = std::max(0, std::min(255, int(g * scale)));
    b = std::max(0, std::min(255, int(b * scale)));
}

inline void applyBrightness(int& r, int& g, int& b, const GammaRgbParams& P) {
    if (P.scaleWithBrightness) postBrightness(r, g, b, P);
    else                       staticBrightness(r, g, b, P.ledBrightness);
}

inline std::string toHex(int r, int g, int b) {
    char buf[8];
    snprintf(buf, sizeof(buf), "#%02X%02X%02X",
             std::max(0, std::min(255, r)),
             std::max(0, std::min(255, g)),
             std::max(0, std::min(255, b)));
    return std::string(buf);
}

} // namespace gammargb

#endif // GAMMA_RGB_PROCESS_H
