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
import static com.android.car.settings.qc.SettingsQCRegistry.HOTSPOT_ROW_URI;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.TetheringManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.UserManager;

import androidx.annotation.Nullable;

import com.android.car.qc.QCActionItem;
import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.settings.R;
import com.android.car.settings.enterprise.EnterpriseUtils;
import com.android.car.settings.wifi.WifiTetherUtil;

/**
 * QCItem for showing a hotspot row element.
 * The row contains an icon, the status, and a switch to enable/disable hotspot.
 */
public class HotspotRow extends SettingsQCItem {
    private final TetheringManager mTetheringManager;
    private final WifiManager mWifiManager;
    // Assume hotspot is available until notified otherwise.
    private boolean mIsSupported = true;
    private int mConnectedDevicesCount;

    public HotspotRow(Context context) {
        super(context);
        setAvailabilityStatusForZone(getAvailabilityStatusForZoneFromXml(context,
                R.xml.network_and_internet_fragment, R.string.pk_wifi_tether_settings_entry));
        mTetheringManager = context.getSystemService(TetheringManager.class);
        mWifiManager = context.getSystemService(WifiManager.class);
    }

    @Override
    QCItem getQCItem() {
        if (isHiddenForZone()) {
            return null;
        }
        Icon icon = Icon.createWithResource(getContext(), R.drawable.ic_qc_hotspot);

        String userRestriction = UserManager.DISALLOW_CONFIG_TETHERING;
        boolean hasDpmRestrictions = EnterpriseUtils.hasUserRestrictionByDpm(getContext(),
                userRestriction);
        boolean hasUmRestrictions = EnterpriseUtils.hasUserRestrictionByUm(getContext(),
                userRestriction);

        boolean isReadOnlyForZone = isReadOnlyForZone();
        PendingIntent disabledPendingIntent = isReadOnlyForZone
                ? QCUtils.getDisabledToastBroadcastIntent(getContext())
                : getActionDisabledDialogIntent(getContext(), userRestriction);

        QCActionItem hotpotToggle = new QCActionItem.Builder(QC_TYPE_ACTION_SWITCH)
                .setChecked(HotspotQCUtils.isHotspotEnabled(mWifiManager))
                .setEnabled(!HotspotQCUtils.isHotspotBusy(mWifiManager) && !hasUmRestrictions
                        && !hasDpmRestrictions && isWritableForZone())
                .setAvailable(mIsSupported)
                .setAction(getBroadcastIntent())
                .setClickableWhileDisabled(hasDpmRestrictions || isReadOnlyForZone)
                .setDisabledClickAction(disabledPendingIntent)
                .setContentDescription(getContext(), R.string.hotspot_settings_title)
                .build();

        QCRow hotspotRow = new QCRow.Builder()
                .setIcon(icon)
                .setTitle(getContext().getString(R.string.hotspot_settings_title))
                .setSubtitle(getSubtitle())
                .addEndItem(hotpotToggle)
                .setPrimaryAction(getPrimaryAction())
                .build();

        return new QCList.Builder()
                .addRow(hotspotRow)
                .build();
    }

    @Override
    Uri getUri() {
        return HOTSPOT_ROW_URI;
    }

    boolean getHotspotSupported() {
        return mIsSupported;
    }

    void setHotspotSupported(boolean supported) {
        mIsSupported = supported;
    }

    void setConnectedDevicesCount(int devicesCount) {
        mConnectedDevicesCount = devicesCount;
    }

    @Override
    void onNotifyChange(Intent intent) {
        boolean newState = intent.getBooleanExtra(QC_ACTION_TOGGLE_STATE,
                !mWifiManager.isWifiApEnabled());
        if (newState) {
            WifiTetherUtil.startTethering(mTetheringManager,
                    HotspotQCUtils.getDefaultStartTetheringCallback(getContext(), getUri()));
        } else {
            WifiTetherUtil.stopTethering(mTetheringManager);
        }
    }

    @Override
    Class getBackgroundWorkerClass() {
        return HotspotRowWorker.class;
    }

    private String getSubtitle() {
        // Early exit in case Wi-Fi is not ready, otherwise it may cause an ANR.
        if (!HotspotQCUtils.isHotspotEnabled(mWifiManager)) {
            return getContext().getString(R.string.wifi_hotspot_state_off);
        }
        return WifiTetherUtil.getHotspotSubtitle(getContext(),
                mWifiManager.getSoftApConfiguration(),
                HotspotQCUtils.isHotspotEnabled(mWifiManager), mConnectedDevicesCount);
    }

    @Nullable
    protected PendingIntent getPrimaryAction() {
        return null;
    }
}
