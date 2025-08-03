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

#include "managed_register_riscv64.h"

#include "base/globals.h"

namespace art HIDDEN {
namespace riscv64 {

bool Riscv64ManagedRegister::Overlaps(const Riscv64ManagedRegister& other) const {
  if (IsNoRegister() || other.IsNoRegister()) {
    return false;
  }
  CHECK(IsValidManagedRegister());
  CHECK(other.IsValidManagedRegister());

  return Equals(other);
}

void Riscv64ManagedRegister::Print(std::ostream& os) const {
  if (!IsValidManagedRegister()) {
    os << "No Register";
  } else if (IsXRegister()) {
    os << "XRegister: " << static_cast<int>(AsXRegister());
  } else if (IsFRegister()) {
    os << "FRegister: " << static_cast<int>(AsFRegister());
  } else {
    os << "??: " << RegId();
  }
}

std::ostream& operator<<(std::ostream& os, const Riscv64ManagedRegister& reg) {
  reg.Print(os);
  return os;
}

}  // namespace riscv64
}  // namespace art
