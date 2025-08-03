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
import static com.android.car.settings.qc.SettingsQCRegistry.MOBILE_DATA_TILE_URI;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.UserManager;

import androidx.annotation.VisibleForTesting;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCTile;
import com.android.car.settings.R;
import com.android.car.settings.enterprise.EnterpriseUtils;
import com.android.settingslib.net.DataUsageController;

/**
 * QCItem for showing a mobile data toggle.
 */
public class MobileDataTile extends SettingsQCItem {

    private final DataUsageController mDataUsageController;

    public MobileDataTile(Context context) {
        super(context);
        setAvailabilityStatusForZone(getAvailabilityStatusForZoneFromXml(context,
                R.xml.network_and_internet_fragment, R.string.pk_mobile_network_settings_entry));
        mDataUsageController = getDataUsageController(context);
    }

    @Override
    QCItem getQCItem() {
        if (!mDataUsageController.isMobileDataSupported() || isHiddenForZone()) {
            return null;
        }
        boolean dataEnabled = mDataUsageController.isMobileDataEnabled();
        String subtitle = MobileNetworkQCUtils.getMobileNetworkSummary(getContext(), dataEnabled);
        if (subtitle == null) {
            subtitle = getContext().getString(R.string.mobile_network_toggle_title);
        }
        Icon icon = MobileNetworkQCUtils.getMobileNetworkSignalIcon(getContext());

        String userRestriction = UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS;
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
                .setChecked(dataEnabled)
                .setAction(getBroadcastIntent())
                .setSubtitle(subtitle)
                .setEnabled(!hasUmRestrictions && !hasDpmRestrictions && isWritableForZone())
                .setClickableWhileDisabled(hasDpmRestrictions | isReadOnlyForZone)
                .setDisabledClickAction(disabledPendingIntent)
                .build();
    }

    @Override
    Uri getUri() {
        return MOBILE_DATA_TILE_URI;
    }

    @Override
    void onNotifyChange(Intent intent) {
        boolean newState = intent.getBooleanExtra(QC_ACTION_TOGGLE_STATE,
                !mDataUsageController.isMobileDataEnabled());
        mDataUsageController.setMobileDataEnabled(newState);
    }

    @Override
    Class getBackgroundWorkerClass() {
        return MobileDataTileWorker.class;
    }

    @VisibleForTesting
    DataUsageController getDataUsageController(Context context) {
        return new DataUsageController(context);
    }
}
