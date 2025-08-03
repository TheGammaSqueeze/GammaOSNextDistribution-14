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
 *   Functions generated:21
 */
#include "test/mock/mock_stack_btm_devctl.h"

#include <base/logging.h>
#include <stddef.h>
#include <stdlib.h>

#include "bt_dev_class.h"
#include "stack/include/btm_api_types.h"
#include "stack/include/btm_status.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

namespace test {
namespace mock {
namespace stack_btm_devctl {

struct BTM_IsDeviceUp BTM_IsDeviceUp;

}
}  // namespace mock
}  // namespace test

bool BTM_IsDeviceUp(void) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_devctl::BTM_IsDeviceUp();
}

tBTM_STATUS BTM_BT_Quality_Report_VSE_Register(
    bool /* is_register */,
    tBTM_BT_QUALITY_REPORT_RECEIVER* /* p_bqr_report_receiver */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
tBTM_STATUS BTM_DeleteStoredLinkKey(const RawAddress* /* bd_addr */,
                                    tBTM_CMPL_CB* /* p_cb */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
tBTM_STATUS BTM_EnableTestMode(void) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
tBTM_STATUS BTM_ReadLocalDeviceName(const char** /* p_name */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
tBTM_STATUS BTM_ReadLocalDeviceNameFromController(
    tBTM_CMPL_CB* /* p_rln_cmpl_cback */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
tBTM_STATUS BTM_RegisterForVSEvents(tBTM_VS_EVT_CB* /* p_cb */,
                                    bool /* is_register */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
tBTM_STATUS BTM_SetDeviceClass(DEV_CLASS /* dev_class */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
tBTM_STATUS BTM_SetLocalDeviceName(const char* /* p_name */) {
  inc_func_call_count(__func__);
  return BTM_SUCCESS;
}
DEV_CLASS BTM_ReadDeviceClass(void) {
  inc_func_call_count(__func__);
  return kDevClassEmpty;
}
void BTM_VendorSpecificCommand(uint16_t /* opcode */, uint8_t /* param_len */,
                               uint8_t* /* p_param_buf */,
                               tBTM_VSC_CMPL_CB* /* p_cb */) {
  inc_func_call_count(__func__);
}
void BTM_WritePageTimeout(uint16_t /* timeout */) {
  inc_func_call_count(__func__);
}
void BTM_WriteVoiceSettings(uint16_t /* settings */) {
  inc_func_call_count(__func__);
}
void BTM_db_reset(void) { inc_func_call_count(__func__); }
void BTM_reset_complete() { inc_func_call_count(__func__); }
void btm_delete_stored_link_key_complete(uint8_t* /* p */,
                                         uint16_t /* evt_len */) {
  inc_func_call_count(__func__);
}
void btm_dev_free() { inc_func_call_count(__func__); }
void btm_dev_init() { inc_func_call_count(__func__); }
void btm_read_local_name_complete(uint8_t* /* p */, uint16_t /* evt_len */) {
  inc_func_call_count(__func__);
}
void btm_vendor_specific_evt(const uint8_t* /* p */, uint8_t /* evt_len */) {
  inc_func_call_count(__func__);
}
void btm_vsc_complete(uint8_t* /* p */, uint16_t /* opcode */,
                      uint16_t /* evt_len */,
                      tBTM_VSC_CMPL_CB* /* p_vsc_cplt_cback */) {
  inc_func_call_count(__func__);
}
