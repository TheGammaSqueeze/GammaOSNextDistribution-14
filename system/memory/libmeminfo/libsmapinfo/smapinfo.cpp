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

#include <inttypes.h>
#include <linux/oom.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <unistd.h>

#include <chrono>
#include <functional>
#include <iomanip>
#include <iostream>
#include <set>
#include <string>
#include <vector>

#include <android-base/file.h>
#include <android-base/parseint.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <meminfo/sysmeminfo.h>

#include <processrecord.h>
#include <smapinfo.h>

namespace android {
namespace smapinfo {

using ::android::base::StringPrintf;
using ::android::meminfo::EscapeCsvString;
using ::android::meminfo::EscapeJsonString;
using ::android::meminfo::Format;
using ::android::meminfo::MemUsage;
using ::android::meminfo::Vma;

bool get_all_pids(std::set<pid_t>* pids) {
    pids->clear();
    std::unique_ptr<DIR, int (*)(DIR*)> procdir(opendir("/proc"), closedir);
    if (!procdir) return false;

    struct dirent* dir;
    pid_t pid;
    while ((dir = readdir(procdir.get()))) {
        if (!::android::base::ParseInt(dir->d_name, &pid)) continue;
        pids->insert(pid);
    }
    return true;
}

namespace procrank {

static bool count_swap_offsets(const ProcessRecord& proc, std::vector<uint16_t>& swap_offset_array,
                               std::ostream& err) {
    const std::vector<uint64_t>& swp_offs = proc.SwapOffsets();
    for (auto& off : swp_offs) {
        if (off >= swap_offset_array.size()) {
            err << "swap offset " << off << " is out of bounds for process: " << proc.pid() << "\n";
            return false;
        }
        if (swap_offset_array[off] == USHRT_MAX) {
            err << "swap offset " << off << " ref count overflow in process: " << proc.pid()
                << "\n";
            return false;
        }
        swap_offset_array[off]++;
    }
    return true;
}

struct params {
    // Calculated total memory usage across all processes in the system.
    uint64_t total_pss;
    uint64_t total_uss;
    uint64_t total_swap;
    uint64_t total_pswap;
    uint64_t total_uswap;
    uint64_t total_zswap;

    // Print options.
    bool show_oomadj;
    bool show_wss;
    bool swap_enabled;
    bool zram_enabled;

