/******************************************************************************
 *
 *  Copyright 2000-2012 Broadcom Corporation
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

/*****************************************************************************
 *
 *  Name:          btm_acl.cc
 *
 *  Description:   This file contains functions that handle ACL connections.
 *                 This includes operations such as hold and sniff modes,
 *                 supported packet types.
 *
 *                 This module contains both internal and external (API)
 *                 functions. External (API) functions are distinguishable
 *                 by their names beginning with uppercase BTM.
 *
 *
 *****************************************************************************/

#include "main/shim/entry.h"
#define LOG_TAG "btm_acl"

#include <bluetooth/log.h>

#include <cstdint>

#include "bta/include/bta_dm_acl.h"
#include "bta/sys/bta_sys.h"
#include "common/init_flags.h"
#include "common/metrics.h"
#include "device/include/controller.h"
#include "device/include/device_iot_config.h"
#include "device/include/interop.h"
#include "hci/controller_interface.h"
#include "include/check.h"
#include "include/l2cap_hci_link_interface.h"
#include "internal_include/bt_target.h"
#include "main/shim/acl_api.h"
#include "main/shim/controller.h"
#include "main/shim/dumpsys.h"
#include "os/log.h"
#include "os/parameter_provider.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"  // UNUSED_ATTR
#include "osi/include/properties.h"
#include "osi/include/stack_power_telemetry.h"
#include "rust/src/connection/ffi/connection_shim.h"
#include "rust/src/core/ffi/types.h"
#include "stack/acl/acl.h"
#include "stack/acl/peer_packet_types.h"
#include "stack/btm/btm_ble_sec.h"
#include "stack/btm/btm_dev.h"
#include "stack/btm/btm_int_types.h"
#include "stack/btm/btm_sco.h"
#include "stack/btm/btm_sec.h"
#include "stack/btm/security_device_record.h"
#include "stack/include/acl_api.h"
#include "stack/include/acl_api_types.h"
#include "stack/include/acl_hci_link_interface.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_types.h"
#include "stack/include/btm_api.h"
#include "stack/include/btm_ble_api.h"
#include "stack/include/btm_iso_api.h"
#include "stack/include/hci_error_code.h"
#include "stack/include/hcimsgs.h"
#include "stack/include/l2cap_acl_interface.h"
#include "stack/include/l2cdefs.h"
#include "stack/include/main_thread.h"
#include "types/hci_role.h"
#include "types/raw_address.h"

#ifndef PROPERTY_LINK_SUPERVISION_TIMEOUT
#define PROPERTY_LINK_SUPERVISION_TIMEOUT \
  "bluetooth.core.acl.link_supervision_timeout"
#endif

using namespace bluetooth;
using bluetooth::legacy::hci::GetInterface;

void BTM_update_version_info(const RawAddress& bd_addr,
                             const remote_version_info& remote_version_info);

static void find_in_device_record(const RawAddress& bd_addr,
                                  tBLE_BD_ADDR* address_with_type);
void l2c_link_hci_conn_comp(tHCI_STATUS status, uint16_t handle,
                            const RawAddress& p_bda);

void BTM_db_reset(void);

extern tBTM_CB btm_cb;
void btm_iot_save_remote_properties(tACL_CONN* p_acl_cb);
void btm_iot_save_remote_versions(tACL_CONN* p_acl_cb);

struct StackAclBtmAcl {
  tACL_CONN* acl_allocate_connection();
  tACL_CONN* acl_get_connection_from_handle(uint16_t handle);
  tACL_CONN* btm_bda_to_acl(const RawAddress& bda, tBT_TRANSPORT transport);
  bool change_connection_packet_types(tACL_CONN& link,
                                      const uint16_t new_packet_type_bitmask);
  void btm_establish_continue(tACL_CONN* p_acl_cb);
  void btm_set_default_link_policy(tLINK_POLICY settings);
  void btm_acl_role_changed(tHCI_STATUS hci_status, const RawAddress& bd_addr,
                            tHCI_ROLE new_role);
  void hci_start_role_switch_to_central(tACL_CONN& p_acl);
  void set_default_packet_types_supported(uint16_t packet_types_supported) {
    btm_cb.acl_cb_.btm_acl_pkt_types_supported = packet_types_supported;
  }
  void btm_acl_consolidate(const RawAddress& identity_addr,
                           const RawAddress& rpa);
};

struct RoleChangeView {
  tHCI_ROLE new_role = HCI_ROLE_UNKNOWN;
  RawAddress bd_addr;
};

namespace {
StackAclBtmAcl internal_;
std::unique_ptr<RoleChangeView> delayed_role_change_ = nullptr;
}

typedef struct {
  uint16_t handle;
  uint16_t hci_len;
} __attribute__((packed)) acl_header_t;

constexpr uint8_t BTM_MAX_SW_ROLE_FAILED_ATTEMPTS = 3;

/* Define masks for supported and exception 2.0 ACL packet types
 */
constexpr uint16_t BTM_ACL_SUPPORTED_PKTS_MASK =
    (HCI_PKT_TYPES_MASK_DM1 | HCI_PKT_TYPES_MASK_DH1 | HCI_PKT_TYPES_MASK_DM3 |
     HCI_PKT_TYPES_MASK_DH3 | HCI_PKT_TYPES_MASK_DM5 | HCI_PKT_TYPES_MASK_DH5);

constexpr uint16_t BTM_ACL_EXCEPTION_PKTS_MASK =
    (HCI_PKT_TYPES_MASK_NO_2_DH1 | HCI_PKT_TYPES_MASK_NO_3_DH1 |
     HCI_PKT_TYPES_MASK_NO_2_DH3 | HCI_PKT_TYPES_MASK_NO_3_DH3 |
     HCI_PKT_TYPES_MASK_NO_2_DH5 | HCI_PKT_TYPES_MASK_NO_3_DH5);

static bool IsEprAvailable(const tACL_CONN& p_acl) {
  if (!p_acl.peer_lmp_feature_valid[0]) {
    log::warn("Checking incomplete feature page read");
    return false;
  }
  return HCI_ATOMIC_ENCRYPT_SUPPORTED(p_acl.peer_lmp_feature_pages[0]) &&
         bluetooth::shim::GetController()->SupportsEncryptionPause();
}

static void btm_process_remote_ext_features(tACL_CONN* p_acl_cb,
                                            uint8_t max_page_number);
static void btm_read_failed_contact_counter_timeout(void* data);
static void btm_read_remote_ext_features(uint16_t handle, uint8_t page_number);
static void btm_read_rssi_timeout(void* data);
static void btm_read_tx_power_timeout(void* data);
static void check_link_policy(tLINK_POLICY* settings);
void btm_set_link_policy(tACL_CONN* conn, tLINK_POLICY policy);

namespace {
void NotifyAclLinkUp(tACL_CONN& p_acl) {
  if (p_acl.link_up_issued) {
    log::info("Already notified BTA layer that the link is up");
    return;
  }
  p_acl.link_up_issued = true;
  BTA_dm_acl_up(p_acl.remote_addr, p_acl.transport, p_acl.hci_handle);
}

void NotifyAclLinkDown(tACL_CONN& p_acl) {
  /* Only notify if link up has had a chance to be issued */
  if (p_acl.link_up_issued) {
    p_acl.link_up_issued = false;
    BTA_dm_acl_down(p_acl.remote_addr, p_acl.transport);
  }
}

void NotifyAclRoleSwitchComplete(const RawAddress& bda, tHCI_ROLE new_role,
                                 tHCI_STATUS hci_status) {
  BTA_dm_report_role_change(bda, new_role, hci_status);
}

void NotifyAclFeaturesReadComplete(tACL_CONN& p_acl,
                                   UNUSED_ATTR uint8_t max_page_number) {
  btm_process_remote_ext_features(&p_acl, max_page_number);
  btm_set_link_policy(&p_acl, btm_cb.acl_cb_.DefaultLinkPolicy());
  BTA_dm_notify_remote_features_complete(p_acl.remote_addr);
}

}  // namespace

static void disconnect_acl(tACL_CONN& p_acl, tHCI_STATUS reason,
                           std::string comment) {
  log::info("Disconnecting peer:{} reason:{} comment:{}",
            ADDRESS_TO_LOGGABLE_CSTR(p_acl.remote_addr),
            hci_error_code_text(reason).c_str(), comment.c_str());
  p_acl.disconnect_reason = reason;

  return bluetooth::shim::ACL_Disconnect(
      p_acl.hci_handle, p_acl.is_transport_br_edr(), reason, comment);
}

void StackAclBtmAcl::hci_start_role_switch_to_central(tACL_CONN& p_acl) {
  GetInterface().StartRoleSwitch(p_acl.remote_addr,
                                 static_cast<uint8_t>(HCI_ROLE_CENTRAL));
  p_acl.set_switch_role_in_progress();
  p_acl.rs_disc_pending = BTM_SEC_RS_PENDING;
}

void hci_btm_set_link_supervision_timeout(tACL_CONN& link, uint16_t timeout) {
  if (link.link_role != HCI_ROLE_CENTRAL) {
    /* Only send if current role is Central; 2.0 spec requires this */
    log::warn("Can only set link supervision timeout if central role:{}",
              RoleText(link.link_role).c_str());
    return;
  }

  if (!bluetooth::shim::
          controller_is_write_link_supervision_timeout_supported()) {
    log::warn(
        "UNSUPPORTED by controller write link supervision timeout:{:.2f}ms "
        "bd_addr:{}",
        supervision_timeout_to_seconds(timeout),
        ADDRESS_TO_LOGGABLE_CSTR(link.RemoteAddress()));
    return;
  }
  log::debug("Setting link supervision timeout:{:.2f}s peer:{}",
             double(timeout) * 0.01,
             ADDRESS_TO_LOGGABLE_CSTR(link.RemoteAddress()));
  link.link_super_tout = timeout;
  btsnd_hcic_write_link_super_tout(link.Handle(), timeout);
}

/* 3 seconds timeout waiting for responses */
#define BTM_DEV_REPLY_TIMEOUT_MS (3 * 1000)

void BTM_acl_after_controller_started(const controller_t* controller) {
  internal_.btm_set_default_link_policy(
      HCI_ENABLE_CENTRAL_PERIPHERAL_SWITCH | HCI_ENABLE_HOLD_MODE |
      HCI_ENABLE_SNIFF_MODE | HCI_ENABLE_PARK_MODE);

  /* Create ACL supported packet types mask */
  uint16_t btm_acl_pkt_types_supported =
      (HCI_PKT_TYPES_MASK_DH1 + HCI_PKT_TYPES_MASK_DM1);

  if (bluetooth::shim::GetController()->Supports3SlotPackets())
    btm_acl_pkt_types_supported |=
        (HCI_PKT_TYPES_MASK_DH3 + HCI_PKT_TYPES_MASK_DM3);

  if (bluetooth::shim::GetController()->Supports5SlotPackets())
    btm_acl_pkt_types_supported |=
        (HCI_PKT_TYPES_MASK_DH5 + HCI_PKT_TYPES_MASK_DM5);

  /* Add in EDR related ACL types */
  if (!bluetooth::shim::GetController()->SupportsClassic2mPhy()) {
    btm_acl_pkt_types_supported |=
        (HCI_PKT_TYPES_MASK_NO_2_DH1 + HCI_PKT_TYPES_MASK_NO_2_DH3 +
         HCI_PKT_TYPES_MASK_NO_2_DH5);
  }

  if (!bluetooth::shim::GetController()->SupportsClassic3mPhy()) {
    btm_acl_pkt_types_supported |=
        (HCI_PKT_TYPES_MASK_NO_3_DH1 + HCI_PKT_TYPES_MASK_NO_3_DH3 +
         HCI_PKT_TYPES_MASK_NO_3_DH5);
  }

  /* Check to see if 3 and 5 slot packets are available */
  if (bluetooth::shim::GetController()->SupportsClassic2mPhy() ||
      bluetooth::shim::GetController()->SupportsClassic3mPhy()) {
    if (!bluetooth::shim::GetController()->Supports3SlotEdrPackets())
      btm_acl_pkt_types_supported |=
          (HCI_PKT_TYPES_MASK_NO_2_DH3 + HCI_PKT_TYPES_MASK_NO_3_DH3);

    if (!bluetooth::shim::GetController()->Supports5SlotEdrPackets())
      btm_acl_pkt_types_supported |=
          (HCI_PKT_TYPES_MASK_NO_2_DH5 + HCI_PKT_TYPES_MASK_NO_3_DH5);
  }
  internal_.set_default_packet_types_supported(btm_acl_pkt_types_supported);
}

/*******************************************************************************
 *
 * Function        btm_bda_to_acl
 *
 * Description     This function returns the FIRST acl_db entry for the passed
 *                 BDA.
 *
 * Parameters      bda : BD address of the remote device
 *                 transport : Physical transport used for ACL connection
 *                 (BR/EDR or LE)
 *
 * Returns         Returns pointer to the ACL DB for the requested BDA if found.
 *                 nullptr if not found.
 *
 ******************************************************************************/
