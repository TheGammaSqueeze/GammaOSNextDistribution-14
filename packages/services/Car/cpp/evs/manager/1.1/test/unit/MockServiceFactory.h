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

#ifndef CPP_EVS_MANAGER_1_1_TEST_UNIT_MOCKSERVICEFACTORY_H_
#define CPP_EVS_MANAGER_1_1_TEST_UNIT_MOCKSERVICEFACTORY_H_

#include "MockEvsEnumerator.h"
#include "ServiceFactory.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <string_view>

namespace android::automotive::evs::V1_1::implementation {

class MockServiceFactory : public ServiceFactory {
public:
    MockServiceFactory() {
        ON_CALL(*this, getService).WillByDefault(::testing::Invoke([&]() {
            return &mockEvsEnumerator;
        }));
    }
    ~MockServiceFactory() override = default;

    MOCK_METHOD(IEvsEnumerator*, getService, (), (override));

private:
    android::automotive::evs::V1_1::implementation::MockEvsEnumerator mockEvsEnumerator;
};

using NiceMockServiceFactory = ::testing::NiceMock<MockServiceFactory>;

}  // namespace android::automotive::evs::V1_1::implementation

#endif  // CPP_EVS_MANAGER_1_1_TEST_UNIT_MOCKSERVICEFACTORY_H_
