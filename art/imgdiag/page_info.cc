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

#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <functional>
#include <optional>
#include <ostream>
#include <string>
#include <vector>

#include "android-base/parseint.h"
#include "android-base/stringprintf.h"
#include "base/os.h"
#include "base/unix_file/fd_file.h"
#include "cmdline.h"
#include "page_util.h"
#include "procinfo/process_map.h"
#include "scoped_thread_state_change-inl.h"

namespace art {

using android::base::StringPrintf;

namespace {

struct ProcFiles {
  // A File for reading /proc/<pid>/mem.
  File mem;
  // A File for reading /proc/<pid>/pagemap.
  File pagemap;
  // A File for reading /proc/kpageflags.
  File kpageflags;
  // A File for reading /proc/kpagecount.
  File kpagecount;
};

bool OpenFile(const char* file_name, /*out*/ File& file, /*out*/ std::string& error_msg) {
  std::unique_ptr<File> file_ptr = std::unique_ptr<File>{OS::OpenFileForReading(file_name)};
  if (file_ptr == nullptr) {
    error_msg = StringPrintf("Failed to open file: %s", file_name);
    return false;
  }
  file = std::move(*file_ptr);
  return true;
}

bool OpenProcFiles(pid_t pid, /*out*/ ProcFiles& files, /*out*/ std::string& error_msg) {
  if (!OpenFile("/proc/kpageflags", files.kpageflags, error_msg)) {
    return false;
  }
  if (!OpenFile("/proc/kpagecount", files.kpagecount, error_msg)) {
    return false;
  }
  std::string mem_file_name =
      StringPrintf("/proc/%ld/mem", static_cast<long>(pid));  // NOLINT [runtime/int]
  if (!OpenFile(mem_file_name.c_str(), files.mem, error_msg)) {
    return false;
  }
  std::string pagemap_file_name =
      StringPrintf("/proc/%ld/pagemap", static_cast<long>(pid));  // NOLINT [runtime/int]
  if (!OpenFile(pagemap_file_name.c_str(), files.pagemap, error_msg)) {
    return false;
  }
  return true;
}

void DumpPageInfo(uint64_t virtual_page_index, ProcFiles& proc_files, std::ostream& os,
                  size_t page_size) {
  const uint64_t virtual_page_addr = virtual_page_index * page_size;
  os << "Virtual page index: " << virtual_page_index << "\n";
  os << "Virtual page addr: " << virtual_page_addr << "\n";

  std::string error_msg;
  uint64_t page_frame_number = -1;
  if (!GetPageFrameNumber(
          proc_files.pagemap, virtual_page_index, /*out*/ page_frame_number, /*out*/ error_msg)) {
    os << "Failed to get page frame number: " << error_msg << "\n";
    return;
  }
  os << "Page frame number: " << page_frame_number << "\n";

  uint64_t page_count = -1;
  if (!GetPageFlagsOrCount(proc_files.kpagecount,
                           page_frame_number,
                           /*out*/ page_count,
                           /*out*/ error_msg)) {
    os << "Failed to get page count: " << error_msg << "\n";
    return;
  }
  os << "kpagecount: " << page_count << "\n";

  uint64_t page_flags = 0;
  if (!GetPageFlagsOrCount(proc_files.kpageflags,
                           page_frame_number,
                           /*out*/ page_flags,
                           /*out*/ error_msg)) {
    os << "Failed to get page flags: " << error_msg << "\n";
    return;
  }
  os << "kpageflags: " << page_flags << "\n";

  if (page_count != 0) {
    std::vector<uint8_t> page_contents(page_size);
    if (!proc_files.mem.PreadFully(page_contents.data(), page_contents.size(), virtual_page_addr)) {
      os << "Failed to read page contents\n";
      return;
    }
    os << "Zero bytes: " << std::count(std::begin(page_contents), std::end(page_contents), 0)
       << "\n";
  }
}

struct MapPageCounts {
  // Present pages count.
  uint64_t pages = 0;
  // Non-present pages count.
  uint64_t non_present_pages = 0;
  // Private (kpagecount == 1) zero page count.
  uint64_t private_zero_pages = 0;
  // Shared (kpagecount > 1) zero page count.
  uint64_t shared_zero_pages = 0;
  // Physical frame numbers of zero pages.
  std::unordered_set<uint64_t> zero_page_pfns;

