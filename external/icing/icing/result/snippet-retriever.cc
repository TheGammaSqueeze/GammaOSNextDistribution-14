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

#include "icing/result/snippet-retriever.h"

#include <algorithm>
#include <iterator>
#include <memory>
#include <string>
#include <string_view>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/query/query-terms.h"
#include "icing/schema/property-util.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"
#include "icing/store/document-filter-data.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/tokenization/token.h"
#include "icing/tokenization/tokenizer-factory.h"
#include "icing/tokenization/tokenizer.h"
#include "icing/transform/normalizer.h"
#include "icing/util/character-iterator.h"
#include "icing/util/i18n-utils.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

inline std::string AddIndexToPath(int values_size, int index,
                                  const std::string& property_path) {
  if (values_size == 1) {
    return property_path;
  }
  return absl_ports::StrCat(
      property_path, property_util::ConvertToPropertyExprIndexStr(index));
}

// Returns a string of the normalized text of the input Token. Normalization
// is applied based on the Token's type.
std::string NormalizeToken(const Normalizer& normalizer, const Token& token) {
  switch (token.type) {
    case Token::Type::RFC822_NAME:
      [[fallthrough]];
    case Token::Type::RFC822_COMMENT:
      [[fallthrough]];
    case Token::Type::RFC822_LOCAL_ADDRESS:
      [[fallthrough]];
    case Token::Type::RFC822_HOST_ADDRESS:
      [[fallthrough]];
    case Token::Type::RFC822_ADDRESS:
      [[fallthrough]];
    case Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL:
      [[fallthrough]];
    case Token::Type::RFC822_ADDRESS_COMPONENT_HOST:
      [[fallthrough]];
    case Token::Type::RFC822_TOKEN:
      [[fallthrough]];
    case Token::Type::URL_SCHEME:
      [[fallthrough]];
    case Token::Type::URL_USERNAME:
      [[fallthrough]];
    case Token::Type::URL_PASSWORD:
      [[fallthrough]];
    case Token::Type::URL_HOST_COMMON_PART:
      [[fallthrough]];
    case Token::Type::URL_HOST_SIGNIFICANT_PART:
      [[fallthrough]];
    case Token::Type::URL_PORT:
      [[fallthrough]];
    case Token::Type::URL_PATH_PART:
      [[fallthrough]];
    case Token::Type::URL_QUERY:
      [[fallthrough]];
    case Token::Type::URL_REF:
      [[fallthrough]];
    case Token::Type::URL_SUFFIX:
      [[fallthrough]];
    case Token::Type::URL_SUFFIX_INNERMOST:
      [[fallthrough]];
    case Token::Type::REGULAR:
      return normalizer.NormalizeTerm(token.text);
    case Token::Type::VERBATIM:
      return std::string(token.text);
    case Token::Type::QUERY_EXCLUSION:
      [[fallthrough]];
    case Token::Type::QUERY_LEFT_PARENTHESES:
      [[fallthrough]];
    case Token::Type::QUERY_RIGHT_PARENTHESES:
      [[fallthrough]];
    case Token::Type::QUERY_OR:
      [[fallthrough]];
    case Token::Type::QUERY_PROPERTY:
      [[fallthrough]];
    case Token::Type::INVALID:
      ICING_LOG(WARNING) << "Unable to normalize token of type: "
                         << static_cast<int>(token.type);
      return std::string(token.text);
  }
}

