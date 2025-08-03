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

#define FAILURE_DEBUG_PREFIX "FakeRotatingCamera"

#include <log/log.h>
#include <android-base/properties.h>
#include <system/camera_metadata.h>
#include <ui/GraphicBuffer.h>
#include <ui/GraphicBufferAllocator.h>
#include <ui/GraphicBufferMapper.h>

#include <gralloc_cb_bp.h>
#include <qemu_pipe_bp.h>

#define GL_GLEXT_PROTOTYPES
#define EGL_EGLEXT_PROTOTYPES
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#undef EGL_EGLEXT_PROTOTYPES
#undef GL_GLEXT_PROTOTYPES

#include "acircles_pattern_512_512.h"
#include "converters.h"
#include "debug.h"
#include "FakeRotatingCamera.h"
#include "jpeg.h"
#include "metadata_utils.h"
#include "utils.h"
#include "yuv.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace hw {

using base::unique_fd;

namespace {
constexpr char kClass[] = "FakeRotatingCamera";

constexpr int kMinFPS = 2;
constexpr int kMedFPS = 15;
constexpr int kMaxFPS = 30;
constexpr int64_t kOneSecondNs = 1000000000;

constexpr float kDefaultFocalLength = 2.8;

constexpr int64_t kMinFrameDurationNs = kOneSecondNs / kMaxFPS;
constexpr int64_t kMaxFrameDurationNs = kOneSecondNs / kMinFPS;
constexpr int64_t kDefaultFrameDurationNs = kOneSecondNs / kMedFPS;

constexpr int64_t kDefaultSensorExposureTimeNs = kOneSecondNs / 100;
constexpr int64_t kMinSensorExposureTimeNs = kDefaultSensorExposureTimeNs / 100;
constexpr int64_t kMaxSensorExposureTimeNs = kDefaultSensorExposureTimeNs * 10;

constexpr int32_t kDefaultJpegQuality = 85;

constexpr BufferUsage usageOr(const BufferUsage a, const BufferUsage b) {
    return static_cast<BufferUsage>(static_cast<uint64_t>(a) | static_cast<uint64_t>(b));
}

constexpr bool usageTest(const BufferUsage a, const BufferUsage b) {
    return (static_cast<uint64_t>(a) & static_cast<uint64_t>(b)) != 0;
}

constexpr uint16_t toR5G6B5(float r, float g, float b) {
    return uint16_t(b * 31) | (uint16_t(g * 63) << 5) | (uint16_t(r * 31) << 11);
}

constexpr uint32_t toR8G8B8A8(uint8_t r, uint8_t g, uint8_t b, uint8_t a) {
    return uint32_t(r) | (uint32_t(g) << 8) | (uint32_t(b) << 16) | (uint32_t(a) << 24);
}

constexpr double degrees2rad(const double degrees) {
    return degrees * M_PI / 180.0;
}

// This texture is useful to debug camera orientation and image aspect ratio
abc3d::AutoTexture loadTestPatternTextureA() {
    constexpr uint16_t B = toR5G6B5(.4, .4, .4);
    constexpr uint16_t R = toR5G6B5( 1, .1, .1);

    static const uint16_t texels[] = {
        B, R, R, R, R, R, B, B,
        R, B, B, B, B, B, R, B,
        B, B, B, B, B, B, R, B,
        B, R, R, R, R, R, B, B,
        R, B, B, B, B, B, R, B,
        R, B, B, B, B, B, R, B,
        R, B, B, B, B, B, R, B,
        B, R, R, R, R, R, B, R,
    };

    abc3d::AutoTexture tex(GL_TEXTURE_2D, GL_RGB, 8, 8,
                           GL_RGB, GL_UNSIGNED_SHORT_5_6_5, texels);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

    return tex;
}

// This texture is useful to debug camera dataspace
abc3d::AutoTexture loadTestPatternTextureColors() {
    static const uint32_t texels[] = {
        toR8G8B8A8(32, 0, 0, 255), toR8G8B8A8(64, 0, 0, 255), toR8G8B8A8(96, 0, 0, 255), toR8G8B8A8(128, 0, 0, 255),
        toR8G8B8A8(160, 0, 0, 255), toR8G8B8A8(192, 0, 0, 255), toR8G8B8A8(224, 0, 0, 255), toR8G8B8A8(255, 0, 0, 255),

        toR8G8B8A8(32, 32, 0, 255), toR8G8B8A8(64, 64, 0, 255), toR8G8B8A8(96, 96, 0, 255), toR8G8B8A8(128, 128, 0, 255),
        toR8G8B8A8(160, 160, 0, 255), toR8G8B8A8(192, 192, 0, 255), toR8G8B8A8(224, 224, 0, 255), toR8G8B8A8(255, 255, 0, 255),

        toR8G8B8A8(0, 32, 0, 255), toR8G8B8A8(0, 64, 0, 255), toR8G8B8A8(0, 96, 0, 255), toR8G8B8A8(0, 128, 0, 255),
        toR8G8B8A8(0, 160, 0, 255), toR8G8B8A8(0, 192, 0, 255), toR8G8B8A8(0, 224, 0, 255), toR8G8B8A8(0, 255, 0, 255),

        toR8G8B8A8(0, 32, 32, 255), toR8G8B8A8(0, 64, 64, 255), toR8G8B8A8(0, 96, 96, 255), toR8G8B8A8(0, 128, 128, 255),
        toR8G8B8A8(0, 160, 160, 255), toR8G8B8A8(0, 192, 192, 255), toR8G8B8A8(0, 224, 224, 255), toR8G8B8A8(0, 255, 255, 255),

        toR8G8B8A8(0, 0, 32, 255), toR8G8B8A8(0, 0, 64, 255), toR8G8B8A8(0, 0, 96, 255), toR8G8B8A8(0, 0, 128, 255),
        toR8G8B8A8(0, 0, 160, 255), toR8G8B8A8(0, 0, 192, 255), toR8G8B8A8(0, 0, 224, 255), toR8G8B8A8(0, 0, 255, 255),

        toR8G8B8A8(32, 0, 32, 255), toR8G8B8A8(64, 0, 64, 255), toR8G8B8A8(96, 0, 96, 255), toR8G8B8A8(128, 0, 128, 255),
        toR8G8B8A8(160, 0, 160, 255), toR8G8B8A8(192, 0, 192, 255), toR8G8B8A8(224, 0, 224, 255), toR8G8B8A8(255, 0, 255, 255),

        toR8G8B8A8(32, 128, 0, 255), toR8G8B8A8(64, 128, 0, 255), toR8G8B8A8(96, 128, 0, 255), toR8G8B8A8(255, 255, 255, 255),
        toR8G8B8A8(160, 128, 0, 255), toR8G8B8A8(192, 128, 0, 255), toR8G8B8A8(224, 128, 0, 255), toR8G8B8A8(255, 128, 0, 255),

        toR8G8B8A8(0, 0, 0, 255), toR8G8B8A8(32, 32, 32, 255), toR8G8B8A8(64, 64, 64, 255), toR8G8B8A8(96, 96, 96, 255),
        toR8G8B8A8(128, 128, 128, 255), toR8G8B8A8(160, 160, 160, 255), toR8G8B8A8(192, 192, 192, 255), toR8G8B8A8(224, 224, 224, 255),
    };

    abc3d::AutoTexture tex(GL_TEXTURE_2D, GL_RGBA, 8, 8,
                           GL_RGBA, GL_UNSIGNED_BYTE, texels);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

    return tex;
}

// This texture is used to pass CtsVerifier
abc3d::AutoTexture loadTestPatternTextureAcircles() {
    constexpr uint16_t kPalette[] = {
        toR5G6B5(0, 0, 0),
        toR5G6B5(.25, .25, .25),
        toR5G6B5(.5, .5, .5),
        toR5G6B5(1, 1, 0),
        toR5G6B5(1, 1, 1),
    };

    std::vector<uint16_t> texels;
    texels.reserve(kAcirclesPatternWidth * kAcirclesPatternWidth);

    auto i = std::begin(kAcirclesPatternRLE);
    const auto end = std::end(kAcirclesPatternRLE);
    while (i < end) {
        const unsigned x = *i;
        ++i;
        unsigned n;
        uint16_t color;
        if (x & 1) {
            n = (x >> 3) + 1;
            color = kPalette[(x >> 1) & 3];
        } else {
            if (x & 2) {
                n = ((unsigned(*i) << 6) | (x >> 2)) + 1;
                ++i;
            } else {
                n = (x >> 2) + 1;
            }
            color = kPalette[4];
        }
        texels.insert(texels.end(), n, color);
    }

    abc3d::AutoTexture tex(GL_TEXTURE_2D, GL_RGB,
                           kAcirclesPatternWidth, kAcirclesPatternWidth,
                           GL_RGB, GL_UNSIGNED_SHORT_5_6_5, texels.data());
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

    return tex;
}

abc3d::AutoTexture loadTestPatternTexture() {
    std::string valueStr =
        base::GetProperty("vendor.qemu.FakeRotatingCamera.scene", "");
    if (valueStr.empty()) {
        valueStr =
            base::GetProperty("ro.boot.qemu.FakeRotatingCamera.scene", "");
    }

    if (strcmp(valueStr.c_str(), "a") == 0) {
        return loadTestPatternTextureA();
    } else if (strcmp(valueStr.c_str(), "colors") == 0) {
        return loadTestPatternTextureColors();
    } else {
        return loadTestPatternTextureAcircles();
    }
}

bool compressNV21IntoJpeg(const Rect<uint16_t> imageSize,
                          const uint8_t* nv21data,
                          const CameraMetadata& metadata,
                          const native_handle_t* jpegBuffer,
                          const size_t jpegBufferSize) {
    const android_ycbcr imageYcbcr = yuv::NV21init(imageSize.width, imageSize.height,
                                                   const_cast<uint8_t*>(nv21data));

    return HwCamera::compressJpeg(imageSize, imageYcbcr, metadata,
                                  jpegBuffer, jpegBufferSize);
}

}  // namespace

