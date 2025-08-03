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

#include "rust/src/connection/ffi/connection_shim.h"
#include "test/common/mock_functions.h"

namespace bluetooth {

namespace connection {

RustConnectionManager& GetConnectionManager() {
  static RustConnectionManager manager = {};
  inc_func_call_count(__func__);
  return manager;
}

core::AddressWithType ResolveRawAddress(RawAddress bd_addr) {
  inc_func_call_count(__func__);
  return {};
}

}  // namespace connection
}  // namespace bluetooth
