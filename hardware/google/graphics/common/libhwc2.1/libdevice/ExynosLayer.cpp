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

#include <aidl/android/hardware/graphics/common/BufferUsage.h>
#include <utils/Errors.h>
#include <linux/videodev2.h>
#include <sys/mman.h>
#include <hardware/hwcomposer_defs.h>
#include <hardware/exynos/ion.h>

#include "BrightnessController.h"
#include "ExynosLayer.h"
#include "ExynosResourceManager.h"
#include "ExynosHWCDebug.h"
#include "ExynosExternalDisplay.h"

#include "VendorVideoAPI.h"

/**
 * ExynosLayer implementation
 */

using AidlBufferUsage = ::aidl::android::hardware::graphics::common::BufferUsage;

ExynosLayer::ExynosLayer(ExynosDisplay* display)
      : ExynosMPPSource(MPP_SOURCE_LAYER, this),
        mDisplay(display),
        mCompositionType(HWC2_COMPOSITION_INVALID),
        mRequestedCompositionType(HWC2_COMPOSITION_INVALID),
        mExynosCompositionType(HWC2_COMPOSITION_INVALID),
        mValidateCompositionType(HWC2_COMPOSITION_INVALID),
        mPrevValidateCompositionType(HWC2_COMPOSITION_INVALID),
        mValidateExynosCompositionType(HWC2_COMPOSITION_INVALID),
        mOverlayInfo(0x0),
        mSupportedMPPFlag(0x0),
        mFps(0),
        mOverlayPriority(ePriorityLow),
        mGeometryChanged(0x0),
        mWindowIndex(0),
        mCompressionInfo({COMP_TYPE_NONE, 0}),
        mAcquireFence(-1),
        mPrevAcquireFence(-1),
        mReleaseFence(-1),
        mFrameCount(0),
        mLastFrameCount(0),
        mLastFpsTime(0),
        mNextLastFrameCount(0),
        mNextLastFpsTime(0),
        mLastLayerBuffer(NULL),
        mLayerBuffer(NULL),
        mLastUpdateTime(0),
        mDamageNum(0),
        mBlending(HWC2_BLEND_MODE_NONE),
        mPlaneAlpha(1.0),
        mTransform(0),
        mZOrder(0),
        mDataSpace(HAL_DATASPACE_UNKNOWN),
        mLayerFlag(0x0),
        mIsHdrLayer(false),
        mBufferHasMetaParcel(false),
        mMetaParcelFd(-1) {
    memset(&mDisplayFrame, 0, sizeof(mDisplayFrame));
    memset(&mSourceCrop, 0, sizeof(mSourceCrop));
    mVisibleRegionScreen.numRects = 0;
    mVisibleRegionScreen.rects = NULL;
    memset(&mColor, 0, sizeof(mColor));
    memset(&mPreprocessedInfo, 0, sizeof(mPreprocessedInfo));
    mCheckMPPFlag.clear();
    mCheckMPPFlag.reserve(MPP_LOGICAL_TYPE_NUM);
    mMetaParcel = NULL;
    mDamageRects.clear();
}

ExynosLayer::~ExynosLayer() {
    if (mMetaParcel != NULL) {
        munmap(mMetaParcel, sizeof(ExynosVideoMeta));
        mMetaParcel = NULL;
    }

    if (mMetaParcelFd >= 0) {
        close(mMetaParcelFd);
        mMetaParcelFd = -1;
    }

    if (mAcquireFence >= 0) {
        mAcquireFence =
                fence_close(mAcquireFence, mDisplay, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_UNDEFINED);
    }

    if (mPrevAcquireFence != -1)
        mPrevAcquireFence = fence_close(mPrevAcquireFence, mDisplay, FENCE_TYPE_SRC_ACQUIRE,
                                        FENCE_IP_UNDEFINED);
}

/**
 * @return float
 */
float ExynosLayer::checkFps(bool increaseCount) {
    uint32_t frameDiff;
    mFrameCount += increaseCount ? 1 : 0;

    nsecs_t now = systemTime();
    if (mLastFpsTime == 0) { // Initialize values
        mLastFpsTime = now;
        mNextLastFpsTime = now;
        // TODO(b/268474771): set the initial FPS to the correct peak refresh rate
        mFps = 120;
        return mFps;
    }

    nsecs_t diff = now - mNextLastFpsTime;
    // Update mLastFrameCount for every 5s, to ensure that FPS calculation is only based on
    // frames in the past at most 10s.
    if (diff >= kLayerFpsStableTimeNs) {
        mLastFrameCount = mNextLastFrameCount;
        mNextLastFrameCount = mFrameCount;

        mLastFpsTime = mNextLastFpsTime;
        mNextLastFpsTime = now;
    }

    bool wasLowFps = (mFps < LOW_FPS_THRESHOLD) ? true : false;

    if (mFrameCount >= mLastFrameCount)
        frameDiff = (mFrameCount - mLastFrameCount);
    else
        frameDiff = (mFrameCount + (UINT_MAX - mLastFrameCount));

    diff = now - mLastFpsTime;
    mFps = (frameDiff * float(s2ns(1))) / diff;

    bool nowLowFps = (mFps < LOW_FPS_THRESHOLD) ? true : false;

    if ((mDisplay->mDisplayControl.handleLowFpsLayers) &&
        (wasLowFps != nowLowFps))
        setGeometryChanged(GEOMETRY_LAYER_FPS_CHANGED);

    return mFps;
}

/**
 * @return float
 */
float ExynosLayer::getFps() {
    return mFps;
}

