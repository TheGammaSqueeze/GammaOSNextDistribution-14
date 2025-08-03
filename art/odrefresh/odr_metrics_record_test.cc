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

#include "odr_metrics_record.h"

#include <string.h>

#include <fstream>

#include "android-base/result-gmock.h"
#include "android-base/stringprintf.h"
#include "base/common_art_test.h"

namespace art {
namespace odrefresh {

class OdrMetricsRecordTest : public CommonArtTest {
 protected:
  void WriteFile() {
    std::ofstream ofs(file_path_);

    ofs << "<odrefresh_metrics>";
    ofs << metrics_version_;
    ofs << "<art_apex_version>81966764218039518</art_apex_version>";
    ofs << "<trigger>16909060</trigger>";
    ofs << "<stage_reached>286397204</stage_reached>";
    ofs << status_;
    ofs << "<cache_space_free_start_mib>1633837924</cache_space_free_start_mib>";
    ofs << "<cache_space_free_end_mib>1903326068</cache_space_free_end_mib>";
    ofs << "<primary_bcp_compilation_millis>825373492</primary_bcp_compilation_millis>";
    ofs << "<secondary_bcp_compilation_millis>1094861636</secondary_bcp_compilation_millis>";
    ofs << "<system_server_compilation_millis>1364349780</system_server_compilation_millis>";
    ofs << primary_bcp_dex2oat_result_;
    ofs << secondary_bcp_dex2oat_result_;
    ofs << system_server_dex2oat_result_;
    ofs << "</odrefresh_metrics>";

    ofs.close();
  }

  void SetUp() override {
    CommonArtTest::SetUp();
    scratch_dir_ = std::make_unique<ScratchDir>(/*keep_files=*/false);
    file_path_ = scratch_dir_->GetPath() + "/metrics-record.xml";
  }

  void TearDown() override { scratch_dir_.reset(); }

