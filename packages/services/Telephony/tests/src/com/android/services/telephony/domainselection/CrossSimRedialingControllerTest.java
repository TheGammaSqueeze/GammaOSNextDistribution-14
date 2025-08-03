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

import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_CROSS_STACK_REDIAL_TIMER_SEC_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_QUICK_CROSS_STACK_REDIAL_TIMER_SEC_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_START_QUICK_CROSS_STACK_REDIAL_TIMER_WHEN_REGISTERED_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.REDIAL_TIMER_DISABLED;
import static android.telephony.PreciseDisconnectCause.EMERGENCY_PERM_FAILURE;
import static android.telephony.PreciseDisconnectCause.EMERGENCY_TEMP_FAILURE;

import static com.android.services.telephony.domainselection.CrossSimRedialingController.MSG_CROSS_STACK_TIMEOUT;
import static com.android.services.telephony.domainselection.CrossSimRedialingController.MSG_QUICK_CROSS_STACK_TIMEOUT;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.telephony.emergency.EmergencyNumber;
import android.testing.TestableLooper;
import android.util.Log;

import com.android.TestContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for CrossSimRedialingController
 */
public class CrossSimRedialingControllerTest {
    private static final String TAG = "CrossSimRedialingControllerTest";

    private static final int SLOT_0 = 0;
    private static final int SLOT_1 = 1;

    private static final String TELECOM_CALL_ID1 = "TC1";
    private static final String TEST_EMERGENCY_NUMBER = "911";

    @Mock private EmergencyCallDomainSelector mEcds;
    @Mock private CrossSimRedialingController.EmergencyNumberHelper mEmergencyNumberHelper;

    private Context mContext;