FakeRotatingCamera::FakeRotatingCamera(const bool isBackFacing)
        : mIsBackFacing(isBackFacing)
        , mAFStateMachine(200, 1, 2) {}

FakeRotatingCamera::~FakeRotatingCamera() {
    closeImpl(true);
}

std::tuple<PixelFormat, BufferUsage, Dataspace, int32_t>
FakeRotatingCamera::overrideStreamParams(const PixelFormat format,
                                         const BufferUsage usage,
                                         const Dataspace dataspace) const {
    constexpr BufferUsage kRgbaExtraUsage = usageOr(BufferUsage::CAMERA_OUTPUT,
                                                    BufferUsage::GPU_RENDER_TARGET);
    constexpr BufferUsage kYuvExtraUsage = usageOr(BufferUsage::CAMERA_OUTPUT,
                                                   BufferUsage::CPU_WRITE_OFTEN);
    constexpr BufferUsage kBlobExtraUsage = usageOr(BufferUsage::CAMERA_OUTPUT,
                                                    BufferUsage::CPU_WRITE_OFTEN);

    switch (format) {
    case PixelFormat::YCBCR_420_888:
        return {PixelFormat::YCBCR_420_888, usageOr(usage, kYuvExtraUsage),
                Dataspace::JFIF, (usageTest(usage, BufferUsage::VIDEO_ENCODER) ? 8 : 4)};

    case PixelFormat::IMPLEMENTATION_DEFINED:
        if (usageTest(usage, BufferUsage::VIDEO_ENCODER)) {
            return {PixelFormat::YCBCR_420_888, usageOr(usage, kYuvExtraUsage),
                    Dataspace::JFIF, 8};
        } else {
            return {PixelFormat::RGBA_8888, usageOr(usage, kRgbaExtraUsage),
                    Dataspace::UNKNOWN, 4};
        }

    case PixelFormat::RGBA_8888:
        return {PixelFormat::RGBA_8888, usageOr(usage, kRgbaExtraUsage),
                Dataspace::UNKNOWN, (usageTest(usage, BufferUsage::VIDEO_ENCODER) ? 8 : 4)};

    case PixelFormat::BLOB:
        switch (dataspace) {
        case Dataspace::JFIF:
            return {PixelFormat::BLOB, usageOr(usage, kBlobExtraUsage),
                    Dataspace::JFIF, 4};  // JPEG
        default:
            return {format, usage, dataspace, FAILURE(kErrorBadDataspace)};
        }

    default:
        return {format, usage, dataspace, FAILURE(kErrorBadFormat)};
    }
}

