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
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.qs.tileimpl.QSTileImpl.ResourceIcon;
import com.android.systemui.plugins.statusbar.StatusBarStateController;

import javax.inject.Inject;

/** Quick settings tile: ABXY Swap **/
public class ABXYTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "abxy";

    private static final String PROP_CONTROL    = "persist.gammaos.abxyswap";
    private static final String MODE_ON         = "on";
    private static final String MODE_OFF        = "off";

    private static final int STATE_ENABLED      = 1;
    private static final int STATE_DISABLED     = 0;

    private int currentState;

    private final Icon mIconOn  = ResourceIcon.get(R.drawable.ic_sysbar_rotate_button_ccw_start_90);
    private final Icon mIconOff = ResourceIcon.get(R.drawable.ic_sysbar_rotate_button_ccw_start_0);
    private final Receiver mReceiver = new Receiver();

    @Inject
    public ABXYTile(
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

        // 1) Read persisted prop (default to OFF)
        currentState = mapPropToState(
                SystemProperties.get(PROP_CONTROL, MODE_OFF)
        );

        // 2) Re-apply prop (in case changed externally)
        applyState(currentState);

        // 3) Listen for screen-off to re-sync
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
            // Re-read prop when QS panel opens
            int newState = mapPropToState(
                    SystemProperties.get(PROP_CONTROL, MODE_ON)
            );
            if (newState != currentState) {
                currentState = newState;
                refreshState();
            }
        }
    }

    @Override
    protected void handleClick(@Nullable View view) {
        // Toggle enabled ↔ disabled
        currentState = (currentState == STATE_ENABLED) ? STATE_DISABLED : STATE_ENABLED;
        applyState(currentState);
        refreshState();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (currentState == STATE_ENABLED) {
            state.label = "ABXY Swap On";
            state.icon  = mIconOn;
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.label = "ABXY Swap Off";
            state.icon  = mIconOff;
            state.state = Tile.STATE_INACTIVE;
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
        return "ABXY Swap";
    }

    @Override
    public int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }

    /** Map "on"/"off" to our internal state. Defaults to ENABLED. */
    private int mapPropToState(String mode) {
        return MODE_OFF.equals(mode) ? STATE_DISABLED : STATE_ENABLED;
    }

    /** Map our state back to prop string. */
    private String mapStateToProp(int state) {
        return (state == STATE_DISABLED) ? MODE_OFF : MODE_ON;
    }

    /** Write the current state into the system property. */
    private void applyState(int state) {
        String mode = mapStateToProp(state);
        SystemProperties.set(PROP_CONTROL, mode);
        if (Log.isLoggable("ABXYTile", Log.DEBUG)) {
            Log.d("ABXYTile", "persist.gammaos.abxyswap=" + mode);
        }
    }

    /** Receiver to re-sync state on screen-off. */
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
                int newState = mapPropToState(
                        SystemProperties.get(PROP_CONTROL, MODE_ON)
                );
                if (newState != currentState) {
                    currentState = newState;
                    applyState(currentState);
                    refreshState();
                }
            }
        }
    }
}
