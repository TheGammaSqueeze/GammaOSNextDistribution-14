/*
 * Copyright 2020 The Android Open Source Project
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

#include "EvsEmulatedCamera.h"

#include <android-base/logging.h>
#include <android/hardware_buffer.h>
#include <ui/GraphicBufferAllocator.h>
#include <ui/GraphicBufferMapper.h>
#include <utils/SystemClock.h>

#include <filesystem>

namespace {

using ::android::hardware::automotive::evs::V1_1::EvsEventDesc;
using ::android::hardware::automotive::evs::V1_1::EvsEventType;

using BufferDesc_1_0 = ::android::hardware::automotive::evs::V1_0::BufferDesc;
using BufferDesc_1_1 = ::android::hardware::automotive::evs::V1_1::BufferDesc;

// Arbitrary limit on number of graphics buffers allowed to be allocated
// Safeguards against unreasonable resource consumption and provides a testable limit
const unsigned MAX_BUFFERS_IN_FLIGHT = 100;

uint32_t yuvToRgbx(const unsigned char Y, const unsigned char Uin, const unsigned char Vin) {
    const float U = Uin - 128.0f;
    const float V = Vin - 128.0f;

    const float Rf = Y + 1.140f * V;
    const float Gf = Y - 0.395f * U - 0.581f * V;
    const float Bf = Y + 2.032f * U;
    const unsigned char R = static_cast<unsigned char>(std::clamp(Rf, 0.0f, 255.0f));
    const unsigned char G = static_cast<unsigned char>(std::clamp(Gf, 0.0f, 255.0f));
    const unsigned char B = static_cast<unsigned char>(std::clamp(Bf, 0.0f, 255.0f));

    return ((R & 0xFF)) | ((G & 0xFF) << 8) | ((B & 0xFF) << 16) |
            0xFF000000;  // Fill the alpha channel with ones
}

void fillRGBAFromYUYV(const BufferDesc_1_1& dstBuff, uint8_t* dstData, void* srcData,
                      unsigned srcStride, unsigned srcHeight) {
    const AHardwareBuffer_Desc* pDesc =
            reinterpret_cast<const AHardwareBuffer_Desc*>(&dstBuff.buffer.description);
    unsigned width = pDesc->width;
    uint32_t* src = reinterpret_cast<uint32_t*>(srcData);
    uint32_t* dst = reinterpret_cast<uint32_t*>(dstData);
    unsigned srcStridePixels = srcStride / 2;
    unsigned dstStridePixels = pDesc->stride;

    const int srcRowPadding32 =
            srcStridePixels / 2 - width / 2;              // 2 bytes per pixel, 4 bytes per word
    const int dstRowPadding32 = dstStridePixels - width;  // 4 bytes per pixel, 4 bytes per word

    const unsigned numRows = std::min(srcHeight, pDesc->height);
    for (unsigned r = 0; r < numRows; ++r) {
        for (unsigned c = 0; c < width / 2; c++) {
            // Note:  we're walking two pixels at a time here (even/odd)
            uint32_t srcPixel = *src++;

            uint8_t Y1 = (srcPixel)&0xFF;
            uint8_t U = (srcPixel >> 8) & 0xFF;
            uint8_t Y2 = (srcPixel >> 16) & 0xFF;
            uint8_t V = (srcPixel >> 24) & 0xFF;

            // On the RGB output, we're writing one pixel at a time
            *(dst + 0) = yuvToRgbx(Y1, U, V);
            *(dst + 1) = yuvToRgbx(Y2, U, V);
            dst += 2;
        }

        // Skip over any extra data or end of row alignment padding
        src += srcRowPadding32;
        dst += dstRowPadding32;
    }
}

void fillBufferCopy(const BufferDesc_1_1& dstBuff, uint8_t* dst, void* srcData, unsigned srcStride,
                    unsigned srcHeight) {
    const AHardwareBuffer_Desc* pDesc =
            reinterpret_cast<const AHardwareBuffer_Desc*>(&dstBuff.buffer.description);

    // HAL_PIXEL_FORMAT_RGBA_8888 default output format
    const unsigned bytesPerPixel = 4;
    const unsigned dstStride = pDesc->stride * bytesPerPixel;

    // Simply copy the data, row by row, without the scaling.
    const unsigned copyStride = std::min(srcStride, dstStride);
    const unsigned numRows = std::min(srcHeight, pDesc->height);
    uint8_t* src = reinterpret_cast<uint8_t*>(srcData);
    for (auto r = 0; r < numRows; ++r) {
        memcpy(dst, src, copyStride);

        // Moves to the next row
        src += srcStride;
        dst += dstStride;
    }
}
}  // namespace

namespace android {
namespace automotive {
namespace evs {
namespace V1_1 {
namespace implementation {

EvsEmulatedCamera::EvsEmulatedCamera(const char* deviceName, const EmulatedCameraDesc& desc) :
      mFramesAllowed(0), mFramesInUse(0), mCaptureDeviceDesc(desc) {
    LOG(INFO) << "EvsEmulatedCamera instantiated";
    mDescription.v1.cameraId = deviceName;

    mVideo = new VideoCapture();

    // Default output buffer format.
    mFormat = HAL_PIXEL_FORMAT_RGBA_8888;

    // How we expect to use the gralloc buffers we'll exchange with our client
    mUsage = GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_SW_READ_RARELY | GRALLOC_USAGE_SW_WRITE_OFTEN;

    mDescription.v1.cameraId = deviceName;
}

EvsEmulatedCamera::~EvsEmulatedCamera() {
    LOG(INFO) << "EvsEmulatedCamera being destroyed";
    shutdown();
}

bool EvsEmulatedCamera::openDevice() {
    bool opened = false;
    if (mVideo) {
        opened = mVideo->open(mCaptureDeviceDesc.path, mCaptureDeviceDesc.interval);
    }

    return opened;
}

void EvsEmulatedCamera::shutdown() {
    LOG(INFO) << "EvsEmulatedCamera shutdown";

    // Make sure our output stream is cleaned up
    // (It really should be already)
    stopVideoStream();

    // Note:  Since stopVideoStream is blocking, no other threads can now be running

    // Close our video capture device
    mVideo->close();

    // Drop all the graphics buffers we've been using
    if (mBuffers.size() > 0) {
        GraphicBufferAllocator& alloc(GraphicBufferAllocator::get());
        for (auto&& rec : mBuffers) {
            if (rec.inUse) {
                LOG(WARNING) << "Releasing buffer despite remote ownership";
            }
            alloc.free(rec.handle);
            rec.handle = nullptr;
        }
        mBuffers.clear();
    }
}

// Methods from ::android::hardware::automotive::evs::V1_0::IEvsCamera follow.
Return<void> EvsEmulatedCamera::getCameraInfo(getCameraInfo_cb _hidl_cb) {
    LOG(DEBUG) << __FUNCTION__;

    // Send back our self description
    _hidl_cb(mDescription.v1);
    return {};
}

Return<EvsResult> EvsEmulatedCamera::setMaxFramesInFlight(uint32_t bufferCount) {
    LOG(DEBUG) << __FUNCTION__;
    std::scoped_lock<std::mutex> lock(mAccessLock);

    // Check whether underlying device is still open
    if (!mVideo->isOpen()) {
        LOG(WARNING) << "Ignoring startVideoStream call when camera has been lost.";
        return EvsResult::OWNERSHIP_LOST;
    }

    // We cannot function without at least one video buffer to send data
    if (bufferCount < 1) {
        LOG(ERROR) << "Ignoring setMaxFramesInFlight with less than one buffer requested";
        return EvsResult::INVALID_ARG;
    }

    // Update our internal state
    if (setAvailableFrames_Locked(bufferCount)) {
        return EvsResult::OK;
    } else {
        return EvsResult::BUFFER_NOT_AVAILABLE;
    }
}

Return<EvsResult> EvsEmulatedCamera::startVideoStream(const sp<IEvsCameraStream_1_0>& stream) {
    LOG(DEBUG) << __FUNCTION__;
    std::scoped_lock<std::mutex> lock(mAccessLock);

    // Check whether underlying device is still open
    if (!mVideo->isOpen()) {
        LOG(WARNING) << "Ignoring startVideoStream call when camera has been lost.";
        return EvsResult::OWNERSHIP_LOST;
    }

    if (mStream != nullptr) {
        LOG(ERROR) << "Ignoring startVideoStream call when a stream is already running.";
        return EvsResult::STREAM_ALREADY_RUNNING;
    }

    mStream = IEvsCameraStream_1_1::castFrom(stream).withDefault(nullptr);
    if (mStream == nullptr) {
        LOG(ERROR) << "A given IEvsCameraStream does not supoprt v1.1 interface.";
        return EvsResult::INVALID_ARG;
    }

    // If the client never indicated otherwise, configure ourselves for a single streaming buffer
    if (mFramesAllowed < 1) {
        if (!setAvailableFrames_Locked(1)) {
            LOG(ERROR) << "Failed to start stream because we couldn't get a graphics buffer";
            return EvsResult::BUFFER_NOT_AVAILABLE;
        }
    }

    if (!mVideo->startStream([this](VideoCapture*, imageBufferDesc* tgt, void* data) {
            this->forwardFrame(tgt, data);
        })) {
        // No need to hold onto this if we failed to start
        mStream = nullptr;
        LOG(ERROR) << "Underlying camera start stream failed";
        return EvsResult::UNDERLYING_SERVICE_ERROR;
    }

    return EvsResult::OK;
}

Return<void> EvsEmulatedCamera::doneWithFrame(const BufferDesc_1_0& buffer) {
    LOG(DEBUG) << __FUNCTION__;
    doneWithFrame_impl(buffer.bufferId, buffer.memHandle);

    return {};
}

Return<void> EvsEmulatedCamera::stopVideoStream() {
    LOG(DEBUG) << __FUNCTION__;

    // Tells the capture device to stop (and block until it does)
    mVideo->stopStream();

    if (mStream != nullptr) {
        // V1.1 client is waiting on STREAM_STOPPED event.
        std::scoped_lock<std::mutex> lock(mAccessLock);

        EvsEventDesc event;
        event.aType = EvsEventType::STREAM_STOPPED;
        auto result = mStream->notify(event);
        if (!result.isOk()) {
            LOG(ERROR) << "Error delivering end of stream event";
        }

        // Drop our reference to the client's stream receiver
        mStream = nullptr;
    }

    return {};
}

Return<int32_t> EvsEmulatedCamera::getExtendedInfo(uint32_t /*opaqueIdentifier*/) {
    LOG(DEBUG) << __FUNCTION__;
    // Return zero by default as required by the spec
    return 0;
}

