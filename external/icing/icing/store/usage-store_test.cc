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

#include "icing/store/usage-store.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {
using ::testing::Eq;
using ::testing::Gt;
using ::testing::Not;

class UsageStoreTest : public testing::Test {
 protected:
  UsageStoreTest() : test_dir_(GetTestTempDir() + "/usage-store-test") {}

  void SetUp() override {
    filesystem_.CreateDirectoryRecursively(test_dir_.c_str());
  }

  void TearDown() override {
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
  }

  const Filesystem filesystem_;
  const std::string test_dir_;
};

UsageReport CreateUsageReport(std::string name_space, std::string uri,
                              int64_t timestamp_ms,
                              UsageReport::UsageType usage_type) {
  UsageReport usage_report;
  usage_report.set_document_namespace(name_space);
  usage_report.set_document_uri(uri);
  usage_report.set_usage_timestamp_ms(timestamp_ms);
  usage_report.set_usage_type(usage_type);
  return usage_report;
}

UsageStore::UsageScores CreateUsageScores(uint32_t type1_timestamp,
                                          uint32_t type2_timestamp,
                                          uint32_t type3_timestamp,
                                          int type1_count, int type2_count,
                                          int type3_count) {
  UsageStore::UsageScores scores;
  scores.usage_type1_last_used_timestamp_s = type1_timestamp;
  scores.usage_type2_last_used_timestamp_s = type2_timestamp;
  scores.usage_type3_last_used_timestamp_s = type3_timestamp;
  scores.usage_type1_count = type1_count;
  scores.usage_type2_count = type2_count;
  scores.usage_type3_count = type3_count;

  return scores;
}

TEST_F(UsageStoreTest, CreationShouldSucceed) {
  EXPECT_THAT(UsageStore::Create(&filesystem_, test_dir_), IsOk());
}

TEST_F(UsageStoreTest, CreationShouldFailOnNullPointer) {
  EXPECT_THAT(UsageStore::Create(nullptr, test_dir_),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
}

TEST_F(UsageStoreTest, UsageScoresShouldBeComparable) {
  UsageStore::UsageScores scores1;
  UsageStore::UsageScores scores2;
  EXPECT_THAT(scores1, Eq(scores2));

  // operator== should compare usage_type1_last_used_timestamp_s.
  ++scores1.usage_type1_last_used_timestamp_s;
  EXPECT_THAT(scores1, Not(Eq(scores2)));
  ++scores2.usage_type1_last_used_timestamp_s;
  EXPECT_THAT(scores1, Eq(scores2));

  // operator== should compare usage_type2_last_used_timestamp_s.
  ++scores1.usage_type2_last_used_timestamp_s;
  EXPECT_THAT(scores1, Not(Eq(scores2)));
  ++scores2.usage_type2_last_used_timestamp_s;
  EXPECT_THAT(scores1, Eq(scores2));

  // operator== should compare usage_type3_last_used_timestamp_s.
  ++scores1.usage_type3_last_used_timestamp_s;
  EXPECT_THAT(scores1, Not(Eq(scores2)));
  ++scores2.usage_type3_last_used_timestamp_s;
  EXPECT_THAT(scores1, Eq(scores2));

  // operator== should compare usage_type1_count.
  ++scores1.usage_type1_count;
  EXPECT_THAT(scores1, Not(Eq(scores2)));
  ++scores2.usage_type1_count;
  EXPECT_THAT(scores1, Eq(scores2));

  // operator== should compare usage_type2_count.
  ++scores1.usage_type2_count;
  EXPECT_THAT(scores1, Not(Eq(scores2)));
  ++scores2.usage_type2_count;
  EXPECT_THAT(scores1, Eq(scores2));

  // operator== should compare usage_type3_count.
  ++scores1.usage_type3_count;
  EXPECT_THAT(scores1, Not(Eq(scores2)));
  ++scores2.usage_type3_count;
  EXPECT_THAT(scores1, Eq(scores2));
}

TEST_F(UsageStoreTest, InvalidDocumentIdShouldReturnError) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));

  DocumentId invalid_document_id = -1;

  EXPECT_THAT(usage_store->AddUsageReport(UsageReport(), invalid_document_id),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  EXPECT_THAT(usage_store->DeleteUsageScores(invalid_document_id),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  EXPECT_THAT(usage_store->GetUsageScores(invalid_document_id),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  EXPECT_THAT(usage_store->SetUsageScores(invalid_document_id,
                                          UsageStore::UsageScores()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(UsageStoreTest, AddUsageReportShouldUpdateLastUsedTimestamp) {
  // Create 3 reports with different timestamps.
  UsageReport usage_report_time1 = CreateUsageReport(
      "namespace", "uri", /*timestamp_ms=*/1000, UsageReport::USAGE_TYPE1);
  UsageReport usage_report_time5 = CreateUsageReport(
      "namespace", "uri", /*timestamp_ms=*/5000, UsageReport::USAGE_TYPE1);
  UsageReport usage_report_time10 = CreateUsageReport(
      "namespace", "uri", /*timestamp_ms=*/10000, UsageReport::USAGE_TYPE1);

  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));

  // Report a usage with timestamp 5.
  ICING_ASSERT_OK(
      usage_store->AddUsageReport(usage_report_time5, /*document_id=*/1));
  UsageStore::UsageScores expected_scores = CreateUsageScores(
      /*type1_timestamp=*/5, /*type2_timestamp=*/0, /*type3_timestamp=*/0,
      /*type1_count=*/1, /*type2_count=*/0, /*type3_count=*/0);
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
              IsOkAndHolds(expected_scores));

  // Report a usage with timestamp 1. The timestamp won't be updated.
  ICING_ASSERT_OK(
      usage_store->AddUsageReport(usage_report_time1, /*document_id=*/1));
  ++expected_scores.usage_type1_count;
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
              IsOkAndHolds(expected_scores));

  // Report a usage with timestamp 10. The timestamp should be updated.
  ICING_ASSERT_OK(
      usage_store->AddUsageReport(usage_report_time10, /*document_id=*/1));
  expected_scores.usage_type1_last_used_timestamp_s = 10;
  ++expected_scores.usage_type1_count;
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
              IsOkAndHolds(expected_scores));
}

