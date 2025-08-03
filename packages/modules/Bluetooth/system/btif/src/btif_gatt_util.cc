/******************************************************************************
 *
 *  Copyright 2009-2013 Broadcom Corporation
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

#define LOG_TAG "bt_btif_gatt"

#include <algorithm>

#include "btif_gatt_util.h"

#include <bluetooth/log.h>
#include <hardware/bluetooth.h>
#include <hardware/bt_gatt.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "bta/include/bta_api_data_types.h"
#include "bta/include/bta_sec_api.h"
#include "btif_storage.h"
#include "common/init_flags.h"
#include "os/log.h"
#include "os/system_properties.h"
#include "osi/include/allocator.h"
#include "stack/btm/btm_sec.h"
#include "stack/include/acl_api.h"
#include "types/ble_address_with_type.h"
#include "types/bluetooth/uuid.h"
#include "types/bt_transport.h"
#include "types/raw_address.h"

using bluetooth::Uuid;
using namespace bluetooth;

/*******************************************************************************
 * BTIF -> BTA conversion functions
 ******************************************************************************/
void btif_to_bta_response(tGATTS_RSP* p_dest, btgatt_response_t* p_src) {
  p_dest->attr_value.auth_req = p_src->attr_value.auth_req;
  p_dest->attr_value.handle = p_src->attr_value.handle;
  p_dest->attr_value.len = std::min<uint16_t>(p_src->attr_value.len, GATT_MAX_ATTR_LEN);
  p_dest->attr_value.offset = p_src->attr_value.offset;
  memcpy(p_dest->attr_value.value, p_src->attr_value.value, p_dest->attr_value.len);
}

/*******************************************************************************
 * Encrypted link map handling
 ******************************************************************************/

static bool btif_gatt_is_link_encrypted(const RawAddress& bd_addr) {
  return BTM_IsEncrypted(bd_addr, BT_TRANSPORT_BR_EDR) ||
         BTM_IsEncrypted(bd_addr, BT_TRANSPORT_LE);
}

static void btif_gatt_set_encryption_cb(const RawAddress& /* bd_addr */,
                                        tBT_TRANSPORT /* transport */,
                                        tBTA_STATUS result) {
  if (result != BTA_SUCCESS && result != BTA_BUSY) {
    log::warn("Encryption failed ({})", result);
  }
}

void btif_gatt_check_encrypted_link(RawAddress bd_addr,
                                    tBT_TRANSPORT transport_link) {
  RawAddress raw_local_addr;
  tBLE_ADDR_TYPE local_addr_type;
  BTM_ReadConnectionAddr(bd_addr, raw_local_addr, &local_addr_type);
  tBLE_BD_ADDR local_addr{local_addr_type, raw_local_addr};
  if (!local_addr.IsPublic() && !local_addr.IsAddressResolvable()) {
    log::debug("Not establishing encryption since address type is NRPA");
    return;
  }

  static const bool check_encrypted = bluetooth::os::GetSystemPropertyBool(
      "bluetooth.gatt.check_encrypted_link.enabled", true);
  if (!check_encrypted) {
    log::debug("Check skipped due to system config");
    return;
  }
  tBTM_LE_PENC_KEYS key;
  if ((btif_storage_get_ble_bonding_key(
           bd_addr, BTM_LE_KEY_PENC, (uint8_t*)&key,
           sizeof(tBTM_LE_PENC_KEYS)) == BT_STATUS_SUCCESS) &&
      !btif_gatt_is_link_encrypted(bd_addr)) {
    log::debug("Checking gatt link peer:{} transport:{}",
               ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
               bt_transport_text(transport_link));
    BTA_DmSetEncryption(bd_addr, transport_link, &btif_gatt_set_encryption_cb,
                        BTM_BLE_SEC_ENCRYPT);
  }
}

void btif_gatt_move_track_adv_data(btgatt_track_adv_info_t* p_dest,
                                   btgatt_track_adv_info_t* p_src) {
  memset(p_dest, 0, sizeof(btgatt_track_adv_info_t));

  memcpy(p_dest, p_src, sizeof(btgatt_track_adv_info_t));

  if (p_src->adv_pkt_len > 0) {
    p_dest->p_adv_pkt_data = (uint8_t*)osi_malloc(p_src->adv_pkt_len);
    memcpy(p_dest->p_adv_pkt_data, p_src->p_adv_pkt_data, p_src->adv_pkt_len);
    osi_free_and_reset((void**)&p_src->p_adv_pkt_data);
  }

  if (p_src->scan_rsp_len > 0) {
    p_dest->p_scan_rsp_data = (uint8_t*)osi_malloc(p_src->scan_rsp_len);
    memcpy(p_dest->p_scan_rsp_data, p_src->p_scan_rsp_data,
           p_src->scan_rsp_len);
    osi_free_and_reset((void**)&p_src->p_scan_rsp_data);
  }
}
