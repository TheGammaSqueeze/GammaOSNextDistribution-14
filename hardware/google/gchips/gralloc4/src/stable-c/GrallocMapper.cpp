/*
 * Copyright (C) 2020 Arm Limited. All rights reserved.
 *
 * Copyright 2016 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "GrallocMapper.h"

#include <cutils/native_handle.h>
#include <pixel-gralloc/mapper.h>
#include <pixel-gralloc/metadata.h>
#include <pixel-gralloc/utils.h>

#include "allocator/mali_gralloc_ion.h"
#include "core/format_info.h"
#include "drmutils.h"
#include "hidl_common/BufferDescriptor.h"
#include "hidl_common/MapperMetadata.h"
#include "hidl_common/SharedMetadata.h"
#include "hidl_common/hidl_common.h"

namespace arm {
namespace mapper {

using namespace android::hardware::graphics::mapper;
using PixelMetadataType = ::pixel::graphics::MetadataType;


AIMapper_Error GrallocMapper::importBuffer(const native_handle_t* _Nonnull handle,
                                           buffer_handle_t _Nullable* _Nonnull outBufferHandle) {
    if (handle == nullptr) return AIMapper_Error::AIMAPPER_ERROR_BAD_BUFFER;
    AIMapper_Error err = static_cast<AIMapper_Error>(common::importBuffer(handle, outBufferHandle));
    return err;
}

AIMapper_Error GrallocMapper::freeBuffer(buffer_handle_t _Nonnull buffer) {
    if (buffer == nullptr) return AIMapper_Error::AIMAPPER_ERROR_BAD_BUFFER;
    native_handle_t* bufferHandle = const_cast<native_handle_t*>(buffer);
    return static_cast<AIMapper_Error>(common::freeBuffer(bufferHandle));
}

AIMapper_Error GrallocMapper::lock(buffer_handle_t _Nonnull buffer, uint64_t cpuUsage,
                                   ARect accessRegion, int acquireFence,
                                   void* _Nullable* _Nonnull outData) {
    if (buffer == nullptr) return AIMapper_Error::AIMAPPER_ERROR_BAD_BUFFER;
    AIMapper_Error err = static_cast<AIMapper_Error>(common::lock(buffer, cpuUsage,
                                                                  common::GrallocRect(accessRegion),
                                                                  acquireFence, outData));
    return err;
}

AIMapper_Error GrallocMapper::unlock(buffer_handle_t _Nonnull buffer, int* _Nonnull releaseFence) {
    if (buffer == nullptr) return AIMapper_Error::AIMAPPER_ERROR_BAD_BUFFER;

    AIMapper_Error err = static_cast<AIMapper_Error>(common::unlock(buffer, releaseFence));
    return err;
}

AIMapper_Error GrallocMapper::flushLockedBuffer(buffer_handle_t _Nonnull buffer) {
    if (buffer == nullptr) return AIMapper_Error::AIMAPPER_ERROR_BAD_BUFFER;

    AIMapper_Error err = static_cast<AIMapper_Error>(common::flushLockedBuffer(buffer));
    return err;
}

AIMapper_Error GrallocMapper::rereadLockedBuffer(buffer_handle_t _Nonnull buffer) {
    if (buffer == nullptr) return AIMapper_Error::AIMAPPER_ERROR_BAD_BUFFER;
    return static_cast<AIMapper_Error>(common::rereadLockedBuffer(buffer));
}


// TODO(b/315854439): getStandardMetadataHelper should be in the common code.
template <typename F, StandardMetadataType metadataType>
int32_t getStandardMetadataHelper(const private_handle_t* hnd, F&& provide,
                                  StandardMetadata<metadataType>) {
    if constexpr (metadataType == StandardMetadataType::BUFFER_ID) {
        return provide(hnd->backing_store_id);
    }
    if constexpr (metadataType == StandardMetadataType::WIDTH) {
        return provide(hnd->width);
    }
    if constexpr (metadataType == StandardMetadataType::HEIGHT) {
        return provide(hnd->height);
    }
    if constexpr (metadataType == StandardMetadataType::LAYER_COUNT) {
        return provide(hnd->layer_count);
    }
    if constexpr (metadataType == StandardMetadataType::PIXEL_FORMAT_REQUESTED) {
        return provide(static_cast<PixelFormat>(hnd->req_format));
    }
    if constexpr (metadataType == StandardMetadataType::PIXEL_FORMAT_FOURCC) {
        return provide(drm_fourcc_from_handle(hnd));
    }
    if constexpr (metadataType == StandardMetadataType::PIXEL_FORMAT_MODIFIER) {
        return provide(drm_modifier_from_handle(hnd));
    }
    if constexpr (metadataType == StandardMetadataType::USAGE) {
        return provide(static_cast<BufferUsage>(hnd->consumer_usage | hnd->producer_usage));
    }
    if constexpr (metadataType == StandardMetadataType::ALLOCATION_SIZE) {
        uint64_t total_size = 0;
        for (int fidx = 0; fidx < hnd->fd_count; fidx++) {
            total_size += hnd->alloc_sizes[fidx];
        }
        return provide(total_size);
    }
    if constexpr (metadataType == StandardMetadataType::PROTECTED_CONTENT) {
        return provide((((hnd->consumer_usage | hnd->producer_usage) &
                         static_cast<uint64_t>(BufferUsage::PROTECTED)) == 0)
                               ? 0
                               : 1);
    }
    if constexpr (metadataType == StandardMetadataType::COMPRESSION) {
        ExtendableType compression = android::gralloc4::Compression_None;
        if (hnd->alloc_format & MALI_GRALLOC_INTFMT_AFBC_BASIC)
            compression = common::Compression_AFBC;
        return provide(compression);
    }
    if constexpr (metadataType == StandardMetadataType::INTERLACED) {
        return provide(android::gralloc4::Interlaced_None);
    }
    if constexpr (metadataType == StandardMetadataType::CHROMA_SITING) {
        ExtendableType siting = android::gralloc4::ChromaSiting_None;
        int format_index = get_format_index(hnd->alloc_format & MALI_GRALLOC_INTFMT_FMT_MASK);
        if (formats[format_index].is_yuv) siting = android::gralloc4::ChromaSiting_Unknown;
        return provide(siting);
    }
    if constexpr (metadataType == StandardMetadataType::PLANE_LAYOUTS) {
        std::vector<PlaneLayout> layouts;
        Error err = static_cast<Error>(common::get_plane_layouts(hnd, &layouts));
        return provide(layouts);
    }
    if constexpr (metadataType == StandardMetadataType::NAME) {
        std::string name;
        common::get_name(hnd, &name);
        return provide(name);
    }
    if constexpr (metadataType == StandardMetadataType::CROP) {
        const int num_planes = common::get_num_planes(hnd);
        std::vector<Rect> crops(num_planes);
        for (size_t plane_index = 0; plane_index < num_planes; ++plane_index) {
            Rect rect = {.top = 0,
                         .left = 0,
                         .right = static_cast<int32_t>(hnd->plane_info[plane_index].alloc_width),
                         .bottom = static_cast<int32_t>(hnd->plane_info[plane_index].alloc_height)};
            if (plane_index == 0) {
                std::optional<Rect> crop_rect;
                common::get_crop_rect(hnd, &crop_rect);
                if (crop_rect.has_value()) {
                    rect = crop_rect.value();
                } else {
                    rect = {.top = 0, .left = 0, .right = hnd->width, .bottom = hnd->height};
                }
            }
            crops[plane_index] = rect;
        }
        return provide(crops);
    }
    if constexpr (metadataType == StandardMetadataType::DATASPACE) {
        std::optional<Dataspace> dataspace;
        common::get_dataspace(hnd, &dataspace);
        return provide(dataspace.value_or(Dataspace::UNKNOWN));
    }
    if constexpr (metadataType == StandardMetadataType::BLEND_MODE) {
        std::optional<BlendMode> blendmode;
        common::get_blend_mode(hnd, &blendmode);
        return provide(blendmode.value_or(BlendMode::INVALID));
    }
    if constexpr (metadataType == StandardMetadataType::SMPTE2086) {
        std::optional<Smpte2086> smpte2086;
        common::get_smpte2086(hnd, &smpte2086);
        return provide(smpte2086);
    }
    if constexpr (metadataType == StandardMetadataType::CTA861_3) {
        std::optional<Cta861_3> cta861_3;
        common::get_cta861_3(hnd, &cta861_3);
        return provide(cta861_3);
    }
    if constexpr (metadataType == StandardMetadataType::SMPTE2094_40) {
        std::optional<std::vector<uint8_t>> smpte2094_40;
        common::get_smpte2094_40(hnd, &smpte2094_40);
        return provide(smpte2094_40);
    }
    if constexpr (metadataType == StandardMetadataType::STRIDE) {
        std::vector<PlaneLayout> layouts;
        Error err = static_cast<Error>(common::get_plane_layouts(hnd, &layouts));
        uint64_t stride = (layouts[0].strideInBytes * 8) / layouts[0].sampleIncrementInBits;
        return provide(stride);
    }
    return -AIMapper_Error::AIMAPPER_ERROR_UNSUPPORTED;
}

int32_t GrallocMapper::getStandardMetadata(buffer_handle_t _Nonnull buffer,
                                           int64_t standardMetadataType, void* _Nonnull outData,
                                           size_t outDataSize) {
    if (buffer == nullptr) return -AIMapper_Error::AIMAPPER_ERROR_BAD_BUFFER;

    auto provider = [&]<StandardMetadataType meta>(auto&& provide) -> int32_t {
        return getStandardMetadataHelper(static_cast<const private_handle_t*>(buffer), provide,
                                         StandardMetadata<meta>{});
    };
    return provideStandardMetadata(static_cast<StandardMetadataType>(standardMetadataType), outData,
                                   outDataSize, provider);
}

bool isPixelMetadataType(common::MetadataType meta) {
    return (meta.name == ::pixel::graphics::kPixelMetadataTypeName);
}

int32_t getPixelMetadataHelper(buffer_handle_t handle, const PixelMetadataType meta, void* outData,
                               size_t outDataSize) {
    switch (meta) {
        case PixelMetadataType::VIDEO_HDR: {
            auto result = ::pixel::graphics::utils::encode(
                    common::get_video_hdr(static_cast<const private_handle_t*>(handle)));

            outData = result.data();
            outDataSize = result.size();
            return -AIMapper_Error::AIMAPPER_ERROR_NONE;
        }
        case PixelMetadataType::VIDEO_ROI: {
            auto result = ::pixel::graphics::utils::encode(
                    common::get_video_roiinfo(static_cast<const private_handle_t*>(handle)));
            outData = result.data();
            outDataSize = result.size();
            return -AIMapper_Error::AIMAPPER_ERROR_NONE;
        }
        default:
            return -AIMapper_Error::AIMAPPER_ERROR_BAD_VALUE;
    }
}

int32_t GrallocMapper::getMetadata(buffer_handle_t _Nonnull buffer,
                                   AIMapper_MetadataType metadataType, void* _Nonnull outData,
                                   size_t outDataSize) {
    if (buffer == nullptr) return -AIMapper_Error::AIMAPPER_ERROR_BAD_BUFFER;

    if (isStandardMetadataType(common::MetadataType(metadataType))) {
        return getStandardMetadata(buffer, metadataType.value, outData, outDataSize);
    } else if (isPixelMetadataType(common::MetadataType(metadataType))) {
        const PixelMetadataType pixelMeta = static_cast<PixelMetadataType>(metadataType.value);
        return getPixelMetadataHelper(buffer, pixelMeta, outData, outDataSize);
    } else {
        return -AIMapper_Error::AIMAPPER_ERROR_UNSUPPORTED;
    }
}

template <StandardMetadataType TYPE>
AIMapper_Error setStandardMetadataHelper(const private_handle_t* hnd,
                                         typename StandardMetadata<TYPE>::value_type&& value) {
    if constexpr (TYPE == StandardMetadataType::DATASPACE) {
        common::set_dataspace(hnd, value);
    }
    if constexpr (TYPE == StandardMetadataType::CROP) {
        common::set_crop_rect(hnd, value[0]);
    }
    if constexpr (TYPE == StandardMetadataType::BLEND_MODE) {
        common::set_blend_mode(hnd, value);
    }
    if constexpr (TYPE == StandardMetadataType::SMPTE2086) {
        common::set_smpte2086(hnd, value);
    }
    if constexpr (TYPE == StandardMetadataType::CTA861_3) {
        common::set_cta861_3(hnd, value);
    }
    if constexpr (TYPE == StandardMetadataType::SMPTE2094_40) {
        common::set_smpte2094_40(hnd, value);
    }
    return AIMapper_Error::AIMAPPER_ERROR_NONE;
}

AIMapper_Error GrallocMapper::setStandardMetadata(buffer_handle_t _Nonnull bufferHandle,
                                                  int64_t standardTypeRaw,
                                                  const void* _Nonnull metadata,
                                                  size_t metadataSize) {
    if (bufferHandle == nullptr) return AIMapper_Error::AIMAPPER_ERROR_BAD_BUFFER;
    auto standardMeta = static_cast<StandardMetadataType>(standardTypeRaw);
    switch (standardMeta) {
        // Read-only values
        case StandardMetadataType::BUFFER_ID:
        case StandardMetadataType::NAME:
        case StandardMetadataType::WIDTH:
        case StandardMetadataType::HEIGHT:
        case StandardMetadataType::LAYER_COUNT:
        case StandardMetadataType::PIXEL_FORMAT_REQUESTED:
        case StandardMetadataType::USAGE:
            return AIMAPPER_ERROR_BAD_VALUE;

        // Supported to set
        case StandardMetadataType::BLEND_MODE:
        case StandardMetadataType::CTA861_3:
        case StandardMetadataType::DATASPACE:
        case StandardMetadataType::SMPTE2086:
        case StandardMetadataType::SMPTE2094_40:
        case StandardMetadataType::CROP:
            break;

        // Everything else unsupported
        default:
            return AIMAPPER_ERROR_UNSUPPORTED;
    }

    auto applier = [&]<StandardMetadataType meta>(auto&& value) -> AIMapper_Error {
        return setStandardMetadataHelper<meta>(static_cast<const private_handle_t*>(bufferHandle),
                                               std::forward<decltype(value)>(value));
    };

    return applyStandardMetadata(standardMeta, metadata, metadataSize, applier);
}

AIMapper_Error GrallocMapper::setMetadata(buffer_handle_t _Nonnull buffer,
                                          AIMapper_MetadataType metadataType,
                                          const void* _Nonnull metadata, size_t metadataSize) {
    if (buffer == nullptr) return AIMapper_Error::AIMAPPER_ERROR_BAD_BUFFER;
    if (isStandardMetadataType(common::MetadataType(metadataType))) {
        return setStandardMetadata(buffer, metadataType.value, metadata, metadataSize);
    } else
        return AIMapper_Error::AIMAPPER_ERROR_NONE;
}

AIMapper_Error GrallocMapper::getTransportSize(buffer_handle_t _Nonnull buffer,
                                               uint32_t* _Nonnull outNumFds,
                                               uint32_t* _Nonnull outNumInts) {
    if (buffer == nullptr) return AIMapper_Error::AIMAPPER_ERROR_BAD_BUFFER;
    AIMapper_Error err =
            static_cast<AIMapper_Error>(common::getTransportSize(buffer, outNumFds, outNumInts));
    return err;
}

AIMapper_Error GrallocMapper::listSupportedMetadataTypes(
        const AIMapper_MetadataTypeDescription* _Nullable* _Nonnull outDescriptionList,
        size_t* _Nonnull outNumberOfDescriptions) {
    std::vector<common::MetadataTypeDescription> desc = common::listSupportedMetadataTypes();
    std::vector<AIMapper_MetadataTypeDescription> outDesc;
    for (auto x : desc) {
        outDesc.push_back(static_cast<AIMapper_MetadataTypeDescription>(x));
    }

    *outDescriptionList = outDesc.data();
    *outNumberOfDescriptions = outDesc.size();
    return AIMapper_Error::AIMAPPER_ERROR_NONE;
}

AIMapper_Error GrallocMapper::dumpBuffer(buffer_handle_t _Nonnull bufferHandle,
                                         AIMapper_DumpBufferCallback _Nonnull dumpBufferCallback,
                                         void* _Null_unspecified context) {
    if (bufferHandle == nullptr) return AIMapper_Error::AIMAPPER_ERROR_BAD_BUFFER;
    common::BufferDump out;
    AIMapper_Error err = static_cast<AIMapper_Error>(common::dumpBuffer(bufferHandle, out));
    for (auto metadump : out.metadataDump) {
        dumpBufferCallback(context, static_cast<AIMapper_MetadataType>(metadump.metadataType),
                           static_cast<void*>((metadump.metadata).data()),
                           sizeof(metadump.metadata));
    }
    return AIMapper_Error::AIMAPPER_ERROR_NONE;
}

AIMapper_Error GrallocMapper::dumpAllBuffers(
        AIMapper_BeginDumpBufferCallback _Nonnull beginDumpBufferCallback,
        AIMapper_DumpBufferCallback _Nonnull dumpBufferCallback, void* _Null_unspecified context) {
    std::vector<common::BufferDump> bufferDump = common::dumpBuffers();
    for (auto dump : bufferDump) {
        beginDumpBufferCallback(context);
        for (auto metadump : dump.metadataDump) {
            dumpBufferCallback(context, static_cast<AIMapper_MetadataType>(metadump.metadataType),
                               static_cast<void*>((metadump.metadata).data()),
                               sizeof(metadump.metadata));
        }
    }
    return AIMapper_Error::AIMAPPER_ERROR_NONE;
}

AIMapper_Error GrallocMapper::getReservedRegion(buffer_handle_t _Nonnull buffer,
                                                void* _Nullable* _Nonnull outReservedRegion,
                                                uint64_t* _Nonnull outReservedSize) {
    if (buffer == nullptr) return AIMapper_Error::AIMAPPER_ERROR_BAD_BUFFER;
    uint64_t reservedSize = 0;
    AIMapper_Error err = static_cast<AIMapper_Error>(
            common::getReservedRegion(buffer, outReservedRegion, reservedSize));
    *outReservedSize = reservedSize;
    return err;
}

} // namespace mapper
} // namespace arm

extern "C" uint32_t ANDROID_HAL_MAPPER_VERSION = AIMAPPER_VERSION_5;

extern "C" AIMapper_Error AIMapper_loadIMapper(AIMapper* _Nullable* _Nonnull outImplementation) {
    static vendor::mapper::IMapperProvider<arm::mapper::GrallocMapper> provider;
    return provider.load(outImplementation);
}
