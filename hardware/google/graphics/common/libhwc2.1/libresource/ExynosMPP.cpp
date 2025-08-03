/*
 * Copyright (C) 2012 The Android Open Source Project
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

/**
 * Project HWC 2.0 Design
 */
#define ATRACE_TAG (ATRACE_TAG_GRAPHICS | ATRACE_TAG_HAL)
#include <utils/Errors.h>
#include <sync/sync.h>
#include <sys/mman.h>
#include <cutils/properties.h>
#include "ExynosMPP.h"
#include "ExynosResourceRestriction.h"
#include <hardware/hwcomposer_defs.h>
#include <math.h>
#include "VendorGraphicBuffer.h"
#include "ExynosHWCDebug.h"
#include "ExynosDisplay.h"
#include "ExynosVirtualDisplay.h"
#include "ExynosLayer.h"
#include "ExynosHWCHelper.h"
#include "exynos_sync.h"
#include "ExynosResourceManager.h"

/**
 * ExynosMPP implementation
 *
 * Abstraction class for HW Resource
 */

using namespace android;
using namespace vendor::graphics;
using namespace SOC_VERSION;

int ExynosMPP::mainDisplayWidth = 0;
int ExynosMPP::mainDisplayHeight = 0;
extern struct exynos_hwc_control exynosHWCControl;

std::unordered_map<tdm_attr_t, TDMInfo_t> HWAttrs = {
    {TDM_ATTR_SRAM_AMOUNT, {String8("SRAM"),  LS_DPUF}},
    {TDM_ATTR_AFBC,        {String8("AFBC"),  LS_DPUF}},
    {TDM_ATTR_SBWC,        {String8("SBWC"),  LS_DPUF}},
    {TDM_ATTR_ITP,         {String8("CSC"),   LS_DPUF}},
    {TDM_ATTR_ROT_90,      {String8("ROT"),   LS_DPUF}},
    {TDM_ATTR_SCALE,       {String8("SCALE"), LS_DPUF}},
    {TDM_ATTR_WCG,         {String8("WCG"),   LS_DPUF_AXI}},
};

void dumpExynosMPPImgInfo(uint32_t type, exynos_mpp_img_info &imgInfo)
{
    HDEBUGLOGD(type, "\tbuffer: %p, bufferType: %d",
            imgInfo.bufferHandle, imgInfo.bufferType);
}

bool exynosMPPSourceComp(const ExynosMPPSource* l, const ExynosMPPSource* r)
{
    if (l == NULL || r == NULL) {
        HWC_LOGE(NULL,"exynosMPP compare error");
        return 0;
    }
    return (l->mSrcImg.zOrder < r->mSrcImg.zOrder);
}

void dump(const restriction_size_t &restrictionSize, String8 &result) {
    result.appendFormat("    maxDownScale = %u, maxUpscale = %u\n", restrictionSize.maxDownScale,
                        restrictionSize.maxUpScale);
    result.appendFormat("    maxFullWidth = %u, maxFullHeight = %u\n", restrictionSize.maxFullWidth,
                        restrictionSize.maxFullHeight);
    result.appendFormat("    minFullWidth = %u, minFullHeight = %u\n", restrictionSize.minFullWidth,
                        restrictionSize.minFullHeight);
    result.appendFormat("    fullWidthAlign = %u, fullHeightAlign = %u\n",
                        restrictionSize.fullWidthAlign, restrictionSize.fullHeightAlign);
    result.appendFormat("    maxCropWidth = %u, maxCropHeight = %u\n", restrictionSize.maxCropWidth,
                        restrictionSize.maxCropHeight);
    result.appendFormat("    minCropWidth = %u, minCropHeight = %u\n", restrictionSize.minCropWidth,
                        restrictionSize.minCropHeight);
    result.appendFormat("    cropXAlign = %u, cropYAlign = %u\n", restrictionSize.cropXAlign,
                        restrictionSize.cropYAlign);
    result.appendFormat("    cropWidthAlign = %u, cropHeightAlign = %u\n",
                        restrictionSize.cropWidthAlign, restrictionSize.cropHeightAlign);
}

ExynosMPPSource::ExynosMPPSource()
      : mSourceType(MPP_SOURCE_MAX), mSource(NULL), mOtfMPP(NULL), mM2mMPP(NULL) {
    memset(&mSrcImg, 0, sizeof(mSrcImg));
    mSrcImg.acquireFenceFd = -1;
    mSrcImg.releaseFenceFd = -1;
    memset(&mDstImg, 0, sizeof(mDstImg));
    mDstImg.acquireFenceFd = -1;
    mDstImg.releaseFenceFd = -1;
    memset(&mMidImg, 0, sizeof(mMidImg));
    mMidImg.acquireFenceFd = -1;
    mMidImg.releaseFenceFd = -1;

    mHWResourceAmount.clear();
}

ExynosMPPSource::ExynosMPPSource(uint32_t sourceType, void *source)
    : mSourceType(sourceType),
    mSource(source),
    mOtfMPP(NULL),
    mM2mMPP(NULL)
{
    memset(&mSrcImg, 0, sizeof(mSrcImg));
    mSrcImg.acquireFenceFd = -1;
    mSrcImg.releaseFenceFd = -1;
    memset(&mDstImg, 0, sizeof(mDstImg));
    mDstImg.acquireFenceFd = -1;
    mDstImg.releaseFenceFd = -1;
    memset(&mMidImg, 0, sizeof(mMidImg));
    mMidImg.acquireFenceFd = -1;
    mMidImg.releaseFenceFd = -1;
}

void ExynosMPPSource::setExynosImage(const exynos_image& src_img, const exynos_image& dst_img) {
    mSrcImg = src_img;
    mDstImg = dst_img;
}

void ExynosMPPSource::setExynosMidImage(const exynos_image& mid_img) {
    mMidImg = mid_img;
}

ExynosMPP::ExynosMPP(ExynosResourceManager* resourceManager,
        uint32_t physicalType, uint32_t logicalType, const char *name,
        uint32_t physicalIndex, uint32_t logicalIndex, uint32_t preAssignInfo)
: mResourceManager(resourceManager),
    mMPPType(MPP_TYPE_NONE),
    mPhysicalType(physicalType),
    mLogicalType(logicalType),
    mName(name),
    mPhysicalIndex(physicalIndex),
    mLogicalIndex(logicalIndex),
    mPreAssignDisplayInfo(preAssignInfo),
    mHWState(MPP_HW_STATE_IDLE),
    mLastStateFenceFd(-1),
    mAssignedState(MPP_ASSIGN_STATE_FREE),
    mEnable(true),
    mAssignedDisplay(NULL),
    mMaxSrcLayerNum(1),
    mPrevAssignedState(MPP_ASSIGN_STATE_FREE),
    mPrevAssignedDisplayType(-1),
    mReservedDisplay(-1),
    mResourceManageThread(android::sp<ResourceManageThread>::make(this)),
    mCapacity(-1),
    mUsedCapacity(0),
    mAllocOutBufFlag(true),
    mFreeOutBufFlag(true),
    mHWBusyFlag(false),
    mCurrentDstBuf(0),
    mPrivDstBuf(-1),
    mNeedCompressedTarget(false),
    mDstAllocatedSize(DST_SIZE_UNKNOWN),
    mUseM2MSrcFence(false),
    mAttr(0),
    mAssignOrder(0),
    mAXIPortId(0),
    mHWBlockId(0),
    mNeedSolidColorLayer(false)
{
    if (mPhysicalType < MPP_DPP_NUM) {
        mClockKhz = VPP_CLOCK;
        mPPC = VPP_PIXEL_PER_CLOCK;
    }

    if (mPhysicalType == MPP_G2D) {
        mClockKhz = G2D_CLOCK;
        if (mLogicalType == MPP_LOGICAL_G2D_RGB) {

            char value[256];
            int afbc_prop;
            property_get("ro.vendor.ddk.set.afbc", value, "0");
            afbc_prop = atoi(value);
            if (afbc_prop == 0)
                mNeedCompressedTarget = false;
            else
                mNeedCompressedTarget = true;

            mMaxSrcLayerNum = G2D_MAX_SRC_NUM;
        } else if (mLogicalType == MPP_LOGICAL_G2D_COMBO &&
                (mPreAssignDisplayInfo & HWC_DISPLAY_VIRTUAL_BIT)) {
            mMaxSrcLayerNum = G2D_MAX_SRC_NUM - 1;
            mAllocOutBufFlag = false;
            mNeedCompressedTarget = false;
            mUseM2MSrcFence = true;
        }
        /* Capacity means time(ms) that can be used for operation */
        mCapacity = MPP_G2D_CAPACITY;
        mAcrylicHandle = AcrylicFactory::createAcrylic("default_compositor");
        if (mAcrylicHandle == NULL) {
            MPP_LOGE("Fail to allocate acrylic handle");
            abort();
        } else {
            MPP_LOGI("mAcrylicHandle is created: %p", mAcrylicHandle);
        }
    }

    /* Basic feature supported flags */
    for (const auto &feature: feature_table) {
        if (feature.hwType == mPhysicalType)
            mAttr = feature.attr;
    }

    if (mPhysicalType == MPP_MSC) {
        mClockKhz = MSC_CLOCK;
        /* To do
        * Capacity should be set
        */
        mCapacity = MPP_MSC_CAPACITY;
        mAcrylicHandle = AcrylicFactory::createAcrylic("default_scaler");
        if (mAcrylicHandle == NULL) {
            MPP_LOGE("Fail to allocate acrylic handle");
            abort();
        } else {
            MPP_LOGI("mAcrylicHandle is created: %p", mAcrylicHandle);
        }
    }

    if (mMaxSrcLayerNum > 1) {
        mNeedSolidColorLayer = true;
        mAcrylicHandle->setDefaultColor(0, 0, 0, 0);
    }

    mAssignedSources.clear();
    resetUsedCapacity();

    mResourceManageThread->mRunning = true;
    mResourceManageThread->run("MPPThread");

    memset(&mPrevFrameInfo, 0, sizeof(mPrevFrameInfo));
    for (int i = 0; i < NUM_MPP_SRC_BUFS; i++) {
        mPrevFrameInfo.srcInfo[i].acquireFenceFd = -1;
        mPrevFrameInfo.srcInfo[i].releaseFenceFd = -1;
        mPrevFrameInfo.dstInfo[i].acquireFenceFd = -1;
        mPrevFrameInfo.dstInfo[i].releaseFenceFd = -1;
    }

    for (uint32_t i = 0; i < NUM_MPP_SRC_BUFS; i++) {
        memset(&mSrcImgs[i], 0, sizeof(mSrcImgs[i]));
        mSrcImgs[i].acrylicAcquireFenceFd = -1;
        mSrcImgs[i].acrylicReleaseFenceFd = -1;
    }
    for (uint32_t i = 0; i < NUM_MPP_DST_BUFS(mLogicalType); i++) {
        memset(&mDstImgs[i], 0, sizeof(mDstImgs[i]));
        mDstImgs[i].acrylicAcquireFenceFd = -1;
        mDstImgs[i].acrylicReleaseFenceFd = -1;
    }

    for (uint32_t i = 0; i < DISPLAY_MODE_NUM; i++)
    {
        mPreAssignDisplayList[i] = 0;
    }
}

ExynosMPP::~ExynosMPP()
{
    mResourceManageThread->mRunning = false;
    mResourceManageThread->requestExitAndWait();
}


ExynosMPP::ResourceManageThread::ResourceManageThread(ExynosMPP *exynosMPP)
: mExynosMPP(exynosMPP),
    mRunning(false)
{
}

ExynosMPP::ResourceManageThread::~ResourceManageThread()
{
}

bool ExynosMPP::isDataspaceSupportedByMPP(struct exynos_image &src, struct exynos_image &dst)
{
    uint32_t srcStandard = (src.dataSpace & HAL_DATASPACE_STANDARD_MASK);
    uint32_t dstStandard = (dst.dataSpace & HAL_DATASPACE_STANDARD_MASK);
    uint32_t srcTransfer = (src.dataSpace & HAL_DATASPACE_TRANSFER_MASK);
    uint32_t dstTransfer = (dst.dataSpace & HAL_DATASPACE_TRANSFER_MASK);

    /* No conversion case */
    if ((srcStandard == dstStandard) && (srcTransfer == dstTransfer))
        return true;

    /* Unspecified conversion case */
    if (((srcStandard == HAL_DATASPACE_STANDARD_UNSPECIFIED) ||
         (dstStandard == HAL_DATASPACE_STANDARD_UNSPECIFIED)) &&
        ((srcTransfer == HAL_DATASPACE_TRANSFER_UNSPECIFIED) ||
         (dstTransfer == HAL_DATASPACE_TRANSFER_UNSPECIFIED)))
        return true;

    /* WCG support check */
    /* 'Src is not HDR' and 'src,dst has differenct dataspace' means WCG case */
    /* Some MPPs are only support HDR but WCG */
    if (!hasHdrInfo(src) && ((mAttr & MPP_ATTR_WCG) == 0))
        return false;

    /* Standard support check */
    auto standard_it = dataspace_standard_map.find(srcStandard);
    if ((standard_it == dataspace_standard_map.end()) ||
        ((mAttr & standard_it->second) == 0))
        return false;

    /* Transfer support check */
    auto transfer_it = dataspace_transfer_map.find(srcTransfer);
    if ((transfer_it == dataspace_transfer_map.end()) ||
        ((mAttr & transfer_it->second) == 0))
        return false;

    return checkCSCRestriction(src, dst);
}

bool ExynosMPP::isSupportedHDR(struct exynos_image &src, struct exynos_image &dst)
{

    uint32_t srcStandard = (src.dataSpace & HAL_DATASPACE_STANDARD_MASK);
    uint32_t dstStandard = (dst.dataSpace & HAL_DATASPACE_STANDARD_MASK);
    uint32_t srcTransfer = (src.dataSpace & HAL_DATASPACE_TRANSFER_MASK);
    uint32_t dstTransfer = (dst.dataSpace & HAL_DATASPACE_TRANSFER_MASK);

    if (hasHdr10Plus(src) || hasHdrInfo(src) ) {
        if (mAttr & MPP_ATTR_HDR10PLUS)
            return true;
        else if ((srcStandard == dstStandard) && (srcTransfer == dstTransfer))
            return true;
        else if ((mLogicalType == MPP_LOGICAL_G2D_COMBO) && (mPreAssignDisplayInfo & HWC_DISPLAY_VIRTUAL_BIT))
            return true;
        else
            return false;
    }
    return true;
}

bool ExynosMPP::isSupportedHStrideCrop(struct exynos_image __unused &src)
{
    return true;
}

bool ExynosMPP::isSupportedBlend(struct exynos_image &src)
{
    switch(src.blending) {
    case HWC2_BLEND_MODE_NONE:
    case HWC2_BLEND_MODE_PREMULTIPLIED:
    case HWC2_BLEND_MODE_COVERAGE:
        return true;
    default:
        return false;
    }
}

bool ExynosMPP::checkRotationCondition(struct exynos_image &src)
{
    /* Check only DPP types */
    if (mPhysicalType >= MPP_DPP_NUM)
        return true;

    /* If DPP has their own restriction, implmemnt module codes */
    if (mAttr & MPP_ATTR_ROT_90) {
        if (isFormatYUV420(src.format) == true)
            return true;
    }

    /* Other DPPs */
    if ((src.transform & HAL_TRANSFORM_ROT_90) == 0)
    {
        if ((src.compressionInfo.type == COMP_TYPE_AFBC) && (src.transform != 0)) return false;
        return true;
    } else {
        return false;
    }

    return true;
}

bool ExynosMPP::isSupportedTransform(struct exynos_image &src)
{
    if (src.transform == 0) return true;

    /* If MPP need to check additional condition,
     * implement checkRotationCondition function to check it */
    /* For example, DPP need to check custom conditons */
    if (!checkRotationCondition(src))
        return false;

    for(auto transform_map : transform_map_table) {
        if (src.transform & transform_map.hal_tr) {
            if (!(mAttr & transform_map.hwc_tr))
                return false;
        }
    }

    return true;
}

bool ExynosMPP::isSupportedCompression(struct exynos_image &src)
{
    if (src.compressionInfo.type == COMP_TYPE_AFBC) {
        if (mAttr & MPP_ATTR_AFBC)
            return true;
        else
            return false;
    }

    return true;
}

bool ExynosMPP::isSupportedCapability(ExynosDisplay &display, struct exynos_image &src)
{
    if (display.mType != HWC_DISPLAY_EXTERNAL)
        return true;

    if (!(mAttr & MPP_ATTR_USE_CAPA))
        return true;

    if (mResourceManager->hasHdrLayer || mResourceManager->hasDrmLayer) {
        if (getDrmMode(src.usageFlags) != NO_DRM)
            return true;
        else if (hasHdrInfo(src))
            return true;
        else
            return false;
    }

    return true;
}

