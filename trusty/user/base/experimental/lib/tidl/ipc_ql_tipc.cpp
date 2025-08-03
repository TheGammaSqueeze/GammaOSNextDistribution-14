/*
 * Copyright (C) 2022 The Android Open Source Project
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

#define TLOG_TAG "ipc_ql_tipc"

#include <lib/tidl/tidl.h>
#include <trusty/sysdeps.h>
#include <trusty/trusty_ipc.h>
#include <trusty/trusty_log.h>
#include <trusty/util.h>

#define TIDL_CHANNEL_MAX 8

static struct trusty_ipc_dev* tidl_dev = (struct trusty_ipc_dev*)NULL;
static struct trusty_ipc_chan tidl_channels[TIDL_CHANNEL_MAX];

extern "C" __attribute__((weak)) int __cxa_atexit(void (*destructor)(void*),
                                                  void* arg,
                                                  void* dso) {
    (void)arg;
    (void)dso;
    return 0;
}

__attribute__((weak)) void operator delete(void* ptr) noexcept {
    if (ptr)
        trusty_free(ptr);
}
namespace tidl {

int handle::reset() {
    if (mFd == INVALID_IPC_HANDLE) {
        return NO_ERROR;
    }
    int rc = tidl_chan_close(mFd);
    mFd = INVALID_IPC_HANDLE;
    return rc;
}

int handle::reset(Handle fd) {
    if (mFd == INVALID_IPC_HANDLE) {
        mFd = fd;
        return NO_ERROR;
    }
    int rc = tidl_chan_close(mFd);
    if (rc != NO_ERROR) {
        mFd = INVALID_IPC_HANDLE;
        return rc;
    }
    mFd = fd;
    return rc;
}
Handle handle::get() {
    return mFd;
}
namespace ipc {

static __inline__ struct trusty_ipc_chan* tidl_get_channel(handle_t fd) {
    TLOGD("looking for channel %u\n", fd);
    for (int i = 0; i < TIDL_CHANNEL_MAX; i++) {
        TLOGD("channel[%d]=%u\n", i, tidl_channels[i].handle);
        if (fd == tidl_channels[i].handle) {
            return &tidl_channels[i];
        }
    }
    if (fd == INVALID_IPC_HANDLE) {
        TLOGE("tidl_get_channel failed, no available channel\n");
    } else {
        TLOGE("tidl_get_channel failed, channel not found!\n");
    }
    return (struct trusty_ipc_chan*)NULL;
}

static __inline__ void binder_init_channels() {
    TLOGD("init channels\n");
    for (int i = 0; i < TIDL_CHANNEL_MAX; i++) {
        trusty_ipc_chan_init(&tidl_channels[i], tidl_dev);
    }
}

static __inline__ void binder_check_channels() {
    TLOGD("check that all channels have been closed\n");
    for (int i = 0; i < TIDL_CHANNEL_MAX; i++) {
        assert(tidl_channels[i].handle == INVALID_IPC_HANDLE);
    }
}

/* TODO: create a TIDL sysdeps mapping to allow non lk-based bootloaders
 * to map errors into their own system. For now tidl returns lk errors.
 */
static int to_tidl_err(int err) {
    switch (err) {
    case TRUSTY_ERR_NONE:
        return NO_ERROR;

    case TRUSTY_ERR_GENERIC:
        return ERR_GENERIC;

    case TRUSTY_ERR_NOT_SUPPORTED:
        return ERR_NOT_SUPPORTED;

    case TRUSTY_ERR_NO_MEMORY:
        return ERR_NO_MEMORY;

    case TRUSTY_ERR_INVALID_ARGS:
        return ERR_INVALID_ARGS;

    case TRUSTY_ERR_SECOS_ERR:
        return ERR_GENERIC;

    case TRUSTY_ERR_MSG_TOO_BIG:
        return ERR_BAD_LEN;

    case TRUSTY_ERR_NO_MSG:
        return ERR_GENERIC;

    case TRUSTY_ERR_CHANNEL_CLOSED:
        return ERR_CHANNEL_CLOSED;

    case TRUSTY_ERR_SEND_BLOCKED:
        return ERR_GENERIC;

    default:
        return err;
    }
}

extern "C" int tidl_init(struct trusty_ipc_dev* dev) {
    assert(dev);
    if (tidl_dev) {
        assert(tidl_dev == dev);
        return NO_ERROR;
    }
    tidl_dev = dev;
    binder_init_channels();
    return NO_ERROR;
}

/*
 * Shutdown binder clients
 *
 */
extern "C" void tidl_shutdown(void) {
    tidl_dev = (struct trusty_ipc_dev*)NULL;
    binder_check_channels();
}

extern "C" int tidl_chan_close(handle_t fd) {
    struct trusty_ipc_chan* chan = tidl_get_channel(fd);
    assert(chan);
    TLOGD("tidl_chan_close fd (%u)\n", fd);
    int rc = trusty_ipc_close(chan);
    if (rc < 0) {
        TLOGE("trusty_ipc_close error (%d)\n", rc);
    }
    return to_tidl_err(rc);
}

