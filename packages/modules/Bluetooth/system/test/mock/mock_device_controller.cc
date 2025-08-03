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
// Mock include file to share data between tests and mock
#include "test/mock/mock_device_controller.h"

// Original included files, if any
#include "btcore/include/version.h"
#include "device/include/controller.h"
#include "stack/include/btm_api_types.h"
#include "stack/include/btm_status.h"
#include "stack/include/hcidefs.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any
// Mocked internal structures, if any
namespace test {
namespace mock {
namespace device_controller {

RawAddress address;
bt_version_t bt_version = {
    .hci_version = 0,
    .hci_revision = 0,
    .lmp_version = 0,
    .manufacturer = 0,
    .lmp_subversion = 0,
};

uint8_t supported_commands[HCI_SUPPORTED_COMMANDS_ARRAY_SIZE]{0};
bt_device_features_t features_classic[MAX_FEATURES_CLASSIC_PAGE_COUNT] = {{
    .as_array{0},
}};
uint8_t last_features_classic_page_index{0};

uint16_t acl_data_size_classic{0};
uint16_t acl_data_size_ble{0};
uint16_t iso_data_size{0};

uint16_t acl_buffer_count_classic{0};
uint8_t acl_buffer_count_ble{0};
uint8_t iso_buffer_count{0};

uint8_t ble_acceptlist_size{0};
uint8_t ble_resolving_list_max_size{0};
uint8_t ble_supported_states[BLE_SUPPORTED_STATES_SIZE]{0};
bt_device_features_t features_ble{0};
uint16_t ble_suggested_default_data_length{0};
uint16_t ble_supported_max_tx_octets{0};
uint16_t ble_supported_max_tx_time{0};
uint16_t ble_supported_max_rx_octets{0};
uint16_t ble_supported_max_rx_time{0};

uint16_t ble_maximum_advertising_data_length{0};
uint8_t ble_number_of_supported_advertising_sets{0};
uint8_t ble_periodic_advertiser_list_size{0};
uint8_t local_supported_codecs[MAX_LOCAL_SUPPORTED_CODECS_SIZE]{0};
uint8_t number_of_local_supported_codecs{0};

bool readable{false};
bool ble_supported{false};
bool iso_supported{false};
bool simple_pairing_supported{false};
bool secure_connections_supported{false};
bool supports_hold_mode{false};
bool supports_sniff_mode{true};
bool supports_park_mode{false};

bool get_is_ready(void) { return readable; }

const RawAddress* get_address(void) { return &address; }

const bt_version_t* get_bt_version(void) { return &bt_version; }

uint8_t* get_local_supported_codecs(uint8_t* number_of_codecs) {
  if (number_of_local_supported_codecs) {
    *number_of_codecs = number_of_local_supported_codecs;
    return local_supported_codecs;
  }
  return NULL;
}

const uint8_t* get_ble_supported_states(void) { return ble_supported_states; }

bool supports_enhanced_setup_synchronous_connection(void) {
  return HCI_ENH_SETUP_SYNCH_CONN_SUPPORTED(supported_commands);
}

bool supports_enhanced_accept_synchronous_connection(void) {
  return HCI_ENH_ACCEPT_SYNCH_CONN_SUPPORTED(supported_commands);
}

bool supports_configure_data_path(void) {
  return HCI_CONFIGURE_DATA_PATH_SUPPORTED(supported_commands);
}

#define HCI_SET_MIN_ENCRYPTION_KEY_SIZE_SUPPORTED(x) ((x)[45] & 0x80)
bool supports_set_min_encryption_key_size(void) {
  return HCI_SET_MIN_ENCRYPTION_KEY_SIZE_SUPPORTED(supported_commands);
}

#define HCI_READ_ENCRYPTION_KEY_SIZE_SUPPORTED(x) ((x)[20] & 0x10)
bool supports_read_encryption_key_size(void) {
  return HCI_READ_ENCRYPTION_KEY_SIZE_SUPPORTED(supported_commands);
}

bool supports_ble(void) { return ble_supported; }

bool supports_ble_privacy(void) {
  return HCI_LE_ENHANCED_PRIVACY_SUPPORTED(features_ble.as_array);
}

bool supports_ble_set_privacy_mode() {
  return HCI_LE_ENHANCED_PRIVACY_SUPPORTED(features_ble.as_array) &&
         HCI_LE_SET_PRIVACY_MODE_SUPPORTED(supported_commands);
}

bool supports_ble_packet_extension(void) {
  return HCI_LE_DATA_LEN_EXT_SUPPORTED(features_ble.as_array);
}

bool supports_ble_connection_parameters_request(void) {
  return HCI_LE_CONN_PARAM_REQ_SUPPORTED(features_ble.as_array);
}

bool supports_ble_2m_phy(void) {
  return HCI_LE_2M_PHY_SUPPORTED(features_ble.as_array);
}

bool supports_ble_coded_phy(void) {
  return HCI_LE_CODED_PHY_SUPPORTED(features_ble.as_array);
}

bool supports_ble_extended_advertising(void) {
  return HCI_LE_EXTENDED_ADVERTISING_SUPPORTED(features_ble.as_array);
}

bool supports_ble_periodic_advertising(void) {
  return HCI_LE_PERIODIC_ADVERTISING_SUPPORTED(features_ble.as_array);
}

bool supports_ble_peripheral_initiated_feature_exchange(void) {
  return HCI_LE_PERIPHERAL_INIT_FEAT_EXC_SUPPORTED(features_ble.as_array);
}

bool supports_ble_periodic_advertising_sync_transfer_sender(void) {
  return HCI_LE_PERIODIC_ADVERTISING_SYNC_TRANSFER_SENDER(
      features_ble.as_array);
}

bool supports_ble_periodic_advertising_sync_transfer_recipient(void) {
  return HCI_LE_PERIODIC_ADVERTISING_SYNC_TRANSFER_RECIPIENT(
      features_ble.as_array);
}

bool supports_ble_connected_isochronous_stream_central(void) {
  return HCI_LE_CIS_CENTRAL(features_ble.as_array);
}

bool supports_ble_connected_isochronous_stream_peripheral(void) {
  return HCI_LE_CIS_PERIPHERAL(features_ble.as_array);
}

bool supports_ble_isochronous_broadcaster(void) {
  return HCI_LE_ISO_BROADCASTER(features_ble.as_array);
}

bool supports_ble_synchronized_receiver(void) {
  return HCI_LE_SYNCHRONIZED_RECEIVER(features_ble.as_array);
}

bool supports_ble_connection_subrating(void) {
  return HCI_LE_CONN_SUBRATING_SUPPORT(features_ble.as_array);
}

bool supports_ble_connection_subrating_host(void) {
  return HCI_LE_CONN_SUBRATING_HOST_SUPPORT(features_ble.as_array);
}

uint16_t get_acl_data_size_classic(void) { return acl_data_size_classic; }

uint16_t get_acl_data_size_ble(void) { return acl_data_size_ble; }

uint16_t get_iso_data_size(void) { return iso_data_size; }

uint16_t get_acl_packet_size_classic(void) {
  return acl_data_size_classic + HCI_DATA_PREAMBLE_SIZE;
}

uint16_t get_acl_packet_size_ble(void) {
  return acl_data_size_ble + HCI_DATA_PREAMBLE_SIZE;
}

uint16_t get_iso_packet_size(void) {
  return iso_data_size + HCI_DATA_PREAMBLE_SIZE;
}

uint16_t get_ble_suggested_default_data_length(void) {
  return ble_suggested_default_data_length;
}

uint16_t get_ble_maximum_tx_data_length(void) {
  return ble_supported_max_tx_octets;
}

uint16_t get_ble_maximum_tx_time(void) { return ble_supported_max_tx_time; }

uint16_t get_ble_maximum_advertising_data_length(void) {
  return ble_maximum_advertising_data_length;
}

uint8_t get_ble_number_of_supported_advertising_sets(void) {
  return ble_number_of_supported_advertising_sets;
}

uint8_t get_ble_periodic_advertiser_list_size(void) {
  return ble_periodic_advertiser_list_size;
}

uint16_t get_acl_buffer_count_classic(void) { return acl_buffer_count_classic; }

uint8_t get_acl_buffer_count_ble(void) { return acl_buffer_count_ble; }

uint8_t get_iso_buffer_count(void) { return iso_buffer_count; }

uint8_t get_ble_acceptlist_size(void) { return ble_acceptlist_size; }

uint8_t get_ble_resolving_list_max_size(void) {
  return ble_resolving_list_max_size;
}

void set_ble_resolving_list_max_size(int resolving_list_max_size) {
  ble_resolving_list_max_size = resolving_list_max_size;
}

uint8_t get_le_all_initiating_phys() {
  uint8_t phy = PHY_LE_1M;
  return phy;
}

tBTM_STATUS clear_event_filter() { return BTM_SUCCESS; }

tBTM_STATUS clear_event_mask() { return BTM_SUCCESS; }

tBTM_STATUS le_rand(LeRandCallback /* cb */) { return BTM_SUCCESS; }
tBTM_STATUS set_event_filter_connection_setup_all_devices() {
  return BTM_SUCCESS;
}
tBTM_STATUS set_event_filter_allow_device_connection(
    std::vector<RawAddress> /* devices */) {
  return BTM_SUCCESS;
}
tBTM_STATUS set_default_event_mask_except(uint64_t /* mask */,
                                          uint64_t /* le_mask */) {
  return BTM_SUCCESS;
}
tBTM_STATUS set_event_filter_inquiry_result_all_devices() {
  return BTM_SUCCESS;
}

const controller_t interface = {
    get_is_ready,

    get_address,
    get_bt_version,

    get_ble_supported_states,

    supports_enhanced_setup_synchronous_connection,
    supports_enhanced_accept_synchronous_connection,
    supports_configure_data_path,
    supports_set_min_encryption_key_size,
    supports_read_encryption_key_size,

    supports_ble,
    supports_ble_packet_extension,
    supports_ble_connection_parameters_request,
    supports_ble_privacy,
    supports_ble_set_privacy_mode,
    supports_ble_2m_phy,
    supports_ble_coded_phy,
    supports_ble_extended_advertising,
    supports_ble_periodic_advertising,
    supports_ble_peripheral_initiated_feature_exchange,
    supports_ble_periodic_advertising_sync_transfer_sender,
    supports_ble_periodic_advertising_sync_transfer_recipient,
    supports_ble_connected_isochronous_stream_central,
    supports_ble_connected_isochronous_stream_peripheral,
    supports_ble_isochronous_broadcaster,
    supports_ble_synchronized_receiver,
    supports_ble_connection_subrating,
    supports_ble_connection_subrating_host,

    get_acl_data_size_classic,
    get_acl_data_size_ble,
    get_iso_data_size,

    get_acl_packet_size_classic,
    get_acl_packet_size_ble,
    get_iso_packet_size,

    get_ble_suggested_default_data_length,
    get_ble_maximum_tx_data_length,
    get_ble_maximum_tx_time,
    get_ble_maximum_advertising_data_length,
    get_ble_number_of_supported_advertising_sets,
    get_ble_periodic_advertiser_list_size,

    get_acl_buffer_count_classic,
    get_acl_buffer_count_ble,
    get_iso_buffer_count,

    get_ble_acceptlist_size,

    get_ble_resolving_list_max_size,
    set_ble_resolving_list_max_size,
    get_local_supported_codecs,
    get_le_all_initiating_phys,
    clear_event_filter,
    clear_event_mask,
    le_rand,
    set_event_filter_connection_setup_all_devices,
    set_event_filter_allow_device_connection,
    set_default_event_mask_except,
    set_event_filter_inquiry_result_all_devices,
};

}  // namespace device_controller
}  // namespace mock
}  // namespace test

// Mocked functions, if any
const controller_t* controller_get_interface() {
  return &test::mock::device_controller::interface;
}

// END mockcify generation
