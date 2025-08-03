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

#include <dirent.h>
#include <errno.h>
#include <getopt.h>
#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include <fstream>
#include <iostream>
#include <map>
#include <memory>
#include <set>
#include <sstream>
#include <string>
#include <vector>

#include <android-base/stringprintf.h>
#include <dmabufinfo/dmabuf_sysfs_stats.h>
#include <dmabufinfo/dmabufinfo.h>
#include <meminfo/procmeminfo.h>

#include "include/dmabuf_output_helper.h"

using DmaBuffer = ::android::dmabufinfo::DmaBuffer;
using Format = ::android::meminfo::Format;

std::unique_ptr<DmabufOutputHelper> outputHelper;

[[noreturn]] static void usage(int exit_status) {
    fprintf(stderr,
            "Usage: %s [-abh] [per-process/per-buffer stats] [-o output type]\n"
            "-a\t show all dma buffers (ion) in big table, [buffer x process] grid \n"
            "-b\t show DMA-BUF per-buffer, per-exporter and per-device statistics \n"
            "-o\t [raw][csv] print output in the specified format.\n"
            "-h\t show this help\n"
            "  \t If PID is supplied, the dmabuf information for that process is shown.\n"
            "  \t Per-buffer DMA-BUF stats do not take an argument.\n",
            getprogname());

    exit(exit_status);
}

static std::string GetProcessComm(const pid_t pid) {
    std::string pid_path = android::base::StringPrintf("/proc/%d/comm", pid);
    std::ifstream in{pid_path};
    if (!in) return std::string("N/A");
    std::string line;
    std::getline(in, line);
    if (!in) return std::string("N/A");
    return line;
}

static void PrintDmaBufTable(const std::vector<DmaBuffer>& bufs) {
    if (bufs.empty()) {
        printf("dmabuf info not found ¯\\_(ツ)_/¯\n");
        return;
    }

    printf("\n----------------------- DMA-BUF Table buffer x process --------------------------\n");

    // Find all unique pids in the input vector, create a set
    std::set<pid_t> pid_set;
    for (auto& buf : bufs) {
        pid_set.insert(buf.pids().begin(), buf.pids().end());
    }

    outputHelper->BufTableMainHeaders();
    for (auto pid : pid_set) {
        std::string process = GetProcessComm(pid);
        outputHelper->BufTableProcessHeader(pid, process);
    }
    printf("\n");

    // holds per-process dmabuf size in kB
    std::map<pid_t, uint64_t> per_pid_size = {};
    uint64_t dmabuf_total_size = 0;

    // Iterate through all dmabufs and collect per-process sizes, refs
    for (auto& buf : bufs) {
        outputHelper->BufTableStats(buf);
        // Iterate through each process to find out per-process references for each buffer,
        // gather total size used by each process etc.
        for (pid_t pid : pid_set) {
            int pid_fdrefs = 0, pid_maprefs = 0;
            if (buf.fdrefs().count(pid) == 1) {
                // Get the total number of ref counts the process is holding
                // on this buffer. We don't differentiate between mmap or fd.
                pid_fdrefs += buf.fdrefs().at(pid);
            }
            if (buf.maprefs().count(pid) == 1) {
                pid_maprefs += buf.maprefs().at(pid);
            }

            if (pid_fdrefs || pid_maprefs) {
                // Add up the per-pid total size. Note that if a buffer is mapped
                // in 2 different processes, the size will be shown as mapped or opened
                // in both processes. This is intended for visibility.
                //
                // If one wants to get the total *unique* dma buffers, they can simply
                // sum the size of all dma bufs shown by the tool
                per_pid_size[pid] += buf.size() / 1024;
            }
            outputHelper->BufTableProcessSize(pid_fdrefs, pid_maprefs);
        }
        dmabuf_total_size += buf.size() / 1024;
        printf("\n");
    }

    printf("------------------------------------\n");
    outputHelper->BufTableTotalHeader();
    for (auto pid : pid_set) {
        std::string process = GetProcessComm(pid);
        outputHelper->BufTableTotalProcessHeader(pid, process);
    }

    outputHelper->BufTableTotalStats(dmabuf_total_size);
    for (auto const& [pid, pid_size] : per_pid_size) {
        outputHelper->BufTableTotalProcessStats(pid_size);
    }
    printf("\n");
}

