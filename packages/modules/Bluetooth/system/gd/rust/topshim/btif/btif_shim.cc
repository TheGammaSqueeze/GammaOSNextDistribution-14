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

#include "rust/topshim/btif/btif_shim.h"

#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <memory>

#include "rust/cxx.h"
#include "src/btif.rs.h"

namespace bluetooth {
namespace topshim {
namespace rust {

InitFlags::InitFlags() {}
InitFlags::~InitFlags() {
  if (flags_) {
    for (int i = 0; flags_[i] != nullptr; ++i) {
      std::free(const_cast<void*>(static_cast<const void*>(flags_[i])));
    }

    std::free(const_cast<void*>(static_cast<const void*>(flags_)));
  }
}

void InitFlags::Convert(::rust::Vec<::rust::String>& initFlags) {
  // Allocate number of flags + 1 (last entry must be null to signify end)
  // Must be calloc so our cleanup correctly frees everything
  flags_ = static_cast<const char**>(std::calloc(initFlags.size() + 1, sizeof(char*)));
  if (!flags_) return;

  for (size_t i = 0; i < initFlags.size(); ++i) {
    flags_[i] = strndup(initFlags[i].data(), initFlags[i].size());
    if (!flags_[i]) {
      return;
    }
  }
}

std::unique_ptr<InitFlags> ConvertFlags(::rust::Vec<::rust::String> flags) {
  auto ret = std::make_unique<InitFlags>();
  ret->Convert(flags);

  return ret;
}

}  // namespace rust
}  // namespace topshim
}  // namespace bluetooth
