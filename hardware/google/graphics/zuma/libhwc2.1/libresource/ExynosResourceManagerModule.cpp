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

#include "ExynosResourceManagerModule.h"

#include <cutils/properties.h>

#include <list>
#include <utility>

#include "ExynosLayer.h"

using namespace zuma;

constexpr uint32_t TDM_OVERLAP_MARGIN = 68;

constexpr uint32_t kSramSBWCWidthAlign = 32;
constexpr uint32_t kSramSBWCWidthMargin = kSramSBWCWidthAlign - 1;
constexpr uint32_t kSramSBWCRotWidthAlign = 4;
constexpr uint32_t kSramAFBC8B4BAlign = 8;
constexpr uint32_t kSramAFBC8B4BMargin = kSramAFBC8B4BAlign - 1;
constexpr uint32_t kSramAFBC2BAlign = 16;
constexpr uint32_t kSramAFBC2BMargin = kSramAFBC2BAlign - 1;

ExynosResourceManagerModule::ExynosResourceManagerModule(ExynosDevice *device)
: gs201::ExynosResourceManagerModule(device)
{
    // HW Resource Table for TDM based allocation
    mHWResourceTables = &HWResourceTables;

    char value[PROPERTY_VALUE_MAX];
    property_get("ro.boot.hw.soc.rev", value, "2");
    const int socRev = atoi(value);
    mConstraintRev = socRev < 2 ? CONSTRAINT_A0 : CONSTRAINT_B0;
    HWAttrs.at(TDM_ATTR_WCG).loadSharing =
            (mConstraintRev == CONSTRAINT_A0) ? LS_DPUF : LS_DPUF_AXI;
    ALOGD("%s(): ro.boot.hw.soc.rev=%s ConstraintRev=%d", __func__, value, mConstraintRev);
}

ExynosResourceManagerModule::~ExynosResourceManagerModule() {}

