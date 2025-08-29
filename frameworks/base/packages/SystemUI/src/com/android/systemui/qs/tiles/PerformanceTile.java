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
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;

import javax.inject.Inject;

/** Quick settings tile: Performance **/
public class PerformanceTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "performance";

    private static final String PROP_PERF_MODE    = "persist.gammaos.performance_mode";
    private static final String MODE_STOCK        = "stock";
    private static final String MODE_POWERSAVE    = "powersave";
    private static final String MODE_MAX          = "max";

    private static final int STATE_STOCK         = 0;
    private static final int STATE_POWERSAVE     = 1;
    private static final int STATE_MAX           = 2;
    private int currentState;

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_device_thermostat_on);
    private final Receiver mReceiver = new Receiver();

    @Inject
    public PerformanceTile(
            QSHost host,
            QsEventLogger qsEventLogger,
            @Background Looper bgLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger
    ) {
        super(host, qsEventLogger, bgLooper, mainHandler, falsingManager, metricsLogger,
              statusBarStateController, activityStarter, qsLogger);

        // 1) Read the persisted prop (default to max if missing), map to our state enum
        currentState = mapPropToState(
                SystemProperties.get(PROP_PERF_MODE, MODE_MAX)
        );

        // 2) Re-apply it (in case it's been changed externally between boots)
        applyState(currentState);

        // 3) Listen for screen-off and boot so we can re-sync
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
            // Re-read the prop when QS panel is opened
            int newState = mapPropToState(
                    SystemProperties.get(PROP_PERF_MODE, MODE_MAX)
            );
            // If it changed externally, update and refresh tile
            if (newState != currentState) {
                currentState = newState;
                refreshState();
            }
        }
    }

    @Override
    protected void handleClick(@Nullable View view) {
        // Cycle through STOCK → POWERSAVE → MAX → STOCK …
        currentState = (currentState + 1) % 3;
        applyState(currentState);
        refreshState();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        switch (currentState) {
            case STATE_STOCK:
                state.label = "Stock Performance Mode";
                state.icon  = ResourceIcon.get(R.drawable.ic_qs_minus);
                state.state = Tile.STATE_ACTIVE;
                break;
            case STATE_POWERSAVE:
                state.label = "Power Saving Mode";
                state.icon  = ResourceIcon.get(R.drawable.ic_power_low);
                state.state = Tile.STATE_ACTIVE;
                break;
            case STATE_MAX:
            default:
                state.label = "Max Performance Mode";
                state.icon  = ResourceIcon.get(R.drawable.ic_device_thermostat_on);
                state.state = Tile.STATE_ACTIVE;
                break;
        }
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
        return "Performance Mode";
    }

    @Override
    public int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }

    /**
     * Map the string prop ("stock"/"powersave"/"max") to our integer state.
     * Invalid or missing values default to MAX.
     */
    private int mapPropToState(String mode) {
        if (MODE_POWERSAVE.equals(mode)) return STATE_POWERSAVE;
        if (MODE_MAX.equals(mode))       return STATE_MAX;
        if (MODE_STOCK.equals(mode))     return STATE_STOCK;
        // default to max on invalid
        return STATE_MAX;
    }

    /**
     * Map our integer state back to the string we store in the prop.
     */
    private String mapStateToProp(int state) {
        switch (state) {
            case STATE_POWERSAVE: return MODE_POWERSAVE;
            case STATE_MAX:       return MODE_MAX;
            case STATE_STOCK:
            default:              return MODE_STOCK;
        }
    }

    /**
     * Write the current state into the system property.
     */
    private void applyState(int state) {
        String mode = mapStateToProp(state);
        SystemProperties.set(PROP_PERF_MODE, mode);
        if (Log.isLoggable("PerformanceTile", Log.DEBUG)) {
            Log.d("PerformanceTile", "Applied performance mode: " + mode);
        }
    }

    /** Receiver to re-sync on screen-off and boot. */
    private final class Receiver extends BroadcastReceiver {
        void init() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_BOOT_COMPLETED);
            mContext.registerReceiver(this, filter, null, mHandler);
        }

        void destroy() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)
             || Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                // Re-read the prop in case it was changed elsewhere
                currentState = mapPropToState(
                        SystemProperties.get(PROP_PERF_MODE, MODE_MAX)
                );
                // Re-apply it just to be safe, and update UI
                applyState(currentState);
                refreshState();
            }
        }
    }
}