bool ExynosMPP::isSupportedDRM(struct exynos_image &src)
{
    if (getDrmMode(src.usageFlags) == NO_DRM)
        return true;

    if (mLogicalType == MPP_LOGICAL_G2D_RGB)
        return false;

    return true;
}

bool ExynosMPP::checkCSCRestriction(struct exynos_image &src, struct exynos_image &dst)
{
    return true;
}

bool ExynosMPP::isDimLayerSupported()
{
    if (mAttr & MPP_ATTR_DIM)
        return true;

    return false;
}

bool ExynosMPP::isSrcFormatSupported(struct exynos_image &src)
{
    if (mLogicalType == MPP_LOGICAL_G2D_YUV) {
        /* Support YUV layer and HDR RGB layer */
        if (isFormatRgb(src.format) && (hasHdrInfo(src) == false))
            return false;
    }
    if ((mLogicalType == MPP_LOGICAL_G2D_RGB) &&
        isFormatYUV(src.format))
        return false;
    if ((mLogicalType == MPP_LOGICAL_MSC_YUV) &&
        isFormatRgb(src.format)) {
        return false;
    }

    if (mResourceManager == NULL) return false;

    for (uint32_t i = 0 ; i < mResourceManager->mFormatRestrictionCnt; i++) {
        if ((mResourceManager->mFormatRestrictions[i].hwType == mPhysicalType) &&
                ((mResourceManager->mFormatRestrictions[i].nodeType == NODE_NONE) ||
                 (mResourceManager->mFormatRestrictions[i].nodeType == NODE_SRC)) &&
                (mResourceManager->mFormatRestrictions[i].format == src.format))
            return true;
    }

    return false;
}

bool ExynosMPP::isDstFormatSupported(struct exynos_image &dst)
{

    for (uint32_t i = 0 ; i < mResourceManager->mFormatRestrictionCnt; i++) {
        if ((mResourceManager->mFormatRestrictions[i].hwType == mPhysicalType) &&
                ((mResourceManager->mFormatRestrictions[i].nodeType == NODE_NONE) ||
                 (mResourceManager->mFormatRestrictions[i].nodeType == NODE_DST)) &&
                (mResourceManager->mFormatRestrictions[i].format == dst.format))
            return true;
    }

    return false;
}

uint32_t ExynosMPP::getMaxUpscale(const struct exynos_image &src,
                                  const struct exynos_image __unused &dst) const {
    uint32_t idx = getRestrictionClassification(src);
    return mSrcSizeRestrictions[idx].maxUpScale;
}

bool ExynosMPP::checkDownscaleCap(const float resolution, const float displayRatio_V) const {
    if (mPhysicalType >= MPP_DPP_NUM) return true;

    return float(mClockKhz) >= ((resolution * VPP_RESOL_MARGIN) / (mPPC * displayRatio_V));
}

uint32_t ExynosMPP::getDownscaleRestriction(const struct exynos_image &src,
                                            const struct exynos_image & /*dst*/) const {
    auto idx = getRestrictionClassification(src);
    return mDstSizeRestrictions[idx].maxDownScale;
}

uint32_t ExynosMPP::getMaxDownscale(const ExynosDisplay &display, const struct exynos_image &src,
                                    const struct exynos_image &dst) const {
    uint32_t maxDownscale = getDownscaleRestriction(src, dst);

    if (maxDownscale <= 1) {
        return maxDownscale;
    }

    if (mPhysicalType < MPP_DPP_NUM) {
        float resolution = float(src.w) * float(src.h) * display.getBtsRefreshRate() / 1000;
        if (!checkDownscaleCap(resolution, float(dst.h) / float(display.mYres))) {
            return 1;
        }
    }

    return maxDownscale;
}

uint32_t ExynosMPP::getSrcXOffsetAlign(struct exynos_image &src)
{
    /* Refer module(ExynosMPPModule) for chip specific restrictions */
    uint32_t idx = getRestrictionClassification(src);
    if ((mPhysicalType == MPP_MSC) &&
            ((src.format == HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_S10B) ||
            (src.format == HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_S10B))) {
        return 16;
    }
    return mSrcSizeRestrictions[idx].cropXAlign;
}
uint32_t ExynosMPP::getSrcXOffsetAlign(uint32_t idx)
{
    if (idx >= RESTRICTION_MAX)
    {
        MPP_LOGE("invalid idx: %d", idx);
        return 16;
    }
    return mSrcSizeRestrictions[idx].cropXAlign;
}
uint32_t ExynosMPP::getSrcYOffsetAlign(struct exynos_image &src)
{
    uint32_t idx = getRestrictionClassification(src);
    return mSrcSizeRestrictions[idx].cropYAlign;
}
uint32_t ExynosMPP::getSrcYOffsetAlign(uint32_t idx)
{
    if (idx >= RESTRICTION_MAX)
    {
        MPP_LOGE("invalid idx: %d", idx);
        return 16;
    }
    return mSrcSizeRestrictions[idx].cropYAlign;
}
uint32_t ExynosMPP::getSrcWidthAlign(struct exynos_image &src)
{
    uint32_t idx = getRestrictionClassification(src);
    return mSrcSizeRestrictions[idx].fullWidthAlign;
}
uint32_t ExynosMPP::getSrcHeightAlign(struct exynos_image &src)
{
    uint32_t idx = getRestrictionClassification(src);
    return mSrcSizeRestrictions[idx].fullHeightAlign;
}
uint32_t ExynosMPP::getSrcMaxWidth(struct exynos_image &src)
{
    if (isFormatYUV(src.format))
        return 4096;

    uint32_t idx = getRestrictionClassification(src);
    return mSrcSizeRestrictions[idx].maxFullWidth;
}
uint32_t ExynosMPP::getSrcMaxHeight(struct exynos_image &src)
{
    if (isFormatYUV(src.format))
        return 4096;

    uint32_t idx = getRestrictionClassification(src);
    return mSrcSizeRestrictions[idx].maxFullHeight;
}
uint32_t ExynosMPP::getSrcMinWidth(struct exynos_image &src)
{
    uint32_t idx = getRestrictionClassification(src);
    return mSrcSizeRestrictions[idx].minFullWidth;
}
uint32_t ExynosMPP::getSrcMinWidth(uint32_t idx)
{
    if (idx >= RESTRICTION_MAX)
    {
        MPP_LOGE("invalid idx: %d", idx);
        return 16;
    }
    return mSrcSizeRestrictions[idx].minFullWidth;
}
uint32_t ExynosMPP::getSrcMinHeight(struct exynos_image &src)
{
    uint32_t idx = getRestrictionClassification(src);
    return mSrcSizeRestrictions[idx].minFullHeight;
}
uint32_t ExynosMPP::getSrcMinHeight(uint32_t idx)
{
    if (idx >= RESTRICTION_MAX)
    {
        MPP_LOGE("invalid idx: %d", idx);
        return 16;
    }
    return mSrcSizeRestrictions[idx].minFullHeight;
}
uint32_t ExynosMPP::getSrcMaxCropWidth(struct exynos_image &src)
{
    uint32_t idx = getRestrictionClassification(src);
    return mSrcSizeRestrictions[idx].maxCropWidth;
}
uint32_t ExynosMPP::getSrcMaxCropHeight(struct exynos_image &src)
{
    if ((mMPPType == MPP_TYPE_OTF) &&
        (src.transform & HAL_TRANSFORM_ROT_90))
        return 2160;

    uint32_t idx = getRestrictionClassification(src);
    return mSrcSizeRestrictions[idx].maxCropHeight;
}
uint32_t ExynosMPP::getSrcMaxCropSize(struct exynos_image &src)
{
    return (getSrcMaxCropWidth(src) * getSrcMaxCropHeight(src));
}
uint32_t ExynosMPP::getSrcMinCropWidth(struct exynos_image &src)
{
    if (((src.format == HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_S10B) ||
         (src.format == HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_S10B)) &&
        (mPhysicalType == MPP_G2D))
        return 2;
    uint32_t idx = getRestrictionClassification(src);
    return mSrcSizeRestrictions[idx].minCropWidth;
}
uint32_t ExynosMPP::getSrcMinCropHeight(struct exynos_image &src)
{
    if (((src.format == HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_S10B) ||
         (src.format == HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_S10B)) &&
        (mPhysicalType == MPP_G2D))
        return 2;
    uint32_t idx = getRestrictionClassification(src);
    return mSrcSizeRestrictions[idx].minCropHeight;
}
uint32_t ExynosMPP::getSrcCropWidthAlign(const struct exynos_image &src) const {
    if (((src.format == HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_S10B) ||
         (src.format == HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_S10B)) &&
        (mPhysicalType == MPP_G2D))
        return 2;
    uint32_t idx = getRestrictionClassification(src);
    return mSrcSizeRestrictions[idx].cropWidthAlign;
}

/* This is used for only otfMPP */
uint32_t ExynosMPP::getSrcCropWidthAlign(uint32_t idx) const {
    if (idx >= RESTRICTION_MAX)
    {
        MPP_LOGE("invalid idx: %d", idx);
        return 16;
    }
    return mSrcSizeRestrictions[idx].cropWidthAlign;
}
uint32_t ExynosMPP::getSrcCropHeightAlign(const struct exynos_image &src) const {
    if (((src.format == HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_S10B) ||
         (src.format == HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_S10B)) &&
        (mPhysicalType == MPP_G2D))
        return 2;

    uint32_t idx = getRestrictionClassification(src);
    return mSrcSizeRestrictions[idx].cropHeightAlign;
}

/* This is used for only otfMPP */
uint32_t ExynosMPP::getSrcCropHeightAlign(uint32_t idx) const {
    if (idx >= RESTRICTION_MAX)
    {
        MPP_LOGE("invalid idx: %d", idx);
        return 16;
    }
    return mSrcSizeRestrictions[idx].cropHeightAlign;
}
uint32_t ExynosMPP::getDstMaxWidth(struct exynos_image &dst)
{
    uint32_t idx = getRestrictionClassification(dst);
    return mDstSizeRestrictions[idx].maxCropWidth;
}
uint32_t ExynosMPP::getDstMaxHeight(struct exynos_image &dst)
{
    uint32_t idx = getRestrictionClassification(dst);
    return mDstSizeRestrictions[idx].maxCropHeight;
}
uint32_t ExynosMPP::getDstMinWidth(struct exynos_image &dst)
{
    if (((dst.format == HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_S10B) ||
         (dst.format == HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_S10B)) &&
        (mPhysicalType == MPP_G2D))
        return 64;

    if ((mNeedSolidColorLayer == false) && mNeedCompressedTarget)
        return 16;

    if ((mPhysicalType == MPP_G2D) && (mNeedSolidColorLayer == false) &&
            isFormatSBWC(dst.format))
        return 32;

    uint32_t idx = getRestrictionClassification(dst);
    return mDstSizeRestrictions[idx].minCropWidth;
}
uint32_t ExynosMPP::getDstMinHeight(struct exynos_image &dst)
{
    if ((mNeedSolidColorLayer == false) && mNeedCompressedTarget)
        return 16;

    if ((mPhysicalType == MPP_G2D) && (mNeedSolidColorLayer == false) &&
            isFormatSBWC(dst.format))
        return 8;

    uint32_t idx = getRestrictionClassification(dst);
    return mDstSizeRestrictions[idx].minCropHeight;
}
uint32_t ExynosMPP::getDstWidthAlign(const struct exynos_image &dst) const {
    if (((dst.format == HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_S10B) ||
         (dst.format == HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_S10B)) &&
        (mPhysicalType == MPP_G2D))
        return 64;

    if ((mNeedSolidColorLayer == false) && mNeedCompressedTarget)
        return 16;

    if ((mPhysicalType == MPP_G2D) && (mNeedSolidColorLayer == false) &&
            isFormatSBWC(dst.format))
        return 32;

    uint32_t idx = getRestrictionClassification(dst);
    return mDstSizeRestrictions[idx].cropWidthAlign;
}
uint32_t ExynosMPP::getDstHeightAlign(const struct exynos_image &dst) const {
    if ((mNeedSolidColorLayer == false) && mNeedCompressedTarget)
        return 16;

    if ((mPhysicalType == MPP_G2D) && (mNeedSolidColorLayer == false) &&
            isFormatSBWC(dst.format))
        return 8;

    uint32_t idx = getRestrictionClassification(dst);
    return mDstSizeRestrictions[idx].cropHeightAlign;
}
uint32_t ExynosMPP::getDstXOffsetAlign(struct exynos_image &dst)
{
    if ((mNeedSolidColorLayer == false) && mNeedCompressedTarget)
        return 16;

    if ((mPhysicalType == MPP_G2D) && (mNeedSolidColorLayer == false) &&
            isFormatSBWC(dst.format))
        return 32;

    uint32_t idx = getRestrictionClassification(dst);
    return mDstSizeRestrictions[idx].cropXAlign;
}
uint32_t ExynosMPP::getDstYOffsetAlign(struct exynos_image &dst)
{
    if ((mNeedSolidColorLayer == false) && mNeedCompressedTarget)
        return 16;

    if ((mPhysicalType == MPP_G2D) && (mNeedSolidColorLayer == false) &&
            isFormatSBWC(dst.format))
        return 8;

    uint32_t idx = getRestrictionClassification(dst);
    return mDstSizeRestrictions[idx].cropYAlign;
}
uint32_t ExynosMPP::getOutBufAlign()
{
    if (mNeedCompressedTarget)
        return 16;
    else
        return 1;
}

int32_t ExynosMPP::isSupportLayerColorTransform(
        struct exynos_image &src, struct exynos_image __unused &dst)
{
    if (src.needColorTransform == false)
        return true;

    if (mAttr & MPP_ATTR_LAYER_TRANSFORM)
        return true;

    return false;
}

bool ExynosMPP::ResourceManageThread::threadLoop()
{
    if (mExynosMPP == NULL)
        return false;

    ALOGI("%s threadLoop is started", mExynosMPP->mName.c_str());
    while(mRunning) {
        Mutex::Autolock lock(mMutex);
        while((mFreedBuffers.size() == 0) &&
                (mStateFences.size() == 0)) {
            mCondition.wait(mMutex);
        }

        if ((mExynosMPP->mHWState == MPP_HW_STATE_RUNNING) &&
                (mStateFences.size() != 0)) {
            if (checkStateFences()) {
                mExynosMPP->mHWState = MPP_HW_STATE_IDLE;
            }
        } else {
            if ((mStateFences.size() != 0) &&
                    (mExynosMPP->mHWState != MPP_HW_STATE_RUNNING)) {
                ALOGW("%s, mHWState(%d) but mStateFences size(%zu)",
                        mExynosMPP->mName.c_str(), mExynosMPP->mHWState,
                        mStateFences.size());
                checkStateFences();
            }
        }

        if (mFreedBuffers.size() != 0) {
            freeBuffers();
        }
    }
    return true;
}

void ExynosMPP::ResourceManageThread::freeBuffers()
{
    VendorGraphicBufferAllocator& gAllocator(VendorGraphicBufferAllocator::get());
    android::List<exynos_mpp_img_info >::iterator it;
    android::List<exynos_mpp_img_info >::iterator end;
    it = mFreedBuffers.begin();
    end = mFreedBuffers.end();

    uint32_t freebufNum = 0;
    while (it != end) {
        exynos_mpp_img_info freeBuffer = (exynos_mpp_img_info)(*it);
        HDEBUGLOGD(eDebugMPP|eDebugFence|eDebugBuf, "freebufNum: %d, buffer: %p", freebufNum, freeBuffer.bufferHandle);
        dumpExynosMPPImgInfo(eDebugMPP|eDebugFence|eDebugBuf, freeBuffer);
        if (fence_valid(freeBuffer.acrylicAcquireFenceFd)) {
            if (sync_wait(freeBuffer.acrylicAcquireFenceFd, 1000) < 0)
                HWC_LOGE(NULL, "%s:: acquire fence sync_wait error", mExynosMPP->mName.c_str());
            freeBuffer.acrylicAcquireFenceFd =
                fence_close(freeBuffer.acrylicAcquireFenceFd, mExynosMPP->mAssignedDisplay,
                        FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_ALL);
        }
        if (fence_valid(freeBuffer.acrylicReleaseFenceFd)) {
            if (sync_wait(freeBuffer.acrylicReleaseFenceFd, 1000) < 0)
                HWC_LOGE(NULL, "%s:: release fence sync_wait error", mExynosMPP->mName.c_str());
            freeBuffer.acrylicReleaseFenceFd =
                fence_close(freeBuffer.acrylicReleaseFenceFd, mExynosMPP->mAssignedDisplay,
                        FENCE_TYPE_SRC_RELEASE, FENCE_IP_ALL);
        }
        gAllocator.free(freeBuffer.bufferHandle);
        it = mFreedBuffers.erase(it);
    }
}

