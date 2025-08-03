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

#include <base/functional/bind.h>
#include <base/functional/callback.h>
#include <base/logging.h>
#include <base/strings/string_number_conversions.h>
#include <bluetooth/log.h>
#include <hardware/bt_csis.h>
#include <hardware/bt_gatt_types.h>

#include <list>
#include <mutex>
#include <string>
#include <vector>

#include "advertise_data_parser.h"
#include "bta_api.h"
#include "bta_csis_api.h"
#include "bta_gatt_api.h"
#include "bta_gatt_queue.h"
#include "bta_groups.h"
#include "bta_le_audio_uuids.h"
#include "bta_sec_api.h"
#include "btif/include/btif_storage.h"
#include "common/init_flags.h"
#include "crypto_toolbox/crypto_toolbox.h"
#include "csis_types.h"
#include "gap_api.h"
#include "gatt_api.h"
#include "include/check.h"
#include "internal_include/bt_target.h"
#include "internal_include/bt_trace.h"
#include "main/shim/le_scanning_manager.h"
#include "osi/include/osi.h"
#include "osi/include/stack_power_telemetry.h"
#include "stack/btm/btm_sec.h"
#include "stack/gatt/gatt_int.h"
#include "stack/include/bt_types.h"
#include "stack/include/btm_ble_sec_api.h"

using base::Closure;
using bluetooth::Uuid;
using bluetooth::csis::ConnectionState;
using bluetooth::csis::CsisClient;
using bluetooth::csis::CsisDevice;
using bluetooth::csis::CsisDiscoveryState;
using bluetooth::csis::CsisGroup;
using bluetooth::csis::CsisGroupLockStatus;
using bluetooth::csis::CsisInstance;
using bluetooth::csis::CsisLockCb;
using bluetooth::csis::CsisLockState;
using bluetooth::csis::kCsisLockUuid;
using bluetooth::csis::kCsisRankUuid;
using bluetooth::csis::kCsisServiceUuid;
using bluetooth::csis::kCsisSirkUuid;
using bluetooth::csis::kCsisSizeUuid;

using bluetooth::groups::DeviceGroups;
using bluetooth::groups::DeviceGroupsCallbacks;

using namespace bluetooth;

namespace {
class CsisClientImpl;
CsisClientImpl* instance;
std::mutex instance_mutex;
DeviceGroupsCallbacks* device_group_callbacks;

/**
 * -----------------------------------------------------------------------------
 * Coordinated Set Service - Client role
 * -----------------------------------------------------------------------------
 *
 * CSIP allows to organize audio servers into sets e.g. Stereo Set, 5.1 Set
 * and speed up connecting it.
 *
 * Since leaudio has already grouping API it was decided to integrate here CSIS
 * and allow it to group devices semi-automatically.
 *
 * Flow:
 * If connected device contains CSIS services, and it is included into CAP
 * service or is not included at all, implementation reads all its
 * characteristisc. The only mandatory characteristic is Set Identity Resolving
 * Key (SIRK) and once this is read implementation assumes there is at least 2
 * devices in the set and start to search for other members by looking for new
 * Advertising Type (RSI Type) and Resolvable Set Identifier (RSI) in it.
 * In the meantime other CSIS characteristics are read and Set Size might be
 * updated. When new set member is found, there is callback called to upper
 * layer with the address and group id for which member has been found. During
 * this time Search is stopped. Upper layers bonds new devices and connect Le
 * Audio profile. If there are other members to find, implementations repeats
 * the procedure.
 *
 */

class CsisClientImpl : public CsisClient {
  static constexpr uint8_t CSIS_STORAGE_CURRENT_LAYOUT_MAGIC = 0x10;
  static constexpr size_t CSIS_STORAGE_HEADER_SZ =
      sizeof(CSIS_STORAGE_CURRENT_LAYOUT_MAGIC) +
      sizeof(uint8_t); /* num_of_sets */
  static constexpr size_t CSIS_STORAGE_ENTRY_SZ =
      sizeof(uint8_t) /* set_id */ + sizeof(uint8_t) /* desired_size */ +
      sizeof(uint8_t) /* rank */ + Octet16().size();

 public:
  CsisClientImpl(bluetooth::csis::CsisClientCallbacks* callbacks,
                 Closure initCb)
      : gatt_if_(0), callbacks_(callbacks) {
    BTA_GATTC_AppRegister(
        [](tBTA_GATTC_EVT event, tBTA_GATTC* p_data) {
          if (instance && p_data) instance->GattcCallback(event, p_data);
        },
        base::Bind(
            [](Closure initCb, uint8_t client_id, uint8_t status) {
              if (status != GATT_SUCCESS) {
                log::error(
                    "Can't start Coordinated Set Service client profile - no "
                    "gatt clients left!");
                return;
              }
              instance->gatt_if_ = client_id;
              initCb.Run();

              DeviceGroups::Initialize(device_group_callbacks);
              instance->dev_groups_ = DeviceGroups::Get();
            },
            initCb),
        true);

    BTA_DmSirkSecCbRegister([](tBTA_DM_SEC_EVT event, tBTA_DM_SEC* p_data) {
      if (event != BTA_DM_SIRK_VERIFICATION_REQ_EVT) {
        log::error("Invalid event received by CSIP: {}",
                   static_cast<int>(event));
        return;
      }

      instance->VerifySetMember(p_data->ble_req.bd_addr);
    });

    log::debug("Background scan enabled");
    CsisObserverSetBackground(true);
  }

  ~CsisClientImpl() override = default;

  std::shared_ptr<bluetooth::csis::CsisGroup> AssignCsisGroup(
      const RawAddress& address, int group_id,
      bool create_group_if_non_existing, const bluetooth::Uuid& uuid) {
    log::debug("Device: {}, group_id: {}", ADDRESS_TO_LOGGABLE_CSTR(address),
               group_id);
    auto csis_group = FindCsisGroup(group_id);
    if (!csis_group) {
      if (create_group_if_non_existing) {
        /* Let's create a group */
        log::debug(": Create a new group {}", group_id);
        auto g = std::make_shared<CsisGroup>(group_id, uuid);
        csis_groups_.push_back(g);
        csis_group = FindCsisGroup(group_id);
      } else {
        log::error(": Missing group - that shall not happen");
        return nullptr;
      }
    }

    auto device = FindDeviceByAddress(address);
    if (device == nullptr) {
      auto dev = std::make_shared<CsisDevice>(address, false);
      devices_.push_back(dev);
      device = FindDeviceByAddress(address);
    }

    if (!csis_group->IsDeviceInTheGroup(device)) csis_group->AddDevice(device);

    return csis_group;
  }

  void OnGroupAddedCb(const RawAddress& address, const bluetooth::Uuid& uuid,
                      int group_id) {
    log::debug("address: {}, uuid: {}, group_id: {}",
               ADDRESS_TO_LOGGABLE_CSTR(address), uuid.ToString(), group_id);

    AssignCsisGroup(address, group_id, true, uuid);
  }

  void OnGroupMemberAddedCb(const RawAddress& address, int group_id) {
    log::debug("{}, group_id: {}", ADDRESS_TO_LOGGABLE_CSTR(address), group_id);

    AssignCsisGroup(address, group_id, false, Uuid::kEmpty);
  }

  void OnGroupRemovedCb(const bluetooth::Uuid& uuid, int group_id) {
    RemoveCsisGroup(group_id);
  }

  void OnGroupMemberRemovedCb(const RawAddress& address, int group_id) {
    log::debug("{}, group_id: {}", ADDRESS_TO_LOGGABLE_CSTR(address), group_id);

    auto device = FindDeviceByAddress(address);
    if (device) RemoveCsisDevice(device, group_id);
  }

  void OnGroupAddFromStorageCb(const RawAddress& address,
                               const bluetooth::Uuid& uuid, int group_id) {
    auto device = FindDeviceByAddress(address);
    if (device == nullptr) return;

    auto csis_group = FindCsisGroup(group_id);
    if (csis_group == nullptr) {
      log::error("the csis group (id: {} ) does not exist", group_id);
      return;
    }

    if (!csis_group->IsDeviceInTheGroup(device)) {
      log::error("the csis group (id: {} ) does contain the device: {}",
                 group_id, ADDRESS_TO_LOGGABLE_CSTR(address));
      return;
    }

    if (csis_group->GetUuid() == Uuid::kEmpty) {
      csis_group->SetUuid(uuid);
    }

    int rank = bluetooth::csis::CSIS_RANK_INVALID;
    auto csis_instance = device->GetCsisInstanceByGroupId(group_id);
    if (csis_instance) {
      rank = csis_instance->GetRank();
    }

    callbacks_->OnDeviceAvailable(device->addr, csis_group->GetGroupId(),
                                  csis_group->GetDesiredSize(), rank, uuid);
  }

  void Connect(const RawAddress& address) override {
    log::debug("{}", ADDRESS_TO_LOGGABLE_CSTR(address));

    auto device = FindDeviceByAddress(address);
    if (device == nullptr) {
      devices_.emplace_back(std::make_shared<CsisDevice>(address, true));
    } else {
      device->connecting_actively = true;
    }

    BTA_GATTC_Open(gatt_if_, address, BTM_BLE_DIRECT_CONNECTION, false);
  }

  void Disconnect(const RawAddress& addr) override {
    log::debug("{}", ADDRESS_TO_LOGGABLE_CSTR(addr));

    auto device = FindDeviceByAddress(addr);
    if (device == nullptr) {
      log::warn("Device not connected to profile {}",
                ADDRESS_TO_LOGGABLE_CSTR(addr));
      callbacks_->OnConnectionState(addr, ConnectionState::DISCONNECTED);
      return;
    }

    /* Removes all active connections or registrations for connection */
    if (device->IsConnected()) {
      BTA_GATTC_Close(device->conn_id);
    } else {
      BTA_GATTC_CancelOpen(gatt_if_, addr, false);
      DoDisconnectCleanUp(device);
      callbacks_->OnConnectionState(addr, ConnectionState::DISCONNECTED);
    }
  }

  void RemoveDevice(const RawAddress& addr) override {
    log::debug("{}", ADDRESS_TO_LOGGABLE_CSTR(addr));

    auto device = FindDeviceByAddress(addr);
    if (!device) return;

    Disconnect(addr);

    dev_groups_->RemoveDevice(addr);
  }

  int GetGroupId(const RawAddress& addr, Uuid uuid) override {
    auto device = FindDeviceByAddress(addr);
    if (device == nullptr) return bluetooth::groups::kGroupUnknown;

    int group_id = dev_groups_->GetGroupId(addr, uuid);
    auto csis_group = FindCsisGroup(group_id);
    if (csis_group == nullptr) return bluetooth::groups::kGroupUnknown;

    return csis_group->GetGroupId();
  }

  void HandleCsisLockProcedureError(
      std::shared_ptr<CsisGroup>& csis_group,
      std::shared_ptr<CsisDevice>& csis_device,
      CsisGroupLockStatus status =
          CsisGroupLockStatus::FAILED_LOCKED_BY_OTHER) {
    /* Clear information about ongoing lock procedure */
    CsisLockCb cb = csis_group->GetLockCb();
    csis_group->SetTargetLockState(CsisLockState::CSIS_STATE_UNSET);

    int group_id = csis_group->GetGroupId();
    /* Send unlock to previous devices. It shall be done in reverse order. */
    auto prev_dev = csis_group->GetPrevDevice(csis_device);
    while (prev_dev) {
      if (prev_dev->IsConnected()) {
        auto prev_csis_instance = prev_dev->GetCsisInstanceByGroupId(group_id);
        LOG_ASSERT(prev_csis_instance) << " prev_csis_instance does not exist!";
        SetLock(prev_dev, prev_csis_instance,
                CsisLockState::CSIS_STATE_UNLOCKED);
      }
      prev_dev = csis_group->GetPrevDevice(prev_dev);
    }
    /* Call application callback */
    NotifyGroupStatus(group_id, false, status, std::move(cb));
  }

