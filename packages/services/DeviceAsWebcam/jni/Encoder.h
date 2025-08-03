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
#include <stdlib.h>
#include <atomic>
#include <condition_variable>
#include <mutex>
#include <queue>
#include <thread>

#include <android/hardware_buffer.h>
#include <jpeglib.h>

#include "Buffer.h"
#include "FrameProvider.h"
#include "Utils.h"

// Manages converting from formats available directly from the camera to standardized formats that
// transport mechanism eg: UVC over USB + host can understand.
namespace android {
namespace webcam {

struct EncodeRequest {
    EncodeRequest() = default;
    EncodeRequest(HardwareBufferDesc& buffer, Buffer* producerBuffer, uint32_t rotation)
        : srcBuffer(buffer), dstBuffer(producerBuffer), rotationDegrees(rotation) {}
    HardwareBufferDesc srcBuffer;
    Buffer* dstBuffer = nullptr;
    uint32_t rotationDegrees = 0;
};

struct I420 {
    std::unique_ptr<uint8_t[]> y;
    std::unique_ptr<uint8_t[]> u;
    std::unique_ptr<uint8_t[]> v;
    uint32_t yRowStride = 0;
    uint32_t uRowStride = 0;
    uint32_t vRowStride = 0;
};

class EncoderCallback {
  public:
    // Callback called by encoder into client when encoding is finished.
    virtual void onEncoded(Buffer* producerBuffer, HardwareBufferDesc& srcBuffer, bool success) = 0;
    virtual ~EncoderCallback() = default;
};

// Encoder for YUV_420_88 -> YUY2 / MJPEG conversion.
class Encoder {
  public:
    Encoder(CameraConfig& config, EncoderCallback* cb);
    ~Encoder();

    [[nodiscard]] bool isInited() const;
    void startEncoderThread();
    void queueRequest(EncodeRequest& request);

  private:
    // Main loop of the encoder thread. Calls EncoderCallback.onEncoded which might call back into
    // java, so encoder thread must be registered with the JVM.
    void encodeThreadLoop();

    void encode(EncodeRequest& request);

    int convertToI420(EncodeRequest& request);
    uint32_t i420ToJpeg(EncodeRequest& request);

    void encodeToMJpeg(EncodeRequest& request);
    void encodeToYUYV(EncodeRequest& request);

    static bool checkError(const char* msg, j_common_ptr jpeg_error_info_);

    std::mutex mRequestLock;
    std::queue<EncodeRequest> mRequestQueue;    // guarded by mRequestLock
    std::condition_variable mRequestCondition;  // guarded by mRequestLock

    std::thread mEncoderThread;
    CameraConfig mConfig;
    EncoderCallback* mCb = nullptr;
    volatile bool mContinueEncoding = true;
    bool mInited = false;
    I420 mI420;
};

}  // namespace webcam
}  // namespace android
