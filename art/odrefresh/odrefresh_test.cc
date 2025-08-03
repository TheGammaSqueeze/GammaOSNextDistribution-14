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

#include "odrefresh.h"

#include <unistd.h>

#include <functional>
#include <memory>
#include <string_view>
#include <utility>
#include <vector>

#include "android-base/file.h"
#include "android-base/parseint.h"
#include "android-base/scopeguard.h"
#include "android-base/stringprintf.h"
#include "android-base/strings.h"
#include "android-modules-utils/sdk_level.h"
#include "arch/instruction_set.h"
#include "base/common_art_test.h"
#include "base/file_utils.h"
#include "base/macros.h"
#include "base/stl_util.h"
#include "exec_utils.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "odr_artifacts.h"
#include "odr_common.h"
#include "odr_config.h"
#include "odr_fs_utils.h"
#include "odr_metrics.h"
#include "odrefresh/odrefresh.h"

namespace art {
namespace odrefresh {

using ::android::base::Split;
using ::android::modules::sdklevel::IsAtLeastU;
using ::testing::_;
using ::testing::AllOf;
using ::testing::Contains;
using ::testing::ElementsAre;
using ::testing::Not;
using ::testing::ResultOf;
using ::testing::Return;

constexpr int kReplace = 1;

void CreateEmptyFile(const std::string& name) {
  File* file = OS::CreateEmptyFile(name.c_str());
  ASSERT_TRUE(file != nullptr) << "Cannot create file " << name;
  file->Release();
  delete file;
}

android::base::ScopeGuard<std::function<void()>> ScopedCreateEmptyFile(const std::string& name) {
  CreateEmptyFile(name);
  return android::base::ScopeGuard([=]() { unlink(name.c_str()); });
}

class MockExecUtils : public ExecUtils {
 public:
  // A workaround to avoid MOCK_METHOD on a method with an `std::string*` parameter, which will lead
  // to a conflict between gmock and android-base/logging.h (b/132668253).
  ExecResult ExecAndReturnResult(const std::vector<std::string>& arg_vector,
                                 int,
                                 std::string*) const override {
    return {.status = ExecResult::kExited, .exit_code = DoExecAndReturnCode(arg_vector)};
  }

  MOCK_METHOD(int, DoExecAndReturnCode, (const std::vector<std::string>& arg_vector), (const));
};

// Matches a flag that starts with `flag` and whose value matches `matcher`.
MATCHER_P2(Flag, flag, matcher, "") {
  std::string_view value(arg);
  if (!android::base::ConsumePrefix(&value, flag)) {
    return false;
  }
  return ExplainMatchResult(matcher, std::string(value), result_listener);
}

// Matches a flag that starts with `flag` and whose value is a colon-separated list that matches
// `matcher`. The matcher acts on an `std::vector<std::string>` of the split list argument.
MATCHER_P2(ListFlag, flag, matcher, "") {
  return ExplainMatchResult(
      Flag(flag, ResultOf(std::bind(Split, std::placeholders::_1, ":"), matcher)),
      arg,
      result_listener);
}

// Matches an FD of a file whose path matches `matcher`.
MATCHER_P(FdOf, matcher, "") {
  char path[PATH_MAX];
  int fd;
  if (!android::base::ParseInt(std::string{arg}, &fd)) {
    return false;
  }
  std::string proc_path = android::base::StringPrintf("/proc/self/fd/%d", fd);
  ssize_t len = readlink(proc_path.c_str(), path, sizeof(path));
  if (len < 0) {
    return false;
  }
  std::string path_str{path, static_cast<size_t>(len)};
  return ExplainMatchResult(matcher, path_str, result_listener);
}

void WriteFakeApexInfoList(const std::string& filename) {
  std::string content = R"xml(
<?xml version="1.0" encoding="utf-8"?>
<apex-info-list>
  <apex-info
      moduleName="com.android.art"
      modulePath="/data/apex/active/com.android.art@319999900.apex"
      preinstalledModulePath="/system/apex/com.android.art.capex"
      versionCode="319999900"
      versionName=""
      isFactory="false"
      isActive="true"
      lastUpdateMillis="12345678">
  </apex-info>
</apex-info-list>
)xml";
  android::base::WriteStringToFile(content, filename);
}

class OdRefreshTest : public CommonArtTest {
 public:
  OdRefreshTest() : config_("odrefresh") {}