bool ExynosResourceManagerModule::checkTDMResource(ExynosDisplay *display, ExynosMPP *currentMPP,
                                                   ExynosMPPSource *mppSrc) {
    std::array<uint32_t, TDM_ATTR_MAX> accumulatedDPUFAmount{};
    std::array<uint32_t, TDM_ATTR_MAX> accumulatedDPUFAXIAmount{};
    const uint32_t blkId = currentMPP->getHWBlockId();
    const uint32_t axiId = currentMPP->getAXIPortId();
    HDEBUGLOGD(eDebugTDM, "%s : %p trying to assign to %s, compare with layers", __func__,
               mppSrc->mSrcImg.bufferHandle, currentMPP->mName.c_str());
    ExynosLayer *layer = (mppSrc->mSourceType == MPP_SOURCE_LAYER) ? (ExynosLayer *)mppSrc : nullptr;

    for (auto compLayer : display->mLayers) {
        ExynosMPP *otfMPP = compLayer->mOtfMPP;
        if (!otfMPP || layer == compLayer) continue;
        getAmounts(display, blkId, axiId, otfMPP, mppSrc, compLayer,
                   accumulatedDPUFAmount, accumulatedDPUFAXIAmount);
    }

    if (display->mExynosCompositionInfo.mHasCompositionLayer) {
        HDEBUGLOGD(eDebugTDM,
                   "%s : %p trying to assign to %s, compare with ExynosComposition Target buffer",
                   __func__, mppSrc->mSrcImg.bufferHandle, currentMPP->mName.c_str());
        ExynosMPP *otfMPP = display->mExynosCompositionInfo.mOtfMPP;
        if (otfMPP)
            getAmounts(display, blkId, axiId, otfMPP, mppSrc, &display->mExynosCompositionInfo,
                       accumulatedDPUFAmount, accumulatedDPUFAXIAmount);
    }

    if (display->mClientCompositionInfo.mHasCompositionLayer) {
        HDEBUGLOGD(eDebugTDM,
                   "%s : %p trying to assign to %s, compare with ClientComposition Target buffer",
                   __func__, mppSrc->mSrcImg.bufferHandle, currentMPP->mName.c_str());
        ExynosMPP *otfMPP = display->mClientCompositionInfo.mOtfMPP;
        if (otfMPP)
            getAmounts(display, blkId, axiId, otfMPP, mppSrc, &display->mClientCompositionInfo,
                       accumulatedDPUFAmount, accumulatedDPUFAXIAmount);
    }

    for (auto attr = HWAttrs.begin(); attr != HWAttrs.end(); attr++) {
        const LoadSharing_t &loadSharing = attr->second.loadSharing;
        uint32_t currentAmount = mppSrc->getHWResourceAmount(attr->first);
        auto &accumulatedAmount =
                (loadSharing == LS_DPUF) ? accumulatedDPUFAmount : accumulatedDPUFAXIAmount;
        const auto &TDMInfoIdx =
                std::make_pair(blkId,
                               (loadSharing == LS_DPUF) ? AXI_DONT_CARE : axiId);
        int32_t totalAmount =
                display->mDisplayTDMInfo[TDMInfoIdx].getAvailableAmount(attr->first).totalAmount;
        HDEBUGLOGD(eDebugTDM,
                   "%s, layer[%p] -> %s attr[%s],ls=%d,accumulated:%d,current:%d,total: %d",
                   __func__, mppSrc->mSrcImg.bufferHandle, currentMPP->mName.c_str(),
                   attr->second.name.c_str(), loadSharing, accumulatedAmount[attr->first],
                   currentAmount, totalAmount);
        if (accumulatedAmount[attr->first] + currentAmount > totalAmount) {
            HDEBUGLOGD(eDebugTDM, "%s, %s could not assigned by attr[%s]", __func__,
                       currentMPP->mName.c_str(), attr->second.name.c_str());
            return false;
        }
    }

    HDEBUGLOGD(eDebugTDM, "%s : %p trying to assign to %s successfully", __func__,
               mppSrc->mSrcImg.bufferHandle, currentMPP->mName.c_str());
    return true;
}

bool ExynosResourceManagerModule::isHWResourceAvailable(ExynosDisplay *display,
                                                        ExynosMPP *currentMPP,
                                                        ExynosMPPSource *mppSrc) {
    if (!checkTDMResource(display, currentMPP, mppSrc)) {
        return false;
    }

    std::list<ExynosLayer *> overlappedLayers;
    uint32_t currentBlockId = currentMPP->getHWBlockId();
    for (auto layer : display->mLayers) {
        ExynosMPP *otfMPP = layer->mOtfMPP;
        if (!otfMPP || dynamic_cast<ExynosMPPSource *>(layer) == mppSrc) continue;

        if ((currentBlockId == otfMPP->getHWBlockId()) && isOverlapped(display, mppSrc, layer))
            overlappedLayers.push_back(layer);
    }

    if (overlappedLayers.size()) {
        HDEBUGLOGD(eDebugTDM,
                   "%s : %p trying to assign to %s, check its overlapped layers(%zu) status",
                   __func__, mppSrc->mSrcImg.bufferHandle, currentMPP->mName.c_str(),
                   overlappedLayers.size());

        for (auto &overlappedLayer : overlappedLayers) {
            HDEBUGLOGD(eDebugTDM, "%s : %p overlapped %p", __func__, mppSrc->mSrcImg.bufferHandle,
                       overlappedLayer->mLayerBuffer);
            if (!checkTDMResource(display, overlappedLayer->mOtfMPP, overlappedLayer)) {
                return false;
            }
        }
    }
    return true;
}

