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

package com.android.server.wifi;

import static android.net.wifi.WifiManager.PnoScanResultsCallback.REGISTER_PNO_CALLBACK_RESOURCE_BUSY;
import static android.net.wifi.WifiManager.PnoScanResultsCallback.REMOVE_PNO_CALLBACK_RESULTS_DELIVERED;
import static android.net.wifi.WifiManager.PnoScanResultsCallback.REMOVE_PNO_CALLBACK_UNREGISTERED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.IPnoScanResultsCallback;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiSsid;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.test.TestLooper;
import android.util.ArraySet;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Unit tests for {@link com.android.server.wifi.ExternalPnoScanRequestManager}.
 */
@SmallTest
public class ExternalPnoScanRequestManagerTest extends WifiBaseTest {
    private static final int TEST_UID = 1001;
    private static final String TEST_PACKAGE = "TestPackage";
    private static final String TEST_SSID_1 = "\"TEST_SSID_1\"";
    private static final String TEST_SSID_2 = "\"TEST_SSID_2\"";
    private static final List<WifiSsid> TEST_WIFI_SSIDS = Arrays.asList(
            WifiSsid.fromString(TEST_SSID_1),
            WifiSsid.fromString(TEST_SSID_2));
    private static final Set<String> EXPECTED_SSIDS_SET = new ArraySet<>(
            Arrays.asList(TEST_SSID_1, TEST_SSID_2)
    );
    private static final int[] TEST_FREQUENCIES = new int[] {2420, 5160};
    private static final int[] TEST_FREQUENCIES_2 = new int[] {2420, 5180};
    private static final Set<Integer> EXPECTED_FREQUENCIES_SET =
            new ArraySet<>(Arrays.asList(2420, 5160));

    private TestLooper mLooper;
    private ExternalPnoScanRequestManager mExternalPnoScanRequestManager;
    @Mock private IPnoScanResultsCallback mCallback;
    @Mock private IBinder mIBinder;
    @Mock private Context mContext;

