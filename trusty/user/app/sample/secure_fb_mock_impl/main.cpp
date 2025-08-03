/*
 * Copyright 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define TLOG_TAG "secure_fb_impl"

#include <lib/secure_dpu/secure_dpu.h>
#include <lib/secure_fb/srv/dev.h>
#include <lib/secure_fb/srv/srv.h>
#include <lib/tipc/tipc.h>
#include <lk/err_ptr.h>
#include <lk/macros.h>
#include <sys/auxv.h>
#include <sys/mman.h>
#include <trusty/memref.h>
#include <trusty_ipc.h>
#include <trusty_log.h>

#include <tuple>

#define PAGE_SIZE() (getauxval(AT_PAGESZ))

static constexpr const uint32_t kDeviceWidth = 400;
static constexpr const uint32_t kDeviceHeight = 800;
static constexpr const uint32_t kFbCount = 1;
static constexpr const uint32_t kFbId = 0xdeadbeef;

static handle_t secure_dpu_handle = INVALID_IPC_HANDLE;

class SecureFbMockImpl {
private:
    struct FbDbEntry {
        secure_fb_info fb_info;
        secure_dpu_buf_info buf_info;
        handle_t handle = INVALID_IPC_HANDLE;
        ptrdiff_t offset;
    };

    FbDbEntry fb_db_[kFbCount];

public:
    ~SecureFbMockImpl() {
        if (fb_db_[0].handle != INVALID_IPC_HANDLE) {
            close(fb_db_[0].handle);
        }
        int rc = munmap(fb_db_[0].fb_info.buffer, fb_db_[0].fb_info.size);
        if (rc < 0) {
            TLOGE("Failed to do munmap\n");
        }
        if (secure_dpu_release_buffer(&fb_db_[0].buf_info) < 0) {
            TLOGE("Failed to free framebuffer\n");
        }
        if (secure_dpu_stop_secure_display(secure_dpu_handle) < 0) {
            TLOGE("Failed to stop secure_display\n");
        }
    }

    int Init(uint32_t width, uint32_t height) {
        if (secure_dpu_start_secure_display(secure_dpu_handle) < 0) {
            TLOGE("Failed to start secure_display\n");
            return SECURE_FB_ERROR_UNINITIALIZED;
        }

        uint32_t fb_size =
                round_up(sizeof(uint32_t) * width * height, PAGE_SIZE());
        secure_dpu_buf_info buf_info;

        if (secure_dpu_allocate_buffer(secure_dpu_handle,
                                       (size_t)fb_size,
                                       &buf_info) < 0) {
            TLOGE("Failed to allocate framebuffer of size: %u\n", fb_size);
            return SECURE_FB_ERROR_MEMORY_ALLOCATION;
        }
        void* fb_base = mmap(0, (size_t)fb_size, PROT_READ | PROT_WRITE, 0,
                             buf_info.handle, 0);
        if (fb_base == MAP_FAILED) {
            TLOGE("Error when calling mmap()\n");
            return SECURE_FB_ERROR_SHARED_MEMORY;
        }

        /*
         * Create a handle for the buffer by which it can be passed to the TUI
         * app for rendering.
         */
        int handle =
                memref_create(fb_base, fb_size, PROT_READ | PROT_WRITE);
        if (handle < 0) {
            TLOGE("Failed to create memref (%d)\n", handle);
            return SECURE_FB_ERROR_SHARED_MEMORY;
        }

        fb_db_[0] = {
                .fb_info = {.buffer = (uint8_t*)fb_base,
                            .size = fb_size,
                            .pixel_stride = 4,
                            .line_stride = 4 * width,
                            .width = width,
                            .height = height,
                            .pixel_format = TTUI_PF_RGBA8,
                            .rotation = TTUI_DRAW_ROTATION_0,
                            .display_index = 0},
                .buf_info = buf_info,
                .handle = handle,
        };

        return SECURE_FB_ERROR_OK;
    }

    int GetFbs(struct secure_fb_impl_buffers* buffers) {
        *buffers = {
                .num_fbs = 1,
                .fbs[0] =
                        {
                                .buffer_id = kFbId,
                                .handle_index = 0,
                                .fb_info = fb_db_[0].fb_info,
                        },
                .num_handles = 1,
                .handles[0] = fb_db_[0].handle,
        };
        return SECURE_FB_ERROR_OK;
    }

    int Display(uint32_t buffer_id) {
        if (buffer_id != kFbId) {
            return SECURE_FB_ERROR_INVALID_REQUEST;
        }

        /* This is a no-op in the mock case. */
        return SECURE_FB_ERROR_OK;
    }
};

static secure_fb_handle_t secure_fb_impl_init() {
    auto sfb = new SecureFbMockImpl();
    auto rc = sfb->Init(kDeviceWidth, kDeviceHeight);
    if (rc != SECURE_FB_ERROR_OK) {
        delete sfb;
        return NULL;
    }
    return sfb;
}

static int secure_fb_impl_get_fbs(secure_fb_handle_t sfb_handle,
                                  struct secure_fb_impl_buffers* buffers) {
    SecureFbMockImpl* sfb = reinterpret_cast<SecureFbMockImpl*>(sfb_handle);
    return sfb->GetFbs(buffers);
}

static int secure_fb_impl_display_fb(secure_fb_handle_t sfb_handle,
                                     uint32_t buffer_id) {
    SecureFbMockImpl* sfb = reinterpret_cast<SecureFbMockImpl*>(sfb_handle);
    return sfb->Display(buffer_id);
}

static int secure_fb_impl_release(secure_fb_handle_t sfb_handle) {
    SecureFbMockImpl* sfb = reinterpret_cast<SecureFbMockImpl*>(sfb_handle);
    delete sfb;
    return SECURE_FB_ERROR_OK;
}

static const struct secure_fb_impl_ops ops = {
        .init = secure_fb_impl_init,
        .get_fbs = secure_fb_impl_get_fbs,
        .display_fb = secure_fb_impl_display_fb,
        .release = secure_fb_impl_release,
};

int main(void) {
    int rc;
    struct tipc_hset* hset;

    hset = tipc_hset_create();
    if (IS_ERR(hset)) {
        TLOGE("failed (%d) to create handle set\n", PTR_ERR(hset));
        return PTR_ERR(hset);
    }

    rc = add_secure_dpu_service(hset, &secure_dpu_handle);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to initialize secure_dpu mock service\n", rc);
        return rc;
    }

    rc = add_secure_fb_service(hset, &ops, 1);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to initialize secure_fb mock service\n", rc);
        return rc;
    }

    return tipc_run_event_loop(hset);
}