bool FakeRotatingCamera::configure(const CameraMetadata& sessionParams,
                                   size_t nStreams,
                                   const Stream* streams,
                                   const HalStream* halStreams) {
    closeImpl(false);

    applyMetadata(sessionParams);

    if (!mQemuChannel.ok()) {
        static const char kPipeName[] = "FakeRotatingCameraSensor";
        mQemuChannel.reset(qemu_pipe_open_ns(NULL, kPipeName, O_RDWR));
        if (!mQemuChannel.ok()) {
            ALOGE("%s:%s:%d qemu_pipe_open_ns failed for '%s'",
                  kClass, __func__, __LINE__, kPipeName);
            return FAILURE(false);
        }
    }

    const abc3d::EglCurrentContext currentContext = initOpenGL();
    if (!currentContext.ok()) {
        return FAILURE(false);
    }

    LOG_ALWAYS_FATAL_IF(!mStreamInfoCache.empty());
    for (; nStreams > 0; --nStreams, ++streams, ++halStreams) {
        const int32_t id = streams->id;
        LOG_ALWAYS_FATAL_IF(halStreams->id != id);
        StreamInfo& si = mStreamInfoCache[id];
        si.usage = halStreams->producerUsage;
        si.size.width = streams->width;
        si.size.height = streams->height;
        si.pixelFormat = halStreams->overrideFormat;
        si.blobBufferSize = streams->bufferSize;

        if (si.pixelFormat != PixelFormat::RGBA_8888) {
            const native_handle_t* buffer;
            GraphicBufferAllocator& gba = GraphicBufferAllocator::get();
            uint32_t stride;

            if (gba.allocate(si.size.width, si.size.height,
                    static_cast<int>(PixelFormat::RGBA_8888), 1,
                    static_cast<uint64_t>(usageOr(BufferUsage::GPU_RENDER_TARGET,
                                                  usageOr(BufferUsage::CPU_READ_OFTEN,
                                                          BufferUsage::CAMERA_OUTPUT))),
                    &buffer, &stride, kClass) == NO_ERROR) {
                si.rgbaBuffer.reset(buffer);
            } else {
                mStreamInfoCache.clear();
                return FAILURE(false);
            }
        }
    }

    return true;
}

void FakeRotatingCamera::close() {
    closeImpl(true);
}

abc3d::EglCurrentContext FakeRotatingCamera::initOpenGL() {
    if (mGlProgram.ok()) {
        return mEglContext.getCurrentContext();
    }

    abc3d::EglContext context;
    abc3d::EglCurrentContext currentContext = context.init();
    if (!currentContext.ok()) {
        return abc3d::EglCurrentContext();
    }

    abc3d::AutoTexture testPatternTexture = loadTestPatternTexture();
    if (!testPatternTexture.ok()) {
        return abc3d::EglCurrentContext();
    }

    const char kVertexShaderStr[] = R"CODE(
attribute vec4 a_position;
attribute vec2 a_texCoord;
uniform mat4 u_pvmMatrix;
varying vec2 v_texCoord;
void main() {
    gl_Position = u_pvmMatrix * a_position;
    v_texCoord = a_texCoord;
}
)CODE";
    abc3d::AutoShader vertexShader;
    if (!vertexShader.compile(GL_VERTEX_SHADER, kVertexShaderStr)) {
        return abc3d::EglCurrentContext();
    }

    const char kFragmentShaderStr[] = R"CODE(