    // If zram is enabled, the compression ratio is zram used / swap used.
    float zram_compression_ratio;
};

static std::function<bool(ProcessRecord& a, ProcessRecord& b)> select_sort(struct params* params,
                                                                           SortOrder sort_order) {
    // Create sort function based on sort_order.
    std::function<bool(ProcessRecord & a, ProcessRecord & b)> proc_sort;
    switch (sort_order) {
        case (SortOrder::BY_OOMADJ):
            proc_sort = [](ProcessRecord& a, ProcessRecord& b) { return a.oomadj() > b.oomadj(); };
            break;
        case (SortOrder::BY_RSS):
            proc_sort = [=](ProcessRecord& a, ProcessRecord& b) {
                return a.Usage(params->show_wss).rss > b.Usage(params->show_wss).rss;
            };
            break;
        case (SortOrder::BY_SWAP):
            proc_sort = [=](ProcessRecord& a, ProcessRecord& b) {
                return a.Usage(params->show_wss).swap > b.Usage(params->show_wss).swap;
            };
            break;
        case (SortOrder::BY_USS):
            proc_sort = [=](ProcessRecord& a, ProcessRecord& b) {
                return a.Usage(params->show_wss).uss > b.Usage(params->show_wss).uss;
            };
            break;
        case (SortOrder::BY_VSS):
            proc_sort = [=](ProcessRecord& a, ProcessRecord& b) {
                return a.Usage(params->show_wss).vss > b.Usage(params->show_wss).vss;
            };
            break;
        case (SortOrder::BY_PSS):
        default:
            proc_sort = [=](ProcessRecord& a, ProcessRecord& b) {
                return a.Usage(params->show_wss).pss > b.Usage(params->show_wss).pss;
            };
            break;
    }
    return proc_sort;
}

static bool populate_procs(struct params* params, uint64_t pgflags, uint64_t pgflags_mask,
                           std::vector<uint16_t>& swap_offset_array, const std::set<pid_t>& pids,
                           std::vector<ProcessRecord>* procs,
                           std::map<pid_t, ProcessRecord>* processrecords_ptr, std::ostream& err) {
    // Fall back to using an empty map of ProcessRecords if nullptr was passed in.
    std::map<pid_t, ProcessRecord> processrecords;
    if (!processrecords_ptr) {
        processrecords_ptr = &processrecords;
    }
    // Mark each swap offset used by the process as we find them for calculating
    // proportional swap usage later.
    for (pid_t pid : pids) {
        // Check if a ProcessRecord already exists for this pid, create one if one does not exist.
        auto iter = processrecords_ptr->find(pid);
        ProcessRecord& proc =
                (iter != processrecords_ptr->end())
                        ? iter->second
                        : processrecords_ptr
                                  ->emplace(pid, ProcessRecord(pid, params->show_wss, pgflags,
                                                               pgflags_mask, true,
                                                               params->show_oomadj, err))
                                  .first->second;

        if (!proc.valid()) {
            // Check to see if the process is still around, skip the process if the proc
            // directory is inaccessible. It was most likely killed while creating the process
            // record.
            std::string procdir = StringPrintf("/proc/%d", pid);
            if (access(procdir.c_str(), F_OK | R_OK)) continue;

            // Warn if we failed to gather process stats even while it is still alive.
            // Return success here, so we continue to print stats for other processes.
            err << "warning: failed to create process record for: " << pid << "\n";
            continue;
        }

        // Skip processes with no memory mappings.
        uint64_t vss = proc.Usage(params->show_wss).vss;
        if (vss == 0) continue;

        // Collect swap_offset counts from all processes in 1st pass.
        if (!params->show_wss && params->swap_enabled &&
            !count_swap_offsets(proc, swap_offset_array, err)) {
            err << "Failed to count swap offsets for process: " << pid << "\n";
            err << "Failed to read all pids from the system\n";
            return false;
        }

        procs->push_back(proc);
    }
    return true;
}

static void print_header(struct params* params, std::ostream& out) {
    out << StringPrintf("%5s  ", "PID");
    if (params->show_oomadj) {
        out << StringPrintf("%5s  ", "oom");
    }

    if (params->show_wss) {
        out << StringPrintf("%7s  %7s  %7s  ", "WRss", "WPss", "WUss");
    } else {
        // Swap statistics here, as working set pages by definition shouldn't end up in swap.
        out << StringPrintf("%8s  %7s  %7s  %7s  ", "Vss", "Rss", "Pss", "Uss");
        if (params->swap_enabled) {
            out << StringPrintf("%7s  %7s  %7s  ", "Swap", "PSwap", "USwap");
            if (params->zram_enabled) {
                out << StringPrintf("%7s  ", "ZSwap");
            }
        }
    }

    out << "cmdline\n";
}

static void print_divider(struct params* params, std::ostream& out) {
    out << StringPrintf("%5s  ", "");
    if (params->show_oomadj) {
        out << StringPrintf("%5s  ", "");
    }

    if (params->show_wss) {
        out << StringPrintf("%7s  %7s  %7s  ", "", "------", "------");
    } else {
        out << StringPrintf("%8s  %7s  %7s  %7s  ", "", "", "------", "------");
        if (params->swap_enabled) {
            out << StringPrintf("%7s  %7s  %7s  ", "------", "------", "------");
            if (params->zram_enabled) {
                out << StringPrintf("%7s  ", "------");
            }
        }
    }

    out << StringPrintf("%s\n", "------");
}

static void print_processrecord(struct params* params, ProcessRecord& proc, std::ostream& out) {
    out << StringPrintf("%5d  ", proc.pid());
    if (params->show_oomadj) {
        out << StringPrintf("%5d  ", proc.oomadj());
    }

    if (params->show_wss) {
        out << StringPrintf("%6" PRIu64 "K  %6" PRIu64 "K  %6" PRIu64 "K  ",
                            proc.Usage(params->show_wss).rss, proc.Usage(params->show_wss).pss,
                            proc.Usage(params->show_wss).uss);
    } else {
        out << StringPrintf("%7" PRIu64 "K  %6" PRIu64 "K  %6" PRIu64 "K  %6" PRIu64 "K  ",
                            proc.Usage(params->show_wss).vss, proc.Usage(params->show_wss).rss,
                            proc.Usage(params->show_wss).pss, proc.Usage(params->show_wss).uss);
        if (params->swap_enabled) {
            out << StringPrintf("%6" PRIu64 "K  ", proc.Usage(params->show_wss).swap);
            out << StringPrintf("%6" PRIu64 "K  ", proc.proportional_swap());
            out << StringPrintf("%6" PRIu64 "K  ", proc.unique_swap());
            if (params->zram_enabled) {
                out << StringPrintf("%6" PRIu64 "K  ", proc.zswap());
            }
        }
    }
    out << proc.cmdline() << "\n";
}

static void print_totals(struct params* params, std::ostream& out) {
    out << StringPrintf("%5s  ", "");
    if (params->show_oomadj) {
        out << StringPrintf("%5s  ", "");
    }

    if (params->show_wss) {
        out << StringPrintf("%7s  %6" PRIu64 "K  %6" PRIu64 "K  ", "", params->total_pss,
                            params->total_uss);
    } else {
        out << StringPrintf("%8s  %7s  %6" PRIu64 "K  %6" PRIu64 "K  ", "", "", params->total_pss,
                            params->total_uss);
        if (params->swap_enabled) {
            out << StringPrintf("%6" PRIu64 "K  ", params->total_swap);
            out << StringPrintf("%6" PRIu64 "K  ", params->total_pswap);
            out << StringPrintf("%6" PRIu64 "K  ", params->total_uswap);
            if (params->zram_enabled) {
                out << StringPrintf("%6" PRIu64 "K  ", params->total_zswap);
            }
        }
    }
    out << "TOTAL\n\n";
}

static void print_sysmeminfo(struct params* params, const ::android::meminfo::SysMemInfo& smi,
                             std::ostream& out) {
    if (params->swap_enabled) {
        out << StringPrintf("ZRAM: %" PRIu64 "K physical used for %" PRIu64 "K in swap (%" PRIu64
                            "K total swap)\n",
                            smi.mem_zram_kb(), (smi.mem_swap_kb() - smi.mem_swap_free_kb()),
                            smi.mem_swap_kb());
    }

    out << StringPrintf(" RAM: %" PRIu64 "K total, %" PRIu64 "K free, %" PRIu64
                        "K buffers, %" PRIu64 "K cached, %" PRIu64 "K shmem, %" PRIu64 "K slab\n",
                        smi.mem_total_kb(), smi.mem_free_kb(), smi.mem_buffers_kb(),
                        smi.mem_cached_kb(), smi.mem_shmem_kb(), smi.mem_slab_kb());
}

static void add_to_totals(struct params* params, ProcessRecord& proc,
                          const std::vector<uint16_t>& swap_offset_array) {
    params->total_pss += proc.Usage(params->show_wss).pss;
    params->total_uss += proc.Usage(params->show_wss).uss;
    if (!params->show_wss && params->swap_enabled) {
        proc.CalculateSwap(swap_offset_array, params->zram_compression_ratio);
        params->total_swap += proc.Usage(params->show_wss).swap;
        params->total_pswap += proc.proportional_swap();
        params->total_uswap += proc.unique_swap();
        if (params->zram_enabled) {
            params->total_zswap += proc.zswap();
        }
    }
}

}  // namespace procrank

bool run_procrank(uint64_t pgflags, uint64_t pgflags_mask, const std::set<pid_t>& pids,
                  bool get_oomadj, bool get_wss, SortOrder sort_order, bool reverse_sort,
                  std::map<pid_t, ProcessRecord>* processrecords_ptr, std::ostream& out,
                  std::ostream& err) {
    ::android::meminfo::SysMemInfo smi;
    if (!smi.ReadMemInfo()) {
        err << "Failed to get system memory info\n";
        return false;
    }

    struct procrank::params params = {
            .total_pss = 0,
            .total_uss = 0,
            .total_swap = 0,
            .total_pswap = 0,
            .total_uswap = 0,
            .total_zswap = 0,
            .show_oomadj = get_oomadj,
            .show_wss = get_wss,
            .swap_enabled = false,
            .zram_enabled = false,
            .zram_compression_ratio = 0.0,
    };

    // Figure out swap and zram.
    uint64_t swap_total = smi.mem_swap_kb() * 1024;
    params.swap_enabled = swap_total > 0;
    // Allocate the swap array.
    std::vector<uint16_t> swap_offset_array(swap_total / getpagesize() + 1, 0);
    if (params.swap_enabled) {
        params.zram_enabled = smi.mem_zram_kb() > 0;
        if (params.zram_enabled) {
            params.zram_compression_ratio = static_cast<float>(smi.mem_zram_kb()) /
                                            (smi.mem_swap_kb() - smi.mem_swap_free_kb());
        }
    }

    std::vector<ProcessRecord> procs;
    if (!procrank::populate_procs(&params, pgflags, pgflags_mask, swap_offset_array, pids, &procs,
                                  processrecords_ptr, err)) {
        return false;
    }

    if (procs.empty()) {
        // This would happen in corner cases where procrank is being run to find KSM usage on a
        // system with no KSM and combined with working set determination as follows
        //   procrank -w -u -k
        //   procrank -w -s -k
        //   procrank -w -o -k
        out << "<empty>\n\n";
        procrank::print_sysmeminfo(&params, smi, out);
        return true;
    }

    // Create sort function based on sort_order, default is PSS descending.
    std::function<bool(ProcessRecord & a, ProcessRecord & b)> proc_sort =
            procrank::select_sort(&params, sort_order);

    // Sort all process records, default is PSS descending.
    if (reverse_sort) {
        std::sort(procs.rbegin(), procs.rend(), proc_sort);
    } else {
        std::sort(procs.begin(), procs.end(), proc_sort);
    }

    procrank::print_header(&params, out);

    for (auto& proc : procs) {
        procrank::add_to_totals(&params, proc, swap_offset_array);
        procrank::print_processrecord(&params, proc, out);
    }

    procrank::print_divider(&params, out);
    procrank::print_totals(&params, out);
    procrank::print_sysmeminfo(&params, smi, out);

    return true;
}

namespace librank {

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

// Represents a specific process's usage of a library.
struct LibProcRecord {
  public:
    LibProcRecord(ProcessRecord& proc) : pid_(-1), oomadj_(OOM_SCORE_ADJ_MAX + 1) {
        pid_ = proc.pid();
        cmdline_ = proc.cmdline();
        oomadj_ = proc.oomadj();
        usage_.clear();
    }

