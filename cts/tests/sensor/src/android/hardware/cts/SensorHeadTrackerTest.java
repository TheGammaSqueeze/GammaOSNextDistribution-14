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

package android.hardware.cts;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventCallback;
import android.hardware.SensorManager;
import android.hardware.cts.accessories.VirtualHeadTracker;
import android.hardware.cts.helpers.SensorTestStateNotSupportedException;
import android.hardware.sensor.cts.R;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PropertyUtil;
import com.android.compatibility.common.util.SystemUtil;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@AppModeFull(reason = "Test requires special permission not available to instant apps")
public class SensorHeadTrackerTest extends SensorTestCase {
    private static final String TAG = "SensorHeadTrackerTest";

    private SensorManager mSensorManager;
    private boolean mHasSensorDynamicHeadTracker = false;
    private Callback mCallback;
    private Integer mSensorId;
    private VirtualHeadTracker mVirtualHeadTracker;
    private static final String VIRTUAL_HEAD_TRACKER_PRIMARY = "Emulated HT";
    private static final String VIRTUAL_HEAD_TRACKER_SECONDARY = "Emulated HT Secondary";
    private static final int CONNECTION_TIMEOUT_SEC = 5;
    private static final int DISCONNECTION_TIMEOUT_SEC = 5;
    private static final int FLUSH_COMPLETED_TIMEOUT_SEC = 5;
    private static final int EVENT_TIMEOUT_SEC = 5;

    @Override
    protected void setUp() throws InterruptedException {
        if (PropertyUtil.getVsrApiLevel() < Build.VERSION_CODES.TIRAMISU) {
            throw new SensorTestStateNotSupportedException(
                    "Test only applies for HAL version T or later, skip.");
        }
        configureHtSensorAccess(true);
        mSensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        mHasSensorDynamicHeadTracker =
                getContext()
                        .getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_SENSOR_DYNAMIC_HEAD_TRACKER);
        Log.d(TAG, String.format("mHasSensorDynamicHeadTracker %b", mHasSensorDynamicHeadTracker));

        mCallback = new Callback();
        mVirtualHeadTracker = new VirtualHeadTracker();

        mSensorManager.registerDynamicSensorCallback(mCallback, mVirtualHeadTracker.handler);

        mVirtualHeadTracker.registerDevice(R.raw.head_tracker_main);

