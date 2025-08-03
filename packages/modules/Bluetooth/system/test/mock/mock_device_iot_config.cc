/*
 * Copyright 2023 The Android Open Source Project
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
// Mock include file to share data between tests and mock
#include "test/mock/mock_device_iot_config.h"

#include <cstdint>
#include <string>

#include "test/common/mock_functions.h"

namespace test {
namespace mock {
namespace device_iot_config {

struct device_iot_config_get_int device_iot_config_get_int;
struct device_iot_config_set_int device_iot_config_set_int;
struct device_iot_config_int_add_one device_iot_config_int_add_one;
struct device_iot_config_get_hex device_iot_config_get_hex;
struct device_iot_config_set_hex device_iot_config_set_hex;
struct device_iot_config_set_hex_if_greater device_iot_config_set_hex_if_greater;
struct device_iot_config_get_str device_iot_config_get_str;
struct device_iot_config_set_str device_iot_config_set_str;
struct device_iot_config_get_bin device_iot_config_get_bin;
struct device_iot_config_set_bin device_iot_config_set_bin;
struct device_iot_config_get_bin_length device_iot_config_get_bin_length;
struct device_iot_config_has_section device_iot_config_has_section;
struct device_iot_config_exist device_iot_config_exist;
struct device_iot_config_remove device_iot_config_remove;
struct device_iot_config_clear device_iot_config_clear;
struct device_iot_config_flush device_iot_config_flush;
struct device_debug_iot_config_dump device_debug_iot_config_dump;

}  // namespace device_iot_config
}  // namespace mock
}  // namespace test

// Mocked functions, if any
bool device_iot_config_get_int(const std::string& section, const std::string& key,
                     int& value) {
  inc_func_call_count(__func__);
  return test::mock::device_iot_config::device_iot_config_get_int(section, key, value);
}

bool device_iot_config_set_int(const std::string& section,
                               const std::string& key, int value) {
  inc_func_call_count(__func__);
  return test::mock::device_iot_config::device_iot_config_set_int(section, key, value);
}

bool device_iot_config_int_add_one(const std::string& section,
                               const std::string& key) {
  inc_func_call_count(__func__);
  return test::mock::device_iot_config::device_iot_config_int_add_one(section, key);
}

bool device_iot_config_get_hex(const std::string& section,
                               const std::string& key, int& value) {
  inc_func_call_count(__func__);
  return test::mock::device_iot_config::device_iot_config_get_hex(section, key, value);
}

bool device_iot_config_set_hex(const std::string& section,
                               const std::string& key, int value, int byte_num) {
  inc_func_call_count(__func__);
  return test::mock::device_iot_config::device_iot_config_set_hex(section, key, value, byte_num);
}

bool device_iot_config_set_hex_if_greater(const std::string& section,
                               const std::string& key, int value, int byte_num) {
  inc_func_call_count(__func__);
  return test::mock::device_iot_config::device_iot_config_set_hex_if_greater(section, key, value, byte_num);
}

bool device_iot_config_get_str(const std::string& section,
                               const std::string& key, char* value,
                               int* size_bytes) {
  inc_func_call_count(__func__);
  return test::mock::device_iot_config::device_iot_config_get_str(section, key, value, size_bytes);
}

bool device_iot_config_set_str(const std::string& section,
                               const std::string& key,
                               const std::string& value) {
  inc_func_call_count(__func__);
  return test::mock::device_iot_config::device_iot_config_set_str(section, key, value);
}

bool device_iot_config_get_bin(const std::string& section,
                               const std::string& key, uint8_t* value,
                               size_t* length) {
  inc_func_call_count(__func__);
  return test::mock::device_iot_config::device_iot_config_get_bin(section, key, value, length);
}

bool device_iot_config_set_bin(const std::string& section,
                               const std::string& key, const uint8_t* value,
                               size_t length) {
  inc_func_call_count(__func__);
  return test::mock::device_iot_config::device_iot_config_set_bin(section, key, value, length);
}

size_t device_iot_config_get_bin_length(const std::string& section,
                                        const std::string& key) {
  inc_func_call_count(__func__);
  return test::mock::device_iot_config::device_iot_config_get_bin_length(section, key);
}

bool device_iot_config_has_section(const std::string& section) {
  inc_func_call_count(__func__);
  return test::mock::device_iot_config::device_iot_config_has_section(section);
}

bool device_iot_config_exist(const std::string& section,
                             const std::string& key) {
  inc_func_call_count(__func__);
  return test::mock::device_iot_config::device_iot_config_exist(section, key);
}

bool device_iot_config_remove(const std::string& section,
                              const std::string& key) {
  inc_func_call_count(__func__);
  return test::mock::device_iot_config::device_iot_config_remove(section, key);
}

void device_iot_config_flush(void) {
  inc_func_call_count(__func__);
  test::mock::device_iot_config::device_iot_config_flush();
}

bool device_iot_config_clear(void) {
  inc_func_call_count(__func__);
  return test::mock::device_iot_config::device_iot_config_clear();
}

void device_debug_iot_config_dump(int fd) {
  inc_func_call_count(__func__);
  test::mock::device_iot_config::device_debug_iot_config_dump(fd);
}
