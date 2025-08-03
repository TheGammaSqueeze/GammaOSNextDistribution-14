// Copyright (C) 2021 Google LLC
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

#include <unistd.h>

#include <fstream>
#include <iostream>
#include <memory>
#include <ostream>
#include <random>
#include <sstream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <unordered_set>
#include <vector>

#include "testing/base/public/benchmark.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/persist.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/schema-builder.h"
#include "icing/schema/schema-store.h"
#include "icing/store/document-store.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"
#include "icing/util/clock.h"

// Run on a Linux workstation:
//    $ blaze build -c opt --dynamic_mode=off --copt=-gmlt
//    //icing/store:document-store_benchmark
//
//    $ blaze-bin/icing/store/document-store_benchmark
//    --benchmark_filter=all --benchmark_memory_usage
//
// Run on an Android device:
//    $ blaze build --copt="-DGOOGLE_COMMANDLINEFLAGS_FULL_API=1"
//    --config=android_arm64 -c opt --dynamic_mode=off --copt=-gmlt
//    //icing/store:document-store_benchmark
//
//    $ adb push blaze-bin/icing/store/document-store_benchmark
//    /data/local/tmp/
//
//    $ adb shell /data/local/tmp/document-store_benchmark
//    --benchmark_filter=all

namespace icing {
namespace lib {

namespace {

class DestructibleDirectory {
 public:
  explicit DestructibleDirectory(const Filesystem& filesystem,
                                 const std::string& dir)
      : filesystem_(filesystem), dir_(dir) {
    filesystem_.CreateDirectoryRecursively(dir_.c_str());
  }
  ~DestructibleDirectory() {
    filesystem_.DeleteDirectoryRecursively(dir_.c_str());
  }

 private:
  Filesystem filesystem_;
  std::string dir_;
};

DocumentProto CreateDocument(const std::string namespace_,
                             const std::string uri) {
  return DocumentBuilder()
      .SetKey(namespace_, uri)
      .SetSchema("email")
      .AddStringProperty("subject", "subject foo")
      .AddStringProperty("body", "body bar")
      .Build();
}

SchemaProto CreateSchema() {
  return SchemaBuilder()
      .AddType(SchemaTypeConfigBuilder()
                   .SetType("email")
                   .AddProperty(
                       PropertyConfigBuilder()
                           .SetName("subject")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
                   .AddProperty(
                       PropertyConfigBuilder()
                           .SetName("body")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL)))
      .Build();
}

std::unique_ptr<SchemaStore> CreateSchemaStore(Filesystem filesystem,
                                               const std::string directory,
                                               const Clock* clock) {
  const std::string schema_store_dir = directory + "/schema";
  filesystem.CreateDirectoryRecursively(schema_store_dir.data());
  std::unique_ptr<SchemaStore> schema_store =
      SchemaStore::Create(&filesystem, schema_store_dir, clock).ValueOrDie();

  auto set_schema_status = schema_store->SetSchema(
      CreateSchema(), /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false);
  if (!set_schema_status.ok()) {
    ICING_LOG(ERROR) << set_schema_status.status().error_message();
  }

  return schema_store;
}

libtextclassifier3::StatusOr<DocumentStore::CreateResult> CreateDocumentStore(
    const Filesystem* filesystem, const std::string& base_dir,
    const Clock* clock, const SchemaStore* schema_store) {
  return DocumentStore::Create(
      filesystem, base_dir, clock, schema_store,
      /*force_recovery_and_revalidate_documents=*/false,
      /*namespace_id_fingerprint=*/false, /*pre_mapping_fbv=*/false,
      /*use_persistent_hash_map=*/false,
      PortableFileBackedProtoLog<DocumentWrapper>::kDeflateCompressionLevel,
      /*initialize_stats=*/nullptr);
}

void BM_DoesDocumentExistBenchmark(benchmark::State& state) {
  Filesystem filesystem;
  Clock clock;

  std::string directory = GetTestTempDir() + "/icing";
  DestructibleDirectory ddir(filesystem, directory);

  std::string document_store_dir = directory + "/store";
  std::unique_ptr<SchemaStore> schema_store =
      CreateSchemaStore(filesystem, directory, &clock);

  filesystem.CreateDirectoryRecursively(document_store_dir.data());
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentStore::CreateResult create_result,
      CreateDocumentStore(&filesystem, document_store_dir, &clock,
                          schema_store.get()));
  std::unique_ptr<DocumentStore> document_store =
      std::move(create_result.document_store);

  int max_document_id = 300000;
  for (int i = 0; i < max_document_id; ++i) {
    // Put and delete a lot of documents to fill up our derived files with
    // stuff.
    ICING_ASSERT_OK(document_store->Put(
        CreateDocument("namespace", /*uri=*/std::to_string(i))));
    ICING_ASSERT_OK(document_store->Delete("namespace",
                                           /*uri=*/std::to_string(i),
                                           clock.GetSystemTimeMilliseconds()));
  }

