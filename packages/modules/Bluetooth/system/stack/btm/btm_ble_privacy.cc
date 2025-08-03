/******************************************************************************
 *
 *  Copyright 1999-2012 Broadcom Corporation
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

/******************************************************************************
 *
 *  This file contains functions for BLE controller based privacy.
 *
 ******************************************************************************/
#define LOG_TAG "ble_priv"

#include "stack/include/btm_ble_privacy.h"

#include <bluetooth/log.h>

#include "btm_dev.h"
#include "btm_sec_cb.h"
#include "btm_sec_int_types.h"
#include "device/include/controller.h"
#include "main/shim/acl_api.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "stack/btm/btm_int_types.h"
#include "stack/include/bt_octets.h"
#include "stack/include/bt_types.h"
#include "stack/include/btm_api.h"
#include "types/raw_address.h"

using namespace bluetooth;

extern tBTM_CB btm_cb;

/* RPA offload VSC specifics */
#define HCI_VENDOR_BLE_RPA_VSC (0x0155 | HCI_GRP_VENDOR_SPECIFIC)

#define BTM_BLE_META_IRK_ENABLE 0x01
#define BTM_BLE_META_ADD_IRK_ENTRY 0x02
#define BTM_BLE_META_REMOVE_IRK_ENTRY 0x03
#define BTM_BLE_META_CLEAR_IRK_LIST 0x04
#define BTM_BLE_META_READ_IRK_ENTRY 0x05
#define BTM_BLE_META_CS_RESOLVE_ADDR 0x00000001
#define BTM_BLE_IRK_ENABLE_LEN 2

#define BTM_BLE_META_ADD_IRK_LEN 24
#define BTM_BLE_META_REMOVE_IRK_LEN 8
#define BTM_BLE_META_CLEAR_IRK_LEN 1
#define BTM_BLE_META_READ_IRK_LEN 2
#define BTM_BLE_META_ADD_WL_ATTR_LEN 9

/*******************************************************************************
 *         Functions implemented controller based privacy using Resolving List
 ******************************************************************************/
/*******************************************************************************
 *
 * Function         btm_ble_enq_resolving_list_pending
 *
 * Description      add target address into resolving pending operation queue
 *
 * Parameters       target_bda: target device address
 *                  add_entry: true for add entry, false for remove entry
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_ble_enq_resolving_list_pending(const RawAddress& pseudo_bda,
                                               uint8_t op_code) {
  tBTM_BLE_RESOLVE_Q* p_q = &btm_cb.ble_ctr_cb.resolving_list_pend_q;

  p_q->resolve_q_random_pseudo[p_q->q_next] = pseudo_bda;
  p_q->resolve_q_action[p_q->q_next] = op_code;
  p_q->q_next++;
  p_q->q_next %= controller_get_interface()->get_ble_resolving_list_max_size();
}

/*******************************************************************************
 *
 * Function         btm_ble_brcm_find_resolving_pending_entry
 *
 * Description      check to see if the action is in pending list
 *
 * Parameters       true: action pending;
 *                  false: new action
 *
 * Returns          void
 *
 ******************************************************************************/
static bool btm_ble_brcm_find_resolving_pending_entry(
    const RawAddress& pseudo_addr, uint8_t action) {
  tBTM_BLE_RESOLVE_Q* p_q = &btm_cb.ble_ctr_cb.resolving_list_pend_q;

  for (uint8_t i = p_q->q_pending; i != p_q->q_next;) {
    if (p_q->resolve_q_random_pseudo[i] == pseudo_addr &&
        action == p_q->resolve_q_action[i])
      return true;

    i++;
    i %= controller_get_interface()->get_ble_resolving_list_max_size();
  }
  return false;
}

/*******************************************************************************
 *
 * Function         btm_ble_deq_resolving_pending
 *
 * Description      dequeue target address from resolving pending operation
 *                  queue
 *
 * Parameters       pseudo_addr: pseudo_addr device address
 *
 * Returns          void
 *
 ******************************************************************************/
