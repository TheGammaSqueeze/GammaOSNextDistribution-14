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

package android.videocodec.cts;

import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;
import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;
import static android.mediav2.common.cts.CodecTestBase.ComponentClass.HARDWARE;
import static android.videocodec.cts.VideoEncoderInput.getRawResource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.mediav2.common.cts.CompareStreams;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.RawResource;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 1. MinMaxResolutionsTest should query the ranges of supported width and height using
 * MediaCodecInfo.VideoCapabilities, test the min resolution and the max resolution of the encoder.
 * <p> Test Params:  Input configuration = Resolution: min/max, frame rate: 30, bitrate: choose
 * basing on resolution </p>
 * 2. MinMaxBitrateTest should query the range of the supported bitrates, and test min/max of them.
 * <p> Test Params: Input configuration = Resolution: choose basing on bitrate, frame rate: 30,
 * bitrate: min/max </p>
 * 3. MinMaxFrameRatesTest should query the range of the supported frame rates, and test min/max
 * of them.
 * <p> Test Params:  Input configuration = Resolution: 720p, frame rate: min/max, bitrate: 5mbps
 * </p>
 * All tests should run for following combinations:
 * <p>Bitrate mode = CBR/VBR, MaxBFrames = 0/1, Codec type = AVC/HEVC, Intra frame interval = 0/1
 * second</p>
 */
@RunWith(Parameterized.class)
public class VideoEncoderMinMaxTest extends VideoEncoderValidationTestBase {
    private static final float MIN_ACCEPTABLE_QUALITY = 20.0f;  // psnr in dB
    private static final int FRAME_LIMIT = 300;
    private static final int TARGET_WIDTH = 1280;
    private static final int TARGET_HEIGHT = 720;
    private static final int TARGET_FRAME_RATE = 30;
    private static final int TARGET_BIT_RATE = 5000000;
    private static final List<Object[]> exhaustiveArgsList = new ArrayList<>();

    private static EncoderConfigParams getVideoEncoderCfgParams(String mediaType, int bitRateMode,
            int maxBFrames, int intraInterval) {
        return new EncoderConfigParams.Builder(mediaType)
                .setWidth(TARGET_WIDTH)
                .setHeight(TARGET_HEIGHT)
                .setBitRate(TARGET_BIT_RATE)
                .setBitRateMode(bitRateMode)
                .setMaxBFrames(maxBFrames)
                .setKeyFrameInterval(intraInterval)
                .setFrameRate(TARGET_FRAME_RATE)
                .build();
    }

    private static void addParams() {
        final String[] mediaTypes = new String[]{MediaFormat.MIMETYPE_VIDEO_AVC,
                MediaFormat.MIMETYPE_VIDEO_HEVC};
        final int[] maxBFramesPerSubGop = new int[]{0, 1};
        final int[] intraIntervals = new int[]{0, 1};
        final int[] bitRateModes = new int[]{BITRATE_MODE_CBR, BITRATE_MODE_VBR};
        for (String mediaType : mediaTypes) {
            for (int maxBFrames : maxBFramesPerSubGop) {
                for (int intraInterval : intraIntervals) {
                    for (int bitRateMode : bitRateModes) {
                        // mediaType, cfg
                        exhaustiveArgsList.add(
                                new Object[]{mediaType, getVideoEncoderCfgParams(mediaType,
                                        bitRateMode, maxBFrames, intraInterval)});
                    }
                }
            }
        }
    }

