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

#ifndef ART_LIBARTTOOLS_INCLUDE_TOOLS_TOOLS_H_
#define ART_LIBARTTOOLS_INCLUDE_TOOLS_TOOLS_H_

#include <string>
#include <string_view>
#include <vector>

namespace art {
namespace tools {

// Searches in a filesystem, starting from `root_dir`. Returns all regular files (i.e., excluding
// directories, symlinks, etc.) that match at least one pattern in `patterns`. Each pattern is an
// absolute path that contains zero or more wildcards. The scan does not follow symlinks to
// directories.
//
// Supported wildcards are:
// - Those documented in glob(7)
// - '**': Matches zero or more path elements. This is only recognised by itself as a path segment.
//
// For simplicity and efficiency, at most one '**' is allowed.
std::vector<std::string> Glob(const std::vector<std::string>& patterns,
                              std::string_view root_dir = "/");

// Escapes a string so that it's not recognized as a wildcard pattern for `Glob`.
std::string EscapeGlob(const std::string& str);

}  // namespace tools
}  // namespace art

#endif  // ART_LIBARTTOOLS_INCLUDE_TOOLS_TOOLS_H_
