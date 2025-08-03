/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include "oat_file_assistant.h"

#include <fcntl.h>
#include <gtest/gtest.h>
#include <sys/param.h>

#include <functional>
#include <iterator>
#include <memory>
#include <optional>
#include <string>
#include <type_traits>
#include <vector>

#include "android-base/scopeguard.h"
#include "android-base/strings.h"
#include "arch/instruction_set.h"
#include "art_field-inl.h"
#include "base/os.h"
#include "base/utils.h"
#include "class_linker.h"
#include "class_loader_context.h"
#include "common_runtime_test.h"
#include "dexopt_test.h"
#include "oat.h"
#include "oat_file.h"
#include "oat_file_assistant_context.h"
#include "oat_file_manager.h"
#include "scoped_thread_state_change.h"
#include "thread.h"

namespace art HIDDEN {

class OatFileAssistantBaseTest : public DexoptTest {};

class OatFileAssistantTest : public OatFileAssistantBaseTest,
                             public testing::WithParamInterface<bool> {
 public:
  void SetUp() override {
    DexoptTest::SetUp();
    with_runtime_ = GetParam();
    ofa_context_ = CreateOatFileAssistantContext();
  }

  // Verifies all variants of `GetOptimizationStatus`.
  //
  // `expected_filter` can be either a value of `CompilerFilter::Filter` or a string.
  // If `check_context` is true, only verifies the variants that checks class loader context.
  template <typename T>
  void VerifyOptimizationStatus(const std::string& file,
                                ClassLoaderContext* context,
                                const T& expected_filter,
                                const std::string& expected_reason,
                                const std::string& expected_odex_status,
                                OatFileAssistant::Location expected_location,
                                bool check_context = false) {
    std::string expected_filter_name;
    if constexpr (std::is_same_v<T, CompilerFilter::Filter>) {
      expected_filter_name = CompilerFilter::NameOfFilter(expected_filter);
    } else {
      expected_filter_name = expected_filter;
    }

    // Verify the static method (called from PM for dumpsys).
    // This variant does not check class loader context.
    if (!check_context) {
      std::string compilation_filter;
      std::string compilation_reason;

      OatFileAssistant::GetOptimizationStatus(file,
                                              kRuntimeISA,
                                              &compilation_filter,
                                              &compilation_reason,
                                              MaybeGetOatFileAssistantContext());

      ASSERT_EQ(expected_filter_name, compilation_filter);
      ASSERT_EQ(expected_reason, compilation_reason);
    }

    // Verify the instance methods (called at runtime and from artd).
    OatFileAssistant assistant = CreateOatFileAssistant(file.c_str(), context);
    VerifyOptimizationStatusWithInstance(
        &assistant, expected_filter_name, expected_reason, expected_odex_status, expected_location);
  }

  void VerifyOptimizationStatusWithInstance(OatFileAssistant* assistant,
                                            const std::string& expected_filter,
                                            const std::string& expected_reason,
                                            const std::string& expected_odex_status,
                                            OatFileAssistant::Location expected_location) {
    std::string odex_location;  // ignored
    std::string compilation_filter;
    std::string compilation_reason;
    std::string odex_status;
    OatFileAssistant::Location location;

    assistant->GetOptimizationStatus(
        &odex_location, &compilation_filter, &compilation_reason, &odex_status, &location);

    ASSERT_EQ(expected_filter, compilation_filter);
    ASSERT_EQ(expected_reason, compilation_reason);
    ASSERT_EQ(expected_odex_status, odex_status);
    ASSERT_EQ(expected_location, location);
  }

  bool InsertNewBootClasspathEntry(const std::string& src, std::string* error_msg) {
    std::vector<std::unique_ptr<const DexFile>> dex_files;
    ArtDexFileLoader dex_file_loader(src);
    if (!dex_file_loader.Open(/*verify=*/true,
                              /*verify_checksum=*/false,
                              error_msg,
                              &dex_files)) {
      return false;
    }

    runtime_->AppendToBootClassPath(src, src, dex_files);
    std::move(dex_files.begin(), dex_files.end(), std::back_inserter(opened_dex_files_));

    return true;
  }

  // Verifies the current version of `GetDexOptNeeded` (called from artd).
  void VerifyGetDexOptNeeded(OatFileAssistant* assistant,
                             CompilerFilter::Filter compiler_filter,
                             OatFileAssistant::DexOptTrigger dexopt_trigger,
                             bool expected_dexopt_needed,
                             bool expected_is_vdex_usable,
                             OatFileAssistant::Location expected_location) {
    OatFileAssistant::DexOptStatus status;
    EXPECT_EQ(
        assistant->GetDexOptNeeded(compiler_filter, dexopt_trigger, &status),
        expected_dexopt_needed);
    EXPECT_EQ(status.IsVdexUsable(), expected_is_vdex_usable);
    EXPECT_EQ(status.GetLocation(), expected_location);
  }

  // Verifies all versions of `GetDexOptNeeded` with the default dexopt trigger.
  void VerifyGetDexOptNeededDefault(OatFileAssistant* assistant,
                                    CompilerFilter::Filter compiler_filter,
                                    bool expected_dexopt_needed,
                                    bool expected_is_vdex_usable,
                                    OatFileAssistant::Location expected_location,
                                    int expected_legacy_result) {
    // Verify the current version (called from artd).
    VerifyGetDexOptNeeded(assistant,
                          compiler_filter,
                          default_trigger_,
                          expected_dexopt_needed,
                          expected_is_vdex_usable,
                          expected_location);

    // Verify the legacy version (called from PM).
    EXPECT_EQ(
        assistant->GetDexOptNeeded(compiler_filter, /*profile_changed=*/false, /*downgrade=*/false),
        expected_legacy_result);
  }

  static std::unique_ptr<ClassLoaderContext> InitializeDefaultContext() {
    auto context = ClassLoaderContext::Default();
    context->OpenDexFiles();
    return context;
  }

  // Temporarily disables the pointer to the current runtime if `with_runtime_` is false.
  // Essentially simulates an environment where there is no active runtime.
  android::base::ScopeGuard<std::function<void()>> ScopedMaybeWithoutRuntime() {
    if (!with_runtime_) {
      Runtime::TestOnlySetCurrent(nullptr);
    }
    return android::base::make_scope_guard(
        [this]() { Runtime::TestOnlySetCurrent(runtime_.get()); });
  }

  std::unique_ptr<OatFileAssistantContext> CreateOatFileAssistantContext() {
    return std::make_unique<OatFileAssistantContext>(
        std::make_unique<OatFileAssistantContext::RuntimeOptions>(
            OatFileAssistantContext::RuntimeOptions{
                .image_locations = runtime_->GetImageLocations(),
                .boot_class_path = runtime_->GetBootClassPath(),
                .boot_class_path_locations = runtime_->GetBootClassPathLocations(),
                .boot_class_path_files = !runtime_->GetBootClassPathFiles().empty() ?
                                             runtime_->GetBootClassPathFiles() :
                                             std::optional<ArrayRef<File>>(),
                .deny_art_apex_data_files = runtime_->DenyArtApexDataFiles(),
            }));
  }

  OatFileAssistantContext* MaybeGetOatFileAssistantContext() {
    return with_runtime_ ? nullptr : ofa_context_.get();
  }

  // A helper function to create OatFileAssistant with some default arguments.
  OatFileAssistant CreateOatFileAssistant(const char* dex_location,
                                          ClassLoaderContext* context = nullptr,
                                          bool load_executable = false,
                                          int vdex_fd = -1,
                                          int oat_fd = -1,
                                          int zip_fd = -1) {
    return OatFileAssistant(dex_location,
                            kRuntimeISA,
                            context != nullptr ? context : default_context_.get(),
                            load_executable,
                            /*only_load_trusted_executable=*/false,
                            MaybeGetOatFileAssistantContext(),
                            vdex_fd,
                            oat_fd,
                            zip_fd);
  }

  void ExpectHasDexFiles(OatFileAssistant* oat_file_assistant, bool expected_value) {
    std::string error_msg;
    std::optional<bool> has_dex_files = oat_file_assistant->HasDexFiles(&error_msg);
    ASSERT_TRUE(has_dex_files.has_value()) << error_msg;
    EXPECT_EQ(*has_dex_files, expected_value);
  }

  std::unique_ptr<ClassLoaderContext> default_context_ = InitializeDefaultContext();
  bool with_runtime_;
  const OatFileAssistant::DexOptTrigger default_trigger_{
      .targetFilterIsBetter = true, .primaryBootImageBecomesUsable = true, .needExtraction = true};
  std::unique_ptr<OatFileAssistantContext> ofa_context_;
  std::vector<std::unique_ptr<const DexFile>> opened_dex_files_;
};

class ScopedNonWritable {
 public:
  explicit ScopedNonWritable(const std::string& dex_location) {
    is_valid_ = false;
    size_t pos = dex_location.rfind('/');
    if (pos != std::string::npos) {
      is_valid_ = true;
      dex_parent_ = dex_location.substr(0, pos);
      if (chmod(dex_parent_.c_str(), 0555) != 0)  {
        PLOG(ERROR) << "Could not change permissions on " << dex_parent_;
      }
    }
  }

  bool IsSuccessful() { return is_valid_ && (access(dex_parent_.c_str(), W_OK) != 0); }

  ~ScopedNonWritable() {
    if (is_valid_) {
      if (chmod(dex_parent_.c_str(), 0777) != 0) {
        PLOG(ERROR) << "Could not restore permissions on " << dex_parent_;
      }
    }
  }

 private:
  std::string dex_parent_;
  bool is_valid_;
};

static bool IsExecutedAsRoot() {
  return geteuid() == 0;
}

// Case: We have a MultiDEX file and up-to-date ODEX file for it with relative
// encoded dex locations.
// Expect: The oat file status is kNoDexOptNeeded.
TEST_P(OatFileAssistantTest, RelativeEncodedDexLocation) {
  std::string dex_location = GetScratchDir() + "/RelativeEncodedDexLocation.jar";
  std::string odex_location = GetOdexDir() + "/RelativeEncodedDexLocation.odex";

  // Create the dex file
  Copy(GetMultiDexSrc1(), dex_location);

  // Create the oat file with relative encoded dex location.
  std::vector<std::string> args = {
    "--dex-file=" + dex_location,
    "--dex-location=" + std::string("RelativeEncodedDexLocation.jar"),
    "--oat-file=" + odex_location,
    "--compiler-filter=speed"
  };

  std::string error_msg;
  ASSERT_TRUE(Dex2Oat(args, &error_msg)) << error_msg;

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  // Verify we can load both dex files.
  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str(),
                                                               /*context=*/nullptr,
                                                               /*load_executable=*/true);

  std::unique_ptr<OatFile> oat_file = oat_file_assistant.GetBestOatFile();
  ASSERT_TRUE(oat_file.get() != nullptr);
  if (with_runtime_) {
    EXPECT_TRUE(oat_file->IsExecutable());
  }
  std::vector<std::unique_ptr<const DexFile>> dex_files;
  dex_files = oat_file_assistant.LoadDexFiles(*oat_file, dex_location.c_str());
  EXPECT_EQ(2u, dex_files.size());
}

TEST_P(OatFileAssistantTest, MakeUpToDateWithContext) {
  std::string dex_location = GetScratchDir() + "/TestDex.jar";
  std::string odex_location = GetOdexDir() + "/TestDex.odex";
  std::string context_location = GetScratchDir() + "/ContextDex.jar";
  Copy(GetDexSrc1(), dex_location);
  Copy(GetDexSrc2(), context_location);

  std::string context_str = "PCL[" + context_location + "]";
  std::unique_ptr<ClassLoaderContext> context = ClassLoaderContext::Create(context_str);
  ASSERT_TRUE(context != nullptr);
  ASSERT_TRUE(context->OpenDexFiles());

  std::string error_msg;
  std::vector<std::string> args;
  args.push_back("--dex-file=" + dex_location);
  args.push_back("--oat-file=" + odex_location);
  args.push_back("--class-loader-context=" + context_str);
  ASSERT_TRUE(Dex2Oat(args, &error_msg)) << error_msg;

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str(), context.get());

  std::unique_ptr<OatFile> oat_file = oat_file_assistant.GetBestOatFile();
  ASSERT_NE(nullptr, oat_file.get());
  ASSERT_NE(nullptr, oat_file->GetOatHeader().GetStoreValueByKey(OatHeader::kClassPathKey));
  EXPECT_EQ(context->EncodeContextForOatFile(""),
            oat_file->GetOatHeader().GetStoreValueByKey(OatHeader::kClassPathKey));
}

