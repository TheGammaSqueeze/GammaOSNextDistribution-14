/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.chre.test.chreconcurrency;

import static com.google.common.truth.Truth.assertThat;

import android.hardware.location.NanoAppBinary;

import com.google.android.chre.test.chqts.ContextHubChreApiTestExecutor;
import com.google.android.utils.chre.ChreApiTestUtil;

import org.junit.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import dev.chre.rpc.proto.ChreApiTest;

/**
 * A class that can execute the CHRE BLE concurrency test.
 */
public class ContextHubChreConcurrencyTestExecutor extends ContextHubChreApiTestExecutor {
    private static final String TAG = "ContextHubChreConcurrencyTestExecutor";

    private static final int CHRE_EVENT_SENSOR_ACCELEROMETER_DATA = 0x0100 + 1;

    private static final int CHRE_EVENT_SENSOR_SAMPLING_CHANGE = 0x200;

    private static final int CHRE_SENSOR_CONFIGURE_RAW_POWER_ON = 1 << 0;

    private static final int CHRE_SENSOR_CONFIGURE_RAW_REPORT_CONTINUOUS = 1 << 1;

    private static final int CHRE_SENSOR_CONFIGURE_MODE_CONTINUOUS =
            CHRE_SENSOR_CONFIGURE_RAW_POWER_ON | CHRE_SENSOR_CONFIGURE_RAW_REPORT_CONTINUOUS;

    private static final int CHRE_SENSOR_TYPE_ACCELEROMETER = 1;

    private static final int NUM_EVENTS_TO_GATHER = 10;

    private static final long TIMEOUT_IN_NS = 5000000000L;

    /**
     * The multiplier used to allow for jitter in the intervals of the samples. This is
     * multiplied against the requested interval between samples to produce the final interval
     * that is compared with the real interval between samples. This allows for 5% of jitter.
     */
    private static final double JITTER_MULTIPLIER = 1.05;

    /**
     * The sum of the intervals in ns and timestamp count. Used to calculate the average interval.
     */
    private double mIntervalSumNs = 0;
    private int mTimestampCount = 0;

    public ContextHubChreConcurrencyTestExecutor(NanoAppBinary nanoapp, NanoAppBinary nanoapp2) {
        super(Arrays.asList(nanoapp, nanoapp2));
    }

    /**
     * Tests for accelerometer data concurrency.
     */
    public void runAccelerometerConcurrencyTest() throws Exception {
        Future<List<List<ChreApiTest.GeneralEventsMessage>>> eventsFuture =
                new ChreApiTestUtil().gatherEventsConcurrent(mRpcClients,
                        Arrays.asList(CHRE_EVENT_SENSOR_ACCELEROMETER_DATA,
                                      CHRE_EVENT_SENSOR_SAMPLING_CHANGE),
                        NUM_EVENTS_TO_GATHER,
                        TIMEOUT_IN_NS);

        int accelerometerHandle = findDefaultAccelerometer();
        long minInterval = getMinIntervalForAccelerometer(accelerometerHandle);

        List<ChreApiTest.ChreSensorConfigureInput> configureInputs =
                assertAccelerometerIsConfigured(accelerometerHandle, minInterval);

        List<List<ChreApiTest.GeneralEventsMessage>> events =
                eventsFuture.get(2 * TIMEOUT_IN_NS, TimeUnit.NANOSECONDS);
        assertThat(events).isNotNull();

        for (int i = 0; i < events.size(); ++i) {
            List<ChreApiTest.GeneralEventsMessage> eventsFromNanoapp = events.get(i);
            assertThat(eventsFromNanoapp).isNotNull();
            Assert.assertEquals(eventsFromNanoapp.size(), NUM_EVENTS_TO_GATHER);
            assertSensorIntervalsAreCorrect(accelerometerHandle, eventsFromNanoapp,
                    configureInputs.get(i).getInterval());
        }
    }