int32_t ExynosLayer::doPreProcess()
{
    overlay_priority priority = ePriorityLow;
    mIsHdrLayer = false;
    mBufferHasMetaParcel = false;
    mLayerFlag = 0x0;

    mPreprocessedInfo.preProcessed = false;
    mPreprocessedInfo.sourceCrop = mSourceCrop;
    mPreprocessedInfo.displayFrame = mDisplayFrame;
    mPreprocessedInfo.interlacedType = V4L2_FIELD_NONE;
    mPreprocessedInfo.sdrDimRatio = mBrightness;

    if (mCompositionType == HWC2_COMPOSITION_SOLID_COLOR) {
        mLayerFlag |= EXYNOS_HWC_DIM_LAYER;
    } else {
        mLayerFlag &= ~(EXYNOS_HWC_DIM_LAYER);
    }

    if (mLayerBuffer == NULL) {
        if (mOverlayPriority != priority)
            setGeometryChanged(GEOMETRY_LAYER_PRIORITY_CHANGED);

        mOverlayPriority = priority;
        return NO_ERROR;
    }

    VendorGraphicBufferMeta gmeta(mLayerBuffer);

    mPreprocessedInfo.mUsePrivateFormat = false;
    mPreprocessedInfo.mPrivateFormat = gmeta.format;

    if (isFormatYUV(gmeta.format)) {
        mPreprocessedInfo.sourceCrop.top = (int)mSourceCrop.top;
        mPreprocessedInfo.sourceCrop.left = (int)mSourceCrop.left;
        mPreprocessedInfo.sourceCrop.bottom = (int)(mSourceCrop.bottom + 0.9);
        mPreprocessedInfo.sourceCrop.right = (int)(mSourceCrop.right + 0.9);
        mPreprocessedInfo.preProcessed = true;
    }

    if (isFormatYUV(gmeta.format)) {

        ExynosVideoMeta *metaData = NULL;
        int priv_fd = -1;

        if (gmeta.flags & VendorGraphicBufferMeta::PRIV_FLAGS_USES_2PRIVATE_DATA)
            priv_fd = gmeta.fd1;
        else if (gmeta.flags & VendorGraphicBufferMeta::PRIV_FLAGS_USES_3PRIVATE_DATA)
            priv_fd = gmeta.fd2;

        if (priv_fd >= 0) {

            metaData = (ExynosVideoMeta*)mmap(0, sizeof(ExynosVideoMeta), PROT_READ|PROT_WRITE, MAP_SHARED, priv_fd, 0);

            if (metaData == NULL) {
                HWC_LOGE(mDisplay, "Layer's metadata is NULL!!");
            } else if (metaData == MAP_FAILED) {
                HWC_LOGE(mDisplay, "Layer's metadata map failed!!");
            } else {
                mBufferHasMetaParcel = true;
                if ((metaData->eType & VIDEO_INFO_TYPE_HDR_STATIC) ||
                        (metaData->eType & VIDEO_INFO_TYPE_HDR_DYNAMIC)) {
                    if (allocMetaParcel() == NO_ERROR) {
                        mMetaParcel->eType = metaData->eType;
                        if (metaData->eType & VIDEO_INFO_TYPE_HDR_STATIC) {
                            mMetaParcel->sHdrStaticInfo = metaData->sHdrStaticInfo;
                            HDEBUGLOGD(eDebugLayer, "HWC2: Static metadata min(%d), max(%d)",
                                    mMetaParcel->sHdrStaticInfo.sType1.mMinDisplayLuminance,
                                    mMetaParcel->sHdrStaticInfo.sType1.mMaxDisplayLuminance);
                        }
                        if (metaData->eType & VIDEO_INFO_TYPE_HDR_DYNAMIC) {
                            /* Reserved field for dynamic meta data */
                            /* Currently It's not be used not only HWC but also OMX */
                            mMetaParcel->sHdrDynamicInfo = metaData->sHdrDynamicInfo;
                            HDEBUGLOGD(eDebugLayer, "HWC2: Layer has dynamic metadata");
                        }
                    }
                }
                if (metaData->eType & VIDEO_INFO_TYPE_INTERLACED) {
                    mPreprocessedInfo.interlacedType = metaData->data.dec.nInterlacedType;
                    if (mPreprocessedInfo.interlacedType == V4L2_FIELD_INTERLACED_BT) {
                        if ((int)mSourceCrop.left < (int)(gmeta.stride)) {
                            mPreprocessedInfo.sourceCrop.left = (int)mSourceCrop.left + gmeta.stride;
                            mPreprocessedInfo.sourceCrop.right = (int)mSourceCrop.right + gmeta.stride;
                        }
                    }
                    if (mPreprocessedInfo.interlacedType == V4L2_FIELD_INTERLACED_TB ||
                            mPreprocessedInfo.interlacedType == V4L2_FIELD_INTERLACED_BT) {
                        mPreprocessedInfo.sourceCrop.top = (int)(mSourceCrop.top)/2;
                        mPreprocessedInfo.sourceCrop.bottom = (int)(mSourceCrop.bottom)/2;
                    }
                }
                if (metaData->eType & VIDEO_INFO_TYPE_CHECK_PIXEL_FORMAT) {
                    mPreprocessedInfo.mUsePrivateFormat = true;
                    mPreprocessedInfo.mPrivateFormat = metaData->nPixelFormat;
                }
                munmap(metaData, sizeof(ExynosVideoMeta));
            }
        }
        mPreprocessedInfo.preProcessed = true;
    }

    exynos_image src_img;
    exynos_image dst_img;
    setSrcExynosImage(&src_img);
    setDstExynosImage(&dst_img);
    ExynosMPP *exynosMPPVG = nullptr;
    if (isFormatYUV(gmeta.format)) {
        auto otfMPPs = ExynosResourceManager::getOtfMPPs();
        auto mpp_it = std::find_if(otfMPPs.begin(), otfMPPs.end(),
                [&src_img](auto m) { return m->isSrcFormatSupported(src_img); });
        exynosMPPVG = mpp_it == otfMPPs.end() ? nullptr : *mpp_it;
    }

    /* Set HDR Flag */
    if(hasHdrInfo(src_img)) mIsHdrLayer = true;

    if (isFormatYUV(gmeta.format) && exynosMPPVG) {
        /*
         * layer's sourceCrop should be aligned
         */
        uint32_t srcCropXAlign = exynosMPPVG->getSrcXOffsetAlign(src_img);
        uint32_t srcCropYAlign = exynosMPPVG->getSrcYOffsetAlign(src_img);
        uint32_t srcCropWidthAlign = exynosMPPVG->getSrcWidthAlign(src_img);
        uint32_t srcCropHeightAlign = exynosMPPVG->getSrcHeightAlign(src_img);
        mPreprocessedInfo.sourceCrop.left = pixel_align((int)mPreprocessedInfo.sourceCrop.left, srcCropXAlign);
        mPreprocessedInfo.sourceCrop.top = pixel_align((int)mPreprocessedInfo.sourceCrop.top, srcCropYAlign);
        mPreprocessedInfo.sourceCrop.right = mPreprocessedInfo.sourceCrop.left +
            pixel_align_down(WIDTH(mPreprocessedInfo.sourceCrop), srcCropWidthAlign);
        mPreprocessedInfo.sourceCrop.bottom = mPreprocessedInfo.sourceCrop.top +
            pixel_align_down(HEIGHT(mPreprocessedInfo.sourceCrop), srcCropHeightAlign);
        mPreprocessedInfo.preProcessed = true;
    }

    if (exynosMPPVG && ((getDrmMode(mLayerBuffer) != NO_DRM) ||
        (mIsHdrLayer == true)))
    {
        if ((mDisplay->mDisplayControl.adjustDisplayFrame == true) &&
            ((mSupportedMPPFlag & (MPP_LOGICAL_DPP_G | MPP_LOGICAL_DPP_VG | MPP_LOGICAL_DPP_VGFS | MPP_LOGICAL_DPP_VGRFS)) == 0))
        {
            /*
             * M2mMPP should be used for DRM, HDR video
             * layer's displayFrame is the source of DPP
             */
            uint32_t cropWidthAlign = exynosMPPVG->getSrcCropWidthAlign(src_img);
            uint32_t cropHeightAlign = exynosMPPVG->getSrcCropHeightAlign(src_img);

            mPreprocessedInfo.displayFrame.right = mDisplayFrame.left +
                pixel_align(WIDTH(mDisplayFrame), cropWidthAlign);
            mPreprocessedInfo.displayFrame.bottom = mDisplayFrame.top +
                pixel_align(HEIGHT(mDisplayFrame), cropHeightAlign);

            if (mPreprocessedInfo.displayFrame.right > (int)(mDisplay->mXres)) {
                mPreprocessedInfo.displayFrame.left = mDisplay->mXres -
                    pixel_align(WIDTH(mPreprocessedInfo.displayFrame), cropWidthAlign);
                mPreprocessedInfo.displayFrame.right = mDisplay->mXres;
            }

            if (mPreprocessedInfo.displayFrame.bottom > (int)(mDisplay->mYres)) {
                mPreprocessedInfo.displayFrame.top = mDisplay->mYres -
                    pixel_align_down(HEIGHT(mPreprocessedInfo.displayFrame), cropHeightAlign);
                mPreprocessedInfo.displayFrame.bottom = mDisplay->mYres;
            }
        }

        uint32_t minDstWidth = exynosMPPVG->getDstMinWidth(dst_img);
        uint32_t minDstHeight = exynosMPPVG->getDstMinHeight(dst_img);
        if ((uint32_t)WIDTH(mDisplayFrame) < minDstWidth) {
            ALOGI("%s DRM layer displayFrame width %d is smaller than otf minWidth %d",
                    mDisplay->mDisplayName.c_str(),
                    WIDTH(mDisplayFrame), minDstWidth);
            mPreprocessedInfo.displayFrame.right = mDisplayFrame.left +
                pixel_align(WIDTH(mDisplayFrame), minDstWidth);

            if (mPreprocessedInfo.displayFrame.right > (int)(mDisplay->mXres)) {
                mPreprocessedInfo.displayFrame.left = mDisplay->mXres -
                    pixel_align(WIDTH(mPreprocessedInfo.displayFrame), minDstWidth);
                mPreprocessedInfo.displayFrame.right = mDisplay->mXres;
            }
        }
        if ((uint32_t)HEIGHT(mDisplayFrame) < minDstHeight) {
            ALOGI("%s DRM layer displayFrame height %d is smaller than vpp minHeight %d",
                    mDisplay->mDisplayName.c_str(),
                    HEIGHT(mDisplayFrame), minDstHeight);
            mPreprocessedInfo.displayFrame.bottom = mDisplayFrame.top +
                pixel_align(HEIGHT(mDisplayFrame), minDstHeight);

            if (mPreprocessedInfo.displayFrame.bottom > (int)(mDisplay->mYres)) {
                mPreprocessedInfo.displayFrame.top = mDisplay->mYres -
                    pixel_align(HEIGHT(mPreprocessedInfo.displayFrame), minDstHeight);
                mPreprocessedInfo.displayFrame.bottom = mDisplay->mYres;
            }
        }
        mPreprocessedInfo.preProcessed = true;
    }

    if (VendorGraphicBufferMeta::get_usage(mLayerBuffer) &
               toUnderlying(AidlBufferUsage::FRONT_BUFFER)) {
        priority = ePriorityMax;
    } else if (getDrmMode(mLayerBuffer) != NO_DRM) {
        priority = ePriorityMax;
    } else if (mIsHdrLayer) {
        if (isFormatRgb(gmeta.format))
            priority = ePriorityMax;
        else
            priority = ePriorityHigh;
    } else if (isFormatYUV(gmeta.format)) {
        priority = ePriorityHigh;
    } else if ((mDisplay->mDisplayControl.cursorSupport == true) &&
               (mCompositionType == HWC2_COMPOSITION_CURSOR)) {
        priority = ePriorityMid;
    } else {
        priority = ePriorityLow;
    }

    if (mOverlayPriority != priority)
        setGeometryChanged(GEOMETRY_LAYER_PRIORITY_CHANGED);

    mOverlayPriority = priority;

    return NO_ERROR;
}