  std::default_random_engine random;
  std::uniform_int_distribution<> dist(1, max_document_id);
  for (auto s : state) {
    // Check random document ids to see if they exist. Hopefully to simulate
    // page faulting in different sections of our mmapped derived files.
    int document_id = dist(random);
    benchmark::DoNotOptimize(document_store->GetAliveDocumentFilterData(
        document_id, clock.GetSystemTimeMilliseconds()));
  }
}
BENCHMARK(BM_DoesDocumentExistBenchmark);

void BM_Put(benchmark::State& state) {
  Filesystem filesystem;
  Clock clock;

  std::string directory = GetTestTempDir() + "/icing";
  DestructibleDirectory ddir(filesystem, directory);

  std::string document_store_dir = directory + "/store";
  std::unique_ptr<SchemaStore> schema_store =
      CreateSchemaStore(filesystem, directory, &clock);

  filesystem.CreateDirectoryRecursively(document_store_dir.data());
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentStore::CreateResult create_result,
      CreateDocumentStore(&filesystem, document_store_dir, &clock,
                          schema_store.get()));
  std::unique_ptr<DocumentStore> document_store =
      std::move(create_result.document_store);

  DocumentProto document = CreateDocument("namespace", "uri");

  for (auto s : state) {
    // It's ok that this is the same document over and over. We'll create a new
    // document_id for it and still insert the proto into the underlying log.
    benchmark::DoNotOptimize(document_store->Put(document));
  }
}
BENCHMARK(BM_Put);

void BM_GetSameDocument(benchmark::State& state) {
  Filesystem filesystem;
  Clock clock;

  std::string directory = GetTestTempDir() + "/icing";
  DestructibleDirectory ddir(filesystem, directory);

  std::string document_store_dir = directory + "/store";
  std::unique_ptr<SchemaStore> schema_store =
      CreateSchemaStore(filesystem, directory, &clock);

  filesystem.CreateDirectoryRecursively(document_store_dir.data());
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentStore::CreateResult create_result,
      CreateDocumentStore(&filesystem, document_store_dir, &clock,
                          schema_store.get()));
  std::unique_ptr<DocumentStore> document_store =
      std::move(create_result.document_store);

  ICING_ASSERT_OK(document_store->Put(CreateDocument("namespace", "uri")));

  for (auto s : state) {
    benchmark::DoNotOptimize(document_store->Get("namespace", "uri"));
  }
}
BENCHMARK(BM_GetSameDocument);

void BM_Delete(benchmark::State& state) {
  Filesystem filesystem;
  Clock clock;

  std::string directory = GetTestTempDir() + "/icing";
  DestructibleDirectory ddir(filesystem, directory);

  std::string document_store_dir = directory + "/store";
  std::unique_ptr<SchemaStore> schema_store =
      CreateSchemaStore(filesystem, directory, &clock);

  filesystem.CreateDirectoryRecursively(document_store_dir.data());
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentStore::CreateResult create_result,
      CreateDocumentStore(&filesystem, document_store_dir, &clock,
                          schema_store.get()));
  std::unique_ptr<DocumentStore> document_store =
      std::move(create_result.document_store);

  DocumentProto document = CreateDocument("namespace", "uri");

  for (auto s : state) {
    state.PauseTiming();
    ICING_ASSERT_OK(document_store->Put(document));
    state.ResumeTiming();

    benchmark::DoNotOptimize(document_store->Delete(
        "namespace", "uri", clock.GetSystemTimeMilliseconds()));
  }
}
BENCHMARK(BM_Delete);

void BM_Create(benchmark::State& state) {
  Filesystem filesystem;
  Clock clock;

  std::string directory = GetTestTempDir() + "/icing";
  std::string document_store_dir = directory + "/store";

  std::unique_ptr<SchemaStore> schema_store =
      CreateSchemaStore(filesystem, directory, &clock);

  // Create an initial document store and put some data in.
  {
    DestructibleDirectory ddir(filesystem, directory);

    filesystem.CreateDirectoryRecursively(document_store_dir.data());
    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentStore::CreateResult create_result,
        CreateDocumentStore(&filesystem, document_store_dir, &clock,
                            schema_store.get()));
    std::unique_ptr<DocumentStore> document_store =
        std::move(create_result.document_store);

    DocumentProto document = CreateDocument("namespace", "uri");
    ICING_ASSERT_OK(document_store->Put(document));
    ICING_ASSERT_OK(document_store->PersistToDisk(PersistType::FULL));
  }

  // Recreating it with some content to checksum over.
  DestructibleDirectory ddir(filesystem, directory);

  filesystem.CreateDirectoryRecursively(document_store_dir.data());

  for (auto s : state) {
    benchmark::DoNotOptimize(CreateDocumentStore(
        &filesystem, document_store_dir, &clock, schema_store.get()));
  }
}
BENCHMARK(BM_Create);

void BM_ComputeChecksum(benchmark::State& state) {
  Filesystem filesystem;
  Clock clock;

  std::string directory = GetTestTempDir() + "/icing";
  DestructibleDirectory ddir(filesystem, directory);

  std::string document_store_dir = directory + "/store";
  std::unique_ptr<SchemaStore> schema_store =
      CreateSchemaStore(filesystem, directory, &clock);

  filesystem.CreateDirectoryRecursively(document_store_dir.data());
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentStore::CreateResult create_result,
      CreateDocumentStore(&filesystem, document_store_dir, &clock,
                          schema_store.get()));
  std::unique_ptr<DocumentStore> document_store =
      std::move(create_result.document_store);

  DocumentProto document = CreateDocument("namespace", "uri");
  ICING_ASSERT_OK(document_store->Put(document));
  ICING_ASSERT_OK(document_store->PersistToDisk(PersistType::LITE));

  for (auto s : state) {
    benchmark::DoNotOptimize(document_store->ComputeChecksum());
  }
}
BENCHMARK(BM_ComputeChecksum);

}  // namespace

}  // namespace lib
}  // namespace icing
