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

package com.android.server.wifi.util;

import static android.net.wifi.SoftApCapability.SOFTAP_FEATURE_ACS_OFFLOAD;
import static android.net.wifi.SoftApCapability.SOFTAP_FEATURE_BAND_24G_SUPPORTED;
import static android.net.wifi.SoftApCapability.SOFTAP_FEATURE_BAND_5G_SUPPORTED;
import static android.net.wifi.SoftApCapability.SOFTAP_FEATURE_BAND_60G_SUPPORTED;
import static android.net.wifi.SoftApCapability.SOFTAP_FEATURE_BAND_6G_SUPPORTED;
import static android.net.wifi.SoftApCapability.SOFTAP_FEATURE_CLIENT_FORCE_DISCONNECT;
import static android.net.wifi.SoftApCapability.SOFTAP_FEATURE_IEEE80211_AX;
import static android.net.wifi.SoftApCapability.SOFTAP_FEATURE_IEEE80211_BE;
import static android.net.wifi.SoftApCapability.SOFTAP_FEATURE_MAC_ADDRESS_CUSTOMIZATION;
import static android.net.wifi.SoftApCapability.SOFTAP_FEATURE_WPA3_OWE;
import static android.net.wifi.SoftApCapability.SOFTAP_FEATURE_WPA3_OWE_TRANSITION;
import static android.net.wifi.SoftApCapability.SOFTAP_FEATURE_WPA3_SAE;

import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_AP_BRIDGE;
import static com.android.server.wifi.HalDeviceManager.HDM_CREATE_IFACE_STA;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Resources;
import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.ScanResult;
import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApConfiguration.BandType;
import android.net.wifi.SoftApInfo;
import android.net.wifi.WifiAvailableChannel;
import android.net.wifi.WifiClient;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.SoftApManager;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WifiSettingsConfigStore;
import com.android.server.wifi.coex.CoexManager;
import com.android.wifi.resources.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Provide utility functions for updating soft AP related configuration.
 */
public class ApConfigUtil {
    private static final String TAG = "ApConfigUtil";

    public static final int INVALID_VALUE_FOR_BAND_OR_CHANNEL = -1;
    public static final int DEFAULT_AP_BAND = SoftApConfiguration.BAND_2GHZ;
    public static final int DEFAULT_AP_CHANNEL = 6;
    public static final int HIGHEST_2G_AP_CHANNEL = 14;

    /* Random number generator used for AP channel selection. */
    private static final Random sRandom = new Random();
    private static boolean sVerboseLoggingEnabled = false;

    /**
     * Enable or disable verbose logging
     * @param verboseEnabled true if verbose logging is enabled
     */
    public static void enableVerboseLogging(boolean verboseEnabled) {
        sVerboseLoggingEnabled = verboseEnabled;
    }

    /**
     * Valid Global Operating classes in each wifi band
     * Reference: Table E-4 in IEEE Std 802.11-2016.
     */
    private static final SparseArray<int[]> sBandToOperatingClass = new SparseArray<>();
    static {
        sBandToOperatingClass.append(SoftApConfiguration.BAND_2GHZ, new int[]{81, 82, 83, 84});
        sBandToOperatingClass.append(SoftApConfiguration.BAND_5GHZ, new int[]{115, 116, 117, 118,
                119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130});
        sBandToOperatingClass.append(SoftApConfiguration.BAND_6GHZ, new int[]{131, 132, 133, 134,
                135, 136});
    }

    /**
     * Converts a SoftApConfiguration.BAND_* constant to a meaningful String
     */
    public static String bandToString(int band) {
        StringJoiner sj = new StringJoiner(" & ");
        sj.setEmptyValue("unspecified");
        if ((band & SoftApConfiguration.BAND_2GHZ) != 0) {
            sj.add("2Ghz");
        }
        band &= ~SoftApConfiguration.BAND_2GHZ;

        if ((band & SoftApConfiguration.BAND_5GHZ) != 0) {
            sj.add("5Ghz");
        }
        band &= ~SoftApConfiguration.BAND_5GHZ;

        if ((band & SoftApConfiguration.BAND_6GHZ) != 0) {
            sj.add("6Ghz");
        }
        band &= ~SoftApConfiguration.BAND_6GHZ;

        if ((band & SoftApConfiguration.BAND_60GHZ) != 0) {
            sj.add("60Ghz");
        }
        band &= ~SoftApConfiguration.BAND_60GHZ;
        if (band != 0) {
            return "Invalid band";
        }
        return sj.toString();
    }

    /**
     * Helper function to get the band corresponding to the operating class.
     *
     * @param operatingClass Global operating class.
     * @return band, -1 if no match.
     *
     */
    public static int getBandFromOperatingClass(int operatingClass) {
        for (int i = 0; i < sBandToOperatingClass.size(); i++) {
            int band = sBandToOperatingClass.keyAt(i);
            int[] operatingClasses = sBandToOperatingClass.get(band);

            for (int j = 0; j < operatingClasses.length; j++) {
                if (operatingClasses[j] == operatingClass) {
                    return band;
                }
            }
        }
        return -1;
    }

    /**
     * Convert band from SoftApConfiguration.BandType to WifiScanner.WifiBand
     * @param band in SoftApConfiguration.BandType
     * @return band in WifiScanner.WifiBand
     */
    public static @WifiScanner.WifiBand int apConfig2wifiScannerBand(@BandType int band) {
        switch(band) {
            case SoftApConfiguration.BAND_2GHZ:
                return WifiScanner.WIFI_BAND_24_GHZ;
            case SoftApConfiguration.BAND_5GHZ:
                return WifiScanner.WIFI_BAND_5_GHZ;
            case SoftApConfiguration.BAND_6GHZ:
                return WifiScanner.WIFI_BAND_6_GHZ;
            case SoftApConfiguration.BAND_60GHZ:
                return WifiScanner.WIFI_BAND_60_GHZ;
            default:
                return WifiScanner.WIFI_BAND_UNSPECIFIED;
        }
    }

    /**
     * Convert channel/band to frequency.
     * Note: the utility does not perform any regulatory domain compliance.
     * @param channel number to convert
     * @param band of channel to convert
     * @return center frequency in Mhz of the channel, -1 if no match
     */
    public static int convertChannelToFrequency(int channel, @BandType int band) {
        return ScanResult.convertChannelToFrequencyMhzIfSupported(channel,
                apConfig2wifiScannerBand(band));
    }

    /**
     * Convert frequency to band.
     * Note: the utility does not perform any regulatory domain compliance.
     * @param frequency frequency to convert
     * @return band, -1 if no match
     */
    public static int convertFrequencyToBand(int frequency) {
        if (ScanResult.is24GHz(frequency)) {
            return SoftApConfiguration.BAND_2GHZ;
        } else if (ScanResult.is5GHz(frequency)) {
            return SoftApConfiguration.BAND_5GHZ;
        } else if (ScanResult.is6GHz(frequency)) {
            return SoftApConfiguration.BAND_6GHZ;
        } else if (ScanResult.is60GHz(frequency)) {
            return SoftApConfiguration.BAND_60GHZ;
        }

        return -1;
    }

    /**
     * Convert band from WifiConfiguration into SoftApConfiguration
     *
     * @param wifiConfigBand band encoded as WifiConfiguration.AP_BAND_xxxx
     * @return band as encoded as SoftApConfiguration.BAND_xxx
     */
    public static int convertWifiConfigBandToSoftApConfigBand(int wifiConfigBand) {
        switch (wifiConfigBand) {
            case WifiConfiguration.AP_BAND_2GHZ:
                return SoftApConfiguration.BAND_2GHZ;
            case WifiConfiguration.AP_BAND_5GHZ:
                return SoftApConfiguration.BAND_5GHZ;
            case WifiConfiguration.AP_BAND_ANY:
                return SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ;
            default:
                return SoftApConfiguration.BAND_2GHZ;
        }
    }