bool ExynosMPP::ResourceManageThread::checkStateFences()
{
    bool ret = true;
    android::List<int >::iterator it;
    android::List<int >::iterator end;

    it = mStateFences.begin();
    end = mStateFences.end();
    uint32_t waitFenceNum = 0;
    while (it != end) {
        int fence = (int)(*it);
        HDEBUGLOGD(eDebugMPP|eDebugFence, "%d wait fence: %d", waitFenceNum, fence);
        waitFenceNum++;
        if (fence_valid(fence)) {
            if (sync_wait(fence, 5000) < 0) {
                HWC_LOGE(NULL, "%s::[%s][%d] sync_wait(%d) error(%s)", __func__,
                        mExynosMPP->mName.c_str(), mExynosMPP->mLogicalIndex, fence, strerror(errno));
                ret = false;
            }
            fence = fence_close(fence, mExynosMPP->mAssignedDisplay,
                    FENCE_TYPE_ALL, FENCE_IP_ALL);
        }
        it = mStateFences.erase(it);
    }
    return ret;
}

void ExynosMPP::ResourceManageThread::addFreedBuffer(exynos_mpp_img_info freedBuffer)
{
    android::Mutex::Autolock lock(mMutex);
    mFreedBuffers.push_back(freedBuffer);
    mCondition.signal();
}

void ExynosMPP::ResourceManageThread::addStateFence(int fence)
{
    Mutex::Autolock lock(mMutex);
    HDEBUGLOGD(eDebugMPP|eDebugFence, "wait fence is added: %d", fence);
    mStateFences.push_back(fence);
    mCondition.signal();
}

/**
 * @param w
 * @param h
 * @param color
 * @param usage
 * @return int32_t
 */
int32_t ExynosMPP::allocOutBuf(uint32_t w, uint32_t h, uint32_t format, uint64_t usage, uint32_t index) {
    ATRACE_CALL();
    uint32_t dstStride = 0;

    MPP_LOGD(eDebugMPP|eDebugBuf, "index: %d++++++++", index);

    if (index >= NUM_MPP_DST_BUFS(mLogicalType)) {
        return -EINVAL;
    }

    exynos_mpp_img_info freeDstBuf = mDstImgs[index];
    MPP_LOGD(eDebugMPP|eDebugBuf, "mDstImg[%d] is reallocated", index);
    dumpExynosMPPImgInfo(eDebugMPP, mDstImgs[index]);

    uint64_t allocUsage = getBufferUsage(usage);
    if (!needCompressDstBuf()) {
        allocUsage |= VendorGraphicBufferUsage::NO_AFBC;
    }
    buffer_handle_t dstBuffer;

    MPP_LOGD(eDebugMPP|eDebugBuf, "\tw: %d, h: %d, format: 0x%8x, previousBuffer: %p, allocUsage: 0x%" PRIx64 ", usage: 0x%" PRIx64 "",
            w, h, format, freeDstBuf.bufferHandle, allocUsage, usage);

    status_t error = NO_ERROR;

    {
        ATRACE_CALL();

        VendorGraphicBufferAllocator& gAllocator(VendorGraphicBufferAllocator::get());
        error = gAllocator.allocate(w, h, format, 1, allocUsage, &dstBuffer, &dstStride, "HWC");
    }

    if ((error != NO_ERROR) || (dstBuffer == NULL)) {
        MPP_LOGE("failed to allocate destination buffer(%dx%d): %d", w, h, error);
        return -EINVAL;
    }

    memset(&mDstImgs[index], 0, sizeof(mDstImgs[index]));

    mDstImgs[index].acrylicAcquireFenceFd = -1;
    mDstImgs[index].acrylicReleaseFenceFd = -1;
    mDstImgs[index].bufferHandle = dstBuffer;
    mDstImgs[index].bufferType = getBufferType(usage);
    mDstImgs[index].format = format;

    MPP_LOGD(eDebugMPP|eDebugBuf, "free outbuf[%d] %p", index, freeDstBuf.bufferHandle);

    if (freeDstBuf.bufferHandle != NULL) {
        freeOutBuf(freeDstBuf);
    } else {
        if (mAssignedDisplay != NULL) {
            freeDstBuf.acrylicAcquireFenceFd = fence_close(freeDstBuf.acrylicAcquireFenceFd,
                    mAssignedDisplay, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_G2D);
            freeDstBuf.acrylicReleaseFenceFd = fence_close(freeDstBuf.acrylicReleaseFenceFd,
                    mAssignedDisplay, FENCE_TYPE_SRC_RELEASE, FENCE_IP_G2D);
        }
    }

    MPP_LOGD(eDebugMPP|eDebugBuf, "dstBuffer(%p)-----------", dstBuffer);

    return NO_ERROR;
}

/**
 * @param outbuf
 * @return int32_t
 */
int32_t ExynosMPP::setOutBuf(buffer_handle_t outbuf, int32_t fence) {
    mDstImgs[mCurrentDstBuf].bufferHandle = NULL;
    if (outbuf != NULL) {
        mDstImgs[mCurrentDstBuf].bufferHandle = outbuf;
        mDstImgs[mCurrentDstBuf].format =
            VendorGraphicBufferMeta::get_format(mDstImgs[mCurrentDstBuf].bufferHandle);
    }
    setDstAcquireFence(fence);
    return NO_ERROR;
}

/**
 * @param dst
 * @return int32_t
 */
int32_t ExynosMPP::freeOutBuf(struct exynos_mpp_img_info dst) {
    mResourceManageThread->addFreedBuffer(dst);
    dst.bufferHandle = NULL;
    return NO_ERROR;
}

uint32_t ExynosMPP::getBufferType(uint64_t usage)
{
    if (getDrmMode(usage) == SECURE_DRM)
        return MPP_BUFFER_SECURE_DRM;
    else if (getDrmMode(usage) == NORMAL_DRM)
        return MPP_BUFFER_NORMAL_DRM;
    else {
        if (exynosHWCControl.dumpMidBuf)
            return MPP_BUFFER_DUMP;
        else
            return MPP_BUFFER_NORMAL;
    }
}

uint32_t ExynosMPP::getBufferType(const buffer_handle_t handle)
{
    uint64_t usage = VendorGraphicBufferMeta::get_usage(handle);

    return getBufferType(usage);
}

uint64_t ExynosMPP::getBufferUsage(uint64_t usage)
{
    uint64_t allocUsage = 0;
    if (getBufferType(usage) == MPP_BUFFER_DUMP) {
        allocUsage = BufferUsage::CPU_READ_OFTEN |
            BufferUsage::CPU_WRITE_OFTEN;
    } else {
        allocUsage = BufferUsage::CPU_READ_NEVER |
            BufferUsage::CPU_WRITE_NEVER |
            VendorGraphicBufferUsage::NOZEROED |
            BufferUsage::COMPOSER_OVERLAY;
    }

    if (getDrmMode(usage) == SECURE_DRM) {
        allocUsage |= BufferUsage::PROTECTED;
        allocUsage &= ~VendorGraphicBufferUsage::PRIVATE_NONSECURE;
    } else if (getDrmMode(usage) == NORMAL_DRM) {
        allocUsage |= BufferUsage::PROTECTED;
        allocUsage |= VendorGraphicBufferUsage::PRIVATE_NONSECURE;
    }

    return allocUsage;
}

bool ExynosMPP::needCompressDstBuf() const {
    return (mMaxSrcLayerNum > 1) && mNeedCompressedTarget;
}

bool ExynosMPP::needDstBufRealloc(struct exynos_image &dst, uint32_t index)
{
    MPP_LOGD(eDebugMPP|eDebugBuf, "index: %d++++++++", index);

    if (index >= NUM_MPP_DST_BUFS(mLogicalType)) {
        MPP_LOGE("%s:: index(%d) is not valid", __func__, index);
        return false;
    }
    buffer_handle_t dst_handle = NULL;
    if (mDstImgs[index].bufferHandle != NULL)
        dst_handle = mDstImgs[index].bufferHandle;

    if (dst_handle == NULL) {
        MPP_LOGD(eDebugMPP|eDebugBuf, "\tDstImag[%d]  handle is NULL", index);
        return true;
    }

    int32_t assignedDisplay = -1;
    if (mAssignedDisplay != NULL) {
        assignedDisplay = mAssignedDisplay->mType;
    } else {
        MPP_LOGE("%s:: mpp is not assigned", __func__);
        return false;
    }

    VendorGraphicBufferMeta gmeta(dst_handle);

    uint32_t prevAssignedBufferNum =
            getBufferNumOfFormat(gmeta.format, getCompressionType(dst_handle));
    uint32_t assignedBufferNum = getBufferNumOfFormat(dst.format, getCompressionType(dst_handle));

    MPP_LOGD(eDebugMPP | eDebugBuf, "\tdst_handle(%p) afbc (%u) sbwc (%u) lossy (%u)", dst_handle,
             isAFBCCompressed(dst_handle), isFormatSBWC(gmeta.format), isFormatLossy(gmeta.format));
    MPP_LOGD(eDebugMPP | eDebugBuf,
             "\tAssignedDisplay[%d, %d] format[0x%8x, 0x%8x], bufferType[%d, %d], bufferNum[%d, "
             "%d] "
             "usageFlags: 0x%" PRIx64 ", need comp_type 0x%x lossy %u",
             mPrevAssignedDisplayType, assignedDisplay, gmeta.format, dst.format,
             mDstImgs[index].bufferType, getBufferType(dst.usageFlags), prevAssignedBufferNum,
             assignedBufferNum, dst.usageFlags, dst.compressionInfo.type,
             isFormatLossy(dst.format));

    bool realloc = (mPrevAssignedDisplayType != assignedDisplay) ||
            (prevAssignedBufferNum < assignedBufferNum) ||
            (formatToBpp(gmeta.format) < formatToBpp(dst.format)) ||
            ((gmeta.stride * gmeta.vstride) < (int)(dst.fullWidth * dst.fullHeight)) ||
            (mDstImgs[index].bufferType != getBufferType(dst.usageFlags)) ||
            (isAFBCCompressed(dst_handle) != (dst.compressionInfo.type == COMP_TYPE_AFBC)) ||
            (isFormatSBWC(gmeta.format) != isFormatSBWC(dst.format)) ||
            (isFormatLossy(gmeta.format) != isFormatLossy(dst.format));

    MPP_LOGD(eDebugMPP|eDebugBuf, "realloc: %d--------", realloc);
    return realloc;
}

bool ExynosMPP::canUsePrevFrame()
{
    if ((mAssignedDisplay && !mAssignedDisplay->mDisplayControl.skipM2mProcessing) ||
        !exynosHWCControl.skipM2mProcessing)
        return false;

    /* virtual display always require composition */
    if (mAllocOutBufFlag == false)
        return false;

    if (mPrevFrameInfo.srcNum != mAssignedSources.size())
        return false;

    for (uint32_t i = 0; i < mPrevFrameInfo.srcNum; i++) {
        if ((mPrevFrameInfo.srcInfo[i].bufferHandle != mAssignedSources[i]->mSrcImg.bufferHandle) ||
            (mPrevFrameInfo.srcInfo[i].x != mAssignedSources[i]->mSrcImg.x) ||
            (mPrevFrameInfo.srcInfo[i].y != mAssignedSources[i]->mSrcImg.y) ||
            (mPrevFrameInfo.srcInfo[i].w != mAssignedSources[i]->mSrcImg.w) ||
            (mPrevFrameInfo.srcInfo[i].h != mAssignedSources[i]->mSrcImg.h) ||
            (mPrevFrameInfo.srcInfo[i].format != mAssignedSources[i]->mSrcImg.format) ||
            (mPrevFrameInfo.srcInfo[i].usageFlags != mAssignedSources[i]->mSrcImg.usageFlags) ||
            (mPrevFrameInfo.srcInfo[i].dataSpace != mAssignedSources[i]->mSrcImg.dataSpace) ||
            (mPrevFrameInfo.srcInfo[i].blending != mAssignedSources[i]->mSrcImg.blending) ||
            (mPrevFrameInfo.srcInfo[i].transform != mAssignedSources[i]->mSrcImg.transform) ||
            (mPrevFrameInfo.srcInfo[i].compressionInfo.type !=
             mAssignedSources[i]->mSrcImg.compressionInfo.type) ||
            (mPrevFrameInfo.srcInfo[i].planeAlpha != mAssignedSources[i]->mSrcImg.planeAlpha) ||
            (mPrevFrameInfo.dstInfo[i].x != mAssignedSources[i]->mMidImg.x) ||
            (mPrevFrameInfo.dstInfo[i].y != mAssignedSources[i]->mMidImg.y) ||
            (mPrevFrameInfo.dstInfo[i].w != mAssignedSources[i]->mMidImg.w) ||
            (mPrevFrameInfo.dstInfo[i].h != mAssignedSources[i]->mMidImg.h) ||
            (mPrevFrameInfo.dstInfo[i].format != mAssignedSources[i]->mMidImg.format))
            return false;
    }

   int32_t prevDstIndex  = (mCurrentDstBuf + NUM_MPP_DST_BUFS(mLogicalType) - 1)% NUM_MPP_DST_BUFS(mLogicalType);
   if (mDstImgs[prevDstIndex].bufferHandle == NULL)
       return false;

    return true;
}

