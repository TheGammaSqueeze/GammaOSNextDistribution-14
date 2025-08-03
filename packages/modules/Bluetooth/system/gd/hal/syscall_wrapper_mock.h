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

#include "gmock/gmock.h"
#include "hal/syscall_wrapper_interface.h"

namespace bluetooth {
namespace hal {

class SyscallWrapperMock : public SyscallWrapperInterface {
 public:
  MOCK_METHOD(int, Socket, (int, int, int));

  MOCK_METHOD(int, Bind, (int, const struct sockaddr*, socklen_t));

  MOCK_METHOD(int, Connect, (int, const struct sockaddr*, socklen_t));

  MOCK_METHOD(ssize_t, Send, (int, const void*, size_t, int));

  MOCK_METHOD(ssize_t, Recv, (int, void*, size_t, int));

  MOCK_METHOD(int, Setsockopt, (int, int, int, const void*, socklen_t));

  MOCK_METHOD(int, Listen, (int, int));

  MOCK_METHOD(int, Accept, (int, struct sockaddr*, socklen_t*, int));

  MOCK_METHOD(ssize_t, Write, (int, const void*, size_t));

  MOCK_METHOD(int, Close, (int));

  MOCK_METHOD(int, Pipe2, (int*, int));

  MOCK_METHOD(int, GetErrno, (), (const));

  MOCK_METHOD(void, FDSet, (int, fd_set*));

  MOCK_METHOD(void, FDClr, (int, fd_set*));

  MOCK_METHOD(bool, FDIsSet, (int, fd_set*));

  MOCK_METHOD(void, FDZero, (fd_set*));

  MOCK_METHOD(int, Select, (int, fd_set*, fd_set*, fd_set*, struct timeval*));
};

}  // namespace hal
}  // namespace bluetooth
