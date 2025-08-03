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

package android.mediav2.common.cts;

import static org.junit.Assert.assertEquals;

import android.util.Log;

import com.android.compatibility.common.util.Preconditions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Class to compute per-frame PSNR, minimum PSNR and global PSNR between two YUV420P yuv streams.
 */
public class VideoErrorManager {
    private static final String LOG_TAG = VideoErrorManager.class.getSimpleName();
    private static final boolean ENABLE_LOGS = false;

    private final RawResource mRefYuv;
    private final RawResource mTestYuv;
    private final boolean mAllowLoopBack;

    private boolean mGenerateStats;
    private final double[] mGlobalMSE;
    private final double[] mMinimumMSE;
    private final double[] mGlobalPSNR;
    private final double[] mMinimumPSNR;
    private final double[] mAvgPSNR;
    private final ArrayList<double[]> mFramesPSNR;

    public VideoErrorManager(RawResource refYuv, RawResource testYuv, boolean allowLoopBack) {
        mRefYuv = refYuv;
        mTestYuv = testYuv;
        mAllowLoopBack = allowLoopBack;
        if (mRefYuv.mHeight != mTestYuv.mHeight || mRefYuv.mWidth != mTestYuv.mWidth
                || mRefYuv.mBytesPerSample != mTestYuv.mBytesPerSample) {
            String msg = String.format(
                    "Reference file attributes and Test file attributes are not same. Reference "
                            + "width : %d, height : %d, bytesPerSample : %d, Test width : %d, "
                            + "height : %d, bytesPerSample : %d \n",
                    mRefYuv.mWidth, mRefYuv.mHeight, mRefYuv.mBytesPerSample, mTestYuv.mWidth,
                    mTestYuv.mHeight, mTestYuv.mBytesPerSample);
            throw new IllegalArgumentException(msg);
        }
        if (((mRefYuv.mWidth & 1) != 0) || ((mRefYuv.mHeight & 1) != 0) || (
                (mRefYuv.mBytesPerSample != 1) && (mRefYuv.mBytesPerSample != 2))) {
            String msg = String.format(LOG_TAG
                            + " handles only YUV420p 8bit or 16bit inputs. Current file "
                            + "attributes are width : %d, height : %d, bytesPerSample : %d",
                    mRefYuv.mWidth, mRefYuv.mHeight, mRefYuv.mBytesPerSample);
            throw new IllegalArgumentException(msg);
        }
        mMinimumMSE = new double[3];
        Arrays.fill(mMinimumMSE, Float.MAX_VALUE);
        mGlobalMSE = new double[3];
        Arrays.fill(mGlobalMSE, 0.0);
        mGlobalPSNR = new double[3];
        mMinimumPSNR = new double[3];
        mAvgPSNR = new double[3];
        Arrays.fill(mAvgPSNR, 0.0);
        mFramesPSNR = new ArrayList<>();
    }

    static double computeMSE(byte[] data0, byte[] data1, int bytesPerSample) {
        assertEquals(data0.length, data1.length);
        int length = data0.length / bytesPerSample;
        long squareError = 0;

        if (bytesPerSample == 2) {
            short[] dataA = new short[length];
            ByteBuffer.wrap(data0).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(dataA);
            short[] dataB = new short[length];
            ByteBuffer.wrap(data1).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(dataB);
            for (int i = 0; i < length; i++) {
                long diff = ((int) dataA[i] & 0xffff) - ((int) dataB[i] & 0xffff);
                squareError += diff * diff;
            }
        } else {
            for (int i = 0; i < length; i++) {
                int diff = ((int) data0[i] & 0xff) - ((int) data1[i] & 0xff);
                squareError += diff * diff;
            }
        }
        return (double) squareError / length;
    }

    static double computePSNR(double mse, int bytesPerSample) {
        if (mse == 0) return 100.0;
        final int peakSignal = (1 << (8 * bytesPerSample)) - 1;
        return 10 * Math.log10((double) peakSignal * peakSignal / mse);
    }

