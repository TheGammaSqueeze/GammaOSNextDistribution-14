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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.MacAddress;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiSsid;
import android.os.Handler;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.wifi.resources.R;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Utility class to translate between non-UTF-8 SSIDs in the Native layer and UTF-8 SSIDs in the
 * Framework for SSID Translation.
 *
 * SSID Translation is intended to provide backwards compatibility with legacy apps that do not
 * recognize non-UTF-8 SSIDs. Translating non-UTF-8 SSIDs from Native->Framework into UTF-8
 * (and back) will effectively switch all non-UTF-8 APs into UTF-8 APs from the perspective of the
 * Framework and apps.
 *
 * The list of alternate non-UTF-8 character sets to translate is defined in
 * R.string.config_wifiCharsetsForSsidTranslation.
 *
 * This class is thread-safe.
 */
public class SsidTranslator {
    private static final String TAG = "SsidTranslator";
    private static final String LOCALE_LANGUAGE_ALL = "all";
    @VisibleForTesting static final long BSSID_CACHE_TIMEOUT_MS = 30_000;
    private final @NonNull WifiContext mWifiContext;
    private final @NonNull Handler mWifiHandler;

    private @Nullable Charset mCurrentLocaleAlternateCharset = null;
    private @NonNull Map<String, Charset> mCharsetsPerLocaleLanguage = new HashMap<>();
    private @NonNull Map<String, Charset> mMockCharsetsPerLocaleLanguage = new HashMap<>();

    // Maps a translated SSID to all of its BSSIDs using the alternate Charset.
    private @NonNull Map<WifiSsid, Set<MacAddress>> mTranslatedBssids = new ArrayMap<>();
    // Maps a translated SSID to all of its BSSIDs not using the alternate Charset.
    private @NonNull Map<WifiSsid, Set<MacAddress>> mUntranslatedBssids = new ArrayMap<>();
    private final Map<Pair<WifiSsid, MacAddress>, Runnable> mUntranslatedBssidTimeoutRunnables =
            new ArrayMap<>();
    private final Map<Pair<WifiSsid, MacAddress>, Runnable> mTranslatedBssidTimeoutRunnables =
            new ArrayMap<>();
    private final Map<String, WifiSsid> mTranslatedSsidForStaIface = new ArrayMap<>();

    public SsidTranslator(@NonNull WifiContext wifiContext, @NonNull Handler wifiHandler) {
        mWifiContext = wifiContext;
        mWifiHandler = wifiHandler;
    }

