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

#pragma once
#include <list>
#include <vector>

#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace groups {

static constexpr int kGroupUnknown = -1;
static const bluetooth::Uuid kGenericContextUuid =
    bluetooth::Uuid::From16Bit(0x0000);

class DeviceGroupsCallbacks {
 public:
  virtual ~DeviceGroupsCallbacks() = default;

  /* Notifies first group appearance.
   * This callback also contains first group member and uuid of the group.
   */
  virtual void OnGroupAdded(const RawAddress& address,
                            const bluetooth::Uuid& group_uuid,
                            int group_id) = 0;

  /* Followed group members are notified with this callback */
  virtual void OnGroupMemberAdded(const RawAddress& address, int group_id) = 0;

  /* Group removal callback */
  virtual void OnGroupRemoved(const bluetooth::Uuid& group_uuid,
                              int group_id) = 0;

  /* Callback with group status update */
  virtual void OnGroupMemberRemoved(const RawAddress& address,
                                    int group_id) = 0;

  /* Callback with group information added from storage */
  virtual void OnGroupAddFromStorage(const RawAddress& address,
                                     const bluetooth::Uuid& group_uuid,
                                     int group_id) = 0;
};

class DeviceGroups {
 public:
  virtual ~DeviceGroups() = default;
  static void Initialize(DeviceGroupsCallbacks* callbacks);
  static void AddFromStorage(const RawAddress& addr,
                             const std::vector<uint8_t>& in);
  static bool GetForStorage(const RawAddress& addr, std::vector<uint8_t>& out);
  static void CleanUp(DeviceGroupsCallbacks* callbacks);
  static DeviceGroups* Get();
  static void DebugDump(int fd);
  /** To add to the existing group, group_id needs to be provided.
   *  Otherwise a new group for the given context uuid will be created.
   */
  virtual int AddDevice(
      const RawAddress& addr,
      bluetooth::Uuid uuid = bluetooth::groups::kGenericContextUuid,
      int group_id = bluetooth::groups::kGroupUnknown) = 0;
  virtual int GetGroupId(
      const RawAddress& addr,
      bluetooth::Uuid uuid = bluetooth::groups::kGenericContextUuid) const = 0;
  virtual void RemoveDevice(
      const RawAddress& addr,
      int group_id = bluetooth::groups::kGroupUnknown) = 0;
};

}  // namespace groups
}  // namespace bluetooth
