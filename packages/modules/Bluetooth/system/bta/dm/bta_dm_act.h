/*
 * Copyright 2023 The Android Open Source Project
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

#pragma once

#include "bta/dm/bta_dm_int.h"
#include "osi/include/osi.h"
#include "types/bt_transport.h"
#include "types/raw_address.h"

void bta_dm_process_remove_device_no_callback(const RawAddress& bd_addr);

tBTA_DM_PEER_DEVICE* find_connected_device(const RawAddress& bd_addr,
                                           UNUSED_ATTR tBT_TRANSPORT transport);
