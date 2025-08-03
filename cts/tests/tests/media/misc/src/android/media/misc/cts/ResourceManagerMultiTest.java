/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.misc.cts;

import static com.android.media.codec.flags.Flags.codecImportance;

import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Range;

import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.NonMainlineTest;
import com.android.media.codec.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Validates codec resource reclaim for all the oem codecs
 * implemented on the device for a range for resolutions
 * that are supported by the codecs.
 */
@RequiresDevice
@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(Parameterized.class)
@NonMainlineTest
public class ResourceManagerMultiTest {

    private static final String TAG = "ResourceManagerMultiTest";

    public static final boolean FIRST_SDK_IS_AT_LEAST_U =
            ApiLevelUtil.isFirstApiAfter(Build.VERSION_CODES.TIRAMISU);
    public static final boolean SDK_IS_AT_LEAST_U =
            ApiLevelUtil.isAfter(Build.VERSION_CODES.TIRAMISU);
    public static final boolean VNDK_IS_AT_LEAST_U =
            SystemProperties.getInt("ro.vndk.version", Build.VERSION_CODES.CUR_DEVELOPMENT)
                > Build.VERSION_CODES.TIRAMISU;

    private static final MediaCodecList sMCL = new MediaCodecList(MediaCodecList.REGULAR_CODECS);

    @Rule
    public final ActivityTestRule<ResourceManagerStubActivity> mActivityRule =
            new ActivityTestRule<>(ResourceManagerStubActivity.class, false, false);

    @Parameterized.Parameter(0)
    public String mCodecName;

    @Parameterized.Parameter(1)
    public String mMimeType;

    @Parameterized.Parameter(2)
    public int mWidth;

    @Parameterized.Parameter(3)
    public int mHeight;

    private static Object[] getArgs(String codecName, String mimeType, int width, int height) {
        final Object[] testArgs = new Object[4];
        // First argument is the name of the codec.
        testArgs[0] = codecName;
        // Second argument is the mime type.
        testArgs[1] = mimeType;
        // Next resolution as width x height
        testArgs[2] = width;
        testArgs[3] = height;

        return testArgs;
    }

