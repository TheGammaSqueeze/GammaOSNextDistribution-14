/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.os.nano.OsProtoEnums;

import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.os.nano.AtomsProto;
import com.android.os.nano.StatsLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Android Unit tests for {@link com.android.helpers.ThermalHelper}.
 *
 * <p>To run this test: Disable SELinux with "adb shell setenforce 0"; if this command fails with
 * "Permission denied", try running "adb shell su 0 setenforce 0". Then run: "atest
 * CollectorsHelperTest:com.android.helpers.ThermalHelperTest".
 */
@RunWith(AndroidJUnit4.class)
public class ThermalHelperTest {
    private static final String THROTTLING_KEY =
            MetricUtility.constructKey("thermal", "throttling", "severity");
    private static final String FAKE_SERVICE_DUMP = "F\nA\nK\nE\nThermal Status: 2\nO\nK";
    private static final String SERVICE_DUMP_TEMPLATE =
            "IsStatusOverride: false\n"
                    + "ThermalEventListeners:\n"
                    + "        callbacks: 1\n"
                    + "        killed: false\n"
                    + "        broadcasts count: -1\n"
                    + "ThermalStatusListeners:\n"
                    + "        callbacks: 1\n"
                    + "        killed: false\n"
                    + "        broadcasts count: -1\n"
                    + "Thermal Status: 0\n"
                    + "Cached temperatures:\n"
                    + "        Temperature{mValue=45.67, mType=3, mName=cached temperature sensor,"
                    + " mStatus=0}\n"
                    + "HAL Ready: true\n"
                    + "HAL connection:\n"
                    + "        ThermalHAL 2.0 connected: yes\n"
                    + "Current temperatures from HAL:\n"
                    + "        Temperature{mValue=%s, mType=3, mName=%s, mStatus=0}\n"
                    + "        Temperature{mValue=45.6, mType=3, mName=test temperature sensor2,"
                    + " mStatus=0}\n"
                    + "        Temperature{mValue=56.789, mType=3, mName=test temperature sensor3,"
                    + " mStatus=0}\n"
                    + "Current cooling devices from HAL:\n"
                    + "        CoolingDevice{mValue=100, mType=0, mName=test cooling device}";

    private ThermalHelper mThermalHelper;
    private StatsdHelper mStatsdHelper;
    private UiDevice mDevice;

    @Before
    public void setUp() throws Exception {
        mThermalHelper = new ThermalHelper();
        // Set up the statsd helper to mock statsd calls.
        mStatsdHelper = Mockito.spy(new StatsdHelper());
        mThermalHelper.setStatsdHelper(mStatsdHelper);
        // Set up the fake device for mocking shell commands.
        mDevice = Mockito.mock(UiDevice.class);
        mThermalHelper.setUiDevice(mDevice);
        when(mDevice.executeShellCommand("dumpsys thermalservice")).thenReturn(FAKE_SERVICE_DUMP);
    }

    /** Test registering and unregistering the thermal config. */
    @Test
    public void testThermalConfigRegistration() throws Exception {
        assertTrue(mThermalHelper.startCollecting());
        assertTrue(mThermalHelper.stopCollecting());
    }

    /** Test registering the thermal config fails when no initial throttling value is found. */
    @Test
    public void testThermalConfigRegistration_noInitialValue() throws Exception {
        when(mDevice.executeShellCommand("dumpsys thermalservice")).thenReturn("FAKE RESPONSE");
        assertFalse(mThermalHelper.startCollecting());
    }

    /** Test that only the initial value shows up when there are no events. */
    @Test
    public void testInitialMetricsWithoutEvents() throws Exception {
        when(mStatsdHelper.getEventMetrics()).thenReturn(new ArrayList<StatsLog.EventMetricData>());
        assertTrue(mThermalHelper.startCollecting());

        assertEquals(
                mThermalHelper.getMetrics().get(THROTTLING_KEY).toString(),
                String.valueOf(OsProtoEnums.MODERATE));
        assertTrue(mThermalHelper.stopCollecting());
    }

