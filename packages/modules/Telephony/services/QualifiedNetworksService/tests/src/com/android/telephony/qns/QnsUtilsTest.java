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

package com.android.telephony.qns;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.net.NetworkCapabilities;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(JUnit4.class)
public class QnsUtilsTest extends QnsTest {

    private static final String HANDOVER_POLICY_1 =
            "source=GERAN|UTRAN|NGRAN, target=IWLAN, type=disallowed,"
                    + " capabilities=IMS|eims|MMS|cbs|xcap";
    private static final String HANDOVER_POLICY_2 =
            "source=IWLAN, target=GERAN|UTRAN|EUTRAN|NGRAN, roaming=true, type=disallowed,"
                    + " capabilities=IMS|eims|MMS|cbs|xcap";
    private static final String FALLBACK_RULE0 = "cause=321~378|1503, time=60000, preference=cell";
    private static final String FALLBACK_RULE1 = "cause=232|267|350~380|1503, time=90000";
    PersistableBundle mTestBundle;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        super.setUp();
        mTestBundle = new PersistableBundle();
        doReturn(mMockSubscriptionInfo, mMockSubscriptionInfo, null)
                .when(mMockSubscriptionManager)
                .getActiveSubscriptionInfoForSimSlotIndex(anyInt());
        doReturn(2, 1).when(mMockSubscriptionInfo).getSubscriptionId();
    }

    @Test
    public void testGetStringAccessNetworkTypes() {
        assertEquals("[empty]", QnsUtils.getStringAccessNetworkTypes(new ArrayList<>()));

        Integer[] accessNetworks =
                new Integer[] {
                    AccessNetworkConstants.AccessNetworkType.EUTRAN,
                    AccessNetworkConstants.AccessNetworkType.IWLAN,
                    AccessNetworkConstants.AccessNetworkType.NGRAN
                };
        String output =
                String.join(
                        "|",
                        AccessNetworkConstants.AccessNetworkType.toString(
                                AccessNetworkConstants.AccessNetworkType.EUTRAN),
                        AccessNetworkConstants.AccessNetworkType.toString(
                                AccessNetworkConstants.AccessNetworkType.IWLAN),
                        AccessNetworkConstants.AccessNetworkType.toString(
                                AccessNetworkConstants.AccessNetworkType.NGRAN));
        assertEquals(output, QnsUtils.getStringAccessNetworkTypes(List.of(accessNetworks)));

        accessNetworks =
                new Integer[] {
                    AccessNetworkConstants.AccessNetworkType.EUTRAN,
                    AccessNetworkConstants.AccessNetworkType.NGRAN,
                    AccessNetworkConstants.AccessNetworkType.UTRAN
                };
        output =
                String.join(
                        "|",
                        AccessNetworkConstants.AccessNetworkType.toString(
                                AccessNetworkConstants.AccessNetworkType.EUTRAN),
                        AccessNetworkConstants.AccessNetworkType.toString(
                                AccessNetworkConstants.AccessNetworkType.NGRAN),
                        AccessNetworkConstants.AccessNetworkType.toString(
                                AccessNetworkConstants.AccessNetworkType.UTRAN));
        assertEquals(output, QnsUtils.getStringAccessNetworkTypes(List.of(accessNetworks)));
    }

    @Test
    public void testGetSubId() {
        assertEquals(2, QnsUtils.getSubId(sMockContext, 0));
        assertEquals(1, QnsUtils.getSubId(sMockContext, 0));
        assertEquals(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID, QnsUtils.getSubId(sMockContext, 0));
    }

    @Ignore(value = "Static implementation of SubscriptionManager")
    public void testIsDefaultDataSubs() {
        // default subId is always -1
        assertFalse(QnsUtils.isDefaultDataSubs(2));
        assertFalse(QnsUtils.isDefaultDataSubs(5));
    }

    @Test
    public void testGetSystemElapsedRealTime() {
        long elapsedRealTime = SystemClock.elapsedRealtime();
        assertTrue(QnsUtils.getSystemElapsedRealTime() >= elapsedRealTime);
        assertFalse(QnsUtils.getSystemElapsedRealTime() - elapsedRealTime > 100);
    }

    @Test
    public void testIsCrossSimCallingEnabled() {
        when(mMockQnsImsManager.isCrossSimCallingEnabled()).thenReturn(true, false);
        assertTrue(QnsUtils.isCrossSimCallingEnabled(mMockQnsImsManager));
        assertFalse(QnsUtils.isCrossSimCallingEnabled(mMockQnsImsManager));
    }

    @Test
    public void testIsWfcEnabled_Home() {
        when(mMockQnsImsManager.isWfcEnabledByPlatform()).thenReturn(true, false, true);
        when(mMockQnsImsManager.isWfcEnabledByUser()).thenReturn(true, true, false, true);
        when(mMockQnsImsManager.isWfcProvisionedOnDevice()).thenReturn(true, true, true, false);
        assertTrue(
                QnsUtils.isWfcEnabled(
                        mMockQnsImsManager, mMockQnsProvisioningListener, false)); // all OK
        assertFalse(
                QnsUtils.isWfcEnabled(
                        mMockQnsImsManager,
                        mMockQnsProvisioningListener,
                        false)); // wfc disabled by platform
        assertFalse(
                QnsUtils.isWfcEnabled(
                        mMockQnsImsManager,
                        mMockQnsProvisioningListener,
                        false)); // wfc disabled by user
        assertFalse(
                QnsUtils.isWfcEnabled(
                        mMockQnsImsManager,
                        mMockQnsProvisioningListener,
                        false)); // wfc provisioning false
    }

    @Test
    public void testIsWfcEnabled_Roaming() {
        when(mMockQnsImsManager.isWfcEnabledByPlatform()).thenReturn(true);
        when(mMockQnsImsManager.isWfcEnabledByUser()).thenReturn(true);
        when(mMockQnsImsManager.isWfcProvisionedOnDevice()).thenReturn(true);

        when(mMockQnsImsManager.isWfcRoamingEnabledByUser()).thenReturn(true, true, false, true);
        when(mMockQnsProvisioningListener.getLastProvisioningWfcRoamingEnabledInfo())
                .thenReturn(true, true, false);
        assertTrue(
                QnsUtils.isWfcEnabled(
                        mMockQnsImsManager, mMockQnsProvisioningListener, true)); // exception
        assertTrue(
                QnsUtils.isWfcEnabled(
                        mMockQnsImsManager, mMockQnsProvisioningListener, true)); // all OK
        assertFalse(
                QnsUtils.isWfcEnabled(
                        mMockQnsImsManager,
                        mMockQnsProvisioningListener,
                        true)); // wfc roaming disabled by user
        assertFalse(
                QnsUtils.isWfcEnabled(
                        mMockQnsImsManager,
                        mMockQnsProvisioningListener,
                        true)); // wfc roaming provisioning is false
    }

    @Test
    public void testGetWfcMode() {
        when(mMockQnsImsManager.getWfcMode(true)).thenReturn(0, 1, 2);
        when(mMockQnsImsManager.getWfcMode(false)).thenReturn(0, 1, 2);
        assertEquals(0, QnsUtils.getWfcMode(mMockQnsImsManager, true));
        assertEquals(1, QnsUtils.getWfcMode(mMockQnsImsManager, true));
        assertEquals(2, QnsUtils.getWfcMode(mMockQnsImsManager, true));
        assertEquals(0, QnsUtils.getWfcMode(mMockQnsImsManager, false));
        assertEquals(1, QnsUtils.getWfcMode(mMockQnsImsManager, false));
        assertEquals(2, QnsUtils.getWfcMode(mMockQnsImsManager, false));
    }

    @Test
    public void testGetCellularAccessNetworkType() {
        assertEquals(
                AccessNetworkConstants.AccessNetworkType.UNKNOWN,
                QnsUtils.getCellularAccessNetworkType(
                        ServiceState.STATE_OUT_OF_SERVICE, TelephonyManager.NETWORK_TYPE_LTE));
        assertEquals(
                AccessNetworkConstants.AccessNetworkType.UNKNOWN,
                QnsUtils.getCellularAccessNetworkType(
                        ServiceState.STATE_EMERGENCY_ONLY, TelephonyManager.NETWORK_TYPE_LTE));
        assertEquals(
                AccessNetworkConstants.AccessNetworkType.UNKNOWN,
                QnsUtils.getCellularAccessNetworkType(
                        ServiceState.STATE_POWER_OFF, TelephonyManager.NETWORK_TYPE_LTE_CA));
        assertEquals(
                AccessNetworkConstants.AccessNetworkType.EUTRAN,
                QnsUtils.getCellularAccessNetworkType(
                        ServiceState.STATE_IN_SERVICE, TelephonyManager.NETWORK_TYPE_LTE));
        assertEquals(
                AccessNetworkConstants.AccessNetworkType.EUTRAN,
                QnsUtils.getCellularAccessNetworkType(
                        ServiceState.STATE_IN_SERVICE, TelephonyManager.NETWORK_TYPE_LTE_CA));
        assertEquals(
                AccessNetworkConstants.AccessNetworkType.GERAN,
                QnsUtils.getCellularAccessNetworkType(
                        ServiceState.STATE_IN_SERVICE, TelephonyManager.NETWORK_TYPE_GPRS));
        assertEquals(
                AccessNetworkConstants.AccessNetworkType.UTRAN,
                QnsUtils.getCellularAccessNetworkType(
                        ServiceState.STATE_IN_SERVICE, TelephonyManager.NETWORK_TYPE_TD_SCDMA));
        assertEquals(
                AccessNetworkConstants.AccessNetworkType.NGRAN,
                QnsUtils.getCellularAccessNetworkType(
                        ServiceState.STATE_IN_SERVICE, TelephonyManager.NETWORK_TYPE_NR));
    }

    @Test
    public void testGetConfig() {
        createTestBundle();
        doReturn(mTestBundle).when(mMockCarrierConfigManager).getConfigForSubId(anyInt());
        int[] defaultIntArray = new int[] {1, 2};
        int[] defaultNoVopsArray = new int[] {1, 0};
        int[] defaultWwanHystTimerIntArray = new int[] {50000, 50000, 50000};
        int[] defaultWlanHystTimerIntArray = new int[] {50000, 50000, 50000};
        int[] defaultNonImsWwanHystTimerIntArray = new int[] {50000, 50000};
        int[] defaultNonImsWlanHystTimerIntArray = new int[] {50000, 50000};
        int[] defaultWaitingTimerIntArray = new int[] {45000, 45000};
        int[] defaultIwlanMaxHoCountAndFallback = new int[] {3, 1};
        String defaultString = "www.test.com,3,200,32,50,20000,10000";
        String[] defaultStringArray = new String[] {"LTE", "UMTS"};
        String[] defaultGapOffsetStringArray =
                new String[] {"eutran:rsrp:-5", "ngran:ssrsp:-2", "utran:rscp:-3"};
        String[] defaultHysteresisDbLevelStringArray =
                new String[] {"eutran:rsrp:1", "ngran:ssrsp:2", "utran:rscp:5"};
        String[] defaultHandoverPolicyArray = new String[] {HANDOVER_POLICY_1, HANDOVER_POLICY_2};
        String[] fallbackWwanRuleWithImsUnregistered =
                new String[] {FALLBACK_RULE0, FALLBACK_RULE1};
        String[] fallbackWwanRuleWithImsHoRegisterFail =
                new String[] {FALLBACK_RULE1, FALLBACK_RULE0};
        String[] defaultFallbackConfigInitialDataConnection =
                new String[] {"ims:2:30000:60000:5", "mms:1:10000:5000:2"};

        assertFalse(
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager.KEY_QNS_SUPPORT_WFC_DURING_AIRPLANE_MODE_BOOL));
        assertTrue(
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager
                                .KEY_IN_CALL_HO_DECISION_WLAN_TO_WWAN_WITHOUT_VOPS_CONDITION_BOOL));
        assertFalse(
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager
                                .KEY_QNS_ALLOW_VIDEO_OVER_IWLAN_WITH_CELLULAR_LIMITED_CASE_BOOL));
        assertTrue(
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager
                                .KEY_QNS_ALLOW_IMS_OVER_IWLAN_CELLULAR_LIMITED_CASE_BOOL));
        assertFalse(
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager.KEY_QNS_HO_GUARDING_BY_PREFERENCE_BOOL));
        assertFalse(
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager.KEY_QNS_SUPPORT_SERVICE_BARRING_CHECK_BOOL));
        assertFalse(
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager
                                .KEY_ROAM_TRANSPORT_TYPE_SELECTION_WITHOUT_SIGNAL_STRENGTH_BOOL));
        assertFalse(
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager
                                .KEY_PREFER_CURRENT_TRANSPORT_TYPE_IN_VOICE_CALL_BOOL));
        assertFalse(
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager
                                .KEY_QNS_ROVE_OUT_POLICY_WITH_WIFI_BAD_GUARDTIMER_CONDITIONS_BOOL));
        assertFalse(
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager
                                .KEY_BLOCK_IWLAN_IN_INTERNATIONAL_ROAMING_WITHOUT_WWAN_BOOL));
        assertFalse(
                QnsUtils.getConfig(
                        mTestBundle, null, QnsCarrierConfigManager.KEY_BLOCK_IPV6_ONLY_WIFI_BOOL));
        assertEquals(
                QnsConstants.SIP_DIALOG_SESSION_POLICY_FOLLOW_VOICE_CALL,
                (int)
                        QnsUtils.getConfig(
                                mTestBundle,
                                null,
                                QnsCarrierConfigManager
                                        .KEY_QNS_CELLULAR_SS_THRESHOLDBACKHAUL_TIMER_MS_INT));
        assertEquals(
                1,
                (int)
                        QnsUtils.getConfig(
                                mTestBundle,
                                null,
                                QnsCarrierConfigManager
                                        .KEY_QNS_CELLULAR_SS_THRESHOLDBACKHAUL_TIMER_MS_INT));
        assertEquals(
                1,
                (int)
                        QnsUtils.getConfig(
                                mTestBundle,
                                null,
                                QnsCarrierConfigManager
                                        .KEY_QNS_WIFI_RSSI_THRESHOLDBACKHAUL_TIMER_MS_INT));
        assertEquals(
                1,
                (int)
                        QnsUtils.getConfig(
                                mTestBundle,
                                null,
                                QnsCarrierConfigManager.KEY_QNS_IMS_TRANSPORT_TYPE_INT));
        assertEquals(
                1,
                (int)
                        QnsUtils.getConfig(
                                mTestBundle,
                                null,
                                QnsCarrierConfigManager.KEY_QNS_MMS_TRANSPORT_TYPE_INT));
        assertArrayEquals(
                new int[] {
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                },
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        CarrierConfigManager.ImsSs.KEY_XCAP_OVER_UT_SUPPORTED_RATS_INT_ARRAY));
        assertEquals(
                1,
                (int)
                        QnsUtils.getConfig(
                                mTestBundle,
                                null,
                                QnsCarrierConfigManager.KEY_QNS_CBS_TRANSPORT_TYPE_INT));
        assertEquals(
                1,
                (int)
                        QnsUtils.getConfig(
                                mTestBundle,
                                null,
                                QnsCarrierConfigManager.KEY_QNS_SOS_TRANSPORT_TYPE_INT));
        assertEquals(
                QnsConstants.RAT_PREFERENCE_WIFI_ONLY,
                (int)
                        QnsUtils.getConfig(
                                mTestBundle,
                                null,
                                QnsCarrierConfigManager.KEY_QNS_SOS_RAT_PREFERENCE_INT));
        assertEquals(
                QnsConstants.RAT_PREFERENCE_WIFI_ONLY,
                (int)
                        QnsUtils.getConfig(
                                mTestBundle,
                                null,
                                QnsCarrierConfigManager.KEY_QNS_MMS_RAT_PREFERENCE_INT));
        assertEquals(
                QnsConstants.RAT_PREFERENCE_WIFI_ONLY,
                (int)
                        QnsUtils.getConfig(
                                mTestBundle,
                                null,
                                QnsCarrierConfigManager.KEY_QNS_XCAP_RAT_PREFERENCE_INT));
        assertEquals(
                QnsConstants.RAT_PREFERENCE_WIFI_ONLY,
                (int)
                        QnsUtils.getConfig(
                                mTestBundle,
                                null,
                                QnsCarrierConfigManager.KEY_QNS_CBS_RAT_PREFERENCE_INT));
        assertEquals(
                QnsConstants.COVERAGE_HOME,
                (int)
                        QnsUtils.getConfig(
                                mTestBundle,
                                null,
                                QnsCarrierConfigManager
                                        .KEY_QNS_IMS_NETWORK_ENABLE_HO_HYSTERESIS_TIMER_INT));
        assertEquals(
                3000 /* test value */,
                (int)
                        QnsUtils.getConfig(
                                mTestBundle,
                                null,
                                QnsCarrierConfigManager
                                        .KEY_QNS_MEDIA_THRESHOLD_RTP_PACKET_LOSS_TIME_MILLIS_INT));
        assertEquals(
                2000 /* test value */,
                (int)
                        QnsUtils.getConfig(
                                mTestBundle,
                                null,
                                QnsCarrierConfigManager
                                        .KEY_MINIMUM_HANDOVER_GUARDING_TIMER_MS_INT));
        assertArrayEquals(
                defaultIwlanMaxHoCountAndFallback,
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager
                              .KEY_QNS_IN_CALL_ROVEIN_ALLOWED_COUNT_AND_FALLBACK_REASON_INT_ARRAY));
        assertArrayEquals(
                defaultWwanHystTimerIntArray,
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager.KEY_IMS_WWAN_HYSTERESIS_TIMER_MS_INT_ARRAY));
        assertArrayEquals(
                defaultWlanHystTimerIntArray,
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager.KEY_IMS_WLAN_HYSTERESIS_TIMER_MS_INT_ARRAY));
        assertArrayEquals(
                defaultNonImsWwanHystTimerIntArray,
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager.KEY_NON_IMS_WWAN_HYSTERESIS_TIMER_MS_INT_ARRAY));
        assertArrayEquals(
                defaultNonImsWlanHystTimerIntArray,
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager.KEY_NON_IMS_WLAN_HYSTERESIS_TIMER_MS_INT_ARRAY));
        assertArrayEquals(
                defaultWaitingTimerIntArray,
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager
                                .KEY_WAITING_TIME_FOR_PREFERRED_TRANSPORT_WHEN_POWER_ON_INT_ARRAY));
        assertArrayEquals(
                defaultWaitingTimerIntArray,
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager
                                .KEY_QNS_HO_RESTRICT_TIME_WITH_LOW_RTP_QUALITY_MILLIS_INT_ARRAY));
        assertArrayEquals(
                defaultIntArray,
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager
                              .KEY_CHOOSE_WFC_PREFERRED_TRANSPORT_IN_BOTH_BAD_CONDITION_INT_ARRAY));
        assertArrayEquals(
                defaultNoVopsArray,
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        CarrierConfigManager.Ims.KEY_IMS_PDN_ENABLED_IN_NO_VOPS_SUPPORT_INT_ARRAY));
        assertEquals(
                defaultString,
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager
                                .KEY_QNS_WLAN_RTT_BACKHAUL_CHECK_ON_ICMP_PING_STRING));
        assertArrayEquals(
                defaultStringArray,
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager.KEY_IMS_CELLULAR_ALLOWED_RAT_STRING_ARRAY));
        assertArrayEquals(
                defaultGapOffsetStringArray,
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager
                                .KEY_QNS_ROVEIN_THRESHOLD_GAP_WITH_GUARD_TIMER_STRING_ARRAY));
        assertArrayEquals(
                defaultHysteresisDbLevelStringArray,
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager
                                .KEY_QNS_CELLULAR_SIGNAL_STRENGTH_HYSTERESIS_DB_STRING_ARRAY));
        assertArrayEquals(
                defaultHandoverPolicyArray,
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        CarrierConfigManager.KEY_IWLAN_HANDOVER_POLICY_STRING_ARRAY));
        assertArrayEquals(
                fallbackWwanRuleWithImsUnregistered,
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager
                                .KEY_QNS_FALLBACK_WWAN_IMS_UNREGISTRATION_REASON_STRING_ARRAY));
        assertArrayEquals(
                fallbackWwanRuleWithImsHoRegisterFail,
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager
                                .KEY_QNS_FALLBACK_WWAN_IMS_HO_REGISTER_FAIL_REASON_STRING_ARRAY));
        assertArrayEquals(
                defaultFallbackConfigInitialDataConnection,
                QnsUtils.getConfig(
                        mTestBundle,
                        null,
                        QnsCarrierConfigManager
                                .KEY_QNS_FALLBACK_ON_INITIAL_CONNECTION_FAILURE_STRING_ARRAY));
    }

    private void createTestBundle() {
        mTestBundle.putBoolean(
                QnsCarrierConfigManager.KEY_QNS_SUPPORT_WFC_DURING_AIRPLANE_MODE_BOOL, false);
        mTestBundle.putBoolean(
                QnsCarrierConfigManager
                        .KEY_IN_CALL_HO_DECISION_WLAN_TO_WWAN_WITHOUT_VOPS_CONDITION_BOOL,
                true);
        mTestBundle.putBoolean(
                QnsCarrierConfigManager
                        .KEY_QNS_ALLOW_VIDEO_OVER_IWLAN_WITH_CELLULAR_LIMITED_CASE_BOOL,
                false);
        mTestBundle.putBoolean(
                QnsCarrierConfigManager.KEY_QNS_ALLOW_IMS_OVER_IWLAN_CELLULAR_LIMITED_CASE_BOOL,
                true);
        mTestBundle.putBoolean(
                QnsCarrierConfigManager.KEY_QNS_HO_GUARDING_BY_PREFERENCE_BOOL, false);
        mTestBundle.putBoolean(
                QnsCarrierConfigManager.KEY_QNS_SUPPORT_SERVICE_BARRING_CHECK_BOOL, false);
        mTestBundle.putBoolean(
                QnsCarrierConfigManager
                        .KEY_ROAM_TRANSPORT_TYPE_SELECTION_WITHOUT_SIGNAL_STRENGTH_BOOL,
                false);
        mTestBundle.putBoolean(
                QnsCarrierConfigManager.KEY_PREFER_CURRENT_TRANSPORT_TYPE_IN_VOICE_CALL_BOOL,
                false);
        mTestBundle.putBoolean(
                QnsCarrierConfigManager
                        .KEY_QNS_ROVE_OUT_POLICY_WITH_WIFI_BAD_GUARDTIMER_CONDITIONS_BOOL,
                false);
        mTestBundle.putBoolean(
                QnsCarrierConfigManager.KEY_BLOCK_IWLAN_IN_INTERNATIONAL_ROAMING_WITHOUT_WWAN_BOOL,
                false);
        mTestBundle.putBoolean(QnsCarrierConfigManager.KEY_BLOCK_IPV6_ONLY_WIFI_BOOL, false);
        mTestBundle.putInt(
                QnsCarrierConfigManager.KEY_SIP_DIALOG_SESSION_POLICY_INT,
                QnsConstants.SIP_DIALOG_SESSION_POLICY_FOLLOW_VOICE_CALL);
        mTestBundle.putInt(
                QnsCarrierConfigManager.KEY_QNS_CELLULAR_SS_THRESHOLDBACKHAUL_TIMER_MS_INT, 1);
        mTestBundle.putInt(
                QnsCarrierConfigManager.KEY_QNS_WIFI_RSSI_THRESHOLDBACKHAUL_TIMER_MS_INT, 1);
        mTestBundle.putInt(QnsCarrierConfigManager.KEY_QNS_IMS_TRANSPORT_TYPE_INT, 1);
        mTestBundle.putInt(QnsCarrierConfigManager.KEY_QNS_MMS_TRANSPORT_TYPE_INT, 1);
        mTestBundle.putIntArray(
                CarrierConfigManager.ImsSs.KEY_XCAP_OVER_UT_SUPPORTED_RATS_INT_ARRAY,
                new int[] {
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                });
        mTestBundle.putInt(QnsCarrierConfigManager.KEY_QNS_CBS_TRANSPORT_TYPE_INT, 1);
        mTestBundle.putInt(QnsCarrierConfigManager.KEY_QNS_SOS_TRANSPORT_TYPE_INT, 1);
        mTestBundle.putInt(QnsCarrierConfigManager.KEY_QNS_MMS_RAT_PREFERENCE_INT, 1);
        mTestBundle.putInt(QnsCarrierConfigManager.KEY_QNS_SOS_RAT_PREFERENCE_INT, 1);
        mTestBundle.putInt(QnsCarrierConfigManager.KEY_QNS_XCAP_RAT_PREFERENCE_INT, 1);
        mTestBundle.putInt(QnsCarrierConfigManager.KEY_QNS_CBS_RAT_PREFERENCE_INT, 1);
        mTestBundle.putInt(
                QnsCarrierConfigManager.KEY_QNS_IMS_NETWORK_ENABLE_HO_HYSTERESIS_TIMER_INT,
                QnsConstants.COVERAGE_HOME);
        mTestBundle.putInt(
                QnsCarrierConfigManager.KEY_QNS_MEDIA_THRESHOLD_RTP_PACKET_LOSS_TIME_MILLIS_INT,
                3000);
        mTestBundle.putInt(
                QnsCarrierConfigManager.KEY_QNS_MEDIA_THRESHOLD_RTP_PACKET_LOSS_TIME_MILLIS_INT,
                3000);
        mTestBundle.putInt(
                QnsCarrierConfigManager.KEY_MINIMUM_HANDOVER_GUARDING_TIMER_MS_INT,
                2000);
        mTestBundle.putIntArray(
                QnsCarrierConfigManager
                        .KEY_QNS_IN_CALL_ROVEIN_ALLOWED_COUNT_AND_FALLBACK_REASON_INT_ARRAY,
                new int[] {3, 1});
        mTestBundle.putIntArray(
                QnsCarrierConfigManager.KEY_IMS_WWAN_HYSTERESIS_TIMER_MS_INT_ARRAY,
                new int[] {50000, 50000, 50000});
        mTestBundle.putIntArray(
                QnsCarrierConfigManager.KEY_IMS_WLAN_HYSTERESIS_TIMER_MS_INT_ARRAY,
                new int[] {50000, 50000, 50000});
        mTestBundle.putIntArray(
                QnsCarrierConfigManager.KEY_NON_IMS_WWAN_HYSTERESIS_TIMER_MS_INT_ARRAY,
                new int[] {50000, 50000});
        mTestBundle.putIntArray(
                QnsCarrierConfigManager.KEY_NON_IMS_WLAN_HYSTERESIS_TIMER_MS_INT_ARRAY,
                new int[] {50000, 50000});
        mTestBundle.putIntArray(
                QnsCarrierConfigManager
                        .KEY_WAITING_TIME_FOR_PREFERRED_TRANSPORT_WHEN_POWER_ON_INT_ARRAY,
                new int[] {45000, 45000});
        mTestBundle.putIntArray(
                QnsCarrierConfigManager
                        .KEY_QNS_HO_RESTRICT_TIME_WITH_LOW_RTP_QUALITY_MILLIS_INT_ARRAY,
                new int[] {45000, 45000});
        mTestBundle.putIntArray(
                QnsCarrierConfigManager
                        .KEY_CHOOSE_WFC_PREFERRED_TRANSPORT_IN_BOTH_BAD_CONDITION_INT_ARRAY,
                new int[] {1, 2});
        mTestBundle.putIntArray(
                CarrierConfigManager.Ims.KEY_IMS_PDN_ENABLED_IN_NO_VOPS_SUPPORT_INT_ARRAY,
                new int[] {1, 0});
        mTestBundle.putString(
                QnsCarrierConfigManager.KEY_QNS_WLAN_RTT_BACKHAUL_CHECK_ON_ICMP_PING_STRING,
                "www.test.com,3,200,32,50,20000,10000");
        mTestBundle.putStringArray(
                QnsCarrierConfigManager.KEY_IMS_CELLULAR_ALLOWED_RAT_STRING_ARRAY,
                new String[] {"LTE", "UMTS"});
        mTestBundle.putStringArray(
                QnsCarrierConfigManager.KEY_QNS_ROVEIN_THRESHOLD_GAP_WITH_GUARD_TIMER_STRING_ARRAY,
                new String[] {"eutran:rsrp:-5", "ngran:ssrsp:-2", "utran:rscp:-3"});
        mTestBundle.putStringArray(
                CarrierConfigManager.KEY_IWLAN_HANDOVER_POLICY_STRING_ARRAY,
                new String[] {HANDOVER_POLICY_1, HANDOVER_POLICY_2});
        mTestBundle.putStringArray(
                QnsCarrierConfigManager
                        .KEY_QNS_FALLBACK_WWAN_IMS_UNREGISTRATION_REASON_STRING_ARRAY,
                new String[] {FALLBACK_RULE0, FALLBACK_RULE1});
        mTestBundle.putStringArray(
                QnsCarrierConfigManager
                        .KEY_QNS_FALLBACK_WWAN_IMS_HO_REGISTER_FAIL_REASON_STRING_ARRAY,
                new String[] {FALLBACK_RULE1, FALLBACK_RULE0});
        mTestBundle.putStringArray(
                QnsCarrierConfigManager.KEY_QNS_FALLBACK_ON_INITIAL_CONNECTION_FAILURE_STRING_ARRAY,
                new String[] {"ims:2:30000:60000:5", "mms:1:10000:5000:2"});
        mTestBundle.putStringArray(
                QnsCarrierConfigManager.KEY_QNS_CELLULAR_SIGNAL_STRENGTH_HYSTERESIS_DB_STRING_ARRAY,
                new String[] {"eutran:rsrp:1", "ngran:ssrsp:2", "utran:rscp:5"});
    }

    @Test
    public void testGetDefaultValueForKey() {
        doReturn(null).when(mMockCarrierConfigManager).getConfigForSubId(anyInt());
        doReturn(1234).when(mMockTelephonyManager).getSimCarrierId();
        assertTrue(
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager.KEY_QNS_SUPPORT_WFC_DURING_AIRPLANE_MODE_BOOL));
        assertFalse(
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager
                                .KEY_IN_CALL_HO_DECISION_WLAN_TO_WWAN_WITHOUT_VOPS_CONDITION_BOOL));
        assertFalse(
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager
                                .KEY_QNS_ALLOW_VIDEO_OVER_IWLAN_WITH_CELLULAR_LIMITED_CASE_BOOL));
        assertFalse(
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager
                                .KEY_QNS_ALLOW_IMS_OVER_IWLAN_CELLULAR_LIMITED_CASE_BOOL));
        assertFalse(
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager.KEY_QNS_HO_GUARDING_BY_PREFERENCE_BOOL));
        assertFalse(
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager.KEY_QNS_SUPPORT_SERVICE_BARRING_CHECK_BOOL));
        assertFalse(
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager
                                .KEY_ROAM_TRANSPORT_TYPE_SELECTION_WITHOUT_SIGNAL_STRENGTH_BOOL));
        assertFalse(
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager
                                .KEY_PREFER_CURRENT_TRANSPORT_TYPE_IN_VOICE_CALL_BOOL));
        assertFalse(
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager
                                .KEY_QNS_ROVE_OUT_POLICY_WITH_WIFI_BAD_GUARDTIMER_CONDITIONS_BOOL));
        assertFalse(
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager
                                .KEY_BLOCK_IWLAN_IN_INTERNATIONAL_ROAMING_WITHOUT_WWAN_BOOL));
        assertTrue(
                QnsUtils.getConfig(
                        null, null, QnsCarrierConfigManager.KEY_BLOCK_IPV6_ONLY_WIFI_BOOL));
        assertEquals(
                QnsConstants.SIP_DIALOG_SESSION_POLICY_NONE,
                (int)
                        QnsUtils.getConfig(
                                null,
                                null,
                                QnsCarrierConfigManager
                                        .KEY_SIP_DIALOG_SESSION_POLICY_INT));
        assertEquals(
                QnsConstants.KEY_DEFAULT_VALUE,
                (int)
                        QnsUtils.getConfig(
                                null,
                                null,
                                QnsCarrierConfigManager
                                        .KEY_QNS_CELLULAR_SS_THRESHOLDBACKHAUL_TIMER_MS_INT));
        assertEquals(
                QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER,
                (int)
                        QnsUtils.getConfig(
                                null,
                                null,
                                QnsCarrierConfigManager
                                        .KEY_QNS_WIFI_RSSI_THRESHOLDBACKHAUL_TIMER_MS_INT));
        assertEquals(
                QnsConstants.TRANSPORT_TYPE_ALLOWED_BOTH,
                (int)
                        QnsUtils.getConfig(
                                null,
                                null,
                                QnsCarrierConfigManager.KEY_QNS_IMS_TRANSPORT_TYPE_INT));
        assertEquals(
                QnsConstants.TRANSPORT_TYPE_ALLOWED_WWAN,
                (int)
                        QnsUtils.getConfig(
                                null,
                                null,
                                QnsCarrierConfigManager.KEY_QNS_MMS_TRANSPORT_TYPE_INT));
        assertEquals(
                QnsConstants.TRANSPORT_TYPE_ALLOWED_WWAN,
                (int)
                        QnsUtils.getConfig(
                                null,
                                null,
                                QnsCarrierConfigManager.KEY_QNS_CBS_TRANSPORT_TYPE_INT));
        assertEquals(
                QnsConstants.TRANSPORT_TYPE_ALLOWED_WWAN,
                (int)
                        QnsUtils.getConfig(
                                null,
                                null,
                                QnsCarrierConfigManager.KEY_QNS_SOS_TRANSPORT_TYPE_INT));
        assertEquals(
                QnsConstants.RAT_PREFERENCE_DEFAULT,
                (int)
                        QnsUtils.getConfig(
                                null,
                                null,
                                QnsCarrierConfigManager.KEY_QNS_SOS_RAT_PREFERENCE_INT));
        assertEquals(
                QnsConstants.RAT_PREFERENCE_DEFAULT,
                (int)
                        QnsUtils.getConfig(
                                null,
                                null,
                                QnsCarrierConfigManager.KEY_QNS_MMS_RAT_PREFERENCE_INT));
        assertEquals(
                QnsConstants.RAT_PREFERENCE_DEFAULT,
                (int)
                        QnsUtils.getConfig(
                                null,
                                null,
                                QnsCarrierConfigManager.KEY_QNS_XCAP_RAT_PREFERENCE_INT));
        assertEquals(
                QnsConstants.RAT_PREFERENCE_DEFAULT,
                (int)
                        QnsUtils.getConfig(
                                null,
                                null,
                                QnsCarrierConfigManager.KEY_QNS_CBS_RAT_PREFERENCE_INT));
        assertEquals(
                QnsConstants.COVERAGE_BOTH,
                (int)
                        QnsUtils.getConfig(
                                null,
                                null,
                                QnsCarrierConfigManager
                                        .KEY_QNS_IMS_NETWORK_ENABLE_HO_HYSTERESIS_TIMER_INT));
        assertEquals(
                QnsConstants.KEY_DEFAULT_PACKET_LOSS_TIME_MILLIS,
                (int)
                        QnsUtils.getConfig(
                                null,
                                null,
                                QnsCarrierConfigManager
                                        .KEY_QNS_MEDIA_THRESHOLD_RTP_PACKET_LOSS_TIME_MILLIS_INT));
        assertEquals(
                QnsConstants.CONFIG_DEFAULT_MIN_HANDOVER_GUARDING_TIMER,
                (int)
                        QnsUtils.getConfig(
                                null,
                                null,
                                QnsCarrierConfigManager
                                        .KEY_MINIMUM_HANDOVER_GUARDING_TIMER_MS_INT));
        assertArrayEquals(
                new int[] {QnsConstants.MAX_COUNT_INVALID, QnsConstants.FALLBACK_REASON_INVALID},
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager
                              .KEY_QNS_IN_CALL_ROVEIN_ALLOWED_COUNT_AND_FALLBACK_REASON_INT_ARRAY));
        assertArrayEquals(
                new int[] {
                    QnsConstants.KEY_DEFAULT_HYST_TIMER,
                    QnsConstants.KEY_DEFAULT_HYST_TIMER,
                    QnsConstants.KEY_DEFAULT_HYST_TIMER
                },
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager.KEY_IMS_WWAN_HYSTERESIS_TIMER_MS_INT_ARRAY));
        assertArrayEquals(
                new int[] {
                    QnsConstants.KEY_DEFAULT_HYST_TIMER,
                    QnsConstants.KEY_DEFAULT_HYST_TIMER,
                    QnsConstants.KEY_DEFAULT_HYST_TIMER
                },
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager.KEY_IMS_WLAN_HYSTERESIS_TIMER_MS_INT_ARRAY));
        assertArrayEquals(
                new int[] {QnsConstants.KEY_DEFAULT_VALUE, QnsConstants.KEY_DEFAULT_VALUE},
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager.KEY_NON_IMS_WWAN_HYSTERESIS_TIMER_MS_INT_ARRAY));
        assertArrayEquals(
                new int[] {QnsConstants.KEY_DEFAULT_VALUE, QnsConstants.KEY_DEFAULT_VALUE},
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager.KEY_NON_IMS_WLAN_HYSTERESIS_TIMER_MS_INT_ARRAY));
        assertArrayEquals(
                new int[] {QnsConstants.KEY_DEFAULT_VALUE, QnsConstants.KEY_DEFAULT_VALUE},
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager
                                .KEY_WAITING_TIME_FOR_PREFERRED_TRANSPORT_WHEN_POWER_ON_INT_ARRAY));
        assertArrayEquals(
                new int[] {
                    QnsConstants.KEY_DEFAULT_IWLAN_AVOID_TIME_LOW_RTP_QUALITY_MILLIS,
                    QnsConstants.KEY_DEFAULT_WWAN_AVOID_TIME_LOW_RTP_QUALITY_MILLIS
                },
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager
                                .KEY_QNS_HO_RESTRICT_TIME_WITH_LOW_RTP_QUALITY_MILLIS_INT_ARRAY));
        assertArrayEquals(
                new int[] {},
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager
                              .KEY_CHOOSE_WFC_PREFERRED_TRANSPORT_IN_BOTH_BAD_CONDITION_INT_ARRAY));
        assertArrayEquals(
                new int[] {},
                QnsUtils.getConfig(
                        null,
                        null,
                        CarrierConfigManager.Ims.KEY_IMS_PDN_ENABLED_IN_NO_VOPS_SUPPORT_INT_ARRAY));
        assertArrayEquals(
                new int[] {
                        QnsConstants.KEY_DEFAULT_THRESHOLD_SSRSRP_GOOD,
                        QnsConstants.KEY_DEFAULT_THRESHOLD_SSRSRP_BAD,
                        QnsCarrierConfigManager.QnsConfigArray.INVALID
                },
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierAnspSupportConfig.KEY_IDLE_NGRAN_SSRSRP_INT_ARRAY));
        assertArrayEquals(
                new int[] {
                        QnsConstants.KEY_DEFAULT_THRESHOLD_SSRSRP_GOOD,
                        QnsConstants.KEY_DEFAULT_THRESHOLD_SSRSRP_BAD,
                        QnsCarrierConfigManager.QnsConfigArray.INVALID
                },
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierAnspSupportConfig.KEY_VOICE_NGRAN_SSRSRP_INT_ARRAY));
        assertArrayEquals(
                new int[] {
                        QnsConstants.KEY_DEFAULT_THRESHOLD_SSRSRP_GOOD,
                        QnsConstants.KEY_DEFAULT_THRESHOLD_SSRSRP_BAD,
                        QnsCarrierConfigManager.QnsConfigArray.INVALID
                },
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierAnspSupportConfig.KEY_VIDEO_NGRAN_SSRSRP_INT_ARRAY));
        assertArrayEquals(
                new int[] {
                        QnsConstants.KEY_DEFAULT_THRESHOLD_RSRP_GOOD,
                        QnsConstants.KEY_DEFAULT_THRESHOLD_RSRP_BAD,
                        QnsCarrierConfigManager.QnsConfigArray.INVALID
                },
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierAnspSupportConfig.KEY_IDLE_EUTRAN_RSRP_INT_ARRAY));
        assertArrayEquals(
                new int[] {
                        QnsConstants.KEY_DEFAULT_THRESHOLD_RSRP_GOOD,
                        QnsConstants.KEY_DEFAULT_THRESHOLD_RSRP_BAD,
                        QnsCarrierConfigManager.QnsConfigArray.INVALID
                },
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierAnspSupportConfig.KEY_VOICE_EUTRAN_RSRP_INT_ARRAY));
        assertArrayEquals(
                new int[] {
                        QnsConstants.KEY_DEFAULT_THRESHOLD_RSRP_GOOD,
                        QnsConstants.KEY_DEFAULT_THRESHOLD_RSRP_BAD,
                        QnsCarrierConfigManager.QnsConfigArray.INVALID
                },
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierAnspSupportConfig.KEY_VIDEO_EUTRAN_RSRP_INT_ARRAY));
        assertArrayEquals(
                new int[] {
                        QnsConstants.KEY_DEFAULT_THRESHOLD_RSCP_GOOD,
                        QnsConstants.KEY_DEFAULT_THRESHOLD_RSCP_BAD,
                        QnsCarrierConfigManager.QnsConfigArray.INVALID
                },
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierAnspSupportConfig.KEY_IDLE_UTRAN_RSCP_INT_ARRAY));
        assertArrayEquals(
                new int[] {
                        QnsConstants.KEY_DEFAULT_THRESHOLD_RSCP_GOOD,
                        QnsConstants.KEY_DEFAULT_THRESHOLD_RSCP_BAD,
                        QnsCarrierConfigManager.QnsConfigArray.INVALID
                },
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierAnspSupportConfig.KEY_VOICE_UTRAN_RSCP_INT_ARRAY));
        assertArrayEquals(
                new int[] {
                        QnsConstants.KEY_DEFAULT_THRESHOLD_RSCP_GOOD,
                        QnsConstants.KEY_DEFAULT_THRESHOLD_RSCP_BAD,
                        QnsCarrierConfigManager.QnsConfigArray.INVALID
                },
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierAnspSupportConfig.KEY_VIDEO_UTRAN_RSCP_INT_ARRAY));
        assertArrayEquals(
                new int[] {
                        QnsConstants.KEY_DEFAULT_THRESHOLD_GERAN_RSSI_GOOD,
                        QnsConstants.KEY_DEFAULT_THRESHOLD_GERAN_RSSI_BAD,
                        QnsCarrierConfigManager.QnsConfigArray.INVALID
                },
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierAnspSupportConfig.KEY_IDLE_GERAN_RSSI_INT_ARRAY));
        assertArrayEquals(
                new int[] {
                        QnsConstants.KEY_DEFAULT_THRESHOLD_GERAN_RSSI_GOOD,
                        QnsConstants.KEY_DEFAULT_THRESHOLD_GERAN_RSSI_BAD,
                        QnsCarrierConfigManager.QnsConfigArray.INVALID
                },
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierAnspSupportConfig.KEY_VOICE_GERAN_RSSI_INT_ARRAY));
        assertArrayEquals(
                new int[] {
                        QnsConstants.KEY_DEFAULT_THRESHOLD_GERAN_RSSI_GOOD,
                        QnsConstants.KEY_DEFAULT_THRESHOLD_GERAN_RSSI_BAD,
                        QnsCarrierConfigManager.QnsConfigArray.INVALID
                },
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierAnspSupportConfig.KEY_VIDEO_GERAN_RSSI_INT_ARRAY));
        assertArrayEquals(
                new int[] {
                        QnsConstants.KEY_DEFAULT_THRESHOLD_WIFI_RSSI_GOOD,
                        QnsConstants.KEY_DEFAULT_THRESHOLD_WIFI_RSSI_BAD
                },
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierAnspSupportConfig.KEY_IDLE_WIFI_RSSI_INT_ARRAY));
        assertArrayEquals(
                new int[] {
                        QnsConstants.KEY_DEFAULT_THRESHOLD_WIFI_RSSI_GOOD,
                        QnsConstants.KEY_DEFAULT_THRESHOLD_WIFI_RSSI_BAD
                },
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierAnspSupportConfig.KEY_VOICE_WIFI_RSSI_INT_ARRAY));
        assertArrayEquals(
                new int[] {
                        QnsConstants.KEY_DEFAULT_THRESHOLD_WIFI_RSSI_GOOD,
                        QnsConstants.KEY_DEFAULT_THRESHOLD_WIFI_RSSI_BAD
                },
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierAnspSupportConfig.KEY_VIDEO_WIFI_RSSI_INT_ARRAY));
        assertEquals(
                "",
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager
                                .KEY_QNS_WLAN_RTT_BACKHAUL_CHECK_ON_ICMP_PING_STRING));
        assertArrayEquals(
                new String[] {"LTE", "NR"},
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager.KEY_IMS_CELLULAR_ALLOWED_RAT_STRING_ARRAY));
        assertArrayEquals(
                (String[]) null,
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager
                                .KEY_QNS_ROVEIN_THRESHOLD_GAP_WITH_GUARD_TIMER_STRING_ARRAY));
        assertArrayEquals(
                (String[]) null,
                QnsUtils.getConfig(
                        null, null, CarrierConfigManager.KEY_IWLAN_HANDOVER_POLICY_STRING_ARRAY));
        assertArrayEquals(
                new String[] {},
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager
                                .KEY_QNS_FALLBACK_ON_INITIAL_CONNECTION_FAILURE_STRING_ARRAY));
        assertArrayEquals(
                (String[]) null,
                QnsUtils.getConfig(
                        null,
                        null,
                        QnsCarrierConfigManager
                                .KEY_QNS_CELLULAR_SIGNAL_STRENGTH_HYSTERESIS_DB_STRING_ARRAY));
    }

    @Test
    public void testGetConfigCarrierId() {
        doReturn(null).when(mMockCarrierConfigManager).getConfigForSubId(anyInt());
        doReturn(TelephonyManager.UNKNOWN_CARRIER_ID, 1000, 1001, 1002)
                .when(mMockTelephonyManager)
                .getSimCarrierId();
        assertEquals(
                TelephonyManager.UNKNOWN_CARRIER_ID, QnsUtils.getConfigCarrierId(sMockContext, 0));
        assertEquals(1000, QnsUtils.getConfigCarrierId(sMockContext, 1));
        assertEquals(1001, QnsUtils.getConfigCarrierId(sMockContext, 0));
        assertEquals(1002, QnsUtils.getConfigCarrierId(sMockContext, 0));
    }

    @Test
    public void testIsWifiCallingAvailable() {
        when(mMockTelephonyManager.isWifiCallingAvailable())
                .thenReturn(false, true)
                .thenThrow(new IllegalStateException("TestException"));
        assertFalse(QnsUtils.isWifiCallingAvailable(sMockContext, 0));
        assertTrue(QnsUtils.isWifiCallingAvailable(sMockContext, 0));
        assertFalse(QnsUtils.isWifiCallingAvailable(sMockContext, 0)); // exception
    }

    @Test
    public void testReadQnsDefaultConfigFromAssets_InvalidCarrierId() {
        assertNull(
                QnsUtils.readQnsDefaultConfigFromAssets(
                        sMockContext, TelephonyManager.UNKNOWN_CARRIER_ID));
    }

    @Test
    public void testIsValidSlotIndex() {
        when(mMockTelephonyManager.getActiveModemCount()).thenReturn(-1, 1, 2, 3);
        assertFalse(QnsUtils.isValidSlotIndex(sMockContext, 0));
        assertFalse(QnsUtils.isValidSlotIndex(sMockContext, 1));
        assertTrue(QnsUtils.isValidSlotIndex(sMockContext, 1));
        assertTrue(QnsUtils.isValidSlotIndex(sMockContext, 2));
    }

    @Test
    public void testGetNetworkCapabilitiesFromString() {
        Set<Integer> capabilities = QnsUtils.getNetworkCapabilitiesFromString("MMS|XCAP");
        assertEquals(2, capabilities.size());
        assertTrue(capabilities.contains(NetworkCapabilities.NET_CAPABILITY_MMS));
        assertTrue(capabilities.contains(NetworkCapabilities.NET_CAPABILITY_XCAP));
    }

    @Test
    public void testNetworkCapabilitiesToString() {
        Set<Integer> set = new HashSet<>();
        set.add(NetworkCapabilities.NET_CAPABILITY_MMS);
        set.add(NetworkCapabilities.NET_CAPABILITY_XCAP);
        set.add(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        String capabilities = QnsUtils.networkCapabilitiesToString(set);
        assertEquals("[MMS|XCAP|INTERNET]", capabilities);
    }

    @Test
    public void testGetNetworkCapabilityFromString() {
        assertEquals(
                NetworkCapabilities.NET_CAPABILITY_INTERNET,
                QnsUtils.getNetworkCapabilityFromString("INTERNET"));
        assertEquals(
                NetworkCapabilities.NET_CAPABILITY_MMS,
                QnsUtils.getNetworkCapabilityFromString("MMS"));
        assertEquals(
                NetworkCapabilities.NET_CAPABILITY_SUPL,
                QnsUtils.getNetworkCapabilityFromString("SUPL"));
        assertEquals(
                NetworkCapabilities.NET_CAPABILITY_DUN,
                QnsUtils.getNetworkCapabilityFromString("DUN"));
        assertEquals(
                NetworkCapabilities.NET_CAPABILITY_FOTA,
                QnsUtils.getNetworkCapabilityFromString("FOTA"));
        assertEquals(
                NetworkCapabilities.NET_CAPABILITY_IMS,
                QnsUtils.getNetworkCapabilityFromString("IMS"));
        assertEquals(
                NetworkCapabilities.NET_CAPABILITY_CBS,
                QnsUtils.getNetworkCapabilityFromString("CBS"));
        assertEquals(
                NetworkCapabilities.NET_CAPABILITY_XCAP,
                QnsUtils.getNetworkCapabilityFromString("XCAP"));
        assertEquals(
                NetworkCapabilities.NET_CAPABILITY_EIMS,
                QnsUtils.getNetworkCapabilityFromString("EIMS"));
        assertEquals(
                NetworkCapabilities.NET_CAPABILITY_MCX,
                QnsUtils.getNetworkCapabilityFromString("MCX"));
        assertEquals(
                NetworkCapabilities.NET_CAPABILITY_VSIM,
                QnsUtils.getNetworkCapabilityFromString("VSIM"));
        assertEquals(
                NetworkCapabilities.NET_CAPABILITY_BIP,
                QnsUtils.getNetworkCapabilityFromString("BIP"));
        assertEquals(
                NetworkCapabilities.NET_CAPABILITY_ENTERPRISE,
                QnsUtils.getNetworkCapabilityFromString("ENTERPRISE"));
        assertEquals(
                NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH,
                QnsUtils.getNetworkCapabilityFromString("PRIORITIZE_BANDWIDTH"));
        assertEquals(
                NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY,
                QnsUtils.getNetworkCapabilityFromString("PRIORITIZE_LATENCY"));
        assertEquals(-1, QnsUtils.getNetworkCapabilityFromString("FOO"));
    }

    @Test
    public void testNetworkCapabilityToString() {
        assertEquals(
                "INTERNET",
                QnsUtils.networkCapabilityToString(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        assertEquals(
                "MMS", QnsUtils.networkCapabilityToString(NetworkCapabilities.NET_CAPABILITY_MMS));
        assertEquals(
                "SUPL",
                QnsUtils.networkCapabilityToString(NetworkCapabilities.NET_CAPABILITY_SUPL));
        assertEquals(
                "DUN", QnsUtils.networkCapabilityToString(NetworkCapabilities.NET_CAPABILITY_DUN));
        assertEquals(
                "FOTA",
                QnsUtils.networkCapabilityToString(NetworkCapabilities.NET_CAPABILITY_FOTA));
        assertEquals(
                "IMS", QnsUtils.networkCapabilityToString(NetworkCapabilities.NET_CAPABILITY_IMS));
        assertEquals(
                "CBS", QnsUtils.networkCapabilityToString(NetworkCapabilities.NET_CAPABILITY_CBS));
        assertEquals(
                "XCAP",
                QnsUtils.networkCapabilityToString(NetworkCapabilities.NET_CAPABILITY_XCAP));
        assertEquals(
                "EIMS",
                QnsUtils.networkCapabilityToString(NetworkCapabilities.NET_CAPABILITY_EIMS));
        assertEquals(
                "MCX", QnsUtils.networkCapabilityToString(NetworkCapabilities.NET_CAPABILITY_MCX));
        assertEquals(
                "VSIM",
                QnsUtils.networkCapabilityToString(NetworkCapabilities.NET_CAPABILITY_VSIM));
        assertEquals(
                "BIP", QnsUtils.networkCapabilityToString(NetworkCapabilities.NET_CAPABILITY_BIP));
        assertEquals(
                "ENTERPRISE",
                QnsUtils.networkCapabilityToString(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE));
        assertEquals(
                "PRIORITIZE_BANDWIDTH",
                QnsUtils.networkCapabilityToString(
                        NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH));
        assertEquals(
                "PRIORITIZE_LATENCY",
                QnsUtils.networkCapabilityToString(
                        NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY));
        assertEquals("Unknown(-1)", QnsUtils.networkCapabilityToString(-1));
    }

    @Test
    public void testTransitionBtwApnTypeAndNetCapability() {
        int[] netCapabilities =
                new int[] {
                    NetworkCapabilities.NET_CAPABILITY_IMS,
                    NetworkCapabilities.NET_CAPABILITY_EIMS,
                    NetworkCapabilities.NET_CAPABILITY_MMS,
                    NetworkCapabilities.NET_CAPABILITY_XCAP,
                    NetworkCapabilities.NET_CAPABILITY_CBS
                };
        for (int netCapability : netCapabilities) {
            int apnType = QnsUtils.getApnTypeFromNetCapability(netCapability);
            int netCapabilityFromApnType = QnsUtils.getNetCapabilityFromApnType(apnType);
            assertEquals(netCapability, netCapabilityFromApnType);
        }

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        QnsUtils.getApnTypeFromNetCapability(
                                NetworkCapabilities.NET_CAPABILITY_INTERNET));
        assertThrows(
                IllegalArgumentException.class,
                () -> QnsUtils.getNetCapabilityFromApnType(ApnSetting.TYPE_DEFAULT));
    }

    @Test
    public void testTransitionBtwApnTypeNameAndNetCapabilityName() {
        int[] netCapabilities =
                new int[] {
                    NetworkCapabilities.NET_CAPABILITY_IMS,
                    NetworkCapabilities.NET_CAPABILITY_EIMS,
                    NetworkCapabilities.NET_CAPABILITY_MMS,
                    NetworkCapabilities.NET_CAPABILITY_XCAP,
                    NetworkCapabilities.NET_CAPABILITY_CBS
                };
        for (int netCapability : netCapabilities) {
            String netCapabilityName = QnsUtils.getNameOfNetCapability(netCapability);
            int apnType = QnsUtils.getApnTypeFromNetCapability(netCapability);
            String apnTypeName = ApnSetting.getApnTypeString(apnType);
            int netCapabilityFromApnType = QnsUtils.getNetCapabilityFromApnType(apnType);
            String netCapabilityNameFromApnType =
                    QnsUtils.getNameOfNetCapability(netCapabilityFromApnType);
            if (netCapability == NetworkCapabilities.NET_CAPABILITY_EIMS) {
                assertEquals(netCapabilityName, "eims");
                assertEquals(apnTypeName, "emergency");
                assertEquals(netCapabilityName, netCapabilityNameFromApnType);
            } else {
                assertEquals(netCapabilityName, apnTypeName);
                assertEquals(apnTypeName, netCapabilityNameFromApnType);
                assertEquals(netCapabilityName, netCapabilityNameFromApnType);
            }
        }
    }

    @Test
    public void testGetNetCapabilitiesFromApnTypesString() {
        int[] netCapabilities =
                new int[] {
                    NetworkCapabilities.NET_CAPABILITY_IMS,
                    NetworkCapabilities.NET_CAPABILITY_EIMS,
                    NetworkCapabilities.NET_CAPABILITY_MMS,
                    NetworkCapabilities.NET_CAPABILITY_XCAP,
                    NetworkCapabilities.NET_CAPABILITY_CBS
                };
        String[] apnTypes = new String[] {"ims", "emergency", "mms", "xcap", "cbs"};

        List<Integer> result = QnsUtils.getNetCapabilitiesFromApnTypesString(apnTypes);
        for (int netCapability : netCapabilities) {
            assertTrue(result.contains(netCapability));
        }
    }
}