Return<EvsResult> EvsEmulatedCamera::setExtendedInfo(uint32_t /*opaqueIdentifier*/,
                                                     int32_t /*opaqueValue*/) {
    LOG(DEBUG) << __FUNCTION__;
    std::scoped_lock<std::mutex> lock(mAccessLock);

    // If we've been displaced by another owner of the camera, then we can't do anything else
    if (!mVideo->isOpen()) {
        LOG(WARNING) << "Ignoring setExtendedInfo call when camera has been lost.";
        return EvsResult::OWNERSHIP_LOST;
    }

    // We don't store any device specific information in this implementation
    return EvsResult::INVALID_ARG;
}

// Methods from ::android::hardware::automotive::evs::V1_1::IEvsCamera follow.
Return<void> EvsEmulatedCamera::getCameraInfo_1_1(getCameraInfo_1_1_cb _hidl_cb) {
    LOG(DEBUG) << __FUNCTION__;

    // Send back our self description
    _hidl_cb(mDescription);
    return {};
}

Return<void> EvsEmulatedCamera::getPhysicalCameraInfo(const hidl_string& /*id*/,
                                                      getPhysicalCameraInfo_cb _hidl_cb) {
    LOG(DEBUG) << __FUNCTION__;

    // This method works exactly the same as getCameraInfo_1_1() in EVS HW module.
    _hidl_cb(mDescription);
    return {};
}

