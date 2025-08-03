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

#include <algorithm>

#include "diskio.h"

using namespace std;

int DiskIO::OpenForRead(const unsigned char* data, size_t size) {
    this->data = data;
    this->size = size;
    this->off = 0;
    this->isOpen = 1;
    this->openForWrite = 0;
    return 1;
}

void DiskIO::MakeRealName(void) {
    realFilename = userFilename;
}

int DiskIO::OpenForRead(void) {
    return 1;
}

int DiskIO::OpenForWrite(void) {
    return 1;
}

void DiskIO::Close(void) {
}

int DiskIO::GetBlockSize(void) {
    return 512;
}

int DiskIO::GetPhysBlockSize(void) {
    return 512;
}

uint32_t DiskIO::GetNumHeads(void) {
    return 255;
}

uint32_t DiskIO::GetNumSecsPerTrack(void) {
    return 63;
}

int DiskIO::DiskSync(void) {
    return 1;
}

int DiskIO::Seek(uint64_t sector) {
    off_t off = sector * GetBlockSize();
    if (off >= this->size) {
        return 0;
    } else {
        this->off = off;
        return 1;
    }
}

int DiskIO::Read(void* buffer, int numBytes) {
    int actualBytes = std::min(static_cast<int>(this->size - this->off), numBytes);
    memcpy(buffer, this->data + this->off, actualBytes);
    return actualBytes;
}

int DiskIO::Write(void*, int) {
    return 0;
}

uint64_t DiskIO::DiskSize(int *) {
    return this->size / GetBlockSize();
}
