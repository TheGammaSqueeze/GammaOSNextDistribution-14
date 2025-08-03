/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_PRIMARY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_DEFAULT_COUNTRY_CODE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsMmTelManager;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.hotspot2.NetworkDetail;
import com.android.server.wifi.p2p.WifiP2pMetrics;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Unit tests for {@link com.android.server.wifi.WifiCountryCode}.
 */
@SmallTest
public class WifiCountryCodeTest extends WifiBaseTest {

    private static final String TAG = "WifiCountryCodeTest";
    /* TODO: replace with PackageManager.FEATURE_TELEPHONY_CALLING once
     * wifi-module-sdk-version-defaults min_sdk_version bumps to API 33. */
    private static final String FEATURE_TELEPHONY_CALLING = "android.hardware.telephony.calling";
    private static final String TEST_COUNTRY_CODE = "JP";
    private static final String TEST_COUNTRY_CODE_2 = "CN";
    private static final int TEST_ACTIVE_SUBSCRIPTION_ID = 1;
    private static final String SSID1 = "Network1";
    private static final String TARGET_SSID1 = "\"" + SSID1 + "\"";
    private String mDefaultCountryCode = "US";
    private String mTelephonyCountryCode = "JP";
    private String mWorldModeCountryCode = "00";
    private boolean mRevertCountryCodeOnCellularLoss = true;
    private boolean mStaDynamicCountryCodeUpdateSupported = false;
    // Default assume true since it was a design before R
    private boolean mDriverSupportedNl80211RegChangedEvent = false;
    private boolean mForcedSoftApRestateWhenCountryCodeChanged = false;
    private boolean mCallingSupported;
    @Mock Context mContext;
    MockResources mResources = new MockResources();
    @Mock TelephonyManager mTelephonyManager;
    @Mock PackageManager mPackageManager;
    @Mock ActiveModeWarden mActiveModeWarden;
    @Mock ConcreteClientModeManager mClientModeManager;
    @Mock SoftApManager mSoftApManager;
    @Mock ClientModeImplMonitor mClientModeImplMonitor;
    @Mock WifiNative mWifiNative;
    @Mock WifiSettingsConfigStore mSettingsConfigStore;
    @Mock WifiInfo mWifiInfo;
    @Mock WifiCountryCode.ChangeListener mExternalChangeListener;
    @Mock SoftApModeConfiguration mSoftApModeConfiguration;
    @Mock SubscriptionManager mSubscriptionManager;
    @Mock SubscriptionInfo  mActiveSubscriptionInfo;
    @Mock ImsMmTelManager mImsMmTelManager;
    @Mock Clock mClock;
    @Mock WifiPermissionsUtil mWifiPermissionsUtil;
    @Mock WifiP2pMetrics mWifiP2pMetrics;
    private WifiCountryCode mWifiCountryCode;
    private List<ClientModeManager> mClientManagerList;
    private List<SubscriptionInfo> mSubscriptionInfoList = new ArrayList<>();
    private MockitoSession mStaticMockSession = null;

    @Captor
    private ArgumentCaptor<ActiveModeWarden.ModeChangeCallback> mModeChangeCallbackCaptor;
    @Captor
    private ArgumentCaptor<ClientModeImplListener> mClientModeImplListenerCaptor;
    @Captor
    private ArgumentCaptor<WifiCountryCode.ChangeListener> mChangeListenerCaptor;
    @Captor
    private ArgumentCaptor<String> mSetCountryCodeCaptor;
    @Mock ScanDetail mScanDetail1;
    @Mock ScanDetail mScanDetail2;
    @Mock ScanDetail mScanDetail3;
    @Mock NetworkDetail mNetworkDetail1;
    @Mock NetworkDetail mNetworkDetail2;
    @Mock NetworkDetail mNetworkDetail3;
    @Mock ScanResult mScanResult1;
    @Mock ScanResult mScanResult2;
    @Mock ScanResult mScanResult3;
    private List<ScanDetail> mScanDetails;