precision mediump float;
varying vec2 v_texCoord;
uniform sampler2D u_texture;
void main() {
    gl_FragColor = texture2D(u_texture, v_texCoord);
}
)CODE";
    abc3d::AutoShader fragmentShader;
    if (!fragmentShader.compile(GL_FRAGMENT_SHADER, kFragmentShaderStr)) {
        return abc3d::EglCurrentContext();
    }

    abc3d::AutoProgram program;
    if (!program.link(vertexShader.get(), fragmentShader.get())) {
        return abc3d::EglCurrentContext();
    }

    const GLint programAttrPositionLoc = program.getAttribLocation("a_position");
    if (programAttrPositionLoc < 0) {
        return abc3d::EglCurrentContext();
    }
    const GLint programAttrTexCoordLoc = program.getAttribLocation("a_texCoord");
    if (programAttrTexCoordLoc < 0) {
        return abc3d::EglCurrentContext();
    }
    const GLint programUniformTextureLoc = program.getUniformLocation("u_texture");
    if (programUniformTextureLoc < 0) {
        return abc3d::EglCurrentContext();
    }
    const GLint programUniformPvmMatrixLoc = program.getUniformLocation("u_pvmMatrix");
    if (programUniformPvmMatrixLoc < 0) {
        return abc3d::EglCurrentContext();
    }

    mEglContext = std::move(context);
    mGlTestPatternTexture = std::move(testPatternTexture);
    mGlProgramAttrPositionLoc = programAttrPositionLoc;
    mGlProgramAttrTexCoordLoc = programAttrTexCoordLoc;
    mGlProgramUniformTextureLoc = programUniformTextureLoc;
    mGlProgramUniformPvmMatrixLoc = programUniformPvmMatrixLoc;
    mGlProgram = std::move(program);

    return std::move(currentContext);
}

void FakeRotatingCamera::closeImpl(const bool everything) {
    {
        const abc3d::EglCurrentContext currentContext = mEglContext.getCurrentContext();
        LOG_ALWAYS_FATAL_IF(!mStreamInfoCache.empty() && !currentContext.ok());
        mStreamInfoCache.clear();

        if (everything) {
            mGlProgram.clear();
            mGlTestPatternTexture.clear();
        }
    }

    if (everything) {
        mEglContext.clear();
        mQemuChannel.reset();
    }
}

std::tuple<int64_t, int64_t, CameraMetadata,
           std::vector<StreamBuffer>, std::vector<DelayedStreamBuffer>>
FakeRotatingCamera::processCaptureRequest(CameraMetadata metadataUpdate,
                                          Span<CachedStreamBuffer*> csbs) {
    CameraMetadata resultMetadata = metadataUpdate.metadata.empty() ?
        updateCaptureResultMetadata() :
        applyMetadata(std::move(metadataUpdate));

    const size_t csbsSize = csbs.size();
    std::vector<StreamBuffer> outputBuffers;
    std::vector<DelayedStreamBuffer> delayedOutputBuffers;
    outputBuffers.reserve(csbsSize);

    const abc3d::EglCurrentContext currentContext = mEglContext.getCurrentContext();
    if (!currentContext.ok()) {
        goto fail;
    }

    RenderParams renderParams;
    {
        SensorValues sensorValues;
        if (readSensors(&sensorValues)) {
            static_assert(sizeof(renderParams.cameraParams.rotXYZ3) ==
                          sizeof(sensorValues.rotation));

            memcpy(renderParams.cameraParams.rotXYZ3, sensorValues.rotation,
                   sizeof(sensorValues.rotation));
        } else {
            goto fail;
        }

        constexpr double kR = 5.0;

        float* pos3 = renderParams.cameraParams.pos3;
        pos3[0] = -kR * sin(sensorValues.rotation[0]) * sin(sensorValues.rotation[1]);
        pos3[1] = -kR * sin(sensorValues.rotation[0]) * cos(sensorValues.rotation[1]);
        pos3[2] = kR * cos(sensorValues.rotation[0]);
    }

    for (size_t i = 0; i < csbsSize; ++i) {
        CachedStreamBuffer* csb = csbs[i];
        LOG_ALWAYS_FATAL_IF(!csb);  // otherwise mNumBuffersInFlight will be hard

        const StreamInfo* si = csb->getStreamInfo<StreamInfo>();
        if (!si) {
            const auto sii = mStreamInfoCache.find(csb->getStreamId());
            if (sii == mStreamInfoCache.end()) {
                ALOGE("%s:%s:%d could not find stream=%d in the cache",
                      kClass, __func__, __LINE__, csb->getStreamId());
            } else {
                si = &sii->second;
                csb->setStreamInfo(si);
            }
        }

        if (si) {
            captureFrame(*si, renderParams, csb, &outputBuffers, &delayedOutputBuffers);
        } else {
            outputBuffers.push_back(csb->finish(false));
        }
    }

    return make_tuple(mFrameDurationNs, kDefaultSensorExposureTimeNs,
                      std::move(resultMetadata), std::move(outputBuffers),
                      std::move(delayedOutputBuffers));

fail:
    for (size_t i = 0; i < csbsSize; ++i) {
        CachedStreamBuffer* csb = csbs[i];
        LOG_ALWAYS_FATAL_IF(!csb);  // otherwise mNumBuffersInFlight will be hard
        outputBuffers.push_back(csb->finish(false));
    }

    return make_tuple(FAILURE(-1), 0,
                      std::move(resultMetadata), std::move(outputBuffers),
                      std::move(delayedOutputBuffers));
}