 protected:
  void SetUp() override {
    CommonArtTest::SetUp();

    temp_dir_ = std::make_unique<ScratchDir>();
    std::string temp_dir_path = temp_dir_->GetPath();
    // Remove the trailing '/';
    temp_dir_path.resize(temp_dir_path.length() - 1);

    std::string android_root_path = temp_dir_path + "/system";
    ASSERT_TRUE(EnsureDirectoryExists(android_root_path));
    android_root_env_ = std::make_unique<ScopedUnsetEnvironmentVariable>("ANDROID_ROOT");
    setenv("ANDROID_ROOT", android_root_path.c_str(), kReplace);

    std::string android_art_root_path = temp_dir_path + "/apex/com.android.art";
    ASSERT_TRUE(EnsureDirectoryExists(android_art_root_path));
    android_art_root_env_ = std::make_unique<ScopedUnsetEnvironmentVariable>("ANDROID_ART_ROOT");
    setenv("ANDROID_ART_ROOT", android_art_root_path.c_str(), kReplace);

    std::string art_apex_data_path = temp_dir_path + kArtApexDataDefaultPath;
    ASSERT_TRUE(EnsureDirectoryExists(art_apex_data_path));
    art_apex_data_env_ = std::make_unique<ScopedUnsetEnvironmentVariable>("ART_APEX_DATA");
    setenv("ART_APEX_DATA", art_apex_data_path.c_str(), kReplace);

    dalvik_cache_dir_ = art_apex_data_path + "/dalvik-cache";
    ASSERT_TRUE(EnsureDirectoryExists(dalvik_cache_dir_ + "/x86_64"));

    std::string system_etc_dir = android_root_path + "/etc";
    ASSERT_TRUE(EnsureDirectoryExists(system_etc_dir));
    framework_profile_ = system_etc_dir + "/boot-image.prof";
    CreateEmptyFile(framework_profile_);
    dirty_image_objects_file_ = system_etc_dir + "/dirty-image-objects";
    CreateEmptyFile(dirty_image_objects_file_);
    preloaded_classes_file_ = system_etc_dir + "/preloaded-classes";
    CreateEmptyFile(preloaded_classes_file_);
    std::string art_etc_dir = android_art_root_path + "/etc";
    ASSERT_TRUE(EnsureDirectoryExists(art_etc_dir));
    art_profile_ = art_etc_dir + "/boot-image.prof";
    CreateEmptyFile(art_profile_);

    framework_dir_ = android_root_path + "/framework";
    framework_jar_ = framework_dir_ + "/framework.jar";
    location_provider_jar_ = framework_dir_ + "/com.android.location.provider.jar";
    services_jar_ = framework_dir_ + "/services.jar";
    services_foo_jar_ = framework_dir_ + "/services-foo.jar";
    services_bar_jar_ = framework_dir_ + "/services-bar.jar";
    services_jar_profile_ = framework_dir_ + "/services.jar.prof";
    std::string art_javalib_dir = android_art_root_path + "/javalib";
    core_oj_jar_ = art_javalib_dir + "/core-oj.jar";
    std::string conscrypt_javalib_dir = temp_dir_path + "/apex/com.android.conscrypt/javalib";
    conscrypt_jar_ = conscrypt_javalib_dir + "/conscrypt.jar";
    std::string wifi_javalib_dir = temp_dir_path + "/apex/com.android.wifi/javalib";
    framework_wifi_jar_ = wifi_javalib_dir + "/framework-wifi.jar";

    // Create placeholder files.
    ASSERT_TRUE(EnsureDirectoryExists(framework_dir_ + "/x86_64"));
    CreateEmptyFile(framework_jar_);
    CreateEmptyFile(location_provider_jar_);
    CreateEmptyFile(services_jar_);
    CreateEmptyFile(services_foo_jar_);
    CreateEmptyFile(services_bar_jar_);
    CreateEmptyFile(services_jar_profile_);
    ASSERT_TRUE(EnsureDirectoryExists(art_javalib_dir));
    CreateEmptyFile(core_oj_jar_);
    ASSERT_TRUE(EnsureDirectoryExists(conscrypt_javalib_dir));
    CreateEmptyFile(conscrypt_jar_);
    ASSERT_TRUE(EnsureDirectoryExists(wifi_javalib_dir));
    CreateEmptyFile(framework_wifi_jar_);

    std::string apex_info_filename = temp_dir_path + "/apex-info-list.xml";
    WriteFakeApexInfoList(apex_info_filename);
    config_.SetApexInfoListFile(apex_info_filename);

    config_.SetArtBinDir(temp_dir_path + "/bin");
    config_.SetBootClasspath(core_oj_jar_ + ":" + framework_jar_ + ":" + conscrypt_jar_ + ":" +
                             framework_wifi_jar_);
    config_.SetDex2oatBootclasspath(core_oj_jar_ + ":" + framework_jar_);
    config_.SetSystemServerClasspath(location_provider_jar_ + ":" + services_jar_);
    config_.SetStandaloneSystemServerJars(services_foo_jar_ + ":" + services_bar_jar_);
    config_.SetIsa(InstructionSet::kX86_64);
    config_.SetZygoteKind(ZygoteKind::kZygote64_32);
    config_.SetSystemServerCompilerFilter("");
    config_.SetArtifactDirectory(dalvik_cache_dir_);

    std::string staging_dir = dalvik_cache_dir_ + "/staging";
    ASSERT_TRUE(EnsureDirectoryExists(staging_dir));
    config_.SetStagingDir(staging_dir);

    auto mock_exec_utils = std::make_unique<MockExecUtils>();
    mock_exec_utils_ = mock_exec_utils.get();

    metrics_ = std::make_unique<OdrMetrics>(dalvik_cache_dir_);
    cache_info_xml_ = dalvik_cache_dir_ + "/cache-info.xml";
    odrefresh_ = std::make_unique<OnDeviceRefresh>(config_,
                                                   cache_info_xml_,
                                                   std::move(mock_exec_utils),
                                                   /*check_compilation_space=*/[] { return true; });
  }

  void TearDown() override {
    metrics_.reset();
    temp_dir_.reset();
    android_root_env_.reset();
    android_art_root_env_.reset();
    art_apex_data_env_.reset();

    CommonArtTest::TearDown();
  }

  std::unique_ptr<ScratchDir> temp_dir_;
  std::unique_ptr<ScopedUnsetEnvironmentVariable> android_root_env_;
  std::unique_ptr<ScopedUnsetEnvironmentVariable> android_art_root_env_;
  std::unique_ptr<ScopedUnsetEnvironmentVariable> art_apex_data_env_;
  OdrConfig config_;
  MockExecUtils* mock_exec_utils_;
  std::unique_ptr<OnDeviceRefresh> odrefresh_;
  std::unique_ptr<OdrMetrics> metrics_;
  std::string core_oj_jar_;
  std::string framework_jar_;
  std::string conscrypt_jar_;
  std::string framework_wifi_jar_;
  std::string location_provider_jar_;
  std::string services_jar_;
  std::string services_foo_jar_;
  std::string services_bar_jar_;
  std::string dalvik_cache_dir_;
  std::string framework_dir_;
  std::string framework_profile_;
  std::string art_profile_;
  std::string services_jar_profile_;
  std::string dirty_image_objects_file_;
  std::string preloaded_classes_file_;
  std::string cache_info_xml_;
};

TEST_F(OdRefreshTest, PrimaryBootImage) {
  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(AllOf(
                  Contains(Flag("--dex-file=", core_oj_jar_)),
                  Contains(Flag("--dex-file=", framework_jar_)),
                  Not(Contains(Flag("--dex-file=", conscrypt_jar_))),
                  Not(Contains(Flag("--dex-file=", framework_wifi_jar_))),
                  Contains(Flag("--dex-fd=", FdOf(core_oj_jar_))),
                  Contains(Flag("--dex-fd=", FdOf(framework_jar_))),
                  Not(Contains(Flag("--dex-fd=", FdOf(conscrypt_jar_)))),
                  Not(Contains(Flag("--dex-fd=", FdOf(framework_wifi_jar_)))),
                  Contains(ListFlag("-Xbootclasspath:", ElementsAre(core_oj_jar_, framework_jar_))),
                  Contains(ListFlag("-Xbootclasspathfds:",
                                    ElementsAre(FdOf(core_oj_jar_), FdOf(framework_jar_)))),
                  Contains(Flag("--oat-location=", dalvik_cache_dir_ + "/x86_64/boot.oat")),
                  Contains(Flag("--base=", _)),
                  Not(Contains(Flag("--boot-image=", _))),
                  Contains(Flag("--cache-info-fd=", FdOf(cache_info_xml_))))))
      .WillOnce(Return(0));

