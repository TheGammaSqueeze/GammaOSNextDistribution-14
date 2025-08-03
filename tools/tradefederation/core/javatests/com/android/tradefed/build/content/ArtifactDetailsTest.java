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
package com.android.tradefed.build.content;

import static org.junit.Assert.assertEquals;

import com.android.tradefed.build.content.ArtifactDetails.ArtifactFileDescriptor;
import com.android.tradefed.util.FileUtil;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** Unit tests for {@link ArtifactDetails}. */
@RunWith(JUnit4.class)
public class ArtifactDetailsTest {

    @Test
    public void testCompare() throws Exception {
        File baseJson = generateBaseContent();
        File currentJson = generateCurrentContent();
        try {
            String artifactName = "mysuite.zip";
            ArtifactDetails base = ArtifactDetails.parseFile(baseJson, artifactName);
            ArtifactDetails presubmit = ArtifactDetails.parseFile(currentJson, artifactName);
            List<ArtifactFileDescriptor> diffs = ArtifactDetails.diffContents(base, presubmit);
            assertEquals(2, diffs.size());
        } finally {
            FileUtil.deleteFile(baseJson);
            FileUtil.deleteFile(currentJson);
        }
    }

    @Test
    public void testCompareWithBuildId() throws Exception {
        File baseJson = generateBaseContent();
        File currentJson = generateCurrentContent();
        try {
            String artifactName = "mine-tests-P9999.zip";
            ArtifactDetails base =
                    ArtifactDetails.parseFile(baseJson, artifactName, "8888", "P9999");
            ArtifactDetails presubmit = ArtifactDetails.parseFile(currentJson, artifactName);
            List<ArtifactFileDescriptor> diffs = ArtifactDetails.diffContents(base, presubmit);
            assertEquals(1, diffs.size());
        } finally {
            FileUtil.deleteFile(baseJson);
            FileUtil.deleteFile(currentJson);
        }
    }

    private File generateBaseContent() throws IOException {
        File content = FileUtil.createTempFile("artifacts-details-test", ".json");
        String baseContent =
                "[\n"
                        + "  {\n"
                        + "  \"artifact\": \"mysuite.zip\",\n"
                        + "  \"details\": [\n"
                        + "      {\n"
                        + "        \"digest\":"
                        + " \"acc469f0e5461328f89bd3afb3cfac52b40e35481d90a9899cfcdeb3c8eac627\",\n"
                        + "        \"path\": \"host/testcases/module1/someapk.apk\",\n"
                        + "        \"size\": 8542\n"
                        + "      },\n"
                        + "      {\n"
                        + "        \"digest\":"
                        + " \"b69ad7f80ed55963c5782bee548e19b167406a03d5ae9204031f2ca7ff8b6304\",\n"
                        + "        \"path\": \"host/testcases/module2/otherfile.xml\",\n"
                        + "        \"size\": 762\n"
                        + "      }\n"
                        + "    ]\n"
                        + "  },\n"
                        + "  {\n"
                        + "  \"artifact\": \"mine-tests-8888.zip\",\n"
                        + "  \"details\": [\n"
                        + "      {\n"
                        + "        \"digest\":"
                        + " \"acc469f0e5461328f89bd3afb3cfac52b40e35481d90a9899cfcdeb3c8eac627\",\n"
                        + "        \"path\": \"host/testcases/module1/someapk.apk\",\n"
                        + "        \"size\": 8542\n"
                        + "      },\n"
                        + "      {\n"
                        + "        \"digest\":"
                        + " \"b69ad7f80ed55963c5782bee548e19b167406a03d5ae9204031f2ca7ff8b6304\",\n"
                        + "        \"path\": \"host/testcases/module2/otherfile.xml\",\n"
                        + "        \"size\": 762\n"
                        + "      }\n"
                        + "    ]\n"
                        + "  }\n"
                        + "]";
        FileUtil.writeToFile(baseContent, content);
        return content;
    }

    private File generateCurrentContent() throws IOException {
        File content = FileUtil.createTempFile("artifacts-details-test", ".json");
        String currentContent =
                "[\n"
                        + "  {\n"
                        + "  \"artifact\": \"mysuite.zip\",\n"
                        + "  \"details\": [\n"
                        + "      {\n"
                        + "        \"digest\": \"8888\",\n"
                        + "        \"path\": \"host/testcases/module1/someapk.apk\",\n"
                        + "        \"size\": 8542\n"
                        + "      },\n"
                        + "      {\n"
                        + "        \"digest\":"
                        + " \"b69ad7f80ed55963c5782bee548e19b167406a03d5ae9204031f2ca7ff8b6304\",\n"
                        + "        \"path\": \"host/testcases/module2/otherfile.xml\",\n"
                        + "        \"size\": 762\n"
                        + "      },\n"
                        + "      {\n"
                        + "        \"digest\": \"9999\",\n"
                        + "        \"path\": \"host/testcases/module2/newfile.xml\",\n"
                        + "        \"size\": 762\n"
                        + "      }\n"
                        + "    ]\n"
                        + "  },\n"
                        + "  {\n"
                        + "  \"artifact\": \"mine-tests-P9999.zip\",\n"
                        + "  \"details\": [\n"
                        + "      {\n"
                        + "        \"digest\":"
                        + " \"acc469f0e5461328f89bd3afb3cfac52b40e35481d90a9899cfcdeb3c8eac627\",\n"
                        + "        \"path\": \"host/testcases/module1/someapk.apk\",\n"
                        + "        \"size\": 8542\n"
                        + "      },\n"
                        + "      {\n"
                        + "        \"digest\":"
                        + " \"b69ad7f80ed55963c5782bee54aaaaaaaaaaaaaaaaa31f2ca7ff8b6304\",\n"
                        + "        \"path\": \"host/testcases/module2/otherfile.xml\",\n"
                        + "        \"size\": 762\n"
                        + "      }\n"
                        + "    ]\n"
                        + "  }\n"
                        + "]";
        FileUtil.writeToFile(currentContent, content);
        return content;
    }
}