    /**
     * Setup test.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mClientManagerList = new ArrayList<>();
        mClientManagerList.add(mClientModeManager);
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(mClientManagerList);
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        when(mClientModeManager.setCountryCode(anyString())).thenReturn(true);
        when(mClientModeManager.isConnected()).thenReturn(true);
        when(mClientModeManager.getConnectionInfo()).thenReturn(mWifiInfo);
        when(mWifiInfo.getSuccessfulTxPacketsPerSecond()).thenReturn(10.0);
        when(mWifiInfo.getSuccessfulRxPacketsPerSecond()).thenReturn(5.0);
        when(mContext.getSystemService(Context.TELEPHONY_SERVICE))
                .thenReturn(mTelephonyManager);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);

        setCallingSupported(true);

        doAnswer(new AnswerWithArguments() {
            public void answer(WifiSettingsConfigStore.Key<String> key, Object countryCode) {
                when(mSettingsConfigStore.get(WIFI_DEFAULT_COUNTRY_CODE))
                        .thenReturn((String) countryCode);
            }
        }).when(mSettingsConfigStore).put(eq(WIFI_DEFAULT_COUNTRY_CODE), any(String.class));

        when(mSettingsConfigStore.get(WIFI_DEFAULT_COUNTRY_CODE)).thenReturn(mDefaultCountryCode);
        when(mContext.getSystemService(SubscriptionManager.class)).thenReturn(mSubscriptionManager);
        mSubscriptionInfoList.add(mActiveSubscriptionInfo);

        when(mSubscriptionManager.getCompleteActiveSubscriptionInfoList())
            .thenReturn(mSubscriptionInfoList);
        when(mActiveSubscriptionInfo.getSubscriptionId()).thenReturn(TEST_ACTIVE_SUBSCRIPTION_ID);
        mStaticMockSession = mockitoSession()
            .mockStatic(ImsMmTelManager.class)
            .startMocking();

        lenient().when(ImsMmTelManager.createForSubscriptionId(eq(TEST_ACTIVE_SUBSCRIPTION_ID)))
                .thenReturn(mImsMmTelManager);
        when(mImsMmTelManager.isAvailable(anyInt(), anyInt())).thenReturn(false);

        createWifiCountryCode();
        mScanDetails = setupScanDetails(TEST_COUNTRY_CODE);
    }

    private void setCallingSupported(boolean supported) {
        mCallingSupported = supported;
        when(mPackageManager.hasSystemFeature(FEATURE_TELEPHONY_CALLING)).thenReturn(supported);
    }

    @After
    public void cleanUp() throws Exception {
        mStaticMockSession.finishMocking();
    }

    private void createWifiCountryCode() {
        mResources.setBoolean(R.bool.config_wifi_revert_country_code_on_cellular_loss,
                mRevertCountryCodeOnCellularLoss);
        mResources.setBoolean(R.bool.config_wifiStaDynamicCountryCodeUpdateSupported,
                mStaDynamicCountryCodeUpdateSupported);
        mResources.setBoolean(R.bool.config_wifiDriverSupportedNl80211RegChangedEvent,
                mDriverSupportedNl80211RegChangedEvent);
        mResources.setBoolean(R.bool.config_wifiForcedSoftApRestartWhenCountryCodeChanged,
                mForcedSoftApRestateWhenCountryCodeChanged);
        mResources.setBoolean(R.bool.config_wifiUpdateCountryCodeFromScanResultGeneric, false);
        mResources.setString(R.string.config_wifiDriverWorldModeCountryCode, mWorldModeCountryCode);
        doAnswer((invocation) -> {
            if (SdkLevel.isAtLeastS()) {
                mChangeListenerCaptor.getValue()
                        .onSetCountryCodeSucceeded(mSetCountryCodeCaptor.getValue());
            }
            if (mDriverSupportedNl80211RegChangedEvent) {
                mChangeListenerCaptor.getValue()
                        .onDriverCountryCodeChanged(mSetCountryCodeCaptor.getValue());
            }
            return true;
        }).when(mClientModeManager).setCountryCode(
                    mSetCountryCodeCaptor.capture());
        when(mContext.getResources()).thenReturn(mResources);
        mWifiCountryCode = new WifiCountryCode(
                mContext,
                mActiveModeWarden,
                mWifiP2pMetrics,
                mClientModeImplMonitor,
                mWifiNative,
                mSettingsConfigStore,
                mClock,
                mWifiPermissionsUtil);
        mWifiCountryCode.enableVerboseLogging(true);
        verify(mActiveModeWarden, atLeastOnce()).registerModeChangeCallback(
                    mModeChangeCallbackCaptor.capture());
        verify(mClientModeImplMonitor, atLeastOnce()).registerListener(
                mClientModeImplListenerCaptor.capture());
        verify(mWifiNative, atLeastOnce()).registerCountryCodeEventListener(
                mChangeListenerCaptor.capture());
    }

    /**
     * Test if we do not receive country code from Telephony.
     * @throws Exception
     */
    @Test
    public void useDefaultCountryCode() throws Exception {
        // Supplicant started.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        // Wifi get L2 connected.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);
        verify(mClientModeManager).setCountryCode(anyString());
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
        verify(mWifiP2pMetrics).setIsCountryCodeWorldMode(false);
    }

