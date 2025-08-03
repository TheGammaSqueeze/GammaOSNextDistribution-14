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

import android.media.MediaFormat;
import android.mediav2.common.cts.HDRDecoderTestBase;
import android.os.Build;

import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.CddTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HDR Metadata is an aid for a display device to show the content in an optimal manner. It
 * contains the HDR content and mastering device properties that are used by the display device
 * to map the content according to its own color gamut and peak brightness. This information can
 * be part of container and/or elementary stream.
 * <p>
 * The test checks if the muxer and/or decoder propagates this information from file to application
 * correctly. Whether this information is used by the device during display is beyond the scope
 * of this test.
 */
@RunWith(Parameterized.class)
// P010 support was added in Android T, hence limit the following tests to Android T and above
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
public class DecoderHDRInfoTest extends HDRDecoderTestBase {
    private static final String LOG_TAG = DecoderHDRInfoTest.class.getSimpleName();
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    public static final HashMap<Long, String> HDR_DYNAMIC_INFO_HEVC = new HashMap<>();
    public static final HashMap<Long, String> HDR_DYNAMIC_INCORRECT_INFO_HEVC = new HashMap<>();
    public static final HashMap<Long, String> HDR_DYNAMIC_INFO_AV1 = new HashMap<>();
    public static final HashMap<Long, String> HDR_DYNAMIC_INCORRECT_INFO_AV1 = new HashMap<>();
    public static final HashMap<Long, String> HDR_DYNAMIC_INFO_VP9 = new HashMap<>();

    private final String mHDRStaticInfoStream;
    private final String mHDRStaticInfoContainer;
    private final Map<Long, String> mHDRDynamicInfoStream;
    private final Map<Long, String> mHDRDynamicInfoContainer;

    static {
        HDR_DYNAMIC_INFO_HEVC.put(0L, HDR10_INFO_SCENE_A);
        HDR_DYNAMIC_INFO_HEVC.put(133333L, HDR10_INFO_SCENE_B);
        HDR_DYNAMIC_INFO_HEVC.put(400000L, HDR10_INFO_SCENE_C);
        HDR_DYNAMIC_INFO_HEVC.put(733333L, HDR10_INFO_SCENE_D);

        HDR_DYNAMIC_INCORRECT_INFO_HEVC.put(0L, HDR10_INCORRECT_INFO_SCENE_A);
        HDR_DYNAMIC_INCORRECT_INFO_HEVC.put(133333L, HDR10_INCORRECT_INFO_SCENE_B);
        HDR_DYNAMIC_INCORRECT_INFO_HEVC.put(400000L, HDR10_INCORRECT_INFO_SCENE_C);
        HDR_DYNAMIC_INCORRECT_INFO_HEVC.put(733333L, HDR10_INCORRECT_INFO_SCENE_D);

        HDR_DYNAMIC_INFO_AV1.put(0L, HDR10_INFO_SCENE_A);
        HDR_DYNAMIC_INFO_AV1.put(133000L, HDR10_INFO_SCENE_B);
        HDR_DYNAMIC_INFO_AV1.put(400000L, HDR10_INFO_SCENE_C);
        HDR_DYNAMIC_INFO_AV1.put(733000L, HDR10_INFO_SCENE_D);

        HDR_DYNAMIC_INCORRECT_INFO_AV1.put(0L, HDR10_INCORRECT_INFO_SCENE_A);
        HDR_DYNAMIC_INCORRECT_INFO_AV1.put(133000L, HDR10_INCORRECT_INFO_SCENE_B);
        HDR_DYNAMIC_INCORRECT_INFO_AV1.put(400000L, HDR10_INCORRECT_INFO_SCENE_C);
        HDR_DYNAMIC_INCORRECT_INFO_AV1.put(733000L, HDR10_INCORRECT_INFO_SCENE_D);

        HDR_DYNAMIC_INFO_VP9.put(0L, HDR10_INFO_SCENE_A);
        HDR_DYNAMIC_INFO_VP9.put(200000L, HDR10_INFO_SCENE_B);
        HDR_DYNAMIC_INFO_VP9.put(400000L, HDR10_INFO_SCENE_C);
        HDR_DYNAMIC_INFO_VP9.put(640000L, HDR10_INFO_SCENE_D);
    }

