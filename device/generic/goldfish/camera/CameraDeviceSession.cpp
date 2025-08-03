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

#define FAILURE_DEBUG_PREFIX "CameraDeviceSession"

#include <inttypes.h>

#include <chrono>
#include <memory>

#include <log/log.h>
#include <aidlcommonsupport/NativeHandle.h>
#include <utils/ThreadDefs.h>

#include <aidl/android/hardware/camera/device/ErrorCode.h>
#include <aidl/android/hardware/graphics/common/Dataspace.h>

#include "debug.h"
#include "CameraDeviceSession.h"
#include "CameraDevice.h"
#include "metadata_utils.h"

#include <system/camera_metadata.h>

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {

using aidl::android::hardware::camera::common::Status;
using aidl::android::hardware::camera::device::CaptureResult;
using aidl::android::hardware::camera::device::ErrorCode;
using aidl::android::hardware::camera::device::StreamRotation;
using aidl::android::hardware::camera::device::StreamType;

using aidl::android::hardware::graphics::common::Dataspace;

namespace {
constexpr char kClass[] = "CameraDeviceSession";

constexpr int64_t kOneSecondNs = 1000000000;
constexpr size_t kMsgQueueSize = 256 * 1024;

struct timespec timespecAddNanos(const struct timespec t, const int64_t addNs) {
    const lldiv_t r = lldiv(t.tv_nsec + addNs, kOneSecondNs);

    struct timespec tm;
    tm.tv_sec = t.tv_sec + r.quot;
    tm.tv_nsec = r.rem;

    return tm;
}

int64_t timespec2nanos(const struct timespec t) {
    return kOneSecondNs * t.tv_sec + t.tv_nsec;
}

const char* pixelFormatToStr(const PixelFormat fmt, char* buf, int bufSz) {
    switch (fmt) {
    case PixelFormat::UNSPECIFIED: return "UNSPECIFIED";
    case PixelFormat::IMPLEMENTATION_DEFINED: return "IMPLEMENTATION_DEFINED";
    case PixelFormat::YCBCR_420_888: return "YCBCR_420_888";
    case PixelFormat::RGBA_8888: return "RGBA_8888";
    case PixelFormat::BLOB: return "BLOB";
    default:
        snprintf(buf, bufSz, "0x%x", static_cast<uint32_t>(fmt));
        return buf;
    }
}

void notifyError(ICameraDeviceCallback* cb,
                 const int32_t frameNumber,
                 const int32_t errorStreamId,
                 const ErrorCode err) {
    using aidl::android::hardware::camera::device::NotifyMsg;
    using aidl::android::hardware::camera::device::ErrorMsg;
    using NotifyMsgTag = NotifyMsg::Tag;

    NotifyMsg msg;

    {
        ErrorMsg errorMsg;
        errorMsg.frameNumber = frameNumber;
        errorMsg.errorStreamId = errorStreamId;
        errorMsg.errorCode = err;
        msg.set<NotifyMsgTag::error>(errorMsg);
    }

    cb->notify({msg});
}

void notifyShutter(ICameraDeviceCallback* cb,
                   const int32_t frameNumber,
                   const int64_t shutterTimestamp,
                   const int64_t readoutTimestamp) {
    using aidl::android::hardware::camera::device::NotifyMsg;
    using aidl::android::hardware::camera::device::ShutterMsg;
    using NotifyMsgTag = NotifyMsg::Tag;

    NotifyMsg msg;

    {
        ShutterMsg shutterMsg;
        shutterMsg.frameNumber = frameNumber;
        shutterMsg.timestamp = shutterTimestamp;
        shutterMsg.readoutTimestamp = readoutTimestamp;
        msg.set<NotifyMsgTag::shutter>(shutterMsg);
    }

    cb->notify({msg});
}

CaptureResult makeCaptureResult(const int frameNumber,
                                CameraMetadata metadata,
                                std::vector<StreamBuffer> outputBuffers) {
    CaptureResult cr;
    cr.frameNumber = frameNumber;
    cr.result = std::move(metadata);
    cr.outputBuffers = std::move(outputBuffers);
    cr.inputBuffer.streamId = -1;
    cr.inputBuffer.bufferId = 0;
    cr.partialResult = cr.result.metadata.empty() ? 0 : 1;
    return cr;
}
}  // namespace

CameraDeviceSession::CameraDeviceSession(
        std::shared_ptr<CameraDevice> parent,
        std::shared_ptr<ICameraDeviceCallback> cb,
        hw::HwCamera& hwCamera)
         : mParent(std::move(parent))
         , mCb(std::move(cb))
         , mHwCamera(hwCamera)
         , mRequestQueue(kMsgQueueSize, false)
         , mResultQueue(kMsgQueueSize, false) {
    LOG_ALWAYS_FATAL_IF(!mRequestQueue.isValid());
    LOG_ALWAYS_FATAL_IF(!mResultQueue.isValid());
    mCaptureThread = std::thread(&CameraDeviceSession::captureThreadLoop, this);
    mDelayedCaptureThread = std::thread(&CameraDeviceSession::delayedCaptureThreadLoop, this);
}

CameraDeviceSession::~CameraDeviceSession() {
    closeImpl();

    mCaptureRequests.cancel();
    mDelayedCaptureResults.cancel();
    mCaptureThread.join();
    mDelayedCaptureThread.join();
}

ScopedAStatus CameraDeviceSession::close() {
    closeImpl();
    return ScopedAStatus::ok();
}

ScopedAStatus CameraDeviceSession::configureStreams(
        const StreamConfiguration& cfg,
        std::vector<HalStream>* halStreamsOut) {
    ALOGD("%s:%s:%d cfg={ "
          ".streams.size=%zu, .operationMode=%u, .cfg.sessionParams.size()=%zu, "
          " .streamConfigCounter=%d, .multiResolutionInputImage=%s }",
          kClass, __func__, __LINE__,
          cfg.streams.size(), static_cast<uint32_t>(cfg.operationMode),
          cfg.sessionParams.metadata.size(), cfg.streamConfigCounter,
          (cfg.multiResolutionInputImage ? "true" : "false"));

    for (const auto& s : cfg.streams) {
        const uint32_t dataspaceBits = static_cast<uint32_t>(s.dataSpace);
        const uint32_t dataspaceLow = dataspaceBits & 0xFFFF;
        const uint32_t dataspaceS =
            (dataspaceBits & static_cast<uint32_t>(Dataspace::STANDARD_MASK)) >>
            static_cast<uint32_t>(Dataspace::STANDARD_SHIFT);
        const uint32_t dataspaceT =
            (dataspaceBits & static_cast<uint32_t>(Dataspace::TRANSFER_MASK)) >>
            static_cast<uint32_t>(Dataspace::TRANSFER_SHIFT);
        const uint32_t dataspaceR =
            (dataspaceBits & static_cast<uint32_t>(Dataspace::RANGE_MASK)) >>
            static_cast<uint32_t>(Dataspace::RANGE_SHIFT);

        char pixelFormatStrBuf[16];

        ALOGD("%s:%s:%d stream={ .id=%d, "
              ".streamType=%u, .width=%d, .height=%d, .format=%s, .usage=0x%" PRIx64 ", "
              ".dataSpace={ .low=0x%x, .s=%u, .t=%u, .r=%u }, .rotation=%u, .physicalCameraId='%s', .bufferSize=%d, "
              ".groupId=%d, .dynamicRangeProfile=0x%x }", kClass, __func__, __LINE__,
              s.id, static_cast<unsigned>(s.streamType), s.width, s.height,
              pixelFormatToStr(s.format, pixelFormatStrBuf, sizeof(pixelFormatStrBuf)),
              static_cast<uint64_t>(s.usage),
              dataspaceLow, dataspaceS, dataspaceT, dataspaceR,
              static_cast<unsigned>(s.rotation),
              s.physicalCameraId.c_str(), s.bufferSize, s.groupId,
              static_cast<unsigned>(s.dynamicRangeProfile)
        );
    }

    auto [status, halStreams] = configureStreamsStatic(cfg, mHwCamera);
    if (status != Status::OK) {
        return toScopedAStatus(status);
    }

    const size_t nStreams = cfg.streams.size();
    LOG_ALWAYS_FATAL_IF(halStreams.size() != nStreams);

    if (mHwCamera.configure(cfg.sessionParams, nStreams,
                            cfg.streams.data(), halStreams.data())) {
        mStreamBufferCache.clearStreamInfo();
        *halStreamsOut = std::move(halStreams);
        return ScopedAStatus::ok();
    } else {
        return toScopedAStatus(FAILURE(Status::INTERNAL_ERROR));
    }
}

ScopedAStatus CameraDeviceSession::constructDefaultRequestSettings(
        const RequestTemplate tpl,
        CameraMetadata* metadata) {
    auto maybeMetadata = serializeCameraMetadataMap(
        mParent->constructDefaultRequestSettings(tpl));

    if (maybeMetadata) {
        *metadata = std::move(maybeMetadata.value());
        return ScopedAStatus::ok();
    } else {
        return toScopedAStatus(Status::INTERNAL_ERROR);
    }
}

ScopedAStatus CameraDeviceSession::flush() {
    flushImpl(std::chrono::steady_clock::now());
    return ScopedAStatus::ok();
}

ScopedAStatus CameraDeviceSession::getCaptureRequestMetadataQueue(
        MQDescriptor<int8_t, SynchronizedReadWrite>* desc) {
    *desc = mRequestQueue.dupeDesc();
    return ScopedAStatus::ok();
}

ScopedAStatus CameraDeviceSession::getCaptureResultMetadataQueue(
        MQDescriptor<int8_t, SynchronizedReadWrite>* desc) {
    *desc = mResultQueue.dupeDesc();
    return ScopedAStatus::ok();
}

ScopedAStatus CameraDeviceSession::isReconfigurationRequired(
        const CameraMetadata& /*oldParams*/,
        const CameraMetadata& /*newParams*/,
        bool* resultOut) {
    *resultOut = false;
    return ScopedAStatus::ok();
}

ScopedAStatus CameraDeviceSession::processCaptureRequest(
        const std::vector<CaptureRequest>& requests,
        const std::vector<BufferCache>& cachesToRemove,
        int32_t* countOut) {
    for (const BufferCache& bc : cachesToRemove) {
        mStreamBufferCache.remove(bc.bufferId);
    }

    int count = 0;
    for (const CaptureRequest& r : requests) {
        const Status s = processOneCaptureRequest(r);
        if (s == Status::OK) {
            ++count;
        } else {
            *countOut = count;
            return toScopedAStatus(s);
        }
    }

    *countOut = count;
    return ScopedAStatus::ok();
}

ScopedAStatus CameraDeviceSession::signalStreamFlush(
        const std::vector<int32_t>& /*streamIds*/,
        const int32_t /*streamConfigCounter*/) {
    return toScopedAStatus(FAILURE(Status::OPERATION_NOT_SUPPORTED));
}

ScopedAStatus CameraDeviceSession::switchToOffline(
        const std::vector<int32_t>& /*streamsToKeep*/,
        CameraOfflineSessionInfo* /*offlineSessionInfo*/,
        std::shared_ptr<ICameraOfflineSession>* /*session*/) {
    return toScopedAStatus(FAILURE(Status::OPERATION_NOT_SUPPORTED));
}

ScopedAStatus CameraDeviceSession::repeatingRequestEnd(
        const int32_t /*frameNumber*/,
        const std::vector<int32_t>& /*streamIds*/) {
    return ScopedAStatus::ok();
}

bool CameraDeviceSession::isStreamCombinationSupported(const StreamConfiguration& cfg,
                                                       hw::HwCamera& hwCamera) {
    const auto [status, unused] = configureStreamsStatic(cfg, hwCamera);
    return status == Status::OK;
}

void CameraDeviceSession::closeImpl() {
    flushImpl(std::chrono::steady_clock::now());
    mHwCamera.close();
}

void CameraDeviceSession::flushImpl(const std::chrono::steady_clock::time_point start) {
    mFlushing = true;
    waitFlushingDone(start);
    mFlushing = false;
}

int CameraDeviceSession::waitFlushingDone(const std::chrono::steady_clock::time_point start) {
    std::unique_lock<std::mutex> lock(mNumBuffersInFlightMtx);
    if (mNumBuffersInFlight == 0) {
        return 0;
    }

    using namespace std::chrono_literals;
    constexpr int kRecommendedDeadlineMs = 100;
    constexpr int kFatalDeadlineMs = 1000;
    const auto fatalDeadline = start + (1ms * kFatalDeadlineMs);

    const auto checkIfNoBuffersInFlight = [this](){ return mNumBuffersInFlight == 0; };

    if (mNoBuffersInFlight.wait_until(lock, fatalDeadline, checkIfNoBuffersInFlight)) {
        const int waitedForMs = (std::chrono::steady_clock::now() - start) / 1ms;

        if (waitedForMs > kRecommendedDeadlineMs) {
            ALOGW("%s:%s:%d: flushing took %dms, Android "
                  "recommends %dms latency and requires no more than %dms",
                  kClass, __func__, __LINE__, waitedForMs, kRecommendedDeadlineMs,
                  kFatalDeadlineMs);
        }

        return waitedForMs;
    } else {
        LOG_ALWAYS_FATAL("%s:%s:%d: %zu buffers are still in "
                         "flight after %dms of waiting, some buffers might have "
                         "leaked", kClass, __func__, __LINE__, mNumBuffersInFlight,
                         kFatalDeadlineMs);
    }
}

std::pair<Status, std::vector<HalStream>>
CameraDeviceSession::configureStreamsStatic(const StreamConfiguration& cfg,
                                            hw::HwCamera& hwCamera) {
    if (cfg.multiResolutionInputImage) {
        return {FAILURE(Status::OPERATION_NOT_SUPPORTED), {}};
    }

    const size_t streamsSize = cfg.streams.size();
    if (!streamsSize) {
        return {FAILURE(Status::ILLEGAL_ARGUMENT), {}};
    }

    std::vector<HalStream> halStreams;
    halStreams.reserve(streamsSize);

    for (const auto& s : cfg.streams) {
        if (s.streamType == StreamType::INPUT) {
            return {FAILURE(Status::OPERATION_NOT_SUPPORTED), {}};
        }

        if (s.width <= 0) {
            return {FAILURE(Status::ILLEGAL_ARGUMENT), {}};
        }

        if (s.height <= 0) {
            return {FAILURE(Status::ILLEGAL_ARGUMENT), {}};
        }

        if (s.rotation != StreamRotation::ROTATION_0) {
            return {FAILURE(Status::ILLEGAL_ARGUMENT), {}};
        }

        if (s.bufferSize < 0) {
            return {FAILURE(Status::ILLEGAL_ARGUMENT), {}};
        }

        HalStream hs;
        std::tie(hs.overrideFormat, hs.producerUsage,
                 hs.overrideDataSpace, hs.maxBuffers) =
            hwCamera.overrideStreamParams(s.format, s.usage, s.dataSpace);

        if (hs.maxBuffers <= 0) {
            switch (hs.maxBuffers) {
            case hw::HwCamera::kErrorBadFormat:
                ALOGE("%s:%s:%d unexpected format=0x%" PRIx32,
                      kClass, __func__, __LINE__, static_cast<uint32_t>(s.format));
                return {Status::ILLEGAL_ARGUMENT, {}};

            case hw::HwCamera::kErrorBadUsage:
                ALOGE("%s:%s:%d unexpected usage=0x%" PRIx64
                      " for format=0x%" PRIx32 " and dataSpace=0x%" PRIx32,
                      kClass, __func__, __LINE__, static_cast<uint64_t>(s.usage),
                      static_cast<uint32_t>(s.format),
                      static_cast<uint32_t>(s.dataSpace));
                return {Status::ILLEGAL_ARGUMENT, {}};

            case hw::HwCamera::kErrorBadDataspace:
                ALOGE("%s:%s:%d unexpected dataSpace=0x%" PRIx32
                      " for format=0x%" PRIx32 " and usage=0x%" PRIx64,
                      kClass, __func__, __LINE__, static_cast<uint32_t>(s.dataSpace),
                      static_cast<uint32_t>(s.format),
                      static_cast<uint64_t>(s.usage));
                return {Status::ILLEGAL_ARGUMENT, {}};

            default:
                ALOGE("%s:%s:%d something is not right for format=0x%" PRIx32
                      " usage=0x%" PRIx64 " and dataSpace=0x%" PRIx32,
                      kClass, __func__, __LINE__, static_cast<uint32_t>(s.format),
                      static_cast<uint64_t>(s.usage),
                      static_cast<uint32_t>(s.dataSpace));
                return {Status::ILLEGAL_ARGUMENT, {}};
            }
        }

        hs.id = s.id;
        hs.consumerUsage = static_cast<BufferUsage>(0);
        hs.physicalCameraId = s.physicalCameraId;
        hs.supportOffline = false;

        halStreams.push_back(std::move(hs));
    }

    return {Status::OK, std::move(halStreams)};
}

Status CameraDeviceSession::processOneCaptureRequest(const CaptureRequest& request) {
    // If inputBuffer is valid, the request is for reprocessing
    if (!isAidlNativeHandleEmpty(request.inputBuffer.buffer)) {
        return FAILURE(Status::OPERATION_NOT_SUPPORTED);
    }

    if (request.inputWidth || request.inputHeight) {
        return FAILURE(Status::OPERATION_NOT_SUPPORTED);
    }

    if (!request.physicalCameraSettings.empty()) {
        return FAILURE(Status::OPERATION_NOT_SUPPORTED);
    }

    const size_t outputBuffersSize = request.outputBuffers.size();

    if (outputBuffersSize == 0) {
        return FAILURE(Status::ILLEGAL_ARGUMENT);
    }

    HwCaptureRequest hwReq;

    if (request.fmqSettingsSize < 0) {
        return FAILURE(Status::ILLEGAL_ARGUMENT);
    } else if (request.fmqSettingsSize > 0) {
        CameraMetadata tmp;
        tmp.metadata.resize(request.fmqSettingsSize);

        if (mRequestQueue.read(
                reinterpret_cast<int8_t*>(tmp.metadata.data()),
                request.fmqSettingsSize)) {
            hwReq.metadataUpdate = std::move(tmp);
        } else {
            return FAILURE(Status::INTERNAL_ERROR);
        }
    } else if (!request.settings.metadata.empty()) {
        hwReq.metadataUpdate = request.settings;
    }

    hwReq.buffers.resize(outputBuffersSize);
    for (size_t i = 0; i < outputBuffersSize; ++i) {
        hwReq.buffers[i] = mStreamBufferCache.update(request.outputBuffers[i]);
    }

    {
        std::lock_guard<std::mutex> guard(mNumBuffersInFlightMtx);
        mNumBuffersInFlight += outputBuffersSize;
    }

    hwReq.frameNumber = request.frameNumber;

    if (mCaptureRequests.put(&hwReq)) {
        return Status::OK;
    } else {
        disposeCaptureRequest(std::move(hwReq));
        return FAILURE(Status::INTERNAL_ERROR);
    }
}

void CameraDeviceSession::captureThreadLoop() {
    setThreadPriority(SP_FOREGROUND, ANDROID_PRIORITY_VIDEO);

    struct timespec nextFrameT;
    clock_gettime(CLOCK_MONOTONIC, &nextFrameT);
    while (true) {
        std::optional<HwCaptureRequest> maybeReq = mCaptureRequests.get();
        if (maybeReq.has_value()) {
            HwCaptureRequest& req = maybeReq.value();
            if (mFlushing) {
                disposeCaptureRequest(std::move(req));
            } else {
                nextFrameT = captureOneFrame(nextFrameT, std::move(req));
            }
        } else {
            break;
        }
    }
}

struct timespec CameraDeviceSession::captureOneFrame(struct timespec nextFrameT,
                                                     HwCaptureRequest req) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    if (std::make_pair(now.tv_sec, now.tv_nsec) <
            std::make_pair(nextFrameT.tv_sec, nextFrameT.tv_nsec)) {
        clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &nextFrameT, nullptr);
    } else {
        nextFrameT = now;
    }

    const int32_t frameNumber = req.frameNumber;

    auto [frameDurationNs, exposureDurationNs, metadata,
          outputBuffers, delayedOutputBuffers] =
        mHwCamera.processCaptureRequest(std::move(req.metadataUpdate),
                                        {req.buffers.begin(), req.buffers.end()});

    for (hw::DelayedStreamBuffer& dsb : delayedOutputBuffers) {
        DelayedCaptureResult dcr;
        dcr.delayedBuffer = std::move(dsb);
        dcr.frameNumber = frameNumber;
        if (!mDelayedCaptureResults.put(&dcr)) {
            // `delayedBuffer(false)` only releases the buffer (fast).
            outputBuffers.push_back(dcr.delayedBuffer(false));
        }
    }

    const int64_t shutterTimestampNs = timespec2nanos(nextFrameT);
    notifyShutter(&*mCb, frameNumber, shutterTimestampNs, shutterTimestampNs + exposureDurationNs);
    metadataSetShutterTimestamp(&metadata, shutterTimestampNs);
    consumeCaptureResult(makeCaptureResult(frameNumber,
        std::move(metadata), std::move(outputBuffers)));

    if (frameDurationNs > 0) {
        nextFrameT = timespecAddNanos(nextFrameT, frameDurationNs);
    } else {
        notifyError(&*mCb, frameNumber, -1, ErrorCode::ERROR_DEVICE);
    }

    return nextFrameT;
}