    /**
     * Add 2.4Ghz to target band when 2.4Ghz SoftAp supported.
     *
     * @param targetBand The band is needed to add 2.4G.
     * @return The band includes 2.4Ghz when 2.4G SoftAp supported.
     */
    public static @BandType int append24GToBandIf24GSupported(@BandType int targetBand,
            Context context) {
        if (isBandSupported(SoftApConfiguration.BAND_2GHZ, context)) {
            return targetBand | SoftApConfiguration.BAND_2GHZ;
        }
        return targetBand;
    }

    /**
     * Add 5Ghz to target band when 5Ghz SoftAp supported.
     *
     * @param targetBand The band is needed to add 5GHz band.
     * @return The band includes 5Ghz when 5G SoftAp supported.
     */
    public static @BandType int append5GToBandIf5GSupported(@BandType int targetBand,
            Context context) {
        if (isBandSupported(SoftApConfiguration.BAND_5GHZ, context)) {
            return targetBand | SoftApConfiguration.BAND_5GHZ;
        }
        return targetBand;
    }

    /**
     * Checks if band is a valid combination of {link  SoftApConfiguration#BandType} values
     */
    public static boolean isBandValid(@BandType int band) {
        int bandAny = SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ
                | SoftApConfiguration.BAND_6GHZ | SoftApConfiguration.BAND_60GHZ;
        return ((band != 0) && ((band & ~bandAny) == 0));
    }

    /**
     * Check if the band contains a certain sub-band
     *
     * @param band The combination of bands to validate
     * @param testBand the test band to validate on
     * @return true if band contains testBand, false otherwise
     */
    public static boolean containsBand(@BandType int band, @BandType int testBand) {
        return ((band & testBand) != 0);
    }

    /**
     * Checks if band contains multiple sub-bands
     * @param band a combination of sub-bands
     * @return true if band has multiple sub-bands, false otherwise
     */
    public static boolean isMultiband(@BandType int band) {
        return ((band & (band - 1)) != 0);
    }


    /**
     * Checks whether or not band configuration is supported.
     * @param apBand a combination of the bands
     * @param context the caller context used to get value from resource file.
     * @return true if band is supported, false otherwise
     */
    public static boolean isBandSupported(@BandType int apBand, Context context) {
        if (!isBandValid(apBand)) {
            Log.e(TAG, "Invalid SoftAp band " + apBand);
            return false;
        }

        for (int b : SoftApConfiguration.BAND_TYPES) {
            if (containsBand(apBand, b) && !isSoftApBandSupported(context, b)) {
                Log.e(TAG, "Can not start softAp with band " + bandToString(b)
                        + " not supported.");
                return false;
            }
        }

        return true;
    }

    /**
     * Convert string to channel list
     * Format of the list is a comma separated channel numbers, or range of channel numbers
     * Example, "34-48, 149".
     * @param channelString for a comma separated channel numbers, or range of channel numbers
     *        such as "34-48, 149"
     * @return list of channel numbers
     */
    public static List<Integer> convertStringToChannelList(String channelString) {
        if (channelString == null) {
            return null;
        }

        List<Integer> channelList = new ArrayList<Integer>();

        for (String channelRange : channelString.split(",")) {
            try {
                if (channelRange.contains("-")) {
                    String[] channels = channelRange.split("-");
                    if (channels.length != 2) {
                        Log.e(TAG, "Unrecognized channel range, Length is " + channels.length);
                        continue;
                    }
                    int start = Integer.parseInt(channels[0].trim());
                    int end = Integer.parseInt(channels[1].trim());
                    if (start > end) {
                        Log.e(TAG, "Invalid channel range, from " + start + " to " + end);
                        continue;
                    }

                    for (int channel = start; channel <= end; channel++) {
                        channelList.add(channel);
                    }
                } else {
                    channelList.add(Integer.parseInt(channelRange.trim()));
                }
            } catch (NumberFormatException e) {
                // Ignore malformed string
                Log.e(TAG, "Malformed channel value detected: " + e);
                continue;
            }
        }
        return channelList;
    }

    /**
     * Returns the unsafe channels frequency from coex module.
     *
     * @param coexManager reference used to get unsafe channels to avoid for coex.
     */
    @NonNull
    public static Set<Integer> getUnsafeChannelFreqsFromCoex(@NonNull CoexManager coexManager) {
        Set<Integer> unsafeFreqs = new HashSet<>();
        if (SdkLevel.isAtLeastS()) {
            for (CoexUnsafeChannel unsafeChannel : coexManager.getCoexUnsafeChannels()) {
                unsafeFreqs.add(ScanResult.convertChannelToFrequencyMhzIfSupported(
                        unsafeChannel.getChannel(), unsafeChannel.getBand()));
            }
        }
        return unsafeFreqs;
    }

    private static List<Integer> getConfiguredChannelList(Resources resources, @BandType int band) {
        switch (band) {
            case SoftApConfiguration.BAND_2GHZ:
                return convertStringToChannelList(resources.getString(
                        R.string.config_wifiSoftap2gChannelList));
            case SoftApConfiguration.BAND_5GHZ:
                return convertStringToChannelList(resources.getString(
                        R.string.config_wifiSoftap5gChannelList));
            case SoftApConfiguration.BAND_6GHZ:
                return convertStringToChannelList(resources.getString(
                        R.string.config_wifiSoftap6gChannelList));
            case SoftApConfiguration.BAND_60GHZ:
                return convertStringToChannelList(resources.getString(
                        R.string.config_wifiSoftap60gChannelList));
            default:
                return null;
        }
    }

    private static List<Integer> addDfsChannelsIfNeeded(List<Integer> regulatoryList,
            @WifiScanner.WifiBand int scannerBand, WifiNative wifiNative, Resources resources,
            boolean inFrequencyMHz) {
        // Add DFS channels to the supported channel list if the device supports SoftAp
        // operation in the DFS channel.
        if (resources.getBoolean(R.bool.config_wifiSoftapAcsIncludeDfs)
                && scannerBand == WifiScanner.WIFI_BAND_5_GHZ) {
            int[] dfs5gBand = wifiNative.getChannelsForBand(
                    WifiScanner.WIFI_BAND_5_GHZ_DFS_ONLY);
            for (int freq : dfs5gBand) {
                final int freqOrChan = inFrequencyMHz
                        ? freq : ScanResult.convertFrequencyMhzToChannelIfSupported(freq);
                if (!regulatoryList.contains(freqOrChan)) {
                    regulatoryList.add(freqOrChan);
                }
            }
        }
        return regulatoryList;
    }

    private static List<Integer> getWifiCondAvailableChannelsForBand(
            @WifiScanner.WifiBand int scannerBand, WifiNative wifiNative, Resources resources,
            boolean inFrequencyMHz) {
        List<Integer> regulatoryList = new ArrayList<Integer>();
        // Get the allowed list of channel frequencies in MHz from wificond
        int[] regulatoryArray = wifiNative.getChannelsForBand(scannerBand);
        for (int freq : regulatoryArray) {
            regulatoryList.add(inFrequencyMHz
                    ? freq : ScanResult.convertFrequencyMhzToChannelIfSupported(freq));
        }
        return addDfsChannelsIfNeeded(regulatoryList, scannerBand, wifiNative, resources,
                inFrequencyMHz);
    }

