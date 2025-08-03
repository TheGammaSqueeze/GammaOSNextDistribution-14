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

#include "ExynosMPPModule.h"

#include "ExynosHWCDebug.h"
#include "ExynosPrimaryDisplayModule.h"
#include "ExynosResourceManager.h"

using namespace gs201;

ExynosMPPModule::ExynosMPPModule(ExynosResourceManager *resourceManager, uint32_t physicalType,
                                 uint32_t logicalType, const char *name, uint32_t physicalIndex,
                                 uint32_t logicalIndex, uint32_t preAssignInfo)
      : gs101::ExynosMPPModule(resourceManager, physicalType, logicalType, name, physicalIndex,
                               logicalIndex, preAssignInfo) {}

ExynosMPPModule::~ExynosMPPModule() {}

/* This function is used to restrict case that current MIF voting can't cover
 * it. Once a solution is ready, the restriction need to be removed.
 */
bool ExynosMPPModule::checkSpecificRestriction(const uint32_t refreshRate,
                                               const struct exynos_image &src,
                                               const struct exynos_image &dst) {
    /* additional restriction for composer in high refresh rate */
    if (mPhysicalType < MPP_DPP_NUM && refreshRate >= 90) {
        VendorGraphicBufferMeta gmeta(src.bufferHandle);

        if (isFormatYUV(gmeta.format)) {
            // 16:9 4k or large YUV layer
            if (src.w >= 3584 && src.h >= 1600) {
                return true;
            }
            // 9:16 4k or large YUV layer
            if (src.h >= 2600 && src.w >= 1450 && src.h > dst.h && (dst.h * 100 / src.h) < 67) {
                return true;
            }
        } else if (src.w >= 1680 && src.h > dst.h && (dst.h * 100 / src.h) < 60) {
            // vertical downscale RGB layer
            return true;
        }
    }

    return ExynosMPP::checkSpecificRestriction(refreshRate, src, dst);
}

int64_t ExynosMPPModule::isSupported(ExynosDisplay &display,
                                     struct exynos_image &src,
                                     struct exynos_image &dst) {
    if (mPhysicalType < MPP_DPP_NUM && src.bufferHandle != nullptr) {
        const uint32_t refreshRate = display.getBtsRefreshRate();

        if (checkSpecificRestriction(refreshRate, src, dst)) {
            return -eMPPSatisfiedRestriction;
        }
    }

    return ExynosMPP::isSupported(display, src, dst);
}
