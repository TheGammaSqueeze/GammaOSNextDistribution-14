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
import android.hardware.display.IDisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
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

/** Quick settings tile: Secondary display toggle (GammaOS multi-display) */
public class GammaSecondaryDisplayTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "gammasecondarydisplay";

    private static final String TAG = "GammaSecondaryDisplay";
    private static final String PROP_CONTROL = "persist.gammaos.multidisplay.secondary_display";

    private static final int STATE_DISABLED = 0;
    private static final int STATE_ENABLED  = 1;

    private final Icon mIconOn  = ResourceIcon.get(R.drawable.ic_qs_circle);
    private final Icon mIconOff = ResourceIcon.get(R.drawable.ic_add_circle);
    private final Receiver mReceiver = new Receiver();

    private final int mSecondaryDisplayId;
    private final IDisplayManager mDisplayManager;

    private int mCurrentState;

    @Inject
    public GammaSecondaryDisplayTile(
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

        mSecondaryDisplayId = mContext.getResources().getInteger(
                R.integer.config_gammaos_secondary_display_id);
        mDisplayManager = IDisplayManager.Stub.asInterface(
                ServiceManager.getService(Context.DISPLAY_SERVICE));

        // 1) Read persisted prop (default to ON)
        mCurrentState = SystemProperties.getInt(PROP_CONTROL, STATE_ENABLED);
        // 2) Re-apply it in case it's changed externally
        applyState(mCurrentState);
        // 3) Listen for screen-on and boot to re-sync
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
            int newState = SystemProperties.getInt(PROP_CONTROL, STATE_ENABLED);
            if (newState != mCurrentState) {
                mCurrentState = newState;
                // Ensure the actual display state follows the persisted preference.
                applyState(mCurrentState);
                refreshState();
            }
        }
    }

    @Override
    protected void handleClick(@Nullable View view) {
        // Toggle OFF ⇄ ON
        mCurrentState = (mCurrentState == STATE_ENABLED) ? STATE_DISABLED : STATE_ENABLED;
        applyState(mCurrentState);
        refreshState();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.label = "Secondary display";

        if (!isSecondaryDisplayPresent()) {
            state.icon = mIconOff;
            state.secondaryLabel = "Unavailable";
            state.state = Tile.STATE_UNAVAILABLE;
            return;
        }

        state.icon = (mCurrentState == STATE_ENABLED) ? mIconOn : mIconOff;
        state.secondaryLabel = (mCurrentState == STATE_ENABLED) ? "On" : "Off";
        state.state = (mCurrentState == STATE_ENABLED)
                ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    protected void handleLongClick(@Nullable View view) {
        // no-op
    }

    @Override
    public CharSequence getTileLabel() {
        return "Secondary display";
    }

    @Override
    public int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }

    private boolean isSecondaryDisplayPresent() {
        if (mDisplayManager == null) return false;
        try {
            return mDisplayManager.getDisplayInfo(mSecondaryDisplayId) != null;
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to query display info", e);
            return false;
        }
    }

    /**
     * Persist the current state into the system property and update DisplayManagerService.
     */
    private void applyState(int state) {
        SystemProperties.set(PROP_CONTROL, Integer.toString(state));

        if (mDisplayManager == null) {
            Log.w(TAG, "Display service unavailable; cannot toggle secondary display");
            return;
        }

        try {
            if (state == STATE_ENABLED) {
                mDisplayManager.enableConnectedDisplay(mSecondaryDisplayId);
            } else {
                mDisplayManager.disableConnectedDisplay(mSecondaryDisplayId);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Missing permission to manage displays", e);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed toggling secondary display", e);
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Display " + mSecondaryDisplayId + " state=" + state);
        }
    }

    /** Receiver to re-sync state on screen-on and boot. */
    private final class Receiver extends BroadcastReceiver {
        void init() {
            IntentFilter filter = new IntentFilter();
            // Re-apply the user's preference after waking, in case the display stack
            // re-enables the secondary display during sleep/wake transitions.
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_BOOT_COMPLETED);
            mContext.registerReceiver(this, filter, null, mHandler);
        }

        void destroy() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)
                    || Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                int newState = SystemProperties.getInt(PROP_CONTROL, STATE_ENABLED);
                if (newState != mCurrentState) {
                    mCurrentState = newState;
                }
                applyState(mCurrentState);
                refreshState();
            }
        }
    }
}
