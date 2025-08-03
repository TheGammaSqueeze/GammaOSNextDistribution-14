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

#include <getopt.h>
#include <inttypes.h>
#include <linux/kernel-page-flags.h>
#include <stdlib.h>

#include <iostream>
#include <map>
#include <set>
#include <unordered_map>
#include <vector>

#include <android-base/parseint.h>
#include <android-base/strings.h>
#include <meminfo/procmeminfo.h>
#include <procinfo/process.h>

#include <processrecord.h>
#include <smapinfo.h>

using ::android::smapinfo::SortOrder;

[[noreturn]] static void usage(int exit_status) {
    std::cerr << "Usage: " << getprogname() << " [ -W ] [ -v | -r | -p | -u | -s | -h ] [-d PID]"
              << std::endl
              << "    -v  Sort by VSS." << std::endl
              << "    -r  Sort by RSS." << std::endl
              << "    -p  Sort by PSS." << std::endl
              << "    -u  Sort by USS." << std::endl
              << "    -s  Sort by swap." << std::endl
              << "        (Default sort order is PSS.)" << std::endl
              << "    -R  Reverse sort order (default is descending)." << std::endl
              << "    -c  Only show cached (storage backed) pages" << std::endl
              << "    -C  Only show non-cached (ram/swap backed) pages" << std::endl
              << "    -k  Only show pages collapsed by KSM" << std::endl
              << "    -w  Display statistics for working set only." << std::endl
              << "    -W  Reset working set of processes." << std::endl
              << "    -o  Show and sort by oom score against lowmemorykiller thresholds."
              << std::endl
              << "    -d  Filter to descendants of specified process (can be repeated)" << std::endl
              << "    -h  Display this help screen." << std::endl;
    exit(exit_status);
}

int main(int argc, char* argv[]) {
    // Count all pages by default.
    uint64_t pgflags = 0;
    uint64_t pgflags_mask = 0;

    // Sort by PSS descending by default.
    SortOrder sort_order = SortOrder::BY_PSS;
    bool reverse_sort = false;

    bool get_oomadj = false;
    bool get_wss = false;
    bool reset_wss = false;

    std::vector<pid_t> descendant_filter;

    int opt;
    while ((opt = getopt(argc, argv, "cCd:hkoprRsuvwW")) != -1) {
        switch (opt) {
            case 'c':
                pgflags = 0;
                pgflags_mask = (1 << KPF_SWAPBACKED);
                break;
            case 'C':
                pgflags = (1 << KPF_SWAPBACKED);
                pgflags_mask = (1 << KPF_SWAPBACKED);
                break;
            case 'd': {
                pid_t p;
                if (!android::base::ParseInt(optarg, &p)) {
                    std::cerr << "Failed to parse pid '" << optarg << "'" << std::endl;
                    usage(EXIT_FAILURE);
                }
                descendant_filter.push_back(p);
                break;
            }
            case 'h':
                usage(EXIT_SUCCESS);
            case 'k':
                pgflags = (1 << KPF_KSM);
                pgflags_mask = (1 << KPF_KSM);
                break;
            case 'o':
                sort_order = SortOrder::BY_OOMADJ;
                get_oomadj = true;
                break;
            case 'p':
                sort_order = SortOrder::BY_PSS;
                break;
            case 'r':
                sort_order = SortOrder::BY_RSS;
                break;
            case 'R':
                reverse_sort = true;
                break;
            case 's':
                sort_order = SortOrder::BY_SWAP;
                break;
            case 'u':
                sort_order = SortOrder::BY_USS;
                break;
            case 'v':
                sort_order = SortOrder::BY_VSS;
                break;
            case 'w':
                get_wss = true;
                break;
            case 'W':
                reset_wss = true;
                break;
            default:
                usage(EXIT_FAILURE);
        }
    }

    std::set<pid_t> pids;
    if (!::android::smapinfo::get_all_pids(&pids)) {
        std::cerr << "Failed to get all pids." << std::endl;
        exit(EXIT_FAILURE);
    }

    if (descendant_filter.size()) {
        // Map from parent pid to all of its children.
        std::unordered_map<pid_t, std::vector<pid_t>> pid_tree;

        for (pid_t pid : pids) {
            android::procinfo::ProcessInfo info;
            std::string error;
            if (!android::procinfo::GetProcessInfo(pid, &info, &error)) {
                std::cerr << "warning: failed to get process info for: " << pid << ": " << error
                          << std::endl;
                continue;
            }

            pid_tree[info.ppid].push_back(pid);
        }

        std::set<pid_t> final_pids;
        std::vector<pid_t>& frontier = descendant_filter;

        // Do a breadth-first walk of the process tree, starting from the pids we were given.
        while (!frontier.empty()) {
            pid_t pid = frontier.back();
            frontier.pop_back();

            // It's possible for the pid we're looking at to already be in our list if one of the
            // passed in processes descends from another, or if the same pid is passed twice.
            auto [it, inserted] = final_pids.insert(pid);
            if (inserted) {
                auto it = pid_tree.find(pid);
                if (it != pid_tree.end()) {
                    // Add all of the children of |pid| to the list of nodes to visit.
                    frontier.insert(frontier.end(), it->second.begin(), it->second.end());
                }
            }
        }

        pids = std::move(final_pids);
    }

    if (reset_wss) {
        for (pid_t pid : pids) {
            if (!::android::meminfo::ProcMemInfo::ResetWorkingSet(pid)) {
                std::cerr << "Failed to reset working set of all processes" << std::endl;
                exit(EXIT_FAILURE);
            }
        }
        // Other options passed to procrank are ignored if reset_wss is true.
        return 0;
    }

    bool success = ::android::smapinfo::run_procrank(pgflags, pgflags_mask, pids, get_oomadj,
                                                     get_wss, sort_order, reverse_sort, nullptr,
                                                     std::cout, std::cerr);
    if (!success) {
        exit(EXIT_FAILURE);
    }
    return 0;
}
