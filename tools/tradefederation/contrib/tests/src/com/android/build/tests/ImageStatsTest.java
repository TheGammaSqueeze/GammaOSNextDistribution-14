/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.tests;

import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Unit tests for {@link ImageStats} */
@RunWith(JUnit4.class)
public class ImageStatsTest {

    private static final String TEST_DATA = "  ["
            + "{\n" +
            "    \"SHA256\": \"94557affe476aaf1bb77bc2114375162c2a7a066d3d4cfde9a545d7b1bcf\",\n" +
            "    \"Name\": \"/system/app/WallpapersBReel2017/WallpapersBReel2017.apk\",\n" +
            "    \"Size\": 164424453\n" +
            "  },\n" +
            "  {\n" +
            "    \"SHA256\": \"c15be36f620a78b98c79cf236175b33a09df8f9946f5814ba27ab2b746476\",\n" +
            "    \"Name\": \"/system/app/Chrome/Chrome.apk\",\n" +
            "    \"Size\": 124082279\n" +
            "  },\n" +
            "  {\n" +
            "    \"SHA256\": \"bff67553c1850054b00324bf24c7e2c57e2d5ca67c475919fde77a661e909\",\n" +
            "    \"Name\": \"/system/priv-app/Velvet/Velvet.apk\",\n" +
            "    \"Size\": 92966112\n" +
            "  },\n" +
            "  {\n" +
            "    \"SHA256\": \"c15be36f620a78b98c79cf236175b33a09df8f9946f5814ba27ab2b746476\",\n" +
            "    \"Name\": \"/system/framework/ext.jar\",\n" +
            "    \"Size\": 1790897\n" +
            "  },\n" +
            "  {\n" +
            "    \"SHA256\": \"c15be36f620a78b98c79cf236175b33a09df8f9946f5814ba27ab2b746476\",\n" +
            "    \"Name\": \"/system/fonts/NotoSansEgyptianHieroglyphs-Regular.ttf\",\n" +
            "    \"Size\": 505436\n" +
            "  },\n" +
            "  {\n" +
            "    \"SHA256\": \"c15be36f620a78b98c79cf236175b33a09df8f9946f5814ba27ab2b746476\",\n" +
            "    \"Name\": \"/system/bin/ip6tables\",\n" +
            "    \"Size\": 500448\n" +
            "  },\n" +
            "  {\n" +
            "    \"SHA256\": \"c15be36f620a78b98c79cf236175b33a09df8f9946f5814ba27ab2b746476\",\n" +
            "    \"Name\": \"/system/usr/share/zoneinfo/tzdata\",\n" +
            "    \"Size\": 500393\n" +
            "  },\n" +
            "  {\n" +
            "    \"SHA256\": \"c15be36f620a78b98c79cf236175b33a09df8f9946f5814ba27ab2b746476\",\n" +
            "    \"Name\": \"/system/fonts/NotoSansCuneiform-Regular.ttf\",\n" +
            "    \"Size\": 500380\n" +
            "  },\n" +
            "  {\n" +
            "    \"SHA256\": \"c15be36f620a78b98c79cf236175b33a09df8f9946f5814ba27ab2b746476\",\n" +
            "    \"Name\": \"/system/framework/core-oj.jar\",\n" +
            "    \"Size\": 126391\n" +
            "  },\n" +
            "  {\n" +
            "    \"SHA256\": \"c15be36f620a78b98c79cf236175b33a09df8f9946f5814ba27ab2b746476\",\n" +
            "    \"Name\": \"/system/framework/com.quicinc.cne.jar\",\n" +
            "    \"Size\": 122641\n" +
            "  }"
            + "]";

    private static final String TEST_DATA_SMALL = " ["
            + "{\n" +
            "    \"SHA256\": \"94557affe476aaf1bb77bc2114375162c2a7a066d3d4cfde9a545d7b1bcfa\",\n" +
            "    \"Name\": \"/system/app/WallpapersBReel2017/WallpapersBReel2017.apk\",\n" +
            "    \"Size\": 164424453\n" +
            "  },\n" +
            "  {\n" +
            "    \"SHA256\": \"c15be36f620a78b98c79cf236175b33a09df8f9946f5814ba27ab2b746476\",\n" +
            "    \"Name\": \"/system/app/Chrome/Chrome.apk\",\n" +
            "    \"Size\": 124082279\n" +
            "  }"
            + "]";

    private static final Map<String, Long> PARSED_TEST_DATA = new HashMap<>();

    static {
        PARSED_TEST_DATA.put("/system/app/WallpapersBReel2017/WallpapersBReel2017.apk", 164424453L);
        PARSED_TEST_DATA.put("/system/app/Chrome/Chrome.apk", 124082279L);
        PARSED_TEST_DATA.put("/system/priv-app/Velvet/Velvet.apk", 92966112L);
        PARSED_TEST_DATA.put("/system/framework/ext.jar", 1790897L);
        PARSED_TEST_DATA.put("/system/fonts/NotoSansEgyptianHieroglyphs-Regular.ttf", 505436L);
        PARSED_TEST_DATA.put("/system/bin/ip6tables", 500448L);
        PARSED_TEST_DATA.put("/system/usr/share/zoneinfo/tzdata", 500393L);
        PARSED_TEST_DATA.put("/system/fonts/NotoSansCuneiform-Regular.ttf", 500380L);
        PARSED_TEST_DATA.put("/system/framework/core-oj.jar", 126391L);
        PARSED_TEST_DATA.put("/system/framework/com.quicinc.cne.jar", 122641L);
    }

