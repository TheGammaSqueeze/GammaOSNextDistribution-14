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
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.plugins.statusbar.StatusBarStateController;

import javax.inject.Inject;

/** Quick settings tile: Screen Map — toggle screen mapping on/off, long-press to edit */
public class ScreenMapTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "screenmap";

    private static final String PROP_ACTIVE = "sys.gammaos.screenmap.active";
    private static final String PROP_ENABLED = "persist.gammaos.screenmap.enabled";

    private static final String TARGET_PKG = "com.gammaos.screenmapper";
    private static final String TARGET_SERVICE =
            "com.gammaos.screenmapper.ScreenMapOverlayService";

    private boolean mIsActive;

    @Inject
    public ScreenMapTile(
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
        // Read persistent state so tile stays ON across reboots
        mIsActive = "1".equals(SystemProperties.get(PROP_ENABLED, "0"));
        // Sync volatile property on tile init (e.g. after SystemUI restart)
        if (mIsActive) {
            SystemProperties.set(PROP_ACTIVE, "1");
            startOverlayService(1 /* MODE_PLAY */);
        }
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleSetListening(boolean listening) {
        super.handleSetListening(listening);
        if (listening) {
            boolean newActive = "1".equals(SystemProperties.get(PROP_ENABLED, "0"));
            if (newActive != mIsActive) {
                mIsActive = newActive;
                refreshState();
            }
        }
    }

    @Override
    protected void handleClick(@Nullable View view) {
        mIsActive = !mIsActive;
        SystemProperties.set(PROP_ACTIVE, mIsActive ? "1" : "0");
        SystemProperties.set(PROP_ENABLED, mIsActive ? "1" : "0");
        if (mIsActive) {
            startOverlayService(1 /* MODE_PLAY */);
        } else {
            stopOverlayService();
        }
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        // Return a dummy intent; we handle it ourselves in handleLongClick
        return new Intent();
    }

    @Override
    protected void handleLongClick(@Nullable View view) {
        // Enable mapping and launch the editor overlay
        if (!mIsActive) {
            mIsActive = true;
            SystemProperties.set(PROP_ACTIVE, "1");
            SystemProperties.set(PROP_ENABLED, "1");
            refreshState();
        }
        startOverlayService(0 /* MODE_EDIT */);
        // Collapse QS panel
        mActivityStarter.postStartActivityDismissingKeyguard(
                new Intent(), 0 /* delay */);
    }

    private void startOverlayService(int mode) {
        Intent intent = new Intent();
        intent.setClassName(TARGET_PKG, TARGET_SERVICE);
        intent.putExtra("mode", mode);
        try {
            mContext.startForegroundService(intent);
        } catch (Exception e) {
            try { mContext.startService(intent); } catch (Exception ignored) {}
        }
    }

    private void stopOverlayService() {
        Intent intent = new Intent();
        intent.setClassName(TARGET_PKG, TARGET_SERVICE);
        try {
            mContext.stopService(intent);
        } catch (Exception ignored) {}
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_screen_map_label);
    }

    @Override
    public int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.label = mContext.getString(R.string.quick_settings_screen_map_label);
        if (mIsActive) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_touch);
            state.state = Tile.STATE_ACTIVE;
            state.secondaryLabel = mContext.getString(R.string.quick_settings_screen_map_on);
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_touch);
            state.state = Tile.STATE_INACTIVE;
            state.secondaryLabel = mContext.getString(R.string.quick_settings_screen_map_off);
        }
    }

}
