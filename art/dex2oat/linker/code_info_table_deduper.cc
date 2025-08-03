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

#include "code_info_table_deduper.h"

#include "oat/stack_map.h"

namespace art {
namespace linker {

void CodeInfoTableDeduper::ReserveDedupeBuffer(size_t num_code_infos) {
  DCHECK(dedupe_set_.empty());
  const size_t max_size = num_code_infos * CodeInfo::kNumBitTables;
  // Reserve space for 1/2 of the maximum dedupe set size to avoid rehashing.
  // Usually only 30%-40% of bit tables are unique.
  dedupe_set_.reserve(max_size / 2u);
}

size_t CodeInfoTableDeduper::Dedupe(const uint8_t* code_info_data) {
  static constexpr size_t kNumHeaders = CodeInfo::kNumHeaders;
  static constexpr size_t kNumBitTables = CodeInfo::kNumBitTables;

  // The back-reference offset takes space so dedupe is not worth it for tiny tables.
  constexpr size_t kMinDedupSize = 33;  // Assume 32-bit offset on average.

  size_t start_bit_offset = writer_.NumberOfWrittenBits();
  DCHECK_ALIGNED(start_bit_offset, kBitsPerByte);

  // Reserve enough space in the `dedupe_set_` to avoid reashing later in this
  // function and allow using direct pointers to the `HashSet<>` entries.
  size_t elements_until_expand = dedupe_set_.ElementsUntilExpand();
  if (UNLIKELY(elements_until_expand - dedupe_set_.size() < kNumBitTables)) {
    // When resizing, try to make the load factor close to the minimum load factor.
    size_t required_capacity = dedupe_set_.size() + kNumBitTables;
    double factor = dedupe_set_.GetMaxLoadFactor() / dedupe_set_.GetMinLoadFactor();
    size_t reservation = required_capacity * factor;
    DCHECK_GE(reservation, required_capacity);
    dedupe_set_.reserve(reservation);
    elements_until_expand = dedupe_set_.ElementsUntilExpand();
    DCHECK_GE(elements_until_expand - dedupe_set_.size(), kNumBitTables);
  }

  // Read the existing code info and record bit table starts and end.
  BitMemoryReader reader(code_info_data);
  std::array<uint32_t, kNumHeaders> header = reader.ReadInterleavedVarints<kNumHeaders>();
  CodeInfo code_info;
  CodeInfo::ForEachHeaderField([&code_info, &header](size_t i, auto member_pointer) {
    code_info.*member_pointer = header[i];
  });
  DCHECK(!code_info.HasDedupedBitTables());  // Input `CodeInfo` has no deduped tables.
  std::array<uint32_t, kNumBitTables + 1u> bit_table_bit_starts;
  CodeInfo::ForEachBitTableField([&](size_t i, auto member_pointer) {
    bit_table_bit_starts[i] = dchecked_integral_cast<uint32_t>(reader.NumberOfReadBits());
    DCHECK(!code_info.IsBitTableDeduped(i));
    if (LIKELY(code_info.HasBitTable(i))) {
      auto& table = code_info.*member_pointer;
      table.Decode(reader);
    }
  });
  bit_table_bit_starts[kNumBitTables] = dchecked_integral_cast<uint32_t>(reader.NumberOfReadBits());

  // Copy the source data.
  BitMemoryRegion read_region = reader.GetReadRegion();
  writer_.WriteBytesAligned(code_info_data, BitsToBytesRoundUp(read_region.size_in_bits()));

  // Insert entries for large tables to the `dedupe_set_` and check for duplicates.
  std::array<DedupeSetEntry*, kNumBitTables> dedupe_entries;
  std::fill(dedupe_entries.begin(), dedupe_entries.end(), nullptr);
  CodeInfo::ForEachBitTableField([&](size_t i, [[maybe_unused]] auto member_pointer) {
    if (LIKELY(code_info.HasBitTable(i))) {
      uint32_t table_bit_size = bit_table_bit_starts[i + 1u] - bit_table_bit_starts[i];
      if (table_bit_size >= kMinDedupSize) {
        uint32_t table_bit_start = start_bit_offset + bit_table_bit_starts[i];
        BitMemoryRegion region(
            const_cast<uint8_t*>(writer_.data()), table_bit_start, table_bit_size);
        DedupeSetEntry entry{table_bit_start, table_bit_size};
        auto [it, inserted] = dedupe_set_.insert(entry);
        dedupe_entries[i] = &*it;
        if (!inserted) {
          code_info.SetBitTableDeduped(i);  // Mark as deduped before we write header.
        }
      }
    }
  });
  DCHECK_EQ(elements_until_expand, dedupe_set_.ElementsUntilExpand()) << "Unexpected resizing!";

  if (code_info.HasDedupedBitTables()) {
    // Reset the writer to the original position. This makes new entries in the
    // `dedupe_set_` effectively point to non-existent data. We shall write the
    // new data again at the correct position and update these entries.
    writer_.Truncate(start_bit_offset);
    // Update bit table flags in the `header` and write the `header`.
    header[kNumHeaders - 1u] = code_info.bit_table_flags_;
    CodeInfo::ForEachHeaderField([&code_info, &header](size_t i, auto member_pointer) {
      DCHECK_EQ(code_info.*member_pointer, header[i]);
    });
    writer_.WriteInterleavedVarints(header);
    // Write bit tables and update offsets in `dedupe_set_` after encoding the `header`.
    CodeInfo::ForEachBitTableField([&](size_t i, [[maybe_unused]] auto member_pointer) {
      if (code_info.HasBitTable(i)) {
        size_t current_bit_offset = writer_.NumberOfWrittenBits();
        if (code_info.IsBitTableDeduped(i)) {
          DCHECK_GE(bit_table_bit_starts[i + 1u] - bit_table_bit_starts[i], kMinDedupSize);
          DCHECK(dedupe_entries[i] != nullptr);
          size_t deduped_offset = dedupe_entries[i]->bit_start;
          writer_.WriteVarint(current_bit_offset - deduped_offset);
        } else {
          uint32_t table_bit_size = bit_table_bit_starts[i + 1u] - bit_table_bit_starts[i];
          writer_.WriteRegion(read_region.Subregion(bit_table_bit_starts[i], table_bit_size));
          if (table_bit_size >= kMinDedupSize) {
            // Update offset in the `dedupe_set_` entry.
            DCHECK(dedupe_entries[i] != nullptr);
            dedupe_entries[i]->bit_start = current_bit_offset;
          }
        }
      }
    });
    writer_.ByteAlign();
  }  // else nothing to do - we already copied the data.

  if (kIsDebugBuild) {
    CodeInfo old_code_info(code_info_data);
    CodeInfo new_code_info(writer_.data() + start_bit_offset / kBitsPerByte);
    CodeInfo::ForEachHeaderField([&old_code_info, &new_code_info](size_t, auto member_pointer) {
      if (member_pointer != &CodeInfo::bit_table_flags_) {  // Expected to differ.
        DCHECK_EQ(old_code_info.*member_pointer, new_code_info.*member_pointer);
      }
    });
    CodeInfo::ForEachBitTableField([&old_code_info, &new_code_info](size_t i, auto member_pointer) {
      DCHECK_EQ(old_code_info.HasBitTable(i), new_code_info.HasBitTable(i));
      DCHECK((old_code_info.*member_pointer).Equals(new_code_info.*member_pointer));
    });
  }

  return start_bit_offset / kBitsPerByte;
}

}  //  namespace linker
}  //  namespace art
