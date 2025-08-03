/******************************************************************************
 *
 *  Copyright 2020 The Android Open Source Project
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

#include "mock_btif_storage.h"

#include "stack/include/btm_sec_api_types.h"

static bluetooth::manager::MockBtifStorageInterface* btif_storage_interface =
    nullptr;

void bluetooth::manager::SetMockBtifStorageInterface(
    MockBtifStorageInterface* mock_btif_storage_interface) {
  btif_storage_interface = mock_btif_storage_interface;
}

void btif_storage_load_bonded_eatt(void) {
  btif_storage_interface->LoadBondedEatt();
}

tBTM_IO_CAP btif_storage_get_local_io_caps() { return BTM_IO_CAP_UNKNOWN; }
