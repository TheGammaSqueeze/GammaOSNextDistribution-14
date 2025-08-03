/******************************************************************************
 *
 *  Copyright 2021 Google, Inc.
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

#define LOG_TAG "BtGdWakelockNative"

#include "os/internal/wakelock_native.h"
#include "os/log.h"

namespace bluetooth {
namespace os {
namespace internal {

struct WakelockNative::Impl {};

void WakelockNative::Initialize() {
  LOG_INFO("Linux native wakelock is not implemented");
}

WakelockNative::StatusCode WakelockNative::Acquire(const std::string& lock_name) {
  LOG_INFO("Linux native wakelock is not implemented");
  return StatusCode::SUCCESS;
}

WakelockNative::StatusCode WakelockNative::Release(const std::string& lock_name) {
  LOG_INFO("Linux native wakelock is not implemented");
  return StatusCode::SUCCESS;
}
void WakelockNative::CleanUp() {
  LOG_INFO("Linux native wakelock is not implemented");
}

WakelockNative::WakelockNative() : pimpl_(std::make_unique<Impl>()) {}

WakelockNative::~WakelockNative() = default;

}  // namespace internal
}  // namespace os
}  // namespace bluetooth