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

package android.telephony.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.pm.PackageManager;
import android.os.Parcel;
import android.telephony.AccessNetworkConstants;
import android.telephony.SignalThresholdInfo;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Test SignalThresholdInfo to make sure the object can only constructed with only valid data. */
public class SignalThresholdInfoTest {
    private static final String TAG = "SignalThresholdInfo";

    // A sample of valid (RAN, SignalMeasurementType, threshold value) arrays. Threshold value will
    // used to construct thresholds array during test.
    private static final int[][] VALID_PARAMS = {
        {
            AccessNetworkConstants.AccessNetworkType.GERAN,
            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
            -100,
            0
        },
        {
            AccessNetworkConstants.AccessNetworkType.CDMA2000,
            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
            -100,
            1
        },
        {
            AccessNetworkConstants.AccessNetworkType.UTRAN,
            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSCP,
            -100,
            2
        },
        {
            AccessNetworkConstants.AccessNetworkType.EUTRAN,
            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
            -100,
            2
        },
        {
            AccessNetworkConstants.AccessNetworkType.NGRAN,
            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP,
            -100,
            0
        },
        {
            AccessNetworkConstants.AccessNetworkType.UTRAN,
            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_ECNO,
            -16,
            1
        },
    };

    // Map of SignalMeasurementType to invalid thresholds edge values.
    // Each invalid value will be constructed with a thresholds array to test separately.
    private static final Map<Integer, List<Integer>> INVALID_THRESHOLDS_MAP =
            Map.of(
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                    List.of(
                            SignalThresholdInfo.SIGNAL_RSSI_MIN_VALUE - 1,
                            SignalThresholdInfo.SIGNAL_RSSI_MAX_VALUE + 1),
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSCP,
                    List.of(
                            SignalThresholdInfo.SIGNAL_RSCP_MIN_VALUE - 1,
                            SignalThresholdInfo.SIGNAL_RSCP_MAX_VALUE + 1),
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                    List.of(
                            SignalThresholdInfo.SIGNAL_RSRP_MIN_VALUE - 1,
                            SignalThresholdInfo.SIGNAL_RSRP_MAX_VALUE + 1),
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ,
                    List.of(
                            SignalThresholdInfo.SIGNAL_RSRQ_MIN_VALUE - 1,
                            SignalThresholdInfo.SIGNAL_RSRQ_MAX_VALUE + 1),
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR,
                    List.of(
                            SignalThresholdInfo.SIGNAL_RSSNR_MIN_VALUE - 1,
                            SignalThresholdInfo.SIGNAL_RSSNR_MAX_VALUE + 1),
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP,
                    List.of(
                            SignalThresholdInfo.SIGNAL_SSRSRP_MIN_VALUE - 1,
                            SignalThresholdInfo.SIGNAL_SSRSRP_MAX_VALUE + 1),
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ,
                    List.of(
                            SignalThresholdInfo.SIGNAL_SSRSRQ_MIN_VALUE - 1,
                            SignalThresholdInfo.SIGNAL_SSRSRQ_MAX_VALUE + 1),
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR,
                    List.of(
                            SignalThresholdInfo.SIGNAL_SSSINR_MIN_VALUE - 1,
                            SignalThresholdInfo.SIGNAL_SSSINR_MAX_VALUE + 1),
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_ECNO,
                    List.of(
                            SignalThresholdInfo.SIGNAL_ECNO_MIN_VALUE - 1,
                            SignalThresholdInfo.SIGNAL_ECNO_MAX_VALUE + 1));

    // Map of RAN to allowed SignalMeasurementType set.
    // RAN/TYPE pair will be used to verify the validation of the combo
    private static final Map<Integer, Set<Integer>> VALID_RAN_TO_MEASUREMENT_TYPE_MAP =
            Map.of(
                    AccessNetworkConstants.AccessNetworkType.GERAN,
                    Set.of(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI),
                    AccessNetworkConstants.AccessNetworkType.CDMA2000,
                    Set.of(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI),
                    AccessNetworkConstants.AccessNetworkType.UTRAN,
                    Set.of(
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSCP,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_ECNO),
                    AccessNetworkConstants.AccessNetworkType.EUTRAN,
                    Set.of(
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR),
                    AccessNetworkConstants.AccessNetworkType.NGRAN,
                    Set.of(
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR));

