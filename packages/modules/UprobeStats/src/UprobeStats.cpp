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

#define LOG_TAG "uprobestats"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/strings.h>
#include <android_uprobestats_flags.h>

#include <string>

#include "BpfUtilities.h"
#include "ProcessInfoRetriever.h"


// Reads probing configuration from a file. This is a temporary implementation
// that's to be replaced with something that supports protobuf.
int readConfig(std::string configFilePath, std::string* filename, int* offset) {
    std::string config_str;
    if (!android::base::ReadFileToString(configFilePath, &config_str)) {
        LOG(ERROR) << "Failed to open config file";
        return 1;
    }
    std::vector<std::string> config_fields = android::base::Split(config_str, " ");
    if (config_fields.size() != 2) {
        return -1;
    }
    *filename = config_fields[0];
    if (!android::base::ParseInt(android::base::Trim(config_fields[1]), offset)) {
        return -1;
    }
    return 0;
}

int main(int argc, char **argv) {
    if (!android::uprobestats::flags::enable_uprobestats()) {
        LOG(ERROR) << "uprobestats disabled by flag. Exiting.";
        return 1;
    }
    if (argc < 2) {
        LOG(ERROR) << "Not enough command line arguments. Exiting.";
        return 1;
    }
    std::string filename;
    int offset;
    readConfig(std::string("/data/misc/uprobestats-configs/") + argv[1], &filename, &offset);
    const char* prog_path =
            "/sys/fs/bpf/uprobestats/prog_BitmapAllocation_uprobe_bitmap_constructor_heap";
    const char *map_path = "/sys/fs/bpf/uprobestats/map_BitmapAllocation_output_buf";
    int pid = android::uprobestats::getPid("system_server");
    if (pid < 0) {
        LOG(ERROR) << "Unable to find pid of system_server";
    }
    android::uprobestats::bpfPerfEventOpen(filename.c_str(), offset, pid, prog_path);
    sleep(10);
    android::uprobestats::printRingBuf(map_path);
    return 0;
}
