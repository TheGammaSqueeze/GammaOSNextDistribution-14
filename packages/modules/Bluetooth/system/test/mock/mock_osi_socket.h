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

#include <functional>

// Original included files, if any
#include "osi/include/reactor.h"
#include "osi/include/socket.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace osi_socket {

// Shared state between mocked functions and tests
// Name: socket_accept
// Params: const socket_t* socket
// Return: socket_t*
struct socket_accept {
  socket_t* return_value{0};
  std::function<socket_t*(const socket_t* socket)> body{
      [this](const socket_t* /* socket */) { return return_value; }};
  socket_t* operator()(const socket_t* socket) { return body(socket); };
};
extern struct socket_accept socket_accept;

// Name: socket_bytes_available
// Params: const socket_t* socket
// Return: ssize_t
struct socket_bytes_available {
  ssize_t return_value{0};
  std::function<ssize_t(const socket_t* socket)> body{
      [this](const socket_t* /* socket */) { return return_value; }};
  ssize_t operator()(const socket_t* socket) { return body(socket); };
};
extern struct socket_bytes_available socket_bytes_available;

// Name: socket_free
// Params: socket_t* socket
// Return: void
struct socket_free {
  std::function<void(socket_t* socket)> body{[](socket_t* /* socket */) {}};
  void operator()(socket_t* socket) { body(socket); };
};
extern struct socket_free socket_free;

// Name: socket_listen
// Params: const socket_t* socket, port_t port
// Return: bool
struct socket_listen {
  bool return_value{false};
  std::function<bool(const socket_t* socket, port_t port)> body{
      [this](const socket_t* /* socket */, port_t /* port */) {
        return return_value;
      }};
  bool operator()(const socket_t* socket, port_t port) {
    return body(socket, port);
  };
};
extern struct socket_listen socket_listen;

// Name: socket_new
// Params: void
// Return: socket_t*
struct socket_new {
  socket_t* return_value{0};
  std::function<socket_t*(void)> body{[this](void) { return return_value; }};
  socket_t* operator()(void) { return body(); };
};
extern struct socket_new socket_new;

// Name: socket_new_from_fd
// Params: int fd
// Return: socket_t*
struct socket_new_from_fd {
  socket_t* return_value{0};
  std::function<socket_t*(int fd)> body{
      [this](int /* fd */) { return return_value; }};
  socket_t* operator()(int fd) { return body(fd); };
};
extern struct socket_new_from_fd socket_new_from_fd;

// Name: socket_read
// Params: const socket_t* socket, void* buf, size_t count
// Return: ssize_t
struct socket_read {
  ssize_t return_value{0};
  std::function<ssize_t(const socket_t* socket, void* buf, size_t count)> body{
      [this](const socket_t* /* socket */, void* /* buf */,
             size_t /* count */) { return return_value; }};
  ssize_t operator()(const socket_t* socket, void* buf, size_t count) {
    return body(socket, buf, count);
  };
};
extern struct socket_read socket_read;

// Name: socket_register
// Params: socket_t* socket, reactor_t* reactor, void* context, socket_cb
// read_cb, socket_cb write_cb Return: void
struct socket_register {
  std::function<void(socket_t* socket, reactor_t* reactor, void* context,
                     socket_cb read_cb, socket_cb write_cb)>
      body{[](socket_t* /* socket */, reactor_t* /* reactor */,
              void* /* context */, socket_cb /* read_cb */,
              socket_cb /* write_cb */) {}};
  void operator()(socket_t* socket, reactor_t* reactor, void* context,
                  socket_cb read_cb, socket_cb write_cb) {
    body(socket, reactor, context, read_cb, write_cb);
  };
};
extern struct socket_register socket_register;

// Name: socket_unregister
// Params: socket_t* socket
// Return: void
struct socket_unregister {
  std::function<void(socket_t* socket)> body{[](socket_t* /* socket */) {}};
  void operator()(socket_t* socket) { body(socket); };
};
extern struct socket_unregister socket_unregister;

// Name: socket_write
// Params: const socket_t* socket, const void* buf, size_t count
// Return: ssize_t
struct socket_write {
  ssize_t return_value{0};
  std::function<ssize_t(const socket_t* socket, const void* buf, size_t count)>
      body{[this](const socket_t* /* socket */, const void* /* buf */,
                  size_t /* count */) { return return_value; }};
  ssize_t operator()(const socket_t* socket, const void* buf, size_t count) {
    return body(socket, buf, count);
  };
};
extern struct socket_write socket_write;

// Name: socket_write_and_transfer_fd
// Params: const socket_t* socket, const void* buf, size_t count, int fd
// Return: ssize_t
struct socket_write_and_transfer_fd {
  ssize_t return_value{0};
  std::function<ssize_t(const socket_t* socket, const void* buf, size_t count,
                        int fd)>
      body{[this](const socket_t* /* socket */, const void* /* buf */,
                  size_t /* count */, int /* fd */) { return return_value; }};
  ssize_t operator()(const socket_t* socket, const void* buf, size_t count,
                     int fd) {
    return body(socket, buf, count, fd);
  };
};
extern struct socket_write_and_transfer_fd socket_write_and_transfer_fd;

}  // namespace osi_socket
}  // namespace mock
}  // namespace test

// END mockcify generation
