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

package com.android.tv.samples.sampletunertvinput;

import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Parser for ATSC PSIP sections */
public class SampleTunerTvInputSectionParser {
    private static final String TAG = "SampleTunerTvInput";
    private static final boolean DEBUG = true;

    public static final byte DESCRIPTOR_TAG_EXTENDED_CHANNEL_NAME = (byte) 0xa0;
    public static final byte COMPRESSION_TYPE_NO_COMPRESSION = (byte) 0x00;
    public static final byte MODE_UTF16 = (byte) 0x3f;

    /**
     * Parses a single TVCT section, as defined in A/65 6.4
     * @param data, a ByteBuffer containing a single TVCT section which describes only one channel
     * @return null if there is an error while parsing, the channel with parsed data otherwise
     */
    public static TvctChannelInfo parseTvctSection(byte[] data) {
        if (!checkValidPsipSection(data)) {
            return null;
        }
        int numChannels = data[9] & 0xff;
        if(numChannels != 1) {
            Log.e(TAG, "parseTVCTSection expected 1 channel, found " + numChannels);
            return null;
        }
        // TVCT Sections are a minimum of 16 bytes, with a minimum of 32 bytes per channel
        if(data.length < 48) {
            Log.e(TAG, "parseTVCTSection found section under minimum length");
            return null;
        }

        // shortName begins at data[10] and ends at either the first stuffing
        // UTF-16 character of value 0x0000, or at a length of 14 Bytes
        int shortNameLength = 14;
        for(int i = 0; i < 14; i += 2) {
            int charValue = ((data[10 + i] & 0xff) << 8) | (data[10 + (i + 1)] & 0xff);
            if (charValue == 0x0000) {
                shortNameLength = i;
                break;
            }
        }
        // Data field positions are as defined by A/65 Section 6.4 for one channel
        String name = new String(Arrays.copyOfRange(data, 10, 10 + shortNameLength),
                        StandardCharsets.UTF_16);
        int majorNumber = ((data[24] & 0x0f) << 6) | ((data[25] & 0xff) >> 2);
        int minorNumber = ((data[25] & 0x03) << 8) | (data[26] & 0xff);
        if (DEBUG) {
            Log.d(TAG, "parseTVCTSection found shortName: " + name
                    + " channel number: " + majorNumber + "-" + minorNumber);
        }
        int descriptorsLength = ((data[40] & 0x03) << 8) | (data[41] & 0xff);
        List<TsDescriptor> descriptors = parseDescriptors(data, 42, 42 + descriptorsLength);
        for (TsDescriptor descriptor : descriptors) {
            if (descriptor instanceof ExtendedChannelNameDescriptor) {
                ExtendedChannelNameDescriptor longNameDescriptor =
                        (ExtendedChannelNameDescriptor)descriptor;
                name = longNameDescriptor.getLongChannelName();
                if (DEBUG) {
                    Log.d(TAG, "parseTVCTSection found longName: " + name);
                }
            }
        }

        return new TvctChannelInfo(name, majorNumber, minorNumber);
    }

    /**
     * Parses a single EIT section, as defined in ATSC A/65 Section 6.5
     * @param data, a byte array containing a single EIT section which describes only one event
     * @return {@code null} if there is an error while parsing, the event with parsed data otherwise
     */
    public static EitEventInfo parseEitSection(byte[] data) {
        if (!checkValidPsipSection(data)) {
            return null;
        }
        int numEvents = data[9] & 0xff;
        if(numEvents != 1) {
            Log.e(TAG, "parseEitSection expected 1 event, found " + numEvents);
            return null;
        }
        // EIT Sections are a minimum of 14 bytes, with a minimum of 12 bytes per event
        if(data.length < 26) {
            Log.e(TAG, "parseEitSection found section under minimum length");
            return null;
        }

        // Data field positions are as defined by A/65 Section 6.5 for one event
        int lengthInSeconds = ((data[16] & 0x0f) << 16) | ((data[17] & 0xff) << 8)
                | (data[18] & 0xff);
        int titleLength = data[19] & 0xff;
        String titleText = parseMultipleStringStructure(data, 20, 20 + titleLength);

        if (DEBUG) {
            Log.d(TAG, "parseEitSection found titleText: " + titleText
                    + " lengthInSeconds: " + lengthInSeconds);
        }
        return new EitEventInfo(titleText, lengthInSeconds);
    }


    // Descriptor data structure defined in ISO/IEC 13818-1 Section 2.6
    // Returns an empty list on parsing failures
    private static List<TsDescriptor> parseDescriptors(byte[] data, int offset, int limit) {
        List<TsDescriptor> descriptors = new ArrayList<>();
        if (data.length < limit) {
            Log.e(TAG, "parseDescriptors given limit larger than data");
            return descriptors;
        }
        int pos = offset;
        while (pos + 1 < limit) {
            int tag = data[pos] & 0xff;
            int length = data[pos + 1] & 0xff;
            if (length <= 0) {
                continue;
            }
            pos += 2;

            if (limit < pos + length) {
                Log.e(TAG, "parseDescriptors found descriptor with length longer than limit");
                break;
            }
            if (DEBUG) {
                Log.d(TAG, "parseDescriptors found descriptor with tag: " + tag);
            }
            TsDescriptor descriptor = null;
            switch ((byte) tag) {
                case DESCRIPTOR_TAG_EXTENDED_CHANNEL_NAME:
                    descriptor = parseExtendedChannelNameDescriptor(data, pos, pos + length);
                    break;
                default:
                    break;
            }
            if (descriptor != null) {
                descriptors.add(descriptor);
            }
            pos += length;
        }
        return descriptors;
    }

