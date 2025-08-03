/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <android-base/logging.h>
// hwc2 types
#include <hardware/hwcomposer2.h>
// new hwc3 types used by exynosdisplay
#include "ExynosHwc3Types.h"
// aidl types
#include "include/IComposerHal.h"

namespace aidl::android::hardware::graphics::composer3::impl {

// hwc2 to aidl conversion
namespace h2a {

template <typename T, typename U>
inline void translate(const T& in, U& out) {
    out = static_cast<U>(in);
}

template <typename T, typename U>
inline void translate(const std::vector<T>& in, std::vector<U>& out) {
    out.clear();
    for (auto const &t : in) {
        U u;
        translate(t, u);
        out.emplace_back(std::move(u));
    }
}

template<>
inline void translate(const hwc_vsync_period_change_timeline_t& in,
                      VsyncPeriodChangeTimeline& out) {
    out.newVsyncAppliedTimeNanos = in.newVsyncAppliedTimeNanos;
    out.refreshRequired = in.refreshRequired;
    out.refreshTimeNanos = in.refreshTimeNanos;
}

template<>
inline void translate(const int32_t& fd, ndk::ScopedFileDescriptor& sfd) {
    // ownership of fd is transferred to sfd
    sfd = ndk::ScopedFileDescriptor(fd);
}

template<>
inline void translate(const hwc_client_target_property& in, ClientTargetProperty& out) {
    translate(in.pixelFormat, out.pixelFormat);
    translate(in.dataspace, out.dataspace);
}

template<>
inline void translate(const HwcMountOrientation& in, common::Transform& out) {
    switch (in) {
    case HwcMountOrientation::ROT_0:
        out = common::Transform::NONE;
        break;

    case HwcMountOrientation::ROT_90:
        out = common::Transform::ROT_90;
        break;

    case HwcMountOrientation::ROT_180:
        out = common::Transform::ROT_180;
        break;

    case HwcMountOrientation::ROT_270:
        out = common::Transform::ROT_270;
        break;

    default:
        LOG(WARNING) << "unrecoganized display orientation: " << static_cast<uint32_t>(in);
        out = common::Transform::NONE;
        break;
    }
}

} // namespace h2a

// aidl to hwc2 conversion
namespace a2h {

template <typename T, typename U>
inline void translate(const T& in, U& out) {
    out = static_cast<U>(in);
}

template <typename T, typename U>
inline void translate(const std::vector<std::optional<T>>& in, std::vector<U>& out) {
    out.clear();
    for (auto const &t : in) {
        U u;
        if (t) {
            translate(*t, u);
            out.emplace_back(std::move(u));
        }
    }
}

template <typename T, typename U>
inline void translate(const std::vector<T>& in, std::vector<U>& out) {
    out.clear();
    for (auto const &t : in) {
        U u;
        translate(t, u);
        out.emplace_back(std::move(u));
    }
}

template<>
inline void translate(const common::Rect& in, hwc_rect_t& out) {
     out.left = in.left;
     out.top = in.top;
     out.right =in.right;
     out.bottom =in.bottom;
}

template<>
inline void translate(const common::FRect& in, hwc_frect_t& out) {
     out.left = in.left;
     out.top = in.top;
     out.right =in.right;
     out.bottom =in.bottom;
}

template <>
inline void translate(const VsyncPeriodChangeConstraints& in,
                      hwc_vsync_period_change_constraints_t& out) {
    out.desiredTimeNanos = in.desiredTimeNanos;
    out.seamlessRequired = in.seamlessRequired;
}

template<>
inline void translate(const ndk::ScopedFileDescriptor& in, int32_t& out) {
    // it's not defined as const. drop the const to avoid dup
    auto& sfd = const_cast<ndk::ScopedFileDescriptor&>(in);
    // take the ownership
    out = sfd.get();
    *sfd.getR() = -1;
}

template<>
inline void translate(const bool& in, hwc2_vsync_t& out) {
    // HWC_VSYNC_DISABLE is 2
    out = in ? HWC2_VSYNC_ENABLE : HWC2_VSYNC_DISABLE;
}

template<>
inline void translate(const Color& in, hwc_color_t& out) {
    const auto floatColorToUint8Clamped = [](float val) -> uint8_t {
        const auto intVal = static_cast<uint64_t>(std::round(255.0f * val));
        const auto minVal = static_cast<uint64_t>(0);
        const auto maxVal = static_cast<uint64_t>(255);
        return std::clamp(intVal, minVal, maxVal);
    };

    out.r = floatColorToUint8Clamped(in.r);
    out.g = floatColorToUint8Clamped(in.g);
    out.b = floatColorToUint8Clamped(in.b);
    out.a = floatColorToUint8Clamped(in.a);
}

} // namespace a2h

} // namespace aidl::android::hardware::graphics::composer3::impl
