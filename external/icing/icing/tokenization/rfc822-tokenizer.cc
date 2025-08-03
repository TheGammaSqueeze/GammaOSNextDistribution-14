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

#include "icing/tokenization/rfc822-tokenizer.h"

#include <algorithm>
#include <deque>
#include <queue>
#include <string_view>
#include <utility>
#include <vector>

#include "icing/tokenization/token.h"
#include "icing/tokenization/tokenizer.h"
#include "icing/util/character-iterator.h"
#include "icing/util/i18n-utils.h"
#include "icing/util/status-macros.h"
#include "unicode/umachine.h"

namespace icing {
namespace lib {

namespace {
bool IsDelimiter(UChar32 c) { return c == ',' || c == ';' || c == '\n'; }
}  // namespace

class Rfc822TokenIterator : public Tokenizer::Iterator {
 public:
  // Cursor is the index into the string_view, text_end_ is the length.
  explicit Rfc822TokenIterator(std::string_view text)
      : text_(std::move(text)),
        iterator_(text, 0, 0, 0),
        text_end_(text.length()),
        token_index_(-1) {}

  // Advance will move token_index_ past the end of tokens_
  bool Advance() override {
    // Stop the token index on a RFC822 token, or one past the end, where the
    // next RFC822 token will be if more are generated.
    do {
      token_index_++;
    } while (token_index_ < tokens_.size() &&
             tokens_[token_index_].type != Token::Type::RFC822_TOKEN);

    // There is still something left, possible if we rewinded and call Advance
    if (token_index_ < tokens_.size()) {
      return true;
    }

    // Done with the entire string_view.
    if (iterator_.utf8_index() >= text_end_) {
      return false;
    }

    // Parsing a new email, update the current email marker.
    AdvancePastWhitespace();

    // This may return false, as in the case of "<alex>,,", where after
    // processing <alex>, there are no more tokens.
    return GetNextRfc822Token();
  }

  // Returns the current token group, an RFC822_TOKEN along with all it's
  // subtokens. For example, "tim@google.com" will return all tokens generated
  // from that text.
  //
  // Returns:
  //   A vector of Tokens on success
  //   An empty vector if the token list is empty
  //   An empty vector if the index is past the end of the token list
  std::vector<Token> GetTokens() const override {
    std::vector<Token> result;
    if (token_index_ < tokens_.size() && token_index_ >= 0) {
      int index = token_index_;
      do {
        result.push_back(tokens_[index]);
      } while (++index < tokens_.size() &&
               tokens_[index].type != Token::Type::RFC822_TOKEN);
    }
    return result;
  }

  bool ResetToTokenStartingAfter(int32_t utf32_offset) override {
    CharacterIterator tracker(text_);
    for (int new_index = 0; new_index < tokens_.size(); ++new_index) {
      const Token& t = tokens_[new_index];
      if (t.type != Token::Type::RFC822_TOKEN) {
        continue;
      }

      tracker.AdvanceToUtf8(t.text.begin() - text_.begin());
      if (tracker.utf32_index() > utf32_offset) {
        token_index_ = new_index;
        return true;
      }
    }

    return false;
  }

  // This will attempt to reset the token_index to point to the last token
  // ending before an offset. If it fails, due to there not being any tokens
  // before the offset, the token index will become -1.
  bool ResetToTokenEndingBefore(int32_t utf32_offset) override {
    // First, advance until we pass offset or Advance is false
    if (tokens_.empty()) {
      if (!Advance()) {
        // No tokens available, and Advancing doesn't get more, so return false.
        return false;
      }
    }

    CharacterIterator tracker(text_);

    // Keep advancing until we parse all the emails, or run past the offset.
    // Advance will always make token_index_ point to an RFC822_TOKEN, so we can
    // look at that tokens text end to determine if it ends before the offset.
    // This first loop will guarantee that we end up either past the offset or
    // at the end.
    do {
      tracker.AdvanceToUtf8(tokens_[token_index_].text.end() - text_.begin());

      // When we Advance and have to convert names to email addresses, it's
      // possible that multiple RFC822 tokens are added. We need to advance
      // through these one at a time, we cannot skip to the top of the line.
    } while (tracker.utf32_index() <= utf32_offset && Advance());

    // We are either past the offset or at the end. Either way, we now work
    // backwards and reset to the first (highest index) RFC822_TOKEN we find.
    while (--token_index_ >= 0) {
      if (tokens_[token_index_].type != Token::Type::RFC822_TOKEN) {
        continue;
      }

      tracker.MoveToUtf8(tokens_[token_index_].text.end() - text_.begin());
      if (tracker.utf32_index() <= utf32_offset) {
        return true;
      }
    }
    return false;
  }