int32_t ExynosLayer::setCursorPosition(int32_t x, int32_t y) {
    return mDisplay->setCursorPositionAsync(x, y);
}


int32_t ExynosLayer::setLayerBuffer(buffer_handle_t buffer, int32_t acquireFence) {

    /* TODO : Exception here ? */
    //TODO mGeometryChanged  here

    uint64_t internal_format = 0;

    if (mDisplay->mPlugState == false)
        buffer = NULL;

    if (buffer != NULL) {
        if (VendorGraphicBufferMeta::get_fd(buffer,0) < 0)
            return HWC2_ERROR_BAD_LAYER;
    }

    VendorGraphicBufferMeta gmeta(buffer);
    internal_format = gmeta.format;

    if ((mLayerBuffer == NULL) || (buffer == NULL))
        setGeometryChanged(GEOMETRY_LAYER_UNKNOWN_CHANGED);
    else {
        if (getDrmMode(VendorGraphicBufferMeta::get_producer_usage(mLayerBuffer)) != getDrmMode(gmeta.producer_usage))
            setGeometryChanged(GEOMETRY_LAYER_DRM_CHANGED);
        if (VendorGraphicBufferMeta::get_format(mLayerBuffer) != gmeta.format)
            setGeometryChanged(GEOMETRY_LAYER_FORMAT_CHANGED);
        if ((VendorGraphicBufferMeta::get_usage(mLayerBuffer) &
                    toUnderlying(AidlBufferUsage::FRONT_BUFFER)) !=
                (VendorGraphicBufferMeta::get_usage(buffer) &
                    toUnderlying(AidlBufferUsage::FRONT_BUFFER)))
            setGeometryChanged(GEOMETRY_LAYER_FRONT_BUFFER_USAGE_CHANGED);
    }

    {
        Mutex::Autolock lock(mDisplay->mDRMutex);
        mLayerBuffer = buffer;
        checkFps(mLastLayerBuffer != mLayerBuffer);
        if (mLayerBuffer != mLastLayerBuffer) {
            mLastUpdateTime = systemTime(CLOCK_MONOTONIC);
            if (mRequestedCompositionType != HWC2_COMPOSITION_REFRESH_RATE_INDICATOR)
                mDisplay->mBufferUpdates++;
        }
    }
    mPrevAcquireFence =
            fence_close(mPrevAcquireFence, mDisplay, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_UNDEFINED);
    mAcquireFence = fence_close(mAcquireFence, mDisplay, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_UNDEFINED);

    mAcquireFence = hwcCheckFenceDebug(mDisplay, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_LAYER, acquireFence);
    mPrevAcquireFence = hwcCheckFenceDebug(mDisplay, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_LAYER,
                                           hwc_dup(mAcquireFence, mDisplay, FENCE_TYPE_SRC_ACQUIRE,
                                                   FENCE_IP_LAYER, true));
    if (mReleaseFence >= 0)
        HWC_LOGE(NULL, "Layer's release fence is not initialized");
    mReleaseFence = -1;
#ifdef DISABLE_FENCE
    if (mAcquireFence >= 0)
        fence_close(mAcquireFence);
    mAcquireFence = -1;
#endif

    /* Set Compression Information from GraphicBuffer */
    uint32_t prevCompressionType = mCompressionInfo.type;
    mCompressionInfo = getCompressionInfo(mLayerBuffer);
    if (mCompressionInfo.type != prevCompressionType)
        setGeometryChanged(GEOMETRY_LAYER_COMPRESSED_CHANGED);

    if (buffer != NULL) {
        /*
         * HAL_DATASPACE_V0_JFIF = HAL_DATASPACE_STANDARD_BT601_625 |
         * HAL_DATASPACE_TRANSFER_SMPTE_170M | HAL_DATASPACE_RANGE_FULL,
         */
        if (gmeta.format == HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_FULL)
            setLayerDataspace(HAL_DATASPACE_V0_JFIF);
    } else {
        setLayerDataspace(HAL_DATASPACE_UNKNOWN);
    }

    HDEBUGLOGD(eDebugFence,
               "layers bufferHandle: %p, mDataSpace: 0x%8x, acquireFence: %d, compressionType: "
               "0x%x, internal_format: 0x%" PRIx64 "",
               mLayerBuffer, mDataSpace, mAcquireFence, mCompressionInfo.type, internal_format);

    return 0;
}


