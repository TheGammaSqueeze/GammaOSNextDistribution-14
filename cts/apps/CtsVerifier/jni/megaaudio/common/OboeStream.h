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

#ifndef MEGA_COMMON_OBOESTREAM_H
#define MEGA_COMMON_OBOESTREAM_H

#include <cstdint>
#include <mutex>

#include <StreamBase.h>

class OboeStream: public StreamBase {
public:
    static Result OboeErrorToMegaAudioError(oboe::Result oboeError);

    virtual Result teardownStream() override;

    virtual Result startStream() override;
    virtual Result stopStream() override;

    // Oboe-specific
    oboe::StreamState getState() const;

    int getLastErrorCallbackResult();

    StreamBase::Result getTimeStamp(oboe::FrameTimestamp* timeStamp);

    int32_t getRoutedDeviceId();

    int32_t getSharingMode();
    int32_t getChannelCount();

protected:
    OboeStream(int32_t subtype) : mSubtype(subtype), mStreamStarted(false) {}

    // determine native back-end to use.
    // either SUB_TYPE_OBOE_DEFAULT, SUB_TYPE_OBOE_AAUDIO or SUB_TYPE_OBOE_OPENSL_ES
    int32_t mSubtype;

    // Oboe Audio Stream
    std::shared_ptr<oboe::AudioStream>  mAudioStream;
    bool    mStreamStarted;

    std::mutex mStreamLock;

    Result teardownStream_l();
};

#endif //MEGA_COMMON_OBOESTREAM_H
