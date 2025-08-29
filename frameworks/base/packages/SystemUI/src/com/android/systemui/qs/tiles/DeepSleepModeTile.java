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

/** Quick settings tile: Deep Sleep Mode **/
public class DeepSleepModeTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "deepsleepmode";

    private static final String PROP_CONTROL = "persist.gammaos.ultra_low_power_saving_mode";

    private static final int STATE_DISABLED = 0;
    private static final int STATE_ENABLED  = 1;

    private int currentState;

    private final Icon mIconOn  = ResourceIcon.get(R.drawable.ic_qs_circle);
    private final Icon mIconOff = ResourceIcon.get(R.drawable.ic_add_circle);
    private final Receiver mReceiver = new Receiver();

    @Inject
    public DeepSleepModeTile(
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
        currentState = SystemProperties.getInt(PROP_CONTROL, STATE_DISABLED);
        // 2) Re-apply it in case it's changed externally
        applyState(currentState);
        // 3) Listen for screen-off and boot to re-sync
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
            int newState = SystemProperties.getInt(PROP_CONTROL, STATE_DISABLED);
            if (newState != currentState) {
                currentState = newState;
                refreshState();
            }
        }
    }

    @Override
    protected void handleClick(@Nullable View view) {
        // Toggle OFF ⇄ ON
        currentState = (currentState == STATE_ENABLED) ? STATE_DISABLED : STATE_ENABLED;
        applyState(currentState);
        refreshState();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.label = "Deep Sleep Mode";
        state.icon = (currentState == STATE_ENABLED) ? mIconOn : mIconOff;
        state.secondaryLabel = (currentState == STATE_ENABLED) ? "On" : "Off";
        state.state = (currentState == STATE_ENABLED)
                ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
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
        return "Deep Sleep Mode";
    }

    @Override
    public int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }

    /**
     * Persist the current state into the system property.
     */
    private void applyState(int state) {
        SystemProperties.set(PROP_CONTROL, Integer.toString(state));
        if (Log.isLoggable("DeepSleepModeTile", Log.DEBUG)) {
            Log.d("DeepSleepModeTile", PROP_CONTROL + "=" + state);
        }
    }

    /**
     * Receiver to re-sync state on screen-off and boot.
     */
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
                int newState = SystemProperties.getInt(PROP_CONTROL, STATE_DISABLED);
                if (newState != currentState) {
                    currentState = newState;
                    applyState(currentState);
                    refreshState();
                }
            }
        }
    }
}