/*
* Copyright 2011 The Android Open Source Project
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

#ifndef __GRALLOC_CB_H__
#define __GRALLOC_CB_H__

#include <cutils/native_handle.h>
#include <qemu_pipe_types_bp.h>

#include <cinttypes>

const uint32_t CB_HANDLE_MAGIC_MASK = 0xFFFFFFF0;
const uint32_t CB_HANDLE_MAGIC_BASE = 0xABFABFA0;

#define CB_HANDLE_NUM_INTS(nfd) \
    ((sizeof(*this)-sizeof(native_handle_t)-nfd*sizeof(int32_t))/sizeof(int32_t))

struct cb_handle_t : public native_handle_t {
    cb_handle_t(uint32_t p_magic,
                uint32_t p_hostHandle,
                int32_t p_format,
                uint32_t p_stride,
                uint32_t p_bufSize,
                uint64_t p_mmapedOffset)
        : magic(p_magic),
          hostHandle(p_hostHandle),
          format(p_format),
          bufferSize(p_bufSize),
          stride(p_stride),
          mmapedOffsetLo(static_cast<uint32_t>(p_mmapedOffset)),
          mmapedOffsetHi(static_cast<uint32_t>(p_mmapedOffset >> 32)) {
        version = sizeof(native_handle);
    }

    uint64_t getMmapedOffset() const {
        return (uint64_t(mmapedOffsetHi) << 32) | mmapedOffsetLo;
    }

    uint32_t allocatedSize() const {
        return bufferSize;
    }

    bool isValid() const {
        return (version == sizeof(native_handle))
               && (magic & CB_HANDLE_MAGIC_MASK) == CB_HANDLE_MAGIC_BASE;
    }

    static cb_handle_t* from(void* p) {
        if (!p) { return NULL; }
        cb_handle_t* cb = static_cast<cb_handle_t*>(p);
        return cb->isValid() ? cb : NULL;
    }

    static const cb_handle_t* from(const void* p) {
        return from(const_cast<void*>(p));
    }

    static cb_handle_t* from_unconst(const void* p) {
        return from(const_cast<void*>(p));
    }

    int32_t fds[2];

    // ints
    uint32_t magic;         // magic number in order to validate a pointer
    uint32_t hostHandle;    // the host reference to this buffer
    uint32_t format;        // real internal pixel format format
    uint32_t bufferSize;
    uint32_t stride;
    uint32_t mmapedOffsetLo;
    uint32_t mmapedOffsetHi;
};

#endif //__GRALLOC_CB_H__
