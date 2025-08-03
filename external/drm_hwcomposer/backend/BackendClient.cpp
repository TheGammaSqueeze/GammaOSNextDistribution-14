/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "BackendClient.h"

#include "BackendManager.h"

namespace android {

HWC2::Error BackendClient::ValidateDisplay(HwcDisplay *display,
                                           uint32_t *num_types,
                                           uint32_t * /*num_requests*/) {
  for (auto &[layer_handle, layer] : display->layers()) {
    layer.SetValidatedType(HWC2::Composition::Client);
    ++*num_types;
  }
  return HWC2::Error::HasChanges;
}

// clang-format off
// NOLINTNEXTLINE(cppcoreguidelines-avoid-non-const-global-variables, cert-err58-cpp)
REGISTER_BACKEND("client", BackendClient);
// clang-format on

}  // namespace android
