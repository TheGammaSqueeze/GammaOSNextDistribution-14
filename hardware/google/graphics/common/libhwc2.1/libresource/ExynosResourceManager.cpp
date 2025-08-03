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
#include "ExynosResourceManager.h"

#include <cutils/properties.h>

#include <numeric>
#include <unordered_set>

#include "ExynosDeviceInterface.h"
#include "ExynosExternalDisplay.h"
#include "ExynosHWCDebug.h"
#include "ExynosLayer.h"
#include "ExynosMPPModule.h"
#include "ExynosPrimaryDisplayModule.h"
#include "ExynosVirtualDisplay.h"
#include "hardware/exynos/acryl.h"

using namespace std::chrono_literals;
constexpr float msecsPerSec = std::chrono::milliseconds(1s).count();

using namespace android;
using namespace vendor::graphics;
using namespace SOC_VERSION;

ExynosMPPVector ExynosResourceManager::mOtfMPPs;
ExynosMPPVector ExynosResourceManager::mM2mMPPs;
extern struct exynos_hwc_control exynosHWCControl;

ExynosMPPVector::ExynosMPPVector() {
}

ExynosMPPVector::ExynosMPPVector(const ExynosMPPVector& rhs)
    : android::SortedVector<ExynosMPP* >(rhs) {
}

int ExynosMPPVector::do_compare(const void* lhs, const void* rhs) const
{
    if (lhs == NULL || rhs == NULL)
        return 0;

    const ExynosMPP* l = *((ExynosMPP**)(lhs));
    const ExynosMPP* r = *((ExynosMPP**)(rhs));

    if (l == NULL || r == NULL)
        return 0;

    if (l->mPhysicalType != r->mPhysicalType) {
        return l->mPhysicalType - r->mPhysicalType;
    }

    if (l->mLogicalType != r->mLogicalType) {
        return l->mLogicalType - r->mLogicalType;
    }

    if (l->mPhysicalIndex != r->mPhysicalIndex) {
        return l->mPhysicalIndex - r->mPhysicalIndex;
    }

    return l->mLogicalIndex - r->mLogicalIndex;
}
/**
 * ExynosResourceManager implementation
 *
 */

ExynosResourceManager::DstBufMgrThread::DstBufMgrThread(ExynosResourceManager *exynosResourceManager)
: mExynosResourceManager(exynosResourceManager),
    mRunning(false),
    mBufXres(0),
    mBufYres(0)
{
}

ExynosResourceManager::DstBufMgrThread::~DstBufMgrThread()
{
}


ExynosResourceManager::ExynosResourceManager(ExynosDevice *device)
: mForceReallocState(DST_REALLOC_DONE),
    mDevice(device),
    hasHdrLayer(false),
    hasDrmLayer(false),
    mFormatRestrictionCnt(0),
    mDstBufMgrThread(sp<DstBufMgrThread>::make(this)),
    mResourceReserved(0x0)
{

    memset(mSizeRestrictionCnt, 0, sizeof(mSizeRestrictionCnt));
    memset(mFormatRestrictions, 0, sizeof(mFormatRestrictions));
    memset(mSizeRestrictions, 0, sizeof(mSizeRestrictions));

    size_t num_mpp_units = sizeof(available_otf_mpp_units)/sizeof(exynos_mpp_t);
    for (size_t i = 0; i < num_mpp_units; i++) {
        exynos_mpp_t exynos_mpp = available_otf_mpp_units[i];
        ALOGI("otfMPP type(%d, %d), physical_index(%d), logical_index(%d)",
                exynos_mpp.physicalType, exynos_mpp.logicalType,
                exynos_mpp.physical_index, exynos_mpp.logical_index);
        ExynosMPP* exynosMPP = new ExynosMPPModule(this, exynos_mpp.physicalType,
                exynos_mpp.logicalType, exynos_mpp.name, exynos_mpp.physical_index,
                exynos_mpp.logical_index, exynos_mpp.pre_assign_info);
        exynosMPP->mMPPType = MPP_TYPE_OTF;
        exynosMPP->initTDMInfo(exynos_mpp.hw_block_index, exynos_mpp.axi_port_index);
        mOtfMPPs.add(exynosMPP);
    }

    num_mpp_units = sizeof(AVAILABLE_M2M_MPP_UNITS)/sizeof(exynos_mpp_t);
    for (size_t i = 0; i < num_mpp_units; i++) {
        exynos_mpp_t exynos_mpp = AVAILABLE_M2M_MPP_UNITS[i];
        ALOGI("m2mMPP type(%d, %d), physical_index(%d), logical_index(%d)",
                exynos_mpp.physicalType, exynos_mpp.logicalType,
                exynos_mpp.physical_index, exynos_mpp.logical_index);
        ExynosMPP* exynosMPP = new ExynosMPPModule(this, exynos_mpp.physicalType,
                exynos_mpp.logicalType, exynos_mpp.name, exynos_mpp.physical_index,
                exynos_mpp.logical_index, exynos_mpp.pre_assign_info);
        exynosMPP->mMPPType = MPP_TYPE_M2M;
        mM2mMPPs.add(exynosMPP);
    }

    ALOGI("mOtfMPPs(%zu), mM2mMPPs(%zu)", mOtfMPPs.size(), mM2mMPPs.size());
    if (hwcCheckDebugMessages(eDebugResourceManager)) {
        for (uint32_t i = 0; i < mOtfMPPs.size(); i++)
        {
            HDEBUGLOGD(eDebugResourceManager, "otfMPP[%d]", i);
            String8 dumpMPP;
            mOtfMPPs[i]->dump(dumpMPP);
            HDEBUGLOGD(eDebugResourceManager, "%s", dumpMPP.c_str());
        }
        for (uint32_t i = 0; i < mM2mMPPs.size(); i++)
        {
            HDEBUGLOGD(eDebugResourceManager, "m2mMPP[%d]", i);
            String8 dumpMPP;
            mM2mMPPs[i]->dump(dumpMPP);
            HDEBUGLOGD(eDebugResourceManager, "%s", dumpMPP.c_str());
        }
    }

    mDstBufMgrThread->mRunning = true;
    mDstBufMgrThread->run("DstBufMgrThread");

    char value[PROPERTY_VALUE_MAX];
    mMinimumSdrDimRatio = property_get("debug.hwc.min_sdr_dimming", value, nullptr) > 0
                          ? std::atof(value) : 0.0f;
    updateSupportWCG();
}

ExynosResourceManager::~ExynosResourceManager()
{
    for (int32_t i = mOtfMPPs.size(); i-- > 0;) {
        ExynosMPP *exynosMPP = mOtfMPPs[i];
        delete exynosMPP;
    }
    mOtfMPPs.clear();
    for (int32_t i = mM2mMPPs.size(); i-- > 0;) {
        ExynosMPP *exynosMPP = mM2mMPPs[i];
        delete exynosMPP;
    }
    mM2mMPPs.clear();

    mDstBufMgrThread->mRunning = false;
    mDstBufMgrThread->requestExitAndWait();
}

void ExynosResourceManager::reloadResourceForHWFC()
{
    for (int32_t i = mM2mMPPs.size(); i-- > 0;) {
        ExynosMPP *exynosMPP = mM2mMPPs[i];
        if (exynosMPP->mLogicalType == MPP_LOGICAL_G2D_COMBO &&
                (exynosMPP->mPreAssignDisplayInfo & HWC_DISPLAY_VIRTUAL_BIT)) {
            exynosMPP->reloadResourceForHWFC();
            break;
        }
    }
}

void ExynosResourceManager::setTargetDisplayLuminance(uint16_t min, uint16_t max)
{
    for (int32_t i = mM2mMPPs.size(); i-- > 0;) {
        ExynosMPP *exynosMPP = mM2mMPPs[i];
        if (exynosMPP->mLogicalType == MPP_LOGICAL_G2D_COMBO &&
                (exynosMPP->mPreAssignDisplayInfo & HWC_DISPLAY_VIRTUAL_BIT)) {
            exynosMPP->setTargetDisplayLuminance(min, max);
            break;
        }
    }
}

void ExynosResourceManager::setTargetDisplayDevice(int device)
{
    for (int32_t i = mM2mMPPs.size(); i-- > 0;) {
        ExynosMPP *exynosMPP = mM2mMPPs[i];
        if (exynosMPP->mLogicalType == MPP_LOGICAL_G2D_COMBO &&
                (exynosMPP->mPreAssignDisplayInfo & HWC_DISPLAY_VIRTUAL_BIT)) {
            exynosMPP->setTargetDisplayDevice(device);
            break;
        }
    }
}

int32_t ExynosResourceManager::doPreProcessing()
{
    int32_t ret = NO_ERROR;
    /* Assign m2mMPP's out buffers */
    ExynosDisplay *display = mDevice->getDisplay(getDisplayId(HWC_DISPLAY_PRIMARY, 0));
    if (display == NULL)
        return -EINVAL;
    ret = doAllocDstBufs(display->mXres, display->mYres);
    return ret;
}

void ExynosResourceManager::doReallocDstBufs(uint32_t Xres, uint32_t Yres)
{
    HDEBUGLOGD(eDebugBuf, "M2M dst alloc call ");
    mDstBufMgrThread->reallocDstBufs(Xres, Yres);
}

bool ExynosResourceManager::DstBufMgrThread::needDstRealloc(uint32_t Xres, uint32_t Yres, ExynosMPP *m2mMPP)
{
    bool ret = false;
    if (((Xres == 720 && Yres == 1480) && (m2mMPP->getDstAllocSize() != DST_SIZE_HD_PLUS)) ||
            ((Xres == 720 && Yres == 1280) && (m2mMPP->getDstAllocSize() != DST_SIZE_HD)) ||
            ((Xres == 1080 && Yres == 2220) && (m2mMPP->getDstAllocSize() != DST_SIZE_FHD_PLUS)) ||
            ((Xres == 1080 && Yres == 1920) && (m2mMPP->getDstAllocSize() != DST_SIZE_FHD)) ||
            ((Xres == 1440 && Yres == 2960) && (m2mMPP->getDstAllocSize() != DST_SIZE_WQHD_PLUS)) ||
            ((Xres == 1440 && Yres == 2560) && (m2mMPP->getDstAllocSize() != DST_SIZE_WQHD))) {
        ret = true;
    }
    return ret;
}

void ExynosResourceManager::DstBufMgrThread::reallocDstBufs(uint32_t Xres, uint32_t Yres)
{
    bool needRealloc = false;
    for (uint32_t i = 0; i < mM2mMPPs.size(); i++) {
        if (mM2mMPPs[i]->needPreAllocation())
        {
            if (needDstRealloc(Xres, Yres, mM2mMPPs[i])) {
                HDEBUGLOGD(eDebugBuf, "M2M dst alloc : %d Realloc Start ++++++", mM2mMPPs[i]->mLogicalType);
                needRealloc = true;
            }
            else HDEBUGLOGD(eDebugBuf, "M2M dst alloc : %d MPP's DST Realloc is not needed : Size is same", mM2mMPPs[i]->mLogicalType);
        }
    }

    if (needRealloc) {
        Mutex::Autolock lock(mStateMutex);
        if (mExynosResourceManager->mForceReallocState == DST_REALLOC_DONE) {
            mExynosResourceManager->mForceReallocState = DST_REALLOC_START;
            android::Mutex::Autolock lock(mMutex);
            mCondition.signal();
        } else {
            HDEBUGLOGD(eDebugBuf, "M2M dst alloc thread : queue aready.");
        }
    }
}

bool ExynosResourceManager::DstBufMgrThread::threadLoop()
{
    while(mRunning) {
        Mutex::Autolock lock(mMutex);
        mCondition.wait(mMutex);

        ExynosDevice *device = mExynosResourceManager->mDevice;
        if (device == NULL)
            return false;

        /* TODO(b/265244856): to clarify which display size to alloc */
        ExynosDisplay *display = device->getDisplay(getDisplayId(HWC_DISPLAY_PRIMARY, 0));
        if (display == NULL)
            return false;

        do {
            {
                HDEBUGLOGD(eDebugBuf, "M2M dst alloc %d, %d, %d, %d : Realloc On going ----------",
                        mBufXres, display->mXres, mBufYres, display->mYres);
                Mutex::Autolock lock(mResInfoMutex);
                mBufXres = display->mXres;mBufYres = display->mYres;
            }
            mExynosResourceManager->doAllocDstBufs(mBufXres, mBufYres);
        } while (mBufXres != display->mXres || mBufYres != display->mYres);

        {
            Mutex::Autolock lock(mStateMutex);
            mExynosResourceManager->mForceReallocState = DST_REALLOC_DONE;
            HDEBUGLOGD(eDebugBuf, "M2M dst alloc %d, %d, %d, %d : Realloc On Done ----------",
                    mBufXres, display->mXres, mBufYres, display->mYres);
        }
    }
    return true;
}

int32_t ExynosResourceManager::doAllocDstBufs(uint32_t Xres, uint32_t Yres)
{
    ATRACE_CALL();
    int32_t ret = NO_ERROR;
    /* Assign m2mMPP's out buffers */

    for (uint32_t i = 0; i < mM2mMPPs.size(); i++) {
        if (mM2mMPPs[i]->needPreAllocation())
        {
            mM2mMPPs[i]->mFreeOutBufFlag = false;
            for (uint32_t index = 0; index < NUM_MPP_DST_BUFS(mM2mMPPs[i]->mLogicalType); index++) {
                HDEBUGLOGD(eDebugBuf, "%s allocate dst buffer[%d]%p, x: %d, y: %d",
                        __func__, index, mM2mMPPs[i]->mDstImgs[index].bufferHandle, Xres, Yres);
                uint32_t bufAlign = mM2mMPPs[i]->getOutBufAlign();
                ret = mM2mMPPs[i]->allocOutBuf(ALIGN_UP(Xres, bufAlign),
                        ALIGN_UP(Yres, bufAlign),
                        DEFAULT_MPP_DST_FORMAT, 0x0, index);
                if (ret < 0) {
                    HWC_LOGE(NULL, "%s:: fail to allocate dst buffer[%d]",
                            __func__, index);
                    return ret;
                }
                mM2mMPPs[i]->mPrevAssignedDisplayType = HWC_DISPLAY_PRIMARY;
            }
            mM2mMPPs[i]->setDstAllocSize(Xres, Yres);
        }
    }
    return ret;
}

int32_t ExynosResourceManager::checkScenario(ExynosDisplay __unused *display) {
    uint32_t prevResourceReserved = mResourceReserved;
    mResourceReserved = 0x0;
    /* Check whether camera preview is running */
    ExynosDisplay *exynosDisplay = NULL;
    for (uint32_t i = 0; i < mDevice->mDisplays.size(); i++) {
        exynosDisplay = mDevice->mDisplays[i];
        if ((exynosDisplay != NULL) && (exynosDisplay->mPlugState == true)) {
            for (uint32_t i = 0; i < exynosDisplay->mLayers.size(); i++) {
                ExynosLayer *layer = exynosDisplay->mLayers[i];
                VendorGraphicBufferMeta gmeta(layer->mLayerBuffer);
                if ((layer->mLayerBuffer != NULL) &&
                    (gmeta.producer_usage & BufferUsage::CAMERA_OUTPUT)) {
                    mResourceReserved |= (MPP_LOGICAL_G2D_YUV | MPP_LOGICAL_G2D_RGB);
                    break;
                }
            }
        }
    }

    if (prevResourceReserved != mResourceReserved) {
        mDevice->setGeometryChanged(GEOMETRY_DEVICE_SCENARIO_CHANGED);
    }

    return NO_ERROR;
}

/**
 * @param * display
 * @return int
 */
