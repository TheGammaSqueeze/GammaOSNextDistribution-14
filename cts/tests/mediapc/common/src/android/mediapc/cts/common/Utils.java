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

package android.mediapc.cts.common;

import static android.util.DisplayMetrics.DENSITY_400;

import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint;
import android.media.MediaFormat;
import android.os.Build;
import android.os.SystemProperties;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Test utilities.
 */
public class Utils {
    private static final int sPc;

    private static final String TAG = "PerformanceClassTestUtils";
    private static final String MEDIA_PERF_CLASS_KEY = "media-performance-class";

    public static final int DISPLAY_DPI;
    public static final int MIN_DISPLAY_CANDIDATE_DPI = DENSITY_400;
    public static final int DISPLAY_LONG_PIXELS;
    public static final int MIN_DISPLAY_LONG_CANDIDATE_PIXELS = 1920;
    public static final int DISPLAY_SHORT_PIXELS;
    public static final int MIN_DISPLAY_SHORT_CANDIDATE_PIXELS = 1080;

    public static final long TOTAL_MEMORY_MB;
    // Media performance requires 6 GB minimum RAM, but keeping the following to 5 GB
    // as activityManager.getMemoryInfo() returns around 5.4 GB on a 6 GB device.
    public static final long MIN_MEMORY_PERF_CLASS_CANDIDATE_MB = 5 * 1024;
    // Android T Media performance requires 8 GB min RAM, so setting lower as above
    public static final long MIN_MEMORY_PERF_CLASS_T_MB = 7 * 1024;

    private static final boolean MEETS_AVC_CODEC_PRECONDITIONS;
    static {
        // with a default-media-performance-class that can be configured through a command line
        // argument.
        android.os.Bundle args;
        try {
            args = InstrumentationRegistry.getArguments();
        } catch (Exception e) {
            args = null;
        }
        if (args != null) {
            String mediaPerfClassArg = args.getString(MEDIA_PERF_CLASS_KEY);
            if (mediaPerfClassArg != null) {
                Log.d(TAG, "Running the tests with performance class set to " + mediaPerfClassArg);
                sPc = Integer.parseInt(mediaPerfClassArg);
            } else {
                sPc = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)
                        ? Build.VERSION.MEDIA_PERFORMANCE_CLASS
                        : SystemProperties.getInt("ro.odm.build.media_performance_class", 0);
            }
            Log.d(TAG, "performance class is " + sPc);
        } else {
            sPc = 0;
        }

