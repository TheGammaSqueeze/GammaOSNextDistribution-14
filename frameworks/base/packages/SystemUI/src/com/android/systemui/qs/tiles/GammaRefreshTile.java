/*
 * GammaOS Refresh Rate Quick Settings Tile
 *
 * Toggles between 60 Hz and 120 Hz refresh configurations using internal
 * APIs where possible. Mirrors the behavior of the shell commands:
 *
 * 60 Hz:
 *   setprop persist.gammaos.refresh.lock 0
 *   settings put system default_refresh_rate 60
 *   settings put system min_refresh_rate 60
 *   settings put system peak_refresh_rate 60
 *   settings put global low_power 0
 *   settings put global match_content_frame_rate 0
 *   cmd display set-user-preferred-display-mode 0 1280 960 60
 *
 * 120 Hz:
 *   settings put system default_refresh_rate 120
 *   settings put system min_refresh_rate 120
 *   settings put system peak_refresh_rate 120
 *   settings put global low_power 0
 *   settings put global match_content_frame_rate 0
 *   cmd display set-user-preferred-display-mode 0 1280 960 120
 *   setprop persist.gammaos.refresh.lock 1
 */

package com.android.systemui.qs.tiles;

import android.content.Intent;
import static com.android.internal.logging.MetricsLogger.VIEW_UNKNOWN;

import android.content.ContentResolver;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.provider.Settings;
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

