// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <assert.h>
#include <fcntl.h>
#include <lib/magma/magma_common_defs.h>
#include <stdarg.h>
#include <stdint.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>
#include <virtgpu_drm.h>
#include <xf86drm.h>

#include <limits>
#include <mutex>
#include <thread>
#include <unordered_map>

#include "VirtioGpuAddressSpaceStream.h"
#include "EncoderDebug.h"
#include "magma_enc.h"

static uint64_t get_ns_monotonic(bool raw) {
    struct timespec time;
    int ret = clock_gettime(raw ? CLOCK_MONOTONIC_RAW : CLOCK_MONOTONIC, &time);
    if (ret < 0) return 0;
    return static_cast<uint64_t>(time.tv_sec) * 1000000000ULL + time.tv_nsec;
}

class MagmaClientContext : public magma_encoder_context_t {
   public:
    MagmaClientContext(AddressSpaceStream* stream);

    AddressSpaceStream* stream() {
        return reinterpret_cast<AddressSpaceStream*>(magma_encoder_context_t::m_stream);
    }

    magma_status_t get_fd_for_buffer(magma_buffer_t buffer, int* fd_out);

    std::mutex& mutex() { return m_mutex_; }

    static magma_status_t magma_device_import(void* self, magma_handle_t device_channel,
                                              magma_device_t* device_out);
    static magma_status_t magma_device_query(void* self, magma_device_t device, uint64_t id,
                                             magma_handle_t* handle_out, uint64_t* value_out);
    static magma_status_t magma_buffer_get_handle(void* self, magma_buffer_t buffer,
                                                  magma_handle_t* handle_out);
    static magma_status_t magma_buffer_export(void* self, magma_buffer_t buffer,
                                              magma_handle_t* handle_out);
    static magma_status_t magma_poll(void* self, magma_poll_item_t* items, uint32_t count,
                                     uint64_t timeout_ns);
    static magma_status_t magma_connection_create_buffer(void* self, magma_connection_t connection,
                                                         uint64_t size, uint64_t* size_out,
                                                         magma_buffer_t* buffer_out,
                                                         magma_buffer_id_t* id_out);
    static void magma_connection_release_buffer(void* self, magma_connection_t connection,
                                                magma_buffer_t buffer);

    static void set_thread_local_context_lock(std::unique_lock<std::mutex>* lock) { t_lock = lock; }

    static std::unique_lock<std::mutex>* get_thread_local_context_lock() { return t_lock; }

    magma_device_import_client_proc_t magma_device_import_enc_;
    magma_buffer_get_handle_client_proc_t magma_buffer_get_handle_enc_;
    magma_poll_client_proc_t magma_poll_enc_;
    magma_connection_create_buffer_client_proc_t magma_connection_create_buffer_enc_;
    magma_connection_release_buffer_client_proc_t magma_connection_release_buffer_enc_;

    int render_node_fd_;

    // Stores buffer info upon creation.
    struct BufferInfo {
        magma_connection_t connection;  // Owning connection.
        uint64_t size;                  // Actual size.
        magma_buffer_id_t id;           // Id.
    };
    std::unordered_map<magma_buffer_t, BufferInfo> buffer_info_;

    std::mutex m_mutex_;
    static thread_local std::unique_lock<std::mutex>* t_lock;
};

// This makes the mutex lock available to decoding methods that can take time
// (eg magma_poll), to prevent one thread from locking out others.
class ContextLock {
   public:
    ContextLock(MagmaClientContext* context) : m_context_(context), m_lock_(context->mutex()) {
        m_context_->set_thread_local_context_lock(&m_lock_);
    }

    ~ContextLock() { m_context_->set_thread_local_context_lock(nullptr); }

   private:
    MagmaClientContext* m_context_;
    std::unique_lock<std::mutex> m_lock_;
};

// static
thread_local std::unique_lock<std::mutex>* MagmaClientContext::t_lock;