// Returns a CharacterIterator for token's text, advancing one past the last
// matching character from the query term.
CharacterIterator FindMatchEnd(const Normalizer& normalizer, const Token& token,
                               const std::string& match_query_term) {
  switch (token.type) {
    case Token::Type::VERBATIM: {
      // VERBATIM tokens are not normalized. This means the non-normalized
      // matched query term must be either equal to or a prefix of the token's
      // text. Therefore, the match must end at the end of the matched query
      // term.
      CharacterIterator verbatim_match_end =
          CharacterIterator(token.text, 0, 0, 0);
      verbatim_match_end.AdvanceToUtf8(match_query_term.length());
      return verbatim_match_end;
    }
    case Token::Type::QUERY_EXCLUSION:
      [[fallthrough]];
    case Token::Type::QUERY_LEFT_PARENTHESES:
      [[fallthrough]];
    case Token::Type::QUERY_RIGHT_PARENTHESES:
      [[fallthrough]];
    case Token::Type::QUERY_OR:
      [[fallthrough]];
    case Token::Type::QUERY_PROPERTY:
      [[fallthrough]];
    case Token::Type::INVALID:
      ICING_LOG(WARNING)
          << "Unexpected Token type " << static_cast<int>(token.type)
          << " found when finding match end of query term and token.";
      [[fallthrough]];
    case Token::Type::RFC822_NAME:
      [[fallthrough]];
    case Token::Type::RFC822_COMMENT:
      [[fallthrough]];
    case Token::Type::RFC822_LOCAL_ADDRESS:
      [[fallthrough]];
    case Token::Type::RFC822_HOST_ADDRESS:
      [[fallthrough]];
    case Token::Type::RFC822_ADDRESS:
      [[fallthrough]];
    case Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL:
      [[fallthrough]];
    case Token::Type::RFC822_ADDRESS_COMPONENT_HOST:
      [[fallthrough]];
    case Token::Type::RFC822_TOKEN:
      [[fallthrough]];
    case Token::Type::URL_SCHEME:
      [[fallthrough]];
    case Token::Type::URL_USERNAME:
      [[fallthrough]];
    case Token::Type::URL_PASSWORD:
      [[fallthrough]];
    case Token::Type::URL_HOST_COMMON_PART:
      [[fallthrough]];
    case Token::Type::URL_HOST_SIGNIFICANT_PART:
      [[fallthrough]];
    case Token::Type::URL_PORT:
      [[fallthrough]];
    case Token::Type::URL_QUERY:
      [[fallthrough]];
    case Token::Type::URL_PATH_PART:
      [[fallthrough]];
    case Token::Type::URL_REF:
      [[fallthrough]];
    case Token::Type::URL_SUFFIX:
      [[fallthrough]];
    case Token::Type::URL_SUFFIX_INNERMOST:
      [[fallthrough]];
    case Token::Type::REGULAR:
      return normalizer.FindNormalizedMatchEndPosition(token.text,
                                                       match_query_term);
  }
}

class TokenMatcher {
 public:
  virtual ~TokenMatcher() = default;

  // Returns a CharacterIterator pointing just past the end of the substring in
  // token.text that matches a query term. Note that the utf* indices will be
  // in relation to token.text's start.
  //
  // If there is no match, then it will construct a CharacterIterator with all
  // of its indices set to -1.
  //
  // Ex. With an exact matcher, query terms=["foo","bar"] and token.text="bar",
  // Matches will return a CharacterIterator(u8:3, u16:3, u32:3).
  virtual CharacterIterator Matches(Token token) const = 0;
};

class TokenMatcherExact : public TokenMatcher {
 public:
  explicit TokenMatcherExact(
      const std::unordered_set<std::string>& unrestricted_query_terms,
      const std::unordered_set<std::string>& restricted_query_terms,
      const Normalizer& normalizer)
      : unrestricted_query_terms_(unrestricted_query_terms),
        restricted_query_terms_(restricted_query_terms),
        normalizer_(normalizer) {}

  CharacterIterator Matches(Token token) const override {
    std::string s = NormalizeToken(normalizer_, token);
    auto itr = unrestricted_query_terms_.find(s);
    if (itr == unrestricted_query_terms_.end()) {
      itr = restricted_query_terms_.find(s);
    }
    if (itr != unrestricted_query_terms_.end() &&
        itr != restricted_query_terms_.end()) {
      return FindMatchEnd(normalizer_, token, *itr);
    }

    return CharacterIterator(token.text, -1, -1, -1);
  }

 private:
  const std::unordered_set<std::string>& unrestricted_query_terms_;
  const std::unordered_set<std::string>& restricted_query_terms_;
  const Normalizer& normalizer_;
};

