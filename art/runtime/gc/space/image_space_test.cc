/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include <gtest/gtest.h>

#include "android-base/logging.h"
#include "android-base/stringprintf.h"
#include "android-base/strings.h"
#include "base/globals.h"
#include "base/stl_util.h"
#include "class_linker.h"
#include "dex/utf.h"
#include "dexopt_test.h"
#include "intern_table-inl.h"
#include "noop_compiler_callbacks.h"
#include "oat/oat_file.h"

namespace art HIDDEN {
namespace gc {
namespace space {

class ImageSpaceTest : public CommonRuntimeTest {
 protected:
  void SetUpRuntimeOptions(RuntimeOptions* options) override {
    // Disable relocation.
    options->emplace_back("-Xnorelocate", nullptr);
  }

  std::string GetFilenameBase(const std::string& full_path) {
    size_t slash_pos = full_path.rfind('/');
    CHECK_NE(std::string::npos, slash_pos);
    size_t dot_pos = full_path.rfind('.');
    CHECK_NE(std::string::npos, dot_pos);
    CHECK_GT(dot_pos, slash_pos + 1u);
    return full_path.substr(slash_pos + 1u, dot_pos - (slash_pos + 1u));
  }
};

TEST_F(ImageSpaceTest, StringDeduplication) {
  const char* const kBaseNames[] = {"Extension1", "Extension2"};

  ScratchDir scratch;
  const std::string& scratch_dir = scratch.GetPath();
  std::string image_dir = scratch_dir + GetInstructionSetString(kRuntimeISA);
  int mkdir_result = mkdir(image_dir.c_str(), 0700);
  ASSERT_EQ(0, mkdir_result);

  // Prepare boot class path variables.
  std::vector<std::string> bcp = GetLibCoreDexFileNames();
  std::vector<std::string> bcp_locations = GetLibCoreDexLocations();
  CHECK_EQ(bcp.size(), bcp_locations.size());
  std::string base_bcp_string = android::base::Join(bcp, ':');
  std::string base_bcp_locations_string = android::base::Join(bcp_locations, ':');
  std::string base_image_location = GetImageLocation();

  // Compile the two extensions independently.
  std::vector<std::string> extension_image_locations;
  for (const char* base_name : kBaseNames) {
    std::string jar_name = GetTestDexFileName(base_name);
    ArrayRef<const std::string> dex_files(&jar_name, /*size=*/1u);
    ScratchFile profile_file;
    GenerateBootProfile(dex_files, profile_file.GetFile());
    std::vector<std::string> extra_args = {
        "--profile-file=" + profile_file.GetFilename(),
        "--runtime-arg",
        ART_FORMAT("-Xbootclasspath:{}:{}", base_bcp_string, jar_name),
        "--runtime-arg",
        ART_FORMAT("-Xbootclasspath-locations:{}:{}", base_bcp_locations_string, jar_name),
        "--boot-image=" + base_image_location,
    };
    std::string prefix = GetFilenameBase(base_image_location);
    std::string error_msg;
    bool success =
        CompileBootImage(extra_args, ART_FORMAT("{}/{}", image_dir, prefix), dex_files, &error_msg);
    ASSERT_TRUE(success) << error_msg;
    bcp.push_back(jar_name);
    bcp_locations.push_back(jar_name);
    extension_image_locations.push_back(scratch_dir + prefix + '-' + GetFilenameBase(jar_name) +
                                        ".art");
  }

  // Also compile the second extension as an app with app image.
  const char* app_base_name = kBaseNames[std::size(kBaseNames) - 1u];
  std::string app_jar_name = GetTestDexFileName(app_base_name);
  std::string app_odex_name = scratch_dir + app_base_name + ".odex";
  std::string app_image_name = scratch_dir + app_base_name + ".art";
  {
    ArrayRef<const std::string> dex_files(&app_jar_name, /*size=*/1u);
    ScratchFile profile_file;
    GenerateProfile(dex_files, profile_file.GetFile());
    std::vector<std::string> argv;
    std::string error_msg;
    bool success = StartDex2OatCommandLine(&argv, &error_msg, /*use_runtime_bcp_and_image=*/false);
    ASSERT_TRUE(success) << error_msg;
    argv.insert(argv.end(),
                {
                    "--profile-file=" + profile_file.GetFilename(),
                    "--runtime-arg",
                    "-Xbootclasspath:" + base_bcp_string,
                    "--runtime-arg",
                    "-Xbootclasspath-locations:" + base_bcp_locations_string,
                    "--boot-image=" + base_image_location,
                    "--dex-file=" + app_jar_name,
                    "--dex-location=" + app_jar_name,
                    "--oat-file=" + app_odex_name,
                    "--app-image-file=" + app_image_name,
                    "--initialize-app-image-classes=true",
                });
    success = RunDex2Oat(argv, &error_msg);
    ASSERT_TRUE(success) << error_msg;
  }

  std::vector<std::string> full_image_locations;
  std::vector<std::unique_ptr<gc::space::ImageSpace>> boot_image_spaces;
  MemMap extra_reservation;
  auto load_boot_image = [&]() REQUIRES_SHARED(Locks::mutator_lock_) {
    boot_image_spaces.clear();
    extra_reservation = MemMap::Invalid();
    return ImageSpace::LoadBootImage(
        bcp,
        bcp_locations,
        /*boot_class_path_files=*/{},
        /*boot_class_path_image_files=*/{},
        /*boot_class_path_vdex_files=*/{},
        /*boot_class_path_oat_files=*/{},
        full_image_locations,
        kRuntimeISA,
        /*relocate=*/false,
        /*executable=*/true,
        /*extra_reservation_size=*/0u,
        /*allow_in_memory_compilation=*/false,
        Runtime::GetApexVersions(ArrayRef<const std::string>(bcp_locations)),
        &boot_image_spaces,
        &extra_reservation);
  };

  const char test_string[] = "SharedBootImageExtensionTestString";
  size_t test_string_length = std::size(test_string) - 1u;  // Equals UTF-16 length.
  uint32_t hash = InternTable::Utf8String::Hash(test_string_length, test_string);
  InternTable::Utf8String utf8_test_string(test_string_length, test_string);
  auto contains_test_string = [utf8_test_string,
                               hash](ImageSpace* space) REQUIRES_SHARED(Locks::mutator_lock_) {
    const ImageHeader& image_header = space->GetImageHeader();
    if (image_header.GetInternedStringsSection().Size() != 0u) {
      const uint8_t* data = space->Begin() + image_header.GetInternedStringsSection().Offset();
      size_t read_count;
      InternTable::UnorderedSet temp_set(data, /*make_copy_of_data=*/false, &read_count);
      return temp_set.FindWithHash(utf8_test_string, hash) != temp_set.end();
    } else {
      return false;
    }
  };

  // Load extensions and test for the presence of the test string.
  ScopedObjectAccess soa(Thread::Current());
  ASSERT_EQ(2u, extension_image_locations.size());
  full_image_locations = {
      base_image_location, extension_image_locations[0], extension_image_locations[1]};
  bool success = load_boot_image();
  ASSERT_TRUE(success);
  ASSERT_EQ(bcp.size(), boot_image_spaces.size());
  EXPECT_TRUE(contains_test_string(boot_image_spaces[boot_image_spaces.size() - 2u].get()));
  // The string in the second extension should be replaced and removed from interned string section.
  EXPECT_FALSE(contains_test_string(boot_image_spaces[boot_image_spaces.size() - 1u].get()));

  // Reload extensions in reverse order and test for the presence of the test string.
  std::swap(bcp[bcp.size() - 2u], bcp[bcp.size() - 1u]);
  std::swap(bcp_locations[bcp_locations.size() - 2u], bcp_locations[bcp_locations.size() - 1u]);
  full_image_locations = {
      base_image_location, extension_image_locations[1], extension_image_locations[0]};
  success = load_boot_image();
  ASSERT_TRUE(success);
  ASSERT_EQ(bcp.size(), boot_image_spaces.size());
  EXPECT_TRUE(contains_test_string(boot_image_spaces[boot_image_spaces.size() - 2u].get()));
  // The string in the second extension should be replaced and removed from interned string section.
  EXPECT_FALSE(contains_test_string(boot_image_spaces[boot_image_spaces.size() - 1u].get()));

  // Reload the image without the second extension.
  bcp.erase(bcp.end() - 2u);
  bcp_locations.erase(bcp_locations.end() - 2u);
  full_image_locations = {base_image_location, extension_image_locations[0]};
  success = load_boot_image();
  ASSERT_TRUE(success);
  ASSERT_EQ(bcp.size(), boot_image_spaces.size());
  ASSERT_TRUE(contains_test_string(boot_image_spaces[boot_image_spaces.size() - 1u].get()));

  // Load the app odex file and app image.
  std::string error_msg;
  std::unique_ptr<OatFile> odex_file(OatFile::Open(/*zip_fd=*/-1,
                                                   app_odex_name,
                                                   app_odex_name,
                                                   /*executable=*/false,
                                                   /*low_4gb=*/false,
                                                   app_jar_name,
                                                   &error_msg));
  ASSERT_TRUE(odex_file != nullptr) << error_msg;
  std::vector<ImageSpace*> non_owning_boot_image_spaces =
      MakeNonOwningPointerVector(boot_image_spaces);
  std::unique_ptr<ImageSpace> app_image_space =
      ImageSpace::CreateFromAppImage(app_image_name.c_str(),
                                     odex_file.get(),
                                     ArrayRef<ImageSpace* const>(non_owning_boot_image_spaces),
                                     &error_msg);
  ASSERT_TRUE(app_image_space != nullptr) << error_msg;

  // The string in the app image should be replaced and removed from interned string section.
  EXPECT_FALSE(contains_test_string(app_image_space.get()));
}

TEST_F(DexoptTest, ValidateOatFile) {
  std::string dex1 = GetScratchDir() + "/Dex1.jar";
  std::string multidex1 = GetScratchDir() + "/MultiDex1.jar";
  std::string dex2 = GetScratchDir() + "/Dex2.jar";
  std::string oat_location = GetScratchDir() + "/Oat.oat";

  Copy(GetDexSrc1(), dex1);
  Copy(GetMultiDexSrc1(), multidex1);
  Copy(GetDexSrc2(), dex2);

  std::string error_msg;
  std::vector<std::string> args;
  args.push_back("--dex-file=" + dex1);
  args.push_back("--dex-file=" + multidex1);
  args.push_back("--dex-file=" + dex2);
  args.push_back("--oat-file=" + oat_location);
  ASSERT_TRUE(Dex2Oat(args, &error_msg)) << error_msg;

  std::unique_ptr<OatFile> oat(OatFile::Open(/*zip_fd=*/-1,
                                             oat_location,
                                             oat_location,
                                             /*executable=*/false,
                                             /*low_4gb=*/false,
                                             &error_msg));
  ASSERT_TRUE(oat != nullptr) << error_msg;

  {
    // Test opening the oat file also with explicit dex filenames.
    std::vector<std::string> dex_filenames{dex1, multidex1, dex2};
    std::unique_ptr<OatFile> oat2(OatFile::Open(/*zip_fd=*/-1,
                                                oat_location,
                                                oat_location,
                                                /*executable=*/false,
                                                /*low_4gb=*/false,
                                                ArrayRef<const std::string>(dex_filenames),
                                                /*dex_files=*/{},
                                                /*reservation=*/nullptr,
                                                &error_msg));
    ASSERT_TRUE(oat2 != nullptr) << error_msg;
  }

  // Originally all the dex checksums should be up to date.
  EXPECT_TRUE(ImageSpace::ValidateOatFile(*oat, &error_msg)) << error_msg;

  // Invalidate the dex1 checksum.
  Copy(GetDexSrc2(), dex1);
  EXPECT_FALSE(ImageSpace::ValidateOatFile(*oat, &error_msg));

  // Restore the dex1 checksum.
  Copy(GetDexSrc1(), dex1);
  EXPECT_TRUE(ImageSpace::ValidateOatFile(*oat, &error_msg)) << error_msg;

  // Invalidate the non-main multidex checksum.
  Copy(GetMultiDexSrc2(), multidex1);
  EXPECT_FALSE(ImageSpace::ValidateOatFile(*oat, &error_msg));

  // Restore the multidex checksum.
  Copy(GetMultiDexSrc1(), multidex1);
  EXPECT_TRUE(ImageSpace::ValidateOatFile(*oat, &error_msg)) << error_msg;

  // Invalidate the dex2 checksum.
  Copy(GetDexSrc1(), dex2);
  EXPECT_FALSE(ImageSpace::ValidateOatFile(*oat, &error_msg));

  // restore the dex2 checksum.
  Copy(GetDexSrc2(), dex2);
  EXPECT_TRUE(ImageSpace::ValidateOatFile(*oat, &error_msg)) << error_msg;

  // Replace the multidex file with a non-multidex file.
  Copy(GetDexSrc1(), multidex1);
  EXPECT_FALSE(ImageSpace::ValidateOatFile(*oat, &error_msg));

  // Restore the multidex file
  Copy(GetMultiDexSrc1(), multidex1);
  EXPECT_TRUE(ImageSpace::ValidateOatFile(*oat, &error_msg)) << error_msg;

  // Replace dex1 with a multidex file.
  Copy(GetMultiDexSrc1(), dex1);
  EXPECT_FALSE(ImageSpace::ValidateOatFile(*oat, &error_msg));

  // Restore the dex1 file.
  Copy(GetDexSrc1(), dex1);
  EXPECT_TRUE(ImageSpace::ValidateOatFile(*oat, &error_msg)) << error_msg;

  // Remove the dex2 file.
  EXPECT_EQ(0, unlink(dex2.c_str()));
  EXPECT_FALSE(ImageSpace::ValidateOatFile(*oat, &error_msg));

  // Restore the dex2 file.
  Copy(GetDexSrc2(), dex2);
  EXPECT_TRUE(ImageSpace::ValidateOatFile(*oat, &error_msg)) << error_msg;

  // Remove the multidex file.
  EXPECT_EQ(0, unlink(multidex1.c_str()));
  EXPECT_FALSE(ImageSpace::ValidateOatFile(*oat, &error_msg));
}

template <bool kImage, bool kRelocate>
class ImageSpaceLoadingTest : public CommonRuntimeTest {
 protected:
  void SetUpRuntimeOptions(RuntimeOptions* options) override {
    missing_image_base_ = std::make_unique<ScratchFile>();
    std::string image_location = PrepareImageLocation();
    options->emplace_back(android::base::StringPrintf("-Ximage:%s", image_location.c_str()),
                          nullptr);
    options->emplace_back(kRelocate ? "-Xrelocate" : "-Xnorelocate", nullptr);
    options->emplace_back("-Xallowinmemorycompilation", nullptr);

    // We want to test the relocation behavior of ImageSpace. As such, don't pretend we're a
    // compiler.
    callbacks_.reset();

    // Clear DEX2OATBOOTCLASSPATH environment variable used for boot image compilation.
    // We don't want that environment variable to affect the behavior of this test.
    CHECK(old_dex2oat_bcp_ == nullptr);
    const char* old_dex2oat_bcp = getenv("DEX2OATBOOTCLASSPATH");
    if (old_dex2oat_bcp != nullptr) {
      old_dex2oat_bcp_.reset(strdup(old_dex2oat_bcp));
      CHECK(old_dex2oat_bcp_ != nullptr);
      unsetenv("DEX2OATBOOTCLASSPATH");
    }
  }

