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

#ifndef EXYNOS_DISPLAY_DRM_INTERFACE_MODULE_GS201_H
#define EXYNOS_DISPLAY_DRM_INTERFACE_MODULE_GS201_H

#include <drm/samsung_drm.h>

#include "../../gs101/libhwc2.1/libdisplayinterface/ExynosDisplayDrmInterfaceModule.h"

namespace gs201 {

using namespace displaycolor;

class ExynosDisplayDrmInterfaceModule : public gs101::ExynosDisplayDrmInterfaceModule {
    static constexpr size_t sizeCgcDmaLut = 2 * 3 * DRM_SAMSUNG_CGC_DMA_LUT_ENTRY_CNT;	// 16bit BGR
    static constexpr int32_t disabledCgc = -1;
    static constexpr size_t sizeCgCDataInfo = 2;
    using GsInterfaceType = gs::ColorDrmBlobFactory::GsInterfaceType;
    public:
        ExynosDisplayDrmInterfaceModule(ExynosDisplay *exynosDisplay);
        virtual ~ExynosDisplayDrmInterfaceModule();
        virtual int32_t initDrmDevice(DrmDevice *drmDevice);

        virtual int32_t setDisplayColorSetting(
                ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq);

        /* For Histogram */
        virtual int32_t setDisplayHistogramSetting(
            ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq) override;
        virtual void registerHistogramInfo(const std::shared_ptr<IDLHistogram> &info) override;

    private:
        int32_t createCgcDMAFromIDqe(const GsInterfaceType::IDqe::CgcData &cgcData);
        int32_t setCgcLutDmaProperty(const DrmProperty &prop,
                                     ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq);
        /* For Histogram */
        int32_t setHistoPosProperty(
            const DrmProperty &prop,
            ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq);

        bool mCgcEnabled = false;

        using CGCDataInfo = std::pair<int32_t, struct cgc_dma_lut *>;
        std::vector<CGCDataInfo> mCGCDataInfos;
        size_t iCGCDataInfo = 0;
};

class ExynosPrimaryDisplayDrmInterfaceModule : public ExynosDisplayDrmInterfaceModule {
    public:
        ExynosPrimaryDisplayDrmInterfaceModule(ExynosDisplay *exynosDisplay);
        virtual ~ExynosPrimaryDisplayDrmInterfaceModule();
};

using ExynosExternalDisplayDrmInterfaceModule = gs101::ExynosExternalDisplayDrmInterfaceModule;

}  // namespace gs201

#endif // EXYNOS_DISPLAY_DRM_INTERFACE_MODULE_GS201_H