class TokenMatcherPrefix : public TokenMatcher {
 public:
  explicit TokenMatcherPrefix(
      const std::unordered_set<std::string>& unrestricted_query_terms,
      const std::unordered_set<std::string>& restricted_query_terms,
      const Normalizer& normalizer)
      : unrestricted_query_terms_(unrestricted_query_terms),
        restricted_query_terms_(restricted_query_terms),
        normalizer_(normalizer) {}

  CharacterIterator Matches(Token token) const override {
    std::string s = NormalizeToken(normalizer_, token);
    for (const std::string& query_term : unrestricted_query_terms_) {
      if (query_term.length() <= s.length() &&
          s.compare(0, query_term.length(), query_term) == 0) {
        return FindMatchEnd(normalizer_, token, query_term);
      }
    }
    for (const std::string& query_term : restricted_query_terms_) {
      if (query_term.length() <= s.length() &&
          s.compare(0, query_term.length(), query_term) == 0) {
        return FindMatchEnd(normalizer_, token, query_term);
      }
    }
    return CharacterIterator(token.text, -1, -1, -1);
  }

 private:
  const std::unordered_set<std::string>& unrestricted_query_terms_;
  const std::unordered_set<std::string>& restricted_query_terms_;
  const Normalizer& normalizer_;
};

libtextclassifier3::StatusOr<std::unique_ptr<TokenMatcher>> CreateTokenMatcher(
    TermMatchType::Code match_type,
    const std::unordered_set<std::string>& unrestricted_query_terms,
    const std::unordered_set<std::string>& restricted_query_terms,
    const Normalizer& normalizer) {
  switch (match_type) {
    case TermMatchType::EXACT_ONLY:
      return std::make_unique<TokenMatcherExact>(
          unrestricted_query_terms, restricted_query_terms, normalizer);
    case TermMatchType::PREFIX:
      return std::make_unique<TokenMatcherPrefix>(
          unrestricted_query_terms, restricted_query_terms, normalizer);
    case TermMatchType::UNKNOWN:
      [[fallthrough]];
    default:
      return absl_ports::InvalidArgumentError("Invalid match type provided.");
  }
}

// Finds the start position of a valid token that is after
// window_start_min_exclusive_utf32
//
// Returns:
//   the position of the window start if successful
//   INTERNAL_ERROR - if a tokenizer error is encountered
libtextclassifier3::StatusOr<CharacterIterator> DetermineWindowStart(
    const ResultSpecProto::SnippetSpecProto& snippet_spec,
    std::string_view value, int window_start_min_exclusive_utf32,
    Tokenizer::Iterator* iterator) {
  if (!iterator->ResetToTokenStartingAfter(window_start_min_exclusive_utf32)) {
    return absl_ports::InternalError(
        "Couldn't reset tokenizer to determine snippet window!");
  }
  return iterator->CalculateTokenStart();
}

// Increments window_end_exclusive so long as the character at the position
// of window_end_exclusive is punctuation and does not exceed
// window_end_max_exclusive_utf32.
CharacterIterator IncludeTrailingPunctuation(
    std::string_view value, CharacterIterator window_end_exclusive,
    int window_end_max_exclusive_utf32) {
  size_t max_search_index = value.length() - 1;
  while (window_end_exclusive.utf8_index() <= max_search_index &&
         window_end_exclusive.utf32_index() < window_end_max_exclusive_utf32) {
    int char_len = 0;
    if (!i18n_utils::IsPunctuationAt(value, window_end_exclusive.utf8_index(),
                                     &char_len)) {
      break;
    }
    // Expand window by char_len and check the next character.
    window_end_exclusive.AdvanceToUtf32(window_end_exclusive.utf32_index() + 1);
  }
  return window_end_exclusive;
}