    @Before
    public void setUp() throws Exception {
        assumeTrue(
                InstrumentationRegistry.getContext()
                        .getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_TELEPHONY));
    }

    @Test
    public void testConstructor_validParams() {
        for (int[] params : VALID_PARAMS) {
            final int ran = params[0];
            final int signalMeasurementType = params[1];
            final int[] thresholds = new int[] {params[2]};
            final int hysteresisDb = params[3];

            SignalThresholdInfo sti =
                    new SignalThresholdInfo.Builder()
                            .setRadioAccessNetworkType(ran)
                            .setSignalMeasurementType(signalMeasurementType)
                            .setThresholds(thresholds)
                            .setHysteresisDb(hysteresisDb)
                            .build();

            assertEquals(ran, sti.getRadioAccessNetworkType());
            assertEquals(signalMeasurementType, sti.getSignalMeasurementType());
            assertThat(thresholds).isEqualTo(sti.getThresholds());
            assertEquals(hysteresisDb, sti.getHysteresisDb());
        }
    }

    @Test
    public void testConstructor_invalidSignalMeasurementType() {
        final int[] invalidSignalMeasurementTypes = new int[] {-1, 0, 9};
        for (int signalMeasurementType : invalidSignalMeasurementTypes) {
            buildWithInvalidParameterThrowException(
                    AccessNetworkConstants.AccessNetworkType.GERAN,
                    signalMeasurementType,
                    new int[] {-1},
                    1);
        }
    }

    @Test
    public void testConstructor_nullThresholds() {
        buildWithInvalidParameterThrowException(
                AccessNetworkConstants.AccessNetworkType.GERAN,
                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                null,
                2);
    }

    @Test
    public void testConstructor_invalidRanMeasurementTypeCombo() {
        for (int ran : VALID_RAN_TO_MEASUREMENT_TYPE_MAP.keySet()) {
            Set validTypes = VALID_RAN_TO_MEASUREMENT_TYPE_MAP.get(ran);
            for (int type = SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI;
                    type <= SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_ECNO;
                    type++) {
                if (!validTypes.contains(type)) {
                    buildWithInvalidParameterThrowException(ran, type, new int[] {-1}, 0);
                }
            }
        }
    }

    @Test
    public void testConstructor_thresholdsOutOfRange() {
        for (int signalMeasurementType : INVALID_THRESHOLDS_MAP.keySet()) {
            List<Integer> invalidThresholds = INVALID_THRESHOLDS_MAP.get(signalMeasurementType);
            for (int threshold : invalidThresholds) {
                buildWithInvalidParameterThrowException(
                        getValidRan(signalMeasurementType),
                        signalMeasurementType,
                        new int[] {threshold},
                        -1);
            }
        }
    }

    @Test
    public void testParcel() {
        for (int[] params : VALID_PARAMS) {
            final int ran = params[0];
            final int signalMeasurementType = params[1];
            final int[] thresholds = new int[] {params[2]};
            final int hysteresisDb = params[3];

            SignalThresholdInfo sti =
                    new SignalThresholdInfo.Builder()
                            .setRadioAccessNetworkType(ran)
                            .setSignalMeasurementType(signalMeasurementType)
                            .setThresholds(thresholds)
                            .setHysteresisDb(hysteresisDb)
                            .build();
            Parcel p = Parcel.obtain();
            sti.writeToParcel(p, 0);
            p.setDataPosition(0);

            SignalThresholdInfo newSt = SignalThresholdInfo.CREATOR.createFromParcel(p);
            assertThat(newSt).isEqualTo(sti);
        }
    }

    @Test
    public void testConstructor_WithHysteresisOutofRange() {
        final int[] dummyThresholds = new int[] {-100, -90, -70, -60};
        buildWithInvalidParameterThrowException(
                AccessNetworkConstants.AccessNetworkType.GERAN,
                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                dummyThresholds,
                -1);
    }

    @Test
    public void testEquals() {
        final int[] dummyThresholds = new int[] {-100, -90, -70, -60};
        final int[] dummyThresholdsDisordered = new int[] {-60, -90, -100, -70};

        SignalThresholdInfo sti1 =
                new SignalThresholdInfo.Builder()
                        .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.GERAN)
                        .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                        .setThresholds(dummyThresholds)
                        .setHysteresisDb(0)
                        .build();

        SignalThresholdInfo sti2 =
                new SignalThresholdInfo.Builder()
                        .setRadioAccessNetworkType(
                                AccessNetworkConstants.AccessNetworkType.CDMA2000)
                        .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                        .setThresholds(dummyThresholds)
                        .build();

        SignalThresholdInfo sti3 =
                new SignalThresholdInfo.Builder()
                        .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.GERAN)
                        .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                        .setThresholds(dummyThresholds)
                        .setHysteresisDb(0)
                        .build();

        SignalThresholdInfo sti4 =
                new SignalThresholdInfo.Builder()
                        .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.GERAN)
                        .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                        .setThresholds(dummyThresholdsDisordered)
                        .setHysteresisDb(0)
                        .build();

        SignalThresholdInfo sti5 =
                new SignalThresholdInfo.Builder()
                        .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.GERAN)
                        .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                        .setThresholds(dummyThresholdsDisordered)
                        .setHysteresisDb(2)
                        .build();

        assertTrue(sti1.equals(sti1));
        assertFalse(sti1.equals(sti2));
        assertTrue(sti1.equals(sti3));
        assertTrue(sti1.equals(sti4));
        assertFalse(sti1.equals("sti1"));
        assertFalse(sti4.equals(sti5));
    }

    private void buildWithInvalidParameterThrowException(
            int ran, int signalMeasurementType, int[] thresholds, int hysteresisDb) {
        try {
            new SignalThresholdInfo.Builder()
                    .setRadioAccessNetworkType(ran)
                    .setSignalMeasurementType(signalMeasurementType)
                    .setThresholds(thresholds)
                    .setHysteresisDb(hysteresisDb)
                    .build();
            fail("Exception expected");
        } catch (IllegalArgumentException | NullPointerException expected) {

        }
    }

    /**
     * Return a possible valid RAN value for the measurement type. This is used to prevent the
     * invalid ran/type causing IllegalArgumentException when testing other invalid input cases.
     */
    private static int getValidRan(@SignalThresholdInfo.SignalMeasurementType int type) {
        switch (type) {
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI:
                return AccessNetworkConstants.AccessNetworkType.GERAN;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSCP:
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_ECNO:
                return AccessNetworkConstants.AccessNetworkType.UTRAN;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP:
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ:
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR:
                return AccessNetworkConstants.AccessNetworkType.EUTRAN;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP:
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ:
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR:
                return AccessNetworkConstants.AccessNetworkType.NGRAN;
            default:
                return AccessNetworkConstants.AccessNetworkType.UNKNOWN;
        }
    }
}
