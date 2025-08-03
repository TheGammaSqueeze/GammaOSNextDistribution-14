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
package com.android.tradefed.testtype.binary;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.result.TestDescription;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KTapResultParserTest {

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                    {KTapResultParser.ParseResolution.INDIVIDUAL_LEAVES},
                    {KTapResultParser.ParseResolution.AGGREGATED_TOP_LEVEL}
                });
    }

    @Parameter public KTapResultParser.ParseResolution mParseResolution;

    @Test
    public void test_doc_example_tap() {
        // Example taken from https://docs.kernel.org/dev-tools/ktap.html#example-ktap-output
        String ktapResults =
                "KTAP version 1\n"
                        + "1..1\n"
                        + "  KTAP version 1\n"
                        + "  1..3\n"
                        + "    KTAP version 1\n"
                        + "    1..1\n"
                        + "    # test_1: initializing test_1\n"
                        + "    ok 1 test_1\n"
                        + "  ok 1 example_test_1\n"
                        + "    KTAP version 1\n"
                        + "    1..2\n"
                        + "    ok 1 test_1 # SKIP test_1 skipped\n"
                        + "    ok 2 test_2\n"
                        + "  ok 2 example_test_2\n"
                        + "    KTAP version 1\n"
                        + "    1..3\n"
                        + "    ok 1 test_1\n"
                        + "    # test_2: FAIL\n"
                        + "    not ok 2 test_2\n"
                        + "    ok 3 test_3 # SKIP test_3 skipped\n"
                        + "  not ok 3 example_test_3\n"
                        + "not ok 1 main_test\n";

        String[][] expectedLeafResults = {
            {"main_test.example_test_1.test_1", "PASSED", ""},
            {"main_test.example_test_2.test_1", "IGNORED", ""},
            {"main_test.example_test_2.test_2", "PASSED", ""},
            {"main_test.example_test_3.test_1", "PASSED", ""},
            {"main_test.example_test_3.test_2", "FAILURE", "# test_2: FAIL"},
            {"main_test.example_test_3.test_3", "IGNORED", ""}
        };
        String[] expectedRootResults = {"main_test", "FAILURE", ""};
        checkKTap(ktapResults, expectedLeafResults, expectedRootResults);
    }

    @Test
    public void test_partial_tap() {
        String[] ktapResultsByLine = {
            "KTAP version 1\n",
            "1..1\n",
            "  KTAP version 1\n",
            "  1..3\n",
            "    KTAP version 1\n",
            "    1..1\n",
            "    # test_1: initializing test_1\n",
            "    ok 1 test_1\n",
            "  ok 1 example_test_1\n",
            "    KTAP version 1\n",
            "    1..2\n",
            "    ok 1 test_1 # SKIP test_1 skipped\n",
            "    ok 2 test_2\n",
            "  ok 2 example_test_2\n",
            "    KTAP version 1\n",
            "    1..3\n",
            "    ok 1 test_1\n",
            "    # test_2: FAIL\n",
            "    not ok 2 test_2\n",
            "    ok 3 test_3 # SKIP test_3 skipped\n",
            "  not ok 3 example_test_3\n",
            "not ok 1 main_test\n"
        };

        String[][] expectedLeafResults = {
            {"main_test.example_test_1.test_1", "PASSED", ""},
            {"main_test.example_test_2.test_1", "IGNORED", ""},
            {"main_test.example_test_2.test_2", "PASSED", ""},
            {"main_test.example_test_3.test_1", "PASSED", ""},
            {"main_test.example_test_3.test_2", "FAILURE", "# test_2: FAIL"},
            {"main_test.example_test_3.test_3", "IGNORED", ""}
        };

        // The full KTAP should pass with no error.
        Optional<String> ktapResults =
                Arrays.stream(ktapResultsByLine).reduce((str1, str2) -> str1 + str2);
        String[] expectedRootResults = {"main_test", "FAILURE", ""};
        checkKTap(ktapResults.get(), expectedLeafResults, expectedRootResults);

        // Loop through the good KTAP with an additional line removed for each iteration.
        // An exception should be thrown for each partial result.
        for (int i = ktapResultsByLine.length - 2; i > 0; --i) {
            Optional<String> ktapResults2 =
                    Arrays.stream(ktapResultsByLine).limit(i).reduce((str1, str2) -> str1 + str2);

            assertThrows(
                    String.format(
                            "The following partial ktap results with the last '%d' lines omitted"
                                    + " didn't report an error as expected: %s",
                            ktapResultsByLine.length - i - 1, ktapResults2.get()),
                    RuntimeException.class,
                    () -> checkKTap(ktapResults2.get(), expectedLeafResults, expectedRootResults));
        }
    }

    @Test
    public void test_too_many_results() {
        String ktapResults =
                "KTAP version 1\n"
                        + "1..1\n"
                        + "  KTAP version 1\n"
                        + "  1..3\n"
                        + "    KTAP version 1\n"
                        + "    1..1\n"
                        + "    # test_1: initializing test_1\n"
                        + "    ok 1 test_1\n"
                        + "  ok 1 example_test_1\n"
                        + "    KTAP version 1\n"
                        + "    1..2\n"
                        + "    ok 1 test_1 # SKIP test_1 skipped\n"
                        + "    ok 2 test_2\n"
                        + "    ok 3 test_2\n" // <-- This is the error
                        + "  ok 2 example_test_2\n"
                        + "    KTAP version 1\n"
                        + "    1..3\n"
                        + "    ok 1 test_1\n"
                        + "    # test_2: FAIL\n"
                        + "    not ok 2 test_2\n"
                        + "    ok 3 test_3 # SKIP test_3 skipped\n"
                        + "  not ok 3 example_test_3\n"
                        + "not ok 1 main_test\n";

        assertThrows(RuntimeException.class, () -> checkKTap(ktapResults, null, null));
    }

    @Test
    public void test_duplicate_test_num() {
        String ktapResults =
                "KTAP version 1\n"
                        + "1..1\n"
                        + "  KTAP version 1\n"
                        + "  1..3\n"
                        + "    KTAP version 1\n"
                        + "    1..1\n"
                        + "    # test_1: initializing test_1\n"
                        + "    ok 1 test_1\n"
                        + "  ok 1 example_test_1\n"
                        + "    KTAP version 1\n"
                        + "    1..2\n"
                        + "    ok 1 test_1 # SKIP test_1 skipped\n"
                        + "    ok 1 test_2\n" // <-- This is the error
                        + "  ok 2 example_test_2\n"
                        + "    KTAP version 1\n"
                        + "    1..3\n"
                        + "    ok 1 test_1\n"
                        + "    # test_2: FAIL\n"
                        + "    not ok 2 test_2\n"
                        + "    ok 3 test_3 # SKIP test_3 skipped\n"
                        + "  not ok 3 example_test_3\n"
                        + "not ok 1 main_test\n";

        assertThrows(RuntimeException.class, () -> checkKTap(ktapResults, null, null));
    }

    @Test
    public void test_skipped_test_num() {
        String ktapResults =
                "KTAP version 1\n"
                        + "1..1\n"
                        + "  KTAP version 1\n"
                        + "  1..3\n"
                        + "    KTAP version 1\n"
                        + "    1..1\n"
                        + "    # test_1: initializing test_1\n"
                        + "    ok 1 test_1\n"
                        + "  ok 1 example_test_1\n"
                        + "    KTAP version 1\n"
                        + "    1..2\n"
                        + "    ok 1 test_1 # SKIP test_1 skipped\n"
                        + "    ok 2 test_2\n"
                        + "  ok 2 example_test_2\n"
                        + "    KTAP version 1\n"
                        + "    1..3\n"
                        + "    ok 1 test_1\n"
                        + "    # test_2: FAIL\n"
                        + "    # not ok 2 test_2\n" // <-- This is the error.
                        + "    ok 3 test_3 # SKIP test_3 skipped\n"
                        + "  not ok 3 example_test_3\n"
                        + "not ok 1 main_test\n";

        assertThrows(RuntimeException.class, () -> checkKTap(ktapResults, null, null));
    }

    @Test
    public void test_toStringWithSubtests() {
        String ktapResults =
                "KTAP version 1\n"
                        + "1..1\n"
                        + "  KTAP version 1\n"
                        + "  1..3\n"
                        + "    KTAP version 1\n"
                        + "    1..1\n"
                        + "    # test_1: initializing test_1\n"
                        + "    ok 1 test_1\n"
                        + "  ok 1 example_test_1\n"
                        + "    KTAP version 1\n"
                        + "    1..2\n"
                        + "    ok 1 test_1 # SKIP test_1 skipped\n"
                        + "    ok 2 test_2\n"
                        + "  ok 2 example_test_2\n"
                        + "    KTAP version 1\n"
                        + "    1..3\n"
                        + "    ok 1 test_1\n"
                        + "    # test_2: FAIL\n"
                        + "    not ok 2 test_2\n"
                        + "    ok 3 test_3 # SKIP test_3 skipped\n"
                        + "  not ok 3 example_test_3\n"
                        + "not ok 1 main_test\n";

        KTapResultParser parser = new KTapResultParser();
        String actual = parser.processResultsFileContent(ktapResults).toStringWithSubtests("");
        String expected =
                "null\n"
                        + "   main_test\n"
                        + "      example_test_1\n"
                        + "         test_1\n"
                        + "      example_test_2\n"
                        + "         test_1\n"
                        + "         test_2\n"
                        + "      example_test_3\n"
                        + "         test_1\n"
                        + "         test_2\n"
                        + "         test_3\n";
        assertEquals(expected, actual);
    }

    @Test
    public void test_ext4_inode_test_ktap() {
        // Example output taken from ext4-inode-test.ko
        String ktapResults =
                "KTAP version 1\n"
                    + "1..1\n"
                    + "    KTAP version 1\n"
                    + "    # Subtest: ext4_inode_test\n"
                    + "    1..1\n"
                    + "        KTAP version 1\n"
                    + "        # Subtest: inode_test_xtimestamp_decoding\n"
                    + "        ok 1 1901-12-13 Lower bound of 32bit < 0 timestamp, no extra bits\n"
                    + "        ok 2 1969-12-31 Upper bound of 32bit < 0 timestamp, no extra bits\n"
                    + "        ok 3 1970-01-01 Lower bound of 32bit >=0 timestamp, no extra bits\n"
                    + "        ok 4 2038-01-19 Upper bound of 32bit >=0 timestamp, no extra bits\n"
                    + "        ok 5 2038-01-19 Lower bound of 32bit <0 timestamp, lo extra sec bit"
                    + " on\n"
                    + "        ok 6 2106-02-07 Upper bound of 32bit <0 timestamp, lo extra sec bit"
                    + " on\n"
                    + "        ok 7 2106-02-07 Lower bound of 32bit >=0 timestamp, lo extra sec bit"
                    + " on\n"
                    + "        ok 8 2174-02-25 Upper bound of 32bit >=0 timestamp, lo extra sec bit"
                    + " on\n"
                    + "        ok 9 2174-02-25 Lower bound of 32bit <0 timestamp, hi extra sec bit"
                    + " on\n"
                    + "        ok 10 2242-03-16 Upper bound of 32bit <0 timestamp, hi extra sec bit"
                    + " on\n"
                    + "        ok 11 2242-03-16 Lower bound of 32bit >=0 timestamp, hi extra sec"
                    + " bit on\n"
                    + "        ok 12 2310-04-04 Upper bound of 32bit >=0 timestamp, hi extra sec"
                    + " bit on\n"
                    + "        ok 13 2310-04-04 Upper bound of 32bit>=0 timestamp, hi extra sec bit"
                    + " 1. 1 ns\n"
                    + "        ok 14 2378-04-22 Lower bound of 32bit>= timestamp. Extra sec bits 1."
                    + " Max ns\n"
                    + "        ok 15 2378-04-22 Lower bound of 32bit >=0 timestamp. All extra sec"
                    + " bits on\n"
                    + "        ok 16 2446-05-10 Upper bound of 32bit >=0 timestamp. All extra sec"
                    + " bits on\n"
                    + "    # inode_test_xtimestamp_decoding: pass:16 fail:0 skip:0 total:16\n"
                    + "    ok 1 inode_test_xtimestamp_decoding\n"
                    + "    # module: ext4_inode_test\n"
                    + "# Totals: pass:16 fail:0 skip:0 total:16\n"
                    + "ok 1 ext4_inode_test\n";

        String[][] expectedLeafResults = {
            {
                "ext4_inode_test.inode_test_xtimestamp_decoding.1901-12-13_Lower_bound_of_32bit_<_0_timestamp,_no_extra_bits",
                "PASSED",
                ""
            },
            {
                "ext4_inode_test.inode_test_xtimestamp_decoding.1969-12-31_Upper_bound_of_32bit_<_0_timestamp,_no_extra_bits",
                "PASSED",
                ""
            },
            {
                "ext4_inode_test.inode_test_xtimestamp_decoding.1970-01-01_Lower_bound_of_32bit_>=0_timestamp,_no_extra_bits",
                "PASSED",
                ""
            },
            {
                "ext4_inode_test.inode_test_xtimestamp_decoding.2038-01-19_Upper_bound_of_32bit_>=0_timestamp,_no_extra_bits",
                "PASSED",
                ""
            },
            {
                "ext4_inode_test.inode_test_xtimestamp_decoding.2038-01-19_Lower_bound_of_32bit_<0_timestamp,_lo_extra_sec_bit_on",
                "PASSED",
                ""
            },
            {
                "ext4_inode_test.inode_test_xtimestamp_decoding.2106-02-07_Upper_bound_of_32bit_<0_timestamp,_lo_extra_sec_bit_on",
                "PASSED",
                ""
            },
            {
                "ext4_inode_test.inode_test_xtimestamp_decoding.2106-02-07_Lower_bound_of_32bit_>=0_timestamp,_lo_extra_sec_bit_on",
                "PASSED",
                ""
            },
            {
                "ext4_inode_test.inode_test_xtimestamp_decoding.2174-02-25_Upper_bound_of_32bit_>=0_timestamp,_lo_extra_sec_bit_on",
                "PASSED",
                ""
            },
            {
                "ext4_inode_test.inode_test_xtimestamp_decoding.2174-02-25_Lower_bound_of_32bit_<0_timestamp,_hi_extra_sec_bit_on",
                "PASSED",
                ""
            },
            {
                "ext4_inode_test.inode_test_xtimestamp_decoding.2242-03-16_Upper_bound_of_32bit_<0_timestamp,_hi_extra_sec_bit_on",
                "PASSED",
                ""
            },
            {
                "ext4_inode_test.inode_test_xtimestamp_decoding.2242-03-16_Lower_bound_of_32bit_>=0_timestamp,_hi_extra_sec_bit_on",
                "PASSED",
                ""
            },
            {
                "ext4_inode_test.inode_test_xtimestamp_decoding.2310-04-04_Upper_bound_of_32bit_>=0_timestamp,_hi_extra_sec_bit_on",
                "PASSED",
                ""
            },
            {
                "ext4_inode_test.inode_test_xtimestamp_decoding.2310-04-04_Upper_bound_of_32bit>=0_timestamp,_hi_extra_sec_bit_1._1_ns",
                "PASSED",
                ""
            },
            {
                "ext4_inode_test.inode_test_xtimestamp_decoding.2378-04-22_Lower_bound_of_32bit>=_timestamp._Extra_sec_bits_1._Max_ns",
                "PASSED",
                ""
            },
            {
                "ext4_inode_test.inode_test_xtimestamp_decoding.2378-04-22_Lower_bound_of_32bit_>=0_timestamp._All_extra_sec_bits_on",
                "PASSED",
                ""
            },
            {
                "ext4_inode_test.inode_test_xtimestamp_decoding.2446-05-10_Upper_bound_of_32bit_>=0_timestamp._All_extra_sec_bits_on",
                "PASSED",
                ""
            },
        };
        String[] expectedRootResults = {"ext4_inode_test", "PASSED", ""};
        checkKTap(ktapResults, expectedLeafResults, expectedRootResults);
    }

    @Test
    public void test_fat_test_ktap() {
        // Example output taken from fat_test.ko
        String ktapResults =
                "KTAP version 1\n"
                        + "1..1\n"
                        + "    KTAP version 1\n"
                        + "    # Subtest: fat_test\n"
                        + "    1..3\n"
                        + "    ok 1 fat_checksum_test\n"
                        + "        KTAP version 1\n"
                        + "        # Subtest: fat_time_fat2unix_test\n"
                        + "        ok 1 Earliest possible UTC (1980-01-01 00:00:00)\n"
                        + "        ok 2 Latest possible UTC (2107-12-31 23:59:58)\n"
                        + "        ok 3 Earliest possible (UTC-11) (== 1979-12-31 13:00:00 UTC)\n"
                        + "        ok 4 Latest possible (UTC+11) (== 2108-01-01 10:59:58 UTC)\n"
                        + "        ok 5 Leap Day / Year (1996-02-29 00:00:00)\n"
                        + "        ok 6 Year 2000 is leap year (2000-02-29 00:00:00)\n"
                        + "        ok 7 Year 2100 not leap year (2100-03-01 00:00:00)\n"
                        + "        ok 8 Leap year + timezone UTC+1 (== 2004-02-29 00:30:00 UTC)\n"
                        + "        ok 9 Leap year + timezone UTC-1 (== 2004-02-29 23:30:00 UTC)\n"
                        + "        ok 10 VFAT odd-second resolution (1999-12-31 23:59:59)\n"
                        + "        ok 11 VFAT 10ms resolution (1980-01-01 00:00:00:0010)\n"
                        + "    # fat_time_fat2unix_test: pass:11 fail:0 skip:0 total:11\n"
                        + "    ok 2 fat_time_fat2unix_test\n"
                        + "        KTAP version 1\n"
                        + "        # Subtest: fat_time_unix2fat_test\n"
                        + "        ok 1 Earliest possible UTC (1980-01-01 00:00:00)\n"
                        + "        ok 2 Latest possible UTC (2107-12-31 23:59:58)\n"
                        + "        ok 3 Earliest possible (UTC-11) (== 1979-12-31 13:00:00 UTC)\n"
                        + "        ok 4 Latest possible (UTC+11) (== 2108-01-01 10:59:58 UTC)\n"
                        + "        ok 5 Leap Day / Year (1996-02-29 00:00:00)\n"
                        + "        ok 6 Year 2000 is leap year (2000-02-29 00:00:00)\n"
                        + "        ok 7 Year 2100 not leap year (2100-03-01 00:00:00)\n"
                        + "        ok 8 Leap year + timezone UTC+1 (== 2004-02-29 00:30:00 UTC)\n"
                        + "        ok 9 Leap year + timezone UTC-1 (== 2004-02-29 23:30:00 UTC)\n"
                        + "        ok 10 VFAT odd-second resolution (1999-12-31 23:59:59)\n"
                        + "        ok 11 VFAT 10ms resolution (1980-01-01 00:00:00:0010)\n"
                        + "    # fat_time_unix2fat_test: pass:11 fail:0 skip:0 total:11\n"
                        + "    ok 3 fat_time_unix2fat_test\n"
                        + "    # module: fat_test\n"
                        + "# fat_test: pass:3 fail:0 skip:0 total:3\n"
                        + "# Totals: pass:23 fail:0 skip:0 total:23\n"
                        + "ok 1 fat_test\n";

        String[][] expectedLeafResults = {
            {"fat_test.fat_checksum_test", "PASSED", ""},
            {
                "fat_test.fat_time_fat2unix_test.Earliest_possible_UTC_(1980-01-01_00:00:00)",
                "PASSED",
                ""
            },
            {
                "fat_test.fat_time_fat2unix_test.Latest_possible_UTC_(2107-12-31_23:59:58)",
                "PASSED",
                ""
            },
            {
                "fat_test.fat_time_fat2unix_test.Earliest_possible_(UTC-11)_(==_1979-12-31_13:00:00_UTC)",
                "PASSED",
                ""
            },
            {
                "fat_test.fat_time_fat2unix_test.Latest_possible_(UTC+11)_(==_2108-01-01_10:59:58_UTC)",
                "PASSED",
                ""
            },
            {"fat_test.fat_time_fat2unix_test.Leap_Day_/_Year_(1996-02-29_00:00:00)", "PASSED", ""},
            {
                "fat_test.fat_time_fat2unix_test.Year_2000_is_leap_year_(2000-02-29_00:00:00)",
                "PASSED",
                ""
            },
            {
                "fat_test.fat_time_fat2unix_test.Year_2100_not_leap_year_(2100-03-01_00:00:00)",
                "PASSED",
                ""
            },
            {
                "fat_test.fat_time_fat2unix_test.Leap_year_+_timezone_UTC+1_(==_2004-02-29_00:30:00_UTC)",
                "PASSED",
                ""
            },
            {
                "fat_test.fat_time_fat2unix_test.Leap_year_+_timezone_UTC-1_(==_2004-02-29_23:30:00_UTC)",
                "PASSED",
                ""
            },
            {
                "fat_test.fat_time_fat2unix_test.VFAT_odd-second_resolution_(1999-12-31_23:59:59)",
                "PASSED",
                ""
            },
            {
                "fat_test.fat_time_fat2unix_test.VFAT_10ms_resolution_(1980-01-01_00:00:00:0010)",
                "PASSED",
                ""
            },
            {
                "fat_test.fat_time_unix2fat_test.Earliest_possible_UTC_(1980-01-01_00:00:00)",
                "PASSED",
                ""
            },
            {
                "fat_test.fat_time_unix2fat_test.Latest_possible_UTC_(2107-12-31_23:59:58)",
                "PASSED",
                ""
            },
            {
                "fat_test.fat_time_unix2fat_test.Earliest_possible_(UTC-11)_(==_1979-12-31_13:00:00_UTC)",
                "PASSED",
                ""
            },
            {
                "fat_test.fat_time_unix2fat_test.Latest_possible_(UTC+11)_(==_2108-01-01_10:59:58_UTC)",
                "PASSED",
                ""
            },
            {"fat_test.fat_time_unix2fat_test.Leap_Day_/_Year_(1996-02-29_00:00:00)", "PASSED", ""},
            {
                "fat_test.fat_time_unix2fat_test.Year_2000_is_leap_year_(2000-02-29_00:00:00)",
                "PASSED",
                ""
            },
            {
                "fat_test.fat_time_unix2fat_test.Year_2100_not_leap_year_(2100-03-01_00:00:00)",
                "PASSED",
                ""
            },
            {
                "fat_test.fat_time_unix2fat_test.Leap_year_+_timezone_UTC+1_(==_2004-02-29_00:30:00_UTC)",
                "PASSED",
                ""
            },
            {
                "fat_test.fat_time_unix2fat_test.Leap_year_+_timezone_UTC-1_(==_2004-02-29_23:30:00_UTC)",
                "PASSED",
                ""
            },
            {
                "fat_test.fat_time_unix2fat_test.VFAT_odd-second_resolution_(1999-12-31_23:59:59)",
                "PASSED",
                ""
            },
            {
                "fat_test.fat_time_unix2fat_test.VFAT_10ms_resolution_(1980-01-01_00:00:00:0010)",
                "PASSED",
                ""
            },
        };
        String[] expectedRootResults = {"fat_test", "PASSED", ""};
        checkKTap(ktapResults, expectedLeafResults, expectedRootResults);
    }

    @Test
    public void test_no_tests_run_with_header_ktap() {
        // Example taken from KUnits: test_data/test_is_test_passed-no_tests_run_with_header.log
        String ktapResults = "TAP version 14\n" + "1..0\n";
        String[][] expectedLeafResults = {};
        String[] expectedRootResults = {};
        assertThrows(
                IllegalArgumentException.class,
                () -> checkKTap(ktapResults, expectedLeafResults, expectedRootResults));
    }

    @Test
    public void test_passed_no_tests_no_plan_ktap() {
        // Example taken from KUnits: test_data/test_is_test_passed-no_tests_no_plan.log
        String ktapResults =
                "TAP version 14\n"
                        + "1..1\n"
                        + "  # Subtest: suite\n"
                        + "  1..1\n"
                        + "    # Subtest: case\n"
                        + "  ok 1 - case\n"
                        + "ok 1 - suite\n";

        String[][] expectedLeafResults = {{"suite.case", "PASSED", ""}};
        String[] expectedRootResults = {"suite", "PASSED", ""};
        checkKTap(ktapResults, expectedLeafResults, expectedRootResults);
    }

    @Test
    public void test_passed_missing_plan_ktap() {
        // Example from KUnits: test_data/test_is_test_passed-missing_plan.log
        String ktapResults =
                "KTAP version 1\n"
                    + "	# Subtest: sysctl_test\n"
                    + "	# sysctl_test_dointvec_null_tbl_data: sysctl_test_dointvec_null_tbl_data"
                    + " passed\n"
                    + "	ok 1 - sysctl_test_dointvec_null_tbl_data\n"
                    + "	# sysctl_test_dointvec_table_maxlen_unset:"
                    + " sysctl_test_dointvec_table_maxlen_unset passed\n"
                    + "	ok 2 - sysctl_test_dointvec_table_maxlen_unset\n"
                    + "	# sysctl_test_dointvec_table_len_is_zero:"
                    + " sysctl_test_dointvec_table_len_is_zero passed\n"
                    + "	ok 3 - sysctl_test_dointvec_table_len_is_zero\n"
                    + "	# sysctl_test_dointvec_table_read_but_position_set:"
                    + " sysctl_test_dointvec_table_read_but_position_set passed\n"
                    + "	ok 4 - sysctl_test_dointvec_table_read_but_position_set\n"
                    + "	# sysctl_test_dointvec_happy_single_positive:"
                    + " sysctl_test_dointvec_happy_single_positive passed\n"
                    + "	ok 5 - sysctl_test_dointvec_happy_single_positive\n"
                    + "	# sysctl_test_dointvec_happy_single_negative:"
                    + " sysctl_test_dointvec_happy_single_negative passed\n"
                    + "	ok 6 - sysctl_test_dointvec_happy_single_negative\n"
                    + "	# sysctl_test_dointvec_single_less_int_min:"
                    + " sysctl_test_dointvec_single_less_int_min passed\n"
                    + "	ok 7 - sysctl_test_dointvec_single_less_int_min\n"
                    + "	# sysctl_test_dointvec_single_greater_int_max:"
                    + " sysctl_test_dointvec_single_greater_int_max passed\n"
                    + "	ok 8 - sysctl_test_dointvec_single_greater_int_max\n"
                    + "kunit sysctl_test: all tests passed\n"
                    + "ok 1 - sysctl_test\n"
                    + "	# Subtest: example\n"
                    + "	1..2\n"
                    + "init_suite\n"
                    + "	# example_simple_test: initializing\n"
                    + "	# example_simple_test: example_simple_test passed\n"
                    + "	ok 1 - example_simple_test\n"
                    + "	# example_mock_test: initializing\n"
                    + "	# example_mock_test: example_mock_test passed\n"
                    + "	ok 2 - example_mock_test\n"
                    + "kunit example: all tests passed\n"
                    + "ok 2 - example\n";

        String[][] expectedLeafResults = {
            {"sysctl_test.sysctl_test_dointvec_null_tbl_data", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_table_maxlen_unset", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_table_len_is_zero", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_table_read_but_position_set", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_happy_single_positive", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_happy_single_negative", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_single_less_int_min", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_single_greater_int_max", "PASSED", ""},
            {"example.example_simple_test", "PASSED", ""},
            {"example.example_mock_test", "PASSED", ""},
        };
        String[] expectedRootResults = {"sysctl_test.example", "PASSED", ""};
        checkKTap(ktapResults, expectedLeafResults, expectedRootResults);
    }

    @Test
    public void test_passed_kselftest_ktap() {
        // Example from KUnits: test_data/test_is_test_passed-kselftest.log
        String ktapResults =
                "TAP version 13\n"
                    + "1..2\n"
                    + "# selftests: membarrier: membarrier_test_single_thread\n"
                    + "# TAP version 13\n"
                    + "# 1..2\n"
                    + "# ok 1 sys_membarrier available\n"
                    + "# ok 2 sys membarrier invalid command test: command = -1, flags = 0, errno ="
                    + " 22. Failed as expected\n"
                    + "ok 1 selftests: membarrier: membarrier_test_single_thread\n"
                    + "# selftests: membarrier: membarrier_test_multi_thread\n"
                    + "# TAP version 13\n"
                    + "# 1..2\n"
                    + "# ok 1 sys_membarrier available\n"
                    + "# ok 2 sys membarrier invalid command test: command = -1, flags = 0, errno ="
                    + " 22. Failed as expected\n"
                    + "ok 2 selftests: membarrier: membarrier_test_multi_thread\n";

        String[][] expectedLeafResults = {
            {"selftests:_membarrier:_membarrier_test_single_thread", "PASSED", ""},
            {"selftests:_membarrier:_membarrier_test_multi_thread", "PASSED", ""}
        };
        String[] expectedRootResults = {
            "selftests:_membarrier:_membarrier_test_single_thread.selftests:_membarrier:_membarrier_test_multi_thread",
            "PASSED",
            ""
        };
        checkKTap(ktapResults, expectedLeafResults, expectedRootResults);
    }

    @Test
    public void test_passed_failure_ktap() {
        // Example from KUnits: test_data/test_is_test_passed-failure.log
        String ktapResults =
                "TAP version 14\n"
                    + "1..2\n"
                    + "	# Subtest: sysctl_test\n"
                    + "	1..8\n"
                    + "	# sysctl_test_dointvec_null_tbl_data: sysctl_test_dointvec_null_tbl_data"
                    + " passed\n"
                    + "	ok 1 - sysctl_test_dointvec_null_tbl_data\n"
                    + "	# sysctl_test_dointvec_table_maxlen_unset:"
                    + " sysctl_test_dointvec_table_maxlen_unset passed\n"
                    + "	ok 2 - sysctl_test_dointvec_table_maxlen_unset\n"
                    + "	# sysctl_test_dointvec_table_len_is_zero:"
                    + " sysctl_test_dointvec_table_len_is_zero passed\n"
                    + "	ok 3 - sysctl_test_dointvec_table_len_is_zero\n"
                    + "	# sysctl_test_dointvec_table_read_but_position_set:"
                    + " sysctl_test_dointvec_table_read_but_position_set passed\n"
                    + "	ok 4 - sysctl_test_dointvec_table_read_but_position_set\n"
                    + "	# sysctl_test_dointvec_happy_single_positive:"
                    + " sysctl_test_dointvec_happy_single_positive passed\n"
                    + "	ok 5 - sysctl_test_dointvec_happy_single_positive\n"
                    + "	# sysctl_test_dointvec_happy_single_negative:"
                    + " sysctl_test_dointvec_happy_single_negative passed\n"
                    + "	ok 6 - sysctl_test_dointvec_happy_single_negative\n"
                    + "	# sysctl_test_dointvec_single_less_int_min:"
                    + " sysctl_test_dointvec_single_less_int_min passed\n"
                    + "	ok 7 - sysctl_test_dointvec_single_less_int_min\n"
                    + "	# sysctl_test_dointvec_single_greater_int_max:"
                    + " sysctl_test_dointvec_single_greater_int_max passed\n"
                    + "	ok 8 - sysctl_test_dointvec_single_greater_int_max\n"
                    + "kunit sysctl_test: all tests passed\n"
                    + "ok 1 - sysctl_test\n"
                    + "	# Subtest: example\n"
                    + "	1..2\n"
                    + "init_suite\n"
                    + "	# example_simple_test: initializing\n"
                    + "	# example_simple_test: EXPECTATION FAILED at lib/kunit/example-test.c:30\n"
                    + "	Expected 1 + 1 == 3, but\n"
                    + "		1 + 1 == 2\n"
                    + "		3 == 3\n"
                    + "	# example_simple_test: example_simple_test failed\n"
                    + "	not ok 1 - example_simple_test\n"
                    + "	# example_mock_test: initializing\n"
                    + "	# example_mock_test: example_mock_test passed\n"
                    + "	ok 2 - example_mock_test\n"
                    + "kunit example: one or more tests failed\n"
                    + "not ok 2 - example\n";

        String[][] expectedLeafResults = {
            {"sysctl_test.sysctl_test_dointvec_null_tbl_data", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_table_maxlen_unset", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_table_len_is_zero", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_table_read_but_position_set", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_happy_single_positive", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_happy_single_negative", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_single_less_int_min", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_single_greater_int_max", "PASSED", ""},
            {
                "example.example_simple_test",
                "FAILURE",
                "init_suite\n"
                    + "# example_simple_test: initializing\n"
                    + "# example_simple_test: EXPECTATION FAILED at lib/kunit/example-test.c:30\n"
                    + "Expected 1 + 1 == 3, but\n"
                    + "1 + 1 == 2\n"
                    + "3 == 3\n"
                    + "# example_simple_test: example_simple_test failed"
            },
            {"example.example_mock_test", "PASSED", ""},
        };
        String[] expectedRootResults = {"sysctl_test.example", "FAILURE", ""};
        checkKTap(ktapResults, expectedLeafResults, expectedRootResults);
    }

    @Test
    public void test_passed_all_passed_nested_ktap() {
        // Example from KUnits: test_data/test_is_test_passed-all_passed_nested.log
        String ktapResults =
                "TAP version 14\n"
                    + "1..2\n"
                    + "	# Subtest: sysctl_test\n"
                    + "	1..4\n"
                    + "	# sysctl_test_dointvec_null_tbl_data: sysctl_test_dointvec_null_tbl_data"
                    + " passed\n"
                    + "	ok 1 - sysctl_test_dointvec_null_tbl_data\n"
                    + "		# Subtest: example\n"
                    + "		1..2\n"
                    + "	init_suite\n"
                    + "		# example_simple_test: initializing\n"
                    + "		# example_simple_test: example_simple_test passed\n"
                    + "		ok 1 - example_simple_test\n"
                    + "		# example_mock_test: initializing\n"
                    + "		# example_mock_test: example_mock_test passed\n"
                    + "		ok 2 - example_mock_test\n"
                    + "	kunit example: all tests passed\n"
                    + "	ok 2 - example\n"
                    + "	# sysctl_test_dointvec_table_len_is_zero:"
                    + " sysctl_test_dointvec_table_len_is_zero passed\n"
                    + "	ok 3 - sysctl_test_dointvec_table_len_is_zero\n"
                    + "	# sysctl_test_dointvec_table_read_but_position_set:"
                    + " sysctl_test_dointvec_table_read_but_position_set passed\n"
                    + "	ok 4 - sysctl_test_dointvec_table_read_but_position_set\n"
                    + "kunit sysctl_test: all tests passed\n"
                    + "ok 1 - sysctl_test\n"
                    + "	# Subtest: example\n"
                    + "	1..2\n"
                    + "init_suite\n"
                    + "	# example_simple_test: initializing\n"
                    + "	# example_simple_test: example_simple_test passed\n"
                    + "	ok 1 - example_simple_test\n"
                    + "	# example_mock_test: initializing\n"
                    + "	# example_mock_test: example_mock_test passed\n"
                    + "	ok 2 - example_mock_test\n"
                    + "kunit example: all tests passed\n"
                    + "ok 2 - example\n";

        String[][] expectedLeafResults = {
            {"sysctl_test.sysctl_test_dointvec_null_tbl_data", "PASSED", ""},
            {"sysctl_test.example.example_simple_test", "PASSED", ""},
            {"sysctl_test.example.example_mock_test", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_table_len_is_zero", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_table_read_but_position_set", "PASSED", ""},
            {"example.example_simple_test", "PASSED", ""},
            {"example.example_mock_test", "PASSED", ""}
        };
        String[] expectedRootResults = {"sysctl_test.example", "PASSED", ""};
        checkKTap(ktapResults, expectedLeafResults, expectedRootResults);
    }

    @Test
    public void test_passed_all_passed_ktap() {
        // Example from KUnits: test_data/test_is_test_passed-all_passed.log
        String ktapResults =
                "TAP version 14\n"
                    + "1..2\n"
                    + "	# Subtest: sysctl_test\n"
                    + "	1..8\n"
                    + "	# sysctl_test_dointvec_null_tbl_data: sysctl_test_dointvec_null_tbl_data"
                    + " passed\n"
                    + "	ok 1 - sysctl_test_dointvec_null_tbl_data\n"
                    + "	# sysctl_test_dointvec_table_maxlen_unset:"
                    + " sysctl_test_dointvec_table_maxlen_unset passed\n"
                    + "	ok 2 - sysctl_test_dointvec_table_maxlen_unset\n"
                    + "	# sysctl_test_dointvec_table_len_is_zero:"
                    + " sysctl_test_dointvec_table_len_is_zero passed\n"
                    + "	ok 3 - sysctl_test_dointvec_table_len_is_zero\n"
                    + "	# sysctl_test_dointvec_table_read_but_position_set:"
                    + " sysctl_test_dointvec_table_read_but_position_set passed\n"
                    + "	ok 4 - sysctl_test_dointvec_table_read_but_position_set\n"
                    + "	# sysctl_test_dointvec_happy_single_positive:"
                    + " sysctl_test_dointvec_happy_single_positive passed\n"
                    + "	ok 5 - sysctl_test_dointvec_happy_single_positive\n"
                    + "	# sysctl_test_dointvec_happy_single_negative:"
                    + " sysctl_test_dointvec_happy_single_negative passed\n"
                    + "	ok 6 - sysctl_test_dointvec_happy_single_negative\n"
                    + "	# sysctl_test_dointvec_single_less_int_min:"
                    + " sysctl_test_dointvec_single_less_int_min passed\n"
                    + "	ok 7 - sysctl_test_dointvec_single_less_int_min\n"
                    + "	# sysctl_test_dointvec_single_greater_int_max:"
                    + " sysctl_test_dointvec_single_greater_int_max passed\n"
                    + "	ok 8 - sysctl_test_dointvec_single_greater_int_max\n"
                    + "kunit sysctl_test: all tests passed\n"
                    + "ok 1 - sysctl_test\n"
                    + "	# Subtest: example\n"
                    + "	1..2\n"
                    + "init_suite\n"
                    + "	# example_simple_test: initializing\n"
                    + "	# example_simple_test: example_simple_test passed\n"
                    + "	ok 1 - example_simple_test\n"
                    + "	# example_mock_test: initializing\n"
                    + "	# example_mock_test: example_mock_test passed\n"
                    + "	ok 2 - example_mock_test\n"
                    + "kunit example: all tests passed\n"
                    + "ok 2 - example\n";
        String[][] expectedLeafResults = {
            {"sysctl_test.sysctl_test_dointvec_null_tbl_data", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_table_maxlen_unset", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_table_len_is_zero", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_table_read_but_position_set", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_happy_single_positive", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_happy_single_negative", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_single_less_int_min", "PASSED", ""},
            {"sysctl_test.sysctl_test_dointvec_single_greater_int_max", "PASSED", ""},
            {"example.example_simple_test", "PASSED", ""},
            {"example.example_mock_test", "PASSED", ""},
        };
        String[] expectedRootResults = {"sysctl_test.example", "PASSED", ""};
        checkKTap(ktapResults, expectedLeafResults, expectedRootResults);
    }

    private void checkKTap(
            String ktapResults, String[][] expectedLeafResults, String[] expectedRootResults) {
        // Setup
        String[][] expectedResults =
                mParseResolution == KTapResultParser.ParseResolution.INDIVIDUAL_LEAVES
                        ? expectedLeafResults
                        : new String[][] {expectedRootResults};
        CollectingTestListener listener = new CollectingTestListener();
        String moduleName = "kunit_test_module.ko";
        listener.testRunStarted(moduleName, 1);

        KTapResultParser.applyKTapResultToListener(
                listener, moduleName, ktapResults, mParseResolution);

        listener.testRunEnded(0, new HashMap<String, Metric>());
        List<TestRunResult> testRunResults = listener.getMergedTestRunResults();

        assertEquals(1, testRunResults.size());

        int i = 0;
        for (Map.Entry<TestDescription, TestResult> entry :
                testRunResults.get(0).getTestResults().entrySet()) {
            String[] testResult = new String[3];
            assertEquals(moduleName, entry.getKey().getClassName());
            testResult[0] = entry.getKey().getTestName();
            testResult[1] = entry.getValue().getStatus().toString();
            testResult[2] = entry.getValue().getStackTrace();
            if (testResult[2] == null) {
                testResult[2] = "";
            }
            assertArrayEquals(expectedResults[i++], testResult);
        }

        assertEquals(expectedResults.length, i);
    }
}