void ExynosResourceManagerModule::setupHWResource(const tdm_attr_t& tdmAttrId, const String8& name,
                                                  const DPUblockId_t& blkId,
                                                  const AXIPortId_t& axiId, ExynosDisplay* mainDisp,
                                                  ExynosDisplay* minorDisp,
                                                  const ConstraintRev_t& constraintsRev) {
    const auto& resourceIdx = HWResourceIndexes(tdmAttrId, blkId, axiId, constraintsRev);
    const auto &iter = mHWResourceTables->find(resourceIdx);
    if (iter != mHWResourceTables->end()) {
        auto &hwResource = iter->second;
        const auto &TDMInfoIdx = (HWAttrs.at(tdmAttrId).loadSharing == LS_DPUF)
                ? std::make_pair(blkId, AXI_DONT_CARE)
                : std::make_pair(blkId, axiId);
        if (mainDisp != nullptr) {
            const uint32_t mainAmount = (minorDisp != nullptr)
                    ? hwResource.mainAmount
                    : hwResource.mainAmount + hwResource.minorAmount;
            mainDisp->mDisplayTDMInfo[TDMInfoIdx]
                    .initTDMInfo(DisplayTDMInfo::ResourceAmount_t{mainAmount}, tdmAttrId);
        }
        if (minorDisp != nullptr) {
            const uint32_t minorAmount = hwResource.minorAmount;
            minorDisp->mDisplayTDMInfo[TDMInfoIdx]
                    .initTDMInfo(DisplayTDMInfo::ResourceAmount_t{minorAmount}, tdmAttrId);
        }
    } else {
        ALOGW("(%s): cannot find resource for %s", resourceIdx.toString8().c_str(), name.c_str());
    }
}

uint32_t ExynosResourceManagerModule::setDisplaysTDMInfo(ExynosDisplay* mainDisp,
                                                         ExynosDisplay* minorDisp) {
    /*
     * Update main/minor display resource amount
     * If only one display exists, all TDM resources are allocated for the only display.
     */
    for (auto attr = HWAttrs.begin(); attr != HWAttrs.end(); attr++) {
        for (auto blockId = DPUBlocks.begin(); blockId != DPUBlocks.end(); blockId++) {
            if (attr->second.loadSharing == LS_DPUF) {
                setupHWResource(attr->first, attr->second.name, blockId->first, AXI_DONT_CARE,
                                mainDisp, minorDisp, mConstraintRev);
            } else if (attr->second.loadSharing == LS_DPUF_AXI) {
                for (auto axi = AXIPorts.begin(); axi != AXIPorts.end(); ++axi) {
                    setupHWResource(attr->first, attr->second.name, blockId->first, axi->first,
                                    mainDisp, minorDisp, mConstraintRev);
                }
            }
            else {
                ALOGE("%s attr[%s] wrong load sharing=%d", __func__, attr->second.name.c_str(),
                                                           attr->second.loadSharing);
                return BAD_TYPE;
            }
        }
    }

    if (hwcCheckDebugMessages(eDebugTDM)) {
        for (auto &display : mDisplays) {
            if (!(display->mPlugState == true && display->mPowerModeState.has_value() &&
                  display->mPowerModeState.value() != (hwc2_power_mode_t)HWC_POWER_MODE_OFF))
                continue;
            for (auto attr = HWAttrs.begin(); attr != HWAttrs.end(); attr++) {
                for (auto blockId = DPUBlocks.begin(); blockId != DPUBlocks.end(); blockId++) {
                    if (attr->second.loadSharing == LS_DPUF) {
                        const auto &TDMInfoId = std::make_pair(blockId->first, AXI_DONT_CARE);
                        int32_t amount = display->mDisplayTDMInfo[TDMInfoId]
                                                 .getAvailableAmount(attr->first)
                                                 .totalAmount;
                        HDEBUGLOGD(eDebugTDM, "%s : [%s] display:%d,block:%d, amount : %d(%s)",
                                   __func__, attr->second.name.c_str(), display->mDisplayId,
                                   blockId->first, amount,
                                   display->isEnabled() ? "used" : "not used");
                    } else {
                        for (auto axi = AXIPorts.begin(); axi != AXIPorts.end(); ++axi) {
                            const auto &TDMInfoId = std::make_pair(blockId->first, axi->first);
                            int32_t amount = display->mDisplayTDMInfo[TDMInfoId]
                                                     .getAvailableAmount(attr->first)
                                                     .totalAmount;
                            HDEBUGLOGD(eDebugTDM,
                                       "%s : [%s] display:%d,block:%d,axi:%d, amount:%d(%s)",
                                       __func__, attr->second.name.c_str(), display->mType,
                                       blockId->first, axi->first, amount,
                                       display->isEnabled() ? "used" : "not used");
                        }
                    }
                }
            }
        }
    }

    return NO_ERROR;
}