int32_t ExynosResourceManager::assignResource(ExynosDisplay *display)
{
    ATRACE_CALL();
    int ret = 0;
    if ((mDevice == NULL) || (display == NULL))
        return -EINVAL;

    HDEBUGLOGD(eDebugResourceManager|eDebugSkipResourceAssign, "mGeometryChanged(0x%" PRIx64 "), display(%d)",
            mDevice->mGeometryChanged, display->mType);

    if (mDevice->mGeometryChanged == 0) {
        return NO_ERROR;
    }

    for (uint32_t i = 0; i < display->mLayers.size(); i++) {
        display->mLayers[i]->resetValidateData();
    }

    display->initializeValidateInfos();

    if ((ret = preProcessLayer(display)) != NO_ERROR) {
        HWC_LOGE(display, "%s:: preProcessLayer() error (%d)",
                __func__, ret);
        return ret;
    }

    if ((ret = display->updateColorConversionInfo()) != NO_ERROR) {
        HWC_LOGE(display, "%s:: updateColorConversionInfo() fail, ret(%d)", __func__, ret);
        return ret;
    }
    display->checkPreblendingRequirement();

    HDEBUGLOGD(eDebugTDM, "%s layer's calculation start", __func__);
    for (uint32_t i = 0; i < display->mLayers.size(); i++) {
        calculateHWResourceAmount(display, display->mLayers[i]);
    }

    if (mDevice->isFirstValidate()) {
        HDEBUGLOGD(eDebugResourceManager, "This is first validate");
        if (exynosHWCControl.displayMode < DISPLAY_MODE_NUM)
            mDevice->mDisplayMode = exynosHWCControl.displayMode;

        if ((ret = prepareResources()) != NO_ERROR) {
            HWC_LOGE(display, "%s:: prepareResources() error (%d)",
                    __func__, ret);
            return ret;
        }
        preAssignWindows(display);

    }

    if ((ret = updateSupportedMPPFlag(display)) != NO_ERROR) {
        HWC_LOGE(display, "%s:: updateSupportedMPPFlag() error (%d)",
                __func__, ret);
        return ret;
    }

    if ((ret = assignResourceInternal(display)) != NO_ERROR) {
        HWC_LOGE(display, "%s:: assignResourceInternal() error (%d)",
                __func__, ret);
        return ret;
    }

    if ((ret = assignWindow(display)) != NO_ERROR) {
        HWC_LOGE(display, "%s:: assignWindow() error (%d)",
                __func__, ret);
        return ret;
    }

    if (hwcCheckDebugMessages(eDebugResourceManager)) {
        HDEBUGLOGD(eDebugResourceManager, "AssignResource result");
        String8 result;
        display->mClientCompositionInfo.dump(result);
        HDEBUGLOGD(eDebugResourceManager, "%s", result.c_str());
        result.clear();
        display->mExynosCompositionInfo.dump(result);
        HDEBUGLOGD(eDebugResourceManager, "%s", result.c_str());
        for (uint32_t i = 0; i < display->mLayers.size(); i++) {
            result.clear();
            HDEBUGLOGD(eDebugResourceManager, "%d layer(%p) dump", i, display->mLayers[i]);
            display->mLayers[i]->printLayer();
            HDEBUGLOGD(eDebugResourceManager, "%s", result.c_str());
        }
    }

    if (mDevice->isLastValidate(display)) {
        if ((ret = finishAssignResourceWork()) != NO_ERROR) {
            HWC_LOGE(display, "%s:: finishAssignResourceWork() error (%d)",
                    __func__, ret);
            return ret;
        }
    }

    if (!display->mUseDpu) {
        if (display->mClientCompositionInfo.mHasCompositionLayer) {
            if ((ret = display->mExynosCompositionInfo.mM2mMPP->assignMPP(display, &display->mClientCompositionInfo)) != NO_ERROR)
            {
                ALOGE("%s:: %s MPP assignMPP() error (%d)",
                        __func__, display->mExynosCompositionInfo.mM2mMPP->mName.c_str(), ret);
                return ret;
            }
            int prevHasCompositionLayer = display->mExynosCompositionInfo.mHasCompositionLayer;
            display->mExynosCompositionInfo.mHasCompositionLayer = true;
            // if prevHasCompositionLayer is false, setResourcePriority is not called
            if (prevHasCompositionLayer == false)
                setResourcePriority(display);
        }
    }

    return NO_ERROR;
}

int32_t ExynosResourceManager::setResourcePriority(ExynosDisplay *display)
{
    int ret = NO_ERROR;
    int check_ret = NO_ERROR;
    ExynosMPP *m2mMPP = NULL;

    for (uint32_t i = 0; i < display->mLayers.size(); i++) {
        ExynosLayer *layer = display->mLayers[i];
        if ((layer->mValidateCompositionType == HWC2_COMPOSITION_DEVICE) &&
            (layer->mM2mMPP != NULL) &&
            (layer->mM2mMPP->mPhysicalType == MPP_G2D) &&
            ((check_ret = layer->mM2mMPP->prioritize(2)) != NO_ERROR)) {
            if (check_ret < 0) {
                HWC_LOGE(display, "Fail to set exynoscomposition priority(%d)", ret);
            } else {
                m2mMPP = layer->mM2mMPP;
                layer->resetAssignedResource();
                layer->mOverlayInfo |= eResourcePendingWork;
                layer->mValidateCompositionType = HWC2_COMPOSITION_DEVICE;
                ret = EXYNOS_ERROR_CHANGED;
                HDEBUGLOGD(eDebugResourceManager, "\t%s is reserved without display because of panding work",
                        m2mMPP->mName.c_str());
                m2mMPP->reserveMPP();
                layer->mCheckMPPFlag[m2mMPP->mLogicalType] = eMPPHWBusy;
            }
        }
    }

    m2mMPP = display->mExynosCompositionInfo.mM2mMPP;
    ExynosCompositionInfo &compositionInfo = display->mExynosCompositionInfo;
    if (compositionInfo.mHasCompositionLayer == true)
    {
        if ((m2mMPP == NULL) || (m2mMPP->mAcrylicHandle == NULL)) {
            HWC_LOGE(display, "There is exynos composition layers but resource is null (%p)",
                    m2mMPP);
        } else if ((check_ret = m2mMPP->prioritize(2)) != NO_ERROR) {
            HDEBUGLOGD(eDebugResourceManager, "%s setting priority error(%d)", m2mMPP->mName.c_str(), check_ret);
            if (check_ret < 0) {
                HWC_LOGE(display, "Fail to set exynoscomposition priority(%d)", ret);
            } else {
                if (display->mExynosCompositionInfo.mFirstIndex >= 0) {
                    uint32_t firstIndex = (uint32_t)display->mExynosCompositionInfo.mFirstIndex;
                    uint32_t lastIndex = (uint32_t)display->mExynosCompositionInfo.mLastIndex;
                    for (uint32_t i = firstIndex; i <= lastIndex; i++) {
                        ExynosLayer *layer = display->mLayers[i];
                        layer->resetAssignedResource();
                        layer->mOverlayInfo |= eResourcePendingWork;
                        layer->mValidateCompositionType = HWC2_COMPOSITION_DEVICE;
                        layer->mCheckMPPFlag[m2mMPP->mLogicalType] = eMPPHWBusy;
                    }
                }
                compositionInfo.initializeInfos(display);
                ret = EXYNOS_ERROR_CHANGED;
                m2mMPP->resetUsedCapacity();
                HDEBUGLOGD(eDebugResourceManager, "\t%s is reserved without display because of pending work",
                        m2mMPP->mName.c_str());
                m2mMPP->reserveMPP();
            }
        } else {
            HDEBUGLOGD(eDebugResourceManager, "%s setting priority is ok", m2mMPP->mName.c_str());
        }
    }

    return ret;
}

int32_t ExynosResourceManager::assignResourceInternal(ExynosDisplay *display)
{
    int ret = NO_ERROR;
    int retry_count = 0;

    Mutex::Autolock lock(mDstBufMgrThread->mStateMutex);

    /*
     * First add layers that SF requested HWC2_COMPOSITION_CLIENT type
     * to client composition
     */
    for (uint32_t i = 0; i < display->mLayers.size(); i++) {
        ExynosLayer *layer = display->mLayers[i];
        if (layer->mCompositionType == HWC2_COMPOSITION_CLIENT) {
            layer->mOverlayInfo |= eSkipLayer;
            layer->mValidateCompositionType = HWC2_COMPOSITION_CLIENT;
            if (((ret = display->addClientCompositionLayer(i)) != NO_ERROR) &&
                 (ret != EXYNOS_ERROR_CHANGED)) {
                HWC_LOGE(display, "Handle HWC2_COMPOSITION_CLIENT type layers, but addClientCompositionLayer failed (%d)", ret);
                return ret;
            }
        }
    }

    do {
        HDEBUGLOGD(eDebugResourceAssigning, "%s:: retry_count(%d)", __func__, retry_count);
        if ((ret = resetAssignedResources(display)) != NO_ERROR)
            return ret;
        if ((ret = assignCompositionTarget(display, COMPOSITION_CLIENT)) != NO_ERROR) {
            HWC_LOGE(display, "%s:: Fail to assign resource for compositionTarget",
                    __func__);
            return ret;
        }

        if ((ret = assignLayers(display, ePriorityMax)) != NO_ERROR) {
            if (ret == EXYNOS_ERROR_CHANGED) {
                retry_count++;
                continue;
            } else {
                HWC_LOGE(display, "%s:: Fail to assign resource for ePriorityMax layer",
                        __func__);
                return ret;
            }
        }

        if ((ret = assignLayers(display, ePriorityHigh)) != NO_ERROR) {
            if (ret == EXYNOS_ERROR_CHANGED) {
                retry_count++;
                continue;
            } else {
                HWC_LOGE(display, "%s:: Fail to assign resource for ePriorityHigh layer",
                        __func__);
                return ret;
            }
        }

        if ((ret = assignCompositionTarget(display, COMPOSITION_EXYNOS)) != NO_ERROR) {
            if (ret == eInsufficientMPP) {
                /*
                 * Change compositionTypes to HWC2_COMPOSITION_CLIENT
                 */
                uint32_t firstIndex = (uint32_t)display->mExynosCompositionInfo.mFirstIndex;
                uint32_t lastIndex = (uint32_t)display->mExynosCompositionInfo.mLastIndex;
                for (uint32_t i = firstIndex; i <= lastIndex; i++) {
                    ExynosLayer *layer = display->mLayers[i];
                    layer->resetAssignedResource();
                    layer->mOverlayInfo |= eInsufficientMPP;
                    layer->mValidateCompositionType = HWC2_COMPOSITION_CLIENT;
                    if (((ret = display->addClientCompositionLayer(i)) != NO_ERROR) &&
                        (ret != EXYNOS_ERROR_CHANGED)) {
                        HWC_LOGE(display, "Change compositionTypes to HWC2_COMPOSITION_CLIENT, but addClientCompositionLayer failed (%d)", ret);
                        return ret;
                    }
                }
                display->mExynosCompositionInfo.initializeInfos(display);
                ret = EXYNOS_ERROR_CHANGED;
            } else {
                return ret;
            }
        }

        if (ret == NO_ERROR) {
            for (int32_t i = ePriorityHigh - 1; i > ePriorityNone; i--) {
                if ((ret = assignLayers(display, i)) == EXYNOS_ERROR_CHANGED)
                    break;
                if (ret != NO_ERROR)
                    return ret;
            }
        }

        /* Assignment is done */
        if (ret == NO_ERROR) {
            ret = setResourcePriority(display);
        }
        retry_count++;
    } while((ret == EXYNOS_ERROR_CHANGED) && (retry_count < ASSIGN_RESOURCE_TRY_COUNT));

    if (retry_count == ASSIGN_RESOURCE_TRY_COUNT) {
        HWC_LOGE(display, "%s:: assign resources fail", __func__);
        ret = eUnknown;
        return ret;
    } else {
        if ((ret = updateExynosComposition(display)) != NO_ERROR)
            return ret;
        if ((ret = updateClientComposition(display)) != NO_ERROR)
            return ret;
    }

    if (hwcCheckDebugMessages(eDebugCapacity)) {
        for (uint32_t i = 0; i < mM2mMPPs.size(); i++) {
            if (mM2mMPPs[i]->mPhysicalType == MPP_G2D)
            {
                String8 dumpMPP;
                mM2mMPPs[i]->dump(dumpMPP);
                HDEBUGLOGD(eDebugCapacity, "%s", dumpMPP.c_str());
            }
        }
    }
    return ret;
}