public class GammaRefreshTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "gammarefresh";

    private static final String TAG = "GammaRefreshTile";

    // Matches the property used by your RefreshRatePolicy / SurfaceFlinger patches
    private static final String PROP_REFRESH_LOCK = "persist.gammaos.refresh.lock";

    // System setting keys used by "settings put"
    // We use raw key names to avoid depending on any custom Settings.java additions.
    private static final String KEY_DEFAULT_REFRESH_RATE = "default_refresh_rate";
    private static final String KEY_MIN_REFRESH_RATE = "min_refresh_rate";
    private static final String KEY_PEAK_REFRESH_RATE = "peak_refresh_rate"; 
    private static final String KEY_MATCH_CONTENT_FRAME_RATE = "match_content_frame_rate";

    // Two discrete modes we care about
    private static final int REFRESH_60 = 60;
    private static final int REFRESH_120 = 120;

    private int mCurrentHz;

    // Reuse existing circle icons (same as GammaDualFocus / DeepSleep tiles)
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

        // Initialize from current display mode / settings
        mCurrentHz = readCurrentRefreshRateHz();
        // Do not force-apply here; just show whatever the system is using.
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
            // Re-sync from current preferred mode when QS panel is opened
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

        // Optional: content description for accessibility
        state.contentDescription = state.label + " " + state.secondaryLabel;
    }

    @Override
    public Intent getLongClickIntent() {
        // Mirror GammaDualFocusTile behavior:
        // no dedicated settings screen, so we return null and explicitly
        // handle the long-click to avoid SystemUI trying to start anything.
        return null;
    }

    @Override
    protected void handleLongClick(@Nullable View view) {
        // Explicit no-op, like GammaDualFocusTile.
        // This ensures long-press will not crash SystemUI even if the base
        // implementation changes its behavior.
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
     * Read the current effective user-preferred refresh rate in Hz, using:
     *  - DisplayManagerGlobal user preferred mode if set
     *  - Active display mode otherwise
     *  - Fallback to the GammaOS lock property if needed
     */
    private int readCurrentRefreshRateHz() {
        try {
            DisplayManager dm = mContext.getSystemService(DisplayManager.class);
            if (dm != null) {
                Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
                if (display != null) {
                    DisplayManagerGlobal global = DisplayManagerGlobal.getInstance();
                    Display.Mode preferred =
                            global.getUserPreferredDisplayMode(display.getDisplayId());
                    if (preferred == null) {
                        preferred = display.getMode();
                    }
                    if (preferred != null) {
                        int hz = Math.round(preferred.getRefreshRate());
                        if (hz >= 110) {
                            return REFRESH_120;
                        } else if (hz <= 70) {
                            return REFRESH_60;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to read current preferred display mode", t);
        }

        // Fallback to GammaOS property
        int lock = SystemProperties.getInt(PROP_REFRESH_LOCK, 0);
        return (lock == 1) ? REFRESH_120 : REFRESH_60;
    }

    /**
     * Apply the requested refresh configuration using internal APIs, preserving the ordering
     * of operations you described:
     *
     *  - For 60 Hz: setprop first, then Settings + display mode
     *  - For 120 Hz: Settings + display mode first, then setprop
     */
    private void applyRefreshConfiguration(int hz) {
        final ContentResolver resolver = mContext.getContentResolver();

        if (hz == REFRESH_60) {
            // 60 Hz: property first
            SystemProperties.set(PROP_REFRESH_LOCK, "0");

            // System settings (equivalent to "settings put system ... 60")
            putSystemIntSafely(resolver, KEY_DEFAULT_REFRESH_RATE, REFRESH_60);
            putSystemIntSafely(resolver, KEY_MIN_REFRESH_RATE, REFRESH_60);
            putSystemIntSafely(resolver, KEY_PEAK_REFRESH_RATE, REFRESH_60);

            // Global settings
            putGlobalIntSafely(resolver, Settings.Global.LOW_POWER_MODE, 0);
            putGlobalIntSafely(resolver, KEY_MATCH_CONTENT_FRAME_RATE, 0);

            // cmd display set-user-preferred-display-mode 0 <w> <h> 60
            setUserPreferredModeForDefaultDisplay(REFRESH_60);
        } else {
            // 120 Hz: Settings + display mode first
            putSystemIntSafely(resolver, KEY_DEFAULT_REFRESH_RATE, REFRESH_120);
            putSystemIntSafely(resolver, KEY_MIN_REFRESH_RATE, REFRESH_120);
            putSystemIntSafely(resolver, KEY_PEAK_REFRESH_RATE, REFRESH_120);

            putGlobalIntSafely(resolver, Settings.Global.LOW_POWER_MODE, 0);
            putGlobalIntSafely(resolver, KEY_MATCH_CONTENT_FRAME_RATE, 0);

            setUserPreferredModeForDefaultDisplay(REFRESH_120);

            // Property last
            SystemProperties.set(PROP_REFRESH_LOCK, "1");
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Applied refresh configuration: " + hz + " Hz");
        }
    }

    /**
     * Emulates:
     *   cmd display set-user-preferred-display-mode 0 1280 960 <hz>
     *
     * But instead of hardcoding 1280x960, we:
     *   - Query the primary display
     *   - Use its active mode resolution
     *   - Pick the best matching mode with the requested refresh rate
     */
    private void setUserPreferredModeForDefaultDisplay(int targetHz) {
        DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        if (dm == null) {
            return;
        }

        Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) {
            return;
        }

        DisplayManagerGlobal global = DisplayManagerGlobal.getInstance();
        Display.Mode activeMode = display.getMode();
        Display.Mode[] modes = display.getSupportedModes();
        if (modes == null || modes.length == 0 || activeMode == null) {
            return;
        }

        Display.Mode best = pickBestMode(modes, activeMode, targetHz);
        if (best == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "No matching mode found for " + targetHz + " Hz");
            }
            return;
        }

        try {
            global.setUserPreferredDisplayMode(display.getDisplayId(), best);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to set user preferred display mode", t);
        }
    }

    /**
     * Choose the best mode:
     *   - Same resolution as the active mode
     *   - Refresh rate rounded to the requested Hz
     *   - If multiple, pick the one with the highest "mode id" or just the first.
     */
    @Nullable
    private Display.Mode pickBestMode(Display.Mode[] modes,
                                      Display.Mode active,
                                      int targetHz) {
        final int targetRound = targetHz;
        final int activeWidth = active.getPhysicalWidth();
        final int activeHeight = active.getPhysicalHeight();

        Display.Mode candidate = null;

        for (Display.Mode mode : modes) {
            if (mode.getPhysicalWidth() != activeWidth
                    || mode.getPhysicalHeight() != activeHeight) {
                continue;
            }
            int modeHz = Math.round(mode.getRefreshRate());
            if (modeHz != targetRound) {
                continue;
            }

            // Prefer the first match; if needed, you can add tie-breaking using mode.getModeId().
            candidate = mode;
            break;
        }

        // Fallback: if we didn't find an exact resolution+Hz match, try any mode with that Hz
        if (candidate == null) {
            for (Display.Mode mode : modes) {
                int modeHz = Math.round(mode.getRefreshRate());
                if (modeHz == targetRound) {
                    candidate = mode;
                    break;
                }
            }
        }

        return candidate;
    }

    private void putSystemIntSafely(ContentResolver resolver, String key, int value) {
        try {
            Settings.System.putInt(resolver, key, value);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to write system setting " + key + "=" + value, t);
        }
    }

    private void putGlobalIntSafely(ContentResolver resolver, String key, int value) {
        try {
            Settings.Global.putInt(resolver, key, value);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to write global setting " + key + "=" + value, t);
        }
    }
}
