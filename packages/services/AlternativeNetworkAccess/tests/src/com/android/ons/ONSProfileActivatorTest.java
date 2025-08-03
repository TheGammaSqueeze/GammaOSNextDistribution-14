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

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.euicc.EuiccManager;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ONSProfileActivatorTest extends ONSBaseTest {
    private static final String TAG = ONSProfileActivatorTest.class.getName();
    private static final int TEST_SUBID_0 = 0;
    private static final int TEST_SUBID_1 = 1;

    @Mock
    Context mMockContext;
    @Mock
    SubscriptionManager mMockSubManager;
    @Mock
    EuiccManager mMockEuiccManager;
    @Mock
    TelephonyManager mMockTeleManager;
    @Mock
    ConnectivityManager mMockConnectivityManager;
    @Mock
    CarrierConfigManager mMockCarrierConfigManager;
    @Mock
    ONSProfileConfigurator mMockONSProfileConfigurator;
    @Mock
    ONSProfileDownloader mMockONSProfileDownloader;
    @Mock
    List<SubscriptionInfo> mMockactiveSubInfos;
    @Mock
    SubscriptionInfo mMockSubInfo;
    @Mock
    SubscriptionInfo mMockSubInfo1;
    @Mock
    List<SubscriptionInfo> mMocksubsInPSIMGroup;
    @Mock
    Resources mMockResources;
    @Mock
    ONSStats mMockONSStats;

    @Before
    public void setUp() throws Exception {
        super.setUp("ONSTest");
        MockitoAnnotations.initMocks(this);
        Looper.prepare();

        doReturn(mMockResources).when(mMockContext).getResources();

        doReturn(mMockConnectivityManager).when(mMockContext).getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkRequest request = new NetworkRequest.Builder().addCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED).build();
        doNothing().when(mMockConnectivityManager).registerNetworkCallback(request,
                new ConnectivityManager.NetworkCallback());

        PersistableBundle persistableBundle = new PersistableBundle();
        persistableBundle.putBoolean(CarrierConfigManager
                .KEY_CARRIER_SUPPORTS_OPP_DATA_AUTO_PROVISIONING_BOOL, true);
        doReturn(persistableBundle).when(mMockCarrierConfigManager).getConfigForSubId(TEST_SUBID_1);
    }

    // Worker thread is used for testing asynchronous APIs and Message Handlers.
    // ASync APIs are called and Handler messages are processed by Worker thread. Test results are
    // verified by Main Thread.
    static class WorkerThread extends Thread {
        Looper mWorkerLooper;
        private final Runnable mRunnable;

        WorkerThread(Runnable runnable) {
            mRunnable = runnable;
        }

        @Override
        public void run() {
            super.run();
            Looper.prepare();
            mWorkerLooper = Looper.myLooper();
            mRunnable.run();
            mWorkerLooper.loop();
        }

        public void exit() {
            mWorkerLooper.quitSafely();
        }
    }

    /*@Test
    public void testSIMNotReady() {
        doReturn(TelephonyManager.SIM_STATE_NOT_READY).when(mMockTeleManager).getSimState();

        ONSProfileActivator onsProfileActivator = new ONSProfileActivator(mMockContext,
                mMockSubManager, mMockTeleManager, mMockCarrierConfigManager, mMockEuiccManager,
                mMockConnectivityManager, mMockONSProfileConfigurator, mMockONSProfileDownloader);

        assertEquals(ONSProfileActivator.Result.ERR_SIM_NOT_READY,
                onsProfileActivator.handleSimStateChange());
    }*/

    @Test
    public void testONSAutoProvisioningDisabled() {

        doReturn(false).when(mMockResources).getBoolean(R.bool.enable_ons_auto_provisioning);

        ONSProfileActivator onsProfileActivator = new ONSProfileActivator(mMockContext,
                mMockSubManager, mMockTeleManager, mMockCarrierConfigManager, mMockEuiccManager,
                mMockConnectivityManager, mMockONSProfileConfigurator, mMockONSProfileDownloader,
                mMockONSStats);

        assertEquals(ONSProfileActivator.Result.ERR_AUTO_PROVISIONING_DISABLED,
                onsProfileActivator.handleCarrierConfigChange());
    }

    @Test
    public void testESIMNotSupported() {

        doReturn(true).when(mMockResources).getBoolean(R.bool.enable_ons_auto_provisioning);
        doReturn(false).when(mMockEuiccManager).isEnabled();

        ONSProfileActivator onsProfileActivator = new ONSProfileActivator(mMockContext,
                mMockSubManager, mMockTeleManager, mMockCarrierConfigManager, mMockEuiccManager,
                mMockConnectivityManager, mMockONSProfileConfigurator, mMockONSProfileDownloader,
                mMockONSStats);

        assertEquals(ONSProfileActivator.Result.ERR_ESIM_NOT_SUPPORTED,
                onsProfileActivator.handleCarrierConfigChange());
    }

    @Test
    //@DisplayName("Single SIM Device with eSIM support")
    public void testMultiSIMNotSupported() {

        doReturn(true).when(mMockResources).getBoolean(R.bool.enable_ons_auto_provisioning);
        doReturn(true).when(mMockEuiccManager).isEnabled();
        doReturn(1).when(mMockTeleManager).getSupportedModemCount();
        doReturn(2).when(mMockTeleManager).getActiveModemCount();

        ONSProfileActivator onsProfileActivator = new ONSProfileActivator(mMockContext,
                mMockSubManager, mMockTeleManager, mMockCarrierConfigManager, mMockEuiccManager,
                mMockConnectivityManager, mMockONSProfileConfigurator, mMockONSProfileDownloader,
                mMockONSStats);

        assertEquals(ONSProfileActivator.Result.ERR_MULTISIM_NOT_SUPPORTED,
                onsProfileActivator.handleCarrierConfigChange());
    }

    @Test
    public void testDeviceSwitchToDualSIMModeFailed() {

        doReturn(true).when(mMockResources).getBoolean(R.bool.enable_ons_auto_provisioning);
        doReturn(true).when(mMockEuiccManager).isEnabled();
        doReturn(2).when(mMockTeleManager).getSupportedModemCount();
        doReturn(1).when(mMockTeleManager).getActiveModemCount();
        doReturn(true).when(mMockTeleManager).doesSwitchMultiSimConfigTriggerReboot();
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockSubInfo).when(mMockactiveSubInfos).get(TEST_SUBID_0);
        doReturn(1).when(mMockSubInfo).getSubscriptionId();
        doReturn(false).when(mMockSubInfo).isOpportunistic();

        ONSProfileActivator onsProfileActivator = new ONSProfileActivator(mMockContext,
                mMockSubManager, mMockTeleManager, mMockCarrierConfigManager, mMockEuiccManager,
                mMockConnectivityManager, mMockONSProfileConfigurator, mMockONSProfileDownloader,
                mMockONSStats);

        assertEquals(ONSProfileActivator.Result.ERR_CANNOT_SWITCH_TO_DUAL_SIM_MODE,
                onsProfileActivator.handleCarrierConfigChange());
    }

    @Test
    public void testDeviceSwitchToDualSIMModeSuccess() {

        doReturn(true).when(mMockResources).getBoolean(R.bool.enable_ons_auto_provisioning);
        doReturn(true).when(mMockEuiccManager).isEnabled();
        doReturn(2).when(mMockTeleManager).getSupportedModemCount();
        doReturn(1).when(mMockTeleManager).getActiveModemCount();
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockSubInfo).when(mMockactiveSubInfos).get(TEST_SUBID_0);
        doReturn(1).when(mMockSubInfo).getSubscriptionId();
        doReturn(false).when(mMockSubInfo).isOpportunistic();

        doReturn(false).when(mMockTeleManager).doesSwitchMultiSimConfigTriggerReboot();

        ONSProfileActivator onsProfileActivator = new ONSProfileActivator(mMockContext,
                mMockSubManager, mMockTeleManager, mMockCarrierConfigManager, mMockEuiccManager,
                mMockConnectivityManager, mMockONSProfileConfigurator, mMockONSProfileDownloader,
                mMockONSStats);

        assertEquals(ONSProfileActivator.Result.ERR_SWITCHING_TO_DUAL_SIM_MODE,
                onsProfileActivator.handleCarrierConfigChange());
    }

    //@DisplayName("Dual SIM device with no SIM inserted")
    public void testNoActiveSubscriptions() {

        doReturn(true).when(mMockResources).getBoolean(R.bool.enable_ons_auto_provisioning);
        doReturn(true).when(mMockEuiccManager).isEnabled();
        doReturn(2).when(mMockTeleManager).getSupportedModemCount();
        doReturn(2).when(mMockTeleManager).getActiveModemCount();
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(0).when(mMockactiveSubInfos).size();

        ONSProfileActivator onsProfileActivator = new ONSProfileActivator(mMockContext,
                mMockSubManager, mMockTeleManager, mMockCarrierConfigManager, mMockEuiccManager,
                mMockConnectivityManager, mMockONSProfileConfigurator, mMockONSProfileDownloader,
                mMockONSStats);

        assertEquals(ONSProfileActivator.Result.ERR_NO_SIM_INSERTED,
                onsProfileActivator.handleCarrierConfigChange());
    }

    public void testNullActiveSubscriptionList() {

        doReturn(true).when(mMockResources).getBoolean(R.bool.enable_ons_auto_provisioning);
        doReturn(true).when(mMockEuiccManager).isEnabled();
        doReturn(2).when(mMockTeleManager).getSupportedModemCount();
        doReturn(2).when(mMockTeleManager).getActiveModemCount();
        doReturn(null).when(mMockSubManager).getActiveSubscriptionInfoList();

        ONSProfileActivator onsProfileActivator = new ONSProfileActivator(mMockContext,
                mMockSubManager, mMockTeleManager, mMockCarrierConfigManager, mMockEuiccManager,
                mMockConnectivityManager, mMockONSProfileConfigurator, mMockONSProfileDownloader,
                mMockONSStats);

        assertEquals(ONSProfileActivator.Result.ERR_NO_SIM_INSERTED,
                onsProfileActivator.handleCarrierConfigChange());
    }

    @Test
    //@DisplayName("Dual SIM device and non CBRS carrier pSIM inserted")
    public void testNonCBRSCarrierPSIMInserted() {

        doReturn(true).when(mMockResources).getBoolean(R.bool.enable_ons_auto_provisioning);
        doReturn(true).when(mMockEuiccManager).isEnabled();
        doReturn(2).when(mMockTeleManager).getSupportedModemCount();
        doReturn(2).when(mMockTeleManager).getActiveModemCount();

        PersistableBundle persistableBundle = new PersistableBundle();
        persistableBundle.putBoolean(CarrierConfigManager
                .KEY_CARRIER_SUPPORTS_OPP_DATA_AUTO_PROVISIONING_BOOL, false);
        doReturn(persistableBundle).when(mMockCarrierConfigManager).getConfigForSubId(TEST_SUBID_0);

        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockSubInfo).when(mMockactiveSubInfos).get(TEST_SUBID_0);
        doReturn(TEST_SUBID_0).when(mMockSubInfo).getSubscriptionId();
        doReturn(false).when(mMockSubInfo).isOpportunistic();

        ONSProfileActivator onsProfileActivator = new ONSProfileActivator(mMockContext,
                mMockSubManager, mMockTeleManager, mMockCarrierConfigManager, mMockEuiccManager,
                mMockConnectivityManager, mMockONSProfileConfigurator, mMockONSProfileDownloader,
                mMockONSStats);

        assertEquals(ONSProfileActivator.Result.ERR_CARRIER_DOESNT_SUPPORT_CBRS,
                onsProfileActivator.handleCarrierConfigChange());
    }

    @Test
    //@DisplayName("Dual SIM device with Two PSIM active subscriptions")
    public void testTwoActivePSIMSubscriptions() {

        doReturn(true).when(mMockResources).getBoolean(R.bool.enable_ons_auto_provisioning);
        doReturn(true).when(mMockEuiccManager).isEnabled();
        doReturn(2).when(mMockTeleManager).getSupportedModemCount();
        doReturn(2).when(mMockTeleManager).getActiveModemCount();

        ArrayList<SubscriptionInfo> mActiveSubInfos = new ArrayList<>();
        mActiveSubInfos.add(mMockSubInfo);
        mActiveSubInfos.add(mMockSubInfo1);
        doReturn(mActiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(false).when(mMockSubInfo).isEmbedded();
        doReturn(false).when(mMockSubInfo1).isEmbedded();

        ONSProfileActivator onsProfileActivator = new ONSProfileActivator(mMockContext,
                mMockSubManager, mMockTeleManager, mMockCarrierConfigManager, mMockEuiccManager,
                mMockConnectivityManager, mMockONSProfileConfigurator, mMockONSProfileDownloader,
                mMockONSStats);

        assertEquals(ONSProfileActivator.Result.ERR_DUAL_ACTIVE_SUBSCRIPTIONS,
                onsProfileActivator.handleCarrierConfigChange());
    }

    @Test
    public void testOneCBRSPSIMAndOneNonCBRSESIM() {
        doReturn(true).when(mMockResources).getBoolean(R.bool.enable_ons_auto_provisioning);
        doReturn(true).when(mMockEuiccManager).isEnabled();
        doReturn(2).when(mMockTeleManager).getSupportedModemCount();
        doReturn(2).when(mMockTeleManager).getActiveModemCount();

        ArrayList<SubscriptionInfo> mActiveSubInfos = new ArrayList<>();
        mActiveSubInfos.add(mMockSubInfo);
        mActiveSubInfos.add(mMockSubInfo1);
        doReturn(mActiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(false).when(mMockSubInfo).isEmbedded();
        doReturn(true).when(mMockSubInfo1).isEmbedded();
        doReturn(TEST_SUBID_0).when(mMockSubInfo).getSubscriptionId();
        doReturn(TEST_SUBID_1).when(mMockSubInfo1).getSubscriptionId();

        PersistableBundle persistableBundle = new PersistableBundle();
        persistableBundle.putBoolean(CarrierConfigManager
                .KEY_CARRIER_SUPPORTS_OPP_DATA_AUTO_PROVISIONING_BOOL, false);
        doReturn(persistableBundle).when(mMockCarrierConfigManager).getConfigForSubId(TEST_SUBID_0);

        ONSProfileActivator onsProfileActivator = new ONSProfileActivator(mMockContext,
                mMockSubManager, mMockTeleManager, mMockCarrierConfigManager, mMockEuiccManager,
                mMockConnectivityManager, mMockONSProfileConfigurator, mMockONSProfileDownloader,
                mMockONSStats);

        assertEquals(ONSProfileActivator.Result.ERR_DUAL_ACTIVE_SUBSCRIPTIONS,
                onsProfileActivator.handleCarrierConfigChange());
    }

    @Test
    public void testOneCBRSPSIMAndOneOpportunisticESIM() {
        doReturn(true).when(mMockResources).getBoolean(R.bool.enable_ons_auto_provisioning);
        doReturn(true).when(mMockEuiccManager).isEnabled();
        doReturn(2).when(mMockTeleManager).getSupportedModemCount();
        doReturn(2).when(mMockTeleManager).getActiveModemCount();

        ArrayList<SubscriptionInfo> mActiveSubInfos = new ArrayList<>();
        mActiveSubInfos.add(mMockSubInfo); //Primary CBRS SIM
        mActiveSubInfos.add(mMockSubInfo1); //Opportunistic eSIM
        doReturn(mActiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(mActiveSubInfos).when(mMockSubManager).getAvailableSubscriptionInfoList();

        doReturn(mMockSubInfo).when(mMockSubManager).getActiveSubscriptionInfo(TEST_SUBID_0);
        doReturn(TEST_SUBID_0).when(mMockSubInfo).getSubscriptionId();
        doReturn(true).when(mMockSubManager).isActiveSubscriptionId(TEST_SUBID_0);
        doReturn(false).when(mMockSubInfo).isOpportunistic();
        doReturn(false).when(mMockSubInfo).isEmbedded();
        ParcelUuid pSIMSubGroupId = new ParcelUuid(new UUID(0, 1));
        doReturn(pSIMSubGroupId).when(mMockSubInfo).getGroupUuid();
        PersistableBundle persistableBundle = new PersistableBundle();
        persistableBundle.putBoolean(CarrierConfigManager
                .KEY_CARRIER_SUPPORTS_OPP_DATA_AUTO_PROVISIONING_BOOL, true);
        persistableBundle.putIntArray(CarrierConfigManager
                .KEY_OPPORTUNISTIC_CARRIER_IDS_INT_ARRAY, new int[]{1, 2});
        doReturn(persistableBundle).when(mMockCarrierConfigManager).getConfigForSubId(TEST_SUBID_0);

        doReturn(mMockSubInfo1).when(mMockSubManager).getActiveSubscriptionInfo(TEST_SUBID_1);
        doReturn(TEST_SUBID_1).when(mMockSubInfo1).getSubscriptionId();
        doReturn(true).when(mMockSubManager).isActiveSubscriptionId(TEST_SUBID_1);
        doReturn(true).when(mMockSubInfo1).isOpportunistic();
        doReturn(true).when(mMockSubInfo1).isEmbedded();
        doReturn(pSIMSubGroupId).when(mMockSubInfo1).getGroupUuid();
        doReturn(1).when(mMockSubInfo1).getCarrierId();

        doReturn(mMockSubInfo1).when(mMockONSProfileConfigurator)
                .findOpportunisticSubscription(TEST_SUBID_0);

        ONSProfileActivator onsProfileActivator = new ONSProfileActivator(mMockContext,
                mMockSubManager, mMockTeleManager, mMockCarrierConfigManager, mMockEuiccManager,
                mMockConnectivityManager, mMockONSProfileConfigurator, mMockONSProfileDownloader,
                mMockONSStats);

        assertEquals(ONSProfileActivator.Result.SUCCESS,
                onsProfileActivator.handleCarrierConfigChange());
    }

    @Test
    //@DisplayName("Dual SIM device with only opportunistic eSIM active")
    public void testOnlyOpportunisticESIMActive() {

        doReturn(true).when(mMockResources).getBoolean(R.bool.enable_ons_auto_provisioning);
        doReturn(true).when(mMockEuiccManager).isEnabled();
        doReturn(2).when(mMockTeleManager).getSupportedModemCount();
        doReturn(2).when(mMockTeleManager).getActiveModemCount();
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockSubInfo).when(mMockactiveSubInfos).get(0);
        doReturn(true).when(mMockSubInfo).isOpportunistic();

        ONSProfileActivator onsProfileActivator = new ONSProfileActivator(mMockContext,
                mMockSubManager, mMockTeleManager, mMockCarrierConfigManager, mMockEuiccManager,
                mMockConnectivityManager, mMockONSProfileConfigurator, mMockONSProfileDownloader,
                mMockONSStats);

        assertEquals(ONSProfileActivator.Result.ERR_SINGLE_ACTIVE_OPPORTUNISTIC_SIM,
                onsProfileActivator.handleCarrierConfigChange());
    }

    @Test
    //@DisplayName("Dual SIM device, only CBRS carrier pSIM inserted and pSIM not Grouped")
    public void testCBRSpSIMAndNotGrouped() {

        doReturn(true).when(mMockResources).getBoolean(R.bool.enable_ons_auto_provisioning);
        doReturn(true).when(mMockEuiccManager).isEnabled();
        doReturn(2).when(mMockTeleManager).getSupportedModemCount();
        doReturn(2).when(mMockTeleManager).getActiveModemCount();

        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockSubInfo).when(mMockactiveSubInfos).get(0);
        doReturn(false).when(mMockSubInfo).isOpportunistic();
        doReturn(TEST_SUBID_1).when(mMockSubInfo).getSubscriptionId();
        doReturn(null).when(mMockSubInfo).getGroupUuid();
        doReturn(ONSProfileDownloader.DownloadProfileResult.SUCCESS).when(mMockONSProfileDownloader)
                .downloadProfile(TEST_SUBID_1);

        ONSProfileActivator onsProfileActivator = new ONSProfileActivator(mMockContext,
                mMockSubManager, mMockTeleManager, mMockCarrierConfigManager, mMockEuiccManager,
                mMockConnectivityManager, mMockONSProfileConfigurator, mMockONSProfileDownloader,
                mMockONSStats);

        onsProfileActivator.mIsInternetConnAvailable = true;
        assertEquals(ONSProfileActivator.Result.DOWNLOAD_REQUESTED,
                onsProfileActivator.handleCarrierConfigChange());
    }

    @Test
    public void testCalculateBackoffDelay() {
        final Object lock = new Object();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                int delay = ONSProfileActivator.calculateBackoffDelay(1, 1) / 1000;
                assertEquals(true, (delay >= 1 && delay <= 2));

                Log.i(TAG, "calculateBackoffDelay(2, 1)");
                delay = ONSProfileActivator.calculateBackoffDelay(2, 1) / 1000;
                assertEquals(true, (delay >= 1 && delay < 4));

                delay = ONSProfileActivator.calculateBackoffDelay(3, 1) / 1000;
                assertEquals(true, (delay >= 1 && delay < 8));

                delay = ONSProfileActivator.calculateBackoffDelay(4, 1) / 1000;
                assertEquals(true, (delay >= 1 && delay < 16));

                delay = ONSProfileActivator.calculateBackoffDelay(1, 2) / 1000;
                assertEquals(true, (delay >= 1 && delay <= 4));

                delay = ONSProfileActivator.calculateBackoffDelay(1, 3) / 1000;
                assertEquals(true, (delay >= 1 && delay <= 6));

                delay = ONSProfileActivator.calculateBackoffDelay(2, 2) / 1000;
                assertEquals(true, (delay >= 2 && delay < 8));

                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        synchronized (lock) {
            try {
                lock.wait();
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }

        workerThread.exit();
    }

    /* Unable to mock final class ParcelUuid. These testcases should be enabled once the solution
    is found */
    /*@Test
    //@DisplayName("Dual SIM device, only CBRS carrier pSIM inserted and pSIM Grouped")
    public void testOneSubscriptionInCBRSpSIMGroup() {
        ParcelUuid mMockParcelUuid = crateMock(ParcelUuid.class);

        doReturn(true).when(mMockONSProfileConfigurator)
                .isESIMSupported();
        doReturn(true).when(mMockONSUtil).isMultiSIMPhone();
        doReturn(true).when(mMockONSProfileConfigurator).isOppDataAutoProvisioningSupported(
                mMockPrimaryCBRSSubInfo);
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockPrimaryCBRSSubInfo).when(mMockactiveSubInfos).get(0);
        doReturn(false).when(mMockPrimaryCBRSSubInfo).isOpportunistic();
        doReturn(true).when(mMockONSProfileConfigurator).isOppDataAutoProvisioningSupported(
        mMockPrimaryCBRSSubInfo);
        doReturn(mMockParcelUuid).when(mMockPrimaryCBRSSubInfo).getGroupUuid();
        doReturn(mMocksubsInPSIMGroup).when(mMockSubManager).getSubscriptionsInGroup(
        mMockParcelUuid);
        doReturn(1).when(mMocksubsInPSIMGroup).size();

        ONSProfileActivator onsProfileActivator =
                new ONSProfileActivator(mMockContext, mMockSubManager, mMockEuiCCManager,
                mMockTeleManager,
                        mMockONSProfileConfigurator, mMockONSProfileDownloader, mMockONSStats);

        assertEquals(ONSProfileActivator.Result.SUCCESS,
                onsProfileActivator.handleSimStateChange());
    }

    @Test
    //@DisplayName("Dual SIM device, only CBRS carrier pSIM inserted and pSIM Group has two
    subscription info.")
    public void testTwoSubscriptionsInCBRSpSIMGroup() {
        ParcelUuid mMockParcelUuid = crateMock(ParcelUuid.class);

        doReturn(true).when(mMockONSProfileConfigurator)
                .isESIMSupported();
        doReturn(true).when(mMockONSUtil).isMultiSIMPhone();
        doReturn(true).when(mMockONSProfileConfigurator).isOppDataAutoProvisioningSupported(
                mMockPrimaryCBRSSubInfo);
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockPrimaryCBRSSubInfo).when(mMockactiveSubInfos).get(0);
        doReturn(false).when(mMockPrimaryCBRSSubInfo).isOpportunistic();
        doReturn(true).when(mMockONSProfileConfigurator).isOppDataAutoProvisioningSupported(
        mMockPrimaryCBRSSubInfo);
        doReturn(mMockParcelUuid).when(mMockPrimaryCBRSSubInfo).getGroupUuid();
        doReturn(mMocksubsInPSIMGroup).when(mMockSubManager).getSubscriptionsInGroup(
        mMockParcelUuid);
        doReturn(2).when(mMocksubsInPSIMGroup).size();

        ONSProfileActivator onsProfileActivator =
                new ONSProfileActivator(mMockContext, mMockSubManager, mMockEuiCCManager,
                mMockTeleManager, mMockONSProfileConfigurator, mMockONSProfileDownloader);

        assertEquals(ONSProfileActivator.Result.SUCCESS,
                onsProfileActivator.handleSimStateChange());
    }

    @Test
    //@DisplayName("Dual SIM device, only CBRS carrier pSIM inserted and pSIM Group has more than
    two subscription info.")
    public void testMoreThanTwoSubscriptionsInCBRSpSIMGroup() {
        ParcelUuid mMockParcelUuid = crateMock(ParcelUuid.class);

        doReturn(true).when(mMockONSProfileConfigurator)
                .isESIMSupported();
        doReturn(true).when(mMockONSUtil).isMultiSIMPhone();
        doReturn(true).when(mMockONSProfileConfigurator).isOppDataAutoProvisioningSupported(
                mMockPrimaryCBRSSubInfo);
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockPrimaryCBRSSubInfo).when(mMockactiveSubInfos).get(0);
        doReturn(false).when(mMockPrimaryCBRSSubInfo).isOpportunistic();
        doReturn(true).when(mMockONSProfileConfigurator).isOppDataAutoProvisioningSupported(
        mMockPrimaryCBRSSubInfo);
        doReturn(mMockParcelUuid).when(mMockPrimaryCBRSSubInfo).getGroupUuid();
        doReturn(mMocksubsInPSIMGroup).when(mMockSubManager).getSubscriptionsInGroup(
        mMockParcelUuid);
        doReturn(3).when(mMocksubsInPSIMGroup).size();

        ONSProfileActivator onsProfileActivator =
                new ONSProfileActivator(mMockContext, mMockSubManager, mMockEuiCCManager,
                mMockTeleManager, mMockONSProfileConfigurator, mMockONSProfileDownloader);

        assertEquals(ONSProfileActivator.Result.SUCCESS,
                onsProfileActivator.handleSimStateChange());
    }

    @Test
    public void testRetryDownloadAfterRebootWithOppESIMAlreadyDownloaded() {
        doReturn(true).when(mMockONSProfileConfigurator).getRetryDownloadAfterReboot();
        doReturn(1).when(mMockONSProfileConfigurator).getRetryDownloadPSIMSubId();
        doReturn(mMockSubManager).when(mMockONSUtil).getSubscriptionManager();
        doReturn(mMocksubsInPSIMGroup).when(mMockSubManager).getActiveSubscriptionInfo();
        //TODO: mock ParcelUuid - pSIM group

        ONSProfileActivator onsProfileActivator = new ONSProfileActivator(mMockContext,
                mMockONSProfileConfigurator, mMockONSProfileDownloader, mMockONSStats);

        assertEquals(ONSProfileActivator.Result.ERR_INVALID_PSIM_SUBID,
                onsProfileActivator.retryDownloadAfterReboot());
    }
    */

    /*@Test
    public void testNoInternetDownloadRequest() {
        doReturn(TEST_SUB_ID).when(mMockSubInfo).getSubscriptionId();

        ONSProfileDownloader onsProfileDownloader = new ONSProfileDownloader(mMockContext,
                mMockCarrierConfigManager, mMockEUICCManager, mMockONSProfileConfig, null);

        onsProfileDownloader.mIsInternetConnAvailable = false;
        onsProfileDownloader.downloadOpportunisticESIM(mMockSubInfo);

        assertEquals(onsProfileDownloader.mRetryDownloadWhenNWConnected, true);
        verify(mMockEUICCManager, never()).downloadSubscription(null, true, null);
    }*/

    @Test
    public void testESIMDownloadFailureAndRetry() {
        doReturn(true).when(mMockResources).getBoolean(R.bool.enable_ons_auto_provisioning);
        doReturn(true).when(mMockEuiccManager).isEnabled();
        doReturn(2).when(mMockTeleManager).getSupportedModemCount();
        doReturn(2).when(mMockTeleManager).getActiveModemCount();
        doReturn(ONSProfileDownloader.DownloadProfileResult.SUCCESS).when(mMockONSProfileDownloader)
            .downloadProfile(TEST_SUBID_0);

        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockSubInfo).when(mMockactiveSubInfos).get(0);
        doReturn(false).when(mMockSubInfo).isOpportunistic();
        doReturn(TEST_SUBID_0).when(mMockSubInfo).getSubscriptionId();
        doReturn(null).when(mMockSubInfo).getGroupUuid();

        final int maxRetryCount = 5;
        final int retryBackoffTime = 1; //1 second

        PersistableBundle persistableBundle = new PersistableBundle();
        persistableBundle.putBoolean(CarrierConfigManager
                .KEY_CARRIER_SUPPORTS_OPP_DATA_AUTO_PROVISIONING_BOOL, true);
        persistableBundle.putInt(CarrierConfigManager
                .KEY_ESIM_MAX_DOWNLOAD_RETRY_ATTEMPTS_INT, maxRetryCount);
        persistableBundle.putInt(CarrierConfigManager
                .KEY_ESIM_DOWNLOAD_RETRY_BACKOFF_TIMER_SEC_INT, retryBackoffTime);
        doReturn(persistableBundle).when(mMockCarrierConfigManager).getConfigForSubId(TEST_SUBID_0);

        final Object lock = new Object();
        class TestRunnable implements Runnable {
            public ONSProfileActivator mOnsProfileActivator;

            @Override
            public void run() {
                mOnsProfileActivator = new ONSProfileActivator(mMockContext,
                        mMockSubManager, mMockTeleManager, mMockCarrierConfigManager,
                        mMockEuiccManager, mMockConnectivityManager, mMockONSProfileConfigurator,
                        mMockONSProfileDownloader, mMockONSStats);

                synchronized (lock) {
                    lock.notify();
                }
            }
        }

        TestRunnable runnable = new TestRunnable();
        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        ONSProfileActivator onsProfileActivator = runnable.mOnsProfileActivator;
        onsProfileActivator.mIsInternetConnAvailable = true;

        for (int idx = 0; idx <= maxRetryCount; idx++) {
            onsProfileActivator.onDownloadError(
                    TEST_SUBID_0,
                    ONSProfileDownloader.DownloadRetryResultCode.ERR_RETRY_DOWNLOAD, 0);

            //Wait for Handler to process download message. Backoff delay + 500 milli secs.
            try {
                Thread.sleep(onsProfileActivator.calculateBackoffDelay(
                        onsProfileActivator.mDownloadRetryCount, retryBackoffTime) + 1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        workerThread.exit();

        verify(mMockONSProfileDownloader, times(maxRetryCount)).downloadProfile(TEST_SUBID_0);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }
}