    /**
     * Finds the default accelerometer for each nanoapp then asserts that it exists and both
     * handles are the same.
     *
     * @return                              the accelerometer handle.
     */
    private int findDefaultAccelerometer() throws Exception {
        ChreApiTest.ChreSensorFindDefaultInput input = ChreApiTest.ChreSensorFindDefaultInput
                .newBuilder().setSensorType(CHRE_SENSOR_TYPE_ACCELEROMETER).build();
        List<ChreApiTest.ChreSensorFindDefaultOutput> responses =
                ChreApiTestUtil.callConcurrentUnaryRpcMethodSync(mRpcClients,
                        "chre.rpc.ChreApiTestService.ChreSensorFindDefault", input);
        assertThat(responses).isNotNull();

        Integer accelerometerHandle = null;
        for (ChreApiTest.ChreSensorFindDefaultOutput response: responses) {
            Assert.assertTrue(response.getFoundSensor());
            if (accelerometerHandle == null) {
                accelerometerHandle = response.getSensorHandle();
            } else {
                Assert.assertEquals(accelerometerHandle.intValue(), response.getSensorHandle());
            }
        }
        assertThat(accelerometerHandle).isNotNull();
        return accelerometerHandle;
    }

    /**
     * Gets the minimum interval for the accelerometer and asserts that it is the same
     * for all nanoapps.
     *
     * @param accelerometerHandle           the accelerometer handle.
     * @return                              the minimum interval.
     */
    private long getMinIntervalForAccelerometer(int accelerometerHandle) throws Exception {
        ChreApiTest.ChreHandleInput input = ChreApiTest.ChreHandleInput.newBuilder()
                .setHandle(accelerometerHandle)
                .build();
        List<ChreApiTest.ChreGetSensorInfoOutput> accelerometerInfos =
                ChreApiTestUtil.callConcurrentUnaryRpcMethodSync(mRpcClients,
                        "chre.rpc.ChreApiTestService.ChreGetSensorInfo", input);
        assertThat(accelerometerInfos).isNotNull();

        Long minInterval = null;
        for (ChreApiTest.ChreGetSensorInfoOutput accelerometerInfo: accelerometerInfos) {
            if (minInterval == null) {
                minInterval = accelerometerInfo.getMinInterval();
            } else {
                Assert.assertEquals(minInterval.longValue(), accelerometerInfo.getMinInterval());
            }
        }

        assertThat(minInterval).isNotNull();
        return minInterval;
    }

    /**
     * Configures the accelerometer with two different intervals corresponding to each
     * nanoapp. Asserts this was successful and returns the input used to configure.
     *
     * @param accelerometerHandle           the accelerometer handle.
     * @param minInterval                   the minimum interval for the accelerometer.
     *
     * @return                              the configuration input.
     */
    private List<ChreApiTest.ChreSensorConfigureInput> assertAccelerometerIsConfigured(
                int accelerometerHandle, long minInterval) throws Exception {
        List<ChreApiTest.ChreSensorConfigureInput> configureInputs = Arrays.asList(
                ChreApiTest.ChreSensorConfigureInput.newBuilder()
                        .setSensorHandle(accelerometerHandle)
                        .setMode(CHRE_SENSOR_CONFIGURE_MODE_CONTINUOUS)
                        .setLatency(0)
                        .setInterval(minInterval)
                        .build(),
                ChreApiTest.ChreSensorConfigureInput.newBuilder()
                    .setSensorHandle(accelerometerHandle)
                    .setMode(CHRE_SENSOR_CONFIGURE_MODE_CONTINUOUS)
                    .setLatency(0)
                    .setInterval(minInterval * 2)
                    .build()
        );

        List<ChreApiTest.Status> responses =
                ChreApiTestUtil.callConcurrentUnaryRpcMethodSync(mRpcClients,
                "chre.rpc.ChreApiTestService.ChreSensorConfigure", configureInputs);
        assertThat(responses).isNotNull();
        for (ChreApiTest.Status response: responses) {
            Assert.assertTrue(response.getStatus());
        }

        return configureInputs;
    }