        try {
            featureSupportedOrSkip();
        } catch (SensorTestStateNotSupportedException e) {
            return;
        }
    }

    @Override
    protected void tearDown() {
        Log.d(TAG, "Teardown.");
        mVirtualHeadTracker.closeDevice();

        configureHtSensorAccess(false);
    }

    @CddTest(requirements = {"7.3"})
    public void testIsNotDynamicSensorDiscoverySupported() {

        if (!mHasSensorDynamicHeadTracker && mSensorManager.isDynamicSensorDiscoverySupported()) {
            assertFalse(
                    "Discovered head tracker sensor but feature flag is not set.",
                    mCallback.waitForConnection(VIRTUAL_HEAD_TRACKER_PRIMARY));
        }
    }

    @CddTest(requirements = {"7.3"})
    public void testIsDynamicSensorDiscoverySupported() {
        featureSupportedOrSkip();

        assertTrue("Cannot detect sensor connection.",
                mCallback.waitForConnection(VIRTUAL_HEAD_TRACKER_PRIMARY));

        assertTrue(
                "Dynamic sensor discovery is not supported.",
                mSensorManager.isDynamicSensorDiscoverySupported());
    }

    @CddTest(requirements = {"7.3"})
    public void testRegisterDynamicSensorCallback() {
        featureSupportedOrSkip();

        mVirtualHeadTracker.closeDevice();

        Log.d(
                TAG,
                String.format(
                        "Fake head tracker sensor has %d seconds to connect.",
                        CONNECTION_TIMEOUT_SEC));

        mVirtualHeadTracker.registerDevice(R.raw.head_tracker_main);

        assertTrue("Cannot detect sensor connection.",
                mCallback.waitForConnection(VIRTUAL_HEAD_TRACKER_PRIMARY));

        assertTrue(
                String.format(
                        "Sensor type is %d. Sensor type should be %d",
                        mCallback.mSensor.getType(), Sensor.TYPE_HEAD_TRACKER),
                mCallback.isTypeHeadTracker());

        assertTrue("Sensor is a wake up sensor.", !mCallback.isNotWakeUpSensor());

        assertTrue("Sensor is not a dynamic sensor.", mCallback.isDynamicSensor());

        assertTrue(
                String.format("Sensor id is %d. Must not be 0 or 1.", mCallback.mSensor.getId()),
                mCallback.checkId());

        assertTrue(
                String.format(
                        "Reporting mode is %d. Reporting mode should be %d.",
                        mCallback.mSensor.getReportingMode(), Sensor.REPORTING_MODE_CONTINUOUS),
                mCallback.isReportingModeContinuous());

        assertTrue(
                String.format(
                        "Sensor string type is %s. Sensor string should be %s.",
                        mCallback.mSensor.getStringType(), Sensor.STRING_TYPE_HEAD_TRACKER),
                mCallback.isStringTypeHeadTracker());

        assertTrue("Sensor name is incorrect.", mCallback.isUhidName());

        assertTrue("Sensor is not in dynamic sensor list.", mCallback.isSensorInList());
    }

    @CddTest(requirements = {"7.3"})
    public void testArrayOfSixFloats() {
        featureSupportedOrSkip();

        assertTrue("Cannot detect sensor connection.",
                mCallback.waitForConnection(VIRTUAL_HEAD_TRACKER_PRIMARY));

        mCallback.headTrackerData();
    }

    @CddTest(requirements = {"7.3"})
    public void testDiscontinuity() {
        featureSupportedOrSkip();

        assertTrue("Cannot detect sensor connection.",
                mCallback.waitForConnection(VIRTUAL_HEAD_TRACKER_PRIMARY));

        mVirtualHeadTracker.incDiscontinuityCount();

        assertTrue(
                "First event after discontinuity was not called.", mCallback.discontinuityCount());
    }

    @CddTest(requirements = {"7.3"})
    public void testSensorManagerFlush() {
        featureSupportedOrSkip();

        assertTrue("Cannot detect sensor connection.",
                mCallback.waitForConnection(VIRTUAL_HEAD_TRACKER_PRIMARY));

        assertTrue("Flush was not completed within five seconds.", mCallback.waitForFlush());
    }

    @CddTest(requirements = {"7.3"})
    public void testDisconnectionReconnection() {
        featureSupportedOrSkip();

        assertTrue("Cannot detect sensor connection.",
                mCallback.waitForConnection(VIRTUAL_HEAD_TRACKER_PRIMARY));

        mSensorId = mCallback.getSensorId();

        mVirtualHeadTracker.closeDevice();

        assertTrue("Device was not disconnected.", mCallback.waitForDisconnection());

        mVirtualHeadTracker.registerDevice(R.raw.head_tracker_main);

        assertTrue("Cannot detect sensor reconnection.",
                mCallback.waitForConnection(VIRTUAL_HEAD_TRACKER_PRIMARY));

        Integer sensorId = mCallback.getSensorId();
        boolean match =
                mSensorId != null
                        && sensorId != null
                        && sensorId.intValue() == mSensorId.intValue();

        assertTrue("ID mismatch for the reconnected sensor.", match);
    }

    @CddTest(requirements = {"7.3"})
    public void testAddSecondSensor() {
        featureSupportedOrSkip();

        assertTrue(
                "Cannot detect sensor connection.",
                mCallback.waitForConnection(VIRTUAL_HEAD_TRACKER_PRIMARY));

        Callback secondCallback = new Callback();
        VirtualHeadTracker virtualHeadTrackerTwo = new VirtualHeadTracker();

        mSensorManager.registerDynamicSensorCallback(secondCallback, virtualHeadTrackerTwo.handler);
        virtualHeadTrackerTwo.registerDevice(R.raw.head_tracker_distinct_id);
        try {
            assertTrue(
                    "Cannot detect second sensor connection.",
                    secondCallback.waitForConnection(VIRTUAL_HEAD_TRACKER_SECONDARY));
            assertTrue(
                    String.format(
                            "Sensor id is %d. Must not be 0 or 1.", mCallback.mSensor.getId()),
                    mCallback.checkId());

            assertTrue(
                    String.format(
                            "Sensor id is %d. Must not be 0 or 1.", secondCallback.mSensor.getId()),
                    secondCallback.checkId());

            assertNotEquals(
                    "Sensors have the same id.",
                    secondCallback.getSensorId(),
                    mCallback.getSensorId());
        } finally {
            if (Objects.nonNull(virtualHeadTrackerTwo)) {
                virtualHeadTrackerTwo.closeDevice();
            }
        }
    }

    private class Callback extends SensorManager.DynamicSensorCallback {

        Sensor mSensor = null;
        private boolean mFirstDiscontinuityReported;
        private int mDuplicateDiscontinuityCount = 0;
        private String mExpectedSensorName;
        private CountDownLatch mConnectLatch;
        private CountDownLatch mDisconnectLatch;
        private final String mEmulatorName = "Emulated HT";

        @Override
        public void onDynamicSensorConnected(Sensor sensor) {
            Log.d(TAG, "Sensor Connected: " + sensor);
            if (mExpectedSensorName == null || mExpectedSensorName == sensor.getName()) {
                mSensor = sensor;
                if (mConnectLatch != null) {
                    mConnectLatch.countDown();
                }
            }
        }

        @Override
        public void onDynamicSensorDisconnected(Sensor sensor) {
            Log.d(TAG, "Sensor disconnected: " + sensor);
            if (mSensor == sensor) {
                mSensor = null;
                if (mDisconnectLatch != null) {
                    mDisconnectLatch.countDown();
                }
            }
        }

        public boolean waitForConnection(String sensorName) {
            boolean ret;
            mExpectedSensorName = sensorName;
            mConnectLatch = new CountDownLatch(1);
            try {
                ret = mConnectLatch.await(CONNECTION_TIMEOUT_SEC, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                ret = false;
                Thread.currentThread().interrupt();
            } finally {
                mConnectLatch = null;
            }
            return ret;
        }

        public boolean waitForDisconnection() {
            boolean ret;
            mDisconnectLatch = new CountDownLatch(1);
            try {
                ret = mDisconnectLatch.await(DISCONNECTION_TIMEOUT_SEC, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                ret = false;
                Thread.currentThread().interrupt();
            } finally {
                mDisconnectLatch = null;
            }
            return ret;
        }

        public boolean waitForFlush() {
            final CountDownLatch eventLatch = new CountDownLatch(1);

            SensorEventCallback eventCallback =
                    new SensorEventCallback() {
                        @Override
                        public void onFlushCompleted(Sensor s) {
                            eventLatch.countDown();
                        }
                    };

            assertTrue(
                    "Register listener is false in waitForFlush().",
                    mSensorManager.registerListener(
                            eventCallback, mSensor, SensorManager.SENSOR_DELAY_NORMAL));

            mSensorManager.flush(eventCallback);

            boolean flush;
            try {
                flush = eventLatch.await(FLUSH_COMPLETED_TIMEOUT_SEC, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                flush = false;
                Thread.currentThread().interrupt();
            } finally {
                mSensorManager.unregisterListener(eventCallback);
            }
            return flush;
        }

        private class SensorEventCopy {
            private float[] mValues;
        }

        SensorEventCopy getOneSensorEvent() {
            SensorEventCopy eventCopy = new SensorEventCopy();

            final CountDownLatch eventLatch = new CountDownLatch(1);
            SensorEventCallback eventCallback =
                    new SensorEventCallback() {
                        public void onSensorChanged(SensorEvent event) {
                            if (eventLatch.getCount() == 1) {
                                eventCopy.mValues = event.values.clone();
                                eventLatch.countDown();
                            }
                        }
                    };

            assertTrue(
                    "Register listener is false in getOneSensorEvent().",
                    mSensorManager.registerListener(
                            eventCallback, mSensor, SensorManager.SENSOR_DELAY_NORMAL));

            try {
                eventLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                mSensorManager.unregisterListener(eventCallback);
            }

            return eventCopy;
        }

        public boolean discontinuityCount() {
            final CountDownLatch eventLatch = new CountDownLatch(4);
            SensorEventCallback eventCallback =
                    new SensorEventCallback() {
                        public void onSensorChanged(SensorEvent event) {
                            if (eventLatch.getCount() == 4) {
                                if (event.firstEventAfterDiscontinuity) {
                                    mFirstDiscontinuityReported = true;
                                    eventLatch.countDown();
                                } // else: ignore event
                            } else {
                                if (event.firstEventAfterDiscontinuity) {
                                    mDuplicateDiscontinuityCount++;
                                }
                                eventLatch.countDown();
                            }
                        }
                    };

            assertTrue(
                    "Register listener is returning false in discontinuityCount().",
                    mSensorManager.registerListener(
                            eventCallback, mSensor, SensorManager.SENSOR_DELAY_NORMAL));

            try {
                eventLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
                assertEquals(
                        "mDuplicateDiscontinuityCount is not equal to expected.",
                        0,
                        mDuplicateDiscontinuityCount);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } finally {
                mSensorManager.unregisterListener(eventCallback);
            }

            return mFirstDiscontinuityReported;
        }

        public void headTrackerData() {
            float threshold = 0.001f;

            SensorEventCopy event = mCallback.getOneSensorEvent();

            assertNotNull("Time out waiting on a SensorEvent", event);

            for (int i = 0; i < mVirtualHeadTracker.HEAD_TRACKER_VALUES.length; i++) {
                assertEquals(
                        mVirtualHeadTracker.HEAD_TRACKER_VALUES[i], event.mValues[i], threshold);
            }
        }

        public boolean isTypeHeadTracker() {
            return assumeSensorIsSet() && mSensor.getType() == Sensor.TYPE_HEAD_TRACKER;
        }

        public boolean isStringTypeHeadTracker() {
            return assumeSensorIsSet()
                    && mSensor.getStringType() == Sensor.STRING_TYPE_HEAD_TRACKER;
        }

        public boolean isNotWakeUpSensor() {
            return assumeSensorIsSet() && mSensor.isWakeUpSensor();
        }

        public boolean isDynamicSensor() {
            return assumeSensorIsSet() && mSensor.isDynamicSensor();
        }

        public boolean checkId() {
            return assumeSensorIsSet() && mSensor.getId() != 0 && mSensor.getId() != -1;
        }

        public boolean isReportingModeContinuous() {
            return assumeSensorIsSet()
                    && mSensor.getReportingMode() == Sensor.REPORTING_MODE_CONTINUOUS;
        }

        public boolean isUhidName() {
            return assumeSensorIsSet() && mSensor.getName() == mEmulatorName;
        }

        public boolean isSensorInList() {
            return assumeSensorIsSet()
                    && mSensorManager.getDynamicSensorList(Sensor.TYPE_ALL).contains(mSensor);
        }

        public boolean isSensorInListOfSpecificType() {
            return assumeSensorIsSet()
                    && mSensorManager.getDynamicSensorList(mSensor.getType()).contains(mSensor);
        }

        public Integer getSensorId() {
            return assumeSensorIsSet() ? mSensor.getId() : null;
        }

        private boolean assumeSensorIsSet() {
            if (mSensor == null) {
                Log.e(TAG, "Sensor is not set.");
                return false;
            }
            return true;
        }
    }

    private void featureSupportedOrSkip() {
        if (!mHasSensorDynamicHeadTracker) {
            throw new SensorTestStateNotSupportedException(
                    "Dynamic sensor discovery not supported, skip.");
        }
    }

    private static void configureHtSensorAccess(boolean enable) {
        final String command = "cmd sensorservice " + ((enable) ? "un" : "") + "restrict-ht";
        Log.d(TAG, "Running command " + command);
        try {
            SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(), command);
        } catch (IOException e) {
            Log.e(TAG, "Failed to run command " + command, e);
        }
    }
}
