/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef BERBERIS_NATIVE_BRIDGE_NATIVE_BRIDGE_H_
#define BERBERIS_NATIVE_BRIDGE_NATIVE_BRIDGE_H_

#include <cstdint>

namespace android {

// Environment values required by the apps running with native bridge.
// See android/system/core/libnativebridge/native_bridge.cc
struct NativeBridgeRuntimeValues {
  const char* os_arch;
  const char* cpu_abi;
  const char* cpu_abi2;
  const char** supported_abis;
  int32_t abi_count;
};

}  // namespace android

namespace berberis {

// Should be defined separately according to the target guest architecture.
extern const char* kGuestIsa;
extern const char* kSupportedLibraryPathSubstring;
extern const android::NativeBridgeRuntimeValues kNativeBridgeRuntimeValues;

}  // namespace berberis

#endif  // BERBERIS_NATIVE_BRIDGE_NATIVE_BRIDGE_H_
