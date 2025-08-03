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

package com.android.server.telecom.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.isA;


import android.content.ComponentName;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;

import com.android.internal.telecom.ICallControl;
import com.android.internal.telecom.ICallEventCallback;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.TransactionalServiceRepository;
import com.android.server.telecom.TransactionalServiceWrapper;
import com.android.server.telecom.voip.EndCallTransaction;
import com.android.server.telecom.voip.HoldCallTransaction;
import com.android.server.telecom.voip.SerialTransaction;
import com.android.server.telecom.voip.TransactionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class TransactionalServiceWrapperTest extends TelecomTestCase {

    private static final PhoneAccountHandle SERVICE_HANDLE = new PhoneAccountHandle(
            ComponentName.unflattenFromString("com.foo/.Blah"), "Service1");

    private static final String CALL_ID_1 = "1";
    private static final String CALL_ID_2 = "2";

    TransactionalServiceWrapper mTransactionalServiceWrapper;

    @Mock private Call mMockCall1;
    @Mock private Call mMockCall2;
    @Mock private CallsManager mCallsManager;
    @Mock private TransactionManager mTransactionManager;
    @Mock private ICallEventCallback mCallEventCallback;
    @Mock private TransactionalServiceRepository mRepository;
    @Mock private IBinder mIBinder;
    private final TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() {};

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        Mockito.when(mMockCall1.getId()).thenReturn(CALL_ID_1);
        Mockito.when(mMockCall2.getId()).thenReturn(CALL_ID_2);
        Mockito.when(mCallsManager.getLock()).thenReturn(mLock);
        Mockito.when(mCallEventCallback.asBinder()).thenReturn(mIBinder);
        mTransactionalServiceWrapper = new TransactionalServiceWrapper(mCallEventCallback,
                mCallsManager, SERVICE_HANDLE, mMockCall1, mRepository);

        mTransactionalServiceWrapper.setTransactionManager(mTransactionManager);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testTransactionalServiceWrapperStartState() throws Exception {
        TransactionalServiceWrapper service =
                new TransactionalServiceWrapper(mCallEventCallback,
                        mCallsManager, SERVICE_HANDLE, mMockCall1, mRepository);

        assertEquals(SERVICE_HANDLE, service.getPhoneAccountHandle());
        assertEquals(1, service.getNumberOfTrackedCalls());
    }

    @Test
    public void testTransactionalServiceWrapperCallCount() throws Exception {
        TransactionalServiceWrapper service =
                new TransactionalServiceWrapper(mCallEventCallback,
                        mCallsManager, SERVICE_HANDLE, mMockCall1, mRepository);

        assertEquals(1, service.getNumberOfTrackedCalls());
        service.trackCall(mMockCall2);
        assertEquals(2, service.getNumberOfTrackedCalls());

        assertTrue(service.untrackCall(mMockCall2));
        assertEquals(1, service.getNumberOfTrackedCalls());

        assertTrue(service.untrackCall(mMockCall1));
        assertFalse(service.untrackCall(mMockCall1));
        assertEquals(0, service.getNumberOfTrackedCalls());
    }

    @Test
    public void testCallControlSetActive() throws RemoteException {
        // GIVEN
        mTransactionalServiceWrapper.trackCall(mMockCall1);

        // WHEN
        ICallControl callControl = mTransactionalServiceWrapper.getICallControl();
        callControl.setActive(CALL_ID_1, new ResultReceiver(null));

        //THEN
        verify(mTransactionManager, times(1))
                .addTransaction(isA(SerialTransaction.class), isA(OutcomeReceiver.class));
    }

    @Test
    public void testCallControlRejectCall() throws RemoteException {
        // GIVEN
        mTransactionalServiceWrapper.trackCall(mMockCall1);

        // WHEN
        ICallControl callControl = mTransactionalServiceWrapper.getICallControl();
        callControl.disconnect(CALL_ID_1, new DisconnectCause(DisconnectCause.REJECTED),
                new ResultReceiver(null));

        //THEN
        verify(mTransactionManager, times(1))
                .addTransaction(isA(EndCallTransaction.class), isA(OutcomeReceiver.class));
    }

    @Test
    public void testCallControlDisconnectCall() throws RemoteException {
        // GIVEN
        mTransactionalServiceWrapper.trackCall(mMockCall1);

        // WHEN
        ICallControl callControl = mTransactionalServiceWrapper.getICallControl();
        callControl.disconnect(CALL_ID_1, new DisconnectCause(DisconnectCause.LOCAL),
                new ResultReceiver(null));

        //THEN
        verify(mTransactionManager, times(1))
                .addTransaction(isA(EndCallTransaction.class), isA(OutcomeReceiver.class));
    }

    @Test
    public void testCallControlSetInactive() throws RemoteException {
        // GIVEN
        mTransactionalServiceWrapper.trackCall(mMockCall1);

        // WHEN
        ICallControl callControl = mTransactionalServiceWrapper.getICallControl();
        callControl.setInactive(CALL_ID_1, new ResultReceiver(null));

        //THEN
        verify(mTransactionManager, times(1))
                .addTransaction(isA(HoldCallTransaction.class), isA(OutcomeReceiver.class));
    }
}
