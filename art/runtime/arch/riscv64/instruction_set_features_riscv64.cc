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

#include "instruction_set_features_riscv64.h"

#include <fstream>
#include <sstream>

#include "android-base/stringprintf.h"
#include "android-base/strings.h"
#include "base/logging.h"

namespace art HIDDEN {

using android::base::StringPrintf;

// Basic feature set is rv64gcv_zba_zbb_zbs, aka rv64imafdcv_zba_zbb_zbs.
constexpr uint32_t BasicFeatures() {
  return Riscv64InstructionSetFeatures::kExtGeneric |
         Riscv64InstructionSetFeatures::kExtCompressed |
         Riscv64InstructionSetFeatures::kExtVector |
         Riscv64InstructionSetFeatures::kExtZba |
         Riscv64InstructionSetFeatures::kExtZbb |
         Riscv64InstructionSetFeatures::kExtZbs;
}

Riscv64FeaturesUniquePtr Riscv64InstructionSetFeatures::FromVariant(
    const std::string& variant, [[maybe_unused]] std::string* error_msg) {
  if (variant != "generic") {
    LOG(WARNING) << "Unexpected CPU variant for Riscv64 using defaults: " << variant;
  }
  return Riscv64FeaturesUniquePtr(new Riscv64InstructionSetFeatures(BasicFeatures()));
}

Riscv64FeaturesUniquePtr Riscv64InstructionSetFeatures::FromBitmap(uint32_t bitmap) {
  return Riscv64FeaturesUniquePtr(new Riscv64InstructionSetFeatures(bitmap));
}

Riscv64FeaturesUniquePtr Riscv64InstructionSetFeatures::FromCppDefines() {
  // Assume kExtGeneric is always present.
  uint32_t bits = kExtGeneric;
#ifdef __riscv_c
  bits |= kExtCompressed;
#endif
#ifdef __riscv_v
  bits |= kExtVector;
#endif
#ifdef __riscv_zba
  bits |= kExtZba;
#endif
#ifdef __riscv_zbb
  bits |= kExtZbb;
#endif
#ifdef __riscv_zbs
  bits |= kExtZbs;
#endif
  return FromBitmap(bits);
}

Riscv64FeaturesUniquePtr Riscv64InstructionSetFeatures::FromCpuInfo() {
  UNIMPLEMENTED(WARNING);
  return FromCppDefines();
}

Riscv64FeaturesUniquePtr Riscv64InstructionSetFeatures::FromHwcap() {
  UNIMPLEMENTED(WARNING);
  return FromCppDefines();
}

Riscv64FeaturesUniquePtr Riscv64InstructionSetFeatures::FromAssembly() {
  UNIMPLEMENTED(WARNING);
  return FromCppDefines();
}

Riscv64FeaturesUniquePtr Riscv64InstructionSetFeatures::FromCpuFeatures() {
  UNIMPLEMENTED(WARNING);
  return FromCppDefines();
}

bool Riscv64InstructionSetFeatures::Equals(const InstructionSetFeatures* other) const {
  if (InstructionSet::kRiscv64 != other->GetInstructionSet()) {
    return false;
  }
  return bits_ == other->AsRiscv64InstructionSetFeatures()->bits_;
}

uint32_t Riscv64InstructionSetFeatures::AsBitmap() const { return bits_; }

static const std::pair<uint32_t, std::string> kExtensionList[] = {
    {Riscv64InstructionSetFeatures::kExtGeneric, "rv64g"},
    {Riscv64InstructionSetFeatures::kExtCompressed, "c"},
    {Riscv64InstructionSetFeatures::kExtVector, "v"},
    {Riscv64InstructionSetFeatures::kExtZba, "_zba"},
    {Riscv64InstructionSetFeatures::kExtZbb, "_zbb"},
    {Riscv64InstructionSetFeatures::kExtZbs, "_zbs"},
};

std::string Riscv64InstructionSetFeatures::GetFeatureString() const {
  std::string result = "";
  for (auto&& [ext_bit, ext_string] : kExtensionList) {
    if (bits_ & ext_bit) {
      result += ext_string;
    }
  }
  return result;
}

std::unique_ptr<const InstructionSetFeatures>
Riscv64InstructionSetFeatures::AddFeaturesFromSplitString(const std::vector<std::string>& features,
                                                          std::string* error_msg) const {
  uint32_t bits = bits_;
  if (!features.empty()) {
    // There should be only one feature, the ISA string.
    DCHECK_EQ(features.size(), 1U);
    std::string_view isa_string = features.front();
    bits = 0;
    for (auto&& [ext_bit, ext_string] : kExtensionList) {
      if (isa_string.substr(0, ext_string.length()) == ext_string) {
        isa_string.remove_prefix(ext_string.length());
        bits |= ext_bit;
      }
    }
    if (!isa_string.empty()) {
      *error_msg = StringPrintf("Unknown extension in ISA string: '%s'", features.front().c_str());
      return nullptr;
    }
    DCHECK(bits & kExtGeneric);
  }
  return FromBitmap(bits);
}

}  // namespace art
