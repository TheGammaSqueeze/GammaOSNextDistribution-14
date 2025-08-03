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

import java.util.function.BiPredicate;

/**
 * Stores constants used by PerformanceClassEvaluator.  Constants relating to requirement number are
 * stored in order as they appear in the Android CDD.  Constants relating to measurements are stored
 * alphabetically.
 */
public class RequirementConstants {
    private static final String TAG = RequirementConstants.class.getSimpleName();

    public static final String REPORT_LOG_NAME = "CtsMediaPerformanceClassTestCases";
    public static final String TN_FIELD_NAME = "test_name";
    public static final String PC_FIELD_NAME = "performance_class";

    public static final String R5_1__H_1_1 = "r5_1__h_1_1"; // 5.1/H-1-1
    public static final String R5_1__H_1_2 = "r5_1__h_1_2"; // 5.1/H-1-2
    public static final String R5_1__H_1_3 = "r5_1__h_1_3"; // 5.1/H-1-3
    public static final String R5_1__H_1_4 = "r5_1__h_1_4"; // 5.1/H-1-4
    public static final String R5_1__H_1_5 = "r5_1__h_1_5"; // 5.1/H-1-5
    public static final String R5_1__H_1_6 = "r5_1__h_1_6"; // 5.1/H-1-6
    public static final String R5_1__H_1_7 = "r5_1__h_1_7"; // 5.1/H-1-7
    public static final String R5_1__H_1_8 = "r5_1__h_1_8"; // 5.1/H-1-8
    public static final String R5_1__H_1_9 = "r5_1__h_1_9"; // 5.1/H-1-9
    public static final String R5_1__H_1_10 = "r5_1__h_1_10"; // 5.1/H-1-10
    public static final String R5_1__H_1_11 = "r5_1__h_1_11"; // 5.1/H-1-11
    public static final String R5_1__H_1_12 = "r5_1__h_1_12"; // 5.1/H-1-12
    public static final String R5_1__H_1_13 = "r5_1__h_1_13"; // 5.1/H-1-13
    public static final String R5_1__H_1_14 = "r5_1__h_1_14"; // 5.1/H-1-14
    public static final String R5_1__H_1_15 = "r5_1__h_1_15"; // 5.1/H-1-16
    public static final String R5_1__H_1_16 = "r5_1__h_1_16"; // 5.1/H-1-16
    public static final String R5_1__H_1_17 = "r5_1__h_1_17"; // 5.1/H-1-17
    public static final String R5_1__H_1_18 = "r5_1__h_1_18"; // 5.1/H-1-18
    public static final String R5_1__H_1_19 = "r5_1__h_1_19"; // 5.1/H-1-19

    public static final String R5_3__H_1_1 = "r5_3__h_1_1"; // 5.3/H-1-1
    public static final String R5_3__H_1_2 = "r5_3__h_1_2"; // 5.3/H-1-2

    public static final String R5_6__H_1_1 = "r5_6__h_1_1"; // 5.6/H-1-1

    public static final String R5_7__H_1_1 = "r5_7__h_1_1"; // 5.7/H-1-1
    public static final String R5_7__H_1_2 = "r5_7__h_1_2"; // 5.7/H-1-2

    public static final String R5_12__H_1_2 = "r5_12__h_1_2"; // 5.12/H-1-2
    public static final String R5_12__H_1_3 = "r5_12__h_1_3"; // 5.12/H-1-3

    public static final String R7_5__H_1_1 = "r7_5__h_1_1"; // 7.5/H-1-1
    public static final String R7_5__H_1_2 = "r7_5__h_1_2"; // 7.5/H-1-2
    public static final String R7_5__H_1_3 = "r7_5__h_1_3"; // 7.5/H-1-3
    public static final String R7_5__H_1_4 = "r7_5__h_1_4"; // 7.5/H-1-4

    // these includes "its" because the proto in google3 was originally implemented incorrectly
    public static final String R7_5__H_1_5 = "r7_5__h_1_5__its"; // 7.5/H-1-5
    public static final String R7_5__H_1_6 = "r7_5__h_1_6__its"; // 7.5/H-1-6

    public static final String R7_5__H_1_8 = "r7_5__h_1_8"; // 7.5/H-1-8
    public static final String R7_5__H_1_9 = "r7_5__h_1_9"; // 7.5/H-1-9
    public static final String R7_5__H_1_10 = "r7_5__h_1_10"; // 7.5/H-1-10
    public static final String R7_5__H_1_11 = "r7_5__h_1_11"; // 7.5/H-1-11
    public static final String R7_5__H_1_12 = "r7_5__h_1_12"; // 7.5/H-1-12
    public static final String R7_5__H_1_13 = "r7_5__h_1_13"; // 7.5/H-1-13
    public static final String R7_5__H_1_14 = "r7_5__h_1_14"; // 7.5/H-1-14
    public static final String R7_5__H_1_15 = "r7_5__h_1_15"; // 7.5/H-1-15
    public static final String R7_5__H_1_16 = "r7_5__h_1_16"; // 7.5/H-1-16
    public static final String R7_5__H_1_17 = "r7_5__h_1_17"; // 7.5/H-1-17

