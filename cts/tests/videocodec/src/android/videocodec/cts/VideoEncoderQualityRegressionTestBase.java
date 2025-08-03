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

package android.videocodec.cts;

import static android.videocodec.cts.VideoEncoderInput.RES_YUV_MAP;
import static android.videocodec.cts.VideoEncoderInput.getRawResource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.mediav2.common.cts.CompareStreams;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.RawResource;
import android.util.Log;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Wrapper class for testing quality regression.
 */
public class VideoEncoderQualityRegressionTestBase {
    private static final String TAG = VideoEncoderQualityRegressionTestBase.class.getSimpleName();
    private static final ArrayList<String> mTmpFiles = new ArrayList<>();
    protected static ArrayList<VideoEncoderInput.CompressedResource> RESOURCES = new ArrayList<>();

    protected final String mCodecName;
    protected final String mMediaType;
    protected final VideoEncoderInput.CompressedResource mCRes;
    protected final String mAllTestParams;

    static {
        System.loadLibrary("ctsvideoqualityutils_jni");
    }

    VideoEncoderQualityRegressionTestBase(String encoder, String mediaType,
            VideoEncoderInput.CompressedResource cRes, String allTestParams) {
        mCodecName = encoder;
        mMediaType = mediaType;
        mCRes = cRes;
        mAllTestParams = allTestParams;
    }

    /**
     * Decodes a compressed resource to get YUV file and logs the list of files currently residing
     * in the cache.
     */
    @BeforeClass
    public static void decodeResourcesToYuv() {
        VideoEncoderValidationTestBase.decodeStreamsToYuv(RESOURCES, RES_YUV_MAP, TAG);
    }

    /**
     * Clean up the raw resource.
     */
    @AfterClass
    public static void cleanUpResources() {
        VideoEncoderValidationTestBase.cleanUpResources();
    }

    @Before
    public void setUp() {
        assumeNotNull("no raw resource found for testing : "
                + VideoEncoderValidationTestBase.DIAGNOSTICS, getRawResource(mCRes));
    }

    @After
    public void tearDown() {
        for (String tmpFile : mTmpFiles) {
            File tmp = new File(tmpFile);
            if (tmp.exists()) assertTrue("unable to delete file " + tmpFile, tmp.delete());
        }
        mTmpFiles.clear();
    }

    protected static EncoderConfigParams getVideoEncoderCfgParams(String mediaType, int width,
            int height, int bitRate, int bitRateMode, int keyFrameInterval, int frameRate,
            int maxBFrames) {
        return new EncoderConfigParams.Builder(mediaType)
                .setWidth(width)
                .setHeight(height)
                .setBitRate(bitRate)
                .setBitRateMode(bitRateMode)
                .setKeyFrameInterval(keyFrameInterval)
                .setFrameRate(frameRate)
                .setMaxBFrames(maxBFrames)
                .build();
    }

    private native double nativeGetBDRate(double[] qualitiesA, double[] ratesA, double[] qualitiesB,
            double[] ratesB, boolean selBdSnr, StringBuilder retMsg);

    protected void getQualityRegressionForCfgs(List<EncoderConfigParams[]> cfgsUnion,
            VideoEncoderValidationTestBase[] testInstances, String[] encoderNames, RawResource res,
            int frameLimit, int frameRate, boolean setLoopBack, Predicate<Double> predicate)
            throws IOException, InterruptedException {
        assertEquals("Quality comparison is done between two sets", 2, cfgsUnion.size());
        assertTrue("Minimum of 4 points are required for polynomial curve fitting",
                cfgsUnion.get(0).length >= 4);
        double[][] psnrs = new double[cfgsUnion.size()][cfgsUnion.get(0).length];
        double[][] rates = new double[cfgsUnion.size()][cfgsUnion.get(0).length];
        for (int i = 0; i < cfgsUnion.size(); i++) {
            EncoderConfigParams[] cfgs = cfgsUnion.get(i);
            String mediaType = cfgs[0].mMediaType;
            testInstances[i].setLoopBack(setLoopBack);
            for (int j = 0; j < cfgs.length; j++) {
                testInstances[i].encodeToMemory(encoderNames[i], cfgs[j], res, frameLimit, true,
                        true);
                mTmpFiles.add(testInstances[i].getMuxedOutputFilePath());
                assertEquals("encoder did not encode the requested number of frames \n", frameLimit,
                        testInstances[i].getOutputCount());
                int outSize = testInstances[i].getOutputManager().getOutStreamSize();
                double achievedBitRate = ((double) outSize * 8 * frameRate) / (1000 * frameLimit);
                CompareStreams cs = null;
                try {
                    cs = new CompareStreams(res, mediaType,
                            testInstances[i].getMuxedOutputFilePath(), true, true);
                    final double[] globalPSNR = cs.getGlobalPSNR();
                    double weightedPSNR = (6 * globalPSNR[0] + globalPSNR[1] + globalPSNR[2]) / 8;
                    psnrs[i][j] = weightedPSNR;
                    rates[i][j] = achievedBitRate;
                } finally {
                    if (cs != null) cs.cleanUp();
                }
                testInstances[i].deleteMuxedFile();
            }
        }
        StringBuilder retMsg = new StringBuilder();
        double bdRate = nativeGetBDRate(psnrs[0], rates[0], psnrs[1], rates[1], false, retMsg);
        if (retMsg.length() != 0) fail(retMsg.toString());
        for (int i = 0; i < psnrs.length; i++) {
            retMsg.append(String.format("\nBitrate GlbPsnr Set %d\n", i));
            for (int j = 0; j < psnrs[i].length; j++) {
                retMsg.append(String.format("{%f, %f},\n", rates[i][j], psnrs[i][j]));
            }
        }
        retMsg.append(String.format(Locale.getDefault(), "bd rate: %f", bdRate));
        Log.d(TAG, retMsg.toString());
        assumeTrue(retMsg.toString(), predicate.test(bdRate));
    }
}
