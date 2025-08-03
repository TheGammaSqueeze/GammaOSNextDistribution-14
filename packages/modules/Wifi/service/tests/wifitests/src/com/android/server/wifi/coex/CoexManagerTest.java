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

package com.android.server.wifi.coex;


import static android.net.wifi.WifiManager.COEX_RESTRICTION_SOFTAP;
import static android.net.wifi.WifiManager.COEX_RESTRICTION_WIFI_AWARE;
import static android.net.wifi.WifiManager.COEX_RESTRICTION_WIFI_DIRECT;
import static android.net.wifi.WifiScanner.WIFI_BAND_24_GHZ;
import static android.net.wifi.WifiScanner.WIFI_BAND_5_GHZ;
import static android.telephony.TelephonyManager.NETWORK_TYPE_LTE;

import static com.android.server.wifi.coex.CoexUtils.CHANNEL_SET_5_GHZ;
import static com.android.server.wifi.coex.CoexUtils.CHANNEL_SET_5_GHZ_20_MHZ;
import static com.android.server.wifi.coex.CoexUtils.CHANNEL_SET_5_GHZ_40_MHZ;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.ICoexCallback;
import android.os.Handler;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.test.TestLooper;
import android.telephony.AccessNetworkConstants;
import android.telephony.Annotation;
import android.telephony.CarrierConfigManager;
import android.telephony.PhysicalChannelConfig;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.WifiNative;
import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Unit tests for {@link com.android.server.wifi.coex.CoexManager}.
 */
