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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.TetheringManager;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiManager;
import android.os.UserManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.qc.QCActionItem;
import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.settings.R;
import com.android.internal.util.ConcurrentUtils;
import com.android.settingslib.wifi.WifiUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class HotspotRowTest extends BaseSettingsQCItemTestCase {
    private static final String TEST_SSID = "TEST_SSID";
    private static final String TEST_PASSWORD = "TEST_PASSWORD";

    private HotspotRow mHotspotRow;

    @Mock
    private WifiManager mWifiManager;
    @Mock
    private TetheringManager mTetheringManager;
    @Mock
    private SoftApConfiguration mSoftApConfiguration;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemService(TetheringManager.class)).thenReturn(mTetheringManager);
        when(mContext.getSystemService(WifiManager.class)).thenReturn(mWifiManager);
        when(mSoftApConfiguration.getSsid()).thenReturn(TEST_SSID);
        when(mSoftApConfiguration.getPassphrase()).thenReturn(TEST_PASSWORD);
        when(mSoftApConfiguration.getSecurityType()).thenReturn(
                SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        when(mWifiManager.getSoftApConfiguration()).thenReturn(mSoftApConfiguration);

        mHotspotRow = getHotspotRowInstance();
    }

    @Test
    public void getQCItem_setsTitleAndIcon() {
        when(mWifiManager.getWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_DISABLED);
        QCRow row = getHotspotRow();
        assertThat(row.getTitle()).isEqualTo(mContext.getString(R.string.hotspot_settings_title));
        assertThat(row.getStartIcon()).isNotNull();
        assertThat(row.getStartIcon().getResId()).isEqualTo(R.drawable.ic_qc_hotspot);
    }

    @Test
    public void getQCItem_hotspotDisabled_returnsRow() {
        when(mWifiManager.getWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_DISABLED);
        QCRow row = getHotspotRow();
        assertThat(row.getSubtitle()).isEqualTo(
                mContext.getString(R.string.wifi_hotspot_state_off));
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isChecked()).isFalse();
    }

    @Test
    public void getQCItem_hotspotEnabled_noDevices_returnsRow() {
        when(mWifiManager.getWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_ENABLED);
        QCRow row = getHotspotRow();
        assertThat(row.getSubtitle()).isEqualTo(String.format("%s / %s", TEST_SSID, TEST_PASSWORD));
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isChecked()).isTrue();
    }

    @Test
    public void getQCItem_hotspotEnabled_hasDevices_returnsRow() {
        int connectedDevices = 1;
        when(mWifiManager.getWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_ENABLED);
        mHotspotRow.setConnectedDevicesCount(connectedDevices);
        QCRow row = getHotspotRow();
        assertThat(row.getSubtitle()).isEqualTo(
                WifiUtils.getWifiTetherSummaryForConnectedDevices(mContext, connectedDevices));
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isChecked()).isTrue();
    }

    @Test
    public void getQCItem_hasBaseUmRestriction_switchDisabled() {
        setBaseUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING, /* restricted= */ true);
        QCRow row = getHotspotRow();
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isEnabled()).isFalse();
        assertThat(actionItem.isClickableWhileDisabled()).isFalse();
    }

    @Test
    public void getQCItem_hasUmRestriction_switchClickableWhileDisabled() {
        setUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING, /* restricted= */ true);
        QCRow row = getHotspotRow();
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isEnabled()).isFalse();
        assertThat(actionItem.isClickableWhileDisabled()).isTrue();
    }

    @Test
    public void onNotifyChange_togglesHotspot() {
        when(mWifiManager.getWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_DISABLED);
        Intent intent = new Intent();
        intent.putExtra(QC_ACTION_TOGGLE_STATE, true);
        mHotspotRow.onNotifyChange(intent);
        verify(mTetheringManager).startTethering(eq(ConnectivityManager.TETHERING_WIFI),
                eq(ConcurrentUtils.DIRECT_EXECUTOR), any());
    }

    @Test
    public void getQCItem_createsRow_zoneWrite() {
        mHotspotRow.setAvailabilityStatusForZone("write");
        QCRow row = getHotspotRow();
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isEnabled()).isTrue();
    }

    @Test
    public void getQCItem_createsRow_zoneRead() {
        mHotspotRow.setAvailabilityStatusForZone("read");
        QCRow row = getHotspotRow();
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isEnabled()).isFalse();
        assertThat(actionItem.isClickableWhileDisabled()).isTrue();
    }

    @Test
    public void getQCItem_createsRow_zoneHidden() {
        mHotspotRow.setAvailabilityStatusForZone("hidden");
        QCItem item = mHotspotRow.getQCItem();
        assertThat(item).isNull();
    }

    protected QCRow getHotspotRow() {
        QCItem item = mHotspotRow.getQCItem();
        assertThat(item).isNotNull();
        assertThat(item instanceof QCList).isTrue();
        QCList list = (QCList) item;
        assertThat(list.getRows().size()).isEqualTo(1);
        return list.getRows().get(0);
    }

    protected HotspotRow getHotspotRowInstance() {
        return new HotspotRow(mContext);
    }
}