TEST_F(UsageStoreTest, AddUsageReportShouldUpdateCounts) {
  // Create 3 reports with different usage types.
  UsageReport usage_report_type1 = CreateUsageReport(
      "namespace", "uri", /*timestamp_ms=*/0, UsageReport::USAGE_TYPE1);
  UsageReport usage_report_type2 = CreateUsageReport(
      "namespace", "uri", /*timestamp_ms=*/0, UsageReport::USAGE_TYPE2);
  UsageReport usage_report_type3 = CreateUsageReport(
      "namespace", "uri", /*timestamp_ms=*/0, UsageReport::USAGE_TYPE3);

  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));

  // Report a usage with type 1.
  ICING_ASSERT_OK(
      usage_store->AddUsageReport(usage_report_type1, /*document_id=*/1));
  UsageStore::UsageScores expected_scores = CreateUsageScores(
      /*type1_timestamp=*/0, /*type2_timestamp=*/0, /*type3_timestamp=*/0,
      /*type1_count=*/1, /*type2_count=*/0, /*type3_count=*/0);
  ;
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
              IsOkAndHolds(expected_scores));
  // Report another usage with type 1.
  ICING_ASSERT_OK(
      usage_store->AddUsageReport(usage_report_type1, /*document_id=*/1));
  ++expected_scores.usage_type1_count;
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
              IsOkAndHolds(expected_scores));

  // Report a usage with type 2.
  ICING_ASSERT_OK(
      usage_store->AddUsageReport(usage_report_type2, /*document_id=*/1));
  ++expected_scores.usage_type2_count;
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
              IsOkAndHolds(expected_scores));
  // Report another usage with type 2.
  ICING_ASSERT_OK(
      usage_store->AddUsageReport(usage_report_type2, /*document_id=*/1));
  ++expected_scores.usage_type2_count;
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
              IsOkAndHolds(expected_scores));

  // Report a usage with type 3.
  ICING_ASSERT_OK(
      usage_store->AddUsageReport(usage_report_type3, /*document_id=*/1));
  ++expected_scores.usage_type3_count;
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
              IsOkAndHolds(expected_scores));
  // Report another usage with type 3.
  ICING_ASSERT_OK(
      usage_store->AddUsageReport(usage_report_type3, /*document_id=*/1));
  ++expected_scores.usage_type3_count;
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
              IsOkAndHolds(expected_scores));
}

