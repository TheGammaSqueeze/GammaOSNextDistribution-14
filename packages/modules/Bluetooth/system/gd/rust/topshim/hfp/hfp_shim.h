/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "btif/include/btif_hf.h"
#include "include/hardware/bluetooth_headset_callbacks.h"
#include "rust/cxx.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace topshim {
namespace rust {

struct TelephonyDeviceStatus;
struct CallInfo;
struct PhoneState;

class HfpIntf {
 public:
  HfpIntf(headset::Interface* intf) : intf_(intf){};

  int init();
  uint32_t connect(RawAddress addr);
  int connect_audio(RawAddress addr, bool sco_offload, int disabled_codecs);
  int set_active_device(RawAddress addr);
  int set_volume(int8_t volume, RawAddress addr);
  uint32_t set_mic_volume(int8_t volume, RawAddress addr);
  uint32_t disconnect(RawAddress addr);
  int disconnect_audio(RawAddress addr);
  uint32_t device_status_notification(TelephonyDeviceStatus status, RawAddress addr);
  uint32_t indicator_query_response(
      TelephonyDeviceStatus device_status, PhoneState phone_state, RawAddress addr);
  uint32_t current_calls_query_response(const ::rust::Vec<CallInfo>& call_list, RawAddress addr);
  uint32_t phone_state_change(
      PhoneState phone_state, const ::rust::String& number, RawAddress addr);
  uint32_t simple_at_response(bool ok, RawAddress addr);
  void debug_dump();
  void cleanup();

 private:
  headset::Interface* intf_;
};

std::unique_ptr<HfpIntf> GetHfpProfile(const unsigned char* btif);
bool interop_insert_call_when_sco_start(RawAddress addr);

}  // namespace rust
}  // namespace topshim
}  // namespace bluetooth
