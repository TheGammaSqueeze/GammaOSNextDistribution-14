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

package com.android.car.oem.utils;

import static com.android.car.oem.focus.FocusInteraction.INTERACTION_CONCURRENT;
import static com.android.car.oem.focus.FocusInteraction.INTERACTION_EXCLUSIVE;
import static com.android.car.oem.focus.FocusInteraction.INTERACTION_REJECT;
import static com.android.car.oem.utils.AudioUtils.getAudioAttributeFromUsage;

import android.annotation.Nullable;
import android.media.AudioAttributes;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;
import android.util.Xml;

import com.android.internal.util.Preconditions;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class OemCarServiceHelper {
    private static final String TAG = OemCarServiceHelper.class.getSimpleName();

    // Per documentation if namespace is disabled, it must be null
    private static final String NO_NAMESPACE = null;
    private static final String NO_TAG = null;
    private static final String TAG_ROOT_VOLUME = "oemAudioVolumeConfiguration";
    private static final String TAG_ROOT_DUCKING = "oemAudioDuckingConfigurations";
    private static final String TAG_ROOT_FOCUS = "oemAudioFocusConfigurations";
    private static final String TAG_ATTRIBUTE = "attribute";
    private static final String TAG_DUCK = "duck";
    private static final String TAG_EXCLUSIVE = "exclusive";
    private static final String TAG_REJECT = "reject";
    private static final String TAG_CONCURRENT = "concurrent";
    private static final String TAG_VOLUME_PRIORITY = "volumePriorities";
    private static final String TAG_DUCK_INTERACTIONS = "duckingInteractions";
    private static final String TAG_DUCK_INTERACTION = "duckingInteraction";
    private static final String TAG_FOCUS_INTERACTIONS = "focusInteractions";
    private static final String TAG_FOCUS_INTERACTION = "focusInteraction";
    private static final String ATTR_USAGE = "usage";
    private static final int DEPRECATED_AUDIO_ATTRIBUTES = 3;
    private static final int SYSTEM_AUDIO_ATTRIBUTES = 4;
    // Size of suppressible usages minus deprecated suppressible usages plus system usages
    private static final int TOTAL_AUDIO_ATTRIBUTES =
            AudioAttributes.SUPPRESSIBLE_USAGES.size() - DEPRECATED_AUDIO_ATTRIBUTES
                    + SYSTEM_AUDIO_ATTRIBUTES;

    private @Nullable List<AudioAttributes> mVolumePriorities;
    // A mapping from AudioAttributes to other AudioAttributes that is either reject, exclusive,
    // or concurrent with values -1, 1, and 2 respectively
    private @Nullable ArrayMap<AudioAttributes, ArrayMap<AudioAttributes, Integer>>
            mCurrentHolderToIncomingFocusInteractions;
    private @Nullable ArrayMap<AudioAttributes, List<AudioAttributes>> mDuckingInteractions;

    /**
     * Parses the given inputStream and puts the priority list/interaction mapping into their
     * respective audio management interaction list/mappings.
     *
     * @param inputStream The inputstream to be parsed.
     * @throws XmlPullParserException Exception to be thrown if formatting is incorrect
     * @throws IOException Exception to be thrown if file does not exist
     */
    public void parseAudioManagementConfiguration(InputStream inputStream)
            throws XmlPullParserException, IOException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, NO_NAMESPACE != null);
        parser.setInput(inputStream, /* inputEncoding= */ null);
        // When setInput() is used with a XmlPullParser, the parser is set to the
        // initial value of START_DOCUMENT.
        parser.next();
        // Per XmlPullParser documentation, null will match with any namespace and any name
        parser.require(XmlPullParser.START_TAG, NO_NAMESPACE, NO_TAG);
        String parserName = parser.getName();
        if (parserName == null || (!parserName.equals(TAG_ROOT_VOLUME)
                && !parserName.equals(TAG_ROOT_DUCKING) && !parserName.equals(TAG_ROOT_FOCUS))) {
            throw new XmlPullParserException("expected " + TAG_ROOT_VOLUME + " or "
                + TAG_ROOT_DUCKING + " or " + TAG_ROOT_FOCUS);
        }

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            String currentTag = parser.getName();
            switch (currentTag) {
                case TAG_VOLUME_PRIORITY:
                    mVolumePriorities = parseVolumePriority(parser);
                    continue;
                case TAG_DUCK_INTERACTIONS:
                    parseDuckingInteractions(parser);
                    continue;
                case TAG_FOCUS_INTERACTIONS:
                    parseFocusInteractions(parser);
                    continue;
                default:
                    Slog.w(TAG, "Could not match given tag");
                    skip(parser);
            }
        }

        if (mDuckingInteractions != null && evaluateLoops(mDuckingInteractions)) {
            throw new IllegalStateException("Ducking interactions contain loops");
        }

        //TODO (b/266977493): Delete these logs when b/266977442 is fixed, strictly for debugging
        if (mVolumePriorities != null) {
            Slog.i(TAG, "Volume priority list is " + mVolumePriorities);
        }
        if (mDuckingInteractions != null) {
            Slog.i(TAG, "Ducking interactions is: " + mDuckingInteractions);
        }
        if (mCurrentHolderToIncomingFocusInteractions != null) {
            Slog.i(TAG, "focus interactions is: " + mCurrentHolderToIncomingFocusInteractions);
            for (int i = 0; i < mCurrentHolderToIncomingFocusInteractions.size(); i++) {
                Slog.i(TAG, "Current focus holder is: "
                        + mCurrentHolderToIncomingFocusInteractions.keyAt(i).usageToString());
                ArrayMap<AudioAttributes, Integer> focusInteraction =
                        mCurrentHolderToIncomingFocusInteractions.valueAt(i);
                for (int j = 0; j < focusInteraction.size(); j++) {
                    AudioAttributes attr = focusInteraction.keyAt(j);
                    Slog.i(TAG, "\t focus interaction for: "
                            + attr.usageToString() + " is: " + focusInteraction.get(attr));
                }
            }
        }
    }

    /**
     * Gets the volume priority list.
     *
     * @return The volume priority list from highest priority to lowest priority if it exists. If
     * it was not set in the xml, returns null.
     */
    public @Nullable List<AudioAttributes> getVolumePriorityList() {
        return mVolumePriorities;
    }

    /**
     * Gets the current focus holder to incoming focus holder mapping.
     *
     * @return The focus interaction mapping if it exists. If it was not set in the xml, returns
     * null.
     */
    public @Nullable ArrayMap<AudioAttributes, ArrayMap<AudioAttributes, Integer>>
            getCurrentFocusToIncomingFocusInteractions() {
        return mCurrentHolderToIncomingFocusInteractions;
    }

    /**
     * Gets the ducking interaction mapping.
     *
     * @return The ducking interaction mapping if it exists. If it was not set in the xml, returns
     * null.
     */
    public @Nullable ArrayMap<AudioAttributes, List<AudioAttributes>> getDuckingInteractions() {
        return mDuckingInteractions;
    }

    private void parseFocusInteractions(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        mCurrentHolderToIncomingFocusInteractions = new ArrayMap<>();
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            if (TAG_FOCUS_INTERACTION.equals(parser.getName())) {
                parseFocusInteraction(parser, mCurrentHolderToIncomingFocusInteractions);
            } else {
                skip(parser);
            }
        }
    }

    private void parseFocusInteraction(XmlPullParser parser, ArrayMap<AudioAttributes,
            ArrayMap<AudioAttributes, Integer>> currentHolderToIncomingFocusInteractions)
            throws XmlPullParserException, IOException {
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            if (TAG_ATTRIBUTE.equals(parser.getName())) {
                AudioAttributes currentAttribute = getAudioAttributeFromString(
                        parser.getAttributeValue(NO_NAMESPACE, ATTR_USAGE));
                // skip the focus holder since it's already been read
                parser.next();
                parseExclusiveOrRejectFocusAttributes(parser,
                        currentHolderToIncomingFocusInteractions, currentAttribute);
                // There is no end tag for reading attribute
                return;
            } else {
                skip(parser);
            }
        }
    }

    private void parseExclusiveOrRejectFocusAttributes(XmlPullParser parser,
            ArrayMap<AudioAttributes, ArrayMap<AudioAttributes, Integer>>
                    currentHolderToIncomingFocusInteractions, AudioAttributes focusHolder)
            throws XmlPullParserException, IOException {
        List<AudioAttributes> exclusive = new ArrayList<>();
        List<AudioAttributes> rejected = new ArrayList<>();
        List<AudioAttributes> concurrent = new ArrayList<>();
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            String priorityType = parser.getName();
            switch(priorityType) {
                case TAG_EXCLUSIVE:
                    exclusive.addAll(parseAttributes(parser));
                    continue;
                case TAG_REJECT:
                    rejected.addAll(parseAttributes(parser));
                    continue;
                case TAG_CONCURRENT:
                    concurrent.addAll(parseAttributes(parser));
                    continue;
                default:
                    skip(parser);
            }
        }
        // verify that exclusive, rejected and concurrent are disjoint
        Preconditions.checkArgument(Collections.disjoint(exclusive, rejected));
        Preconditions.checkArgument(Collections.disjoint(exclusive, concurrent));
        Preconditions.checkArgument(Collections.disjoint(rejected, concurrent));
        ArrayMap<AudioAttributes, Integer> incomingFocusInteractions = new ArrayMap<>();
        listToMappingWithValue(exclusive, INTERACTION_EXCLUSIVE, incomingFocusInteractions);
        listToMappingWithValue(rejected, INTERACTION_REJECT, incomingFocusInteractions);
        listToMappingWithValue(concurrent, INTERACTION_CONCURRENT, incomingFocusInteractions);
        Preconditions.checkArgument(incomingFocusInteractions.size() == TOTAL_AUDIO_ATTRIBUTES);
        currentHolderToIncomingFocusInteractions.put(focusHolder, incomingFocusInteractions);
    }

    private void parseDuckingInteractions(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        mDuckingInteractions = new ArrayMap<>();
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            if (TAG_DUCK_INTERACTION.equals(parser.getName())) {
                parseDuckingInteraction(parser, mDuckingInteractions);
            } else {
                skip(parser);
            }
        }
    }

    private void parseDuckingInteraction(XmlPullParser parser, ArrayMap<AudioAttributes,
            List<AudioAttributes>> duckingInteractions) throws XmlPullParserException, IOException {
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            if (TAG_ATTRIBUTE.equals(parser.getName())) {
                String duckingHolder = parser.getAttributeValue(NO_NAMESPACE, ATTR_USAGE);
                // skip the ducking holder since it's already been read
                parser.next();
                Objects.requireNonNull(duckingHolder, "requires attribute to be present");
                parseToDuckAttributes(parser, duckingInteractions, duckingHolder);
                // There is no end tag for reading attribute
                return;
            } else {
                skip(parser);
            }
        }
    }

    private List<AudioAttributes> parseVolumePriority(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        return parseAttributes(parser);
    }

    private void parseToDuckAttributes(XmlPullParser parser, ArrayMap<AudioAttributes,
            List<AudioAttributes>> duckingInteractions, String duckingHolder)
            throws XmlPullParserException, IOException {
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            if (TAG_DUCK.equals(parser.getName())) {
                List<AudioAttributes> duckedAttributes = parseAttributes(parser);
                duckingInteractions.put(getAudioAttributeFromString(duckingHolder),
                        duckedAttributes);
            }
        }
    }

    private List<AudioAttributes> parseAttributes(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        List<AudioAttributes> priorityList = new ArrayList<>();
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            if (TAG_ATTRIBUTE.equals(parser.getName())) {
                String audioAttributeString = parser.getAttributeValue(NO_NAMESPACE, ATTR_USAGE);
                Objects.requireNonNull(audioAttributeString, "requires attribute to be present");
                priorityList.add(getAudioAttributeFromString(audioAttributeString));
            }
            skip(parser);
        }
        return priorityList;
    }

    private void listToMappingWithValue(List<AudioAttributes> list, int value,
            ArrayMap<AudioAttributes, Integer> mapping) {
        for (int i = 0; i < list.size(); i++) {
            mapping.put(list.get(i), value);
        }
    }

    private AudioAttributes getAudioAttributeFromString(String stringAudioAttribute) {
        if (Objects.equals(stringAudioAttribute, "AUDIO_USAGE_NOTIFICATION_EVENT")) {
            return getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT);
        }
        return getAudioAttributeFromUsage(AudioAttributes.xsdStringToUsage(stringAudioAttribute));
    }

    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
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
            }
        }
    }

    private boolean evaluateLoops(ArrayMap<AudioAttributes, List<AudioAttributes>> mapping) {
        ArraySet<AudioAttributes> seenAttributes = new ArraySet<>();
        for (int i = 0; i < mapping.size(); i++) {
            if (containLoops(mapping.keyAt(i), new ArraySet<>(), seenAttributes, mapping)) {
                return true;
            }
        }
        return false;
    }

    private boolean containLoops(AudioAttributes currentAttribute,
            ArraySet<AudioAttributes> currentlyVisited, ArraySet<AudioAttributes> seen,
            ArrayMap<AudioAttributes, List<AudioAttributes>> mapping) {
        // contains loop
        if (currentlyVisited.contains(currentAttribute)) {
            return true;
        }
        if (seen.contains(currentAttribute)) {
            return false;
        }
        List<AudioAttributes> nextAttributes = mapping.getOrDefault(currentAttribute, List.of());
        currentlyVisited.add(currentAttribute);
        seen.add(currentAttribute);
        for (int i = 0; i < nextAttributes.size(); i++) {
            if (containLoops(nextAttributes.get(i), currentlyVisited, seen, mapping)) {
                // contains loops
                return true;
            }
        }
        // make sure to we remove what we're currently visiting to avoid issues such as A->B->C
        // and A->C. If B and C does not get removed, then loop will be found.
        currentlyVisited.remove(currentAttribute);
        return false;
    }
}
