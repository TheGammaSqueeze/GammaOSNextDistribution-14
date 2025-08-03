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
 *   Functions generated:12
 */

#include <cstdint>

#include "test/common/mock_functions.h"
#include "udrv/include/uipc.h"

std::unique_ptr<tUIPC_STATE> mock_uipc_init_ret;
uint32_t mock_uipc_read_ret;
bool mock_uipc_send_ret;

bool UIPC_Open(tUIPC_STATE& uipc, tUIPC_CH_ID ch_id, tUIPC_RCV_CBACK* p_cback,
               const char* socket_path) {
  inc_func_call_count(__func__);
  return false;
}
bool UIPC_Send(tUIPC_STATE& uipc, tUIPC_CH_ID ch_id, uint16_t msg_evt,
               const uint8_t* p_buf, uint16_t msglen) {
  inc_func_call_count(__func__);
  return mock_uipc_send_ret;
}
int uipc_start_main_server_thread(tUIPC_STATE& uipc) {
  inc_func_call_count(__func__);
  return 0;
}
std::unique_ptr<tUIPC_STATE> UIPC_Init() {
  inc_func_call_count(__func__);
  return std::move(mock_uipc_init_ret);
}
const char* dump_uipc_event(tUIPC_EVENT event) {
  inc_func_call_count(__func__);
  return nullptr;
}
uint32_t UIPC_Read(tUIPC_STATE& uipc, tUIPC_CH_ID ch_id, uint8_t* p_buf,
                   uint32_t len) {
  inc_func_call_count(__func__);
  return mock_uipc_read_ret;
}
bool UIPC_Ioctl(tUIPC_STATE& uipc, tUIPC_CH_ID ch_id, uint32_t request,
                void* param) {
  inc_func_call_count(__func__);
  return false;
}
void UIPC_Close(tUIPC_STATE& uipc, tUIPC_CH_ID ch_id) {
  inc_func_call_count(__func__);
}
void uipc_close_locked(tUIPC_STATE& uipc, tUIPC_CH_ID ch_id) {
  inc_func_call_count(__func__);
}
void uipc_main_cleanup(tUIPC_STATE& uipc) { inc_func_call_count(__func__); }
void uipc_stop_main_server_thread(tUIPC_STATE& uipc) {
  inc_func_call_count(__func__);
}