void CameraDeviceSession::delayedCaptureThreadLoop() {
    while (true) {
        std::optional<DelayedCaptureResult> maybeDCR = mDelayedCaptureResults.get();
        if (maybeDCR.has_value()) {
            const DelayedCaptureResult& dcr = maybeDCR.value();

            // `dcr.delayedBuffer(true)` is expected to be slow, so we do not
            // produce too much IPC traffic here. This also returns buffes to
            // the framework earlier to reuse in capture requests.
            std::vector<StreamBuffer> outputBuffers(1);
            outputBuffers.front() = dcr.delayedBuffer(!mFlushing);
            consumeCaptureResult(makeCaptureResult(dcr.frameNumber,
                {}, std::move(outputBuffers)));
        } else {
            break;
        }
    }
}

void CameraDeviceSession::disposeCaptureRequest(HwCaptureRequest req) {
    notifyError(&*mCb, req.frameNumber, -1, ErrorCode::ERROR_REQUEST);

    const size_t reqBuffersSize = req.buffers.size();

    {
        std::vector<StreamBuffer> outputBuffers(reqBuffersSize);

        for (size_t i = 0; i < reqBuffersSize; ++i) {
            CachedStreamBuffer* csb = req.buffers[i];
            LOG_ALWAYS_FATAL_IF(!csb);  // otherwise mNumBuffersInFlight will be hard
            outputBuffers[i] = csb->finish(false);
        }

        std::vector<CaptureResult> crs(1);
        crs.front() = makeCaptureResult(req.frameNumber, {},
                                        std::move(outputBuffers));

        std::lock_guard<std::mutex> guard(mResultQueueMutex);
        mCb->processCaptureResult(std::move(crs));
    }

    notifyBuffersReturned(reqBuffersSize);
}

