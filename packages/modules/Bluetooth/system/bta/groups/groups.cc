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
#include <bluetooth/log.h>

#include <algorithm>
#include <limits>
#include <map>
#include <mutex>
#include <unordered_set>

#include "bta_groups.h"
#include "btif/include/btif_profile_storage.h"
#include "os/logging/log_adapter.h"
#include "stack/include/bt_types.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using bluetooth::Uuid;

namespace bluetooth {
namespace groups {

class DeviceGroupsImpl;
DeviceGroupsImpl* instance;
std::mutex instance_mutex;
static constexpr int kMaxGroupId = 0xEF;

class DeviceGroup {
 public:
  DeviceGroup(int group_id, Uuid uuid)
      : group_id_(group_id), group_uuid_(uuid) {}
  void Add(const RawAddress& addr) { devices_.insert(addr); }
  void Remove(const RawAddress& addr) { devices_.erase(addr); }
  bool Contains(const RawAddress& addr) const {
    return (devices_.count(addr) != 0);
  }

  void ForEachDevice(std::function<void(const RawAddress&)> cb) const {
    for (auto const& addr : devices_) {
      cb(addr);
    }
  }

  int Size(void) const { return devices_.size(); }
  int GetGroupId(void) const { return group_id_; }
  const Uuid& GetUuid(void) const { return group_uuid_; }

 private:
  friend std::ostream& operator<<(std::ostream& out,
                                  const bluetooth::groups::DeviceGroup& value);
  int group_id_;
  Uuid group_uuid_;
  std::unordered_set<RawAddress> devices_;
};

class DeviceGroupsImpl : public DeviceGroups {
  static constexpr uint8_t GROUP_STORAGE_CURRENT_LAYOUT_MAGIC = 0x10;
  static constexpr size_t GROUP_STORAGE_HEADER_SZ =
      sizeof(GROUP_STORAGE_CURRENT_LAYOUT_MAGIC) +
      sizeof(uint8_t); /* num_of_groups */
  static constexpr size_t GROUP_STORAGE_ENTRY_SZ =
      sizeof(uint8_t) /* group_id */ + Uuid::kNumBytes128;

 public:
  DeviceGroupsImpl(DeviceGroupsCallbacks* callbacks) {
    AddCallbacks(callbacks);
    btif_storage_load_bonded_groups();
  }

  int GetGroupId(const RawAddress& addr, Uuid uuid) const override {
    for (const auto& [id, g] : groups_) {
      if ((g.Contains(addr)) && (uuid == g.GetUuid())) return id;
    }
    return kGroupUnknown;
  }

  void add_to_group(const RawAddress& addr, DeviceGroup* group) {
    group->Add(addr);

    bool first_device_in_group = (group->Size() == 1);

    for (auto c : callbacks_) {
      if (first_device_in_group) {
        c->OnGroupAdded(addr, group->GetUuid(), group->GetGroupId());
      } else {
        c->OnGroupMemberAdded(addr, group->GetGroupId());
      }
    }
  }

  int AddDevice(const RawAddress& addr, Uuid uuid, int group_id) override {
    DeviceGroup* group = nullptr;

    if (group_id == kGroupUnknown) {
      auto gid = GetGroupId(addr, uuid);
      if (gid != kGroupUnknown) return gid;
      group = create_group(uuid);
    } else {
      group = get_or_create_group_with_id(group_id, uuid);
      if (!group) {
        return kGroupUnknown;
      }
    }

    LOG_ASSERT(group);

    if (group->Contains(addr)) {
      log::error("device {} already in the group: {}",
                 ADDRESS_TO_LOGGABLE_STR(addr), group_id);
      return group->GetGroupId();
    }

    add_to_group(addr, group);

    btif_storage_add_groups(addr);
    return group->GetGroupId();
  }

