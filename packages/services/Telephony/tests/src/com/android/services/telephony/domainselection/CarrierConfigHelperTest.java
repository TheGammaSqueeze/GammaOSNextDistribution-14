/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.telephony.AccessNetworkConstants.AccessNetworkType.EUTRAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.NGRAN;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_OVER_IMS_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.assertNotNull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.testing.TestableLooper;
import android.util.Log;

import com.android.TestContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

/**
 * Unit tests for CarrierConfigHelper
 */
public class CarrierConfigHelperTest {
    private static final String TAG = "CarrierConfigHelperTest";

    private static final int SLOT_0 = 0;
    private static final int SLOT_1 = 1;
    private static final int SUB_1 = 1;
    private static final int TEST_SIM_CARRIER_ID = 1911;

    @Mock private SharedPreferences mSharedPreferences;
    @Mock private SharedPreferences.Editor mEditor;
    @Mock private Resources mResources;

    private Context mContext;
    private HandlerThread mHandlerThread;
    private TestableLooper mLooper;
    private CarrierConfigHelper mCarrierConfigHelper;
    private CarrierConfigManager mCarrierConfigManager;
    private TelephonyManager mTelephonyManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = new TestContext() {
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
            public Resources getResources() {
                return mResources;
            }
        };

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        mHandlerThread = new HandlerThread("CarrierConfigHelperTest");
        mHandlerThread.start();

        try {
            mLooper = new TestableLooper(mHandlerThread.getLooper());
        } catch (Exception e) {
            logd("Unable to create looper from handler.");
        }

        doReturn(mEditor).when(mSharedPreferences).edit();

        mCarrierConfigManager = mContext.getSystemService(CarrierConfigManager.class);
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        doReturn(TelephonyManager.SIM_STATE_READY)
                .when(mTelephonyManager).getSimState(anyInt());

        doReturn(new int[] { TEST_SIM_CARRIER_ID }).when(mResources).getIntArray(anyInt());

