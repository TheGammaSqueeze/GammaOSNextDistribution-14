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
#ifndef GD_RUST_TOPSHIM_GATT_GATT_SHIM_H
#define GD_RUST_TOPSHIM_GATT_GATT_SHIM_H

#include <memory>

#include "include/hardware/bt_gatt.h"

#include "rust/cxx.h"

namespace bluetooth {
namespace topshim {
namespace rust {

class GattClientIntf {
 public:
  GattClientIntf(const btgatt_client_interface_t* client_intf) : client_intf_(client_intf){};
  ~GattClientIntf() = default;

  int read_phy(int client_if, RawAddress bt_addr);

 private:
  const btgatt_client_interface_t* client_intf_;
};

std::unique_ptr<GattClientIntf> GetGattClientProfile(const unsigned char* gatt_intf);

class GattServerIntf {
 public:
  GattServerIntf(const btgatt_server_interface_t* server_intf) : server_intf_(server_intf){};
  ~GattServerIntf() = default;

  int server_read_phy(int server_if, RawAddress bt_addr);

 private:
  const btgatt_server_interface_t* server_intf_;
};

std::unique_ptr<GattServerIntf> GetGattServerProfile(const unsigned char* gatt_intf);

}  // namespace rust
}  // namespace topshim
}  // namespace bluetooth

#endif  // GD_RUST_TOPSHIM_GATT_GATT_SHIM_H
