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

#include <cstdint>
#include <memory>
#include <string_view>

#include "icing/scoring/advanced_scoring/advanced-scorer.h"
#include "icing/testing/fake-clock.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FakeClock fake_clock;
  Filesystem filesystem;
  const std::string test_dir = GetTestTempDir() + "/icing";
  const std::string doc_store_dir = test_dir + "/doc_store";
  const std::string schema_store_dir = test_dir + "/schema_store";
  filesystem.DeleteDirectoryRecursively(test_dir.c_str());
  filesystem.CreateDirectoryRecursively(doc_store_dir.c_str());
  filesystem.CreateDirectoryRecursively(schema_store_dir.c_str());

  std::unique_ptr<SchemaStore> schema_store =
      SchemaStore::Create(&filesystem, schema_store_dir, &fake_clock)
          .ValueOrDie();
  std::unique_ptr<DocumentStore> document_store =
      DocumentStore::Create(
          &filesystem, doc_store_dir, &fake_clock, schema_store.get(),
          /*force_recovery_and_revalidate_documents=*/false,
          /*namespace_id_fingerprint=*/false, /*pre_mapping_fbv=*/false,
          /*use_persistent_hash_map=*/false,
          PortableFileBackedProtoLog<DocumentWrapper>::kDeflateCompressionLevel,
          /*initialize_stats=*/nullptr)
          .ValueOrDie()
          .document_store;

  std::string_view text(reinterpret_cast<const char*>(data), size);
  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(
      ScoringSpecProto::RankingStrategy::ADVANCED_SCORING_EXPRESSION);
  scoring_spec.set_advanced_scoring_expression(text);

  AdvancedScorer::Create(scoring_spec,
                         /*default_score=*/10, document_store.get(),
                         schema_store.get(),
                         fake_clock.GetSystemTimeMilliseconds());

  // Not able to test the GetScore method of AdvancedScorer, since it will only
  // be available after AdvancedScorer is successfully created. However, the
  // text provided by the fuzz test is very random, which means that in most
  // cases, there will be syntax errors or type errors that cause
  // AdvancedScorer::Create to fail.
  return 0;
}

}  // namespace lib
}  // namespace icing