    private static List<Integer> getHalAvailableChannelsForBand(
            @WifiScanner.WifiBand int scannerBand, WifiNative wifiNative, Resources resources,
            boolean inFrequencyMHz) {
        // Try vendor HAL API to get the usable channel list.
        List<WifiAvailableChannel> usableChannelList = wifiNative.getUsableChannels(
                scannerBand,
                WifiAvailableChannel.OP_MODE_SAP,
                WifiAvailableChannel.FILTER_REGULATORY);
        if (usableChannelList == null) {
            // If HAL doesn't support getUsableChannels then return null
            return null;
        }
        List<Integer> regulatoryList = new ArrayList<>();
        if (inFrequencyMHz) {
            usableChannelList.forEach(a -> regulatoryList.add(a.getFrequencyMhz()));
        } else {
            usableChannelList.forEach(a -> regulatoryList.add(ScanResult
                    .convertFrequencyMhzToChannelIfSupported(a.getFrequencyMhz())));

        }
        return addDfsChannelsIfNeeded(regulatoryList, scannerBand, wifiNative, resources,
                inFrequencyMHz);
    }

    /**
     * Get channels or frequencies for band that are allowed by both regulatory
     * and OEM configuration.
     *
     * @param band to get channels for
     * @param wifiNative reference used to get regulatory restrictions.
     * @param resources used to get OEM restrictions
     * @param inFrequencyMHz true to convert channel to frequency.
     * @return A list of frequencies that are allowed, null on error.
     */
    public static List<Integer> getAvailableChannelFreqsForBand(
            @BandType int band, WifiNative wifiNative, Resources resources,
            boolean inFrequencyMHz) {
        if (!isBandValid(band) || isMultiband(band)) {
            return null;
        }

        int scannerBand = apConfig2wifiScannerBand(band);
        List<Integer> regulatoryList = null;
        boolean useWifiCond = false;
        // Check if vendor HAL API for getting usable channels is available. If HAL doesn't support
        // the API it returns null list, in that case we retrieve the list from wificond.
        if (!wifiNative.isHalSupported()) {
            // HAL is not supported, fallback to wificond
            useWifiCond = true;
        } else {
            if (!wifiNative.isHalStarted()) {
                // HAL is not started, return null
                return null;
            }
            regulatoryList = getHalAvailableChannelsForBand(scannerBand, wifiNative, resources,
                    inFrequencyMHz);
            if (regulatoryList == null) {
                // HAL API not supported by HAL, fallback to wificond
                useWifiCond = true;
            }
        }
        if (useWifiCond) {
            regulatoryList = getWifiCondAvailableChannelsForBand(scannerBand, wifiNative, resources,
                    inFrequencyMHz);
        }
        List<Integer> configuredList = getConfiguredChannelList(resources, band);
        if (configuredList == null || configuredList.isEmpty() || regulatoryList == null) {
            return regulatoryList;
        }
        List<Integer> filteredList = new ArrayList<Integer>();
        // Otherwise, filter the configured list
        for (int channel : configuredList) {
            if (inFrequencyMHz) {
                int channelFreq = convertChannelToFrequency(channel, band);
                if (regulatoryList.contains(channelFreq)) {
                    filteredList.add(channelFreq);
                }
            } else if (regulatoryList.contains(channel)) {
                filteredList.add(channel);
            }
        }
        if (sVerboseLoggingEnabled) {
            Log.d(TAG, "Filtered channel list for band " + bandToString(band) + " : "
                    + filteredList.stream().map(Object::toString).collect(Collectors.joining(",")));
        }
        return filteredList;
    }

    /**
     * Return a channel frequency for AP setup based on the frequency band.
     * @param apBand one or combination of the values of SoftApConfiguration.BAND_*.
     * @param coexManager reference used to get unsafe channels to avoid for coex.
     * @param resources the resources to use to get configured allowed channels.
     * @param capability soft AP capability
     * @return a valid channel frequency on success, -1 on failure.
     */
    public static int chooseApChannel(int apBand, @NonNull CoexManager coexManager,
            @NonNull Resources resources, SoftApCapability capability) {
        if (!isBandValid(apBand)) {
            Log.e(TAG, "Invalid band: " + apBand);
            return -1;
        }

        Set<Integer> unsafeFreqs = new HashSet<>();
        if (SdkLevel.isAtLeastS()) {
            unsafeFreqs = getUnsafeChannelFreqsFromCoex(coexManager);
        }
        final int[] bandPreferences = new int[]{
                SoftApConfiguration.BAND_60GHZ,
                SoftApConfiguration.BAND_6GHZ,
                SoftApConfiguration.BAND_5GHZ,
                SoftApConfiguration.BAND_2GHZ};
        int selectedUnsafeFreq = 0;
        for (int band : bandPreferences) {
            if ((apBand & band) == 0) {
                continue;
            }
            int[] availableChannels = capability.getSupportedChannelList(band);
            if (availableChannels == null || availableChannels.length == 0) {
                continue;
            }
            final List<Integer> availableFreqs =
                    Arrays.stream(availableChannels).boxed()
                            .map(ch -> convertChannelToFrequency(ch, band))
                            .collect(Collectors.toList());
            // Separate the available freqs by safe and unsafe.
            List<Integer> availableSafeFreqs = new ArrayList<>();
            List<Integer> availableUnsafeFreqs = new ArrayList<>();
            for (int freq : availableFreqs) {
                if (unsafeFreqs.contains(freq)) {
                    availableUnsafeFreqs.add(freq);
                } else {
                    availableSafeFreqs.add(freq);
                }
            }
            // If there are safe freqs available for this band, randomly select one.
            if (!availableSafeFreqs.isEmpty()) {
                return availableSafeFreqs.get(sRandom.nextInt(availableSafeFreqs.size()));
            } else if (!availableUnsafeFreqs.isEmpty() && selectedUnsafeFreq == 0) {
                // Save an unsafe freq from the first preferred band to fall back on later.
                selectedUnsafeFreq = availableUnsafeFreqs.get(
                        sRandom.nextInt(availableUnsafeFreqs.size()));
            }
        }
        // If all available channels are soft unsafe, select a random one of the highest band.
        boolean isHardUnsafe = false;
        if (SdkLevel.isAtLeastS()) {
            isHardUnsafe =
                    (coexManager.getCoexRestrictions() & WifiManager.COEX_RESTRICTION_SOFTAP) != 0;
        }
        if (!isHardUnsafe && selectedUnsafeFreq != 0) {
            return selectedUnsafeFreq;
        }

        // If all available channels are hard unsafe, select the default AP channel.
        if (containsBand(apBand, DEFAULT_AP_BAND)) {
            final int defaultChannelFreq = convertChannelToFrequency(DEFAULT_AP_CHANNEL,
                    DEFAULT_AP_BAND);
            Log.e(TAG, "Allowed channel list not specified, selecting default channel");
            if (isHardUnsafe && unsafeFreqs.contains(defaultChannelFreq)) {
                Log.e(TAG, "Default channel is hard restricted due to coex");
            }
            return defaultChannelFreq;
        }
        Log.e(TAG, "No available channels");
        return -1;
    }

