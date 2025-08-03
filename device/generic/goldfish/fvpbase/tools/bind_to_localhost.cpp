// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <dlfcn.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/types.h>

static int (*real_bind)(int sockfd, const sockaddr* addr, socklen_t addrlen) =
    (int (*)(int, const sockaddr*, socklen_t))dlsym(RTLD_NEXT, "bind");

extern "C" int bind(int sockfd, const sockaddr* addr, socklen_t addrlen) {
  if (addr->sa_family != AF_INET) return real_bind(sockfd, addr, addrlen);
  const sockaddr_in* sin = (const sockaddr_in*)addr;
  if (sin->sin_addr.s_addr != 0) return real_bind(sockfd, addr, addrlen);
  sockaddr_in new_sin = *sin;
  new_sin.sin_addr.s_addr = 0x0100007f;
  return real_bind(sockfd, (sockaddr*)&new_sin, addrlen);
}
