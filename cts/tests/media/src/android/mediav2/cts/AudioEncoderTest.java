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

package android.mediav2.cts;

import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.CodecEncoderTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.OutputManager;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * The test verifies encoders present in media codec list in bytebuffer mode. The test feeds raw
 * input data to the component and receives compressed bitstream from the component.
 * <p>
 * At the end of encoding process, the test enforces following checks :-
 * <ul>
 *     <li>For lossless audio codecs, this file is decoded and the decoded output is expected to
 *     be bit-exact with encoder input.</li>
 *     <li>For lossy audio codecs, the output sample count (after stripping priming and padding
 *     samples) should be close enough to input sample count else it would result in noticeable
 *     av sync errors.</li>
 * </ul>
 */
@RunWith(Parameterized.class)
public class AudioEncoderTest extends CodecEncoderTestBase {
    public AudioEncoderTest(String encoder, String mediaType, EncoderConfigParams encCfgParams,
            @SuppressWarnings("unused") String testLabel, String allTestParams) {
        super(encoder, mediaType, new EncoderConfigParams[]{encCfgParams}, allTestParams);
    }

    private static EncoderConfigParams getAudioEncoderCfgParams(String mediaType, int qualityPreset,
            int sampleRate, int channelCount, int pcmEncoding) {
        EncoderConfigParams.Builder foreman = new EncoderConfigParams.Builder(mediaType)
                .setSampleRate(sampleRate)
                .setChannelCount(channelCount)
                .setPcmEncoding(pcmEncoding);
        if (mediaType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
            foreman = foreman.setCompressionLevel(qualityPreset);
        } else {
            foreman = foreman.setBitRate(qualityPreset);
        }
        return foreman.build();
    }

