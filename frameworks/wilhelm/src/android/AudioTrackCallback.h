/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef SL_PREFETCHEVENT_NONE // This is defined in slesl_allinclusive, which isn't guarded
#include "sles_allinclusive.h"
#endif

#include "media/AudioTrack.h"

void audioPlayer_dispatch_headAtEnd_lockPlay(CAudioPlayer*, bool, bool);

void audioTrack_handleUnderrun_lockPlay(CAudioPlayer* ap);
void audioTrack_handleMarker_lockPlay(CAudioPlayer* ap);
void audioTrack_handleNewPos_lockPlay(CAudioPlayer* ap);
size_t audioTrack_handleMoreData_lockPlay(CAudioPlayer* ap,
                                        const android::AudioTrack::Buffer& buffer);
//--------------------------------------------------------------------------------------------------
namespace android {
class AudioTrackCallback : public AudioTrack::IAudioTrackCallback {
  public:
    AudioTrackCallback(CAudioPlayer * player) : mAp(player) {}

    size_t onMoreData(const AudioTrack::Buffer& buffer) override {
        if (!android::CallbackProtector::enterCbIfOk(mAp->mCallbackProtector)) {
          // it is not safe to enter the callback (the track is about to go away)
          return buffer.size(); // duplicate existing behavior
        }
        size_t bytesCopied = audioTrack_handleMoreData_lockPlay(mAp, buffer);
        mAp->mCallbackProtector->exitCb();
        return bytesCopied;
      }

    void onUnderrun() override {
        if (!android::CallbackProtector::enterCbIfOk(mAp->mCallbackProtector)) {
          // it is not safe to enter the callback (the track is about to go away)
            return;
        }
        audioTrack_handleUnderrun_lockPlay(mAp);
        mAp->mCallbackProtector->exitCb();
    }

    void onLoopEnd([[maybe_unused]] int32_t loopsRemaining) override {
        SL_LOGE("Encountered loop end for CAudioPlayer %p", mAp);
    }
    void onMarker([[maybe_unused]] uint32_t markerPosition) override {
        if (!android::CallbackProtector::enterCbIfOk(mAp->mCallbackProtector)) {
          // it is not safe to enter the callback (the track is about to go away)
          return;
        }
        audioTrack_handleMarker_lockPlay(mAp);
        mAp->mCallbackProtector->exitCb();
    }

    void onNewPos([[maybe_unused]] uint32_t newPos) override {
        if (!android::CallbackProtector::enterCbIfOk(mAp->mCallbackProtector)) {
          // it is not safe to enter the callback (the track is about to go away)
          return;
        }
        audioTrack_handleNewPos_lockPlay(mAp);
        mAp->mCallbackProtector->exitCb();
    }
    void onBufferEnd() override {
        SL_LOGE("Encountered buffer end for CAudioPlayer %p", mAp);
    }
    // Ignore
    void onNewIAudioTrack() override {}
    void onStreamEnd() override {
        SL_LOGE("Encountered buffer end for CAudioPlayer %p", mAp);
    }
    void onNewTimestamp([[maybe_unused]] AudioTimestamp timestamp) {
        SL_LOGE("Encountered write more data for CAudioPlayer %p", mAp);
    }
    size_t onCanWriteMoreData([[maybe_unused]] const AudioTrack::Buffer& buffer) {
        SL_LOGE("Encountered write more data for CAudioPlayer %p", mAp);
        return 0;
    }

  private:
    AudioTrackCallback(const AudioTrackCallback&) = delete;
    AudioTrackCallback& operator=(const AudioTrackCallback&) = delete;
    CAudioPlayer* const mAp;
};
}  // namespace android
