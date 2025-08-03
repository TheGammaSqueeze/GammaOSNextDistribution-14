/*
* Copyright (C) 2020 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

#include <android/hardware/graphics/mapper/3.0/IMapper.h>
#include <cutils/native_handle.h>
#include <sync/sync.h>

#include "cb_handle_30.h"
#include "host_connection_session.h"
#include "FormatConversions.h"
#include "debug.h"

#include "aemu/base/Tracing.h"

#define ATRACE_TAG ATRACE_TAG_GRAPHICS

const int kOMX_COLOR_FormatYUV420Planar = 19;

using ::android::hardware::hidl_handle;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;

using ::android::hardware::graphics::common::V1_2::PixelFormat;
using ::android::hardware::graphics::common::V1_0::BufferUsage;

namespace MapperV3 = ::android::hardware::graphics::mapper::V3_0;

using IMapper3 = MapperV3::IMapper;
using Error3 = MapperV3::Error;
using YCbCrLayout3 = MapperV3::YCbCrLayout;

namespace {
size_t align(const size_t v, const size_t a) { return (v + a - 1) & ~(a - 1); }

static int waitFenceFd(const int fd, const char* logname) {
    const int warningTimeout = 5000;
    if (sync_wait(fd, warningTimeout) < 0) {
        if (errno == ETIME) {
            ALOGW("%s: fence %d didn't signal in %d ms", logname, fd, warningTimeout);
            if (sync_wait(fd, -1) < 0) {
                RETURN_ERROR(errno);
            } else {
                RETURN(0);
            }
        } else {
            RETURN_ERROR(errno);
        }
    } else {
        RETURN(0);
    }
}

int waitHidlFence(const hidl_handle& hidlHandle, const char* logname) {
    const native_handle_t* nativeHandle = hidlHandle.getNativeHandle();

    if (!nativeHandle) {
        RETURN(0);
    }
    if (nativeHandle->numFds > 1) {
        RETURN_ERROR(-EINVAL);
    }
    if (nativeHandle->numInts != 0) {
        RETURN_ERROR(-EINVAL);
    }

    return waitFenceFd(nativeHandle->data[0], logname);
}

bool needGpuBuffer(const uint32_t usage) {
    return usage & (BufferUsage::GPU_TEXTURE
                    | BufferUsage::GPU_RENDER_TARGET
                    | BufferUsage::COMPOSER_OVERLAY
                    | BufferUsage::COMPOSER_CLIENT_TARGET
                    | BufferUsage::GPU_DATA_BUFFER);
}

constexpr uint64_t one64 = 1;

constexpr uint64_t ones(int from, int to) {
    return ((one64 << (to - from + 1)) - 1) << from;
}

class GoldfishMapper : public IMapper3 {
public:
    GoldfishMapper() : m_hostConn(HostConnection::createUnique()) {
        GoldfishAddressSpaceHostMemoryAllocator host_memory_allocator(false);
        CRASH_IF(!host_memory_allocator.is_opened(),
                 "GoldfishAddressSpaceHostMemoryAllocator failed to open");

        GoldfishAddressSpaceBlock bufferBits;
        CRASH_IF(host_memory_allocator.hostMalloc(&bufferBits, 256),
                 "hostMalloc failed");

        m_physAddrToOffset = bufferBits.physAddr() - bufferBits.offset();

        host_memory_allocator.hostFree(&bufferBits);
    }

    Return<void> importBuffer(const hidl_handle& hh,
                              importBuffer_cb hidl_cb) {
        native_handle_t* imported = nullptr;
        const Error3 e = importBufferImpl(hh.getNativeHandle(), &imported);
        if (e == Error3::NONE) {
            hidl_cb(Error3::NONE, imported);
        } else {
            hidl_cb(e, nullptr);
        }
        return {};
    }

    Return<Error3> freeBuffer(void* raw) {
        if (!raw) {
            RETURN_ERROR(Error3::BAD_BUFFER);
        }
        cb_handle_30_t* cb = cb_handle_30_t::from(raw);
        if (!cb) {
            RETURN_ERROR(Error3::BAD_BUFFER);
        }

        if (cb->mmapedSize > 0) {
            GoldfishAddressSpaceBlock::memoryUnmap(cb->getBufferPtr(), cb->mmapedSize);
        }

        native_handle_close(cb);
        native_handle_delete(cb);

        RETURN(Error3::NONE);
    }

    Return<void> lock(void* raw,
                      uint64_t cpuUsage,
                      const Rect& accessRegion,
                      const hidl_handle& acquireFence,
                      lock_cb hidl_cb) {
        void* ptr = nullptr;
        int32_t bytesPerPixel = 0;
        int32_t bytesPerStride = 0;

        const Error3 e = lockImpl(raw, cpuUsage, accessRegion, acquireFence,
                                  &ptr, &bytesPerPixel, &bytesPerStride);
        if (e == Error3::NONE) {
            hidl_cb(Error3::NONE, ptr, bytesPerPixel, bytesPerStride);
        } else {
            hidl_cb(e, nullptr, 0, 0);
        }
        return {};
    }

    Return<void> lockYCbCr(void* raw,
                           uint64_t cpuUsage,
                           const Rect& accessRegion,
                           const hidl_handle& acquireFence,
                           lockYCbCr_cb hidl_cb) {
        YCbCrLayout3 ycbcr = {};
        const Error3 e = lockYCbCrImpl(raw, cpuUsage, accessRegion, acquireFence,
                                       &ycbcr);
        if (e == Error3::NONE) {
            hidl_cb(Error3::NONE, ycbcr);
        } else {
            hidl_cb(e, {});
        }
        return {};
    }

    Return<void> unlock(void* raw, unlock_cb hidl_cb) {
        hidl_cb(unlockImpl(raw), {});
        return {};

    }

    Return<void> createDescriptor(const BufferDescriptorInfo& description,
                                  createDescriptor_cb hidl_cb) {
        hidl_vec<uint32_t> raw;
        encodeBufferDescriptorInfo(description, &raw);
        hidl_cb(Error3::NONE, raw);
        return {};
    }

    Return<void> isSupported(const IMapper::BufferDescriptorInfo& description,
                             isSupported_cb hidl_cb) {
        hidl_cb(Error3::NONE, isSupportedImpl(description));
        return {};
    }

    Return<Error3> validateBufferSize(void* buffer,
                                      const BufferDescriptorInfo& descriptor,
                                      uint32_t stride) {
        const cb_handle_30_t* cb = cb_handle_30_t::from(buffer);
        if (cb) {
            return validateBufferSizeImpl(*cb, descriptor, stride);
        } else {
            RETURN_ERROR(Error3::BAD_BUFFER);
        }
    }

    Return<void> getTransportSize(void* buffer,
                                  getTransportSize_cb hidl_cb) {
        const cb_handle_30_t* cb = cb_handle_30_t::from(buffer);
        if (cb) {
            hidl_cb(Error3::NONE, cb->numFds, cb->numInts);
        } else {
            hidl_cb(Error3::BAD_BUFFER, 0, 0);
        }

        return {};
    }

private:  // **** impl ****
    Error3 importBufferImpl(const native_handle_t* nh, native_handle_t** phandle) {
        if (!nh) {
            RETURN_ERROR(Error3::BAD_BUFFER);
        }
        native_handle_t* imported = native_handle_clone(nh);
        if (!imported) {
            RETURN_ERROR(Error3::BAD_BUFFER);
        }
        cb_handle_30_t* cb = cb_handle_30_t::from(imported);
        if (!cb) {
            native_handle_close(imported);
            native_handle_delete(imported);
            RETURN_ERROR(Error3::BAD_BUFFER);
        }

        if (cb->mmapedSize > 0) {
            LOG_ALWAYS_FATAL_IF(cb->bufferFdIndex < 0);
            void* ptr;
            const int res = GoldfishAddressSpaceBlock::memoryMap(
                cb->getBufferPtr(),
                cb->mmapedSize,
                cb->fds[cb->bufferFdIndex],
                cb->getMmapedOffset(),
                &ptr);
            if (res) {
                native_handle_close(imported);
                native_handle_delete(imported);
                RETURN_ERROR(Error3::NO_RESOURCES);
            }
            cb->setBufferPtr(ptr);
        }

        *phandle = imported;
        RETURN(Error3::NONE);
    }

    void setLocked(cb_handle_30_t* cb, const uint8_t checkedUsage,
                   const Rect& accessRegion) {
        if (checkedUsage & BufferUsage::CPU_WRITE_MASK) {
            cb->lockedLeft = accessRegion.left;
            cb->lockedTop = accessRegion.top;
            cb->lockedWidth = accessRegion.width;
            cb->lockedHeight = accessRegion.height;
        } else {
            cb->lockedLeft = 0;
            cb->lockedTop = 0;
            cb->lockedWidth = cb->width;
            cb->lockedHeight = cb->height;
        }
        cb->lockedUsage = checkedUsage;
    }

    Error3 lockImpl(void* raw,
                    const uint64_t uncheckedUsage,
                    const Rect& accessRegion,
                    const hidl_handle& acquireFence,
                    void** pptr,
                    int32_t* pBytesPerPixel,
                    int32_t* pBytesPerStride) {
        if (!raw) {
            RETURN_ERROR(Error3::BAD_BUFFER);
        }
        cb_handle_30_t* cb = cb_handle_30_t::from(raw);
        if (!cb) {
            RETURN_ERROR(Error3::BAD_BUFFER);
        }
        if (cb->lockedUsage) {
            RETURN_ERROR(Error3::BAD_VALUE);
        }
        const uint8_t checkedUsage = uncheckedUsage & cb->usage &
            (BufferUsage::CPU_READ_MASK | BufferUsage::CPU_WRITE_MASK);
        if (checkedUsage == 0) {
            RETURN_ERROR(Error3::BAD_VALUE);
        }
        if (!cb->bufferSize) {
            RETURN_ERROR(Error3::BAD_BUFFER);
        }
        char* const bufferBits = static_cast<char*>(cb->getBufferPtr());
        if (!bufferBits) {
            RETURN_ERROR(Error3::BAD_BUFFER);
        }
        if (waitHidlFence(acquireFence, __func__)) {
            RETURN_ERROR(Error3::BAD_VALUE);
        }

        if (cb->hostHandle) {
            const Error3 e = lockHostImpl(*cb, checkedUsage, accessRegion, bufferBits);
            if (e != Error3::NONE) {
                return e;
            }
        }

        setLocked(cb, checkedUsage, accessRegion);

        *pptr = bufferBits;
        *pBytesPerPixel = cb->bytesPerPixel;
        *pBytesPerStride = cb->bytesPerPixel * cb->stride;
        RETURN(Error3::NONE);
    }

    Error3 lockYCbCrImpl(void* raw,
                         const uint64_t uncheckedUsage,
                         const Rect& accessRegion,
                         const hidl_handle& acquireFence,
                         YCbCrLayout3* pYcbcr) {
        if (!raw) {
            RETURN_ERROR(Error3::BAD_BUFFER);
        }
        cb_handle_30_t* cb = cb_handle_30_t::from(raw);
        if (!cb) {
            RETURN_ERROR(Error3::BAD_BUFFER);
        }
        if (cb->lockedUsage) {
            RETURN_ERROR(Error3::BAD_VALUE);
        }
        const uint8_t checkedUsage = uncheckedUsage & cb->usage &
            (BufferUsage::CPU_READ_MASK | BufferUsage::CPU_WRITE_MASK);
        if (checkedUsage == 0) {
            RETURN_ERROR(Error3::BAD_VALUE);
        }
        if (!cb->bufferSize) {
            RETURN_ERROR(Error3::BAD_BUFFER);
        }
        char* const bufferBits = static_cast<char*>(cb->getBufferPtr());
        if (!bufferBits) {
            RETURN_ERROR(Error3::BAD_BUFFER);
        }
        if (waitHidlFence(acquireFence, __func__)) {
            RETURN_ERROR(Error3::BAD_VALUE);
        }

        size_t uOffset;
        size_t vOffset;
        size_t yStride;
        size_t cStride;
        size_t cStep;
        switch (static_cast<PixelFormat>(cb->format)) {
        case PixelFormat::YCRCB_420_SP:
            yStride = cb->width;
            cStride = yStride;
            vOffset = yStride * cb->height;
            uOffset = vOffset + 1;
            cStep = 2;
            break;

        case PixelFormat::YV12:
            // https://developer.android.com/reference/android/graphics/ImageFormat.html#YV12
            yStride = align(cb->width, 16);
            cStride = align(yStride / 2, 16);
            vOffset = yStride * cb->height;
            uOffset = vOffset + (cStride * cb->height / 2);
            cStep = 1;
            break;

        case PixelFormat::YCBCR_420_888:
            yStride = cb->width;
            cStride = yStride / 2;
            uOffset = cb->height * yStride;
            vOffset = uOffset + cStride * cb->height / 2;
            cStep = 1;
            break;

        case PixelFormat::YCBCR_P010:
            yStride = cb->width * 2;
            cStride = yStride;
            uOffset = cb->height * yStride;
            vOffset = uOffset + 2;
            cStep = 4;
            break;

        default:
            ALOGE("%s:%d unexpected format (%d)", __func__, __LINE__, cb->format);
            RETURN_ERROR(Error3::BAD_BUFFER);
        }

        if (cb->hostHandle) {
            const Error3 e = lockHostImpl(*cb, checkedUsage, accessRegion, bufferBits);
            if (e != Error3::NONE) {
                return e;
            }
        }

        setLocked(cb, checkedUsage, accessRegion);

        pYcbcr->y = bufferBits;
        pYcbcr->cb = bufferBits + uOffset;
        pYcbcr->cr = bufferBits + vOffset;
        pYcbcr->yStride = yStride;
        pYcbcr->cStride = cStride;
        pYcbcr->chromaStep = cStep;

        RETURN(Error3::NONE);
    }

    Error3 lockHostImpl(cb_handle_30_t& cb,
                        const uint8_t checkedUsage,
                        const Rect& accessRegion,
                        char* const bufferBits) {
        const HostConnectionSession conn = getHostConnectionSession();
        ExtendedRCEncoderContext *const rcEnc = conn.getRcEncoder();
        const bool usageSwRead = (checkedUsage & BufferUsage::CPU_READ_MASK) != 0;

        const int res = rcEnc->rcColorBufferCacheFlush(
            rcEnc, cb.hostHandle, 0, usageSwRead);
        if (res < 0) {
            RETURN_ERROR(Error3::NO_RESOURCES);
        }

        if (usageSwRead) {
            if (gralloc_is_yuv_format(cb.format)) {
                if (rcEnc->hasYUVCache()) {
                    uint32_t bufferSize;
                    switch (static_cast<PixelFormat>(cb.format)) {
                    case PixelFormat::YV12:
                        get_yv12_offsets(cb.width, cb.height,
                                         nullptr, nullptr, &bufferSize);
                        break;
                    case PixelFormat::YCBCR_420_888:
                        get_yuv420p_offsets(cb.width, cb.height,
                                            nullptr, nullptr, &bufferSize);
                        break;
                    default:
                        CRASH("Unexpected format, switch is out of sync with gralloc_is_yuv_format");
                        break;
                    }

                    rcEnc->rcReadColorBufferYUV(rcEnc, cb.hostHandle,
                        0, 0, cb.width, cb.height,
                        bufferBits, bufferSize);
                } else {
                    // We are using RGB888
                    std::vector<char> tmpBuf(cb.width * cb.height * 3);
                    rcEnc->rcReadColorBuffer(rcEnc, cb.hostHandle,
                                             0, 0, cb.width, cb.height,
                                             cb.glFormat, cb.glType,
                                             tmpBuf.data());
                    switch (static_cast<PixelFormat>(cb.format)) {
                    case PixelFormat::YV12:
                        rgb888_to_yv12(bufferBits, tmpBuf.data(),
                                       cb.width, cb.height,
                                       accessRegion.left,
                                       accessRegion.top,
                                       accessRegion.left + accessRegion.width - 1,
                                       accessRegion.top + accessRegion.height - 1);
                        break;
                    case PixelFormat::YCBCR_420_888:
                        rgb888_to_yuv420p(bufferBits, tmpBuf.data(),
                                          cb.width, cb.height,
                                          accessRegion.left,
                                          accessRegion.top,
                                          accessRegion.left + accessRegion.width - 1,
                                          accessRegion.top + accessRegion.height - 1);
                        break;
                    default:
                        CRASH("Unexpected format, switch is out of sync with gralloc_is_yuv_format");
                        break;
                    }
                }
            } else {
                if (rcEnc->featureInfo()->hasReadColorBufferDma) {
                    {
                        AEMU_SCOPED_TRACE("bindDmaDirectly");
                        rcEnc->bindDmaDirectly(bufferBits,
                                getMmapedPhysAddr(cb.getMmapedOffset()));
                    }
                    rcEnc->rcReadColorBufferDMA(rcEnc,
                        cb.hostHandle,
                        0, 0, cb.width, cb.height,
                        cb.glFormat, cb.glType,
                        bufferBits, cb.width * cb.height * cb.bytesPerPixel);
                } else {
                    rcEnc->rcReadColorBuffer(rcEnc,
                        cb.hostHandle,
                        0, 0, cb.width, cb.height,
                        cb.glFormat, cb.glType,
                        bufferBits);
                }
            }
        }

        RETURN(Error3::NONE);
    }

    Error3 unlockImpl(void* raw) {
        AEMU_SCOPED_TRACE("unlockImpl body");
        if (!raw) {
            RETURN_ERROR(Error3::BAD_BUFFER);
        }
        cb_handle_30_t* cb = cb_handle_30_t::from(raw);
        if (!cb) {
            RETURN_ERROR(Error3::BAD_BUFFER);
        }
        if (cb->lockedUsage == 0) {
            RETURN_ERROR(Error3::BAD_VALUE);
        }
        if (!cb->bufferSize) {
            RETURN_ERROR(Error3::BAD_BUFFER);
        }
        char* const bufferBits = static_cast<char*>(cb->getBufferPtr());
        if (!bufferBits) {
            RETURN_ERROR(Error3::BAD_BUFFER);
        }

        if (cb->hostHandle) {
            unlockHostImpl(*cb, bufferBits);
        }

        cb->lockedLeft = 0;
        cb->lockedTop = 0;
        cb->lockedWidth = 0;
        cb->lockedHeight = 0;
        cb->lockedUsage = 0;

        RETURN(Error3::NONE);
    }

    void unlockHostImpl(cb_handle_30_t& cb, char* const bufferBits) {
        AEMU_SCOPED_TRACE("unlockHostImpl body");
        if (cb.lockedUsage & BufferUsage::CPU_WRITE_MASK) {
            const int bpp = glUtilsPixelBitSize(cb.glFormat, cb.glType) >> 3;
            const uint32_t rgbSize = cb.width * cb.height * bpp;
            const char* bitsToSend;
            uint32_t sizeToSend;

            if (gralloc_is_yuv_format(cb.format)) {
                bitsToSend = bufferBits;
                switch (static_cast<PixelFormat>(cb.format)) {
                    case PixelFormat::YV12:
                        get_yv12_offsets(cb.width, cb.height, nullptr, nullptr, &sizeToSend);
                        break;
                    case PixelFormat::YCBCR_420_888:
                        get_yuv420p_offsets(cb.width, cb.height, nullptr, nullptr, &sizeToSend);
                        break;
                    default:
                        CRASH("Unexpected format, switch is out of sync with gralloc_is_yuv_format");
                        break;
                }
            } else {
                bitsToSend = bufferBits;
                sizeToSend = rgbSize;
            }

            {
                const HostConnectionSession conn = getHostConnectionSession();
                ExtendedRCEncoderContext *const rcEnc = conn.getRcEncoder();
                {
                    AEMU_SCOPED_TRACE("bindDmaDirectly");
                    rcEnc->bindDmaDirectly(bufferBits,
                            getMmapedPhysAddr(cb.getMmapedOffset()));
                }
                {
                    AEMU_SCOPED_TRACE("updateColorBuffer");
                    rcEnc->rcUpdateColorBufferDMA(rcEnc, cb.hostHandle,
                            0, 0, cb.width, cb.height,
                            cb.glFormat, cb.glType,
                            const_cast<char*>(bitsToSend),
                            sizeToSend);
                }
            }
        }
    }

    /* BufferUsage bits that must be zero */
    static constexpr uint64_t kReservedUsage =
        (one64 << 10)
        | (one64 << 13)
        | (one64 << 19)
        | (one64 << 21)
        | ones(25, 27) /* bits 25-27 must be zero and are reserved for future versions */
        | ones(32, 47); /* bits 32-47 must be zero and are reserved for future versions */

    bool isSupportedImpl(const IMapper::BufferDescriptorInfo& descriptor) const {
        if (!descriptor.width) { RETURN(false); }
        if (!descriptor.height) { RETURN(false); }
        if (descriptor.layerCount != 1) { RETURN(false); }

        const uint64_t usage64 = descriptor.usage;
        if (usage64 & kReservedUsage) {
            RETURN(false);
        }

        const uint32_t usage = usage64;

        switch (descriptor.format) {
        case PixelFormat::RGBA_8888:
        case PixelFormat::RGBX_8888:
        case PixelFormat::BGRA_8888:
        case PixelFormat::RGB_565:
        case PixelFormat::RGBA_FP16:
        case PixelFormat::RGBA_1010102:
        case PixelFormat::YV12:
        case PixelFormat::YCBCR_420_888:
        case PixelFormat::YCBCR_P010:
            RETURN(true);

        case PixelFormat::IMPLEMENTATION_DEFINED:
            RETURN(false);

        case PixelFormat::RGB_888:
        case PixelFormat::YCRCB_420_SP:
        case PixelFormat::RAW16:
        case PixelFormat::Y16:
        case PixelFormat::BLOB:
            RETURN(!needGpuBuffer(usage));

        default:
            if (static_cast<int>(descriptor.format) == kOMX_COLOR_FormatYUV420Planar) {
                return (usage & BufferUsage::VIDEO_DECODER) != 0;
            }

            RETURN(false);
        }
    }

    Error3 validateBufferSizeImpl(const cb_handle_t& /*cb*/,
                                  const BufferDescriptorInfo& /*descriptor*/,
                                  uint32_t /*stride*/) {
        RETURN(Error3::NONE);
    }

    HostConnectionSession getHostConnectionSession() const {
        return HostConnectionSession(m_hostConn.get());
    }

    static void encodeBufferDescriptorInfo(const BufferDescriptorInfo& d,
                                           hidl_vec<uint32_t>* raw) {
        raw->resize(5);

        (*raw)[0] = d.width;
        (*raw)[1] = d.height;
        (*raw)[2] = d.layerCount;
        (*raw)[3] = static_cast<uint32_t>(d.format);
        (*raw)[4] = d.usage & UINT32_MAX;
    }

    uint64_t getMmapedPhysAddr(uint64_t offset) const {
        return m_physAddrToOffset + offset;
    }

    std::unique_ptr<HostConnection> m_hostConn;
    uint64_t m_physAddrToOffset;
};
}  // namespace

extern "C" IMapper3* HIDL_FETCH_IMapper(const char* /*name*/) {
    return new GoldfishMapper;
}