void FakeRotatingCamera::captureFrame(const StreamInfo& si,
                                      const RenderParams& renderParams,
                                      CachedStreamBuffer* csb,
                                      std::vector<StreamBuffer>* outputBuffers,
                                      std::vector<DelayedStreamBuffer>* delayedOutputBuffers) const {
    switch (si.pixelFormat) {
    case PixelFormat::RGBA_8888:
        outputBuffers->push_back(csb->finish(captureFrameRGBA(si, renderParams, csb)));
        break;

    case PixelFormat::YCBCR_420_888:
        outputBuffers->push_back(csb->finish(captureFrameYUV(si, renderParams, csb)));
        break;

    case PixelFormat::BLOB:
        delayedOutputBuffers->push_back(captureFrameJpeg(si, renderParams, csb));
        break;

    default:
        ALOGE("%s:%s:%d: unexpected pixelFormat=%" PRIx32,
              kClass, __func__, __LINE__, static_cast<uint32_t>(si.pixelFormat));
        outputBuffers->push_back(csb->finish(false));
        break;
    }
}

bool FakeRotatingCamera::captureFrameRGBA(const StreamInfo& si,
                                            const RenderParams& renderParams,
                                            CachedStreamBuffer* csb) const {
    if (!csb->waitAcquireFence(mFrameDurationNs / 2000000)) {
        return FAILURE(false);
    }

    return renderIntoRGBA(si, renderParams, csb->getBuffer());
}

bool FakeRotatingCamera::captureFrameYUV(const StreamInfo& si,
                                         const RenderParams& renderParams,
                                         CachedStreamBuffer* csb) const {
    LOG_ALWAYS_FATAL_IF(!si.rgbaBuffer);
    if (!renderIntoRGBA(si, renderParams, si.rgbaBuffer.get())) {
        return false;
    }

    if (!csb->waitAcquireFence(mFrameDurationNs / 2000000)) {
        return false;
    }

    void* rgba = nullptr;
    if (GraphicBufferMapper::get().lock(
            si.rgbaBuffer.get(), static_cast<uint32_t>(BufferUsage::CPU_READ_OFTEN),
            {si.size.width, si.size.height}, &rgba) != NO_ERROR) {
        return FAILURE(false);
    }

    android_ycbcr ycbcr;
    if (GraphicBufferMapper::get().lockYCbCr(
            csb->getBuffer(), static_cast<uint32_t>(BufferUsage::CPU_WRITE_OFTEN),
            {si.size.width, si.size.height}, &ycbcr) != NO_ERROR) {
        LOG_ALWAYS_FATAL_IF(GraphicBufferMapper::get().unlock(si.rgbaBuffer.get()) != NO_ERROR);
        return FAILURE(false);
    }

    const bool converted = conv::rgba2yuv(si.size.width, si.size.height,
                                          static_cast<const uint32_t*>(rgba),
                                          ycbcr);

    LOG_ALWAYS_FATAL_IF(GraphicBufferMapper::get().unlock(csb->getBuffer()) != NO_ERROR);
    LOG_ALWAYS_FATAL_IF(GraphicBufferMapper::get().unlock(si.rgbaBuffer.get()) != NO_ERROR);

    return converted;
}

DelayedStreamBuffer FakeRotatingCamera::captureFrameJpeg(const StreamInfo& si,
                                                         const RenderParams& renderParams,
                                                         CachedStreamBuffer* csb) const {
    std::vector<uint8_t> nv21data = captureFrameForCompressing(si, renderParams);

    const Rect<uint16_t> imageSize = si.size;
    const uint32_t jpegBufferSize = si.blobBufferSize;
    const int64_t frameDurationNs = mFrameDurationNs;
    CameraMetadata metadata = mCaptureResultMetadata;

    return [csb, imageSize, nv21data = std::move(nv21data), metadata = std::move(metadata),
            jpegBufferSize, frameDurationNs](const bool ok) -> StreamBuffer {
        StreamBuffer sb;
        if (ok && !nv21data.empty() && csb->waitAcquireFence(frameDurationNs / 1000000)) {
            sb = csb->finish(compressNV21IntoJpeg(imageSize, nv21data.data(), metadata,
                                                  csb->getBuffer(), jpegBufferSize));
        } else {
            sb = csb->finish(false);
        }

        return sb;
    };
}

