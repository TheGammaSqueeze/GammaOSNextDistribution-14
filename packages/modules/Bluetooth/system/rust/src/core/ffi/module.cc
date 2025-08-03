/*
 * Copyright 2023 The Android Open Source Project
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

#include "module.h"

#include <hardware/bt_gatt.h>

#include "btcore/include/module.h"
#include "os/log.h"
#ifndef TARGET_FLOSS
#include "src/connection/ffi/connection_shim.h"
#include "src/core/ffi.rs.h"
#include "src/gatt/ffi.rs.h"
#endif

#ifdef TARGET_FLOSS

// Rust modules don't run on Floss yet (b/277643360)
const module_t rust_module = {.name = RUST_MODULE,
                              .init = nullptr,
                              .start_up = nullptr,
                              .shut_down = nullptr,
                              .clean_up = nullptr,
                              .dependencies = {}};

#else

extern const btgatt_callbacks_t* bt_gatt_callbacks;

namespace bluetooth {
namespace rust_shim {

void FutureReady(future_t& future) { future_ready(&future, FUTURE_SUCCESS); }

}  // namespace rust_shim
}  // namespace bluetooth

namespace {
future_t* Start() {
  auto fut = future_new();

  if (bt_gatt_callbacks == nullptr) {
    // We can't crash here since some adapter tests mis-use the stack
    // startup/cleanup logic and start the stack without GATT, but don't fully
    // mock out the native layer.
    LOG_ERROR(
        "GATT profile not started, so we cannot start the Rust loop - this "
        "happens only in tests.");
    bluetooth::rust_shim::FutureReady(*fut);
    return fut;
  }
  bluetooth::rust_shim::start(
      std::make_unique<bluetooth::gatt::GattServerCallbacks>(
          *bt_gatt_callbacks->server),
      std::make_unique<bluetooth::connection::LeAclManagerShim>(), *fut);

  return fut;
}

future_t* Stop() {
  bluetooth::rust_shim::stop();
  return nullptr;
}
}  // namespace

const module_t rust_module = {.name = RUST_MODULE,
                              .init = nullptr,
                              .start_up = Start,
                              .shut_down = Stop,
                              .clean_up = nullptr,
                              .dependencies = {}};

#endif
