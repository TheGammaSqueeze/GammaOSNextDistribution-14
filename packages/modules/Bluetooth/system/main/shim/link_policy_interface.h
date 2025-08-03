/*
 * Copyright 2020 The Android Open Source Project
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

#include <cstdint>

namespace bluetooth {
namespace shim {

class LinkPolicyInterface {
 public:
  virtual bool HoldMode(uint16_t handle, uint16_t max_interval,
                        uint16_t min_interval) = 0;
  virtual bool SniffMode(uint16_t handle, uint16_t max_interval,
                         uint16_t min_interval, uint16_t attempt,
                         uint16_t timeout) = 0;
  virtual bool ExitSniffMode(uint16_t handle) = 0;
  virtual bool SniffSubrating(uint16_t handle, uint16_t maximum_latency,
                              uint16_t minimum_remote_timeout,
                              uint16_t minimum_local_timeout) = 0;
  virtual ~LinkPolicyInterface() = default;
};

}  // namespace shim
}  // namespace bluetooth
