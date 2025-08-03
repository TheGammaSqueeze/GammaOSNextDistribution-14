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

#include "connection_shim.h"

#include <algorithm>
#include <cstdint>
#include <iterator>
#include <optional>

#include "hci/acl_manager.h"
#include "hci/address_with_type.h"
#include "hci/hci_packets.h"
#include "main/shim/entry.h"
#ifndef TARGET_FLOSS
#include "src/connection/ffi.rs.h"
#endif
#include "src/core/ffi/types.h"
#include "stack/btm/btm_dev.h"

namespace bluetooth {
namespace connection {

#ifdef TARGET_FLOSS
struct LeAclManagerCallbackShim {
  void OnLeConnectSuccess(core::AddressWithType addr) const {
    LOG_ALWAYS_FATAL("system/rust not available in Floss");
  }
  void OnLeConnectFail(core::AddressWithType addr, uint8_t status) const {
    LOG_ALWAYS_FATAL("system/rust not available in Floss");
  };
  void OnLeDisconnection(core::AddressWithType addr) const {
    LOG_ALWAYS_FATAL("system/rust not available in Floss");
  };
};

using BoxedLeAclManagerCallbackShim = std::unique_ptr<LeAclManagerCallbackShim>;

#else

using BoxedLeAclManagerCallbackShim = ::rust::Box<LeAclManagerCallbackShim>;

#endif

namespace {
hci::AddressWithType ToCppAddress(core::AddressWithType address) {
  auto hci_address = hci::Address();
  hci_address.FromOctets(address.address.data());
  return hci::AddressWithType(hci_address,
                              (hci::AddressType)address.address_type);
}

core::AddressWithType ToRustAddress(hci::AddressWithType address) {
  return core::AddressWithType{address.GetAddress().address,
                               (core::AddressType)address.GetAddressType()};
}
}  // namespace

struct LeAclManagerShim::impl : hci::acl_manager::LeAcceptlistCallbacks {
 public:
  impl() { acl_manager_ = shim::GetAclManager(); }

  ~impl() {
    if (callbacks_.has_value()) {
      callbacks_.reset();
      auto promise = std::promise<void>();
      auto future = promise.get_future();
      acl_manager_->UnregisterLeAcceptlistCallbacks(this, std::move(promise));
      future.wait();
    }
  }

  void CreateLeConnection(core::AddressWithType address, bool is_direct) {
    acl_manager_->CreateLeConnection(ToCppAddress(address), is_direct);
  }

  void CancelLeConnect(core::AddressWithType address) {
    acl_manager_->CancelLeConnect(ToCppAddress(address));
  }

#ifndef TARGET_FLOSS
  void RegisterRustCallbacks(BoxedLeAclManagerCallbackShim callbacks) {
    callbacks_ = std::move(callbacks);
    acl_manager_->RegisterLeAcceptlistCallbacks(this);
  }
#endif

  // hci::acl_manager::LeAcceptlistCallbacks
  virtual void OnLeConnectSuccess(hci::AddressWithType address) {
    callbacks_.value()->OnLeConnectSuccess(ToRustAddress(address));
  }

  // hci::acl_manager::LeAcceptlistCallbacks
  virtual void OnLeConnectFail(hci::AddressWithType address,
                               hci::ErrorCode reason) {
    callbacks_.value()->OnLeConnectFail(ToRustAddress(address),
                                        static_cast<uint8_t>(reason));
  }

  // hci::acl_manager::LeAcceptlistCallbacks
  virtual void OnLeDisconnection(hci::AddressWithType address) {
    callbacks_.value()->OnLeDisconnection(ToRustAddress(address));
  }

  // hci::acl_manager::LeAcceptlistCallbacks
  virtual void OnResolvingListChange() {}

 private:
  std::optional<BoxedLeAclManagerCallbackShim> callbacks_;
  hci::AclManager* acl_manager_{};
};

LeAclManagerShim::LeAclManagerShim() {
  pimpl_ = std::make_unique<LeAclManagerShim::impl>();
}

LeAclManagerShim::~LeAclManagerShim() = default;

void LeAclManagerShim::CreateLeConnection(core::AddressWithType address,
                                          bool is_direct) const {
  pimpl_->CreateLeConnection(address, is_direct);
}

void LeAclManagerShim::CancelLeConnect(core::AddressWithType address) const {
  pimpl_->CancelLeConnect(address);
}

#ifndef TARGET_FLOSS
void LeAclManagerShim::RegisterRustCallbacks(
    BoxedLeAclManagerCallbackShim callbacks) {
  pimpl_->RegisterRustCallbacks(std::move(callbacks));
}
#endif

namespace {

std::optional<RustConnectionManager> connection_manager;

}  // namespace

RustConnectionManager& GetConnectionManager() {
  return connection_manager.value();
}

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
        stop_all_connections_to_device) {
  connection_manager = {start_direct_connection,
                        stop_direct_connection,
                        add_background_connection,
                        remove_background_connection,
                        remove_client,
                        stop_all_connections_to_device};
}

core::AddressWithType ResolveRawAddress(RawAddress bd_addr) {
  tBLE_BD_ADDR address = BTM_Sec_GetAddressWithType(bd_addr);
  return core::ToRustAddress(address);
}

}  // namespace connection
}  // namespace bluetooth
