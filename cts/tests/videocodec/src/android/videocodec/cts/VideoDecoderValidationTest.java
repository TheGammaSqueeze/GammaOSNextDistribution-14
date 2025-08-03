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

import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;

import static org.junit.Assert.assertEquals;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.os.Build;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;

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

/**
 * This test is similar to {@link android.mediav2.cts.CodecDecoderValidationTest},
 * {@link android.mediav2.cts.DecoderColorAspectsTest}, except the test clips are longer in
 * duration and are larger resolutions.
 */
@RunWith(Parameterized.class)
// P010 support was added in Android T, hence limit the following tests to Android T and above
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
public class VideoDecoderValidationTest extends CodecDecoderTestBase {
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();

    private final long mRefCRC;
    private final int mRange;
    private final int mStandard;
    private final int mTransfer;

    public VideoDecoderValidationTest(String decoder, String mediaType, String testFile,
            long refCRC, int range, int standard, int transfer, String allTestParams) {
        super(decoder, mediaType, MEDIA_DIR + testFile, allTestParams);
        mRefCRC = refCRC;
        mRange = range;
        mStandard = standard;
        mTransfer = transfer;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = true;
        final boolean needVideo = true;
        // mediaType, testfile, checksum, range, standard, transfer
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "HLG_Concert_HEVC_yuv420p10le_4k.mp4",
                        4194958348L, MediaFormat.COLOR_RANGE_LIMITED,
                        MediaFormat.COLOR_STANDARD_BT2020, MediaFormat.COLOR_TRANSFER_HLG},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "HLG_BirthdayParty_HEVC_yuv420p10le_4k.mp4",
                        152715480L, MediaFormat.COLOR_RANGE_LIMITED,
                        MediaFormat.COLOR_STANDARD_BT2020, MediaFormat.COLOR_TRANSFER_HLG},
        }));
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, false);
    }

    /**
     * Extract, Decode and Validate. Check description of class {@link VideoDecoderValidationTest}
     */
    @ApiTest(apis = {"MediaCodecInfo.CodecCapabilities#COLOR_FormatYUVP010",
            "MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420Flexible",
            "android.media.MediaFormat#KEY_COLOR_RANGE",
            "android.media.MediaFormat#KEY_COLOR_STANDARD",
            "android.media.MediaFormat#KEY_COLOR_TRANSFER"})
    @LargeTest
    @Test
    public void testDecodeAndValidate() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        checkFormatSupport(mCodecName, mMediaType, false, formats, null, CODEC_OPTIONAL);
        decodeToMemory(mTestFile, mCodecName, 0, MediaExtractor.SEEK_TO_CLOSEST_SYNC,
                Integer.MAX_VALUE);
        validateColorAspects(getOutputFormat(), mRange, mStandard, mTransfer);
        Assume.assumeFalse("skip checksum verification due to tone mapping",
                mSkipChecksumVerification);
        assertEquals("checksum mismatch. \n" + mTestConfig + mTestEnv, mRefCRC,
                mOutputBuff.getCheckSumImage());
    }
}