Return<EvsResult> EvsEmulatedCamera::doneWithFrame_1_1(const hidl_vec<BufferDesc_1_1>& buffers) {
    LOG(DEBUG) << __FUNCTION__;

    for (auto&& buffer : buffers) {
        doneWithFrame_impl(buffer.bufferId, buffer.buffer.nativeHandle);
    }

    return EvsResult::OK;
}

Return<EvsResult> EvsEmulatedCamera::pauseVideoStream() {
    return EvsResult::UNDERLYING_SERVICE_ERROR;
}

Return<EvsResult> EvsEmulatedCamera::resumeVideoStream() {
    return EvsResult::UNDERLYING_SERVICE_ERROR;
}

Return<EvsResult> EvsEmulatedCamera::setMaster() {
    // TODO(b/162946784): Implement this operation
    return EvsResult::OK;
}

Return<EvsResult> EvsEmulatedCamera::forceMaster(const sp<IEvsDisplay_1_0>&) {
    // TODO(b/162946784): Implement this operation
    return EvsResult::OK;
}

Return<EvsResult> EvsEmulatedCamera::unsetMaster() {
    // TODO(b/162946784): Implement this operation
    return EvsResult::OK;
}

Return<void> EvsEmulatedCamera::getParameterList(getParameterList_cb _hidl_cb) {
    // TODO(b/162946784): reads emulated controls from the configuration and
    //                    returns.
    hidl_vec<CameraParam> hidlCtrls;
    _hidl_cb(hidlCtrls);
    return {};
}

