/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "adbconnection/common.h"

#include <sys/socket.h>

#include <string_view>

namespace {
using namespace std::string_view_literals;
constexpr std::string_view kJdwpControlName = "\0jdwp-control"sv;
static_assert(kJdwpControlName.size() <= sizeof(reinterpret_cast<sockaddr_un*>(0)->sun_path));
}  // namespace

std::tuple<sockaddr_un, socklen_t> get_control_socket_addr() {
  sockaddr_un addr = {};
  addr.sun_family = AF_UNIX;
  memcpy(addr.sun_path, kJdwpControlName.data(), kJdwpControlName.size());
  socklen_t addrlen = offsetof(sockaddr_un, sun_path) + kJdwpControlName.size();

  return {addr, addrlen};
}