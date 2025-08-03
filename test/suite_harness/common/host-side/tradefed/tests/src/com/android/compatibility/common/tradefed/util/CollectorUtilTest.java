/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.compatibility.common.tradefed.util;

import com.android.tradefed.util.FileUtil;

import java.io.File;

import junit.framework.TestCase;

/**
 * Unit tests for {@link CollectorUtil}
 */
public class CollectorUtilTest extends TestCase {

    private static final String UNFORMATTED_JSON = "{"
            + "\"stream_name_1\":"
            + "{\"id\":1,\"key1\":\"value1\"},"
            + "\"stream_name_2\":"
            + "{\"id\":1,\"key1\":\"value3\"},"
            + "\"stream_name_1\":"
            + "{\"id\":2,\"key1\":\"value2\"},"
            + "}";

    private static final String REFORMATTED_JSON = "{"
            + "\"stream_name_2\":"
            + "["
            + "{\"id\":1,\"key1\":\"value3\"}"
            + "],"
            + "\"stream_name_1\":"
            + "["
            + "{\"id\":1,\"key1\":\"value1\"},"
            + "{\"id\":2,\"key1\":\"value2\"}"
            + "]"
            + "}";

    private static final String BASE_JSON_1 =
            "{\"r8_2__h_2_4\":{\"test_name\":\"testRandomRead\",\"filesystem_io_rate_mbps\":175.59324391013755,\"performance_class\":31},\"r8_2__h_1_4\":{\"test_name\":\"testRandomRead\",\"filesystem_io_rate_mbps\":175.59324391013755,\"performance_class\":33},\"r8_2__h_1_3\":{\"test_name\":\"testSingleSequentialRead\",\"filesystem_io_rate_mbps\":932.9734657750472,\"performance_class\":33},\"r8_2__h_2_3\":{\"test_name\":\"testSingleSequentialRead\",\"filesystem_io_rate_mbps\":932.9734657750472,\"performance_class\":31},\"r8_2__h_1_2\":{\"test_name\":\"testRandomUpdate\",\"filesystem_io_rate_mbps\":39.89857839786116,\"performance_class\":33},\"r8_2__h_2_2\":{\"test_name\":\"testRandomUpdate\",\"filesystem_io_rate_mbps\":39.89857839786116,\"performance_class\":31},\"r8_2__h_1_1\":{\"test_name\":\"testSingleSequentialWrite\",\"filesystem_io_rate_mbps\":129.3339939566921,\"performance_class\":33},\"r8_2__h_2_1\":{\"test_name\":\"testSingleSequentialWrite\",\"filesystem_io_rate_mbps\":129.3339939566921,\"performance_class\":31}}";

    private static final String BASE_JSON_2 =
            "{\"r8_2__h_2_4\":{\"test_name\":\"testRandomRead\",\"filesystem_io_rate_mbps\":193.31682502849046,\"performance_class\":31},\"r8_2__h_1_4\":{\"test_name\":\"testRandomRead\",\"filesystem_io_rate_mbps\":193.31682502849046,\"performance_class\":33},\"r8_2__h_2_3\":{\"test_name\":\"testSingleSequentialRead\",\"filesystem_io_rate_mbps\":916.3888536493184,\"performance_class\":31},\"r8_2__h_1_3\":{\"test_name\":\"testSingleSequentialRead\",\"filesystem_io_rate_mbps\":916.3888536493184,\"performance_class\":33},\"r8_2__h_1_2\":{\"test_name\":\"testRandomUpdate\",\"filesystem_io_rate_mbps\":39.9265724519172,\"performance_class\":33},\"r8_2__h_2_2\":{\"test_name\":\"testRandomUpdate\",\"filesystem_io_rate_mbps\":39.9265724519172,\"performance_class\":31},\"r8_2__h_1_1\":{\"test_name\":\"testSingleSequentialWrite\",\"filesystem_io_rate_mbps\":131.29526574217743,\"performance_class\":33},\"r8_2__h_2_1\":{\"test_name\":\"testSingleSequentialWrite\",\"filesystem_io_rate_mbps\":131.29526574217743,\"performance_class\":31}}";