tACL_CONN* StackAclBtmAcl::btm_bda_to_acl(const RawAddress& bda,
                                          tBT_TRANSPORT transport) {
  tACL_CONN* p_acl = &btm_cb.acl_cb_.acl_db[0];
  for (uint8_t index = 0; index < MAX_L2CAP_LINKS; index++, p_acl++) {
    if ((p_acl->in_use) && p_acl->remote_addr == bda &&
        p_acl->transport == transport) {
      return p_acl;
    }
  }
  return nullptr;
}

tACL_CONN* acl_get_connection_from_address(const RawAddress& bd_addr,
                                           tBT_TRANSPORT transport) {
  return internal_.btm_bda_to_acl(bd_addr, transport);
}

void StackAclBtmAcl::btm_acl_consolidate(const RawAddress& identity_addr,
                                         const RawAddress& rpa) {
  tACL_CONN* p_acl = &btm_cb.acl_cb_.acl_db[0];
  for (uint8_t index = 0; index < MAX_L2CAP_LINKS; index++, p_acl++) {
    if (!p_acl->in_use) continue;

    if (p_acl->remote_addr == rpa) {
      log::info("consolidate {} -> {}", ADDRESS_TO_LOGGABLE_CSTR(rpa),
                ADDRESS_TO_LOGGABLE_CSTR(identity_addr));
      p_acl->remote_addr = identity_addr;
      return;
    }
  }
}

void btm_acl_consolidate(const RawAddress& identity_addr,
                         const RawAddress& rpa) {
  return internal_.btm_acl_consolidate(identity_addr, rpa);
}

/*******************************************************************************
 *
 * Function         btm_handle_to_acl_index
 *
 * Description      This function returns the FIRST acl_db entry for the passed
 *                  hci_handle.
 *
 * Returns          index to the acl_db or MAX_L2CAP_LINKS.
 *
 ******************************************************************************/
uint8_t btm_handle_to_acl_index(uint16_t hci_handle) {
  tACL_CONN* p = &btm_cb.acl_cb_.acl_db[0];
  uint8_t xx;
  for (xx = 0; xx < MAX_L2CAP_LINKS; xx++, p++) {
    if ((p->in_use) && (p->hci_handle == hci_handle)) {
      break;
    }
  }

  /* If here, no BD Addr found */
  return (xx);
}

tACL_CONN* StackAclBtmAcl::acl_get_connection_from_handle(uint16_t hci_handle) {
  uint8_t index = btm_handle_to_acl_index(hci_handle);
  if (index >= MAX_L2CAP_LINKS) return nullptr;
  return &btm_cb.acl_cb_.acl_db[index];
}

tACL_CONN* acl_get_connection_from_handle(uint16_t handle) {
  return internal_.acl_get_connection_from_handle(handle);
}

void btm_acl_process_sca_cmpl_pkt(uint8_t len, uint8_t* data) {
  uint16_t handle;
  uint8_t sca;
  uint8_t status;

  if (len < 4) {
    log::warn("Malformatted packet, not containing enough data");
    return;
  }

  STREAM_TO_UINT8(status, data);

  if (status != HCI_SUCCESS) {
    log::warn("Peer SCA Command complete failed:{}",
              hci_error_code_text(static_cast<tHCI_STATUS>(status)).c_str());
    return;
  }

  STREAM_TO_UINT16(handle, data);
  STREAM_TO_UINT8(sca, data);

  tACL_CONN* p_acl = internal_.acl_get_connection_from_handle(handle);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return;
  }
  p_acl->sca = sca;
}

tACL_CONN* StackAclBtmAcl::acl_allocate_connection() {
  tACL_CONN* p_acl = &btm_cb.acl_cb_.acl_db[0];
  for (uint8_t xx = 0; xx < MAX_L2CAP_LINKS; xx++, p_acl++) {
    if (!p_acl->in_use) {
      return p_acl;
    }
  }
  return nullptr;
}

void btm_acl_created(const RawAddress& bda, uint16_t hci_handle,
                     tHCI_ROLE link_role, tBT_TRANSPORT transport) {
  tACL_CONN* p_acl = internal_.btm_bda_to_acl(bda, transport);
  if (p_acl != (tACL_CONN*)NULL) {
    p_acl->hci_handle = hci_handle;
    p_acl->link_role = link_role;
    p_acl->transport = transport;
    if (transport == BT_TRANSPORT_BR_EDR) {
      btm_set_link_policy(p_acl, btm_cb.acl_cb_.DefaultLinkPolicy());
    }
    log::warn(
        "Unable to create duplicate acl when one already exists handle:{} "
        "role:{} transport:{}",
        hci_handle, RoleText(link_role).c_str(),
        bt_transport_text(transport).c_str());
    return;
  }

  p_acl = internal_.acl_allocate_connection();
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return;
  }

  p_acl->in_use = true;
  p_acl->hci_handle = hci_handle;
  p_acl->link_role = link_role;
  p_acl->link_up_issued = false;
  p_acl->remote_addr = bda;
  p_acl->sca = 0xFF;
  p_acl->transport = transport;
  p_acl->switch_role_failed_attempts = 0;
  p_acl->reset_switch_role();

  log::debug(
      "Created new ACL connection peer:{} role:{} handle:0x{:04x} transport:{}",
      ADDRESS_TO_LOGGABLE_CSTR(bda), RoleText(p_acl->link_role).c_str(),
      hci_handle, bt_transport_text(transport).c_str());

  if (p_acl->is_transport_br_edr()) {
    BTM_PM_OnConnected(hci_handle, bda);
    btm_set_link_policy(p_acl, btm_cb.acl_cb_.DefaultLinkPolicy());
  }

  // save remote properties to iot conf file
  btm_iot_save_remote_properties(p_acl);

  /* if BR/EDR do something more */
  if (transport == BT_TRANSPORT_BR_EDR) {
    btsnd_hcic_read_rmt_clk_offset(hci_handle);
  }

  if (transport == BT_TRANSPORT_LE) {
    btm_ble_get_acl_remote_addr(hci_handle, p_acl->active_remote_addr,
                                &p_acl->active_remote_addr_type);

    if (controller_get_interface()
            ->SupportsBlePeripheralInitiatedFeaturesExchange() ||
        link_role == HCI_ROLE_CENTRAL) {
      btsnd_hcic_ble_read_remote_feat(p_acl->hci_handle);
    } else {
      internal_.btm_establish_continue(p_acl);
    }
  }
}

void btm_acl_create_failed(const RawAddress& bda, tBT_TRANSPORT transport,
                           tHCI_STATUS hci_status) {
  BTA_dm_acl_up_failed(bda, transport, hci_status);
}

/*******************************************************************************
 *
 * Function         btm_acl_removed
 *
 * Description      This function is called by L2CAP when an ACL connection
 *                  is removed. Since only L2CAP creates ACL links, we use
 *                  the L2CAP link index as our index into the control blocks.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_acl_removed(uint16_t handle) {
  tACL_CONN* p_acl = internal_.acl_get_connection_from_handle(handle);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return;
  }
  p_acl->in_use = false;
  NotifyAclLinkDown(*p_acl);
  if (p_acl->is_transport_br_edr()) {
    BTM_PM_OnDisconnected(handle);
  }
  p_acl->Reset();
}

/*******************************************************************************
 *
 * Function         btm_acl_device_down
 *
 * Description      This function is called when the local device is deemed
 *                  to be down. It notifies L2CAP of the failure.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_acl_device_down(void) {
  tACL_CONN* p = &btm_cb.acl_cb_.acl_db[0];
  uint16_t xx;
  for (xx = 0; xx < MAX_L2CAP_LINKS; xx++, p++) {
    if (p->in_use) {
      l2c_link_hci_disc_comp(p->hci_handle, HCI_ERR_HW_FAILURE);
    }
  }
  BTM_db_reset();
}

tBTM_STATUS BTM_GetRole(const RawAddress& remote_bd_addr, tHCI_ROLE* p_role) {
  if (p_role == nullptr) {
    return BTM_ILLEGAL_VALUE;
  }
  *p_role = HCI_ROLE_UNKNOWN;

  tACL_CONN* p_acl =
      internal_.btm_bda_to_acl(remote_bd_addr, BT_TRANSPORT_BR_EDR);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return BTM_UNKNOWN_ADDR;
  }
  *p_role = p_acl->link_role;
  return BTM_SUCCESS;
}

/*******************************************************************************
 *
 * Function         BTM_SwitchRoleToCentral
 *
 * Description      This function is called to switch role between central and
 *                  peripheral.  If role is already set it will do nothing.
 *
 * Returns          BTM_SUCCESS if already in specified role.
 *                  BTM_CMD_STARTED if command issued to controller.
 *                  BTM_NO_RESOURCES if couldn't allocate memory to issue
 *                                   command
 *                  BTM_UNKNOWN_ADDR if no active link with bd addr specified
 *                  BTM_MODE_UNSUPPORTED if local device does not support role
 *                                       switching
 *                  BTM_BUSY if the previous command is not completed
 *
 ******************************************************************************/
tBTM_STATUS BTM_SwitchRoleToCentral(const RawAddress& remote_bd_addr) {
  if (!bluetooth::shim::GetController()->SupportsRoleSwitch()) {
    log::info("Local controller does not support role switching");
    return BTM_MODE_UNSUPPORTED;
  }

  tACL_CONN* p_acl =
      internal_.btm_bda_to_acl(remote_bd_addr, BT_TRANSPORT_BR_EDR);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return BTM_UNKNOWN_ADDR;
  }

  if (p_acl->link_role == HCI_ROLE_CENTRAL) {
    log::info("Requested role is already in effect");
    return BTM_SUCCESS;
  }

  if (interop_match_addr(INTEROP_DISABLE_ROLE_SWITCH, &remote_bd_addr)) {
    log::info("Remote device is on list preventing role switch");
    return BTM_DEV_RESTRICT_LISTED;
  }

  if (BTM_IsScoActiveByBdaddr(remote_bd_addr)) {
    log::info("An active SCO to device prevents role switch at this time");
    return BTM_NO_RESOURCES;
  }

  if (!p_acl->is_switch_role_idle()) {
    log::info("Role switch is already progress");
    return BTM_BUSY;
  }

  if (interop_match_addr(INTEROP_DYNAMIC_ROLE_SWITCH, &remote_bd_addr)) {
    log::debug("Device restrict listed under INTEROP_DYNAMIC_ROLE_SWITCH");
    return BTM_DEV_RESTRICT_LISTED;
  }

  tBTM_PM_MODE pwr_mode;
  if (!BTM_ReadPowerMode(p_acl->remote_addr, &pwr_mode)) {
    log::warn(
        "Unable to find device to read current power mode prior to role "
        "switch");
    return BTM_UNKNOWN_ADDR;
  };

  if (pwr_mode == BTM_PM_MD_PARK || pwr_mode == BTM_PM_MD_SNIFF) {
    if (!BTM_SetLinkPolicyActiveMode(p_acl->remote_addr)) {
      log::warn("Unable to set link policy active before attempting switch");
      return BTM_WRONG_MODE;
    }
    p_acl->set_switch_role_changing();
  }
  /* some devices do not support switch while encryption is on */
  else {
    if (p_acl->is_encrypted && !IsEprAvailable(*p_acl)) {
      /* bypass turning off encryption if change link key is already doing it */
      p_acl->set_encryption_off();
      p_acl->set_switch_role_encryption_off();
    } else {
      internal_.hci_start_role_switch_to_central(*p_acl);
    }
  }

  return BTM_CMD_STARTED;
}

/*******************************************************************************
 *
 * Function         btm_acl_encrypt_change
 *
 * Description      This function is when encryption of the connection is
 *                  completed by the LM.  Checks to see if a role switch or
 *                  change of link key was active and initiates or continues
 *                  process if needed.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_acl_encrypt_change(uint16_t handle, uint8_t status,
                            uint8_t encr_enable) {
  tACL_CONN* p = internal_.acl_get_connection_from_handle(handle);
  if (p == nullptr) {
    log::warn("Unable to find active acl");
    return;
  }

  /* Common Criteria mode only: if we are trying to drop encryption on an
   * encrypted connection, drop the connection */
  if (bluetooth::os::ParameterProvider::IsCommonCriteriaMode()) {
    if (p->is_encrypted && !encr_enable) {
      log::error(
          "attempting to decrypt encrypted connection, disconnecting. "
          "handle: {}",
          loghex(handle));

      acl_disconnect_from_handle(handle, HCI_ERR_HOST_REJECT_SECURITY,
                                 "stack::btu::btu_hcif::read_drop_encryption "
                                 "Connection Already Encrypted");
      return;
    }
  }

  p->is_encrypted = encr_enable;

  /* Process Role Switch if active */
  if (p->is_switch_role_encryption_off()) {
    /* if encryption turn off failed we still will try to switch role */
    if (encr_enable) {
      p->set_encryption_idle();
      p->reset_switch_role();
    } else {
      p->set_encryption_switching();
      p->set_switch_role_switching();
    }
    internal_.hci_start_role_switch_to_central(*p);
  }
  /* Finished enabling Encryption after role switch */
  else if (p->is_switch_role_encryption_on()) {
    p->reset_switch_role();
    p->set_encryption_idle();
    NotifyAclRoleSwitchComplete(
        btm_cb.acl_cb_.switch_role_ref_data.remote_bd_addr,
        btm_cb.acl_cb_.switch_role_ref_data.role,
        btm_cb.acl_cb_.switch_role_ref_data.hci_status);

    /* If a disconnect is pending, issue it now that role switch has completed
     */
    if (p->rs_disc_pending == BTM_SEC_DISC_PENDING) {
      disconnect_acl(*p, HCI_ERR_PEER_USER,
                     "stack::acl::btm_acl::encrypt after role switch");
    }
    p->rs_disc_pending = BTM_SEC_RS_NOT_PENDING; /* reset flag */
  }
}

