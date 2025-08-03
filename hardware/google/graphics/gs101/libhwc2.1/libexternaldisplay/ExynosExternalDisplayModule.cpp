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
//#define LOG_NDEBUG 0
#include "ExynosExternalDisplayModule.h"
#include "ExynosPrimaryDisplayModule.h"

#ifdef USES_VIRTUAL_DISPLAY
#include "ExynosVirtualDisplayModule.h"
#endif

#include "ExynosDisplayDrmInterfaceModule.h"
#include "ExynosHWCDebug.h"
#include "ExynosHWCHelper.h"

#define SKIP_FRAME_COUNT        3

using namespace gs101;

ExynosExternalDisplayModule::ExynosExternalDisplayModule(uint32_t index, ExynosDevice* device,
                                                         const std::string& displayName)
      : ExynosExternalDisplay(index, device, displayName) {
    mColorManager = std::make_unique<ColorManager>(this, static_cast<ExynosDeviceModule*>(device));
}

ExynosExternalDisplayModule::~ExynosExternalDisplayModule ()
{

}

int32_t ExynosExternalDisplayModule::validateWinConfigData()
{
    bool flagValidConfig = true;

    if (ExynosDisplay::validateWinConfigData() != NO_ERROR)
        flagValidConfig = false;

    for (size_t i = 0; i < mDpuData.configs.size(); i++) {
        struct exynos_win_config_data &config = mDpuData.configs[i];
        if (config.state == config.WIN_STATE_BUFFER) {
            bool configInvalid = false;
            uint32_t mppType = config.assignedMPP->mPhysicalType;
            if ((config.src.w != config.dst.w) ||
                (config.src.h != config.dst.h)) {
                if ((mppType == MPP_DPP_GF) ||
                    (mppType == MPP_DPP_VG) ||
                    (mppType == MPP_DPP_VGF)) {
                    DISPLAY_LOGE("WIN_CONFIG error: invalid assign id : %zu,  s_w : %d, d_w : %d, s_h : %d, d_h : %d, mppType : %d",
                            i, config.src.w, config.dst.w, config.src.h, config.dst.h, mppType);
                    configInvalid = true;
                }
            }
            if (configInvalid) {
                config.state = config.WIN_STATE_DISABLED;
                flagValidConfig = false;
            }
        }
    }
    if (flagValidConfig)
        return NO_ERROR;
    else
        return -EINVAL;
}

int32_t ExynosExternalDisplayModule::getColorModes(uint32_t* outNumModes, int32_t* outModes) {
    return mColorManager->getColorModes(outNumModes, outModes);
}

int32_t ExynosExternalDisplayModule::setColorMode(int32_t mode) {
    return mColorManager->setColorMode(mode);
}

int32_t ExynosExternalDisplayModule::getRenderIntents(int32_t mode, uint32_t* outNumIntents,
                                                      int32_t* outIntents) {
    return mColorManager->getRenderIntents(mode, outNumIntents, outIntents);
}

int32_t ExynosExternalDisplayModule::setColorModeWithRenderIntent(int32_t mode, int32_t intent) {
    return mColorManager->setColorModeWithRenderIntent(mode, intent);
}

int32_t ExynosExternalDisplayModule::setColorTransform(const float* matrix, int32_t hint) {
    return mColorManager->setColorTransform(matrix, hint);
}

int32_t ExynosExternalDisplayModule::updateColorConversionInfo() {
    return mColorManager->updateColorConversionInfo();
}

int32_t ExynosExternalDisplayModule::resetColorMappingInfo(ExynosMPPSource* mppSrc) {
    return mColorManager->resetColorMappingInfo(mppSrc);
}

int ExynosExternalDisplayModule::deliverWinConfigData() {
    int ret = 0;
    ExynosDisplayDrmInterfaceModule* moduleDisplayInterface =
            (ExynosDisplayDrmInterfaceModule*)(mDisplayInterface.get());
    GsInterfaceType* displayColorInterface = getDisplayColorInterface();

    bool forceDisplayColorSetting = false;
    if (!getDisplaySceneInfo().displaySettingDelivered || isForceColorUpdate())
        forceDisplayColorSetting = true;

    setForceColorUpdate(false);

    if (displayColorInterface != nullptr) {
        moduleDisplayInterface
                ->setColorSettingChanged(getDisplaySceneInfo().needDisplayColorSetting(),
                                         forceDisplayColorSetting);
    }

    ret = ExynosDisplay::deliverWinConfigData();

    if (mDpuData.enable_readback && !mDpuData.readback_info.requested_from_service)
        getDisplaySceneInfo().displaySettingDelivered = false;
    else
        getDisplaySceneInfo().displaySettingDelivered = true;

    return ret;
}