  // Returns a character iterator to the start of the token.
  libtextclassifier3::StatusOr<CharacterIterator> CalculateTokenStart()
      override {
    CharacterIterator token_start = iterator_;
    token_start.MoveToUtf8(GetTokens().at(0).text.begin() - text_.begin());
    return token_start;
  }

  // Returns a character iterator to right after the end of the token.
  libtextclassifier3::StatusOr<CharacterIterator> CalculateTokenEndExclusive()
      override {
    CharacterIterator token_end = iterator_;
    token_end.MoveToUtf8(GetTokens().at(0).text.end() - text_.begin());
    return token_end;
  }

  // Reset to start moves to the state we're in after the first Advance().
  bool ResetToStart() override {
    token_index_ = -1;
    return Advance();
  }

 private:
  // Advance until the next email delimiter, generating as many tokens as
  // necessary.
  bool GetNextRfc822Token() {
    if (iterator_.utf8_index() >= text_end_) {
      return false;
    }

    int token_start = iterator_.utf8_index();
    bool address_found = false;
    bool name_found = false;
    std::vector<Token> next_tokens;
    Token rfc822(Token::Type::RFC822_TOKEN);

    // We start at unquoted and run until a ",;\n<( .
    while (iterator_.utf8_index() < text_end_) {
      UChar32 c = iterator_.GetCurrentChar();
      if (IsDelimiter(c)) {
        // End of the token, advance cursor past all delimiters then quit.
        rfc822.text =
            text_.substr(token_start, iterator_.utf8_index() - token_start);

        UChar32 delimiter;
        do {
          AdvanceCursor();
          delimiter = iterator_.GetCurrentChar();
          // If we get current char on the end, it is not a delimiter so this
          // loop will end
        } while (IsDelimiter(delimiter));

        break;
      }

      std::vector<Token> consume_result;
      if (c == '"') {
        consume_result = ConsumeQuotedSection();
        name_found |= !consume_result.empty();
      } else if (c == '(') {
        consume_result = ConsumeParenthesizedSection();
      } else if (c == '<') {
        // Only set address_found to true if ConsumeAdress returns true.
        // Otherwise, keep address_found as is to prevent setting address_found
        // back to false if it is true.
        consume_result = ConsumeAddress();
        address_found |= !consume_result.empty();
      } else {
        consume_result = ConsumeUnquotedSection();
        name_found |= !consume_result.empty();
      }
      next_tokens.insert(next_tokens.end(), consume_result.begin(),
                         consume_result.end());
    }
    if (iterator_.utf8_index() >= text_end_) {
      rfc822.text = text_.substr(token_start, text_end_ - token_start);
    }

    // If an address is found, use the tokens we have.
    // If an address isn't found, and a name isn't found, also use the tokens
    // we have.
    // If an address isn't found but a name is, convert name Tokens to email
    // Tokens.
    if (!address_found && name_found) {
      // We don't add the rfc822 token, as it will be handled by
      // ConvertNameToEmail.
      std::vector<Token> converted_tokens = ConvertNameToEmail(next_tokens);
      tokens_.insert(tokens_.end(), converted_tokens.begin(),
                     converted_tokens.end());
    } else {
      if (next_tokens.empty()) {
        // Tokens may not be generated in the case of ",,,,,,"
        return false;
      } else {
        // If tokens were generated, push back the RFC822 token for them
        tokens_.push_back(rfc822);
        tokens_.insert(tokens_.end(), next_tokens.begin(), next_tokens.end());
      }
    }

    return true;
  }

