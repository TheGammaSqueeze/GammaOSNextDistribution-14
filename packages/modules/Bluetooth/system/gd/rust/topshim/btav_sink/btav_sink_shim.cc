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

#include "rust/topshim/btav_sink/btav_sink_shim.h"

#include <memory>

#include "include/hardware/bluetooth.h"
#include "rust/cxx.h"
#include "src/profiles/a2dp.rs.h"
#include "types/raw_address.h"

namespace rusty = ::bluetooth::topshim::rust;

namespace bluetooth {
namespace topshim {
namespace rust {
namespace internal {
static A2dpSinkIntf* g_a2dp_sink_if;

static A2dpError to_rust_error(const btav_error_t& error) {
  A2dpError a2dp_error = {
      .status = error.status,
      .error_code = error.error_code,
      .error_msg = error.error_msg.value_or(""),
  };
  return a2dp_error;
}

static void connection_state_cb(
    const RawAddress& addr, btav_connection_state_t state, const btav_error_t& error) {
  A2dpError a2dp_error = to_rust_error(error);
  rusty::sink_connection_state_callback(addr, state, a2dp_error);
}
static void audio_state_cb(const RawAddress& addr, btav_audio_state_t state) {
  rusty::sink_audio_state_callback(addr, state);
}
static void audio_config_cb(const RawAddress& addr, uint32_t sample_rate, uint8_t channel_count) {
  rusty::sink_audio_config_callback(addr, sample_rate, channel_count);
}

btav_sink_callbacks_t g_a2dp_sink_callbacks = {
    sizeof(btav_sink_callbacks_t),
    connection_state_cb,
    audio_state_cb,
    audio_config_cb,
};
}  // namespace internal

A2dpSinkIntf::~A2dpSinkIntf() {
  // TODO
}

std::unique_ptr<A2dpSinkIntf> GetA2dpSinkProfile(const unsigned char* btif) {
  if (internal::g_a2dp_sink_if) std::abort();

  const bt_interface_t* btif_ = reinterpret_cast<const bt_interface_t*>(btif);

  auto a2dp_sink = std::make_unique<A2dpSinkIntf>(
      reinterpret_cast<const btav_sink_interface_t*>(btif_->get_profile_interface("a2dp_sink")));
  internal::g_a2dp_sink_if = a2dp_sink.get();
  return a2dp_sink;
}

int A2dpSinkIntf::init() const {
  return intf_->init(&internal::g_a2dp_sink_callbacks, 1);
}

int A2dpSinkIntf::connect(RawAddress addr) const {
  return intf_->connect(addr);
}

int A2dpSinkIntf::disconnect(RawAddress addr) const {
  return intf_->disconnect(addr);
}

int A2dpSinkIntf::set_active_device(RawAddress addr) const {
  return intf_->set_active_device(addr);
}

void A2dpSinkIntf::cleanup() const {
  // TODO: Implement.
}

}  // namespace rust
}  // namespace topshim
}  // namespace bluetooth
