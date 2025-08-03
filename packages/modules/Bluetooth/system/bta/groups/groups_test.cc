/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

#include <base/logging.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "bta_groups.h"
#include "test/common/mock_functions.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace groups {

using ::testing::_;
using ::testing::DoAll;
using ::testing::Invoke;
using ::testing::Mock;
using ::testing::Return;
using ::testing::SaveArg;
using ::testing::SetArgPointee;
using ::testing::Test;

using bluetooth::groups::DeviceGroups;
using bluetooth::groups::DeviceGroupsCallbacks;

DeviceGroupsCallbacks* dev_callbacks;

RawAddress GetTestAddress(int index) {
  CHECK_LT(index, UINT8_MAX);
  RawAddress result = {
      {0xC0, 0xDE, 0xC0, 0xDE, 0x00, static_cast<uint8_t>(index)}};
  return result;
}

class MockGroupsCallbacks : public DeviceGroupsCallbacks {
 public:
  MockGroupsCallbacks() = default;
  MockGroupsCallbacks(const MockGroupsCallbacks&) = delete;
  MockGroupsCallbacks& operator=(const MockGroupsCallbacks&) = delete;

  ~MockGroupsCallbacks() override = default;

  MOCK_METHOD((void), OnGroupAdded,
              (const RawAddress& address, const bluetooth::Uuid& uuid,
               int group_id),
              (override));
  MOCK_METHOD((void), OnGroupMemberAdded,
              (const RawAddress& address, int group_id), (override));

  MOCK_METHOD((void), OnGroupRemoved,
              (const bluetooth::Uuid& uuid, int group_id), (override));
  MOCK_METHOD((void), OnGroupMemberRemoved,
              (const RawAddress& address, int group_id), (override));
  MOCK_METHOD((void), OnGroupAddFromStorage,
              (const RawAddress& address, const bluetooth::Uuid& uuid,
               int group_id),
              (override));
};

class GroupsTest : public ::testing::Test {
 protected:
  void SetUp() override {
    reset_mock_function_count_map();
    callbacks.reset(new MockGroupsCallbacks());
  }

  void TearDown() override { DeviceGroups::CleanUp(callbacks.get()); }

  std::unique_ptr<MockGroupsCallbacks> callbacks;
};

TEST_F(GroupsTest, test_initialize) {
  DeviceGroups::Initialize(callbacks.get());
  ASSERT_TRUE(DeviceGroups::Get());
  DeviceGroups::CleanUp(callbacks.get());
}

TEST_F(GroupsTest, test_initialize_twice) {
  DeviceGroups::Initialize(callbacks.get());
  DeviceGroups* dev_groups_p = DeviceGroups::Get();
  DeviceGroups::Initialize(callbacks.get());
  ASSERT_EQ(dev_groups_p, DeviceGroups::Get());
  DeviceGroups::CleanUp(callbacks.get());
  dev_groups_p->CleanUp(callbacks.get());
}

TEST_F(GroupsTest, test_cleanup_initialized) {
  DeviceGroups::Initialize(callbacks.get());
  DeviceGroups::CleanUp(callbacks.get());
  ASSERT_FALSE(DeviceGroups::Get());
}

TEST_F(GroupsTest, test_cleanup_uninitialized) {
  DeviceGroups::CleanUp(callbacks.get());
  ASSERT_FALSE(DeviceGroups::Get());
}

TEST_F(GroupsTest, test_groups_add_single_device) {
  EXPECT_CALL(*callbacks, OnGroupAdded(GetTestAddress(1), Uuid::kEmpty, 7));
  DeviceGroups::Initialize(callbacks.get());
  DeviceGroups::Get()->AddDevice(GetTestAddress(1), Uuid::kEmpty, 7);
  DeviceGroups::CleanUp(callbacks.get());
}

