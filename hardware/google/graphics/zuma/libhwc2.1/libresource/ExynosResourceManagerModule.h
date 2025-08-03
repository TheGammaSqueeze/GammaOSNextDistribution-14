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

#ifndef _EXYNOS_RESOURCE_MANAGER_MODULE_ZUMA_H
#define _EXYNOS_RESOURCE_MANAGER_MODULE_ZUMA_H

#include "../../gs201/libhwc2.1/libresource/ExynosResourceManagerModule.h"

namespace zuma {

class ExynosResourceManagerModule : public gs201::ExynosResourceManagerModule {
    public:
        ExynosResourceManagerModule(ExynosDevice *device);
        ~ExynosResourceManagerModule();

        /* TDM (Time-Division Multiplexing) based Resource Management */
        virtual bool isHWResourceAvailable(ExynosDisplay *display, ExynosMPP *currentMPP,
                                           ExynosMPPSource *mppSrc);
        virtual uint32_t setDisplaysTDMInfo(ExynosDisplay* mainDisp, ExynosDisplay* minorDisp);
        virtual uint32_t calculateHWResourceAmount(ExynosDisplay *display, ExynosMPPSource *mppSrc);
        virtual int32_t otfMppReordering(ExynosDisplay *display, ExynosMPPVector &otfMPPs,
                                         struct exynos_image &src, struct exynos_image &dst);

        bool isOverlapped(ExynosDisplay *display, ExynosMPPSource *current,
                          ExynosMPPSource *compare);
        uint32_t getAmounts(ExynosDisplay* display, uint32_t currentBlockId, uint32_t currentAXIId,
                            ExynosMPP* compOtfMPP, ExynosMPPSource* curSrc,
                            ExynosMPPSource* compSrc,
                            std::array<uint32_t, TDM_ATTR_MAX>& DPUFAmounts,
                            std::array<uint32_t, TDM_ATTR_MAX>& AXIAmounts);
        bool checkTDMResource(ExynosDisplay *display, ExynosMPP *currentMPP,
                              ExynosMPPSource *mppSrc);
        const std::map<HWResourceIndexes, HWResourceAmounts_t> *mHWResourceTables = nullptr;
        void setupHWResource(const tdm_attr_t& tdmAttrId, const String8& name,
                             const DPUblockId_t& blkId, const AXIPortId_t& axiId,
                             ExynosDisplay* mainDisp, ExynosDisplay* minorDisp,
                             const ConstraintRev_t& constraintsRev);
        virtual bool isAssignable(ExynosMPP* candidateMPP, ExynosDisplay* display,
                                  struct exynos_image& src, struct exynos_image& dst,
                                  ExynosMPPSource* mppSrc);

    private:
        ConstraintRev_t mConstraintRev;
};

}  // namespace zuma

#endif // _EXYNOS_RESOURCE_MANAGER_MODULE_ZUMA_H
