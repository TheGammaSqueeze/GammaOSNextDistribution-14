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

#define LOG_TAG "LargeParcelable"

#include "MappedFile.h"

#include <utils/Log.h>

#include <assert.h>
#include <errno.h>
#include <sys/mman.h>

namespace android {
namespace automotive {
namespace car_binder_lib {

MappedFile::MappedFile(int memoryFd, int32_t fileSize, bool writtable) {
    mAddr = mmap(/*addr=*/NULL, fileSize, (writtable ? PROT_WRITE : PROT_READ), MAP_SHARED,
                 memoryFd, /*offset=*/0);
    if (mAddr == MAP_FAILED) {
        ALOGE("mmap failed: %s", std::strerror(errno));
        mErrno = errno;
    }
    mReadOnly = !writtable;
    mSize = fileSize;
}

void MappedFile::sync() const {
    msync(mAddr, mSize, MS_SYNC);
}

MappedFile::~MappedFile() {
    if (isValid()) {
        munmap(mAddr, mSize);
    }
}

}  // namespace car_binder_lib
}  // namespace automotive
}  // namespace android
