/*
 * Copyright (C) 2008 The Lineage Android Project
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

#ifndef __SGDISK
#define __SGDISK

#include <string>
#include <vector>

enum ptbl_type {
    MBR,
    GPT
};

struct sgdisk_partition_table {
    ptbl_type   type;
    std::string guid;
};

struct sgdisk_partition {
    int         num;
    std::string type;
    std::string guid;
    std::string name;
};

int sgdisk_read(const char* device, sgdisk_partition_table& ptbl,
                std::vector<sgdisk_partition>& partitions);

#endif