  // Ignore the invocation for the mainline extension.
  EXPECT_CALL(*mock_exec_utils_, DoExecAndReturnCode(Contains(Flag("--dex-file=", conscrypt_jar_))))
      .WillOnce(Return(0));

  EXPECT_EQ(odrefresh_->Compile(
                *metrics_,
                CompilationOptions{
                    .boot_images_to_generate_for_isas{
                        {InstructionSet::kX86_64,
                         {.primary_boot_image = true, .boot_image_mainline_extension = true}}},
                }),
            ExitCode::kCompilationSuccess);
}

TEST_F(OdRefreshTest, BootImageMainlineExtension) {
  EXPECT_CALL(
      *mock_exec_utils_,
      DoExecAndReturnCode(AllOf(
          Not(Contains(Flag("--dex-file=", core_oj_jar_))),
          Not(Contains(Flag("--dex-file=", framework_jar_))),
          Contains(Flag("--dex-file=", conscrypt_jar_)),
          Contains(Flag("--dex-file=", framework_wifi_jar_)),
          Not(Contains(Flag("--dex-fd=", FdOf(core_oj_jar_)))),
          Not(Contains(Flag("--dex-fd=", FdOf(framework_jar_)))),
          Contains(Flag("--dex-fd=", FdOf(conscrypt_jar_))),
          Contains(Flag("--dex-fd=", FdOf(framework_wifi_jar_))),
          Contains(ListFlag(
              "-Xbootclasspath:",
              ElementsAre(core_oj_jar_, framework_jar_, conscrypt_jar_, framework_wifi_jar_))),
          Contains(ListFlag("-Xbootclasspathfds:",
                            ElementsAre(FdOf(core_oj_jar_),
                                        FdOf(framework_jar_),
                                        FdOf(conscrypt_jar_),
                                        FdOf(framework_wifi_jar_)))),
          Contains(Flag("--oat-location=", dalvik_cache_dir_ + "/x86_64/boot-conscrypt.oat")),
          Not(Contains(Flag("--base=", _))),
          Contains(Flag("--boot-image=", _)),
          Contains(Flag("--cache-info-fd=", FdOf(cache_info_xml_))))))
      .WillOnce(Return(0));

  EXPECT_EQ(odrefresh_->Compile(
                *metrics_,
                CompilationOptions{
                    .boot_images_to_generate_for_isas{
                        {InstructionSet::kX86_64, {.boot_image_mainline_extension = true}}},
                }),
            ExitCode::kCompilationSuccess);
}

TEST_F(OdRefreshTest, BootClasspathJarsWithExplicitCompilerFilter) {
  config_.SetBootImageCompilerFilter("speed");

  // Profiles should still be passed for primary boot image.
  EXPECT_CALL(
      *mock_exec_utils_,
      DoExecAndReturnCode(AllOf(Contains(Flag("--dex-file=", core_oj_jar_)),
                                Contains(Flag("--profile-file-fd=", FdOf(art_profile_))),
                                Contains(Flag("--profile-file-fd=", FdOf(framework_profile_))),
                                Contains("--compiler-filter=speed"))))
      .WillOnce(Return(0));

  // "verify" should always be used for boot image mainline extension.
  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(AllOf(Contains(Flag("--dex-file=", conscrypt_jar_)),
                                        Not(Contains(Flag("--profile-file-fd=", _))),
                                        Contains("--compiler-filter=verify"))))
      .WillOnce(Return(0));

  EXPECT_EQ(odrefresh_->Compile(
                *metrics_,
                CompilationOptions{
                    .boot_images_to_generate_for_isas{
                        {InstructionSet::kX86_64,
                         {.primary_boot_image = true, .boot_image_mainline_extension = true}}},
                }),
            ExitCode::kCompilationSuccess);
}

TEST_F(OdRefreshTest, BootClasspathJarsWithDefaultCompilerFilter) {
  EXPECT_CALL(
      *mock_exec_utils_,
      DoExecAndReturnCode(AllOf(Contains(Flag("--dex-file=", core_oj_jar_)),
                                Contains(Flag("--profile-file-fd=", FdOf(art_profile_))),
                                Contains(Flag("--profile-file-fd=", FdOf(framework_profile_))),
                                Contains("--compiler-filter=speed-profile"))))
      .WillOnce(Return(0));

  // "verify" should always be used for boot image mainline extension.
  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(AllOf(Contains(Flag("--dex-file=", conscrypt_jar_)),
                                        Not(Contains(Flag("--profile-file-fd=", _))),
                                        Contains("--compiler-filter=verify"))))
      .WillOnce(Return(0));

  EXPECT_EQ(odrefresh_->Compile(
                *metrics_,
                CompilationOptions{
                    .boot_images_to_generate_for_isas{
                        {InstructionSet::kX86_64,
                         {.primary_boot_image = true, .boot_image_mainline_extension = true}}},
                }),
            ExitCode::kCompilationSuccess);
}

