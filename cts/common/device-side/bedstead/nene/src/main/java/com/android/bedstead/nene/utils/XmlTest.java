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

package com.android.bedstead.nene.utils;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcelable;
import android.util.Xml;

import com.android.bedstead.nene.exceptions.NeneException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Utilities for testing XML objects. */
public final class XmlTest {

    private static final String ATTRIBUTE_VALUE = "value";

    private XmlTest() {

    }

    /**
     * Checks that the given XML serializes and deserializes correctly and returns the attribute
     * values from the deserialized instance to be used for assertion.
     *
     * <p> Below is an example xml that can be serialized and deserialized using this method,
     * where {@code tag} = factory_reset_protection_policy,
     * {@code keyTag} = factory_reset_protection_account,
     * {@code attributeValues} = test@account.com
     *
     *  <pre>
     *      {@code
     *      <xml>
     *          <factory_reset_protection_policy>
     *              <factory_reset_protection_account> test@account.com </factory_reset_protection_account>
     *          </factory_reset_protection_policy>
     *      </xml>
     *      }
     *  </pre>
     */
    public static List<String> serializeAndDeserialize(
            String tag, String keyTag, List<String> attributeValues) {
        try (ByteArrayOutputStream outputStream = serialize(tag, keyTag, attributeValues);
             InputStreamReader inputStream = new InputStreamReader(
                     new ByteArrayInputStream(outputStream.toByteArray()))) {
            XmlPullParser xmlParser = Xml.newPullParser();
            xmlParser.setInput(inputStream);

            assertThat(xmlParser.next()).isEqualTo(XmlPullParser.START_TAG);

            int type;
            List<String> parsedValues = new ArrayList<>(attributeValues.size());
            while ((type = xmlParser.next()) != XmlPullParser.END_DOCUMENT &&
                    type != XmlPullParser.END_TAG) {
                parsedValues.add(
                        xmlParser.getAttributeValue(null, ATTRIBUTE_VALUE));
            }
            return parsedValues;
        } catch (XmlPullParserException | IOException | NoSuchMethodException |
                 IllegalAccessException | InvocationTargetException e) {
            throw new NeneException("XmlTest: Unable to serialize and deserialize xml", e);
        }
    }

    private static <T extends Parcelable> ByteArrayOutputStream serialize(
            String tag, String keyTag, List<String> attributeValues)
            throws IOException, NoSuchMethodException, InvocationTargetException,
            IllegalAccessException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        XmlSerializer outXml = Xml.newSerializer();
        try {
            outXml.setOutput(outStream, StandardCharsets.UTF_8.name());
            outXml.startDocument(null, true);
            outXml.startTag(null, tag);
            outXml.startTag(null, keyTag);
            for (String value : attributeValues) {
                outXml.attribute(null, ATTRIBUTE_VALUE, value);
            }
            outXml.endTag(null, keyTag);
            outXml.endTag(null, tag);
            outXml.endDocument();
        } finally {
            outXml.flush();
        }
        return outStream;
    }
}
