/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.car.settings.CarSettings.Global.FORCED_DAY_NIGHT_MODE;

import static com.android.car.qc.QCItem.QC_TYPE_ACTION_TOGGLE;

import android.app.PendingIntent;
import android.car.feature.Flags;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import com.android.car.qc.QCActionItem;
import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.settings.R;

/**
 * QC Item to control the day/night theme mode.
 */
public final class ThemeToggle extends SettingsQCItem {
    static final String EXTRA_BUTTON_TYPE = "THEME_MODE_EXTRA_BUTTON_TYPE";
    static final int FORCED_SENSOR_MODE = 0;
    static final int FORCED_DAY_MODE = 1;
    static final int FORCED_NIGHT_MODE = 2;

    public ThemeToggle(Context context) {
        super(context);
    }

    @Override
    QCItem getQCItem() {
        if (!Flags.carNightGlobalSetting()) {
            return null;
        }
        QCList.Builder listBuilder = new QCList.Builder();
        listBuilder.addRow(new QCRow.Builder()
                .setTitle(getContext().getString(R.string.qc_ui_mode_title))
                .addEndItem(createThemeToggleButton(FORCED_SENSOR_MODE))
                .addEndItem(createThemeToggleButton(FORCED_DAY_MODE))
                .addEndItem(createThemeToggleButton(FORCED_NIGHT_MODE))
                .build());

        return listBuilder.build();
    }

    @Override
    Uri getUri() {
        return SettingsQCRegistry.THEME_TOGGLE_URI;
    }

    @Override
    void onNotifyChange(Intent intent) {
        int buttonType = intent.getIntExtra(EXTRA_BUTTON_TYPE, -1);
        if (buttonType == -1) return;

        if (!(buttonType == getForcedDayNightModeSetting())) {
            setForcedDayNightModeSetting(buttonType);
        }
    }

    @Override
    Class getBackgroundWorkerClass() {
        if (!Flags.carNightGlobalSetting()) {
            return null;
        }
        return ThemeToggleWorker.class;
    }

    private QCActionItem createThemeToggleButton(int mode) {
        Bundle extras = new Bundle();
        extras.putInt(EXTRA_BUTTON_TYPE, mode);
        PendingIntent action = getBroadcastIntent(extras, mode);
        boolean isSelected = getForcedDayNightModeSetting() == mode;

        return new QCActionItem.Builder(QC_TYPE_ACTION_TOGGLE)
                .setAction(action)
                .setIcon(getThemeModeIcon(mode))
                .setChecked(isSelected)
                .setClickable(!isSelected)
                .build();
    }

    private int getForcedDayNightModeSetting() {
        return Settings.Global.getInt(getContext().getContentResolver(),
                FORCED_DAY_NIGHT_MODE, FORCED_SENSOR_MODE);
    }

    private void setForcedDayNightModeSetting(int mode) {
        Settings.Global.putInt(getContext().getContentResolver(), FORCED_DAY_NIGHT_MODE, mode);
    }

    private Icon getThemeModeIcon(int mode) {
        switch(mode) {
            case FORCED_SENSOR_MODE:
                return Icon.createWithResource(getContext(), R.drawable.ic_qc_ui_mode_auto);
            case FORCED_DAY_MODE:
                return Icon.createWithResource(getContext(), R.drawable.ic_qc_ui_mode_day);
            case FORCED_NIGHT_MODE:
                return Icon.createWithResource(getContext(), R.drawable.ic_qc_ui_mode_night);
        }
        return null;
    }
}
