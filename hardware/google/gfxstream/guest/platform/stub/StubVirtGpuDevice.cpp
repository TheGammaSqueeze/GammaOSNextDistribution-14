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

StubVirtGpuDevice::StubVirtGpuDevice(enum VirtGpuCapset capset) : VirtGpuDevice(capset) {
    // Unimplemented stub
}

struct VirtGpuCaps StubVirtGpuDevice::getCaps(void) { return mCaps; }

int64_t StubVirtGpuDevice::getDeviceHandle(void) {
    return mDeviceHandle;
}

VirtGpuBlobPtr StubVirtGpuDevice::createVirglBlob(uint32_t width, uint32_t height, uint32_t virglFormat) {
    (void)width;
    (void)height;
    (void)virglFormat;
    return nullptr;
}

VirtGpuBlobPtr StubVirtGpuDevice::createBlob(const struct VirtGpuCreateBlob&) {
    return nullptr;
}

VirtGpuBlobPtr StubVirtGpuDevice::importBlob(const struct VirtGpuExternalHandle&) {
    return nullptr;
}

int StubVirtGpuDevice::execBuffer(struct VirtGpuExecBuffer&, const VirtGpuBlob*) { return -1; }

VirtGpuBlobPtr createColorBuffer(int, int, uint32_t) {
    return nullptr;
}

VirtGpuBlobPtr createColorBuffer(int) {
    return nullptr;
}

StubVirtGpuDevice::~StubVirtGpuDevice() {
    // Unimplemented stub
}

VirtGpuDevice* createPlatformVirtGpuDevice(enum VirtGpuCapset capset, int) {
    return new StubVirtGpuDevice(capset);
}
