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

package com.android.ims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.IBinder;
import android.telephony.ims.ImsService;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.aidl.ISipTransport;
import android.telephony.ims.feature.ImsFeature;

import androidx.test.filters.SmallTest;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.ims.internal.IImsServiceFeatureCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class ImsFeatureBinderRepositoryTest extends ImsTestBase {

    private static final int TEST_PHONE_ID_1 = 1;
    private static final int TEST_PHONE_ID_2 = 2;
    private static final int TEST_SUB_ID_3 = 3;
    private static final long TEST_SERVICE_CAPS = ImsService.CAPABILITY_EMERGENCY_OVER_MMTEL;

    @Mock IBinder mMockMmTelFeatureA;
    @Mock IBinder mMockMmTelFeatureB;
    @Mock IBinder mMockRcsFeatureA;
    @Mock IImsConfig mMockImsConfig;
    @Mock IImsRegistration mMockImsRegistration;
    @Mock ISipTransport mMockSipTransport;

    @Mock IImsServiceFeatureCallback mConnectionCallback;
    @Mock IBinder mConnectionCallbackBinder;
    @Mock IImsServiceFeatureCallback mConnectionCallback2;
    @Mock IBinder mConnectionCallback2Binder;

    private ImsFeatureBinderRepository mRepository;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mRepository = new ImsFeatureBinderRepository();
        when(mConnectionCallbackBinder.isBinderAlive()).thenReturn(true);
        when(mConnectionCallback2Binder.isBinderAlive()).thenReturn(true);
        when(mConnectionCallback.asBinder()).thenReturn(mConnectionCallbackBinder);
        when(mConnectionCallback2.asBinder()).thenReturn(mConnectionCallback2Binder);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testGetInterfaceExists() throws Exception {
        ImsFeatureContainer fc =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.addConnection(TEST_PHONE_ID_1, TEST_SUB_ID_3, ImsFeature.FEATURE_MMTEL, fc);
        ImsFeatureContainer resultFc =
                mRepository.getIfExists(TEST_PHONE_ID_1, ImsFeature.FEATURE_MMTEL).orElse(null);
        assertNotNull("returned connection should not be null!", resultFc);
        assertEquals("returned connection does not match the set connection",
                fc, resultFc);
    }

    @Test
    @SmallTest
    public void testGetInterfaceDoesntExist() throws Exception {
        ImsFeatureContainer fc =
                mRepository.getIfExists(TEST_PHONE_ID_1,
                ImsFeature.FEATURE_MMTEL).orElse(null);
        assertNull("returned connection should be null!", fc);
    }

    @Test
    @SmallTest
    public void testGetInterfaceRemoveDoesntExist() throws Exception {
        ImsFeatureContainer fc =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.addConnection(TEST_PHONE_ID_1, TEST_SUB_ID_3, ImsFeature.FEATURE_MMTEL, fc);
        mRepository.addConnection(TEST_PHONE_ID_1, TEST_SUB_ID_3, ImsFeature.FEATURE_MMTEL, null);
        ImsFeatureContainer resultFc =
                mRepository.getIfExists(TEST_PHONE_ID_1,
                ImsFeature.FEATURE_MMTEL).orElse(null);
        assertNull("returned connection should be null!", resultFc);
    }

    @Test
    @SmallTest
    public void testGetInterfaceUpdateExists() throws Exception {
        ImsFeatureContainer fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        ImsFeatureContainer fcB =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.addConnection(TEST_PHONE_ID_1, TEST_SUB_ID_3, ImsFeature.FEATURE_MMTEL, fcA);
        mRepository.addConnection(TEST_PHONE_ID_1, TEST_SUB_ID_3, ImsFeature.FEATURE_MMTEL, fcB);
        ImsFeatureContainer resultFc =
                mRepository.getIfExists(TEST_PHONE_ID_1,
                        ImsFeature.FEATURE_MMTEL).orElse(null);
        assertNotNull("returned connection should not be null!", resultFc);
        assertEquals("returned connection does not match the set connection",
                fcB, resultFc);
    }

    @Test
    @SmallTest
    public void testGetMultipleInterfacesExists() throws Exception {
        ImsFeatureContainer fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        ImsFeatureContainer fcB =
                getFeatureContainer(mMockRcsFeatureA, TEST_SERVICE_CAPS);
        mRepository.addConnection(TEST_PHONE_ID_1, TEST_SUB_ID_3, ImsFeature.FEATURE_MMTEL, fcA);
        mRepository.addConnection(TEST_PHONE_ID_1, TEST_SUB_ID_3, ImsFeature.FEATURE_RCS, fcB);
        ImsFeatureContainer resultFcA =
                mRepository.getIfExists(TEST_PHONE_ID_1,
                        ImsFeature.FEATURE_MMTEL).orElse(null);
        ImsFeatureContainer resultFcB =
                mRepository.getIfExists(TEST_PHONE_ID_1,
                        ImsFeature.FEATURE_RCS).orElse(null);
        assertNotNull("returned connection should not be null!", resultFcA);
        assertNotNull("returned connection should not be null!", resultFcB);
        assertEquals("returned connection does not match the set connection",
                fcA, resultFcA);
        assertEquals("returned connection does not match the set connection",
                fcB, resultFcB);
    }

    @Test
    @SmallTest
    public void testListenForUpdate() throws Exception {
        ImsFeatureContainer fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, ImsFeature.FEATURE_MMTEL,
                mConnectionCallback, Runnable::run);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
        mRepository.addConnection(TEST_PHONE_ID_1, TEST_SUB_ID_3, ImsFeature.FEATURE_MMTEL, fcA);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
    }

    @Test
    @SmallTest
    public void testListenNoUpdateForStaleListener() throws Exception {
        ImsFeatureContainer fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, ImsFeature.FEATURE_MMTEL,
                mConnectionCallback, Runnable::run);
        mRepository.addConnection(TEST_PHONE_ID_1, TEST_SUB_ID_3, ImsFeature.FEATURE_MMTEL, fcA);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());

        when(mConnectionCallbackBinder.isBinderAlive()).thenReturn(false);
        // Listener is "dead", so we should not get this update
        mRepository.notifyFeatureStateChanged(TEST_PHONE_ID_1, ImsFeature.FEATURE_MMTEL,
                ImsFeature.STATE_READY);
        verify(mConnectionCallback, never()).imsStatusChanged(ImsFeature.STATE_READY,
                TEST_SUB_ID_3);
    }

    @Test
    @SmallTest
    public void testListenForUpdateStateChanged() throws Exception {
        ImsFeatureContainer fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, ImsFeature.FEATURE_MMTEL,
                mConnectionCallback, Runnable::run);
        mRepository.addConnection(TEST_PHONE_ID_1, TEST_SUB_ID_3, ImsFeature.FEATURE_MMTEL, fcA);
        ImsFeatureContainer resultFc =
                mRepository.getIfExists(TEST_PHONE_ID_1, ImsFeature.FEATURE_MMTEL).orElse(null);
        assertNotNull(resultFc);
        assertEquals(ImsFeature.STATE_UNAVAILABLE, resultFc.getState());
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
        verify(mConnectionCallback, never()).imsStatusChanged(anyInt(), eq(TEST_SUB_ID_3));

        mRepository.notifyFeatureStateChanged(TEST_PHONE_ID_1, ImsFeature.FEATURE_MMTEL,
                ImsFeature.STATE_READY);
        verify(mConnectionCallback).imsStatusChanged(ImsFeature.STATE_READY, TEST_SUB_ID_3);
        assertEquals(ImsFeature.STATE_READY, resultFc.getState());
    }

    @Test
    @SmallTest
    public void testListenForUpdateCapsChanged() throws Exception {
        ImsFeatureContainer fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, ImsFeature.FEATURE_MMTEL,
                mConnectionCallback, Runnable::run);

        mRepository.addConnection(TEST_PHONE_ID_1, TEST_SUB_ID_3, ImsFeature.FEATURE_MMTEL, fcA);
        ImsFeatureContainer resultFc =
                mRepository.getIfExists(TEST_PHONE_ID_1, ImsFeature.FEATURE_MMTEL).
                                orElse(null);
        assertNotNull(resultFc);
        assertEquals(TEST_SERVICE_CAPS, resultFc.getCapabilities());
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());

        mRepository.notifyFeatureCapabilitiesChanged(TEST_PHONE_ID_1, ImsFeature.FEATURE_MMTEL, 0);
        verify(mConnectionCallback).updateCapabilities(0);
        assertEquals(0, resultFc.getCapabilities());
    }


    @Test
    @SmallTest
    public void testRemoveCallback() throws Exception {
        ImsFeatureContainer fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        ImsFeatureContainer fcB =
                getFeatureContainer(mMockMmTelFeatureB, TEST_SERVICE_CAPS);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, ImsFeature.FEATURE_MMTEL,
                mConnectionCallback, Runnable::run);
        mRepository.addConnection(TEST_PHONE_ID_1, TEST_SUB_ID_3, ImsFeature.FEATURE_MMTEL, fcA);
        mRepository.unregisterForConnectionUpdates(mConnectionCallback);

        mRepository.addConnection(TEST_PHONE_ID_1, TEST_SUB_ID_3, ImsFeature.FEATURE_MMTEL, fcB);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verifyFeatureCreatedCalled(0 /*times*/, mConnectionCallback, fcB);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
    }

    @Test
    @SmallTest
    public void testAddSameCallback() throws Exception {
        ImsFeatureContainer fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, ImsFeature.FEATURE_MMTEL,
                mConnectionCallback, Runnable::run);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, ImsFeature.FEATURE_MMTEL,
                mConnectionCallback, Runnable::run);
        mRepository.addConnection(TEST_PHONE_ID_1, TEST_SUB_ID_3, ImsFeature.FEATURE_MMTEL, fcA);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
    }

    @Test
    @SmallTest
    public void testListenAfterUpdate() throws Exception {
        ImsFeatureContainer fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.addConnection(TEST_PHONE_ID_1, TEST_SUB_ID_3, ImsFeature.FEATURE_MMTEL, fcA);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, ImsFeature.FEATURE_MMTEL,
                mConnectionCallback, Runnable::run);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
    }

    @Test
    @SmallTest
    public void testListenNoUpdate() throws Exception {
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, ImsFeature.FEATURE_MMTEL,
                mConnectionCallback, Runnable::run);
        verify(mConnectionCallback, never()).imsFeatureCreated(any(), anyInt());
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
    }

    @Test
    @SmallTest
    public void testListenNull() throws Exception {
        ImsFeatureContainer fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.addConnection(TEST_PHONE_ID_1, TEST_SUB_ID_3, ImsFeature.FEATURE_MMTEL, fcA);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, ImsFeature.FEATURE_MMTEL,
                mConnectionCallback, Runnable::run);
        mRepository.removeConnection(TEST_PHONE_ID_1, ImsFeature.FEATURE_MMTEL);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback).imsFeatureRemoved(
                FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);
    }

    @Test
    @SmallTest
    public void testMultipleListeners() throws Exception {
        ImsFeatureContainer fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        ImsFeatureContainer fcB =
                getFeatureContainer(mMockRcsFeatureA, TEST_SERVICE_CAPS);
        mRepository.addConnection(TEST_PHONE_ID_1, TEST_SUB_ID_3, ImsFeature.FEATURE_MMTEL, fcA);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, ImsFeature.FEATURE_MMTEL,
                mConnectionCallback, Runnable::run);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());

        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, ImsFeature.FEATURE_RCS,
                mConnectionCallback2, Runnable::run);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
        verify(mConnectionCallback2, never()).imsFeatureCreated(any(), eq(TEST_SUB_ID_3));
        verify(mConnectionCallback2, never()).imsFeatureRemoved(anyInt());

        mRepository.addConnection(TEST_PHONE_ID_1, TEST_SUB_ID_3, ImsFeature.FEATURE_RCS, fcB);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback2, fcB);
        verify(mConnectionCallback2, never()).imsFeatureRemoved(anyInt());
    }

    @Test
    @SmallTest
    public void testMultiplePhones() throws Exception {
        ImsFeatureContainer fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        ImsFeatureContainer fcB =
                getFeatureContainer(mMockRcsFeatureA, TEST_SERVICE_CAPS);
        mRepository.addConnection(TEST_PHONE_ID_1, TEST_SUB_ID_3, ImsFeature.FEATURE_MMTEL, fcA);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, ImsFeature.FEATURE_MMTEL,
                mConnectionCallback, Runnable::run);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());

        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_2, ImsFeature.FEATURE_RCS,
                mConnectionCallback2, Runnable::run);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
        verify(mConnectionCallback2, never()).imsFeatureCreated(any(), anyInt());
        verify(mConnectionCallback2, never()).imsFeatureRemoved(anyInt());

        mRepository.addConnection(TEST_PHONE_ID_2, TEST_SUB_ID_3, ImsFeature.FEATURE_RCS, fcB);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback2, fcB);
        verify(mConnectionCallback2, never()).imsFeatureRemoved(anyInt());
    }

    private void verifyFeatureCreatedCalled(int timesCalled, IImsServiceFeatureCallback cb,
            ImsFeatureContainer fc) throws Exception {
        verify(cb, times(timesCalled)).imsFeatureCreated(fc, TEST_SUB_ID_3);
    }

    private ImsFeatureContainer getFeatureContainer(IBinder feature, long caps) {
        return new ImsFeatureContainer(feature, mMockImsConfig,
                mMockImsRegistration, mMockSipTransport, caps);
    }
}
