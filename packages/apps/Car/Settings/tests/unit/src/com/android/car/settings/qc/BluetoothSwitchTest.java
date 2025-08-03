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
import static org.mockito.Mockito.withSettings;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.UserManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.qc.QCActionItem;
import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.settings.R;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

@RunWith(AndroidJUnit4.class)
public class BluetoothSwitchTest extends BaseSettingsQCItemTestCase {
    private BluetoothSwitch mBluetoothSwitch;
    private MockitoSession mSession;

    @Mock
    private BluetoothAdapter mBluetoothAdapter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mSession = ExtendedMockito.mockitoSession().mockStatic(
                BluetoothAdapter.class, withSettings().lenient()).startMocking();
        when(BluetoothAdapter.getDefaultAdapter()).thenReturn(mBluetoothAdapter);
        when(mBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_ON);
        mBluetoothSwitch = new BluetoothSwitch(mContext);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void getQCItem_createsRow() {
        QCItem item = mBluetoothSwitch.getQCItem();
        assertThat(item instanceof QCList).isTrue();
        QCList list = (QCList) item;
        assertThat(list.getRows().size()).isEqualTo(1);
    }

    @Test
    public void getQCItem_createsRow_zoneWrite() {
        mBluetoothSwitch.setAvailabilityStatusForZone("write");
        QCItem item = mBluetoothSwitch.getQCItem();
        assertThat(item instanceof QCList).isTrue();
        QCList list = (QCList) item;
        assertThat(list.getRows().size()).isEqualTo(1);
        QCRow row = list.getRows().get(0);
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isEnabled()).isTrue();
    }

    @Test
    public void getQCItem_createsRow_zoneRead() {
        mBluetoothSwitch.setAvailabilityStatusForZone("read");
        QCItem item = mBluetoothSwitch.getQCItem();
        assertThat(item instanceof QCList).isTrue();
        QCList list = (QCList) item;
        assertThat(list.getRows().size()).isEqualTo(1);
        QCRow row = list.getRows().get(0);
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isEnabled()).isFalse();
        assertThat(actionItem.isClickableWhileDisabled()).isTrue();
    }

    @Test
    public void getQCItem_createsRow_zoneHidden() {
        mBluetoothSwitch.setAvailabilityStatusForZone("hidden");
        QCItem item = mBluetoothSwitch.getQCItem();
        assertThat(item).isNull();
    }

    @Test
    public void getQCItem_titleSet() {
        QCList list = (QCList) mBluetoothSwitch.getQCItem();
        QCRow row = list.getRows().get(0);
        assertThat(row.getTitle()).isEqualTo(mContext.getString(R.string.bluetooth_settings_title));
    }

    @Test
    public void getQCItem_hasActionSwitch() {
        QCList list = (QCList) mBluetoothSwitch.getQCItem();
        QCRow row = list.getRows().get(0);
        assertThat(row.getEndItems().size()).isEqualTo(1);
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.getType()).isEqualTo(QCItem.QC_TYPE_ACTION_SWITCH);
    }

    @Test
    public void getQCItem_bluetoothOn_switchChecked() {
        QCList list = (QCList) mBluetoothSwitch.getQCItem();
        QCActionItem actionItem = list.getRows().get(0).getEndItems().get(0);
        assertThat(actionItem.isChecked()).isTrue();
    }

    @Test
    public void getQCItem_bluetoothOff_switchUnchecked() {
        when(mBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_OFF);
        QCList list = (QCList) mBluetoothSwitch.getQCItem();
        QCActionItem actionItem = list.getRows().get(0).getEndItems().get(0);
        assertThat(actionItem.isChecked()).isFalse();
    }

    @Test
    public void getQCItem_hasBaseUmRestriction_switchDisabled() {
        setBaseUserRestriction(UserManager.DISALLOW_CONFIG_BLUETOOTH, /* restricted= */ true);
        QCList list = (QCList) mBluetoothSwitch.getQCItem();
        QCActionItem actionItem = list.getRows().get(0).getEndItems().get(0);
        assertThat(actionItem.isEnabled()).isFalse();
        assertThat(actionItem.isClickableWhileDisabled()).isFalse();
    }

    @Test
    public void getQCItem_hasUmRestriction_switchClickableWhileDisabled() {
        setUserRestriction(UserManager.DISALLOW_CONFIG_BLUETOOTH, /* restricted= */ true);
        QCList list = (QCList) mBluetoothSwitch.getQCItem();
        QCActionItem actionItem = list.getRows().get(0).getEndItems().get(0);
        assertThat(actionItem.isEnabled()).isFalse();
        assertThat(actionItem.isClickableWhileDisabled()).isTrue();
    }

    @Test
    public void onNotifyChange_enable_enablesBluetooth() {
        when(mBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_OFF);
        Intent intent = new Intent();
        intent.putExtra(QC_ACTION_TOGGLE_STATE, true);
        mBluetoothSwitch.onNotifyChange(intent);
        verify(mBluetoothAdapter).enable();
    }

    @Test
    public void onNotifyChange_disable_disablesBluetooth() {
        when(mBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_ON);
        Intent intent = new Intent();
        intent.putExtra(QC_ACTION_TOGGLE_STATE, false);
        mBluetoothSwitch.onNotifyChange(intent);
        verify(mBluetoothAdapter).disable();
    }
}
