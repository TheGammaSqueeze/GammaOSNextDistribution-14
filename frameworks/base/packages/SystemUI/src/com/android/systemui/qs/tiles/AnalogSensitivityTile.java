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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.service.quicksettings.Tile;
import android.util.Log;
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

/** Quick settings tile: Analog Sensitivity **/
public class AnalogSensitivityTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "analogsensitivity";

    private static final String PROP_CONTROL = "persist.gammaos.analogsensitivity";

    // Sequence of sensitivity values to cycle through
    private static final int[] STATE_SEQUENCE = {0, 1, 2, 3, -3, -2, -1};
    private int currentState;

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_more_vert);
    private final Receiver mReceiver = new Receiver();

    @Inject
    public AnalogSensitivityTile(
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

        // 1) Read persisted prop (default to 0)
        currentState = SystemProperties.getInt(PROP_CONTROL, 0);
        // 2) Re-apply it in case it was changed externally
        applyState(currentState);
        // 3) Listen for screen-off to re-sync state
        mReceiver.init();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        mReceiver.destroy();
    }

    @Override
    protected void handleSetListening(boolean listening) {
        super.handleSetListening(listening);
        if (listening) {
            int newState = SystemProperties.getInt(PROP_CONTROL, 0);
            if (newState != currentState) {
                currentState = newState;
                refreshState();
            }
        }
    }

    @Override
    protected void handleClick(@Nullable View view) {
        // Find current index in sequence and advance
        int idx = 0;
        for (int i = 0; i < STATE_SEQUENCE.length; i++) {
            if (STATE_SEQUENCE[i] == currentState) {
                idx = i;
                break;
            }
        }
        int next = STATE_SEQUENCE[(idx + 1) % STATE_SEQUENCE.length];
        currentState = next;
        applyState(currentState);
        refreshState();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        // Primary label
        state.label = "Analog Sensitivity";
        state.icon = mIcon;

        // Secondary label shows the current value
        String secondary;
        switch (currentState) {
            case -3: secondary = "-50%"; break;
            case -2: secondary = "-25%"; break;
            case -1: secondary = "-10%"; break;
            case  1: secondary = "+10%"; break;
            case  2: secondary = "+25%"; break;
            case  3: secondary = "+50%"; break;
            default: secondary = "Off"; break;
        }
        state.secondaryLabel = secondary;

        state.state = (currentState == 0)
                ? Tile.STATE_INACTIVE
                : Tile.STATE_ACTIVE;
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    protected void handleLongClick(@Nullable View view) {
        // no-op: we intercept the long-press here
    }

    @Override
    public CharSequence getTileLabel() {
        return "Analog Sensitivity";
    }

    @Override
    public int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }

    /**
     * Write the current sensitivity into the system property.
     */
    private void applyState(int stateValue) {
        String value = Integer.toString(stateValue);
        SystemProperties.set(PROP_CONTROL, value);
        if (Log.isLoggable("AnalogSensitivityTile", Log.DEBUG)) {
            Log.d("AnalogSensitivityTile", PROP_CONTROL + "=" + value);
        }
    }

    /**
     * Receiver to re-sync state on screen-off.
     */
    private final class Receiver extends BroadcastReceiver {
        void init() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            mContext.registerReceiver(this, filter, null, mHandler);
        }

        void destroy() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                int newState = SystemProperties.getInt(PROP_CONTROL, 0);
                if (newState != currentState) {
                    currentState = newState;
                    applyState(currentState);
                    refreshState();
                }
            }
        }
    }
}
