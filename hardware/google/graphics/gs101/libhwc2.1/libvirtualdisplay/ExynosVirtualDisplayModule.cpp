/*
 * Copyright (C) 2019 The Android Open Source Project
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
#undef LOG_TAG
#define LOG_TAG "hwc-virt-display"

#include "ExynosVirtualDisplayModule.h"

using namespace gs101;

ExynosVirtualDisplayModule::ExynosVirtualDisplayModule(uint32_t index, ExynosDevice* device,
                                                       const std::string& displayName)
      : ExynosVirtualDisplay(index, device, displayName) {
    mGLESFormat = HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M;

    if (device == NULL) {
        ALOGE("Display creation failed!");
    }

    mBaseWindowIndex = 0;
    mMaxWindowNum = 0;
}

ExynosVirtualDisplayModule::~ExynosVirtualDisplayModule ()
{

}

int32_t ExynosVirtualDisplayModule::getDisplayAttribute(
    hwc2_config_t __unused config,
    int32_t /*hwc2_attribute_t*/ __unused attribute, int32_t* __unused outValue)
{
    switch(attribute) {
        default:
            ALOGE("unknown display attribute %u", attribute);
            return -EINVAL;
    }
    return HWC2_ERROR_NONE;
}

