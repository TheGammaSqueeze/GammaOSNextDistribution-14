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

package com.android.services.telephony.domainselection;

import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_CALLBACK_MODE_SUPPORTED_BOOL;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.assertNotNull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.testing.TestableLooper;
import android.util.Log;

import com.android.TestContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

/**
 * Unit tests for EmergencyCallbackModeHelper
 */
public class EmergencyCallbackModeHelperTest {
    private static final String TAG = "EmergencyCallbackModeHelperTest";

    private static final int SLOT_0 = 0;
    private static final int SLOT_1 = 1;
    private static final int SUB_1 = 1;
    private static final int SUB_2 = 2;

    private Context mContext;
    private HandlerThread mHandlerThread;
    private TestableLooper mLooper;
    private EmergencyCallbackModeHelper mEcbmHelper;
    private CarrierConfigManager mCarrierConfigManager;
    private TelephonyManager mTelephonyManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = new TestContext() {
            private Intent mIntent;

            @Override
            public String getSystemServiceName(Class<?> serviceClass) {
                if (serviceClass == TelephonyManager.class) {
                    return Context.TELEPHONY_SERVICE;
                } else if (serviceClass == CarrierConfigManager.class) {
                    return Context.CARRIER_CONFIG_SERVICE;
                }
                return super.getSystemServiceName(serviceClass);
            }

            @Override
            public String getOpPackageName() {
                return "";
            }

            @Override
            public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
                return mIntent;
            }