    /**
     * Remove unavailable bands from the input band and return the resulting
     * (remaining) available bands. Unavailable bands are those which don't have channels available.
     *
     * @param capability SoftApCapability which indicates supported channel list.
     * @param targetBand The target band which plan to enable
     * @param coexManager reference to CoexManager
     *
     * @return the available band which removed the unsupported band.
     *         0 when all of the band is not supported.
     */
    public static @BandType int removeUnavailableBands(SoftApCapability capability,
            @NonNull int targetBand, CoexManager coexManager) {
        int availableBand = targetBand;
        for (int band : SoftApConfiguration.BAND_TYPES) {
            Set<Integer> availableChannelFreqsList = new HashSet<>();
            if ((targetBand & band) != 0) {
                for (int channel : capability.getSupportedChannelList(band)) {
                    availableChannelFreqsList.add(convertChannelToFrequency(channel, band));
                }
                // Only remove hard unsafe channels
                if (SdkLevel.isAtLeastS()
                        && (coexManager.getCoexRestrictions() & WifiManager.COEX_RESTRICTION_SOFTAP)
                        != 0) {
                    availableChannelFreqsList.removeAll(getUnsafeChannelFreqsFromCoex(coexManager));
                }
                if (availableChannelFreqsList.size() == 0) {
                    availableBand &= ~band;
                }
            }
        }
        return availableBand;
    }

