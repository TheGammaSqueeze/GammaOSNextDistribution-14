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

package android.mediav2.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;

import static org.junit.Assert.assertNotNull;

import android.media.MediaFormat;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.HDREncoderTestBase;
import android.os.Build;

import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.CddTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Test to validate hdr static and dynamic metadata in encoders.
 * HDR Metadata is an aid for a display device to show the content in an optimal manner. It
 * contains the HDR content and mastering device properties that are used by the display device
 * to map the content according to its own color gamut and peak brightness. This information can
 * be part of container and/or elementary stream. If the encoder is configured with hdr metadata,
 * then it is expected to place this information in the elementary stream as-is. If a muxer is
 * configured with hdr metadata then it is expected to place this information in container as-is.
 * This test validates these requirements.
 */
@RunWith(Parameterized.class)
// P010 support was added in Android T, hence limit the following tests to Android T and above
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
public class EncoderHDRInfoTest extends HDREncoderTestBase {
    private static final String LOG_TAG = EncoderHDRInfoTest.class.getSimpleName();
    public static final HashMap<Long, String> HDR_DYNAMIC_INFO = new HashMap<>();

    private final String mHDRStaticInfo;
    private final Map<Long, String> mHDRDynamicInfo;

    static {
        HDR_DYNAMIC_INFO.put(0L, HDR10_INFO_SCENE_A);
        HDR_DYNAMIC_INFO.put(133333L, HDR10_INFO_SCENE_B);
        HDR_DYNAMIC_INFO.put(400000L, HDR10_INFO_SCENE_C);
        HDR_DYNAMIC_INFO.put(733333L, HDR10_INFO_SCENE_D);
    }

    public EncoderHDRInfoTest(String encoderName, String mediaType,
            EncoderConfigParams encCfgParams, @SuppressWarnings("unused") String testLabel,
            String hdrStaticInfo, Map<Long, String> hdrDynamicInfo, String allTestParams) {
        super(encoderName, mediaType, encCfgParams, allTestParams);
        mHDRStaticInfo = hdrStaticInfo;
        mHDRDynamicInfo = hdrDynamicInfo;
    }

    private static EncoderConfigParams getVideoEncoderCfgParams(String mediaType, int profile,
             int maxBframe) {
        return new EncoderConfigParams.Builder(mediaType)
                .setBitRate(512000)
                .setWidth(352)
                .setHeight(288)
                .setMaxBFrames(maxBframe)
                .setColorFormat(COLOR_FormatYUVP010)
                .setProfile(profile)
                .setRange(MediaFormat.COLOR_RANGE_LIMITED)
                .setStandard(MediaFormat.COLOR_STANDARD_BT2020)
                .setTransfer(MediaFormat.COLOR_TRANSFER_ST2084)
                .build();
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{3}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = false;
        final boolean needVideo = true;
        final String[] HDRMediaTypes = new String[]{
                MediaFormat.MIMETYPE_VIDEO_AV1,
                MediaFormat.MIMETYPE_VIDEO_HEVC,
                MediaFormat.MIMETYPE_VIDEO_VP9
        };
        final int[] maxBFrames = {0, 2};

        final List<Object[]> exhaustiveArgsList = new ArrayList<>();
        for (String mediaType : HDRMediaTypes) {
            for (int maxBFrame : maxBFrames) {
                // mediaType, bitrate, width, height, maxBFrames, hdrStaticInfo, hdrDynamicInfo
                if (!mediaType.equals(MediaFormat.MIMETYPE_VIDEO_HEVC) && maxBFrame != 0) {
                    continue;
                }
                int profile = Objects.requireNonNull(PROFILE_HDR10_MAP.get(mediaType),
                        "mediaType : " + mediaType + " has no profile supporting HDR10")[0];
                exhaustiveArgsList.add(new Object[]{mediaType, getVideoEncoderCfgParams(mediaType,
                        profile, maxBFrame), String.format("%dkbps_%dx%d_%s_%s_%d-bframes", 512,
                        352, 288, "yuvp010", "hdrstaticinfo", maxBFrame), HDR_STATIC_INFO, null});

                profile = Objects.requireNonNull(PROFILE_HDR10_PLUS_MAP.get(mediaType),
                        "mediaType : " + mediaType + " has no profile supporting HDR10+")[0];
                exhaustiveArgsList.add(new Object[]{mediaType, getVideoEncoderCfgParams(mediaType,
                        profile, maxBFrame), String.format("%dkbps_%dx%d_%s_%s_%d-bframes", 512,
                        352, 288, "yuvp010", "hdrdynamicinfo", maxBFrame), null, HDR_DYNAMIC_INFO});
            }
        }
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, false);
    }

    /**
     * Check description of class {@link EncoderHDRInfoTest}
     */
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    @CddTest(requirements = {"5.12/C-6-4", "5.2.6/C-1-3"})
    public void testHDRInfo() throws IOException, InterruptedException {
        mActiveEncCfg = mEncCfgParams[0];
        mActiveRawRes = EncoderInput.getRawResource(mActiveEncCfg);
        assertNotNull("no raw resource found for testing config : " + mActiveEncCfg + mTestConfig
                + mTestEnv, mActiveRawRes);
        validateHDRInfo(mHDRStaticInfo, mHDRDynamicInfo);
    }
}