    /**
     * Initializes SsidTranslator after boot completes to get boot-dependent resources.
     */
    public synchronized void handleBootCompleted() {
        Resources res = mWifiContext.getResources();
        if (res == null) {
            Log.e(TAG, "Boot completed but could not get resources!");
            return;
        }
        String[] charsetCsvs = res.getStringArray(
                R.array.config_wifiCharsetsForSsidTranslation);
        if (charsetCsvs == null) {
            return;
        }
        for (String charsetCsv : charsetCsvs) {
            String[] charsetNames = charsetCsv.split(",");
            if (charsetNames.length != 2) {
                continue;
            }
            String localeLanguage = charsetNames[0];
            Charset charset;
            try {
                charset = Charset.forName(charsetNames[1]);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Could not find Charset with name " + charsetNames[1]);
                continue;
            }
            mCharsetsPerLocaleLanguage.put(localeLanguage, charset);
        }
        mWifiContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!Intent.ACTION_LOCALE_CHANGED.equals(intent.getAction())) {
                    return;
                }
                updateCurrentLocaleCharset();
            }
        }, new IntentFilter(Intent.ACTION_LOCALE_CHANGED), null, mWifiHandler);
        updateCurrentLocaleCharset();
    }

    /** Updates mCurrentLocaleCharset to the alternate charset of the current Locale language. */
    private synchronized void updateCurrentLocaleCharset() {
        // Clear existing Charset mappings.
        for (Runnable runnable : mTranslatedBssidTimeoutRunnables.values()) {
            mWifiHandler.removeCallbacks(runnable);
        }
        mTranslatedBssidTimeoutRunnables.clear();
        mTranslatedBssids.clear();
        for (Runnable runnable : mUntranslatedBssidTimeoutRunnables.values()) {
            mWifiHandler.removeCallbacks(runnable);
        }
        mUntranslatedBssidTimeoutRunnables.clear();
        mUntranslatedBssids.clear();
        mCurrentLocaleAlternateCharset = null;
        // Try to find the Charset for the specific language.
        String language = null;
        Resources res = mWifiContext.getResources();
        if (res != null) {
            Locale locale = res.getConfiguration().getLocales().get(0);
            if (locale != null) {
                language = locale.getLanguage();
            } else {
                Log.e(TAG, "Current Locale is null!");
            }
        } else {
            Log.e(TAG, "Could not get resources to update locale!");
        }
        if (language != null) {
            mCurrentLocaleAlternateCharset = mMockCharsetsPerLocaleLanguage.get(language);
            if (mCurrentLocaleAlternateCharset == null) {
                mCurrentLocaleAlternateCharset = mCharsetsPerLocaleLanguage.get(language);
            }
        }
        // No Charset for the specific language, use the "all" charset if it exists.
        if (mCurrentLocaleAlternateCharset == null) {
            mCurrentLocaleAlternateCharset =
                    mMockCharsetsPerLocaleLanguage.get(LOCALE_LANGUAGE_ALL);
        }
        if (mCurrentLocaleAlternateCharset == null) {
            mCurrentLocaleAlternateCharset = mCharsetsPerLocaleLanguage.get(LOCALE_LANGUAGE_ALL);
        }
        Log.i(TAG, "Locale language changed to " + language + ", alternate charset "
                + "is now " + mCurrentLocaleAlternateCharset);
    }

    /** Translates an SSID from a source Charset to a target Charset */
    private WifiSsid translateSsid(@NonNull WifiSsid ssid,
            @NonNull Charset sourceCharset,
            @NonNull Charset targetCharset) {
        CharsetDecoder decoder = sourceCharset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharsetEncoder encoder = targetCharset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            ByteBuffer buffer = encoder.encode(decoder.decode(ByteBuffer.wrap(ssid.getBytes())));
            byte[] bytes = new byte[buffer.limit()];
            buffer.get(bytes);
            return WifiSsid.fromBytes(bytes);
        } catch (CharacterCodingException | IllegalArgumentException e) {
            // Could not translate to a valid SSID.
            Log.e(TAG, "Could not translate SSID " + ssid + ": " + e);
            return null;
        }
    }

    /**
     * Translate an SSID to UTF-8 if it is not already UTF-8 and is encoded with the alternate
     * Charset of the current Locale language.
     *
     * @param ssid SSID to translate.
     * @return translated SSID, or the given SSID if it should not be translated.
     */
    public synchronized @NonNull WifiSsid getTranslatedSsid(@NonNull WifiSsid ssid) {
        return getTranslatedSsidAndRecordBssidCharset(ssid, null);
    }

    /**
     * Gets the translated SSID used for a STA iface. This may be different from the default
     * translation if the untranslated SSID has an ambiguous encoding.
     */
    public synchronized @NonNull WifiSsid getTranslatedSsidForStaIface(
            @NonNull WifiSsid untranslated, @NonNull String staIface) {
        WifiSsid translated = mTranslatedSsidForStaIface.get(staIface);
        if (translated == null || !getAllPossibleOriginalSsids(translated).contains(untranslated)) {
            // No recorded translation for the iface, use the default translation.
            return getTranslatedSsid(untranslated);
        }
        // Return the recorded translation.
        return translated;
    }

    /**
     * Record the actual translated SSID used for a STA iface in case the untranslated SSID
     * has an ambiguous encoding.
     */
    public synchronized void setTranslatedSsidForStaIface(
            @NonNull WifiSsid translated, @NonNull String staIface) {
        mTranslatedSsidForStaIface.put(staIface, translated);
    }

    /**
     * Translate an SSID to UTF-8 if it is not already UTF-8 and is encoded with the alternate
     * Charset of the current Locale language, and record the BSSID as translated. If the SSID is
     * already in UTF-8 or is not encoded with the alternate Charset, then the SSID will not be
     * translated and the BSSID will be recorded as untranslated.
     *
     * @param ssid SSID to translate.
     * @param bssid BSSID to record the Charset of.
     * @return translated SSID, or the given SSID if it should not be translated.
     */
    public synchronized @NonNull WifiSsid getTranslatedSsidAndRecordBssidCharset(
            @NonNull WifiSsid ssid, @Nullable MacAddress bssid) {
        if (mCurrentLocaleAlternateCharset == null) {
            return ssid;
        }
        if (ssid.getUtf8Text() == null) {
            WifiSsid translatedSsid =
                    translateSsid(ssid, mCurrentLocaleAlternateCharset, StandardCharsets.UTF_8);
            if (translatedSsid != null) {
                if (bssid != null) {
                    mTranslatedBssids.computeIfAbsent(translatedSsid, k -> new ArraySet<>())
                            .add(bssid);
                    Pair<WifiSsid, MacAddress> ssidBssidPair = new Pair<>(translatedSsid, bssid);
                    Runnable oldRunnable = mTranslatedBssidTimeoutRunnables.remove(ssidBssidPair);
                    if (oldRunnable != null) {
                        mWifiHandler.removeCallbacks(oldRunnable);
                    }
                    Runnable timeoutRunnable = new Runnable() {
                        @Override
                        public void run() {
                            handleTranslatedBssidTimeout(translatedSsid, bssid, this);
                        }
                    };
                    mTranslatedBssidTimeoutRunnables.put(ssidBssidPair, timeoutRunnable);
                    mWifiHandler.postDelayed(timeoutRunnable, BSSID_CACHE_TIMEOUT_MS);
                }
                return translatedSsid;
            }
        }
        if (bssid != null) {
            mUntranslatedBssids.computeIfAbsent(ssid, k -> new ArraySet<>()).add(bssid);
            Pair<WifiSsid, MacAddress> ssidBssidPair = new Pair<>(ssid, bssid);
            Runnable oldRunnable = mUntranslatedBssidTimeoutRunnables.remove(ssidBssidPair);
            if (oldRunnable != null) {
                mWifiHandler.removeCallbacks(oldRunnable);
            }
            Runnable timeoutRunnable = new Runnable() {
                @Override
                public void run() {
                    handleUntranslatedBssidTimeout(ssid, bssid, this);
                }
            };
            mUntranslatedBssidTimeoutRunnables.put(ssidBssidPair, timeoutRunnable);
            mWifiHandler.postDelayed(timeoutRunnable, BSSID_CACHE_TIMEOUT_MS);
        }

        return ssid;
    }

    /** Removes a timed out translated ssid/bssid mapping */
    private synchronized void handleTranslatedBssidTimeout(
            WifiSsid ssid, MacAddress bssid, Runnable runnable) {
        Pair<WifiSsid, MacAddress> mapping = new Pair<>(ssid, bssid);
        if (mTranslatedBssidTimeoutRunnables.get(mapping) != runnable) {
            // This runnable isn't the active runnable anymore. Ignore.
            return;
        }
        mTranslatedBssidTimeoutRunnables.remove(mapping);
        Set<MacAddress> bssids = mTranslatedBssids.get(ssid);
        if (bssids == null) {
            return;
        }
        bssids.remove(bssid);
        if (bssids.isEmpty()) {
            mTranslatedBssids.remove(ssid);
        }
    }

    /** Removes a timed out untranslated ssid/bssid mapping */
    private synchronized void handleUntranslatedBssidTimeout(
            WifiSsid ssid, MacAddress bssid, Runnable runnable) {
        Pair<WifiSsid, MacAddress> mapping = new Pair<>(ssid, bssid);
        if (mUntranslatedBssidTimeoutRunnables.get(mapping) != runnable) {
            // This runnable isn't the active runnable anymore. Ignore.
            return;
        }
        mUntranslatedBssidTimeoutRunnables.remove(mapping);
        Set<MacAddress> bssids = mUntranslatedBssids.get(ssid);
        if (bssids == null) {
            return;
        }
        bssids.remove(bssid);
        if (bssids.isEmpty()) {
            mUntranslatedBssids.remove(ssid);
        }
    }

    /**
     * Converts the specified translated SSID back to its original Charset if the BSSID is recorded
     * as translated, or there are translated BSSIDs but no untranslated BSSIDs for this SSID.
     *
     * If the BSSID has not been recorded at all, then we will return the SSID as-is.
     *
     * @param translatedSsid translated SSID.
     * @param bssid optional BSSID to look up the Charset.
     * @return original SSID. May be null if there are no valid translations back to the alternate
     *         Charset and the translated SSID is not a valid SSID.
     */
    public synchronized @Nullable WifiSsid getOriginalSsid(
            @NonNull WifiSsid translatedSsid, @Nullable MacAddress bssid) {
        if (mCurrentLocaleAlternateCharset == null) {
            return translatedSsid.getBytes().length <= 32 ? translatedSsid : null;
        }
        boolean ssidWasTranslatedForSomeBssids = mTranslatedBssids.containsKey(translatedSsid);
        boolean ssidWasTranslatedForThisBssid = ssidWasTranslatedForSomeBssids
                && mTranslatedBssids.get(translatedSsid).contains(bssid);
        boolean ssidNotTranslatedForSomeBssids = mUntranslatedBssids.containsKey(translatedSsid);
        if (ssidWasTranslatedForThisBssid
                || (ssidWasTranslatedForSomeBssids && !ssidNotTranslatedForSomeBssids)) {
            // Try to get the SSID in the alternate Charset.
            WifiSsid altCharsetSsid = translateSsid(
                    translatedSsid, StandardCharsets.UTF_8, mCurrentLocaleAlternateCharset);
            if (altCharsetSsid == null || altCharsetSsid.getBytes().length > 32) {
                Log.e(TAG, "Could not translate " + translatedSsid + " back to "
                        + mCurrentLocaleAlternateCharset + " for BSSID " + bssid);
            } else {
                return altCharsetSsid;
            }
        }
        // Use the translated SSID as-is
        if (translatedSsid.getBytes().length > 32) {
            return null;
        }
        return translatedSsid;
    }

    /**
     * Gets the original SSID of a WifiConfiguration based on its network selection BSSID or
     * candidate BSSID.
     */
    public synchronized @Nullable WifiSsid getOriginalSsid(@NonNull WifiConfiguration config) {
        WifiConfiguration.NetworkSelectionStatus networkSelectionStatus =
                config.getNetworkSelectionStatus();
        String networkSelectionBssid = networkSelectionStatus.getNetworkSelectionBSSID();
        String candidateBssid = networkSelectionStatus.getCandidate() != null
                ? networkSelectionStatus.getCandidate().BSSID : null;
        MacAddress selectedBssid = null;
        if (!TextUtils.isEmpty(networkSelectionBssid) && !TextUtils.equals(
                networkSelectionBssid, ClientModeImpl.SUPPLICANT_BSSID_ANY)) {
            selectedBssid = MacAddress.fromString(networkSelectionBssid);
        } else if (!TextUtils.isEmpty(candidateBssid) && !TextUtils.equals(
                candidateBssid, ClientModeImpl.SUPPLICANT_BSSID_ANY)) {
            selectedBssid = MacAddress.fromString(candidateBssid);
        }
        return getOriginalSsid(WifiSsid.fromString(config.SSID), selectedBssid);
    }

    /**
     * Returns a list of all possible original SSIDs for the specified translated SSID. This will
     * include all charsets declared for the current Locale language, as well as the UTF-8 SSID.
     *
     * @param translatedSsid translated SSID.
     * @return list of untranslated SSIDs. May be empty if there are no valid reverse translations.
     */
    public synchronized @NonNull List<WifiSsid> getAllPossibleOriginalSsids(
            @NonNull WifiSsid translatedSsid) {
        List<WifiSsid> untranslatedSsids = new ArrayList<>();
        // Add the translated SSID first (UTF-8 or unknown character set)
        if (translatedSsid.getBytes().length <= 32) {
            untranslatedSsids.add(translatedSsid);
        }
        if (mCurrentLocaleAlternateCharset != null) {
            WifiSsid altCharsetSsid = translateSsid(translatedSsid,
                    StandardCharsets.UTF_8, mCurrentLocaleAlternateCharset);
            if (altCharsetSsid != null && !altCharsetSsid.equals(translatedSsid)
                    && altCharsetSsid.getBytes().length <= 32) {
                untranslatedSsids.add(altCharsetSsid);
            }
        }
        return untranslatedSsids;
    }

    /**
     * Dump of {@link SsidTranslator}.
     */
    public synchronized void dump(PrintWriter pw) {
        pw.println("Dump of SsidTranslator");
        pw.println("mCurrentLocaleCharset: " + mCurrentLocaleAlternateCharset);
        pw.println("mCharsetsPerLocaleLanguage Begin ---");
        for (Map.Entry<String, Charset> entry : mCharsetsPerLocaleLanguage.entrySet()) {
            pw.println(entry.getKey() + ": " + entry.getValue());
        }
        pw.println("mCharsetsPerLocaleLanguage End ---");
        pw.println("mTranslatedBssids Begin ---");
        for (Map.Entry<WifiSsid, Set<MacAddress>> translatedBssidsEntry
                : mTranslatedBssids.entrySet()) {
            pw.println("Translated SSID: " + translatedBssidsEntry.getKey() + ", BSSIDS: "
                    + Arrays.toString(translatedBssidsEntry.getValue().toArray()));
        }
        pw.println("mTranslatedBssids End ---");
        pw.println("mUntranslatedBssids Begin ---");
        for (Map.Entry<WifiSsid, Set<MacAddress>> untranslatedBssidsEntry
                : mUntranslatedBssids.entrySet()) {
            pw.println("Translated SSID: " + untranslatedBssidsEntry.getKey() + ", BSSIDS: "
                    + Arrays.toString(untranslatedBssidsEntry.getValue().toArray()));
        }
        pw.println("mUntranslatedBssids End ---");
    }

    /**
     * Sets a mock Charset for the specified Locale language.
     * Use {@link #clearMockLocaleCharsets()} to clear the mock list.
     */
    public synchronized void setMockLocaleCharset(
            @NonNull String localeLanguage, @NonNull Charset charset) {
        Log.i(TAG, "Setting mock alternate charset for " + localeLanguage + ": " + charset);
        mMockCharsetsPerLocaleLanguage.put(localeLanguage, charset);
        updateCurrentLocaleCharset();
    }

    /**
     * Clears all mocked Charsets set by {@link #setMockLocaleCharset(String, Charset)}.
     */
    public synchronized void clearMockLocaleCharsets() {
        Log.i(TAG, "Clearing mock charsets");
        mMockCharsetsPerLocaleLanguage.clear();
        updateCurrentLocaleCharset();
    }

    /**
     * Indicates whether SSID translation is currently enabled.
     */
    public synchronized boolean isSsidTranslationEnabled() {
        return mCurrentLocaleAlternateCharset != null;
    }
}