    /**
     * Remove unavailable bands from the softAp configuration and return the updated configuration.
     * Unavailable bands are those which don't have channels available.
     *
     * @param config The current {@link SoftApConfiguration}.
     * @param capability SoftApCapability which indicates supported channel list.
     * @param coexManager reference to CoexManager
     * @param context the caller context used to get value from resource file.
     *
     * @return the updated SoftApConfiguration.
     */
    public static SoftApConfiguration removeUnavailableBandsFromConfig(
            SoftApConfiguration config, SoftApCapability capability, CoexManager coexManager,
            @NonNull Context context) {
        SoftApConfiguration.Builder builder = new SoftApConfiguration.Builder(config);

        try {
            if (config.getBands().length == 1) {
                int configuredBand = config.getBand();
                int availableBand = ApConfigUtil.removeUnavailableBands(
                        capability,
                        configuredBand, coexManager);
                if (availableBand != configuredBand) {
                    availableBand = ApConfigUtil.append24GToBandIf24GSupported(availableBand,
                            context);
                    Log.i(TAG, "Reset band from " + configuredBand + " to "
                            + availableBand + " in single AP configuration");
                    builder.setBand(availableBand);
                }
            } else if (SdkLevel.isAtLeastS()) {
                SparseIntArray channels = config.getChannels();
                SparseIntArray newChannels = new SparseIntArray(channels.size());
                for (int i = 0; i < channels.size(); i++) {
                    int configuredBand = channels.keyAt(i);
                    int availableBand = ApConfigUtil.removeUnavailableBands(
                            capability,
                            configuredBand, coexManager);
                    if (availableBand != configuredBand) {
                        Log.i(TAG, "Reset band in index " + i + " from " + configuredBand
                                + " to " + availableBand + " in dual AP configuration");
                    }
                    if (isBandValid(availableBand)) {
                        newChannels.put(availableBand, channels.valueAt(i));
                    }
                }
                if (newChannels.size() != 0) {
                    builder.setChannels(newChannels);
                } else {
                    builder.setBand(
                            ApConfigUtil.append24GToBandIf24GSupported(0, context));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to update config by removing unavailable bands"
                    + e);
            return null;
        }

        return builder.build();
    }

    /**
     * Remove all unsupported bands from the input band and return the resulting
     * (remaining) support bands. Unsupported bands are those which don't have channels available.
     *
     * @param context The caller context used to get value from resource file.
     * @param band The target band which plan to enable
     *
     * @return the available band which removed the unsupported band.
     *         0 when all of the band is not supported.
     */
    public static @BandType int removeUnsupportedBands(Context context,
            @NonNull int band) {
        int availableBand = band;
        for (int b : SoftApConfiguration.BAND_TYPES) {
            if (((band & b) != 0) && !isSoftApBandSupported(context, b)) {
                availableBand &= ~b;
            }
        }
        return availableBand;
    }

    /**
     * Check if security type is restricted for operation in 6GHz band
     * As per WFA specification for 6GHz operation, the following security types are not allowed to
     * be used in 6GHz band:
     *   - OPEN
     *   - WPA2-Personal
     *   - WPA3-SAE-Transition
     *   - WPA3-OWE-Transition
     *
     * @param type security type to check on
     *
     * @return true if security type is restricted for operation in 6GHz band, false otherwise
     */
    public static boolean isSecurityTypeRestrictedFor6gBand(
            @SoftApConfiguration.SecurityType int type) {
        switch(type) {
            case SoftApConfiguration.SECURITY_TYPE_OPEN:
            case SoftApConfiguration.SECURITY_TYPE_WPA2_PSK:
            case SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION:
            case SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION:
                return true;
        }
        return false;
    }

    /**
     * Checks whether HAL support converting the restricted security type to an allowed one in 6GHz
     * band configuration.
     * @param resources the resources to get the OEM configuration for HAL support.
     * @param type security type.
     * @return true if HAL support to map WPA3 transition mode to WPA3 in 6GHz band,
     * false otherwise.
     */
    public static boolean canHALConvertRestrictedSecurityTypeFor6GHz(@NonNull Resources resources,
            @SoftApConfiguration.SecurityType int type) {
        return type == SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION
                && resources.getBoolean(R.bool
                        .config_wifiSofapHalMapWpa3TransitionModeToWpa3OnlyIn6GHzBand);
    }

    /**
     * Remove {@link SoftApConfiguration#BAND_6GHZ} if multiple bands are configured
     * as a mask when security type is restricted to operate in this band.
     *
     * @param resources the resources to get the OEM configuration for HAL support.
     * @param config The current {@link SoftApConfiguration}.
     * @param isBridgedMode true if bridged mode is enabled, false otherwise.
     *
     * @return the updated SoftApConfiguration.
     */
    public static SoftApConfiguration remove6gBandForUnsupportedSecurity(
            @NonNull Resources resources,
            SoftApConfiguration config, boolean isBridgedMode) {
        SoftApConfiguration.Builder builder = new SoftApConfiguration.Builder(config);

        try {
            int securityType = config.getSecurityType();
            if (config.getBands().length == 1) {
                int configuredBand = config.getBand();
                if ((configuredBand & SoftApConfiguration.BAND_6GHZ) != 0
                        && isSecurityTypeRestrictedFor6gBand(config.getSecurityType())) {
                    Log.i(TAG, "remove BAND_6G if multiple bands are configured "
                            + "as a mask when security type is restricted");
                    builder.setBand(configuredBand & ~SoftApConfiguration.BAND_6GHZ);
                }
            } else if (SdkLevel.isAtLeastS()) {
                SparseIntArray channels = config.getChannels();
                SparseIntArray newChannels = new SparseIntArray(channels.size());
                if (isSecurityTypeRestrictedFor6gBand(securityType)) {
                    for (int i = 0; i < channels.size(); i++) {
                        int band = channels.keyAt(i);
                        if ((band & SoftApConfiguration.BAND_6GHZ) != 0
                                && canHALConvertRestrictedSecurityTypeFor6GHz(resources,
                                securityType) && isBridgedMode) {
                            Log.i(TAG, "Do not remove BAND_6G in bridged mode for"
                                    + " security type: " + securityType
                                    + " as HAL can convert the security type");
                        } else {
                            Log.i(TAG, "remove BAND_6G if multiple bands are configured "
                                    + "as a mask when security type is restricted");
                            band &= ~SoftApConfiguration.BAND_6GHZ;
                        }
                        newChannels.put(band, channels.valueAt(i));
                    }
                    builder.setChannels(newChannels);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to update config by removing 6G band for unsupported security type:"
                    + e);
            return null;
        }

        return builder.build();
    }

    /**
     * Update AP band and channel based on the provided country code and band.
     * This will also set
     * @param wifiNative reference to WifiNative
     * @param coexManager reference to CoexManager
     * @param resources the resources to use to get configured allowed channels.
     * @param countryCode country code
     * @param config configuration to update
     * @param capability soft ap capability
     * @return the corresponding {@link SoftApManager.StartResult} result code.
     */
    public static @SoftApManager.StartResult int updateApChannelConfig(WifiNative wifiNative,
            @NonNull CoexManager coexManager,
            Resources resources,
            String countryCode,
            SoftApConfiguration.Builder configBuilder,
            SoftApConfiguration config,
            SoftApCapability capability) {
        /* Use default band and channel for device without HAL. */
        if (!wifiNative.isHalStarted()) {
            configBuilder.setChannel(DEFAULT_AP_CHANNEL, DEFAULT_AP_BAND);
            return SoftApManager.START_RESULT_SUCCESS;
        }

        /* Country code is mandatory for 5GHz band. */
        if (config.getBand() == SoftApConfiguration.BAND_5GHZ
                && countryCode == null) {
            Log.e(TAG, "5GHz band is not allowed without country code");
            return SoftApManager.START_RESULT_FAILURE_GENERAL;
        }
        if (!capability.areFeaturesSupported(SOFTAP_FEATURE_ACS_OFFLOAD)) {
            /* Select a channel if it is not specified and ACS is not enabled */
            if (config.getChannel() == 0) {
                int freq = chooseApChannel(config.getBand(), coexManager, resources,
                        capability);
                if (freq == -1) {
                    /* We're not able to get channel from wificond. */
                    Log.e(TAG, "Failed to get available channel.");
                    return SoftApManager.START_RESULT_FAILURE_NO_CHANNEL;
                }
                configBuilder.setChannel(
                        ScanResult.convertFrequencyMhzToChannelIfSupported(freq),
                        convertFrequencyToBand(freq));
            }

            if (SdkLevel.isAtLeastT()) {
                /* remove list of allowed channels since they only apply to ACS */
                if (sVerboseLoggingEnabled) {
                    Log.i(TAG, "Ignoring Allowed ACS channels since ACS is not supported.");
                }
                configBuilder.setAllowedAcsChannels(SoftApConfiguration.BAND_2GHZ,
                        new int[] {});
                configBuilder.setAllowedAcsChannels(SoftApConfiguration.BAND_5GHZ,
                        new int[] {});
                configBuilder.setAllowedAcsChannels(SoftApConfiguration.BAND_6GHZ,
                        new int[] {});
            }
        }

        return SoftApManager.START_RESULT_SUCCESS;
    }

    /**
     * Helper function for converting WifiConfiguration to SoftApConfiguration.
     *
     * Only Support None and WPA2 configuration conversion.
     * Note that WifiConfiguration only Supports 2GHz, 5GHz, 2GHz+5GHz bands,
     * so conversion is limited to these bands.
     *
     * @param wifiConfig the WifiConfiguration which need to convert.
     * @return the SoftApConfiguration if wifiConfig is valid, null otherwise.
     */
    @Nullable
    public static SoftApConfiguration fromWifiConfiguration(
            @NonNull WifiConfiguration wifiConfig) {
        SoftApConfiguration.Builder configBuilder = new SoftApConfiguration.Builder();
        try {
            // WifiConfiguration#SSID uses a formatted string with double quotes for UTF-8 and no
            // quotes for hexadecimal. But to support legacy behavior, we need to continue
            // setting the entire string with quotes as the UTF-8 SSID.
            configBuilder.setSsid(wifiConfig.SSID);
            if (wifiConfig.getAuthType() == WifiConfiguration.KeyMgmt.WPA2_PSK) {
                configBuilder.setPassphrase(wifiConfig.preSharedKey,
                        SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
            }
            configBuilder.setHiddenSsid(wifiConfig.hiddenSSID);

            int band;
            switch (wifiConfig.apBand) {
                case WifiConfiguration.AP_BAND_2GHZ:
                    band = SoftApConfiguration.BAND_2GHZ;
                    break;
                case WifiConfiguration.AP_BAND_5GHZ:
                    band = SoftApConfiguration.BAND_5GHZ;
                    break;
                case WifiConfiguration.AP_BAND_60GHZ:
                    band = SoftApConfiguration.BAND_60GHZ;
                    break;
                default:
                    // WifiConfiguration.AP_BAND_ANY means only 2GHz and 5GHz bands
                    band = SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ;
                    break;
            }
            if (wifiConfig.apChannel == 0) {
                configBuilder.setBand(band);
            } else {
                configBuilder.setChannel(wifiConfig.apChannel, band);
            }
        } catch (IllegalArgumentException iae) {
            Log.e(TAG, "Invalid WifiConfiguration" + iae);
            return null;
        } catch (IllegalStateException ise) {
            Log.e(TAG, "Invalid WifiConfiguration" + ise);
            return null;
        }
        return configBuilder.build();
    }

    /**
     * Helper function to creating SoftApCapability instance with initial field from resource file.
     *
     * @param context the caller context used to get value from resource file.
     * @return SoftApCapability which updated the feature support or not from resource.
     */
    @NonNull
    public static SoftApCapability updateCapabilityFromResource(@NonNull Context context) {
        long features = 0;
        if (isAcsSupported(context)) {
            Log.d(TAG, "Update Softap capability, add acs feature support");
            features |= SOFTAP_FEATURE_ACS_OFFLOAD;
        }

        if (isClientForceDisconnectSupported(context)) {
            Log.d(TAG, "Update Softap capability, add client control feature support");
            features |= SOFTAP_FEATURE_CLIENT_FORCE_DISCONNECT;
        }

        if (isWpa3SaeSupported(context)) {
            Log.d(TAG, "Update Softap capability, add SAE feature support");
            features |= SOFTAP_FEATURE_WPA3_SAE;
        }

        if (isMacCustomizationSupported(context)) {
            Log.d(TAG, "Update Softap capability, add MAC customization support");
            features |= SOFTAP_FEATURE_MAC_ADDRESS_CUSTOMIZATION;
        }

        if (isSoftApBandSupported(context, SoftApConfiguration.BAND_2GHZ)) {
            Log.d(TAG, "Update Softap capability, add 2.4G support");
            features |= SOFTAP_FEATURE_BAND_24G_SUPPORTED;
        }

        if (isSoftApBandSupported(context, SoftApConfiguration.BAND_5GHZ)) {
            Log.d(TAG, "Update Softap capability, add 5G support");
            features |= SOFTAP_FEATURE_BAND_5G_SUPPORTED;
        }

        if (isSoftApBandSupported(context, SoftApConfiguration.BAND_6GHZ)) {
            Log.d(TAG, "Update Softap capability, add 6G support");
            features |= SOFTAP_FEATURE_BAND_6G_SUPPORTED;
        }

        if (isSoftApBandSupported(context, SoftApConfiguration.BAND_60GHZ)) {
            Log.d(TAG, "Update Softap capability, add 60G support");
            features |= SOFTAP_FEATURE_BAND_60G_SUPPORTED;
        }

        if (isIeee80211axSupported(context)) {
            Log.d(TAG, "Update Softap capability, add ax support");
            features |= SOFTAP_FEATURE_IEEE80211_AX;
        }

        if (isIeee80211beSupported(context)) {
            Log.d(TAG, "Update Softap capability, add be support");
            features |= SOFTAP_FEATURE_IEEE80211_BE;
        }

        if (isOweTransitionSupported(context)) {
            Log.d(TAG, "Update Softap capability, add OWE Transition feature support");
            features |= SOFTAP_FEATURE_WPA3_OWE_TRANSITION;
        }

        if (isOweSupported(context)) {
            Log.d(TAG, "Update Softap capability, add OWE feature support");
            features |= SOFTAP_FEATURE_WPA3_OWE;
        }

        SoftApCapability capability = new SoftApCapability(features);
        int hardwareSupportedMaxClient = context.getResources().getInteger(
                R.integer.config_wifiHardwareSoftapMaxClientCount);
        if (hardwareSupportedMaxClient > 0) {
            Log.d(TAG, "Update Softap capability, max client = " + hardwareSupportedMaxClient);
            capability.setMaxSupportedClients(hardwareSupportedMaxClient);
        }

        return capability;
    }

    /**
     * Helper function to update SoftApCapability instance based on config store.
     *
     * @param capability the original softApCapability
     * @param configStore where we stored the Capability after first time fetch from driver.
     * @return SoftApCapability which updated from the config store.
     */
    @NonNull
    public static SoftApCapability updateCapabilityFromConfigStore(
            SoftApCapability capability,
            WifiSettingsConfigStore configStore) {
        if (capability == null) {
            return null;
        }
        if (capability.areFeaturesSupported(SOFTAP_FEATURE_IEEE80211_BE)) {
            capability.setSupportedFeatures(isIeee80211beEnabledInConfig(configStore),
                    SOFTAP_FEATURE_IEEE80211_BE);
        }
        return capability;
    }

    /**
     * Helper function to get device support 802.11 AX on Soft AP or not
     *
     * @param context the caller context used to get value from resource file.
     * @return true if supported, false otherwise.
     */
    public static boolean isIeee80211axSupported(@NonNull Context context) {
        return context.getResources().getBoolean(
                    R.bool.config_wifiSoftapIeee80211axSupported);
    }

    /**
     * Helper function to get device support 802.11 BE on Soft AP or not
     *
     * @param context the caller context used to get value from resource file.
     * @return true if supported, false otherwise.
     */
    public static boolean isIeee80211beSupported(@NonNull Context context) {
        return context.getResources().getBoolean(
                    R.bool.config_wifiSoftapIeee80211beSupported);
    }

    /**
     * Helper function to check Config supports 802.11 BE on Soft AP or not
     *
     * @param configStore to check the support from WifiSettingsConfigStore
     * @return true if supported, false otherwise.
     */
    public static boolean isIeee80211beEnabledInConfig(
            WifiSettingsConfigStore configStore) {
        return configStore.get(
                    WifiSettingsConfigStore.WIFI_WIPHY_11BE_SUPPORTED);
    }

    /**
     * Helper function to get device support AP MAC randomization or not.
     *
     * @param context the caller context used to get value from resource file.
     * @return true if supported, false otherwise.
     */
    public static boolean isApMacRandomizationSupported(@NonNull Context context) {
        return context.getResources().getBoolean(
                    R.bool.config_wifi_ap_mac_randomization_supported);
    }

    /**
     * Helper function to get HAL support bridged AP or not.
     *
     * @param context the caller context used to get value from resource file.
     * @param wifiNative to get the Iface combination from device.
     * @return true if supported, false otherwise.
     */
    public static boolean isBridgedModeSupported(
            @NonNull Context context, @NonNull WifiNative wifiNative) {
        return SdkLevel.isAtLeastS() && context.getResources().getBoolean(
                    R.bool.config_wifiBridgedSoftApSupported)
                    && wifiNative.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                            put(HDM_CREATE_IFACE_AP_BRIDGE, 1);
                        }});
    }

