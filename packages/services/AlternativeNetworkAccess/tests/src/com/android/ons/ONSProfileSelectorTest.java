/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.ons;

import static org.mockito.Mockito.*;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Looper;
import android.os.ServiceManager;
import android.telephony.AvailableNetworkInfo;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccCardInfo;
import android.telephony.UiccPortInfo;
import android.telephony.euicc.EuiccManager;
import android.util.Log;

import com.android.internal.telephony.ISub;
import com.android.internal.telephony.IUpdateAvailableNetworksCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ONSProfileSelectorTest extends ONSBaseTest {

    private MyONSProfileSelector mONSProfileSelector;
    private boolean testFailed;
    private boolean mCallbackInvoked;
    private int mDataSubId;
    private int mResult;
    @Mock
    EuiccManager mMockEuiccManager;
    @Mock
    ONSNetworkScanCtlr mONSNetworkScanCtlr;
    @Mock
    TelephonyManager mSubscriptionBoundTelephonyManager;
    @Mock
    ISub mISubMock;
    @Mock
    IBinder mISubBinderMock;
    @Mock
    SubscriptionInfo mSubInfo;
    private Looper mLooper;
    private static final String TAG = "ONSProfileSelectorTest";

    MyONSProfileSelector.ONSProfileSelectionCallback mONSProfileSelectionCallback =
        new MyONSProfileSelector.ONSProfileSelectionCallback() {
            public void onProfileSelectionDone() {
                mCallbackInvoked = true;
                setReady(true);
            }
        };

    public class MyONSProfileSelector extends ONSProfileSelector {

        public SubscriptionManager.OnOpportunisticSubscriptionsChangedListener mProfileChngLstnrCpy;
        public BroadcastReceiver mProfileSelectorBroadcastReceiverCpy;
        public ONSNetworkScanCtlr.NetworkAvailableCallBack mNetworkAvailableCallBackCpy;

        public MyONSProfileSelector(Context c,
            MyONSProfileSelector.ONSProfileSelectionCallback aNSProfileSelectionCallback) {
            super(c, aNSProfileSelectionCallback);
        }

        public void triggerProfileUpdate() {
            mHandler.sendEmptyMessage(1);
        }

        public void updateOppSubs() {
            updateOpportunisticSubscriptions();
        }

        public int getCurrentPreferredData() {
            return mCurrentDataSubId;
        }

        public void setCurrentPreferredData(int subId) {
            mCurrentDataSubId = subId;
        }

        protected void init(Context c,
            MyONSProfileSelector.ONSProfileSelectionCallback aNSProfileSelectionCallback) {
            super.init(c, aNSProfileSelectionCallback);
            this.mSubscriptionManager = ONSProfileSelectorTest.this.mSubscriptionManager;
            this.mSubscriptionBoundTelephonyManager =
                ONSProfileSelectorTest.this.mSubscriptionBoundTelephonyManager;
            mProfileChngLstnrCpy = mProfileChangeListener;
            mProfileSelectorBroadcastReceiverCpy = null;
            mNetworkAvailableCallBackCpy = mNetworkAvailableCallBack;
            mNetworkScanCtlr = mONSNetworkScanCtlr;
        }
    }

    private void addISubService() throws Exception {
        Field field = ServiceManager.class.getDeclaredField("sCache");
        field.setAccessible(true);
        ((Map<String, IBinder>)field.get(null)).put("isub", mISubBinderMock);
        doReturn(mISubMock).when(mISubBinderMock).queryLocalInterface(any());
    }

    private void removeISubService() throws Exception {
        Field field = ServiceManager.class.getDeclaredField("sCache");
        field.setAccessible(true);
        ((Map<String, IBinder>)field.get(null)).remove("isub");
    }

    @Before
    public void setUp() throws Exception {
        super.setUp("ONSTest");
        mLooper = null;
        MockitoAnnotations.initMocks(this);
        addISubService();
    }

    @After
    public void tearDown() throws Exception {
        removeISubService();
        super.tearDown();
        if (mLooper != null) {
            mLooper.quit();
            mLooper.getThread().join();
        }
    }


    @Test
    public void testStartProfileSelectionWithNoOpportunisticSub() {
        List<CellInfo> results2 = new ArrayList<CellInfo>();
        CellIdentityLte cellIdentityLte = new CellIdentityLte(310, 210, 1, 1, 1);
        CellInfoLte cellInfoLte = new CellInfoLte();
        cellInfoLte.setCellIdentity(cellIdentityLte);
        results2.add((CellInfo) cellInfoLte);
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
            new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);

        UiccPortInfo uiccPortInfo = new UiccPortInfo("", 1, 1, false);
        ArrayList<UiccPortInfo> uiccPortInfoList = new ArrayList<>();
        uiccPortInfoList.add(uiccPortInfo);

        UiccCardInfo uiccCardInfo = new UiccCardInfo(true, 1, "", 0, false, true, uiccPortInfoList);
        ArrayList<UiccCardInfo> uiccCardInfoList = new ArrayList<>();
        uiccCardInfoList.add(uiccCardInfo);

        doReturn(uiccCardInfoList).when(mMockTelephonyManager).getUiccCardsInfo();
        doReturn(mMockEuiccManager).when(mMockEuiccManager).createForCardId(1);
        doReturn(true).when(mMockEuiccManager).isSimPortAvailable(1);

        IUpdateAvailableNetworksCallback mCallback = new IUpdateAvailableNetworksCallback.Stub() {
            @Override
            public void onComplete(int result) {
                Log.d(TAG, "mResult end:" + result);
                mResult = result;
            }
        };

        mResult = -1;
        mReady = false;
        mCallbackInvoked = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                doReturn(true).when(mONSNetworkScanCtlr).startFastNetworkScan(anyObject());
                doReturn(new ArrayList<>()).when(mSubscriptionManager)
                    .getOpportunisticSubscriptions();
                mONSProfileSelector = new MyONSProfileSelector(mContext,
                    mONSProfileSelectionCallback);
                mONSProfileSelector.mTelephonyManager = mMockTelephonyManager;
                mONSProfileSelector.mEuiccManager = mMockEuiccManager;
                mONSProfileSelector.updateOppSubs();
                mONSProfileSelector.startProfileSelection(availableNetworkInfos, mCallback);
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();
        mReady = false;

        // Testing startProfileSelection without any oppotunistic data.
        // should not get any callback invocation.
        waitUntilReady(100);
        assertEquals(
                TelephonyManager.UPDATE_AVAILABLE_NETWORKS_NO_OPPORTUNISTIC_SUB_AVAILABLE, mResult);
        assertFalse(mCallbackInvoked);
    }

    @Test
    public void testStartProfileSelectionSuccess() {
        int subId = 5;
        List<SubscriptionInfo> activeSubscriptionInfoList = new ArrayList<SubscriptionInfo>();
        List<SubscriptionInfo> oppSubscriptionInfoList = new ArrayList<SubscriptionInfo>();
        SubscriptionInfo subscriptionInfo1 = new SubscriptionInfo(subId, "", 1, "TMO", "TMO", 1, 1,
                "123", 1, null, "310", "210", "", true, null, "1", 1, true, null, false, 1, 1, 1,
                null, null, false, 0);
        SubscriptionInfo subscriptionInfo2 = new SubscriptionInfo(6, "", 1, "TMO", "TMO", 1, 1,
                "123", 1, null, "310", "211", "", true, null, "1", 1, false, null, false, 1, 1, 1,
                null, null, false, 0);
        oppSubscriptionInfoList.add(subscriptionInfo1);
        activeSubscriptionInfoList.add(subscriptionInfo1);
        activeSubscriptionInfoList.add(subscriptionInfo2);

        List<CellInfo> results2 = new ArrayList<CellInfo>();
        CellIdentityLte cellIdentityLte = new CellIdentityLte(310, 210, 1, 1, 1);
        CellInfoLte cellInfoLte = new CellInfoLte();
        cellInfoLte.setCellIdentity(cellIdentityLte);
        results2.add((CellInfo) cellInfoLte);
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(subId, 1, mccMncs,
            new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);

        IUpdateAvailableNetworksCallback mCallback = new IUpdateAvailableNetworksCallback.Stub() {
            @Override
            public void onComplete(int result) {
                mResult = result;
            }
        };

        mResult = -1;
        mReady = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                doReturn(subscriptionInfo1).when(mSubscriptionManager)
                        .getActiveSubscriptionInfo(subId);
                doReturn(oppSubscriptionInfoList).when(mSubscriptionManager)
                    .getOpportunisticSubscriptions();
                doReturn(activeSubscriptionInfoList).when(mSubscriptionManager)
                        .getActiveSubscriptionInfoList();
                doReturn(activeSubscriptionInfoList).when(mSubscriptionManager)
                        .getCompleteActiveSubscriptionInfoList();
                doReturn(true).when(mSubscriptionManager).isActiveSubId(subId);
                doReturn(true).when(mSubscriptionBoundTelephonyManager).enableModemForSlot(
                    anyInt(), anyBoolean());
                mONSProfileSelector = new MyONSProfileSelector(mContext,
                    new MyONSProfileSelector.ONSProfileSelectionCallback() {
                        public void onProfileSelectionDone() {
                            setReady(true);
                        }
                    });
                mONSProfileSelector.updateOppSubs();
                mONSProfileSelector.startProfileSelection(availableNetworkInfos, mCallback);
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();
        mReady = false;
        mDataSubId = -1;

        // Testing startProfileSelection with oppotunistic sub.
        // On success onProfileSelectionDone must get invoked.
        assertFalse(mReady);
        waitForMs(500);
        mONSProfileSelector.mNetworkAvailableCallBackCpy.onNetworkAvailability(results2);
        Intent callbackIntent = new Intent(MyONSProfileSelector.ACTION_SUB_SWITCH);
        callbackIntent.putExtra("sequenceId", 1);
        callbackIntent.putExtra("subId", subId);
        waitUntilReady();
        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS, mResult);
        assertTrue(mReady);
    }

    @Test
    public void testStartProfileSelectionWithDifferentPrioritySubInfo() {
        int PRIORITY_HIGH = 1;
        int PRIORITY_MED = 2;

        List<SubscriptionInfo> subscriptionInfoList = new ArrayList<SubscriptionInfo>();
        SubscriptionInfo subscriptionInfo = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
                "123", 1, null, "310", "210", "", true, null, "1", 1, true, null, false, 1, 1, 1,
                null, null, false, 0);
        subscriptionInfoList.add(subscriptionInfo);
        SubscriptionInfo subscriptionInfo_2 = new SubscriptionInfo(8, "", 1, "Vzw", "Vzw", 1, 1,
                "456", 1, null, "311", "480", "", true, null, "1", 1, true, null, false, 1, 1, 1,
                null, null, false, 1);
        subscriptionInfoList.add(subscriptionInfo_2);
        doReturn(subscriptionInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(5);
        doReturn(subscriptionInfo_2).when(mSubscriptionManager).getActiveSubscriptionInfo(8);
        doReturn(subscriptionInfoList).when(mSubscriptionManager)
                .getCompleteActiveSubscriptionInfoList();
        doReturn(subscriptionInfoList).when(mSubscriptionManager).getOpportunisticSubscriptions();

        List<CellInfo> results2 = new ArrayList<CellInfo>();
        CellIdentityLte cellIdentityLte = new CellIdentityLte(310, 210, 1, 1, 1);
        CellInfoLte cellInfoLte = new CellInfoLte();
        cellInfoLte.setCellIdentity(cellIdentityLte);
        results2.add((CellInfo) cellInfoLte);
        CellIdentityLte cellIdentityLte_2 = new CellIdentityLte(311, 480, 1, 1, 1);
        CellInfoLte cellInfoLte_2 = new CellInfoLte();
        cellInfoLte_2.setCellIdentity(cellIdentityLte_2);
        results2.add((CellInfo) cellInfoLte_2);

        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(5, PRIORITY_MED,
                mccMncs, new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<>();
        availableNetworkInfos.add(availableNetworkInfo);
        ArrayList<String> mccMncs_2 = new ArrayList<>();
        mccMncs_2.add("311480");
        AvailableNetworkInfo availableNetworkInfo_2 = new AvailableNetworkInfo(8, PRIORITY_HIGH,
                mccMncs_2, new ArrayList<Integer>());
        availableNetworkInfos.add(availableNetworkInfo_2);

        IUpdateAvailableNetworksCallback mCallback = new IUpdateAvailableNetworksCallback.Stub() {
            @Override
            public void onComplete(int result) {
                mResult = result;
            }
        };

        mResult = -1;
        mReady = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                doReturn(true).when(mSubscriptionManager).isActiveSubId(anyInt());
                doReturn(true).when(mSubscriptionBoundTelephonyManager).enableModemForSlot(
                        anyInt(), anyBoolean());
                mONSProfileSelector = new MyONSProfileSelector(mContext,
                        new MyONSProfileSelector.ONSProfileSelectionCallback() {
                            public void onProfileSelectionDone() {
                                setReady(true);
                            }
                        });
                mONSProfileSelector.updateOppSubs();
                mONSProfileSelector.startProfileSelection(availableNetworkInfos, mCallback);
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();
        waitUntilReady();
        waitForMs(500);
        // get high priority subId
        int retrieveSubId = mONSProfileSelector.retrieveBestSubscription(results2);
        mONSProfileSelector.mNetworkAvailableCallBackCpy.onNetworkAvailability(results2);
        assertEquals(8, retrieveSubId);
        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS, mResult);
    }

    @Test
    public void testStartProfileSelectionWithActivePrimarySimOnESim() {
        List<SubscriptionInfo> opportunisticSubscriptionInfoList = new ArrayList<SubscriptionInfo>();
        List<SubscriptionInfo> activeSubscriptionInfoList = new ArrayList<SubscriptionInfo>();
        SubscriptionInfo subscriptionInfo1 = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
                "123", 1, null, "310", "210", "", true, null, "1", 1, true, null, false, 1839, 1,
                1, null, null, false, 1);
        SubscriptionInfo subscriptionInfo2 = new SubscriptionInfo(6, "", 1, "TMO", "TMO", 1, 1,
                "456", 1, null, "310", "211", "", true, null, "1", 1, false, null, false, 1839, 1,
                1, null, null, false, 2);

        activeSubscriptionInfoList.add(subscriptionInfo1);
        activeSubscriptionInfoList.add(subscriptionInfo2);
        doReturn(subscriptionInfo1).when(mSubscriptionManager).getActiveSubscriptionInfo(5);
        doReturn(subscriptionInfo2).when(mSubscriptionManager).getActiveSubscriptionInfo(6);

        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(5, 2, mccMncs,
            new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);

        ArrayList<UiccPortInfo> uiccPortInfoList = new ArrayList<>();
        uiccPortInfoList.add(new UiccPortInfo("1", 0, 0, false));
        uiccPortInfoList.add(new UiccPortInfo("2", 1, 1, true));

        UiccCardInfo uiccCardInfo = new UiccCardInfo(
                true, 1, "1", 0, false, true, uiccPortInfoList);
        ArrayList<UiccCardInfo> uiccCardInfoList = new ArrayList<>();
        uiccCardInfoList.add(uiccCardInfo);

        doReturn(uiccCardInfoList).when(mMockTelephonyManager).getUiccCardsInfo();
        doReturn(mMockEuiccManager).when(mMockEuiccManager).createForCardId(1);
        doReturn(true).when(mMockEuiccManager).isSimPortAvailable(1);

        IUpdateAvailableNetworksCallback mCallback = new IUpdateAvailableNetworksCallback.Stub() {
            @Override
            public void onComplete(int result) {
                mResult = result;
            }
        };

        mResult = -1;
        mReady = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                doReturn(opportunisticSubscriptionInfoList).when(mSubscriptionManager)
                    .getOpportunisticSubscriptions();
                doReturn(false).when(mSubscriptionManager).isActiveSubId(anyInt());
                doReturn(activeSubscriptionInfoList).when(mSubscriptionManager)
                    .getActiveSubscriptionInfoList(anyBoolean());
                doReturn(activeSubscriptionInfoList).when(mSubscriptionManager)
                        .getActiveSubscriptionInfoList();
                doReturn(true).when(mSubscriptionBoundTelephonyManager).enableModemForSlot(
                        anyInt(), anyBoolean());
                mONSProfileSelector = new MyONSProfileSelector(mContext,
                    new MyONSProfileSelector.ONSProfileSelectionCallback() {
                        public void onProfileSelectionDone() {
                            setReady(true);
                        }
                    });
                mONSProfileSelector.mTelephonyManager = mMockTelephonyManager;
                mONSProfileSelector.mEuiccManager = mMockEuiccManager;
                mONSProfileSelector.updateOppSubs();
                mONSProfileSelector.startProfileSelection(availableNetworkInfos, mCallback);
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();
        mReady = false;
        mDataSubId = -1;

        // Testing startProfileSelection with opportunistic sub.
        // On success onProfileSelectionDone must get invoked.
        assertFalse(mReady);
        waitForMs(100);
        Intent callbackIntent = new Intent(MyONSProfileSelector.ACTION_SUB_SWITCH);
        callbackIntent.putExtra("sequenceId", 1);
        callbackIntent.putExtra("subId", 5);
        waitUntilReady();
        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_NO_OPPORTUNISTIC_SUB_AVAILABLE,
                mResult);
    }

    public static void waitForMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Log.d(TAG, "InterruptedException while waiting: " + e);
        }
    }

    @Test
    public void testselectProfileForDataWithNoOpportunsticSub() {
        mReady = false;
        doReturn(new ArrayList<>()).when(mSubscriptionManager).getOpportunisticSubscriptions();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mONSProfileSelector = new MyONSProfileSelector(mContext,
                    new MyONSProfileSelector.ONSProfileSelectionCallback() {
                        public void onProfileSelectionDone() {
                        }
                    });
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();

        // Testing selectProfileForData with no oppotunistic sub and the function should
        // return false.
        mONSProfileSelector.selectProfileForData(1, false, null);
    }

    @Test
    public void testselectProfileForDataWithInActiveSub() {
        List<SubscriptionInfo> subscriptionInfoList = new ArrayList<SubscriptionInfo>();
        SubscriptionInfo subscriptionInfo = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
            "123", 1, null, "310", "210", "", false, null, "1");
        subscriptionInfoList.add(subscriptionInfo);
        doReturn(subscriptionInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(5);
        mReady = false;
        doReturn(new ArrayList<>()).when(mSubscriptionManager).getOpportunisticSubscriptions();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mONSProfileSelector = new MyONSProfileSelector(mContext,
                    new MyONSProfileSelector.ONSProfileSelectionCallback() {
                        public void onProfileSelectionDone() {
                        }
                    });
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();

        // Testing selectProfileForData with in active sub and the function should return false.
        mONSProfileSelector.selectProfileForData(5, false, null);
    }

    @Test
    public void testselectProfileForDataWithInvalidSubId() {
        List<SubscriptionInfo> subscriptionInfoList = new ArrayList<SubscriptionInfo>();
        SubscriptionInfo subscriptionInfo = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
            "123", 1, null, "310", "210", "", false, null, "1");
        subscriptionInfoList.add(subscriptionInfo);
        doReturn(subscriptionInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(5);
        mReady = false;
        doReturn(subscriptionInfoList).when(mSubscriptionManager).getOpportunisticSubscriptions();
        doNothing().when(mSubscriptionManager).setPreferredDataSubscriptionId(
            anyInt(), anyBoolean(), any(), any());
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mONSProfileSelector = new MyONSProfileSelector(mContext,
                    new MyONSProfileSelector.ONSProfileSelectionCallback() {
                        public void onProfileSelectionDone() {
                        }
                    });
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();

        // Testing selectProfileForData with INVALID_SUBSCRIPTION_ID and the function should
        // return true.
        mONSProfileSelector.selectProfileForData(
            SubscriptionManager.INVALID_SUBSCRIPTION_ID, false, null);
    }

    @Test
    public void testselectProfileForDataWithValidSub() {
        List<SubscriptionInfo> subscriptionInfoList = new ArrayList<SubscriptionInfo>();
        SubscriptionInfo subscriptionInfo = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
            "123", 1, null, "310", "210", "", false, null, "1");
        subscriptionInfoList.add(subscriptionInfo);
        doReturn(subscriptionInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(5);
        mReady = false;
        doReturn(subscriptionInfoList).when(mSubscriptionManager)
            .getActiveSubscriptionInfoList();
        doNothing().when(mSubscriptionManager).setPreferredDataSubscriptionId(
            anyInt(), anyBoolean(), any(), any());
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                doReturn(subscriptionInfoList).when(mSubscriptionManager)
                    .getOpportunisticSubscriptions();
                mONSProfileSelector = new MyONSProfileSelector(mContext,
                    new MyONSProfileSelector.ONSProfileSelectionCallback() {
                        public void onProfileSelectionDone() {
                        }
                    });
                mONSProfileSelector.updateOppSubs();
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();

        // Testing selectProfileForData with valid opportunistic sub and the function should
        // return true.
        mONSProfileSelector.selectProfileForData(5, false, null);
    }

    @Test
    public void testStartProfileSelectionSuccessWithSameArgumentsAgain() {
        List<SubscriptionInfo> activeSubscriptionInfoList = new ArrayList<SubscriptionInfo>();
        List<SubscriptionInfo> oppSubscriptionInfoList = new ArrayList<SubscriptionInfo>();
        SubscriptionInfo subscriptionInfo1 = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
                "123", 1, null, "310", "210", "", true, null, "1", 1, true, null, false, 1, 1, 1,
                null, null, false, 0);
        SubscriptionInfo subscriptionInfo2 = new SubscriptionInfo(6, "", 1, "TMO", "TMO", 1, 1,
                "123", 1, null, "310", "211", "", true, null, "1", 1, false, null, false, 1, 1, 1,
                null, null, false, 0);

        oppSubscriptionInfoList.add(subscriptionInfo1);
        doReturn(subscriptionInfo1).when(mSubscriptionManager).getActiveSubscriptionInfo(5);
        doReturn(subscriptionInfo2).when(mSubscriptionManager).getActiveSubscriptionInfo(6);

        activeSubscriptionInfoList.add(subscriptionInfo1);
        activeSubscriptionInfoList.add(subscriptionInfo2);
        doReturn(activeSubscriptionInfoList).when(mSubscriptionManager)
                .getCompleteActiveSubscriptionInfoList();

        List<CellInfo> results2 = new ArrayList<CellInfo>();
        CellIdentityLte cellIdentityLte = new CellIdentityLte(310, 210, 1, 1, 1);
        CellInfoLte cellInfoLte = new CellInfoLte();
        cellInfoLte.setCellIdentity(cellIdentityLte);
        results2.add((CellInfo) cellInfoLte);
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(5, 1, mccMncs,
            new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);

        IUpdateAvailableNetworksCallback mCallback = new IUpdateAvailableNetworksCallback.Stub() {
            @Override
            public void onComplete(int result) {
                mResult = result;
            }
        };

        mResult = -1;
        mReady = false;
        mONSProfileSelector = new MyONSProfileSelector(mContext,
            new MyONSProfileSelector.ONSProfileSelectionCallback() {
                public void onProfileSelectionDone() {
                    setReady(true);
                }
            });
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                doReturn(oppSubscriptionInfoList).when(mSubscriptionManager)
                    .getOpportunisticSubscriptions();
                doReturn(true).when(mSubscriptionManager).isActiveSubId(anyInt());
                doReturn(true).when(mSubscriptionBoundTelephonyManager).enableModemForSlot(
                    anyInt(), anyBoolean());

                mONSProfileSelector.updateOppSubs();
                mONSProfileSelector.startProfileSelection(availableNetworkInfos, mCallback);
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();
        mReady = false;
        mDataSubId = -1;

        // Testing startProfileSelection with oppotunistic sub.
        // On success onProfileSelectionDone must get invoked.
        assertFalse(mReady);
        waitForMs(500);
        mONSProfileSelector.mNetworkAvailableCallBackCpy.onNetworkAvailability(results2);
        Intent callbackIntent = new Intent(MyONSProfileSelector.ACTION_SUB_SWITCH);
        callbackIntent.putExtra("sequenceId", 1);
        callbackIntent.putExtra("subId", 5);
        waitUntilReady();
        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS, mResult);
        assertTrue(mReady);

        mResult = -1;
        mReady = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                doReturn(oppSubscriptionInfoList).when(mSubscriptionManager)
                    .getOpportunisticSubscriptions();
                doReturn(true).when(mSubscriptionManager).isActiveSubId(anyInt());
                doReturn(true).when(mSubscriptionBoundTelephonyManager).enableModemForSlot(
                    anyInt(), anyBoolean());
                mONSProfileSelector.updateOppSubs();
                mONSProfileSelector.startProfileSelection(availableNetworkInfos, mCallback);
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();
        mReady = false;
        mDataSubId = -1;

        // Testing startProfileSelection with oppotunistic sub.
        // On success onProfileSelectionDone must get invoked.
        assertFalse(mReady);
        waitForMs(500);
        mONSProfileSelector.mNetworkAvailableCallBackCpy.onNetworkAvailability(results2);
        waitUntilReady();
        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS, mResult);
        assertTrue(mReady);
    }

    @Test
    public void testStopProfileSelectionWithPreferredDataOnSame() {
        List<SubscriptionInfo> subscriptionInfoList = new ArrayList<SubscriptionInfo>();
        SubscriptionInfo subscriptionInfo = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
                "123", 1, null, "310", "210", "", true, null, "1", true, null, 0, 0);
        subscriptionInfoList.add(subscriptionInfo);
        doReturn(subscriptionInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(5);

        IUpdateAvailableNetworksCallback mCallback = new IUpdateAvailableNetworksCallback.Stub() {
            @Override
            public void onComplete(int result) {
                mResult = result;
            }
        };

        mResult = -1;
        mReady = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                doReturn(subscriptionInfoList).when(mSubscriptionManager)
                        .getOpportunisticSubscriptions();
                doReturn(true).when(mSubscriptionManager).isActiveSubId(anyInt());
                doReturn(true).when(mSubscriptionBoundTelephonyManager).enableModemForSlot(
                        anyInt(), anyBoolean());
                doReturn(5).when(mSubscriptionManager).getPreferredDataSubscriptionId();
                doReturn(subscriptionInfoList).when(mSubscriptionManager)
                        .getActiveSubscriptionInfoList(anyBoolean());

                mONSProfileSelector = new MyONSProfileSelector(mContext,
                        new MyONSProfileSelector.ONSProfileSelectionCallback() {
                            public void onProfileSelectionDone() {
                                setReady(true);
                            }
                        });
                mONSProfileSelector.updateOppSubs();
                mONSProfileSelector.setCurrentPreferredData(5);
                mONSProfileSelector.stopProfileSelection(null);
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();
        waitUntilReady();
        waitForMs(500);
        assertEquals(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, mONSProfileSelector.getCurrentPreferredData());
    }

    @Test
    public void testStopProfileSelectionWithPreferredDataOnDifferent() {
        List<SubscriptionInfo> subscriptionInfoList = new ArrayList<SubscriptionInfo>();
        SubscriptionInfo subscriptionInfo = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
                "123", 1, null, "310", "210", "", true, null, "1", true, null, 0, 0);
        subscriptionInfoList.add(subscriptionInfo);
        doReturn(subscriptionInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(5);

        IUpdateAvailableNetworksCallback mCallback = new IUpdateAvailableNetworksCallback.Stub() {
            @Override
            public void onComplete(int result) {
                mResult = result;
            }
        };

        mResult = -1;
        mReady = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                doReturn(subscriptionInfoList).when(mSubscriptionManager)
                        .getOpportunisticSubscriptions();
                doReturn(true).when(mSubscriptionManager).isActiveSubId(anyInt());
                doReturn(true).when(mSubscriptionBoundTelephonyManager).enableModemForSlot(
                        anyInt(), anyBoolean());
                doReturn(4).when(mSubscriptionManager).getPreferredDataSubscriptionId();
                doReturn(subscriptionInfoList).when(mSubscriptionManager)
                        .getActiveSubscriptionInfoList(anyBoolean());

                mONSProfileSelector = new MyONSProfileSelector(mContext,
                        new MyONSProfileSelector.ONSProfileSelectionCallback() {
                            public void onProfileSelectionDone() {
                                setReady(true);
                            }
                        });
                mONSProfileSelector.updateOppSubs();
                mONSProfileSelector.setCurrentPreferredData(5);
                mONSProfileSelector.stopProfileSelection(null);
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();
        waitUntilReady();
        waitForMs(500);
        assertEquals(5, mONSProfileSelector.getCurrentPreferredData());
    }

    @Test
    public void testAvailablePortWhenTwoPrimarySIMsAreActive() {
        /**
         * 2 - Primary active subscriptions and
         * 1 - Inactive opportunistic subscription
         */

        List<SubscriptionInfo> activeSubscriptionInfoList = new ArrayList<SubscriptionInfo>();
        List<SubscriptionInfo> opportunisticInfoList = new ArrayList<SubscriptionInfo>();

        SubscriptionInfo oppSubInfo = new SubscriptionInfo(4, "", -1, "TMO", "TMO", 1, 1,
                "001", 1, null, "110", "210", "", true, null, "1", 1, true, null, false, 2839, 1,
                1, null, null, false, TelephonyManager.INVALID_PORT_INDEX);

        SubscriptionInfo primarySubInfo1 = new SubscriptionInfo(5, "", 0, "TMO", "TMO", 1, 1,
                "123", 1, null, "310", "210", "", true, null, "1", 1, false, null, false, 1839, 1,
                1, null, null, false, 0);
        SubscriptionInfo primarySubInfo2 = new SubscriptionInfo(6, "", 0, "TMO", "TMO", 1, 1,
                "456", 1, null, "310", "211", "", true, null, "1", 1, false, null, false, 1839, 1,
                1, null, null, false, 1);

        activeSubscriptionInfoList.add(primarySubInfo1);
        activeSubscriptionInfoList.add(primarySubInfo2);
        opportunisticInfoList.add(oppSubInfo);

        doReturn(opportunisticInfoList).when(mSubscriptionManager).getOpportunisticSubscriptions();
        doReturn(activeSubscriptionInfoList).when(mSubscriptionManager)
                .getCompleteActiveSubscriptionInfoList();

        UiccPortInfo uiccPortInfo1 = new UiccPortInfo("", 0, 0, true);
        UiccPortInfo uiccPortInfo2 = new UiccPortInfo("", 1, 0, true);
        ArrayList<UiccPortInfo> uiccPortInfoList = new ArrayList<>();
        uiccPortInfoList.add(uiccPortInfo1);
        uiccPortInfoList.add(uiccPortInfo2);

        UiccCardInfo uiccCardInfo = new UiccCardInfo(true, 1, "", 0, false, true, uiccPortInfoList);
        ArrayList<UiccCardInfo> uiccCardInfoList = new ArrayList<>();
        uiccCardInfoList.add(uiccCardInfo);

        doReturn(uiccCardInfoList).when(mMockTelephonyManager).getUiccCardsInfo();
        doReturn(mMockEuiccManager).when(mMockEuiccManager).createForCardId(1);
        doReturn(false).when(mMockEuiccManager).isSimPortAvailable(0);
        doReturn(false).when(mMockEuiccManager).isSimPortAvailable(1);

        mONSProfileSelector = new MyONSProfileSelector(mContext, null);
        mONSProfileSelector.mTelephonyManager = mMockTelephonyManager;
        mONSProfileSelector.mEuiccManager = mMockEuiccManager;

        int portIdx = mONSProfileSelector.getAvailableESIMPortIndex();
        assertEquals(TelephonyManager.INVALID_PORT_INDEX, portIdx);
    }

    @Test
    public void testAvailablePortWhenOpportunisticEsimIsActive() {
        /**
         * 1 - Primary active subscriptions and
         * 1 - Active opportunistic subscription
         */

        List<SubscriptionInfo> activeSubscriptionInfoList = new ArrayList<SubscriptionInfo>();
        List<SubscriptionInfo> opportunisticInfoList = new ArrayList<SubscriptionInfo>();

        SubscriptionInfo oppSubInfo = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
                "123", 1, null, "310", "210", "", true, null, "1", 1, true, null, false, 1839, 1,
                1, null, null, false, 0);

        SubscriptionInfo primarySubInfo = new SubscriptionInfo(6, "", 1, "TMO", "TMO", 1, 1,
                "456", 1, null, "310", "211", "", true, null, "1", 1, false, null, false, 1839, 1,
                1, null, null, false, 1);

        opportunisticInfoList.add(oppSubInfo);
        activeSubscriptionInfoList.add(oppSubInfo);
        activeSubscriptionInfoList.add(primarySubInfo);

        doReturn(opportunisticInfoList).when(mSubscriptionManager)
                .getOpportunisticSubscriptions();
        doReturn(activeSubscriptionInfoList).when(mSubscriptionManager)
                .getCompleteActiveSubscriptionInfoList();

        mONSProfileSelector = new MyONSProfileSelector(mContext, null);
        int portIdx = mONSProfileSelector.getAvailableESIMPortIndex();
        assertEquals(0, portIdx);
    }

    @Test
    public void testAvailablePortWhenTwoOpportunisticEsimsAreActive() {
        /**
         * 2 - Active opportunistic subscriptions.
         */

        List<SubscriptionInfo> activeSubscriptionInfoList = new ArrayList<SubscriptionInfo>();
        List<SubscriptionInfo> opportunisticInfoList = new ArrayList<SubscriptionInfo>();

        SubscriptionInfo opportunisticSubInfo1 = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
                "123", 1, null, "310", "210", "", true, null, "1", 1, true, null, false, 1839, 1,
                1, null, null, false, 0);

        SubscriptionInfo opportunisticSubInfo2 = new SubscriptionInfo(6, "", 1, "TMO", "TMO", 1, 1,
                "456", 1, null, "310", "211", "", true, null, "1", 1, true, null, false, 1839, 1,
                1, null, null, false, 1);

        opportunisticInfoList.add(opportunisticSubInfo1);
        opportunisticInfoList.add(opportunisticSubInfo2);
        activeSubscriptionInfoList.add(opportunisticSubInfo1);
        activeSubscriptionInfoList.add(opportunisticSubInfo2);

        doReturn(opportunisticInfoList).when(mSubscriptionManager)
                .getOpportunisticSubscriptions();
        doReturn(activeSubscriptionInfoList).when(mSubscriptionManager)
                .getCompleteActiveSubscriptionInfoList();

        mONSProfileSelector = new MyONSProfileSelector(mContext, null);
        int portIdx = mONSProfileSelector.getAvailableESIMPortIndex();

        /* one of the opportunistic eSIM port should be selected */
        assertTrue(portIdx == 0 || portIdx == 1);
    }

    @Test
    public void testAvailablePortWhenOpportunisticEsimIsActiveAndInactiveSubscriptions() {
        /**
         * 1 - Primary active subscription and
         * 1 - Active opportunistic subscription and
         * 2 - Inactive opportunistic subscriptions
         */

        List<SubscriptionInfo> activeSubscriptionInfoList = new ArrayList<SubscriptionInfo>();
        List<SubscriptionInfo> opportunisticInfoList = new ArrayList<SubscriptionInfo>();

        SubscriptionInfo opportunisticSubInfo1 = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
                "123", 1, null, "310", "210", "", true, null, "1", 1, true, null, false, 1839, 1,
                1, null, null, false, 1);
        SubscriptionInfo primarySubInfo = new SubscriptionInfo(6, "", 1, "TMO", "TMO", 1, 1,
                "456", 1, null, "310", "211", "", true, null, "1", 1, false, null, false, 1839, 1,
                1, null, null, false, 0);

        SubscriptionInfo opportunisticSubInfo2 = new SubscriptionInfo(7, "", 1, "TMO", "TMO", 1, 1,
                "789", 1, null, "310", "210", "", true, null, "1", 1, true, null, false, 1839, 1,
                1, null, null, false, TelephonyManager.INVALID_PORT_INDEX);

        SubscriptionInfo oppSubInfo3 = new SubscriptionInfo(8, "", 1, "TMO", "TMO", 1, 1,
                "012", 1, null, "310", "210", "", true, null, "1", 1, true, null, false, 1839, 1,
                1, null, null, false, TelephonyManager.INVALID_PORT_INDEX);

        opportunisticInfoList.add(opportunisticSubInfo1);
        opportunisticInfoList.add(opportunisticSubInfo2);
        opportunisticInfoList.add(oppSubInfo3);
        activeSubscriptionInfoList.add(opportunisticSubInfo1);
        activeSubscriptionInfoList.add(primarySubInfo);

        doReturn(opportunisticInfoList).when(mSubscriptionManager)
                .getOpportunisticSubscriptions();
        doReturn(activeSubscriptionInfoList).when(mSubscriptionManager)
                .getCompleteActiveSubscriptionInfoList();

        mONSProfileSelector = new MyONSProfileSelector(mContext, null);
        int portIdx = mONSProfileSelector.getAvailableESIMPortIndex();
        assertEquals(1, portIdx);
    }

    @Test
    public void testAvailablePortWhenOnlyInactiveSubscriptions() {
        /**
         * 1 - Primary inactive subscription and
         * 2 - Inactive opportunistic subscriptions
         */

        List<SubscriptionInfo> activeSubscriptionInfoList = new ArrayList<SubscriptionInfo>();
        List<SubscriptionInfo> opportunisticInfoList = new ArrayList<SubscriptionInfo>();

        SubscriptionInfo oppSubInfo1 = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
                "123", 1, null, "310", "210", "", true, null, "1", 1, true, null, false, 1839, 1,
                1, null, null, false, TelephonyManager.INVALID_PORT_INDEX);

        // Not used in activeSubscriptionInfoList or opportunisticInfoList
        /*SubscriptionInfo primarySubInfo = new SubscriptionInfo(6, "", 1, "TMO", "TMO", 1, 1,
                "456", 1, null, "310", "211", "", true, null, "1", 1, false, null, false, 1839, 1,
                1, null, null, false, 2);*/

        SubscriptionInfo oppSubInfo2 = new SubscriptionInfo(7, "", 1, "TMO", "TMO", 1, 1,
                "789", 1, null, "310", "210", "", true, null, "1", 1, true, null, false, 1839, 1,
                1, null, null, false, TelephonyManager.INVALID_PORT_INDEX);

        opportunisticInfoList.add(oppSubInfo1);
        opportunisticInfoList.add(oppSubInfo2);

        doReturn(opportunisticInfoList).when(mSubscriptionManager)
                .getOpportunisticSubscriptions();
        doReturn(activeSubscriptionInfoList).when(mSubscriptionManager)
                .getCompleteActiveSubscriptionInfoList();

        UiccPortInfo uiccPortInfo1 = new UiccPortInfo("", 0, 0, false);
        UiccPortInfo uiccPortInfo2 = new UiccPortInfo("", 1, 0, false);
        ArrayList<UiccPortInfo> uiccPortInfoList = new ArrayList<>();
        uiccPortInfoList.add(uiccPortInfo1);
        uiccPortInfoList.add(uiccPortInfo2);

        UiccCardInfo uiccCardInfo = new UiccCardInfo(true, 1, "", 0, false, true, uiccPortInfoList);
        ArrayList<UiccCardInfo> uiccCardInfoList = new ArrayList<>();
        uiccCardInfoList.add(uiccCardInfo);

        doReturn(uiccCardInfoList).when(mMockTelephonyManager).getUiccCardsInfo();
        doReturn(mMockEuiccManager).when(mMockEuiccManager).createForCardId(1);
        doReturn(true).when(mMockEuiccManager).isSimPortAvailable(0);
        doReturn(true).when(mMockEuiccManager).isSimPortAvailable(1);

        mONSProfileSelector = new MyONSProfileSelector(mContext, null);
        mONSProfileSelector.mTelephonyManager = mMockTelephonyManager;
        mONSProfileSelector.mEuiccManager = mMockEuiccManager;

        int portIdx = mONSProfileSelector.getAvailableESIMPortIndex();
        assertTrue(portIdx == 0 || portIdx == 1);
    }

    @Test
    public void testGetMncMccFromCellInfoNr() {
        mONSProfileSelector = new MyONSProfileSelector(mContext, null);

        CellIdentityNr cellIdentityNr = new CellIdentityNr(0, 0, 0, new int[]{0}, "111", "222", 0,
                "", "",  Collections.emptyList());

        CellInfoNr cellinfoNr = new CellInfoNr(0, true, 0, cellIdentityNr, null);

        assertEquals(mONSProfileSelector.getMcc(cellinfoNr), "111");
        assertEquals(mONSProfileSelector.getMnc(cellinfoNr), "222");
    }
}