static bool btm_ble_deq_resolving_pending(RawAddress& pseudo_addr) {
  tBTM_BLE_RESOLVE_Q* p_q = &btm_cb.ble_ctr_cb.resolving_list_pend_q;

  if (p_q->q_next != p_q->q_pending) {
    pseudo_addr = p_q->resolve_q_random_pseudo[p_q->q_pending];
    p_q->resolve_q_random_pseudo[p_q->q_pending] = RawAddress::kEmpty;
    p_q->q_pending++;
    p_q->q_pending %=
        controller_get_interface()->get_ble_resolving_list_max_size();
    return true;
  }

  return false;
}

/*******************************************************************************
 *
 * Function         btm_ble_clear_irk_index
 *
 * Description      clear IRK list index mask for availability
 *
 * Returns          none
 *
 ******************************************************************************/
static void btm_ble_clear_irk_index(uint8_t index) {
  uint8_t byte;
  uint8_t bit;

  if (index < controller_get_interface()->get_ble_resolving_list_max_size()) {
    byte = index / 8;
    bit = index % 8;
    btm_cb.ble_ctr_cb.irk_list_mask[byte] &= (~(1 << bit));
  }
}

/*******************************************************************************
 *
 * Function         btm_ble_find_irk_index
 *
 * Description      find the first available IRK list index
 *
 * Returns          index from 0 ~ max (127 default)
 *
 ******************************************************************************/
static uint8_t btm_ble_find_irk_index(void) {
  uint8_t i = 0;
  uint8_t byte;
  uint8_t bit;

  while (i < controller_get_interface()->get_ble_resolving_list_max_size()) {
    byte = i / 8;
    bit = i % 8;

    if ((btm_cb.ble_ctr_cb.irk_list_mask[byte] & (1 << bit)) == 0) {
      btm_cb.ble_ctr_cb.irk_list_mask[byte] |= (1 << bit);
      return i;
    }
    i++;
  }

  log::error("no index found");
  return i;
}

/*******************************************************************************
 *
 * Function         btm_ble_update_resolving_list
 *
 * Description      update resolving list entry in host maintained record
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_ble_update_resolving_list(const RawAddress& pseudo_bda,
                                          bool add) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(pseudo_bda);
  if (p_dev_rec == NULL) return;

  if (add) {
    p_dev_rec->ble.in_controller_list |= BTM_RESOLVING_LIST_BIT;
    if (!controller_get_interface()->SupportsBlePrivacy())
      p_dev_rec->ble.resolving_list_index = btm_ble_find_irk_index();
  } else {
    p_dev_rec->ble.in_controller_list &= ~BTM_RESOLVING_LIST_BIT;
    if (!controller_get_interface()->SupportsBlePrivacy()) {
      /* clear IRK list index mask */
      btm_ble_clear_irk_index(p_dev_rec->ble.resolving_list_index);
      p_dev_rec->ble.resolving_list_index = 0;
    }
  }
}

static bool clear_resolving_list_bit(void* data, void* context) {
  tBTM_SEC_DEV_REC* p_dev_rec = static_cast<tBTM_SEC_DEV_REC*>(data);
  p_dev_rec->ble.in_controller_list &= ~BTM_RESOLVING_LIST_BIT;
  return true;
}

/*******************************************************************************
 *
 * Function         btm_ble_clear_resolving_list_complete
 *
 * Description      This function is called when command complete for
 *                  clear resolving list
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_clear_resolving_list_complete(uint8_t* p, uint16_t evt_len) {
  uint8_t status = 0;

  if (evt_len < 1) {
    log::error("malformatted event packet: containing zero bytes");
    return;
  }

  STREAM_TO_UINT8(status, p);

  log::verbose("status={}", status);

  if (status == HCI_SUCCESS) {
    if (evt_len >= 3) {
      /* VSC complete has one extra byte for op code and list size, skip it here
       */
      p++;

      /* updated the available list size, and current list size */
      uint8_t irk_list_sz_max = 0;
      STREAM_TO_UINT8(irk_list_sz_max, p);

      if (controller_get_interface()->get_ble_resolving_list_max_size() == 0)
        btm_ble_resolving_list_init(irk_list_sz_max);

      uint8_t irk_mask_size = (irk_list_sz_max % 8) ? (irk_list_sz_max / 8 + 1)
                                                    : (irk_list_sz_max / 8);
      memset(btm_cb.ble_ctr_cb.irk_list_mask, 0, irk_mask_size);
    }

    btm_cb.ble_ctr_cb.resolving_list_avail_size =
        controller_get_interface()->get_ble_resolving_list_max_size();

    log::verbose("resolving_list_avail_size={}",
                 btm_cb.ble_ctr_cb.resolving_list_avail_size);

    list_foreach(btm_sec_cb.sec_dev_rec, clear_resolving_list_bit, NULL);
  }
}