  // We allow for the "First Last <email>" format, but if there is no email in
  // brackets, we won't allow for unquoted spaces. For example, the input
  // "alex@google.com tim@google.com" has an unquoted space, so we will split
  // it into two emails. We don't need to find more tokens, we just need to
  // find @ signs and spaces and convert name tokens to parts of the email.
  std::vector<Token> ConvertNameToEmail(std::vector<Token>& name_tokens) {
    if (name_tokens.empty()) {
      return name_tokens;
    }

    // There will only be names and comments, and they will be in order.
    std::vector<Token> converted_tokens;

    // Start at the beginning of the current email.
    CharacterIterator scanner(text_);

    scanner.MoveToUtf8(name_tokens[0].text.begin() - text_.begin());
    int token_processed_index = 0;

    bool in_quote = false;
    // Setting at_sign_index to before the beginning, it'll only be set to
    // something else if we find an @ sign
    const char* at_sign_index = nullptr;

    // Run to the end
    while (scanner.utf8_index() < iterator_.utf8_index()) {
      const char* end_of_token = nullptr;
      UChar32 c = scanner.GetCurrentChar();
      if (c == '\\') {
        // Skip the slash, as well as the following token.
        scanner.AdvanceToUtf32(scanner.utf32_index() + 1);
        scanner.AdvanceToUtf32(scanner.utf32_index() + 1);
        continue;
      }
      if (c == '"') {
        in_quote = !in_quote;
      }
      if (c == '@') {
        at_sign_index = text_.begin() + scanner.utf8_index();
      }

      // If the next character is the end OR we hit an unquoted space.
      if (scanner.utf8_index() + i18n_utils::GetUtf8Length(c) ==
              iterator_.utf8_index() ||
          (!in_quote && c == ' ')) {
        if (!in_quote && c == ' ') {
          end_of_token = text_.begin() + scanner.utf8_index();
        } else {
          end_of_token = text_.begin() + iterator_.utf8_index();
        }
        std::deque<Token> more_tokens = ConvertOneNameToEmail(
            name_tokens, at_sign_index, end_of_token, token_processed_index);
        converted_tokens.insert(converted_tokens.end(), more_tokens.begin(),
                                more_tokens.end());
        // Reset the at_sign_index
        at_sign_index = nullptr;
      }
      scanner.AdvanceToUtf32(scanner.utf32_index() + 1);
    }

    // It's possible we left something out.
    if (token_processed_index < name_tokens.size()) {
      std::deque<Token> more_tokens =
          ConvertOneNameToEmail(name_tokens, at_sign_index,
                                name_tokens[name_tokens.size() - 1].text.end(),
                                token_processed_index);
      converted_tokens.insert(converted_tokens.end(), more_tokens.begin(),
                              more_tokens.end());
    }

    return converted_tokens;
  }

  // Once a name is determined to be an address, convert its tokens to address
  // tokens.
  std::deque<Token> ConvertOneNameToEmail(const std::vector<Token>& name_tokens,
                                          const char* at_sign_index,
                                          const char* end_of_token,
                                          int& token_processed_index) {
    const char* address_start = nullptr;
    const char* local_address_end = nullptr;
    const char* host_address_start = nullptr;
    const char* address_end = nullptr;
    const char* token_start = nullptr;
    const char* token_end = nullptr;
    std::deque<Token> converted_tokens;

    // Transform tokens up to end of token pointer.

    for (; token_processed_index < name_tokens.size();
         ++token_processed_index) {
      const Token& token = name_tokens[token_processed_index];

      if (token.text.end() > end_of_token) {
        break;
      }
      std::string_view text = token.text;
      // We need to do this both for comment and name tokens. Comment tokens
      // will get a corresponding RFC822 token, but not an address or local
      // address.
      if (token_start == nullptr) {
        token_start = text.begin();
      }
      token_end = text.end();

      if (token.type == Token::Type::RFC822_COMMENT) {
        // Comment tokens will stay as they are.
        converted_tokens.push_back(token);
      } else if (token.type == Token::Type::RFC822_NAME) {
        // Names need to be converted to address tokens. We keep the order of
        // which the name tokens appeared. Name tokens that appear before an
        // @ sign in the name will become RFC822_ADDRESS_COMPONENT_LOCAL, and
        // those after will become RFC822_ADDRESS_COMPONENT_HOST. We aren't
        // able to determine RFC822_ADDRESS, RFC822_LOCAL_ADDRESS, and
        // RFC_HOST_ADDRESS before checking the name tokens, so they will be
        // added after the component tokens.
        if (address_start == nullptr) {
          address_start = text.begin();
        }
        address_end = text.end();
        if (text.begin() > at_sign_index) {
          if (host_address_start == nullptr) {
            host_address_start = text.begin();
          }
          // Once this is hit, we switch to COMPONENT_HOST and mark end of the
          // local address
          converted_tokens.push_back(
              Token(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, token.text));
        } else {
          local_address_end = text.end();
          converted_tokens.push_back(
              Token(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, token.text));
        }
      }
    }

    if (address_start != nullptr) {
      converted_tokens.push_back(
          Token(Token::Type::RFC822_ADDRESS,
                std::string_view(address_start, address_end - address_start)));
      if (local_address_end != nullptr) {
        converted_tokens.push_back(
            Token(Token::Type::RFC822_LOCAL_ADDRESS,
                  std::string_view(address_start,
                                   local_address_end - address_start)));
      }
    }

    if (host_address_start != nullptr && host_address_start < address_end) {
      converted_tokens.push_back(
          Token(Token::Type::RFC822_HOST_ADDRESS,
                text_.substr(host_address_start - text_.begin(),
                             address_end - host_address_start)));
    }

    if (token_start != nullptr) {
      converted_tokens.push_front(
          Token(Token::Type::RFC822_TOKEN,
                std::string_view(token_start, token_end - token_start)));
    }

    return converted_tokens;
  }

