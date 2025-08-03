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

import static android.server.biometrics.cts.FingerprintHostsideConstants.FACE_AUTH_ACQUIRED_MESSAGES_AIDL;
import static android.server.biometrics.cts.FingerprintHostsideConstants.FACE_ENROLL_ACQUIRED_MESSAGES_AIDL;
import static android.server.biometrics.cts.FingerprintHostsideConstants.FINGERPRINT_AUTH_ACQUIRED_MESSAGES;
import static android.server.biometrics.cts.FingerprintHostsideConstants.FINGERPRINT_AUTH_ACQUIRED_MESSAGES_AIDL;
import static android.server.biometrics.cts.FingerprintHostsideConstants.FINGERPRINT_ENROLL_ACQUIRED_MESSAGES;
import static android.server.biometrics.cts.FingerprintHostsideConstants.FINGERPRINT_ENROLL_ACQUIRED_MESSAGES_AIDL;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.hardware.biometrics.ActionEnum;
import android.hardware.biometrics.ClientEnum;
import android.hardware.biometrics.ModalityEnum;
import android.hardware.biometrics.SessionTypeEnum;

import com.android.compatibility.common.util.NonApiTest;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RunUtil;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Tests for biometric atom logging.
 *
 * Run via: atest CtsBiometricsHostTestCases -c
 */
public class BiometricsAtomsTests extends BiometricDeviceTestCase {

    private static final String TEST_PKG = "android.server.biometrics.cts.app";
    private static final String TEST_CLASS = ".BiometricsAtomsHostSideTests";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    @NonApiTest(exemptionReasons = {}, justification = "METRIC")
    public void testEnrollAtom() throws Exception {
        if (!hasAidlBiometrics()) {
            CLog.w("Skipping test - no AIDL biometrics on device");
            return;
        }

        final List<StatsLog.EventMetricData> data = runOnDevice(
                "testEnroll",
                new int[]{AtomsProto.Atom.BIOMETRIC_ENROLLED_FIELD_NUMBER,
                        AtomsProto.Atom.BIOMETRIC_ACQUIRED_FIELD_NUMBER});

        if (hasFeatureFingerprint(true /* aidlOnly */)) {
            final ModalityEnum modality = ModalityEnum.MODALITY_FINGERPRINT;

            final List<AtomsProto.BiometricEnrolled> enrolledAtoms =
                    filterEnrollmentAtoms(data, modality);
            assertThat(enrolledAtoms).hasSize(1);
            assertEnrollmentAtomData(enrolledAtoms.get(0));

            final List<AtomsProto.BiometricAcquired> acquiredAtoms =
                    filterAcquiredAtoms(data, modality);
            assertEnrollmentAcquiredAtomsData(acquiredAtoms, modality);
        } else {
            CLog.w("Skipping test - no AIDL biometrics on device");
        }

        if (hasFeatureFace(true /* aidlOnly */)) {
            final ModalityEnum modality = ModalityEnum.MODALITY_FACE;

            final List<AtomsProto.BiometricEnrolled> enrolledAtoms =
                    filterEnrollmentAtoms(data, modality);
            assertThat(enrolledAtoms).hasSize(1);
            assertEnrollmentAtomData(enrolledAtoms.get(0));

            final List<AtomsProto.BiometricAcquired> acquiredAtoms =
                    filterAcquiredAtoms(data, modality);
            assertEnrollmentAcquiredAtomsData(acquiredAtoms, modality);
        } else {
            CLog.w("Skipping test - no AIDL biometrics on device");
        }
    }

    private void assertEnrollmentAtomData(AtomsProto.BiometricEnrolled atom) throws Exception {
        assertThat(atom.hasSuccess() && atom.getSuccess()).isTrue();
        assertThat(atom.getUser()).isEqualTo(getDevice().getCurrentUser());
        assertThat(atom.hasAmbientLightLux()).isTrue();
    }