    /** Test that the initial value and a single event show up from a single metric event. */
    @Test
    public void testSingleEvent() throws Exception {
        when(mStatsdHelper.getEventMetrics())
                .thenReturn(
                        getFakeEventMetrics(
                                getThermalThrottlingSeverityStateChangedEvent(
                                        OsProtoEnums.TEMPERATURE_TYPE_SKIN,
                                        "sensor_name",
                                        OsProtoEnums.LIGHT)));
        assertTrue(mThermalHelper.startCollecting());
        Map<String, StringBuilder> metrics = mThermalHelper.getMetrics();
        assertEquals(
                metrics.get(THROTTLING_KEY).toString(),
                String.join(
                        ",",
                        String.valueOf(OsProtoEnums.MODERATE),
                        String.valueOf(OsProtoEnums.LIGHT)));
        assertTrue(mThermalHelper.stopCollecting());
    }

    /** Test that multiple throttling events with different sources show up together. */
    @Test
    public void testMultipleDifferentEvents() throws Exception {
        when(mStatsdHelper.getEventMetrics())
                .thenReturn(
                        getFakeEventMetrics(
                                getThermalThrottlingSeverityStateChangedEvent(
                                        OsProtoEnums.TEMPERATURE_TYPE_SKIN,
                                        "sensor1_name",
                                        OsProtoEnums.LIGHT),
                                getThermalThrottlingSeverityStateChangedEvent(
                                        OsProtoEnums.TEMPERATURE_TYPE_CPU,
                                        "sensor2_name",
                                        OsProtoEnums.MODERATE),
                                getThermalThrottlingSeverityStateChangedEvent(
                                        OsProtoEnums.TEMPERATURE_TYPE_GPU,
                                        "sensor3_name",
                                        OsProtoEnums.NONE)));

        assertTrue(mThermalHelper.startCollecting());
        Map<String, StringBuilder> metrics = mThermalHelper.getMetrics();
        assertEquals(
                metrics.get(THROTTLING_KEY).toString(),
                String.join(
                        ",",
                        String.valueOf(OsProtoEnums.MODERATE),
                        String.valueOf(OsProtoEnums.LIGHT),
                        String.valueOf(OsProtoEnums.MODERATE),
                        String.valueOf(OsProtoEnums.NONE)));
        assertTrue(mThermalHelper.stopCollecting());
    }

    /** Test that the temperature section is parsed correctly. */
    @Test
    public void testParseTemperature() throws Exception {
        // Use real data for this test. It should work everywhere, except for
        // aosp_cf_x86_64_phone-userdebug.
        mThermalHelper = new ThermalHelper();
        mThermalHelper.setStatsdHelper(mStatsdHelper);
        assertTrue(mThermalHelper.startCollecting());
        Map<String, StringBuilder> metrics = mThermalHelper.getMetrics();
        // Validate at least 2 temperature keys exist with all 3 metrics.
        int statusMetricsFound = 0;
        int valueMetricsFound = 0;
        int typeMetricsFound = 0;
        for (String key : metrics.keySet()) {
            if (!key.startsWith("temperature")) {
                continue;
            }

            if (key.endsWith("status")) {
                statusMetricsFound++;
            } else if (key.endsWith("value")) {
                valueMetricsFound++;
            } else if (key.endsWith("type")) {
                typeMetricsFound++;
            }
        }

        assertTrue(
                "Didn't find at least 2 status, value, and type temperature metrics.",
                statusMetricsFound >= 2 && valueMetricsFound >= 2 && typeMetricsFound >= 2);
        assertTrue(mThermalHelper.stopCollecting());
    }

    /** Test that the mock temperature section is parsed correctly. */
    @Test
    public void testParseTemperatureMock() throws Exception {
        // Use mock data for this test.
        final String correctName = "test temperature sensor";
        final double correctValue = 32.1;
        final String correctOutput =
                String.format(SERVICE_DUMP_TEMPLATE, String.valueOf(correctValue), correctName);
        when(mDevice.executeShellCommand(ThermalHelper.DUMP_THERMALSERVICE_CMD))
                .thenReturn(correctOutput);
        Map<String, StringBuilder> metrics = mThermalHelper.getMetrics();
        // Validate at least 2 temperature keys exist with all 3 metrics.
        int statusMetricsFound = 0;
        int valueMetricsFound = 0;
        int typeMetricsFound = 0;
        for (String key : metrics.keySet()) {
            if (!key.startsWith("temperature")) {
                continue;
            }

            if (key.endsWith("status")) {
                statusMetricsFound++;
            } else if (key.endsWith("value")) {
                valueMetricsFound++;
            } else if (key.endsWith("type")) {
                typeMetricsFound++;
            }
        }

        assertTrue(
                "Didn't find at least 2 status, value, and type temperature metrics.",
                statusMetricsFound >= 2 && valueMetricsFound >= 2 && typeMetricsFound >= 2);
    }