MagmaClientContext::MagmaClientContext(AddressSpaceStream* stream)
    : magma_encoder_context_t(stream, new gfxstream::guest::ChecksumCalculator) {
    magma_device_import_enc_ = magma_client_context_t::magma_device_import;
    magma_buffer_get_handle_enc_ = magma_client_context_t::magma_buffer_get_handle;
    magma_poll_enc_ = magma_client_context_t::magma_poll;
    magma_connection_create_buffer_enc_ = magma_client_context_t::magma_connection_create_buffer;

    magma_client_context_t::magma_device_import = &MagmaClientContext::magma_device_import;
    magma_client_context_t::magma_device_query = &MagmaClientContext::magma_device_query;
    magma_client_context_t::magma_buffer_get_handle = &MagmaClientContext::magma_buffer_get_handle;
    magma_client_context_t::magma_buffer_export = &MagmaClientContext::magma_buffer_export;
    magma_client_context_t::magma_poll = &MagmaClientContext::magma_poll;
    magma_client_context_t::magma_connection_create_buffer =
        &MagmaClientContext::magma_connection_create_buffer;
    magma_client_context_t::magma_connection_release_buffer =
        &MagmaClientContext::magma_connection_release_buffer;
}

// static
magma_status_t MagmaClientContext::magma_device_import(void* self, magma_handle_t device_channel,
                                                       magma_device_t* device_out) {
    auto context = reinterpret_cast<MagmaClientContext*>(self);

    magma_handle_t placeholder = 0xacbd1234;  // not used

    magma_status_t status = context->magma_device_import_enc_(self, placeholder, device_out);

    // The local fd isn't needed, just close it.
    int fd = device_channel;
    close(fd);

    return status;
}

magma_status_t MagmaClientContext::get_fd_for_buffer(magma_buffer_t buffer, int* fd_out) {
    *fd_out = -1;

    auto it = buffer_info_.find(buffer);
    if (it == buffer_info_.end()) {
        ALOGE("%s: buffer (%lu) not found in map", __func__, buffer);
        return MAGMA_STATUS_INVALID_ARGS;
    }
    auto& info = it->second;

    // TODO(fxbug.dev/42073573): Evaluate deferred guest resource creation.
    auto blob = VirtGpuDevice::getInstance(VirtGpuCapset::kCapsetGfxStreamMagma)
                    ->createBlob({.size = info.size,
                                  .flags = kBlobFlagMappable | kBlobFlagShareable,
                                  .blobMem = kBlobMemHost3d,
                                  .blobId = info.id});
    if (!blob) {
        return MAGMA_STATUS_INTERNAL_ERROR;
    }

    VirtGpuExternalHandle handle{};
    int result = blob->exportBlob(handle);
    if (result != 0 || handle.osHandle < 0) {
        return MAGMA_STATUS_INTERNAL_ERROR;
    }

    *fd_out = handle.osHandle;

    return MAGMA_STATUS_OK;
}

magma_status_t MagmaClientContext::magma_device_query(void* self, magma_device_t device,
                                                      uint64_t id, magma_handle_t* handle_out,
                                                      uint64_t* value_out) {
    auto context = reinterpret_cast<MagmaClientContext*>(self);

    // TODO(b/277219980): Support guest-allocated buffers.
    constexpr magma_bool_t kHostAllocate = 1;

    uint64_t value = 0;
    uint64_t result_buffer_mapping_id = 0;
    uint64_t result_buffer_size = 0;
    magma_status_t status = context->magma_device_query_fudge(
        self, device, id, kHostAllocate, &result_buffer_mapping_id, &result_buffer_size, &value);
    if (status != MAGMA_STATUS_OK) {
        ALOGE("magma_device_query_fudge failed: %d\n", status);
        return status;
    }

    // For non-buffer queries, just return the value.
    if (result_buffer_size == 0) {
        if (!value_out) {
            ALOGE("MAGMA_STATUS_INVALID_ARGS\n");
            return MAGMA_STATUS_INVALID_ARGS;
        }
        *value_out = value;
        ALOGE("MAGMA_STATUS_OK (value = %lu)\n", value);
        return MAGMA_STATUS_OK;
    }

    // Otherwise, create and return a fd for the host-allocated buffer.
    if (!handle_out) {
        ALOGE("MAGMA_STATUS_INVALID_ARGS\n");
        return MAGMA_STATUS_INVALID_ARGS;
    }

    ALOGI("opening blob id %lu size %lu\n", result_buffer_mapping_id, result_buffer_size);
    auto blob = VirtGpuDevice::getInstance(VirtGpuCapset::kCapsetGfxStreamMagma)
                    ->createBlob({.size = result_buffer_size,
                                  .flags = kBlobFlagMappable | kBlobFlagShareable,
                                  .blobMem = kBlobMemHost3d,
                                  .blobId = result_buffer_mapping_id});
    if (!blob) {
        ALOGE("VirtGpuDevice::createBlob failed\n");
        return MAGMA_STATUS_INTERNAL_ERROR;
    }

    VirtGpuExternalHandle handle{};
    int result = blob->exportBlob(handle);
    if (result != 0 || handle.osHandle < 0) {
        ALOGE("VirtGpuBlob::exportBlob failed\n");
        return MAGMA_STATUS_INTERNAL_ERROR;
    }

    *handle_out = handle.osHandle;
    return MAGMA_STATUS_OK;
}

