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

#include "icing/tokenization/tokenizer-factory.h"

#include <memory>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/proto/schema.pb.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/tokenization/plain-tokenizer.h"
#include "icing/tokenization/raw-query-tokenizer.h"
#include "icing/tokenization/rfc822-tokenizer.h"
#include "icing/tokenization/tokenizer.h"

#ifdef ENABLE_URL_TOKENIZER
#include "icing/tokenization/url-tokenizer.h"
#endif  // ENABLE_URL_TOKENIZER

#include "icing/tokenization/verbatim-tokenizer.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace tokenizer_factory {

libtextclassifier3::StatusOr<std::unique_ptr<Tokenizer>>
CreateIndexingTokenizer(StringIndexingConfig::TokenizerType::Code type,
                        const LanguageSegmenter* lang_segmenter) {
  ICING_RETURN_ERROR_IF_NULL(lang_segmenter);

  switch (type) {
    case StringIndexingConfig::TokenizerType::PLAIN:
      return std::make_unique<PlainTokenizer>(lang_segmenter);
    case StringIndexingConfig::TokenizerType::VERBATIM:
      return std::make_unique<VerbatimTokenizer>();
    case StringIndexingConfig::TokenizerType::RFC822:
      return std::make_unique<Rfc822Tokenizer>();
// TODO (b/246964044): remove ifdef guard when url-tokenizer is ready for export
// to Android.
#ifdef ENABLE_URL_TOKENIZER
    case StringIndexingConfig::TokenizerType::URL:
      return std::make_unique<UrlTokenizer>();
#endif  // ENABLE_URL_TOKENIZER
    case StringIndexingConfig::TokenizerType::NONE:
      [[fallthrough]];
    default:
      // This should never happen.
      return absl_ports::InvalidArgumentError(
          "Invalid tokenizer type for an indexed section");
  }
}

libtextclassifier3::StatusOr<std::unique_ptr<Tokenizer>> CreateQueryTokenizer(
    QueryTokenizerType query_tokenizer_type,
    const LanguageSegmenter* lang_segmenter) {
  ICING_RETURN_ERROR_IF_NULL(lang_segmenter);

  switch (query_tokenizer_type) {
    case RAW_QUERY:
      return std::make_unique<RawQueryTokenizer>(lang_segmenter);
    default:
      // This should never happen.
      return absl_ports::InvalidArgumentError(
          "Invalid tokenizer type for query");
  }
}

}  // namespace tokenizer_factory

}  // namespace lib
}  // namespace icing
