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

#ifndef MEGA_COMMON_STREAMBASE_H
#define MEGA_COMMON_STREAMBASE_H

#include <cstdint>

class AudioSink;

class StreamBase {
public:
    //
    // Error Codes
    // These values must be kept in sync with the equivalent symbols in
    // org.hyphonate.megaaudio.common.Streambase.java
    //
    enum Result {
        OK = 0,
        ERROR_UNKNOWN = -1,
        ERROR_UNSUPPORTED = -2,
        ERROR_INVALID_STATE = -3
    };

    StreamBase() :
        mChannelCount(0),
        mSampleRate(0),
        mRouteDeviceId(ROUTING_DEVICE_NONE),
        mBufferSizeInFrames(-1) {}
    virtual ~StreamBase() {}

    //
    // Attributes
    //
    static const int32_t ROUTING_DEVICE_NONE    = -1;

    static const int32_t SHARING_MODE_INVALID   = -1;

    int32_t getNumBufferFrames() const { return mBufferSizeInFrames; }

    //
    // State
    //

    /**
     * Deinitializes the stream.
     * Concrete subclasses should stop the stream (is not already stopped) and deallocate any
     * resources being used by the stream.
     * The stream cannot be started again without another call to setupAudioStream().
     *
     * The expectation is that this method will be synchronous in concrete subclasses.
     * @return ERROR_NONE if successful, otherwise an error code
     */
    virtual Result teardownStream() = 0;

    /**
     * Begin the playback/recording process.
     *
     * In concrete subclasses, this may be either synchronous or asynchronous.
     * @return ERROR_NONE if successful, otherwise an error code
     */
    virtual Result startStream() = 0;

    /**
     * Stop the playback/recording process.
     *
     * In concrete subclasses, this may be either synchronous or asynchronous.
     */
    virtual Result stopStream() = 0;

protected:
    // Audio attributes
    int32_t mChannelCount;
    int32_t mSampleRate;
    int32_t mRouteDeviceId;
    int32_t mBufferSizeInFrames;

    // from org.hyphonate.megaaudio.common.BuilderBase
    static const int32_t SUB_TYPE_OBOE_AAUDIO       = 0x0001;
    static const int32_t SUB_TYPE_OBOE_OPENSL_ES    = 0x0002;
};

#endif // MEGA_COMMON_STREAMBASE_H

