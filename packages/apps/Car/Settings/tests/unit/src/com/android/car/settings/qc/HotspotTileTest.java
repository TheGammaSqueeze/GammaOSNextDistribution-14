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
import android.net.wifi.WifiManager;
import android.os.UserManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCTile;
import com.android.car.settings.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class HotspotTileTest extends BaseSettingsQCItemTestCase {
    private HotspotTile mHotspotTile;

    @Mock
    private TetheringManager mTetheringManager;
    @Mock
    private WifiManager mWifiManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemService(TetheringManager.class)).thenReturn(mTetheringManager);
        when(mContext.getSystemService(WifiManager.class)).thenReturn(mWifiManager);

        mHotspotTile = new HotspotTile(mContext);
    }

    @Test
    public void getQCItem_titleAndIconSet() {
        when(mWifiManager.getWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_DISABLED);
        QCTile tile = getTile();
        assertThat(tile.getSubtitle()).isEqualTo(
                mContext.getString(R.string.hotspot_settings_title));
        assertThat(tile.getIcon()).isNotNull();
    }

    @Test
    public void getQCItem_hotspotDisabled_tileUnchecked() {
        when(mWifiManager.getWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_DISABLED);
        QCTile tile = getTile();
        assertThat(tile.isChecked()).isFalse();
        assertThat(tile.isEnabled()).isTrue();
        assertThat(tile.isAvailable()).isTrue();
    }

    @Test
    public void getQCItem_hotspotEnabled_tileChecked() {
        when(mWifiManager.getWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_ENABLED);
        QCTile tile = getTile();
        assertThat(tile.isChecked()).isTrue();
        assertThat(tile.isEnabled()).isTrue();
        assertThat(tile.isAvailable()).isTrue();
    }

    @Test
    public void getQCItem_hotspotBusy_tileDisabled() {
        when(mWifiManager.getWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_ENABLING);
        QCTile tile = getTile();
        assertThat(tile.isEnabled()).isFalse();
    }

    @Test
    public void getQCItem_hotspotUnsupported_tileUnavailable() {
        when(mWifiManager.getWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_DISABLED);
        mHotspotTile.setHotspotSupported(false);
        QCTile tile = getTile();
        assertThat(tile.isAvailable()).isFalse();
    }

    @Test
    public void getQCItem_hasBaseUmRestriction_tileDisabled() {
        setBaseUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING, /* restricted= */ true);
        QCTile tile = getTile();
        assertThat(tile.isEnabled()).isFalse();
        assertThat(tile.isClickableWhileDisabled()).isFalse();
    }

    @Test
    public void getQCItem_hasUmRestriction_tileClickableWhileDisabled() {
        setUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING, /* restricted= */ true);
        QCTile tile = getTile();
        assertThat(tile.isEnabled()).isFalse();
        assertThat(tile.isClickableWhileDisabled()).isTrue();
    }

    @Test
    public void onNotifyChange_togglesHotspot() {
        when(mWifiManager.getWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_DISABLED);
        Intent intent = new Intent();
        intent.putExtra(QC_ACTION_TOGGLE_STATE, true);
        mHotspotTile.onNotifyChange(intent);
        verify(mTetheringManager).startTethering(eq(ConnectivityManager.TETHERING_WIFI),
                any(Executor.class), any(TetheringManager.StartTetheringCallback.class));
    }

    @Test
    public void getQCItem_createsTile_zoneWrite() {
        mHotspotTile.setAvailabilityStatusForZone("write");
        when(mWifiManager.getWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_ENABLED);
        QCTile tile = getTile();
        assertThat(tile.isEnabled()).isTrue();
    }

    @Test
    public void getQCItem_createsTile_zoneRead() {
        mHotspotTile.setAvailabilityStatusForZone("read");
        when(mWifiManager.getWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_ENABLED);
        QCTile tile = getTile();
        assertThat(tile.isEnabled()).isFalse();
        assertThat(tile.isClickableWhileDisabled()).isTrue();
    }

    @Test
    public void getQCItem_createsTile_zoneHidden() {
        mHotspotTile.setAvailabilityStatusForZone("hidden");
        when(mWifiManager.getWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_ENABLED);
        QCItem item = mHotspotTile.getQCItem();
        assertThat(item).isNull();
    }

    private QCTile getTile() {
        QCItem item = mHotspotTile.getQCItem();
        assertThat(item).isNotNull();
        assertThat(item instanceof QCTile).isTrue();
        return (QCTile) item;
    }
}