TEST_F(OdRefreshTest, BootClasspathJarsFallback) {
  // Simulate the case where dex2oat fails when generating the full boot image.
  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(AllOf(Contains(Flag("--dex-file=", core_oj_jar_)),
                                        Contains(Flag("--dex-file=", framework_jar_)))))
      .Times(2)
      .WillRepeatedly(Return(1));

  // It should fall back to generating a minimal boot image.
  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(AllOf(Contains(Flag("--dex-file=", core_oj_jar_)),
                                        Not(Contains(Flag("--dex-file=", framework_jar_))))))
      .Times(2)
      .WillOnce(Return(0));

  EXPECT_EQ(odrefresh_->Compile(
                *metrics_,
                CompilationOptions{
                    .boot_images_to_generate_for_isas{
                        {InstructionSet::kX86_64,
                         {.primary_boot_image = true, .boot_image_mainline_extension = true}},
                        {InstructionSet::kX86,
                         {.primary_boot_image = true, .boot_image_mainline_extension = true}}},
                    .system_server_jars_to_compile = odrefresh_->AllSystemServerJars(),
                }),
            ExitCode::kCompilationFailed);
}

TEST_F(OdRefreshTest, AllSystemServerJars) {
  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(AllOf(Contains(Flag("--dex-file=", location_provider_jar_)),
                                        Contains("--class-loader-context=PCL[]"),
                                        Not(Contains(Flag("--class-loader-context-fds=", _))),
                                        Contains(Flag("--cache-info-fd=", FdOf(cache_info_xml_))))))
      .WillOnce(Return(0));
  EXPECT_CALL(
      *mock_exec_utils_,
      DoExecAndReturnCode(AllOf(
          Contains(Flag("--dex-file=", services_jar_)),
          Contains(Flag("--class-loader-context=", ART_FORMAT("PCL[{}]", location_provider_jar_))),
          Contains(Flag("--class-loader-context-fds=", FdOf(location_provider_jar_))),
          Contains(Flag("--cache-info-fd=", FdOf(cache_info_xml_))))))
      .WillOnce(Return(0));
  EXPECT_CALL(
      *mock_exec_utils_,
      DoExecAndReturnCode(AllOf(
          Contains(Flag("--dex-file=", services_foo_jar_)),
          Contains(Flag("--class-loader-context=",
                        ART_FORMAT("PCL[];PCL[{}:{}]", location_provider_jar_, services_jar_))),
          Contains(ListFlag("--class-loader-context-fds=",
                            ElementsAre(FdOf(location_provider_jar_), FdOf(services_jar_)))),
          Contains(Flag("--cache-info-fd=", FdOf(cache_info_xml_))))))
      .WillOnce(Return(0));
  EXPECT_CALL(
      *mock_exec_utils_,
      DoExecAndReturnCode(AllOf(
          Contains(Flag("--dex-file=", services_bar_jar_)),
          Contains(Flag("--class-loader-context=",
                        ART_FORMAT("PCL[];PCL[{}:{}]", location_provider_jar_, services_jar_))),
          Contains(ListFlag("--class-loader-context-fds=",
                            ElementsAre(FdOf(location_provider_jar_), FdOf(services_jar_)))),
          Contains(Flag("--cache-info-fd=", FdOf(cache_info_xml_))))))
      .WillOnce(Return(0));

  EXPECT_EQ(
      odrefresh_->Compile(*metrics_,
                          CompilationOptions{
                              .system_server_jars_to_compile = odrefresh_->AllSystemServerJars(),
                          }),
      ExitCode::kCompilationSuccess);
}

TEST_F(OdRefreshTest, PartialSystemServerJars) {
  EXPECT_CALL(
      *mock_exec_utils_,
      DoExecAndReturnCode(AllOf(
          Contains(Flag("--dex-file=", services_jar_)),
          Contains(Flag("--class-loader-context=", ART_FORMAT("PCL[{}]", location_provider_jar_))),
          Contains(Flag("--class-loader-context-fds=", FdOf(location_provider_jar_))))))
      .WillOnce(Return(0));
  EXPECT_CALL(
      *mock_exec_utils_,
      DoExecAndReturnCode(AllOf(
          Contains(Flag("--dex-file=", services_bar_jar_)),
          Contains(Flag("--class-loader-context=",
                        ART_FORMAT("PCL[];PCL[{}:{}]", location_provider_jar_, services_jar_))),
          Contains(ListFlag("--class-loader-context-fds=",
                            ElementsAre(FdOf(location_provider_jar_), FdOf(services_jar_)))))))
      .WillOnce(Return(0));

  EXPECT_EQ(
      odrefresh_->Compile(*metrics_,
                          CompilationOptions{
                              .system_server_jars_to_compile = {services_jar_, services_bar_jar_},
                          }),
      ExitCode::kCompilationSuccess);
}

// Verifies that odrefresh can run properly when the STANDALONE_SYSTEM_SERVER_JARS variable is
// missing, which is expected on Android S.
TEST_F(OdRefreshTest, MissingStandaloneSystemServerJars) {
  config_.SetStandaloneSystemServerJars("");
  EXPECT_CALL(*mock_exec_utils_, DoExecAndReturnCode(_)).WillRepeatedly(Return(0));
  EXPECT_EQ(
      odrefresh_->Compile(*metrics_,
                          CompilationOptions{
                              .system_server_jars_to_compile = odrefresh_->AllSystemServerJars(),
                          }),
      ExitCode::kCompilationSuccess);
}