TEST_F(UsageStoreTest, GetNonExistingDocumentShouldReturnDefaultScores) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));

  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
              IsOkAndHolds(UsageStore::UsageScores()));
}

TEST_F(UsageStoreTest, SetAndGetUsageScores) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));

  // Create usage scores with some random numbers.
  UsageStore::UsageScores scores = CreateUsageScores(
      /*type1_timestamp=*/7, /*type2_timestamp=*/9, /*type3_timestamp=*/1,
      /*type1_count=*/3, /*type2_count=*/4, /*type3_count=*/9);

  // Verify that set and get results are consistent.
  ICING_EXPECT_OK(usage_store->SetUsageScores(/*document_id=*/1, scores));
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
              IsOkAndHolds(scores));
}

TEST_F(UsageStoreTest, ImplicitlyInitializedScoresShouldBeZero) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));

  // Explicitly set scores for document 2.
  ICING_ASSERT_OK(usage_store->SetUsageScores(/*document_id=*/2,
                                              UsageStore::UsageScores()));

  // Now the scores of document 1 have been implicitly initialized. The scores
  // should all be 0.
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
              IsOkAndHolds(UsageStore::UsageScores()));
}

TEST_F(UsageStoreTest, DeleteUsageScores) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));

  // Create usage scores with some random numbers.
  UsageStore::UsageScores scores = CreateUsageScores(
      /*type1_timestamp=*/7, /*type2_timestamp=*/9, /*type3_timestamp=*/1,
      /*type1_count=*/3, /*type2_count=*/4, /*type3_count=*/9);
  ;
  ICING_EXPECT_OK(usage_store->SetUsageScores(/*document_id=*/1, scores));

  // Delete the usage scores of document 1, all the scores of document 1 should
  // be 0.
  ICING_EXPECT_OK(usage_store->DeleteUsageScores(/*document_id=*/1));
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
              IsOkAndHolds(UsageStore::UsageScores()));
}

TEST_F(UsageStoreTest, CloneUsageScores) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));

  // Create usage scores with some random numbers and assign them to document 1.
  UsageStore::UsageScores scores_a = CreateUsageScores(
      /*type1_timestamp=*/7, /*type2_timestamp=*/9, /*type3_timestamp=*/1,
      /*type1_count=*/3, /*type2_count=*/4, /*type3_count=*/9);
  ;
  ICING_ASSERT_OK(usage_store->SetUsageScores(/*document_id=*/1, scores_a));

  // Create another set of usage scores with some random numbers and assign them
  // to document 2.
  UsageStore::UsageScores scores_b = CreateUsageScores(
      /*type1_timestamp=*/111, /*type2_timestamp=*/666, /*type3_timestamp=*/333,
      /*type1_count=*/50, /*type2_count=*/30, /*type3_count=*/100);
  ICING_ASSERT_OK(usage_store->SetUsageScores(/*document_id=*/2, scores_b));

  // Clone scores from document 1 to document 3.
  EXPECT_THAT(usage_store->CloneUsageScores(/*from_document_id=*/1,
                                            /*to_document_id=*/3),
              IsOk());

  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/3),
              IsOkAndHolds(scores_a));

  // Clone scores from document 2 to document 3.
  EXPECT_THAT(usage_store->CloneUsageScores(/*from_document_id=*/2,
                                            /*to_document_id=*/3),
              IsOk());
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/3),
              IsOkAndHolds(scores_b));

  // Clone scores from document 4 to document 3, scores should be set to
  // default.
  EXPECT_THAT(usage_store->CloneUsageScores(/*from_document_id=*/4,
                                            /*to_document_id=*/3),
              IsOk());
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/3),
              IsOkAndHolds(UsageStore::UsageScores()));
}

TEST_F(UsageStoreTest, PersistToDisk) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));

  // Create usage scores with some random numbers.
  UsageStore::UsageScores scores = CreateUsageScores(
      /*type1_timestamp=*/7, /*type2_timestamp=*/9, /*type3_timestamp=*/1,
      /*type1_count=*/3, /*type2_count=*/4, /*type3_count=*/9);
  ICING_ASSERT_OK(usage_store->SetUsageScores(/*document_id=*/1, scores));

  EXPECT_THAT(usage_store->PersistToDisk(), IsOk());
}

