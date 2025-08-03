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
 *   Functions generated:24
 *
 *  mockcify.pl ver 0.3.0
 */

#include <cstdint>
#include <functional>
#include <string>

// Original included files, if any
#include <base/files/file_util.h>
#include <base/logging.h>
#include <fcntl.h>
#include <libgen.h>
#include <log/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <unistd.h>

#include "osi/include/config.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace osi_config {

// Shared state between mocked functions and tests
// Name: checksum_read
// Params: const char* filename
// Return: std::string
struct checksum_read {
  std::string return_value{std::string()};
  std::function<std::string(const char* filename)> body{
      [this](const char* /* filename */) { return return_value; }};
  std::string operator()(const char* filename) { return body(filename); };
};
extern struct checksum_read checksum_read;

// Name: checksum_save
// Params: const std::string& checksum, const std::string& filename
// Return: bool
struct checksum_save {
  bool return_value{false};
  std::function<bool(const std::string& checksum, const std::string& filename)>
      body{[this](const std::string& /* checksum */,
                  const std::string& /* filename */) { return return_value; }};
  bool operator()(const std::string& checksum, const std::string& filename) {
    return body(checksum, filename);
  };
};
extern struct checksum_save checksum_save;

// Name: config_get_bool
// Params: const config_t& config, const std::string& section, const
// std::string& key, bool def_value Return: bool
struct config_get_bool {
  bool return_value{false};
  std::function<bool(const config_t& config, const std::string& section,
                     const std::string& key, bool def_value)>
      body{[this](const config_t& /* config */,
                  const std::string& /* section */,
                  const std::string& /* key */,
                  bool /* def_value */) { return return_value; }};
  bool operator()(const config_t& config, const std::string& section,
                  const std::string& key, bool def_value) {
    return body(config, section, key, def_value);
  };
};
extern struct config_get_bool config_get_bool;

// Name: config_get_int
// Params: const config_t& config, const std::string& section, const
// std::string& key, int def_value Return: int
struct config_get_int {
  int return_value{0};
  std::function<int(const config_t& config, const std::string& section,
                    const std::string& key, int def_value)>
      body{[this](const config_t& /* config */,
                  const std::string& /* section */,
                  const std::string& /* key */,
                  int /* def_value */) { return return_value; }};
  int operator()(const config_t& config, const std::string& section,
                 const std::string& key, int def_value) {
    return body(config, section, key, def_value);
  };
};
extern struct config_get_int config_get_int;

// Name: config_get_string
// Params: const config_t& config, const std::string& section, const
// std::string& key, const std::string* def_value Return: const std::string*
struct config_get_string {
  const std::string* return_value{0};
  std::function<const std::string*(
      const config_t& config, const std::string& section,
      const std::string& key, const std::string* def_value)>
      body{[this](const config_t& /* config */,
                  const std::string& /* section */,
                  const std::string& /* key */,
                  const std::string* /* def_value */) { return return_value; }};
  const std::string* operator()(const config_t& config,
                                const std::string& section,
                                const std::string& key,
                                const std::string* def_value) {
    return body(config, section, key, def_value);
  };
};
extern struct config_get_string config_get_string;

// Name: config_get_uint64
// Params: const config_t& config, const std::string& section, const
// std::string& key, uint64_t def_value Return: uint64_t
struct config_get_uint64 {
  uint64_t return_value{0};
  std::function<uint64_t(const config_t& config, const std::string& section,
                         const std::string& key, uint64_t def_value)>
      body{[this](const config_t& /* config */,
                  const std::string& /* section */,
                  const std::string& /* key */,
                  uint64_t /* def_value */) { return return_value; }};
  uint64_t operator()(const config_t& config, const std::string& section,
                      const std::string& key, uint64_t def_value) {
    return body(config, section, key, def_value);
  };
};
extern struct config_get_uint64 config_get_uint64;

// Name: config_has_key
// Params: const config_t& config, const std::string& section, const
// std::string& key Return: bool
struct config_has_key {
  bool return_value{false};
  std::function<bool(const config_t& config, const std::string& section,
                     const std::string& key)>
      body{[this](const config_t& /* config */,
                  const std::string& /* section */,
                  const std::string& /* key */) { return return_value; }};
  bool operator()(const config_t& config, const std::string& section,
                  const std::string& key) {
    return body(config, section, key);
  };
};
extern struct config_has_key config_has_key;

// Name: config_has_section
// Params: const config_t& config, const std::string& section
// Return: bool
struct config_has_section {
  bool return_value{false};
  std::function<bool(const config_t& /* config */,
                     const std::string& /* section */)>
      body{[this](const config_t& /* config */,
                  const std::string& /* section */) { return return_value; }};
  bool operator()(const config_t& config, const std::string& section) {
    return body(config, section);
  };
};
extern struct config_has_section config_has_section;

// Name: config_new
// Params: const char* filename
// Return: std::unique_ptr<config_t>
struct config_new {
  std::function<std::unique_ptr<config_t>(const char* filename)> body{
      [](const char* /* filename */) { return std::make_unique<config_t>(); }};
  std::unique_ptr<config_t> operator()(const char* filename) {
    return body(filename);
  };
};
extern struct config_new config_new;

// Name: config_new_clone
// Params: const config_t& src
// Return: std::unique_ptr<config_t>
struct config_new_clone {
  std::function<std::unique_ptr<config_t>(const config_t& src)> body{
      [](const config_t& /* src */) { return std::make_unique<config_t>(); }};
  std::unique_ptr<config_t> operator()(const config_t& src) {
    return body(src);
  };
};
extern struct config_new_clone config_new_clone;

