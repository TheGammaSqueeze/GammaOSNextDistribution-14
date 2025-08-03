/*
 * Copyright 2016 The Android Open Source Project
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

#include "avb_manager.h"
#include "secure_storage_interface.h"

namespace avb {

// Fake implementation of SecureStorageInterface
class SecureStorageFake : public SecureStorageInterface {
public:
    int open(const char* name) override { return 0; }
    int delete_file(const char* name) override { return 0; }
    int read(uint64_t off, void* buf, size_t size) const override {
        *(uint64_t*)buf = 1;
        return size;
    }
    int get_file_size(uint64_t* size) const override {
        *size = sizeof(uint64_t) * kRollbackSlotMax;
        return 0;
    }
    int write(uint64_t off, const void* buf, size_t size) const override {
        return size;
    }
};

}  // namespace avb
