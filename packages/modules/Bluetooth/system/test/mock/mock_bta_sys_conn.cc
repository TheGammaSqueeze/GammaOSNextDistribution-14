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
 *   Functions generated:19
 */

#include <cstdint>

#include "bta/sys/bta_sys.h"
#include "test/common/mock_functions.h"
#include "types/hci_role.h"
#include "types/raw_address.h"

void bta_sys_app_close(tBTA_SYS_ID /* id */, uint8_t /* app_id */,
                       const RawAddress& /* peer_addr */) {
  inc_func_call_count(__func__);
}
void bta_sys_app_open(tBTA_SYS_ID /* id */, uint8_t /* app_id */,
                      const RawAddress& /* peer_addr */) {
  inc_func_call_count(__func__);
}
void bta_sys_busy(tBTA_SYS_ID /* id */, uint8_t /* app_id */,
                  const RawAddress& /* peer_addr */) {
  inc_func_call_count(__func__);
}
void bta_sys_chg_ssr_config(tBTA_SYS_ID /* id */, uint8_t /* app_id */,
                            uint16_t /* max_latency */,
                            uint16_t /* min_tout */) {
  inc_func_call_count(__func__);
}
void bta_sys_collision_register(tBTA_SYS_ID /* bta_id */,
                                tBTA_SYS_CONN_CBACK* /* p_cback */) {
  inc_func_call_count(__func__);
}
void bta_sys_conn_close(tBTA_SYS_ID /* id */, uint8_t /* app_id */,
                        const RawAddress& /* peer_addr */) {
  inc_func_call_count(__func__);
}
void bta_sys_conn_open(tBTA_SYS_ID /* id */, uint8_t /* app_id */,
                       const RawAddress& /* peer_addr */) {
  inc_func_call_count(__func__);
}
void bta_sys_idle(tBTA_SYS_ID /* id */, uint8_t /* app_id */,
                  const RawAddress& /* peer_addr */) {
  inc_func_call_count(__func__);
}
void bta_sys_notify_collision(const RawAddress& /* peer_addr */) {
  inc_func_call_count(__func__);
}
void bta_sys_notify_role_chg(const RawAddress& /* peer_addr */,
                             tHCI_ROLE /* new_role */,
                             tHCI_STATUS /* hci_status */) {
  inc_func_call_count(__func__);
}
void bta_sys_pm_register(tBTA_SYS_CONN_CBACK* /* p_cback */) {
  inc_func_call_count(__func__);
}
void bta_sys_sniff_register(tBTA_SYS_SNIFF_CBACK* /* p_cback */) {
  inc_func_call_count(__func__);
}
void bta_sys_reset_sniff(uint8_t /* id */, uint8_t /* app_id */,
                         const RawAddress& /* peer_addr */) {
  inc_func_call_count(__func__);
}
void bta_sys_rm_register(tBTA_SYS_CONN_CBACK* /* p_cback */) {
  inc_func_call_count(__func__);
}
void bta_sys_role_chg_register(tBTA_SYS_CONN_CBACK* /* p_cback */) {
  inc_func_call_count(__func__);
}
void bta_sys_sco_close(tBTA_SYS_ID /* id */, uint8_t /* app_id */,
                       const RawAddress& /* peer_addr */) {
  inc_func_call_count(__func__);
}
void bta_sys_sco_open(tBTA_SYS_ID /* id */, uint8_t /* app_id */,
                      const RawAddress& /* peer_addr */) {
  inc_func_call_count(__func__);
}
void bta_sys_sco_register(tBTA_SYS_CONN_SCO_CBACK* /* p_cback */) {
  inc_func_call_count(__func__);
}
void bta_sys_sco_unuse(tBTA_SYS_ID /* id */, uint8_t /* app_id */,
                       const RawAddress& /* peer_addr */) {
  inc_func_call_count(__func__);
}
void bta_sys_sco_use(tBTA_SYS_ID /* id */, uint8_t /* app_id */,
                     const RawAddress& /* peer_addr */) {
  inc_func_call_count(__func__);
}
void bta_sys_ssr_cfg_register(tBTA_SYS_SSR_CFG_CBACK* /* p_cback */) {
  inc_func_call_count(__func__);
}
void bta_sys_eir_register(tBTA_SYS_EIR_CBACK* /* p_cback */) {
  inc_func_call_count(__func__);
}
void bta_sys_eir_unregister() { inc_func_call_count(__func__); }
void bta_sys_cust_eir_register(tBTA_SYS_CUST_EIR_CBACK* /* p_cback */) {
  inc_func_call_count(__func__);
}
void bta_sys_add_uuid(uint16_t /* uuid16 */) { inc_func_call_count(__func__); }
void bta_sys_remove_uuid(uint16_t /* uuid16 */) {
  inc_func_call_count(__func__);
}
void bta_sys_add_cust_uuid(const tBTA_CUSTOM_UUID& /* curr */) {
  inc_func_call_count(__func__);
}
void bta_sys_remove_cust_uuid(const tBTA_CUSTOM_UUID& /* curr */) {
  inc_func_call_count(__func__);
}
