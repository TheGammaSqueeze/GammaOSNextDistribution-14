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

package android.mediav2.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;
import static android.mediav2.cts.EncoderInput.getRawResource;

import static org.junit.Assert.assertNotNull;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.EncoderProfileLevelTestBase;
import android.mediav2.common.cts.OutputManager;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The purpose of this test is to check video encoders behaviour towards level key.
 * <p>
 * According to the documentation
 * <a href="https://developer.android.com/reference/android/media/MediaFormat#KEY_LEVEL
 * ">KEY_LEVEL</a>, cannot be used to constrain the encoder's output to a maximum encoding level.
 * Encoders are free to target a different level if the other configured encoding parameters
 * dictate it. <p>
 * The test picks an encoding configuration that is supported by the component. The test then
 * configures KEY_LEVEL to different values. The test expects the codec to not hang or codec
 * configure to not fail for any level value. The codec is expected to simply choose a supported
 * level and continue with encode operation.
 * <p>
 * At the end of encoding process, the test enforces following checks :-
 * <ul>
 *     <li>The minimum PSNR of encoded output is at least the tolerance value.</li>
 * </ul>
 */
@RunWith(Parameterized.class)
public class EncoderLevelTest extends EncoderProfileLevelTestBase {
    public EncoderLevelTest(String encoder, String mediaType, EncoderConfigParams[] encCfgParams,
            @SuppressWarnings("unused") String testLabel, String allTestParams) {
        super(encoder, mediaType, encCfgParams, allTestParams);
    }

    private static EncoderConfigParams[] getVideoEncoderCfgParams(String mediaType, int bitRate,
            int width, int height, int frameRate, int colorFormat, int[] profiles, int level) {
        ArrayList<EncoderConfigParams> cfgParams = new ArrayList<>();
        for (int profile : profiles) {
            cfgParams.add(new EncoderConfigParams.Builder(mediaType)
                    .setBitRate(bitRate)
                    .setWidth(width)
                    .setHeight(height)
                    .setFrameRate(frameRate)
                    .setProfile(profile)
                    .setLevel(level)
                    .setColorFormat(colorFormat)
                    .build());
        }
        return cfgParams.toArray(new EncoderConfigParams[0]);
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{3}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = false;
        final boolean needVideo = true;
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                // mediaType, width, height, bit-rate, frame-rate
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, 352, 288, 512000, 30},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 176, 144, 64000, 15},
                {MediaFormat.MIMETYPE_VIDEO_H263, 176, 144, 64000, 15},
                {MediaFormat.MIMETYPE_VIDEO_VP8, 352, 288, 512000, 30},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 352, 288, 512000, 30},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 512, 512, 512000, 30},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 352, 288, 512000, 30},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 352, 288, 512000, 30},
        }));
        final List<Object[]> argsList = new ArrayList<>();
        for (Object[] arg : exhaustiveArgsList) {
            final String mediaType = (String) arg[0];
            final int width = (int) arg[1];
            final int height = (int) arg[2];
            final int br = (int) arg[3];
            final int fps = (int) arg[4];

            int[] levelList = LEVEL_MAP.get(mediaType);
            int[] actualLevelList;
            if (levelList != null) {
                int levelListLength = levelList.length;
                actualLevelList = new int[levelList.length + 3];
                actualLevelList[0] = 0;  // zero (for some media types unrecognized)
                actualLevelList[1] = -1;  // level is not set in the format
                actualLevelList[2] = 101;  // unrecognized level
                System.arraycopy(levelList, 0, actualLevelList, 3, levelListLength);
            } else {
                actualLevelList = new int[]{0, -1, 101};
            }
            if (PROFILE_SDR_MAP.containsKey(mediaType)) {
                for (int level : actualLevelList) {
                    Object[] testArgs = new Object[3];
                    testArgs[0] = arg[0];
                    testArgs[1] = getVideoEncoderCfgParams(mediaType, br, width, height, fps,
                            COLOR_FormatYUV420Flexible,
                            Objects.requireNonNull(PROFILE_SDR_MAP.get(mediaType)), level);
                    testArgs[2] = String.format("%dkbps_%dx%d_%dfps_%s_%d-level", br / 1000, width,
                            height, fps, colorFormatToString(COLOR_FormatYUV420Flexible, -1),
                            level);
                    argsList.add(testArgs);
                }
            }
            if (CodecTestBase.IS_AT_LEAST_T && PROFILE_HLG_MAP.containsKey(mediaType)) {
                for (int level : actualLevelList) {
                    Object[] testArgs = new Object[3];
                    testArgs[0] = arg[0];
                    testArgs[1] = getVideoEncoderCfgParams(mediaType, br, width, height, fps,
                            COLOR_FormatYUVP010,
                            Objects.requireNonNull(PROFILE_HLG_MAP.get(mediaType)), level);
                    testArgs[2] = String.format("%dkbps_%dx%d_%dfps_%s_%d-level", br / 1000, width,
                            height, fps, colorFormatToString(COLOR_FormatYUVP010, -1), level);
                    argsList.add(testArgs);
                }
            }
        }
        return prepareParamList(argsList, isEncoder, needAudio, needVideo, false);
    }

    /**
     * Check description of class {@link EncoderLevelTest}
     */
    @ApiTest(apis = "android.media.MediaFormat#KEY_LEVEL")
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testVideoEncodeLevels() throws IOException, InterruptedException,
            CloneNotSupportedException {
        if (mEncCfgParams[0].mInputBitDepth != 8) {
            Assume.assumeTrue(mCodecName + " doesn't support " + colorFormatToString(
                            mEncCfgParams[0].mColorFormat, mEncCfgParams[0].mInputBitDepth),
                    hasSupportForColorFormat(mCodecName, mMediaType,
                            mEncCfgParams[0].mColorFormat));
        }

        mActiveRawRes = getRawResource(mEncCfgParams[0]);
        assertNotNull("no raw resource found for testing config : "
                + mEncCfgParams[0] + mTestConfig + mTestEnv, mActiveRawRes);
        setUpSource(mActiveRawRes.mFileName);
        mSaveToMem = false;
        mMuxOutput = true;
        mOutputBuff = new OutputManager();
        mCodec = MediaCodec.createByCodecName(mCodecName);
        MediaCodecInfo.CodecCapabilities codecCapabilities =
                mCodec.getCodecInfo().getCapabilitiesForType(mMediaType);
        for (EncoderConfigParams cfg : mEncCfgParams) {
            // check if format is supported by the component with out configuring level key.
            MediaFormat formatNotForUse = cfg.getFormat();
            formatNotForUse.removeKey(MediaFormat.KEY_LEVEL);
            if (!codecCapabilities.isFormatSupported(formatNotForUse)) {
                continue;
            }

            // if format is supported, then bad level key must not effect encoding.
            mActiveEncCfg = cfg;
            mOutputBuff.reset();
            configureCodec(cfg.getFormat(), false, true, true);
            mCodec.start();
            doWork(5);
            queueEOS();
            waitForAllOutputs();
            mCodec.reset();

            EncoderConfigParams.Builder foreman = cfg.getBuilder().clone().setLevel(
                    EncoderProfileLevelTest.getMinLevel(cfg.mMediaType, cfg.mWidth,
                            cfg.mHeight, cfg.mFrameRate, cfg.mBitRate, cfg.mProfile));
            mActiveEncCfg = foreman.build();
            validateProfileAndLevel();

            validateEncodedPSNR(getRawResource(cfg), mMediaType, mMuxedOutputFile, true, false,
                    ACCEPTABLE_WIRELESS_TX_QUALITY);
            deleteMuxedFile();
        }
    }
}
