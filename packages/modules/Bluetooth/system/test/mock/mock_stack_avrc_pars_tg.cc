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
 *   Functions generated:4
 */

#include "avrc_api.h"
#include "avrc_defs.h"
#include "test/common/mock_functions.h"

tAVRC_STS AVRC_Ctrl_ParsCommand(tAVRC_MSG* /* p_msg */,
                                tAVRC_COMMAND* /* p_result */) {
  inc_func_call_count(__func__);
  return 0;
}
tAVRC_STS AVRC_ParsCommand(tAVRC_MSG* /* p_msg */,
                           tAVRC_COMMAND* /* p_result */, uint8_t* /* p_buf */,
                           uint16_t /* buf_len */) {
  inc_func_call_count(__func__);
  return 0;
}