  void OnGattCsisWriteLockRsp(uint16_t conn_id, tGATT_STATUS status,
                              uint16_t handle, void* data) {
    auto device = FindDeviceByConnId(conn_id);
    if (device == nullptr) {
      log::error("Device not there for conn_id: 0x{:04x}", conn_id);
      return;
    }

    int group_id = PTR_TO_UINT(data);
    auto csis_group = FindCsisGroup(group_id);
    if (csis_group == nullptr) {
      log::error("There is no group: {}", group_id);
      return;
    }

    CsisLockState target_lock_state = csis_group->GetTargetLockState();

    log::debug("Device {}, target lock: {}, status: 0x{:02x}",
               ADDRESS_TO_LOGGABLE_CSTR(device->addr), (int)target_lock_state,
               (int)status);
    if (target_lock_state == CsisLockState::CSIS_STATE_UNSET) return;

    if (status != GATT_SUCCESS &&
        status != bluetooth::csis::kCsisErrorCodeLockAlreadyGranted) {
      if (target_lock_state == CsisLockState::CSIS_STATE_UNLOCKED) {
        /* When unlocking just drop the counter on error and that is it */
        csis_group->UpdateLockTransitionCnt(-1);
        return;
      }

      /* In case of GATT ERROR */
      log::error("Incorrect write status=0x{:02x}", (int)(status));

      /* Unlock previous devices */
      HandleCsisLockProcedureError(csis_group, device);

      if (status == GATT_DATABASE_OUT_OF_SYNC) {
        log::info("Database out of sync for {}",
                  ADDRESS_TO_LOGGABLE_CSTR(device->addr));
        ClearDeviceInformationAndStartSearch(device);
      }
      return;
    }

    /* All is good, continue. Try to send lock to other devices.*/
    auto csis_instance = device->GetCsisInstanceByGroupId(group_id);
    LOG_ASSERT(csis_instance) << " csis_instance does not exist!";
    csis_instance->SetLockState(target_lock_state);

    if (csis_group->GetLockTransitionCnt() == 0) {
      log::error("Not expected lock state");
      return;
    }

    if (csis_group->UpdateLockTransitionCnt(-1) == 0) {
      csis_group->SetCurrentLockState(csis_group->GetTargetLockState());
      CsisLockCompleted(
          csis_group,
          csis_group->GetCurrentLockState() == CsisLockState::CSIS_STATE_LOCKED,
          CsisGroupLockStatus::SUCCESS);
      return;
    }

    if (target_lock_state == CsisLockState::CSIS_STATE_LOCKED) {
      std::shared_ptr<CsisDevice> next_dev;

      do {
        next_dev = csis_group->GetNextDevice(device);
        if (!next_dev) break;
      } while (!next_dev->IsConnected());

      if (next_dev) {
        auto next_csis_inst = next_dev->GetCsisInstanceByGroupId(group_id);
        LOG_ASSERT(csis_instance) << " csis_instance does not exist!";
#if CSIP_UPPER_TESTER_FORCE_TO_SEND_LOCK == FALSE
        if (next_csis_inst->GetLockState() ==
            CsisLockState::CSIS_STATE_LOCKED) {
          /* Somebody else managed to lock it.
           * Unlock previous devices
           */
          HandleCsisLockProcedureError(csis_group, next_dev);
          return;
        }
#endif
        SetLock(next_dev, next_csis_inst, CsisLockState::CSIS_STATE_LOCKED);
      }
    }
  }

  void SetLock(std::shared_ptr<CsisDevice>& device,
               std::shared_ptr<CsisInstance>& csis_instance,
               CsisLockState lock) {
    std::vector<uint8_t> value = {
        (std::underlying_type<CsisLockState>::type)lock};

    log::info("{},  rank: {}, conn_id: 0x{:04x}, handle: 0x{:04x}",
              ADDRESS_TO_LOGGABLE_CSTR(device->addr), csis_instance->GetRank(),
              device->conn_id, csis_instance->svc_data.lock_handle.val_hdl);

    BtaGattQueue::WriteCharacteristic(
        device->conn_id, csis_instance->svc_data.lock_handle.val_hdl, value,
        GATT_WRITE,
        [](uint16_t conn_id, tGATT_STATUS status, uint16_t handle, uint16_t len,
           const uint8_t* value, void* data) {
          if (instance)
            instance->OnGattCsisWriteLockRsp(conn_id, status, handle, data);
        },
        UINT_TO_PTR(csis_instance->GetGroupId()));
  }

  void NotifyGroupStatus(int group_id, bool lock, CsisGroupLockStatus status,
                         CsisLockCb cb) {
    callbacks_->OnGroupLockChanged(group_id, lock, status);
    if (cb) std::move(cb).Run(group_id, lock, status);
  }

  std::vector<RawAddress> GetDeviceList(int group_id) override {
    std::vector<RawAddress> result;
    auto csis_group = FindCsisGroup(group_id);

    if (!csis_group || csis_group->IsEmpty()) return result;

    auto csis_device = csis_group->GetFirstDevice();
    while (csis_device) {
      result.push_back(csis_device->addr);
      csis_device = csis_group->GetNextDevice(csis_device);
    }

    return result;
  }

  void LockGroup(int group_id, bool lock, CsisLockCb cb) override {
    if (lock) {
      log::debug("Locking group: {}", group_id);
    } else {
      log::debug("Unlocking group: {}", group_id);
    }

    /* For now we try to lock only connected devices in the group
     * TODO: We can consider reconnected to not connected devices and then
     * locked them
     */
    auto csis_group = FindCsisGroup(group_id);
    if (csis_group == nullptr) {
      log::error("Group not found: {}", group_id);
      NotifyGroupStatus(group_id, false,
                        CsisGroupLockStatus::FAILED_INVALID_GROUP,
                        std::move(cb));
      return;
    }

    if (csis_group->IsEmpty()) {
      NotifyGroupStatus(group_id, false,
                        CsisGroupLockStatus::FAILED_GROUP_EMPTY, std::move(cb));
      return;
    }

    if (csis_group->GetTargetLockState() != CsisLockState::CSIS_STATE_UNSET) {
      /* CSIS operation ongoing */

      log::debug(
          "Lock operation ongoing: group id: {}, target state {}", group_id,
          (csis_group->GetTargetLockState() == CsisLockState::CSIS_STATE_LOCKED
               ? "lock"
               : "unlock"));
      return;
    }

    CsisLockState new_lock_state = lock ? CsisLockState::CSIS_STATE_LOCKED
                                        : CsisLockState::CSIS_STATE_UNLOCKED;

    if (csis_group->GetCurrentLockState() == new_lock_state) {
      log::debug("Nothing to do as requested lock is there");
      NotifyGroupStatus(group_id, lock, CsisGroupLockStatus::SUCCESS,
                        std::move(cb));
      return;
    }

#if CSIP_UPPER_TESTER_FORCE_TO_SEND_LOCK == FALSE
    if (lock && !csis_group->IsAvailableForCsisLockOperation()) {
      log::debug("Group {} locked by other", group_id);
      NotifyGroupStatus(group_id, false,
                        CsisGroupLockStatus::FAILED_LOCKED_BY_OTHER,
                        std::move(cb));
      return;
    }
#endif

    csis_group->SetTargetLockState(new_lock_state, std::move(cb));

    if (lock) {
      /* In locking case we need to make sure we lock all the device
       * and that in case of error on the way to lock the group, we
       * can revert lock previously locked devices as per specification.
       */
      auto csis_device = csis_group->GetFirstDevice();
      while (!csis_device->IsConnected()) {
        csis_device = csis_group->GetNextDevice(csis_device);
      }

      auto csis_instance = csis_device->GetCsisInstanceByGroupId(group_id);
      LOG_ASSERT(csis_instance) << " csis_instance does not exist!";
      SetLock(csis_device, csis_instance, new_lock_state);
    } else {
      /* For unlocking, we don't have to monitor status of unlocking device,
       * therefore, we can just send unlock to all of them, in oposite rank
       * order and check if we get new state notification.
       */
      auto csis_device = csis_group->GetLastDevice();
      auto csis_instance = csis_device->GetCsisInstanceByGroupId(group_id);
      LOG_ASSERT(csis_instance) << " csis_instance does not exist!";
      while (csis_device) {
        if ((csis_device->IsConnected()) &&
            ((csis_instance->GetLockState() != new_lock_state))) {
          csis_group->UpdateLockTransitionCnt(1);
          SetLock(csis_device, csis_instance, new_lock_state);
        }
        csis_device = csis_group->GetPrevDevice(csis_device);
      }
    }
  }

  int GetDesiredSize(int group_id) override {
    auto csis_group = FindCsisGroup(group_id);
    if (!csis_group) {
      log::info("Unknown group {}", group_id);
      return -1;
    }

    return csis_group->GetDesiredSize();
  }

  bool SerializeSets(const RawAddress& addr, std::vector<uint8_t>& out) const {
    auto device = FindDeviceByAddress(addr);
    if (device == nullptr) {
      log::warn("Skipping unknown device addr= {}",
                ADDRESS_TO_LOGGABLE_CSTR(addr));
      return false;
    }

    if (device->GetNumberOfCsisInstances() == 0) {
      log::warn("No CSIS instances for addr= {}",
                ADDRESS_TO_LOGGABLE_CSTR(addr));
      return false;
    }

    log::debug(": device= {}", ADDRESS_TO_LOGGABLE_CSTR(device->addr));

    auto num_sets = device->GetNumberOfCsisInstances();
    if ((num_sets == 0) || (num_sets > std::numeric_limits<uint8_t>::max()))
      return false;

    out.resize(CSIS_STORAGE_HEADER_SZ + (num_sets * CSIS_STORAGE_ENTRY_SZ));
    auto* ptr = out.data();

    /* header */
    UINT8_TO_STREAM(ptr, CSIS_STORAGE_CURRENT_LAYOUT_MAGIC);
    UINT8_TO_STREAM(ptr, num_sets);

    /* set entries */
    device->ForEachCsisInstance(
        [&](const std::shared_ptr<CsisInstance>& csis_inst) {
          auto gid = csis_inst->GetGroupId();
          auto csis_group = FindCsisGroup(gid);
          if (csis_group == nullptr) {
            log::error("SerializeSets: No matching group found!");
            return;
          }

          UINT8_TO_STREAM(ptr, gid);
          UINT8_TO_STREAM(ptr, csis_group->GetDesiredSize());
          UINT8_TO_STREAM(ptr, csis_inst->GetRank());
          Octet16 sirk = csis_group->GetSirk();
          memcpy(ptr, sirk.data(), sirk.size());
          ptr += sirk.size();
        });

    return true;
  }

