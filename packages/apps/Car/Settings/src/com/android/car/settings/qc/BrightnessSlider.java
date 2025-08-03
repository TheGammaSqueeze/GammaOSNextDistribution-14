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

import static com.android.car.qc.QCItem.QC_ACTION_SLIDER_VALUE;
import static com.android.car.settings.qc.QCUtils.getActionDisabledDialogIntent;
import static com.android.car.settings.qc.QCUtils.getAvailabilityStatusForZoneFromXml;
import static com.android.car.settings.qc.SettingsQCRegistry.BRIGHTNESS_SLIDER_URI;
import static com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MAX;
import static com.android.settingslib.display.BrightnessUtils.convertGammaToLinear;
import static com.android.settingslib.display.BrightnessUtils.convertLinearToGamma;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.PowerManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;

import androidx.annotation.VisibleForTesting;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.qc.QCSlider;
import com.android.car.settings.CarSettingsApplication;
import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.car.settings.enterprise.EnterpriseUtils;
import com.android.internal.display.BrightnessSynchronizer;

/**
 *  QCItem for showing a brightness slider.
 */
public class BrightnessSlider extends SettingsQCItem {
    private static final Logger LOG = new Logger(BrightnessSlider.class);
    private final int mMaximumBacklight;
    private final int mMinimumBacklight;
    private final Context mContextForUser;
    private final Context mContext;
    private final boolean mIsVisibleBackgroundUsersSupported;
    private DisplayManager mDisplayManager;

    public BrightnessSlider(Context context) {
        super(context);
        setAvailabilityStatusForZone(getAvailabilityStatusForZoneFromXml(context,
                R.xml.display_settings_fragment, R.string.pk_brightness_level));
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        mMaximumBacklight = powerManager.getMaximumScreenBrightnessSetting();
        mMinimumBacklight = powerManager.getMinimumScreenBrightnessSetting();
        mContext = context;
        mContextForUser = context
                .createContextAsUser(
                        UserHandle.of(UserHandle.myUserId()), /* flags= */ 0);
        UserManager userManager = context.getSystemService(UserManager.class);
        mIsVisibleBackgroundUsersSupported =
                userManager != null ? userManager.isVisibleBackgroundUsersSupported() : false;
        if (mIsVisibleBackgroundUsersSupported) {
            mDisplayManager = context.getSystemService(DisplayManager.class);
        }
    }

    @Override
    QCItem getQCItem() {
        if (isHiddenForZone()) {
            return null;
        }
        QCList.Builder listBuilder = new QCList.Builder()
                .addRow(getBrightnessRowBuilder().build());

        return listBuilder.build();
    }

    @Override
    Uri getUri() {
        return BRIGHTNESS_SLIDER_URI;
    }

    @Override
    void onNotifyChange(Intent intent) {
        int value = intent.getIntExtra(QC_ACTION_SLIDER_VALUE, Integer.MIN_VALUE);
        if (value == Integer.MIN_VALUE) {
            return;
        }
        int linear = convertGammaToLinear(value, mMinimumBacklight, mMaximumBacklight);

        if (mIsVisibleBackgroundUsersSupported) {
            if (mDisplayManager != null) {
                float linearFloat = BrightnessSynchronizer.brightnessIntToFloat(linear);
                mDisplayManager.setBrightness(getMyOccupantZoneDisplayId(), linearFloat);
            }
        } else {
            Settings.System.putInt(mContextForUser.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, linear);
        }
    }

    protected QCRow.Builder getBrightnessRowBuilder() {
        String userRestriction = UserManager.DISALLOW_CONFIG_BRIGHTNESS;
        boolean hasDpmRestrictions = EnterpriseUtils.hasUserRestrictionByDpm(getContext(),
                userRestriction);
        boolean hasUmRestrictions = EnterpriseUtils.hasUserRestrictionByUm(getContext(),
                userRestriction);

        boolean isReadOnlyForZone = isReadOnlyForZone();
        PendingIntent disabledPendingIntent = isReadOnlyForZone
                ? QCUtils.getDisabledToastBroadcastIntent(getContext())
                : getActionDisabledDialogIntent(getContext(), userRestriction);

        return new QCRow.Builder()
                .setTitle(getContext().getString(R.string.qc_display_brightness))
                .addSlider(new QCSlider.Builder()
                        .setMax(GAMMA_SPACE_MAX)
                        .setValue(getSeekbarValue())
                        .setInputAction(getBroadcastIntent())
                        .setEnabled(!hasUmRestrictions && !hasDpmRestrictions
                                && isWritableForZone())
                        .setClickableWhileDisabled(hasDpmRestrictions || isReadOnlyForZone)
                        .setDisabledClickAction(disabledPendingIntent)
                        .build()
                );
    }

    @VisibleForTesting
    int getSeekbarValue() {
        int gamma = GAMMA_SPACE_MAX;
        if (mIsVisibleBackgroundUsersSupported) {
            if (mDisplayManager != null) {
                float linearFloat = mDisplayManager.getBrightness(getMyOccupantZoneDisplayId());
                int linear = BrightnessSynchronizer.brightnessFloatToInt(linearFloat);
                gamma = convertLinearToGamma(linear, mMinimumBacklight, mMaximumBacklight);
            }
        } else {
            try {
                int linear = Settings.System.getInt(mContextForUser.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS);
                gamma = convertLinearToGamma(linear, mMinimumBacklight, mMaximumBacklight);
            } catch (Settings.SettingNotFoundException e) {
                LOG.w("Can't find setting for SCREEN_BRIGHTNESS.");
            }
        }
        return gamma;
    }

    private int getMyOccupantZoneDisplayId() {
        return ((CarSettingsApplication) mContext.getApplicationContext())
                .getMyOccupantZoneDisplayId();
    }
}
