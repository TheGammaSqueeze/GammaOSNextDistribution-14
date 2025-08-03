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

package com.android.safetycenter.persistence;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES;
import static org.xmlpull.v1.XmlPullParser.START_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;
import static org.xmlpull.v1.XmlPullParser.TEXT;

import static java.util.Collections.unmodifiableList;

import android.util.AtomicFile;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.RequiresApi;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Utility class to persist identifiers and metadata of safety source issues related to a user. */
@RequiresApi(TIRAMISU)
public final class SafetyCenterIssuesPersistence {

    private static final String TAG = "SafetyCenterIssuesPersi";

    private static final String TAG_ISSUES = "issues";
    private static final String TAG_ISSUE = "issue";

    private static final String ATTRIBUTE_VERSION = "version";
    private static final String ATTRIBUTE_KEY = "key";
    private static final String ATTRIBUTE_FIRST_SEEN_AT = "first_seen_at_epoch_millis";
    private static final String ATTRIBUTE_DISMISSED_AT = "dismissed_at_epoch_millis";
    private static final String ATTRIBUTE_DISMISS_COUNT = "dismiss_count";
    private static final String ATTRIBUTE_NOTIFICATION_DISMISSED_AT =
            "notification_dismissed_at_epoch_millis";

    private static final int NO_VERSION = -1;
    private static final int CURRENT_VERSION = 2;
    private static final int MIN_COMPATIBLE_VERSION = 0;

    private SafetyCenterIssuesPersistence() {}

    /**
     * Read the issues state from persistence.
     *
     * <p>This will perform I/O operations synchronously.
     *
     * @param file the file to read from
     * @return the list of issue states read or an empty list if the file does not exist
     * @throws PersistenceException if there is an unexpected error while reading the file
     */
    public static List<PersistedSafetyCenterIssue> read(File file) throws PersistenceException {
        XmlPullParser parser = Xml.newPullParser();
        try (FileInputStream inputStream = new AtomicFile(file).openRead()) {
            parser.setFeature(FEATURE_PROCESS_NAMESPACES, true);
            parser.setInput(inputStream, null);
            return unmodifiableList(parseXml(parser));
        } catch (FileNotFoundException e) {
            Log.i(TAG, "File not found: " + file);
            return unmodifiableList(new ArrayList<>());
        } catch (IOException | XmlPullParserException e) {
            throw new PersistenceException("Failed to read file: " + file, e);
        }
    }

    private static List<PersistedSafetyCenterIssue> parseXml(XmlPullParser parser)
            throws IOException, PersistenceException, XmlPullParserException {
        if (parser.getEventType() != START_DOCUMENT) {
            throw new PersistenceException("Unexpected parser state");
        }
        parser.nextTag();
        validateElementStart(parser, TAG_ISSUES);
        List<PersistedSafetyCenterIssue> persistedSafetyCenterIssues = parseIssues(parser);
        while (parser.getEventType() == TEXT && parser.isWhitespace()) {
            parser.next();
        }
        if (parser.getEventType() != END_DOCUMENT) {
            throw new PersistenceException("Unexpected extra root element");
        }
        return persistedSafetyCenterIssues;
    }

    private static List<PersistedSafetyCenterIssue> parseIssues(XmlPullParser parser)
            throws IOException, PersistenceException, XmlPullParserException {
        int version = NO_VERSION;
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            switch (parser.getAttributeName(i)) {
                case ATTRIBUTE_VERSION:
                    version = parseInteger(parser.getAttributeValue(i), parser.getAttributeName(i));
                    break;
                default:
                    throw attributeUnexpected(parser.getAttributeName(i));
            }
        }
        if (version == NO_VERSION) {
            throw new PersistenceException("Missing version");
        }
        if (version > CURRENT_VERSION || version < MIN_COMPATIBLE_VERSION) {
            throw new PersistenceException("Unsupported version: " + version);
        }

