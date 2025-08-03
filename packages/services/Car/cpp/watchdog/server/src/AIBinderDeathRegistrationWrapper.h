/*
 * Copyright (c) 2022, The Android Open Source Project
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

#ifndef CPP_WATCHDOG_SERVER_SRC_AIBINDERDEATHREGISTRATIONWRAPPER_H_
#define CPP_WATCHDOG_SERVER_SRC_AIBINDERDEATHREGISTRATIONWRAPPER_H_

#include <android/binder_auto_utils.h>
#include <utils/RefBase.h>

namespace android {
namespace automotive {
namespace watchdog {

class AIBinderDeathRegistrationWrapperInterface : public virtual android::RefBase {
public:
    /**
     * Links the recipient to the binder's death. The cookie is passed to the recipient in case
     * the binder dies.
     */
    virtual ndk::ScopedAStatus linkToDeath(AIBinder* binder, AIBinder_DeathRecipient* recipient,
                                           void* cookie) const = 0;
    /**
     * Unlinks the recipient from the binder's death. Pass the same cookie that was used to link to
     * the binder's death.
     */
    virtual ndk::ScopedAStatus unlinkToDeath(AIBinder* binder, AIBinder_DeathRecipient* recipient,
                                             void* cookie) const = 0;
};

class AIBinderDeathRegistrationWrapper final : public AIBinderDeathRegistrationWrapperInterface {
public:
    ndk::ScopedAStatus linkToDeath(AIBinder* binder, AIBinder_DeathRecipient* recipient,
                                   void* cookie) const override;
    ndk::ScopedAStatus unlinkToDeath(AIBinder* binder, AIBinder_DeathRecipient* recipient,
                                     void* cookie) const override;
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_SRC_AIBINDERDEATHREGISTRATIONWRAPPER_H_
