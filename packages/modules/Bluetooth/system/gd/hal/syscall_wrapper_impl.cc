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

#include "hal/syscall_wrapper_impl.h"

#include <unistd.h>

#include <cerrno>

namespace bluetooth {
namespace hal {

int SyscallWrapperImpl::Socket(int domain, int type, int protocol) {
  int ret = socket(domain, type, protocol);
  errno_ = errno;
  return ret;
}

int SyscallWrapperImpl::Bind(int fd, const struct sockaddr* addr, socklen_t len) {
  int ret = bind(fd, addr, len);
  errno_ = errno;
  return ret;
}

int SyscallWrapperImpl::Connect(int fd, const struct sockaddr* addr, socklen_t len) {
  int ret = connect(fd, addr, len);
  errno_ = errno;
  return ret;
}

ssize_t SyscallWrapperImpl::Send(int fd, const void* buf, size_t n, int flags) {
  int ret = send(fd, buf, n, flags);
  errno_ = errno;
  return ret;
}

ssize_t SyscallWrapperImpl::Recv(int fd, void* buf, size_t n, int flags) {
  int ret = recv(fd, buf, n, flags);
  errno_ = errno;
  return ret;
}

int SyscallWrapperImpl::Setsockopt(int fd, int level, int optname, const void* optval, socklen_t optlen) {
  int ret = setsockopt(fd, level, optname, optval, optlen);
  errno_ = errno;
  return ret;
}

int SyscallWrapperImpl::Listen(int fd, int n) {
  int ret = listen(fd, n);
  errno_ = errno;
  return ret;
}

int SyscallWrapperImpl::Accept(int fd, struct sockaddr* addr, socklen_t* addr_len, int flags) {
  int ret = accept4(fd, addr, addr_len, flags);
  errno_ = errno;
  return ret;
}

ssize_t SyscallWrapperImpl::Write(int fd, const void* buf, size_t count) {
  ssize_t ret = write(fd, buf, count);
  errno_ = errno;
  return ret;
}

int SyscallWrapperImpl::Close(int fd) {
  int ret = close(fd);
  errno_ = errno;
  return ret;
}

int SyscallWrapperImpl::Pipe2(int* pipefd, int flags) {
  int ret = pipe2(pipefd, flags);
  errno_ = errno;
  return ret;
}

int SyscallWrapperImpl::GetErrno() const {
  return errno_;
}

void SyscallWrapperImpl::FDSet(int fd, fd_set* set) {
  FD_SET(fd, set);
}

void SyscallWrapperImpl::FDClr(int fd, fd_set* set) {
  FD_CLR(fd, set);
}

bool SyscallWrapperImpl::FDIsSet(int fd, fd_set* set) {
  return FD_ISSET(fd, set);
}

void SyscallWrapperImpl::FDZero(fd_set* set) {
  FD_ZERO(set);
}

int SyscallWrapperImpl::Select(
    int __nfds, fd_set* __readfds, fd_set* __writefds, fd_set* __exceptfds, struct timeval* __timeout) {
  int ret = select(__nfds, __readfds, __writefds, __exceptfds, __timeout);
  errno_ = errno;
  return ret;
}

}  // namespace hal
}  // namespace bluetooth
