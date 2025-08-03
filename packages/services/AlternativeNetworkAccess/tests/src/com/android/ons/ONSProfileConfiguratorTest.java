/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.ons;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.euicc.EuiccManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.UUID;

public class ONSProfileConfiguratorTest extends ONSBaseTest {
    private static final String TAG = ONSProfileConfiguratorTest.class.getName();
    private static final int TEST_SUB_ID = 1;
    @Mock SubscriptionManager mMockSubManager;
    @Mock SubscriptionInfo mMockSubscriptionInfo1;
    @Mock SubscriptionInfo mMockSubscriptionInfo2;
    @Mock EuiccManager mMockEuiccMngr;
    @Mock TelephonyManager mMockTelephonyManager;
    @Mock CarrierConfigManager mMockCarrierConfigManager;
    @Mock private Context mMockContext;
    @Mock private ONSProfileActivator mMockONSProfileActivator;
    @Mock private ONSProfileConfigurator.ONSProfConfigListener mMockConfigListener;

    @Before
    public void setUp() throws Exception {
        super.setUp("ONSTest");
        MockitoAnnotations.initMocks(this);
        Looper.prepare();
    }

    @Test
    public void testDeleteSubscription() {
        ONSProfileConfigurator mOnsProfileConfigurator = new ONSProfileConfigurator(mContext,
                mMockSubManager, mMockCarrierConfigManager, mMockEuiccMngr, mMockConfigListener);

        Intent intent = new Intent();
        intent.putExtra(
                ONSProfileConfigurator.PARAM_REQUEST_TYPE,
                ONSProfileConfigurator.REQUEST_CODE_DELETE_SUB);
        intent.putExtra(ONSProfileConfigurator.PARAM_SUB_ID, TEST_SUB_ID);
        Message msg = new Message();
        msg.obj = intent;
        msg.arg1 = EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK;
        mOnsProfileConfigurator.callbackMsgHandler(msg);

        verify(mMockConfigListener).onOppSubscriptionDeleted(TEST_SUB_ID);
    }

    @Test
    public void testGroupSubscriptionAndSetOpportunistic() {
        doReturn(TEST_SUB_ID).when(mMockSubscriptionInfo1).getSubscriptionId();

        ONSProfileConfigurator mOnsProfileConfigurator = new ONSProfileConfigurator(mContext,
                mMockSubManager, mMockCarrierConfigManager, mMockEuiccMngr, mMockConfigListener);

        ParcelUuid parcelUuid = new ParcelUuid(new UUID(1, 2));
        mOnsProfileConfigurator.groupWithPSIMAndSetOpportunistic(
                mMockSubscriptionInfo1, parcelUuid);

        ArrayList<Integer> subList = new ArrayList<>();
        subList.add(TEST_SUB_ID);
        verify(mMockSubManager).addSubscriptionsIntoGroup(subList, parcelUuid);
    }

    @Test
    public void testAlreadyGroupedSubscriptions() {
        doReturn(TEST_SUB_ID).when(mMockSubscriptionInfo1).getSubscriptionId();
        doReturn(true).when(mMockSubscriptionInfo1).isOpportunistic();

        ONSProfileConfigurator mOnsProfileConfigurator = new ONSProfileConfigurator(mContext,
                mMockSubManager, mMockCarrierConfigManager, mMockEuiccMngr, mMockConfigListener);

        ParcelUuid uuid = new ParcelUuid(new UUID(1, 2));
        doReturn(uuid).when(mMockSubscriptionInfo1).getGroupUuid();

        mOnsProfileConfigurator.groupWithPSIMAndSetOpportunistic(mMockSubscriptionInfo1, uuid);

        verifyNoMoreInteractions(mMockSubManager);
    }

    @Test
    public void testActivateSubscription() {
        ONSProfileConfigurator mOnsProfileConfigurator = new ONSProfileConfigurator(mContext,
                mMockSubManager, mMockCarrierConfigManager, mMockEuiccMngr, mMockConfigListener);

        Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
        intent.setAction(ONSProfileConfigurator.ACTION_ONS_ESIM_CONFIG);
        intent.putExtra(
                ONSProfileConfigurator.PARAM_REQUEST_TYPE,
                ONSProfileConfigurator.REQUEST_CODE_ACTIVATE_SUB);
        intent.putExtra(ONSProfileConfigurator.PARAM_SUB_ID, TEST_SUB_ID);
        PendingIntent callbackIntent =
                PendingIntent.getBroadcast(
                        mContext,
                        ONSProfileConfigurator.REQUEST_CODE_ACTIVATE_SUB,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE);

        mOnsProfileConfigurator.activateSubscription(TEST_SUB_ID);
        verify(mMockEuiccMngr).switchToSubscription(TEST_SUB_ID, callbackIntent);
    }

