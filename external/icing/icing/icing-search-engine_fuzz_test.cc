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

#include <cstddef>
#include <cstdint>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/document-builder.h"
#include "icing/icing-search-engine.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/initialize.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/scoring.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/schema-builder.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/test-data.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {
namespace {

IcingSearchEngineOptions Setup() {
  IcingSearchEngineOptions icing_options;
  icing_options.set_base_dir(GetTestTempDir() + "/icing");
  return icing_options;
}

DocumentProto MakeDocument(const uint8_t* data, size_t size) {
  // TODO (sidchhabra): Added more optimized fuzzing techniques.
  DocumentProto document;
  std::string string_prop(reinterpret_cast<const char*>(data), size);
  return DocumentBuilder()
      .SetKey("namespace", "uri1")
      .SetSchema("Message")
      .AddStringProperty("body", string_prop)
      .Build();
}

SearchSpecProto SetSearchSpec(const uint8_t* data, size_t size) {
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  // TODO (sidchhabra): Added more optimized fuzzing techniques.
  std::string query_string(reinterpret_cast<const char*>(data), size);
  search_spec.set_query(query_string);
  return search_spec;
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  // Initialize
  IcingSearchEngineOptions icing_options = Setup();
  std::string icu_data_file_path = GetTestFilePath("icing/icu.dat");
  if (!icu_data_file_helper::SetUpICUDataFile(icu_data_file_path).ok()) {
    return 1;
  }
  IcingSearchEngine icing(icing_options);
  const Filesystem filesystem_;
  // TODO (b/145758378): Deleting directory should not be required.
  filesystem_.DeleteDirectoryRecursively(icing_options.base_dir().c_str());
  icing.Initialize();

  SchemaProto schema_proto =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message").AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();
  icing.SetSchema(schema_proto);

  // Index
  DocumentProto document = MakeDocument(data, size);
  icing.Put(document);

  // Query
  SearchSpecProto search_spec = SetSearchSpec(data, size);
  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);
  ResultSpecProto result_spec;
  libtextclassifier3::StatusOr<SearchResultProto> result =
      icing.Search(search_spec, scoring_spec, result_spec);
  return 0;
}

}  // namespace
}  // namespace lib
}  // namespace icing
