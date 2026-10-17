// Copyright 2016 Citra Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#include <algorithm>
#include <array>
#include <cstddef>
#include "audio_core/hle/common.h"
#include "audio_core/hle/filter.h"
#include "audio_core/hle/shared_memory.h"
#include "common/common_types.h"
#include "common/gammaos_tweak.h"

namespace AudioCore::HLE {

void SourceFilters::Reset() {
    Enable(false, false);
}

void SourceFilters::Enable(bool simple, bool biquad) {
    simple_filter_enabled = simple;
    biquad_filter_enabled = biquad;

    if (!simple)
        simple_filter.Reset();
    if (!biquad)
        biquad_filter.Reset();
}

void SourceFilters::Configure(SourceConfiguration::Configuration::SimpleFilter config) {
    simple_filter.Configure(config);
}

void SourceFilters::Configure(SourceConfiguration::Configuration::BiquadFilter config) {
    biquad_filter.Configure(config);
}

void SourceFilters::ProcessFrame(StereoFrame16& frame) {
    if (!simple_filter_enabled && !biquad_filter_enabled)
        return;

    // persist.gammaos.azahar_nano.audio_fast=0 keeps the per sample calls; default Mali only.
    static const bool whole_frame =
        Common::GammaOS::TweakEnabled("persist.gammaos.azahar_nano.audio_fast");

    if (simple_filter_enabled) {
        if (whole_frame) {
            simple_filter.ProcessFrame(frame);
        } else {
            FilterFrame(frame, simple_filter);
        }
    }

    if (biquad_filter_enabled) {
        if (whole_frame) {
            biquad_filter.ProcessFrame(frame);
        } else {
            FilterFrame(frame, biquad_filter);
        }
    }
}

// SimpleFilter

void SourceFilters::SimpleFilter::Reset() {
    y1.fill(0);
    // Configure as passthrough.
    a1 = 0;
    b0 = 1 << 15;
}

void SourceFilters::SimpleFilter::Configure(
    SourceConfiguration::Configuration::SimpleFilter config) {

    a1 = config.a1;
    b0 = config.b0;
}

std::array<s16, 2> SourceFilters::SimpleFilter::ProcessSample(const std::array<s16, 2>& x0) {
    std::array<s16, 2> y0;
    for (std::size_t i = 0; i < 2; i++) {
        const s32 tmp = (b0 * x0[i] + a1 * y1[i]) >> 15;
        y0[i] = std::clamp(tmp, -32768, 32767);
    }

    y1 = y0;

    return y0;
}

// BiquadFilter

void SourceFilters::BiquadFilter::Reset() {
    x1.fill(0);
    x2.fill(0);
    y1.fill(0);
    y2.fill(0);
    // Configure as passthrough.
    a1 = a2 = b1 = b2 = 0;
    b0 = 1 << 14;
}

void SourceFilters::BiquadFilter::Configure(
    SourceConfiguration::Configuration::BiquadFilter config) {

    a1 = config.a1;
    a2 = config.a2;
    b0 = config.b0;
    b1 = config.b1;
    b2 = config.b2;
}

std::array<s16, 2> SourceFilters::BiquadFilter::ProcessSample(const std::array<s16, 2>& x0) {
    std::array<s16, 2> y0;
    for (std::size_t i = 0; i < 2; i++) {
        const s32 tmp = (b0 * x0[i] + b1 * x1[i] + b2 * x2[i] + a1 * y1[i] + a2 * y2[i]) >> 14;
        y0[i] = std::clamp(tmp, -32768, 32767);
    }

    x2 = x1;
    x1 = x0;
    y2 = y1;
    y1 = y0;

    return y0;
}

void SourceFilters::SimpleFilter::ProcessFrame(StereoFrame16& frame) {
    s32 y1_0 = y1[0], y1_1 = y1[1];
    for (auto& sample : frame) {
        const s32 t0 = (b0 * sample[0] + a1 * y1_0) >> 15;
        const s32 t1 = (b0 * sample[1] + a1 * y1_1) >> 15;
        y1_0 = static_cast<s16>(std::clamp(t0, -32768, 32767));
        y1_1 = static_cast<s16>(std::clamp(t1, -32768, 32767));
        sample[0] = static_cast<s16>(y1_0);
        sample[1] = static_cast<s16>(y1_1);
    }
    y1 = {static_cast<s16>(y1_0), static_cast<s16>(y1_1)};
}

void SourceFilters::BiquadFilter::ProcessFrame(StereoFrame16& frame) {
    s32 x1_0 = x1[0], x1_1 = x1[1], x2_0 = x2[0], x2_1 = x2[1];
    s32 y1_0 = y1[0], y1_1 = y1[1], y2_0 = y2[0], y2_1 = y2[1];
    for (auto& sample : frame) {
        const s32 x0_0 = sample[0], x0_1 = sample[1];
        const s32 t0 = (b0 * x0_0 + b1 * x1_0 + b2 * x2_0 + a1 * y1_0 + a2 * y2_0) >> 14;
        const s32 t1 = (b0 * x0_1 + b1 * x1_1 + b2 * x2_1 + a1 * y1_1 + a2 * y2_1) >> 14;
        const s32 y0_0 = static_cast<s16>(std::clamp(t0, -32768, 32767));
        const s32 y0_1 = static_cast<s16>(std::clamp(t1, -32768, 32767));
        x2_0 = x1_0;
        x2_1 = x1_1;
        x1_0 = x0_0;
        x1_1 = x0_1;
        y2_0 = y1_0;
        y2_1 = y1_1;
        y1_0 = y0_0;
        y1_1 = y0_1;
        sample[0] = static_cast<s16>(y0_0);
        sample[1] = static_cast<s16>(y0_1);
    }
    x1 = {static_cast<s16>(x1_0), static_cast<s16>(x1_1)};
    x2 = {static_cast<s16>(x2_0), static_cast<s16>(x2_1)};
    y1 = {static_cast<s16>(y1_0), static_cast<s16>(y1_1)};
    y2 = {static_cast<s16>(y2_0), static_cast<s16>(y2_1)};
}

} // namespace AudioCore::HLE
