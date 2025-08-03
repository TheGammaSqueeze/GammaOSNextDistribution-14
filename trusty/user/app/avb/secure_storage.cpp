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

#include "secure_storage.h"

namespace avb {

SecureStorage::~SecureStorage() {
    close();
}

int SecureStorage::open(const char* name) {
    // Close any storage session/file still open
    close();
    error_ = storage_open_session(&session_handle_, STORAGE_CLIENT_TP_PORT);
    if (error_ < 0) {
        return error_;
    }
    error_ = storage_open_file(session_handle_, &file_handle_, name,
                               STORAGE_FILE_OPEN_CREATE, 0 /* don't commit */);
    return error_;
}

int SecureStorage::delete_file(const char* name) {
    // Reuse existing storage session if possible
    if (session_handle_ == 0) {
        error_ = storage_open_session(&session_handle_, STORAGE_CLIENT_TP_PORT);
    }
    if (error_ < 0) {
        return error_;
    }
    return storage_delete_file(session_handle_, name, STORAGE_OP_COMPLETE);
}

int SecureStorage::read(uint64_t off, void* buf, size_t size) const {
    if (error_ < 0) {
        return error_;
    }
    return storage_read(file_handle_, off, buf, size);
}

int SecureStorage::get_file_size(uint64_t* size) const {
    if (error_ < 0) {
        return error_;
    }
    return storage_get_file_size(file_handle_, size);
}

int SecureStorage::write(uint64_t off, const void* buf, size_t size) const {
    if (error_ < 0) {
        return error_;
    }
    return storage_write(file_handle_, off, buf, size, STORAGE_OP_COMPLETE);
}

void SecureStorage::close() {
    if (error_ < 0) {
        return;
    }
    if (file_handle_) {
        storage_close_file(file_handle_);
    }
    if (session_handle_) {
        storage_close_session(session_handle_);
    }
    file_handle_ = 0;
    session_handle_ = 0;
    error_ = -EINVAL;
}

}  // namespace avb
