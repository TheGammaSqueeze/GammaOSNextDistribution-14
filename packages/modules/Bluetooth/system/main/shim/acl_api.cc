/*
 * Copyright 2020 The Android Open Source Project
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

#include "main/shim/acl_api.h"

#include <android_bluetooth_sysprop.h>
#include <base/location.h>
#include <cstdint>
#include <future>
#include <optional>

#include "hci/acl_manager.h"
#include "hci/remote_name_request.h"
#include "main/shim/entry.h"
#include "main/shim/helpers.h"
#include "main/shim/stack.h"
#include "osi/include/allocator.h"
#include "stack/btm/btm_sec.h"
#include "stack/btm/security_device_record.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/inq_hci_link_interface.h"
#include "stack/include/main_thread.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

void bluetooth::shim::ACL_CreateClassicConnection(
    const RawAddress& raw_address) {
  auto address = ToGdAddress(raw_address);
  Stack::GetInstance()->GetAcl()->CreateClassicConnection(address);
}

void bluetooth::shim::ACL_CancelClassicConnection(
    const RawAddress& raw_address) {
  auto address = ToGdAddress(raw_address);
  Stack::GetInstance()->GetAcl()->CancelClassicConnection(address);
}

bool bluetooth::shim::ACL_AcceptLeConnectionFrom(
    const tBLE_BD_ADDR& legacy_address_with_type, bool is_direct) {
  std::promise<bool> promise;
  auto future = promise.get_future();
  Stack::GetInstance()->GetAcl()->AcceptLeConnectionFrom(
      ToAddressWithTypeFromLegacy(legacy_address_with_type), is_direct,
      std::move(promise));
  return future.get();
}

void bluetooth::shim::ACL_IgnoreLeConnectionFrom(
    const tBLE_BD_ADDR& legacy_address_with_type) {
  Stack::GetInstance()->GetAcl()->IgnoreLeConnectionFrom(
      ToAddressWithTypeFromLegacy(legacy_address_with_type));
}

void bluetooth::shim::ACL_WriteData(uint16_t handle, BT_HDR* p_buf) {
  std::unique_ptr<bluetooth::packet::RawBuilder> packet = MakeUniquePacket(
      p_buf->data + p_buf->offset + HCI_DATA_PREAMBLE_SIZE,
      p_buf->len - HCI_DATA_PREAMBLE_SIZE, IsPacketFlushable(p_buf));
  Stack::GetInstance()->GetAcl()->WriteData(handle, std::move(packet));
  osi_free(p_buf);
}

void bluetooth::shim::ACL_ConfigureLePrivacy(bool is_le_privacy_enabled) {
  hci::LeAddressManager::AddressPolicy address_policy =
      is_le_privacy_enabled
          ? hci::LeAddressManager::AddressPolicy::USE_RESOLVABLE_ADDRESS
          : hci::LeAddressManager::AddressPolicy::USE_PUBLIC_ADDRESS;
  hci::AddressWithType empty_address_with_type(
      hci::Address{}, hci::AddressType::RANDOM_DEVICE_ADDRESS);

  /* Default to 7 minutes minimum, 15 minutes maximum for random address refreshing;
   * device can override. */
  auto minimum_rotation_time = std::chrono::minutes(
      GET_SYSPROP(Ble, random_address_rotation_interval_min, 7));
  auto maximum_rotation_time = std::chrono::minutes(
      GET_SYSPROP(Ble, random_address_rotation_interval_max, 15));

  Stack::GetInstance()
      ->GetStackManager()
      ->GetInstance<bluetooth::hci::AclManager>()
      ->SetPrivacyPolicyForInitiatorAddress(
          address_policy, empty_address_with_type, minimum_rotation_time,
          maximum_rotation_time);
}

void bluetooth::shim::ACL_Disconnect(uint16_t handle, bool is_classic,
                                     tHCI_STATUS reason, std::string comment) {
  (is_classic)
      ? Stack::GetInstance()->GetAcl()->DisconnectClassic(handle, reason,
                                                          comment)
      : Stack::GetInstance()->GetAcl()->DisconnectLe(handle, reason, comment);
}

void bluetooth::shim::ACL_Shutdown() {
  Stack::GetInstance()->GetAcl()->Shutdown();
}

void bluetooth::shim::ACL_IgnoreAllLeConnections() {
  return Stack::GetInstance()->GetAcl()->ClearFilterAcceptList();
}

void bluetooth::shim::ACL_ReadConnectionAddress(uint16_t handle,
                                                RawAddress& conn_addr,
                                                tBLE_ADDR_TYPE* p_addr_type,
                                                bool ota_address) {
  auto local_address =
      Stack::GetInstance()->GetAcl()->GetConnectionLocalAddress(handle,
                                                                ota_address);

  conn_addr = ToRawAddress(local_address.GetAddress());
  *p_addr_type = static_cast<tBLE_ADDR_TYPE>(local_address.GetAddressType());
}

