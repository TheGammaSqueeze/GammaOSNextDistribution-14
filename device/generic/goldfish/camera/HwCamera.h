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

#pragma once

#include <functional>
#include <memory>
#include <tuple>
#include <vector>
#include <stdint.h>

#include <aidl/android/hardware/camera/device/CameraMetadata.h>
#include <aidl/android/hardware/camera/device/CaptureResult.h>
#include <aidl/android/hardware/camera/device/HalStream.h>
#include <aidl/android/hardware/camera/device/RequestTemplate.h>
#include <aidl/android/hardware/camera/device/Stream.h>
#include <aidl/android/hardware/camera/device/StreamBuffer.h>
#include <aidl/android/hardware/graphics/common/BufferUsage.h>
#include <aidl/android/hardware/graphics/common/Dataspace.h>
#include <aidl/android/hardware/graphics/common/PixelFormat.h>

#include <cutils/native_handle.h>
#include <system/camera_metadata.h>
#include <system/graphics.h>

#include "Rect.h"
#include "Span.h"
#include "CachedStreamBuffer.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace hw {

using aidl::android::hardware::camera::device::CameraMetadata;
using aidl::android::hardware::camera::device::HalStream;
using aidl::android::hardware::camera::device::RequestTemplate;
using aidl::android::hardware::camera::device::Stream;
using aidl::android::hardware::camera::device::StreamBuffer;
using aidl::android::hardware::graphics::common::BufferUsage;
using aidl::android::hardware::graphics::common::Dataspace;
using aidl::android::hardware::graphics::common::PixelFormat;

struct HwCaptureRequest {
    CameraMetadata metadataUpdate;
    std::vector<CachedStreamBuffer*> buffers;
    int32_t frameNumber;
};

// pass `true` to process the buffer, pass `false` to return an error asap to
// release the underlying buffer to the framework.
using DelayedStreamBuffer = std::function<StreamBuffer(bool)>;

struct HwCamera {
    static constexpr int32_t kErrorBadFormat = -1;
    static constexpr int32_t kErrorBadUsage = -2;
    static constexpr int32_t kErrorBadDataspace = -3;

    virtual ~HwCamera() {}

    virtual std::tuple<PixelFormat, BufferUsage, Dataspace, int32_t>
        overrideStreamParams(PixelFormat, BufferUsage, Dataspace) const = 0;

    virtual bool configure(const CameraMetadata& sessionParams, size_t nStreams,
                           const Stream* streams, const HalStream* halStreams) = 0;
    virtual void close() = 0;

    virtual std::tuple<int64_t, int64_t, CameraMetadata, std::vector<StreamBuffer>,
                       std::vector<DelayedStreamBuffer>>
        processCaptureRequest(CameraMetadata, Span<CachedStreamBuffer*>) = 0;

    static int64_t getFrameDuration(const camera_metadata_t*, int64_t def,
                                    int64_t min, int64_t max);

    static camera_metadata_enum_android_lens_state_t
        getAfLensState(camera_metadata_enum_android_control_af_state_t);

    static bool compressJpeg(Rect<uint16_t> imageSize,
                             const android_ycbcr& imageYcbcr,
                             const CameraMetadata& metadata,
                             const native_handle_t* jpegBuffer,
                             size_t jpegBufferSize);

    static bool convertRGBAtoRAW16(Rect<uint16_t> imageSize,
                                   const void* rgba,
                                   const native_handle_t* raw16Buffer);

    ////////////////////////////////////////////////////////////////////////////
    virtual Span<const std::pair<int32_t, int32_t>> getTargetFpsRanges() const = 0;
    virtual std::tuple<int32_t, int32_t, int32_t, int32_t> getAeCompensationRange() const;
    virtual std::pair<float, float> getZoomRatioRange() const;
    virtual std::pair<int, int> getSupportedFlashStrength() const;
    virtual Span<const Rect<uint16_t>> getAvailableThumbnailSizes() const = 0;
    virtual int32_t getJpegMaxSize() const;
    virtual bool isBackFacing() const = 0;
    virtual Span<const float> getAvailableApertures() const;
    virtual Span<const float> getAvailableFocalLength() const;
    virtual float getHyperfocalDistance() const;
    virtual float getMinimumFocusDistance() const;
    virtual std::tuple<int32_t, int32_t, int32_t> getMaxNumOutputStreams() const = 0;
    virtual int32_t getPipelineMaxDepth() const;
    virtual uint32_t getAvailableCapabilitiesBitmap() const;
    virtual Span<const PixelFormat> getSupportedPixelFormats() const = 0;
    virtual Span<const Rect<uint16_t>> getSupportedResolutions() const = 0;
    virtual float getMaxDigitalZoom() const;
    virtual int64_t getMinFrameDurationNs() const = 0;
    virtual int64_t getStallFrameDurationNs() const;
    virtual int32_t getSensorOrientation() const;
    virtual Rect<uint16_t> getSensorSize() const = 0;
    virtual uint8_t getSensorColorFilterArrangement() const = 0;
    virtual float getSensorDPI() const;
    virtual std::pair<int32_t, int32_t> getSensorSensitivityRange() const;
    virtual std::pair<int64_t, int64_t> getSensorExposureTimeRange() const = 0;
    virtual int64_t getSensorMaxFrameDuration() const = 0;

    ////////////////////////////////////////////////////////////////////////////
    virtual std::pair<int32_t, int32_t> getDefaultTargetFpsRange(RequestTemplate) const = 0;
    virtual float getDefaultAperture() const;
    virtual float getDefaultFocalLength() const;
    virtual int32_t getDefaultSensorSensitivity() const;
    virtual int64_t getDefaultSensorExpTime() const = 0;
    virtual int64_t getDefaultSensorFrameDuration() const = 0;
};

using HwCameraFactoryProduct = std::unique_ptr<HwCamera>;
using HwCameraFactory = std::function<HwCameraFactoryProduct()>;

}  // namespace hw
}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
