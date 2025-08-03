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

package com.android.telephony.qns;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.os.Message;
import android.os.test.TestLooper;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.feature.ImsFeature;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.lang.reflect.Field;
import java.util.concurrent.Executor;

@RunWith(JUnit4.class)
public class QnsProvisioningListenerTest extends QnsTest {

    private QnsProvisioningListener mQnsProvisioningListener;
    private final int mSlotIndex = 0;
    private MockitoSession mMockitoSession;
    @Mock private ProvisioningManager mMockProvisioningManager;

    private Handler mHandler;
    TestLooper mTestLooper;
    private ProvisioningManager.Callback mQnsProvisioningCallback;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        super.setUp();
        mMockitoSession =
                mockitoSession()
                        .mockStatic(IwlanNetworkStatusTracker.class)
                        .mockStatic(QnsUtils.class)
                        .mockStatic(SubscriptionManager.class)
                        .mockStatic(ProvisioningManager.class)
                        .startMocking();
        mTestLooper = new TestLooper();
        mHandler = new Handler(mTestLooper.getLooper());
        mockStatics();
        mQnsProvisioningListener =
                new QnsProvisioningListener(sMockContext, mMockQnsImsManager, mSlotIndex);
        captureProvisioningHandler();
    }

    private void captureProvisioningHandler() throws ImsException {
        ArgumentCaptor<ProvisioningManager.Callback> arg =
                ArgumentCaptor.forClass(ProvisioningManager.Callback.class);

        verify(mMockProvisioningManager, atLeast(1))
                .registerProvisioningChangedCallback(isA(Executor.class), arg.capture());
        mQnsProvisioningCallback = arg.getValue();
    }

    private void mockStatics() throws Exception {
        lenient()
                .when(ProvisioningManager.createForSubscriptionId(mSlotIndex))
                .thenReturn(mMockProvisioningManager);
        lenient().when(QnsUtils.getSubId(sMockContext, mSlotIndex)).thenReturn(mSlotIndex);
        lenient().when(SubscriptionManager.isValidSubscriptionId(mSlotIndex)).thenReturn(true);
        when(mMockQnsImsManager.getImsServiceState()).thenReturn(ImsFeature.STATE_READY);
        when(mMockProvisioningManager.getProvisioningIntValue(anyInt())).thenReturn(0);
        when(mMockProvisioningManager.getProvisioningStringValue(anyInt())).thenReturn("");
    }

    @After
    public void tearDown() {
        mMockitoSession.finishMocking();
    }

    @Test
    public void testRegisterProvisioningItemInfoChanged() {
        // wait for QnsProvisioningListener to load default items.
        waitForLastHandlerAction(mQnsProvisioningListener.mQnsProvisioningHandler);
        mQnsProvisioningListener.registerProvisioningItemInfoChanged(mHandler, 1, null, true);

        Message msg = mTestLooper.nextMessage();
        assertNotNull(msg);
        assertEquals(1, msg.what);
        assertNotNull(msg.obj);
        QnsAsyncResult result = (QnsAsyncResult) msg.obj;
        assertNotNull(result);
        QnsProvisioningListener.QnsProvisioningInfo info =
                (QnsProvisioningListener.QnsProvisioningInfo) result.mResult;
        assertNotNull(info);
        mTestLooper.dispatchAll();

        mQnsProvisioningCallback.onProvisioningIntChanged(
                ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        waitForLastHandlerAction(mQnsProvisioningListener.mQnsProvisioningHandler);
        msg = mTestLooper.nextMessage();
        assertNotNull(msg);
        assertEquals(1, msg.what);
        assertNotNull(msg.obj);
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result);
        info = (QnsProvisioningListener.QnsProvisioningInfo) result.mResult;
        assertNotNull(info);
        assertEquals(
                ProvisioningManager.PROVISIONING_VALUE_ENABLED,
                (int) info.getIntegerItem(ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS));

        mQnsProvisioningListener.registerProvisioningItemInfoChanged(mHandler, 2, null, false);
        msg = mTestLooper.nextMessage();
        assertNull(msg);
    }

    @Test
    public void testUnregisterProvisioningItemInfoChanged() {
        // wait for QnsProvisioningListener to load default items.
        waitForLastHandlerAction(mQnsProvisioningListener.mQnsProvisioningHandler);
        mQnsProvisioningListener.registerProvisioningItemInfoChanged(mHandler, 1, null, false);

        mQnsProvisioningCallback.onProvisioningIntChanged(
                ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        waitForLastHandlerAction(mQnsProvisioningListener.mQnsProvisioningHandler);
        Message msg = mTestLooper.nextMessage();
        assertNotNull(msg);

        mQnsProvisioningListener.unregisterProvisioningItemInfoChanged(mHandler);

        // Handler unregistered - so should not receive callback
        mQnsProvisioningCallback.onProvisioningIntChanged(
                ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        waitForLastHandlerAction(mQnsProvisioningListener.mQnsProvisioningHandler);
        msg = mTestLooper.nextMessage();
        assertNull(msg);
    }

    @Test
    public void testQnsProvisioningInfo() {
        // wait for QnsProvisioningListener to load default items.
        waitForLastHandlerAction(mQnsProvisioningListener.mQnsProvisioningHandler);
        String entitlementId = "test-id";
        mQnsProvisioningListener.registerProvisioningItemInfoChanged(mHandler, 1, null, false);
        mQnsProvisioningCallback.onProvisioningIntChanged(
                ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        waitForLastHandlerAction(mQnsProvisioningListener.mQnsProvisioningHandler);
        mTestLooper.nextMessage();
        mQnsProvisioningCallback.onProvisioningStringChanged(
                ProvisioningManager.KEY_VOICE_OVER_WIFI_ENTITLEMENT_ID, entitlementId);
        waitForLastHandlerAction(mQnsProvisioningListener.mQnsProvisioningHandler);
        mTestLooper.nextMessage();
        mQnsProvisioningCallback.onProvisioningIntChanged(
                ProvisioningManager.KEY_VOICE_OVER_WIFI_MODE_OVERRIDE,
                ImsMmTelManager.WIFI_MODE_WIFI_PREFERRED);
        waitForLastHandlerAction(mQnsProvisioningListener.mQnsProvisioningHandler);
        Message msg = mTestLooper.nextMessage();

        assertNotNull(msg);
        assertEquals(1, msg.what);
        assertNotNull(msg.obj);
        QnsAsyncResult result = (QnsAsyncResult) msg.obj;
        assertNotNull(result);
        QnsProvisioningListener.QnsProvisioningInfo info =
                (QnsProvisioningListener.QnsProvisioningInfo) result.mResult;

        // test hasItem()
        assertTrue(info.hasItem(ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS));

        // test getIntegerItem()
        assertEquals(
                ProvisioningManager.PROVISIONING_VALUE_ENABLED,
                (int) info.getIntegerItem(ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS));
        assertEquals(
                ImsMmTelManager.WIFI_MODE_WIFI_PREFERRED,
                (int) info.getIntegerItem(ProvisioningManager.KEY_VOICE_OVER_WIFI_MODE_OVERRIDE));

        // test getStringItem()
        assertEquals(
                entitlementId,
                info.getStringItem(ProvisioningManager.KEY_VOICE_OVER_WIFI_ENTITLEMENT_ID));

        // test markUpdated() and isUpdated();
        info.markUpdated(false);
        assertFalse(info.isUpdated());

        info.markUpdated(true);
        assertTrue(info.isUpdated());

        // test clear()
        info.clear();
        mQnsProvisioningListener.mQnsProvisioningHandler.handleMessage(
                Message.obtain(
                        mQnsProvisioningListener.mQnsProvisioningHandler,
                        11005, // EVENT_IMS_STATE_CHANGED
                        new QnsAsyncResult(null, new QnsImsManager.ImsState(false), null)));
        assertNull(info.getIntegerItem(ProvisioningManager.KEY_VOICE_OVER_WIFI_MODE_OVERRIDE));
        assertNull(info.getIntegerItem(ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS));
        assertNull(info.getStringItem(ProvisioningManager.KEY_VOICE_OVER_WIFI_ENTITLEMENT_ID));
    }

    @Test
    public void testEqualsIntegerItem_QnsProvisioningInfo() {
        // wait for QnsProvisioningListener to load default items.
        waitForLastHandlerAction(mQnsProvisioningListener.mQnsProvisioningHandler);
        mQnsProvisioningListener.registerProvisioningItemInfoChanged(mHandler, 1, null, false);
        mQnsProvisioningCallback.onProvisioningIntChanged(
                ProvisioningManager.KEY_VOICE_OVER_WIFI_MODE_OVERRIDE,
                ImsMmTelManager.WIFI_MODE_WIFI_PREFERRED);
        waitForLastHandlerAction(mQnsProvisioningListener.mQnsProvisioningHandler);
        Message msg = mTestLooper.nextMessage();
        assertNotNull(msg);
        assertEquals(1, msg.what);
        assertNotNull(msg.obj);
        QnsAsyncResult result = (QnsAsyncResult) msg.obj;
        assertNotNull(result);
        QnsProvisioningListener.QnsProvisioningInfo info1 =
                (QnsProvisioningListener.QnsProvisioningInfo) result.mResult;

        mQnsProvisioningCallback.onProvisioningIntChanged(
                ProvisioningManager.KEY_VOICE_OVER_WIFI_MODE_OVERRIDE,
                ImsMmTelManager.WIFI_MODE_WIFI_ONLY);
        waitForLastHandlerAction(mQnsProvisioningListener.mQnsProvisioningHandler);
        msg = mTestLooper.nextMessage();
        assertNotNull(msg);
        assertEquals(1, msg.what);
        assertNotNull(msg.obj);
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result);
        QnsProvisioningListener.QnsProvisioningInfo info2 =
                (QnsProvisioningListener.QnsProvisioningInfo) result.mResult;

        mQnsProvisioningCallback.onProvisioningIntChanged(
                ProvisioningManager.KEY_VOICE_OVER_WIFI_MODE_OVERRIDE,
                ImsMmTelManager.WIFI_MODE_WIFI_PREFERRED);
        waitForLastHandlerAction(mQnsProvisioningListener.mQnsProvisioningHandler);
        msg = mTestLooper.nextMessage();
        assertNotNull(msg);
        assertEquals(1, msg.what);
        assertNotNull(msg.obj);
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result);
        QnsProvisioningListener.QnsProvisioningInfo info3 =
                (QnsProvisioningListener.QnsProvisioningInfo) result.mResult;

        assertFalse(
                info2.equalsIntegerItem(
                        info1, ProvisioningManager.KEY_VOICE_OVER_WIFI_MODE_OVERRIDE));
        assertTrue(
                info3.equalsIntegerItem(
                        info1, ProvisioningManager.KEY_VOICE_OVER_WIFI_MODE_OVERRIDE));
    }

    @Test
    public void testUnregisterProvisioningCallback() throws ImsException {
        mQnsProvisioningListener.mQnsProvisioningHandler.handleMessage(
                Message.obtain(
                        mQnsProvisioningListener.mQnsProvisioningHandler,
                        11005, // EVENT_IMS_STATE_CHANGED
                        new QnsAsyncResult(null, new QnsImsManager.ImsState(true), null)));

        ArgumentCaptor<ProvisioningManager.Callback> arg =
                ArgumentCaptor.forClass(ProvisioningManager.Callback.class);
        verify(mMockProvisioningManager).unregisterProvisioningChangedCallback(arg.capture());

        captureProvisioningHandler();
    }

    @Test
    public void testOnIwlanNetworkStatusChangedEventHandler() throws ImsException {
        mQnsProvisioningListener.mQnsProvisioningHandler.handleMessage(
                Message.obtain(mQnsProvisioningListener.mQnsProvisioningHandler, 11002, null));

        IwlanNetworkStatusTracker.IwlanAvailabilityInfo info =
                mMockIwlanNetworkStatusTracker.new IwlanAvailabilityInfo(true, false);

        mQnsProvisioningListener.mQnsProvisioningHandler.handleMessage(
                Message.obtain(
                        mQnsProvisioningListener.mQnsProvisioningHandler,
                        11002,
                        new QnsAsyncResult(null, info, null)));
        ArgumentCaptor<ProvisioningManager.Callback> arg =
                ArgumentCaptor.forClass(ProvisioningManager.Callback.class);
        verify(mMockProvisioningManager, times(1))
                .registerProvisioningChangedCallback(isA(Executor.class), arg.capture());
        mQnsProvisioningCallback = arg.getValue();
    }

    @Test
    public void testOnImsStateChangedEventHandler()
            throws ImsException, NoSuchFieldException, IllegalAccessException {
        int eventImsStateChanged = 11005;

        mQnsProvisioningListener.mQnsProvisioningHandler.handleMessage(
                Message.obtain(
                        mQnsProvisioningListener.mQnsProvisioningHandler,
                        eventImsStateChanged,
                        null));

        QnsImsManager.ImsState unavailable = new QnsImsManager.ImsState(false);
        QnsImsManager.ImsState available = new QnsImsManager.ImsState(true);
        waitForLastHandlerAction(mQnsProvisioningListener.mQnsProvisioningHandler);
        mQnsProvisioningListener.mQnsProvisioningHandler.handleMessage(
                Message.obtain(
                        mQnsProvisioningListener.mQnsProvisioningHandler,
                        eventImsStateChanged,
                        new QnsAsyncResult(null, unavailable, null)));

        QnsProvisioningListener.QnsProvisioningInfo clearInfo =
                new QnsProvisioningListener.QnsProvisioningInfo();
        clearInfo.clear();
        waitForLastHandlerAction(mQnsProvisioningListener.mQnsProvisioningHandler);
        Field field = QnsProvisioningListener.class.getDeclaredField("mProvisioningInfo");
        field.setAccessible(true);
        QnsProvisioningListener.QnsProvisioningInfo provisioningInfo =
                (QnsProvisioningListener.QnsProvisioningInfo) field.get(mQnsProvisioningListener);

        assertEquals(clearInfo.toString(), provisioningInfo.toString());

        mQnsProvisioningListener.mQnsProvisioningHandler.handleMessage(
                Message.obtain(
                        mQnsProvisioningListener.mQnsProvisioningHandler,
                        eventImsStateChanged,
                        new QnsAsyncResult(null, available, null)));

        waitForLastHandlerAction(mQnsProvisioningListener.mQnsProvisioningHandler);

        ArgumentCaptor<ProvisioningManager.Callback> arg =
                ArgumentCaptor.forClass(ProvisioningManager.Callback.class);
        verify(mMockProvisioningManager, times(2))
                .registerProvisioningChangedCallback(isA(Executor.class), arg.capture());
        mQnsProvisioningCallback = arg.getValue();
    }
}
