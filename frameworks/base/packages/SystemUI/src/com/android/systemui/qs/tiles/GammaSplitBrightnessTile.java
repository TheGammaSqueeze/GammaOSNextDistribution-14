/*
 * Copyright (C) 2026
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

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
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

/** Quick settings tile: GammaOS split brightness mode **/
public class GammaSplitBrightnessTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "gammasplitbrightness";

    private static final String PROP_CONTROL =
            "persist.gammaos.multidisplay.split_brightness";

    private static final int STATE_DISABLED = 0;
    private static final int STATE_ENABLED  = 1;

    private int mCurrentState;

    private final Icon mIconOn  = ResourceIcon.get(R.drawable.ic_qs_circle);
    private final Icon mIconOff = ResourceIcon.get(R.drawable.ic_add_circle);

    @Inject
    public GammaSplitBrightnessTile(
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

        // Read persisted prop (default to OFF).
        mCurrentState = SystemProperties.getInt(PROP_CONTROL, STATE_DISABLED);
        applyState(mCurrentState);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleSetListening(boolean listening) {
        super.handleSetListening(listening);
        if (listening) {
            final int newState = SystemProperties.getInt(PROP_CONTROL, STATE_DISABLED);
            if (newState != mCurrentState) {
                mCurrentState = newState;
                refreshState();
            }
        }
    }

    @Override
    protected void handleClick(@Nullable View view) {
        mCurrentState = (mCurrentState == STATE_ENABLED) ? STATE_DISABLED : STATE_ENABLED;
        applyState(mCurrentState);
        refreshState();
    }

    private void applyState(int state) {
        SystemProperties.set(PROP_CONTROL, Integer.toString(state));
        // Ensure SystemProperties change callbacks fire immediately in-process (and in other
        // processes that rely on reportSyspropChanged), so QS shade updates live.
        SystemProperties.reportSyspropChanged();
    }

    @Override
    public int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }
 
    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final boolean enabled = (mCurrentState == STATE_ENABLED);

        state.value = enabled;
        state.label = getTileLabel();
        state.icon = enabled ? mIconOn : mIconOff;
        state.state = enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
        state.contentDescription = state.label;
    }

    @Override
    public CharSequence getTileLabel() {
        return "Split Brightness";
    }
}
