/*
 * Copyright (c) 2021, The Android Open Source Project
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

#ifndef CPP_WATCHDOG_SERVER_TESTS_MOCKRESOURCEOVERUSELISTENER_H_
#define CPP_WATCHDOG_SERVER_TESTS_MOCKRESOURCEOVERUSELISTENER_H_

#include <aidl/android/automotive/watchdog/IResourceOveruseListener.h>
#include <aidl/android/automotive/watchdog/ResourceOveruseStats.h>
#include <binder/Status.h>
#include <gmock/gmock.h>
#include <utils/StrongPointer.h>

namespace android {
namespace automotive {
namespace watchdog {

class MockResourceOveruseListener :
      public aidl::android::automotive::watchdog::IResourceOveruseListenerDefault {
public:
    MockResourceOveruseListener() {}
    ~MockResourceOveruseListener() {}

    MOCK_METHOD(ndk::ScopedAStatus, onOveruse,
                (const aidl::android::automotive::watchdog::ResourceOveruseStats&), (override));
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_TESTS_MOCKRESOURCEOVERUSELISTENER_H_
