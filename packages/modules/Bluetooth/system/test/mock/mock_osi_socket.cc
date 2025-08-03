/*
 * Copyright 2021 The Android Open Source Project
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

/*
 * Generated mock file from original source file
 *   Functions generated:11
 *
 *  mockcify.pl ver 0.3.0
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_osi_socket.h"

#include "test/common/mock_functions.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace osi_socket {

// Function state capture and return values, if needed
struct socket_accept socket_accept;
struct socket_bytes_available socket_bytes_available;
struct socket_free socket_free;
struct socket_listen socket_listen;
struct socket_new socket_new;
struct socket_new_from_fd socket_new_from_fd;
struct socket_read socket_read;
struct socket_register socket_register;
struct socket_unregister socket_unregister;
struct socket_write socket_write;
struct socket_write_and_transfer_fd socket_write_and_transfer_fd;

}  // namespace osi_socket
}  // namespace mock
}  // namespace test

// Mocked functions, if any
socket_t* socket_accept(const socket_t* socket) {
  inc_func_call_count(__func__);
  return test::mock::osi_socket::socket_accept(socket);
}
ssize_t socket_bytes_available(const socket_t* socket) {
  inc_func_call_count(__func__);
  return test::mock::osi_socket::socket_bytes_available(socket);
}
void socket_free(socket_t* socket) {
  inc_func_call_count(__func__);
  test::mock::osi_socket::socket_free(socket);
}
bool socket_listen(const socket_t* socket, port_t port) {
  inc_func_call_count(__func__);
  return test::mock::osi_socket::socket_listen(socket, port);
}
socket_t* socket_new(void) {
  inc_func_call_count(__func__);
  return test::mock::osi_socket::socket_new();
}
socket_t* socket_new_from_fd(int fd) {
  inc_func_call_count(__func__);
  return test::mock::osi_socket::socket_new_from_fd(fd);
}
ssize_t socket_read(const socket_t* socket, void* buf, size_t count) {
  inc_func_call_count(__func__);
  return test::mock::osi_socket::socket_read(socket, buf, count);
}
void socket_register(socket_t* socket, reactor_t* reactor, void* context,
                     socket_cb read_cb, socket_cb write_cb) {
  inc_func_call_count(__func__);
  test::mock::osi_socket::socket_register(socket, reactor, context, read_cb,
                                          write_cb);
}
void socket_unregister(socket_t* socket) {
  inc_func_call_count(__func__);
  test::mock::osi_socket::socket_unregister(socket);
}
ssize_t socket_write(const socket_t* socket, const void* buf, size_t count) {
  inc_func_call_count(__func__);
  return test::mock::osi_socket::socket_write(socket, buf, count);
}
ssize_t socket_write_and_transfer_fd(const socket_t* socket, const void* buf,
                                     size_t count, int fd) {
  inc_func_call_count(__func__);
  return test::mock::osi_socket::socket_write_and_transfer_fd(socket, buf,
                                                              count, fd);
}
// Mocked functions complete
// END mockcify generation
int osi_socket_local_server_bind(int /* s */, const char* /* name */,
                                 int /* namespaceId */) {
  inc_func_call_count(__func__);
  return 0;
}
