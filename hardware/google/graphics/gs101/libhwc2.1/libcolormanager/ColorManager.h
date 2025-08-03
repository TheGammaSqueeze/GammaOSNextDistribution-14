/*
 * Copyright (C) 2023 The Android Open Source Project
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
#ifndef COLOR_MANAGER_H
#define COLOR_MANAGER_H

#include <displaycolor/displaycolor.h>

#include "DisplaySceneInfo.h"
#include "ExynosDeviceModule.h"
#include "ExynosHwc3Types.h"

using namespace displaycolor;
class ExynosDisplay;

namespace gs101 {

class ExynosDeviceModule;

class ColorManager {
public:
    ColorManager(ExynosDisplay* display, ExynosDeviceModule* device)
          : mExynosDisplay(display), mDevice(device) {}

    DisplaySceneInfo& getDisplaySceneInfo() { return mDisplaySceneInfo; }

    using GsInterfaceType = gs::ColorDrmBlobFactory::GsInterfaceType;
    GsInterfaceType* getDisplayColorInterface() { return mDevice->getDisplayColorInterface(); }

    int32_t getColorModes(uint32_t* outNumModes, int32_t* outModes);
    int32_t setColorMode(int32_t mode);
    int32_t getRenderIntents(int32_t mode, uint32_t* outNumIntents, int32_t* outIntents);
    int32_t setColorModeWithRenderIntent(int32_t mode, int32_t intent);
    int32_t setColorTransform(const float* matrix, int32_t hint);

    /* Call getDppForLayer() only if hasDppForLayer() is true */
    bool hasDppForLayer(ExynosMPPSource* layer);
    const GsInterfaceType::IDpp& getDppForLayer(ExynosMPPSource* layer);
    int32_t getDppIndexForLayer(ExynosMPPSource* layer);
    /* Check if layer's assigned plane id has changed, save the new planeId.
     * call only if hasDppForLayer is true */
    bool checkAndSaveLayerPlaneId(ExynosMPPSource* layer, uint32_t planeId) {
        auto& info = getDisplaySceneInfo().layerDataMappingInfo[layer];
        bool change = info.planeId != planeId;
        info.planeId = planeId;
        return change;
    }

    const GsInterfaceType::IDqe& getDqe();

    int32_t updateColorConversionInfo();
    int32_t resetColorMappingInfo(ExynosMPPSource* mppSrc);

private:
    int32_t setLayersColorData();

    ExynosDisplay* mExynosDisplay;
    ExynosDeviceModule* mDevice;
    DisplaySceneInfo mDisplaySceneInfo;
};

} // namespace gs101

#endif // COLOR_MANAGER_H
