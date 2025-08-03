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

#pragma once

#include <sys/types.h>
#include <unistd.h>

#include <cstdint>
#include <map>
#include <string>

#include <dmabufinfo/dmabuf_sysfs_stats.h>
#include <dmabufinfo/dmabufinfo.h>

class DmabufOutputHelper {
  public:
    virtual ~DmabufOutputHelper() = default;

    // Table buffer x process
    virtual void BufTableMainHeaders() = 0;
    virtual void BufTableProcessHeader(const pid_t pid, const std::string& process) = 0;
    virtual void BufTableStats(const android::dmabufinfo::DmaBuffer& buf) = 0;
    virtual void BufTableProcessSize(int pid_fdrefs, int pid_maprefs) = 0;
    virtual void BufTableTotalHeader() {}
    virtual void BufTableTotalProcessHeader([[maybe_unused]] const pid_t pid,
                                            [[maybe_unused]] const std::string& process) {}
    virtual void BufTableTotalStats(const uint64_t dmabuf_total_size) = 0;
    virtual void BufTableTotalProcessStats(const uint64_t pid_size) = 0;

    // Per Process
    virtual void PerProcessHeader(const std::string& process, const pid_t pid) = 0;
    virtual void PerProcessBufStats(const android::dmabufinfo::DmaBuffer& buf) = 0;
    virtual void PerProcessTotalStat(const uint64_t pss, const uint64_t rss) = 0;
    virtual void TotalProcessesStats(const uint64_t& total_rss, const uint64_t& total_pss,
                                     const uint64_t& userspace_size,
                                     const uint64_t& kernel_rss) = 0;

    // Per-buffer (Sysfs)
    virtual void PerBufferHeader() = 0;
    virtual void PerBufferStats(const android::dmabufinfo::DmabufInfo& bufInfo) = 0;

    virtual void ExporterHeader() = 0;
    virtual void ExporterStats(const std::string& exporter,
                               const android::dmabufinfo::DmabufTotal& dmaBufTotal) = 0;

    virtual void SysfsBufTotalStats(const android::dmabufinfo::DmabufSysfsStats& stats) = 0;
};

class CsvOutput final : public DmabufOutputHelper {
  public:
    // Table buffer x process
    void BufTableMainHeaders() override {
        printf("\"Dmabuf Inode\",\"Size(kB)\",\"Fd Ref Counts\",\"Map Ref Counts\"");
    }
    void BufTableProcessHeader(const pid_t pid, const std::string& process) override {
        printf(",\"%s:%d\"", process.c_str(), pid);
    }

    void BufTableStats(const android::dmabufinfo::DmaBuffer& buf) override {
        printf("%ju,%" PRIu64 ",%zu,%zu", static_cast<uintmax_t>(buf.inode()), buf.size() / 1024,
               buf.fdrefs().size(), buf.maprefs().size());
    }
    void BufTableProcessSize(int pid_fdrefs, int pid_maprefs) override {
        if (pid_fdrefs || pid_maprefs)
            printf(",\"%d(%d) refs\"", pid_fdrefs, pid_maprefs);
        else
            printf(",\"\"");
    }

    void BufTableTotalHeader() override { printf("\"Total Size(kB)\","); }

    void BufTableTotalProcessHeader(const pid_t pid, const std::string& process) override {
        printf("\"%s:%d size(kB)\",", process.c_str(), pid);
    }

    void BufTableTotalStats(const uint64_t dmabuf_total_size) override {
        printf("\n%" PRIu64 "", dmabuf_total_size);
    }

    void BufTableTotalProcessStats(const uint64_t pid_size) override {
        printf(",%" PRIu64 "", pid_size);
    }

    // Per Process
    void PerProcessHeader(const std::string& process, const pid_t pid) override {
        printf("\t%s:%d\n", process.c_str(), pid);
        printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n", "Name", "Rss(kB)", "Pss(kB)", "nr_procs",
               "Inode");
    }

    void PerProcessBufStats(const android::dmabufinfo::DmaBuffer& buf) override {
        printf("\"%s\",%" PRIu64 ",%" PRIu64 ",%zu,%" PRIuMAX "\n",
               buf.name().empty() ? "<unknown>" : buf.name().c_str(), buf.size() / 1024,
               buf.Pss() / 1024, buf.pids().size(), static_cast<uintmax_t>(buf.inode()));
    }

    void PerProcessTotalStat(const uint64_t pss, const uint64_t rss) override {
        printf("\nPROCESS TOTAL\n");
        printf("\"Rss total(kB)\",\"Pss total(kB)\"\n");
        printf("%" PRIu64 ",%" PRIu64 "\n", rss / 1024, pss / 1024);
    }

    void TotalProcessesStats(const uint64_t& total_rss, const uint64_t& total_pss,
                             const uint64_t& userspace_size, const uint64_t& kernel_rss) override {
        printf("\tTOTALS\n");
        // Headers
        printf("\"dmabuf total (kB)\",\"kernel_rss (kB)\",\"userspace_rss "
               "(kB)\",\"userspace_pss (kB)\"\n");
        // Stats
        printf("%" PRIu64 ",%" PRIu64 ",%" PRIu64 ",%" PRIu64 "\n",
               (userspace_size + kernel_rss) / 1024, kernel_rss / 1024, total_rss / 1024,
               total_pss / 1024);
    }

    // Per-buffer (Sysfs)
    void PerBufferHeader() override {
        printf("\"Dmabuf Inode\",\"Size(bytes)\",\"Exporter Name\"\n");
    }

    void PerBufferStats(const android::dmabufinfo::DmabufInfo& bufInfo) override {
        printf("%lu,%" PRIu64 ",\"%s\"\n", bufInfo.inode, bufInfo.size, bufInfo.exp_name.c_str());
    }

    void ExporterHeader() override {
        printf("\"%s\",\"%s\",\"%s\"\n", "Exporter Name", "Total Count", "Total Size(bytes)");
    }

    void ExporterStats(const std::string& exporter,
                       const android::dmabufinfo::DmabufTotal& dmaBufTotal) override {
        printf("\"%s\",%u,%" PRIu64 "\n", exporter.c_str(), dmaBufTotal.buffer_count,
               dmaBufTotal.size);
    }

    void SysfsBufTotalStats(const android::dmabufinfo::DmabufSysfsStats& stats) override {
        printf("\"%s\",\"%s\"\n", "Total DMA-BUF count", "Total DMA-BUF size(bytes)");
        printf("%u,%" PRIu64 "\n", stats.total_count(), stats.total_size());
    }
};

