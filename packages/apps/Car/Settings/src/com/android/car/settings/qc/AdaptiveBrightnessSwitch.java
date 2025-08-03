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
import static com.android.car.settings.qc.SettingsQCRegistry.ADAPTIVE_BRIGHTNESS_SWITCH_URI;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.UserManager;
import android.provider.Settings;

import androidx.annotation.VisibleForTesting;

import com.android.car.qc.QCActionItem;
import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.settings.R;
import com.android.car.settings.enterprise.EnterpriseUtils;

/**
 * QCItem for toggling adaptive brightness.
 */
public class AdaptiveBrightnessSwitch extends SettingsQCItem {
    public AdaptiveBrightnessSwitch(Context context) {
        super(context);
        setAvailabilityStatusForZone(getAvailabilityStatusForZoneFromXml(context,
                R.xml.display_settings_fragment, R.string.pk_adaptive_brightness_switch));
    }

    @Override
    QCItem getQCItem() {
        if (!supportsAdaptiveBrightness() || isHiddenForZone()) {
            return null;
        }

        String userRestriction = UserManager.DISALLOW_CONFIG_BRIGHTNESS;
        boolean hasDpmRestrictions = EnterpriseUtils.hasUserRestrictionByDpm(getContext(),
                userRestriction);
        boolean hasUmRestrictions = EnterpriseUtils.hasUserRestrictionByUm(getContext(),
                userRestriction);

        boolean isReadOnlyForZone = isReadOnlyForZone();
        PendingIntent disabledPendingIntent = isReadOnlyForZone
                ? QCUtils.getDisabledToastBroadcastIntent(getContext())
                : getActionDisabledDialogIntent(getContext(), userRestriction);

        QCActionItem actionItem = new QCActionItem.Builder(QC_TYPE_ACTION_SWITCH)
                .setChecked(isAdaptiveBrightnessEnabled())
                .setAction(getBroadcastIntent())
                .setEnabled(!hasUmRestrictions && !hasDpmRestrictions && isWritableForZone())
                .setClickableWhileDisabled(hasDpmRestrictions || isReadOnlyForZone)
                .setDisabledClickAction(disabledPendingIntent)
                .setContentDescription(getContext(), R.string.qc_display_brightness)
                .build();

        QCList.Builder listBuilder = new QCList.Builder()
                .addRow(new QCRow.Builder()
                        .setTitle(getContext().getString(R.string.auto_brightness_title))
                        .addEndItem(actionItem)
                        .build()
                );
        return listBuilder.build();
    }

    @Override
    Uri getUri() {
        return ADAPTIVE_BRIGHTNESS_SWITCH_URI;
    }

    @Override
    void onNotifyChange(Intent intent) {
        boolean newState = intent.getBooleanExtra(QC_ACTION_TOGGLE_STATE,
                !isAdaptiveBrightnessEnabled());
        Settings.System.putInt(getContext().getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                newState ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                        : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
    }

    private boolean isAdaptiveBrightnessEnabled() {
        int brightnessMode = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        return brightnessMode != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
    }

    @VisibleForTesting
    boolean supportsAdaptiveBrightness() {
        return getContext().getResources().getBoolean(R.bool.config_automatic_brightness_available);
    }
}
