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

#ifndef ANDROID_MEDIA_ECO_SERVICE_STATS_PROVIDER_H_
#define ANDROID_MEDIA_ECO_SERVICE_STATS_PROVIDER_H_

#include <aidl/android/media/eco/BnECOServiceStatsProvider.h>
#include <aidl/android/media/eco/IECOService.h>
#include <aidl/android/media/eco/IECOSession.h>
#include <utils/Log.h>

#include <condition_variable>
#include <memory>
#include <mutex>
#include <thread>

#include "ECOData.h"
#include "ECOServiceConstants.h"

namespace android {
namespace media {
namespace eco {

using aidl::android::media::eco::BnECOServiceStatsProvider;
using aidl::android::media::eco::ECOData;
using aidl::android::media::eco::ECODataStatus;
using aidl::android::media::eco::IECOService;
using aidl::android::media::eco::IECOSession;
using ::ndk::ScopedAStatus;

/**
 * ECOServiceStatsProvider interface class.
 */
class ECOServiceStatsProvider : public BnECOServiceStatsProvider {
public:
    ECOServiceStatsProvider(int32_t width, int32_t height, bool isCameraRecording,
                            std::shared_ptr<IECOSession>& session, const char* name);
    virtual ~ECOServiceStatsProvider() {}

    virtual ScopedAStatus getType(int32_t* _aidl_return);
    virtual ScopedAStatus getName(std::string* _aidl_return);
    virtual ScopedAStatus getECOSession(::ndk::SpAIBinder* _aidl_return);
    virtual ScopedAStatus isCameraRecording(bool* _aidl_return);

    // IBinder::DeathRecipient implementation
    virtual void binderDied(const std::weak_ptr<AIBinder>& who);

    bool updateStats(const ECOData& data);
    bool addProvider();
    bool removeProvider();
    float getFramerate(int64_t currTimestamp);
    static std::shared_ptr<ECOServiceStatsProvider> create(int32_t width, int32_t height,
                                                           bool isCameraRecording,
                                                           const char* name);

private:
    int32_t mWidth = 0;
    int32_t mHeight = 0;
    bool mIsCameraRecording = false;
    std::shared_ptr<IECOSession> mECOSession = nullptr;
    const char* mProviderName = nullptr;
    int64_t mLastFrameTimestamp = 0;
};

}  // namespace eco
}  // namespace media
}  // namespace android

#endif  // ANDROID_MEDIA_ECO_SERVICE_STATS_PROVIDER_H_