uint32_t getSramAmount(tdm_attr_t attr, uint32_t formatProperty, lbWidthIndex_t widthIndex) {
    auto it = sramAmountMap.find(sramAmountParams(attr, formatProperty, widthIndex));
    return (it != sramAmountMap.end()) ? it->second : 0;
}

uint32_t ExynosResourceManagerModule::calculateHWResourceAmount(ExynosDisplay *display,
                                                                ExynosMPPSource *mppSrc)
{
    uint32_t SRAMtotal = 0;

    if (mppSrc == nullptr) return SRAMtotal;

    if (mppSrc->mSourceType == MPP_SOURCE_LAYER) {
        ExynosLayer *layer = static_cast<ExynosLayer *>(mppSrc->mSource);
        if (layer == nullptr) {
            ALOGE("%s: cannot cast ExynosLayer", __func__);
            return SRAMtotal;
        }
        exynos_image src_img;
        exynos_image dst_img;
        layer->setSrcExynosImage(&src_img);
        layer->setDstExynosImage(&dst_img);
        layer->setExynosImage(src_img, dst_img);
    }

    int32_t transform = mppSrc->mSrcImg.transform;
    int32_t compressType = mppSrc->mSrcImg.compressionInfo.type;
    bool rotation = (transform & HAL_TRANSFORM_ROT_90) ? true : false;

    int32_t width = mppSrc->mSrcImg.w;
    int32_t height = mppSrc->mSrcImg.h;
    uint32_t format = mppSrc->mSrcImg.format;
    uint32_t formatBPP = 0;
    if (isFormat10Bit(format))
        formatBPP = BIT10;
    else if (isFormat8Bit(format))
        formatBPP = BIT8;

    /** To find index **/
    uint32_t formatIndex = 0;

    lbWidthIndex_t widthIndex = LB_W_3073_INF;

    auto findWidthIndex = [&](int32_t w) -> lbWidthIndex_t {
        for (auto it = LB_WIDTH_INDEX_MAP.begin(); it != LB_WIDTH_INDEX_MAP.end(); it++) {
            if (w >= it->second.widthDownto && w <= it->second.widthUpto) {
                return it->first;
            }
        }
        return LB_W_3073_INF;
    };

    /* Caluclate SRAM amount */
    if (rotation) {
        width = height;
        /* Rotation amount, Only YUV rotation is supported */
        if (compressType == COMP_TYPE_SBWC) {
            /* Y and UV width should be aligned and should get sram for each Y and UV */
            int32_t width_y = pixel_align(width + kSramSBWCRotWidthAlign, kSramSBWCRotWidthAlign);
            int32_t width_c =
                    pixel_align(width / 2 + kSramSBWCRotWidthAlign, kSramSBWCRotWidthAlign);
            SRAMtotal += getSramAmount(TDM_ATTR_ROT_90, SBWC_Y, findWidthIndex(width_y));
            SRAMtotal += getSramAmount(TDM_ATTR_ROT_90, SBWC_UV, findWidthIndex(width_c * 2));
        } else {
            /* sramAmountMap has SRAM for both Y and UV */
            widthIndex = findWidthIndex(width);
            SRAMtotal += getSramAmount(TDM_ATTR_ROT_90, NON_SBWC_Y | formatBPP, widthIndex);
            SRAMtotal += getSramAmount(TDM_ATTR_ROT_90, NON_SBWC_UV | formatBPP, widthIndex);
        }
        HDEBUGLOGD(eDebugTDM, "+ rotation : %d", SRAMtotal);
    } else {
        if (compressType == COMP_TYPE_SBWC) {
            width = pixel_align(width + kSramSBWCWidthMargin, kSramSBWCWidthAlign);
        } else if (compressType == COMP_TYPE_AFBC) {
            /* Align for 8,4Byte/pixel formats */
            if (formatToBpp(format) > 16) {
                width = pixel_align(width + kSramAFBC8B4BMargin, kSramAFBC8B4BAlign);
            } else {
                /* Align for 2Byte/pixel formats */
                width = pixel_align(width + kSramAFBC2BMargin, kSramAFBC2BAlign);
            }
        }
        widthIndex = findWidthIndex(width);

        /* AFBC amount */
        if (compressType == COMP_TYPE_AFBC) {
            formatIndex = (isFormatRgb(format) ? RGB : 0) | formatBPP;
            SRAMtotal += getSramAmount(TDM_ATTR_AFBC, formatIndex, widthIndex);
            HDEBUGLOGD(eDebugTDM, "+ AFBC : %d", SRAMtotal);
        }

        /* SBWC amount */
        if (compressType == COMP_TYPE_SBWC) {
            SRAMtotal += getSramAmount(TDM_ATTR_SBWC, SBWC_Y, widthIndex);
            SRAMtotal += getSramAmount(TDM_ATTR_SBWC, SBWC_UV, widthIndex);
            HDEBUGLOGD(eDebugTDM, "+ SBWC : %d", SRAMtotal);
        }
    }

    /* ITP (CSC) amount */
    if (isFormatYUV(format)) {
        /** ITP has no size difference, Use width index as LB_W_3073_INF **/
        SRAMtotal += getSramAmount(TDM_ATTR_ITP, formatBPP, LB_W_3073_INF);
        HDEBUGLOGD(eDebugTDM, "+ YUV : %d", SRAMtotal);
    }

    /* Scale amount */
    int srcW = mppSrc->mSrcImg.w;
    int srcH = mppSrc->mSrcImg.h;
    int dstW = mppSrc->mDstImg.w;
    int dstH = mppSrc->mDstImg.h;

    if (!!(transform & HAL_TRANSFORM_ROT_90)) {
        int tmp = dstW;
        dstW = dstH;
        dstH = tmp;
    }

    bool isScaled = ((srcW != dstW) || (srcH != dstH));

    if (isScaled) {
        if (formatHasAlphaChannel(format))
            formatIndex = FORMAT_RGB_MASK;
        else
            formatIndex = FORMAT_YUV_MASK;

        /** Scale has no size difference, Use width index as LB_W_3073_INF **/
        SRAMtotal += getSramAmount(TDM_ATTR_SCALE, formatIndex, LB_W_3073_INF);
        HDEBUGLOGD(eDebugTDM, "+ Scale : %d", SRAMtotal);
    }

    for (auto it = HWAttrs.begin(); it != HWAttrs.end(); it++) {
        uint32_t amount = 0;
        if (it->first == TDM_ATTR_SRAM_AMOUNT) {
            amount = SRAMtotal;
        } else {
            amount = needHWResource(display, mppSrc->mSrcImg, mppSrc->mDstImg, it->first);
        }
        mppSrc->setHWResourceAmount(it->first, amount);
    }

    HDEBUGLOGD(eDebugTDM,
               "mppSrc(%p) needed SRAM(%d), SCALE(%d), AFBC(%d), CSC(%d), SBWC(%d), WCG(%d), "
               "ROT(%d)",
               mppSrc->mSrcImg.bufferHandle, SRAMtotal,
               needHWResource(display, mppSrc->mSrcImg, mppSrc->mDstImg, TDM_ATTR_SCALE),
               needHWResource(display, mppSrc->mSrcImg, mppSrc->mDstImg, TDM_ATTR_AFBC),
               needHWResource(display, mppSrc->mSrcImg, mppSrc->mDstImg, TDM_ATTR_ITP),
               needHWResource(display, mppSrc->mSrcImg, mppSrc->mDstImg, TDM_ATTR_SBWC),
               needHWResource(display, mppSrc->mSrcImg, mppSrc->mDstImg, TDM_ATTR_WCG),
               needHWResource(display, mppSrc->mSrcImg, mppSrc->mDstImg, TDM_ATTR_ROT_90));

    return SRAMtotal;
}

