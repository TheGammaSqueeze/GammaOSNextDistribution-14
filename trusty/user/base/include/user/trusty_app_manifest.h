/*
 * Copyright (c) 2013, Google, Inc. All rights reserved
 * Copyright (c) 2012-2013, NVIDIA CORPORATION. All rights reserved
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
#pragma once

#include <sys/types.h>
#include <uapi/trusty_app_manifest_types.h>

/*
 * Layout of .trusty_app.manifest section in the trusted application is the
 * required UUID followed by an abitrary number of configuration options.
 */
#define CONCAT(x, y) x##y
#define XCONCAT(x, y) CONCAT(x, y)

#define TRUSTY_APP_CONFIG_MIN_STACK_SIZE(sz) \
    TRUSTY_APP_CONFIG_KEY_MIN_STACK_SIZE, sz

#define TRUSTY_APP_CONFIG_MIN_HEAP_SIZE(sz) \
    TRUSTY_APP_CONFIG_KEY_MIN_HEAP_SIZE, sz

#define TRUSTY_APP_CONFIG_MAP_MEM(id, off, sz) \
    TRUSTY_APP_CONFIG_KEY_MAP_MEM, id, off, sz

#define TRUSTY_APP_CONFIG_MGMT_FLAGS(mgmt_flags) \
    TRUSTY_APP_CONFIG_KEY_MGMT_FLAGS, mgmt_flags

/* Valid flags: IPC_PORT_ALLOW_* */
#define TRUSTY_APP_START_PORT(name, flags)                                    \
    struct trusty_app_manifest_entry_port                                     \
            __attribute((section(".trusty_app.manifest.entry")))              \
                    XCONCAT(trusty_app_manifest_entry_port_, __COUNTER__) = { \
                            .config_key = TRUSTY_APP_CONFIG_KEY_START_PORT,   \
                            .port_flags = flags,                              \
                            .port_name_size = sizeof(name),                   \
                            .port_name = name,                                \
    };

/* manifest section attributes */
#define TRUSTY_APP_MANIFEST_ATTRS   \
    const __attribute((aligned(4))) \
            __attribute((section(".trusty_app.manifest")))
