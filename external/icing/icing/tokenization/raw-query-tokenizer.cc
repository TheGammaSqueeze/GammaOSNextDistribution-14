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

#include "icing/tokenization/raw-query-tokenizer.h"

#include <cctype>
#include <cstddef>
#include <memory>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_join.h"
#include "icing/schema/property-util.h"
#include "icing/schema/schema-util.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/tokenization/token.h"
#include "icing/tokenization/tokenizer.h"
#include "icing/util/i18n-utils.h"
#include "icing/util/status-macros.h"

// This file provides rules that tell the tokenizer what to do when it sees a
// term.
//
// Some definitions:
//
// 1. State: We treat raw query tokenizer as a state machine, it has different
//           states when processing different terms.
// 2. TermType: type of input terms from language segmenter
// 3. Rule: a rule here is the combination of State and TermType, a rule tells
//          the raw query tokenizer what to do when it's in a certain State and
//          sees a certain TermType.
//
//    There are 2 kinds of rules here:
//    3.1 State transition rule: it tells the raw query tokenizer what new state
//                               to transition into.
//    3.2 Action rule: it tells the raw query tokenizer whether to output the
//                     current term as a token or skip.
//
// Then a rule can be described as:
// [current state] + [next term type] -> [new state] + [action]
//
// Since currently there're 9 states and 8 term types, we need 9 * 8 = 72 rules
// to cover all possible cases for both state transition and action.
//
// Besides the 72 rules, there're 4 extra rules that we handle separately:
// 1. Property name must be in ASCII.
// 2. "OR" is ignored if there's no valid token on its left.
// 3. "OR" is ignored if there's no valid token on its right.
// 4. Parentheses must appear in pairs.
namespace icing {
namespace lib {

namespace {
constexpr char kWhitespace = ' ';
constexpr char kColon = ':';
constexpr char kLeftParentheses = '(';
constexpr char kRightParentheses = ')';
constexpr char kExclusion = '-';
constexpr std::string_view kOrOperator = "OR";

enum State {
  // Ready to process any terms
  READY = 0,

  // When seeing an alphanumeric term
  PROCESSING_ALPHANUMERIC_TERM = 1,

  // When seeing an exclusion operator "-"
  PROCESSING_EXCLUSION = 2,

  // When seeing an exclusion operator + alphanumeric term
  PROCESSING_EXCLUSION_TERM = 3,

  // When seeing ASCII alphanumeric term + colon
  PROCESSING_PROPERTY_RESTRICT = 4,

  // When seeing ASCII alphanumeric term + colon + alphanumeric term
  PROCESSING_PROPERTY_TERM = 5,

  // When seeing OR operator
  PROCESSING_OR = 6,

  // When seeing left parentheses
  OPENING_PARENTHESES = 7,

  // When seeing right parentheses
  CLOSING_PARENTHESES = 8,

  PROCESSING_NON_ASCII_ALPHANUMERIC_TERM = 9,

  PROCESSING_PROPERTY_TERM_APPENDING = 10,

  // Valid state count
  STATE_COUNT = 11,

  INVALID = 12
};

enum TermType {
  // " "
  WHITESPACE = 0,

  // A term that consists of unicode alphabetic and numeric characters
  ASCII_ALPHANUMERIC_TERM = 1,

  NON_ASCII_ALPHANUMERIC_TERM = 2,

  // "("
  LEFT_PARENTHESES = 3,

  // ")"
  RIGHT_PARENTHESES = 4,

  // "-"
  EXCLUSION_OPERATOR = 5,

  // "OR"
  OR_OPERATOR = 6,

  // ":"
  COLON = 7,

  // All the other characters seen that are not the types above
  OTHER = 8,

  TYPE_COUNT = 9
};

enum ActionOrError {
  // Output the current term as token
  OUTPUT = 0,

  // Do nothing and wait for more information as it's not clear what the current
  // term is used for.
  KEEP = 1,