// Finds the end position of a valid token that is before the
// window_end_max_exclusive_utf32.
//
// Returns:
//   the position of the window end if successful
//   INTERNAL_ERROR - if a tokenizer error is encountered
libtextclassifier3::StatusOr<CharacterIterator> DetermineWindowEnd(
    const ResultSpecProto::SnippetSpecProto& snippet_spec,
    std::string_view value, int window_end_max_exclusive_utf32,
    Tokenizer::Iterator* iterator) {
  if (!iterator->ResetToTokenEndingBefore(window_end_max_exclusive_utf32)) {
    return absl_ports::InternalError(
        "Couldn't reset tokenizer to determine snippet window!");
  }
  ICING_ASSIGN_OR_RETURN(CharacterIterator end_exclusive,
                         iterator->CalculateTokenEndExclusive());
  return IncludeTrailingPunctuation(value, end_exclusive,
                                    window_end_max_exclusive_utf32);
}

struct SectionData {
  std::string_view section_name;
  std::string_view section_subcontent;
};

// Creates a snippet match proto for the match pointed to by the iterator,
// between start_itr and end_itr
// Returns:
//   the position of the window start if successful
//   INTERNAL_ERROR - if a tokenizer error is encountered and iterator is left
//     in an invalid state
//   ABORTED_ERROR - if an invalid utf-8 sequence is encountered
libtextclassifier3::StatusOr<SnippetMatchProto> RetrieveMatch(
    const ResultSpecProto::SnippetSpecProto& snippet_spec,
    const SectionData& value, Tokenizer::Iterator* iterator,
    const CharacterIterator& start_itr, const CharacterIterator& end_itr) {
  SnippetMatchProto snippet_match;
  // When finding boundaries,  we have a few cases:
  //
  // Case 1:
  //   If we have an odd length match an odd length window, the window surrounds
  //   the match perfectly.
  //     match  = "bar" in "foo bar baz"
  //     window =              |---|
  //
  // Case 2:
  //   If we have an even length match with an even length window, the window
  //   surrounds the match perfectly.
  //     match  = "baar" in "foo baar baz"
  //     window =               |----|
  //
  // Case 3:
  //   If we have an odd length match with an even length window, we allocate
  //   that extra window byte to the beginning.
  //     match  = "bar" in "foo bar baz"
  //     window =             |----|
  //
  // Case 4:
  //   If we have an even length match with an odd length window, we allocate
  //   that extra window byte to the end.
  //     match  = "baar" in "foo baar baz"
  //     window =               |-----|
  //
  // We have do +1/-1 below to get the math to match up.
  int match_pos_utf32 = start_itr.utf32_index();
  int match_len_utf32 = end_itr.utf32_index() - match_pos_utf32;
  int match_mid_utf32 = match_pos_utf32 + match_len_utf32 / 2;
  int window_start_min_exclusive_utf32 =
      (match_mid_utf32 - snippet_spec.max_window_utf32_length() / 2) - 1;
  int window_end_max_exclusive_utf32 =
      match_mid_utf32 + (snippet_spec.max_window_utf32_length() + 1) / 2;

  snippet_match.set_exact_match_byte_position(start_itr.utf8_index());
  snippet_match.set_exact_match_utf16_position(start_itr.utf16_index());
  snippet_match.set_exact_match_byte_length(end_itr.utf8_index() -
                                            start_itr.utf8_index());
  snippet_match.set_exact_match_utf16_length(end_itr.utf16_index() -
                                             start_itr.utf16_index());

  // Only include windows if it'll at least include the matched text. Otherwise,
  // it'll just be an empty string anyways.
  if (snippet_spec.max_window_utf32_length() >= match_len_utf32) {
    // Find the beginning of the window.
    ICING_ASSIGN_OR_RETURN(
        CharacterIterator window_start,
        DetermineWindowStart(snippet_spec, value.section_subcontent,
                             window_start_min_exclusive_utf32, iterator));

    // Check. Did we get fewer characters than we requested? If so, then add it
    // on to the window_end.
    int extra_window_space =
        window_start.utf32_index() - 1 - window_start_min_exclusive_utf32;
    window_end_max_exclusive_utf32 += extra_window_space;

    // Find the end of the window.
    ICING_ASSIGN_OR_RETURN(
        CharacterIterator window_end,
        DetermineWindowEnd(snippet_spec, value.section_subcontent,
                           window_end_max_exclusive_utf32, iterator));

    // Check one more time. Did we get fewer characters than we requested? If
    // so, then see if we can push the start back again.
    extra_window_space =
        window_end_max_exclusive_utf32 - window_end.utf32_index();
    if (extra_window_space > 0) {
      window_start_min_exclusive_utf32 =
          window_start.utf32_index() - 1 - extra_window_space;
      ICING_ASSIGN_OR_RETURN(
          window_start,
          DetermineWindowStart(snippet_spec, value.section_subcontent,
                               window_start_min_exclusive_utf32, iterator));
    }

    snippet_match.set_window_byte_position(window_start.utf8_index());
    snippet_match.set_window_utf16_position(window_start.utf16_index());
    snippet_match.set_window_byte_length(window_end.utf8_index() -
                                         window_start.utf8_index());
    snippet_match.set_window_utf16_length(window_end.utf16_index() -
                                          window_start.utf16_index());

    // DetermineWindowStart/End may change the position of the iterator, but it
    // will be reset once the entire batch of tokens is checked.
  }

  return snippet_match;
}

