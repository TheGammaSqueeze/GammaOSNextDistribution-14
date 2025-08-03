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

/* DEF_SYSCALL(syscall_nr, syscall_name, return type, nr_args, [argument list])
 *
 * Please keep this table sorted by syscall number.
 * Please keep each syscall def on a single line, this file is parsed by
 * scripts.
 */

/* clang-format off */

DEF_SYSCALL(0x1, writev, long, 3, uint32_t fd, const struct iovec *iov, uint32_t iovcnt)
DEF_SYSCALL(0x2, brk, void*, 1, void* brk)
DEF_SYSCALL(0x3, exit_etc, long, 2, int32_t status, uint32_t flags)
DEF_SYSCALL(0x4, readv, long, 3, uint32_t fd, const struct iovec *iov, uint32_t iovcnt)
DEF_SYSCALL(0x5, ioctl, long, 3, uint32_t fd, uint32_t req, void *buf)
DEF_SYSCALL(0x6, nanosleep, long, 3, uint32_t clock_id, uint32_t flags, uint64_t sleep_time)
DEF_SYSCALL(0x7, gettime, long, 3, uint32_t clock_id, uint32_t flags, int64_t *time)
DEF_SYSCALL(0x8, mmap, long, 4, void *uaddr, uint32_t size, uint32_t flags, int32_t handle)
DEF_SYSCALL(0x9, munmap, long, 2, void *uaddr, uint32_t size)
DEF_SYSCALL(0xa, prepare_dma, long, 4, void *uaddr, uint32_t size, uint32_t flags, struct dma_pmem *pmem)
DEF_SYSCALL(0xb, finish_dma, long, 3, void *uaddr, uint32_t size, uint32_t flags)
DEF_SYSCALL(0xc, set_user_tls, long, 1, void *uaddr)
DEF_SYSCALL(0xd, dup, long, 1, int32_t handle)

/* IPC connection establishement syscalls */
DEF_SYSCALL(0x10, port_create, long, 4, const char *path, uint32_t num_recv_bufs, uint32_t recv_buf_size, uint32_t flags)
DEF_SYSCALL(0x11, connect, long, 2, const char *path, uint32_t flags)
DEF_SYSCALL(0x12, accept, long, 2, int32_t handle, struct uuid *peer_uuid)
DEF_SYSCALL(0x13, close, long, 1, int32_t handle)
DEF_SYSCALL(0x14, set_cookie, long, 2, int32_t handle, void *cookie)
DEF_SYSCALL(0x15, handle_set_create, long, 0)
DEF_SYSCALL(0x16, handle_set_ctrl, long, 3, int32_t handle, uint32_t cmd, struct uevent *evt)

/* handle polling related syscalls */
DEF_SYSCALL(0x18, wait, long, 3, int32_t handle, struct uevent *event, uint32_t timeout_msecs)
DEF_SYSCALL(0x19, wait_any, long, 2, struct uevent *event, uint32_t timeout_msecs)

/* message send/recv syscalls */
DEF_SYSCALL(0x20, get_msg, long, 2, int32_t handle, struct ipc_msg_info *msg_info)
DEF_SYSCALL(0x21, read_msg, long, 4, int32_t handle, uint32_t msg_id, uint32_t offset, struct ipc_msg *msg)
DEF_SYSCALL(0x22, put_msg, long, 2, int32_t handle, uint32_t msg_id)
DEF_SYSCALL(0x23, send_msg, long, 2, int32_t handle, struct ipc_msg *msg)

/* memref */
DEF_SYSCALL(0x40, memref_create, long, 3, void *uaddr, uint32_t size, uint32_t mmap_prot)
