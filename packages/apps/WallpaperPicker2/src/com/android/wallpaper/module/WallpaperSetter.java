package com.android.wallpaper.module;

import static android.stats.style.StyleEnums.SET_WALLPAPER_ENTRY_POINT_RESTORE;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle.Event;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.model.LiveWallpaperInfo;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.WallpaperPersister.Destination;
import com.android.wallpaper.module.WallpaperPersister.SetWallpaperCallback;
import com.android.wallpaper.module.logging.UserEventLogger;
import com.android.wallpaper.module.logging.UserEventLogger.SetWallpaperEntryPoint;
import com.android.wallpaper.picker.SetWallpaperDialogFragment;
import com.android.wallpaper.picker.SetWallpaperDialogFragment.Listener;

import com.bumptech.glide.Glide;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Helper class used to set the current wallpaper. It handles showing the destination request dialog
 * and actually setting the wallpaper on a given destination.
 * It is expected to be instantiated within a Fragment or Activity, and {@link #cleanUp()} should
 * be called from its owner's onDestroy method (or equivalent).
 */
public class WallpaperSetter {

    private static final String TAG = "WallpaperSetter";
    private static final String PROGRESS_DIALOG_NO_TITLE = null;
    private static final boolean PROGRESS_DIALOG_INDETERMINATE = true;

    private static final int UNUSED_REQUEST_CODE = 1;
    private static final String TAG_SET_WALLPAPER_DIALOG_FRAGMENT = "set_wallpaper_dialog";

    private final WallpaperPersister mWallpaperPersister;
    private final WallpaperPreferences mPreferences;
    private final UserEventLogger mUserEventLogger;
    private final CurrentWallpaperInfoFactory mCurrentWallpaperInfoFactory;
    private ProgressDialog mProgressDialog;
    private Optional<Integer> mCurrentScreenOrientation = Optional.empty();

    public WallpaperSetter(WallpaperPersister wallpaperPersister,
            WallpaperPreferences preferences, UserEventLogger userEventLogger,
            CurrentWallpaperInfoFactory currentWallpaperInfoFactory) {
        mWallpaperPersister = wallpaperPersister;
        mPreferences = preferences;
        mUserEventLogger = userEventLogger;
        mCurrentWallpaperInfoFactory = currentWallpaperInfoFactory;
    }

    /**
     * Sets current wallpaper to the device based on current zoom and scroll state.
     *
     * @param containerActivity main Activity that owns the current fragment
     * @param wallpaper         Info for the actual wallpaper to set
     * @param wallpaperAsset    Wallpaper asset from which to retrieve image data.
     * @param setWallpaperEntryPoint The entry point where the wallpaper is set.
     * @param destination       The wallpaper destination i.e. home vs. lockscreen vs. both.
     * @param wallpaperScale    Scaling factor applied to the source image before setting the
     *                          wallpaper to the device.
     * @param cropRect          Desired crop area of the wallpaper in post-scale units. If null,
     *                          then the
     *                          wallpaper image will be set without any scaling or cropping.
     * @param callback          Optional callback to be notified when the wallpaper is set.
     */
    public void setCurrentWallpaper(Activity containerActivity, WallpaperInfo wallpaper,
            @Nullable Asset wallpaperAsset, @SetWallpaperEntryPoint int setWallpaperEntryPoint,
            @Destination final int destination, float wallpaperScale, @Nullable Rect cropRect,
            WallpaperColors wallpaperColors, @Nullable SetWallpaperCallback callback) {
        if (wallpaper instanceof LiveWallpaperInfo) {
            setCurrentLiveWallpaper(containerActivity, (LiveWallpaperInfo) wallpaper,
                    setWallpaperEntryPoint, destination, wallpaperColors, callback);
            return;
        }
        mPreferences.setPendingWallpaperSetStatus(
                WallpaperPreferences.WALLPAPER_SET_PENDING);

        // Save current screen rotation so we can temporarily disable rotation while setting the
        // wallpaper and restore after setting the wallpaper finishes.
        saveAndLockScreenOrientationIfNeeded(containerActivity);

        // Clear MosaicView tiles and Glide's cache and pools to reclaim memory for final cropped
        // bitmap.
        Glide.get(containerActivity).clearMemory();

        // ProgressDialog endlessly updates the UI thread, keeping it from going idle which
        // therefore causes Espresso to hang once the dialog is shown.
        if (!containerActivity.isFinishing()) {
            int themeResId = (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP)
                    ? R.style.ProgressDialogThemePreL : R.style.LightDialogTheme;
            mProgressDialog = new ProgressDialog(containerActivity, themeResId);

            mProgressDialog.setTitle(PROGRESS_DIALOG_NO_TITLE);
            mProgressDialog.setMessage(containerActivity.getString(
                    R.string.set_wallpaper_progress_message));
            mProgressDialog.setIndeterminate(PROGRESS_DIALOG_INDETERMINATE);
            if (containerActivity instanceof LifecycleOwner) {
                ((LifecycleOwner) containerActivity).getLifecycle().addObserver(
                        new LifecycleEventObserver() {
                            @Override
                            public void onStateChanged(@NonNull LifecycleOwner source,
                                    @NonNull Event event) {
                                if (event == Event.ON_DESTROY) {
                                    if (mProgressDialog != null) {
                                        mProgressDialog.dismiss();
                                        mProgressDialog = null;
                                    }
                                }
                            }
                        });
            }
            mProgressDialog.show();
        }

        mWallpaperPersister.setIndividualWallpaper(
                wallpaper, wallpaperAsset, cropRect,
                wallpaperScale, destination, new SetWallpaperCallback() {
                    @Override
                    public void onSuccess(WallpaperInfo wallpaperInfo,
                            @Destination int destination) {
                        onWallpaperApplied(containerActivity, wallpaper, setWallpaperEntryPoint,
                                destination);
                        if (callback != null) {
                            callback.onSuccess(wallpaper, destination);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        onWallpaperApplyError(containerActivity);
                        if (callback != null) {
                            callback.onError(throwable);
                        }
                    }
                });
        mCurrentWallpaperInfoFactory.clearCurrentWallpaperInfos();
    }

    private void setCurrentLiveWallpaper(Activity activity, LiveWallpaperInfo wallpaper,
            @SetWallpaperEntryPoint int setWallpaperEntryPoint, @Destination final int destination,
            WallpaperColors colors, @Nullable SetWallpaperCallback callback) {
        try {
            // Save current screen rotation so we can temporarily disable rotation while setting the
            // wallpaper and restore after setting the wallpaper finishes.
            saveAndLockScreenOrientationIfNeeded(activity);

            WallpaperManager wallpaperManager = WallpaperManager.getInstance(activity);
            LiveWallpaperInfo updatedWallpaperInfo = wallpaper.saveWallpaper(
                    activity.getApplicationContext(), destination);
            if (updatedWallpaperInfo != null) {
                wallpaper = updatedWallpaperInfo;
            }
            setWallpaperComponent(wallpaperManager, wallpaper, destination);
            wallpaperManager.setWallpaperOffsetSteps(0.5f /* xStep */, 0.0f /* yStep */);
            wallpaperManager.setWallpaperOffsets(
                    activity.getWindow().getDecorView().getRootView().getWindowToken(),
                    0.5f /* xOffset */, 0.0f /* yOffset */);
            mPreferences.storeLatestWallpaper(WallpaperPersister.destinationToFlags(destination),
                    wallpaper.getWallpaperId(), wallpaper, colors);
            mCurrentWallpaperInfoFactory.clearCurrentWallpaperInfos();
            onWallpaperApplied(activity, wallpaper, setWallpaperEntryPoint, destination);
            if (callback != null) {
                callback.onSuccess(wallpaper, destination);
            }
            mWallpaperPersister.onLiveWallpaperSet(destination);
        } catch (RuntimeException | IOException e) {
            onWallpaperApplyError(activity);
            if (callback != null) {
                callback.onError(e);
            }
        }
    }

    private void setWallpaperComponent(WallpaperManager wallpaperManager,
            LiveWallpaperInfo wallpaper, int destination) throws IOException {
        try {
            Method m = wallpaperManager.getClass().getMethod("setWallpaperComponentWithFlags",
                    ComponentName.class, int.class);
            wallpaperManager.setWallpaperComponentWithFlags(
                    wallpaper.getWallpaperComponent().getComponent(),
                    WallpaperPersister.destinationToFlags(destination));
        } catch (NoSuchMethodException e) {
            Log.d(TAG, "setWallpaperComponentWithFlags not available, using setWallpaperComponent");
            wallpaperManager.setWallpaperComponent(
                    wallpaper.getWallpaperComponent().getComponent());
        }
    }

    /**
     * Sets current live wallpaper to the device (restore case)
     *
     * @param context     The context for initiating wallpaper manager
     * @param wallpaper   Information for the actual wallpaper to set
     * @param destination The wallpaper destination i.e. home vs. lockscreen vs. both
     * @param colors      The {@link WallpaperColors} for placeholder of quickswitching
     * @param callback    Optional callback to be notified when the wallpaper is set.
     */
    public void setCurrentLiveWallpaperFromRestore(Context context, LiveWallpaperInfo wallpaper,
            @Destination final int destination, @Nullable WallpaperColors colors,
            @Nullable SetWallpaperCallback callback) {
        try {
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);
            setWallpaperComponent(wallpaperManager, wallpaper, destination);
            mPreferences.storeLatestWallpaper(WallpaperPersister.destinationToFlags(destination),
                    wallpaper.getWallpaperId(),
                    wallpaper, colors != null ? colors :
                            WallpaperColors.fromBitmap(wallpaper.getThumbAsset(context)
                                    .getLowResBitmap(context)));
            mCurrentWallpaperInfoFactory.clearCurrentWallpaperInfos();
            // Not call onWallpaperApplied() as no UI is presented.
            mUserEventLogger.logWallpaperApplied(
                    wallpaper.getCollectionId(context),
                    wallpaper.getWallpaperId(), wallpaper.getEffectNames(),
                    SET_WALLPAPER_ENTRY_POINT_RESTORE,
                    UserEventLogger.Companion.toWallpaperDestinationForLogging(destination));
            if (callback != null) {
                callback.onSuccess(wallpaper, destination);
            }
            mWallpaperPersister.onLiveWallpaperSet(destination);
        } catch (RuntimeException | IOException e) {
            // Not call onWallpaperApplyError() as no UI is presented.
            if (callback != null) {
                callback.onError(e);
            }
        }
    }

    private void onWallpaperApplied(
            Activity containerActivity,
            WallpaperInfo wallpaper,
            @SetWallpaperEntryPoint int setWallpaperEntryPoint,
            @Destination int destination) {
        mUserEventLogger.logWallpaperApplied(
                wallpaper.getCollectionId(containerActivity),
                wallpaper.getWallpaperId(), wallpaper.getEffectNames(),
                setWallpaperEntryPoint,
                UserEventLogger.Companion.toWallpaperDestinationForLogging(destination));
        mPreferences.setPendingWallpaperSetStatus(
                WallpaperPreferences.WALLPAPER_SET_NOT_PENDING);
        cleanUp();
        restoreScreenOrientationIfNeeded(containerActivity);
    }

    private void onWallpaperApplyError(Activity containerActivity) {
        mPreferences.setPendingWallpaperSetStatus(
                WallpaperPreferences.WALLPAPER_SET_NOT_PENDING);
        cleanUp();
        restoreScreenOrientationIfNeeded(containerActivity);
    }

    /**
     * Call this method to clean up this instance's state.
     */
    public void cleanUp() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    /**
     * Show a dialog asking the user for the Wallpaper's destination
     * (eg, "Home screen", "Lock Screen")
     *
     * @param isLiveWallpaper whether the wallpaper that we want to set is a live wallpaper.
     * @param listener        {@link SetWallpaperDialogFragment.Listener} that will receive the
     *                        response.
     * @param isLockOptionAllowed whether the wallpaper we want to set can be set on lockscreen
     * @param isHomeOptionAllowed whether the wallpaper we want to set can be set on homescreen
     * @see Destination
     */
    public void requestDestination(Activity activity, FragmentManager fragmentManager,
            Listener listener, boolean isLiveWallpaper, boolean isHomeOptionAllowed,
            boolean isLockOptionAllowed) {
        requestDestination(activity, fragmentManager, R.string.set_wallpaper_dialog_message,
                listener, isLiveWallpaper, isHomeOptionAllowed, isLockOptionAllowed);
    }

    /**
     * Show a dialog asking the user for the Wallpaper's destination
     * (eg, "Home screen", "Lock Screen")
     *
     * @param isLiveWallpaper whether the wallpaper that we want to set is a live wallpaper.
     * @param listener        {@link SetWallpaperDialogFragment.Listener} that will receive the
     *                        response.
     * @param titleResId      title for the dialog
     * @param isHomeOption    whether the wallpaper we want to set can be set on homescreen
     * @param isLockOption    whether the wallpaper we want to set can be set on lockscreen
     * @see Destination
     */
    public void requestDestination(Activity activity, FragmentManager fragmentManager,
            @StringRes int titleResId, Listener listener, boolean isLiveWallpaper,
            boolean isHomeOption, boolean isLockOption) {
        saveAndLockScreenOrientationIfNeeded(activity);
        Listener listenerWrapper = new Listener() {
            @Override
            public void onSet(int destination) {
                if (listener != null) {
                    listener.onSet(destination);
                }
            }

            @Override
            public void onDialogDismissed(boolean withItemSelected) {
                if (!withItemSelected) {
                    restoreScreenOrientationIfNeeded(activity);
                }
                if (listener != null) {
                    listener.onDialogDismissed(withItemSelected);
                }
            }
        };

        WallpaperManager wallpaperManager = WallpaperManager.getInstance(activity);
        SetWallpaperDialogFragment setWallpaperDialog = new SetWallpaperDialogFragment();
        setWallpaperDialog.setTitleResId(titleResId);
        setWallpaperDialog.setListener(listenerWrapper);
        if (isLiveWallpaper) {
            setWallpaperDialog.setHomeOptionAvailable(isHomeOption);
            setWallpaperDialog.setLockOptionAvailable(isLockOption);
        }
        setWallpaperDialog.show(fragmentManager, TAG_SET_WALLPAPER_DIALOG_FRAGMENT);
    }

    private void saveAndLockScreenOrientationIfNeeded(Activity activity) {
        if (!mCurrentScreenOrientation.isPresent()) {
            mCurrentScreenOrientation = Optional.of(activity.getRequestedOrientation());
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }
    }

    private void restoreScreenOrientationIfNeeded(Activity activity) {
        mCurrentScreenOrientation.ifPresent(orientation -> {
            if (activity.getRequestedOrientation() != orientation) {
                activity.setRequestedOrientation(orientation);
            }
            mCurrentScreenOrientation = Optional.empty();
        });
    }
}