    // Constructs the parameters needed for the test case:
    // - codecName
    // - mime
    // - resolution (width x height)
    private static List<Object[]> getAllVideoCodecParameters() {
        class Resolution {
            int mWidth;
            int mHeight;

            Resolution(int w, int h) {
                mWidth = w;
                mHeight = h;
            }
        }

        // Set of codec resolutions that we want to test
        // (provided, the codec supports the same)
        final Resolution[] testResolutions = {
            new Resolution(176, 144),
            new Resolution(320, 180),
            new Resolution(352, 240),
            new Resolution(480, 360),
            new Resolution(640, 360),
            new Resolution(720, 480),
            new Resolution(1280, 720),
            new Resolution(1920, 1080),
            new Resolution(2048, 1024),
            new Resolution(3840, 2160),
            new Resolution(4096, 2048),
            new Resolution(5120, 2560),
            new Resolution(5760, 2880),
            new Resolution(7680, 3840),
            new Resolution(7680, 4320),
            new Resolution(8192, 4096),
            new Resolution(8192, 4352)
        };

        final List<Object[]> argsList = new ArrayList<>();

        for (MediaCodecInfo info : sMCL.getCodecInfos()) {
            if (info.isAlias()) {
                // don't consider aliases here
                continue;
            }
            if (info.isSoftwareOnly()) {
                // not testing the sw codecs for now.
                continue;
            }

            for (String mimeType : info.getSupportedTypes()) {
                CodecCapabilities caps = null;
                try {
                    caps = info.getCapabilitiesForType(mimeType);
                } catch (IllegalArgumentException e) {
                    // mime is not supported
                    continue;
                }
                VideoCapabilities videoCap = caps.getVideoCapabilities();
                if (videoCap == null) {
                    // Not a video codec.
                    continue;
                }

                // Get min and max supported resolution.
                Range<Integer> widthRange = videoCap.getSupportedWidths();
                int minWidth = widthRange.getLower();
                int minHeight = videoCap.getSupportedHeightsFor(minWidth).getLower();

                // Start with the lowest resolution supported by the codec.
                argsList.add(getArgs(info.getName(), mimeType, minWidth, minHeight));
                long minPixels = (long) minWidth * minHeight;

                // Skip through all the resolutions from testResolutions that are
                // smaller than the lowest resolution supported by the codec.
                int index = 0;
                for (; index < testResolutions.length; index++) {
                    Resolution resolution = testResolutions[index];
                    long pixels = (long) resolution.mWidth * resolution.mHeight;
                    if (pixels > minPixels) {
                        // Found a resolution that is higher than the
                        // lowest resolution supported by the codec.
                        break;
                    }
                }

                // Now scan through the testResolutions, starting from index
                // until we reach the highest resolution supported by the codec.
                // Add it to test vectors, if the codec supports the resolution.
                int maxWidth = widthRange.getUpper();
                int maxHeight = videoCap.getSupportedHeightsFor(maxWidth).getUpper();
                long maxPixels = (long) maxWidth * maxHeight;
                for (; index < testResolutions.length; index++) {
                    Resolution resolution = testResolutions[index];
                    long pixels = (long) resolution.mWidth * resolution.mHeight;
                    if (pixels > maxPixels) {
                        // More than the max supported resolution.
                        break;
                    }
                    // Check if this resolution is supported.
                    if (videoCap.isSizeSupported(resolution.mWidth, resolution.mHeight)) {
                        // Add it to list of parameters for the test.
                        argsList.add(getArgs(info.getName(), mimeType,
                                resolution.mWidth, resolution.mHeight));
                    }
                }

                // If the last resolution added to the test vector isn't the
                // highest resolution supported, add it in the end.
                Object[] lastArg = argsList.get(argsList.size() - 1);
                if ((int) lastArg[2] != maxWidth || (int) lastArg[3] != maxHeight) {
                    argsList.add(getArgs(info.getName(), mimeType, maxWidth, maxHeight));
                }
            }
        }

        return argsList;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{2}_{3}")
    public static Collection<Object[]> input() {
        return getAllVideoCodecParameters();
    }

    private void doTestReclaimResource(String codecName, String mimeType, int width, int height)
            throws Exception {
        ResourceManagerStubActivity activity = mActivityRule.launchActivity(new Intent());
        activity.doTestReclaimResource(codecName, mimeType, width, height);
        activity.finish();
    }

    private void doTestCodecImportanceReclaimResource(
            String codecName, String mimeType, int width, int height) throws Exception {
        ResourceManagerStubActivity activity = mActivityRule.launchActivity(new Intent());
        activity.doTestCodecImportanceReclaimResource(
                codecName, mimeType, width, height,
                true, /*high resolution*/
                true  /*change importance during codec configuration*/);
        activity.finish();
    }

    // Activity1 creates allowable number of codecs with given name (mCodecName)
    // for the given mime type (mMimeType) and the resolution as a background task.
    // Activity2 attempts to create at least one codec which should result it resource
    // manager service reclaiming a codec from Activity1 (background activity - lower priority)
    // Test verifies that the Activity1 has received a codec reclaim exception and
    // the Activity2 could create at least one codec successfully.
    @Test
    public void testReclaimResource() throws Exception {
        assumeTrue("The Device should be on at least VNDK U", VNDK_IS_AT_LEAST_U);
        doTestReclaimResource(mCodecName, mMimeType, mWidth, mHeight);
    }

    // Activity creates allowable number of codecs with given name (mCodecName)
    // for the given mime type (mMimeType) and the resolution.
    // The first codec is configured at lower importance so that the test verifies
    // that the first codec is reclaimed while starting a more important codec
    // at the later stage (when all codec resources run out).
    // Once the first (lower importance) is reclaimed, the test attempts to configure
    // another lower importance codec, which it expects to fail.
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CODEC_IMPORTANCE)
    public void testCodecImportanceReclaimResource() throws Exception {
        assumeTrue("Codec Importance Feature is OFF", codecImportance());
        doTestCodecImportanceReclaimResource(mCodecName, mMimeType, mWidth, mHeight);
    }
}