    bool valid() const { return pid_ != -1; }
    void AddUsage(const MemUsage& mem_usage) { add_mem_usage(&usage_, mem_usage); }

    // Getters
    pid_t pid() const { return pid_; }
    const std::string& cmdline() const { return cmdline_; }
    int32_t oomadj() const { return oomadj_; }
    const MemUsage& usage() const { return usage_; }

  private:
    pid_t pid_;
    std::string cmdline_;
    int32_t oomadj_;
    MemUsage usage_;
};

// Represents all processes' usage of a specific library.
struct LibRecord {
  public:
    LibRecord(const std::string& name) : name_(name) {}

    void AddUsage(const LibProcRecord& proc, const MemUsage& mem_usage) {
        auto [it, inserted] = procs_.insert(std::pair<pid_t, LibProcRecord>(proc.pid(), proc));
        // Adds to proc's PID's contribution to usage of this lib, as well as total lib usage.
        it->second.AddUsage(mem_usage);
        add_mem_usage(&usage_, mem_usage);
    }
    uint64_t pss() const { return usage_.pss; }

    // Getters
    const std::string& name() const { return name_; }
    const std::map<pid_t, LibProcRecord>& processes() const { return procs_; }

  private:
    std::string name_;
    MemUsage usage_;
    std::map<pid_t, LibProcRecord> procs_;
};

static std::function<bool(LibProcRecord& a, LibProcRecord& b)> select_sort(SortOrder sort_order) {
    // Create sort function based on sort_order.
    std::function<bool(LibProcRecord & a, LibProcRecord & b)> proc_sort;
    switch (sort_order) {
        case (SortOrder::BY_RSS):
            proc_sort = [](LibProcRecord& a, LibProcRecord& b) {
                return a.usage().rss > b.usage().rss;
            };
            break;
        case (SortOrder::BY_USS):
            proc_sort = [](LibProcRecord& a, LibProcRecord& b) {
                return a.usage().uss > b.usage().uss;
            };
            break;
        case (SortOrder::BY_VSS):
            proc_sort = [](LibProcRecord& a, LibProcRecord& b) {
                return a.usage().vss > b.usage().vss;
            };
            break;
        case (SortOrder::BY_OOMADJ):
            proc_sort = [](LibProcRecord& a, LibProcRecord& b) { return a.oomadj() > b.oomadj(); };
            break;
        case (SortOrder::BY_PSS):
        default:
            proc_sort = [](LibProcRecord& a, LibProcRecord& b) {
                return a.usage().pss > b.usage().pss;
            };
            break;
    }
    return proc_sort;
}

struct params {
    // Filtering options.
    std::string lib_prefix;
    bool all_libs;
    const std::vector<std::string>& excluded_libs;
    uint16_t mapflags_mask;