    /**
     * Helper function to get HAL support STA + bridged AP or not.
     *
     * @param context the caller context used to get value from resource file.
     * @param wifiNative to get the Iface combination from device.
     * @return true if supported, false otherwise.
     */
    public static boolean isStaWithBridgedModeSupported(
            @NonNull Context context, @NonNull WifiNative wifiNative) {
        return SdkLevel.isAtLeastS() && context.getResources().getBoolean(
                    R.bool.config_wifiStaWithBridgedSoftApConcurrencySupported)
                    && wifiNative.canDeviceSupportCreateTypeCombo(new SparseArray<Integer>() {{
                            put(HDM_CREATE_IFACE_AP_BRIDGE, 1);
                            put(HDM_CREATE_IFACE_STA, 1);
                        }});
    }

    /**
     * Helper function to get HAL support client force disconnect or not.
     *
     * @param context the caller context used to get value from resource file.
     * @return true if supported, false otherwise.
     */
    public static boolean isClientForceDisconnectSupported(@NonNull Context context) {
        return context.getResources().getBoolean(
                R.bool.config_wifiSofapClientForceDisconnectSupported);
    }

    /**
     * Helper function to get SAE support or not.
     *
     * @param context the caller context used to get value from resource file.
     * @return true if supported, false otherwise.
     */
    public static boolean isWpa3SaeSupported(@NonNull Context context) {
        return context.getResources().getBoolean(
                R.bool.config_wifi_softap_sae_supported);
    }

    /**
     * Helper function to get ACS support or not.
     *
     * @param context the caller context used to get value from resource file.
     * @return true if supported, false otherwise.
     */
    public static boolean isAcsSupported(@NonNull Context context) {
        return context.getResources().getBoolean(
                R.bool.config_wifi_softap_acs_supported);
    }

    /**
     * Helper function to get MAC Address customization or not.
     *
     * @param context the caller context used to get value from resource file.
     * @return true if supported, false otherwise.
     */
    public static boolean isMacCustomizationSupported(@NonNull Context context) {
        return context.getResources().getBoolean(
                R.bool.config_wifiSoftapMacAddressCustomizationSupported);
    }

    /**
     * Helper function to get whether or not Soft AP support on particular band.
     *
     * @param context the caller context used to get value from resource file.
     * @param band the band soft AP to operate on.
     * @return true if supported, false otherwise.
     */
    public static boolean isSoftApBandSupported(@NonNull Context context, @BandType int band) {
        switch (band) {
            case SoftApConfiguration.BAND_2GHZ:
                return context.getResources().getBoolean(R.bool.config_wifi24ghzSupport)
                        && context.getResources().getBoolean(
                        R.bool.config_wifiSoftap24ghzSupported);
            case SoftApConfiguration.BAND_5GHZ:
                return context.getResources().getBoolean(R.bool.config_wifi5ghzSupport)
                        && context.getResources().getBoolean(
                        R.bool.config_wifiSoftap5ghzSupported);
            case SoftApConfiguration.BAND_6GHZ:
                return context.getResources().getBoolean(R.bool.config_wifi6ghzSupport)
                        && context.getResources().getBoolean(
                        R.bool.config_wifiSoftap6ghzSupported);
            case SoftApConfiguration.BAND_60GHZ:
                return context.getResources().getBoolean(R.bool.config_wifi60ghzSupport)
                        && context.getResources().getBoolean(
                        R.bool.config_wifiSoftap60ghzSupported);
            default:
                return false;
        }
    }

    /**
     * Helper function to get whether or not dynamic country code update is supported when Soft AP
     * enabled.
     *
     * @param context the caller context used to get value from resource file.
     * @return true if supported, false otherwise.
     */
    public static boolean isSoftApDynamicCountryCodeSupported(@NonNull Context context) {
        return context.getResources().getBoolean(
                R.bool.config_wifiSoftApDynamicCountryCodeUpdateSupported);
    }