  // Returns name tokens in an unquoted section. This is useful in case we do
  // not find an address and have to use the name. An unquoted section may look
  // like "Alex Sav", or "alex@google.com". In the absense of a bracketed email
  // address, the unquoted section will be used as the email address along with
  // the quoted section.
  std::vector<Token> ConsumeUnquotedSection() {
    UChar32 c;

    int token_start = -1;
    std::vector<Token> next_tokens;

    // Advance to another state or a character marking the end of token, one
    // of \n,; .
    while (iterator_.utf8_index() < text_end_) {
      c = iterator_.GetCurrentChar();

      if (i18n_utils::IsAlphaNumeric(c)) {
        if (token_start == -1) {
          // Start recording
          token_start = iterator_.utf8_index();
        }
        AdvanceCursor();

      } else {
        if (token_start != -1) {
          // The character is non alphabetic, save a token.
          next_tokens.push_back(Token(
              Token::Type::RFC822_NAME,
              text_.substr(token_start, iterator_.utf8_index() - token_start)));
          token_start = -1;
        }

        if (c == '"' || c == '<' || c == '(' || IsDelimiter(c)) {
          // Stay on the token.
          break;
        }

        AdvanceCursor();
      }
    }
    if (token_start != -1) {
      next_tokens.push_back(Token(
          Token::Type::RFC822_NAME,
          text_.substr(token_start, iterator_.utf8_index() - token_start)));
    }
    return next_tokens;
  }

  // Names that are within quotes should have all characters blindly
  // unescaped. When a name is made into an address, it isn't re-escaped.

  // Returns name tokens found in a quoted section. This is useful in case we do
  // not find an address and have to use the name. The quoted section may
  // contain whitespaces.
  std::vector<Token> ConsumeQuotedSection() {
    // Get past the first quote.
    AdvanceCursor();

    bool end_quote_found = false;
    std::vector<Token> next_tokens;
    UChar32 c;

    int token_start = -1;

    while (!end_quote_found && (iterator_.utf8_index() < text_end_)) {
      c = iterator_.GetCurrentChar();

      if (i18n_utils::IsAlphaNumeric(c)) {
        if (token_start == -1) {
          // Start tracking the token.
          token_start = iterator_.utf8_index();
        }
        AdvanceCursor();

      } else {
        // Non- alphabetic
        if (c == '\\') {
          // A backslash, let's look at the next character.
          CharacterIterator temp = iterator_;
          temp.AdvanceToUtf32(iterator_.utf32_index() + 1);
          UChar32 n = temp.GetCurrentChar();
          if (i18n_utils::IsAlphaNumeric(n)) {
            // The next character is alphabetic, skip the slash and don't end
            // the last token. For quoted sections, the only things that are
            // escaped are double quotes and slashes. For example, in "a\lex",
            // an l appears after the slash. We want to treat this as if it
            // was just "alex". So we tokenize it as <RFC822_NAME, "a\lex">.
            AdvanceCursor();
          } else {
            // Not alphabetic, so save the last token if necessary.
            if (token_start != -1) {
              next_tokens.push_back(
                  Token(Token::Type::RFC822_NAME,
                        text_.substr(token_start,
                                     iterator_.utf8_index() - token_start)));
              token_start = -1;
            }

            // Skip the backslash.
            AdvanceCursor();

            if (n == '"' || n == '\\' || n == '@') {
              // Skip these too if they're next.
              AdvanceCursor();
            }
          }
        } else {
          // Not a backslash.

          if (token_start != -1) {
            next_tokens.push_back(
                Token(Token::Type::RFC822_NAME,
                      text_.substr(token_start,
                                   iterator_.utf8_index() - token_start)));
            token_start = -1;
          }

          if (c == '"') {
            end_quote_found = true;
          }
          // Advance one more time to get past the non-alphabetic character.
          AdvanceCursor();
        }
      }
    }
    if (token_start != -1) {
      next_tokens.push_back(Token(
          Token::Type::RFC822_NAME,
          text_.substr(token_start, iterator_.utf8_index() - token_start)));
    }
    return next_tokens;
  }

