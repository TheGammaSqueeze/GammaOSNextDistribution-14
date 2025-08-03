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
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;
import static android.mediav2.common.cts.CodecTestBase.VNDK_IS_AT_MOST_U;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.CodecTestActivity;
import android.mediav2.common.cts.OutputManager;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Test video decoders support for Adaptive Playback.
 * <p>
 * Adaptive playback support for video decoders is only activated if the codec is configured to
 * decode onto a Surface. The getOutputImage() will return null if the codec was configured with
 * an output surface. Hence any form of checksum validation for the decoded output is ruled out.
 * The only form of validation this test currently does is, it checks if the output count is same
 * as input count and output timestamps list and input timestamps list are same.
 */
@RunWith(Parameterized.class)
public class AdaptivePlaybackTest extends CodecDecoderTestBase {
    private final String[] mSrcFiles;
    private final SupportClass mSupportRequirements;
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();

    private long mMaxPts = 0;

    public AdaptivePlaybackTest(String decoder, String mediaType, String[] srcFiles,
            SupportClass supportRequirements, String allTestParams) {
        super(decoder, mediaType, null, allTestParams);
        mSrcFiles = srcFiles;
        mSupportRequirements = supportRequirements;
    }

    @Rule
    public ActivityScenarioRule<CodecTestActivity> mActivityRule =
            new ActivityScenarioRule<>(CodecTestActivity.class);

