/******************************************************************************
 *
 *  Copyright (C) 2022 Google, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#pragma once

#include <sys/select.h>
#include <sys/socket.h>

namespace bluetooth {
namespace hal {

class SyscallWrapperInterface {
 public:
  virtual ~SyscallWrapperInterface() = default;

  /* Wrapper for  <sys/socket.h> socket() API */
  virtual int Socket(int domain, int type, int protocol) = 0;

  /* Wrapper for <sys/socket.h> bind() API */
  virtual int Bind(int fd, const struct sockaddr* addr, socklen_t len) = 0;

  /* Wrapper for <sys/socket.h> connect() API */
  virtual int Connect(int fd, const struct sockaddr* addr, socklen_t len) = 0;

  /* Wrapper for <sys/socket.h> send() API */
  virtual ssize_t Send(int fd, const void* buf, size_t n, int flags) = 0;

  /* Wrapper for <sys/socket.h> recv() API */
  virtual ssize_t Recv(int fd, void* buf, size_t n, int flags) = 0;

  /* Wrapper for <sys/socket.h> setsockopt() API */
  virtual int Setsockopt(int fd, int level, int optname, const void* optval, socklen_t optlen) = 0;

  /* Wrapper for <sys/socket.h> listen() API */
  virtual int Listen(int fd, int n) = 0;

  /* Wrapper for <sys/socket.h> accept() API */
  virtual int Accept(int fd, struct sockaddr* addr, socklen_t* addr_len, int flags) = 0;

  /* Wrapper for <unistd.h> pipe2() API */
  virtual int Pipe2(int* pipefd, int flags) = 0;

  /* Wrapper for <errno.h> errno API */
  virtual int GetErrno() const = 0;

  /* Wrapper for <unistd.h> write() API */
  virtual ssize_t Write(int, const void*, size_t) = 0;

  /* Wrapper for <unistd.h> close() API */
  virtual int Close(int fd) = 0;

  /* Wrapper for <sys/select.h> FD_SET() API */
  virtual void FDSet(int fd, fd_set* set) = 0;

  /* Wrapper for <sys/select.h> FD_CLR() API */
  virtual void FDClr(int fd, fd_set* set) = 0;

  /* Wrapper for <sys/select.h> FD_ISSET() API */
  virtual bool FDIsSet(int fd, fd_set* set) = 0;

  /* Wrapper for <sys/select.h> FD_ZERO() API */
  virtual void FDZero(fd_set* set) = 0;

  /* Wrapper for <sys/select.h> select() API */
  virtual int Select(
      int __nfds, fd_set* __readfds, fd_set* __writefds, fd_set* __exceptfds, struct timeval* __timeout) = 0;
};

}  // namespace hal
}  // namespace bluetooth
