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
 * distributed under the License is distributed on an "AS IS" BASIS
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the Licnse.
 */

package android.mediapc.cts.common;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.hardware.camera2.CameraMetadata;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.cts.verifier.CtsVerifierReportLog;

import com.google.common.base.Preconditions;

import org.junit.rules.TestName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Logs a set of measurements and results for defined performance class requirements.
 *
 * <p> Nested classes are organized alphabetically, add[Requirement] functions are organized by
 * their requirement number in the order they appear in the Android CDD
 */
public class PerformanceClassEvaluator {
    private static final String TAG = PerformanceClassEvaluator.class.getSimpleName();

    private final String mTestName;
    private Set<Requirement> mRequirements;

    public PerformanceClassEvaluator(TestName testName) {
        Preconditions.checkNotNull(testName);
        String baseTestName = testName.getMethodName() != null ? testName.getMethodName() : "";
        this.mTestName = baseTestName.replace("{", "(").replace("}", ")");
        this.mRequirements = new HashSet<Requirement>();
    }

    String getTestName() {
        return mTestName;
    }

    public static class AudioTap2ToneLatencyRequirement extends Requirement {
        private static final String TAG = AudioTap2ToneLatencyRequirement.class.getSimpleName();

        private AudioTap2ToneLatencyRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setNativeLatency(double latency) {
            this.setMeasuredValue(RequirementConstants.API_NATIVE_LATENCY, latency);
        }

        public void setJavaLatency(double latency) {
            this.setMeasuredValue(RequirementConstants.API_JAVA_LATENCY, latency);
        }