int32_t ExynosResourceManager::updateExynosComposition(ExynosDisplay *display)
{
    int ret = NO_ERROR;
    /* Use Exynos composition as many as possible */
    if ((display->mExynosCompositionInfo.mHasCompositionLayer == true) &&
        (display->mExynosCompositionInfo.mM2mMPP != NULL)) {
        if (display->mDisplayControl.useMaxG2DSrc == 1) {
            ExynosMPP *m2mMPP = display->mExynosCompositionInfo.mM2mMPP;
            uint32_t lastIndex = display->mExynosCompositionInfo.mLastIndex;
            uint32_t firstIndex = display->mExynosCompositionInfo.mFirstIndex;
            uint32_t remainNum = m2mMPP->mMaxSrcLayerNum - (lastIndex - firstIndex + 1);

            HDEBUGLOGD(eDebugResourceAssigning,
                       "Update ExynosComposition firstIndex: %d, lastIndex: %d, remainNum: %d++++",
                       firstIndex, lastIndex, remainNum);

            ExynosLayer *layer = NULL;
            exynos_image src_img;
            exynos_image dst_img;
            if (remainNum > 0) {
                for (uint32_t i = (lastIndex + 1); i < display->mLayers.size(); i++)
                {
                    layer = display->mLayers[i];
                    layer->setSrcExynosImage(&src_img);
                    layer->setDstExynosImage(&dst_img);
                    layer->setExynosImage(src_img, dst_img);
                    bool isAssignableState = false;
                    if ((layer->mSupportedMPPFlag & m2mMPP->mLogicalType) != 0)
                        isAssignableState = isAssignable(m2mMPP, display, src_img, dst_img, layer);

                    bool canChange = (layer->mValidateCompositionType != HWC2_COMPOSITION_CLIENT) &&
                            ((display->mDisplayControl.cursorSupport == false) ||
                             (layer->mCompositionType != HWC2_COMPOSITION_CURSOR)) &&
                            (layer->mSupportedMPPFlag & m2mMPP->mLogicalType) && isAssignableState;

                    HDEBUGLOGD(eDebugResourceAssigning,
                               "\tlayer[%d] type: %d, 0x%8x, isAssignable: %d, canChange: %d, "
                               "remainNum(%d)",
                               i, layer->mValidateCompositionType, layer->mSupportedMPPFlag,
                               isAssignableState, canChange, remainNum);
                    if (canChange) {
                        layer->resetAssignedResource();
                        layer->mOverlayInfo |= eUpdateExynosComposition;
                        if ((ret = m2mMPP->assignMPP(display, layer)) != NO_ERROR)
                        {
                            ALOGE("%s:: %s MPP assignMPP() error (%d)",
                                    __func__, m2mMPP->mName.c_str(), ret);
                            return ret;
                        }
                        layer->setExynosMidImage(dst_img);
                        float totalUsedCapacity = getResourceUsedCapa(*m2mMPP);
                        display->addExynosCompositionLayer(i, totalUsedCapacity);
                        layer->mValidateCompositionType = HWC2_COMPOSITION_EXYNOS;
                        remainNum--;
                    }
                    if ((canChange == false) || (remainNum == 0))
                        break;
                }
            }
            if (remainNum > 0) {
                for (int32_t i = (firstIndex - 1); i >= 0; i--)
                {
                    layer = display->mLayers[i];
                    layer->setSrcExynosImage(&src_img);
                    layer->setDstExynosImage(&dst_img);
                    layer->setExynosImage(src_img, dst_img);
                    bool isAssignableState = false;
                    if ((layer->mSupportedMPPFlag & m2mMPP->mLogicalType) != 0)
                        isAssignableState = isAssignable(m2mMPP, display, src_img, dst_img, layer);

                    bool canChange = (layer->mValidateCompositionType != HWC2_COMPOSITION_CLIENT) &&
                            ((display->mDisplayControl.cursorSupport == false) ||
                             (layer->mCompositionType != HWC2_COMPOSITION_CURSOR)) &&
                            (layer->mSupportedMPPFlag & m2mMPP->mLogicalType) && isAssignableState;

                    HDEBUGLOGD(eDebugResourceAssigning,
                               "\tlayer[%d] type: %d, 0x%8x, isAssignable: %d, canChange: %d, "
                               "remainNum(%d)",
                               i, layer->mValidateCompositionType, layer->mSupportedMPPFlag,
                               isAssignableState, canChange, remainNum);
                    if (canChange) {
                        layer->resetAssignedResource();
                        layer->mOverlayInfo |= eUpdateExynosComposition;
                        if ((ret = m2mMPP->assignMPP(display, layer)) != NO_ERROR)
                        {
                            ALOGE("%s:: %s MPP assignMPP() error (%d)",
                                    __func__, m2mMPP->mName.c_str(), ret);
                            return ret;
                        }
                        layer->setExynosMidImage(dst_img);
                        float totalUsedCapacity = getResourceUsedCapa(*m2mMPP);
                        display->addExynosCompositionLayer(i, totalUsedCapacity);
                        layer->mValidateCompositionType = HWC2_COMPOSITION_EXYNOS;
                        remainNum--;
                    }
                    if ((canChange == false) || (remainNum == 0))
                        break;
                }
            }
            HDEBUGLOGD(eDebugResourceAssigning,
                       "Update ExynosComposition firstIndex: %d, lastIndex: %d, remainNum: %d-----",
                       display->mExynosCompositionInfo.mFirstIndex,
                       display->mExynosCompositionInfo.mLastIndex, remainNum);
        }

        /*
         * Check if there is only one exynos composition layer
         * Then it is not composition and m2mMPP is not required
         * if internalMPP can process the layer alone.
         */
        ExynosMPP *otfMPP = display->mExynosCompositionInfo.mOtfMPP;
        if ((display->mDisplayControl.enableExynosCompositionOptimization == true) &&
            (otfMPP != NULL) &&
            (display->mExynosCompositionInfo.mFirstIndex >= 0) &&
            (display->mExynosCompositionInfo.mFirstIndex == display->mExynosCompositionInfo.mLastIndex))
        {
            ExynosLayer* layer = display->mLayers[display->mExynosCompositionInfo.mFirstIndex];
            if (layer->mSupportedMPPFlag & otfMPP->mLogicalType) {
                layer->resetAssignedResource();
                layer->mValidateCompositionType = HWC2_COMPOSITION_DEVICE;
                display->mExynosCompositionInfo.initializeInfos(display);
                // reset otfMPP
                if ((ret = otfMPP->resetAssignedState()) != NO_ERROR)
                {
                    ALOGE("%s:: %s MPP resetAssignedState() error (%d)",
                            __func__, otfMPP->mName.c_str(), ret);
                }
                // assign otfMPP again
                if ((ret = otfMPP->assignMPP(display, layer)) != NO_ERROR)
                {
                    ALOGE("%s:: %s MPP assignMPP() error (%d)",
                            __func__, otfMPP->mName.c_str(), ret);
                }
            }
        }
    }
    return ret;
}

int32_t ExynosResourceManager::changeLayerFromClientToDevice(ExynosDisplay* display,
                                                             ExynosLayer* layer,
                                                             uint32_t layer_index,
                                                             const exynos_image& m2m_out_img,
                                                             ExynosMPP* m2mMPP, ExynosMPP* otfMPP) {
    int ret = NO_ERROR;
    if ((ret = display->removeClientCompositionLayer(layer_index)) != NO_ERROR) {
        ALOGD("removeClientCompositionLayer return error(%d)", ret);
        return ret;
    }
    if (otfMPP != NULL) {
        if ((ret = otfMPP->assignMPP(display, layer)) != NO_ERROR)
        {
            ALOGE("%s:: %s MPP assignMPP() error (%d)",
                    __func__, otfMPP->mName.c_str(), ret);
            return ret;
        }
        HDEBUGLOGD(eDebugResourceAssigning, "\t\t[%d] layer: %s MPP is assigned", layer_index,
                   otfMPP->mName.c_str());
    }
    if (m2mMPP != NULL) {
        if ((ret = m2mMPP->assignMPP(display, layer)) != NO_ERROR)
        {
            ALOGE("%s:: %s MPP assignMPP() error (%d)",
                    __func__, m2mMPP->mName.c_str(), ret);
            return ret;
        }
        layer->setExynosMidImage(m2m_out_img);
        HDEBUGLOGD(eDebugResourceAssigning, "\t\t[%d] layer: %s MPP is assigned", layer_index,
                   m2mMPP->mName.c_str());
    }
    layer->mValidateCompositionType = HWC2_COMPOSITION_DEVICE;
    display->mWindowNumUsed++;
    HDEBUGLOGD(eDebugResourceAssigning, "\t\t[%d] layer: mWindowNumUsed(%d)", layer_index,
               display->mWindowNumUsed);

    return ret;
}
int32_t ExynosResourceManager::updateClientComposition(ExynosDisplay *display)
{
    int ret = NO_ERROR;

    if (display->mDisplayControl.enableClientCompositionOptimization == false)
        return ret;

    if ((exynosHWCControl.forceGpu == 1) ||
        (display->mClientCompositionInfo.mHasCompositionLayer == false))
        return ret;

    /* Check if there is layer that can be handled by overlay */
    int32_t firstIndex = display->mClientCompositionInfo.mFirstIndex;
    int32_t lastIndex = display->mClientCompositionInfo.mLastIndex;

    /* Don't optimize if only low fps layers are composited by GLES */
    if ((display->mLowFpsLayerInfo.mHasLowFpsLayer == true) &&
        (display->mLowFpsLayerInfo.mFirstIndex == firstIndex) &&
        (display->mLowFpsLayerInfo.mLastIndex == lastIndex))
        return ret;

    for (int32_t i = firstIndex; i <= lastIndex; i++) {
        ExynosMPP *m2mMPP = NULL;
        ExynosMPP *otfMPP = NULL;
        exynos_image m2m_out_img;
        uint32_t overlayInfo = 0;
        int32_t compositionType = 0;
        ExynosLayer *layer = display->mLayers[i];
        if ((layer->mOverlayPriority >= ePriorityHigh) &&
            (layer->mValidateCompositionType == HWC2_COMPOSITION_DEVICE)) {
            display->mClientCompositionInfo.mFirstIndex++;
            continue;
        }
        compositionType = assignLayer(display, layer, i, m2m_out_img, &m2mMPP, &otfMPP, overlayInfo);
        if (compositionType == HWC2_COMPOSITION_DEVICE) {
            /*
             * Don't allocate G2D
             * Execute can be fail because of other job
             * Prioritizing is required to allocate G2D
             */
            if ((m2mMPP != NULL) && (m2mMPP->mPhysicalType == MPP_G2D))
                break;

            if ((ret = changeLayerFromClientToDevice(display, layer, i, m2m_out_img, m2mMPP, otfMPP)) != NO_ERROR)
                return ret;
        } else {
            break;
        }
    }

    firstIndex = display->mClientCompositionInfo.mFirstIndex;
    lastIndex = display->mClientCompositionInfo.mLastIndex;
    for (int32_t i = lastIndex; i >= 0; i--) {
        ExynosMPP *m2mMPP = NULL;
        ExynosMPP *otfMPP = NULL;
        exynos_image m2m_out_img;
        uint32_t overlayInfo = 0;
        int32_t compositionType = 0;
        ExynosLayer *layer = display->mLayers[i];
        if ((layer->mOverlayPriority >= ePriorityHigh) &&
            (layer->mValidateCompositionType == HWC2_COMPOSITION_DEVICE)) {
            display->mClientCompositionInfo.mLastIndex--;
            continue;
        }
        compositionType = assignLayer(display, layer, i, m2m_out_img, &m2mMPP, &otfMPP, overlayInfo);
        if (compositionType == HWC2_COMPOSITION_DEVICE) {
            /*
             * Don't allocate G2D
             * Execute can be fail because of other job
             * Prioritizing is required to allocate G2D
             */
            if ((m2mMPP != NULL) && (m2mMPP->mPhysicalType == MPP_G2D))
                break;
            if ((ret = changeLayerFromClientToDevice(display, layer, i, m2m_out_img, m2mMPP, otfMPP)) != NO_ERROR)
                return ret;
        } else {
            break;
        }
    }

    return ret;
}

int32_t ExynosResourceManager::resetAssignedResources(ExynosDisplay * display, bool forceReset)
{
    for (uint32_t i = 0; i < mOtfMPPs.size(); i++) {
        if (mOtfMPPs[i]->mAssignedDisplay != display)
            continue;

        mOtfMPPs[i]->resetAssignedState();
    }
    for (uint32_t i = 0; i < mM2mMPPs.size(); i++) {
        if (mM2mMPPs[i]->mAssignedDisplay != display)
            continue;
        if ((forceReset == false) &&
            ((mM2mMPPs[i]->mLogicalType == MPP_LOGICAL_G2D_RGB) ||
             (mM2mMPPs[i]->mLogicalType == MPP_LOGICAL_G2D_COMBO)))
        {
            /*
             * Don't reset assigned state
             */
            continue;
        }
        mM2mMPPs[i]->resetAssignedState();
    }
    display->mWindowNumUsed = 0;

    return NO_ERROR;
}

int32_t ExynosResourceManager::assignCompositionTarget(ExynosDisplay * display, uint32_t targetType)
{
    int32_t ret = NO_ERROR;
    ExynosCompositionInfo *compositionInfo;

    HDEBUGLOGD(eDebugResourceManager, "%s:: display(%d), targetType(%d) +++++",
            __func__, display->mType, targetType);

    if (targetType == COMPOSITION_CLIENT)
        compositionInfo = &(display->mClientCompositionInfo);
    else if (targetType == COMPOSITION_EXYNOS)
        compositionInfo = &(display->mExynosCompositionInfo);
    else
        return -EINVAL;

    if (compositionInfo->mHasCompositionLayer == false)
    {
        HDEBUGLOGD(eDebugResourceManager, "\tthere is no composition layers");
        return NO_ERROR;
    }

    exynos_image src_img;
    exynos_image dst_img;
    display->setCompositionTargetExynosImage(targetType, &src_img, &dst_img);

    if (targetType == COMPOSITION_EXYNOS) {
        for (uint32_t i = 0; i < mM2mMPPs.size(); i++) {
            if ((display->mUseDpu == true) &&
                (mM2mMPPs[i]->mLogicalType != MPP_LOGICAL_G2D_RGB))
                continue;
            if ((display->mUseDpu == false) &&
                (mM2mMPPs[i]->mLogicalType != MPP_LOGICAL_G2D_COMBO))
                continue;
            if (mM2mMPPs[i]->isAssignableState(display, src_img, dst_img)) {
                /* assignMPP(display, compositionInfo) is not called hear
                 * assignMPP() was called already during assigning layer
                 * Source of M2mMPP should be Layer, not composition target buffer*/
                compositionInfo->mM2mMPP = mM2mMPPs[i];
            }
        }
        if (compositionInfo->mM2mMPP == NULL) {
            HWC_LOGE(display, "%s:: fail to assign M2mMPP (%d)",__func__, ret);
            return eInsufficientMPP;
        }
    }

    if ((compositionInfo->mFirstIndex < 0) ||
        (compositionInfo->mLastIndex < 0)) {
        HWC_LOGE(display, "%s:: layer index is not valid mFirstIndex(%d), mLastIndex(%d)",
                __func__, compositionInfo->mFirstIndex, compositionInfo->mLastIndex);
        return -EINVAL;
    }

    if (display->mUseDpu == false) {
        return NO_ERROR;
    }

    int64_t isSupported = 0;
    bool isAssignableState = false;

    otfMppReordering(display, mOtfMPPs, src_img, dst_img);

    for (uint32_t i = 0; i < mOtfMPPs.size(); i++) {
        compositionInfo->setExynosImage(src_img, dst_img);
        compositionInfo->setExynosMidImage(dst_img);
        HDEBUGLOGD(eDebugTDM, "%s M2M target calculation start", __func__);
        calculateHWResourceAmount(display, compositionInfo);

        isSupported = mOtfMPPs[i]->isSupported(*display, src_img, dst_img);
        if (isSupported == NO_ERROR)
            isAssignableState =
                    isAssignable(mOtfMPPs[i], display, src_img, dst_img, compositionInfo);

        HDEBUGLOGD(eDebugResourceAssigning,
                   "\t\t check %s: supportedBit(0x%" PRIx64 "), isAssignable(%d)",
                   mOtfMPPs[i]->mName.c_str(), -isSupported, isAssignableState);
        if ((isSupported == NO_ERROR) && (isAssignableState)) {
            if ((ret = mOtfMPPs[i]->assignMPP(display, compositionInfo)) != NO_ERROR)
            {
                HWC_LOGE(display, "%s:: %s MPP assignMPP() error (%d)",
                        __func__, mOtfMPPs[i]->mName.c_str(), ret);
                return ret;
            }
            compositionInfo->mOtfMPP = mOtfMPPs[i];
            display->mWindowNumUsed++;

            HDEBUGLOGD(eDebugResourceManager, "%s:: %s is assigned", __func__, mOtfMPPs[i]->mName.c_str());
            return NO_ERROR;
        }
    }

    HDEBUGLOGD(eDebugResourceManager, "%s:: insufficient MPP", __func__);
    return eInsufficientMPP;
}

int32_t ExynosResourceManager::validateLayer(uint32_t index, ExynosDisplay *display, ExynosLayer *layer)
{
    if ((layer == NULL) || (display == NULL))
        return eUnknown;

    if (exynosHWCControl.forceGpu == 1) {
        if ((layer->mLayerBuffer == NULL) ||
            (getDrmMode(layer->mLayerBuffer) == NO_DRM))
            return eForceFbEnabled;
    }

    if ((display->mLayers.size() >= MAX_OVERLAY_LAYER_NUM) &&
        (layer->mOverlayPriority < ePriorityHigh))
        return eExceedMaxLayerNum;

    if ((layer->mLayerBuffer != NULL) &&
        (getDrmMode(layer->mLayerBuffer) == NO_DRM) &&
        (display->mDREnable == true) &&
        (display->mDynamicReCompMode == DEVICE_2_CLIENT))
        return eDynamicRecomposition;

    if ((layer->mLayerBuffer != NULL) &&
            (display->mType == HWC_DISPLAY_PRIMARY) &&
            (mForceReallocState != DST_REALLOC_DONE)) {
        ALOGI("Device type assign skipping by dst reallocation...... ");
        return eReallocOnGoingForDDI;
    }

    if (layer->mCompositionType == HWC2_COMPOSITION_CLIENT)
        return eSkipLayer;

#ifndef HWC_SUPPORT_COLOR_TRANSFORM
    if (display->mColorTransformHint != HAL_COLOR_TRANSFORM_IDENTITY) {
        HWC_LOGE(display, "unsupported color transform");
        return eUnSupportedColorTransform;
    }
#else
    if ((display->mColorTransformHint < 0) &&
        (layer->mOverlayPriority < ePriorityHigh))
        return eUnSupportedColorTransform;
#endif

    if ((display->mLowFpsLayerInfo.mHasLowFpsLayer == true) &&
        (display->mLowFpsLayerInfo.mFirstIndex <= (int32_t)index) &&
        ((int32_t)index <= display->mLowFpsLayerInfo.mLastIndex))
        return eLowFpsLayer;

    if(layer->isDimLayer() && layer->mLayerBuffer == NULL) {
        return eDimLayer;
    }

    if (!(display->mType == HWC_DISPLAY_VIRTUAL &&
        ((ExynosVirtualDisplay *)display)->mIsWFDState == (int)LLWFD))

    if (layer->mLayerBuffer == NULL)
        return eInvalidHandle;
    if (isSrcCropFloat(layer->mPreprocessedInfo.sourceCrop))
        return eHasFloatSrcCrop;

    if ((layer->mPreprocessedInfo.displayFrame.left < 0) ||
        (layer->mPreprocessedInfo.displayFrame.top < 0) ||
        (layer->mPreprocessedInfo.displayFrame.right > (int32_t)display->mXres) ||
        (layer->mPreprocessedInfo.displayFrame.bottom > (int32_t)display->mYres))
        return eInvalidDispFrame;

    if (layer->mPreprocessedInfo.sdrDimRatio < mMinimumSdrDimRatio)
        return eExceedSdrDimRatio;

    return NO_ERROR;
}

