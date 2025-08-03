/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <sys/cdefs.h>

// Some apps ship DSOs with a main function, and it works because the main function in app_process
// isn't exported. Ensure that this keeps working. (http://b/190100879).
//
// This function is in a separate C++ file to try to guard against the -fno-semantic-interposition
// optimization that Clang enables by default.
extern "C" int main(int argc __unused, char* argv[] __unused) {
  return 42;
}
