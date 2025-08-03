/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * This software is licensed under the terms of the GNU General Public
 * License version 2, as published by the Free Software Foundation, and
 * may be copied, distributed, and modified under those terms.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

#include <fstream>
#include <iostream>

#include "diskio.h"
#include "mbr.h"
#include "gpt.h"

#include <fuzzer/FuzzedDataProvider.h>

std::ofstream silence("/dev/null");

extern "C" int LLVMFuzzerInitialize(int *, char ***) {
    std::cout.rdbuf(silence.rdbuf());
    std::cerr.rdbuf(silence.rdbuf());
    return 0;
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
    DiskIO disk;
    disk.OpenForRead(static_cast<const unsigned char*>(data), size);

    BasicMBRData mbrData;
    mbrData.ReadMBRData(&disk);

    GPTData gptData;
    gptData.SetDisk(disk);
    gptData.LoadPartitions("/dev/does_not_exist");

    return 0;
}
