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

package android.net.wifi;

import static android.net.wifi.ScanResult.WIFI_BAND_24_GHZ;
import static android.net.wifi.ScanResult.WIFI_BAND_5_GHZ;
import static android.net.wifi.ScanResult.WIFI_BAND_6_GHZ;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.os.Parcel;
import android.util.SparseArray;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Test;

/**
 * Unit tests for {@link android.net.wifi.WifiNetworkSelectionConfig}.
 */
@SmallTest
public class WifiNetworkSelectionConfigTest {

    /**
     * Check that parcel marshalling/unmarshalling works
     */
    @Test
    public void testWifiNetworkSelectionConfigParcel() {
        assumeTrue(SdkLevel.isAtLeastT());
        int[] rssi2Thresholds = {-81, -79, -73, -60};
        int[] rssi5Thresholds = {-80, -77, -71, -55};
        int[] rssi6Thresholds = {-79, -72, -65, -55};

        SparseArray<Integer> weights = new SparseArray<>();
        weights.put(2450, WifiNetworkSelectionConfig.FREQUENCY_WEIGHT_HIGH);
        weights.put(5450, WifiNetworkSelectionConfig.FREQUENCY_WEIGHT_LOW);

        WifiNetworkSelectionConfig config = new WifiNetworkSelectionConfig.Builder()
                .setAssociatedNetworkSelectionOverride(
                        WifiNetworkSelectionConfig.ASSOCIATED_NETWORK_SELECTION_OVERRIDE_ENABLED)
                .setSufficiencyCheckEnabledWhenScreenOff(false)
                .setUserConnectChoiceOverrideEnabled(false)
                .setLastSelectionWeightEnabled(false)
                .setRssiThresholds(WIFI_BAND_24_GHZ, rssi2Thresholds)
                .setRssiThresholds(WIFI_BAND_5_GHZ, rssi5Thresholds)
                .setRssiThresholds(WIFI_BAND_6_GHZ, rssi6Thresholds)
                .setFrequencyWeights(weights)
                .build();

        Parcel parcelW = Parcel.obtain();
        config.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        WifiNetworkSelectionConfig parcelConfig =
                WifiNetworkSelectionConfig.CREATOR.createFromParcel(parcelR);

        assertEquals(WifiNetworkSelectionConfig.ASSOCIATED_NETWORK_SELECTION_OVERRIDE_ENABLED,
                parcelConfig.getAssociatedNetworkSelectionOverride());
        assertFalse(parcelConfig.isSufficiencyCheckEnabledWhenScreenOff());
        assertTrue(parcelConfig.isSufficiencyCheckEnabledWhenScreenOn());
        assertFalse(parcelConfig.isUserConnectChoiceOverrideEnabled());
        assertFalse(parcelConfig.isLastSelectionWeightEnabled());
        assertArrayEquals(rssi2Thresholds, parcelConfig.getRssiThresholds(WIFI_BAND_24_GHZ));
        assertArrayEquals(rssi5Thresholds, parcelConfig.getRssiThresholds(WIFI_BAND_5_GHZ));
        assertArrayEquals(rssi6Thresholds, parcelConfig.getRssiThresholds(WIFI_BAND_6_GHZ));
        assertTrue(weights.contentEquals(parcelConfig.getFrequencyWeights()));
        assertEquals(config, parcelConfig);
        assertEquals(config.hashCode(), parcelConfig.hashCode());
    }

    @Test
    public void testInvalidBuilderThrowsException() {
        assumeTrue(SdkLevel.isAtLeastT());
        assertThrows(IllegalArgumentException.class,
                () -> new WifiNetworkSelectionConfig.Builder()
                        .setAssociatedNetworkSelectionOverride(-1));
    }

    @Test
    public void testInvalidRssiThresholdArrayThrowsException() {
        assumeTrue(SdkLevel.isAtLeastT());
        assertThrows(IllegalArgumentException.class,
                () -> new WifiNetworkSelectionConfig.Builder()
                        .setRssiThresholds(WIFI_BAND_24_GHZ, null));
        assertThrows(IllegalArgumentException.class,
                () -> new WifiNetworkSelectionConfig.Builder()
                        .setRssiThresholds(WIFI_BAND_24_GHZ, new int[] {-200, -100, -50, 50}));
        assertThrows(IllegalArgumentException.class,
                () -> new WifiNetworkSelectionConfig.Builder()
                        .setRssiThresholds(WIFI_BAND_5_GHZ, new int[] {-100, -60, -70, -50}));
        assertThrows(IllegalArgumentException.class,
                () -> new WifiNetworkSelectionConfig.Builder()
                        .setRssiThresholds(WIFI_BAND_5_GHZ, new int[] {-60, -60, -50, -40}));
        assertThrows(IllegalArgumentException.class,
                () -> new WifiNetworkSelectionConfig.Builder()
                        .setRssiThresholds(WIFI_BAND_5_GHZ, new int[] {-127, -60, -50, -1}));
        assertThrows(IllegalArgumentException.class,
                () -> new WifiNetworkSelectionConfig.Builder()
                        .setRssiThresholds(WIFI_BAND_5_GHZ, new int[] {-126, -60, -50, 0}));
        assertThrows(IllegalArgumentException.class,
                () -> new WifiNetworkSelectionConfig.Builder()
                        .setRssiThresholds(WIFI_BAND_6_GHZ, new int[] {0, 0, 0, -50}));
    }

    @Test
    public void testInvalidFrequencyWeightsThrowsException() {
        assumeTrue(SdkLevel.isAtLeastT());
        assertThrows(IllegalArgumentException.class,
                () -> new WifiNetworkSelectionConfig.Builder().setFrequencyWeights(null));
        SparseArray<Integer> invalidInput = new SparseArray<>();
        invalidInput.put(2400, 10);
        assertThrows(IllegalArgumentException.class,
                () -> new WifiNetworkSelectionConfig.Builder().setFrequencyWeights(invalidInput));
        invalidInput.put(2400, -10);
        assertThrows(IllegalArgumentException.class,
                () -> new WifiNetworkSelectionConfig.Builder().setFrequencyWeights(invalidInput));
    }

    /**
     * Verify the default builder creates a config with the expected default values.
     */
    @Test
    public void testDefaultBuilder() {
        assumeTrue(SdkLevel.isAtLeastT());
        WifiNetworkSelectionConfig config = new WifiNetworkSelectionConfig.Builder().build();

        assertEquals(WifiNetworkSelectionConfig.ASSOCIATED_NETWORK_SELECTION_OVERRIDE_NONE,
                config.getAssociatedNetworkSelectionOverride());
        assertTrue(config.isSufficiencyCheckEnabledWhenScreenOff());
        assertTrue(config.isSufficiencyCheckEnabledWhenScreenOn());
        assertTrue(config.isUserConnectChoiceOverrideEnabled());
        assertTrue(config.isLastSelectionWeightEnabled());
        assertArrayEquals(new int[4], config.getRssiThresholds(WIFI_BAND_24_GHZ));
        assertArrayEquals(new int[4], config.getRssiThresholds(WIFI_BAND_5_GHZ));
        assertArrayEquals(new int[4], config.getRssiThresholds(WIFI_BAND_6_GHZ));
        assertTrue(new SparseArray<Integer>().contentEquals(config.getFrequencyWeights()));
    }
}