static void check_link_policy(tLINK_POLICY* settings) {
  if ((*settings & HCI_ENABLE_CENTRAL_PERIPHERAL_SWITCH) &&
      (!bluetooth::shim::GetController()->SupportsRoleSwitch())) {
    *settings &= (~HCI_ENABLE_CENTRAL_PERIPHERAL_SWITCH);
    log::info("Role switch not supported (settings: 0x{:04x})", *settings);
  }
  if ((*settings & HCI_ENABLE_HOLD_MODE) &&
      (!bluetooth::shim::GetController()->SupportsHoldMode())) {
    *settings &= (~HCI_ENABLE_HOLD_MODE);
    log::info("hold not supported (settings: 0x{:04x})", *settings);
  }
  if ((*settings & HCI_ENABLE_SNIFF_MODE) &&
      (!bluetooth::shim::GetController()->SupportsSniffMode())) {
    *settings &= (~HCI_ENABLE_SNIFF_MODE);
    log::info("sniff not supported (settings: 0x{:04x})", *settings);
  }
  if ((*settings & HCI_ENABLE_PARK_MODE) &&
      (!bluetooth::shim::GetController()->SupportsParkMode())) {
    *settings &= (~HCI_ENABLE_PARK_MODE);
    log::info("park not supported (settings: 0x{:04x})", *settings);
  }
}

void btm_set_link_policy(tACL_CONN* conn, tLINK_POLICY policy) {
  conn->link_policy = policy;
  check_link_policy(&conn->link_policy);
  if ((conn->link_policy & HCI_ENABLE_CENTRAL_PERIPHERAL_SWITCH) &&
      interop_match_addr(INTEROP_DISABLE_SNIFF, &(conn->remote_addr))) {
    conn->link_policy &= (~HCI_ENABLE_SNIFF_MODE);
  }
  btsnd_hcic_write_policy_set(conn->hci_handle,
                              static_cast<uint16_t>(conn->link_policy));
}

static void btm_toggle_policy_on_for(const RawAddress& peer_addr,
                                     uint16_t flag) {
  auto conn = internal_.btm_bda_to_acl(peer_addr, BT_TRANSPORT_BR_EDR);
  if (!conn) {
    log::warn("Unable to find active acl");
    return;
  }
  btm_set_link_policy(conn, conn->link_policy | flag);
}

static void btm_toggle_policy_off_for(const RawAddress& peer_addr,
                                      uint16_t flag) {
  auto conn = internal_.btm_bda_to_acl(peer_addr, BT_TRANSPORT_BR_EDR);
  if (!conn) {
    log::warn("Unable to find active acl");
    return;
  }
  btm_set_link_policy(conn, conn->link_policy & ~flag);
}

bool BTM_is_sniff_allowed_for(const RawAddress& peer_addr) {
  auto conn = internal_.btm_bda_to_acl(peer_addr, BT_TRANSPORT_BR_EDR);
  if (!conn) {
    log::warn("Unable to find active acl");
    return false;
  }
  return conn->link_policy & HCI_ENABLE_SNIFF_MODE;
}

void BTM_unblock_sniff_mode_for(const RawAddress& peer_addr) {
  btm_toggle_policy_on_for(peer_addr, HCI_ENABLE_SNIFF_MODE);
}

void BTM_block_sniff_mode_for(const RawAddress& peer_addr) {
  btm_toggle_policy_off_for(peer_addr, HCI_ENABLE_SNIFF_MODE);
}

void BTM_unblock_role_switch_for(const RawAddress& peer_addr) {
  btm_toggle_policy_on_for(peer_addr, HCI_ENABLE_CENTRAL_PERIPHERAL_SWITCH);
}

void BTM_block_role_switch_for(const RawAddress& peer_addr) {
  btm_toggle_policy_off_for(peer_addr, HCI_ENABLE_CENTRAL_PERIPHERAL_SWITCH);
}

void BTM_unblock_role_switch_and_sniff_mode_for(const RawAddress& peer_addr) {
  btm_toggle_policy_on_for(
      peer_addr, HCI_ENABLE_SNIFF_MODE | HCI_ENABLE_CENTRAL_PERIPHERAL_SWITCH);
}

void BTM_block_role_switch_and_sniff_mode_for(const RawAddress& peer_addr) {
  btm_toggle_policy_off_for(
      peer_addr, HCI_ENABLE_SNIFF_MODE | HCI_ENABLE_CENTRAL_PERIPHERAL_SWITCH);
}

void StackAclBtmAcl::btm_set_default_link_policy(tLINK_POLICY settings) {
  check_link_policy(&settings);
  btm_cb.acl_cb_.btm_def_link_policy = settings;
  btsnd_hcic_write_def_policy_set(settings);
}

void BTM_default_unblock_role_switch() {
  internal_.btm_set_default_link_policy(btm_cb.acl_cb_.DefaultLinkPolicy() |
                                        HCI_ENABLE_CENTRAL_PERIPHERAL_SWITCH);
}

void BTM_default_block_role_switch() {
  internal_.btm_set_default_link_policy(btm_cb.acl_cb_.DefaultLinkPolicy() &
                                        ~HCI_ENABLE_CENTRAL_PERIPHERAL_SWITCH);
}

extern void bta_gattc_continue_discovery_if_needed(const RawAddress& bd_addr,
                                                   uint16_t acl_handle);

/*******************************************************************************
 *
 * Function         btm_read_remote_version_complete
 *
 * Description      This function is called when the command complete message
 *                  is received from the HCI for the remote version info.
 *
 * Returns          void
 *
 ******************************************************************************/
static void maybe_chain_more_commands_after_read_remote_version_complete(
    uint8_t status, uint16_t handle) {
  tACL_CONN* p_acl_cb = internal_.acl_get_connection_from_handle(handle);
  if (p_acl_cb == nullptr) {
    log::warn("Received remote version complete for unknown device");
    return;
  }

  switch (p_acl_cb->transport) {
    case BT_TRANSPORT_LE:
      l2cble_notify_le_connection(p_acl_cb->remote_addr);
      l2cble_use_preferred_conn_params(p_acl_cb->remote_addr);
      bta_gattc_continue_discovery_if_needed(p_acl_cb->remote_addr,
                                             p_acl_cb->Handle());
      break;
    case BT_TRANSPORT_BR_EDR:
      /**
       * When running legacy stack continue chain of executing various
       * read commands.  Skip when gd_acl is enabled because that
       * module handles all remote read functionality.
       */
      break;
    default:
      log::error("Unable to determine transport:{} device:{}",
                 bt_transport_text(p_acl_cb->transport).c_str(),
                 ADDRESS_TO_LOGGABLE_CSTR(p_acl_cb->remote_addr));
  }

  // save remote versions to iot conf file
  btm_iot_save_remote_versions(p_acl_cb);
}

void btm_process_remote_version_complete(uint8_t status, uint16_t handle,
                                         uint8_t lmp_version,
                                         uint16_t manufacturer,
                                         uint16_t lmp_subversion) {
  tACL_CONN* p_acl_cb = internal_.acl_get_connection_from_handle(handle);
  if (p_acl_cb == nullptr) {
    log::warn("Received remote version complete for unknown acl");
    return;
  }
  p_acl_cb->remote_version_received = true;

  if (status == HCI_SUCCESS) {
    p_acl_cb->remote_version_info.lmp_version = lmp_version;
    p_acl_cb->remote_version_info.manufacturer = manufacturer;
    p_acl_cb->remote_version_info.lmp_subversion = lmp_subversion;
    p_acl_cb->remote_version_info.valid = true;
    BTM_update_version_info(p_acl_cb->RemoteAddress(),
                            p_acl_cb->remote_version_info);

    bluetooth::common::LogRemoteVersionInfo(handle, status, lmp_version,
                                            manufacturer, lmp_subversion);
  } else {
    bluetooth::common::LogRemoteVersionInfo(handle, status, 0, 0, 0);
  }
}

void btm_read_remote_version_complete(tHCI_STATUS status, uint16_t handle,
                                      uint8_t lmp_version,
                                      uint16_t manufacturer,
                                      uint16_t lmp_subversion) {
  btm_process_remote_version_complete(status, handle, lmp_version, manufacturer,
                                      lmp_subversion);
  maybe_chain_more_commands_after_read_remote_version_complete(status, handle);
}

/*******************************************************************************
 *
 * Function         btm_process_remote_ext_features
 *
 * Description      Local function called to process all extended features pages
 *                  read from a remote device.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_process_remote_ext_features(tACL_CONN* p_acl_cb,
                                     uint8_t max_page_number) {
  CHECK(p_acl_cb != nullptr);
  if (!p_acl_cb->peer_lmp_feature_valid[max_page_number]) {
    log::warn("Checking remote features but remote feature read is incomplete");
  }

  bool ssp_supported =
      HCI_SSP_HOST_SUPPORTED(p_acl_cb->peer_lmp_feature_pages[1]);
  bool secure_connections_supported =
      HCI_SC_HOST_SUPPORTED(p_acl_cb->peer_lmp_feature_pages[1]);
  bool role_switch_supported =
      HCI_SWITCH_SUPPORTED(p_acl_cb->peer_lmp_feature_pages[0]);
  bool br_edr_supported =
      !HCI_BREDR_NOT_SPT_SUPPORTED(p_acl_cb->peer_lmp_feature_pages[0]);
  bool le_supported =
      HCI_LE_SPT_SUPPORTED(p_acl_cb->peer_lmp_feature_pages[0]) &&
      HCI_LE_HOST_SUPPORTED(p_acl_cb->peer_lmp_feature_pages[1]);
  btm_sec_set_peer_sec_caps(p_acl_cb->hci_handle, ssp_supported,
                            secure_connections_supported, role_switch_supported,
                            br_edr_supported, le_supported);
}

/*******************************************************************************
 *
 * Function         btm_read_remote_ext_features
 *
 * Description      Local function called to send a read remote extended
 *                  features
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_read_remote_ext_features(uint16_t handle, uint8_t page_number) {
  btsnd_hcic_rmt_ext_features(handle, page_number);
}

/*******************************************************************************
 *
 * Function         btm_read_remote_ext_features_complete
 *
 * Description      This function is called when the remote extended features
 *                  complete event is received from the HCI.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_read_remote_ext_features_complete_raw(uint8_t* p, uint8_t evt_len) {
  uint8_t page_num, max_page;
  uint16_t handle;

  if (evt_len < HCI_EXT_FEATURES_SUCCESS_EVT_LEN) {
    log::warn("Remote extended feature length too short. length={}", evt_len);
    return;
  }

  ++p;
  STREAM_TO_UINT16(handle, p);
  STREAM_TO_UINT8(page_num, p);
  STREAM_TO_UINT8(max_page, p);

  if (max_page > HCI_EXT_FEATURES_PAGE_MAX) {
    log::warn("Too many max pages read page={} unknown", max_page);
    return;
  }

  if (page_num > HCI_EXT_FEATURES_PAGE_MAX) {
    log::warn("Too many received pages num_page={} invalid", page_num);
    return;
  }

  if (page_num > max_page) {
    log::warn("num_page={}, max_page={} invalid", page_num, max_page);
  }

  btm_read_remote_ext_features_complete(handle, page_num, max_page, p);
}

void btm_read_remote_ext_features_complete(uint16_t handle, uint8_t page_num,
                                           uint8_t max_page,
                                           uint8_t* features) {
  /* Validate parameters */
  auto* p_acl_cb = internal_.acl_get_connection_from_handle(handle);
  if (p_acl_cb == nullptr) {
    log::warn("Unable to find active acl");
    return;
  }

  /* Copy the received features page */
  STREAM_TO_ARRAY(p_acl_cb->peer_lmp_feature_pages[page_num], features,
                  HCI_FEATURE_BYTES_PER_PAGE);
  p_acl_cb->peer_lmp_feature_valid[page_num] = true;

  /* save remote extended features to iot conf file */
  std::string key = IOT_CONF_KEY_RT_EXT_FEATURES "_" + std::to_string(page_num);

  DEVICE_IOT_CONFIG_ADDR_SET_BIN(p_acl_cb->remote_addr, key,
                                 p_acl_cb->peer_lmp_feature_pages[page_num],
                                 BD_FEATURES_LEN);

  /* If there is the next remote features page and
   * we have space to keep this page data - read this page */
  if ((page_num < max_page) && (page_num < HCI_EXT_FEATURES_PAGE_MAX)) {
    page_num++;
    log::debug("BTM reads next remote extended features page ({})", page_num);
    btm_read_remote_ext_features(handle, page_num);
    return;
  }

  /* Reading of remote feature pages is complete */
  log::debug("BTM reached last remote extended features page ({})", page_num);

  /* Process the pages */
  btm_process_remote_ext_features(p_acl_cb, max_page);

  /* Continue with HCI connection establishment */
  internal_.btm_establish_continue(p_acl_cb);
}

