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

#include "page_util.h"

#include "android-base/stringprintf.h"

namespace art {

using android::base::StringPrintf;

bool GetPageFlagsOrCount(art::File& kpage_file,
                         uint64_t page_frame_number,
                         /*out*/ uint64_t& page_flags_or_count,
                         /*out*/ std::string& error_msg) {
  return GetPageFlagsOrCounts(kpage_file,
                              ArrayRef<const uint64_t>(&page_frame_number, 1u),
                              ArrayRef<uint64_t>(&page_flags_or_count, 1u),
                              error_msg);
}

bool GetPageFlagsOrCounts(File& kpage_file,
                          ArrayRef<const uint64_t> page_frame_numbers,
                          /*out*/ ArrayRef<uint64_t> page_flags_or_counts,
                          /*out*/ std::string& error_msg) {
  static_assert(kPageFlagsEntrySize == kPageCountEntrySize, "entry size check");
  CHECK_NE(page_frame_numbers.size(), 0u);
  CHECK_EQ(page_flags_or_counts.size(), page_frame_numbers.size());
  CHECK(page_frame_numbers.data() != nullptr);
  CHECK(page_flags_or_counts.data() != nullptr);

  size_t size = page_frame_numbers.size();
  size_t i = 0;
  while (i != size) {
    size_t start = i;
    ++i;
    while (i != size && page_frame_numbers[i] - page_frame_numbers[start] == i - start) {
      ++i;
    }
    // Read 64-bit entries from /proc/kpageflags or /proc/kpagecount.
    if (!kpage_file.PreadFully(page_flags_or_counts.data() + start,
                               (i - start) * kPageMapEntrySize,
                               page_frame_numbers[start] * kPageFlagsEntrySize)) {
      error_msg = StringPrintf("Failed to read the page flags or counts from %s, error: %s",
                               kpage_file.GetPath().c_str(),
                               strerror(errno));
      return false;
    }
  }

  return true;
}

bool GetPageFrameNumber(File& page_map_file,
                        size_t virtual_page_index,
                        /*out*/ uint64_t& page_frame_number,
                        /*out*/ std::string& error_msg) {
  return GetPageFrameNumbers(
      page_map_file, virtual_page_index, ArrayRef<uint64_t>(&page_frame_number, 1u), error_msg);
}

bool GetPageFrameNumbers(File& page_map_file,
                         size_t virtual_page_index,
                         /*out*/ ArrayRef<uint64_t> page_frame_numbers,
                         /*out*/ std::string& error_msg) {
  CHECK_NE(page_frame_numbers.size(), 0u);
  CHECK(page_frame_numbers.data() != nullptr);

  // Read 64-bit entries from /proc/$pid/pagemap to get the physical page frame numbers.
  if (!page_map_file.PreadFully(page_frame_numbers.data(),
                                page_frame_numbers.size() * kPageMapEntrySize,
                                virtual_page_index * kPageMapEntrySize)) {
    error_msg = StringPrintf("Failed to read virtual page index entries from %s, error: %s",
                             page_map_file.GetPath().c_str(),
                             strerror(errno));
    return false;
  }

  // Extract page frame numbers from pagemap entries.
  for (uint64_t& page_frame_number : page_frame_numbers) {
    page_frame_number &= kPageFrameNumberMask;
  }

  return true;
}

}  // namespace art