TEST_P(OatFileAssistantTest, GetDexOptNeededWithUpToDateContextRelative) {
  std::string dex_location = GetScratchDir() + "/TestDex.jar";
  std::string odex_location = GetOdexDir() + "/TestDex.odex";
  std::string context_location = GetScratchDir() + "/ContextDex.jar";
  Copy(GetDexSrc1(), dex_location);
  Copy(GetDexSrc2(), context_location);

  // A relative context simulates a dependent split context.
  std::unique_ptr<ClassLoaderContext> relative_context =
      ClassLoaderContext::Create("PCL[ContextDex.jar]");
  ASSERT_TRUE(relative_context != nullptr);
  std::vector<int> context_fds;
  ASSERT_TRUE(relative_context->OpenDexFiles(GetScratchDir(), context_fds));

  std::string error_msg;
  std::vector<std::string> args;
  args.push_back("--dex-file=" + dex_location);
  args.push_back("--oat-file=" + odex_location);
  args.push_back("--class-loader-context=PCL[" + context_location + "]");
  ASSERT_TRUE(Dex2Oat(args, &error_msg)) << error_msg;

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant =
      CreateOatFileAssistant(dex_location.c_str(), relative_context.get());

  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kDefaultCompilerFilter,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/-OatFileAssistant::kNoDexOptNeeded);
}

// Case: We have a DEX file, but no OAT file for it.
// Expect: The status is kDex2OatNeeded.
TEST_P(OatFileAssistantTest, DexNoOat) {
  std::string dex_location = GetScratchDir() + "/DexNoOat.jar";
  Copy(GetDexSrc1(), dex_location);

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kVerify,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/false,
                               /*expected_location=*/OatFileAssistant::kLocationNoneOrError,
                               /*expected_legacy_result=*/OatFileAssistant::kDex2OatFromScratch);
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeedProfile,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/false,
                               /*expected_location=*/OatFileAssistant::kLocationNoneOrError,
                               /*expected_legacy_result=*/OatFileAssistant::kDex2OatFromScratch);
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/false,
                               /*expected_location=*/OatFileAssistant::kLocationNoneOrError,
                               /*expected_legacy_result=*/OatFileAssistant::kDex2OatFromScratch);

  EXPECT_FALSE(oat_file_assistant.IsInBootClassPath());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OdexFileStatus());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OatFileStatus());
  ExpectHasDexFiles(&oat_file_assistant, true);

  VerifyOptimizationStatus(dex_location,
                           default_context_.get(),
                           "run-from-apk",
                           "unknown",
                           "io-error-no-oat",
                           OatFileAssistant::kLocationNoneOrError);
}

// Case: We have no DEX file and no OAT file.
// Expect: Status is kNoDexOptNeeded. Loading should fail, but not crash.
TEST_P(OatFileAssistantTest, NoDexNoOat) {
  std::string dex_location = GetScratchDir() + "/NoDexNoOat.jar";

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/false,
                               /*expected_location=*/OatFileAssistant::kLocationNoneOrError,
                               /*expected_legacy_result=*/OatFileAssistant::kNoDexOptNeeded);
  std::string error_msg_ignored;
  EXPECT_FALSE(oat_file_assistant.HasDexFiles(&error_msg_ignored).has_value());

  // Trying to get the best oat file should fail, but not crash.
  std::unique_ptr<OatFile> oat_file = oat_file_assistant.GetBestOatFile();
  EXPECT_EQ(nullptr, oat_file.get());

  VerifyOptimizationStatusWithInstance(&oat_file_assistant,
                                       "unknown",
                                       "unknown",
                                       "io-error-no-apk",
                                       OatFileAssistant::kLocationNoneOrError);
}

// Case: We have a DEX file and an ODEX file, but no OAT file.
// Expect: The status is kNoDexOptNeeded.
TEST_P(OatFileAssistantTest, OdexUpToDate) {
  std::string dex_location = GetScratchDir() + "/OdexUpToDate.jar";
  std::string odex_location = GetOdexDir() + "/OdexUpToDate.odex";
  Copy(GetDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeed, "install");

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  // Force the use of oat location by making the dex parent not writable.
  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/-OatFileAssistant::kNoDexOptNeeded);
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kVerify,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/-OatFileAssistant::kNoDexOptNeeded);
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kEverything,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/-OatFileAssistant::kDex2OatForFilter);

  EXPECT_FALSE(oat_file_assistant.IsInBootClassPath());
  EXPECT_EQ(OatFileAssistant::kOatUpToDate, oat_file_assistant.OdexFileStatus());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OatFileStatus());
  ExpectHasDexFiles(&oat_file_assistant, true);

  VerifyOptimizationStatus(dex_location,
                           default_context_.get(),
                           CompilerFilter::kSpeed,
                           "install",
                           "up-to-date",
                           OatFileAssistant::kLocationOdex);
}

// Case: We have an ODEX file compiled against partial boot image.
// Expect: The status is kNoDexOptNeeded.
TEST_P(OatFileAssistantTest, OdexUpToDatePartialBootImage) {
  std::string dex_location = GetScratchDir() + "/OdexUpToDate.jar";
  std::string odex_location = GetOdexDir() + "/OdexUpToDate.odex";
  Copy(GetDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeed, "install");

  // Insert an extra dex file to the boot class path.
  std::string error_msg;
  ASSERT_TRUE(InsertNewBootClasspathEntry(GetMultiDexSrc1(), &error_msg)) << error_msg;

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  // Force the use of oat location by making the dex parent not writable.
  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/-OatFileAssistant::kNoDexOptNeeded);
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kVerify,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/-OatFileAssistant::kNoDexOptNeeded);
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kEverything,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/-OatFileAssistant::kDex2OatForFilter);

  EXPECT_FALSE(oat_file_assistant.IsInBootClassPath());
  EXPECT_EQ(OatFileAssistant::kOatUpToDate, oat_file_assistant.OdexFileStatus());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OatFileStatus());
  ExpectHasDexFiles(&oat_file_assistant, true);

  VerifyOptimizationStatus(dex_location,
                           default_context_.get(),
                           CompilerFilter::kSpeed,
                           "install",
                           "up-to-date",
                           OatFileAssistant::kLocationOdex);
}

// Case: We have a DEX file and a PIC ODEX file, but no OAT file. We load the dex
// file via a symlink.
// Expect: The status is kNoDexOptNeeded.
TEST_P(OatFileAssistantTest, OdexUpToDateSymLink) {
  std::string scratch_dir = GetScratchDir();
  std::string dex_location = GetScratchDir() + "/OdexUpToDate.jar";
  std::string odex_location = GetOdexDir() + "/OdexUpToDate.odex";

  Copy(GetDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeed);

  // Now replace the dex location with a symlink.
  std::string link = scratch_dir + "/link";
  ASSERT_EQ(0, symlink(scratch_dir.c_str(), link.c_str()));
  dex_location = link + "/OdexUpToDate.jar";

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/-OatFileAssistant::kNoDexOptNeeded);
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kVerify,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/-OatFileAssistant::kNoDexOptNeeded);
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kEverything,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/-OatFileAssistant::kDex2OatForFilter);

  EXPECT_FALSE(oat_file_assistant.IsInBootClassPath());
  EXPECT_EQ(OatFileAssistant::kOatUpToDate, oat_file_assistant.OdexFileStatus());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OatFileStatus());
  ExpectHasDexFiles(&oat_file_assistant, true);
}

// Case: We have a DEX file and up-to-date OAT file for it.
// Expect: The status is kNoDexOptNeeded.
TEST_P(OatFileAssistantTest, OatUpToDate) {
  if (IsExecutedAsRoot()) {
    // We cannot simulate non writable locations when executed as root: b/38000545.
    LOG(ERROR) << "Test skipped because it's running as root";
    return;
  }

  std::string dex_location = GetScratchDir() + "/OatUpToDate.jar";
  Copy(GetDexSrc1(), dex_location);
  GenerateOatForTest(dex_location.c_str(), CompilerFilter::kSpeed);

  // Force the use of oat location by making the dex parent not writable.
  ScopedNonWritable scoped_non_writable(dex_location);
  ASSERT_TRUE(scoped_non_writable.IsSuccessful());

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOat,
                               /*expected_legacy_result=*/OatFileAssistant::kNoDexOptNeeded);
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kVerify,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOat,
                               /*expected_legacy_result=*/OatFileAssistant::kNoDexOptNeeded);
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kEverything,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOat,
                               /*expected_legacy_result=*/OatFileAssistant::kDex2OatForFilter);

  EXPECT_FALSE(oat_file_assistant.IsInBootClassPath());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OdexFileStatus());
  EXPECT_EQ(OatFileAssistant::kOatUpToDate, oat_file_assistant.OatFileStatus());
  ExpectHasDexFiles(&oat_file_assistant, true);

  VerifyOptimizationStatus(dex_location,
                           default_context_.get(),
                           CompilerFilter::kSpeed,
                           "unknown",
                           "up-to-date",
                           OatFileAssistant::kLocationOat);
}

// Case: Passing valid file descriptors of updated odex/vdex files along with the dex file.
// Expect: The status is kNoDexOptNeeded.
TEST_P(OatFileAssistantTest, GetDexOptNeededWithFd) {
  std::string dex_location = GetScratchDir() + "/OatUpToDate.jar";
  std::string odex_location = GetScratchDir() + "/OatUpToDate.odex";
  std::string vdex_location = GetScratchDir() + "/OatUpToDate.vdex";

  Copy(GetDexSrc1(), dex_location);
  GenerateOatForTest(dex_location,
                     odex_location,
                     CompilerFilter::kSpeed,
                     /* with_alternate_image= */ false);

  android::base::unique_fd odex_fd(open(odex_location.c_str(), O_RDONLY | O_CLOEXEC));
  android::base::unique_fd vdex_fd(open(vdex_location.c_str(), O_RDONLY | O_CLOEXEC));
  android::base::unique_fd zip_fd(open(dex_location.c_str(), O_RDONLY | O_CLOEXEC));

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str(),
                                                               /*context=*/nullptr,
                                                               /*load_executable=*/false,
                                                               vdex_fd.get(),
                                                               odex_fd.get(),
                                                               zip_fd.get());
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/OatFileAssistant::kNoDexOptNeeded);
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kVerify,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/OatFileAssistant::kNoDexOptNeeded);
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kEverything,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/-OatFileAssistant::kDex2OatForFilter);

  EXPECT_FALSE(oat_file_assistant.IsInBootClassPath());
  EXPECT_EQ(OatFileAssistant::kOatUpToDate, oat_file_assistant.OdexFileStatus());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OatFileStatus());
  ExpectHasDexFiles(&oat_file_assistant, true);
}

// Case: Passing invalid odex fd and valid vdex and zip fds.
// Expect: The status should be kDex2OatForBootImage.
TEST_P(OatFileAssistantTest, GetDexOptNeededWithInvalidOdexFd) {
  std::string dex_location = GetScratchDir() + "/OatUpToDate.jar";
  std::string odex_location = GetScratchDir() + "/OatUpToDate.odex";
  std::string vdex_location = GetScratchDir() + "/OatUpToDate.vdex";

  Copy(GetDexSrc1(), dex_location);
  GenerateOatForTest(dex_location,
                     odex_location,
                     CompilerFilter::kSpeed,
                     /* with_alternate_image= */ false);

  android::base::unique_fd vdex_fd(open(vdex_location.c_str(), O_RDONLY | O_CLOEXEC));
  android::base::unique_fd zip_fd(open(dex_location.c_str(), O_RDONLY | O_CLOEXEC));

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str(),
                                                               /*context=*/nullptr,
                                                               /*load_executable=*/false,
                                                               vdex_fd.get(),
                                                               /*oat_fd=*/-1,
                                                               zip_fd.get());
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kVerify,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/-OatFileAssistant::kNoDexOptNeeded);
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/-OatFileAssistant::kDex2OatForFilter);
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kEverything,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/-OatFileAssistant::kDex2OatForFilter);

  EXPECT_FALSE(oat_file_assistant.IsInBootClassPath());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OdexFileStatus());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OatFileStatus());
  ExpectHasDexFiles(&oat_file_assistant, true);
}