  std::map<uint8_t, uint8_t> DeserializeSets(const RawAddress& addr,
                                             const std::vector<uint8_t>& in) {
    std::map<uint8_t, uint8_t> group_rank_map;

    if (in.size() < CSIS_STORAGE_HEADER_SZ + CSIS_STORAGE_ENTRY_SZ)
      return group_rank_map;
    auto* ptr = in.data();

    uint8_t magic;
    STREAM_TO_UINT8(magic, ptr);

    if (magic == CSIS_STORAGE_CURRENT_LAYOUT_MAGIC) {
      uint8_t num_sets;
      STREAM_TO_UINT8(num_sets, ptr);

      if (in.size() <
          CSIS_STORAGE_HEADER_SZ + (num_sets * CSIS_STORAGE_ENTRY_SZ)) {
        log::error("Invalid persistent storage data");
        return group_rank_map;
      }

      /* sets entries */
      while (num_sets--) {
        uint8_t gid;
        Octet16 sirk;
        uint8_t size;
        uint8_t rank;

        STREAM_TO_UINT8(gid, ptr);
        STREAM_TO_UINT8(size, ptr);
        STREAM_TO_UINT8(rank, ptr);
        STREAM_TO_ARRAY(sirk.data(), ptr, (int)sirk.size());

        // Set grouping and SIRK
        auto csis_group = AssignCsisGroup(addr, gid, true, Uuid::kEmpty);
        if (csis_group == nullptr) {
          continue;
        }

        csis_group->SetDesiredSize(size);
        csis_group->SetSirk(sirk);

        // TODO: Save it for later, so we won't have to read it using GATT
        group_rank_map[gid] = rank;
      }
    }

    return group_rank_map;
  }

  void StartOpportunisticConnect(const RawAddress& address) {
    /* Oportunistic works only for direct connect,
     * but in fact this is background connect
     */
    log::info(": {}", ADDRESS_TO_LOGGABLE_CSTR(address));
    BTA_GATTC_Open(gatt_if_, address, BTM_BLE_DIRECT_CONNECTION, true);
  }

  void AddFromStorage(const RawAddress& addr, const std::vector<uint8_t>& in) {
    auto group_rank_map = DeserializeSets(addr, in);

    log::debug("{}, number of groups {}", ADDRESS_TO_LOGGABLE_CSTR(addr),
               static_cast<int>(csis_groups_.size()));

    auto device = FindDeviceByAddress(addr);
    if (device == nullptr) {
      device = std::make_shared<CsisDevice>(addr, false);
      devices_.push_back(device);
    }

    for (const auto& csis_group : csis_groups_) {
      if (!csis_group->IsDeviceInTheGroup(device)) continue;

      if (csis_group->GetUuid() != Uuid::kEmpty) {
        auto group_id = csis_group->GetGroupId();
        uint8_t rank = bluetooth::csis::CSIS_RANK_INVALID;
        if (group_rank_map.count(group_id) != 0) {
          rank = group_rank_map.at(group_id);
        }

        callbacks_->OnDeviceAvailable(device->addr, group_id,
                                      csis_group->GetDesiredSize(), rank,
                                      csis_group->GetUuid());
      }
    }

    /* For bonded devices, CSIP can be always opportunistic service */
    StartOpportunisticConnect(addr);
  }

  void CleanUp() {
    LOG_DEBUG();

    BTA_GATTC_AppDeregister(gatt_if_);
    for (auto& device : devices_) {
      if (device->IsConnected()) BTA_GATTC_Close(device->conn_id);
      DoDisconnectCleanUp(device);
    }

    devices_.clear();
    csis_groups_.clear();

    CsisObserverSetBackground(false);
    dev_groups_->CleanUp(device_group_callbacks);
  }

  void Dump(int fd) {
    std::stringstream stream;

    stream << "  APP ID: " << +gatt_if_ << "\n"
           << "  Groups:\n";
    for (const auto& g : csis_groups_) {
      stream << "    == id: " << g->GetGroupId() << " ==\n"
             << "    uuid: " << g->GetUuid() << "\n"
             << "    desired size: " << g->GetDesiredSize() << "\n"
             << "    discoverable state: "
             << static_cast<int>(g->GetDiscoveryState()) << "\n"
             << "    current lock state: "
             << static_cast<int>(g->GetCurrentLockState()) << "\n"
             << "    target lock state: "
             << static_cast<int>(g->GetTargetLockState()) << "\n"
             << "    devices: \n";
      for (auto& device : devices_) {
        if (!g->IsDeviceInTheGroup(device)) {
          if (device->GetExpectedGroupIdMember() == g->GetGroupId()) {
            stream << "        == candidate addr: "
                   << ADDRESS_TO_LOGGABLE_STR(device->addr) << "\n";
          }
          continue;
        }

        stream << "        == addr: " << ADDRESS_TO_LOGGABLE_STR(device->addr)
               << " ==\n"
               << "        csis instance: data:"
               << "\n";

        auto instance = device->GetCsisInstanceByGroupId(g->GetGroupId());
        if (!instance) {
          stream << "          No csis instance available\n";
        } else {
          stream << "          service handle: "
                 << loghex(instance->svc_data.start_handle)
                 << "          rank: " << +instance->GetRank() << "\n";
        }

        if (!device->IsConnected()) {
          stream << "        Not connected\n";
        } else {
          stream << "        Connected conn_id = "
                 << std::to_string(device->conn_id) << "\n";
        }
      }
    }

    dprintf(fd, "%s", stream.str().c_str());
  }

 private:
  std::shared_ptr<CsisDevice> FindDeviceByConnId(uint16_t conn_id) {
    auto it = find_if(devices_.begin(), devices_.end(),
                      CsisDevice::MatchConnId(conn_id));
    if (it != devices_.end()) return (*it);

    return nullptr;
  }

  void RemoveCsisDevice(std::shared_ptr<CsisDevice>& device, int group_id) {
    auto it = find_if(devices_.begin(), devices_.end(),
                      CsisDevice::MatchAddress(device->addr));
    if (it == devices_.end()) return;

    if (group_id != bluetooth::groups::kGroupUnknown) {
      auto csis_group = FindCsisGroup(group_id);
      if (!csis_group) {
        /* This could happen when remove device is called when bonding is
         * removed */
        log::debug("group not found {}", group_id);
        return;
      }

      csis_group->RemoveDevice(device->addr);

      if (csis_group->IsEmpty()) {
        RemoveCsisGroup(group_id);

        /* Remove cached candidate devices for group */
        devices_.erase(
            std::remove_if(devices_.begin(), devices_.end(),
                           [group_id](auto& dev) {
                             if (dev->GetNumberOfCsisInstances() == 0 &&
                                 dev->GetExpectedGroupIdMember() == group_id &&
                                 dev->GetPairingSirkReadFlag() == false) {
                               return true;
                             }

                             return false;
                           }),
            devices_.end());
      }

      device->RemoveCsisInstance(group_id);
    }

    if (device->GetNumberOfCsisInstances() == 0) {
      devices_.erase(it);
    }
  }

  std::shared_ptr<CsisDevice> FindDeviceByAddress(
      const RawAddress& addr) const {
    auto it = find_if(devices_.cbegin(), devices_.cend(),
                      CsisDevice::MatchAddress(addr));
    if (it != devices_.end()) return (*it);

    return nullptr;
  }

  std::shared_ptr<CsisGroup> FindCsisGroup(int group_id) const {
    auto it =
        find_if(csis_groups_.cbegin(), csis_groups_.cend(),
                [group_id](auto& g) { return (group_id == g->GetGroupId()); });

    if (it == csis_groups_.end()) return nullptr;
    return (*it);
  }

  void RemoveCsisGroup(int group_id) {
    for (auto it = csis_groups_.begin(); it != csis_groups_.end(); it++) {
      if ((*it)->GetGroupId() == group_id) {
        csis_groups_.erase(it);
        return;
      }
    }
  }

  /* Handle encryption */
  void OnEncrypted(std::shared_ptr<CsisDevice>& device) {
    log::debug("{}", ADDRESS_TO_LOGGABLE_CSTR(device->addr));

    if (device->is_gatt_service_valid) {
      NotifyCsisDeviceValidAndStoreIfNeeded(device);
    } else {
      BTA_GATTC_ServiceSearchRequest(device->conn_id, &kCsisServiceUuid);
    }
  }

  void NotifyCsisDeviceValidAndStoreIfNeeded(
      std::shared_ptr<CsisDevice>& device) {
    /* Notify that we are ready to go. Notice that multiple callback calls
     * for a single device address can be called if device is in more than one
     * CSIS group.
     */
    bool notify_connected = false;
    int group_id_to_discover = bluetooth::groups::kGroupUnknown;
    for (const auto& csis_group : csis_groups_) {
      if (!csis_group->IsDeviceInTheGroup(device)) continue;

      int group_id = csis_group->GetGroupId();
      auto csis_instance = device->GetCsisInstanceByGroupId(group_id);
      log::debug("group id {}", group_id);

      if (!csis_instance) {
        /* This can happen when some other user added device to group in the
         * context which is not existing on the peer side. e.g. LeAudio added it
         * in the CAP context, but CSIS exist on the peer device without a
         * context. We will endup in having device in 2 groups. One in generic
         * context with valid csis_instance, and one in CAP context without csis
         * instance */
        log::info("csis_instance does not exist for group {}", group_id);
        continue;
      }

      callbacks_->OnDeviceAvailable(
          device->addr, group_id, csis_group->GetDesiredSize(),
          csis_instance->GetRank(), csis_instance->GetUuid());
      notify_connected = true;

      if (group_id_to_discover == bluetooth::groups::kGroupUnknown) {
        group_id_to_discover = group_id;
      }
    }

    if (notify_connected) {
      callbacks_->OnConnectionState(device->addr, ConnectionState::CONNECTED);

      log::debug("group_id {}", group_id_to_discover);
      if (group_id_to_discover != bluetooth::groups::kGroupUnknown) {
        /* Start active search for the other device
         * b/281120322
         */
        auto g = FindCsisGroup(group_id_to_discover);
        log::debug("Group size  {}  target size {}", g->GetDesiredSize(),
                   g->GetCurrentSize());

        auto dev_waiting_for_bonding_cnt =
            GetNumOfKnownExpectedDevicesWaitingForBonding(g->GetGroupId());
        log::debug("Group size: {}, desired size: {}, waiting for bonding: {}",
                   g->GetCurrentSize(), g->GetDesiredSize(),
                   dev_waiting_for_bonding_cnt);

        if (g->GetDesiredSize() >
            g->GetCurrentSize() + dev_waiting_for_bonding_cnt) {
          CsisActiveDiscovery(g);
        }
      }
    }
  }

  void OnGattWriteCcc(uint16_t conn_id, tGATT_STATUS status, uint16_t handle,
                      void* user_data) {
    auto device = FindDeviceByConnId(conn_id);
    if (device == nullptr) {
      log::info("unknown conn_id= 0x{:04x}", conn_id);
      BtaGattQueue::Clean(conn_id);
      return;
    }

    if (status == GATT_DATABASE_OUT_OF_SYNC) {
      log::info("Database out of sync for {}",
                ADDRESS_TO_LOGGABLE_CSTR(device->addr));
      ClearDeviceInformationAndStartSearch(device);
      return;
    }

    if (status == GATT_SUCCESS) {
      log::info("Successfully registered on ccc: 0x{:04x}, device: {}", handle,
                ADDRESS_TO_LOGGABLE_CSTR(device->addr));
      return;
    }

    log::error(
        "Failed to register for indications: 0x{:04x}, device: {}, status: "
        "0x{:02x}",
        handle, ADDRESS_TO_LOGGABLE_CSTR(device->addr), status);

    auto val_handle = device->FindValueHandleByCccHandle(handle);
    if (!val_handle) {
      log::error("Unknown ccc handle: 0x{:04x}, device: {}", handle,
                 ADDRESS_TO_LOGGABLE_CSTR(device->addr));
      return;
    }

    if (val_handle != GAP_INVALID_HANDLE) {
      BTA_GATTC_DeregisterForNotifications(gatt_if_, device->addr, val_handle);
    }
  }

