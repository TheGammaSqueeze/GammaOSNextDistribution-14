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

#define FAILURE_DEBUG_PREFIX "QemuCamera"

#include <inttypes.h>
#include <cstdlib>

#include <log/log.h>
#include <system/camera_metadata.h>
#include <linux/videodev2.h>
#include <ui/GraphicBufferAllocator.h>
#include <ui/GraphicBufferMapper.h>

#include <gralloc_cb_bp.h>

#include "debug.h"
#include "jpeg.h"
#include "metadata_utils.h"
#include "QemuCamera.h"
#include "qemu_channel.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace hw {

using base::unique_fd;

namespace {
constexpr char kClass[] = "QemuCamera";

constexpr int kMinFPS = 2;
constexpr int kMedFPS = 15;
constexpr int kMaxFPS = 30;
constexpr int64_t kOneSecondNs = 1000000000;

constexpr int64_t kMinFrameDurationNs = kOneSecondNs / kMaxFPS;
constexpr int64_t kMaxFrameDurationNs = kOneSecondNs / kMinFPS;
constexpr int64_t kDefaultFrameDurationNs = kOneSecondNs / kMedFPS;

constexpr int64_t kMinSensorExposureTimeNs = kOneSecondNs / 20000;
constexpr int64_t kMaxSensorExposureTimeNs = kOneSecondNs / 2;
constexpr int64_t kDefaultSensorExposureTimeNs = kOneSecondNs / 100;

constexpr int32_t kMinSensorSensitivity = 25;
constexpr int32_t kMaxSensorSensitivity = 1600;
constexpr int32_t kDefaultSensorSensitivity = 200;

constexpr float   kMinAperture = 1.4;
constexpr float   kMaxAperture = 16.0;
constexpr float   kDefaultAperture = 4.0;

constexpr int32_t kDefaultJpegQuality = 85;

const float kColorCorrectionGains[4] = {1.0f, 1.0f, 1.0f, 1.0f};

const camera_metadata_rational_t kRationalZero = {
    .numerator = 0, .denominator = 128
};
const camera_metadata_rational_t kRationalOne = {
    .numerator = 128, .denominator = 128
};

const camera_metadata_rational_t kColorCorrectionTransform[9] = {
    kRationalOne, kRationalZero, kRationalZero,
    kRationalZero, kRationalOne, kRationalZero,
    kRationalZero, kRationalZero, kRationalOne
};

const camera_metadata_rational kNeutralColorPoint[3] = {
    {1023, 1}, {1023, 1}, {1023, 1}
};

const double kSensorNoiseProfile[8] = {
    1.0, .000001, 1.0, .000001, 1.0, .000001, 1.0, .000001
};

// system/media/camera/docs/docs.html#dynamic_android.statistics.lensShadingMap
const float kLensShadingMap[] = {
    1.3, 1.2, 1.15, 1.2, 1.2, 1.2, 1.15, 1.2,
    1.1, 1.2, 1.2, 1.2, 1.3, 1.2, 1.3, 1.3,
    1.2, 1.2, 1.25, 1.1, 1.1, 1.1, 1.1, 1.0,
    1.0, 1.0, 1.0, 1.0, 1.2, 1.3, 1.25, 1.2,
    1.3, 1.2, 1.2, 1.3, 1.2, 1.15, 1.1, 1.2,
    1.2, 1.1, 1.0, 1.2, 1.3, 1.15, 1.2, 1.3
};

constexpr BufferUsage usageOr(const BufferUsage a, const BufferUsage b) {
    return static_cast<BufferUsage>(static_cast<uint64_t>(a) | static_cast<uint64_t>(b));
}

constexpr bool usageTest(const BufferUsage a, const BufferUsage b) {
    return (static_cast<uint64_t>(a) & static_cast<uint64_t>(b)) != 0;
}

}  // namespace

QemuCamera::QemuCamera(const Parameters& params)
        : mParams(params)
        , mAFStateMachine(200, 1, 2) {}