// Case: Passing invalid vdex fd and valid odex and zip fds.
// Expect: The status should be kDex2OatFromScratch.
TEST_P(OatFileAssistantTest, GetDexOptNeededWithInvalidVdexFd) {
  std::string dex_location = GetScratchDir() + "/OatUpToDate.jar";
  std::string odex_location = GetScratchDir() + "/OatUpToDate.odex";

  Copy(GetDexSrc1(), dex_location);
  GenerateOatForTest(dex_location,
                     odex_location,
                     CompilerFilter::kSpeed,
                     /* with_alternate_image= */ false);

  android::base::unique_fd odex_fd(open(odex_location.c_str(), O_RDONLY | O_CLOEXEC));
  android::base::unique_fd zip_fd(open(dex_location.c_str(), O_RDONLY | O_CLOEXEC));

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str(),
                                                               /*context=*/nullptr,
                                                               /*load_executable=*/false,
                                                               /*vdex_fd=*/-1,
                                                               odex_fd.get(),
                                                               zip_fd.get());

  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/false,
                               /*expected_location=*/OatFileAssistant::kLocationNoneOrError,
                               /*expected_legacy_result=*/OatFileAssistant::kDex2OatFromScratch);
  EXPECT_FALSE(oat_file_assistant.IsInBootClassPath());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OdexFileStatus());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OatFileStatus());
  ExpectHasDexFiles(&oat_file_assistant, true);
}

// Case: Passing invalid vdex and odex fd with valid zip fd.
// Expect: The status is kDex2oatFromScratch.
TEST_P(OatFileAssistantTest, GetDexOptNeededWithInvalidOdexVdexFd) {
  std::string dex_location = GetScratchDir() + "/OatUpToDate.jar";

  Copy(GetDexSrc1(), dex_location);

  android::base::unique_fd zip_fd(open(dex_location.c_str(), O_RDONLY | O_CLOEXEC));

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str(),
                                                               /*context=*/nullptr,
                                                               /*load_executable=*/false,
                                                               /*vdex_fd=*/-1,
                                                               /*oat_fd=*/-1,
                                                               zip_fd);
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/false,
                               /*expected_location=*/OatFileAssistant::kLocationNoneOrError,
                               /*expected_legacy_result=*/OatFileAssistant::kDex2OatFromScratch);
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OdexFileStatus());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OatFileStatus());
}

// Case: We have a DEX file and up-to-date VDEX file for it, but no ODEX file, and the DEX file is
// compressed.
TEST_P(OatFileAssistantTest, VdexUpToDateNoOdex) {
  std::string dex_location = GetScratchDir() + "/VdexUpToDateNoOdex.jar";
  std::string odex_location = GetOdexDir() + "/VdexUpToDateNoOdex.oat";

  Copy(GetDexSrc1(), dex_location);

  // Generating and deleting the oat file should have the side effect of
  // creating an up-to-date vdex file.
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeed);
  ASSERT_EQ(0, unlink(odex_location.c_str()));

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kVerify,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/-OatFileAssistant::kNoDexOptNeeded);
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/-OatFileAssistant::kDex2OatForFilter);

  // Make sure we don't crash in this case when we dump the status. We don't
  // care what the actual dumped value is.
  oat_file_assistant.GetStatusDump();

  VerifyOptimizationStatus(dex_location,
                           default_context_.get(),
                           "verify",
                           "vdex",
                           "up-to-date",
                           OatFileAssistant::kLocationOdex);
}

// Case: We have a DEX file and empty VDEX and ODEX files.
TEST_P(OatFileAssistantTest, EmptyVdexOdex) {
  std::string dex_location = GetScratchDir() + "/EmptyVdexOdex.jar";
  std::string odex_location = GetOdexDir() + "/EmptyVdexOdex.oat";
  std::string vdex_location = GetOdexDir() + "/EmptyVdexOdex.vdex";

  Copy(GetDexSrc1(), dex_location);
  ScratchFile vdex_file(vdex_location);
  ScratchFile odex_file(odex_location);

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/false,
                               /*expected_location=*/OatFileAssistant::kLocationNoneOrError,
                               /*expected_legacy_result=*/OatFileAssistant::kDex2OatFromScratch);
}

// Case: We have a DEX file and up-to-date (OAT) VDEX file for it, but no OAT
// file.
TEST_P(OatFileAssistantTest, VdexUpToDateNoOat) {
  if (IsExecutedAsRoot()) {
    // We cannot simulate non writable locations when executed as root: b/38000545.
    LOG(ERROR) << "Test skipped because it's running as root";
    return;
  }

  std::string dex_location = GetScratchDir() + "/VdexUpToDateNoOat.jar";
  std::string oat_location;
  std::string error_msg;
  ASSERT_TRUE(OatFileAssistant::DexLocationToOatFilename(
      dex_location, kRuntimeISA, /* deny_art_apex_data_files= */false, &oat_location, &error_msg))
      << error_msg;

  Copy(GetDexSrc1(), dex_location);
  GenerateOatForTest(dex_location.c_str(), CompilerFilter::kSpeed);
  ASSERT_EQ(0, unlink(oat_location.c_str()));

  ScopedNonWritable scoped_non_writable(dex_location);
  ASSERT_TRUE(scoped_non_writable.IsSuccessful());

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOat,
                               /*expected_legacy_result=*/OatFileAssistant::kDex2OatForFilter);
}

// Case: We have a DEX file and speed-profile OAT file for it.
// Expect: The status is kNoDexOptNeeded if the profile hasn't changed, but
// kDex2Oat if the profile has changed.
TEST_P(OatFileAssistantTest, ProfileOatUpToDate) {
  if (IsExecutedAsRoot()) {
    // We cannot simulate non writable locations when executed as root: b/38000545.
    LOG(ERROR) << "Test skipped because it's running as root";
    return;
  }

  std::string dex_location = GetScratchDir() + "/ProfileOatUpToDate.jar";
  Copy(GetDexSrc1(), dex_location);
  GenerateOatForTest(dex_location.c_str(), CompilerFilter::kSpeedProfile);

  ScopedNonWritable scoped_non_writable(dex_location);
  ASSERT_TRUE(scoped_non_writable.IsSuccessful());

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeedProfile,
                        default_trigger_,
                        /*expected_dexopt_needed=*/false,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationOat);
  EXPECT_EQ(
      OatFileAssistant::kNoDexOptNeeded,
      oat_file_assistant.GetDexOptNeeded(CompilerFilter::kSpeedProfile, /*profile_changed=*/false));

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kVerify,
                        default_trigger_,
                        /*expected_dexopt_needed=*/false,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationOat);
  EXPECT_EQ(OatFileAssistant::kNoDexOptNeeded,
            oat_file_assistant.GetDexOptNeeded(CompilerFilter::kVerify, /*profile_changed=*/false));

  OatFileAssistant::DexOptTrigger profile_changed_trigger = default_trigger_;
  profile_changed_trigger.targetFilterIsSame = true;

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeedProfile,
                        profile_changed_trigger,
                        /*expected_dexopt_needed=*/true,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationOat);
  EXPECT_EQ(
      OatFileAssistant::kDex2OatForFilter,
      oat_file_assistant.GetDexOptNeeded(CompilerFilter::kSpeedProfile, /*profile_changed=*/true));

  // We should not recompile even if `profile_changed` is true because the compiler filter should
  // not be downgraded.
  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kVerify,
                        profile_changed_trigger,
                        /*expected_dexopt_needed=*/false,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationOat);
  EXPECT_EQ(OatFileAssistant::kNoDexOptNeeded,
            oat_file_assistant.GetDexOptNeeded(CompilerFilter::kVerify, /*profile_changed=*/true));

  EXPECT_FALSE(oat_file_assistant.IsInBootClassPath());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OdexFileStatus());
  EXPECT_EQ(OatFileAssistant::kOatUpToDate, oat_file_assistant.OatFileStatus());
  ExpectHasDexFiles(&oat_file_assistant, true);
}

// Case: We have a MultiDEX file and up-to-date OAT file for it.
// Expect: The status is kNoDexOptNeeded and we load all dex files.
TEST_P(OatFileAssistantTest, MultiDexOatUpToDate) {
  if (IsExecutedAsRoot()) {
    // We cannot simulate non writable locations when executed as root: b/38000545.
    LOG(ERROR) << "Test skipped because it's running as root";
    return;
  }

  std::string dex_location = GetScratchDir() + "/MultiDexOatUpToDate.jar";
  Copy(GetMultiDexSrc1(), dex_location);
  GenerateOatForTest(dex_location.c_str(), CompilerFilter::kSpeed);

  ScopedNonWritable scoped_non_writable(dex_location);
  ASSERT_TRUE(scoped_non_writable.IsSuccessful());

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str(),
                                                               /*context=*/nullptr,
                                                               /*load_executable=*/true);
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOat,
                               /*expected_legacy_result=*/OatFileAssistant::kNoDexOptNeeded);
  ExpectHasDexFiles(&oat_file_assistant, true);

  // Verify we can load both dex files.
  std::unique_ptr<OatFile> oat_file = oat_file_assistant.GetBestOatFile();
  ASSERT_TRUE(oat_file.get() != nullptr);
  if (with_runtime_) {
    EXPECT_TRUE(oat_file->IsExecutable());
  }
  std::vector<std::unique_ptr<const DexFile>> dex_files;
  dex_files = oat_file_assistant.LoadDexFiles(*oat_file, dex_location.c_str());
  EXPECT_EQ(2u, dex_files.size());
}

// Case: We have a MultiDEX file where the non-main multdex entry is out of date.
// Expect: The status is kDex2OatNeeded.
TEST_P(OatFileAssistantTest, MultiDexNonMainOutOfDate) {
  if (IsExecutedAsRoot()) {
    // We cannot simulate non writable locations when executed as root: b/38000545.
    LOG(ERROR) << "Test skipped because it's running as root";
    return;
  }

  std::string dex_location = GetScratchDir() + "/MultiDexNonMainOutOfDate.jar";

  // Compile code for GetMultiDexSrc1.
  Copy(GetMultiDexSrc1(), dex_location);
  GenerateOatForTest(dex_location.c_str(), CompilerFilter::kSpeed);

  // Now overwrite the dex file with GetMultiDexSrc2 so the non-main checksum
  // is out of date.
  Copy(GetMultiDexSrc2(), dex_location);

  ScopedNonWritable scoped_non_writable(dex_location);
  ASSERT_TRUE(scoped_non_writable.IsSuccessful());

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/false,
                               /*expected_location=*/OatFileAssistant::kLocationNoneOrError,
                               /*expected_legacy_result=*/OatFileAssistant::kDex2OatFromScratch);
  ExpectHasDexFiles(&oat_file_assistant, true);
}

// Case: We have a DEX file and an OAT file out of date with respect to the
// dex checksum.
TEST_P(OatFileAssistantTest, OatDexOutOfDate) {
  if (IsExecutedAsRoot()) {
    // We cannot simulate non writable locations when executed as root: b/38000545.
    LOG(ERROR) << "Test skipped because it's running as root";
    return;
  }

  std::string dex_location = GetScratchDir() + "/OatDexOutOfDate.jar";

  // We create a dex, generate an oat for it, then overwrite the dex with a
  // different dex to make the oat out of date.
  Copy(GetDexSrc1(), dex_location);
  GenerateOatForTest(dex_location.c_str(), CompilerFilter::kSpeed);
  Copy(GetDexSrc2(), dex_location);

  ScopedNonWritable scoped_non_writable(dex_location);
  ASSERT_TRUE(scoped_non_writable.IsSuccessful());

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/false,
                               /*expected_location=*/OatFileAssistant::kLocationNoneOrError,
                               /*expected_legacy_result=*/OatFileAssistant::kDex2OatFromScratch);

  EXPECT_FALSE(oat_file_assistant.IsInBootClassPath());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OdexFileStatus());
  EXPECT_EQ(OatFileAssistant::kOatDexOutOfDate, oat_file_assistant.OatFileStatus());
  ExpectHasDexFiles(&oat_file_assistant, true);

  VerifyOptimizationStatus(dex_location,
                           default_context_.get(),
                           "run-from-apk-fallback",
                           "unknown",
                           "apk-more-recent",
                           OatFileAssistant::kLocationNoneOrError);
}

// Case: We have a DEX file and an (ODEX) VDEX file out of date with respect
// to the dex checksum, but no ODEX file.
TEST_P(OatFileAssistantTest, VdexDexOutOfDate) {
  std::string dex_location = GetScratchDir() + "/VdexDexOutOfDate.jar";
  std::string odex_location = GetOdexDir() + "/VdexDexOutOfDate.oat";

  Copy(GetDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeed);
  ASSERT_EQ(0, unlink(odex_location.c_str()));
  Copy(GetDexSrc2(), dex_location);

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/false,
                               /*expected_location=*/OatFileAssistant::kLocationNoneOrError,
                               /*expected_legacy_result=*/OatFileAssistant::kDex2OatFromScratch);
}

