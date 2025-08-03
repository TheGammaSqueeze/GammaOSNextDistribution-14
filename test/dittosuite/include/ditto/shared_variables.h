// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include <list>
#include <string>
#include <unordered_map>
#include <variant>
#include <vector>

namespace dittosuite {

class SharedVariables {
 public:
  typedef std::variant<int, std::string, std::vector<std::string>> Variant;

  static int GetKey(const std::list<int>& thread_ids, const std::string& variable_name);
  static Variant Get(int key);
  static Variant Get(const std::list<int>& thread_ids, const std::string& variable_name);
  static void Set(int key, const Variant& value);
  static void Set(const std::list<int>& thread_ids, const std::string& variable_name,
                  const Variant& value);
  static void ClearKeys();

 private:
  static std::vector<Variant> variables_;
  static std::unordered_map<int, std::unordered_map<std::string, int>> keys_;
};

}  // namespace dittosuite