std::tuple<PixelFormat, BufferUsage, Dataspace, int32_t>
QemuCamera::overrideStreamParams(const PixelFormat format,
                                 const BufferUsage usage,
                                 const Dataspace dataspace) const {
    constexpr BufferUsage kExtraUsage = usageOr(BufferUsage::CAMERA_OUTPUT,
                                                BufferUsage::CPU_WRITE_OFTEN);

    switch (format) {
    case PixelFormat::IMPLEMENTATION_DEFINED:
        if (usageTest(usage, BufferUsage::VIDEO_ENCODER)) {
            return {PixelFormat::YCBCR_420_888, usageOr(usage, kExtraUsage),
                    Dataspace::JFIF, 8};
        } else {
            return {PixelFormat::RGBA_8888, usageOr(usage, kExtraUsage),
                    Dataspace::UNKNOWN, 4};
        }

    case PixelFormat::YCBCR_420_888:
        return {PixelFormat::YCBCR_420_888, usageOr(usage, kExtraUsage),
                Dataspace::JFIF, usageTest(usage, BufferUsage::VIDEO_ENCODER) ? 8 : 4};

    case PixelFormat::RAW16:
        return {PixelFormat::RAW16, usageOr(usage, kExtraUsage),
                Dataspace::SRGB_LINEAR, 4};

    case PixelFormat::RGBA_8888:
        return {PixelFormat::RGBA_8888, usageOr(usage, kExtraUsage),
                Dataspace::UNKNOWN, usageTest(usage, BufferUsage::VIDEO_ENCODER) ? 8 : 4};

    case PixelFormat::BLOB:
        switch (dataspace) {
        case Dataspace::JFIF:
            return {PixelFormat::BLOB, usageOr(usage, kExtraUsage),
                    Dataspace::JFIF, 4};  // JPEG
        default:
            return {format, usage, dataspace, FAILURE(kErrorBadDataspace)};
        }

    default:
        return {format, usage, dataspace, FAILURE(kErrorBadFormat)};
    }
}

bool QemuCamera::configure(const CameraMetadata& sessionParams,
                           size_t nStreams,
                           const Stream* streams,
                           const HalStream* halStreams) {
    applyMetadata(sessionParams);

    if (!mQemuChannel.ok()) {
        auto qemuChannel = qemuOpenChannel(std::string("name=") + mParams.name);
        if (!qemuChannel.ok()) {
            return false;
        }

        static const char kConnectQuery[] = "connect";
        if (qemuRunQuery(qemuChannel.get(), kConnectQuery, sizeof(kConnectQuery)) < 0) {
            return false;
        }

        static const char kStartQuery[] = "start";
        if (qemuRunQuery(qemuChannel.get(), kStartQuery, sizeof(kStartQuery)) < 0) {
            return false;
        }

        mQemuChannel = std::move(qemuChannel);
    }

    mStreamInfoCache.clear();
    for (; nStreams > 0; --nStreams, ++streams, ++halStreams) {
        const int32_t id = streams->id;
        LOG_ALWAYS_FATAL_IF(halStreams->id != id);
        StreamInfo& si = mStreamInfoCache[id];
        si.size.width = streams->width;
        si.size.height = streams->height;
        si.pixelFormat = halStreams->overrideFormat;
        si.blobBufferSize = streams->bufferSize;
    }

    return true;
}

void QemuCamera::close() {
    mStreamInfoCache.clear();

    if (mQemuChannel.ok()) {
        static const char kStopQuery[] = "stop";
        if (qemuRunQuery(mQemuChannel.get(), kStopQuery, sizeof(kStopQuery)) >= 0) {
            static const char kDisconnectQuery[] = "disconnect";
            qemuRunQuery(mQemuChannel.get(), kDisconnectQuery, sizeof(kDisconnectQuery));
        }

        mQemuChannel.reset();
    }
}

std::tuple<int64_t, int64_t, CameraMetadata,
           std::vector<StreamBuffer>, std::vector<DelayedStreamBuffer>>
QemuCamera::processCaptureRequest(CameraMetadata metadataUpdate,
                                  Span<CachedStreamBuffer*> csbs) {
    CameraMetadata resultMetadata = metadataUpdate.metadata.empty() ?
        updateCaptureResultMetadata() :
        applyMetadata(std::move(metadataUpdate));

    const size_t csbsSize = csbs.size();
    std::vector<StreamBuffer> outputBuffers;
    std::vector<DelayedStreamBuffer> delayedOutputBuffers;
    outputBuffers.reserve(csbsSize);

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
            captureFrame(*si, csb, &outputBuffers, &delayedOutputBuffers);
        } else {
            outputBuffers.push_back(csb->finish(false));
        }
    }

    return make_tuple((mQemuChannel.ok() ? mFrameDurationNs : FAILURE(-1)),
                      mSensorExposureDurationNs,
                      std::move(resultMetadata), std::move(outputBuffers),
                      std::move(delayedOutputBuffers));
}

