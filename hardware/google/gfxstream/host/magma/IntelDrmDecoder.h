// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License") override;
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

#pragma once

#include "Decoder.h"
#include "MonotonicMap.h"
#include "DrmDevice.h"
#include "Connection.h"

namespace gfxstream {
namespace magma {

// Magma decoder for running on an Intel DRM backend.
class IntelDrmDecoder : public Decoder {
  public:
    static std::unique_ptr<IntelDrmDecoder> create(uint32_t context_id);

  private:
    IntelDrmDecoder();
    magma_status_t magma_device_import(magma_handle_t device_channel, magma_device_t* device_out) override;
    void magma_device_release(magma_device_t device) override;
    magma_status_t magma_device_query_fudge(magma_device_t device, uint64_t id, magma_bool_t host_allocate, uint64_t* result_buffer_mapping_id_inout, uint64_t* result_buffer_size_inout, uint64_t* result_out) override;
    magma_status_t magma_device_create_connection(magma_device_t device, magma_connection_t* connection_out) override;
    void magma_connection_release(magma_connection_t connection) override;
    magma_status_t magma_connection_create_buffer(magma_connection_t connection, uint64_t size, uint64_t* size_out, magma_buffer_t* buffer_out, magma_buffer_id_t* id_out) override;
    void magma_connection_release_buffer(magma_connection_t connection, magma_buffer_t buffer) override;
    magma_status_t magma_connection_create_semaphore(magma_connection_t magma_connection, magma_semaphore_t* semaphore_out, magma_semaphore_id_t* id_out) override;
    void magma_connection_release_semaphore(magma_connection_t connection, magma_semaphore_t semaphore) override;
    magma_status_t magma_buffer_get_info(magma_buffer_t buffer, magma_buffer_info_t* info_out) override;
    magma_status_t magma_buffer_get_handle(magma_buffer_t buffer, magma_handle_t* handle_out) override;
    magma_status_t magma_buffer_export(magma_buffer_t buffer, magma_handle_t* buffer_handle_out) override;
    void magma_semaphore_signal(magma_semaphore_t semaphore) override;
    void magma_semaphore_reset(magma_semaphore_t semaphore) override;
    magma_status_t magma_poll(magma_poll_item_t* items, uint32_t count, uint64_t timeout_ns) override;
    magma_status_t magma_connection_get_error(magma_connection_t connection) override;
    magma_status_t magma_connection_create_context(magma_connection_t connection, uint32_t* context_id_out) override;
    void magma_connection_release_context(magma_connection_t connection, uint32_t context_id) override;
    magma_status_t magma_connection_map_buffer(magma_connection_t connection, uint64_t hw_va, magma_buffer_t buffer, uint64_t offset, uint64_t length, uint64_t map_flags) override;
    void magma_connection_unmap_buffer(magma_connection_t connection, uint64_t hw_va, magma_buffer_t buffer) override;

    uint32_t mContextId;
    MonotonicMap<magma_device_t, DrmDevice> mDevices;
    MonotonicMap<magma_buffer_t, DrmBuffer> mBuffers;
    MonotonicMap<magma_connection_t, Connection> mConnections;

    // Maps GEM handles to Buffers.
    std::unordered_map<uint32_t, magma_buffer_t> mGemHandleToBuffer;
};

} // namespace magma
} // namespace gfxstream