TEST_F(OdRefreshTest, ContinueWhenBcpCompilationFailed) {
  // Simulate that the compilation of BCP for the system server ISA succeeds.
  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(AllOf(Contains("--instruction-set=x86_64"),
                                        Contains(Flag("--dex-file=", core_oj_jar_)))))
      .WillOnce(Return(0));
  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(AllOf(Contains("--instruction-set=x86_64"),
                                        Contains(Flag("--dex-file=", conscrypt_jar_)))))
      .WillOnce(Return(0));

  // Simulate that the compilation of BCP for the other ISA fails.
  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(AllOf(Contains("--instruction-set=x86"),
                                        Contains(Flag("--dex-file=", core_oj_jar_)))))
      .Times(2)
      .WillRepeatedly(Return(1));

  // It should still compile system server.
  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(Contains(Flag("--dex-file=", location_provider_jar_))))
      .WillOnce(Return(0));
  EXPECT_CALL(*mock_exec_utils_, DoExecAndReturnCode(Contains(Flag("--dex-file=", services_jar_))))
      .WillOnce(Return(0));
  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(Contains(Flag("--dex-file=", services_foo_jar_))))
      .WillOnce(Return(0));
  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(Contains(Flag("--dex-file=", services_bar_jar_))))
      .WillOnce(Return(0));

  EXPECT_EQ(odrefresh_->Compile(
                *metrics_,
                CompilationOptions{
                    .boot_images_to_generate_for_isas{
                        {InstructionSet::kX86_64,
                         {.primary_boot_image = true, .boot_image_mainline_extension = true}},
                        {InstructionSet::kX86,
                         {.primary_boot_image = true, .boot_image_mainline_extension = true}}},
                    .system_server_jars_to_compile = odrefresh_->AllSystemServerJars(),
                }),
            ExitCode::kCompilationFailed);
}

TEST_F(OdRefreshTest, ContinueWhenSystemServerCompilationFailed) {
  // Simulate that the compilation of "services.jar" fails, while others still succeed.
  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(Contains(Flag("--dex-file=", location_provider_jar_))))
      .WillOnce(Return(0));
  EXPECT_CALL(*mock_exec_utils_, DoExecAndReturnCode(Contains(Flag("--dex-file=", services_jar_))))
      .WillOnce(Return(1));
  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(Contains(Flag("--dex-file=", services_foo_jar_))))
      .WillOnce(Return(0));
  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(Contains(Flag("--dex-file=", services_bar_jar_))))
      .WillOnce(Return(0));

  EXPECT_EQ(
      odrefresh_->Compile(*metrics_,
                          CompilationOptions{
                              .system_server_jars_to_compile = odrefresh_->AllSystemServerJars(),
                          }),
      ExitCode::kCompilationFailed);
}

// Test setup: The compiler filter is explicitly set to "speed-profile". Use it regardless of
// whether the profile exists or not. Dex2oat will fall back to "verify" if the profile doesn't
// exist.
TEST_F(OdRefreshTest, CompileSetsCompilerFilterWithExplicitValue) {
  config_.SetSystemServerCompilerFilter("speed-profile");

  // Uninteresting calls.
  EXPECT_CALL(*mock_exec_utils_, DoExecAndReturnCode(_))
      .Times(odrefresh_->AllSystemServerJars().size() - 2)
      .WillRepeatedly(Return(0));

  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(AllOf(Contains(Flag("--dex-file=", location_provider_jar_)),
                                        Not(Contains(Flag("--profile-file-fd=", _))),
                                        Contains("--compiler-filter=speed-profile"))))
      .WillOnce(Return(0));
  EXPECT_CALL(
      *mock_exec_utils_,
      DoExecAndReturnCode(AllOf(Contains(Flag("--dex-file=", services_jar_)),
                                Contains(Flag("--profile-file-fd=", FdOf(services_jar_profile_))),
                                Contains("--compiler-filter=speed-profile"))))
      .WillOnce(Return(0));
  EXPECT_EQ(
      odrefresh_->Compile(*metrics_,
                          CompilationOptions{
                              .system_server_jars_to_compile = odrefresh_->AllSystemServerJars(),
                          }),
      ExitCode::kCompilationSuccess);
}

// Test setup: The compiler filter is not explicitly set. Use "speed-profile" if there is a vetted
// profile (on U+), otherwise fall back to "speed".
TEST_F(OdRefreshTest, CompileSetsCompilerFilterWithDefaultValue) {
  // Uninteresting calls.
  EXPECT_CALL(*mock_exec_utils_, DoExecAndReturnCode(_))
      .Times(odrefresh_->AllSystemServerJars().size() - 2)
      .WillRepeatedly(Return(0));

  // services.jar has a profile, while location.provider.jar does not.
  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(AllOf(Contains(Flag("--dex-file=", location_provider_jar_)),
                                        Not(Contains(Flag("--profile-file-fd=", _))),
                                        Contains("--compiler-filter=speed"))))
      .WillOnce(Return(0));
  // Only on U+ should we use the profile by default if available.
  if (IsAtLeastU()) {
    EXPECT_CALL(
        *mock_exec_utils_,
        DoExecAndReturnCode(AllOf(Contains(Flag("--dex-file=", services_jar_)),
                                  Contains(Flag("--profile-file-fd=", FdOf(services_jar_profile_))),
                                  Contains("--compiler-filter=speed-profile"))))
        .WillOnce(Return(0));
  } else {
    EXPECT_CALL(*mock_exec_utils_,
                DoExecAndReturnCode(AllOf(Contains(Flag("--dex-file=", services_jar_)),
                                          Not(Contains(Flag("--profile-file-fd=", _))),
                                          Contains("--compiler-filter=speed"))))
        .WillOnce(Return(0));
  }
  EXPECT_EQ(
      odrefresh_->Compile(*metrics_,
                          CompilationOptions{
                              .system_server_jars_to_compile = odrefresh_->AllSystemServerJars(),
                          }),
      ExitCode::kCompilationSuccess);
}