Return<void> EvsEmulatedCamera::getIntParameterRange(CameraParam /*id*/,
                                                     getIntParameterRange_cb _hidl_cb) {
    // TODO(b/162946784): reads emulated controls from the configuration and
    //                    returns.
    _hidl_cb(0, 0, 0);
    return {};
}

Return<void> EvsEmulatedCamera::setIntParameter(CameraParam /*id*/, int32_t /*value*/,
                                                setIntParameter_cb _hidl_cb) {
    // TODO(b/162946784): Implement this operation
    hidl_vec<int32_t> values;
    values.resize(1);
    _hidl_cb(EvsResult::INVALID_ARG, values);
    return {};
}

Return<void> EvsEmulatedCamera::getIntParameter(CameraParam /*id*/, getIntParameter_cb _hidl_cb) {
    // TODO(b/162946784): Implement this operation
    hidl_vec<int32_t> values;
    values.resize(1);
    _hidl_cb(EvsResult::INVALID_ARG, values);
    return {};
}

Return<EvsResult> EvsEmulatedCamera::setExtendedInfo_1_1(uint32_t opaqueIdentifier,
                                                         const hidl_vec<uint8_t>& opaqueValue) {
    mExtInfo.insert_or_assign(opaqueIdentifier, opaqueValue);
    return EvsResult::OK;
}

Return<void> EvsEmulatedCamera::getExtendedInfo_1_1(uint32_t opaqueIdentifier,
                                                    getExtendedInfo_1_1_cb _hidl_cb) {
    const auto it = mExtInfo.find(opaqueIdentifier);
    hidl_vec<uint8_t> value;
    auto status = EvsResult::OK;
    if (it == mExtInfo.end()) {
        status = EvsResult::INVALID_ARG;
    } else {
        value = mExtInfo[opaqueIdentifier];
    }

    _hidl_cb(status, value);
    return {};
}