  void OnCsisNotification(uint16_t conn_id, uint16_t handle, uint16_t len,
                          const uint8_t* value) {
    auto device = FindDeviceByConnId(conn_id);
    if (device == nullptr) {
      log::warn("Skipping unknown device, conn_id= 0x{:04x}", conn_id);
      return;
    }

    auto csis_instance = device->GetCsisInstanceByOwningHandle(handle);
    if (csis_instance == nullptr) {
      log::error("unknown notification handle: 0x{:04x} for conn_id: 0x{:04x}",
                 handle, conn_id);
      return;
    }

    if (handle == csis_instance->svc_data.sirk_handle.val_hdl) {
      OnCsisSirkValueUpdate(conn_id, GATT_SUCCESS, handle, len, value);
    } else if (handle == csis_instance->svc_data.lock_handle.val_hdl) {
      OnCsisLockNotifications(device, csis_instance, len, value);
    } else if (handle == csis_instance->svc_data.size_handle.val_hdl) {
      OnCsisSizeValueUpdate(conn_id, GATT_SUCCESS, handle, len, value);
    } else {
      log::warn("unknown notification handle 0x{:04x} for conn_id= 0x{:04x}",
                handle, conn_id);
    }
  }

  static CsisGroupLockStatus LockError2GroupLockStatus(tGATT_STATUS status) {
    switch (status) {
      case bluetooth::csis::kCsisErrorCodeLockDenied:
        return CsisGroupLockStatus::FAILED_LOCKED_BY_OTHER;
      case bluetooth::csis::kCsisErrorCodeReleaseNotAllowed:
        return CsisGroupLockStatus::FAILED_LOCKED_BY_OTHER;
      case bluetooth::csis::kCsisErrorCodeInvalidValue:
        return CsisGroupLockStatus::FAILED_OTHER_REASON;
      default:
        return CsisGroupLockStatus::FAILED_OTHER_REASON;
    }
  }

  void CsisLockCompleted(std::shared_ptr<CsisGroup>& csis_group, bool lock,
                         CsisGroupLockStatus status) {
    log::debug("group id: {}, target state {}", csis_group->GetGroupId(),
               (lock ? "lock" : "unlock"));

    NotifyGroupStatus(csis_group->GetGroupId(), lock, status,
                      std::move(csis_group->GetLockCb()));
    csis_group->SetTargetLockState(CsisLockState::CSIS_STATE_UNSET);
  }

  void OnCsisLockNotifications(std::shared_ptr<CsisDevice>& device,
                               std::shared_ptr<CsisInstance>& csis_instance,
                               uint16_t len, const uint8_t* value) {
    if (len != 1) {
      log::error("invalid notification len: {}", len);
      return;
    }

    CsisLockState new_lock = (CsisLockState)(value[0]);

    log::debug("New lock state: {},  device rank:  {}",
               static_cast<int>(new_lock), csis_instance->GetRank());

    csis_instance->SetLockState(new_lock);

    auto csis_group = FindCsisGroup(csis_instance->GetGroupId());
    if (!csis_group) return;

    CsisLockCb cb = csis_group->GetLockCb();
    if (csis_group->GetTargetLockState() == CsisLockState::CSIS_STATE_UNSET) {
      if (csis_group->GetCurrentLockState() ==
              CsisLockState::CSIS_STATE_LOCKED &&
          new_lock == CsisLockState::CSIS_STATE_UNLOCKED) {
        /* We are here when members fires theirs lock timeout.
         * Not sure what to do with our current lock state. For now we will
         * change local lock state after first set member removes its lock. Then
         * we count that others will do the same
         */
        csis_group->SetCurrentLockState(CsisLockState::CSIS_STATE_UNLOCKED);
        NotifyGroupStatus(csis_group->GetGroupId(), false,
                          CsisGroupLockStatus::SUCCESS, std::move(cb));
      }
      return;
    }

    if (csis_group->GetCurrentLockState() != csis_group->GetTargetLockState()) {
      /* We are in process of changing lock state. If new device lock
       * state is what is targeted that means all is good, we don't need
       * to do here nothing, as state will be changed once all the
       * characteristics are written. If new device state is not what is
       * targeted, that means, device changed stated unexpectedly and locking
       * procedure is broken
       */
      if (new_lock != csis_group->GetTargetLockState()) {
        /* Device changed back the lock state from what we expected, skip
         * locking and notify user about that
         */
        CsisLockCompleted(csis_group, false,
                          CsisGroupLockStatus::FAILED_OTHER_REASON);
      }
    }
  }

  void OnCsisSizeValueUpdate(uint16_t conn_id, tGATT_STATUS status,
                             uint16_t handle, uint16_t len,
                             const uint8_t* value,
                             bool notify_valid_services = false) {
    auto device = FindDeviceByConnId(conn_id);

    if (device == nullptr) {
      log::warn("Skipping unknown device, conn_id=0x{:04x}", conn_id);
      return;
    }

    log::debug("{}, status: 0x{:02x}", ADDRESS_TO_LOGGABLE_CSTR(device->addr),
               status);

    if (status != GATT_SUCCESS) {
      if (status == GATT_DATABASE_OUT_OF_SYNC) {
        log::info("Database out of sync for {}",
                  ADDRESS_TO_LOGGABLE_CSTR(device->addr));
        ClearDeviceInformationAndStartSearch(device);
      } else {
        log::error("Could not read characteristic at handle=0x{:04x}", handle);
        BTA_GATTC_Close(device->conn_id);
      }
      return;
    }

    if (len != 1) {
      log::error("Invalid size value length={} at handle= 0x{:04x}", len,
                 handle);
      BTA_GATTC_Close(device->conn_id);
      return;
    }

    auto csis_instance = device->GetCsisInstanceByOwningHandle(handle);
    if (csis_instance == nullptr) {
      log::error("Unknown csis instance");
      BTA_GATTC_Close(device->conn_id);
      return;
    }
    auto csis_group = FindCsisGroup(csis_instance->GetGroupId());
    if (!csis_group) {
      log::error("Unknown group id yet");
      return;
    }

    auto new_size = value[0];
    csis_group->SetDesiredSize(new_size);

    if (notify_valid_services) NotifyCsisDeviceValidAndStoreIfNeeded(device);
  }

  void OnCsisLockReadRsp(uint16_t conn_id, tGATT_STATUS status, uint16_t handle,
                         uint16_t len, const uint8_t* value,
                         bool notify_valid_services = false) {
    auto device = FindDeviceByConnId(conn_id);
    if (device == nullptr) {
      log::warn("Skipping unknown device, conn_id=0x{:04x}", conn_id);
      return;
    }

    log::info("{}, status 0x{:02x}", ADDRESS_TO_LOGGABLE_CSTR(device->addr),
              status);

    if (status != GATT_SUCCESS) {
      if (status == GATT_DATABASE_OUT_OF_SYNC) {
        log::info("Database out of sync for {}",
                  ADDRESS_TO_LOGGABLE_CSTR(device->addr));
        ClearDeviceInformationAndStartSearch(device);
      } else {
        log::error("Could not read characteristic at handle=0x{:04x}", handle);
        BTA_GATTC_Close(device->conn_id);
      }
      return;
    }

    if (len != 1) {
      log::error("Invalid lock value length={}, at handle=0x{:04x}", len,
                 handle);
      BTA_GATTC_Close(device->conn_id);
      return;
    }

    auto csis_instance = device->GetCsisInstanceByOwningHandle(handle);
    if (csis_instance == nullptr) {
      log::error("Unknown csis instance");
      BTA_GATTC_Close(device->conn_id);
      return;
    }
    csis_instance->SetLockState((CsisLockState)(value[0]));

    if (notify_valid_services) NotifyCsisDeviceValidAndStoreIfNeeded(device);
  }

  void OnCsisRankReadRsp(uint16_t conn_id, tGATT_STATUS status, uint16_t handle,
                         uint16_t len, const uint8_t* value,
                         bool notify_valid_services) {
    auto device = FindDeviceByConnId(conn_id);
    if (device == nullptr) {
      log::warn("Skipping unknown device, conn_id= 0x{:04x}", conn_id);
      return;
    }

    log::debug("{}, status: 0x{:02x}, rank: {}",
               ADDRESS_TO_LOGGABLE_CSTR(device->addr), status, value[0]);
    if (status != GATT_SUCCESS) {
      if (status == GATT_DATABASE_OUT_OF_SYNC) {
        log::info("Database out of sync for {}",
                  ADDRESS_TO_LOGGABLE_CSTR(device->addr));
        ClearDeviceInformationAndStartSearch(device);
      } else {
        log::error("Could not read characteristic at handle=0x{:04x}", handle);
        BTA_GATTC_Close(device->conn_id);
      }
      return;
    }

    if (len != 1) {
      log::error("Invalid rank value length= {},  at handle= 0x{:04x}", len,
                 handle);
      BTA_GATTC_Close(device->conn_id);
      return;
    }

    auto csis_instance = device->GetCsisInstanceByOwningHandle(handle);
    if (csis_instance == nullptr) {
      log::error("Unknown csis instance handle 0x{:04x}", handle);
      BTA_GATTC_Close(device->conn_id);
      return;
    }

    csis_instance->SetRank((value[0]));
    auto csis_group = FindCsisGroup(csis_instance->GetGroupId());
    if (!csis_group) {
      log::error("Unknown group id yet");
      return;
    }

    csis_group->SortByCsisRank();

    if (notify_valid_services) NotifyCsisDeviceValidAndStoreIfNeeded(device);
  }

  void OnCsisObserveCompleted(void) {
    log::info("Group_id: {}", discovering_group_);

    if (discovering_group_ == bluetooth::groups::kGroupUnknown) {
      log::error("No ongoing CSIS discovery - disable scan");
      return;
    }

    auto csis_group = FindCsisGroup(discovering_group_);
    discovering_group_ = bluetooth::groups::kGroupUnknown;

    if (!csis_group) {
      log::warn("Group_id {} is not existing", discovering_group_);
      discovering_group_ = bluetooth::groups::kGroupUnknown;
      return;
    }

    discovering_group_ = bluetooth::groups::kGroupUnknown;
    if (csis_group->IsGroupComplete()) {
      csis_group->SetDiscoveryState(
          CsisDiscoveryState::CSIS_DISCOVERY_COMPLETED);
    } else {
      csis_group->SetDiscoveryState(CsisDiscoveryState::CSIS_DISCOVERY_IDLE);
    }
  }