/*******************************************************************************
 *
 * Function         btm_read_remote_ext_features_failed
 *
 * Description      This function is called when the remote extended features
 *                  complete event returns a failed status.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_read_remote_ext_features_failed(uint8_t status, uint16_t handle) {
  log::warn("status 0x{:02x} for handle {}", status, handle);

  tACL_CONN* p_acl_cb = internal_.acl_get_connection_from_handle(handle);
  if (p_acl_cb == nullptr) {
    log::warn("Unable to find active acl");
    return;
  }

  /* Process supported features only */
  btm_process_remote_ext_features(p_acl_cb, 0);

  /* Continue HCI connection establishment */
  internal_.btm_establish_continue(p_acl_cb);
}

/*******************************************************************************
 *
 * Function         btm_establish_continue
 *
 * Description      This function is called when the command complete message
 *                  is received from the HCI for the read local link policy
 *                  request.
 *
 * Returns          void
 *
 ******************************************************************************/
void StackAclBtmAcl::btm_establish_continue(tACL_CONN* p_acl) {
  CHECK(p_acl != nullptr);

  if (p_acl->is_transport_br_edr()) {
    /* For now there are a some devices that do not like sending */
    /* commands events and data at the same time. */
    /* Set the packet types to the default allowed by the device */
    const uint16_t default_packet_type_mask =
        btm_cb.acl_cb_.DefaultPacketTypes();
    if (!internal_.change_connection_packet_types(*p_acl,
                                                  default_packet_type_mask)) {
      log::error(
          "Unable to change connection packet type types:{:04x} address:{}",
          default_packet_type_mask,
          ADDRESS_TO_LOGGABLE_CSTR(p_acl->RemoteAddress()));
    }
    btm_set_link_policy(p_acl, btm_cb.acl_cb_.DefaultLinkPolicy());
  } else if (p_acl->is_transport_ble()) {
    tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(p_acl->remote_addr);

    if (p_dev_rec == nullptr) {
      log::warn("No security record for {}",
                ADDRESS_TO_LOGGABLE_CSTR(p_acl->RemoteAddress()));
    } else if (p_dev_rec->sec_rec.is_le_link_key_known()) {
      btm_ble_set_encryption(
          p_acl->remote_addr, BTM_BLE_SEC_ENCRYPT,
          p_dev_rec->role_central ? HCI_ROLE_CENTRAL : HCI_ROLE_PERIPHERAL);
    }
  }
  NotifyAclLinkUp(*p_acl);
}

void btm_establish_continue_from_address(const RawAddress& bda,
                                         tBT_TRANSPORT transport) {
  tACL_CONN* p_acl = internal_.btm_bda_to_acl(bda, transport);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return;
  }
  internal_.btm_establish_continue(p_acl);
}

/*******************************************************************************
 *
 * Function         BTM_GetLinkSuperTout
 *
 * Description      Read the link supervision timeout value of the connection
 *
 * Returns          status of the operation
 *
 ******************************************************************************/
tBTM_STATUS BTM_GetLinkSuperTout(const RawAddress& remote_bda,
                                 uint16_t* p_timeout) {
  CHECK(p_timeout != nullptr);
  const tACL_CONN* p_acl =
      internal_.btm_bda_to_acl(remote_bda, BT_TRANSPORT_BR_EDR);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return BTM_UNKNOWN_ADDR;
  }
  *p_timeout = p_acl->link_super_tout;
  return BTM_SUCCESS;
}

/*******************************************************************************
 *
 * Function         BTM_SetLinkSuperTout
 *
 * Description      Create and send HCI "Write Link Supervision Timeout" command
 *
 * Returns          status of the operation
 *
 ******************************************************************************/
tBTM_STATUS BTM_SetLinkSuperTout(const RawAddress& remote_bda,
                                 uint16_t timeout) {
  tACL_CONN* p_acl = internal_.btm_bda_to_acl(remote_bda, BT_TRANSPORT_BR_EDR);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return BTM_UNKNOWN_ADDR;
  }

  /* Only send if current role is Central; 2.0 spec requires this */
  if (p_acl->link_role == HCI_ROLE_CENTRAL) {
    if (!bluetooth::shim::
            controller_is_write_link_supervision_timeout_supported()) {
      log::warn(
          "UNSUPPORTED by controller write link supervision timeout:{:.2f}ms "
          "bd_addr:{}",
          supervision_timeout_to_seconds(timeout),
          ADDRESS_TO_LOGGABLE_CSTR(remote_bda));
      return BTM_MODE_UNSUPPORTED;
    }
    p_acl->link_super_tout = timeout;
    btsnd_hcic_write_link_super_tout(p_acl->hci_handle, timeout);
    log::debug("Set supervision timeout:{:.2f}ms bd_addr:{}",
               supervision_timeout_to_seconds(timeout),
               ADDRESS_TO_LOGGABLE_CSTR(remote_bda));
    return BTM_CMD_STARTED;
  } else {
    log::warn(
        "Role is peripheral so unable to set supervision timeout:{:.2f}ms "
        "bd_addr:{}",
        supervision_timeout_to_seconds(timeout),
        ADDRESS_TO_LOGGABLE_CSTR(remote_bda));
    return BTM_SUCCESS;
  }
}

bool BTM_IsAclConnectionUp(const RawAddress& remote_bda,
                           tBT_TRANSPORT transport) {
  return internal_.btm_bda_to_acl(remote_bda, transport) != nullptr;
}

bool BTM_IsAclConnectionUpAndHandleValid(const RawAddress& remote_bda,
                                         tBT_TRANSPORT transport) {
  tACL_CONN* p_acl = internal_.btm_bda_to_acl(remote_bda, transport);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return false;
  }
  return p_acl->hci_handle != HCI_INVALID_HANDLE;
}

bool BTM_IsAclConnectionUpFromHandle(uint16_t hci_handle) {
  return internal_.acl_get_connection_from_handle(hci_handle) != nullptr;
}

/*******************************************************************************
 *
 * Function         BTM_GetNumAclLinks
 *
 * Description      This function is called to count the number of
 *                  ACL links that are active.
 *
 * Returns          uint16_t Number of active ACL links
 *
 ******************************************************************************/
uint16_t BTM_GetNumAclLinks(void) {
  return static_cast<uint16_t>(btm_cb.acl_cb_.NumberOfActiveLinks());
}

/*******************************************************************************
 *
 * Function         btm_get_acl_disc_reason_code
 *
 * Description      This function is called to get the disconnection reason code
 *                  returned by the HCI at disconnection complete event.
 *
 * Returns          true if connection is up, else false.
 *
 ******************************************************************************/
tHCI_REASON btm_get_acl_disc_reason_code(void) {
  return btm_cb.acl_cb_.get_disconnect_reason();
}

/*******************************************************************************
 *
 * Function         btm_is_acl_locally_initiated
 *
 * Description      This function is called to get which side initiates the
 *                  connection, at HCI connection complete event.
 *
 * Returns          true if connection is locally initiated, else false.
 *
 ******************************************************************************/
bool btm_is_acl_locally_initiated(void) {
  return btm_cb.acl_cb_.is_locally_initiated();
}

/*******************************************************************************
 *
 * Function         BTM_GetHCIConnHandle
 *
 * Description      This function is called to get the handle for an ACL
 *                  connection to a specific remote BD Address.
 *
 * Returns          the handle of the connection, or HCI_INVALID_HANDLE if none.
 *
 ******************************************************************************/
uint16_t BTM_GetHCIConnHandle(const RawAddress& remote_bda,
                              tBT_TRANSPORT transport) {
  tACL_CONN* p;
  p = internal_.btm_bda_to_acl(remote_bda, transport);
  if (p != (tACL_CONN*)NULL) {
    return (p->hci_handle);
  }

  /* If here, no BD Addr found */
  return HCI_INVALID_HANDLE;
}

/*******************************************************************************
 *
 * Function         BTM_IsPhy2mSupported
 *
 * Description      This function is called to check PHY 2M support
 *                  from peer device
 * Returns          True when PHY 2M supported false otherwise
 *
 ******************************************************************************/
bool BTM_IsPhy2mSupported(const RawAddress& remote_bda, tBT_TRANSPORT transport) {
  tACL_CONN* p;
  log::verbose("BTM_IsPhy2mSupported");
  p = internal_.btm_bda_to_acl(remote_bda, transport);
  if (p == (tACL_CONN*)NULL) {
    log::verbose("BTM_IsPhy2mSupported: no connection");
    return false;
  }

  if (!p->peer_le_features_valid) {
    log::warn("Checking remote features but remote feature read is incomplete");
  }
  return HCI_LE_2M_PHY_SUPPORTED(p->peer_le_features);
}

/*******************************************************************************
 *
 * Function         BTM_RequestPeerSCA
 *
 * Description      This function is called to request sleep clock accuracy
 *                  from peer device
 *
 ******************************************************************************/
void BTM_RequestPeerSCA(const RawAddress& remote_bda, tBT_TRANSPORT transport) {
  tACL_CONN* p;
  p = internal_.btm_bda_to_acl(remote_bda, transport);
  if (p == (tACL_CONN*)NULL) {
    log::warn("Unable to find active acl");
    return;
  }

  btsnd_hcic_req_peer_sca(p->hci_handle);
}

/*******************************************************************************
 *
 * Function         BTM_GetPeerSCA
 *
 * Description      This function is called to get peer sleep clock accuracy
 *
 * Returns          SCA or 0xFF if SCA was never previously requested, request
 *                  is not supported by peer device or ACL does not exist
 *
 ******************************************************************************/
uint8_t BTM_GetPeerSCA(const RawAddress& remote_bda, tBT_TRANSPORT transport) {
  tACL_CONN* p;
  p = internal_.btm_bda_to_acl(remote_bda, transport);
  if (p != (tACL_CONN*)NULL) {
    return (p->sca);
  }
  log::warn("Unable to find active acl");

  /* If here, no BD Addr found */
  return (0xFF);
}

/*******************************************************************************
 *
 * Function         btm_rejectlist_role_change_device
 *
 * Description      This function is used to rejectlist the device if the role
 *                  switch fails for maximum number of times. It also removes
 *                  the device from the black list if the role switch succeeds.
 *
 * Input Parms      bd_addr - remote BD addr
 *                  hci_status - role switch status
 *
 * Returns          void
 *
 *******************************************************************************/
void btm_rejectlist_role_change_device(const RawAddress& bd_addr,
                                       uint8_t hci_status) {
  tACL_CONN* p = internal_.btm_bda_to_acl(bd_addr, BT_TRANSPORT_BR_EDR);

  if (!p) {
    log::warn("Unable to find active acl");
    return;
  }
  if (hci_status == HCI_SUCCESS) {
    p->switch_role_failed_attempts = 0;
    return;
  }

  /* check for carkits */
  const uint32_t cod_audio_device =
      (BTM_COD_SERVICE_AUDIO | BTM_COD_MAJOR_AUDIO) << 8;
  DEV_CLASS dev_class = btm_get_dev_class(bd_addr);
  if (dev_class == kDevClassEmpty) return;
  const uint32_t cod =
      ((dev_class[0] << 16) | (dev_class[1] << 8) | dev_class[2]) & 0xffffff;
  if ((hci_status != HCI_SUCCESS) &&
      (p->is_switch_role_switching_or_in_progress()) &&
      ((cod & cod_audio_device) == cod_audio_device) &&
      (!interop_match_addr(INTEROP_DYNAMIC_ROLE_SWITCH, &bd_addr))) {
    p->switch_role_failed_attempts++;
    if (p->switch_role_failed_attempts == BTM_MAX_SW_ROLE_FAILED_ATTEMPTS) {
      log::warn(
          "Device {} rejectlisted for role switching - multiple role switch "
          "failed attempts: {}",
          ADDRESS_TO_LOGGABLE_CSTR(bd_addr), p->switch_role_failed_attempts);
      interop_database_add(INTEROP_DYNAMIC_ROLE_SWITCH, &bd_addr, 3);
    }
  }
}

/*******************************************************************************
 *
 * Function         acl_cache_role
 *
 * Description      This function caches the role of the device associated
 *                  with the given address. This happens if we get a role change
 *                  before connection complete. The cached role is propagated
 *                  when ACL Link is created.
 *
 * Returns          void
 *
 ******************************************************************************/

void acl_cache_role(const RawAddress& bd_addr, tHCI_ROLE new_role,
                    bool overwrite_cache) {
  if (overwrite_cache || delayed_role_change_ == nullptr) {
    RoleChangeView role_change;
    role_change.new_role = new_role;
    role_change.bd_addr = bd_addr;
    delayed_role_change_ =
        std::make_unique<RoleChangeView>(std::move(role_change));
  }
}

/*******************************************************************************
 *
 * Function         btm_acl_role_changed
 *
 * Description      This function is called whan a link's central/peripheral
 *role change event or command status event (with error) is received. It updates
 *the link control block, and calls the registered callback with status and role
 *(if registered).
 *
 * Returns          void
 *
 ******************************************************************************/