    // check enrollment acquired messages match the fixed values in the test
    private void assertEnrollmentAcquiredAtomsData(
            List<AtomsProto.BiometricAcquired> atoms, ModalityEnum modality) throws Exception {
        assertThat(atoms).isNotEmpty();

        for (AtomsProto.BiometricAcquired atom : atoms) {
            assertThat(atom.hasModality() && atom.getModality() == modality).isTrue();
            assertThat(atom.hasAction() && atom.getAction() == ActionEnum.ACTION_ENROLL).isTrue();
            assertThat(atom.hasUser() && atom.getUser() == getDevice().getCurrentUser()).isTrue();
        }

        if (modality == ModalityEnum.MODALITY_FINGERPRINT) {
            assertThat(atoms.stream().map(d -> d.getAcquireInfo()).collect(Collectors.toList()))
                    .containsExactlyElementsIn(hasAidlFingerprintSensorId()
                            ? FINGERPRINT_ENROLL_ACQUIRED_MESSAGES_AIDL
                            : FINGERPRINT_ENROLL_ACQUIRED_MESSAGES)
                    .inOrder();
        }

        if (modality == ModalityEnum.MODALITY_FACE) {
            assertThat(atoms.stream().map(d -> d.getAcquireInfo()).collect(Collectors.toList()))
                    .containsExactlyElementsIn(FACE_ENROLL_ACQUIRED_MESSAGES_AIDL)
                    .inOrder();
        }
    }

    @NonApiTest(exemptionReasons = {}, justification = "METRIC")
    public void testAuthenticateAtom() throws Exception {
        if (!hasAidlBiometrics()) {
            CLog.w("Skipping test - no AIDL biometrics on device");
            return;
        }

        final SensorInfo sensorInfo = getSensorInfo();
        final List<StatsLog.EventMetricData> data = runOnDevice(
                "testAuthenticateWithBiometricPrompt",
                new int[]{AtomsProto.Atom.BIOMETRIC_AUTHENTICATED_FIELD_NUMBER,
                        AtomsProto.Atom.BIOMETRIC_ACQUIRED_FIELD_NUMBER});

        if (hasFeatureFingerprint(true /* aidlOnly */)) {
            final ModalityEnum modality = ModalityEnum.MODALITY_FINGERPRINT;

            final List<AtomsProto.BiometricAuthenticated> authAtoms =
                    filterAuthenticatedAtoms(data, modality);

            // TODO(b/253318030): No API beyond bp (doesn't allow convenience) - need new test API
            if (sensorInfo.hasWeakOrGreaterFingerprintSensor()) {
                assertThat(authAtoms).hasSize(1);
                assertAuthenticateAtomData(authAtoms.get(0));

                final List<AtomsProto.BiometricAcquired> acquiredAtoms =
                        filterAcquiredAtoms(data, modality);
                assertAuthenticateAcquiredAtomsData(
                        acquiredAtoms, modality, authAtoms.get(0).getSessionId());
            } else {
                assertThat(authAtoms).isEmpty();
            }
        } else {
            CLog.w("Skipping test - no AIDL biometrics on device");
        }

        if (hasFeatureFace(true /* aidlOnly */)) {
            final ModalityEnum modality = ModalityEnum.MODALITY_FACE;

            final List<AtomsProto.BiometricAuthenticated> authAtoms =
                    filterAuthenticatedAtoms(data, modality);

            // TODO(b/253318030): No API beyond bp (doesn't allow convenience) - need new test API
            if (sensorInfo.hasWeakOrGreaterFaceSensor()) {
                assertThat(authAtoms).hasSize(2);
                assertAuthenticateAtomData(authAtoms.get(0));
                assertAuthenticateAtomData(authAtoms.get(1));

                final List<AtomsProto.BiometricAcquired> acquiredAtoms =
                        filterAcquiredAtoms(data, modality);
                assertAuthenticateAcquiredAtomsData(
                        acquiredAtoms, modality, authAtoms.get(0).getSessionId());
            } else {
                assertThat(authAtoms).isEmpty();
                CLog.i("Skipping test - BP does not allow convenience sensor");
            }
        } else {
            CLog.w("Skipping test - no AIDL biometrics on device");
        }
    }

    private void assertAuthenticateAtomData(
            AtomsProto.BiometricAuthenticated atom) throws Exception {
        assertThat(atom.getState()).isEqualTo(AtomsProto.BiometricAuthenticated.State.CONFIRMED);
        assertThat(atom.getUser()).isEqualTo(getDevice().getCurrentUser());
        assertThat(atom.hasAmbientLightLux()).isTrue();
        assertThat(atom.getSessionId()).isGreaterThan(0);
        assertThat(atom.getSessionType()).isEqualTo(SessionTypeEnum.SESSION_TYPE_BIOMETRIC_PROMPT);
        assertThat(atom.getClient()).isEqualTo(ClientEnum.CLIENT_BIOMETRIC_PROMPT);
    }

