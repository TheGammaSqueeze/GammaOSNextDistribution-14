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
#include <aidl/hardware/google/bluetooth/sar/IBluetoothSar.h>
#include <android/binder_auto_utils.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>

using aidl::hardware::google::bluetooth::sar::IBluetoothSar;
using ndk::ScopedAStatus;
using ndk::SpAIBinder;

class BluetoothSarTest : public ::testing::TestWithParam<std::string> {
public:
  virtual void SetUp() override;
  virtual void TearDown() override;

  ndk::ScopedAStatus setAndCheckBluetoothTxPowerCap(int8_t cap);
  ndk::ScopedAStatus setAndCheckBluetoothTechBasedTxPowerCap(int8_t br_cap, int8_t edr_cap, int8_t ble_cap);
  ndk::ScopedAStatus setAndCheckBluetoothModeBasedTxPowerCap(const std::array<uint8_t, 3>& chain_0_cap, const std::array<uint8_t, 3>& chain_1_cap, const std::array<uint8_t, 6>& beamforming_cap);
  ndk::ScopedAStatus setAndCheckBluetoothModeBasedTxPowerCapPlusHR(const std::array<uint8_t, 4>& chain_0_cap, const std::array<uint8_t, 4>& chain_1_cap, const std::array<uint8_t, 8>& beamforming_cap);
  ndk::ScopedAStatus setAndCheckBluetoothAreaCode(const std::array<uint8_t, 3>& area_code);

private:
  std::shared_ptr<IBluetoothSar> bluetooth_sar_;
};

void BluetoothSarTest::SetUp() {
  ALOGI("SetUp Bluetooth SAR Test");
  bluetooth_sar_ = IBluetoothSar::fromBinder(
      ndk::SpAIBinder(AServiceManager_waitForService(GetParam().c_str())));
  ASSERT_NE(bluetooth_sar_, nullptr);
}

void BluetoothSarTest::TearDown() {
  ALOGI("TearDown Bluetooth SAR Test");
  bluetooth_sar_ = nullptr;
  ASSERT_EQ(bluetooth_sar_, nullptr);
}

ndk::ScopedAStatus BluetoothSarTest::setAndCheckBluetoothTxPowerCap(int8_t cap) {
  return bluetooth_sar_->setBluetoothTxPowerCap(cap);
}

ndk::ScopedAStatus BluetoothSarTest::setAndCheckBluetoothTechBasedTxPowerCap(
    int8_t br_cap, int8_t edr_cap, int8_t ble_cap) {
  return bluetooth_sar_->setBluetoothTechBasedTxPowerCap(br_cap, edr_cap, ble_cap);
}

ndk::ScopedAStatus BluetoothSarTest::setAndCheckBluetoothModeBasedTxPowerCap(
    const std::array<uint8_t, 3>& chain_0_cap,
    const std::array<uint8_t, 3>& chain_1_cap,
    const std::array<uint8_t, 6>& beamforming_cap) {
  return bluetooth_sar_->setBluetoothModeBasedTxPowerCap(chain_0_cap, chain_1_cap, beamforming_cap);
}


ndk::ScopedAStatus BluetoothSarTest::setAndCheckBluetoothModeBasedTxPowerCapPlusHR(
    const std::array<uint8_t, 4>& chain_0_cap,
    const std::array<uint8_t, 4>& chain_1_cap,
    const std::array<uint8_t, 8>& beamforming_cap){
  return bluetooth_sar_->setBluetoothModeBasedTxPowerCapPlusHR(chain_0_cap, chain_1_cap, beamforming_cap);
}

TEST_P(BluetoothSarTest, setAndCheckBluetoothTxPowerCap) {
  ndk::ScopedAStatus status = setAndCheckBluetoothTxPowerCap(40);
  ASSERT_TRUE(status.isOk());
  // check invalid power cap (greater than 80)
  status = setAndCheckBluetoothTxPowerCap(100);
  ASSERT_FALSE(status.isOk());
}


TEST_P(BluetoothSarTest, setAndCheckBluetoothTechBasedTxPowerCap) {
  ndk::ScopedAStatus status = setAndCheckBluetoothTechBasedTxPowerCap(10, 20, 30);
  ASSERT_TRUE(status.isOk());
  // check invalid power cap (greater than 80)
  status = setAndCheckBluetoothTechBasedTxPowerCap(10, 120, 30);
  ASSERT_FALSE(status.isOk());
}

TEST_P(BluetoothSarTest, setAndCheckBluetoothModeBasedTxPowerCap) {
  std::array<uint8_t, 3> chain_0_cap = {10, 20, 30};
  std::array<uint8_t, 3> chain_1_cap = {15, 25, 35};
  std::array<uint8_t, 6> beamforming_cap = {10, 20, 30, 40, 50, 60};
  ndk::ScopedAStatus status =
      setAndCheckBluetoothModeBasedTxPowerCap(chain_0_cap, chain_1_cap, beamforming_cap);
  ASSERT_TRUE(status.isOk());
  // check invalid power cap (greater than 80)
  std::array<uint8_t, 3> bad_cap = {15, 125, 35};
  status = setAndCheckBluetoothModeBasedTxPowerCap(chain_0_cap, bad_cap, beamforming_cap);
  ASSERT_FALSE(status.isOk());
}

TEST_P(BluetoothSarTest, setAndCheckBluetoothModeBasedTxPowerCapPlusHR) {
  std::array<uint8_t, 4> chain_0_cap = {10, 20, 30, 40};
  std::array<uint8_t, 4> chain_1_cap = {15, 25, 35, 45};
  std::array<uint8_t, 8> beamforming_cap = {10, 20, 30, 40, 50, 60, 70, 80};
  ndk::ScopedAStatus status =
      setAndCheckBluetoothModeBasedTxPowerCapPlusHR(chain_0_cap, chain_1_cap, beamforming_cap);
  ASSERT_TRUE(status.isOk());
  // check invalid power cap (greater than 80)
  std::array<uint8_t, 4> bad_cap = {10, 20, 30, 200};
  status = setAndCheckBluetoothModeBasedTxPowerCapPlusHR(bad_cap, chain_1_cap, beamforming_cap);
  ASSERT_FALSE(status.isOk());
}

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(BluetoothSarTest);
INSTANTIATE_TEST_SUITE_P(PerInstance, BluetoothSarTest,
                         testing::ValuesIn(android::getAidlHalInstanceNames(
                             IBluetoothSar::descriptor)),
                         android::PrintInstanceNameToString);

int main(int argc, char **argv) {
  ::testing::InitGoogleTest(&argc, argv);
  ABinderProcess_startThreadPool();
  int status = RUN_ALL_TESTS();
  ALOGI("Test result = %d", status);
  return status;
}
