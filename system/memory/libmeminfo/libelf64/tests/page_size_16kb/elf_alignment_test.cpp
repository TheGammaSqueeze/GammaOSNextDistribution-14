/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <elf.h>
#include <mntent.h>
#include <gtest/gtest.h>

#include <set>

#include <libelf64/iter.h>
#include <libdm/dm.h>

#include <android/api-level.h>
#include <android-base/properties.h>
#include <android-base/strings.h>

constexpr char kLowRamProp[] = "ro.config.low_ram";
constexpr char kProductMaxPageSizeProp[] = "ro.product.cpu.pagesize.max";
constexpr char kVendorApiLevelProp[] = "ro.vendor.api_level";
// 64KB by default (unsupported devices must explicitly opt-out)
constexpr int kRequiredMaxSupportedPageSize = 65536;

static std::set<std::string> GetMounts() {
    std::unique_ptr<std::FILE, int (*)(std::FILE*)> fp(setmntent("/proc/mounts", "re"), endmntent);
    std::set<std::string> exclude ({ "/", "/config", "/data", "/data_mirror", "/dev",
                                          "/linkerconfig", "/mnt", "/proc", "/storage", "/sys" });
    std::set<std::string> mounts;

    if (fp == nullptr) {
      return mounts;
    }

    mntent* mentry;
    while ((mentry = getmntent(fp.get())) != nullptr) {
      std::string mount_dir(mentry->mnt_dir);

      std::string dir = "/" + android::base::Split(mount_dir, "/")[1];

      if (exclude.find(dir) != exclude.end()) {
        continue;
      }

      mounts.insert(dir);
    }

    return mounts;
}

class ElfAlignmentTest :public ::testing::TestWithParam<std::string> {
  protected:
    static void LoadAlignmentCb(const android::elf64::Elf64Binary& elf) {
      for (int i = 0; i < elf.phdrs.size(); i++) {
        Elf64_Phdr phdr = elf.phdrs[i];

        if (phdr.p_type != PT_LOAD) {
          continue;
        }

        uint64_t p_align = phdr.p_align;

        EXPECT_EQ(p_align, kRequiredMaxSupportedPageSize)
            << " " << elf.path << " is not 64KB aligned";
      }
    };

    static bool IsLowRamDevice() {
      return android::base::GetBoolProperty(kLowRamProp, false);
    }

    static int MaxPageSizeSupported() {
      return android::base::GetIntProperty(kProductMaxPageSizeProp,
                                           kRequiredMaxSupportedPageSize);
    }

    static int VendorApiLevel() {
      // "ro.vendor.api_level" is added in Android T. Undefined indicates S or below
      return android::base::GetIntProperty(kVendorApiLevelProp, __ANDROID_API_S__);
    }

    void SetUp() override {
      if (VendorApiLevel() < __ANDROID_API_V__) {
        GTEST_SKIP() << "16kB support is only required on V and later releases.";
      } else if (IsLowRamDevice()) {
        GTEST_SKIP() << "Low Ram devices only support 4kB page size";
      } else if (MaxPageSizeSupported()) {
        GTEST_SKIP() << "Device opted-out of 16kB page size support";
      }
    }
};

TEST_P(ElfAlignmentTest, VerifyLoadSegmentAlignment) {
  android::elf64::ForEachElf64FromDir(GetParam(), &LoadAlignmentCb);
}

INSTANTIATE_TEST_SUITE_P(ElfTestPartitionsAligned, ElfAlignmentTest,
                         ::testing::ValuesIn(GetMounts()));

int main(int argc, char* argv[]) {
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
