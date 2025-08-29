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

/** Quick settings tile: Fan **/
public class FanTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "fan";

    private static final String PROP_FAN_MODE    = "persist.gammaos.fan_mode";
    private static final String MODE_AUTO        = "auto";
    private static final String MODE_COOL        = "cool";
    private static final String MODE_MAX         = "max";
    private static final String MODE_OFF         = "off";

    private static final int STATE_AUTO          = 0;
    private static final int STATE_COOL          = 1;
    private static final int STATE_MAX           = 2;
    private static final int STATE_OFF           = 3;
    private int currentState;

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_device_fan_on);
    private final Receiver mReceiver = new Receiver();

    @Inject
    public FanTile(
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
        
        // 1) Read the persisted prop (default to auto if missing/invalid), map to our state enum
        currentState = mapPropToState(
                SystemProperties.get(PROP_FAN_MODE, MODE_AUTO)
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
                    SystemProperties.get(PROP_FAN_MODE, MODE_AUTO)
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
        // Cycle through AUTO → COOL → MAX → OFF → AUTO …
        currentState = (currentState + 1) % 4;
        applyState(currentState);
        refreshState();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        switch (currentState) {
            case STATE_AUTO:
                state.label = "Fan Auto";
                state.icon  = ResourceIcon.get(R.drawable.ic_device_fan_on);
                state.state = Tile.STATE_ACTIVE;
                break;
            case STATE_COOL:
                state.label = "Fan Cool";
                state.icon  = ResourceIcon.get(R.drawable.ic_device_fan_on);
                state.state = Tile.STATE_ACTIVE;
                break;
            case STATE_MAX:
                state.label = "Fan Max";
                state.icon  = ResourceIcon.get(R.drawable.ic_device_fan_on);
                state.state = Tile.STATE_ACTIVE;
                break;
            case STATE_OFF:
            default:
                state.label = "Fan Off";
                state.icon  = ResourceIcon.get(R.drawable.ic_device_fan_off);
                state.state = Tile.STATE_INACTIVE;
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
        return "Fan Speed";
    }

    @Override
    public int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }

    /**
     * Map the string prop ("auto"/"cool"/"max"/"off") to our integer state.
     * Invalid or missing values default to AUTO.
     */
    private int mapPropToState(String mode) {
        if (MODE_COOL.equals(mode))  return STATE_COOL;
        if (MODE_MAX.equals(mode))   return STATE_MAX;
        if (MODE_OFF.equals(mode))   return STATE_OFF;
        // default to auto on missing or invalid
        return STATE_AUTO;
    }

    /**
     * Map our integer state back to the string we store in the prop.
     */
    private String mapStateToProp(int state) {
        switch (state) {
            case STATE_COOL: return MODE_COOL;
            case STATE_MAX:  return MODE_MAX;
            case STATE_OFF:  return MODE_OFF;
            case STATE_AUTO:
            default:         return MODE_AUTO;
        }
    }

    /**
     * Write the current state into the system property.
     */
    private void applyState(int state) {
        String mode = mapStateToProp(state);
        SystemProperties.set(PROP_FAN_MODE, mode);
        if (Log.isLoggable("FanTile", Log.DEBUG)) {
            Log.d("FanTile", "Applied fan mode: " + mode);
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
                        SystemProperties.get(PROP_FAN_MODE, MODE_AUTO)
                );
                // Re-apply it just to be safe, and update UI
                applyState(currentState);
                refreshState();
            }
        }
    }
}