magma_status_t MagmaClientContext::magma_buffer_get_handle(void* self, magma_buffer_t buffer,
                                                           magma_handle_t* handle_out) {
    auto context = reinterpret_cast<MagmaClientContext*>(self);
    magma_buffer_info_t info{};
    magma_status_t status = context->magma_buffer_get_info(self, buffer, &info);
    if (status != MAGMA_STATUS_OK) return status;
    magma_handle_t mapping_id = 0;
    status = context->magma_buffer_get_handle_enc_(self, buffer, &mapping_id);
    if (status != MAGMA_STATUS_OK) return status;
    auto blob = VirtGpuDevice::getInstance(VirtGpuCapset::kCapsetGfxStreamMagma)
                    ->createBlob({.size = info.size,
                                  .flags = kBlobFlagMappable | kBlobFlagShareable,
                                  .blobMem = kBlobMemHost3d,
                                  .blobId = mapping_id});
    if (!blob) {
        return MAGMA_STATUS_INTERNAL_ERROR;
    }

    VirtGpuExternalHandle handle{};
    int result = blob->exportBlob(handle);
    if (result != 0 || handle.osHandle < 0) {
        return MAGMA_STATUS_INTERNAL_ERROR;
    }
    *handle_out = handle.osHandle;
    return MAGMA_STATUS_OK;
}

magma_status_t MagmaClientContext::magma_buffer_export(void* self, magma_buffer_t buffer,
                                                       magma_handle_t* handle_out) {
    auto context = reinterpret_cast<MagmaClientContext*>(self);

    int fd;
    magma_status_t status = context->get_fd_for_buffer(buffer, &fd);
    if (status != MAGMA_STATUS_OK) return status;

    *handle_out = fd;

    return MAGMA_STATUS_OK;
}

// We can't pass a non-zero timeout to the server, as that would block the server from handling
// requests from other threads. So we busy wait here, which isn't ideal; however if the server did
// block, gfxstream would busy wait for the response anyway.
magma_status_t MagmaClientContext::magma_poll(void* self, magma_poll_item_t* items, uint32_t count,
                                              uint64_t timeout_ns) {
    auto context = reinterpret_cast<MagmaClientContext*>(self);

    int64_t time_start = static_cast<int64_t>(get_ns_monotonic(false));

    int64_t abs_timeout_ns = time_start + timeout_ns;

    if (abs_timeout_ns < time_start) {
        abs_timeout_ns = std::numeric_limits<int64_t>::max();
    }

    bool warned_for_long_poll = false;

    while (true) {
        magma_status_t status = context->magma_poll_enc_(self, items, count, 0);

        if (status != MAGMA_STATUS_TIMED_OUT) return status;

        // Not ready, allow other threads to work in with us
        get_thread_local_context_lock()->unlock();

        std::this_thread::yield();

        int64_t time_now = static_cast<int64_t>(get_ns_monotonic(false));

        // TODO(fxb/122604): Add back-off to the busy loop, ideally based on recent sleep
        // patterns (e.g. start polling shortly before next expected burst).
        if (!warned_for_long_poll && time_now - time_start > 5000000000) {
            ALOGE("magma_poll: long poll detected (%lu us)", (time_now - time_start) / 1000);
            warned_for_long_poll = true;
        }

        if (time_now >= abs_timeout_ns) break;

        get_thread_local_context_lock()->lock();
    }

    return MAGMA_STATUS_TIMED_OUT;
}

