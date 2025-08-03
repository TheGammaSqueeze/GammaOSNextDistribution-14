/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#if defined(__TRUSTY__) and !defined(__QL_TIPC__)
#include <assert.h>
#include <lib/tidl/android-base/unique_fd.h>
#include <lib/tipc/tipc.h>
#include <lib/tipc/tipc_srv.h>
#include <lk/compiler.h>
#include <lk/err_ptr.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <sys/types.h>
#include <trusty_ipc.h>
#include <uapi/trusty_uuid.h>

#include <array>
#include <type_traits>
#include <utility>
#endif

#if defined(__QL_TIPC__)
#include <lib/tidl/tidl_ql_tipc.h>
#include <trusty/sysdeps.h>
#include <trusty/trusty_ipc.h>
#endif

#define TIDL_PACKED_ATTR __PACKED

namespace tidl {

#if !defined(__QL_TIPC__)
template <typename T, size_t N>
using Array = std::array<T, N>;

template <typename T>
auto&& move(T&& x) {
    return std::move(x);
}
#endif

using Handle = handle_t;

#if defined(__QL_TIPC__)
class handle {
public:
    handle() : mFd(INVALID_IPC_HANDLE) {}
    handle(Handle fd) : mFd(fd) {}
    ~handle() { (void)reset(); }
    bool ok() const { return mFd != INVALID_IPC_HANDLE; }
    int reset();
    int reset(Handle fd);
    Handle get();

private:
    Handle mFd;
};
#endif  // #if defined(__QL_TIPC__)

struct TIDL_PACKED_ATTR RequestHeader {
    uint32_t cmd;
    uint32_t resp_payload_size;
};

struct TIDL_PACKED_ATTR ResponseHeader {
    uint32_t cmd;
    uint32_t resp_payload_size;
    int32_t rc;
};

#if !defined(__QL_TIPC__)
class TIDL_PACKED_ATTR ParcelFileDescriptor {
public:
    android::base::unique_fd handle;

    // Handle methods
    static constexpr uint32_t num_handles = 1;
    void send_handles(handle_t*& hptr) { *hptr++ = handle.release(); }
    void recv_handles(handle_t*& hptr) { handle.reset(*hptr++); }

private:
    // struct trusty_shm from Android has 2 32-bit fields
    // so we reserve the space for the second one here
    __UNUSED uint32_t reserved;
};
STATIC_ASSERT(sizeof(ParcelFileDescriptor) == 2 * sizeof(uint32_t));
#endif

// Default implementation for all types without handles
template <typename T, typename = void>
class HandleOps {
public:
    static constexpr uint32_t num_handles = 0;
    static void send_handles(void*, handle_t*& hptr) {}
    static void recv_handles(void*, handle_t*& hptr) {}
};

// HasHandleMembers<T> is equal to void for all types T
// that have the 3 members we need for HandleOps, and
// doesn't exist for any other types (triggering SFINAE below)
#if !defined(__QL_TIPC__)
template <typename T>
using HasHandleMembers = std::void_t<
        decltype(T::num_handles),
        decltype(std::declval<T>().send_handles(std::declval<handle_t*&>())),
        decltype(std::declval<T>().recv_handles(std::declval<handle_t*&>()))>;

// Specialization for types that implement their own handle methods
template <typename T>
class HandleOps<T, HasHandleMembers<T>> {
public:
    static constexpr uint32_t num_handles = T::num_handles;
    static void send_handles(void* x, handle_t*& hptr) {
        reinterpret_cast<T*>(x)->send_handles(hptr);
    }
    static void recv_handles(void* x, handle_t*& hptr) {
        reinterpret_cast<T*>(x)->recv_handles(hptr);
    }
};
#endif

class Payload {
public:
    Payload() : mData(nullptr), mSize(0) {}
    Payload(uint8_t* data, uint32_t size) : mData(data), mSize(size) {}
    Payload(const Payload&) = delete;
    Payload& operator=(const Payload&) = delete;

    Payload(Payload&& other) : mData(other.mData), mSize(other.mSize) {
        other.reset();
    }

    Payload& operator=(Payload&& other) {
        mData = other.mData;
        mSize = other.mSize;
        other.reset();
        return *this;
    }

