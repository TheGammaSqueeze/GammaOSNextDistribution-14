/*
 * Copyright 2021 The Android Open Source Project
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

/*
 * Generated mock file from original source file
 *   Functions generated:23
 */

#include "l2c_api.h"
#include "stack/gatt/gatt_int.h"
#include "stack/include/bt_hdr.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

void gatt_init(void) { inc_func_call_count(__func__); }
bool gatt_act_connect(tGATT_REG* /* p_reg */, const RawAddress& /* bd_addr */,
                      tBLE_ADDR_TYPE /* addr_type */,
                      tBT_TRANSPORT /* transport */,
                      int8_t /* initiating_phys */) {
  inc_func_call_count(__func__);
  return false;
}
bool gatt_connect(const RawAddress& /* rem_bda */, tGATT_TCB* /* p_tcb */,
                  tBLE_ADDR_TYPE /* addr_type */, tBT_TRANSPORT /* transport */,
                  uint8_t /* initiating_phys */, tGATT_IF /* gatt_if */) {
  inc_func_call_count(__func__);
  return false;
}
bool gatt_disconnect(tGATT_TCB* /* p_tcb */) {
  inc_func_call_count(__func__);
  return false;
}
bool gatt_update_app_hold_link_status(tGATT_IF /* gatt_if */,
                                      tGATT_TCB* /* p_tcb */,
                                      bool /* is_add */) {
  inc_func_call_count(__func__);
  return false;
}
tGATT_CH_STATE gatt_get_ch_state(tGATT_TCB* /* p_tcb */) {
  inc_func_call_count(__func__);
  return GATT_CH_CLOSE;
}
void gatt_add_a_bonded_dev_for_srv_chg(const RawAddress& /* bda */) {
  inc_func_call_count(__func__);
}
void gatt_chk_srv_chg(tGATTS_SRV_CHG* /* p_srv_chg_clt */) {
  inc_func_call_count(__func__);
}
void gatt_data_process(tGATT_TCB& /* tcb */, uint16_t /* cid */,
                       BT_HDR* /* p_buf */) {
  inc_func_call_count(__func__);
}
void gatt_consolidate(const RawAddress& /* identity_addr */,
                      const RawAddress& /* rpa */) {
  inc_func_call_count(__func__);
}
void gatt_free(void) { inc_func_call_count(__func__); }
void gatt_init_srv_chg(void) { inc_func_call_count(__func__); }
void gatt_l2cif_config_cfm_cback(uint16_t /* lcid */, uint16_t /* initiator */,
                                 tL2CAP_CFG_INFO* /* p_cfg */) {
  inc_func_call_count(__func__);
}
void gatt_l2cif_config_ind_cback(uint16_t /* lcid */,
                                 tL2CAP_CFG_INFO* /* p_cfg */) {
  inc_func_call_count(__func__);
}
void gatt_l2cif_disconnect_ind_cback(uint16_t /* lcid */,
                                     bool /* ack_needed */) {
  inc_func_call_count(__func__);
}
void gatt_notify_conn_update(const RawAddress& /* remote */,
                             uint16_t /* interval */, uint16_t /* latency */,
                             uint16_t /* timeout */, tHCI_STATUS /* status */) {
  inc_func_call_count(__func__);
}
void gatt_notify_phy_updated(tHCI_STATUS /* status */, uint16_t /* handle */,
                             uint8_t /* tx_phy */, uint8_t /* rx_phy */) {
  inc_func_call_count(__func__);
}
void gatt_notify_subrate_change(uint16_t /* handle */,
                                uint16_t /* subrate_factor */,
                                uint16_t /* latency */, uint16_t /* cont_num */,
                                uint16_t /* timeout */, uint8_t /* status */) {
  inc_func_call_count(__func__);
}
void gatt_proc_srv_chg(void) { inc_func_call_count(__func__); }
void gatt_send_srv_chg_ind(const RawAddress& /* peer_bda */) {
  inc_func_call_count(__func__);
}
void gatt_set_ch_state(tGATT_TCB* /* p_tcb */, tGATT_CH_STATE /* ch_state */) {
  inc_func_call_count(__func__);
}
void gatt_update_app_use_link_flag(tGATT_IF /* gatt_if */,
                                   tGATT_TCB* /* p_tcb */, bool /* is_add */,
                                   bool /* check_acl_link */) {
  inc_func_call_count(__func__);
}

void gatt_tcb_dump(int fd) {
  inc_func_call_count(__func__);
}
