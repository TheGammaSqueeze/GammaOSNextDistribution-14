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
#include <aidl/hardware/google/bluetooth/ewp/IBluetoothEwp.h>
#include <android/binder_auto_utils.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>

using aidl::hardware::google::bluetooth::ewp::IBluetoothEwp;
using ndk::ScopedAStatus;
using ndk::SpAIBinder;

class BluetoothEwpTest : public ::testing::TestWithParam<std::string> {
public:
  virtual void SetUp() override;
  virtual void TearDown() override;

  ndk::ScopedAStatus enableEwp(uint16_t event_mask);
  ndk::ScopedAStatus disableEwp();

private:
  std::shared_ptr<IBluetoothEwp> bluetooth_ewp_;
};

void BluetoothEwpTest::SetUp() {
  ALOGI("SetUp Bluetooth Ewp Test");
  bluetooth_ewp_ = IBluetoothEwp::fromBinder(
      ndk::SpAIBinder(AServiceManager_waitForService(GetParam().c_str())));
  ASSERT_NE(bluetooth_ewp_, nullptr);
}

void BluetoothEwpTest::TearDown() {
  ALOGI("TearDown Bluetooth Ewp Test");
  bluetooth_ewp_ = nullptr;
  ASSERT_EQ(bluetooth_ewp_, nullptr);
}

ndk::ScopedAStatus BluetoothEwpTest::enableEwp(uint16_t event_mask){
  return bluetooth_ewp_->enableEwp(event_mask);
}

ndk::ScopedAStatus BluetoothEwpTest::disableEwp() {
  return bluetooth_ewp_->disableEwp();
}

TEST_P(BluetoothEwpTest, enableEwp) {
  uint16_t event_mask = 275;
  ndk::ScopedAStatus status = enableEwp(event_mask);
  ASSERT_TRUE(status.isOk());
}

TEST_P(BluetoothEwpTest, disableEwp) {
  ndk::ScopedAStatus status = disableEwp();
  ASSERT_TRUE(status.isOk());
}

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(BluetoothEwpTest);
INSTANTIATE_TEST_SUITE_P(PerInstance, BluetoothEwpTest,
                         testing::ValuesIn(android::getAidlHalInstanceNames(
                             IBluetoothEwp::descriptor)),
                         android::PrintInstanceNameToString);

int main(int argc, char **argv) {
  ::testing::InitGoogleTest(&argc, argv);
  ABinderProcess_startThreadPool();
  int status = RUN_ALL_TESTS();
  ALOGI("Test result = %d", status);
  return status;
}

