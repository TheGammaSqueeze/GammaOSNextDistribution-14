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

#include <fstream>
#include <string>
#include <log/log.h>
#include <sys/stat.h>

extern bool fs_mgr_get_boot_config(const std::string& key, std::string* out_val);

namespace {
int printUsage() {
    ALOGE("Usage: qemu-export-property [-f] property_name filename");
    return 1;
}

int exportPropertyImpl(const char* propName, const char* filename) {
    std::string propValue;
    if (!fs_mgr_get_boot_config(propName, &propValue)) {
        ALOGV("'%s' bootconfig property is not set", propName);
        return 0;
    }

    std::ofstream f;
    f.open(filename);
    if (f.is_open()) {
        f << propValue;
        f.close();
        return 0;
    } else {
        ALOGE("Failed to open '%s'\n", filename);
        return 1;
    }
}
} // namespace

int main(const int argc, const char* argv[]) {
    if (argc < 2) {
        return printUsage();
    }

    if (strcmp(argv[1], "-f") == 0) {
        if (argc == 4) {
            return exportPropertyImpl(argv[2], argv[3]);
        } else {
            return printUsage();
        }
    } else if (argc == 3) {
        struct stat st;
        if (stat(argv[2], &st) == 0) {
            ALOGV("'%s' already exists", argv[2]);
            return 0;
        } else {
            return exportPropertyImpl(argv[1], argv[2]);
        }
    } else {
        return printUsage();
    }
}