TEST_F(OdRefreshTest, OutputFilesAndIsa) {
  config_.MutableSystemProperties()->emplace("dalvik.vm.isa.x86_64.features", "foo");
  config_.MutableSystemProperties()->emplace("dalvik.vm.isa.x86_64.variant", "bar");

  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(AllOf(Contains("--instruction-set=x86_64"),
                                        Contains(Flag("--instruction-set-features=", "foo")),
                                        Contains(Flag("--instruction-set-variant=", "bar")),
                                        Contains(Flag("--image-fd=", FdOf(_))),
                                        Contains(Flag("--output-vdex-fd=", FdOf(_))),
                                        Contains(Flag("--oat-fd=", FdOf(_))))))
      .Times(2)
      .WillRepeatedly(Return(0));

  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(AllOf(Contains("--instruction-set=x86_64"),
                                        Contains(Flag("--instruction-set-features=", "foo")),
                                        Contains(Flag("--instruction-set-variant=", "bar")),
                                        Contains(Flag("--app-image-fd=", FdOf(_))),
                                        Contains(Flag("--output-vdex-fd=", FdOf(_))),
                                        Contains(Flag("--oat-fd=", FdOf(_))))))
      .Times(odrefresh_->AllSystemServerJars().size())
      .WillRepeatedly(Return(0));

  // No instruction set features or variant set for x86.
  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(AllOf(Contains("--instruction-set=x86"),
                                        Not(Contains(Flag("--instruction-set-features=", _))),
                                        Not(Contains(Flag("--instruction-set-variant=", _))))))
      .Times(2)
      .WillRepeatedly(Return(0));

  EXPECT_EQ(odrefresh_->Compile(
                *metrics_,
                CompilationOptions{
                    .boot_images_to_generate_for_isas{
                        {InstructionSet::kX86_64,
                         {.primary_boot_image = true, .boot_image_mainline_extension = true}},
                        {InstructionSet::kX86,
                         {.primary_boot_image = true, .boot_image_mainline_extension = true}}},
                    .system_server_jars_to_compile = odrefresh_->AllSystemServerJars(),
                }),
            ExitCode::kCompilationSuccess);
}

TEST_F(OdRefreshTest, RuntimeOptions) {
  config_.MutableSystemProperties()->emplace("dalvik.vm.image-dex2oat-Xms", "10");
  config_.MutableSystemProperties()->emplace("dalvik.vm.image-dex2oat-Xmx", "20");
  config_.MutableSystemProperties()->emplace("dalvik.vm.dex2oat-Xms", "30");
  config_.MutableSystemProperties()->emplace("dalvik.vm.dex2oat-Xmx", "40");

  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(AllOf(Contains(Flag("--image-fd=", FdOf(_))),
                                        Contains(Flag("-Xms", "10")),
                                        Contains(Flag("-Xmx", "20")))))
      .Times(2)
      .WillRepeatedly(Return(0));

  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(AllOf(Contains(Flag("--app-image-fd=", FdOf(_))),
                                        Contains(Flag("-Xms", "30")),
                                        Contains(Flag("-Xmx", "40")))))
      .Times(odrefresh_->AllSystemServerJars().size())
      .WillRepeatedly(Return(0));

  EXPECT_EQ(odrefresh_->Compile(
                *metrics_,
                CompilationOptions{
                    .boot_images_to_generate_for_isas{
                        {InstructionSet::kX86_64,
                         {.primary_boot_image = true, .boot_image_mainline_extension = true}}},
                    .system_server_jars_to_compile = odrefresh_->AllSystemServerJars(),
                }),
            ExitCode::kCompilationSuccess);
}

TEST_F(OdRefreshTest, GenerateBootImageMainlineExtensionChoosesBootImage_OnData) {
  // Primary boot image is on /data.
  OdrArtifacts primary = OdrArtifacts::ForBootImage(dalvik_cache_dir_ + "/x86_64/boot.art");
  auto file1 = ScopedCreateEmptyFile(primary.ImagePath());
  auto file2 = ScopedCreateEmptyFile(primary.VdexPath());
  auto file3 = ScopedCreateEmptyFile(primary.OatPath());

  EXPECT_CALL(*mock_exec_utils_,
              DoExecAndReturnCode(AllOf(
                  Contains(Flag("--dex-file=", conscrypt_jar_)),
                  Contains(Flag("--boot-image=", dalvik_cache_dir_ + "/boot.art")),
                  Contains(ListFlag("-Xbootclasspathimagefds:",
                                    ElementsAre(FdOf(primary.ImagePath()), "-1", "-1", "-1"))),
                  Contains(ListFlag("-Xbootclasspathvdexfds:",
                                    ElementsAre(FdOf(primary.VdexPath()), "-1", "-1", "-1"))),
                  Contains(ListFlag("-Xbootclasspathoatfds:",
                                    ElementsAre(FdOf(primary.OatPath()), "-1", "-1", "-1"))))))
      .WillOnce(Return(0));

  EXPECT_EQ(odrefresh_->Compile(
                *metrics_,
                CompilationOptions{
                    .boot_images_to_generate_for_isas{
                        {InstructionSet::kX86_64, {.boot_image_mainline_extension = true}}},
                }),
            ExitCode::kCompilationSuccess);
}

TEST_F(OdRefreshTest, GenerateBootImageMainlineExtensionChoosesBootImage_OnSystem) {
  // Primary boot image and framework extension are on /system.
  OdrArtifacts primary = OdrArtifacts::ForBootImage(framework_dir_ + "/x86_64/boot.art");
  auto file1 = ScopedCreateEmptyFile(primary.ImagePath());
  auto file2 = ScopedCreateEmptyFile(primary.VdexPath());
  auto file3 = ScopedCreateEmptyFile(primary.OatPath());
  OdrArtifacts framework_ext =
      OdrArtifacts::ForBootImage(framework_dir_ + "/x86_64/boot-framework.art");
  auto file4 = ScopedCreateEmptyFile(framework_ext.ImagePath());
  auto file5 = ScopedCreateEmptyFile(framework_ext.VdexPath());
  auto file6 = ScopedCreateEmptyFile(framework_ext.OatPath());

  if (IsAtLeastU()) {
    EXPECT_CALL(
        *mock_exec_utils_,
        DoExecAndReturnCode(AllOf(
            Contains(Flag("--dex-file=", conscrypt_jar_)),
            Contains(ListFlag("--boot-image=", ElementsAre(framework_dir_ + "/boot.art"))),
            Contains(ListFlag(
                "-Xbootclasspathimagefds:",
                ElementsAre(
                    FdOf(primary.ImagePath()), FdOf(framework_ext.ImagePath()), "-1", "-1"))),
            Contains(ListFlag(
                "-Xbootclasspathvdexfds:",
                ElementsAre(FdOf(primary.VdexPath()), FdOf(framework_ext.VdexPath()), "-1", "-1"))),
            Contains(ListFlag(
                "-Xbootclasspathoatfds:",
                ElementsAre(FdOf(primary.OatPath()), FdOf(framework_ext.OatPath()), "-1", "-1"))))))
        .WillOnce(Return(0));
  } else {
    EXPECT_CALL(
        *mock_exec_utils_,
        DoExecAndReturnCode(AllOf(
            Contains(Flag("--dex-file=", conscrypt_jar_)),
            Contains(ListFlag(
                "--boot-image=",
                ElementsAre(framework_dir_ + "/boot.art", framework_dir_ + "/boot-framework.art"))),
            Contains(ListFlag(
                "-Xbootclasspathimagefds:",
                ElementsAre(
                    FdOf(primary.ImagePath()), FdOf(framework_ext.ImagePath()), "-1", "-1"))),
            Contains(ListFlag(
                "-Xbootclasspathvdexfds:",
                ElementsAre(FdOf(primary.VdexPath()), FdOf(framework_ext.VdexPath()), "-1", "-1"))),
            Contains(ListFlag(
                "-Xbootclasspathoatfds:",
                ElementsAre(FdOf(primary.OatPath()), FdOf(framework_ext.OatPath()), "-1", "-1"))))))
        .WillOnce(Return(0));
  }

  EXPECT_EQ(odrefresh_->Compile(
                *metrics_,
                CompilationOptions{
                    .boot_images_to_generate_for_isas{
                        {InstructionSet::kX86_64, {.boot_image_mainline_extension = true}}},
                }),
            ExitCode::kCompilationSuccess);
}

