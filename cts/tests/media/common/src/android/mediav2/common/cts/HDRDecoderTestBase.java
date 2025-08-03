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
import static org.junit.Assert.assertTrue;

import android.media.MediaCodec;
import android.media.MediaFormat;

import com.android.compatibility.common.util.Preconditions;

import org.junit.Assume;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/**
 * Wrapper class for testing HDR support in video decoder components
 */
public class HDRDecoderTestBase extends CodecDecoderTestBase {
    private static final String LOG_TAG = HDRDecoderTestBase.class.getSimpleName();

    private ByteBuffer mHdrStaticInfoRef;
    private ByteBuffer mHdrStaticInfoStream;
    private ByteBuffer mHdrStaticInfoContainer;
    private Map<Long, String> mHdrDynamicInfoRef;
    private Map<Long, String> mHdrDynamicInfoStream;
    private Map<Long, String> mHdrDynamicInfoContainer;
    private final ArrayList<Long> mTotalMetadataQueued = new ArrayList<>();

    public HDRDecoderTestBase(String decoder, String mediaType, String testFile,
            String allTestParams) {
        super(decoder, mediaType, testFile, allTestParams);
    }

    private String getMetadataForPts(Map<Long, String> dynamicInfoList, Long pts) {
        final int sttsToleranceUs = 1000;
        if (dynamicInfoList.containsKey(pts)) return dynamicInfoList.get(pts);
        for (Map.Entry<Long, String> entry : dynamicInfoList.entrySet()) {
            Long keyPts = entry.getKey();
            if (Math.abs(keyPts - pts) < sttsToleranceUs) return entry.getValue();
        }
        return null;
    }

    public void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        mTotalMetadataQueued.clear();
        super.resetContext(isAsync, signalEOSWithLastFrame);
    }

    protected void enqueueInput(int bufferIndex) {
        if (mHdrDynamicInfoContainer != null && mExtractor.getSampleSize() != -1) {
            String info = getMetadataForPts(mHdrDynamicInfoContainer, mExtractor.getSampleTime());
            if (info != null) {
                insertHdrDynamicInfo(loadByteArrayFromString(info));
                mTotalMetadataQueued.add(mExtractor.getSampleTime());
            }
        }
        super.enqueueInput(bufferIndex);
    }

    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (info.size > 0 && mHdrDynamicInfoRef != null) {
            MediaFormat format = mCodec.getOutputFormat(bufferIndex);
            String hdr10Info = getMetadataForPts(mHdrDynamicInfoRef, info.presentationTimeUs);
            if (hdr10Info != null) {
                validateHDRInfo(format, MediaFormat.KEY_HDR10_PLUS_INFO,
                        ByteBuffer.wrap(loadByteArrayFromString(hdr10Info)),
                        info.presentationTimeUs);
            }
        }
        super.dequeueOutput(bufferIndex, info);
    }

    public void validateHDRInfo(String hdrStaticInfoStream, String hdrStaticInfoContainer,
            Map<Long, String> hdrDynamicInfoStream, Map<Long, String> hdrDynamicInfoContainer)
            throws IOException, InterruptedException {
        mHdrStaticInfoStream = hdrStaticInfoStream != null
                ? ByteBuffer.wrap(loadByteArrayFromString(hdrStaticInfoStream)) : null;
        mHdrStaticInfoContainer = hdrStaticInfoContainer != null
                ? ByteBuffer.wrap(loadByteArrayFromString(hdrStaticInfoContainer)) : null;
        mHdrStaticInfoRef = mHdrStaticInfoStream == null ? mHdrStaticInfoContainer :
                mHdrStaticInfoStream;
        mHdrDynamicInfoStream = hdrDynamicInfoStream;
        mHdrDynamicInfoContainer = hdrDynamicInfoContainer;
        mHdrDynamicInfoRef = hdrDynamicInfoStream == null ? hdrDynamicInfoContainer :
                hdrDynamicInfoStream;

        assertTrue("reference hdr10/hdr10+ info is not supplied for validation \n" + mTestConfig
                + mTestEnv, mHdrDynamicInfoRef != null || mHdrStaticInfoRef != null);

        if (mHdrDynamicInfoStream != null || mHdrDynamicInfoContainer != null) {
            Assume.assumeNotNull("Test is only applicable to codecs that have HDR10+ profiles",
                    PROFILE_HDR10_PLUS_MAP.get(mMediaType));
        }
        if (mHdrStaticInfoStream != null || mHdrStaticInfoContainer != null) {
            Assume.assumeNotNull("Test is only applicable to codecs that have HDR10 profiles",
                    PROFILE_HDR10_MAP.get(mMediaType));
        }

        Preconditions.assertTestFileExists(mTestFile);
        // For decoders, if you intend to supply hdr10+ info using external means like json, make
        // sure that info that is being supplied is in sync with SEI info
        if (mHdrDynamicInfoStream != null && mHdrDynamicInfoContainer != null) {
            assertEquals("Container hdr10+ info size and elementary stream SEI hdr10+ info"
                    + " size are unequal \n" + mTestConfig + mTestEnv, mHdrDynamicInfoStream.size(),
                    mHdrDynamicInfoContainer.size());
            for (Map.Entry<Long, String> element : mHdrDynamicInfoStream.entrySet()) {
                assertTrue("Container hdr10+ info and elementary stream SEI hdr10+ info "
                                + "frame positions are not in sync \n" + mTestConfig + mTestEnv,
                        mHdrDynamicInfoContainer.containsKey(element.getKey()));
            }
        }
        mOutputBuff = new OutputManager();
        MediaFormat format = setUpSource(mTestFile);
        if (mHdrDynamicInfoStream != null || mHdrDynamicInfoContainer != null) {
            format.setInteger(MediaFormat.KEY_PROFILE,
                    Objects.requireNonNull(PROFILE_HDR10_PLUS_MAP.get(mMediaType),
                            "mediaType : " + mMediaType + " has no profile supporting HDR10+")[0]);
        } else {
            format.setInteger(MediaFormat.KEY_PROFILE,
                    Objects.requireNonNull(PROFILE_HDR10_MAP.get(mMediaType),
                            "mediaType : " + mMediaType + " has no profile supporting HDR10")[0]);
        }
        ArrayList<MediaFormat> formatList = new ArrayList<>();
        formatList.add(format);
        Assume.assumeTrue(mCodecName + " does not support HDR10/HDR10+ profile",
                areFormatsSupported(mCodecName, mMediaType, formatList));
        mCodec = MediaCodec.createByCodecName(mCodecName);
        configureCodec(format, false, true, false);
        mCodec.start();
        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllOutputs();
        if (mHdrStaticInfoRef != null) {
            validateHDRInfo(mCodec.getOutputFormat(), MediaFormat.KEY_HDR_STATIC_INFO,
                    mHdrStaticInfoRef, -1L);
        }
        mCodec.stop();
        mCodec.release();
        mExtractor.release();
        if (mHdrDynamicInfoContainer != null) {
            assertEquals("Test did not queue metadata of all frames",
                    mHdrDynamicInfoContainer.size(), mTotalMetadataQueued.size());
        }
    }
}