TEST_F(GroupsTest, test_groups_add_two_devices) {
  EXPECT_CALL(*callbacks, OnGroupAdded(GetTestAddress(1), _, 7));
  EXPECT_CALL(*callbacks, OnGroupMemberAdded(GetTestAddress(2), 7));
  DeviceGroups::Initialize(callbacks.get());
  DeviceGroups::Get()->AddDevice(GetTestAddress(1), Uuid::kEmpty, 7);
  DeviceGroups::Get()->AddDevice(GetTestAddress(2), Uuid::kEmpty, 7);
  DeviceGroups::CleanUp(callbacks.get());
}

TEST_F(GroupsTest, test_groups_remove_device) {
  EXPECT_CALL(*callbacks, OnGroupMemberRemoved(GetTestAddress(2), 7));
  DeviceGroups::Initialize(callbacks.get());
  DeviceGroups::Get()->AddDevice(GetTestAddress(2), Uuid::kEmpty, 7);
  DeviceGroups::Get()->RemoveDevice(GetTestAddress(2));
  ASSERT_EQ(kGroupUnknown,
            DeviceGroups::Get()->GetGroupId(GetTestAddress(2), Uuid::kEmpty));
  ASSERT_EQ(kGroupUnknown,
            DeviceGroups::Get()->GetGroupId(GetTestAddress(3), Uuid::kEmpty));
  DeviceGroups::CleanUp(callbacks.get());
}

TEST_F(GroupsTest, test_add_multiple_devices) {
  EXPECT_CALL(*callbacks, OnGroupAdded(GetTestAddress(2), Uuid::kEmpty, 7));
  EXPECT_CALL(*callbacks, OnGroupMemberAdded(_, 7)).Times(2);
  DeviceGroups::Initialize(callbacks.get());
  DeviceGroups::Get()->AddDevice(GetTestAddress(2), Uuid::kEmpty, 7);
  DeviceGroups::Get()->AddDevice(GetTestAddress(3), Uuid::kEmpty, 7);
  DeviceGroups::Get()->AddDevice(GetTestAddress(4), Uuid::kEmpty, 7);
  DeviceGroups::CleanUp(callbacks.get());
}

TEST_F(GroupsTest, test_remove_multiple_devices) {
  EXPECT_CALL(*callbacks, OnGroupMemberRemoved(_, _)).Times(3);
  DeviceGroups::Initialize(callbacks.get());
  DeviceGroups::Get()->AddDevice(GetTestAddress(2), Uuid::kEmpty, 7);
  DeviceGroups::Get()->AddDevice(GetTestAddress(3), Uuid::kEmpty, 7);
  DeviceGroups::Get()->AddDevice(GetTestAddress(4), Uuid::kEmpty, 7);
  DeviceGroups::Get()->RemoveDevice(GetTestAddress(2));
  DeviceGroups::Get()->RemoveDevice(GetTestAddress(3));
  DeviceGroups::Get()->RemoveDevice(GetTestAddress(4));
  DeviceGroups::CleanUp(callbacks.get());
}

TEST_F(GroupsTest, test_add_multiple_groups) {
  EXPECT_CALL(*callbacks, OnGroupAdded(_, _, _)).Times(2);
  DeviceGroups::Initialize(callbacks.get());
  DeviceGroups::Get()->AddDevice(GetTestAddress(1), Uuid::kEmpty, 8);
  DeviceGroups::Get()->AddDevice(GetTestAddress(1), Uuid::kEmpty, 9);
  DeviceGroups::CleanUp(callbacks.get());
}