TEST_F(UsageStoreTest, ComputeChecksum) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));
  ICING_ASSERT_OK_AND_ASSIGN(Crc32 checksum1, usage_store->ComputeChecksum());

  // Create usage scores with some random numbers.
  UsageStore::UsageScores scores = CreateUsageScores(
      /*type1_timestamp=*/7, /*type2_timestamp=*/9, /*type3_timestamp=*/1,
      /*type1_count=*/3, /*type2_count=*/4, /*type3_count=*/9);
  ICING_ASSERT_OK(usage_store->SetUsageScores(/*document_id=*/1, scores));
  ICING_ASSERT_OK_AND_ASSIGN(Crc32 checksum2, usage_store->ComputeChecksum());

  ICING_ASSERT_OK(usage_store->SetUsageScores(/*document_id=*/2, scores));
  ICING_ASSERT_OK_AND_ASSIGN(Crc32 checksum3, usage_store->ComputeChecksum());

  EXPECT_THAT(checksum1, Not(Eq(checksum2)));
  EXPECT_THAT(checksum1, Not(Eq(checksum3)));
  EXPECT_THAT(checksum2, Not(Eq(checksum3)));

  // Without changing the store, checksum should be the same.
  ICING_ASSERT_OK_AND_ASSIGN(Crc32 checksum4, usage_store->ComputeChecksum());
  EXPECT_THAT(checksum3, Eq(checksum4));
}

TEST_F(UsageStoreTest, TruncateTo) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));

  // Create usage scores with some random numbers and set scores for document 0,
  // 1, 2.
  UsageStore::UsageScores scores = CreateUsageScores(
      /*type1_timestamp=*/7, /*type2_timestamp=*/9, /*type3_timestamp=*/1,
      /*type1_count=*/3, /*type2_count=*/4, /*type3_count=*/9);
  ICING_ASSERT_OK(usage_store->SetUsageScores(/*document_id=*/0, scores));
  ICING_ASSERT_OK(usage_store->SetUsageScores(/*document_id=*/1, scores));
  ICING_ASSERT_OK(usage_store->SetUsageScores(/*document_id=*/2, scores));

  // Truncate number of documents to 2, scores of document 2 should be gone.
  EXPECT_THAT(usage_store->TruncateTo(/*num_documents=*/2), IsOk());
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/0),
              IsOkAndHolds(scores));
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
              IsOkAndHolds(scores));
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/2),
              IsOkAndHolds(UsageStore::UsageScores()));
}

TEST_F(UsageStoreTest, TruncateToALargeNumberShouldDoNothing) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));

  // Create usage scores with some random numbers and set scores for document
  // 0, 1.
  UsageStore::UsageScores scores = CreateUsageScores(
      /*type1_timestamp=*/7, /*type2_timestamp=*/9, /*type3_timestamp=*/1,
      /*type1_count=*/3, /*type2_count=*/4, /*type3_count=*/9);
  ICING_ASSERT_OK(usage_store->SetUsageScores(/*document_id=*/0, scores));
  ICING_ASSERT_OK(usage_store->SetUsageScores(/*document_id=*/1, scores));

  ASSERT_THAT(usage_store->GetUsageScores(/*document_id=*/0),
              IsOkAndHolds(scores));
  ASSERT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
              IsOkAndHolds(scores));
  ASSERT_THAT(usage_store->GetUsageScores(/*document_id=*/2),
              IsOkAndHolds(UsageStore::UsageScores()));

  // Truncate to a number that is greater than the number of documents. Scores
  // should be the same.
  EXPECT_THAT(usage_store->TruncateTo(1000), IsOk());

  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/0),
              IsOkAndHolds(scores));
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
              IsOkAndHolds(scores));
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/2),
              IsOkAndHolds(UsageStore::UsageScores()));
}

TEST_F(UsageStoreTest, TruncateToNegativeNumberShouldReturnError) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));

  EXPECT_THAT(usage_store->TruncateTo(-1),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
}