int32_t ExynosLayer::setLayerSurfaceDamage(hwc_region_t damage) {

    mDamageNum = damage.numRects;
    mDamageRects.clear();

    if (mDamageNum == 0) return 0;

    for (size_t i = 0; i<mDamageNum; i++){
        mDamageRects.push_back(damage.rects[i]);
    }

    return 0;
}

int32_t ExynosLayer::setLayerBlendMode(int32_t /*hwc2_blend_mode_t*/ mode) {

    //TODO mGeometryChanged  here
    if (mode < 0)
        return HWC2_ERROR_BAD_PARAMETER;
    if (mBlending != mode)
        setGeometryChanged(GEOMETRY_LAYER_BLEND_CHANGED);
    mBlending = mode;
    return HWC2_ERROR_NONE;
}


int32_t ExynosLayer::setLayerColor(hwc_color_t color) {
    /* TODO : Implementation here */
    mColor = color;
    return 0;
}

int32_t ExynosLayer::setLayerCompositionType(int32_t /*hwc2_composition_t*/ type) {

    if (type < 0)
        return HWC2_ERROR_BAD_PARAMETER;

    // FIXME: HWC2_COMPOSITION_SCREENSHOT is not defined in AOSP
    //        HWC guys should fix this.
#if 0
    if (mDisplay->mType == HWC_DISPLAY_PRIMARY)
        if (type == HWC2_COMPOSITION_SCREENSHOT)
            type = HWC2_COMPOSITION_DEVICE;
#endif

    if (type != mCompositionType) {
        setGeometryChanged(GEOMETRY_LAYER_TYPE_CHANGED);
    }

    mCompositionType = type;
    mRequestedCompositionType = type;

    return HWC2_ERROR_NONE;
}

int32_t ExynosLayer::setLayerDataspace(int32_t /*android_dataspace_t*/ dataspace) {
    android_dataspace currentDataSpace = (android_dataspace_t)dataspace;
    if ((mLayerBuffer != NULL) && (VendorGraphicBufferMeta::get_format(mLayerBuffer) == HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_FULL))
        currentDataSpace = HAL_DATASPACE_V0_JFIF;
    else {
        /* Change legacy dataspace */
        switch (dataspace) {
        case HAL_DATASPACE_SRGB_LINEAR:
            currentDataSpace = HAL_DATASPACE_V0_SRGB_LINEAR;
            break;
        case HAL_DATASPACE_SRGB:
            currentDataSpace = HAL_DATASPACE_V0_SRGB;
            break;
        case HAL_DATASPACE_JFIF:
            currentDataSpace = HAL_DATASPACE_V0_JFIF;
            break;
        case HAL_DATASPACE_BT601_625:
            currentDataSpace = HAL_DATASPACE_V0_BT601_625;
            break;
        case HAL_DATASPACE_BT601_525:
            currentDataSpace = HAL_DATASPACE_V0_BT601_525;
            break;
        case HAL_DATASPACE_BT709:
            currentDataSpace = HAL_DATASPACE_V0_BT709;
            break;
        default:
            currentDataSpace = (android_dataspace)dataspace;
            break;
        }
    }

    if (currentDataSpace != mDataSpace) {
        setGeometryChanged(GEOMETRY_LAYER_DATASPACE_CHANGED);
        // invalidate metadata if dataspace is changed. need metadata update
        // to be after dataspace update.
        if (mMetaParcel != nullptr) {
            mMetaParcel->eType = VIDEO_INFO_TYPE_INVALID;
        }
    }
    mDataSpace = currentDataSpace;

    return HWC2_ERROR_NONE;
}

int32_t ExynosLayer::setLayerDisplayFrame(hwc_rect_t frame) {

    if ((frame.left != mDisplayFrame.left) ||
        (frame.top != mDisplayFrame.top) ||
        (frame.right != mDisplayFrame.right) ||
        (frame.bottom != mDisplayFrame.bottom))
        setGeometryChanged(GEOMETRY_LAYER_DISPLAYFRAME_CHANGED);
    mDisplayFrame = frame;

    return HWC2_ERROR_NONE;
}

int32_t ExynosLayer::setLayerPlaneAlpha(float alpha) {
    if (alpha < 0.0f || alpha > 1.0f) {
        ALOGE("%s: invalid alpha %f", __func__, alpha);
        return HWC2_ERROR_BAD_PARAMETER;
    }

    if ((mPlaneAlpha != alpha) && ((mPlaneAlpha == 0.0) || (alpha == 0.0)))
        setGeometryChanged(GEOMETRY_LAYER_IGNORE_CHANGED);

    mPlaneAlpha = alpha;

    if (mPlaneAlpha > 0.0)
        mLayerFlag &= ~(EXYNOS_HWC_IGNORE_LAYER);
    else
        mLayerFlag |= EXYNOS_HWC_IGNORE_LAYER;

    return HWC2_ERROR_NONE;
}

int32_t ExynosLayer::setLayerSidebandStream(const native_handle_t* __unused stream) {
    return HWC2_ERROR_NONE;
}

int32_t ExynosLayer::setLayerSourceCrop(hwc_frect_t crop) {
    if ((crop.left != mSourceCrop.left) ||
        (crop.top != mSourceCrop.top) ||
        (crop.right != mSourceCrop.right) ||
        (crop.bottom != mSourceCrop.bottom)) {
        setGeometryChanged(GEOMETRY_LAYER_SOURCECROP_CHANGED);
        mSourceCrop = crop;
    }

    return HWC2_ERROR_NONE;
}

