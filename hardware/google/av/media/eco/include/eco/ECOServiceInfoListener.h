/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef ANDROID_MEDIA_ECO_SERVICE_INFO_LISTENER_H_
#define ANDROID_MEDIA_ECO_SERVICE_INFO_LISTENER_H_

#include <aidl/android/media/eco/BnECOServiceInfoListener.h>
#include <aidl/android/media/eco/IECOSession.h>

#include <condition_variable>
#include <memory>
#include <mutex>
#include <thread>

#include "ECOData.h"

namespace android {
namespace media {
namespace eco {

using aidl::android::media::eco::BnECOServiceInfoListener;
using aidl::android::media::eco::ECOData;
using aidl::android::media::eco::ECODataStatus;
using ::ndk::ScopedAStatus;

/**
 * ECOServiceInfoListener interface class.
 */
class ECOServiceInfoListener : public BnECOServiceInfoListener {
public:
    // Create a ECOServiceInfoListener with specifed width, height and isCameraRecording.
    ECOServiceInfoListener(int32_t width, int32_t height, bool isCameraRecording);

    virtual ~ECOServiceInfoListener() {}

    virtual ScopedAStatus getType(int32_t* _aidl_return) = 0;
    virtual ScopedAStatus getName(std::string* _aidl_return) = 0;
    virtual ScopedAStatus getECOSession(::ndk::SpAIBinder* _aidl_return) = 0;
    virtual ScopedAStatus onNewInfo(const ::android::media::eco::ECOData& newInfo) = 0;

    // IBinder::DeathRecipient implementation.
    virtual void binderDied(const std::weak_ptr<AIBinder>& who);

private:
};

}  // namespace eco
}  // namespace media
}  // namespace android

#endif  // ANDROID_MEDIA_ECO_SERVICE_INFO_LISTENER_H_
