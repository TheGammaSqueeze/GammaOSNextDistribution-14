/*
 * Copyright 2022 The Android Open Source Project
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

#include <functional>
namespace test {
namespace mock {
namespace stack_btm_devctl {

// Function state capture and return values, if needed
struct BTM_IsDeviceUp {
  std::function<bool()> body{[]() { return false; }};
  bool operator()() { return body(); };
};
extern struct BTM_IsDeviceUp BTM_IsDeviceUp;

}  // namespace stack_btm_devctl
}  // namespace mock
}  // namespace test