  /*
   * Sirk shall be in LE order
   * encrypted_sirk: LE order
   */
  bool sdf(const RawAddress& address, const Octet16& encrypted_sirk,
           Octet16& sirk) {
    auto pltk = BTM_BleGetPeerLTK(address);
    if (!pltk.has_value()) {
      log::error("No security for {}", ADDRESS_TO_LOGGABLE_CSTR(address));
      return false;
    }

#ifdef CSIS_DEBUG
    auto irk = BTM_BleGetPeerIRK(address);
    log::info("LTK {}", (base::HexEncode(*pltk.data(), 16)));
    log::info("IRK {}", base::HexEncode(*irk.data(), 16));
#endif

    /* Calculate salt CSIS d1.0r05 4.3 */
    Octet16 zero_key;
    memset(zero_key.data(), 0, 16);

    std::string msg1 = "SIRKenc";
    std::reverse(msg1.begin(), msg1.end());

    Octet16 s1 = crypto_toolbox::aes_cmac(zero_key, (uint8_t*)(msg1.c_str()),
                                          msg1.size());

#ifdef CSIS_DEBUG
    log::info("s1 (le) {}", base::HexEncode(s1.data(), 16));
    /* Create K = LTK */
    log::info("K (le) {}", base::HexEncode(*pltk.data(), 16));
#endif

    Octet16 T = crypto_toolbox::aes_cmac(s1, *pltk);

#ifdef CSIS_DEBUG
    log::info("T (le) {}", base::HexEncode(T.data(), 16));
#endif

    std::string msg2 = "csis";
    std::reverse(msg2.begin(), msg2.end());

    Octet16 k1 =
        crypto_toolbox::aes_cmac(T, (uint8_t*)(msg2.c_str()), msg2.size());
#ifdef CSIS_DEBUG
    log::info("K1 (le) {}", base::HexEncode(k1.data(), 16));
#endif

    for (int i = 0; i < 16; i++) sirk[i] = encrypted_sirk[i] ^ k1[i];

#ifdef CSIS_DEBUG
    log::info("SIRK (le){}", base::HexEncode(sirk.data(), 16));
#endif

    return true;
  }

  std::vector<RawAddress> GetAllRsiFromAdvertising(
      const tBTA_DM_INQ_RES* result) {
    const uint8_t* p_service_data = result->p_eir;
    std::vector<RawAddress> devices;
    uint8_t service_data_len = 0;

    while ((p_service_data = AdvertiseDataParser::GetFieldByType(
                p_service_data + service_data_len,
                result->eir_len - (p_service_data - result->p_eir) -
                    service_data_len,
                BTM_BLE_AD_TYPE_RSI, &service_data_len))) {
      RawAddress bda;
      const uint8_t* p_bda = p_service_data;
      if (service_data_len < RawAddress::kLength) {
        continue;
      }

      STREAM_TO_BDADDR(bda, p_bda);
      devices.push_back(std::move(bda));
    }

    return std::move(devices);
  }

  int GetNumOfKnownExpectedDevicesWaitingForBonding(int group_id) {
    return std::count_if(
        devices_.begin(), devices_.end(), [group_id](const auto& device) {
          return device->GetExpectedGroupIdMember() == group_id &&
                 !device->GetCsisInstanceByGroupId(group_id);
        });
  }

  void CacheAndAdvertiseExpectedMember(const RawAddress& address,
                                       int group_id) {
    auto device = FindDeviceByAddress(address);
    if (device == nullptr) {
      device = std::make_shared<CsisDevice>(address, false);
      devices_.push_back(device);
    }

    /*
     * Expected group ID will be checked while reading SIRK if this device
     * truly is member of group.
     */
    device.get()->SetExpectedGroupIdMember(group_id);
    callbacks_->OnSetMemberAvailable(address,
                                     device.get()->GetExpectedGroupIdMember());
  }

  void OnActiveScanResult(const tBTA_DM_INQ_RES* result) {
    auto csis_device = FindDeviceByAddress(result->bd_addr);
    if (csis_device) {
      log::debug("Drop same device .. {}",
                 ADDRESS_TO_LOGGABLE_CSTR(result->bd_addr));
      return;
    }

    /* Make sure device is not already bonded which could
     * be a case for dual mode devices where
     */
    if (BTM_BleIsLinkKeyKnown(result->bd_addr)) {
      log::verbose("Device {} already bonded. Identity address: {}",
                   ADDRESS_TO_LOGGABLE_CSTR(result->bd_addr),
                   ADDRESS_TO_LOGGABLE_CSTR(
                       *BTM_BleGetIdentityAddress(result->bd_addr)));
      return;
    }

    auto all_rsi = GetAllRsiFromAdvertising(result);
    if (all_rsi.empty()) return;

    /* Notify only the actively searched group */
    auto csis_group = FindCsisGroup(discovering_group_);
    if (csis_group == nullptr) {
      log::error("No ongoing CSIS discovery - disable scan");
      CsisActiveObserverSet(false);
      return;
    }

    if (csis_group->GetDesiredSize() > 0 &&
        (csis_group->GetDesiredSize() == csis_group->GetCurrentSize())) {
      log::warn("Group is already complete");
      return;
    }

    auto discovered_group_rsi = std::find_if(
        all_rsi.cbegin(), all_rsi.cend(), [&csis_group](const auto& rsi) {
          return csis_group->IsRsiMatching(rsi);
        });
    if (discovered_group_rsi != all_rsi.cend()) {
      log::debug("Found set member {}",
                 ADDRESS_TO_LOGGABLE_CSTR(result->bd_addr));

      CacheAndAdvertiseExpectedMember(result->bd_addr,
                                      csis_group->GetGroupId());

      /* Switch back to the opportunistic observer mode.
       * When second device will pair, csis will restart active scan
       * to search more members if needed */
      CsisActiveObserverSet(false);
      csis_group->SetDiscoveryState(CsisDiscoveryState::CSIS_DISCOVERY_IDLE);
    }
  }

  static void csis_ad_type_filter_set(bool enable) {
    bool is_ad_type_filter_supported =
        bluetooth::shim::is_ad_type_filter_supported();

    log::info("enable: {}, is_ad_type_filter_supported: {}", enable,
              is_ad_type_filter_supported);

    if (is_ad_type_filter_supported) {
      bluetooth::shim::set_ad_type_rsi_filter(enable);
    } else {
      bluetooth::shim::set_empty_filter(enable);
    }
  }

  void CsisActiveObserverSet(bool enable) {
    log::info("Group_id {}: enable: {}", discovering_group_, enable);
    csis_ad_type_filter_set(enable);

    BTA_DmBleCsisObserve(enable,
                         [](tBTA_DM_SEARCH_EVT event, tBTA_DM_SEARCH* p_data) {
                           /* If there's no instance we are most likely shutting
                            * down the whole stack and we can ignore this event.
                            */
                           if (instance == nullptr) return;

                           if (event == BTA_DM_INQ_CMPL_EVT) {
                             power_telemetry::GetInstance().LogBleScan(
                                 static_cast<int>(p_data->inq_cmpl.num_resps));
                             log::info("BLE observe complete. Num Resp: {}",
                                       p_data->inq_cmpl.num_resps);
                             csis_ad_type_filter_set(false);
                             instance->OnCsisObserveCompleted();
                             instance->CsisObserverSetBackground(true);
                             return;
                           }

                           if (event != BTA_DM_INQ_RES_EVT) {
                             log::warn("Unknown event: 0x{:02x}", event);
                             return;
                           }

                           instance->OnActiveScanResult(&p_data->inq_res);
                         });
    BTA_DmBleScan(enable, bluetooth::csis::kDefaultScanDurationS, true);

    /* Need to call it by ourselfs */
    if (!enable) {
      OnCsisObserveCompleted();
      CsisObserverSetBackground(true);
    }
  }

  void CheckForGroupInInqDb(const std::shared_ptr<CsisGroup>& csis_group) {
    // Check if last inquiry already found devices with RSI matching this group
    for (tBTM_INQ_INFO* inq_ent = BTM_InqDbFirst(); inq_ent != nullptr;
         inq_ent = BTM_InqDbNext(inq_ent)) {
      RawAddress rsi = inq_ent->results.ble_ad_rsi;
      if (!csis_group->IsRsiMatching(rsi)) continue;

      RawAddress address = inq_ent->results.remote_bd_addr;
      auto device = FindDeviceByAddress(address);
      if (device && csis_group->IsDeviceInTheGroup(device)) {
        // InqDb will also contain existing devices, already in group - skip
        // them
        continue;
      }

      log::info("Device {} from inquiry cache match to group id {}",
                ADDRESS_TO_LOGGABLE_CSTR(address), csis_group->GetGroupId());
      callbacks_->OnSetMemberAvailable(address, csis_group->GetGroupId());
      break;
    }
  }

  void CsisActiveDiscovery(std::shared_ptr<CsisGroup> csis_group) {
    if (bluetooth::common::InitFlags::UseRsiFromCachedInquiryResults()) {
      CheckForGroupInInqDb(csis_group);
    }

    if ((csis_group->GetDiscoveryState() !=
         CsisDiscoveryState::CSIS_DISCOVERY_IDLE)) {
      log::error("Incorrect ase group: {}, state 0x{:02x}",
                 csis_group->GetGroupId(),
                 static_cast<int>(csis_group->GetDiscoveryState()));
      return;
    }

    csis_group->SetDiscoveryState(CsisDiscoveryState::CSIS_DISCOVERY_ONGOING);
    /* TODO Maybe we don't need it */
    discovering_group_ = csis_group->GetGroupId();
    CsisActiveObserverSet(true);
  }

  void OnScanBackgroundResult(const tBTA_DM_INQ_RES* result) {
    if (csis_groups_.empty()) return;

    auto csis_device = FindDeviceByAddress(result->bd_addr);
    if (csis_device) {
      log::debug("Drop known device {}",
                 ADDRESS_TO_LOGGABLE_CSTR(result->bd_addr));
      return;
    }

    /* Make sure device is not already bonded which could
     * be a case for dual mode devices where
     */
    if (BTM_BleIsLinkKeyKnown(result->bd_addr)) {
      log::verbose("Device {} already bonded. Identity address: {}",
                   ADDRESS_TO_LOGGABLE_CSTR(result->bd_addr),
                   ADDRESS_TO_LOGGABLE_CSTR(
                       *BTM_BleGetIdentityAddress(result->bd_addr)));
      return;
    }

    auto all_rsi = GetAllRsiFromAdvertising(result);
    if (all_rsi.empty()) return;

    /* Notify all the groups this device belongs to. */
    for (auto& group : csis_groups_) {
      for (auto& rsi : all_rsi) {
        if (group->IsRsiMatching(rsi)) {
          log::info("Device {} match to group id {}",
                    ADDRESS_TO_LOGGABLE_CSTR(result->bd_addr),
                    group->GetGroupId());
          if (group->GetDesiredSize() > 0 &&
              (group->GetCurrentSize() == group->GetDesiredSize())) {
            log::warn(
                "Group is already completed. Some other device use same SIRK");
            break;
          }

          CacheAndAdvertiseExpectedMember(result->bd_addr, group->GetGroupId());

          break;
        }
      }
    }
  }

  void CsisObserverSetBackground(bool enable) {
    log::debug("CSIS Discovery background: {}", enable);

    BTA_DmBleCsisObserve(enable,
                         [](tBTA_DM_SEARCH_EVT event, tBTA_DM_SEARCH* p_data) {
                           /* If there's no instance we are most likely shutting
                            * down the whole stack and we can ignore this event.
                            */
                           if (instance == nullptr) return;

                           if (event == BTA_DM_INQ_CMPL_EVT) {
                             power_telemetry::GetInstance().LogBleScan(
                                 static_cast<int>(p_data->inq_cmpl.num_resps));
                             log::verbose("BLE observe complete. Num Resp: {}",
                                          p_data->inq_cmpl.num_resps);
                             return;
                           }

                           if (event != BTA_DM_INQ_RES_EVT) {
                             log::warn("Unknown event: 0x{:02x}", event);
                             return;
                           }

                           instance->OnScanBackgroundResult(&p_data->inq_res);
                         });
  }

