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

#ifndef EXYNOS_DEVICE_MODULE_H
#define EXYNOS_DEVICE_MODULE_H

#include "DisplayColorModule.h"
#include "ExynosDevice.h"

using namespace displaycolor;

namespace gs101 {
class ColorManager;

class ExynosDeviceModule : public ExynosDevice {
    using GsInterfaceType = gs::ColorDrmBlobFactory::GsInterfaceType;
    public:
        ExynosDeviceModule(bool isVrrApiSupported);
        virtual ~ExynosDeviceModule();

        GsInterfaceType* getDisplayColorInterface() { return mDisplayColorInterface; }
        void setActiveDisplay(uint32_t index) { mActiveDisplay = index; }
        uint32_t getActiveDisplay() const { return mActiveDisplay; }

        ColorManager* getDisplayColorManager(ExynosDisplay* display);

    private:
        int initDisplayColor(const std::vector<displaycolor::DisplayInfo>& display_info);

        GsInterfaceType * mDisplayColorInterface;
        gs::ColorDrmBlobFactory::DcLoaderType mDisplayColorLoader;
        uint32_t mActiveDisplay;
};

}  // namespace gs101

#endif