  void TearDown() override {
    if (old_dex2oat_bcp_ != nullptr) {
      int result = setenv("DEX2OATBOOTCLASSPATH", old_dex2oat_bcp_.get(), /* replace */ 0);
      CHECK_EQ(result, 0);
      old_dex2oat_bcp_.reset();
    }
    missing_image_base_.reset();
  }

  virtual std::string PrepareImageLocation() {
    std::string image_location = GetCoreArtLocation();
    if (!kImage) {
      image_location = missing_image_base_->GetFilename() + ".art";
    }
    return image_location;
  }

  void CheckImageSpaceAndOatFile(size_t space_count) {
    const std::vector<ImageSpace*>& image_spaces =
        Runtime::Current()->GetHeap()->GetBootImageSpaces();
    ASSERT_EQ(image_spaces.size(), space_count);

    for (size_t i = 0; i < space_count; i++) {
      // This test does not support multi-image compilation.
      ASSERT_NE(image_spaces[i]->GetImageHeader().GetImageReservationSize(), 0u);

      const OatFile* oat_file = image_spaces[i]->GetOatFile();
      ASSERT_TRUE(oat_file != nullptr);

      // Compiled by JIT Zygote.
      EXPECT_EQ(oat_file->GetCompilerFilter(), CompilerFilter::Filter::kVerify);
    }
  }

