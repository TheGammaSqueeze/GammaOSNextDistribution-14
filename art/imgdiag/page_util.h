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

#ifndef ART_IMGDIAG_PAGE_UTIL_H_
#define ART_IMGDIAG_PAGE_UTIL_H_

#include <cstdint>

#include "base/array_ref.h"
#include "base/os.h"
#include "base/unix_file/fd_file.h"

namespace art {
static constexpr size_t kPageMapEntrySize = sizeof(uint64_t);
// bits 0-54 [in /proc/$pid/pagemap]
static constexpr uint64_t kPageFrameNumberMask = (1ULL << 55) - 1;

static constexpr size_t kPageFlagsEntrySize = sizeof(uint64_t);
static constexpr size_t kPageCountEntrySize = sizeof(uint64_t);
static constexpr uint64_t kPageFlagsDirtyMask = (1ULL << 4);    // in /proc/kpageflags
static constexpr uint64_t kPageFlagsNoPageMask = (1ULL << 20);  // in /proc/kpageflags
static constexpr uint64_t kPageFlagsMmapMask = (1ULL << 11);    // in /proc/kpageflags

// Note: On failure, `page_flags_or_counts[.]` shall be clobbered.
bool GetPageFlagsOrCount(art::File& kpage_file,
                         uint64_t page_frame_number,
                         /*out*/ uint64_t& page_flags_or_count,
                         /*out*/ std::string& error_msg);

bool GetPageFlagsOrCounts(art::File& kpage_file,
                          ArrayRef<const uint64_t> page_frame_numbers,
                          /*out*/ ArrayRef<uint64_t> page_flags_or_counts,
                          /*out*/ std::string& error_msg);

// Note: On failure, `*page_frame_number` shall be clobbered.
bool GetPageFrameNumber(art::File& page_map_file,
                        size_t virtual_page_index,
                        /*out*/ uint64_t& page_frame_number,
                        /*out*/ std::string& error_msg);

bool GetPageFrameNumbers(art::File& page_map_file,
                         size_t virtual_page_index,
                         /*out*/ ArrayRef<uint64_t> page_frame_numbers,
                         /*out*/ std::string& error_msg);

}  // namespace art

#endif  // ART_IMGDIAG_PAGE_UTIL_H_