void QemuCamera::captureFrame(const StreamInfo& si,
                              CachedStreamBuffer* csb,
                              std::vector<StreamBuffer>* outputBuffers,
                              std::vector<DelayedStreamBuffer>* delayedOutputBuffers) const {
    switch (si.pixelFormat) {
    case PixelFormat::YCBCR_420_888:
        outputBuffers->push_back(csb->finish(captureFrameYUV(si, csb)));
        break;

    case PixelFormat::RGBA_8888:
        outputBuffers->push_back(csb->finish(captureFrameRGBA(si, csb)));
        break;

    case PixelFormat::RAW16:
        delayedOutputBuffers->push_back(captureFrameRAW16(si, csb));
        break;

    case PixelFormat::BLOB:
        delayedOutputBuffers->push_back(captureFrameJpeg(si, csb));
        break;

    default:
        ALOGE("%s:%s:%d: unexpected pixelFormat=0x%" PRIx32,
              kClass, __func__, __LINE__,
              static_cast<uint32_t>(si.pixelFormat));
        outputBuffers->push_back(csb->finish(false));
        break;
    }
}

bool QemuCamera::captureFrameYUV(const StreamInfo& si,
                                 CachedStreamBuffer* csb) const {
    const cb_handle_t* const cb = cb_handle_t::from(csb->getBuffer());
    if (!cb) {
        return FAILURE(false);
    }

    if (!csb->waitAcquireFence(mFrameDurationNs / 2000000)) {
        return FAILURE(false);
    }

    const auto size = si.size;
    android_ycbcr ycbcr;
    if (GraphicBufferMapper::get().lockYCbCr(
            cb, static_cast<uint32_t>(BufferUsage::CPU_WRITE_OFTEN),
            {size.width, size.height}, &ycbcr) != NO_ERROR) {
        return FAILURE(false);
    }

    bool const res = queryFrame(si.size, V4L2_PIX_FMT_YUV420,
                                mExposureComp, cb->getMmapedOffset());

    LOG_ALWAYS_FATAL_IF(GraphicBufferMapper::get().unlock(cb) != NO_ERROR);
    return res;
}

bool QemuCamera::captureFrameRGBA(const StreamInfo& si,
                                  CachedStreamBuffer* csb) const {
    const cb_handle_t* const cb = cb_handle_t::from(csb->getBuffer());
    if (!cb) {
        return FAILURE(false);
    }

    if (!csb->waitAcquireFence(mFrameDurationNs / 2000000)) {
        return FAILURE(false);
    }

    const auto size = si.size;
    void* mem = nullptr;
    if (GraphicBufferMapper::get().lock(
            cb, static_cast<uint32_t>(BufferUsage::CPU_WRITE_OFTEN),
            {size.width, size.height}, &mem) != NO_ERROR) {
        return FAILURE(false);
    }

    bool const res = queryFrame(si.size, V4L2_PIX_FMT_RGB32,
                                mExposureComp, cb->getMmapedOffset());
    LOG_ALWAYS_FATAL_IF(GraphicBufferMapper::get().unlock(cb) != NO_ERROR);
    return res;
}

DelayedStreamBuffer QemuCamera::captureFrameRAW16(const StreamInfo& si,
                                                  CachedStreamBuffer* csb) const {
    const native_handle_t* const image = captureFrameForCompressing(
        si.size, PixelFormat::RGBA_8888, V4L2_PIX_FMT_RGB32);

    const Rect<uint16_t> imageSize = si.size;
    const int64_t frameDurationNs = mFrameDurationNs;
    CameraMetadata metadata = mCaptureResultMetadata;

    return [csb, image, imageSize, metadata = std::move(metadata),
            frameDurationNs](const bool ok) -> StreamBuffer {
        StreamBuffer sb;
        if (ok && image && csb->waitAcquireFence(frameDurationNs / 1000000)) {
            void* mem = nullptr;
            if (GraphicBufferMapper::get().lock(
                    image, static_cast<uint32_t>(BufferUsage::CPU_READ_OFTEN),
                    {imageSize.width, imageSize.height}, &mem) == NO_ERROR) {
                sb = csb->finish(convertRGBAtoRAW16(imageSize, mem, csb->getBuffer()));
                LOG_ALWAYS_FATAL_IF(GraphicBufferMapper::get().unlock(image) != NO_ERROR);
            } else {
                sb = csb->finish(FAILURE(false));
            }
        } else {
            sb = csb->finish(false);
        }

        if (image) {
            GraphicBufferAllocator::get().free(image);
        }

        return sb;
    };
}

