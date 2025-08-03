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
import static android.hardware.SensorDirectChannel.RATE_STOP;
import static android.hardware.SensorDirectChannel.RATE_VERY_FAST;
import static android.hardware.SensorDirectChannel.TYPE_HARDWARE_BUFFER;
import static android.hardware.SensorDirectChannel.TYPE_MEMORY_FILE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.companion.virtual.sensor.VirtualSensorConfig;
import android.hardware.Sensor;
import android.os.Parcel;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualSensorConfigTest {

    private static final String SENSOR_NAME = "VirtualSensorName";
    private static final String SENSOR_VENDOR = "VirtualSensorVendor";

    @Test
    public void parcelAndUnparcel_matches() {
        final VirtualSensorConfig originalConfig =
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, SENSOR_NAME)
                        .setVendor(SENSOR_VENDOR)
                        .setHighestDirectReportRateLevel(RATE_VERY_FAST)
                        .setDirectChannelTypesSupported(TYPE_MEMORY_FILE)
                        .setMaximumRange(1.2f)
                        .setResolution(3.4f)
                        .setPower(5.6f)
                        .setMinDelay(7)
                        .setMaxDelay(8)
                        .build();
        final Parcel parcel = Parcel.obtain();
        originalConfig.writeToParcel(parcel, /* flags= */ 0);
        parcel.setDataPosition(0);
        final VirtualSensorConfig recreatedConfig =
                VirtualSensorConfig.CREATOR.createFromParcel(parcel);
        assertThat(recreatedConfig.getType()).isEqualTo(originalConfig.getType());
        assertThat(recreatedConfig.getName()).isEqualTo(originalConfig.getName());
        assertThat(recreatedConfig.getVendor()).isEqualTo(originalConfig.getVendor());
        assertThat(recreatedConfig.getHighestDirectReportRateLevel()).isEqualTo(RATE_VERY_FAST);
        assertThat(recreatedConfig.getDirectChannelTypesSupported()).isEqualTo(TYPE_MEMORY_FILE);
        assertThat(recreatedConfig.getMaximumRange()).isEqualTo(1.2f);
        assertThat(recreatedConfig.getResolution()).isEqualTo(3.4f);
        assertThat(recreatedConfig.getPower()).isEqualTo(5.6f);
        assertThat(recreatedConfig.getMinDelay()).isEqualTo(7);
        assertThat(recreatedConfig.getMaxDelay()).isEqualTo(8);

        // From hardware/libhardware/include/hardware/sensors-base.h:
        //   0x400 is SENSOR_FLAG_DIRECT_CHANNEL_ASHMEM (i.e. TYPE_MEMORY_FILE)
        //   0x800 is SENSOR_FLAG_DIRECT_CHANNEL_GRALLOC (i.e. TYPE_HARDWARE_BUFFER)
        //   7 is SENSOR_FLAG_SHIFT_DIRECT_REPORT
        assertThat(recreatedConfig.getFlags()).isEqualTo(0x400 | RATE_VERY_FAST << 7);
    }

    @Test
    public void virtualSensorConfig_invalidName_throwsException() {
        assertThrows(
                NullPointerException.class,
                () -> new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, null));
    }

    @Test
    public void virtualSensorConfig_invalidType_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new VirtualSensorConfig.Builder(Sensor.TYPE_ALL, SENSOR_NAME));

        assertThrows(
                IllegalArgumentException.class,
                () -> new VirtualSensorConfig.Builder(0, SENSOR_NAME));
    }

    @Test
    public void hardwareBufferDirectChannelTypeSupported_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, SENSOR_NAME)
                        .setDirectChannelTypesSupported(TYPE_HARDWARE_BUFFER | TYPE_MEMORY_FILE));
    }

    @Test
    public void directChannelTypeSupported_missingHighestReportRateLevel_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, SENSOR_NAME)
                        .setDirectChannelTypesSupported(TYPE_MEMORY_FILE)
                        .build());
    }

    @Test
    public void directChannelTypeSupported_missingDirectChannelTypeSupported_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, SENSOR_NAME)
                        .setHighestDirectReportRateLevel(RATE_VERY_FAST)
                        .build());
    }

    @Test
    public void sensorConfig_onlyRequiredFields() {
        final VirtualSensorConfig config =
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, SENSOR_NAME).build();
        assertThat(config.getVendor()).isNull();
        assertThat(config.getHighestDirectReportRateLevel()).isEqualTo(RATE_STOP);
        assertThat(config.getDirectChannelTypesSupported()).isEqualTo(0);
        assertThat(config.getMaximumRange()).isEqualTo(0f);
        assertThat(config.getResolution()).isEqualTo(0f);
        assertThat(config.getPower()).isEqualTo(0f);
        assertThat(config.getMinDelay()).isEqualTo(0);
        assertThat(config.getMaxDelay()).isEqualTo(0);
        assertThat(config.getFlags()).isEqualTo(0);
    }
}