Return<void> EvsEmulatedCamera::importExternalBuffers(const hidl_vec<BufferDesc_1_1>& buffers,
                                                      importExternalBuffers_cb _hidl_cb) {
    LOG(DEBUG) << __FUNCTION__;

    // If we've been displaced by another owner of the camera, then we can't do anything else
    if (!mVideo->isOpen()) {
        LOG(WARNING) << "Ignoring a request add external buffers "
                     << "when camera has been lost.";
        _hidl_cb(EvsResult::UNDERLYING_SERVICE_ERROR, mFramesAllowed);
        return {};
    }

    auto numBuffersToAdd = buffers.size();
    if (numBuffersToAdd < 1) {
        LOG(DEBUG) << "No buffers to add.";
        _hidl_cb(EvsResult::OK, mFramesAllowed);
        return {};
    }

    {
        std::scoped_lock<std::mutex> lock(mAccessLock);

        if (numBuffersToAdd > (MAX_BUFFERS_IN_FLIGHT - mFramesAllowed)) {
            numBuffersToAdd -= (MAX_BUFFERS_IN_FLIGHT - mFramesAllowed);
            LOG(WARNING) << "Exceed the limit on number of buffers.  " << numBuffersToAdd
                         << " buffers will be added only.";
        }

        GraphicBufferMapper& mapper = GraphicBufferMapper::get();
        const auto before = mFramesAllowed;
        for (auto i = 0; i < numBuffersToAdd; ++i) {
            auto& b = buffers[i];
            const AHardwareBuffer_Desc* pDesc =
                    reinterpret_cast<const AHardwareBuffer_Desc*>(&b.buffer.description);

            // Import a buffer to add
            buffer_handle_t memHandle = nullptr;
            status_t result = mapper.importBuffer(b.buffer.nativeHandle, pDesc->width,
                                                  pDesc->height, pDesc->layers, pDesc->format,
                                                  pDesc->usage, pDesc->stride, &memHandle);
            if (result != android::NO_ERROR || !memHandle) {
                LOG(WARNING) << "Failed to import a buffer " << b.bufferId;
                continue;
            }

            auto stored = false;
            for (auto&& rec : mBuffers) {
                if (rec.handle == nullptr) {
                    // Use this existing entry
                    rec.handle = memHandle;
                    rec.inUse = false;

                    stored = true;
                    break;
                }
            }

            if (!stored) {
                // Add a BufferRecord wrapping this handle to our set of available buffers
                mBuffers.emplace_back(memHandle);
            }

            ++mFramesAllowed;
        }

        _hidl_cb(EvsResult::OK, mFramesAllowed - before);
        return {};
    }
}

EvsResult EvsEmulatedCamera::doneWithFrame_impl(const uint32_t bufferId,
                                                const buffer_handle_t memHandle) {
    std::scoped_lock<std::mutex> lock(mAccessLock);

    // If we've been displaced by another owner of the camera, then we can't do anything else
    if (!mVideo->isOpen()) {
        LOG(WARNING) << "Ignoring doneWithFrame call when camera has been lost.";
    } else {
        if (memHandle == nullptr) {
            LOG(ERROR) << "Ignoring doneWithFrame called with null handle";
        } else if (bufferId >= mBuffers.size()) {
            LOG(ERROR) << "Ignoring doneWithFrame called with invalid bufferId " << bufferId
                       << " (max is " << mBuffers.size() - 1 << ")";
        } else if (!mBuffers[bufferId].inUse) {
            LOG(ERROR) << "Ignoring doneWithFrame called on frame " << bufferId
                       << " which is already free";
        } else {
            // Mark the frame as available
            mBuffers[bufferId].inUse = false;
            --mFramesInUse;

            // If this frame's index is high in the array, try to move it down
            // to improve locality after mFramesAllowed has been reduced.
            if (bufferId >= mFramesAllowed) {
                // Find an empty slot lower in the array (which should always exist in this case)
                for (auto&& rec : mBuffers) {
                    if (rec.handle == nullptr) {
                        rec.handle = mBuffers[bufferId].handle;
                        mBuffers[bufferId].handle = nullptr;
                        break;
                    }
                }
            }
        }
    }

    return EvsResult::OK;
}

bool EvsEmulatedCamera::setAvailableFrames_Locked(unsigned bufferCount) {
    if (bufferCount < 1) {
        LOG(ERROR) << "Rejecting a buffer request to set buffer count to zero";
        return false;
    }

    if (bufferCount > MAX_BUFFERS_IN_FLIGHT) {
        LOG(ERROR) << "Rejecting a buffer request in excess of internal limit";
        return false;
    }

    // Is an increase required?
    if (mFramesAllowed < bufferCount) {
        // An increase is required
        unsigned needed = bufferCount - mFramesAllowed;
        LOG(INFO) << "Allocating " << needed << " buffers for camera frames";

        unsigned added = increaseAvailableFrames_Locked(needed);
        if (added != needed) {
            // If we didn't add all the frames we needed, then roll back to the previous state
            LOG(ERROR) << "Rolling back to previous frame queue size";
            decreaseAvailableFrames_Locked(added);
            return false;
        }
    } else if (mFramesAllowed > bufferCount) {
        // A decrease is required
        unsigned framesToRelease = mFramesAllowed - bufferCount;
        LOG(INFO) << "Returning " << framesToRelease << " camera frame buffers";

        unsigned released = decreaseAvailableFrames_Locked(framesToRelease);
        if (released != framesToRelease) {
            // This shouldn't happen with a properly behaving client because the client
            // should only make this call after returning sufficient outstanding buffers
            // to allow a clean resize.
            LOG(ERROR) << "Buffer queue shrink failed -- too many buffers currently in use?";
        }
    }

    return true;
}

