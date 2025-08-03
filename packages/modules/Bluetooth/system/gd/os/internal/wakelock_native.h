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

#pragma once

#include <memory>

namespace bluetooth {
namespace os {
namespace internal {

// DO NOT USE OUTSIDE os/
// Native wakelock APIs implemented by each architecture, not public APIs
class WakelockNative {
 public:
  static WakelockNative& Get() {
    static WakelockNative instance;
    return instance;
  }
  enum StatusCode : uint8_t { SUCCESS = 0, NATIVE_SERVICE_NOT_AVAILABLE = 1, NATIVE_API_ERROR = 2 };
  void Initialize();
  StatusCode Acquire(const std::string& lock_name);
  StatusCode Release(const std::string& lock_name);
  void CleanUp();

  ~WakelockNative();

 private:
  WakelockNative();
  struct Impl;
  std::unique_ptr<Impl> pimpl_;
};

}  // namespace internal
}  // namespace os
}  // namespace bluetooth