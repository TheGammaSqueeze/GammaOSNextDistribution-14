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
#include <android/log.h>

#include <oboe/Oboe.h>

#include "OboeStream.h"

static const char * const TAG = "OboeStream(native)";

using namespace oboe;

StreamBase::Result OboeStream::OboeErrorToMegaAudioError(oboe::Result oboeError) {

    StreamBase::Result maErr = ERROR_UNKNOWN;

    switch (oboeError) {
        case oboe::Result::OK:
            maErr = OK;
            break;
        case oboe::Result::ErrorInternal:
            maErr = ERROR_UNKNOWN;
            break;
        case oboe::Result::ErrorClosed:
            maErr = ERROR_INVALID_STATE;
            break;
        default:
            maErr = ERROR_UNKNOWN;
            break;
    }

    return maErr;
}

StreamBase::Result OboeStream::teardownStream() {
    std::lock_guard<std::mutex> lock(mStreamLock);
    return teardownStream_l();
}

StreamBase::Result OboeStream::teardownStream_l() {
    // tear down the player
    if (mAudioStream == nullptr) {
        return ERROR_INVALID_STATE;
    } else {
        oboe::Result result = oboe::Result::OK;
        result = mAudioStream->stop();
        if (result == oboe::Result::OK) {
            result = mAudioStream->close();
        }
        mAudioStream = nullptr;
        return OboeErrorToMegaAudioError(result);
    }
}

StreamBase::Result OboeStream::startStream() {
    // Don't cover up (potential) bugs in AAudio
    oboe::OboeGlobals::setWorkaroundsEnabled(false);

    std::lock_guard<std::mutex> lock(mStreamLock);

    oboe::Result result = oboe::Result::ErrorInternal;
    if (mAudioStream != nullptr) {
        result = mAudioStream->requestStart();
        if (result != oboe::Result::OK){
            __android_log_print(
                    ANDROID_LOG_ERROR,
                    TAG,
                    "requestStart failed. Error: %s", convertToText(result));

            // clean up
            teardownStream_l();
        }
    }
    mStreamStarted = result == oboe::Result::OK;

    return OboeErrorToMegaAudioError(result);
}

StreamBase::Result OboeStream::stopStream() {
    std::lock_guard<std::mutex> lock(mStreamLock);

    Result errCode = ERROR_UNKNOWN;
    if (mAudioStream == nullptr) {
        errCode = ERROR_INVALID_STATE;
    } else {
        oboe::Result result = mAudioStream->stop();
        mStreamStarted = false;

        errCode = OboeErrorToMegaAudioError(result);
    }

    mStreamStarted = false;
    return errCode;
}

StreamBase::Result OboeStream::getTimeStamp(oboe::FrameTimestamp* timeStamp) {
    Result errCode = ERROR_UNKNOWN;
    if (mAudioStream == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "ERROR_INVALID_STATE");
        errCode = ERROR_INVALID_STATE;
    } else {
        ResultWithValue<oboe::FrameTimestamp> result = mAudioStream->getTimestamp(CLOCK_MONOTONIC);

        *timeStamp = result.value();

        errCode = OK;
    }

    return errCode;
}

oboe::StreamState OboeStream::getState() const {
    return mAudioStream != nullptr ? mAudioStream->getState() : oboe::StreamState::Unknown;
}

int OboeStream::getLastErrorCallbackResult() {
    return (int) (mAudioStream != nullptr
                    ? (int) mAudioStream->getLastErrorCallbackResult() : StreamBase::ERROR_INVALID_STATE);
}

int32_t OboeStream::getRoutedDeviceId() {
    return (int32_t) (mAudioStream != nullptr
                               ? (int32_t) mAudioStream->getDeviceId()
                               : ROUTING_DEVICE_NONE);
}

int32_t OboeStream::getSharingMode() {
    return (int32_t) (mAudioStream != nullptr
                               ? (int32_t) mAudioStream->getSharingMode()
                               : SHARING_MODE_INVALID);
}

int32_t OboeStream::getChannelCount() {
    return (int32_t) (mAudioStream != nullptr
                               ? (int32_t) mAudioStream->getChannelCount()
                               : -1);
}