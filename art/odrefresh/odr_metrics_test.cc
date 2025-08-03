/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "odr_metrics.h"

#include <unistd.h>

#include <chrono>
#include <cstdint>
#include <fstream>
#include <memory>
#include <string>
#include <thread>

#include "base/casts.h"
#include "base/common_art_test.h"
#include "odr_metrics_record.h"

namespace art {
namespace odrefresh {

using std::chrono_literals::operator""ms;  // NOLINT

class OdrMetricsTest : public CommonArtTest {
 public:
  void SetUp() override {
    CommonArtTest::SetUp();

    scratch_dir_ = std::make_unique<ScratchDir>();
    metrics_file_path_ = scratch_dir_->GetPath() + "/metrics.xml";
    cache_directory_ = scratch_dir_->GetPath() + "/dir";
    mkdir(cache_directory_.c_str(), S_IRWXU);
  }

  void TearDown() override {
    scratch_dir_.reset();
  }

  bool MetricsFileExists() const {
    const char* path = metrics_file_path_.c_str();
    return OS::FileExists(path);
  }

  const std::string GetCacheDirectory() const { return cache_directory_; }
  const std::string GetMetricsFilePath() const { return metrics_file_path_; }

 protected:
  std::unique_ptr<ScratchDir> scratch_dir_;
  std::string metrics_file_path_;
  std::string cache_directory_;
};

TEST_F(OdrMetricsTest, MetricsFileIsNotCreatedIfNotEnabled) {
  // Metrics file is (potentially) written in OdrMetrics destructor.
  {
    OdrMetrics metrics(GetCacheDirectory(), GetMetricsFilePath());
    metrics.SetArtApexVersion(99);
    metrics.SetTrigger(OdrMetrics::Trigger::kApexVersionMismatch);
    metrics.SetStage(OdrMetrics::Stage::kCheck);
    metrics.SetStatus(OdrMetrics::Status::kNoSpace);
  }
  EXPECT_FALSE(MetricsFileExists());
}

TEST_F(OdrMetricsTest, MetricsFileIsCreatedIfEnabled) {
  // Metrics file is (potentially) written in OdrMetrics destructor.
  {
    OdrMetrics metrics(GetCacheDirectory(), GetMetricsFilePath());
    metrics.SetEnabled(true);
    metrics.SetArtApexVersion(101);
    metrics.SetTrigger(OdrMetrics::Trigger::kDexFilesChanged);
    metrics.SetStage(OdrMetrics::Stage::kCheck);
    metrics.SetStatus(OdrMetrics::Status::kNoSpace);
  }
  EXPECT_TRUE(MetricsFileExists());
}

TEST_F(OdrMetricsTest, CacheSpaceValuesAreUpdated) {
  OdrMetrics metrics(GetCacheDirectory(), GetMetricsFilePath());
  metrics.CaptureSpaceFreeEnd();
  OdrMetricsRecord record = metrics.ToRecord();
  EXPECT_GT(record.cache_space_free_start_mib, 0);
  EXPECT_GT(record.cache_space_free_end_mib, 0);
}

TEST_F(OdrMetricsTest, PrimaryBcpResultWithValue) {
  OdrMetrics metrics(GetCacheDirectory(), GetMetricsFilePath());
  metrics.SetDex2OatResult(
      OdrMetrics::Stage::kPrimaryBootClasspath,
      100,
      ExecResult{.status = ExecResult::Status::kExited, .exit_code = 0, .signal = 0});
  metrics.SetBcpCompilationType(OdrMetrics::Stage::kPrimaryBootClasspath,
                                OdrMetrics::BcpCompilationType::kMainline);
  OdrMetricsRecord record = metrics.ToRecord();

  EXPECT_EQ(record.primary_bcp_compilation_millis, 100);
  EXPECT_EQ(record.primary_bcp_dex2oat_result.status, ExecResult::Status::kExited);
  EXPECT_EQ(record.primary_bcp_dex2oat_result.exit_code, 0);
  EXPECT_EQ(record.primary_bcp_dex2oat_result.signal, 0);
  EXPECT_EQ(record.primary_bcp_compilation_type,
            static_cast<int32_t>(OdrMetrics::BcpCompilationType::kMainline));

  EXPECT_EQ(record.secondary_bcp_compilation_millis, 0);
  EXPECT_EQ(record.secondary_bcp_dex2oat_result.status, kExecResultNotRun);
  EXPECT_EQ(record.secondary_bcp_compilation_type,
            static_cast<int32_t>(OdrMetrics::BcpCompilationType::kUnknown));

  EXPECT_EQ(record.system_server_compilation_millis, 0);
  EXPECT_EQ(record.system_server_dex2oat_result.status, kExecResultNotRun);
}

TEST_F(OdrMetricsTest, PrimaryBcpResultWithoutValue) {
  OdrMetrics metrics(GetCacheDirectory(), GetMetricsFilePath());

  OdrMetricsRecord record = metrics.ToRecord();
  EXPECT_EQ(record.primary_bcp_dex2oat_result.status, kExecResultNotRun);
  EXPECT_EQ(record.primary_bcp_dex2oat_result.exit_code, -1);
  EXPECT_EQ(record.primary_bcp_dex2oat_result.signal, 0);
}

TEST_F(OdrMetricsTest, SecondaryBcpResultWithValue) {
  OdrMetrics metrics(GetCacheDirectory(), GetMetricsFilePath());
  metrics.SetDex2OatResult(
      OdrMetrics::Stage::kPrimaryBootClasspath,
      100,
      ExecResult{.status = ExecResult::Status::kExited, .exit_code = 0, .signal = 0});
  metrics.SetBcpCompilationType(OdrMetrics::Stage::kPrimaryBootClasspath,
                                OdrMetrics::BcpCompilationType::kMainline);
  metrics.SetDex2OatResult(
      OdrMetrics::Stage::kSecondaryBootClasspath,
      200,
      ExecResult{.status = ExecResult::Status::kTimedOut, .exit_code = 3, .signal = 0});
  metrics.SetBcpCompilationType(OdrMetrics::Stage::kSecondaryBootClasspath,
                                OdrMetrics::BcpCompilationType::kPrimaryAndMainline);
  OdrMetricsRecord record = metrics.ToRecord();

  EXPECT_EQ(record.primary_bcp_compilation_millis, 100);
  EXPECT_EQ(record.primary_bcp_dex2oat_result.status, ExecResult::Status::kExited);
  EXPECT_EQ(record.primary_bcp_dex2oat_result.exit_code, 0);
  EXPECT_EQ(record.primary_bcp_dex2oat_result.signal, 0);
  EXPECT_EQ(record.primary_bcp_compilation_type,
            static_cast<int32_t>(OdrMetrics::BcpCompilationType::kMainline));

  EXPECT_EQ(record.secondary_bcp_compilation_millis, 200);
  EXPECT_EQ(record.secondary_bcp_dex2oat_result.status, ExecResult::Status::kTimedOut);
  EXPECT_EQ(record.secondary_bcp_dex2oat_result.exit_code, 3);
  EXPECT_EQ(record.secondary_bcp_dex2oat_result.signal, 0);
  EXPECT_EQ(record.secondary_bcp_compilation_type,
            static_cast<int32_t>(OdrMetrics::BcpCompilationType::kPrimaryAndMainline));

  EXPECT_EQ(record.system_server_compilation_millis, 0);
  EXPECT_EQ(record.system_server_dex2oat_result.status, kExecResultNotRun);
}

TEST_F(OdrMetricsTest, SystemServerResultWithValue) {
  OdrMetrics metrics(GetCacheDirectory(), GetMetricsFilePath());
  metrics.SetDex2OatResult(
      OdrMetrics::Stage::kPrimaryBootClasspath,
      100,
      ExecResult{.status = ExecResult::Status::kExited, .exit_code = 0, .signal = 0});
  metrics.SetDex2OatResult(
      OdrMetrics::Stage::kSecondaryBootClasspath,
      200,
      ExecResult{.status = ExecResult::Status::kTimedOut, .exit_code = 3, .signal = 0});
  metrics.SetDex2OatResult(
      OdrMetrics::Stage::kSystemServerClasspath,
      300,
      ExecResult{.status = ExecResult::Status::kSignaled, .exit_code = 2, .signal = 9});
  OdrMetricsRecord record = metrics.ToRecord();

  EXPECT_EQ(record.primary_bcp_compilation_millis, 100);
  EXPECT_EQ(record.primary_bcp_dex2oat_result.status, ExecResult::Status::kExited);
  EXPECT_EQ(record.primary_bcp_dex2oat_result.exit_code, 0);
  EXPECT_EQ(record.primary_bcp_dex2oat_result.signal, 0);

  EXPECT_EQ(record.secondary_bcp_compilation_millis, 200);
  EXPECT_EQ(record.secondary_bcp_dex2oat_result.status, ExecResult::Status::kTimedOut);
  EXPECT_EQ(record.secondary_bcp_dex2oat_result.exit_code, 3);
  EXPECT_EQ(record.secondary_bcp_dex2oat_result.signal, 0);

  EXPECT_EQ(record.system_server_compilation_millis, 300);
  EXPECT_EQ(record.system_server_dex2oat_result.status, ExecResult::Status::kSignaled);
  EXPECT_EQ(record.system_server_dex2oat_result.exit_code, 2);
  EXPECT_EQ(record.system_server_dex2oat_result.signal, 9);
}

}  // namespace odrefresh
}  // namespace art
