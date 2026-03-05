/*
 * Copyright (C) 2016 The CyanogenMod Project
 * Copyright (c) 2017 The LineageOS Project
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

package com.android.systemui.qs.tiles;

import static com.android.internal.logging.MetricsLogger.VIEW_UNKNOWN;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.R;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.qs.QSTile.Icon;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.qs.tileimpl.QSTileImpl.ResourceIcon;

import javax.inject.Inject;

/** Quick settings tile: Analog Calibration — opens GamepadSettings calibration dialog **/
public class AnalogCalibrationTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "analogcalibration";

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_add_circle);

    @Inject
    public AnalogCalibrationTile(
            QSHost host,
            QsEventLogger qsEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger
    ) {
        super(host, qsEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick(@Nullable View view) {
        // Open GamepadSettings — the calibration preference click is handled there
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(new ComponentName("org.lineageos.lineageparts",
                "org.lineageos.lineageparts.input.GamepadSettings"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(":settings:fragment_args_key", "gamepad_calibration");
        mActivityStarter.postStartActivityDismissingKeyguard(intent, 0);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.label = mContext.getString(R.string.quick_settings_analog_calibration_label);
        state.icon = mIcon;
        state.state = Tile.STATE_INACTIVE;
    }

    @Override
    public Intent getLongClickIntent() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(new ComponentName("org.lineageos.lineageparts",
                "org.lineageos.lineageparts.input.GamepadSettings"));
        return intent;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_analog_calibration_label);
    }

    @Override
    public int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }
}