    /**
     * Verifies the intervals between different samples and different readings in each sample
     * are less than the requested interval.
     *
     * @param accelerometerHandle           the handle for the accelerometer.
     * @param events                        the events received from the nanoapps.
     * @param requestedIntervalNs           the interval requested when configuring the
     *                                      accelerometers.
     */
    private void assertSensorIntervalsAreCorrect(
            int accelerometerHandle,
            List<ChreApiTest.GeneralEventsMessage> events,
            long requestedIntervalNs) {
        assertThat(events).isNotNull();

        Long lastReadingTimestamp = null;
        mIntervalSumNs = 0;
        mTimestampCount = 0;
        for (ChreApiTest.GeneralEventsMessage event: events) {
            Assert.assertTrue(event.getStatus());
            if (event.hasChreSensorThreeAxisData()) {
                lastReadingTimestamp = handleChreSensorThreeAxisData(event, requestedIntervalNs,
                        lastReadingTimestamp, accelerometerHandle);
            } else if (event.hasChreSensorSamplingStatusEvent()) {
                // The interval will change due to the sampling status event.
                // Assert the past data was good and continue with fresh data.
                assertAverageIntervalIsLessThanOrEqualToTheRequestedInterval(requestedIntervalNs);

                mIntervalSumNs = 0;
                mTimestampCount = 0;
                requestedIntervalNs = handleChreSensorSamplingStatusEvent(event,
                        requestedIntervalNs, accelerometerHandle);
            } else {
                Assert.fail("Event does not contain any requested data.");
            }
        }

        assertAverageIntervalIsLessThanOrEqualToTheRequestedInterval(requestedIntervalNs);
    }

    /**
     * Handles ChreSensorThreeAxisData events.
     *
     * @param event                         the general event.
     * @param requestedIntervalNs           the requestd interval in ns.
     * @param lastReadingTimestamp          the timestamp of the last reading.
     * @param accelerometerHandle           the accelerometer handle.
     * @return                              the last reading timestamp.
     */
    private Long handleChreSensorThreeAxisData(ChreApiTest.GeneralEventsMessage event,
            long requestedIntervalNs, Long lastReadingTimestamp, int accelerometerHandle) {
        ChreApiTest.ChreSensorThreeAxisData data = event.getChreSensorThreeAxisData();
        Assert.assertEquals(data.getHeader().getSensorHandle(), accelerometerHandle);
        assertThat(data.getReadingsCount()).isGreaterThan(0);

        for (int i = 0; i < data.getReadingsCount(); ++i) {
            ChreApiTest.ChreSensorThreeAxisSampleData sampleData = data.getReadings(i);

            // baseTimestamp is the timestamp provided in the event, and to calculate
            // each reading's timestamp, one must sum, in order, the timestampDelta
            // up to the current reading.
            long readingTimestamp = (i == 0
                    ? data.getHeader().getBaseTimestamp()
                    : lastReadingTimestamp) + sampleData.getTimestampDelta();
            if (lastReadingTimestamp != null) {
                mIntervalSumNs += readingTimestamp - lastReadingTimestamp;
                ++mTimestampCount;
            }
            lastReadingTimestamp = readingTimestamp;
        }
        return lastReadingTimestamp;
    }

    /**
     * Handles ChreSensorSamplingStatusEvent events.
     *
     * @param event                         the general event.
     * @param requestedIntervalNs           the requested interval in ns.
     * @param accelerometerHandle           the accelerometer handle.
     * @return                              the new interval determined by
     *                                      the sampling status data.
     */
    private long handleChreSensorSamplingStatusEvent(ChreApiTest.GeneralEventsMessage event,
            long requestedIntervalNs, int accelerometerHandle) {
        ChreApiTest.ChreSensorSamplingStatusEvent samplingStatusEvent =
                event.getChreSensorSamplingStatusEvent();
        Assert.assertEquals(samplingStatusEvent.getSensorHandle(), accelerometerHandle);
        ChreApiTest.ChreSensorSamplingStatus samplingStatus = samplingStatusEvent.getStatus();
        return samplingStatus.getEnabled() ? samplingStatus.getInterval() : requestedIntervalNs;
    }

    /**
     * Asserts that the average interval is less than the requested interval (both in ns),
     * accounting for jitter.
     *
     * @param requestedIntervalNs           the requested interval in ns.
     */
    private void assertAverageIntervalIsLessThanOrEqualToTheRequestedInterval(
                long requestedIntervalNs) {
        if (mTimestampCount <= 0) {
            return;
        }

        double averageIntervalNs = mIntervalSumNs / mTimestampCount;
        Assert.assertTrue("Invalid average timestamp between samples: averageIntervalNs: "
                + averageIntervalNs + ", requestedIntervalNs: " + requestedIntervalNs,
                averageIntervalNs <= requestedIntervalNs * JITTER_MULTIPLIER);
    }
}