  // '(', ')', '\\' chars should be escaped. All other escaped chars should be
  // unescaped.
  std::vector<Token> ConsumeParenthesizedSection() {
    // Skip the initial (
    AdvanceCursor();

    int paren_layer = 1;
    UChar32 c;
    std::vector<Token> next_tokens;

    int token_start = -1;

    while (paren_layer > 0 && (iterator_.utf8_index() < text_end_)) {
      c = iterator_.GetCurrentChar();

      if (i18n_utils::IsAlphaNumeric(c)) {
        if (token_start == -1) {
          // Start tracking a token.
          token_start = iterator_.utf8_index();
        }
        AdvanceCursor();
      } else {
        // Non alphabetic.
        if (c == '\\') {
          // A backslash, let's look at the next character.
          UChar32 n = i18n_utils::GetUChar32At(text_.begin(), text_.length(),
                                               iterator_.utf8_index() + 1);
          if (i18n_utils::IsAlphaNumeric(n)) {
            // Alphabetic, skip the slash and don't end the last token.
            AdvanceCursor();
          } else {
            // Not alphabetic, save the last token if necessary.
            if (token_start != -1) {
              next_tokens.push_back(
                  Token(Token::Type::RFC822_COMMENT,
                        text_.substr(token_start,
                                     iterator_.utf8_index() - token_start)));
              token_start = -1;
            }

            // Skip the backslash.
            AdvanceCursor();

            if (n == ')' || n == '(' || n == '\\') {
              // Skip these too if they're next.
              AdvanceCursor();
            }
          }
        } else {
          // Not a backslash.
          if (token_start != -1) {
            next_tokens.push_back(
                Token(Token::Type::RFC822_COMMENT,
                      text_.substr(token_start,
                                   iterator_.utf8_index() - token_start)));
            token_start = -1;
          }

          if (c == '(') {
            paren_layer++;
          } else if (c == ')') {
            paren_layer--;
          }
          AdvanceCursor();
        }
      }
    }

    if (token_start != -1) {
      // Ran past the end of text_ without getting the last token.

      // substr returns "a view of the substring [pos, pos + // rcount), where
      // rcount is the smaller of count and size() - pos" therefore the count
      // argument can be any value >= this->cursor - token_start. Therefore,
      // ignoring the mutation warning.
      next_tokens.push_back(Token(
          Token::Type::RFC822_COMMENT,
          text_.substr(token_start, iterator_.utf8_index() - token_start)));
    }
    return next_tokens;
  }

