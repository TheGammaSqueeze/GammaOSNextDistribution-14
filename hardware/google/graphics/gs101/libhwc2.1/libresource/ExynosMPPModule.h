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
#ifndef _EXYNOS_MPP_MODULE_H
#define _EXYNOS_MPP_MODULE_H

#include "ExynosMPP.h"

#define MAX_DPP_ROT_SRC_SIZE (3040*1440)

namespace gs101 {

class ExynosMPPModule : public ExynosMPP {
    public:
        ExynosMPPModule(ExynosResourceManager* resourceManager, uint32_t physicalType, uint32_t logicalType, const char *name,
            uint32_t physicalIndex, uint32_t logicalIndex, uint32_t preAssignInfo);
        ~ExynosMPPModule();
        virtual uint32_t getSrcXOffsetAlign(struct exynos_image &src);
        virtual int32_t setColorConversionInfo();
    public:
        uint32_t mChipId;
};

}  // namespace gs101

#endif
