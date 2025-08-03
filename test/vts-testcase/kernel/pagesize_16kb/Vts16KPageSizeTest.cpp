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

#include <android-base/properties.h>
#include <android/api-level.h>
#include <elf.h>
#include <gtest/gtest.h>
#include <libelf64/parse.h>

class Vts16KPageSizeTest : public ::testing::Test {
  protected:
    static bool IsLowRamDevice() {
        return android::base::GetBoolProperty("ro.config.low_ram", false);
    }

    static int VendorApiLevel() {
        // "ro.vendor.api_level" is added in Android T.
        // Undefined indicates S or below
        return android::base::GetIntProperty("ro.vendor.api_level", __ANDROID_API_S__);
    }

    static std::string Architecture() { return android::base::GetProperty("ro.bionic.arch", ""); }

    static ssize_t MaxPageSize(const std::string& filepath) {
        ssize_t maxPageSize = -1;

        android::elf64::Elf64Binary elf;

        if (!android::elf64::Elf64Parser::ParseElfFile(filepath, elf)) {
            return -1;
        }

        for (int i = 0; i < elf.phdrs.size(); i++) {
            Elf64_Phdr phdr = elf.phdrs[i];

            if ((phdr.p_type != PT_LOAD) || !(phdr.p_type & PF_X)) {
                continue;
            }

            maxPageSize = phdr.p_align;
            break;
        }

        return maxPageSize;
    }

    static void SetUpTestSuite() {
        if (VendorApiLevel() < __ANDROID_API_V__) {
            GTEST_SKIP() << "16kB support is only required on V and later releases.";
        } else if (IsLowRamDevice()) {
            GTEST_SKIP() << "Low Ram devices only support 4kB page size";
        }
    }

    size_t RequiredMaxPageSize() {
        if (mArch == "x86_64") {
            return 4096;
        } else if (mArch == "arm64" || mArch == "aarch64") {
            return 65536;
        } else {
            return -1;
        }
    }

    const std::string mArch = Architecture();
};

/**
 * Checks the max-page-size of init against the architecture's
 * required max-page-size.
 */
TEST_F(Vts16KPageSizeTest, InitMaxPageSizeTest) {
    constexpr char initPath[] = "/system/bin/init";

    ssize_t expectedMaxPageSize = RequiredMaxPageSize();
    ASSERT_NE(expectedMaxPageSize, -1)
            << "Failed to get required max page size for arch: " << mArch;

    ssize_t initMaxPageSize = MaxPageSize(initPath);
    ASSERT_NE(initMaxPageSize, -1) << "Failed to get max page size of ELF: " << initPath;

    ASSERT_EQ(initMaxPageSize, expectedMaxPageSize)
            << "ELF " << initPath << " was not built with the required max-page-size";
}
