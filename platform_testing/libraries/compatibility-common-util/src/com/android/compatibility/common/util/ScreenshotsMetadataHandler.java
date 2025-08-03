/*
 * Copyright (C) 2015 The Android Open Source Project
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

import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Handles conversion of results to/from files.
 */
public class ScreenshotsMetadataHandler {

    private static final String ENCODING = "UTF-8";
    private static final String TYPE = "org.kxml2.io.KXmlParser,org.kxml2.io.KXmlSerializer";
    private static final String NS = null;
    public static final String SCREENSHOTS_METADATA_FILE_NAME = "screenshots_metadata.xml";

    // XML constants
    private static final String CASE_TAG = "TestCase";
    private static final String MODULE_TAG = "Module";
    private static final String NAME_ATTR = "name";
    private static final String RESULT_TAG = "Result";
    private static final String TEST_TAG = "Test";

    /**
     * @param result - result of a single Compatibility invocation
     * @param resultDir - directory where to write the screenshots metadata file
     * @return The screenshots metadata file created.
     */
    public static File writeResults(IInvocationResult result, File resultDir)
            throws IOException, XmlPullParserException {
        File screenshotsMetadataFile = new File(resultDir, SCREENSHOTS_METADATA_FILE_NAME);
        OutputStream stream = new FileOutputStream(screenshotsMetadataFile);
        XmlSerializer serializer = XmlPullParserFactory.newInstance(TYPE, null).newSerializer();
        serializer.setOutput(stream, ENCODING);
        serializer.startDocument(ENCODING, false);
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
        serializer.processingInstruction(
                "xml-stylesheet type=\"text/xsl\" href=\"compatibility_result.xsl\"");
        serializer.startTag(NS, RESULT_TAG);
        // Results
        for (IModuleResult module : result.getModules()) {
            serializer.startTag(NS, MODULE_TAG);
            serializer.attribute(NS, NAME_ATTR, module.getName());
            for (ICaseResult cr : module.getResults()) {
                serializer.startTag(NS, CASE_TAG);
                serializer.attribute(NS, NAME_ATTR, cr.getName());
                for (ITestResult r : cr.getResults()) {
                    TestStatus status = r.getResultStatus();
                    if (status == null) {
                        continue; // test was not executed, don't report
                    }
                    serializer.startTag(NS, TEST_TAG);
                    serializer.attribute(NS, NAME_ATTR, r.getName());

                    TestScreenshotsMetadata screenshotsMetadata = r.getTestScreenshotsMetadata();
                    if (screenshotsMetadata != null) {
                        TestScreenshotsMetadata.serialize(serializer, screenshotsMetadata);
                    }
                    serializer.endTag(NS, TEST_TAG);
                }
                serializer.endTag(NS, CASE_TAG);
            }
            serializer.endTag(NS, MODULE_TAG);
        }
        serializer.endDocument();
        return screenshotsMetadataFile;
    }
}
