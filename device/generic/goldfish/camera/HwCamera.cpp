/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <hardware/camera3.h>
#include <ui/GraphicBufferMapper.h>

#include "debug.h"
#include "HwCamera.h"
#include "jpeg.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace hw {

using base::unique_fd;

namespace {
constexpr int64_t kOneSecondNs = 1000000000;

constexpr float kDefaultAperture = 4.0;
constexpr float kDefaultFocalLength = 1.0;
constexpr int32_t kDefaultSensorSensitivity = 100;

constexpr char kClass[] = "HwCamera";
}  // namespace

int64_t HwCamera::getFrameDuration(const camera_metadata_t* const metadata,
                                   const int64_t def,
                                   const int64_t min,
                                   const int64_t max) {
    camera_metadata_ro_entry_t entry;
    camera_metadata_enum_android_control_ae_mode ae_mode;

    if (find_camera_metadata_ro_entry(metadata, ANDROID_CONTROL_AE_MODE, &entry)) {
        ae_mode = ANDROID_CONTROL_AE_MODE_OFF;
    } else {
        ae_mode = camera_metadata_enum_android_control_ae_mode(entry.data.i32[0]);
    }

    if (ae_mode == ANDROID_CONTROL_AE_MODE_OFF) {
        if (find_camera_metadata_ro_entry(metadata, ANDROID_SENSOR_FRAME_DURATION, &entry)) {
            return def;
        } else {
            return std::max(std::min(entry.data.i64[0], max), min);
        }
    } else {
        if (find_camera_metadata_ro_entry(metadata, ANDROID_CONTROL_AE_TARGET_FPS_RANGE, &entry)) {
            return def;
        } else {
            const int fps = (entry.data.i32[0] + entry.data.i32[1]) / 2;
            if (fps > 0) {
                return std::max(std::min(kOneSecondNs / fps, max), min);
            }  else {
                return def;
            }
        }
    }
}

camera_metadata_enum_android_lens_state_t
HwCamera::getAfLensState(const camera_metadata_enum_android_control_af_state_t state) {
    switch (state) {
    default:
        ALOGW("%s:%s:%d unexpected AF state=%d", kClass, __func__, __LINE__, state);
        [[fallthrough]];

    case ANDROID_CONTROL_AF_STATE_INACTIVE:
    case ANDROID_CONTROL_AF_STATE_PASSIVE_SCAN:
    case ANDROID_CONTROL_AF_STATE_PASSIVE_FOCUSED:
    case ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED:
    case ANDROID_CONTROL_AF_STATE_NOT_FOCUSED_LOCKED:
    case ANDROID_CONTROL_AF_STATE_PASSIVE_UNFOCUSED:
        return ANDROID_LENS_STATE_STATIONARY;

    case ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN:
        return ANDROID_LENS_STATE_MOVING;
    }
}

bool HwCamera::compressJpeg(const Rect<uint16_t> imageSize,
                            const android_ycbcr& imageYcbcr,
                            const CameraMetadata& metadata,
                            const native_handle_t* jpegBuffer,
                            const size_t jpegBufferSize) {
    GraphicBufferMapper& gbm = GraphicBufferMapper::get();

    void* jpegData = nullptr;
    if (gbm.lock(jpegBuffer, static_cast<uint32_t>(BufferUsage::CPU_WRITE_OFTEN),
                 {static_cast<int32_t>(jpegBufferSize), 1}, &jpegData) != NO_ERROR) {
        return FAILURE(false);
    }

    const size_t jpegImageDataCapacity = jpegBufferSize - sizeof(struct camera3_jpeg_blob);
    const size_t compressedSize = jpeg::compressYUV(imageYcbcr, imageSize, metadata,
                                                    jpegData, jpegImageDataCapacity);

    LOG_ALWAYS_FATAL_IF(gbm.unlock(jpegBuffer) != NO_ERROR);

    const bool success = (compressedSize > 0);
    if (success) {
        struct camera3_jpeg_blob blob;
        blob.jpeg_blob_id = CAMERA3_JPEG_BLOB_ID;
        blob.jpeg_size = compressedSize;
        memcpy(static_cast<uint8_t*>(jpegData) + jpegImageDataCapacity,
               &blob, sizeof(blob));
    }

    return success;
}

