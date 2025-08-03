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

#include <android-base/file.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <fs_mgr.h>
#include <fstab/fstab.h>
#include <gtest/gtest.h>

#include <string>
#include <thread>

#include "utils.h"

using namespace std::chrono_literals;

static bool isExemptFromAVBTests() {
    int first_api_level = getFirstApiLevel();
    int vendor_api_level = getVendorApiLevel();
    GTEST_LOG_(INFO) << "First API level is " << first_api_level;
    GTEST_LOG_(INFO) << "Vendor API level is " << vendor_api_level;
    if (first_api_level < __ANDROID_API_S__) {
        GTEST_LOG_(INFO) << "Exempt from avb test due to old starting API level";
        return true;
    }

    // This feature name check only applies to devices that first shipped with
    // SC or later.
    int min_api_level = (first_api_level < vendor_api_level) ? first_api_level : vendor_api_level;
    if (min_api_level >= __ANDROID_API_S__ &&
        !deviceSupportsFeature("android.hardware.security.model.compatible")) {
        GTEST_LOG_(INFO) << "Skipping test: FEATURE_SECURITY_MODEL_COMPATIBLE missing.";
        return true;
    }
    return false;
}

static std::set<std::string> getVerityMountPoints() {
    std::set<std::string> verity_partitions;

    android::fs_mgr::Fstab mounted_fstab;
    if (!ReadFstabFromFile("/proc/mounts", &mounted_fstab)) {
        ADD_FAILURE() << "Failed to read the mounted fstab";
        return verity_partitions;
    }

    // Build a list of mount points that are either mounted or known to have
    // importance.
    std::set<std::string> mount_points = {"/", "/system"};
    for (const auto& entry : mounted_fstab) {
        mount_points.insert(entry.mount_point);
    }
    android::fs_mgr::Fstab fstab;
    if (!ReadDefaultFstab(&fstab)) {
        ADD_FAILURE() << "Failed to read default fstab";
        return verity_partitions;
    }

    for (const auto& entry : fstab) {
        if (!entry.fs_mgr_flags.avb) {
            continue;
        }

        if (mount_points.find(entry.mount_point) == mount_points.end()) {
            GTEST_LOG_(INFO) << entry.mount_point << " isn't mounted, skipping";
            continue;
        }

        if (mount_points.find(entry.mount_point) == mount_points.end()) {
            GTEST_LOG_(INFO) << entry.mount_point << " isn't mounted, skipping";
            continue;
        }

        if (android::base::EqualsIgnoreCase(entry.fs_type, "emmc")) {
            GTEST_LOG_(INFO) << entry.mount_point << " has emmc fs_type, skipping";
            continue;
        }

        GTEST_LOG_(INFO) << "partition enabled verity " << entry.mount_point;
        verity_partitions.insert(android::base::Basename(entry.mount_point));
    }
    return verity_partitions;
}

// The properties for this test are set in init.  There is a race condition that
// causes this test to be evaluated before these properties are readable.  Avoid
// this by waiting for the properties to be available.
void waitForProperty(const std::string &property) {
    int retries = 40;
    std::string value = android::base::GetProperty(property, "unset");
    while (android::base::EqualsIgnoreCase(property, "unset") && retries--) {
        value = android::base::GetProperty(property, "unset");
        std::this_thread::sleep_for(100ms);
    }
    if (android::base::EqualsIgnoreCase(property, "unset"))
        ADD_FAILURE() << "Property was never set: " << property;
}

// As required by CDD, verified boot MUST use verification algorithms as strong
// as current recommendations from NIST for hashing algorithms (SHA-256).
// @CddTest = 9.10/C-1-5
TEST(VerifiedBootTest, avbHashtreeNotUsingSha1) {
    if (isExemptFromAVBTests()) {
        GTEST_SKIP();
    }

    auto verity_mount_points = getVerityMountPoints();
    for (const auto& mount_point : verity_mount_points) {
        // The verity sysprop use "system" as the partition name in the system as
        // root case.
        std::string partition = mount_point == "/" ? "system" : mount_point;
        std::string alg_prop_name = "partition." + partition + ".verified.hash_alg";
        waitForProperty(alg_prop_name);
        std::string hash_alg = android::base::GetProperty(alg_prop_name, "");

        if (hash_alg.empty())
            ADD_FAILURE() << "Could not find hash algorithm for" << partition;
        if (android::base::StartsWithIgnoreCase(hash_alg, "sha1"))
            ADD_FAILURE() << "SHA1 is insecure, but is being used for " << partition;
    }
}

// Ensure that protected partitions are being verified every time they are read
// from, rather than once per boot.
// @CddTest = 9.10/C-1-7
TEST(VerifiedBootTest, avbNotUsingCheckAtMostOnce) {
    if (isExemptFromAVBTests()) {
        GTEST_SKIP();
    }
    if (getFirstApiLevel() < __ANDROID_API_U__) {
        GTEST_SKIP() << "Skipping test: Exempt due to old API level";
    }

    // Any device with sufficient RAM or a 64-bit CPU is not allowed to use
    // check_at_most_once.
    //
    // Sufficiently performance limited devices are allowed to use it out of necessity.
    if (android::base::GetBoolProperty("ro.config.low_ram", false) &&
            android::base::GetProperty("ro.product.cpu.abilist64", "").empty())
        GTEST_SKIP()
                << "Skipping test: Device is performance constrained (low ram or 32-bit)";

    auto verity_mount_points = getVerityMountPoints();
    for (const auto& mount_point : verity_mount_points) {
        // The verity sysprop use "system" as the partition name in the system as
        // root case.
        std::string partition = mount_point == "/" ? "system" : mount_point;
        std::string prop_name = "partition." + partition + ".verified.check_at_most_once";
        waitForProperty(prop_name);
        if (android::base::GetBoolProperty(prop_name, false))
            ADD_FAILURE() << "check_at_most_once is set on " << partition;
    }
}