unsigned EvsEmulatedCamera::increaseAvailableFrames_Locked(unsigned numToAdd) {
    // Acquire the graphics buffer allocator
    GraphicBufferAllocator& alloc(GraphicBufferAllocator::get());

    unsigned added = 0;

    while (added < numToAdd) {
        unsigned pixelsPerLine;
        buffer_handle_t memHandle = nullptr;
        status_t result = alloc.allocate(mCaptureDeviceDesc.width, mCaptureDeviceDesc.height,
                                         mFormat, 1 /* layers */, mUsage, &memHandle,
                                         &pixelsPerLine, 0, "EvsEmulatedCamera");
        if (result != NO_ERROR) {
            LOG(ERROR) << "Error " << result << " allocating " << mCaptureDeviceDesc.width << " x "
                       << mCaptureDeviceDesc.height << " graphics buffer";
            break;
        }

        if (!memHandle) {
            LOG(ERROR) << "We didn't get a buffer handle back from the allocator";
            break;
        }

        if (mStride) {
            if (mStride != pixelsPerLine) {
                LOG(ERROR) << "We did not expect to get buffers with different strides!";
            }
        } else {
            // Gralloc defines stride in terms of pixels per line
            mStride = pixelsPerLine;
        }

        // Find a place to store the new buffer
        bool stored = false;
        for (auto&& rec : mBuffers) {
            if (rec.handle == nullptr) {
                // Use this existing entry
                rec.handle = memHandle;
                rec.inUse = false;
                stored = true;
                break;
            }
        }

        if (!stored) {
            // Add a BufferRecord wrapping this handle to our set of available buffers
            mBuffers.emplace_back(memHandle);
        }

        mFramesAllowed++;
        added++;
    }

    return added;
}

unsigned EvsEmulatedCamera::decreaseAvailableFrames_Locked(unsigned numToRemove) {
    // Acquire the graphics buffer allocator
    GraphicBufferAllocator& alloc(GraphicBufferAllocator::get());

    unsigned removed = 0;

    for (auto&& rec : mBuffers) {
        // Is this record not in use, but holding a buffer that we can free?
        if ((rec.inUse == false) && (rec.handle != nullptr)) {
            // Release buffer and update the record so we can recognize it as "empty"
            alloc.free(rec.handle);
            rec.handle = nullptr;

            mFramesAllowed--;
            removed++;

            if (removed == numToRemove) {
                break;
            }
        }
    }

    return removed;
}

