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

#undef LOG_TAG
#define LOG_TAG "hwc-virt-display"
#include "ExynosVirtualDisplay.h"
#include "../libdevice/ExynosDevice.h"
#include "../libdevice/ExynosLayer.h"

#include "ExynosHWCHelper.h"
#include "VendorGraphicBuffer.h"

using vendor::graphics::BufferUsage;
using vendor::graphics::VendorGraphicBufferUsage;

extern struct exynos_hwc_control exynosHWCControl;

ExynosVirtualDisplay::ExynosVirtualDisplay(uint32_t index, ExynosDevice* device,
                                           const std::string& displayName)
      : ExynosDisplay(HWC_DISPLAY_VIRTUAL, index, device, displayName) {
    /* Initialization */

    mDisplayControl.earlyStartMPP = false;

    mOutputBufferAcquireFenceFd = -1;
    mOutputBufferReleaseFenceFd = -1;

    mIsWFDState = 0;
    mIsSecureVDSState = false;
    mIsSkipFrame = false;
    mPresentationMode = false;

    // TODO : Hard coded currently
    mNumMaxPriorityAllowed = 1;

    mDisplayWidth = 0;
    mDisplayHeight = 0;
    mOutputBuffer = NULL;
    mCompositionType = COMPOSITION_GLES;
    mGLESFormat = HAL_PIXEL_FORMAT_RGBA_8888;
    mSinkUsage = BufferUsage::COMPOSER_OVERLAY | BufferUsage::VIDEO_ENCODER;
    mIsSecureDRM = false;
    mIsNormalDRM = false;
    mNeedReloadResourceForHWFC = false;
    mMinTargetLuminance = 0;
    mMaxTargetLuminance = 100;
    mSinkDeviceType = 0;

    mUseDpu = false;
    mDisplayControl.enableExynosCompositionOptimization = false;
    mDisplayControl.enableClientCompositionOptimization = false;
    mDisplayControl.handleLowFpsLayers = false;
    mMaxWindowNum = 0;
}

ExynosVirtualDisplay::~ExynosVirtualDisplay()
{

}

void ExynosVirtualDisplay::createVirtualDisplay(uint32_t width, uint32_t height, int32_t* format)
{
    ALOGI("Virtual display is added. width(%d), height(%d), format(%d)", width, height, *format);

    initDisplay();

    // Virtual Display don't use skip static layer.
    mClientCompositionInfo.mEnableSkipStatic = false;

    mPlugState = true;
    mDisplayWidth = width;
    mDisplayHeight = height;
    mXres = width;
    mYres = height;
    mGLESFormat = *format;
}

void ExynosVirtualDisplay::destroyVirtualDisplay()
{
    ALOGI("Virtual display is deleted");

    mPlugState = false;
    mDisplayWidth = 0;
    mDisplayHeight = 0;
    mXres = 0;
    mYres = 0;
    mMinTargetLuminance = 0;
    mMaxTargetLuminance = 100;
    mSinkDeviceType = 0;
    mCompositionType = COMPOSITION_GLES;
    mGLESFormat = HAL_PIXEL_FORMAT_RGBA_8888;
    mResourceManager->reloadResourceForHWFC();
    mResourceManager->setTargetDisplayLuminance(mMinTargetLuminance, mMaxTargetLuminance);
    mResourceManager->setTargetDisplayDevice(mSinkDeviceType);
    mNeedReloadResourceForHWFC = false;
}

int ExynosVirtualDisplay::setWFDMode(unsigned int mode)
{
    if ((mode == GOOGLEWFD_TO_LLWFD || mode == LLWFD_TO_GOOGLEWFD))
        mNeedReloadResourceForHWFC = true;
    mIsWFDState = mode;
    return HWC2_ERROR_NONE;
}

int ExynosVirtualDisplay::getWFDMode()
{
    return mIsWFDState;
}