    /**
     * Helper function to get whether or not restart Soft AP required when country code changed.
     *
     * @param context the caller context used to get value from resource file.
     * @return true if supported, false otherwise.
     */
    public static boolean isSoftApRestartRequiredWhenCountryCodeChanged(@NonNull Context context) {
        return context.getResources().getBoolean(
                R.bool.config_wifiForcedSoftApRestartWhenCountryCodeChanged);
    }

    /**
     * Helper function to get OWE-Transition is support or not.
     *
     * @param context the caller context used to get value from resource file.
     * @return true if supported, false otherwise.
     */
    public static boolean isOweTransitionSupported(@NonNull Context context) {
        return context.getResources().getBoolean(
                R.bool.config_wifiSoftapOweTransitionSupported);
    }

    /**
     * Helper function to get OWE is support or not.
     *
     * @param context the caller context used to get value from resource file.
     * @return true if supported, false otherwise.
     */
    public static boolean isOweSupported(@NonNull Context context) {
        return context.getResources().getBoolean(
                R.bool.config_wifiSoftapOweSupported);
    }

    /**
     * Helper function for comparing two SoftApConfiguration.
     *
     * @param currentConfig the original configuration.
     * @param newConfig the new configuration which plan to apply.
     * @return true if the difference between the two configurations requires a restart to apply,
     *         false otherwise.
     */
    public static boolean checkConfigurationChangeNeedToRestart(
            SoftApConfiguration currentConfig, SoftApConfiguration newConfig) {
        return !Objects.equals(currentConfig.getWifiSsid(), newConfig.getWifiSsid())
                || !Objects.equals(currentConfig.getBssid(), newConfig.getBssid())
                || currentConfig.getSecurityType() != newConfig.getSecurityType()
                || !Objects.equals(currentConfig.getPassphrase(), newConfig.getPassphrase())
                || currentConfig.isHiddenSsid() != newConfig.isHiddenSsid()
                || currentConfig.getBand() != newConfig.getBand()
                || currentConfig.getChannel() != newConfig.getChannel()
                || (SdkLevel.isAtLeastS() && !currentConfig.getChannels().toString()
                        .equals(newConfig.getChannels().toString()));
    }


    /**
     * Helper function for checking all of the configuration are supported or not.
     *
     * @param config target configuration want to check.
     * @param capability the capability which indicate feature support or not.
     * @return true if supported, false otherwise.
     */
    public static boolean checkSupportAllConfiguration(SoftApConfiguration config,
            SoftApCapability capability) {
        if (!capability.areFeaturesSupported(SOFTAP_FEATURE_CLIENT_FORCE_DISCONNECT)
                && (config.getMaxNumberOfClients() != 0 || config.isClientControlByUserEnabled()
                || config.getBlockedClientList().size() != 0)) {
            Log.d(TAG, "Error, Client control requires HAL support");
            return false;
        }
        if (!capability.areFeaturesSupported(SOFTAP_FEATURE_WPA3_SAE)) {
            if (config.getSecurityType() == SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION
                    || config.getSecurityType() == SoftApConfiguration.SECURITY_TYPE_WPA3_SAE) {
                Log.d(TAG, "Error, SAE requires HAL support");
                return false;
            }
        }
        if (!capability.areFeaturesSupported(SOFTAP_FEATURE_MAC_ADDRESS_CUSTOMIZATION)) {
            if (config.getBssid() != null) {
                Log.d(TAG, "Error, MAC address customization requires HAL support");
                return false;
            }
            if (SdkLevel.isAtLeastS()
                    && (config.getMacRandomizationSetting()
                    == SoftApConfiguration.RANDOMIZATION_PERSISTENT
                    || config.getMacRandomizationSetting()
                    == SoftApConfiguration.RANDOMIZATION_NON_PERSISTENT)) {
                Log.d(TAG, "Error, MAC randomization requires HAL support");
                return false;
            }
        }
        int requestedBands = 0;
        for (int band : config.getBands()) {
            requestedBands |= band;
        }
        if (!capability.areFeaturesSupported(SOFTAP_FEATURE_BAND_24G_SUPPORTED)) {
            if ((requestedBands & SoftApConfiguration.BAND_2GHZ) != 0) {
                Log.d(TAG, "Error, 2.4Ghz band requires HAL support");
                return false;
            }
        }
        if (!capability.areFeaturesSupported(SOFTAP_FEATURE_BAND_5G_SUPPORTED)) {
            if ((requestedBands & SoftApConfiguration.BAND_5GHZ) != 0) {
                Log.d(TAG, "Error, 5Ghz band requires HAL support");
                return false;
            }
        }
        if (!capability.areFeaturesSupported(SOFTAP_FEATURE_BAND_6G_SUPPORTED)) {
            if ((requestedBands & SoftApConfiguration.BAND_6GHZ) != 0) {
                Log.d(TAG, "Error, 6Ghz band requires HAL support");
                return false;
            }
        }
        if (!capability.areFeaturesSupported(SOFTAP_FEATURE_BAND_60G_SUPPORTED)) {
            if ((requestedBands & SoftApConfiguration.BAND_60GHZ) != 0) {
                Log.d(TAG, "Error, 60Ghz band requires HAL support");
                return false;
            }
        }
        if (!capability.areFeaturesSupported(SOFTAP_FEATURE_WPA3_OWE_TRANSITION)) {
            if (config.getSecurityType() == SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION) {
                Log.d(TAG, "Error, OWE transition requires HAL support");
                return false;
            }
        }
        if (!capability.areFeaturesSupported(SOFTAP_FEATURE_WPA3_OWE)) {
            if (config.getSecurityType() == SoftApConfiguration.SECURITY_TYPE_WPA3_OWE) {
                Log.d(TAG, "Error, OWE requires HAL support");
                return false;
            }
        }

        // Checks for Dual AP
        if (SdkLevel.isAtLeastS() && config.getBands().length > 1) {
            int[] bands = config.getBands();
            if ((bands[0] & SoftApConfiguration.BAND_60GHZ) != 0
                    || (bands[1] & SoftApConfiguration.BAND_60GHZ) != 0) {
                Log.d(TAG, "Error, dual APs doesn't support on 60GHz");
                return false;
            }
            if (!capability.areFeaturesSupported(SOFTAP_FEATURE_ACS_OFFLOAD)
                    && (config.getChannels().valueAt(0) == 0
                    || config.getChannels().valueAt(1) == 0)) {
                Log.d(TAG, "Error, dual APs requires HAL ACS support when channel isn't specified");
                return false;
            }
        }
        return true;
    }


    /**
     * Check if need to provide freq range for ACS.
     *
     * @param band in SoftApConfiguration.BandType
     * @param context the caller context used to get values from resource file
     * @param config the used SoftApConfiguration
     *
     * @return true when freq ranges is needed, otherwise false.
     */
    public static boolean isSendFreqRangesNeeded(@BandType int band, Context context,
            SoftApConfiguration config) {
        // Fist we check if one of the selected bands has restrictions in the overlay file or in the
        // provided SoftApConfiguration.
        // Note,
        //   - We store the config string here for future use, hence we need to check all bands.
        //   - If there is no restrictions on channels, we store the full band
        for (int b : SoftApConfiguration.BAND_TYPES) {
            if ((band & b) != 0) {
                List<Integer> configuredList = getConfiguredChannelList(context.getResources(), b);
                if (configuredList != null && !configuredList.isEmpty()) {
                    // If any of the selected band has restriction in the overlay file return true.
                    return true;
                }
                if (SdkLevel.isAtLeastT() && config.getAllowedAcsChannels(b).length != 0) {
                    return true;
                }
            }
        }

        // Next, if only one of 5G or 6G is selected, then we need freqList to separate them
        // Since there is no other way.
        if (((band & SoftApConfiguration.BAND_5GHZ) != 0)
                && ((band & SoftApConfiguration.BAND_6GHZ) == 0)) {
            return true;
        }
        if (((band & SoftApConfiguration.BAND_5GHZ) == 0)
                && ((band & SoftApConfiguration.BAND_6GHZ) != 0)) {
            return true;
        }

        // In all other cases, we don't need to set the freqList
        return false;
    }