int32_t ExynosResourceManagerModule::otfMppReordering(ExynosDisplay *display,
                                                      ExynosMPPVector &otfMPPs,
                                                      struct exynos_image &src,
                                                      struct exynos_image &dst)
{
    int orderingType = isAFBCCompressed(src.bufferHandle)
            ? ORDER_AFBC
            : (needHdrProcessing(display, src, dst) ? ORDER_WCG : ORDER_AXI);

    int usedAFBCCount[DPU_BLOCK_CNT] = {0};
    int usedWCGCount[DPU_BLOCK_CNT * AXI_PORT_MAX_CNT] = {0};
    int usedBlockCount[DPU_BLOCK_CNT] = {0};
    int usedAXIPortCount[AXI_PORT_MAX_CNT] = {0};

    auto orderPolicy = [&](const void *lhs, const void *rhs) -> bool {
        if (lhs == NULL || rhs == NULL) {
            return 0;
        }

        const ExynosMPPModule *l = (ExynosMPPModule *)lhs;
        const ExynosMPPModule *r = (ExynosMPPModule *)rhs;

        uint32_t assignedStateL = l->mAssignedState & MPP_ASSIGN_STATE_ASSIGNED;
        uint32_t assignedStateR = r->mAssignedState & MPP_ASSIGN_STATE_ASSIGNED;

        if (assignedStateL != assignedStateR) return assignedStateL < assignedStateR;

        if (l->mPhysicalType != r->mPhysicalType) return l->mPhysicalType < r->mPhysicalType;

        if (orderingType == ORDER_AFBC) {
            /* AFBC balancing */
            if ((l->mAttr & MPP_ATTR_AFBC) != (r->mAttr & MPP_ATTR_AFBC))
                return (l->mAttr & MPP_ATTR_AFBC) > (r->mAttr & MPP_ATTR_AFBC);
            if (l->mAttr & MPP_ATTR_AFBC) {
                /* If layer is AFBC, DPU block that AFBC HW block belongs
                 * which has not been used much should be placed in the front */
                if (usedAFBCCount[l->mHWBlockId] != usedAFBCCount[r->mHWBlockId])
                    return usedAFBCCount[l->mHWBlockId] < usedAFBCCount[r->mHWBlockId];
            }
        } else if (orderingType == ORDER_WCG) {
            /* WCG balancing */
            if ((l->mAttr & MPP_ATTR_WCG) != (r->mAttr & MPP_ATTR_WCG))
                return (l->mAttr & MPP_ATTR_WCG) > (r->mAttr & MPP_ATTR_WCG);
            if (l->mAttr & MPP_ATTR_WCG) {
                /* If layer is WCG, DPU block that WCG HW block belongs
                 * which has not been used much should be placed in the front */
                if (usedWCGCount[l->mHWBlockId * AXI_PORT_MAX_CNT + l->mAXIPortId] !=
                    usedWCGCount[r->mHWBlockId * AXI_PORT_MAX_CNT + r->mAXIPortId])
                    return usedWCGCount[l->mHWBlockId * AXI_PORT_MAX_CNT + l->mAXIPortId] <
                        usedWCGCount[r->mHWBlockId * AXI_PORT_MAX_CNT + r->mAXIPortId];
            }
        }

        /* AXI bus balancing */
        /* AXI port which has not been used much should be placed in the front */
        if (usedAXIPortCount[l->mAXIPortId] != usedAXIPortCount[r->mAXIPortId]) {
            return usedAXIPortCount[l->mAXIPortId] < usedAXIPortCount[r->mAXIPortId];
        }
        /* IF MPP connected same AXI port, Block balancing should be regarded after */
        if (usedBlockCount[l->mHWBlockId] != usedBlockCount[r->mHWBlockId])
            return usedBlockCount[l->mHWBlockId] < usedBlockCount[r->mHWBlockId];

        return l->mPhysicalIndex < r->mPhysicalIndex;
    };

