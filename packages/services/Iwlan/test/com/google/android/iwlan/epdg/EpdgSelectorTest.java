/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.iwlan.epdg;

import static android.net.DnsResolver.TYPE_A;
import static android.net.DnsResolver.TYPE_AAAA;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import static java.util.stream.Collectors.toList;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.DnsResolver;
import android.net.InetAddresses;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.DataFailCause;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.android.iwlan.ErrorPolicyManager;
import com.google.android.iwlan.IwlanError;
import com.google.android.iwlan.flags.FeatureFlags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class EpdgSelectorTest {

    private static final String TAG = "EpdgSelectorTest";
    private EpdgSelector mEpdgSelector;
    public static final int DEFAULT_SLOT_INDEX = 0;

    private static final byte[] TEST_PCO_NO_DATA = {0x00};
    private static final byte[] TEST_PCO_PLMN_DATA = {0x38, 0x01, 0x24, 0x00};
    private static final byte[] TEST_PCO_IPV4_DATA = {0x38, 0x01, 0x24, 0x7F, 0x00, 0x00, 0x01};
    private static final byte[] TEST_PCO_IPV6_DATA = {
        0x38, 0x01, 0x24, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x01
    };
    private static final String TEST_IP_ADDRESS = "127.0.0.1";
    private static final String TEST_IP_ADDRESS_1 = "127.0.0.2";
    private static final String TEST_IP_ADDRESS_2 = "127.0.0.3";
    private static final String TEST_IP_ADDRESS_3 = "127.0.0.4";
    private static final String TEST_IP_ADDRESS_4 = "127.0.0.5";
    private static final String TEST_IP_ADDRESS_5 = "127.0.0.6";
    private static final String TEST_IP_ADDRESS_6 = "127.0.0.7";
    private static final String TEST_IP_ADDRESS_7 = "127.0.0.8";
    private static final String TEST_IPV6_ADDRESS = "0000:0000:0000:0000:0000:0000:0000:0001";

    private static int testPcoIdIPv6 = 0xFF01;
    private static int testPcoIdIPv4 = 0xFF02;

    private String testPcoString = "testPcoData";
    private byte[] pcoData = testPcoString.getBytes();
    private List<String> ehplmnList = new ArrayList<String>();

    @Mock private Context mMockContext;
    @Mock private Network mMockNetwork;
    @Mock private ErrorPolicyManager mMockErrorPolicyManager;
    @Mock private SubscriptionManager mMockSubscriptionManager;
    @Mock private SubscriptionInfo mMockSubscriptionInfo;
    @Mock private CarrierConfigManager mMockCarrierConfigManager;
    @Mock private TelephonyManager mMockTelephonyManager;
    @Mock private SharedPreferences mMockSharedPreferences;
    @Mock private CellInfoGsm mMockCellInfoGsm;
    @Mock private CellIdentityGsm mMockCellIdentityGsm;
    @Mock private CellInfoWcdma mMockCellInfoWcdma;
    @Mock private CellIdentityWcdma mMockCellIdentityWcdma;
    @Mock private CellInfoLte mMockCellInfoLte;
    @Mock private CellIdentityLte mMockCellIdentityLte;
    @Mock private CellInfoNr mMockCellInfoNr;
    @Mock private CellIdentityNr mMockCellIdentityNr;
    @Mock private DnsResolver mMockDnsResolver;
    @Mock private FeatureFlags mfakeFeatureFlags;

    private PersistableBundle mTestBundle;
    private FakeDns mFakeDns;
    MockitoSession mStaticMockSession;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                mockitoSession()
                        .mockStatic(DnsResolver.class)
                        .mockStatic(ErrorPolicyManager.class)
                        .startMocking();

        when(ErrorPolicyManager.getInstance(mMockContext, DEFAULT_SLOT_INDEX))
                .thenReturn(mMockErrorPolicyManager);
        mEpdgSelector = spy(new EpdgSelector(mMockContext, DEFAULT_SLOT_INDEX, mfakeFeatureFlags));

        when(mMockContext.getSystemService(eq(SubscriptionManager.class)))
                .thenReturn(mMockSubscriptionManager);

        when(mMockSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(anyInt()))
                .thenReturn(mMockSubscriptionInfo);

        when(mMockSubscriptionInfo.getMccString()).thenReturn("311");

        when(mMockSubscriptionInfo.getMncString()).thenReturn("120");

        when(mMockTelephonyManager.getNetworkOperator()).thenReturn("311120");

        when(mMockContext.getSystemService(eq(TelephonyManager.class)))
                .thenReturn(mMockTelephonyManager);

        when(mMockTelephonyManager.createForSubscriptionId(anyInt()))
                .thenReturn(mMockTelephonyManager);

        ehplmnList.add("300120");
        when(mMockTelephonyManager.getEquivalentHomePlmns()).thenReturn(ehplmnList);

        when(mMockTelephonyManager.getSimCountryIso()).thenReturn("ca");

        when(mMockContext.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mMockSharedPreferences);

        when(mMockSharedPreferences.getString(any(), any())).thenReturn("US");

        // Mock carrier configs with test bundle
        mTestBundle = new PersistableBundle();
        mTestBundle.putInt(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_IP_TYPE_PREFERENCE_INT,
                CarrierConfigManager.Iwlan.EPDG_ADDRESS_IPV4_PREFERRED);
        when(mMockContext.getSystemService(eq(CarrierConfigManager.class)))
                .thenReturn(mMockCarrierConfigManager);
        when(mMockCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(mTestBundle);

        mFakeDns = new FakeDns();
        mFakeDns.startMocking();
    }

    @After
    public void cleanUp() throws Exception {
        mStaticMockSession.finishMocking();
        mFakeDns.clearAll();
    }

    @Test
    public void testStaticMethodPass() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
        doReturn(true).when(mEpdgSelector).hasIpv4Address(mMockNetwork);
        doReturn(true).when(mEpdgSelector).hasIpv6Address(mMockNetwork);

        // Set DnsResolver query mock
        final String testStaticAddress = "epdg.epc.mnc088.mcc888.pub.3gppnetwork.org";
        mFakeDns.setAnswer(testStaticAddress, new String[] {TEST_IP_ADDRESS}, TYPE_A);

        // Set carrier config mock
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        mTestBundle.putString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, testStaticAddress);

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithDefaultParams(false /*isEmergency*/);

        InetAddress expectedAddress = InetAddress.getByName(TEST_IP_ADDRESS);

        assertEquals(1, testInetAddresses.size());
        assertEquals(expectedAddress, testInetAddresses.get(0));
    }

    @Test
    public void testStaticMethodDirectIpAddress_noDnsResolution() throws Exception {
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        // Carrier config directly contains the ePDG IP address.
        mTestBundle.putString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, TEST_IP_ADDRESS);

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithDefaultParams(false /*isEmergency*/);

        assertEquals(1, testInetAddresses.size());
        assertEquals(InetAddresses.parseNumericAddress(TEST_IP_ADDRESS), testInetAddresses.get(0));
    }

    @Test
    public void testRoamStaticMethodPass() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
        doReturn(true).when(mEpdgSelector).hasIpv4Address(mMockNetwork);
        doReturn(true).when(mEpdgSelector).hasIpv6Address(mMockNetwork);

        // Set DnsResolver query mock
        final String testRoamStaticAddress = "epdg.epc.mnc088.mcc888.pub.3gppnetwork.org";
        mFakeDns.setAnswer(testRoamStaticAddress, new String[] {TEST_IP_ADDRESS}, TYPE_A);

        // Set carrier config mock
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        mTestBundle.putString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_ROAMING_STRING,
                testRoamStaticAddress);

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithDefaultParams(false /*isEmergency*/);

        InetAddress expectedAddress = InetAddress.getByName(TEST_IP_ADDRESS);

        assertEquals(1, testInetAddresses.size());
        assertEquals(expectedAddress, testInetAddresses.get(0));
    }

    @Test
    public void testPlmnResolutionMethod() throws Exception {
        testPlmnResolutionMethod(false);
    }

    @Test
    public void testPlmnResolutionMethodForEmergency() throws Exception {
        testPlmnResolutionMethod(true);
    }

    @Test
    public void testPlmnResolutionMethodWithNoPlmnInCarrierConfig() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
        doReturn(true).when(mEpdgSelector).hasIpv4Address(mMockNetwork);
        doReturn(true).when(mEpdgSelector).hasIpv6Address(mMockNetwork);

        // setUp() fills default values for mcc-mnc
        String expectedFqdnFromImsi = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        String expectedFqdnFromEhplmn = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";

        mFakeDns.setAnswer(expectedFqdnFromImsi, new String[] {TEST_IP_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(expectedFqdnFromEhplmn, new String[] {TEST_IP_ADDRESS_2}, TYPE_A);

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithDefaultParams(false /*isEmergency*/);

        assertEquals(2, testInetAddresses.size());
        assertTrue(testInetAddresses.contains(InetAddress.getByName(TEST_IP_ADDRESS_1)));
        assertTrue(testInetAddresses.contains(InetAddress.getByName(TEST_IP_ADDRESS_2)));
    }

    private void testPlmnResolutionMethod(boolean isEmergency) throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
        doReturn(true).when(mEpdgSelector).hasIpv4Address(mMockNetwork);
        doReturn(true).when(mEpdgSelector).hasIpv6Address(mMockNetwork);

        String expectedFqdnFromImsi = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        String expectedFqdnFromRplmn = "epdg.epc.mnc121.mcc311.pub.3gppnetwork.org";
        String expectedFqdnFromEhplmn = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        String excludedFqdnFromConfig = "epdg.epc.mnc480.mcc310.pub.3gppnetwork.org";

        when(mMockTelephonyManager.getNetworkOperator()).thenReturn("311121");

        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN});
        mTestBundle.putStringArray(
                CarrierConfigManager.Iwlan.KEY_MCC_MNCS_STRING_ARRAY,
                new String[] {"310-480", "300-120", "311-120", "311-121"});

        mFakeDns.setAnswer(expectedFqdnFromImsi, new String[] {TEST_IP_ADDRESS}, TYPE_A);
        mFakeDns.setAnswer(expectedFqdnFromEhplmn, new String[] {TEST_IP_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(excludedFqdnFromConfig, new String[] {TEST_IP_ADDRESS_2}, TYPE_A);
        mFakeDns.setAnswer("sos." + expectedFqdnFromImsi, new String[] {TEST_IP_ADDRESS_3}, TYPE_A);
        mFakeDns.setAnswer(
                "sos." + expectedFqdnFromEhplmn, new String[] {TEST_IP_ADDRESS_4}, TYPE_A);
        mFakeDns.setAnswer(
                "sos." + excludedFqdnFromConfig, new String[] {TEST_IP_ADDRESS_5}, TYPE_A);
        mFakeDns.setAnswer(expectedFqdnFromRplmn, new String[] {TEST_IP_ADDRESS_6}, TYPE_A);
        mFakeDns.setAnswer(
                "sos." + expectedFqdnFromRplmn, new String[] {TEST_IP_ADDRESS_7}, TYPE_A);

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithDefaultParams(isEmergency);

        if (isEmergency) {
            assertEquals(6, testInetAddresses.size());
            assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_7), testInetAddresses.get(0));
            assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_6), testInetAddresses.get(1));
            assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_3), testInetAddresses.get(2));
            assertEquals(InetAddress.getByName(TEST_IP_ADDRESS), testInetAddresses.get(3));
            assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_4), testInetAddresses.get(4));
            assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_1), testInetAddresses.get(5));
        } else {
            assertEquals(3, testInetAddresses.size());
            assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_6), testInetAddresses.get(0));
            assertEquals(InetAddress.getByName(TEST_IP_ADDRESS), testInetAddresses.get(1));
            assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_1), testInetAddresses.get(2));
        }
    }

    @Test
    public void testPlmnResolutionMethodWithDuplicatedImsiAndEhplmn() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
        doReturn(true).when(mEpdgSelector).hasIpv4Address(mMockNetwork);
        doReturn(true).when(mEpdgSelector).hasIpv6Address(mMockNetwork);

        String fqdnFromEhplmn1 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        String fqdnFromEhplmn2AndImsi = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        String fqdnFromEhplmn3 = "epdg.epc.mnc122.mcc300.pub.3gppnetwork.org";
        String fqdnFromEhplmn4 = "epdg.epc.mnc123.mcc300.pub.3gppnetwork.org";

        when(mMockTelephonyManager.getNetworkOperator()).thenReturn("300121");
        ehplmnList.add("300122");
        ehplmnList.add("300123");

        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN});
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_PLMN_PRIORITY_INT_ARRAY,
                new int[] {
                    CarrierConfigManager.Iwlan.EPDG_PLMN_HPLMN,
                    CarrierConfigManager.Iwlan.EPDG_PLMN_EHPLMN_ALL,
                });

        mFakeDns.setAnswer(fqdnFromEhplmn1, new String[] {TEST_IP_ADDRESS}, TYPE_A);
        mFakeDns.setAnswer(fqdnFromEhplmn2AndImsi, new String[] {TEST_IP_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(fqdnFromEhplmn3, new String[] {TEST_IP_ADDRESS_2}, TYPE_A);
        mFakeDns.setAnswer(fqdnFromEhplmn4, new String[] {TEST_IP_ADDRESS_3}, TYPE_A);

        ArrayList<InetAddress> testInetAddresses = getValidatedServerListWithDefaultParams(false);

        assertEquals(4, testInetAddresses.size());
        assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_1), testInetAddresses.get(0));
        assertEquals(InetAddress.getByName(TEST_IP_ADDRESS), testInetAddresses.get(1));
        assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_2), testInetAddresses.get(2));
        assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_3), testInetAddresses.get(3));
    }

    @Test
    public void testPlmnResolutionMethodWithInvalidLengthPlmns() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
        doReturn(true).when(mEpdgSelector).hasIpv4Address(mMockNetwork);
        doReturn(true).when(mEpdgSelector).hasIpv6Address(mMockNetwork);

        when(mMockSubscriptionInfo.getMccString()).thenReturn("31");
        when(mMockSubscriptionInfo.getMncString()).thenReturn("12");

        when(mMockTelephonyManager.getNetworkOperator()).thenReturn("300");
        ehplmnList.add("3001");
        ehplmnList.add("3");

        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN});
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_PLMN_PRIORITY_INT_ARRAY,
                new int[] {
                    CarrierConfigManager.Iwlan.EPDG_PLMN_RPLMN,
                    CarrierConfigManager.Iwlan.EPDG_PLMN_HPLMN,
                    CarrierConfigManager.Iwlan.EPDG_PLMN_EHPLMN_ALL,
                });

        ArrayList<InetAddress> testInetAddresses = getValidatedServerListWithDefaultParams(false);

        assertEquals(0, testInetAddresses.size());
    }

    @Test
    public void testPlmnResolutionMethodWithInvalidCharacterPlmns() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
        doReturn(true).when(mEpdgSelector).hasIpv4Address(mMockNetwork);
        doReturn(true).when(mEpdgSelector).hasIpv6Address(mMockNetwork);

        when(mMockSubscriptionInfo.getMccString()).thenReturn("a b");
        when(mMockSubscriptionInfo.getMncString()).thenReturn("!@#");

        when(mMockTelephonyManager.getNetworkOperator()).thenReturn("a cde#");
        ehplmnList.add("abcdef");
        ehplmnList.add("1 23456");
        ehplmnList.add("1 2345");

        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN});
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_PLMN_PRIORITY_INT_ARRAY,
                new int[] {
                    CarrierConfigManager.Iwlan.EPDG_PLMN_RPLMN,
                    CarrierConfigManager.Iwlan.EPDG_PLMN_HPLMN,
                    CarrierConfigManager.Iwlan.EPDG_PLMN_EHPLMN_ALL,
                });

        ArrayList<InetAddress> testInetAddresses = getValidatedServerListWithDefaultParams(false);

        assertEquals(0, testInetAddresses.size());
    }

    @Test
    public void testPlmnResolutionMethodWithEmptyPlmns() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
        doReturn(true).when(mEpdgSelector).hasIpv4Address(mMockNetwork);
        doReturn(true).when(mEpdgSelector).hasIpv6Address(mMockNetwork);

        when(mMockSubscriptionInfo.getMccString()).thenReturn(null);
        when(mMockSubscriptionInfo.getMncString()).thenReturn(null);

        when(mMockTelephonyManager.getNetworkOperator()).thenReturn("");
        ehplmnList.add("");

        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN});
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_PLMN_PRIORITY_INT_ARRAY,
                new int[] {
                    CarrierConfigManager.Iwlan.EPDG_PLMN_RPLMN,
                    CarrierConfigManager.Iwlan.EPDG_PLMN_HPLMN,
                    CarrierConfigManager.Iwlan.EPDG_PLMN_EHPLMN_ALL,
                });

        ArrayList<InetAddress> testInetAddresses = getValidatedServerListWithDefaultParams(false);

        assertEquals(0, testInetAddresses.size());
    }

    @Test
    public void testPlmnResolutionMethodWithFirstEhplmn() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
        doReturn(true).when(mEpdgSelector).hasIpv4Address(mMockNetwork);
        doReturn(true).when(mEpdgSelector).hasIpv6Address(mMockNetwork);

        String fqdnFromEhplmn1 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        String fqdnFromEhplmn2 = "epdg.epc.mnc121.mcc300.pub.3gppnetwork.org";
        String fqdnFromEhplmn3 = "epdg.epc.mnc122.mcc300.pub.3gppnetwork.org";
        String fqdnFromEhplmn4 = "epdg.epc.mnc123.mcc300.pub.3gppnetwork.org";

        ehplmnList.add("300121");
        ehplmnList.add("300122");
        ehplmnList.add("300123");

        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN});
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_PLMN_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_PLMN_EHPLMN_FIRST});

        mFakeDns.setAnswer(fqdnFromEhplmn1, new String[] {TEST_IP_ADDRESS}, TYPE_A);
        mFakeDns.setAnswer(fqdnFromEhplmn2, new String[] {TEST_IP_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(fqdnFromEhplmn3, new String[] {TEST_IP_ADDRESS_2}, TYPE_A);
        mFakeDns.setAnswer(fqdnFromEhplmn4, new String[] {TEST_IP_ADDRESS_3}, TYPE_A);

        ArrayList<InetAddress> testInetAddresses = getValidatedServerListWithDefaultParams(false);

        assertEquals(1, testInetAddresses.size());
        assertEquals(InetAddress.getByName(TEST_IP_ADDRESS), testInetAddresses.get(0));
    }

    @Test
    public void testPlmnResolutionMethodWithRplmn() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
        doReturn(true).when(mEpdgSelector).hasIpv4Address(mMockNetwork);
        doReturn(true).when(mEpdgSelector).hasIpv6Address(mMockNetwork);

        String fqdnFromRplmn = "epdg.epc.mnc122.mcc300.pub.3gppnetwork.org";
        String fqdnFromEhplmn1 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        String fqdnFromEhplmn2 = "epdg.epc.mnc121.mcc300.pub.3gppnetwork.org";

        when(mMockTelephonyManager.getNetworkOperator()).thenReturn("300122");
        ehplmnList.add("300121");

        mTestBundle.putStringArray(
                CarrierConfigManager.Iwlan.KEY_MCC_MNCS_STRING_ARRAY,
                new String[] {"310-480", "300-122", "300-121"});

        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN});
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_PLMN_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_PLMN_RPLMN});

        mFakeDns.setAnswer(fqdnFromRplmn, new String[] {TEST_IP_ADDRESS}, TYPE_A);
        mFakeDns.setAnswer(fqdnFromEhplmn1, new String[] {TEST_IP_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(fqdnFromEhplmn2, new String[] {TEST_IP_ADDRESS_2}, TYPE_A);

        ArrayList<InetAddress> testInetAddresses = getValidatedServerListWithDefaultParams(false);

        assertEquals(1, testInetAddresses.size());
        assertEquals(InetAddress.getByName(TEST_IP_ADDRESS), testInetAddresses.get(0));
    }

    @Test
    public void testCarrierConfigStaticAddressList() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
        doReturn(true).when(mEpdgSelector).hasIpv4Address(mMockNetwork);
        doReturn(true).when(mEpdgSelector).hasIpv6Address(mMockNetwork);

        // Set DnsResolver query mock
        final String addr1 = "epdg.epc.mnc480.mcc310.pub.3gppnetwork.org";
        final String addr2 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        final String addr3 = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        final String testStaticAddress = addr1 + "," + addr2 + "," + addr3;

        mFakeDns.setAnswer(addr1, new String[] {TEST_IP_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(addr2, new String[] {TEST_IP_ADDRESS_2}, TYPE_A);
        mFakeDns.setAnswer(addr3, new String[] {TEST_IP_ADDRESS}, TYPE_A);

        // Set carrier config mock
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        mTestBundle.putString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, testStaticAddress);

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithDefaultParams(false /*isEmergency*/);

        assertEquals(3, testInetAddresses.size());
        assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_1), testInetAddresses.get(0));
        assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_2), testInetAddresses.get(1));
        assertEquals(InetAddress.getByName(TEST_IP_ADDRESS), testInetAddresses.get(2));
    }

    private ArrayList<InetAddress> getValidatedServerListWithDefaultParams(boolean isEmergency)
            throws Exception {
        return getValidatedServerListWithIpPreference(
                EpdgSelector.PROTO_FILTER_IPV4V6, EpdgSelector.IPV4_PREFERRED, isEmergency);
    }

    private ArrayList<InetAddress> getValidatedServerListWithIpPreference(
            @EpdgSelector.ProtoFilter int filter,
            @EpdgSelector.EpdgAddressOrder int order,
            boolean isEmergency)
            throws Exception {
        ArrayList<InetAddress> testInetAddresses = new ArrayList<InetAddress>();
        final CountDownLatch latch = new CountDownLatch(1);
        IwlanError ret =
                mEpdgSelector.getValidatedServerList(
                        1234,
                        filter,
                        order,
                        false /* isRoaming */,
                        isEmergency,
                        mMockNetwork,
                        new EpdgSelector.EpdgSelectorCallback() {
                            @Override
                            public void onServerListChanged(
                                    int transactionId, List<InetAddress> validIPList) {
                                assertEquals(transactionId, 1234);

                                for (InetAddress mInetAddress : validIPList) {
                                    testInetAddresses.add(mInetAddress);
                                }
                                Log.d(TAG, "onServerListChanged received");
                                latch.countDown();
                            }

                            @Override
                            public void onError(int transactionId, IwlanError epdgSelectorError) {
                                Log.d(TAG, "onError received");
                                latch.countDown();
                            }
                        });

        assertEquals(ret.getErrorType(), IwlanError.NO_ERROR);
        latch.await(1, TimeUnit.SECONDS);
        return testInetAddresses;
    }

    @Test
    public void testSetPcoData() throws Exception {
        addTestPcoIdsToTestConfigBundle();

        boolean retIPv6 = mEpdgSelector.setPcoData(testPcoIdIPv6, pcoData);
        boolean retIPv4 = mEpdgSelector.setPcoData(testPcoIdIPv4, pcoData);
        boolean retIncorrect = mEpdgSelector.setPcoData(0xFF00, pcoData);

        assertTrue(retIPv6);
        assertTrue(retIPv4);
        assertFalse(retIncorrect);
    }

    @Test
    public void testPcoResolutionMethod() throws Exception {
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PCO});
        addTestPcoIdsToTestConfigBundle();

        mEpdgSelector.clearPcoData();
        assertTrue(mEpdgSelector.setPcoData(testPcoIdIPv6, TEST_PCO_IPV6_DATA));
        assertTrue(mEpdgSelector.setPcoData(testPcoIdIPv4, TEST_PCO_IPV4_DATA));

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithDefaultParams(false /* isEmergency */);

        assertEquals(2, testInetAddresses.size());
        assertTrue(testInetAddresses.contains(InetAddress.getByName(TEST_IP_ADDRESS)));
        assertTrue(testInetAddresses.contains(InetAddress.getByName(TEST_IPV6_ADDRESS)));
    }

    @Test
    public void testPcoResolutionMethodWithNoPcoData() throws Exception {
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PCO});
        addTestPcoIdsToTestConfigBundle();

        mEpdgSelector.clearPcoData();
        assertTrue(mEpdgSelector.setPcoData(testPcoIdIPv6, TEST_PCO_NO_DATA));
        assertTrue(mEpdgSelector.setPcoData(testPcoIdIPv4, TEST_PCO_NO_DATA));

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithDefaultParams(false /* isEmergency */);

        assertEquals(0, testInetAddresses.size());
    }

    @Test
    public void testPcoResolutionMethodWithOnlyPlmnData() throws Exception {
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PCO});
        addTestPcoIdsToTestConfigBundle();

        mEpdgSelector.clearPcoData();
        assertTrue(mEpdgSelector.setPcoData(testPcoIdIPv6, TEST_PCO_PLMN_DATA));
        assertTrue(mEpdgSelector.setPcoData(testPcoIdIPv4, TEST_PCO_PLMN_DATA));

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithDefaultParams(false /* isEmergency */);

        assertEquals(0, testInetAddresses.size());
    }

    private void addTestPcoIdsToTestConfigBundle() {
        mTestBundle.putInt(CarrierConfigManager.Iwlan.KEY_EPDG_PCO_ID_IPV6_INT, testPcoIdIPv6);
        mTestBundle.putInt(CarrierConfigManager.Iwlan.KEY_EPDG_PCO_ID_IPV4_INT, testPcoIdIPv4);
    }

    @Test
    public void testCellularResolutionMethod() throws Exception {
        testCellularResolutionMethod(false);
    }

    @Test
    public void testCellularResolutionMethodForEmergency() throws Exception {
        testCellularResolutionMethod(true);
    }

    private void testCellularResolutionMethod(boolean isEmergency) throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        int testMcc = 311;
        int testMnc = 120;
        String testMccString = "311";
        String testMncString = "120";
        int testLac = 65484;
        int testTac = 65484;
        int testNrTac = 16764074;

        List<CellInfo> fakeCellInfoArray = new ArrayList<CellInfo>();

        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_CELLULAR_LOC});

        // Set cell info mock
        fakeCellInfoArray.add(mMockCellInfoGsm);
        when(mMockCellInfoGsm.isRegistered()).thenReturn(true);
        when(mMockCellInfoGsm.getCellIdentity()).thenReturn(mMockCellIdentityGsm);
        when(mMockCellIdentityGsm.getMcc()).thenReturn(testMcc);
        when(mMockCellIdentityGsm.getMnc()).thenReturn(testMnc);
        when(mMockCellIdentityGsm.getLac()).thenReturn(testLac);

        fakeCellInfoArray.add(mMockCellInfoWcdma);
        when(mMockCellInfoWcdma.isRegistered()).thenReturn(true);
        when(mMockCellInfoWcdma.getCellIdentity()).thenReturn(mMockCellIdentityWcdma);
        when(mMockCellIdentityWcdma.getMcc()).thenReturn(testMcc);
        when(mMockCellIdentityWcdma.getMnc()).thenReturn(testMnc);
        when(mMockCellIdentityWcdma.getLac()).thenReturn(testLac);

        fakeCellInfoArray.add(mMockCellInfoLte);
        when(mMockCellInfoLte.isRegistered()).thenReturn(true);
        when(mMockCellInfoLte.getCellIdentity()).thenReturn(mMockCellIdentityLte);
        when(mMockCellIdentityLte.getMcc()).thenReturn(testMcc);
        when(mMockCellIdentityLte.getMnc()).thenReturn(testMnc);
        when(mMockCellIdentityLte.getTac()).thenReturn(testTac);

        fakeCellInfoArray.add(mMockCellInfoNr);
        when(mMockCellInfoNr.isRegistered()).thenReturn(true);
        when(mMockCellInfoNr.getCellIdentity()).thenReturn(mMockCellIdentityNr);
        when(mMockCellIdentityNr.getMccString()).thenReturn(testMccString);
        when(mMockCellIdentityNr.getMncString()).thenReturn(testMncString);
        when(mMockCellIdentityNr.getTac()).thenReturn(testNrTac);

        when(mMockTelephonyManager.getAllCellInfo()).thenReturn(fakeCellInfoArray);

        setAnswerForCellularMethod(isEmergency, 311, 120);
        setAnswerForCellularMethod(isEmergency, 300, 120);

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithDefaultParams(isEmergency);

        assertEquals(3, testInetAddresses.size());
        assertEquals(InetAddress.getByName(TEST_IP_ADDRESS), testInetAddresses.get(0));
        assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_1), testInetAddresses.get(1));
        assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_2), testInetAddresses.get(2));
    }

    @Test
    public void testTemporaryExcludedIpAddressWhenDisabledExcludeFailedIp() throws Exception {
        doReturn(false).when(mfakeFeatureFlags).epdgSelectionExcludeFailedIpAddress();
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
        doReturn(true).when(mEpdgSelector).hasIpv4Address(mMockNetwork);
        doReturn(true).when(mEpdgSelector).hasIpv6Address(mMockNetwork);

        String fqdnFromRplmn = "epdg.epc.mnc122.mcc300.pub.3gppnetwork.org";
        final String staticAddr = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";

        when(mMockTelephonyManager.getNetworkOperator()).thenReturn("300122");
        mTestBundle.putStringArray(
                CarrierConfigManager.Iwlan.KEY_MCC_MNCS_STRING_ARRAY, new String[] {"300-122"});

        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {
                    CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN,
                    CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC
                });
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_PLMN_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_PLMN_RPLMN});

        mTestBundle.putString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, staticAddr);

        mFakeDns.setAnswer(fqdnFromRplmn, new String[] {TEST_IP_ADDRESS}, TYPE_A);
        mFakeDns.setAnswer(staticAddr, new String[] {TEST_IP_ADDRESS_1, TEST_IPV6_ADDRESS}, TYPE_A);

        ArrayList<InetAddress> testInetAddresses = getValidatedServerListWithDefaultParams(false);
        assertArrayEquals(
                List.of(
                                InetAddress.getByName(TEST_IP_ADDRESS),
                                InetAddress.getByName(TEST_IP_ADDRESS_1),
                                InetAddress.getByName(TEST_IPV6_ADDRESS))
                        .toArray(),
                testInetAddresses.toArray());

        mEpdgSelector.onEpdgConnectionFailed(InetAddress.getByName(TEST_IP_ADDRESS));
        // Flag disabled should not affect the result
        testInetAddresses = getValidatedServerListWithDefaultParams(false);
        assertArrayEquals(
                List.of(
                                InetAddress.getByName(TEST_IP_ADDRESS),
                                InetAddress.getByName(TEST_IP_ADDRESS_1),
                                InetAddress.getByName(TEST_IPV6_ADDRESS))
                        .toArray(),
                testInetAddresses.toArray());

        mEpdgSelector.onEpdgConnectedSuccessfully();
        // Flag disabled should not affect the result
        testInetAddresses = getValidatedServerListWithDefaultParams(false);
        assertArrayEquals(
                List.of(
                                InetAddress.getByName(TEST_IP_ADDRESS),
                                InetAddress.getByName(TEST_IP_ADDRESS_1),
                                InetAddress.getByName(TEST_IPV6_ADDRESS))
                        .toArray(),
                testInetAddresses.toArray());
    }

    @Test
    public void testTemporaryExcludedIpAddressWhenEnabledExcludeFailedIp() throws Exception {
        doReturn(true).when(mfakeFeatureFlags).epdgSelectionExcludeFailedIpAddress();
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
        doReturn(true).when(mEpdgSelector).hasIpv4Address(mMockNetwork);
        doReturn(true).when(mEpdgSelector).hasIpv6Address(mMockNetwork);

        final String fqdnFromRplmn = "epdg.epc.mnc122.mcc300.pub.3gppnetwork.org";
        final String staticAddr = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";

        when(mMockTelephonyManager.getNetworkOperator()).thenReturn("300122");
        mTestBundle.putStringArray(
                CarrierConfigManager.Iwlan.KEY_MCC_MNCS_STRING_ARRAY, new String[] {"300-122"});

        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {
                    CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN,
                    CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC
                });
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_PLMN_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_PLMN_RPLMN});

        mTestBundle.putString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, staticAddr);

        mFakeDns.setAnswer(fqdnFromRplmn, new String[] {TEST_IP_ADDRESS}, TYPE_A);
        mFakeDns.setAnswer(staticAddr, new String[] {TEST_IP_ADDRESS_1, TEST_IPV6_ADDRESS}, TYPE_A);

        ArrayList<InetAddress> testInetAddresses = getValidatedServerListWithDefaultParams(false);
        assertArrayEquals(
                List.of(
                                InetAddress.getByName(TEST_IP_ADDRESS),
                                InetAddress.getByName(TEST_IP_ADDRESS_1),
                                InetAddress.getByName(TEST_IPV6_ADDRESS))
                        .toArray(),
                testInetAddresses.toArray());

        mEpdgSelector.onEpdgConnectionFailed(InetAddress.getByName(TEST_IP_ADDRESS));
        testInetAddresses = getValidatedServerListWithDefaultParams(false);
        assertArrayEquals(
                List.of(
                                InetAddress.getByName(TEST_IP_ADDRESS_1),
                                InetAddress.getByName(TEST_IPV6_ADDRESS))
                        .toArray(),
                testInetAddresses.toArray());

        // Reset temporary excluded ip addresses
        mEpdgSelector.onEpdgConnectedSuccessfully();
        testInetAddresses = getValidatedServerListWithDefaultParams(false);
        assertArrayEquals(
                List.of(
                                InetAddress.getByName(TEST_IP_ADDRESS),
                                InetAddress.getByName(TEST_IP_ADDRESS_1),
                                InetAddress.getByName(TEST_IPV6_ADDRESS))
                        .toArray(),
                testInetAddresses.toArray());

        mEpdgSelector.onEpdgConnectionFailed(InetAddress.getByName(TEST_IP_ADDRESS));
        testInetAddresses = getValidatedServerListWithDefaultParams(false);
        assertArrayEquals(
                List.of(
                                InetAddress.getByName(TEST_IP_ADDRESS_1),
                                InetAddress.getByName(TEST_IPV6_ADDRESS))
                        .toArray(),
                testInetAddresses.toArray());

        mEpdgSelector.onEpdgConnectionFailed(InetAddress.getByName(TEST_IPV6_ADDRESS));
        testInetAddresses = getValidatedServerListWithDefaultParams(false);
        assertArrayEquals(
                List.of(InetAddress.getByName(TEST_IP_ADDRESS_1)).toArray(),
                testInetAddresses.toArray());

        mEpdgSelector.onEpdgConnectionFailed(InetAddress.getByName(TEST_IP_ADDRESS_1));
        // All ip addresses removed, should reset excluded address
        testInetAddresses = getValidatedServerListWithDefaultParams(false);
        assertArrayEquals(
                List.of(
                                InetAddress.getByName(TEST_IP_ADDRESS),
                                InetAddress.getByName(TEST_IP_ADDRESS_1),
                                InetAddress.getByName(TEST_IPV6_ADDRESS))
                        .toArray(),
                testInetAddresses.toArray());

        mEpdgSelector.onEpdgConnectionFailed(InetAddress.getByName(TEST_IP_ADDRESS_1));
        testInetAddresses = getValidatedServerListWithDefaultParams(false);
        assertArrayEquals(
                List.of(
                                InetAddress.getByName(TEST_IP_ADDRESS),
                                InetAddress.getByName(TEST_IPV6_ADDRESS))
                        .toArray(),
                testInetAddresses.toArray());

        // When the original result changed
        mFakeDns.setAnswer(staticAddr, new String[] {TEST_IP_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(fqdnFromRplmn, new String[] {TEST_IP_ADDRESS_3}, TYPE_A);
        testInetAddresses = getValidatedServerListWithDefaultParams(false);
        assertArrayEquals(
                List.of(InetAddress.getByName(TEST_IP_ADDRESS_3)).toArray(),
                testInetAddresses.toArray());

        mEpdgSelector.onEpdgConnectionFailed(InetAddress.getByName(TEST_IP_ADDRESS_3));
        // It should also reset the excluded list once all ip addresses are excluded
        testInetAddresses = getValidatedServerListWithDefaultParams(false);
        assertArrayEquals(
                List.of(
                                InetAddress.getByName(TEST_IP_ADDRESS_3),
                                InetAddress.getByName(TEST_IP_ADDRESS_1))
                        .toArray(),
                testInetAddresses.toArray());
    }

    private void setAnswerForCellularMethod(boolean isEmergency, int mcc, int mnc)
            throws Exception {
        String expectedFqdn1 =
                (isEmergency)
                        ? "lacffcc.sos.epdg.epc.mnc" + mnc + ".mcc" + mcc + ".pub.3gppnetwork.org"
                        : "lacffcc.epdg.epc.mnc" + mnc + ".mcc" + mcc + ".pub.3gppnetwork.org";
        String expectedFqdn2 =
                (isEmergency)
                        ? "tac-lbcc.tac-hbff.tac.sos.epdg.epc.mnc"
                                + mnc
                                + ".mcc"
                                + mcc
                                + ".pub.3gppnetwork.org"
                        : "tac-lbcc.tac-hbff.tac.epdg.epc.mnc"
                                + mnc
                                + ".mcc"
                                + mcc
                                + ".pub.3gppnetwork.org";
        String expectedFqdn3 =
                (isEmergency)
                        ? "tac-lbaa.tac-mbcc.tac-hbff.5gstac.sos.epdg.epc.mnc"
                                + mnc
                                + ".mcc"
                                + mcc
                                + ".pub.3gppnetwork.org"
                        : "tac-lbaa.tac-mbcc.tac-hbff.5gstac.epdg.epc.mnc"
                                + mnc
                                + ".mcc"
                                + mcc
                                + ".pub.3gppnetwork.org";

        mFakeDns.setAnswer(expectedFqdn1, new String[] {TEST_IP_ADDRESS}, TYPE_A);
        mFakeDns.setAnswer(expectedFqdn2, new String[] {TEST_IP_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(expectedFqdn3, new String[] {TEST_IP_ADDRESS_2}, TYPE_A);
    }

    @Test
    public void testGetValidatedServerListIpv4Preferred() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
        doReturn(true).when(mEpdgSelector).hasIpv4Address(mMockNetwork);
        doReturn(true).when(mEpdgSelector).hasIpv6Address(mMockNetwork);

        final String addr1 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        final String addr2 = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        final String testStaticAddress = addr1 + "," + addr2;

        mFakeDns.setAnswer(addr1, new String[] {TEST_IP_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(addr2, new String[] {TEST_IPV6_ADDRESS}, TYPE_AAAA);

        // Set carrier config mock
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        mTestBundle.putString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, testStaticAddress);

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithIpPreference(
                        EpdgSelector.PROTO_FILTER_IPV4V6,
                        EpdgSelector.IPV4_PREFERRED,
                        false /*isEmergency*/);

        assertEquals(2, testInetAddresses.size());
        assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_1), testInetAddresses.get(0));
        assertEquals(InetAddress.getByName(TEST_IPV6_ADDRESS), testInetAddresses.get(1));
    }

    @Test
    public void testGetValidatedServerListIpv6Preferred() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
        doReturn(true).when(mEpdgSelector).hasIpv4Address(mMockNetwork);
        doReturn(true).when(mEpdgSelector).hasIpv6Address(mMockNetwork);

        final String addr1 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        final String addr2 = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        final String testStaticAddress = addr1 + "," + addr2;

        mFakeDns.setAnswer(addr1, new String[] {TEST_IP_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(addr2, new String[] {TEST_IPV6_ADDRESS}, TYPE_AAAA);

        // Set carrier config mock
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        mTestBundle.putString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, testStaticAddress);

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithIpPreference(
                        EpdgSelector.PROTO_FILTER_IPV4V6,
                        EpdgSelector.IPV6_PREFERRED,
                        false /*isEmergency*/);

        assertEquals(2, testInetAddresses.size());
        assertEquals(InetAddress.getByName(TEST_IPV6_ADDRESS), testInetAddresses.get(0));
        assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_1), testInetAddresses.get(1));
    }

    @Test
    public void testGetValidatedServerListIpv4Only() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
        doReturn(true).when(mEpdgSelector).hasIpv4Address(mMockNetwork);
        doReturn(true).when(mEpdgSelector).hasIpv6Address(mMockNetwork);

        final String addr1 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        final String addr2 = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        final String testStaticAddress = addr1 + "," + addr2;

        mFakeDns.setAnswer(addr1, new String[] {TEST_IP_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(addr2, new String[] {TEST_IPV6_ADDRESS}, TYPE_AAAA);

        // Set carrier config mock
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        mTestBundle.putString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, testStaticAddress);

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithIpPreference(
                        EpdgSelector.PROTO_FILTER_IPV4,
                        EpdgSelector.SYSTEM_PREFERRED,
                        false /*isEmergency*/);

        assertEquals(1, testInetAddresses.size());
        assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_1), testInetAddresses.get(0));
    }

    @Test
    public void testGetValidatedServerListIpv4OnlyCongestion() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
        doReturn(true).when(mEpdgSelector).hasIpv4Address(mMockNetwork);
        doReturn(true).when(mEpdgSelector).hasIpv6Address(mMockNetwork);

        when(mMockErrorPolicyManager.getMostRecentDataFailCause())
                .thenReturn(DataFailCause.IWLAN_CONGESTION);
        when(mMockErrorPolicyManager.getCurrentFqdnIndex(anyInt())).thenReturn(0);

        String expectedFqdnFromHplmn = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        String expectedFqdnFromEHplmn = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        String expectedFqdnFromConfig = "epdg.epc.mnc480.mcc310.pub.3gppnetwork.org";

        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN});
        mTestBundle.putStringArray(
                CarrierConfigManager.Iwlan.KEY_MCC_MNCS_STRING_ARRAY,
                new String[] {"310-480", "300-120", "311-120"});

        mFakeDns.setAnswer(expectedFqdnFromHplmn, new String[] {TEST_IPV6_ADDRESS}, TYPE_AAAA);
        mFakeDns.setAnswer(expectedFqdnFromEHplmn, new String[] {TEST_IP_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(expectedFqdnFromConfig, new String[] {TEST_IP_ADDRESS_2}, TYPE_A);

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithIpPreference(
                        EpdgSelector.PROTO_FILTER_IPV4,
                        EpdgSelector.SYSTEM_PREFERRED,
                        false /*isEmergency*/);

        assertEquals(1, testInetAddresses.size());
        assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_1), testInetAddresses.get(0));
    }

    @Test
    public void testGetValidatedServerListIpv6Only() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
        doReturn(true).when(mEpdgSelector).hasIpv4Address(mMockNetwork);
        doReturn(true).when(mEpdgSelector).hasIpv6Address(mMockNetwork);

        final String addr1 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        final String addr2 = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        final String testStaticAddress = addr1 + "," + addr2;

        mFakeDns.setAnswer(addr1, new String[] {TEST_IP_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(addr2, new String[] {TEST_IPV6_ADDRESS}, TYPE_AAAA);

        // Set carrier config mock
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        mTestBundle.putString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, testStaticAddress);

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithIpPreference(
                        EpdgSelector.PROTO_FILTER_IPV6,
                        EpdgSelector.SYSTEM_PREFERRED,
                        false /*isEmergency*/);

        assertEquals(1, testInetAddresses.size());
        assertEquals(InetAddress.getByName(TEST_IPV6_ADDRESS), testInetAddresses.get(0));
    }

    @Test
    public void testGetValidatedServerListSystemPreferred() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);
        doReturn(true).when(mEpdgSelector).hasIpv4Address(mMockNetwork);
        doReturn(true).when(mEpdgSelector).hasIpv6Address(mMockNetwork);

        final String addr1 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        final String addr2 = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        final String addr3 = "epdg.epc.mnc120.mcc312.pub.3gppnetwork.org";
        final String testStaticAddress = addr1 + "," + addr2 + "," + addr3;

        mFakeDns.setAnswer(addr1, new String[] {TEST_IP_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(addr2, new String[] {TEST_IPV6_ADDRESS}, TYPE_AAAA);
        mFakeDns.setAnswer(addr3, new String[] {TEST_IP_ADDRESS_2}, TYPE_A);

        // Set carrier config mock
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        mTestBundle.putString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, testStaticAddress);

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithIpPreference(
                        EpdgSelector.PROTO_FILTER_IPV4V6,
                        EpdgSelector.SYSTEM_PREFERRED,
                        false /*isEmergency*/);

        assertEquals(3, testInetAddresses.size());
        assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_1), testInetAddresses.get(0));
        assertEquals(InetAddress.getByName(TEST_IPV6_ADDRESS), testInetAddresses.get(1));
        assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_2), testInetAddresses.get(2));
    }

    /**
     * Fakes DNS responses.
     *
     * <p>Allows test methods to configure the IP addresses that will be resolved by
     * Network#getAllByName and by DnsResolver#query.
     */
    class FakeDns {
        /** Data class to record the Dns entry. */
        class DnsEntry {
            final String mHostname;
            final int mType;
            final List<InetAddress> mAddresses;

            DnsEntry(String host, int type, List<InetAddress> addr) {
                mHostname = host;
                mType = type;
                mAddresses = addr;
            }
            // Full match or partial match that target host contains the entry hostname to support
            // random private dns probe hostname.
            private boolean matches(String hostname, int type) {
                return hostname.equals(mHostname) && type == mType;
            }
        }

        private final List<DnsEntry> mAnswers = new ArrayList<>();

        /** Clears all DNS entries. */
        private synchronized void clearAll() {
            mAnswers.clear();
        }

        /** Returns the answer for a given name and type. */
        private synchronized List<InetAddress> getAnswer(String hostname, int type) {
            return mAnswers.stream()
                    .filter(e -> e.matches(hostname, type))
                    .map(answer -> answer.mAddresses)
                    .findFirst()
                    .orElse(List.of());
        }

        /** Sets the answer for a given name and type. */
        private synchronized void setAnswer(String hostname, String[] answer, int type)
                throws UnknownHostException {
            DnsEntry record = new DnsEntry(hostname, type, generateAnswer(answer));
            // Remove the existing one.
            mAnswers.removeIf(entry -> entry.matches(hostname, type));
            // Add or replace a new record.
            mAnswers.add(record);
        }

        private List<InetAddress> generateAnswer(String[] answer) {
            if (answer == null) return new ArrayList<>();
            return Arrays.stream(answer)
                    .map(addr -> InetAddresses.parseNumericAddress(addr))
                    .collect(toList());
        }

        // Regardless of the type, depends on what the responses contained in the network.
        private List<InetAddress> queryIpv4(String hostname) {
            return getAnswer(hostname, TYPE_A);
        }

        // Regardless of the type, depends on what the responses contained in the network.
        private List<InetAddress> queryIpv6(String hostname) {
            return getAnswer(hostname, TYPE_AAAA);
        }

        // Regardless of the type, depends on what the responses contained in the network.
        private List<InetAddress> queryAllTypes(String hostname) {
            List<InetAddress> answer = new ArrayList<>();
            answer.addAll(queryIpv4(hostname));
            answer.addAll(queryIpv6(hostname));
            return answer;
        }

        private void addAllIfNotNull(List<InetAddress> list, List<InetAddress> c) {
            if (c != null) {
                list.addAll(c);
            }
        }

        /** Starts mocking DNS queries. */
        private void startMocking() throws UnknownHostException {
            // 5-arg DnsResolver.query()
            doAnswer(
                            invocation -> {
                                return mockQuery(
                                        invocation,
                                        1 /* posHostname */,
                                        -1 /* posType */,
                                        3 /* posExecutor */,
                                        5 /* posCallback */);
                            })
                    .when(mMockDnsResolver)
                    .query(any(), anyString(), anyInt(), any(), any(), any());

            // 6-arg DnsResolver.query() with explicit query type (IPv4 or v6).
            doAnswer(
                            invocation -> {
                                return mockQuery(
                                        invocation,
                                        1 /* posHostname */,
                                        2 /* posType */,
                                        4 /* posExecutor */,
                                        6 /* posCallback */);
                            })
                    .when(mMockDnsResolver)
                    .query(any(), anyString(), anyInt(), anyInt(), any(), any(), any());
        }

        // Mocking queries on DnsResolver#query.
        private Answer mockQuery(
                InvocationOnMock invocation,
                int posHostname,
                int posType,
                int posExecutor,
                int posCallback) {
            String hostname = invocation.getArgument(posHostname);
            Executor executor = invocation.getArgument(posExecutor);
            DnsResolver.Callback<List<InetAddress>> callback = invocation.getArgument(posCallback);
            List<InetAddress> answer;

            switch (posType) {
                case TYPE_A:
                    answer = queryIpv4(hostname);
                    break;
                case TYPE_AAAA:
                    answer = queryIpv6(hostname);
                    break;
                default:
                    answer = queryAllTypes(hostname);
            }

            if (answer != null && answer.size() > 0) {
                new Handler(Looper.getMainLooper())
                        .post(
                                () -> {
                                    executor.execute(() -> callback.onAnswer(answer, 0));
                                });
            }
            // If no answers, do nothing. sendDnsProbeWithTimeout will time out and throw UHE.
            return null;
        }
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void testMultipleBackToBackSetupDataCallRequest() throws Exception {
        when(mfakeFeatureFlags.preventEpdgSelectionThreadsExhausted()).thenReturn(true);
        EpdgSelector epdgSelector =
                new EpdgSelector(mMockContext, DEFAULT_SLOT_INDEX, mfakeFeatureFlags);
        Runnable runnable = mock(Runnable.class);
        // Prefetch
        epdgSelector.trySubmitEpdgSelectionExecutor(runnable, true, false);
        // First set up data call
        epdgSelector.trySubmitEpdgSelectionExecutor(runnable, false, false);
        // Second set up data call
        epdgSelector.trySubmitEpdgSelectionExecutor(runnable, false, false);
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void testBackToBackSetupDataCallRequest() throws Exception {
        when(mfakeFeatureFlags.preventEpdgSelectionThreadsExhausted()).thenReturn(false);
        EpdgSelector epdgSelector =
                new EpdgSelector(mMockContext, DEFAULT_SLOT_INDEX, mfakeFeatureFlags);
        Runnable runnable = mock(Runnable.class);
        // Prefetch
        epdgSelector.trySubmitEpdgSelectionExecutor(runnable, true, false);
        // First set up data call
        epdgSelector.trySubmitEpdgSelectionExecutor(runnable, false, false);
        // Second set up data call request exhausts the thread pool
        assertThrows(
                RejectedExecutionException.class,
                () -> epdgSelector.trySubmitEpdgSelectionExecutor(runnable, false, false));
    }
}
