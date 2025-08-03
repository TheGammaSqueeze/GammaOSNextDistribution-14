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

package android.video.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;

import java.io.IOException;

class CodecDecoderPerformanceTestBase extends CodecPerformanceTestBase {
    private static final String LOG_TAG = CodecDecoderPerformanceTestBase.class.getSimpleName();

    double mAchievedFps;

    @Rule
    public ActivityTestRule<CodecTestActivity> mActivityRule =
            new ActivityTestRule<>(CodecTestActivity.class);

    public CodecDecoderPerformanceTestBase(String decoderName, String testFile, int keyPriority,
            float scalingFactor) {
        super(decoderName, testFile, keyPriority, scalingFactor);
    }

    private void setUpFormat(MediaFormat format) throws IOException {
        mDecoderFormat = new MediaFormat(format);
        mDecoderFormat.setInteger(MediaFormat.KEY_PRIORITY, mKeyPriority);
        mDecoderFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mOperatingRateExpected = getMaxOperatingRate(mDecoderName, mDecoderMime);
        if (mMaxOpRateScalingFactor > 0.0f) {
            int operatingRateToSet = (int) (mOperatingRateExpected * mMaxOpRateScalingFactor);
            if (mMaxOpRateScalingFactor < 1.0f) {
                mOperatingRateExpected = operatingRateToSet;
            }
            mDecoderFormat.setInteger(MediaFormat.KEY_OPERATING_RATE, operatingRateToSet);
        } else if (mMaxOpRateScalingFactor < 0.0f) {
            mDecoderFormat.setInteger(MediaFormat.KEY_OPERATING_RATE, -1);
        }
    }

    private void doWork() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (!mSawDecOutputEOS) {
            if (!mSawDecInputEOS) {
                int inputBufIndex = mDecoder.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                if (inputBufIndex >= 0) {
                    enqueueDecoderInput(inputBufIndex);
                }
            }
            int outputBufIndex = mDecoder.dequeueOutputBuffer(info, Q_DEQ_TIMEOUT_US);
            if (outputBufIndex >= 0) {
                dequeueDecoderOutput(outputBufIndex, info, false);
            }
        }
    }

    public void decode() throws IOException, InterruptedException {
        MediaFormat format = setUpDecoderInput();
        assertNotNull("Video track not present in " + mTestFile, format);
        mActivityRule.getActivity().waitTillSurfaceIsCreated();
        mSurface = mActivityRule.getActivity().getSurface();
        assertTrue("Surface created is null.", mSurface != null);
        assertTrue("Surface created is invalid.", mSurface.isValid());
        setUpFormat(format);
        mDecoder = MediaCodec.createByCodecName(mDecoderName);
        mDecoder.configure(mDecoderFormat, mSurface, null, 0);
        mDecoder.start();
        long start = System.currentTimeMillis();
        doWork();
        long finish = System.currentTimeMillis();
        mDecoder.stop();
        mDecoder.release();
        mDecoder = null;
        mSurface.release();
        mSurface = null;
        assertTrue("Decoder output count is zero", mDecOutputNum > 0);
        mAchievedFps = mDecOutputNum / ((finish - start) / 1000.0);
    }

    @Override
    protected void finalize() throws Throwable {
        if (mDecoder != null) {
            mDecoder.release();
            mDecoder = null;
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
    }
}
