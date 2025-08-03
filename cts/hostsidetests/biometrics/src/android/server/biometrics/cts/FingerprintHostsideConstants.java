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

package android.server.biometrics.cts;

import java.util.List;

/**
 * Shared constants for the on-device and host-side test apps.
 *
 * TODO(b/253318030): Find a better way to make these visible to the host.
 */
public class FingerprintHostsideConstants {

    /** Acquired messages sent during the enrollment test. */
    public static final List<Integer> FINGERPRINT_ENROLL_ACQUIRED_MESSAGES = List.of(
            8 /* AcquiredInfo.START */,
            4 /* AcquiredInfo.SENSOR_DIRTY */,
            2 /* AcquiredInfo.PARTIAL */,
            5 /* AcquiredInfo.TOO_SLOW */,
            1 /* AcquiredInfo.GOOD */);

    /** {@see com.android.server.biometrics.sensors.fingerprint.aidl.AidlConversionUtils#toFrameworkAcquiredInfo}. */
    public static final List<Integer> FINGERPRINT_ENROLL_ACQUIRED_MESSAGES_AIDL = List.of(
            7 /* BiometricFingerprintConstants.FINGERPRINT_ACQUIRED_START */,
            3 /* BiometricFingerprintConstants.FINGERPRINT_ACQUIRED_IMAGER_DIRTY */,
            1 /* BiometricFingerprintConstants.FINGERPRINT_ACQUIRED_PARTIAL */,
            4 /* BiometricFingerprintConstants.FINGERPRINT_ACQUIRED_TOO_SLOW */,
            0 /* BiometricFingerprintConstants.FINGERPRINT_ACQUIRED_GOOD */
    );

    /** Acquired messages sent during the enrollment test. */
    public static final List<Integer> FACE_ENROLL_ACQUIRED_MESSAGES = List.of(
            21 /* AcquiredInfo.START */,
            22 /* AcquiredInfo.SENSOR_DIRTY */,
            20 /* AcquiredInfo.FACE_OBSCURED */,
            10 /* AcquiredInfo.FACE_TOO_LEFT */,
            1 /* AcquiredInfo.GOOD */
    );

    /** {@see com.android.server.biometrics.sensors.face.aidl.AidlConversionUtils#toFrameworkAcquiredInfo}. */
    public static final List<Integer> FACE_ENROLL_ACQUIRED_MESSAGES_AIDL = List.of(
            20 /* BiometricFaceConstants.FACE_ACQUIRED_START */,
            21 /* BiometricFaceConstants.FACE_ACQUIRED_SENSOR_DIRTY */,
            19 /* BiometricFaceConstants.FACE_ACQUIRED_FACE_OBSCURED */,
            9 /* BiometricFaceConstants.FACE_ACQUIRED_TOO_LEFT */,
            0 /* BiometricFaceConstants.FACE_ACQUIRED_GOOD */
    );

    /** Acquired messages sent during the authentication test. */
    public static final List<Integer> FINGERPRINT_AUTH_ACQUIRED_MESSAGES = List.of(
            8 /* AcquiredInfo.START */,
            4 /* AcquiredInfo.SENSOR_DIRTY */,
            2 /* AcquiredInfo.PARTIAL */,
            5 /* AcquiredInfo.TOO_SLOW */,
            1 /* AcquiredInfo.GOOD */);

    /** {@see com.android.server.biometrics.sensors.fingerprint.aidl.AidlConversionUtils#toFrameworkAcquiredInfo}. */
    public static final List<Integer> FINGERPRINT_AUTH_ACQUIRED_MESSAGES_AIDL = List.of(
            7 /* BiometricFingerprintConstants.FINGERPRINT_ACQUIRED_START */,
            3 /* BiometricFingerprintConstants.FINGERPRINT_ACQUIRED_IMAGER_DIRTY */,
            1 /* BiometricFingerprintConstants.FINGERPRINT_ACQUIRED_PARTIAL */,
            4 /* BiometricFingerprintConstants.FINGERPRINT_ACQUIRED_TOO_SLOW */,
            0 /* BiometricFingerprintConstants.FINGERPRINT_ACQUIRED_GOOD */
    );

    /** Acquired messages sent during the authentication test. */
    public static final List<Integer> FACE_AUTH_ACQUIRED_MESSAGES = List.of(
            21 /* AcquiredInfo.START */,
            22 /* AcquiredInfo.SENSOR_DIRTY */,
            20 /* AcquiredInfo.FACE_OBSCURED */,
            10 /* AcquiredInfo.FACE_TOO_LEFT */,
            1 /* AcquiredInfo.GOOD */
    );

    /** {@see com.android.server.biometrics.sensors.face.aidl.AidlConversionUtils#toFrameworkAcquiredInfo}. */
    public static final List<Integer> FACE_AUTH_ACQUIRED_MESSAGES_AIDL = List.of(
            20 /* BiometricFaceConstants.FACE_ACQUIRED_START */,
            21 /* BiometricFaceConstants.FACE_ACQUIRED_SENSOR_DIRTY */,
            19 /* BiometricFaceConstants.FACE_ACQUIRED_FACE_OBSCURED */,
            9 /* BiometricFaceConstants.FACE_ACQUIRED_TOO_LEFT */,
            0 /* BiometricFaceConstants.FACE_ACQUIRED_GOOD */
    );

    private FingerprintHostsideConstants() {}
}
