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

#include <string>
#include <unordered_map>

#include <android-base/unique_fd.h>

#include "HwCamera.h"
#include "AFStateMachine.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace hw {

struct QemuCamera : public HwCamera {
    struct Parameters {
        std::string name;
        std::vector<Rect<uint16_t>> supportedResolutions;
        std::vector<Rect<uint16_t>> availableThumbnailResolutions;
        Rect<uint16_t> sensorSize;
        bool isBackFacing;
    };

    explicit QemuCamera(const Parameters& params);

    std::tuple<PixelFormat, BufferUsage, Dataspace, int32_t>
        overrideStreamParams(PixelFormat, BufferUsage, Dataspace) const override;

    bool configure(const CameraMetadata& sessionParams, size_t nStreams,
                   const Stream* streams, const HalStream* halStreams) override;
    void close() override;

    std::tuple<int64_t, int64_t, CameraMetadata, std::vector<StreamBuffer>,
               std::vector<DelayedStreamBuffer>>
        processCaptureRequest(CameraMetadata, Span<CachedStreamBuffer*>) override;

    // metadata
    uint32_t getAvailableCapabilitiesBitmap() const override;
    Span<const std::pair<int32_t, int32_t>> getTargetFpsRanges() const override;
    Span<const Rect<uint16_t>> getAvailableThumbnailSizes() const override;
    bool isBackFacing() const override;
    Span<const float> getAvailableApertures() const override;
    std::tuple<int32_t, int32_t, int32_t> getMaxNumOutputStreams() const override;
    Span<const PixelFormat> getSupportedPixelFormats() const override;
    Span<const Rect<uint16_t>> getSupportedResolutions() const override;
    int64_t getMinFrameDurationNs() const override;
    Rect<uint16_t> getSensorSize() const override;
    uint8_t getSensorColorFilterArrangement() const override;
    std::pair<int32_t, int32_t> getSensorSensitivityRange() const override;
    std::pair<int64_t, int64_t> getSensorExposureTimeRange() const override;
    int64_t getSensorMaxFrameDuration() const override;

    std::pair<int32_t, int32_t> getDefaultTargetFpsRange(RequestTemplate) const override;
    float getDefaultAperture() const override;
    int64_t getDefaultSensorExpTime() const override;
    int64_t getDefaultSensorFrameDuration() const override;
    int32_t getDefaultSensorSensitivity() const override;

private:
    struct StreamInfo {
        Rect<uint16_t> size;
        PixelFormat pixelFormat;
        uint32_t blobBufferSize;
    };

    void captureFrame(const StreamInfo& si,
                      CachedStreamBuffer* csb,
                      std::vector<StreamBuffer>* outputBuffers,
                      std::vector<DelayedStreamBuffer>* delayedOutputBuffers) const;
    bool captureFrameYUV(const StreamInfo& si, CachedStreamBuffer* dst) const;
    bool captureFrameRGBA(const StreamInfo& si, CachedStreamBuffer* dst) const;
    DelayedStreamBuffer captureFrameRAW16(const StreamInfo& si,
                                          CachedStreamBuffer* csb) const;
    DelayedStreamBuffer captureFrameJpeg(const StreamInfo& si,
                                         CachedStreamBuffer* csb) const;
    const native_handle_t* captureFrameForCompressing(Rect<uint16_t> dim,
                                                      PixelFormat bufferFormat,
                                                      uint32_t qemuFormat) const;
    bool queryFrame(Rect<uint16_t> dim, uint32_t pixelFormat,
                    float exposureComp, uint64_t dataOffset) const;
    static float calculateExposureComp(int64_t exposureNs, int sensorSensitivity,
                                       float aperture);
    CameraMetadata applyMetadata(const CameraMetadata& metadata);
    CameraMetadata updateCaptureResultMetadata();

    const Parameters& mParams;
    AFStateMachine mAFStateMachine;
    std::unordered_map<int32_t, StreamInfo> mStreamInfoCache;
    base::unique_fd mQemuChannel;
    CameraMetadata mCaptureResultMetadata;

    int64_t mFrameDurationNs = 0;
    int64_t mSensorExposureDurationNs = 0;
    int32_t mSensorSensitivity = 0;
    float mAperture = 0;
    float mExposureComp = 0;
};

}  // namespace hw
}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