void StackAclBtmAcl::btm_acl_role_changed(tHCI_STATUS hci_status,
                                          const RawAddress& bd_addr,
                                          tHCI_ROLE new_role) {
  tACL_CONN* p_acl = internal_.btm_bda_to_acl(bd_addr, BT_TRANSPORT_BR_EDR);
  if (p_acl == nullptr) {
    // If we get a role change before connection complete, we cache the new
    // role here and then propagate it when ACL Link is created.
    acl_cache_role(bd_addr, new_role, /*overwrite_cache=*/true);
    log::warn("Unable to find active acl");
    return;
  }

  tBTM_ROLE_SWITCH_CMPL* p_switch_role = &btm_cb.acl_cb_.switch_role_ref_data;
  log::debug("Role change event received peer:{} hci_status:{} new_role:{}",
             ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
             hci_error_code_text(hci_status).c_str(),
             RoleText(new_role).c_str());

  p_switch_role->hci_status = hci_status;
  if (hci_status == HCI_SUCCESS) {
    p_switch_role->role = new_role;
    p_switch_role->remote_bd_addr = bd_addr;

    /* Update cached value */
    p_acl->link_role = new_role;

    /* Reload LSTO: link supervision timeout is reset in the LM after a role
     * switch */
    if (new_role == HCI_ROLE_CENTRAL) {
      uint16_t link_supervision_timeout =
          osi_property_get_int32(PROPERTY_LINK_SUPERVISION_TIMEOUT, 8000);
      BTM_SetLinkSuperTout(bd_addr, link_supervision_timeout);
    }
  } else {
    new_role = p_acl->link_role;
  }

  /* Check if any SCO req is pending for role change */
  btm_sco_chk_pend_rolechange(p_acl->hci_handle);

  /* if switching state is switching we need to turn encryption on */
  /* if idle, we did not change encryption */
  if (p_acl->is_switch_role_switching()) {
    p_acl->set_encryption_on();
    p_acl->set_switch_role_encryption_on();
    return;
  }

  /* Set the switch_role_state to IDLE since the reply received from HCI */
  /* regardless of its result either success or failed. */
  if (p_acl->is_switch_role_in_progress()) {
    p_acl->set_encryption_idle();
    p_acl->reset_switch_role();
  }

  BTA_dm_report_role_change(bd_addr, new_role, hci_status);
  btm_sec_role_changed(hci_status, bd_addr, new_role);

  /* If a disconnect is pending, issue it now that role switch has completed */
  if (p_acl->rs_disc_pending == BTM_SEC_DISC_PENDING) {
    disconnect_acl(*p_acl, HCI_ERR_PEER_USER,
                   "stack::acl::btm_acl::role after role switch");
  }
  p_acl->rs_disc_pending = BTM_SEC_RS_NOT_PENDING; /* reset flag */
}

void btm_acl_role_changed(tHCI_STATUS hci_status, const RawAddress& bd_addr,
                          tHCI_ROLE new_role) {
  btm_rejectlist_role_change_device(bd_addr, hci_status);

  if (hci_status == HCI_SUCCESS) {
    l2c_link_role_changed(&bd_addr, new_role, hci_status);
  } else {
    l2c_link_role_changed(nullptr, HCI_ROLE_UNKNOWN,
                          HCI_ERR_COMMAND_DISALLOWED);
  }
  internal_.btm_acl_role_changed(hci_status, bd_addr, new_role);
}

/*******************************************************************************
 *
 * Function         change_connection_packet_types
 *
 * Description      This function sets the packet types used for a specific
 *                  ACL connection. It is called internally by btm_acl_created
 *                  or by an application/profile by BTM_SetPacketTypes.
 *
 * Returns          status of the operation
 *
 ******************************************************************************/
bool StackAclBtmAcl::change_connection_packet_types(
    tACL_CONN& link, const uint16_t new_packet_type_mask) {
  // Start with the default configured packet types
  const uint16_t default_packet_type_mask = btm_cb.acl_cb_.DefaultPacketTypes();

  uint16_t packet_type_mask =
      default_packet_type_mask &
      (new_packet_type_mask & BTM_ACL_SUPPORTED_PKTS_MASK);

  /* OR in any exception packet types if at least 2.0 version of spec */
  packet_type_mask |=
      ((new_packet_type_mask & BTM_ACL_EXCEPTION_PKTS_MASK) |
       (BTM_ACL_EXCEPTION_PKTS_MASK & default_packet_type_mask));

  /* Exclude packet types not supported by the peer */
  if (link.peer_lmp_feature_valid[0]) {
    PeerPacketTypes peer_packet_types(link.peer_lmp_feature_pages[0]);
    packet_type_mask &= peer_packet_types.acl.supported;
    packet_type_mask |= peer_packet_types.acl.unsupported;
  } else {
    log::info(
        "Unable to include remote supported packet types as read feature "
        "incomplete");
    log::info("TIP: Maybe wait until read feature complete beforehand");
  }

  if (packet_type_mask == 0) {
    log::warn("Unable to send controller illegal change packet mask:0x{:04x}",
              packet_type_mask);
    return false;
  }

  link.pkt_types_mask = packet_type_mask;
  GetInterface().ChangeConnectionPacketType(link.Handle(), link.pkt_types_mask);
  log::debug("Started change connection packet type:0x{:04x} address:{}",
             link.pkt_types_mask,
             ADDRESS_TO_LOGGABLE_CSTR(link.RemoteAddress()));
  return true;
}

void btm_set_packet_types_from_address(const RawAddress& bd_addr,
                                       uint16_t pkt_types) {
  tACL_CONN* p_acl = internal_.btm_bda_to_acl(bd_addr, BT_TRANSPORT_BR_EDR);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return;
  }

  if (!internal_.change_connection_packet_types(*p_acl, pkt_types)) {
    log::error(
        "Unable to change connection packet type types:{:04x} address:{}",
        pkt_types, ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
  }
}

/*******************************************************************************
 *
 * Function         BTM_GetMaxPacketSize
 *
 * Returns          Returns maximum packet size that can be used for current
 *                  connection, 0 if connection is not established
 *
 ******************************************************************************/
uint16_t BTM_GetMaxPacketSize(const RawAddress& addr) {
  tACL_CONN* p = internal_.btm_bda_to_acl(addr, BT_TRANSPORT_BR_EDR);
  uint16_t pkt_types = 0;
  uint16_t pkt_size = 0;
  if (p != NULL) {
    pkt_types = p->pkt_types_mask;
  } else {
    /* Special case for when info for the local device is requested */
    if (addr == *controller_get_interface()->get_address()) {
      pkt_types = btm_cb.acl_cb_.DefaultPacketTypes();
    }
  }

  if (pkt_types) {
    if (!(pkt_types & HCI_PKT_TYPES_MASK_NO_3_DH5))
      pkt_size = HCI_EDR3_DH5_PACKET_SIZE;
    else if (!(pkt_types & HCI_PKT_TYPES_MASK_NO_2_DH5))
      pkt_size = HCI_EDR2_DH5_PACKET_SIZE;
    else if (!(pkt_types & HCI_PKT_TYPES_MASK_NO_3_DH3))
      pkt_size = HCI_EDR3_DH3_PACKET_SIZE;
    else if (pkt_types & HCI_PKT_TYPES_MASK_DH5)
      pkt_size = HCI_DH5_PACKET_SIZE;
    else if (!(pkt_types & HCI_PKT_TYPES_MASK_NO_2_DH3))
      pkt_size = HCI_EDR2_DH3_PACKET_SIZE;
    else if (pkt_types & HCI_PKT_TYPES_MASK_DM5)
      pkt_size = HCI_DM5_PACKET_SIZE;
    else if (pkt_types & HCI_PKT_TYPES_MASK_DH3)
      pkt_size = HCI_DH3_PACKET_SIZE;
    else if (pkt_types & HCI_PKT_TYPES_MASK_DM3)
      pkt_size = HCI_DM3_PACKET_SIZE;
    else if (!(pkt_types & HCI_PKT_TYPES_MASK_NO_3_DH1))
      pkt_size = HCI_EDR3_DH1_PACKET_SIZE;
    else if (!(pkt_types & HCI_PKT_TYPES_MASK_NO_2_DH1))
      pkt_size = HCI_EDR2_DH1_PACKET_SIZE;
    else if (pkt_types & HCI_PKT_TYPES_MASK_DH1)
      pkt_size = HCI_DH1_PACKET_SIZE;
    else if (pkt_types & HCI_PKT_TYPES_MASK_DM1)
      pkt_size = HCI_DM1_PACKET_SIZE;
  }

  return (pkt_size);
}

/*******************************************************************************
 *
 * Function         BTM_IsRemoteVersionReceived
 *
 * Returns          Returns true if "LE Read remote version info" was already
 *                  received on LE transport for this device.
 *
 ******************************************************************************/
bool BTM_IsRemoteVersionReceived(const RawAddress& addr) {
  const tACL_CONN* p_acl = internal_.btm_bda_to_acl(addr, BT_TRANSPORT_LE);
  if (p_acl == nullptr) {
    return false;
  }

  return p_acl->remote_version_received;
}

/*******************************************************************************
 *
 * Function         BTM_ReadRemoteVersion
 *
 * Returns          If connected report peer device info
 *
 ******************************************************************************/
bool BTM_ReadRemoteVersion(const RawAddress& addr, uint8_t* lmp_version,
                           uint16_t* manufacturer, uint16_t* lmp_sub_version) {
  const tACL_CONN* p_acl = internal_.btm_bda_to_acl(addr, BT_TRANSPORT_BR_EDR);
  if (p_acl == nullptr) {
    p_acl = internal_.btm_bda_to_acl(addr, BT_TRANSPORT_LE);
    if (p_acl == nullptr) {
      log::warn("Unable to find active acl");
      return false;
    }
  }

  if (!p_acl->remote_version_info.valid) {
    log::warn("Remote version information is invalid");
    return false;
  }

  if (lmp_version) *lmp_version = p_acl->remote_version_info.lmp_version;

  if (manufacturer) *manufacturer = p_acl->remote_version_info.manufacturer;

  if (lmp_sub_version)
    *lmp_sub_version = p_acl->remote_version_info.lmp_subversion;

  return true;
}

/*******************************************************************************
 *
 * Function         BTM_ReadRemoteFeatures
 *
 * Returns          pointer to the remote supported features mask (8 bytes)
 *
 ******************************************************************************/
uint8_t* BTM_ReadRemoteFeatures(const RawAddress& addr) {
  tACL_CONN* p = internal_.btm_bda_to_acl(addr, BT_TRANSPORT_BR_EDR);
  if (p == NULL) {
    log::warn("Unable to find active acl");
    return (NULL);
  }

  return (p->peer_lmp_feature_pages[0]);
}

/*******************************************************************************
 *
 * Function         BTM_ReadRSSI
 *
 * Description      This function is called to read the link policy settings.
 *                  The address of link policy results are returned in the
 *                  callback.
 *                  (tBTM_RSSI_RESULT)
 *
 * Returns          BTM_CMD_STARTED if successfully initiated or error code
 *
 ******************************************************************************/
tBTM_STATUS BTM_ReadRSSI(const RawAddress& remote_bda, tBTM_CMPL_CB* p_cb) {
  tACL_CONN* p = NULL;
  tBT_DEVICE_TYPE dev_type;
  tBLE_ADDR_TYPE addr_type;

  /* If someone already waiting on the version, do not allow another */
  if (btm_cb.devcb.p_rssi_cmpl_cb) return (BTM_BUSY);

  BTM_ReadDevInfo(remote_bda, &dev_type, &addr_type);

  if (dev_type & BT_DEVICE_TYPE_BLE) {
    p = internal_.btm_bda_to_acl(remote_bda, BT_TRANSPORT_LE);
  }

  if (p == NULL && dev_type & BT_DEVICE_TYPE_BREDR) {
    p = internal_.btm_bda_to_acl(remote_bda, BT_TRANSPORT_BR_EDR);
  }

  if (p) {
    btm_cb.devcb.p_rssi_cmpl_cb = p_cb;
    alarm_set_on_mloop(btm_cb.devcb.read_rssi_timer, BTM_DEV_REPLY_TIMEOUT_MS,
                       btm_read_rssi_timeout, NULL);

    btsnd_hcic_read_rssi(p->hci_handle);
    return (BTM_CMD_STARTED);
  }
  log::warn("Unable to find active acl");

  /* If here, no BD Addr found */
  return (BTM_UNKNOWN_ADDR);
}

/*******************************************************************************
 *
 * Function         BTM_ReadFailedContactCounter
 *
 * Description      This function is called to read the failed contact counter.
 *                  The result is returned in the callback.
 *                  (tBTM_FAILED_CONTACT_COUNTER_RESULT)
 *
 * Returns          BTM_CMD_STARTED if successfully initiated or error code
 *
 ******************************************************************************/
