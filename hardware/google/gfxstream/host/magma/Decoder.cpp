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

#include "host/magma/Decoder.h"

#include "RenderThreadInfoMagma.h"

#if GFXSTREAM_MAGMA_USE_INTEL_DRM
#include "host/magma/IntelDrmDecoder.h"
#endif

namespace gfxstream {
namespace magma {

std::unique_ptr<Decoder> Decoder::create(uint32_t context_id) {
#if GFXSTREAM_MAGMA_USE_INTEL_DRM
    return IntelDrmDecoder::create(context_id);
#endif
    return nullptr;
}

#define MAGMA_DECODER_BIND_METHOD(method)                               \
    magma_server_context_t::method = [](auto... args) {                 \
        auto decoder = RenderThreadInfoMagma::get() -> mMagmaDec.get(); \
        return static_cast<decltype(this)>(decoder)->method(args...);   \
    }

Decoder::Decoder() {
    // Bind standard methods.
    MAGMA_DECODER_BIND_METHOD(magma_device_import);
    MAGMA_DECODER_BIND_METHOD(magma_device_release);
    MAGMA_DECODER_BIND_METHOD(magma_device_query);
    MAGMA_DECODER_BIND_METHOD(magma_device_create_connection);
    MAGMA_DECODER_BIND_METHOD(magma_connection_release);
    MAGMA_DECODER_BIND_METHOD(magma_connection_get_error);
    MAGMA_DECODER_BIND_METHOD(magma_connection_create_context);
    MAGMA_DECODER_BIND_METHOD(magma_connection_release_context);
    MAGMA_DECODER_BIND_METHOD(magma_connection_create_buffer);
    MAGMA_DECODER_BIND_METHOD(magma_connection_release_buffer);
    MAGMA_DECODER_BIND_METHOD(magma_connection_import_buffer);
    MAGMA_DECODER_BIND_METHOD(magma_connection_create_semaphore);
    MAGMA_DECODER_BIND_METHOD(magma_connection_release_semaphore);
    MAGMA_DECODER_BIND_METHOD(magma_connection_import_semaphore);
    MAGMA_DECODER_BIND_METHOD(magma_connection_perform_buffer_op);
    MAGMA_DECODER_BIND_METHOD(magma_connection_map_buffer);
    MAGMA_DECODER_BIND_METHOD(magma_connection_unmap_buffer);
    MAGMA_DECODER_BIND_METHOD(magma_connection_execute_command);
    MAGMA_DECODER_BIND_METHOD(magma_connection_execute_immediate_commands);
    MAGMA_DECODER_BIND_METHOD(magma_connection_flush);
    MAGMA_DECODER_BIND_METHOD(magma_connection_get_notification_channel_handle);
    MAGMA_DECODER_BIND_METHOD(magma_connection_read_notification_channel);
    MAGMA_DECODER_BIND_METHOD(magma_buffer_clean_cache);
    MAGMA_DECODER_BIND_METHOD(magma_buffer_set_cache_policy);
    MAGMA_DECODER_BIND_METHOD(magma_buffer_get_cache_policy);
    MAGMA_DECODER_BIND_METHOD(magma_buffer_set_name);
    MAGMA_DECODER_BIND_METHOD(magma_buffer_get_info);
    MAGMA_DECODER_BIND_METHOD(magma_buffer_get_handle);
    MAGMA_DECODER_BIND_METHOD(magma_buffer_export);
    MAGMA_DECODER_BIND_METHOD(magma_semaphore_signal);
    MAGMA_DECODER_BIND_METHOD(magma_semaphore_reset);
    MAGMA_DECODER_BIND_METHOD(magma_semaphore_export);
    MAGMA_DECODER_BIND_METHOD(magma_poll);
    MAGMA_DECODER_BIND_METHOD(magma_initialize_tracing);
    MAGMA_DECODER_BIND_METHOD(magma_initialize_logging);
    MAGMA_DECODER_BIND_METHOD(magma_connection_enable_performance_counter_access);
    MAGMA_DECODER_BIND_METHOD(magma_connection_enable_performance_counters);
    MAGMA_DECODER_BIND_METHOD(magma_connection_create_performance_counter_buffer_pool);
    MAGMA_DECODER_BIND_METHOD(magma_connection_release_performance_counter_buffer_pool);
    MAGMA_DECODER_BIND_METHOD(magma_connection_add_performance_counter_buffer_offsets_to_pool);
    MAGMA_DECODER_BIND_METHOD(magma_connection_remove_performance_counter_buffer_from_pool);
    MAGMA_DECODER_BIND_METHOD(magma_connection_dump_performance_counters);
    MAGMA_DECODER_BIND_METHOD(magma_connection_clear_performance_counters);
    MAGMA_DECODER_BIND_METHOD(magma_connection_read_performance_counter_completion);
    MAGMA_DECODER_BIND_METHOD(magma_virt_connection_create_image);
    MAGMA_DECODER_BIND_METHOD(magma_virt_connection_get_image_info);

    // Bind fudged methods.
    MAGMA_DECODER_BIND_METHOD(magma_device_query_fudge);
    MAGMA_DECODER_BIND_METHOD(magma_connection_execute_command_fudge);
    MAGMA_DECODER_BIND_METHOD(magma_connection_execute_immediate_commands_fudge);
    MAGMA_DECODER_BIND_METHOD(magma_buffer_set_name_fudge);
}

#define MAGMA_NOTIMPL() WARN("magma::Decoder method not implemented: %s", __FUNCTION__)

magma_status_t Decoder::magma_device_import(magma_handle_t device_channel,
                                            magma_device_t* device_out) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

void Decoder::magma_device_release(magma_device_t device) { MAGMA_NOTIMPL(); }

magma_status_t Decoder::magma_device_query(magma_device_t device, uint64_t id,
                                           magma_handle_t* result_buffer_out,
                                           uint64_t* result_out) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_device_create_connection(magma_device_t device,
                                                       magma_connection_t* connection_out) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

void Decoder::magma_connection_release(magma_connection_t connection) { MAGMA_NOTIMPL(); }

magma_status_t Decoder::magma_connection_get_error(magma_connection_t connection) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_connection_create_context(magma_connection_t connection,
                                                        uint32_t* context_id_out) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

void Decoder::magma_connection_release_context(magma_connection_t connection, uint32_t context_id) {
    MAGMA_NOTIMPL();
}

magma_status_t Decoder::magma_connection_create_buffer(magma_connection_t connection, uint64_t size,
                                                       uint64_t* size_out,
                                                       magma_buffer_t* buffer_out,
                                                       magma_buffer_id_t* id_out) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

void Decoder::magma_connection_release_buffer(magma_connection_t connection,
                                              magma_buffer_t buffer) {
    MAGMA_NOTIMPL();
}

magma_status_t Decoder::magma_connection_import_buffer(magma_connection_t connection,
                                                       magma_handle_t buffer_handle,
                                                       uint64_t* size_out,
                                                       magma_buffer_t* buffer_out,
                                                       magma_buffer_id_t* id_out) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_connection_create_semaphore(magma_connection_t magma_connection,
                                                          magma_semaphore_t* semaphore_out,
                                                          magma_semaphore_id_t* id_out) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

void Decoder::magma_connection_release_semaphore(magma_connection_t connection,
                                                 magma_semaphore_t semaphore) {
    MAGMA_NOTIMPL();
}

magma_status_t Decoder::magma_connection_import_semaphore(magma_connection_t connection,
                                                          magma_handle_t semaphore_handle,
                                                          magma_semaphore_t* semaphore_out,
                                                          magma_semaphore_id_t* id_out) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_connection_perform_buffer_op(magma_connection_t connection,
                                                           magma_buffer_t buffer, uint32_t options,
                                                           uint64_t start_offset, uint64_t length) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_connection_map_buffer(magma_connection_t connection, uint64_t hw_va,
                                                    magma_buffer_t buffer, uint64_t offset,
                                                    uint64_t length, uint64_t map_flags) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

void Decoder::magma_connection_unmap_buffer(magma_connection_t connection, uint64_t hw_va,
                                            magma_buffer_t buffer) {
    MAGMA_NOTIMPL();
}

magma_status_t Decoder::magma_connection_execute_command(magma_connection_t connection,
                                                         uint32_t context_id,
                                                         magma_command_descriptor_t* descriptor) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_connection_execute_immediate_commands(
    magma_connection_t connection, uint32_t context_id, uint64_t command_count,
    magma_inline_command_buffer_t* command_buffers) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_connection_flush(magma_connection_t connection) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_handle_t Decoder::magma_connection_get_notification_channel_handle(
    magma_connection_t connection) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_connection_read_notification_channel(magma_connection_t connection,
                                                                   void* buffer,
                                                                   uint64_t buffer_size,
                                                                   uint64_t* buffer_size_out,
                                                                   magma_bool_t* more_data_out) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_buffer_clean_cache(magma_buffer_t buffer, uint64_t offset,
                                                 uint64_t size, magma_cache_operation_t operation) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_buffer_set_cache_policy(magma_buffer_t buffer,
                                                      magma_cache_policy_t policy) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_buffer_get_cache_policy(magma_buffer_t buffer,
                                                      magma_cache_policy_t* cache_policy_out) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_buffer_set_name(magma_buffer_t buffer, const char* name) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_buffer_get_info(magma_buffer_t buffer,
                                              magma_buffer_info_t* info_out) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_buffer_get_handle(magma_buffer_t buffer, magma_handle_t* handle_out) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_buffer_export(magma_buffer_t buffer,
                                            magma_handle_t* buffer_handle_out) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

void Decoder::magma_semaphore_signal(magma_semaphore_t semaphore) { MAGMA_NOTIMPL(); }

void Decoder::magma_semaphore_reset(magma_semaphore_t semaphore) { MAGMA_NOTIMPL(); }

magma_status_t Decoder::magma_semaphore_export(magma_semaphore_t semaphore,
                                               magma_handle_t* semaphore_handle_out) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_poll(magma_poll_item_t* items, uint32_t count, uint64_t timeout_ns) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_initialize_tracing(magma_handle_t channel) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_initialize_logging(magma_handle_t channel) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_connection_enable_performance_counter_access(
    magma_connection_t connection, magma_handle_t channel) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_connection_enable_performance_counters(magma_connection_t connection,
                                                                     uint64_t* counters,
                                                                     uint64_t counters_count) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_connection_create_performance_counter_buffer_pool(
    magma_connection_t connection, magma_perf_count_pool_t* pool_id_out,
    magma_handle_t* notification_handle_out) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_connection_release_performance_counter_buffer_pool(
    magma_connection_t connection, magma_perf_count_pool_t pool_id) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_connection_add_performance_counter_buffer_offsets_to_pool(
    magma_connection_t connection, magma_perf_count_pool_t pool_id,
    const magma_buffer_offset_t* offsets, uint64_t offsets_count) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_connection_remove_performance_counter_buffer_from_pool(
    magma_connection_t connection, magma_perf_count_pool_t pool_id, magma_buffer_t buffer) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_connection_dump_performance_counters(magma_connection_t connection,
                                                                   magma_perf_count_pool_t pool_id,
                                                                   uint32_t trigger_id) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_connection_clear_performance_counters(magma_connection_t connection,
                                                                    uint64_t* counters,
                                                                    uint64_t counters_count) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_connection_read_performance_counter_completion(
    magma_connection_t connection, magma_perf_count_pool_t pool_id, uint32_t* trigger_id_out,
    uint64_t* buffer_id_out, uint32_t* buffer_offset_out, uint64_t* time_out,
    uint32_t* result_flags_out) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_virt_connection_create_image(magma_connection_t connection,
                                                           magma_image_create_info_t* create_info,
                                                           uint64_t* size_out,
                                                           magma_buffer_t* image_out,
                                                           magma_buffer_id_t* buffer_id_out) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_virt_connection_get_image_info(magma_connection_t connection,
                                                             magma_buffer_t image,
                                                             magma_image_info_t* image_info_out) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_device_query_fudge(magma_device_t device, uint64_t id,
                                                 magma_bool_t host_allocate,
                                                 uint64_t* result_buffer_mapping_id_inout,
                                                 uint64_t* result_buffer_size_inout,
                                                 uint64_t* result_out) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_connection_execute_command_fudge(magma_connection_t connection,
                                                               uint32_t context_id,
                                                               void* descriptor,
                                                               uint64_t descriptor_size) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_connection_execute_immediate_commands_fudge(
    magma_connection_t connection, uint32_t context_id, uint64_t command_count,
    void* command_buffers, uint64_t command_buffers_size, uint64_t* command_buffer_offsets) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

magma_status_t Decoder::magma_buffer_set_name_fudge(magma_buffer_t buffer, void* name,
                                                    uint64_t name_size) {
    MAGMA_NOTIMPL();
    return MAGMA_STATUS_UNIMPLEMENTED;
}

}  // namespace magma
}  // namespace gfxstream
