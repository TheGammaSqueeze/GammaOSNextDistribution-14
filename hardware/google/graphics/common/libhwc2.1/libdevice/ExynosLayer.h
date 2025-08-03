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

#ifndef _EXYNOSLAYER_H
#define _EXYNOSLAYER_H

#include <aidl/android/hardware/graphics/composer3/Composition.h>
#include <hardware/hwcomposer2.h>
#include <log/log.h>
#include <system/graphics.h>
#include <utils/Timers.h>

#include <array>
#include <unordered_map>

#include "ExynosDisplay.h"
#include "ExynosHWC.h"
#include "ExynosHWCHelper.h"
#include "VendorGraphicBuffer.h"
#include "VendorVideoAPI.h"

#ifndef HWC2_HDR10_PLUS_SEI
/* based on android.hardware.composer.2_3 */
#define HWC2_HDR10_PLUS_SEI 12
#endif

using namespace android;
using namespace vendor::graphics;
using ::aidl::android::hardware::graphics::composer3::Composition;

constexpr nsecs_t kLayerFpsStableTimeNs = s2ns(5);

class ExynosMPP;

enum overlay_priority {
    ePriorityNone,
    /* Normal layer */
    ePriorityLow,
    /* Assign resource before normal layers */
    ePriorityMid,
    /*
     * Overlay is better than client composition,
     * Displayed screen can be abnormal if the layer is composited by client
     */
    ePriorityHigh,
    /*
     * Overlay is mandatory,
     * Black screen will be displayed if the layer is composited by client
     */
    ePriorityMax
};

typedef struct pre_processed_layer_info
{
    bool preProcessed;
    hwc_frect_t sourceCrop;
    hwc_rect_t displayFrame;
    int interlacedType;
    float sdrDimRatio;
    /* SBWC exception */
    bool mUsePrivateFormat = false;
    uint32_t mPrivateFormat = 0;
} pre_processed_layer_info_t;

enum {
    HWC2_COMPOSITION_DISPLAY_DECORATION = toUnderlying(Composition::DISPLAY_DECORATION),
    HWC2_COMPOSITION_REFRESH_RATE_INDICATOR = toUnderlying(Composition::REFRESH_RATE_INDICATOR),
    /*add after hwc2_composition_t, margin number here*/
    HWC2_COMPOSITION_EXYNOS = 32,
};

class ExynosLayer : public ExynosMPPSource {
    public:

        ExynosLayer(ExynosDisplay* display);
        virtual ~ExynosLayer();

        ExynosDisplay* mDisplay;

        /**
         * Layer's compositionType
         *
         * If acceptDisplayChanges() is called, it will be set to the validated type
         * since SF may update their state and doesn't call back into HWC
         */
        int32_t mCompositionType;

        /**
         * Composition type that is originally requested by SF only using setLayerComposisionType()
         *
         * It will not be changed if applyDisplayChanges() is called.
         */
        int32_t mRequestedCompositionType;

        /**
         * Composition type that is used by HAL
         * (ex: COMPOSITION_G2D)
         */
        int32_t mExynosCompositionType;

        /**
         * Validated compositionType
         */
        int32_t mValidateCompositionType;

        /**
         * The last validated composition type
         */
        int32_t mPrevValidateCompositionType;

        /**
         * Validated ExynosCompositionType
         */
        int32_t mValidateExynosCompositionType;

        uint32_t mOverlayInfo;

        /**
         * Layer supported information for each MPP type (bit setting)
         * (= Restriction check, out format will be set as RGB for temporary
         * If mGeometryChanged is true, It will be rearranged in ExynosDisplay::validateDisplay()
         * This infor will be used for supported infomation at resource arranging time
         */
        uint32_t mSupportedMPPFlag;

        /**
         * TODO : Should be defined..
         */
        /* Key is logical type of MPP */
        std::unordered_map<uint32_t, uint64_t> mCheckMPPFlag;

        /**
         * Update rate for using client composition.
         */
        float mFps;

        /**
         * Assign priority, when priority changing is needded by order infomation in mGeometryChanged
         */
        overlay_priority mOverlayPriority;

        /**
         * This will be set when property changed except buffer update case.
         */
        uint64_t mGeometryChanged;

        /**
         * Layer's window index
         */
        uint32_t mWindowIndex;

        /**
         * Source buffer's compression information
         */
        CompressionInfo mCompressionInfo;