TEST_F(OdRefreshTest, CompileSystemServerChoosesBootImage_OnData) {
  // Boot images are on /data.
  OdrArtifacts primary = OdrArtifacts::ForBootImage(dalvik_cache_dir_ + "/x86_64/boot.art");
  auto file1 = ScopedCreateEmptyFile(primary.ImagePath());
  auto file2 = ScopedCreateEmptyFile(primary.VdexPath());
  auto file3 = ScopedCreateEmptyFile(primary.OatPath());
  OdrArtifacts mainline_ext =
      OdrArtifacts::ForBootImage(dalvik_cache_dir_ + "/x86_64/boot-conscrypt.art");
  auto file4 = ScopedCreateEmptyFile(mainline_ext.ImagePath());
  auto file5 = ScopedCreateEmptyFile(mainline_ext.VdexPath());
  auto file6 = ScopedCreateEmptyFile(mainline_ext.OatPath());

  EXPECT_CALL(
      *mock_exec_utils_,
      DoExecAndReturnCode(AllOf(
          Contains(ListFlag("--boot-image=",
                            ElementsAre(dalvik_cache_dir_ + "/boot.art",
                                        dalvik_cache_dir_ + "/boot-conscrypt.art"))),
          Contains(ListFlag(
              "-Xbootclasspathimagefds:",
              ElementsAre(FdOf(primary.ImagePath()), "-1", FdOf(mainline_ext.ImagePath()), "-1"))),
          Contains(ListFlag(
              "-Xbootclasspathvdexfds:",
              ElementsAre(FdOf(primary.VdexPath()), "-1", FdOf(mainline_ext.VdexPath()), "-1"))),
          Contains(ListFlag(
              "-Xbootclasspathoatfds:",
              ElementsAre(FdOf(primary.OatPath()), "-1", FdOf(mainline_ext.OatPath()), "-1"))))))
      .Times(odrefresh_->AllSystemServerJars().size())
      .WillRepeatedly(Return(0));
  EXPECT_EQ(
      odrefresh_->Compile(*metrics_,
                          CompilationOptions{
                              .system_server_jars_to_compile = odrefresh_->AllSystemServerJars(),
                          }),
      ExitCode::kCompilationSuccess);
}

TEST_F(OdRefreshTest, CompileSystemServerChoosesBootImage_OnSystemAndData) {
  // The mainline extension is on /data, while others are on /system.
  OdrArtifacts primary = OdrArtifacts::ForBootImage(framework_dir_ + "/x86_64/boot.art");
  auto file1 = ScopedCreateEmptyFile(primary.ImagePath());
  auto file2 = ScopedCreateEmptyFile(primary.VdexPath());
  auto file3 = ScopedCreateEmptyFile(primary.OatPath());
  OdrArtifacts framework_ext =
      OdrArtifacts::ForBootImage(framework_dir_ + "/x86_64/boot-framework.art");
  auto file4 = ScopedCreateEmptyFile(framework_ext.ImagePath());
  auto file5 = ScopedCreateEmptyFile(framework_ext.VdexPath());
  auto file6 = ScopedCreateEmptyFile(framework_ext.OatPath());
  OdrArtifacts mainline_ext =
      OdrArtifacts::ForBootImage(dalvik_cache_dir_ + "/x86_64/boot-conscrypt.art");
  auto file7 = ScopedCreateEmptyFile(mainline_ext.ImagePath());
  auto file8 = ScopedCreateEmptyFile(mainline_ext.VdexPath());
  auto file9 = ScopedCreateEmptyFile(mainline_ext.OatPath());

  if (IsAtLeastU()) {
    EXPECT_CALL(*mock_exec_utils_,
                DoExecAndReturnCode(AllOf(
                    Contains(ListFlag("--boot-image=",
                                      ElementsAre(GetPrebuiltPrimaryBootImageDir() + "/boot.art",
                                                  dalvik_cache_dir_ + "/boot-conscrypt.art"))),
                    Contains(ListFlag("-Xbootclasspathimagefds:",
                                      ElementsAre(FdOf(primary.ImagePath()),
                                                  FdOf(framework_ext.ImagePath()),
                                                  FdOf(mainline_ext.ImagePath()),
                                                  "-1"))),
                    Contains(ListFlag("-Xbootclasspathvdexfds:",
                                      ElementsAre(FdOf(primary.VdexPath()),
                                                  FdOf(framework_ext.VdexPath()),
                                                  FdOf(mainline_ext.VdexPath()),
                                                  "-1"))),
                    Contains(ListFlag("-Xbootclasspathoatfds:",
                                      ElementsAre(FdOf(primary.OatPath()),
                                                  FdOf(framework_ext.OatPath()),
                                                  FdOf(mainline_ext.OatPath()),
                                                  "-1"))))))
        .Times(odrefresh_->AllSystemServerJars().size())
        .WillRepeatedly(Return(0));
  } else {
    EXPECT_CALL(*mock_exec_utils_,
                DoExecAndReturnCode(AllOf(
                    Contains(ListFlag("--boot-image=",
                                      ElementsAre(GetPrebuiltPrimaryBootImageDir() + "/boot.art",
                                                  framework_dir_ + "/boot-framework.art",
                                                  dalvik_cache_dir_ + "/boot-conscrypt.art"))),
                    Contains(ListFlag("-Xbootclasspathimagefds:",
                                      ElementsAre(FdOf(primary.ImagePath()),
                                                  FdOf(framework_ext.ImagePath()),
                                                  FdOf(mainline_ext.ImagePath()),
                                                  "-1"))),
                    Contains(ListFlag("-Xbootclasspathvdexfds:",
                                      ElementsAre(FdOf(primary.VdexPath()),
                                                  FdOf(framework_ext.VdexPath()),
                                                  FdOf(mainline_ext.VdexPath()),
                                                  "-1"))),
                    Contains(ListFlag("-Xbootclasspathoatfds:",
                                      ElementsAre(FdOf(primary.OatPath()),
                                                  FdOf(framework_ext.OatPath()),
                                                  FdOf(mainline_ext.OatPath()),
                                                  "-1"))))))
        .Times(odrefresh_->AllSystemServerJars().size())
        .WillRepeatedly(Return(0));
  }

  EXPECT_EQ(
      odrefresh_->Compile(*metrics_,
                          CompilationOptions{
                              .system_server_jars_to_compile = odrefresh_->AllSystemServerJars(),
                          }),
      ExitCode::kCompilationSuccess);
}

