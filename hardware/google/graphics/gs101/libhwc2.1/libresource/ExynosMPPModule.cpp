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

#include "ExynosMPPModule.h"
#include "ExynosHWCDebug.h"
#include "ExynosResourceManager.h"
#include "ExynosPrimaryDisplayModule.h"

using namespace gs101;

ExynosMPPModule::ExynosMPPModule(ExynosResourceManager* resourceManager,
        uint32_t physicalType, uint32_t logicalType, const char *name,
        uint32_t physicalIndex, uint32_t logicalIndex, uint32_t preAssignInfo)
    : ExynosMPP(resourceManager, physicalType, logicalType, name, physicalIndex, logicalIndex, preAssignInfo),
    mChipId(0x00)
{
    if (mLogicalType == MPP_LOGICAL_G2D_RGB)
        mEnable = false;
}

ExynosMPPModule::~ExynosMPPModule()
{
}

uint32_t ExynosMPPModule::getSrcXOffsetAlign(struct exynos_image &src)
{
    uint32_t idx = getRestrictionClassification(src);
    return mSrcSizeRestrictions[idx].cropXAlign;
}

int32_t ExynosMPPModule::setColorConversionInfo()
{
    if (mAssignedDisplay == nullptr) {
        MPP_LOGE("%s: mAssignedDisplay is null", __func__);
        return -EINVAL;
    }

    ExynosDeviceModule* device = static_cast<ExynosDeviceModule*>(mAssignedDisplay->mDevice);
    ColorManager* colorManager = device->getDisplayColorManager(mAssignedDisplay);
    if (colorManager == nullptr) {
        MPP_LOGE("%s: colorManager is null", __func__);
        return -EINVAL;
    }
    if (!colorManager->getDisplayColorInterface()) {
        return NO_ERROR;
    }

    for (size_t i = 0; i < mAssignedSources.size(); i++) {
        auto mppSource = mAssignedSources[i];
        ExynosLayer* layer = (ExynosLayer*)mppSource;
        AcrylicLayer* mppLayer = mSrcImgs[i].mppLayer;
        if ((mppSource == nullptr) || (layer == nullptr) ||
            (mppLayer == nullptr)) {
            MPP_LOGE("%s: src[%zu] source layer is null", __func__, i);
            return -EINVAL;
        }
        if (mppSource->mSrcImg.dataSpace == mppSource->mMidImg.dataSpace) {
            //set null layer data to acryl
            mppLayer->setLayerData(nullptr, 0);
            continue;
        }
        if (colorManager->hasDppForLayer(layer) == false) {
            MPP_LOGE("%s: src[%zu] need color conversion but there is no IDpp", __func__, i);
            return -EINVAL;
        }
        MPP_LOGD(eDebugColorManagement,
                "%s, src: 0x%8x", __func__, mppSource->mSrcImg.dataSpace);
        const auto& dpp = colorManager->getDppForLayer(layer);
        mppLayer->setLayerData((void *)&dpp, sizeof(dpp));
    }
    return NO_ERROR;
}