tBTM_STATUS BTM_ReadFailedContactCounter(const RawAddress& remote_bda,
                                         tBTM_CMPL_CB* p_cb) {
  tACL_CONN* p;
  tBT_TRANSPORT transport = BT_TRANSPORT_BR_EDR;
  tBT_DEVICE_TYPE dev_type;
  tBLE_ADDR_TYPE addr_type;

  /* If someone already waiting on the result, do not allow another */
  if (btm_cb.devcb.p_failed_contact_counter_cmpl_cb) return (BTM_BUSY);

  BTM_ReadDevInfo(remote_bda, &dev_type, &addr_type);
  if (dev_type == BT_DEVICE_TYPE_BLE) transport = BT_TRANSPORT_LE;

  p = internal_.btm_bda_to_acl(remote_bda, transport);
  if (p != (tACL_CONN*)NULL) {
    btm_cb.devcb.p_failed_contact_counter_cmpl_cb = p_cb;
    alarm_set_on_mloop(btm_cb.devcb.read_failed_contact_counter_timer,
                       BTM_DEV_REPLY_TIMEOUT_MS,
                       btm_read_failed_contact_counter_timeout, NULL);

    btsnd_hcic_read_failed_contact_counter(p->hci_handle);
    return (BTM_CMD_STARTED);
  }
  log::warn("Unable to find active acl");

  /* If here, no BD Addr found */
  return (BTM_UNKNOWN_ADDR);
}

/*******************************************************************************
 *
 * Function         BTM_ReadTxPower
 *
 * Description      This function is called to read the current
 *                  TX power of the connection. The tx power level results
 *                  are returned in the callback.
 *                  (tBTM_RSSI_RESULT)
 *
 * Returns          BTM_CMD_STARTED if successfully initiated or error code
 *
 ******************************************************************************/
tBTM_STATUS BTM_ReadTxPower(const RawAddress& remote_bda,
                            tBT_TRANSPORT transport, tBTM_CMPL_CB* p_cb) {
  tACL_CONN* p;
#define BTM_READ_RSSI_TYPE_CUR 0x00
#define BTM_READ_RSSI_TYPE_MAX 0X01

  log::verbose("RemBdAddr: {}", ADDRESS_TO_LOGGABLE_STR(remote_bda));

  /* If someone already waiting on the version, do not allow another */
  if (btm_cb.devcb.p_tx_power_cmpl_cb) return (BTM_BUSY);

  p = internal_.btm_bda_to_acl(remote_bda, transport);
  if (p != (tACL_CONN*)NULL) {
    btm_cb.devcb.p_tx_power_cmpl_cb = p_cb;
    alarm_set_on_mloop(btm_cb.devcb.read_tx_power_timer,
                       BTM_DEV_REPLY_TIMEOUT_MS, btm_read_tx_power_timeout,
                       NULL);

    if (p->transport == BT_TRANSPORT_LE) {
      btm_cb.devcb.read_tx_pwr_addr = remote_bda;
      btsnd_hcic_ble_read_adv_chnl_tx_power();
    } else {
      btsnd_hcic_read_tx_power(p->hci_handle, BTM_READ_RSSI_TYPE_CUR);
    }

    return (BTM_CMD_STARTED);
  }

  log::warn("Unable to find active acl");

  /* If here, no BD Addr found */
  return (BTM_UNKNOWN_ADDR);
}

/*******************************************************************************
 *
 * Function         btm_read_tx_power_timeout
 *
 * Description      Callback when reading the tx power times out.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_read_tx_power_timeout(UNUSED_ATTR void* data) {
  tBTM_CMPL_CB* p_cb = btm_cb.devcb.p_tx_power_cmpl_cb;
  btm_cb.devcb.p_tx_power_cmpl_cb = NULL;
  if (p_cb) (*p_cb)((void*)NULL);
}

/*******************************************************************************
 *
 * Function         btm_read_tx_power_complete
 *
 * Description      This function is called when the command complete message
 *                  is received from the HCI for the read tx power request.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_read_tx_power_complete(uint8_t* p, uint16_t evt_len, bool is_ble) {
  tBTM_CMPL_CB* p_cb = btm_cb.devcb.p_tx_power_cmpl_cb;
  tBTM_TX_POWER_RESULT result;

  alarm_cancel(btm_cb.devcb.read_tx_power_timer);
  btm_cb.devcb.p_tx_power_cmpl_cb = NULL;

  /* If there was a registered callback, call it */
  if (p_cb) {
    if (evt_len < 1) {
      goto err_out;
    }

    STREAM_TO_UINT8(result.hci_status, p);

    if (result.hci_status == HCI_SUCCESS) {
      result.status = BTM_SUCCESS;

      if (!is_ble) {
        uint16_t handle;

        if (evt_len < 4) {
          goto err_out;
        }

        STREAM_TO_UINT16(handle, p);
        STREAM_TO_UINT8(result.tx_power, p);

        tACL_CONN* p_acl_cb = internal_.acl_get_connection_from_handle(handle);
        if (p_acl_cb != nullptr) {
          result.rem_bda = p_acl_cb->remote_addr;
        }
      } else {
        if (evt_len < 2) {
          goto err_out;
        }

        STREAM_TO_UINT8(result.tx_power, p);
        result.rem_bda = btm_cb.devcb.read_tx_pwr_addr;
      }
      log::debug(
          "Transmit power complete: tx_power:{} hci status:{}", result.tx_power,
          hci_error_code_text(static_cast<tHCI_STATUS>(result.hci_status))
              .c_str());
    } else {
      result.status = BTM_ERR_PROCESSING;
    }

    (*p_cb)(&result);
  }

  return;

 err_out:
   log::error("Bogus event packet, too short");
}

/*******************************************************************************
 *
 * Function         btm_read_rssi_timeout
 *
 * Description      Callback when reading the RSSI times out.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_read_rssi_timeout(UNUSED_ATTR void* data) {
  tBTM_RSSI_RESULT result;
  tBTM_CMPL_CB* p_cb = btm_cb.devcb.p_rssi_cmpl_cb;
  btm_cb.devcb.p_rssi_cmpl_cb = NULL;
  result.status = BTM_DEVICE_TIMEOUT;
  if (p_cb) (*p_cb)(&result);
}

/*******************************************************************************
 *
 * Function         btm_read_rssi_complete
 *
 * Description      This function is called when the command complete message
 *                  is received from the HCI for the read rssi request.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_read_rssi_complete(uint8_t* p, uint16_t evt_len) {
  tBTM_CMPL_CB* p_cb = btm_cb.devcb.p_rssi_cmpl_cb;
  tBTM_RSSI_RESULT result;

  alarm_cancel(btm_cb.devcb.read_rssi_timer);
  btm_cb.devcb.p_rssi_cmpl_cb = NULL;

  /* If there was a registered callback, call it */
  if (p_cb) {
    if (evt_len < 1) {
      goto err_out;
    }

    STREAM_TO_UINT8(result.hci_status, p);
    result.status = BTM_ERR_PROCESSING;

    if (result.hci_status == HCI_SUCCESS) {
      uint16_t handle;

      if (evt_len < 4) {
        goto err_out;
      }
      STREAM_TO_UINT16(handle, p);

      STREAM_TO_UINT8(result.rssi, p);
      log::debug("Read rrsi complete rssi:%hhd hci status:{}", result.rssi);

      tACL_CONN* p_acl_cb = internal_.acl_get_connection_from_handle(handle);
      if (p_acl_cb != nullptr) {
        result.rem_bda = p_acl_cb->remote_addr;
        result.status = BTM_SUCCESS;
      }
    }
    (*p_cb)(&result);
  }

  return;

 err_out:
   log::error("Bogus event packet, too short");
}

/*******************************************************************************
 *
 * Function         btm_read_failed_contact_counter_timeout
 *
 * Description      Callback when reading the failed contact counter times out.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_read_failed_contact_counter_timeout(UNUSED_ATTR void* data) {
  tBTM_FAILED_CONTACT_COUNTER_RESULT result;
  tBTM_CMPL_CB* p_cb = btm_cb.devcb.p_failed_contact_counter_cmpl_cb;
  btm_cb.devcb.p_failed_contact_counter_cmpl_cb = NULL;
  result.status = BTM_DEVICE_TIMEOUT;
  if (p_cb) (*p_cb)(&result);
}

/*******************************************************************************
 *
 * Function         btm_read_failed_contact_counter_complete
 *
 * Description      This function is called when the command complete message
 *                  is received from the HCI for the read failed contact
 *                  counter request.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_read_failed_contact_counter_complete(uint8_t* p) {
  tBTM_CMPL_CB* p_cb = btm_cb.devcb.p_failed_contact_counter_cmpl_cb;
  tBTM_FAILED_CONTACT_COUNTER_RESULT result;

  alarm_cancel(btm_cb.devcb.read_failed_contact_counter_timer);
  btm_cb.devcb.p_failed_contact_counter_cmpl_cb = NULL;

  /* If there was a registered callback, call it */
  if (p_cb) {
    uint16_t handle;
    STREAM_TO_UINT8(result.hci_status, p);

    if (result.hci_status == HCI_SUCCESS) {
      result.status = BTM_SUCCESS;

      STREAM_TO_UINT16(handle, p);

      STREAM_TO_UINT16(result.failed_contact_counter, p);
      log::debug(
          "Failed contact counter complete: counter {}, hci status:{}",
          result.failed_contact_counter,
          hci_status_code_text(to_hci_status_code(result.hci_status)).c_str());

      tACL_CONN* p_acl_cb = internal_.acl_get_connection_from_handle(handle);
      if (p_acl_cb != nullptr) {
        result.rem_bda = p_acl_cb->remote_addr;
      }
    } else {
      result.status = BTM_ERR_PROCESSING;
    }

    (*p_cb)(&result);
  }
}

/*******************************************************************************
 *
 * Function         btm_read_automatic_flush_timeout_complete
 *
 * Description      This function is called when the command complete message
 *                  is received from the HCI for the read automatic flush
 *                  timeout request.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_read_automatic_flush_timeout_complete(uint8_t* p) {
  tBTM_CMPL_CB* p_cb = btm_cb.devcb.p_automatic_flush_timeout_cmpl_cb;
  tBTM_AUTOMATIC_FLUSH_TIMEOUT_RESULT result;

  alarm_cancel(btm_cb.devcb.read_automatic_flush_timeout_timer);
  btm_cb.devcb.p_automatic_flush_timeout_cmpl_cb = nullptr;

  /* If there was a registered callback, call it */
  if (p_cb) {
    uint16_t handle;
    STREAM_TO_UINT8(result.hci_status, p);
    result.status = BTM_ERR_PROCESSING;

    if (result.hci_status == HCI_SUCCESS) {
      result.status = BTM_SUCCESS;

      STREAM_TO_UINT16(handle, p);
      STREAM_TO_UINT16(result.automatic_flush_timeout, p);
      log::debug(
          "Read automatic flush timeout complete timeout:{} hci_status:{}",
          result.automatic_flush_timeout,
          hci_error_code_text(static_cast<tHCI_STATUS>(result.hci_status))
              .c_str());

      tACL_CONN* p_acl_cb = internal_.acl_get_connection_from_handle(handle);
      if (p_acl_cb != nullptr) {
        result.rem_bda = p_acl_cb->remote_addr;
      }
    }
    (*p_cb)(&result);
  }
}

/*******************************************************************************
 *
 * Function         btm_read_link_quality_timeout
 *
 * Description      Callback when reading the link quality times out.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_read_link_quality_timeout(UNUSED_ATTR void* data) {
  tBTM_CMPL_CB* p_cb = btm_cb.devcb.p_link_qual_cmpl_cb;
  btm_cb.devcb.p_link_qual_cmpl_cb = NULL;
  if (p_cb) (*p_cb)((void*)NULL);
}

/*******************************************************************************
 *
 * Function         btm_read_link_quality_complete
 *
 * Description      This function is called when the command complete message
 *                  is received from the HCI for the read link quality.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_read_link_quality_complete(uint8_t* p, uint16_t evt_len) {
  tBTM_CMPL_CB* p_cb = btm_cb.devcb.p_link_qual_cmpl_cb;
  tBTM_LINK_QUALITY_RESULT result;

  alarm_cancel(btm_cb.devcb.read_link_quality_timer);
  btm_cb.devcb.p_link_qual_cmpl_cb = NULL;

  /* If there was a registered callback, call it */
  if (p_cb) {
    if (evt_len < 1) {
      goto err_out;
    }

    STREAM_TO_UINT8(result.hci_status, p);

    if (result.hci_status == HCI_SUCCESS) {
      uint16_t handle;
      result.status = BTM_SUCCESS;

      if (evt_len < 4) {
        goto err_out;
      }

      STREAM_TO_UINT16(handle, p);

      STREAM_TO_UINT8(result.link_quality, p);
      log::debug(
          "BTM Link Quality Complete: Link Quality {}, hci status:{}",
          result.link_quality,
          hci_error_code_text(static_cast<tHCI_STATUS>(result.hci_status))
              .c_str());

      tACL_CONN* p_acl_cb = internal_.acl_get_connection_from_handle(handle);
      if (p_acl_cb != nullptr) {
        result.rem_bda = p_acl_cb->remote_addr;
      }
    } else {
      result.status = BTM_ERR_PROCESSING;
    }

    (*p_cb)(&result);
  }

  return;

err_out:
  log::error("Bogus Link Quality event packet, size: {}", evt_len);
}

/*******************************************************************************
 *
 * Function         btm_remove_acl
 *
 * Description      This function is called to disconnect an ACL connection
 *
 * Returns          BTM_SUCCESS if successfully initiated, otherwise
 *                  BTM_UNKNOWN_ADDR.
 *
 ******************************************************************************/