        mCarrierConfigHelper = new CarrierConfigHelper(mContext, mHandlerThread.getLooper(),
                mSharedPreferences);
    }

    @After
    public void tearDown() throws Exception {
        if (mCarrierConfigHelper != null) {
            mCarrierConfigHelper.destroy();
            mCarrierConfigHelper = null;
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
        assertFalse(mCarrierConfigHelper.isVoNrEmergencySupported(SLOT_0));
    }

    @Test
    public void testCarrierConfigNotApplied() throws Exception {
        ArgumentCaptor<CarrierConfigManager.CarrierConfigChangeListener> callbackCaptor =
                ArgumentCaptor.forClass(CarrierConfigManager.CarrierConfigChangeListener.class);

        verify(mCarrierConfigManager).registerCarrierConfigChangeListener(any(),
                callbackCaptor.capture());

        CarrierConfigManager.CarrierConfigChangeListener callback = callbackCaptor.getValue();

        assertNotNull(callback);

        // NR is included but carrier config is not applied.
        PersistableBundle b = getPersistableBundle(new int[] { EUTRAN, NGRAN }, false);
        doReturn(b).when(mCarrierConfigManager).getConfigForSubId(anyInt(), anyString());
        callback.onCarrierConfigChanged(SLOT_0, SUB_1, 0, 0);

        assertFalse(mCarrierConfigHelper.isVoNrEmergencySupported(SLOT_0));
    }

    @Test
    public void testCarrierConfigApplied() throws Exception {
        ArgumentCaptor<CarrierConfigManager.CarrierConfigChangeListener> callbackCaptor =
                ArgumentCaptor.forClass(CarrierConfigManager.CarrierConfigChangeListener.class);

        verify(mCarrierConfigManager).registerCarrierConfigChangeListener(any(),
                callbackCaptor.capture());

        CarrierConfigManager.CarrierConfigChangeListener callback = callbackCaptor.getValue();

        assertNotNull(callback);

        // NR is included and carrier config is applied.
        PersistableBundle b = getPersistableBundle(new int[] { EUTRAN, NGRAN }, true);
        doReturn(b).when(mCarrierConfigManager).getConfigForSubId(anyInt(), anyString());
        callback.onCarrierConfigChanged(SLOT_0, SUB_1, 0, 0);

        assertTrue(mCarrierConfigHelper.isVoNrEmergencySupported(SLOT_0));
        assertFalse(mCarrierConfigHelper.isVoNrEmergencySupported(SLOT_1));

        verify(mEditor).putBoolean(eq(CarrierConfigHelper.KEY_VONR_EMERGENCY_SUPPORT + SLOT_0),
                eq(true));

        // NR is not included and carrier config is applied.
        b = getPersistableBundle(new int[] { EUTRAN }, true);
        doReturn(b).when(mCarrierConfigManager).getConfigForSubId(anyInt(), anyString());
        callback.onCarrierConfigChanged(SLOT_0, SUB_1, 0, 0);

        assertFalse(mCarrierConfigHelper.isVoNrEmergencySupported(SLOT_0));

        verify(mEditor).putBoolean(eq(CarrierConfigHelper.KEY_VONR_EMERGENCY_SUPPORT + SLOT_0),
                eq(false));
    }

    @Test
    public void testCarrierConfigInvalidSubId() throws Exception {
        ArgumentCaptor<CarrierConfigManager.CarrierConfigChangeListener> callbackCaptor =
                ArgumentCaptor.forClass(CarrierConfigManager.CarrierConfigChangeListener.class);

        verify(mCarrierConfigManager).registerCarrierConfigChangeListener(any(),
                callbackCaptor.capture());

        CarrierConfigManager.CarrierConfigChangeListener callback = callbackCaptor.getValue();

        assertNotNull(callback);

        // NR is included and carrier config is applied.
        PersistableBundle b = getPersistableBundle(new int[] { EUTRAN, NGRAN }, true);
        doReturn(b).when(mCarrierConfigManager).getConfigForSubId(anyInt(), anyString());

        // Invalid subscription
        callback.onCarrierConfigChanged(SLOT_0, SubscriptionManager.INVALID_SUBSCRIPTION_ID, 0, 0);

        assertFalse(mCarrierConfigHelper.isVoNrEmergencySupported(SLOT_0));
    }

    @Test
    public void testRestoreFromSharedPreferences() throws Exception {
        doReturn(true).when(mSharedPreferences).getBoolean(anyString(), anyBoolean());
        mCarrierConfigHelper = new CarrierConfigHelper(mContext, mHandlerThread.getLooper(),
                mSharedPreferences);

        assertTrue(mCarrierConfigHelper.isVoNrEmergencySupported(SLOT_0));
    }

    @Test
    public void testCarrierIgnoreNrWhenSimRemoved() throws Exception {
        ArgumentCaptor<CarrierConfigManager.CarrierConfigChangeListener> callbackCaptor =
                ArgumentCaptor.forClass(CarrierConfigManager.CarrierConfigChangeListener.class);

        verify(mCarrierConfigManager).registerCarrierConfigChangeListener(any(),
                callbackCaptor.capture());

        CarrierConfigManager.CarrierConfigChangeListener callback = callbackCaptor.getValue();

        assertNotNull(callback);

        // NR is included and carrier config for TEST SIM is applied.
        PersistableBundle b = getPersistableBundle(new int[] { EUTRAN, NGRAN }, true);
        doReturn(b).when(mCarrierConfigManager).getConfigForSubId(anyInt(), anyString());
        callback.onCarrierConfigChanged(SLOT_0, SUB_1, TEST_SIM_CARRIER_ID, 0);

        // NR is ignored.
        assertFalse(mCarrierConfigHelper.isVoNrEmergencySupported(SLOT_0));
        assertFalse(mCarrierConfigHelper.isVoNrEmergencySupported(SLOT_1));
    }

    private static PersistableBundle getPersistableBundle(int[] imsRats, boolean applied) {
        PersistableBundle bundle  = new PersistableBundle();
        bundle.putIntArray(KEY_EMERGENCY_OVER_IMS_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY, imsRats);
        bundle.putBoolean(KEY_CARRIER_CONFIG_APPLIED_BOOL, applied);
        return bundle;
    }

    private static void logd(String str) {
        Log.d(TAG, str);
    }
}
