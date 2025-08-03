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

#include <errno.h>

#include <lib/storage/storage.h>

#include "secure_storage_interface.h"

namespace avb {

// Implementation of SecureStorageInterface that uses Trusty's
// secure storage library
class SecureStorage : public SecureStorageInterface {
public:
    ~SecureStorage() override;
    int open(const char* name) override;
    int delete_file(const char* name) override;
    int read(uint64_t off, void* buf, size_t size) const override;
    int get_file_size(uint64_t* size) const override;
    int write(uint64_t off, const void* buf, size_t size) const override;

private:
    void close();

    storage_session_t session_handle_ = 0;
    file_handle_t file_handle_ = 0;
    int error_ = -EINVAL;
};

}  // namespace avb
