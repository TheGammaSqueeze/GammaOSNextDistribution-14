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

package com.android.car.settings.qc;

import static com.android.car.qc.QCItem.QC_ACTION_TOGGLE_STATE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.UserManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.qc.QCActionItem;
import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.settings.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class WifiRowTest extends BaseSettingsQCItemTestCase {
    private WifiRow mWifiRow;

    @Mock
    private WifiManager mWifiManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemService(WifiManager.class)).thenReturn(mWifiManager);

        mWifiRow = new WifiRow(mContext);
    }

    @Test
    public void getQCItem_setsTitle() {
        when(mWifiManager.isWifiEnabled()).thenReturn(false);
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_DISABLED);
        QCRow row = getWifiRow();
        assertThat(row.getTitle()).isEqualTo(mContext.getString(R.string.wifi_settings));
    }

    @Test
    public void getQCItem_wifiDisabled_returnsRow() {
        when(mWifiManager.isWifiEnabled()).thenReturn(false);
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_DISABLED);
        QCRow row = getWifiRow();
        assertThat(row.getSubtitle()).isEqualTo(mContext.getString(R.string.wifi_disabled));
        assertThat(row.getStartIcon().getResId()).isEqualTo(R.drawable.ic_qc_wifi_disabled);
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isChecked()).isFalse();
    }

    @Test
    public void getQCItem_wifiNotConnected_returnsRow() {
        when(mWifiManager.isWifiEnabled()).thenReturn(true);
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);
        WifiInfo wifiInfo = mock(WifiInfo.class);
        when(wifiInfo.getSSID()).thenReturn(WifiManager.UNKNOWN_SSID);
        when(wifiInfo.getNetworkId()).thenReturn(-1);
        when(mWifiManager.getConnectionInfo()).thenReturn(wifiInfo);
        QCRow row = getWifiRow();
        assertThat(row.getSubtitle()).isEqualTo(mContext.getString(R.string.wifi_disconnected));
        assertThat(row.getStartIcon().getResId()).isEqualTo(R.drawable.ic_qc_wifi_disconnected);
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isChecked()).isTrue();
    }

    @Test
    public void getQCItem_wifiConnected_returnsRow() {
        String testSSID = "TEST_SSID";
        when(mWifiManager.isWifiEnabled()).thenReturn(true);
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);
        WifiInfo wifiInfo = mock(WifiInfo.class);
        when(wifiInfo.getSSID()).thenReturn(testSSID);
        when(wifiInfo.getNetworkId()).thenReturn(1);
        when(wifiInfo.getRssi()).thenReturn(-90);
        when(mWifiManager.getConnectionInfo()).thenReturn(wifiInfo);
        when(mWifiManager.calculateSignalLevel(anyInt())).thenReturn(4);
        QCRow row = getWifiRow();
        assertThat(row.getSubtitle()).isEqualTo(testSSID);
        assertThat(row.getStartIcon().getResId()).isEqualTo(R.drawable.ic_qc_wifi_level_4);
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isChecked()).isTrue();
    }

    @Test
    public void getQCItem_passpointWifiConnected_hasFriendlyName_returnsRow() {
        String testTitle = "TEST_TITLE";
        when(mWifiManager.isWifiEnabled()).thenReturn(true);
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);
        WifiInfo wifiInfo = mock(WifiInfo.class);
        when(wifiInfo.isPasspointAp()).thenReturn(true);
        when(wifiInfo.getPasspointProviderFriendlyName()).thenReturn(testTitle);
        when(wifiInfo.getNetworkId()).thenReturn(1);
        when(wifiInfo.getRssi()).thenReturn(-90);
        when(mWifiManager.getConnectionInfo()).thenReturn(wifiInfo);
        when(mWifiManager.calculateSignalLevel(anyInt())).thenReturn(4);
        QCRow row = getWifiRow();
        assertThat(row.getSubtitle()).isEqualTo(testTitle);
        assertThat(row.getStartIcon().getResId()).isEqualTo(R.drawable.ic_qc_wifi_level_4);
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isChecked()).isTrue();
    }

    @Test
    public void getQCItem_passpointWifiConnected_noFriendlyName_returnsRow() {
        String testSSID = "TEST_SSID";
        when(mWifiManager.isWifiEnabled()).thenReturn(true);
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);
        WifiInfo wifiInfo = mock(WifiInfo.class);
        when(wifiInfo.isPasspointAp()).thenReturn(true);
        when(wifiInfo.getSSID()).thenReturn(testSSID);
        when(wifiInfo.getNetworkId()).thenReturn(1);
        when(wifiInfo.getRssi()).thenReturn(-90);
        when(mWifiManager.getConnectionInfo()).thenReturn(wifiInfo);
        when(mWifiManager.calculateSignalLevel(anyInt())).thenReturn(4);
        QCRow row = getWifiRow();
        assertThat(row.getSubtitle()).isEqualTo(testSSID);
        assertThat(row.getStartIcon().getResId()).isEqualTo(R.drawable.ic_qc_wifi_level_4);
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isChecked()).isTrue();
    }

    @Test
    public void getQCItem_osuWifiConnected_hasFriendlyName_returnsRow() {
        String testTitle = "TEST_TITLE";
        when(mWifiManager.isWifiEnabled()).thenReturn(true);
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);
        WifiInfo wifiInfo = mock(WifiInfo.class);
        when(wifiInfo.isOsuAp()).thenReturn(true);
        when(wifiInfo.getPasspointProviderFriendlyName()).thenReturn(testTitle);
        when(wifiInfo.getNetworkId()).thenReturn(1);
        when(wifiInfo.getRssi()).thenReturn(-90);
        when(mWifiManager.getConnectionInfo()).thenReturn(wifiInfo);
        when(mWifiManager.calculateSignalLevel(anyInt())).thenReturn(4);
        QCRow row = getWifiRow();
        assertThat(row.getSubtitle()).isEqualTo(testTitle);
        assertThat(row.getStartIcon().getResId()).isEqualTo(R.drawable.ic_qc_wifi_level_4);
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isChecked()).isTrue();
    }

    @Test
    public void getQCItem_osuWifiConnected_noFriendlyName_returnsRow() {
        String testSSID = "TEST_SSID";
        when(mWifiManager.isWifiEnabled()).thenReturn(true);
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);
        WifiInfo wifiInfo = mock(WifiInfo.class);
        when(wifiInfo.isOsuAp()).thenReturn(true);
        when(wifiInfo.getSSID()).thenReturn(testSSID);
        when(wifiInfo.getNetworkId()).thenReturn(1);
        when(wifiInfo.getRssi()).thenReturn(-90);
        when(mWifiManager.getConnectionInfo()).thenReturn(wifiInfo);
        when(mWifiManager.calculateSignalLevel(anyInt())).thenReturn(4);
        QCRow row = getWifiRow();
        assertThat(row.getSubtitle()).isEqualTo(testSSID);
        assertThat(row.getStartIcon().getResId()).isEqualTo(R.drawable.ic_qc_wifi_level_4);
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isChecked()).isTrue();
    }

    @Test
    public void getQCItem_hasBaseUmRestriction_switchDisabled() {
        when(mWifiManager.isWifiEnabled()).thenReturn(false);
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_DISABLED);
        setBaseUserRestriction(UserManager.DISALLOW_CONFIG_WIFI, /* restricted= */ true);
        QCRow row = getWifiRow();
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isEnabled()).isFalse();
        assertThat(actionItem.isClickableWhileDisabled()).isFalse();
    }

    @Test
    public void getQCItem_hasUmRestriction_switchClickableWhileDisabled() {
        when(mWifiManager.isWifiEnabled()).thenReturn(false);
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_DISABLED);
        setUserRestriction(UserManager.DISALLOW_CONFIG_WIFI, /* restricted= */ true);
        QCRow row = getWifiRow();
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isEnabled()).isFalse();
        assertThat(actionItem.isClickableWhileDisabled()).isTrue();
    }

    @Test
    public void onNotifyChange_togglesWifi() {
        when(mWifiManager.isWifiEnabled()).thenReturn(false);
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_DISABLED);
        Intent intent = new Intent();
        intent.putExtra(QC_ACTION_TOGGLE_STATE, true);
        mWifiRow.onNotifyChange(intent);
        verify(mWifiManager).setWifiEnabled(true);
    }

    @Test
    public void getQCItem_createsRow_zoneWrite() {
        mWifiRow.setAvailabilityStatusForZone("write");
        String testSSID = "TEST_SSID";
        when(mWifiManager.isWifiEnabled()).thenReturn(true);
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);
        WifiInfo wifiInfo = mock(WifiInfo.class);
        when(wifiInfo.getSSID()).thenReturn(testSSID);
        when(wifiInfo.getNetworkId()).thenReturn(1);
        when(wifiInfo.getRssi()).thenReturn(-90);
        when(mWifiManager.getConnectionInfo()).thenReturn(wifiInfo);
        when(mWifiManager.calculateSignalLevel(anyInt())).thenReturn(4);
        QCRow row = getWifiRow();
        assertThat(row.getSubtitle()).isEqualTo(testSSID);
        assertThat(row.getStartIcon().getResId()).isEqualTo(R.drawable.ic_qc_wifi_level_4);
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isEnabled()).isTrue();
    }

    @Test
    public void getQCItem_createsRow_zoneRead() {
        mWifiRow.setAvailabilityStatusForZone("read");
        String testSSID = "TEST_SSID";
        when(mWifiManager.isWifiEnabled()).thenReturn(true);
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);
        WifiInfo wifiInfo = mock(WifiInfo.class);
        when(wifiInfo.getSSID()).thenReturn(testSSID);
        when(wifiInfo.getNetworkId()).thenReturn(1);
        when(wifiInfo.getRssi()).thenReturn(-90);
        when(mWifiManager.getConnectionInfo()).thenReturn(wifiInfo);
        when(mWifiManager.calculateSignalLevel(anyInt())).thenReturn(4);
        QCRow row = getWifiRow();
        assertThat(row.getSubtitle()).isEqualTo(testSSID);
        assertThat(row.getStartIcon().getResId()).isEqualTo(R.drawable.ic_qc_wifi_level_4);
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isEnabled()).isFalse();
        assertThat(actionItem.isClickableWhileDisabled()).isTrue();
    }

    @Test
    public void getQCItem_createsRow_zoneHidden() {
        mWifiRow.setAvailabilityStatusForZone("hidden");
        String testSSID = "TEST_SSID";
        when(mWifiManager.isWifiEnabled()).thenReturn(true);
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);
        WifiInfo wifiInfo = mock(WifiInfo.class);
        when(wifiInfo.getSSID()).thenReturn(testSSID);
        when(wifiInfo.getNetworkId()).thenReturn(1);
        when(wifiInfo.getRssi()).thenReturn(-90);
        when(mWifiManager.getConnectionInfo()).thenReturn(wifiInfo);
        when(mWifiManager.calculateSignalLevel(anyInt())).thenReturn(4);
        QCItem item = mWifiRow.getQCItem();
        assertThat(item).isNull();
    }

    private QCRow getWifiRow() {
        QCItem item = mWifiRow.getQCItem();
        assertThat(item).isNotNull();
        assertThat(item instanceof QCList).isTrue();
        QCList list = (QCList) item;
        assertThat(list.getRows().size()).isEqualTo(1);
        return list.getRows().get(0);
    }
}