DelayedStreamBuffer QemuCamera::captureFrameJpeg(const StreamInfo& si,
                                                 CachedStreamBuffer* csb) const {
    const native_handle_t* const image = captureFrameForCompressing(
        si.size, PixelFormat::YCBCR_420_888, V4L2_PIX_FMT_YUV420);

    const Rect<uint16_t> imageSize = si.size;
    const uint32_t jpegBufferSize = si.blobBufferSize;
    const int64_t frameDurationNs = mFrameDurationNs;
    CameraMetadata metadata = mCaptureResultMetadata;

    return [csb, image, imageSize, metadata = std::move(metadata), jpegBufferSize,
            frameDurationNs](const bool ok) -> StreamBuffer {
        StreamBuffer sb;
        if (ok && image && csb->waitAcquireFence(frameDurationNs / 1000000)) {
            android_ycbcr imageYcbcr;
            if (GraphicBufferMapper::get().lockYCbCr(
                    image, static_cast<uint32_t>(BufferUsage::CPU_READ_OFTEN),
                    {imageSize.width, imageSize.height}, &imageYcbcr) == NO_ERROR) {
                sb = csb->finish(compressJpeg(imageSize, imageYcbcr, metadata,
                                              csb->getBuffer(), jpegBufferSize));
                LOG_ALWAYS_FATAL_IF(GraphicBufferMapper::get().unlock(image) != NO_ERROR);
            } else {
                sb = csb->finish(FAILURE(false));
            }
        } else {
            sb = csb->finish(false);
        }

        if (image) {
            GraphicBufferAllocator::get().free(image);
        }

        return sb;
    };
}

const native_handle_t* QemuCamera::captureFrameForCompressing(
        const Rect<uint16_t> dim,
        const PixelFormat bufferFormat,
        const uint32_t qemuFormat) const {
    constexpr BufferUsage kUsage = usageOr(BufferUsage::CAMERA_OUTPUT,
                                           BufferUsage::CPU_READ_OFTEN);

    GraphicBufferAllocator& gba = GraphicBufferAllocator::get();
    const native_handle_t* image = nullptr;
    uint32_t stride;

    if (gba.allocate(dim.width, dim.height, static_cast<int>(bufferFormat), 1,
                     static_cast<uint64_t>(kUsage), &image, &stride,
                     "QemuCamera") != NO_ERROR) {
        return FAILURE(nullptr);
    }

    const cb_handle_t* const cb = cb_handle_t::from(image);
    if (!cb) {
        gba.free(image);
        return FAILURE(nullptr);
    }

    if (!queryFrame(dim, qemuFormat, mExposureComp, cb->getMmapedOffset())) {
        gba.free(image);
        return FAILURE(nullptr);
    }

    return image;
}

bool QemuCamera::queryFrame(const Rect<uint16_t> dim,
                            const uint32_t pixelFormat,
                            const float exposureComp,
                            const uint64_t dataOffset) const {
    constexpr float scaleR = 1;
    constexpr float scaleG = 1;
    constexpr float scaleB = 1;

    char queryStr[128];
    const int querySize = snprintf(queryStr, sizeof(queryStr),
        "frame dim=%" PRIu32 "x%" PRIu32 " pix=%" PRIu32 " offset=%" PRIu64
        " whiteb=%g,%g,%g expcomp=%g time=%d",
        dim.width, dim.height, static_cast<uint32_t>(pixelFormat), dataOffset,
        scaleR, scaleG, scaleB, exposureComp, 0);

    return qemuRunQuery(mQemuChannel.get(), queryStr, querySize + 1) >= 0;
}

