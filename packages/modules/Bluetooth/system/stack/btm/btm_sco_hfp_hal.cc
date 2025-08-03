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

#include "btm_sco_hfp_hal.h"

#include <vector>

#include "common/init_flags.h"
#include "device/include/esco_parameters.h"
#include "internal_include/bt_target.h"
#include "osi/include/properties.h"

namespace hfp_hal_interface {
namespace {
bool offload_supported = true;
bool offload_enabled = true;
std::vector<bt_codec> cached_codecs;
}  // namespace

// Android implementation only has consts. Initialize CVSD and MSBC to PCM
// offloaded defaults.
void init() {
  bt_codec cvsd = {
      .codec = codec::CVSD,
      .data_path = ESCO_DATA_PATH_PCM,
  };

  bt_codec msbc = {
      .codec = codec::MSBC,
      .data_path = ESCO_DATA_PATH_PCM,
  };

  cached_codecs.clear();
  cached_codecs.emplace_back(cvsd);
  cached_codecs.emplace_back(msbc);
}

// This is not used in Android.
bool is_coding_format_supported(esco_coding_format_t coding_format) {
  return true;
}

// Android statically compiles WBS support.
bool get_wbs_supported() { return !DISABLE_WBS; }

bool get_swb_supported() {
  return osi_property_get_bool("bluetooth.hfp.swb.supported", false) &&
         bluetooth::common::init_flags::sco_codec_select_lc3_is_enabled();
}

// Checks the supported codecs
bt_codecs get_codec_capabilities(uint64_t codecs) {
  bt_codecs codec_list = {.offload_capable = offload_supported};

  for (auto c : cached_codecs) {
    if (c.codec & codecs) {
      codec_list.codecs.push_back(c);
    }
  }

  return codec_list;
}

// Check if hardware offload is supported
bool get_offload_supported() { return offload_supported; }

// Check if hardware offload is enabled
bool get_offload_enabled() { return offload_supported && offload_enabled; }

// Set offload enable/disable
bool enable_offload(bool enable) {
  if (!offload_supported) {
    return false;
  }
  offload_enabled = enable;
  return true;
}

// On Android, this is a no-op because the settings default to offloaded case.
void set_codec_datapath(int codec_uuid) {}

// No packet size limits on Android since it will be offloaded.
int get_packet_size(int codec) { return kDefaultPacketSize; }

void notify_sco_connection_change(RawAddress device, bool is_connected,
                                  int codec) {
  // Do nothing since this is handled by Android's audio hidl.
}

// On Android, this is a no-op because the settings default to work for Android.
void update_esco_parameters(enh_esco_params_t* p_parms) {}
}  // namespace hfp_hal_interface
