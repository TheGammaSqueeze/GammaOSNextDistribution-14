/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef SHELL_AS_ELF_H_
#define SHELL_AS_ELF_H_

#include <sys/types.h>

namespace shell_as {

// Sets entry_address to the process's entry point.
//
// This method assumes that PIE binaries are executing with ADDR_NO_RANDOMIZE.
//
// The is_arm_mode flag is set to true IFF the architecture is 32bit ARM and the
// expected instruction set for code located at the entry address is not-thumb.
// It is false for all other cases.
bool GetElfEntryPoint(const pid_t process_id, uint64_t* entry_address,
                      bool* is_arm_mode);
}  // namespace shell_as

#endif  // SHELL_AS_ELF_H_
