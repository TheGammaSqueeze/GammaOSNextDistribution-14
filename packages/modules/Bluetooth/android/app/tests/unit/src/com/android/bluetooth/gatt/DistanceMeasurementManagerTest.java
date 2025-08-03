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

package com.android.bluetooth.gatt;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.DistanceMeasurementMethod;
import android.bluetooth.le.DistanceMeasurementParams;
import android.bluetooth.le.DistanceMeasurementResult;
import android.bluetooth.le.IDistanceMeasurementCallback;
import android.os.RemoteException;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.btservice.AdapterService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

/**
 * Test cases for {@link DistanceMeasurementManager}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DistanceMeasurementManagerTest {
    @Mock private DistanceMeasurementNativeInterface mDistanceMeasurementNativeInterface;
    @Mock private AdapterService mAdapterService;
    @Mock private IDistanceMeasurementCallback mCallback;
    private DistanceMeasurementManager mDistanceMeasurementManager;
    private UUID mUuid;
    private BluetoothDevice mDevice;

    private static final String IDENTITY_ADDRESS = "00:01:02:03:04:05";
    private static final int RSSI_FREQUENCY_LOW = 3000;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(IDENTITY_ADDRESS).when(mAdapterService).getIdentityAddress(IDENTITY_ADDRESS);
        DistanceMeasurementNativeInterface.setInstance(mDistanceMeasurementNativeInterface);
        mDistanceMeasurementManager = new DistanceMeasurementManager(mAdapterService);
        mUuid = UUID.randomUUID();
        mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(IDENTITY_ADDRESS);
    }

    @After
    public void tearDown() throws Exception {
        mDistanceMeasurementManager.cleanup();
        DistanceMeasurementNativeInterface.setInstance(null);
    }

    @Test
    public void testStartRssiTracker() {
        DistanceMeasurementParams params = new DistanceMeasurementParams.Builder(mDevice)
                .setDurationSeconds(1000)
                .setFrequency(DistanceMeasurementParams.REPORT_FREQUENCY_LOW)
                .setMethodId(DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI)
                .build();
        mDistanceMeasurementManager.startDistanceMeasurement(mUuid, params, mCallback);
        verify(mDistanceMeasurementNativeInterface).startDistanceMeasurement(
            IDENTITY_ADDRESS, RSSI_FREQUENCY_LOW,
            DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI);
    }

    @Test
    public void testStopRssiTracker() {
        DistanceMeasurementParams params = new DistanceMeasurementParams.Builder(mDevice)
                .setDurationSeconds(1000)
                .setFrequency(DistanceMeasurementParams.REPORT_FREQUENCY_LOW)
                .setMethodId(DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI)
                .build();
        mDistanceMeasurementManager.startDistanceMeasurement(mUuid, params, mCallback);
        mDistanceMeasurementManager.stopDistanceMeasurement(mUuid, mDevice,
                DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI, false);
        verify(mDistanceMeasurementNativeInterface).stopDistanceMeasurement(
            IDENTITY_ADDRESS, DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI);
    }

    @Test
    public void testHandleRssiStarted() throws RemoteException {
        DistanceMeasurementParams params = new DistanceMeasurementParams.Builder(mDevice)
                .setDurationSeconds(1000)
                .setFrequency(DistanceMeasurementParams.REPORT_FREQUENCY_LOW)
                .setMethodId(DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI)
                .build();
        mDistanceMeasurementManager.startDistanceMeasurement(mUuid, params, mCallback);
        verify(mDistanceMeasurementNativeInterface).startDistanceMeasurement(
            IDENTITY_ADDRESS, RSSI_FREQUENCY_LOW,
            DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI);
        mDistanceMeasurementManager.onDistanceMeasurementStarted(IDENTITY_ADDRESS,
                DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI);
        verify(mCallback).onStarted(mDevice);
    }

    @Test
    public void testHandleRssiStartFail() throws RemoteException {
        DistanceMeasurementParams params = new DistanceMeasurementParams.Builder(mDevice)
                .setDurationSeconds(1000)
                .setFrequency(DistanceMeasurementParams.REPORT_FREQUENCY_LOW)
                .setMethodId(DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI)
                .build();
        mDistanceMeasurementManager.startDistanceMeasurement(mUuid, params, mCallback);
        verify(mDistanceMeasurementNativeInterface).startDistanceMeasurement(
            IDENTITY_ADDRESS, RSSI_FREQUENCY_LOW,
            DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI);
        mDistanceMeasurementManager.onDistanceMeasurementStartFail(IDENTITY_ADDRESS,
                BluetoothStatusCodes.ERROR_DISTANCE_MEASUREMENT_INTERNAL,
                DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI);
        verify(mCallback).onStartFail(mDevice,
                BluetoothStatusCodes.ERROR_DISTANCE_MEASUREMENT_INTERNAL);
    }

    @Test
    public void testHandleRssiStopped() throws RemoteException {
        DistanceMeasurementParams params = new DistanceMeasurementParams.Builder(mDevice)
                .setDurationSeconds(1000)
                .setFrequency(DistanceMeasurementParams.REPORT_FREQUENCY_LOW)
                .setMethodId(DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI)
                .build();
        mDistanceMeasurementManager.startDistanceMeasurement(mUuid, params, mCallback);
        mDistanceMeasurementManager.onDistanceMeasurementStarted(IDENTITY_ADDRESS,
                DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI);
        verify(mCallback).onStarted(mDevice);

        mDistanceMeasurementManager.onDistanceMeasurementStopped(IDENTITY_ADDRESS,
                BluetoothStatusCodes.REASON_REMOTE_REQUEST,
                DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI);
        verify(mCallback).onStopped(mDevice,
                BluetoothStatusCodes.REASON_REMOTE_REQUEST);
    }

    @Test
    public void testHandleRssiResult() throws RemoteException {
        DistanceMeasurementParams params = new DistanceMeasurementParams.Builder(mDevice)
                .setDurationSeconds(1000)
                .setFrequency(DistanceMeasurementParams.REPORT_FREQUENCY_LOW)
                .setMethodId(DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI)
                .build();
        mDistanceMeasurementManager.startDistanceMeasurement(mUuid, params, mCallback);
        mDistanceMeasurementManager.onDistanceMeasurementStarted(IDENTITY_ADDRESS,
                DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI);
        verify(mCallback).onStarted(mDevice);

        mDistanceMeasurementManager.onDistanceMeasurementResult(IDENTITY_ADDRESS,
                100, 100, -1, -1, -1, -1,
                DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI);
        ArgumentCaptor<DistanceMeasurementResult> result =
                ArgumentCaptor.forClass(DistanceMeasurementResult.class);
        verify(mCallback, times(1)).onResult(eq(mDevice), result.capture());
        assertThat(result.getValue().getResultMeters()).isEqualTo(1.00);
        assertThat(result.getValue().getErrorMeters()).isEqualTo(1.00);
        assertThat(result.getValue().getAzimuthAngle()).isEqualTo(Double.NaN);
        assertThat(result.getValue().getErrorAzimuthAngle()).isEqualTo(Double.NaN);
        assertThat(result.getValue().getAltitudeAngle()).isEqualTo(Double.NaN);
        assertThat(result.getValue().getErrorAltitudeAngle()).isEqualTo(Double.NaN);
    }

    @Test
    public void testReceivedResultAfterStopped() throws RemoteException {
        DistanceMeasurementParams params = new DistanceMeasurementParams.Builder(mDevice)
                .setDurationSeconds(1000)
                .setFrequency(DistanceMeasurementParams.REPORT_FREQUENCY_LOW)
                .setDurationSeconds(DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI)
                .build();
        mDistanceMeasurementManager.startDistanceMeasurement(mUuid, params, mCallback);
        mDistanceMeasurementManager.stopDistanceMeasurement(mUuid, mDevice,
                DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI, false);
        verify(mDistanceMeasurementNativeInterface).stopDistanceMeasurement(
            IDENTITY_ADDRESS, DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI);
        mDistanceMeasurementManager.onDistanceMeasurementResult(IDENTITY_ADDRESS,
                100, 100, -1, -1, -1, -1,
                DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI);
        DistanceMeasurementResult result = new DistanceMeasurementResult.Builder(
                1.00, 1.00).build();
        verify(mCallback, after(100).never()).onResult(mDevice, result);
    }
}