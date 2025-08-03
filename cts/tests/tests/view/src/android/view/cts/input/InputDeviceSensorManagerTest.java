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

package android.view.cts.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.hardware.Sensor;
import android.hardware.SensorDirectChannel;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.MemoryFile;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.util.Log;
import android.view.InputDevice;
import android.view.cts.R;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.input.UinputDevice;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link android.view.InputDevice} sensor functionality.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class InputDeviceSensorManagerTest {
    private static final String TAG = "InputDeviceSensorManagerTest";
    private static final int SENSOR_VEC_LENGTH = 3;
    private static final int EV_SYN = 0;
    private static final int EV_ABS = 3;
    private static final int EV_MSC = 4;
    private static final int ABS_X = 0;
    private static final int ABS_Y = 1;
    private static final int ABS_Z = 2;
    private static final int ABS_RX = 3;
    private static final int ABS_RY = 4;
    private static final int ABS_RZ = 5;
    private static final int MSC_TIMESTAMP = 5;
    // The time interval for between sensor time events, in unit of micro seconds.
    private static final int TIME_INTERVAL_US = 10000;
    // Requested sensor listening interval, to pass to registerListener API,
    // in unit of milli seconds.
    private static final int SAMPLING_INTERVAL_US = 20000;
    // The Gyroscope sensor hardware resolution of 1 unit, degree/second.
    private static final float GYRO_RESOLUTION = 1024.0f;
    // The Accelerometer sensor hardware resolution of 1 unit, per g.
    private static final float ACCEL_RESOLUTION = 8192.0f;
    // Numbers of sensor samples to run.
    private static final int RUNNING_SAMPLES = 100;
    // Sensor raw value increment step for each sensor event.
    private static final int SAMPLE_STEP = 92;
    // Tolerance of sensor event values.
    private static final float TOLERANCE = 0.01f;
    // Linux accelerometer unit is per g,  Android unit is m/s^2
    private static final float GRAVITY_MS2_UNIT = 9.80665f;
    // Linux gyroscope unit is degree/second, Android unit is radians/second
    private static final float DEGREE_RADIAN_UNIT = 0.0174533f;
    // Share memory size
    private static final int SHARED_MEMORY_SIZE = 8192;

    private static final int CONNECTION_TIMEOUT_SEC = 3;

    private InputManager mInputManager;
    private UinputDevice mUinputDevice;
    private Instrumentation mInstrumentation;
    private SensorManager mSensorManager;
    private HandlerThread mSensorThread = null;
    private Handler mSensorHandler = null;
    private final Object mLock = new Object();

    private class Callback extends SensorManager.DynamicSensorCallback {
        private Sensor mSensor;
        private CountDownLatch mConnectLatch = new CountDownLatch(1);
        private CountDownLatch mDisconnectLatch = new CountDownLatch(1);

        Callback(@NonNull Sensor sensor) {
            mSensor = sensor;
        }

        @Override
        public void onDynamicSensorConnected(Sensor sensor) {
            synchronized (mSensor) {
                if (mSensor.getId() == sensor.getId()) {
                    mConnectLatch.countDown();
                }
            }
        }

        @Override
        public void onDynamicSensorDisconnected(Sensor sensor) {
            synchronized (mSensor) {
                if (mSensor.getId() == sensor.getId()) {
                    mDisconnectLatch.countDown();
                }
            }
        }

        public boolean waitForConnection() {
            try {
                return mConnectLatch.await(CONNECTION_TIMEOUT_SEC, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return false;
        }

        public void assertNoDisconnection() {
            assertEquals(1, mDisconnectLatch.getCount());
        }
    }

    private class InputTestSensorEventListener implements SensorEventListener {
        private CountDownLatch mAccuracyLatch;
        private int mAccuracy = SensorManager.SENSOR_STATUS_NO_CONTACT;
        private final BlockingQueue<SensorEvent> mEvents = new LinkedBlockingQueue<>();
        InputTestSensorEventListener() {
            super();
            mAccuracyLatch = new CountDownLatch(1);
        }

        public SensorEvent waitForSensorEvent() {
            try {
                return mEvents.poll(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail("unexpectedly interrupted while waiting for SensorEvent");
                return null;
            }
        }

        public int waitForAccuracyChanged() {
            boolean ret;
            try {
                ret = mAccuracyLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                ret = false;
                Thread.currentThread().interrupt();
            }

            synchronized (mLock) {
                return mAccuracy;
            }
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            synchronized (mLock) {
                try {
                    mEvents.put(event);
                } catch (InterruptedException ex) {
                    fail("interrupted while adding a SensorEvent to the queue");
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            synchronized (mLock) {
                mAccuracy = accuracy;
            }
            if (mAccuracyLatch != null) {
                mAccuracyLatch.countDown();
            }
        }
    }

    /**
     * Get a SensorManager object from input device with specified Vendor Id and Product Id.
     * @param vid Vendor Id
     * @param pid Product Id
     * @return SensorManager object in specified InputDevice
     */
    private SensorManager getSensorManager(int vid, int pid) {
        final int[] inputDeviceIds = mInputManager.getInputDeviceIds();
        for (int inputDeviceId : inputDeviceIds) {
            final InputDevice inputDevice = mInputManager.getInputDevice(inputDeviceId);
            if (inputDevice.getVendorId() == vid && inputDevice.getProductId() == pid) {
                SensorManager sensorManager = inputDevice.getSensorManager();
                assertNotNull("getSensorManager returns null", sensorManager);
                Log.i(TAG, "Input device: " + inputDeviceId + " VendorId: "
                        + inputDevice.getVendorId() + " ProductId: " + inputDevice.getProductId());
                return sensorManager;
            }
        }
        return null;
    }

    private void bumpSensorsData(int[] sensorVector) {
        final int step = SAMPLE_STEP;
        for (int i = 0; i < sensorVector.length; i++) {
            sensorVector[i] = sensorVector[i] + step;
        }
    }

    private float[] getExpectedSensorValue(Sensor sensor, int[] dataVector) {
        float[] sensorValues = new float[dataVector.length];
        for (int i = 0; i < dataVector.length; i++) {
            switch (sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    sensorValues[i] = ((float) dataVector[i]) / ACCEL_RESOLUTION
                            * GRAVITY_MS2_UNIT;
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    sensorValues[i] = ((float) dataVector[i]) / GYRO_RESOLUTION
                            * DEGREE_RADIAN_UNIT;
                    break;
                default:
                    break;
            }
        }
        return sensorValues;
    }

    private void assertSensorDataEquals(float[] expected, float[] received) {
        assertEquals("expected sensor data length is not same as received sensor data length",
                expected.length, received.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("Data index[" + i + "] not match", expected[i], received[i], TOLERANCE);
        }
    }

    /**
     * Simulate a sensor data sample from device.
     * @param sensor sensor object for data to be injected
     * @param dataVec sensor data vector
     * @param timestamp sensor data timestamp and sync to be injected, 0 for no timestamp and sync
     */
    private void injectSensorSample(Sensor sensor, int[] dataVec, int timestamp) {
        assertEquals("Sensor sample size is wrong", dataVec.length, SENSOR_VEC_LENGTH);

        switch (sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER: {
                int[] evSensorSample = new int[] {
                    EV_ABS, ABS_X, dataVec[0],
                    EV_ABS, ABS_Y, dataVec[1],
                    EV_ABS, ABS_Z, dataVec[2],
                };
                mUinputDevice.injectEvents(Arrays.toString(evSensorSample));
                break;
            }
            case Sensor.TYPE_GYROSCOPE: {
                int[] evSensorSample = new int[] {
                    EV_ABS, ABS_RX, dataVec[0],
                    EV_ABS, ABS_RY, dataVec[1],
                    EV_ABS, ABS_RZ, dataVec[2],
                };
                mUinputDevice.injectEvents(Arrays.toString(evSensorSample));
                break;
            }
            default:
                return;
        }
        if (timestamp > 0) {
            int[] evTimestamp = new int[] {
                    EV_MSC, MSC_TIMESTAMP, timestamp,
                    EV_SYN, 0, 0 };
            mUinputDevice.injectEvents(Arrays.toString(evTimestamp));
        }
    }

    private void testSensorManagerListenerForSensors(Sensor[] sensors) {
        final InputTestSensorEventListener[] listeners =
                new InputTestSensorEventListener[sensors.length];
        int[] dataVector = new int[]{2535, -2398, 21345};
        long[] lastTimestamp = new long[sensors.length];

        for (int i = 0; i < sensors.length; i++) {
            listeners[i] = new InputTestSensorEventListener();
            assertTrue(mSensorManager.registerListener(listeners[i], sensors[i],
                    SensorManager.SENSOR_DELAY_GAME, mSensorHandler));
        }

        long startTimestamp = SystemClock.elapsedRealtimeNanos();
        for (int count = 0; count < RUNNING_SAMPLES; count++) {
            bumpSensorsData(dataVector);
            // when the listener's sampling interval is longer than sensor native sample interval,
            // the listener get report for multiple sensor samples, inject multiple samples so
            // sensor listener can get an event callback.
            for (int hwTimestamp = 100000;
                    hwTimestamp - 100000 < SAMPLING_INTERVAL_US;
                    hwTimestamp += TIME_INTERVAL_US) {
                // Inject sensor samples
                for (int i = 0; i < sensors.length; i++) {
                    if (i == sensors.length - 1) {
                        injectSensorSample(sensors[i], dataVector, hwTimestamp);
                    } else {
                        injectSensorSample(sensors[i], dataVector, 0 /* timestamp */);
                    }
                }
                SystemClock.sleep(TIME_INTERVAL_US / 1000);
            }
            // Check the sensor listener events for each sensor
            for (int i = 0; i < sensors.length; i++) {
                SensorEvent e = listeners[i].waitForSensorEvent();
                assertNotNull("Sensor event for count " + count + " is null", e);
                // Verify timestamp monotonically increasing
                if (lastTimestamp[i] != 0) {
                    final long diff = e.timestamp - lastTimestamp[i];
                    assertTrue("Sensor timestamp " + e.timestamp + " not monotonically increasing!"
                            + "last " + lastTimestamp[i], diff > TIME_INTERVAL_US);
                }
                lastTimestamp[i] = e.timestamp;
                // Verify sensor timestamp greater than start Android time
                assertTrue("Sensor timestamp smaller than starting elapsedRealtimeNanos",
                        startTimestamp < e.timestamp);
                assertSensorDataEquals(getExpectedSensorValue(sensors[i], dataVector),
                        e.values);
            }
            // Check sensor onAccuracyChanged events are called
            for (int i = 0; i < sensors.length; i++) {
                assertEquals(SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
                        listeners[i].waitForAccuracyChanged());
            }
        }

        for (int i = 0; i < sensors.length; i++) {
            mSensorManager.unregisterListener(listeners[i]);
        }
    }

    private Sensor getDefaultSensor(int sensorType) {
        Sensor sensor = mSensorManager.getDefaultSensor(sensorType);
        assertNotNull(sensor);
        assertEquals(sensor.getType(), sensorType);
        return sensor;
    }

    @Before
    public void setup() {
        final int resourceId = R.raw.gamepad_sensors_register;
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mInputManager = mInstrumentation.getTargetContext().getSystemService(InputManager.class);
        assertNotNull(mInputManager);

        mUinputDevice = UinputDevice.create(mInstrumentation, R.raw.gamepad_sensors_register,
                InputDevice.SOURCE_KEYBOARD);
        mSensorManager = getSensorManager(mUinputDevice.getVendorId(),
                mUinputDevice.getProductId());
        assertNotNull(mSensorManager);

        mSensorThread = new HandlerThread("SensorThread");
        mSensorThread.start();
        mSensorHandler = new Handler(mSensorThread.getLooper());
    }

    @After
    public void tearDown() {
        mUinputDevice.close();
    }

    @Test
    public void testAccelerometerSensorListener() {
        // Test Accelerometer sensor
        final Sensor[] sensors = new Sensor[]{
            getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        };
        testSensorManagerListenerForSensors(sensors);
    }

    @Test
    public void testGyroscopeSensorListener() {
        // Test Gyroscope sensor
        final Sensor[] sensors = new Sensor[]{
            getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        };
        testSensorManagerListenerForSensors(sensors);
    }

    @Test
    public void testAllSensorsListeners() {
        final Sensor[] sensors = new Sensor[]{
            getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        };
        testSensorManagerListenerForSensors(sensors);
    }

    @Test
    public void testSupportedSensorTypes() {
        final List<Integer> types = Arrays.asList(Sensor.TYPE_ACCELEROMETER,
                Sensor.TYPE_GYROSCOPE);
        for (int i = 0; i < types.size(); i++) {
            List<Sensor> sensors = mSensorManager.getSensorList(types.get(i));
            assertEquals("Sensor type " + types.get(i), 1L, sensors.size());
        }
    }

    @Test
    public void testUnsupportedSensorTypes() {
        final List<Integer> supportedTypes = Arrays.asList(Sensor.TYPE_ACCELEROMETER,
                Sensor.TYPE_GYROSCOPE);

        for (int type = Sensor.TYPE_ACCELEROMETER; type <= Sensor.TYPE_HINGE_ANGLE; type++) {
            if (!supportedTypes.contains(type)) {
                List<Sensor> sensors = mSensorManager.getSensorList(type);
                assertEquals(0L, sensors.size());
                assertNull(mSensorManager.getDefaultSensor(type));
            }
        }
    }

    @Test
    public void testDirectChannelAPIs() {
        // Direct channel is not supported by input device sensor manager.
        try {
            final MemoryFile memFile = new MemoryFile("Sensor Channel", SHARED_MEMORY_SIZE);
            SensorDirectChannel channel = mSensorManager.createDirectChannel(memFile);
            // Expect returning a null channel when calling the API
            assertNull(channel);
        } catch (IOException e) {
            fail("IOException when allocating MemoryFile");
        }
    }

    @Test
    public void testDynamicSensorAPIs() {
        final List<Sensor> dynamicAccelerometers =
                mSensorManager.getDynamicSensorList(Sensor.TYPE_ACCELEROMETER);
        // Input device sensor manager doesn't expose any dynamic sensor
        assertEquals(0, dynamicAccelerometers.size());

        // Attempt to register regular sensor as dynamic sensor
        final Sensor accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        final Callback callback = new Callback(accelerometer);
        mSensorManager.registerDynamicSensorCallback(callback);
        // Dynamic call back is not supported, not connection or disconnection should happen.
        assertFalse(callback.waitForConnection());
        callback.assertNoDisconnection();
        // Unregister the dynamic sensor callback shouldn't throw any exception.
        mSensorManager.unregisterDynamicSensorCallback(callback);
        // The isDynamicSensorDiscoverySupported API should returns false.
        assertFalse(mSensorManager.isDynamicSensorDiscoverySupported());

    }

}
