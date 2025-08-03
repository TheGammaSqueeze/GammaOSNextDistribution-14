// Copyright (C) 2022 Google LLC
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

#include <cstdint>
#include <memory>
#include <string_view>

#include "icing/query/advanced_query_parser/lexer.h"

namespace icing {
namespace lib {

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  std::string_view text(reinterpret_cast<const char*>(data), size);

  std::unique_ptr<Lexer> lexer =
      std::make_unique<Lexer>(text, Lexer::Language::QUERY);
  lexer->ExtractTokens();

  lexer = std::make_unique<Lexer>(text, Lexer::Language::SCORING);
  lexer->ExtractTokens();
  return 0;
}

}  // namespace lib
}  // namespace icing
