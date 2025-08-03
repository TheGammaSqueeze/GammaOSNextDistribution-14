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

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;

import android.os.Looper;
import android.telephony.AccessNetworkConstants;
import android.telephony.AvailableNetworkInfo;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.NetworkScan;
import android.telephony.NetworkScanRequest;
import android.telephony.RadioAccessSpecifier;
import android.telephony.SubscriptionInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ONSNetworkScanCtlrTest extends ONSBaseTest {
    private ONSNetworkScanCtlr mONSNetworkScanCtlr;
    private NetworkScan mNetworkScan;
    private List<CellInfo> mResults;
    private int mError;
    private boolean mCallbackInvoked;
    private Looper mLooper;

    private static final int SEARCH_PERIODICITY = 60;
    private static final SubscriptionInfo TEST_SUBSCRIPTION_INFO = new SubscriptionInfo(
            1, "", 1, null, null, 0, 0, null, 0, null, "310", "210", null,
            false, null, null);
    private static final RadioAccessSpecifier TEST_5G_RAS = new RadioAccessSpecifier(
            AccessNetworkConstants.AccessNetworkType.NGRAN,
            new int[] {AccessNetworkConstants.NgranBands.BAND_71},
            null);
    private static final RadioAccessSpecifier TEST_4G_RAS = new RadioAccessSpecifier(
            AccessNetworkConstants.AccessNetworkType.EUTRAN,
            new int[] {AccessNetworkConstants.EutranBand.BAND_48},
            null);

    @Before
    public void setUp() throws Exception {
        super.setUp("ONSTest");
        mLooper = null;
        mNetworkScan = new NetworkScan(1, 1);
        doReturn(mNetworkScan).when(mMockTelephonyManager).requestNetworkScan(anyObject(), anyObject());
    }

    @After
    public void tearDown() throws Exception {
        if (mLooper != null) {
            mLooper.quit();
            mLooper.getThread().join();
        }
        super.tearDown();
    }

    @Test
    public void testStartFastNetworkScan() {
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
                new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);
        List<CellInfo> expectedResults = new ArrayList<CellInfo>();
        CellIdentityLte cellIdentityLte = new CellIdentityLte(310, 210, 1, 1, 1);
        CellInfoLte cellInfoLte = new CellInfoLte();
        cellInfoLte.setCellIdentity(cellIdentityLte);
        expectedResults.add((CellInfo)cellInfoLte);

        initONSNetworkScanCtrl();

        // Testing startFastNetworkScan, onNetworkAvailability should be called with expectedResults
        mONSNetworkScanCtlr.startFastNetworkScan(availableNetworkInfos);
        mONSNetworkScanCtlr.mNetworkScanCallback.onResults(expectedResults);
        waitUntilReady(100);
        assertEquals(expectedResults, mResults);
    }

    @Test
    public void testStartFastNetworkScanFail() {
        List<CellInfo> expectedResults = new ArrayList<CellInfo>();
        CellIdentityLte cellIdentityLte = new CellIdentityLte(310, 210, 1, 1, 1);
        CellInfoLte cellInfoLte = new CellInfoLte();
        cellInfoLte.setCellIdentity(cellIdentityLte);
        expectedResults.add((CellInfo)cellInfoLte);
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
                new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);
        mError = NetworkScan.SUCCESS;

        initONSNetworkScanCtrl();

        // Testing startFastNetworkScan, onError should be called with ERROR_INVALID_SCAN
        mONSNetworkScanCtlr.startFastNetworkScan(availableNetworkInfos);
        mONSNetworkScanCtlr.mNetworkScanCallback.onError(NetworkScan.ERROR_INVALID_SCAN);
        waitUntilReady(100);
        assertEquals(NetworkScan.ERROR_INVALID_SCAN, mError);
    }

    @Test
    public void testStartFastNetworkScanWithMultipleNetworks() {
        List<CellInfo> expectedResults = new ArrayList<CellInfo>();
        CellIdentityLte cellIdentityLte = new CellIdentityLte(310, 210, 1, 1, 1);
        CellInfoLte cellInfoLte = new CellInfoLte();
        cellInfoLte.setCellIdentity(cellIdentityLte);
        expectedResults.add((CellInfo)cellInfoLte);
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        mccMncs.add("310211");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
            new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);

        initONSNetworkScanCtrl();

        // Testing startSlowNetworkScan, onNetworkAvailability should be called with expectedResults
        mONSNetworkScanCtlr.startFastNetworkScan(availableNetworkInfos);
        mONSNetworkScanCtlr.mNetworkScanCallback.onResults(expectedResults);
        waitUntilReady(100);
        assertEquals(expectedResults, mResults);
    }

    @Test
    public void testStopNetworkScan() {
        List<CellInfo> expectedResults = new ArrayList<CellInfo>();
        CellIdentityLte cellIdentityLte = new CellIdentityLte(310, 210, 1, 1, 1);
        CellInfoLte cellInfoLte = new CellInfoLte();
        cellInfoLte.setCellIdentity(cellIdentityLte);
        expectedResults.add((CellInfo)cellInfoLte);
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
                new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos =
                new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);
        mCallbackInvoked = false;

        initONSNetworkScanCtrl();

        // Testing stopNetworkScan, should not get any callback invocation after stopNetworkScan.
        mONSNetworkScanCtlr.startFastNetworkScan(availableNetworkInfos);
        mONSNetworkScanCtlr.stopNetworkScan();
        mONSNetworkScanCtlr.mNetworkScanCallback.onResults(expectedResults);
        waitUntilReady(100);
        assertFalse(mCallbackInvoked);
    }

    @Test
    public void testCreateNetworkScanRequest_withNoSpecifiedRasOrBands_4gScanEnabled() {
        initONSNetworkScanCtrl();
        mONSNetworkScanCtlr.setIs4gScanEnabled(true);

        NetworkScanRequest networkScanRequest = createNetworkScanRequest(new ArrayList<>());
        RadioAccessSpecifier[] radioAccessSpecifiers = networkScanRequest.getSpecifiers();

        assertEquals(networkScanRequest.getSearchPeriodicity(), SEARCH_PERIODICITY);
        assertEquals(networkScanRequest.getPlmns().size(), 1);
        assertEquals(networkScanRequest.getPlmns().get(0), "310210");
        assertEquals(radioAccessSpecifiers.length, 2);
        assertEquals(radioAccessSpecifiers[0], ONSNetworkScanCtlr.DEFAULT_5G_RAS);
        assertEquals(radioAccessSpecifiers[1], ONSNetworkScanCtlr.DEFAULT_4G_RAS);
    }

    @Test
    public void testCreateNetworkScanRequest_withNoSpecifiedRasOrBands_4gScanDisabled() {
        initONSNetworkScanCtrl();
        mONSNetworkScanCtlr.setIs4gScanEnabled(false);

        NetworkScanRequest networkScanRequest = createNetworkScanRequest(new ArrayList<>());
        RadioAccessSpecifier[] radioAccessSpecifiers = networkScanRequest.getSpecifiers();

        assertEquals(networkScanRequest.getSearchPeriodicity(), SEARCH_PERIODICITY);
        assertEquals(networkScanRequest.getPlmns().size(), 1);
        assertEquals(networkScanRequest.getPlmns().get(0), "310210");
        assertEquals(radioAccessSpecifiers.length, 1);
        assertEquals(radioAccessSpecifiers[0], ONSNetworkScanCtlr.DEFAULT_5G_RAS);
    }

    @Test
    public void testCreateNetworkScanRequest_withSpecified5gRAS_4gScanEnabled() {
        initONSNetworkScanCtrl();
        mONSNetworkScanCtlr.setIs4gScanEnabled(true);

        NetworkScanRequest networkScanRequest = createNetworkScanRequest(
                new ArrayList<>(Arrays.asList(TEST_5G_RAS)));
        RadioAccessSpecifier[] radioAccessSpecifiers = networkScanRequest.getSpecifiers();

        assertEquals(networkScanRequest.getSearchPeriodicity(), SEARCH_PERIODICITY);
        assertEquals(networkScanRequest.getPlmns().size(), 1);
        assertEquals(networkScanRequest.getPlmns().get(0), "310210");
        assertEquals(radioAccessSpecifiers.length, 1);
        assertEquals(radioAccessSpecifiers[0], TEST_5G_RAS);
    }

    @Test
    public void testCreateNetworkScanRequest_withSpecified4gRAS_4gScanEnabled() {
        initONSNetworkScanCtrl();
        mONSNetworkScanCtlr.setIs4gScanEnabled(true);

        NetworkScanRequest networkScanRequest = createNetworkScanRequest(
                new ArrayList<>(Arrays.asList(TEST_4G_RAS)));
        RadioAccessSpecifier[] radioAccessSpecifiers = networkScanRequest.getSpecifiers();

        assertEquals(networkScanRequest.getSearchPeriodicity(), SEARCH_PERIODICITY);
        assertEquals(networkScanRequest.getPlmns().size(), 1);
        assertEquals(networkScanRequest.getPlmns().get(0), "310210");
        assertEquals(radioAccessSpecifiers.length, 1);
        assertEquals(radioAccessSpecifiers[0], TEST_4G_RAS);
    }

    @Test
    public void testCreateNetworkScanRequest_withSpecified4gRAS_4gScanDisabled() {
        initONSNetworkScanCtrl();
        mONSNetworkScanCtlr.setIs4gScanEnabled(false);

        NetworkScanRequest networkScanRequest = createNetworkScanRequest(
                new ArrayList<>(Arrays.asList(TEST_4G_RAS)));
        RadioAccessSpecifier[] radioAccessSpecifiers = networkScanRequest.getSpecifiers();

        assertEquals(networkScanRequest.getSearchPeriodicity(), SEARCH_PERIODICITY);
        assertEquals(networkScanRequest.getPlmns().size(), 1);
        assertEquals(networkScanRequest.getPlmns().get(0), "310210");
        assertEquals(radioAccessSpecifiers.length, 1);
        assertEquals(radioAccessSpecifiers[0], ONSNetworkScanCtlr.DEFAULT_5G_RAS);
    }

    @Test
    public void testCreateNetworkScanRequest_withSpecified4gAnd5gRAS_4gScanEnabled() {
        initONSNetworkScanCtrl();
        mONSNetworkScanCtlr.setIs4gScanEnabled(true);

        NetworkScanRequest networkScanRequest = createNetworkScanRequest(
                new ArrayList<>(Arrays.asList(TEST_5G_RAS, TEST_4G_RAS)));
        RadioAccessSpecifier[] radioAccessSpecifiers = networkScanRequest.getSpecifiers();

        assertEquals(networkScanRequest.getSearchPeriodicity(), SEARCH_PERIODICITY);
        assertEquals(networkScanRequest.getPlmns().size(), 1);
        assertEquals(networkScanRequest.getPlmns().get(0), "310210");
        assertEquals(radioAccessSpecifiers.length, 2);
        assertEquals(radioAccessSpecifiers[0], TEST_4G_RAS);
        assertEquals(radioAccessSpecifiers[1], TEST_5G_RAS);
    }

    @Test
    public void testCreateNetworkScanRequest_withSpecified4gAnd5gRAS_4gScanDisabled() {
        initONSNetworkScanCtrl();
        mONSNetworkScanCtlr.setIs4gScanEnabled(false);

        NetworkScanRequest networkScanRequest = createNetworkScanRequest(
                new ArrayList<>(Arrays.asList(TEST_5G_RAS, TEST_4G_RAS)));
        RadioAccessSpecifier[] radioAccessSpecifiers = networkScanRequest.getSpecifiers();

        assertEquals(networkScanRequest.getSearchPeriodicity(), SEARCH_PERIODICITY);
        assertEquals(networkScanRequest.getPlmns().size(), 1);
        assertEquals(networkScanRequest.getPlmns().get(0), "310210");
        assertEquals(radioAccessSpecifiers.length, 1);
        assertEquals(radioAccessSpecifiers[0], TEST_5G_RAS);
    }

    private NetworkScanRequest createNetworkScanRequest(ArrayList<RadioAccessSpecifier> ras) {
        AvailableNetworkInfo availableNetworkInfo =
                new AvailableNetworkInfo.Builder(TEST_SUBSCRIPTION_INFO.getSubscriptionId())
                        .setPriority(AvailableNetworkInfo.PRIORITY_LOW)
                        .setMccMncs(new ArrayList<>(Arrays.asList("310210")))
                        .setRadioAccessSpecifiers(ras)
                        .build();
        ArrayList<AvailableNetworkInfo> availableNetworkInfos =
            new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);

        return mONSNetworkScanCtlr.createNetworkScanRequest(availableNetworkInfos,
                SEARCH_PERIODICITY);
    }

    private void initONSNetworkScanCtrl() {
        mReady = false;

        // initializing ONSNetworkScanCtlr
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mONSNetworkScanCtlr = new ONSNetworkScanCtlr(mContext, mMockTelephonyManager,
                    new ONSNetworkScanCtlr.NetworkAvailableCallBack() {
                        @Override
                        public void onNetworkAvailability(List<CellInfo> results) {
                            mResults = results;
                            setReady(true);
                        }

                        public void onError(int error) {
                            mError = error;
                            setReady(true);
                        }
                    });

                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();
        mReady = false;
    }

    @Test
    public void testGetMncMccFromCellInfoNr() {
        mONSNetworkScanCtlr = new ONSNetworkScanCtlr(mContext, mMockTelephonyManager, null);

        CellIdentityNr cellIdentityNr = new CellIdentityNr(0, 0, 0, new int[]{0}, "111", "222", 0,
                "", "",  Collections.emptyList());

        CellInfoNr cellinfoNr = new CellInfoNr(0, true, 0, cellIdentityNr, null);

        assertEquals(mONSNetworkScanCtlr.getMccMnc(cellinfoNr), "111222");
    }
}
