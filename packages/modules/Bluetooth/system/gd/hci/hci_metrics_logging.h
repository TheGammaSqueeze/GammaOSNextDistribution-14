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

#pragma once
#include <frameworks/proto_logging/stats/enums/bluetooth/enums.pb.h>

#include "hci/hci_packets.h"
#include "storage/storage_module.h"

namespace bluetooth {
namespace hci {
void log_hci_event(
    std::unique_ptr<CommandView>& command_view, EventView packet, storage::StorageModule* storage_module);
void log_link_layer_connection_command(std::unique_ptr<CommandView>& command_view);
void log_link_layer_connection_command_status(std::unique_ptr<CommandView>& command_view, ErrorCode status);
void log_link_layer_connection_command_complete(EventView event, std::unique_ptr<CommandView>& command_view);
void log_link_layer_connection_event_le_meta(LeMetaEventView le_meta_event_view);
void log_link_layer_connection_other_hci_event(EventView packet, storage::StorageModule* storage_module);

void log_classic_pairing_command_status(std::unique_ptr<CommandView>& command_view, ErrorCode status);
void log_classic_pairing_command_complete(EventView event, std::unique_ptr<CommandView>& command_view);
void log_classic_pairing_other_hci_event(EventView packet);

void log_remote_device_information(
    const Address& address,
    android::bluetooth::AddressTypeEnum address_type,
    uint32_t connection_handle,
    ErrorCode status,
    storage::StorageModule* storage_module);
}  // namespace hci
}  // namespace bluetooth