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

#include <chrono>
#include <aidl/android/hardware/gnss/IGnss.h>
#include <debug.h>

#include "GnssBatching.h"

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace implementation {
namespace {
using Clock = std::chrono::steady_clock;

constexpr size_t kBatchSize = 4;
}  // namsepace

GnssBatching::~GnssBatching() {
    stopImpl();
}

ndk::ScopedAStatus GnssBatching::init(const std::shared_ptr<IGnssBatchingCallback>& callback) {
    if (callback == nullptr) {
        return ndk::ScopedAStatus::fromExceptionCode(FAILURE(IGnss::ERROR_INVALID_ARGUMENT));
    }

    stopImpl();

    std::lock_guard<std::mutex> lock(mMtx);
    mBatchedLocations.clear();
    mCallback = callback;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus GnssBatching::getBatchSize(int* size) {
    *size = kBatchSize;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus GnssBatching::start(const Options& options) {
    if (options.periodNanos < 0) {
        return ndk::ScopedAStatus::fromExceptionCode(FAILURE(IGnss::ERROR_INVALID_ARGUMENT));
    }

    const Clock::duration interval = std::chrono::nanoseconds(options.periodNanos);
    const bool wakeUpOnFifoFull =
        (options.flags & IGnssBatching::WAKEUP_ON_FIFO_FULL) ? true : false;

    stopImpl();

    std::lock_guard<std::mutex> lock(mMtx);
    mRunning = true;
    mThread = std::thread([this, interval, wakeUpOnFifoFull](){
        Clock::time_point wakeupT = Clock::now() + interval;

        for (;; wakeupT += interval) {
            std::unique_lock<std::mutex> lock(mMtx);
            if ((mThreadNotification.wait_until(lock, wakeupT) == std::cv_status::no_timeout) &&
                    !mRunning) {
                return;
            }

            if (mLocation.has_value()) {
                batchLocationLocked(mLocation.value(), wakeUpOnFifoFull);
            }
        }
    });

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus GnssBatching::flush() {
    std::lock_guard<std::mutex> lock(mMtx);
    if (flushLocked()) {
        return ndk::ScopedAStatus::ok();
    } else {
        return ndk::ScopedAStatus::fromServiceSpecificError(IGnss::ERROR_GENERIC);
    }
}

ndk::ScopedAStatus GnssBatching::stop() {
    stopImpl();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus GnssBatching::cleanup() {
    stopImpl();

    std::lock_guard<std::mutex> lock(mMtx);
    flushLocked();
    mCallback.reset();
    return ndk::ScopedAStatus::ok();
}

void GnssBatching::onGnssLocationCb(GnssLocation location) {
    std::lock_guard<std::mutex> lock(mMtx);
    mLocation = std::move(location);
}

void GnssBatching::stopImpl() {
    bool needJoin;

    {
        std::lock_guard<std::mutex> lock(mMtx);
        if (mThread.joinable()) {
            mRunning = false;
            mThreadNotification.notify_all();
            needJoin = true;
        } else {
            needJoin = false;
        }
    }

    if (needJoin) {
        mThread.join();
    }
}

void GnssBatching::batchLocationLocked(GnssLocation location,
                                       const bool wakeUpOnFifoFull) {
    while (mBatchedLocations.size() >= kBatchSize) {
        mBatchedLocations.pop_front();
    }

    mBatchedLocations.push_back(location);

    if (wakeUpOnFifoFull && (mBatchedLocations.size() >= kBatchSize)) {
        flushLocked();
    }
}

bool GnssBatching::flushLocked() {
    if (mCallback) {
        mCallback->gnssLocationBatchCb({mBatchedLocations.begin(),
                                        mBatchedLocations.end()});
        mBatchedLocations.clear();
        return true;
    } else {
        return false;
    }
}

}  // namespace implementation
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