TEST_F(GroupsTest, test_remove_multiple_groups) {
  Uuid uuid1 = Uuid::GetRandom();
  Uuid uuid2 = Uuid::GetRandom();
  ASSERT_NE(uuid1, uuid2);

  EXPECT_CALL(*callbacks, OnGroupAdded(_, _, _)).Times(2);
  DeviceGroups::Initialize(callbacks.get());
  DeviceGroups::Get()->AddDevice(GetTestAddress(1), uuid1, 8);
  DeviceGroups::Get()->AddDevice(GetTestAddress(1), uuid2, 9);
  DeviceGroups::Get()->AddDevice(GetTestAddress(2), uuid2, 9);

  EXPECT_CALL(*callbacks, OnGroupMemberRemoved(GetTestAddress(1), 8));
  EXPECT_CALL(*callbacks, OnGroupMemberRemoved(GetTestAddress(1), 9));
  EXPECT_CALL(*callbacks, OnGroupRemoved(uuid1, 8));
  EXPECT_CALL(*callbacks, OnGroupRemoved(uuid2, 9)).Times(0);
  DeviceGroups::Get()->RemoveDevice(GetTestAddress(1));

  DeviceGroups::CleanUp(callbacks.get());
}

TEST_F(GroupsTest, test_remove_device_fo_devices) {
  Uuid uuid1 = Uuid::GetRandom();
  Uuid uuid2 = Uuid::GetRandom();
  EXPECT_CALL(*callbacks, OnGroupAdded(_, _, _)).Times(2);
  DeviceGroups::Initialize(callbacks.get());
  DeviceGroups::Get()->AddDevice(GetTestAddress(1), uuid1, 8);
  DeviceGroups::Get()->AddDevice(GetTestAddress(1), uuid2, 9);

  EXPECT_CALL(*callbacks, OnGroupRemoved(uuid1, 8));
  EXPECT_CALL(*callbacks, OnGroupRemoved(uuid2, 9)).Times(0);

  DeviceGroups::Get()->RemoveDevice(GetTestAddress(1), 8);

  Mock::VerifyAndClearExpectations(&callbacks);

  EXPECT_CALL(*callbacks, OnGroupRemoved(uuid1, 8)).Times(0);
  EXPECT_CALL(*callbacks, OnGroupRemoved(uuid2, 9));

  DeviceGroups::Get()->RemoveDevice(GetTestAddress(1), 9);
}

TEST_F(GroupsTest, test_add_devices_different_group_id) {
  DeviceGroups::Initialize(callbacks.get());
  DeviceGroups::Get()->AddDevice(GetTestAddress(2), Uuid::kEmpty, 10);
  DeviceGroups::Get()->AddDevice(GetTestAddress(3), Uuid::kEmpty, 11);
  auto group_id_1 =
      DeviceGroups::Get()->GetGroupId(GetTestAddress(2), Uuid::kEmpty);
  auto group_id_2 =
      DeviceGroups::Get()->GetGroupId(GetTestAddress(3), Uuid::kEmpty);
  ASSERT_TRUE(group_id_1 != group_id_2);
  DeviceGroups::CleanUp(callbacks.get());
}

TEST_F(GroupsTest, test_group_id_assign) {
  int captured_gid1 = kGroupUnknown;
  int captured_gid2 = kGroupUnknown;

  DeviceGroups::Initialize(callbacks.get());
  EXPECT_CALL(*callbacks, OnGroupAdded(GetTestAddress(1), _, _))
      .WillOnce(SaveArg<2>(&captured_gid1));
  EXPECT_CALL(*callbacks, OnGroupAdded(GetTestAddress(2), _, _))
      .WillOnce(SaveArg<2>(&captured_gid2));

  int gid1 = DeviceGroups::Get()->AddDevice(GetTestAddress(1), Uuid::kEmpty,
                                            bluetooth::groups::kGroupUnknown);
  int gid2 = DeviceGroups::Get()->AddDevice(GetTestAddress(2), Uuid::kEmpty);
  int gid3 = DeviceGroups::Get()->AddDevice(GetTestAddress(2), Uuid::kEmpty);
  ASSERT_NE(bluetooth::groups::kGroupUnknown, gid1);
  ASSERT_NE(bluetooth::groups::kGroupUnknown, gid2);
  ASSERT_EQ(gid2, gid3);
  ASSERT_EQ(gid1, captured_gid1);
  ASSERT_EQ(gid2, captured_gid2);

  DeviceGroups::CleanUp(callbacks.get());
}