/*******************************************************************************
 *
 * Function         btm_ble_add_resolving_list_entry_complete
 *
 * Description      This function is called when command complete for
 *                  add resolving list entry
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_add_resolving_list_entry_complete(uint8_t* p, uint16_t evt_len) {
  uint8_t status;

  if (evt_len < 1) {
    log::error("malformatted event packet: containing zero byte");
    return;
  }

  STREAM_TO_UINT8(status, p);

  log::verbose("status={}", status);

  RawAddress pseudo_bda;
  if (!btm_ble_deq_resolving_pending(pseudo_bda)) {
    log::verbose("no pending resolving list operation");
    return;
  }

  if (status == HCI_SUCCESS) {
    btm_ble_update_resolving_list(pseudo_bda, true);
    /* privacy 1.2 command complete does not have these extra byte */
    if (evt_len > 2) {
      /* VSC complete has one extra byte for op code, skip it here */
      p++;
      STREAM_TO_UINT8(btm_cb.ble_ctr_cb.resolving_list_avail_size, p);
    } else
      btm_cb.ble_ctr_cb.resolving_list_avail_size--;
  } else if (status ==
             HCI_ERR_MEMORY_FULL) /* BT_ERROR_CODE_MEMORY_CAPACITY_EXCEEDED  */
  {
    btm_cb.ble_ctr_cb.resolving_list_avail_size = 0;
    log::verbose("Resolving list Full");
  }
}

/*******************************************************************************
 *
 * Function         btm_ble_remove_resolving_list_entry_complete
 *
 * Description      This function is called when command complete for
 *                  remove resolving list entry
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_remove_resolving_list_entry_complete(uint8_t* p,
                                                  uint16_t evt_len) {
  RawAddress pseudo_bda;
  uint8_t status;

  STREAM_TO_UINT8(status, p);

  log::verbose("status={}", status);

  if (!btm_ble_deq_resolving_pending(pseudo_bda)) {
    log::error("no pending resolving list operation");
    return;
  }

  if (status == HCI_SUCCESS) {
    /* proprietary: spec does not have these extra bytes */
    if (evt_len > 2) {
      p++; /* skip opcode */
      STREAM_TO_UINT8(btm_cb.ble_ctr_cb.resolving_list_avail_size, p);
    } else
      btm_cb.ble_ctr_cb.resolving_list_avail_size++;
  }
}

/*******************************************************************************
 *
 * Function         btm_ble_read_resolving_list_entry_complete
 *
 * Description      This function is called when command complete for
 *                  remove resolving list entry
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_read_resolving_list_entry_complete(const uint8_t* p,
                                                uint16_t evt_len) {
  uint8_t status;
  RawAddress rra, pseudo_bda;

  STREAM_TO_UINT8(status, p);

  log::verbose("status={}", status);

  if (!btm_ble_deq_resolving_pending(pseudo_bda)) {
    log::error("no pending resolving list operation");
    return;
  }

  if (status == HCI_SUCCESS) {
    /* proprietary spec has extra bytes */
    if (evt_len > 8) {
      /* skip subcode, index, IRK value, address type, identity addr type */
      p += (2 + 16 + 1 + 6);
      STREAM_TO_BDADDR(rra, p);

      log::info("peer_addr:{}", ADDRESS_TO_LOGGABLE_CSTR(rra));
    } else {
      STREAM_TO_BDADDR(rra, p);
    }
    btm_ble_refresh_peer_resolvable_private_addr(
        pseudo_bda, rra, tBLE_RAND_ADDR_TYPE::BTM_BLE_ADDR_PSEUDO);
  }
}
/*******************************************************************************
                VSC that implement controller based privacy
 ******************************************************************************/
