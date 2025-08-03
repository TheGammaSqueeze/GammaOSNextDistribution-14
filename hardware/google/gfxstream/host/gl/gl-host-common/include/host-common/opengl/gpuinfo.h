// Copyright 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
#pragma once

#include "aemu/base/Compiler.h"

#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string>
#include <string.h>
#include <vector>

// gpuinfo is designed to collect information about the GPUs
// installed on the host system for the purposes of
// automatically determining which renderer to select,
// in the cases where the GPU drivers are known to have issues
// running the emulator.

// The main entry points:

// host_gpu_blacklisted_async() does the two steps above,
// but on a different thread, with a timeout in case
// the started processes hang or what not.
void async_query_host_gpu_start();
bool async_query_host_gpu_blacklisted();
bool async_query_host_gpu_AngleWhitelisted();
bool async_query_host_gpu_SyncBlacklisted();
bool async_query_host_gpu_VulkanBlacklisted();

// Below is the implementation.

struct GpuInfoView{
    const char* make;
    const char* model;
    const char* device_id;
    const char* revision_id;
    const char* version;
    const char* renderer;
    const char* os;
};
// We keep a blacklist of known crashy GPU drivers
// as a static const list with items of this type:
using BlacklistEntry = GpuInfoView;
// We keep a whitelist to use Angle for buggy
// GPU drivers
using WhitelistEntry = GpuInfoView;

// GpuInfo/GpuInfoList are the representation of parsed information
// about the system's GPU.s
class GpuInfo {
public:
    GpuInfo() : current_gpu(false) { }
    GpuInfo(const std::string& _make,
            const std::string& _model,
            const std::string& _device_id,
            const std::string& _revision_id,
            const std::string& _version,
            const std::string& _renderer) :
        current_gpu(false),
        make(_make),
        model(_model),
        device_id(_device_id),
        revision_id(_revision_id),
        version(_version),
        renderer(_renderer) { }

    bool current_gpu;

    void addDll(std::string dll_str);

    std::string make;
    std::string model;
    std::string device_id;
    std::string revision_id;
    std::string version;
    std::string renderer;

    std::vector<std::string> dlls;
    std::string os;
};

class GpuInfoList {
public:
    GpuInfoList() = default;
    void addGpu();
    GpuInfo& currGpu();
    std::string dump() const;
    void clear();

    std::vector<GpuInfo> infos;

    bool blacklist_status = false;
    bool Anglelist_status = false;
    bool SyncBlacklist_status = false;
    bool VulkanBlacklist_status = false;

    DISALLOW_COPY_ASSIGN_AND_MOVE(GpuInfoList);
};

// Below are helper functions that can be useful in various
// contexts (e.g., unit testing).

// gpuinfo_query_blacklist():
// Function to query a given blacklist of GPU's.
// The blacklist |list| (of length |size|) attempts
// to match all non-NULL entry fields exactly against
// info of all GPU's in |gpulist|. If there is any match,
// the host system is considered on the blacklist.
// (Null blacklist entry fields are ignored and
// essentially act as wildcards).
bool gpuinfo_query_blacklist(GpuInfoList* gpulist,
                             const BlacklistEntry* list,
                             int size);

// Platform-specific information parsing functions.
void parse_gpu_info_list_linux(const std::string& contents, GpuInfoList* gpulist);
void parse_gpu_info_list_windows(const std::string& contents, GpuInfoList* gpulist);

// If we actually switched to software, call this.
void setGpuBlacklistStatus(bool switchedToSoftware);

// Return a fully loaded global GPU info list.
const GpuInfoList& globalGpuInfoList();
