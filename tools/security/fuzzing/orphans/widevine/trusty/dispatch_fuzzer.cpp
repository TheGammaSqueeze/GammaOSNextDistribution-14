/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <BufferAllocator/BufferAllocator.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <trusty/coverage/coverage.h>
#include <trusty/fuzz/counters.h>
#include <trusty/fuzz/utils.h>
#include <trusty/tipc.h>
#include <unistd.h>
#include <iostream>

using android::trusty::coverage::CoverageRecord;
using android::trusty::fuzz::ExtraCounters;
using android::trusty::fuzz::TrustyApp;

#define countof(arr) (sizeof(arr) / sizeof(arr[0]))

#define TIPC_DEV "/dev/trusty-ipc-dev0"
#define WIDEVINE_PORT "com.android.trusty.widevine"
#define WIDEVINE_MODULE_NAME "widevine.syms.elf"

#define WV_IPC_BUFFER_SIZE (32)
#define WV_MESSAGE_BUFFER_SIZE (32 * 1024)
#define WV_SHARED_BUFFER_SIZE (16 * 1024 * 1024)

struct wv_ipc_header {
    uint16_t tag;
};

enum wv_tag : uint16_t {
    WV_TAG_ACK = 0u,
    WV_TAG_BIND = 1u,
    WV_TAG_WIDEVINE = 2u,
};

struct bind_message {
    uint32_t protocol_version;
    uint32_t message_buffer_size;
    uint32_t shared_buffer_size;
};

struct widevine_message {
    uint32_t message_size;
};

/* Widevine TA's UUID is 08d3ed40-bde2-448c-a91d-75f1989c57ef */
static struct uuid widevine_uuid = {
        0x08d3ed40,
        0xbde2,
        0x448c,
        {0xa9, 0x1d, 0x75, 0xf1, 0x98, 0x9c, 0x57, 0xef},
};

static android::base::unique_fd wv_msg_buf_fd;
static void* wv_msg_buf_base;

static android::base::unique_fd wv_shared_buf_fd;
static void* wv_shared_buf_base;

static TrustyApp trusty_app(TIPC_DEV, WIDEVINE_PORT);
static CoverageRecord record(TIPC_DEV, &widevine_uuid, WIDEVINE_MODULE_NAME);

extern "C" int LLVMFuzzerInitialize(int* /* argc */, char*** /* argv */) {
    auto ret = trusty_app.Connect();
    if (!ret.ok()) {
        std::cerr << ret.error() << std::endl;
        exit(-1);
    }

    ret = record.Open();
    if (!ret.ok()) {
        std::cerr << ret.error() << std::endl;
        exit(-1);
    }

    BufferAllocator allocator;

    wv_msg_buf_fd.reset(allocator.Alloc(kDmabufSystemHeapName, WV_MESSAGE_BUFFER_SIZE));
    if (wv_msg_buf_fd < 0) {
        std::cerr << "Failed to allocate message buffer." << std::endl;
        exit(-1);
    }

    wv_msg_buf_base = mmap(0, WV_MESSAGE_BUFFER_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED,
                           wv_msg_buf_fd, 0);
    if (wv_msg_buf_base == MAP_FAILED) {
        std::cerr << "Failed to mmap() message buffer." << std::endl;
        exit(-1);
    }

    wv_shared_buf_fd.reset(allocator.Alloc(kDmabufSystemHeapName, WV_SHARED_BUFFER_SIZE));
    if (wv_shared_buf_fd < 0) {
        std::cerr << "Failed to allocate shared buffer." << std::endl;
        exit(-1);
    }

    wv_shared_buf_base = mmap(0, WV_SHARED_BUFFER_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED,
                              wv_shared_buf_fd, 0);
    if (wv_shared_buf_base == MAP_FAILED) {
        std::cerr << "Failed to mmap() shared buffer." << std::endl;
        exit(-1);
    }

    return 0;
}

static bool Bind() {
    wv_ipc_header hdr = {
        .tag = WV_TAG_BIND,
    };
    bind_message args = {
        .protocol_version = 0,
        .message_buffer_size = WV_MESSAGE_BUFFER_SIZE,
        .shared_buffer_size = WV_SHARED_BUFFER_SIZE,
    };
    iovec iov[] = {
        {
            .iov_base = &hdr,
            .iov_len = sizeof(hdr),
        },
        {
            .iov_base = &args,
            .iov_len = sizeof(args),
        },
    };
    trusty_shm handles[] = {
        {
            .fd = wv_msg_buf_fd,
            .transfer = TRUSTY_SHARE,
        },
        {
            .fd = wv_shared_buf_fd,
            .transfer = TRUSTY_SHARE,
        },
    };
    int chan = *trusty_app.GetRawFd();

    int rc = tipc_send(chan, iov, countof(iov), handles, countof(handles));
    if (rc != static_cast<int>(sizeof(hdr) + sizeof(args))) {
        return false;
    }

    rc = read(chan, &hdr, sizeof(hdr));
    if (rc != static_cast<int>(sizeof(hdr))) {
        return false;
    }

    return true;
}

static bool Msg(const uint8_t* data, size_t size) {
    size = std::min((size_t)WV_MESSAGE_BUFFER_SIZE, size);
    wv_ipc_header hdr = {
        .tag = WV_TAG_WIDEVINE,
    };
    widevine_message args = {
        .message_size = static_cast<uint32_t>(size),
    };
    iovec iov[] = {
        {
            .iov_base = &hdr,
            .iov_len = sizeof(hdr),
        },
        {
            .iov_base = &args,
            .iov_len = sizeof(args),
        },
    };
    int chan = *trusty_app.GetRawFd();

    memset(wv_msg_buf_base, 0, WV_MESSAGE_BUFFER_SIZE);
    memcpy(wv_msg_buf_base, data, size);

    int rc = tipc_send(chan, iov, countof(iov), NULL, 0);
    if (rc != static_cast<int>(sizeof(hdr) + sizeof(args))) {
        return false;
    }

    rc = readv(chan, iov, countof(iov));
    if (rc != static_cast<int>(sizeof(hdr) + sizeof(args))) {
        return false;
    }

    return true;
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    ExtraCounters counters(&record);
    counters.Reset();

    bool success = Bind();
    if (!success) {
        android::trusty::fuzz::Abort();
    }

    success = Msg(data, size);
    if (!success) {
        android::trusty::fuzz::Abort();
    }

    // Reconnect to ensure that the service is still up.
    trusty_app.Disconnect();
    auto ret = trusty_app.Connect();
    if (!ret.ok()) {
        std::cerr << ret.error() << std::endl;
        android::trusty::fuzz::Abort();
    }

    return 0;
}
