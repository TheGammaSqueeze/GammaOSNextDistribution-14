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
package com.android.car;

import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER;
import static android.car.VehicleAreaSeat.SEAT_ROW_1_LEFT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.CarRemoteDeviceManager;
import android.car.CarRemoteDeviceManager.StateCallback;
import android.car.occupantconnection.ICarRemoteDevice;
import android.car.occupantconnection.IStateCallback;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.IBinder;
import android.os.RemoteException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.Executor;

@RunWith(MockitoJUnitRunner.class)
public final class CarRemoteDeviceManagerUnitTest {

    private static final String PACKAGE_NAME = "my_package_name";
    private static final int TIMEOUT_MS = 1000;

    @Mock
    private Car mCar;
    @Mock
    private IBinder mBinder;
    @Mock
    private ICarRemoteDevice mService;
    @Mock
    private Context mContext;
    @Mock
    private StateCallback mStateCallback;

    private final OccupantZoneInfo mReceiverZone =
            new OccupantZoneInfo(/* zoneId= */ 0, OCCUPANT_TYPE_DRIVER, SEAT_ROW_1_LEFT);

    private CarRemoteDeviceManager mRemoteDeviceManager;

    @Before
    public void setUp() {
        when(mBinder.queryLocalInterface(anyString())).thenReturn(mService);
        when(mCar.getContext()).thenReturn(mContext);
        when(mContext.getPackageName()).thenReturn(PACKAGE_NAME);

        mRemoteDeviceManager = new CarRemoteDeviceManager(mCar, mBinder);
    }

    @Test
    public void testGetEndpointPackageInfoWithNullParameters_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mRemoteDeviceManager.getEndpointPackageInfo(/* receiverZone= */ null));
    }

    @Test
    public void testGetEndpointPackageInfo() throws RemoteException {
        int zoneId = 0;
        OccupantZoneInfo zone =
                new OccupantZoneInfo(zoneId, OCCUPANT_TYPE_DRIVER, SEAT_ROW_1_LEFT);
        PackageInfo expectedValue = mock(PackageInfo.class);
        when(mService.getEndpointPackageInfo(eq(zoneId), eq(PACKAGE_NAME)))
                .thenReturn(expectedValue);

        assertThat(mRemoteDeviceManager.getEndpointPackageInfo(zone)).isEqualTo(expectedValue);
    }

    @Test
    public void testRegisterStateCallbackWithNullParameters_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mRemoteDeviceManager.registerStateCallback(/* executor= */ null,
                        mock(StateCallback.class)));
        assertThrows(NullPointerException.class,
                () -> mRemoteDeviceManager.registerStateCallback(Runnable::run,
                        /* callback= */ null));
    }

    @Test
    public void testRegisterStateCallbackWithOccupantZoneStateChange() throws RemoteException {
        IStateCallback[] binderCallback = new IStateCallback[1];
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            assertThat(args.length).isEqualTo(2);

            String packageName = (String) args[0];
            assertThat(packageName).isEqualTo(PACKAGE_NAME);

            binderCallback[0] = (IStateCallback) args[1];
            // Don't call binderCallback[0].on*StateChanged() here, because mStateCallback
            // is added AFTER this method returns. So if we call
            // binderCallback[0].on*StateChanged here, mStateCallback will not be invoked.
            return null;
        }).when(mService).registerStateCallback(anyString(), any());

        int occupantZoneStates = 123;
        mRemoteDeviceManager.registerStateCallback(command -> {
            command.run();
            // Verify that mStateCallback is invoked on the Executor.
            verify(mStateCallback, timeout(TIMEOUT_MS))
                    .onOccupantZoneStateChanged(eq(mReceiverZone), eq(occupantZoneStates));
        }, mStateCallback);
        binderCallback[0].onOccupantZoneStateChanged(mReceiverZone, occupantZoneStates);
    }

    @Test
    public void testRegisterStateCallbackWithAppStateChange() throws RemoteException {
        IStateCallback[] binderCallback = new IStateCallback[1];
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            String packageName = (String) args[0];
            assertThat(packageName).isEqualTo(PACKAGE_NAME);
            binderCallback[0] = (IStateCallback) args[1];
            // Don't call binderCallback[0].on*StateChanged() here, because mStateCallback
            // is added AFTER this method returns. So if we call
            // binderCallback[0].on*StateChanged here, mStateCallback will not be invoked.
            return null;
        }).when(mService).registerStateCallback(anyString(), any());

        int appStates = 456;
        mRemoteDeviceManager.registerStateCallback(command -> {
            command.run();
            // Verify that mStateCallback is invoked on the Executor.
            verify(mStateCallback, timeout(1000))
                    .onAppStateChanged(eq(mReceiverZone), eq(appStates));
        }, mStateCallback);
        binderCallback[0].onAppStateChanged(mReceiverZone, appStates);
    }

    @Test
    public void testRegisterDuplicateStateCallback_throwsException() {
        mRemoteDeviceManager.registerStateCallback(mock(Executor.class), mock(StateCallback.class));

        // The client shouldn't register another StateCallback.
        assertThrows(IllegalStateException.class,
                () -> mRemoteDeviceManager.registerStateCallback(mock(Executor.class),
                        mock(StateCallback.class)));
    }

    @Test
    public void testUnregisterNonexistentStateCallback_throwsException() {
        assertThrows(IllegalStateException.class,
                () -> mRemoteDeviceManager.unregisterStateCallback());
    }

    @Test
    public void testUnregisterStateCallback() throws RemoteException {
        mRemoteDeviceManager.registerStateCallback(mock(Executor.class), mStateCallback);
        mRemoteDeviceManager.unregisterStateCallback();

        verify(mService).unregisterStateCallback(eq(PACKAGE_NAME));
        // The mStateCallback must be unregistered, otherwise it will throw an exception.
        mRemoteDeviceManager.registerStateCallback(mock(Executor.class), mStateCallback);
    }

    @Test
    public void testSetOccupantZonePowerOn() throws RemoteException {
        int zoneId = 0;
        OccupantZoneInfo zone =
                new OccupantZoneInfo(zoneId, OCCUPANT_TYPE_DRIVER, SEAT_ROW_1_LEFT);

        mRemoteDeviceManager.setOccupantZonePower(zone, true);
        verify(mService).setOccupantZonePower(zone, true);
    }

    @Test
    public void testSetOccupantZonePowerOff() throws RemoteException {
        int zoneId = 0;
        OccupantZoneInfo zone =
                new OccupantZoneInfo(zoneId, OCCUPANT_TYPE_DRIVER, SEAT_ROW_1_LEFT);

        mRemoteDeviceManager.setOccupantZonePower(zone, false);
        verify(mService).setOccupantZonePower(zone, false);
    }

    @Test
    public void testIsOccupantZonePowerOn() throws RemoteException {
        int zoneId = 0;
        OccupantZoneInfo zone =
                new OccupantZoneInfo(zoneId, OCCUPANT_TYPE_DRIVER, SEAT_ROW_1_LEFT);
        boolean expectedValue = true;
        when(mService.isOccupantZonePowerOn(zone)).thenReturn(expectedValue);

        assertThat(mRemoteDeviceManager.isOccupantZonePowerOn(zone)).isEqualTo(expectedValue);
    }
}
