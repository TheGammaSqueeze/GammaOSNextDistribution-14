/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <trusty_ipc.h>

#include <trusty_syscalls.h>

handle_t port_create(const char* path,
                     uint32_t num_recv_bufs,
                     uint32_t recv_buf_size,
                     uint32_t flags) {
    return _trusty_port_create(path, num_recv_bufs, recv_buf_size, flags);
}

handle_t connect(const char* path, uint32_t flags) {
    return _trusty_connect(path, flags);
}

handle_t accept(handle_t handle, struct uuid* peer_uuid) {
    return _trusty_accept(handle, peer_uuid);
}

int close(handle_t handle) {
    return _trusty_close(handle);
}

int set_cookie(handle_t handle, void* cookie) {
    return _trusty_set_cookie(handle, cookie);
}

handle_t handle_set_create(void) {
    return _trusty_handle_set_create();
}

int handle_set_ctrl(handle_t handle, uint32_t cmd, struct uevent* evt) {
    return _trusty_handle_set_ctrl(handle, cmd, evt);
}

int wait(handle_t handle, struct uevent* event, uint32_t timeout_msecs) {
    return _trusty_wait(handle, event, timeout_msecs);
}

int wait_any(struct uevent* event, uint32_t timeout_msecs) {
    return _trusty_wait_any(event, timeout_msecs);
}

int get_msg(handle_t handle, struct ipc_msg_info* msg_info) {
    return _trusty_get_msg(handle, msg_info);
}

ssize_t read_msg(handle_t handle,
                 uint32_t msg_id,
                 uint32_t offset,
                 struct ipc_msg* msg) {
    return _trusty_read_msg(handle, msg_id, offset, msg);
}

int put_msg(handle_t handle, uint32_t msg_id) {
    return _trusty_put_msg(handle, msg_id);
}

ssize_t send_msg(handle_t handle, struct ipc_msg* msg) {
    return _trusty_send_msg(handle, msg);
}

handle_t dup(handle_t handle) {
    return _trusty_dup(handle);
}