        public static AudioTap2ToneLatencyRequirement createR5_6__H_1_1() {
            RequiredMeasurement<Double> apiNativeLatency = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.API_NATIVE_LATENCY)
                    .setPredicate(RequirementConstants.DOUBLE_LTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 80.0)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 80.0)
                    .addRequiredValue(Build.VERSION_CODES.S, 100.0)
                    .addRequiredValue(Build.VERSION_CODES.R, 100.0)
                    .build();
            RequiredMeasurement<Double> apiJavaLatency = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.API_JAVA_LATENCY)
                    .setPredicate(RequirementConstants.DOUBLE_LTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 80.0)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 80.0)
                    .addRequiredValue(Build.VERSION_CODES.S, 100.0)
                    .addRequiredValue(Build.VERSION_CODES.R, 100.0)
                    .build();

            return new AudioTap2ToneLatencyRequirement(
                    RequirementConstants.R5_6__H_1_1,
                    apiNativeLatency,
                    apiJavaLatency);
        }
    }

    public static class Camera240FpsRequirement extends Requirement {
        private static final String TAG = Camera240FpsRequirement.class.getSimpleName();

        private Camera240FpsRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setRear240FpsSupported(boolean rear240FpsSupported) {
            this.setMeasuredValue(RequirementConstants.REAR_CAMERA_240FPS_SUPPORTED,
                    rear240FpsSupported);
        }

        /**
         * [2.2.7.2/7.5/H-1-9] MUST have a rear-facing primary camera supporting 720p
         * or 1080p @ 240fps.
         */
        public static Camera240FpsRequirement create240FpsReq() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERA_240FPS_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();

            return new Camera240FpsRequirement(RequirementConstants.R7_5__H_1_9, requirement);
        }
    }

    public static class CameraExtensionRequirement extends Requirement {
        private static final String TAG =
                CameraExtensionRequirement.class.getSimpleName();

        public static int PRIMARY_REAR_CAMERA = 0;
        public static int PRIMARY_FRONT_CAMERA = 1;

        private CameraExtensionRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setCamera2NightExtensionSupported(int camera, boolean supported) {
            if (camera == PRIMARY_REAR_CAMERA) {
                this.setMeasuredValue(RequirementConstants.REAR_CAMERA2_EXTENSION_NIGHT_SUPPORTED,
                        supported);
            } else if (camera == PRIMARY_FRONT_CAMERA) {
                this.setMeasuredValue(RequirementConstants.FRONT_CAMERA2_EXTENSION_NIGHT_SUPPORTED,
                        supported);
            }
        }

        public void setCameraXNightExtensionSupported(int camera, boolean supported) {
            if (camera == PRIMARY_REAR_CAMERA) {
                this.setMeasuredValue(RequirementConstants.REAR_CAMERAX_EXTENSION_NIGHT_SUPPORTED,
                        supported);
            } else if (camera == PRIMARY_FRONT_CAMERA) {
                this.setMeasuredValue(RequirementConstants.FRONT_CAMERAX_EXTENSION_NIGHT_SUPPORTED,
                        supported);
            }
        }

        /**
         * [2.2.7.2/7.5/H-1-15] MUST support Night mode extensions via both CameraX and
         * Camera2 extensions for primary cameras.
         */
        public static CameraExtensionRequirement createCameraExtensionReq() {
            RequiredMeasurement<Boolean> rearCamera2NightRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERA2_EXTENSION_NIGHT_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();
            RequiredMeasurement<Boolean> frontCamera2NightRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.FRONT_CAMERA2_EXTENSION_NIGHT_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();

            RequiredMeasurement<Boolean> rearCameraXNightRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERAX_EXTENSION_NIGHT_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();
            RequiredMeasurement<Boolean> frontCameraXNightRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.FRONT_CAMERAX_EXTENSION_NIGHT_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();

            return new CameraExtensionRequirement(RequirementConstants.R7_5__H_1_15,
                    rearCamera2NightRequirement, frontCamera2NightRequirement,
                    rearCameraXNightRequirement, frontCameraXNightRequirement);
        }
    }

    public static class CameraLatencyRequirement extends Requirement {
        private static final String TAG = CameraTimestampSourceRequirement.class.getSimpleName();

        private CameraLatencyRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setRearCameraLatency(float latency) {
            this.setMeasuredValue(RequirementConstants.REAR_CAMERA_LATENCY, latency);
        }

        public void setFrontCameraLatency(float latency) {
            this.setMeasuredValue(RequirementConstants.FRONT_CAMERA_LATENCY, latency);
        }

        /**
         * [2.2.7.2/7.5/H-1-5] MUST have camera2 JPEG capture latency < 1000ms for 1080p resolution
         * as measured by the CTS camera PerformanceTest under ITS lighting conditions
         * (3000K) for both primary cameras.
         */
        public static CameraLatencyRequirement createJpegLatencyReq() {
            RequiredMeasurement<Float> rearJpegLatency = RequiredMeasurement
                    .<Float>builder()
                    .setId(RequirementConstants.REAR_CAMERA_LATENCY)
                    .setPredicate(RequirementConstants.FLOAT_LTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 1000.0f)
                    .addRequiredValue(Build.VERSION_CODES.S, 1000.0f)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1000.0f)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 1000.0f)
                    .build();
            RequiredMeasurement<Float> frontJpegLatency = RequiredMeasurement
                    .<Float>builder()
                    .setId(RequirementConstants.FRONT_CAMERA_LATENCY)
                    .setPredicate(RequirementConstants.FLOAT_LTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 1000.0f)
                    .addRequiredValue(Build.VERSION_CODES.S, 1000.0f)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1000.0f)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 1000.0f)
                    .build();

            return new CameraLatencyRequirement(RequirementConstants.R7_5__H_1_5,
                    rearJpegLatency, frontJpegLatency);
        }

        /**
         * [2.2.7.2/7.5/H-1-6] MUST have camera2 startup latency (open camera to first
         * preview frame) < 600ms (S and below) or 500ms (T and above) as measured by the CTS camera
         * PerformanceTest under ITS lighting conditions (3000K) for both primary cameras.
         */
        public static CameraLatencyRequirement createLaunchLatencyReq() {
            RequiredMeasurement<Float> rearLaunchLatency = RequiredMeasurement
                    .<Float>builder()
                    .setId(RequirementConstants.REAR_CAMERA_LATENCY)
                    .setPredicate(RequirementConstants.FLOAT_LTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 600.0f)
                    .addRequiredValue(Build.VERSION_CODES.S, 600.0f)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 500.0f)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 500.0f)
                    .build();
            RequiredMeasurement<Float> frontLaunchLatency = RequiredMeasurement
                    .<Float>builder()
                    .setId(RequirementConstants.FRONT_CAMERA_LATENCY)
                    .setPredicate(RequirementConstants.FLOAT_LTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 600.0f)
                    .addRequiredValue(Build.VERSION_CODES.S, 600.0f)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 500.0f)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 500.0f)
                    .build();

            return new CameraLatencyRequirement(RequirementConstants.R7_5__H_1_6,
                    rearLaunchLatency, frontLaunchLatency);
        }
    }

    public static class CameraRawRequirement extends Requirement {
        private static final String TAG = CameraRawRequirement.class.getSimpleName();

        private CameraRawRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setRearRawSupported(boolean rearRawSupported) {
            this.setMeasuredValue(RequirementConstants.REAR_CAMERA_RAW_SUPPORTED,
                    rearRawSupported);
        }

        /**
         * [2.2.7.2/7.5/H-1-8] MUST support CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW and
         * android.graphics.ImageFormat.RAW_SENSOR for the primary back camera.
         */
        public static CameraRawRequirement createRawReq() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERA_RAW_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.S, true)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();

            return new CameraRawRequirement(RequirementConstants.R7_5__H_1_8, requirement);
        }
    }

    public static class CameraTimestampSourceRequirement extends Requirement {
        private static final String TAG = CameraTimestampSourceRequirement.class.getSimpleName();
        private static final int TIMESTAMP_REALTIME =
                CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME;

        private CameraTimestampSourceRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setRearCameraTimestampSource(Integer timestampSource) {
            this.setMeasuredValue(RequirementConstants.REAR_CAMERA_TIMESTAMP_SOURCE,
                    timestampSource);
        }

        public void setFrontCameraTimestampSource(Integer timestampSource) {
            this.setMeasuredValue(RequirementConstants.FRONT_CAMERA_TIMESTAMP_SOURCE,
                    timestampSource);
        }
        /**
         * [2.2.7.2/7.5/H-1-4] MUST support CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME
         * for both primary cameras.
         */
        public static CameraTimestampSourceRequirement createTimestampSourceReq() {
            RequiredMeasurement<Integer> rearTimestampSource = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.REAR_CAMERA_TIMESTAMP_SOURCE)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, TIMESTAMP_REALTIME)
                    .addRequiredValue(Build.VERSION_CODES.S, TIMESTAMP_REALTIME)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, TIMESTAMP_REALTIME)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, TIMESTAMP_REALTIME)
                    .build();
            RequiredMeasurement<Integer> frontTimestampSource = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.FRONT_CAMERA_TIMESTAMP_SOURCE)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, TIMESTAMP_REALTIME)
                    .addRequiredValue(Build.VERSION_CODES.S, TIMESTAMP_REALTIME)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, TIMESTAMP_REALTIME)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, TIMESTAMP_REALTIME)
                    .build();

            return new CameraTimestampSourceRequirement(RequirementConstants.R7_5__H_1_4,
                    rearTimestampSource, frontTimestampSource);
        }
    }

    public static class CodecInitLatencyRequirement extends Requirement {

        private static final String TAG = CodecInitLatencyRequirement.class.getSimpleName();

        private CodecInitLatencyRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setCodecInitLatencyMs(long codecInitLatencyMs) {
            this.setMeasuredValue(RequirementConstants.CODEC_INIT_LATENCY, codecInitLatencyMs);
        }

        /**
         * [2.2.7.1/5.1/H-1-7] MUST have a codec initialization latency of 65(R) / 50(S) / 40(T)
         * ms or less for a 1080p or smaller video encoding session for all hardware video
         * encoders when under load. Load here is defined as a concurrent 1080p to 720p
         * video-only transcoding session using hardware video codecs together with the 1080p
         * audio-video recording initialization. For Dolby vision codec, the codec initialization
         * latency MUST be 50 ms or less.
         */
        public static CodecInitLatencyRequirement createR5_1__H_1_7(String mediaType) {
            long latency = mediaType.equals(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION) ? 50L : 40L;
            RequiredMeasurement<Long> codec_init_latency =
                RequiredMeasurement.<Long>builder().setId(RequirementConstants.CODEC_INIT_LATENCY)
                        .setPredicate(RequirementConstants.LONG_LTE)
                        .addRequiredValue(Build.VERSION_CODES.R, 65L)
                        .addRequiredValue(Build.VERSION_CODES.S, 50L)
                        .addRequiredValue(Build.VERSION_CODES.TIRAMISU, latency)
                        .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, latency)
                        .build();

            return new CodecInitLatencyRequirement(RequirementConstants.R5_1__H_1_7,
                codec_init_latency);
        }

        /**
         * [2.2.7.1/5.1/H-1-8] MUST have a codec initialization latency of 50(R) / 40(S) / 30(T)
         * ms or less for a 128 kbps or lower bitrate audio encoding session for all audio
         * encoders when under load. Load here is defined as a concurrent 1080p to 720p
         * video-only transcoding session using hardware video codecs together with the 1080p
         * audio-video recording initialization.
         */
        public static CodecInitLatencyRequirement createR5_1__H_1_8() {
            RequiredMeasurement<Long> codec_init_latency =
                RequiredMeasurement.<Long>builder().setId(RequirementConstants.CODEC_INIT_LATENCY)
                        .setPredicate(RequirementConstants.LONG_LTE)
                        .addRequiredValue(Build.VERSION_CODES.R, 50L)
                        .addRequiredValue(Build.VERSION_CODES.S, 40L)
                        .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 30L)
                        .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 30L)
                        .build();

            return new CodecInitLatencyRequirement(RequirementConstants.R5_1__H_1_8,
                codec_init_latency);
        }

        /**
         * [2.2.7.1/5.1/H-1-12] Codec initialization latency of 40ms or less for a 1080p or
         * smaller video decoding session for all hardware video encoders when under load. Load
         * here is defined as a concurrent 1080p to 720p video-only transcoding session using
         * hardware video codecs together with the 1080p audio-video recording initialization.
         */
        public static CodecInitLatencyRequirement createR5_1__H_1_12() {
            RequiredMeasurement<Long> codec_init_latency =
                RequiredMeasurement.<Long>builder().setId(RequirementConstants.CODEC_INIT_LATENCY)
                        .setPredicate(RequirementConstants.LONG_LTE)
                        .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 40L)
                        .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 40L)
                        .build();

            return new CodecInitLatencyRequirement(RequirementConstants.R5_1__H_1_12,
                    codec_init_latency);
        }

        /**
         * [2.2.7.1/5.1/H-1-13] Codec initialization latency of 30ms or less for a 128kbps or
         * lower bitrate audio decoding session for all audio encoders when under load. Load here
         * is defined as a concurrent 1080p to 720p video-only transcoding session using hardware
         * video codecs together with the 1080p audio-video recording initialization.
         */
        public static CodecInitLatencyRequirement createR5_1__H_1_13() {
            RequiredMeasurement<Long> codec_init_latency =
                RequiredMeasurement.<Long>builder().setId(RequirementConstants.CODEC_INIT_LATENCY)
                        .setPredicate(RequirementConstants.LONG_LTE)
                        .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 30L)
                        .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 30L)
                        .build();

            return new CodecInitLatencyRequirement(RequirementConstants.R5_1__H_1_13,
                    codec_init_latency);
        }
    }

    // used for requirements [2.2.7.1/5.1/H-1-1], [2.2.7.1/5.1/H-1-2], [2.2.7.1/5.1/H-1-3],
    // [2.2.7.1/5.1/H-1-4], [2.2.7.1/5.1/H-1-5], [2.2.7.1/5.1/H-1-6], [2.2.7.1/5.1/H-1-9],
    // [2.2.7.1/5.1/H-1-10]
    public static class ConcurrentCodecRequirement extends Requirement {
        private static final String TAG = ConcurrentCodecRequirement.class.getSimpleName();
        // allowed tolerance in measured fps vs expected fps in percentage, i.e. codecs achieving
        // fps that is greater than (FPS_TOLERANCE_FACTOR * expectedFps) will be considered as
        // passing the test
        private static final double FPS_TOLERANCE_FACTOR = 0.95;
        private static final double FPS_30_TOLERANCE = 30.0 * FPS_TOLERANCE_FACTOR;
        static final int REQUIRED_MIN_CONCURRENT_INSTANCES = 6;
        static final int REQUIRED_MIN_CONCURRENT_INSTANCES_FOR_VP9 = 2;

        private ConcurrentCodecRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setConcurrentInstances(int concurrentInstances) {
            this.setMeasuredValue(RequirementConstants.CONCURRENT_SESSIONS,
                    concurrentInstances);
        }

        public void setConcurrentFps(double achievedFps) {
            this.setMeasuredValue(RequirementConstants.CONCURRENT_FPS, achievedFps);
        }

        // copied from android.mediapc.cts.getReqMinConcurrentInstances due to build issues on aosp
        public static int getReqMinConcurrentInstances(int performanceClass, String mimeType1,
            String mimeType2, int resolution) {
            ArrayList<String> MEDIAPC_CONCURRENT_CODECS_R = new ArrayList<>(
                Arrays.asList(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC));
            ArrayList<String> MEDIAPC_CONCURRENT_CODECS = new ArrayList<>(Arrays
                .asList(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC,
                    MediaFormat.MIMETYPE_VIDEO_VP9, MediaFormat.MIMETYPE_VIDEO_AV1));

            if (performanceClass >= Build.VERSION_CODES.TIRAMISU) {
                return resolution >= 1080 ? REQUIRED_MIN_CONCURRENT_INSTANCES : 0;
            } else if (performanceClass == Build.VERSION_CODES.S) {
                if (resolution >= 1080) {
                    return 0;
                }
                if (MEDIAPC_CONCURRENT_CODECS.contains(mimeType1) && MEDIAPC_CONCURRENT_CODECS
                    .contains(mimeType2)) {
                    if (MediaFormat.MIMETYPE_VIDEO_VP9.equalsIgnoreCase(mimeType1)
                        || MediaFormat.MIMETYPE_VIDEO_VP9.equalsIgnoreCase(mimeType2)) {
                        return REQUIRED_MIN_CONCURRENT_INSTANCES_FOR_VP9;
                    } else {
                        return REQUIRED_MIN_CONCURRENT_INSTANCES;
                    }
                } else {
                    return 0;
                }
            } else if (performanceClass == Build.VERSION_CODES.R) {
                if (resolution >= 1080) {
                    return 0;
                }
                if (MEDIAPC_CONCURRENT_CODECS_R.contains(mimeType1) && MEDIAPC_CONCURRENT_CODECS_R
                    .contains(mimeType2)) {
                    return REQUIRED_MIN_CONCURRENT_INSTANCES;
                } else {
                    return 0;
                }
            } else {
                return 0;
            }
        }

        private static double getReqMinConcurrentFps(int performanceClass, String mimeType1,
            String mimeType2, int resolution) {
            return FPS_30_TOLERANCE * getReqMinConcurrentInstances(performanceClass, mimeType1,
                mimeType2, resolution);
        }

        /**
         * Helper method used to create ConcurrentCodecRequirements, builds and fills out the
         * a requirement for tests ran with a resolution of 720p
         */
        private static ConcurrentCodecRequirement create720p(String requirementId,
                RequiredMeasurement<?> measure) {
            RequiredMeasurement<Integer> testResolution = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.TEST_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, 720)
                    .build();

            ConcurrentCodecRequirement req = new ConcurrentCodecRequirement(requirementId, measure,
                    testResolution);
            req.setMeasuredValue(RequirementConstants.TEST_RESOLUTION, 720);
            return req;
        }

        /**
         * Helper method used to create ConcurrentCodecRequirements, builds and fills out the
         * a requirement for tests ran with a resolution of 1080p
         */
        private static ConcurrentCodecRequirement create1080p(String requirementId,
                RequiredMeasurement<?> measure) {
            RequiredMeasurement<Integer> testResolution = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.TEST_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1080)
                    .build();

            ConcurrentCodecRequirement req = new ConcurrentCodecRequirement(requirementId, measure,
                    testResolution);
            req.setMeasuredValue(RequirementConstants.TEST_RESOLUTION, 1080);
            return req;
        }

        /**
         * Helper method used to create ConcurrentCodecRequirements, builds and fills out the
         * a requirement for tests ran with a resolution of 4k
         */
        private static ConcurrentCodecRequirement create4k(String requirementId,
                RequiredMeasurement<?> measure) {
            RequiredMeasurement<Integer> testResolution = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.TEST_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 2160)
                    .build();

            ConcurrentCodecRequirement req = new ConcurrentCodecRequirement(requirementId, measure,
                    testResolution);
            req.setMeasuredValue(RequirementConstants.TEST_RESOLUTION,  2160);
            return req;
        }

        /**
         * [2.2.7.1/5.1/H-1-1] MUST advertise the maximum number of hardware video decoder
         * sessions that can be run concurrently in any codec combination via the
         * CodecCapabilities.getMaxSupportedInstances() and VideoCapabilities
         * .getSupportedPerformancePoints() methods.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_1_720p(String mimeType1,
                String mimeType2, int resolution) {
            RequiredMeasurement<Integer> maxInstances = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.CONCURRENT_SESSIONS)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R,
                        getReqMinConcurrentInstances(
                                Build.VERSION_CODES.R, mimeType1, mimeType2, resolution))
                    .addRequiredValue(Build.VERSION_CODES.S,
                        getReqMinConcurrentInstances(
                                Build.VERSION_CODES.S, mimeType1, mimeType2, resolution))
                    .build();

            return create720p(RequirementConstants.R5_1__H_1_1, maxInstances);
        }

        /**
         * [2.2.7.1/5.1/H-1-1] MUST advertise the maximum number of hardware video decoder
         * sessions that can be run concurrently in any codec combination via the
         * CodecCapabilities.getMaxSupportedInstances() and VideoCapabilities
         * .getSupportedPerformancePoints() methods.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_1_1080p() {
            RequiredMeasurement<Integer> maxInstances = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.CONCURRENT_SESSIONS)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 6)
                    .build();

            return create1080p(RequirementConstants.R5_1__H_1_1, maxInstances);
        }

        /**
         * [2.2.7.1/5.1/H-1-1] MUST advertise the maximum number of hardware video decoder
         * sessions that can be run concurrently in any codec combination via the
         * CodecCapabilities.getMaxSupportedInstances() and VideoCapabilities
         * .getSupportedPerformancePoints() methods.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_1_4k() {
            RequiredMeasurement<Integer> maxInstances = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.CONCURRENT_SESSIONS)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 6)
                    .build();

            return create4k(RequirementConstants.R5_1__H_1_1, maxInstances);
        }

        /**
         * [2.2.7.1/5.1/H-1-2] MUST support 6 instances of hardware video decoder sessions (AVC,
         * HEVC, VP9* or later) in any codec combination running concurrently at 720p(R,S)
         * resolution@30 fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_2_720p(String mimeType1,
            String mimeType2, int resolution) {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R,
                        getReqMinConcurrentFps(
                                Build.VERSION_CODES.R, mimeType1, mimeType2, resolution))
                    .addRequiredValue(Build.VERSION_CODES.S,
                        getReqMinConcurrentFps(
                                Build.VERSION_CODES.S, mimeType1, mimeType2, resolution))
                    .build();

            return create720p(RequirementConstants.R5_1__H_1_2, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-2] MUST support 6 instances of hardware video decoder sessions (AVC,
         * HEVC, VP9* or later) in any codec combination running concurrently at 1080p(T)
         * resolution@30 fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_2_1080p() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 6 * FPS_30_TOLERANCE)
                    .build();

            return create1080p(RequirementConstants.R5_1__H_1_2, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-2] MUST support 6 instances of hardware video decoder sessions (AVC,
         * HEVC, VP9, AV1 or later) in any codec combination running concurrently with 3 sessions
         * at 1080p resolution@30 fps and 3 sessions at 4k(U) resolution@30fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_2_4k() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 6 * FPS_30_TOLERANCE)
                    .build();

            return create4k(RequirementConstants.R5_1__H_1_2, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-3] MUST advertise the maximum number of hardware video encoder
         * sessions that can be run concurrently in any codec combination via the
         * CodecCapabilities.getMaxSupportedInstances() and VideoCapabilities
         * .getSupportedPerformancePoints() methods.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_3_720p(String mimeType1,
            String mimeType2, int resolution) {
            RequiredMeasurement<Integer> maxInstances = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.CONCURRENT_SESSIONS)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R,
                        getReqMinConcurrentInstances(
                                Build.VERSION_CODES.R, mimeType1, mimeType2, resolution))
                    .addRequiredValue(Build.VERSION_CODES.S,
                        getReqMinConcurrentInstances(
                                Build.VERSION_CODES.S, mimeType1, mimeType2, resolution))
                    .build();

            return create720p(RequirementConstants.R5_1__H_1_3, maxInstances);
        }

        /**
         * [2.2.7.1/5.1/H-1-3] MUST advertise the maximum number of hardware video encoder
         * sessions that can be run concurrently in any codec combination via the
         * CodecCapabilities.getMaxSupportedInstances() and VideoCapabilities
         * .getSupportedPerformancePoints() methods.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_3_1080p() {
            RequiredMeasurement<Integer> maxInstances = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.CONCURRENT_SESSIONS)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 6)
                    .build();

            return create1080p(RequirementConstants.R5_1__H_1_3, maxInstances);
        }

        /**
         * [2.2.7.1/5.1/H-1-3] MUST advertise the maximum number of hardware video encoder
         * sessions that can be run concurrently in any codec combination via the
         * CodecCapabilities.getMaxSupportedInstances() and VideoCapabilities
         * .getSupportedPerformancePoints() methods.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_3_4K() {
            RequiredMeasurement<Integer> maxInstances = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.CONCURRENT_SESSIONS)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 6)
                    .build();

            return create4k(RequirementConstants.R5_1__H_1_3, maxInstances);
        }

        /**
         * [2.2.7.1/5.1/H-1-4] MUST support 6 instances of hardware video encoder sessions (AVC,
         * HEVC, VP9* or later) in any codec combination running concurrently at 720p(R,S)
         * resolution@30 fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_4_720p() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    // Requirement not asserted since encoder test runs in byte buffer mode
                    .addRequiredValue(Build.VERSION_CODES.R, 0.0)
                    .addRequiredValue(Build.VERSION_CODES.S, 0.0)
                    .build();

            return create720p(RequirementConstants.R5_1__H_1_4, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-4] MUST support 6 instances of hardware video encoder sessions (AVC,
         * HEVC, VP9* or later) in any codec combination running concurrently at 1080p(T)
         * resolution@30 fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_4_1080p() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    // Requirement not asserted since encoder test runs in byte buffer mode
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 0.0)
                    .build();

            return create1080p(RequirementConstants.R5_1__H_1_4, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-4] MUST support 6 instances of hardware video encoder sessions (AVC,
         * HEVC, VP9* or later) in any codec combination running concurrently at 4k(U)
         * resolution@30 fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_4_4k() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    // Requirement not asserted since encoder test runs in byte buffer mode
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 0.0)
                    .build();

            return create4k(RequirementConstants.R5_1__H_1_4, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-5] MUST advertise the maximum number of hardware video encoder and
         * decoder sessions that can be run concurrently in any codec combination via the
         * CodecCapabilities.getMaxSupportedInstances() and VideoCapabilities
         * .getSupportedPerformancePoints() methods.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_5_720p(String mimeType1,
            String mimeType2, int resolution) {
            RequiredMeasurement<Integer> maxInstances = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.CONCURRENT_SESSIONS)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R,
                        getReqMinConcurrentInstances(
                                Build.VERSION_CODES.R, mimeType1, mimeType2, resolution))
                    .addRequiredValue(Build.VERSION_CODES.S,
                        getReqMinConcurrentInstances(
                                Build.VERSION_CODES.S, mimeType1, mimeType2, resolution))
                    .build();

            return create720p(RequirementConstants.R5_1__H_1_5, maxInstances);
        }

        /**
         * [2.2.7.1/5.1/H-1-5] MUST advertise the maximum number of hardware video encoder and
         * decoder sessions that can be run concurrently in any codec combination via the
         * CodecCapabilities.getMaxSupportedInstances() and VideoCapabilities
         * .getSupportedPerformancePoints() methods.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_5_1080p() {
            RequiredMeasurement<Integer> maxInstances = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.CONCURRENT_SESSIONS)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 6)
                    .build();

            return create1080p(RequirementConstants.R5_1__H_1_5, maxInstances);
        }

        /**
         * [2.2.7.1/5.1/H-1-5] MUST advertise the maximum number of hardware video encoder and
         * decoder sessions that can be run concurrently in any codec combination via the
         * CodecCapabilities.getMaxSupportedInstances() and VideoCapabilities
         * .getSupportedPerformancePoints() methods.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_5_4k() {
            RequiredMeasurement<Integer> maxInstances = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.CONCURRENT_SESSIONS)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 6)
                    .build();

            return create4k(RequirementConstants.R5_1__H_1_5, maxInstances);
        }

        /**
         * [2.2.7.1/5.1/H-1-6] Support 6 instances of hardware video decoder and hardware video
         * encoder sessions (AVC, HEVC, VP9 or AV1) in any codec combination running concurrently
         * at 720p(R,S) /1080p(T) /4k(U) @30fps resolution.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_6_720p(String mimeType1,
            String mimeType2, int resolution) {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    // Test transcoding, fps calculated for encoder and decoder combined so req / 2
                    .addRequiredValue(Build.VERSION_CODES.R,
                        getReqMinConcurrentFps(
                                Build.VERSION_CODES.R, mimeType1, mimeType2, resolution)
                            / 2)
                    .addRequiredValue(Build.VERSION_CODES.S,
                        getReqMinConcurrentFps(
                                Build.VERSION_CODES.S, mimeType1, mimeType2, resolution)
                            / 2)
                    .build();

            return create720p(RequirementConstants.R5_1__H_1_6, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-6] Support 6 instances of hardware video decoder and hardware video
         * encoder sessions (AVC, HEVC, VP9 or AV1) in any codec combination running concurrently
         * at 720p(R,S) /1080p(T) /4k(U) @30fps resolution.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_6_1080p() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    // Test transcoding, fps calculated for encoder and decoder combined so req / 2
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 6 * FPS_30_TOLERANCE / 2)
                    .build();

            return create1080p(RequirementConstants.R5_1__H_1_6, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-6] Support 6 instances of hardware video decoder and hardware video
         * encoder sessions (AVC, HEVC, VP9 or AV1) in any codec combination running concurrently
         * at 720p(R,S) /1080p(T) /4k(U) @30fps resolution.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_6_4k() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    // Test transcoding, fps calculated for encoder and decoder combined so req / 2
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                            6 * FPS_30_TOLERANCE / 2)
                    .build();

            return create4k(RequirementConstants.R5_1__H_1_6, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-9] Support 2 instances of secure hardware video decoder sessions
         * (AVC, HEVC, VP9 or AV1) in any codec combination running concurrently at 1080p
         * resolution@30fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_9_1080p() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 2 * FPS_30_TOLERANCE)
                    .build();

            return create1080p(RequirementConstants.R5_1__H_1_9, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-9] Support 2 instances of secure hardware video decoder sessions
         * (AVC, HEVC, VP9, AV1 or later) in any codec combination running concurrently at 4k
         * resolution@30 fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_9_4k() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 2 * FPS_30_TOLERANCE)
                    .build();

            return create4k(RequirementConstants.R5_1__H_1_9, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-10] Support 3 instances of non-secure hardware video decoder sessions
         * together with 1 instance of secure hardware video decoder session (4 instances total)
         * (AVC, HEVC, VP9 or AV1) in any codec combination running concurrently at 1080p
         * resolution@30fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_10_1080p() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 4 * FPS_30_TOLERANCE)
                    .build();

            return create1080p(RequirementConstants.R5_1__H_1_10, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-10] Support 3 instances of non-secure hardware video decoder sessions
         * together with 1 instance of secure hardware video decoder session (4 instances total)
         * (AVC, HEVC, VP9, AV1 or later) in any codec combination running concurrently with 3
         * sessions at 4K resolution@30 fps which includes one secure decoder session and 1
         * session at 1080p resolution@30fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_10_4k() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 4 * FPS_30_TOLERANCE)
                    .build();

            return create4k(RequirementConstants.R5_1__H_1_10, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-19] Support 3 instances of hardware video decoder and hardware video
         * encoder sessions (AVC, HEVC, VP9 or AV1) in any codec combination running concurrently
         * at 4k(U) @30fps resolution for 10-bit with at most one encoder session.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_19() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    // Test transcoding, fps calculated for encoder and decoder combined so req / 2
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                            3 * FPS_30_TOLERANCE / 2)
                    .build();

            return create4k(RequirementConstants.R5_1__H_1_19, reqConcurrentFps);
        }
    }

    public static class ConcurrentRearFrontRequirement extends Requirement {
        private static final String TAG = ConcurrentRearFrontRequirement.class.getSimpleName();

        private ConcurrentRearFrontRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setConcurrentRearFrontSupported(boolean concurrentRearFrontSupported) {
            this.setMeasuredValue(RequirementConstants.CONCURRENT_REAR_FRONT_SUPPORTED,
                    concurrentRearFrontSupported);
        }

        /**
         * [2.2.7.2/7.5/H-1-11] MUST implement concurrent front-back streaming on primary cameras.
         */
        public static ConcurrentRearFrontRequirement createConcurrentRearFrontReq() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.CONCURRENT_REAR_FRONT_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();

            return new ConcurrentRearFrontRequirement(RequirementConstants.R7_5__H_1_11,
                    requirement);
        }
    }

    // used for requirements [7.1.1.3/H-1-1], [7.1.1.3/H-2-1]
    public static class DensityRequirement extends Requirement {
        private static final String TAG = DensityRequirement.class.getSimpleName();

        private DensityRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setDisplayDensity(int displayDensity) {
            this.<Integer>setMeasuredValue(RequirementConstants.DISPLAY_DENSITY, displayDensity);
        }

        /**
         * [7.1.1.3/H-1-1] MUST have screen density of at least 400 dpi.
         */
        public static DensityRequirement createR7_1_1_3__H_1_1() {
            RequiredMeasurement<Integer> display_density = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.DISPLAY_DENSITY)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 400)
                    .build();

            return new DensityRequirement(RequirementConstants.R7_1_1_3__H_1_1, display_density);
        }

        /**
         * [7.1.1.3/H-2-1] MUST have screen density of at least 400 dpi.
         */
        public static DensityRequirement createR7_1_1_3__H_2_1() {
            RequiredMeasurement<Integer> display_density = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.DISPLAY_DENSITY)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.S, 400)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 400)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 400)
                    .build();

            return new DensityRequirement(RequirementConstants.R7_1_1_3__H_2_1, display_density);
        }
    }

    public static class DynamicRangeTenBitsRequirement extends Requirement {
        private static final String TAG =
                DynamicRangeTenBitsRequirement.class.getSimpleName();

        public static int PRIMARY_REAR_CAMERA = 0;
        public static int PRIMARY_FRONT_CAMERA = 1;

        private DynamicRangeTenBitsRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setDynamicRangeTenBitsSupported(int camera, boolean supported) {
            if (camera == PRIMARY_REAR_CAMERA) {
                this.setMeasuredValue(RequirementConstants.REAR_CAMERA_DYNAMIC_TENBITS_SUPPORTED,
                        supported);
            } else if (camera == PRIMARY_FRONT_CAMERA) {
                this.setMeasuredValue(RequirementConstants.FRONT_CAMERA_DYNAMIC_TENBITS_SUPPORTED,
                        supported);
            }
        }

        /**
         * [2.2.7.2/7.5/H-1-16] MUST support DYNAMIC_RANGE_TEN_BIT capability for
         * the primary cameras.
         */
        public static DynamicRangeTenBitsRequirement createDynamicRangeTenBitsReq() {
            RequiredMeasurement<Boolean> rearDynamicRangeTenBitsRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERA_DYNAMIC_TENBITS_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();
            RequiredMeasurement<Boolean> frontDynamicRangeTenBitsRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.FRONT_CAMERA_DYNAMIC_TENBITS_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();
            return new DynamicRangeTenBitsRequirement(RequirementConstants.R7_5__H_1_16,
                    rearDynamicRangeTenBitsRequirement, frontDynamicRangeTenBitsRequirement);
        }
    }

    public static class ExtYuvTargetRequirement extends Requirement {
        private static final String TAG = ExtYuvTargetRequirement.class.getSimpleName();

        private ExtYuvTargetRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setExtYuvTargetSupport(boolean extensionSupported) {
            this.setMeasuredValue(RequirementConstants.EXT_YUV_EXTENSION, extensionSupported);
        }

        /**
         * [5.12/H-1-3] MUST checks for EXT_YUV_target extension support.
         */
        public static ExtYuvTargetRequirement createExtensionReq() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.EXT_YUV_EXTENSION)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();

            return new ExtYuvTargetRequirement(RequirementConstants.R5_12__H_1_3, requirement);
        }
    }

    public static class FaceDetectionRequirement extends Requirement {
        private static final String TAG =
                FaceDetectionRequirement.class.getSimpleName();

        public static int PRIMARY_REAR_CAMERA = 0;
        public static int PRIMARY_FRONT_CAMERA = 1;

        private FaceDetectionRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setFaceDetectionSupported(int camera, boolean supported) {
            if (camera == PRIMARY_REAR_CAMERA) {
                this.setMeasuredValue(RequirementConstants.REAR_CAMERA_FACE_DETECTION_SUPPORTED,
                        supported);
            } else if (camera == PRIMARY_FRONT_CAMERA) {
                this.setMeasuredValue(RequirementConstants.FRONT_CAMERA_FACE_DETECTION_SUPPORTED,
                        supported);
            }
        }

        /**
         * [2.2.7.2/7.5/H-1-17] MUST support face detection capability
         * (STATISTICS_FACE_DETECT_MODE_SIMPLE or STATISTICS_FACE_DETECT_MODE_FULL) for the primary
         * cameras.
         */
        public static FaceDetectionRequirement createFaceDetectionReq() {
            RequiredMeasurement<Boolean> rearFaceDetectionRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERA_FACE_DETECTION_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();
            RequiredMeasurement<Boolean> frontFaceDetectionRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.FRONT_CAMERA_FACE_DETECTION_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();
            return new FaceDetectionRequirement(RequirementConstants.R7_5__H_1_17,
                    rearFaceDetectionRequirement, frontFaceDetectionRequirement);
        }
    }

    // used for requirements [8.2/H-1-1], [8.2/H-1-2], [8.2/H-1-3], [8.2/H-1-4]
    public static class FileSystemRequirement extends Requirement {

        private static final String TAG = FileSystemRequirement.class.getSimpleName();

        private FileSystemRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }
        /**
         * Set the Filesystem I/O Rate in MB/s.
         */
        public void setFilesystemIoRate(double filesystemIoRate) {
            this.setMeasuredValue(RequirementConstants.FILESYSTEM_IO_RATE, filesystemIoRate);
        }

        /**
         * [8.2/H-1-1] MUST ensure a sequential write performance of at least 100(R) / 125(S &
         * above) MB/s.
         */
        public static FileSystemRequirement createR8_2__H_1_1() {
            RequiredMeasurement<Double> filesystem_io_rate = RequiredMeasurement
                    .<Double>builder().setId(RequirementConstants.FILESYSTEM_IO_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 100.0)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 125.0)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 150.0)
                    .build();

            return new FileSystemRequirement(RequirementConstants.R8_2__H_1_1, filesystem_io_rate);
        }

        /**
         * [8.2/H-2-1] MUST ensure a sequential write performance of at least 125 MB/s.
         */
        public static FileSystemRequirement createR8_2__H_2_1() {
            RequiredMeasurement<Double> filesystem_io_rate = RequiredMeasurement
                    .<Double>builder().setId(RequirementConstants.FILESYSTEM_IO_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.S, 125.0)
                    .build();

            return new FileSystemRequirement(RequirementConstants.R8_2__H_2_1, filesystem_io_rate);
        }

        /**
         * [8.2/H-1-2] MUST ensure a random write performance of at least 10 MB/s
         */
        public static FileSystemRequirement createR8_2__H_1_2() {
            RequiredMeasurement<Double> filesystem_io_rate = RequiredMeasurement
                    .<Double>builder().setId(RequirementConstants.FILESYSTEM_IO_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 10.0)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 10.0)
                    .build();

            return new FileSystemRequirement(RequirementConstants.R8_2__H_1_2, filesystem_io_rate);
        }

        /**
         * [8.2/H-2-2] MUST ensure a random write performance of at least 10 MB/s.
         */
        public static FileSystemRequirement createR8_2__H_2_2() {
            RequiredMeasurement<Double> filesystem_io_rate = RequiredMeasurement
                    .<Double>builder().setId(RequirementConstants.FILESYSTEM_IO_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.S, 10.0)
                    .build();

            return new FileSystemRequirement(RequirementConstants.R8_2__H_2_2, filesystem_io_rate);
        }

        /**
         * [8.2/H-1-3] MUST ensure a sequential read performance of at least 200(R) / 250(S &
         * above) MB/s.
         */
        public static FileSystemRequirement createR8_2__H_1_3() {
            RequiredMeasurement<Double> filesystem_io_rate = RequiredMeasurement
                    .<Double>builder().setId(RequirementConstants.FILESYSTEM_IO_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 200.0)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 250.0)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 250.0)
                    .build();

            return new FileSystemRequirement(RequirementConstants.R8_2__H_1_3, filesystem_io_rate);
        }

        /**
         * [8.2/H-2-3] MUST ensure a sequential read performance of at least 250 MB/s.
         */
        public static FileSystemRequirement createR8_2__H_2_3() {
            RequiredMeasurement<Double> filesystem_io_rate = RequiredMeasurement
                    .<Double>builder().setId(RequirementConstants.FILESYSTEM_IO_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.S, 250.0)
                    .build();

            return new FileSystemRequirement(RequirementConstants.R8_2__H_2_3, filesystem_io_rate);
        }

        /**
         * [8.2/H-1-4] MUST ensure a random read performance of at least 25(R) / 40(S & above) MB/s.
         */
        public static FileSystemRequirement createR8_2__H_1_4() {
            RequiredMeasurement<Double> filesystem_io_rate = RequiredMeasurement
                    .<Double>builder().setId(RequirementConstants.FILESYSTEM_IO_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 25.0)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 40.0)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 100.0)
                    .build();

            return new FileSystemRequirement(RequirementConstants.R8_2__H_1_4, filesystem_io_rate);
        }

        /**
         * [8.2/H-2-4] MUST ensure a random read performance of at least 40 MB/s.
         */
        public static FileSystemRequirement createR8_2__H_2_4() {
            RequiredMeasurement<Double> filesystem_io_rate = RequiredMeasurement
                    .<Double>builder().setId(RequirementConstants.FILESYSTEM_IO_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.S, 40.0)
                    .build();

            return new FileSystemRequirement(RequirementConstants.R8_2__H_2_4, filesystem_io_rate);
        }
    }

    // used for requirements [2.2.7.1/5.3/H-1-1], [2.2.7.1/5.3/H-1-2]
    public static class FrameDropRequirement extends Requirement {
        private static final String TAG = FrameDropRequirement.class.getSimpleName();

        private FrameDropRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setFramesDropped(int framesDropped) {
            this.setMeasuredValue(RequirementConstants.FRAMES_DROPPED, framesDropped);
        }

        public void setFrameRate(double frameRate) {
            this.setMeasuredValue(RequirementConstants.FRAME_RATE, frameRate);
        }

        public void setTestResolution(int res) {
            this.setMeasuredValue(RequirementConstants.TEST_RESOLUTION, res);
        }

        /**
         * [2.2.7.1/5.3/H-1-1] MUST NOT drop more than 1 frames in 10 seconds (i.e less than 0.333
         * percent frame drop) for a 1080p 30 fps video session under load. Load is defined as a
         * concurrent 1080p to 720p video-only transcoding session using hardware video codecs,
         * as well as a 128 kbps AAC audio playback.
         */
        public static FrameDropRequirement createR5_3__H_1_1_R() {
            RequiredMeasurement<Integer> frameDropped = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.FRAMES_DROPPED)
                    .setPredicate(RequirementConstants.INTEGER_LTE)
                    // MUST NOT drop more than 1 frame in 10 seconds so 3 frames for 30 seconds
                    .addRequiredValue(Build.VERSION_CODES.R, 3)
                    .build();

            RequiredMeasurement<Double> frameRate = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.FRAME_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, 30.0)
                    .build();

            RequiredMeasurement<Integer> testResolution = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.TEST_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, 1080)
                    .build();

            return new FrameDropRequirement(
                    RequirementConstants.R5_3__H_1_1, frameDropped, frameRate, testResolution);
        }

        /**
         * [2.2.7.1/5.3/H-1-2] MUST NOT drop more than 1 frame in 10 seconds during a video
         * resolution change in a 30 fps video session under load. Load is defined as a
         * concurrent 1080p to 720p video-only transcoding session using hardware video codecs,
         * as well as a 128Kbps AAC audio playback.
         */
        public static FrameDropRequirement createR5_3__H_1_2_R() {
            RequiredMeasurement<Integer> frameDropped = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.FRAMES_DROPPED)
                    .setPredicate(RequirementConstants.INTEGER_LTE)
                    // MUST NOT drop more than 1 frame in 10 seconds so 3 frames for 30 seconds
                    .addRequiredValue(Build.VERSION_CODES.R, 3)
                    .build();

            RequiredMeasurement<Double> frameRate = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.FRAME_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, 30.0)
                    .build();

            RequiredMeasurement<Integer> testResolution = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.TEST_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, 1080)
                    .build();

            return new FrameDropRequirement(
                    RequirementConstants.R5_3__H_1_2, frameDropped, frameRate, testResolution);
        }

        /**
         * [2.2.7.1/5.3/H-1-1] MUST NOT drop more than 2(S) / 1(T) frames in 10 seconds for a
         * 1080p 60 fps video session under load. Load is defined as a concurrent 1080p to 720p
         * video-only transcoding session using hardware video codecs, as well as a 128 kbps AAC
         * audio playback.
         */
        public static FrameDropRequirement createR5_3__H_1_1_ST() {
            RequiredMeasurement<Integer> frameDropped = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.FRAMES_DROPPED)
                    .setPredicate(RequirementConstants.INTEGER_LTE)
                    // MUST NOT drop more than 2 frame in 10 seconds so 6 frames for 30 seconds
                    .addRequiredValue(Build.VERSION_CODES.S, 6)
                    // MUST NOT drop more than 1 frame in 10 seconds so 3 frames for 30 seconds
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 3)
                    .build();

            RequiredMeasurement<Double> frameRate = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.FRAME_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 60.0)
                    .build();

            RequiredMeasurement<Integer> testResolution = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.TEST_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1080)
                    .build();

            return new FrameDropRequirement(
                    RequirementConstants.R5_3__H_1_1, frameDropped, frameRate, testResolution);
        }

        /**
         * [2.2.7.1/5.3/H-1-1] MUST NOT drop more than 1 frame in 10 seconds for a
         * 4k 60 fps video session under load. Load is defined as a concurrent 1080p to 720p
         * video-only transcoding session using hardware video codecs, as well as a 128 kbps AAC
         * audio playback.
         */
        public static FrameDropRequirement createR5_3__H_1_1_U() {
            RequiredMeasurement<Integer> frameDropped = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.FRAMES_DROPPED)
                    .setPredicate(RequirementConstants.INTEGER_LTE)
                    // MUST NOT drop more than 1 frame in 10 seconds so 3 frames for 30 seconds
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 3)
                    .build();

            RequiredMeasurement<Double> frameRate = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.FRAME_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 60.0)
                    .build();

            RequiredMeasurement<Integer> testResolution = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.TEST_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 2160)
                    .build();

            return new FrameDropRequirement(
                    RequirementConstants.R5_3__H_1_1, frameDropped, frameRate, testResolution);
        }

        /**
         * [2.2.7.1/5.3/H-1-2] MUST NOT drop more than 2(S) / 1(T) frames in 10 seconds during a
         * video resolution change in a 60 fps video session under load. Load is defined as a
         * concurrent 1080p to 720p video-only transcoding session using hardware video codecs,
         * as well as a 128Kbps AAC audio playback.
         */
        public static FrameDropRequirement createR5_3__H_1_2_ST() {
            RequiredMeasurement<Integer> frameDropped = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.FRAMES_DROPPED)
                    .setPredicate(RequirementConstants.INTEGER_LTE)
                    // MUST NOT drop more than 2 frame in 10 seconds so 6 frames for 30 seconds
                    .addRequiredValue(Build.VERSION_CODES.S, 6)
                    // MUST NOT drop more than 1 frame in 10 seconds so 3 frames for 30 seconds
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 3)
                    .build();

            RequiredMeasurement<Double> frameRate = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.FRAME_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 60.0)
                    .build();

            RequiredMeasurement<Integer> testResolution = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.TEST_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1080)
                    .build();

            return new FrameDropRequirement(
                    RequirementConstants.R5_3__H_1_2, frameDropped, frameRate, testResolution);
        }

        /**
         * [2.2.7.1/5.3/H-1-2] MUST NOT drop more than 1 frames in 10 seconds during a
         * video resolution change in a 4k 60 fps video session under load. Load is defined as a
         * concurrent 1080p to 720p video-only transcoding session using hardware video codecs,
         * as well as a 128Kbps AAC audio playback.
         */
        public static FrameDropRequirement createR5_3__H_1_2_U() {
            RequiredMeasurement<Integer> frameDropped = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.FRAMES_DROPPED)
                    .setPredicate(RequirementConstants.INTEGER_LTE)
                    // MUST NOT drop more than 1 frame in 10 seconds so 3 frames for 30 seconds
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 3)
                    .build();

            RequiredMeasurement<Double> frameRate = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.FRAME_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 60.0)
                    .build();

            RequiredMeasurement<Integer> testResolution = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.TEST_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 2160)
                    .build();

            return new FrameDropRequirement(
                    RequirementConstants.R5_3__H_1_2, frameDropped, frameRate, testResolution);
        }
    }

    public static class LogicalMultiCameraRequirement extends Requirement {
        private static final String TAG =
                LogicalMultiCameraRequirement.class.getSimpleName();

        private LogicalMultiCameraRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setRearLogicalMultiCameraReqMet(boolean reqMet) {
            this.setMeasuredValue(RequirementConstants.REAR_CAMERA_LOGICAL_MULTI_CAMERA_REQ_MET,
                    reqMet);
        }

        /**
         * [2.2.7.2/7.5/H-1-13] MUST support LOGICAL_MULTI_CAMERA capability for the primary
         * rear-facing camera if there are greater than 1 RGB rear-facing cameras.
         */
        public static LogicalMultiCameraRequirement createLogicalMultiCameraReq() {
            RequiredMeasurement<Boolean> rearRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERA_LOGICAL_MULTI_CAMERA_REQ_MET)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();

            return new LogicalMultiCameraRequirement(RequirementConstants.R7_5__H_1_13,
                    rearRequirement);
        }
    }

    // used for requirements [7.6.1/H-1-1], [7.6.1/H-2-1]
    public static class MemoryRequirement extends Requirement {
        private static final String TAG = MemoryRequirement.class.getSimpleName();

        private MemoryRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setPhysicalMemory(long physicalMemory) {
            this.<Long>setMeasuredValue(RequirementConstants.PHYSICAL_MEMORY, physicalMemory);
        }

        /**
         * [7.6.1/H-1-1] MUST have at least 6 GB of physical memory.
         */
        public static MemoryRequirement createR7_6_1__H_1_1() {
            RequiredMeasurement<Long> physical_memory = RequiredMeasurement
                    .<Long>builder()
                    .setId(RequirementConstants.PHYSICAL_MEMORY)
                    .setPredicate(RequirementConstants.LONG_GTE)
                    // Media performance requires 6 GB minimum RAM, but keeping the following to
                    // 5 GB as activityManager.getMemoryInfo() returns around 5.4 GB on a 6 GB
                    // device.
                    .addRequiredValue(Build.VERSION_CODES.R, 5L * 1024L)
                    .build();

            return new MemoryRequirement(RequirementConstants.R7_6_1__H_1_1, physical_memory);
        }

        /**
         * [7.6.1/H-2-1] MUST have at least 6/8 GB of physical memory.
         */
        public static MemoryRequirement createR7_6_1__H_2_1() {
            RequiredMeasurement<Long> physical_memory = RequiredMeasurement
                    .<Long>builder()
                    .setId(RequirementConstants.PHYSICAL_MEMORY)
                    .setPredicate(RequirementConstants.LONG_GTE)
                    // Media performance requires 6/8 GB minimum RAM, but keeping the following to
                    // 5/7 GB as activityManager.getMemoryInfo() returns around 5.4 GB on a 6 GB
                    // device.
                    .addRequiredValue(Build.VERSION_CODES.S, 5L * 1024L)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 7L * 1024L)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 7L * 1024L)
                    .build();

            return new MemoryRequirement(RequirementConstants.R7_6_1__H_2_1, physical_memory);
        }
    }

    public static class PreviewStabilizationRequirement extends Requirement {
        private static final String TAG =
                PreviewStabilizationRequirement.class.getSimpleName();

        private PreviewStabilizationRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setRearPreviewStabilizationSupported(boolean supported) {
            this.setMeasuredValue(RequirementConstants.REAR_CAMERA_PREVIEW_STABILIZATION_SUPPORTED,
                    supported);
        }

        /**
         * [2.2.7.2/7.5/H-1-12] MUST support CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
         * for the primary back camera.
         */
        public static PreviewStabilizationRequirement createPreviewStabilizationReq() {
            RequiredMeasurement<Boolean> rearRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERA_PREVIEW_STABILIZATION_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();

            return new PreviewStabilizationRequirement(RequirementConstants.R7_5__H_1_12,
                    rearRequirement);
        }
    }

    public static class PrimaryCameraRequirement extends Requirement {
        private static final long MIN_BACK_SENSOR_PERF_CLASS_RESOLUTION = 12000000;
        private static final long MIN_FRONT_SENSOR_S_PERF_CLASS_RESOLUTION = 5000000;
        private static final long MIN_FRONT_SENSOR_R_PERF_CLASS_RESOLUTION = 4000000;
        private static final String TAG = PrimaryCameraRequirement.class.getSimpleName();

        private PrimaryCameraRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setPrimaryCameraSupported(boolean hasPrimaryCamera) {
            this.setMeasuredValue(RequirementConstants.PRIMARY_CAMERA_AVAILABLE,
                    hasPrimaryCamera);
        }

        public void setResolution(long resolution) {
            this.setMeasuredValue(RequirementConstants.PRIMARY_CAMERA_RESOLUTION,
                    resolution);
        }

        public void setVideoSizeReqSatisfied(boolean videoSizeReqSatisfied) {
            this.setMeasuredValue(RequirementConstants.PRIMARY_CAMERA_VIDEO_SIZE_REQ_SATISFIED,
                    videoSizeReqSatisfied);
        }

        public void setVideoFps(double videoFps) {
            this.setMeasuredValue(RequirementConstants.PRIMARY_CAMERA_VIDEO_FPS, videoFps);
        }

        /**
         * [2.2.7.2/7.5/H-1-1] MUST have a primary rear facing camera with a resolution of at
         * least 12 megapixels supporting video capture at 4k@30fps
         */
        public static PrimaryCameraRequirement createRearPrimaryCamera() {
            RequiredMeasurement<Boolean> hasPrimaryCamera = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_AVAILABLE)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, true)
                    .addRequiredValue(Build.VERSION_CODES.S, true)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();

            RequiredMeasurement<Long> cameraResolution = RequiredMeasurement
                    .<Long>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_RESOLUTION)
                    .setPredicate(RequirementConstants.LONG_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R, MIN_BACK_SENSOR_PERF_CLASS_RESOLUTION)
                    .addRequiredValue(Build.VERSION_CODES.S, MIN_BACK_SENSOR_PERF_CLASS_RESOLUTION)
                    .addRequiredValue(
                            Build.VERSION_CODES.TIRAMISU, MIN_BACK_SENSOR_PERF_CLASS_RESOLUTION)
                    .addRequiredValue(
                            Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                            MIN_BACK_SENSOR_PERF_CLASS_RESOLUTION)
                    .build();

            RequiredMeasurement<Boolean> videoSizeReqSatisfied = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_VIDEO_SIZE_REQ_SATISFIED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, true)
                    .addRequiredValue(Build.VERSION_CODES.S, true)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();

            RequiredMeasurement<Double> videoFps = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_VIDEO_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 29.9)
                    .addRequiredValue(Build.VERSION_CODES.S, 29.9)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 29.9)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 29.9)
                    .build();

            return new PrimaryCameraRequirement(RequirementConstants.R7_5__H_1_1,
                    hasPrimaryCamera, cameraResolution, videoSizeReqSatisfied,
                    videoFps);
        }

        /**
         * [2.2.7.2/7.5/H-1-2] MUST have a primary front facing camera with a resolution of
         * at least 4 megapixels supporting video capture at 1080p@30fps.
         */
        public static PrimaryCameraRequirement createFrontPrimaryCamera() {
            RequiredMeasurement<Boolean> hasPrimaryCamera = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_AVAILABLE)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, true)
                    .addRequiredValue(Build.VERSION_CODES.S, true)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();

            RequiredMeasurement<Long> cameraResolution = RequiredMeasurement
                    .<Long>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_RESOLUTION)
                    .setPredicate(RequirementConstants.LONG_GTE)
                    .addRequiredValue(
                            Build.VERSION_CODES.R, MIN_FRONT_SENSOR_R_PERF_CLASS_RESOLUTION)
                    .addRequiredValue(
                            Build.VERSION_CODES.S, MIN_FRONT_SENSOR_S_PERF_CLASS_RESOLUTION)
                    .addRequiredValue(
                            Build.VERSION_CODES.TIRAMISU, MIN_FRONT_SENSOR_S_PERF_CLASS_RESOLUTION)
                    .addRequiredValue(
                            Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                            MIN_FRONT_SENSOR_S_PERF_CLASS_RESOLUTION)
                    .build();

            RequiredMeasurement<Boolean> videoSizeReqSatisfied = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_VIDEO_SIZE_REQ_SATISFIED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, true)
                    .addRequiredValue(Build.VERSION_CODES.S, true)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();

            RequiredMeasurement<Double> videoFps = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_VIDEO_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 29.9)
                    .addRequiredValue(Build.VERSION_CODES.S, 29.9)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 29.9)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 29.9)
                    .build();

            return new PrimaryCameraRequirement(RequirementConstants.R7_5__H_1_2,
                    hasPrimaryCamera, cameraResolution, videoSizeReqSatisfied,
                    videoFps);
        }
    }

    // used for requirements [7.1.1.1/H-1-1], [7.1.1.1/H-2-1]
    public static class ResolutionRequirement extends Requirement {
        private static final String TAG = ResolutionRequirement.class.getSimpleName();

        private ResolutionRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setLongResolution(int longResolution) {
            this.<Integer>setMeasuredValue(RequirementConstants.LONG_RESOLUTION, longResolution);
        }

        public void setShortResolution(int shortResolution) {
            this.<Integer>setMeasuredValue(RequirementConstants.SHORT_RESOLUTION, shortResolution);
        }

        /**
         * [7.1.1.1/H-1-1] MUST have screen resolution of at least 1080p.
         */
        public static ResolutionRequirement createR7_1_1_1__H_1_1() {
            RequiredMeasurement<Integer> long_resolution = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.LONG_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 1920)
                    .build();
            RequiredMeasurement<Integer> short_resolution = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.SHORT_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 1080)
                    .build();

            return new ResolutionRequirement(RequirementConstants.R7_1_1_1__H_1_1, long_resolution,
                short_resolution);
        }

        /**
         * [7.1.1.1/H-2-1] MUST have screen resolution of at least 1080p.
         */
        public static ResolutionRequirement createR7_1_1_1__H_2_1() {
            RequiredMeasurement<Integer> long_resolution = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.LONG_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.S, 1920)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1920)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 1920)
                    .build();
            RequiredMeasurement<Integer> short_resolution = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.SHORT_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.S, 1080)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1080)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 1080)
                    .build();

            return new ResolutionRequirement(RequirementConstants.R7_1_1_1__H_2_1, long_resolution,
                short_resolution);
        }
    }

    // used for requirements [2.2.7.1/5.1/H-1-11], [2.2.7.1/5.7/H-1-2]
    public static class SecureCodecRequirement extends Requirement {
        private static final String TAG = SecureCodecRequirement.class.getSimpleName();

        private SecureCodecRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setSecureReqSatisfied(boolean secureReqSatisfied) {
            this.setMeasuredValue(RequirementConstants.SECURE_REQ_SATISFIED, secureReqSatisfied);
        }

        public void setNumCryptoHwSecureAllDec(int numCryptoHwSecureAllDec) {
            this.setMeasuredValue(RequirementConstants.NUM_CRYPTO_HW_SECURE_ALL_SUPPORT,
                    numCryptoHwSecureAllDec);
        }

        /**
         * [2.2.7.1/5.7/H-1-2] MUST support MediaDrm.SECURITY_LEVEL_HW_SECURE_ALL with the below
         * content decryption capabilities.
         */
        public static SecureCodecRequirement createR5_7__H_1_2() {
            RequiredMeasurement<Integer> hw_secure_all = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.NUM_CRYPTO_HW_SECURE_ALL_SUPPORT)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 1)
                    .build();

            return new SecureCodecRequirement(RequirementConstants.R5_7__H_1_2, hw_secure_all);
        }

        /**
         * [2.2.7.1/5.1/H-1-11] Must support secure decoder when a corresponding AVC/VP9/HEVC or AV1
         * hardware decoder is available
         */
        public static SecureCodecRequirement createR5_1__H_1_11() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.SECURE_REQ_SATISFIED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();

            return new SecureCodecRequirement(RequirementConstants.R5_1__H_1_11, requirement);
        }
    }

    public static class StreamUseCaseRequirement extends Requirement {
        private static final String TAG =
                StreamUseCaseRequirement.class.getSimpleName();

        private StreamUseCaseRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setRearStreamUseCaseSupported(boolean supported) {
            this.setMeasuredValue(RequirementConstants.REAR_CAMERA_STREAM_USECASE_SUPPORTED,
                    supported);
        }

        public void setFrontStreamUseCaseSupported(boolean supported) {
            this.setMeasuredValue(RequirementConstants.FRONT_CAMERA_STREAM_USECASE_SUPPORTED,
                    supported);
        }

        /**
         * [2.2.7.2/7.5/H-1-14] MUST support STREAM_USE_CASE capability for both primary
         * front and primary back camera.
         */
        public static StreamUseCaseRequirement createStreamUseCaseReq() {
            RequiredMeasurement<Boolean> rearRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERA_STREAM_USECASE_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();
            RequiredMeasurement<Boolean> frontRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.FRONT_CAMERA_STREAM_USECASE_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();

            return new StreamUseCaseRequirement(RequirementConstants.R7_5__H_1_14,
                    rearRequirement, frontRequirement);
        }
    }

    public static class VideoCodecRequirement extends Requirement {
        private static final String TAG = VideoCodecRequirement.class.getSimpleName();

        private VideoCodecRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setAv1DecoderReq(boolean av1DecoderReqSatisfied) {
            this.setMeasuredValue(RequirementConstants.AV1_DEC_REQ, av1DecoderReqSatisfied);
        }

        public void set4kHwDecoders(int num4kHwDecoders) {
            this.setMeasuredValue(RequirementConstants.NUM_4k_HW_DEC, num4kHwDecoders);
        }

        public void set4kHwEncoders(int num4kHwEncoders) {
            this.setMeasuredValue(RequirementConstants.NUM_4k_HW_ENC, num4kHwEncoders);
        }

        public void setAVIFDecoderReq(boolean avifDecoderReqSatisfied) {
            this.setMeasuredValue(RequirementConstants.AVIF_DEC_REQ, avifDecoderReqSatisfied);
        }

        public void setAv1EncResolution(int resolution) {
            this.setMeasuredValue(RequirementConstants.AV1_ENC_RESOLUTION, resolution);
        }

        public void setAv1EncFps(double fps) {
            this.setMeasuredValue(RequirementConstants.AV1_ENC_FPS, fps);
        }

        public void setAv1EncBitrate(int bitrate) {
            this.setMeasuredValue(RequirementConstants.AV1_ENC_BITRATE, bitrate);
        }

        public void setColorFormatSupportReq(boolean colorFormatSupported) {
            this.setMeasuredValue(RequirementConstants.RGBA_1010102_COLOR_FORMAT_REQ,
                    colorFormatSupported);
        }

        /**
         * [2.2.7.1/5.1/H-1-15] Must have at least 1 HW video decoder supporting 4K60
         */
        public static VideoCodecRequirement createR4k60HwDecoder() {
            RequiredMeasurement<Integer> requirement = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.NUM_4k_HW_DEC)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 1)
                    .build();

            return new VideoCodecRequirement(RequirementConstants.R5_1__H_1_15, requirement);
        }

        /**
         * [2.2.7.1/5.1/H-1-16] Must have at least 1 HW video encoder supporting 4K60
         */
        public static VideoCodecRequirement createR4k60HwEncoder() {
            RequiredMeasurement<Integer> requirement = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.NUM_4k_HW_ENC)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 1)
                    .build();

            return new VideoCodecRequirement(RequirementConstants.R5_1__H_1_16, requirement);
        }

        /**
         * [2.2.7.1/5.1/H-1-14] AV1 Hardware decoder: Main 10, Level 4.1, Film Grain
         */
        public static VideoCodecRequirement createRAV1DecoderReq() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.AV1_DEC_REQ)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();

            return new VideoCodecRequirement(RequirementConstants.R5_1__H_1_14, requirement);
        }

        /**
         * [5.1/H-1-17] MUST have at least 1 Hw image decoder supporting AVIF Baseline profile.
         */
        public static VideoCodecRequirement createRAVIFDecoderReq() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.AVIF_DEC_REQ)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();

            return new VideoCodecRequirement(RequirementConstants.R5_1__H_1_17, requirement);
        }

        /**
         * [2.2.7.1/5.1/H-1-18] MUST support AV1 encoder which can encode up to 480p resolution
         * at 30fps and 1Mbps.
         */
        public static VideoCodecRequirement createRAV1EncoderReq() {
            RequiredMeasurement<Integer> resolution = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.AV1_ENC_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 480)
                    .build();

            RequiredMeasurement<Double> fps = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.AV1_ENC_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 30.0)
                    .build();

            RequiredMeasurement<Integer> bitrate = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.AV1_ENC_BITRATE)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 1)
                    .build();

            return new VideoCodecRequirement(RequirementConstants.R5_1__H_1_18, resolution, fps,
                    bitrate);
        }

        /**
         * [5.12/H-1-2] MUST support RGBA_1010102 color format for all hardware AV1 and HEVC
         * encoders present on the device.
         */
        public static VideoCodecRequirement createColorFormatSupportReq() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.RGBA_1010102_COLOR_FORMAT_REQ)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();

            return new VideoCodecRequirement(RequirementConstants.R5_12__H_1_2, requirement);
        }
    }

    public static class UltraWideZoomRatioRequirement extends Requirement {
        private static final String TAG =
                UltraWideZoomRatioRequirement.class.getSimpleName();

        private UltraWideZoomRatioRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setRearUltraWideZoomRatioReqMet(boolean ultrawideZoomRatioReqMet) {
            this.setMeasuredValue(RequirementConstants.REAR_CAMERA_ULTRAWIDE_ZOOMRATIO_REQ_MET,
                    ultrawideZoomRatioReqMet);
        }

        public void setFrontUltraWideZoomRatioReqMet(boolean ultrawideZoomRatioReqMet) {
            this.setMeasuredValue(RequirementConstants.FRONT_CAMERA_ULTRAWIDE_ZOOMRATIO_REQ_MET,
                    ultrawideZoomRatioReqMet);
        }

        /**
         * [2.2.7.2/7.5/H-1-10] MUST have min ZOOM_RATIO < 1.0 for the primary cameras if
         * there is an ultrawide RGB camera facing the same direction.
         */
        public static UltraWideZoomRatioRequirement createUltrawideZoomRatioReq() {
            RequiredMeasurement<Boolean> rearRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERA_ULTRAWIDE_ZOOMRATIO_REQ_MET)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();
            RequiredMeasurement<Boolean> frontRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.FRONT_CAMERA_ULTRAWIDE_ZOOMRATIO_REQ_MET)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();

            return new UltraWideZoomRatioRequirement(RequirementConstants.R7_5__H_1_10,
                    rearRequirement, frontRequirement);
        }
    }

    public <R extends Requirement> R addRequirement(R req) {
        if (!this.mRequirements.add(req)) {
            throw new IllegalStateException("Requirement " + req.id() + " already added");
        }
        return req;
    }

    public ConcurrentCodecRequirement addR5_1__H_1_1_720p(String mimeType1, String mimeType2,
        int resolution) {
        return this.addRequirement(
            ConcurrentCodecRequirement.createR5_1__H_1_1_720p(mimeType1, mimeType2, resolution));
    }

    public ConcurrentCodecRequirement addR5_1__H_1_1_1080p() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_1_1080p());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_1_4k() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_1_4k());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_2_720p(String mimeType1, String mimeType2,
        int resolution) {
        return this.addRequirement(
            ConcurrentCodecRequirement.createR5_1__H_1_2_720p(mimeType1, mimeType2, resolution));
    }

    public ConcurrentCodecRequirement addR5_1__H_1_2_1080p() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_2_1080p());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_2_4k() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_2_4k());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_3_720p(String mimeType1, String mimeType2,
        int resolution) {
        return this.addRequirement(
            ConcurrentCodecRequirement.createR5_1__H_1_3_720p(mimeType1, mimeType2, resolution));
    }

    public ConcurrentCodecRequirement addR5_1__H_1_3_1080p() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_3_1080p());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_3_4k() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_3_4K());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_4_720p() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_4_720p());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_4_1080p() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_4_1080p());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_4_4k() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_4_4k());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_5_720p(String mimeType1, String mimeType2,
        int resolution) {
        return this.addRequirement(
            ConcurrentCodecRequirement.createR5_1__H_1_5_720p(mimeType1, mimeType2, resolution));
    }

    public ConcurrentCodecRequirement addR5_1__H_1_5_1080p() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_5_1080p());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_5_4k() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_5_4k());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_6_720p(String mimeType1, String mimeType2,
        int resolution) {
        return this.addRequirement(
            ConcurrentCodecRequirement.createR5_1__H_1_6_720p(mimeType1, mimeType2, resolution));
    }

    public ConcurrentCodecRequirement addR5_1__H_1_6_1080p() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_6_1080p());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_6_4k() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_6_4k());
    }

    public CodecInitLatencyRequirement addR5_1__H_1_7(String mediaType) {
        return this.addRequirement(CodecInitLatencyRequirement.createR5_1__H_1_7(mediaType));
    }

    public CodecInitLatencyRequirement addR5_1__H_1_8() {
        return this.addRequirement(CodecInitLatencyRequirement.createR5_1__H_1_8());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_9_1080p() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_9_1080p());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_9_4k() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_9_4k());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_10_1080p() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_10_1080p());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_10_4k() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_10_4k());
    }
    public SecureCodecRequirement addR5_1__H_1_11() {
        return this.addRequirement(SecureCodecRequirement.createR5_1__H_1_11());
    }

    public CodecInitLatencyRequirement addR5_1__H_1_12() {
        return this.addRequirement(CodecInitLatencyRequirement.createR5_1__H_1_12());
    }

    public CodecInitLatencyRequirement addR5_1__H_1_13() {
        return this.addRequirement(CodecInitLatencyRequirement.createR5_1__H_1_13());
    }

    /* Adds requirement 5.1/H-1-14 */
    public VideoCodecRequirement addRAV1DecoderReq() {
        return this.addRequirement(VideoCodecRequirement.createRAV1DecoderReq());
    }

    /* Adds requirement 5.1/H-1-15 */
    public VideoCodecRequirement addR4k60HwDecoder() {
        return this.addRequirement(VideoCodecRequirement.createR4k60HwDecoder());
    }

    /* Adds requirement 5.1/H-1-16 */
    public VideoCodecRequirement addR4k60HwEncoder() {
        return this.addRequirement(VideoCodecRequirement.createR4k60HwEncoder());
    }

    /* Adds requirement 5.1/H-1-17 */
    public VideoCodecRequirement addRAVIFDecoderReq() {
        return this.addRequirement(VideoCodecRequirement.createRAVIFDecoderReq());
    }

    /* Adds requirement 5.1/H-1-18 */
    public VideoCodecRequirement addRAV1EncoderReq() {
        return this.addRequirement(VideoCodecRequirement.createRAV1EncoderReq());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_19() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_19());
    }


    public FrameDropRequirement addR5_3__H_1_1_R() {
        return this.addRequirement(FrameDropRequirement.createR5_3__H_1_1_R());
    }

    public FrameDropRequirement addR5_3__H_1_1_ST() {
        return this.addRequirement(FrameDropRequirement.createR5_3__H_1_1_ST());
    }

    public FrameDropRequirement addR5_3__H_1_1_U() {
        return this.addRequirement(FrameDropRequirement.createR5_3__H_1_1_U());
    }

    public FrameDropRequirement addR5_3__H_1_2_R() {
        return this.addRequirement(FrameDropRequirement.createR5_3__H_1_2_R());
    }

    public FrameDropRequirement addR5_3__H_1_2_ST() {
        return this.addRequirement(FrameDropRequirement.createR5_3__H_1_2_ST());
    }

    public FrameDropRequirement addR5_3__H_1_2_U() {
        return this.addRequirement(FrameDropRequirement.createR5_3__H_1_2_U());
    }


    public AudioTap2ToneLatencyRequirement addR5_6__H_1_1() {
        return this.addRequirement(AudioTap2ToneLatencyRequirement.createR5_6__H_1_1());
    }


    public SecureCodecRequirement addR5_7__H_1_2() {
        return this.addRequirement(SecureCodecRequirement.createR5_7__H_1_2());
    }


    /* Adds requirement 5.12/H-1-2 */
    public VideoCodecRequirement addColorFormatSupportReq() {
        return this.addRequirement(VideoCodecRequirement.createColorFormatSupportReq());
    }

    /* Adds requirement 5.12/H-1-3 */
    public ExtYuvTargetRequirement addExtYUVSupportReq() {
        return this.addRequirement(ExtYuvTargetRequirement.createExtensionReq());
    }


    /* Adds requirement 7.5/H-1-1 */
    public PrimaryCameraRequirement addPrimaryRearCameraReq() {
        return this.addRequirement(PrimaryCameraRequirement.createRearPrimaryCamera());
    }

    /* Adds requirement 7.5/H-1-2 */
    public PrimaryCameraRequirement addPrimaryFrontCameraReq() {
        return this.addRequirement(PrimaryCameraRequirement.createFrontPrimaryCamera());
    }

    public CameraTimestampSourceRequirement addR7_5__H_1_4() {
        return this.addRequirement(CameraTimestampSourceRequirement.createTimestampSourceReq());
    }

    public CameraLatencyRequirement addR7_5__H_1_5() {
        return this.addRequirement(CameraLatencyRequirement.createJpegLatencyReq());
    }

    public CameraLatencyRequirement addR7_5__H_1_6() {
        return this.addRequirement(CameraLatencyRequirement.createLaunchLatencyReq());
    }

    public CameraRawRequirement addR7_5__H_1_8() {
        return this.addRequirement(CameraRawRequirement.createRawReq());
    }

    public Camera240FpsRequirement addR7_5__H_1_9() {
        return this.addRequirement(Camera240FpsRequirement.create240FpsReq());
    }

    public UltraWideZoomRatioRequirement addR7_5__H_1_10() {
        return this.addRequirement(UltraWideZoomRatioRequirement.createUltrawideZoomRatioReq());
    }

    public ConcurrentRearFrontRequirement addR7_5__H_1_11() {
        return this.addRequirement(ConcurrentRearFrontRequirement.createConcurrentRearFrontReq());
    }

    public PreviewStabilizationRequirement addR7_5__H_1_12() {
        return this.addRequirement(PreviewStabilizationRequirement.createPreviewStabilizationReq());
    }

    public LogicalMultiCameraRequirement addR7_5__H_1_13() {
        return this.addRequirement(LogicalMultiCameraRequirement.createLogicalMultiCameraReq());
    }

    public StreamUseCaseRequirement addR7_5__H_1_14() {
        return this.addRequirement(StreamUseCaseRequirement.createStreamUseCaseReq());
    }

    public CameraExtensionRequirement addR7_5__H_1_15() {
        return this.addRequirement(CameraExtensionRequirement.createCameraExtensionReq());
    }

    public DynamicRangeTenBitsRequirement addR7_5__H_1_16() {
        return this.addRequirement(DynamicRangeTenBitsRequirement.createDynamicRangeTenBitsReq());
    }

    public FaceDetectionRequirement addR7_5__H_1_17() {
        return this.addRequirement(FaceDetectionRequirement.createFaceDetectionReq());
    }


    public ResolutionRequirement addR7_1_1_1__H_1_1() {
        return this.<ResolutionRequirement>addRequirement(
            ResolutionRequirement.createR7_1_1_1__H_1_1());
    }

    public DensityRequirement addR7_1_1_3__H_1_1() {
        return this.<DensityRequirement>addRequirement(DensityRequirement.createR7_1_1_3__H_1_1());
    }

    public MemoryRequirement addR7_6_1__H_1_1() {
        return this.<MemoryRequirement>addRequirement(MemoryRequirement.createR7_6_1__H_1_1());
    }

    public ResolutionRequirement addR7_1_1_1__H_2_1() {
        return this.<ResolutionRequirement>addRequirement(
            ResolutionRequirement.createR7_1_1_1__H_2_1());
    }

    public DensityRequirement addR7_1_1_3__H_2_1() {
        return this.<DensityRequirement>addRequirement(DensityRequirement.createR7_1_1_3__H_2_1());
    }

    public MemoryRequirement addR7_6_1__H_2_1() {
        return this.<MemoryRequirement>addRequirement(MemoryRequirement.createR7_6_1__H_2_1());
    }


    public FileSystemRequirement addR8_2__H_1_1() {
        return this.addRequirement(FileSystemRequirement.createR8_2__H_1_1());
    }

    public FileSystemRequirement addR8_2__H_2_1() {
        return this.addRequirement(FileSystemRequirement.createR8_2__H_2_1());
    }

    public FileSystemRequirement addR8_2__H_1_2() {
        return this.addRequirement(FileSystemRequirement.createR8_2__H_1_2());
    }

    public FileSystemRequirement addR8_2__H_2_2() {
        return this.addRequirement(FileSystemRequirement.createR8_2__H_2_2());
    }

    public FileSystemRequirement addR8_2__H_1_3() {
        return this.addRequirement(FileSystemRequirement.createR8_2__H_1_3());
    }

    public FileSystemRequirement addR8_2__H_2_3() {
        return this.addRequirement(FileSystemRequirement.createR8_2__H_2_3());
    }

    public FileSystemRequirement addR8_2__H_1_4() {
        return this.addRequirement(FileSystemRequirement.createR8_2__H_1_4());
    }

    public FileSystemRequirement addR8_2__H_2_4() {
        return this.addRequirement(FileSystemRequirement.createR8_2__H_2_4());
    }


    private enum SubmitType {
        TRADEFED, VERIFIER
    }

    public void submitAndCheck() {
        boolean perfClassMet = submit(SubmitType.TRADEFED);

        // check performance class
        assumeTrue("Build.VERSION.MEDIA_PERFORMANCE_CLASS is not declared", Utils.isPerfClass());
        assertThat(perfClassMet).isTrue();
    }

    public void submitAndVerify() {
        boolean perfClassMet = submit(SubmitType.VERIFIER);

        if (!perfClassMet && Utils.isPerfClass()) {
            Log.w(TAG, "Device did not meet specified performance class: " + Utils.getPerfClass());
        }
    }

    private boolean submit(SubmitType type) {
        boolean perfClassMet = true;
        for (Requirement req: this.mRequirements) {
            switch (type) {
                case VERIFIER:
                    CtsVerifierReportLog verifierLog = new CtsVerifierReportLog(
                            RequirementConstants.REPORT_LOG_NAME, req.id());
                    perfClassMet &= req.writeLogAndCheck(verifierLog, this.mTestName);
                    verifierLog.submit();
                    break;

                case TRADEFED:
                default:
                    DeviceReportLog tradefedLog = new DeviceReportLog(
                            RequirementConstants.REPORT_LOG_NAME, req.id());
                    perfClassMet &= req.writeLogAndCheck(tradefedLog, this.mTestName);
                    tradefedLog.submit(InstrumentationRegistry.getInstrumentation());
                    break;
            }
        }
        this.mRequirements.clear(); // makes sure report isn't submitted twice
        return perfClassMet;
    }
}
