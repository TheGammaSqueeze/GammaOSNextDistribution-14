/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.location.cts.common;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.CorrelationVector;
import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.location.SatellitePvt;
import android.util.Log;

import com.google.common.collect.Range;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for GnssMeasurement Tests.
 */
public final class TestMeasurementUtil {

    private static final String TAG = "TestMeasurementUtil";

    private static final long NSEC_IN_SEC = 1000_000_000L;
    // Generally carrier phase quality prr's have uncertainties around 0.001-0.05 m/s, vs.
    // doppler energy quality prr's closer to 0.25-10 m/s.  Threshold is chosen between those
    // typical ranges.
    private static final float THRESHOLD_FOR_CARRIER_PRR_UNC_METERS_PER_SEC = 0.15F;

    // For gpsTimeInNs >= 1.14 * 10^18 (year 2016+)
    private static final long GPS_TIME_YEAR_2016_IN_NSEC = 1_140_000_000L * NSEC_IN_SEC;

    // Error message for GnssMeasurements Registration.
    public static final String REGISTRATION_ERROR_MESSAGE = "Registration of GnssMeasurements" +
            " listener has failed, this indicates a platform bug. Please report the issue with" +
            " a full bugreport.";

    private static final Range<Double> GPS_L5_QZSS_J5_FREQ_RANGE_HZ =
            Range.closed(1.164e9, 1.189e9);
    private static final Range<Double> GAL_E1_FREQ_RANGE_HZ =
            Range.closed(1.559e9, 1.591e9);
    private static final Range<Double> GAL_E5_FREQ_RANGE_HZ =
            Range.closed(1.164e9, 1.218e9);

    private enum GnssBand {
        GNSS_L1,
        GNSS_L2,
        GNSS_L5,
        GNSS_E6
    }

    // The valid Gnss navigation message type as listed in
    // android/hardware/libhardware/include/hardware/gps.h
    public static final Set<Integer> GNSS_NAVIGATION_MESSAGE_TYPE =
            new HashSet<Integer>(Arrays.asList(
                    GnssNavigationMessage.TYPE_UNKNOWN,
                    GnssNavigationMessage.TYPE_GPS_L1CA,
                    GnssNavigationMessage.TYPE_GPS_L2CNAV,
                    GnssNavigationMessage.TYPE_GPS_L5CNAV,
                    GnssNavigationMessage.TYPE_GPS_CNAV2,
                    GnssNavigationMessage.TYPE_SBS,
                    GnssNavigationMessage.TYPE_GLO_L1CA,
                    GnssNavigationMessage.TYPE_QZS_L1CA,
                    GnssNavigationMessage.TYPE_BDS_D1,
                    GnssNavigationMessage.TYPE_BDS_D2,
                    GnssNavigationMessage.TYPE_BDS_CNAV1,
                    GnssNavigationMessage.TYPE_BDS_CNAV2,
                    GnssNavigationMessage.TYPE_GAL_I,
                    GnssNavigationMessage.TYPE_GAL_F,
                    GnssNavigationMessage.TYPE_IRN_L5CA
            ));

    /**
     * Check if test can be run on the current device.
     *
     * @param  testLocationManager TestLocationManager
     * @return true if Build.VERSION &gt;= {@code androidSdkVersionCode} and Location GPS present on
     *         device.
     */
    public static boolean canTestRunOnCurrentDevice(TestLocationManager testLocationManager,
            String testTag) {
        // If device does not have a GPS, skip the test.
        if (!TestUtils.deviceHasGpsFeature(testLocationManager.getContext())) {
            Log.i(TAG, "Skip the test since GPS is not supported on the device.");
            return false;
        }

        boolean gpsProviderEnabled = testLocationManager.getLocationManager()
                .isProviderEnabled(LocationManager.GPS_PROVIDER);
        SoftAssert.failOrWarning(true, " GPS location disabled on the device. "
                + "Enable location in settings to continue test.", gpsProviderEnabled);
        return gpsProviderEnabled;
    }