int ExynosVirtualDisplay::sendWFDCommand(int32_t cmd, int32_t ext1, int32_t ext2)
{
    ALOGI("sendWFDCommand: cmd(%d), ext1(%d), ext2(%d)", cmd, ext1, ext2);

    int ret = 0;

    switch (cmd) {
        case SET_WFD_MODE:
            /* ext1: mode, ext2: unused */
            ret = setWFDMode(ext1);
            break;
        case SET_TARGET_DISPLAY_LUMINANCE:
            /* ext1: min, ext2: max */
            mMinTargetLuminance = (uint16_t)ext1;
            mMaxTargetLuminance = (uint16_t)ext2;
            mResourceManager->setTargetDisplayLuminance(mMinTargetLuminance, mMaxTargetLuminance);
            break;
        case SET_TARGET_DISPLAY_DEVICE:
            /* ext1: type, ext2: unused */
            mSinkDeviceType = ext1;
            mResourceManager->setTargetDisplayDevice(mSinkDeviceType);
            break;
        default:
            ALOGE("invalid cmd(%d)", cmd);
            break;
    }

    return ret;
}

int ExynosVirtualDisplay::setSecureVDSMode(unsigned int mode)
{
    mIsWFDState = mode;
    mIsSecureVDSState = !!mode;
    return HWC2_ERROR_NONE;
}

int ExynosVirtualDisplay::setWFDOutputResolution(
    unsigned int width, unsigned int height)
{
    mDisplayWidth = width;
    mDisplayHeight = height;
    mXres = width;
    mYres = height;
    return HWC2_ERROR_NONE;
}

void ExynosVirtualDisplay::getWFDOutputResolution(
    unsigned int *width, unsigned int *height)
{
    *width = mDisplayWidth;
    *height = mDisplayHeight;
}

void ExynosVirtualDisplay::setPresentationMode(bool use)
{
    mPresentationMode = use;
}

int ExynosVirtualDisplay::getPresentationMode(void)
{
    return mPresentationMode;
}

int ExynosVirtualDisplay::setVDSGlesFormat(int format)
{
    DISPLAY_LOGD(eDebugVirtualDisplay, "setVDSGlesFormat: 0x%x", format);
    mGLESFormat = format;
    return HWC2_ERROR_NONE;
}

int32_t ExynosVirtualDisplay::setOutputBuffer(
    buffer_handle_t buffer, int32_t releaseFence) {
    mOutputBuffer = buffer;
    mOutputBufferAcquireFenceFd = hwc_dup(releaseFence,
            this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_G2D);
    releaseFence = fence_close(releaseFence, this, FENCE_TYPE_SRC_RELEASE, FENCE_IP_G2D);

    if (mExynosCompositionInfo.mM2mMPP != NULL) {
        mExynosCompositionInfo.mM2mMPP->setOutBuf(mOutputBuffer, mOutputBufferAcquireFenceFd);
        mOutputBufferAcquireFenceFd = -1;
    }

    DISPLAY_LOGD(eDebugVirtualDisplay, "setOutputBuffer(), mOutputBufferAcquireFenceFd %d", mOutputBufferAcquireFenceFd);
    return HWC2_ERROR_NONE;
}

int ExynosVirtualDisplay::clearDisplay(bool needModeClear) {
    return 0;
}

int32_t ExynosVirtualDisplay::validateDisplay(
    uint32_t* outNumTypes, uint32_t* outNumRequests)
{
    DISPLAY_LOGD(eDebugVirtualDisplay, "validateDisplay");
    int32_t ret = HWC2_ERROR_NONE;

    initPerFrameData();

    mClientCompositionInfo.setCompressionType(COMP_TYPE_NONE);

    if (mNeedReloadResourceForHWFC) {
        ALOGI("validateDisplay() mIsWFDState %d", mIsWFDState);
        mResourceManager->reloadResourceForHWFC();
        mResourceManager->setTargetDisplayLuminance(mMinTargetLuminance, mMaxTargetLuminance);
        mResourceManager->setTargetDisplayDevice(mSinkDeviceType);
        mNeedReloadResourceForHWFC = false;
    }

    /* validateDisplay should be called for preAssignResource */
    ret = ExynosDisplay::validateDisplay(outNumTypes, outNumRequests);

    if (checkSkipFrame()) {
        handleSkipFrame();
    } else {
        setDrmMode();
        setSinkBufferUsage();
        setCompositionType();
    }

    return ret;
}