// Case: We have a MultiDEX (ODEX) VDEX file where the non-main multidex entry
// is out of date and there is no corresponding ODEX file.
TEST_P(OatFileAssistantTest, VdexMultiDexNonMainOutOfDate) {
  std::string dex_location = GetScratchDir() + "/VdexMultiDexNonMainOutOfDate.jar";
  std::string odex_location = GetOdexDir() + "/VdexMultiDexNonMainOutOfDate.odex";

  Copy(GetMultiDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeed);
  ASSERT_EQ(0, unlink(odex_location.c_str()));
  Copy(GetMultiDexSrc2(), dex_location);

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/false,
                               /*expected_location=*/OatFileAssistant::kLocationNoneOrError,
                               /*expected_legacy_result=*/OatFileAssistant::kDex2OatFromScratch);
}

// Case: We have a DEX file and an OAT file out of date with respect to the
// boot image.
TEST_P(OatFileAssistantTest, OatImageOutOfDate) {
  if (IsExecutedAsRoot()) {
    // We cannot simulate non writable locations when executed as root: b/38000545.
    LOG(ERROR) << "Test skipped because it's running as root";
    return;
  }

  std::string dex_location = GetScratchDir() + "/OatImageOutOfDate.jar";

  Copy(GetDexSrc1(), dex_location);
  GenerateOatForTest(dex_location.c_str(),
                     CompilerFilter::kSpeed,
                     /* with_alternate_image= */ true);

  ScopedNonWritable scoped_non_writable(dex_location);
  ASSERT_TRUE(scoped_non_writable.IsSuccessful());

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kVerify,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOat,
                               /*expected_legacy_result=*/OatFileAssistant::kNoDexOptNeeded);
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOat,
                               /*expected_legacy_result=*/OatFileAssistant::kDex2OatForFilter);

  EXPECT_FALSE(oat_file_assistant.IsInBootClassPath());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OdexFileStatus());
  EXPECT_EQ(OatFileAssistant::kOatBootImageOutOfDate, oat_file_assistant.OatFileStatus());
  ExpectHasDexFiles(&oat_file_assistant, true);

  VerifyOptimizationStatus(dex_location,
                           default_context_.get(),
                           "verify",
                           "vdex",
                           "up-to-date",
                           OatFileAssistant::kLocationOat);
}

TEST_P(OatFileAssistantTest, OatContextOutOfDate) {
  std::string dex_location = GetScratchDir() + "/TestDex.jar";
  std::string odex_location = GetOdexDir() + "/TestDex.odex";

  std::string context_location = GetScratchDir() + "/ContextDex.jar";
  Copy(GetDexSrc1(), dex_location);
  Copy(GetDexSrc2(), context_location);

  std::string error_msg;
  std::vector<std::string> args;
  args.push_back("--dex-file=" + dex_location);
  args.push_back("--oat-file=" + odex_location);
  args.push_back("--class-loader-context=PCL[" + context_location + "]");
  ASSERT_TRUE(Dex2Oat(args, &error_msg)) << error_msg;

  // Update the context by overriding the jar file.
  Copy(GetMultiDexSrc2(), context_location);

  std::unique_ptr<ClassLoaderContext> context =
      ClassLoaderContext::Create("PCL[" + context_location + "]");
  ASSERT_TRUE(context != nullptr);
  ASSERT_TRUE(context->OpenDexFiles());

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  VerifyOptimizationStatus(dex_location,
                           context.get(),
                           "verify",
                           "vdex",
                           "up-to-date",
                           OatFileAssistant::kLocationOdex,
                           /*check_context=*/true);
}

// Case: We have a DEX file and an ODEX file, but no OAT file.
TEST_P(OatFileAssistantTest, DexOdexNoOat) {
  std::string dex_location = GetScratchDir() + "/DexOdexNoOat.jar";
  std::string odex_location = GetOdexDir() + "/DexOdexNoOat.odex";

  // Create the dex and odex files
  Copy(GetDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeed);

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  // Verify the status.
  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/OatFileAssistant::kNoDexOptNeeded);

  EXPECT_FALSE(oat_file_assistant.IsInBootClassPath());
  EXPECT_EQ(OatFileAssistant::kOatUpToDate, oat_file_assistant.OdexFileStatus());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OatFileStatus());
  ExpectHasDexFiles(&oat_file_assistant, true);

  // We should still be able to get the non-executable odex file to run from.
  std::unique_ptr<OatFile> oat_file = oat_file_assistant.GetBestOatFile();
  ASSERT_TRUE(oat_file.get() != nullptr);
}

// Case: We have a resource-only DEX file, no ODEX file and no
// OAT file. Expect: The status is kNoDexOptNeeded.
TEST_P(OatFileAssistantTest, ResourceOnlyDex) {
  std::string dex_location = GetScratchDir() + "/ResourceOnlyDex.jar";

  Copy(GetResourceOnlySrc1(), dex_location);

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  // Verify the status.
  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/false,
                               /*expected_location=*/OatFileAssistant::kLocationNoneOrError,
                               /*expected_legacy_result=*/OatFileAssistant::kNoDexOptNeeded);
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kVerify,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/false,
                               /*expected_location=*/OatFileAssistant::kLocationNoneOrError,
                               /*expected_legacy_result=*/OatFileAssistant::kNoDexOptNeeded);

  EXPECT_FALSE(oat_file_assistant.IsInBootClassPath());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OdexFileStatus());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OatFileStatus());
  ExpectHasDexFiles(&oat_file_assistant, false);

  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/false,
                               /*expected_location=*/OatFileAssistant::kLocationNoneOrError,
                               /*expected_legacy_result=*/OatFileAssistant::kNoDexOptNeeded);

  EXPECT_FALSE(oat_file_assistant.IsInBootClassPath());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OdexFileStatus());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OatFileStatus());
  ExpectHasDexFiles(&oat_file_assistant, false);

  VerifyOptimizationStatusWithInstance(&oat_file_assistant,
                                       "unknown",
                                       "unknown",
                                       "no-dex-code",
                                       OatFileAssistant::kLocationNoneOrError);
}

// Case: We have a DEX file, an ODEX file and an OAT file.
// Expect: It shouldn't crash. We should load the odex file executable.
TEST_P(OatFileAssistantTest, OdexOatOverlap) {
  std::string dex_location = GetScratchDir() + "/OdexOatOverlap.jar";
  std::string odex_location = GetOdexDir() + "/OdexOatOverlap.odex";

  // Create the dex, the odex and the oat files.
  Copy(GetDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeed);
  GenerateOatForTest(dex_location.c_str(), CompilerFilter::kSpeed);

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  // Verify things don't go bad.
  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str(),
                                                               /*context=*/nullptr,
                                                               /*load_executable=*/true);

  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/true,
                               /*expected_location=*/OatFileAssistant::kLocationOdex,
                               /*expected_legacy_result=*/OatFileAssistant::kNoDexOptNeeded);

  EXPECT_FALSE(oat_file_assistant.IsInBootClassPath());
  EXPECT_EQ(OatFileAssistant::kOatUpToDate, oat_file_assistant.OdexFileStatus());
  EXPECT_EQ(OatFileAssistant::kOatUpToDate, oat_file_assistant.OatFileStatus());
  ExpectHasDexFiles(&oat_file_assistant, true);

  std::unique_ptr<OatFile> oat_file = oat_file_assistant.GetBestOatFile();
  ASSERT_TRUE(oat_file.get() != nullptr);

  if (with_runtime_) {
    EXPECT_TRUE(oat_file->IsExecutable());
  }
  std::vector<std::unique_ptr<const DexFile>> dex_files;
  dex_files = oat_file_assistant.LoadDexFiles(*oat_file, dex_location.c_str());
  EXPECT_EQ(1u, dex_files.size());
}

// Case: We have a DEX file and up-to-date OAT file for it.
// Expect: We should load an executable dex file.
TEST_P(OatFileAssistantTest, LoadOatUpToDate) {
  if (IsExecutedAsRoot()) {
    // We cannot simulate non writable locations when executed as root: b/38000545.
    LOG(ERROR) << "Test skipped because it's running as root";
    return;
  }

  std::string dex_location = GetScratchDir() + "/LoadOatUpToDate.jar";

  Copy(GetDexSrc1(), dex_location);
  GenerateOatForTest(dex_location.c_str(), CompilerFilter::kSpeed);

  ScopedNonWritable scoped_non_writable(dex_location);
  ASSERT_TRUE(scoped_non_writable.IsSuccessful());

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  // Load the oat using an oat file assistant.
  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str(),
                                                               /*context=*/nullptr,
                                                               /*load_executable=*/true);

  std::unique_ptr<OatFile> oat_file = oat_file_assistant.GetBestOatFile();
  ASSERT_TRUE(oat_file.get() != nullptr);
  if (with_runtime_) {
    EXPECT_TRUE(oat_file->IsExecutable());
  }
  std::vector<std::unique_ptr<const DexFile>> dex_files;
  dex_files = oat_file_assistant.LoadDexFiles(*oat_file, dex_location.c_str());
  EXPECT_EQ(1u, dex_files.size());
}

// Case: We have a DEX file and up-to-date quicken OAT file for it.
// Expect: We should still load the oat file as executable.
TEST_P(OatFileAssistantTest, LoadExecInterpretOnlyOatUpToDate) {
  if (IsExecutedAsRoot()) {
    // We cannot simulate non writable locations when executed as root: b/38000545.
    LOG(ERROR) << "Test skipped because it's running as root";
    return;
  }

  std::string dex_location = GetScratchDir() + "/LoadExecInterpretOnlyOatUpToDate.jar";

  Copy(GetDexSrc1(), dex_location);
  GenerateOatForTest(dex_location.c_str(), CompilerFilter::kVerify);

  ScopedNonWritable scoped_non_writable(dex_location);
  ASSERT_TRUE(scoped_non_writable.IsSuccessful());

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  // Load the oat using an oat file assistant.
  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str(),
                                                               /*context=*/nullptr,
                                                               /*load_executable=*/true);

  std::unique_ptr<OatFile> oat_file = oat_file_assistant.GetBestOatFile();
  ASSERT_TRUE(oat_file.get() != nullptr);
  if (with_runtime_) {
    EXPECT_TRUE(oat_file->IsExecutable());
  }
  std::vector<std::unique_ptr<const DexFile>> dex_files;
  dex_files = oat_file_assistant.LoadDexFiles(*oat_file, dex_location.c_str());
  EXPECT_EQ(1u, dex_files.size());
}

// Case: We have a DEX file and up-to-date OAT file for it.
// Expect: Loading non-executable should load the oat non-executable.
TEST_P(OatFileAssistantTest, LoadNoExecOatUpToDate) {
  if (IsExecutedAsRoot()) {
    // We cannot simulate non writable locations when executed as root: b/38000545.
    LOG(ERROR) << "Test skipped because it's running as root";
    return;
  }

  std::string dex_location = GetScratchDir() + "/LoadNoExecOatUpToDate.jar";

  Copy(GetDexSrc1(), dex_location);

  ScopedNonWritable scoped_non_writable(dex_location);
  ASSERT_TRUE(scoped_non_writable.IsSuccessful());

  GenerateOatForTest(dex_location.c_str(), CompilerFilter::kSpeed);

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  // Load the oat using an oat file assistant.
  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str(),
                                                               /*context=*/nullptr,
                                                               /*load_executable=*/true);

  std::unique_ptr<OatFile> oat_file = oat_file_assistant.GetBestOatFile();
  ASSERT_TRUE(oat_file.get() != nullptr);
  if (with_runtime_) {
    EXPECT_TRUE(oat_file->IsExecutable());
  }
  std::vector<std::unique_ptr<const DexFile>> dex_files;
  dex_files = oat_file_assistant.LoadDexFiles(*oat_file, dex_location.c_str());
  EXPECT_EQ(1u, dex_files.size());
}

