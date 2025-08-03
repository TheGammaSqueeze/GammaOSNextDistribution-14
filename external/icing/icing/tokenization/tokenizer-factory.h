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

#ifndef ICING_TOKENIZATION_TOKENIZER_FACTORY_H_
#define ICING_TOKENIZATION_TOKENIZER_FACTORY_H_

#include <memory>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/proto/schema.pb.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/tokenization/tokenizer.h"

namespace icing {
namespace lib {

namespace tokenizer_factory {

// Factory function to create an indexing Tokenizer which does not take
// ownership of any input components, and all pointers must refer to valid
// objects that outlive the created Tokenizer instance.
//
// Returns:
//   A tokenizer on success
//   FAILED_PRECONDITION on any null pointer input
//   INVALID_ARGUMENT if tokenizer type is invalid
libtextclassifier3::StatusOr<std::unique_ptr<Tokenizer>>
CreateIndexingTokenizer(StringIndexingConfig::TokenizerType::Code type,
                        const LanguageSegmenter* lang_segmenter);

// All the supported query tokenizer types
enum QueryTokenizerType { RAW_QUERY = 0 };

// Factory function to create a query Tokenizer which does not take ownership of
// any input components, and all pointers must refer to valid objects that
// outlive the created Tokenizer instance.
//
// Returns:
//   A tokenizer on success
//   FAILED_PRECONDITION on any null pointer input
//   INVALID_ARGUMENT if tokenizer type is invalid
libtextclassifier3::StatusOr<std::unique_ptr<Tokenizer>> CreateQueryTokenizer(
    QueryTokenizerType query_tokenizer_type,
    const LanguageSegmenter* lang_segmenter);

}  // namespace tokenizer_factory

}  // namespace lib
}  // namespace icing

#endif  // ICING_TOKENIZATION_TOKENIZER_FACTORY_H_
