/*
 * Copyright (c) 2022 Google Inc. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

#include <lib/dtb_embedded/dtb_embedded.h>
#include <lib/dtb_service/dtb_service.h>
#include <lib/ktipc/ktipc.h>
#include <libfdt.h>
#include <lk/init.h>
#include <lk/trace.h>
#include <uapi/uapi/err.h>

#define DT_PORT "com.android.kernel.device_tree"
#define DTB_PORT "com.android.trusty.kernel.device_tree.blob"

static struct ktipc_server dtb_ktipc_server =
        KTIPC_SERVER_INITIAL_VALUE(dtb_ktipc_server, "dtb_ktipc_server");

static int find_dtb_by_compatible(const char* compat,
                                  const void** dtb,
                                  size_t* dtb_size) {
    assert(dtb);
    assert(dtb_size);

    int rc;
    const void* current_dtb = NULL;
    size_t current_dtb_size = 0;
    struct dtb_embedded_iterator* iter;
    rc = dtb_embedded_iterator_new(&iter);
    if (rc != NO_ERROR) {
        return rc;
    }

    for (;;) {
        rc = dtb_embedded_iterator_next(iter, &current_dtb, &current_dtb_size);
        if (rc != NO_ERROR) {
            break;
        }
        int test_node_offset = fdt_node_offset_by_compatible(
                current_dtb, -1 /* search from the start of the dtb */, compat);
        if (test_node_offset >= 0) {
            /* Found the test node */
            *dtb = current_dtb;
            *dtb_size = current_dtb_size;
            break;
        }
    }

    dtb_embedded_iterator_free(&iter);

    return rc;
}

static void* apply_test_overlay(const void* base_dtb,
                                size_t base_dtb_size,
                                const void* test_dtbo,
                                size_t test_dtbo_size) {
    /* Overestimate the size needed for the full dtb for now */
    size_t full_size = base_dtb_size + test_dtbo_size;
    void* base = calloc(1, full_size);
    if (!base) {
        goto alloc_base;
    }
    /*
     * libfdt overwrites part of the dtbo header if applying the overlay fails
     * so we have to make a temporary copy since the original dtbo is in
     * read-only memory.
     */
    void* overlay = calloc(1, test_dtbo_size);
    if (!overlay) {
        goto alloc_overlay;
    }
    /*
     * Using fdt_open_into copies the dtb to the buffer allocated above and
     * updates the fdt header with the new buffer size
     */
    int rc = fdt_open_into(base_dtb, base, full_size);
    if (rc < 0) {
        TRACEF("fdt_open_into failed for base dtb (%d)\n", rc);
        goto copy_base;
    }
    rc = fdt_open_into(test_dtbo, overlay, test_dtbo_size);
    if (rc < 0) {
        TRACEF("fdt_open_into failed for test dtb overlay (%d)\n", rc);
        goto copy_overlay;
    }
    rc = fdt_overlay_apply(base, overlay);
    if (rc < 0) {
        TRACEF("Failed to apply test overlay to base dtb (%d)\n", rc);
        goto apply_overlay;
    }
    free(overlay);
    return base;

apply_overlay:
copy_overlay:
copy_base:
    free(overlay);
alloc_overlay:
    free(base);
alloc_base:
    return NULL;
}

static void platform_dtb_init(uint level) {
    int rc;
    rc = ktipc_server_start(&dtb_ktipc_server);
    if (rc < 0) {
        panic("Failed (%d) to start dtb server\n", rc);
    }

    const void* base_dtb = NULL;
    const void* test_dtbo = NULL;
    size_t base_dtb_size, test_dtbo_size = 0;
    rc = find_dtb_by_compatible("google,test_base", &base_dtb, &base_dtb_size);
    if (rc != NO_ERROR) {
        panic("No embedded base dtb found (%d)\n", rc);
    }

    rc = find_dtb_by_compatible("google,test_overlay", &test_dtbo,
                                &test_dtbo_size);
    if (rc != NO_ERROR) {
        panic("No embedded test dtbo found (%d)\n", rc);
    }

    const void* dtb = apply_test_overlay(base_dtb, base_dtb_size, test_dtbo,
                                         test_dtbo_size);
    if (!dtb) {
        panic("Failed to apply test overlay to base dtb\n");
    }
    size_t dtb_size = fdt_totalsize(dtb);

    rc = dtb_service_add(dtb, dtb_size, DT_PORT, DTB_PORT, &dtb_ktipc_server);
    if (rc < 0) {
        panic("Failed (%d) to add dtb service to server\n", rc);
    }
}

LK_INIT_HOOK(platform_dtb, platform_dtb_init, LK_INIT_LEVEL_THREADING + 1);
