/*
 * Copyright (C) 2022 The Android Open Source Project
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

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

/**
 * Utility class to add test case screenshot metadata to the report. This class records per-case
 * screenshot metadata for CTS Verifier. If this field is used for large test suites like CTS, it
 * may cause performance issues in APFE. Thus please do not use this class in other test suites.
 */
public class TestScreenshotsMetadata implements Serializable {

    // XML constants
    private static final String SCREENSHOTS_TAG = "Screenshots";
    private static final String SCREENSHOT_TAG = "Screenshot";
    private static final String NAME_ATTR = "name";
    private static final String DESCRIPTION_ATTR = "description";

    private final String mTestName;
    private final Set<TestScreenshotsMetadata.ScreenshotMetadata> mScreenshotMetadataSet;

    /**
     * Constructor of test screenshots metadata.
     *
     * @param screenshots a Set of ScreenshotMetadata.
     */
    public TestScreenshotsMetadata(String testName,
            Set<TestScreenshotsMetadata.ScreenshotMetadata> screenshots) {
        mTestName = testName;
        mScreenshotMetadataSet = screenshots;
    }

    /** Get test name */
    public String getTestName() {
        return mTestName;
    }

    /** Get a set of ScreenshotMetadata. */
    public Set<TestScreenshotsMetadata.ScreenshotMetadata> getScreenshotMetadataSet() {
        return mScreenshotMetadataSet;
    }

    @Override
    public String toString() {
        ArrayList<String> arr = new ArrayList<>();
        for (TestScreenshotsMetadata.ScreenshotMetadata e : mScreenshotMetadataSet) {
            arr.add(e.toString());
        }
        return String.join(", ", arr);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TestScreenshotsMetadata)) {
            return false;
        }
        TestScreenshotsMetadata that = (TestScreenshotsMetadata) o;
        return Objects.equals(mTestName, that.mTestName)
                && Objects.equals(mScreenshotMetadataSet, that.mScreenshotMetadataSet);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(mTestName, mScreenshotMetadataSet);
    }

    /**
     * Serializes a given {@link TestScreenshotsMetadata} to XML.
     *
     * @param serializer          given serializer.
     * @param screenshotsMetadata test screenshots metadata.
     */
    public static void serialize(
            XmlSerializer serializer, TestScreenshotsMetadata screenshotsMetadata)
            throws IOException {
        if (screenshotsMetadata == null) {
            throw new IllegalArgumentException("Test screenshots metadata was null");
        }

        serializer.startTag(null, SCREENSHOTS_TAG);

        for (TestScreenshotsMetadata.ScreenshotMetadata screenshotMetadata :
                screenshotsMetadata.getScreenshotMetadataSet()) {
            serializer.startTag(null, SCREENSHOT_TAG);
            serializer.attribute(null,
                    NAME_ATTR, String.valueOf(screenshotMetadata.getScreenshotName()));
            serializer.attribute(
                    null, DESCRIPTION_ATTR, String.valueOf(screenshotMetadata.getDescription()));
            serializer.endTag(null, SCREENSHOT_TAG);
        }
        serializer.endTag(null, SCREENSHOTS_TAG);
    }

    /** Single screenshot information */
    public static class ScreenshotMetadata implements Serializable {
        String mScreenshotName;
        String mDescription;

        public void setDescription(String description) {
            mDescription = description;
        }

        public String getDescription() {
            return mDescription;
        }

        public void setScreenshotName(String screenshotName) {
            mScreenshotName = screenshotName;
        }

        public String getScreenshotName() {
            return mScreenshotName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ScreenshotMetadata)) {
                return false;
            }
            TestScreenshotsMetadata.ScreenshotMetadata that =
                    (TestScreenshotsMetadata.ScreenshotMetadata) o;
            return mScreenshotName.equals(that.mScreenshotName)
                    && mDescription.equals(that.mDescription);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mScreenshotName, mDescription);
        }

        @Override
        public String toString() {
            return "[" + mScreenshotName + ", " + mDescription + "]";
        }
    }
}
