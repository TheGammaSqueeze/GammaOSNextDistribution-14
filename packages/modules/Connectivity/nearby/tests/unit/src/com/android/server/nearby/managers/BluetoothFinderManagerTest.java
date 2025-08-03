/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.nearby.managers;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.hardware.bluetooth.finder.Eid;
import android.hardware.bluetooth.finder.IBluetoothFinder;
import android.nearby.PoweredOffFindingEphemeralId;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.os.ServiceSpecificException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

public class BluetoothFinderManagerTest {
    private BluetoothFinderManager mBluetoothFinderManager;
    private boolean mGetServiceCalled = false;

    @Mock private IBluetoothFinder mIBluetoothFinderMock;
    @Mock private IBinder mServiceBinderMock;

    private ArgumentCaptor<DeathRecipient> mDeathRecipientCaptor =
            ArgumentCaptor.forClass(DeathRecipient.class);

    private ArgumentCaptor<Eid[]> mEidArrayCaptor = ArgumentCaptor.forClass(Eid[].class);

    private class BluetoothFinderManagerSpy extends BluetoothFinderManager {
        @Override
        protected IBluetoothFinder getServiceMockable() {
            mGetServiceCalled = true;
            return mIBluetoothFinderMock;
        }

        @Override
        protected IBinder getServiceBinderMockable() {
            return mServiceBinderMock;
        }
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mBluetoothFinderManager = new BluetoothFinderManagerSpy();
    }

    @Test
    public void testSendEids() throws Exception {
        byte[] eidBytes1 = {
                (byte) 0xe1, (byte) 0xde, (byte) 0x1d, (byte) 0xe1, (byte) 0xde, (byte) 0x1d,
                (byte) 0xe1, (byte) 0xde, (byte) 0x1d, (byte) 0xe1, (byte) 0xde, (byte) 0x1d,
                (byte) 0xe1, (byte) 0xde, (byte) 0x1d, (byte) 0xe1, (byte) 0xde, (byte) 0x1d,
                (byte) 0xe1, (byte) 0xde
        };
        byte[] eidBytes2 = {
                (byte) 0xf2, (byte) 0xef, (byte) 0x2e, (byte) 0xf2, (byte) 0xef, (byte) 0x2e,
                (byte) 0xf2, (byte) 0xef, (byte) 0x2e, (byte) 0xf2, (byte) 0xef, (byte) 0x2e,
                (byte) 0xf2, (byte) 0xef, (byte) 0x2e, (byte) 0xf2, (byte) 0xef, (byte) 0x2e,
                (byte) 0xf2, (byte) 0xef
        };
        PoweredOffFindingEphemeralId ephemeralId1 = new PoweredOffFindingEphemeralId();
        PoweredOffFindingEphemeralId ephemeralId2 = new PoweredOffFindingEphemeralId();
        ephemeralId1.bytes = eidBytes1;
        ephemeralId2.bytes = eidBytes2;

        mBluetoothFinderManager.sendEids(List.of(ephemeralId1, ephemeralId2));

        verify(mIBluetoothFinderMock).sendEids(mEidArrayCaptor.capture());
        assertThat(mEidArrayCaptor.getValue()[0].bytes).isEqualTo(eidBytes1);
        assertThat(mEidArrayCaptor.getValue()[1].bytes).isEqualTo(eidBytes2);
    }

    @Test
    public void testSendEids_remoteException() throws Exception {
        doThrow(new RemoteException())
                .when(mIBluetoothFinderMock).sendEids(any());
        mBluetoothFinderManager.sendEids(List.of());

        // Verify that we get the service again following a RemoteException.
        mGetServiceCalled = false;
        mBluetoothFinderManager.sendEids(List.of());
        assertThat(mGetServiceCalled).isTrue();
    }

    @Test
    public void testSendEids_serviceSpecificException() throws Exception {
        doThrow(new ServiceSpecificException(1))
                .when(mIBluetoothFinderMock).sendEids(any());
        mBluetoothFinderManager.sendEids(List.of());
    }

    @Test
    public void testSetPoweredOffFinderMode() throws Exception {
        mBluetoothFinderManager.setPoweredOffFinderMode(true);
        verify(mIBluetoothFinderMock).setPoweredOffFinderMode(true);

        mBluetoothFinderManager.setPoweredOffFinderMode(false);
        verify(mIBluetoothFinderMock).setPoweredOffFinderMode(false);
    }

    @Test
    public void testSetPoweredOffFinderMode_remoteException() throws Exception {
        doThrow(new RemoteException())
                .when(mIBluetoothFinderMock).setPoweredOffFinderMode(anyBoolean());
        mBluetoothFinderManager.setPoweredOffFinderMode(true);

        // Verify that we get the service again following a RemoteException.
        mGetServiceCalled = false;
        mBluetoothFinderManager.setPoweredOffFinderMode(true);
        assertThat(mGetServiceCalled).isTrue();
    }

    @Test
    public void testSetPoweredOffFinderMode_serviceSpecificException() throws Exception {
        doThrow(new ServiceSpecificException(1))
                .when(mIBluetoothFinderMock).setPoweredOffFinderMode(anyBoolean());
        mBluetoothFinderManager.setPoweredOffFinderMode(true);
    }

    @Test
    public void testGetPoweredOffFinderMode() throws Exception {
        when(mIBluetoothFinderMock.getPoweredOffFinderMode()).thenReturn(true);
        assertThat(mBluetoothFinderManager.getPoweredOffFinderMode()).isTrue();

        when(mIBluetoothFinderMock.getPoweredOffFinderMode()).thenReturn(false);
        assertThat(mBluetoothFinderManager.getPoweredOffFinderMode()).isFalse();
    }

    @Test
    public void testGetPoweredOffFinderMode_remoteException() throws Exception {
        when(mIBluetoothFinderMock.getPoweredOffFinderMode()).thenThrow(new RemoteException());
        assertThat(mBluetoothFinderManager.getPoweredOffFinderMode()).isFalse();

        // Verify that we get the service again following a RemoteException.
        mGetServiceCalled = false;
        assertThat(mBluetoothFinderManager.getPoweredOffFinderMode()).isFalse();
        assertThat(mGetServiceCalled).isTrue();
    }

    @Test
    public void testGetPoweredOffFinderMode_serviceSpecificException() throws Exception {
        when(mIBluetoothFinderMock.getPoweredOffFinderMode())
                .thenThrow(new ServiceSpecificException(1));
        assertThat(mBluetoothFinderManager.getPoweredOffFinderMode()).isFalse();
    }

    @Test
    public void testDeathRecipient() throws Exception {
        mBluetoothFinderManager.setPoweredOffFinderMode(true);
        verify(mServiceBinderMock).linkToDeath(mDeathRecipientCaptor.capture(), anyInt());
        mDeathRecipientCaptor.getValue().binderDied();

        // Verify that we get the service again following a binder death.
        mGetServiceCalled = false;
        mBluetoothFinderManager.setPoweredOffFinderMode(true);
        assertThat(mGetServiceCalled).isTrue();
    }
}
