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

#include "rust/topshim/controller/controller_shim.h"

#include <memory>

#include "rust/cxx.h"
#include "src/controller.rs.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace topshim {
namespace rust {
namespace internal {
static ControllerIntf* g_controller_intf;
}  // namespace internal

ControllerIntf::~ControllerIntf() {}

std::unique_ptr<ControllerIntf> GetControllerInterface() {
  if (internal::g_controller_intf) std::abort();
  auto controller_intf = std::make_unique<ControllerIntf>();
  internal::g_controller_intf = controller_intf.get();
  return controller_intf;
}

RawAddress ControllerIntf::read_local_addr() const {
  if (!controller_) std::abort();
  return *controller_->get_address();
}

uint64_t ControllerIntf::get_ble_supported_states() const {
  if (!controller_) std::abort();
  uint64_t states;
  memcpy(&states, controller_->get_ble_supported_states(), sizeof(uint64_t));
  return states;
}

}  // namespace rust
}  // namespace topshim
}  // namespace bluetooth