void bluetooth::shim::ACL_ReadPeerConnectionAddress(uint16_t handle,
                                                    RawAddress& conn_addr,
                                                    tBLE_ADDR_TYPE* p_addr_type,
                                                    bool ota_address) {
  auto remote_ota_address =
      Stack::GetInstance()->GetAcl()->GetConnectionPeerAddress(handle,
                                                               ota_address);

  conn_addr = ToRawAddress(remote_ota_address.GetAddress());
  *p_addr_type =
      static_cast<tBLE_ADDR_TYPE>(remote_ota_address.GetAddressType());
}

std::optional<uint8_t> bluetooth::shim::ACL_GetAdvertisingSetConnectedTo(
    const RawAddress& addr) {
  return Stack::GetInstance()->GetAcl()->GetAdvertisingSetConnectedTo(addr);
}

void bluetooth::shim::ACL_AddToAddressResolution(
    const tBLE_BD_ADDR& legacy_address_with_type, const Octet16& peer_irk,
    const Octet16& local_irk) {
  Stack::GetInstance()->GetAcl()->AddToAddressResolution(
      ToAddressWithType(legacy_address_with_type.bda,
                        legacy_address_with_type.type),
      peer_irk, local_irk);
}

void bluetooth::shim::ACL_RemoveFromAddressResolution(
    const tBLE_BD_ADDR& legacy_address_with_type) {
  Stack::GetInstance()->GetAcl()->RemoveFromAddressResolution(ToAddressWithType(
      legacy_address_with_type.bda, legacy_address_with_type.type));
}

void bluetooth::shim::ACL_ClearAddressResolution() {
  Stack::GetInstance()->GetAcl()->ClearAddressResolution();
}

void bluetooth::shim::ACL_ClearFilterAcceptList() {
  Stack::GetInstance()->GetAcl()->ClearFilterAcceptList();
}
void bluetooth::shim::ACL_LeSetDefaultSubrate(uint16_t subrate_min,
                                              uint16_t subrate_max,
                                              uint16_t max_latency,
                                              uint16_t cont_num,
                                              uint16_t sup_tout) {
  Stack::GetInstance()->GetAcl()->LeSetDefaultSubrate(
      subrate_min, subrate_max, max_latency, cont_num, sup_tout);
}

void bluetooth::shim::ACL_LeSubrateRequest(
    uint16_t hci_handle, uint16_t subrate_min, uint16_t subrate_max,
    uint16_t max_latency, uint16_t cont_num, uint16_t sup_tout) {
  Stack::GetInstance()->GetAcl()->LeSubrateRequest(
      hci_handle, subrate_min, subrate_max, max_latency, cont_num, sup_tout);
}

void bluetooth::shim::ACL_RemoteNameRequest(const RawAddress& addr,
                                            uint8_t page_scan_rep_mode,
                                            uint8_t /* page_scan_mode */,
                                            uint16_t clock_offset) {
  bluetooth::shim::GetRemoteNameRequest()->StartRemoteNameRequest(
      ToGdAddress(addr),
      hci::RemoteNameRequestBuilder::Create(
          ToGdAddress(addr), hci::PageScanRepetitionMode(page_scan_rep_mode),
          clock_offset & (~BTM_CLOCK_OFFSET_VALID),
          (clock_offset & BTM_CLOCK_OFFSET_VALID)
              ? hci::ClockOffsetValid::VALID
              : hci::ClockOffsetValid::INVALID),
      GetGdShimHandler()->BindOnce([](hci::ErrorCode status) {
        if (status != hci::ErrorCode::SUCCESS) {
          do_in_main_thread(
              FROM_HERE,
              base::BindOnce(
                  [](hci::ErrorCode status) {
                    // NOTE: we intentionally don't supply the address, to match
                    // the legacy behavior.
                    // Callsites that want the address should use
                    // StartRemoteNameRequest() directly, rather than going
                    // through this shim.
                    btm_process_remote_name(nullptr, nullptr, 0,
                                            static_cast<tHCI_STATUS>(status));
                    btm_sec_rmt_name_request_complete(
                        nullptr, nullptr, static_cast<tHCI_STATUS>(status));
                  },
                  status));
        }
      }),
      GetGdShimHandler()->BindOnce(
          [](RawAddress addr, uint64_t features) {
            static_assert(sizeof(features) == 8);
            do_in_main_thread(
                FROM_HERE,
                base::BindOnce(btm_sec_rmt_host_support_feat_evt, addr,
                               static_cast<uint8_t>(features & 0xff)));
          },
          addr),
      GetGdShimHandler()->BindOnce(
          [](RawAddress addr, hci::ErrorCode status,
             std::array<uint8_t, 248> name) {
            do_in_main_thread(
                FROM_HERE,
                base::BindOnce(
                    [](RawAddress addr, hci::ErrorCode status,
                       std::array<uint8_t, 248> name) {
                      auto p = (uint8_t*)osi_malloc(name.size());
                      std::copy(name.begin(), name.end(), p);

                      btm_process_remote_name(&addr, p, name.size(),
                                              static_cast<tHCI_STATUS>(status));
                      btm_sec_rmt_name_request_complete(
                          &addr, p, static_cast<tHCI_STATUS>(status));
                    },
                    addr, status, name));
          },
          addr));
}

void bluetooth::shim::ACL_CancelRemoteNameRequest(const RawAddress& addr) {
  bluetooth::shim::GetRemoteNameRequest()->CancelRemoteNameRequest(
      ToGdAddress(addr));
}