int32_t ExynosResourceManager::validateRCDLayer(const ExynosDisplay &display,
                                                const ExynosLayer &layer, const uint32_t layerIndex,
                                                const exynos_image &srcImg,
                                                const exynos_image &dstImg) {
    if (CC_UNLIKELY(srcImg.bufferHandle == NULL || srcImg.format != HAL_PIXEL_FORMAT_GOOGLE_R_8)) {
        return eInvalidHandle;
    }

    if (bool supported;
        CC_UNLIKELY(display.getRCDLayerSupport(supported) != NO_ERROR || !supported))
        return eUnSupportedUseCase;

    // no rotation
    if (srcImg.transform & (HAL_TRANSFORM_ROT_90 | HAL_TRANSFORM_ROT_180)) {
        return eMPPUnsupported;
    }

    // no scale
    if (srcImg.w != dstImg.w || srcImg.h != dstImg.h) {
        return eMPPUnsupported;
    }

    // only support RCD Layers on the top
    if (layerIndex != display.mLayers.size() - 1) {
        return eMPPUnsupported;
    }

    // b/215335109: IMG_SIZE must be equal or larger than display output
    if (dstImg.x != 0 || dstImg.y != 0 || dstImg.w != display.mXres || dstImg.h != display.mYres) {
        return eInvalidDispFrame;
    }

    return NO_ERROR;
}

exynos_image ExynosResourceManager::getAlignedImage(exynos_image image, const ExynosMPP *m2mMpp,
                                                    const ExynosMPP *otfMpp) const {
    const auto srcCropWidthAlign = otfMpp ? otfMpp->getSrcCropWidthAlign(image) : 1;
    const auto srcCropHeightAlign = otfMpp ? otfMpp->getSrcCropHeightAlign(image) : 1;
    const auto dstwidthAlign = m2mMpp ? m2mMpp->getDstWidthAlign(image) : 1;
    const auto dstHeightAlign = m2mMpp ? m2mMpp->getDstHeightAlign(image) : 1;

    const auto widthAlign = std::lcm(srcCropWidthAlign, dstwidthAlign);
    const auto heighAlign = std::lcm(srcCropHeightAlign, dstHeightAlign);

    image.w = pixel_align(image.w, widthAlign);
    image.h = pixel_align(image.h, heighAlign);

    return image;
}

void ExynosResourceManager::getCandidateScalingM2mMPPOutImages(
        const ExynosDisplay *display, const exynos_image &src_img, const exynos_image &dst_img,
        std::vector<exynos_image> &image_lists) {
    const bool isPerpendicular = !!(src_img.transform & HAL_TRANSFORM_ROT_90);
    const uint32_t srcWidth = isPerpendicular ? src_img.h : src_img.w;
    const uint32_t srcHeight = isPerpendicular ? src_img.w : src_img.h;

    const bool scaleUp = (srcWidth < dst_img.w && srcHeight < dst_img.h);
    const bool scaleDown = (srcWidth > dst_img.w && srcHeight > dst_img.h);

    if (!scaleUp && !scaleDown) {
        return;
    }

    /* otfMPP doesn't rotate image, m2mMPP rotates image */
    exynos_image dst_scale_img = dst_img;

    if (hasHdrInfo(src_img)) {
        if (isFormatYUV(src_img.format))
            dst_scale_img.format = HAL_PIXEL_FORMAT_YCBCR_P010;
        else
            dst_scale_img.format = HAL_PIXEL_FORMAT_RGBA_1010102;
    } else {
        if (isFormatYUV(src_img.format)) {
            dst_scale_img.format = DEFAULT_MPP_DST_YUV_FORMAT;
        }
    }

    ExynosMPP *otfMpp = nullptr;
    ExynosMPP *m2mMpp = nullptr;
    uint32_t otfMppRatio = 1;
    uint32_t m2mMppRatio = 1;
    if (scaleUp) {
        for (ExynosMPP *m : mOtfMPPs) {
            auto ratio = m->getMaxUpscale(dst_scale_img, dst_img);
            if (ratio > 1) {
                otfMpp = m;
                otfMppRatio = ratio;
                break;
            }
        }
        const auto reqRatio = max(float(dst_img.w) / float(srcWidth * otfMppRatio),
                                  float(dst_img.h) / float(srcHeight * otfMppRatio));
        for (ExynosMPP *m : mM2mMPPs) {
            float ratio = float(m->getMaxUpscale(src_img, dst_scale_img));
            if (ratio > reqRatio) {
                m2mMpp = m;
                m2mMppRatio = ratio;
                break;
            }
        }
    } else {
        for (ExynosMPP *m : mM2mMPPs) {
            auto ratio = m->getMaxDownscale(*display, src_img, dst_scale_img);
            if (ratio > 1) {
                m2mMpp = m;
                m2mMppRatio = ratio;
                break;
            }
        }
        const float otfSrcWidth = float(srcWidth / m2mMppRatio);
        const float scaleRatio_H = otfSrcWidth / float(dst_img.w);
        const float otfSrcHeight = float(srcHeight / m2mMppRatio);
        const float scaleRatio_V = otfSrcHeight / float(dst_img.h);
        const float displayRatio_V = float(dst_img.h) / float(display->mYres);
        const float resolution = otfSrcWidth * otfSrcHeight * display->getBtsRefreshRate() / 1000;

        for (ExynosMPP *m : mOtfMPPs) {
            auto ratio = m->getDownscaleRestriction(dst_scale_img, dst_img);

            if (ratio >= scaleRatio_H && ratio >= scaleRatio_V &&
                m->checkDownscaleCap(resolution, displayRatio_V)) {
                otfMpp = m;
                otfMppRatio = ratio;
                break;
            }
        }
    }

    if (!otfMpp && !m2mMpp) {
        HDEBUGLOGD(eDebugResourceManager,
                   "Cannot find available MPP for scaling src %d x %d, dst %d x %d", src_img.w,
                   src_img.h, dst_img.w, dst_img.h);
        return;
    }

    dst_scale_img.x = 0;
    dst_scale_img.y = 0;
    dst_scale_img.w = scaleDown ? dst_img.w : srcWidth;
    dst_scale_img.h = scaleDown ? dst_img.h : srcHeight;

    HDEBUGLOGD(eDebugResourceManager,
               "scaling w: %d, h: %d, ratio = otfType %d - %d, m2mType %d - %d", dst_scale_img.w,
               dst_scale_img.h, otfMpp ? otfMpp->mLogicalType : -1, otfMppRatio,
               m2mMpp ? m2mMpp->mLogicalType : -1, m2mMppRatio);
    if (scaleUp) {
        if (dst_scale_img.w * otfMppRatio < dst_img.w) {
            dst_scale_img.w = uint32_t(ceilf(float(dst_img.w) / float(otfMppRatio)));
        }
        if (dst_scale_img.h * otfMppRatio < dst_img.h) {
            dst_scale_img.h = uint32_t(ceilf(float(dst_img.h) / float(otfMppRatio)));
        }
    } else {
        if (dst_scale_img.w * m2mMppRatio < srcWidth) {
            dst_scale_img.w = uint32_t(ceilf(float(srcWidth) / float(m2mMppRatio)));
        }
        if (dst_scale_img.h * m2mMppRatio < srcHeight) {
            dst_scale_img.h = uint32_t(ceilf(float(srcHeight) / float(m2mMppRatio)));
        }
    }
    HDEBUGLOGD(eDebugResourceAssigning,
               "\tsrc[%d, %d, %d,%d], dst[%d, %d, %d,%d], mid[%d, %d, %d, %d]", src_img.x,
               src_img.y, src_img.w, src_img.h, dst_img.x, dst_img.y, dst_img.w, dst_img.h,
               dst_scale_img.x, dst_scale_img.y, dst_scale_img.w, dst_scale_img.h);

    if (isFormatSBWC(dst_scale_img.format)) {
        image_lists.emplace_back(getAlignedImage(dst_scale_img, m2mMpp, otfMpp));
        /*
         * SBWC format could not be supported in specific dst size
         * Add uncompressed YUV format to cover this size
         */
        dst_scale_img.format = DEFAULT_MPP_DST_UNCOMP_YUV_FORMAT;
    }

    image_lists.emplace_back(getAlignedImage(dst_scale_img, m2mMpp, otfMpp));
}

int32_t ExynosResourceManager::getCandidateM2mMPPOutImages(ExynosDisplay *display,
        ExynosLayer *layer, std::vector<exynos_image> &image_lists)
{
    exynos_image src_img;
    exynos_image dst_img;
    layer->setSrcExynosImage(&src_img);
    layer->setDstExynosImage(&dst_img);
    /* Position is (0, 0) */
    dst_img.x = 0;
    dst_img.y = 0;

    /* Check original source format first */
    dst_img.format = src_img.format;
    dst_img.dataSpace = src_img.dataSpace;

    /* Copy origin source HDR metadata */
    dst_img.metaParcel = src_img.metaParcel;

    getCandidateScalingM2mMPPOutImages(display, src_img, dst_img, image_lists);

    if (isFormatYUV(src_img.format) && !hasHdrInfo(src_img)) {
        dst_img.format = DEFAULT_MPP_DST_YUV_FORMAT;
    }

    ExynosExternalDisplay *external_display =
        (ExynosExternalDisplay*)mDevice->getDisplay(getDisplayId(HWC_DISPLAY_EXTERNAL, 0));

    /* For HDR through MSC or G2D case but dataspace is not changed */
    if (hasHdrInfo(src_img)) {
        if (isFormatYUV(src_img.format))
            dst_img.format = HAL_PIXEL_FORMAT_YCBCR_P010;
        else
            dst_img.format = HAL_PIXEL_FORMAT_RGBA_1010102;
        dst_img.dataSpace = src_img.dataSpace;

        /*
         * Align dst size
         * HDR10Plus should able to be processed by VGRFS
         * HDR on primary display should be processed by VGRFS
         * when external display is connected
         * because G2D is used by external display
         */
        if (hasHdr10Plus(dst_img) ||
            ((external_display != NULL) && (external_display->mPlugState) &&
             (display->mType == HWC_DISPLAY_PRIMARY))) {
            ExynosMPP *otfMppForHDRPlus = nullptr;
            auto mpp_it = std::find_if(mOtfMPPs.begin(), mOtfMPPs.end(),
                    [](auto m) {
                    return (m->mAttr & MPP_ATTR_HDR10PLUS);
                    });
            otfMppForHDRPlus = mpp_it == mOtfMPPs.end() ? nullptr : *mpp_it;
            uint32_t srcCropWidthAlign = 1;
            uint32_t srcCropHeightAlign = 1;
            if (otfMppForHDRPlus) {
                srcCropWidthAlign = otfMppForHDRPlus->getSrcCropWidthAlign(dst_img);
                srcCropHeightAlign = otfMppForHDRPlus->getSrcCropHeightAlign(dst_img);
            }
            dst_img.w = pixel_align(dst_img.w, srcCropWidthAlign);
            dst_img.h = pixel_align(dst_img.h, srcCropHeightAlign);
        }
    }

    image_lists.push_back(dst_img);
    if (isFormatSBWC(dst_img.format)) {
        /*
         * SBWC format could not be supported in specific dst size
         * Add uncompressed YUV format to cover this size
         */
        dst_img.format = DEFAULT_MPP_DST_UNCOMP_YUV_FORMAT;
        image_lists.push_back(dst_img);
    }

    /* For G2D HDR case */
    if (hasHdrInfo(src_img)) {
        bool isExternalPlugged = false;
        isHdrExternal = false;

        if (external_display != NULL) {
            if (external_display->mPlugState) isExternalPlugged = true;
            if (isExternalPlugged && (external_display->mExternalHdrSupported == true))
                isHdrExternal = true;
        }

        if (isHdrExternal && (display->mType == HWC_DISPLAY_EXTERNAL)) {
            dst_img.format = HAL_PIXEL_FORMAT_RGBA_1010102;
            dst_img.dataSpace = src_img.dataSpace;
        } else {
            uint32_t dataspace = HAL_DATASPACE_UNKNOWN;
            if (display->mColorMode == HAL_COLOR_MODE_NATIVE) {
                dataspace = HAL_DATASPACE_DCI_P3;
                dataspace &= ~HAL_DATASPACE_TRANSFER_MASK;
                dataspace |= HAL_DATASPACE_TRANSFER_GAMMA2_2;
                dataspace &= ~HAL_DATASPACE_RANGE_MASK;
                dataspace |= HAL_DATASPACE_RANGE_LIMITED;
            } else {
                dataspace = colorModeToDataspace(display->mColorMode);
            }
            dst_img.format = HAL_PIXEL_FORMAT_RGBX_8888;
            dst_img.dataSpace = (android_dataspace)dataspace;
        }

        /*
         * This image is not pushed for primary display
         * if external display is connected
         * because G2D is used only for HDR on exernal display
         */
        if (!(isExternalPlugged && (display->mType == HWC_DISPLAY_PRIMARY))) {
            image_lists.push_back(dst_img);
        }
    }

    if (isFormatYUV(src_img.format) && !hasHdrInfo(src_img)) {
        /* Check RGB format */
        dst_img.format = DEFAULT_MPP_DST_FORMAT;
        if (display->mColorMode == HAL_COLOR_MODE_NATIVE) {
            /* Bypass dataSpace */
            dst_img.dataSpace = src_img.dataSpace;
        } else {
            /* Covert data space */
            dst_img.dataSpace = colorModeToDataspace(display->mColorMode);
        }
        image_lists.push_back(dst_img);
    }

    /*
     * image_lists[] would be src of otfMPP.
     * Layer color transform should be addressed
     * with dataspace conversion.
     * It should be addressed by m2mMPP if m2mMPP converts dataspace.
     * In other cases, m2mMPP ignores color transform setting and
     * otfMPP addresses layer color transform if it is necessary.
     */
    for (auto &image: image_lists) {
        if (image.dataSpace == src_img.dataSpace)
            image.needColorTransform = src_img.needColorTransform;
        else
            image.needColorTransform = false;

    }

    return static_cast<int32_t>(image_lists.size());
}

