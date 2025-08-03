/*
 * Copyright 2023 The Android Open Source Project
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
#pragma once

/*
 * Generated mock file from original source file
 *   Functions generated:10
 *
 *  mockcify.pl ver 0.6.2
 */

#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/types.h>

#include <cstdint>
#include <functional>

#include "btif/include/btif_uid.h"
#include "stack/include/bt_hdr.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

// Original usings
using bluetooth::Uuid;

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace btif_sock_rfc {

// Shared state between mocked functions and tests
// Name: bta_co_rfc_data_incoming
// Params: uint32_t id, BT_HDR* p_buf
// Return: int
struct bta_co_rfc_data_incoming {
  static int return_value;
  std::function<int(uint32_t id, BT_HDR* p_buf)> body{
      [](uint32_t /* id */, BT_HDR* /* p_buf */) { return return_value; }};
  int operator()(uint32_t id, BT_HDR* p_buf) { return body(id, p_buf); };
};
extern struct bta_co_rfc_data_incoming bta_co_rfc_data_incoming;

// Name: bta_co_rfc_data_outgoing
// Params: uint32_t id, uint8_t* buf, uint16_t size
// Return: int
struct bta_co_rfc_data_outgoing {
  static int return_value;
  std::function<int(uint32_t id, uint8_t* buf, uint16_t size)> body{
      [](uint32_t /* id */, uint8_t* /* buf */, uint16_t /* size */) {
        return return_value;
      }};
  int operator()(uint32_t id, uint8_t* buf, uint16_t size) {
    return body(id, buf, size);
  };
};
extern struct bta_co_rfc_data_outgoing bta_co_rfc_data_outgoing;

// Name: bta_co_rfc_data_outgoing_size
// Params: uint32_t id, int* size
// Return: int
struct bta_co_rfc_data_outgoing_size {
  static int return_value;
  std::function<int(uint32_t id, int* size)> body{
      [](uint32_t /* id */, int* /* size */) { return return_value; }};
  int operator()(uint32_t id, int* size) { return body(id, size); };
};
extern struct bta_co_rfc_data_outgoing_size bta_co_rfc_data_outgoing_size;

// Name: btsock_rfc_cleanup
// Params: void
// Return: void
struct btsock_rfc_cleanup {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btsock_rfc_cleanup btsock_rfc_cleanup;

// Name: btsock_rfc_connect
// Params: const RawAddress* bd_addr, const Uuid* service_uuid, int channel,
// int* sock_fd, int flags, int app_uid Return: bt_status_t
struct btsock_rfc_connect {
  static bt_status_t return_value;
  std::function<bt_status_t(const RawAddress* bd_addr, const Uuid* service_uuid,
                            int channel, int* sock_fd, int flags, int app_uid)>
      body{[](const RawAddress* /* bd_addr */, const Uuid* /* service_uuid */,
              int /* channel */, int* /* sock_fd */, int /* flags */,
              int /* app_uid */) { return return_value; }};
  bt_status_t operator()(const RawAddress* bd_addr, const Uuid* service_uuid,
                         int channel, int* sock_fd, int flags, int app_uid) {
    return body(bd_addr, service_uuid, channel, sock_fd, flags, app_uid);
  };
};
extern struct btsock_rfc_connect btsock_rfc_connect;

// Name: btsock_rfc_control_req
// Params: uint8_t dlci, const RawAddress& bd_addr, uint8_t modem_signal,
// uint8_t break_signal, uint8_t discard_buffers, uint8_t break_signal_seq, bool
// fc Return: bt_status_t
struct btsock_rfc_control_req {
  static bt_status_t return_value;
  std::function<bt_status_t(uint8_t dlci, const RawAddress& bd_addr,
                            uint8_t modem_signal, uint8_t break_signal,
                            uint8_t discard_buffers, uint8_t break_signal_seq,
                            bool fc)>
      body{[](uint8_t /* dlci */, const RawAddress& /* bd_addr */,
              uint8_t /* modem_signal */, uint8_t /* break_signal */,
              uint8_t /* discard_buffers */, uint8_t /* break_signal_seq */,
              bool /* fc */) { return return_value; }};
  bt_status_t operator()(uint8_t dlci, const RawAddress& bd_addr,
                         uint8_t modem_signal, uint8_t break_signal,
                         uint8_t discard_buffers, uint8_t break_signal_seq,
                         bool fc) {
    return body(dlci, bd_addr, modem_signal, break_signal, discard_buffers,
                break_signal_seq, fc);
  };
};
extern struct btsock_rfc_control_req btsock_rfc_control_req;

// Name: btsock_rfc_disconnect
// Params: const RawAddress* bd_addr
// Return: bt_status_t
struct btsock_rfc_disconnect {
  static bt_status_t return_value;
  std::function<bt_status_t(const RawAddress* bd_addr)> body{
      [](const RawAddress* /* bd_addr */) { return return_value; }};
  bt_status_t operator()(const RawAddress* bd_addr) { return body(bd_addr); };
};
extern struct btsock_rfc_disconnect btsock_rfc_disconnect;

// Name: btsock_rfc_init
// Params: int poll_thread_handle, uid_set_t* set
// Return: bt_status_t
struct btsock_rfc_init {
  static bt_status_t return_value;
  std::function<bt_status_t(int poll_thread_handle, uid_set_t* set)> body{
      [](int /* poll_thread_handle */, uid_set_t* /* set */) {
        return return_value;
      }};
  bt_status_t operator()(int poll_thread_handle, uid_set_t* set) {
    return body(poll_thread_handle, set);
  };
};
extern struct btsock_rfc_init btsock_rfc_init;

// Name: btsock_rfc_listen
// Params: const char* service_name, const Uuid* service_uuid, int channel, int*
// sock_fd, int flags, int app_uid Return: bt_status_t
struct btsock_rfc_listen {
  static bt_status_t return_value;
  std::function<bt_status_t(const char* service_name, const Uuid* service_uuid,
                            int channel, int* sock_fd, int flags, int app_uid)>
      body{[](const char* /* service_name */, const Uuid* /* service_uuid */,
              int /* channel */, int* /* sock_fd */, int /* flags */,
              int /* app_uid */) { return return_value; }};
  bt_status_t operator()(const char* service_name, const Uuid* service_uuid,
                         int channel, int* sock_fd, int flags, int app_uid) {
    return body(service_name, service_uuid, channel, sock_fd, flags, app_uid);
  };
};
extern struct btsock_rfc_listen btsock_rfc_listen;

// Name: btsock_rfc_signaled
// Params:  int fd, int flags, uint32_t id
// Return: void
struct btsock_rfc_signaled {
  std::function<void(int fd, int flags, uint32_t id)> body{
      [](int /* fd */, int /* flags */, uint32_t /* id */) {}};
  void operator()(int fd, int flags, uint32_t id) { body(fd, flags, id); };
};
extern struct btsock_rfc_signaled btsock_rfc_signaled;

}  // namespace btif_sock_rfc
}  // namespace mock
}  // namespace test

// END mockcify generation