struct MatchOptions {
  const ResultSpecProto::SnippetSpecProto& snippet_spec;
  int max_matches_remaining;
};

// Retrieves snippets in the string values of current_property.
// Tokenizer is provided to tokenize string content and matcher is provided to
// indicate when a token matches content in the query.
//
// current_property is the property with the string values to snippet.
// property_path is the path in the document to current_property.
//
// MatchOptions holds the snippet spec and number of desired matches remaining.
// Each call to GetEntriesFromProperty will decrement max_matches_remaining
// by the number of entries that it adds to snippet_proto.
//
// The SnippetEntries found for matched content will be added to snippet_proto.
void GetEntriesFromProperty(const PropertyProto* current_property,
                            const std::string& property_path,
                            const TokenMatcher* matcher,
                            const Tokenizer* tokenizer,
                            MatchOptions* match_options,
                            SnippetProto* snippet_proto) {
  // We're at the end. Let's check our values.
  for (int i = 0; i < current_property->string_values_size(); ++i) {
    SnippetProto::EntryProto snippet_entry;
    snippet_entry.set_property_name(AddIndexToPath(
        current_property->string_values_size(), /*index=*/i, property_path));
    std::string_view value = current_property->string_values(i);
    std::unique_ptr<Tokenizer::Iterator> iterator =
        tokenizer->Tokenize(value).ValueOrDie();
    // All iterators are moved through positions sequentially. Constructing them
    // each time resets them to the beginning of the string. This means that,
    // for t tokens and in a string of n chars, each MoveToUtf8 call from the
    // beginning of the string is on average O(n/2), whereas calling MoveToUtf8
    // from the token immediately prior to the desired one is O(n/t).
    // Constructing each outside of the while-loop ensures that performance will
    // be O(t * (n/t)) = O(n) rather than O(t * n / 2).
    CharacterIterator start_itr(value);
    CharacterIterator end_itr(value);
    CharacterIterator reset_itr(value);
    bool encountered_error = false;
    while (iterator->Advance()) {
      std::vector<Token> batch_tokens = iterator->GetTokens();
      if (batch_tokens.empty()) {
        continue;
      }

      bool needs_reset = false;
      reset_itr.MoveToUtf8(batch_tokens.at(0).text.begin() - value.begin());
      start_itr = reset_itr;
      end_itr = start_itr;
      for (int i = 0; i < batch_tokens.size(); ++i) {
        const Token& token = batch_tokens.at(i);
        CharacterIterator submatch_end = matcher->Matches(token);
        // If the token matched a query term, then submatch_end will point to an
        // actual position within token.text.
        if (submatch_end.utf8_index() == -1) {
          continue;
        }
        // As snippet matching may move iterator around, we save a reset
        // iterator so that we can reset to the initial iterator state, and
        // continue Advancing in order in the next round.
        if (!start_itr.MoveToUtf8(token.text.begin() - value.begin())) {
          encountered_error = true;
          break;
        }
        if (!end_itr.MoveToUtf8(token.text.end() - value.begin())) {
          encountered_error = true;
          break;
        }
        SectionData data = {property_path, value};
        auto match_or = RetrieveMatch(match_options->snippet_spec, data,
                                      iterator.get(), start_itr, end_itr);
        if (!match_or.ok()) {
          if (absl_ports::IsAborted(match_or.status())) {
            // Only an aborted. We can't get this match, but we might be able
            // to retrieve others. Just continue.
            continue;
          } else {
            encountered_error = true;
            break;
          }
        }
        SnippetMatchProto match = std::move(match_or).ValueOrDie();
        if (match.window_byte_length() > 0) {
          needs_reset = true;
        }
        // submatch_end refers to a position *within* token.text.
        // This, conveniently enough, means that index that submatch_end
        // points to is the length of the submatch (because the submatch
        // starts at 0 in token.text).
        match.set_submatch_byte_length(submatch_end.utf8_index());
        match.set_submatch_utf16_length(submatch_end.utf16_index());
        // Add the values for the submatch.
        snippet_entry.mutable_snippet_matches()->Add(std::move(match));

        if (--match_options->max_matches_remaining <= 0) {
          *snippet_proto->add_entries() = std::move(snippet_entry);
          return;
        }
      }

      if (encountered_error) {
        break;
      }

      // RetrieveMatch may call DetermineWindowStart/End if windowing is
      // requested, which may change the position of the iterator. So, reset the
      // iterator back to the original position. The first token of the token
      // batch will be the token to reset to.
      if (needs_reset) {
        if (reset_itr.utf8_index() > 0) {
          encountered_error =
              !iterator->ResetToTokenStartingAfter(reset_itr.utf32_index() - 1);
        } else {
          encountered_error = !iterator->ResetToStart();
        }
      }
      if (encountered_error) {
        break;
      }
    }
    if (!snippet_entry.snippet_matches().empty()) {
      *snippet_proto->add_entries() = std::move(snippet_entry);
    }
  }
}