  void OnCsisSirkValueUpdate(uint16_t conn_id, tGATT_STATUS status,
                             uint16_t handle, uint16_t len,
                             const uint8_t* value,
                             bool notify_valid_services = true) {
    auto device = FindDeviceByConnId(conn_id);
    if (device == nullptr) {
      log::warn("Skipping unknown device, conn_id=0x{:04x}", conn_id);
      return;
    }

    log::debug("{}, status: 0x{:02x}", ADDRESS_TO_LOGGABLE_CSTR(device->addr),
               status);

    if (status != GATT_SUCCESS) {
      /* TODO handle error codes:
       * kCsisErrorCodeLockAccessSirkRejected
       * kCsisErrorCodeLockOobSirkOnly
       */
      if (status == GATT_DATABASE_OUT_OF_SYNC) {
        log::info("Database out of sync for {}",
                  ADDRESS_TO_LOGGABLE_CSTR(device->addr));
        ClearDeviceInformationAndStartSearch(device);
      } else {
        log::error("Could not read characteristic at handle=0x{:04x}", handle);
        BTA_GATTC_Close(device->conn_id);
      }
      return;
    }

    if (len != bluetooth::csis::kCsisSirkCharLen) {
      log::error("Invalid sirk value length= {} at handle= 0x{:04x}", len,
                 handle);
      BTA_GATTC_Close(device->conn_id);
      return;
    }

    auto csis_instance = device->GetCsisInstanceByOwningHandle(handle);
    if (csis_instance == nullptr) {
      log::error("Unknown csis instance: handle 0x{:04x}", handle);
      BTA_GATTC_Close(device->conn_id);
      return;
    }

    uint8_t sirk_type = value[0];
    log::info("SIRK Type: 0x{:02x}", sirk_type);

    /* Verify if sirk is not all zeros */
    Octet16 zero{};
    if (memcmp(zero.data(), value + 1, 16) == 0) {
      log::error("Received invalid zero SIRK conn_id: 0x{:02x}. Disconnecting",
                 device->conn_id);
      BTA_GATTC_Close(device->conn_id);
      return;
    }

    Octet16 received_sirk;
    memcpy(received_sirk.data(), value + 1, 16);

    if (sirk_type == bluetooth::csis::kCsisSirkTypeEncrypted) {
      /* Decrypt encrypted SIRK */
      Octet16 sirk;
      sdf(device->addr, received_sirk, sirk);
      received_sirk = sirk;
    }

    /* SIRK is ready. Add device to the group */

    std::shared_ptr<CsisGroup> csis_group;
    int group_id = csis_instance->GetGroupId();
    if (group_id != bluetooth::groups::kGroupUnknown) {
      /* Group already exist. */
      csis_group = FindCsisGroup(group_id);
      LOG_ASSERT(csis_group) << " group does not exist? " << group_id;
    } else {
      /* Now having SIRK we can decide if the device belongs to some group we
       * know or this is a new group
       */
      for (auto& g : csis_groups_) {
        if (g->IsSirkBelongsToGroup(received_sirk)) {
          group_id = g->GetGroupId();
          break;
        }
      }

      if (group_id == bluetooth::groups::kGroupUnknown) {
        /* Here it means, we have new group. Let's us create it */
        group_id =
            dev_groups_->AddDevice(device->addr, csis_instance->GetUuid());
        LOG_ASSERT(group_id != bluetooth::groups::kGroupUnknown);
      } else {
        dev_groups_->AddDevice(device->addr, csis_instance->GetUuid(),
                               group_id);
      }

      csis_group = FindCsisGroup(group_id);
      csis_group->AddDevice(device);
      /* Let's update csis instance group id */
      csis_instance->SetGroupId(group_id);
    }

    csis_group->SetSirk(received_sirk);
    device->is_gatt_service_valid = true;
    btif_storage_update_csis_info(device->addr);

    if (notify_valid_services) NotifyCsisDeviceValidAndStoreIfNeeded(device);

#ifdef CSIS_DEBUG
    log::info("SIRK {}, address: {}", base::HexEncode(received_sirk.data(), 16),
              ADDRESS_TO_LOGGABLE_CSTR(device->addr));
#endif

    log::verbose("Expected group size {},  actual group Size: {}",
                 csis_group->GetDesiredSize(), csis_group->GetCurrentSize());

    if (csis_group->GetDesiredSize() == csis_group->GetCurrentSize()) {
      auto iter = devices_.cbegin();

      /*
       * Remove devices which are expected members but are not connected and
       * group is already completed. Those devices are cached ivalid devices
       * kept on list to not trigger "new device" found every time advertising
       * event is received.
       */
      while (iter != devices_.cend()) {
        if (((*iter)->GetExpectedGroupIdMember() == csis_group->GetGroupId()) &&
            !(*iter)->IsConnected()) {
          iter = devices_.erase(iter);
        } else {
          ++iter;
        }
      }
    }
  }

  void DeregisterNotifications(std::shared_ptr<CsisDevice> device) {
    device->ForEachCsisInstance(
        [&](const std::shared_ptr<CsisInstance>& csis_inst) {
          DisableGattNotification(device->conn_id, device->addr,
                                  csis_inst->svc_data.lock_handle.val_hdl);
          DisableGattNotification(device->conn_id, device->addr,
                                  csis_inst->svc_data.sirk_handle.val_hdl);
          DisableGattNotification(device->conn_id, device->addr,
                                  csis_inst->svc_data.size_handle.val_hdl);
        });
  }

  void DoDisconnectCleanUp(std::shared_ptr<CsisDevice> device) {
    log::info("{}", ADDRESS_TO_LOGGABLE_CSTR(device->addr));

    DeregisterNotifications(device);

    if (device->IsConnected()) {
      BtaGattQueue::Clean(device->conn_id);
      device->conn_id = GATT_INVALID_CONN_ID;
    }
  }

  bool OnCsisServiceFound(std::shared_ptr<CsisDevice> device,
                          const gatt::Service* service,
                          const bluetooth::Uuid& context_uuid,
                          bool is_last_instance) {
    log::debug("service handle: 0x{:04x}, end handle: 0x{:04x}, uuid: {}",
               service->handle, service->end_handle, context_uuid.ToString());

    auto csis_inst = std::make_shared<CsisInstance>(
        (uint16_t)service->handle, (uint16_t)service->end_handle, context_uuid);

    /* Let's check if we know group of this device */
    int group_id = dev_groups_->GetGroupId(device->addr, context_uuid);
    if (group_id != bluetooth::groups::kGroupUnknown)
      csis_inst->SetGroupId(group_id);

    device->SetCsisInstance(csis_inst->svc_data.start_handle, csis_inst);

    /* Initially validate and store GATT service discovery data */
    for (const gatt::Characteristic& charac : service->characteristics) {
      if (charac.uuid == kCsisLockUuid) {
        /* Find the mandatory CCC descriptor */
        uint16_t ccc_handle =
            FindCccHandle(device->conn_id, charac.value_handle);
        if (ccc_handle == GAP_INVALID_HANDLE) {
          log::error("no HAS Active Preset CCC descriptor found!");
          device->RemoveCsisInstance(group_id);
          return false;
        }
        csis_inst->svc_data.lock_handle.val_hdl = charac.value_handle;
        csis_inst->svc_data.lock_handle.ccc_hdl = ccc_handle;

        SubscribeForNotifications(device->conn_id, device->addr,
                                  charac.value_handle, ccc_handle);

        log::debug(
            "Lock UUID found handle: 0x{:04x}, ccc handle: 0x{:04x}, device: "
            "{}",
            csis_inst->svc_data.lock_handle.val_hdl,
            csis_inst->svc_data.lock_handle.ccc_hdl,
            ADDRESS_TO_LOGGABLE_CSTR(device->addr));
      } else if (charac.uuid == kCsisRankUuid) {
        csis_inst->svc_data.rank_handle = charac.value_handle;

        log::debug("Rank UUID found handle: 0x{:04x}, device: {}",
                   csis_inst->svc_data.rank_handle,
                   ADDRESS_TO_LOGGABLE_CSTR(device->addr));
      } else if (charac.uuid == kCsisSirkUuid) {
        /* Find the optional CCC descriptor */
        uint16_t ccc_handle =
            FindCccHandle(device->conn_id, charac.value_handle);
        csis_inst->svc_data.sirk_handle.ccc_hdl = ccc_handle;
        csis_inst->svc_data.sirk_handle.val_hdl = charac.value_handle;

        if (ccc_handle != GAP_INVALID_HANDLE)
          SubscribeForNotifications(device->conn_id, device->addr,
                                    charac.value_handle, ccc_handle);

        log::debug(
            "SIRK UUID found handle: 0x{:04x}, ccc handle: 0x{:04x}, device: "
            "{}",
            csis_inst->svc_data.sirk_handle.val_hdl,
            csis_inst->svc_data.sirk_handle.ccc_hdl,
            ADDRESS_TO_LOGGABLE_CSTR(device->addr));
      } else if (charac.uuid == kCsisSizeUuid) {
        /* Find the optional CCC descriptor */
        uint16_t ccc_handle =
            FindCccHandle(device->conn_id, charac.value_handle);
        csis_inst->svc_data.size_handle.ccc_hdl = ccc_handle;
        csis_inst->svc_data.size_handle.val_hdl = charac.value_handle;

        if (ccc_handle != GAP_INVALID_HANDLE)
          SubscribeForNotifications(device->conn_id, device->addr,
                                    charac.value_handle, ccc_handle);

        log::debug(
            "Size UUID found handle: 0x{:04x}, ccc handle: 0x{:04x}, device: "
            "{}",
            csis_inst->svc_data.size_handle.val_hdl,
            csis_inst->svc_data.size_handle.ccc_hdl,
            ADDRESS_TO_LOGGABLE_CSTR(device->addr));
      }
    }

    /* Sirk is the only mandatory characteristic. If it is in
     * place, service is OK
     */
    if (csis_inst->svc_data.sirk_handle.val_hdl == GAP_INVALID_HANDLE) {
      /* We have some characteristics but all dependencies are not satisfied */
      log::error("Service has a broken structure.");
      device->RemoveCsisInstance(group_id);
      return false;
    }

    bool notify_after_sirk_read = false;
    bool notify_after_lock_read = false;
    bool notify_after_rank_read = false;
    bool notify_after_size_read = false;

    /* Find which read will be the last one*/
    if (is_last_instance) {
      if (csis_inst->svc_data.rank_handle != GAP_INVALID_HANDLE) {
        notify_after_rank_read = true;
      } else if (csis_inst->svc_data.size_handle.val_hdl !=
                 GAP_INVALID_HANDLE) {
        notify_after_size_read = true;
      } else if (csis_inst->svc_data.lock_handle.val_hdl !=
                 GAP_INVALID_HANDLE) {
        notify_after_lock_read = true;
      } else {
        notify_after_sirk_read = true;
      }
    }

    /* Read SIRK */
    BtaGattQueue::ReadCharacteristic(
        device->conn_id, csis_inst->svc_data.sirk_handle.val_hdl,
        [](uint16_t conn_id, tGATT_STATUS status, uint16_t handle, uint16_t len,
           uint8_t* value, void* user_data) {
          if (instance)
            instance->OnCsisSirkValueUpdate(conn_id, status, handle, len, value,
                                            (bool)user_data);
        },
        (void*)notify_after_sirk_read);

    /* Read Lock */
    if (csis_inst->svc_data.lock_handle.val_hdl != GAP_INVALID_HANDLE) {
      BtaGattQueue::ReadCharacteristic(
          device->conn_id, csis_inst->svc_data.lock_handle.val_hdl,
          [](uint16_t conn_id, tGATT_STATUS status, uint16_t handle,
             uint16_t len, uint8_t* value, void* user_data) {
            if (instance)
              instance->OnCsisLockReadRsp(conn_id, status, handle, len, value,
                                          (bool)user_data);
          },
          (void*)notify_after_lock_read);
    }

    /* Read Size */
    if (csis_inst->svc_data.size_handle.val_hdl != GAP_INVALID_HANDLE) {
      BtaGattQueue::ReadCharacteristic(
          device->conn_id, csis_inst->svc_data.size_handle.val_hdl,
          [](uint16_t conn_id, tGATT_STATUS status, uint16_t handle,
             uint16_t len, uint8_t* value, void* user_data) {
            if (instance)
              instance->OnCsisSizeValueUpdate(conn_id, status, handle, len,
                                              value, (bool)user_data);
          },
          (void*)notify_after_size_read);
    }

    /* Read Rank */
    if (csis_inst->svc_data.rank_handle != GAP_INVALID_HANDLE) {
      BtaGattQueue::ReadCharacteristic(
          device->conn_id, csis_inst->svc_data.rank_handle,
          [](uint16_t conn_id, tGATT_STATUS status, uint16_t handle,
             uint16_t len, uint8_t* value, void* user_data) {
            if (instance)
              instance->OnCsisRankReadRsp(conn_id, status, handle, len, value,
                                          (bool)user_data);
          },
          (void*)notify_after_rank_read);
    }

    return true;
  }