    // Print options.
    Format format;
    bool swap_enabled;
    bool show_oomadj;
};

static bool populate_libs(struct params* params, uint64_t pgflags, uint64_t pgflags_mask,
                          const std::set<pid_t>& pids,
                          std::map<std::string, LibRecord>& lib_name_map,
                          std::map<pid_t, ProcessRecord>* processrecords_ptr, std::ostream& err) {
    // Fall back to using an empty map of ProcessRecords if nullptr was passed in.
    std::map<pid_t, ProcessRecord> processrecords;
    if (!processrecords_ptr) {
        processrecords_ptr = &processrecords;
    }
    for (pid_t pid : pids) {
        // Check if a ProcessRecord already exists for this pid, create one if one does not exist.
        auto iter = processrecords_ptr->find(pid);
        ProcessRecord& proc =
                (iter != processrecords_ptr->end())
                        ? iter->second
                        : processrecords_ptr
                                  ->emplace(pid, ProcessRecord(pid, false, pgflags, pgflags_mask,
                                                               true, params->show_oomadj, err))
                                  .first->second;

        if (!proc.valid()) {
            err << "error: failed to create process record for: " << pid << "\n";
            return false;
        }

        const std::vector<Vma>& maps = proc.Smaps();
        if (maps.size() == 0) {
            continue;
        }

        LibProcRecord record(proc);
        for (const Vma& map : maps) {
            // Skip library/map if the prefix for the path doesn't match.
            if (!params->lib_prefix.empty() &&
                !::android::base::StartsWith(map.name, params->lib_prefix)) {
                continue;
            }
            // Skip excluded library/map names.
            if (!params->all_libs &&
                (std::find(params->excluded_libs.begin(), params->excluded_libs.end(), map.name) !=
                 params->excluded_libs.end())) {
                continue;
            }
            // Skip maps based on map permissions.
            if (params->mapflags_mask &&
                ((map.flags & (PROT_READ | PROT_WRITE | PROT_EXEC)) != params->mapflags_mask)) {
                continue;
            }

            // Add memory for lib usage.
            auto [it, inserted] = lib_name_map.emplace(map.name, LibRecord(map.name));
            it->second.AddUsage(record, map.usage);

            if (!params->swap_enabled && map.usage.swap) {
                params->swap_enabled = true;
            }
        }
    }
    return true;
}

static void print_header(struct params* params, std::ostream& out) {
    switch (params->format) {
        case Format::RAW:
            // clang-format off
            out << std::setw(7) << "RSStot"
                << std::setw(10) << "VSS"
                << std::setw(9) << "RSS"
                << std::setw(9) << "PSS"
                << std::setw(9) << "USS"
                << "  ";
            //clang-format on
            if (params->swap_enabled) {
                out << std::setw(7) << "Swap"
                    << "  ";
            }
            if (params->show_oomadj) {
                out << std::setw(7) << "Oom"
                    << "  ";
            }
            out << "Name/PID\n";
            break;
        case Format::CSV:
            out << "\"Library\",\"Total_RSS\",\"Process\",\"PID\",\"VSS\",\"RSS\",\"PSS\",\"USS\"";
            if (params->swap_enabled) {
                out << ",\"Swap\"";
            }
            if (params->show_oomadj) {
                out << ",\"Oomadj\"";
            }
            out << "\n";
            break;
        case Format::JSON:
        default:
            break;
    }
}

static void print_library(struct params* params, const LibRecord& lib,
                          std::ostream& out) {
    if (params->format == Format::RAW) {
        // clang-format off
        out << std::setw(6) << lib.pss() << "K"
            << std::setw(10) << ""
            << std::setw(9) << ""
            << std::setw(9) << ""
            << std::setw(9) << ""
            << "  ";
        // clang-format on
        if (params->swap_enabled) {
            out << std::setw(7) << ""
                << "  ";
        }
        if (params->show_oomadj) {
            out << std::setw(7) << ""
                << "  ";
        }
        out << lib.name() << "\n";
    }
}

static void print_proc_as_raw(struct params* params, const LibProcRecord& p, std::ostream& out) {
    const MemUsage& usage = p.usage();
    // clang-format off
    out << std::setw(7) << ""
        << std::setw(9) << usage.vss << "K  "
        << std::setw(6) << usage.rss << "K  "
        << std::setw(6) << usage.pss << "K  "
        << std::setw(6) << usage.uss << "K  ";
    // clang-format on
    if (params->swap_enabled) {
        out << std::setw(6) << usage.swap << "K  ";
    }
    if (params->show_oomadj) {
        out << std::setw(7) << p.oomadj() << "  ";
    }
    out << "  " << p.cmdline() << " [" << p.pid() << "]\n";
}

static void print_proc_as_json(struct params* params, const LibRecord& l, const LibProcRecord& p,
                               std::ostream& out) {
    const MemUsage& usage = p.usage();
    // clang-format off
    out << "{\"Library\":" << EscapeJsonString(l.name())
        << ",\"Total_RSS\":" << l.pss()
        << ",\"Process\":" << EscapeJsonString(p.cmdline())
        << ",\"PID\":\"" << p.pid() << "\""
        << ",\"VSS\":" << usage.vss
        << ",\"RSS\":" << usage.rss
        << ",\"PSS\":" << usage.pss
        << ",\"USS\":" << usage.uss;
    // clang-format on
    if (params->swap_enabled) {
        out << ",\"Swap\":" << usage.swap;
    }
    if (params->show_oomadj) {
        out << ",\"Oom\":" << p.oomadj();
    }
    out << "}\n";
}

static void print_proc_as_csv(struct params* params, const LibRecord& l, const LibProcRecord& p,
                              std::ostream& out) {
    const MemUsage& usage = p.usage();
    // clang-format off
    out << EscapeCsvString(l.name())
        << "," << l.pss()
        << "," << EscapeCsvString(p.cmdline())
        << ",\"[" << p.pid() << "]\""
        << "," << usage.vss
        << "," << usage.rss
        << "," << usage.pss
        << "," << usage.uss;
    // clang-format on
    if (params->swap_enabled) {
        out << "," << usage.swap;
    }
    if (params->show_oomadj) {
        out << "," << p.oomadj();
    }
    out << "\n";
}

static void print_procs(struct params* params, const LibRecord& lib,
                        const std::vector<LibProcRecord>& procs, std::ostream& out) {
    for (const LibProcRecord& p : procs) {
        switch (params->format) {
            case Format::RAW:
                print_proc_as_raw(params, p, out);
                break;
            case Format::JSON:
                print_proc_as_json(params, lib, p, out);
                break;
            case Format::CSV:
                print_proc_as_csv(params, lib, p, out);
                break;
            default:
                break;
        }
    }
}

}  // namespace librank

bool run_librank(uint64_t pgflags, uint64_t pgflags_mask, const std::set<pid_t>& pids,
                 const std::string& lib_prefix, bool all_libs,
                 const std::vector<std::string>& excluded_libs, uint16_t mapflags_mask,
                 Format format, SortOrder sort_order, bool reverse_sort,
                 std::map<pid_t, ProcessRecord>* processrecords_ptr, std::ostream& out,
                 std::ostream& err) {
    struct librank::params params = {
            .lib_prefix = lib_prefix,
            .all_libs = all_libs,
            .excluded_libs = excluded_libs,
            .mapflags_mask = mapflags_mask,
            .format = format,
            .swap_enabled = false,
            .show_oomadj = (sort_order == SortOrder::BY_OOMADJ),
    };

    // Fills in usage info for each LibRecord.
    std::map<std::string, librank::LibRecord> lib_name_map;
    if (!librank::populate_libs(&params, pgflags, pgflags_mask, pids, lib_name_map,
                                processrecords_ptr, err)) {
        return false;
    }

    librank::print_header(&params, out);

    // Create vector of all LibRecords, sorted by descending PSS.
    std::vector<librank::LibRecord> libs;
    libs.reserve(lib_name_map.size());
    for (const auto& [k, v] : lib_name_map) {
        libs.push_back(v);
    }
    std::sort(libs.begin(), libs.end(),
              [](const librank::LibRecord& l1, const librank::LibRecord& l2) {
                  return l1.pss() > l2.pss();
              });

    std::function<bool(librank::LibProcRecord & a, librank::LibProcRecord & b)> libproc_sort =
            librank::select_sort(sort_order);
    for (librank::LibRecord& lib : libs) {
        // Sort all processes for this library, default is PSS-descending.
        std::vector<librank::LibProcRecord> procs;
        procs.reserve(lib.processes().size());
        for (const auto& [k, v] : lib.processes()) {
            procs.push_back(v);
        }
        if (reverse_sort) {
            std::sort(procs.rbegin(), procs.rend(), libproc_sort);
        } else {
            std::sort(procs.begin(), procs.end(), libproc_sort);
        }

        librank::print_library(&params, lib, out);
        librank::print_procs(&params, lib, procs, out);
    }

    return true;
}

namespace showmap {

// These are defined as static variables instead of a struct (as in procrank::params and
// librank::params) because the collect_vma callback references them.
static bool show_addr;
static bool verbose;

static std::string get_vma_name(const Vma& vma, bool total, bool is_bss) {
    if (total) {
        return "TOTAL";
    }
    std::string vma_name = vma.name;
    if (is_bss) {
        vma_name.append(" [bss]");
    }
    return vma_name;
}

static std::string get_flags(const Vma& vma, bool total) {
    std::string flags_str("---");
    if (verbose && !total) {
        if (vma.flags & PROT_READ) flags_str[0] = 'r';
        if (vma.flags & PROT_WRITE) flags_str[1] = 'w';
        if (vma.flags & PROT_EXEC) flags_str[2] = 'x';
    }
    return flags_str;
}

struct VmaInfo {
    Vma vma;
    bool is_bss;
    uint32_t count;

