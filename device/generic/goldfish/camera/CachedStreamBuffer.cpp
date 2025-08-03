/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <algorithm>
#include <inttypes.h>

#include <aidlcommonsupport/NativeHandle.h>
#include <sync/sync.h>
#include <ui/GraphicBufferMapper.h>

#include "CachedStreamBuffer.h"
#include "debug.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {

using base::unique_fd;

namespace {
const native_handle_t* importAidlNativeHandle(const NativeHandle& anh) {
    typedef decltype(native_handle_t::version) T;
    std::vector<T> data(sizeof(native_handle_t) / sizeof(T) + anh.fds.size() +
        anh.ints.size());

    native_handle_t* h = native_handle_init(
        reinterpret_cast<char*>(&data[0]), anh.fds.size(), anh.ints.size());
    std::transform(anh.fds.begin(), anh.fds.end(), &h->data[0],
                   [](const ndk::ScopedFileDescriptor& sfd){ return sfd.get(); });
    std::copy(anh.ints.begin(), anh.ints.end(), &h->data[anh.fds.size()]);

    const native_handle_t* importedH = nullptr;
    if (GraphicBufferMapper::get().importBufferNoValidate(h, &importedH) != NO_ERROR) {
        return FAILURE(nullptr);
    }

    return importedH;
}

unique_fd importAidlNativeHandleFence(const NativeHandle& nh) {
    const size_t nfds = nh.fds.size();
    const size_t nints = nh.ints.size();

    if (nints == 0) {
        switch (nfds) {
        case 0:
            return unique_fd();

        case 1: {
                const int fd = fcntl(nh.fds.front().get(), F_DUPFD_CLOEXEC, 0);
                if (fd < 0) {
                    return FAILURE_V(unique_fd(), "fcntl failed with %s (%d)",
                                     strerror(errno), errno);
                }

                return unique_fd(fd);
            }

        default:
            return FAILURE_V(unique_fd(), "unexpected fence shape, nfds=%zu, must "
                             "be one", nfds);
        }
    } else {
        return unique_fd();
    }
}

NativeHandle moveFenceToAidlNativeHandle(unique_fd fence) {
    if (!fence.ok()) {
        return {};
    }

    typedef decltype(native_handle_t::version) T;
    T on_stack[sizeof(native_handle_t) / sizeof(T) + 1];

    native_handle_t* nh = native_handle_init(
        reinterpret_cast<char*>(&on_stack[0]), 1, 0);
    nh->data[0] = fence.release();
    return makeToAidl(nh);
}
}  //namespace

CachedStreamBuffer::CachedStreamBuffer(const StreamBuffer& sb)
        : mBuffer(importAidlNativeHandle(sb.buffer))
        , mBufferId(sb.bufferId)
        , mAcquireFence(importAidlNativeHandleFence(sb.acquireFence))
        , mStreamId(sb.streamId) {
    LOG_ALWAYS_FATAL_IF(!mBuffer);
    LOG_ALWAYS_FATAL_IF(!mBufferId);
    LOG_ALWAYS_FATAL_IF(mStreamId < 0);
}

CachedStreamBuffer::CachedStreamBuffer(CachedStreamBuffer&& rhs) noexcept
        : mBuffer(std::exchange(rhs.mBuffer, nullptr))
        , mBufferId(std::exchange(rhs.mBufferId, 0))
        , mAcquireFence(std::exchange(rhs.mAcquireFence, {}))
        , mStreamId(std::exchange(rhs.mStreamId, -1))
        , mProcessed(std::exchange(rhs.mProcessed, true)) {
    LOG_ALWAYS_FATAL_IF(!mBuffer);
    LOG_ALWAYS_FATAL_IF(!mBufferId);
    LOG_ALWAYS_FATAL_IF(mStreamId < 0);
}

CachedStreamBuffer::~CachedStreamBuffer() {
    LOG_ALWAYS_FATAL_IF(!mProcessed);
    if (mStreamId >= 0) {
        LOG_ALWAYS_FATAL_IF(!mBuffer);
        LOG_ALWAYS_FATAL_IF(GraphicBufferMapper::get().freeBuffer(mBuffer) != NO_ERROR);
    }
}

void CachedStreamBuffer::importAcquireFence(const NativeHandle& fence) {
    LOG_ALWAYS_FATAL_IF(!mProcessed);
    mAcquireFence = importAidlNativeHandleFence(fence);
    mProcessed = false;
}

bool CachedStreamBuffer::waitAcquireFence(const unsigned timeoutMs) {
    if (mAcquireFence.ok()) {
        if (sync_wait(mAcquireFence.get(), timeoutMs)) {
            return FAILURE(false);
        } else {
            mAcquireFence.reset();
            return true;
        }
    } else {
        return true;
    }
}

StreamBuffer CachedStreamBuffer::finish(const bool success) {
    using aidl::android::hardware::camera::device::BufferStatus;
    LOG_ALWAYS_FATAL_IF(mProcessed);
    LOG_ALWAYS_FATAL_IF(!mBufferId);
    LOG_ALWAYS_FATAL_IF(mStreamId < 0);

    StreamBuffer sb;
    sb.streamId = mStreamId;
    sb.bufferId = mBufferId;
    sb.status = success ? BufferStatus::OK : BufferStatus::ERROR;
    sb.releaseFence = moveFenceToAidlNativeHandle(std::move(mAcquireFence));

    mProcessed = true;
    return sb;
}

}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
