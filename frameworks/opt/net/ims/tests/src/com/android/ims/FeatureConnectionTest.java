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

import junit.framework.AssertionFailedError;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.aidl.ISipTransport;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;


@RunWith(AndroidJUnit4.class)
public class FeatureConnectionTest extends ImsTestBase {

    private class TestFeatureConnection extends FeatureConnection {
        private Integer mFeatureState = ImsFeature.STATE_READY;

        public boolean isFeatureCreatedCalled = false;
        public boolean isFeatureRemovedCalled = false;
        public int mNewStatus = ImsFeature.STATE_UNAVAILABLE;
        public long mCapabilities;

        TestFeatureConnection(Context context, int slotId, int subId) {
            super(context, slotId, subId, mConfigBinder, mRegistrationBinder, mSipTransportBinder);
            if (!ImsManager.isImsSupportedOnDevice(context)) {
                sImsSupportedOnDevice = false;
            }
        }

        @Override
        public void checkServiceIsReady() throws RemoteException {
            super.checkServiceIsReady();
        }

        @Override
        protected Integer retrieveFeatureState() {
            return mFeatureState;
        }

        @Override
        protected void onFeatureCapabilitiesUpdated(long capabilities) {
            mCapabilities = capabilities;
        }

        public void setFeatureState(int state) {
            mFeatureState = state;
        }
    };

    private TestFeatureConnection mTestFeatureConnection;
    @Mock IBinder mBinder;
    @Mock IImsRegistration mRegistrationBinder;
    @Mock IImsConfig mConfigBinder;
    @Mock ISipTransport mSipTransportBinder;

    public static final int PHONE_ID = 1;
    public static final int SUB_ID = 2;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        doReturn(null).when(mContext).getMainLooper();
        mContextFixture.addSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS);

        mTestFeatureConnection = new TestFeatureConnection(mContext, PHONE_ID, SUB_ID);
        mTestFeatureConnection.setBinder(mBinder);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test service is ready when binder is alive and IMS status is ready.
     */
    @Test
    @SmallTest
    public void testServiceIsReady() {
        when(mBinder.isBinderAlive()).thenReturn(true);
        mTestFeatureConnection.setFeatureState(ImsFeature.STATE_READY);

        try {
            mTestFeatureConnection.checkServiceIsReady();
        } catch (RemoteException e) {
            throw new AssertionFailedError("Exception in testServiceIsReady: " + e);
        }
    }

    /**
     * Test service is not ready when binder is not alive or status is not ready.
     */
    @Test
    @SmallTest
    public void testServiceIsNotReady() {
        // Binder is not alive
        when(mBinder.isBinderAlive()).thenReturn(false);

        try {
            mTestFeatureConnection.checkServiceIsReady();
            throw new AssertionFailedError("testServiceIsNotReady: binder isn't alive");
        } catch (RemoteException e) {
            // expected result
        }

        // IMS feature status is unavailable
        when(mBinder.isBinderAlive()).thenReturn(true);
        mTestFeatureConnection.setFeatureState(ImsFeature.STATE_UNAVAILABLE);

        try {
            mTestFeatureConnection.checkServiceIsReady();
            throw new AssertionFailedError("testServiceIsNotReady: status unavailable");
        } catch (RemoteException e) {
            // expected result
        }
    }

    /**
     * Test registration tech callbacks.
     */
    @Test
    @SmallTest
    public void testRegistrationTech() throws Exception {
        when(mRegistrationBinder.getRegistrationTechnology()).thenReturn(
                ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN);

        assertEquals(ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN,
                mTestFeatureConnection.getRegistrationTech());

    }

    /**
     * Test registration tech callbacks.
     */
    @Test
    @SmallTest
    public void testUpdateCapabilities() throws Exception {
        long testCaps = 1;
        assertEquals(0 /*base state*/, mTestFeatureConnection.mCapabilities);
        mTestFeatureConnection.updateFeatureCapabilities(testCaps);
        assertEquals(testCaps, mTestFeatureConnection.mCapabilities);

    }
}
