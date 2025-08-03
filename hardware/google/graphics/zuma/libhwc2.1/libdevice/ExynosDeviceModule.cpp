/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "ExynosDeviceModule.h"

#include <aidl/android/hardware/graphics/common/Dataspace.h>
#include <aidl/android/hardware/graphics/common/PixelFormat.h>

using DisplayPixelFormat = aidl::android::hardware::graphics::common::PixelFormat;
using Dataspace = aidl::android::hardware::graphics::common::Dataspace;

namespace zuma {

ExynosDeviceModule::ExynosDeviceModule(bool isVrrApiSupported)
      : gs201::ExynosDeviceModule(isVrrApiSupported) {}

ExynosDeviceModule::~ExynosDeviceModule() {}

const ExynosDeviceModule::SupportedBufferCombinations overlay_caps_rgb =
        {{

                 DisplayPixelFormat::RGBA_8888, DisplayPixelFormat::RGBX_8888,
                 DisplayPixelFormat::RGB_888, DisplayPixelFormat::RGB_565,
                 DisplayPixelFormat::BGRA_8888, DisplayPixelFormat::RGBA_FP16,
                 DisplayPixelFormat::RGBA_1010102, DisplayPixelFormat::R_8},
         {Dataspace::STANDARD_BT2020, Dataspace::STANDARD_BT709, Dataspace::STANDARD_DCI_P3},
         {Dataspace::TRANSFER_UNSPECIFIED, Dataspace::TRANSFER_LINEAR, Dataspace::TRANSFER_SRGB,
          Dataspace::TRANSFER_SMPTE_170M, Dataspace::TRANSFER_GAMMA2_2,
          Dataspace::TRANSFER_GAMMA2_6, Dataspace::TRANSFER_GAMMA2_8, Dataspace::TRANSFER_ST2084,
          Dataspace::TRANSFER_HLG},
         {Dataspace::RANGE_FULL}};

const ExynosDeviceModule::SupportedBufferCombinations overlay_caps_yuv =
        {{DisplayPixelFormat::YCRCB_420_SP, DisplayPixelFormat::YV12,
          DisplayPixelFormat::YCBCR_P010},
         {Dataspace::STANDARD_BT2020, Dataspace::STANDARD_BT709, Dataspace::STANDARD_DCI_P3},
         {Dataspace::TRANSFER_UNSPECIFIED, Dataspace::TRANSFER_LINEAR, Dataspace::TRANSFER_SRGB,
          Dataspace::TRANSFER_SMPTE_170M, Dataspace::TRANSFER_GAMMA2_2,
          Dataspace::TRANSFER_GAMMA2_6, Dataspace::TRANSFER_GAMMA2_8, Dataspace::TRANSFER_ST2084,
          Dataspace::TRANSFER_HLG},
         {Dataspace::RANGE_FULL, Dataspace::RANGE_LIMITED}};

int32_t ExynosDeviceModule::getOverlaySupport(OverlayProperties* caps) {
    if (caps == nullptr) {
        ALOGE("%s:: no caps pointer", __func__);
        return NO_ERROR;
    }
    caps->combinations.push_back(overlay_caps_rgb);
    caps->combinations.push_back(overlay_caps_yuv);

    caps->supportMixedColorSpaces = supportMixedColorSpaces;
    return NO_ERROR;
}
} // namespace zuma