TEST_F(OdRefreshTest, CompileSystemServerChoosesBootImage_OnSystem) {
  // Boot images are on /system.
  OdrArtifacts primary = OdrArtifacts::ForBootImage(framework_dir_ + "/x86_64/boot.art");
  auto file1 = ScopedCreateEmptyFile(primary.ImagePath());
  auto file2 = ScopedCreateEmptyFile(primary.VdexPath());
  auto file3 = ScopedCreateEmptyFile(primary.OatPath());
  OdrArtifacts framework_ext =
      OdrArtifacts::ForBootImage(framework_dir_ + "/x86_64/boot-framework.art");
  auto file4 = ScopedCreateEmptyFile(framework_ext.ImagePath());
  auto file5 = ScopedCreateEmptyFile(framework_ext.VdexPath());
  auto file6 = ScopedCreateEmptyFile(framework_ext.OatPath());
  OdrArtifacts mainline_ext =
      OdrArtifacts::ForBootImage(framework_dir_ + "/x86_64/boot-conscrypt.art");
  auto file7 = ScopedCreateEmptyFile(mainline_ext.ImagePath());
  auto file8 = ScopedCreateEmptyFile(mainline_ext.VdexPath());
  auto file9 = ScopedCreateEmptyFile(mainline_ext.OatPath());

  if (IsAtLeastU()) {
    EXPECT_CALL(*mock_exec_utils_,
                DoExecAndReturnCode(AllOf(
                    Contains(ListFlag("--boot-image=",
                                      ElementsAre(GetPrebuiltPrimaryBootImageDir() + "/boot.art",
                                                  framework_dir_ + "/boot-conscrypt.art"))),
                    Contains(ListFlag("-Xbootclasspathimagefds:",
                                      ElementsAre(FdOf(primary.ImagePath()),
                                                  FdOf(framework_ext.ImagePath()),
                                                  FdOf(mainline_ext.ImagePath()),
                                                  "-1"))),
                    Contains(ListFlag("-Xbootclasspathvdexfds:",
                                      ElementsAre(FdOf(primary.VdexPath()),
                                                  FdOf(framework_ext.VdexPath()),
                                                  FdOf(mainline_ext.VdexPath()),
                                                  "-1"))),
                    Contains(ListFlag("-Xbootclasspathoatfds:",
                                      ElementsAre(FdOf(primary.OatPath()),
                                                  FdOf(framework_ext.OatPath()),
                                                  FdOf(mainline_ext.OatPath()),
                                                  "-1"))))))
        .Times(odrefresh_->AllSystemServerJars().size())
        .WillRepeatedly(Return(0));
  } else {
    EXPECT_CALL(*mock_exec_utils_,
                DoExecAndReturnCode(AllOf(
                    Contains(ListFlag("--boot-image=",
                                      ElementsAre(GetPrebuiltPrimaryBootImageDir() + "/boot.art",
                                                  framework_dir_ + "/boot-framework.art",
                                                  framework_dir_ + "/boot-conscrypt.art"))),
                    Contains(ListFlag("-Xbootclasspathimagefds:",
                                      ElementsAre(FdOf(primary.ImagePath()),
                                                  FdOf(framework_ext.ImagePath()),
                                                  FdOf(mainline_ext.ImagePath()),
                                                  "-1"))),
                    Contains(ListFlag("-Xbootclasspathvdexfds:",
                                      ElementsAre(FdOf(primary.VdexPath()),
                                                  FdOf(framework_ext.VdexPath()),
                                                  FdOf(mainline_ext.VdexPath()),
                                                  "-1"))),
                    Contains(ListFlag("-Xbootclasspathoatfds:",
                                      ElementsAre(FdOf(primary.OatPath()),
                                                  FdOf(framework_ext.OatPath()),
                                                  FdOf(mainline_ext.OatPath()),
                                                  "-1"))))))
        .Times(odrefresh_->AllSystemServerJars().size())
        .WillRepeatedly(Return(0));
  }

  EXPECT_EQ(
      odrefresh_->Compile(*metrics_,
                          CompilationOptions{
                              .system_server_jars_to_compile = odrefresh_->AllSystemServerJars(),
                          }),
      ExitCode::kCompilationSuccess);
}

}  // namespace odrefresh
}  // namespace art