    private static List<Object> applyMinMaxRanges(MediaCodecInfo.VideoCapabilities caps,
            Object cfgObject) throws CloneNotSupportedException {
        List<Object> cfgObjects = new ArrayList<>();
        EncoderConfigParams cfgParam = (EncoderConfigParams) cfgObject;
        int minW = caps.getSupportedWidths().getLower();
        int minHForMinW = caps.getSupportedHeightsFor(minW).getLower();
        int maxHForMinW = caps.getSupportedHeightsFor(minW).getUpper();
        int minH = caps.getSupportedHeights().getLower();
        int minWForMinH = caps.getSupportedWidthsFor(minH).getLower();
        int maxWForMinH = caps.getSupportedWidthsFor(minH).getUpper();
        int maxW = caps.getSupportedWidths().getUpper();
        int minHForMaxW = caps.getSupportedHeightsFor(maxW).getLower();
        int maxHForMaxW = caps.getSupportedHeightsFor(maxW).getUpper();
        int maxH = caps.getSupportedHeights().getUpper();
        int minWForMaxH = caps.getSupportedWidthsFor(maxH).getLower();
        int maxWForMaxH = caps.getSupportedWidthsFor(maxH).getUpper();
        int minBitRate = caps.getBitrateRange().getLower();
        int maxBitRate = caps.getBitrateRange().getUpper();

        // min max res & bitrate tests
        android.util.Range<Double> rates = caps.getSupportedFrameRatesFor(minW, minHForMinW);
        int frameRate = rates.clamp((double) TARGET_FRAME_RATE).intValue();
        cfgObjects.add(cfgParam.getBuilder().setWidth(minW).setHeight(minHForMinW)
                .setFrameRate(frameRate).setBitRate(minBitRate).build());
        rates = caps.getSupportedFrameRatesFor(maxW, maxHForMaxW);
        frameRate = rates.clamp((double) TARGET_FRAME_RATE).intValue();
        cfgObjects.add(cfgParam.getBuilder().setWidth(maxW).setHeight(maxHForMaxW)
                .setFrameRate(frameRate).setBitRate(maxBitRate).build());
        int bitrate;
        if (minW != minWForMinH || minH != minHForMinW) {
            rates = caps.getSupportedFrameRatesFor(minWForMinH, minH);
            frameRate = rates.clamp((double) TARGET_FRAME_RATE).intValue();
            bitrate = caps.getBitrateRange().clamp((int) (maxBitRate / Math.sqrt(
                    (double) maxW * maxHForMaxW / minWForMinH / minH)));
            cfgObjects.add(cfgParam.getBuilder().setWidth(minWForMinH).setHeight(minH)
                    .setFrameRate(frameRate).setBitRate(bitrate).build());
        }
        if (maxW != maxWForMaxH || maxH != maxHForMaxW) {
            rates = caps.getSupportedFrameRatesFor(maxWForMaxH, maxH);
            frameRate = rates.clamp((double) TARGET_FRAME_RATE).intValue();
            bitrate = caps.getBitrateRange().clamp((int) (maxBitRate / Math.sqrt(
                    (double) maxW * maxHForMaxW / maxWForMaxH / maxH)));
            cfgObjects.add(cfgParam.getBuilder().setWidth(maxWForMaxH).setHeight(maxH)
                    .setFrameRate(frameRate).setBitRate(bitrate).build());
        }

        rates = caps.getSupportedFrameRatesFor(minW, maxHForMinW);
        frameRate = rates.clamp((double) TARGET_FRAME_RATE).intValue();
        bitrate = caps.getBitrateRange().clamp((int) (maxBitRate / Math.sqrt(
                (double) maxW * maxHForMaxW / minW / maxHForMinW)));
        cfgObjects.add(cfgParam.getBuilder().setWidth(minW).setHeight(maxHForMinW)
                .setFrameRate(frameRate).setBitRate(bitrate).build());

        rates = caps.getSupportedFrameRatesFor(maxWForMinH, minH);
        frameRate = rates.clamp((double) TARGET_FRAME_RATE).intValue();
        bitrate = caps.getBitrateRange().clamp((int) (maxBitRate / Math.sqrt(
                (double) maxW * maxHForMaxW / maxWForMinH / minH)));
        cfgObjects.add(cfgParam.getBuilder().setWidth(maxWForMinH).setHeight(minH)
                .setFrameRate(frameRate).setBitRate(bitrate).build());
        if (maxW != maxWForMinH || minH != minHForMaxW) {
            rates = caps.getSupportedFrameRatesFor(maxW, minHForMaxW);
            frameRate = rates.clamp((double) TARGET_FRAME_RATE).intValue();
            bitrate = caps.getBitrateRange().clamp((int) (maxBitRate / Math.sqrt(
                    (double) maxW * maxHForMaxW / maxW / minHForMaxW)));
            cfgObjects.add(cfgParam.getBuilder().setWidth(maxW).setHeight(minHForMaxW)
                    .setFrameRate(frameRate).setBitRate(bitrate).build());
        }
        if (minW != minWForMaxH || maxH != maxHForMinW) {
            rates = caps.getSupportedFrameRatesFor(minWForMaxH, maxH);
            frameRate = rates.clamp((double) TARGET_FRAME_RATE).intValue();
            bitrate = caps.getBitrateRange().clamp((int) (maxBitRate / Math.sqrt(
                    (double) maxW * maxHForMaxW / minWForMaxH / maxH)));
            cfgObjects.add(cfgParam.getBuilder().setWidth(minWForMaxH).setHeight(maxH)
                    .setFrameRate(frameRate).setBitRate(bitrate).build());
        }

        // min-max frame rate tests
        try {
            int minFps = caps.getSupportedFrameRatesFor(TARGET_WIDTH, TARGET_HEIGHT).getLower()
                    .intValue();
            cfgObjects.add(cfgParam.getBuilder().setFrameRate(minFps).build());
        } catch (IllegalArgumentException ignored) {
        }
        try {
            int maxFps = caps.getSupportedFrameRatesFor(TARGET_WIDTH, TARGET_HEIGHT).getUpper()
                    .intValue();
            cfgObjects.add(cfgParam.getBuilder().setFrameRate(maxFps).build());
        } catch (IllegalArgumentException ignored) {
        }

        return cfgObjects;
    }

    private static MediaCodecInfo getCodecInfo(String codecName, String mediaType) {
        for (MediaCodecInfo info : MEDIA_CODEC_LIST_REGULAR.getCodecInfos()) {
            if (info.getName().equals(codecName)) {
                for (String type : info.getSupportedTypes()) {
                    if (mediaType.equals(type)) {
                        return info;
                    }
                }
            }
        }
        return null;
    }