int32_t ExynosResourceManager::assignLayer(ExynosDisplay *display, ExynosLayer *layer, uint32_t layer_index,
        exynos_image &m2m_out_img, ExynosMPP **m2mMPP, ExynosMPP **otfMPP, uint32_t &overlayInfo)
{
    int32_t ret = NO_ERROR;
    uint32_t validateFlag = 0;

    exynos_image src_img;
    exynos_image dst_img;
    layer->setSrcExynosImage(&src_img);
    layer->setDstExynosImage(&dst_img);
    layer->setExynosImage(src_img, dst_img);
    layer->setExynosMidImage(dst_img);

    validateFlag = validateLayer(layer_index, display, layer);
    if ((display->mUseDpu) &&
        (display->mWindowNumUsed >= display->mMaxWindowNum))
        validateFlag |= eInsufficientWindow;

    HDEBUGLOGD(eDebugResourceManager, "\t[%d] layer: validateFlag(0x%8x), supportedMPPFlag(0x%8x)",
            layer_index, validateFlag, layer->mSupportedMPPFlag);

    if (hwcCheckDebugMessages(eDebugResourceAssigning)) {
        layer->printLayer();
    }

    if ((validateFlag == NO_ERROR) || (validateFlag == eInsufficientWindow) ||
        (validateFlag == eDimLayer)) {
        bool isAssignableFlag = false;
        uint64_t isSupported = 0;
        /* 1. Find available otfMPP */
        if (validateFlag != eInsufficientWindow) {
            otfMppReordering(display, mOtfMPPs, src_img, dst_img);

            for (uint32_t j = 0; j < mOtfMPPs.size(); j++) {
                if ((layer->mSupportedMPPFlag & mOtfMPPs[j]->mLogicalType) != 0)
                    isAssignableFlag = isAssignable(mOtfMPPs[j], display, src_img, dst_img, layer);

                HDEBUGLOGD(eDebugResourceAssigning,
                           "\t\t check %s: flag (%d) supportedBit(%d), isAssignable(%d)",
                           mOtfMPPs[j]->mName.c_str(), layer->mSupportedMPPFlag,
                           (layer->mSupportedMPPFlag & mOtfMPPs[j]->mLogicalType),
                           isAssignableFlag);

                if ((layer->mSupportedMPPFlag & mOtfMPPs[j]->mLogicalType) && (isAssignableFlag)) {
                    isSupported = mOtfMPPs[j]->isSupported(*display, src_img, dst_img);
                    HDEBUGLOGD(eDebugResourceAssigning, "\t\t\t isSupported(%" PRIx64 ")",
                               -isSupported);
                    if (isSupported == NO_ERROR) {
                        *otfMPP = mOtfMPPs[j];
                        return HWC2_COMPOSITION_DEVICE;
                    }
                }
            }
        }

        /* 2. Find available m2mMPP */
        for (uint32_t j = 0; j < mM2mMPPs.size(); j++) {
            if ((display->mUseDpu == true) &&
                (mM2mMPPs[j]->mLogicalType == MPP_LOGICAL_G2D_COMBO))
                continue;
            if ((display->mUseDpu == false) &&
                (mM2mMPPs[j]->mLogicalType == MPP_LOGICAL_G2D_RGB))
                continue;

            /* Only G2D can be assigned if layer is supported by G2D
             * when window is not sufficient
             */
            if ((validateFlag == eInsufficientWindow) &&
                (mM2mMPPs[j]->mLogicalType != MPP_LOGICAL_G2D_RGB) &&
                (mM2mMPPs[j]->mLogicalType != MPP_LOGICAL_G2D_COMBO)) {
                HDEBUGLOGD(eDebugResourceAssigning,
                           "\t\tInsufficient window but exynosComposition is not assigned");
                continue;
            }

            bool isAssignableState = mM2mMPPs[j]->isAssignableState(display, src_img, dst_img);

            HDEBUGLOGD(eDebugResourceAssigning,
                       "\t\t check %s: supportedBit(%d), isAssignableState(%d)",
                       mM2mMPPs[j]->mName.c_str(),
                       (layer->mSupportedMPPFlag & mM2mMPPs[j]->mLogicalType), isAssignableState);

            float totalUsedCapa = ExynosResourceManager::getResourceUsedCapa(*mM2mMPPs[j]);
            if (isAssignableState) {
                if ((mM2mMPPs[j]->mLogicalType != MPP_LOGICAL_G2D_RGB) &&
                    (mM2mMPPs[j]->mLogicalType != MPP_LOGICAL_G2D_COMBO)) {
                    exynos_image otf_dst_img = dst_img;

                    otf_dst_img.format = DEFAULT_MPP_DST_FORMAT;

                    std::vector<exynos_image> image_lists;
                    if ((ret = getCandidateM2mMPPOutImages(display, layer, image_lists)) < 0)
                    {
                        HWC_LOGE(display, "Fail getCandidateM2mMPPOutImages (%d)", ret);
                        return ret;
                    }
                    HDEBUGLOGD(eDebugResourceAssigning, "candidate M2mMPPOutImage num: %zu",
                               image_lists.size());
                    for (auto &otf_src_img : image_lists) {
                        dumpExynosImage(eDebugResourceAssigning, otf_src_img);
                        exynos_image m2m_src_img = src_img;
                        /* transform is already handled by m2mMPP */
                        if (CC_UNLIKELY(otf_src_img.transform != 0 || otf_dst_img.transform != 0)) {
                            ALOGE("%s:: transform should be handled by m2mMPP. otf_src_img "
                                  "transform %d, otf_dst_img transform %d",
                                  __func__, otf_src_img.transform, otf_dst_img.transform);
                            otf_src_img.transform = 0;
                            otf_dst_img.transform = 0;
                        }

                        /*
                         * This is the case that layer color transform should be
                         * addressed by otfMPP not m2mMPP
                         */
                        if (otf_src_img.needColorTransform)
                            m2m_src_img.needColorTransform = false;

                        if (((isSupported = mM2mMPPs[j]->isSupported(*display, m2m_src_img,
                                                                     otf_src_img)) != NO_ERROR) ||
                            ((isAssignableFlag =
                                      mM2mMPPs[j]->hasEnoughCapa(display, m2m_src_img, otf_src_img,
                                                                 totalUsedCapa)) == false)) {
                            HDEBUGLOGD(eDebugResourceAssigning,
                                       "\t\t\t check %s: supportedBit(0x%" PRIx64
                                       "), hasEnoughCapa(%d)",
                                       mM2mMPPs[j]->mName.c_str(), -isSupported, isAssignableFlag);
                            continue;
                        }

                        otfMppReordering(display, mOtfMPPs, otf_src_img, otf_dst_img);

                        /* 3. Find available OtfMPP for output of m2mMPP */
                        for (uint32_t k = 0; k < mOtfMPPs.size(); k++) {
                            isSupported = mOtfMPPs[k]->isSupported(*display, otf_src_img, otf_dst_img);
                            isAssignableFlag = false;
                            if (isSupported == NO_ERROR) {
                                /* to prevent HW resource execeeded */
                                ExynosCompositionInfo dpuSrcInfo;
                                dpuSrcInfo.mSrcImg = otf_src_img;
                                dpuSrcInfo.mDstImg = otf_dst_img;
                                HDEBUGLOGD(eDebugTDM,
                                           "%s Composition target calculation start (candidates)",
                                           __func__);
                                calculateHWResourceAmount(display, &dpuSrcInfo);

                                isAssignableFlag = isAssignable(mOtfMPPs[k], display, otf_src_img,
                                                                otf_dst_img, &dpuSrcInfo);
                            }

                            HDEBUGLOGD(eDebugResourceAssigning,
                                       "\t\t\t check %s: supportedBit(0x%" PRIx64
                                       "), isAssignable(%d)",
                                       mOtfMPPs[k]->mName.c_str(), -isSupported, isAssignableFlag);
                            if ((isSupported == NO_ERROR) && isAssignableFlag) {
                                *m2mMPP = mM2mMPPs[j];
                                *otfMPP = mOtfMPPs[k];
                                m2m_out_img = otf_src_img;
                                return HWC2_COMPOSITION_DEVICE;
                            }
                        }
                    }
                } else {
                    if ((layer->mSupportedMPPFlag & mM2mMPPs[j]->mLogicalType) &&
                        ((isAssignableFlag = mM2mMPPs[j]->hasEnoughCapa(display, src_img, dst_img,
                                                                        totalUsedCapa) == true))) {
                        *m2mMPP = mM2mMPPs[j];
                        return HWC2_COMPOSITION_EXYNOS;
                    } else {
                        HDEBUGLOGD(eDebugResourceManager,
                                   "\t\t\t check %s: layer's mSupportedMPPFlag(0x%8x), "
                                   "hasEnoughCapa(%d)",
                                   mM2mMPPs[j]->mName.c_str(), layer->mSupportedMPPFlag,
                                   isAssignableFlag);
                    }
                }
            }
        }
    }
    /* Fail to assign resource */
    if (validateFlag != NO_ERROR)
        overlayInfo = validateFlag;
    else
        overlayInfo = eMPPUnsupported;
    return HWC2_COMPOSITION_CLIENT;
}

int32_t ExynosResourceManager::assignLayers(ExynosDisplay * display, uint32_t priority)
{
    HDEBUGLOGD(eDebugResourceAssigning, "%s:: display(%d), priority(%d) +++++", __func__,
               display->mType, priority);

    int32_t ret = NO_ERROR;
    bool needReAssign = false;
    for (uint32_t i = 0; i < display->mLayers.size(); i++) {
        ExynosLayer *layer = display->mLayers[i];
        ExynosMPP *m2mMPP = NULL;
        ExynosMPP *otfMPP = NULL;
        exynos_image m2m_out_img;
        uint32_t validateFlag = 0;
        int32_t compositionType = 0;

        if ((layer->mValidateCompositionType == HWC2_COMPOSITION_CLIENT) ||
            (layer->mValidateCompositionType == HWC2_COMPOSITION_EXYNOS))
            continue;
        if (layer->mOverlayPriority != priority)
            continue;

        exynos_image src_img;
        exynos_image dst_img;
        layer->setSrcExynosImage(&src_img);
        layer->setDstExynosImage(&dst_img);
        layer->setExynosImage(src_img, dst_img);
        layer->setExynosMidImage(dst_img);

        // TODO: call validate function for RCD layer
        if (layer->mCompositionType == HWC2_COMPOSITION_DISPLAY_DECORATION &&
            validateRCDLayer(*display, *layer, i, src_img, dst_img) == NO_ERROR) {
            layer->mValidateCompositionType = HWC2_COMPOSITION_DISPLAY_DECORATION;
            continue;
        }

        compositionType = assignLayer(display, layer, i, m2m_out_img, &m2mMPP, &otfMPP, validateFlag);
        if (compositionType == HWC2_COMPOSITION_DEVICE) {
            if (otfMPP != NULL) {
                if ((ret = otfMPP->assignMPP(display, layer)) != NO_ERROR)
                {
                    ALOGE("%s:: %s MPP assignMPP() error (%d)",
                            __func__, otfMPP->mName.c_str(), ret);
                    return ret;
                }
                HDEBUGLOGD(eDebugResourceAssigning, "\t\t[%d] layer: %s MPP is assigned", i,
                           otfMPP->mName.c_str());
            }
            if (m2mMPP != NULL) {
                if ((ret = m2mMPP->assignMPP(display, layer)) != NO_ERROR)
                {
                    ALOGE("%s:: %s MPP assignMPP() error (%d)",
                            __func__, m2mMPP->mName.c_str(), ret);
                    return ret;
                }
                layer->setExynosMidImage(m2m_out_img);
                HDEBUGLOGD(eDebugResourceAssigning, "\t\t[%d] layer: %s MPP is assigned", i,
                           m2mMPP->mName.c_str());
            }
            layer->mValidateCompositionType = compositionType;
            display->mWindowNumUsed++;
            HDEBUGLOGD(eDebugResourceAssigning, "\t\t[%d] layer: mWindowNumUsed(%d)", i,
                       display->mWindowNumUsed);
        } else if (compositionType == HWC2_COMPOSITION_EXYNOS) {
            float totalUsedCapacity = 0;
            if (m2mMPP != NULL) {
                if ((ret = m2mMPP->assignMPP(display, layer)) != NO_ERROR)
                {
                    ALOGE("%s:: %s MPP assignMPP() error (%d)",
                            __func__, m2mMPP->mName.c_str(), ret);
                    return ret;
                }
                totalUsedCapacity = getResourceUsedCapa(*m2mMPP);
                HDEBUGLOGD(eDebugResourceAssigning, "\t\t[%d] layer: %s MPP is assigned", i,
                           m2mMPP->mName.c_str());
            }
            layer->mValidateCompositionType = compositionType;

            HDEBUGLOGD(eDebugResourceAssigning, "\t\t[%d] layer: exynosComposition", i);
            /* G2D composition */
            if (((ret = display->addExynosCompositionLayer(i, totalUsedCapacity)) ==
                 EXYNOS_ERROR_CHANGED) ||
                (ret < 0))
                return ret;
            else {
                /*
                 * If high fps layer should be composited by GLES then
                 * disable handling low fps feature and reassign resources
                 */
                if ((display->mLowFpsLayerInfo.mHasLowFpsLayer == true) &&
                    (display->mClientCompositionInfo.mHasCompositionLayer == true) &&
                    ((display->mClientCompositionInfo.mFirstIndex < display->mLowFpsLayerInfo.mFirstIndex) ||
                     (display->mClientCompositionInfo.mLastIndex > display->mLowFpsLayerInfo.mLastIndex))) {
                    needReAssign = true;
                    break;
                }
            }
        } else {
            /*
             * If high fps layer should be composited by GLES then
             * disable handling low fps feature and reassign resources
            */
            if ((display->mLowFpsLayerInfo.mHasLowFpsLayer == true) &&
                (((int32_t)i < display->mLowFpsLayerInfo.mFirstIndex) ||
                 (display->mLowFpsLayerInfo.mLastIndex < (int32_t)i))) {
                needReAssign = true;
                break;
            }

            /* Fail to assign resource, set HWC2_COMPOSITION_CLIENT */
            if (validateFlag != NO_ERROR)
                layer->mOverlayInfo |= validateFlag;
            else
                layer->mOverlayInfo |= eMPPUnsupported;

            layer->mValidateCompositionType = HWC2_COMPOSITION_CLIENT;
            if (((ret = display->addClientCompositionLayer(i)) == EXYNOS_ERROR_CHANGED) ||
                (ret < 0))
                return ret;
        }
    }
    if (needReAssign) {
        if ((display->mClientCompositionInfo.mHasCompositionLayer) &&
                (display->mClientCompositionInfo.mOtfMPP != NULL))
            display->mClientCompositionInfo.mOtfMPP->resetAssignedState();

        if (display->mExynosCompositionInfo.mHasCompositionLayer) {
            if (display->mExynosCompositionInfo.mOtfMPP != NULL)
                display->mExynosCompositionInfo.mOtfMPP->resetAssignedState();
            if (display->mExynosCompositionInfo.mM2mMPP != NULL)
                display->mExynosCompositionInfo.mM2mMPP->resetAssignedState();
        }

        display->initializeValidateInfos();
        display->mLowFpsLayerInfo.initializeInfos();
        return EXYNOS_ERROR_CHANGED;
    }
    return ret;
}

