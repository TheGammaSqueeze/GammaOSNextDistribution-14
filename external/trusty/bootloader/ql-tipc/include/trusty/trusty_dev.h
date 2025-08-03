/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#ifndef TRUSTY_TRUSTY_DEV_H_
#define TRUSTY_TRUSTY_DEV_H_

#include <trusty/sysdeps.h>

typedef uint64_t trusty_shared_mem_id_t;

/*
 * Architecture specific Trusty device struct.
 *
 * @priv_data:   system dependent data, may be unused
 * @api_version: TIPC version
 */
struct trusty_dev {
    void* priv_data;
    uint32_t api_version;
    uint16_t ffa_local_id;
    uint16_t ffa_remote_id;
    void* ffa_tx;
    void* ffa_rx;
};

/*
 * Initializes @dev with @priv, and gets the API version by calling
 * into Trusty. Returns negative on error.
 */
int trusty_dev_init(struct trusty_dev* dev, void* priv);

/*
 * Cleans up anything related to @dev. Returns negative on error.
 */
int trusty_dev_shutdown(struct trusty_dev* dev);

/*
 * Enter trusty on cpus that are not in an ipc call
 */
int trusty_dev_nop(struct trusty_dev* dev);

/*
 * Invokes creation of queueless Trusty IPC device on the secure side.
 * @buf will be mapped into Trusty's address space.
 *
 * @dev:      trusty device, initialized with trusty_dev_init
 * @buf:      physical address info of buffer to share with Trusty
 * @buf_size: size of @buf
 */
int trusty_dev_init_ipc(struct trusty_dev* dev,
                        trusty_shared_mem_id_t buf_id,
                        uint32_t buf_size);
/*
 * Invokes execution of command on the secure side.
 *
 * @dev:      trusty device, initialized with trusty_dev_init
 * @buf:      physical address info of shared buffer containing command
 * @buf_size: size of command data
 */
int trusty_dev_exec_ipc(struct trusty_dev* dev,
                        trusty_shared_mem_id_t buf_id,
                        uint32_t buf_size);

int trusty_dev_exec_fc_ipc(struct trusty_dev* dev,
                           trusty_shared_mem_id_t buf_id,
                           uint32_t buf_size);

/*
 * Invokes deletion of queueless Trusty IPC device on the secure side.
 * @buf is unmapped, and all open channels are closed.
 *
 * @dev:      trusty device, initialized with trusty_dev_init
 * @buf:      physical address info of shared buffer
 * @buf_size: size of @buf
 */
int trusty_dev_shutdown_ipc(struct trusty_dev* dev,
                            trusty_shared_mem_id_t buf_id,
                            uint32_t buf_size);

/**
 * trusty_dev_share_memory - Share a contiguous memory region
 * @dev:        trusty device, initialized with trusty_dev_init.
 * @idp:        pointer to return shared memory object id in.
 * @pinfo:      physical address and memory attributes
 * @page_count: number of 4k pages to share.
 */
int trusty_dev_share_memory(struct trusty_dev* dev,
                            trusty_shared_mem_id_t* idp,
                            struct ns_mem_page_info* pinfo,
                            size_t page_count);

/**
 * trusty_dev_reclaim_memory - Reclaim a contiguous memory region
 * @dev:        trusty device, initialized with trusty_dev_init.
 * @id:         shared memory object id returned from trusty_dev_share_memory.
 */
int trusty_dev_reclaim_memory(struct trusty_dev* dev,
                              trusty_shared_mem_id_t id);

#endif /* TRUSTY_TRUSTY_DEV_H_ */