int32_t ExynosVirtualDisplay::canSkipValidate() {
#if 1   // disable the skipValidate features when virtual display is added
    return SKIP_ERR_FORCE_VALIDATE;
#else
    if (checkSkipFrame())
        return SKIP_ERR_FORCE_VALIDATE;

    return ExynosDisplay::canSkipValidate();
#endif
}

int32_t ExynosVirtualDisplay::presentDisplay(
    int32_t* outRetireFence)
{
    DISPLAY_LOGD(eDebugVirtualDisplay, "presentDisplay, mCompositionType %d",
        mCompositionType);

    int32_t ret = HWC2_ERROR_NONE;

    if (mIsSkipFrame) {
        if ((exynosHWCControl.skipValidate == true) &&
            ((mRenderingState == RENDERING_STATE_PRESENTED) ||
             (mRenderingState == RENDERING_STATE_NONE))) {

            if (mDevice->canSkipValidate() == false) {
                mRenderingState = RENDERING_STATE_NONE;
                return HWC2_ERROR_NOT_VALIDATED;
            } else {
                DISPLAY_LOGD(eDebugSkipValidate, "validate is skipped");
            }
        }

        handleAcquireFence();
        /* this frame is not presented, but mRenderingState is updated to RENDERING_STATE_PRESENTED */
        mRenderingState = RENDERING_STATE_PRESENTED;

        /*
         * Resource assignment information was initialized during skipping frames
         * So resource assignment for the first displayed frame after skpping frames
         * should not be skipped
         */
        setGeometryChanged(GEOMETRY_DISPLAY_FORCE_VALIDATE);

        return ret;
    }

    ret = ExynosDisplay::presentDisplay(outRetireFence);

    /* handle outbuf acquireFence */
    mOutputBufferAcquireFenceFd = fence_close(mOutputBufferAcquireFenceFd, this,
            FENCE_TYPE_DST_ACQUIRE, FENCE_IP_G2D);

    if (*outRetireFence == -1 && mOutputBufferReleaseFenceFd >= 0) {
        *outRetireFence = mOutputBufferReleaseFenceFd;
        mOutputBufferReleaseFenceFd = -1;
    }

    DISPLAY_LOGD(eDebugVirtualDisplay, "presentDisplay(), outRetireFence %d", *outRetireFence);

    return ret;
}

int ExynosVirtualDisplay::setWinConfigData()
{
    return NO_ERROR;
}

int ExynosVirtualDisplay::setDisplayWinConfigData()
{
    return NO_ERROR;
}

int32_t ExynosVirtualDisplay::validateWinConfigData()
{
    return NO_ERROR;
}

int ExynosVirtualDisplay::deliverWinConfigData()
{
    mDpuData.retire_fence = -1;
    return 0;
}

int ExynosVirtualDisplay::setReleaseFences()
{
    DISPLAY_LOGD(eDebugVirtualDisplay, "setReleaseFences(), mCompositionType %d", mCompositionType);

    int ret = 0;

    if (mClientCompositionInfo.mHasCompositionLayer) {
        int fence;
        uint32_t framebufferTargetIndex;
        framebufferTargetIndex = mExynosCompositionInfo.mM2mMPP->getAssignedSourceNum() - 1;
        fence = mExynosCompositionInfo.mM2mMPP->getSrcReleaseFence(framebufferTargetIndex);
        if (fence > 0)
            fence_close(fence, this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_FB);
    }

    ret = ExynosDisplay::setReleaseFences();

    mOutputBufferReleaseFenceFd = hwcCheckFenceDebug(this, FENCE_TYPE_RETIRE, FENCE_IP_G2D, mExynosCompositionInfo.mAcquireFence);
    setFenceInfo(mExynosCompositionInfo.mAcquireFence, this, FENCE_TYPE_RETIRE, FENCE_IP_G2D,
                 HwcFenceDirection::TO);
    mExynosCompositionInfo.mAcquireFence = -1;
    /* mClientCompositionInfo.mAcquireFence is delivered to G2D */
    mClientCompositionInfo.mAcquireFence = -1;

    return ret;
}