std::vector<uint8_t>
FakeRotatingCamera::captureFrameForCompressing(const StreamInfo& si,
                                               const RenderParams& renderParams) const {
    if (!renderIntoRGBA(si, renderParams, si.rgbaBuffer.get())) {
        return {};
    }

    void* rgba = nullptr;
    if (GraphicBufferMapper::get().lock(
            si.rgbaBuffer.get(), static_cast<uint32_t>(BufferUsage::CPU_READ_OFTEN),
            {si.size.width, si.size.height}, &rgba) != NO_ERROR) {
        return {};
    }

    std::vector<uint8_t> nv21data(yuv::NV21size(si.size.width, si.size.height));
    const android_ycbcr ycbcr = yuv::NV21init(si.size.width, si.size.height,
                                              nv21data.data());

    const bool converted = conv::rgba2yuv(si.size.width, si.size.height,
                                          static_cast<const uint32_t*>(rgba),
                                          ycbcr);

    LOG_ALWAYS_FATAL_IF(GraphicBufferMapper::get().unlock(si.rgbaBuffer.get()) != NO_ERROR);

    if (converted) {
        return nv21data;
    } else {
        return {};
    }
}

bool FakeRotatingCamera::drawScene(const Rect<uint16_t> imageSize,
                                   const RenderParams& renderParams,
                                   const bool isHardwareBuffer) const {
    float pvMatrix44[16];
    {
        float projectionMatrix44[16];
        float viewMatrix44[16];

        // This matrix takes into account specific behaviors below:
        // * The Y axis if rendering int0 AHardwareBuffer goes down while it
        //   goes up everywhere else (e.g. when rendering to `EGLSurface`).
        // * We set `sensorOrientation=90` because a lot of places in Android and
        //   3Ps assume this and don't work properly with `sensorOrientation=0`.
        const float workaroundMatrix44[16] = {
            0, (isHardwareBuffer ? -1.0f : 1.0f), 0, 0,
           -1,                                 0, 0, 0,
            0,                                 0, 1, 0,
            0,                                 0, 0, 1,
        };

        {
            constexpr double kNear = 1.0;
            constexpr double kFar = 10.0;

            // We use `height` to calculate `right` because the image is 90degrees
            // rotated (sensorOrientation=90).
            const double right = kNear * (.5 * getSensorSize().height / getSensorDPI() / getDefaultFocalLength());
            const double top = right / imageSize.width * imageSize.height;
            abc3d::frustum(pvMatrix44, -right, right, -top, top,
                           kNear, kFar);
        }

        abc3d::mulM44(projectionMatrix44, pvMatrix44, workaroundMatrix44);

        {
            const auto& cam = renderParams.cameraParams;
            abc3d::lookAtXyzRot(viewMatrix44, cam.pos3, cam.rotXYZ3);
        }

        abc3d::mulM44(pvMatrix44, projectionMatrix44, viewMatrix44);
    }

    glViewport(0, 0, imageSize.width, imageSize.height);
    const bool result = drawSceneImpl(pvMatrix44);
    glFinish();
    return result;
}

bool FakeRotatingCamera::drawSceneImpl(const float pvMatrix44[]) const {
    constexpr float kX = 0;
    constexpr float kY = 0;
    constexpr float kZ = 0;
    constexpr float kS = 1;

    const GLfloat vVertices[] = {
       -kS + kX, kY, kZ - kS,   // Position 0
        0,  0,                  // TexCoord 0
       -kS + kX, kY, kZ + kS,   // Position 1
        0,  1,                  // TexCoord 1
        kS + kX, kY, kZ + kS,   // Position 2
        1,  1,                  // TexCoord 2
        kS + kX, kY, kZ - kS,   // Position 3
        1,  0                   // TexCoord 3
    };

    static const GLushort indices[] = { 0, 1, 2, 0, 2, 3 };

    glClearColor(0.2, 0.3, 0.2, 1.0);
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(mGlProgram.get());
    glVertexAttribPointer(mGlProgramAttrPositionLoc, 3, GL_FLOAT, GL_FALSE,
                          5 * sizeof(GLfloat), &vVertices[0]);
    glEnableVertexAttribArray(mGlProgramAttrPositionLoc);
    glVertexAttribPointer(mGlProgramAttrTexCoordLoc, 2, GL_FLOAT, GL_FALSE,
                          5 * sizeof(GLfloat), &vVertices[3]);
    glEnableVertexAttribArray(mGlProgramAttrTexCoordLoc);
    glUniformMatrix4fv(mGlProgramUniformPvmMatrixLoc, 1, true, pvMatrix44);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, mGlTestPatternTexture.get());
    glUniform1i(mGlProgramUniformTextureLoc, 0);

    glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_SHORT, indices);

    return true;
}

bool FakeRotatingCamera::renderIntoRGBA(const StreamInfo& si,
                                        const RenderParams& renderParams,
                                        const native_handle_t* rgbaBuffer) const {
    const cb_handle_t* const cb = cb_handle_t::from(rgbaBuffer);
    if (!cb) {
        return FAILURE(false);
    }

    const auto gb = sp<GraphicBuffer>::make(
        rgbaBuffer, GraphicBuffer::WRAP_HANDLE, si.size.width,
        si.size.height, static_cast<int>(si.pixelFormat), 1,
        static_cast<uint64_t>(si.usage), cb->stride);

    const EGLClientBuffer clientBuf =
        eglGetNativeClientBufferANDROID(gb->toAHardwareBuffer());
    if (!clientBuf) {
        return FAILURE(false);
    }

    const abc3d::AutoImageKHR eglImage(mEglContext.getDisplay(), clientBuf);
    if (!eglImage.ok()) {
        return false;
    }

    abc3d::AutoTexture fboTex(GL_TEXTURE_2D);
    glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, eglImage.get());

    abc3d::AutoFrameBuffer fbo;
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                           GL_TEXTURE_2D, fboTex.get(), 0);

    // drawing into EGLClientBuffer is Y-flipped on Android
    return drawScene(si.size, renderParams, true);
}