  /* These are all generic GATT event handlers calling HAS specific code. */
  void GattcCallback(tBTA_GATTC_EVT event, tBTA_GATTC* p_data) {
    log::info("event = 0x{:02x}", event);

    /* This is in case Csis CleanUp is already done
     * while GATT is still up and could send events
     */
    if (!instance) return;

    switch (event) {
      case BTA_GATTC_DEREG_EVT:
        break;

      case BTA_GATTC_OPEN_EVT:
        OnGattConnected(p_data->open);
        break;

      case BTA_GATTC_CLOSE_EVT:
        OnGattDisconnected(p_data->close);
        break;

      case BTA_GATTC_SEARCH_CMPL_EVT:
        OnGattServiceSearchComplete(p_data->search_cmpl);
        break;

      case BTA_GATTC_NOTIF_EVT:
        OnGattNotification(p_data->notify);
        break;

      case BTA_GATTC_ENC_CMPL_CB_EVT: {
        uint8_t encryption_status;
        if (BTM_IsEncrypted(p_data->enc_cmpl.remote_bda, BT_TRANSPORT_LE)) {
          encryption_status = BTM_SUCCESS;
        } else {
          encryption_status = BTM_FAILED_ON_SECURITY;
        }
        OnLeEncryptionComplete(p_data->enc_cmpl.remote_bda, encryption_status);
      } break;

      case BTA_GATTC_SRVC_CHG_EVT:
        OnGattServiceChangeEvent(p_data->remote_bda);
        break;

      case BTA_GATTC_SRVC_DISC_DONE_EVT:
        OnGattServiceDiscoveryDoneEvent(p_data->remote_bda);
        break;

      default:
        break;
    }
  }

  void OnGattConnected(const tBTA_GATTC_OPEN& evt) {
    log::info("{}, conn_id=0x{:04x}, transport={}, status={}(0x{:02x})",
              ADDRESS_TO_LOGGABLE_CSTR(evt.remote_bda), evt.conn_id,
              bt_transport_text(evt.transport), gatt_status_text(evt.status),
              evt.status);

    if (evt.transport != BT_TRANSPORT_LE) {
      log::warn("Only LE connection is allowed (transport {})",
                bt_transport_text(evt.transport));
      BTA_GATTC_Close(evt.conn_id);
      return;
    }

    auto device = FindDeviceByAddress(evt.remote_bda);
    if (device == nullptr) {
      log::debug("Skipping unknown device, address= {}",
                 ADDRESS_TO_LOGGABLE_CSTR(evt.remote_bda));
      BTA_GATTC_Close(evt.conn_id);
      return;
    }

    if (evt.status != GATT_SUCCESS) {
      log::error("Failed to connect to server device {}",
                 ADDRESS_TO_LOGGABLE_CSTR(evt.remote_bda));
      if (device->connecting_actively)
        callbacks_->OnConnectionState(evt.remote_bda,
                                      ConnectionState::DISCONNECTED);
      DoDisconnectCleanUp(device);
      return;
    }

    device->connecting_actively = false;
    device->conn_id = evt.conn_id;

    /* Verify bond */
    if (BTM_SecIsSecurityPending(device->addr)) {
      /* if security collision happened, wait for encryption done
       * (BTA_GATTC_ENC_CMPL_CB_EVT) */
      return;
    }

    /* verify bond */
    if (BTM_IsEncrypted(device->addr, BT_TRANSPORT_LE)) {
      /* if link has been encrypted */
      OnEncrypted(device);
      return;
    }

    int result = BTM_SetEncryption(device->addr, BT_TRANSPORT_LE, nullptr,
                                   nullptr, BTM_BLE_SEC_ENCRYPT);

    log::info("Encryption required for {}. Request result: 0x{:02x}",
              ADDRESS_TO_LOGGABLE_CSTR(device->addr), result);

    if (result == BTM_ERR_KEY_MISSING) {
      log::error("Link key unknown for {}, disconnect profile",
                 ADDRESS_TO_LOGGABLE_CSTR(device->addr));
      BTA_GATTC_Close(device->conn_id);
    }
  }

  void OnGattDisconnected(const tBTA_GATTC_CLOSE& evt) {
    auto device = FindDeviceByAddress(evt.remote_bda);
    if (device == nullptr) {
      log::warn("Skipping unknown device disconnect, conn_id= 0x{:04x}",
                evt.conn_id);
      return;
    }

    log::debug("device={}", ADDRESS_TO_LOGGABLE_CSTR(device->addr));

    callbacks_->OnConnectionState(evt.remote_bda,
                                  ConnectionState::DISCONNECTED);

    // Unlock others only if device was locked by us but has disconnected
    // unexpectedly.
    if ((evt.reason == GATT_CONN_TIMEOUT) ||
        (evt.reason == GATT_CONN_TERMINATE_PEER_USER)) {
      device->ForEachCsisInstance(
          [&](const std::shared_ptr<CsisInstance>& csis_inst) {
            auto csis_group = FindCsisGroup(csis_inst->GetGroupId());
            if (csis_group == nullptr) return;
            if ((csis_group->GetCurrentLockState() ==
                 CsisLockState::CSIS_STATE_LOCKED)) {
              HandleCsisLockProcedureError(
                  csis_group, device,
                  CsisGroupLockStatus::LOCKED_GROUP_MEMBER_LOST);
            }
          });
    }

    DoDisconnectCleanUp(device);
  }

  void OnGattServiceSearchComplete(const tBTA_GATTC_SEARCH_CMPL& evt) {
    auto device = FindDeviceByConnId(evt.conn_id);

    if (device == nullptr) {
      log::warn("Skipping unknown device, conn_id= 0x{:4x}", evt.conn_id);
      return;
    }

    /* verify encryption enabled */
    if (!BTM_IsEncrypted(device->addr, BT_TRANSPORT_LE)) {
      log::warn("Device not yet bonded - waiting for encryption");
      return;
    }

    /* Ignore if our service data is valid (discovery initiated by someone
     * else?) */
    if (!device->is_gatt_service_valid) {
      if (evt.status != GATT_SUCCESS) {
        log::error("Service discovery failed");
        BTA_GATTC_Close(device->conn_id);
        DoDisconnectCleanUp(device);
        return;
      }

      LOG_VERBOSE();

      const std::list<gatt::Service>* all_services =
          BTA_GATTC_GetServices(device->conn_id);

      std::vector<uint16_t> all_csis_start_handles;

      /* Le's just find all the CSIS primary services and store the start
       * handles */
      for (auto& svrc : *all_services) {
        if (svrc.uuid == kCsisServiceUuid) {
          all_csis_start_handles.push_back(svrc.handle);
        }
      }

      if (all_csis_start_handles.size() == 0) {
        log::debug("No Csis instances found");
        BTA_GATTC_Close(device->conn_id);
        RemoveCsisDevice(device, bluetooth::groups::kGroupUnknown);
        return;
      }

      for (auto& svrc : *all_services) {
        if (svrc.uuid == kCsisServiceUuid) continue;

        /* Try to find context for CSIS instances */
        for (auto& included_srvc : svrc.included_services) {
          if (included_srvc.uuid == kCsisServiceUuid) {
            auto csis_svrc = BTA_GATTC_GetOwningService(
                device->conn_id, included_srvc.start_handle);
            auto iter = std::find(all_csis_start_handles.begin(),
                                  all_csis_start_handles.end(),
                                  included_srvc.start_handle);
            if (iter != all_csis_start_handles.end())
              all_csis_start_handles.erase(iter);
            instance->OnCsisServiceFound(device, csis_svrc, svrc.uuid,
                                         all_csis_start_handles.empty());
          }
        }
      }

      /* Here if CSIS is included, all_csis_start_handles should be empty
       * Otherwise it means, we have some primary CSIS without a context,
       * which means it is for the complete device.
       * As per spec, there can be only one service like this.
       */
      if (all_csis_start_handles.size()) {
        log::debug("there is {} primary services without a context",
                   static_cast<int>(all_csis_start_handles.size()));
        auto csis_svrc = BTA_GATTC_GetOwningService(device->conn_id,
                                                    all_csis_start_handles[0]);
        instance->OnCsisServiceFound(
            device, csis_svrc, bluetooth::groups::kGenericContextUuid, true);
        all_csis_start_handles.clear();
      }
    } else {
      /* This might be set already if there is no optional attributes to read
       * or write.
       */
      if (evt.status == GATT_SUCCESS) {
        NotifyCsisDeviceValidAndStoreIfNeeded(device);
      }
    }
  }

  void OnGattNotification(const tBTA_GATTC_NOTIFY& evt) {
    /* Reject invalid lengths and indications as they are not supported */
    if (!evt.is_notify || evt.len > GATT_MAX_ATTR_LEN) {
      log::error(": rejected BTA_GATTC_NOTIF_EVT. is_notify = {}, len= {}",
                 evt.is_notify, evt.len);
    }

    OnCsisNotification(evt.conn_id, evt.handle, evt.len, evt.value);
  }

