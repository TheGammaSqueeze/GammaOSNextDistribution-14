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

package com.android.services.telephony.rcs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import android.net.InetAddresses;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.ims.DelegateRegistrationState;
import android.telephony.ims.DelegateRequest;
import android.telephony.ims.FeatureTagState;
import android.telephony.ims.SipDelegateConfiguration;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.aidl.ISipDelegate;
import android.telephony.ims.aidl.ISipDelegateMessageCallback;
import android.telephony.ims.aidl.ISipDelegateStateCallback;
import android.telephony.ims.aidl.ISipTransport;
import android.util.ArraySet;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.TelephonyTestBase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class SipDelegateBinderConnectionTest extends TelephonyTestBase {
    private static final int TEST_SUB_ID = 1;

    @Mock private ISipDelegate mMockDelegate;
    @Mock private ISipTransport mMockTransport;
    @Mock private IImsRegistration mMockRegistration;
    @Mock private IBinder mTransportBinder;
    @Mock private ISipDelegateMessageCallback mMessageCallback;
    @Mock private DelegateBinderStateManager.StateCallback mMockStateCallback;
    @Mock private BiConsumer<ISipDelegate, Set<FeatureTagState>> mMockCreatedCallback;
    @Mock private Consumer<Integer> mMockDestroyedCallback;

    private ArrayList<SipDelegateBinderConnection.StateCallback> mStateCallbackList;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        doReturn(mTransportBinder).when(mMockTransport).asBinder();
        mStateCallbackList = new ArrayList<>(1);
        mStateCallbackList.add(mMockStateCallback);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @SmallTest
    @Test
    public void testBaseImpl() throws Exception {
        DelegateBinderStateManager baseConnection = new SipDelegateBinderConnectionStub(
                getMmTelDeniedTag(), Runnable::run, mStateCallbackList);

        baseConnection.create(null /*message cb*/, mMockCreatedCallback);
        // Verify the stub simulates onCreated + on registration state callback.
        verify(mMockCreatedCallback).accept(any(), eq(getMmTelDeniedTag()));
        verify(mMockStateCallback).onRegistrationStateChanged(
                new DelegateRegistrationState.Builder().build());

        // Verify onDestroyed is called correctly.
        baseConnection.destroy(SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP,
                mMockDestroyedCallback);
        verify(mMockDestroyedCallback).accept(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
    }

    @SmallTest
    @Test
    public void testCreateConnection() throws Exception {
        DelegateRequest request = getDelegateRequest();
        ArraySet<FeatureTagState> deniedTags = getMmTelDeniedTag();
        SipDelegateBinderConnection connection = new SipDelegateBinderConnection(TEST_SUB_ID,
                mMockTransport, mMockRegistration, request, deniedTags, Runnable::run,
                mStateCallbackList);
        ISipDelegateStateCallback cb = createDelegateCaptureStateCallback(request, connection);

        // Send onCreated callback from SipDelegate
        ArrayList<FeatureTagState> delegateDeniedTags = new ArrayList<>(1);
        delegateDeniedTags.add(new FeatureTagState(ImsSignallingUtils.GROUP_CHAT_TAG,
                SipDelegateManager.DENIED_REASON_IN_USE_BY_ANOTHER_DELEGATE));
        assertNotNull(cb);
        cb.onCreated(mMockDelegate, delegateDeniedTags);

        ArraySet<FeatureTagState> totalDeniedTags = new ArraySet<>(deniedTags);
        // Add the tags denied by the controller as well.
        totalDeniedTags.addAll(delegateDeniedTags);
        // The callback should contain the controller and delegate denied tags in the callback.
        verify(mMockCreatedCallback).accept(mMockDelegate, totalDeniedTags);
    }

    @SmallTest
    @Test
    public void testCreateConnectionServiceDead() throws Exception {
        DelegateRequest request = getDelegateRequest();
        ArraySet<FeatureTagState> deniedTags = getMmTelDeniedTag();
        SipDelegateBinderConnection connection = new SipDelegateBinderConnection(TEST_SUB_ID,
                mMockTransport, mMockRegistration, request, deniedTags, Runnable::run,
                mStateCallbackList);
        doThrow(new RemoteException()).when(mMockTransport).createSipDelegate(eq(TEST_SUB_ID),
                any(), any(), any());
        ISipDelegateStateCallback cb = createDelegateCaptureStateCallback(request, connection);
        assertNull(cb);
    }

    @SmallTest
    @Test
    public void testDestroyConnection() throws Exception {
        DelegateRequest request = getDelegateRequest();
        ArraySet<FeatureTagState> deniedTags = getMmTelDeniedTag();
        SipDelegateBinderConnection connection = new SipDelegateBinderConnection(TEST_SUB_ID,
                mMockTransport, mMockRegistration, request, deniedTags, Runnable::run,
                mStateCallbackList);
        ISipDelegateStateCallback cb = createDelegateCaptureStateCallback(request, connection);
        assertNotNull(cb);
        cb.onCreated(mMockDelegate, null /*denied*/);
        verify(mMockCreatedCallback).accept(mMockDelegate, deniedTags);

        // call Destroy on the SipDelegate
        destroy(connection, SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        cb.onDestroyed(SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        verify(mMockDestroyedCallback).accept(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
    }

    @SmallTest
    @Test
    public void testDestroyConnectionDead() throws Exception {
        DelegateRequest request = getDelegateRequest();
        ArraySet<FeatureTagState> deniedTags = getMmTelDeniedTag();
        SipDelegateBinderConnection connection = new SipDelegateBinderConnection(TEST_SUB_ID,
                mMockTransport, mMockRegistration, request, deniedTags, Runnable::run,
                mStateCallbackList);
        ISipDelegateStateCallback cb = createDelegateCaptureStateCallback(request, connection);
        assertNotNull(cb);
        cb.onCreated(mMockDelegate, null /*denied*/);
        verify(mMockCreatedCallback).accept(mMockDelegate, deniedTags);

        // try to destroy when dead and ensure callback is still called.
        doThrow(new RemoteException()).when(mMockTransport).destroySipDelegate(any(), anyInt());
        destroy(connection, SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        verify(mMockDestroyedCallback).accept(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
    }

    @SmallTest
    @Test
    public void testStateCallback() throws Exception {
        DelegateRequest request = getDelegateRequest();
        ArraySet<FeatureTagState> deniedTags = getMmTelDeniedTag();
        SipDelegateBinderConnection connection = new SipDelegateBinderConnection(TEST_SUB_ID,
                mMockTransport, mMockRegistration, request, deniedTags, Runnable::run,
                mStateCallbackList);
        ISipDelegateStateCallback cb = createDelegateCaptureStateCallback(request, connection);
        assertNotNull(cb);
        cb.onCreated(mMockDelegate, new ArrayList<>(deniedTags));
        verify(mMockCreatedCallback).accept(mMockDelegate, deniedTags);

        InetSocketAddress localAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("1.1.1.1"), 80);
        InetSocketAddress serverAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("2.2.2.2"), 81);
        SipDelegateConfiguration c = new SipDelegateConfiguration.Builder(1,
                SipDelegateConfiguration.SIP_TRANSPORT_TCP, localAddr, serverAddr).build();
        cb.onConfigurationChanged(c);
        verify(mMockStateCallback).onConfigurationChanged(c);

        DelegateRegistrationState regState = new DelegateRegistrationState.Builder()
                .addRegisteredFeatureTags(request.getFeatureTags()).build();
        cb.onFeatureTagRegistrationChanged(regState);
        verify(mMockStateCallback).onRegistrationStateChanged(regState);
    }

    private ISipDelegateStateCallback createDelegateCaptureStateCallback(
            DelegateRequest r, SipDelegateBinderConnection c) throws Exception {
        boolean isCreating = c.create(mMessageCallback, mMockCreatedCallback);
        if (!isCreating) return null;
        ArgumentCaptor<ISipDelegateStateCallback> stateCaptor =
                ArgumentCaptor.forClass(ISipDelegateStateCallback.class);
        verify(mMockTransport).createSipDelegate(eq(TEST_SUB_ID), eq(r), stateCaptor.capture(),
                eq(mMessageCallback));
        assertNotNull(stateCaptor.getValue());
        return stateCaptor.getValue();
    }

    private void destroy(SipDelegateBinderConnection c, int reason) throws Exception {
        c.destroy(reason, mMockDestroyedCallback);
        verify(mMockTransport).destroySipDelegate(mMockDelegate, reason);
    }

    private DelegateRequest getDelegateRequest() {
        ArraySet<String> featureTags = new ArraySet<>(2);
        featureTags.add(ImsSignallingUtils.ONE_TO_ONE_CHAT_TAG);
        featureTags.add(ImsSignallingUtils.GROUP_CHAT_TAG);
        return new DelegateRequest(featureTags);
    }

    private ArraySet<FeatureTagState> getMmTelDeniedTag() {
        ArraySet<FeatureTagState> deniedTags = new ArraySet<>();
        deniedTags.add(new FeatureTagState(ImsSignallingUtils.MMTEL_TAG,
                SipDelegateManager.DENIED_REASON_NOT_ALLOWED));
        return deniedTags;
    }
}
