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

#ifndef ICING_TOKENIZATION_TOKEN_H_
#define ICING_TOKENIZATION_TOKEN_H_

#include <string_view>

namespace icing {
namespace lib {

struct Token {
  enum class Type {
    // Common types
    REGULAR,  // A token without special meanings, the value of it will be
              // indexed or searched directly

    VERBATIM,  // A token that should be indexed and searched without any
               // modifications to the raw text

    // An RFC822 section with the content in RFC822_TOKEN tokenizes as follows:
    RFC822_NAME,                     // "User", "Johnsson"
    RFC822_COMMENT,                  // "A", "comment", "here"
    RFC822_LOCAL_ADDRESS,            // "user.name"
    RFC822_HOST_ADDRESS,             // "domain.name.com"
    RFC822_ADDRESS,                  // "user.name@domain.name.com"
    RFC822_ADDRESS_COMPONENT_LOCAL,  // "user", "name",
    RFC822_ADDRESS_COMPONENT_HOST,   // "domain", "name", "com"
    RFC822_TOKEN,  // "User Johnsson (A comment) <user.name@domain.name.com>"

    // Types only used in raw query
    QUERY_OR,         // Indicates OR logic between its left and right tokens
    QUERY_EXCLUSION,  // Indicates exclusion operation on next token
    QUERY_PROPERTY,   // Indicates property restrict on next token
    QUERY_LEFT_PARENTHESES,   // Left parentheses
    QUERY_RIGHT_PARENTHESES,  // Right parentheses

    // Types used in URL tokenization
    URL_SCHEME,  // "http", "https", "ftp", "content"
    URL_USERNAME,
    URL_PASSWORD,
    URL_HOST_COMMON_PART,  // Hosts are split into two types, common and
                           // significant. Common are e.g: www, ww2, .com, etc.
    URL_HOST_SIGNIFICANT_PART,
    URL_PORT,
    URL_PATH_PART,  // Tokenized path, e.g. /abc-d/e.fg-> [abc-d], [e.fg]
    URL_QUERY,      // After ?, before #, e.g. "param1=value-1&param2=value-2
    URL_REF,        // Anything after #. Could be anything
    URL_SUFFIX,
    URL_SUFFIX_INNERMOST,

    // Indicates errors
    INVALID,
  };

  // The input text should outlive the Token instance.
  explicit Token(Type type_in, std::string_view text_in = "")
      : type(type_in), text(text_in) {}

  // The type of token
  Type type;

  // The content of token
  std::string_view text;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_TOKENIZATION_TOKEN_H_