bool FakeRotatingCamera::readSensors(SensorValues* vals) {
    static const char kReadCommand[] = "get";

    uint32_t len = sizeof(kReadCommand);
    if (qemu_pipe_write_fully(mQemuChannel.get(), &len, sizeof(len))) {
        return FAILURE(false);
    }
    if (qemu_pipe_write_fully(mQemuChannel.get(), &kReadCommand[0], sizeof(kReadCommand))) {
        return FAILURE(false);
    }
    if (qemu_pipe_read_fully(mQemuChannel.get(), &len, sizeof(len))) {
        return FAILURE(false);
    }
    if (len != sizeof(*vals)) {
        return FAILURE(false);
    }
    if (qemu_pipe_read_fully(mQemuChannel.get(), vals, len)) {
        return FAILURE(false);
    }

    return true;
}

CameraMetadata FakeRotatingCamera::applyMetadata(const CameraMetadata& metadata) {
    const camera_metadata_t* const raw =
        reinterpret_cast<const camera_metadata_t*>(metadata.metadata.data());

    mFrameDurationNs = getFrameDuration(raw, kDefaultFrameDurationNs,
                                        kMinFrameDurationNs, kMaxFrameDurationNs);

    camera_metadata_ro_entry_t entry;
    const camera_metadata_enum_android_control_af_mode_t afMode =
        find_camera_metadata_ro_entry(raw, ANDROID_CONTROL_AF_MODE, &entry) ?
            ANDROID_CONTROL_AF_MODE_OFF :
            static_cast<camera_metadata_enum_android_control_af_mode_t>(entry.data.u8[0]);

    const camera_metadata_enum_android_control_af_trigger_t afTrigger =
        find_camera_metadata_ro_entry(raw, ANDROID_CONTROL_AF_TRIGGER, &entry) ?
            ANDROID_CONTROL_AF_TRIGGER_IDLE :
            static_cast<camera_metadata_enum_android_control_af_trigger_t>(entry.data.u8[0]);

    const auto af = mAFStateMachine(afMode, afTrigger);

    CameraMetadataMap m = parseCameraMetadataMap(metadata);

    m[ANDROID_CONTROL_AE_STATE] = uint8_t(ANDROID_CONTROL_AE_STATE_CONVERGED);
    m[ANDROID_CONTROL_AF_STATE] = uint8_t(af.first);
    m[ANDROID_CONTROL_AWB_STATE] = uint8_t(ANDROID_CONTROL_AWB_STATE_CONVERGED);
    m[ANDROID_FLASH_STATE] = uint8_t(ANDROID_FLASH_STATE_UNAVAILABLE);
    m[ANDROID_LENS_APERTURE] = getDefaultAperture();
    m[ANDROID_LENS_FOCUS_DISTANCE] = af.second;
    m[ANDROID_LENS_STATE] = uint8_t(getAfLensState(af.first));
    m[ANDROID_REQUEST_PIPELINE_DEPTH] = uint8_t(4);
    m[ANDROID_SENSOR_FRAME_DURATION] = mFrameDurationNs;
    m[ANDROID_SENSOR_EXPOSURE_TIME] = kDefaultSensorExposureTimeNs;
    m[ANDROID_SENSOR_SENSITIVITY] = getDefaultSensorSensitivity();
    m[ANDROID_SENSOR_TIMESTAMP] = int64_t(0);
    m[ANDROID_SENSOR_ROLLING_SHUTTER_SKEW] = kMinSensorExposureTimeNs;
    m[ANDROID_STATISTICS_SCENE_FLICKER] = uint8_t(ANDROID_STATISTICS_SCENE_FLICKER_NONE);

    std::optional<CameraMetadata> maybeSerialized =
        serializeCameraMetadataMap(m);

    if (maybeSerialized) {
        mCaptureResultMetadata = std::move(maybeSerialized.value());
    }

    {   // reset ANDROID_CONTROL_AF_TRIGGER to IDLE
        camera_metadata_t* const raw =
            reinterpret_cast<camera_metadata_t*>(mCaptureResultMetadata.metadata.data());

        camera_metadata_ro_entry_t entry;
        const auto newTriggerValue = ANDROID_CONTROL_AF_TRIGGER_IDLE;

        if (find_camera_metadata_ro_entry(raw, ANDROID_CONTROL_AF_TRIGGER, &entry)) {
            return mCaptureResultMetadata;
        } else if (entry.data.i32[0] == newTriggerValue) {
            return mCaptureResultMetadata;
        } else {
            CameraMetadata result = mCaptureResultMetadata;

            if (update_camera_metadata_entry(raw, entry.index, &newTriggerValue, 1, nullptr)) {
                ALOGW("%s:%s:%d: update_camera_metadata_entry(ANDROID_CONTROL_AF_TRIGGER) "
                      "failed", kClass, __func__, __LINE__);
            }

            return result;
        }
    }
}