        /**
         * Acquire fence
         */
        int32_t mAcquireFence;
        int32_t mPrevAcquireFence;

        /**
         * Release fence
         */
        int32_t mReleaseFence;

        uint32_t mFrameCount;
        uint32_t mLastFrameCount;
        nsecs_t mLastFpsTime;
        uint32_t mNextLastFrameCount;
        nsecs_t mNextLastFpsTime;

        /**
         * Previous buffer's handle
         */
        buffer_handle_t mLastLayerBuffer;

        /**
         * Display buffer handle
         */
        buffer_handle_t mLayerBuffer;

        nsecs_t mLastUpdateTime;

        /**
         * Surface Damage
         */
        size_t mDamageNum;
        android::Vector <hwc_rect_t> mDamageRects;

        /**
         * Blending type
         */
        int32_t mBlending; /* hwc2_blend_mode_t */

        /**
         * Display Frame
         */
        hwc_rect_t mDisplayFrame;

        /**
         * Pland alpha
         */
        float mPlaneAlpha;

        /**
         * Source Crop
         */
        hwc_frect_t mSourceCrop;

        /**
         * Transform
         */
        int32_t mTransform; /*hwc_transform_t*/

        /**
         * Visible region
         */
        hwc_region_t mVisibleRegionScreen;

        /**
         *
         */
        hwc_rect_t mBlockingRect;

        /**
         * Z-Order
         */
        uint32_t mZOrder;

        /**
         * Color
         */
        hwc_color_t mColor;

        /** Data Space
         */
        android_dataspace mDataSpace; // android_dataspace_t

        pre_processed_layer_info mPreprocessedInfo;

        /**
         * layer brightness, normalized to current display brightness
         */
        float mBrightness = 1.0;

        /**
         * user defined flag
         */
        int32_t mLayerFlag;

        /**
         * HDR flags
         */
        bool mIsHdrLayer;
        bool mBufferHasMetaParcel;
        int mMetaParcelFd;

        /**
         * color transform info
         */
        struct LayerColorTransform {
            bool enable = false;
            std::array<float, TRANSFORM_MAT_SIZE> mat;
        } mLayerColorTransform;

        /**
         * @param type
         */
        int32_t setCompositionType(int32_t type);

        float checkFps(bool increaseCount);

        float getFps();

        int32_t doPreProcess();

        /* setCursorPosition(..., x, y)
         * Descriptor: HWC2_FUNCTION_SET_CURSOR_POSITION
         * HWC2_PFN_SET_CURSOR_POSITION
         */
        virtual int32_t setCursorPosition(int32_t x, int32_t y);

        /* setLayerBuffer(..., buffer, acquireFence)
         * Descriptor: HWC2_FUNCTION_SET_LAYER_BUFFER
         * HWC2_PFN_SET_LAYER_BUFFER
         */
        virtual int32_t setLayerBuffer(buffer_handle_t buffer, int32_t acquireFence);

        /* setLayerSurfaceDamage(..., damage)
         * Descriptor: HWC2_FUNCTION_SET_LAYER_SURFACE_DAMAGE
         * HWC2_PFN_SET_LAYER_SURFACE_DAMAGE
         */
        virtual int32_t setLayerSurfaceDamage(hwc_region_t damage);

        /*
         * Layer State Functions
         *
         * These functions modify the state of a given layer. They do not take effect
         * until the display configuration is successfully validated with
         * validateDisplay and the display contents are presented with presentDisplay.
         *
         * All of these functions take as their first three parameters a device pointer,
         * a display handle for the display which contains the layer, and a layer
         * handle, so these parameters are omitted from the described parameter lists.
         */

        /* setLayerBlendMode(..., mode)
         * Descriptor: HWC2_FUNCTION_SET_LAYER_BLEND_MODE
         * HWC2_PFN_SET_LAYER_BLEND_MODE
         */
        virtual int32_t setLayerBlendMode(int32_t /*hwc2_blend_mode_t*/ mode);

        /* setLayerColor(..., color)
         * Descriptor: HWC2_FUNCTION_SET_LAYER_COLOR
         * HWC2_PFN_SET_LAYER_COLOR
         */
        virtual int32_t setLayerColor(hwc_color_t color);

        /* setLayerCompositionType(..., type)
         * Descriptor: HWC2_FUNCTION_SET_LAYER_COMPOSITION_TYPE
         * HWC2_PFN_SET_LAYER_COMPOSITION_TYPE
         */
        virtual int32_t setLayerCompositionType(
                int32_t /*hwc2_composition_t*/ type);

