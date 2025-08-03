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

package android.media.audio.cts;

import static org.testng.Assert.assertThrows;

import android.media.AudioFormat;
import android.os.Parcel;
import android.platform.test.annotations.AppModeSdkSandbox;

import com.android.compatibility.common.util.CtsAndroidTestCase;
import com.android.compatibility.common.util.NonMainlineTest;

@NonMainlineTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class AudioFormatTest extends CtsAndroidTestCase {

    // -----------------------------------------------------------------
    // AUDIOFORMAT TESTS:
    // ----------------------------------

    // -----------------------------------------------------------------
    // Builder tests
    // ----------------------------------

    // Test case 1: Use Builder to duplicate an AudioFormat with all fields supplied
    public void testBuilderForCopy() throws Exception {
        final int TEST_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_SR = 48000;
        final int TEST_CONF_POS = AudioFormat.CHANNEL_OUT_5POINT1;
        // 6ch, like in 5.1 above offset by a randomly chosen number
        final int TEST_CONF_IDX = 0x3F << 3;

        final AudioFormat formatToCopy = new AudioFormat.Builder()
                .setEncoding(TEST_ENCODING).setSampleRate(TEST_SR)
                .setChannelMask(TEST_CONF_POS).setChannelIndexMask(TEST_CONF_IDX).build();
        assertNotNull("Failure to create the AudioFormat to copy", formatToCopy);

        final AudioFormat copiedFormat = new AudioFormat.Builder(formatToCopy).build();
        assertNotNull("Failure to create AudioFormat copy with Builder", copiedFormat);
        assertEquals("New AudioFormat has wrong sample rate",
                TEST_SR, copiedFormat.getSampleRate());
        assertEquals("New AudioFormat has wrong encoding",
                TEST_ENCODING, copiedFormat.getEncoding());
        assertEquals("New AudioFormat has wrong channel mask",
                TEST_CONF_POS, copiedFormat.getChannelMask());
        assertEquals("New AudioFormat has wrong channel index mask",
                TEST_CONF_IDX, copiedFormat.getChannelIndexMask());
        assertEquals("New AudioFormat has wrong channel count",
                6, copiedFormat.getChannelCount());
        assertEquals("New AudioFormat has the wrong frame size",
                6 /* channels */ * 2 /* bytes per sample */, copiedFormat.getFrameSizeInBytes());
    }

    // Test case 2: Use Builder to duplicate an AudioFormat with only encoding supplied
    public void testPartialFormatBuilderForCopyEncoding() throws Exception {
        final int TEST_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

        final AudioFormat formatToCopy = new AudioFormat.Builder()
                .setEncoding(TEST_ENCODING).build();
        assertNotNull("Failure to create the AudioFormat to copy", formatToCopy);

        final AudioFormat copiedFormat = new AudioFormat.Builder(formatToCopy).build();
        assertNotNull("Failure to create AudioFormat copy with Builder", copiedFormat);
        assertEquals("New AudioFormat has wrong encoding",
                TEST_ENCODING, copiedFormat.getEncoding());
        // test expected values when none has been set
        assertEquals("New AudioFormat doesn't report expected sample rate",
                0, copiedFormat.getSampleRate());
        assertEquals("New AudioFormat doesn't report expected channel mask",
                AudioFormat.CHANNEL_INVALID, copiedFormat.getChannelMask());
        assertEquals("New AudioFormat doesn't report expected channel index mask",
                AudioFormat.CHANNEL_INVALID, copiedFormat.getChannelIndexMask());
    }

    // Test case 3: Use Builder to duplicate an AudioFormat with only sample rate supplied
    public void testPartialFormatBuilderForCopyRate() throws Exception {
        final int TEST_SR = 48000;

        final AudioFormat formatToCopy = new AudioFormat.Builder()
                .setSampleRate(TEST_SR).build();
        assertNotNull("Failure to create the AudioFormat to copy", formatToCopy);

        final AudioFormat copiedFormat = new AudioFormat.Builder(formatToCopy).build();
        assertNotNull("Failure to create AudioFormat copy with Builder", copiedFormat);
        assertEquals("New AudioFormat has wrong sample rate",
                TEST_SR, copiedFormat.getSampleRate());
        // test expected values when none has been set
        assertEquals("New AudioFormat doesn't report expected encoding",
                AudioFormat.ENCODING_INVALID, copiedFormat.getEncoding());
        assertEquals("New AudioFormat doesn't report expected channel mask",
                AudioFormat.CHANNEL_INVALID, copiedFormat.getChannelMask());
        assertEquals("New AudioFormat doesn't report expected channel index mask",
                AudioFormat.CHANNEL_INVALID, copiedFormat.getChannelIndexMask());
    }

    // Test case 4: Use Builder to duplicate an AudioFormat with only channel mask supplied
    public void testPartialFormatBuilderForCopyChanMask() throws Exception {
        final int TEST_CONF_POS = AudioFormat.CHANNEL_OUT_5POINT1;

        final AudioFormat formatToCopy = new AudioFormat.Builder()
                .setChannelMask(TEST_CONF_POS).build();
        assertNotNull("Failure to create the AudioFormat to copy", formatToCopy);

        final AudioFormat copiedFormat = new AudioFormat.Builder(formatToCopy).build();
        assertNotNull("Failure to create AudioFormat copy with Builder", copiedFormat);
        assertEquals("New AudioFormat has wrong channel mask",
                TEST_CONF_POS, copiedFormat.getChannelMask());
        // test expected values when none has been set
        assertEquals("New AudioFormat doesn't report expected encoding",
                AudioFormat.ENCODING_INVALID, copiedFormat.getEncoding());
        assertEquals("New AudioFormat doesn't report expected sample rate",
                0, copiedFormat.getSampleRate());
        assertEquals("New AudioFormat doesn't report expected channel index mask",
                AudioFormat.CHANNEL_INVALID, copiedFormat.getChannelIndexMask());
    }


    // Test case 5: Use Builder to duplicate an AudioFormat with only channel index mask supplied
    public void testPartialFormatBuilderForCopyChanIdxMask() throws Exception {
        final int TEST_CONF_IDX = 0x30;

        final AudioFormat formatToCopy = new AudioFormat.Builder()
                .setChannelIndexMask(TEST_CONF_IDX).build();
        assertNotNull("Failure to create the AudioFormat to copy", formatToCopy);

        final AudioFormat copiedFormat = new AudioFormat.Builder(formatToCopy).build();
        assertNotNull("Failure to create AudioFormat copy with Builder", copiedFormat);
        assertEquals("New AudioFormat has wrong channel mask",
                TEST_CONF_IDX, copiedFormat.getChannelIndexMask());
        // test expected values when none has been set
        assertEquals("New AudioFormat doesn't report expected encoding",
                AudioFormat.ENCODING_INVALID, copiedFormat.getEncoding());
        assertEquals("New AudioFormat doesn't report expected sample rate",
                0, copiedFormat.getSampleRate());
        assertEquals("New AudioFormat doesn't report expected channel mask",
                AudioFormat.CHANNEL_INVALID, copiedFormat.getChannelMask());
    }

    // Test case 6: create an instance, marshall it and create a new instance,
    //      check for equality
    public void testParcel() throws Exception {
        final int TEST_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_SR = 48000;
        final int TEST_CONF_POS = AudioFormat.CHANNEL_OUT_5POINT1;
        // 6ch, like in 5.1 above offset by a randomly chosen number
        final int TEST_CONF_IDX = 0x3F << 3;

        final AudioFormat formatToMarshall = new AudioFormat.Builder()
                .setEncoding(TEST_ENCODING).setSampleRate(TEST_SR)
                .setChannelMask(TEST_CONF_POS).setChannelIndexMask(TEST_CONF_IDX).build();
        assertNotNull("Failure to create the AudioFormat to marshall", formatToMarshall);
        assertEquals(0, formatToMarshall.describeContents());

        final Parcel srcParcel = Parcel.obtain();
        final Parcel dstParcel = Parcel.obtain();

        formatToMarshall.writeToParcel(srcParcel, 0 /*no public flags for marshalling*/);
        final byte[] mbytes = srcParcel.marshall();
        dstParcel.unmarshall(mbytes, 0, mbytes.length);
        dstParcel.setDataPosition(0);
        final AudioFormat unmarshalledFormat = AudioFormat.CREATOR.createFromParcel(dstParcel);

        assertNotNull("Failure to unmarshall AudioFormat", unmarshalledFormat);
        assertEquals("Source and destination AudioFormat not equal",
                formatToMarshall, unmarshalledFormat);
    }

    // Test case 7: Check frame size for compressed, float formats.
    public void testFrameSize() throws Exception {
        int[] encodings = {
            AudioFormat.ENCODING_MP3,
            AudioFormat.ENCODING_AAC_LC,
            AudioFormat.ENCODING_AAC_HE_V1,
            AudioFormat.ENCODING_AAC_HE_V2,
            AudioFormat.ENCODING_OPUS,
            AudioFormat.ENCODING_MPEGH_BL_L3,
            AudioFormat.ENCODING_MPEGH_BL_L4,
            AudioFormat.ENCODING_MPEGH_LC_L3,
            AudioFormat.ENCODING_MPEGH_LC_L4,
            AudioFormat.ENCODING_DTS_UHD,
            AudioFormat.ENCODING_DRA,
            AudioFormat.ENCODING_DTS_HD_MA,
            AudioFormat.ENCODING_DTS_UHD_P1,
            AudioFormat.ENCODING_DTS_UHD_P2,
        };
        for (int encoding : encodings) {
            final AudioFormat format = new AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(44100)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build();

            assertEquals("AudioFormat with encoding " + encoding + " has the wrong frame size",
                    1, format.getFrameSizeInBytes());
        }

        final AudioFormat formatPcmFloat = new AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(192000)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build();

        assertEquals("Float AudioFormat has the wrong frame size",
            2 /* channels */ * 4 /* bytes per sample */, formatPcmFloat.getFrameSizeInBytes());
    }

    // Test case 8: Check setting valid encodings
    public void testValidEncodings() throws Exception {
        int[] encodings = {
            AudioFormat.ENCODING_DEFAULT,
            AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_PCM_8BIT,
            AudioFormat.ENCODING_PCM_FLOAT,
            AudioFormat.ENCODING_AC3,
            AudioFormat.ENCODING_E_AC3,
            AudioFormat.ENCODING_DTS,
            AudioFormat.ENCODING_DTS_HD,
            AudioFormat.ENCODING_MP3,
            AudioFormat.ENCODING_AAC_LC,
            AudioFormat.ENCODING_AAC_HE_V1,
            AudioFormat.ENCODING_AAC_HE_V2,
            AudioFormat.ENCODING_IEC61937,
            AudioFormat.ENCODING_DOLBY_TRUEHD,
            AudioFormat.ENCODING_AAC_ELD,
            AudioFormat.ENCODING_AAC_XHE,
            AudioFormat.ENCODING_AC4,
            AudioFormat.ENCODING_E_AC3_JOC,
            AudioFormat.ENCODING_DOLBY_MAT,
            AudioFormat.ENCODING_OPUS,
            AudioFormat.ENCODING_PCM_24BIT_PACKED,
            AudioFormat.ENCODING_PCM_32BIT,
            AudioFormat.ENCODING_MPEGH_BL_L3,
            AudioFormat.ENCODING_MPEGH_BL_L4,
            AudioFormat.ENCODING_MPEGH_LC_L3,
            AudioFormat.ENCODING_MPEGH_LC_L4,
            AudioFormat.ENCODING_DTS_UHD_P1,
            AudioFormat.ENCODING_DRA,
            AudioFormat.ENCODING_DTS_HD_MA,
            AudioFormat.ENCODING_DTS_UHD_P2,
            AudioFormat.ENCODING_DSD,
        };
        for (int encoding : encodings) {
            final AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(44100)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build();
        }
    }

    /**
     * Check whether the bits in a are all present in b.
     *
     * Used for channel position mask verification.
     */
    private boolean subsetOf(int a, int b) {
        return Integer.bitCount(a ^ b) == Integer.bitCount(b) - Integer.bitCount(a);
    }

    /**
     * Test case 8: Check validity of channel masks
     */
    public void testChannelMasks() throws Exception {
        // Channel count check.
        int[][] maskCount = new int[][] {
                {AudioFormat.CHANNEL_OUT_MONO, 1},
                {AudioFormat.CHANNEL_OUT_STEREO, 2},
                {AudioFormat.CHANNEL_OUT_QUAD, 4},
                {AudioFormat.CHANNEL_OUT_SURROUND, 4},
                {AudioFormat.CHANNEL_OUT_5POINT1, 6},
                {AudioFormat.CHANNEL_OUT_6POINT1, 7},
                {AudioFormat.CHANNEL_OUT_5POINT1POINT2, 8},
                {AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 8},
                {AudioFormat.CHANNEL_OUT_7POINT1POINT2, 10},
                {AudioFormat.CHANNEL_OUT_5POINT1POINT4, 10},
                {AudioFormat.CHANNEL_OUT_7POINT1POINT2, 10},
                {AudioFormat.CHANNEL_OUT_7POINT1POINT4, 12},
                {AudioFormat.CHANNEL_OUT_9POINT1POINT4, 14},
                {AudioFormat.CHANNEL_OUT_13POINT_360RA, 13},
                {AudioFormat.CHANNEL_OUT_9POINT1POINT6, 16},
                {AudioFormat.CHANNEL_OUT_22POINT2, 24},
        };
        for (int[] pair : maskCount) {
            assertEquals("Mask " + Integer.toHexString(pair[0])
                    + " should have " + pair[1] + " bits set#",
                    /*expected*/ pair[1], /*actual*/ Integer.bitCount(pair[0]));
        }

        // Check channel position masks that are a subset of other masks.
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.CHANNEL_OUT_STEREO));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.CHANNEL_OUT_QUAD));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.CHANNEL_OUT_SURROUND));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_QUAD,
                AudioFormat.CHANNEL_OUT_5POINT1));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_5POINT1,
                AudioFormat.CHANNEL_OUT_5POINT1POINT2));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_5POINT1,
                AudioFormat.CHANNEL_OUT_5POINT1POINT4));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_5POINT1,
                AudioFormat.CHANNEL_OUT_6POINT1));
        // Note CHANNEL_OUT_5POINT1POINT2 not a subset of CHANNEL_OUT_5POINT1POINT4
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND,
                AudioFormat.CHANNEL_OUT_7POINT1POINT2));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND,
                AudioFormat.CHANNEL_OUT_7POINT1POINT4));
        // Note CHANNEL_OUT_7POINT1POINT2 not a subset of CHANNEL_OUT_7POINT1POINT4
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_5POINT1POINT4,
                AudioFormat.CHANNEL_OUT_7POINT1POINT4));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_7POINT1POINT4,
                AudioFormat.CHANNEL_OUT_22POINT2));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_7POINT1POINT4,
                AudioFormat.CHANNEL_OUT_9POINT1POINT4));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_9POINT1POINT4,
                AudioFormat.CHANNEL_OUT_9POINT1POINT6));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_13POINT_360RA,
                AudioFormat.CHANNEL_OUT_22POINT2));
    }

    /**
     * Test AudioFormat Builder error handling.
     *
     * @throws Exception
     */
    public void testAudioFormatBuilderError() throws Exception {
        final int BIGNUM = Integer.MAX_VALUE;

        // Note: setChannelMask() and setChannelIndexMask() are
        // validated when used, i.e. in AudioTrack and AudioRecord.

        assertThrows(IllegalArgumentException.class, () -> {
            new AudioFormat.Builder()
                    .setEncoding(BIGNUM)
                    .build();
        });

        // Sample rate out of bounds. These cases caught in AudioFormat.
        for (int sampleRate : new int[] {-BIGNUM, -1, BIGNUM}) {
            assertThrows(IllegalArgumentException.class, () -> {
                new AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .build();
            });
        }
    }
}