CameraMetadata FakeRotatingCamera::updateCaptureResultMetadata() {
    camera_metadata_t* const raw =
        reinterpret_cast<camera_metadata_t*>(mCaptureResultMetadata.metadata.data());

    const auto af = mAFStateMachine();

    camera_metadata_ro_entry_t entry;

    if (find_camera_metadata_ro_entry(raw, ANDROID_CONTROL_AF_STATE, &entry)) {
        ALOGW("%s:%s:%d: find_camera_metadata_ro_entry(ANDROID_CONTROL_AF_STATE) failed",
              kClass, __func__, __LINE__);
    } else if (update_camera_metadata_entry(raw, entry.index, &af.first, 1, nullptr)) {
        ALOGW("%s:%s:%d: update_camera_metadata_entry(ANDROID_CONTROL_AF_STATE) failed",
              kClass, __func__, __LINE__);
    }

    if (find_camera_metadata_ro_entry(raw, ANDROID_LENS_FOCUS_DISTANCE, &entry)) {
        ALOGW("%s:%s:%d: find_camera_metadata_ro_entry(ANDROID_LENS_FOCUS_DISTANCE) failed",
              kClass, __func__, __LINE__);
    } else if (update_camera_metadata_entry(raw, entry.index, &af.second, 1, nullptr)) {
        ALOGW("%s:%s:%d: update_camera_metadata_entry(ANDROID_LENS_FOCUS_DISTANCE) failed",
              kClass, __func__, __LINE__);
    }

    return metadataCompact(mCaptureResultMetadata);
}

////////////////////////////////////////////////////////////////////////////////

Span<const std::pair<int32_t, int32_t>> FakeRotatingCamera::getTargetFpsRanges() const {
    // ordered to satisfy testPreviewFpsRangeByCamera
    static const std::pair<int32_t, int32_t> targetFpsRanges[] = {
        {kMinFPS, kMedFPS},
        {kMedFPS, kMedFPS},
        {kMinFPS, kMaxFPS},
        {kMaxFPS, kMaxFPS},
    };

    return targetFpsRanges;
}

Span<const Rect<uint16_t>> FakeRotatingCamera::getAvailableThumbnailSizes() const {
    static const Rect<uint16_t> availableThumbnailSizes[] = {
        {0, 0},
        {11 * 4, 9 * 4},
        {16 * 4, 9 * 4},
        {4 * 16, 3 * 16},
    };

    return availableThumbnailSizes;
}

bool FakeRotatingCamera::isBackFacing() const {
    return mIsBackFacing;
}

Span<const float> FakeRotatingCamera::getAvailableFocalLength() const {
    static const float availableFocalLengths[] = {
        kDefaultFocalLength
    };

    return availableFocalLengths;
}

std::tuple<int32_t, int32_t, int32_t> FakeRotatingCamera::getMaxNumOutputStreams() const {
    return {
        0,  // raw
        2,  // processed
        1,  // jpeg
    };
}

Span<const PixelFormat> FakeRotatingCamera::getSupportedPixelFormats() const {
    static const PixelFormat supportedPixelFormats[] = {
        PixelFormat::IMPLEMENTATION_DEFINED,
        PixelFormat::YCBCR_420_888,
        PixelFormat::RGBA_8888,
        PixelFormat::BLOB,
    };

    return {supportedPixelFormats};
}

int64_t FakeRotatingCamera::getMinFrameDurationNs() const {
    return kMinFrameDurationNs;
}

Rect<uint16_t> FakeRotatingCamera::getSensorSize() const {
    return {1920, 1080};
}

uint8_t FakeRotatingCamera::getSensorColorFilterArrangement() const {
    return ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB;
}

std::pair<int64_t, int64_t> FakeRotatingCamera::getSensorExposureTimeRange() const {
    return {kMinSensorExposureTimeNs, kMaxSensorExposureTimeNs};
}

int64_t FakeRotatingCamera::getSensorMaxFrameDuration() const {
    return kMaxFrameDurationNs;
}

Span<const Rect<uint16_t>> FakeRotatingCamera::getSupportedResolutions() const {
    static const Rect<uint16_t> supportedResolutions[] = {
        {176, 144},
        {320, 240},
        {640, 480},
        {1024, 576},
        {1280, 720},
        {1600, 900},
        {1920, 1080},
    };

    return supportedResolutions;
}

std::pair<int32_t, int32_t> FakeRotatingCamera::getDefaultTargetFpsRange(const RequestTemplate tpl) const {
    switch (tpl) {
    case RequestTemplate::PREVIEW:
    case RequestTemplate::VIDEO_RECORD:
    case RequestTemplate::VIDEO_SNAPSHOT:
        return {kMaxFPS, kMaxFPS};

    default:
        return {kMinFPS, kMaxFPS};
    }
}

int64_t FakeRotatingCamera::getDefaultSensorExpTime() const {
    return kDefaultSensorExposureTimeNs;
}

int64_t FakeRotatingCamera::getDefaultSensorFrameDuration() const {
    return kMinFrameDurationNs;
}

float FakeRotatingCamera::getDefaultFocalLength() const {
    return kDefaultFocalLength;
}

}  // namespace hw
}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
