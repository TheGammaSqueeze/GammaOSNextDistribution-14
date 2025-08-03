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

// Copyright 2012 Google Inc. All Rights Reserved.
// Author: sbanacho@google.com (Scott Banachowski)
//
// This header is used to declare typedefs and constants
// that are shared across several modules.

#ifndef ICING_LEGACY_CORE_ICING_CORE_TYPES_H_
#define ICING_LEGACY_CORE_ICING_CORE_TYPES_H_

#include <cstddef>  // size_t not defined implicitly for all platforms.
#include <cstdint>
#include <vector>

#include "icing/legacy/core/icing-compat.h"

namespace icing {
namespace lib {

enum IcingTokenizerType {
  // Group tokens by clustering characters that match IsRuneLetter
  // together.
  TOKENIZER_PLAIN,
  // Marks tokens that look like html tags (enclosed in <>) or entities
  // (enclosed in &;).
  TOKENIZER_HTML,
  // Skip over html tags.
  TOKENIZER_HTML_IGNORE_TAGS,
  // Email address parsing.  Marks the address, local address and rfc token
  // portion of from, to, cc headers.  This assumes the rfc822 tokens
  // were pre-processed to canonical form by the Android Java Rfc822Tokenizer.
  TOKENIZER_RFC822,
  // For tokenizing queries, recognizes query syntax.
  TOKENIZER_QUERY,
  // For tokenizing simple queries, which only breaks on whitespace tokens.
  TOKENIZER_QUERY_SIMPLE,
  // For tokenizing ST-like queries.
  TOKENIZER_ST_QUERY,
  // For tokenizing URLs.
  TOKENIZER_URL,
  // For not tokenizing and returning one token same as the input.
  TOKENIZER_VERBATIM,
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_LEGACY_CORE_ICING_CORE_TYPES_H_