            @Override
            public void sendStickyBroadcast(Intent intent) {
                mIntent = intent;
            }
        };

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        mHandlerThread = new HandlerThread("EmergencyCallbackModeHelperTest");
        mHandlerThread.start();

        try {
            mLooper = new TestableLooper(mHandlerThread.getLooper());
        } catch (Exception e) {
            logd("Unable to create looper from handler.");
        }

        mCarrierConfigManager = mContext.getSystemService(CarrierConfigManager.class);
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        doReturn(mTelephonyManager).when(mTelephonyManager).createForSubscriptionId(anyInt());

        mEcbmHelper = new EmergencyCallbackModeHelper(mContext, mHandlerThread.getLooper());
    }

    @After
    public void tearDown() throws Exception {
        if (mEcbmHelper != null) {
            mEcbmHelper.destroy();
            mEcbmHelper = null;
        }

        if (mLooper != null) {
            mLooper.destroy();
            mLooper = null;
        }
    }

    @Test
    public void testInit() throws Exception {
        ArgumentCaptor<CarrierConfigManager.CarrierConfigChangeListener> callbackCaptor =
                ArgumentCaptor.forClass(CarrierConfigManager.CarrierConfigChangeListener.class);
        ArgumentCaptor<Executor> executorCaptor = ArgumentCaptor.forClass(Executor.class);

        verify(mCarrierConfigManager).registerCarrierConfigChangeListener(executorCaptor.capture(),
                callbackCaptor.capture());
        assertNotNull(executorCaptor.getValue());
        assertNotNull(callbackCaptor.getValue());
    }

    @Test
    public void testEmergencyCallbackModeNotSupported() throws Exception {
        ArgumentCaptor<CarrierConfigManager.CarrierConfigChangeListener> callbackCaptor =
                ArgumentCaptor.forClass(CarrierConfigManager.CarrierConfigChangeListener.class);

        verify(mCarrierConfigManager).registerCarrierConfigChangeListener(any(),
                callbackCaptor.capture());

        CarrierConfigManager.CarrierConfigChangeListener callback = callbackCaptor.getValue();

        assertNotNull(callback);

        // ECBM not supported
        PersistableBundle b = getPersistableBundle(false);
        doReturn(b).when(mCarrierConfigManager).getConfigForSubId(anyInt(), anyString());
        callback.onCarrierConfigChanged(SLOT_0, SUB_1, 0, 0);

        // No TelephonyCallback registered
        verify(mTelephonyManager, never()).registerTelephonyCallback(any(), any());
    }

    @Test
    public void testEmergencyCallbackModeSupported() throws Exception {
        ArgumentCaptor<CarrierConfigManager.CarrierConfigChangeListener> callbackCaptor =
                ArgumentCaptor.forClass(CarrierConfigManager.CarrierConfigChangeListener.class);

        verify(mCarrierConfigManager).registerCarrierConfigChangeListener(any(),
                callbackCaptor.capture());

        CarrierConfigManager.CarrierConfigChangeListener callback = callbackCaptor.getValue();

        assertNotNull(callback);

        // ECBM supported
        PersistableBundle b = getPersistableBundle(true);
        doReturn(b).when(mCarrierConfigManager).getConfigForSubId(anyInt(), anyString());
        callback.onCarrierConfigChanged(SLOT_0, SUB_1, 0, 0);

        verify(mTelephonyManager).createForSubscriptionId(eq(SUB_1));

        ArgumentCaptor<TelephonyCallback> telephonyCallbackCaptor =
                ArgumentCaptor.forClass(TelephonyCallback.class);

        // TelephonyCallback registered
        verify(mTelephonyManager).registerTelephonyCallback(any(),
                telephonyCallbackCaptor.capture());

        assertNotNull(telephonyCallbackCaptor.getValue());
    }

    @Test
    public void testEmergencyCallbackModeChanged() throws Exception {
        ArgumentCaptor<CarrierConfigManager.CarrierConfigChangeListener> callbackCaptor =
                ArgumentCaptor.forClass(CarrierConfigManager.CarrierConfigChangeListener.class);

        verify(mCarrierConfigManager).registerCarrierConfigChangeListener(any(),
                callbackCaptor.capture());

        CarrierConfigManager.CarrierConfigChangeListener callback = callbackCaptor.getValue();

        assertNotNull(callback);

        // ECBM supported
        PersistableBundle b = getPersistableBundle(true);
        doReturn(b).when(mCarrierConfigManager).getConfigForSubId(anyInt(), anyString());
        callback.onCarrierConfigChanged(SLOT_0, SUB_1, 0, 0);

        verify(mTelephonyManager).createForSubscriptionId(eq(SUB_1));

        ArgumentCaptor<TelephonyCallback> telephonyCallbackCaptor =
                ArgumentCaptor.forClass(TelephonyCallback.class);

        // TelephonyCallback registered
        verify(mTelephonyManager).registerTelephonyCallback(any(),
                telephonyCallbackCaptor.capture());

        TelephonyCallback telephonyCallback = telephonyCallbackCaptor.getValue();

        assertNotNull(telephonyCallback);

        // Carrier config changes, ECBM not supported
        b = getPersistableBundle(false);
        doReturn(b).when(mCarrierConfigManager).getConfigForSubId(anyInt(), anyString());
        callback.onCarrierConfigChanged(SLOT_0, SUB_1, 0, 0);

        // TelephonyCallback unregistered
        verify(mTelephonyManager).unregisterTelephonyCallback(eq(telephonyCallback));
    }

    @Test
    public void testEmergencyCallbackModeEnter() throws Exception {
        ArgumentCaptor<CarrierConfigManager.CarrierConfigChangeListener> callbackCaptor =
                ArgumentCaptor.forClass(CarrierConfigManager.CarrierConfigChangeListener.class);

        verify(mCarrierConfigManager).registerCarrierConfigChangeListener(any(),
                callbackCaptor.capture());

        CarrierConfigManager.CarrierConfigChangeListener callback = callbackCaptor.getValue();

        assertNotNull(callback);

        // ECBM supported
        PersistableBundle b = getPersistableBundle(true);
        doReturn(b).when(mCarrierConfigManager).getConfigForSubId(anyInt(), anyString());
        callback.onCarrierConfigChanged(SLOT_0, SUB_1, 0, 0);
        callback.onCarrierConfigChanged(SLOT_1, SUB_2, 0, 0);

        // Enter ECBM on slot 1
        mContext.sendStickyBroadcast(getIntent(true, SLOT_1));

        assertFalse(mEcbmHelper.isInEmergencyCallbackMode(SLOT_0));
        assertTrue(mEcbmHelper.isInEmergencyCallbackMode(SLOT_1));
    }

    @Test
    public void testEmergencyCallbackModeExit() throws Exception {
        ArgumentCaptor<CarrierConfigManager.CarrierConfigChangeListener> callbackCaptor =
                ArgumentCaptor.forClass(CarrierConfigManager.CarrierConfigChangeListener.class);

        verify(mCarrierConfigManager).registerCarrierConfigChangeListener(any(),
                callbackCaptor.capture());

        CarrierConfigManager.CarrierConfigChangeListener callback = callbackCaptor.getValue();

        assertNotNull(callback);

        // ECBM supported
        PersistableBundle b = getPersistableBundle(true);
        doReturn(b).when(mCarrierConfigManager).getConfigForSubId(anyInt(), anyString());
        callback.onCarrierConfigChanged(SLOT_0, SUB_1, 0, 0);

        // Exit ECBM
        mContext.sendStickyBroadcast(getIntent(false, SLOT_0));

        assertFalse(mEcbmHelper.isInEmergencyCallbackMode(SLOT_0));
    }

    private static Intent getIntent(boolean inEcm, int slotIndex) {
        Intent intent = new Intent(TelephonyManager.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        intent.putExtra(TelephonyManager.EXTRA_PHONE_IN_ECM_STATE, inEcm);
        intent.putExtra(SubscriptionManager.EXTRA_SLOT_INDEX, slotIndex);
        return intent;
    }

    private static PersistableBundle getPersistableBundle(boolean supported) {
        PersistableBundle bundle  = new PersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_CALLBACK_MODE_SUPPORTED_BOOL, supported);
        return bundle;
    }

    private static void logd(String str) {
        Log.d(TAG, str);
    }
}