    private ImageStats mImageStats = null;

    private File mTestStatsFile;

    @Before
    public void setup() throws Exception {
        mImageStats = new ImageStats();
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.deleteFile(mTestStatsFile);
    }

    @Test
    public void testParseFileSizes() throws Exception {
        mTestStatsFile = FileUtil.createTempFile("stats_temp", ".json");
        FileUtil.writeToFile(TEST_DATA, mTestStatsFile);
        Map<String, Long> ret = mImageStats.parseFileSizes(mTestStatsFile);
        Assert.assertEquals(
                "parsed test file sizes mismatches expectations", PARSED_TEST_DATA, ret);
    }

    /** Verifies that regular matching pattern without capturing group works as expected */
    @Test
    public void testGetAggregationLabel_regular() throws Exception {
        String fileName = "/system/app/WallpapersBReel2017/WallpapersBReel2017.apk";
        Pattern pattern = Pattern.compile("^.+\\.apk$");
        final String label = "foobar";
        Assert.assertEquals(
                "unexpected label transformation output",
                label,
                mImageStats.getAggregationLabel(pattern.matcher(fileName), label));
    }

    /** Verifies that matching pattern with corresponding capturing groups works as expected */
    @Test
    public void testGetAggregationLabel_capturingGroups() throws Exception {
        String fileName = "/system/app/WallpapersBReel2017/WallpapersBReel2017.apk";
        Pattern pattern = Pattern.compile("^/system/(.+?)/.+\\.(.+)$");
        final String label = "folder-\\1-ext-\\2";
        Matcher m = pattern.matcher(fileName);
        Assert.assertTrue(
                "this shouldn't fail unless test case isn't written correctly", m.matches());
        Assert.assertEquals(
                "unexpected label transformation output",
                "folder-app-ext-apk",
                mImageStats.getAggregationLabel(m, label));
    }

    /**
     * Verifies that matching pattern with capturing groups but partial back references works as
     * expected
     */
    @Test
    public void testGetAggregationLabel_capturingGroups_partialBackReference() throws Exception {
        String fileName = "/system/app/WallpapersBReel2017/WallpapersBReel2017.apk";
        Pattern pattern = Pattern.compile("^/system/(.+?)/.+\\.(.+)$");
        final String label = "ext-\\2";
        Matcher m = pattern.matcher(fileName);
        Assert.assertTrue(
                "this shouldn't fail unless test case isn't written correctly", m.matches());
        Assert.assertEquals(
                "unexpected label transformation output",
                "ext-apk",
                mImageStats.getAggregationLabel(m, label));
    }

    /** Verifies that aggregating the sample input with patterns works as expected */
    @Test
    public void testPerformAggregation() throws Exception {
        Map<Pattern, String> mapping = new HashMap<>();
        mapping.put(Pattern.compile("^.+\\.(.+)"), "ext-\\1"); // aggregate by extension
        mapping.put(Pattern.compile("^/system/(.+?)/.+$"), "folder-\\1"); // aggregate by folder
        Map<String, String> ret = mImageStats.performAggregation(PARSED_TEST_DATA, mapping);
        Assert.assertEquals(
                "failed to verify aggregated size for category 'ext-apk'",
                "381472844",
                ret.get("ext-apk"));
        Assert.assertEquals(
                "failed to verify aggregated size for category 'ext-jar'",
                "2039929",
                ret.get("ext-jar"));
        Assert.assertEquals(
                "failed to verify aggregated size for category 'ext-ttf'",
                "1005816",
                ret.get("ext-ttf"));
        Assert.assertEquals(
                "failed to verify aggregated size for category 'uncategorized'",
                "0",
                ret.get("uncategorized"));
        Assert.assertEquals(
                "failed to verify aggregated size for category 'total'",
                "385519430",
                ret.get("total"));
    }

    /** Verifies all the individual file size metrics are added as expected.*/
    @Test
    public void testParseFinalMetrics() throws Exception {
        Map<String, String> finalMetrics = new HashMap<>();
        mTestStatsFile = FileUtil.createTempFile("stats_temp", ".json");
        FileUtil.writeToFile(TEST_DATA_SMALL, mTestStatsFile);
        mImageStats.parseFinalMetrics(mTestStatsFile, finalMetrics);
        Assert.assertEquals("Total number of metrics is not as expected", 5, finalMetrics.size());
        Assert.assertEquals(
                "Failed to get WallpapersBReel2017.apk file metris.",
                "164424453",
                finalMetrics.get("/system/app/WallpapersBReel2017/WallpapersBReel2017.apk"));
        Assert.assertEquals(
                "Failed to get Chrome.apk file metris'",
                "124082279",
                finalMetrics.get("/system/app/Chrome/Chrome.apk"));
        Assert.assertEquals(
                "failed to verify aggregated size for category 'categorized'",
                "0",
                finalMetrics.get("categorized"));
        Assert.assertEquals(
                "failed to verify aggregated size for category 'uncategorized'",
                "288506732",
                finalMetrics.get("uncategorized"));
        Assert.assertEquals(
                "failed to verify aggregated size for category 'total'",
                "288506732",
                finalMetrics.get("total"));
    }
}
