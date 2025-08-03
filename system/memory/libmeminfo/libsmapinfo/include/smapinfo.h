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

#pragma once

#include <cstdint>
#include <map>
#include <ostream>
#include <set>
#include <string>
#include <vector>

#include <meminfo/procmeminfo.h>
#include <processrecord.h>

namespace android {
namespace smapinfo {

// The user-specified order to sort processes.
enum class SortOrder { BY_PSS = 0, BY_RSS, BY_USS, BY_VSS, BY_SWAP, BY_OOMADJ };

// Populates the input set with all pids present in the /proc directory. Only
// returns false if /proc could not be opened, returns true otherwise.
bool get_all_pids(std::set<pid_t>* pids);

// Sorts processes provided in 'pids' by memory usage (or oomadj score) and
// prints them. Returns false in the following failure cases:
// a) system memory information could not be read,
// b) swap offsets could not be counted for some process,
// c) reset_wss is true but the working set for some process could not be reset.
bool run_procrank(uint64_t pgflags, uint64_t pgflags_mask, const std::set<pid_t>& pids,
                  bool get_oomadj, bool get_wss, SortOrder sort_order, bool reverse_sort,
                  std::map<pid_t, ProcessRecord>* processrecords_ptr, std::ostream& out,
                  std::ostream& err);

// Sorts libraries used by processes in 'pids' by memory usage and prints them.
// Returns false if any process's usage info could not be read.
bool run_librank(uint64_t pgflags, uint64_t pgflags_mask, const std::set<pid_t>& pids,
                 const std::string& lib_prefix, bool all_libs,
                 const std::vector<std::string>& excluded_libs, uint16_t mapflags_mask,
                 android::meminfo::Format format, SortOrder sort_order, bool reverse_sort,
                 std::map<pid_t, ProcessRecord>* processrecords_ptr, std::ostream& out,
                 std::ostream& err);

// Retrieves showmap information from the provided pid (or file) and prints it.
// Returns false if there are no maps associated with 'pid' or if the file
// denoted by 'filename' is malformed.
bool run_showmap(pid_t pid, const std::string& filename, bool terse, bool verbose, bool show_addr,
                 bool quiet, android::meminfo::Format format,
                 std::map<pid_t, ProcessRecord>* processrecords_ptr, std::ostream& out,
                 std::ostream& err);

// Runs procrank, librank, and showmap with a single read of smaps. Default
// arguments are used for all tools (except quiet output for showmap). This
// prints output that is specifically meant to be included in bug reports.
// Returns false only in the case that /proc could not be opened.
bool run_bugreport_procdump(std::ostream& out, std::ostream& err);

}  // namespace smapinfo
}  // namespace android