tBTM_STATUS btm_remove_acl(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
  tACL_CONN* p_acl = internal_.btm_bda_to_acl(bd_addr, transport);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return BTM_UNKNOWN_ADDR;
  }

  if (p_acl->Handle() == HCI_INVALID_HANDLE) {
    log::warn("Cannot remove unknown acl bd_addr:{} transport:{}",
              ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
              bt_transport_text(transport).c_str());
    return BTM_UNKNOWN_ADDR;
  }

  if (p_acl->rs_disc_pending == BTM_SEC_RS_PENDING) {
    log::debug(
        "Delay disconnect until role switch is complete bd_addr:{} "
        "transport:{}",
        ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
        bt_transport_text(transport).c_str());
    p_acl->rs_disc_pending = BTM_SEC_DISC_PENDING;
    return BTM_SUCCESS;
  }

  disconnect_acl(*p_acl, HCI_ERR_PEER_USER,
                 "stack::acl::btm_acl::btm_remove_acl");
  return BTM_SUCCESS;
}

void btm_cont_rswitch_from_handle(uint16_t hci_handle) {
  tACL_CONN* p = internal_.acl_get_connection_from_handle(hci_handle);
  if (p == nullptr) {
    log::warn("Role switch received but with no active ACL");
    return;
  }

  /* Check to see if encryption needs to be turned off if pending
   change of link key or role switch */
  if (p->is_switch_role_mode_change()) {
    /* Must turn off Encryption first if necessary */
    /* Some devices do not support switch or change of link key while encryption
     * is on */
    if (p->is_encrypted && !IsEprAvailable(*p)) {
      p->set_encryption_off();
      if (p->is_switch_role_mode_change()) {
        p->set_switch_role_encryption_off();
      }
    } else /* Encryption not used or EPR supported, continue with switch
              and/or change of link key */
    {
      if (p->is_switch_role_mode_change()) {
        internal_.hci_start_role_switch_to_central(*p);
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         btm_acl_notif_conn_collision
 *
 * Description      Send connection collision event to upper layer if registered
 *
 *
 ******************************************************************************/
void btm_acl_notif_conn_collision(const RawAddress& bda) {
  do_in_main_thread(FROM_HERE, base::BindOnce(bta_sys_notify_collision, bda));
}

bool BTM_BLE_IS_RESOLVE_BDA(const RawAddress& x) {
  return ((x.address)[0] & BLE_RESOLVE_ADDR_MASK) == BLE_RESOLVE_ADDR_MSB;
}

bool acl_refresh_remote_address(const RawAddress& identity_address,
                                tBLE_ADDR_TYPE identity_address_type,
                                const RawAddress& bda,
                                tBLE_RAND_ADDR_TYPE rra_type,
                                const RawAddress& rpa) {
  tACL_CONN* p_acl = internal_.btm_bda_to_acl(bda, BT_TRANSPORT_LE);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return false;
  }

  if (rra_type == BTM_BLE_ADDR_PSEUDO) {
    /* use identity address, resolvable_private_addr is empty */
    if (rpa.IsEmpty()) {
      p_acl->active_remote_addr_type = identity_address_type;
      p_acl->active_remote_addr = identity_address;
    } else {
      p_acl->active_remote_addr_type = BLE_ADDR_RANDOM;
      p_acl->active_remote_addr = rpa;
    }
  } else {
    p_acl->active_remote_addr_type = static_cast<tBLE_ADDR_TYPE>(rra_type);
    p_acl->active_remote_addr = rpa;
  }

  log::debug("active_remote_addr_type: {} ", p_acl->active_remote_addr_type);
  return true;
}

bool acl_peer_supports_ble_connection_parameters_request(
    const RawAddress& remote_bda) {
  tACL_CONN* p_acl = internal_.btm_bda_to_acl(remote_bda, BT_TRANSPORT_LE);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return false;
  }
  if (!p_acl->peer_le_features_valid) {
    log::warn("Checking remote features but remote feature read is incomplete");
  }
  return HCI_LE_CONN_PARAM_REQ_SUPPORTED(p_acl->peer_le_features);
}

bool acl_peer_supports_sniff_subrating(const RawAddress& remote_bda) {
  tACL_CONN* p_acl = internal_.btm_bda_to_acl(remote_bda, BT_TRANSPORT_BR_EDR);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return false;
  }
  if (!p_acl->peer_lmp_feature_valid[0]) {
    log::warn("Checking remote features but remote feature read is incomplete");
  }
  return HCI_SNIFF_SUB_RATE_SUPPORTED(p_acl->peer_lmp_feature_pages[0]);
}

bool acl_peer_supports_ble_connection_subrating(const RawAddress& remote_bda) {
  tACL_CONN* p_acl = internal_.btm_bda_to_acl(remote_bda, BT_TRANSPORT_LE);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return false;
  }
  if (!p_acl->peer_le_features_valid) {
    log::warn("Checking remote features but remote feature read is incomplete");
  }
  return HCI_LE_CONN_SUBRATING_SUPPORT(p_acl->peer_le_features);
}

bool acl_peer_supports_ble_connection_subrating_host(
    const RawAddress& remote_bda) {
  tACL_CONN* p_acl = internal_.btm_bda_to_acl(remote_bda, BT_TRANSPORT_LE);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return false;
  }
  if (!p_acl->peer_le_features_valid) {
    log::warn("Checking remote features but remote feature read is incomplete");
  }
  return HCI_LE_CONN_SUBRATING_HOST_SUPPORT(p_acl->peer_le_features);
}

/*******************************************************************************
 *
 * Function         BTM_ReadConnectionAddr
 *
 * Description      This function is called to get the local LE device address
 *                  information.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTM_ReadConnectionAddr(const RawAddress& remote_bda,
                            RawAddress& local_conn_addr,
                            tBLE_ADDR_TYPE* p_addr_type, bool ota_address) {
  tBTM_SEC_DEV_REC* p_sec_rec = btm_find_dev(remote_bda);
  if (p_sec_rec == nullptr) {
    log::warn("No matching known device {} in record",
              ADDRESS_TO_LOGGABLE_CSTR(remote_bda));
    return;
  }

  bluetooth::shim::ACL_ReadConnectionAddress(
      p_sec_rec->ble_hci_handle, local_conn_addr, p_addr_type, ota_address);
}

/*******************************************************************************
 *
 * Function         BTM_IsBleConnection
 *
 * Description      This function is called to check if the connection handle
 *                  for an LE link
 *
 * Returns          true if connection is LE link, otherwise false.
 *
 ******************************************************************************/
bool BTM_IsBleConnection(uint16_t hci_handle) {
  const tACL_CONN* p_acl = internal_.acl_get_connection_from_handle(hci_handle);
  if (p_acl == nullptr) return false;
  return p_acl->is_transport_ble();
}

const RawAddress acl_address_from_handle(uint16_t handle) {
  tACL_CONN* p_acl = acl_get_connection_from_handle(handle);
  if (p_acl == nullptr) {
    return RawAddress::kEmpty;
  }
  return p_acl->remote_addr;
}

bool acl_is_switch_role_idle(const RawAddress& bd_addr,
                             tBT_TRANSPORT transport) {
  tACL_CONN* p_acl = internal_.btm_bda_to_acl(bd_addr, transport);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return false;
  }
  return p_acl->is_switch_role_idle();
}

/*******************************************************************************
 *
 * Function       BTM_ReadRemoteConnectionAddr
 *
 * Description    This function is read the LE remote device address used in
 *                connection establishment
 *
 * Parameters     pseudo_addr: pseudo random address available
 *                conn_addr:connection address used
 *                p_addr_type : BD Address type, Public or Random of the address
 *                              used
 *                ota_address: When use if remote used RPA in OTA it will be
 *returned.
 *
 * Returns        bool, true if connection to remote device exists, else false
 *
 ******************************************************************************/
bool BTM_ReadRemoteConnectionAddr(const RawAddress& pseudo_addr,
                                  RawAddress& conn_addr,
                                  tBLE_ADDR_TYPE* p_addr_type,
                                  bool ota_address) {
  tBTM_SEC_DEV_REC* p_sec_rec = btm_find_dev(pseudo_addr);
  if (p_sec_rec == nullptr) {
    log::warn("No matching known device {} in record",
              ADDRESS_TO_LOGGABLE_CSTR(pseudo_addr));
    return false;
  }

  bluetooth::shim::ACL_ReadPeerConnectionAddress(
      p_sec_rec->ble_hci_handle, conn_addr, p_addr_type, ota_address);
  return true;
}

uint8_t acl_link_role_from_handle(uint16_t handle) {
  tACL_CONN* p_acl = internal_.acl_get_connection_from_handle(handle);
  if (p_acl == nullptr) {
    return HCI_ROLE_UNKNOWN;
  }
  return p_acl->link_role;
}

bool acl_peer_supports_ble_packet_extension(uint16_t hci_handle) {
  tACL_CONN* p_acl = internal_.acl_get_connection_from_handle(hci_handle);
  if (p_acl == nullptr) {
    return false;
  }
  if (!p_acl->peer_le_features_valid) {
    log::warn("Checking remote features but remote feature read is incomplete");
  }
  return HCI_LE_DATA_LEN_EXT_SUPPORTED(p_acl->peer_le_features);
}

bool acl_peer_supports_ble_2m_phy(uint16_t hci_handle) {
  tACL_CONN* p_acl = internal_.acl_get_connection_from_handle(hci_handle);
  if (p_acl == nullptr) {
    return false;
  }
  if (!p_acl->peer_le_features_valid) {
    log::warn("Checking remote features but remote feature read is incomplete");
  }
  return HCI_LE_2M_PHY_SUPPORTED(p_acl->peer_le_features);
}

bool acl_peer_supports_ble_coded_phy(uint16_t hci_handle) {
  tACL_CONN* p_acl = internal_.acl_get_connection_from_handle(hci_handle);
  if (p_acl == nullptr) {
    return false;
  }
  if (!p_acl->peer_le_features_valid) {
    log::warn("Checking remote features but remote feature read is incomplete");
    return false;
  }
  return HCI_LE_CODED_PHY_SUPPORTED(p_acl->peer_le_features);
}

void acl_set_disconnect_reason(tHCI_STATUS acl_disc_reason) {
  btm_cb.acl_cb_.set_disconnect_reason(acl_disc_reason);
}

void acl_set_locally_initiated(bool locally_initiated) {
  btm_cb.acl_cb_.set_locally_initiated(locally_initiated);
}

bool acl_is_role_switch_allowed() {
  return btm_cb.acl_cb_.DefaultLinkPolicy() &
         HCI_ENABLE_CENTRAL_PERIPHERAL_SWITCH;
}

uint16_t acl_get_supported_packet_types() {
  return btm_cb.acl_cb_.DefaultPacketTypes();
}

bool acl_set_peer_le_features_from_handle(uint16_t hci_handle,
                                          const uint8_t* p) {
  tACL_CONN* p_acl = internal_.acl_get_connection_from_handle(hci_handle);
  if (p_acl == nullptr) {
    return false;
  }
  STREAM_TO_ARRAY(p_acl->peer_le_features, p, BD_FEATURES_LEN);
  p_acl->peer_le_features_valid = true;
  log::debug("Completed le feature read request");

  /* save LE remote supported features to iot conf file */
  std::string key = IOT_CONF_KEY_RT_SUPP_FEATURES "_" + std::to_string(0);

  DEVICE_IOT_CONFIG_ADDR_SET_BIN(p_acl->remote_addr, key,
                                 p_acl->peer_le_features, BD_FEATURES_LEN);
  return true;
}

void on_acl_br_edr_connected(const RawAddress& bda, uint16_t handle,
                             uint8_t enc_mode, bool locally_initiated) {
  power_telemetry::GetInstance().LogLinkDetails(handle, bda, true, true);
  if (delayed_role_change_ != nullptr && delayed_role_change_->bd_addr == bda) {
    btm_sec_connected(bda, handle, HCI_SUCCESS, enc_mode,
                      delayed_role_change_->new_role);
  } else {
    btm_sec_connected(bda, handle, HCI_SUCCESS, enc_mode);
  }
  delayed_role_change_ = nullptr;
  l2c_link_hci_conn_comp(HCI_SUCCESS, handle, bda);
  uint16_t link_supervision_timeout =
      osi_property_get_int32(PROPERTY_LINK_SUPERVISION_TIMEOUT, 8000);
  BTM_SetLinkSuperTout(bda, link_supervision_timeout);

  tACL_CONN* p_acl = internal_.acl_get_connection_from_handle(handle);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return;
  }

  acl_set_locally_initiated(locally_initiated);

  /*
   * The legacy code path informs the upper layer via the BTA
   * layer after all relevant read_remote_ commands are complete.
   * The GD code path has ownership of the read_remote_ commands
   * and thus may inform the upper layers about the connection.
   */
  NotifyAclLinkUp(*p_acl);
}