TEST_F(UsageStoreTest, Reset) {
  {
    ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                               UsageStore::Create(&filesystem_, test_dir_));

    // Create usage scores with some random numbers.
    UsageStore::UsageScores scores = CreateUsageScores(
        /*type1_timestamp=*/7, /*type2_timestamp=*/9, /*type3_timestamp=*/1,
        /*type1_count=*/3, /*type2_count=*/4, /*type3_count=*/9);

    // Set scores for document 1 and document 2.
    ICING_EXPECT_OK(usage_store->SetUsageScores(/*document_id=*/1, scores));
    ICING_EXPECT_OK(usage_store->SetUsageScores(/*document_id=*/2, scores));

    EXPECT_THAT(usage_store->Reset(), IsOk());

    // After resetting, all the scores are cleared.
    EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
                IsOkAndHolds(UsageStore::UsageScores()));
    EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/2),
                IsOkAndHolds(UsageStore::UsageScores()));
  }

  // New instances should be created successfully after Reset().
  EXPECT_THAT(UsageStore::Create(&filesystem_, test_dir_).status(), IsOk());
}

TEST_F(UsageStoreTest, TimestampInSecondsShouldNotOverflow) {
  // Create a report with the max value of timestamps.
  UsageReport usage_report = CreateUsageReport(
      "namespace", "uri", /*timestamp_ms=*/std::numeric_limits<int64_t>::max(),
      UsageReport::USAGE_TYPE1);

  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));

  // The stored timestamp in seconds should be the max value of uint32.
  ICING_ASSERT_OK(usage_store->AddUsageReport(usage_report, /*document_id=*/1));
  UsageStore::UsageScores expected_scores = CreateUsageScores(
      /*type1_timestamp=*/std::numeric_limits<uint32_t>::max(),
      /*type2_timestamp=*/0, /*type3_timestamp=*/0,
      /*type1_count=*/1, /*type2_count=*/0, /*type3_count=*/0);
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
              IsOkAndHolds(expected_scores));
}

TEST_F(UsageStoreTest, CountsShouldNotOverflow) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));

  // Create usage scores with the max value of int.
  UsageStore::UsageScores scores = CreateUsageScores(
      /*type1_timestamp=*/0, /*type2_timestamp=*/0, /*type3_timestamp=*/0,
      /*type1_count=*/std::numeric_limits<int>::max(), /*type2_count=*/0,
      /*type3_count=*/0);

  ICING_ASSERT_OK(usage_store->SetUsageScores(/*document_id=*/1, scores));
  ASSERT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
              IsOkAndHolds(scores));

  // Report another usage with type 1.
  UsageReport usage_report = CreateUsageReport(
      "namespace", "uri", /*timestamp_ms=*/0, UsageReport::USAGE_TYPE1);
  ICING_ASSERT_OK(usage_store->AddUsageReport(usage_report, /*document_id=*/1));

  // usage_type1_count should not change because it's already the max value.
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/1),
              IsOkAndHolds(scores));
}

TEST_F(UsageStoreTest, StoreShouldBeResetOnVectorChecksumMismatch) {
  {
    ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                               UsageStore::Create(&filesystem_, test_dir_));

    // Create usage scores with some random numbers.
    UsageStore::UsageScores scores = CreateUsageScores(
        /*type1_timestamp=*/7, /*type2_timestamp=*/9, /*type3_timestamp=*/1,
        /*type1_count=*/3, /*type2_count=*/4, /*type3_count=*/9);
    ICING_EXPECT_OK(usage_store->SetUsageScores(/*document_id=*/0, scores));
    ASSERT_THAT(usage_store->GetUsageScores(/*document_id=*/0),
                IsOkAndHolds(scores));
  }

  // Modify the header to trigger a vector checksum mismatch.
  const std::string score_cache_file_path =
      absl_ports::StrCat(test_dir_, "/usage-scores");
  FileBackedVector<UsageStore::UsageScores>::Header header{};
  filesystem_.PRead(
      score_cache_file_path.c_str(), /*buf=*/&header,
      /*buf_size=*/sizeof(FileBackedVector<UsageStore::UsageScores>::Header),
      /*offset=*/0);
  header.vector_checksum = 10;  // Arbitrary garbage checksum
  header.header_checksum = header.CalculateHeaderChecksum();
  filesystem_.PWrite(
      score_cache_file_path.c_str(), /*offset=*/0, /*data=*/&header,
      /*data_size=*/sizeof(FileBackedVector<UsageStore::UsageScores>::Header));

  // Recover from checksum mismatch.
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));
  // Previous data should be cleared.
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/0),
              IsOkAndHolds(UsageStore::UsageScores()));
}

