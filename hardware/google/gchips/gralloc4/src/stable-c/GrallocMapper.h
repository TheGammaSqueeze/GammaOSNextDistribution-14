/*
 * Copyright (C) 2020 Arm Limited. All rights reserved.
 *
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
#ifndef ANDROID_HARDWARE_GRAPHICS_MAPPER_V4_x_GRALLOC_MAPPER_H
#define ANDROID_HARDWARE_GRAPHICS_MAPPER_V4_x_GRALLOC_MAPPER_H

#include <android/hardware/graphics/mapper/IMapper.h>
#include <android/hardware/graphics/mapper/utils/IMapperMetadataTypes.h>
#include <android/hardware/graphics/mapper/utils/IMapperProvider.h>

#include "hidl_common/Mapper.h"

namespace arm {
namespace mapper {

class GrallocMapper final : public vendor::mapper::IMapperV5Impl {
public:
    GrallocMapper() = default;

    ~GrallocMapper() = default;

    AIMapper_Error importBuffer(const native_handle_t* _Nonnull handle,
                                buffer_handle_t _Nullable* _Nonnull outBufferHandle) override;

    AIMapper_Error freeBuffer(buffer_handle_t _Nonnull buffer) override;

    AIMapper_Error getTransportSize(buffer_handle_t _Nonnull buffer, uint32_t* _Nonnull outNumFds,
                                    uint32_t* _Nonnull outNumInts) override;

    AIMapper_Error lock(buffer_handle_t _Nonnull buffer, uint64_t cpuUsage, ARect accessRegion,
                        int acquireFence, void* _Nullable* _Nonnull outData) override;

    AIMapper_Error unlock(buffer_handle_t _Nonnull buffer, int* _Nonnull releaseFence) override;

    AIMapper_Error flushLockedBuffer(buffer_handle_t _Nonnull buffer) override;

    AIMapper_Error rereadLockedBuffer(buffer_handle_t _Nonnull buffer) override;

    int32_t getMetadata(buffer_handle_t _Nonnull buffer, AIMapper_MetadataType metadataType,
                        void* _Nonnull outData, size_t outDataSize) override;

    int32_t getStandardMetadata(buffer_handle_t _Nonnull buffer, int64_t standardMetadataType,
                                void* _Nonnull outData, size_t outDataSize) override;

    AIMapper_Error setMetadata(buffer_handle_t _Nonnull buffer, AIMapper_MetadataType metadataType,
                               const void* _Nonnull metadata, size_t metadataSize) override;

    AIMapper_Error setStandardMetadata(buffer_handle_t _Nonnull buffer,
                                       int64_t standardMetadataType, const void* _Nonnull metadata,
                                       size_t metadataSize) override;

    AIMapper_Error listSupportedMetadataTypes(
            const AIMapper_MetadataTypeDescription* _Nullable* _Nonnull outDescriptionList,
            size_t* _Nonnull outNumberOfDescriptions) override;

    AIMapper_Error dumpBuffer(buffer_handle_t _Nonnull bufferHandle,
                              AIMapper_DumpBufferCallback _Nonnull dumpBufferCallback,
                              void* _Null_unspecified context) override;

    AIMapper_Error dumpAllBuffers(AIMapper_BeginDumpBufferCallback _Nonnull beginDumpBufferCallback,
                                  AIMapper_DumpBufferCallback _Nonnull dumpBufferCallback,
                                  void* _Null_unspecified context) override;

    AIMapper_Error getReservedRegion(buffer_handle_t _Nonnull buffer,
                                     void* _Nullable* _Nonnull outReservedRegion,
                                     uint64_t* _Nonnull outReservedSize) override;
};

} // namespace mapper
} // namespace arm

#endif // ANDROID_HARDWARE_GRAPHICS_MAPPER_V4_x_GRALLOC_MAPPER_H
