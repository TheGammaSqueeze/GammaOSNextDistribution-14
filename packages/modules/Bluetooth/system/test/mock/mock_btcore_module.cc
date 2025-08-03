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
 *   Functions generated:7
 *
 *  mockcify.pl ver 0.3.0
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_btcore_module.h"

#include "test/common/mock_functions.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace btcore_module {

// Function state capture and return values, if needed
struct get_module get_module;
struct module_clean_up module_clean_up;
struct module_init module_init;
struct module_management_start module_management_start;
struct module_management_stop module_management_stop;
struct module_shut_down module_shut_down;
struct module_start_up module_start_up;

}  // namespace btcore_module
}  // namespace mock
}  // namespace test

// Mocked functions, if any
const module_t* get_module(const char* name) {
  inc_func_call_count(__func__);
  return test::mock::btcore_module::get_module(name);
}
void module_clean_up(const module_t* module) {
  inc_func_call_count(__func__);
  test::mock::btcore_module::module_clean_up(module);
}
bool module_init(const module_t* module) {
  inc_func_call_count(__func__);
  return test::mock::btcore_module::module_init(module);
}
void module_management_start(void) {
  inc_func_call_count(__func__);
  test::mock::btcore_module::module_management_start();
}
void module_management_stop(void) {
  inc_func_call_count(__func__);
  test::mock::btcore_module::module_management_stop();
}
void module_shut_down(const module_t* module) {
  inc_func_call_count(__func__);
  test::mock::btcore_module::module_shut_down(module);
}
bool module_start_up(const module_t* module) {
  inc_func_call_count(__func__);
  return test::mock::btcore_module::module_start_up(module);
}
// Mocked functions complete
// END mockcify generation
