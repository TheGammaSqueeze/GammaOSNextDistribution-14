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

package com.android.compatibility.common.util;

import static org.junit.Assert.assertEquals;

import com.android.tradefed.util.FileUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.FileOutputStream;
import java.security.CodeSource;

/** Unit tests for {@link UrlReplacement}. */
@RunWith(JUnit4.class)
public final class UrlReplacementTest {
    private static final String URL_REPLACEMENT_CONFIG =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<urlReplacement>\n"
                    + "<dynamicConfigUrl>http://dynamice-config-server.com/</dynamicConfigUrl>\n"
                    + "<entry url=\"http://url1\"><replacement>http://url2</replacement></entry>\n"
                    + "</urlReplacement>";

    @Before
    public void setUp() {
        UrlReplacement.initialized = false;
    }

    @Test
    public void getUrlReplacementConfigFile_fromJarDirectory() throws Exception {
        CodeSource codeSource = UrlReplacement.class.getProtectionDomain().getCodeSource();
        String toolDirectory = new File(codeSource.getLocation().toURI()).getParent();
        File replacementFile = UrlReplacement.getUrlReplacementConfigFile();

        assertEquals(
                toolDirectory + File.separator + "UrlReplacement.xml",
                replacementFile.getAbsolutePath());
    }

    @Test
    public void init_verifyUrlReplacement() throws Exception {
        File configFile = createUrlReplacementFile();
        try {
            UrlReplacement.init(configFile);

            assertEquals(
                    "http://dynamice-config-server.com/",
                    UrlReplacement.getDynamicConfigServerUrl());
            assertEquals("http://url2", UrlReplacement.getUrlReplacementMap().get("http://url1"));
        } finally {
            FileUtil.deleteFile(configFile);
        }
    }

    private File createUrlReplacementFile() throws Exception {
        File configFile = File.createTempFile("urlReplacement", "xml");
        FileOutputStream stream = new FileOutputStream(configFile);
        stream.write(URL_REPLACEMENT_CONFIG.getBytes());
        stream.flush();
        stream.close();
        return configFile;
    }
}