    for (auto it : otfMPPs) {
        ExynosMPPModule *mpp = (ExynosMPPModule *)it;
        uint32_t bId = mpp->getHWBlockId();
        uint32_t aId = mpp->getAXIPortId();
        bool isAFBC = false;
        bool isWCG = false;

        if (mpp->mAssignedState & MPP_ASSIGN_STATE_ASSIGNED) {
            ExynosMPPSource *mppSrc = mpp->mAssignedSources[0];
            if ((mppSrc->mSourceType == MPP_SOURCE_LAYER) &&
                (mppSrc->mSrcImg.bufferHandle != nullptr)) {
                if ((mpp->mAttr & MPP_ATTR_AFBC) &&
                    (isAFBCCompressed(mppSrc->mSrcImg.bufferHandle))) {
                    isAFBC = true;
                    usedAFBCCount[bId]++;
                } else if ((mpp->mAttr & MPP_ATTR_WCG) &&
                           (needHdrProcessing(display, mppSrc->mSrcImg, mppSrc->mDstImg))) {
                    isWCG = true;
                    usedWCGCount[bId]++;
                }
            } else if (mppSrc->mSourceType == MPP_SOURCE_COMPOSITION_TARGET) {
                ExynosCompositionInfo *info = (ExynosCompositionInfo *)mppSrc;
                // ESTEVAN_TBD
                // if ((mpp->mAttr & MPP_ATTR_AFBC) && (info->mCompressionInfo.type ==
                // COMP_TYPE_AFBC)) {
                if ((mpp->mAttr & MPP_ATTR_AFBC) &&
                    (isAFBCCompressed(mppSrc->mSrcImg.bufferHandle))) {
                    isAFBC = true;
                    usedAFBCCount[bId]++;
                } else if ((mpp->mAttr & MPP_ATTR_WCG) &&
                           (needHdrProcessing(display, info->mSrcImg, info->mDstImg))) {
                    isWCG = true;
                    usedWCGCount[bId]++;
                }
            }

            HDEBUGLOGD(eDebugLoadBalancing, "%s: %s is assigned (AFBC:%d, WCG:%d), is %s", __func__,
                       mpp->mName.c_str(), isAFBC, isWCG,
                       (mppSrc->mSourceType == MPP_SOURCE_LAYER) ? "Layer" : "Client Target");
            usedBlockCount[bId]++;
            usedAXIPortCount[aId]++;
        }
    }