    /**
     * Test that we read the country code from telephony at bootup.
     * @throws Exception
     */
    @Test
    public void useTelephonyCountryCodeOnBootup() throws Exception {
        when(mTelephonyManager.getNetworkCountryIso()).thenReturn(mTelephonyCountryCode);
        // Supplicant started.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        // Wifi get L2 connected.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);
        verify(mClientModeManager).setCountryCode(anyString());
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
    }

    /**
     * Test if we receive country code from Telephony before supplicant starts.
     * @throws Exception
     */
    @Test
    public void useTelephonyCountryCodeOnChange() throws Exception {
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        assertEquals(null, mWifiCountryCode.getCurrentDriverCountryCode());
        // Supplicant started.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        // Wifi get L2 connected.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);
        verify(mClientModeManager).setCountryCode(anyString());
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
    }

    /**
     * Test if we receive country code from Telephony after supplicant starts.
     * @throws Exception
     */
    @Test
    public void telephonyCountryCodeChangeAfterSupplicantStarts() throws Exception {
        // Start in scan only mode.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());

        // Supplicant starts.
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(mClientModeManager);
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());

        // Telephony country code arrives.
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        // Wifi get L2 connected.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);

        verify(mClientModeManager, times(2)).setCountryCode(anyString());
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
    }


    /**
     * Test if we receive country code from Telephony after supplicant stop.
     * @throws Exception
     */
    @Test
    public void telephonyCountryCodeChangeAfterSupplicantStop() throws Exception {
        // Start in scan only mode.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());

        // Supplicant starts.
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(mClientModeManager);
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());

        // Telephony country code arrives.
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        // Wifi get L2 connected.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);

        // Set twice, one is mDefaultCountryCode and another on is mTelephonyCountryCode
        verify(mClientModeManager, times(2)).setCountryCode(anyString());
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());

        // Remove mode manager.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRemoved(mClientModeManager);

        // Send Telephony country code again - should be ignored, times keep 2.
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        verify(mClientModeManager, times(2)).setCountryCode(anyString());
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCountryCode());

        // Now try removing the mode manager again - should not crash.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRemoved(mClientModeManager);
    }

    /**
     * Test if we receive country code from Telephony after we get L2 connected.
     * @throws Exception
     */
    @Test
    public void telephonyCountryCodeChangeAfterL2Connected() throws Exception {
        mWifiCountryCode.setDefaultCountryCode("00");
        // Supplicant starts.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        // Wifi get L2 connected.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);

        // Wifi Calling is available
        when(mImsMmTelManager.isAvailable(anyInt(), anyInt())).thenReturn(true);
        // Telephony country code arrives.
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        // Telephony country code won't be applied at this time.
        assertEquals("00", mWifiCountryCode.getCurrentDriverCountryCode());
        verify(mWifiP2pMetrics).setIsCountryCodeWorldMode(true);
        // Wifi is not forced to disconnect
        verify(mClientModeManager, times(0)).disconnect();

        // Wifi Calling is not available
        when(mImsMmTelManager.isAvailable(anyInt(), anyInt())).thenReturn(false);
        // Wifi traffic is high
        when(mWifiInfo.getSuccessfulTxPacketsPerSecond()).thenReturn(20.0);
        // Telephony country code arrives.
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        // Telephony country code won't be applied at this time.
        assertEquals("00", mWifiCountryCode.getCurrentDriverCountryCode());
        verify(mWifiP2pMetrics).setIsCountryCodeWorldMode(true);
        // Wifi is not forced to disconnect
        verify(mClientModeManager, times(0)).disconnect();

        // Wifi traffic is low
        when(mWifiInfo.getSuccessfulTxPacketsPerSecond()).thenReturn(10.0);
        // Telephony country code arrives for multiple times
        for (int i = 0; i < 3; i++) {
            mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        }
        // Telephony country code still won't be applied.
        assertEquals("00", mWifiCountryCode.getCurrentDriverCountryCode());
        if (mCallingSupported) {
            // Wifi is forced to disconnect
            verify(mClientModeManager, times(1)).disconnect();
        }

        mClientModeImplListenerCaptor.getValue().onConnectionEnd(mClientModeManager);
        // Telephony country is applied after supplicant is ready.
        verify(mClientModeManager, times(2)).setCountryCode(anyString());
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
    }

    @Test
    public void telephonyCountryCodeChangeAfterL2ConnectedWithoutCalling() throws Exception {
        setCallingSupported(false);
        telephonyCountryCodeChangeAfterL2Connected();
    }

    /**
     * Test if we receive country code from Telephony after we get L2 connected on 2 STA interfaces.
     * @throws Exception
     */
    @Test
    public void telephonyCountryCodeChangeAfterL2ConnectedOnTwoClientModeManager()
            throws Exception {
        // Primary CMM
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        // Wifi get L2 connected on the primary.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);
        // Telephony country code arrives.
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        // Telephony country code won't be applied at this time.
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());

        // Create secondary CMM
        ConcreteClientModeManager secondaryClientModeManager =
                mock(ConcreteClientModeManager.class);
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(secondaryClientModeManager);
        // Wifi get L2 connected on the secondary.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(secondaryClientModeManager);

        // Telephony country code still not applied.
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());

        // Disconnection on primary
        mClientModeImplListenerCaptor.getValue().onConnectionEnd(mClientModeManager);

        // Telephony country code still not applied.
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());

        // Disconnection on secondary
        mClientModeImplListenerCaptor.getValue().onConnectionEnd(secondaryClientModeManager);

        // Telephony coutry is applied after both of them are disconnected.
        verify(mClientModeManager, times(2)).setCountryCode(anyString());
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
    }

    /**
     * Test if we can reset to the default country code when phone is out of service, when
     * |config_wifi_revert_country_code_on_cellular_loss| is set to true;
     * Telephony service calls |setCountryCode| with an empty string when phone is out of service.
     * In this case we should fall back to the default country code.
     * @throws Exception
     */
    @Test
    public void resetCountryCodeWhenOutOfService() throws Exception {
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCountryCode());
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCountryCode());
        // Out of service.
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate("");
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCountryCode());
    }

    /**
     * Test that we don't crash when we try to set the country code if the TelephonyService
     * cannot be found. This is really only the case when instrumentation tests that run on the
     * phone process are cleaned up.
     */
    @Test
    public void setCountryCodeDoesNotCrashWhenTelephonyServiceNotFound() throws Exception {
        when(mImsMmTelManager.isAvailable(anyInt(), anyInt())).thenThrow(new RuntimeException());
        try {
            mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        } catch (RuntimeException e) {
            fail("Didn't catch RuntimeException from Telephony Service not being found!");
        }
    }

    /**
     * Test if we can keep using the last known country code when phone is out of service, when
     * |config_wifi_revert_country_code_on_cellular_loss| is set to false;
     * Telephony service calls |setCountryCode| with an empty string when phone is out of service.
     * In this case we should keep using the last known country code.
     * @throws Exception
     */
    @Test
    public void doNotResetCountryCodeWhenOutOfService() throws Exception {
        // Refresh mWifiCountryCode with |config_wifi_revert_country_code_on_cellular_loss|
        // setting to false.
        mRevertCountryCodeOnCellularLoss = false;
        createWifiCountryCode();

        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCountryCode());
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCountryCode());
        // Out of service.
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate("");
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCountryCode());
    }

    /**
     * Tests that we always use the US locale for converting the provided country code regardless
     * of the system locale set.
     */
    @Test
    public void useUSLocaleForConversionToUpperCase() {
        String oemCountryCodeLower = "us";
        String oemCountryCodeUpper = "US";
        String telephonyCountryCodeLower = "il";
        String telephonyCountryCodeUpper = "IL";

        mDefaultCountryCode = oemCountryCodeLower;
        createWifiCountryCode();

        // Set the default locale to "tr" (Non US).
        Locale.setDefault(new Locale("tr"));

        // Trigger a country code change using the OEM country code.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        verify(mClientModeManager).setCountryCode(oemCountryCodeUpper);

        // Now trigger a country code change using the telephony country code.
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(telephonyCountryCodeLower);
        verify(mClientModeManager).setCountryCode(telephonyCountryCodeUpper);
    }
    /**
     * Verifies that dump() does not fail
     */
    @Test
    public void dumpDoesNotFail() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        mRevertCountryCodeOnCellularLoss = false;
        createWifiCountryCode();

        mWifiCountryCode.dump(null, pw, null);
        String dumpCountryCodeStr = sw.toString();

        assertTrue(dumpCountryCodeStr.contains("mTelephonyCountryCode"));
        assertTrue(dumpCountryCodeStr.contains("DefaultCountryCode(system property)"));
        assertTrue(dumpCountryCodeStr.contains("DefaultCountryCode(config store)"));
        assertTrue(dumpCountryCodeStr.contains("mTelephonyCountryTimestamp"));
        assertTrue(dumpCountryCodeStr.contains("mReadyTimestamp"));
        assertTrue(dumpCountryCodeStr.contains("mReady"));
        assertTrue(dumpCountryCodeStr.contains("mDriverCountryCode"));
        assertTrue(dumpCountryCodeStr.contains("mDriverCountryCodeUpdatedTimestamp"));

    }

    /**
     * Test set Default country code
     * @throws Exception
     */
    @Test
    public void setDefaultCountryCode() throws Exception {
        // Supplicant started, it will update default country code (US) to driver
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        verify(mClientModeManager).setCountryCode(eq(mDefaultCountryCode));
        // Remove mode manager.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRemoved(mClientModeManager);
        // Update default country code (JP) to driver
        mWifiCountryCode.setDefaultCountryCode(TEST_COUNTRY_CODE);
        // Supplicant started again, it will update new default country code (JP) to driver
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        verify(mClientModeManager).setCountryCode(eq(TEST_COUNTRY_CODE));
        assertEquals(TEST_COUNTRY_CODE, mWifiCountryCode.getCurrentDriverCountryCode());
        verify(mSettingsConfigStore).put(eq(WIFI_DEFAULT_COUNTRY_CODE), eq(TEST_COUNTRY_CODE));
        assertEquals(TEST_COUNTRY_CODE, mSettingsConfigStore.get(WIFI_DEFAULT_COUNTRY_CODE));
    }

    /**
     * Test is valid country code
     * @throws Exception
     */
    @Test
    public void testValidCountryCode() throws Exception {
        assertEquals(WifiCountryCode.isValid(null), false);
        assertEquals(WifiCountryCode.isValid("JPUS"), false);
        assertEquals(WifiCountryCode.isValid("JP"), true);
        assertEquals(WifiCountryCode.isValid("00"), true);
        assertEquals(WifiCountryCode.isValid("0U"), true);
    }

    /**
     * Test driver country code is null when there is no active mode.
     */
    @Test
    public void testDriverCountryCodeIsNullWhenNoModeActive() throws Exception {
        // Supplicant started, it will update default country code (US) to driver
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        assertEquals(mWifiCountryCode.getCurrentDriverCountryCode(), mDefaultCountryCode);
        // Remove mode manager.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRemoved(mClientModeManager);
        assertNull(mWifiCountryCode.getCurrentDriverCountryCode());
    }

    /**
     * Test driver country code updated correctly
     * when config_wifiDriverSupportedNl80211RegChangedEvent is true.
     */
    @Test
    public void testDriverCountryCodeUpdateWhenOverlayisTrue() throws Exception {
        mDriverSupportedNl80211RegChangedEvent = true;
        createWifiCountryCode();
        // Supplicant started.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        // Wifi get L2 connected.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);
        verify(mClientModeManager).setCountryCode(anyString());
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
        if (SdkLevel.isAtLeastT()) {
            verify(mWifiNative, never()).countryCodeChanged(any());
        } else if (SdkLevel.isAtLeastS()) {
            verify(mWifiNative).countryCodeChanged(mDefaultCountryCode);
        }
    }

    @Test
    public void testCountryCodeChangedWillNotifyExternalListener()
            throws Exception {
        // External caller register the listener
        mWifiCountryCode.registerListener(mExternalChangeListener);
        // Supplicant started.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        // Wifi get L2 connected.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);
        verify(mClientModeManager).setCountryCode(anyString());
        if (SdkLevel.isAtLeastS()) {
            // It is called in onSetSucceeded which supported from S.
            verify(mWifiNative).countryCodeChanged(mDefaultCountryCode);
        }
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
        verify(mExternalChangeListener).onDriverCountryCodeChanged(mDefaultCountryCode);
    }

    @Test
    public void testSetTelephonyCountryCodeAndUpdateWithEmptyCCReturnFalseWhenDefaultSIMCCExist()
            throws Exception {
        when(mTelephonyManager.getNetworkCountryIso()).thenReturn(mTelephonyCountryCode);
        assertFalse(mWifiCountryCode.setTelephonyCountryCodeAndUpdate(""));
    }

    @Test
    public void testClientModeManagerAndSoftApManagerDoesntImpactEachOther()
            throws Exception {
        // Supplicant started.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        mChangeListenerCaptor.getValue().onSetCountryCodeSucceeded(mDefaultCountryCode);
        verify(mClientModeManager).setCountryCode(anyString());

        // SoftApManager activated, it shouldn't impact to client mode, the times keep 1.
        when(mSoftApManager.getRole()).thenReturn(ActiveModeManager.ROLE_SOFTAP_TETHERED);
        when(mSoftApManager.updateCountryCode(anyString())).thenReturn(true);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mSoftApManager);
        verify(mClientModeManager).setCountryCode(anyString());
        // Verify the SoftAp enable shouldn't trigger the update CC event.
        verify(mSoftApManager, never()).updateCountryCode(anyString());

        // Remove and add client mode manager again.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRemoved(mClientModeManager);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        // Verify the SoftApManager doesn't impact when client mode changed
        verify(mSoftApManager, never()).updateCountryCode(anyString());
        // The mode change should not set country code again
        verify(mClientModeManager).setCountryCode(anyString());

        // Override the mClientModeManager.setCountryCode mock in setUp, do not update driver
        // country code, so both client mode manager and ap mode manager will update country code.
        doAnswer((invocation) -> {
            return true;
        }).when(mClientModeManager).setCountryCode(mSetCountryCodeCaptor.capture());
        // Test telephony CC changed, check both of client mode and softap mode update the CC.
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        verify(mClientModeManager).setCountryCode(mTelephonyCountryCode);
        verify(mSoftApManager).updateCountryCode(mTelephonyCountryCode);
    }

    @Test
    public void testCountryCodeChangedWhenSoftApManagerActive()
            throws Exception {
        // SoftApManager actived
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mSoftApManager);
        // Simulate the country code set succeeded via SoftApManager
        mChangeListenerCaptor.getValue().onSetCountryCodeSucceeded(mDefaultCountryCode);
        verify(mSoftApManager, never()).updateCountryCode(anyString());
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        verify(mSoftApManager).updateCountryCode(mTelephonyCountryCode);
    }

    @Test
    public void testCountryCodeChangedWhenSoftApManagerActiveAndForceSoftApRestart()
            throws Exception {
        mForcedSoftApRestateWhenCountryCodeChanged = true;
        ArgumentCaptor<SoftApModeConfiguration> softApModeConfigCaptor =
                ArgumentCaptor.forClass(SoftApModeConfiguration.class);
        when(mSoftApManager.getSoftApModeConfiguration()).thenReturn(mSoftApModeConfiguration);
        when(mSoftApModeConfiguration.getTargetMode())
                .thenReturn(WifiManager.IFACE_IP_MODE_TETHERED);
        createWifiCountryCode();
        // SoftApManager actived
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mSoftApManager);
        // Simulate the country code set succeeded via SoftApManager
        mChangeListenerCaptor.getValue().onSetCountryCodeSucceeded(mDefaultCountryCode);
        verify(mSoftApManager, never()).updateCountryCode(anyString());
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        verify(mSoftApManager).getSoftApModeConfiguration();
        verify(mActiveModeWarden).stopSoftAp(anyInt());
        verify(mActiveModeWarden).startSoftAp(softApModeConfigCaptor.capture(), any());
        assertEquals(softApModeConfigCaptor.getValue().getCountryCode(), mTelephonyCountryCode);
    }

    @Test
    public void testSetOverrideCountryCodeAndOnCountryCodeChangePending() {
        // External caller register the listener
        mWifiCountryCode.registerListener(mExternalChangeListener);
        mWifiCountryCode.setOverrideCountryCode(TEST_COUNTRY_CODE);
        verify(mExternalChangeListener).onCountryCodeChangePending(TEST_COUNTRY_CODE);
    }

    @Test
    public void testCCIsUpdatedStaDynamicCountryCodeUpdateSupportedEvenIfCmmNotReady() {
        mStaDynamicCountryCodeUpdateSupported = true;
        createWifiCountryCode();
        // Supplicant starts.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        // Wifi get L2 connected.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);

        // Telephony country code arrives when L2 connected (i.e. cmm is not ready).
        // Verify Telephony country code is updated since overlay is true;
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
    }

    private List<ScanDetail> setupScanDetails(String countryCode) {
        List<ScanDetail> scanDetails = new ArrayList<>();
        scanDetails.add(mScanDetail1);
        scanDetails.add(mScanDetail2);
        scanDetails.add(mScanDetail3);
        when(mScanDetail1.getNetworkDetail()).thenReturn(mNetworkDetail1);
        when(mScanDetail2.getNetworkDetail()).thenReturn(mNetworkDetail2);
        when(mScanDetail3.getNetworkDetail()).thenReturn(mNetworkDetail3);
        when(mScanDetail1.getScanResult()).thenReturn(mScanResult1);
        when(mScanDetail2.getScanResult()).thenReturn(mScanResult2);
        when(mScanDetail3.getScanResult()).thenReturn(mScanResult3);
        mScanResult1.level = WifiCountryCode.MIN_SCAN_RSSI_DBM;
        mScanResult2.level = WifiCountryCode.MIN_SCAN_RSSI_DBM;
        mScanResult3.level = WifiCountryCode.MIN_SCAN_RSSI_DBM - 1;
        when(mNetworkDetail1.getSSID()).thenReturn(SSID1);
        when(mNetworkDetail2.getSSID()).thenReturn(SSID1);
        when(mNetworkDetail3.getSSID()).thenReturn(SSID1);
        when(mNetworkDetail1.getCountryCode()).thenReturn(countryCode);
        when(mNetworkDetail2.getCountryCode()).thenReturn(countryCode);
        when(mNetworkDetail3.getCountryCode()).thenReturn(countryCode);
        return scanDetails;
    }

    @Test
    public void testUpdateountryCodeGenericDisabled() {
        mResources.setBoolean(R.bool.config_wifiUpdateCountryCodeFromScanResultGeneric, false);
        mWifiCountryCode.updateCountryCodeFromScanResults(mScanDetails);
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCountryCode());
    }

    @Test
    public void testUpdateountryCodeGenericEnabled() {
        mResources.setBoolean(R.bool.config_wifiUpdateCountryCodeFromScanResultGeneric, true);
        mWifiCountryCode.updateCountryCodeFromScanResults(mScanDetails);
        assertEquals(TEST_COUNTRY_CODE, mWifiCountryCode.getCountryCode());

        mResources.setBoolean(R.bool.config_wifiDriverSupportedNl80211RegChangedEvent, false);
        mChangeListenerCaptor.getValue().onSetCountryCodeSucceeded(TEST_COUNTRY_CODE_2);
        mScanDetails = setupScanDetails(TEST_COUNTRY_CODE_2);
        mWifiCountryCode.updateCountryCodeFromScanResults(mScanDetails);
        assertEquals(TEST_COUNTRY_CODE_2, mWifiCountryCode.getCountryCode());
    }

    @Test
    public void testUpdateCountryCodeGenericWithTelephonyCountryCode() {
        when(mTelephonyManager.getNetworkCountryIso()).thenReturn(TEST_COUNTRY_CODE_2);
        mResources.setBoolean(R.bool.config_wifiUpdateCountryCodeFromScanResultGeneric, true);
        mWifiCountryCode.updateCountryCodeFromScanResults(mScanDetails);
        assertEquals(TEST_COUNTRY_CODE_2, mWifiCountryCode.getCountryCode());
    }

    @Test
    public void testUpdateountryCodeGenericMismatchScanResult() {
        when(mNetworkDetail2.getCountryCode()).thenReturn(TEST_COUNTRY_CODE_2);
        mResources.setBoolean(R.bool.config_wifiUpdateCountryCodeFromScanResultGeneric, true);
        mWifiCountryCode.updateCountryCodeFromScanResults(mScanDetails);
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCountryCode());
    }

    @Test
    public void testUpdateountryCodeGenericOneGoodScanResult() {
        mResources.setBoolean(R.bool.config_wifiUpdateCountryCodeFromScanResultGeneric, true);
        mScanResult2.level = WifiCountryCode.MIN_SCAN_RSSI_DBM - 1;
        mWifiCountryCode.updateCountryCodeFromScanResults(mScanDetails);
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCountryCode());
    }

    @Test
    public void testUpdateountryCodeGenericTwoGoodScanResultUs() {
        mResources.setBoolean(R.bool.config_wifiUpdateCountryCodeFromScanResultGeneric, true);
        mScanDetails = setupScanDetails("US");
        mDefaultCountryCode = "CA";
        when(mSettingsConfigStore.get(WIFI_DEFAULT_COUNTRY_CODE)).thenReturn(mDefaultCountryCode);
        mWifiCountryCode.updateCountryCodeFromScanResults(mScanDetails);
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCountryCode());
    }
}