TEST_F(UsageStoreTest, StoreShouldBeResetOnHeaderChecksumMismatch) {
  {
    ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                               UsageStore::Create(&filesystem_, test_dir_));

    // Create usage scores with some random numbers.
    UsageStore::UsageScores scores = CreateUsageScores(
        /*type1_timestamp=*/7, /*type2_timestamp=*/9, /*type3_timestamp=*/1,
        /*type1_count=*/3, /*type2_count=*/4, /*type3_count=*/9);
    ICING_EXPECT_OK(usage_store->SetUsageScores(/*document_id=*/0, scores));
    ASSERT_THAT(usage_store->GetUsageScores(/*document_id=*/0),
                IsOkAndHolds(scores));
  }

  // Modify the header to trigger a header checksum mismatch.
  const std::string score_cache_file_path =
      absl_ports::StrCat(test_dir_, "/usage-scores");
  FileBackedVector<UsageStore::UsageScores>::Header header{};
  filesystem_.PRead(
      score_cache_file_path.c_str(), /*buf=*/&header,
      /*buf_size=*/sizeof(FileBackedVector<UsageStore::UsageScores>::Header),
      /*offset=*/0);
  header.header_checksum = 10;  // Arbitrary garbage checksum
  filesystem_.PWrite(
      score_cache_file_path.c_str(), /*offset=*/0, /*data=*/&header,
      /*data_size=*/sizeof(FileBackedVector<UsageStore::UsageScores>::Header));

  // Recover from checksum mismatch.
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));
  // Previous data should be cleared.
  EXPECT_THAT(usage_store->GetUsageScores(/*document_id=*/0),
              IsOkAndHolds(UsageStore::UsageScores()));
}

TEST_F(UsageStoreTest, GetElementsFileSize) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));

  ICING_ASSERT_OK_AND_ASSIGN(int64_t empty_file_size,
                             usage_store->GetElementsFileSize());
  EXPECT_THAT(empty_file_size, Eq(0));

  UsageReport usage_report = CreateUsageReport(
      "namespace", "uri", /*timestamp_ms=*/1000, UsageReport::USAGE_TYPE1);
  ICING_ASSERT_OK(usage_store->AddUsageReport(usage_report, /*document_id=*/1));

  EXPECT_THAT(usage_store->GetElementsFileSize(),
              IsOkAndHolds(Gt(empty_file_size)));
}

TEST_F(UsageStoreTest, GetDiskUsageEmpty) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));

  // There's some internal metadata, so our disk usage will round up to 1 block.
  ICING_ASSERT_OK_AND_ASSIGN(int64_t empty_disk_usage,
                             usage_store->GetDiskUsage());
  EXPECT_THAT(empty_disk_usage, Gt(0));
}

TEST_F(UsageStoreTest, GetDiskUsageNonEmpty) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<UsageStore> usage_store,
                             UsageStore::Create(&filesystem_, test_dir_));

  // There's some internal metadata, so our disk usage will round up to 1 block.
  ICING_ASSERT_OK_AND_ASSIGN(int64_t empty_disk_usage,
                             usage_store->GetDiskUsage());

  // Since our GetDiskUsage can only get sizes in increments of block_size, we
  // need to insert enough usage reports so the disk usage will increase by at
  // least 1 block size. The number 200 is a bit arbitrary, gotten from manually
  // testing.
  UsageReport usage_report = CreateUsageReport(
      "namespace", "uri", /*timestamp_ms=*/1000, UsageReport::USAGE_TYPE1);
  for (int i = 0; i < 200; ++i) {
    ICING_ASSERT_OK(
        usage_store->AddUsageReport(usage_report, /*document_id=*/i));
  }

  // We need to persist since iOS won't see the new disk allocations until after
  // everything gets written.
  ICING_ASSERT_OK(usage_store->PersistToDisk());

  EXPECT_THAT(usage_store->GetDiskUsage(), IsOkAndHolds(Gt(empty_disk_usage)));
}

}  // namespace

}  // namespace lib
}  // namespace icing
