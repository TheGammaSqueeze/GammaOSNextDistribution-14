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

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <inttypes.h>

#include <dmabufinfo/dmabuf_sysfs_stats.h>

#include <filesystem>
#include <string>
#include <unordered_map>

namespace android {
namespace dmabufinfo {

static bool ReadUintFromFile(const std::string& path, uint64_t* val) {
    std::string temp;

    if (!android::base::ReadFileToString(path, &temp)) {
        PLOG(ERROR) << "Unable to access " << path;
        return false;
    }

    if (!android::base::ParseUint(android::base::Trim(temp), val)) {
        LOG(ERROR) << "Unable to parse value from " << path;
        return false;
    }
    return true;
}

bool ReadBufferExporter(unsigned int inode, std::string* exporter,
                        const std::string& dmabuf_sysfs_path) {
    std::string exporter_path =
            ::android::base::StringPrintf("%s/%u/exporter_name", dmabuf_sysfs_path.c_str(), inode);
    return android::base::ReadFileToString(exporter_path, exporter);
}

bool ReadBufferSize(unsigned int inode, uint64_t* size, const std::string& dmabuf_sysfs_path) {
    std::string size_path =
            ::android::base::StringPrintf("%s/%u/size", dmabuf_sysfs_path.c_str(), inode);
    return ReadUintFromFile(size_path, size);
}

bool GetDmabufSysfsStats(DmabufSysfsStats* stats, const std::string& dmabuf_sysfs_stats_path) {
    std::unique_ptr<DIR, int (*)(DIR*)> dir(opendir(dmabuf_sysfs_stats_path.c_str()), closedir);

    if (!dir) {
        PLOG(ERROR) << "Unable to access: " << dmabuf_sysfs_stats_path;
        return false;
    }

    // clear stats
    *stats = {};

    // Iterate over all the buffer directories to save exporter name, and size.
    struct dirent* dent;
    while ((dent = readdir(dir.get()))) {
        if (!strcmp(dent->d_name, ".") || !strcmp(dent->d_name, "..")) continue;

        std::string buf_entry_path = ::android::base::StringPrintf(
                "%s/%s", dmabuf_sysfs_stats_path.c_str(), dent->d_name);

        struct DmabufInfo info = {};

        // Save inode number from dir name
        if (!android::base::ParseUint(dent->d_name, &info.inode)) {
            LOG(ERROR) << "Unable to parse value from " << dent->d_name;
            return false;
        }

        // Read exporter name for the buffer
        std::string exp_name_path = buf_entry_path + "/exporter_name";
        std::string exporter_name;
        if (!android::base::ReadFileToString(exp_name_path, &exporter_name)) {
            PLOG(ERROR) << "Unable to access " << exp_name_path;
            return false;
        }

        info.exp_name = android::base::Trim(exporter_name);

        // Read size of the buffer
        std::string size_path = buf_entry_path + "/size";
        if (!ReadUintFromFile(size_path, &info.size)) return false;

        // Update totals
        stats->total_.size += info.size;
        stats->total_.buffer_count++;

        stats->buffer_stats_.emplace_back(info);

        // update exporter_info_ map.
        auto exp_stats = stats->exporter_info_.find(info.exp_name);
        if (exp_stats != stats->exporter_info_.end()) {
            exp_stats->second.size += info.size;
            exp_stats->second.buffer_count++;
        } else {
            struct DmabufTotal total = {.size = info.size, .buffer_count = 1};
            stats->exporter_info_[info.exp_name] = total;
        }
    }

    return true;
}

bool GetDmabufTotalExportedKb(uint64_t* total_exported,
                              const std::string& dmabuf_sysfs_stats_path) {
    std::unique_ptr<DIR, int (*)(DIR*)> dir(opendir(dmabuf_sysfs_stats_path.c_str()), closedir);
    if (!dir) {
        PLOG(ERROR) << "Unable to access: " << dmabuf_sysfs_stats_path;
        return false;
    }

    *total_exported = 0;
    struct dirent* dent;
    while ((dent = readdir(dir.get()))) {
        if (!strcmp(dent->d_name, ".") || !strcmp(dent->d_name, "..")) continue;

        std::string buf_entry_path = ::android::base::StringPrintf(
                "%s/%s", dmabuf_sysfs_stats_path.c_str(), dent->d_name);

        // Read size of the buffer
        uint64_t buf_size = 0;
        std::string size_path = buf_entry_path + "/size";
        if (!ReadUintFromFile(size_path, &buf_size)) return false;
        *total_exported += buf_size;
    }

    *total_exported = *total_exported / 1024;

    return true;
}
}  // namespace dmabufinfo
}  // namespace android
