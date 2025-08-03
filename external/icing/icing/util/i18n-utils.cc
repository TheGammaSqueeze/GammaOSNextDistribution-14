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

#include "icing/util/i18n-utils.h"

#include <cctype>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/util/logging.h"
#include "unicode/uchar.h"
#include "unicode/umachine.h"
#include "unicode/ustring.h"
#include "unicode/utf16.h"
#include "unicode/utf8.h"
#include "unicode/utypes.h"

namespace icing {
namespace lib {
namespace i18n_utils {

namespace {

// All ASCII punctuation that's also in a Unicode Punctuation category
// (https://www.fileformat.info/info/unicode/category/index.htm). The set of
// characters that are regarded as punctuation is not the same for std::ispunct
// and u_ispunct.
constexpr std::string_view kAsciiIcuPunctuation = "!\"#%&'*,./:;?@\\_-([{}])";

}  // namespace

libtextclassifier3::StatusOr<std::string> Utf16ToUtf8(
    const std::u16string& utf16_string) {
  std::string utf8_string;
  // Allocates the maximum possible UTF8 string length:
  // 3 UTF-8 bytes per UTF16 code unit, plus one for the terminating NUL.
  //
  // NOTE: we need to call resize() but not reserve() because values can't be
  // set at positions after length().
  utf8_string.resize(utf16_string.length() * 3 + 1);

  int result_length = 0;
  UErrorCode status = U_ZERO_ERROR;
  u_strToUTF8(&utf8_string[0], utf8_string.length(), &result_length,
              utf16_string.data(), utf16_string.length(), &status);
  // Corrects the length
  utf8_string.resize(result_length);

  if (U_FAILURE(status)) {
    return absl_ports::InternalError("Failed to convert UTF16 string to UTF8");
  }
  return utf8_string;
}

libtextclassifier3::StatusOr<std::u16string> Utf8ToUtf16(
    std::string_view utf8_string) {
  std::u16string utf16_result;
  // The UTF16 string won't be longer than its UTF8 format
  //
  // NOTE: we need to call resize() but not reserve() because values can't be
  // set at positions after length().
  utf16_result.resize(utf8_string.length());

  int result_length = 0;
  UErrorCode status = U_ZERO_ERROR;
  u_strFromUTF8(&utf16_result[0], utf16_result.length(), &result_length,
                utf8_string.data(), utf8_string.length(), &status);
  // Corrects the length
  utf16_result.resize(result_length);

  if (U_FAILURE(status)) {
    return absl_ports::InternalError(absl_ports::StrCat(
        "Failed to convert UTF8 string '", utf8_string, "' to UTF16"));
  }
  return utf16_result;
}

UChar32 GetUChar32At(const char* data, int length, int position) {
  UChar32 uchar32;
  U8_NEXT_OR_FFFD(data, position, length, uchar32);
  return uchar32;
}

void SafeTruncateUtf8(std::string* str, int truncate_to_length) {
  if (str == nullptr || truncate_to_length >= str->length()) {
    return;
  }

  str->resize(SafeTruncateUtf8Length(str->c_str(), truncate_to_length));
}

int SafeTruncateUtf8Length(const char* str, int desired_length) {
  while (desired_length > 0) {
    if (IsLeadUtf8Byte(str[desired_length])) {
      break;
    }
    --desired_length;
  }
  return desired_length;
}

bool IsAscii(char c) { return U8_IS_SINGLE((uint8_t)c); }

bool IsAscii(UChar32 c) { return U8_LENGTH(c) == 1; }

bool IsAlphaNumeric(UChar32 c) { return u_isalnum(c); }

int GetUtf8Length(UChar32 c) { return U8_LENGTH(c); }

int GetUtf16Length(UChar32 c) { return U16_LENGTH(c); }

bool IsLeadUtf8Byte(char c) { return IsAscii(c) || U8_IS_LEAD((uint8_t)c); }

bool IsPunctuationAt(std::string_view input, int position, int* char_len_out) {
  if (IsAscii(input[position])) {
    if (char_len_out != nullptr) {
      *char_len_out = 1;
    }
    return kAsciiIcuPunctuation.find(input[position]) != std::string_view::npos;
  }
  UChar32 c = GetUChar32At(input.data(), input.length(), position);
  if (char_len_out != nullptr) {
    *char_len_out = U8_LENGTH(c);
  }
  return u_ispunct(c);
}

bool IsWhitespaceAt(std::string_view input, int position) {
  if (IsAscii(input[position])) {
    return std::isspace(input[position]);
  }
  UChar32 c = GetUChar32At(input.data(), input.length(), position);
  return u_isUWhiteSpace(c);
}

bool IsAlphabeticAt(std::string_view input, int position) {
  if (IsAscii(input[position])) {
    return std::isalpha(input[position]);
  }
  UChar32 c = GetUChar32At(input.data(), input.length(), position);
  return u_isUAlphabetic(c);
}

void AppendUchar32ToUtf8(std::string* utf8_string, UChar32 uchar) {
  uint8_t utf8_buffer[4];  // U8_APPEND writes 0 to 4 bytes

  int utf8_index = 0;
  UBool has_error = false;

  // utf8_index is advanced to the end of the contents if successful
  U8_APPEND(utf8_buffer, utf8_index, sizeof(utf8_buffer), uchar, has_error);

  if (has_error) {
    ICING_LOG(WARNING) << "Error appending UChar32 to the UTF8 string.";
    return;
  }
  utf8_string->append(reinterpret_cast<char*>(utf8_buffer), utf8_index);
}

}  // namespace i18n_utils
}  // namespace lib
}  // namespace icing