// Turn an absolute path into a path relative to the current working
// directory.
static std::string MakePathRelative(const std::string& target) {
  char buf[MAXPATHLEN];
  std::string cwd = getcwd(buf, MAXPATHLEN);

  // Split the target and cwd paths into components.
  std::vector<std::string> target_path;
  std::vector<std::string> cwd_path;
  Split(target, '/', &target_path);
  Split(cwd, '/', &cwd_path);

  // Reverse the path components, so we can use pop_back().
  std::reverse(target_path.begin(), target_path.end());
  std::reverse(cwd_path.begin(), cwd_path.end());

  // Drop the common prefix of the paths. Because we reversed the path
  // components, this becomes the common suffix of target_path and cwd_path.
  while (!target_path.empty() && !cwd_path.empty()
      && target_path.back() == cwd_path.back()) {
    target_path.pop_back();
    cwd_path.pop_back();
  }

  // For each element of the remaining cwd_path, add '..' to the beginning
  // of the target path. Because we reversed the path components, we add to
  // the end of target_path.
  for (unsigned int i = 0; i < cwd_path.size(); i++) {
    target_path.push_back("..");
  }

  // Reverse again to get the right path order, and join to get the result.
  std::reverse(target_path.begin(), target_path.end());
  return android::base::Join(target_path, '/');
}

// Case: Non-absolute path to Dex location.
// Expect: Not sure, but it shouldn't crash.
TEST_P(OatFileAssistantTest, NonAbsoluteDexLocation) {
  std::string abs_dex_location = GetScratchDir() + "/NonAbsoluteDexLocation.jar";
  Copy(GetDexSrc1(), abs_dex_location);

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  std::string dex_location = MakePathRelative(abs_dex_location);
  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  EXPECT_FALSE(oat_file_assistant.IsInBootClassPath());
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/false,
                               /*expected_location=*/OatFileAssistant::kLocationNoneOrError,
                               /*expected_legacy_result=*/OatFileAssistant::kDex2OatFromScratch);
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OdexFileStatus());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OatFileStatus());
}

// Case: Very short, non-existent Dex location.
// Expect: kNoDexOptNeeded.
TEST_P(OatFileAssistantTest, ShortDexLocation) {
  std::string dex_location = "/xx";

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  EXPECT_FALSE(oat_file_assistant.IsInBootClassPath());
  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/false,
                               /*expected_location=*/OatFileAssistant::kLocationNoneOrError,
                               /*expected_legacy_result=*/OatFileAssistant::kNoDexOptNeeded);
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OdexFileStatus());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OatFileStatus());
  std::string error_msg_ignored;
  EXPECT_FALSE(oat_file_assistant.HasDexFiles(&error_msg_ignored).has_value());
}

// Case: Non-standard extension for dex file.
// Expect: The status is kDex2OatNeeded.
TEST_P(OatFileAssistantTest, LongDexExtension) {
  std::string dex_location = GetScratchDir() + "/LongDexExtension.jarx";
  Copy(GetDexSrc1(), dex_location);

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/true,
                               /*expected_is_vdex_usable=*/false,
                               /*expected_location=*/OatFileAssistant::kLocationNoneOrError,
                               /*expected_legacy_result=*/OatFileAssistant::kDex2OatFromScratch);

  EXPECT_FALSE(oat_file_assistant.IsInBootClassPath());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OdexFileStatus());
  EXPECT_EQ(OatFileAssistant::kOatCannotOpen, oat_file_assistant.OatFileStatus());
}

// A task to generate a dex location. Used by the RaceToGenerate test.
class RaceGenerateTask : public Task {
 public:
  RaceGenerateTask(OatFileAssistantBaseTest& test,
                   const std::string& dex_location,
                   const std::string& oat_location,
                   Mutex* lock)
      : test_(test),
        dex_location_(dex_location),
        oat_location_(oat_location),
        lock_(lock),
        loaded_oat_file_(nullptr) {}

  void Run([[maybe_unused]] Thread* self) override {
    // Load the dex files, and save a pointer to the loaded oat file, so that
    // we can verify only one oat file was loaded for the dex location.
    std::vector<std::unique_ptr<const DexFile>> dex_files;
    std::vector<std::string> error_msgs;
    const OatFile* oat_file = nullptr;
    {
      MutexLock mu(Thread::Current(), *lock_);
      // Create the oat file.
      std::vector<std::string> args;
      args.push_back("--dex-file=" + dex_location_);
      args.push_back("--oat-file=" + oat_location_);
      std::string error_msg;
      ASSERT_TRUE(test_.Dex2Oat(args, &error_msg)) << error_msg;
    }

    dex_files = Runtime::Current()->GetOatFileManager().OpenDexFilesFromOat(
        dex_location_.c_str(),
        Runtime::Current()->GetSystemClassLoader(),
        /*dex_elements=*/nullptr,
        &oat_file,
        &error_msgs);
    CHECK(!dex_files.empty()) << android::base::Join(error_msgs, '\n');
    if (dex_files[0]->GetOatDexFile() != nullptr) {
      loaded_oat_file_ = dex_files[0]->GetOatDexFile()->GetOatFile();
    }
    CHECK_EQ(loaded_oat_file_, oat_file);
  }

  const OatFile* GetLoadedOatFile() const {
    return loaded_oat_file_;
  }

 private:
  OatFileAssistantBaseTest& test_;
  std::string dex_location_;
  std::string oat_location_;
  Mutex* lock_;
  const OatFile* loaded_oat_file_;
};

// Test the case where dex2oat invocations race with multiple processes trying to
// load the oat file.
TEST_F(OatFileAssistantBaseTest, RaceToGenerate) {
  std::string dex_location = GetScratchDir() + "/RaceToGenerate.jar";
  std::string oat_location = GetOdexDir() + "/RaceToGenerate.oat";

  // Start the runtime to initialize the system's class loader.
  Thread::Current()->TransitionFromSuspendedToRunnable();
  runtime_->Start();

  // We use the lib core dex file, because it's large, and hopefully should
  // take a while to generate.
  Copy(GetLibCoreDexFileNames()[0], dex_location);

  const size_t kNumThreads = 16;
  Thread* self = Thread::Current();
  std::unique_ptr<ThreadPool> thread_pool(
      ThreadPool::Create("Oat file assistant test thread pool", kNumThreads));
  std::vector<std::unique_ptr<RaceGenerateTask>> tasks;
  Mutex lock("RaceToGenerate");
  for (size_t i = 0; i < kNumThreads; i++) {
    std::unique_ptr<RaceGenerateTask> task(
        new RaceGenerateTask(*this, dex_location, oat_location, &lock));
    thread_pool->AddTask(self, task.get());
    tasks.push_back(std::move(task));
  }
  thread_pool->StartWorkers(self);
  thread_pool->Wait(self, /* do_work= */ true, /* may_hold_locks= */ false);

  // Verify that tasks which got an oat file got a unique one.
  std::set<const OatFile*> oat_files;
  for (auto& task : tasks) {
    const OatFile* oat_file = task->GetLoadedOatFile();
    if (oat_file != nullptr) {
      EXPECT_TRUE(oat_files.find(oat_file) == oat_files.end());
      oat_files.insert(oat_file);
    }
  }
}

// Case: We have a DEX file and an ODEX file, and no OAT file,
// Expect: We should load the odex file executable.
TEST_P(OatFileAssistantTest, LoadDexOdexNoOat) {
  std::string dex_location = GetScratchDir() + "/LoadDexOdexNoOat.jar";
  std::string odex_location = GetOdexDir() + "/LoadDexOdexNoOat.odex";

  // Create the dex and odex files
  Copy(GetDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeed);

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  // Load the oat using an executable oat file assistant.
  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str(),
                                                               /*context=*/nullptr,
                                                               /*load_executable=*/true);

  std::unique_ptr<OatFile> oat_file = oat_file_assistant.GetBestOatFile();
  ASSERT_TRUE(oat_file.get() != nullptr);
  if (with_runtime_) {
    EXPECT_TRUE(oat_file->IsExecutable());
  }
  std::vector<std::unique_ptr<const DexFile>> dex_files;
  dex_files = oat_file_assistant.LoadDexFiles(*oat_file, dex_location.c_str());
  EXPECT_EQ(1u, dex_files.size());
}

// Case: We have a MultiDEX file and an ODEX file, and no OAT file.
// Expect: We should load the odex file executable.
TEST_P(OatFileAssistantTest, LoadMultiDexOdexNoOat) {
  std::string dex_location = GetScratchDir() + "/LoadMultiDexOdexNoOat.jar";
  std::string odex_location = GetOdexDir() + "/LoadMultiDexOdexNoOat.odex";

  // Create the dex and odex files
  Copy(GetMultiDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeed);

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  // Load the oat using an executable oat file assistant.
  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str(),
                                                               /*context=*/nullptr,
                                                               /*load_executable=*/true);

  std::unique_ptr<OatFile> oat_file = oat_file_assistant.GetBestOatFile();
  ASSERT_TRUE(oat_file.get() != nullptr);
  if (with_runtime_) {
    EXPECT_TRUE(oat_file->IsExecutable());
  }
  std::vector<std::unique_ptr<const DexFile>> dex_files;
  dex_files = oat_file_assistant.LoadDexFiles(*oat_file, dex_location.c_str());
  EXPECT_EQ(2u, dex_files.size());
}

TEST(OatFileAssistantUtilsTest, DexLocationToOdexFilename) {
  std::string error_msg;
  std::string odex_file;

  EXPECT_TRUE(OatFileAssistant::DexLocationToOdexFilename(
      "/foo/bar/baz.jar", InstructionSet::kArm, &odex_file, &error_msg))
      << error_msg;
  EXPECT_EQ("/foo/bar/oat/arm/baz.odex", odex_file);

  EXPECT_TRUE(OatFileAssistant::DexLocationToOdexFilename(
      "/foo/bar/baz.funnyext", InstructionSet::kArm, &odex_file, &error_msg))
      << error_msg;
  EXPECT_EQ("/foo/bar/oat/arm/baz.odex", odex_file);

  EXPECT_FALSE(OatFileAssistant::DexLocationToOdexFilename(
      "nopath.jar", InstructionSet::kArm, &odex_file, &error_msg));

  EXPECT_TRUE(OatFileAssistant::DexLocationToOdexFilename(
      "/foo/bar/baz_noext", InstructionSet::kArm, &odex_file, &error_msg));
  EXPECT_EQ("/foo/bar/oat/arm/baz_noext.odex", odex_file);
}

// Verify the dexopt status values from dalvik.system.DexFile
// match the OatFileAssistant::DexOptStatus values.
TEST_F(OatFileAssistantBaseTest, DexOptStatusValues) {
  std::pair<OatFileAssistant::DexOptNeeded, const char*> mapping[] = {
    {OatFileAssistant::kNoDexOptNeeded, "NO_DEXOPT_NEEDED"},
    {OatFileAssistant::kDex2OatFromScratch, "DEX2OAT_FROM_SCRATCH"},
    {OatFileAssistant::kDex2OatForBootImage, "DEX2OAT_FOR_BOOT_IMAGE"},
    {OatFileAssistant::kDex2OatForFilter, "DEX2OAT_FOR_FILTER"},
  };

  ScopedObjectAccess soa(Thread::Current());
  StackHandleScope<1> hs(soa.Self());
  ClassLinker* linker = Runtime::Current()->GetClassLinker();
  Handle<mirror::Class> dexfile(
      hs.NewHandle(linker->FindSystemClass(soa.Self(), "Ldalvik/system/DexFile;")));
  ASSERT_FALSE(dexfile == nullptr);
  linker->EnsureInitialized(soa.Self(), dexfile, true, true);

  for (std::pair<OatFileAssistant::DexOptNeeded, const char*> field : mapping) {
    ArtField* art_field = dexfile->FindStaticField(field.second, "I");
    ASSERT_FALSE(art_field == nullptr);
    EXPECT_EQ(art_field->GetTypeAsPrimitiveType(), Primitive::kPrimInt);
    EXPECT_EQ(field.first, art_field->GetInt(dexfile.Get()));
  }
}