    private static List<Object> getMinMaxRangeCfgObjects(Object codecName, Object mediaType,
            Object cfgObject) throws CloneNotSupportedException {
        MediaCodecInfo info = getCodecInfo((String) codecName, (String) mediaType);
        MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType((String) mediaType);
        return applyMinMaxRanges(caps.getVideoCapabilities(), cfgObject);
    }

    private static Collection<Object[]> updateParamList(Collection<Object[]> paramList)
            throws CloneNotSupportedException {
        Collection<Object[]> newParamList = new ArrayList<>();
        for (Object[] arg : paramList) {
            List<Object> cfgObjects = getMinMaxRangeCfgObjects(arg[0], arg[1], arg[2]);
            for (Object obj : cfgObjects) {
                Object[] argUpdate = new Object[arg.length + 1];
                System.arraycopy(arg, 0, argUpdate, 0, arg.length);
                argUpdate[2] = obj;
                EncoderConfigParams cfgVar = (EncoderConfigParams) obj;
                String label = String.format("%.2fmbps_%dx%d_%dfps_maxb-%d_%s_i-dist-%d",
                        cfgVar.mBitRate / 1000000., cfgVar.mWidth, cfgVar.mHeight,
                        cfgVar.mFrameRate, cfgVar.mMaxBFrames,
                        bitRateModeToString(cfgVar.mBitRateMode), (int) cfgVar.mKeyFrameInterval);
                argUpdate[arg.length - 1] = label;
                argUpdate[arg.length] = paramToString(argUpdate);
                newParamList.add(argUpdate);
            }
        }
        return newParamList;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{3}")
    public static Collection<Object[]> input() throws CloneNotSupportedException {
        addParams();
        return updateParamList(prepareParamList(exhaustiveArgsList, true, false, true, false,
                HARDWARE));
    }

    public VideoEncoderMinMaxTest(String encoder, String mediaType, EncoderConfigParams cfgParams,
            @SuppressWarnings("unused") String testLabel, String allTestParams) {
        super(encoder, mediaType, cfgParams, allTestParams);
    }

    @Before
    public void setUp() {
        mIsLoopBack = true;
    }

    @ApiTest(apis = {"VideoCapabilities#getSupportedWidths",
            "VideoCapabilities#getSupportedHeightsFor",
            "VideoCapabilities#getSupportedWidthsFor",
            "VideoCapabilities#getSupportedHeights",
            "VideoCapabilities#getSupportedFrameRatesFor",
            "VideoCapabilities#getBitrateRange",
            "android.media.MediaFormat#KEY_WIDTH",
            "android.media.MediaFormat#KEY_HEIGHT",
            "android.media.MediaFormat#KEY_BITRATE",
            "android.media.MediaFormat#KEY_FRAME_RATE"})
    @Test
    public void testMinMaxSupport() throws IOException, InterruptedException {
        MediaFormat format = mEncCfgParams[0].getFormat();
        MediaCodecInfo info = getCodecInfo(mCodecName, mMediaType);
        assumeTrue(mCodecName + " does not support bitrate mode : " + bitRateModeToString(
                        mEncCfgParams[0].mBitRateMode),
                info.getCapabilitiesForType(mMediaType).getEncoderCapabilities()
                        .isBitrateModeSupported(mEncCfgParams[0].mBitRateMode));
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        assertTrue("Encoder: " + mCodecName + " doesn't support format: " + format,
                areFormatsSupported(mCodecName, mMediaType, formats));
        RawResource res = getRawResource(mEncCfgParams[0]);
        assertNotNull("no raw resource found for testing config : " + mEncCfgParams[0] + mTestConfig
                + mTestEnv + DIAGNOSTICS, res);
        encodeToMemory(mCodecName, mEncCfgParams[0], res, FRAME_LIMIT, false, true);
        CompareStreams cs = null;
        StringBuilder msg = new StringBuilder();
        boolean isOk = true;
        try {
            cs = new CompareStreams(res, mMediaType, mMuxedOutputFile, true, mIsLoopBack);
            final double[] minPSNR = cs.getMinimumPSNR();
            for (int i = 0; i < minPSNR.length; i++) {
                if (minPSNR[i] < MIN_ACCEPTABLE_QUALITY) {
                    msg.append(String.format("For %d plane, minPSNR is less than tolerance"
                                    + " threshold, Got %f, Threshold %f", i, minPSNR[i],
                            MIN_ACCEPTABLE_QUALITY));
                    isOk = false;
                    break;
                }
            }
        } finally {
            if (cs != null) cs.cleanUp();
        }
        assertEquals("encoder did not encode the requested number of frames \n"
                + mTestConfig + mTestEnv, FRAME_LIMIT, mOutputCount);
        assertTrue("Encountered frames with PSNR less than configured threshold "
                + MIN_ACCEPTABLE_QUALITY + "dB \n" + msg + mTestConfig + mTestEnv, isOk);
    }
}
