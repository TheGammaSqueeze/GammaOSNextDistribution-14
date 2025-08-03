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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.os.UserManager;
import android.telephony.TelephonyManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCTile;
import com.android.car.settings.R;
import com.android.settingslib.net.DataUsageController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class MobileDataTileTest extends BaseSettingsQCItemTestCase {
    private static final String TEST_NETWORK_NAME = "TEST_NETWORK";

    private MobileDataTile mMobileDataTile;

    @Mock
    private DataUsageController mDataUsageController;
    @Mock
    private TelephonyManager mTelephonyManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemService(TelephonyManager.class)).thenReturn(mTelephonyManager);
        mMobileDataTile = new TestMobileDataTile(mContext);
    }

    @Test
    public void getQCItem_mobileDataUnsupported_returnsNull() {
        when(mDataUsageController.isMobileDataSupported()).thenReturn(false);
        QCItem item = mMobileDataTile.getQCItem();
        assertThat(item).isNull();
    }

    @Test
    public void getQCItem_mobileDataDisabled_tileUnchecked() {
        when(mDataUsageController.isMobileDataSupported()).thenReturn(true);
        when(mTelephonyManager.getNetworkOperatorName()).thenReturn(TEST_NETWORK_NAME);
        when(mDataUsageController.isMobileDataEnabled()).thenReturn(false);
        QCTile tile = getTile();
        assertThat(tile.isChecked()).isFalse();
    }

    @Test
    public void getQCItem_mobileDataEnabled_tileChecked() {
        when(mDataUsageController.isMobileDataSupported()).thenReturn(true);
        when(mTelephonyManager.getNetworkOperatorName()).thenReturn(TEST_NETWORK_NAME);
        when(mDataUsageController.isMobileDataEnabled()).thenReturn(true);
        QCTile tile = getTile();
        assertThat(tile.isChecked()).isTrue();
    }

    @Test
    public void getQCItem_noNetworkName_setsDefaultSubtitle() {
        when(mDataUsageController.isMobileDataSupported()).thenReturn(true);
        when(mTelephonyManager.getNetworkOperatorName()).thenReturn("");
        when(mDataUsageController.isMobileDataEnabled()).thenReturn(true);
        QCTile tile = getTile();
        assertThat(tile.getSubtitle()).isEqualTo(
                mContext.getString(R.string.mobile_network_toggle_title));
    }

    @Test
    public void getQCItem_hasNetworkName_setsSubtitle() {
        when(mDataUsageController.isMobileDataSupported()).thenReturn(true);
        when(mTelephonyManager.getNetworkOperatorName()).thenReturn(TEST_NETWORK_NAME);
        when(mDataUsageController.isMobileDataEnabled()).thenReturn(true);
        QCTile tile = getTile();
        assertThat(tile.getSubtitle()).isEqualTo(TEST_NETWORK_NAME);
    }

    @Test
    public void getQCItem_dataDisabled_setsSubtitle() {
        when(mDataUsageController.isMobileDataSupported()).thenReturn(true);
        when(mTelephonyManager.getNetworkOperatorName()).thenReturn(TEST_NETWORK_NAME);
        when(mDataUsageController.isMobileDataEnabled()).thenReturn(false);
        QCTile tile = getTile();
        assertThat(tile.getSubtitle()).isEqualTo(
                mContext.getString(R.string.mobile_network_state_off));
    }

    @Test
    public void getQCItem_hasBaseUmRestriction_tileDisabled() {
        when(mDataUsageController.isMobileDataSupported()).thenReturn(true);
        when(mTelephonyManager.getNetworkOperatorName()).thenReturn(TEST_NETWORK_NAME);
        when(mDataUsageController.isMobileDataEnabled()).thenReturn(true);
        setBaseUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS, /* restricted= */ true);
        QCTile tile = getTile();
        assertThat(tile.isEnabled()).isFalse();
        assertThat(tile.isClickableWhileDisabled()).isFalse();
    }

    @Test
    public void getQCItem_hasUmRestriction_tileClickableWhileDisabled() {
        when(mDataUsageController.isMobileDataSupported()).thenReturn(true);
        when(mTelephonyManager.getNetworkOperatorName()).thenReturn(TEST_NETWORK_NAME);
        when(mDataUsageController.isMobileDataEnabled()).thenReturn(true);
        setUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS, /* restricted= */ true);
        QCTile tile = getTile();
        assertThat(tile.isEnabled()).isFalse();
        assertThat(tile.isClickableWhileDisabled()).isTrue();
    }

    @Test
    public void onNotifyChange_togglesMobileData() {
        when(mDataUsageController.isMobileDataSupported()).thenReturn(true);
        when(mTelephonyManager.getNetworkOperatorName()).thenReturn(TEST_NETWORK_NAME);
        when(mDataUsageController.isMobileDataEnabled()).thenReturn(false);
        Intent intent = new Intent();
        intent.putExtra(QC_ACTION_TOGGLE_STATE, true);
        mMobileDataTile.onNotifyChange(intent);
        verify(mDataUsageController).setMobileDataEnabled(true);
    }

    @Test
    public void getQCItem_createsTile_zoneWrite() {
        when(mDataUsageController.isMobileDataSupported()).thenReturn(true);
        mMobileDataTile.setAvailabilityStatusForZone("write");
        QCTile tile = getTile();
        assertThat(tile.isEnabled()).isTrue();
    }

    @Test
    public void getQCItem_createsTile_zoneRead() {
        when(mDataUsageController.isMobileDataSupported()).thenReturn(true);
        mMobileDataTile.setAvailabilityStatusForZone("read");
        QCTile tile = getTile();
        assertThat(tile.isEnabled()).isFalse();
        assertThat(tile.isClickableWhileDisabled()).isTrue();
    }

    @Test
    public void getQCItem_createsTile_zoneHidden() {
        when(mDataUsageController.isMobileDataSupported()).thenReturn(true);
        mMobileDataTile.setAvailabilityStatusForZone("hidden");
        QCItem item = mMobileDataTile.getQCItem();
        assertThat(item).isNull();
    }

    private QCTile getTile() {
        QCItem item = mMobileDataTile.getQCItem();
        assertThat(item instanceof QCTile).isTrue();
        return (QCTile) item;
    }

    private class TestMobileDataTile extends MobileDataTile {

        TestMobileDataTile(Context context) {
            super(context);
        }

        @Override
        DataUsageController getDataUsageController(Context context) {
            return mDataUsageController;
        }
    }
}