    @Test
    public void testdeleteInactiveOpportunisticSubscriptionsWithNoneSavedOppSubs() {
        ONSProfileConfigurator mOnsProfileConfigurator = new ONSProfileConfigurator(mContext,
                mMockSubManager, mMockCarrierConfigManager, mMockEuiccMngr, mMockConfigListener);

        PersistableBundle persistableBundle = new PersistableBundle();
        persistableBundle.putIntArray(
                CarrierConfigManager.KEY_OPPORTUNISTIC_CARRIER_IDS_INT_ARRAY, null);
        doReturn(persistableBundle).when(mMockCarrierConfigManager).getConfigForSubId(TEST_SUB_ID);

        boolean res = mOnsProfileConfigurator.deleteInactiveOpportunisticSubscriptions(TEST_SUB_ID);
        // verify(mOnsProfileConfigurator).deleteOldOpportunisticESimsOfPSIMOperator(TEST_SUB_ID);
        assertEquals(res, false);
    }

    @Test
    public void testdeleteInactiveOpportunisticSubscriptionsWithSavedOppSubs() {
        ONSProfileConfigurator mOnsProfileConfigurator = new ONSProfileConfigurator(mContext,
                mMockSubManager, mMockCarrierConfigManager, mMockEuiccMngr, mMockConfigListener);

        doReturn(1).when(mMockSubscriptionInfo1).getSubscriptionId();
        doReturn(true).when(mMockSubManager).isActiveSubscriptionId(1);

        doReturn(2).when(mMockSubscriptionInfo2).getSubscriptionId();
        doReturn(false).when(mMockSubManager).isActiveSubscriptionId(2);

        ArrayList<SubscriptionInfo> oppSubList = new ArrayList<>();
        oppSubList.add(mMockSubscriptionInfo1);
        oppSubList.add(mMockSubscriptionInfo2);
        doReturn(oppSubList).when(mMockSubManager).getOpportunisticSubscriptions();

        Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
        intent.setAction(ONSProfileConfigurator.ACTION_ONS_ESIM_CONFIG);
        intent.putExtra(
                ONSProfileConfigurator.PARAM_REQUEST_TYPE,
                ONSProfileConfigurator.REQUEST_CODE_DELETE_SUB);
        intent.putExtra(ONSProfileConfigurator.PARAM_SUB_ID, 2);
        PendingIntent callbackIntent2 =
                PendingIntent.getBroadcast(
                        mContext,
                        ONSProfileConfigurator.REQUEST_CODE_DELETE_SUB,
                        intent,
                        PendingIntent.FLAG_MUTABLE);

        boolean res = mOnsProfileConfigurator.deleteInactiveOpportunisticSubscriptions(2);
        verify(mMockEuiccMngr).deleteSubscription(2, callbackIntent2);
        verifyNoMoreInteractions(mMockEuiccMngr);
        //verify(mMockEuiccManager).deleteSubscription(2, callbackIntent2);
        // verify(mOnsProfileConfigurator).deleteOldOpportunisticESimsOfPSIMOperator(TEST_SUB_ID);
        assertEquals(res, true);
    }

    @Test
    public void testFindOpportunisticSubscription() {
        ONSProfileConfigurator mOnsProfileConfigurator = new ONSProfileConfigurator(mContext,
                mMockSubManager, mMockCarrierConfigManager, mMockEuiccMngr, mMockConfigListener);

        int[] oppCarrierList = {2};
        PersistableBundle persistableBundle = new PersistableBundle();
        persistableBundle.putIntArray(
                CarrierConfigManager.KEY_OPPORTUNISTIC_CARRIER_IDS_INT_ARRAY, oppCarrierList);
        doReturn(persistableBundle).when(mMockCarrierConfigManager).getConfigForSubId(TEST_SUB_ID);

        ArrayList<SubscriptionInfo> oppSubList = new ArrayList<>();
        oppSubList.add(mMockSubscriptionInfo1);
        oppSubList.add(mMockSubscriptionInfo2);
        doReturn(oppSubList).when(mMockSubManager).getAvailableSubscriptionInfoList();

        ParcelUuid groupUUID = new ParcelUuid(new UUID(0, 100));
        doReturn(groupUUID).when(mMockSubscriptionInfo1).getGroupUuid();
        doReturn(true).when(mMockSubscriptionInfo1).isEmbedded();
        doReturn(1).when(mMockSubscriptionInfo1).getCarrierId();
        doReturn(mMockSubscriptionInfo1).when(mMockSubManager)
                .getActiveSubscriptionInfo(TEST_SUB_ID);
        doReturn(TEST_SUB_ID).when(mMockSubscriptionInfo1).getSubscriptionId();

        doReturn(null).when(mMockSubscriptionInfo2).getGroupUuid();
        doReturn(true).when(mMockSubscriptionInfo2).isEmbedded();
        doReturn(2).when(mMockSubscriptionInfo2).getCarrierId();
        doReturn(mMockSubscriptionInfo2).when(mMockSubManager).getActiveSubscriptionInfo(2);
        doReturn(2).when(mMockSubscriptionInfo2).getSubscriptionId();

        SubscriptionInfo oppSubscription = mOnsProfileConfigurator
                .findOpportunisticSubscription(TEST_SUB_ID);
        assertEquals(oppSubscription, mMockSubscriptionInfo2);

        doReturn(groupUUID).when(mMockSubscriptionInfo2).getGroupUuid();
        assertEquals(oppSubscription, mMockSubscriptionInfo2);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }
}