    public static final String R7_1_1_1__H_1_1 = "r7_1_1_1__h_1_1"; // 7.1.1.1/H-1-1
    public static final String R7_1_1_3__H_1_1 = "r7_1_1_3__h_1_1"; // 7.1.1.3/H-1-1
    public static final String R7_6_1__H_1_1 = "r7_6_1__h_1_1"; // 7.6.1/H-1-1
    public static final String R7_1_1_1__H_2_1 = "r7_1_1_1__h_2_1"; // 7.1.1.1/H-2-1
    public static final String R7_1_1_3__H_2_1 = "r7_1_1_3__h_2_1"; // 7.1.1.3/H-2-1
    public static final String R7_6_1__H_2_1 = "r7_6_1__h_2_1"; // 7.6.1/H-2-1

    public static final String R8_2__H_1_1 = "r8_2__h_1_1"; // 8.2/H-1-1
    public static final String R8_2__H_1_2 = "r8_2__h_1_2"; // 8.2/H-1-2
    public static final String R8_2__H_1_3 = "r8_2__h_1_3"; // 8.2/H-1-3
    public static final String R8_2__H_1_4 = "r8_2__h_1_4"; // 8.2/H-1-4
    public static final String R8_2__H_2_1 = "r8_2__h_2_1"; // 8.2/H-2-1
    public static final String R8_2__H_2_2 = "r8_2__h_2_2"; // 8.2/H-2-2
    public static final String R8_2__H_2_3 = "r8_2__h_2_3"; // 8.2/H-2-3
    public static final String R8_2__H_2_4 = "r8_2__h_2_4"; // 8.2/H-2-4

    public static final String R7_6_1__H_3_1 = "r7_6_1__h_3_1"; // 7.6.1/H-3-1, currently unused
    public static final String RTBD = "tbd"; // placeholder for requirements without a set id

    public static final String API_JAVA_LATENCY = "java_latency_ms";
    public static final String API_NATIVE_LATENCY = "native_latency_ms";
    public static final String AV1_DEC_REQ = "av1_decoder_requirement_boolean";
    public static final String AV1_ENC_BITRATE = "av1_encoder_bitrate";
    public static final String AV1_ENC_FPS = "av1_encoder_fps";
    public static final String AV1_ENC_RESOLUTION = "av1_encoder_resolution";
    public static final String AVIF_DEC_REQ = "avif_image_decoder_boolean";

    public static final String CODEC_INIT_LATENCY = "codec_initialization_latency_ms";
    public static final String CONCURRENT_FPS = "concurrent_fps";
    public static final String CONCURRENT_REAR_FRONT_SUPPORTED = "rear_front_concurrent_camera";
    public static final String CONCURRENT_SESSIONS = "concurrent_sessions";

    public static final String DISPLAY_DENSITY = "display_density_dpi";

    public static final String EXT_YUV_EXTENSION = "ext_yuv_target_supported";

    public static final String FILESYSTEM_IO_RATE = "filesystem_io_rate_mbps";
    public static final String FRAME_RATE = "frame_rate";
    public static final String FRAMES_DROPPED = "frame_drops_per_30sec";

    public static final String FRONT_CAMERA_DYNAMIC_TENBITS_SUPPORTED =
            "front_camera_dynamic_tenbits_supported";
    public static final String FRONT_CAMERA_FACE_DETECTION_SUPPORTED =
            "front_camera_face_detection_supported";
    public static final String FRONT_CAMERA_HWL_LEVEL = "front_primary_camera_hwl_level";
    public static final String FRONT_CAMERA_LATENCY = "front_camera_latency";
    public static final String FRONT_CAMERA_LOGICAL_MULTI_CAMERA_REQ_MET =
            "front_camera_logical_multi_camera_req_met";
    public static final String FRONT_CAMERA_STREAM_USECASE_SUPPORTED =
            "front_camera_stream_usecase_supported";
    public static final String FRONT_CAMERA_TIMESTAMP_SOURCE =
            "front_primary_camera_timestamp_source";
    public static final String FRONT_CAMERA_ULTRAWIDE_ZOOMRATIO_REQ_MET =
            "front_camera_ultrawide_zoom_req_met";
    public static final String FRONT_CAMERAX_EXTENSION_NIGHT_SUPPORTED =
            "front_camerax_extension_night_supported";
    public static final String FRONT_CAMERA2_EXTENSION_NIGHT_SUPPORTED =
            "front_camera2_extension_night_supported";

    public static final String LONG_RESOLUTION = "long_resolution_pixels";

    public static final String NUM_4k_HW_DEC = "number_4k_hw_decoders";
    public static final String NUM_4k_HW_ENC = "number_4k_hw_encoders";
    public static final String NUM_CRYPTO_HW_SECURE_ALL_SUPPORT =
            "number_crypto_hw_secure_all_support";