    /** Test getting temperature value from DUT correctly. */
    @Test
    public void testGetTemperature() throws Exception {
        final double THRESHOLD = 0.0001;
        final String correctName = "test temperature sensor";
        final double correctValue = 32.1;
        final String correctOutput =
                String.format(SERVICE_DUMP_TEMPLATE, String.valueOf(correctValue), correctName);

        when(mDevice.executeShellCommand(ThermalHelper.DUMP_THERMALSERVICE_CMD))
                .thenReturn(correctOutput);
        assertTrue(Math.abs(mThermalHelper.getTemperature(correctName) - correctValue) < THRESHOLD);
    }

    /** Test failing to get temperature value from DUT. */
    @Test
    public void testGetTemperatureFailed() throws Exception {
        final String correctName = "test temperature sensor";
        final double correctValue = 32.1;
        final String correctOutput =
                String.format(SERVICE_DUMP_TEMPLATE, String.valueOf(correctValue), correctName);
        final String expectedMessage = "Failed to get temperature of ";

        final String badName = "bad temperature sensor";
        when(mDevice.executeShellCommand(ThermalHelper.DUMP_THERMALSERVICE_CMD))
                .thenReturn(correctOutput);
        Exception exception1 =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            mThermalHelper.getTemperature(badName);
                        });
        assertTrue(exception1.getMessage().contains(expectedMessage));

        final String badOutput = String.format(SERVICE_DUMP_TEMPLATE, "bad", correctName);
        when(mDevice.executeShellCommand(ThermalHelper.DUMP_THERMALSERVICE_CMD))
                .thenReturn(badOutput);
        Exception exception2 =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            mThermalHelper.getTemperature(correctName);
                        });
        assertTrue(exception2.getMessage().contains(expectedMessage));

        final String badOutputEmpty = String.format(SERVICE_DUMP_TEMPLATE, "", correctName);
        when(mDevice.executeShellCommand(ThermalHelper.DUMP_THERMALSERVICE_CMD))
                .thenReturn(badOutputEmpty);
        Exception exception3 =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            mThermalHelper.getTemperature(correctName);
                        });
        assertTrue(exception3.getMessage().contains(expectedMessage));
    }

    /**
     * Returns a list of {@link com.android.os.nano.StatsLog.EventMetricData} that statsd returns.
     */
    private List<StatsLog.EventMetricData> getFakeEventMetrics(
            AtomsProto.ThermalThrottlingSeverityStateChanged... throttleSeverityEvents) {
        List<StatsLog.EventMetricData> result = new ArrayList<>();
        for (AtomsProto.ThermalThrottlingSeverityStateChanged event : throttleSeverityEvents) {
            AtomsProto.Atom atom = new AtomsProto.Atom();
            atom.setThermalThrottlingSeverityStateChanged(event);
            StatsLog.EventMetricData metricData = new StatsLog.EventMetricData();
            metricData.atom = atom;
            result.add(metricData);
        }
        return result;
    }

    /** Returns a state change protobuf for thermal throttling severity. */
    private AtomsProto.ThermalThrottlingSeverityStateChanged
            getThermalThrottlingSeverityStateChangedEvent(int type, String name, int severity) {
        AtomsProto.ThermalThrottlingSeverityStateChanged stateChanged =
                new AtomsProto.ThermalThrottlingSeverityStateChanged();

        stateChanged.sensorType = type;
        stateChanged.sensorName = name;
        stateChanged.severity = severity;
        return stateChanged;
    }

    /** Get the thermal metric key for a thermal sensor type and name. */
    private String getMetricKey(int type, String name) {
        return MetricUtility.constructKey(
                "thermal", ThermalHelper.getShorthandSensorType(type), name);
    }

    @After
    public void tearDown() {
        mThermalHelper.stopCollecting();
    }
}