int32_t ExynosLayer::setLayerTransform(int32_t /*hwc_transform_t*/ transform) {

    if (mTransform != transform) {
        setGeometryChanged(GEOMETRY_LAYER_TRANSFORM_CHANGED);
        mTransform = transform;
    }

    return HWC2_ERROR_NONE;
}

int32_t ExynosLayer::setLayerVisibleRegion(hwc_region_t visible) {

    mVisibleRegionScreen = visible;

    return HWC2_ERROR_NONE;
}

int32_t ExynosLayer::setLayerZOrder(uint32_t z) {
    if (mZOrder != z) {
        setGeometryChanged(GEOMETRY_LAYER_ZORDER_CHANGED);
        mZOrder = z;
    }
    return HWC2_ERROR_NONE;
}

int32_t ExynosLayer::setLayerPerFrameMetadata(uint32_t numElements,
        const int32_t* /*hw2_per_frame_metadata_key_t*/ keys, const float* metadata)
{
    if (allocMetaParcel() != NO_ERROR)
        return -1;
    unsigned int multipliedVal = 50000;
    mMetaParcel->eType =
        static_cast<ExynosVideoInfoType>(mMetaParcel->eType | VIDEO_INFO_TYPE_HDR_STATIC);
    for (uint32_t i = 0; i < numElements; i++) {
        HDEBUGLOGD(eDebugLayer, "HWC2: setLayerPerFrameMetadata key(%d), value(%7.5f)",
                keys[i], metadata[i]);
        switch (keys[i]) {
            case HWC2_DISPLAY_RED_PRIMARY_X:
                mMetaParcel->sHdrStaticInfo.sType1.mR.x =
                    (unsigned int)(metadata[i] * multipliedVal);
                break;
            case HWC2_DISPLAY_RED_PRIMARY_Y:
                mMetaParcel->sHdrStaticInfo.sType1.mR.y =
                    (unsigned int)(metadata[i] * multipliedVal);
                break;
            case HWC2_DISPLAY_GREEN_PRIMARY_X:
                mMetaParcel->sHdrStaticInfo.sType1.mG.x =
                    (unsigned int)(metadata[i] * multipliedVal);
                break;
            case HWC2_DISPLAY_GREEN_PRIMARY_Y:
                mMetaParcel->sHdrStaticInfo.sType1.mG.y =
                    (unsigned int)(metadata[i] * multipliedVal);
                break;
            case HWC2_DISPLAY_BLUE_PRIMARY_X:
                mMetaParcel->sHdrStaticInfo.sType1.mB.x =
                    (unsigned int)(metadata[i] * multipliedVal);
                break;
            case HWC2_DISPLAY_BLUE_PRIMARY_Y:
                mMetaParcel->sHdrStaticInfo.sType1.mB.y =
                    (unsigned int)(metadata[i] * multipliedVal);
                break;
            case HWC2_WHITE_POINT_X:
                mMetaParcel->sHdrStaticInfo.sType1.mW.x =
                    (unsigned int)(metadata[i] * multipliedVal);
                break;
            case HWC2_WHITE_POINT_Y:
                mMetaParcel->sHdrStaticInfo.sType1.mW.y =
                    (unsigned int)(metadata[i] * multipliedVal);
                break;
            case HWC2_MAX_LUMINANCE:
                mMetaParcel->sHdrStaticInfo.sType1.mMaxDisplayLuminance =
                    (unsigned int)(metadata[i] * 10000);
                break;
            case HWC2_MIN_LUMINANCE:
                mMetaParcel->sHdrStaticInfo.sType1.mMinDisplayLuminance =
                    (unsigned int)(metadata[i] * 10000);
                break;
            case HWC2_MAX_CONTENT_LIGHT_LEVEL:
                /* Should be checked */
                mMetaParcel->sHdrStaticInfo.sType1.mMaxContentLightLevel =
                    (unsigned int)(metadata[i]);
                break;
            case HWC2_MAX_FRAME_AVERAGE_LIGHT_LEVEL:
                /* Should be checked */
                mMetaParcel->sHdrStaticInfo.sType1.mMaxFrameAverageLightLevel =
                    (unsigned int)(metadata[i]);
                break;
            default:
                return HWC2_ERROR_UNSUPPORTED;
        }
    }
    return NO_ERROR;
}

int32_t ExynosLayer::setLayerPerFrameMetadataBlobs(uint32_t numElements, const int32_t* keys, const uint32_t* sizes,
        const uint8_t* metadata)
{
    const uint8_t *metadata_start = metadata;
    for (uint32_t i = 0; i < numElements; i++) {
        HDEBUGLOGD(eDebugLayer, "HWC2: setLayerPerFrameMetadataBlobs key(%d)", keys[i]);
        switch (keys[i]) {
        case HWC2_HDR10_PLUS_SEI:
            if (allocMetaParcel() == NO_ERROR) {
                mMetaParcel->eType =
                    static_cast<ExynosVideoInfoType>(mMetaParcel->eType | VIDEO_INFO_TYPE_HDR_DYNAMIC);
                ExynosHdrDynamicInfo *info = &(mMetaParcel->sHdrDynamicInfo);
                Exynos_parsing_user_data_registered_itu_t_t35(info, (void*)metadata_start,
                                                              sizes[i]);
            } else {
                ALOGE("Layer has no metaParcel!");
                return HWC2_ERROR_UNSUPPORTED;
            }
            break;
        default:
            return HWC2_ERROR_BAD_PARAMETER;
        }
        metadata_start += sizes[i];
    }
    return HWC2_ERROR_NONE;
}

int32_t ExynosLayer::setLayerColorTransform(const float* matrix)
{
    mLayerColorTransform.enable = true;
    for (uint32_t i = 0; i < TRANSFORM_MAT_SIZE; i++)
    {
        mLayerColorTransform.mat[i] = matrix[i];
    }

    return 0;
}

int32_t ExynosLayer::setLayerGenericMetadata(hwc2_layer_t __unused layer,
        uint32_t __unused keyLength, const char* __unused key,
        bool __unused mandatory, uint32_t __unused valueLength, const uint8_t* __unused value)
{
    return HWC2_ERROR_UNSUPPORTED;
}

int32_t ExynosLayer::setLayerBrightness(float brightness) {
    if (mDisplay->mType == HWC_DISPLAY_EXTERNAL && mDisplay->mBrightnessController == nullptr) {
        if (brightness == 1.0f) {
            return HWC2_ERROR_NONE;
        } else {
            HWC_LOGE(mDisplay, "[ExternalDisplay layer] setLayerBrightness != 1.0");
            return HWC2_ERROR_BAD_PARAMETER;
        }
    }

    if (mDisplay->mBrightnessController == nullptr ||
        !mDisplay->mBrightnessController->validateLayerBrightness(brightness)) {
        return HWC2_ERROR_BAD_PARAMETER;
    }

    if (mBrightness != brightness) {
        // Trigger display validation in case client composition is needed.
        setGeometryChanged(GEOMETRY_LAYER_WHITEPOINT_CHANGED);
        mBrightness = brightness;
    }
    return HWC2_ERROR_NONE;
}