TEST_F(GroupsTest, test_storage_calls) {
  ASSERT_EQ(0, get_func_call_count("btif_storage_load_bonded_groups"));
  DeviceGroups::Initialize(callbacks.get());
  ASSERT_EQ(1, get_func_call_count("btif_storage_load_bonded_groups"));

  ASSERT_EQ(0, get_func_call_count("btif_storage_add_groups"));
  DeviceGroups::Get()->AddDevice(GetTestAddress(1), Uuid::kEmpty, 7);
  DeviceGroups::Get()->AddDevice(GetTestAddress(1), Uuid::kEmpty, 8);
  ASSERT_EQ(2, get_func_call_count("btif_storage_add_groups"));

  DeviceGroups::Get()->AddDevice(GetTestAddress(2), Uuid::kEmpty, 7);
  DeviceGroups::Get()->AddDevice(GetTestAddress(3), Uuid::kEmpty, 7);
  ASSERT_EQ(4, get_func_call_count("btif_storage_add_groups"));

  ASSERT_EQ(0, get_func_call_count("btif_storage_remove_groups"));
  DeviceGroups::Get()->RemoveDevice(GetTestAddress(1));
  DeviceGroups::Get()->RemoveDevice(GetTestAddress(2));
  DeviceGroups::Get()->RemoveDevice(GetTestAddress(3));
  ASSERT_EQ(3, get_func_call_count("btif_storage_remove_groups"));

  DeviceGroups::CleanUp(callbacks.get());
}

TEST_F(GroupsTest, test_storage_content) {
  int gid1 = bluetooth::groups::kGroupUnknown;
  int gid2 = bluetooth::groups::kGroupUnknown;
  Uuid uuid1 = Uuid::GetRandom();
  Uuid uuid2 = Uuid::GetRandom();
  ASSERT_NE(uuid1, uuid2);

  DeviceGroups::Initialize(callbacks.get());
  gid1 = DeviceGroups::Get()->AddDevice(GetTestAddress(1), uuid1, gid1);
  DeviceGroups::Get()->AddDevice(GetTestAddress(2), uuid1, gid1);
  gid2 = DeviceGroups::Get()->AddDevice(GetTestAddress(2), uuid2, gid2);
  ASSERT_NE(bluetooth::groups::kGroupUnknown, gid1);
  ASSERT_NE(gid1, gid2);

  std::vector<uint8_t> dev1_storage;
  std::vector<uint8_t> dev2_storage;

  // Store to byte buffer
  DeviceGroups::GetForStorage(GetTestAddress(1), dev1_storage);
  DeviceGroups::GetForStorage(GetTestAddress(2), dev2_storage);
  ASSERT_NE(0u, dev1_storage.size());
  ASSERT_TRUE(dev2_storage.size() > dev1_storage.size());

  // Clean it up
  DeviceGroups::CleanUp(callbacks.get());
  ASSERT_EQ(nullptr, DeviceGroups::Get());

  // Restore dev1 from the byte buffer
  DeviceGroups::Initialize(callbacks.get());
  EXPECT_CALL(*callbacks, OnGroupAdded(GetTestAddress(1), uuid1, gid1));
  DeviceGroups::AddFromStorage(GetTestAddress(1), dev1_storage);

  // Restore dev2 from the byte buffer
  EXPECT_CALL(*callbacks, OnGroupAdded(GetTestAddress(2), uuid2, gid2));
  EXPECT_CALL(*callbacks, OnGroupMemberAdded(GetTestAddress(2), gid1)).Times(1);
  DeviceGroups::AddFromStorage(GetTestAddress(2), dev2_storage);

  DeviceGroups::CleanUp(callbacks.get());
}

}  // namespace groups
}  // namespace bluetooth