    private void generateErrorStats() throws IOException {
        Preconditions.assertTestFileExists(mRefYuv.mFileName);
        Preconditions.assertTestFileExists(mTestYuv.mFileName);

        try (RandomAccessFile refStream = new RandomAccessFile(new File(mRefYuv.mFileName), "r");
             InputStream testStream = new FileInputStream(mTestYuv.mFileName)) {
            int ySize = mRefYuv.mWidth * mRefYuv.mHeight * mRefYuv.mBytesPerSample;
            int uvSize = ySize >> 2;
            byte[] yRef = new byte[ySize];
            byte[] uvRef = new byte[uvSize];
            byte[] yTest = new byte[ySize];
            byte[] uvTest = new byte[uvSize];

            int frames = 0;
            while (true) {
                int bytesReadRef = refStream.read(yRef);
                int bytesReadDec = testStream.read(yTest);
                if (bytesReadDec != ySize || (!mAllowLoopBack && bytesReadRef != ySize)) break;
                if (bytesReadRef != ySize) {
                    refStream.seek(0);
                    refStream.read(yRef);
                }
                double curYMSE = computeMSE(yRef, yTest, mRefYuv.mBytesPerSample);
                mGlobalMSE[0] += curYMSE;
                mMinimumMSE[0] = Math.min(mMinimumMSE[0], curYMSE);

                assertEquals("failed to read U Plane " + mRefYuv.mFileName
                                + " contains insufficient bytes", uvSize,
                        refStream.read(uvRef));
                assertEquals("failed to read U Plane " + mTestYuv.mFileName
                                + " contains insufficient bytes", uvSize,
                        testStream.read(uvTest));
                double curUMSE = computeMSE(uvRef, uvTest, mRefYuv.mBytesPerSample);
                mGlobalMSE[1] += curUMSE;
                mMinimumMSE[1] = Math.min(mMinimumMSE[1], curUMSE);

                assertEquals("failed to read V Plane " + mRefYuv.mFileName
                                + " contains insufficient bytes", uvSize,
                        refStream.read(uvRef));
                assertEquals("failed to read V Plane " + mTestYuv.mFileName
                                + " contains insufficient bytes", uvSize,
                        testStream.read(uvTest));
                double curVMSE = computeMSE(uvRef, uvTest, mRefYuv.mBytesPerSample);
                mGlobalMSE[2] += curVMSE;
                mMinimumMSE[2] = Math.min(mMinimumMSE[2], curVMSE);

                double yFramePSNR = computePSNR(curYMSE, mRefYuv.mBytesPerSample);
                double uFramePSNR = computePSNR(curUMSE, mRefYuv.mBytesPerSample);
                double vFramePSNR = computePSNR(curVMSE, mRefYuv.mBytesPerSample);
                mAvgPSNR[0] += yFramePSNR;
                mAvgPSNR[1] += uFramePSNR;
                mAvgPSNR[2] += vFramePSNR;
                mFramesPSNR.add(new double[]{yFramePSNR, uFramePSNR, vFramePSNR});

                if (ENABLE_LOGS) {
                    String msg = String.format(
                            "frame: %d mse_y:%,.2f mse_u:%,.2f mse_v:%,.2f psnr_y:%,.2f psnr_u:%,"
                                    + ".2f psnr_v:%,.2f",
                            frames, curYMSE, curUMSE, curVMSE, mFramesPSNR.get(frames)[0],
                            mFramesPSNR.get(frames)[1], mFramesPSNR.get(frames)[2]);
                    Log.v(LOG_TAG, msg);
                }
                frames++;
            }
            for (int i = 0; i < mGlobalPSNR.length; i++) {
                mGlobalMSE[i] /= frames;
                mGlobalPSNR[i] = computePSNR(mGlobalMSE[i], mRefYuv.mBytesPerSample);
                mMinimumPSNR[i] = computePSNR(mMinimumMSE[i], mRefYuv.mBytesPerSample);
                mAvgPSNR[i] /= frames;
            }
            if (ENABLE_LOGS) {
                String msg = String.format(
                        "global_psnr_y:%.2f, global_psnr_u:%.2f, global_psnr_v:%.2f, min_psnr_y:%"
                                + ".2f, min_psnr_u:%.2f, min_psnr_v:%.2f avg_psnr_y:%.2f, "
                                + "avg_psnr_u:%.2f, avg_psnr_v:%.2f",
                        mGlobalPSNR[0], mGlobalPSNR[1], mGlobalPSNR[2], mMinimumPSNR[0],
                        mMinimumPSNR[1], mMinimumPSNR[2], mAvgPSNR[0], mAvgPSNR[1], mAvgPSNR[2]);
                Log.v(LOG_TAG, msg);
            }
        }
    }

    /**
     * Returns Min(Ypsnr of all frames), Min(Upsnr of all frames), Min(Vpsnr of all frames) as an
     * array at subscripts 0, 1, 2 respectively
     */
    public double[] getMinimumPSNR() throws IOException {
        if (!mGenerateStats) {
            generateErrorStats();
            mGenerateStats = true;
        }
        return mMinimumPSNR;
    }

    /**
     * Returns GlobalYpsnr, GlobalUpsnr, GlobalVpsnr as an array at subscripts 0, 1, 2 respectively.
     * Globalpsnr = 10 * log10 (peakSignal * peakSignal / global mse)
     * GlobalMSE = Sum of all frames MSE / Total Frames
     * MSE = Sum of all (error * error) / Total pixels
     * error = ref[i] - test[i]
     */
    public double[] getGlobalPSNR() throws IOException {
        if (!mGenerateStats) {
            generateErrorStats();
            mGenerateStats = true;
        }
        return mGlobalPSNR;
    }

    /**
     * returns list of all frames PSNR. Each entry in the list is an array of 3 elements,
     * representing Y, U, V Planes PSNR separately
     */
    public ArrayList<double[]> getFramesPSNR() throws IOException {
        if (!mGenerateStats) {
            generateErrorStats();
            mGenerateStats = true;
        }
        return mFramesPSNR;
    }

    /**
     * Returns Avg(Ypsnr of all frames), Avg(Upsnr of all frames), Avg(Vpsnr of all frames) as an
     * array at subscripts 0, 1, 2 respectively
     */
    public double[] getAvgPSNR() throws IOException {
        if (!mGenerateStats) {
            generateErrorStats();
            mGenerateStats = true;
        }
        return mAvgPSNR;
    }
}
