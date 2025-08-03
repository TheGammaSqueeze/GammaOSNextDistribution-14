/**
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

#ifndef CPP_WATCHDOG_SERVER_TESTS_MOCKHIDLSERVICEMANAGER_H_
#define CPP_WATCHDOG_SERVER_TESTS_MOCKHIDLSERVICEMANAGER_H_

#include <android/hidl/manager/1.0/IServiceManager.h>
#include <gmock/gmock.h>
#include <hidl/HidlSupport.h>
#include <hidl/Status.h>

namespace android {
namespace automotive {
namespace watchdog {

class MockHidlServiceManager : public android::hidl::manager::V1_0::IServiceManager {
public:
    using IServiceManager = android::hidl::manager::V1_0::IServiceManager;
    using IBase = android::hidl::base::V1_0::IBase;
    template <typename T>
    using Return = android::hardware::Return<T>;
    using String = const android::hardware::hidl_string&;
    ~MockHidlServiceManager() = default;

#define MOCK_METHOD_CB(name) MOCK_METHOD1(name, Return<void>(IServiceManager::name##_cb))

    MOCK_METHOD2(get, Return<android::sp<IBase>>(String, String));
    MOCK_METHOD2(add, Return<bool>(String, const android::sp<IBase>&));
    MOCK_METHOD2(getTransport, Return<IServiceManager::Transport>(String, String));
    MOCK_METHOD_CB(list);
    MOCK_METHOD2(listByInterface, Return<void>(String, listByInterface_cb));
    MOCK_METHOD3(registerForNotifications,
                 Return<bool>(String, String,
                              const sp<android::hidl::manager::V1_0::IServiceNotification>&));
    MOCK_METHOD_CB(debugDump);
    MOCK_METHOD2(registerPassthroughClient, Return<void>(String, String));
    MOCK_METHOD_CB(interfaceChain);
    MOCK_METHOD2(debug,
                 Return<void>(const android::hardware::hidl_handle&,
                              const android::hardware::hidl_vec<android::hardware::hidl_string>&));
    MOCK_METHOD_CB(interfaceDescriptor);
    MOCK_METHOD_CB(getHashChain);
    MOCK_METHOD0(setHalInstrumentation, Return<void>());
    MOCK_METHOD2(linkToDeath,
                 Return<bool>(const sp<android::hardware::hidl_death_recipient>&, uint64_t));
    MOCK_METHOD0(ping, Return<void>());
    MOCK_METHOD_CB(getDebugInfo);
    MOCK_METHOD0(notifySyspropsChanged, Return<void>());
    MOCK_METHOD1(unlinkToDeath, Return<bool>(const sp<android::hardware::hidl_death_recipient>&));
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_TESTS_MOCKHIDLSERVICEMANAGER_H_
