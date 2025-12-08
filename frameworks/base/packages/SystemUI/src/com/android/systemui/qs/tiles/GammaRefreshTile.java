/*
 * GammaOS Refresh Rate Quick Settings Tile
 *
 * Simple controller for persist.gammaos.refresh.rate:
 *   60  -> 60 Hz mode requested
 *   120 -> 120 Hz mode requested (default if unset/invalid)
 *
 * Your existing scripts / framework patches are responsible for watching
 * this property and applying the actual refresh rate configuration.
 */

package com.android.systemui.qs.tiles;

import static com.android.internal.logging.MetricsLogger.VIEW_UNKNOWN;

import android.content.Intent;
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

public class GammaRefreshTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "gammarefresh";

    private static final String TAG = "GammaRefreshTile";

    // Single source of truth controlled by this tile; your scripts/framework
    // are responsible for reacting to this property.
    private static final String PROP_REFRESH_RATE = "persist.gammaos.refresh.rate";

    // Two discrete modes we care about
    private static final int REFRESH_60 = 60;
    private static final int REFRESH_120 = 120;

    private int mCurrentHz;

    // Reuse existing circle icons (same as GammaDualFocus / other tiles)
    private final Icon mIconOn  = ResourceIcon.get(R.drawable.ic_qs_circle);
    private final Icon mIconOff = ResourceIcon.get(R.drawable.ic_add_circle);

    @Inject
    public GammaRefreshTile(
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

        // Initialize from the property; default to 120 Hz if unset/invalid.
        mCurrentHz = readCurrentRefreshRateHz();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
    }

    @Override
    protected void handleSetListening(boolean listening) {
        super.handleSetListening(listening);
        if (listening) {
            // Re-sync from property when QS panel is opened
            int newHz = readCurrentRefreshRateHz();
            if (newHz != mCurrentHz) {
                mCurrentHz = newHz;
                refreshState();
            }
        }
    }

    @Override
    protected void handleClick(@Nullable View view) {
        // Toggle between 60 Hz and 120 Hz
        mCurrentHz = (mCurrentHz == REFRESH_120) ? REFRESH_60 : REFRESH_120;
        applyRefreshConfiguration(mCurrentHz);
        refreshState();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final boolean is120 = (mCurrentHz == REFRESH_120);

        state.label = "Refresh Rate";
        state.secondaryLabel = is120 ? "120 Hz" : "60 Hz";
        state.icon = is120 ? mIconOn : mIconOff;

        state.value = is120;
        state.state = is120 ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;

        // Accessibility
        state.contentDescription = state.label + " " + state.secondaryLabel;
    }

    @Override
    public Intent getLongClickIntent() {
        // No dedicated settings screen; return null and intercept long-press.
        return null;
    }

    @Override
    protected void handleLongClick(@Nullable View view) {
        // Explicit no-op, like GammaDualFocusTile.
    }

    @Override
    public CharSequence getTileLabel() {
        return "Refresh Rate";
    }

    @Override
    public int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }

    /**
     * Read the requested refresh rate from the property.
     *
     * Default to 120 Hz if the value is missing or not clearly 60.
     */
    private int readCurrentRefreshRateHz() {
        int hz = SystemProperties.getInt(PROP_REFRESH_RATE, REFRESH_120);
        if (hz >= 110) {
            return REFRESH_120;
        } else {
            return REFRESH_60;
        }
    }

    /**
     * Apply the requested refresh configuration by writing the single property:
     *
     *  - For 60 Hz:  persist.gammaos.refresh.rate = "60"
     *  - For 120 Hz: persist.gammaos.refresh.rate = "120"
     *
     * Your existing scripts / framework patches should observe this property
     * and apply the actual mode, settings, and policy.
     */
    private void applyRefreshConfiguration(int hz) {
        String value = (hz >= 110) ? "120" : "60";
        SystemProperties.set(PROP_REFRESH_RATE, value);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Set " + PROP_REFRESH_RATE + "=" + value);
        }
    }
}