int32_t ExynosMPP::setupLayer(exynos_mpp_img_info *srcImgInfo, struct exynos_image &src, struct exynos_image &dst)
{
    int ret = NO_ERROR;

    if (srcImgInfo->mppLayer == NULL) {
        if ((srcImgInfo->mppLayer = mAcrylicHandle->createLayer()) == NULL)
        {
            MPP_LOGE("%s:: Fail to create layer", __func__);
            return -EINVAL;
        }
    }

    if (src.bufferHandle == NULL) {
        MPP_LOGE("%s:: Invalid source handle", __func__);
        return -EINVAL;
    }

    buffer_handle_t srcHandle = NULL;
    if (src.bufferHandle != NULL)
        srcHandle = src.bufferHandle;

    VendorGraphicBufferMeta gmeta(srcHandle);
    int bufFds[MAX_HW2D_PLANES];
    size_t bufLength[MAX_HW2D_PLANES];
    uint32_t attribute = 0;
    uint32_t bufferNum = getBufferNumOfFormat(gmeta.format, getCompressionType(srcHandle));
    android_dataspace_t dataspace = src.dataSpace;
    if (dataspace == HAL_DATASPACE_UNKNOWN)
    {
        if (isFormatRgb(gmeta.format))
            dataspace = HAL_DATASPACE_V0_SRGB;
        else
            dataspace = HAL_DATASPACE_V0_BT601_625;
    }

    if (bufferNum == 0)
    {
        MPP_LOGE("%s:: Fail to get bufferNum(%d), format(0x%8x, afbc %d)", __func__, bufferNum,
                 gmeta.format, isAFBCCompressed(srcHandle));
        return -EINVAL;
    }
    bufFds[0] = gmeta.fd;
    bufFds[1] = gmeta.fd1;
    bufFds[2] = gmeta.fd2;
    if (getBufLength(srcHandle, MAX_HW2D_PLANES, bufLength, gmeta.format, src.fullWidth, src.fullHeight) != NO_ERROR) {
        MPP_LOGE("%s:: invalid bufferLength(%zu, %zu, %zu), format(0x%8x)", __func__,
                bufLength[0], bufLength[1], bufLength[2], gmeta.format);
        return -EINVAL;
    }

    /* HDR process */
    if (hasHdrInfo(src)) {
        unsigned int max = (src.metaParcel.sHdrStaticInfo.sType1.mMaxDisplayLuminance/10000);
        unsigned int min = src.metaParcel.sHdrStaticInfo.sType1.mMinDisplayLuminance;

        srcImgInfo->mppLayer->setMasterDisplayLuminance(min,max);
        MPP_LOGD(eDebugMPP, "HWC2: G2D luminance min %d, max %d", min, max);
        MPP_LOGD(eDebugMPP|eDebugFence, "G2D getting HDR source!");

        srcImgInfo->mppLayer->setLayerHDR(true);
    } else
        srcImgInfo->mppLayer->setLayerHDR(false);

    /* Transfer MetaData */
    if (src.hasMetaParcel) {
        srcImgInfo->mppLayer->setLayerData(&src.metaParcel, sizeof(src.metaParcel));
    }

    srcImgInfo->bufferType = getBufferType(srcHandle);
    if (srcImgInfo->bufferType == MPP_BUFFER_SECURE_DRM)
        attribute |= AcrylicCanvas::ATTR_PROTECTED;
    /*Change AFBC attribute on the basis of the modifier*/
    if (src.compressionInfo.type == COMP_TYPE_AFBC) {
        if ((src.compressionInfo.modifier & AFBC_FORMAT_MOD_BLOCK_SIZE_MASK) ==
            AFBC_FORMAT_MOD_BLOCK_SIZE_32x8) {
            attribute |= AcrylicCanvas::ATTR_COMPRESSED_WIDEBLK;
        } else {
            attribute |= AcrylicCanvas::ATTR_COMPRESSED;
        }
    }

    srcImgInfo->bufferHandle = srcHandle;
    srcImgInfo->acrylicAcquireFenceFd =
        hwcCheckFenceDebug(mAssignedDisplay, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_G2D, src.acquireFenceFd);

    MPP_LOGD(eDebugMPP|eDebugFence, "source configuration:");
    MPP_LOGD(eDebugMPP, "\tImageDimension[%d, %d], ImageType[0x%8x, 0x%8x]",
            src.fullWidth, src.fullHeight,
            gmeta.format, dataspace);
    MPP_LOGD(eDebugMPP|eDebugFence, "\tImageBuffer handle: %p, fds[%d, %d, %d], bufLength[%zu, %zu, %zu], bufferNum: %d, acquireFence: %d, attribute: %d",
            srcHandle, bufFds[0], bufFds[1], bufFds[2], bufLength[0], bufLength[1], bufLength[2],
            bufferNum, srcImgInfo->acrylicAcquireFenceFd, attribute);
    MPP_LOGD(eDebugMPP, "\tsrc_rect[%d, %d, %d, %d], dst_rect[%d, %d, %d, %d], transform(0x%4x)",
            (int)src.x, (int)src.y, (int)(src.x + src.w), (int)(src.y + src.h),
            (int)dst.x, (int)dst.y, (int)(dst.x + dst.w), (int)(dst.y + dst.h), src.transform);

    srcImgInfo->mppLayer->setImageDimension(src.fullWidth, src.fullHeight);

    if (gmeta.format == HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_PRIV) {
        srcImgInfo->mppLayer->setImageType(HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M, dataspace);
    } else {
        srcImgInfo->mppLayer->setImageType(gmeta.format, dataspace);
    }

    if (mPhysicalType == MPP_G2D) {
        setFenceName(srcImgInfo->acrylicAcquireFenceFd, FENCE_G2D_SRC_LAYER);
        setFenceInfo(srcImgInfo->acrylicAcquireFenceFd, mAssignedDisplay, FENCE_TYPE_SRC_ACQUIRE,
                     FENCE_IP_G2D, HwcFenceDirection::TO);
    } else if (mPhysicalType == MPP_MSC) {
        setFenceName(srcImgInfo->acrylicAcquireFenceFd, FENCE_MSC_SRC_LAYER);
        setFenceInfo(srcImgInfo->acrylicAcquireFenceFd, mAssignedDisplay, FENCE_TYPE_SRC_ACQUIRE,
                     FENCE_IP_MSC, HwcFenceDirection::TO);
    } else {
        MPP_LOGE("%s:: invalid mPhysicalType(%d)", __func__, mPhysicalType);
    }

    srcImgInfo->mppLayer->setImageBuffer(bufFds, bufLength, bufferNum,
            srcImgInfo->acrylicAcquireFenceFd, attribute);

    if (mMaxSrcLayerNum > 1) {
        srcImgInfo->mppLayer->setCompositMode(src.blending, (uint8_t)(255 * src.planeAlpha), src.zOrder);
    } else {
        srcImgInfo->mppLayer->setCompositMode(src.blending, 255, src.zOrder);
    }

    hwc_rect_t src_rect = {(int)src.x, (int)src.y, (int)(src.x + src.w), (int)(src.y + src.h)};
    hwc_rect_t dst_rect = {(int)dst.x, (int)dst.y, (int)(dst.x + dst.w), (int)(dst.y + dst.h)};

    if ((mAssignedDisplay != NULL) &&
        ((mAssignedDisplay->mType == HWC_DISPLAY_VIRTUAL) ||
         (mAssignedDisplay->mType == HWC_DISPLAY_EXTERNAL)))
        srcImgInfo->mppLayer->setCompositArea(src_rect, dst_rect, src.transform, AcrylicLayer::ATTR_NORESAMPLING);
    else {
        if(isFormatYUV(src.format))
            srcImgInfo->mppLayer->setCompositArea(src_rect, dst_rect, src.transform, AcrylicLayer::ATTR_NORESAMPLING);
        else
            srcImgInfo->mppLayer->setCompositArea(src_rect, dst_rect, src.transform);
    }

    srcImgInfo->acrylicAcquireFenceFd = -1;
    srcImgInfo->format = gmeta.format;

    if (gmeta.format == HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_PRIV) {
        srcImgInfo->format = HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M;
    }

    return ret;
}

dstMetaInfo_t ExynosMPP::getDstMetaInfo(android_dataspace_t dstDataspace)
{
    dstMetaInfo_t metaInfo;

    if ((mAssignedSources.size() <= 1) &&
            (mAssignedSources[0]->mSrcImg.dataSpace == dstDataspace)) {
        metaInfo.minLuminance =
            (uint16_t)mAssignedSources[0]->mSrcImg.metaParcel.sHdrStaticInfo.sType1.mMinDisplayLuminance;
        metaInfo.maxLuminance =
            (uint16_t)(mAssignedSources[0]->mSrcImg.metaParcel.sHdrStaticInfo.sType1.mMaxDisplayLuminance/10000);
    } else {
        // minLuminance: 0.0001nit unit, maxLuminance: 1nit unit
        metaInfo.minLuminance = (uint16_t)(mAssignedDisplay->mMinLuminance * 10000);
        metaInfo.maxLuminance = (uint16_t)mAssignedDisplay->mMaxLuminance;
    }

    return metaInfo;
}

int32_t ExynosMPP::setupDst(exynos_mpp_img_info *dstImgInfo)
{
    int ret = NO_ERROR;
    bool isComposition = (mMaxSrcLayerNum > 1);
    buffer_handle_t dstHandle = dstImgInfo->bufferHandle;
    int bufFds[MAX_HW2D_PLANES];
    size_t bufLength[MAX_HW2D_PLANES];
    uint32_t attribute = 0;
    uint32_t bufferNum = getBufferNumOfFormat(dstImgInfo->format, getCompressionType(dstHandle));
    if (bufferNum == 0)
    {
        MPP_LOGE("%s:: Fail to get bufferNum(%d), format(0x%8x, afbc %d)", __func__, bufferNum,
                 dstImgInfo->format, isAFBCCompressed(dstHandle));
        return -EINVAL;
    }

    android_dataspace_t dataspace = HAL_DATASPACE_UNKNOWN;
    VendorGraphicBufferMeta gmeta(dstHandle);

    if (isComposition) {
        if (isFormatRgb(dstImgInfo->format)) {
            if ((mAssignedDisplay != NULL) &&
                (mAssignedDisplay->mColorMode != HAL_COLOR_MODE_NATIVE))
                dataspace = colorModeToDataspace(mAssignedDisplay->mColorMode);
        } else {
            dataspace =
                (android_dataspace)(HAL_DATASPACE_STANDARD_BT709 | HAL_DATASPACE_TRANSFER_GAMMA2_2 | HAL_DATASPACE_RANGE_LIMITED);
        }
    } else {
        dataspace = mAssignedSources[0]->mMidImg.dataSpace;
    }

    if (dataspace == HAL_DATASPACE_UNKNOWN)
    {
        if (isFormatRgb(dstImgInfo->format))
            dataspace = HAL_DATASPACE_V0_SRGB;
        else
            dataspace = HAL_DATASPACE_V0_BT601_625;
    }

    bufFds[0] = gmeta.fd;
    bufFds[1] = gmeta.fd1;
    bufFds[2] = gmeta.fd2;
    if (getBufLength(dstHandle, MAX_HW2D_PLANES, bufLength, dstImgInfo->format,
                gmeta.stride, gmeta.vstride) != NO_ERROR) {
        MPP_LOGE("%s:: invalid bufferLength(%zu, %zu, %zu), format(0x%8x)", __func__,
                bufLength[0], bufLength[1], bufLength[2], dstImgInfo->format);
        return -EINVAL;
    }

    dstImgInfo->bufferType = getBufferType(dstHandle);
    if (dstImgInfo->bufferType == MPP_BUFFER_SECURE_DRM)
        attribute |= AcrylicCanvas::ATTR_PROTECTED;

    if (mAssignedDisplay != NULL) {
        mAcrylicHandle->setCanvasDimension(pixel_align(mAssignedDisplay->mXres, G2D_JUSTIFIED_DST_ALIGN),
                pixel_align(mAssignedDisplay->mYres, G2D_JUSTIFIED_DST_ALIGN));
    }

    /* setup dst */
    if (needCompressDstBuf()) {
        attribute |= AcrylicCanvas::ATTR_COMPRESSED;
    }

    if (mPhysicalType == MPP_G2D) {
        setFenceName(dstImgInfo->acrylicAcquireFenceFd, FENCE_G2D_DST_DPP);
        /* Might be closed next frame */
        setFenceInfo(dstImgInfo->acrylicAcquireFenceFd, mAssignedDisplay, FENCE_TYPE_DST_ACQUIRE,
                     FENCE_IP_G2D, HwcFenceDirection::TO);
    } else if (mPhysicalType == MPP_MSC) {
        setFenceName(dstImgInfo->acrylicAcquireFenceFd, FENCE_MSC_DST_DPP);
        /* Might be closed next frame */
        setFenceInfo(dstImgInfo->acrylicAcquireFenceFd, mAssignedDisplay, FENCE_TYPE_DST_ACQUIRE,
                     FENCE_IP_MSC, HwcFenceDirection::TO);
    } else {
        MPP_LOGE("%s:: invalid mPhysicalType(%d)", __func__, mPhysicalType);
    }

    mAcrylicHandle->setCanvasImageType(dstImgInfo->format, dataspace);

    if ((mLogicalType == MPP_LOGICAL_G2D_COMBO) &&
            (mAssignedDisplay != NULL) &&
            (mAssignedDisplay->mType == HWC_DISPLAY_VIRTUAL) &&
            (((ExynosVirtualDisplay *)mAssignedDisplay)->mIsWFDState == (int)LLWFD)) {
        mAcrylicHandle->setCanvasImageType(HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN, dataspace);
        dstImgInfo->acrylicAcquireFenceFd = fence_close(dstImgInfo->acrylicAcquireFenceFd,
                mAssignedDisplay, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_G2D);
        mAcrylicHandle->setCanvasBuffer(bufFds, bufLength, bufferNum,
                dstImgInfo->acrylicAcquireFenceFd, attribute);
        mAcrylicHandle->setCanvasOTF(attribute);
    }
    else {
        dstImgInfo->acrylicAcquireFenceFd =
            hwcCheckFenceDebug(mAssignedDisplay, FENCE_TYPE_DST_ACQUIRE, FENCE_IP_G2D, dstImgInfo->acrylicAcquireFenceFd);
        mAcrylicHandle->setCanvasBuffer(bufFds, bufLength, bufferNum,
                dstImgInfo->acrylicAcquireFenceFd, attribute);
    }

    dstMetaInfo_t metaInfo = getDstMetaInfo(dataspace);
    if ((mAssignedDisplay != NULL) &&
        (mAssignedDisplay->mType != HWC_DISPLAY_VIRTUAL)) {
        mAcrylicHandle->setTargetDisplayLuminance(metaInfo.minLuminance, metaInfo.maxLuminance);
    }

    MPP_LOGD(eDebugMPP|eDebugFence, "destination configuration:");
    MPP_LOGD(eDebugMPP, "\tImageDimension[%d, %d], ImageType[0x%8x, %d], target luminance[%d, %d]",
            gmeta.stride, gmeta.vstride,
            dstImgInfo->format, dataspace, metaInfo.minLuminance, metaInfo.maxLuminance);
    MPP_LOGD(eDebugMPP|eDebugFence, "\tImageBuffer handle: %p, fds[%d, %d, %d], bufLength[%zu, %zu, %zu], bufferNum: %d, acquireFence: %d, attribute: %d",
            dstHandle, bufFds[0], bufFds[1], bufFds[2], bufLength[0], bufLength[1], bufLength[2],
            bufferNum, dstImgInfo->acrylicAcquireFenceFd, attribute);


    dstImgInfo->acrylicAcquireFenceFd = -1;
    dstImgInfo->dataspace = dataspace;

    return ret;
}

