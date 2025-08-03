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

#ifndef SECURE_STORAGE_H_
#define SECURE_STORAGE_H_

#include <stddef.h>
#include <stdint.h>

namespace avb {

// Abstract interface for secure storage.
class SecureStorageInterface {
public:
    SecureStorageInterface() = default;
    virtual ~SecureStorageInterface() = default;

    // SecureStorageInterface is neither copyable nor moveable
    SecureStorageInterface(const SecureStorageInterface&) = delete;
    SecureStorageInterface& operator=(const SecureStorageInterface&) = delete;

    // Opens a file in secure storage named |filename|.
    //
    // Returns NO_ERROR on success, negative error code on failure.
    virtual int open(const char* filename) = 0;

    // Deletes a file in secure storage named |filename|.
    //
    // Returns NO_ERROR on success, negative error code on failure.
    virtual int delete_file(const char* filename) = 0;

    // Reads |size| bytes into |buf| from the file starting at offset |off|. The
    // file must have been previously opened by open().
    //
    // Returns number of bytes read on success, negative error code on failure.
    virtual int read(uint64_t off, void* buf, size_t size) const = 0;

    // Gets the size of the file in secure storage previously opened with open()
    // and stores it in |size|.
    //
    // Returns NO_ERROR on success, negative error code on failure.
    virtual int get_file_size(uint64_t* size) const = 0;

    // Writes |size| bytes from |buf| into the file starting at offset |off|.
    // The file must have been previously opened by open().
    //
    // Returns number of bytes written on succes, negative error code on
    // failure.
    virtual int write(uint64_t off, const void* buf, size_t size) const = 0;
};

}  // namespace avb

#endif  // SECURE_STORAGE_H_