// Retrieves snippets in document from content at section_path.
// Tokenizer is provided to tokenize string content and matcher is provided to
// indicate when a token matches content in the query.
//
// section_path_index refers to the current property that is held by document.
// current_path is equivalent to the first section_path_index values in
// section_path, but with value indices present.
//
// For example, suppose that a hit appeared somewhere in the "bcc.emailAddress".
// The arguments for RetrieveSnippetForSection might be
// {section_path=["bcc", "emailAddress"], section_path_index=0, current_path=""}
// on the first call and
// {section_path=["bcc", "emailAddress"], section_path_index=1,
// current_path="bcc[1]"} on the second recursive call.
//
// MatchOptions holds the snippet spec and number of desired matches remaining.
// Each call to RetrieveSnippetForSection will decrement max_matches_remaining
// by the number of entries that it adds to snippet_proto.
//
// The SnippetEntries found for matched content will be added to snippet_proto.
void RetrieveSnippetForSection(
    const DocumentProto& document, const TokenMatcher* matcher,
    const Tokenizer* tokenizer,
    const std::vector<std::string_view>& section_path, int section_path_index,
    const std::string& current_path, MatchOptions* match_options,
    SnippetProto* snippet_proto) {
  std::string_view next_property_name = section_path.at(section_path_index);
  const PropertyProto* current_property =
      property_util::GetPropertyProto(document, next_property_name);
  if (current_property == nullptr) {
    ICING_VLOG(1) << "No property " << next_property_name << " found at path "
                  << current_path;
    return;
  }
  std::string property_path = property_util::ConcatenatePropertyPathExpr(
      current_path, next_property_name);
  if (section_path_index == section_path.size() - 1) {
    // We're at the end. Let's check our values.
    GetEntriesFromProperty(current_property, property_path, matcher, tokenizer,
                           match_options, snippet_proto);
  } else {
    // Still got more to go. Let's look through our subdocuments.
    std::vector<SnippetProto::EntryProto> entries;
    for (int i = 0; i < current_property->document_values_size(); ++i) {
      std::string new_path = AddIndexToPath(
          current_property->document_values_size(), /*index=*/i, property_path);
      RetrieveSnippetForSection(current_property->document_values(i), matcher,
                                tokenizer, section_path, section_path_index + 1,
                                new_path, match_options, snippet_proto);
      if (match_options->max_matches_remaining <= 0) {
        break;
      }
    }
  }
}

}  // namespace

