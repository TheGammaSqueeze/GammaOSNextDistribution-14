/*
 * Copyright 2022 The Android Open Source Project
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

#include <stdint.h>

#include <vector>

#include "device/include/esco_parameters.h"
#include "internal_include/bt_target.h"
#include "raw_address.h"

// Used by the Bluetooth stack to get WBS supported and codec, or notify SCO
// connection change to lower layer (kernel) when SCO-over-HCI is used. So far
// ChromeOS uses SCO-over-HCI; usually Android phone uses hardware SCO route so
// it doesn't apply here.
namespace hfp_hal_interface {
enum codec : uint64_t {
  CVSD = 1 << 0,
  MSBC_TRANSPARENT = 1 << 1,
  MSBC = 1 << 2,
  LC3 = 1 << 3,
};

struct bt_codec {
  codec codec;
  uint8_t data_path;
  std::vector<uint8_t> data;
};

struct bt_codecs {
  bool offload_capable;
  std::vector<bt_codec> codecs;
};

// Use default packet size for codec if this value is given.
constexpr int kDefaultPacketSize = 0;

constexpr inline int esco_coding_to_codec(esco_coding_format_t esco_coding) {
  switch (esco_coding) {
    case ESCO_CODING_FORMAT_TRANSPNT:
      return codec::MSBC_TRANSPARENT;
    case ESCO_CODING_FORMAT_MSBC:
      return codec::MSBC;
    case ESCO_CODING_FORMAT_LC3:
      return codec::LC3;

    // Default to CVSD encoding if unknown format.
    case ESCO_CODING_FORMAT_CVSD:
    default:
      return codec::CVSD;
  }
}

// Initialize the SCO HFP HAL module
void init();

// Check if specified coding format is supported by the adapter.
bool is_coding_format_supported(esco_coding_format_t coding_format);

// Check if wideband speech is supported on local device.
bool get_wbs_supported();

// Check if super wideband speech is supported on local device.
bool get_swb_supported();

// Checks the details of the codecs (specified as a bitmask of enum codec).
bt_codecs get_codec_capabilities(uint64_t codecs);

// Check if hardware offload is supported.
bool get_offload_supported();

// Check if hardware offload is enabled.
bool get_offload_enabled();

// Set offload enable/disable.
bool enable_offload(bool enable);

// Notify the codec datapath to lower layer for offload mode.
void set_codec_datapath(int codec_uuid);

// Get the maximum supported packet size from the lower layer.
int get_packet_size(int codec);

// Notify the lower layer about SCO connection change.
void notify_sco_connection_change(RawAddress device, bool is_connected,
                                  int codec);

// Update eSCO parameters
void update_esco_parameters(enh_esco_params_t* p_parms);
}  // namespace hfp_hal_interface