        /* setLayerDataspace(..., dataspace)
         * Descriptor: HWC2_FUNCTION_SET_LAYER_DATASPACE
         * HWC2_PFN_SET_LAYER_DATASPACE
         */
        virtual int32_t setLayerDataspace(int32_t /*android_dataspace_t*/ dataspace);

        /* setLayerDisplayFrame(..., frame)
         * Descriptor: HWC2_FUNCTION_SET_LAYER_DISPLAY_FRAME
         * HWC2_PFN_SET_LAYER_DISPLAY_FRAME
         */
        virtual int32_t setLayerDisplayFrame(hwc_rect_t frame);

        /* setLayerPlaneAlpha(..., alpha)
         * Descriptor: HWC2_FUNCTION_SET_LAYER_PLANE_ALPHA
         * HWC2_PFN_SET_LAYER_PLANE_ALPHA
         */
        virtual int32_t setLayerPlaneAlpha(float alpha);

        /* setLayerSidebandStream(..., stream)
         * Descriptor: HWC2_FUNCTION_SET_LAYER_SIDEBAND_STREAM
         * HWC2_PFN_SET_LAYER_SIDEBAND_STREAM
         */
        virtual int32_t setLayerSidebandStream(const native_handle_t* stream);

        /* setLayerSourceCrop(..., crop)
         * Descriptor: HWC2_FUNCTION_SET_LAYER_SOURCE_CROP
         * HWC2_PFN_SET_LAYER_SOURCE_CROP
         */
        virtual int32_t setLayerSourceCrop(hwc_frect_t crop);

        /* setLayerTransform(..., transform)
         * Descriptor: HWC2_FUNCTION_SET_LAYER_TRANSFORM
         * HWC2_PFN_SET_LAYER_TRANSFORM
         */
        virtual int32_t setLayerTransform(int32_t /*hwc_transform_t*/ transform);

        /* setLayerVisibleRegion(..., visible)
         * Descriptor: HWC2_FUNCTION_SET_LAYER_VISIBLE_REGION
         * HWC2_PFN_SET_LAYER_VISIBLE_REGION
         */
        virtual int32_t setLayerVisibleRegion(hwc_region_t visible);

        /* setLayerZOrder(..., z)
         * Descriptor: HWC2_FUNCTION_SET_LAYER_Z_ORDER
         * HWC2_PFN_SET_LAYER_Z_ORDER
         */
        virtual int32_t setLayerZOrder(uint32_t z);

        virtual int32_t setLayerPerFrameMetadata(uint32_t numElements,
                const int32_t* /*hw2_per_frame_metadata_key_t*/ keys, const float* metadata);

        /* setLayerPerFrameMetadataBlobs(...,numElements, keys, sizes, blobs)
         * Descriptor: HWC2_FUNCTION_SET_LAYER_PER_FRAME_METADATA_BLOBS
         * Parameters:
         *   numElements is the number of elements in each of the keys, sizes, and
         *   metadata arrays
         *   keys is a pointer to an array of keys.  Current valid keys are those listed
         *   above as valid blob type keys.
         *   sizes is a pointer to an array of unsigned ints specifying the sizes of
         *   each metadata blob
         *   metadata is a pointer to a blob of data holding all blobs contiguously in
         *   memory
         *
         *   Returns HWC2_ERROR_NONE or one of the following erros:
         *     HWC2_ERROR_BAD_DISPLAY - an invalid display handle was passed in
         *     HWC2_ERROR_BAD_PARAMETER - sizes of keys and metadata parameters does
         *     not match numElements, numElements < 0, or keys contains a
         *     non-valid key (see above for current valid blob type keys).
         *     HWC2_ERROR_UNSUPPORTED - metadata is not supported on this display
         */
        int32_t setLayerPerFrameMetadataBlobs(uint32_t numElements, const int32_t* keys, const uint32_t* sizes,
                const uint8_t* metadata);

