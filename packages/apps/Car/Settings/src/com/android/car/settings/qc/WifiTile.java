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
import static com.android.car.settings.qc.QCUtils.getActionDisabledDialogIntent;
import static com.android.car.settings.qc.QCUtils.getAvailabilityStatusForZoneFromXml;
import static com.android.car.settings.qc.SettingsQCRegistry.WIFI_TILE_URI;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.UserManager;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCTile;
import com.android.car.settings.R;
import com.android.car.settings.enterprise.EnterpriseUtils;

/**
 * QCItem for showing a wifi toggle.
 */
public class WifiTile extends SettingsQCItem {
    private final WifiManager mWifiManager;

    public WifiTile(Context context) {
        super(context);
        setAvailabilityStatusForZone(getAvailabilityStatusForZoneFromXml(context,
                R.xml.network_and_internet_fragment, R.string.pk_wifi_entry_state_switch));
        mWifiManager = context.getSystemService(WifiManager.class);
    }

    @Override
    QCItem getQCItem() {
        if (isHiddenForZone()) {
            return null;
        }
        int wifiState = mWifiManager.getWifiState();
        boolean wifiEnabled = wifiState == WifiManager.WIFI_STATE_ENABLED
                || wifiState == WifiManager.WIFI_STATE_ENABLING;
        Icon icon = Icon.createWithResource(getContext(), WifiQCUtils.getIcon(mWifiManager));

        String userRestriction = UserManager.DISALLOW_CONFIG_WIFI;
        boolean hasDpmRestrictions = EnterpriseUtils.hasUserRestrictionByDpm(getContext(),
                userRestriction);
        boolean hasUmRestrictions = EnterpriseUtils.hasUserRestrictionByUm(getContext(),
                userRestriction);

        boolean isReadOnlyForZone = isReadOnlyForZone();
        PendingIntent disabledPendingIntent = isReadOnlyForZone
                ? QCUtils.getDisabledToastBroadcastIntent(getContext())
                : getActionDisabledDialogIntent(getContext(), userRestriction);

        return new QCTile.Builder()
                .setIcon(icon)
                .setChecked(wifiEnabled)
                .setAction(getBroadcastIntent())
                .setEnabled(!WifiQCUtils.isWifiBusy(mWifiManager) && !hasUmRestrictions
                        && !hasDpmRestrictions && isWritableForZone())
                .setClickableWhileDisabled(hasDpmRestrictions | isReadOnlyForZone)
                .setDisabledClickAction(disabledPendingIntent)
                .setSubtitle(WifiQCUtils.getSubtitle(getContext(), mWifiManager))
                .build();
    }

    @Override
    Uri getUri() {
        return WIFI_TILE_URI;
    }

    @Override
    void onNotifyChange(Intent intent) {
        boolean newState = intent.getBooleanExtra(QC_ACTION_TOGGLE_STATE,
                !mWifiManager.isWifiEnabled());
        mWifiManager.setWifiEnabled(newState);
    }

    @Override
    Class getBackgroundWorkerClass() {
        return WifiTileWorker.class;
    }
}
