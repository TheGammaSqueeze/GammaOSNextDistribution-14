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
#ifndef EXYNOS_VIRTUAL_DISPLAY_MODULE_H
#define EXYNOS_VIRTUAL_DISPLAY_MODULE_H

#include "ExynosDisplay.h"
#include "ExynosVirtualDisplay.h"

namespace gs101 {

class ExynosVirtualDisplayModule : public ExynosVirtualDisplay {
public:
    ExynosVirtualDisplayModule(uint32_t index, ExynosDevice* device,
                               const std::string& displayName);
    ~ExynosVirtualDisplayModule();

    /* getDisplayAttribute(..., config, attribute, outValue)
     * Descriptor: HWC2_FUNCTION_GET_DISPLAY_ATTRIBUTE
     * HWC2_PFN_GET_DISPLAY_ATTRIBUTE
     */
    virtual int32_t getDisplayAttribute(
            hwc2_config_t config,
            int32_t /*hwc2_attribute_t*/ attribute, int32_t* outValue);
};

}

#endif
