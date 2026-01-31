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

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.IDisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.service.quicksettings.Tile;
import android.util.Log;
import android.view.Display;
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

/**
 * Quick settings tile: Secondary display toggle (GammaOS multi-display)
 *
 * Requirements:
 *  - Never apply display state on load, on listen, on boot, or on wake.
 *  - No persistence: after reboot, assume both displays should be on.
 *  - Only toggle when user taps the tile.
 *  - No properties; keep state in memory only.
 */
public class GammaSecondaryDisplayTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "gammasecondarydisplay";

    private static final String TAG = "GammaSecondaryDisplay";

    private final Icon mIconOn = ResourceIcon.get(R.drawable.ic_qs_circle);
    private final Icon mIconOff = ResourceIcon.get(R.drawable.ic_add_circle);

    private final int mSecondaryDisplayId;
    private final IDisplayManager mDisplayManager;

    /**
     * In-memory state only.
     * Default is ON (both displays on after boot).
     *
     * IMPORTANT: This state is not applied automatically. It is only used to drive the QS UI
     * and to decide what to do when the user taps the tile.
     */
    private boolean mEnabled = true;

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

        // DO NOT apply any display state here.
        // No receivers, no persistence, no re-sync on wake.
        // The tile only does anything when the user taps it.
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        // No receivers/listeners registered, nothing to tear down.
    }

    @Override
    protected void handleSetListening(boolean listening) {
        super.handleSetListening(listening);
        // DO NOT apply any display state here.
        // Only update UI when QS is shown/hidden.
        if (listening) {
            refreshState();
        }
    }

    @Override
    protected void handleClick(@Nullable View view) {
        if (!isSecondaryDisplayPresent()) {
            // Nothing to toggle.
            refreshState();
            return;
        }

        // Toggle requested state (in-memory).
        mEnabled = !mEnabled;

        // Apply ONLY due to explicit user interaction.
        applyStateForUserTap(mEnabled);

        // Update UI.
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

        state.icon = mEnabled ? mIconOn : mIconOff;
        state.secondaryLabel = mEnabled ? "On" : "Off";
        state.state = mEnabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
    }

    @Override
    public android.content.Intent getLongClickIntent() {
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
     * Apply display state as a direct result of a user tapping the tile.
     * No persistence and no automatic re-application anywhere else.
     */
    private void applyStateForUserTap(boolean enable) {
        if (mDisplayManager == null) {
            Log.w(TAG, "Display service unavailable; cannot toggle secondary display");
            return;
        }

        try {
            if (enable) {
                mDisplayManager.enableConnectedDisplay(mSecondaryDisplayId);
            } else {
                mDisplayManager.disableConnectedDisplay(mSecondaryDisplayId);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Missing permission to manage displays", e);
            // Revert the in-memory state so UI stays truthful if we couldn't apply.
            mEnabled = !enable;
        } catch (RemoteException e) {
            Log.w(TAG, "Failed toggling secondary display", e);
            // Revert the in-memory state so UI stays truthful if we couldn't apply.
            mEnabled = !enable;
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "User toggle: display " + mSecondaryDisplayId + " enable=" + enable);
        }
    }
}
