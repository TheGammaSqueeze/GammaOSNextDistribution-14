/*
 * Copyright (C) 2016, 2018-2020 ARM Limited. All rights reserved.
 *
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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

#include "mali_gralloc_reference.h"

#include <android-base/thread_annotations.h>
#include <hardware/gralloc1.h>
#include <unistd.h>

#include <algorithm>
#include <map>
#include <mutex>

#include "allocator/mali_gralloc_ion.h"
#include "mali_gralloc_buffer.h"

class BufferManager {
private:
    // This struct for now is just for validation and reference counting. When we
    // are sure that private_handle_t::bases is not being used outside gralloc, this
    // should become the only place where address mapping is maintained and can be
    // queried from.
    struct MappedData {
        std::array<void *, MAX_BUFFER_FDS> bases;
        size_t alloc_sizes[MAX_BUFFER_FDS] = {};

        void *metadata_vaddr;
        size_t metadata_size;

        uint64_t ref_count = 0;
    };

    BufferManager() = default;

    std::mutex lock;
    std::map<const private_handle_t *, std::unique_ptr<MappedData>> buffer_map GUARDED_BY(lock);

    static off_t get_buffer_size(unsigned int fd) {
        off_t current = lseek(fd, 0, SEEK_CUR);
        off_t size = lseek(fd, 0, SEEK_END);
        lseek(fd, current, SEEK_SET);
        return size;
    }

    // TODO(b/296934447): AION buffers set the size of the buffer themselves. That size exceeds the
    // size of the actual allocated dmabuf.
    static bool dmabuf_sanity_check(buffer_handle_t handle, bool skip_buffer_size_check = false) {
        private_handle_t *hnd =
                static_cast<private_handle_t *>(const_cast<native_handle_t *>(handle));

        if (hnd->fd_count < 0 || hnd->fd_count > MAX_FDS) {
            MALI_GRALLOC_LOGE("%s failed: invalid number of fds (%d)", __func__, hnd->fd_count);
            return false;
        }

        int valid_fd_count = std::find(hnd->fds, hnd->fds + MAX_FDS, -1) - hnd->fds;
        // One fd is reserved for metadata which is not accounted for in fd_count
        if (hnd->fd_count + 1 != valid_fd_count) {
            MALI_GRALLOC_LOGE("%s failed: count of valid buffer fds does not match fd_count (%d != "
                              "%d)",
                              __func__, hnd->fd_count, valid_fd_count - 1);
            return false;
        }

        auto check_pid = [&](int fd, uint64_t allocated_size) -> bool {
            auto size = get_buffer_size(fd);
            auto size_padding = size - (off_t)allocated_size;
            if ((size != -1) && ((size_padding < 0) || (size_padding > getpagesize()))) {
                MALI_GRALLOC_LOGE("%s failed: fd (%d) size (%jd) is not within a page of "
                                  "expected size (%" PRIx64 ")",
                                  __func__, fd, static_cast<intmax_t>(size), allocated_size);
                return false;
            }
            return true;
        };

        // Check client facing dmabufs
        if (!skip_buffer_size_check) {
            for (auto i = 0; i < hnd->fd_count; i++) {
                if (!check_pid(hnd->fds[i], hnd->alloc_sizes[i])) {
                    MALI_GRALLOC_LOGE("%s failed: Size check failed for alloc_sizes[%d]", __func__,
                                      i);
                    return false;
                }
            }
        }

        // Check metadata dmabuf
        if (!check_pid(hnd->get_share_attr_fd(), hnd->attr_size)) {
            MALI_GRALLOC_LOGE("%s failed: Size check failed for metadata fd", __func__);
            return false;
        }

        return true;
    }

    bool map_buffer_locked(buffer_handle_t handle) REQUIRES(lock) {
        auto data_oe = get_validated_data_locked(handle);
        if (!data_oe.has_value()) {
            return false;
        }
        MappedData &data = data_oe.value();

        // Return early if buffer is already mapped
        if (data.bases[0] != nullptr) {
            return true;
        }

        if (!dmabuf_sanity_check(handle)) {
            return false;
        }

        private_handle_t *hnd =
                reinterpret_cast<private_handle_t *>(const_cast<native_handle *>(handle));
        data.bases = mali_gralloc_ion_map(hnd);
        if (data.bases[0] == nullptr) {
            return false;
        }

        for (auto i = 0; i < MAX_BUFFER_FDS; i++) {
            data.alloc_sizes[i] = hnd->alloc_sizes[i];
        }

        return true;
    }

    bool map_metadata_locked(buffer_handle_t handle) REQUIRES(lock) {
        auto data_oe = get_validated_data_locked(handle);
        if (!data_oe.has_value()) {
            return false;
        }
        MappedData &data = data_oe.value();

        // Return early if buffer is already mapped
        if (data.metadata_vaddr != nullptr) {
            return true;
        }

        if (!dmabuf_sanity_check(handle, /*skip_buffer_size_check=*/true)) {
            return false;
        }

        private_handle_t *hnd =
                reinterpret_cast<private_handle_t *>(const_cast<native_handle *>(handle));
        data.metadata_vaddr = mmap(nullptr, hnd->attr_size, PROT_READ | PROT_WRITE, MAP_SHARED,
                                   hnd->get_share_attr_fd(), 0);
        if (data.metadata_vaddr == nullptr) {
            return false;
        }

        data.metadata_size = hnd->attr_size;
        return true;
    }

    bool validate_locked(buffer_handle_t handle) REQUIRES(lock) {
        if (private_handle_t::validate(handle) < 0) {
            MALI_GRALLOC_LOGE("Reference invalid buffer %p, returning error", handle);
            return false;
        }

        const auto *hnd = reinterpret_cast<private_handle_t *>(const_cast<native_handle *>(handle));
        auto it = buffer_map.find(hnd);
        if (it == buffer_map.end()) {
            MALI_GRALLOC_LOGE("Reference unimported buffer %p, returning error", handle);
            return false;
        }

        auto &data = *(it->second.get());
        if (data.bases[0] != nullptr) {
            for (auto i = 0; i < MAX_BUFFER_FDS; i++) {
                if (data.alloc_sizes[i] != hnd->alloc_sizes[i]) {
                    MALI_GRALLOC_LOGE(
                            "Validation failed: Buffer attributes inconsistent with mapper");
                    return false;
                }
            }
        } else {
            for (auto i = 0; i < MAX_BUFFER_FDS; i++) {
                if (data.bases[i] != nullptr) {
                    MALI_GRALLOC_LOGE("Validation failed: Expected nullptr for unmapped buffer");
                    return false;
                }
            }
        }

        return true;
    }

    std::optional<std::reference_wrapper<MappedData>> get_validated_data_locked(
            buffer_handle_t handle) REQUIRES(lock) {
        if (!validate_locked(handle)) {
            return {};
        }

        private_handle_t *hnd =
                reinterpret_cast<private_handle_t *>(const_cast<native_handle *>(handle));
        auto it = buffer_map.find(hnd);
        if (it == buffer_map.end()) {
            MALI_GRALLOC_LOGE("Trying to release a non-imported buffer");
            return {};
        }

        MappedData &data = *(it->second.get());
        if (data.ref_count == 0) {
            MALI_GRALLOC_LOGE("BUG: Found an imported buffer with ref count 0, expect errors");
        }

        return data;
    }

