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

#ifndef ICING_INDEX_TERM_METADATA_H_
#define ICING_INDEX_TERM_METADATA_H_

#include <string>

namespace icing {
namespace lib {

// A POD struct storing metadata of a term.
struct TermMetadata {
  TermMetadata(std::string content_in, int score_in)
      : content(std::move(content_in)), score(score_in) {}

  // Content of the term.
  std::string content;

  // The score of the term.
  // It will either be:
  //- HIT_COUNT - number of document+section hits associated with the term
  //- TERM_FREQUENCY - the number of times that the term appears in documents
  int score;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_TERM_METADATA_H_
