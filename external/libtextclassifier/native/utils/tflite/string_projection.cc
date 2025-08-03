/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "utils/tflite/string_projection.h"

#include <string>
#include <unordered_map>

#include "utils/strings/utf8.h"
#include "utils/tflite/string_projection_base.h"
#include "utils/utf8/unilib-common.h"
#include "absl/container/flat_hash_set.h"
#include "absl/strings/match.h"
#include "flatbuffers/flexbuffers.h"
#include "tensorflow/lite/context.h"
#include "tensorflow/lite/string_util.h"

namespace tflite {
namespace ops {
namespace custom {

namespace libtextclassifier3 {
namespace string_projection {
namespace {

const char kStartToken[] = "<S>";
const char kEndToken[] = "<E>";
const char kEmptyToken[] = "<S> <E>";
constexpr size_t kEntireString = SIZE_MAX;
constexpr size_t kAllTokens = SIZE_MAX;
constexpr int kInvalid = -1;

constexpr char kApostrophe = '\'';
constexpr char kSpace = ' ';
constexpr char kComma = ',';
constexpr char kDot = '.';

// Returns true if the given text contains a number.
bool IsDigitString(const std::string& text) {
  for (size_t i = 0; i < text.length();) {
    const int bytes_read =
        ::libtextclassifier3::GetNumBytesForUTF8Char(text.data());
    if (bytes_read <= 0 || bytes_read > text.length() - i) {
      break;
    }
    const char32_t rune = ::libtextclassifier3::ValidCharToRune(text.data());
    if (::libtextclassifier3::IsDigit(rune)) return true;
    i += bytes_read;
  }
  return false;
}

// Gets the string containing |num_chars| characters from |start| position.
std::string GetCharToken(const std::vector<std::string>& char_tokens, int start,
                         int num_chars) {
  std::string char_token = "";
  if (start + num_chars <= char_tokens.size()) {
    for (int i = 0; i < num_chars; ++i) {
      char_token.append(char_tokens[start + i]);
    }
  }
  return char_token;
}

// Counts how many times |pattern| appeared from |start| position.
int GetNumPattern(const std::vector<std::string>& char_tokens, size_t start,
                  size_t num_chars, const std::string& pattern) {
  int count = 0;
  for (int i = start; i < char_tokens.size(); i += num_chars) {
    std::string cur_pattern = GetCharToken(char_tokens, i, num_chars);
    if (pattern == cur_pattern) {
      ++count;
    } else {
      break;
    }
  }
  return count;
}

inline size_t FindNextSpace(const char* input_ptr, size_t from, size_t length) {
  size_t space_index;
  for (space_index = from; space_index < length; space_index++) {
    if (input_ptr[space_index] == kSpace) {
      break;
    }
  }
  return space_index == length ? kInvalid : space_index;
}

template <typename T>
void SplitByCharInternal(std::vector<T>* tokens, const char* input_ptr,
                         size_t len, size_t max_tokens) {
  for (size_t i = 0; i < len;) {
    auto bytes_read =
        ::libtextclassifier3::GetNumBytesForUTF8Char(input_ptr + i);
    if (bytes_read <= 0 || bytes_read > len - i) break;
    tokens->emplace_back(input_ptr + i, bytes_read);
    if (max_tokens != kInvalid && tokens->size() == max_tokens) {
      break;
    }
    i += bytes_read;
  }
}

std::vector<std::string> SplitByChar(const char* input_ptr, size_t len,
                                     size_t max_tokens) {
  std::vector<std::string> tokens;
  SplitByCharInternal(&tokens, input_ptr, len, max_tokens);
  return tokens;
}

std::string ContractToken(const char* input_ptr, size_t len, size_t num_chars) {
  // This function contracts patterns whose length is |num_chars| and appeared
  // more than twice. So if the input is shorter than 3 * |num_chars|, do not
  // apply any contraction.
  if (len < 3 * num_chars) {
    return input_ptr;
  }
  std::vector<std::string> char_tokens = SplitByChar(input_ptr, len, len);

  std::string token;
  token.reserve(len);
  for (int i = 0; i < char_tokens.size();) {
    std::string cur_pattern = GetCharToken(char_tokens, i, num_chars);

    // Count how many times this pattern appeared.
    int num_cur_patterns = 0;
    if (!absl::StrContains(cur_pattern, " ") && !IsDigitString(cur_pattern)) {
      num_cur_patterns =
          GetNumPattern(char_tokens, i + num_chars, num_chars, cur_pattern);
    }

    if (num_cur_patterns >= 2) {
      // If this pattern is repeated, store it only twice.
      token.append(cur_pattern);
      token.append(cur_pattern);
      i += (num_cur_patterns + 1) * num_chars;
    } else {
      token.append(char_tokens[i]);
      ++i;
    }
  }

  return token;
}

template <typename T>
void SplitBySpaceInternal(std::vector<T>* tokens, const char* input_ptr,
                          size_t len, size_t max_input, size_t max_tokens) {
  size_t last_index =
      max_input == kEntireString ? len : (len < max_input ? len : max_input);
  size_t start = 0;
  // skip leading spaces
  while (start < last_index && input_ptr[start] == kSpace) {
    start++;
  }
  auto end = FindNextSpace(input_ptr, start, last_index);
  while (end != kInvalid &&
         (max_tokens == kAllTokens || tokens->size() < max_tokens - 1)) {
    auto length = end - start;
    if (length > 0) {
      tokens->emplace_back(input_ptr + start, length);
    }

    start = end + 1;
    end = FindNextSpace(input_ptr, start, last_index);
  }
  auto length = end == kInvalid ? (last_index - start) : (end - start);
  if (length > 0) {
    tokens->emplace_back(input_ptr + start, length);
  }
}

std::vector<std::string> SplitBySpace(const char* input_ptr, size_t len,
                                      size_t max_input, size_t max_tokens) {
  std::vector<std::string> tokens;
  SplitBySpaceInternal(&tokens, input_ptr, len, max_input, max_tokens);
  return tokens;
}

bool prepend_separator(char separator) { return separator == kApostrophe; }

bool is_numeric(char c) { return c >= '0' && c <= '9'; }

class ProjectionNormalizer {
 public:
  explicit ProjectionNormalizer(const std::string& separators,
                                bool normalize_repetition = false) {
    InitializeSeparators(separators);
    normalize_repetition_ = normalize_repetition;
  }