    const uint8_t* data() const { return mData; }

    uint8_t* data() { return mData; }

    uint32_t size() const { return mSize; }

    void resize(uint32_t size) { mSize = size; }

private:
    uint8_t* mData;
    uint32_t mSize;

    void reset() {
        mData = nullptr;
        mSize = 0;
    }
};

template <uint32_t S>
class FixedPayload {
    static const uint32_t mSize = S;

public:
    FixedPayload() {}
    FixedPayload(uint8_t* data) { memcpy(mData, data, S); }

    FixedPayload(const FixedPayload<S>& payload) {
        memcpy(mData, payload.data(), S);
    }

    FixedPayload& operator=(const FixedPayload<S>& payload) {
        memcpy(mData, payload.data(), S);
        return *this;
    }

    FixedPayload<S>& operator=(FixedPayload<S>&& other) {
        memcpy(mData, other.mData, S);
        return *this;
    }

    const uint8_t* data() const { return mData; }

    uint8_t* data() { return mData; }

    uint32_t size() const { return mSize; }

private:
    uint8_t mData[S];
};

#if !defined(__QL_TIPC__)
class Service {
public:
    using Port = struct tipc_port;
    using PortAcl = struct tipc_port_acl;
    using Ops = struct tipc_srv_ops;
    using HandleSet = struct tipc_hset*;

    Service() = delete;
    Service(const char*,
            const char* port_name,
            uint32_t msg_max_size,
            const PortAcl* acl,
            const Ops* ops)
            : mPort(), mOpsPtr(ops) {
        mPort.name = port_name;
        mPort.msg_max_size = msg_max_size;
        mPort.msg_queue_len = 1;
        mPort.acl = acl;
        mPort.priv = this;
    }

    void set_max_channels(uint32_t max_chan_cnt) {
        mMaxChannels = max_chan_cnt;
    }

    int add_service(HandleSet hset) {
        return tipc_add_service(hset, &mPort, 1, mMaxChannels, mOpsPtr);
    }

    int run_service(void) {
        HandleSet hset = tipc_hset_create();
        if (IS_ERR(hset)) {
            return PTR_ERR(hset);
        }
        int rc = add_service(hset);
        if (rc < 0) {
            return rc;
        }
        return tipc_run_event_loop(hset);
    }

protected:
    virtual int get_payload_buffer(Payload&, uint32_t size, bool) {
        if (!size) {
            return NO_ERROR;
        }
        return ERR_NOT_IMPLEMENTED;
    }

    virtual void free_payload_buffer(Payload) {}

    Port mPort;
    uint32_t mMaxChannels = 1;

private:
    const Ops* mOpsPtr;
};
#endif  // #if !defined(__QL_TIPC__)

namespace ipc {

#if !defined(__QL_TIPC__)
int connect(const char* path, uint32_t flags, android::base::unique_fd& out_fd);
#else
int connect(const char* path, uint32_t flags, ::tidl::handle& out_fd);
#endif

int send(handle_t chan,
         const void* buf,
         size_t len,
         handle_t* handles,
         uint32_t num_handles);
int recv(handle_t chan,
         size_t min_sz,
         void* buf,
         size_t buf_sz,
         handle_t* handles,
         uint32_t num_handles);
int send(handle_t chan,
         const void* hdr,
         size_t hdr_len,
         const void* payload1,
         size_t payload1_len,
         handle_t* handles,
         uint32_t num_handles);
int recv(handle_t chan,
         size_t min_sz,
         void* buf1,
         size_t buf1_sz,
         void* buf2,
         size_t buf2_sz,
         handle_t* handles,
         uint32_t num_handles);
int send(handle_t chan,
         const void* hdr,
         size_t hdr_len,
         const void* payload1,
         size_t payload1_len,
         const void* payload2,
         size_t payload2_len,
         handle_t* handles,
         uint32_t num_handles);
int recv(handle_t chan,
         size_t min_sz,
         void* buf1,
         size_t buf1_sz,
         void* buf2,
         size_t buf2_sz,
         void* buf3,
         size_t buf3_sz,
         handle_t* handles,
         uint32_t num_handles);
int wait_for_msg(handle_t chan);
}  // namespace ipc

}  // namespace tidl
