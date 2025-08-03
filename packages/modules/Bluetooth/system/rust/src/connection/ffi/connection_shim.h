// Copyright 2023, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include <cstdint>
#include <memory>

#include "rust/cxx.h"
#include "rust/src/core/ffi/types.h"
#include "types/ble_address_with_type.h"

namespace bluetooth {

namespace connection {

struct LeAclManagerCallbackShim;

class LeAclManagerShim {
 public:
  LeAclManagerShim();
  ~LeAclManagerShim();

  void CreateLeConnection(core::AddressWithType address, bool is_direct) const;

  void CancelLeConnect(core::AddressWithType address) const;

#ifndef TARGET_FLOSS
  void RegisterRustCallbacks(::rust::Box<LeAclManagerCallbackShim> callbacks);
#endif

 private:
  struct impl;
  std::unique_ptr<impl> pimpl_;
};

void RegisterRustApis(
    ::rust::Fn<void(uint8_t client_id, core::AddressWithType address)>
        start_direct_connection,
    ::rust::Fn<void(uint8_t client_id, core::AddressWithType address)>
        stop_direct_connection,
    ::rust::Fn<void(uint8_t client_id, core::AddressWithType address)>
        add_background_connection,
    ::rust::Fn<void(uint8_t client_id, core::AddressWithType address)>
        remove_background_connection,
    ::rust::Fn<void(uint8_t client_id)> remove_client,
    ::rust::Fn<void(core::AddressWithType address)>
        stop_all_connections_to_device);

struct RustConnectionManager {
  ::rust::Fn<void(uint8_t client_id, core::AddressWithType address)>
      start_direct_connection;
  ::rust::Fn<void(uint8_t client_id, core::AddressWithType address)>
      stop_direct_connection;
  ::rust::Fn<void(uint8_t client_id, core::AddressWithType address)>
      add_background_connection;
  ::rust::Fn<void(uint8_t client_id, core::AddressWithType address)>
      remove_background_connection;
  ::rust::Fn<void(uint8_t client_id)> remove_client;
  ::rust::Fn<void(core::AddressWithType address)>
      stop_all_connections_to_device;
};

RustConnectionManager& GetConnectionManager();

core::AddressWithType ResolveRawAddress(RawAddress bd_addr);

}  // namespace connection
}  // namespace bluetooth