int32_t ExynosMPP::doPostProcessingInternal()
{
    ATRACE_CALL();
    int ret = NO_ERROR;
    size_t sourceNum = mAssignedSources.size();

    if (mAcrylicHandle == NULL) {
        MPP_LOGE("%s:: mAcrylicHandle is NULL", __func__);
        return -EINVAL;
    }

    /* setup source layers */
    for(size_t i = 0; i < sourceNum; i++) {
        MPP_LOGD(eDebugMPP|eDebugFence, "Setup [%zu] source: %p", i, mAssignedSources[i]);
        if ((ret = setupLayer(&mSrcImgs[i], mAssignedSources[i]->mSrcImg, mAssignedSources[i]->mMidImg)) != NO_ERROR) {
            MPP_LOGE("%s:: fail to setupLayer[%zu], ret %d",
                    __func__, i, ret);
            return ret;
        }
    }

    if ((ret = setColorConversionInfo()) != NO_ERROR) {
            MPP_LOGE("%s:: fail to setColorConversionInfo ret %d",
                    __func__, ret);
            return ret;
    }

    if (mPrevFrameInfo.srcNum > sourceNum) {
        MPP_LOGD(eDebugMPP, "prev sourceNum(%d), current sourceNum(%zu)",
                mPrevFrameInfo.srcNum, sourceNum);
        for (size_t i = sourceNum; i < mPrevFrameInfo.srcNum; i++)
        {
            MPP_LOGD(eDebugMPP, "Remove mSrcImgs[%zu], %p", i, mSrcImgs[i].mppLayer);
            if (mSrcImgs[i].mppLayer != NULL) {
                delete mSrcImgs[i].mppLayer;
                mSrcImgs[i].mppLayer = NULL;
            }
        }
    }

    if (mAcrylicHandle->layerCount() != mAssignedSources.size()) {
        MPP_LOGE("Different layer number, acrylic layers(%d), assigned size(%zu)",
                mAcrylicHandle->layerCount(), mAssignedSources.size());
        return -EINVAL;
    }
    MPP_LOGD(eDebugFence, "setupDst ++ mDstImgs[%d] acrylicAcquireFenceFd(%d)",
            mCurrentDstBuf, mDstImgs[mCurrentDstBuf].acrylicAcquireFenceFd);

    setupDst(&mDstImgs[mCurrentDstBuf]);

    MPP_LOGD(eDebugFence, "setupDst -- mDstImgs[%d] acrylicAcquireFenceFd(%d) closed",
            mCurrentDstBuf, mDstImgs[mCurrentDstBuf].acrylicAcquireFenceFd);


    int usingFenceCnt = 1;
    bool acrylicReturn = true;

#ifndef DISABLE_FENCE
    if (mUseM2MSrcFence)
        usingFenceCnt = sourceNum + 1; // Get and Use src + dst fence
    else
        usingFenceCnt = 1;             // Get and Use only dst fence
    int *releaseFences = new int[usingFenceCnt];
    int dstBufIdx = usingFenceCnt - 1;
#else
    usingFenceCnt = 0;                 // Get and Use no fences
    int dstBufIdx = 0;
    int *releaseFences = NULL;
#endif

    acrylicReturn = mAcrylicHandle->execute(releaseFences, usingFenceCnt);

    if (acrylicReturn == false) {
        MPP_LOGE("%s:: fail to excute compositor", __func__);
        for(size_t i = 0; i < sourceNum; i++) {
            mSrcImgs[i].acrylicReleaseFenceFd = -1;
            MPP_LOGE("src[%zu]: ImageDimension[%d, %d], src_rect[%d, %d, %d, %d], dst_rect[%d, %d, %d, %d], transform(0x%4x)",
                    i,
                    mAssignedSources[i]->mSrcImg.fullWidth, mAssignedSources[i]->mSrcImg.fullHeight,
                    mAssignedSources[i]->mSrcImg.x, mAssignedSources[i]->mSrcImg.y,
                    mAssignedSources[i]->mSrcImg.x + mAssignedSources[i]->mSrcImg.w,
                    mAssignedSources[i]->mSrcImg.y + mAssignedSources[i]->mSrcImg.h,
                    mAssignedSources[i]->mMidImg.x, mAssignedSources[i]->mMidImg.y,
                    mAssignedSources[i]->mMidImg.x + mAssignedSources[i]->mMidImg.w,
                    mAssignedSources[i]->mMidImg.y + mAssignedSources[i]->mMidImg.h,
                    mAssignedSources[i]->mSrcImg.transform);
        }
        mDstImgs[mCurrentDstBuf].acrylicReleaseFenceFd = -1;
        ret = -EPERM;
    } else {

        // set fence informations from acryl
        if (mPhysicalType == MPP_G2D) {
            setFenceInfo(releaseFences[dstBufIdx], mAssignedDisplay, FENCE_TYPE_DST_ACQUIRE,
                         FENCE_IP_G2D, HwcFenceDirection::FROM);
            if (usingFenceCnt > 1) {
                for(size_t i = 0; i < sourceNum; i++) {
                    // TODO DPU release fence is tranferred to m2mMPP's source layer fence
                    setFenceInfo(releaseFences[i], mAssignedDisplay, FENCE_TYPE_SRC_RELEASE,
                                 FENCE_IP_G2D, HwcFenceDirection::FROM);
                }
            }
        } else if (mPhysicalType == MPP_MSC) {
            setFenceInfo(releaseFences[dstBufIdx], mAssignedDisplay, FENCE_TYPE_DST_ACQUIRE,
                         FENCE_IP_MSC, HwcFenceDirection::FROM);
            if (usingFenceCnt > 1) {
                for(size_t i = 0; i < sourceNum; i++) {
                    // TODO DPU release fence is tranferred to m2mMPP's source layer fence
                    setFenceInfo(releaseFences[i], mAssignedDisplay, FENCE_TYPE_SRC_RELEASE,
                                 FENCE_IP_MSC, HwcFenceDirection::FROM);
                }
            }
        } else {
            MPP_LOGE("%s:: invalid mPhysicalType(%d)", __func__, mPhysicalType);
        }

        if ((mLogicalType == MPP_LOGICAL_G2D_COMBO) &&
                (mAssignedDisplay != NULL) &&
                (mAssignedDisplay->mType == HWC_DISPLAY_VIRTUAL)) {
            if (((ExynosVirtualDisplay *)mAssignedDisplay)->mIsWFDState == (int)LLWFD) {
                if (usingFenceCnt != 0) // Use no fences
                    releaseFences[dstBufIdx] = fence_close(releaseFences[dstBufIdx],
                            mAssignedDisplay, FENCE_TYPE_SRC_RELEASE, FENCE_IP_G2D); // Close dst buf's fence
            }
            if (mUseM2MSrcFence) {
                if (((ExynosVirtualDisplay *)mAssignedDisplay)->mIsWFDState != (int)GOOGLEWFD) {
                    for (size_t i = 0; i < sourceNum; i++)
                        releaseFences[i] = fence_close(releaseFences[i],
                                mAssignedDisplay, FENCE_TYPE_SRC_RELEASE, FENCE_IP_G2D);
                }
            }
        }

        if (usingFenceCnt == 0) { // Use no fences
            for(size_t i = 0; i < sourceNum; i++) {
                mSrcImgs[i].acrylicReleaseFenceFd = -1;
            }
            mDstImgs[mCurrentDstBuf].acrylicReleaseFenceFd = -1;
        } else {
            for(size_t i = 0; i < sourceNum; i++) {
                if (mUseM2MSrcFence)
                    mSrcImgs[i].acrylicReleaseFenceFd =
                        hwcCheckFenceDebug(mAssignedDisplay, FENCE_TYPE_SRC_RELEASE, FENCE_IP_G2D, releaseFences[i]);
                else
                    mSrcImgs[i].acrylicReleaseFenceFd = -1;
                MPP_LOGD(eDebugFence, "mSrcImgs[%zu] acrylicReleaseFenceFd: %d",
                        i, mSrcImgs[i].acrylicReleaseFenceFd);
            }

            if (mDstImgs[mCurrentDstBuf].acrylicReleaseFenceFd >= 0) {
                MPP_LOGE("mDstImgs[%d].acrylicReleaseFenceFd(%d) is not initialized",
                        mCurrentDstBuf,
                        mDstImgs[mCurrentDstBuf].acrylicReleaseFenceFd);
            }

            if (mPhysicalType == MPP_G2D)
                mDstImgs[mCurrentDstBuf].acrylicReleaseFenceFd =
                    hwcCheckFenceDebug(mAssignedDisplay, FENCE_TYPE_DST_RELEASE, FENCE_IP_G2D, releaseFences[dstBufIdx]);
            else if (mPhysicalType == MPP_MSC)
                mDstImgs[mCurrentDstBuf].acrylicReleaseFenceFd =
                    hwcCheckFenceDebug(mAssignedDisplay, FENCE_TYPE_DST_RELEASE, FENCE_IP_MSC, releaseFences[dstBufIdx]);

            MPP_LOGD(eDebugFence, "mDstImgs[%d] acrylicReleaseFenceFd: %d , releaseFences[%d]",
                    mCurrentDstBuf, mDstImgs[mCurrentDstBuf].acrylicReleaseFenceFd, dstBufIdx);
        }

        if (exynosHWCControl.dumpMidBuf) {
            ALOGI("dump image");
            exynosHWCControl.dumpMidBuf = false;
            if ((mDstImgs[mCurrentDstBuf].acrylicReleaseFenceFd > 0) &&
                (sync_wait(mDstImgs[mCurrentDstBuf].acrylicReleaseFenceFd, 1000) < 0)) {
                ALOGE("%s:: fence sync_wait error to dump image", __func__);
            } else {
                buffer_handle_t dstHandle = mDstImgs[mCurrentDstBuf].bufferHandle;
                VendorGraphicBufferMeta gmeta(dstHandle);

                ALOGI("dump image fw: %d, fh:%d, size: %d", gmeta.stride, gmeta.vstride, gmeta.size);
                FILE *fp;
                fp = fopen(MPP_DUMP_PATH,"ab");

                if (fp) {
                    void *temp = mmap(0, gmeta.size, PROT_READ|PROT_WRITE, MAP_SHARED, gmeta.fd, 0);
                    if (temp) {
                        ALOGI("write...%p", temp);
                        int write_size = fwrite(temp, gmeta.size, 1, fp);
                        if (write_size < 0) {
                            ALOGI("write error: %s", strerror(errno));
                        } else {
                            ALOGI("write size: %d", write_size);
                        }
                        munmap(temp, gmeta.size);
                    } else {
                        ALOGE("mmap is NULL %s", strerror(errno));
                    }
                    fclose(fp);
                } else {
                    ALOGE("open fail %s", strerror(errno));
                }
            }
        }
    }

#ifndef DISABLE_FENCE
    delete [] releaseFences;
#endif

    return ret;
}

bool ExynosMPP::canSkipProcessing()
{
    if ((mAssignedDisplay == NULL) || (mAssignedSources.size() == 0))
        return true;
    ExynosMPPSource *source = mAssignedSources[0];
    exynos_image dst = source->mMidImg;
    if ((mLogicalType == MPP_LOGICAL_G2D_RGB) ||
        (mLogicalType == MPP_LOGICAL_G2D_COMBO)) {
        dst = mAssignedDisplay->mExynosCompositionInfo.mDstImg;
    }
    return ((needDstBufRealloc(dst, mCurrentDstBuf) == false) & canUsePrevFrame());

}

/**
 * @param dst
 * @return int32_t 0 on success, or a negative error code on failure.
 */
int32_t ExynosMPP::doPostProcessing(struct exynos_image& dst) {
    ATRACE_CALL();
    MPP_LOGD(eDebugMPP, "total assigned sources (%zu)++++++++", mAssignedSources.size());

    int ret = NO_ERROR;
    bool realloc = false;
    if (mAssignedSources.size() == 0) {
        MPP_LOGE("Assigned source size(%zu) is not valid",
                mAssignedSources.size());
        ret = -EINVAL;
        goto save_frame_info;
    }

    // Check whether destination buffer allocation is required
    if (mAllocOutBufFlag) {
        if ((realloc = needDstBufRealloc(dst, mCurrentDstBuf)) == true) {
            //  allocate mDstImgs[mCurrentDstBuf]
            uint32_t bufAlign = getOutBufAlign();
            bool isComposition = (mMaxSrcLayerNum > 1);
            if (isComposition)
                dst.format = DEFAULT_MPP_DST_FORMAT;

            uint32_t allocFormat = dst.format;
            if (mFreeOutBufFlag == false)
                allocFormat = DEFAULT_MPP_DST_FORMAT;

            if ((allocFormat == HAL_PIXEL_FORMAT_RGBA_1010102) ||
                (allocFormat == HAL_PIXEL_FORMAT_GOOGLE_NV12_SP_10B) ||
                (allocFormat == HAL_PIXEL_FORMAT_YCBCR_P010))
                allocFormat = DEFAULT_MPP_DST_FORMAT;

            ret = allocOutBuf(ALIGN_UP(mAssignedDisplay->mXres, bufAlign),
                    ALIGN_UP(mAssignedDisplay->mYres, bufAlign),
                    allocFormat, dst.usageFlags, mCurrentDstBuf);
        }
        if (ret < 0) {
            MPP_LOGE("%s:: fail to allocate dst buffer[%d]", __func__, mCurrentDstBuf);
            goto save_frame_info;
        }
        if (mDstImgs[mCurrentDstBuf].format != dst.format) {
            MPP_LOGD(eDebugMPP, "dst format is changed (%d -> %d)",
                    mDstImgs[mCurrentDstBuf].format, dst.format);
            mDstImgs[mCurrentDstBuf].format = dst.format;
        }
    }

    if ((realloc == false) && canUsePrevFrame()) {
        mCurrentDstBuf = (mCurrentDstBuf + NUM_MPP_DST_BUFS(mLogicalType) - 1)% NUM_MPP_DST_BUFS(mLogicalType);
        MPP_LOGD(eDebugMPP|eDebugFence, "Reuse previous frame, dstImg[%d]", mCurrentDstBuf);
        for (uint32_t i = 0; i < mAssignedSources.size(); i++) {
            mAssignedSources[i]->mSrcImg.acquireFenceFd =
                fence_close(mAssignedSources[i]->mSrcImg.acquireFenceFd,
                        mAssignedDisplay, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_G2D);
        }
        goto save_frame_info;
    }

    /* G2D or sclaer case */
    if ((ret = doPostProcessingInternal()) < 0) {
        MPP_LOGE("%s:: fail to post processing, ret %d",
                __func__, ret);
        goto save_frame_info;
    }

save_frame_info:
    /* Save current frame information for next frame*/
    mPrevAssignedDisplayType = mAssignedDisplay->mType;
    mPrevFrameInfo.srcNum = (uint32_t)mAssignedSources.size();
    for (uint32_t i = 0; i < mPrevFrameInfo.srcNum; i++) {
        mPrevFrameInfo.srcInfo[i] = mAssignedSources[i]->mSrcImg;
        mPrevFrameInfo.dstInfo[i] = mAssignedSources[i]->mMidImg;
    }

    MPP_LOGD(eDebugMPP, "mPrevAssignedState: %d, mPrevAssignedDisplayType: %d--------------",
            mAssignedState, mAssignedDisplay->mType);

    return ret;
}

/*
 * This function should be called after doPostProcessing()
 * because doPostProcessing() sets
 * mSrcImgs[].mppImg.releaseFenceFd
 */
int32_t ExynosMPP::getSrcReleaseFence(uint32_t srcIndex)
{
    if (srcIndex >= NUM_MPP_SRC_BUFS)
        return -EINVAL;

    return mSrcImgs[srcIndex].acrylicReleaseFenceFd;

    return -EINVAL;
}

int32_t ExynosMPP::resetSrcReleaseFence()
{
    MPP_LOGD(eDebugFence, "");
    for (uint32_t i = 0; i < mAssignedSources.size(); i++) {
        mSrcImgs[i].acrylicReleaseFenceFd = -1;
    }
    return NO_ERROR;
}

int32_t ExynosMPP::getDstImageInfo(exynos_image *img)
{
    if ((mCurrentDstBuf < 0) || (mCurrentDstBuf >= NUM_MPP_DST_BUFS(mLogicalType)) ||
        (mAssignedDisplay == NULL)) {
        MPP_LOGE("mCurrentDstBuf(%d), mAssignedDisplay(%p)", mCurrentDstBuf, mAssignedDisplay);
        return -EINVAL;
    }

    memset(img, 0, sizeof(exynos_image));
    img->acquireFenceFd = -1;
    img->releaseFenceFd = -1;

    if (needCompressDstBuf()) {
        img->compressionInfo.type = COMP_TYPE_AFBC;
        img->compressionInfo.modifier = AFBC_FORMAT_MOD_BLOCK_SIZE_16x16;
    }

    if (mDstImgs[mCurrentDstBuf].bufferHandle == NULL) {
        img->acquireFenceFd = -1;
        img->releaseFenceFd = -1;
        return -EFAULT;
    } else {
        img->bufferHandle = mDstImgs[mCurrentDstBuf].bufferHandle;
        img->compressionInfo = getCompressionInfo(img->bufferHandle);
        VendorGraphicBufferMeta gmeta(img->bufferHandle);
        img->fullWidth = gmeta.stride;
        img->fullHeight = gmeta.vstride;
        if ((mLogicalType == MPP_LOGICAL_G2D_RGB) ||
            (mLogicalType == MPP_LOGICAL_G2D_COMBO)) {
            if (mAssignedSources.size() == 1) {
                img->x = mAssignedSources[0]->mDstImg.x;
                img->y = mAssignedSources[0]->mDstImg.y;
                img->w = mAssignedSources[0]->mDstImg.w;
                img->h = mAssignedSources[0]->mDstImg.h;
            } else {
                img->x = 0;
                img->y = 0;
                img->w = mAssignedDisplay->mXres;
                img->h = mAssignedDisplay->mXres;
            }
        } else {
            img->x = mAssignedSources[0]->mMidImg.x;
            img->y = mAssignedSources[0]->mMidImg.y;
            img->w = mAssignedSources[0]->mMidImg.w;
            img->h = mAssignedSources[0]->mMidImg.h;
            img->needColorTransform =
                mAssignedSources[0]->mMidImg.needColorTransform;
        }

        img->format = mDstImgs[mCurrentDstBuf].format;
        MPP_LOGD(eDebugFence, "get dstBuf[%d] accquireFence(%d)", mCurrentDstBuf,
                mDstImgs[mCurrentDstBuf].acrylicAcquireFenceFd);
        img->acquireFenceFd = mDstImgs[mCurrentDstBuf].acrylicAcquireFenceFd;
        img->releaseFenceFd = mDstImgs[mCurrentDstBuf].acrylicReleaseFenceFd;
        img->dataSpace = mDstImgs[mCurrentDstBuf].dataspace;
    }
    return NO_ERROR;
}

/*
 * This function should be called after getDstReleaseFence()
 * by ExynosDisplay
 */
int32_t ExynosMPP::setDstAcquireFence(int acquireFence)
{

    int dstBufIndex = 0;

    dstBufIndex = mPrivDstBuf;

    if (mPrivDstBuf == mCurrentDstBuf)
        MPP_LOGD(eDebugFence|eDebugMPP,
                "M2MMPP : same buffer was reused idx %d, %d",mPrivDstBuf, mCurrentDstBuf);

    if (dstBufIndex < 0 || dstBufIndex >= NUM_MPP_DST_BUFS(mLogicalType)) {
        // TODO fence_close..
        acquireFence = fence_close(acquireFence, mAssignedDisplay, FENCE_TYPE_DST_ACQUIRE, FENCE_IP_ALL);
        mPrivDstBuf = mCurrentDstBuf;
        return -EINVAL;
    }

    if (acquireFence < 0) {
        mPrivDstBuf = mCurrentDstBuf;
        return -EINVAL;
    }

    if (mDstImgs[dstBufIndex].acrylicAcquireFenceFd >= 0) {
        MPP_LOGD(eDebugFence,"mDstImgs[%d].acrylicAcquireFenceFd: %d is closed", dstBufIndex,
                mDstImgs[dstBufIndex].acrylicAcquireFenceFd);
        fence_close(mDstImgs[dstBufIndex].acrylicAcquireFenceFd, mAssignedDisplay,
                FENCE_TYPE_DST_ACQUIRE, FENCE_IP_ALL);
    }
    if (mPhysicalType == MPP_MSC)
        mDstImgs[dstBufIndex].acrylicAcquireFenceFd =
            hwcCheckFenceDebug(mAssignedDisplay, FENCE_TYPE_DST_ACQUIRE, FENCE_IP_MSC, acquireFence);
    else
        mDstImgs[dstBufIndex].acrylicAcquireFenceFd =
            hwcCheckFenceDebug(mAssignedDisplay, FENCE_TYPE_DST_ACQUIRE, FENCE_IP_G2D, acquireFence);

    MPP_LOGD(eDebugFence,"->mDstImgs[%d].acrylicAcquireFenceFd: %d", dstBufIndex,
            mDstImgs[dstBufIndex].acrylicAcquireFenceFd);

    mPrivDstBuf = mCurrentDstBuf;

    return NO_ERROR;
}

