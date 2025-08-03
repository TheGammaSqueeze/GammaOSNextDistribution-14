/*
 * Copyright (C) 2015 - 2023 The Android Open Source Project
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

#include <fcntl.h>
#include <unistd.h>

#include <memory>
#include <utility>

namespace android {

using UniqueFd = std::unique_ptr<int, void (*)(const int *)>;
using SharedFd = std::shared_ptr<int>;

auto MakeUniqueFd(int fd) -> UniqueFd;

auto MakeSharedFd(int fd) -> SharedFd;

auto DupFd(SharedFd const &fd) -> int;

}  // namespace android
