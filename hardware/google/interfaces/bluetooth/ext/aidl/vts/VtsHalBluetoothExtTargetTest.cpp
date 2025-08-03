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
#include <aidl/hardware/google/bluetooth/ext/IBluetoothExt.h>
#include <android/binder_auto_utils.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>

using aidl::hardware::google::bluetooth::ext::IBluetoothExt;
using ndk::ScopedAStatus;
using ndk::SpAIBinder;

class BluetoothExtTest : public ::testing::TestWithParam<std::string> {
public:
  virtual void SetUp() override;
  virtual void TearDown() override;

  ndk::ScopedAStatus setBluetoothCmdPacket(uint16_t opcode, const std::vector<uint8_t>& params, bool* ret_ptr);

private:
  std::shared_ptr<IBluetoothExt> bluetooth_ext_;
};

void BluetoothExtTest::SetUp() {
  ALOGI("SetUp Bluetooth Ext Test");
  bluetooth_ext_ = IBluetoothExt::fromBinder(
      ndk::SpAIBinder(AServiceManager_waitForService(GetParam().c_str())));
  ASSERT_NE(bluetooth_ext_, nullptr);
}

void BluetoothExtTest::TearDown() {
  ALOGI("Tear Down Bluetooth Ext Test");
  bluetooth_ext_ = nullptr;
  ASSERT_EQ(bluetooth_ext_, nullptr);
}

ndk::ScopedAStatus BluetoothExtTest::setBluetoothCmdPacket(uint16_t opcode, const std::vector<uint8_t>& params, bool* ret_ptr) {
  ndk::ScopedAStatus status = bluetooth_ext_->setBluetoothCmdPacket(opcode, params, ret_ptr);
  if (!(*ret_ptr)) {
    return ndk::ScopedAStatus::fromServiceSpecificError(STATUS_BAD_VALUE);
  }
  return status;
}

TEST_P(BluetoothExtTest, setBluetoothCmdPacket) {
  uint16_t opcode = 0xfe20;
  std::vector<uint8_t> params = {0x20, 0xfe, 0x08, 0x14, 0x01, 0xff, 0x00, 0x10, 0x00, 0x01, 0x00};
  bool is_valid_packet = false;
  ndk::ScopedAStatus status = setBluetoothCmdPacket(opcode, params, &is_valid_packet);
  ASSERT_TRUE(is_valid_packet);
  ASSERT_TRUE(status.isOk());
}

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(BluetoothExtTest);
INSTANTIATE_TEST_SUITE_P(PerInstance, BluetoothExtTest,
                         testing::ValuesIn(android::getAidlHalInstanceNames(
                             IBluetoothExt::descriptor)),
                         android::PrintInstanceNameToString);

int main(int argc, char **argv) {
  ::testing::InitGoogleTest(&argc, argv);
  ABinderProcess_startThreadPool();
  int status = RUN_ALL_TESTS();
  ALOGI("Test result = %d", status);
  return status;
}

