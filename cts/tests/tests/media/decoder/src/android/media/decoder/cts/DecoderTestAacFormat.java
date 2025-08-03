/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.media.decoder.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.decoder.cts.DecoderTest.AudioParameter;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.Preconditions;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

@AppModeFull(reason = "Instant apps cannot access the SD card")
public class DecoderTestAacFormat {
    private static final String TAG = "DecoderTestAacFormat";

    static final String mInpPrefix = WorkDir.getMediaDirString();
    private static final boolean sIsAndroidRAndAbove =
            ApiLevelUtil.isAtLeast(Build.VERSION_CODES.R);
    private static final boolean sIsAtLeastT =
            ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU);
    private static final String MIMETYPE_AAC = MediaFormat.MIMETYPE_AUDIO_AAC;
    @Before
    public void setUp() throws Exception {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        assertNotNull(inst);
    }

    /**
     * Verify downmixing to stereo at decoding of MPEG-4 HE-AAC 5.0 and 5.1 channel streams
     */
    @Test
    @CddTest(requirements = {"5.1.2/C-2-1", "5.1.2/C-7-1", "5.1.2/C-7-2"})
    public void testHeAacM4aMultichannelDownmix() throws Exception {
        Log.i(TAG, "START testDecodeHeAacMcM4a");

        if (!MediaUtils.check(sIsAndroidRAndAbove, "M-chan downmix fixed in Android R"))
            return;

        // array of multichannel resources with their expected number of channels without downmixing
        // and the channel mask of the content
        Object [][] samples = {
                //  {resource, numChannels},
                {"noise_5ch_48khz_aot5_dr_sbr_sig1_mp4.m4a", 5,
                        AudioFormat.CHANNEL_OUT_QUAD | AudioFormat.CHANNEL_OUT_FRONT_CENTER},
                {"noise_6ch_44khz_aot5_dr_sbr_sig2_mp4.m4a", 6, AudioFormat.CHANNEL_OUT_5POINT1},
        };
        for (Object[] sample: samples) {
            for (String codecName : DecoderTest.codecsFor((String)sample[0] /* resource */,
                    DecoderTest.CODEC_DEFAULT)) {
                // verify correct number of channels is observed without downmixing
                AudioParameter chanParams = new AudioParameter();
                decodeUpdateFormat(codecName, (String) sample[0] /*resource*/, chanParams,
                        0 /*no downmix*/, "" /*ignored*/);
                assertEquals("Number of channels differs for codec:" + codecName
                                +  " with no downmixing",
                        sample[1], chanParams.getNumChannels());

                // verify correct number of channels is observed when downmixing to stereo
                // - with AAC specific key
                AudioParameter aacDownmixParams = new AudioParameter();
                decodeUpdateFormat(codecName, (String) sample[0] /* resource */, aacDownmixParams,
                        2 /*stereo downmix*/,
                        MediaFormat.KEY_AAC_MAX_OUTPUT_CHANNEL_COUNT);
                assertEquals("Number of channels differs for codec:" + codecName
                                + " when downmixing with KEY_AAC_MAX_OUTPUT_CHANNEL_COUNT",
                        2, aacDownmixParams.getNumChannels());
                if (sIsAtLeastT && DecoderTest.isDefaultCodec(codecName, MIMETYPE_AAC)) {
                    // KEY_CHANNEL_MASK expected to work starting with T
                    assertEquals("Wrong channel mask with KEY_AAC_MAX_OUTPUT_CHANNEL_COUNT",
                            AudioFormat.CHANNEL_OUT_STEREO,
                            aacDownmixParams.getChannelMask());

                    // KEY_MAX_OUTPUT_CHANNEL_COUNT introduced in T
                    // - with codec-agnostic key
                    AudioParameter downmixParams = new AudioParameter();
                    decodeUpdateFormat(codecName, (String) sample[0] /* resource */, downmixParams,
                            2 /*stereo downmix*/,
                            MediaFormat.KEY_MAX_OUTPUT_CHANNEL_COUNT);
                    assertEquals("Number of channels differs for codec:" + codecName
                                    + " when downmixing with KEY_MAX_OUTPUT_CHANNEL_COUNT",
                            2, downmixParams.getNumChannels());
                    assertEquals("Wrong channel mask with KEY_MAX_OUTPUT_CHANNEL_COUNT",
                            AudioFormat.CHANNEL_OUT_STEREO,
                            aacDownmixParams.getChannelMask());

                    // verify setting value larger than actual channel count behaves like
                    // no downmixing
                    AudioParameter bigChanParams = new AudioParameter();
                    final int tooManyChannels = ((Integer) sample[1]).intValue() + 99;
                    decodeUpdateFormat(codecName, (String) sample[0] /*resource*/, bigChanParams,
                            tooManyChannels, MediaFormat.KEY_MAX_OUTPUT_CHANNEL_COUNT);
                    assertEquals("Number of channels differs for codec:" + codecName
                                    + " when setting " + tooManyChannels
                                    + " on KEY_MAX_OUTPUT_CHANNEL_COUNT",
                            sample[1], bigChanParams.getNumChannels());
                    assertEquals("Wrong channel mask with big KEY_MAX_OUTPUT_CHANNEL_COUNT",
                            ((Integer) sample[2]).intValue(),
                            bigChanParams.getChannelMask());
                }
            }
        }
    }

    /**
     *
     * @param decoderName
     * @param testInput
     * @param audioParams
     * @param downmixChannelCount 0 if no downmix requested,
     *                           positive number for number of channels in requested downmix
     * @param keyForChannelCountControl the key to use to control decoding when downmixChannelCount
     *                                  is not 0
     * @throws IOException
     */
    private void decodeUpdateFormat(String decoderName, final String testInput,
            AudioParameter audioParams, int downmixChannelCount,
            String keyForChannelCountControl)
            throws IOException
    {
        Preconditions.assertTestFileExists(mInpPrefix + testInput);
        File inpFile = new File(mInpPrefix + testInput);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
        AssetFileDescriptor testFd = new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());

        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(testFd.getFileDescriptor(), testFd.getStartOffset(),
                testFd.getLength());
        testFd.close();

        assertEquals("wrong number of tracks", 1, extractor.getTrackCount());
        MediaFormat format = extractor.getTrackFormat(0);
        String mime = format.getString(MediaFormat.KEY_MIME);
        assertTrue("not an aac audio file", mime.equals(MIMETYPE_AAC));

        MediaCodec decoder;
        if (decoderName == null) {
            decoder = MediaCodec.createDecoderByType(mime);
        } else {
            decoder = MediaCodec.createByCodecName(decoderName);
        }

        MediaFormat configFormat = format;
        if (downmixChannelCount > 0) {
            configFormat.setInteger(keyForChannelCountControl, downmixChannelCount);
        }

        Log.v(TAG, "configuring with " + configFormat);
        decoder.configure(configFormat, null /* surface */, null /* crypto */, 0 /* flags */);

        decoder.start();
        ByteBuffer[] codecInputBuffers = decoder.getInputBuffers();
        ByteBuffer[] codecOutputBuffers = decoder.getOutputBuffers();

        extractor.selectTrack(0);

        // start decoding
        final long kTimeOutUs = 5000;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        int noOutputCounter = 0;
        int samplecounter = 0;
        short[] decoded = new short[0];
        int decodedIdx = 0;
        while (!sawOutputEOS && noOutputCounter < 50) {
            noOutputCounter++;
            if (!sawInputEOS) {
                int inputBufIndex = decoder.dequeueInputBuffer(kTimeOutUs);

                if (inputBufIndex >= 0) {
                    ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];

                    int sampleSize =
                            extractor.readSampleData(dstBuf, 0 /* offset */);

                    long presentationTimeUs = 0;

                    if (sampleSize < 0) {
                        Log.d(TAG, "saw input EOS.");
                        sawInputEOS = true;
                        sampleSize = 0;
                    } else {
                        samplecounter++;
                        presentationTimeUs = extractor.getSampleTime();
                    }
                    decoder.queueInputBuffer(
                            inputBufIndex,
                            0 /* offset */,
                            sampleSize,
                            presentationTimeUs,
                            sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                    if (!sawInputEOS) {
                        extractor.advance();
                    }
                }
            }

            int res = decoder.dequeueOutputBuffer(info, kTimeOutUs);

            if (res >= 0) {
                if (info.size > 0) {
                    noOutputCounter = 0;
                }

                int outputBufIndex = res;
                ByteBuffer buf = codecOutputBuffers[outputBufIndex];

                if (decodedIdx + (info.size / 2) >= decoded.length) {
                    decoded = Arrays.copyOf(decoded, decodedIdx + (info.size / 2));
                }

                buf.position(info.offset);
                for (int i = 0; i < info.size; i += 2) {
                    decoded[decodedIdx++] = buf.getShort();
                }

                decoder.releaseOutputBuffer(outputBufIndex, false /* render */);

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "saw output EOS.");
                    sawOutputEOS = true;
                }
            } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = decoder.getOutputBuffers();
                Log.d(TAG, "output buffers have changed.");
            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat outputFormat = decoder.getOutputFormat();
                try {
                    audioParams.setNumChannels(
                            outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                } catch (NullPointerException e) {
                    fail("KEY_CHANNEL_COUNT not found on output format");
                }
                try {
                    audioParams.setSamplingRate(
                            outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                } catch (NullPointerException e) {
                    fail("KEY_SAMPLE_RATE not found on output format");
                }
                if (sIsAtLeastT && DecoderTest.isDefaultCodec(decoderName, MIMETYPE_AAC)) {
                    try {
                        audioParams.setChannelMask(
                                outputFormat.getInteger(MediaFormat.KEY_CHANNEL_MASK));
                    } catch (NullPointerException e) {
                        fail("KEY_CHANNEL_MASK not found on output format");
                    }
                }
                Log.i(TAG, "output format has changed to " + outputFormat);
            } else {
                Log.d(TAG, "dequeueOutputBuffer returned " + res);
            }
        }
        if (noOutputCounter >= 50) {
            fail("decoder stopped outputing data");
        }
        decoder.stop();
        decoder.release();
        extractor.release();
    }
}

