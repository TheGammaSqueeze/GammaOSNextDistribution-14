/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <linux/kernel-page-flags.h>
#include <stdio.h>
#include <unistd.h>

#include <atomic>
#include <fstream>
#include <iostream>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android-base/unique_fd.h>
#include <procinfo/process_map.h>

#include "meminfo_private.h"

namespace android {
namespace meminfo {

// List of VMA names that we don't want to process:
//   - On ARM32, [vectors] is a special VMA that is outside of pagemap range.
//   - On x86, [vsyscall] is a kernel memory that is outside of pagemap range.
static const std::vector<std::string> g_excluded_vmas = {
    "[vectors]",
#ifdef __x86_64__
    "[vsyscall]"
#endif
};

static void add_mem_usage(MemUsage* to, const MemUsage& from) {
    to->vss += from.vss;
    to->rss += from.rss;
    to->pss += from.pss;
    to->uss += from.uss;

    to->swap += from.swap;

    to->private_clean += from.private_clean;
    to->private_dirty += from.private_dirty;

    to->shared_clean += from.shared_clean;
    to->shared_dirty += from.shared_dirty;
}

// Converts MemUsage stats from KB to B in case usage is expected in bytes.
static void convert_usage_kb_to_b(MemUsage& usage) {
    // These stats are only populated if /proc/<pid>/smaps is read, so they are excluded:
    // swap_pss, anon_huge_pages, shmem_pmdmapped, file_pmd_mapped, shared_hugetlb, private_hugetlb.
    constexpr int conversion_factor = 1024;
    usage.vss *= conversion_factor;
    usage.rss *= conversion_factor;
    usage.pss *= conversion_factor;
    usage.uss *= conversion_factor;

    usage.swap *= conversion_factor;

    usage.private_clean *= conversion_factor;
    usage.private_dirty *= conversion_factor;

    usage.shared_clean *= conversion_factor;
    usage.shared_dirty *= conversion_factor;

    usage.thp *= conversion_factor;
}

// Returns true if the line was valid smaps stats line false otherwise.
static bool parse_smaps_field(const char* line, MemUsage* stats) {
    const char *end = line;

    // https://lore.kernel.org/patchwork/patch/1088579/ introduced tabs. Handle this case as well.
    while (*end && !isspace(*end)) end++;
    if (*end && end > line && *(end - 1) == ':') {
        const char* c = end;
        while (isspace(*c)) c++;
        switch (line[0]) {
            case 'P':
                if (strncmp(line, "Pss:", 4) == 0) {
                    stats->pss = strtoull(c, nullptr, 10);
                } else if (strncmp(line, "Private_Clean:", 14) == 0) {
                    uint64_t prcl = strtoull(c, nullptr, 10);
                    stats->private_clean = prcl;
                    stats->uss += prcl;
                } else if (strncmp(line, "Private_Dirty:", 14) == 0) {
                    uint64_t prdi = strtoull(c, nullptr, 10);
                    stats->private_dirty = prdi;
                    stats->uss += prdi;
                } else if (strncmp(line, "Private_Hugetlb:", 16) == 0) {
                    stats->private_hugetlb = strtoull(c, nullptr, 10);
                }
                break;
            case 'S':
                if (strncmp(line, "Size:", 5) == 0) {
                    stats->vss = strtoull(c, nullptr, 10);
                } else if (strncmp(line, "Shared_Clean:", 13) == 0) {
                    stats->shared_clean = strtoull(c, nullptr, 10);
                } else if (strncmp(line, "Shared_Dirty:", 13) == 0) {
                    stats->shared_dirty = strtoull(c, nullptr, 10);
                } else if (strncmp(line, "Swap:", 5) == 0) {
                    stats->swap = strtoull(c, nullptr, 10);
                } else if (strncmp(line, "SwapPss:", 8) == 0) {
                    stats->swap_pss = strtoull(c, nullptr, 10);
                } else if (strncmp(line, "ShmemPmdMapped:", 15) == 0) {
                    stats->shmem_pmd_mapped = strtoull(c, nullptr, 10);
                } else if (strncmp(line, "Shared_Hugetlb:", 15) == 0) {
                    stats->shared_hugetlb = strtoull(c, nullptr, 10);
                }
                break;
            case 'R':
                if (strncmp(line, "Rss:", 4) == 0) {
                    stats->rss = strtoull(c, nullptr, 10);
                }
                break;
            case 'A':
                if (strncmp(line, "AnonHugePages:", 14) == 0) {
                    stats->anon_huge_pages = strtoull(c, nullptr, 10);
                }
                break;
            case 'F':
                if (strncmp(line, "FilePmdMapped:", 14) == 0) {
                    stats->file_pmd_mapped = strtoull(c, nullptr, 10);
                }
                break;
            case 'L':
                if (strncmp(line, "Locked:", 7) == 0) {
                    stats->locked = strtoull(c, nullptr, 10);
                }
                break;
        }
        return true;
    }

    return false;
}

bool ProcMemInfo::ResetWorkingSet(pid_t pid) {
    std::string clear_refs_path = ::android::base::StringPrintf("/proc/%d/clear_refs", pid);
    if (!::android::base::WriteStringToFile("1\n", clear_refs_path)) {
        PLOG(ERROR) << "Failed to write to " << clear_refs_path;
        return false;
    }

    return true;
}

ProcMemInfo::ProcMemInfo(pid_t pid, bool get_wss, uint64_t pgflags, uint64_t pgflags_mask)
    : pid_(pid), get_wss_(get_wss), pgflags_(pgflags), pgflags_mask_(pgflags_mask) {}

const std::vector<Vma>& ProcMemInfo::Maps() {
    if (maps_.empty() && !ReadMaps(get_wss_)) {
        LOG(ERROR) << "Failed to read maps for Process " << pid_;
    }

    return maps_;
}

const std::vector<Vma>& ProcMemInfo::MapsWithPageIdle() {
    if (maps_.empty() && !ReadMaps(get_wss_, true)) {
        LOG(ERROR) << "Failed to read maps with page idle for Process " << pid_;
    }

    return maps_;
}

const std::vector<Vma>& ProcMemInfo::MapsWithoutUsageStats() {
    if (maps_.empty() && !ReadMaps(get_wss_, false, false)) {
        LOG(ERROR) << "Failed to read maps for Process " << pid_;
    }

    return maps_;
}

const std::vector<Vma>& ProcMemInfo::Smaps(const std::string& path, bool collect_usage) {
    if (!maps_.empty()) {
        return maps_;
    }

    auto collect_vmas = [&](const Vma& vma) {
        if (std::find(g_excluded_vmas.begin(), g_excluded_vmas.end(), vma.name) ==
                g_excluded_vmas.end()) {
            maps_.emplace_back(vma);
            if (collect_usage) {
                add_mem_usage(&usage_, vma.usage);
            }
        }
    };
    if (path.empty() && !ForEachVma(collect_vmas)) {
        LOG(ERROR) << "Failed to read smaps for Process " << pid_;
        maps_.clear();
    }

    if (!path.empty() && !ForEachVmaFromFile(path, collect_vmas)) {
        LOG(ERROR) << "Failed to read smaps from file " << path;
        maps_.clear();
    }

    return maps_;
}

const MemUsage& ProcMemInfo::Usage() {
    if (get_wss_) {
        LOG(WARNING) << "Trying to read process memory usage for " << pid_
                     << " using invalid object";
        return usage_;
    }

    if (maps_.empty() && !ReadMaps(get_wss_)) {
        LOG(ERROR) << "Failed to get memory usage for Process " << pid_;
    }

    return usage_;
}

const MemUsage& ProcMemInfo::Wss() {
    if (!get_wss_) {
        LOG(WARNING) << "Trying to read process working set for " << pid_
                     << " using invalid object";
        return usage_;
    }

    if (maps_.empty() && !ReadMaps(get_wss_)) {
        LOG(ERROR) << "Failed to get working set for Process " << pid_;
    }

    return usage_;
}

bool ProcMemInfo::ForEachVma(const VmaCallback& callback, bool use_smaps) {
    std::string path =
            ::android::base::StringPrintf("/proc/%d/%s", pid_, use_smaps ? "smaps" : "maps");
    return ForEachVmaFromFile(path, callback, use_smaps);
}

bool ProcMemInfo::ForEachExistingVma(const VmaCallback& callback) {
    if (maps_.empty()) {
        return false;
    }
    for (auto& vma : maps_) {
        callback(vma);
    }
    return true;
}

bool ProcMemInfo::ForEachVmaFromMaps(const VmaCallback& callback) {
    Vma vma;
    auto vmaCollect = [&callback,&vma](const uint64_t start, uint64_t end, uint16_t flags,
                            uint64_t pgoff, ino_t inode, const char* name, bool shared) {
        vma.start = start;
        vma.end = end;
        vma.flags = flags;
        vma.offset = pgoff;
        vma.name = name;
        vma.inode = inode;
        vma.is_shared = shared;
        callback(vma);
    };

    bool success = ::android::procinfo::ReadProcessMaps(pid_, vmaCollect);

    return success;
}

bool ProcMemInfo::ForEachVmaFromMaps(const VmaCallback& callback, std::string& mapsBuffer) {
    Vma vma;
    vma.name.reserve(256);
    auto vmaCollect = [&callback,&vma](const uint64_t start, uint64_t end, uint16_t flags,
                            uint64_t pgoff, ino_t inode, const char* name, bool shared) {
        vma.start = start;
        vma.end = end;
        vma.flags = flags;
        vma.offset = pgoff;
        vma.name = name;
        vma.inode = inode;
        vma.is_shared = shared;
        callback(vma);
    };

    bool success = ::android::procinfo::ReadProcessMaps(pid_, vmaCollect, mapsBuffer);

    return success;
}

bool ProcMemInfo::SmapsOrRollup(MemUsage* stats) const {
    std::string path = ::android::base::StringPrintf(
            "/proc/%d/%s", pid_, IsSmapsRollupSupported() ? "smaps_rollup" : "smaps");
    return SmapsOrRollupFromFile(path, stats);
}

bool ProcMemInfo::SmapsOrRollupPss(uint64_t* pss) const {
    std::string path = ::android::base::StringPrintf(
            "/proc/%d/%s", pid_, IsSmapsRollupSupported() ? "smaps_rollup" : "smaps");
    return SmapsOrRollupPssFromFile(path, pss);
}

bool ProcMemInfo::StatusVmRSS(uint64_t* rss) const {
    std::string path = ::android::base::StringPrintf("/proc/%d/status", pid_);
    return StatusVmRSSFromFile(path, rss);
}

const std::vector<uint64_t>& ProcMemInfo::SwapOffsets() {
    if (get_wss_) {
        LOG(WARNING) << "Trying to read process swap offsets for " << pid_
                     << " using invalid object";
        return swap_offsets_;
    }

    if (maps_.empty() && !ReadMaps(get_wss_, false, true, true)) {
        LOG(ERROR) << "Failed to get swap offsets for Process " << pid_;
    }

    return swap_offsets_;
}

bool ProcMemInfo::PageMap(const Vma& vma, std::vector<uint64_t>* pagemap) {
    pagemap->clear();
    std::string pagemap_file = ::android::base::StringPrintf("/proc/%d/pagemap", pid_);
    ::android::base::unique_fd pagemap_fd(
            TEMP_FAILURE_RETRY(open(pagemap_file.c_str(), O_RDONLY | O_CLOEXEC)));
    if (pagemap_fd == -1) {
        PLOG(ERROR) << "Failed to open " << pagemap_file;
        return false;
    }

    uint64_t nr_pages = (vma.end - vma.start) / getpagesize();
    pagemap->resize(nr_pages);

    size_t bytes_to_read = sizeof(uint64_t) * nr_pages;
    off64_t start_addr = (vma.start / getpagesize()) * sizeof(uint64_t);
    ssize_t bytes_read = pread64(pagemap_fd, pagemap->data(), bytes_to_read, start_addr);
    if (bytes_read == -1) {
        PLOG(ERROR) << "Failed to read page frames from page map for pid: " << pid_;
        return false;
    } else if (static_cast<size_t>(bytes_read) != bytes_to_read) {
        LOG(ERROR) << "Failed to read page frames from page map for pid: " << pid_
                   << ": read bytes " << bytes_read << " expected bytes " << bytes_to_read;
        return false;
    }

    return true;
}

static int GetPagemapFd(pid_t pid) {
    std::string pagemap_file = ::android::base::StringPrintf("/proc/%d/pagemap", pid);
    int fd = TEMP_FAILURE_RETRY(open(pagemap_file.c_str(), O_RDONLY | O_CLOEXEC));
    if (fd == -1) {
        PLOG(ERROR) << "Failed to open " << pagemap_file;
    }
    return fd;
}

bool ProcMemInfo::ReadMaps(bool get_wss, bool use_pageidle, bool get_usage_stats, bool swap_only) {
    // Each object reads /proc/<pid>/maps only once. This is done to make sure programs that are
    // running for the lifetime of the system can recycle the objects and don't have to
    // unnecessarily retain and update this object in memory (which can get significantly large).
    // E.g. A program that only needs to reset the working set will never all ->Maps(), ->Usage().
    // E.g. A program that is monitoring smaps_rollup, may never call ->maps(), Usage(), so it
    // doesn't make sense for us to parse and retain unnecessary memory accounting stats by default.
    if (!maps_.empty()) return true;

    // parse and read /proc/<pid>/maps
    std::string maps_file = ::android::base::StringPrintf("/proc/%d/maps", pid_);
    if (!::android::procinfo::ReadMapFile(
                maps_file, [&](const android::procinfo::MapInfo& mapinfo) {
                    if (std::find(g_excluded_vmas.begin(), g_excluded_vmas.end(), mapinfo.name) ==
                            g_excluded_vmas.end()) {
                      maps_.emplace_back(Vma(mapinfo.start, mapinfo.end,
                                             mapinfo.pgoff, mapinfo.flags,
                                             mapinfo.name,
                                             mapinfo.inode, mapinfo.shared));
                    }
                })) {
        LOG(ERROR) << "Failed to parse " << maps_file;
        maps_.clear();
        return false;
    }

    if (!get_usage_stats) {
        return true;
    }

    if (!GetUsageStats(get_wss, use_pageidle, swap_only)) {
        maps_.clear();
        return false;
    }
    return true;
}

bool ProcMemInfo::GetUsageStats(bool get_wss, bool use_pageidle, bool swap_only) {
    ::android::base::unique_fd pagemap_fd(GetPagemapFd(pid_));
    if (pagemap_fd == -1) {
        return false;
    }

    for (auto& vma : maps_) {
        if (!ReadVmaStats(pagemap_fd.get(), vma, get_wss, use_pageidle, swap_only)) {
            LOG(ERROR) << "Failed to read page map for vma " << vma.name << "[" << vma.start << "-"
                       << vma.end << "]";
            return false;
        }
        add_mem_usage(&usage_, vma.usage);
    }

    return true;
}

bool ProcMemInfo::FillInVmaStats(Vma& vma, bool use_kb) {
    ::android::base::unique_fd pagemap_fd(GetPagemapFd(pid_));
    if (pagemap_fd == -1) {
        return false;
    }

    if (!ReadVmaStats(pagemap_fd.get(), vma, get_wss_, false, false)) {
        LOG(ERROR) << "Failed to read page map for vma " << vma.name << "[" << vma.start << "-"
                   << vma.end << "]";
        return false;
    }
    if (!use_kb) {
        convert_usage_kb_to_b(vma.usage);
    }
    return true;
}

bool ProcMemInfo::ReadVmaStats(int pagemap_fd, Vma& vma, bool get_wss, bool use_pageidle,
                               bool swap_only) {
    PageAcct& pinfo = PageAcct::Instance();
    if (get_wss && use_pageidle && !pinfo.InitPageAcct(true)) {
        LOG(ERROR) << "Failed to init idle page accounting";
        return false;
    }

    uint64_t pagesz_kb = getpagesize() / 1024;
    size_t num_pages = (vma.end - vma.start) / (pagesz_kb * 1024);
    size_t first_page = vma.start / (pagesz_kb * 1024);

    std::vector<uint64_t> page_cache;
    size_t cur_page_cache_index = 0;
    size_t num_in_page_cache = 0;
    size_t num_leftover_pages = num_pages;
    for (size_t cur_page = first_page; cur_page < first_page + num_pages; ++cur_page) {
        // Cache page map data.
        if (cur_page_cache_index == num_in_page_cache) {
            static constexpr size_t kMaxPages = 2048;
            num_leftover_pages -= num_in_page_cache;
            if (num_leftover_pages > kMaxPages) {
                num_in_page_cache = kMaxPages;
            } else {
                num_in_page_cache = num_leftover_pages;
            }
            page_cache.resize(num_in_page_cache);
            size_t total_bytes = page_cache.size() * sizeof(uint64_t);
            ssize_t bytes = pread64(pagemap_fd, page_cache.data(), total_bytes,
                                    cur_page * sizeof(uint64_t));
            if (bytes != total_bytes) {
                if (bytes == -1) {
                    PLOG(ERROR) << "Failed to read page data at offset 0x" << std::hex
                                << cur_page * sizeof(uint64_t);
                } else {
                    LOG(ERROR) << "Failed to read page data at offset 0x" << std::hex
                               << cur_page * sizeof(uint64_t) << std::dec << " read bytes " << bytes
                               << " expected bytes " << total_bytes;
                }
                return false;
            }
            cur_page_cache_index = 0;
        }

        uint64_t page_info = page_cache[cur_page_cache_index++];
        if (!PAGE_PRESENT(page_info) && !PAGE_SWAPPED(page_info)) continue;

        if (PAGE_SWAPPED(page_info)) {
            vma.usage.swap += pagesz_kb;
            swap_offsets_.emplace_back(PAGE_SWAP_OFFSET(page_info));
            continue;
        }

        if (swap_only)
            continue;

        uint64_t page_frame = PAGE_PFN(page_info);
        uint64_t cur_page_flags;
        if (!pinfo.PageFlags(page_frame, &cur_page_flags)) {
            LOG(ERROR) << "Failed to get page flags for " << page_frame << " in process " << pid_;
            swap_offsets_.clear();
            return false;
        }

        if (KPAGEFLAG_THP(cur_page_flags)) {
            vma.usage.thp += pagesz_kb;
        }

        // skip unwanted pages from the count
        if ((cur_page_flags & pgflags_mask_) != pgflags_) continue;

        uint64_t cur_page_counts;
        if (!pinfo.PageMapCount(page_frame, &cur_page_counts)) {
            LOG(ERROR) << "Failed to get page count for " << page_frame << " in process " << pid_;
            swap_offsets_.clear();
            return false;
        }

        // Page was unmapped between the presence check at the beginning of the loop and here.
        if (cur_page_counts == 0) {
            continue;
        }

        bool is_dirty = !!(cur_page_flags & (1 << KPF_DIRTY));
        bool is_private = (cur_page_counts == 1);
        // Working set
        if (get_wss) {
            bool is_referenced = use_pageidle ? (pinfo.IsPageIdle(page_frame) == 1)
                                              : !!(cur_page_flags & (1 << KPF_REFERENCED));
            if (!is_referenced) {
                continue;
            }
            // This effectively makes vss = rss for the working set is requested.
            // The libpagemap implementation returns vss > rss for
            // working set, which doesn't make sense.
            vma.usage.vss += pagesz_kb;
        }

        vma.usage.rss += pagesz_kb;
        vma.usage.uss += is_private ? pagesz_kb : 0;
        vma.usage.pss += pagesz_kb / cur_page_counts;
        if (is_private) {
            vma.usage.private_dirty += is_dirty ? pagesz_kb : 0;
            vma.usage.private_clean += is_dirty ? 0 : pagesz_kb;
        } else {
            vma.usage.shared_dirty += is_dirty ? pagesz_kb : 0;
            vma.usage.shared_clean += is_dirty ? 0 : pagesz_kb;
        }
    }
    if (!get_wss) {
        vma.usage.vss += pagesz_kb * num_pages;
    }
    return true;
}

// Public APIs
bool ForEachVmaFromFile(const std::string& path, const VmaCallback& callback,
                        bool read_smaps_fields) {
    auto fp = std::unique_ptr<FILE, decltype(&fclose)>{fopen(path.c_str(), "re"), fclose};
    if (fp == nullptr) {
        return false;
    }

    char* line = nullptr;
    bool parsing_vma = false;
    ssize_t line_len;
    size_t line_alloc = 0;
    Vma vma;
    while ((line_len = getline(&line, &line_alloc, fp.get())) > 0) {
        // Make sure the line buffer terminates like a C string for ReadMapFile
        line[line_len] = '\0';

        if (parsing_vma) {
            if (parse_smaps_field(line, &vma.usage)) {
                // This was a stats field
                continue;
            }

            // Done collecting stats, make the call back
            callback(vma);
            parsing_vma = false;
        }

        vma.clear();
        // If it has, we are looking for the vma stats
        // 00400000-00409000 r-xp 00000000 fc:00 426998  /usr/lib/gvfs/gvfsd-http
        if (!::android::procinfo::ReadMapFileContent(
                    line, [&](const android::procinfo::MapInfo& mapinfo) {
                        vma.start = mapinfo.start;
                        vma.end = mapinfo.end;
                        vma.flags = mapinfo.flags;
                        vma.offset = mapinfo.pgoff;
                        vma.name = mapinfo.name;
                        vma.inode = mapinfo.inode;
                        vma.is_shared = mapinfo.shared;
                    })) {
            // free getline() managed buffer
            free(line);
            LOG(ERROR) << "Failed to parse " << path;
            return false;
        }
        if (read_smaps_fields) {
            parsing_vma = true;
        } else {
            // Done collecting stats, make the call back
            callback(vma);
        }
    }

    // free getline() managed buffer
    free(line);

    if (parsing_vma) {
        callback(vma);
    }

    return true;
}

enum smaps_rollup_support { UNTRIED, SUPPORTED, UNSUPPORTED };

static std::atomic<smaps_rollup_support> g_rollup_support = UNTRIED;

bool IsSmapsRollupSupported() {
    // Similar to OpenSmapsOrRollup checks from android_os_Debug.cpp, except
    // the method only checks if rollup is supported and returns the status
    // right away.
    enum smaps_rollup_support rollup_support = g_rollup_support.load(std::memory_order_relaxed);
    if (rollup_support != UNTRIED) {
        return rollup_support == SUPPORTED;
    }

    // Check the calling process for smaps_rollup since it is guaranteed to be alive
    if (access("/proc/self/smaps_rollup", F_OK | R_OK)) {
        g_rollup_support.store(UNSUPPORTED, std::memory_order_relaxed);
        return false;
    }

    g_rollup_support.store(SUPPORTED, std::memory_order_relaxed);
    LOG(INFO) << "Using smaps_rollup for pss collection";
    return true;
}

bool SmapsOrRollupFromFile(const std::string& path, MemUsage* stats) {
    auto fp = std::unique_ptr<FILE, decltype(&fclose)>{fopen(path.c_str(), "re"), fclose};
    if (fp == nullptr) {
        return false;
    }

    char* line = nullptr;
    size_t line_alloc = 0;
    stats->clear();
    while (getline(&line, &line_alloc, fp.get()) > 0) {
        switch (line[0]) {
            case 'P':
                if (strncmp(line, "Pss:", 4) == 0) {
                    char* c = line + 4;
                    stats->pss += strtoull(c, nullptr, 10);
                } else if (strncmp(line, "Private_Clean:", 14) == 0) {
                    char* c = line + 14;
                    uint64_t prcl = strtoull(c, nullptr, 10);
                    stats->private_clean += prcl;
                    stats->uss += prcl;
                } else if (strncmp(line, "Private_Dirty:", 14) == 0) {
                    char* c = line + 14;
                    uint64_t prdi = strtoull(c, nullptr, 10);
                    stats->private_dirty += prdi;
                    stats->uss += prdi;
                }
                break;
            case 'R':
                if (strncmp(line, "Rss:", 4) == 0) {
                    char* c = line + 4;
                    stats->rss += strtoull(c, nullptr, 10);
                }
                break;
            case 'S':
                if (strncmp(line, "SwapPss:", 8) == 0) {
                    char* c = line + 8;
                    stats->swap_pss += strtoull(c, nullptr, 10);
                }
                break;
        }
    }

    // free getline() managed buffer
    free(line);
    return true;
}

bool SmapsOrRollupPssFromFile(const std::string& path, uint64_t* pss) {
    auto fp = std::unique_ptr<FILE, decltype(&fclose)>{fopen(path.c_str(), "re"), fclose};
    if (fp == nullptr) {
        return false;
    }
    *pss = 0;
    char* line = nullptr;
    size_t line_alloc = 0;
    while (getline(&line, &line_alloc, fp.get()) > 0) {
        uint64_t v;
        if (sscanf(line, "Pss: %" SCNu64 " kB", &v) == 1) {
            *pss += v;
        }
    }

    // free getline() managed buffer
    free(line);
    return true;
}

bool StatusVmRSSFromFile(const std::string& path, uint64_t* rss) {
    auto fp = std::unique_ptr<FILE, decltype(&fclose)>{fopen(path.c_str(), "re"), fclose};
    if (fp == nullptr) {
        return false;
    }

    // We use this bool because -1 as an "invalid" value for RSS will wrap
    // around to a positive number.
    bool success = false;

    *rss = 0;
    char* line = nullptr;
    size_t line_alloc = 0;
    while (getline(&line, &line_alloc, fp.get()) > 0) {
        uint64_t v;
        if (sscanf(line, "VmRSS: %" SCNu64 " kB", &v) == 1) {
            *rss = v;
            success = true;
            // Break because there is only one VmRSS field in status.
            break;
        }
    }

    // free getline() managed buffer
    free(line);
    return success;
}

Format GetFormat(std::string_view arg) {
    if (arg == "json") {
        return Format::JSON;
    }
    if (arg == "csv") {
        return Format::CSV;
    }
    if (arg == "raw") {
        return Format::RAW;
    }
    return Format::INVALID;
}

std::string EscapeCsvString(const std::string& raw) {
    std::string ret;
    for (auto it = raw.cbegin(); it != raw.cend(); it++) {
        switch (*it) {
            case '"':
                ret += "\"";
                break;
            default:
                ret += *it;
                break;
        }
    }
    return '"' + ret + '"';
}

std::string EscapeJsonString(const std::string& raw) {
    std::string ret;
    for (auto it = raw.cbegin(); it != raw.cend(); it++) {
        switch (*it) {
            case '\\':
                ret += "\\\\";
                break;
            case '"':
                ret += "\\\"";
                break;
            case '/':
                ret += "\\/";
                break;
            case '\b':
                ret += "\\b";
                break;
            case '\f':
                ret += "\\f";
                break;
            case '\n':
                ret += "\\n";
                break;
            case '\r':
                ret += "\\r";
                break;
            case '\t':
                ret += "\\t";
                break;
            default:
                ret += *it;
                break;
        }
    }
    return '"' + ret + '"';
}

}  // namespace meminfo
}  // namespace android
