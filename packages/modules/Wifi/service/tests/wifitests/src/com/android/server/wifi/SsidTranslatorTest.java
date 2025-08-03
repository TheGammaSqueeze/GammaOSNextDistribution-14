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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiSsid;
import android.os.Handler;
import android.os.LocaleList;

import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Unit tests for SsidTranslator.
 */
public class SsidTranslatorTest extends WifiBaseTest{
    private @Mock WifiContext mWifiContext;
    private @Mock Resources mResources;
    private @Mock Configuration mConfiguration;
    private @Mock LocaleList mLocaleList;
    private @Mock Locale mLocale;
    private @Mock Handler mHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mWifiContext.getResources()).thenReturn(mResources);
        when(mResources.getConfiguration()).thenReturn(mConfiguration);
        when(mConfiguration.getLocales()).thenReturn(mLocaleList);
        when(mLocaleList.get(0)).thenReturn(mLocale);
        when(mLocale.getLanguage()).thenReturn("en");
        when(mResources.getStringArray(R.array.config_wifiCharsetsForSsidTranslation))
                .thenReturn(new String[]{
                        "all,ISO-8859-8",
                        "zh,GBK",
                        "ko,EUC-KR",
                });
    }

    /**
     * Verifies behavior of {@link SsidTranslator#getTranslatedSsid(WifiSsid)}.
     */
    @Test
    public void testGetTranslatedSsid() throws Exception {
        ArgumentCaptor<BroadcastReceiver> broadcastReceiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        SsidTranslator ssidTranslator = new SsidTranslator(mWifiContext, mHandler);
        ssidTranslator.handleBootCompleted();
        verify(mWifiContext).registerReceiver(
                broadcastReceiverCaptor.capture(), any(), eq(null), eq(mHandler));
        WifiSsid utf8Ssid = WifiSsid.fromBytes("安卓".getBytes(StandardCharsets.UTF_8));
        WifiSsid iso8859_8Ssid = WifiSsid.fromBytes("שלום".getBytes("ISO-8859-8"));
        WifiSsid gbkSsid = WifiSsid.fromBytes("安卓".getBytes("GBK"));
        WifiSsid eucKrSsid = WifiSsid.fromBytes("안드로이드".getBytes("EUC-KR"));
        WifiSsid unknownSsid = WifiSsid.fromBytes(new byte[]{
                (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd, (byte) 0xee, (byte) 0xff});

        // Current Locale language is "en", so only ISO-8859-8 from the "all" list is translated.
        assertThat(ssidTranslator.getTranslatedSsid(utf8Ssid)).isEqualTo(utf8Ssid);
        assertThat(ssidTranslator.getTranslatedSsid(iso8859_8Ssid))
                .isEqualTo(WifiSsid.fromBytes("שלום".getBytes(StandardCharsets.UTF_8)));
        assertThat(ssidTranslator.getTranslatedSsid(gbkSsid)).isEqualTo(gbkSsid);
        assertThat(ssidTranslator.getTranslatedSsid(eucKrSsid)).isEqualTo(eucKrSsid);
        assertThat(ssidTranslator.getTranslatedSsid(unknownSsid)).isEqualTo(unknownSsid);

        // Switch Locale language to "zh", GBK SSIDs should be translated.
        when(mLocale.getLanguage()).thenReturn("zh");
        broadcastReceiverCaptor.getValue().onReceive(mWifiContext,
                new Intent(Intent.ACTION_LOCALE_CHANGED));
        assertThat(ssidTranslator.getTranslatedSsid(gbkSsid))
                .isEqualTo(WifiSsid.fromBytes("安卓".getBytes(StandardCharsets.UTF_8)));
        // EUC-KR SSID is translated as gibberish (but valid) GBK.
        assertThat(ssidTranslator.getTranslatedSsid(eucKrSsid))
                .isEqualTo(WifiSsid.fromBytes("救靛肺捞靛".getBytes(StandardCharsets.UTF_8)));
        // UTF-8 SSID is not translated since UTF-8 takes precedence over alternate charsets, even
        // though it's valid GBK.
        assertThat(ssidTranslator.getTranslatedSsid(utf8Ssid)).isEqualTo(utf8Ssid);
        assertThat(ssidTranslator.getTranslatedSsid(unknownSsid)).isEqualTo(unknownSsid);

        // Switch Locale language to "ko", EUC-KR SSIDs should be translated.
        when(mLocale.getLanguage()).thenReturn("ko");
        broadcastReceiverCaptor.getValue().onReceive(mWifiContext,
                new Intent(Intent.ACTION_LOCALE_CHANGED));
        assertThat(ssidTranslator.getTranslatedSsid(eucKrSsid))
                .isEqualTo(WifiSsid.fromBytes("안드로이드".getBytes(StandardCharsets.UTF_8)));
        // GBK SSID is translated as gibberish (but valid) EUC-KR.
        assertThat(ssidTranslator.getTranslatedSsid(gbkSsid))
                .isEqualTo(WifiSsid.fromBytes("갛六".getBytes(StandardCharsets.UTF_8)));
        // UTF-8 SSID is not translated since UTF-8 takes precedence over alternate charsets, even
        // though it's valid EUC-KR
        assertThat(ssidTranslator.getTranslatedSsid(utf8Ssid)).isEqualTo(utf8Ssid);
        assertThat(ssidTranslator.getTranslatedSsid(unknownSsid)).isEqualTo(unknownSsid);
    }

    /**
     * Verifies behavior of {@link SsidTranslator#getTranslatedSsidForStaIface(WifiSsid, String)}.
     */
    @Test
    public void testGetTranslatedSsidForStaIface() throws Exception {
        ArgumentCaptor<BroadcastReceiver> broadcastReceiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        SsidTranslator ssidTranslator = new SsidTranslator(mWifiContext, mHandler);
        ssidTranslator.handleBootCompleted();
        verify(mWifiContext).registerReceiver(
                broadcastReceiverCaptor.capture(), any(), eq(null), eq(mHandler));
        when(mLocale.getLanguage()).thenReturn("zh");
        broadcastReceiverCaptor.getValue().onReceive(mWifiContext,
                new Intent(Intent.ACTION_LOCALE_CHANGED));
        String staIface = "wlan0";
        // SSID may be "安卓" in UTF-8 or "瀹夊崜" in GBK
        String utf8Interpretation = "\"安卓\"";
        String gbkInterpretation = "\"瀹夊崜\"";
        WifiSsid ambiguousSsid = WifiSsid.fromString(utf8Interpretation);

        // Default translation prioritizes UTF-8 over GBK, so the SSID is translated as "安卓"
        assertThat(ssidTranslator.getTranslatedSsidForStaIface(ambiguousSsid, staIface)
                .toString()).isEqualTo(utf8Interpretation);

        // Record that we're actually connecting with "瀹夊崜" as the intended SSID for this iface.
        ssidTranslator.setTranslatedSsidForStaIface(
                WifiSsid.fromString(gbkInterpretation), staIface);

        // The translated SSID for the iface should map to the GBK interpretation now.
        assertThat(ssidTranslator.getTranslatedSsidForStaIface(ambiguousSsid, staIface)
                .toString()).isEqualTo(gbkInterpretation);
    }

    /**
     * Verifies behavior of {@link SsidTranslator#getAllPossibleOriginalSsids(WifiSsid)} .
     */
    @Test
    public void testGetAllPossibleOriginalSsids() throws Exception {
        ArgumentCaptor<BroadcastReceiver> broadcastReceiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        SsidTranslator ssidTranslator = new SsidTranslator(mWifiContext, mHandler);
        ssidTranslator.handleBootCompleted();
        verify(mWifiContext).registerReceiver(
                broadcastReceiverCaptor.capture(), any(), eq(null), eq(mHandler));
        WifiSsid utf8Ssid = WifiSsid.fromBytes("これはSSIDです。".getBytes(StandardCharsets.UTF_8));
        WifiSsid gbkSsid = WifiSsid.fromBytes("これはSSIDです。".getBytes("GBK"));
        WifiSsid eucKrSsid = WifiSsid.fromBytes("これはSSIDです。".getBytes("EUC-KR"));
        WifiSsid unknownSsid = WifiSsid.fromBytes(new byte[]{
                (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd, (byte) 0xee, (byte) 0xff});

        // Current Locale language is "en", so only UTF-8 is possible.
        assertThat(ssidTranslator.getAllPossibleOriginalSsids(utf8Ssid)).containsExactly(utf8Ssid);
        // Non-UTF-8 encodings are already in their "original" form.
        assertThat(ssidTranslator.getAllPossibleOriginalSsids(gbkSsid)).containsExactly(gbkSsid);
        assertThat(ssidTranslator.getAllPossibleOriginalSsids(eucKrSsid))
                .containsExactly(eucKrSsid);
        assertThat(ssidTranslator.getAllPossibleOriginalSsids(unknownSsid))
                .containsExactly(unknownSsid);

        // Switch Locale language to "zh", GBK should be returned.
        when(mLocale.getLanguage()).thenReturn("zh");
        broadcastReceiverCaptor.getValue().onReceive(mWifiContext,
                new Intent(Intent.ACTION_LOCALE_CHANGED));
        assertThat(ssidTranslator.getAllPossibleOriginalSsids(utf8Ssid)).containsExactly(
                utf8Ssid, gbkSsid);

        // Switch Locale language to "ko", EUC-KR should be returned.
        when(mLocale.getLanguage()).thenReturn("ko");
        broadcastReceiverCaptor.getValue().onReceive(mWifiContext,
                new Intent(Intent.ACTION_LOCALE_CHANGED));
        assertThat(ssidTranslator.getAllPossibleOriginalSsids(utf8Ssid)).containsExactly(
                utf8Ssid, eucKrSsid);

        // SSID is too long for UTF-8, but can be represented in EUC-KR.
        WifiSsid longUtf8Ssid = WifiSsid.fromBytes(
                "漢字漢字漢字漢字漢字漢字".getBytes(StandardCharsets.UTF_8));
        WifiSsid longEucKrSsid = WifiSsid.fromBytes(
                "漢字漢字漢字漢字漢字漢字".getBytes("EUC-KR"));
        assertThat(ssidTranslator.getAllPossibleOriginalSsids(longUtf8Ssid)).containsExactly(
                longEucKrSsid);

        // SSID is too long for any encoding, so return an empty list.
        assertThat(ssidTranslator.getAllPossibleOriginalSsids(WifiSsid.fromBytes(
                "こんにちは! This is an SSID!!!!!!!!!!!!!!!!!!!!".getBytes(StandardCharsets.UTF_8))))
                .isEmpty();
    }

    /**
     * Verifies behavior of {@link SsidTranslator#getOriginalSsid(WifiSsid, MacAddress)}.
     */
    @Test
    public void testGetOriginalSsid() throws Exception {
        ArgumentCaptor<BroadcastReceiver> broadcastReceiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        ArgumentCaptor<Runnable> timeoutRunnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        List<Runnable> timeoutRunnables = new ArrayList<>();
        SsidTranslator ssidTranslator = new SsidTranslator(mWifiContext, mHandler);
        ssidTranslator.handleBootCompleted();
        verify(mWifiContext).registerReceiver(
                broadcastReceiverCaptor.capture(), any(), eq(null), eq(mHandler));
        WifiSsid utf8Ssid = WifiSsid.fromBytes("安卓".getBytes(StandardCharsets.UTF_8));
        WifiSsid gbkSsid = WifiSsid.fromBytes("安卓".getBytes("GBK"));
        MacAddress utf8MacAddress = MacAddress.fromBytes(new byte[]{
                (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa});
        MacAddress gbkMacAddress = MacAddress.fromBytes(new byte[]{
                (byte) 0xbb, (byte) 0xbb, (byte) 0xbb, (byte) 0xbb, (byte) 0xbb, (byte) 0xbb});

        when(mLocale.getLanguage()).thenReturn("zh");
        broadcastReceiverCaptor.getValue().onReceive(mWifiContext,
                new Intent(Intent.ACTION_LOCALE_CHANGED));

        // BSSID does not match any seen scan results, use UTF-8.
        assertThat(ssidTranslator.getOriginalSsid(utf8Ssid, null)).isEqualTo(utf8Ssid);
        assertThat(ssidTranslator.getOriginalSsid(utf8Ssid, utf8MacAddress)).isEqualTo(utf8Ssid);
        assertThat(ssidTranslator.getOriginalSsid(utf8Ssid, gbkMacAddress)).isEqualTo(utf8Ssid);

        // Record a BSSID using GBK. All non-matching BSSIDs should return GBK.
        ssidTranslator.getTranslatedSsidAndRecordBssidCharset(gbkSsid, gbkMacAddress);
        verify(mHandler, times(1)).postDelayed(
                timeoutRunnableCaptor.capture(), eq(SsidTranslator.BSSID_CACHE_TIMEOUT_MS));
        timeoutRunnables.add(timeoutRunnableCaptor.getValue());
        assertThat(ssidTranslator.getOriginalSsid(utf8Ssid, null)).isEqualTo(gbkSsid);
        assertThat(ssidTranslator.getOriginalSsid(utf8Ssid, utf8MacAddress)).isEqualTo(gbkSsid);
        assertThat(ssidTranslator.getOriginalSsid(utf8Ssid, gbkMacAddress)).isEqualTo(gbkSsid);

        // Record a BSSID using UTF-8. The UTF-8 and null BSSIDs should return UTF-8 now.
        ssidTranslator.getTranslatedSsidAndRecordBssidCharset(utf8Ssid, utf8MacAddress);
        verify(mHandler, times(2)).postDelayed(
                timeoutRunnableCaptor.capture(), eq(SsidTranslator.BSSID_CACHE_TIMEOUT_MS));
        timeoutRunnables.add(timeoutRunnableCaptor.getValue());
        assertThat(ssidTranslator.getOriginalSsid(utf8Ssid, null)).isEqualTo(utf8Ssid);
        assertThat(ssidTranslator.getOriginalSsid(utf8Ssid, utf8MacAddress)).isEqualTo(utf8Ssid);
        assertThat(ssidTranslator.getOriginalSsid(utf8Ssid, gbkMacAddress)).isEqualTo(gbkSsid);

        // Untranslated SSIDs should not be changed since this means these SSIDs were originally
        // untranslatable.
        WifiSsid unknownSsid = WifiSsid.fromBytes(new byte[]{
                (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd, (byte) 0xee, (byte) 0xff});
        assertThat(ssidTranslator.getOriginalSsid(unknownSsid, null)).isEqualTo(unknownSsid);

        // Timeout the scan results. Now we should return UTF-8 SSIDs for every BSSID again.
        for (Runnable runnable : timeoutRunnables) {
            runnable.run();
        }
        assertThat(ssidTranslator.getOriginalSsid(utf8Ssid, null)).isEqualTo(utf8Ssid);
        assertThat(ssidTranslator.getOriginalSsid(utf8Ssid, utf8MacAddress)).isEqualTo(utf8Ssid);
        assertThat(ssidTranslator.getOriginalSsid(utf8Ssid, gbkMacAddress)).isEqualTo(utf8Ssid);

        // SSID is too long for any encoding, so return null.
        assertThat(ssidTranslator.getOriginalSsid(WifiSsid.fromBytes(
                "こんにちは! This is an SSID!!!!!!!!!!!!!!!!!!!!".getBytes(StandardCharsets.UTF_8)),
                null))
                .isNull();
    }

    /**
     * Verifies behavior of {@link SsidTranslator#getOriginalSsid(WifiConfiguration)}.
     */
    @Test
    public void testGetOriginalSsidForWifiConfiguration() throws Exception {
        ArgumentCaptor<BroadcastReceiver> broadcastReceiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        ArgumentCaptor<Runnable> timeoutRunnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        List<Runnable> timeoutRunnables = new ArrayList<>();
        SsidTranslator ssidTranslator = new SsidTranslator(mWifiContext, mHandler);
        ssidTranslator.handleBootCompleted();
        verify(mWifiContext).registerReceiver(
                broadcastReceiverCaptor.capture(), any(), eq(null), eq(mHandler));
        WifiSsid utf8Ssid = WifiSsid.fromBytes("安卓".getBytes(StandardCharsets.UTF_8));
        WifiSsid gbkSsid = WifiSsid.fromBytes("安卓".getBytes("GBK"));
        MacAddress utf8MacAddress = MacAddress.fromBytes(new byte[]{
                (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa});
        MacAddress gbkMacAddress = MacAddress.fromBytes(new byte[]{
                (byte) 0xbb, (byte) 0xbb, (byte) 0xbb, (byte) 0xbb, (byte) 0xbb, (byte) 0xbb});

        when(mLocale.getLanguage()).thenReturn("zh");
        broadcastReceiverCaptor.getValue().onReceive(mWifiContext,
                new Intent(Intent.ACTION_LOCALE_CHANGED));

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"安卓\"";
        ScanResult candidate = new ScanResult();
        WifiConfiguration.NetworkSelectionStatus status = config.getNetworkSelectionStatus();
        status.setCandidate(candidate);

        // BSSID does not match any seen scan results, use UTF-8.
        status.setNetworkSelectionBSSID(null);
        candidate.BSSID = null;
        // Using candidate BSSID
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(utf8Ssid);
        candidate.BSSID = "any";
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(utf8Ssid);
        candidate.BSSID = utf8MacAddress.toString();
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(utf8Ssid);
        candidate.BSSID = gbkMacAddress.toString();
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(utf8Ssid);
        // Using network selection BSSID (should override candidate)
        status.setNetworkSelectionBSSID("any");
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(utf8Ssid);
        status.setNetworkSelectionBSSID(utf8MacAddress.toString());
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(utf8Ssid);
        status.setNetworkSelectionBSSID(gbkMacAddress.toString());
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(utf8Ssid);

        // Record a BSSID using GBK. All non-matching BSSIDs should return GBK.
        ssidTranslator.getTranslatedSsidAndRecordBssidCharset(gbkSsid, gbkMacAddress);
        verify(mHandler, times(1)).postDelayed(
                timeoutRunnableCaptor.capture(), eq(SsidTranslator.BSSID_CACHE_TIMEOUT_MS));
        timeoutRunnables.add(timeoutRunnableCaptor.getValue());
        status.setNetworkSelectionBSSID(null);
        candidate.BSSID = null;
        // Using candidate BSSID
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(gbkSsid);
        candidate.BSSID = "any";
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(gbkSsid);
        candidate.BSSID = utf8MacAddress.toString();
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(gbkSsid);
        candidate.BSSID = gbkMacAddress.toString();
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(gbkSsid);
        // Using network selection BSSID (should override candidate)
        status.setNetworkSelectionBSSID("any");
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(gbkSsid);
        status.setNetworkSelectionBSSID(utf8MacAddress.toString());
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(gbkSsid);
        status.setNetworkSelectionBSSID(gbkMacAddress.toString());
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(gbkSsid);

        // Record a BSSID using UTF-8. The UTF-8 and null BSSIDs should return UTF-8 now.
        ssidTranslator.getTranslatedSsidAndRecordBssidCharset(utf8Ssid, utf8MacAddress);
        verify(mHandler, times(2)).postDelayed(
                timeoutRunnableCaptor.capture(), eq(SsidTranslator.BSSID_CACHE_TIMEOUT_MS));
        timeoutRunnables.add(timeoutRunnableCaptor.getValue());
        status.setNetworkSelectionBSSID(null);
        candidate.BSSID = null;
        // Using candidate BSSID
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(utf8Ssid);
        candidate.BSSID = "any";
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(utf8Ssid);
        candidate.BSSID = utf8MacAddress.toString();
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(utf8Ssid);
        candidate.BSSID = gbkMacAddress.toString();
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(gbkSsid);
        // Using network selection BSSID (should override candidate)
        status.setNetworkSelectionBSSID("any");
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(gbkSsid);
        status.setNetworkSelectionBSSID(utf8MacAddress.toString());
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(utf8Ssid);
        status.setNetworkSelectionBSSID(gbkMacAddress.toString());
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(gbkSsid);

        // Untranslated SSIDs should not be changed since this means these SSIDs were originally
        // untranslatable.
        WifiSsid unknownSsid = WifiSsid.fromBytes(new byte[]{
                (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd, (byte) 0xee, (byte) 0xff});
        config.SSID = unknownSsid.toString();
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(unknownSsid);

        // Timeout the scan results. Now we should return UTF-8 SSIDs for every BSSID again.
        for (Runnable runnable : timeoutRunnables) {
            runnable.run();
        }
        status.setNetworkSelectionBSSID(null);
        candidate.BSSID = null;
        config.SSID = "\"安卓\"";
        // Using candidate BSSID
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(utf8Ssid);
        candidate.BSSID = "any";
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(utf8Ssid);
        candidate.BSSID = utf8MacAddress.toString();
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(utf8Ssid);
        candidate.BSSID = gbkMacAddress.toString();
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(utf8Ssid);
        // Using network selection BSSID (should override candidate)
        status.setNetworkSelectionBSSID("any");
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(utf8Ssid);
        status.setNetworkSelectionBSSID(utf8MacAddress.toString());
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(utf8Ssid);
        status.setNetworkSelectionBSSID(gbkMacAddress.toString());
        assertThat(ssidTranslator.getOriginalSsid(config)).isEqualTo(utf8Ssid);

        // SSID is too long for any encoding, so return null.
        config.SSID = "\"こんにちは! This is an SSID!!!!!!!!!!!!!!!!!!!!\"";
        assertThat(ssidTranslator.getOriginalSsid(config)).isNull();
    }
}