TEST_P(OatFileAssistantTest, GetDexOptNeededWithOutOfDateContext) {
  std::string dex_location = GetScratchDir() + "/TestDex.jar";
  std::string odex_location = GetOdexDir() + "/TestDex.odex";

  std::string context_location = GetScratchDir() + "/ContextDex.jar";
  Copy(GetDexSrc1(), dex_location);
  Copy(GetDexSrc2(), context_location);

  std::string context_str = "PCL[" + context_location + "]";

  std::unique_ptr<ClassLoaderContext> context = ClassLoaderContext::Create(context_str);
  ASSERT_TRUE(context != nullptr);
  ASSERT_TRUE(context->OpenDexFiles());

  std::string error_msg;
  std::vector<std::string> args;
  args.push_back("--dex-file=" + dex_location);
  args.push_back("--oat-file=" + odex_location);
  args.push_back("--class-loader-context=" + context_str);
  ASSERT_TRUE(Dex2Oat(args, &error_msg)) << error_msg;

  // Update the context by overriding the jar file.
  Copy(GetMultiDexSrc2(), context_location);

  {
    std::unique_ptr<ClassLoaderContext> updated_context = ClassLoaderContext::Create(context_str);
    ASSERT_TRUE(updated_context != nullptr);
    std::vector<int> context_fds;
    ASSERT_TRUE(updated_context->OpenDexFiles("", context_fds,  /*only_read_checksums*/ true));

    auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

    OatFileAssistant oat_file_assistant =
        CreateOatFileAssistant(dex_location.c_str(), updated_context.get());
    // DexOptNeeded should advise compilation for filter when the context changes.
    VerifyGetDexOptNeededDefault(&oat_file_assistant,
                                 CompilerFilter::kDefaultCompilerFilter,
                                 /*expected_dexopt_needed=*/true,
                                 /*expected_is_vdex_usable=*/true,
                                 /*expected_location=*/OatFileAssistant::kLocationOdex,
                                 /*expected_legacy_result=*/-OatFileAssistant::kDex2OatForFilter);
  }
  {
    std::unique_ptr<ClassLoaderContext> updated_context = ClassLoaderContext::Create(context_str);
    ASSERT_TRUE(updated_context != nullptr);
    std::vector<int> context_fds;
    ASSERT_TRUE(updated_context->OpenDexFiles("", context_fds, /*only_read_checksums*/ true));
    args.push_back("--compiler-filter=verify");
    ASSERT_TRUE(Dex2Oat(args, &error_msg)) << error_msg;

    auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

    OatFileAssistant oat_file_assistant =
        CreateOatFileAssistant(dex_location.c_str(), updated_context.get());
    // Now check that DexOptNeeded does not advise compilation if we only verify the file.
    VerifyGetDexOptNeededDefault(&oat_file_assistant,
                                 CompilerFilter::kVerify,
                                 /*expected_dexopt_needed=*/false,
                                 /*expected_is_vdex_usable=*/true,
                                 /*expected_location=*/OatFileAssistant::kLocationOdex,
                                 /*expected_legacy_result=*/OatFileAssistant::kNoDexOptNeeded);
  }
}

// Case: We have a DEX file and speed-profile ODEX file for it. The caller's intention is to
// downgrade the compiler filter.
// Expect: Dexopt should be performed only if the target compiler filter is worse than the current
// one.
TEST_P(OatFileAssistantTest, Downgrade) {
  std::string dex_location = GetScratchDir() + "/TestDex.jar";
  std::string odex_location = GetOdexDir() + "/TestDex.odex";
  Copy(GetDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeedProfile);

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());
  OatFileAssistant::DexOptTrigger downgrade_trigger{.targetFilterIsWorse = true};

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeed,
                        downgrade_trigger,
                        /*expected_dexopt_needed=*/false,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationOdex);
  EXPECT_EQ(-OatFileAssistant::kNoDexOptNeeded,
            oat_file_assistant.GetDexOptNeeded(
                CompilerFilter::kSpeed, /*profile_changed=*/false, /*downgrade=*/true));

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeedProfile,
                        downgrade_trigger,
                        /*expected_dexopt_needed=*/false,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationOdex);
  EXPECT_EQ(-OatFileAssistant::kNoDexOptNeeded,
            oat_file_assistant.GetDexOptNeeded(
                CompilerFilter::kSpeedProfile, /*profile_changed=*/false, /*downgrade=*/true));

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kVerify,
                        downgrade_trigger,
                        /*expected_dexopt_needed=*/true,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationOdex);
  EXPECT_EQ(-OatFileAssistant::kDex2OatForFilter,
            oat_file_assistant.GetDexOptNeeded(
                CompilerFilter::kVerify, /*profile_changed=*/false, /*downgrade=*/true));
}

// Case: We have a DEX file but we don't have an ODEX file for it. The caller's intention is to
// downgrade the compiler filter.
// Expect: Dexopt should never be performed regardless of the target compiler filter.
TEST_P(OatFileAssistantTest, DowngradeNoOdex) {
  std::string dex_location = GetScratchDir() + "/TestDex.jar";
  Copy(GetDexSrc1(), dex_location);

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());
  OatFileAssistant::DexOptTrigger downgrade_trigger{.targetFilterIsWorse = true};

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeed,
                        downgrade_trigger,
                        /*expected_dexopt_needed=*/false,
                        /*expected_is_vdex_usable=*/false,
                        /*expected_location=*/OatFileAssistant::kLocationNoneOrError);
  EXPECT_EQ(OatFileAssistant::kNoDexOptNeeded,
            oat_file_assistant.GetDexOptNeeded(
                CompilerFilter::kSpeed, /*profile_changed=*/false, /*downgrade=*/true));

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeedProfile,
                        downgrade_trigger,
                        /*expected_dexopt_needed=*/false,
                        /*expected_is_vdex_usable=*/false,
                        /*expected_location=*/OatFileAssistant::kLocationNoneOrError);
  EXPECT_EQ(OatFileAssistant::kNoDexOptNeeded,
            oat_file_assistant.GetDexOptNeeded(
                CompilerFilter::kSpeedProfile, /*profile_changed=*/false, /*downgrade=*/true));

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kVerify,
                        downgrade_trigger,
                        /*expected_dexopt_needed=*/false,
                        /*expected_is_vdex_usable=*/false,
                        /*expected_location=*/OatFileAssistant::kLocationNoneOrError);
  EXPECT_EQ(OatFileAssistant::kNoDexOptNeeded,
            oat_file_assistant.GetDexOptNeeded(
                CompilerFilter::kVerify, /*profile_changed=*/false, /*downgrade=*/true));
}

// Case: We have a DEX file and speed-profile ODEX file for it. The legacy version is called with
// both `profile_changed` and `downgrade` being true. This won't happen in the real case. Just to be
// complete.
// Expect: The behavior should be as `profile_changed` is false and `downgrade` is true.
TEST_P(OatFileAssistantTest, ProfileChangedDowngrade) {
  std::string dex_location = GetScratchDir() + "/TestDex.jar";
  std::string odex_location = GetOdexDir() + "/TestDex.odex";
  Copy(GetDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeedProfile);

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  EXPECT_EQ(-OatFileAssistant::kNoDexOptNeeded,
            oat_file_assistant.GetDexOptNeeded(
                CompilerFilter::kSpeed, /*profile_changed=*/true, /*downgrade=*/true));

  EXPECT_EQ(-OatFileAssistant::kNoDexOptNeeded,
            oat_file_assistant.GetDexOptNeeded(
                CompilerFilter::kSpeedProfile, /*profile_changed=*/true, /*downgrade=*/true));

  EXPECT_EQ(-OatFileAssistant::kDex2OatForFilter,
            oat_file_assistant.GetDexOptNeeded(
                CompilerFilter::kVerify, /*profile_changed=*/true, /*downgrade=*/true));
}

// Case: We have a DEX file and speed-profile ODEX file for it. The caller's intention is to force
// the compilation.
// Expect: Dexopt should be performed regardless of the target compiler filter. The VDEX file is
// usable.
//
// The legacy version does not support this case. Historically, Package Manager does not take the
// result from OatFileAssistant for forced compilation. It uses an arbitrary non-zero value instead.
// Therefore, we don't test the legacy version here.
TEST_P(OatFileAssistantTest, Force) {
  std::string dex_location = GetScratchDir() + "/TestDex.jar";
  std::string odex_location = GetOdexDir() + "/TestDex.odex";
  Copy(GetDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeedProfile);

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());
  OatFileAssistant::DexOptTrigger force_trigger{.targetFilterIsBetter = true,
                                                .targetFilterIsSame = true,
                                                .targetFilterIsWorse = true,
                                                .primaryBootImageBecomesUsable = true};

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeed,
                        force_trigger,
                        /*expected_dexopt_needed=*/true,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationOdex);

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeedProfile,
                        force_trigger,
                        /*expected_dexopt_needed=*/true,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationOdex);

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kVerify,
                        force_trigger,
                        /*expected_dexopt_needed=*/true,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationOdex);
}

// Case: We have a DEX file but we don't have an ODEX file for it. The caller's intention is to
// force the compilation.
// Expect: Dexopt should be performed regardless of the target compiler filter. No VDEX file is
// usable.
//
// The legacy version does not support this case. Historically, Package Manager does not take the
// result from OatFileAssistant for forced compilation. It uses an arbitrary non-zero value instead.
// Therefore, we don't test the legacy version here.
TEST_P(OatFileAssistantTest, ForceNoOdex) {
  std::string dex_location = GetScratchDir() + "/TestDex.jar";
  Copy(GetDexSrc1(), dex_location);

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());
  OatFileAssistant::DexOptTrigger force_trigger{.targetFilterIsBetter = true,
                                                .targetFilterIsSame = true,
                                                .targetFilterIsWorse = true,
                                                .primaryBootImageBecomesUsable = true};

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeed,
                        force_trigger,
                        /*expected_dexopt_needed=*/true,
                        /*expected_is_vdex_usable=*/false,
                        /*expected_location=*/OatFileAssistant::kLocationNoneOrError);

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeedProfile,
                        force_trigger,
                        /*expected_dexopt_needed=*/true,
                        /*expected_is_vdex_usable=*/false,
                        /*expected_location=*/OatFileAssistant::kLocationNoneOrError);

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kVerify,
                        force_trigger,
                        /*expected_dexopt_needed=*/true,
                        /*expected_is_vdex_usable=*/false,
                        /*expected_location=*/OatFileAssistant::kLocationNoneOrError);
}

// Case: We have a DEX file and a DM file for it, and the DEX file is uncompressed.
// Expect: Dexopt should be performed if the compiler filter is better than "verify". The location
// should be kLocationDm.
//
// The legacy version should return kDex2OatFromScratch if the target compiler filter is better than
// "verify".
TEST_P(OatFileAssistantTest, DmUpToDateDexUncompressed) {
  std::string dex_location = GetScratchDir() + "/TestDex.jar";
  std::string dm_location = GetScratchDir() + "/TestDex.dm";
  std::string odex_location = GetOdexDir() + "/TestDex.odex";
  std::string vdex_location = GetOdexDir() + "/TestDex.vdex";
  Copy(GetMultiDexUncompressedAlignedSrc1(), dex_location);

  // Generate temporary ODEX and VDEX files in order to create the DM file from.
  GenerateOdexForTest(
      dex_location, odex_location, CompilerFilter::kVerify, "install", {"--copy-dex-files=false"});

  CreateDexMetadata(vdex_location, dm_location);

  // Cleanup the temporary files.
  ASSERT_EQ(0, unlink(odex_location.c_str()));
  ASSERT_EQ(0, unlink(vdex_location.c_str()));

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeed,
                        default_trigger_,
                        /*expected_dexopt_needed=*/true,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationDm);
  EXPECT_EQ(OatFileAssistant::kDex2OatFromScratch,
            oat_file_assistant.GetDexOptNeeded(CompilerFilter::kSpeed));

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeedProfile,
                        default_trigger_,
                        /*expected_dexopt_needed=*/true,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationDm);
  EXPECT_EQ(OatFileAssistant::kDex2OatFromScratch,
            oat_file_assistant.GetDexOptNeeded(CompilerFilter::kSpeedProfile));

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kVerify,
                        default_trigger_,
                        /*expected_dexopt_needed=*/false,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationDm);
  EXPECT_EQ(OatFileAssistant::kNoDexOptNeeded,
            oat_file_assistant.GetDexOptNeeded(CompilerFilter::kVerify));
}