int32_t ExynosMPP::resetDstReleaseFence()
{
    MPP_LOGD(eDebugFence, "");

    if (mCurrentDstBuf < 0 || mCurrentDstBuf >= NUM_MPP_DST_BUFS(mLogicalType))
        return -EINVAL;

    mDstImgs[mCurrentDstBuf].acrylicReleaseFenceFd = -1;

    return NO_ERROR;
}

int32_t ExynosMPP::requestHWStateChange(uint32_t state)
{
    MPP_LOGD(eDebugMPP|eDebugFence|eDebugBuf, "state: %d", state);
    /* Set HW state to running */
    if (mHWState == state) {
        if ((mPhysicalType == MPP_G2D) && (state == MPP_HW_STATE_IDLE) && (mHWBusyFlag == false)) {
            int ret = NO_ERROR;
            if ((ret = prioritize(-1)) != NO_ERROR)
                MPP_LOGI("prioritize (%d) will be applied on next work", ret);
        }
        return NO_ERROR;
    }

    if (state == MPP_HW_STATE_RUNNING) {
        mHWState = MPP_HW_STATE_RUNNING;
    } else if (state == MPP_HW_STATE_IDLE) {
        if (mLastStateFenceFd >= 0) {
            mResourceManageThread->addStateFence(mLastStateFenceFd);
        } else {
            mHWState = MPP_HW_STATE_IDLE;
        }

        mLastStateFenceFd = -1;

        if ((mPhysicalType == MPP_G2D) && (mHWBusyFlag == false)) {
            int ret = NO_ERROR;
            if ((ret = prioritize(-1)) != NO_ERROR)
                MPP_LOGI("prioritize (%d) is not applied on next work", ret);
        }

        /* Free all of output buffers */
        if (mMPPType == MPP_TYPE_M2M) {
            for(uint32_t i = 0; i < NUM_MPP_DST_BUFS(mLogicalType); i++) {
                exynos_mpp_img_info freeDstBuf = mDstImgs[i];
                memset(&mDstImgs[i], 0, sizeof(mDstImgs[i]));
                mDstImgs[i].acrylicAcquireFenceFd = freeDstBuf.acrylicAcquireFenceFd;
                mDstImgs[i].acrylicReleaseFenceFd = freeDstBuf.acrylicReleaseFenceFd;
                freeDstBuf.acrylicAcquireFenceFd = -1;
                freeDstBuf.acrylicReleaseFenceFd = -1;

                if (mFreeOutBufFlag == true) {
                    MPP_LOGD(eDebugMPP|eDebugFence|eDebugBuf, "free outbuf[%d] %p",
                            i, freeDstBuf.bufferHandle);
                    if (freeDstBuf.bufferHandle != NULL && mAllocOutBufFlag) {
                        freeOutBuf(freeDstBuf);
                    }
                } else {
                    mDstImgs[i].bufferHandle = freeDstBuf.bufferHandle;
                    mDstImgs[i].bufferType = freeDstBuf.bufferType;
                }
            }
        }

        for (uint32_t i = 0; i < NUM_MPP_SRC_BUFS; i++)
        {
            if (mSrcImgs[i].mppLayer != NULL) {
                delete mSrcImgs[i].mppLayer;
                mSrcImgs[i].mppLayer = NULL;
            }
        }
        memset(&mPrevFrameInfo, 0, sizeof(mPrevFrameInfo));
        for (int i = 0; i < NUM_MPP_SRC_BUFS; i++) {
            mPrevFrameInfo.srcInfo[i].acquireFenceFd = -1;
            mPrevFrameInfo.srcInfo[i].releaseFenceFd = -1;
            mPrevFrameInfo.dstInfo[i].acquireFenceFd = -1;
            mPrevFrameInfo.dstInfo[i].releaseFenceFd = -1;
        }
    }

    return NO_ERROR;
}

int32_t ExynosMPP::setHWStateFence(int32_t fence)
{
    MPP_LOGD(eDebugFence, "Update HWState fence, Close(%d), set(%d)",
            mLastStateFenceFd, fence);
    mLastStateFenceFd = fence;

    return NO_ERROR;
}

/**
 * @param ..
 * @return int32_t
 */
int32_t ExynosMPP::setupRestriction() {

    MPP_LOGD(eDebugMPP, "mPhysicalType(%d)", mPhysicalType);

    for (uint32_t i = 0; i < RESTRICTION_MAX; i++) {
        const restriction_size_element *restriction_size_table = mResourceManager->mSizeRestrictions[i];
        for (uint32_t j = 0; j < mResourceManager->mSizeRestrictionCnt[i]; j++) {
            if (restriction_size_table[j].key.hwType == mPhysicalType) {
                if ((restriction_size_table[j].key.nodeType == NODE_SRC) ||
                        (restriction_size_table[j].key.nodeType == NODE_NONE)) {
                    memcpy(&mSrcSizeRestrictions[i], &restriction_size_table[j].sizeRestriction,
                            sizeof(mSrcSizeRestrictions[i]));
                    MPP_LOGD(eDebugMPP, "\tSet mSrcSizeRestrictions[%d], "
                            "[%d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d]",
                            i, mSrcSizeRestrictions[i].maxDownScale, mSrcSizeRestrictions[i].maxUpScale,
                            mSrcSizeRestrictions[i].maxFullWidth, mSrcSizeRestrictions[i].maxFullHeight,
                            mSrcSizeRestrictions[i].minFullWidth, mSrcSizeRestrictions[i].minFullHeight,
                            mSrcSizeRestrictions[i].fullWidthAlign, mSrcSizeRestrictions[i].fullHeightAlign,
                            mSrcSizeRestrictions[i].maxCropWidth, mSrcSizeRestrictions[i].maxCropHeight,
                            mSrcSizeRestrictions[i].minCropWidth, mSrcSizeRestrictions[i].minCropHeight,
                            mSrcSizeRestrictions[i].cropXAlign, mSrcSizeRestrictions[i].cropYAlign,
                            mSrcSizeRestrictions[i].cropWidthAlign, mSrcSizeRestrictions[i].cropHeightAlign);

                }
                if ((restriction_size_table[j].key.nodeType == NODE_DST) ||
                        (restriction_size_table[j].key.nodeType == NODE_NONE)) {
                    memcpy(&mDstSizeRestrictions[i], &restriction_size_table[j].sizeRestriction,
                            sizeof(mDstSizeRestrictions[i]));
                    MPP_LOGD(eDebugMPP, "\tSet mDstSizeRestrictions[%d], "
                            "[%d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d]",
                            i, mDstSizeRestrictions[i].maxDownScale, mDstSizeRestrictions[i].maxUpScale,
                            mDstSizeRestrictions[i].maxFullWidth, mDstSizeRestrictions[i].maxFullHeight,
                            mDstSizeRestrictions[i].minFullWidth, mDstSizeRestrictions[i].minFullHeight,
                            mDstSizeRestrictions[i].fullWidthAlign, mDstSizeRestrictions[i].fullHeightAlign,
                            mDstSizeRestrictions[i].maxCropWidth, mDstSizeRestrictions[i].maxCropHeight,
                            mDstSizeRestrictions[i].minCropWidth, mDstSizeRestrictions[i].minCropHeight,
                            mDstSizeRestrictions[i].cropXAlign, mDstSizeRestrictions[i].cropYAlign,
                            mDstSizeRestrictions[i].cropWidthAlign, mDstSizeRestrictions[i].cropHeightAlign);
                }
            }
        }
    }

    return NO_ERROR;
}

int64_t ExynosMPP::isSupported(ExynosDisplay &display, struct exynos_image &src, struct exynos_image &dst)
{
    uint32_t maxSrcWidth = getSrcMaxWidth(src);
    uint32_t maxSrcHeight = getSrcMaxHeight(src);
    uint32_t minSrcWidth = getSrcMinWidth(src);
    uint32_t minSrcHeight = getSrcMinHeight(src);
    uint32_t srcWidthAlign = getSrcWidthAlign(src);
    uint32_t srcHeightAlign = getSrcHeightAlign(src);

    uint32_t maxSrcCropWidth = getSrcMaxCropWidth(src);
    uint32_t maxSrcCropHeight = getSrcMaxCropHeight(src);
    uint32_t maxSrcCropSize = getSrcMaxCropSize(src);
    uint32_t minSrcCropWidth = getSrcMinCropWidth(src);
    uint32_t minSrcCropHeight = getSrcMinCropHeight(src);
    uint32_t srcCropWidthAlign = getSrcCropWidthAlign(src);
    uint32_t srcCropHeightAlign = getSrcCropHeightAlign(src);
    uint32_t srcXOffsetAlign = getSrcXOffsetAlign(src);
    uint32_t srcYOffsetAlign = getSrcYOffsetAlign(src);

    uint32_t maxDstWidth = getDstMaxWidth(dst);
    uint32_t maxDstHeight = getDstMaxHeight(dst);
    uint32_t minDstWidth = getDstMinWidth(dst);
    uint32_t minDstHeight = getDstMinHeight(dst);
    uint32_t dstWidthAlign = getDstWidthAlign(dst);
    uint32_t dstHeightAlign = getDstHeightAlign(dst);
    uint32_t dstXOffsetAlign = getDstXOffsetAlign(dst);
    uint32_t dstYOffsetAlign = getDstYOffsetAlign(dst);

    uint32_t maxDownscale = getMaxDownscale(display, src, dst);
    uint32_t maxUpscale = getMaxUpscale(src, dst);

    exynos_image rot_dst = dst;
    bool isPerpendicular = !!(src.transform & HAL_TRANSFORM_ROT_90);
    if (isPerpendicular) {
        rot_dst.w = dst.h;
        rot_dst.h = dst.w;
    }

    if (dst.w > maxDstWidth)
        return -eMPPExeedMaxDstWidth;
    else if (dst.h > maxDstHeight)
        return -eMPPExeedMaxDstHeight;
    else if (dst.w < minDstWidth)
        return -eMPPExeedMinDstWidth;
    else if (dst.h < minDstHeight)
        return -eMPPExeedMinDstHeight;
    else if (src.isDimLayer()) { // Dim layer
        if (isDimLayerSupported()) {
            return NO_ERROR;
        } else {
            return -eMPPUnsupportedDIMLayer;
        }
    }
    if (!isSupportedCapability(display, src))
        return -eMPPSaveCapability;
    else if (!isSrcFormatSupported(src))
        return -eMPPUnsupportedFormat;
    else if (!isDstFormatSupported(dst))
        return -eMPPUnsupportedFormat;
    else if (!isDataspaceSupportedByMPP(src, dst))
        return -eMPPUnsupportedCSC;
    else if (!isSupportedHDR(src, dst))
        return -eMPPUnsupportedDynamicMeta;
    else if (!isSupportedBlend(src))
        return -eMPPUnsupportedBlending;
    else if (!isSupportedTransform(src))
        return -eMPPUnsupportedRotation;
    else if (src.fullWidth < minSrcWidth)
        return -eMPPExeedMinSrcWidth;
    else if (src.fullHeight < minSrcHeight)
        return -eMPPExeedMinSrcHeight;
    else if (src.w < minSrcCropWidth)
        return -eMPPExeedSrcWCropMin;
    else if (src.h < minSrcCropHeight)
        return -eMPPExeedSrcHCropMin;
    else if ((dst.w % dstWidthAlign != 0) || (dst.h % dstHeightAlign != 0))
        return -eMPPNotAlignedDstSize;
    else if (src.w > rot_dst.w * maxDownscale)
        return -eMPPExeedMaxDownScale;
    else if (rot_dst.w > src.w * maxUpscale)
        return -eMPPExeedMaxUpScale;
    else if (src.h > rot_dst.h * maxDownscale)
        return -eMPPExeedMaxDownScale;
    else if (rot_dst.h > src.h * maxUpscale)
        return -eMPPExeedMaxUpScale;
    else if (!isSupportedDRM(src))
        return -eMPPUnsupportedDRM;
    else if (!isSupportedHStrideCrop(src))
        return -eMPPStrideCrop;
    else if (src.fullWidth > maxSrcWidth)
        return -eMPPExceedHStrideMaximum;
    else if (src.fullWidth % srcWidthAlign != 0)
        return -eMPPNotAlignedHStride;

    if ((src.w * src.h) > maxSrcCropSize)
        return -eMPPExeedSrcCropMax;

    if (getDrmMode(src.usageFlags) == NO_DRM) {
        if (src.fullHeight > maxSrcHeight)
            return -eMPPExceedVStrideMaximum;
        else if (src.fullHeight % srcHeightAlign != 0)
            return -eMPPNotAlignedVStride;
        else if (src.w > maxSrcCropWidth)
            return -eMPPExeedSrcWCropMax;
        else if (src.h > maxSrcCropHeight)
            return -eMPPExeedSrcHCropMax;
        else if ((src.w % srcCropWidthAlign != 0) || (src.h % srcCropHeightAlign != 0))
            return -eMPPNotAlignedCrop;
        else if ((src.x % srcXOffsetAlign != 0) || (src.y % srcYOffsetAlign != 0))
            return -eMPPNotAlignedOffset;
    }

    if ((dst.x % dstXOffsetAlign != 0) || (dst.y % dstYOffsetAlign != 0))
        return -eMPPNotAlignedOffset;

    if (!isSupportedCompression(src))
        return -eMPPUnsupportedCompression;

    if (!isSupportLayerColorTransform(src,dst))
        return -eMPPUnsupportedColorTransform;

    return NO_ERROR;
}

int32_t ExynosMPP::resetMPP()
{
    mAssignedState = MPP_ASSIGN_STATE_FREE;
    mAssignedDisplay = NULL;
    mAssignedSources.clear();
    resetUsedCapacity();
    mReservedDisplay = -1;
    mHWBusyFlag = false;

    return NO_ERROR;
}

int32_t ExynosMPP::resetAssignedState()
{
    for (int i = (int)mAssignedSources.size(); i-- > 0;) {
        ExynosMPPSource *mppSource = mAssignedSources[i];
        if (mppSource->mOtfMPP == this) {
            mppSource->mOtfMPP = NULL;
        }
        if (mppSource->mM2mMPP == this) {
            mppSource->mM2mMPP = NULL;
        }
        mAssignedSources.removeItemsAt(i);
    }

    /* Keep status if mAssignedState is MPP_ASSIGN_STATE_RESERVED */
    if ((mAssignedState & MPP_ASSIGN_STATE_ASSIGNED) &&
        (mAssignedSources.size() == 0)) {
        mAssignedState &= ~MPP_ASSIGN_STATE_ASSIGNED;
        mAssignedDisplay = NULL;
    }

    /* All mpp source are removed, reset capacity information */
    resetUsedCapacity();

    return NO_ERROR;
}

int32_t ExynosMPP::resetAssignedState(ExynosMPPSource *mppSource)
{
    bool needUpdateCapacity = false;
    for (int i = (int)mAssignedSources.size(); i-- > 0;) {
        ExynosMPPSource *source = mAssignedSources[i];
        if (source == mppSource) {
            if (mppSource->mM2mMPP == this) {
                mppSource->mM2mMPP = NULL;
            }
            /* Update information for used capacity */
            /* This should be called before mAssignedSources.removeItemsAt(mppSource) */
            needUpdateCapacity = removeCapacity(mppSource);

            mAssignedSources.removeItemsAt(i);

            if (needUpdateCapacity)
                updateUsedCapacity();

            break;
        }
    }

    /* Keep status if mAssignedState is MPP_ASSIGN_STATE_RESERVED */
    if ((mAssignedState & MPP_ASSIGN_STATE_ASSIGNED) &&
        (mAssignedSources.size() == 0)) {
        mAssignedState &= ~MPP_ASSIGN_STATE_ASSIGNED;
        mAssignedDisplay = NULL;
    }

    return NO_ERROR;
}

int32_t ExynosMPP::reserveMPP(int32_t displayId)
{
    mAssignedState |= MPP_ASSIGN_STATE_RESERVED;
    mReservedDisplay = displayId;

    return NO_ERROR;
}

