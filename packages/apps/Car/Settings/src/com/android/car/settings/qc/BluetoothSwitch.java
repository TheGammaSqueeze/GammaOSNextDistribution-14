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
import static com.android.car.qc.QCItem.QC_TYPE_ACTION_SWITCH;
import static com.android.car.settings.qc.QCUtils.getActionDisabledDialogIntent;
import static com.android.car.settings.qc.QCUtils.getAvailabilityStatusForZoneFromXml;

import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.UserManager;

import com.android.car.qc.QCActionItem;
import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.settings.R;
import com.android.car.settings.enterprise.EnterpriseUtils;

/**
 * Bluetooth Switch QCItem.
 */
public class BluetoothSwitch extends SettingsQCItem {

    public BluetoothSwitch(Context context) {
        super(context);
        setAvailabilityStatusForZone(getAvailabilityStatusForZoneFromXml(context,
                R.xml.bluetooth_settings_fragment, R.string.pk_bluetooth_state_switch));
    }

    @Override
    QCItem getQCItem() {
        if (isHiddenForZone()) {
            return null;
        }
        String userRestriction = UserManager.DISALLOW_CONFIG_BLUETOOTH;
        boolean hasDpmRestrictions = EnterpriseUtils.hasUserRestrictionByDpm(getContext(),
                userRestriction);
        boolean hasUmRestrictions = EnterpriseUtils.hasUserRestrictionByUm(getContext(),
                userRestriction);

        boolean isReadOnlyForZone = isReadOnlyForZone();
        PendingIntent disabledPendingIntent = isReadOnlyForZone
                ? QCUtils.getDisabledToastBroadcastIntent(getContext())
                : getActionDisabledDialogIntent(getContext(), userRestriction);

        QCActionItem actionItem = new QCActionItem.Builder(QC_TYPE_ACTION_SWITCH)
                .setChecked(isBluetoothOn())
                .setAction(getBroadcastIntent())
                .setEnabled(!hasUmRestrictions && !hasDpmRestrictions && isWritableForZone())
                .setClickableWhileDisabled(hasDpmRestrictions || isReadOnlyForZone)
                .setDisabledClickAction(disabledPendingIntent)
                .setContentDescription(getContext(), R.string.bluetooth_settings_title)
                .build();

        QCList.Builder listBuilder = new QCList.Builder()
                .addRow(new QCRow.Builder()
                        .setTitle(getContext().getString(R.string.bluetooth_settings_title))
                        .addEndItem(actionItem)
                        .build()
                );
        return listBuilder.build();
    }

    @Override
    Uri getUri() {
        return SettingsQCRegistry.BLUETOOTH_SWITCH_URI;
    }

    @Override
    void onNotifyChange(Intent intent) {
        boolean newState = intent.getBooleanExtra(QC_ACTION_TOGGLE_STATE, !isBluetoothOn());
        if (newState) {
            BluetoothAdapter.getDefaultAdapter().enable();
        } else {
            BluetoothAdapter.getDefaultAdapter().disable();
        }
    }

    @Override
    Class getBackgroundWorkerClass() {
        return BluetoothSwitchWorker.class;
    }

    private boolean isBluetoothOn() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter.getState() == BluetoothAdapter.STATE_ON
                || adapter.getState() == BluetoothAdapter.STATE_TURNING_ON;
    }
}
