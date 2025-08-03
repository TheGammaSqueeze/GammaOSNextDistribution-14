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
 * limitations under the License. */

#include <libelf64/iter.h>
#include <libelf64/parse.h>

#include <filesystem>

namespace android {
namespace elf64 {

int ForEachElf64FromDir(const std::string& path, const Elf64Callback& callback) {
    int nr_parsed = 0;

    for (const std::filesystem::directory_entry& dir_entry :
        std::filesystem::recursive_directory_iterator(path)) {

        if (dir_entry.is_symlink() || !dir_entry.is_regular_file())
            continue;

        std::string name = dir_entry.path();
        android::elf64::Elf64Binary elf64Binary;
        if (Elf64Parser::ParseElfFile(name, elf64Binary)) {
            nr_parsed++;
        } else {
            continue;
        }

        callback(elf64Binary);
    }

    return nr_parsed;
}

}  // namespace elf64
}  // namespace android