int32_t ExynosResourceManager::assignWindow(ExynosDisplay *display)
{
    HDEBUGLOGD(eDebugResourceManager, "%s +++++", __func__);
    int ret = NO_ERROR;
    uint32_t windowIndex = 0;

    if (!display->mUseDpu)
        return ret;

    windowIndex = display->mBaseWindowIndex;

    for (uint32_t i = 0; i < display->mLayers.size(); i++) {
        ExynosLayer *layer = display->mLayers[i];
        HDEBUGLOGD(eDebugResourceAssigning, "\t[%d] layer type: %d", i,
                   layer->mValidateCompositionType);

        if (layer->mValidateCompositionType == HWC2_COMPOSITION_DEVICE) {
            layer->mWindowIndex = windowIndex;
            HDEBUGLOGD(eDebugResourceManager, "\t\t[%d] layer windowIndex: %d", i, windowIndex);
        } else if ((layer->mValidateCompositionType == HWC2_COMPOSITION_CLIENT) ||
                   (layer->mValidateCompositionType == HWC2_COMPOSITION_EXYNOS)) {
            ExynosCompositionInfo *compositionInfo;
            if (layer->mValidateCompositionType == HWC2_COMPOSITION_CLIENT)
                compositionInfo = &display->mClientCompositionInfo;
            else
                compositionInfo = &display->mExynosCompositionInfo;

            if ((compositionInfo->mHasCompositionLayer == false) ||
                (compositionInfo->mFirstIndex < 0) ||
                (compositionInfo->mLastIndex < 0)) {
                HWC_LOGE(display, "%s:: Invalid %s CompositionInfo mHasCompositionLayer(%d), "
                        "mFirstIndex(%d), mLastIndex(%d) ",
                        __func__, compositionInfo->getTypeStr().c_str(),
                        compositionInfo->mHasCompositionLayer,
                        compositionInfo->mFirstIndex,
                        compositionInfo->mLastIndex);
                continue;
            }
            if (i != (uint32_t)compositionInfo->mLastIndex)
                continue;
            compositionInfo->mWindowIndex = windowIndex;
            HDEBUGLOGD(eDebugResourceManager, "\t\t[%d] %s Composition windowIndex: %d",
                    i, compositionInfo->getTypeStr().c_str(), windowIndex);
        } else if (layer->mValidateCompositionType == HWC2_COMPOSITION_DISPLAY_DECORATION) {
            layer->mWindowIndex = -1;
            continue;
        } else {
            HWC_LOGE(display, "%s:: Invalid layer compositionType layer(%d), compositionType(%d)",
                    __func__, i, layer->mValidateCompositionType);
            continue;
        }
        windowIndex++;
    }
    HDEBUGLOGD(eDebugResourceManager, "%s ------", __func__);
    return ret;
}

/**
 * @param * display
 * @return int
 */
int32_t ExynosResourceManager::updateSupportedMPPFlag(ExynosDisplay * display)
{
    int64_t ret = 0;
    HDEBUGLOGD(eDebugResourceAssigning, "%s++++++++++", __func__);
    for (uint32_t i = 0; i < display->mLayers.size(); i++) {
        ExynosLayer *layer = display->mLayers[i];
        HDEBUGLOGD(eDebugResourceAssigning, "[%d] layer ", i);

        if (layer->mGeometryChanged == 0)
            continue;

        exynos_image src_img;
        exynos_image dst_img;
        exynos_image dst_img_yuv;
        layer->setSrcExynosImage(&src_img);
        layer->setDstExynosImage(&dst_img);
        layer->setDstExynosImage(&dst_img_yuv);
        dst_img.format = DEFAULT_MPP_DST_FORMAT;
        dst_img_yuv.format = DEFAULT_MPP_DST_YUV_FORMAT;
        HDEBUGLOGD(eDebugResourceAssigning, "\tsrc_img");
        dumpExynosImage(eDebugResourceAssigning, src_img);
        HDEBUGLOGD(eDebugResourceAssigning, "\tdst_img");
        dumpExynosImage(eDebugResourceAssigning, dst_img);

        /* Initialize flags */
        layer->mSupportedMPPFlag = 0;
        layer->mCheckMPPFlag.clear();

        /* Check OtfMPPs */
        for (uint32_t j = 0; j < mOtfMPPs.size(); j++) {
            if ((ret = mOtfMPPs[j]->isSupported(*display, src_img, dst_img)) == NO_ERROR) {
                layer->mSupportedMPPFlag |= mOtfMPPs[j]->mLogicalType;
                HDEBUGLOGD(eDebugResourceAssigning, "\t%s: supported", mOtfMPPs[j]->mName.c_str());
            } else {
                if (((-ret) == eMPPUnsupportedFormat) &&
                    ((ret = mOtfMPPs[j]->isSupported(*display, src_img, dst_img_yuv)) == NO_ERROR)) {
                    layer->mSupportedMPPFlag |= mOtfMPPs[j]->mLogicalType;
                    HDEBUGLOGD(eDebugResourceAssigning, "\t%s: supported with yuv dst",
                               mOtfMPPs[j]->mName.c_str());
                }
            }
            if (ret < 0) {
                HDEBUGLOGD(eDebugResourceAssigning, "\t%s: unsupported flag(0x%" PRIx64 ")",
                           mOtfMPPs[j]->mName.c_str(), -ret);
                uint64_t checkFlag = 0x0;
                if (layer->mCheckMPPFlag.find(mOtfMPPs[j]->mLogicalType) !=
                        layer->mCheckMPPFlag.end()) {
                    checkFlag = layer->mCheckMPPFlag.at(mOtfMPPs[j]->mLogicalType);
                }
                checkFlag |= (-ret);
                layer->mCheckMPPFlag[mOtfMPPs[j]->mLogicalType] = checkFlag;
            }
        }

        /* Check M2mMPPs */
        for (uint32_t j = 0; j < mM2mMPPs.size(); j++) {
            if ((ret = mM2mMPPs[j]->isSupported(*display, src_img, dst_img)) == NO_ERROR) {
                layer->mSupportedMPPFlag |= mM2mMPPs[j]->mLogicalType;
                HDEBUGLOGD(eDebugResourceAssigning, "\t%s: supported", mM2mMPPs[j]->mName.c_str());
            } else {
                if (((-ret) == eMPPUnsupportedFormat) &&
                    ((ret = mM2mMPPs[j]->isSupported(*display, src_img, dst_img_yuv)) == NO_ERROR)) {
                    layer->mSupportedMPPFlag |= mM2mMPPs[j]->mLogicalType;
                    HDEBUGLOGD(eDebugResourceAssigning, "\t%s: supported with yuv dst",
                               mM2mMPPs[j]->mName.c_str());
                }
            }
            if (ret < 0) {
                HDEBUGLOGD(eDebugResourceAssigning, "\t%s: unsupported flag(0x%" PRIx64 ")",
                           mM2mMPPs[j]->mName.c_str(), -ret);
                uint64_t checkFlag = 0x0;
                if (layer->mCheckMPPFlag.find(mM2mMPPs[j]->mLogicalType) !=
                        layer->mCheckMPPFlag.end()) {
                    checkFlag = layer->mCheckMPPFlag.at(mM2mMPPs[j]->mLogicalType);
                }
                checkFlag |= (-ret);
                layer->mCheckMPPFlag[mM2mMPPs[j]->mLogicalType] = checkFlag;
            }
        }
        HDEBUGLOGD(eDebugResourceAssigning, "[%d] layer mSupportedMPPFlag(0x%8x)", i,
                   layer->mSupportedMPPFlag);
    }
    HDEBUGLOGD(eDebugResourceAssigning, "%s-------------", __func__);

    return NO_ERROR;
}

int32_t ExynosResourceManager::resetResources()
{
    HDEBUGLOGD(eDebugResourceManager, "%s+++++++++", __func__);

    for (uint32_t i = 0; i < mOtfMPPs.size(); i++) {
        mOtfMPPs[i]->resetMPP();
        if (hwcCheckDebugMessages(eDebugResourceManager)) {
            String8 dumpMPP;
            mOtfMPPs[i]->dump(dumpMPP);
            HDEBUGLOGD(eDebugResourceManager, "%s", dumpMPP.c_str());
        }
    }
    for (uint32_t i = 0; i < mM2mMPPs.size(); i++) {
        mM2mMPPs[i]->resetMPP();
        if (hwcCheckDebugMessages(eDebugResourceManager)) {
            String8 dumpMPP;
            mM2mMPPs[i]->dump(dumpMPP);
            HDEBUGLOGD(eDebugResourceManager, "%s", dumpMPP.c_str());
        }
    }

    HDEBUGLOGD(eDebugResourceManager, "%s-----------",  __func__);
    return NO_ERROR;
}

std::pair<ExynosDisplay*, ExynosDisplay*> ExynosResourceManager::decideMainAndMinorDisplay(
        const int32_t willOnDispId) const {
    ExynosDisplay *mainDisp = nullptr, *minorDisp = nullptr;

    auto rule = [](const ExynosDisplay* left, const ExynosDisplay* right) {
        if (left->mType == right->mType) return left->mIndex < right->mIndex;
        return left->mType > right->mType;
    };

    std::set<ExynosDisplay*, decltype(rule)> availableDisps(rule);
    for (auto& [disp_id, disp] : mDisplayMap) {
        if (disp == nullptr || !disp->mPlugState || !disp->mPowerModeState.has_value() ||
            (disp->mDisplayId != willOnDispId &&
             disp->mPowerModeState.value() == (hwc2_power_mode_t)HWC_POWER_MODE_OFF))
            continue;
        availableDisps.insert(disp);
        HDEBUGLOGD(eDebugResourceManager, "%s: available display id: %d ", __func__,
                   disp->mDisplayId);
    }
    if (availableDisps.size() == 1) {
        mainDisp = *availableDisps.begin();
        HDEBUGLOGD(eDebugResourceManager, "%s: main display:%d ", __func__, mainDisp->mDisplayId);
    } else if (availableDisps.size() == 2) {
        mainDisp = *availableDisps.begin();
        minorDisp = *availableDisps.rbegin();
        HDEBUGLOGD(eDebugResourceManager, "%s: main display:%d minor display:%d ", __func__,
                   mainDisp->mDisplayId, minorDisp->mDisplayId);
    } else {
        ALOGE("%s(): wrong available display size:%zu", __func__, availableDisps.size());
    }

    return {mainDisp, minorDisp};
}

void ExynosResourceManager::updatePreAssignDisplayList(const ExynosDisplay* mainDisp,
                                                       const ExynosDisplay* minorDisp) {
    auto updatePreAssignDisplay = [=](ExynosMPP* mpp, const ExynosDisplay* mainDisp,
                                      const ExynosDisplay* minorDisp) {
        if (mpp->mPreAssignDisplayInfo == HWC_RESERVE_DISPLAY_MAIN_BIT) {
            if (mainDisp != NULL)
                mpp->mPreAssignDisplayList[DISPLAY_MODE_PRIMARY_MAIN] =
                        mainDisp->getDisplayPreAssignBit();
            else
                ALOGE("Main display does not exist!");
        } else if (mpp->mPreAssignDisplayInfo == HWC_RESERVE_DISPLAY_MINOR_BIT) {
            if (minorDisp != NULL)
                mpp->mPreAssignDisplayList[DISPLAY_MODE_PRIMARY_MAIN] =
                        minorDisp->getDisplayPreAssignBit();
            else if (mainDisp != NULL)
                mpp->mPreAssignDisplayList[DISPLAY_MODE_PRIMARY_MAIN] =
                        mainDisp->getDisplayPreAssignBit();
            else
                ALOGE("Main & minor display don't exist!");
        }
    };

    for (auto& mpp : mOtfMPPs) {
        updatePreAssignDisplay(mpp, mainDisp, minorDisp);
    }
    for (auto& mpp : mM2mMPPs) {
        updatePreAssignDisplay(mpp, mainDisp, minorDisp);
    }
}

int32_t ExynosResourceManager::preAssignResources()
{
    HDEBUGLOGD(eDebugResourceManager, "%s+++++++++", __func__);
    uint32_t displayMode = mDevice->mDisplayMode;

    for (uint32_t i = 0; i < mOtfMPPs.size(); i++) {
        if (mOtfMPPs[i]->mEnable == false) {
            mOtfMPPs[i]->reserveMPP();
            continue;
        }

        if (mOtfMPPs[i]->mPreAssignDisplayList[displayMode] != 0) {
            HDEBUGLOGD(eDebugResourceAssigning, "\t%s check, dispMode(%d), 0x%8x",
                       mOtfMPPs[i]->mName.c_str(), displayMode,
                       mOtfMPPs[i]->mPreAssignDisplayList[displayMode]);

            ExynosDisplay *display = NULL;
            for (size_t j = 0; j < mDevice->mDisplays.size(); j++) {
                display = mDevice->mDisplays[j];
                if (display == nullptr)
                    continue;
                int checkBit = mOtfMPPs[i]->mPreAssignDisplayList[displayMode] & display->getDisplayPreAssignBit();
                HDEBUGLOGD(eDebugResourceAssigning, "\t\tdisplay index(%zu), checkBit(%d)", j,
                           checkBit);
                if (checkBit) {
                    HDEBUGLOGD(eDebugResourceAssigning,
                               "\t\tdisplay index(%zu), displayId(%d), display(%p)", j,
                               display->mDisplayId, display);
                    if (display->mDisplayControl.forceReserveMPP ||
                        (display->mPlugState &&
                         ((display->mType != HWC_DISPLAY_PRIMARY) ||
                          (display->mPowerModeState.has_value() &&
                           (display->mPowerModeState.value() != HWC2_POWER_MODE_OFF))))) {
                        HDEBUGLOGD(eDebugResourceAssigning, "\t\treserve to display %d",
                                   display->mDisplayId);
                        mOtfMPPs[i]->reserveMPP(display->mDisplayId);
                        break;
                    }
                }
            }
        }
    }
    for (uint32_t i = 0; i < mM2mMPPs.size(); i++) {
        if (mM2mMPPs[i]->mEnable == false) {
            mM2mMPPs[i]->reserveMPP();
            continue;
        }

        if (mResourceReserved & mM2mMPPs[i]->mLogicalType) {
            /* MSC can't be used for rendering */
            HDEBUGLOGD(eDebugResourceManager, "\t\tMPP_MSC reserve without display because preview is running");
            mM2mMPPs[i]->reserveMPP();
            continue;
        }
        HDEBUGLOGD(eDebugResourceAssigning, "\t%s check, 0x%8x", mM2mMPPs[i]->mName.c_str(),
                   mM2mMPPs[i]->mPreAssignDisplayList[displayMode]);
        if (mM2mMPPs[i]->mPreAssignDisplayList[displayMode] != 0) {
            ExynosDisplay *display = NULL;
            for (size_t j = 0; j < mDevice->mDisplays.size(); j++) {
                display = mDevice->mDisplays[j];
                int checkBit = mM2mMPPs[i]->mPreAssignDisplayList[displayMode] & display->getDisplayPreAssignBit();
                HDEBUGLOGD(eDebugResourceAssigning, "\t\tdisplay index(%zu), checkBit(%d)", j,
                           checkBit);
                if (checkBit) {
                    HDEBUGLOGD(eDebugResourceAssigning,
                               "\t\tdisplay index(%zu), displayId(%d), display(%p)", j,
                               display->mDisplayId, display);
                    if ((display != NULL) && (display->mPlugState == true)) {
                        HDEBUGLOGD(eDebugResourceAssigning, "\t\treserve to display %d",
                                   display->mDisplayId);
                        mM2mMPPs[i]->reserveMPP(display->mDisplayId);
                        break;
                    } else {
                        HDEBUGLOGD(eDebugResourceAssigning, "\t\treserve without display");
                        mM2mMPPs[i]->reserveMPP();
                    }
                }
            }
        }
    }
    for (uint32_t i = 0; i < mOtfMPPs.size(); i++) {
        if (hwcCheckDebugMessages(eDebugResourceManager)) {
            String8 dumpMPP;
            mOtfMPPs[i]->dump(dumpMPP);
            HDEBUGLOGD(eDebugResourceManager, "%s", dumpMPP.c_str());
        }
    }
    for (uint32_t i = 0; i < mM2mMPPs.size(); i++) {
        if (hwcCheckDebugMessages(eDebugResourceManager)) {
            String8 dumpMPP;
            mM2mMPPs[i]->dump(dumpMPP);
            HDEBUGLOGD(eDebugResourceManager, "%s", dumpMPP.c_str());
        }
    }
    HDEBUGLOGD(eDebugResourceManager, "%s-----------",  __func__);
    return NO_ERROR;
}

