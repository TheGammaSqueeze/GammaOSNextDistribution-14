/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <ftw.h>
#include <unistd.h>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <fs_mgr.h>
#include <gtest/gtest.h>
#include <liblp/liblp.h>
#include <vintf/VintfObject.h>
#include <vintf/parse_string.h>

using namespace std::literals;

namespace {

void VerifyDlkmPartition(const std::string &name) {
  const auto TAG = __FUNCTION__ + "("s + name + ")";

  const auto dlkm_symlink = "/" + name + "/lib/modules";
  const auto dlkm_partition = name + "_dlkm";
  const auto dlkm_directory = "/" + dlkm_partition + "/lib/modules";

  // Check existence of /{name}/lib/modules.
  if (access(dlkm_symlink.c_str(), F_OK)) {
    if (errno == ENOENT) {
      GTEST_LOG_(INFO) << TAG << ": '" << dlkm_symlink
                       << "' doesn't exist, skip checking it.";
      SUCCEED();
    } else {
      ADD_FAILURE() << "access(" << dlkm_symlink << "): " << strerror(errno);
    }
    return;
  }

  // If it exists then make sure it is a directory.
  struct stat st;
  ASSERT_EQ(0, stat(dlkm_symlink.c_str(), &st))
      << "stat(" << dlkm_symlink << "): " << strerror(errno);
  if (!S_ISDIR(st.st_mode)) {
    ADD_FAILURE() << "'" << dlkm_symlink << "' is not a directory.";
    return;
  }

  // If it is a directory then check if it is empty or not.
  auto not_empty_callback = [](const char *, const struct stat *, int) {
    return 1;
  };
  int ret = ftw(dlkm_symlink.c_str(), not_empty_callback, 128);
  ASSERT_NE(-1, ret) << "ftw(" << dlkm_symlink << "): " << strerror(errno);

  if (ret == 0) {
    // ftw() returns without visiting any file, so the directory must be empty.
    GTEST_LOG_(INFO) << TAG << ": '" << dlkm_symlink
                     << "' is empty directory, skip checking it.";
    SUCCEED();
    return;
  }
  // Otherwise ftw() must had returned 1, which means the callback is called at
  // least once, so /{name}/lib/modules must not be empty.
  ASSERT_EQ(1, ret);

  // We want to ensure /{name}/lib/modules symlinks to /{name}_dlkm/lib/modules.
  ASSERT_EQ(0, lstat(dlkm_symlink.c_str(), &st))
      << "lstat(" << dlkm_symlink << "): " << strerror(errno);
  if (!S_ISLNK(st.st_mode)) {
    ADD_FAILURE() << "'" << dlkm_symlink << "' is not a symlink.";
    return;
  }

  std::string link_target;
  ASSERT_TRUE(android::base::Readlink(dlkm_symlink, &link_target))
      << "readlink(" << dlkm_symlink << "): " << strerror(errno);
  if (link_target != dlkm_directory) {
    ADD_FAILURE() << "'" << dlkm_symlink << "' must be a symlink pointing at '"
                  << dlkm_directory << "'.";
  } else {
    GTEST_LOG_(INFO) << TAG << ": '" << dlkm_symlink << "' -> '"
                     << dlkm_directory << "'.";
  }

  // Ensure {name}_dlkm is a logical partition.
  const auto super_device = fs_mgr_get_super_partition_name();
  const auto slot_suffix = fs_mgr_get_slot_suffix();
  const auto slot_number =
      android::fs_mgr::SlotNumberForSlotSuffix(slot_suffix);
  auto lp_metadata = android::fs_mgr::ReadMetadata(super_device, slot_number);
  ASSERT_NE(nullptr, lp_metadata)
      << "ReadMetadata(" << super_device << "): " << strerror(errno);
  auto lp_partition = android::fs_mgr::FindPartition(
      *lp_metadata, dlkm_partition + slot_suffix);
  EXPECT_NE(nullptr, lp_partition)
      << "Cannot find logical partition of '" << dlkm_partition << "'";
}

}  // namespace

class DlkmPartitionTest : public testing::Test {
 protected:
  void SetUp() override {
    // Fetch device runtime information.
    runtime_info = android::vintf::VintfObject::GetRuntimeInfo();
    ASSERT_NE(nullptr, runtime_info);

    product_first_api_level =
        android::base::GetIntProperty("ro.product.first_api_level", 0);
    ASSERT_NE(0, product_first_api_level)
        << "ro.product.first_api_level is undefined.";

    const auto board_api_level = android::base::GetIntProperty(
        "ro.board.api_level", __ANDROID_API_FUTURE__);
    const auto board_first_api_level = android::base::GetIntProperty(
        "ro.board.first_api_level", __ANDROID_API_FUTURE__);
    vendor_api_level = android::base::GetIntProperty(
        "ro.vendor.api_level",
        std::min(product_first_api_level,
                 std::max(board_api_level, board_first_api_level)));
    ASSERT_NE(0, vendor_api_level) << "ro.vendor.api_level is undefined.";
  }

  std::shared_ptr<const android::vintf::RuntimeInfo> runtime_info;
  int vendor_api_level;
  int product_first_api_level;
};

TEST_F(DlkmPartitionTest, VendorDlkmPartition) {
  if (vendor_api_level < __ANDROID_API_S__) {
    GTEST_SKIP()
        << "Exempt from vendor_dlkm partition test. ro.vendor.api_level ("
        << vendor_api_level << ") < " << __ANDROID_API_S__;
  }
  // Only enforce this test on products launched with Android T and later.
  if (product_first_api_level < __ANDROID_API_T__) {
    GTEST_SKIP() << "Exempt from vendor_dlkm partition test. "
                    "ro.product.first_api_level ("
                 << product_first_api_level << ") < " << __ANDROID_API_T__;
  }
  if (runtime_info->kernelVersion().dropMinor() !=
          android::vintf::Version{5, 4} &&
      runtime_info->kernelVersion().dropMinor() <
          android::vintf::Version{5, 10}) {
    GTEST_SKIP() << "Exempt from vendor_dlkm partition test. kernel: "
                 << runtime_info->kernelVersion();
  }
  ASSERT_NO_FATAL_FAILURE(VerifyDlkmPartition("vendor"));
  ASSERT_NO_FATAL_FAILURE(VerifyDlkmPartition("odm"));
}

TEST_F(DlkmPartitionTest, SystemDlkmPartition) {
  if (vendor_api_level < __ANDROID_API_T__) {
    GTEST_SKIP()
        << "Exempt from system_dlkm partition test. ro.vendor.api_level ("
        << vendor_api_level << ") < " << __ANDROID_API_T__;
  }
  if (runtime_info->kernelVersion().dropMinor() <
      android::vintf::Version{5, 10}) {
    GTEST_SKIP() << "Exempt from system_dlkm partition test. kernel: "
                 << runtime_info->kernelVersion();
  }
  ASSERT_NO_FATAL_FAILURE(VerifyDlkmPartition("system"));
}

int main(int argc, char *argv[]) {
  ::testing::InitGoogleTest(&argc, argv);
  android::base::InitLogging(argv, android::base::StderrLogger);
  return RUN_ALL_TESTS();
}