/*******************************************************************************
 *
 * Function         btm_ble_resolving_list_vsc_op_cmpl
 *
 * Description      IRK operation VSC complete handler
 *
 * Parameters
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_ble_resolving_list_vsc_op_cmpl(tBTM_VSC_CMPL* p_params) {
  uint8_t *p = p_params->p_param_buf, op_subcode;
  uint16_t evt_len = p_params->param_len;

  op_subcode = *(p + 1);

  log::verbose("op_subcode={}", op_subcode);

  if (op_subcode == BTM_BLE_META_CLEAR_IRK_LIST) {
    btm_ble_clear_resolving_list_complete(p, evt_len);
  } else if (op_subcode == BTM_BLE_META_ADD_IRK_ENTRY) {
    btm_ble_add_resolving_list_entry_complete(p, evt_len);
  } else if (op_subcode == BTM_BLE_META_REMOVE_IRK_ENTRY) {
    btm_ble_remove_resolving_list_entry_complete(p, evt_len);
  } else if (op_subcode == BTM_BLE_META_READ_IRK_ENTRY) {
    btm_ble_read_resolving_list_entry_complete(p, evt_len);
  } else if (op_subcode == BTM_BLE_META_IRK_ENABLE) {
    /* RPA offloading enable/disabled */
  }
}

/*******************************************************************************
 *
 * Function         btm_ble_remove_resolving_list_entry
 *
 * Description      This function to remove an IRK entry from the list
 *
 * Parameters       ble_addr_type: address type
 *                  ble_addr: LE adddress
 *
 * Returns          status
 *
 ******************************************************************************/
static tBTM_STATUS btm_ble_remove_resolving_list_entry(
    tBTM_SEC_DEV_REC* p_dev_rec) {
  /* if controller does not support RPA offloading or privacy 1.2, skip */
  if (controller_get_interface()->get_ble_resolving_list_max_size() == 0)
    return BTM_WRONG_MODE;

  if (controller_get_interface()->SupportsBlePrivacy()) {
    bluetooth::shim::ACL_RemoveFromAddressResolution(
        p_dev_rec->ble.identity_address_with_type);
  } else {
    uint8_t param[20] = {0};
    uint8_t* p = param;

    UINT8_TO_STREAM(p, BTM_BLE_META_REMOVE_IRK_ENTRY);
    UINT8_TO_STREAM(p, p_dev_rec->ble.identity_address_with_type.type);
    BDADDR_TO_STREAM(p, p_dev_rec->ble.identity_address_with_type.bda);

    BTM_VendorSpecificCommand(HCI_VENDOR_BLE_RPA_VSC,
                              BTM_BLE_META_REMOVE_IRK_LEN, param,
                              btm_ble_resolving_list_vsc_op_cmpl);
    btm_ble_enq_resolving_list_pending(p_dev_rec->bd_addr,
                                       BTM_BLE_META_REMOVE_IRK_ENTRY);
  }
  return BTM_CMD_STARTED;
}

/*******************************************************************************
 *
 * Function         btm_ble_clear_resolving_list
 *
 * Description      This function clears the resolving  list
 *
 * Parameters       None.
 *
 ******************************************************************************/
static void btm_ble_clear_resolving_list(void) {
  if (controller_get_interface()->SupportsBlePrivacy()) {
    bluetooth::shim::ACL_ClearAddressResolution();
  } else {
    uint8_t param[20] = {0};
    uint8_t* p = param;

    UINT8_TO_STREAM(p, BTM_BLE_META_CLEAR_IRK_LIST);
    BTM_VendorSpecificCommand(HCI_VENDOR_BLE_RPA_VSC,
                              BTM_BLE_META_CLEAR_IRK_LEN, param,
                              btm_ble_resolving_list_vsc_op_cmpl);
  }
}