void ExynosResourceManager::preAssignWindows(ExynosDisplay *display) {
    ExynosPrimaryDisplayModule *primaryDisplay = NULL;

    if (display->mType == HWC_DISPLAY_PRIMARY) {
        primaryDisplay = (ExynosPrimaryDisplayModule *)display;
    } else {
        primaryDisplay = (ExynosPrimaryDisplayModule *)mDevice->getDisplay(
                getDisplayId(HWC_DISPLAY_PRIMARY, 0));
    }

    primaryDisplay->usePreDefinedWindow(false);
}

int32_t ExynosResourceManager::preProcessLayer(ExynosDisplay * display)
{
    int32_t ret = 0;
    hasHdrLayer = false;
    hasDrmLayer = false;

    for (uint32_t i = 0; i < display->mLayers.size(); i++) {
        ExynosLayer *layer = display->mLayers[i];
        if ((ret = layer->doPreProcess()) < 0) {
            HWC_LOGE(display, "%s:: doPreProcess() error, display(%d), layer %d", __func__, display->mType, i);
            return ret;
        }
        /* mIsHdrLayer is known after preprocess */
        if (layer->mIsHdrLayer) hasHdrLayer = true;
        if ((layer->mLayerBuffer != NULL) && (getDrmMode(layer->mLayerBuffer) != NO_DRM))
            hasDrmLayer = true;
    }

    // Re-align layer priority for max overlay resources
    uint32_t mNumMaxPriorityLayers = 0;
    for (int i = (display->mLayers.size()-1); i >= 0; i--) {
        ExynosLayer *layer = display->mLayers[i];
        HDEBUGLOGD(eDebugResourceManager, "Priority align: i:%d, layer priority:%d, Max:%d, mNumMaxPriorityAllowed:%d", i,
                layer->mOverlayPriority, mNumMaxPriorityLayers, display->mNumMaxPriorityAllowed);
        if (layer->mOverlayPriority == ePriorityMax) {
            if (mNumMaxPriorityLayers >= display->mNumMaxPriorityAllowed) {
                layer->mOverlayPriority = ePriorityHigh;
            }
            mNumMaxPriorityLayers++;
        }
    }

    return NO_ERROR;
}

ExynosMPP* ExynosResourceManager::getExynosMPP(uint32_t type)
{
    for (uint32_t i = 0; i < mOtfMPPs.size(); i++) {
        if (mOtfMPPs[i]->mLogicalType == type)
            return mOtfMPPs[i];
    }
    for (uint32_t i = 0; i < mM2mMPPs.size(); i++) {
        if (mM2mMPPs[i]->mLogicalType == type)
            return mM2mMPPs[i];
    }

    return NULL;
}

ExynosMPP* ExynosResourceManager::getExynosMPP(uint32_t physicalType, uint32_t physicalIndex)
{
    for (uint32_t i = 0; i < mOtfMPPs.size(); i++) {
        if ((mOtfMPPs[i]->mPhysicalType == physicalType) &&
            (mOtfMPPs[i]->mPhysicalIndex == physicalIndex))
            return mOtfMPPs[i];
    }
    for (uint32_t i = 0; i < mM2mMPPs.size(); i++) {
        if ((mM2mMPPs[i]->mPhysicalType == physicalType) &&
            (mM2mMPPs[i]->mPhysicalIndex == physicalIndex))
            return mM2mMPPs[i];
    }

    return NULL;
}

int32_t ExynosResourceManager::updateResourceState()
{
    for (uint32_t i = 0; i < mOtfMPPs.size(); i++) {
        if (mOtfMPPs[i]->mAssignedSources.size() == 0)
            mOtfMPPs[i]->requestHWStateChange(MPP_HW_STATE_IDLE);
        mOtfMPPs[i]->mPrevAssignedState = mOtfMPPs[i]->mAssignedState;
    }
    for (uint32_t i = 0; i < mM2mMPPs.size(); i++) {
        if (mM2mMPPs[i]->mAssignedSources.size() == 0)
            mM2mMPPs[i]->requestHWStateChange(MPP_HW_STATE_IDLE);
        mM2mMPPs[i]->mPrevAssignedState = mM2mMPPs[i]->mAssignedState;
    }
    return NO_ERROR;
}

/*
 * This function is called every frame.
 * This base function does nothing.
 * Module that supports setting frame rate should implement this function
 * in the module source code (hardware/samsung_slsi/graphics/exynos...).
 */
void ExynosResourceManager::setFrameRateForPerformance(ExynosMPP &mpp,
        AcrylicPerformanceRequestFrame *frame)
{
    int fps = ceil(msecsPerSec / mpp.mCapacity);
    HDEBUGLOGD(eDebugResourceAssigning, "%s setFrameRate %d",
            mpp.mName.c_str(), fps);
    frame->setFrameRate(fps);
}

int32_t ExynosResourceManager::deliverPerformanceInfo()
{
    int ret = NO_ERROR;
    for (uint32_t mpp_physical_type = 0; mpp_physical_type < MPP_P_TYPE_MAX; mpp_physical_type++) {
        /* Only G2D gets performance info in current version */
        if (mpp_physical_type != MPP_G2D)
            continue;
        AcrylicPerformanceRequest request;
        uint32_t assignedInstanceNum = 0;
        uint32_t assignedInstanceIndex = 0;
        ExynosMPP *mpp = NULL;
        bool canSkipSetting = true;

        for (uint32_t i = 0; i < mM2mMPPs.size(); i++) {
            mpp = mM2mMPPs[i];
            if (mpp->mPhysicalType != mpp_physical_type)
                continue;
            /* Performance setting can be skipped
             * if all of instance's mPrevAssignedState, mAssignedState
             * are MPP_ASSIGN_STATE_FREE
             */
            if ((mpp->mPrevAssignedState & MPP_ASSIGN_STATE_ASSIGNED) ||
                (mpp->mAssignedState & MPP_ASSIGN_STATE_ASSIGNED))
            {
                canSkipSetting = false;
            }

            if (mpp->canSkipProcessing())
                continue;

            if ((mpp->mAssignedDisplay != NULL) &&
                (mpp->mAssignedSources.size() > 0))
            {
                assignedInstanceNum++;
            }
        }
        if ((canSkipSetting == true) && (assignedInstanceNum != 0)) {
            HWC_LOGE(NULL, "%s:: canSKip true but assignedInstanceNum(%d)",
                    __func__, assignedInstanceNum);
        }
        request.reset(assignedInstanceNum);

        if (canSkipSetting == true)
            continue;

        for (uint32_t i = 0; i < mM2mMPPs.size(); i++) {
            mpp = mM2mMPPs[i];
            if ((mpp->mPhysicalType == mpp_physical_type) &&
                (mpp->mAssignedDisplay != NULL) &&
                (mpp->mAssignedSources.size() > 0))
            {
                if (mpp->canSkipProcessing())
                    continue;
                if (assignedInstanceIndex >= assignedInstanceNum) {
                    HWC_LOGE(NULL,"assignedInstanceIndex error (%d, %d)", assignedInstanceIndex, assignedInstanceNum);
                    break;
                }
                AcrylicPerformanceRequestFrame *frame = request.getFrame(assignedInstanceIndex);
                if(frame->reset(mpp->mAssignedSources.size()) == false) {
                    HWC_LOGE(NULL,"%d frame reset fail (%zu)", assignedInstanceIndex, mpp->mAssignedSources.size());
                    break;
                }
                setFrameRateForPerformance(*mpp, frame);

                for (uint32_t j = 0; j < mpp->mAssignedSources.size(); j++) {
                    ExynosMPPSource* mppSource = mpp->mAssignedSources[j];
                    frame->setSourceDimension(j,
                            mppSource->mSrcImg.w, mppSource->mSrcImg.h,
                            mppSource->mSrcImg.format);

                    if (mppSource->mSrcImg.compressionInfo.type == COMP_TYPE_AFBC)
                        frame->setAttribute(j, AcrylicCanvas::ATTR_COMPRESSED);

                    hwc_rect_t src_area;
                    src_area.left = mppSource->mSrcImg.x;
                    src_area.top = mppSource->mSrcImg.y;
                    src_area.right = mppSource->mSrcImg.x + mppSource->mSrcImg.w;
                    src_area.bottom = mppSource->mSrcImg.y + mppSource->mSrcImg.h;

                    hwc_rect_t out_area;
                    out_area.left = mppSource->mMidImg.x;
                    out_area.top = mppSource->mMidImg.y;
                    out_area.right = mppSource->mMidImg.x + mppSource->mMidImg.w;
                    out_area.bottom = mppSource->mMidImg.y + mppSource->mMidImg.h;

                    frame->setTransfer(j, src_area, out_area, mppSource->mSrcImg.transform);
                }
                uint32_t format = mpp->mAssignedSources[0]->mMidImg.format;
                bool hasSolidColorLayer = false;
                if (mpp->mNeedSolidColorLayer) {
                    format = DEFAULT_MPP_DST_FORMAT;
                    hasSolidColorLayer = true;
                }

                frame->setTargetDimension(mpp->mAssignedDisplay->mXres,
                        mpp->mAssignedDisplay->mYres, format, hasSolidColorLayer);

                assignedInstanceIndex++;
            }
        }
        if ((mpp = getExynosMPP(MPP_LOGICAL_G2D_RGB)) != NULL)
            mpp->mAcrylicHandle->requestPerformanceQoS(&request);
        else
            HWC_LOGE(NULL,"getExynosMPP(MPP_LOGICAL_G2D_RGB) failed");
    }
    return ret;
}

/*
 * Get used capacity of the resource that abstracts same HW resource
 * but it is different instance with mpp
 */
float ExynosResourceManager::getResourceUsedCapa(ExynosMPP &mpp)
{
    float usedCapa = 0;
    if (mpp.mCapacity < 0)
        return usedCapa;

    HDEBUGLOGD(eDebugResourceAssigning, "%s:: [%s][%d] mpp[%d, %d]", __func__, mpp.mName.c_str(),
               mpp.mLogicalIndex, mpp.mPhysicalType, mpp.mPhysicalIndex);

    if (mpp.mMPPType == MPP_TYPE_OTF) {
        for (uint32_t i = 0; i < mOtfMPPs.size(); i++) {
            if ((mpp.mPhysicalType == mOtfMPPs[i]->mPhysicalType) &&
                (mpp.mPhysicalIndex == mOtfMPPs[i]->mPhysicalIndex)) {
                usedCapa += mOtfMPPs[i]->mUsedCapacity;
            }
        }
    } else {
        for (uint32_t i = 0; i < mM2mMPPs.size(); i++) {
            if ((mpp.mPhysicalType == mM2mMPPs[i]->mPhysicalType) &&
                (mpp.mPhysicalIndex == mM2mMPPs[i]->mPhysicalIndex)) {
                usedCapa += mM2mMPPs[i]->mUsedCapacity;
            }
        }
    }

    HDEBUGLOGD(eDebugResourceAssigning, "\t[%s][%d] mpp usedCapa: %f", mpp.mName.c_str(),
               mpp.mLogicalIndex, usedCapa);
    return usedCapa;
}

void ExynosResourceManager::enableMPP(uint32_t physicalType, uint32_t physicalIndex, uint32_t logicalIndex, uint32_t enable)
{
    for (uint32_t i = 0; i < mOtfMPPs.size(); i++) {
        if ((mOtfMPPs[i]->mPhysicalType == physicalType) &&
            (mOtfMPPs[i]->mPhysicalIndex == physicalIndex) &&
            (mOtfMPPs[i]->mLogicalIndex == logicalIndex)) {
            mOtfMPPs[i]->mEnable = !!(enable);
            return;
        }
    }

    for (uint32_t i = 0; i < mM2mMPPs.size(); i++) {
        if ((mM2mMPPs[i]->mPhysicalType == physicalType) &&
            (mM2mMPPs[i]->mPhysicalIndex == physicalIndex) &&
            (mM2mMPPs[i]->mLogicalIndex == logicalIndex)) {
            mM2mMPPs[i]->mEnable = !!(enable);
            return;
        }
    }
}

void ExynosResourceManager::setScaleDownRatio(uint32_t physicalType,
        uint32_t physicalIndex, uint32_t logicalIndex, uint32_t scaleDownRatio)
{
    ExynosMPP *findMpp = nullptr;

    auto mpp_compare = [=](ExynosMPP *mpp)->bool {
        return ((mpp->mPhysicalType == physicalType) &&
                (mpp->mPhysicalIndex == physicalIndex) &&
                (mpp->mLogicalIndex == logicalIndex));
    };

    auto otfMPP = std::find_if(mOtfMPPs.begin(), mOtfMPPs.end(),
            mpp_compare);
    if (otfMPP != mOtfMPPs.end()) {
        findMpp = *otfMPP;
    } else {
        auto m2mMPP = std::find_if(mM2mMPPs.begin(), mM2mMPPs.end(),
                mpp_compare);
        findMpp = m2mMPP == mM2mMPPs.end() ? nullptr : *m2mMPP;
    }

    if (findMpp == nullptr) {
        ALOGE("%s:: Invalid mpp (type: %d, index: %d, %d)",
                __func__, physicalType, physicalIndex, logicalIndex);
        return;
    }
    for (uint32_t i = RESTRICTION_RGB; i < RESTRICTION_MAX; i++) {
        findMpp->mDstSizeRestrictions[i].maxDownScale = scaleDownRatio;
    }
}

int32_t ExynosResourceManager::prepareResources(const int32_t willOnDispId) {
    int ret = NO_ERROR;
    HDEBUGLOGD(eDebugResourceManager, "This is first validate");
    if ((ret = resetResources()) != NO_ERROR) {
        HWC_LOGE(NULL,"%s:: resetResources() error (%d)",
                __func__, ret);
        return ret;
    }
    auto [mainDisp, minorDisp] = decideMainAndMinorDisplay(willOnDispId);

    if ((mDevice->mGeometryChanged & GEOMETRY_DISPLAY_POWER_ON) ||
        (mDevice->mGeometryChanged & GEOMETRY_DISPLAY_POWER_OFF) ||
        (mDevice->mGeometryChanged & GEOMETRY_DEVICE_DISPLAY_ADDED) ||
        (mDevice->mGeometryChanged & GEOMETRY_DEVICE_DISPLAY_REMOVED) || willOnDispId != -1)
        updatePreAssignDisplayList(mainDisp, minorDisp);

    if ((ret = preAssignResources()) != NO_ERROR) {
        HWC_LOGE(NULL,"%s:: preAssignResources() error (%d)",
                __func__, ret);
        return ret;
    }

    setDisplaysTDMInfo(mainDisp, minorDisp);

    return ret;
}

int32_t ExynosResourceManager::finishAssignResourceWork()
{
	int ret = NO_ERROR;
    if ((ret = updateResourceState()) != NO_ERROR) {
        HWC_LOGE(NULL,"%s:: stopUnAssignedResource() error (%d)",
                __func__, ret);
        return ret;
    }

    mDevice->clearGeometryChanged();
    return ret;
}

