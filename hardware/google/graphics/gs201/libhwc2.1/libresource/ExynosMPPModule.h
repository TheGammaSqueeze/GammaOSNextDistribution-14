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

#ifndef _EXYNOS_MPP_MODULE_GS201_H
#define _EXYNOS_MPP_MODULE_GS201_H

#include "../../gs101/libhwc2.1/libresource/ExynosMPPModule.h"

namespace gs201 {

class ExynosMPPModule : public gs101::ExynosMPPModule {
    public:
        ExynosMPPModule(ExynosResourceManager* resourceManager, uint32_t physicalType,
                        uint32_t logicalType, const char *name,
                        uint32_t physicalIndex, uint32_t logicalIndex,
                        uint32_t preAssignInfo);
        ~ExynosMPPModule();
        virtual int64_t isSupported(ExynosDisplay &display, struct exynos_image &src,
                                    struct exynos_image &dst);
        virtual bool checkSpecificRestriction(const uint32_t __unused refreshRate,
                                              const struct exynos_image &src,
                                              const struct exynos_image &dst);
};

}  // namespace gs201

#endif // _EXYNOS_MPP_MODULE_GS201_H
