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

#include "SharedMemory.h"

#include "MappedFile.h"

#include <android-base/unique_fd.h>
#include <cutils/ashmem.h>
#include <utils/Errors.h>
#include <utils/Log.h>

#include <assert.h>
#include <errno.h>
#include <sys/mman.h>

#include <memory>

namespace android {
namespace automotive {
namespace car_binder_lib {

using ::android::OK;
using ::android::status_t;
using ::android::base::borrowed_fd;
using ::android::base::unique_fd;

SharedMemory::SharedMemory(unique_fd fd) : mBorrowedFd(-1), mOwned(true) {
    int intFd = fd.get();
    if (!ashmem_valid(intFd)) {
        ALOGE("%s", "the FD is not valid");
        mErrno = errno;
        return;
    }
    int size = ashmem_get_size_region(intFd);
    if (size < 0) {
        ALOGE("ashmem_get_size_region failed, error: %s", std::strerror(errno));
        mErrno = errno;
        return;
    }
    mOwnedFd = std::move(fd);
    mSize = size;
}

SharedMemory::SharedMemory(borrowed_fd fd) : mBorrowedFd(-1), mOwned(false) {
    int intFd = fd.get();
    if (!ashmem_valid(intFd)) {
        ALOGE("%s", "the FD is not valid");
        return;
    }
    int size = ashmem_get_size_region(intFd);
    if (size < 0) {
        ALOGE("ashmem_get_size_region failed, error: %s", std::strerror(errno));
        mErrno = errno;
        return;
    }
    mBorrowedFd = std::move(fd);
    mSize = size;
}

SharedMemory::SharedMemory(size_t size) : mBorrowedFd(-1), mOwned(true) {
    int fd = ashmem_create_region("SharedMemory", size);
    if (fd < 0) {
        ALOGE("ASharedMemory_create failed, error: %s", std::strerror(errno));
        mErrno = errno;
        return;
    }
    mOwnedFd.reset(fd);
    mSize = size;
}

status_t SharedMemory::lock() {
    int result = ashmem_set_prot_region(getFd(), PROT_READ);
    if (result != 0) {
        ALOGE("ASharedMemory_setProt failed, error: %s", std::strerror(errno));
        mErrno = errno;
        return -result;
    }
    mLocked = true;
    return OK;
}

}  // namespace car_binder_lib
}  // namespace automotive
}  // namespace android