  std::unique_ptr<ScratchDir> scratch_dir_;
  std::string file_path_;
  std::string metrics_version_ = android::base::StringPrintf(
      "<odrefresh_metrics_version>%d</odrefresh_metrics_version>", kOdrefreshMetricsVersion);
  std::string status_ = "<status>30</status>";
  std::string primary_bcp_dex2oat_result_ =
      R"(<primary_bcp_dex2oat_result status="1" exit-code="-1" signal="0" />)";
  std::string secondary_bcp_dex2oat_result_ =
      R"(<secondary_bcp_dex2oat_result status="2" exit-code="15" signal="0" />)";
  std::string system_server_dex2oat_result_ =
      R"(<system_server_dex2oat_result status="3" exit-code="-1" signal="9" />)";
};

using android::base::testing::HasError;
using android::base::testing::Ok;
using android::base::testing::WithMessage;

TEST_F(OdrMetricsRecordTest, HappyPath) {
  OdrMetricsRecord expected{};
  expected.odrefresh_metrics_version = art::odrefresh::kOdrefreshMetricsVersion;
  expected.art_apex_version = 0x01233456'789abcde;
  expected.trigger = 0x01020304;
  expected.stage_reached = 0x11121314;
  expected.status = 0x21222324;
  expected.cache_space_free_start_mib = 0x61626364;
  expected.cache_space_free_end_mib = 0x71727374;
  expected.primary_bcp_compilation_millis = 0x31323334;
  expected.secondary_bcp_compilation_millis = 0x41424344;
  expected.system_server_compilation_millis = 0x51525354;
  expected.primary_bcp_dex2oat_result = OdrMetricsRecord::Dex2OatExecResult(1, -1, 0);
  expected.secondary_bcp_dex2oat_result = OdrMetricsRecord::Dex2OatExecResult(2, 15, 0);
  expected.system_server_dex2oat_result = OdrMetricsRecord::Dex2OatExecResult(3, -1, 9);
  expected.primary_bcp_compilation_type = 0x82837192;
  expected.secondary_bcp_compilation_type = 0x91827312;

  ASSERT_THAT(expected.WriteToFile(file_path_), Ok());

  OdrMetricsRecord actual{};
  ASSERT_THAT(actual.ReadFromFile(file_path_), Ok());

  ASSERT_EQ(expected.odrefresh_metrics_version, actual.odrefresh_metrics_version);
  ASSERT_EQ(expected.art_apex_version, actual.art_apex_version);
  ASSERT_EQ(expected.trigger, actual.trigger);
  ASSERT_EQ(expected.stage_reached, actual.stage_reached);
  ASSERT_EQ(expected.status, actual.status);
  ASSERT_EQ(expected.cache_space_free_start_mib, actual.cache_space_free_start_mib);
  ASSERT_EQ(expected.cache_space_free_end_mib, actual.cache_space_free_end_mib);
  ASSERT_EQ(expected.primary_bcp_compilation_millis, actual.primary_bcp_compilation_millis);
  ASSERT_EQ(expected.secondary_bcp_compilation_millis, actual.secondary_bcp_compilation_millis);
  ASSERT_EQ(expected.system_server_compilation_millis, actual.system_server_compilation_millis);
  ASSERT_EQ(expected.primary_bcp_dex2oat_result.status, actual.primary_bcp_dex2oat_result.status);
  ASSERT_EQ(expected.primary_bcp_dex2oat_result.exit_code,
            actual.primary_bcp_dex2oat_result.exit_code);
  ASSERT_EQ(expected.primary_bcp_dex2oat_result.signal, actual.primary_bcp_dex2oat_result.signal);
  ASSERT_EQ(expected.secondary_bcp_dex2oat_result.status,
            actual.secondary_bcp_dex2oat_result.status);
  ASSERT_EQ(expected.secondary_bcp_dex2oat_result.exit_code,
            actual.secondary_bcp_dex2oat_result.exit_code);
  ASSERT_EQ(expected.secondary_bcp_dex2oat_result.signal,
            actual.secondary_bcp_dex2oat_result.signal);
  ASSERT_EQ(expected.system_server_dex2oat_result.status,
            actual.system_server_dex2oat_result.status);
  ASSERT_EQ(expected.system_server_dex2oat_result.exit_code,
            actual.system_server_dex2oat_result.exit_code);
  ASSERT_EQ(expected.system_server_dex2oat_result.signal,
            actual.system_server_dex2oat_result.signal);
  ASSERT_EQ(expected.primary_bcp_compilation_type, actual.primary_bcp_compilation_type);
  ASSERT_EQ(expected.secondary_bcp_compilation_type, actual.secondary_bcp_compilation_type);
  ASSERT_EQ(0, memcmp(&expected, &actual, sizeof(expected)));
}

TEST_F(OdrMetricsRecordTest, EmptyInput) {
  OdrMetricsRecord record{};
  ASSERT_THAT(record.ReadFromFile(file_path_), testing::Not(Ok()));
}

TEST_F(OdrMetricsRecordTest, UnexpectedInput) {
  std::ofstream ofs(file_path_);
  ofs << "<not_odrefresh_metrics></not_odrefresh_metrics>";
  ofs.close();

  OdrMetricsRecord record{};
  ASSERT_THAT(record.ReadFromFile(file_path_),
              HasError(WithMessage("odrefresh_metrics element not found in " + file_path_)));
}

TEST_F(OdrMetricsRecordTest, ExpectedElementNotFound) {
  metrics_version_ = "<not_valid_metric>25</not_valid_metric>";
  WriteFile();

  OdrMetricsRecord record{};
  ASSERT_THAT(
      record.ReadFromFile(file_path_),
      HasError(WithMessage("Expected Odrefresh metric odrefresh_metrics_version not found")));
}

TEST_F(OdrMetricsRecordTest, ExpectedAttributeNotFound) {
  // Missing "status".
  primary_bcp_dex2oat_result_ = R"(<primary_bcp_dex2oat_result exit-code="17" signal="18" />)";
  WriteFile();

  OdrMetricsRecord record{};
  ASSERT_THAT(record.ReadFromFile(file_path_),
              HasError(WithMessage(
                  "Expected Odrefresh metric primary_bcp_dex2oat_result.status is not an int32")));
}

TEST_F(OdrMetricsRecordTest, UnexpectedOdrefreshMetricsVersion) {
  metrics_version_ = "<odrefresh_metrics_version>0</odrefresh_metrics_version>";
  WriteFile();

  OdrMetricsRecord record{};
  std::string expected_error = android::base::StringPrintf(
      "odrefresh_metrics_version 0 is different than expected (%d)", kOdrefreshMetricsVersion);
  ASSERT_THAT(record.ReadFromFile(file_path_), HasError(WithMessage(expected_error)));
}

TEST_F(OdrMetricsRecordTest, UnexpectedType) {
  status_ = "<status>abcd</status>";  // It should be an int32.
  WriteFile();

  OdrMetricsRecord record{};
  ASSERT_THAT(record.ReadFromFile(file_path_),
              HasError(WithMessage("Odrefresh metric status is not an int32")));
}

TEST_F(OdrMetricsRecordTest, ResultStatusOutsideOfRange) {
  // Status is valid between 0 and 5 (5 being NOT_RUN)
  primary_bcp_dex2oat_result_ =
      R"(<primary_bcp_dex2oat_result status="-1" exit-code="-1" signal="0" />)";
  WriteFile();

  OdrMetricsRecord record{};
  ASSERT_THAT(
      record.ReadFromFile(file_path_),
      HasError(WithMessage("Odrefresh metric primary_bcp_dex2oat_result.status has a value (-1) "
                           "outside of the expected range ([0, 5])")));

  primary_bcp_dex2oat_result_ =
      R"(<primary_bcp_dex2oat_result status="9" exit-code="-1" signal="0" />)";
  WriteFile();

  ASSERT_THAT(
      record.ReadFromFile(file_path_),
      HasError(WithMessage("Odrefresh metric primary_bcp_dex2oat_result.status has a value (9) "
                           "outside of the expected range ([0, 5])")));
}

TEST_F(OdrMetricsRecordTest, ResultExitCodeOutsideOfRange) {
  // Exit Code is valid between -1 and 255
  secondary_bcp_dex2oat_result_ =
      R"(<secondary_bcp_dex2oat_result status="2" exit-code="-2" signal="0" />)";
  WriteFile();

  OdrMetricsRecord record{};
  ASSERT_THAT(record.ReadFromFile(file_path_),
              HasError(WithMessage(
                  "Odrefresh metric secondary_bcp_dex2oat_result.exit-code has a value (-2) "
                  "outside of the expected range ([-1, 255])")));

  secondary_bcp_dex2oat_result_ =
      R"(<secondary_bcp_dex2oat_result status="2" exit-code="258" signal="0" />)";
  WriteFile();

  ASSERT_THAT(record.ReadFromFile(file_path_),
              HasError(WithMessage(
                  "Odrefresh metric secondary_bcp_dex2oat_result.exit-code has a value (258) "
                  "outside of the expected range ([-1, 255])")));
}

TEST_F(OdrMetricsRecordTest, ResultSignalOutsideOfRange) {
  // Signal is valid between 0 and SIGRTMAX
  system_server_dex2oat_result_ =
      R"(<system_server_dex2oat_result status="3" exit-code="0" signal="-6" />)";
  WriteFile();

  OdrMetricsRecord record{};
  ASSERT_THAT(record.ReadFromFile(file_path_),
              HasError(WithMessage(android::base::StringPrintf(
                  "Odrefresh metric system_server_dex2oat_result.signal has a value (-6) "
                  "outside of the expected range ([0, %d])",
                  SIGRTMAX))));

  system_server_dex2oat_result_ =
      R"(<system_server_dex2oat_result status="3" exit-code="0" signal="65" />)";
  WriteFile();

  ASSERT_THAT(record.ReadFromFile(file_path_),
              HasError(WithMessage(android::base::StringPrintf(
                  "Odrefresh metric system_server_dex2oat_result.signal has a value (65) "
                  "outside of the expected range ([0, %d])",
                  SIGRTMAX))));
}

}  // namespace odrefresh
}  // namespace art