    /**
     * Check if current device is an Android Automotive OS device.
     */
    public static boolean isAutomotiveDevice(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    /**
     * Check if pseudorange rate uncertainty in Gnss Measurement is in the expected range.
     * See field description in {@code gps.h}.
     *
     * @param measurement GnssMeasurement
     * @return true if this measurement has prr uncertainty in a range indicative of carrier phase
     */
    public static boolean gnssMeasurementHasCarrierPhasePrr(GnssMeasurement measurement) {
      return (measurement.getPseudorangeRateUncertaintyMetersPerSecond() <
              THRESHOLD_FOR_CARRIER_PRR_UNC_METERS_PER_SEC);
    }

    /**
     * Assert all mandatory fields in Gnss Clock are in expected range.
     * See mandatory fields in {@code gps.h}.
     *
     * @param clock       GnssClock
     * @param softAssert  custom SoftAssert
     * @param timeInNs    event time in ns
     */
    public static void assertGnssClockFields(GnssClock clock,
                                             SoftAssert softAssert,
                                             long timeInNs) {
        softAssert.assertTrue("time_ns: clock value",
                timeInNs,
                "X >= 0",
                String.valueOf(timeInNs),
                timeInNs >= 0L);

        // If full bias is valid and accurate within one sec. verify its sign & magnitude
        if (clock.hasFullBiasNanos() &&
                ((!clock.hasBiasUncertaintyNanos()) ||
                        (clock.getBiasUncertaintyNanos() < NSEC_IN_SEC))) {
            long gpsTimeInNs = timeInNs - clock.getFullBiasNanos();
            softAssert.assertTrue("TimeNanos - FullBiasNanos = GpsTimeNanos: clock value",
                    gpsTimeInNs,
                    "gpsTimeInNs >= 1.14 * 10^18 (year 2016+)",
                    String.valueOf(gpsTimeInNs),
                    gpsTimeInNs >= GPS_TIME_YEAR_2016_IN_NSEC);
        }
    }

    /**
     * Asserts the same FullBiasNanos of multiple GnssMeasurementEvents at the same time epoch.
     *
     * <p>FullBiasNanos denotes the receiver clock bias calculated by the GNSS chipset. If multiple
     * GnssMeasurementEvents are tagged with the same time epoch, their FullBiasNanos should be the
     * same.
     *
     * @param softAssert custom SoftAssert
     * @param events     GnssMeasurementEvents. Each event includes one GnssClock with a
     *                   fullBiasNanos.
     */
    public static void assertGnssClockHasConsistentFullBiasNanos(SoftAssert softAssert,
            List<GnssMeasurementsEvent> events) {
        Map<Long, List<Long>> timeToFullBiasList = new HashMap<>();
        for (GnssMeasurementsEvent event : events) {
            long timeNanos = event.getClock().getTimeNanos();
            long fullBiasNanos = event.getClock().getFullBiasNanos();

            timeToFullBiasList.putIfAbsent(timeNanos, new ArrayList<>());
            List<Long> fullBiasNanosList = timeToFullBiasList.get(timeNanos);
            fullBiasNanosList.add(fullBiasNanos);
        }

        for (Map.Entry<Long, List<Long>> entry : timeToFullBiasList.entrySet()) {
            long timeNanos = entry.getKey();
            List<Long> fullBiasNanosList = entry.getValue();
            if (fullBiasNanosList.size() < 2) {
                continue;
            }
            long fullBiasNanos = fullBiasNanosList.get(0);
            for (int i = 1; i < fullBiasNanosList.size(); i++) {
                softAssert.assertTrue("FullBiasNanos are the same at the same timeNanos",
                        timeNanos,
                        "fullBiasNanosList.get(i) - fullBiasNanosList.get(0) == 0",
                        String.valueOf(fullBiasNanosList.get(i) - fullBiasNanos),
                        fullBiasNanosList.get(i) - fullBiasNanos == 0);
            }
        }
    }

    /**
     * Assert all mandatory fields in Gnss Measurement are in expected range.
     * See mandatory fields in {@code gps.h}.
     *
     * @param testLocationManager TestLocationManager
     * @param measurement GnssMeasurement
     * @param softAssert  custom SoftAssert
     * @param timeInNs    event time in ns
     */
    public static void assertAllGnssMeasurementMandatoryFields(
        TestLocationManager testLocationManager, GnssMeasurement measurement,
        SoftAssert softAssert, long timeInNs) {

        verifySvid(measurement, softAssert, timeInNs);
        verifyReceivedSatelliteVehicleTimeInNs(measurement, softAssert, timeInNs);
        verifyAccumulatedDeltaRanges(measurement, softAssert, timeInNs);

        int state = measurement.getState();
        softAssert.assertTrue("state: Satellite code sync state",
                timeInNs,
                "X >= 0",
                String.valueOf(state),
                state >= 0);

        // Check received_gps_tow_uncertainty_ns
        softAssert.assertTrueAsWarning("received_gps_tow_uncertainty_ns:" +
                        " Uncertainty of received GPS Time-of-Week in ns",
                timeInNs,
                "X > 0",
                String.valueOf(measurement.getReceivedSvTimeUncertaintyNanos()),
                measurement.getReceivedSvTimeUncertaintyNanos() > 0L);

        long timeOffsetInSec = TimeUnit.NANOSECONDS.toSeconds(
                (long) measurement.getTimeOffsetNanos());
        softAssert.assertTrue("time_offset_ns: Time offset",
                timeInNs,
                "-100 seconds < X < +10 seconds",
                String.valueOf(measurement.getTimeOffsetNanos()),
                (-100 < timeOffsetInSec) && (timeOffsetInSec < 10));

        softAssert.assertTrue("c_n0_dbhz: Carrier-to-noise density",
                timeInNs,
                "0.0 >= X <=63",
                String.valueOf(measurement.getCn0DbHz()),
                measurement.getCn0DbHz() >= 0.0 &&
                        measurement.getCn0DbHz() <= 63.0);

        softAssert.assertTrue("pseudorange_rate_uncertainty_mps: " +
                        "Pseudorange Rate Uncertainty in m/s",
                timeInNs,
                "X > 0.0",
                String.valueOf(
                        measurement.getPseudorangeRateUncertaintyMetersPerSecond()),
                measurement.getPseudorangeRateUncertaintyMetersPerSecond() > 0.0);

        verifyGnssCarrierFrequency(softAssert, testLocationManager,
                measurement.hasCarrierFrequencyHz(),
                measurement.hasCarrierFrequencyHz() ? measurement.getCarrierFrequencyHz() : 0F);

        // Check carrier_phase.
        if (measurement.hasCarrierPhase()) {
            softAssert.assertTrue("carrier_phase: Carrier phase",
                    timeInNs,
                    "0.0 >= X <= 1.0",
                    String.valueOf(measurement.getCarrierPhase()),
                    measurement.getCarrierPhase() >= 0.0 && measurement.getCarrierPhase() <= 1.0);
        }

        // Check carrier_phase_uncertainty..
        if (measurement.hasCarrierPhaseUncertainty()) {
            softAssert.assertTrue("carrier_phase_uncertainty: 1-Sigma uncertainty of the " +
                            "carrier-phase",
                    timeInNs,
                    "X > 0.0",
                    String.valueOf(measurement.getCarrierPhaseUncertainty()),
                    measurement.getCarrierPhaseUncertainty() > 0.0);
        }

        // Check GNSS Measurement's multipath_indicator.
        softAssert.assertTrue("multipath_indicator: GNSS Measurement's multipath indicator",
                timeInNs,
                "0 >= X <= 2",
                String.valueOf(measurement.getMultipathIndicator()),
                measurement.getMultipathIndicator() >= 0
                        && measurement.getMultipathIndicator() <= 2);


        // Check Signal-to-Noise ratio (SNR).
        if (measurement.hasSnrInDb()) {
            softAssert.assertTrue("snr: Signal-to-Noise ratio (SNR) in dB",
                    timeInNs,
                    "0.0 >= X <= 63",
                    String.valueOf(measurement.getSnrInDb()),
                    measurement.getSnrInDb() >= 0.0 && measurement.getSnrInDb() <= 63);
        }

        if (measurement.hasAutomaticGainControlLevelDb()) {
            softAssert.assertTrue("Automatic Gain Control level in dB",
                timeInNs,
                "-100 >= X <= 100",
                String.valueOf(measurement.getAutomaticGainControlLevelDb()),
                measurement.getAutomaticGainControlLevelDb() >= -100
                    && measurement.getAutomaticGainControlLevelDb() <= 100);
        }
    }

    /**
     * Assert all SystemApi fields in Gnss Measurement are in expected range.
     *
     * @param measurement GnssMeasurement
     * @param softAssert  custom SoftAssert
     * @param timeInNs    event time in ns
     */
    public static void assertAllGnssMeasurementSystemFields(GnssMeasurement measurement,
        SoftAssert softAssert, long timeInNs) {

        if (measurement.hasCorrelationVectors()) {
            verifyCorrelationVectors(measurement, softAssert, timeInNs);
        }

        if (measurement.hasSatellitePvt()) {
            verifySatellitePvt(measurement, softAssert, timeInNs);
        }
    }

    /**
     * Verify correlation vectors are in expected range.
     *
     * @param measurement GnssMeasurement
     * @param softAssert  custom SoftAssert
     * @param timeInNs    event time in ns
     */
    private static void verifyCorrelationVectors(GnssMeasurement measurement,
        SoftAssert softAssert, long timeInNs) {
        Collection<CorrelationVector> correlationVectors =
                measurement.getCorrelationVectors();
        assertNotNull("CorrelationVectors cannot be null.", correlationVectors);
        softAssert.assertTrue("CorrelationVectors count",
                timeInNs,
                "X > 0",
                String.valueOf(correlationVectors.size()),
                correlationVectors.size() > 0);
        for (CorrelationVector correlationVector : correlationVectors) {
            assertNotNull("CorrelationVector cannot be null.", correlationVector);
            int[] magnitude = correlationVector.getMagnitude();
            softAssert.assertTrue("frequency_offset_mps : "
                    + "Frequency offset from reported pseudorange rate "
                    + "for this CorrelationVector",
                    timeInNs,
                    "X >= 0.0",
                    String.valueOf(correlationVector.getFrequencyOffsetMetersPerSecond()),
                    correlationVector.getFrequencyOffsetMetersPerSecond() >= 0.0);
            softAssert.assertTrue("sampling_width_m : "
                    + "The space between correlation samples in meters",
                    timeInNs,
                    "X > 0.0",
                    String.valueOf(correlationVector.getSamplingWidthMeters()),
                    correlationVector.getSamplingWidthMeters() > 0.0);
            softAssert.assertTrue("Magnitude count",
                    timeInNs,
                    "X > 0",
                    String.valueOf(magnitude.length),
                magnitude.length > 0);
            for (int value : magnitude) {
                softAssert.assertTrue("magnitude : Data representing normalized "
                        + "correlation magnitude values",
                        timeInNs,
                        "-32768 <= X < 32767",
                        String.valueOf(value),
                        value >= -32768 && value < 32767);
            }
        }
    }

    /**
     * Verify accumulated delta ranges are in expected range.
     *
     * @param measurement GnssMeasurement
     * @param softAssert  custom SoftAssert
     * @param timeInNs    event time in ns
     */
    private static void verifyAccumulatedDeltaRanges(GnssMeasurement measurement,
        SoftAssert softAssert, long timeInNs) {

        int accumulatedDeltaRangeState = measurement.getAccumulatedDeltaRangeState();
        softAssert.assertTrue("accumulated_delta_range_state: " +
                "Accumulated delta range state",
                timeInNs,
                "X & ~ADR_STATE_ALL == 0",
                String.valueOf(accumulatedDeltaRangeState),
                (accumulatedDeltaRangeState & ~GnssMeasurement.ADR_STATE_ALL) == 0);
        softAssert.assertTrue("accumulated_delta_range_state: " +
                "Accumulated delta range state",
                timeInNs,
                "ADR_STATE_HALF_CYCLE_REPORTED, or !ADR_STATE_HALF_CYCLE_RESOLVED",
                String.valueOf(accumulatedDeltaRangeState),
                ((accumulatedDeltaRangeState &
                  GnssMeasurement.ADR_STATE_HALF_CYCLE_REPORTED) != 0) ||
                 (accumulatedDeltaRangeState &
                  GnssMeasurement.ADR_STATE_HALF_CYCLE_RESOLVED) == 0);
        if ((accumulatedDeltaRangeState & GnssMeasurement.ADR_STATE_VALID) != 0) {
            double accumulatedDeltaRangeInMeters =
                    measurement.getAccumulatedDeltaRangeMeters();
            softAssert.assertTrue("accumulated_delta_range_m: " +
                    "Accumulated delta range in meter",
                    timeInNs,
                    "X != 0.0",
                    String.valueOf(accumulatedDeltaRangeInMeters),
                    accumulatedDeltaRangeInMeters != 0.0);
            double accumulatedDeltaRangeUncertainty =
                    measurement.getAccumulatedDeltaRangeUncertaintyMeters();
            softAssert.assertTrue("accumulated_delta_range_uncertainty_m: " +
                    "Accumulated delta range uncertainty in meter",
                    timeInNs,
                    "X > 0.0",
                    String.valueOf(accumulatedDeltaRangeUncertainty),
                    accumulatedDeltaRangeUncertainty > 0.0);
        }
    }

    /**
     * Verify svid's are in expected range.
     *
     * @param measurement GnssMeasurement
     * @param softAssert  custom SoftAssert
     * @param timeInNs    event time in ns
     */
    private static void verifySvid(GnssMeasurement measurement, SoftAssert softAssert,
        long timeInNs) {

        int constellationType = measurement.getConstellationType();
        int svid = measurement.getSvid();
        validateSvidSub(softAssert, timeInNs, constellationType, svid);
    }

    public static void validateSvidSub(SoftAssert softAssert, Long timeInNs,
        int constellationType, int svid) {

        String svidValue = String.valueOf(svid);

        switch (constellationType) {
            case GnssStatus.CONSTELLATION_GPS:
                softAssert.assertTrue("svid: Space Vehicle ID. Constellation type " +
                                "= CONSTELLATION_GPS",
                        timeInNs,
                        "[1, 32]",
                        svidValue,
                        svid > 0 && svid <= 32);
                break;
            case GnssStatus.CONSTELLATION_SBAS:
                softAssert.assertTrue("svid: Space Vehicle ID. Constellation type " +
                                "= CONSTELLATION_SBAS",
                        timeInNs,
                        "120 <= X <= 192",
                        svidValue,
                        svid >= 120 && svid <= 192);
                break;
            case GnssStatus.CONSTELLATION_GLONASS:
                softAssert.assertTrue("svid: Slot ID, or if unknown, Frequency + 100 (93-106). " +
                                "Constellation type = CONSTELLATION_GLONASS",
                        timeInNs,
                        "1 <= svid <= 25 || 93 <= svid <= 106",
                        svidValue,
                        (svid >= 1 && svid <= 25) || (svid >= 93 && svid <= 106));
                break;
            case GnssStatus.CONSTELLATION_QZSS:
                softAssert.assertTrue("svid: Space Vehicle ID. Constellation type " +
                                "= CONSTELLATION_QZSS",
                        timeInNs,
                        "183 <= X <= 206",
                        svidValue,
                        svid >= 183 && svid <= 206);
                break;
            case GnssStatus.CONSTELLATION_BEIDOU:
                softAssert.assertTrue("svid: Space Vehicle ID. Constellation type " +
                                "= CONSTELLATION_BEIDOU",
                        timeInNs,
                        "1 <= X <= 63",
                        svidValue,
                        svid >= 1 && svid <= 63);
                break;
            case GnssStatus.CONSTELLATION_GALILEO:
                softAssert.assertTrue("svid: Space Vehicle ID. Constellation type " +
                                "= CONSTELLATION_GALILEO",
                        timeInNs,
                        "1 <= X <= 36",
                        String.valueOf(svid),
                        svid >= 1 && svid <= 36);
                break;
            case GnssStatus.CONSTELLATION_IRNSS:
                softAssert.assertTrue("svid: Space Vehicle ID. Constellation type " +
                                "= CONSTELLATION_IRNSS",
                        timeInNs,
                        "1 <= X <= 14",
                        String.valueOf(svid),
                        svid >= 1 && svid <= 14);
                break;
            default:
                // Explicit fail if did not receive valid constellation type.
                softAssert.assertTrue("svid: Space Vehicle ID. Did not receive any valid " +
                                "constellation type.",
                        timeInNs,
                        "Valid constellation type.",
                        svidValue,
                        false);
                break;
        }
    }

    /**
     * Verify sv times are in expected range.
     *
     * @param measurement GnssMeasurement
     * @param softAssert  custom SoftAssert
     * @param timeInNs    event time in ns
     * */
    private static void verifyReceivedSatelliteVehicleTimeInNs(GnssMeasurement measurement,
        SoftAssert softAssert, long timeInNs) {

        int constellationType = measurement.getConstellationType();
        int state = measurement.getState();
        long received_sv_time_ns = measurement.getReceivedSvTimeNanos();
        double sv_time_ms = TimeUnit.NANOSECONDS.toMillis(received_sv_time_ns);
        double sv_time_sec = TimeUnit.NANOSECONDS.toSeconds(received_sv_time_ns);
        double sv_time_days = TimeUnit.NANOSECONDS.toDays(received_sv_time_ns);

        // Check ranges for received_sv_time_ns for given Gps State
        if (state == 0) {
            softAssert.assertTrue("received_sv_time_ns:" +
                            " Received SV Time-of-Week in ns." +
                            " GNSS_MEASUREMENT_STATE_UNKNOWN.",
                    timeInNs,
                    "X == 0",
                    String.valueOf(received_sv_time_ns),
                    sv_time_ms == 0);
        }

        switch (constellationType) {
            case GnssStatus.CONSTELLATION_GPS:
                verifyGpsQzssSvTimes(measurement, softAssert, timeInNs, state, "CONSTELLATION_GPS");
                break;
            case GnssStatus.CONSTELLATION_QZSS:
                verifyGpsQzssSvTimes(measurement, softAssert, timeInNs, state,
                        "CONSTELLATION_QZSS");
                break;
            case GnssStatus.CONSTELLATION_SBAS:
                if ((state & GnssMeasurement.STATE_SBAS_SYNC)
                        == GnssMeasurement.STATE_SBAS_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_SBAS_SYNC",
                                    "GnssStatus.CONSTELLATION_SBAS"),
                            timeInNs,
                            "0s >= X <= 1s",
                            String.valueOf(sv_time_sec),
                            sv_time_sec >= 0 && sv_time_sec <= 1);
                } else if ((state & GnssMeasurement.STATE_SYMBOL_SYNC)
                        == GnssMeasurement.STATE_SYMBOL_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_SYMBOL_SYNC",
                                    "GnssStatus.CONSTELLATION_SBAS"),
                            timeInNs,
                            "0ms >= X <= 2ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 2);
                } else if ((state & GnssMeasurement.STATE_CODE_LOCK)
                        == GnssMeasurement.STATE_CODE_LOCK) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_CODE_LOCK",
                                    "GnssStatus.CONSTELLATION_SBAS"),
                            timeInNs,
                            "0ms >= X <= 1ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 1);
                }
                break;
            case GnssStatus.CONSTELLATION_GLONASS:
                if ((state & GnssMeasurement.STATE_GLO_TOD_DECODED)
                        == GnssMeasurement.STATE_GLO_TOD_DECODED) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_GLO_TOD_DECODED",
                                    "GnssStatus.CONSTELLATION_GLONASS"),
                            timeInNs,
                            "0 day >= X <= 1 day",
                            String.valueOf(sv_time_days),
                            sv_time_days >= 0 && sv_time_days <= 1);
                } else if ((state & GnssMeasurement.STATE_GLO_TOD_KNOWN)
                         == GnssMeasurement.STATE_GLO_TOD_KNOWN) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_GLO_TOD_KNOWN",
                                    "GnssStatus.CONSTELLATION_GLONASS"),
                            timeInNs,
                            "0 day >= X <= 1 day",
                            String.valueOf(sv_time_days),
                            sv_time_days >= 0 && sv_time_days <= 1);
                } else if ((state & GnssMeasurement.STATE_GLO_STRING_SYNC)
                        == GnssMeasurement.STATE_GLO_STRING_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_GLO_STRING_SYNC",
                                    "GnssStatus.CONSTELLATION_GLONASS"),
                            timeInNs,
                            "0s >= X <= 2s",
                            String.valueOf(sv_time_sec),
                            sv_time_sec >= 0 && sv_time_sec <= 2);
                } else if ((state & GnssMeasurement.STATE_BIT_SYNC)
                        == GnssMeasurement.STATE_BIT_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_BIT_SYNC",
                                    "GnssStatus.CONSTELLATION_GLONASS"),
                            timeInNs,
                            "0ms >= X <= 20ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 20);
                } else if ((state & GnssMeasurement.STATE_SYMBOL_SYNC)
                        == GnssMeasurement.STATE_SYMBOL_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_SYMBOL_SYNC",
                                    "GnssStatus.CONSTELLATION_GLONASS"),
                            timeInNs,
                            "0ms >= X <= 10ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 10);
                } else if ((state & GnssMeasurement.STATE_CODE_LOCK)
                        == GnssMeasurement.STATE_CODE_LOCK) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_CODE_LOCK",
                                    "GnssStatus.CONSTELLATION_GLONASS"),
                            timeInNs,
                            "0ms >= X <= 1ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 1);
                }
                break;
            case GnssStatus.CONSTELLATION_GALILEO:
                if ((state & GnssMeasurement.STATE_TOW_DECODED)
                        == GnssMeasurement.STATE_TOW_DECODED) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_TOW_DECODED",
                                    "GnssStatus.CONSTELLATION_GALILEO"),
                            timeInNs,
                            "0 >= X <= 7 days",
                            String.valueOf(sv_time_days),
                            sv_time_days >= 0 && sv_time_days <= 7);
                } else if ((state & GnssMeasurement.STATE_TOW_KNOWN)
                              == GnssMeasurement.STATE_TOW_KNOWN) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_TOW_DECODED",
                                    "GnssStatus.CONSTELLATION_GALILEO"),
                        timeInNs,
                        "0 >= X <= 7 days",
                        String.valueOf(sv_time_days),
                        sv_time_days >= 0 && sv_time_days <= 7);
                } else if ((state & GnssMeasurement.STATE_GAL_E1B_PAGE_SYNC)
                        == GnssMeasurement.STATE_GAL_E1B_PAGE_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_GAL_E1B_PAGE_SYNC",
                                    "GnssStatus.CONSTELLATION_GALILEO"),
                            timeInNs,
                            "0s >= X <= 2s",
                            String.valueOf(sv_time_sec),
                            sv_time_sec >= 0 && sv_time_sec <= 2);
                } else if ((state & GnssMeasurement.STATE_GAL_E1C_2ND_CODE_LOCK)
                        == GnssMeasurement.STATE_GAL_E1C_2ND_CODE_LOCK) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_GAL_E1C_2ND_CODE_LOCK",
                                    "GnssStatus.CONSTELLATION_GALILEO"),
                            timeInNs,
                            "0ms >= X <= 100ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 100);
                } else if ((state & GnssMeasurement.STATE_GAL_E1BC_CODE_LOCK)
                        == GnssMeasurement.STATE_GAL_E1BC_CODE_LOCK) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_GAL_E1BC_CODE_LOCK",
                                    "GnssStatus.CONSTELLATION_GALILEO"),
                            timeInNs,
                            "0ms >= X <= 4ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 4);
                }
                break;
            case GnssStatus.CONSTELLATION_BEIDOU:
                if ((state & GnssMeasurement.STATE_TOW_DECODED)
                        == GnssMeasurement.STATE_TOW_DECODED) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_TOW_DECODED",
                                    "GnssStatus.CONSTELLATION_BEIDOU"),
                            timeInNs,
                            "0 >= X <= 7 days",
                            String.valueOf(sv_time_days),
                            sv_time_days >= 0 && sv_time_days <= 7);
                } else if ((state & GnssMeasurement.STATE_TOW_KNOWN)
                        == GnssMeasurement.STATE_TOW_KNOWN) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_TOW_KNOWN",
                                    "GnssStatus.CONSTELLATION_BEIDOU"),
                            timeInNs,
                            "0 >= X <= 7 days",
                            String.valueOf(sv_time_days),
                            sv_time_days >= 0 && sv_time_days <= 7);
                } else if ((state & GnssMeasurement.STATE_SUBFRAME_SYNC)
                        == GnssMeasurement.STATE_SUBFRAME_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_SUBFRAME_SYNC",
                                    "GnssStatus.CONSTELLATION_BEIDOU"),
                            timeInNs,
                            "0s >= X <= 6s",
                            String.valueOf(sv_time_sec),
                            sv_time_sec >= 0 && sv_time_sec <= 6);
                } else if ((state & GnssMeasurement.STATE_BDS_D2_SUBFRAME_SYNC)
                        == GnssMeasurement.STATE_BDS_D2_SUBFRAME_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_BDS_D2_SUBFRAME_SYNC",
                                    "GnssStatus.CONSTELLATION_BEIDOU"),
                            timeInNs,
                            "0ms >= X <= 600ms (0.6sec)",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 600);
                } else if ((state & GnssMeasurement.STATE_BIT_SYNC)
                        == GnssMeasurement.STATE_BIT_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_BIT_SYNC",
                                    "GnssStatus.CONSTELLATION_BEIDOU"),
                            timeInNs,
                            "0ms >= X <= 20ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 20);
                } else if ((state & GnssMeasurement.STATE_BDS_D2_BIT_SYNC)
                        == GnssMeasurement.STATE_BDS_D2_BIT_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_BDS_D2_BIT_SYNC",
                                    "GnssStatus.CONSTELLATION_BEIDOU"),
                            timeInNs,
                            "0ms >= X <= 2ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 2);
                } else if ((state & GnssMeasurement.STATE_CODE_LOCK)
                        == GnssMeasurement.STATE_CODE_LOCK) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_CODE_LOCK",
                                    "GnssStatus.CONSTELLATION_BEIDOU"),
                            timeInNs,
                            "0ms >= X <= 1ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 1);
                }
                break;
        }
    }

    private static String getReceivedSvTimeNsLogMessage(String state, String constellationType) {
        return "received_sv_time_ns: Received SV Time-of-Week in ns. Constellation type = "
                + constellationType + ". State = " + state;
    }

    /**
     * Verify sv times are in expected range for given constellation type.
     * This is common check for CONSTELLATION_GPS & CONSTELLATION_QZSS.
     *
     * @param measurement GnssMeasurement
     * @param softAssert  custom SoftAssert
     * @param timeInNs    event time in ns
     * @param state       GnssMeasurement State
     * @param constellationType Gnss Constellation type
     */
    private static void verifyGpsQzssSvTimes(GnssMeasurement measurement,
        SoftAssert softAssert, long timeInNs, int state, String constellationType) {

        long received_sv_time_ns = measurement.getReceivedSvTimeNanos();
        double sv_time_ms = TimeUnit.NANOSECONDS.toMillis(received_sv_time_ns);
        double sv_time_sec = TimeUnit.NANOSECONDS.toSeconds(received_sv_time_ns);
        double sv_time_days = TimeUnit.NANOSECONDS.toDays(received_sv_time_ns);

        if ((state & GnssMeasurement.STATE_TOW_DECODED)
                == GnssMeasurement.STATE_TOW_DECODED) {
            softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                            "GNSS_MEASUREMENT_STATE_TOW_DECODED",
                            constellationType),
                    timeInNs,
                    "0 >= X <= 7 days",
                    String.valueOf(sv_time_days),
                    sv_time_days >= 0 && sv_time_days <= 7);
        } else if ((state & GnssMeasurement.STATE_TOW_KNOWN)
                == GnssMeasurement.STATE_TOW_KNOWN) {
            softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                            "GNSS_MEASUREMENT_STATE_TOW_KNOWN",
                            constellationType),
                    timeInNs,
                    "0 >= X <= 7 days",
                    String.valueOf(sv_time_days),
                    sv_time_days >= 0 && sv_time_days <= 7);
        } else if ((state & GnssMeasurement.STATE_SUBFRAME_SYNC)
                == GnssMeasurement.STATE_SUBFRAME_SYNC) {
            softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                            "GNSS_MEASUREMENT_STATE_SUBFRAME_SYNC",
                            constellationType),
                    timeInNs,
                    "0s >= X <= 6s",
                    String.valueOf(sv_time_sec),
                    sv_time_sec >= 0 && sv_time_sec <= 6);
        } else if ((state & GnssMeasurement.STATE_BIT_SYNC)
                == GnssMeasurement.STATE_BIT_SYNC) {
            softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                    "GNSS_MEASUREMENT_STATE_BIT_SYNC",
                    constellationType),
                    timeInNs,
                    "0ms >= X <= 20ms",
                    String.valueOf(sv_time_ms),
                    sv_time_ms >= 0 && sv_time_ms <= 20);

        } else if ((state & GnssMeasurement.STATE_2ND_CODE_LOCK)
                == GnssMeasurement.STATE_2ND_CODE_LOCK) {
            int maxReceivedSvTimeMs = -1;
            if (isGpsL5OrQzssJ5I(measurement)) {
                maxReceivedSvTimeMs = 10;
            } else if (isGpsL5OrQzssJ5Q(measurement)) {
                maxReceivedSvTimeMs = 20;
            } else if (isGalileoE1C(measurement)) {
                maxReceivedSvTimeMs = 100;
            } else if (isGalileoE5AQ(measurement)) {
                maxReceivedSvTimeMs = 100;
            } else {
                softAssert.assertTrue(
                        "Signal type does not have secondary code but has have "
                                + "STATE_2ND_CODE_LOCK state set. constellation="
                                + measurement.getConstellationType()
                                + ", carrierFrequencyHz=" + measurement.getCarrierFrequencyHz()
                                + ", codeType=" + measurement.getCodeType()
                        , false);
            }

            softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                    "GNSS_MEASUREMENT_STATE_2ND_CODE_LOCK",
                    constellationType),
                    timeInNs,
                    "0ms >= X <= " + maxReceivedSvTimeMs + "ms",
                    String.valueOf(sv_time_ms),
                    sv_time_ms >= 0 && sv_time_ms <= maxReceivedSvTimeMs);
        } else if ((state & GnssMeasurement.STATE_CODE_LOCK)
                == GnssMeasurement.STATE_CODE_LOCK) {
            softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                            "GNSS_MEASUREMENT_STATE_CODE_LOCK",
                            constellationType),
                    timeInNs,
                    "0ms >= X <= 1ms",
                    String.valueOf(sv_time_ms),
                    sv_time_ms >= 0 && sv_time_ms <= 1);
        }
    }


    /**
     * Get a unique string for the SV including the constellation and the default L1 band.
     *
     * @param constellationType Gnss Constellation type
     * @param svId Gnss Sv Identifier
     */
    public static String getUniqueSvStringId(int constellationType, int svId) {
        return getUniqueSvStringId(constellationType, svId, GnssBand.GNSS_L1);
    }

    /**
     * Get a unique string for the SV including the constellation and the band.
     *
     * @param constellationType Gnss Constellation type
     * @param svId Gnss Sv Identifier
     * @param carrierFrequencyHz Carrier Frequency for Sv in Hz
     */
    public static String getUniqueSvStringId(int constellationType, int svId,
        float carrierFrequencyHz) {
        return getUniqueSvStringId(constellationType, svId,
            frequencyToGnssBand(carrierFrequencyHz));
    }

    private static String getUniqueSvStringId(int constellationType, int svId, GnssBand gnssBand) {
        return gnssBand.toString() + "." + constellationType + "." + svId;
    }

    /**
     * Assert all mandatory fields in Gnss Navigation Message are in expected range.
     * See mandatory fields in {@code gps.h}.
     *
     * @param testLocationManager TestLocationManager
     * @param events GnssNavigationMessageEvents
     */
    public static void verifyGnssNavMessageMandatoryField(TestLocationManager testLocationManager,
                                                          List<GnssNavigationMessage> events) {
        // Verify mandatory GnssNavigationMessage field values.
        SoftAssert softAssert = new SoftAssert(TAG);
        for (GnssNavigationMessage message : events) {
            int type = message.getType();
            softAssert.assertTrue("Gnss Navigation Message Type:expected [" +
                getGnssNavMessageTypes() + "] actual = " + type,
                    GNSS_NAVIGATION_MESSAGE_TYPE.contains(type));

            int messageType = message.getType();
            softAssert.assertTrue("Message ID cannot be 0", message.getMessageId() != 0);
            if (messageType == GnssNavigationMessage.TYPE_GAL_I) {
                softAssert.assertTrue("Sub Message ID can not be negative.",
                    message.getSubmessageId() >= 0);
            } else {
                softAssert.assertTrue("Sub Message ID has to be greater than 0.",
                    message.getSubmessageId() > 0);
            }

            // if message type == TYPE_L1CA, verify PRN & Data Size.
            if (messageType == GnssNavigationMessage.TYPE_GPS_L1CA) {
                int svid = message.getSvid();
                softAssert.assertTrue("Space Vehicle ID : expected = [1, 32], actual = " +
                                svid,
                        svid >= 1 && svid <= 32);
                int dataSize = message.getData().length;
                softAssert.assertTrue("Data size: expected = 40, actual = " + dataSize,
                        dataSize == 40);
            } else {
                Log.i(TAG, "GnssNavigationMessage (type = " + messageType
                        + ") skipped for verification.");
            }
        }
        softAssert.assertAll();
    }

    /**
     * Asserts presence of CarrierFrequency and the values are in expected range.
     * As per CDD 7.3.3 / C-3-3 Year 2107+ should have Carrier Frequency present
     * As of 2018, per http://www.navipedia.net/index.php/GNSS_signal, all known GNSS bands
     * lie within 2 frequency ranges [1100-1300] & [1500-1700].
     *
     * @param softAssert custom SoftAssert
     * @param testLocationManager TestLocationManager
     * @param hasCarrierFrequency Whether carrierFrequency is present
     * @param carrierFrequencyHz Value of carrier frequency in Hz if hasCarrierFrequency is true.
     *                              It is ignored when hasCarrierFrequency is false.
     */
    public static void verifyGnssCarrierFrequency(SoftAssert softAssert,
        TestLocationManager testLocationManager,
        boolean hasCarrierFrequency, float carrierFrequencyHz) {

        if (hasCarrierFrequency) {
            float frequencyMhz = carrierFrequencyHz/1e6F;
            softAssert.assertTrue("carrier_frequency_mhz: Carrier frequency in Mhz",
                "1100 < X < 1300 || 1500 < X < 1700",
                String.valueOf(frequencyMhz),
                (frequencyMhz > 1100.0 && frequencyMhz < 1300.0) ||
                    (frequencyMhz > 1500.0 && frequencyMhz < 1700.0));
        }
    }

    private static String getGnssNavMessageTypes() {
        StringBuilder typesStr = new StringBuilder();
        for (int type : GNSS_NAVIGATION_MESSAGE_TYPE) {
            typesStr.append(String.format("0x%04X", type));
            typesStr.append(", ");
        }

        return typesStr.length() > 2 ? typesStr.substring(0, typesStr.length() - 2) : "";
    }

    /**
     * The band information is as of 2018, per http://www.navipedia.net/index.php/GNSS_signal
     * Bands are combined for simplicity as the constellation is also tracked.
     *
     * @param frequencyHz Frequency in Hz
     * @return GnssBand where the frequency lies.
     */
    private static GnssBand frequencyToGnssBand(float frequencyHz) {
        float frequencyMhz = frequencyHz/1e6F;
        if (frequencyMhz >= 1151 && frequencyMhz <= 1214) {
            return GnssBand.GNSS_L5;
        }
        if (frequencyMhz > 1214 && frequencyMhz <= 1255) {
            return GnssBand.GNSS_L2;
        }
        if (frequencyMhz > 1255 && frequencyMhz <= 1300) {
            return GnssBand.GNSS_E6;
        }
        return GnssBand.GNSS_L1; // default to L1 band
    }

    /**
     * Assert most of the fields in Satellite PVT are in expected range.
     *
     * @param measurement GnssMeasurement
     * @param softAssert  custom SoftAssert
     * @param timeInNs    event time in ns
     */
    private static void verifySatellitePvt(GnssMeasurement measurement,
        SoftAssert softAssert, long timeInNs) {
        SatellitePvt satellitePvt = measurement.getSatellitePvt();
        assertNotNull("SatellitePvt cannot be null when HAS_SATELLITE_PVT is true.", satellitePvt);

        if (satellitePvt.hasPositionVelocityClockInfo()){
            assertNotNull("PositionEcef cannot be null when "
                    + "HAS_POSITION_VELOCITY_CLOCK_INFO is true.", satellitePvt.getPositionEcef());
            assertNotNull("VelocityEcef cannot be null when "
                    + "HAS_POSITION_VELOCITY_CLOCK_INFO is true.", satellitePvt.getVelocityEcef());
            assertNotNull("ClockInfo cannot be null when "
                    + "HAS_POSITION_VELOCITY_CLOCK_INFO is true.", satellitePvt.getClockInfo());
            softAssert.assertTrue("x_meters : "
                    + "Satellite position X in WGS84 ECEF (meters)",
                    timeInNs,
                    "-43000000 <= X <= 43000000",
                    String.valueOf(satellitePvt.getPositionEcef().getXMeters()),
                    satellitePvt.getPositionEcef().getXMeters() >= -43000000 &&
                        satellitePvt.getPositionEcef().getXMeters() <= 43000000);
            softAssert.assertTrue("y_meters : "
                    + "Satellite position Y in WGS84 ECEF (meters)",
                    timeInNs,
                    "-43000000 <= X <= 43000000",
                    String.valueOf(satellitePvt.getPositionEcef().getYMeters()),
                    satellitePvt.getPositionEcef().getYMeters() >= -43000000 &&
                        satellitePvt.getPositionEcef().getYMeters() <= 43000000);
            softAssert.assertTrue("z_meters : "
                    + "Satellite position Z in WGS84 ECEF (meters)",
                    timeInNs,
                    "-43000000 <= X <= 43000000",
                    String.valueOf(satellitePvt.getPositionEcef().getZMeters()),
                    satellitePvt.getPositionEcef().getZMeters() >= -43000000 &&
                        satellitePvt.getPositionEcef().getZMeters() <= 43000000);
            softAssert.assertTrue("ure_meters : "
                    + "The Signal in Space User Range Error (URE) (meters)",
                    timeInNs,
                    "X > 0",
                    String.valueOf(satellitePvt.getPositionEcef().getUreMeters()),
                    satellitePvt.getPositionEcef().getUreMeters() > 0);
            softAssert.assertTrue("x_mps : "
                    + "Satellite velocity X in WGS84 ECEF (meters per second)",
                    timeInNs,
                    "-4000 <= X <= 4000",
                    String.valueOf(satellitePvt.getVelocityEcef().getXMetersPerSecond()),
                    satellitePvt.getVelocityEcef().getXMetersPerSecond() >= -4000 &&
                        satellitePvt.getVelocityEcef().getXMetersPerSecond() <= 4000);
            softAssert.assertTrue("y_mps : "
                    + "Satellite velocity Y in WGS84 ECEF (meters per second)",
                    timeInNs,
                    "-4000 <= X <= 4000",
                    String.valueOf(satellitePvt.getVelocityEcef().getYMetersPerSecond()),
                    satellitePvt.getVelocityEcef().getYMetersPerSecond() >= -4000 &&
                        satellitePvt.getVelocityEcef().getYMetersPerSecond() <= 4000);
            softAssert.assertTrue("z_mps : "
                    + "Satellite velocity Z in WGS84 ECEF (meters per second)",
                    timeInNs,
                    "-4000 <= X <= 4000",
                    String.valueOf(satellitePvt.getVelocityEcef().getZMetersPerSecond()),
                    satellitePvt.getVelocityEcef().getZMetersPerSecond() >= -4000 &&
                        satellitePvt.getVelocityEcef().getZMetersPerSecond() <= 4000);
            softAssert.assertTrue("ure_rate_mps : "
                    + "The Signal in Space User Range Error Rate (URE Rate) (meters per second)",
                    timeInNs,
                    "X > 0",
                    String.valueOf(satellitePvt.getVelocityEcef().getUreRateMetersPerSecond()),
                    satellitePvt.getVelocityEcef().getUreRateMetersPerSecond() > 0);
            softAssert.assertTrue("hardware_code_bias_meters : "
                    + "The satellite hardware code bias of the reported code type "
                    + "w.r.t ionosphere-free measurement in meters.",
                    timeInNs,
                    "-17.869 < X < 17.729",
                    String.valueOf(satellitePvt.getClockInfo().getHardwareCodeBiasMeters()),
                    satellitePvt.getClockInfo().getHardwareCodeBiasMeters() > -17.869 &&
                    satellitePvt.getClockInfo().getHardwareCodeBiasMeters() < 17.729);
            softAssert.assertTrue("time_correction_meters : "
                    + "The satellite time correction for ionospheric-free signal measurement "
                    + "(meters)",
                    timeInNs,
                    "-3e6 < X < 3e6",
                    String.valueOf(satellitePvt.getClockInfo().getTimeCorrectionMeters()),
                    satellitePvt.getClockInfo().getTimeCorrectionMeters() > -3e6 &&
                    satellitePvt.getClockInfo().getTimeCorrectionMeters() < 3e6);
            softAssert.assertTrue("clock_drift_mps : "
                    + "The satellite clock drift (meters per second)",
                    timeInNs,
                    "-1.117 < X < 1.117",
                    String.valueOf(satellitePvt.getClockInfo().getClockDriftMetersPerSecond()),
                    satellitePvt.getClockInfo().getClockDriftMetersPerSecond() > -1.117 &&
                    satellitePvt.getClockInfo().getClockDriftMetersPerSecond() < 1.117);
        }

        if (satellitePvt.hasIono()){
            softAssert.assertTrue("iono_delay_meters : "
                    + "The ionospheric delay in meters",
                    timeInNs,
                    "0 < X < 100",
                    String.valueOf(satellitePvt.getIonoDelayMeters()),
                    satellitePvt.getIonoDelayMeters() > 0 &&
                    satellitePvt.getIonoDelayMeters() < 100);
        }

        if (satellitePvt.hasTropo()){
            softAssert.assertTrue("tropo_delay_meters : "
                    + "The tropospheric delay in meters",
                    timeInNs,
                    "0 < X < 100",
                    String.valueOf(satellitePvt.getTropoDelayMeters()),
                    satellitePvt.getTropoDelayMeters() > 0 &&
                    satellitePvt.getTropoDelayMeters() < 100);
        }
    }

    private static boolean isGpsL5OrQzssJ5I(GnssMeasurement measurement) {
        return (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS ||
                measurement.getConstellationType() == GnssStatus.CONSTELLATION_QZSS)
                && GPS_L5_QZSS_J5_FREQ_RANGE_HZ.contains(
                (double) measurement.getCarrierFrequencyHz())
                && measurement.hasCodeType()
                && "I".equals(measurement.getCodeType());
    }

    private static boolean isGpsL5OrQzssJ5Q(GnssMeasurement measurement) {
        return (measurement.getConstellationType() == GnssStatus.CONSTELLATION_GPS ||
                measurement.getConstellationType() == GnssStatus.CONSTELLATION_QZSS)
                && GPS_L5_QZSS_J5_FREQ_RANGE_HZ.contains(
                (double) measurement.getCarrierFrequencyHz())
                && measurement.hasCodeType()
                && "Q".equals(measurement.getCodeType());
    }

    private static boolean isGalileoE1C(GnssMeasurement measurement) {
        return measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO
                && GAL_E1_FREQ_RANGE_HZ.contains((double) measurement.getCarrierFrequencyHz())
                && measurement.hasCodeType()
                && "C".equals(measurement.getCodeType());
    }

    private static boolean isGalileoE5AQ(GnssMeasurement measurement) {
        return measurement.getConstellationType() == GnssStatus.CONSTELLATION_GALILEO
                && GAL_E5_FREQ_RANGE_HZ.contains((double) measurement.getCarrierFrequencyHz())
                && measurement.hasCodeType()
                && "Q".equals(measurement.getCodeType());
    }
}
