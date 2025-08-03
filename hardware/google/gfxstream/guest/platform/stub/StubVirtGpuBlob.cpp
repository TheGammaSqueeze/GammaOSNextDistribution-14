/*
 * Copyright 2022 The Android Open Source Project
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

#include "StubVirtGpu.h"

StubVirtGpuBlob::StubVirtGpuBlob(int64_t deviceHandle, uint32_t blobHandle, uint32_t resourceHandle,
                         uint64_t size)
    : mDeviceHandle(deviceHandle),
      mBlobHandle(blobHandle),
      mResourceHandle(resourceHandle),
      mSize(size) {}

StubVirtGpuBlob::~StubVirtGpuBlob() {
    // Unimplemented stub
}

uint32_t StubVirtGpuBlob::getBlobHandle() const { return 0; }

uint32_t StubVirtGpuBlob::getResourceHandle() const { return 0; }

VirtGpuBlobMappingPtr StubVirtGpuBlob::createMapping() { return nullptr; }

int StubVirtGpuBlob::wait() {
    return -1;
}

int StubVirtGpuBlob::transferFromHost(uint32_t, uint32_t) {
    return -1;
}

int StubVirtGpuBlob::transferToHost(uint32_t, uint32_t) {
    return -1;
}