        List<PersistedSafetyCenterIssue> persistedSafetyCenterIssues = new ArrayList<>();
        parser.nextTag();
        while (parser.getEventType() == START_TAG && parser.getName().equals(TAG_ISSUE)) {
            persistedSafetyCenterIssues.add(parseIssue(parser));
        }
        validateElementEnd(parser, TAG_ISSUES);
        parser.next();
        return persistedSafetyCenterIssues;
    }

    private static PersistedSafetyCenterIssue parseIssue(XmlPullParser parser)
            throws IOException, PersistenceException, XmlPullParserException {
        boolean hasDismissedAt = false;
        boolean hasDismissCount = false;
        PersistedSafetyCenterIssue.Builder builder = new PersistedSafetyCenterIssue.Builder();
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            switch (parser.getAttributeName(i)) {
                case ATTRIBUTE_KEY:
                    builder.setKey(parser.getAttributeValue(i));
                    break;
                case ATTRIBUTE_FIRST_SEEN_AT:
                    builder.setFirstSeenAt(
                            parseInstant(parser.getAttributeValue(i), parser.getAttributeName(i)));
                    break;
                case ATTRIBUTE_DISMISSED_AT:
                    hasDismissedAt = true;
                    builder.setDismissedAt(
                            parseInstant(parser.getAttributeValue(i), parser.getAttributeName(i)));
                    break;
                case ATTRIBUTE_DISMISS_COUNT:
                    hasDismissCount = true;
                    try {
                        builder.setDismissCount(
                                parseInteger(
                                        parser.getAttributeValue(i), parser.getAttributeName(i)));
                    } catch (IllegalArgumentException e) {
                        throw attributeInvalid(
                                parser.getAttributeValue(i), parser.getAttributeName(i), e);
                    }
                    break;
                case ATTRIBUTE_NOTIFICATION_DISMISSED_AT:
                    builder.setNotificationDismissedAt(
                            parseInstant(parser.getAttributeValue(i), parser.getAttributeName(i)));
                    break;
                default:
                    throw attributeUnexpected(parser.getAttributeName(i));
            }
        }
        if (hasDismissedAt && !hasDismissCount) {
            builder.setDismissCount(1);
        }
        parser.nextTag();
        validateElementEnd(parser, TAG_ISSUE);
        parser.nextTag();
        try {
            return builder.build();
        } catch (IllegalStateException e) {
            throw new PersistenceException("Element issue invalid", e);
        }
    }

    private static void validateElementStart(XmlPullParser parser, String name)
            throws PersistenceException, XmlPullParserException {
        if (parser.getEventType() != START_TAG || !parser.getName().equals(name)) {
            throw new PersistenceException(String.format("Element %s missing", name));
        }
    }

    private static void validateElementEnd(XmlPullParser parser, String name)
            throws PersistenceException, XmlPullParserException {
        if (parser.getEventType() != END_TAG || !parser.getName().equals(name)) {
            throw new PersistenceException(String.format("Element %s not closed", name));
        }
    }

    private static int parseInteger(String value, String name) throws PersistenceException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw attributeInvalid(value, name, e);
        }
    }

    private static Instant parseInstant(String value, String name) throws PersistenceException {
        try {
            return Instant.ofEpochMilli(Long.parseLong(value));
        } catch (DateTimeException | NumberFormatException e) {
            throw attributeInvalid(value, name, e);
        }
    }

    private static PersistenceException attributeUnexpected(String name) {
        return new PersistenceException("Unexpected attribute " + name);
    }

    private static PersistenceException attributeInvalid(String value, String name, Throwable ex) {
        return new PersistenceException(
                "Attribute value \"" + value + "\" for " + name + " invalid", ex);
    }

    /**
     * Write the issues state to persistence.
     *
     * <p>This will perform I/O operations synchronously.
     *
     * @param persistedSafetyCenterIssues the issue states to write
     * @param file the file to write to
     */
    public static void write(
            List<PersistedSafetyCenterIssue> persistedSafetyCenterIssues, File file) {
        AtomicFile atomicFile = new AtomicFile(file);
        FileOutputStream outputStream = null;
        try {
            outputStream = atomicFile.startWrite();

            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(outputStream, StandardCharsets.UTF_8.name());
            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            serializer.startDocument(null, true);

            serializeIssues(serializer, persistedSafetyCenterIssues);

            serializer.endDocument();
            atomicFile.finishWrite(outputStream);
        } catch (Exception e) {
            Log.wtf(TAG, "Failed to write, restoring backup: " + file, e);
            atomicFile.failWrite(outputStream);
        } finally {
            try {
                outputStream.close();
            } catch (Exception ignored) {
                // Ignored.
            }
        }
    }

    private static void serializeIssues(
            XmlSerializer serializer, List<PersistedSafetyCenterIssue> persistedSafetyCenterIssues)
            throws IOException {
        serializer.startTag(null, TAG_ISSUES);
        serializer.attribute(null, ATTRIBUTE_VERSION, Integer.toString(CURRENT_VERSION));

        for (int i = 0; i < persistedSafetyCenterIssues.size(); i++) {
            PersistedSafetyCenterIssue persistedSafetyCenterIssue =
                    persistedSafetyCenterIssues.get(i);

            serializer.startTag(null, TAG_ISSUE);
            serializer.attribute(null, ATTRIBUTE_KEY, persistedSafetyCenterIssue.getKey());
            serializer.attribute(
                    null,
                    ATTRIBUTE_FIRST_SEEN_AT,
                    Long.toString(persistedSafetyCenterIssue.getFirstSeenAt().toEpochMilli()));
            Instant dismissedAt = persistedSafetyCenterIssue.getDismissedAt();
            if (dismissedAt != null) {
                serializer.attribute(
                        null, ATTRIBUTE_DISMISSED_AT, Long.toString(dismissedAt.toEpochMilli()));
            }
            int dismissCount = persistedSafetyCenterIssue.getDismissCount();
            if (dismissCount > 0) {
                serializer.attribute(null, ATTRIBUTE_DISMISS_COUNT, Integer.toString(dismissCount));
            }
            Instant notificationDismissedAt =
                    persistedSafetyCenterIssue.getNotificationDismissedAt();
            if (notificationDismissedAt != null) {
                serializer.attribute(
                        null,
                        ATTRIBUTE_NOTIFICATION_DISMISSED_AT,
                        Long.toString(notificationDismissedAt.toEpochMilli()));
            }
            serializer.endTag(null, TAG_ISSUE);
        }

        serializer.endTag(null, TAG_ISSUES);
    }
}
