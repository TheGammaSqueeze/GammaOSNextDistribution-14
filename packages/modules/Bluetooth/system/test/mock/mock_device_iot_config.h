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
#include <cstdint>
#include <functional>
#include <string>

namespace test {
namespace mock {
namespace device_iot_config {

// Shared state between mocked functions and tests
// Name: device_iot_config_get_int
// Params: const std::string& section, const std::string& key, int& value
// Return: bool
struct device_iot_config_get_int {
  bool return_value{false};
  std::function<bool(const std::string& section, const std::string& key,
                     int& value)>
      body{[this](const std::string& /* section */,
                  const std::string& /* key */,
                  int& /* value */) { return return_value; }};
  bool operator()(const std::string& section, const std::string& key,
                  int& value) {
    return body(section, key, value);
  };
};
extern struct device_iot_config_get_int device_iot_config_get_int;

// Name: device_iot_config_set_int
// Params: const std::string& section, const std::string& key, int& value
// Return: bool
struct device_iot_config_set_int {
  bool return_value{false};
  std::function<bool(const std::string& section, const std::string& key,
                     int value)>
      body{[this](const std::string& /* section */,
                  const std::string& /* key */,
                  int /* value */) { return return_value; }};
  bool operator()(const std::string& section, const std::string& key,
                  int value) {
    return body(section, key, value);
  };
};
extern struct device_iot_config_set_int device_iot_config_set_int;

// Name: device_iot_config_int_add_one
// Params: const std::string& section, const std::string& key
// Return: bool
struct device_iot_config_int_add_one {
  bool return_value{false};
  std::function<bool(const std::string& section, const std::string& key)> body{
      [this](const std::string& /* section */, const std::string& /* key */) {
        return return_value;
      }};
  bool operator()(const std::string& section, const std::string& key) {
    return body(section, key);
  };
};
extern struct device_iot_config_int_add_one device_iot_config_int_add_one;

// Name: device_iot_config_get_hex
// Params: const std::string& section, const std::string& key, int& value
// Return: bool
struct device_iot_config_get_hex {
  bool return_value{false};
  std::function<bool(const std::string& section, const std::string& key,
                     int& value)>
      body{[this](const std::string& /* section */,
                  const std::string& /* key */,
                  int& /* value */) { return return_value; }};
  bool operator()(const std::string& section, const std::string& key,
                  int& value) {
    return body(section, key, value);
  };
};
extern struct device_iot_config_get_hex device_iot_config_get_hex;

// Name: device_iot_config_set_hex
// Params: const std::string& section, const std::string& key, int value, int
// byte_num
// Return: bool
struct device_iot_config_set_hex {
  bool return_value{false};
  std::function<bool(const std::string& section, const std::string& key,
                     int value, int byte_num)>
      body{[this](const std::string& /* section */,
                  const std::string& /* key */, int /* value */,
                  int /* byte_num */) { return return_value; }};
  bool operator()(const std::string& section, const std::string& key, int value,
                  int byte_num) {
    return body(section, key, value, byte_num);
  };
};
extern struct device_iot_config_set_hex device_iot_config_set_hex;

// Name: device_iot_config_set_hex_if_greater
// Params: const std::string& section, const std::string& key, int value, int
// byte_num
// Return: bool
struct device_iot_config_set_hex_if_greater {
  bool return_value{false};
  std::function<bool(const std::string& section, const std::string& key,
                     int value, int byte_num)>
      body{[this](const std::string& /* section */,
                  const std::string& /* key */, int /* value */,
                  int /* byte_num */) { return return_value; }};
  bool operator()(const std::string& section, const std::string& key, int value,
                  int byte_num) {
    return body(section, key, value, byte_num);
  };
};
extern struct device_iot_config_set_hex_if_greater
    device_iot_config_set_hex_if_greater;

// Name: device_iot_config_get_str
// Params: const std::string& section, const std::string& key, char* value, int*
// size_bytes
// Return: bool
struct device_iot_config_get_str {
  bool return_value{false};
  std::function<bool(const std::string& section, const std::string& key,
                     char* value, int* size_bytes)>
      body{[this](const std::string& /* section */,
                  const std::string& /* key */, char* /* value */,
                  int* /* size_bytes */) { return return_value; }};
  bool operator()(const std::string& section, const std::string& key,
                  char* value, int* size_bytes) {
    return body(section, key, value, size_bytes);
  };
};
extern struct device_iot_config_get_str device_iot_config_get_str;

// Name: device_iot_config_set_str
// Params: const std::string& section, const std::string& key, const
// std::string& value
// Return: bool
struct device_iot_config_set_str {
  bool return_value{false};
  std::function<bool(const std::string& section, const std::string& key,
                     const std::string& value)>
      body{[this](const std::string& /* section */,
                  const std::string& /* key */,
                  const std::string& /* value */) { return return_value; }};
  bool operator()(const std::string& section, const std::string& key,
                  const std::string& value) {
    return body(section, key, value);
  };
};
extern struct device_iot_config_set_str device_iot_config_set_str;

// Name: device_iot_config_get_bin
// Params: const std::string& section, const std::string& key, uint8_t* value,
// size_t* length
// Return: bool
struct device_iot_config_get_bin {
  bool return_value{false};
  std::function<bool(const std::string& section, const std::string& key,
                     uint8_t* value, size_t* length)>
      body{[this](const std::string& /* section */,
                  const std::string& /* key */, uint8_t* /* value */,
                  size_t* /* length */) { return return_value; }};
  bool operator()(const std::string& section, const std::string& key,
                  uint8_t* value, size_t* length) {
    return body(section, key, value, length);
  };
};
extern struct device_iot_config_get_bin device_iot_config_get_bin;

// Name: device_iot_config_set_bin
// Params: const std::string& section, const std::string& key, const uint8_t*
// value, size_t length
// Return: bool
struct device_iot_config_set_bin {
  bool return_value{false};
  std::function<bool(const std::string& section, const std::string& key,
                     const uint8_t* value, size_t length)>
      body{[this](const std::string& /* section */,
                  const std::string& /* key */, const uint8_t* /* value */,
                  size_t /* length */) { return return_value; }};
  bool operator()(const std::string& section, const std::string& key,
                  const uint8_t* value, size_t length) {
    return body(section, key, value, length);
  };
};
extern struct device_iot_config_set_bin device_iot_config_set_bin;

// Name: device_iot_config_get_bin_length
// Params: const std::string& section, const std::string& key
// Return: size_t
struct device_iot_config_get_bin_length {
  size_t return_value{0};
  std::function<size_t(const std::string& section, const std::string& key)>
      body{[this](const std::string& /* section */,
                  const std::string& /* key */) { return return_value; }};
  size_t operator()(const std::string& section, const std::string& key) {
    return body(section, key);
  };
};
extern struct device_iot_config_get_bin_length device_iot_config_get_bin_length;

// Name: device_iot_config_has_section
// Params: const std::string& section
// Return: bool
struct device_iot_config_has_section {
  bool return_value{false};
  std::function<bool(const std::string& section)> body{
      [this](const std::string& /* section */) { return return_value; }};
  bool operator()(const std::string& section) { return body(section); };
};
extern struct device_iot_config_has_section device_iot_config_has_section;

// Name: device_iot_config_exist
// Params: const std::string& section, const std::string& key
// Return: bool
struct device_iot_config_exist {
  bool return_value{false};
  std::function<bool(const std::string& section, const std::string& key)> body{
      [this](const std::string& /* section */, const std::string& /* key */) {
        return return_value;
      }};
  bool operator()(const std::string& section, const std::string& key) {
    return body(section, key);
  };
};
extern struct device_iot_config_exist device_iot_config_exist;

// Name: device_iot_config_remove
// Params: const std::string& section, const std::string& key
// Return: bool
struct device_iot_config_remove {
  bool return_value{false};
  std::function<bool(const std::string& section, const std::string& key)> body{
      [this](const std::string& /* section */, const std::string& /* key */) {
        return return_value;
      }};
  bool operator()(const std::string& section, const std::string& key) {
    return body(section, key);
  };
};
extern struct device_iot_config_remove device_iot_config_remove;

// Name: device_iot_config_clear
// Params: void
// Return: bool
struct device_iot_config_clear {
  bool return_value{false};
  std::function<bool(void)> body{[this]() { return return_value; }};
  bool operator()(void) { return body(); };
};
extern struct device_iot_config_clear device_iot_config_clear;

// Name: device_iot_config_flush
// Params: void
// Return: void
struct device_iot_config_flush {
  std::function<void(void)> body{[]() {}};
  void operator()(void){ body(); };
};
extern struct device_iot_config_flush device_iot_config_flush;

// Name: device_debug_iot_config_dump
// Params: int fd
// Return: void
struct device_debug_iot_config_dump {
  std::function<void(int fd)> body{[](int /* fd */) {}};
  void operator()(int fd) { body(fd); };
};
extern struct device_debug_iot_config_dump device_debug_iot_config_dump;

}  // namespace device_iot_config
}  // namespace mock
}  // namespace test