static void PrintDmaBufPerProcess(const std::vector<DmaBuffer>& bufs) {
    if (bufs.empty()) {
        printf("dmabuf info not found ¯\\_(ツ)_/¯\n");
        return;
    }

    // Create a reverse map from pid to dmabufs
    std::unordered_map<pid_t, std::set<ino_t>> pid_to_inodes = {};
    uint64_t userspace_size = 0;  // Size of userspace dmabufs in the system
    for (auto& buf : bufs) {
        for (auto pid : buf.pids()) {
            pid_to_inodes[pid].insert(buf.inode());
        }
        userspace_size += buf.size();
    }
    // Create an inode to dmabuf map. We know inodes are unique..
    std::unordered_map<ino_t, DmaBuffer> inode_to_dmabuf;
    for (auto buf : bufs) {
        inode_to_dmabuf[buf.inode()] = buf;
    }

    uint64_t total_rss = 0, total_pss = 0;
    for (auto& [pid, inodes] : pid_to_inodes) {
        uint64_t pss = 0;
        uint64_t rss = 0;

        outputHelper->PerProcessHeader(GetProcessComm(pid), pid);

        for (auto& inode : inodes) {
            DmaBuffer& buf = inode_to_dmabuf[inode];
            outputHelper->PerProcessBufStats(buf);
            rss += buf.size();
            pss += buf.Pss();
        }

        outputHelper->PerProcessTotalStat(pss, rss);
        printf("----------------------\n");
        total_rss += rss;
        total_pss += pss;
    }

    uint64_t kernel_rss = 0;  // Total size of dmabufs NOT mapped or opened by a process
    if (android::dmabufinfo::GetDmabufTotalExportedKb(&kernel_rss)) {
        kernel_rss *= 1024;  // KiB -> bytes
        if (kernel_rss >= userspace_size)
            kernel_rss -= userspace_size;
        else
            printf("Warning: Total dmabufs < userspace dmabufs\n");
    } else {
        printf("Warning: Could not get total exported dmabufs. Kernel size will be 0.\n");
    }

    outputHelper->TotalProcessesStats(total_rss, total_pss, userspace_size, kernel_rss);
}

static void DumpDmabufSysfsStats() {
    android::dmabufinfo::DmabufSysfsStats stats;

    if (!android::dmabufinfo::GetDmabufSysfsStats(&stats)) {
        printf("Unable to read DMA-BUF sysfs stats from device\n");
        return;
    }

    auto buffer_stats = stats.buffer_stats();
    auto exporter_stats = stats.exporter_info();

    const char separator[] = "-----------------------";
    printf("\n\n%s DMA-BUF per-buffer stats %s\n", separator, separator);
    outputHelper->PerBufferHeader();
    for (const auto& buf : buffer_stats) {
        outputHelper->PerBufferStats(buf);
    }

    printf("\n\n%s DMA-BUF exporter stats %s\n", separator, separator);
    outputHelper->ExporterHeader();
    for (auto const& [exporter_name, dmaBufTotal] : exporter_stats) {
        outputHelper->ExporterStats(exporter_name, dmaBufTotal);
    }

    printf("\n\n%s DMA-BUF total stats %s\n", separator, separator);
    outputHelper->SysfsBufTotalStats(stats);
}

int main(int argc, char* argv[]) {
    struct option longopts[] = {{"all", no_argument, nullptr, 'a'},
                                {"per-buffer", no_argument, nullptr, 'b'},
                                {"help", no_argument, nullptr, 'h'},
                                {0, 0, nullptr, 0}};

    int opt;
    bool show_table = false;
    bool show_dmabuf_sysfs_stats = false;
    Format format = Format::RAW;
    while ((opt = getopt_long(argc, argv, "abho:", longopts, nullptr)) != -1) {
        switch (opt) {
            case 'a':
                show_table = true;
                break;
            case 'b':
                show_dmabuf_sysfs_stats = true;
                break;
            case 'o':
                format = android::meminfo::GetFormat(optarg);
                switch (format) {
                    case Format::CSV:
                        outputHelper = std::make_unique<CsvOutput>();
                        break;
                    case Format::RAW:
                        outputHelper = std::make_unique<RawOutput>();
                        break;
                    default:
                        fprintf(stderr, "Invalid output format.\n");
                        usage(EXIT_FAILURE);
                }
                break;
            case 'h':
                usage(EXIT_SUCCESS);
            default:
                usage(EXIT_FAILURE);
        }
    }

    if (!outputHelper) {
        outputHelper = std::make_unique<RawOutput>();
    }

    pid_t pid = -1;
    if (optind < argc) {
        if (show_table || show_dmabuf_sysfs_stats) {
            fprintf(stderr, "Invalid arguments: -a and -b does not need arguments\n");
            usage(EXIT_FAILURE);
        }
        if (optind != (argc - 1)) {
            fprintf(stderr, "Invalid arguments - only one [PID] argument is allowed\n");
            usage(EXIT_FAILURE);
        }
        pid = atoi(argv[optind]);
        if (pid == 0) {
            fprintf(stderr, "Invalid process id %s\n", argv[optind]);
            usage(EXIT_FAILURE);
        }
    }

    if (show_dmabuf_sysfs_stats) {
        DumpDmabufSysfsStats();
    }

    if (!show_table && show_dmabuf_sysfs_stats) {
        return 0;
    }

    std::vector<DmaBuffer> bufs;
    if (pid != -1) {
        if (!ReadDmaBufInfo(pid, &bufs)) {
            fprintf(stderr, "Unable to read dmabuf info for %d\n", pid);
            exit(EXIT_FAILURE);
        }
    } else {
        if (!ReadProcfsDmaBufs(&bufs)) {
            fprintf(stderr, "Failed to ReadProcfsDmaBufs, check logcat for info\n");
            exit(EXIT_FAILURE);
        }
    }

    // Show the old dmabuf table, inode x process
    if (show_table) {
        printf("%s", (show_dmabuf_sysfs_stats) ? "\n\n" : "");
        PrintDmaBufTable(bufs);
        return 0;
    }

    if (!show_table && !show_dmabuf_sysfs_stats) {
        PrintDmaBufPerProcess(bufs);
    }

    return 0;
}
