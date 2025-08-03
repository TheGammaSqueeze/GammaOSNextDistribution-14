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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.telephony.ims.ImsService;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.aidl.ISipTransport;
import android.telephony.ims.feature.ImsFeature;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.internal.IImsServiceFeatureCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class FeatureConnectorTest extends ImsTestBase {

    private static class TestFeatureConnection extends FeatureConnection {

        public TestFeatureConnection(Context context, int slotId, int subId, IImsConfig c,
                IImsRegistration r, ISipTransport s) {
            super(context, slotId, subId, c, r, s);
        }

        @Override
        protected Integer retrieveFeatureState() {
            return null;
        }

        @Override
        protected void onFeatureCapabilitiesUpdated(long capabilities) {
        }
    }

    private static class TestManager implements FeatureUpdates {

        public IImsServiceFeatureCallback callback;
        public TestFeatureConnection connection;
        private Context mContext;
        private int mPhoneId;


        public TestManager(Context context, int phoneId) {
            mContext = context;
            mPhoneId = phoneId;
        }

        @Override
        public void registerFeatureCallback(int slotId, IImsServiceFeatureCallback cb) {
            callback = cb;
        }

        @Override
        public void unregisterFeatureCallback(IImsServiceFeatureCallback cb) {
            callback = null;
        }

        @Override
        public void associate(ImsFeatureContainer c, int subId) {
            connection = new TestFeatureConnection(mContext, mPhoneId, subId, c.imsConfig,
                    c.imsRegistration, c.sipTransport);
            connection.setBinder(c.imsFeature);
        }

        @Override
        public void invalidate() {
            connection = null;
        }

        @Override
        public void updateFeatureState(int state) {
            assertNotNull(connection);
            connection.updateFeatureState(state);
        }

        @Override
        public void updateFeatureCapabilities(long capabilities) {
            connection.updateFeatureCapabilities(capabilities);
        }
    }

    private FeatureConnector<TestManager> mFeatureConnector;
    private TestManager mTestManager;
    @Mock private FeatureConnector.Listener<TestManager> mListener;
    @Mock private IBinder feature;
    @Mock private IImsRegistration reg;
    @Mock private IImsConfig config;
    @Mock private ISipTransport transport;

    private static final int PHONE_ID = 1;
    private static final int SUB_ID = 2;
    private static final long TEST_CAPS = ImsService.CAPABILITY_EMERGENCY_OVER_MMTEL;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setImsSupportedFeature(true);
        mTestManager = new TestManager(mContext, PHONE_ID);
        when(feature.isBinderAlive()).thenReturn(true);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testConnect() throws Exception {
        createFeatureConnector();
        mFeatureConnector.connect();
        assertNotNull("connect should trigger the callback registration", mTestManager.callback);

        // simulate callback from ImsResolver
        mTestManager.callback.imsFeatureCreated(createContainer(), SUB_ID);
        assertNotNull(mTestManager.connection);
        assertEquals(TEST_CAPS, mTestManager.connection.getFeatureCapabilties());
        verify(mListener, never()).connectionReady(any(), eq(SUB_ID));
        verify(mListener, never()).connectionUnavailable(anyInt());

        // simulate callback from ImsResolver
        mTestManager.callback.imsStatusChanged(ImsFeature.STATE_READY, SUB_ID);
        verify(mListener).connectionReady(mTestManager, SUB_ID);
        verify(mListener, never()).connectionUnavailable(anyInt());
    }

    @Test
    @SmallTest
    public void testConnectNotSupported() {
        createFeatureConnector();
        // set not supported
        setImsSupportedFeature(false);

        mFeatureConnector.connect();
        assertNull("connect should not the callback registration if not supported",
                mTestManager.callback);
        verify(mListener).connectionUnavailable(
                FeatureConnector.UNAVAILABLE_REASON_IMS_UNSUPPORTED);
    }

    @Test
    @SmallTest
    public void testConnectReadyNotReady() throws Exception {
        createFeatureConnector();
        mFeatureConnector.connect();
        assertNotNull("connect should trigger the callback registration", mTestManager.callback);
        // simulate callback from ImsResolver
        mTestManager.callback.imsFeatureCreated(createContainer(), SUB_ID);
        mTestManager.callback.imsStatusChanged(ImsFeature.STATE_READY, SUB_ID);
        verify(mListener).connectionReady(mTestManager, SUB_ID);
        verify(mListener, never()).connectionUnavailable(anyInt());

        mTestManager.callback.imsStatusChanged(ImsFeature.STATE_UNAVAILABLE, SUB_ID);
        assertNotNull("When not ready, the callback should still be registered",
                mTestManager.callback);
        assertNotNull("Do not invalidate the connection if not ready", mTestManager.connection);
        verify(mListener).connectionReady(mTestManager, SUB_ID);
        verify(mListener).connectionUnavailable(FeatureConnector.UNAVAILABLE_REASON_NOT_READY);
    }

    @Test
    @SmallTest
    public void testConnectReadyAndInitializing() throws Exception {
        ArrayList<Integer> filterList = new ArrayList<>();
        filterList.add(ImsFeature.STATE_READY);
        filterList.add(ImsFeature.STATE_INITIALIZING);
        createFeatureConnector(filterList);
        mFeatureConnector.connect();
        assertNotNull("connect should trigger the callback registration", mTestManager.callback);
        // simulate callback from ImsResolver
        mTestManager.callback.imsFeatureCreated(createContainer(), SUB_ID);
        verify(mListener, never()).connectionReady(mTestManager, SUB_ID);
        verify(mListener, never()).connectionUnavailable(anyInt());

        mTestManager.callback.imsStatusChanged(ImsFeature.STATE_INITIALIZING, SUB_ID);
        assertNotNull("When not ready, the callback should still be registered",
                mTestManager.callback);
        assertNotNull("Do not invalidate the connection if not ready", mTestManager.connection);
        verify(mListener).connectionReady(mTestManager, SUB_ID);
        verify(mListener, never()).connectionUnavailable(anyInt());

        mTestManager.callback.imsStatusChanged(ImsFeature.STATE_READY, SUB_ID);
        assertNotNull("When not ready, the callback should still be registered",
                mTestManager.callback);
        assertNotNull("Do not invalidate the connection if not ready", mTestManager.connection);
        // Should not notify ready multiple times
        verify(mListener).connectionReady(mTestManager, SUB_ID);
        verify(mListener, never()).connectionUnavailable(anyInt());
    }

    @Test
    @SmallTest
    public void testConnectReadyAndUnavailable() throws Exception {
        ArrayList<Integer> filterList = new ArrayList<>();
        filterList.add(ImsFeature.STATE_READY);
        filterList.add(ImsFeature.STATE_INITIALIZING);
        filterList.add(ImsFeature.STATE_UNAVAILABLE);
        createFeatureConnector(filterList);
        mFeatureConnector.connect();
        assertNotNull("connect should trigger the callback registration", mTestManager.callback);
        // simulate callback from ImsResolver
        mTestManager.callback.imsFeatureCreated(createContainer(), SUB_ID);
        verify(mListener).connectionReady(mTestManager, SUB_ID);
        verify(mListener, never()).connectionUnavailable(anyInt());

        mTestManager.callback.imsStatusChanged(ImsFeature.STATE_UNAVAILABLE, SUB_ID);
        assertNotNull("When not ready, the callback should still be registered",
                mTestManager.callback);
        assertNotNull("Do not invalidate the connection if not ready", mTestManager.connection);
        verify(mListener).connectionReady(mTestManager, SUB_ID);
        verify(mListener, never()).connectionUnavailable(anyInt());

        mTestManager.callback.imsStatusChanged(ImsFeature.STATE_INITIALIZING, SUB_ID);
        assertNotNull("When not ready, the callback should still be registered",
                mTestManager.callback);
        assertNotNull("Do not invalidate the connection if not ready", mTestManager.connection);
        // Should not notify ready multiple times
        verify(mListener).connectionReady(mTestManager, SUB_ID);
        verify(mListener, never()).connectionUnavailable(anyInt());

        mTestManager.callback.imsStatusChanged(ImsFeature.STATE_READY, SUB_ID);
        // Should not notify ready multiple times
        verify(mListener).connectionReady(mTestManager, SUB_ID);
        verify(mListener, never()).connectionUnavailable(anyInt());
    }

    @Test
    @SmallTest
    public void testCantConnectToServer() throws Exception {
        ArrayList<Integer> filterList = new ArrayList<>();
        filterList.add(ImsFeature.STATE_READY);
        filterList.add(ImsFeature.STATE_INITIALIZING);
        filterList.add(ImsFeature.STATE_UNAVAILABLE);
        createFeatureConnector(filterList);

        mFeatureConnector.connect();
        mTestManager.callback.imsFeatureRemoved(
                FeatureConnector.UNAVAILABLE_REASON_SERVER_UNAVAILABLE);
        verify(mListener).connectionUnavailable(
                FeatureConnector.UNAVAILABLE_REASON_SERVER_UNAVAILABLE);

        // Clear callback and ensure that the second connect tries to register a callback.
        mTestManager.registerFeatureCallback(PHONE_ID, null);
        mFeatureConnector.connect();
        assertNotNull("The register request should happen the second time as well.",
                mTestManager.callback);
        mTestManager.callback.imsFeatureRemoved(
                FeatureConnector.UNAVAILABLE_REASON_SERVER_UNAVAILABLE);
        // In the special case that UNAVAILABLE_REASON_SERVER_UNAVAILABLE is returned, we should get
        // an unavailable callback every time because it will require connect to be called again.
        verify(mListener,times(2)).connectionUnavailable(
                FeatureConnector.UNAVAILABLE_REASON_SERVER_UNAVAILABLE);
    }

    @Test
    @SmallTest
    public void testConnectReadyRemovedReady() throws Exception {
        createFeatureConnector();
        mFeatureConnector.connect();
        assertNotNull("connect should trigger the callback registration", mTestManager.callback);
        // simulate callback from ImsResolver
        mTestManager.callback.imsFeatureCreated(createContainer(), SUB_ID);
        mTestManager.callback.imsStatusChanged(ImsFeature.STATE_READY, SUB_ID);
        verify(mListener).connectionReady(mTestManager, SUB_ID);
        verify(mListener, never()).connectionUnavailable(anyInt());

        mTestManager.callback.imsFeatureRemoved(FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);
        assertNotNull("When not ready, the callback should still be registered",
                mTestManager.callback);
        verify(mListener).connectionReady(mTestManager, SUB_ID);
        verify(mListener).connectionUnavailable(FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);

        mTestManager.callback.imsFeatureCreated(createContainer(), SUB_ID);
        mTestManager.callback.imsStatusChanged(ImsFeature.STATE_READY, SUB_ID);
        verify(mListener, times(2)).connectionReady(mTestManager, SUB_ID);
        verify(mListener).connectionUnavailable(FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);
    }

    @Test
    @SmallTest
    public void testConnectDisconnect() throws Exception {
        createFeatureConnector();
        mFeatureConnector.connect();
        assertNotNull("connect should trigger the callback registration", mTestManager.callback);
        // simulate callback from ImsResolver
        mTestManager.callback.imsFeatureCreated(createContainer(), SUB_ID);
        mTestManager.callback.imsStatusChanged(ImsFeature.STATE_READY, SUB_ID);
        IImsServiceFeatureCallback oldCb = mTestManager.callback;
        TestFeatureConnection testFc = mTestManager.connection;

        mFeatureConnector.disconnect();
        assertNull(mTestManager.callback);
        assertNull(mTestManager.connection);
        verify(mListener).connectionReady(mTestManager, SUB_ID);
        verify(mListener).connectionUnavailable(FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);

        // make sure status/caps updates do not trigger more events after disconnect
        oldCb.imsStatusChanged(ImsFeature.STATE_READY, SUB_ID);
        oldCb.imsStatusChanged(ImsFeature.STATE_UNAVAILABLE, SUB_ID);
        oldCb.updateCapabilities(0);
        assertEquals(TEST_CAPS, testFc.getFeatureCapabilties());
        verify(mListener).connectionReady(mTestManager, SUB_ID);
        verify(mListener).connectionUnavailable(FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);
    }

    @Test
    @SmallTest
    public void testConnectDisconnectConnect() throws Exception {
        createFeatureConnector();
        mFeatureConnector.connect();
        assertNotNull("connect should trigger the callback registration", mTestManager.callback);
        // simulate callback from ImsResolver
        mTestManager.callback.imsFeatureCreated(createContainer(), SUB_ID);
        mTestManager.callback.imsStatusChanged(ImsFeature.STATE_READY, SUB_ID);

        mFeatureConnector.disconnect();
        assertNull(mTestManager.callback);
        assertNull(mTestManager.connection);
        verify(mListener).connectionReady(mTestManager, SUB_ID);
        verify(mListener).connectionUnavailable(FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);

        mFeatureConnector.connect();
        assertNotNull(mTestManager.callback);
        // simulate callback from ImsResolver
        mTestManager.callback.imsFeatureCreated(createContainer(), SUB_ID);
        mTestManager.callback.imsStatusChanged(ImsFeature.STATE_READY, SUB_ID);
        assertNotNull(mTestManager.connection);
        verify(mListener, times(2)).connectionReady(mTestManager, SUB_ID);
        verify(mListener).connectionUnavailable(FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);
    }

    @Test
    @SmallTest
    public void testUpdateCapabilities() throws Exception {
        createFeatureConnector();
        mFeatureConnector.connect();
        assertNotNull("connect should trigger the callback registration", mTestManager.callback);
        // simulate callback from ImsResolver
        mTestManager.callback.imsFeatureCreated(createContainer(), SUB_ID);
        mTestManager.callback.imsStatusChanged(ImsFeature.STATE_READY, SUB_ID);
        assertEquals(TEST_CAPS, mTestManager.connection.getFeatureCapabilties());
        mTestManager.callback.updateCapabilities(0);
        assertEquals(0, mTestManager.connection.getFeatureCapabilties());
    }

    @Test
    @SmallTest
    public void testUpdateStatus() throws Exception {
        createFeatureConnector();
        mFeatureConnector.connect();
        assertNotNull("connect should trigger the callback registration", mTestManager.callback);
        // simulate callback from ImsResolver
        mTestManager.callback.imsFeatureCreated(createContainer(), SUB_ID);
        mTestManager.callback.imsStatusChanged(ImsFeature.STATE_READY, SUB_ID);
        assertEquals(ImsFeature.STATE_READY, mTestManager.connection.getFeatureState());
    }

    private void setImsSupportedFeature(boolean isSupported) {
        if(isSupported) {
            mContextFixture.addSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS);
        } else {
            mContextFixture.removeSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS);
        }
    }

    private ImsFeatureContainer createContainer() {
        ImsFeatureContainer c =  new ImsFeatureContainer(feature, config, reg, transport,
                TEST_CAPS);
        c.setState(ImsFeature.STATE_UNAVAILABLE);
        return c;
    }

    private void createFeatureConnector() {
        ArrayList<Integer> filter = new ArrayList<>();
        filter.add(ImsFeature.STATE_READY);
        createFeatureConnector(filter);
    }

    private void createFeatureConnector(List<Integer> featureReadyFilter) {
        mFeatureConnector = new FeatureConnector<>(mContext, PHONE_ID,
                (c, p) -> mTestManager, "Test", featureReadyFilter, mListener, Runnable::run);
    }
}