    private static List<Object[]> flattenParams(List<Object[]> params) {
        List<Object[]> argsList = new ArrayList<>();
        for (Object[] param : params) {
            String mediaType = (String) param[0];
            int[] qualityPresets = (int[]) param[1];
            int[] sampleRates = (int[]) param[2];
            int[] channelCounts = (int[]) param[3];
            int pcmEncoding = (int) param[4];
            for (int qualityPreset : qualityPresets) {
                for (int sampleRate : sampleRates) {
                    for (int channelCount : channelCounts) {
                        Object[] testArgs = new Object[3];
                        testArgs[0] = param[0];
                        testArgs[1] = getAudioEncoderCfgParams(mediaType, qualityPreset, sampleRate,
                                channelCount, pcmEncoding);
                        testArgs[2] = String.format("%d%s_%dkHz_%dch_%s",
                                mediaType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC) ? qualityPreset :
                                        qualityPreset / 1000,
                                mediaType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC) ? "clevel" :
                                        "kbps", sampleRate / 1000, channelCount,
                                audioEncodingToString(pcmEncoding));
                        argsList.add(testArgs);
                    }
                }
            }
        }
        return argsList;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{3}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = true;
        final boolean needVideo = false;
        List<Object[]> defArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                // mediaType, arrays of bit-rates, sample rates, channel counts, pcm encoding
                {MediaFormat.MIMETYPE_AUDIO_AAC, new int[]{64000, 128000}, new int[]{8000, 12000,
                        16000, 22050, 24000, 32000, 44100, 48000}, new int[]{1, 2},
                        AudioFormat.ENCODING_PCM_16BIT},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, new int[]{64000, 128000}, new int[]{8000, 12000,
                        16000, 24000, 48000}, new int[]{1, 2},
                        AudioFormat.ENCODING_PCM_16BIT},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, new int[]{4750, 5150, 5900, 6700, 7400, 7950,
                        10200, 12200}, new int[]{8000}, new int[]{1},
                        AudioFormat.ENCODING_PCM_16BIT},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, new int[]{6600, 8850, 12650, 14250, 15850,
                        18250, 19850, 23050, 23850}, new int[]{16000}, new int[]{1},
                        AudioFormat.ENCODING_PCM_16BIT},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8},
                        new int[]{8000, 16000, 32000, 48000, 96000, 192000}, new int[]{1, 2},
                        AudioFormat.ENCODING_PCM_16BIT},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8},
                        new int[]{8000, 16000, 32000, 48000, 96000, 192000}, new int[]{1, 2},
                        AudioFormat.ENCODING_PCM_FLOAT},
        }));
        List<Object[]> argsList = flattenParams(defArgsList);
        return prepareParamList(argsList, isEncoder, needAudio, needVideo, false);
    }

    void encodeAndValidate() throws IOException, InterruptedException {
        // encode
        setUpSource(mActiveRawRes.mFileName);
        mSaveToMem = true;
        mOutputBuff = new OutputManager();
        mCodec = MediaCodec.createByCodecName(mCodecName);
        configureCodec(mActiveEncCfg.getFormat(), false, true, true);
        MediaFormat acceptedFmt = mCodec.getInputFormat();
        assertEquals(String.format("cdd required audio encoding %s, not supported by %s \n",
                        audioEncodingToString(mActiveEncCfg.mPcmEncoding), mCodecName) + mTestConfig
                        + mTestEnv, mActiveEncCfg.mPcmEncoding,
                acceptedFmt.getInteger(MediaFormat.KEY_PCM_ENCODING,
                        AudioFormat.ENCODING_PCM_16BIT));
        mCodec.start();
        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        mCodec.release();

        // decode
        ArrayList<MediaFormat> fmts = new ArrayList<>();
        mOutFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, mActiveEncCfg.mPcmEncoding);
        fmts.add(mOutFormat);
        ArrayList<String> listOfDecoders = selectCodecs(mMediaType, fmts, null, false);
        assertFalse("no suitable codecs found for fmt: " + mOutFormat + "\n" + mTestConfig
                + mTestEnv, listOfDecoders.isEmpty());
        CodecDecoderTestBase cdtb = new CodecDecoderTestBase(listOfDecoders.get(0), mMediaType,
                null, mAllTestParams);
        cdtb.decodeToMemory(mOutputBuff.getBuffer(), mInfoList, mOutFormat, listOfDecoders.get(0));
        assertEquals(String.format("cdd required audio encoding %s, not supported by %s \n",
                        audioEncodingToString(mActiveEncCfg.mPcmEncoding), listOfDecoders.get(0))
                        + mTestConfig + mTestEnv, mActiveEncCfg.mPcmEncoding,
                cdtb.getOutputFormat().getInteger(MediaFormat.KEY_PCM_ENCODING,
                        AudioFormat.ENCODING_PCM_16BIT));

        // validate
        ByteBuffer out = cdtb.getOutputManager().getBuffer();
        if (isMediaTypeLossless(mMediaType)) {
            if (mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)
                    && mActiveEncCfg.mPcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                CodecDecoderTest.verify(cdtb.getOutputManager(), mActiveRawRes.mFileName, 3.446394f,
                        mActiveEncCfg.mPcmEncoding, -1L,
                        mTestConfig.toString() + mTestEnv.toString());
            } else {
                assertEquals("Identity test failed for lossless codec \n " + mTestConfig
                        + mTestEnv, out, ByteBuffer.wrap(mInputData));
            }
        } else {
            float tolerance = ACCEPTABLE_AV_SYNC_ERROR * mActiveEncCfg.mSampleRate
                    * mActiveEncCfg.mChannelCount * mActiveRawRes.mBytesPerSample / 1000;
            if (mInputData.length > out.limit() + tolerance) {
                String errMsg = "################    Error Details   #################\n";
                errMsg += String.format("Input sample size is %d, output sample size is %d",
                        mInputData.length, out.limit());
                fail("In the process {[i/p] -> Encode -> Decode [o/p]}, the output sample count "
                        + "is less than input sample count. This could be due to encoder-delay "
                        + "and/or encoder-padding not communicated cleanly. A/V sync errors "
                        + "possible \n" + mTestConfig + mTestEnv + errMsg + errMsg);
            }
        }
    }

    /**
     * Check description of class {@link AudioEncoderTest}
     */
    @ApiTest(apis = {"android.media.AudioFormat#ENCODING_PCM_16BIT",
            "android.media.AudioFormat#ENCODING_PCM_FLOAT"})
    @CddTest(requirements = "5.1.1/C-3-1")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testEncodeAndValidate() throws IOException, InterruptedException {
        // pre run checks
        mActiveEncCfg = mEncCfgParams[0];
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(mActiveEncCfg.getFormat());
        checkFormatSupport(mCodecName, mMediaType, true, formats, null, CODEC_OPTIONAL);

        // encode and validate
        mActiveRawRes = EncoderInput.getRawResource(mActiveEncCfg);
        assertNotNull("no raw resource found for testing config : " + mActiveEncCfg + mTestConfig
                + mTestEnv, mActiveRawRes);
        encodeAndValidate();
    }
}