float QemuCamera::calculateExposureComp(const int64_t exposureNs,
                                        const int sensorSensitivity,
                                        const float aperture) {
    return (double(exposureNs) * sensorSensitivity
                * kDefaultAperture * kDefaultAperture) /
           (double(kDefaultSensorExposureTimeNs) * kDefaultSensorSensitivity
                * aperture * aperture);
}

CameraMetadata QemuCamera::applyMetadata(const CameraMetadata& metadata) {
    const camera_metadata_t* const raw =
        reinterpret_cast<const camera_metadata_t*>(metadata.metadata.data());
    camera_metadata_ro_entry_t entry;

    mFrameDurationNs = getFrameDuration(raw, kDefaultFrameDurationNs,
                                        kMinFrameDurationNs, kMaxFrameDurationNs);

    if (find_camera_metadata_ro_entry(raw, ANDROID_SENSOR_EXPOSURE_TIME, &entry)) {
        mSensorExposureDurationNs = std::min(mFrameDurationNs, kDefaultSensorExposureTimeNs);
    } else {
        mSensorExposureDurationNs = entry.data.i64[0];
    }

    if (find_camera_metadata_ro_entry(raw, ANDROID_SENSOR_SENSITIVITY, &entry)) {
        mSensorSensitivity = kDefaultSensorSensitivity;
    } else {
        mSensorSensitivity = entry.data.i32[0];
    }

    if (find_camera_metadata_ro_entry(raw, ANDROID_LENS_APERTURE, &entry)) {
        mAperture = kDefaultAperture;
    } else {
        mAperture = entry.data.f[0];
    }

    const camera_metadata_enum_android_control_af_mode_t afMode =
        find_camera_metadata_ro_entry(raw, ANDROID_CONTROL_AF_MODE, &entry) ?
            ANDROID_CONTROL_AF_MODE_OFF :
            static_cast<camera_metadata_enum_android_control_af_mode_t>(entry.data.u8[0]);

    const camera_metadata_enum_android_control_af_trigger_t afTrigger =
        find_camera_metadata_ro_entry(raw, ANDROID_CONTROL_AF_TRIGGER, &entry) ?
            ANDROID_CONTROL_AF_TRIGGER_IDLE :
            static_cast<camera_metadata_enum_android_control_af_trigger_t>(entry.data.u8[0]);

    const auto af = mAFStateMachine(afMode, afTrigger);

    mExposureComp = calculateExposureComp(mSensorExposureDurationNs,
                                          mSensorSensitivity, mAperture);

    CameraMetadataMap m = parseCameraMetadataMap(metadata);

    m[ANDROID_COLOR_CORRECTION_GAINS] = kColorCorrectionGains;
    m[ANDROID_COLOR_CORRECTION_TRANSFORM] = kColorCorrectionTransform;
    m[ANDROID_CONTROL_AE_STATE] = uint8_t(ANDROID_CONTROL_AE_STATE_CONVERGED);
    m[ANDROID_CONTROL_AF_STATE] = uint8_t(af.first);
    m[ANDROID_CONTROL_AWB_STATE] = uint8_t(ANDROID_CONTROL_AWB_STATE_CONVERGED);
    m[ANDROID_FLASH_STATE] = uint8_t(ANDROID_FLASH_STATE_UNAVAILABLE);
    m[ANDROID_LENS_APERTURE] = mAperture;
    m[ANDROID_LENS_FOCUS_DISTANCE] = af.second;
    m[ANDROID_LENS_STATE] = uint8_t(getAfLensState(af.first));
    m[ANDROID_REQUEST_PIPELINE_DEPTH] = uint8_t(4);
    m[ANDROID_SENSOR_FRAME_DURATION] = mFrameDurationNs;
    m[ANDROID_SENSOR_EXPOSURE_TIME] = mSensorExposureDurationNs;
    m[ANDROID_SENSOR_SENSITIVITY] = mSensorSensitivity;
    m[ANDROID_SENSOR_TIMESTAMP] = int64_t(0);
    m[ANDROID_SENSOR_NEUTRAL_COLOR_POINT] = kNeutralColorPoint;
    m[ANDROID_SENSOR_NOISE_PROFILE] = kSensorNoiseProfile;
    m[ANDROID_SENSOR_ROLLING_SHUTTER_SKEW] = kMinSensorExposureTimeNs;
    m[ANDROID_STATISTICS_SCENE_FLICKER] = uint8_t(ANDROID_STATISTICS_SCENE_FLICKER_NONE);

    if (!find_camera_metadata_ro_entry(raw, ANDROID_STATISTICS_LENS_SHADING_MAP_MODE, &entry)
        && (entry.data.u8[0] == ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_ON)) {
        m[ANDROID_STATISTICS_LENS_SHADING_MAP] = kLensShadingMap;
    }

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

CameraMetadata QemuCamera::updateCaptureResultMetadata() {
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

Span<const std::pair<int32_t, int32_t>> QemuCamera::getTargetFpsRanges() const {
    // ordered to satisfy testPreviewFpsRangeByCamera
    static const std::pair<int32_t, int32_t> targetFpsRanges[] = {
        {kMinFPS, kMedFPS},
        {kMedFPS, kMedFPS},
        {kMinFPS, kMaxFPS},
        {kMaxFPS, kMaxFPS},
    };

    return targetFpsRanges;
}

Span<const Rect<uint16_t>> QemuCamera::getAvailableThumbnailSizes() const {
    return {mParams.availableThumbnailResolutions.begin(),
            mParams.availableThumbnailResolutions.end()};
}

bool QemuCamera::isBackFacing() const {
    return mParams.isBackFacing;
}

Span<const float> QemuCamera::getAvailableApertures() const {
    static const float availableApertures[] = {
        1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0
    };

    return availableApertures;
}

std::tuple<int32_t, int32_t, int32_t> QemuCamera::getMaxNumOutputStreams() const {
    return {
        1,  // raw
        2,  // processed
        1,  // jpeg
    };
}

uint32_t QemuCamera::getAvailableCapabilitiesBitmap() const {
    return
        (1U << ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) |
        (1U << ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS) |
        (1U << ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW);
}

Span<const PixelFormat> QemuCamera::getSupportedPixelFormats() const {
    static const PixelFormat supportedPixelFormats[] = {
        PixelFormat::IMPLEMENTATION_DEFINED,
        PixelFormat::YCBCR_420_888,
        PixelFormat::RGBA_8888,
        PixelFormat::RAW16,
        PixelFormat::BLOB,
    };

    return {supportedPixelFormats};
}

int64_t QemuCamera::getMinFrameDurationNs() const {
    return kMinFrameDurationNs;
}

Rect<uint16_t> QemuCamera::getSensorSize() const {
    return mParams.sensorSize;
}

uint8_t QemuCamera::getSensorColorFilterArrangement() const {
    return ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB;
}

std::pair<int32_t, int32_t> QemuCamera::getSensorSensitivityRange() const {
    return {kMinSensorSensitivity, kMaxSensorSensitivity};
}

std::pair<int64_t, int64_t> QemuCamera::getSensorExposureTimeRange() const {
    return {kMinSensorExposureTimeNs, kMaxSensorExposureTimeNs};
}

int64_t QemuCamera::getSensorMaxFrameDuration() const {
    return kMaxSensorExposureTimeNs;
}

Span<const Rect<uint16_t>> QemuCamera::getSupportedResolutions() const {
    return {mParams.supportedResolutions.begin(), mParams.supportedResolutions.end()};
}

std::pair<int32_t, int32_t> QemuCamera::getDefaultTargetFpsRange(const RequestTemplate tpl) const {
    switch (tpl) {
    case RequestTemplate::PREVIEW:
    case RequestTemplate::VIDEO_RECORD:
    case RequestTemplate::VIDEO_SNAPSHOT:
        return {kMaxFPS, kMaxFPS};

    default:
        return {kMinFPS, kMaxFPS};
    }
}

float QemuCamera::getDefaultAperture() const {
    return kDefaultAperture;
}

int64_t QemuCamera::getDefaultSensorExpTime() const {
    return kDefaultSensorExposureTimeNs;
}

int64_t QemuCamera::getDefaultSensorFrameDuration() const {
    return kMinFrameDurationNs;
}

int32_t QemuCamera::getDefaultSensorSensitivity() const {
    return kDefaultSensorSensitivity;
}

}  // namespace hw
}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