    /**
     * Called before each test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mLooper = new TestLooper();
        mExternalPnoScanRequestManager = new ExternalPnoScanRequestManager(
                new Handler(mLooper.getLooper()), mContext);
        mExternalPnoScanRequestManager.enableVerboseLogging(true);
    }

    @Test
    public void testSetRequest_success() throws RemoteException {
        InOrder inOrder = inOrder(mCallback, mIBinder);

        // initial register should be successful
        assertTrue(mExternalPnoScanRequestManager.setRequest(TEST_UID, TEST_PACKAGE, mIBinder,
                mCallback, TEST_WIFI_SSIDS, TEST_FREQUENCIES));
        inOrder.verify(mIBinder).linkToDeath(mExternalPnoScanRequestManager, 0);
        inOrder.verify(mCallback).onRegisterSuccess();

        // Another register with same uid should override the existing one.
        assertTrue(mExternalPnoScanRequestManager.setRequest(TEST_UID, TEST_PACKAGE, mIBinder,
                mCallback, TEST_WIFI_SSIDS, TEST_FREQUENCIES_2));
        inOrder.verify(mCallback).onRegisterSuccess();

        // Another register with different uid should fail with REGISTER_PNO_CALLBACK_RESOURCE_BUSY
        assertFalse(mExternalPnoScanRequestManager.setRequest(TEST_UID + 1, TEST_PACKAGE,
                mIBinder, mCallback, TEST_WIFI_SSIDS, TEST_FREQUENCIES));
        inOrder.verify(mCallback).onRegisterFailed(REGISTER_PNO_CALLBACK_RESOURCE_BUSY);


        assertEquals(EXPECTED_SSIDS_SET, mExternalPnoScanRequestManager.getExternalPnoScanSsids());
        Set<Integer> expectedFrequencies2 = Arrays.stream(TEST_FREQUENCIES_2).boxed().collect(
                Collectors.toSet());
        assertEquals(expectedFrequencies2,
                mExternalPnoScanRequestManager.getExternalPnoScanFrequencies());
    }

    @Test
    public void testSetRequest_linkToDeathFailed() throws RemoteException {
        IPnoScanResultsCallback anotherCallback = mock(IPnoScanResultsCallback.class);
        InOrder inOrder = inOrder(mCallback, anotherCallback, mIBinder);

        // Expect fail to set request due to link to death fail.
        doThrow(new RemoteException()).when(mIBinder).linkToDeath(
                mExternalPnoScanRequestManager, 0);
        assertFalse(mExternalPnoScanRequestManager.setRequest(TEST_UID, TEST_PACKAGE, mIBinder,
                mCallback, TEST_WIFI_SSIDS, TEST_FREQUENCIES));
        inOrder.verify(mIBinder).linkToDeath(mExternalPnoScanRequestManager, 0);
        assertEquals(Collections.EMPTY_SET,
                mExternalPnoScanRequestManager.getExternalPnoScanSsids());
        assertEquals(Collections.EMPTY_SET,
                mExternalPnoScanRequestManager.getExternalPnoScanFrequencies());
    }

    @Test
    public void testRemoveRequest_explicit() throws RemoteException {
        InOrder inOrder = inOrder(mCallback, mIBinder);
        // Removing when no requests registered should be no-op
        assertFalse(mExternalPnoScanRequestManager.removeRequest(TEST_UID));

        // register a request
        assertTrue(mExternalPnoScanRequestManager.setRequest(TEST_UID, TEST_PACKAGE, mIBinder,
                mCallback, TEST_WIFI_SSIDS, TEST_FREQUENCIES));
        inOrder.verify(mIBinder).linkToDeath(mExternalPnoScanRequestManager, 0);
        inOrder.verify(mCallback).onRegisterSuccess();

        // Removing with a different uid should fail.
        assertFalse(mExternalPnoScanRequestManager.removeRequest(TEST_UID + 1));

        // Removing with the same uid should work.
        assertTrue(mExternalPnoScanRequestManager.removeRequest(TEST_UID));
        inOrder.verify(mCallback).onRemoved(REMOVE_PNO_CALLBACK_UNREGISTERED);
        inOrder.verify(mIBinder).unlinkToDeath(mExternalPnoScanRequestManager, 0);
        assertEquals(Collections.EMPTY_SET,
                mExternalPnoScanRequestManager.getExternalPnoScanSsids());
        assertEquals(Collections.EMPTY_SET,
                mExternalPnoScanRequestManager.getExternalPnoScanFrequencies());
    }

    @Test
    public void testRemoveRequest_afterDelivery() throws RemoteException {
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        InOrder inOrder = inOrder(mCallback, mIBinder, mContext);

        // register a request
        assertTrue(mExternalPnoScanRequestManager.setRequest(TEST_UID, TEST_PACKAGE, mIBinder,
                mCallback, TEST_WIFI_SSIDS, TEST_FREQUENCIES));
        inOrder.verify(mIBinder).linkToDeath(mExternalPnoScanRequestManager, 0);
        inOrder.verify(mCallback).onRegisterSuccess();
        assertEquals(EXPECTED_SSIDS_SET,
                mExternalPnoScanRequestManager.getExternalPnoScanSsids());
        assertEquals(EXPECTED_FREQUENCIES_SET,
                mExternalPnoScanRequestManager.getExternalPnoScanFrequencies());

        // Mock 3 scan results.
        // scanResults[0] is not requested.
        // scanResults[1] is being requested.
        // scanResults[2] is being requested.
        ScanResult scanResult1 = new ScanResult();
        scanResult1.setWifiSsid(WifiSsid.fromString("\"RANDOM_SSID_123\""));
        ScanResult scanResult2 = new ScanResult();
        scanResult2.setWifiSsid(WifiSsid.fromString(TEST_SSID_1));
        ScanResult scanResult3 = new ScanResult();
        scanResult3.setWifiSsid(WifiSsid.fromString(TEST_SSID_2));
        List<ScanDetail> scanDetails = new ArrayList<>();
        ScanDetail scanDetail1 = mock(ScanDetail.class);
        ScanDetail scanDetail2 = mock(ScanDetail.class);
        ScanDetail scanDetail3 = mock(ScanDetail.class);
        when(scanDetail1.getScanResult()).thenReturn(scanResult1);
        when(scanDetail2.getScanResult()).thenReturn(scanResult2);
        when(scanDetail3.getScanResult()).thenReturn(scanResult3);
        scanDetails.add(scanDetail1);
        scanDetails.add(scanDetail2);
        scanDetails.add(scanDetail3);

        List<ScanResult> expectedResults = new ArrayList<>();
        expectedResults.add(scanResult2);
        expectedResults.add(scanResult3);
        mExternalPnoScanRequestManager.onScanResultsAvailable(scanDetails);
        inOrder.verify(mContext).sendBroadcastAsUser(intentArgumentCaptor.capture(), any());
        assertEquals(TEST_PACKAGE, intentArgumentCaptor.getValue().getPackage());
        inOrder.verify(mCallback).onScanResultsAvailable(expectedResults);
        inOrder.verify(mCallback).onRemoved(REMOVE_PNO_CALLBACK_RESULTS_DELIVERED);
        inOrder.verify(mIBinder).unlinkToDeath(mExternalPnoScanRequestManager, 0);
        assertEquals(Collections.EMPTY_SET,
                mExternalPnoScanRequestManager.getExternalPnoScanSsids());
        assertEquals(Collections.EMPTY_SET,
                mExternalPnoScanRequestManager.getExternalPnoScanFrequencies());
    }

    @Test
    public void testRemoveRequest_noDeliveryIfNoMatch() throws RemoteException {
        InOrder inOrder = inOrder(mCallback, mIBinder, mContext);

        // register a request
        assertTrue(mExternalPnoScanRequestManager.setRequest(TEST_UID, TEST_PACKAGE, mIBinder,
                mCallback, TEST_WIFI_SSIDS, TEST_FREQUENCIES));
        inOrder.verify(mIBinder).linkToDeath(mExternalPnoScanRequestManager, 0);
        inOrder.verify(mCallback).onRegisterSuccess();
        assertEquals(EXPECTED_SSIDS_SET,
                mExternalPnoScanRequestManager.getExternalPnoScanSsids());
        assertEquals(EXPECTED_FREQUENCIES_SET,
                mExternalPnoScanRequestManager.getExternalPnoScanFrequencies());

        // Mock a scan result that's not being requested
        ScanResult scanResult1 = new ScanResult();
        scanResult1.setWifiSsid(WifiSsid.fromString("\"RANDOM_SSID_123\""));
        List<ScanDetail> scanDetails = new ArrayList<>();
        ScanDetail scanDetail1 = mock(ScanDetail.class);
        when(scanDetail1.getScanResult()).thenReturn(scanResult1);
        scanDetails.add(scanDetail1);

        // Results should not be delivered, and the request should still be registered.
        mExternalPnoScanRequestManager.onScanResultsAvailable(scanDetails);
        inOrder.verify(mContext, never()).sendBroadcastAsUser(any(), any());
        inOrder.verify(mCallback, never()).onScanResultsAvailable(any());
        inOrder.verify(mCallback, never()).onRemoved(anyInt());
        inOrder.verify(mIBinder, never()).unlinkToDeath(mExternalPnoScanRequestManager, 0);
        assertEquals(EXPECTED_SSIDS_SET,
                mExternalPnoScanRequestManager.getExternalPnoScanSsids());
        assertEquals(EXPECTED_FREQUENCIES_SET,
                mExternalPnoScanRequestManager.getExternalPnoScanFrequencies());
    }

    @Test
    public void testKeepRequest_afterBinderDeath() throws RemoteException {
        InOrder inOrder = inOrder(mCallback, mIBinder);

        // register a request
        assertTrue(mExternalPnoScanRequestManager.setRequest(TEST_UID, TEST_PACKAGE, mIBinder,
                mCallback, TEST_WIFI_SSIDS, TEST_FREQUENCIES));
        inOrder.verify(mIBinder).linkToDeath(mExternalPnoScanRequestManager, 0);
        inOrder.verify(mCallback).onRegisterSuccess();
        assertEquals(EXPECTED_SSIDS_SET,
                mExternalPnoScanRequestManager.getExternalPnoScanSsids());
        assertEquals(EXPECTED_FREQUENCIES_SET,
                mExternalPnoScanRequestManager.getExternalPnoScanFrequencies());

        mExternalPnoScanRequestManager.binderDied();
        mLooper.dispatchAll();
        assertEquals(EXPECTED_SSIDS_SET,
                mExternalPnoScanRequestManager.getExternalPnoScanSsids());
        assertEquals(EXPECTED_FREQUENCIES_SET,
                mExternalPnoScanRequestManager.getExternalPnoScanFrequencies());
    }
}