int32_t ExynosLayer::setLayerBlockingRegion(const std::vector<hwc_rect_t>& blockingRegion) {
    hwc_rect_t maxRect;

    for (auto rect : blockingRegion) {
        maxRect = std::max(maxRect, rect, [](const hwc_rect_t& lhs, const hwc_rect_t& rhs) {
            return rectSize(lhs) < rectSize(rhs);
        });
    }

    mBlockingRect = maxRect;

    return HWC2_ERROR_NONE;
}

void ExynosLayer::resetValidateData()
{
    mValidateCompositionType = HWC2_COMPOSITION_INVALID;
    mOtfMPP = NULL;
    mM2mMPP = NULL;
    mOverlayInfo = 0x0;
    mWindowIndex = 0;
}

int32_t ExynosLayer::setSrcExynosImage(exynos_image *src_img)
{
    buffer_handle_t handle = mLayerBuffer;
    if (isDimLayer()) {
        src_img->format = HAL_PIXEL_FORMAT_RGBA_8888;
        src_img->usageFlags = 0xb00;
        src_img->bufferHandle = 0;

        src_img->x = 0;
        src_img->y = 0;

        if (mDisplay != NULL) {
            src_img->fullWidth = src_img->w = mDisplay->mXres;
            src_img->fullHeight = src_img->h = mDisplay->mYres;
        } else {
            src_img->fullWidth = src_img->w = 1440;
            src_img->fullHeight = src_img->h = 2560;
        }

        src_img->layerFlags = mLayerFlag;
        src_img->acquireFenceFd = mAcquireFence;
        src_img->releaseFenceFd = -1;
        src_img->dataSpace = HAL_DATASPACE_V0_SRGB;
        src_img->blending = mBlending;
        src_img->transform = mTransform;
        src_img->compressionInfo = mCompressionInfo;
        src_img->planeAlpha = mPlaneAlpha;
        src_img->zOrder = mZOrder;


        return NO_ERROR;
    }

    if (handle == NULL) {
        src_img->fullWidth = 0;
        src_img->fullHeight = 0;
        src_img->format = 0;
        src_img->usageFlags = 0x0;
        src_img->bufferHandle = handle;
    } else {
        VendorGraphicBufferMeta gmeta(handle);

        if ((mPreprocessedInfo.interlacedType == V4L2_FIELD_INTERLACED_TB) ||
            (mPreprocessedInfo.interlacedType == V4L2_FIELD_INTERLACED_BT))
        {
            src_img->fullWidth = (gmeta.stride * 2);
            src_img->fullHeight = pixel_align_down((gmeta.vstride / 2), 2);
        } else {
            src_img->fullWidth = gmeta.stride;
            // The BW VDEC will generate AFBC streams based on the initial requested height
            // instead of the adjusted vstride from gralloc.
            src_img->fullHeight = (isAFBC32x8(mCompressionInfo) &&
                                   (gmeta.producer_usage & VendorGraphicBufferUsage::BW))
                    ? gmeta.height
                    : gmeta.vstride;
        }
        if (!mPreprocessedInfo.mUsePrivateFormat)
            src_img->format = gmeta.format;
        else
            src_img->format = mPreprocessedInfo.mPrivateFormat;
        src_img->usageFlags = gmeta.producer_usage;
        src_img->bufferHandle = handle;
    }
    src_img->x = (int)mPreprocessedInfo.sourceCrop.left;
    src_img->y = (int)mPreprocessedInfo.sourceCrop.top;
    src_img->w = (int)mPreprocessedInfo.sourceCrop.right - (int)mPreprocessedInfo.sourceCrop.left;
    src_img->h = (int)mPreprocessedInfo.sourceCrop.bottom - (int)mPreprocessedInfo.sourceCrop.top;
    if ((mPreprocessedInfo.interlacedType == V4L2_FIELD_INTERLACED_TB) ||
        (mPreprocessedInfo.interlacedType == V4L2_FIELD_INTERLACED_BT))
    {
        while ((src_img->h % 2 != 0) ||
               (src_img->h > src_img->fullHeight)) {
            src_img->h -= 1;
        }
    }
    src_img->layerFlags = mLayerFlag;
    src_img->acquireFenceFd = mAcquireFence;
    src_img->releaseFenceFd = -1;

    src_img->dataSpace = mDataSpace;
    if(src_img->dataSpace == HAL_DATASPACE_UNKNOWN)
        src_img->dataSpace = HAL_DATASPACE_V0_SRGB;

    src_img->blending = mBlending;
    src_img->transform = mTransform;
    src_img->compressionInfo = mCompressionInfo;
    src_img->planeAlpha = mPlaneAlpha;
    src_img->zOrder = mZOrder;
    /* Copy HDR metadata */
    memset(&(src_img->metaParcel), 0, sizeof(src_img->metaParcel));
    src_img->metaType = VIDEO_INFO_TYPE_INVALID;
    if (mMetaParcel != NULL) {
        memcpy(&(src_img->metaParcel), mMetaParcel, sizeof(src_img->metaParcel));
        src_img->metaType = mMetaParcel->eType;
        src_img->hasMetaParcel = true;
    } else {
        src_img->hasMetaParcel = false;
    }

    src_img->needColorTransform = mLayerColorTransform.enable;
    src_img->needPreblending = mNeedPreblending;

    return NO_ERROR;
}

int32_t ExynosLayer::setDstExynosImage(exynos_image *dst_img)
{
    buffer_handle_t handle = mLayerBuffer;

    if (handle == NULL) {
        dst_img->usageFlags = 0x0;
    } else {
        dst_img->usageFlags = VendorGraphicBufferMeta::get_producer_usage(handle);
    }

    if (isDimLayer()) {
        dst_img->usageFlags = 0xb00;
    }

    dst_img->format = DEFAULT_MPP_DST_FORMAT;
    dst_img->x = mPreprocessedInfo.displayFrame.left;
    dst_img->y = mPreprocessedInfo.displayFrame.top;
    dst_img->w = (mPreprocessedInfo.displayFrame.right - mPreprocessedInfo.displayFrame.left);
    dst_img->h = (mPreprocessedInfo.displayFrame.bottom - mPreprocessedInfo.displayFrame.top);
    dst_img->layerFlags = mLayerFlag;
    dst_img->acquireFenceFd = -1;
    dst_img->releaseFenceFd = -1;
    dst_img->bufferHandle = NULL;
    dst_img->dataSpace = HAL_DATASPACE_UNKNOWN;
    if (mDisplay != NULL) {
        dst_img->fullWidth = mDisplay->mXres;
        dst_img->fullHeight = mDisplay->mYres;
        if (mDisplay->mColorMode != HAL_COLOR_MODE_NATIVE) {
            dst_img->dataSpace = colorModeToDataspace(mDisplay->mColorMode);
        } else {
            if (hasHdrInfo(mDataSpace)) {
                android_dataspace hdrDataSpace =
                    (android_dataspace)(HAL_DATASPACE_STANDARD_DCI_P3 | HAL_DATASPACE_TRANSFER_GAMMA2_2 | HAL_DATASPACE_RANGE_LIMITED);
                if (mDisplay->mType == HWC_DISPLAY_EXTERNAL) {
                    ExynosExternalDisplay *externalDisplay = (ExynosExternalDisplay*)mDisplay;
                    if (externalDisplay->mExternalHdrSupported == true)
                        dst_img->dataSpace = HAL_DATASPACE_UNKNOWN;
                    else
                        dst_img->dataSpace = hdrDataSpace;
                } else {
                    dst_img->dataSpace = hdrDataSpace;
                }
            }
        }
    } else {
        HWC_LOGE(NULL, "mDisplay is NULL");
    }
    dst_img->blending = mBlending;
    dst_img->transform = 0;
    dst_img->compressionInfo.type = COMP_TYPE_NONE;
    dst_img->planeAlpha = mPlaneAlpha;
    dst_img->zOrder = mZOrder;

    /* Copy HDR metadata */
    memset(&(dst_img->metaParcel), 0, sizeof(dst_img->metaParcel));
    dst_img->metaType = VIDEO_INFO_TYPE_INVALID;
    if (mMetaParcel != NULL) {
        memcpy(&(dst_img->metaParcel), mMetaParcel, sizeof(dst_img->metaParcel));
        dst_img->metaType = mMetaParcel->eType;
        dst_img->hasMetaParcel = true;
    } else {
        dst_img->hasMetaParcel = false;
    }

    return NO_ERROR;
}

