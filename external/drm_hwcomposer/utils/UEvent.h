/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <linux/netlink.h>
#include <sys/socket.h>

#include <cerrno>
#include <memory>
#include <optional>
#include <string>

#include "fd.h"
#include "log.h"

namespace android {

class UEvent {
 public:
  static auto CreateInstance() -> std::unique_ptr<UEvent> {
    auto fd = MakeUniqueFd(
        socket(PF_NETLINK, SOCK_DGRAM | SOCK_CLOEXEC, NETLINK_KOBJECT_UEVENT));

    if (!fd) {
      ALOGE("Failed to open uevent socket: errno=%i", errno);
      return {};
    }

    struct sockaddr_nl addr {};
    addr.nl_family = AF_NETLINK;
    addr.nl_pid = 0;
    addr.nl_groups = UINT32_MAX;

    // NOLINTNEXTLINE(cppcoreguidelines-pro-type-cstyle-cast)
    const int ret = bind(*fd, (struct sockaddr *)&addr, sizeof(addr));
    if (ret != 0) {
      ALOGE("Failed to bind uevent socket: errno=%i", errno);
      return {};
    }

    return std::unique_ptr<UEvent>(new UEvent(fd));
  }

  auto ReadNext() -> std::optional<std::string> {
    constexpr int kUEventBufferSize = 1024;
    char buffer[kUEventBufferSize];
    ssize_t ret = 0;
    ret = read(*fd_, &buffer, sizeof(buffer));
    if (ret == 0)
      return {};

    if (ret < 0) {
      ALOGE("Got error reading uevent %zd", ret);
      return {};
    }

    for (int i = 0; i < ret - 1; i++) {
      if (buffer[i] == '\0') {
        buffer[i] = '\n';
      }
    }

    return std::string(buffer);
  }

 private:
  explicit UEvent(UniqueFd &fd) : fd_(std::move(fd)){};
  UniqueFd fd_;
};

}  // namespace android
