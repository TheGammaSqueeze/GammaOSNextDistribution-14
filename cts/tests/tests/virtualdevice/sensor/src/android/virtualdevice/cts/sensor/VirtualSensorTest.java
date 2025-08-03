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

package android.virtualdevice.cts.sensor;

import static android.hardware.Sensor.TYPE_ACCELEROMETER;
import static android.hardware.SensorDirectChannel.RATE_NORMAL;
import static android.hardware.SensorDirectChannel.RATE_STOP;
import static android.hardware.SensorDirectChannel.TYPE_HARDWARE_BUFFER;
import static android.hardware.SensorDirectChannel.TYPE_MEMORY_FILE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import static java.util.concurrent.TimeUnit.MICROSECONDS;

import android.annotation.Nullable;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.sensor.VirtualSensor;
import android.companion.virtual.sensor.VirtualSensorCallback;
import android.companion.virtual.sensor.VirtualSensorConfig;
import android.companion.virtual.sensor.VirtualSensorDirectChannelCallback;
import android.companion.virtual.sensor.VirtualSensorDirectChannelWriter;
import android.companion.virtual.sensor.VirtualSensorEvent;
import android.content.Context;
import android.hardware.HardwareBuffer;
import android.hardware.Sensor;
import android.hardware.SensorDirectChannel;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.MemoryFile;
import android.os.SharedMemory;
import android.platform.test.annotations.AppModeFull;
import android.system.ErrnoException;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualSensorTest {

    private static final String VIRTUAL_SENSOR_NAME = "VirtualAccelerometer";
    private static final String SECOND_SENSOR_NAME = VIRTUAL_SENSOR_NAME + "2";
    private static final String VIRTUAL_SENSOR_VENDOR = "VirtualDeviceVendor";

    private static final int CUSTOM_SENSOR_TYPE = Sensor.TYPE_DEVICE_PRIVATE_BASE + 9999;

    private static final int SENSOR_TIMEOUT_MILLIS = 1000;

    private static final int SENSOR_EVENT_SIZE = 104;
    private static final int SENSOR_EVENT_COUNT = 100;
    private static final int SHARED_MEMORY_SIZE = SENSOR_EVENT_COUNT * SENSOR_EVENT_SIZE;

    @Rule
    public VirtualDeviceRule mVirtualDeviceRule = VirtualDeviceRule.createDefault();
    @Nullable
    private VirtualDeviceManager.VirtualDevice mVirtualDevice;

    private SensorManager mSensorManager;
    @Mock
    private SensorManager.DynamicSensorCallback mDynamicSensorCallback;

    private SensorManager mVirtualDeviceSensorManager;
    private VirtualSensor mVirtualSensor;
    private MemoryFile mMemoryFile;
    private SensorDirectChannel mDirectChannel;
    private VirtualSensorDirectChannelWriter mVirtualSensorDirectChannelWriter;
    @Mock
    private VirtualSensorCallback mVirtualSensorCallback;
    @Mock
    private VirtualSensorDirectChannelCallback mVirtualSensorDirectChannelCallback;
    private final VirtualSensorEventListener mSensorEventListener =
            new VirtualSensorEventListener();

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    // Synchronize the IO to reduce test flakiness.
    private final Object mDirectChannelIoLock = new Object();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mSensorManager = mContext.getSystemService(SensorManager.class);
    }

    private VirtualSensor setUpVirtualSensor(VirtualSensorConfig sensorConfig) {
        VirtualDeviceParams.Builder builder = new VirtualDeviceParams.Builder()
                .setDevicePolicy(VirtualDeviceParams.POLICY_TYPE_SENSORS,
                        VirtualDeviceParams.DEVICE_POLICY_CUSTOM);
        if (sensorConfig != null) {
            builder = builder
                    .addVirtualSensorConfig(sensorConfig)
                    .setVirtualSensorCallback(
                            mContext.getMainExecutor(), mVirtualSensorCallback);
            if (sensorConfig.getDirectChannelTypesSupported() > 0) {
                builder = builder.setVirtualSensorDirectChannelCallback(
                        mContext.getMainExecutor(), mVirtualSensorDirectChannelCallback);
            }
        }
        mVirtualDevice = mVirtualDeviceRule.createManagedVirtualDevice(builder.build());
        Context deviceContext = mContext.createDeviceContext(mVirtualDevice.getDeviceId());
        mVirtualDeviceSensorManager = deviceContext.getSystemService(SensorManager.class);
        if (sensorConfig == null) {
            return null;
        } else {
            return mVirtualDevice.getVirtualSensorList().get(0);
        }
    }

    private void setUpDirectChannel() throws Exception {
        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME)
                        .setDirectChannelTypesSupported(TYPE_MEMORY_FILE)
                        .setHighestDirectReportRateLevel(RATE_NORMAL)
                        .build());

        mMemoryFile = new MemoryFile("Sensor Channel", SHARED_MEMORY_SIZE);
        mDirectChannel = mVirtualDeviceSensorManager.createDirectChannel(mMemoryFile);
    }

    @After
    public void tearDown() {
        if (mMemoryFile != null) {
            mMemoryFile.close();
        }
        if (mDirectChannel != null) {
            mDirectChannel.close();
        }
    }

    @Test
    public void buildVirtualSensorConfig_hardwareBufferDirectChannel_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME)
                        .setDirectChannelTypesSupported(TYPE_HARDWARE_BUFFER | TYPE_MEMORY_FILE)
                        .build());
    }

    @Test
    public void getSensorList_noVirtualSensors_returnsEmptyList() {
        mVirtualSensor = setUpVirtualSensor(/* sensorConfig= */ null);
        assertThat(mVirtualSensor).isNull();
        assertThat(mVirtualDeviceSensorManager.getSensorList(TYPE_ACCELEROMETER)).isEmpty();
    }

    @Test
    public void getSensorList_returnsVirtualSensor() {
        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME)
                        .setVendor(VIRTUAL_SENSOR_VENDOR)
                        .setDirectChannelTypesSupported(TYPE_MEMORY_FILE)
                        .setHighestDirectReportRateLevel(RATE_NORMAL)
                        .setMaximumRange(1.2f)
                        .setResolution(3.4f)
                        .setPower(5.6f)
                        .setMinDelay(7)
                        .setMaxDelay(8)
                        .build());

        assertThat(mVirtualSensor.getType()).isEqualTo(TYPE_ACCELEROMETER);
        assertThat(mVirtualSensor.getName()).isEqualTo(VIRTUAL_SENSOR_NAME);
        assertThat(mVirtualSensor.getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());

        Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);
        List<Sensor> sensors = mVirtualDeviceSensorManager.getSensorList(TYPE_ACCELEROMETER);
        Sensor defaultDeviceSensor = mSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);

        assertThat(sensors).containsExactly(sensor);
        assertThat(sensor).isNotEqualTo(defaultDeviceSensor);

        assertThat(sensor.getName()).isEqualTo(VIRTUAL_SENSOR_NAME);
        assertThat(sensor.getVendor()).isEqualTo(VIRTUAL_SENSOR_VENDOR);
        assertThat(sensor.getType()).isEqualTo(TYPE_ACCELEROMETER);
        assertThat(sensor.isDynamicSensor()).isFalse();
        assertThat(sensor.isDirectChannelTypeSupported(TYPE_MEMORY_FILE)).isTrue();
        assertThat(sensor.isDirectChannelTypeSupported(TYPE_HARDWARE_BUFFER)).isFalse();
        assertThat(sensor.getHighestDirectReportRateLevel()).isEqualTo(RATE_NORMAL);
        assertThat(sensor.getMaximumRange()).isEqualTo(1.2f);
        assertThat(sensor.getResolution()).isEqualTo(3.4f);
        assertThat(sensor.getPower()).isEqualTo(5.6f);
        assertThat(sensor.getMinDelay()).isEqualTo(7);
        assertThat(sensor.getMaxDelay()).isEqualTo(8);
        assertThat(sensor.getStringType()).isEqualTo(Sensor.STRING_TYPE_ACCELEROMETER);
        assertThat(sensor.getReportingMode()).isEqualTo(Sensor.REPORTING_MODE_CONTINUOUS);
    }

    @Test
    public void getSensorList_isCached() {
        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME).build());

        final List<Sensor> allSensors = mVirtualDeviceSensorManager.getSensorList(Sensor.TYPE_ALL);
        assertThat(allSensors).isSameInstanceAs(
                mVirtualDeviceSensorManager.getSensorList(Sensor.TYPE_ALL));

        final List<Sensor> sensors = mVirtualDeviceSensorManager.getSensorList(TYPE_ACCELEROMETER);
        assertThat(sensors).isSameInstanceAs(
                mVirtualDeviceSensorManager.getSensorList(TYPE_ACCELEROMETER));
    }

    @Test
    public void createVirtualSensor_multipleSensorsSameType() {
        VirtualDeviceParams.Builder builder = new VirtualDeviceParams.Builder()
                .setDevicePolicy(VirtualDeviceParams.POLICY_TYPE_SENSORS,
                        VirtualDeviceParams.DEVICE_POLICY_CUSTOM)
                .addVirtualSensorConfig(
                        new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME)
                                .build())
                .addVirtualSensorConfig(
                        new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, SECOND_SENSOR_NAME)
                                .build())
                .setVirtualSensorCallback(
                        mContext.getMainExecutor(), mVirtualSensorCallback);
        mVirtualDevice = mVirtualDeviceRule.createManagedVirtualDevice(builder.build());
        Context deviceContext = mContext.createDeviceContext(mVirtualDevice.getDeviceId());
        mVirtualDeviceSensorManager = deviceContext.getSystemService(SensorManager.class);

        List<VirtualSensor> virtualSensors = mVirtualDevice.getVirtualSensorList();
        assertThat(virtualSensors).hasSize(2);
        assertThat(virtualSensors.get(0).getType()).isEqualTo(TYPE_ACCELEROMETER);
        assertThat(virtualSensors.get(0).getName()).isEqualTo(VIRTUAL_SENSOR_NAME);
        assertThat(virtualSensors.get(0).getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());
        assertThat(virtualSensors.get(1).getType()).isEqualTo(TYPE_ACCELEROMETER);
        assertThat(virtualSensors.get(1).getName()).isEqualTo(SECOND_SENSOR_NAME);
        assertThat(virtualSensors.get(1).getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());

        List<Sensor> sensors = mVirtualDeviceSensorManager.getSensorList(TYPE_ACCELEROMETER);
        assertThat(sensors).hasSize(2);
        assertThat(sensors.get(0).getName()).isEqualTo(VIRTUAL_SENSOR_NAME);
        assertThat(sensors.get(0).getType()).isEqualTo(TYPE_ACCELEROMETER);
        assertThat(sensors.get(1).getName()).isEqualTo(SECOND_SENSOR_NAME);
        assertThat(sensors.get(1).getType()).isEqualTo(TYPE_ACCELEROMETER);
    }

    @Test
    public void createVirtualSensor_dynamicSensorCallback_notCalled() {
        mSensorManager.registerDynamicSensorCallback(mDynamicSensorCallback);

        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME).build());

        verify(mDynamicSensorCallback, never()).onDynamicSensorConnected(any());

        mSensorManager.unregisterDynamicSensorCallback(mDynamicSensorCallback);
    }

    @Test
    public void closeVirtualDevice_removesSensor() {
        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME).build());
        mVirtualDevice.close();

        // The virtual device ID is no longer valid, SensorManager falls back to default device.
        Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);
        Sensor defaultDeviceSensor = mSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);

        assertThat(sensor.getHandle()).isEqualTo(defaultDeviceSensor.getHandle());
        assertThat(sensor.getName()).isEqualTo(defaultDeviceSensor.getName());
    }

    @Test
    public void registerListener_triggersVirtualSensorCallback() {
        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME).build());

        Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);

        final int samplingPeriodMicros = 2345000;
        final int maxReportLatencyMicros = 678000;
        mVirtualDeviceSensorManager.registerListener(
                mSensorEventListener, sensor, samplingPeriodMicros, maxReportLatencyMicros);

        final Duration expectedSamplingPeriod =
                Duration.ofNanos(MICROSECONDS.toNanos(samplingPeriodMicros));
        final Duration expectedReportLatency =
                Duration.ofNanos(MICROSECONDS.toNanos(maxReportLatencyMicros));

        ArgumentCaptor<VirtualSensor> virtualSensor = ArgumentCaptor.forClass(VirtualSensor.class);
        verify(mVirtualSensorCallback, timeout(SENSOR_TIMEOUT_MILLIS).times(1))
                .onConfigurationChanged(virtualSensor.capture(), eq(true),
                        eq(expectedSamplingPeriod), eq(expectedReportLatency));
        assertThat(virtualSensor.getValue().getHandle()).isEqualTo(mVirtualSensor.getHandle());

        mVirtualDeviceSensorManager.unregisterListener(mSensorEventListener);

        verify(mVirtualSensorCallback, timeout(SENSOR_TIMEOUT_MILLIS).times(1))
                .onConfigurationChanged(virtualSensor.capture(), eq(false), any(Duration.class),
                        any(Duration.class));
        assertThat(virtualSensor.getValue().getHandle()).isEqualTo(mVirtualSensor.getHandle());

        verifyNoMoreInteractions(mVirtualSensorCallback);
    }

    @Test
    public void sendEvent_reachesRegisteredListeners() {
        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME).build());

        Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);

        mVirtualDeviceSensorManager.registerListener(
                mSensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);

        final VirtualSensorEvent firstEvent =
                new VirtualSensorEvent.Builder(new float[] {0.1f, 2.3f, 4.5f}).build();
        mVirtualSensor.sendEvent(firstEvent);

        mSensorEventListener.assertReceivedSensorEvent(sensor, firstEvent);

        final VirtualSensorEvent secondEvent =
                new VirtualSensorEvent.Builder(new float[] {6.7f, 8.9f, 0.1f}).build();
        mVirtualSensor.sendEvent(secondEvent);

        mSensorEventListener.assertReceivedSensorEvent(sensor, secondEvent);

        mVirtualDeviceSensorManager.unregisterListener(mSensorEventListener);

        final VirtualSensorEvent thirdEvent =
                new VirtualSensorEvent.Builder(new float[] {2.3f, 4.5f, 6.7f}).build();
        mVirtualSensor.sendEvent(thirdEvent);

        mSensorEventListener.assertNoMoreEvents();
    }

    @Test
    public void sendEvent_explicitTimestampSpecified() {
        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME).build());
        Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);
        mVirtualDeviceSensorManager.registerListener(
                mSensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);

        VirtualSensorEvent event =
                new VirtualSensorEvent.Builder(new float[] {0.1f, 2.3f, 4.5f})
                        .setTimestampNanos(System.nanoTime())
                        .build();
        mVirtualSensor.sendEvent(event);

        mSensorEventListener.assertReceivedSensorEvent(sensor, event);

        mVirtualDeviceSensorManager.unregisterListener(mSensorEventListener);

        mSensorEventListener.assertNoMoreEvents();
    }

    @Test
    public void sendEvent_invalidValues_eventIsDropped() {
        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME).build());

        Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);

        mVirtualDeviceSensorManager.registerListener(
                mSensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);

        final VirtualSensorEvent firstEvent =
                new VirtualSensorEvent.Builder(new float[] {0.1f}).build();
        mVirtualSensor.sendEvent(firstEvent);

        final VirtualSensorEvent secondEvent =
                new VirtualSensorEvent.Builder(new float[] {2.3f, 4.5f, 6.7f, 8.9f}).build();
        mVirtualSensor.sendEvent(secondEvent);

        final VirtualSensorEvent thirdEvent =
                new VirtualSensorEvent.Builder(new float[] {7.7f, 8.8f}).build();
        mVirtualSensor.sendEvent(thirdEvent);

        mSensorEventListener.assertNoMoreEvents();

        mVirtualDeviceSensorManager.unregisterListener(mSensorEventListener);
    }

    @Test
    public void virtualSensor_arbitrarySensorType() {
        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(CUSTOM_SENSOR_TYPE, VIRTUAL_SENSOR_NAME).build());

        Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(CUSTOM_SENSOR_TYPE);

        mVirtualDeviceSensorManager.registerListener(
                mSensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);

        float[] values = new float[16];
        Arrays.fill(values, 1.2f);

        final VirtualSensorEvent validEvent = new VirtualSensorEvent.Builder(values).build();
        mVirtualSensor.sendEvent(validEvent);

        mSensorEventListener.assertReceivedSensorEvent(sensor, validEvent);

        // Sensor events can have at most 16 values. Check that events with more values are dropped.
        float[] invalidValues = new float[17];
        Arrays.fill(values, 3.4f);
        final VirtualSensorEvent invalidEvent =
                new VirtualSensorEvent.Builder(invalidValues).build();
        mVirtualSensor.sendEvent(invalidEvent);

        mSensorEventListener.assertNoMoreEvents();

        mVirtualDeviceSensorManager.unregisterListener(mSensorEventListener);
    }

    @Test
    public void directConnection_memoryFile_notSupported() throws Exception {
        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME).build());

        MemoryFile memoryFile = new MemoryFile("Sensor Channel", SHARED_MEMORY_SIZE);
        SensorDirectChannel channel = mVirtualDeviceSensorManager.createDirectChannel(memoryFile);

        Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);
        assertThat(channel.configure(sensor, RATE_NORMAL)).isEqualTo(0);
    }

    @Test
    public void directConnection_hardwareBuffer_throwsException() {
        // Skip this test if hardware buffer direct channel is generally not supported on the device
        assumeTrue(mSensorManager.getSensorList(Sensor.TYPE_ALL).stream().anyMatch(
                s -> s.isDirectChannelTypeSupported(TYPE_HARDWARE_BUFFER)));

        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME).build());

        try (HardwareBuffer hardwareBuffer = createSensorsHardwareBuffer()) {
            assertThrows(UncheckedIOException.class,
                    () -> mVirtualDeviceSensorManager.createDirectChannel(hardwareBuffer));
        }
    }

    @Test
    public void directConnection_memoryFile_onlyValidForVirtualDevice() throws Exception {
        setUpDirectChannel();

        // The channel is created for the virtual device ID, configuring it for a sensor of the
        // default device should not be allowed.
        Sensor defaultDeviceSensor = mSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);
        assertThat(mDirectChannel.configure(defaultDeviceSensor, RATE_NORMAL)).isEqualTo(0);
    }

    @Test
    public void directConnectionForDefaultDevice_memoryFile_notValidForVirtualDevice()
            throws Exception {
        // Skip this test if memory file direct channel is generally not supported on the device.
        assumeTrue(mSensorManager.getSensorList(Sensor.TYPE_ALL).stream().anyMatch(
                s -> s.isDirectChannelTypeSupported(TYPE_MEMORY_FILE)));

        MemoryFile memoryFile = new MemoryFile("Sensor Channel", SHARED_MEMORY_SIZE);
        SensorDirectChannel channel = mSensorManager.createDirectChannel(memoryFile);

        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME)
                        .setDirectChannelTypesSupported(TYPE_MEMORY_FILE)
                        .setHighestDirectReportRateLevel(RATE_NORMAL)
                        .build());

        // The channel is created for the default device ID, configuring it for a sensor of the
        // virtual device should not be allowed.
        Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);
        assertThat(channel.configure(sensor, RATE_NORMAL)).isEqualTo(0);
    }

    @Test
    public void directConnection_memoryFile_notValidForAnotherVirtualDevice() throws Exception {
        setUpDirectChannel();

        // Creates a new virtual device, while the direct channel is associated with the Sensor
        // manager of the first virtual device.
        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME)
                        .setDirectChannelTypesSupported(TYPE_MEMORY_FILE)
                        .setHighestDirectReportRateLevel(RATE_NORMAL)
                        .build());

        // The channel is configured for the first device and the sensor does not belong to it.
        Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);
        assertThat(mDirectChannel.configure(sensor, RATE_NORMAL)).isEqualTo(0);
    }

    @Test
    public void directConnectionForDefaultDevice_hardwareBuffer_notValidForVirtualDevice() {
        // Skip this test if hardware buffer direct channel is generally not supported on the device
        assumeTrue(mSensorManager.getSensorList(Sensor.TYPE_ALL).stream().anyMatch(
                s -> s.isDirectChannelTypeSupported(TYPE_HARDWARE_BUFFER)));

        try (HardwareBuffer hardwareBuffer = createSensorsHardwareBuffer()) {
            SensorDirectChannel channel = mSensorManager.createDirectChannel(hardwareBuffer);

            mVirtualSensor = setUpVirtualSensor(
                    new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER,
                            VIRTUAL_SENSOR_NAME).build());

            // The channel is created for the default device ID, configuring it for a sensor of the
            // virtual device should not be allowed.
            Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);
            assertThat(channel.configure(sensor, RATE_NORMAL)).isEqualTo(0);
        }
    }

    @Test
    public void directConnection_memoryFile_triggersVirtualSensorCallback() throws Exception {
        setUpDirectChannel();

        ArgumentCaptor<Integer> channelHandle = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<SharedMemory> sharedMemory = ArgumentCaptor.forClass(SharedMemory.class);
        verify(mVirtualSensorDirectChannelCallback, timeout(SENSOR_TIMEOUT_MILLIS).times(1))
                .onDirectChannelCreated(channelHandle.capture(), sharedMemory.capture());

        doAnswer((Answer<Void>) i -> {
            sharedMemory.getValue().close();
            return null;
        }).when(mVirtualSensorDirectChannelCallback)
                .onDirectChannelDestroyed(channelHandle.getValue());

        Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);
        int reportToken = mDirectChannel.configure(sensor, RATE_NORMAL);
        assertThat(reportToken).isGreaterThan(0);

        ArgumentCaptor<VirtualSensor> virtualSensor = ArgumentCaptor.forClass(VirtualSensor.class);
        verify(mVirtualSensorDirectChannelCallback, timeout(SENSOR_TIMEOUT_MILLIS).times(1))
                .onDirectChannelConfigured(eq(channelHandle.getValue()), virtualSensor.capture(),
                        eq(RATE_NORMAL), eq(reportToken));
        assertThat(virtualSensor.getValue().getHandle()).isEqualTo(mVirtualSensor.getHandle());

        assertThat(mDirectChannel.configure(sensor, RATE_STOP)).isEqualTo(1);

        verify(mVirtualSensorDirectChannelCallback, timeout(SENSOR_TIMEOUT_MILLIS).times(1))
                .onDirectChannelConfigured(eq(channelHandle.getValue()), virtualSensor.capture(),
                        eq(RATE_STOP), eq(reportToken));
        assertThat(virtualSensor.getValue().getHandle()).isEqualTo(mVirtualSensor.getHandle());

        mDirectChannel.close();
        mDirectChannel = null;
        verify(mVirtualSensorDirectChannelCallback, timeout(SENSOR_TIMEOUT_MILLIS).times(1))
                .onDirectChannelDestroyed(eq(channelHandle.getValue()));
    }

    @Test
    public void directConnection_memoryFile_stopAll_triggersVirtualSensorCallback()
            throws Exception {
        setUpDirectChannel();

        ArgumentCaptor<Integer> channelHandle = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<SharedMemory> sharedMemory = ArgumentCaptor.forClass(SharedMemory.class);
        verify(mVirtualSensorDirectChannelCallback, timeout(SENSOR_TIMEOUT_MILLIS).times(1))
                .onDirectChannelCreated(channelHandle.capture(), sharedMemory.capture());

        doAnswer((Answer<Void>) i -> {
            sharedMemory.getValue().close();
            return null;
        }).when(mVirtualSensorDirectChannelCallback)
                .onDirectChannelDestroyed(channelHandle.getValue());

        Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);
        int reportToken = mDirectChannel.configure(sensor, RATE_NORMAL);
        assertThat(reportToken).isGreaterThan(0);

        ArgumentCaptor<VirtualSensor> virtualSensor = ArgumentCaptor.forClass(VirtualSensor.class);
        verify(mVirtualSensorDirectChannelCallback, timeout(SENSOR_TIMEOUT_MILLIS).times(1))
                .onDirectChannelConfigured(eq(channelHandle.getValue()), virtualSensor.capture(),
                        eq(RATE_NORMAL), eq(reportToken));
        assertThat(virtualSensor.getValue().getHandle()).isEqualTo(mVirtualSensor.getHandle());

        assertThat(mDirectChannel.configure(/*sensor=*/null, RATE_STOP)).isEqualTo(1);

        verify(mVirtualSensorDirectChannelCallback, timeout(SENSOR_TIMEOUT_MILLIS).times(1))
                .onDirectChannelConfigured(eq(channelHandle.getValue()), virtualSensor.capture(),
                        eq(RATE_STOP), eq(reportToken));
        assertThat(virtualSensor.getValue().getHandle()).isEqualTo(mVirtualSensor.getHandle());
    }

    @Test
    public void directConnection_memoryFile_injectEvents() throws Exception {
        setUpDirectChannel();

        ArgumentCaptor<Integer> channelHandle = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<SharedMemory> sharedMemory = ArgumentCaptor.forClass(SharedMemory.class);
        verify(mVirtualSensorDirectChannelCallback, timeout(SENSOR_TIMEOUT_MILLIS).times(1))
                .onDirectChannelCreated(channelHandle.capture(), sharedMemory.capture());

        doAnswer((Answer<Void>) i -> {
            int reportToken = (int) i.getArguments()[3];
            writeDirectChannelEvents(reportToken, sharedMemory.getValue());
            return null;
        }).when(mVirtualSensorDirectChannelCallback)
                .onDirectChannelConfigured(
                        eq(channelHandle.getValue()), any(), eq(RATE_NORMAL), anyInt());

        Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);
        int reportToken = mDirectChannel.configure(sensor, RATE_NORMAL);
        verifyDirectChannelEvents(reportToken);

        doAnswer((Answer<Void>) i -> {
            sharedMemory.getValue().close();
            return null;
        }).when(mVirtualSensorDirectChannelCallback)
                .onDirectChannelDestroyed(channelHandle.getValue());
    }

    @Test
    public void directConnection_memoryFile_injectEvents_withHelperWriter() throws Exception {
        mVirtualSensorDirectChannelWriter = new VirtualSensorDirectChannelWriter();

        doAnswer((Answer<Void>) i -> {
            int channelHandle = (int) i.getArguments()[0];
            SharedMemory sharedMemory = (SharedMemory) i.getArguments()[1];
            mVirtualSensorDirectChannelWriter.addChannel(channelHandle, sharedMemory);
            return null;
        }).when(mVirtualSensorDirectChannelCallback).onDirectChannelCreated(anyInt(), any());

        doAnswer((Answer<Void>) i -> {
            int channelHandle = (int) i.getArguments()[0];
            mVirtualSensorDirectChannelWriter.removeChannel(channelHandle);
            return null;
        }).when(mVirtualSensorDirectChannelCallback).onDirectChannelDestroyed(anyInt());

        doAnswer((Answer<Void>) i -> {
            int channelHandle = (int) i.getArguments()[0];
            VirtualSensor sensor = (VirtualSensor) i.getArguments()[1];
            int rateLevel = (int) i.getArguments()[2];
            int reportToken = (int) i.getArguments()[3];
            assertThat(mVirtualSensorDirectChannelWriter.configureChannel(
                    channelHandle, sensor, rateLevel, reportToken)).isTrue();
            Random random = new Random();

            for (int eventCount = 0; eventCount < SENSOR_EVENT_COUNT * 2; ++eventCount) {
                float[] values = new float[] {
                        reportToken + eventCount * 0.01f,
                        reportToken + eventCount * 0.02f,
                        reportToken + eventCount * 0.03f,
                };
                synchronized (mDirectChannelIoLock) {
                    assertThat(mVirtualSensorDirectChannelWriter.writeSensorEvent(sensor,
                            new VirtualSensorEvent.Builder(values)
                                    .setTimestampNanos(System.nanoTime())
                                    .build()))
                            .isTrue();
                }
                try {
                    Thread.sleep(random.nextInt(10));  // Sleep random time of 0-20ms.
                } catch (InterruptedException e) {
                    fail("Interrupted while writing sensor events: " + e);
                }
            }
            return null;
        }).when(mVirtualSensorDirectChannelCallback)
                .onDirectChannelConfigured(anyInt(), any(), eq(RATE_NORMAL), anyInt());

        setUpDirectChannel();

        Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);
        int reportToken = mDirectChannel.configure(sensor, RATE_NORMAL);
        verifyDirectChannelEvents(reportToken);
    }

    private static class VirtualSensorEventListener implements SensorEventListener {
        private final BlockingQueue<SensorEvent> mEvents = new LinkedBlockingQueue<>();

        @Override
        public void onSensorChanged(SensorEvent event) {
            try {
                mEvents.put(event);
            } catch (InterruptedException ex) {
                fail("Interrupted while adding a SensorEvent to the queue");
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        public void assertReceivedSensorEvent(Sensor sensor, VirtualSensorEvent expected) {
            SensorEvent event = waitForEvent();
            if (event == null) {
                fail("Did not receive SensorEvent with values "
                        + Arrays.toString(expected.getValues()));
            }

            assertThat(event.sensor).isEqualTo(sensor);
            assertThat(event.values).isEqualTo(expected.getValues());
            assertThat(event.timestamp).isEqualTo(expected.getTimestampNanos());
        }

        public void assertNoMoreEvents() {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SensorEvent event = waitForEvent();
            if (event != null) {
                fail("Received extra SensorEvent with values: " + Arrays.toString(event.values));
            }
        }

        private SensorEvent waitForEvent() {
            try {
                return mEvents.poll(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail("Interrupted while waiting for SensorEvent");
                return null;
            }
        }
    }

    private void writeDirectChannelEvents(int reportToken, SharedMemory sharedMemory) {
        int offset = 0;
        int eventCount = 0;
        ByteBuffer event = ByteBuffer.allocate(SENSOR_EVENT_SIZE);
        event.order(ByteOrder.nativeOrder());
        Random random = new Random();
        ByteBuffer memoryMapping = null;
        try {
            memoryMapping = sharedMemory.mapReadWrite();
        } catch (ErrnoException e) {
            sharedMemory.close();
            fail("Could not map the shared memory for IO: " + e);
        }

        while (eventCount < SENSOR_EVENT_COUNT * 2) {
            event.position(0);
            event.putInt(SENSOR_EVENT_SIZE);
            event.putInt(reportToken);
            event.putInt(TYPE_ACCELEROMETER);
            event.putInt(++eventCount);
            event.putLong(System.nanoTime());
            // sensor values
            event.putFloat(reportToken + eventCount * 0.01f);
            event.putFloat(reportToken + eventCount * 0.02f);
            event.putFloat(reportToken + eventCount * 0.03f);

            memoryMapping.position(offset);
            synchronized (mDirectChannelIoLock) {
                memoryMapping.put(event.array(), 0, SENSOR_EVENT_SIZE);
            }
            try {
                Thread.sleep(random.nextInt(10));  // Sleep random time of 0-20ms.
            } catch (InterruptedException e) {
                sharedMemory.close();
                fail("Interrupted while writing sensor events: " + e);
            }

            offset += SENSOR_EVENT_SIZE;
            if (offset + SENSOR_EVENT_SIZE >= sharedMemory.getSize()) {
                offset = 0;
            }
        }
    }

    private void verifyDirectChannelEvents(int reportToken) throws Exception {
        int offset = 0;
        int eventCount = 0;
        ByteBuffer byteBuffer = ByteBuffer.allocate(SENSOR_EVENT_SIZE);
        byteBuffer.order(ByteOrder.nativeOrder());

        while (eventCount < SENSOR_EVENT_COUNT * 2) {
            synchronized (mDirectChannelIoLock) {
                assertThat(mMemoryFile.readBytes(byteBuffer.array(), offset, 0, SENSOR_EVENT_SIZE))
                        .isEqualTo(SENSOR_EVENT_SIZE);
            }
            byteBuffer.position(0);
            int eventSize = byteBuffer.getInt();
            int actualReportToken = byteBuffer.getInt();
            if (reportToken != actualReportToken) {
                Thread.sleep(10);
                continue;
            }
            int sensorType = byteBuffer.getInt();
            int eventCounter = byteBuffer.getInt();

            if (eventCounter > 0) {
                if (eventCounter != eventCount + 1) {
                    Thread.sleep(10);
                    continue;
                }
                eventCount++;

                assertThat(eventSize).isEqualTo(SENSOR_EVENT_SIZE);
                assertThat(sensorType).isEqualTo(TYPE_ACCELEROMETER);

                byteBuffer.getLong();  // timestamp

                // verify the sensor values
                assertThat(byteBuffer.getFloat())
                        .isEqualTo(reportToken + eventCounter * 0.1f);
                assertThat(byteBuffer.getFloat())
                        .isEqualTo(reportToken + eventCounter * 0.2f);
                assertThat(byteBuffer.getFloat())
                        .isEqualTo(reportToken + eventCounter * 0.3f);

                offset += SENSOR_EVENT_SIZE;
                if (offset + SENSOR_EVENT_SIZE >= SHARED_MEMORY_SIZE) {
                    offset = 0;
                }
            }
        }
    }

    private static HardwareBuffer createSensorsHardwareBuffer() {
        return HardwareBuffer.create(
                /*width=*/SHARED_MEMORY_SIZE, /*height=*/1, HardwareBuffer.BLOB, /*layers=*/1,
                HardwareBuffer.USAGE_CPU_READ_OFTEN
                        | HardwareBuffer.USAGE_GPU_DATA_BUFFER
                        | HardwareBuffer.USAGE_SENSOR_DIRECT_DATA);
    }
}