    private HandlerThread mHandlerThread;
    private TestableLooper mLooper;
    private CrossSimRedialingController mCsrController;
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
        };

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        mHandlerThread = new HandlerThread("CrossSimRedialingControllerTest");
        mHandlerThread.start();

        try {
            mLooper = new TestableLooper(mHandlerThread.getLooper());
        } catch (Exception e) {
            logd("Unable to create looper from handler.");
        }

        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        when(mTelephonyManager.createForSubscriptionId(anyInt()))
                .thenReturn(mTelephonyManager);
        when(mTelephonyManager.getNetworkCountryIso()).thenReturn("");
        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        doReturn(TelephonyManager.SIM_STATE_READY)
                .when(mTelephonyManager).getSimState(anyInt());

        mCarrierConfigManager = mContext.getSystemService(CarrierConfigManager.class);
        doReturn(getDefaultPersistableBundle()).when(mCarrierConfigManager)
                .getConfigForSubId(anyInt(), ArgumentMatchers.<String>any());

        doReturn(true).when(mEmergencyNumberHelper).isEmergencyNumber(anyInt(), anyString());

        doReturn(SLOT_0).when(mEcds).getSlotId();
    }

    @After
    public void tearDown() throws Exception {
        if (mCsrController != null) {
            mCsrController.destroy();
            mCsrController = null;
        }

        if (mLooper != null) {
            mLooper.destroy();
            mLooper = null;
        }
    }

    @Test
    public void testDefaultStartTimerInService() throws Exception {
        createController();

        boolean inService = true;
        boolean inRoaming = false;
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertFalse(mCsrController.hasMessages(MSG_QUICK_CROSS_STACK_TIMEOUT));
        assertTrue(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));

        mCsrController.sendEmptyMessage(MSG_CROSS_STACK_TIMEOUT);
        processAllMessages();

        verify(mEcds).notifyCrossStackTimerExpired();
    }

    @Test
    public void testDefaultStartTimerInServiceRoaming() throws Exception {
        createController();

        boolean inService = true;
        boolean inRoaming = true;
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertFalse(mCsrController.hasMessages(MSG_QUICK_CROSS_STACK_TIMEOUT));
        assertTrue(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));
    }

    @Test
    public void testDefaultStartTimerOutOfService() throws Exception {
        createController();

        boolean inService = false;
        boolean inRoaming = false;
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertFalse(mCsrController.hasMessages(MSG_QUICK_CROSS_STACK_TIMEOUT));
        assertTrue(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));
    }

    @Test
    public void testDefaultStartTimerOutOfServiceRoaming() throws Exception {
        createController();

        boolean inService = false;
        boolean inRoaming = true;
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertFalse(mCsrController.hasMessages(MSG_QUICK_CROSS_STACK_TIMEOUT));
        assertTrue(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));
    }

    @Test
    public void testQuickStartTimerInService() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putInt(KEY_QUICK_CROSS_STACK_REDIAL_TIMER_SEC_INT, 3);
        doReturn(bundle).when(mCarrierConfigManager)
                .getConfigForSubId(anyInt(), ArgumentMatchers.<String>any());

        createController();

        boolean inService = true;
        boolean inRoaming = false;
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertTrue(mCsrController.hasMessages(MSG_QUICK_CROSS_STACK_TIMEOUT));
        assertFalse(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));

        mCsrController.sendEmptyMessage(MSG_QUICK_CROSS_STACK_TIMEOUT);
        processAllMessages();

        verify(mEcds).notifyCrossStackTimerExpired();
    }

    @Test
    public void testQuickStartTimerInServiceRoaming() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putInt(KEY_QUICK_CROSS_STACK_REDIAL_TIMER_SEC_INT, 3);
        doReturn(bundle).when(mCarrierConfigManager)
                .getConfigForSubId(anyInt(), ArgumentMatchers.<String>any());

        createController();

        boolean inService = true;
        boolean inRoaming = true;
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertFalse(mCsrController.hasMessages(MSG_QUICK_CROSS_STACK_TIMEOUT));
        assertTrue(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));
    }

    @Test
    public void testQuickStartTimerOutOfService() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putInt(KEY_QUICK_CROSS_STACK_REDIAL_TIMER_SEC_INT, 3);
        doReturn(bundle).when(mCarrierConfigManager)
                .getConfigForSubId(anyInt(), ArgumentMatchers.<String>any());

        createController();

        boolean inService = false;
        boolean inRoaming = false;
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertTrue(mCsrController.hasMessages(MSG_QUICK_CROSS_STACK_TIMEOUT));
        assertFalse(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));
    }

    @Test
    public void testQuickStartTimerOutOfServiceRoaming() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putInt(KEY_QUICK_CROSS_STACK_REDIAL_TIMER_SEC_INT, 3);
        doReturn(bundle).when(mCarrierConfigManager)
                .getConfigForSubId(anyInt(), ArgumentMatchers.<String>any());

        createController();

        boolean inService = false;
        boolean inRoaming = true;
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertFalse(mCsrController.hasMessages(MSG_QUICK_CROSS_STACK_TIMEOUT));
        assertTrue(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));
    }

    @Test
    public void testNoNormalStartTimerInService() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putInt(KEY_CROSS_STACK_REDIAL_TIMER_SEC_INT, REDIAL_TIMER_DISABLED);
        doReturn(bundle).when(mCarrierConfigManager)
                .getConfigForSubId(anyInt(), ArgumentMatchers.<String>any());

        createController();

        boolean inService = true;
        boolean inRoaming = false;
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertFalse(mCsrController.hasMessages(MSG_QUICK_CROSS_STACK_TIMEOUT));
        assertFalse(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));
    }

    @Test
    public void testQuickWhenInServiceStartTimerOutOfService() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putInt(KEY_QUICK_CROSS_STACK_REDIAL_TIMER_SEC_INT, 3);
        bundle.putBoolean(KEY_START_QUICK_CROSS_STACK_REDIAL_TIMER_WHEN_REGISTERED_BOOL, true);
        doReturn(bundle).when(mCarrierConfigManager)
                .getConfigForSubId(anyInt(), ArgumentMatchers.<String>any());

        createController();

        boolean inService = false;
        boolean inRoaming = false;
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertFalse(mCsrController.hasMessages(MSG_QUICK_CROSS_STACK_TIMEOUT));
        assertTrue(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));
    }

    @Test
    public void testQuickNoNormalStartTimerInService() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putInt(KEY_QUICK_CROSS_STACK_REDIAL_TIMER_SEC_INT, 3);
        bundle.putInt(KEY_CROSS_STACK_REDIAL_TIMER_SEC_INT, REDIAL_TIMER_DISABLED);
        doReturn(bundle).when(mCarrierConfigManager)
                .getConfigForSubId(anyInt(), ArgumentMatchers.<String>any());

        createController();

        boolean inService = true;
        boolean inRoaming = false;
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertTrue(mCsrController.hasMessages(MSG_QUICK_CROSS_STACK_TIMEOUT));
        assertFalse(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));
    }

    @Test
    public void testDefaultSlot0ThenSlot1() throws Exception {
        createController();

        boolean inService = true;
        boolean inRoaming = false;
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertTrue(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));

        mCsrController.removeMessages(MSG_CROSS_STACK_TIMEOUT);
        assertFalse(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));

        doReturn(SLOT_1).when(mEcds).getSlotId();
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertTrue(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));
    }

    @Test
    public void testDefaultSlot0PermThenSlot1Timeout() throws Exception {
        createController();

        boolean inService = true;
        boolean inRoaming = false;
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertTrue(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));

        mCsrController.notifyCallFailure(EMERGENCY_PERM_FAILURE);
        mCsrController.stopTimer();
        assertFalse(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));

        doReturn(SLOT_1).when(mEcds).getSlotId();
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertTrue(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));

        mCsrController.sendEmptyMessage(MSG_CROSS_STACK_TIMEOUT);
        processAllMessages();

        verify(mEcds, times(0)).notifyCrossStackTimerExpired();
    }

    @Test
    public void testDefaultSlot0TempThenSlot1Timeout() throws Exception {
        createController();

        boolean inService = true;
        boolean inRoaming = false;
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertTrue(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));

        mCsrController.notifyCallFailure(EMERGENCY_TEMP_FAILURE);
        mCsrController.stopTimer();
        assertFalse(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));

        doReturn(SLOT_1).when(mEcds).getSlotId();
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertTrue(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));

        mCsrController.sendEmptyMessage(MSG_CROSS_STACK_TIMEOUT);
        processAllMessages();

        verify(mEcds).notifyCrossStackTimerExpired();
    }

    @Test
    public void testDefaultSlot0TempThenSlot1TimeoutNotEmergencyNumber() throws Exception {
        createController();

        boolean inService = true;
        boolean inRoaming = false;
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertTrue(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));

        mCsrController.notifyCallFailure(EMERGENCY_TEMP_FAILURE);
        mCsrController.stopTimer();
        assertFalse(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));

        doReturn(SLOT_1).when(mEcds).getSlotId();
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertTrue(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));

        doReturn(false).when(mEmergencyNumberHelper).isEmergencyNumber(anyInt(), anyString());
        mCsrController.sendEmptyMessage(MSG_CROSS_STACK_TIMEOUT);
        processAllMessages();

        verify(mEcds, times(0)).notifyCrossStackTimerExpired();
    }

    @Test
    public void testDefaultSlot0TempThenSlot1TimeoutPinLocked() throws Exception {
        createController();

        boolean inService = true;
        boolean inRoaming = false;
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertTrue(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));

        mCsrController.notifyCallFailure(EMERGENCY_TEMP_FAILURE);
        mCsrController.stopTimer();
        assertFalse(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));

        doReturn(SLOT_1).when(mEcds).getSlotId();
        mCsrController.startTimer(mContext, mEcds, TELECOM_CALL_ID1,
                TEST_EMERGENCY_NUMBER, inService, inRoaming, 2);

        assertTrue(mCsrController.hasMessages(MSG_CROSS_STACK_TIMEOUT));

        doReturn(TelephonyManager.SIM_STATE_PIN_REQUIRED)
                .when(mTelephonyManager).getSimState(anyInt());
        mCsrController.sendEmptyMessage(MSG_CROSS_STACK_TIMEOUT);
        processAllMessages();

        verify(mEcds, times(0)).notifyCrossStackTimerExpired();
    }

    @Test
    public void testEmergencyNumberHelper() throws Exception {
        mCsrController = new CrossSimRedialingController(mContext,
                mHandlerThread.getLooper());

        CrossSimRedialingController.EmergencyNumberHelper helper =
                mCsrController.getEmergencyNumberHelper();

        assertNotNull(helper);

        EmergencyNumber num1 = new EmergencyNumber(TEST_EMERGENCY_NUMBER, "us", "",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE, new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);

        EmergencyNumber num2 = new EmergencyNumber("119", "jp", "",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE, new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);

        Map<Integer, List<EmergencyNumber>> lists = new HashMap<>();
        List<EmergencyNumber> list = new ArrayList<>();
        list.add(num1);
        lists.put(1, list);

        list = new ArrayList<>();
        list.add(num2);
        lists.put(2, list);

        doReturn(lists).when(mTelephonyManager).getEmergencyNumberList();

        assertTrue(helper.isEmergencyNumber(1, TEST_EMERGENCY_NUMBER));
        assertFalse(helper.isEmergencyNumber(2, TEST_EMERGENCY_NUMBER));
        assertFalse(helper.isEmergencyNumber(3, TEST_EMERGENCY_NUMBER));
    }

    private void createController() throws Exception {
        mCsrController = new CrossSimRedialingController(mContext,
                mHandlerThread.getLooper(), mEmergencyNumberHelper);
    }

    private static PersistableBundle getDefaultPersistableBundle() {
        return getPersistableBundle(0, 120, false);
    }

    private static PersistableBundle getPersistableBundle(
            int quickTimer, int timer, boolean startQuickInService) {
        PersistableBundle bundle  = new PersistableBundle();
        bundle.putInt(KEY_QUICK_CROSS_STACK_REDIAL_TIMER_SEC_INT, quickTimer);
        bundle.putInt(KEY_CROSS_STACK_REDIAL_TIMER_SEC_INT, timer);
        bundle.putBoolean(KEY_START_QUICK_CROSS_STACK_REDIAL_TIMER_WHEN_REGISTERED_BOOL,
                startQuickInService);

        return bundle;
    }

    private void processAllMessages() {
        while (!mLooper.getLooper().getQueue().isIdle()) {
            mLooper.processAllMessages();
        }
    }

    private static void logd(String str) {
        Log.d(TAG, str);
    }
}
