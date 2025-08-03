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
 */

#include "main/shim/dumpsys.h"
#include "shim/dumpsys.h"
#include "test/common/mock_functions.h"

void bluetooth::shim::RegisterDumpsysFunction(const void* /* token */,
                                              DumpsysFunction /* func */) {
  inc_func_call_count(__func__);
}
void bluetooth::shim::Dump(int /* fd */, const char** /* args */) {
  inc_func_call_count(__func__);
}
void bluetooth::shim::UnregisterDumpsysFunction(const void* /* token */) {
  inc_func_call_count(__func__);
}

void bluetooth::shim::Dumpsys::Dump(int /* fd */, const char** /* args */) {}
