/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef CHRE_UTIL_PIGWEED_PERMISSION_H_
#define CHRE_UTIL_PIGWEED_PERMISSION_H_

#include <cstdint>
#include <optional>

#include "chre/util/nanoapp/assert.h"
#include "chre/util/non_copyable.h"
#include "chre/util/optional.h"
#include "chre_api/chre.h"

namespace chre {

/**
 * Holds the permission for the next message sent by a server.
 */
class RpcPermission : public NonCopyable {
 public:
  void set(uint32_t permission) {
    mPermission = permission;
  }

  uint32_t getAndReset() {
    CHRE_ASSERT(mPermission.has_value());
    uint32_t permission = mPermission.has_value()
                              ? mPermission.value()
                              : CHRE_MESSAGE_PERMISSION_NONE;
    mPermission.reset();
    return permission;
  }

 private:
  /** Bitmasked CHRE_MESSAGE_PERMISSION_ */
  Optional<uint32_t> mPermission;
};

}  // namespace chre

#endif  // CHRE_UTIL_PIGWEED_PERMISSION_H_