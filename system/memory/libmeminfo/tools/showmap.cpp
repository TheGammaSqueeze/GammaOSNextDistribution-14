/*
 * Copyright (C) 2019 The Android Open Source Project
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
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <unistd.h>

#include <iomanip>
#include <iostream>
#include <map>
#include <memory>
#include <string>

#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <meminfo/procmeminfo.h>

#include <processrecord.h>
#include <smapinfo.h>

using ::android::meminfo::Format;
using ::android::meminfo::GetFormat;

[[noreturn]] static void usage(int exit_status) {
    std::cerr << "showmap [-aqtv] [-f FILE] PID\n"
              << "-a\taddresses (show virtual memory map)\n"
              << "-q\tquiet (don't show error if map could not be read)\n"
              << "-t\tterse (show only items with private pages)\n"
              << "-v\tverbose (don't coalesce maps with the same name)\n"
              << "-f\tFILE (read from input from FILE instead of PID)\n"
              << "-o\t[raw][json][csv] Print output in the specified format.\n"
              << "  \tDefault output format is raw text. All memory in KB.)\n";

    exit(exit_status);
}

int main(int argc, char* argv[]) {
    signal(SIGPIPE, SIG_IGN);
    struct option longopts[] = {
            {"help", no_argument, nullptr, 'h'},
            {0, 0, nullptr, 0},
    };

    // Printing options.
    bool terse = false;
    bool quiet = false;

    // Output options.
    bool show_addr = false;
    bool verbose = false;
    Format format = Format::RAW;

    // 'pid' will be ignored if a file is specified.
    std::string filename;
    pid_t pid = 0;

    int opt;
    while ((opt = getopt_long(argc, argv, "tvaqf:o:h", longopts, nullptr)) != -1) {
        switch (opt) {
            case 't':
                terse = true;
                break;
            case 'a':
                show_addr = true;
                break;
            case 'v':
                verbose = true;
                break;
            case 'q':
                quiet = true;
                break;
            case 'f':
                filename = optarg;
                break;
            case 'o':
                format = GetFormat(optarg);
                if (format == Format::INVALID) {
                    std::cerr << "Invalid format.\n";
                    usage(EXIT_FAILURE);
                }
                break;
            case 'h':
                usage(EXIT_SUCCESS);
            default:
                usage(EXIT_FAILURE);
        }
    }

    if (filename.empty()) {
        if ((argc - 1) < optind) {
            std::cerr << "Invalid arguments: Must provide <pid> at the end\n";
            usage(EXIT_FAILURE);
        }

        pid = atoi(argv[optind]);
        if (pid <= 0) {
            std::cerr << "Invalid process id " << argv[optind] << "\n";
            usage(EXIT_FAILURE);
        }
        // run_showmap will read directly from this file and ignore the pid argument.
        filename = ::android::base::StringPrintf("/proc/%d/smaps", pid);
    }

    bool success = ::android::smapinfo::run_showmap(pid, filename, terse, verbose, show_addr, quiet,
                                                    format, nullptr, std::cout, std::cerr);
    if (!success) {
        exit(EXIT_FAILURE);
    }
    return 0;
}
