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
#include <random>
#include <utility>

#include "gtest/gtest.h"
#include "icing/monkey_test/icing-monkey-test-runner.h"
#include "icing/monkey_test/monkey-test-util.h"
#include "icing/portable/platform.h"
#include "icing/proto/debug.pb.h"
#include "icing/schema/section.h"
#include "icing/util/logging.h"

namespace icing {
namespace lib {

TEST(IcingSearchEngineMonkeyTest, MonkeyTest) {
  IcingMonkeyTestRunnerConfiguration config(
      /*seed=*/std::random_device()(),
      /*num_types=*/30,
      /*num_namespaces=*/100,
      /*num_uris=*/1000,
      /*index_merge_size=*/1024 * 1024);
  config.possible_num_properties = {0,
                                    1,
                                    2,
                                    4,
                                    8,
                                    16,
                                    kTotalNumSections / 2,
                                    kTotalNumSections,
                                    kTotalNumSections + 1,
                                    kTotalNumSections * 2};
  config.possible_num_tokens_ = {0, 1, 4, 16, 64, 256};
  config.monkey_api_schedules = {
      {&IcingMonkeyTestRunner::DoPut, 500},
      {&IcingMonkeyTestRunner::DoSearch, 200},
      {&IcingMonkeyTestRunner::DoGet, 70},
      {&IcingMonkeyTestRunner::DoGetAllNamespaces, 50},
      {&IcingMonkeyTestRunner::DoDelete, 50},
      {&IcingMonkeyTestRunner::DoDeleteByNamespace, 50},
      {&IcingMonkeyTestRunner::DoDeleteBySchemaType, 45},
      {&IcingMonkeyTestRunner::DoDeleteByQuery, 20},
      {&IcingMonkeyTestRunner::DoOptimize, 5},
      {&IcingMonkeyTestRunner::DoUpdateSchema, 5},
      {&IcingMonkeyTestRunner::ReloadFromDisk, 5}};
  uint32_t num_iterations = IsAndroidArm() ? 1000 : 5000;
  IcingMonkeyTestRunner runner(std::move(config));
  ASSERT_NO_FATAL_FAILURE(runner.Initialize());
  ASSERT_NO_FATAL_FAILURE(runner.Run(num_iterations));
}

TEST(DISABLED_IcingSearchEngineMonkeyTest, MonkeyManyDocTest) {
  IcingMonkeyTestRunnerConfiguration config(
      /*seed=*/std::random_device()(),
      /*num_types=*/30,
      /*num_namespaces=*/200,
      /*num_uris=*/100000,
      /*index_merge_size=*/1024 * 1024);

  // Due to the large amount of documents, we need to make each document smaller
  // to finish the test.
  config.possible_num_properties = {0, 1, 2};
  config.possible_num_tokens_ = {0, 1, 4};

  // No deletion is performed to preserve a large number of documents.
  config.monkey_api_schedules = {
      {&IcingMonkeyTestRunner::DoPut, 500},
      {&IcingMonkeyTestRunner::DoSearch, 200},
      {&IcingMonkeyTestRunner::DoGet, 70},
      {&IcingMonkeyTestRunner::DoGetAllNamespaces, 50},
      {&IcingMonkeyTestRunner::DoOptimize, 5},
      {&IcingMonkeyTestRunner::ReloadFromDisk, 5}};
  IcingMonkeyTestRunner runner(std::move(config));
  ASSERT_NO_FATAL_FAILURE(runner.Initialize());
  // Pre-fill with 4 million documents
  SetLoggingLevel(LogSeverity::WARNING);
  for (int i = 0; i < 4000000; i++) {
    ASSERT_NO_FATAL_FAILURE(runner.DoPut());
  }
  SetLoggingLevel(LogSeverity::INFO);
  ASSERT_NO_FATAL_FAILURE(runner.Run(1000));
}

}  // namespace lib
}  // namespace icing
