/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <unordered_map>
#include <vector>

namespace android {
namespace dmabufinfo {

/*
 * struct DmabufInfo: Represents information about a DMA-BUF.
 *
 * @inode: The unique inode number for the buffer.
 * @exp_name: Name of the exporter of the buffer.
 * @size: Size of the buffer.
 */
struct DmabufInfo {
    unsigned long inode;
    std::string exp_name;
    uint64_t size;
};

struct DmabufTotal {
    uint64_t size;
    unsigned int buffer_count;
};

class DmabufSysfsStats {
  public:
    inline const std::vector<DmabufInfo>& buffer_stats() const { return buffer_stats_; }
    inline const std::unordered_map<std::string, struct DmabufTotal>& exporter_info() const {
        return exporter_info_;
    }
    inline uint64_t total_size() const { return total_.size; }
    inline unsigned int total_count() const { return total_.buffer_count; }

    friend bool GetDmabufSysfsStats(DmabufSysfsStats* stats, const std::string& path);

  private:
    std::vector<DmabufInfo> buffer_stats_;
    std::unordered_map<std::string, struct DmabufTotal> exporter_info_;
    struct DmabufTotal total_;
};

/*
 * Reads and parses DMA-BUF statistics from sysfs to create per-buffer
 * and per-exporter stats.
 *
 * @stats: output argument that will be populated with information from DMA-BUF sysfs stats.
 * @path: Not for use by clients, to be used only for unit testing.
 *
 * Returns true on success.
 */
bool GetDmabufSysfsStats(DmabufSysfsStats* stats,
                         const std::string& path = "/sys/kernel/dmabuf/buffers");

/*
 * Calculates the total size of all DMA-BUFs exported. It does not read or parse
 * the rest of the DMA-BUF sysfs statistics inorder to be performant.
 *
 * @path: Not for use by clients, to be used only for unit testing.
 *
 * Returns total size of all DMA-BUFs exported in units of KB.
 */
bool GetDmabufTotalExportedKb(uint64_t* total_exported,
                              const std::string& path = "/sys/kernel/dmabuf/buffers");

/* Reads the exporter name of the DMA buffer with @inode */
bool ReadBufferExporter(unsigned int inode, std::string* exporter,
                        const std::string& dmabuf_sysfs_path = "/sys/kernel/dmabuf/buffers");

/* Reads the size of the DMA buffer with @inode */
bool ReadBufferSize(unsigned int inode, uint64_t* size,
                    const std::string& dmabuf_sysfs_path = "/sys/kernel/dmabuf/buffers");

}  // namespace dmabufinfo
}  // namespace android
