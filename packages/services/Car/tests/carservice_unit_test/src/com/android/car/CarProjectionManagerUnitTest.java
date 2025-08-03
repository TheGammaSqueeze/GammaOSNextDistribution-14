/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.car.Car;
import android.car.CarProjectionManager;
import android.car.ICarProjection;
import android.car.ICarProjectionKeyEventHandler;
import android.car.ICarProjectionStatusListener;
import android.car.projection.ProjectionStatus;
import android.content.Intent;
import android.net.wifi.SoftApConfiguration;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;

import androidx.annotation.NonNull;

import com.google.common.primitives.Ints;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public final class CarProjectionManagerUnitTest {
    private static final int MAX_WAIT_TIME_MS = 3000;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Mock private Car mCar;
    @Mock private IBinder mBinder;
    @Mock private ICarProjection mService;

    private CarProjectionManager mCarProjectionManager;

    @Before
    public void setUp() {
        when(mCar.getEventHandler()).thenReturn(mMainHandler);
        when(mBinder.queryLocalInterface(anyString())).thenReturn(mService);
        mCarProjectionManager = new CarProjectionManager(mCar, mBinder);
    }

    @Test
    public void testRegisterProjectionListener() throws Exception {
        CarProjectionManager.CarProjectionListener listener = mock(
                CarProjectionManager.CarProjectionListener.class);

        mCarProjectionManager.registerProjectionListener(listener,
                CarProjectionManager.PROJECTION_VOICE_SEARCH);

        ICarProjectionKeyEventHandler keyEventHandlerImpl = captureKeyEventHandlerImplOnRegister();
        keyEventHandlerImpl.onKeyEvent(
                CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP);

        verify(listener, timeout(MAX_WAIT_TIME_MS)).onVoiceAssistantRequest(eq(false));

        mCarProjectionManager.unregisterProjectionListener();
        verify(mService).unregisterKeyEventHandler(keyEventHandlerImpl);

        /* Shouldn't call the listener as it is removed. */
        keyEventHandlerImpl.onKeyEvent(
                CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP);

        verifyNoMoreInteractions(listener, mService);
    }

    @Test
    public void testFailsRegisterProjectionListenerWithNullListener() throws Exception {
        assertThrows(NullPointerException.class,
                () -> mCarProjectionManager.registerProjectionListener(null,
                        CarProjectionManager.PROJECTION_VOICE_SEARCH));
    }

    @Test
    public void testRegisterProjectionRunner() throws Exception {
        Intent serviceIntent = mock(Intent.class);
        mCarProjectionManager.registerProjectionRunner(serviceIntent);
        verify(mService).registerProjectionRunner(eq(serviceIntent));
    }

    @Test
    public void testFailsRegisterProjectionRunnerWithNullIntent() throws Exception {
        assertThrows(NullPointerException.class,
                () -> mCarProjectionManager.registerProjectionRunner(null));
    }

    @Test
    public void testUnregisterProjectionRunner() throws Exception {
        Intent serviceIntent = mock(Intent.class);
        mCarProjectionManager.unregisterProjectionRunner(serviceIntent);
        verify(mService).unregisterProjectionRunner(eq(serviceIntent));
    }

    @Test
    public void testFailsUnregisterProjectionRunnerWithNullIntent() throws Exception {
        assertThrows(NullPointerException.class,
                () -> mCarProjectionManager.unregisterProjectionRunner(null));
    }

    @Test
    public void testStartProjectionAccessPoint() throws Exception {
        CountDownLatch onStartedLatch = new CountDownLatch(1);
        CarProjectionManager.ProjectionAccessPointCallback callback =
                new CarProjectionManager.ProjectionAccessPointCallback() {
                    @Override
                    public void onStarted(@NonNull SoftApConfiguration softApConfiguration) {
                        onStartedLatch.countDown();
                    }
                };

        mCarProjectionManager.startProjectionAccessPoint(callback);

        Messenger messenger = captureProjectionAccessPointMessenger();
        Message msg = new Message();
        msg.obj = new SoftApConfiguration.Builder().build();
        msg.what = CarProjectionManager.PROJECTION_AP_STARTED;
        messenger.send(msg);

        assertThat(onStartedLatch.await(MAX_WAIT_TIME_MS, TimeUnit.MILLISECONDS)).isTrue();

        mCarProjectionManager.stopProjectionAccessPoint();
        verify(mService).stopProjectionAccessPoint(any());
    }

    @Test
    public void testStartProjectionAccessPointWithOnStoppedCallback() throws Exception {
        CountDownLatch onStoppedLatch = new CountDownLatch(1);
        CarProjectionManager.ProjectionAccessPointCallback callback =
                new CarProjectionManager.ProjectionAccessPointCallback() {
                    @Override
                    public void onStopped() {
                        onStoppedLatch.countDown();
                    }
                };

        mCarProjectionManager.startProjectionAccessPoint(callback);

        Messenger messenger = captureProjectionAccessPointMessenger();
        Message msg = new Message();
        msg.obj = new SoftApConfiguration.Builder().build();
        msg.what = CarProjectionManager.PROJECTION_AP_STOPPED;
        messenger.send(msg);

        assertThat(onStoppedLatch.await(MAX_WAIT_TIME_MS, TimeUnit.MILLISECONDS)).isTrue();

        mCarProjectionManager.stopProjectionAccessPoint();
        verify(mService).stopProjectionAccessPoint(any());
    }

    @Test
    public void testFailsStartProjectionAccessPointWithNullCallback() throws Exception {
        assertThrows(NullPointerException.class,
                () -> mCarProjectionManager.startProjectionAccessPoint(null));
    }

    @Test
    public void testGetAvailableWifiChannels() throws Exception {
        int band = 1;
        List<Integer> expectedChannels = Arrays.asList(1, 2, 3, 4, 5, 6);
        when(mService.getAvailableWifiChannels(band)).thenReturn(Ints.toArray(expectedChannels));

        List<Integer> channels = mCarProjectionManager.getAvailableWifiChannels(band);

        assertThat(channels).containsExactlyElementsIn(expectedChannels);
    }

    @Test
    public void testRequestBluetoothProfileInhibit() throws Exception {
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(mService.requestBluetoothProfileInhibit(eq(device), anyInt(), any())).thenReturn(true);

        assertThat(mCarProjectionManager.requestBluetoothProfileInhibit(device,
                /* profile= */ 1)).isTrue();
    }

    @Test
    public void testFailsRequestBluetoothProfileInhibitWithNullDevice() throws Exception {
        assertThrows(NullPointerException.class,
                () -> mCarProjectionManager.requestBluetoothProfileInhibit(/* device= */
                        null,  /* profile= */1));
    }

    @Test
    public void testReleaseBluetoothProfileInhibit() throws Exception {
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(mService.releaseBluetoothProfileInhibit(eq(device), anyInt(), any())).thenReturn(true);

        assertThat(mCarProjectionManager.releaseBluetoothProfileInhibit(device,
                /* profile= */ 1)).isTrue();
    }

    @Test
    public void testIsBluetoothProfileInhibited() throws Exception {
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(mService.isBluetoothProfileInhibited(eq(device), anyInt(), any())).thenReturn(true);

        assertThat(mCarProjectionManager.isBluetoothProfileInhibited(device,
                /* profile= */ 1)).isTrue();
    }

    @Test
    public void testFailsIsBluetoothProfileInhibitedWithNullDevice() throws Exception {
        assertThrows(NullPointerException.class,
                () -> mCarProjectionManager.isBluetoothProfileInhibited(
                        /* device= */ null, /* profile= */1));
    }

    @Test
    public void testFailsReleaseBluetoothProfileInhibitWithNullDevice() throws Exception {
        assertThrows(NullPointerException.class,
                () -> mCarProjectionManager.releaseBluetoothProfileInhibit(
                        /* device= */ null, /* profile= */1));
    }

    @Test
    public void testUpdateProjectionStatus() throws Exception {
        ProjectionStatus status = mock(ProjectionStatus.class);
        mCarProjectionManager.updateProjectionStatus(status);
        verify(mService).updateProjectionStatus(eq(status), any());
    }

    @Test
    public void testFailsUpdateProjectionStatusWithNullStatus() throws Exception {
        assertThrows(NullPointerException.class,
                () -> mCarProjectionManager.updateProjectionStatus(null));
    }

    @Test
    public void testRegisterProjectionStatusListener() throws Exception {
        CarProjectionManager.ProjectionStatusListener listener = mock(
                CarProjectionManager.ProjectionStatusListener.class);

        mCarProjectionManager.registerProjectionStatusListener(listener);

        ICarProjectionStatusListener statusListenerImpl = captureStatusListenerImplOnRegister();
        List<ProjectionStatus> details = new ArrayList<>();
        statusListenerImpl.onProjectionStatusChanged(1111, "test.package", details);

        verify(listener, timeout(MAX_WAIT_TIME_MS).times(1)).onProjectionStatusChanged(eq(1111),
                eq("test.package"), eq(details));

        mCarProjectionManager.unregisterProjectionStatusListener(listener);
        verify(mService).unregisterProjectionStatusListener(statusListenerImpl);

        /* Shouldn't call the listener as it is removed. */
        statusListenerImpl.onProjectionStatusChanged(1111, "test.package", details);

        verifyNoMoreInteractions(listener, mService);
    }

    @Test
    public void testFailsRegisterProjectionStatusListenerWithNullListener() throws Exception {
        assertThrows(NullPointerException.class,
                () -> mCarProjectionManager.registerProjectionStatusListener(null));
    }

    @Test
    public void testFailsUnregisterProjectionStatusListenerWithNullListener() throws Exception {
        assertThrows(NullPointerException.class,
                () -> mCarProjectionManager.unregisterProjectionStatusListener(null));
    }

    @Test
    public void testGetProjectionOptions() throws Exception {
        Bundle expectedBundle = mock(Bundle.class);
        when(mService.getProjectionOptions()).thenReturn(expectedBundle);

        assertThat(mCarProjectionManager.getProjectionOptions()).isEqualTo(expectedBundle);
    }

    private ICarProjectionKeyEventHandler captureKeyEventHandlerImplOnRegister() throws Exception {
        ArgumentCaptor<ICarProjectionKeyEventHandler> handlerArgumentCaptor =
                ArgumentCaptor.forClass(ICarProjectionKeyEventHandler.class);

        verify(mService).registerKeyEventHandler(handlerArgumentCaptor.capture(), any());
        return handlerArgumentCaptor.getValue();
    }

    private Messenger captureProjectionAccessPointMessenger() throws Exception {
        ArgumentCaptor<Messenger> messengerArgumentCaptor =
                ArgumentCaptor.forClass(Messenger.class);

        verify(mService).startProjectionAccessPoint(messengerArgumentCaptor.capture(), any());
        return messengerArgumentCaptor.getValue();
    }

    private ICarProjectionStatusListener captureStatusListenerImplOnRegister() throws Exception {
        ArgumentCaptor<ICarProjectionStatusListener> listenerArgumentCaptor =
                ArgumentCaptor.forClass(ICarProjectionStatusListener.class);

        verify(mService).registerProjectionStatusListener(listenerArgumentCaptor.capture());
        return listenerArgumentCaptor.getValue();
    }

}