// Magma 1.0 no longer tracks buffer size and id on behalf of the client, so we mirror it here.
magma_status_t MagmaClientContext::magma_connection_create_buffer(void* self,
                                                                  magma_connection_t connection,
                                                                  uint64_t size, uint64_t* size_out,
                                                                  magma_buffer_t* buffer_out,
                                                                  magma_buffer_id_t* id_out) {
    auto context = reinterpret_cast<MagmaClientContext*>(self);

    // TODO(b/277219980): support guest-allocated buffers
    magma_status_t status = context->magma_connection_create_buffer_enc_(
        self, connection, size, size_out, buffer_out, id_out);
    if (status != MAGMA_STATUS_OK) return status;

    auto [_, inserted] = context->buffer_info_.emplace(
        *buffer_out, BufferInfo{.connection = connection, .size = *size_out, .id = *id_out});
    if (!inserted) {
        ALOGE("magma_connection_create_buffer: duplicate entry in buffer info map");
        return MAGMA_STATUS_INTERNAL_ERROR;
    }

    return MAGMA_STATUS_OK;
}

void MagmaClientContext::magma_connection_release_buffer(void* self, magma_connection_t connection,
                                                         magma_buffer_t buffer) {
    auto context = reinterpret_cast<MagmaClientContext*>(self);

    context->magma_connection_release_buffer_enc_(self, connection, buffer);

    // Invalid buffer or connection is treated as no-op by magma, so only log as verbose.
    auto it = context->buffer_info_.find(buffer);
    if (it == context->buffer_info_.end()) {
        ALOGV("magma_connection_release_buffer: buffer (%lu) not found in map", buffer);
        return;
    }
    if (it->second.connection != connection) {
        ALOGV(
            "magma_connection_release_buffer: buffer (%lu) attempted release using wrong "
            "connection (expected %lu, received %lu)",
            buffer, it->second.connection, connection);
        return;
    }
    context->buffer_info_.erase(it);
}

template <typename T, typename U>
static T SafeCast(const U& value) {
    if (value > std::numeric_limits<T>::max() || value < std::numeric_limits<T>::min()) {
        abort();
    }
    return static_cast<T>(value);
}

// We have a singleton client context for all threads.  We want all client
// threads served by a single server RenderThread.
MagmaClientContext* GetMagmaContext() {
    static MagmaClientContext* s_context;
    static std::once_flag once_flag;

    std::call_once(once_flag, []() {
        auto stream = createVirtioGpuAddressSpaceStream(kCapsetGfxStreamMagma, nullptr);
        assert(stream);

        // RenderThread expects flags: send zero 'clientFlags' to the host.
        {
            auto pClientFlags =
                reinterpret_cast<unsigned int*>(stream->allocBuffer(sizeof(unsigned int)));
            *pClientFlags = 0;
            stream->commitBuffer(sizeof(unsigned int));
        }

        s_context = new MagmaClientContext(stream);
        auto render_node_fd =
            VirtGpuDevice::getInstance(VirtGpuCapset::kCapsetGfxStreamMagma)->getDeviceHandle();
        s_context->render_node_fd_ = SafeCast<int>(render_node_fd);

        ALOGE("Created new context\n");
        fflush(stdout);
    });

    return s_context;
}

// Used in magma_entry.cpp
// Always lock around the encoding methods because we have a singleton context.
#define GET_CONTEXT                              \
    MagmaClientContext* ctx = GetMagmaContext(); \
    ContextLock lock(ctx)

#include "magma_entry.cpp"