bool HwCamera::convertRGBAtoRAW16(const Rect<uint16_t> imageSize,
                                  const void* rgba,
                                  const native_handle_t* raw16Buffer) {
    if ((imageSize.width & 1) || (imageSize.height & 1)) {
        /*
         * This format assumes
         * - an even width
         * - an even height
        */
        return FAILURE(false);
    }

    void* raw16 = nullptr;
    if (GraphicBufferMapper::get().lock(
            raw16Buffer, static_cast<uint32_t>(BufferUsage::CPU_WRITE_OFTEN),
            {imageSize.width, imageSize.height}, &raw16) != NO_ERROR) {
        return FAILURE(false);
    }

    const unsigned height = imageSize.height;
    const unsigned rgbaWidth = imageSize.width;
    const unsigned rgbaWidth2 = rgbaWidth / 2;  // we will process two RGBAs at once

    /*
     * This format assumes
     * - a horizontal stride multiple of 16 pixels
     * - strides are specified in pixels, not in bytes
    */
    const unsigned rawrawAlign2 = (((rgbaWidth + 15U) & ~15U) - rgbaWidth) / 2;

    const uint64_t* rgbargbaPtr = static_cast<const uint64_t*>(rgba);
    uint32_t* rawraw = static_cast<uint32_t*>(raw16);

#define TRANSFORM10(V8) (8U + ((V8) * 16410U) >> 12)
#define RAWRAW(LO, HI) (TRANSFORM10(LO) | (TRANSFORM10(HI) << 16))

    for (unsigned row = 0; row < height; row += 2) {
#define RGBARGBA_TO_R16G16(RGBARGBA) RAWRAW((RGBARGBA & 0xFF), ((RGBARGBA >> 40) & 0xFF))
        for (unsigned n = rgbaWidth2 % 8; n > 0; --n, ++rgbargbaPtr, ++rawraw) {  // the RG loop
            const uint64_t rgbargba = *rgbargbaPtr;
            *rawraw = RGBARGBA_TO_R16G16(rgbargba);
        }
        for (unsigned n = rgbaWidth2 / 8; n > 0; --n, rgbargbaPtr += 8, rawraw += 8) {  // the RG loop
            const uint64_t rgbargba0 = rgbargbaPtr[0];
            const uint64_t rgbargba1 = rgbargbaPtr[1];
            const uint64_t rgbargba2 = rgbargbaPtr[2];
            const uint64_t rgbargba3 = rgbargbaPtr[3];
            const uint64_t rgbargba4 = rgbargbaPtr[4];
            const uint64_t rgbargba5 = rgbargbaPtr[5];
            const uint64_t rgbargba6 = rgbargbaPtr[6];
            const uint64_t rgbargba7 = rgbargbaPtr[7];

            rawraw[0] = RGBARGBA_TO_R16G16(rgbargba0);
            rawraw[1] = RGBARGBA_TO_R16G16(rgbargba1);
            rawraw[2] = RGBARGBA_TO_R16G16(rgbargba2);
            rawraw[3] = RGBARGBA_TO_R16G16(rgbargba3);
            rawraw[4] = RGBARGBA_TO_R16G16(rgbargba4);
            rawraw[5] = RGBARGBA_TO_R16G16(rgbargba5);
            rawraw[6] = RGBARGBA_TO_R16G16(rgbargba6);
            rawraw[7] = RGBARGBA_TO_R16G16(rgbargba7);
        }
#undef RGBARGBA_TO_R16G16
        rawraw += rawrawAlign2;

#define RGBARGBA_TO_G16B16(RGBARGBA) RAWRAW(((RGBARGBA >> 8) & 0xFF), ((RGBARGBA >> 48) & 0xFF))
        for (unsigned n = rgbaWidth2 % 8; n > 0; --n, ++rgbargbaPtr, ++rawraw) {  // the GB loop
            const uint64_t rgbargba = *rgbargbaPtr;
            *rawraw = RGBARGBA_TO_G16B16(rgbargba);
        }
        for (unsigned n = rgbaWidth2 / 8; n > 0; --n, rgbargbaPtr += 8, rawraw += 8) {  // the GB loop
            const uint64_t rgbargba0 = rgbargbaPtr[0];
            const uint64_t rgbargba1 = rgbargbaPtr[1];
            const uint64_t rgbargba2 = rgbargbaPtr[2];
            const uint64_t rgbargba3 = rgbargbaPtr[3];
            const uint64_t rgbargba4 = rgbargbaPtr[4];
            const uint64_t rgbargba5 = rgbargbaPtr[5];
            const uint64_t rgbargba6 = rgbargbaPtr[6];
            const uint64_t rgbargba7 = rgbargbaPtr[7];

            rawraw[0] = RGBARGBA_TO_G16B16(rgbargba0);
            rawraw[1] = RGBARGBA_TO_G16B16(rgbargba1);
            rawraw[2] = RGBARGBA_TO_G16B16(rgbargba2);
            rawraw[3] = RGBARGBA_TO_G16B16(rgbargba3);
            rawraw[4] = RGBARGBA_TO_G16B16(rgbargba4);
            rawraw[5] = RGBARGBA_TO_G16B16(rgbargba5);
            rawraw[6] = RGBARGBA_TO_G16B16(rgbargba6);
            rawraw[7] = RGBARGBA_TO_G16B16(rgbargba7);
        }
#undef RGBARGBA_TO_G16B16
        rawraw += rawrawAlign2;
    }

#undef RAWRAW
#undef TRANSFORM10

    LOG_ALWAYS_FATAL_IF(GraphicBufferMapper::get().unlock(raw16Buffer) != NO_ERROR);
    return true;
}