int32_t ExynosLayer::resetAssignedResource()
{
    int32_t ret = NO_ERROR;
    if (mM2mMPP != NULL) {
        HDEBUGLOGD(eDebugResourceManager, "\t\t %s mpp is reset", mM2mMPP->mName.c_str());
        mM2mMPP->resetAssignedState(this);
        mM2mMPP = NULL;
    }
    if (mOtfMPP != NULL) {
        HDEBUGLOGD(eDebugResourceManager, "\t\t %s mpp is reset", mOtfMPP->mName.c_str());
        mOtfMPP->resetAssignedState();
        mOtfMPP = NULL;
    }
    return ret;
}

bool ExynosLayer::checkBtsCap(const uint32_t bts_refresh_rate) {
    if (mOtfMPP == nullptr) return true;

    exynos_image src_img;
    exynos_image dst_img;
    setSrcExynosImage(&src_img);
    setDstExynosImage(&dst_img);
    if (mOtfMPP->checkSpecificRestriction(bts_refresh_rate, src_img, dst_img)) {
        return false;
    }

    const bool isPerpendicular = !!(src_img.transform & HAL_TRANSFORM_ROT_90);
    const uint32_t srcWidth = isPerpendicular ? src_img.h : src_img.w;
    const uint32_t srcHeight = isPerpendicular ? src_img.w : src_img.h;
    const bool scaleDown = (srcWidth > dst_img.w || srcHeight > dst_img.h);

    if (!scaleDown) return true;

    const float resolution = float(src_img.w) * float(src_img.h) * bts_refresh_rate / 1000;

    return mOtfMPP->checkDownscaleCap(resolution, float(dst_img.h) / float(mDisplay->mYres));
}

void ExynosLayer::setSrcAcquireFence() {
    if (mAcquireFence == -1 && mPrevAcquireFence != -1) {
        mAcquireFence = hwcCheckFenceDebug(mDisplay, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_LAYER,
                                           hwc_dup(mPrevAcquireFence, mDisplay,
                                                   FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_LAYER));
    } else if (mAcquireFence != -1) {
        setFenceInfo(mAcquireFence, mDisplay, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_LAYER,
                     HwcFenceDirection::FROM);
    }
}

void ExynosLayer::dump(String8& result)
{
    int format = HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED;
    int32_t fd, fd1, fd2;
    uint64_t unique_id;
    if (mLayerBuffer != NULL)
    {
        VendorGraphicBufferMeta gmeta(mLayerBuffer);
        format = gmeta.format;
        fd = gmeta.fd;
        fd1 = gmeta.fd1;
        fd2 = gmeta.fd2;
        unique_id = gmeta.unique_id;
    } else {
        format = HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED;
        fd = -1;
        fd1 = -1;
        fd2 = -1;
        unique_id = 0;
    }

    {
        TableBuilder tb;
        tb.add("zOrder", mZOrder)
          .add("priority", mOverlayPriority);
        if (mCompositionType == HWC2_COMPOSITION_SOLID_COLOR) {
            tb.add("color", std::vector<uint64_t>({mColor.r, mColor.g, mColor.b, mColor.a}), true);
        } else {
            tb.add("handle", mLayerBuffer)
                    .add("fd", std::vector<int>({fd, fd1, fd2}))
                    .add("compression", getCompressionStr(mCompressionInfo).c_str());
        }
        tb.add("format", getFormatStr(format, mCompressionInfo.type).c_str())
                .add("dataSpace", mDataSpace, true)
                .add("colorTr", mLayerColorTransform.enable)
                .add("blend", mBlending, true)
                .add("planeAlpha", mPlaneAlpha)
                .add("fps", mFps);
        result.append(tb.build().c_str());
    }

    result.append(TableBuilder()
                          .add("sourceCrop",
                               std::vector<double>({mPreprocessedInfo.sourceCrop.left,
                                                    mPreprocessedInfo.sourceCrop.top,
                                                    mPreprocessedInfo.sourceCrop.right,
                                                    mPreprocessedInfo.sourceCrop.bottom}))
                          .add("dispFrame",
                               std::vector<int>({mPreprocessedInfo.displayFrame.left,
                                                 mPreprocessedInfo.displayFrame.top,
                                                 mPreprocessedInfo.displayFrame.right,
                                                 mPreprocessedInfo.displayFrame.bottom}))
                          .add("blockRect",
                               std::vector<int>({mBlockingRect.left, mBlockingRect.top,
                                                 mBlockingRect.right, mBlockingRect.bottom}))
                          .add("tr", mTransform, true)
                          .add("windowIndex", mWindowIndex)
                          .add("type", mCompositionType)
                          .add("exynosType", mExynosCompositionType)
                          .add("validateType", mValidateCompositionType)
                          .add("overlayInfo", mOverlayInfo, true)
                          .add("GrallocBufferId", unique_id)
                          .build()
                          .c_str());

    result.append(TableBuilder()
                          .add("MPPFlag", mSupportedMPPFlag, true)
                          .add("dim ratio", mPreprocessedInfo.sdrDimRatio)
                          .build()
                          .c_str());

    if ((mDisplay != NULL) && (mDisplay->mResourceManager != NULL)) {
        result.appendFormat("MPPFlags for otfMPP\n");
        for (uint32_t i = 0; i < mDisplay->mResourceManager->getOtfMPPSize(); i++) {
            result.appendFormat("[%s: 0x%" PRIx64 "] ", mDisplay->mResourceManager->getOtfMPP(i)->mName.c_str(),
                    mCheckMPPFlag[mDisplay->mResourceManager->getOtfMPP(i)->mLogicalType]);
        }
        result.appendFormat("\n");
        result.appendFormat("MPPFlags for m2mMPP\n");
        for (uint32_t i = 0; i < mDisplay->mResourceManager->getM2mMPPSize(); i++) {
            result.appendFormat("[%s: 0x%" PRIx64 "] ", mDisplay->mResourceManager->getM2mMPP(i)->mName.c_str(),
                    mCheckMPPFlag[mDisplay->mResourceManager->getM2mMPP(i)->mLogicalType]);
            if ((i!=0) && (i%4==0)) result.appendFormat("\n");
        }
        result.appendFormat("\n");
    }
    result.appendFormat("acquireFence: %d\n", mAcquireFence);
    if ((mOtfMPP == NULL) && (mM2mMPP == NULL))
        result.appendFormat("\tresource is not assigned.\n");
    if (mOtfMPP != NULL)
        result.appendFormat("\tassignedMPP: %s\n", mOtfMPP->mName.c_str());
    if (mM2mMPP != NULL)
        result.appendFormat("\tassignedM2mMPP: %s\n", mM2mMPP->mName.c_str());
    result.appendFormat("\tdump midImg\n");
    dumpExynosImage(result, mMidImg);

}

