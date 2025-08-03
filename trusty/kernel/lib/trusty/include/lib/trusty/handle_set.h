/*
 * Copyright (c) 2018, Google, Inc. All rights reserved
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

#include <lib/trusty/handle.h>
#include <sys/types.h>

#define HSET_ADD 0
#define HSET_DEL 1
#define HSET_MOD 2

__BEGIN_CDECLS

struct handle* handle_set_create(void);

int handle_set_attach(struct handle* h, struct handle_ref* ref);

void handle_set_detach_ref(struct handle_ref* ref);

void handle_set_update_ref(struct handle_ref* ref,
                           uint32_t event,
                           void* cookie);

int handle_set_wait(struct handle* h,
                    struct handle_ref* out,
                    lk_time_t timeout);

bool handle_set_ready(struct handle* h);

__END_CDECLS