    /**
     * Collect a List of allowed channels for ACS operations on a selected band
     *
     * @param band on which channel list are required
     * @param oemConfigString Configuration string from OEM resource file.
     *        An empty string means all channels on this band are allowed
     * @param callerConfig allowed chnannels as required by the caller
     *
     * @return List of channel numbers that meet both criteria
     */
    public static List<Integer> collectAllowedAcsChannels(@BandType int band,
            String oemConfigString, int[] callerConfig) {

        // Convert the OEM config string into a set of channel numbers
        Set<Integer> allowedChannelSet = getOemAllowedChannels(band, oemConfigString);

        // Update the allowed channels with user configuration
        allowedChannelSet.retainAll(getCallerAllowedChannels(band, callerConfig));

        return new ArrayList<Integer>(allowedChannelSet);
    }

    private static Set<Integer> getSetForAllChannelsInBand(@BandType int band) {
        switch(band) {
            case SoftApConfiguration.BAND_2GHZ:
                return IntStream.rangeClosed(
                        ScanResult.BAND_24_GHZ_FIRST_CH_NUM,
                        ScanResult.BAND_24_GHZ_LAST_CH_NUM)
                        .boxed()
                        .collect(Collectors.toSet());

            case SoftApConfiguration.BAND_5GHZ:
                return IntStream.rangeClosed(
                        ScanResult.BAND_5_GHZ_FIRST_CH_NUM,
                        ScanResult.BAND_5_GHZ_LAST_CH_NUM)
                        .boxed()
                        .collect(Collectors.toSet());

            case SoftApConfiguration.BAND_6GHZ:
                return IntStream.rangeClosed(
                        ScanResult.BAND_6_GHZ_FIRST_CH_NUM,
                        ScanResult.BAND_6_GHZ_LAST_CH_NUM)
                        .boxed()
                        .collect(Collectors.toSet());
            default:
                Log.e(TAG, "Invalid band: " + bandToString(band));
                return Collections.emptySet();
        }
    }

    private static Set<Integer> getOemAllowedChannels(@BandType int band, String oemConfigString) {
        if (TextUtils.isEmpty(oemConfigString)) {
            // Empty string means all channels are allowed in this band
            return getSetForAllChannelsInBand(band);
        }

        // String is not empty, parsing it
        Set<Integer> allowedChannelsOem = new HashSet<>();

        for (String channelRange : oemConfigString.split(",")) {
            try {
                if (channelRange.contains("-")) {
                    String[] channels  = channelRange.split("-");
                    if (channels.length != 2) {
                        Log.e(TAG, "Unrecognized channel range, length is " + channels.length);
                        continue;
                    }
                    int start = Integer.parseInt(channels[0].trim());
                    int end = Integer.parseInt(channels[1].trim());
                    if (start > end) {
                        Log.e(TAG, "Invalid channel range, from " + start + " to " + end);
                        continue;
                    }

                    allowedChannelsOem.addAll(IntStream.rangeClosed(start, end)
                            .boxed().collect(Collectors.toSet()));
                } else if (!TextUtils.isEmpty(channelRange)) {
                    int channel = Integer.parseInt(channelRange.trim());
                    allowedChannelsOem.add(channel);
                }
            } catch (NumberFormatException e) {
                // Ignore malformed value
                Log.e(TAG, "Malformed channel value detected: " + e);
                continue;
            }
        }

        return allowedChannelsOem;
    }

    private static Set<Integer> getCallerAllowedChannels(@BandType int band, int[] callerConfig) {
        if (callerConfig.length == 0) {
            // Empty set means all channels are allowed in this band
            return getSetForAllChannelsInBand(band);
        }

        // Otherwise return the caller set as is
        return IntStream.of(callerConfig).boxed()
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Deep copy for object Map<String, SoftApInfo>
     */
    public static Map<String, SoftApInfo> deepCopyForSoftApInfoMap(
            Map<String, SoftApInfo> originalMap) {
        if (originalMap == null) {
            return null;
        }
        Map<String, SoftApInfo> deepCopyMap = new HashMap<String, SoftApInfo>();
        for (Map.Entry<String, SoftApInfo> entry: originalMap.entrySet()) {
            deepCopyMap.put(entry.getKey(), new SoftApInfo(entry.getValue()));
        }
        return deepCopyMap;
    }

    /**
     * Deep copy for object Map<String, List<WifiClient>>
     */
    public static Map<String, List<WifiClient>> deepCopyForWifiClientListMap(
            Map<String, List<WifiClient>> originalMap) {
        if (originalMap == null) {
            return null;
        }
        Map<String, List<WifiClient>> deepCopyMap = new HashMap<String, List<WifiClient>>();
        for (Map.Entry<String, List<WifiClient>> entry: originalMap.entrySet()) {
            List<WifiClient> clients = new ArrayList<>();
            for (WifiClient client : entry.getValue()) {
                clients.add(new WifiClient(client.getMacAddress(),
                        client.getApInstanceIdentifier()));
            }
            deepCopyMap.put(entry.getKey(), clients);
        }
        return deepCopyMap;
    }

    /**
     * Observer the available channel from native layer (vendor HAL if getUsableChannels is
     * supported, or wificond if not supported) and update the SoftApCapability
     *
     * @param softApCapability the current softap capability
     * @param context the caller context used to get value from resource file
     * @param wifiNative reference used to collect regulatory restrictions.
     * @param channelMap the channel for each band
     * @return updated soft AP capability
     */
    public static SoftApCapability updateSoftApCapabilityWithAvailableChannelList(
            @NonNull SoftApCapability softApCapability, @NonNull Context context,
            @NonNull WifiNative wifiNative, @NonNull SparseArray<int[]> channelMap) {
        SoftApCapability newSoftApCapability = new SoftApCapability(softApCapability);
        if (channelMap != null) {
            for (int band : SoftApConfiguration.BAND_TYPES) {
                if (isSoftApBandSupported(context, band)) {
                    int[] supportedChannelList = channelMap.get(band);
                    if (supportedChannelList != null) {
                        newSoftApCapability.setSupportedChannelList(band, supportedChannelList);
                    }
                }
            }
            return newSoftApCapability;
        }
        List<Integer> supportedChannelList = null;

        for (int band : SoftApConfiguration.BAND_TYPES) {
            if (isSoftApBandSupported(context, band)) {
                supportedChannelList = getAvailableChannelFreqsForBand(
                        band, wifiNative, context.getResources(), false);
                if (supportedChannelList != null) {
                    newSoftApCapability.setSupportedChannelList(
                            band,
                            supportedChannelList.stream().mapToInt(Integer::intValue).toArray());
                }
            }
        }
        return newSoftApCapability;
    }

    /**
     * Helper function to check if security type can ignore password.
     *
     * @param security type for SoftApConfiguration.
     * @return true for Open/Owe-Transition SoftAp AKM.
     */
    public static boolean isNonPasswordAP(int security) {
        return (security == SoftApConfiguration.SECURITY_TYPE_OPEN
                || security == SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION
                || security == SoftApConfiguration.SECURITY_TYPE_WPA3_OWE);
    }
}