  void RemoveDevice(const RawAddress& addr, int group_id) override {
    int num_of_groups_dev_belongs = 0;

    /* Remove from all the groups. Usually happens on unbond */
    for (auto it = groups_.begin(); it != groups_.end();) {
      auto& [id, g] = *it;
      if (!g.Contains(addr)) {
        ++it;
        continue;
      }

      num_of_groups_dev_belongs++;

      if ((group_id != bluetooth::groups::kGroupUnknown) && (group_id != id)) {
        ++it;
        continue;
      }

      num_of_groups_dev_belongs--;

      g.Remove(addr);
      for (auto c : callbacks_) {
        c->OnGroupMemberRemoved(addr, id);
      }

      if (g.Size() == 0) {
        for (auto c : callbacks_) {
          c->OnGroupRemoved(g.GetUuid(), g.GetGroupId());
        }
        it = groups_.erase(it);
      } else {
        ++it;
      }
    }

    btif_storage_remove_groups(addr);
    if (num_of_groups_dev_belongs > 0) {
      btif_storage_add_groups(addr);
    }
  }

  bool SerializeGroups(const RawAddress& addr,
                       std::vector<uint8_t>& out) const {
    auto num_groups = std::count_if(
        groups_.begin(), groups_.end(), [&addr](auto& id_group_pair) {
          return id_group_pair.second.Contains(addr);
        });
    if ((num_groups == 0) || (num_groups > std::numeric_limits<uint8_t>::max()))
      return false;

    out.resize(GROUP_STORAGE_HEADER_SZ + (num_groups * GROUP_STORAGE_ENTRY_SZ));
    auto* ptr = out.data();

    /* header */
    UINT8_TO_STREAM(ptr, GROUP_STORAGE_CURRENT_LAYOUT_MAGIC);
    UINT8_TO_STREAM(ptr, num_groups);

    /* group entries */
    for (const auto& [id, g] : groups_) {
      if (g.Contains(addr)) {
        UINT8_TO_STREAM(ptr, id);

        Uuid::UUID128Bit uuid128 = g.GetUuid().To128BitLE();
        memcpy(ptr, uuid128.data(), Uuid::kNumBytes128);
        ptr += Uuid::kNumBytes128;
      }
    }

    return true;
  }

  void DeserializeGroups(const RawAddress& addr,
                         const std::vector<uint8_t>& in) {
    if (in.size() < GROUP_STORAGE_HEADER_SZ + GROUP_STORAGE_ENTRY_SZ) return;

    auto* ptr = in.data();

    uint8_t magic;
    STREAM_TO_UINT8(magic, ptr);

    if (magic == GROUP_STORAGE_CURRENT_LAYOUT_MAGIC) {
      uint8_t num_groups;
      STREAM_TO_UINT8(num_groups, ptr);

      if (in.size() <
          GROUP_STORAGE_HEADER_SZ + (num_groups * GROUP_STORAGE_ENTRY_SZ)) {
        log::error("Invalid persistent storage data");
        return;
      }

      /* group entries */
      while (num_groups--) {
        uint8_t id;
        STREAM_TO_UINT8(id, ptr);

        Uuid::UUID128Bit uuid128;
        STREAM_TO_ARRAY(uuid128.data(), ptr, (int)Uuid::kNumBytes128);

        auto* group =
            get_or_create_group_with_id(id, Uuid::From128BitLE(uuid128));
        if (group) add_to_group(addr, group);

        for (auto c : callbacks_) {
          c->OnGroupAddFromStorage(addr, Uuid::From128BitLE(uuid128), id);
        }
      }
    }
  }

  void AddCallbacks(DeviceGroupsCallbacks* callbacks) {
    callbacks_.push_back(std::move(callbacks));

    /* Notify new user about known groups */
    for (const auto& [id, g] : groups_) {
      auto group_uuid = g.GetUuid();
      auto group_id = g.GetGroupId();
      g.ForEachDevice([&](auto& dev) {
        callbacks->OnGroupAdded(dev, group_uuid, group_id);
      });
    }
  }