        Context context;
        try {
            context = InstrumentationRegistry.getInstrumentation().getContext();
        } catch (Exception e) {
            context = null;
        }
        // When used from ItsService, context will be null
        if (context != null) {
            DisplayManager displayManager = context.getSystemService(DisplayManager.class);
            Display defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
            Display.Mode maxResolutionDisplayMode =
                    Arrays.stream(displayManager.getDisplays())
                            .map(Display::getSupportedModes)
                            .flatMap(Stream::of)
                            .max(Comparator.comparing(Display.Mode::getPhysicalHeight))
                            .orElseThrow(
                                    () -> new RuntimeException("Failed to determine max height"));
            int maxWidthPixels = maxResolutionDisplayMode.getPhysicalWidth();
            int maxHeightPixels = maxResolutionDisplayMode.getPhysicalHeight();
            DISPLAY_LONG_PIXELS = Math.max(maxWidthPixels, maxHeightPixels);
            DISPLAY_SHORT_PIXELS = Math.min(maxWidthPixels, maxHeightPixels);

            int widthPixels = defaultDisplay.getMode().getPhysicalWidth();
            int heightPixels = defaultDisplay.getMode().getPhysicalHeight();

            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            final double widthInch = (double) widthPixels / (double) metrics.xdpi;
            final double heightInch = (double) heightPixels / (double) metrics.ydpi;
            final double diagonalInch = Math.sqrt(widthInch * widthInch + heightInch * heightInch);
            final double maxDiagonalPixels =
                    Math.sqrt(maxWidthPixels * maxWidthPixels + maxHeightPixels * maxHeightPixels);
            // Use max of computed dpi and advertised dpi as these values differ in some devices.
            DISPLAY_DPI = Math.max((int) (maxDiagonalPixels / diagonalInch),
                    context.getResources().getConfiguration().densityDpi);

            ActivityManager activityManager = context.getSystemService(ActivityManager.class);
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            TOTAL_MEMORY_MB = memoryInfo.totalMem / 1024 / 1024;
        } else {
            DISPLAY_DPI = 0;
            DISPLAY_LONG_PIXELS = 0;
            DISPLAY_SHORT_PIXELS = 0;
            TOTAL_MEMORY_MB = 0;
        }
        MEETS_AVC_CODEC_PRECONDITIONS = meetsAvcCodecPreconditions();
    }

    /**
     * First defined media performance class.
     */
    private static final int FIRST_PERFORMANCE_CLASS = Build.VERSION_CODES.R;

    public static boolean isRPerfClass() {
        return sPc == Build.VERSION_CODES.R;
    }

    public static boolean isSPerfClass() {
        return sPc == Build.VERSION_CODES.S;
    }

    public static boolean isTPerfClass() {
        return sPc == Build.VERSION_CODES.TIRAMISU;
    }

    public static boolean isBeforeTPerfClass() {
        return sPc < Build.VERSION_CODES.TIRAMISU;
    }

    public static boolean isUPerfClass() {
        return sPc == Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    }

    /**
     * Latest defined media performance class.
     */
    private static final int LAST_PERFORMANCE_CLASS = Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

    public static boolean isHandheld() {
        // handheld nature is not exposed to package manager, for now
        // we check for touchscreen and NOT watch and NOT tv
        PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        return pm.hasSystemFeature(pm.FEATURE_TOUCHSCREEN)
                && !pm.hasSystemFeature(pm.FEATURE_WATCH)
                && !pm.hasSystemFeature(pm.FEATURE_TELEVISION)
                && !pm.hasSystemFeature(pm.FEATURE_AUTOMOTIVE);
    }

    private static boolean meetsAvcCodecPreconditions(boolean isEncoder) {
        // Latency tests need the following instances of codecs at 30 fps
        // 1920x1080 encoder in MediaRecorder for load conditions
        // 1920x1080 decoder and 1920x1080 encoder for load conditions
        // 1920x1080 encoder for initialization test
        // Since there is no way to know if encoder and decoder are supported concurrently at their
        // maximum load, we will test the above combined requirements are met for both encoder and
        // decoder (so a minimum of 4 instances required for both encoder and decoder)
        int minInstancesRequired = 4;
        int width = 1920;
        int height = 1080;
        double fps = 30 /* encoder for media recorder */
                + 30 /* 1080p decoder for transcoder */
                + 30 /* 1080p encoder for transcoder */
                + 30 /* 1080p encoder for latency test */;

        String avcMediaType = MediaFormat.MIMETYPE_VIDEO_AVC;
        PerformancePoint pp1080p = new PerformancePoint(width, height, (int) fps);
        MediaCodec codec;
        try {
            codec = isEncoder ? MediaCodec.createEncoderByType(avcMediaType) :
                    MediaCodec.createDecoderByType(avcMediaType);
        } catch (IOException e) {
            Log.d(TAG, "Unable to create codec " + e);
            return false;
        }
        MediaCodecInfo info = codec.getCodecInfo();
        MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(avcMediaType);
        List<PerformancePoint> pps =
                caps.getVideoCapabilities().getSupportedPerformancePoints();
        if (pps == null || pps.size() == 0) {
            Log.w(TAG, info.getName() + " doesn't advertise performance points. Assuming codec "
                    + "meets the requirements");
            codec.release();
            return true;
        }
        boolean supportsRequiredRate = false;
        for (PerformancePoint pp : pps) {
            if (pp.covers(pp1080p)) {
                supportsRequiredRate = true;
            }
        }

        boolean supportsRequiredSize = caps.getVideoCapabilities().isSizeSupported(width, height);
        boolean supportsRequiredInstances = caps.getMaxSupportedInstances() >= minInstancesRequired;
        codec.release();
        Log.d(TAG, info.getName() + " supports required FPS : " + supportsRequiredRate
                + ", supports required size : " + supportsRequiredSize
                + ", supports required instances : " + supportsRequiredInstances);
        return supportsRequiredRate && supportsRequiredSize && supportsRequiredInstances;
    }

    private static boolean meetsAvcCodecPreconditions() {
        return meetsAvcCodecPreconditions(/* isEncoder */ true)
                && meetsAvcCodecPreconditions(/* isEncoder */ false);
    }

    private static boolean meetsMemoryPrecondition() {
        if (isBeforeTPerfClass()) {
            return TOTAL_MEMORY_MB >= MIN_MEMORY_PERF_CLASS_CANDIDATE_MB;
        } else {
            return TOTAL_MEMORY_MB >= MIN_MEMORY_PERF_CLASS_T_MB;
        }
    }

    public static int getPerfClass() {
        return sPc;
    }

    public static boolean isPerfClass() {
        return sPc >= FIRST_PERFORMANCE_CLASS &&
               sPc <= LAST_PERFORMANCE_CLASS;
    }

    public static boolean meetsPerformanceClassPreconditions() {
        if (isPerfClass()) {
            return true;
        }

        // If device doesn't advertise performance class, check if this can be ruled out as a
        // candidate for performance class tests.
        if (!isHandheld()
                || !meetsMemoryPrecondition()
                || DISPLAY_DPI < MIN_DISPLAY_CANDIDATE_DPI
                || DISPLAY_LONG_PIXELS < MIN_DISPLAY_LONG_CANDIDATE_PIXELS
                || DISPLAY_SHORT_PIXELS < MIN_DISPLAY_SHORT_CANDIDATE_PIXELS
                || !MEETS_AVC_CODEC_PRECONDITIONS) {
            return false;
        }
        return true;
    }

    public static void assumeDeviceMeetsPerformanceClassPreconditions() {
        assumeTrue(
                "Test skipped because the device does not meet the hardware requirements for "
                        + "performance class.",
                meetsPerformanceClassPreconditions());
    }
}
