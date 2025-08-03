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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.result.LegacySubprocessResultsReporter;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/** Unit tests for {@link SubprocessConfigBuilder} */
@RunWith(JUnit4.class)
public class SubprocessConfigBuilderTest {

    private static final String REPORTER_CLASS = LegacySubprocessResultsReporter.class.getName();

    private SubprocessConfigBuilder mConfigBuilder;
    private String mClasspath;
    private File mWorkDir;

    @Before
    public void setUp() throws IOException {
        mConfigBuilder = new SubprocessConfigBuilder();
        mClasspath = System.getProperty("java.class.path");
        mWorkDir = FileUtil.createTempDir("tfjar");
    }

    @After
    public void tearDown() {
        FileUtil.recursiveDelete(mWorkDir);
    }

    @Test
    public void testCreateWrapperConfig() throws Exception {
        String oriConfigName = "host";
        String reporterPort = "1024";
        mConfigBuilder
                .setClasspath(mClasspath)
                .setWorkingDir(mWorkDir)
                .setOriginalConfig(oriConfigName)
                .setPort(reporterPort);
        File config = mConfigBuilder.build();
        assertNotNull(config);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(config);
        verifyWrapperXml(doc, reporterPort);
    }

    @Test
    public void testCreateWrapperConfig_forCommandWithSlashes() throws Exception {
        String oriConfigName = "util/timewaster";
        String reporterPort = "1024";
        mConfigBuilder
                .setClasspath(mClasspath)
                .setWorkingDir(mWorkDir)
                .setOriginalConfig(oriConfigName)
                .setPort(reporterPort);
        File config = mConfigBuilder.build();
        assertNotNull(config);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(config);
        verifyWrapperXml(doc, reporterPort);
    }

    private void verifyWrapperXml(Document doc, String reporterPort) {
        NodeList reporters = doc.getElementsByTagName("result_reporter");
        assertTrue(0 < reporters.getLength());
        Element reporter = (Element) reporters.item(reporters.getLength() - 1);
        String reporterClass = reporter.getAttribute("class");
        assertEquals(REPORTER_CLASS, reporterClass);
        NodeList option = reporter.getElementsByTagName("option");
        assertEquals(1, option.getLength());
        String optionName = ((Element) option.item(0)).getAttribute("name");
        assertEquals("subprocess-report-port", optionName);
        String optionPort = ((Element) option.item(0)).getAttribute("value");
        assertEquals(reporterPort, optionPort);
    }
}