void on_acl_br_edr_failed(const RawAddress& bda, tHCI_STATUS status,
                          bool locally_initiated) {
  ASSERT_LOG(status != HCI_SUCCESS,
             "Successful connection entering failing code path");
  if (delayed_role_change_ != nullptr && delayed_role_change_->bd_addr == bda) {
    btm_sec_connected(bda, HCI_INVALID_HANDLE, status, false,
                      delayed_role_change_->new_role);
  } else {
    btm_sec_connected(bda, HCI_INVALID_HANDLE, status, false);
  }
  delayed_role_change_ = nullptr;
  l2c_link_hci_conn_comp(status, HCI_INVALID_HANDLE, bda);

  acl_set_locally_initiated(locally_initiated);
  btm_acl_create_failed(bda, BT_TRANSPORT_BR_EDR, status);
}

void btm_acl_connected(const RawAddress& bda, uint16_t handle,
                       tHCI_STATUS status, uint8_t enc_mode) {
  switch (status) {
    case HCI_SUCCESS:
      power_telemetry::GetInstance().LogLinkDetails(handle, bda, true, true);
      return on_acl_br_edr_connected(bda, handle, enc_mode,
                                     true /* locally_initiated */);
    default:
      return on_acl_br_edr_failed(bda, status, /* locally_initiated */ true);
  }
}

void btm_acl_disconnected(tHCI_STATUS status, uint16_t handle,
                          tHCI_REASON reason) {
  if (status != HCI_SUCCESS) {
    log::warn("Received disconnect with error:{}",
              hci_error_code_text(status).c_str());
  }
  power_telemetry::GetInstance().LogLinkDetails(handle, RawAddress::kEmpty,
                                                false, true);
  /* There can be a case when we rejected PIN code authentication */
  /* otherwise save a new reason */
  if (btm_get_acl_disc_reason_code() != HCI_ERR_HOST_REJECT_SECURITY) {
    acl_set_disconnect_reason(static_cast<tHCI_STATUS>(reason));
  }

  /* Let L2CAP know about it */
  l2c_link_hci_disc_comp(handle, reason);

  /* Notify security manager */
  btm_sec_disconnected(handle, reason,
                       "stack::acl::btm_acl::btm_acl_disconnected");
}

void acl_create_classic_connection(const RawAddress& bd_addr,
                                   bool there_are_high_priority_channels,
                                   bool is_bonding) {
  return bluetooth::shim::ACL_CreateClassicConnection(bd_addr);
}

void btm_connection_request(const RawAddress& bda,
                            const bluetooth::hci::ClassOfDevice& cod) {
  // Copy Cod information
  DEV_CLASS dc;

  /* Some device may request a connection before we are done with the HCI_Reset
   * sequence */
  if (!controller_get_interface()->get_is_ready()) {
    log::verbose("Security Manager: connect request when device not ready");
    btsnd_hcic_reject_conn(bda, HCI_ERR_HOST_REJECT_DEVICE);
    return;
  }

  dc[0] = cod.cod[2], dc[1] = cod.cod[1], dc[2] = cod.cod[0];

  btm_sec_conn_req(bda, dc);
}

void acl_disconnect_from_handle(uint16_t handle, tHCI_STATUS reason,
                                std::string comment) {
  acl_disconnect_after_role_switch(handle, reason, comment);
}

// BLUETOOTH CORE SPECIFICATION Version 5.4 | Vol 4, Part E
// 7.1.6 Disconnect command
// Only a subset of reasons are valid and will be accepted
// by the controller
bool is_disconnect_reason_valid(const tHCI_REASON& reason) {
  switch (reason) {
    case HCI_ERR_AUTH_FAILURE:
    case HCI_ERR_PEER_USER:
    case HCI_ERR_REMOTE_LOW_RESOURCE:
    case HCI_ERR_REMOTE_POWER_OFF:
    case HCI_ERR_UNSUPPORTED_REM_FEATURE:
    case HCI_ERR_PAIRING_WITH_UNIT_KEY_NOT_SUPPORTED:
    case HCI_ERR_UNACCEPT_CONN_INTERVAL:
      return true;
    default:
      break;
  }
  return false;
}

void acl_disconnect_after_role_switch(uint16_t conn_handle, tHCI_STATUS reason,
                                      std::string comment) {
  if (!is_disconnect_reason_valid(reason)) {
    log::warn(
        "Controller will not accept invalid reason parameter:{} instead "
        "sending:{}",
        hci_error_code_text(reason).c_str(),
        hci_error_code_text(HCI_ERR_PEER_USER).c_str());
    reason = HCI_ERR_PEER_USER;
  }

  tACL_CONN* p_acl = internal_.acl_get_connection_from_handle(conn_handle);
  if (p_acl == nullptr) {
    log::error("Sending disconnect for unknown acl:{} PLEASE FIX", conn_handle);
    GetInterface().Disconnect(conn_handle, reason);
    return;
  }

  /* If a role switch is in progress, delay the HCI Disconnect to avoid
   * controller problem */
  if (p_acl->rs_disc_pending == BTM_SEC_RS_PENDING) {
    log::debug(
        "Role switch in progress - Set DISC Pending flag in "
        "btm_sec_send_hci_disconnect to delay disconnect");
    p_acl->rs_disc_pending = BTM_SEC_DISC_PENDING;
  } else {
    log::debug("Sending acl disconnect reason:{} [{}]",
               hci_error_code_text(reason).c_str(), reason);
    disconnect_acl(*p_acl, reason, comment);
  }
}

void acl_send_data_packet_br_edr(const RawAddress& bd_addr, BT_HDR* p_buf) {
    tACL_CONN* p_acl = internal_.btm_bda_to_acl(bd_addr, BT_TRANSPORT_BR_EDR);
    if (p_acl == nullptr) {
      log::warn("Acl br_edr data write for unknown device:{}",
                ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
      osi_free(p_buf);
      return;
    }
    power_telemetry::GetInstance().LogTxAclPktData(p_buf->len);
    return bluetooth::shim::ACL_WriteData(p_acl->hci_handle, p_buf);
}

void acl_send_data_packet_ble(const RawAddress& bd_addr, BT_HDR* p_buf) {
    tACL_CONN* p_acl = internal_.btm_bda_to_acl(bd_addr, BT_TRANSPORT_LE);
    if (p_acl == nullptr) {
      log::warn("Acl le data write for unknown device:{}",
                ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
      osi_free(p_buf);
      return;
    }
    power_telemetry::GetInstance().LogTxAclPktData(p_buf->len);
    return bluetooth::shim::ACL_WriteData(p_acl->hci_handle, p_buf);
}

void acl_write_automatic_flush_timeout(const RawAddress& bd_addr,
                                       uint16_t flush_timeout_in_ticks) {
  tACL_CONN* p_acl = internal_.btm_bda_to_acl(bd_addr, BT_TRANSPORT_BR_EDR);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return;
  }
  if (p_acl->flush_timeout_in_ticks == flush_timeout_in_ticks) {
    log::info(
        "Ignoring since cached value is same as requested flush_timeout:{}",
        flush_timeout_in_ticks);
    return;
  }
  flush_timeout_in_ticks &= HCI_MAX_AUTOMATIC_FLUSH_TIMEOUT;
  p_acl->flush_timeout_in_ticks = flush_timeout_in_ticks;
  btsnd_hcic_write_auto_flush_tout(p_acl->hci_handle, flush_timeout_in_ticks);
}

bool acl_create_le_connection_with_id(uint8_t id, const RawAddress& bd_addr,
                                      tBLE_ADDR_TYPE addr_type) {
  tBLE_BD_ADDR address_with_type{
      .type = addr_type,
      .bda = bd_addr,
  };

  find_in_device_record(bd_addr, &address_with_type);

  log::debug("Creating le direct connection to:{} type:{} (initial type: {})",
             ADDRESS_TO_LOGGABLE_CSTR(address_with_type),
             AddressTypeText(address_with_type.type).c_str(),
             AddressTypeText(addr_type).c_str());

  if (address_with_type.type == BLE_ADDR_ANONYMOUS) {
    log::warn(
        "Creating le direct connection to:{}, address type 'anonymous' is "
        "invalid",
        ADDRESS_TO_LOGGABLE_CSTR(address_with_type));
    return false;
  }

  if (bluetooth::common::init_flags::
          use_unified_connection_manager_is_enabled()) {
    bluetooth::connection::GetConnectionManager().start_direct_connection(
        id, bluetooth::core::ToRustAddress(address_with_type));
  } else {
    bluetooth::shim::ACL_AcceptLeConnectionFrom(address_with_type,
                                                /* is_direct */ true);
  }
  return true;
}

bool acl_create_le_connection_with_id(uint8_t id, const RawAddress& bd_addr) {
  return acl_create_le_connection_with_id(id, bd_addr, BLE_ADDR_PUBLIC);
}

bool acl_create_le_connection(const RawAddress& bd_addr) {
  return acl_create_le_connection_with_id(CONN_MGR_ID_L2CAP, bd_addr);
}

void acl_rcv_acl_data(BT_HDR* p_msg) {
  acl_header_t acl_header{
      .handle = HCI_INVALID_HANDLE,
      .hci_len = 0,
  };
  const uint8_t* p = (uint8_t*)(p_msg + 1) + p_msg->offset;

  STREAM_TO_UINT16(acl_header.handle, p);
  acl_header.handle = HCID_GET_HANDLE(acl_header.handle);

  power_telemetry::GetInstance().LogRxAclPktData(p_msg->len);
  STREAM_TO_UINT16(acl_header.hci_len, p);
  if (acl_header.hci_len < L2CAP_PKT_OVERHEAD ||
      acl_header.hci_len != p_msg->len - sizeof(acl_header)) {
    log::warn("Received mismatched hci header length:{} data_len:{}",
              acl_header.hci_len, p_msg->len - sizeof(acl_header));
    osi_free(p_msg);
    return;
  }
  l2c_rcv_acl_data(p_msg);
}

void acl_packets_completed(uint16_t handle, uint16_t credits) {
  l2c_packets_completed(handle, credits);
  bluetooth::hci::IsoManager::GetInstance()->HandleGdNumComplDataPkts(handle,
                                                                      credits);
}

void acl_process_supported_features(uint16_t handle, uint64_t features) {
  tACL_CONN* p_acl = internal_.acl_get_connection_from_handle(handle);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return;
  }
  const uint8_t current_page_number = 0;

  memcpy(p_acl->peer_lmp_feature_pages[current_page_number],
         (uint8_t*)&features, sizeof(uint64_t));
  p_acl->peer_lmp_feature_valid[current_page_number] = true;

  log::debug(
      "Copied supported feature pages handle:{} current_page_number:{} "
      "features:{}",
      handle, current_page_number,
      bd_features_text(p_acl->peer_lmp_feature_pages[current_page_number])
          .c_str());

  if ((HCI_LMP_EXTENDED_SUPPORTED(p_acl->peer_lmp_feature_pages[0])) &&
      (bluetooth::shim::GetController()->IsSupported(
          bluetooth::hci::OpCode::READ_REMOTE_EXTENDED_FEATURES))) {
    log::debug("Waiting for remote extended feature response to arrive");
  } else {
    log::debug("No more remote features outstanding so notify upper layer");
    NotifyAclFeaturesReadComplete(*p_acl, current_page_number);
  }
}

void acl_process_extended_features(uint16_t handle, uint8_t current_page_number,
                                   uint8_t max_page_number, uint64_t features) {
  if (current_page_number > HCI_EXT_FEATURES_PAGE_MAX) {
    log::warn("Unable to process current_page_number:{}", current_page_number);
    return;
  }
  tACL_CONN* p_acl = internal_.acl_get_connection_from_handle(handle);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return;
  }
  memcpy(p_acl->peer_lmp_feature_pages[current_page_number],
         (uint8_t*)&features, sizeof(uint64_t));
  p_acl->peer_lmp_feature_valid[current_page_number] = true;

  log::debug(
      "Copied extended feature pages handle:{} current_page_number:{} "
      "max_page_number:{} features:{}",
      handle, current_page_number, max_page_number,
      bd_features_text(p_acl->peer_lmp_feature_pages[current_page_number])
          .c_str());

  if (max_page_number == 0 || max_page_number == current_page_number) {
    NotifyAclFeaturesReadComplete(*p_acl, max_page_number);
  }
}

void ACL_RegisterClient(struct acl_client_callback_s* callbacks) {
  log::debug("UNIMPLEMENTED");
}

void ACL_UnregisterClient(struct acl_client_callback_s* callbacks) {
  log::debug("UNIMPLEMENTED");
}

tACL_CONN* btm_acl_for_bda(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
  tACL_CONN* p_acl = internal_.btm_bda_to_acl(bd_addr, transport);
  if (p_acl == nullptr) {
    log::warn("Unable to find active acl");
    return nullptr;
  }
  return p_acl;
}

void find_in_device_record(const RawAddress& bd_addr,
                           tBLE_BD_ADDR* address_with_type) {
  const tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec == nullptr) {
    return;
  }

  if (p_dev_rec->device_type & BT_DEVICE_TYPE_BLE) {
    if (p_dev_rec->ble.identity_address_with_type.bda.IsEmpty()) {
      *address_with_type = {.type = p_dev_rec->ble.AddressType(),
                            .bda = bd_addr};
      return;
    }
    *address_with_type = p_dev_rec->ble.identity_address_with_type;
    return;
  }
  *address_with_type = {.type = BLE_ADDR_PUBLIC, .bda = bd_addr};
  return;
}
