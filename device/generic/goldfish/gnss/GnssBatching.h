/*
 * Copyright (C) 2020 The Android Open Source Project
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
#include <condition_variable>
#include <deque>
#include <mutex>
#include <thread>
#include <aidl/android/hardware/gnss/BnGnssBatching.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace implementation {

struct GnssBatching : public BnGnssBatching {
    ~GnssBatching();

    ndk::ScopedAStatus init(const std::shared_ptr<IGnssBatchingCallback>& callback) override;
    ndk::ScopedAStatus getBatchSize(int* size) override;
    ndk::ScopedAStatus start(const Options& options) override;
    ndk::ScopedAStatus flush() override;
    ndk::ScopedAStatus stop() override;
    ndk::ScopedAStatus cleanup() override;

    void onGnssLocationCb(GnssLocation location);

private:
    void stopImpl();
    void batchLocationLocked(GnssLocation location, bool wakeUpOnFifoFull);
    bool flushLocked();

    std::shared_ptr<IGnssBatchingCallback> mCallback;
    std::deque<GnssLocation> mBatchedLocations;
    std::optional<GnssLocation> mLocation;
    std::condition_variable mThreadNotification;
    bool mRunning = false;
    std::thread mThread;
    mutable std::mutex mMtx;
};

}  // namespace implementation
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