  // Memory map name.
  std::string name;
  // Memory map start address.
  uint64_t start = 0;
  // Memory map end address.
  uint64_t end = 0;
};

bool GetMapPageCounts(ProcFiles& proc_files,
                      const android::procinfo::MapInfo& map_info,
                      MapPageCounts& map_page_counts,
                      std::string& error_msg,
                      size_t page_size) {
  map_page_counts.name = map_info.name;
  map_page_counts.start = map_info.start;
  map_page_counts.end = map_info.end;
  std::vector<uint8_t> page_contents(page_size);
  for (uint64_t begin = map_info.start; begin < map_info.end; begin += page_size) {
    const size_t virtual_page_index = begin / page_size;
    uint64_t page_frame_number = -1;
    if (!GetPageFrameNumber(proc_files.pagemap, virtual_page_index, page_frame_number, error_msg)) {
      return false;
    }
    uint64_t page_count = -1;
    if (!GetPageFlagsOrCounts(proc_files.kpagecount,
                              ArrayRef<const uint64_t>(&page_frame_number, 1),
                              /*out*/ ArrayRef<uint64_t>(&page_count, 1),
                              /*out*/ error_msg)) {
      return false;
    }

    const auto is_zero_page = [](const std::vector<uint8_t>& page) {
      const auto non_zero_it =
          std::find_if(std::begin(page), std::end(page), [](uint8_t b) { return b != 0; });
      return non_zero_it == std::end(page);
    };

    if (page_count == 0) {
      map_page_counts.non_present_pages += 1;
      continue;
    }

    // Handle present page.
    if (!proc_files.mem.PreadFully(page_contents.data(), page_contents.size(), begin)) {
      error_msg = StringPrintf(
          "Failed to read present page %" PRIx64 " for mapping %s\n", begin, map_info.name.c_str());
      return false;
    }
    const bool is_zero = is_zero_page(page_contents);
    const bool is_private = (page_count == 1);
    map_page_counts.pages += 1;
    if (is_zero) {
      map_page_counts.zero_page_pfns.insert(page_frame_number);
      if (is_private) {
        map_page_counts.private_zero_pages += 1;
      } else {
        map_page_counts.shared_zero_pages += 1;
      }
    }
  }
  return true;
}

void CountZeroPages(pid_t pid, ProcFiles& proc_files, std::ostream& os, size_t page_size) {
  std::vector<android::procinfo::MapInfo> proc_maps;
  if (!android::procinfo::ReadProcessMaps(pid, &proc_maps)) {
    os << "Could not read process maps for " << pid;
    return;
  }

  MapPageCounts total;
  std::vector<MapPageCounts> stats;
  for (const android::procinfo::MapInfo& map_info : proc_maps) {
    MapPageCounts map_page_counts;
    std::string error_msg;
    if (!GetMapPageCounts(proc_files, map_info, map_page_counts, error_msg, page_size)) {
      os << "Error getting map page counts for: " << map_info.name << "\n" << error_msg << "\n\n";
      continue;
    }
    total.pages += map_page_counts.pages;
    total.private_zero_pages += map_page_counts.private_zero_pages;
    total.shared_zero_pages += map_page_counts.shared_zero_pages;
    total.non_present_pages += map_page_counts.non_present_pages;
    total.zero_page_pfns.insert(std::begin(map_page_counts.zero_page_pfns),
                                std::end(map_page_counts.zero_page_pfns));
    stats.push_back(std::move(map_page_counts));
  }

  // Sort by different page counts, descending.
  const auto sort_by_private_zero_pages = [](const auto& stats1, const auto& stats2) {
    return stats1.private_zero_pages > stats2.private_zero_pages;
  };
  const auto sort_by_shared_zero_pages = [](const auto& stats1, const auto& stats2) {
    return stats1.shared_zero_pages > stats2.shared_zero_pages;
  };
  const auto sort_by_unique_zero_pages = [](const auto& stats1, const auto& stats2) {
    return stats1.zero_page_pfns.size() > stats2.zero_page_pfns.size();
  };

  // Print up to `max_lines` entries.
  const auto print_stats = [&stats, &os](size_t max_lines) {
    for (const MapPageCounts& map_page_counts : stats) {
      if (max_lines == 0) {
        return;
      }
      // Skip entries with no present pages.
      if (map_page_counts.pages == 0) {
        continue;
      }
      max_lines -= 1;
      os << StringPrintf("%" PRIx64 "-%" PRIx64 " %s: pages=%" PRIu64
                         ", private_zero_pages=%" PRIu64 ", shared_zero_pages=%" PRIu64
                         ", unique_zero_pages=%" PRIu64 ", non_present_pages=%" PRIu64 "\n",
                         map_page_counts.start,
                         map_page_counts.end,
                         map_page_counts.name.c_str(),
                         map_page_counts.pages,
                         map_page_counts.private_zero_pages,
                         map_page_counts.shared_zero_pages,
                         uint64_t{map_page_counts.zero_page_pfns.size()},
                         map_page_counts.non_present_pages);
    }
  };

  os << StringPrintf("total_pages=%" PRIu64 ", total_private_zero_pages=%" PRIu64
                     ", total_shared_zero_pages=%" PRIu64 ", total_unique_zero_pages=%" PRIu64
                     ", total_non_present_pages=%" PRIu64 "\n",
                     total.pages,
                     total.private_zero_pages,
                     total.shared_zero_pages,
                     uint64_t{total.zero_page_pfns.size()},
                     total.non_present_pages);
  os << "\n\n";

  const size_t top_lines = std::min(size_t{20}, stats.size());
  std::partial_sort(
      std::begin(stats), std::begin(stats) + top_lines, std::end(stats), sort_by_unique_zero_pages);
  os << "Top " << top_lines << " maps by unique zero pages (unique PFN count)\n";
  print_stats(top_lines);
  os << "\n\n";

  std::partial_sort(std::begin(stats),
                    std::begin(stats) + top_lines,
                    std::end(stats),
                    sort_by_private_zero_pages);
  os << "Top " << top_lines << " maps by private zero pages (kpagecount == 1)\n";
  print_stats(top_lines);
  os << "\n\n";

  std::partial_sort(
      std::begin(stats), std::begin(stats) + top_lines, std::end(stats), sort_by_shared_zero_pages);
  os << "Top " << top_lines << " maps by shared zero pages (kpagecount > 1)\n";
  print_stats(top_lines);
  os << "\n\n";

  std::sort(std::begin(stats), std::end(stats), sort_by_unique_zero_pages);
  os << "All maps by unique zero pages (unique PFN count)\n";
  print_stats(stats.size());
  os << "\n\n";
}

}  // namespace

int PageInfo(std::ostream& os,
             pid_t pid,
             bool count_zero_pages,
             std::optional<uint64_t> virtual_page_index,
             size_t page_size) {
  ProcFiles proc_files;
  std::string error_msg;
  if (!OpenProcFiles(pid, proc_files, error_msg)) {
    os << error_msg;
    return EXIT_FAILURE;
  }
  if (virtual_page_index != std::nullopt) {
    DumpPageInfo(virtual_page_index.value(), proc_files, os, page_size);
  }
  if (count_zero_pages) {
    CountZeroPages(pid, proc_files, os, page_size);
  }
  return EXIT_SUCCESS;
}

struct PageInfoArgs : public CmdlineArgs {
 protected:
  using Base = CmdlineArgs;

