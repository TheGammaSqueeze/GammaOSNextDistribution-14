#pragma once

#include <aidl/android/hardware/graphics/common/BufferUsage.h>

#include <cstdint>

namespace pixel::graphics {

using FrameworkUsage = aidl::android::hardware::graphics::common::BufferUsage;

#define MapUsage(f) f = static_cast<uint32_t>(FrameworkUsage::f)

enum Usage : uint64_t {
    MapUsage(CPU_READ_MASK),
    MapUsage(CPU_READ_NEVER),
    MapUsage(CPU_READ_RARELY),
    MapUsage(CPU_READ_OFTEN),
    MapUsage(CPU_WRITE_MASK),
    MapUsage(CPU_WRITE_NEVER),
    MapUsage(CPU_WRITE_RARELY),
    MapUsage(CPU_WRITE_OFTEN),
    MapUsage(GPU_TEXTURE),
    MapUsage(GPU_RENDER_TARGET),
    MapUsage(COMPOSER_OVERLAY),
    MapUsage(COMPOSER_CLIENT_TARGET),
    MapUsage(PROTECTED),
    MapUsage(COMPOSER_CURSOR),
    MapUsage(VIDEO_ENCODER),
    MapUsage(CAMERA_OUTPUT),
    MapUsage(CAMERA_INPUT),
    MapUsage(RENDERSCRIPT),
    MapUsage(VIDEO_DECODER),
    MapUsage(SENSOR_DIRECT_DATA),
    MapUsage(GPU_DATA_BUFFER),
    MapUsage(GPU_CUBE_MAP),
    MapUsage(GPU_MIPMAP_COMPLETE),
    MapUsage(HW_IMAGE_ENCODER),
    MapUsage(FRONT_BUFFER),
    MapUsage(VENDOR_MASK),
    MapUsage(VENDOR_MASK_HI),

    // Pixel specific usages

    // Used for AION to allocate PLACEHOLDER buffers
    PLACEHOLDER_BUFFER = 1ULL << 28,

    NO_COMPRESSION = 1ULL << 29,

    // Video IPs. These flags only make sense in combination with VIDEO_ENCODER/VIDEO_DECODER usage
    // flags
    MFC = 1ULL << 50,
    BO = 1ULL << 51,
    BW = BO,

    // Used for the camera ISP image heap of the dual PD buffer.
    TPU_INPUT = 1ULL << 62,

    // Used to select specific heap for faceauth raw images used for evaluation
    FACEAUTH_RAW_EVAL = 1ULL << 63,
};

#undef MapUsage

} // namespace pixel::graphics
