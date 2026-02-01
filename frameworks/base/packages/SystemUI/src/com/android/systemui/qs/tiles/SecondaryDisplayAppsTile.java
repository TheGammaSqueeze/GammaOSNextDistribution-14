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

/** Quick settings tile: GammaOS secondary-display app routing */
public class SecondaryDisplayAppsTile extends QSTileImpl<BooleanState> {

    // Target activity for both long-press and chevron/side-action.
    private static final String TARGET_PKG = "com.gammaos.secondarydisplaycontrol";
    private static final String TARGET_CLS = "com.gammaos.secondarydisplaycontrol.MainActivity";

    public static final String TILE_SPEC = "secondarydisplayapps";

    private static final String PROP_CONTROL = "persist.gammaos.secondary_display.enabled";
    private static final String MODE_ON  = "1";
    private static final String MODE_OFF = "0";

    private static final int STATE_ENABLED  = 1;
    private static final int STATE_DISABLED = 0;

    private int mCurrentState;

    private final Icon mIconOn  = ResourceIcon.get(R.drawable.ic_cast_connected);
    private final Icon mIconOff = ResourceIcon.get(R.drawable.ic_cast);
    private final Receiver mReceiver = new Receiver();

    @Inject
    public SecondaryDisplayAppsTile(
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

        // 1) Read the persisted prop (default to OFF if missing/invalid), map to our state.
        mCurrentState = mapPropToState(SystemProperties.get(PROP_CONTROL, MODE_OFF));

        // 2) Re-apply it (in case it's been changed externally between boots).
        applyState(mCurrentState);

        // 3) Listen for screen-off and boot so we can re-sync.
        mReceiver.init();
    }

    @Override
    public BooleanState newTileState() {
        // Single-target tile (no chevron / dual-target)
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
            int newState = mapPropToState(SystemProperties.get(PROP_CONTROL, MODE_OFF));
            if (newState != mCurrentState) {
                mCurrentState = newState;
                refreshState();
            }
        }
    }

    @Override
    protected void handleClick(@Nullable View view) {
        // Toggle between ENABLED ⇄ DISABLED.
        mCurrentState = (mCurrentState == STATE_ENABLED) ? STATE_DISABLED : STATE_ENABLED;
        applyState(mCurrentState);
        refreshState();
    }

    private Intent buildLaunchIntent() {
        return new Intent().setClassName(TARGET_PKG, TARGET_CLS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    @Override
    public Intent getLongClickIntent() {
        return buildLaunchIntent();
    }

    @Override
    protected void handleLongClick(@Nullable View view) {
        final Intent intent = getLongClickIntent();
        mActivityStarter.postStartActivityDismissingKeyguard(intent, 0 /* delay */);
    }

    @Override
    public CharSequence getTileLabel() {
        return "Secondary display apps";
    }

    @Override
    public int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }

    private int mapPropToState(String mode) {
        if (MODE_ON.equals(mode) || "true".equalsIgnoreCase(mode)) {
            return STATE_ENABLED;
        }
        return STATE_DISABLED;
    }

    private String mapStateToProp(int state) {
        return (state == STATE_ENABLED) ? MODE_ON : MODE_OFF;
    }

    private void applyState(int state) {
        String mode = mapStateToProp(state);
        SystemProperties.set(PROP_CONTROL, mode);
        if (Log.isLoggable("SecondaryDisplayAppsTile", Log.DEBUG)) {
            Log.d("SecondaryDisplayAppsTile", "Applied secondary-display routing: " + mode);
        }
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.label = "Secondary apps";
        state.icon = (mCurrentState == STATE_ENABLED) ? mIconOn : mIconOff;
        state.state = (mCurrentState == STATE_ENABLED)
                ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
        state.secondaryLabel = (mCurrentState == STATE_ENABLED) ? "On" : "Off";
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
                mCurrentState = mapPropToState(SystemProperties.get(PROP_CONTROL, MODE_OFF));
                applyState(mCurrentState);
                refreshState();
            }
        }
    }
}
