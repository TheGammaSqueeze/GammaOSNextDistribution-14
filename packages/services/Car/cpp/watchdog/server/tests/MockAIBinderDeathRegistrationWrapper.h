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

#ifndef CPP_WATCHDOG_SERVER_TESTS_MOCKAIBINDERDEATHREGISTRATIONWRAPPER_H_
#define CPP_WATCHDOG_SERVER_TESTS_MOCKAIBINDERDEATHREGISTRATIONWRAPPER_H_

#include "AIBinderDeathRegistrationWrapper.h"

#include <gmock/gmock.h>

namespace android {
namespace automotive {
namespace watchdog {

class MockAIBinderDeathRegistrationWrapper : public AIBinderDeathRegistrationWrapperInterface {
public:
    MockAIBinderDeathRegistrationWrapper() {
        EXPECT_CALL(*this, linkToDeath(testing::_, testing::_, testing::_))
                .WillRepeatedly(testing::Return(testing::ByMove(ndk::ScopedAStatus::ok())));
        EXPECT_CALL(*this, unlinkToDeath(testing::_, testing::_, testing::_))
                .WillRepeatedly(testing::Return(testing::ByMove(ndk::ScopedAStatus::ok())));
    }

    MOCK_METHOD(ndk::ScopedAStatus, linkToDeath, (AIBinder*, AIBinder_DeathRecipient*, void*),
                (const, override));
    MOCK_METHOD(ndk::ScopedAStatus, unlinkToDeath, (AIBinder*, AIBinder_DeathRecipient*, void*),
                (const, override));
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_TESTS_MOCKAIBINDERDEATHREGISTRATIONWRAPPER_H_