    public static final String PHYSICAL_MEMORY = "physical_memory_mb";

    public static final String PRIMARY_CAMERA_AVAILABLE = "primary_camera_available";
    public static final String PRIMARY_CAMERA_VIDEO_FPS =
            "primary_camera_video_fps";
    public static final String PRIMARY_CAMERA_RESOLUTION = "primary_camera_resolution";
    public static final String PRIMARY_CAMERA_VIDEO_SIZE_REQ_SATISFIED =
            "primary_camera_video_size_req_satisfied";

    public static final String REAR_CAMERA_240FPS_SUPPORTED = "rear_camera_240fps_supported";
    public static final String REAR_CAMERA_DYNAMIC_TENBITS_SUPPORTED =
            "rear_camera_dynamic_tenbits_supported";
    public static final String REAR_CAMERA_FACE_DETECTION_SUPPORTED =
            "rear_camera_face_detection_supported";
    public static final String REAR_CAMERA_HWL_LEVEL = "rear_primary_camera_hwl_level";
    public static final String REAR_CAMERA_LATENCY = "rear_camera_latency";
    public static final String REAR_CAMERA_LOGICAL_MULTI_CAMERA_REQ_MET =
            "rear_camera_logical_multi_camera_req_met";
    public static final String REAR_CAMERA_PREVIEW_STABILIZATION_SUPPORTED =
            "rear_camera_preview_stabilization_supported";
    public static final String REAR_CAMERA_RAW_SUPPORTED = "rear_camera_raw_supported";
    public static final String REAR_CAMERA_STREAM_USECASE_SUPPORTED =
            "rear_camera_stream_usecase_supported";
    public static final String REAR_CAMERA_TIMESTAMP_SOURCE =
            "rear_primary_camera_timestamp_source";
    public static final String REAR_CAMERA_ULTRAWIDE_ZOOMRATIO_REQ_MET =
            "rear_camera_ultrawide_zoom_req_met";
    public static final String REAR_CAMERAX_EXTENSION_NIGHT_SUPPORTED =
            "rear_camerax_extension_night_supported";
    public static final String REAR_CAMERA2_EXTENSION_NIGHT_SUPPORTED =
            "rear_camera2_extension_night_supported";

    public static final String RGBA_1010102_COLOR_FORMAT_REQ = "rgba_1010102_color_format";

    public static final String SHORT_RESOLUTION = "short_resolution_pixels";
    public static final String SECURE_REQ_SATISFIED = "secure_requirement_satisfied_boolean";
    public static final String SUPPORTED_PERFORMANCE_POINTS = "supported_performance_points";

    public static final String TEST_RESOLUTION = "resolution";

    public enum Result {
        NA, MET, UNMET
    }

    public static final BiPredicate<Long, Long> LONG_GTE = RequirementConstants.gte();
    public static final BiPredicate<Long, Long> LONG_LTE = RequirementConstants.lte();

    public static final BiPredicate<Integer, Integer> INTEGER_GTE = RequirementConstants.gte();
    public static final BiPredicate<Integer, Integer> INTEGER_LTE = RequirementConstants.lte();
    public static final BiPredicate<Integer, Integer> INTEGER_EQ = RequirementConstants.eq();

    public static final BiPredicate<Double, Double> DOUBLE_GTE = RequirementConstants.gte();
    public static final BiPredicate<Double, Double> DOUBLE_LTE = RequirementConstants.lte();
    public static final BiPredicate<Double, Double> DOUBLE_EQ = RequirementConstants.eq();

    public static final BiPredicate<Float, Float> FLOAT_LTE = RequirementConstants.lte();

    public static final BiPredicate<Boolean, Boolean> BOOLEAN_EQ = RequirementConstants.eq();

    /**
     * Creates a >= predicate.
     *
     * This is convenience method to get the types right.
     */
    private static <T, S extends Comparable<T>> BiPredicate<S, T> gte() {
        return new BiPredicate<S, T>() {
            @Override
            public boolean test(S actual, T expected) {
                return actual.compareTo(expected) >= 0;
            }

            @Override
            public String toString() {
                return "Greater than or equal to";
            }
        };
    }

    /**
     * Creates a <= predicate.
     */
    private static <T, S extends Comparable<T>> BiPredicate<S, T> lte() {
        return new BiPredicate<S, T>() {
            @Override
            public boolean test(S actual, T expected) {
                return actual.compareTo(expected) <= 0;
            }

            @Override
            public String toString() {
                return "Less than or equal to";
            }
        };
    }

    /**
     * Creates an == predicate.
     */
    private static <T, S extends Comparable<T>> BiPredicate<S, T> eq() {
        return new BiPredicate<S, T>() {
            @Override
            public boolean test(S actual, T expected) {
                return actual.compareTo(expected) == 0;
            }

            @Override
            public String toString() {
                return "Equal to";
            }
        };
    }

    private RequirementConstants() {} // class should not be instantiated
}
