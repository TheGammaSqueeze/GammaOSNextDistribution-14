/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.cluster;

import static org.junit.Assert.*;

import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil2;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;

/** Unit tests for {@link SubprocessReportingHelper} */
@RunWith(JUnit4.class)
public class SubprocessReportingHelperTest {
    private SubprocessReportingHelper mHelper;
    private File mWorkDir;
    private String mClasspath;

    @Before
    public void setUp() throws IOException {
        mWorkDir = FileUtil.createTempDir("tfjar");
        mClasspath = System.getProperty("java.class.path");
        mHelper = new SubprocessReportingHelper("host", mClasspath, mWorkDir, null);
    }

    @After
    public void tearDown() {
        FileUtil.recursiveDelete(mWorkDir);
    }

    @Test
    public void testCreateSubprocessReporterJar() throws IOException {
        File jar = mHelper.buildSubprocessReporterJar();
        assertNotNull(jar);
        File extractedJar = ZipUtil2.extractZipToTemp(jar, "tmp-jar");
        try {
            assertNotNull(FileUtil.findFile(extractedJar, "LegacySubprocessResultsReporter.class"));
            assertNotNull(FileUtil.findFile(extractedJar, "SubprocessTestResultsParser.class"));
            assertNotNull(FileUtil.findFile(extractedJar, "SubprocessEventHelper.class"));
            assertNotNull(FileUtil.findFile(extractedJar, "SubprocessResultsReporter.class"));
            assertNotNull(FileUtil.findFile(extractedJar, "host.xml"));
        } finally {
            FileUtil.recursiveDelete(extractedJar);
        }
    }
}
