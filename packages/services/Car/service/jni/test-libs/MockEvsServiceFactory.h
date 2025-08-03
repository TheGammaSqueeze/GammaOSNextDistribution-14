/*
 * Copyright 2022 The Android Open Source Project
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

#include "IEvsServiceFactory.h"
#include "LinkUnlinkToDeathBase.h"
#include "MockEvsHal.h"

#include <aidl/android/hardware/automotive/evs/IEvsEnumerator.h>

namespace android::automotive::evs {

class MockEvsServiceFactory final : public IEvsServiceFactory {
public:
    ~MockEvsServiceFactory() = default;

    bool init() override;
    aidl::android::hardware::automotive::evs::IEvsEnumerator* getService() override {
        return mService.get();
    }
    void clear() override { mService.reset(); }

private:
    std::unique_ptr<aidl::android::automotive::evs::implementation::MockEvsHal> mMockEvs;
    std::shared_ptr<aidl::android::hardware::automotive::evs::IEvsEnumerator> mService;
};

class MockLinkUnlinkToDeath final : public LinkUnlinkToDeathBase {
public:
    binder_status_t linkToDeath(AIBinder* binder, AIBinder_DeathRecipient* recipient,
                                void* cookie) override;
    binder_status_t unlinkToDeath(AIBinder* binder) override;

    void* getCookie() override { return mCookie; }
};

}  // namespace android::automotive::evs
