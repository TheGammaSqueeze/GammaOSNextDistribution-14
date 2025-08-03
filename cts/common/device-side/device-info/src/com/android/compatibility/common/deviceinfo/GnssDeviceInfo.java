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

package com.android.compatibility.common.deviceinfo;

import android.content.Context;
import android.location.GnssCapabilities;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.LocationManager;
import android.os.Build;

import com.android.compatibility.common.util.DeviceInfoStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Gnss device info collector.
 */
public final class GnssDeviceInfo extends DeviceInfo {

    private static final String LOG_TAG = "GnssDeviceInfo";
    private static final String UNKNOWN_GNSS_NAME = "unknown";
    private static final String NO_GNSS_HARDWARE = "no_gnss_hardware";
    public static final int ADR_STATE_VALID = (1 << 0);

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        LocationManager locationManager =
                (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);

        if (locationManager == null) {
            store.addResult("gnss_hardware_model_name", UNKNOWN_GNSS_NAME);
            return;
        }
        collectGnssHardwareModelName(store, locationManager);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            collectGnssCapabilities(store, locationManager.getGnssCapabilities());
        }
        collectAccumulatedDeltaRangeMeasurements(store, locationManager);
    }

    /**
     * collect info for gnss hardware model name. If there is no GNSS hardware, the function stores
     * "unknown". If there is no name available, the function stores "no_gnss_hardware".
     */
    private void collectGnssHardwareModelName(
            DeviceInfoStore store, LocationManager locationManager) throws IOException {
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            store.addResult("gnss_hardware_model_name", NO_GNSS_HARDWARE);
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }
        String gnssHardwareModelName = locationManager.getGnssHardwareModelName();
        if (gnssHardwareModelName == null) {
            store.addResult("gnss_hardware_model_name", UNKNOWN_GNSS_NAME);
            return;
        }
        store.addResult("gnss_hardware_model_name", gnssHardwareModelName);
    }

    /** collect info for gnss capabilities into a group */
    private void collectGnssCapabilities(DeviceInfoStore store, GnssCapabilities gnssCapabilities)
            throws IOException {
        store.startGroup("gnss_capabilities");

        store.addResult("has_low_power_mode", gnssCapabilities.hasLowPowerMode());
        store.addResult("has_geofencing", gnssCapabilities.hasGeofencing());
        store.addResult("has_measurements", gnssCapabilities.hasMeasurements());
        store.addResult(
                "has_measurement_corrections", gnssCapabilities.hasMeasurementCorrections());
        store.addResult(
                "has_measurement_corrections_los_sats",
                gnssCapabilities.hasMeasurementCorrectionsLosSats());
        store.addResult(
                "has_measurement_corrections_excess_path_length",
                gnssCapabilities.hasMeasurementCorrectionsExcessPathLength());

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            store.addResult("has_satellite_blocklist", gnssCapabilities.hasSatelliteBlacklist());
            store.addResult("has_navigation_messages", gnssCapabilities.hasNavMessages());
            store.addResult(
                    "has_measurement_corrections_reflecting_plane",
                    gnssCapabilities.hasMeasurementCorrectionsReflectingPane());
        } else {
            store.addResult("has_satellite_blocklist", gnssCapabilities.hasSatelliteBlocklist());
            store.addResult("has_navigation_messages", gnssCapabilities.hasNavigationMessages());
            store.addResult(
                    "has_measurement_corrections_reflecting_plane",
                    gnssCapabilities.hasMeasurementCorrectionsReflectingPlane());
        }

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            store.addResult("has_antenna_info", gnssCapabilities.hasGnssAntennaInfo());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            store.addResult("has_antenna_info", gnssCapabilities.hasAntennaInfo());
            store.addResult("has_satellite_pvt", gnssCapabilities.hasSatellitePvt());
            store.addResult(
                    "has_measurement_correlation_vectors",
                    gnssCapabilities.hasMeasurementCorrelationVectors());
            store.addResult(
                    "has_measurement_corrections_for_driving",
                    gnssCapabilities.hasMeasurementCorrectionsForDriving());
        }
        store.endGroup();
    }

    /**
     * Collect Accumulated Delta Range info:
     * 1. Start measurement with 1s interval and wait for up to 10 measurement events.
     * 2. Set as true if a measurement has a valid AccumulatedDeltaRange state, false otherwise
     */
    private void collectAccumulatedDeltaRangeMeasurements(DeviceInfoStore store,
            LocationManager locationManager) throws InterruptedException, IOException {
        final int gnssMeasurementsEventsToCollect = 10;
        TestGnssMeasurementListener mMeasurementListener = new TestGnssMeasurementListener(
                gnssMeasurementsEventsToCollect);
        locationManager.registerGnssMeasurementsCallback(mMeasurementListener);
        mMeasurementListener.waitFor();
        boolean hasAccumulatedDeltaRange = false;
        for (GnssMeasurementsEvent event : mMeasurementListener.getEvents()) {
            for (GnssMeasurement measurement : event.getMeasurements()) {
                if ((measurement.getAccumulatedDeltaRangeState() & ADR_STATE_VALID)
                        == ADR_STATE_VALID) {
                    hasAccumulatedDeltaRange = true;
                    break;
                }
                if (hasAccumulatedDeltaRange) break;
            }
        }
        locationManager.unregisterGnssMeasurementsCallback(mMeasurementListener);
        store.addResult("has_valid_accumulated_delta_range", hasAccumulatedDeltaRange);
    }

    private class TestGnssMeasurementListener extends GnssMeasurementsEvent.Callback {
        public static final int MEAS_TIMEOUT_IN_SEC = 5;
        private final List<GnssMeasurementsEvent> mMeasurementsEvents;
        private final CountDownLatch mCountDownLatch;
        private static final long STANDARD_WAIT_TIME_MS = 50;
        private static final long STANDARD_SLEEP_TIME_MS = 50;

        /**
         * Constructor for TestGnssMeasurementListener
         *
         * @param eventsToCollect wait until this number of events collected.
         */
        TestGnssMeasurementListener(int eventsToCollect) {
            mCountDownLatch = new CountDownLatch(eventsToCollect);
            mMeasurementsEvents = new ArrayList<>(eventsToCollect);
        }

        @Override
        public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
            if (event.getMeasurements().size() > 0) {
                synchronized (mMeasurementsEvents) {
                    mMeasurementsEvents.add(event);
                }
                mCountDownLatch.countDown();
            }
        }

        /**
         * Get the current list of GPS Measurements Events.
         *
         * @return the current list of GPS Measurements Events
         */
        public List<GnssMeasurementsEvent> getEvents() {
            synchronized (mMeasurementsEvents) {
                List<GnssMeasurementsEvent> clone = new ArrayList<>();
                clone.addAll(mMeasurementsEvents);
                return clone;
            }
        }

        public boolean waitFor() throws InterruptedException {
            long waitTimeRounds = (TimeUnit.SECONDS.toMillis(MEAS_TIMEOUT_IN_SEC))
                    / (STANDARD_WAIT_TIME_MS + STANDARD_SLEEP_TIME_MS);
            for (int i = 0; i < waitTimeRounds; ++i) {
                Thread.sleep(STANDARD_SLEEP_TIME_MS);
                if (mCountDownLatch.await(STANDARD_WAIT_TIME_MS, TimeUnit.MILLISECONDS)) {
                    return true;
                }
            }
            return false;
        }
    }
}