int32_t ExynosMPP::assignMPP(ExynosDisplay *display, ExynosMPPSource* mppSource)
{
    mAssignedState |= MPP_ASSIGN_STATE_ASSIGNED;

    if (mMPPType == MPP_TYPE_OTF)
        mppSource->mOtfMPP = this;
    else if (mMPPType == MPP_TYPE_M2M)
        mppSource->mM2mMPP = this;
    else {
        MPP_LOGE("%s:: Invalid mppType(%d)", __func__, mMPPType);
        return -EINVAL;
    }

    mAssignedDisplay = display;

    /* Update information for used capacity */
    /* This should be called before mAssignedSources.add(mppSource) */
    bool needUpdateCapacity = addCapacity(mppSource);

    mAssignedSources.add(mppSource);

    MPP_LOGD(eDebugCapacity|eDebugMPP, "\tassigned to source(%p) type(%d), mAssignedSources(%zu)",
            mppSource, mppSource->mSourceType,
            mAssignedSources.size());

    if (needUpdateCapacity)
        updateUsedCapacity();

    if (mMaxSrcLayerNum > 1) {
        std::sort(mAssignedSources.begin(), mAssignedSources.end(), exynosMPPSourceComp);
    }

    return NO_ERROR;
}

uint32_t ExynosMPP::getSrcMaxBlendingNum(struct exynos_image __unused &src, struct exynos_image __unused &dst)
{
    uint32_t maxSrcLayerNum = mMaxSrcLayerNum;
    return maxSrcLayerNum;
}

uint32_t ExynosMPP::getAssignedSourceNum()
{
    return mAssignedSources.size();
}

/* Based on multi-resolution support */
void ExynosMPP::setDstAllocSize(uint32_t width, uint32_t height)
{
    switch(width) {
    case 720:
        mDstAllocatedSize = ((height >= 1480) ? DST_SIZE_HD_PLUS : DST_SIZE_HD);
        break;
    case 1080:
        mDstAllocatedSize = ((height >= 2220) ? DST_SIZE_FHD_PLUS : DST_SIZE_FHD);
        break;
    case 1440:
        mDstAllocatedSize = ((height >= 2960) ? DST_SIZE_WQHD_PLUS : DST_SIZE_WQHD);
        break;
    default:
        mDstAllocatedSize = DST_SIZE_UNKNOWN;
        break;
    }
}

dst_alloc_buf_size_t ExynosMPP::getDstAllocSize()
{
    return mDstAllocatedSize;
}

bool ExynosMPP::needPreAllocation()
{
    bool ret = false;

    if ((mLogicalType == MPP_LOGICAL_G2D_RGB) &&
        (mPreAssignDisplayList[mResourceManager->mDevice->mDisplayMode] == HWC_DISPLAY_PRIMARY_BIT))
        ret = true;

    return ret;
}

bool ExynosMPP::isAssignableState(ExynosDisplay *display, struct exynos_image &src, struct exynos_image &dst)
{
    bool isAssignable = false;

    if (mAssignedState == MPP_ASSIGN_STATE_FREE) {
        if (mHWState == MPP_HW_STATE_IDLE)
            isAssignable = true;
        else {
            if ((mPrevAssignedDisplayType < 0) ||
                ((uint32_t)mPrevAssignedDisplayType == display->mType))
                isAssignable = true;
            else
                isAssignable = false;
        }
    }

    if ((mAssignedState & MPP_ASSIGN_STATE_ASSIGNED) && (mAssignedState & MPP_ASSIGN_STATE_RESERVED))
    {
        if (mReservedDisplay == (int32_t)display->getId()) {
            if (mAssignedSources.size() < getSrcMaxBlendingNum(src, dst))
                isAssignable = true;
            else
                isAssignable = false;
        } else {
            isAssignable = false;
        }
    } else if ((mAssignedState & MPP_ASSIGN_STATE_ASSIGNED) && !(mAssignedState & MPP_ASSIGN_STATE_RESERVED)) {
        if (mAssignedSources.size() < getSrcMaxBlendingNum(src, dst))
            isAssignable = true;
        else
            isAssignable = false;
    } else if (mAssignedState & MPP_ASSIGN_STATE_RESERVED) {
        if (mReservedDisplay == (int32_t)display->getId())
            isAssignable = true;
        else
            isAssignable = false;
    }

    MPP_LOGD(eDebugMPP, "\tisAssignableState(%d), assigned size(%zu), getSrcMaxBlendingNum(%d)",
            isAssignable, mAssignedSources.size(), getSrcMaxBlendingNum(src, dst));
    return isAssignable;
}

bool ExynosMPP::isAssignable(ExynosDisplay *display, struct exynos_image &src,
                             struct exynos_image &dst, float totalUsedCapacity)
{
    bool isAssignable = isAssignableState(display, src, dst);
    return (isAssignable && hasEnoughCapa(display, src, dst, totalUsedCapacity));
}

bool ExynosMPP::hasEnoughCapa(ExynosDisplay *display, struct exynos_image &src,
                              struct exynos_image &dst, float totalUsedCapacity)
{
    if (mCapacity == -1)
        return true;

    MPP_LOGD(eDebugCapacity | eDebugMPP, "totalUsedCapacity(%f), mUsedCapacity(%f)",
             totalUsedCapacity, mUsedCapacity);

    /* mUsedCapacity should be re-calculated including src, dst passed as parameters*/
    totalUsedCapacity -= mUsedCapacity;

    float requiredCapacity = getRequiredCapacity(display, src, dst);

    MPP_LOGD(eDebugCapacity | eDebugMPP, "mCapacity(%f), usedCapacity(%f), RequiredCapacity(%f)",
             mCapacity, totalUsedCapacity, requiredCapacity);

    if (mCapacity >= (totalUsedCapacity + requiredCapacity))
        return true;
    else if (isCapacityExceptionCondition(totalUsedCapacity, requiredCapacity, src))
        return true;
    else
        return false;
}

bool ExynosMPP::isCapacityExceptionCondition(float totalUsedCapacity, float requiredCapacity,
                                             struct exynos_image &src)
{
    if ((hasHdrInfo(src) && (totalUsedCapacity == 0) &&
         (requiredCapacity < (mCapacity * MPP_HDR_MARGIN)))) {
        return true;
    } else {
        return false;
    }
}

void ExynosMPP::getPPCIndex(const struct exynos_image &src,
        const struct exynos_image &dst,
        uint32_t &formatIndex, uint32_t &rotIndex, uint32_t &scaleIndex,
        const struct exynos_image &criteria)
{
    formatIndex = 0;
    rotIndex = 0;
    scaleIndex = 0;

    /* Compare SBWC, AFBC and 10bitYUV420 first! because can be overlapped with other format */
    if (isFormatSBWC(criteria.format) && hasPPC(mPhysicalType, PPC_FORMAT_SBWC, PPC_ROT_NO))
        formatIndex = PPC_FORMAT_SBWC;
    else if (src.compressionInfo.type == COMP_TYPE_AFBC) {
        if ((isFormatRgb(criteria.format)) && hasPPC(mPhysicalType, PPC_FORMAT_AFBC_RGB, PPC_ROT_NO))
            formatIndex = PPC_FORMAT_AFBC_RGB;
        else if ((isFormatYUV(criteria.format)) && hasPPC(mPhysicalType, PPC_FORMAT_AFBC_YUV, PPC_ROT_NO))
            formatIndex = PPC_FORMAT_AFBC_YUV;
        else {
            formatIndex = PPC_FORMAT_RGB32;
            MPP_LOGW("%s:: AFBC PPC is not existed. Use default PPC", __func__);
        }
    } else if (isFormatP010(criteria.format) && hasPPC(mPhysicalType, PPC_FORMAT_P010, PPC_ROT_NO))
        formatIndex = PPC_FORMAT_P010;
    else if (isFormatYUV420(criteria.format) && hasPPC(mPhysicalType, PPC_FORMAT_YUV420, PPC_ROT_NO))
        formatIndex = PPC_FORMAT_YUV420;
    else if (isFormatYUV422(criteria.format) && hasPPC(mPhysicalType, PPC_FORMAT_YUV422, PPC_ROT_NO))
        formatIndex = PPC_FORMAT_YUV422;
    else
        formatIndex = PPC_FORMAT_RGB32;

    if (((criteria.transform & HAL_TRANSFORM_ROT_90) != 0) ||
        (mRotatedSrcCropBW > 0))
        rotIndex = PPC_ROT;
    else
        rotIndex = PPC_ROT_NO;

    uint32_t srcResolution = src.w * src.h;
    uint32_t dstResolution = dst.w * dst.h;

    if (mPhysicalType == MPP_G2D) {
        if (srcResolution == dstResolution) {
            scaleIndex = PPC_SCALE_NO;
        } else if (dstResolution > srcResolution) {
            /* scale up case */
            if (dstResolution >= (srcResolution * 4))
                scaleIndex = PPC_SCALE_UP_4_;
            else
                scaleIndex = PPC_SCALE_UP_1_4;
        } else {
            /* scale down case */
            if ((dstResolution * 16) <= srcResolution)
                scaleIndex = PPC_SCALE_DOWN_16_;
            else if (((dstResolution * 9) <= srcResolution) &&
                    (srcResolution < (dstResolution * 16)))
                scaleIndex = PPC_SCALE_DOWN_9_16;
            else if (((dstResolution * 4) <= srcResolution) &&
                    (srcResolution < (dstResolution * 9)))
                scaleIndex = PPC_SCALE_DOWN_4_9;
            else
                scaleIndex = PPC_SCALE_DOWN_1_4;
        }
    } else scaleIndex = 0; /* MSC doesn't refer scale Index */
}

float ExynosMPP::getPPC(const struct exynos_image &src,
        const struct exynos_image &dst, const struct exynos_image &criteria,
        const struct exynos_image *assignCheckSrc,
        const struct exynos_image *assignCheckDst)
{
    float PPC = 0;
    uint32_t formatIndex = 0;
    uint32_t rotIndex = 0;
    uint32_t scaleIndex = 0;

    getPPCIndex(src, dst, formatIndex, rotIndex, scaleIndex, criteria);

    if ((rotIndex == PPC_ROT_NO) && (assignCheckSrc != NULL) &&
        ((assignCheckSrc->transform & HAL_TRANSFORM_ROT_90) != 0)) {
        rotIndex = PPC_ROT;
    }

    if (mPhysicalType == MPP_G2D || mPhysicalType == MPP_MSC) {
        if (hasPPC(mPhysicalType, formatIndex, rotIndex)) {
            PPC = ppc_table_map.at(PPC_IDX(mPhysicalType, formatIndex, rotIndex)).ppcList[scaleIndex];
        }
    }

    if (PPC == 0) {
        MPP_LOGE("%s:: mPhysicalType(%d), formatIndex(%d), rotIndex(%d), scaleIndex(%d), PPC(%f) is not valid",
                __func__, mPhysicalType, formatIndex, rotIndex, scaleIndex, PPC);
        PPC = 0.000001;  /* It means can't use mPhysicalType H/W  */
    }

    MPP_LOGD(eDebugCapacity, "srcW(%d), srcH(%d), dstW(%d), dstH(%d), rot(%d)"
            "formatIndex(%d), rotIndex(%d), scaleIndex(%d), PPC(%f)",
            src.w, src.h, dst.w, dst.h, src.transform,
            formatIndex, rotIndex, scaleIndex, PPC);
    return PPC;
}

float ExynosMPP::getAssignedCapacity()
{
    float capacity = 0;
    float baseCycles = 0;
    uint32_t rotIndex = 0;

    if (mPhysicalType != MPP_G2D)
        return 0;

    /*
     * Client target is assigned to m2mMPP
     * even if capacity is not enough
     */
    if ((mAssignedDisplay != NULL) &&
        (mAssignedDisplay->mType == HWC_DISPLAY_VIRTUAL))
        return 0;


    for (uint32_t i = 0; i < mAssignedSources.size(); i++) {
        if ((mAssignedSources[i]->mSrcImg.transform & HAL_TRANSFORM_ROT_90) != 0)
            rotIndex = PPC_ROT;
    }

    MPP_LOGD(eDebugCapacity, "Check all of assigned layers cycles");
    /* PPC of layers that were added before should be changed */
    /* Check cycles of all assigned layers again */
    if ((mAssignedDisplay != NULL) && (mMaxSrcLayerNum > 1)) {
        baseCycles += ((mAssignedDisplay->mXres * mAssignedDisplay->mYres) / G2D_BASE_PPC_COLORFILL);
        MPP_LOGD(eDebugCapacity, "colorfill cycles: %f, total cycles: %f",
                ((mAssignedDisplay->mXres * mAssignedDisplay->mYres) / G2D_BASE_PPC_COLORFILL), baseCycles);
    }

    for (uint32_t i = 0; i < mAssignedSources.size(); i++) {
        float srcCycles = 0;
        uint32_t srcResolution = mAssignedSources[i]->mSrcImg.w * mAssignedSources[i]->mSrcImg.h;
        uint32_t dstResolution = mAssignedSources[i]->mMidImg.w * mAssignedSources[i]->mMidImg.h;
        uint32_t maxResolution = max(srcResolution, dstResolution);
        float PPC = 0;

        if (mAssignedSources[i]->mSrcImg.layerFlags & EXYNOS_HWC_DIM_LAYER) {
            PPC = G2D_BASE_PPC_COLORFILL;
        } else {
            PPC = getPPC(mAssignedSources[i]->mSrcImg, mAssignedSources[i]->mMidImg, mAssignedSources[i]->mSrcImg,
                    &mAssignedSources[i]->mSrcImg, &mAssignedSources[i]->mMidImg);
        }
        srcCycles = maxResolution/PPC;

        /* Hdr and drm layer is exception */
        if ((hasHdrInfo(mAssignedSources[i]->mSrcImg) ||
            (getDrmMode(mAssignedSources[i]->mSrcImg.usageFlags) != NO_DRM))) {
            MPP_LOGD(eDebugCapacity, "Src[%d] is skipped(drm or hdr), cycles: %f, PPC: %f, srcResolution: %d, dstResolution: %d, rot(%d)",
                    i, srcCycles, PPC, srcResolution, dstResolution, mAssignedSources[i]->mSrcImg.transform);
            continue;
        }

        baseCycles += srcCycles;

        MPP_LOGD(eDebugCapacity, "Src[%d] cycles: %f, total cycles: %f, PPC: %f, srcResolution: %d, dstResolution: %d, rot(%d)",
                i, srcCycles, baseCycles, PPC, srcResolution, dstResolution, mAssignedSources[i]->mSrcImg.transform);
    }

    capacity = baseCycles / mClockKhz;

    return capacity;
}