  // Normalizes the repeated characters (except numbers) which consecutively
  // appeared more than twice in a word.
  std::string Normalize(const std::string& input, size_t max_input = 300) {
    return Normalize(input.data(), input.length(), max_input);
  }
  std::string Normalize(const char* input_ptr, size_t len,
                        size_t max_input = 300) {
    std::string normalized(input_ptr, std::min(len, max_input));

    if (normalize_repetition_) {
      // Remove repeated 1 char (e.g. soooo => soo)
      normalized = ContractToken(normalized.data(), normalized.length(), 1);

      // Remove repeated 2 chars from the beginning (e.g. hahaha =>
      // haha, xhahaha => xhaha, xyhahaha => xyhaha).
      normalized = ContractToken(normalized.data(), normalized.length(), 2);

      // Remove repeated 3 chars from the beginning
      // (e.g. wowwowwow => wowwow, abcdbcdbcd => abcdbcd).
      normalized = ContractToken(normalized.data(), normalized.length(), 3);
    }

    if (!separators_.empty()) {
      // Add space around separators_.
      normalized = NormalizeInternal(normalized.data(), normalized.length());
    }
    return normalized;
  }

 private:
  // Parses and extracts supported separators.
  void InitializeSeparators(const std::string& separators) {
    for (int i = 0; i < separators.length(); ++i) {
      if (separators[i] != ' ') {
        separators_.insert(separators[i]);
      }
    }
  }

  // Removes repeated chars.
  std::string NormalizeInternal(const char* input_ptr, size_t len) {
    std::string normalized;
    normalized.reserve(len * 2);
    for (int i = 0; i < len; ++i) {
      char c = input_ptr[i];
      bool matched_separator = separators_.find(c) != separators_.end();
      if (matched_separator) {
        if (i > 0 && input_ptr[i - 1] != ' ' && normalized.back() != ' ') {
          normalized.append(" ");
        }
      }
      normalized.append(1, c);
      if (matched_separator) {
        if (i + 1 < len && input_ptr[i + 1] != ' ' && c != '\'') {
          normalized.append(" ");
        }
      }
    }
    return normalized;
  }

  absl::flat_hash_set<char> separators_;
  bool normalize_repetition_;
};

class ProjectionTokenizer {
 public:
  explicit ProjectionTokenizer(const std::string& separators) {
    InitializeSeparators(separators);
  }

  // Tokenizes the input by separators_. Limit to max_tokens, when it is not -1.
  std::vector<std::string> Tokenize(const std::string& input, size_t max_input,
                                    size_t max_tokens) const {
    return Tokenize(input.c_str(), input.size(), max_input, max_tokens);
  }

