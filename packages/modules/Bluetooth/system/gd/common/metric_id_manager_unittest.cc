/******************************************************************************
 *
 *  Copyright 2020 Google, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/
#include <thread>

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "common/metric_id_manager.h"

namespace testing {

using bluetooth::common::MetricIdManager;

bluetooth::hci::Address kthAddress(uint32_t k) {
  uint8_t array[6] = {0, 0, 0, 0, 0, 0};
  for (int i = 5; i >= 2; i--) {
    array[i] = k % 256;
    k = k / 256;
  }
  bluetooth::hci::Address addr(array);
  return addr;
}

std::unordered_map<bluetooth::hci::Address, int> generateAddresses(
    const uint32_t num) {
  // generate first num of mac address -> id pairs
  // input may is always valid 256^6 = 2^48 > 2^32
  std::unordered_map<bluetooth::hci::Address, int> device_map;
  for (size_t key = 0; key < num; key++) {
    device_map[kthAddress(key)] = key + MetricIdManager::kMinId;
  }
  return device_map;
}

TEST(BluetoothMetricIdManagerTest, MetricIdManagerInitCloseTest) {
  auto& manager = MetricIdManager::GetInstance();
  std::unordered_map<bluetooth::hci::Address, int> paired_device_map;
  MetricIdManager::Callback callback = [](
      const bluetooth::hci::Address&, const int) {
    return true;
  };
  ASSERT_TRUE(manager.Init(paired_device_map, callback, callback));
  ASSERT_FALSE(manager.Init(paired_device_map, callback, callback));
  ASSERT_TRUE(manager.Close());
}

TEST(BluetoothMetricIdManagerTest, MetricIdManagerNotCloseTest) {
  auto& manager = MetricIdManager::GetInstance();
  std::unordered_map<bluetooth::hci::Address, int> paired_device_map;
  MetricIdManager::Callback callback = [](
      const bluetooth::hci::Address&, const int) {
    return true;
  };
  ASSERT_TRUE(manager.Init(paired_device_map, callback, callback));

  // should fail because it isn't closed
  ASSERT_FALSE(manager.Init(paired_device_map, callback, callback));
  ASSERT_TRUE(manager.Close());
}

TEST(BluetoothMetricIdManagerTest, MetricIdManagerScanDeviceFromEmptyTest) {
  auto& manager = MetricIdManager::GetInstance();
  std::unordered_map<bluetooth::hci::Address, int> paired_device_map;
  MetricIdManager::Callback callback = [](
      const bluetooth::hci::Address&, const int) {
    return true;
  };
  // test empty map, next id should be kMinId
  ASSERT_TRUE(manager.Init(paired_device_map, callback, callback));
  ASSERT_EQ(manager.AllocateId(kthAddress(0)), MetricIdManager::kMinId);
  ASSERT_EQ(manager.AllocateId(kthAddress(1)), MetricIdManager::kMinId + 1);
  ASSERT_EQ(manager.AllocateId(kthAddress(0)), MetricIdManager::kMinId);
  ASSERT_EQ(manager.AllocateId(kthAddress(2)), MetricIdManager::kMinId + 2);
  ASSERT_TRUE(manager.Close());
}

TEST(BluetoothMetricIdManagerTest,
     MetricIdManagerScanDeviceFromFilledTest) {
  auto& manager = MetricIdManager::GetInstance();
  std::unordered_map<bluetooth::hci::Address, int> paired_device_map;
  MetricIdManager::Callback callback = [](
      const bluetooth::hci::Address&, const int) {
    return true;
  };
  int id = static_cast<int>(MetricIdManager::kMaxNumPairedDevicesInMemory) +
           MetricIdManager::kMinId;
  // next id should be MetricIdManager::kMaxNumPairedDevicesInMemory
  paired_device_map =
      generateAddresses(MetricIdManager::kMaxNumPairedDevicesInMemory);
  ASSERT_TRUE(manager.Init(paired_device_map, callback, callback));
  // try new values not in the map, should get new id.
  ASSERT_EQ(manager.AllocateId(kthAddress(INT_MAX)), id);
  ASSERT_EQ(manager.AllocateId(kthAddress(INT_MAX - 1)), id + 1);
  ASSERT_EQ(manager.AllocateId(kthAddress(INT_MAX)), id);
  ASSERT_EQ(manager.AllocateId(kthAddress(INT_MAX - 2)), id + 2);
  ASSERT_TRUE(manager.Close());
}

TEST(BluetoothMetricIdManagerTest, MetricIdManagerAllocateExistingTest) {
  auto& manager = MetricIdManager::GetInstance();
  std::unordered_map<bluetooth::hci::Address, int> paired_device_map =
      generateAddresses(MetricIdManager::kMaxNumPairedDevicesInMemory);

  MetricIdManager::Callback callback = [](
      const bluetooth::hci::Address&, const int) {
    return true;
  };
  int id = MetricIdManager::kMinId;
  // next id should be MetricIdManager::kMaxNumPairedDevicesInMemory
  ASSERT_TRUE(manager.Init(paired_device_map, callback, callback));

  // try values already in the map, should get new id.
  ASSERT_EQ(
      manager.AllocateId(bluetooth::hci::Address({0, 0, 0, 0, 0, 0})), id);
  ASSERT_EQ(
      manager.AllocateId(
          bluetooth::hci::Address({0, 0, 0, 0, 0, 1})), id + 1);
  ASSERT_EQ(
      manager.AllocateId(bluetooth::hci::Address({0, 0, 0, 0, 0, 0})), id);
  ASSERT_EQ(
      manager.AllocateId(
          bluetooth::hci::Address({0, 0, 0, 0, 0, 2})), id + 2);
  ASSERT_TRUE(manager.Close());
}

TEST(BluetoothMetricIdManagerTest, MetricIdManagerMainTest1) {
  auto& manager = MetricIdManager::GetInstance();
  std::unordered_map<bluetooth::hci::Address, int> paired_device_map;
  int placeholder = 22;
  int* pointer = &placeholder;
  MetricIdManager::Callback save_callback = [pointer](
      const bluetooth::hci::Address&,
      const int) {
    *pointer = *pointer * 2;
    return true;
  };
  MetricIdManager::Callback forget_callback = [pointer](
      const bluetooth::hci::Address&,
      const int) {
    *pointer = *pointer / 2;
    return true;
  };

  ASSERT_TRUE(
      manager.Init(paired_device_map, save_callback, forget_callback));
  ASSERT_EQ(manager.AllocateId(bluetooth::hci::Address({0, 0, 0, 0, 0, 0})),
            MetricIdManager::kMinId);
  // save it and make sure the callback is called
  ASSERT_TRUE(
      manager.SaveDevice(bluetooth::hci::Address({0, 0, 0, 0, 0, 0})));
  ASSERT_EQ(placeholder, 44);

  // should fail, since id of device is not allocated
  ASSERT_FALSE(
      manager.SaveDevice(bluetooth::hci::Address({0, 0, 0, 0, 0, 1})));
  ASSERT_EQ(placeholder, 44);

  // save it and make sure the callback is called
  ASSERT_EQ(manager.AllocateId(bluetooth::hci::Address({0, 0, 0, 0, 0, 2})),
            MetricIdManager::kMinId + 1);
  ASSERT_EQ(manager.AllocateId(bluetooth::hci::Address({0, 0, 0, 0, 0, 3})),
            MetricIdManager::kMinId + 2);
  ASSERT_TRUE(
      manager.SaveDevice(bluetooth::hci::Address({0, 0, 0, 0, 0, 2})));
  ASSERT_EQ(placeholder, 88);
  ASSERT_TRUE(
      manager.SaveDevice(bluetooth::hci::Address({0, 0, 0, 0, 0, 3})));
  ASSERT_EQ(placeholder, 176);

  // should be true but callback won't be called, since id had been saved
  ASSERT_TRUE(
      manager.SaveDevice(bluetooth::hci::Address({0, 0, 0, 0, 0, 0})));
  ASSERT_EQ(placeholder, 176);

  // forget
  manager.ForgetDevice(bluetooth::hci::Address({0, 0, 0, 0, 0, 1}));
  ASSERT_EQ(placeholder, 176);
  manager.ForgetDevice(bluetooth::hci::Address({0, 0, 0, 0, 0, 2}));
  ASSERT_EQ(placeholder, 88);

  ASSERT_TRUE(manager.Close());
}

TEST(BluetoothMetricIdManagerTest, MetricIdManagerFullPairedMap) {
  auto& manager = MetricIdManager::GetInstance();
  // preset a full map
  std::unordered_map<bluetooth::hci::Address, int> paired_device_map =
      generateAddresses(MetricIdManager::kMaxNumPairedDevicesInMemory);
  int placeholder = 243;
  int* pointer = &placeholder;
  MetricIdManager::Callback save_callback = [pointer](
      const bluetooth::hci::Address&,
      const int) {
    *pointer = *pointer * 2;
    return true;
  };
  MetricIdManager::Callback forget_callback = [pointer](
      const bluetooth::hci::Address&,
      const int) {
    *pointer = *pointer / 3;
    return true;
  };

  ASSERT_TRUE(
      manager.Init(paired_device_map, save_callback, forget_callback));

  // check if all preset ids are there.
  // comments based on kMaxNumPairedDevicesInMemory = 200. It can change.
  int key = 0;
  for (key = 0;
       key < static_cast<int>(MetricIdManager::kMaxNumPairedDevicesInMemory);
       key++) {
    ASSERT_EQ(manager.AllocateId(kthAddress(key)),
              key + MetricIdManager::kMinId);
  }
  // paired: 0, 1, 2 ... 199,
  // scanned:

  int id = static_cast<int>(MetricIdManager::kMaxNumPairedDevicesInMemory +
                            MetricIdManager::kMinId);
  // next id should be MetricIdManager::kMaxNumPairedDevicesInMemory +
  // MetricIdManager::kMinId

  ASSERT_EQ(manager.AllocateId(kthAddress(key)), id++);
  // paired: 0, 1, 2 ... 199,
  // scanned: 200

  // save it and make sure the callback is called
  ASSERT_TRUE(manager.SaveDevice(kthAddress(key)));
  // one key is evicted, another key is saved so *2/3
  ASSERT_EQ(placeholder, 162);

  // paired: 1, 2 ... 199, 200,
  // scanned:

  ASSERT_EQ(manager.AllocateId(kthAddress(0)), id++);
  // paired: 1, 2 ... 199, 200
  // scanned: 0

  // key == 200
  // should fail, since id of device is not allocated
  ASSERT_FALSE(manager.SaveDevice(kthAddress(key + 1)));
  ASSERT_EQ(placeholder, 162);
  // paired: 1, 2 ... 199, 200,
  // scanned: 0

  ASSERT_EQ(manager.AllocateId(kthAddress(key + 1)), id++);
  ASSERT_TRUE(manager.SaveDevice(kthAddress(key + 1)));
  // one key is evicted, another key is saved so *2/3,
  ASSERT_EQ(placeholder, 108);
  // paired: 2 ... 199, 200, 201
  // scanned: 0

  ASSERT_EQ(manager.AllocateId(kthAddress(1)), id++);
  // paired: 2 ... 199, 200, 201,
  // scanned: 0, 1

  // save it and make sure the callback is called
  ASSERT_EQ(manager.AllocateId(kthAddress(key + 2)), id++);
  ASSERT_EQ(manager.AllocateId(kthAddress(key + 3)), id++);
  // paired: 2 ... 199, 200, 201,
  // scanned: 0, 1, 202, 203

  placeholder = 9;
  ASSERT_TRUE(manager.SaveDevice(kthAddress(key + 2)));
  // one key is evicted, another key is saved so *2/3,
  ASSERT_EQ(placeholder, 6);
  ASSERT_TRUE(manager.SaveDevice(kthAddress(key + 3)));
  // one key is evicted, another key is saved so *2/3,
  ASSERT_EQ(placeholder, 4);
  // paired: 4 ... 199, 200, 201, 202, 203
  // scanned: 0, 1

  // should be true but callback won't be called, since id had been saved
  ASSERT_TRUE(manager.SaveDevice(kthAddress(key + 2)));
  ASSERT_EQ(placeholder, 4);

  placeholder = 27;
  // forget
  manager.ForgetDevice(kthAddress(key + 200));
  ASSERT_EQ(placeholder, 27);  // should fail, no such a key
  manager.ForgetDevice(kthAddress(key + 2));
  ASSERT_EQ(placeholder, 9);
  // paired: 4 ... 199, 200, 201, 203
  // scanned: 0, 1

  // save it and make sure the callback is called
  ASSERT_EQ(manager.AllocateId(kthAddress(key + 2)), id++);
  ASSERT_EQ(manager.AllocateId(kthAddress(key + 4)), id++);
  ASSERT_EQ(manager.AllocateId(kthAddress(key + 5)), id++);
  // paired: 4 ... 199, 200, 201, 203
  // scanned: 0, 1, 202, 204, 205

  ASSERT_TRUE(manager.SaveDevice(kthAddress(key + 2)));
  ASSERT_EQ(placeholder, 18);  // no key is evicted, a key is saved so *2,

  // should be true but callback won't be called, since id had been saved
  ASSERT_TRUE(manager.SaveDevice(kthAddress(key + 3)));
  ASSERT_EQ(placeholder, 18);  // no such a key in scanned
  ASSERT_TRUE(manager.SaveDevice(kthAddress(key + 4)));
  // one key is evicted, another key is saved so *2/3
  ASSERT_EQ(placeholder, 12);
  // paired: 5 6 ... 199, 200, 201, 203, 202, 204
  // scanned: 0, 1, 205

  // verify paired:
  for (key = 5; key <= 199; key++) {
    placeholder = 3;
    manager.ForgetDevice(kthAddress(key));
    ASSERT_EQ(placeholder, 1);
  }
  for (size_t k = MetricIdManager::kMaxNumPairedDevicesInMemory;
       k <= MetricIdManager::kMaxNumPairedDevicesInMemory + 4; k++) {
    placeholder = 3;
    manager.ForgetDevice(kthAddress(k));
    ASSERT_EQ(placeholder, 1);
  }

  // verify scanned
  placeholder = 4;
  ASSERT_TRUE(manager.SaveDevice(kthAddress(0)));
  ASSERT_TRUE(manager.SaveDevice(kthAddress(1)));
  ASSERT_TRUE(manager.SaveDevice(
      kthAddress(MetricIdManager::kMaxNumPairedDevicesInMemory + 5)));
  ASSERT_EQ(placeholder, 32);

  ASSERT_TRUE(manager.Close());
}

TEST(BluetoothMetricIdManagerTest, MetricIdManagerFullScannedMap) {
  auto& manager = MetricIdManager::GetInstance();
  std::unordered_map<bluetooth::hci::Address, int> paired_device_map;
  int placeholder = 22;
  int* pointer = &placeholder;
  MetricIdManager::Callback save_callback = [pointer](
      const bluetooth::hci::Address&,const int) {
    *pointer = *pointer * 2;
    return true;
  };
  MetricIdManager::Callback forget_callback = [pointer](
      const bluetooth::hci::Address&,const int) {
    *pointer = *pointer / 2;
    return true;
  };

  ASSERT_TRUE(
      manager.Init(paired_device_map, save_callback, forget_callback));

  // allocate kMaxNumUnpairedDevicesInMemory ids
  // comments based on kMaxNumUnpairedDevicesInMemory = 200
  for (int key = 0;
       key <
       static_cast<int>(MetricIdManager::kMaxNumUnpairedDevicesInMemory);
       key++) {
    ASSERT_EQ(manager.AllocateId(kthAddress(key)),
              key + MetricIdManager::kMinId);
  }
  // scanned: 0, 1, 2 ... 199,
  // paired:

  int id = MetricIdManager::kMaxNumUnpairedDevicesInMemory +
           MetricIdManager::kMinId;
  bluetooth::hci::Address addr =
      kthAddress(MetricIdManager::kMaxNumUnpairedDevicesInMemory);
  ASSERT_EQ(manager.AllocateId(addr), id);
  // scanned: 1, 2 ... 199, 200

  // save it and make sure the callback is called
  ASSERT_TRUE(manager.SaveDevice(addr));
  ASSERT_EQ(manager.AllocateId(addr), id);
  ASSERT_EQ(placeholder, 44);
  // paired: 200,
  // scanned: 1, 2 ... 199,
  id++;

  addr = kthAddress(MetricIdManager::kMaxNumUnpairedDevicesInMemory + 1);
  ASSERT_EQ(manager.AllocateId(addr), id++);
  // paired: 200,
  // scanned: 1, 2 ... 199, 201

  // try to allocate for device 0, 1, 2, 3, 4....199
  // we should have a new id every time,
  // since the scanned map is full at this point
  for (int key = 0;
       key <
       static_cast<int>(MetricIdManager::kMaxNumUnpairedDevicesInMemory);
       key++) {
    ASSERT_EQ(manager.AllocateId(kthAddress(key)), id++);
  }
  ASSERT_TRUE(manager.Close());
}

TEST(BluetoothMetricIdManagerTest, MetricIdManagerMultiThreadPressureTest) {
  std::unordered_map<bluetooth::hci::Address, int> paired_device_map;
  auto& manager = MetricIdManager::GetInstance();
  int placeholder = 22;
  int* pointer = &placeholder;
  MetricIdManager::Callback save_callback = [pointer](
      const bluetooth::hci::Address&, const int) {
    *pointer = *pointer + 1;
    return true;
  };
  MetricIdManager::Callback forget_callback = [pointer](
      const bluetooth::hci::Address&, const int) {
    *pointer = *pointer - 1;
    return true;
  };
  ASSERT_TRUE(
      manager.Init(paired_device_map, save_callback, forget_callback));

  // make sure no deadlock
  std::vector<std::thread> workers;
  for (int key = 0;
       key <
       static_cast<int>(MetricIdManager::kMaxNumUnpairedDevicesInMemory);
       key++) {
    workers.push_back(std::thread([key]() {
      auto& manager = MetricIdManager::GetInstance();
      bluetooth::hci::Address fake_mac_address = kthAddress(key);
      manager.AllocateId(fake_mac_address);
      ASSERT_TRUE(manager.SaveDevice(fake_mac_address));
      manager.ForgetDevice(fake_mac_address);
    }));
  }
  for (auto& worker : workers) {
    worker.join();
  }
  ASSERT_TRUE(manager.IsEmpty());
  ASSERT_TRUE(manager.Close());
}

TEST(BluetoothMetricIdManagerTest, MetricIdManagerWrapAroundTest1) {
  std::unordered_map<bluetooth::hci::Address, int> paired_device_map;
  auto& manager = MetricIdManager::GetInstance();
  MetricIdManager::Callback callback = [](
      const bluetooth::hci::Address&, const int) {
    return true;
  };

  // make a sparse paired_device_map
  int min_id = MetricIdManager::kMinId;
  paired_device_map[kthAddress(min_id)] = min_id;
  paired_device_map[kthAddress(min_id + 1)] = min_id + 1;
  paired_device_map[kthAddress(min_id + 3)] = min_id + 3;
  paired_device_map[kthAddress(min_id + 4)] = min_id + 4;

  int max_id = MetricIdManager::kMaxId;
  paired_device_map[kthAddress(max_id - 3)] = max_id - 3;
  paired_device_map[kthAddress(max_id - 4)] = max_id - 4;

  ASSERT_TRUE(manager.Init(paired_device_map, callback, callback));

  // next id should be max_id - 2, max_id - 1, max_id, min_id + 2, min_id + 5
  ASSERT_EQ(manager.AllocateId(kthAddress(max_id - 2)), max_id - 2);
  ASSERT_EQ(manager.AllocateId(kthAddress(max_id - 1)), max_id - 1);
  ASSERT_EQ(manager.AllocateId(kthAddress(max_id)), max_id);
  ASSERT_EQ(manager.AllocateId(kthAddress(min_id + 2)), min_id + 2);
  ASSERT_EQ(manager.AllocateId(kthAddress(min_id + 5)), min_id + 5);

  ASSERT_TRUE(manager.Close());
}

TEST(BluetoothMetricIdManagerTest, MetricIdManagerWrapAroundTest2) {
  std::unordered_map<bluetooth::hci::Address, int> paired_device_map;
  auto& manager = MetricIdManager::GetInstance();
  MetricIdManager::Callback callback = [](
      const bluetooth::hci::Address&, const int) {
    return true;
  };

  // make a sparse paired_device_map
  int min_id = MetricIdManager::kMinId;
  int max_id = MetricIdManager::kMaxId;
  paired_device_map[kthAddress(max_id)] = max_id;

  ASSERT_TRUE(manager.Init(paired_device_map, callback, callback));

  // next id should be min_id, min_id + 1
  ASSERT_EQ(manager.AllocateId(kthAddress(min_id)), min_id);
  ASSERT_EQ(manager.AllocateId(kthAddress(min_id + 1)), min_id + 1);

  ASSERT_TRUE(manager.Close());
}

}  // namespace testing