// Case: We have a DEX file and a DM file for it, and the DEX file is compressed.
// Expect: Dexopt should be performed regardless of the compiler filter. The location
// should be kLocationDm.
TEST_P(OatFileAssistantTest, DmUpToDateDexCompressed) {
  std::string dex_location = GetScratchDir() + "/TestDex.jar";
  std::string dm_location = GetScratchDir() + "/TestDex.dm";
  std::string odex_location = GetOdexDir() + "/TestDex.odex";
  std::string vdex_location = GetOdexDir() + "/TestDex.vdex";
  Copy(GetMultiDexSrc1(), dex_location);

  // Generate temporary ODEX and VDEX files in order to create the DM file from.
  GenerateOdexForTest(
      dex_location, odex_location, CompilerFilter::kVerify, "install", {"--copy-dex-files=false"});

  CreateDexMetadata(vdex_location, dm_location);

  // Cleanup the temporary files.
  ASSERT_EQ(0, unlink(odex_location.c_str()));
  ASSERT_EQ(0, unlink(vdex_location.c_str()));

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeed,
                        default_trigger_,
                        /*expected_dexopt_needed=*/true,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationDm);
  EXPECT_EQ(OatFileAssistant::kDex2OatFromScratch,
            oat_file_assistant.GetDexOptNeeded(CompilerFilter::kSpeed));

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeedProfile,
                        default_trigger_,
                        /*expected_dexopt_needed=*/true,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationDm);
  EXPECT_EQ(OatFileAssistant::kDex2OatFromScratch,
            oat_file_assistant.GetDexOptNeeded(CompilerFilter::kSpeedProfile));

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kVerify,
                        default_trigger_,
                        /*expected_dexopt_needed=*/true,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationDm);
  EXPECT_EQ(OatFileAssistant::kDex2OatFromScratch,
            oat_file_assistant.GetDexOptNeeded(CompilerFilter::kVerify));
}

// Case: We have an ODEX file, but the DEX file is gone.
// Expect: No dexopt is needed, as there's nothing we can do.
TEST_P(OatFileAssistantTest, OdexNoDex) {
  std::string dex_location = GetScratchDir() + "/OdexNoDex.jar";
  std::string odex_location = GetOdexDir() + "/OdexNoDex.oat";

  Copy(GetDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeed);
  ASSERT_EQ(0, unlink(dex_location.c_str()));

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/false,
                               /*expected_location=*/OatFileAssistant::kLocationNoneOrError,
                               /*expected_legacy_result=*/OatFileAssistant::kNoDexOptNeeded);

  VerifyOptimizationStatusWithInstance(&oat_file_assistant,
                                       "unknown",
                                       "unknown",
                                       "io-error-no-apk",
                                       OatFileAssistant::kLocationNoneOrError);
}

// Case: We have a VDEX file, but the DEX file is gone.
// Expect: No dexopt is needed, as there's nothing we can do.
TEST_P(OatFileAssistantTest, VdexNoDex) {
  std::string dex_location = GetScratchDir() + "/VdexNoDex.jar";
  std::string odex_location = GetOdexDir() + "/VdexNoDex.oat";

  Copy(GetDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeed);
  ASSERT_EQ(0, unlink(odex_location.c_str()));
  ASSERT_EQ(0, unlink(dex_location.c_str()));

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeededDefault(&oat_file_assistant,
                               CompilerFilter::kSpeed,
                               /*expected_dexopt_needed=*/false,
                               /*expected_is_vdex_usable=*/false,
                               /*expected_location=*/OatFileAssistant::kLocationNoneOrError,
                               /*expected_legacy_result=*/OatFileAssistant::kNoDexOptNeeded);

  VerifyOptimizationStatusWithInstance(&oat_file_assistant,
                                       "unknown",
                                       "unknown",
                                       "io-error-no-apk",
                                       OatFileAssistant::kLocationNoneOrError);
}

// Case: We have a VDEX file, generated without a boot image, and we now have a boot image.
// Expect: Dexopt only if the target compiler filter >= "speed-profile".
TEST_P(OatFileAssistantTest, ShouldRecompileForImageFromVdex) {
  std::string dex_location = GetScratchDir() + "/TestDex.jar";
  std::string odex_location = GetOdexDir() + "/TestDex.odex";
  std::string vdex_location = GetOdexDir() + "/TestDex.vdex";
  Copy(GetMultiDexSrc1(), dex_location);

  // Compile without a boot image.
  GenerateOdexForTest(dex_location,
                      odex_location,
                      CompilerFilter::kVerify,
                      "install",
                      {"--boot-image=/nonx/boot.art"});

  // Delete the odex file and only keep the vdex.
  ASSERT_EQ(0, unlink(odex_location.c_str()));

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeed,
                        default_trigger_,
                        /*expected_dexopt_needed=*/true,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationOdex);
  EXPECT_EQ(-OatFileAssistant::kDex2OatForFilter,
            oat_file_assistant.GetDexOptNeeded(CompilerFilter::kSpeed));

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeedProfile,
                        default_trigger_,
                        /*expected_dexopt_needed=*/true,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationOdex);
  EXPECT_EQ(-OatFileAssistant::kDex2OatForFilter,
            oat_file_assistant.GetDexOptNeeded(CompilerFilter::kSpeedProfile));

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kVerify,
                        default_trigger_,
                        /*expected_dexopt_needed=*/false,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationOdex);
  EXPECT_EQ(OatFileAssistant::kNoDexOptNeeded,
            oat_file_assistant.GetDexOptNeeded(CompilerFilter::kVerify));
}

// Case: We have an ODEX file, generated without a boot image (filter: "verify"), and we now have a
// boot image.
// Expect: Dexopt only if the target compiler filter >= "speed-profile".
TEST_P(OatFileAssistantTest, ShouldRecompileForImageFromVerify) {
  std::string dex_location = GetScratchDir() + "/TestDex.jar";
  std::string odex_location = GetOdexDir() + "/TestDex.odex";
  std::string vdex_location = GetOdexDir() + "/TestDex.vdex";
  Copy(GetMultiDexSrc1(), dex_location);

  // Compile without a boot image.
  GenerateOdexForTest(dex_location,
                      odex_location,
                      CompilerFilter::kVerify,
                      "install",
                      {"--boot-image=/nonx/boot.art"});

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeed,
                        default_trigger_,
                        /*expected_dexopt_needed=*/true,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationOdex);
  EXPECT_EQ(-OatFileAssistant::kDex2OatForFilter,
            oat_file_assistant.GetDexOptNeeded(CompilerFilter::kSpeed));

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeedProfile,
                        default_trigger_,
                        /*expected_dexopt_needed=*/true,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationOdex);
  EXPECT_EQ(-OatFileAssistant::kDex2OatForFilter,
            oat_file_assistant.GetDexOptNeeded(CompilerFilter::kSpeedProfile));

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kVerify,
                        default_trigger_,
                        /*expected_dexopt_needed=*/false,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationOdex);
  EXPECT_EQ(OatFileAssistant::kNoDexOptNeeded,
            oat_file_assistant.GetDexOptNeeded(CompilerFilter::kVerify));
}

// Case: We have an ODEX file, generated without a boot image (filter: "speed-profile"), and we now
// have a boot image.
// Expect: Dexopt only if the target compiler filter >= "speed-profile".
TEST_P(OatFileAssistantTest, ShouldRecompileForImageFromSpeedProfile) {
  std::string dex_location = GetScratchDir() + "/TestDex.jar";
  std::string odex_location = GetOdexDir() + "/TestDex.odex";
  std::string vdex_location = GetOdexDir() + "/TestDex.vdex";
  Copy(GetMultiDexSrc1(), dex_location);

  // Compile without a boot image.
  GenerateOdexForTest(dex_location,
                      odex_location,
                      CompilerFilter::kSpeedProfile,
                      "install",
                      {"--boot-image=/nonx/boot.art"});

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeed,
                        default_trigger_,
                        /*expected_dexopt_needed=*/true,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationOdex);
  EXPECT_EQ(-OatFileAssistant::kDex2OatForFilter,
            oat_file_assistant.GetDexOptNeeded(CompilerFilter::kSpeed));

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kSpeedProfile,
                        default_trigger_,
                        /*expected_dexopt_needed=*/true,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationOdex);
  EXPECT_EQ(-OatFileAssistant::kDex2OatForFilter,
            oat_file_assistant.GetDexOptNeeded(CompilerFilter::kSpeedProfile));

  VerifyGetDexOptNeeded(&oat_file_assistant,
                        CompilerFilter::kVerify,
                        default_trigger_,
                        /*expected_dexopt_needed=*/false,
                        /*expected_is_vdex_usable=*/true,
                        /*expected_location=*/OatFileAssistant::kLocationOdex);
  EXPECT_EQ(OatFileAssistant::kNoDexOptNeeded,
            oat_file_assistant.GetDexOptNeeded(CompilerFilter::kVerify));
}

// Test that GetLocation of a dex file is the same whether the dex
// filed is backed by an oat file or not.
TEST_F(OatFileAssistantBaseTest, GetDexLocation) {
  std::string dex_location = GetScratchDir() + "/TestDex.jar";
  std::string oat_location = GetOdexDir() + "/TestDex.odex";
  std::string art_location = GetOdexDir() + "/TestDex.art";

  // Start the runtime to initialize the system's class loader.
  Thread::Current()->TransitionFromSuspendedToRunnable();
  runtime_->Start();

  Copy(GetDexSrc1(), dex_location);

  std::vector<std::unique_ptr<const DexFile>> dex_files;
  std::vector<std::string> error_msgs;
  const OatFile* oat_file = nullptr;

  dex_files = Runtime::Current()->GetOatFileManager().OpenDexFilesFromOat(
      dex_location.c_str(),
      Runtime::Current()->GetSystemClassLoader(),
      /*dex_elements=*/nullptr,
      &oat_file,
      &error_msgs);
  ASSERT_EQ(dex_files.size(), 1u) << android::base::Join(error_msgs, "\n");
  EXPECT_EQ(oat_file, nullptr);
  std::string stored_dex_location = dex_files[0]->GetLocation();
  {
    // Create the oat file.
    std::vector<std::string> args;
    args.push_back("--dex-file=" + dex_location);
    args.push_back("--dex-location=TestDex.jar");
    args.push_back("--oat-file=" + oat_location);
    args.push_back("--app-image-file=" + art_location);
    std::string error_msg;
    ASSERT_TRUE(DexoptTest::Dex2Oat(args, &error_msg)) << error_msg;
  }
  dex_files = Runtime::Current()->GetOatFileManager().OpenDexFilesFromOat(
      dex_location.c_str(),
      Runtime::Current()->GetSystemClassLoader(),
      /*dex_elements=*/nullptr,
      &oat_file,
      &error_msgs);
  ASSERT_EQ(dex_files.size(), 1u) << android::base::Join(error_msgs, "\n");
  ASSERT_NE(oat_file, nullptr);
  std::string oat_stored_dex_location = dex_files[0]->GetLocation();
  EXPECT_EQ(oat_stored_dex_location, stored_dex_location);
}

