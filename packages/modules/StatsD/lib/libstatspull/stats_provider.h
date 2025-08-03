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

#include <aidl/android/os/IStatsd.h>
#include <android/binder_auto_utils.h>

using StatsProviderBinderDiedCallback = void (*)(void);

/**
 * Wrapper class for providing IStatsd Binder service.
 * It handles Binder death and registers a callback for when the Binder service is restored after
 * death.
 */
class StatsProvider {
public:
    StatsProvider(StatsProviderBinderDiedCallback callback);

    ~StatsProvider();

    std::shared_ptr<aidl::android::os::IStatsd> getStatsService();

private:
    static void binderDied(void* cookie);

    void resetStatsService();

    std::mutex mMutex;
    std::shared_ptr<aidl::android::os::IStatsd> mStatsd;
    const ::ndk::ScopedAIBinder_DeathRecipient mDeathRecipient;
    const StatsProviderBinderDiedCallback mCallback;
};