// This is the async callback from the video camera that tells us a frame is ready
void EvsEmulatedCamera::forwardFrame(imageBufferDesc* pBufferInfo, void* pData) {
    bool readyForFrame = false;
    size_t idx = 0;

    // Lock scope for updating shared state
    {
        std::scoped_lock<std::mutex> lock(mAccessLock);

        // Are we allowed to issue another buffer?
        if (mFramesInUse >= mFramesAllowed) {
            // Can't do anything right now -- skip this frame
            LOG(WARNING) << "Skipped a frame because too many are in flight";
        } else {
            // Identify an available buffer to fill
            for (idx = 0; idx < mBuffers.size(); idx++) {
                if (!mBuffers[idx].inUse) {
                    if (mBuffers[idx].handle != nullptr) {
                        // Found an available record, so stop looking
                        break;
                    }
                }
            }

            if (idx >= mBuffers.size()) {
                // This shouldn't happen since we already checked mFramesInUse vs mFramesAllowed
                LOG(ERROR) << "Failed to find an available buffer slot";
            } else {
                // We're going to make the frame busy
                mBuffers[idx].inUse = true;
                readyForFrame = true;
                ++mFramesInUse;
            }
        }
    }

    if (!readyForFrame) {
        // We need to return the video buffer so it can capture a new frame
        mVideo->markFrameConsumed();
    } else {
        // Assemble the buffer description we'll transmit below
        BufferDesc_1_1 bufDesc_1_1 = {};
        AHardwareBuffer_Desc* pDesc =
                reinterpret_cast<AHardwareBuffer_Desc*>(&bufDesc_1_1.buffer.description);

        pDesc->width = mCaptureDeviceDesc.width;
        pDesc->height = mCaptureDeviceDesc.height;
        pDesc->layers = 1;
        pDesc->format = mFormat;
        pDesc->usage = mUsage;
        pDesc->stride = mStride;
        bufDesc_1_1.buffer.nativeHandle = mBuffers[idx].handle;
        bufDesc_1_1.bufferId = idx;
        bufDesc_1_1.deviceId = mDescription.v1.cameraId;
        // timestamp in microseconds.
        bufDesc_1_1.timestamp = systemTime(SYSTEM_TIME_MONOTONIC);

        // Lock our output buffer for writing
        void* targetPixels = nullptr;
        GraphicBufferMapper& mapper = GraphicBufferMapper::get();
        status_t result =
                mapper.lock(bufDesc_1_1.buffer.nativeHandle,
                            GRALLOC_USAGE_SW_WRITE_OFTEN | GRALLOC_USAGE_SW_READ_NEVER,
                            android::Rect(pDesc->width, pDesc->height), (void**)&targetPixels);

        // If we failed to lock the pixel buffer, we're about to crash, but log it first
        if (!targetPixels) {
            LOG(ERROR) << "Camera failed to gain access to image buffer for writing - "
                       << " status: " << statusToString(result) << " , error: " << strerror(errno);
        }

        // Transfer the video image into the output buffer, making any needed
        // format conversion along the way
        switch (pBufferInfo->info.format) {
            case V4L2_PIX_FMT_YUYV:
                fillRGBAFromYUYV(bufDesc_1_1, reinterpret_cast<uint8_t*>(targetPixels), pData,
                                 mVideo->getStride(), mVideo->getHeight());
                break;

            case V4L2_PIX_FMT_XBGR32:
                [[fallthrough]];
            case V4L2_PIX_FMT_ABGR32:
                fillBufferCopy(bufDesc_1_1, reinterpret_cast<uint8_t*>(targetPixels), pData,
                               mVideo->getStride(), mVideo->getHeight());
                break;

            default:
                LOG(ERROR) << "Source data is in unsupported format";
                break;
        }

        // Unlock the output buffer
        mapper.unlock(bufDesc_1_1.buffer.nativeHandle);

        // Give the video frame back to the underlying device for reuse
        // Note that we do this before making the client callback to give the
        // underlying camera more time to capture the next frame
        mVideo->markFrameConsumed();

        // Issue the (asynchronous) callback to the client -- can't be holding
        // the lock
        bool flag = false;
        {
            hidl_vec<BufferDesc_1_1> frames;
            frames.resize(1);
            frames[0] = bufDesc_1_1;
            auto result = mStream->deliverFrame_1_1(frames);
            flag = result.isOk();
        }

        if (flag) {
            LOG(DEBUG) << "Delivered " << bufDesc_1_1.buffer.nativeHandle.getNativeHandle()
                       << " as id " << bufDesc_1_1.bufferId;
        } else {
            // This can happen if the client dies and is likely unrecoverable.
            // To avoid consuming resources generating failing calls, we stop sending
            // frames.  Note, however, that the stream remains in the "STREAMING" state
            // until cleaned up on the main thread.
            LOG(ERROR) << "Frame delivery call failed in the transport layer.";

            // Since we didn't actually deliver it, mark the frame as available
            std::scoped_lock<std::mutex> lock(mAccessLock);
            mBuffers[idx].inUse = false;

            --mFramesInUse;
        }
    }
}

sp<EvsEmulatedCamera> EvsEmulatedCamera::Create(const char* deviceName,
                                                const EmulatedCameraDesc& desc) {
    LOG(INFO) << "Create " << deviceName;
    sp<EvsEmulatedCamera> pCamera = new EvsEmulatedCamera(deviceName, desc);
    if (pCamera->openDevice()) {
        return pCamera;
    } else {
        LOG(ERROR) << "Failed to open a video device.";
        return nullptr;
    }
}

}  // namespace implementation
}  // namespace V1_1
}  // namespace evs
}  // namespace automotive
}  // namespace android
