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

#include <error.h>
#include <inttypes.h>
#include <linux/kernel-page-flags.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <sys/types.h>

#include <iostream>
#include <map>
#include <memory>
#include <set>

#include <android-base/parseint.h>
#include <android-base/strings.h>
#include <meminfo/procmeminfo.h>

#include <processrecord.h>
#include <smapinfo.h>

using ::android::meminfo::Format;
using ::android::meminfo::GetFormat;
using ::android::smapinfo::SortOrder;

[[noreturn]] static void usage(int exit_status) {
    std::cerr << "Usage: " << getprogname() << " [ -P | -L ] [ -v | -r | -p | -u | -s | -h ]\n"
              << "\n"
              << "Sort options:\n"
              << "    -v  Sort processes by VSS.\n"
              << "    -r  Sort processes by RSS.\n"
              << "    -p  Sort processes by PSS.\n"
              << "    -u  Sort processes by USS.\n"
              << "    -o  Sort (and show) processes by oom score.\n"
              << "    -s  Sort processes by swap.\n"
              << "        (Default sort order is PSS.)\n"
              << "    -a  Show all mappings, including stack, heap and anon.\n"
              << "    -P /path  Limit libraries displayed to those in path.\n"
              << "    -R  Reverse sort order (default is descending).\n"
              << "    -m [r][w][x] Only list pages that exactly match permissions\n"
              << "    -c  Only show cached (storage backed) pages\n"
              << "    -C  Only show non-cached (ram/swap backed) pages\n"
              << "    -k  Only show pages collapsed by KSM\n"
              << "    -f  [raw][json][csv] Print output in the specified format.\n"
              << "        (Default format is raw text.)\n"
              << "    -h  Display this help screen.\n";
    exit(exit_status);
}

static uint16_t parse_mapflags(const char* mapflags) {
    uint16_t ret = 0;
    for (const char* p = mapflags; *p; p++) {
        switch (*p) {
            case 'r':
                ret |= PROT_READ;
                break;
            case 'w':
                ret |= PROT_WRITE;
                break;
            case 'x':
                ret |= PROT_EXEC;
                break;
            default:
                error(EXIT_FAILURE, 0, "Invalid permissions string: %s, %s", mapflags, p);
        }
    }
    return ret;
}

int main(int argc, char* argv[]) {
    uint64_t pgflags = 0;
    uint64_t pgflags_mask = 0;

    // Library filtering options.
    std::string lib_prefix = "";
    bool all_libs = false;
    std::vector<std::string> excluded_libs = {"[heap]", "[stack]"};
    uint16_t mapflags_mask = 0;

    // Output format (raw text, JSON, CSV).
    Format format = Format::RAW;

    // Process sorting options.
    SortOrder sort_order = SortOrder::BY_PSS;
    bool reverse_sort = false;

    int opt;
    while ((opt = getopt(argc, argv, "acCf:hkm:opP:uvrsR")) != -1) {
        switch (opt) {
            case 'a':
                all_libs = true;
                break;
            case 'c':
                pgflags = 0;
                pgflags_mask = (1 << KPF_SWAPBACKED);
                break;
            case 'C':
                pgflags = (1 << KPF_SWAPBACKED);
                pgflags_mask = (1 << KPF_SWAPBACKED);
                break;
            case 'f':
                format = GetFormat(optarg);
                if (format == Format::INVALID) {
                    std::cerr << "Invalid format." << std::endl;
                    usage(EXIT_FAILURE);
                }
                break;
            case 'h':
                usage(EXIT_SUCCESS);
            case 'k':
                pgflags = (1 << KPF_KSM);
                pgflags_mask = (1 << KPF_KSM);
                break;
            case 'm':
                mapflags_mask = parse_mapflags(optarg);
                break;
            case 'o':
                sort_order = SortOrder::BY_OOMADJ;
                break;
            case 'p':
                sort_order = SortOrder::BY_PSS;
                break;
            case 'P':
                lib_prefix = optarg;
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
            default:
                usage(EXIT_FAILURE);
        }
    }

    std::set<pid_t> pids;
    if (!::android::smapinfo::get_all_pids(&pids)) {
        std::cerr << "Failed to get all pids." << std::endl;
        exit(EXIT_FAILURE);
    }

    bool success = ::android::smapinfo::run_librank(
            pgflags, pgflags_mask, pids, lib_prefix, all_libs, excluded_libs, mapflags_mask, format,
            sort_order, reverse_sort, nullptr, std::cout, std::cerr);
    if (!success) {
        exit(EXIT_FAILURE);
    }
    return 0;
}