  // Ignore / throw away the current term
  IGNORE = 2,

  // Concatenate with next term
  CONCATENATE = 3,

  // Errors
  ERROR_UNKNOWN = 100,
  ERROR_NO_WHITESPACE_AROUND_OR = 101,
  ERROR_GROUP_AFTER_EXCLUSION = 102,
  ERROR_GROUP_AS_PROPERTY_NAME = 103,
  ERROR_GROUP_AFTER_PROPERTY_RESTRICTION = 104,
  ERROR_EXCLUSION_PROPERTY_TOGETHER = 105,
  ERROR_EXCLUSION_OR_TOGETHER = 106,
  ERROR_PROPERTY_OR_TOGETHER = 107,
  ERROR_NON_ASCII_AS_PROPERTY_NAME = 108,
};

std::string_view GetErrorMessage(ActionOrError maybe_error) {
  switch (maybe_error) {
    case ERROR_UNKNOWN:
      return "Unknown error";
    case ERROR_NO_WHITESPACE_AROUND_OR:
      return "No whitespaces before or after OR operator";
    case ERROR_GROUP_AFTER_EXCLUSION:
      return "Exclusion on groups is not supported";
    case ERROR_GROUP_AS_PROPERTY_NAME:
      return "Property name can't be a group";
    case ERROR_GROUP_AFTER_PROPERTY_RESTRICTION:
      return "Property restriction on groups is not supported";
    case ERROR_EXCLUSION_PROPERTY_TOGETHER:
      return "Exclusion and property restriction operators can't be used "
             "together";
    case ERROR_EXCLUSION_OR_TOGETHER:
      return "Exclusion and OR operators can't be used together";
    case ERROR_PROPERTY_OR_TOGETHER:
      return "Property restriction and OR operators can't be used together";
    case ERROR_NON_ASCII_AS_PROPERTY_NAME:
      return "Characters in property name must all be ASCII.";
    default:
      return "";
  }
}

// The following state transition table uses numbers to represent states and
// letters to represent actions:
//
// States:
//
// READY = 0
// PROCESSING_ASCII_ALPHANUMERIC_TERM = 1
// PROCESSING_EXCLUSION = 2
// PROCESSING_EXCLUSION_TERM = 3
// PROCESSING_PROPERTY_RESTRICT = 4
// PROCESSING_PROPERTY_TERM = 5
// PROCESSING_OR = 6
// OPENING_PARENTHESES = 7
// CLOSING_PARENTHESES = 8
// PROCESSING_NON_ASCII_ALPHANUMERIC_TERM = 9
// PROCESSING_PROPERTY_TERM_APPENDING = 10
//
// Actions:
//
// OUTPUT = a
// KEEP = b
// IGNORE = c
// CONCAT = d, concatenate the current term and the new term.
//
// =============================================================================
// Transition     ||  0 |  1 |  2 |  3 |  4 |  5 |  6 |  7 |  8 |  9 | 10 |
// =============================================================================
//     WHITESPACE || 0,c| 0,a| 0,c| 0,a| 0,a| 0,a| 0,a| 0,a| 0,a| 0,a| 0,a|
//    ASCII_ALPHA || 1,c| 1,d| 3,a| 1,a| 5,a| 1,a|ERR | 1,a| 1,a| 1,a|10,d|
// NONASCII_ALPHA || 9,c| 9,a| 3,a| 9,a| 5,a| 9,a|ERR | 9,a| 9,a| 9,a|10,d|
//     LEFT_PAREN || 7,c| 7,a|ERR | 7,a|ERR | 7,a| 7,a| 7,a| 7,a| 7,a| 7,a|
//    RIGHT_PAREN || 8,c| 8,a| 8,c| 8,a| 8,a| 8,a| 8,c| 8,a| 8,a| 8,a| 8,a|
//   EXCLUSION_OP || 2,c| 0,a| 2,c| 0,a|ERR | 0,a|ERR | 2,a| 2,a| 0,a| 0,a|
//    OR_OPERATOR || 6,c|ERR |ERR |ERR |ERR |ERR |ERR | 7,b| 6,a|ERR |ERR |
//          COLON || 0,c| 4,b|ERR |ERR | 4,b|10,d|ERR | 0,a|ERR |ERR |10,d|
//          OTHER || 0,c| 0,a| 0,c| 0,a| 0,a| 0,a| 0,a| 0,a| 0,a| 0,a| 0,a|
//
// Each cell is a rule that consists of 4 things:
// [current state] + [next term type] -> [new state] + [action]
//
// E.g. the cell at intersection of "0" and "ALPHANUMERIC_TERM" means that when
// we're at state 0 (READY) and seeing a new term with type "ALPHANUMERIC_TERM",
// we'll transition into a new state 1 (PROCESSING_ALPHANUMERIC_TERM) and take
// action c (IGNORE the current term).

// We use a 2D array to encode the state transition rules,
// The value of state_transition_rules[state1][term_type1] means "what state we
// need to transition into when the current state is state1 and the next term
// type is term_type1".
//
// NOTE: Please update the state transition table above if this is updated.
//
// TODO(tjbarron): support syntax "-property1:term1", right now we don't allow
// exclusion and property restriction applied on the same term.
// TODO(b/141007791): figure out how we'd like to support special characters
// like "+", "&", "@", "#" in indexing and query tokenizers.
constexpr State state_transition_rules[STATE_COUNT][TYPE_COUNT] = {
    /*State: Ready*/
    {READY, PROCESSING_ALPHANUMERIC_TERM,
     PROCESSING_NON_ASCII_ALPHANUMERIC_TERM, OPENING_PARENTHESES,
     CLOSING_PARENTHESES, PROCESSING_EXCLUSION, PROCESSING_OR, READY, READY},
    /*State: PROCESSING_ALPHANUMERIC_TERM*/
    {READY, PROCESSING_ALPHANUMERIC_TERM,
     PROCESSING_NON_ASCII_ALPHANUMERIC_TERM, OPENING_PARENTHESES,
     CLOSING_PARENTHESES, READY, INVALID, PROCESSING_PROPERTY_RESTRICT, READY},
    /*State: PROCESSING_EXCLUSION*/
    {READY, PROCESSING_EXCLUSION_TERM, PROCESSING_EXCLUSION_TERM, INVALID,
     CLOSING_PARENTHESES, PROCESSING_EXCLUSION, INVALID, INVALID, READY},
    /*State: PROCESSING_EXCLUSION_TERM*/
    {READY, PROCESSING_ALPHANUMERIC_TERM,
     PROCESSING_NON_ASCII_ALPHANUMERIC_TERM, OPENING_PARENTHESES,
     CLOSING_PARENTHESES, READY, INVALID, INVALID, READY},
    /*State: PROCESSING_PROPERTY_RESTRICT*/
    {READY, PROCESSING_PROPERTY_TERM, PROCESSING_PROPERTY_TERM, INVALID,
     CLOSING_PARENTHESES, INVALID, INVALID, PROCESSING_PROPERTY_RESTRICT,
     READY},
    /*State: PROCESSING_PROPERTY_TERM*/
    {READY, PROCESSING_ALPHANUMERIC_TERM,
     PROCESSING_NON_ASCII_ALPHANUMERIC_TERM, OPENING_PARENTHESES,
     CLOSING_PARENTHESES, READY, INVALID, PROCESSING_PROPERTY_TERM_APPENDING,
     READY},
    /*State: PROCESSING_OR*/
    {READY, INVALID, INVALID, OPENING_PARENTHESES, CLOSING_PARENTHESES, INVALID,
     INVALID, INVALID, READY},
    /*State: OPENING_PARENTHESES*/
    {READY, PROCESSING_ALPHANUMERIC_TERM,
     PROCESSING_NON_ASCII_ALPHANUMERIC_TERM, OPENING_PARENTHESES,
     CLOSING_PARENTHESES, PROCESSING_EXCLUSION, OPENING_PARENTHESES, READY,
     READY},
    /*State: CLOSING_PARENTHESES*/
    {READY, PROCESSING_ALPHANUMERIC_TERM,
     PROCESSING_NON_ASCII_ALPHANUMERIC_TERM, OPENING_PARENTHESES,
     CLOSING_PARENTHESES, PROCESSING_EXCLUSION, PROCESSING_OR, INVALID, READY},
    /*State: PROCESSING_NON_ASCII_ALPHANUMERIC_TERM*/
    {READY, PROCESSING_ALPHANUMERIC_TERM,
     PROCESSING_NON_ASCII_ALPHANUMERIC_TERM, OPENING_PARENTHESES,
     CLOSING_PARENTHESES, READY, INVALID, INVALID, READY},
    /*State: PROCESSING_PROPERTY_TERM_APPENDING*/
    {READY, PROCESSING_PROPERTY_TERM_APPENDING,
     PROCESSING_PROPERTY_TERM_APPENDING, OPENING_PARENTHESES,
     CLOSING_PARENTHESES, READY, INVALID, PROCESSING_PROPERTY_TERM_APPENDING,
     READY}};

// We use a 2D array to encode the action rules,
// The value of action_rules[state1][term_type1] means "what action we need to
// take when the current state is state1 and the next term type is term_type1".
//
// NOTE: Please update the state transition table above if this is updated.
constexpr ActionOrError action_rules[STATE_COUNT][TYPE_COUNT] = {
    /*State: Ready*/
    {IGNORE, IGNORE, IGNORE, IGNORE, IGNORE, IGNORE, IGNORE, IGNORE, IGNORE},
    /*State: PROCESSING_ALPHANUMERIC_TERM*/
    {OUTPUT, OUTPUT, OUTPUT, OUTPUT, OUTPUT, OUTPUT,
     ERROR_NO_WHITESPACE_AROUND_OR, KEEP, OUTPUT},
    /*State: PROCESSING_EXCLUSION*/
    {IGNORE, OUTPUT, OUTPUT, ERROR_GROUP_AFTER_EXCLUSION, IGNORE, IGNORE,
     ERROR_EXCLUSION_OR_TOGETHER, ERROR_EXCLUSION_PROPERTY_TOGETHER, IGNORE},
    /*State: PROCESSING_EXCLUSION_TERM*/
    {OUTPUT, OUTPUT, OUTPUT, OUTPUT, OUTPUT, OUTPUT,
     ERROR_NO_WHITESPACE_AROUND_OR, ERROR_EXCLUSION_PROPERTY_TOGETHER, OUTPUT},
    /*State: PROCESSING_PROPERTY_RESTRICT*/
    {OUTPUT, OUTPUT, OUTPUT, ERROR_GROUP_AFTER_PROPERTY_RESTRICTION, OUTPUT,
     ERROR_EXCLUSION_PROPERTY_TOGETHER, ERROR_PROPERTY_OR_TOGETHER, KEEP,
     OUTPUT},
    /*State: PROCESSING_PROPERTY_TERM*/
    {OUTPUT, OUTPUT, OUTPUT, OUTPUT, OUTPUT, OUTPUT,
     ERROR_NO_WHITESPACE_AROUND_OR, CONCATENATE, OUTPUT},
    /*State: PROCESSING_OR*/
    {OUTPUT, ERROR_NO_WHITESPACE_AROUND_OR, ERROR_NO_WHITESPACE_AROUND_OR,
     OUTPUT, IGNORE, ERROR_NO_WHITESPACE_AROUND_OR,
     ERROR_NO_WHITESPACE_AROUND_OR, ERROR_NO_WHITESPACE_AROUND_OR, OUTPUT},
    /*State: OPENING_PARENTHESES*/
    {OUTPUT, OUTPUT, OUTPUT, OUTPUT, OUTPUT, OUTPUT, KEEP, OUTPUT, OUTPUT},
    /*State: CLOSING_PARENTHESES*/
    {OUTPUT, OUTPUT, OUTPUT, OUTPUT, OUTPUT, OUTPUT, OUTPUT,
     ERROR_GROUP_AS_PROPERTY_NAME, OUTPUT},
    /*State: PROCESSING_NON_ASCII_ALPHANUMERIC_TERM*/
    {OUTPUT, OUTPUT, OUTPUT, OUTPUT, OUTPUT, OUTPUT,
     ERROR_NO_WHITESPACE_AROUND_OR, ERROR_NON_ASCII_AS_PROPERTY_NAME, OUTPUT},
    /*State: PROCESSING_PROPERTY_TERM_APPENDING*/
    {OUTPUT, CONCATENATE, CONCATENATE, OUTPUT, OUTPUT, OUTPUT,
     ERROR_NO_WHITESPACE_AROUND_OR, CONCATENATE, OUTPUT}};

// Determines the length of the whitespace term beginning at text[pos] and
// returns a pair with the WHITESPACE TermType and a string_view of the
// whitespace term.
std::pair<TermType, std::string_view> GetWhitespaceTerm(std::string_view text,
                                                        size_t pos) {
  size_t cur = pos;
  while (cur < text.length() && text[cur] == kWhitespace) {
    ++cur;
  }
  return std::make_pair(WHITESPACE, text.substr(pos, cur - pos));
}

TermType GetContentTermType(std::string_view text, size_t pos) {
  if (i18n_utils::IsPunctuationAt(text, pos)) {
    return OTHER;
  } else if (i18n_utils::IsAscii(text[pos])) {
    return ASCII_ALPHANUMERIC_TERM;
  }
  return NON_ASCII_ALPHANUMERIC_TERM;
}

bool IsContentTermType(TermType term_type) {
  switch (term_type) {
    case ASCII_ALPHANUMERIC_TERM:
      [[fallthrough]];
    case NON_ASCII_ALPHANUMERIC_TERM:
      [[fallthrough]];
    case OTHER:
      return true;
    case WHITESPACE:
      [[fallthrough]];
    case LEFT_PARENTHESES:
      [[fallthrough]];
    case RIGHT_PARENTHESES:
      [[fallthrough]];
    case EXCLUSION_OPERATOR:
      [[fallthrough]];
    case OR_OPERATOR:
      [[fallthrough]];
    case COLON:
      [[fallthrough]];
    case TYPE_COUNT:
      return false;
  }
}

// Determines the length of the potential content term beginning at text[pos]
// and returns a pair with the appropriate TermType and a string_view of the
// content term.
//
// NOTE: The potential content term could multiple content terms (segmentation
// is needed to determine this), a property restrict (depending on other
// neighboring tokens). It could also be multiple content terms surrounding an
// OR operator (segmentation is also needed to determine this).
std::pair<TermType, std::string_view> GetContentTerm(std::string_view text,
                                                     size_t pos) {
  size_t len = 0;
  // Checks the first char to see if it's an ASCII term
  TermType type = GetContentTermType(text, pos);
  for (size_t cur = pos; cur < text.length() && len == 0; ++cur) {
    switch (text[cur]) {
      case kLeftParentheses:
        [[fallthrough]];
      case kRightParentheses:
        [[fallthrough]];
      case kExclusion:
        [[fallthrough]];
      case kWhitespace:
        [[fallthrough]];
      case kColon:
        // If we reach any of our special characters (colon, exclusion or
        // parentheses), then we've reached the end of the content term. Set len
        // and exit the loop.
        len = cur - pos;
        break;
      default:
        break;
    }
  }
  if (len == 0) {
    // If len isn't set, then we must have reached the end of the string.
    len = text.length() - pos;
  }
  return std::make_pair(type, text.substr(pos, len));
}

// Determines the type and length of the term beginning at text[pos].
std::pair<TermType, std::string_view> GetTerm(std::string_view text,
                                              size_t pos) {
  switch (text[pos]) {
    case kLeftParentheses:
      return std::make_pair(LEFT_PARENTHESES, text.substr(pos, 1));
    case kRightParentheses:
      return std::make_pair(RIGHT_PARENTHESES, text.substr(pos, 1));
    case kExclusion:
      return std::make_pair(EXCLUSION_OPERATOR, text.substr(pos, 1));
    case kWhitespace:
      // Get length of whitespace
      return GetWhitespaceTerm(text, pos);
    case kColon:
      return std::make_pair(COLON, text.substr(pos, 1));
    case kOrOperator[0]:
      if (text.length() >= pos + kOrOperator.length() &&
          text.substr(pos, kOrOperator.length()) == kOrOperator) {
        return std::make_pair(OR_OPERATOR,
                              text.substr(pos, kOrOperator.length()));
      }
      [[fallthrough]];
    default:
      return GetContentTerm(text, pos);
  }
}

// Helper function to remove the last token if it's OR operator. This is used to
// correct the queries where there're no valid tokens after "OR", e.g. [cat OR]
// and [(cat OR)]. This helps assert extra rule 3: "OR" is ignored if there's no
// valid token on its right.
void RemoveLastTokenIfOrOperator(std::vector<Token>* tokens) {
  if (!tokens->empty() && tokens->back().type == Token::Type::QUERY_OR) {
    tokens->pop_back();
  }
}

// Helper function to output an "OR" token while asserting the extra rule 2:
// "OR" is ignored if there's no valid token on its left.
libtextclassifier3::Status OutputOrOperatorToken(std::vector<Token>* tokens) {
  if (tokens->empty()) {
    // Ignores "OR" because it's the first token.
    return libtextclassifier3::Status::OK;
  }
  Token::Type last_token_type = tokens->back().type;
  switch (last_token_type) {
    case Token::Type::REGULAR:
    case Token::Type::QUERY_RIGHT_PARENTHESES:
      tokens->emplace_back(Token::Type::QUERY_OR);
      break;
    case Token::Type::QUERY_OR:
      // Ignores "OR" because there's already an "OR", e.g. "term1 OR OR term2"
      break;
    default:
      // Ignores "OR" because there isn't a valid token on its left.
      break;
  }
  return libtextclassifier3::Status::OK;
}

// Helper function to output a token according to current term and new state.
// The new token will be added to 'tokens'.
//
// NOTE: how we output the current term is depending on the new state and not
// the current state. E.g. for these two queries: [property1: ] and
// [property1:term], "property1" is a regular term in the first query but a
// property name in the second. The meaning of "property1" is determined when
// we read the content after the colon. That's why we need to get the new state
// here.
//
// Returns:
//   OK on success
//   INVALID_ARGUMENT with error message on invalid query syntax
libtextclassifier3::Status OutputToken(State new_state,
                                       std::string_view current_term,
                                       TermType current_term_type,
                                       std::vector<Token>* tokens) {
  switch (current_term_type) {
    case ASCII_ALPHANUMERIC_TERM:
      [[fallthrough]];
    case NON_ASCII_ALPHANUMERIC_TERM:
      if (new_state == PROCESSING_PROPERTY_TERM) {
        // Asserts extra rule 1: each property name in the property path is a
        // valid term.
        for (std::string_view property :
             property_util::SplitPropertyPathExpr(current_term)) {
          if (!SchemaUtil::ValidatePropertyName(property).ok()) {
            return absl_ports::InvalidArgumentError(
                GetErrorMessage(ERROR_NON_ASCII_AS_PROPERTY_NAME));
          }
        }
        tokens->emplace_back(Token::Type::QUERY_PROPERTY, current_term);
      } else {
        tokens->emplace_back(Token::Type::REGULAR, current_term);
      }
      break;
    case LEFT_PARENTHESES:
      tokens->emplace_back(Token::Type::QUERY_LEFT_PARENTHESES);
      break;
    case RIGHT_PARENTHESES:
      // Ignores "OR" if it's followed by right parentheses.
      RemoveLastTokenIfOrOperator(tokens);
      tokens->emplace_back(Token::Type::QUERY_RIGHT_PARENTHESES);
      break;
    case EXCLUSION_OPERATOR:
      tokens->emplace_back(Token::Type::QUERY_EXCLUSION);
      break;
    case OR_OPERATOR:
      return OutputOrOperatorToken(tokens);
    default:
      break;
  }
  return libtextclassifier3::Status::OK;
}

// Helper function to apply proper rules on current state and next term type.
// 'current_state' and other output parameters will be modified to new values,
// new token will be added to 'tokens' if possible.
//
// Returns:
//   OK on success
//   INVALID_ARGUMENT with error message on invalid query syntax
libtextclassifier3::Status ProcessTerm(
    State* current_state, std::string_view* current_term,
    TermType* current_term_type, int* unclosed_parentheses_count,
    const std::string_view next_term, TermType next_term_type,
    const LanguageSegmenter* language_segmenter, std::vector<Token>* tokens) {
  // Asserts extra rule 4: parentheses must appear in pairs.
  if (next_term_type == LEFT_PARENTHESES) {
    ++(*unclosed_parentheses_count);
  } else if (next_term_type == RIGHT_PARENTHESES &&
             --(*unclosed_parentheses_count) < 0) {
    return absl_ports::InvalidArgumentError("Too many right parentheses.");
  }
  // Asks the rules what action to take and what the new state is based on
  // current state and next term.
  ActionOrError action_or_error = action_rules[*current_state][next_term_type];
  State new_state = state_transition_rules[*current_state][next_term_type];
  // Sanity check
  if (action_or_error >= ERROR_UNKNOWN || new_state == INVALID) {
    return absl_ports::InvalidArgumentError(GetErrorMessage(action_or_error));
  }
  switch (action_or_error) {
    case OUTPUT:
      if (*current_state == PROCESSING_PROPERTY_TERM_APPENDING) {
        // We appended multiple terms together in case they actually should have
        // been connected by a colon connector.
        ICING_ASSIGN_OR_RETURN(std::vector<std::string_view> content_terms,
                               language_segmenter->GetAllTerms(*current_term));
        for (std::string_view term : content_terms) {
          TermType type = GetContentTermType(term, 0);
          if (type == OTHER) {
            // Skip OTHER tokens here.
            continue;
          }
          ICING_RETURN_IF_ERROR(OutputToken(new_state, term, type, tokens));
        }
      } else {
        ICING_RETURN_IF_ERROR(
            OutputToken(new_state, *current_term, *current_term_type, tokens));
      }
      [[fallthrough]];
    case IGNORE:
      *current_term = next_term;
      *current_term_type = next_term_type;
      break;
    case KEEP:
      break;
    case CONCATENATE:
      *current_term = std::string_view(
          current_term->data(),
          next_term.data() - current_term->data() + next_term.length());
      break;
    default:
      return absl_ports::InvalidArgumentError(GetErrorMessage(ERROR_UNKNOWN));
  }
  *current_state = new_state;
  return libtextclassifier3::Status::OK;
}

// Processes all the terms from base iterator and produces a list of tokens
// based on the raw query syntax rules.
//
// Returns:
//   A list of tokens on success
//   INVALID_ARGUMENT with error message on invalid query syntax
libtextclassifier3::StatusOr<std::vector<Token>> ProcessTerms(
    const LanguageSegmenter* language_segmenter,
    std::vector<std::pair<TermType, std::string_view>> prescanned_terms) {
  std::vector<Token> tokens;
  State current_state = READY;
  std::string_view current_term;
  TermType current_term_type;
  int unclosed_parentheses_count = 0;
  for (int i = 0; i < prescanned_terms.size(); ++i) {
    const std::pair<TermType, std::string_view>& prescanned_term =
        prescanned_terms.at(i);
    if (!IsContentTermType(prescanned_term.first)) {
      // This can't be a property restrict. Just pass it in.
      ICING_RETURN_IF_ERROR(
          ProcessTerm(&current_state, &current_term, &current_term_type,
                      &unclosed_parentheses_count, prescanned_term.second,
                      prescanned_term.first, language_segmenter, &tokens));
    } else {
      // There's no colon after this term. Now, we need to segment this.
      ICING_ASSIGN_OR_RETURN(
          std::vector<std::string_view> content_terms,
          language_segmenter->GetAllTerms(prescanned_term.second));
      for (std::string_view term : content_terms) {
        TermType type = GetContentTermType(term, 0);
        if (term == kOrOperator) {
          // TODO(tjbarron) Decide whether we should revise this and other
          // handled syntax. This is used to allow queries like "term1,OR,term2"
          // to succeed. It's not clear if we should allow this or require
          // clients to ensure that OR operators are always surrounded by
          // whitespace.
          // Override the type if this is actually an OR operator.
          type = OR_OPERATOR;
        }
        ICING_RETURN_IF_ERROR(ProcessTerm(&current_state, &current_term,
                                          &current_term_type,
                                          &unclosed_parentheses_count, term,
                                          type, language_segmenter, &tokens));
      }
    }
  }
  // Adds a fake whitespace at the end to flush the last term.
  ICING_RETURN_IF_ERROR(ProcessTerm(
      &current_state, &current_term, &current_term_type,
      &unclosed_parentheses_count, std::string_view(&kWhitespace, 1),
      WHITESPACE, language_segmenter, &tokens));
  if (unclosed_parentheses_count > 0) {
    return absl_ports::InvalidArgumentError("Unclosed left parentheses.");
  }
  // Ignores "OR" if it's at the end.
  RemoveLastTokenIfOrOperator(&tokens);
  return tokens;
}

// For raw query, it's easier to produce all the tokens together one time and
// pass them to the iterator because the meaning of each term may relate to the
// terms before or after it.
class RawQueryTokenIterator : public Tokenizer::Iterator {
 public:
  explicit RawQueryTokenIterator(std::vector<Token>&& tokens)
      : tokens_(std::move(tokens)) {}

