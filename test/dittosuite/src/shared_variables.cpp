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

#include <ditto/shared_variables.h>

#include <ditto/logger.h>

namespace dittosuite {

// Matches variable_name to the integer key value.
//
// If variable_name already exists in the map for the current thread or parent threads,
// then the key is returned.
//
// If variable_name does not exist in that map, size of the vector of shared_variables
// is increased by one and the new key (index of the last element in the resized vector)
// is saved together with variable_name in the map for the current thread and returned.
int SharedVariables::GetKey(const std::list<int>& thread_ids, const std::string& variable_name) {
  // If the key exists in the current or parent threads, return it
  for (auto it = thread_ids.rbegin(); it != thread_ids.rend(); ++it) {
    if (keys_.find(*it) == keys_.end() || keys_[*it].find(variable_name) == keys_[*it].end()) {
      continue;
    }
    return SharedVariables::keys_[*it][variable_name];
  }

  // If the key does not exist, create it for the current thread
  std::size_t key = variables_.size();
  keys_[thread_ids.back()].insert({variable_name, key});
  variables_.resize(variables_.size() + 1);
  return key;
}

SharedVariables::Variant SharedVariables::Get(int key) {
  if (key < 0 || static_cast<unsigned int>(key) >= variables_.size()) {
    LOGF("Shared variable with the provided key does not exist");
  }
  return variables_[key];
}

SharedVariables::Variant SharedVariables::Get(const std::list<int>& thread_ids,
                                              const std::string& variable_name) {
  return Get(GetKey(thread_ids, variable_name));
}

void SharedVariables::Set(int key, const SharedVariables::Variant& value) {
  if (key < 0 || static_cast<unsigned int>(key) >= variables_.size()) {
    LOGF("Shared variable with the provided key does not exist");
  }
  variables_[key] = value;
}

void SharedVariables::Set(const std::list<int>& thread_ids, const std::string& variable_name,
                          const Variant& value) {
  Set(GetKey(thread_ids, variable_name), value);
}

void SharedVariables::ClearKeys() {
  keys_.clear();
}

std::vector<SharedVariables::Variant> SharedVariables::variables_;
std::unordered_map<int, std::unordered_map<std::string, int>> SharedVariables::keys_;

}  // namespace dittosuite
