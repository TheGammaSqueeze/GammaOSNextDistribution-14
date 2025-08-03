/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <memory>

#include "rust/cxx.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace topshim {
namespace rust {

void adapter_state_changed(uint32_t state);
void bond_state_changed(
    RawAddress addr, uint32_t device_type, uint32_t status, uint32_t bond_state, int32_t fail_reason);
void bond_create_attempt(RawAddress addr, uint32_t device_type);
void device_info_report(
    RawAddress addr,
    uint32_t device_type,
    uint32_t class_of_device,
    uint32_t appearance,
    uint32_t vendor_id,
    uint32_t vendor_id_src,
    uint32_t product_id,
    uint32_t version);
void profile_connection_state_changed(RawAddress addr, uint32_t profile, uint32_t status, uint32_t state);
void acl_connect_attempt(RawAddress addr, uint32_t acl_state);
void acl_connection_state_changed(
    RawAddress addr, uint32_t transport, uint32_t status, uint32_t acl_state, uint32_t direction, uint32_t hci_reason);
void suspend_complete_state(uint32_t state);

}  // namespace rust
}  // namespace topshim
}  // namespace bluetooth