void ExynosLayer::printLayer()
{
    int format = HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED;
    int32_t fd, fd1, fd2;
    String8 result;
    if (mLayerBuffer != NULL)
    {
        VendorGraphicBufferMeta gmeta(mLayerBuffer);
        format = gmeta.format;
        fd = gmeta.fd;
        fd1 = gmeta.fd1;
        fd2 = gmeta.fd2;
    } else {
        format = HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED;
        fd = -1;
        fd1 = -1;
        fd2 = -1;
    }
    result.appendFormat("handle: %p [fd: %d, %d, %d], acquireFence: %d, tr: 0x%2x, compression: "
                        "%s, dataSpace: 0x%8x, format: %s\n",
                        mLayerBuffer, fd, fd1, fd2, mAcquireFence, mTransform,
                        getCompressionStr(mCompressionInfo).c_str(), mDataSpace,
                        getFormatStr(format, mCompressionInfo.type).c_str());
    result.appendFormat("\tblend: 0x%4x, planeAlpha: %3.1f, zOrder: %d, color[0x%2x, 0x%2x, 0x%2x, 0x%2x]\n",
            mBlending, mPlaneAlpha, mZOrder, mColor.r, mColor.g, mColor.b, mColor.a);
    result.appendFormat("\tfps: %.2f, priority: %d, windowIndex: %d\n", mFps, mOverlayPriority,
                        mWindowIndex);
    result.appendFormat("\tsourceCrop[%7.1f,%7.1f,%7.1f,%7.1f], dispFrame[%5d,%5d,%5d,%5d]\n",
            mSourceCrop.left, mSourceCrop.top, mSourceCrop.right, mSourceCrop.bottom,
            mDisplayFrame.left, mDisplayFrame.top, mDisplayFrame.right, mDisplayFrame.bottom);
    result.appendFormat("\ttype: %2d, exynosType: %2d, validateType: %2d\n",
            mCompositionType, mExynosCompositionType, mValidateCompositionType);
    result.appendFormat("\toverlayInfo: 0x%8x, supportedMPPFlag: 0x%8x, geometryChanged: 0x%" PRIx64 "\n",
            mOverlayInfo, mSupportedMPPFlag, mGeometryChanged);

    if ((mDisplay != NULL) && (mDisplay->mResourceManager != NULL)) {
        result.appendFormat("MPPFlags for otfMPP\n");
        for (uint32_t i = 0; i < mDisplay->mResourceManager->getOtfMPPSize(); i++) {
            result.appendFormat("[%s: 0x%" PRIx64 "] ", mDisplay->mResourceManager->getOtfMPP(i)->mName.c_str(),
                    mCheckMPPFlag[mDisplay->mResourceManager->getOtfMPP(i)->mLogicalType]);
        }
        result.appendFormat("\n");
        result.appendFormat("MPPFlags for m2mMPP\n");
        for (uint32_t i = 0; i < mDisplay->mResourceManager->getM2mMPPSize(); i++) {
            result.appendFormat("[%s: 0x%" PRIx64 "] ", mDisplay->mResourceManager->getM2mMPP(i)->mName.c_str(),
                    mCheckMPPFlag[mDisplay->mResourceManager->getM2mMPP(i)->mLogicalType]);
            if ((i!=0) && (i%4==0)) result.appendFormat("\n");
        }
        result.appendFormat("\n");
    }

    ALOGD("%s", result.c_str());
    result.clear();

    if ((mOtfMPP == NULL) && (mM2mMPP == NULL))
        ALOGD("\tresource is not assigned.");
    if (mOtfMPP != NULL)
        ALOGD("\tassignedMPP: %s", mOtfMPP->mName.c_str());
    if (mM2mMPP != NULL)
        ALOGD("\tassignedM2mMPP: %s", mM2mMPP->mName.c_str());
    ALOGD("\t++ dump midImg ++");
    dumpExynosImage(result, mMidImg);
    ALOGD("%s", result.c_str());

}

void ExynosLayer::setGeometryChanged(uint64_t changedBit)
{
    mLastUpdateTime = systemTime(CLOCK_MONOTONIC);
    mGeometryChanged |= changedBit;
    if (mRequestedCompositionType != HWC2_COMPOSITION_REFRESH_RATE_INDICATOR)
        mDisplay->setGeometryChanged(changedBit);
}

int ExynosLayer::allocMetaParcel()
{
    /* Already allocated */
    if ((mMetaParcelFd >= 0) &&
        (mMetaParcel != NULL))
        return NO_ERROR;

    if (mMetaParcelFd < 0) {
         int ionFd = exynos_ion_open();
         if (ionFd >= 0) {
             mMetaParcelFd = exynos_ion_alloc(ionFd, sizeof(ExynosVideoMeta), EXYNOS_ION_HEAP_SYSTEM_MASK, 0);
             if (mMetaParcelFd < 0) {
                 ALOGE("Failed to ion alloc for metadata parcel");
                 return -1;
             }
             exynos_ion_close(ionFd);
         } else {
             ALOGE("Failed to open ion fd");
             return -1;
         }
    }

    mMetaParcel =
        (ExynosVideoMeta*)mmap(0, sizeof(ExynosVideoMeta), PROT_READ|PROT_WRITE, MAP_SHARED, mMetaParcelFd, 0);
    if (mMetaParcel == NULL) {
        ALOGE("Failed to map metadata parcel");
        return -1;
    }

    return NO_ERROR;
}

bool ExynosLayer::isDimLayer()
{
    if (mLayerFlag & EXYNOS_HWC_DIM_LAYER)
        return true;
    return false;
}
