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

#ifndef ART_DEX2OAT_LINKER_CODE_INFO_TABLE_DEDUPER_H_
#define ART_DEX2OAT_LINKER_CODE_INFO_TABLE_DEDUPER_H_

#include <vector>

#include "base/bit_memory_region.h"
#include "base/hash_set.h"

namespace art {
namespace linker {

class CodeInfoTableDeduper {
 public:
  explicit CodeInfoTableDeduper(std::vector<uint8_t>* output)
      : writer_(output),
        dedupe_set_(kMinLoadFactor,
                    kMaxLoadFactor,
                    DedupeSetEntryHash(output),
                    DedupeSetEntryEquals(output)) {
    DCHECK_EQ(output->size(), 0u);
  }

  void ReserveDedupeBuffer(size_t num_code_infos);

  // Copy CodeInfo into output while de-duplicating the internal bit tables.
  // It returns the byte offset of the copied CodeInfo within the output.
  size_t Dedupe(const uint8_t* code_info);

 private:
  struct DedupeSetEntry {
    uint32_t bit_start;
    uint32_t bit_size;
  };

  class DedupeSetEntryEmpty {
   public:
    void MakeEmpty(DedupeSetEntry& item) const {
      item = {0u, 0u};
    }
    bool IsEmpty(const DedupeSetEntry& item) const {
      return item.bit_size == 0u;
    }
  };

  class DedupeSetEntryHash {
   public:
    explicit DedupeSetEntryHash(std::vector<uint8_t>* output) : output_(output) {}

    uint32_t operator()(const DedupeSetEntry& item) const {
      return DataHash()(BitMemoryRegion(output_->data(), item.bit_start, item.bit_size));
    }

   private:
    std::vector<uint8_t>* const output_;
  };

  class DedupeSetEntryEquals {
   public:
    explicit DedupeSetEntryEquals(std::vector<uint8_t>* output) : output_(output) {}

    bool operator()(const DedupeSetEntry& lhs, const DedupeSetEntry& rhs) const {
      DCHECK_NE(lhs.bit_size, 0u);
      DCHECK_NE(rhs.bit_size, 0u);
      return lhs.bit_size == rhs.bit_size &&
             BitMemoryRegion::Equals(
                 BitMemoryRegion(output_->data(), lhs.bit_start, lhs.bit_size),
                 BitMemoryRegion(output_->data(), rhs.bit_start, rhs.bit_size));
    }

   private:
    std::vector<uint8_t>* const output_;
  };

  using DedupeSet =
      HashSet<DedupeSetEntry, DedupeSetEntryEmpty, DedupeSetEntryHash, DedupeSetEntryEquals>;

  static constexpr double kMinLoadFactor = 0.5;
  static constexpr double kMaxLoadFactor = 0.75;

  BitMemoryWriter<std::vector<uint8_t>> writer_;

  // Deduplicate at BitTable level. Entries describe ranges in `output`, see constructor.
  DedupeSet dedupe_set_;
};

}  //  namespace linker
}  //  namespace art

#endif  // ART_DEX2OAT_LINKER_CODE_INFO_TABLE_DEDUPER_H_
