/*
 *  ion.cpp
 *
 *   Copyright 2021 Samsung Electronics Co., Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

#include <BufferAllocator/BufferAllocator.h>
#include <assert.h>
#include <errno.h>
#include <hardware/exynos/ion.h>
#include <ion/ion.h>
#include <linux/dma-buf.h>
#include <linux/dma-heap.h>
#include <log/log.h>
#include <stdatomic.h>
#include <string.h>
#include <sys/stat.h>

#include <mutex>

#define ARRAY_SIZE(a) (sizeof(a) / sizeof(*(a)))

static const struct {
    std::string heap_name;
    std::string ion_heap_name;
    unsigned int ion_heap_flags;
    unsigned int legacy_ion_heap_mask;
} heap_map_table[] = {
        {"system", "ion_system_heap", ION_FLAG_CACHED, EXYNOS_ION_HEAP_SYSTEM_MASK},
        {"system-uncached", "ion_system_heap", 0, EXYNOS_ION_HEAP_SYSTEM_MASK},
        {"crypto", "crypto_heap", ION_FLAG_CACHED, EXYNOS_ION_HEAP_CRYPTO_MASK},
        {"crypto-uncached", "crypto_heap", 0, EXYNOS_ION_HEAP_CRYPTO_MASK},
        {"vstream-secure", "vstream_heap", ION_FLAG_PROTECTED, EXYNOS_ION_HEAP_VIDEO_STREAM_MASK},
        {"vframe-secure", "vframe_heap", ION_FLAG_PROTECTED, EXYNOS_ION_HEAP_VIDEO_FRAME_MASK},
        {"vscaler-secure", "vscaler_heap", ION_FLAG_PROTECTED, EXYNOS_ION_HEAP_VIDEO_SCALER_MASK},
        {"faceauth_tpu-secure", "fatpu_heap", ION_FLAG_PROTECTED, EXYNOS_ION_HEAP_FA_TPU_MASK},
        {"faimg-secure", "faimg_heap", ION_FLAG_PROTECTED, EXYNOS_ION_HEAP_FA_IMG_MASK},
        {"farawimg-secure", "farawimg_heap", ION_FLAG_PROTECTED, EXYNOS_ION_HEAP_FA_RAWIMG_MASK},
        {"faprev-secure", "faprev_heap", ION_FLAG_PROTECTED, EXYNOS_ION_HEAP_FA_PREV_MASK},
        {"famodel-secure", "famodel_heap", ION_FLAG_PROTECTED, EXYNOS_ION_HEAP_FA_MODEL_MASK},
};

const char *exynos_ion_get_heap_name(unsigned int legacy_heap_id) {
    if (legacy_heap_id >= ARRAY_SIZE(heap_map_table))
        return NULL;

    return heap_map_table[legacy_heap_id].ion_heap_name.c_str();
}

int exynos_ion_open(void) {
    return 0;
}

int exynos_ion_close(int /* fd */) {
    return 0;
}

BufferAllocator& exynos_ion_get_allocator(void) {
    static BufferAllocator bufallocator;

    return bufallocator;
}

int exynos_ion_alloc(int /* ion_fd */, size_t len, unsigned int heap_mask, unsigned int flags) {
    unsigned int heapflags = flags & (ION_FLAG_PROTECTED | ION_FLAG_CACHED);

    auto& bufallocator = exynos_ion_get_allocator();

    for (const auto& it : heap_map_table) {
        if ((heap_mask == it.legacy_ion_heap_mask) && (heapflags == it.ion_heap_flags)) {
            int ret = bufallocator.Alloc(it.heap_name, len, flags);
            if (ret < 0)
                ALOGE("Failed to alloc %s, %zu %x (%d)", it.heap_name.c_str(), len, flags, ret);

            return ret;
        }
    }
    ALOGE("%s: unable to find heaps of heap_mask %#x", __func__, heap_mask);
    return -EINVAL;
}

int exynos_ion_import_handle(int /* ion_fd */, int fd, int* handle) {
    assert(handle != NULL);

    *handle = fd;
    return 0;
}

int exynos_ion_free_handle(int /* ion_fd */, int /* handle */) {
    return 0;
}

int exynos_ion_sync_fd(int __unused ion_fd, int fd) {
    auto& bufallocator = exynos_ion_get_allocator();

    return bufallocator.CpuSyncStart(fd, kSyncReadWrite);
}

int exynos_ion_sync_start(int __unused ion_fd, int fd, int direction) {
    auto& bufallocator = exynos_ion_get_allocator();

    return bufallocator.CpuSyncStart(fd, static_cast<SyncType>(direction));
}

int exynos_ion_sync_end(int __unused ion_fd, int fd, int direction) {
    auto& bufallocator = exynos_ion_get_allocator();

    return bufallocator.CpuSyncEnd(fd, static_cast<SyncType>(direction));
}
