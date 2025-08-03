/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.car.audio;

import static android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT;

import android.car.builtin.media.AudioManagerHelper;
import android.media.AudioAttributes;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utils for common parser functionalities used across multiple config files
 */
/* package */ final class CarAudioParserUtils {

    private static final String NAMESPACE = null;
    private static final int INVALID = -1;

    public static final String TAG_AUDIO_ATTRIBUTES = "audioAttributes";
    public static final String TAG_AUDIO_ATTRIBUTE = "audioAttribute";
    public static final String TAG_USAGE = "usage";
    public static final String ATTR_USAGE_VALUE = "value";
    public static final String ATTR_NAME = "name";
    public static final String ATTR_CONTENT_TYPE = "contentType";
    public static final String ATTR_USAGE = "usage";
    public static final String ATTR_TAGS = "tags";


    /* package */ static List<AudioAttributes> parseAudioAttributes(XmlPullParser parser,
            String sectionName) throws XmlPullParserException, IOException {
        List<AudioAttributes> attrs = new ArrayList<>();
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }

            if (Objects.equals(parser.getName(), TAG_USAGE)) {
                AudioAttributes.Builder attributesBuilder = new AudioAttributes.Builder();
                parseUsage(parser, attributesBuilder, ATTR_USAGE_VALUE);
                AudioAttributes attributes = attributesBuilder.build();
                attrs.add(attributes);
            } else if (Objects.equals(parser.getName(), TAG_AUDIO_ATTRIBUTE)) {
                attrs.add(parseAudioAttribute(parser, sectionName));
            }
            // Always skip to upper level since we're at the lowest.
            skip(parser);
        }
        if (attrs.isEmpty()) {
            throw new IllegalArgumentException("No attributes for config: " + sectionName);
        }
        return attrs;
    }

    static AudioAttributes parseAudioAttribute(XmlPullParser parser,
            String sectionName) throws XmlPullParserException, IOException {
        AudioAttributes.Builder attributesBuilder = new AudioAttributes.Builder();
        // Usage, ContentType and tags are optional but at least one value must be
        // provided to build a valid audio attributes
        boolean hasValidUsage = parseUsage(parser, attributesBuilder, ATTR_USAGE);
        boolean hasValidContentType = parseContentType(parser, attributesBuilder);
        boolean hasValidTags = parseTags(parser, attributesBuilder);
        if (!(hasValidUsage || hasValidContentType || hasValidTags)) {
            throw new RuntimeException("Empty attributes for context: " + sectionName);
        }
        return attributesBuilder.build();
    }

    private static boolean parseUsage(XmlPullParser parser, AudioAttributes.Builder builder,
            String attrValue) throws XmlPullParserException, IOException {
        int usage = parseUsageValue(parser, attrValue);
        if (usage == INVALID) {
            return false;
        }

        if (AudioAttributes.isSystemUsage(usage)) {
            builder.setSystemUsage(usage);
        } else {
            builder.setUsage(usage);
        }
        return true;
    }

    static int parseUsageValue(XmlPullParser parser, String attrValue)
            throws XmlPullParserException, IOException  {
        String usageLiteral = parser.getAttributeValue(NAMESPACE, attrValue);
        if (usageLiteral == null) {
            return INVALID;
        }

        int usage = AudioManagerHelper.xsdStringToUsage(usageLiteral);
        // TODO (b/248106031): Remove once AUDIO_USAGE_NOTIFICATION_EVENT is fixed in core
        if (Objects.equals(usageLiteral, "AUDIO_USAGE_NOTIFICATION_EVENT")) {
            usage = USAGE_NOTIFICATION_EVENT;
        }
        return usage;
    }

    private static boolean parseContentType(XmlPullParser parser, AudioAttributes.Builder builder)
            throws XmlPullParserException, IOException {
        int contentType = parseContentTypeValue(parser);
        if (contentType == INVALID) {
            return false;
        }

        builder.setContentType(contentType);
        return true;
    }

    static int parseContentTypeValue(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        String contentTypeLiteral = parser.getAttributeValue(NAMESPACE, ATTR_CONTENT_TYPE);
        if (contentTypeLiteral == null) {
            return INVALID;
        }
        return AudioManagerHelper.xsdStringToContentType(contentTypeLiteral);
    }

    private static boolean parseTags(XmlPullParser parser, AudioAttributes.Builder builder)
            throws XmlPullParserException, IOException {
        String tagsLiteral = parser.getAttributeValue(NAMESPACE, ATTR_TAGS);
        if (tagsLiteral == null) {
            return false;
        }
        AudioManagerHelper.addTagToAudioAttributes(builder, tagsLiteral);
        return true;
    }

    /* package */ static int parsePositiveIntAttribute(String attribute, String integerString) {
        try {
            return Integer.parseUnsignedInt(integerString);
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            throw new IllegalArgumentException(attribute + " must be a positive integer, but was \""
                    + integerString + "\" instead.", e);
        }
    }

    static long parsePositiveLongAttribute(String attribute, String longString) {
        try {
            return Long.parseUnsignedLong(longString);
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            throw new IllegalArgumentException(attribute + " must be a positive long, but was \""
                    + longString + "\" instead.", e);
        }
    }

    /* package */ static void skip(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
                default:
                    break;
            }
        }
    }
}