/*******************************************************************************
 *
 * Function         btm_ble_read_resolving_list_entry
 *
 * Description      This function read an IRK entry by index
 *
 * Parameters       entry index.
 *
 * Returns          true if command successfully sent, false otherwise
 *
 ******************************************************************************/
bool btm_ble_read_resolving_list_entry(tBTM_SEC_DEV_REC* p_dev_rec) {
  if (btm_cb.ble_ctr_cb.privacy_mode < BTM_PRIVACY_1_2) {
    log::debug("Privacy 1.2 is not enabled");
    return false;
  }
  if (!(p_dev_rec->ble.in_controller_list & BTM_RESOLVING_LIST_BIT)) {
    log::info("Unable to read resolving list entry as resolving bit not set");
    return false;
  }

  if (controller_get_interface()->SupportsBlePrivacy()) {
    btsnd_hcic_ble_read_resolvable_addr_peer(
        p_dev_rec->ble.identity_address_with_type.type,
        p_dev_rec->ble.identity_address_with_type.bda);
  } else {
    uint8_t param[20] = {0};
    uint8_t* p = param;

    UINT8_TO_STREAM(p, BTM_BLE_META_READ_IRK_ENTRY);
    UINT8_TO_STREAM(p, p_dev_rec->ble.resolving_list_index);

    BTM_VendorSpecificCommand(HCI_VENDOR_BLE_RPA_VSC, BTM_BLE_META_READ_IRK_LEN,
                              param, btm_ble_resolving_list_vsc_op_cmpl);

    btm_ble_enq_resolving_list_pending(p_dev_rec->bd_addr,
                                       BTM_BLE_META_READ_IRK_ENTRY);
  }
  return true;
}

static void btm_ble_ble_unsupported_resolving_list_load_dev(
    tBTM_SEC_DEV_REC* p_dev_rec) {
  log::info("Controller does not support BLE privacy");
  uint8_t param[40] = {0};
  uint8_t* p = param;

  UINT8_TO_STREAM(p, BTM_BLE_META_ADD_IRK_ENTRY);
  ARRAY_TO_STREAM(p, p_dev_rec->sec_rec.ble_keys.irk, OCTET16_LEN);
  UINT8_TO_STREAM(p, p_dev_rec->ble.identity_address_with_type.type);
  BDADDR_TO_STREAM(p, p_dev_rec->ble.identity_address_with_type.bda);

  BTM_VendorSpecificCommand(HCI_VENDOR_BLE_RPA_VSC, BTM_BLE_META_ADD_IRK_LEN,
                            param, btm_ble_resolving_list_vsc_op_cmpl);

  btm_ble_enq_resolving_list_pending(p_dev_rec->bd_addr,
                                     BTM_BLE_META_ADD_IRK_ENTRY);
  return;
}

static bool is_peer_identity_key_valid(const tBTM_SEC_DEV_REC& dev_rec) {
  return dev_rec.sec_rec.ble_keys.key_type & BTM_LE_KEY_PID;
}

static Octet16 get_local_irk() { return btm_sec_cb.devcb.id_keys.irk; }

