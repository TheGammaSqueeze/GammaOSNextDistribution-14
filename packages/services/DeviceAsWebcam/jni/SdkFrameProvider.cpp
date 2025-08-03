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

#include <DeviceAsWebcamServiceManager.h>
#include <linux/videodev2.h>
#include <log/log.h>
#include <utility>
#include <vector>

#include "Buffer.h"
#include "SdkFrameProvider.h"
#include "Utils.h"

namespace android {
namespace webcam {

SdkFrameProvider::SdkFrameProvider(std::shared_ptr<BufferProducer> producer, CameraConfig config)
    : FrameProvider(std::move(producer), config) {
    // Set stream configuration in java service.
    mEncoder = std::make_shared<Encoder>(config, this);
    if (!(mEncoder->isInited())) {
        ALOGE("%s: Encoder initialization failed", __FUNCTION__);
        return;
    }
    mEncoder->startEncoderThread();

    mInited = true;
}

void SdkFrameProvider::setStreamConfig() {
    DeviceAsWebcamServiceManager::kInstance->setStreamConfig(
            mConfig.fcc == V4L2_PIX_FMT_MJPEG, mConfig.width, mConfig.height, mConfig.fps);
}

Status SdkFrameProvider::startStreaming() {
    DeviceAsWebcamServiceManager::kInstance->startStreaming();
    return Status::OK;
}

Status SdkFrameProvider::stopStreaming() {
    DeviceAsWebcamServiceManager::kInstance->stopStreaming();
    return Status::OK;
}

Status SdkFrameProvider::encodeImage(AHardwareBuffer* hardwareBuffer, long timestamp,
                                     int rotation) {
    HardwareBufferDesc desc;
    if (getHardwareBufferDescFromHardwareBuffer(hardwareBuffer, desc) != Status::OK) {
        ALOGE("%s Couldn't get hardware buffer descriptor", __FUNCTION__);
        return Status::ERROR;
    }
    return encodeImage(desc, timestamp, rotation);
}

Status SdkFrameProvider::getHardwareBufferDescFromHardwareBuffer(AHardwareBuffer* hardwareBuffer,
                                                                 HardwareBufferDesc& ret) {
    if (hardwareBuffer == nullptr) {
        ALOGE("%s: Received null AHardwareBuffer.", __FUNCTION__);
        return Status::ERROR;
    }

    // Acquire to prevent Java from accidentally GC'ing the hardware buffer while it is in
    // use by SdkFrameProvider.
    AHardwareBuffer_acquire(hardwareBuffer);

    AHardwareBuffer_Planes planes{};
    if (AHardwareBuffer_lockPlanes(hardwareBuffer, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN,
                                   /*fence*/ -1, /*rect*/ nullptr, &planes) != 0) {
        ALOGE("%s: Couldn't get hardware buffer planes from hardware buffer", __FUNCTION__);
        AHardwareBuffer_release(hardwareBuffer);
        return Status::ERROR;
    }
    AHardwareBuffer_Desc desc{};
    AHardwareBuffer_describe(hardwareBuffer, &desc);

    uint32_t height = desc.height;
    uint32_t width = desc.width;
    ret.format = desc.format;
    ret.width = width;
    ret.height = height;

    if (ret.format == AHARDWAREBUFFER_FORMAT_Y8Cb8Cr8_420) {
      YuvHardwareBufferDesc yuvDesc;
      yuvDesc.yData = (uint8_t*) planes.planes[0].data;
      yuvDesc.yDataLength = planes.planes[0].rowStride * (height - 1) + width;
      yuvDesc.yRowStride = planes.planes[0].rowStride;

      yuvDesc.uData = (uint8_t*) planes.planes[1].data;
      yuvDesc.uDataLength = planes.planes[1].rowStride * (height / 2 - 1) +
          (planes.planes[1].pixelStride * width / 2 - 1) + 1;
      yuvDesc.uRowStride = planes.planes[1].rowStride;

      yuvDesc.vData = (uint8_t*) planes.planes[2].data;
      yuvDesc.vDataLength = planes.planes[2].rowStride * (height / 2 - 1) +
          (planes.planes[2].pixelStride * width / 2 - 1) + 1;
      yuvDesc.vRowStride = planes.planes[2].rowStride;

      // Pixel stride is the same for u and v planes
      yuvDesc.uvPixelStride = planes.planes[1].pixelStride;
      ret.bufferDesc = yuvDesc;
    } else if(ret.format == AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM) {
        ARGBHardwareBufferDesc argbDesc;
        argbDesc.buf =  (uint8_t*) planes.planes[0].data;
        argbDesc.rowStride =   planes.planes[0].rowStride;
        ret.bufferDesc = argbDesc;
    }
    {
        std::lock_guard<std::mutex> l(mMapLock);
        ret.bufferId = mNextBufferId++;
        mBufferIdToAHardwareBuffer[ret.bufferId] = hardwareBuffer;
    }
    return Status::OK;
}

Status SdkFrameProvider::encodeImage(HardwareBufferDesc desc, jlong timestamp, jint rotation) {
    Buffer* producerBuffer = mBufferProducer->getFreeBufferIfAvailable();
    if (producerBuffer == nullptr) {
        // Not available so don't compress
        ALOGV("%s: Producer buffer not available, returning", __FUNCTION__);
        releaseHardwareBuffer(desc);
        return Status::ERROR;
    }

    producerBuffer->setTimestamp(static_cast<uint64_t>(timestamp));
    // send to the Encoder.
    EncodeRequest encodeRequest(desc, producerBuffer, rotation);
    mEncoder->queueRequest(encodeRequest);
    return Status::OK;
}

void SdkFrameProvider::onEncoded(Buffer* producerBuffer, HardwareBufferDesc& desc, bool success) {
    releaseHardwareBuffer(desc);
    // Let Java know that HardwareBuffer is free to be cleaned up
    DeviceAsWebcamServiceManager::kInstance->returnImage(
            static_cast<long>(producerBuffer->getTimestamp()));

    if (!success) {
        ALOGE("%s Encoding was unsuccessful", __FUNCTION__);
        mBufferProducer->cancelBuffer(producerBuffer);
        return;
    }
    if (mBufferProducer->queueFilledBuffer(producerBuffer) != Status::OK) {
        ALOGE("%s Queueing filled buffer failed, something is wrong", __FUNCTION__);
        return;
    }
}

void SdkFrameProvider::releaseHardwareBuffer(const HardwareBufferDesc& desc) {
    // Unlock and release
    {
        std::lock_guard<std::mutex> l(mMapLock);
        auto it = mBufferIdToAHardwareBuffer.find(desc.bufferId);
        if (it == mBufferIdToAHardwareBuffer.end()) {
            // Continue anyway to let java call HardwareBuffer.close();
            ALOGE("Couldn't find AHardwareBuffer for buffer id %u, what ?", desc.bufferId);
        } else {
            AHardwareBuffer_unlock(it->second, /*fence*/ nullptr);
            AHardwareBuffer_release(it->second);
            mBufferIdToAHardwareBuffer.erase(it->first);
        }
    }
}

SdkFrameProvider::~SdkFrameProvider() {
    stopStreaming();
    mEncoder.reset();
}

}  // namespace webcam
}  // namespace android
