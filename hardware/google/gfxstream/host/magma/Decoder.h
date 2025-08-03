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

#pragma once

#include <memory>

#include "magma_dec.h"

namespace gfxstream {
namespace magma {

// Generic magma decoder.
class Decoder : public magma_decoder_context_t {
   public:
    static std::unique_ptr<Decoder> create(uint32_t context_id);

   protected:
    Decoder();

   private:
    // These methods map directly to the magma client API.
    // clang-format off
    virtual magma_status_t magma_device_import(magma_handle_t device_channel, magma_device_t* device_out);
    virtual void magma_device_release(magma_device_t device);
    virtual magma_status_t magma_device_query(magma_device_t device, uint64_t id, magma_handle_t* result_buffer_out, uint64_t* result_out);
    virtual magma_status_t magma_device_query_fudge(magma_device_t device, uint64_t id, magma_bool_t host_allocate, uint64_t* result_buffer_mapping_id_inout, uint64_t* result_buffer_size_inout, uint64_t* result_out);
    virtual magma_status_t magma_device_create_connection(magma_device_t device, magma_connection_t* connection_out);
    virtual void magma_connection_release(magma_connection_t connection);
    virtual magma_status_t magma_connection_get_error(magma_connection_t connection);
    virtual magma_status_t magma_connection_create_context(magma_connection_t connection, uint32_t* context_id_out);
    virtual void magma_connection_release_context(magma_connection_t connection, uint32_t context_id);
    virtual magma_status_t magma_connection_create_buffer(magma_connection_t connection, uint64_t size, uint64_t* size_out, magma_buffer_t* buffer_out, magma_buffer_id_t* id_out);
    virtual void magma_connection_release_buffer(magma_connection_t connection, magma_buffer_t buffer);
    virtual magma_status_t magma_connection_import_buffer(magma_connection_t connection, magma_handle_t buffer_handle, uint64_t* size_out, magma_buffer_t* buffer_out, magma_buffer_id_t* id_out);
    virtual magma_status_t magma_connection_create_semaphore(magma_connection_t magma_connection, magma_semaphore_t* semaphore_out, magma_semaphore_id_t* id_out);
    virtual void magma_connection_release_semaphore(magma_connection_t connection, magma_semaphore_t semaphore);
    virtual magma_status_t magma_connection_import_semaphore(magma_connection_t connection, magma_handle_t semaphore_handle, magma_semaphore_t* semaphore_out, magma_semaphore_id_t* id_out);
    virtual magma_status_t magma_connection_perform_buffer_op(magma_connection_t connection, magma_buffer_t buffer, uint32_t options, uint64_t start_offset, uint64_t length);
    virtual magma_status_t magma_connection_map_buffer(magma_connection_t connection, uint64_t hw_va, magma_buffer_t buffer, uint64_t offset, uint64_t length, uint64_t map_flags);
    virtual void magma_connection_unmap_buffer(magma_connection_t connection, uint64_t hw_va, magma_buffer_t buffer);
    virtual magma_status_t magma_connection_execute_command(magma_connection_t connection, uint32_t context_id, magma_command_descriptor_t* descriptor);
    virtual magma_status_t magma_connection_execute_immediate_commands(magma_connection_t connection, uint32_t context_id, uint64_t command_count, magma_inline_command_buffer_t* command_buffers);
    virtual magma_status_t magma_connection_flush(magma_connection_t connection);
    virtual magma_handle_t magma_connection_get_notification_channel_handle(magma_connection_t connection);
    virtual magma_status_t magma_connection_read_notification_channel(magma_connection_t connection, void* buffer, uint64_t buffer_size, uint64_t* buffer_size_out, magma_bool_t* more_data_out);
    virtual magma_status_t magma_buffer_clean_cache(magma_buffer_t buffer, uint64_t offset, uint64_t size, magma_cache_operation_t operation);
    virtual magma_status_t magma_buffer_set_cache_policy(magma_buffer_t buffer, magma_cache_policy_t policy);
    virtual magma_status_t magma_buffer_get_cache_policy(magma_buffer_t buffer, magma_cache_policy_t* cache_policy_out);
    virtual magma_status_t magma_buffer_set_name(magma_buffer_t buffer, const char* name);
    virtual magma_status_t magma_buffer_get_info(magma_buffer_t buffer, magma_buffer_info_t* info_out);
    virtual magma_status_t magma_buffer_get_handle(magma_buffer_t buffer, magma_handle_t* handle_out);
    virtual magma_status_t magma_buffer_export(magma_buffer_t buffer, magma_handle_t* buffer_handle_out);
    virtual void magma_semaphore_signal(magma_semaphore_t semaphore);
    virtual void magma_semaphore_reset(magma_semaphore_t semaphore);
    virtual magma_status_t magma_semaphore_export(magma_semaphore_t semaphore, magma_handle_t* semaphore_handle_out);
    virtual magma_status_t magma_poll(magma_poll_item_t* items, uint32_t count, uint64_t timeout_ns);
    virtual magma_status_t magma_initialize_tracing(magma_handle_t channel);
    virtual magma_status_t magma_initialize_logging(magma_handle_t channel);
    virtual magma_status_t magma_connection_enable_performance_counter_access(magma_connection_t connection, magma_handle_t channel);
    virtual magma_status_t magma_connection_enable_performance_counters(magma_connection_t connection, uint64_t* counters, uint64_t counters_count);
    virtual magma_status_t magma_connection_create_performance_counter_buffer_pool(magma_connection_t connection, magma_perf_count_pool_t* pool_id_out, magma_handle_t* notification_handle_out);
    virtual magma_status_t magma_connection_release_performance_counter_buffer_pool(magma_connection_t connection, magma_perf_count_pool_t pool_id);
    virtual magma_status_t magma_connection_add_performance_counter_buffer_offsets_to_pool(magma_connection_t connection, magma_perf_count_pool_t pool_id, const magma_buffer_offset_t* offsets, uint64_t offsets_count);
    virtual magma_status_t magma_connection_remove_performance_counter_buffer_from_pool(magma_connection_t connection, magma_perf_count_pool_t pool_id, magma_buffer_t buffer);
    virtual magma_status_t magma_connection_dump_performance_counters(magma_connection_t connection, magma_perf_count_pool_t pool_id, uint32_t trigger_id);
    virtual magma_status_t magma_connection_clear_performance_counters(magma_connection_t connection, uint64_t* counters, uint64_t counters_count);
    virtual magma_status_t magma_connection_read_performance_counter_completion(magma_connection_t connection, magma_perf_count_pool_t pool_id, uint32_t* trigger_id_out, uint64_t* buffer_id_out, uint32_t* buffer_offset_out, uint64_t* time_out, uint32_t* result_flags_out);
    virtual magma_status_t magma_virt_connection_create_image(magma_connection_t connection, magma_image_create_info_t* create_info, uint64_t* size_out, magma_buffer_t* image_out, magma_buffer_id_t* buffer_id_out);
    virtual magma_status_t magma_virt_connection_get_image_info(magma_connection_t connection, magma_buffer_t image, magma_image_info_t* image_info_out);
    // clang-format on

    // These are "fudged" methods that alter the signature of a standard magma API, either to
    // provide additional information necessary for efficient proxying, or to work around
    // limitations in emugen.

    // `descriptor` contains the flattened descriptor.
    virtual magma_status_t magma_connection_execute_command_fudge(magma_connection_t connection,
                                                                  uint32_t context_id,
                                                                  void* descriptor,
                                                                  uint64_t descriptor_size);

    // `command_buffers` contains the flattened list of command buffers, with
    // `command_buffer_offsets` containing a list of offsets into `command_buffers` that define the
    // start of each flattened command buffer.
    virtual magma_status_t magma_connection_execute_immediate_commands_fudge(
        magma_connection_t connection, uint32_t context_id, uint64_t command_count,
        void* command_buffers, uint64_t command_buffers_size, uint64_t* command_buffer_offsets);

    // `name` contains a null-terminated string. `name_size` is the size of the string including its
    // null terminator.
    virtual magma_status_t magma_buffer_set_name_fudge(magma_buffer_t buffer, void* name,
                                                       uint64_t name_size);
};

}  // namespace magma
}  // namespace gfxstream
