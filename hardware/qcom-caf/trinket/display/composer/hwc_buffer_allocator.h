/*
 * Copyright (c) 2015-2020, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
* Changes from Qualcomm Innovation Center are provided under the following license:
*
* Copyright (c) 2022, 2024 Qualcomm Innovation Center, Inc. All rights reserved.
* SPDX-License-Identifier: BSD-3-Clause-Clear
*/

#ifndef __HWC_BUFFER_ALLOCATOR_H__
#define __HWC_BUFFER_ALLOCATOR_H__

#include <fcntl.h>
#include <sys/mman.h>

#include <android/hardware/graphics/allocator/4.0/IAllocator.h>
#include <android/hardware/graphics/mapper/4.0/IMapper.h>
#include <vendor/qti/hardware/display/mapper/4.0/IQtiMapper.h>

using android::hardware::graphics::allocator::V4_0::IAllocator;
using android::hardware::graphics::mapper::V4_0::IMapper;
using vendor::qti::hardware::display::mapperextensions::V1_0::IQtiMapperExtensions;

namespace sdm {

template <class Type>
inline Type ALIGN(Type x, Type align) {
  return (x + align - 1) & ~(align - 1);
}

class HWCBufferAllocator : public BufferAllocator {
 public:
  DisplayError AllocateBuffer(BufferInfo *buffer_info);
  DisplayError FreeBuffer(BufferInfo *buffer_info);
  uint32_t GetBufferSize(BufferInfo *buffer_info);

  void GetCustomWidthAndHeight(const native_handle_t *handle, int *width, int *height);
  void GetAlignedWidthAndHeight(int width, int height, int format, uint32_t alloc_type,
                                int *aligned_width, int *aligned_height);
  DisplayError GetAllocatedBufferInfo(const BufferConfig &buffer_config,
                                      AllocatedBufferInfo *allocated_buffer_info);
  DisplayError GetBufferLayout(const AllocatedBufferInfo &buf_info, uint32_t stride[4],
                               uint32_t offset[4], uint32_t *num_planes);
  int SetBufferInfo(LayerBufferFormat format, int *target, uint64_t *flags);
  DisplayError MapBuffer(const native_handle_t *handle, shared_ptr<Fence> acquire_fence, void **base_ptr);
  DisplayError UnmapBuffer(const native_handle_t *handle, int *release_fence);
  DisplayError GetHeight(void *buf, uint32_t &height);
  DisplayError GetWidth(void *buf, uint32_t &width);
  int GetUnalignedHeight(void *buf, uint32_t &height);
  int GetUnalignedWidth(void *buf, uint32_t &width);
  DisplayError GetFd(void *buf, int &fd);
  DisplayError GetAllocationSize(void *buf, uint32_t &alloc_size);
  DisplayError GetBufferId(void *buf, uint64_t &id);
  int GetFormat(void *buf, int32_t &format);
  int GetPrivateFlags(void *buf, int32_t &flags);
  DisplayError GetSDMFormat(void *buf, LayerBufferFormat &sdm_format);
  int GetBufferType(void *buf, uint32_t &buffer_type);
  int GetBufferGeometry(void *buf, int32_t &slice_width, int32_t &slice_height);

 private:
  DisplayError GetGrallocInstance();
  android::sp<IMapper> mapper_;
  android::sp<IAllocator> allocator_;
  android::sp<IQtiMapperExtensions> mapper_ext_;
};

}  // namespace sdm
#endif  // __HWC_BUFFER_ALLOCATOR_H__
