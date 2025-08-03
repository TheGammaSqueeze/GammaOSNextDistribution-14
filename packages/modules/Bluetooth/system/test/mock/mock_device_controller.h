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
 *   Functions generated:1
 *
 *  mockcify.pl ver 0.2
 */

#include <cstddef>
#include <cstdint>

// Original included files, if any

#include "btcore/include/device_features.h"

// Mocked compile conditionals, if any
namespace test {
namespace mock {
namespace device_controller {

constexpr size_t HCI_SUPPORTED_COMMANDS_ARRAY_SIZE = 64;
constexpr size_t MAX_FEATURES_CLASSIC_PAGE_COUNT = 3;
constexpr size_t BLE_SUPPORTED_STATES_SIZE = 8;
constexpr size_t MAX_LOCAL_SUPPORTED_CODECS_SIZE = 8;

// Shared state between mocked functions and tests
extern uint8_t supported_commands[HCI_SUPPORTED_COMMANDS_ARRAY_SIZE];
extern bt_device_features_t features_classic[MAX_FEATURES_CLASSIC_PAGE_COUNT];
extern uint8_t last_features_classic_page_index;

extern uint16_t acl_data_size_classic;
extern uint16_t acl_data_size_ble;
extern uint16_t iso_data_size;

extern uint16_t acl_buffer_count_classic;
extern uint8_t acl_buffer_count_ble;
extern uint8_t iso_buffer_count;

extern uint8_t ble_acceptlist_size;
extern uint8_t ble_resolving_list_max_size;
extern uint8_t ble_supported_states[BLE_SUPPORTED_STATES_SIZE];
extern bt_device_features_t features_ble;
extern uint16_t ble_suggested_default_data_length;
extern uint16_t ble_supported_max_tx_octets;
extern uint16_t ble_supported_max_tx_time;
extern uint16_t ble_supported_max_rx_octets;
extern uint16_t ble_supported_max_rx_time;

extern uint16_t ble_maxium_advertising_data_length;
extern uint8_t ble_number_of_supported_advertising_sets;
extern uint8_t ble_periodic_advertiser_list_size;
extern uint8_t local_supported_codecs[MAX_LOCAL_SUPPORTED_CODECS_SIZE];
extern uint8_t number_of_local_supported_codecs;

extern bool readable;
extern bool ble_supported;
extern bool iso_supported;
extern bool simple_pairing_supported;
extern bool secure_connections_supported;

}  // namespace device_controller
}  // namespace mock
}  // namespace test

// END mockcify generation