  void OnLeEncryptionComplete(const RawAddress& address, uint8_t status) {
    log::info("{}", ADDRESS_TO_LOGGABLE_CSTR(address));
    auto device = FindDeviceByAddress(address);
    if (device == nullptr) {
      log::warn("Skipping unknown device {}",
                ADDRESS_TO_LOGGABLE_CSTR(address));
      return;
    }

    if (status != BTM_SUCCESS) {
      log::error("encryption failed. status: 0x{:02x}", status);

      BTA_GATTC_Close(device->conn_id);
      return;
    }

    if (device->is_gatt_service_valid) {
      instance->OnEncrypted(device);
    } else {
      BTA_GATTC_ServiceSearchRequest(device->conn_id, &kCsisServiceUuid);
    }
  }

  void ClearDeviceInformationAndStartSearch(
      std::shared_ptr<CsisDevice> device) {
    log::info("{}", ADDRESS_TO_LOGGABLE_CSTR(device->addr));
    if (device->is_gatt_service_valid == false) {
      log::debug("Device database already invalidated.");
      return;
    }

    /* Invalidate service discovery results */
    BtaGattQueue::Clean(device->conn_id);
    DeregisterNotifications(device);
    device->ClearSvcData();
    BTA_GATTC_ServiceSearchRequest(device->conn_id, &kCsisServiceUuid);
  }

  void OnGattServiceChangeEvent(const RawAddress& address) {
    auto device = FindDeviceByAddress(address);
    if (!device) {
      log::warn("Skipping unknown device {}",
                ADDRESS_TO_LOGGABLE_CSTR(address));
      return;
    }

    log::info("{}", ADDRESS_TO_LOGGABLE_CSTR(address));
    ClearDeviceInformationAndStartSearch(device);
  }

  void OnGattServiceDiscoveryDoneEvent(const RawAddress& address) {
    auto device = FindDeviceByAddress(address);
    if (!device) {
      log::warn("Skipping unknown device {}",
                ADDRESS_TO_LOGGABLE_CSTR(address));
      return;
    }

    log::debug("address={}", ADDRESS_TO_LOGGABLE_CSTR(address));

    if (!device->is_gatt_service_valid)
      BTA_GATTC_ServiceSearchRequest(device->conn_id, &kCsisServiceUuid);
  }

  static uint16_t FindCccHandle(uint16_t conn_id, uint16_t char_handle) {
    const gatt::Characteristic* p_char =
        BTA_GATTC_GetCharacteristic(conn_id, char_handle);
    if (!p_char) {
      log::warn("No such characteristic: 0x{:04x}", char_handle);
      return GAP_INVALID_HANDLE;
    }

    for (const gatt::Descriptor& desc : p_char->descriptors) {
      if (desc.uuid == Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG))
        return desc.handle;
    }

    return GAP_INVALID_HANDLE;
  }

  void SubscribeForNotifications(uint16_t conn_id, const RawAddress& address,
                                 uint16_t value_handle, uint16_t ccc_handle) {
    if (value_handle != GAP_INVALID_HANDLE) {
      tGATT_STATUS register_status =
          BTA_GATTC_RegisterForNotifications(gatt_if_, address, value_handle);
      log::debug(
          "BTA_GATTC_RegisterForNotifications, status=0x{:02x}, value={}, "
          "ccc=0x{:04x}",
          register_status, loghex(value_handle), ccc_handle);

      if (register_status != GATT_SUCCESS) return;
    }

    std::vector<uint8_t> value(2);
    uint8_t* value_ptr = value.data();
    UINT16_TO_STREAM(value_ptr, GATT_CHAR_CLIENT_CONFIG_NOTIFICATION);
    BtaGattQueue::WriteDescriptor(
        conn_id, ccc_handle, std::move(value), GATT_WRITE,
        [](uint16_t conn_id, tGATT_STATUS status, uint16_t handle, uint16_t len,
           const uint8_t* value, void* user_data) {
          if (instance)
            instance->OnGattWriteCcc(conn_id, status, handle, user_data);
        },
        nullptr);
  }

  void DisableGattNotification(uint16_t conn_id, const RawAddress& address,
                               uint16_t value_handle) {
    if (value_handle != GAP_INVALID_HANDLE) {
      tGATT_STATUS register_status =
          BTA_GATTC_DeregisterForNotifications(gatt_if_, address, value_handle);
      log::debug(
          "DisableGattNotification, status=0x{:02x}, value_handle=0x{:04x}",
          register_status, value_handle);

      if (register_status != GATT_SUCCESS) return;
    }
  }

  void SirkValueReadCompleteDuringPairing(tGATT_STATUS status,
                                          const RawAddress& address,
                                          uint8_t sirk_type,
                                          Octet16& received_sirk) {
    log::info("{}, status: 0x{:02x}", ADDRESS_TO_LOGGABLE_CSTR(address),
              status);

    auto device = FindDeviceByAddress(address);
    if (device == nullptr) {
      log::error("Unknown device {}", ADDRESS_TO_LOGGABLE_CSTR(address));
      BTA_DmSirkConfirmDeviceReply(address, false);
      return;
    }

    auto group_id_to_join = device->GetExpectedGroupIdMember();
    device->SetPairingSirkReadFlag(false);

    /* Verify group still exist, if not it means user forget the group and
     * paring should be rejected.
     */
    auto csis_group = FindCsisGroup(group_id_to_join);
    if (!csis_group) {
      log::error("Group {} removed during paring a set member",
                 group_id_to_join);
      RemoveDevice(address);
      BTA_DmSirkConfirmDeviceReply(address, false);
      return;
    }

    if (status != GATT_SUCCESS) {
      log::info("Invalid member, can't read SIRK (status: 0x{:02x})", status);
      BTA_DmSirkConfirmDeviceReply(address, false);
      return;
    }

    /* Verify if sirk is not all zeros */
    Octet16 zero{};
    if (memcmp(zero.data(), received_sirk.data(), 16) == 0) {
      log::error("Received invalid zero SIRK address: {}",
                 ADDRESS_TO_LOGGABLE_CSTR(address));
      BTA_DmSirkConfirmDeviceReply(address, false);
      return;
    }

    if (sirk_type == bluetooth::csis::kCsisSirkTypeEncrypted) {
      /* Decrypt encrypted SIRK */
      Octet16 sirk;
      sdf(address, received_sirk, sirk);
      received_sirk = sirk;
    }

    if (!csis_group->IsSirkBelongsToGroup(received_sirk)) {
      /*
       * Joining member must join already existing group otherwise it means
       * that its SIRK is different. Device connection was triggered by RSI
       * match for group.
       */
      log::error("Joining device {}, does not match any existig group",
                 ADDRESS_TO_LOGGABLE_CSTR(address));
      BTA_DmSirkConfirmDeviceReply(address, false);
      return;
    }

    log::info("Device {}, verified successfully by SIRK",
              ADDRESS_TO_LOGGABLE_CSTR(address));
    BTA_DmSirkConfirmDeviceReply(address, true);

    /* It was temporary device and we can remove it. When upper layer
     * decides to connect CSIS it will be added then
     */
    RemoveDevice(address);
  }

  void VerifySetMember(const RawAddress& address) {
    auto device = FindDeviceByAddress(address);

    log::info("Device: {}", ADDRESS_TO_LOGGABLE_CSTR(address));

    /* It's ok for device to not be a CSIS device at all */
    if (!device) {
      log::info("Valid - new member");
      BTA_DmSirkConfirmDeviceReply(address, true);
      return;
    }

    auto group_id_to_join = device->GetExpectedGroupIdMember();
    if (group_id_to_join == bluetooth::groups::kGroupUnknown) {
      log::warn(
          "Device {} (conn_id=0x{:04x}) is already known to CSIS (# of "
          "instances={}) but it is not scheduled to join any group.",
          ADDRESS_TO_LOGGABLE_CSTR(address), device->conn_id,
          device->GetNumberOfCsisInstances());
      BTA_DmSirkConfirmDeviceReply(address, true);
      return;
    }

    if (!gatt_cl_read_sirk_req(
            address,
            base::BindOnce(&CsisClientImpl::SirkValueReadCompleteDuringPairing,
                           weak_factory_.GetWeakPtr()))) {
      log::error("Could not read SIKR of {}",
                 ADDRESS_TO_LOGGABLE_CSTR(address));
      BTA_DmSirkConfirmDeviceReply(address, false);
      return;
    }
    device->SetPairingSirkReadFlag(true);
  }

  uint8_t gatt_if_;
  bluetooth::csis::CsisClientCallbacks* callbacks_;
  std::list<std::shared_ptr<CsisDevice>> devices_;
  std::list<std::shared_ptr<CsisGroup>> csis_groups_;
  DeviceGroups* dev_groups_;
  int discovering_group_ = bluetooth::groups::kGroupUnknown;

  base::WeakPtrFactory<CsisClientImpl> weak_factory_{this};
};

class DeviceGroupsCallbacksImpl : public DeviceGroupsCallbacks {
 public:
  void OnGroupAdded(const RawAddress& address, const bluetooth::Uuid& uuid,
                    int group_id) override {
    if (instance) instance->OnGroupAddedCb(address, uuid, group_id);
  }

  void OnGroupMemberAdded(const RawAddress& address, int group_id) override {
    if (instance) instance->OnGroupMemberAddedCb(address, group_id);
  }

  void OnGroupRemoved(const bluetooth::Uuid& uuid, int group_id) override {
    if (instance) instance->OnGroupRemovedCb(uuid, group_id);
  }

  void OnGroupMemberRemoved(const RawAddress& address, int group_id) override {
    if (instance) instance->OnGroupMemberRemovedCb(address, group_id);
  }

  void OnGroupAddFromStorage(const RawAddress& address,
                             const bluetooth::Uuid& uuid,
                             int group_id) override {
    if (instance) instance->OnGroupAddFromStorageCb(address, uuid, group_id);
  }
};

class DeviceGroupsCallbacksImpl;
DeviceGroupsCallbacksImpl deviceGroupsCallbacksImpl;

}  // namespace

void CsisClient::Initialize(bluetooth::csis::CsisClientCallbacks* callbacks,
                            Closure initCb) {
  std::scoped_lock<std::mutex> lock(instance_mutex);
  if (instance) {
    log::info("Already initialized!");
    return;
  }

  device_group_callbacks = &deviceGroupsCallbacksImpl;
  instance = new CsisClientImpl(callbacks, initCb);
}

bool CsisClient::IsCsisClientRunning() { return instance; }

CsisClient* CsisClient::Get(void) {
  CHECK(instance);
  return instance;
}

void CsisClient::AddFromStorage(const RawAddress& addr,
                                const std::vector<uint8_t>& in) {
  if (!instance) {
    log::error("Not initialized yet!");
    return;
  }

  instance->AddFromStorage(addr, in);
}

bool CsisClient::GetForStorage(const RawAddress& addr,
                               std::vector<uint8_t>& out) {
  if (!instance) {
    log::error("Not initialized yet!");
    return false;
  }

  return instance->SerializeSets(addr, out);
}

void CsisClient::CleanUp() {
  std::scoped_lock<std::mutex> lock(instance_mutex);
  BTA_DmSirkSecCbRegister(nullptr);
  CsisClientImpl* ptr = instance;
  instance = nullptr;

  if (ptr) {
    ptr->CleanUp();
    delete ptr;
  }
}

void CsisClient::DebugDump(int fd) {
  std::scoped_lock<std::mutex> lock(instance_mutex);
  dprintf(fd, "Coordinated Set Service Client:\n");
  if (instance) instance->Dump(fd);
  dprintf(fd, "\n");
}
