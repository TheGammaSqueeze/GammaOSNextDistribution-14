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

#ifndef ART_ADBCONNECTION_JDWPARGS_H_
#define ART_ADBCONNECTION_JDWPARGS_H_

#include <string>
#include <unordered_map>
#include <vector>

namespace adbconnection {

// A key/value store which respects order of insertion when join the values.
// This is necessary for jdwp agent parameter. e.g.: key "transport", must be
// issued before "address", otherwise oj-libjdpw will crash.
//
// If a key were to be re-inserted (a.k.a overwritten), the first insertion
// will be used for order.
class JdwpArgs {
 public:
  explicit JdwpArgs(const std::string& opts);
  ~JdwpArgs() = default;

  // Add a key / value
  void put(const std::string& key, const std::string& value);

  bool contains(const std::string& key) { return store.find(key) != store.end(); }

  std::string& get(const std::string& key) { return store[key]; }

  // Concatenate all key/value into a command separated list of "key=value" entries.
  std::string join();

 private:
  std::vector<std::string> keys;
  std::unordered_map<std::string, std::string> store;
};

}  // namespace adbconnection

#endif  // ART_ADBCONNECTION_JDWPARGS_H_