  ParseStatus ParseCustom(const char* raw_option,
                          size_t raw_option_length,
                          std::string* error_msg) override {
    DCHECK_EQ(strlen(raw_option), raw_option_length);
    {
      ParseStatus base_parse = Base::ParseCustom(raw_option, raw_option_length, error_msg);
      if (base_parse != kParseUnknownArgument) {
        return base_parse;
      }
    }

    std::string_view option(raw_option, raw_option_length);
    if (StartsWith(option, "--pid=")) {
      // static_assert(std::is_signed_t
      const char* value = raw_option + strlen("--pid=");
      if (!android::base::ParseInt(value, &pid_)) {
        *error_msg = "Failed to parse pid";
        return kParseError;
      }
    } else if (option == "--count-zero-pages") {
      count_zero_pages_ = true;
    } else if (StartsWith(option, "--dump-page-info=")) {
      const char* value = raw_option + strlen("--dump-page-info=");
      virtual_page_index_ = 0;
      if (!android::base::ParseUint(value, &virtual_page_index_.value())) {
        *error_msg = "Failed to parse virtual page index";
        return kParseError;
      }
    } else {
      return kParseUnknownArgument;
    }

    return kParseOk;
  }

  ParseStatus ParseChecks(std::string* error_msg) override {
    // Perform the parent checks.
    ParseStatus parent_checks = Base::ParseChecks(error_msg);
    if (parent_checks != kParseOk) {
      return parent_checks;
    }
    if (pid_ == -1) {
      *error_msg = "Missing --pid=";
      return kParseError;
    }

    // Perform our own checks.
    if (kill(pid_, /*sig*/ 0) != 0) {  // No signal is sent, perform error-checking only.
      // Check if the pid exists before proceeding.
      if (errno == ESRCH) {
        *error_msg = "Process specified does not exist, pid: " + std::to_string(pid_);
      } else {
        *error_msg = StringPrintf("Failed to check process status: %s", strerror(errno));
      }
      return kParseError;
    }
    return kParseOk;
  }

  std::string GetUsage() const override {
    std::string usage;

    usage +=
        "Usage: pageinfo [options] ...\n"
        "    Example: pageinfo --pid=$(pidof system_server) --count-zero-pages\n"
        "    Example: adb shell pageinfo --pid=$(pid system_server) --dump-page-info=0x70000000\n"
        "\n";

    usage += Base::GetUsage();

    usage +=
        "  --pid=<pid>: PID of the process to analyze.\n"
        "  --count-zero-pages: output zero filled page stats for memory mappings of "
        "<image-diff-pid> process.\n"
        "  --dump-page-info=<virtual_page_index>: output PFN, kpagecount and kpageflags of a "
        "virtual page in <image-diff-pid> process memory space.\n";

    return usage;
  }

 public:
  pid_t pid_ = -1;
  bool count_zero_pages_ = false;
  std::optional<uint64_t> virtual_page_index_;
};

struct PageInfoMain : public CmdlineMain<PageInfoArgs> {
  bool ExecuteWithoutRuntime() override {
    CHECK(args_ != nullptr);
    CHECK(args_->os_ != nullptr);

    return PageInfo(
               *args_->os_, args_->pid_, args_->count_zero_pages_, args_->virtual_page_index_,
               MemMap::GetPageSize()) ==
           EXIT_SUCCESS;
  }

  bool NeedsRuntime() override { return false; }
};

}  // namespace art

int main(int argc, char** argv) {
  art::PageInfoMain main;
  return main.Main(argc, argv);
}
