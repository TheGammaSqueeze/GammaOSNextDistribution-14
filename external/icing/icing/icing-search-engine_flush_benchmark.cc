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
#include "icing/icing-search-engine.h"
#include "icing/proto/initialize.pb.h"
#include "icing/proto/persist.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/status.pb.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/document-generator.h"
#include "icing/testing/random-string.h"
#include "icing/testing/schema-generator.h"
#include "icing/testing/tmp-directory.h"

// Run on a Linux workstation:
//    $ blaze build -c opt --dynamic_mode=off --copt=-gmlt
//    //icing:icing-search-engine_flush_benchmark
//
//    $ blaze-bin/icing/icing-search-engine_flush_benchmark
//    --benchmark_filter=all --benchmark_memory_usage
//
// Run on an Android device:
//    $ blaze build --copt="-DGOOGLE_COMMANDLINEFLAGS_FULL_API=1"
//    --config=android_arm64 -c opt --dynamic_mode=off --copt=-gmlt
//    //icing:icing-search-engine_flush_benchmark
//
//    $ adb push blaze-bin/icing/icing-search-engine_flush_benchmark
//    /data/local/tmp/
//
//    $ adb shell /data/local/tmp/icing-search-engine_flush_benchmark
//    --benchmark_filter=all

namespace icing {
namespace lib {

namespace {

// Assume that there will be roughly 10 packages, each using 3 of its own types.
constexpr int kAvgNumNamespaces = 10;
constexpr int kAvgNumTypes = 3;

// ASSUME: Types will have at most ten properties. Types will be created with
// [1, 10] properties.
constexpr int kMaxNumProperties = 10;

// Based on logs from Icing GMSCore.
constexpr int kAvgDocumentSize = 300;

// ASSUME: ~75% of the document's size comes from its content.
constexpr float kContentSizePct = 0.7;

// Average length of word in English is 4.7 characters.
constexpr int kAvgTokenLen = 5;
// Made up value. This results in a fairly reasonable language - the majority of
// generated words are 3-9 characters, ~3% of words are >=20 chars, and the
// longest ones are 27 chars, (roughly consistent with the longest,
// non-contrived English words
// https://en.wikipedia.org/wiki/Longest_word_in_English)
constexpr int kTokenStdDev = 7;
constexpr int kLanguageSize = 1000;

// The number of documents to index.
constexpr int kNumDocuments = 1024;

std::vector<std::string> CreateNamespaces(int num_namespaces) {
  std::vector<std::string> namespaces;
  while (--num_namespaces >= 0) {
    namespaces.push_back("comgooglepackage" + std::to_string(num_namespaces));
  }
  return namespaces;
}

// Creates a vector containing num_words randomly-generated words for use by
// documents.
template <typename Rand>
std::vector<std::string> CreateLanguage(int num_words, Rand* r) {
  std::vector<std::string> language;
  std::normal_distribution<> norm_dist(kAvgTokenLen, kTokenStdDev);
  while (--num_words >= 0) {
    int word_length = 0;
    while (word_length < 1) {
      word_length = std::round(norm_dist(*r));
    }
    language.push_back(RandomString(kAlNumAlphabet, word_length, r));
  }
  return language;
}

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

void BM_FlushBenchmark(benchmark::State& state) {
  PersistType::Code persist_type =
      (state.range(0)) ? PersistType::LITE : PersistType::FULL;
  int num_documents_per_persist = state.range(1);

  // Initialize the filesystem
  std::string test_dir = GetTestTempDir() + "/icing/benchmark/flush";
  Filesystem filesystem;
  DestructibleDirectory ddir(filesystem, test_dir);

  // Create the schema.
  std::default_random_engine random;
  int num_types = kAvgNumNamespaces * kAvgNumTypes;
  ExactStringPropertyGenerator property_generator;
  RandomSchemaGenerator<std::default_random_engine,
                        ExactStringPropertyGenerator>
      schema_generator(&random, &property_generator);
  SchemaProto schema =
      schema_generator.GenerateSchema(num_types, kMaxNumProperties);
  EvenDistributionTypeSelector type_selector(schema);

  std::vector<std::string> namespaces = CreateNamespaces(kAvgNumNamespaces);
  EvenDistributionNamespaceSelector namespace_selector(namespaces);

  std::vector<std::string> language = CreateLanguage(kLanguageSize, &random);
  UniformDistributionLanguageTokenGenerator<std::default_random_engine>
      token_generator(language, &random);

  DocumentGenerator<
      EvenDistributionNamespaceSelector, EvenDistributionTypeSelector,
      UniformDistributionLanguageTokenGenerator<std::default_random_engine>>
      generator(&namespace_selector, &type_selector, &token_generator,
                kAvgDocumentSize * kContentSizePct);

  IcingSearchEngineOptions options;
  options.set_base_dir(test_dir);
  std::unique_ptr<IcingSearchEngine> icing =
      std::make_unique<IcingSearchEngine>(options);

  ASSERT_THAT(icing->Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing->SetSchema(schema).status(), ProtoIsOk());
  for (auto s : state) {
    for (int i = 0; i < kNumDocuments; ++i) {
      icing->Put(generator.generateDoc());

      if (i % num_documents_per_persist == num_documents_per_persist - 1) {
        icing->PersistToDisk(persist_type);
      }
    }
  }
}
BENCHMARK(BM_FlushBenchmark)
    // First argument: lite_flush,
    // Second argument: num_document_per_lite_flush
    ->ArgPair(true, 1)
    ->ArgPair(false, 1)
    ->ArgPair(true, 32)
    ->ArgPair(false, 32)
    ->ArgPair(true, 1024)
    ->ArgPair(false, 1024);

}  // namespace

}  // namespace lib
}  // namespace icing