  // Returns tokens found in the address.
  std::vector<Token> ConsumeAddress() {
    // Skip the first <.
    AdvanceCursor();

    // Save the start position.
    CharacterIterator address_start_iterator = iterator_;
    std::vector<Token> next_tokens;

    // Place the at sign on the '<', so that if no at_sign is found, the default
    // is that the entire address is the host part.
    int at_sign = -1;
    int address_end = -1;

    UChar32 c = iterator_.GetCurrentChar();
    // Quick scan for @ and > signs.
    while (c != '>' && iterator_.utf8_index() < text_end_) {
      AdvanceCursor();
      c = iterator_.GetCurrentChar();
      if (c == '@') {
        at_sign = iterator_.utf8_index();
      }
    }

    if (iterator_.utf8_index() <= address_start_iterator.utf8_index()) {
      // There is nothing between the brackets, either we have "<" or "<>".
      return next_tokens;
    }

    // Either we find a > or run to the end, either way this is the end of the
    // address. The ending bracket will be handled by ConsumeUnquoted.
    address_end = iterator_.utf8_index();

    // Reset to the start.
    iterator_ = address_start_iterator;

    int address_start = address_start_iterator.utf8_index();

    Token::Type type = Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL;

    // Create a local address token.
    if (at_sign != -1) {
      next_tokens.push_back(
          Token(Token::Type::RFC822_LOCAL_ADDRESS,
                text_.substr(address_start, at_sign - address_start)));
    } else {
      // All the tokens in the address are host components.
      type = Token::Type::RFC822_ADDRESS_COMPONENT_HOST;
      // If no @ is found, treat the entire address as the host address.
      at_sign = address_start - 1;
    }

    // The only case where we don't have a host address part is something like
    // <localaddress@>. If there is no @, the at_sign is the default -1, and the
    // host address is [0, address_end).
    int host_address_start = at_sign + 1;
    if (host_address_start < address_end) {
      next_tokens.push_back(Token(
          Token::Type::RFC822_HOST_ADDRESS,
          text_.substr(host_address_start, address_end - host_address_start)));
    }

    next_tokens.push_back(
        Token(Token::Type::RFC822_ADDRESS,
              text_.substr(address_start, address_end - address_start)));

    int token_start = -1;

    while (iterator_.utf8_index() < address_end) {
      c = iterator_.GetCurrentChar();

      if (i18n_utils::IsAlphaNumeric(c)) {
        if (token_start == -1) {
          token_start = iterator_.utf8_index();
        }
      } else {
        // non alphabetic
        if (c == '\\') {
          // A backslash, let's look at the next character.
          CharacterIterator temp = iterator_;
          temp.AdvanceToUtf32(iterator_.utf32_index() + 1);
          UChar32 n = temp.GetCurrentChar();
          if (!i18n_utils::IsAlphaNumeric(n)) {
            // Not alphabetic, end the last token if necessary.
            if (token_start != -1) {
              next_tokens.push_back(Token(
                  type, text_.substr(token_start,
                                     iterator_.utf8_index() - token_start)));
              token_start = -1;
            }
          }
        } else {
          // Not backslash.
          if (token_start != -1) {
            next_tokens.push_back(Token(
                type, text_.substr(token_start,
                                   iterator_.utf8_index() - token_start)));
            token_start = -1;
          }
          // Switch to host component tokens.
          if (iterator_.utf8_index() == at_sign) {
            type = Token::Type::RFC822_ADDRESS_COMPONENT_HOST;
          }
        }
      }
      AdvanceCursor();
    }
    if (token_start != -1) {
      next_tokens.push_back(Token(
          type,
          text_.substr(token_start, iterator_.utf8_index() - token_start)));
    }
    // Unquoted will handle the closing bracket > if these is one.
    return next_tokens;
  }

  void AdvanceCursor() {
    iterator_.AdvanceToUtf32(iterator_.utf32_index() + 1);
  }

  void AdvancePastWhitespace() {
    while (i18n_utils::IsWhitespaceAt(text_, iterator_.utf8_index())) {
      AdvanceCursor();
    }
  }

  std::string_view text_;
  CharacterIterator iterator_;
  int text_end_;

  // A temporary store of Tokens. As we advance through the provided string,
  // we parse entire addresses at a time rather than one token at a time.
  // However, since we call the tokenizer with Advance() alternating with
  // GetToken(), we need to store tokens for subsequent GetToken calls if
  // Advance generates multiple tokens (it usually does). A vector is used as
  // we need to iterate back and forth through tokens during snippeting. It is
  // cleared by the destructor.
  std::vector<Token> tokens_;
  // Index to keep track of where we are in tokens_. This will always be set to
  // point to an RFC822_TOKEN, or one past the end of the tokens_ vector. The
  // only exception is before the first Advance call.
  int token_index_;
};

libtextclassifier3::StatusOr<std::unique_ptr<Tokenizer::Iterator>>
Rfc822Tokenizer::Tokenize(std::string_view text) const {
  return std::make_unique<Rfc822TokenIterator>(text);
}

libtextclassifier3::StatusOr<std::vector<Token>> Rfc822Tokenizer::TokenizeAll(
    std::string_view text) const {
  ICING_ASSIGN_OR_RETURN(std::unique_ptr<Tokenizer::Iterator> iterator,
                         Tokenize(text));
  std::vector<Token> tokens;
  while (iterator->Advance()) {
    std::vector<Token> batch_tokens = iterator->GetTokens();
    tokens.insert(tokens.end(), batch_tokens.begin(), batch_tokens.end());
  }
  return tokens;
}

}  // namespace lib
}  // namespace icing