    private static final String MERGED_JSON =
            "{\"r8_2__h_1_1\":[{\"filesystem_io_rate_mbps\":129.3339939566921,\"test_name\":\"testSingleSequentialWrite\",\"performance_class\":33},{\"filesystem_io_rate_mbps\":131.29526574217743,\"test_name\":\"testSingleSequentialWrite\",\"performance_class\":33}],\"r8_2__h_1_4\":[{\"filesystem_io_rate_mbps\":175.59324391013755,\"test_name\":\"testRandomRead\",\"performance_class\":33},{\"filesystem_io_rate_mbps\":193.31682502849046,\"test_name\":\"testRandomRead\",\"performance_class\":33}],\"r8_2__h_2_3\":[{\"filesystem_io_rate_mbps\":932.9734657750472,\"test_name\":\"testSingleSequentialRead\",\"performance_class\":31},{\"filesystem_io_rate_mbps\":916.3888536493184,\"test_name\":\"testSingleSequentialRead\",\"performance_class\":31}],\"r8_2__h_2_4\":[{\"filesystem_io_rate_mbps\":175.59324391013755,\"test_name\":\"testRandomRead\",\"performance_class\":31},{\"filesystem_io_rate_mbps\":193.31682502849046,\"test_name\":\"testRandomRead\",\"performance_class\":31}],\"r8_2__h_1_2\":[{\"filesystem_io_rate_mbps\":39.89857839786116,\"test_name\":\"testRandomUpdate\",\"performance_class\":33},{\"filesystem_io_rate_mbps\":39.9265724519172,\"test_name\":\"testRandomUpdate\",\"performance_class\":33}],\"r8_2__h_2_1\":[{\"filesystem_io_rate_mbps\":129.3339939566921,\"test_name\":\"testSingleSequentialWrite\",\"performance_class\":31},{\"filesystem_io_rate_mbps\":131.29526574217743,\"test_name\":\"testSingleSequentialWrite\",\"performance_class\":31}],\"r8_2__h_1_3\":[{\"filesystem_io_rate_mbps\":932.9734657750472,\"test_name\":\"testSingleSequentialRead\",\"performance_class\":33},{\"filesystem_io_rate_mbps\":916.3888536493184,\"test_name\":\"testSingleSequentialRead\",\"performance_class\":33}],\"r8_2__h_2_2\":[{\"filesystem_io_rate_mbps\":39.89857839786116,\"test_name\":\"testRandomUpdate\",\"performance_class\":31},{\"filesystem_io_rate_mbps\":39.9265724519172,\"test_name\":\"testRandomUpdate\",\"performance_class\":31}]}";

    public void testReformatJsonString() throws Exception {
        String reformattedJson = CollectorUtil.reformatJsonString(UNFORMATTED_JSON);
        assertEquals(reformattedJson, REFORMATTED_JSON);
    }

    public void testMerge() throws Exception {
        String reformatedBaseJson1 = CollectorUtil.reformatJsonString(BASE_JSON_1);
        String reformatedBaseJson2 = CollectorUtil.reformatJsonString(BASE_JSON_2);

        File tempDir = FileUtil.createTempDir("CollectorUtilTest");
        File src = new File(tempDir, "src");
        File dest = new File(tempDir, "dest");
        FileUtil.writeToFile(reformatedBaseJson1, src);
        FileUtil.writeToFile(reformatedBaseJson2, dest);

        CollectorUtil.merge(src, dest);
        String mergedJson = FileUtil.readStringFromFile(dest);
        assertEquals(mergedJson, MERGED_JSON);

        FileUtil.recursiveDelete(tempDir);
    }
}
