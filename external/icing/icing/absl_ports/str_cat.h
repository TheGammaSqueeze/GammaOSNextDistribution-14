// Copyright (C) 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef ICING_ABSL_PORTS_STR_CAT_H_
#define ICING_ABSL_PORTS_STR_CAT_H_

#include <cstdarg>
#include <cstring>
#include <string>
#include <string_view>
#include <vector>

namespace icing {
namespace lib {
namespace absl_ports {

// Appends the content of s to the char buffer starting at out and returns the
// address of the first character after the content copied from s.
// REQUIRES: out is large enough to hold all content from s.
char* Append(char* out, std::string_view s);

// A port of absl::StrCat.
//
// Merges given strings or numbers, using no delimiter(s), returning the merged
// result as a string.
//
// Unlike absl::StrCat, this version only accepts string_views. For converting
// numerics to strings, use StringPrintf.
//
// Separate implementations for 2-4 arguments are provided separately from the
// variadic definition, just like absl does. This is a minor optimization to
// avoid constructing a vector and copying all string_view params.
std::string StrCat(std::string_view a, std::string_view b);
std::string StrCat(std::string_view a, std::string_view b, std::string_view c);
std::string StrCat(std::string_view a, std::string_view b, std::string_view c,
                   std::string_view d);

std::string StrCatPieces(std::vector<std::string_view> pieces);

template <typename... AV>
std::string StrCat(const AV&... args) {
  return StrCatPieces({static_cast<const std::string_view&>(args)...});
}

// A port of absl::StrAppend.
//
// Appends a string or set of strings to an existing string, in a similar
// fashion to `StrCat()`.
//
// Unlike absl::StrAppend, this version only accepts string_views. For
// converting numerics to strings, use StringPrintf.
void StrAppend(std::string* dest, std::string_view a);
void StrAppend(std::string* dest, std::string_view a, std::string_view b);
void StrAppend(std::string* dest, std::string_view a, std::string_view b,
               std::string_view c);
void StrAppend(std::string* dest, std::string_view a, std::string_view b,
               std::string_view c, std::string_view d);

void StrAppendPieces(std::string* dest, std::vector<std::string_view> pieces);

template <typename... AV>
void StrAppend(std::string* dest, const AV&... args) {
  StrAppendPieces(dest, {static_cast<const std::string_view&>(args)...});
}

}  // namespace absl_ports
}  // namespace lib
}  // namespace icing

#endif  // ICING_ABSL_PORTS_STR_CAT_H_