float ExynosMPP::getRequiredCapacity(ExynosDisplay *display, struct exynos_image &src,
        struct exynos_image &dst)
{
    float capacity = 0;
    float cycles = 0;
    if (mPhysicalType == MPP_G2D) {
        /* Initialize value with the cycles that were already assigned */
        float baseCycles = mUsedBaseCycles;
        float srcCycles = 0;
        uint32_t srcResolution = src.w * src.h;
        uint32_t dstResolution = dst.w * dst.h;
        uint32_t maxResolution = max(srcResolution, dstResolution);
        float curBaseCycles = 0;
        float PPC = 0;

        if ((mAssignedSources.size() == 0) ||
            (mRotatedSrcCropBW != 0) ||
            ((mRotatedSrcCropBW == 0) &&
             ((src.transform & HAL_TRANSFORM_ROT_90) == 0))) {
            /* Just add cycles for current layer */
            if ((mAssignedSources.size() == 0) &&
                (display != NULL) && (mMaxSrcLayerNum > 1)) {
                curBaseCycles = ((display->mXres * display->mYres) / G2D_BASE_PPC_COLORFILL);
                MPP_LOGD(eDebugCapacity, "There is no assigned layer. Colorfill cycles: %f should be added",
                        curBaseCycles);
            }
            curBaseCycles += getRequiredBaseCycles(src, dst);
            baseCycles += curBaseCycles;
            MPP_LOGD(eDebugCapacity, "mUsedBaseCycles was %f, Add base cycles %f, totalBaseCycle(%f)",
                    mUsedBaseCycles, curBaseCycles, baseCycles);
        } else {
            /* Recalculate cycles for all of layers */
            baseCycles = 0;
            MPP_LOGD(eDebugCapacity, "Check all of assigned layers cycles");
            /* PPC of layers that were added before should be changed */
            /* Check cycles of all assigned layers again */
            if ((display != NULL) && (mMaxSrcLayerNum > 1)) {
                baseCycles += ((display->mXres * display->mYres) / G2D_BASE_PPC_COLORFILL);
                MPP_LOGD(eDebugCapacity, "colorfill cycles: %f, total cycles: %f",
                        ((display->mXres * display->mYres) / G2D_BASE_PPC_COLORFILL), cycles);
            }

            for (uint32_t i = 0; i < mAssignedSources.size(); i++) {
                float assignedSrcCycles = 0;
                uint32_t assignedSrcResolution = mAssignedSources[i]->mSrcImg.w * mAssignedSources[i]->mSrcImg.h;
                uint32_t assignedDstResolution = mAssignedSources[i]->mMidImg.w * mAssignedSources[i]->mMidImg.h;
                uint32_t assignedMaxResolution = max(assignedSrcResolution, assignedDstResolution);
                float assignedPPC = getPPC(mAssignedSources[i]->mSrcImg, mAssignedSources[i]->mMidImg,
                        mAssignedSources[i]->mSrcImg, &src, &dst);

                assignedSrcCycles = assignedMaxResolution/assignedPPC;
                baseCycles += assignedSrcCycles;

                MPP_LOGD(eDebugCapacity, "Src[%d] cycles: %f, total cycles: %f, PPC: %f, srcResolution: %d, dstResolution: %d, rot(%d)",
                        i, assignedSrcCycles, baseCycles, assignedPPC, assignedSrcResolution, assignedDstResolution, mAssignedSources[i]->mSrcImg.transform);
            }

            PPC = getPPC(src, dst, src, &src, &dst);

            srcCycles = maxResolution/PPC;
            baseCycles += srcCycles;

            MPP_LOGD(eDebugCapacity, "check mppSource cycles: %f, total cycles: %f, PPC: %f, srcResolution: %d, dstResolution: %d, rot(%d)",
                    srcCycles, baseCycles, PPC, srcResolution, dstResolution, src.transform);
        }

        capacity = baseCycles / mClockKhz;

        MPP_LOGD(eDebugCapacity, "baseCycles: %f, capacity: %f",
                baseCycles, capacity);
    } else if (mPhysicalType == MPP_MSC) {
        /* Initialize value with the capacity that were already assigned */
        capacity = mUsedCapacity;

        /* Just add capacity for current layer */
        float srcPPC = getPPC(src, dst, src);
        float dstPPC = getPPC(src, dst, dst);
        float srcCapacity = (float((src.w * src.h))) / (mClockKhz * srcPPC);
        float dstCapacity = (float((dst.w * dst.h))) / (mClockKhz * dstPPC);

        capacity += max(srcCapacity, dstCapacity);

    }

    return capacity;
}

float ExynosMPP::getRequiredBaseCycles(struct exynos_image &src, struct exynos_image &dst)
{
    if (mPhysicalType != MPP_G2D)
        return 0;

    uint32_t srcResolution = src.w * src.h;
    uint32_t dstResolution = dst.w * dst.h;
    uint32_t maxResolution = max(srcResolution, dstResolution);

    return maxResolution/(float)getPPC(src, dst, src);
}

bool ExynosMPP::addCapacity(ExynosMPPSource* mppSource)
{
    if ((mppSource == NULL) || mCapacity == -1)
        return false;

    if (mPhysicalType == MPP_G2D) {
        bool needUpdateCapacity = true;
        if ((mAssignedSources.size() == 0) ||
            (mRotatedSrcCropBW != 0) ||
            ((mRotatedSrcCropBW == 0) &&
             ((mppSource->mSrcImg.transform & HAL_TRANSFORM_ROT_90) == 0))) {
            needUpdateCapacity = false;
        }

        if (needUpdateCapacity)
            return true;

        if ((mMaxSrcLayerNum > 1) &&
            (mAssignedSources.size() == 0)) {
            if (mAssignedDisplay != NULL) {
                /* This will be the first mppSource that is assigned to the ExynosMPP */
                /* Add capacity for background */
                mUsedBaseCycles += ((mAssignedDisplay->mXres * mAssignedDisplay->mYres) / G2D_BASE_PPC_COLORFILL);
                MPP_LOGD(eDebugCapacity, "\tcolorfill cycles: %f, total cycles: %f",
                        ((mAssignedDisplay->mXres * mAssignedDisplay->mYres) / G2D_BASE_PPC_COLORFILL), mUsedBaseCycles);
            } else {
                MPP_LOGE("mAssignedDisplay is null");
            }
        }

        float baseCycles = getRequiredBaseCycles(mppSource->mSrcImg, mppSource->mMidImg);
        mUsedBaseCycles += baseCycles;

        uint32_t srcResolution = mppSource->mSrcImg.w * mppSource->mSrcImg.h;
        uint32_t dstResolution = mppSource->mMidImg.w * mppSource->mMidImg.h;
        if ((mppSource->mSrcImg.transform & HAL_TRANSFORM_ROT_90) == 0)
            mNoRotatedSrcCropBW += srcResolution;
        else
            mRotatedSrcCropBW += srcResolution;

        mUsedCapacity = mUsedBaseCycles / mClockKhz;

        MPP_LOGD(eDebugCapacity, "src num: %zu base cycle is added: %f, mUsedBaseCycles: %f, mUsedCapacity(%f), srcResolution: %d, dstResolution: %d, rot: %d, mNoRotatedSrcCropBW(%d), mRotatedSrcCropBW(%d)",
                mAssignedSources.size(),
                baseCycles, mUsedBaseCycles, mUsedCapacity, srcResolution, dstResolution,
                mppSource->mSrcImg.transform, mNoRotatedSrcCropBW, mRotatedSrcCropBW);
    } else if (mPhysicalType == MPP_MSC) {
        mUsedCapacity = getRequiredCapacity(NULL, mppSource->mSrcImg, mppSource->mMidImg);
    }

    return false;
}

bool ExynosMPP::removeCapacity(ExynosMPPSource* mppSource)
{
    if ((mppSource == NULL) || (mCapacity == -1))
        return false;

    if (mPhysicalType == MPP_G2D) {
        uint32_t srcResolution = mppSource->mSrcImg.w * mppSource->mSrcImg.h;
        uint32_t dstResolution = mppSource->mDstImg.w * mppSource->mDstImg.h;

        uint32_t prevRotatedSrcCropBW = mRotatedSrcCropBW;

        if (mppSource->mSrcImg.transform == 0)
            mNoRotatedSrcCropBW -= srcResolution;
        else
            mRotatedSrcCropBW -= srcResolution;

        if ((prevRotatedSrcCropBW > 0) && (mRotatedSrcCropBW == 0))
            return true;

        float baseCycles = getRequiredBaseCycles(mppSource->mSrcImg, mppSource->mMidImg);
        mUsedBaseCycles -= baseCycles;

        mUsedCapacity = mUsedBaseCycles / mClockKhz;

        MPP_LOGD(eDebugCapacity, "src num: %zu, base cycle is removed: %f, mUsedBaseCycles: %f, mUsedCapacity(%f), srcResolution: %d, dstResolution: %d, rot: %d, mNoRotatedSrcCropBW(%d), mRotatedSrcCropBW(%d)",
                mAssignedSources.size(),
                baseCycles, mUsedBaseCycles, mUsedCapacity, srcResolution, dstResolution,
                mppSource->mSrcImg.transform, mNoRotatedSrcCropBW, mRotatedSrcCropBW);
    } else if (mPhysicalType == MPP_MSC) {
        exynos_image &src = mppSource->mSrcImg;
        exynos_image &dst = mppSource->mDstImg;
        uint32_t srcResolution = src.w * src.h;
        uint32_t dstResolution = dst.w * dst.h;

        float srcCapacity = (float)srcResolution / getPPC(src, dst, src);
        float dstCapacity  = (float)dstResolution  / getPPC(src, dst, dst);

        mUsedCapacity -= max(srcCapacity, dstCapacity);
    }

    return false;
}

void ExynosMPP::resetUsedCapacity()
{
    mUsedCapacity = 0;
    mUsedBaseCycles = 0;
    mRotatedSrcCropBW = 0;
    mNoRotatedSrcCropBW = 0;
}

int32_t ExynosMPP::updateUsedCapacity()
{
    int32_t ret = NO_ERROR;
    if (mCapacity == -1)
        return ret;

    float capacity = 0;
    mUsedCapacity = 0;

    mRotatedSrcCropBW = 0;
    mNoRotatedSrcCropBW = 0;

    if ((mPhysicalType == MPP_G2D) &&
        (mAssignedDisplay != NULL) &&
        (mAssignedSources.size() > 0)) {
        float cycles = 0;

        if (mMaxSrcLayerNum > 1) {
            cycles += ((mAssignedDisplay->mXres * mAssignedDisplay->mYres) / G2D_BASE_PPC_COLORFILL);
            MPP_LOGD(eDebugCapacity, "\tcolorfill cycles: %f, total cycles: %f",
                    ((mAssignedDisplay->mXres * mAssignedDisplay->mYres) / G2D_BASE_PPC_COLORFILL), cycles);
        }
        for (uint32_t i = 0; i < mAssignedSources.size(); i++) {
            uint32_t srcResolution = mAssignedSources[i]->mSrcImg.w * mAssignedSources[i]->mSrcImg.h;
            if ((mAssignedSources[i]->mSrcImg.transform & HAL_TRANSFORM_ROT_90) == 0)
                mNoRotatedSrcCropBW += srcResolution;
            else
                mRotatedSrcCropBW += srcResolution;
        }
        MPP_LOGD(eDebugCapacity, "mNoRotatedSrcCropBW(%d), mRotatedSrcCropBW(%d)",
                mNoRotatedSrcCropBW, mRotatedSrcCropBW);
        for (uint32_t i = 0; i < mAssignedSources.size(); i++) {
            float srcCycles = 0;
            uint32_t srcResolution = mAssignedSources[i]->mSrcImg.w * mAssignedSources[i]->mSrcImg.h;
            uint32_t dstResolution = mAssignedSources[i]->mMidImg.w * mAssignedSources[i]->mMidImg.h;
            uint32_t maxResolution = max(srcResolution, dstResolution);
            float PPC = getPPC(mAssignedSources[i]->mSrcImg, mAssignedSources[i]->mMidImg, mAssignedSources[i]->mSrcImg);
            srcCycles = maxResolution/PPC;
            cycles += srcCycles;

            MPP_LOGD(eDebugCapacity, "Src[%d] cycles: %f, total cycles: %f, PPC: %f, srcResolution: %d, dstResolution: %d, rot(%d)",
                    i, srcCycles, cycles, PPC, srcResolution, dstResolution, mAssignedSources[i]->mSrcImg.transform);
        }

        mUsedBaseCycles = cycles;
        capacity = cycles / mClockKhz;

        mUsedCapacity = capacity;

    }
    MPP_LOGD(eDebugCapacity, "assigned layer size(%zu), mUsedCapacity: %f", mAssignedSources.size(), mUsedCapacity);

    return mUsedCapacity;
}

uint32_t ExynosMPP::getRestrictionClassification(const struct exynos_image &img) const {
    return !!(isFormatRgb(img.format) == false);
}

int ExynosMPP::prioritize(int priority)
{
    if ((mPhysicalType != MPP_G2D) ||
        (mAcrylicHandle == NULL)) {
        MPP_LOGE("invalid function call");
        return -1;
    }
    int ret = NO_ERROR;
    ret = mAcrylicHandle->prioritize(priority);

    if ((priority > 0) && (ret == 1))
    {
        /* G2D Driver returned EBUSY */
        mHWBusyFlag = true;
    }
    MPP_LOGD(eDebugMPP, "set resource prioritize (%d), ret(%d), mHWBusyFlag(%d)", priority, ret, mHWBusyFlag);

    return ret;
}

uint32_t ExynosMPP::increaseDstBuffIndex()
{
    if (mAllocOutBufFlag)
        mCurrentDstBuf = (mCurrentDstBuf + 1) % NUM_MPP_DST_BUFS(mLogicalType);
    return mCurrentDstBuf;
}

void ExynosMPP::reloadResourceForHWFC()
{
    ALOGI("reloadResourceForHWFC()");
    delete mAcrylicHandle;
    mAcrylicHandle = AcrylicFactory::createAcrylic("default_compositor");
    if (mAcrylicHandle == NULL) {
        MPP_LOGE("Fail to allocate compositor");
    } else {
        mAcrylicHandle->setDefaultColor(0, 0, 0, 0);
        MPP_LOGI("The resource is reloaded for HWFC: %p", mAcrylicHandle);
    }
    for (uint32_t i = 0; i < NUM_MPP_SRC_BUFS; i++)
    {
        if (mSrcImgs[i].mppLayer != NULL) {
            delete mSrcImgs[i].mppLayer;
            mSrcImgs[i].mppLayer = NULL;
        }
    }
}

void ExynosMPP::setTargetDisplayLuminance(uint16_t min, uint16_t max)
{
    MPP_LOGD(eDebugMPP, "%s: min(%d), max(%d)", __func__, min, max);
    if (mAcrylicHandle == NULL) {
        MPP_LOGE("mAcrylicHandle is NULL");
    } else
        mAcrylicHandle->setTargetDisplayLuminance(min, max);
}

void ExynosMPP::setTargetDisplayDevice(int device)
{
    ALOGI("%s: device(%d)", __func__, device);
    if (mAcrylicHandle == NULL) {
        MPP_LOGE("mAcrylicHandle is NULL");
    } else
        mAcrylicHandle->setTargetDisplayInfo(&device);
}

void ExynosMPP::dump(String8& result)
{
    int32_t assignedDisplayType = -1;
    if (mAssignedDisplay != NULL)
        assignedDisplayType = mAssignedDisplay->mType;

    result.appendFormat("%s: types mppType(%d), (p:%d, l:0x%2x), indexs(p:%d, l:%d), preAssignDisplay(0x%2x)\n",
            mName.c_str(), mMPPType, mPhysicalType, mLogicalType, mPhysicalIndex, mLogicalIndex, mPreAssignDisplayInfo);
    result.appendFormat("\tEnable: %d, HWState: %d, AssignedState: %d, assignedDisplay(%d)\n",
            mEnable, mHWState, mAssignedState, assignedDisplayType);
    result.appendFormat("\tPrevAssignedState: %d, PrevAssignedDisplayType: %d, ReservedDisplay: %d\n",
            mPrevAssignedState, mPrevAssignedDisplayType, mReservedDisplay);
    result.appendFormat("\tassinedSourceNum(%zu), Capacity(%f), CapaUsed(%f), mCurrentDstBuf(%d)\n",
            mAssignedSources.size(), mCapacity, mUsedCapacity, mCurrentDstBuf);

}

void ExynosMPP::closeFences()
{
    for (uint32_t i = 0; i < mAssignedSources.size(); i++)
    {
        mSrcImgs[i].acrylicAcquireFenceFd =
            fence_close(mSrcImgs[i].acrylicAcquireFenceFd, mAssignedDisplay,
                    FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_G2D);
        mSrcImgs[i].acrylicReleaseFenceFd =
            fence_close(mSrcImgs[i].acrylicReleaseFenceFd, mAssignedDisplay,
                    FENCE_TYPE_SRC_RELEASE, FENCE_IP_G2D);
    }

    mDstImgs[mCurrentDstBuf].acrylicAcquireFenceFd =
        fence_close(mDstImgs[mCurrentDstBuf].acrylicAcquireFenceFd, mAssignedDisplay,
                FENCE_TYPE_DST_ACQUIRE, FENCE_IP_G2D);
    mDstImgs[mCurrentDstBuf].acrylicReleaseFenceFd =
        fence_close(mDstImgs[mCurrentDstBuf].acrylicReleaseFenceFd, mAssignedDisplay,
                FENCE_TYPE_DST_RELEASE, FENCE_IP_G2D);
}

void ExynosMPP::updateAttr()
{
    MPP_LOGD(eDebugAttrSetting, "updateAttr::mPhysicalType(%d), mAttr(0x%" PRIx64 ")",
            mPhysicalType, mAttr);

    if (mResourceManager == NULL) return;

    auto iter = mResourceManager->mMPPAttrs.find(mPhysicalType);
    if (iter != mResourceManager->mMPPAttrs.end()) {
        mAttr = iter->second;
        MPP_LOGD(eDebugAttrSetting, "After mAttr(0x%" PRIx64 ")", mAttr);
    }
}

void ExynosMPP::updatePreassignedDisplay(uint32_t fromDisplayBit, uint32_t toDisplayBit)
{
    /*
     * If the pre-assigned resources are required to changed,
     * this function will modify PreAssign table.
     */
    for (uint32_t i = 0; i < DISPLAY_MODE_NUM; i++) {
        if (mPreAssignDisplayList[i] == fromDisplayBit)
            mPreAssignDisplayList[i] = toDisplayBit;
    }

    if (mPreAssignDisplayInfo == fromDisplayBit)
        mPreAssignDisplayInfo = toDisplayBit;
}