  std::unique_ptr<ScratchFile> missing_image_base_;

 private:
  UniqueCPtr<const char[]> old_dex2oat_bcp_;
};

using ImageSpaceNoDex2oatTest = ImageSpaceLoadingTest</*kImage=*/true, /*kRelocate=*/true>;
TEST_F(ImageSpaceNoDex2oatTest, Test) {
  EXPECT_FALSE(Runtime::Current()->GetHeap()->GetBootImageSpaces().empty());
}

using ImageSpaceNoRelocateNoDex2oatTest =
    ImageSpaceLoadingTest</*kImage=*/true, /*kRelocate=*/false>;
TEST_F(ImageSpaceNoRelocateNoDex2oatTest, Test) {
  EXPECT_FALSE(Runtime::Current()->GetHeap()->GetBootImageSpaces().empty());
}

using ImageSpaceNoImageNoProfileTest = ImageSpaceLoadingTest</*kImage=*/false, /*kRelocate=*/true>;
TEST_F(ImageSpaceNoImageNoProfileTest, Test) {
  // Imageless mode.
  EXPECT_TRUE(Runtime::Current()->GetHeap()->GetBootImageSpaces().empty());
}

class ImageSpaceLoadingSingleComponentWithProfilesTest
    : public ImageSpaceLoadingTest</*kImage=*/false, /*kRelocate=*/true> {
 protected:
  std::string PrepareImageLocation() override {
    std::string image_location = missing_image_base_->GetFilename() + ".art";
    // Compiling the primary boot image into a single image is not allowed on host.
    if (kIsTargetBuild) {
      std::vector<std::string> dex_files(GetLibCoreDexFileNames());
      profile1_ = std::make_unique<ScratchFile>();
      GenerateBootProfile(ArrayRef<const std::string>(dex_files),
                          profile1_->GetFile(),
                          /*method_frequency=*/6,
                          /*type_frequency=*/6);
      profile2_ = std::make_unique<ScratchFile>();
      GenerateBootProfile(ArrayRef<const std::string>(dex_files),
                          profile2_->GetFile(),
                          /*method_frequency=*/8,
                          /*type_frequency=*/8);
      image_location += "!" + profile1_->GetFilename() + "!" + profile2_->GetFilename();
    }
    // "/path/to/image.art!/path/to/profile1!/path/to/profile2"
    return image_location;
  }

 private:
  std::unique_ptr<ScratchFile> profile1_;
  std::unique_ptr<ScratchFile> profile2_;
};

TEST_F(ImageSpaceLoadingSingleComponentWithProfilesTest, Test) {
  // Compiling the primary boot image into a single image is not allowed on host.
  TEST_DISABLED_FOR_HOST();

  CheckImageSpaceAndOatFile(/*space_count=*/1);
}

class ImageSpaceLoadingMultipleComponentsWithProfilesTest
    : public ImageSpaceLoadingTest</*kImage=*/false, /*kRelocate=*/true> {
 protected:
  std::string PrepareImageLocation() override {
    std::vector<std::string> dex_files(GetLibCoreDexFileNames());
    CHECK_GE(dex_files.size(), 2);
    std::string image_location_1 = missing_image_base_->GetFilename() + ".art";
    std::string image_location_2 =
        missing_image_base_->GetFilename() + "-" + Stem(dex_files[dex_files.size() - 1]) + ".art";
    // Compiling the primary boot image into a single image is not allowed on host.
    if (kIsTargetBuild) {
      profile1_ = std::make_unique<ScratchFile>();
      GenerateBootProfile(ArrayRef<const std::string>(dex_files).SubArray(
                              /*pos=*/0, /*length=*/dex_files.size() - 1),
                          profile1_->GetFile(),
                          /*method_frequency=*/6,
                          /*type_frequency=*/6);
      image_location_1 += "!" + profile1_->GetFilename();
      profile2_ = std::make_unique<ScratchFile>();
      GenerateBootProfile(ArrayRef<const std::string>(dex_files).SubArray(
                              /*pos=*/dex_files.size() - 1, /*length=*/1),
                          profile2_->GetFile(),
                          /*method_frequency=*/8,
                          /*type_frequency=*/8);
      image_location_2 += "!" + profile2_->GetFilename();
    }
    // "/path/to/image.art!/path/to/profile1:/path/to/image-lastdex.art!/path/to/profile2"
    return image_location_1 + ":" + image_location_2;
  }

  std::string Stem(std::string filename) {
    size_t last_slash = filename.rfind('/');
    if (last_slash != std::string::npos) {
      filename = filename.substr(last_slash + 1);
    }
    size_t last_dot = filename.rfind('.');
    if (last_dot != std::string::npos) {
      filename.resize(last_dot);
    }
    return filename;
  }

 private:
  std::unique_ptr<ScratchFile> profile1_;
  std::unique_ptr<ScratchFile> profile2_;
};

TEST_F(ImageSpaceLoadingMultipleComponentsWithProfilesTest, Test) {
  // Compiling the primary boot image into a single image is not allowed on host.
  TEST_DISABLED_FOR_HOST();

  CheckImageSpaceAndOatFile(/*space_count=*/1);
}

}  // namespace space
}  // namespace gc
}  // namespace art
