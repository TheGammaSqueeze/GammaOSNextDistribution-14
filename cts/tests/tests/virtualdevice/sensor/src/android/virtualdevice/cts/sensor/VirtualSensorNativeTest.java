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

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_SENSORS;
import static android.hardware.Sensor.TYPE_ACCELEROMETER;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNotNull;

import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.flags.Flags;
import android.companion.virtual.sensor.VirtualSensorCallback;
import android.companion.virtual.sensor.VirtualSensorConfig;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.display.VirtualDisplay;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.Display;
import android.virtualdevice.cts.common.VirtualDeviceRule;
import android.virtualdevice.cts.sensor.util.NativeSensorTestActivity;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executors;

/** Tests for native sensor behavior for virtual devices. */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualSensorNativeTest {

    private static final String VIRTUAL_SENSOR_NAME = "virtual device accelerometer name";

    @Rule
    public VirtualDeviceRule mVirtualDeviceRule = VirtualDeviceRule.createDefault();

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private VirtualDeviceManager.VirtualDevice mVirtualDevice;
    @Mock
    private VirtualSensorCallback mVirtualSensorCallback;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mVirtualDevice = mVirtualDeviceRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                        .addVirtualSensorConfig(
                                new VirtualSensorConfig.Builder(
                                        TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME)
                                        .build())
                        .setVirtualSensorCallback(
                                Executors.newSingleThreadExecutor(), mVirtualSensorCallback)
                        .build());
    }

    /** Activity running on the default device should get the default device sensors by default. */
    @Test
    public void activityOnDefaultDisplayGetsDefaultDeviceSensor() {
        Context deviceContext = mContext.createDeviceContext(Context.DEVICE_ID_DEFAULT);
        SensorManager sensorManager = deviceContext.getSystemService(SensorManager.class);
        Sensor defaultDeviceSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        assumeNotNull(defaultDeviceSensor);

        NativeSensorTestActivity activity = mVirtualDeviceRule.startActivityOnDisplaySync(
                        Display.DEFAULT_DISPLAY, NativeSensorTestActivity.class);

        assertThat(activity.nativeGetDefaultAccelerometerName())
                .isEqualTo(defaultDeviceSensor.getName());
    }

    /** Activity running on the virtual device should get the virtual device sensors by default. */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NATIVE_VDM)
    @Test
    public void activityOnVirtualDisplayGetsVirtualDeviceSensor() {
        VirtualDisplay virtualDisplay = mVirtualDeviceRule.createManagedVirtualDisplay(
                mVirtualDevice, VirtualDeviceRule.TRUSTED_VIRTUAL_DISPLAY_CONFIG);
        NativeSensorTestActivity activity = mVirtualDeviceRule.startActivityOnDisplaySync(
                virtualDisplay, NativeSensorTestActivity.class);

        assertThat(activity.nativeGetDefaultAccelerometerName())
                .isEqualTo(VIRTUAL_SENSOR_NAME);
    }
}