libtextclassifier3::StatusOr<std::unique_ptr<SnippetRetriever>>
SnippetRetriever::Create(const SchemaStore* schema_store,
                         const LanguageSegmenter* language_segmenter,
                         const Normalizer* normalizer) {
  ICING_RETURN_ERROR_IF_NULL(schema_store);
  ICING_RETURN_ERROR_IF_NULL(language_segmenter);
  ICING_RETURN_ERROR_IF_NULL(normalizer);

  return std::unique_ptr<SnippetRetriever>(
      new SnippetRetriever(schema_store, language_segmenter, normalizer));
}

SnippetProto SnippetRetriever::RetrieveSnippet(
    const SectionRestrictQueryTermsMap& query_terms,
    TermMatchType::Code match_type,
    const ResultSpecProto::SnippetSpecProto& snippet_spec,
    const DocumentProto& document, SectionIdMask section_id_mask) const {
  SnippetProto snippet_proto;
  ICING_ASSIGN_OR_RETURN(SchemaTypeId type_id,
                         schema_store_.GetSchemaTypeId(document.schema()),
                         snippet_proto);
  const std::unordered_set<std::string> empty_set;
  auto itr = query_terms.find("");
  const std::unordered_set<std::string>& unrestricted_set =
      (itr != query_terms.end()) ? itr->second : empty_set;
  while (section_id_mask != kSectionIdMaskNone) {
    SectionId section_id = __builtin_ctzll(section_id_mask);
    // Remove this section from the mask.
    section_id_mask &= ~(UINT64_C(1) << section_id);

    MatchOptions match_options = {snippet_spec};
    match_options.max_matches_remaining =
        snippet_spec.num_matches_per_property();

    // Determine the section name and match type.
    auto section_metadata_or =
        schema_store_.GetSectionMetadata(type_id, section_id);
    if (!section_metadata_or.ok()) {
      continue;
    }
    const SectionMetadata* metadata = section_metadata_or.ValueOrDie();
    std::vector<std::string_view> section_path =
        property_util::SplitPropertyPathExpr(metadata->path);

    // Match type must be as restrictive as possible. Prefix matches for a
    // snippet should only be included if both the query is Prefix and the
    // section has prefixes enabled.
    TermMatchType::Code section_match_type = TermMatchType::EXACT_ONLY;
    if (match_type == TermMatchType::PREFIX &&
        metadata->term_match_type == TermMatchType::PREFIX) {
      section_match_type = TermMatchType::PREFIX;
    }

    itr = query_terms.find(metadata->path);
    const std::unordered_set<std::string>& restricted_set =
        (itr != query_terms.end()) ? itr->second : empty_set;
    libtextclassifier3::StatusOr<std::unique_ptr<TokenMatcher>> matcher_or =
        CreateTokenMatcher(section_match_type, unrestricted_set, restricted_set,
                           normalizer_);
    if (!matcher_or.ok()) {
      continue;
    }
    std::unique_ptr<TokenMatcher> matcher = std::move(matcher_or).ValueOrDie();

    auto tokenizer_or = tokenizer_factory::CreateIndexingTokenizer(
        metadata->tokenizer, &language_segmenter_);
    if (!tokenizer_or.ok()) {
      // If we couldn't create the tokenizer properly, just skip this section.
      continue;
    }
    std::unique_ptr<Tokenizer> tokenizer = std::move(tokenizer_or).ValueOrDie();
    RetrieveSnippetForSection(
        document, matcher.get(), tokenizer.get(), section_path,
        /*section_path_index=*/0, "", &match_options, &snippet_proto);
  }
  return snippet_proto;
}

}  // namespace lib
}  // namespace icing
