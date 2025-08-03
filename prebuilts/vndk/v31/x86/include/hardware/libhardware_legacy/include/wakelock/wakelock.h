/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <memory>
#include <optional>
#include <string>

namespace android {
namespace wakelock {

// RAII-style wake lock implementation
class WakeLock {
  private:
    class WakeLockImpl;
    std::unique_ptr<WakeLockImpl> mImpl;

  public:
    static std::optional<WakeLock> tryGet(const std::string& name);
    // Constructor is only made public for use with std::optional.
    // It is not intended to be and cannot be invoked from public context,
    // since private WakeLockImpl prevents calling the constructor directly.
    WakeLock(std::unique_ptr<WakeLockImpl> wlImpl);
    ~WakeLock();
};

}  // namespace wakelock
}  // namespace android