std::tuple<int32_t, int32_t, int32_t, int32_t> HwCamera::getAeCompensationRange() const {
    return {-6, 6, 1, 2}; // range=[-6, +6], step=1/2
}

std::pair<float, float> HwCamera::getZoomRatioRange() const {
    return {1.0, 1.0};
}

std::pair<int, int> HwCamera::getSupportedFlashStrength() const {
    return {0, 0};
}

int32_t HwCamera::getJpegMaxSize() const {
    const Rect<uint16_t> size = getSensorSize();
    return int32_t(size.width) * int32_t(size.height) + sizeof(camera3_jpeg_blob);
}

Span<const float> HwCamera::getAvailableApertures() const {
    static const float availableApertures[] = {
        kDefaultAperture
    };

    return availableApertures;
}

Span<const float> HwCamera::getAvailableFocalLength() const {
    static const float availableFocalLengths[] = {
        kDefaultFocalLength
    };

    return availableFocalLengths;
}

float HwCamera::getHyperfocalDistance() const {
    return 0.1;
}

float HwCamera::getMinimumFocusDistance() const {
    return 0.1;
}

int32_t HwCamera::getPipelineMaxDepth() const {
    return 4;
}

uint32_t HwCamera::getAvailableCapabilitiesBitmap() const {
    return
        (1U << ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) |
        (1U << ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS);
}

float HwCamera::getMaxDigitalZoom() const {
    return 1.0;
}

int64_t HwCamera::getStallFrameDurationNs() const {
    return 250000000LL;
}

int32_t HwCamera::getSensorOrientation() const {
    return 90;
}

float HwCamera::getSensorDPI() const {
    return 500.0;
}

std::pair<int32_t, int32_t> HwCamera::getSensorSensitivityRange() const {
    return {kDefaultSensorSensitivity / 4, kDefaultSensorSensitivity * 8};
}

float HwCamera::getDefaultAperture() const {
    return kDefaultAperture;
}

float HwCamera::getDefaultFocalLength() const {
    return kDefaultFocalLength;
}

int32_t HwCamera::getDefaultSensorSensitivity() const {
    return kDefaultSensorSensitivity;
}

}  // namespace hw
}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
