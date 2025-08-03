/*
 * Copyright (C) 2023, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <android/binder_manager.h>
#include <stats_provider.h>

using aidl::android::os::IStatsd;

StatsProvider::StatsProvider(StatsProviderBinderDiedCallback callback)
    : mDeathRecipient(AIBinder_DeathRecipient_new(binderDied)), mCallback(callback) {
}

StatsProvider::~StatsProvider() {
    resetStatsService();
}

std::shared_ptr<IStatsd> StatsProvider::getStatsService() {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mStatsd) {
        // Fetch statsd
        ::ndk::SpAIBinder binder(AServiceManager_getService("stats"));
        mStatsd = IStatsd::fromBinder(binder);
        if (mStatsd) {
            AIBinder_linkToDeath(binder.get(), mDeathRecipient.get(), this);
        }
    }
    return mStatsd;
}

void StatsProvider::resetStatsService() {
    std::lock_guard<std::mutex> lock(mMutex);
    mStatsd = nullptr;
}

void StatsProvider::binderDied(void* cookie) {
    StatsProvider* statsProvider = static_cast<StatsProvider*>(cookie);
    statsProvider->resetStatsService();
    statsProvider->mCallback();
}