// Test that a dex file on the platform location gets the right hiddenapi domain,
// regardless of whether it has a backing oat file.
TEST_F(OatFileAssistantBaseTest, SystemFrameworkDir) {
  std::string filebase = "OatFileAssistantTestSystemFrameworkDir";
  std::string dex_location = GetAndroidRoot() + "/framework/" + filebase + ".jar";
  Copy(GetDexSrc1(), dex_location);

  std::string odex_dir = GetAndroidRoot() + "/framework/oat/";
  mkdir(odex_dir.c_str(), 0700);
  odex_dir = odex_dir + std::string(GetInstructionSetString(kRuntimeISA));
  mkdir(odex_dir.c_str(), 0700);
  std::string oat_location = odex_dir + "/" + filebase + ".odex";
  std::string vdex_location = odex_dir + "/" + filebase + ".vdex";
  std::string art_location = odex_dir + "/" + filebase + ".art";
  // Clean up in case previous run crashed.
  remove(oat_location.c_str());
  remove(vdex_location.c_str());
  remove(art_location.c_str());

  // Start the runtime to initialize the system's class loader.
  Thread::Current()->TransitionFromSuspendedToRunnable();
  runtime_->Start();

  std::vector<std::unique_ptr<const DexFile>> dex_files_first;
  std::vector<std::unique_ptr<const DexFile>> dex_files_second;
  std::vector<std::string> error_msgs;
  const OatFile* oat_file = nullptr;

  dex_files_first = Runtime::Current()->GetOatFileManager().OpenDexFilesFromOat(
      dex_location.c_str(),
      Runtime::Current()->GetSystemClassLoader(),
      /*dex_elements=*/nullptr,
      &oat_file,
      &error_msgs);
  ASSERT_EQ(dex_files_first.size(), 1u) << android::base::Join(error_msgs, "\n");
  EXPECT_EQ(oat_file, nullptr) << dex_location;
  EXPECT_EQ(dex_files_first[0]->GetOatDexFile(), nullptr);

  // Register the dex file to get a domain.
  {
    ScopedObjectAccess soa(Thread::Current());
    Runtime::Current()->GetClassLinker()->RegisterDexFile(
        *dex_files_first[0],
        soa.Decode<mirror::ClassLoader>(Runtime::Current()->GetSystemClassLoader()));
  }
  std::string stored_dex_location = dex_files_first[0]->GetLocation();
  EXPECT_EQ(dex_files_first[0]->GetHiddenapiDomain(), hiddenapi::Domain::kPlatform);
  {
    // Create the oat file.
    std::vector<std::string> args;
    args.push_back("--dex-file=" + dex_location);
    args.push_back("--dex-location=" + filebase + ".jar");
    args.push_back("--oat-file=" + oat_location);
    args.push_back("--app-image-file=" + art_location);
    std::string error_msg;
    ASSERT_TRUE(DexoptTest::Dex2Oat(args, &error_msg)) << error_msg;
  }
  dex_files_second = Runtime::Current()->GetOatFileManager().OpenDexFilesFromOat(
      dex_location.c_str(),
      Runtime::Current()->GetSystemClassLoader(),
      /*dex_elements=*/nullptr,
      &oat_file,
      &error_msgs);
  ASSERT_EQ(dex_files_second.size(), 1u) << android::base::Join(error_msgs, "\n");
  ASSERT_NE(oat_file, nullptr);
  EXPECT_NE(dex_files_second[0]->GetOatDexFile(), nullptr);
  EXPECT_NE(dex_files_second[0]->GetOatDexFile()->GetOatFile(), nullptr);

  // Register the dex file to get a domain.
  {
    ScopedObjectAccess soa(Thread::Current());
    Runtime::Current()->GetClassLinker()->RegisterDexFile(
        *dex_files_second[0],
        soa.Decode<mirror::ClassLoader>(Runtime::Current()->GetSystemClassLoader()));
  }
  std::string oat_stored_dex_location = dex_files_second[0]->GetLocation();
  EXPECT_EQ(oat_stored_dex_location, stored_dex_location);
  EXPECT_EQ(dex_files_second[0]->GetHiddenapiDomain(), hiddenapi::Domain::kPlatform);
  EXPECT_EQ(0, remove(oat_location.c_str()));
}

// Make sure OAT files that require app images are not loaded as executable.
TEST_F(OatFileAssistantBaseTest, LoadOatNoArt) {
  std::string dex_location = GetScratchDir() + "/TestDex.jar";
  std::string odex_location = GetOdexDir() + "/TestDex.odex";
  std::string art_location = GetOdexDir() + "/TestDex.art";
  Copy(GetDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location,
                      odex_location,
                      CompilerFilter::kSpeed,
                      "install",
                      {
                          "--app-image-file=" + art_location,
                      });

  unlink(art_location.c_str());

  std::vector<std::string> error_msgs;
  const OatFile* oat_file = nullptr;

  // Start the runtime to initialize the system's class loader.
  Thread::Current()->TransitionFromSuspendedToRunnable();
  runtime_->Start();

  const auto dex_files = Runtime::Current()->GetOatFileManager().OpenDexFilesFromOat(
      dex_location.c_str(),
      Runtime::Current()->GetSystemClassLoader(),
      /*dex_elements=*/nullptr,
      &oat_file,
      &error_msgs);

  EXPECT_FALSE(dex_files.empty());
  ASSERT_NE(oat_file, nullptr);
  EXPECT_FALSE(oat_file->IsExecutable());
}

TEST_P(OatFileAssistantTest, GetDexOptNeededWithApexVersions) {
  std::string dex_location = GetScratchDir() + "/TestDex.jar";
  std::string odex_location = GetOdexDir() + "/TestDex.odex";
  Copy(GetDexSrc1(), dex_location);

  // Test that using the current's runtime apex versions works.
  {
    std::string error_msg;
    std::vector<std::string> args;
    args.push_back("--dex-file=" + dex_location);
    args.push_back("--oat-file=" + odex_location);
    args.push_back("--apex-versions=" + Runtime::Current()->GetApexVersions());
    ASSERT_TRUE(Dex2Oat(args, &error_msg)) << error_msg;

    auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

    OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());
    EXPECT_EQ(OatFileAssistant::kOatUpToDate, oat_file_assistant.OdexFileStatus());
  }

  // Test that a subset of apex versions works.
  {
    std::string error_msg;
    std::vector<std::string> args;
    args.push_back("--dex-file=" + dex_location);
    args.push_back("--oat-file=" + odex_location);
    args.push_back("--apex-versions=" + Runtime::Current()->GetApexVersions().substr(0, 1));
    ASSERT_TRUE(Dex2Oat(args, &error_msg)) << error_msg;

    auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

    OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());
    EXPECT_EQ(OatFileAssistant::kOatUpToDate, oat_file_assistant.OdexFileStatus());
  }

  // Test that different apex versions require to recompile.
  {
    std::string error_msg;
    std::vector<std::string> args;
    args.push_back("--dex-file=" + dex_location);
    args.push_back("--oat-file=" + odex_location);
    args.push_back("--apex-versions=/1/2/3/4");
    ASSERT_TRUE(Dex2Oat(args, &error_msg)) << error_msg;

    auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

    OatFileAssistant oat_file_assistant = CreateOatFileAssistant(dex_location.c_str());
    EXPECT_EQ(OatFileAssistant::kOatBootImageOutOfDate, oat_file_assistant.OdexFileStatus());
  }
}

TEST_P(OatFileAssistantTest, Create) {
  std::string dex_location = GetScratchDir() + "/OdexUpToDate.jar";
  std::string odex_location = GetOdexDir() + "/OdexUpToDate.odex";
  Copy(GetDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeed, "install");

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  std::unique_ptr<ClassLoaderContext> context;
  std::string error_msg;
  std::unique_ptr<OatFileAssistant> oat_file_assistant =
      OatFileAssistant::Create(dex_location,
                               GetInstructionSetString(kRuntimeISA),
                               default_context_->EncodeContextForDex2oat(/*base_dir=*/""),
                               /*load_executable=*/false,
                               /*only_load_trusted_executable=*/true,
                               MaybeGetOatFileAssistantContext(),
                               &context,
                               &error_msg);
  ASSERT_NE(oat_file_assistant, nullptr);

  // Verify that the created instance is usable.
  VerifyOptimizationStatusWithInstance(
      oat_file_assistant.get(), "speed", "install", "up-to-date", OatFileAssistant::kLocationOdex);
}

TEST_P(OatFileAssistantTest, CreateWithNullContext) {
  std::string dex_location = GetScratchDir() + "/OdexUpToDate.jar";
  std::string odex_location = GetOdexDir() + "/OdexUpToDate.odex";
  Copy(GetDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeed, "install");

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  std::unique_ptr<ClassLoaderContext> context;
  std::string error_msg;
  std::unique_ptr<OatFileAssistant> oat_file_assistant =
      OatFileAssistant::Create(dex_location,
                               GetInstructionSetString(kRuntimeISA),
                               /*context_str=*/std::nullopt,
                               /*load_executable=*/false,
                               /*only_load_trusted_executable=*/true,
                               MaybeGetOatFileAssistantContext(),
                               &context,
                               &error_msg);
  ASSERT_NE(oat_file_assistant, nullptr);
  ASSERT_EQ(context, nullptr);

  // Verify that the created instance is usable.
  VerifyOptimizationStatusWithInstance(
      oat_file_assistant.get(), "speed", "install", "up-to-date", OatFileAssistant::kLocationOdex);
}

TEST_P(OatFileAssistantTest, ErrorOnInvalidIsaString) {
  std::string dex_location = GetScratchDir() + "/OdexUpToDate.jar";
  std::string odex_location = GetOdexDir() + "/OdexUpToDate.odex";
  Copy(GetDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeed, "install");

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  std::unique_ptr<ClassLoaderContext> context;
  std::string error_msg;
  EXPECT_EQ(OatFileAssistant::Create(dex_location,
                                     /*isa_str=*/"foo",
                                     default_context_->EncodeContextForDex2oat(/*base_dir=*/""),
                                     /*load_executable=*/false,
                                     /*only_load_trusted_executable=*/true,
                                     MaybeGetOatFileAssistantContext(),
                                     &context,
                                     &error_msg),
            nullptr);
  EXPECT_EQ(error_msg, "Instruction set 'foo' is invalid");
}

TEST_P(OatFileAssistantTest, ErrorOnInvalidContextString) {
  std::string dex_location = GetScratchDir() + "/OdexUpToDate.jar";
  std::string odex_location = GetOdexDir() + "/OdexUpToDate.odex";
  Copy(GetDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeed, "install");

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  std::unique_ptr<ClassLoaderContext> context;
  std::string error_msg;
  EXPECT_EQ(OatFileAssistant::Create(dex_location,
                                     GetInstructionSetString(kRuntimeISA),
                                     /*context_str=*/"foo",
                                     /*load_executable=*/false,
                                     /*only_load_trusted_executable=*/true,
                                     MaybeGetOatFileAssistantContext(),
                                     &context,
                                     &error_msg),
            nullptr);
  EXPECT_EQ(error_msg, "Class loader context 'foo' is invalid");
}

TEST_P(OatFileAssistantTest, ErrorOnInvalidContextFile) {
  std::string dex_location = GetScratchDir() + "/OdexUpToDate.jar";
  std::string odex_location = GetOdexDir() + "/OdexUpToDate.odex";
  Copy(GetDexSrc1(), dex_location);
  GenerateOdexForTest(dex_location, odex_location, CompilerFilter::kSpeed, "install");

  // Create a broken context file.
  std::string context_location = GetScratchDir() + "/BrokenContext.jar";
  std::ofstream output(context_location);
  output.close();

  auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();

  std::unique_ptr<ClassLoaderContext> context;
  std::string error_msg;
  EXPECT_EQ(OatFileAssistant::Create(dex_location,
                                     GetInstructionSetString(kRuntimeISA),
                                     /*context_str=*/"PCL[" + context_location + "]",
                                     /*load_executable=*/false,
                                     /*only_load_trusted_executable=*/true,
                                     MaybeGetOatFileAssistantContext(),
                                     &context,
                                     &error_msg),
            nullptr);
  EXPECT_EQ(error_msg,
            "Failed to load class loader context files for '" + dex_location +
                "' with context 'PCL[" + context_location + "]'");
}

// Verifies that `OatFileAssistant::ValidateBootClassPathChecksums` accepts the checksum string
// produced by `gc::space::ImageSpace::GetBootClassPathChecksums`.
TEST_P(OatFileAssistantTest, ValidateBootClassPathChecksums) {
  std::string error_msg;
  auto create_and_verify = [&]() {
    std::string checksums = gc::space::ImageSpace::GetBootClassPathChecksums(
        ArrayRef<gc::space::ImageSpace* const>(runtime_->GetHeap()->GetBootImageSpaces()),
        ArrayRef<const DexFile* const>(runtime_->GetClassLinker()->GetBootClassPath()));
    std::string bcp_locations = android::base::Join(runtime_->GetBootClassPathLocations(), ':');

    ofa_context_ = CreateOatFileAssistantContext();
    auto scoped_maybe_without_runtime = ScopedMaybeWithoutRuntime();
    return OatFileAssistant::ValidateBootClassPathChecksums(
        ofa_context_.get(), kRuntimeISA, checksums, bcp_locations, &error_msg);
  };

  ASSERT_TRUE(create_and_verify()) << error_msg;

  for (const std::string& src : {GetDexSrc1(), GetDexSrc2()}) {
    ASSERT_TRUE(InsertNewBootClasspathEntry(src, &error_msg)) << error_msg;
    ASSERT_TRUE(create_and_verify()) << error_msg;
  }
}

// TODO: More Tests:
//  * Test class linker falls back to unquickened dex for DexNoOat
//  * Test class linker falls back to unquickened dex for MultiDexNoOat
//  * Test using secondary isa
//  * Test for status of oat while oat is being generated (how?)
//  * Test case where 32 and 64 bit boot class paths differ,
//      and we ask IsInBootClassPath for a class in exactly one of the 32 or
//      64 bit boot class paths.
//  * Test unexpected scenarios (?):
//    - Dex is stripped, don't have odex.
//    - Oat file corrupted after status check, before reload unexecutable
//    because it's unrelocated and no dex2oat

INSTANTIATE_TEST_SUITE_P(WithOrWithoutRuntime, OatFileAssistantTest, testing::Values(true, false));

}  // namespace art