public:
    static BufferManager &getInstance() {
        static BufferManager instance;
        return instance;
    }

    int retain(buffer_handle_t handle) EXCLUDES(lock) {
        if (private_handle_t::validate(handle) < 0) {
            MALI_GRALLOC_LOGE("Registering/Retaining invalid buffer %p, returning error", handle);
            return -EINVAL;
        }
        std::lock_guard<std::mutex> _l(lock);

        private_handle_t *hnd =
                reinterpret_cast<private_handle_t *>(const_cast<native_handle *>(handle));

        auto it = buffer_map.find(hnd);
        if (it == buffer_map.end()) {
            bool success = false;
            auto _data = std::make_unique<MappedData>();

            std::tie(it, success) = buffer_map.insert({hnd, std::move(_data)});
            if (!success) {
                MALI_GRALLOC_LOGE("Failed to create buffer data mapping");
                return -EINVAL;
            }
        } else if (it->second->ref_count == 0) {
            MALI_GRALLOC_LOGE("BUG: Import counter of an imported buffer is 0, expect errors");
        }
        auto &data = *(it->second.get());

        data.ref_count++;
        return 0;
    }

    int map(buffer_handle_t handle) EXCLUDES(lock) {
        std::lock_guard<std::mutex> _l(lock);
        if (!map_buffer_locked(handle)) {
            return -EINVAL;
        }

        return 0;
    }

    int release(buffer_handle_t handle) EXCLUDES(lock) {
        std::lock_guard<std::mutex> _l(lock);

        auto data_oe = get_validated_data_locked(handle);
        if (!data_oe.has_value()) {
            return -EINVAL;
        }
        MappedData &data = data_oe.value();

        if (data.ref_count == 0) {
            MALI_GRALLOC_LOGE("BUG: Reference held for buffer whose counter is 0");
            return -EINVAL;
        }

        data.ref_count--;
        if (data.ref_count == 0) {
            private_handle_t *hnd =
                    reinterpret_cast<private_handle_t *>(const_cast<native_handle *>(handle));
            auto it = buffer_map.find(hnd);

            if (data.bases[0] != nullptr) {
                mali_gralloc_ion_unmap(hnd, data.bases);
            }

            if (data.metadata_vaddr != nullptr) {
                munmap(data.metadata_vaddr, data.metadata_size);
                data.metadata_vaddr = nullptr;
            }

            buffer_map.erase(it);
        }

        return 0;
    }

    int validate(buffer_handle_t handle) EXCLUDES(lock) {
        std::lock_guard<std::mutex> _l(lock);

        if (!validate_locked(handle)) {
            return -EINVAL;
        }

        return 0;
    }

    std::optional<void *> get_buf_addr(buffer_handle_t handle) {
        std::lock_guard<std::mutex> _l(lock);

        auto data_oe = get_validated_data_locked(handle);
        if (!data_oe.has_value()) {
            return {};
        }
        MappedData &data = data_oe.value();

        if (data.bases[0] == nullptr) {
            MALI_GRALLOC_LOGE("BUG: Called %s for an un-mapped buffer", __FUNCTION__);
            return {};
        }

        return data.bases[0];
    }

    std::optional<void *> get_metadata_addr(buffer_handle_t handle) {
        std::lock_guard<std::mutex> _l(lock);

        auto data_oe = get_validated_data_locked(handle);
        if (!data_oe.has_value()) {
            return {};
        }
        MappedData &data = data_oe.value();

        if (data.metadata_vaddr == nullptr) {
            if (!map_metadata_locked(handle)) {
                return {};
            }
        }

        return data.metadata_vaddr;
    }
};

int mali_gralloc_reference_retain(buffer_handle_t handle) {
    return BufferManager::getInstance().retain(handle);
}

int mali_gralloc_reference_map(buffer_handle_t handle) {
    return BufferManager::getInstance().map(handle);
}

int mali_gralloc_reference_release(buffer_handle_t handle) {
    return BufferManager::getInstance().release(handle);
}

int mali_gralloc_reference_validate(buffer_handle_t handle) {
    return BufferManager::getInstance().validate(handle);
}

std::optional<void *> mali_gralloc_reference_get_buf_addr(buffer_handle_t handle) {
    return BufferManager::getInstance().get_buf_addr(handle);
}

std::optional<void *> mali_gralloc_reference_get_metadata_addr(buffer_handle_t handle) {
    return BufferManager::getInstance().get_metadata_addr(handle);
}