    public DecoderHDRInfoTest(String codecName, String mediaType, String testFile,
            String hdrStaticInfoStream, String hdrStaticInfoContainer,
            Map<Long, String> hdrDynamicInfoStream, Map<Long, String> hdrDynamicInfoContainer,
            String allTestParams) {
        super(codecName, mediaType, MEDIA_DIR + testFile, allTestParams);
        mHDRStaticInfoStream = hdrStaticInfoStream;
        mHDRStaticInfoContainer = hdrStaticInfoContainer;
        mHDRDynamicInfoStream = hdrDynamicInfoStream;
        mHDRDynamicInfoContainer = hdrDynamicInfoContainer;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = false;
        final boolean needVideo = true;
        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                // codecMediaType, testFile, hdrStaticInfo in stream, hdrStaticInfo in container,
                // hdrDynamicInfo in stream, hdrDynamicInfo in container
                {MediaFormat.MIMETYPE_VIDEO_HEVC,
                        "cosmat_352x288_hdr10_stream_and_container_correct_hevc.mkv",
                        HDR_STATIC_INFO, HDR_STATIC_INFO, null, null},
                {MediaFormat.MIMETYPE_VIDEO_HEVC,
                        "cosmat_352x288_hdr10_stream_correct_container_incorrect_hevc.mkv",
                        HDR_STATIC_INFO, HDR_STATIC_INCORRECT_INFO, null, null},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_352x288_hdr10_only_stream_hevc.mkv",
                        HDR_STATIC_INFO, null, null, null},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_352x288_hdr10_only_container_hevc.mkv",
                        null, HDR_STATIC_INFO, null, null},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_352x288_hdr10_only_container_vp9.mkv",
                        null, HDR_STATIC_INFO, null, null},
                {MediaFormat.MIMETYPE_VIDEO_AV1,
                        "cosmat_352x288_hdr10_stream_and_container_correct_av1.mkv",
                        HDR_STATIC_INFO, HDR_STATIC_INFO, null, null},
                {MediaFormat.MIMETYPE_VIDEO_AV1,
                        "cosmat_352x288_hdr10_stream_correct_container_incorrect_av1.mkv",
                        HDR_STATIC_INFO, HDR_STATIC_INCORRECT_INFO, null, null},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_352x288_hdr10_only_stream_av1.mkv",
                        HDR_STATIC_INFO, null, null, null},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_352x288_hdr10_only_container_av1.mkv",
                        null, HDR_STATIC_INFO, null, null},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_352x288_hdr10plus_hevc.mp4",
                        null, null, HDR_DYNAMIC_INFO_HEVC, null},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_352x288_hdr10plus_hevc.mp4",
                        null, null, HDR_DYNAMIC_INFO_HEVC, HDR_DYNAMIC_INCORRECT_INFO_HEVC},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_352x288_hdr10plus_av1.mkv",
                        null, null, HDR_DYNAMIC_INFO_AV1, null},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_352x288_hdr10plus_av1.mkv",
                        null, null, HDR_DYNAMIC_INFO_AV1, HDR_DYNAMIC_INCORRECT_INFO_AV1},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_352x288_hdr10_only_container_vp9.mkv",
                        null, null, null, HDR_DYNAMIC_INFO_VP9},

        });
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, false);
    }

    /**
     * Check description of class {@link DecoderHDRInfoTest}
     */
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    @CddTest(requirements = {"5.3.5/C-3-1", "5.3.7/C-4-1", "5.3.9/C-3-1"})
    public void testHDRInfo() throws IOException, InterruptedException {
        validateHDRInfo(mHDRStaticInfoStream, mHDRStaticInfoContainer, mHDRDynamicInfoStream,
                mHDRDynamicInfoContainer);
    }
}
