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
#include <mutex>
#include <unordered_map>

#include "Encoder.h"
#include "FrameProvider.h"

namespace android {
namespace webcam {

// Class which controls camera operation using sdk.
class SdkFrameProvider : public FrameProvider,
                         public EncoderCallback,
                         public std::enable_shared_from_this<SdkFrameProvider> {
  public:
    SdkFrameProvider(std::shared_ptr<BufferProducer> producer, CameraConfig config);
    ~SdkFrameProvider() override;

    void setStreamConfig() override;
    Status startStreaming() override;
    Status stopStreaming() final ;

    Status encodeImage(AHardwareBuffer* hardwareBuffer, long timestamp, int rotation) override;

    // EncoderCallback overrides
    void onEncoded(Buffer* producerBuffer, HardwareBufferDesc& hardwareBufferDesc,
                           bool success) override;

  private:
    Status getHardwareBufferDescFromHardwareBuffer(AHardwareBuffer* hardwareBuffer,
                                                   HardwareBufferDesc& ret);
    Status encodeImage(HardwareBufferDesc desc, jlong timestamp, jint rotation);
    void releaseHardwareBuffer(const HardwareBufferDesc& desc);

    std::mutex mMapLock;
    std::unordered_map<uint32_t, AHardwareBuffer*>
            mBufferIdToAHardwareBuffer;  // guarded by mMapLock
    uint32_t mNextBufferId = 0;          // guarded by mMapLock
    std::shared_ptr<Encoder> mEncoder;
};

}  // namespace webcam
}  // namespace android
