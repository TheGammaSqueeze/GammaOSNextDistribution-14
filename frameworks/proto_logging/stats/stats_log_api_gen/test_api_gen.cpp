/*
 * Copyright (C) 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <gtest/gtest.h>
#include <statslog.h>

namespace android {
namespace stats_log_api_gen {

/**
 * Tests native auto generated code for specific atom contains proper ids
 */
TEST(ApiGenAtomTest, AtomIdConstantsTest) {
    // For reference from the atoms.proto
    // BleScanStateChanged ble_scan_state_changed = 2
    //         [(module) = "bluetooth", (module) = "statsdtest"];
    // ProcessStateChanged process_state_changed = 3 [(module) = "framework", deprecated = true];
    EXPECT_EQ(android::util::BLE_SCAN_STATE_CHANGED, 2);
    EXPECT_EQ(android::util::PROCESS_STATE_CHANGED, 3);
    EXPECT_EQ(android::util::BOOT_SEQUENCE_REPORTED, 57);
}

/**
 * Tests native auto generated code for specific atom contains proper enums
 */
TEST(ApiGenAtomTest, AtomEnumsConstantsTest) {
    // For reference from the atoms.proto
    // message BleScanStateChanged {
    //     repeated AttributionNode attribution_node = 1
    //             [(state_field_option).primary_field_first_uid = true];

    //     enum State {
    //         OFF = 0;
    //         ON = 1;
    //         // RESET indicates all ble stopped. Used when it (re)starts (e.g. after it crashes).
    //         RESET = 2;
    //     }

    EXPECT_EQ(android::util::BLE_SCAN_STATE_CHANGED__STATE__OFF, 0);
    EXPECT_EQ(android::util::BLE_SCAN_STATE_CHANGED__STATE__ON, 1);
    EXPECT_EQ(android::util::BLE_SCAN_STATE_CHANGED__STATE__RESET, 2);
}

/**
 * Tests complete native auto generated code for specific atom TestAtomReported
 */
TEST(ApiGenAtomTest, TestAtomReportedApiTest) {
    // For reference from the atoms.proto
    // message TestAtomReported {
    //     repeated AttributionNode attribution_node = 1;
    //     optional int32 int_field = 2;
    //     optional int64 long_field = 3;
    //     optional float float_field = 4;
    //     optional string string_field = 5;
    //     optional bool boolean_field = 6;
    //     enum State {
    //         UNKNOWN = 0;
    //         OFF = 1;
    //         ON = 2;
    //     }
    //     optional State state = 7;
    //     optional TrainExperimentIds bytes_field = 8 [(android.os.statsd.log_mode) = MODE_BYTES];
    //     repeated int32 repeated_int_field = 9;
    //     repeated int64 repeated_long_field = 10;
    //     repeated float repeated_float_field = 11;
    //     repeated string repeated_string_field = 12;
    //     repeated bool repeated_boolean_field = 13;
    //     repeated State repeated_enum_field = 14;
    // }
    EXPECT_EQ(android::util::TEST_ATOM_REPORTED, 205);

    EXPECT_EQ(android::util::TEST_ATOM_REPORTED__STATE__UNKNOWN, 0);
    EXPECT_EQ(android::util::TEST_ATOM_REPORTED__STATE__OFF, 1);
    EXPECT_EQ(android::util::TEST_ATOM_REPORTED__STATE__ON, 2);

    typedef int (*WriteApi)(int32_t code, const int32_t* uid, size_t uid_length,
                            const std::vector<char const*>& tag, int32_t arg2, int64_t arg3,
                            float arg4, char const* arg5, bool arg6, int32_t arg7,
                            const android::util::BytesField& arg8, const std::vector<int32_t>& arg9,
                            const std::vector<int64_t>& arg10, const std::vector<float>& arg11,
                            const std::vector<char const*>& arg12, const bool* arg13,
                            size_t arg13_length, const std::vector<int32_t>& arg14);

    WriteApi atomWriteApi = &android::util::stats_write;

    EXPECT_NE(atomWriteApi, nullptr);
}

}  // namespace stats_log_api_gen
}  // namespace android
