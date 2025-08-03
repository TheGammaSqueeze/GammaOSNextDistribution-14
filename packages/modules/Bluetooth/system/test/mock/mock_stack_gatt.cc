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
 *   Functions generated:27
 */

#include <cstdint>

#include "stack/gatt/gatt_int.h"
#include "stack/include/gatt_api.h"
#include "test/common/mock_functions.h"
#include "types/bluetooth/uuid.h"

using namespace bluetooth;

tGATT_HDL_LIST_ELEM elem;  // gatt_add_an_item_to_list

tGATT_HDL_LIST_ELEM& gatt_add_an_item_to_list(uint16_t /* s_handle */) {
  inc_func_call_count(__func__);
  return elem;
}
tGATT_STATUS GATTC_Discover(uint16_t /* conn_id */,
                            tGATT_DISC_TYPE /* disc_type */,
                            uint16_t /* start_handle */,
                            uint16_t /* end_handle */, const Uuid& /* uuid */) {
  inc_func_call_count(__func__);
  return GATT_SUCCESS;
}
