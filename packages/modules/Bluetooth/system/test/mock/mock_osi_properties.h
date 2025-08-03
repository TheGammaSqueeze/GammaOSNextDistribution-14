/*
 * Copyright 2021 The Android Open Source Project
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

/*
 * Generated mock file from original source file
 *   Functions generated:4
 *
 *  mockcify.pl ver 0.3.0
 */

#include <cstdint>
#include <functional>

// Original included files, if any
#include <cutils/properties.h>

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace osi_properties {

// Shared state between mocked functions and tests
// Name: osi_property_get
// Params: const char* key, char* value, const char* default_value
// Return: int
struct osi_property_get {
  int return_value{0};
  std::function<int(const char* key, char* value, const char* default_value)>
      body{[this](const char* /* key */, char* /* value */,
                  const char* /* default_value */) { return return_value; }};
  int operator()(const char* key, char* value, const char* default_value) {
    return body(key, value, default_value);
  };
};
extern struct osi_property_get osi_property_get;

// Name: osi_property_get_bool
// Params: const char* key, bool default_value
// Return: bool
struct osi_property_get_bool {
  bool return_value{false};
  std::function<bool(const char* key, bool default_value)> body{
      [this](const char* /* key */, bool /* default_value */) {
        return return_value;
      }};
  bool operator()(const char* key, bool default_value) {
    return body(key, default_value);
  };
};
extern struct osi_property_get_bool osi_property_get_bool;

// Name: osi_property_get_int32
// Params: const char* key, int32_t default_value
// Return: int32_t
struct osi_property_get_int32 {
  int32_t return_value{0};
  std::function<int32_t(const char* key, int32_t default_value)> body{
      [this](const char* /* key */, int32_t /* default_value */) {
        return return_value;
      }};
  int32_t operator()(const char* key, int32_t default_value) {
    return body(key, default_value);
  };
};
extern struct osi_property_get_int32 osi_property_get_int32;

// Name: osi_property_set
// Params: const char* key, const char* value
// Return: int
struct osi_property_set {
  int return_value{0};
  std::function<int(const char* key, const char* value)> body{
      [this](const char* /* key */, const char* /* value */) {
        return return_value;
      }};
  int operator()(const char* key, const char* value) {
    return body(key, value);
  };
};
extern struct osi_property_set osi_property_set;

}  // namespace osi_properties
}  // namespace mock
}  // namespace test

// END mockcify generation