    HDEBUGLOGD(eDebugLoadBalancing,
               "Sorting by %s ordering, AFBC(used DPUF0:%d, DPUF1:%d), AXI(used AXI0:%d, AXI1:%d), "
               "BLOCK(used DPUF0:%d, DPUF1:%d)",
               (orderingType == ORDER_AFBC) ? "AFBC" : "_AXI", usedAFBCCount[DPUF0],
               usedAFBCCount[DPUF1], usedAXIPortCount[AXI0], usedAXIPortCount[AXI1],
               usedBlockCount[DPUF0], usedBlockCount[DPUF1]);

    std::sort(otfMPPs.begin(), otfMPPs.end(), orderPolicy);

    if (hwcCheckDebugMessages(eDebugLoadBalancing)) {
        String8 after;
        for (uint32_t i = 0; i < otfMPPs.size(); i++) {
            ExynosMPPModule *mpp = (ExynosMPPModule *)otfMPPs[i];
            after.appendFormat("(%s) -> ", mpp->mName.c_str());
        }

        ALOGD("%s %p, %s", __func__, src.bufferHandle, after.c_str());
    }

    return 0;
}

bool ExynosResourceManagerModule::isOverlapped(ExynosDisplay *display, ExynosMPPSource *current,
                                               ExynosMPPSource *compare) {
    int CT = current->mDstImg.y - TDM_OVERLAP_MARGIN;
    CT = (CT < 0) ? 0 : CT;
    int CB = current->mDstImg.y + current->mDstImg.h + TDM_OVERLAP_MARGIN;
    CB = (CB > display->mYres) ? display->mYres : CB;
    int LT = compare->mDstImg.y;
    int LB = compare->mDstImg.y + compare->mDstImg.h;

    if (((LT <= CT && CT <= LB) || (LT <= CB && CB <= LB)) ||
        ((CT <= LT && LT <= CB) || (CT < LB && LB <= CB))) {
        HDEBUGLOGD(eDebugTDM, "%s, current %p and compare %p is overlaped", __func__,
                   current->mSrcImg.bufferHandle, compare->mSrcImg.bufferHandle);
        return true;
    }

    return false;
}

