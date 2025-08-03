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

#ifndef SHELL_AS_EXECUTE_H_
#define SHELL_AS_EXECUTE_H_

#include "context.h"

namespace shell_as {

// Executes a command in the given security context.
//
// The executable_and_args parameter must contain at least two values. The first
// value is the path to the executable to run and the last value must be null.
// Additional arguments are passed to the executable as command line options.
//
// Returns true if the executable was run and false otherwise.
bool ExecuteInContext(char* const executable_and_args[],
                      const SecurityContext* context);
}  // namespace shell_as

#endif  // SHELL_AS_EXECUTE_H_
