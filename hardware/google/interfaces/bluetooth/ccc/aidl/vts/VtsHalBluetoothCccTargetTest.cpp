/*
 * Copyright 2023 The Android Open Source Project
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

#include <aidl/Gtest.h>
#include <aidl/Vintf.h>
#include <aidl/hardware/google/bluetooth/ccc/BnBluetoothCccCallback.h>
#include <aidl/hardware/google/bluetooth/ccc/IBluetoothCcc.h>
#include <aidl/hardware/google/bluetooth/ccc/IBluetoothCccCallback.h>
#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <binder/IServiceManager.h>
#include <utils/Log.h>

#include <vector>
#include <array>

using ::aidl::hardware::google::bluetooth::ccc::IBluetoothCcc;
using ::aidl::hardware::google::bluetooth::ccc::IBluetoothCccCallback;
using ::aidl::hardware::google::bluetooth::ccc::Direction;
using ::aidl::hardware::google::bluetooth::ccc::LmpEventId;
using ::aidl::hardware::google::bluetooth::ccc::Timestamp;
using ::ndk::ScopedAStatus;

class BluetoothCccTest : public ::testing::TestWithParam<std::string> {
 public:
  class BluetoothCccCallback
      : public ::aidl::hardware::google::bluetooth::ccc::BnBluetoothCccCallback {
   public:
    BluetoothCccCallback() = default;

    ::ndk::ScopedAStatus onEventGenerated(const Timestamp& /* timestamp */,
                                          const std::array<uint8_t, 6>& /* address */,
                                          Direction /* direction */,
                                          LmpEventId /* lmpEventId */,
                                          char16_t /* eventCounter */) override {
        return ::ndk::ScopedAStatus::ok();
    }

    ::ndk::ScopedAStatus onRegistered(bool) override {
        return ::ndk::ScopedAStatus::ok();
    }
  };

  virtual void SetUp() override {
    ALOGI("SetUp Ccc Test");
    bluetooth_ccc = IBluetoothCcc::fromBinder(
        ndk::SpAIBinder(AServiceManager_waitForService(GetParam().c_str())));
    ASSERT_NE(bluetooth_ccc, nullptr);

    ccc_callback = ndk::SharedRefBase::make<BluetoothCccCallback>();
    ASSERT_NE(ccc_callback, nullptr);
  }

  virtual void TearDown() override {
    ALOGI("TearDown Ccc Test");
    bluetooth_ccc = nullptr;
    ASSERT_EQ(bluetooth_ccc, nullptr);
  }

  // test functions to call
  ScopedAStatus registerForLmpEvents(std::array<uint8_t, 6>);
  ScopedAStatus unregisterLmpEvents(std::array<uint8_t, 6>);

private:
  std::shared_ptr<IBluetoothCcc> bluetooth_ccc;
  std::shared_ptr<BluetoothCccCallback> ccc_callback;
};


ScopedAStatus BluetoothCccTest::registerForLmpEvents(std::array<uint8_t, 6> addr) {
  std::vector<LmpEventId> events = {LmpEventId::CONNECT_IND,
                                    LmpEventId::LL_PHY_UPDATE_IND};
  return bluetooth_ccc->registerForLmpEvents(ccc_callback, std::array<uint8_t, 6>(addr), events);
}

ScopedAStatus BluetoothCccTest::unregisterLmpEvents(std::array<uint8_t, 6> addr) {
  return bluetooth_ccc->unregisterLmpEvents(std::array<uint8_t, 6>(addr));
}

TEST_P(BluetoothCccTest, RegisterForLmpEvents) {
  ALOGI("Run test RegisterForLmpEvents");
  ScopedAStatus status = registerForLmpEvents(
          std::array<uint8_t, 6>{0x11, 0x22, 0x33, 0x44, 0x55, 0x66});
  ASSERT_TRUE(status.isOk());
}

TEST_P(BluetoothCccTest, UnregisterLmpEvents) {
  std::array<uint8_t, 6> addr = {0x11, 0x22, 0x33, 0x44, 0x55, 0x66};
  ScopedAStatus status = registerForLmpEvents(addr);
  ASSERT_TRUE(status.isOk());
  status = unregisterLmpEvents(addr);
  ASSERT_TRUE(status.isOk());
}

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(BluetoothCccTest);
INSTANTIATE_TEST_SUITE_P(PerInstance, BluetoothCccTest,
                         testing::ValuesIn(android::getAidlHalInstanceNames(
                             IBluetoothCcc::descriptor)),
                         android::PrintInstanceNameToString);

int main(int argc, char **argv) {
  ::testing::InitGoogleTest(&argc, argv);
  ABinderProcess_startThreadPool();
  int status = RUN_ALL_TESTS();
  ALOGI("Test result = %d", status);
  return status;
}
