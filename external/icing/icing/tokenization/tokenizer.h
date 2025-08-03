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

#ifndef ICING_TOKENIZATION_TOKENIZER_H_
#define ICING_TOKENIZATION_TOKENIZER_H_

#include <cstdint>
#include <memory>
#include <string_view>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/tokenization/token.h"
#include "icing/util/character-iterator.h"

namespace icing {
namespace lib {

// A virtual class that all other tokenizers should inherit. It provides
// interfaces that allow callers to tokenize text. The return value could be an
// iterator or a list of tokens. Example usage:
//
// std::unique_ptr<Tokenizer> tokenizer = GetTokenizer();
// ICING_ASSIGN_OR_RETURN(std::unique_ptr<Tokenizer::Iterator> iter,
//                  tokenizer->Tokenize(text));
// ICING_ASSIGN_OR_RETURN(std::vector<Token> tokens,
// tokenizer->TokenizeAll(text));
class Tokenizer {
 public:
  virtual ~Tokenizer() = default;

  // An iterator helping to get tokens.
  // Example usage:
  //
  // while (iterator.Advance()) {
  //   const Token& token = iterator.GetToken();
  //   // Do something
  // }
  class Iterator {
   public:
    virtual ~Iterator() = default;

    // Advances to the next token. Returns false if it has reached the end.
    virtual bool Advance() = 0;

    // Returns the current token, maybe with compound tokens as well. It can be
    // called only when Advance() returns true, otherwise an empty Token vector
    // may be returned.
    virtual std::vector<Token> GetTokens() const = 0;

    virtual libtextclassifier3::StatusOr<CharacterIterator>
    CalculateTokenStart() {
      return absl_ports::UnimplementedError(
          "CalculateTokenStart is not implemented!");
    }

    virtual libtextclassifier3::StatusOr<CharacterIterator>
    CalculateTokenEndExclusive() {
      return absl_ports::UnimplementedError(
          "CalculateTokenEndExclusive is not implemented!");
    }

    // Sets the tokenizer to point at the first token that *starts* *after*
    // offset. Returns false if there are no valid tokens starting after
    // offset.
    // Ex.
    // auto iterator = tokenizer.Tokenize("foo bar baz").ValueOrDie();
    // iterator.ResetToTokenStartingAfter(4);
    // // The first full token starting after position 4 (the 'b' in "bar") is
    // // "baz".
    // PrintToken(iterator.GetToken());  // prints "baz"
    virtual bool ResetToTokenStartingAfter(int32_t utf32_offset) {
      return false;
    }

    // Sets the tokenizer to point at the first token that *ends* *before*
    // offset. Returns false if there are no valid tokens ending
    // before offset.
    // Ex.
    // auto iterator = tokenizer.Tokenize("foo bar baz").ValueOrDie();
    // iterator.ResetToTokenEndingBefore(4);
    // // The first full token ending before position 4 (the 'b' in "bar") is
    // // "foo".
    // PrintToken(iterator.GetToken());  // prints "foo"
    virtual bool ResetToTokenEndingBefore(int32_t utf32_offset) {
      return false;
    }

    virtual bool ResetToStart() { return false; }
  };

  // Tokenizes the input text. The input text should outlive the returned
  // iterator.
  //
  // Returns:
  //   A token iterator on success
  //   INVALID_ARGUMENT with error message if input text has a wrong syntax
  //                    according to implementations of different tokenizer
  //                    types.
  //   INTERNAL_ERROR if any other errors occur
  virtual libtextclassifier3::StatusOr<std::unique_ptr<Iterator>> Tokenize(
      std::string_view text) const = 0;

  // Tokenizes and returns all tokens in the input text. The input text should
  // outlive the returned vector.
  //
  // Returns:
  //   A list of tokens on success
  //   INVALID_ARGUMENT with error message if input text has a wrong syntax
  //                    according to implementations of different tokenizer
  //                    types.
  //   INTERNAL_ERROR if any other errors occur
  virtual libtextclassifier3::StatusOr<std::vector<Token>> TokenizeAll(
      std::string_view text) const = 0;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_TOKENIZATION_TOKENIZER_H_