    @Before
    public void setUp() throws IOException, InterruptedException {
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
        setUpSurface(mActivity);
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = false;
        final boolean needVideo = true;
        // mediaType, array list of test files, SupportClass
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_AVC, new String[]{
                        "bbb_800x640_768kbps_30fps_avc_2b.mp4",
                        "bbb_800x640_768kbps_30fps_avc_nob.mp4",
                        "bbb_1280x720_1mbps_30fps_avc_2b.mp4",
                        "bbb_640x360_512kbps_30fps_avc_nob.mp4",
                        "bbb_1280x720_1mbps_30fps_avc_nob.mp4",
                        "bbb_640x360_512kbps_30fps_avc_2b.mp4",
                        "bbb_1280x720_1mbps_30fps_avc_nob.mp4",
                        "bbb_640x360_512kbps_30fps_avc_nob.mp4",
                        "bbb_640x360_512kbps_30fps_avc_2b.mp4"}, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, new String[]{
                        "bbb_800x640_768kbps_30fps_hevc_2b.mp4",
                        "bbb_800x640_768kbps_30fps_hevc_nob.mp4",
                        "bbb_1280x720_1mbps_30fps_hevc_2b.mp4",
                        "bbb_640x360_512kbps_30fps_hevc_nob.mp4",
                        "bbb_1280x720_1mbps_30fps_hevc_nob.mp4",
                        "bbb_640x360_512kbps_30fps_hevc_2b.mp4",
                        "bbb_1280x720_1mbps_30fps_hevc_nob.mp4",
                        "bbb_640x360_512kbps_30fps_hevc_nob.mp4",
                        "bbb_640x360_512kbps_30fps_hevc_2b.mp4"}, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP8, new String[]{
                        "bbb_800x640_768kbps_30fps_vp8.webm",
                        "bbb_1280x720_1mbps_30fps_vp8.webm",
                        "bbb_640x360_512kbps_30fps_vp8.webm"}, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP9, new String[]{
                        "bbb_800x640_768kbps_30fps_vp9.webm",
                        "bbb_1280x720_1mbps_30fps_vp9.webm",
                        "bbb_640x360_512kbps_30fps_vp9.webm"}, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, new String[]{
                        "bbb_128x96_64kbps_12fps_mpeg4.mp4",
                        "bbb_176x144_192kbps_15fps_mpeg4.mp4",
                        "bbb_128x96_64kbps_12fps_mpeg4.mp4"}, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AV1, new String[]{
                        "bbb_800x640_768kbps_30fps_av1.webm",
                        "bbb_1280x720_1mbps_30fps_av1.webm",
                        "bbb_640x360_512kbps_30fps_av1.webm"}, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, new String[]{
                        "bbb_800x640_768kbps_30fps_mpeg2_2b.mp4",
                        "bbb_800x640_768kbps_30fps_mpeg2_nob.mp4",
                        "bbb_1280x720_1mbps_30fps_mpeg2_2b.mp4",
                        "bbb_640x360_512kbps_30fps_mpeg2_nob.mp4",
                        "bbb_1280x720_1mbps_30fps_mpeg2_nob.mp4",
                        "bbb_640x360_512kbps_30fps_mpeg2_2b.mp4",
                        "bbb_1280x720_1mbps_30fps_mpeg2_nob.mp4",
                        "bbb_640x360_512kbps_30fps_mpeg2_nob.mp4",
                        "bbb_640x360_512kbps_30fps_mpeg2_2b.mp4"}, CODEC_ALL},
        }));
        // P010 support was added in Android T, hence limit the following tests to Android T and
        // above
        if (IS_AT_LEAST_T) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                    {MediaFormat.MIMETYPE_VIDEO_AVC, new String[]{
                            "cosmat_800x640_24fps_crf22_avc_10bit_2b.mkv",
                            "cosmat_800x640_24fps_crf22_avc_10bit_nob.mkv",
                            "cosmat_1280x720_24fps_crf22_avc_10bit_2b.mkv",
                            "cosmat_640x360_24fps_crf22_avc_10bit_nob.mkv",
                            "cosmat_1280x720_24fps_crf22_avc_10bit_nob.mkv",
                            "cosmat_640x360_24fps_crf22_avc_10bit_2b.mkv",
                            "cosmat_1280x720_24fps_crf22_avc_10bit_nob.mkv",
                            "cosmat_640x360_24fps_crf22_avc_10bit_nob.mkv",
                            "cosmat_640x360_24fps_crf22_avc_10bit_2b.mkv"}, CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_HEVC, new String[]{
                            "cosmat_800x640_24fps_crf22_hevc_10bit_2b.mkv",
                            "cosmat_800x640_24fps_crf22_hevc_10bit_nob.mkv",
                            "cosmat_1280x720_24fps_crf22_hevc_10bit_2b.mkv",
                            "cosmat_640x360_24fps_crf22_hevc_10bit_nob.mkv",
                            "cosmat_1280x720_24fps_crf22_hevc_10bit_nob.mkv",
                            "cosmat_640x360_24fps_crf22_hevc_10bit_2b.mkv",
                            "cosmat_1280x720_24fps_crf22_hevc_10bit_nob.mkv",
                            "cosmat_640x360_24fps_crf22_hevc_10bit_nob.mkv",
                            "cosmat_640x360_24fps_crf22_hevc_10bit_2b.mkv"}, CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_VP9, new String[]{
                            "cosmat_640x360_24fps_crf22_vp9_10bit.mkv",
                            "cosmat_1280x720_24fps_crf22_vp9_10bit.mkv",
                            "cosmat_800x640_24fps_crf22_vp9_10bit.mkv"}, CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_AV1, new String[]{
                            "cosmat_640x360_24fps_512kbps_av1_10bit.mkv",
                            "cosmat_1280x720_24fps_1200kbps_av1_10bit.mkv",
                            "cosmat_800x640_24fps_768kbps_av1_10bit.mkv"}, CODEC_ALL},
            }));
        }
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, false);
    }

    @Override
    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputBuff.saveOutPTS(info.presentationTimeUs);
            mOutputCount++;
        }
        mCodec.releaseOutputBuffer(bufferIndex, mSurface != null);
    }

    private MediaFormat createInputList(MediaFormat format, ByteBuffer buffer,
            ArrayList<MediaCodec.BufferInfo> list, int offset, long ptsOffset) {
        if (hasCSD(format)) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            bufferInfo.offset = offset;
            bufferInfo.size = 0;
            // For some devices with VNDK versions till Android U, sending a zero
            // timestamp for CSD results in out of order timestamps at the output.
            // For devices with VNDK versions > Android U, codecs are expected to
            // handle CSD buffers with timestamp set to zero.
            bufferInfo.presentationTimeUs = VNDK_IS_AT_MOST_U ? ptsOffset : 0;
            bufferInfo.flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
            for (int i = 0; ; i++) {
                String csdKey = "csd-" + i;
                if (format.containsKey(csdKey)) {
                    ByteBuffer csdBuffer = format.getByteBuffer(csdKey);
                    bufferInfo.size += csdBuffer.limit();
                    buffer.put(csdBuffer);
                    format.removeKey(csdKey);
                } else break;
            }
            list.add(bufferInfo);
            offset += bufferInfo.size;
        }
        while (true) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            bufferInfo.size = mExtractor.readSampleData(buffer, offset);
            if (bufferInfo.size < 0) break;
            bufferInfo.offset = offset;
            bufferInfo.presentationTimeUs = ptsOffset + mExtractor.getSampleTime();
            mMaxPts = Math.max(mMaxPts, bufferInfo.presentationTimeUs);
            int flags = mExtractor.getSampleFlags();
            bufferInfo.flags = 0;
            if ((flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                bufferInfo.flags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }
            list.add(bufferInfo);
            mExtractor.advance();
            offset += bufferInfo.size;
        }
        buffer.clear();
        buffer.position(offset);
        return format;
    }

    /**
     * Test video decoder for seamless resolution changes.
     */
    @ApiTest(apis = "android.media.MediaCodecInfo.CodecCapabilities#FEATURE_AdaptivePlayback")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testAdaptivePlayback() throws IOException, InterruptedException {
        Assume.assumeTrue("codec: " + mCodecName + " does not support FEATURE_AdaptivePlayback",
                isFeatureSupported(mCodecName, mMediaType,
                        MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback));
        ArrayList<MediaFormat> formats = new ArrayList<>();
        for (String file : mSrcFiles) {
            formats.add(setUpSource(MEDIA_DIR + file));
            mExtractor.release();
        }
        checkFormatSupport(mCodecName, mMediaType, false, formats,
                new String[]{MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback},
                mSupportRequirements);
        formats.clear();
        int totalSize = 0;
        for (String srcFile : mSrcFiles) {
            File file = new File(MEDIA_DIR + srcFile);
            totalSize += (int) file.length();
        }
        long ptsOffset = 0;
        int buffOffset = 0;
        ArrayList<MediaCodec.BufferInfo> list = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        for (String file : mSrcFiles) {
            formats.add(createInputList(setUpSource(MEDIA_DIR + file), buffer, list, buffOffset,
                    ptsOffset));
            mExtractor.release();
            ptsOffset = mMaxPts + 1000000L;
            buffOffset = (list.get(list.size() - 1).offset) + (list.get(list.size() - 1).size);
        }
        mOutputBuff = new OutputManager();
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            MediaFormat format = formats.get(0);
            mActivity.setScreenParams(getWidth(format), getHeight(format), true);
            mOutputBuff.reset();
            configureCodec(format, true, false, false);
            mCodec.start();
            doWork(buffer, list);
            queueEOS();
            waitForAllOutputs();
            mCodec.reset();
            mCodec.release();
        }
    }
}
