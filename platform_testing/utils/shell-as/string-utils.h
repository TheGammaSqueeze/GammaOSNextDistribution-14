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

#ifndef SHELL_AS_STRING_UTILS_H_
#define SHELL_AS_STRING_UTILS_H_

#include <unistd.h>

#include <vector>

namespace shell_as {

// Parses a string into an unsigned 32bit int value. Returns true on success and
// false otherwise.
bool StringToUInt32(const char* s, uint32_t* i);

// Parses a string into a unsigned 64bit int value. Returns true on success and
// false otherwise.
bool StringToUInt64(const char* s, uint64_t* i);

// Splits a line of uid_t/guid_t values by a given separator and returns the
// integer values in a vector.
//
// The separators string may contain multiple characters and is treated as a set
// of possible separating characters.
//
// If num_to_skip is non-zero, then that many entries will be skipped after
// splitting the line and before parsing the values as integers. This is useful
// if the line has a prefix such as "Gid: 1 2 3 4".
//
// Returns true on success and false otherwise.
bool SplitIdsAndSkip(char* line, const char* separators, int num_to_skip,
                     std::vector<uid_t>* ids);

}  // namespace shell_as

#endif  // SHELL_AS_STRING_UTILS_H_