bool ExynosVirtualDisplay::checkFrameValidation()
{
    if (mOutputBuffer == NULL) {
        handleAcquireFence();
        return false;
    }

    buffer_handle_t outbufHandle = mOutputBuffer;
    if (outbufHandle == NULL) {
        handleAcquireFence();
        return false;
    }

    if (mCompositionType != COMPOSITION_HWC) {
        if (mClientCompositionInfo.mTargetBuffer == NULL) {
            handleAcquireFence();
            return false;
        }
    }

    return true;

}

void ExynosVirtualDisplay::setSinkBufferUsage()
{
    mSinkUsage = BufferUsage::COMPOSER_OVERLAY | BufferUsage::VIDEO_ENCODER;
    if (mIsSecureDRM) {
        mSinkUsage |= BufferUsage::CPU_READ_NEVER |
            BufferUsage::CPU_WRITE_NEVER |
            BufferUsage::PROTECTED;
    } else if (mIsNormalDRM)
        mSinkUsage |= VendorGraphicBufferUsage::PRIVATE_NONSECURE;
}

void ExynosVirtualDisplay::setCompositionType()
{
    size_t compositionClientLayerCount = 0;
    size_t CompositionDeviceLayerCount = 0;;
    for (size_t i = 0; i < mLayers.size(); i++) {
        ExynosLayer *layer = mLayers[i];
        if (layer->mValidateCompositionType == HWC2_COMPOSITION_CLIENT ||
            layer->mValidateCompositionType == HWC2_COMPOSITION_INVALID) {
            compositionClientLayerCount++;
        }
        if (layer->mValidateCompositionType == HWC2_COMPOSITION_DEVICE ||
            layer->mValidateCompositionType == HWC2_COMPOSITION_EXYNOS) {
            CompositionDeviceLayerCount++;
        }
    }
    if (compositionClientLayerCount > 0 && CompositionDeviceLayerCount > 0) {
        mCompositionType = COMPOSITION_MIXED;
    } else if (CompositionDeviceLayerCount > 0) {
        mCompositionType = COMPOSITION_HWC;
    } else {
        mCompositionType = COMPOSITION_GLES;
    }

    if (mCompositionType == COMPOSITION_GLES)
        mCompositionType = COMPOSITION_MIXED;

    DISPLAY_LOGD(eDebugVirtualDisplay, "setCompositionType(), compositionClientLayerCount %zu, CompositionDeviceLayerCount %zu, mCompositionType %d",
        compositionClientLayerCount, CompositionDeviceLayerCount, mCompositionType);
}

void ExynosVirtualDisplay::initPerFrameData()
{
    mIsSkipFrame = false;
    mIsSecureDRM = false;
    mIsNormalDRM = false;
    mCompositionType = COMPOSITION_HWC;
    mSinkUsage = BufferUsage::COMPOSER_OVERLAY | BufferUsage::VIDEO_ENCODER;
}

bool ExynosVirtualDisplay::checkSkipFrame()
{
    // FIXME: HWC2_COMPOSITION_SCREENSHOT is not dfeind in AOSP
    //        HWC guys should fix this.
#if 0
    for (size_t i = 0; i < mLayers.size(); i++) {
        ExynosLayer *layer = mLayers[i];
        if (layer->mCompositionType == HWC2_COMPOSITION_SCREENSHOT) {
            DISPLAY_LOGD(eDebugVirtualDisplay, "checkSkipFrame(), skip rotation animation layer");
            return true;
        }
    }
#endif

    if (mLayers.size() == 0) {
        DISPLAY_LOGD(eDebugVirtualDisplay, "checkSkipFrame(), mLayers.size() %zu", mLayers.size());
        return true;
    }

    if (mIsWFDState == 0) {
        DISPLAY_LOGD(eDebugVirtualDisplay, "checkSkipFrame(), mIsWFDState %d", mIsWFDState);
        return true;
    }

    if (mIsWFDState == GOOGLEWFD_TO_LLWFD || mIsWFDState == LLWFD_TO_GOOGLEWFD) {
        DISPLAY_LOGD(eDebugVirtualDisplay, "checkSkipFrame(), mIsWFDState %d", mIsWFDState);
        return true;
    }

    return false;
}

