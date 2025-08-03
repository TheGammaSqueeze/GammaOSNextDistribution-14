/*
 * Copyright 2022 The Android Open Source Project
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

#include <android_bluetooth_flags.h>

#include <chrono>
#include <memory>
#include <utility>

#include "common/callback.h"
#include "common/init_flags.h"
#include "hci/address_with_type.h"
#include "hci/event_checkers.h"
#include "hci/hci_packets.h"
#include "hci/le_scanning_callback.h"
#include "hci/le_scanning_interface.h"
#include "hci/le_scanning_reassembler.h"
#include "hci/uuid.h"
#include "module.h"
#include "os/alarm.h"
#include "os/log.h"

namespace bluetooth {
namespace hci {

constexpr std::chrono::duration kPeriodicSyncTimeout = std::chrono::seconds(30);
constexpr int kMaxSyncTransactions = 16;

enum PeriodicSyncState : int {
  PERIODIC_SYNC_STATE_IDLE = 0,
  PERIODIC_SYNC_STATE_PENDING,
  PERIODIC_SYNC_STATE_ESTABLISHED,
};

struct PeriodicSyncTransferStates {
  int pa_source;
  int connection_handle;
  Address addr;
};

struct PeriodicSyncStates {
  int request_id;
  uint8_t advertiser_sid;
  AddressWithType address_with_type;
  uint16_t sync_handle;
  PeriodicSyncState sync_state;
};

struct PendingPeriodicSyncRequest {
  PendingPeriodicSyncRequest(
      uint8_t advertiser_sid,
      AddressWithType address_with_type,
      uint16_t skip,
      uint16_t sync_timeout,
      os::Handler* handler)
      : advertiser_sid(advertiser_sid),
        address_with_type(std::move(address_with_type)),
        skip(skip),
        sync_timeout(sync_timeout),
        sync_timeout_alarm(handler) {}
  bool busy = false;
  uint8_t advertiser_sid;
  AddressWithType address_with_type;
  uint16_t skip;
  uint16_t sync_timeout;
  os::Alarm sync_timeout_alarm;
};

class PeriodicSyncManager {
 public:
  explicit PeriodicSyncManager(ScanningCallback* callbacks)
      : le_scanning_interface_(nullptr), handler_(nullptr), callbacks_(callbacks), sync_received_callback_id(0) {}

  void Init(hci::LeScanningInterface* le_scanning_interface, os::Handler* handler) {
    le_scanning_interface_ = le_scanning_interface;
    handler_ = handler;
  }

  void SetScanningCallback(ScanningCallback* callbacks) {
    callbacks_ = callbacks;
  }

  void StartSync(const PeriodicSyncStates& request, uint16_t skip, uint16_t sync_timeout) {
    if (periodic_syncs_.size() >= kMaxSyncTransactions) {
      int status = static_cast<int>(ErrorCode::CONNECTION_REJECTED_LIMITED_RESOURCES);
      callbacks_->OnPeriodicSyncStarted(
          request.request_id, status, 0, request.advertiser_sid, request.address_with_type, 0, 0);
      return;
    }
    auto address_type = request.address_with_type.GetAddressType();
    ASSERT_LOG(
        (address_type == AddressType::PUBLIC_DEVICE_ADDRESS || address_type == AddressType::RANDOM_DEVICE_ADDRESS),
        "Invalid address type %s",
        AddressTypeText(address_type).c_str());
    periodic_syncs_.emplace_back(request);
    LOG_DEBUG("address = %s, sid = %d",
              ADDRESS_TO_LOGGABLE_CSTR(request.address_with_type),
              request.advertiser_sid);
    pending_sync_requests_.emplace_back(
        request.advertiser_sid, request.address_with_type, skip, sync_timeout, handler_);
    HandleNextRequest();
  }

  void StopSync(uint16_t handle) {
    LOG_DEBUG("[PSync]: handle = %u", handle);
    auto periodic_sync = GetEstablishedSyncFromHandle(handle);
    if (periodic_sync == periodic_syncs_.end()) {
      LOG_ERROR("[PSync]: invalid index for handle %u", handle);
      le_scanning_interface_->EnqueueCommand(
          hci::LePeriodicAdvertisingTerminateSyncBuilder::Create(handle),
          handler_->BindOnce(check_complete<LePeriodicAdvertisingTerminateSyncCompleteView>));
      return;
    };
    periodic_syncs_.erase(periodic_sync);
    le_scanning_interface_->EnqueueCommand(
        hci::LePeriodicAdvertisingTerminateSyncBuilder::Create(handle),
        handler_->BindOnce(check_complete<LePeriodicAdvertisingTerminateSyncCompleteView>));
  }

  void CancelCreateSync(uint8_t adv_sid, Address address) {
    LOG_DEBUG("[PSync]");
    auto periodic_sync = GetSyncFromAddressAndSid(address, adv_sid);
    if (periodic_sync == periodic_syncs_.end()) {
      LOG_ERROR("[PSync]:Invalid index for sid=%u", adv_sid);
      return;
    }

    if (periodic_sync->sync_state == PERIODIC_SYNC_STATE_PENDING) {
      LOG_WARN("[PSync]: Sync state is pending");
      le_scanning_interface_->EnqueueCommand(
          hci::LePeriodicAdvertisingCreateSyncCancelBuilder::Create(),
          handler_->BindOnceOn(this, &PeriodicSyncManager::HandlePeriodicAdvertisingCreateSyncCancelStatus));
    } else if (periodic_sync->sync_state == PERIODIC_SYNC_STATE_IDLE) {
      LOG_DEBUG("[PSync]: Removing Sync request from queue");
      CleanUpRequest(adv_sid, address);
    }
    periodic_syncs_.erase(periodic_sync);
  }

  void TransferSync(
      const Address& address, uint16_t service_data, uint16_t sync_handle, int pa_source, uint16_t connection_handle) {
    if (periodic_sync_transfers_.size() >= kMaxSyncTransactions) {
      int status = static_cast<int>(ErrorCode::CONNECTION_REJECTED_LIMITED_RESOURCES);
      callbacks_->OnPeriodicSyncTransferred(pa_source, status, address);
      return;
    }

    PeriodicSyncTransferStates request{pa_source, connection_handle, address};
    periodic_sync_transfers_.emplace_back(request);
    le_scanning_interface_->EnqueueCommand(
        hci::LePeriodicAdvertisingSyncTransferBuilder::Create(connection_handle, service_data, sync_handle),
        handler_->BindOnceOn(
            this,
            &PeriodicSyncManager::HandlePeriodicAdvertisingSyncTransferComplete<
                LePeriodicAdvertisingSyncTransferCompleteView>,
            connection_handle));
  }

  void SyncSetInfo(
      const Address& address, uint16_t service_data, uint8_t adv_handle, int pa_source, uint16_t connection_handle) {
    if (periodic_sync_transfers_.size() >= kMaxSyncTransactions) {
      int status = static_cast<int>(ErrorCode::CONNECTION_REJECTED_LIMITED_RESOURCES);
      callbacks_->OnPeriodicSyncTransferred(pa_source, status, address);
      return;
    }
    PeriodicSyncTransferStates request{pa_source, connection_handle, address};
    periodic_sync_transfers_.emplace_back(request);
    le_scanning_interface_->EnqueueCommand(
        hci::LePeriodicAdvertisingSetInfoTransferBuilder::Create(connection_handle, service_data, adv_handle),
        handler_->BindOnceOn(
            this,
            &PeriodicSyncManager::HandlePeriodicAdvertisingSyncTransferComplete<
                LePeriodicAdvertisingSetInfoTransferCompleteView>,
            connection_handle));
  }

  void SyncTxParameters(
      const Address& /* address */, uint8_t mode, uint16_t skip, uint16_t timeout, int reg_id) {
    LOG_DEBUG("[PAST]: mode=%u, skip=%u, timeout=%u", mode, skip, timeout);
    auto sync_cte_type = static_cast<CteType>(
        static_cast<uint8_t>(PeriodicSyncCteType::AVOID_AOA_CONSTANT_TONE_EXTENSION) |
        static_cast<uint8_t>(PeriodicSyncCteType::AVOID_AOD_CONSTANT_TONE_EXTENSION_WITH_ONE_US_SLOTS) |
        static_cast<uint8_t>(PeriodicSyncCteType::AVOID_AOD_CONSTANT_TONE_EXTENSION_WITH_TWO_US_SLOTS));
    sync_received_callback_registered_ = true;
    sync_received_callback_id = reg_id;

    le_scanning_interface_->EnqueueCommand(
        hci::LeSetDefaultPeriodicAdvertisingSyncTransferParametersBuilder::Create(
            static_cast<SyncTransferMode>(mode), skip, timeout, sync_cte_type),
        handler_->BindOnce(
            check_complete<LeSetDefaultPeriodicAdvertisingSyncTransferParametersCompleteView>));
  }

  void HandlePeriodicAdvertisingCreateSyncStatus(CommandStatusView) {}

  void HandlePeriodicAdvertisingCreateSyncCancelStatus(CommandCompleteView) {}

  template <class View>
  void HandlePeriodicAdvertisingSyncTransferComplete(uint16_t connection_handle, CommandCompleteView view) {
    ASSERT(view.IsValid());
    auto status_view = View::Create(view);
    ASSERT(status_view.IsValid());
    if (status_view.GetStatus() != ErrorCode::SUCCESS) {
      LOG_WARN(
          "Got a Command complete %s, status %s, connection_handle %d",
          OpCodeText(view.GetCommandOpCode()).c_str(),
          ErrorCodeText(status_view.GetStatus()).c_str(),
          connection_handle);
    } else {
      LOG_DEBUG(
          "Got a Command complete %s, status %s, connection_handle %d",
          OpCodeText(view.GetCommandOpCode()).c_str(),
          ErrorCodeText(status_view.GetStatus()).c_str(),
          connection_handle);
    }

    auto periodic_sync_transfer = GetSyncTransferRequestFromConnectionHandle(connection_handle);
    if (periodic_sync_transfer == periodic_sync_transfers_.end()) {
      LOG_ERROR("[PAST]:Invalid, conn_handle %u not found in DB", connection_handle);
      return;
    };

    callbacks_->OnPeriodicSyncTransferred(
        periodic_sync_transfer->pa_source, (uint16_t)status_view.GetStatus(), periodic_sync_transfer->addr);
    periodic_sync_transfers_.erase(periodic_sync_transfer);
  }

  void HandleLePeriodicAdvertisingSyncEstablished(LePeriodicAdvertisingSyncEstablishedView event_view) {
    ASSERT(event_view.IsValid());
    LOG_DEBUG(
        "[PSync]: status=%d, sync_handle=%d, s_id=%d, "
        "address_type=%d, adv_phy=%d,adv_interval=%d, clock_acc=%d",
        (uint16_t)event_view.GetStatus(),
        event_view.GetSyncHandle(),
        event_view.GetAdvertisingSid(),
        (uint16_t)event_view.GetAdvertiserAddressType(),
        (uint16_t)event_view.GetAdvertiserPhy(),
        event_view.GetPeriodicAdvertisingInterval(),
        (uint16_t)event_view.GetAdvertiserClockAccuracy());

    auto pending_sync_request =
        GetPendingSyncFromAddressAndSid(event_view.GetAdvertiserAddress(), event_view.GetAdvertisingSid());
    if (pending_sync_request != pending_sync_requests_.end()) {
      pending_sync_request->sync_timeout_alarm.Cancel();
    }

    auto address_with_type = AddressWithType(event_view.GetAdvertiserAddress(), event_view.GetAdvertiserAddressType());
    auto peer_address_type = address_with_type.GetAddressType();
    AddressType temp_address_type;
    switch (peer_address_type) {
      case AddressType::PUBLIC_DEVICE_ADDRESS:
      case AddressType::PUBLIC_IDENTITY_ADDRESS:
        temp_address_type = AddressType::PUBLIC_DEVICE_ADDRESS;
        break;
      case AddressType::RANDOM_DEVICE_ADDRESS:
      case AddressType::RANDOM_IDENTITY_ADDRESS:
        temp_address_type = AddressType::RANDOM_DEVICE_ADDRESS;
        break;
    }

    auto periodic_sync = GetSyncFromAddressWithTypeAndSid(
        AddressWithType(event_view.GetAdvertiserAddress(), temp_address_type), event_view.GetAdvertisingSid());
    if (periodic_sync == periodic_syncs_.end()) {
      LOG_WARN("[PSync]: Invalid address and sid for sync established");
      if (event_view.GetStatus() == ErrorCode::SUCCESS) {
        LOG_WARN("Terminate sync");
        le_scanning_interface_->EnqueueCommand(
            hci::LePeriodicAdvertisingTerminateSyncBuilder::Create(event_view.GetSyncHandle()),
            handler_->BindOnce(check_complete<LePeriodicAdvertisingTerminateSyncCompleteView>));
      }
      AdvanceRequest();
      return;
    }
    periodic_sync->sync_handle = event_view.GetSyncHandle();
    periodic_sync->sync_state = PERIODIC_SYNC_STATE_ESTABLISHED;
    callbacks_->OnPeriodicSyncStarted(
        periodic_sync->request_id,
        (uint8_t)event_view.GetStatus(),
        event_view.GetSyncHandle(),
        event_view.GetAdvertisingSid(),
        address_with_type,
        (uint16_t)event_view.GetAdvertiserPhy(),
        event_view.GetPeriodicAdvertisingInterval());
    AdvanceRequest();
  }

  void HandleLePeriodicAdvertisingReport(LePeriodicAdvertisingReportView event_view) {
    ASSERT(event_view.IsValid());
    LOG_DEBUG(
        "[PSync]: sync_handle = %u, tx_power = %d, rssi = %d,"
        "cte_type = %u, data_status = %u, data_len = %u",
        event_view.GetSyncHandle(),
        event_view.GetTxPower(),
        event_view.GetRssi(),
        (uint16_t)event_view.GetCteType(),
        (uint16_t)event_view.GetDataStatus(),
        (uint16_t)event_view.GetData().size());

    uint16_t sync_handle = event_view.GetSyncHandle();
    auto periodic_sync = GetEstablishedSyncFromHandle(sync_handle);
    if (periodic_sync == periodic_syncs_.end()) {
      LOG_ERROR("[PSync]: index not found for handle %u", sync_handle);
      return;
    }

    auto complete_advertising_data =
        IS_FLAG_ENABLED(le_periodic_scanning_reassembler)
            ? scanning_reassembler_.ProcessPeriodicAdvertisingReport(
                  sync_handle, DataStatus(event_view.GetDataStatus()), event_view.GetData())
            : event_view.GetData();
    if (!complete_advertising_data.has_value()) {
      return;
    }

    LOG_DEBUG("%s", "[PSync]: invoking callback");
    callbacks_->OnPeriodicSyncReport(
        sync_handle,
        event_view.GetTxPower(),
        event_view.GetRssi(),
        (uint16_t)event_view.GetDataStatus(),
        complete_advertising_data.value());
  }

  void HandleLePeriodicAdvertisingSyncLost(LePeriodicAdvertisingSyncLostView event_view) {
    ASSERT(event_view.IsValid());
    uint16_t sync_handle = event_view.GetSyncHandle();
    LOG_DEBUG("[PSync]: sync_handle = %d", sync_handle);
    callbacks_->OnPeriodicSyncLost(sync_handle);
    auto periodic_sync = GetEstablishedSyncFromHandle(sync_handle);
    if (periodic_sync == periodic_syncs_.end()) {
      LOG_ERROR("[PSync]: index not found for handle %u", sync_handle);
      return;
    }
    periodic_syncs_.erase(periodic_sync);
  }

  void HandleLePeriodicAdvertisingSyncTransferReceived(LePeriodicAdvertisingSyncTransferReceivedView event_view) {
    ASSERT(event_view.IsValid());
    uint8_t status = (uint8_t)event_view.GetStatus();
    uint8_t advertiser_phy = (uint8_t)event_view.GetAdvertiserPhy();
    LOG_DEBUG(
        "[PAST]: status = %u, connection_handle = %u, service_data = %u,"
        " sync_handle = %u, adv_sid = %u, address_type = %u, address = %s,"
        " advertiser_phy = %u, periodic_advertising_interval = %u, clock_accuracy = %u",
        status,
        event_view.GetConnectionHandle(),
        event_view.GetServiceData(),
        event_view.GetSyncHandle(),
        event_view.GetAdvertisingSid(),
        (uint8_t)event_view.GetAdvertiserAddressType(),
        ADDRESS_TO_LOGGABLE_CSTR(event_view.GetAdvertiserAddress()),
        advertiser_phy,
        event_view.GetPeriodicAdvertisingInterval(),
        (uint8_t)event_view.GetAdvertiserClockAccuracy());
    if (sync_received_callback_registered_) {
      callbacks_->OnPeriodicSyncStarted(
          sync_received_callback_id,
          status,
          event_view.GetSyncHandle(),
          event_view.GetAdvertisingSid(),
          AddressWithType(event_view.GetAdvertiserAddress(), event_view.GetAdvertiserAddressType()),
          advertiser_phy,
          event_view.GetPeriodicAdvertisingInterval());
    }
  }

  void OnStartSyncTimeout() {
    auto& request = pending_sync_requests_.front();
    LOG_WARN(
        "%s: sync timeout SID=%04X, bd_addr=%s",
        __func__,
        request.advertiser_sid,
        ADDRESS_TO_LOGGABLE_CSTR(request.address_with_type));
    uint8_t adv_sid = request.advertiser_sid;
    AddressWithType address_with_type = request.address_with_type;
    auto sync = GetSyncFromAddressWithTypeAndSid(address_with_type, adv_sid);
    le_scanning_interface_->EnqueueCommand(
        hci::LePeriodicAdvertisingCreateSyncCancelBuilder::Create(),
        handler_->BindOnceOn(this, &PeriodicSyncManager::HandlePeriodicAdvertisingCreateSyncCancelStatus));
    int status = static_cast<int>(ErrorCode::ADVERTISING_TIMEOUT);
    callbacks_->OnPeriodicSyncStarted(
        sync->request_id, status, 0, sync->advertiser_sid, request.address_with_type, 0, 0);
    RemoveSyncRequest(sync);
  }

  void HandleLeBigInfoAdvertisingReport(LeBigInfoAdvertisingReportView event_view) {
    ASSERT(event_view.IsValid());
    LOG_DEBUG(
        "[PAST]:sync_handle %u, num_bises = %u, nse = %u,"
        "iso_interval = %d, bn = %u, pto = %u, irc = %u, max_pdu = %u "
        "sdu_interval = %d, max_sdu = %u, phy = %u, framing = %u, encryption  = "
        "%u",
        event_view.GetSyncHandle(), event_view.GetNumBis(), event_view.GetNse(),
        event_view.GetIsoInterval(), event_view.GetBn(), event_view.GetPto(), event_view.GetIrc(),
        event_view.GetMaxPdu(), event_view.GetSduInterval(), event_view.GetMaxSdu(),
        static_cast<uint32_t>(event_view.GetPhy()), static_cast<uint32_t>(event_view.GetFraming()),
        static_cast<uint32_t>(event_view.GetEncryption()));

    uint16_t sync_handle = event_view.GetSyncHandle();
    auto periodic_sync = GetEstablishedSyncFromHandle(sync_handle);
    if (periodic_sync == periodic_syncs_.end()) {
      LOG_ERROR("[PSync]: index not found for handle %u", sync_handle);
      return;
    }
    LOG_DEBUG("%s", "[PSync]: invoking callback");
    callbacks_->OnBigInfoReport(sync_handle, event_view.GetEncryption() == Enable::ENABLED ? true : false);
  }

 private:
  std::list<PeriodicSyncStates>::iterator GetEstablishedSyncFromHandle(uint16_t handle) {
    for (auto it = periodic_syncs_.begin(); it != periodic_syncs_.end(); it++) {
      if (it->sync_handle == handle && it->sync_state == PeriodicSyncState::PERIODIC_SYNC_STATE_ESTABLISHED) {
        return it;
      }
    }
    return periodic_syncs_.end();
  }

  std::list<PeriodicSyncStates>::iterator GetSyncFromAddressWithTypeAndSid(
      const AddressWithType& address_with_type, uint8_t adv_sid) {
    for (auto it = periodic_syncs_.begin(); it != periodic_syncs_.end(); it++) {
      if (it->advertiser_sid == adv_sid && it->address_with_type == address_with_type) {
        return it;
      }
    }
    return periodic_syncs_.end();
  }

  std::list<PeriodicSyncStates>::iterator GetSyncFromAddressAndSid(const Address& address, uint8_t adv_sid) {
    for (auto it = periodic_syncs_.begin(); it != periodic_syncs_.end(); it++) {
      if (it->advertiser_sid == adv_sid && it->address_with_type.GetAddress() == address) {
        return it;
      }
    }
    return periodic_syncs_.end();
  }

  std::list<PendingPeriodicSyncRequest>::iterator GetPendingSyncFromAddressAndSid(
      const Address& address, uint8_t adv_sid) {
    for (auto it = pending_sync_requests_.begin(); it != pending_sync_requests_.end(); it++) {
      if (it->advertiser_sid == adv_sid && it->address_with_type.GetAddress() == address) {
        return it;
      }
    }
    return pending_sync_requests_.end();
  }

  void RemoveSyncRequest(std::list<PeriodicSyncStates>::iterator it) {
    periodic_syncs_.erase(it);
  }

  std::list<PeriodicSyncTransferStates>::iterator GetSyncTransferRequestFromConnectionHandle(
      uint16_t connection_handle) {
    for (auto it = periodic_sync_transfers_.begin(); it != periodic_sync_transfers_.end(); it++) {
      if (it->connection_handle == connection_handle) {
        return it;
      }
    }
    return periodic_sync_transfers_.end();
  }

  void HandleStartSyncRequest(uint8_t sid, const AddressWithType& address_with_type, uint16_t skip, uint16_t timeout) {
    PeriodicAdvertisingOptions options;
    auto sync_cte_type =
        static_cast<uint8_t>(PeriodicSyncCteType::AVOID_AOA_CONSTANT_TONE_EXTENSION) |
        static_cast<uint8_t>(
            PeriodicSyncCteType::AVOID_AOD_CONSTANT_TONE_EXTENSION_WITH_ONE_US_SLOTS) |
        static_cast<uint8_t>(
            PeriodicSyncCteType::AVOID_AOD_CONSTANT_TONE_EXTENSION_WITH_TWO_US_SLOTS);
    auto sync = GetSyncFromAddressWithTypeAndSid(address_with_type, sid);
    sync->sync_state = PERIODIC_SYNC_STATE_PENDING;
    AdvertisingAddressType advertisingAddressType =
        static_cast<AdvertisingAddressType>(address_with_type.GetAddressType());
    le_scanning_interface_->EnqueueCommand(
        hci::LePeriodicAdvertisingCreateSyncBuilder::Create(
            options, sid, advertisingAddressType, address_with_type.GetAddress(), skip, timeout, sync_cte_type),
        handler_->BindOnceOn(this, &PeriodicSyncManager::HandlePeriodicAdvertisingCreateSyncStatus));
  }

  void HandleNextRequest() {
    if (pending_sync_requests_.empty()) {
      LOG_DEBUG("pending_sync_requests_ empty");
      return;
    }
    auto& request = pending_sync_requests_.front();
    LOG_INFO(
        "executing sync request SID=%04X, bd_addr=%s",
        request.advertiser_sid,
        ADDRESS_TO_LOGGABLE_CSTR(request.address_with_type));
    if (request.busy) {
      LOG_INFO("Request is already busy");
      return;
    }
    request.busy = true;
    request.sync_timeout_alarm.Cancel();
    HandleStartSyncRequest(request.advertiser_sid, request.address_with_type, request.skip, request.sync_timeout);
    request.sync_timeout_alarm.Schedule(
        base::BindOnce(&PeriodicSyncManager::OnStartSyncTimeout, base::Unretained(this)), kPeriodicSyncTimeout);
  }

  void AdvanceRequest() {
    LOG_DEBUG("AdvanceRequest");
    if (pending_sync_requests_.empty()) {
      LOG_DEBUG("pending_sync_requests_ empty");
      return;
    }
    auto it = pending_sync_requests_.begin();
    pending_sync_requests_.erase(it);
    HandleNextRequest();
  }

  void CleanUpRequest(uint8_t advertiser_sid, Address address) {
    auto it = pending_sync_requests_.begin();
    while (it != pending_sync_requests_.end()) {
      if (it->advertiser_sid == advertiser_sid && it->address_with_type.GetAddress() == address) {
        LOG_INFO(
            "removing connection request SID=%04X, bd_addr=%s, busy=%d",
            it->advertiser_sid,
            ADDRESS_TO_LOGGABLE_CSTR(it->address_with_type),
            it->busy);
        it = pending_sync_requests_.erase(it);
      } else {
        ++it;
      }
    }
  }

  hci::LeScanningInterface* le_scanning_interface_;
  os::Handler* handler_;
  ScanningCallback* callbacks_;
  std::list<PendingPeriodicSyncRequest> pending_sync_requests_;
  std::list<PeriodicSyncStates> periodic_syncs_;
  std::list<PeriodicSyncTransferStates> periodic_sync_transfers_;
  LeScanningReassembler scanning_reassembler_;
  bool sync_received_callback_registered_ = false;
  int sync_received_callback_id{};
};

}  // namespace hci
}  // namespace bluetooth