class RawOutput final : public DmabufOutputHelper {
  public:
    // Table buffer x process
    void BufTableMainHeaders() override {
        printf("    Dmabuf Inode |            Size |   Fd Ref Counts |  Map Ref Counts |");
    }
    void BufTableProcessHeader(const pid_t pid, const std::string& process) override {
        printf("%16s:%-5d |", process.c_str(), pid);
    }

    void BufTableStats(const android::dmabufinfo::DmaBuffer& buf) override {
        printf("%16ju |%13" PRIu64 " kB |%16zu |%16zu |", static_cast<uintmax_t>(buf.inode()),
               buf.size() / 1024, buf.fdrefs().size(), buf.maprefs().size());
    }

    void BufTableProcessSize(int pid_fdrefs, int pid_maprefs) override {
        if (pid_fdrefs || pid_maprefs)
            printf("%9d(%6d) refs |", pid_fdrefs, pid_maprefs);
        else
            printf("%22s |", "--");
    }

    void BufTableTotalStats(const uint64_t dmabuf_total_size) override {
        printf("%-16s  %13" PRIu64 " kB |%16s |%16s |", "TOTALS", dmabuf_total_size, "n/a", "n/a");
    };

    void BufTableTotalProcessStats(const uint64_t pid_size) override {
        printf("%19" PRIu64 " kB |", pid_size);
    }

    // PerProcess
    void PerProcessHeader(const std::string& process, const pid_t pid) override {
        printf("%16s:%-5d\n", process.c_str(), pid);
        printf("%22s %16s %16s %16s %16s\n", "Name", "Rss", "Pss", "nr_procs", "Inode");
    }

    void PerProcessBufStats(const android::dmabufinfo::DmaBuffer& buf) override {
        printf("%22s %13" PRIu64 " kB %13" PRIu64 " kB %16zu %16" PRIuMAX "\n",
               buf.name().empty() ? "<unknown>" : buf.name().c_str(), buf.size() / 1024,
               buf.Pss() / 1024, buf.pids().size(), static_cast<uintmax_t>(buf.inode()));
    }
    void PerProcessTotalStat(const uint64_t pss, const uint64_t rss) override {
        printf("%22s %13" PRIu64 " kB %13" PRIu64 " kB %16s\n", "PROCESS TOTAL", rss / 1024,
               pss / 1024, "");
    }

    void TotalProcessesStats(const uint64_t& total_rss, const uint64_t& total_pss,
                             const uint64_t& userspace_size, const uint64_t& kernel_rss) override {
        printf("dmabuf total: %" PRIu64 " kB kernel_rss: %" PRIu64 " kB userspace_rss: %" PRIu64
               " kB userspace_pss: %" PRIu64 " kB\n ",
               (userspace_size + kernel_rss) / 1024, kernel_rss / 1024, total_rss / 1024,
               total_pss / 1024);
    }

    // Per-buffer (Sysfs)
    void PerBufferHeader() override {
        printf("    Dmabuf Inode |     Size(bytes) |    Exporter Name                    |\n");
    }

    void PerBufferStats(const android::dmabufinfo::DmabufInfo& bufInfo) override {
        printf("%16lu |%16" PRIu64 " | %16s \n", bufInfo.inode, bufInfo.size,
               bufInfo.exp_name.c_str());
    }

    void ExporterHeader() override {
        printf("      Exporter Name              | Total Count |     Total Size(bytes)   |\n");
    }
    void ExporterStats(const std::string& exporter,
                       const android::dmabufinfo::DmabufTotal& dmaBufTotal) override {
        printf("%32s | %12u| %" PRIu64 "\n", exporter.c_str(), dmaBufTotal.buffer_count,
               dmaBufTotal.size);
    }

    void SysfsBufTotalStats(const android::dmabufinfo::DmabufSysfsStats& stats) override {
        printf("Total DMA-BUF count: %u, Total DMA-BUF size(bytes): %" PRIu64 "\n",
               stats.total_count(), stats.total_size());
    }
};