int32_t ExynosResourceManager::initResourcesState(ExynosDisplay *display)
{
    int ret = 0;

    if (mDevice->isFirstValidate()) {
        HDEBUGLOGD(eDebugResourceManager, "This is first validate");
        if (exynosHWCControl.displayMode < DISPLAY_MODE_NUM)
            mDevice->mDisplayMode = exynosHWCControl.displayMode;

        if ((ret = prepareResources()) != NO_ERROR) {
            HWC_LOGE(display, "%s:: prepareResources() error (%d)",
                    __func__, ret);
            return ret;
        }
        preAssignWindows(display);

    }

    return NO_ERROR;
}

void ExynosResourceManager::makeSizeRestrictions(uint32_t mppId, const restriction_size_t &size,
                                                 restriction_classification_t format) {
    mSizeRestrictions[format][mSizeRestrictionCnt[format]].key.hwType = static_cast<mpp_phycal_type_t>(mppId);
    mSizeRestrictions[format][mSizeRestrictionCnt[format]].key.nodeType = NODE_SRC;
    mSizeRestrictions[format][mSizeRestrictionCnt[format]].key.format = HAL_PIXEL_FORMAT_NONE;
    mSizeRestrictions[format][mSizeRestrictionCnt[format]].key.reserved = 0;
    mSizeRestrictions[format][mSizeRestrictionCnt[format]++].sizeRestriction = size;

    mSizeRestrictions[format][mSizeRestrictionCnt[format]].key.hwType = static_cast<mpp_phycal_type_t>(mppId);
    mSizeRestrictions[format][mSizeRestrictionCnt[format]].key.nodeType = NODE_DST;
    mSizeRestrictions[format][mSizeRestrictionCnt[format]].key.format = HAL_PIXEL_FORMAT_NONE;
    mSizeRestrictions[format][mSizeRestrictionCnt[format]].key.reserved = 0;
    mSizeRestrictions[format][mSizeRestrictionCnt[format]++].sizeRestriction = size;

    HDEBUGLOGD(eDebugDefault, "MPP : %s, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d",
            getMPPStr(mppId).c_str(),
            size.maxDownScale,
            size.maxUpScale,
            size.maxFullWidth,
            size.maxFullHeight,
            size.minFullWidth,
            size.minFullHeight,
            size.fullWidthAlign,
            size.fullHeightAlign,
            size.maxCropWidth,
            size.maxCropHeight,
            size.minCropWidth,
            size.minCropHeight,
            size.cropXAlign,
            size.cropYAlign,
            size.cropWidthAlign,
            size.cropHeightAlign);
}

void ExynosResourceManager::makeFormatRestrictions(restriction_key_t table) {

    mFormatRestrictions[mFormatRestrictionCnt] = table;

    HDEBUGLOGD(eDebugDefault, "MPP : %s, %d, %s, %d",
               getMPPStr(mFormatRestrictions[mFormatRestrictionCnt].hwType).c_str(),
               mFormatRestrictions[mFormatRestrictionCnt].nodeType,
               getFormatStr(mFormatRestrictions[mFormatRestrictionCnt].format, COMP_TYPE_MASK)
                       .c_str(),
               mFormatRestrictions[mFormatRestrictionCnt].reserved);
    mFormatRestrictionCnt++;
}

void ExynosResourceManager::makeAcrylRestrictions(mpp_phycal_type_t type){

    Acrylic *arc = NULL;
    const HW2DCapability *cap;

    if (type == MPP_MSC)
        arc = Acrylic::createScaler();
    else if (type == MPP_G2D)
        arc = Acrylic::createCompositor();
    else {
        ALOGE("Unknown MPP");
        return;
    }

    cap = &arc->getCapabilities();

    /* format restriction */
    std::unordered_set<int32_t> supportedHalFormats;
    for (uint32_t i = 0; i < FORMAT_MAX_CNT; i++) {
        if (cap->isFormatSupported(exynos_format_desc[i].halFormat)) {
            /* Not add same hal pixel format */
            if (supportedHalFormats.find(exynos_format_desc[i].halFormat) !=
                    supportedHalFormats.end())
                continue;
            restriction_key_t queried_format;
            queried_format.hwType = type;
            queried_format.nodeType = NODE_NONE;
            queried_format.format = exynos_format_desc[i].halFormat;
            queried_format.reserved = 0;
            makeFormatRestrictions(queried_format);
            supportedHalFormats.insert(exynos_format_desc[i].halFormat);
        }
    }

    /* RGB size restrictions */
    restriction_size rSize;
    rSize.maxDownScale = cap->supportedMinMinification().hori;
    rSize.maxUpScale = cap->supportedMaxMagnification().hori;
    rSize.maxFullWidth = cap->supportedMaxSrcDimension().hori;
    rSize.maxFullHeight = cap->supportedMaxSrcDimension().vert;
    rSize.minFullWidth = cap->supportedMinSrcDimension().hori;
    rSize.minFullHeight = cap->supportedMinSrcDimension().vert;
    rSize.fullWidthAlign = cap->supportedDimensionAlign().hori;
    rSize.fullHeightAlign = cap->supportedDimensionAlign().vert;
    rSize.maxCropWidth = cap->supportedMaxSrcDimension().hori;
    rSize.maxCropHeight = cap->supportedMaxSrcDimension().vert;
    rSize.minCropWidth = cap->supportedMinSrcDimension().hori;
    rSize.minCropHeight = cap->supportedMinSrcDimension().vert;
    rSize.cropXAlign = cap->supportedDimensionAlign().hori;
    rSize.cropYAlign = cap->supportedDimensionAlign().vert;
    rSize.cropWidthAlign = cap->supportedDimensionAlign().hori;
    rSize.cropHeightAlign = cap->supportedDimensionAlign().vert;

    makeSizeRestrictions(type, rSize, RESTRICTION_RGB);

    /* YUV size restrictions */
    rSize.fullWidthAlign = max(static_cast<uint32_t>(cap->supportedDimensionAlign().hori),
            YUV_CHROMA_H_SUBSAMPLE);
    rSize.fullHeightAlign = max(static_cast<uint32_t>(cap->supportedDimensionAlign().vert),
            YUV_CHROMA_V_SUBSAMPLE);
    rSize.cropXAlign = max(static_cast<uint32_t>(cap->supportedDimensionAlign().hori),
            YUV_CHROMA_H_SUBSAMPLE);
    rSize.cropYAlign = max(static_cast<uint32_t>(cap->supportedDimensionAlign().vert),
            YUV_CHROMA_V_SUBSAMPLE);
    rSize.cropWidthAlign = max(static_cast<uint32_t>(cap->supportedDimensionAlign().hori),
            YUV_CHROMA_H_SUBSAMPLE);
    rSize.cropHeightAlign = max(static_cast<uint32_t>(cap->supportedDimensionAlign().vert),
            YUV_CHROMA_V_SUBSAMPLE);

    makeSizeRestrictions(type, rSize, RESTRICTION_YUV);

    delete arc;
}

mpp_phycal_type_t ExynosResourceManager::getPhysicalType(int ch) const {

    for (int i=0; i < MAX_DECON_DMA_TYPE; i++){
        if(idma_channel_map[i].channel == ch)
            return idma_channel_map[i].type;
    }

    return MPP_P_TYPE_MAX;
}

ExynosMPP* ExynosResourceManager::getOtfMPPWithChannel(int ch)
{
    ExynosMPP *otfMPP = NULL;

    for (int i=0; i < MAX_DECON_DMA_TYPE; i++){
        if(idma_channel_map[i].channel == ch) {
            otfMPP = getExynosMPP(idma_channel_map[i].type, idma_channel_map[i].index);
            break;
        }
    }
    return otfMPP;
}

void ExynosResourceManager::updateRestrictions() {

    if (mDevice->mDeviceInterface->getUseQuery() == true) {
        std::unordered_set<uint32_t> checkDuplicateMPP;
        for (const auto unit: AVAILABLE_M2M_MPP_UNITS) {
            if (checkDuplicateMPP.find(unit.physicalType) ==
                    checkDuplicateMPP.end())  {
                makeAcrylRestrictions(static_cast<mpp_phycal_type_t>(unit.physicalType));
                checkDuplicateMPP.insert(unit.physicalType);
            }
        }
    } else {
        mFormatRestrictionCnt = sizeof(restriction_format_table)/sizeof(restriction_key);
        for (uint32_t i = 0 ; i < mFormatRestrictionCnt; i++) {
            mFormatRestrictions[i].hwType = restriction_format_table[i].hwType;
            mFormatRestrictions[i].nodeType = restriction_format_table[i].nodeType;
            mFormatRestrictions[i].format = restriction_format_table[i].format;
            mFormatRestrictions[i].reserved = restriction_format_table[i].reserved;
        }

        // i = RGB, YUV
        // j = Size restriction count for each format (YUV, RGB)
        for (uint32_t i = 0; i < sizeof(restriction_tables)/sizeof(restriction_table_element); i++) {
            mSizeRestrictionCnt[i] = restriction_tables[i].table_element_size;
            for (uint32_t j = 0; j < mSizeRestrictionCnt[i]; j++) {
                memcpy(&mSizeRestrictions[i][j], &restriction_tables[i].table[j],
                        sizeof(mSizeRestrictions[i][j]));
            }
        }
    }

    for (uint32_t i = 0; i < mOtfMPPs.size(); i++) {
        // mAttr should be updated with updated feature_table
        mOtfMPPs[i]->updateAttr();
        mOtfMPPs[i]->setupRestriction();
    }

    for (uint32_t i = 0; i < mM2mMPPs.size(); i++) {
        // mAttr should be updated with updated feature_table
        mM2mMPPs[i]->updateAttr();
        mM2mMPPs[i]->setupRestriction();
    }
}

uint32_t ExynosResourceManager::getFeatureTableSize() const
{
    return sizeof(feature_table)/sizeof(feature_support_t);
}

bool ExynosResourceManager::hasHDR10PlusMPP() {

    for (uint32_t i = 0; i < mOtfMPPs.size(); i++) {
        if (mOtfMPPs[i] == NULL) continue;
        if (mOtfMPPs[i]->mAttr & MPP_ATTR_HDR10PLUS)
            return true;
    }
    for (uint32_t i = 0; i < mM2mMPPs.size(); i++) {
        if (mM2mMPPs[i] == NULL) continue;
        if (mM2mMPPs[i]->mAttr & MPP_ATTR_HDR10PLUS)
            return true;
    }

    return false;
}

float ExynosResourceManager::getAssignedCapacity(uint32_t physicalType)
{
    float totalCapacity = 0;

    for (size_t i = 0; i < mM2mMPPs.size(); i++) {
        if (mM2mMPPs[i]->mPhysicalType == physicalType)
            totalCapacity += mM2mMPPs[i]->getAssignedCapacity();
    }
    return totalCapacity;
}

float ExynosResourceManager::getM2MCapa(uint32_t physicalType)
{
    float ret = 0;
    for (size_t i = 0; i < mM2mMPPs.size(); i++) {
        if (mM2mMPPs[i]->mPhysicalType == physicalType)
            return mM2mMPPs[i]->mCapacity;
    }

    return ret;
}

void ExynosResourceManager::dump(String8 &result) const {
    result.appendFormat("Resource Manager:\n");

    result.appendFormat("[RGB Restrictions]\n");
    dump(RESTRICTION_RGB, result);

    result.appendFormat("[YUV Restrictions]\n");
    dump(RESTRICTION_YUV, result);

    result.appendFormat("[MPP Dump]\n");
    for (auto mpp : mOtfMPPs) {
        mpp->dump(result);
    }
    for (auto mpp : mM2mMPPs) {
        mpp->dump(result);
    }
}

void ExynosResourceManager::dump(const restriction_classification_t classification,
                                 String8 &result) const {
    const auto &restrictions = mSizeRestrictions[classification];
    const auto &restrictionCnt = mSizeRestrictionCnt[classification];

    for (int i = 0; i < restrictionCnt; ++i) {
        result.appendFormat("HW-Node %u-%u:\n", restrictions[i].key.hwType,
                            restrictions[i].key.nodeType);
        if (i > 0 && restrictions[i].sizeRestriction == restrictions[i - 1].sizeRestriction) {
            result.append("Same as above\n");
        } else {
            ::dump(restrictions[i].sizeRestriction, result);
        }
        result.appendFormat("\n");
    }
}

void ExynosResourceManager::setM2MCapa(uint32_t physicalType, uint32_t capa)
{
    for (size_t i = 0; i < mM2mMPPs.size(); i++) {
        if (mM2mMPPs[i]->mPhysicalType == physicalType)
            mM2mMPPs[i]->mCapacity = capa;
    }
}

bool ExynosResourceManager::isAssignable(ExynosMPP *candidateMPP, ExynosDisplay *display,
                                         struct exynos_image &src, struct exynos_image &dst,
                                         ExynosMPPSource *mppSrc)
{
    bool ret = true;

    float totalUsedCapacity = getResourceUsedCapa(*candidateMPP);
    ret = candidateMPP->isAssignable(display, src, dst, totalUsedCapacity);

    if ((ret) && (mppSrc != nullptr)) {
        if ((candidateMPP->mMPPType == MPP_TYPE_OTF) &&
            (!isHWResourceAvailable(display, candidateMPP, mppSrc))) {
            if (mppSrc->mSourceType == MPP_SOURCE_LAYER) {
                ExynosLayer *layer = (ExynosLayer *)mppSrc;
                layer->mCheckMPPFlag[candidateMPP->mLogicalType] = eMPPExeedHWResource;
            }
            ret = false;
        }
    }

    return ret;
}

void ExynosResourceManager::updateSupportWCG()
{
    for (uint32_t i = 0; i < mOtfMPPs.size(); i++) {
        if (mOtfMPPs[i] == NULL) continue;
        if (mOtfMPPs[i]->mAttr & (MPP_ATTR_WCG | MPP_ATTR_HDR10)) mDeviceSupportWCG = true;
    }
    for (uint32_t i = 0; i < mM2mMPPs.size(); i++) {
        if (mM2mMPPs[i] == NULL) continue;
        if (mM2mMPPs[i]->mAttr & (MPP_ATTR_WCG | MPP_ATTR_HDR10)) mDeviceSupportWCG = true;
    }
}

bool ExynosResourceManager::needHdrProcessing(ExynosDisplay *display, exynos_image &srcImg,
                                              exynos_image &dstImg)
{
    if (!deviceSupportWCG()) return false;

    return true;
}

uint32_t ExynosResourceManager::needHWResource(ExynosDisplay *display, exynos_image &srcImg,
                                               exynos_image &dstImg, tdm_attr_t attr)
{
    uint32_t ret = 0;

    switch (attr) {
        case TDM_ATTR_SBWC:
            ret = (srcImg.compressionInfo.type == COMP_TYPE_SBWC) ? 1 : 0;
            break;
        case TDM_ATTR_AFBC:
            ret = (srcImg.compressionInfo.type == COMP_TYPE_AFBC) ? 1 : 0;
            break;
        case TDM_ATTR_ITP:
            ret = (isFormatYUV(srcImg.format)) ? 1 : 0;
            break;
        case TDM_ATTR_WCG:
            ret = (srcImg.needPreblending) ? 1 : 0;
            break;
        case TDM_ATTR_ROT_90:
            ret = ((srcImg.transform & HAL_TRANSFORM_ROT_90) == 0) ? 0 : 1;
            break;
        case TDM_ATTR_SCALE: {
            bool isPerpendicular = !!(srcImg.transform & HAL_TRANSFORM_ROT_90);
            if (isPerpendicular) {
                ret = ((srcImg.w != dstImg.h) || (srcImg.h != dstImg.w)) ? 1 : 0;
            } else {
                ret = ((srcImg.w != dstImg.w) || (srcImg.h != dstImg.h)) ? 1 : 0;
            }
        } break;
        default:
            ret = 0;
            break;
    }

    return ret;
}