  std::vector<std::string> Tokenize(const char* input_ptr, size_t len,
                                    size_t max_input, size_t max_tokens) const {
    // If separators_ is not given, tokenize the input with a space.
    if (separators_.empty()) {
      return SplitBySpace(input_ptr, len, max_input, max_tokens);
    }

    std::vector<std::string> tokens;
    size_t last_index =
        max_input == kEntireString ? len : (len < max_input ? len : max_input);
    size_t start = 0;
    // Skip leading spaces.
    while (start < last_index && input_ptr[start] == kSpace) {
      start++;
    }
    auto end = FindNextSeparator(input_ptr, start, last_index);

    while (end != kInvalid &&
           (max_tokens == kAllTokens || tokens.size() < max_tokens - 1)) {
      auto length = end - start;
      if (length > 0) tokens.emplace_back(input_ptr + start, length);

      // Add the separator (except space and apostrophe) as a token
      char separator = input_ptr[end];
      if (separator != kSpace && separator != kApostrophe) {
        tokens.emplace_back(input_ptr + end, 1);
      }

      start = end + (prepend_separator(separator) ? 0 : 1);
      end = FindNextSeparator(input_ptr, end + 1, last_index);
    }
    auto length = end == kInvalid ? (last_index - start) : (end - start);
    if (length > 0) tokens.emplace_back(input_ptr + start, length);
    return tokens;
  }

 private:
  // Parses and extracts supported separators.
  void InitializeSeparators(const std::string& separators) {
    for (int i = 0; i < separators.length(); ++i) {
      separators_.insert(separators[i]);
    }
  }

  // Starting from input_ptr[from], search for the next occurrence of
  // separators_. Don't search beyond input_ptr[length](non-inclusive). Return
  // -1 if not found.
  size_t FindNextSeparator(const char* input_ptr, size_t from,
                           size_t length) const {
    auto index = from;
    while (index < length) {
      char c = input_ptr[index];
      // Do not break a number (e.g. "10,000", "0.23").
      if (c == kComma || c == kDot) {
        if (index + 1 < length && is_numeric(input_ptr[index + 1])) {
          c = input_ptr[++index];
        }
      }
      if (separators_.find(c) != separators_.end()) {
        break;
      }
      ++index;
    }
    return index == length ? kInvalid : index;
  }

  absl::flat_hash_set<char> separators_;
};

inline void StripTrailingAsciiPunctuation(std::string* str) {
  auto it = std::find_if_not(str->rbegin(), str->rend(), ::ispunct);
  str->erase(str->rend() - it);
}

std::string PreProcessString(const char* str, int len,
                             const bool remove_punctuation) {
  std::string output_str(str, len);
  std::transform(output_str.begin(), output_str.end(), output_str.begin(),
                 ::tolower);

  // Remove trailing punctuation.
  if (remove_punctuation) {
    StripTrailingAsciiPunctuation(&output_str);
  }

  if (output_str.empty()) {
    output_str.assign(str, len);
  }
  return output_str;
}

bool ShouldIncludeCurrentNgram(const SkipGramParams& params, int size) {
  if (size <= 0) {
    return false;
  }
  if (params.include_all_ngrams) {
    return size <= params.ngram_size;
  } else {
    return size == params.ngram_size;
  }
}

bool ShouldStepInRecursion(const std::vector<int>& stack, int stack_idx,
                           int num_words, const SkipGramParams& params) {
  // If current stack size and next word enumeration are within valid range.
  if (stack_idx < params.ngram_size && stack[stack_idx] + 1 < num_words) {
    // If this stack is empty, step in for first word enumeration.
    if (stack_idx == 0) {
      return true;
    }
    // If next word enumeration are within the range of max_skip_size.
    // NOTE: equivalent to
    //   next_word_idx = stack[stack_idx] + 1
    //   next_word_idx - stack[stack_idx-1] <= max_skip_size + 1
    if (stack[stack_idx] - stack[stack_idx - 1] <= params.max_skip_size) {
      return true;
    }
  }
  return false;
}

std::string JoinTokensBySpace(const std::vector<int>& stack, int stack_idx,
                              const std::vector<std::string>& tokens) {
  int len = 0;
  for (int i = 0; i < stack_idx; i++) {
    len += tokens[stack[i]].size();
  }
  len += stack_idx - 1;

  std::string res;
  res.reserve(len);
  res.append(tokens[stack[0]]);
  for (int i = 1; i < stack_idx; i++) {
    res.append(" ");
    res.append(tokens[stack[i]]);
  }

  return res;
}

std::unordered_map<std::string, int> ExtractSkipGramsImpl(
    const std::vector<std::string>& tokens, const SkipGramParams& params) {
  // Ignore positional tokens.
  static auto* blacklist = new std::unordered_set<std::string>({
      kStartToken,
      kEndToken,
      kEmptyToken,
  });

  std::unordered_map<std::string, int> res;

  // Stack stores the index of word used to generate ngram.
  // The size of stack is the size of ngram.
  std::vector<int> stack(params.ngram_size + 1, 0);
  // Stack index that indicates which depth the recursion is operating at.
  int stack_idx = 1;
  int num_words = tokens.size();

  while (stack_idx >= 0) {
    if (ShouldStepInRecursion(stack, stack_idx, num_words, params)) {
      // When current depth can fill with a new word
      // and the new word is within the max range to skip,
      // fill this word to stack, recurse into next depth.
      stack[stack_idx]++;
      stack_idx++;
      stack[stack_idx] = stack[stack_idx - 1];
    } else {
      if (ShouldIncludeCurrentNgram(params, stack_idx)) {
        // Add n-gram to tensor buffer when the stack has filled with enough
        // words to generate the ngram.
        std::string ngram = JoinTokensBySpace(stack, stack_idx, tokens);
        if (blacklist->find(ngram) == blacklist->end()) {
          res[ngram] = stack_idx;
        }
      }
      // When current depth cannot fill with a valid new word,
      // and not in last depth to generate ngram,
      // step back to previous depth to iterate to next possible word.
      stack_idx--;
    }
  }

  return res;
}

std::unordered_map<std::string, int> ExtractSkipGrams(
    const std::string& input, ProjectionTokenizer* tokenizer,
    ProjectionNormalizer* normalizer, const SkipGramParams& params) {
  // Normalize the input.
  const std::string& normalized =
      normalizer == nullptr
          ? input
          : normalizer->Normalize(input, params.max_input_chars);

  // Split sentence to words.
  std::vector<std::string> tokens;
  if (params.char_level) {
    tokens = SplitByChar(normalized.data(), normalized.size(),
                         params.max_input_chars);
  } else {
    tokens = tokenizer->Tokenize(normalized.data(), normalized.size(),
                                 params.max_input_chars, kAllTokens);
  }

  // Process tokens
  for (int i = 0; i < tokens.size(); ++i) {
    if (params.preprocess) {
      tokens[i] = PreProcessString(tokens[i].data(), tokens[i].size(),
                                   params.remove_punctuation);
    }
  }

  tokens.insert(tokens.begin(), kStartToken);
  tokens.insert(tokens.end(), kEndToken);

  return ExtractSkipGramsImpl(tokens, params);
}
}  // namespace
// Generates LSH projections for input strings.  This uses the framework in
// `string_projection_base.h`, with the implementation details that the input is
// a string tensor of messages and the op will perform tokenization.
//
// Input:
//   tensor[0]: Input message, string[...]
//
// Additional attributes:
//   max_input_chars: int[]
//     maximum number of input characters to use from each message.
//   token_separators: string[]
//     the list of separators used to tokenize the input.
//   normalize_repetition: bool[]
//     if true, remove repeated characters in tokens ('loool' -> 'lol').

static const int kInputMessage = 0;

class StringProjectionOp : public StringProjectionOpBase {
 public:
  explicit StringProjectionOp(const flexbuffers::Map& custom_options)
      : StringProjectionOpBase(custom_options),
        projection_normalizer_(
            custom_options["token_separators"].AsString().str(),
            custom_options["normalize_repetition"].AsBool()),
        projection_tokenizer_(" ") {
    if (custom_options["max_input_chars"].IsInt()) {
      skip_gram_params().max_input_chars =
          custom_options["max_input_chars"].AsInt32();
    }
  }

