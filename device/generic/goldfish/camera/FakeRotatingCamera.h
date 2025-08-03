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

#include <unordered_map>

#include <android-base/unique_fd.h>

#include "abc3d.h"

#include "AutoNativeHandle.h"
#include "AFStateMachine.h"
#include "HwCamera.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace hw {

struct FakeRotatingCamera : public HwCamera {
    explicit FakeRotatingCamera(bool isBackFacing);
    ~FakeRotatingCamera() override;

    std::tuple<PixelFormat, BufferUsage, Dataspace, int32_t>
        overrideStreamParams(PixelFormat, BufferUsage, Dataspace) const override;

    bool configure(const CameraMetadata& sessionParams, size_t nStreams,
                   const Stream* streams, const HalStream* halStreams) override;
    void close() override;

    std::tuple<int64_t, int64_t, CameraMetadata, std::vector<StreamBuffer>,
               std::vector<DelayedStreamBuffer>>
        processCaptureRequest(CameraMetadata, Span<CachedStreamBuffer*>) override;

    // metadata
    Span<const std::pair<int32_t, int32_t>> getTargetFpsRanges() const override;
    Span<const Rect<uint16_t>> getAvailableThumbnailSizes() const override;
    bool isBackFacing() const override;
    Span<const float> getAvailableFocalLength() const override;
    std::tuple<int32_t, int32_t, int32_t> getMaxNumOutputStreams() const override;
    Span<const PixelFormat> getSupportedPixelFormats() const override;
    Span<const Rect<uint16_t>> getSupportedResolutions() const override;
    int64_t getMinFrameDurationNs() const override;
    Rect<uint16_t> getSensorSize() const override;
    uint8_t getSensorColorFilterArrangement() const override;
    std::pair<int64_t, int64_t> getSensorExposureTimeRange() const override;
    int64_t getSensorMaxFrameDuration() const override;
    std::pair<int32_t, int32_t> getDefaultTargetFpsRange(RequestTemplate) const override;
    int64_t getDefaultSensorExpTime() const override;
    int64_t getDefaultSensorFrameDuration() const override;
    float getDefaultFocalLength() const override;

private:
    struct StreamInfo {
        std::unique_ptr<const native_handle_t,
                        AutoAllocatorNativeHandleDeleter> rgbaBuffer;
        BufferUsage usage;
        Rect<uint16_t> size;
        PixelFormat pixelFormat;
        uint32_t blobBufferSize;
    };

    struct SensorValues {
        float accel[3];
        float magnetic[3];
        float rotation[3];
    };

    struct RenderParams {
        struct CameraParams {
            float pos3[3];
            float rotXYZ3[3];
        };
        CameraParams cameraParams;
    };

    abc3d::EglCurrentContext initOpenGL();
    void closeImpl(bool everything);

    void captureFrame(const StreamInfo& si,
                      const RenderParams& renderParams,
                      CachedStreamBuffer* csb,
                      std::vector<StreamBuffer>* outputBuffers,
                      std::vector<DelayedStreamBuffer>* delayedOutputBuffers) const;
    bool captureFrameRGBA(const StreamInfo& si,
                          const RenderParams& renderParams,
                          CachedStreamBuffer* dst) const;
    bool captureFrameYUV(const StreamInfo& si,
                         const RenderParams& renderParams,
                         CachedStreamBuffer* dst) const;
    DelayedStreamBuffer captureFrameJpeg(const StreamInfo& si,
                                         const RenderParams& renderParams,
                                         CachedStreamBuffer* csb) const;
    std::vector<uint8_t> captureFrameForCompressing(const StreamInfo& si,
                                                    const RenderParams& renderParams) const;
    bool renderIntoRGBA(const StreamInfo& si,
                        const RenderParams& renderParams,
                        const native_handle_t* rgbaBuffer) const;
    bool drawScene(Rect<uint16_t> imageSize,
                   const RenderParams& renderParams,
                   bool isHardwareBuffer) const;
    bool drawSceneImpl(const float pvMatrix44[]) const;
    CameraMetadata applyMetadata(const CameraMetadata& metadata);
    CameraMetadata updateCaptureResultMetadata();
    bool readSensors(SensorValues* vals);

    const bool mIsBackFacing;
    AFStateMachine mAFStateMachine;
    std::unordered_map<int32_t, StreamInfo> mStreamInfoCache;
    base::unique_fd mQemuChannel;

    abc3d::EglContext mEglContext;
    abc3d::AutoTexture mGlTestPatternTexture;
    GLint mGlProgramAttrPositionLoc;
    GLint mGlProgramAttrTexCoordLoc;
    GLint mGlProgramUniformTextureLoc;
    GLint mGlProgramUniformPvmMatrixLoc;
    abc3d::AutoProgram mGlProgram;

    CameraMetadata mCaptureResultMetadata;
    int64_t mFrameDurationNs = 0;
};

}  // namespace hw
}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