    VmaInfo() : is_bss(false), count(0) {};
    VmaInfo(const Vma& v) : vma(v), is_bss(false), count(1) {}
    VmaInfo(const Vma& v, bool bss) : vma(v), is_bss(bss), count(1) {}
    VmaInfo(const Vma& v, const std::string& name, bool bss) : vma(v), is_bss(bss), count(1) {
        vma.name = name;
    }

    void to_raw(bool total, std::ostream& out) const;
    void to_csv(bool total, std::ostream& out) const;
    void to_json(bool total, std::ostream& out) const;
};

void VmaInfo::to_raw(bool total, std::ostream& out) const {
    if (show_addr) {
        if (total) {
            out << "                                  ";
        } else {
            out << std::hex << std::setw(16) << vma.start << " " << std::setw(16) << vma.end << " "
                << std::dec;
        }
    }
    // clang-format off
    out << std::setw(8) << vma.usage.vss << " "
        << std::setw(8) << vma.usage.rss << " "
        << std::setw(8) << vma.usage.pss << " "
        << std::setw(8) << vma.usage.shared_clean << " "
        << std::setw(8) << vma.usage.shared_dirty << " "
        << std::setw(8) << vma.usage.private_clean << " "
        << std::setw(8) << vma.usage.private_dirty << " "
        << std::setw(8) << vma.usage.swap << " "
        << std::setw(8) << vma.usage.swap_pss << " "
        << std::setw(9) << vma.usage.anon_huge_pages << " "
        << std::setw(9) << vma.usage.shmem_pmd_mapped << " "
        << std::setw(9) << vma.usage.file_pmd_mapped << " "
        << std::setw(8) << vma.usage.shared_hugetlb << " "
        << std::setw(8) << vma.usage.private_hugetlb << " "
        << std::setw(8) << vma.usage.locked << " ";
    // clang-format on
    if (!verbose && !show_addr) {
        out << std::setw(4) << count << " ";
    }
    if (verbose) {
        if (total) {
            out << "      ";
        } else {
            out << std::setw(5) << get_flags(vma, total) << " ";
        }
    }
    out << get_vma_name(vma, total, is_bss) << "\n";
}

void VmaInfo::to_csv(bool total, std::ostream& out) const {
    // clang-format off
    out << vma.usage.vss
        << "," << vma.usage.rss
        << "," << vma.usage.pss
        << "," << vma.usage.shared_clean
        << "," << vma.usage.shared_dirty
        << "," << vma.usage.private_clean
        << "," << vma.usage.private_dirty
        << "," << vma.usage.swap
        << "," << vma.usage.swap_pss
        << "," << vma.usage.anon_huge_pages
        << "," << vma.usage.shmem_pmd_mapped
        << "," << vma.usage.file_pmd_mapped
        << "," << vma.usage.shared_hugetlb
        << "," << vma.usage.private_hugetlb
        << "," << vma.usage.locked;
    // clang-format on
    if (show_addr) {
        out << ",";
        if (total) {
            out << ",";
        } else {
            out << std::hex << vma.start << "," << vma.end << std::dec;
        }
    }
    if (!verbose && !show_addr) {
        out << "," << count;
    }
    if (verbose) {
        out << ",";
        if (!total) {
            out << EscapeCsvString(get_flags(vma, total));
        }
    }
    out << "," << EscapeCsvString(get_vma_name(vma, total, is_bss)) << "\n";
}

void VmaInfo::to_json(bool total, std::ostream& out) const {
    // clang-format off
    out << "{\"virtual size\":" << vma.usage.vss
        << ",\"RSS\":" << vma.usage.rss
        << ",\"PSS\":" << vma.usage.pss
        << ",\"shared clean\":" << vma.usage.shared_clean
        << ",\"shared dirty\":" << vma.usage.shared_dirty
        << ",\"private clean\":" << vma.usage.private_clean
        << ",\"private dirty\":" << vma.usage.private_dirty
        << ",\"swap\":" << vma.usage.swap
        << ",\"swapPSS\":" << vma.usage.swap_pss
        << ",\"Anon HugePages\":" << vma.usage.anon_huge_pages
        << ",\"Shmem PmdMapped\":" << vma.usage.shmem_pmd_mapped
        << ",\"File PmdMapped\":" << vma.usage.file_pmd_mapped
        << ",\"Shared Hugetlb\":" << vma.usage.shared_hugetlb
        << ",\"Private Hugetlb\":" << vma.usage.private_hugetlb
        << ",\"Locked\":" << vma.usage.locked;
    // clang-format on
    if (show_addr) {
        if (total) {
            out << ",\"start addr\":\"\",\"end addr\":\"\"";
        } else {
            out << ",\"start addr\":\"" << std::hex << vma.start << "\",\"end addr\":\"" << vma.end
                << "\"" << std::dec;
        }
    }
    if (!verbose && !show_addr) {
        out << ",\"#\":" << count;
    }
    if (verbose) {
        out << ",\"flags\":" << EscapeJsonString(get_flags(vma, total));
    }
    out << ",\"object\":" << EscapeJsonString(get_vma_name(vma, total, is_bss)) << "}";
}

static bool is_library(const std::string& name) {
    return (name.size() > 4) && (name[0] == '/') && ::android::base::EndsWith(name, ".so");
}

static void infer_vma_name(VmaInfo& current, const VmaInfo& recent) {
    if (current.vma.name.empty()) {
        if (recent.vma.end == current.vma.start && is_library(recent.vma.name)) {
            current.vma.name = recent.vma.name;
            current.is_bss = true;
        } else {
            current.vma.name = "[anon]";
        }
    }
}

static void add_mem_usage(MemUsage* to, const MemUsage& from) {
    to->vss += from.vss;
    to->rss += from.rss;
    to->pss += from.pss;

    to->swap += from.swap;
    to->swap_pss += from.swap_pss;

    to->private_clean += from.private_clean;
    to->private_dirty += from.private_dirty;
    to->shared_clean += from.shared_clean;
    to->shared_dirty += from.shared_dirty;

    to->anon_huge_pages += from.anon_huge_pages;
    to->shmem_pmd_mapped += from.shmem_pmd_mapped;
    to->file_pmd_mapped += from.file_pmd_mapped;
    to->shared_hugetlb += from.shared_hugetlb;
    to->private_hugetlb += from.private_hugetlb;
    to->locked += from.locked;
}

// A multimap is used instead of a map to allow for duplicate keys in case verbose output is used.
static std::multimap<std::string, VmaInfo> vmas;

static void collect_vma(const Vma& vma) {
    static VmaInfo recent;
    VmaInfo current(vma);

    std::string key;
    if (show_addr) {
        // vma.end is included in case vma.start is identical for two VMAs.
        key = StringPrintf("%16" PRIx64 "%16" PRIx64, vma.start, vma.end);
    } else {
        key = vma.name;
    }

    if (vmas.empty()) {
        vmas.emplace(key, current);
        recent = current;
        return;
    }

    infer_vma_name(current, recent);
    recent = current;

    // If sorting by address, the VMA can be placed into the map as-is.
    if (show_addr) {
        vmas.emplace(key, current);
        return;
    }

    // infer_vma_name() may have changed current.vma.name, so this key needs to be set again before
    // using it to sort by name. For verbose output, the VMA can immediately be placed into the map.
    key = current.vma.name;
    if (verbose) {
        vmas.emplace(key, current);
        return;
    }

    // Coalesces VMAs' usage by name, if !show_addr && !verbose.
    auto iter = vmas.find(key);
    if (iter == vmas.end()) {
        vmas.emplace(key, current);
        return;
    }

    VmaInfo& match = iter->second;
    add_mem_usage(&match.vma.usage, current.vma.usage);
    match.is_bss &= current.is_bss;
}

static void print_text_header(std::ostream& out) {
    if (show_addr) {
        out << "           start              end ";
    }
    out << " virtual                     shared   shared  private  private                   "
           "Anon      Shmem     File      Shared   Private\n";
    if (show_addr) {
        out << "            addr             addr ";
    }
    out << "    size      RSS      PSS    clean    dirty    clean    dirty     swap  swapPSS "
           "HugePages PmdMapped PmdMapped Hugetlb  Hugetlb    Locked ";
    if (!verbose && !show_addr) {
        out << "   # ";
    }
    if (verbose) {
        out << "flags ";
    }
    out << "object\n";
}

static void print_text_divider(std::ostream& out) {
    if (show_addr) {
        out << "---------------- ---------------- ";
    }
    out << "-------- -------- -------- -------- -------- -------- -------- -------- -------- "
           "--------- --------- --------- -------- -------- -------- ";
    if (!verbose && !show_addr) {
        out << "---- ";
    }
    if (verbose) {
        out << "----- ";
    }
    out << "------------------------------\n";
}

static void print_csv_header(std::ostream& out) {
    out << "\"virtual size\",\"RSS\",\"PSS\",\"shared clean\",\"shared dirty\",\"private clean\","
           "\"private dirty\",\"swap\",\"swapPSS\",\"Anon HugePages\",\"Shmem PmdMapped\","
           "\"File PmdMapped\",\"Shared Hugetlb\",\"Private Hugetlb\",\"Locked\"";
    if (show_addr) {
        out << ",\"start addr\",\"end addr\"";
    }
    if (!verbose && !show_addr) {
        out << ",\"#\"";
    }
    if (verbose) {
        out << ",\"flags\"";
    }
    out << ",\"object\"\n";
}

static void print_header(Format format, std::ostream& out) {
    switch (format) {
        case Format::RAW:
            print_text_header(out);
            print_text_divider(out);
            break;
        case Format::CSV:
            print_csv_header(out);
            break;
        case Format::JSON:
            out << "[";
            break;
        default:
            break;
    }
}

static void print_vmainfo(const VmaInfo& v, Format format, std::ostream& out) {
    switch (format) {
        case Format::RAW:
            v.to_raw(false, out);
            break;
        case Format::CSV:
            v.to_csv(false, out);
            break;
        case Format::JSON:
            v.to_json(false, out);
            out << ",";
            break;
        default:
            break;
    }
}

static void print_vmainfo_totals(const VmaInfo& total_usage, Format format, std::ostream& out) {
    switch (format) {
        case Format::RAW:
            print_text_divider(out);
            print_text_header(out);
            print_text_divider(out);
            total_usage.to_raw(true, out);
            break;
        case Format::CSV:
            total_usage.to_csv(true, out);
            break;
        case Format::JSON:
            total_usage.to_json(true, out);
            out << "]\n";
            break;
        default:
            break;
    }
}

}  // namespace showmap

bool run_showmap(pid_t pid, const std::string& filename, bool terse, bool verbose, bool show_addr,
                 bool quiet, Format format, std::map<pid_t, ProcessRecord>* processrecords_ptr,
                 std::ostream& out, std::ostream& err) {
    // Accumulated vmas are cleared to account for sequential showmap calls by bugreport_procdump.
    showmap::vmas.clear();

    showmap::show_addr = show_addr;
    showmap::verbose = verbose;

    bool success;
    if (!filename.empty()) {
        success = ::android::meminfo::ForEachVmaFromFile(filename, showmap::collect_vma);
    } else if (!processrecords_ptr) {
        ProcessRecord proc(pid, false, 0, 0, false, false, err);
        success = proc.ForEachExistingVma(showmap::collect_vma);
    } else {
        // Check if a ProcessRecord already exists for this pid, create one if one does not exist.
        auto iter = processrecords_ptr->find(pid);
        ProcessRecord& proc =
                (iter != processrecords_ptr->end())
                        ? iter->second
                        : processrecords_ptr
                                  ->emplace(pid, ProcessRecord(pid, false, 0, 0, false, false, err))
                                  .first->second;
        success = proc.ForEachExistingVma(showmap::collect_vma);
    }

    if (!success) {
        if (!quiet) {
            if (!filename.empty()) {
                err << "Failed to parse file " << filename << "\n";
            } else {
                err << "No maps for pid " << pid << "\n";
            }
        }
        return false;
    }

    showmap::print_header(format, out);

    showmap::VmaInfo total_usage;
    for (const auto& entry : showmap::vmas) {
        const showmap::VmaInfo& v = entry.second;
        showmap::add_mem_usage(&total_usage.vma.usage, v.vma.usage);
        total_usage.count += v.count;
        if (terse && !(v.vma.usage.private_dirty || v.vma.usage.private_clean)) {
            continue;
        }
        showmap::print_vmainfo(v, format, out);
    }
    showmap::print_vmainfo_totals(total_usage, format, out);

    return true;
}

namespace bugreport_procdump {

static void create_processrecords(const std::set<pid_t>& pids,
                                  std::map<pid_t, ProcessRecord>& processrecords,
                                  std::ostream& err) {
    for (pid_t pid : pids) {
        ProcessRecord proc(pid, false, 0, 0, true, false, err);
        if (!proc.valid()) {
            err << "Could not create a ProcessRecord for pid " << pid << "\n";
            continue;
        }
        processrecords.emplace(pid, std::move(proc));
    }
}

static void print_section_start(const std::string& name, std::ostream& out) {
    out << "------ " << name << " ------\n";
}

static void print_section_end(const std::string& name,
                              const std::chrono::time_point<std::chrono::steady_clock>& start,
                              std::ostream& out) {
    // std::ratio<1> represents the period for one second.
    using floatsecs = std::chrono::duration<float, std::ratio<1>>;
    auto end = std::chrono::steady_clock::now();
    std::streamsize precision = out.precision();
    out << "------ " << std::setprecision(3) << std::fixed << floatsecs(end - start).count()
        << " was the duration of '" << name << "' ------\n";
    out << std::setprecision(precision) << std::defaultfloat;
}

static void call_smaps_of_all_processes(const std::string& filename, bool terse, bool verbose,
                                        bool show_addr, bool quiet, Format format,
                                        std::map<pid_t, ProcessRecord>& processrecords,
                                        std::ostream& out, std::ostream& err) {
    for (const auto& [pid, record] : processrecords) {
        std::string showmap_title = StringPrintf("SHOW MAP %d: %s", pid, record.cmdline().c_str());

        auto showmap_start = std::chrono::steady_clock::now();
        print_section_start(showmap_title, out);
        run_showmap(pid, filename, terse, verbose, show_addr, quiet, format, &processrecords, out,
                    err);
        print_section_end(showmap_title, showmap_start, out);
    }
}

static void call_librank(const std::set<pid_t>& pids,
                         std::map<pid_t, ProcessRecord>& processrecords, std::ostream& out,
                         std::ostream& err) {
    auto librank_start = std::chrono::steady_clock::now();
    print_section_start("LIBRANK", out);
    run_librank(0, 0, pids, "", false, {"[heap]", "[stack]"}, 0, Format::RAW, SortOrder::BY_PSS,
                false, &processrecords, out, err);
    print_section_end("LIBRANK", librank_start, out);
}

static void call_procrank(const std::set<pid_t>& pids,
                          std::map<pid_t, ProcessRecord>& processrecords, std::ostream& out,
                          std::ostream& err) {
    auto procrank_start = std::chrono::steady_clock::now();
    print_section_start("PROCRANK", out);
    run_procrank(0, 0, pids, false, false, SortOrder::BY_PSS, false, &processrecords, out, err);
    print_section_end("PROCRANK", procrank_start, out);
}

}  // namespace bugreport_procdump

bool run_bugreport_procdump(std::ostream& out, std::ostream& err) {
    std::set<pid_t> pids;
    if (!::android::smapinfo::get_all_pids(&pids)) {
        err << "Failed to get all pids.\n";
        return false;
    }

    // create_processrecords is the only expensive call in this function, as showmap, librank, and
    // procrank will only print already-collected information. This duration is captured by
    // dumpstate in the BUGREPORT PROCDUMP section.
    std::map<pid_t, ProcessRecord> processrecords;
    bugreport_procdump::create_processrecords(pids, processrecords, err);

    // pids without associated ProcessRecords are removed so that librank/procrank do not fall back
    // to creating new ProcessRecords for them.
    for (pid_t pid : pids) {
        if (processrecords.find(pid) == processrecords.end()) {
            pids.erase(pid);
        }
    }

    auto all_smaps_start = std::chrono::steady_clock::now();
    bugreport_procdump::print_section_start("SMAPS OF ALL PROCESSES", out);
    bugreport_procdump::call_smaps_of_all_processes("", false, false, false, true, Format::RAW,
                                                    processrecords, out, err);
    bugreport_procdump::print_section_end("SMAPS OF ALL PROCESSES", all_smaps_start, out);

    bugreport_procdump::call_librank(pids, processrecords, out, err);
    bugreport_procdump::call_procrank(pids, processrecords, out, err);

    return true;
}

}  // namespace smapinfo
}  // namespace android
