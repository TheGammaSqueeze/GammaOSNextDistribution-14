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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaFormat;

import org.junit.Assume;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper class for testing HDR support in video encoder components
 */
public class HDREncoderTestBase extends CodecEncoderTestBase {
    private static final String LOG_TAG = HDREncoderTestBase.class.getSimpleName();

    private ByteBuffer mHdrStaticInfo;
    private Map<Long, String> mHdrDynamicInfo;
    private ArrayList<Long> mTotalMetadataQueued;
    private Map<Long, String> mHdrDynamicInfoReceived;

    public HDREncoderTestBase(String encoderName, String mediaType,
            EncoderConfigParams encCfgParams, String allTestParams) {
        super(encoderName, mediaType, new EncoderConfigParams[]{encCfgParams}, allTestParams);
    }

    private String getMetadataForPts(Map<Long, String> dynamicInfoList, Long pts) {
        final int roundToleranceUs = 10;
        if (dynamicInfoList.containsKey(pts)) return dynamicInfoList.get(pts);
        for (Map.Entry<Long, String> entry : dynamicInfoList.entrySet()) {
            Long keyPts = entry.getKey();
            if (Math.abs(keyPts - pts) < roundToleranceUs) return entry.getValue();
        }
        return null;
    }

    public void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        if (mTotalMetadataQueued != null) mTotalMetadataQueued.clear();
        if (mHdrDynamicInfoReceived != null) mHdrDynamicInfoReceived.clear();
        super.resetContext(isAsync, signalEOSWithLastFrame);
    }

    protected void enqueueInput(int bufferIndex) {
        if (mHdrDynamicInfo != null) {
            long pts = mInputOffsetPts + mInputCount * 1000000L / mActiveEncCfg.mFrameRate;
            String info = getMetadataForPts(mHdrDynamicInfo, pts);
            if (info != null) {
                insertHdrDynamicInfo(loadByteArrayFromString(info));
                mTotalMetadataQueued.add(pts);
            }
        }
        super.enqueueInput(bufferIndex);
    }

    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (mHdrDynamicInfo != null) {
            if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                MediaFormat outFormat = mCodec.getOutputFormat(bufferIndex);
                ByteBuffer metadataBuff =
                        outFormat.getByteBuffer(MediaFormat.KEY_HDR10_PLUS_INFO, null);
                if (metadataBuff != null) {
                    byte[] bytes = new byte[metadataBuff.remaining()];
                    metadataBuff.get(bytes);
                    mHdrDynamicInfoReceived.put(info.presentationTimeUs,
                            byteArrayToHexString(bytes));
                }
            }
        }
        super.dequeueOutput(bufferIndex, info);
    }

    public void validateHDRInfo(String hdrStaticInfo, Map<Long, String> hdrDynamicInfo)
            throws IOException, InterruptedException {
        mHdrStaticInfo = hdrStaticInfo != null
                ? ByteBuffer.wrap(loadByteArrayFromString(hdrStaticInfo)) : null;
        mHdrDynamicInfo = hdrDynamicInfo;

        MediaFormat format = mActiveEncCfg.getFormat();
        if (mHdrStaticInfo != null) {
            format.setByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO, mHdrStaticInfo);
        }
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        Assume.assumeTrue(mCodecName + " does not support HDR10/HDR10+ profile "
                + mActiveEncCfg.mProfile, areFormatsSupported(mCodecName, mMediaType, formats));
        Assume.assumeTrue(mCodecName + " does not support color format COLOR_FormatYUVP010",
                hasSupportForColorFormat(mCodecName, mMediaType, mActiveEncCfg.mColorFormat));

        setUpSource(mActiveRawRes.mFileName);

        int frameLimit = 4;
        if (mHdrDynamicInfo != null) {
            mTotalMetadataQueued = new ArrayList<>();
            mHdrDynamicInfoReceived = new HashMap<>();
            Long lastHdr10PlusFramePts =
                    Collections.max(mHdrDynamicInfo.entrySet(), Map.Entry.comparingByKey())
                            .getKey();
            frameLimit = (int) (lastHdr10PlusFramePts * mActiveEncCfg.mFrameRate / 1000000L) + 10;
        }
        int maxNumFrames = mInputData.length
                / (mActiveRawRes.mWidth * mActiveRawRes.mHeight * mActiveRawRes.mBytesPerSample);
        assertTrue("HDR info tests require input file with at least " + frameLimit + " frames. "
                + mActiveRawRes.mFileName + " has " + maxNumFrames + " frames. \n" + mTestConfig
                + mTestEnv, frameLimit <= maxNumFrames);

        mOutputBuff = new OutputManager();
        mMuxOutput = true;
        mCodec = MediaCodec.createByCodecName(mCodecName);
        configureCodec(format, true, true, true);
        mCodec.start();
        doWork(frameLimit);
        queueEOS();
        waitForAllOutputs();

        MediaFormat fmt = mCodec.getOutputFormat();

        mCodec.stop();
        mCodec.release();

        // verify if the out fmt contains HDR Static info as expected
        if (mHdrStaticInfo != null) {
            validateHDRInfo(fmt, MediaFormat.KEY_HDR_STATIC_INFO, mHdrStaticInfo, -1L);
        }

        // verify if the out fmt contains HDR Dynamic info as expected
        if (mHdrDynamicInfo != null) {
            assertEquals("Test did not queue metadata of all frames", mHdrDynamicInfo.size(),
                    mTotalMetadataQueued.size());
            for (Map.Entry<Long, String> entry : mHdrDynamicInfo.entrySet()) {
                Long pts = entry.getKey();
                if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_VP9)) {
                    assertTrue("At timestamp : " + pts + " application queued hdr10+ metadata,"
                            + " during dequeue application did not receive it in output format",
                            mHdrDynamicInfoReceived.containsKey(pts));
                }
                if (mHdrDynamicInfoReceived.containsKey(pts)) {
                    ByteBuffer hdrInfoRef =
                            ByteBuffer.wrap(loadByteArrayFromString(entry.getValue()));
                    ByteBuffer hdrInfoTest = ByteBuffer.wrap(
                            loadByteArrayFromString(mHdrDynamicInfoReceived.get(pts)));
                    validateHDRInfo(MediaFormat.KEY_HDR10_PLUS_INFO, hdrInfoRef, hdrInfoTest, pts);
                }
            }
        }

        // verify if the muxed file contains HDR metadata as expected
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        String decoder = codecList.findDecoderForFormat(format);
        assertNotNull("Device advertises support for encoding " + format + " but not decoding it \n"
                + mTestConfig + mTestEnv, decoder);

        HDRDecoderTestBase decoderTest =
                new HDRDecoderTestBase(decoder, mMediaType, mMuxedOutputFile, mAllTestParams);
        decoderTest.validateHDRInfo(hdrStaticInfo, hdrStaticInfo, mHdrDynamicInfo,
                mHdrDynamicInfo);
        if (HDR_INFO_IN_BITSTREAM_CODECS.contains(mMediaType)) {
            decoderTest.validateHDRInfo(hdrStaticInfo, null, mHdrDynamicInfo, null);
        }
    }
}