// Name: config_new_empty
// Params: void
// Return: std::unique_ptr<config_t>
struct config_new_empty {
  std::function<std::unique_ptr<config_t>(void)> body{
      [](void) { return std::make_unique<config_t>(); }};
  std::unique_ptr<config_t> operator()(void) { return body(); };
};
extern struct config_new_empty config_new_empty;

// Name: config_remove_key
// Params: config_t* config, const std::string& section, const std::string& key
// Return: bool
struct config_remove_key {
  bool return_value{false};
  std::function<bool(config_t* config, const std::string& section,
                     const std::string& key)>
      body{[this](config_t* /* config */, const std::string& /* section */,
                  const std::string& /* key */) { return return_value; }};
  bool operator()(config_t* config, const std::string& section,
                  const std::string& key) {
    return body(config, section, key);
  };
};
extern struct config_remove_key config_remove_key;

// Name: config_remove_section
// Params: config_t* config, const std::string& section
// Return: bool
struct config_remove_section {
  bool return_value{false};
  std::function<bool(config_t* config, const std::string& section)> body{
      [this](config_t* /* config */, const std::string& /* section */) {
        return return_value;
      }};
  bool operator()(config_t* config, const std::string& section) {
    return body(config, section);
  };
};
extern struct config_remove_section config_remove_section;

// Name: config_save
// Params: const config_t& config, const std::string& filename
// Return: bool
struct config_save {
  bool return_value{false};
  std::function<bool(const config_t& config, const std::string& filename)> body{
      [this](const config_t& /* config */, const std::string& /* filename */) {
        return return_value;
      }};
  bool operator()(const config_t& config, const std::string& filename) {
    return body(config, filename);
  };
};
extern struct config_save config_save;

// Name: config_set_bool
// Params: config_t* config, const std::string& section, const std::string& key,
// bool value Return: void
struct config_set_bool {
  std::function<void(config_t* config, const std::string& section,
                     const std::string& key, bool value)>
      body{[](config_t* /* config */, const std::string& /* section */,
              const std::string& /* key */, bool /* value */) {}};
  void operator()(config_t* config, const std::string& section,
                  const std::string& key, bool value) {
    body(config, section, key, value);
  };
};
extern struct config_set_bool config_set_bool;

// Name: config_set_int
// Params: config_t* config, const std::string& section, const std::string& key,
// int value Return: void
struct config_set_int {
  std::function<void(config_t* config, const std::string& section,
                     const std::string& key, int value)>
      body{[](config_t* /* config */, const std::string& /* section */,
              const std::string& /* key */, int /* value */) {}};
  void operator()(config_t* config, const std::string& section,
                  const std::string& key, int value) {
    body(config, section, key, value);
  };
};
extern struct config_set_int config_set_int;

// Name: config_set_string
// Params: config_t* config, const std::string& section, const std::string& key,
// const std::string& value Return: void
struct config_set_string {
  std::function<void(config_t* config, const std::string& section,
                     const std::string& key, const std::string& value)>
      body{[](config_t* /* config */, const std::string& /* section */,
              const std::string& /* key */, const std::string& /* value */) {}};
  void operator()(config_t* config, const std::string& section,
                  const std::string& key, const std::string& value) {
    body(config, section, key, value);
  };
};
extern struct config_set_string config_set_string;

// Name: config_set_uint64
// Params: config_t* config, const std::string& section, const std::string& key,
// uint64_t value Return: void
struct config_set_uint64 {
  std::function<void(config_t* config, const std::string& section,
                     const std::string& key, uint64_t value)>
      body{[](config_t* /* config */, const std::string& /* section */,
              const std::string& /* key */, uint64_t /* value */) {}};
  void operator()(config_t* config, const std::string& section,
                  const std::string& key, uint64_t value) {
    body(config, section, key, value);
  };
};
extern struct config_set_uint64 config_set_uint64;

// Name: config_t::Find
// Params: const std::string& section
// Return: std::list<section_t>::iterator
struct config_t_Find {
  std::list<section_t> section_;
  std::function<std::list<section_t>::iterator(const std::string& section)>
      body{[this](const std::string& /* section */) {
        return section_.begin();
      }};
  std::list<section_t>::iterator operator()(const std::string& section) {
    return body(section);
  };
};
extern struct config_t_Find config_t_Find;

// Name: config_t_Has
// Params: const std::string& key
// Return: bool
struct config_t_Has {
  bool return_value{false};
  std::function<bool(const std::string& key)> body{
      [this](const std::string& /* key */) { return return_value; }};
  bool operator()(const std::string& key) { return body(key); };
};
extern struct config_t_Has config_t_Has;

// Name: section_t_Find
// Params: const std::string& key
// Return: std::list<entry_t>::iterator
struct section_t_Find {
  std::list<entry_t> list_;
  std::function<std::list<entry_t>::iterator(const std::string& key)> body{
      [this](const std::string& /* key */) { return list_.begin(); }};
  std::list<entry_t>::iterator operator()(const std::string& key) {
    return body(key);
  };
};
extern struct section_t_Find section_t_Find;

// Name: section_t_Has
// Params: const std::string& key
// Return: bool
struct section_t_Has {
  bool return_value{false};
  std::function<bool(const std::string& key)> body{
      [this](const std::string& /* key */) { return return_value; }};
  bool operator()(const std::string& key) { return body(key); };
};
extern struct section_t_Has section_t_Has;

// Name: section_t_Set
// Params: std::string key, std::string value
// Return: void
struct section_t_Set {
  std::function<void(std::string key, std::string value)> body{
      [](std::string /* key */, std::string /* value */) {}};
  void operator()(std::string key, std::string value) { body(key, value); };
};
extern struct section_t_Set section_t_Set;

}  // namespace osi_config
}  // namespace mock
}  // namespace test

// END mockcify generation