    // check enrollment acquired messages match the fixed values in the test
    private void assertAuthenticateAcquiredAtomsData(
            List<AtomsProto.BiometricAcquired> atoms, ModalityEnum modality,
            int sessionId) throws Exception {
        assertThat(atoms).isNotEmpty();

        for (AtomsProto.BiometricAcquired atom : atoms) {
            assertThat(atom.hasModality() && atom.getModality() == modality).isTrue();
            assertThat(atom.hasAction() && atom.getAction() == ActionEnum.ACTION_AUTHENTICATE)
                    .isTrue();
            assertThat(atom.hasUser() && atom.getUser() == getDevice().getCurrentUser()).isTrue();
            assertThat(atom.hasSessionType()
                    && atom.getSessionType() == SessionTypeEnum.SESSION_TYPE_BIOMETRIC_PROMPT)
                    .isTrue();
            assertThat(atom.getSessionId()).isEqualTo(sessionId);
            assertThat(atom.getClient()).isEqualTo(ClientEnum.CLIENT_BIOMETRIC_PROMPT);
        }

        final List<Integer> expectedAcquireCodes;
        if (modality == ModalityEnum.MODALITY_FINGERPRINT) {
            expectedAcquireCodes = hasAidlFingerprintSensorId()
                    ? FINGERPRINT_AUTH_ACQUIRED_MESSAGES_AIDL : FINGERPRINT_AUTH_ACQUIRED_MESSAGES;
        } else if (modality == ModalityEnum.MODALITY_FACE) {
            expectedAcquireCodes = FACE_AUTH_ACQUIRED_MESSAGES_AIDL;
        } else {
            expectedAcquireCodes = List.of();
        }

        assertThat(atoms.stream().map(d -> d.getAcquireInfo()).collect(Collectors.toList()))
                .containsExactlyElementsIn(expectedAcquireCodes).inOrder();
        assertThat(atoms.stream().map(a -> a.getSessionOrder()).collect(Collectors.toList()))
                .containsExactlyElementsIn(
                        IntStream.range(0, expectedAcquireCodes.size())
                                .boxed()
                                .collect(Collectors.toList()));
    }

    private List<StatsLog.EventMetricData> runOnDevice(
            String methodName, int[] atomsToCollect) throws Exception {
        ConfigUtils.uploadConfigForPushedAtoms(getDevice(), TEST_PKG, atomsToCollect);
        DeviceUtils.runDeviceTests(
                getDevice(),
                TEST_PKG,
                TEST_PKG + TEST_CLASS,
                methodName);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        return ReportUtils.getEventMetricDataList(getDevice());
    }

    private static List<AtomsProto.BiometricEnrolled> filterEnrollmentAtoms(
            List<StatsLog.EventMetricData> data, ModalityEnum modality) {
        return data.stream()
                .filter(d -> d.getAtom().hasBiometricEnrolled())
                .map(d -> d.getAtom().getBiometricEnrolled())
                .filter(d -> d.hasModality() && d.getModality() == modality)
                .collect(Collectors.toList());
    }

    private static List<AtomsProto.BiometricAcquired> filterAcquiredAtoms(
            List<StatsLog.EventMetricData> data, ModalityEnum modality) {
        return data.stream()
                .filter(d -> d.getAtom().hasBiometricAcquired())
                .map(d -> d.getAtom().getBiometricAcquired())
                .filter(d -> d.hasModality() && d.getModality() == modality)
                .collect(Collectors.toList());
    }

    private static List<AtomsProto.BiometricAuthenticated> filterAuthenticatedAtoms(
            List<StatsLog.EventMetricData> data, ModalityEnum modality) {
        return data.stream()
                .filter(d -> d.getAtom().hasBiometricAuthenticated())
                .map(d -> d.getAtom().getBiometricAuthenticated())
                .filter(d -> d.hasModality() && d.getModality() == modality)
                .collect(Collectors.toList());
    }
}
