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

#include "hal/syscall_wrapper_interface.h"

namespace bluetooth {
namespace hal {

class SyscallWrapperImpl : public SyscallWrapperInterface {
  int Socket(int domain, int type, int protocol);

  int Bind(int fd, const struct sockaddr* addr, socklen_t len);

  int Connect(int fd, const struct sockaddr* addr, socklen_t len);

  ssize_t Send(int fd, const void* buf, size_t n, int flags);

  ssize_t Recv(int fd, void* buf, size_t n, int flags);

  int Setsockopt(int fd, int level, int optname, const void* optval, socklen_t optlen);

  int Listen(int fd, int n);

  int Accept(int fd, struct sockaddr* addr, socklen_t* addr_len, int flags);

  ssize_t Write(int, const void*, size_t);

  int Close(int fd);

  int Pipe2(int* pipefd, int flags);

  int GetErrno() const;

  void FDSet(int fd, fd_set* set);

  void FDClr(int fd, fd_set* set);

  bool FDIsSet(int fd, fd_set* set);

  void FDZero(fd_set* set);

  int Select(int __nfds, fd_set* __readfds, fd_set* __writefds, fd_set* __exceptfds, struct timeval* __timeout);

 private:
  int errno_;
};

}  // namespace hal
}  // namespace bluetooth
