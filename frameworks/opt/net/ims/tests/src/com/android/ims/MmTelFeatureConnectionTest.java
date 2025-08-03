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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.telephony.ims.feature.ImsFeature;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class MmTelFeatureConnectionTest extends ImsTestBase {

    private class TestCallback extends Binder implements IInterface {

        @Override
        public IBinder asBinder() {
            return this;
        }
    }

    private class CallbackManagerTest extends
            ImsCallbackAdapterManager<TestCallback> {

        List<TestCallback> mCallbacks = new ArrayList<>();
        FeatureConnection mFeatureConnection;

        CallbackManagerTest(Context context, Object lock, FeatureConnection featureConnection) {
            super(context, lock, SLOT_ID, SUB_ID);
            mFeatureConnection = featureConnection;
        }

        // A callback has been registered. Register that callback with the MmTelFeature.
        @Override
        public void registerCallback(TestCallback localCallback) {
            if (!isBinderReady()) {
                return;
            }
            mCallbacks.add(localCallback);
        }

        // A callback has been removed, unregister that callback with the MmTelFeature.
        @Override
        public void unregisterCallback(TestCallback localCallback) {
            if (!mFeatureConnection.isBinderAlive()) {
                return;
            }
            mCallbacks.remove(localCallback);
        }

        public boolean doesCallbackExist(TestCallback callback) {
            return mCallbacks.contains(callback);
        }

        public boolean isBinderReady() {
            return mFeatureConnection.isBinderAlive()
                    && mFeatureConnection.getFeatureState() == ImsFeature.STATE_READY;
        }
    }

    private CallbackManagerTest mCallbackManagerUT;

    @Mock
    FeatureConnection mFeatureConnection;

    public static final int SUB_ID = 1;
    public static final int SLOT_ID = 0;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        when(mFeatureConnection.isBinderAlive()).thenReturn(true);
        when(mFeatureConnection.getFeatureState()).thenReturn(ImsFeature.STATE_READY);
        mCallbackManagerUT = new CallbackManagerTest(mContext, this, mFeatureConnection);
    }

    @After
    public void tearDown() throws Exception {
        mCallbackManagerUT = null;
        super.tearDown();
    }

    /**
     * Basic test of deprecated functionality, ensure that adding the callback directly triggers the
     * appropriate registerCallback and unregisterCallback calls.
     */
    @Test
    @SmallTest
    public void testCallbackAdapter_addAndRemoveCallback() throws Exception {
        TestCallback testCallback = new TestCallback();
        mCallbackManagerUT.addCallback(testCallback);
        assertTrue(mCallbackManagerUT.doesCallbackExist(testCallback));

        mCallbackManagerUT.removeCallback(testCallback);
        assertFalse(mCallbackManagerUT.doesCallbackExist(testCallback));
    }

    /**
     * Ensure that adding the callback and linking subId triggers the appropriate registerCallback
     * and unregisterCallback calls.
     */
    @Test
    @SmallTest
    public void testCallbackAdapter_addCallbackForSubAndRemove() throws Exception {
        TestCallback testCallback = new TestCallback();
        int testSub = 1;
        mCallbackManagerUT.addCallbackForSubscription(testCallback, testSub);
        assertTrue(mCallbackManagerUT.doesCallbackExist(testCallback));

        mCallbackManagerUT.removeCallback(testCallback);
        assertFalse(mCallbackManagerUT.doesCallbackExist(testCallback));
    }

    /**
     * The close() method has been called, so all callbacks should be cleaned up and notified
     * that they have been removed.
     */
    @Test
    @SmallTest
    public void testCallbackAdapter_closeSub() throws Exception {
        TestCallback testCallback1 = new TestCallback();
        int testSub1 = 1;

        mCallbackManagerUT.addCallbackForSubscription(testCallback1, testSub1);
        assertTrue(mCallbackManagerUT.doesCallbackExist(testCallback1));

        // Close the manager, ensure subscription callback are removed
        mCallbackManagerUT.close();
        assertFalse(mCallbackManagerUT.doesCallbackExist(testCallback1));
    }

    /**
     * The close() method has been called, so all callbacks should be cleaned up.
     */
    @Test
    @SmallTest
    public void testCallbackAdapter_closeSlotBasedCallbacks() throws Exception {
        TestCallback testCallback1 = new TestCallback();
        TestCallback testCallback2 = new TestCallback();
        mCallbackManagerUT.addCallback(testCallback1);
        assertTrue(mCallbackManagerUT.doesCallbackExist(testCallback1));
        mCallbackManagerUT.addCallback(testCallback2);
        assertTrue(mCallbackManagerUT.doesCallbackExist(testCallback2));

        // Close the manager, ensure all subscription callbacks are removed
        mCallbackManagerUT.close();
        assertFalse(mCallbackManagerUT.doesCallbackExist(testCallback1));
        assertFalse(mCallbackManagerUT.doesCallbackExist(testCallback2));
    }


    /**
     * UnregisterCallback is success After ImsFeatureState changed to STATE_UNAVAILABLE.
     */
    @Test
    @SmallTest
    public void testCallbackAdapter_removeCallbackSuccessAfterImsFeatureStateChangeToUnavailable()
            throws Exception {
        TestCallback testCallback1 = new TestCallback();
        mCallbackManagerUT.addCallback(testCallback1);
        assertTrue(mCallbackManagerUT.doesCallbackExist(testCallback1));
        mCallbackManagerUT.removeCallback(testCallback1);
        assertFalse(mCallbackManagerUT.doesCallbackExist(testCallback1));

        TestCallback testCallback2 = new TestCallback();
        mCallbackManagerUT.addCallback(testCallback2);
        assertTrue(mCallbackManagerUT.doesCallbackExist(testCallback2));
        assertTrue(mCallbackManagerUT.isBinderReady());
        when(mFeatureConnection.getFeatureState()).thenReturn(ImsFeature.STATE_UNAVAILABLE);
        assertFalse(mCallbackManagerUT.isBinderReady());
        mCallbackManagerUT.removeCallback(testCallback2);
        assertFalse(mCallbackManagerUT.doesCallbackExist(testCallback2));
    }

    /**
     * UnregisterCallback is failed After binder isn't alive.
     */
    @Test
    @SmallTest
    public void testCallbackAdapter_removeCallbackFailedAfterBinderIsNotAlive() throws Exception {
        TestCallback testCallback1 = new TestCallback();
        mCallbackManagerUT.addCallback(testCallback1);
        assertTrue(mCallbackManagerUT.doesCallbackExist(testCallback1));

        when(mFeatureConnection.isBinderAlive()).thenReturn(false);
        mCallbackManagerUT.removeCallback(testCallback1);
        assertTrue(mCallbackManagerUT.doesCallbackExist(testCallback1));
    }

    /**
     * RegisterCallback is failed After binder isn't ready.
     */
    @Test
    @SmallTest
    public void testCallbackAdapter_addCallbackFailedAfterBinderIsNotReady() throws Exception {
        when(mFeatureConnection.isBinderAlive()).thenReturn(false);
        assertFalse(mCallbackManagerUT.isBinderReady());
        TestCallback testCallback1 = new TestCallback();
        mCallbackManagerUT.addCallback(testCallback1);
        assertFalse(mCallbackManagerUT.doesCallbackExist(testCallback1));

        when(mFeatureConnection.isBinderAlive()).thenReturn(true);
        when(mFeatureConnection.getFeatureState()).thenReturn(ImsFeature.STATE_UNAVAILABLE);
        assertFalse(mCallbackManagerUT.isBinderReady());
        mCallbackManagerUT.addCallback(testCallback1);
        assertFalse(mCallbackManagerUT.doesCallbackExist(testCallback1));
    }
}