    // ExtendedChannelNameDescriptor is defined in ATSC A/65 Section 6.9.4 as containing only
    // a single MultipleStringStructure after its tag and length.
    // @return {@code null} if parsing MultipleStringStructure fails
    private static ExtendedChannelNameDescriptor parseExtendedChannelNameDescriptor(byte[] data,
            int offset, int limit) {
        String channelName = parseMultipleStringStructure(data, offset, limit);
        return channelName == null ? null : new ExtendedChannelNameDescriptor(channelName);
    }

    // MultipleStringStructure is defined in ATSC A/65 Section 6.10
    // Returns first string segment with supported compression and mode
    // @return {@code null} on invalid data or no supported string segments
    private static String parseMultipleStringStructure(byte[] data, int offset, int limit) {
        if (limit < offset + 8) {
            Log.e(TAG, "parseMultipleStringStructure given too little data");
            return null;
        }

        int numStrings = data[offset] & 0xff;
        if (numStrings <= 0) {
            Log.e(TAG, "parseMultipleStringStructure found no strings");
            return null;
        }
        int pos = offset + 1;
        for (int i = 0; i < numStrings; i++) {
            if (limit < pos + 4) {
                Log.e(TAG, "parseMultipleStringStructure ran out of data");
                return null;
            }
            int numSegments = data[pos + 3] & 0xff;
            pos += 4;
            for (int j = 0; j < numSegments; j++) {
                if (limit < pos + 3) {
                    Log.e(TAG, "parseMultipleStringStructure ran out of data");
                    return null;
                }
                int compressionType = data[pos] & 0xff;
                int mode = data[pos + 1] & 0xff;
                int numBytes = data[pos + 2] & 0xff;
                pos += 3;
                if (data.length < pos + numBytes) {
                    Log.e(TAG, "parseMultipleStringStructure ran out of data");
                    return null;
                }
                if (compressionType == COMPRESSION_TYPE_NO_COMPRESSION && mode == MODE_UTF16) {
                    return new String(data, pos, numBytes, StandardCharsets.UTF_16);
                }
                pos += numBytes;
            }
        }

        Log.e(TAG, "parseMultipleStringStructure found no supported segments");
        return null;
    }

    private static boolean checkValidPsipSection(byte[] data) {
        if (data.length < 13) {
            Log.e(TAG, "Section was too small");
            return false;
        }
        if ((data[0] & 0xff) == 0xff) {
            // Should clear stuffing bytes as detailed by H222.0 section 2.4.4.
            Log.e(TAG, "Unexpected stuffing bytes while parsing section");
            return false;
        }
        int sectionLength = (((data[1] & 0x0f) << 8) | (data[2] & 0xff)) + 3;
        if (sectionLength != data.length) {
            Log.e(TAG, "Length mismatch while parsing section");
            return false;
        }
        int sectionNumber = data[6] & 0xff;
        int lastSectionNumber = data[7] & 0xff;
        if(sectionNumber > lastSectionNumber) {
            Log.e(TAG, "Found sectionNumber > lastSectionNumber while parsing section");
            return false;
        }
        // TODO: Check CRC 32/MPEG for validity
        return true;
    }

    // Contains the portion of the data contained in the TVCT used by
    // our SampleTunerTvInputSetupActivity
    public static class TvctChannelInfo {
        private final String mChannelName;
        private final int mMajorChannelNumber;
        private final int mMinorChannelNumber;

        public TvctChannelInfo(
                String channelName,
                int majorChannelNumber,
                int minorChannelNumber) {
            mChannelName = channelName;
            mMajorChannelNumber = majorChannelNumber;
            mMinorChannelNumber = minorChannelNumber;
        }

        public String getChannelName() {
            return mChannelName;
        }

        public int getMajorChannelNumber() {
            return mMajorChannelNumber;
        }

        public int getMinorChannelNumber() {
            return mMinorChannelNumber;
        }

        @Override
        public String toString() {
            return String.format(
                    Locale.US,
                    "ChannelName: %s ChannelNumber: %d-%d",
                    mChannelName,
                    mMajorChannelNumber,
                    mMinorChannelNumber);
        }
    }

    /**
     * Contains the portion of the data contained in the EIT used by
     * our SampleTunerTvInputService
     */
    public static class EitEventInfo {
        private final String mEventTitle;
        private final int mLengthSeconds;

        public EitEventInfo(
                String eventTitle,
                int lengthSeconds) {
            mEventTitle = eventTitle;
            mLengthSeconds = lengthSeconds;
        }

        public String getEventTitle() {
            return mEventTitle;
        }

        public int getLengthSeconds() {
            return mLengthSeconds;
        }

        @Override
        public String toString() {
            return String.format(
                    Locale.US,
                    "Event Title: %s Length in Seconds: %d",
                    mEventTitle,
                    mLengthSeconds);
        }
    }

    /**
     * A base class for TS descriptors
     * For details of their structure, see ATSC A/65 Section 6.9
     */
    public abstract static class TsDescriptor {
        public abstract int getTag();
    }

    public static class ExtendedChannelNameDescriptor extends TsDescriptor {
        private final String mLongChannelName;

        public ExtendedChannelNameDescriptor(String longChannelName) {
            mLongChannelName = longChannelName;
        }

        @Override
        public int getTag() {
            return DESCRIPTOR_TAG_EXTENDED_CHANNEL_NAME;
        }

        public String getLongChannelName() {
            return mLongChannelName;
        }
    }
}