void CameraDeviceSession::consumeCaptureResult(CaptureResult cr) {
    const size_t numBuffers = cr.outputBuffers.size();

    {
        std::lock_guard<std::mutex> guard(mResultQueueMutex);
        const size_t metadataSize = cr.result.metadata.size();
        if ((metadataSize > 0) && mResultQueue.write(
                reinterpret_cast<int8_t*>(cr.result.metadata.data()),
                metadataSize)) {
            cr.fmqResultSize = metadataSize;
            cr.result.metadata.clear();
        }

        std::vector<CaptureResult> crs(1);
        crs.front() = std::move(cr);
        mCb->processCaptureResult(std::move(crs));
    }

    notifyBuffersReturned(numBuffers);
}

void CameraDeviceSession::notifyBuffersReturned(const size_t numBuffersToReturn) {
    std::lock_guard<std::mutex> guard(mNumBuffersInFlightMtx);
    LOG_ALWAYS_FATAL_IF(mNumBuffersInFlight < numBuffersToReturn,
                        "mNumBuffersInFlight=%zu numBuffersToReturn=%zu",
                        mNumBuffersInFlight, numBuffersToReturn);

    mNumBuffersInFlight -= numBuffersToReturn;

    if (mNumBuffersInFlight == 0) {
        mNoBuffersInFlight.notify_all();
    }
}

}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
