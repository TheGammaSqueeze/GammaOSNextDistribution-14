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

#ifndef SHELL_AS_COMMAND_LINE_H_
#define SHELL_AS_COMMAND_LINE_H_

#include "./context.h"

namespace shell_as {

// Parse command line options into a target security context and arguments that
// can be passed to ExecuteInContext.
//
// The value of execv_args will either point to a sub-array of argv or to a
// statically allocated default value. In both cases the caller should /not/
// free the memory.
//
// Returns true on success and false if there is a problem parsing options.
bool ParseOptions(const int argc, char* const argv[], bool* verbose,
                  SecurityContext* context, char* const* execv_args[]);
}  // namespace shell_as

#endif  // SHELL_AS_COMMAND_LINE_H_
