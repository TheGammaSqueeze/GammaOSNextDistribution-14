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

#include "icing/testing/random-string.h"

namespace icing {
namespace lib {

std::vector<std::string> GenerateUniqueTerms(int num_terms) {
  char before_a = 'a' - 1;
  std::string term(1, before_a);
  std::vector<std::string> terms;
  int current_char = 0;
  for (int permutation = 0; permutation < num_terms; ++permutation) {
    if (term[current_char] != 'z') {
      ++term[current_char];
    } else {
      if (current_char < term.length() - 1) {
        // The string currently looks something like this "zzzaa"
        // 1. Find the first char after this one that isn't
        current_char = term.find_first_not_of('z', current_char);
        if (current_char != std::string::npos) {
          // 2. Increment that character
          ++term[current_char];

          // 3. Set every character prior to current_char to 'a'
          term.replace(0, current_char, current_char, 'a');
        } else {
          // Every character in this string is a 'z'. We need to grow.
          term = std::string(term.length() + 1, 'a');
        }
      } else {
        term = std::string(term.length() + 1, 'a');
      }
      current_char = 0;
    }
    terms.push_back(term);
  }
  return terms;
}

}  // namespace lib
}  // namespace icing
