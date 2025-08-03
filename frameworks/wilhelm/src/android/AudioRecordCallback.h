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

#include "media/AudioRecord.h"

void audioRecorder_handleOverrun_lockRecord(CAudioRecorder* ar);
void audioRecorder_handleNewPos_lockRecord(CAudioRecorder* ar);
void audioRecorder_handleMarker_lockRecord(CAudioRecorder* ar);
size_t audioRecorder_handleMoreData_lockRecord(CAudioRecorder* ar,
                                               const android::AudioRecord::Buffer&);
//--------------------------------------------------------------------------------------------------
namespace android {

class AudioRecordCallback : public android::AudioRecord::IAudioRecordCallback {
  public:
    AudioRecordCallback(CAudioRecorder * audioRecorder) : mAr(audioRecorder) {}
    AudioRecordCallback(const AudioRecordCallback&) = delete;
    AudioRecordCallback& operator=(const AudioRecordCallback&) = delete;

  private:
    size_t onMoreData(const android::AudioRecord::Buffer& buffer) override {
        if (!android::CallbackProtector::enterCbIfOk(mAr->mCallbackProtector)) {
            // it is not safe to enter the callback (the track is about to go away)
            return buffer.size(); // replicate existing behavior
        }
        size_t bytesRead = audioRecorder_handleMoreData_lockRecord(mAr, buffer);
        mAr->mCallbackProtector->exitCb();
        return bytesRead;
    }


    void onOverrun() override {
        if (!android::CallbackProtector::enterCbIfOk(mAr->mCallbackProtector)) {
            // it is not safe to enter the callback (the track is about to go away)
            return;
        }
        audioRecorder_handleOverrun_lockRecord(mAr);
        mAr->mCallbackProtector->exitCb();
    }
    void onMarker(uint32_t) override {
        if (!android::CallbackProtector::enterCbIfOk(mAr->mCallbackProtector)) {
            // it is not safe to enter the callback (the track is about to go away)
            return;
        }

        audioRecorder_handleMarker_lockRecord(mAr);
        mAr->mCallbackProtector->exitCb();
    }
    void onNewPos(uint32_t) override {
        if (!android::CallbackProtector::enterCbIfOk(mAr->mCallbackProtector)) {
            // it is not safe to enter the callback (the track is about to go away)
            return;
        }

        audioRecorder_handleNewPos_lockRecord(mAr);
        mAr->mCallbackProtector->exitCb();
    }
    CAudioRecorder * const mAr;
};

} // namespace android
