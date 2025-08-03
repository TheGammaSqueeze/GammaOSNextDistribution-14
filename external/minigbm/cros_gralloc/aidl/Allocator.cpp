/*
 * Copyright 2022 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#include "Allocator.h"

#include <aidl/android/hardware/graphics/allocator/AllocationError.h>
#include <aidlcommonsupport/NativeHandle.h>
#include <android-base/logging.h>
#include <android/binder_ibinder_platform.h>
#include <gralloctypes/Gralloc4.h>
#include <log/log.h>

#include "cros_gralloc/gralloc4/CrosGralloc4Utils.h"

using aidl::android::hardware::common::NativeHandle;
using BufferDescriptorInfoV4 =
        android::hardware::graphics::mapper::V4_0::IMapper::BufferDescriptorInfo;

static const std::string STANDARD_METADATA_DATASPACE = "android.hardware.graphics.common.Dataspace";

namespace aidl::android::hardware::graphics::allocator::impl {
namespace {

inline ndk::ScopedAStatus ToBinderStatus(AllocationError error) {
    return ndk::ScopedAStatus::fromServiceSpecificError(static_cast<int32_t>(error));
}

}  // namespace

bool Allocator::init() {
    mDriver = cros_gralloc_driver::get_instance();
    return mDriver != nullptr;
}

// TODO(natsu): deduplicate with CrosGralloc4Allocator after the T release.
ndk::ScopedAStatus Allocator::initializeMetadata(
        cros_gralloc_handle_t crosHandle,
        const struct cros_gralloc_buffer_descriptor& crosDescriptor,
        Dataspace initialDataspace) {
    if (!mDriver) {
        ALOGE("Failed to initializeMetadata. Driver is uninitialized.\n");
        return ToBinderStatus(AllocationError::NO_RESOURCES);
    }

    if (!crosHandle) {
        ALOGE("Failed to initializeMetadata. Invalid handle.\n");
        return ToBinderStatus(AllocationError::NO_RESOURCES);
    }

    void* addr;
    uint64_t size;
    int ret = mDriver->get_reserved_region(crosHandle, &addr, &size);
    if (ret) {
        ALOGE("Failed to getReservedRegion.\n");
        return ToBinderStatus(AllocationError::NO_RESOURCES);
    }

    CrosGralloc4Metadata* crosMetadata = reinterpret_cast<CrosGralloc4Metadata*>(addr);

    snprintf(crosMetadata->name, CROS_GRALLOC4_METADATA_MAX_NAME_SIZE, "%s",
             crosDescriptor.name.c_str());
    crosMetadata->dataspace = initialDataspace;
    crosMetadata->blendMode = common::BlendMode::INVALID;

    return ndk::ScopedAStatus::ok();
}

void Allocator::releaseBufferAndHandle(native_handle_t* handle) {
    mDriver->release(handle);
    native_handle_close(handle);
    native_handle_delete(handle);
}

ndk::ScopedAStatus Allocator::allocate(const std::vector<uint8_t>& descriptor, int32_t count,
                                       allocator::AllocationResult* outResult) {
    if (!mDriver) {
        ALOGE("Failed to allocate. Driver is uninitialized.\n");
        return ToBinderStatus(AllocationError::NO_RESOURCES);
    }

    BufferDescriptorInfoV4 description;

    int ret = ::android::gralloc4::decodeBufferDescriptorInfo(descriptor, &description);
    if (ret) {
        ALOGE("Failed to allocate. Failed to decode buffer descriptor: %d.\n", ret);
        return ToBinderStatus(AllocationError::BAD_DESCRIPTOR);
    }

    std::vector<native_handle_t*> handles;
    handles.resize(count, nullptr);

    for (int32_t i = 0; i < count; i++) {
        ndk::ScopedAStatus status = allocate(description, &outResult->stride, &handles[i]);
        if (!status.isOk()) {
            for (int32_t j = 0; j < i; j++) {
                releaseBufferAndHandle(handles[j]);
            }
            return status;
        }
    }

    outResult->buffers.resize(count);
    for (int32_t i = 0; i < count; i++) {
        auto handle = handles[i];
        outResult->buffers[i] = ::android::dupToAidl(handle);
        releaseBufferAndHandle(handle);
    }

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Allocator::allocate(const BufferDescriptorInfoV4& descriptor, int32_t* outStride,
                                       native_handle_t** outHandle, Dataspace initialDataspace) {
    if (!mDriver) {
        ALOGE("Failed to allocate. Driver is uninitialized.\n");
        return ToBinderStatus(AllocationError::NO_RESOURCES);
    }

    struct cros_gralloc_buffer_descriptor crosDescriptor;
    if (convertToCrosDescriptor(descriptor, &crosDescriptor)) {
        return ToBinderStatus(AllocationError::UNSUPPORTED);
    }

    crosDescriptor.reserved_region_size += sizeof(CrosGralloc4Metadata);

    if (!mDriver->is_supported(&crosDescriptor)) {
        const std::string drmFormatString = get_drm_format_string(crosDescriptor.drm_format);
        const std::string pixelFormatString = getPixelFormatString(descriptor.format);
        const std::string usageString = getUsageString(descriptor.usage);
        ALOGE("Failed to allocate. Unsupported combination: pixel format:%s, drm format:%s, "
              "usage:%s\n",
              pixelFormatString.c_str(), drmFormatString.c_str(), usageString.c_str());
        return ToBinderStatus(AllocationError::UNSUPPORTED);
    }

    native_handle_t* handle;
    int ret = mDriver->allocate(&crosDescriptor, &handle);
    if (ret) {
        return ToBinderStatus(AllocationError::NO_RESOURCES);
    }

    cros_gralloc_handle_t crosHandle = cros_gralloc_convert_handle(handle);

    auto status = initializeMetadata(crosHandle, crosDescriptor, initialDataspace);
    if (!status.isOk()) {
        ALOGE("Failed to allocate. Failed to initialize gralloc buffer metadata.");
        releaseBufferAndHandle(handle);
        return status;
    }

    *outStride = static_cast<int32_t>(crosHandle->pixel_stride);
    *outHandle = handle;

    return ndk::ScopedAStatus::ok();
}

static BufferDescriptorInfoV4 convertAidlToIMapperV4Descriptor(const BufferDescriptorInfo& info) {
    return BufferDescriptorInfoV4 {
        .name{reinterpret_cast<const char*>(info.name.data())},
        .width = static_cast<uint32_t>(info.width),
        .height = static_cast<uint32_t>(info.height),
        .layerCount = static_cast<uint32_t>(info.layerCount),
        .format = static_cast<::android::hardware::graphics::common::V1_2::PixelFormat>(info.format),
        .usage = static_cast<uint64_t>(info.usage),
        .reservedSize = 0,
    };
}

ndk::ScopedAStatus Allocator::allocate2(const BufferDescriptorInfo& descriptor, int32_t count,
                            allocator::AllocationResult* outResult) {
    if (!mDriver) {
        ALOGE("Failed to allocate. Driver is uninitialized.\n");
        return ToBinderStatus(AllocationError::NO_RESOURCES);
    }

    Dataspace initialDataspace = Dataspace::UNKNOWN;

    for (const auto& option : descriptor.additionalOptions) {
        if (option.name != STANDARD_METADATA_DATASPACE) {
            return ToBinderStatus(AllocationError::UNSUPPORTED);
        }
        initialDataspace = static_cast<Dataspace>(option.value);
    }

    BufferDescriptorInfoV4 descriptionV4 = convertAidlToIMapperV4Descriptor(descriptor);

    std::vector<native_handle_t*> handles;
    handles.resize(count, nullptr);

    for (int32_t i = 0; i < count; i++) {
        ndk::ScopedAStatus status = allocate(descriptionV4, &outResult->stride, &handles[i],
                                             initialDataspace);
        if (!status.isOk()) {
            for (int32_t j = 0; j < i; j++) {
                releaseBufferAndHandle(handles[j]);
            }
            return status;
        }
    }

    outResult->buffers.resize(count);
    for (int32_t i = 0; i < count; i++) {
        auto handle = handles[i];
        outResult->buffers[i] = ::android::dupToAidl(handle);
        releaseBufferAndHandle(handle);
    }

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Allocator::isSupported(const BufferDescriptorInfo& descriptor,
                            bool* outResult) {
    if (!mDriver) {
        ALOGE("Failed to allocate. Driver is uninitialized.\n");
        return ToBinderStatus(AllocationError::NO_RESOURCES);
    }

    for (const auto& option : descriptor.additionalOptions) {
        if (option.name != STANDARD_METADATA_DATASPACE) {
            *outResult = false;
            return ndk::ScopedAStatus::ok();
        }
    }

    struct cros_gralloc_buffer_descriptor crosDescriptor;
    if (convertToCrosDescriptor(convertAidlToIMapperV4Descriptor(descriptor), &crosDescriptor)) {
        // Failing to convert the descriptor means the layer count, pixel format, or usage is
        // unsupported, thus isSupported() = false
        *outResult = false;
        return ndk::ScopedAStatus::ok();
    }

    crosDescriptor.reserved_region_size += sizeof(CrosGralloc4Metadata);

    *outResult = mDriver->is_supported(&crosDescriptor);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Allocator::getIMapperLibrarySuffix(std::string* outResult) {
    *outResult = "minigbm";
    return ndk::ScopedAStatus::ok();
}

::ndk::SpAIBinder Allocator::createBinder() {
    auto binder = BnAllocator::createBinder();
    AIBinder_setInheritRt(binder.get(), true);
    return binder;
}

}  // namespace aidl::android::hardware::graphics::allocator::impl