uint32_t ExynosResourceManagerModule::getAmounts(ExynosDisplay* display, uint32_t currentBlockId,
                                                 uint32_t currentAXIId, ExynosMPP* compOtfMPP,
                                                 ExynosMPPSource* curSrc, ExynosMPPSource* compSrc,
                                                 std::array<uint32_t, TDM_ATTR_MAX>& DPUFAmounts,
                                                 std::array<uint32_t, TDM_ATTR_MAX>& AXIAmounts) {
    const uint32_t blockId = compOtfMPP->getHWBlockId();
    const uint32_t AXIId = compOtfMPP->getAXIPortId();
    if (currentBlockId == blockId && isOverlapped(display, curSrc, compSrc)) {
        String8 log;
        if (hwcCheckDebugMessages(eDebugTDM)) {
            log.appendFormat("%s", compOtfMPP->mName.c_str());
        }
        for (auto attr = HWAttrs.begin(); attr != HWAttrs.end(); attr++) {
            uint32_t compareAmount = compSrc->getHWResourceAmount(attr->first);
            if (hwcCheckDebugMessages(eDebugTDM)) {
                log.appendFormat(", attr %s DPUF-%d(+ %d)", attr->second.name.c_str(),
                                 DPUFAmounts[attr->first], compareAmount);
            }
            DPUFAmounts[attr->first] += compareAmount;
            if (attr->second.loadSharing == LS_DPUF_AXI && currentAXIId == AXIId) {
                if (hwcCheckDebugMessages(eDebugTDM)) {
                    log.appendFormat(",AXI-%d(+ %d)", AXIAmounts[attr->first], compareAmount);
                }
                AXIAmounts[attr->first] += compareAmount;
            }
        }
        HDEBUGLOGD(eDebugTDM, "%s %s", __func__, log.c_str());
    }

    return 0;
}

bool ExynosResourceManagerModule::isAssignable(ExynosMPP* candidateMPP, ExynosDisplay* display,
                                               struct exynos_image& src, struct exynos_image& dst,
                                               ExynosMPPSource* mppSrc) {
    if (display != nullptr && candidateMPP != nullptr &&
        display->mType == HWC_DISPLAY_EXTERNAL &&
        !(candidateMPP->mPhysicalType == MPP_DPP_VGRFS && candidateMPP->mPhysicalIndex == 0)) {
        return false;
    }
    return gs201::ExynosResourceManagerModule::isAssignable(candidateMPP, display, src, dst,
                                                            mppSrc);
}