  TfLiteStatus InitializeInput(TfLiteContext* context,
                               TfLiteNode* node) override {
    input_ = &context->tensors[node->inputs->data[kInputMessage]];
    return kTfLiteOk;
  }

  std::unordered_map<std::string, int> ExtractSkipGrams(int i) override {
    StringRef input = GetString(input_, i);
    return ::tflite::ops::custom::libtextclassifier3::string_projection::
        ExtractSkipGrams({input.str, static_cast<size_t>(input.len)},
                         &projection_tokenizer_, &projection_normalizer_,
                         skip_gram_params());
  }

  void FinalizeInput() override { input_ = nullptr; }

  TfLiteIntArray* GetInputShape(TfLiteContext* context,
                                TfLiteNode* node) override {
    return context->tensors[node->inputs->data[kInputMessage]].dims;
  }

 private:
  ProjectionNormalizer projection_normalizer_;
  ProjectionTokenizer projection_tokenizer_;

  TfLiteTensor* input_;
};

void* Init(TfLiteContext* context, const char* buffer, size_t length) {
  const uint8_t* buffer_t = reinterpret_cast<const uint8_t*>(buffer);
  return new StringProjectionOp(flexbuffers::GetRoot(buffer_t, length).AsMap());
}

}  // namespace string_projection

// This op converts a list of strings to integers via LSH projections.
TfLiteRegistration* Register_STRING_PROJECTION() {
  static TfLiteRegistration r = {libtextclassifier3::string_projection::Init,
                                 libtextclassifier3::string_projection::Free,
                                 libtextclassifier3::string_projection::Resize,
                                 libtextclassifier3::string_projection::Eval};
  return &r;
}

}  // namespace libtextclassifier3
}  // namespace custom
}  // namespace ops
}  // namespace tflite
