/*
 * Copyright (c) 2016, Google, Inc. All rights reserved
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

#include <lib/sm.h>
#include <sys/types.h>

#define QL_TIPC_DEV_RESP 0x8000
#define QL_TIPC_DEV_CONNECT 0x1
#define QL_TIPC_DEV_GET_EVENT 0x2
#define QL_TIPC_DEV_SEND 0x3
#define QL_TIPC_DEV_RECV 0x4
#define QL_TIPC_DEV_DISCONNECT 0x5

#define QL_TIPC_DEV_FC_HAS_EVENT 0x100

long ql_tipc_create_device(ext_mem_client_id_t client_id,
                           ext_mem_obj_id_t buf_id,
                           ns_size_t buf_sz,
                           uint mmu_flags);
long ql_tipc_shutdown_device(ext_mem_client_id_t client_id,
                             ext_mem_obj_id_t buf_id);

long ql_tipc_handle_cmd(ext_mem_client_id_t client_id,
                        ext_mem_obj_id_t buf_id,
                        ns_size_t cmd_sz,
                        bool is_fc);
