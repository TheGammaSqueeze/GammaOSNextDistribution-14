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

#ifndef CPP_CAR_BINDER_LIB_LARGEPARCELABLE_INCLUDE_MAPPEDFILE_H_
#define CPP_CAR_BINDER_LIB_LARGEPARCELABLE_INCLUDE_MAPPEDFILE_H_

#include <utils/Errors.h>

#include <assert.h>
#include <sys/mman.h>

namespace android {
namespace automotive {
namespace car_binder_lib {

using ::android::status_t;

// MappedFile represents a memory area mapped from a file. This class owns this area and would
// unmap it during destruction.
class MappedFile {
public:
    // Create a new mapped memory area from 'memoryFd' with size 'fileSize'. If 'write' is true, the
    // area would be mapped with read/write permission, otherwise, read only. Caller should use
    // isValid() to check whether the initialization succeed and use getErr() to read err if
    // isValid() is not true.
    MappedFile(int memoryFd, int32_t fileSize, bool writtable);

    inline bool isValid() const { return (mAddr != MAP_FAILED); }

    inline status_t getErr() const { return -mErrno; }

    inline const void* getAddr() const { return mAddr; }

    inline void* getWriteAddr() const {
        assert(!mReadOnly);
        return mAddr;
    }

    inline size_t getSize() const { return mSize; }

    void sync() const;

    ~MappedFile();

private:
    size_t mSize = 0;
    void* mAddr = MAP_FAILED;
    int mErrno = 0;
    bool mReadOnly;
};

}  // namespace car_binder_lib
}  // namespace automotive
}  // namespace android

#endif  // CPP_CAR_BINDER_LIB_LARGEPARCELABLE_INCLUDE_MAPPEDFILE_H_