void btm_ble_resolving_list_load_dev(tBTM_SEC_DEV_REC& dev_rec) {
  if (btm_cb.ble_ctr_cb.privacy_mode < BTM_PRIVACY_1_2) {
    log::debug("Privacy 1.2 is not enabled");
    return;
  }
  if (controller_get_interface()->get_ble_resolving_list_max_size() == 0) {
    log::info("Controller does not support RPA offloading or privacy 1.2");
    return;
  }

  if (!controller_get_interface()->SupportsBlePrivacy()) {
    return btm_ble_ble_unsupported_resolving_list_load_dev(&dev_rec);
  }

  // No need to check for local identity key validity. It remains unchanged.
  if (!is_peer_identity_key_valid(dev_rec)) {
    log::info("Peer is not an RPA enabled device:{}",
              ADDRESS_TO_LOGGABLE_CSTR(dev_rec.ble.identity_address_with_type));
    return;
  }

  if (dev_rec.ble.in_controller_list & BTM_RESOLVING_LIST_BIT) {
    log::warn("Already in Address Resolving list device:{}",
              ADDRESS_TO_LOGGABLE_CSTR(dev_rec.ble.identity_address_with_type));
    return;
  }

  const Octet16& peer_irk = dev_rec.sec_rec.ble_keys.irk;
  const Octet16& local_irk = get_local_irk();

  if (dev_rec.ble.identity_address_with_type.bda.IsEmpty()) {
    dev_rec.ble.identity_address_with_type = {
        .type = dev_rec.ble.AddressType(),
        .bda = dev_rec.bd_addr,
    };
  }

  if (!is_ble_addr_type_known(dev_rec.ble.identity_address_with_type.type)) {
    log::error("Adding unknown address type({}) to Address Resolving list.",
               dev_rec.ble.identity_address_with_type.type);
    return;
  }

  bluetooth::shim::ACL_AddToAddressResolution(
      dev_rec.ble.identity_address_with_type, peer_irk, local_irk);

  log::debug("Added to Address Resolving list device:{}",
             ADDRESS_TO_LOGGABLE_CSTR(dev_rec.ble.identity_address_with_type));

  dev_rec.ble.in_controller_list |= BTM_RESOLVING_LIST_BIT;
}

/*******************************************************************************
 *
 * Function         btm_ble_resolving_list_remove_dev
 *
 * Description      This function removes the device from resolving list
 *
 * Parameters
 *
 * Returns          status
 *
 ******************************************************************************/
void btm_ble_resolving_list_remove_dev(tBTM_SEC_DEV_REC* p_dev_rec) {
  if (btm_cb.ble_ctr_cb.privacy_mode < BTM_PRIVACY_1_2) {
    log::debug("Privacy 1.2 is not enabled");
    return;
  }

  if ((p_dev_rec->ble.in_controller_list & BTM_RESOLVING_LIST_BIT) &&
      !btm_ble_brcm_find_resolving_pending_entry(
          p_dev_rec->bd_addr, BTM_BLE_META_REMOVE_IRK_ENTRY)) {
    btm_ble_update_resolving_list(p_dev_rec->bd_addr, false);
    btm_ble_remove_resolving_list_entry(p_dev_rec);
  } else {
    log::verbose("Device not in resolving list");
  }
}

/*******************************************************************************
 *
 * Function         btm_ble_resolving_list_init
 *
 * Description      Initialize resolving list in host stack
 *
 * Parameters       Max resolving list size
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_resolving_list_init(uint8_t max_irk_list_sz) {
  tBTM_BLE_RESOLVE_Q* p_q = &btm_cb.ble_ctr_cb.resolving_list_pend_q;
  uint8_t irk_mask_size =
      (max_irk_list_sz % 8) ? (max_irk_list_sz / 8 + 1) : (max_irk_list_sz / 8);

  if (max_irk_list_sz > 0 && p_q->resolve_q_random_pseudo == nullptr) {
    // NOTE: This memory is never freed
    p_q->resolve_q_random_pseudo =
        (RawAddress*)osi_malloc(sizeof(RawAddress) * max_irk_list_sz);
    // NOTE: This memory is never freed
    p_q->resolve_q_action = (uint8_t*)osi_malloc(max_irk_list_sz);

    /* RPA offloading feature */
    if (btm_cb.ble_ctr_cb.irk_list_mask == NULL)
      // NOTE: This memory is never freed
      btm_cb.ble_ctr_cb.irk_list_mask = (uint8_t*)osi_malloc(irk_mask_size);

    log::verbose("max_irk_list_sz={}", max_irk_list_sz);
  }

  controller_get_interface()->set_ble_resolving_list_max_size(max_irk_list_sz);
  btm_ble_clear_resolving_list();
  btm_cb.ble_ctr_cb.resolving_list_avail_size = max_irk_list_sz;
}
