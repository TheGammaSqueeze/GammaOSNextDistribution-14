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

package android.adservices.parser;

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES;
import static org.xmlpull.v1.XmlPullParser.START_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

import static java.util.Objects.requireNonNull;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Class used to parse android manifest file for BackCompatAndroidProcessDetector */
public class AndroidManifestParser {

    private static final String RECEIVER_TAG = "receiver";
    private static final String ACTIVITY_TAG = "activity";
    private static final String SERVICE_TAG = "service";
    private static final String PROVIDER_TAG = "provider";
    private static final String ANDROID_PROCESS_ATTRIBUTE = "process";
    private static final String ADSERVICES_PROCESS_VALUE = ".adservices";

    /** Method to check how many components in the manifest file are missing adservices process */
    public static List<Integer> findComponentsMissingAdservicesProcess(InputStream in)
            throws XmlPullParserException, IOException {
        requireNonNull(in);

        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setFeature(FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(in, null);

        if (parser.getEventType() != START_DOCUMENT) {
            throw new IllegalStateException("Unexpected parser state");
        }

        List<Integer> missingLineNums = new ArrayList<>();
        int eventType = parser.next();
        while (eventType != END_DOCUMENT) {
            if (eventType == START_TAG) {
                switch (parser.getName()) {
                    case RECEIVER_TAG:
                    case ACTIVITY_TAG:
                    case SERVICE_TAG:
                    case PROVIDER_TAG:
                        int lineNumber = parser.getLineNumber();
                        if (!componentHasAdservicesProcess(parser)) {
                            missingLineNums.add(lineNumber);
                        }
                        break;
                    default:
                        // not interested in this tag, do nothing
                        break;
                }
            }

            eventType = parser.next();
        }

        // checked all components and didn't find any missing the adservices process
        return missingLineNums;
    }

    private static boolean componentHasAdservicesProcess(XmlPullParser parser) {
        final int numAttributes = parser.getAttributeCount();

        for (int attrIdx = 0; attrIdx < numAttributes; attrIdx++) {
            final String attrName = parser.getAttributeName(attrIdx);
            if (!ANDROID_PROCESS_ATTRIBUTE.equals(attrName)) {
                continue;
            }

            // found the right attribute, make sure the value is correct
            final String attrValue = parser.getAttributeValue(attrIdx);
            if (ADSERVICES_PROCESS_VALUE.equals(attrValue)) {
                return true;
            }

            // there should only be one process attribute, no need to continue
            break;
        }

        // searched all attributes and did not find the correct one
        return false;
    }
}