void ExynosVirtualDisplay::setDrmMode()
{
    mIsSecureDRM = false;
    for (size_t i = 0; i < mLayers.size(); i++) {
        ExynosLayer *layer = mLayers[i];
        if ((layer->mValidateCompositionType == HWC2_COMPOSITION_DEVICE ||
            layer->mValidateCompositionType == HWC2_COMPOSITION_EXYNOS) &&
            layer->mLayerBuffer && getDrmMode(layer->mLayerBuffer) == SECURE_DRM) {
            mIsSecureDRM = true;
            DISPLAY_LOGD(eDebugVirtualDisplay, "include secure drm layer");
        }
        if ((layer->mValidateCompositionType == HWC2_COMPOSITION_DEVICE ||
            layer->mValidateCompositionType == HWC2_COMPOSITION_EXYNOS) &&
            layer->mLayerBuffer && getDrmMode(layer->mLayerBuffer) == NORMAL_DRM) {
            mIsNormalDRM = true;
            DISPLAY_LOGD(eDebugVirtualDisplay, "include normal drm layer");
        }
    }
    DISPLAY_LOGD(eDebugVirtualDisplay, "setDrmMode(), mIsSecureDRM %d", mIsSecureDRM);
}

void ExynosVirtualDisplay::handleSkipFrame()
{
    mIsSkipFrame = true;
    for (size_t i = 0; i < mLayers.size(); i++) {
        ExynosLayer *layer = mLayers[i];
        layer->mValidateCompositionType = HWC2_COMPOSITION_DEVICE;
    }
    mIsSecureDRM = false;
    mIsNormalDRM = false;
    mCompositionType = COMPOSITION_HWC;
    mSinkUsage = BufferUsage::COMPOSER_OVERLAY | BufferUsage::VIDEO_ENCODER;
    DISPLAY_LOGD(eDebugVirtualDisplay, "handleSkipFrame()");
}

void ExynosVirtualDisplay::handleAcquireFence()
{
    /* handle fence of DEVICE or EXYNOS composition layers */
    for (size_t i = 0; i < mLayers.size(); i++) {
        ExynosLayer *layer = mLayers[i];
        if (layer->mValidateCompositionType == HWC2_COMPOSITION_DEVICE ||
            layer->mValidateCompositionType == HWC2_COMPOSITION_EXYNOS) {
            layer->mReleaseFence = layer->mAcquireFence;
            setFenceInfo(layer->mAcquireFence, this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_LAYER,
                         HwcFenceDirection::TO);
            layer->mAcquireFence = -1;
        }
    }
    mClientCompositionInfo.mReleaseFence = mClientCompositionInfo.mAcquireFence;
    setFenceInfo(mClientCompositionInfo.mAcquireFence, this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_FB,
                 HwcFenceDirection::TO);
    mClientCompositionInfo.mAcquireFence = -1;

    mOutputBufferReleaseFenceFd = mOutputBufferAcquireFenceFd;
    mOutputBufferAcquireFenceFd = -1;
    DISPLAY_LOGD(eDebugVirtualDisplay, "handleAcquireFence()");
}

int32_t ExynosVirtualDisplay::getHdrCapabilities(uint32_t* outNumTypes,
        int32_t* outTypes, float* outMaxLuminance,
        float* outMaxAverageLuminance, float* outMinLuminance)
{
    if (outTypes == NULL) {
        *outNumTypes = 1;
        return 0;
    }
    outTypes[0] = HAL_HDR_HDR10;
    return 0;
}
