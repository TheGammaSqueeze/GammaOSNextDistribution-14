/*
 * Copyright (c) 2013, Google, Inc. All rights reserved
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

#ifndef __LIB_TRUSTY_UCTX_H
#define __LIB_TRUSTY_UCTX_H

#include <stdint.h>
#include <sys/types.h>

#include <lib/trusty/handle.h>
#include <lib/trusty/sys_fd.h>

struct uctx;

typedef uint32_t handle_id_t;

#define INVALID_HANDLE_ID ((handle_id_t)0xFFFFFFFF)

int uctx_create(void* priv, struct uctx** ctx);
void uctx_destroy(struct uctx* ctx);
void* uctx_get_priv(struct uctx* ctx);
struct uctx* current_uctx(void);

int uctx_handle_install(struct uctx* ctx,
                        struct handle* handle,
                        handle_id_t* id);
int uctx_handle_remove(struct uctx* ctx,
                       handle_id_t handle_id,
                       struct handle** handle_ptr);
int uctx_handle_get(struct uctx* ctx,
                    handle_id_t handle_id,
                    struct handle** handle_ptr);

const struct sys_fd_ops* uctx_get_fd_ops(uint32_t fd);

#endif
