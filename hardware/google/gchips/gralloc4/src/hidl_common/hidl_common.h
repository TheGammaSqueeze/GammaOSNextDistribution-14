#ifndef HIDL_COMMON
#define HIDL_COMMON

#include "mali_fourcc.h"
#include <gralloctypes/Gralloc4.h>
#include <aidl/android/hardware/graphics/common/BufferUsage.h>
#include <aidl/android/hardware/graphics/common/PixelFormat.h>
#include <aidl/android/hardware/graphics/common/StandardMetadataType.h>

using aidl::android::hardware::graphics::common::BufferUsage;
using aidl::android::hardware::graphics::common::PixelFormat;
using aidl::android::hardware::graphics::common::StandardMetadataType;
using aidl::android::hardware::graphics::common::ExtendableType;
using aidl::android::hardware::graphics::common::PlaneLayout;
using aidl::android::hardware::graphics::common::PlaneLayoutComponent;
using aidl::android::hardware::graphics::common::Rect;
using aidl::android::hardware::graphics::common::BlendMode;
using aidl::android::hardware::graphics::common::Dataspace;

namespace hidl {
using PixelFormat = android::hardware::graphics::common::V1_2::PixelFormat;
} // namespace hidl


template <typename T>
using frameworks_vec = android::hardware::hidl_vec<T>;

#ifdef GRALLOC_MAPPER_4

#include <android/hardware/graphics/mapper/4.0/IMapper.h>

namespace hidl {
using android::hardware::graphics::mapper::V4_0::Error;
} // namespace hidl
using android::hardware::graphics::mapper::V4_0::BufferDescriptor;
using android::hardware::graphics::mapper::V4_0::IMapper;

using frameworks_handle = android::hardware::hidl_handle;

#elif defined(GRALLOC_MAPPER_5)

#include <android/hardware/graphics/mapper/IMapper.h>
#include <android/hardware/graphics/mapper/utils/IMapperMetadataTypes.h>
#include <android/hardware/graphics/mapper/utils/IMapperProvider.h>
using android::hardware::graphics::mapper::StandardMetadata;

#endif // GRALLOC_MAPPER_4 or GRALLOC_MAPPER_5

#endif // HIDL_COMMON
