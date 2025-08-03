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

#include "test/common/mock_functions.h"

#include <map>

#include "os/log.h"

static std::map<std::string, int>& _get_func_call_count_map() {
  static std::map<std::string, int> mock_function_count_map;
  return mock_function_count_map;
}

int get_func_call_count(const char* fn) {
  return _get_func_call_count_map()[fn];
}
void inc_func_call_count(const char* fn) { _get_func_call_count_map()[fn]++; }

void reset_mock_function_count_map() { _get_func_call_count_map().clear(); }

int get_func_call_size() { return _get_func_call_count_map().size(); }

void dump_mock_function_count_map() {
  LOG_INFO("Mock function count map size:%zu",
           _get_func_call_count_map().size());

  for (const auto& it : _get_func_call_count_map()) {
    LOG_INFO("function:%s: call_count:%d", it.first.c_str(), it.second);
  }
}
