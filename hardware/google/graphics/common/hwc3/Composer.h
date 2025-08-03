/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <aidl/android/hardware/graphics/composer3/BnComposer.h>
#include <utils/Mutex.h>

#include "include/IComposerHal.h"
#include "ComposerClient.h"

namespace aidl::android::hardware::graphics::composer3::impl {

class Composer : public BnComposer {
public:
    Composer();

    binder_status_t dump(int fd, const char** args, uint32_t numArgs) override;

    // composer3 api
    ndk::ScopedAStatus createClient(std::shared_ptr<IComposerClient>* client) override;
    ndk::ScopedAStatus getCapabilities(std::vector<Capability>* caps) override;

protected:
    ::ndk::SpAIBinder createBinder() override;

private:
    bool waitForClientDestroyedLocked(std::unique_lock<std::mutex>& lock) REQUIRES(mClientMutex);
    void onClientDestroyed();

    std::unique_ptr<IComposerHal> mHal;
    std::mutex mClientMutex;
    bool mClientAlive GUARDED_BY(mClientMutex) = false;
    std::condition_variable mClientDestroyedCondition;
};

}  // namespace aidl::android::hardware::graphics::composer3::impl