        int32_t setLayerColorTransform(const float* matrix);
        /* setLayerGenericMetadata(..., keyLength, key, mandatory, valueLength, value)
         * Descriptor: HWC2_FUNCTION_SET_LAYER_GENERIC_METADATA
         * Optional for HWC2 devices for composer 2.4+
         *
         * setLayerGenericMetadata sets a piece of generic metadata for the given layer.
         * If this function is called twice with the same key but different values, the
         * newer value must override the older one. Calling this function with
         * valueLength == 0 must reset that key's metadata as if it had not been set.
         *
         * A given piece of metadata may either be mandatory or a hint (non-mandatory)
         * as indicated by the `mandatory` parameter. Mandatory metadata may affect the
         * composition result, which is to say that it may cause a visible change in the
         * final image. By contrast, hints may only affect the composition strategy,
         * such as which layers are composited by the client, but must not cause a
         * visible change in the final image.
         *
         * This implies that if the device does not understand a given key:
         * - If the key is marked as mandatory, it must mark this layer for client
         *   composition in order to ensure the correct composition result
         * - If the key is a hint, the metadata provided may be ignored
         *
         * Parameters:
         *   keyLength - the length of the key parameter
         *   key - the metadata key
         *   mandatory - indicates whether this particular key represents mandatory
         *       metadata or a hint, as described above
         *   valueLength - the length of the value parameter
         *   value - the metadata value
         *
         * Returns HWC2_ERROR_NONE or one of the following errors:
         *   HWC2_ERROR_BAD_DISPLAY - an invalid display handle was passed in
         *   HWC2_ERROR_BAD_LAYER - an invalid layer handle was passed in
         *   HWC2_ERROR_BAD_PARAMETER - an unsupported key was passed in, or the value
         *       does not conform to the expected format for the key
         */
        int32_t setLayerGenericMetadata(hwc2_layer_t __unused layer,
                uint32_t __unused keyLength, const char* __unused key,
                bool __unused mandatory, uint32_t __unused valueLength, const uint8_t* __unused value);

        /**
         * setLayerBrightness(float brightness);
         *
         * Sets the desired brightness for the layer. This is intended to be used for instance when
         * presenting an SDR layer alongside HDR content. The HDR content will be presented at the
         * display brightness in nits, and accordingly SDR content shall be dimmed according to the
         * provided brightness ratio.
         *
         * @param brightness normalized to current display brightness.
         */
        int32_t setLayerBrightness(float brightness);

        /**
         * Specifies a region of the layer that is transparent and may be skipped
         * by the DPU, e.g. using a blocking region, in order to save power. This
         * is only a hint, so the composition of the layer must look the same
         * whether or not this region is skipped.
         *
         * The region is in screen space and must not exceed the dimensions of
         * the screen.
         */
        int32_t setLayerBlockingRegion(const std::vector<hwc_rect_t>& blockingRegion);

        void resetValidateData();
        virtual void dump(String8& result);
        void printLayer();
        int32_t setSrcExynosImage(exynos_image *src_img);
        int32_t setDstExynosImage(exynos_image *dst_img);
        int32_t resetAssignedResource();
        bool checkBtsCap(const uint32_t btsRefreshRate);

        void setSrcAcquireFence();

        bool isDrm() {return ((mLayerBuffer != NULL) && (getDrmMode(mLayerBuffer) != NO_DRM));};
        bool isLayerFormatRgb() {
            return ((mLayerBuffer != NULL) &&
                    isFormatRgb(VendorGraphicBufferMeta::get_internal_format(mLayerBuffer)));
        }
        bool isLayerFormatYuv() {
            return ((mLayerBuffer != NULL) &&
                    isFormatYUV(VendorGraphicBufferMeta::get_internal_format(mLayerBuffer)));
        }
        bool isLayerHasAlphaChannel() {
            return ((mLayerBuffer != NULL) &&
                    formatHasAlphaChannel(
                            VendorGraphicBufferMeta::get_internal_format(mLayerBuffer)));
        }
        bool isLayerOpaque() {
            return (!isLayerHasAlphaChannel() &&
                    std::fabs(mPlaneAlpha - 1.0f) <= std::numeric_limits<float>::epsilon());
        }
        bool needClearClientTarget() {
            return (mOverlayPriority >= ePriorityHigh && isLayerOpaque());
        }
        size_t getDisplayFrameArea() { return HEIGHT(mDisplayFrame) * WIDTH(mDisplayFrame); }
        void setGeometryChanged(uint64_t changedBit);
        void clearGeometryChanged() {mGeometryChanged = 0;};
        bool isDimLayer();
        const ExynosVideoMeta* getMetaParcel() { return mMetaParcel; };

    private:
        ExynosVideoMeta *mMetaParcel;
        int allocMetaParcel();
};

#endif //_EXYNOSLAYER_H
