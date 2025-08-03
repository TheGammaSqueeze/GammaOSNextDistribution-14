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
import static android.hardware.Sensor.TYPE_GYROSCOPE;
import static android.hardware.SensorDirectChannel.RATE_NORMAL;
import static android.hardware.SensorDirectChannel.RATE_STOP;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.companion.virtual.sensor.VirtualSensor;
import android.companion.virtual.sensor.VirtualSensorDirectChannelWriter;
import android.companion.virtual.sensor.VirtualSensorEvent;
import android.os.SharedMemory;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;


@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualSensorDirectChannelWriterTest {

    private static final int CHANNEL_HANDLE = 7;
    private static final int SENSOR_HANDLE = 42;
    private static final int REPORT_TOKEN = 15;
    private static final String ACCELEROMETER_SENSOR_NAME = "VirtualAccelerometer";
    private static final String GYROSCOPE_SENSOR_NAME = "VirtualGyroscope";
    private static final String SHARED_MEMORY_NAME = "VirtualSensorSharedMemory";
    private static final int SENSOR_EVENT_SIZE = 104;
    private static final int SHARED_MEMORY_SIZE = SENSOR_EVENT_SIZE * 3;

    private final VirtualSensor mAccelerometer = new VirtualSensor(
            SENSOR_HANDLE, TYPE_ACCELEROMETER, ACCELEROMETER_SENSOR_NAME);
    private final VirtualSensor mGyroscope = new VirtualSensor(
            SENSOR_HANDLE + 1, TYPE_GYROSCOPE, GYROSCOPE_SENSOR_NAME);

    private final VirtualSensorDirectChannelWriter mWriter = new VirtualSensorDirectChannelWriter();

    @After
    public void tearDown() {
        mWriter.close();
    }

    @Test
    public void configureChannel_rateNormal_noChannelAdded_returnsError() {
        assertThat(
                mWriter.configureChannel(CHANNEL_HANDLE, mAccelerometer, RATE_NORMAL, REPORT_TOKEN))
                .isFalse();
    }

    @Test
    public void configureChannel_rateStop_noChannelAdded_returnsError() {
        assertThat(
                mWriter.configureChannel(CHANNEL_HANDLE, mAccelerometer, RATE_STOP, REPORT_TOKEN))
                .isFalse();
    }

    @Test
    public void addChannel_nullSharedMemory_throwsException() {
        assertThrows(
                NullPointerException.class,
                () -> mWriter.addChannel(CHANNEL_HANDLE, /*sharedMemory=*/null));
    }

    @Test
    public void removeChannel_closesSharedMemory() throws Exception {
        SharedMemory sharedMemory = SharedMemory.create(SHARED_MEMORY_NAME, SHARED_MEMORY_SIZE);
        assertThat(sharedMemory.getSize()).isGreaterThan(0);

        mWriter.addChannel(CHANNEL_HANDLE, sharedMemory);
        mWriter.removeChannel(CHANNEL_HANDLE);

        // The shared memory's file descriptor is closed.
        assertThrows(IllegalStateException.class, sharedMemory::getSize);
    }

    @Test
    public void writeEvent_nullEvent_throwsException() {
        assertThrows(
                NullPointerException.class,
                () -> mWriter.writeSensorEvent(mAccelerometer, /*event=*/null));
    }

    @Test
    public void writeEvent_nullSensor_throwsException() {
        assertThrows(
                NullPointerException.class,
                () -> mWriter.writeSensorEvent(/*sensor=*/null,
                        new VirtualSensorEvent.Builder(new float[]{1, 2, 3}).build()));
    }

    @Test
    public void writeEvent_noConfiguredChannel_returnsError() {
        assertThat(mWriter.writeSensorEvent(
                mAccelerometer, new VirtualSensorEvent.Builder(new float[]{1, 2, 3}).build()))
                .isFalse();
    }

    @Test
    public void writeEvent_channelStopped_returnsError() throws Exception {
        SharedMemory sharedMemory = SharedMemory.create(SHARED_MEMORY_NAME, SHARED_MEMORY_SIZE);
        mWriter.addChannel(CHANNEL_HANDLE, sharedMemory);

        assertThat(
                mWriter.configureChannel(CHANNEL_HANDLE, mAccelerometer, RATE_NORMAL, REPORT_TOKEN))
                .isTrue();
        assertThat(
                mWriter.configureChannel(CHANNEL_HANDLE, mAccelerometer, RATE_STOP, REPORT_TOKEN))
                .isTrue();

        assertThat(mWriter.writeSensorEvent(
                mAccelerometer, new VirtualSensorEvent.Builder(new float[]{1, 2, 3}).build()))
                .isFalse();
    }

    @Test
    public void writeEvent_channelRemoved_returnsError() throws Exception {
        SharedMemory sharedMemory = SharedMemory.create(SHARED_MEMORY_NAME, SHARED_MEMORY_SIZE);
        mWriter.addChannel(CHANNEL_HANDLE, sharedMemory);
        assertThat(
                mWriter.configureChannel(CHANNEL_HANDLE, mAccelerometer, RATE_NORMAL, REPORT_TOKEN))
                .isTrue();
        mWriter.removeChannel(CHANNEL_HANDLE);

        assertThat(mWriter.writeSensorEvent(
                mAccelerometer, new VirtualSensorEvent.Builder(new float[]{1, 2, 3}).build()))
                .isFalse();
    }

    @Test
    public void writeEvent_singleChannel() throws Exception {
        testWriteSensorEvents(
                List.of(new SharedMemoryWrapper(CHANNEL_HANDLE, SHARED_MEMORY_SIZE)),
                List.of(mAccelerometer));
    }

    @Test
    public void writeEvent_multipleChannels() throws Exception {
        testWriteSensorEvents(
                List.of(
                        new SharedMemoryWrapper(CHANNEL_HANDLE, SHARED_MEMORY_SIZE),
                        new SharedMemoryWrapper(CHANNEL_HANDLE + 1, SHARED_MEMORY_SIZE * 2)),
                List.of(mAccelerometer));
    }

    @Test
    public void writeEvent_multipleSensors_singleChannel() throws Exception {
        testWriteSensorEvents(
                List.of(new SharedMemoryWrapper(CHANNEL_HANDLE, SHARED_MEMORY_SIZE)),
                List.of(mAccelerometer, mGyroscope));
    }

    @Test
    public void writeEvent_multipleSensors_multipleChannels() throws Exception {
        testWriteSensorEvents(
                List.of(
                        new SharedMemoryWrapper(CHANNEL_HANDLE, SHARED_MEMORY_SIZE),
                        new SharedMemoryWrapper(CHANNEL_HANDLE + 1, SHARED_MEMORY_SIZE * 2)),
                List.of(mAccelerometer, mGyroscope));
    }

    private void testWriteSensorEvents(
            List<SharedMemoryWrapper> channels, List<VirtualSensor> sensors) {
        for (SharedMemoryWrapper channel : channels) {
            for (VirtualSensor sensor : sensors) {
                assertThat(
                        mWriter.configureChannel(
                                channel.mChannelHandle, sensor, RATE_NORMAL,
                                /*reportToken=*/sensor.getHandle()))
                        .isTrue();
            }
        }

        for (int i = 0; i < 8; ++i) {
            VirtualSensorEvent event = new VirtualSensorEvent.Builder(new float[]{i, i + 1, i + 2})
                    .setTimestampNanos(System.nanoTime())
                    .build();

            for (VirtualSensor sensor : sensors) {
                int sensorIndex = sensors.indexOf(sensor) + 1;
                if (i % sensorIndex == 0) {
                    assertThat(mWriter.writeSensorEvent(sensor, event)).isTrue();

                    for (SharedMemoryWrapper channel : channels) {
                        channel.verifyEvent(sensor, event, /*eventCount=*/i / sensorIndex + 1);
                    }
                }
            }
        }
    }

    private final class SharedMemoryWrapper {
        private final int mChannelHandle;
        private final SharedMemory mSharedMemory;
        private final ByteBuffer mByteBuffer;
        private int mReadOffset = 0;

        SharedMemoryWrapper(int channelHandle, int size) throws Exception {
            mChannelHandle = channelHandle;
            mSharedMemory = SharedMemory.create(SHARED_MEMORY_NAME, size);
            mByteBuffer = mSharedMemory.mapReadOnly();
            mByteBuffer.order(ByteOrder.nativeOrder());

            mWriter.addChannel(mChannelHandle, mSharedMemory);
        }

        void verifyEvent(VirtualSensor sensor, VirtualSensorEvent event, int eventCount) {
            mByteBuffer.position(mReadOffset);
            assertThat(mByteBuffer.getInt()).isEqualTo(SENSOR_EVENT_SIZE);
            assertThat(mByteBuffer.getInt()).isEqualTo(/*reportToken=*/sensor.getHandle());
            assertThat(mByteBuffer.getInt()).isEqualTo(sensor.getType());
            assertThat(mByteBuffer.getInt()).isEqualTo(eventCount);
            assertThat(mByteBuffer.getLong()).isEqualTo(event.getTimestampNanos());
            for (int i = 0; i < 16; ++i) {
                if (i < event.getValues().length) {
                    assertThat(mByteBuffer.getFloat()).isEqualTo(event.getValues()[i]);
                } else {
                    assertThat(mByteBuffer.getFloat()).isEqualTo(0);
                }
            }
            assertThat(mByteBuffer.getInt()).isEqualTo(0);

            mReadOffset += SENSOR_EVENT_SIZE;
            if (mReadOffset + SENSOR_EVENT_SIZE >= mSharedMemory.getSize()) {
                mReadOffset = 0;
            }
        }

    }
}
