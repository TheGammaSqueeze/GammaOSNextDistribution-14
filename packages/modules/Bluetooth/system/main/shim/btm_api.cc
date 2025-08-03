/*
 * Copyright 2019 The Android Open Source Project
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

#define LOG_TAG "bt_shim_btm"

#include "main/shim/btm_api.h"

#include <base/functional/callback.h>
#include <base/logging.h>

#include "main/shim/btm.h"
#include "main/shim/controller.h"
#include "main/shim/helpers.h"
#include "main/shim/stack.h"
#include "stack/btm/btm_ble_sec.h"
#include "stack/btm/btm_dev.h"
#include "types/raw_address.h"

uint16_t bluetooth::shim::BTM_GetHCIConnHandle(const RawAddress& remote_bda,
                                               tBT_TRANSPORT transport) {
  return Stack::GetInstance()->GetBtm()->GetAclHandle(remote_bda, transport);
}

tBTM_STATUS bluetooth::shim::BTM_ClearEventFilter() {
  controller_get_interface()->clear_event_filter();
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_ClearEventMask() {
  controller_get_interface()->clear_event_mask();
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_ClearFilterAcceptList() {
  Stack::GetInstance()->GetAcl()->ClearFilterAcceptList();
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_DisconnectAllAcls() {
  Stack::GetInstance()->GetAcl()->DisconnectAllForSuspend();
//  Stack::GetInstance()->GetAcl()->Shutdown();
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_LeRand(LeRandCallback cb) {
  Stack::GetInstance()->GetAcl()->LeRand(std::move(cb));
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_SetEventFilterConnectionSetupAllDevices() {
  // Autoplumbed
  controller_get_interface()->set_event_filter_connection_setup_all_devices();
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_AllowWakeByHid(
    std::vector<RawAddress> classic_hid_devices,
    std::vector<std::pair<RawAddress, uint8_t>> le_hid_devices) {
  // First set ACL to suspended state.
  Stack::GetInstance()->GetAcl()->SetSystemSuspendState(/*suspended=*/true);

  // Allow classic HID wake.
  controller_get_interface()->set_event_filter_allow_device_connection(
      std::move(classic_hid_devices));

  // Allow BLE HID
  for (auto hid_address : le_hid_devices) {
    std::promise<bool> accept_promise;
    auto accept_future = accept_promise.get_future();

    tBLE_BD_ADDR bdadr = BTM_Sec_GetAddressWithType(hid_address.first);
    Stack::GetInstance()->GetAcl()->AcceptLeConnectionFrom(
        ToAddressWithType(bdadr.bda, bdadr.type),
        /*is_direct=*/false, std::move(accept_promise));

    accept_future.wait();
  }

  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_RestoreFilterAcceptList(
    std::vector<std::pair<RawAddress, uint8_t>> le_devices) {
  // First, mark ACL as no longer suspended.
  Stack::GetInstance()->GetAcl()->SetSystemSuspendState(/*suspended=*/false);

  // Next, Allow BLE connection from all devices that need to be restored.
  // This will also re-arm the LE connection.
  for (auto address_pair : le_devices) {
    std::promise<bool> accept_promise;
    auto accept_future = accept_promise.get_future();

    tBLE_BD_ADDR bdadr = BTM_Sec_GetAddressWithType(address_pair.first);
    Stack::GetInstance()->GetAcl()->AcceptLeConnectionFrom(
        ToAddressWithType(bdadr.bda, bdadr.type),
        /*is_direct=*/false, std::move(accept_promise));

    accept_future.wait();
  }

  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_SetDefaultEventMaskExcept(uint64_t mask,
                                                           uint64_t le_mask) {
  // Autoplumbed
  controller_get_interface()->set_default_event_mask_except(mask, le_mask);
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_SetEventFilterInquiryResultAllDevices() {
  // Autoplumbed
  controller_get_interface()->set_event_filter_inquiry_result_all_devices();
  return BTM_SUCCESS;
}

tBTM_STATUS bluetooth::shim::BTM_BleResetId() {
  btm_ble_reset_id();
  return BTM_SUCCESS;
}

size_t bluetooth::shim::BTM_BleGetNumberOfAdvertisingInstancesInUse(void) {
  return Stack::GetInstance()->GetBtm()->GetNumberOfAdvertisingInstancesInUse();
}
