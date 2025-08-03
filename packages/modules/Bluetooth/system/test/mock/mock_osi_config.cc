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
// Mock include file to share data between tests and mock
#include "test/mock/mock_osi_config.h"

#include <cstdint>
#include <string>

#include "test/common/mock_functions.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace osi_config {

// Function state capture and return values, if needed
struct checksum_read checksum_read;
struct checksum_save checksum_save;
struct config_get_bool config_get_bool;
struct config_get_int config_get_int;
struct config_get_string config_get_string;
struct config_get_uint64 config_get_uint64;
struct config_has_key config_has_key;
struct config_has_section config_has_section;
struct config_new config_new;
struct config_new_clone config_new_clone;
struct config_new_empty config_new_empty;
struct config_remove_key config_remove_key;
struct config_remove_section config_remove_section;
struct config_save config_save;
struct config_set_bool config_set_bool;
struct config_set_int config_set_int;
struct config_set_string config_set_string;
struct config_set_uint64 config_set_uint64;
struct config_t_Find config_t_Find;
struct config_t_Has config_t_Has;
struct section_t_Find section_t_Find;
struct section_t_Has section_t_Has;
struct section_t_Set section_t_Set;

}  // namespace osi_config
}  // namespace mock
}  // namespace test

// Mocked functions, if any
std::string checksum_read(const char* filename) {
  inc_func_call_count(__func__);
  return test::mock::osi_config::checksum_read(filename);
}
bool checksum_save(const std::string& checksum, const std::string& filename) {
  inc_func_call_count(__func__);
  return test::mock::osi_config::checksum_save(checksum, filename);
}
bool config_get_bool(const config_t& config, const std::string& section,
                     const std::string& key, bool def_value) {
  inc_func_call_count(__func__);
  return test::mock::osi_config::config_get_bool(config, section, key,
                                                 def_value);
}
int config_get_int(const config_t& config, const std::string& section,
                   const std::string& key, int def_value) {
  inc_func_call_count(__func__);
  return test::mock::osi_config::config_get_int(config, section, key,
                                                def_value);
}
const std::string* config_get_string(const config_t& config,
                                     const std::string& section,
                                     const std::string& key,
                                     const std::string* def_value) {
  inc_func_call_count(__func__);
  return test::mock::osi_config::config_get_string(config, section, key,
                                                   def_value);
}
uint64_t config_get_uint64(const config_t& config, const std::string& section,
                           const std::string& key, uint64_t def_value) {
  inc_func_call_count(__func__);
  return test::mock::osi_config::config_get_uint64(config, section, key,
                                                   def_value);
}
bool config_has_key(const config_t& config, const std::string& section,
                    const std::string& key) {
  inc_func_call_count(__func__);
  return test::mock::osi_config::config_has_key(config, section, key);
}
bool config_has_section(const config_t& config, const std::string& section) {
  inc_func_call_count(__func__);
  return test::mock::osi_config::config_has_section(config, section);
}
std::unique_ptr<config_t> config_new(const char* filename) {
  inc_func_call_count(__func__);
  return test::mock::osi_config::config_new(filename);
}
std::unique_ptr<config_t> config_new_clone(const config_t& src) {
  inc_func_call_count(__func__);
  return test::mock::osi_config::config_new_clone(src);
}
std::unique_ptr<config_t> config_new_empty(void) {
  inc_func_call_count(__func__);
  return test::mock::osi_config::config_new_empty();
}
bool config_remove_key(config_t* config, const std::string& section,
                       const std::string& key) {
  inc_func_call_count(__func__);
  return test::mock::osi_config::config_remove_key(config, section, key);
}
bool config_remove_section(config_t* config, const std::string& section) {
  inc_func_call_count(__func__);
  return test::mock::osi_config::config_remove_section(config, section);
}
bool config_save(const config_t& config, const std::string& filename) {
  inc_func_call_count(__func__);
  return test::mock::osi_config::config_save(config, filename);
}
void config_set_bool(config_t* config, const std::string& section,
                     const std::string& key, bool value) {
  inc_func_call_count(__func__);
  test::mock::osi_config::config_set_bool(config, section, key, value);
}
void config_set_int(config_t* config, const std::string& section,
                    const std::string& key, int value) {
  inc_func_call_count(__func__);
  test::mock::osi_config::config_set_int(config, section, key, value);
}
void config_set_string(config_t* config, const std::string& section,
                       const std::string& key, const std::string& value) {
  inc_func_call_count(__func__);
  test::mock::osi_config::config_set_string(config, section, key, value);
}
void config_set_uint64(config_t* config, const std::string& section,
                       const std::string& key, uint64_t value) {
  inc_func_call_count(__func__);
  test::mock::osi_config::config_set_uint64(config, section, key, value);
}
std::list<section_t>::iterator config_t::Find(const std::string& section) {
  inc_func_call_count(__func__);
  return test::mock::osi_config::config_t_Find(section);
}
bool config_t::Has(const std::string& key) {
  inc_func_call_count(__func__);
  return test::mock::osi_config::config_t_Has(key);
}
std::list<entry_t>::iterator section_t::Find(const std::string& key) {
  inc_func_call_count(__func__);
  return test::mock::osi_config::section_t_Find(key);
}
bool section_t::Has(const std::string& key) {
  inc_func_call_count(__func__);
  return test::mock::osi_config::section_t_Has(key);
}
void section_t::Set(std::string key, std::string value) {
  inc_func_call_count(__func__);
  test::mock::osi_config::section_t_Set(key, value);
}
// Mocked functions complete
// END mockcify generation
