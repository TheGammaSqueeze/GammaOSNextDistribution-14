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
#ifndef EXYNOS_EXTERNAL_DISPLAY_MODULE_H
#define EXYNOS_EXTERNAL_DISPLAY_MODULE_H

#include "ColorManager.h"
#include "ExynosDisplay.h"
#include "ExynosExternalDisplay.h"
#include "ExynosLayer.h"

namespace gs101 {

class ColorManager;

class ExynosExternalDisplayModule : public ExynosExternalDisplay {
    using GsInterfaceType = gs::ColorDrmBlobFactory::GsInterfaceType;

public:
    ExynosExternalDisplayModule(uint32_t index, ExynosDevice* device,
                                const std::string& displayName);
    ~ExynosExternalDisplayModule();
    virtual int32_t validateWinConfigData();

    ColorManager* getColorManager() { return mColorManager.get(); }

    int32_t getColorModes(uint32_t* outNumModes, int32_t* outModes) override;
    int32_t setColorMode(int32_t mode) override;
    int32_t getRenderIntents(int32_t mode, uint32_t* outNumIntents, int32_t* outIntents) override;
    int32_t setColorModeWithRenderIntent(int32_t mode, int32_t intent) override;
    int32_t setColorTransform(const float* matrix, int32_t hint) override;

    int32_t updateColorConversionInfo() override;
    int32_t resetColorMappingInfo(ExynosMPPSource* mppSrc) override;

    bool mForceColorUpdate = false;
    bool isForceColorUpdate() const { return mForceColorUpdate; }
    void setForceColorUpdate(bool force) { mForceColorUpdate = force; }
    int deliverWinConfigData() override;

private:
    std::unique_ptr<ColorManager> mColorManager;

    GsInterfaceType* getDisplayColorInterface() {
        return mColorManager->getDisplayColorInterface();
    }

    DisplaySceneInfo& getDisplaySceneInfo() { return mColorManager->getDisplaySceneInfo(); }
};

}  // namespace gs101

#endif
