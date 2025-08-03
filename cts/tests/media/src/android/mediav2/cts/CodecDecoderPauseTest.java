/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_ALL;

import static org.junit.Assert.fail;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.OutputManager;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Test decoders response during playback pause. The pause is emulated by stalling the
 * enqueueInput() call for a short duration during running state.
 */
@RunWith(Parameterized.class)
public class CodecDecoderPauseTest extends CodecDecoderTestBase {
    private static final String LOG_TAG = CodecDecoderPauseTest.class.getSimpleName();
    private static final long PAUSE_TIME_MS = 10000;
    private static final int NUM_FRAMES = 8;
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();

    private final SupportClass mSupportRequirements;

    public CodecDecoderPauseTest(String decoder, String mediaType, String srcFile,
            SupportClass supportRequirements, String allTestParams) {
        super(decoder, mediaType, MEDIA_DIR + srcFile, allTestParams);
        mSupportRequirements = supportRequirements;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = true;
        final boolean needVideo = true;
        // mediaType, test file, SupportClass
        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_AUDIO_AAC, "bbb_2ch_48kHz_he_aac.mp4", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_cif_avc_delay16.mp4", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_H263, "bbb_176x144_128kbps_15fps_h263.3gp", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_cif_hevc_delay15.mp4", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, "bbb_640x360_512kbps_30fps_mpeg2_2b.mp4",
                        CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, "bbb_176x144_192kbps_15fps_mpeg4.mp4",
                        CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP8, "bbb_640x360_512kbps_30fps_vp8.webm", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "bbb_cif_768kbps_30fps_vp9.mkv", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_520x390_24fps_768kbps_av1_10bit.mkv",
                        CODEC_ALL},
        });
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, false);
    }

    /**
     * Test decoder by stalling enqueueInput() call for short duration during running state. The
     * output during normal run and the output during paused run are expected to be same.
     */
    @ApiTest(apis = {"android.media.MediaCodec.Callback#onInputBufferAvailable",
            "android.media.MediaCodec#queueInputBuffer",
            "android.media.MediaCodec.Callback#onOutputBufferAvailable",
            "android.media.MediaCodec#dequeueOutputBuffer",
            "android.media.MediaCodec#releaseOutputBuffer"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testPause() throws IOException, InterruptedException {
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(setUpSource(mTestFile));
        mExtractor.release();
        checkFormatSupport(mCodecName, mMediaType, false, formats, null, mSupportRequirements);
        final boolean isAsync = true;
        MediaFormat format = setUpSource(mTestFile);
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            mSaveToMem = true;
            boolean[] boolStates = {false, true};
            OutputManager ref = new OutputManager();
            OutputManager test = new OutputManager(ref.getSharedErrorLogs());
            for (boolean enablePause : boolStates) {
                mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                configureCodec(format, isAsync, false, false);
                mOutputBuff = enablePause ? test : ref;
                mOutputBuff.reset();
                mCodec.start();
                if (enablePause) {
                    doWork(NUM_FRAMES);
                    Thread.sleep(PAUSE_TIME_MS);
                }
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                mCodec.reset();
                if (enablePause && !ref.equals(test)) {
                    fail("Output received in paused run does not match with output received in "
                            + "normal run \n" + mTestConfig + mTestEnv + test.getErrMsg());
                }
            }
            mCodec.release();
        }
        mExtractor.release();
    }
}