  bool Clear(DeviceGroupsCallbacks* callbacks) {
    auto it = find_if(callbacks_.begin(), callbacks_.end(),
                      [callbacks](auto c) { return c == callbacks; });

    if (it != callbacks_.end()) callbacks_.erase(it);

    if (callbacks_.size() != 0) {
      return false;
    }
    /* When all clients were unregistered */
    groups_.clear();
    return true;
  }

  void Dump(int fd) {
    std::stringstream stream;

    stream << "  Num. registered clients: " << callbacks_.size() << std::endl;
    stream << "  Groups:\n";
    for (const auto& kv_pair : groups_) {
      stream << kv_pair.second << std::endl;
    }

    dprintf(fd, "%s", stream.str().c_str());
  }

 private:
  DeviceGroup* find_device_group(int group_id) {
    return groups_.count(group_id) ? &groups_.at(group_id) : nullptr;
  }

  DeviceGroup* get_or_create_group_with_id(int group_id, Uuid uuid) {
    auto group = find_device_group(group_id);
    if (group) {
      if (group->GetUuid() != uuid) {
        log::error(
            "group {} exists but for different uuid: {}, user request uuid: {}",
            group_id, group->GetUuid(), uuid);
        return nullptr;
      }

      log::info("group already exists: {}", group_id);
      return group;
    }

    DeviceGroup new_group(group_id, uuid);
    groups_.insert({group_id, std::move(new_group)});

    return &groups_.at(group_id);
  }

  DeviceGroup* create_group(Uuid& uuid) {
    /* Generate new group id and return empty group */
    /* Find first free id */

    int group_id = -1;
    for (int i = 1; i < kMaxGroupId; i++) {
      if (groups_.count(i) == 0) {
        group_id = i;
        break;
      }
    }

    if (group_id < 0) {
      log::error("too many groups");
      return nullptr;
    }

    DeviceGroup group(group_id, uuid);
    groups_.insert({group_id, std::move(group)});

    return &groups_.at(group_id);
  }

  std::map<int, DeviceGroup> groups_;
  std::list<DeviceGroupsCallbacks*> callbacks_;
};

void DeviceGroups::Initialize(DeviceGroupsCallbacks* callbacks) {
  std::scoped_lock<std::mutex> lock(instance_mutex);
  if (instance == nullptr) {
    instance = new DeviceGroupsImpl(callbacks);
    return;
  }

  instance->AddCallbacks(callbacks);
}

void DeviceGroups::AddFromStorage(const RawAddress& addr,
                                  const std::vector<uint8_t>& in) {
  if (!instance) {
    log::error("Not initialized yet");
    return;
  }

  instance->DeserializeGroups(addr, in);
}

bool DeviceGroups::GetForStorage(const RawAddress& addr,
                                 std::vector<uint8_t>& out) {
  if (!instance) {
    log::error("Not initialized yet");
    return false;
  }

  return instance->SerializeGroups(addr, out);
}

void DeviceGroups::CleanUp(DeviceGroupsCallbacks* callbacks) {
  std::scoped_lock<std::mutex> lock(instance_mutex);
  if (!instance) return;

  if (instance->Clear(callbacks)) {
    delete (instance);
    instance = nullptr;
  }
}

std::ostream& operator<<(std::ostream& out,
                         bluetooth::groups::DeviceGroup const& group) {
  out << "    == Group id: " << group.group_id_ << " == \n"
      << "      Uuid: " << group.group_uuid_ << std::endl;
  out << "      Devices:\n";
  for (auto const& addr : group.devices_) {
    out << "        " << ADDRESS_TO_LOGGABLE_STR(addr) << std::endl;
  }
  return out;
}

void DeviceGroups::DebugDump(int fd) {
  std::scoped_lock<std::mutex> lock(instance_mutex);
  dprintf(fd, "Device Groups Manager:\n");
  if (instance)
    instance->Dump(fd);
  else
    dprintf(fd, "  Not initialized \n");
}

DeviceGroups* DeviceGroups::Get() { return instance; }

}  // namespace groups
}  // namespace bluetooth
