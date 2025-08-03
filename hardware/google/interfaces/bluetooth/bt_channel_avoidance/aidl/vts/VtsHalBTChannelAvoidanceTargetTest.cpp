/*
 * Copyright (C) 2023 The Android Open Source Project
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
#include <aidl/hardware/google/bluetooth/bt_channel_avoidance/IBTChannelAvoidance.h>
#include <android/binder_auto_utils.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>

using aidl::hardware::google::bluetooth::bt_channel_avoidance::IBTChannelAvoidance;
using ndk::ScopedAStatus;
using ndk::SpAIBinder;

class BTChannelAvoidanceTest : public ::testing::TestWithParam<std::string> {
public:
  virtual void SetUp() override;
  virtual void TearDown() override;

  ndk::ScopedAStatus setBluetoothChannelStatus(const std::array<uint8_t, 10>& channel_map);

private:
  std::shared_ptr<IBTChannelAvoidance> bt_channel_avoidance_;
};

void BTChannelAvoidanceTest::SetUp() {
  ALOGI("SetUp Bluetooth Channel Avoidance Test");
  bt_channel_avoidance_ = IBTChannelAvoidance::fromBinder(
      ndk::SpAIBinder(AServiceManager_waitForService(GetParam().c_str())));
  ASSERT_NE(bt_channel_avoidance_, nullptr);
}

void BTChannelAvoidanceTest::TearDown() {
  ALOGI("TearDown Bluetooth Channel Avoidance Test");
  bt_channel_avoidance_ = nullptr;
  ASSERT_EQ(bt_channel_avoidance_, nullptr);
}

ndk::ScopedAStatus BTChannelAvoidanceTest::setBluetoothChannelStatus(const std::array<uint8_t, 10>& channel_map) {
  return bt_channel_avoidance_->setBluetoothChannelStatus(channel_map);
}

TEST_P(BTChannelAvoidanceTest, setBluetoothChannelStatus) {
  std::array<uint8_t, 10> channel_map = {127, 255, 255, 255, 255, 255, 255, 0, 0, 15};
  ndk::ScopedAStatus status = setBluetoothChannelStatus(channel_map);
  ASSERT_TRUE(status.isOk());
}

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(BTChannelAvoidanceTest);
INSTANTIATE_TEST_SUITE_P(PerInstance, BTChannelAvoidanceTest,
                         testing::ValuesIn(android::getAidlHalInstanceNames(
                             IBTChannelAvoidance::descriptor)),
                         android::PrintInstanceNameToString);

int main(int argc, char **argv) {
  ::testing::InitGoogleTest(&argc, argv);
  ABinderProcess_startThreadPool();
  int status = RUN_ALL_TESTS();
  ALOGI("Test result = %d", status);
  return status;
}