  bool Advance() override { return ++current_ < tokens_.size(); }

  std::vector<Token> GetTokens() const override {
    std::vector<Token> result;

    if (current_ >= 0 && current_ < tokens_.size()) {
      result.push_back(tokens_.at(current_));
    }

    return result;
  }

 private:
  const std::vector<Token> tokens_;
  int current_ = -1;
};

}  // namespace

libtextclassifier3::StatusOr<std::unique_ptr<Tokenizer::Iterator>>
RawQueryTokenizer::Tokenize(std::string_view text) const {
  ICING_ASSIGN_OR_RETURN(std::vector<Token> tokens, TokenizeAll(text));
  return std::make_unique<RawQueryTokenIterator>(std::move(tokens));
}

libtextclassifier3::StatusOr<std::vector<Token>> RawQueryTokenizer::TokenizeAll(
    std::string_view text) const {
  // 1. Prescan all terms in the text, to determine which ones are potentially
  // content and which ones are not.
  std::vector<std::pair<TermType, std::string_view>> prescanned_terms;
  for (size_t pos = 0; pos < text.length();) {
    std::pair<TermType, std::string_view> term_pair = GetTerm(text, pos);
    pos += term_pair.second.length();
    prescanned_terms.push_back(term_pair);
  }
  // 2. Process the prescanned terms, segmenting content terms as needed.
  return ProcessTerms(&language_segmenter_, std::move(prescanned_terms));
}

}  // namespace lib
}  // namespace icing
