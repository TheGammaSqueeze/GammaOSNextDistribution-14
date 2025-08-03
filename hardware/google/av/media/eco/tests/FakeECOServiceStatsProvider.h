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

// A fake ECOServiceStatsProvider for testing ECOService and ECOSession.

#include <aidl/android/media/eco/BnECOServiceStatsProvider.h>
#include <android-base/unique_fd.h>
#include <android/binder_auto_utils.h>
#include <android/binder_parcel.h>
#include <cutils/ashmem.h>
#include <gtest/gtest.h>
#include <math.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <utils/Log.h>

#include <condition_variable>
#include <deque>
#include <list>
#include <memory>
#include <mutex>
#include <thread>

#include "eco/ECOData.h"
#include "eco/ECODataKey.h"
#include "eco/ECOService.h"

namespace android {
namespace media {
namespace eco {

using ::ndk::ScopedAStatus;

/**
 * A fake ECOServiceStatsProvider.
 *
 * FakeECOServiceStatsProvider is a fake ECOServiceStatsProvider that used for testing.
 */

class FakeECOServiceStatsProvider : public BnECOServiceStatsProvider {
public:
    FakeECOServiceStatsProvider(int32_t width, int32_t height, bool isCameraRecording,
                                float frameRate, std::shared_ptr<IECOSession> session);

    FakeECOServiceStatsProvider(int32_t width, int32_t height, bool isCameraRecording,
                                float frameRate);

    void setECOSession(std::shared_ptr<IECOSession> session) { mECOSession = session; }

    // Helper function to inject session stats to the FakeECOServiceStatsProvider so provider
    // could push to the service.
    bool injectSessionStats(const ECOData& stats);

    // Helper function to inject each frame's stats to the FakeECOServiceStatsProvider so provider
    // could push to the service.
    bool injectFrameStats(const ECOData& stats);

    /* Starts the FakeECOServiceStatsProvider */
    void start();

    /* Stops FakeECOServiceStatsProvider */
    void stop();

    virtual ~FakeECOServiceStatsProvider();

    virtual ScopedAStatus getType(int32_t* _aidl_return);
    virtual ScopedAStatus getName(std::string* _aidl_return);
    virtual ScopedAStatus getECOSession(::ndk::SpAIBinder* _aidl_return);

    // IBinder::DeathRecipient implementation
    virtual void binderDied(const std::weak_ptr<AIBinder>& who);

private:
    int32_t mWidth;
    int32_t mHeight;
    bool mIsCameraRecording;
    float mFrameRate;
    uint32_t mFrameNumber;

    std::shared_ptr<IECOSession> mECOSession;
};

}  // namespace eco
}  // namespace media
}  // namespace android
