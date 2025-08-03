/******************************************************************************
 *
 *  Copyright 2014 Google, Inc.
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

#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

#include "base/functional/callback.h"
#include "btcore/include/version.h"
#include "types/raw_address.h"

using LeRandCallback = base::OnceCallback<void(uint64_t)>;

typedef struct controller_t {
  bool (*get_is_ready)(void);

  const RawAddress* (*get_address)(void);
  const bt_version_t* (*get_bt_version)(void);

  const uint8_t* (*get_ble_supported_states)(void);

  bool (*supports_enhanced_setup_synchronous_connection)(void);
  bool (*supports_enhanced_accept_synchronous_connection)(void);
  bool (*supports_configure_data_path)(void);
  bool (*supports_set_min_encryption_key_size)(void);
  bool (*supports_read_encryption_key_size)(void);

  bool (*SupportsBle)(void);
  bool (*SupportsBleDataPacketLengthExtension)(void);
  bool (*SupportsBleConnectionParametersRequest)(void);
  bool (*SupportsBlePrivacy)(void);
  bool (*supports_ble_set_privacy_mode)(void);
  bool (*SupportsBle2mPhy)(void);
  bool (*SupportsBleCodedPhy)(void);
  bool (*SupportsBleExtendedAdvertising)(void);
  bool (*SupportsBlePeriodicAdvertising)(void);
  bool (*SupportsBlePeripheralInitiatedFeaturesExchange)(void);
  bool (*SupportsBlePeriodicAdvertisingSyncTransferSender)(void);
  bool (*SupportsBlePeriodicAdvertisingSyncTransferRecipient)(void);
  bool (*SupportsBleConnectedIsochronousStreamCentral)(void);
  bool (*SupportsBleConnectedIsochronousStreamPeripheral)(void);
  bool (*SupportsBleIsochronousBroadcaster)(void);
  bool (*SupportsBleSynchronizedReceiver)(void);

  bool (*SupportsBleConnectionSubrating)(void);
  bool (*SupportsBleConnectionSubratingHost)(void);

  // Get the cached acl data sizes for the controller.
  uint16_t (*get_acl_data_size_classic)(void);
  uint16_t (*get_acl_data_size_ble)(void);
  uint16_t (*get_iso_data_size)(void);

  // Get the cached acl packet sizes for the controller.
  // This is a convenience function for the respective
  // acl data size + size of the acl header.
  uint16_t (*get_acl_packet_size_classic)(void);
  uint16_t (*get_acl_packet_size_ble)(void);
  uint16_t (*get_iso_packet_size)(void);

  uint16_t (*get_ble_default_data_packet_length)(void);
  uint16_t (*get_ble_maximum_tx_data_length)(void);
  uint16_t (*get_ble_maximum_tx_time)(void);
  uint16_t (*get_ble_maximum_advertising_data_length)(void);
  uint8_t (*get_ble_number_of_supported_advertising_sets)(void);
  uint8_t (*get_ble_periodic_advertiser_list_size)(void);

  // Get the number of acl packets the controller can buffer.
  uint16_t (*get_acl_buffer_count_classic)(void);
  uint8_t (*get_acl_buffer_count_ble)(void);
  uint8_t (*get_iso_buffer_count)(void);

  uint8_t (*get_ble_acceptlist_size)(void);

  uint8_t (*get_ble_resolving_list_max_size)(void);
  void (*set_ble_resolving_list_max_size)(int resolving_list_max_size);
  uint8_t* (*get_local_supported_codecs)(uint8_t* number_of_codecs);
  uint8_t (*get_le_all_initiating_phys)(void);
  uint8_t (*clear_event_filter)(void);
  uint8_t (*clear_event_mask)(void);
  uint8_t (*le_rand)(LeRandCallback);
  uint8_t (*set_event_filter_connection_setup_all_devices)(void);
  uint8_t (*set_event_filter_allow_device_connection)(
      std::vector<RawAddress> devices);
  uint8_t (*set_default_event_mask_except)(uint64_t mask, uint64_t le_mask);
  uint8_t (*set_event_filter_inquiry_result_all_devices)(void);

} controller_t;

const controller_t* controller_get_interface();