@SmallTest
public class CoexManagerTest extends WifiBaseTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    private static final String FILEPATH_MALFORMED = "assets/coex_malformed.xml";
    private static final String FILEPATH_LTE_40_NEIGHBORING = "assets/coex_lte_40_neighboring.xml";
    private static final String FILEPATH_LTE_46_NEIGHBORING = "assets/coex_lte_46_neighboring.xml";
    private static final String FILEPATH_LTE_40_OVERRIDE = "assets/coex_lte_40_override.xml";
    private static final String FILEPATH_LTE_27_HARMONIC = "assets/coex_lte_27_harmonic.xml";
    private static final String FILEPATH_LTE_7_INTERMOD = "assets/coex_lte_7_intermod.xml";

    private TestLooper mTestLooper;

    @Mock private Context mMockContext;
    @Mock private Resources mMockResources;
    @Mock private WifiNative mMockWifiNative;
    @Mock private TelephonyManager mMockDefaultTelephonyManager;
    @Mock private SubscriptionManager mMockSubscriptionManager;
    private List<SubscriptionInfo> mSubscriptionInfos = new ArrayList<>();
    @Mock private CarrierConfigManager mMockCarrierConfigManager;
    private PersistableBundle mUnrestrictedBundle = new PersistableBundle();
    private PersistableBundle mRestrictedBundle = new PersistableBundle();

    private final ArgumentCaptor<CoexManager.CoexOnSubscriptionsChangedListener>
            mCoexSubscriptionsListenerCaptor = ArgumentCaptor.forClass(
                    CoexManager.CoexOnSubscriptionsChangedListener.class);
    private final ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor =
            ArgumentCaptor.forClass(BroadcastReceiver.class);

    /**
     * Set up TelephonyManager and SubscriptionManager mocks for the given subId before creating
     * a CoexManager. Returns the mock TelephonyManager corresponding to the given subId.
     */
    private TelephonyManager setUpSubIdMocks(int subId) {
        TelephonyManager telephonyManager = mock(TelephonyManager.class);
        when(mMockDefaultTelephonyManager.createForSubscriptionId(subId))
                .thenReturn(telephonyManager);
        SubscriptionInfo subscriptionInfo = mock(SubscriptionInfo.class);
        when(subscriptionInfo.getSubscriptionId()).thenReturn(subId);
        mSubscriptionInfos.add(subscriptionInfo);
        return telephonyManager;
    }

    private CoexManager createCoexManager() {
        mTestLooper = new TestLooper();
        final CoexManager coexManager = new CoexManager(mMockContext, mMockWifiNative,
                mMockDefaultTelephonyManager, mMockSubscriptionManager, mMockCarrierConfigManager,
                new Handler(mTestLooper.getLooper()));
        coexManager.enableVerboseLogging(true);
        return coexManager;
    }

    private PhysicalChannelConfig createMockPhysicalChannelConfig(
            @Annotation.NetworkType int rat, int band,
            int dlFreqKhz, int dlBandwidthKhz, int ulFreqKhz, int ulBandwidthKhz) {
        PhysicalChannelConfig config = mock(PhysicalChannelConfig.class);
        when(config.getNetworkType()).thenReturn(rat);
        when(config.getBand()).thenReturn(band);
        when(config.getDownlinkFrequencyKhz()).thenReturn(dlFreqKhz);
        when(config.getCellBandwidthDownlinkKhz()).thenReturn(dlBandwidthKhz);
        when(config.getUplinkFrequencyKhz()).thenReturn(ulFreqKhz);
        when(config.getCellBandwidthUplinkKhz()).thenReturn(ulBandwidthKhz);
        return config;
    }

    private File createFileFromResource(String configFile) throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream(configFile);
        File file = tempFolder.newFile(configFile.split("/")[1]);

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        FileOutputStream out = new FileOutputStream(file);

        String line;

        while ((line = reader.readLine()) != null) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
        }

        out.flush();
        out.close();
        return file;
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getBoolean(R.bool.config_wifiDefaultCoexAlgorithmEnabled))
                .thenReturn(true);
        when(mMockResources.getString(R.string.config_wifiCoexTableFilepath))
                .thenReturn("");
        when(mMockSubscriptionManager.getAvailableSubscriptionInfoList())
                .thenReturn(mSubscriptionInfos);
        mUnrestrictedBundle.putBoolean(CarrierConfigManager.Wifi
                .KEY_AVOID_5GHZ_SOFTAP_FOR_LAA_BOOL, false);
        mUnrestrictedBundle.putBoolean(CarrierConfigManager.Wifi
                .KEY_AVOID_5GHZ_WIFI_DIRECT_FOR_LAA_BOOL, false);
        mRestrictedBundle.putBoolean(CarrierConfigManager.Wifi
                .KEY_AVOID_5GHZ_SOFTAP_FOR_LAA_BOOL, true);
        mRestrictedBundle.putBoolean(CarrierConfigManager.Wifi
                .KEY_AVOID_5GHZ_WIFI_DIRECT_FOR_LAA_BOOL, true);
    }

    /**
     * Verifies that setCoexUnsafeChannels(Set, int) sets values returned in the getter methods
     * getCoexUnsafeChannels() and getCoexRestrictions().
     */
    @Test
    public void testSetCoexUnsafeChannels_nonNullChannels_returnedInGetters() {
        assumeTrue(SdkLevel.isAtLeastS());
        CoexManager coexManager = createCoexManager();
        List<CoexUnsafeChannel> unsafeChannels = new ArrayList<>();
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6));
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 36));
        final int restrictions = COEX_RESTRICTION_WIFI_DIRECT | COEX_RESTRICTION_SOFTAP
                | COEX_RESTRICTION_WIFI_AWARE;

        coexManager.setCoexUnsafeChannels(unsafeChannels, restrictions);

        assertThat(coexManager.getCoexUnsafeChannels()).containsExactlyElementsIn(unsafeChannels);
        assertThat(coexManager.getCoexRestrictions()).isEqualTo(restrictions);
    }

    /**
     * Verifies that setCoexUnsafeChannels(Set, int) with an null set results in no change to the
     * current CoexUnsafeChannels or restrictions
     */
    @Test
    public void testSetCoexUnsafeChannels_nullChannels_setsEmptySet() {
        CoexManager coexManager = createCoexManager();
        List<CoexUnsafeChannel> unsafeChannels = new ArrayList<>();
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6));
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 36));
        final int restrictions = COEX_RESTRICTION_WIFI_DIRECT | COEX_RESTRICTION_SOFTAP
                | COEX_RESTRICTION_WIFI_AWARE;
        coexManager.setCoexUnsafeChannels(unsafeChannels, restrictions);

        coexManager.setCoexUnsafeChannels(null, 0);

        assertThat(coexManager.getCoexUnsafeChannels()).containsExactlyElementsIn(unsafeChannels);
        assertThat(coexManager.getCoexRestrictions()).isEqualTo(restrictions);
    }

    /**
     * Verifies that setCoexUnsafeChannels(Set, int) with undefined restriction flags results in no
     * change to the current CoexUnsafeChannels or restrictions
     */
    @Test
    public void testSetCoexUnsafeChannels_undefinedRestrictions_setsEmptySet() {
        CoexManager coexManager = createCoexManager();
        List<CoexUnsafeChannel> unsafeChannels = new ArrayList<>();
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6));
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 36));
        final int restrictions = COEX_RESTRICTION_WIFI_DIRECT | COEX_RESTRICTION_SOFTAP
                | COEX_RESTRICTION_WIFI_AWARE;
        coexManager.setCoexUnsafeChannels(unsafeChannels, restrictions);

        coexManager.setCoexUnsafeChannels(Collections.emptyList(), ~restrictions);

        assertThat(coexManager.getCoexUnsafeChannels()).containsExactlyElementsIn(unsafeChannels);
        assertThat(coexManager.getCoexRestrictions()).isEqualTo(restrictions);
    }

    /**
     * Verifies that setCoexUnsafeChannels notifies WifiNative with the set values.
     */
    @Test
    public void testSetCoexUnsafeChannels_notifiesWifiVendorHal() {
        CoexManager coexManager = createCoexManager();
        List<CoexUnsafeChannel> unsafeChannels = new ArrayList<>();
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6));
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 36));
        final int restrictions = COEX_RESTRICTION_WIFI_DIRECT | COEX_RESTRICTION_SOFTAP
                | COEX_RESTRICTION_WIFI_AWARE;
        coexManager.setCoexUnsafeChannels(unsafeChannels, restrictions);

        verify(mMockWifiNative).setCoexUnsafeChannels(unsafeChannels, restrictions);
    }

    /**
     * Verifies that the registered CoexListeners are notified when
     * setCoexUnsafeChannels is called.
     */
    @Test
    public void testRegisteredCoexListener_setCoexUnsafeChannels_listenerIsNotified() {
        CoexManager.CoexListener listener1 = mock(CoexManager.CoexListener.class);
        CoexManager.CoexListener listener2 = mock(CoexManager.CoexListener.class);
        CoexManager coexManager = createCoexManager();
        coexManager.registerCoexListener(listener1);
        coexManager.registerCoexListener(listener2);
        List<CoexUnsafeChannel> unsafeChannels = new ArrayList<>();
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6));
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 36));
        int restrictions = COEX_RESTRICTION_WIFI_DIRECT | COEX_RESTRICTION_SOFTAP
                | COEX_RESTRICTION_WIFI_AWARE;

        coexManager.setCoexUnsafeChannels(unsafeChannels, restrictions);

        verify(listener1).onCoexUnsafeChannelsChanged();
        verify(listener2).onCoexUnsafeChannelsChanged();
    }

    /**
     * Verifies that unregistered CoexListeners are not notified when
     * setCoexUnsafeChannels is called.
     */
    @Test
    public void testUnregisteredCoexListener_setCoexUnsafeChannels_listenerIsNotNotified() {
        CoexManager.CoexListener listener1 = mock(CoexManager.CoexListener.class);
        CoexManager.CoexListener listener2 = mock(CoexManager.CoexListener.class);
        CoexManager coexManager = createCoexManager();
        coexManager.registerCoexListener(listener1);
        coexManager.registerCoexListener(listener2);
        coexManager.unregisterCoexListener(listener1);
        coexManager.unregisterCoexListener(listener2);
        List<CoexUnsafeChannel> unsafeChannels = new ArrayList<>();
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6));
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 36));
        int restrictions = COEX_RESTRICTION_WIFI_DIRECT | COEX_RESTRICTION_SOFTAP
                | COEX_RESTRICTION_WIFI_AWARE;

        coexManager.setCoexUnsafeChannels(unsafeChannels, restrictions);

        verify(listener1, never()).onCoexUnsafeChannelsChanged();
        verify(listener2, never()).onCoexUnsafeChannelsChanged();
    }

    /**
     * Verifies that registered remote CoexCallbacks are notified when
     * setCoexUnsafeChannels is called.
     */
    @Test
    public void testRegisteredRemoteCoexCallback_setCoexUnsafeChannels_callbackIsNotified()
            throws RemoteException {
        ICoexCallback remoteCallback1 = mock(ICoexCallback.class);
        when(remoteCallback1.asBinder()).thenReturn(mock(IBinder.class));
        ICoexCallback remoteCallback2 = mock(ICoexCallback.class);
        when(remoteCallback2.asBinder()).thenReturn(mock(IBinder.class));
        CoexManager coexManager = createCoexManager();
        coexManager.registerRemoteCoexCallback(remoteCallback1);
        coexManager.registerRemoteCoexCallback(remoteCallback2);
        verify(remoteCallback1).onCoexUnsafeChannelsChanged(any(), anyInt());
        verify(remoteCallback2).onCoexUnsafeChannelsChanged(any(), anyInt());
        List<CoexUnsafeChannel> unsafeChannels = new ArrayList<>();
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6));
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 36));
        int restrictions = COEX_RESTRICTION_WIFI_DIRECT | COEX_RESTRICTION_SOFTAP
                | COEX_RESTRICTION_WIFI_AWARE;

        coexManager.setCoexUnsafeChannels(unsafeChannels, restrictions);

        List<CoexUnsafeChannel> unsafeChannelList = new ArrayList<>(unsafeChannels);
        verify(remoteCallback1).onCoexUnsafeChannelsChanged(unsafeChannelList, restrictions);
        verify(remoteCallback2).onCoexUnsafeChannelsChanged(unsafeChannelList, restrictions);
    }

    /**
     * Verifies that unregistered remote CoexCallbacks are not notified when
     * setCoexUnsafeChannels is called.
     */
    @Test
    public void testUnregisteredRemoteCoexCallback_setCoexUnsafeChannels_callbackIsNotNotified()
            throws RemoteException {
        ICoexCallback remoteCallback1 = mock(ICoexCallback.class);
        when(remoteCallback1.asBinder()).thenReturn(mock(IBinder.class));
        ICoexCallback remoteCallback2 = mock(ICoexCallback.class);
        when(remoteCallback2.asBinder()).thenReturn(mock(IBinder.class));
        CoexManager coexManager = createCoexManager();
        coexManager.registerRemoteCoexCallback(remoteCallback1);
        coexManager.registerRemoteCoexCallback(remoteCallback2);
        verify(remoteCallback1).onCoexUnsafeChannelsChanged(any(), anyInt());
        verify(remoteCallback2).onCoexUnsafeChannelsChanged(any(), anyInt());
        coexManager.unregisterRemoteCoexCallback(remoteCallback1);
        coexManager.unregisterRemoteCoexCallback(remoteCallback2);
        List<CoexUnsafeChannel> unsafeChannels = new ArrayList<>();
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6));
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 36));
        int restrictions = COEX_RESTRICTION_WIFI_DIRECT | COEX_RESTRICTION_SOFTAP
                | COEX_RESTRICTION_WIFI_AWARE;

        coexManager.setCoexUnsafeChannels(unsafeChannels, restrictions);

        verify(remoteCallback1, times(1)).onCoexUnsafeChannelsChanged(any(), anyInt());
        verify(remoteCallback2, times(1)).onCoexUnsafeChannelsChanged(any(), anyInt());
    }

    /**
     * Verifies that CoexManager does register as a TelephonyCallback if the default coex algorithm
     * is enabled and a coex table xml file exists and could be read.
     */
    @Test
    public void testTelephonyCallback_defaultAlgorithmEnabledXmlExists_registersWithTelephony()
            throws Exception {
        // config_wifiDefaultCoexAlgorithm defaults to true
        when(mMockResources.getString(R.string.config_wifiCoexTableFilepath))
                .thenReturn(createFileFromResource(FILEPATH_LTE_40_NEIGHBORING).getCanonicalPath());
        final TelephonyManager telephonyManager = setUpSubIdMocks(0);
        createCoexManager();
        verify(mMockSubscriptionManager).addOnSubscriptionsChangedListener(
                any(), mCoexSubscriptionsListenerCaptor.capture());
        mCoexSubscriptionsListenerCaptor.getValue().onSubscriptionsChanged();

        verify(telephonyManager, times(1))
                .registerTelephonyCallback(any(Executor.class), any(TelephonyCallback.class));
    }

    /**
     * Verifies that CoexManager does not register as a TelephonyCallback if the default coex
     * algorithm is disabled.
     */
    @Test
    public void testTelephonyCallback_defaultAlgorithmDisabled_doesNotRegisterWithTelephony()
            throws Exception {
        when(mMockResources.getBoolean(R.bool.config_wifiDefaultCoexAlgorithmEnabled))
                .thenReturn(false);
        when(mMockResources.getString(R.string.config_wifiCoexTableFilepath))
                .thenReturn(createFileFromResource(FILEPATH_LTE_40_NEIGHBORING).getCanonicalPath());
        final TelephonyManager telephonyManager = setUpSubIdMocks(0);
        createCoexManager();

        verify(telephonyManager, never())
                .registerTelephonyCallback(any(Executor.class), any(TelephonyCallback.class));
    }

    /**
     * Verifies that CoexManager returns the correct 2.4Ghz CoexUnsafeChannels for a cell channel
     * in the neighboring LTE band 40.
     */
    @Test
    public void testGetCoexUnsafeChannels_neighboringLte40_returns2gNeighboringChannels()
            throws Exception {
        when(mMockResources.getString(R.string.config_wifiCoexTableFilepath))
                .thenReturn(createFileFromResource(FILEPATH_LTE_40_NEIGHBORING).getCanonicalPath());
        final TelephonyManager telephonyManager = setUpSubIdMocks(0);
        CoexManager coexManager = createCoexManager();
        verify(mMockSubscriptionManager).addOnSubscriptionsChangedListener(
                any(), mCoexSubscriptionsListenerCaptor.capture());
        mCoexSubscriptionsListenerCaptor.getValue().onSubscriptionsChanged();
        final ArgumentCaptor<CoexManager.CoexTelephonyCallback> telephonyCallbackCaptor =
                ArgumentCaptor.forClass(CoexManager.CoexTelephonyCallback.class);
        verify(telephonyManager).registerTelephonyCallback(any(Executor.class),
                telephonyCallbackCaptor.capture());

        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 40, 2399_900, 10_000, 0, 0)
        ));

        assertThat(coexManager.getCoexUnsafeChannels()).containsExactly(
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 1, -50),
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 2, -50),
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 3, -50),
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 4, -50),
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 5, -50)
        );
    }

    /**
     * Verifies that CoexManager returns the correct 5Ghz CoexUnsafeChannels for a cell channel
     * in the neighboring LTE band 46.
     */
    @Test
    public void testGetCoexUnsafeChannels_neighboringLte46_returns5gNeighboringChannels()
            throws Exception {
        when(mMockResources.getString(R.string.config_wifiCoexTableFilepath))
                .thenReturn(createFileFromResource(FILEPATH_LTE_46_NEIGHBORING).getCanonicalPath());
        final TelephonyManager telephonyManager = setUpSubIdMocks(0);
        CoexManager coexManager = createCoexManager();
        verify(mMockSubscriptionManager).addOnSubscriptionsChangedListener(
                any(), mCoexSubscriptionsListenerCaptor.capture());
        mCoexSubscriptionsListenerCaptor.getValue().onSubscriptionsChanged();
        final ArgumentCaptor<CoexManager.CoexTelephonyCallback> telephonyCallbackCaptor =
                ArgumentCaptor.forClass(CoexManager.CoexTelephonyCallback.class);
        verify(telephonyManager).registerTelephonyCallback(any(Executor.class),
                telephonyCallbackCaptor.capture());

        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 46, 5150_000, 10_000, 0, 0)
        ));

        assertThat(coexManager.getCoexUnsafeChannels()).containsExactly(
                new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 32, -50),
                new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 34, -50),
                new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 36, -50),
                new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 38, -50),
                new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 42, -50),
                new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 50, -50)
        );
    }

    /**
     * Verifies that CoexManager returns the correct subset of CoexUnsafeChannels caused by
     * harmonic interference from the example channel 27065 of LTE Band 27.
     */
    @Test
    public void testGetCoexUnsafeChannels_channel27065Example_returnsCorrectWifiChannels()
            throws Exception {
        when(mMockResources.getString(R.string.config_wifiCoexTableFilepath))
                .thenReturn(createFileFromResource(FILEPATH_LTE_27_HARMONIC).getCanonicalPath());
        final TelephonyManager telephonyManager = setUpSubIdMocks(0);
        CoexManager coexManager = createCoexManager();
        verify(mMockSubscriptionManager).addOnSubscriptionsChangedListener(
                any(), mCoexSubscriptionsListenerCaptor.capture());
        mCoexSubscriptionsListenerCaptor.getValue().onSubscriptionsChanged();
        final ArgumentCaptor<CoexManager.CoexTelephonyCallback> telephonyCallbackCaptor =
                ArgumentCaptor.forClass(CoexManager.CoexTelephonyCallback.class);
        verify(telephonyManager).registerTelephonyCallback(any(Executor.class),
                telephonyCallbackCaptor.capture());

        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 27,
                        854_500, 17_000, 809_500, 17_000)
        ));

        List<CoexUnsafeChannel> coexUnsafeChannels = new ArrayList<>();
        for (int channel = 2; channel <= 7; channel += 1) {
            coexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, channel, -50));
        }
        assertThat(coexManager.getCoexUnsafeChannels())
                .containsExactlyElementsIn(coexUnsafeChannels);
    }

    /**
     * Verifies that CoexManager returns the correct subset of CoexUnsafeChannels caused by
     * intermod interference with the example channel 3350 of LTE Band 7.
     */
    @Test
    public void testGetCoexUnsafeChannels_channel3350Example_returnsCorrectWifiChannels()
            throws Exception {
        when(mMockResources.getString(R.string.config_wifiCoexTableFilepath))
                .thenReturn(createFileFromResource(FILEPATH_LTE_7_INTERMOD).getCanonicalPath());
        final TelephonyManager telephonyManager = setUpSubIdMocks(0);
        CoexManager coexManager = createCoexManager();
        verify(mMockSubscriptionManager).addOnSubscriptionsChangedListener(
                any(), mCoexSubscriptionsListenerCaptor.capture());
        mCoexSubscriptionsListenerCaptor.getValue().onSubscriptionsChanged();
        final ArgumentCaptor<CoexManager.CoexTelephonyCallback> telephonyCallbackCaptor =
                ArgumentCaptor.forClass(CoexManager.CoexTelephonyCallback.class);
        verify(telephonyManager).registerTelephonyCallback(any(Executor.class),
                telephonyCallbackCaptor.capture());

        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 7,
                        2680_000, 10_000, 2560_000, 10_000)
        ));

        List<CoexUnsafeChannel> coexUnsafeChannels = new ArrayList<>();
        for (int channel = 4; channel <= 9; channel += 1) {
            coexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, channel, -50));
        }
        assertThat(coexManager.getCoexUnsafeChannels())
                .containsExactlyElementsIn(coexUnsafeChannels);
    }

    /**
     * Verifies that CoexManager returns the full list of 2.4GHz CoexUnsafeChannels excluding the
     * default channel if the entire 2.4GHz band is unsafe.
     */
    @Test
    public void testGetCoexUnsafeChannels_entire2gBandUnsafe_excludesDefault2gChannel()
            throws Exception {
        when(mMockResources.getString(R.string.config_wifiCoexTableFilepath))
                .thenReturn(createFileFromResource(FILEPATH_LTE_40_NEIGHBORING).getCanonicalPath());
        final TelephonyManager telephonyManager = setUpSubIdMocks(0);
        CoexManager coexManager = createCoexManager();
        verify(mMockSubscriptionManager).addOnSubscriptionsChangedListener(
                any(), mCoexSubscriptionsListenerCaptor.capture());
        mCoexSubscriptionsListenerCaptor.getValue().onSubscriptionsChanged();
        final ArgumentCaptor<CoexManager.CoexTelephonyCallback> telephonyCallbackCaptor =
                ArgumentCaptor.forClass(CoexManager.CoexTelephonyCallback.class);
        verify(telephonyManager).registerTelephonyCallback(any(Executor.class),
                telephonyCallbackCaptor.capture());

        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 40, 2399_900, 2000_000, 0, 0)
        ));

        assertThat(coexManager.getCoexUnsafeChannels()).hasSize(13);
        assertThat(coexManager.getCoexUnsafeChannels()).doesNotContain(
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6, -50));
    }

    /**
     * Verifies that CoexManager returns the full list of 5GHz CoexUnsafeChannels excluding the
     * default channel if the entire 5GHz band is unsafe.
     */
    @Test
    public void testGetCoexUnsafeChannels_entire5gBandUnsafe_excludesDefault5gChannel()
            throws Exception {
        when(mMockResources.getString(R.string.config_wifiCoexTableFilepath))
                .thenReturn(createFileFromResource(FILEPATH_LTE_46_NEIGHBORING).getCanonicalPath());
        final TelephonyManager telephonyManager = setUpSubIdMocks(0);
        CoexManager coexManager = createCoexManager();
        verify(mMockSubscriptionManager).addOnSubscriptionsChangedListener(
                any(), mCoexSubscriptionsListenerCaptor.capture());
        mCoexSubscriptionsListenerCaptor.getValue().onSubscriptionsChanged();
        final ArgumentCaptor<CoexManager.CoexTelephonyCallback> telephonyCallbackCaptor =
                ArgumentCaptor.forClass(CoexManager.CoexTelephonyCallback.class);
        verify(telephonyManager).registerTelephonyCallback(any(Executor.class),
                telephonyCallbackCaptor.capture());

        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 46, 5150_000, 2000_000, 0, 0)
        ));

        assertThat(coexManager.getCoexUnsafeChannels()).hasSize(CHANNEL_SET_5_GHZ.size() - 1);
        assertThat(coexManager.getCoexUnsafeChannels()).doesNotContain(
                new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 36, -50));
    }

    /**
     * Verifies that added mock cell channels are used instead of real channels to calculate unsafe
     * channels until the mock cell channels are reset.
     */
    @Test
    public void testGetCoexUnsafeChannels_mockCellChannelsAdded_mockCellChannelsUsed()
            throws Exception {
        when(mMockResources.getString(R.string.config_wifiCoexTableFilepath))
                .thenReturn(createFileFromResource(FILEPATH_LTE_40_NEIGHBORING).getCanonicalPath());
        final TelephonyManager telephonyManager = setUpSubIdMocks(0);
        CoexManager coexManager = createCoexManager();
        verify(mMockSubscriptionManager).addOnSubscriptionsChangedListener(
                any(), mCoexSubscriptionsListenerCaptor.capture());
        mCoexSubscriptionsListenerCaptor.getValue().onSubscriptionsChanged();
        final ArgumentCaptor<CoexManager.CoexTelephonyCallback> telephonyCallbackCaptor =
                ArgumentCaptor.forClass(CoexManager.CoexTelephonyCallback.class);
        verify(telephonyManager).registerTelephonyCallback(any(Executor.class),
                telephonyCallbackCaptor.capture());

        // Mock channels set.
        coexManager.setMockCellChannels(Arrays.asList(
                new CoexUtils.CoexCellChannel(NETWORK_TYPE_LTE, 40, 2399_900, 10_000, 0, 0,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID)));
        // Real channels changed.
        telephonyCallbackCaptor.getValue()
                .onPhysicalChannelConfigChanged(Collections.emptyList());

        // Real channels should be ignored while mock channels are set/
        assertThat(coexManager.getCoexUnsafeChannels()).containsExactly(
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 1, -50),
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 2, -50),
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 3, -50),
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 4, -50),
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 5, -50)
        );

        coexManager.resetMockCellChannels();
        mTestLooper.moveTimeForward(CoexManager.CELL_CHANNEL_IDLE_DELAY_MILLIS);
        mTestLooper.dispatchAll();

        // Real channels should be used when mock channels are reset.
        assertThat(coexManager.getCoexUnsafeChannels()).isEmpty();
    }

    /**
     * Verifies that CoexManager returns the list of channels specified in the override list of a
     * corresponding cell band.
     */
    @Test
    public void testGetCoexUnsafeChannels_overrideExists_overrideChannelsAdded()
            throws Exception {
        when(mMockResources.getString(R.string.config_wifiCoexTableFilepath))
                .thenReturn(createFileFromResource(FILEPATH_LTE_40_OVERRIDE).getCanonicalPath());
        final TelephonyManager telephonyManager = setUpSubIdMocks(0);
        CoexManager coexManager = createCoexManager();
        verify(mMockSubscriptionManager).addOnSubscriptionsChangedListener(
                any(), mCoexSubscriptionsListenerCaptor.capture());
        mCoexSubscriptionsListenerCaptor.getValue().onSubscriptionsChanged();
        final ArgumentCaptor<CoexManager.CoexTelephonyCallback> telephonyCallbackCaptor =
                ArgumentCaptor.forClass(CoexManager.CoexTelephonyCallback.class);
        verify(telephonyManager).registerTelephonyCallback(any(Executor.class),
                telephonyCallbackCaptor.capture());

        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 40, 2399_900, 10_000, 0, 0)
        ));

        List<CoexUnsafeChannel> unsafeChannels = new ArrayList<>();
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6));
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 11));
        for (int channel : CHANNEL_SET_5_GHZ_20_MHZ) {
            unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, channel));
        }
        for (int channel : CHANNEL_SET_5_GHZ_40_MHZ) {
            unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, channel));
        }
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 50));
        unsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 114));
        assertThat(coexManager.getCoexUnsafeChannels()).containsExactlyElementsIn(unsafeChannels);
    }

    /**
     * Verifies that CoexManager returns the full list of 5GHz CoexUnsafeChannels and SoftAP and
     * Wifi Direct restrictions if LAA is active for a subscription that has LAA restriction
     * carrier configs set.
     */
    @Test
    public void testGetCoexUnsafeChannels_LAA_restrict5gSoftApAndWifiDirect()
            throws Exception {
        when(mMockCarrierConfigManager.getConfigForSubId(0))
                .thenReturn(mUnrestrictedBundle);
        when(mMockCarrierConfigManager.getConfigForSubId(1))
                .thenReturn(mRestrictedBundle);
        final TelephonyManager telephonyManager0 = setUpSubIdMocks(0);
        final TelephonyManager telephonyManager1 = setUpSubIdMocks(1);
        CoexManager coexManager = createCoexManager();
        verify(mMockSubscriptionManager).addOnSubscriptionsChangedListener(
                any(), mCoexSubscriptionsListenerCaptor.capture());
        mCoexSubscriptionsListenerCaptor.getValue().onSubscriptionsChanged();
        final ArgumentCaptor<CoexManager.CoexTelephonyCallback> telephonyCallbackCaptor0 =
                ArgumentCaptor.forClass(CoexManager.CoexTelephonyCallback.class);
        verify(telephonyManager0).registerTelephonyCallback(any(Executor.class),
                telephonyCallbackCaptor0.capture());
        final ArgumentCaptor<CoexManager.CoexTelephonyCallback> telephonyCallbackCaptor1 =
                ArgumentCaptor.forClass(CoexManager.CoexTelephonyCallback.class);
        verify(telephonyManager1).registerTelephonyCallback(any(Executor.class),
                telephonyCallbackCaptor1.capture());

        // Unrestricted sub id gets an LAA channel
        telephonyCallbackCaptor0.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(
                        NETWORK_TYPE_LTE, AccessNetworkConstants.EutranBand.BAND_46,
                        5150_000, 1_000, 0, 0)
        ));

        // Unrestricted sub id LAA channel does not trigger the 5g band restriction.
        assertThat(coexManager.getCoexUnsafeChannels()).isEmpty();
        assertThat(coexManager.getCoexRestrictions()).isEqualTo(0);

        // Restricted sub id gets an LAA channel
        telephonyCallbackCaptor1.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(
                        NETWORK_TYPE_LTE, AccessNetworkConstants.EutranBand.BAND_46,
                        5150_000, 1_000, 0, 0)
        ));

        // Restricted sub id LAA channel triggers 5g band restriction.
        assertThat(coexManager.getCoexUnsafeChannels().stream()
                .filter(unsafeChannel -> unsafeChannel.getBand() == WIFI_BAND_5_GHZ)
                .map(unsafeChannel -> unsafeChannel.getChannel()).collect(Collectors.toList()))
                .containsExactlyElementsIn(CHANNEL_SET_5_GHZ);
        assertThat(coexManager.getCoexRestrictions() & COEX_RESTRICTION_SOFTAP)
                .isNotEqualTo(0);
        assertThat(coexManager.getCoexRestrictions() & COEX_RESTRICTION_WIFI_DIRECT)
                .isNotEqualTo(0);
    }

    /**
     * Verifies that carrier configs changing will update the unsafe channels
     */
    @Test
    public void testGetCoexUnsafeChannels_carrierConfigsChanged_updatesUnsafeChannels() {
        // Start with no restrictions in the carrier config
        when(mMockCarrierConfigManager.getConfigForSubId(0)).thenReturn(mUnrestrictedBundle);
        final TelephonyManager telephonyManager = setUpSubIdMocks(0);
        CoexManager coexManager = createCoexManager();
        verify(mMockSubscriptionManager).addOnSubscriptionsChangedListener(
                any(), mCoexSubscriptionsListenerCaptor.capture());
        mCoexSubscriptionsListenerCaptor.getValue().onSubscriptionsChanged();
        final ArgumentCaptor<CoexManager.CoexTelephonyCallback> telephonyCallbackCaptor =
                ArgumentCaptor.forClass(CoexManager.CoexTelephonyCallback.class);
        verify(telephonyManager).registerTelephonyCallback(
                any(Executor.class), telephonyCallbackCaptor.capture());
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(IntentFilter.class), eq(null), any(Handler.class));
        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(
                        NETWORK_TYPE_LTE, AccessNetworkConstants.EutranBand.BAND_46,
                        5150_000, 1_000, 0, 0)
        ));

        // Update the carrier config to have restrictions
        when(mMockCarrierConfigManager.getConfigForSubId(0)).thenReturn(mRestrictedBundle);
        Intent intent = new Intent();
        intent.setAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext, intent);

        // New carrier config values should be reflected in the unsafe channels.
        assertThat(coexManager.getCoexUnsafeChannels().stream()
                .filter(unsafeChannel -> unsafeChannel.getBand() == WIFI_BAND_5_GHZ)
                .map(unsafeChannel -> unsafeChannel.getChannel()).collect(Collectors.toList()))
                .containsExactlyElementsIn(CHANNEL_SET_5_GHZ);
        assertThat(coexManager.getCoexRestrictions() & COEX_RESTRICTION_SOFTAP)
                .isNotEqualTo(0);
        assertThat(coexManager.getCoexRestrictions() & COEX_RESTRICTION_WIFI_DIRECT)
                .isNotEqualTo(0);
    }

    /**
     * Verifies that unsafe channels are calculated on the aggregated cell channels of each sub id.
     */
    @Test
    public void testGetCoexUnsafeChannels_multipleSubIds_combineUnsafeChannels() throws Exception {
        when(mMockResources.getString(R.string.config_wifiCoexTableFilepath))
                .thenReturn(createFileFromResource(FILEPATH_LTE_46_NEIGHBORING).getCanonicalPath());
        final TelephonyManager telephonyManager0 = setUpSubIdMocks(0);
        final TelephonyManager telephonyManager1 = setUpSubIdMocks(1);
        CoexManager coexManager = createCoexManager();
        verify(mMockSubscriptionManager).addOnSubscriptionsChangedListener(
                any(), mCoexSubscriptionsListenerCaptor.capture());
        mCoexSubscriptionsListenerCaptor.getValue().onSubscriptionsChanged();
        final ArgumentCaptor<CoexManager.CoexTelephonyCallback> telephonyCallbackCaptor0 =
                ArgumentCaptor.forClass(CoexManager.CoexTelephonyCallback.class);
        verify(telephonyManager0).registerTelephonyCallback(any(Executor.class),
                telephonyCallbackCaptor0.capture());
        final ArgumentCaptor<CoexManager.CoexTelephonyCallback> telephonyCallbackCaptor1 =
                ArgumentCaptor.forClass(CoexManager.CoexTelephonyCallback.class);
        verify(telephonyManager1).registerTelephonyCallback(any(Executor.class),
                telephonyCallbackCaptor1.capture());


        // Add a PhysicalChannelConfig for one sub id to conflict with the lower half of 5g
        telephonyCallbackCaptor0.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 46, 5000_000, 1000_000, 0, 0)
        ));
        // Add a PhysicalChannelConfig for another sub id to conflict with the upper half of 5g
        telephonyCallbackCaptor1.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 46, 6000_000, 1000_000, 0, 0)
        ));

        // The PhysicalChannelConfigs for both sub ids should cause the entire 5g to be unsafe
        // excluding the default channel.
        assertThat(coexManager.getCoexUnsafeChannels()).hasSize(CHANNEL_SET_5_GHZ.size() - 1);
        assertThat(coexManager.getCoexUnsafeChannels()).doesNotContain(
                new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 36, -50));
    }

    /**
     * Verifies that calling onPhysicalChannelConfigChanged with the same list more than once will
     * only notify listeners the first time.
     */
    @Test
    public void testOnPhysicalChannelConfigChanged_sameChannels_doesNotUpdateListeners()
            throws Exception {
        when(mMockResources.getString(R.string.config_wifiCoexTableFilepath))
                .thenReturn(createFileFromResource(FILEPATH_LTE_40_NEIGHBORING).getCanonicalPath());
        final TelephonyManager telephonyManager = setUpSubIdMocks(0);
        CoexManager coexManager = createCoexManager();
        verify(mMockSubscriptionManager).addOnSubscriptionsChangedListener(
                any(), mCoexSubscriptionsListenerCaptor.capture());
        mCoexSubscriptionsListenerCaptor.getValue().onSubscriptionsChanged();
        final ArgumentCaptor<CoexManager.CoexTelephonyCallback> telephonyCallbackCaptor =
                ArgumentCaptor.forClass(CoexManager.CoexTelephonyCallback.class);
        verify(telephonyManager).registerTelephonyCallback(any(Executor.class),
                telephonyCallbackCaptor.capture());
        CoexManager.CoexListener listener = mock(CoexManager.CoexListener.class);
        coexManager.registerCoexListener(listener);
        ICoexCallback remoteCallback = mock(ICoexCallback.class);
        when(remoteCallback.asBinder()).thenReturn(mock(IBinder.class));
        coexManager.registerRemoteCoexCallback(remoteCallback);

        // Update physical channel configs three times with the same channel
        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 40, 2399_900, 10_000, 0, 0)
        ));
        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 40, 2399_900, 10_000, 0, 0)
        ));
        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 40, 2399_900, 10_000, 0, 0)
        ));
        // Update physical channel configs with a different channel now
        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 41, 2399_900, 10_000, 0, 0)
        ));
        mTestLooper.moveTimeForward(CoexManager.CELL_CHANNEL_IDLE_DELAY_MILLIS);
        mTestLooper.dispatchAll();
        // Update physical channel configs back to the first channel
        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 40, 2399_900, 10_000, 0, 0)
        ));

        // Callbacks should be notified three times:
        //     1) no list -> list 1
        //     2) list 1 -> list 2
        //     3) list 2 -> list 1
        verify(mMockWifiNative, times(3)).setCoexUnsafeChannels(any(), anyInt());
        verify(listener, times(3)).onCoexUnsafeChannelsChanged();
        // The remote callback has an extra call since it was notified on registration.
        verify(remoteCallback, times(4)).onCoexUnsafeChannelsChanged(any(), anyInt());
    }

    /**
     * Verifies that calling onPhysicalChannelConfigChanged with empty cell channels will delay
     * clearing the list.
     */
    @Test
    public void testOnPhysicalChannelConfigChanged_emptyCellChannels_channelClearanceIsDelayed()
            throws Exception {
        when(mMockResources.getString(R.string.config_wifiCoexTableFilepath))
                .thenReturn(createFileFromResource(FILEPATH_LTE_40_NEIGHBORING).getCanonicalPath());
        final TelephonyManager telephonyManager = setUpSubIdMocks(0);
        CoexManager coexManager = createCoexManager();
        verify(mMockSubscriptionManager).addOnSubscriptionsChangedListener(
                any(), mCoexSubscriptionsListenerCaptor.capture());
        mCoexSubscriptionsListenerCaptor.getValue().onSubscriptionsChanged();
        final ArgumentCaptor<CoexManager.CoexTelephonyCallback> telephonyCallbackCaptor =
                ArgumentCaptor.forClass(CoexManager.CoexTelephonyCallback.class);
        verify(telephonyManager).registerTelephonyCallback(any(Executor.class),
                telephonyCallbackCaptor.capture());

        // Update physical channel configs to populate the unsafe channel list
        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 40, 2399_900, 10_000, 0, 0)
        ));
        assertThat(coexManager.getCellChannels()).isNotEmpty();

        // Update physical channel configs to clear unsafe channel list.
        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(new ArrayList<>());
        // List should not be cleared yet since clearance delay isn't over
        assertThat(coexManager.getCoexUnsafeChannels()).isNotEmpty();
        // Simulate clearance delay
        mTestLooper.moveTimeForward(CoexManager.CELL_CHANNEL_IDLE_DELAY_MILLIS);
        mTestLooper.dispatchAll();
        // Unsafe channels should now be cleared
        assertThat(coexManager.getCoexUnsafeChannels()).isEmpty();

        // Repopulate unsafe channels
        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 40, 2399_900, 10_000, 0, 0)
        ));
        assertThat(coexManager.getCellChannels()).isNotEmpty();

        // Update physical channel configs to clear unsafe channel list.
        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(new ArrayList<>());
        // Repopulate unsafe channels again before clearance delay is over
        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 40, 2399_900, 10_000, 0, 0)
        ));
        // Simulate clearance delay
        mTestLooper.moveTimeForward(CoexManager.CELL_CHANNEL_IDLE_DELAY_MILLIS);
        mTestLooper.dispatchAll();
        // Unsafe channels should not be cleared since we got a physical channel config during delay
        assertThat(coexManager.getCoexUnsafeChannels()).isNotEmpty();
    }

    /**
     * Verifies that any pending channel clearances triggered by empty channel lists in
     * onPhysicalChannelConfigChanged will be cancelled if a non-empty list is received.
     */
    @Test
    public void testOnPhysicalChannelConfigChanged_nonEmptyCellChannels_cancelsChannelClearances()
            throws Exception {
        when(mMockResources.getString(R.string.config_wifiCoexTableFilepath))
                .thenReturn(createFileFromResource(FILEPATH_LTE_40_NEIGHBORING).getCanonicalPath());
        final TelephonyManager telephonyManager = setUpSubIdMocks(0);
        CoexManager coexManager = createCoexManager();
        verify(mMockSubscriptionManager).addOnSubscriptionsChangedListener(
                any(), mCoexSubscriptionsListenerCaptor.capture());
        mCoexSubscriptionsListenerCaptor.getValue().onSubscriptionsChanged();
        final ArgumentCaptor<CoexManager.CoexTelephonyCallback> telephonyCallbackCaptor =
                ArgumentCaptor.forClass(CoexManager.CoexTelephonyCallback.class);
        verify(telephonyManager).registerTelephonyCallback(any(Executor.class),
                telephonyCallbackCaptor.capture());
        // Populate channel list
        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 40, 2399_900, 10_000, 0, 0)
        ));
        assertThat(coexManager.getCellChannels()).isNotEmpty();

        // Schedule a channel clearance.
        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(new ArrayList<>());
        // List should not be empty yet since delay isn't over
        assertThat(coexManager.getCoexUnsafeChannels()).isNotEmpty();
        // Populate channel list to cancel first channel clearance
        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 40, 2399_900, 10_000, 0, 0)
        ));
        // Schedule a second clearance to be done after the first clearance time
        mTestLooper.moveTimeForward(CoexManager.CELL_CHANNEL_IDLE_DELAY_MILLIS / 2);
        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(new ArrayList<>());

        // Simulate the first clearance time. List should be non-empty since this clearance was
        // cancelled.
        mTestLooper.moveTimeForward(CoexManager.CELL_CHANNEL_IDLE_DELAY_MILLIS / 2);
        mTestLooper.dispatchAll();
        assertThat(coexManager.getCoexUnsafeChannels()).isNotEmpty();

        // Simulate the second clearance time. List should be empty since this clearance was
        // not cancelled.
        mTestLooper.moveTimeForward(CoexManager.CELL_CHANNEL_IDLE_DELAY_MILLIS / 2);
        mTestLooper.dispatchAll();
        assertThat(coexManager.getCoexUnsafeChannels()).isEmpty();
    }

    /**
     * Verify that multiple calls to setCoexUnsafeChannels with the same unsafe channels will only
     * update the driver the first time.
     */
    @Test
    public void testSetCoexUnsafeChannels_multipleSameChannels_updatesDriverOnce() {
        CoexManager coexManager = createCoexManager();

        coexManager.setCoexUnsafeChannels(new ArrayList<>(), 0);
        coexManager.setCoexUnsafeChannels(new ArrayList<>(), 0);
        coexManager.setCoexUnsafeChannels(new ArrayList<>(), 0);
        // Default state after initialization is no channels and no restrictions, so no update to
        // the driver is necessary
        verify(mMockWifiNative, times(0)).setCoexUnsafeChannels(any(), anyInt());

        coexManager.setCoexUnsafeChannels(
                Arrays.asList(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6)), COEX_RESTRICTION_SOFTAP);
        coexManager.setCoexUnsafeChannels(
                Arrays.asList(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6)), COEX_RESTRICTION_SOFTAP);
        coexManager.setCoexUnsafeChannels(
                Arrays.asList(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6)), COEX_RESTRICTION_SOFTAP);
        // Driver should be updated only once for the same unsafe channels
        verify(mMockWifiNative, times(1)).setCoexUnsafeChannels(any(), anyInt());

        // Change in restrictions with same unsafe channels should trigger an update
        coexManager.setCoexUnsafeChannels(
                Arrays.asList(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6)), 0);
        verify(mMockWifiNative, times(2)).setCoexUnsafeChannels(any(), anyInt());
    }

    /**
     * Verifies that CoexUnsafeChannels due to GPS are correctly added when the current cell
     * channels impact GPS with the 2.4GHz band.
     */
    @Test
    public void testOnPhysicalChannelConfigChanged_unsafeChannelsDueToGps_updatesUnsafeChannels() {
        when(mMockResources.getBoolean(R.bool.config_wifiCoexForGpsL1)).thenReturn(true);
        when(mMockResources.getInteger(R.integer.config_wifiCoexGpsL1ThresholdKhz))
                .thenReturn(5_000);
        final TelephonyManager telephonyManager = setUpSubIdMocks(0);
        CoexManager coexManager = createCoexManager();
        verify(mMockSubscriptionManager).addOnSubscriptionsChangedListener(
                any(), mCoexSubscriptionsListenerCaptor.capture());
        mCoexSubscriptionsListenerCaptor.getValue().onSubscriptionsChanged();
        final ArgumentCaptor<CoexManager.CoexTelephonyCallback> telephonyCallbackCaptor =
                ArgumentCaptor.forClass(CoexManager.CoexTelephonyCallback.class);
        verify(telephonyManager).registerTelephonyCallback(any(Executor.class),
                telephonyCallbackCaptor.capture());
        // Populate channel list with a channel that impacts GPS with the 2.4GHz band.
        telephonyCallbackCaptor.getValue().onPhysicalChannelConfigChanged(Arrays.asList(
                createMockPhysicalChannelConfig(NETWORK_TYPE_LTE, 40, 0, 0, 861_580, 10_000)
        ));
        List<CoexUnsafeChannel> unsafeChannels = coexManager.getCoexUnsafeChannels();
        assertThat(unsafeChannels).isNotEmpty();
        assertThat(unsafeChannels).containsExactly(
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 2),
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 3),
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 4),
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 5),
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 6),
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 7),
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 8),
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 9),
                new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 10));
    }
}