int connect(const char* path, uint32_t flags, tidl::handle& out_fd) {
    (void)flags;
    if (!tidl_dev) {
        TLOGE("trusty::aidl::ipc::connect ERROR: device not initialised.\n");
        return ERR_GENERIC;
    }
    struct trusty_ipc_chan* chan = tidl_get_channel(INVALID_IPC_HANDLE);
    if (!chan) {
        return ERR_GENERIC;
    }
    assert(chan->handle == INVALID_IPC_HANDLE);

    TLOGD("Connecting to %s\n", path);
    int rc = trusty_ipc_connect(chan, path, true);
    if (rc < 0) {
        TLOGE("failed (%d) to connect to '%s'\n", rc, path);
        return to_tidl_err(rc);
    }

    TLOGD("channel (%u)\n", chan->handle);
    out_fd.reset(chan->handle);
    return NO_ERROR;
}

static int send_iovs(handle_t fd,
                     const struct trusty_ipc_iovec* iovs,
                     size_t iovs_cnt,
                     handle_t* handles,
                     uint32_t num_handles) {
    assert(num_handles == 0);
    struct trusty_ipc_chan* chan = tidl_get_channel(fd);
    assert(chan);
    trusty_assert(chan->dev);
    trusty_assert(chan->handle);
    int rc = trusty_ipc_send(chan, iovs, iovs_cnt, true);
    return to_tidl_err(rc);
}

int send(handle_t fd,
         const void* hdr,
         size_t hdr_len,
         handle_t* handles,
         uint32_t num_handles) {
    struct trusty_ipc_iovec iovs[] = {
            {
                    .base = (void*)hdr,
                    .len = hdr_len,
            },
    };
    return send_iovs(fd, iovs, 1, handles, num_handles);
}

static int recv_iovs(handle_t fd,
                     struct trusty_ipc_iovec* iovs,
                     size_t iovs_cnt,
                     handle_t* handles,
                     uint32_t num_handles) {
    assert(num_handles == 0);
    struct trusty_ipc_chan* chan = tidl_get_channel(fd);
    assert(chan);
    trusty_assert(chan->dev);
    trusty_assert(chan->handle);
    int rc = trusty_ipc_recv(chan, iovs, iovs_cnt, true);
    return to_tidl_err(rc);
}

int recv(handle_t fd,
         size_t min_sz,
         void* buf,
         size_t buf_sz,
         handle_t* handles,
         uint32_t num_handles) {
    (void)min_sz;
    struct trusty_ipc_iovec iovs[] = {
            {
                    .base = (void*)buf,
                    .len = buf_sz,
            },
    };
    return recv_iovs(fd, iovs, 1, handles, num_handles);
}

int send(handle_t fd,
         const void* hdr,
         size_t hdr_len,
         const void* payload1,
         size_t payload1_len,
         handle_t* handles,
         uint32_t num_handles) {
    struct trusty_ipc_iovec iovs[] = {
            {
                    .base = (void*)hdr,
                    .len = hdr_len,
            },
            {
                    .base = (void*)payload1,
                    .len = payload1_len,
            },
    };
    return send_iovs(fd, iovs, 2, handles, num_handles);
}

int recv(handle_t fd,
         size_t min_sz,
         void* buf1,
         size_t buf1_sz,
         void* buf2,
         size_t buf2_sz,
         handle_t* handles,
         uint32_t num_handles) {
    assert(num_handles == 0);
    struct trusty_ipc_iovec iovs[] = {
            {
                    .base = buf1,
                    .len = buf1_sz,
            },
            {
                    .base = buf2,
                    .len = buf2_sz,
            },
    };
    return recv_iovs(fd, iovs, 2, handles, num_handles);
}

int send(handle_t fd,
         const void* hdr,
         size_t hdr_len,
         const void* payload1,
         size_t payload1_len,
         const void* payload2,
         size_t payload2_len,
         handle_t* handles,
         uint32_t num_handles) {
    assert(num_handles == 0);
    struct trusty_ipc_iovec iovs[] = {
            {
                    .base = (void*)hdr,
                    .len = hdr_len,
            },
            {
                    .base = (void*)payload1,
                    .len = payload1_len,
            },
            {
                    .base = (void*)payload2,
                    .len = payload2_len,
            },
    };
    return send_iovs(fd, iovs, 3, handles, num_handles);
}

int recv(handle_t fd,
         size_t min_sz,
         void* buf1,
         size_t buf1_sz,
         void* buf2,
         size_t buf2_sz,
         void* buf3,
         size_t buf3_sz,
         handle_t* handles,
         uint32_t num_handles) {
    assert(num_handles == 0);
    struct trusty_ipc_iovec iovs[] = {
            {
                    .base = buf1,
                    .len = buf1_sz,
            },
            {
                    .base = buf2,
                    .len = buf2_sz,
            },
            {
                    .base = buf3,
                    .len = buf3_sz,
            },
    };
    return recv_iovs(fd, iovs, 3, handles, num_handles);
}

int wait_for_msg(handle_t chan) {
    (void)chan;
    return NO_ERROR;
}

}  // namespace ipc
}  // namespace tidl
