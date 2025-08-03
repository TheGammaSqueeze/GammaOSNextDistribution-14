/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.wallpaper.picker;

import static android.app.Activity.RESULT_OK;
import static android.app.WallpaperManager.FLAG_LOCK;
import static android.app.WallpaperManager.FLAG_SYSTEM;
import static android.stats.style.StyleEnums.SET_WALLPAPER_ENTRY_POINT_WALLPAPER_PREVIEW;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import static com.android.wallpaper.module.WallpaperPersister.destinationToFlags;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperColors;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.service.wallpaper.IWallpaperConnection;
import android.service.wallpaper.WallpaperService;
import android.service.wallpaper.WallpaperSettingsActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.android.wallpaper.R;
import com.android.wallpaper.model.CreativeWallpaperInfo;
import com.android.wallpaper.model.SetWallpaperViewModel;
import com.android.wallpaper.model.WallpaperAction;
import com.android.wallpaper.model.WallpaperInfo.ColorInfo;
import com.android.wallpaper.module.WallpaperPersister;
import com.android.wallpaper.util.DeletableUtils;
import com.android.wallpaper.util.ResourceUtils;
import com.android.wallpaper.util.RtlUtils;
import com.android.wallpaper.util.WallpaperConnection;
import com.android.wallpaper.util.WallpaperSurfaceCallback2;
import com.android.wallpaper.widget.FloatingSheet;
import com.android.wallpaper.widget.WallpaperColorsLoader;
import com.android.wallpaper.widget.WallpaperControlButtonGroup;
import com.android.wallpaper.widget.floatingsheetcontent.PreviewCustomizeSettingsContent;
import com.android.wallpaper.widget.floatingsheetcontent.WallpaperActionSelectionBottomSheetContent;
import com.android.wallpaper.widget.floatingsheetcontent.WallpaperActionsToggleAdapter;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Fragment which displays the UI for previewing an individual live wallpaper, its attribution
 * information and settings slices if available.
 */
public class LivePreviewFragment extends PreviewFragment {

    public static final String EXTRA_LIVE_WALLPAPER_INFO = "android.live_wallpaper.info";
    public static final String KEY_ACTION_DELETE_LIVE_WALLPAPER = "action_delete_live_wallpaper";

    private static final String TAG = "LivePreviewFragment";
    private static final String KEY_TOOLBAR_GONE = "toolbar_gone";
    private static final ExecutorService sExecutorService = Executors.newCachedThreadPool();
    private ActivityResultLauncher<Void> mSettingsActivityResult;
    private ActivityResultLauncher<Void> mShareActivityResult;
    private Intent mSettingsActivityIntent;
    private WallpaperActionSelectionBottomSheetContent mWallpaperActionSelectionBottomSheetContent;
    private OnBackPressedCallback mSettingsOnBackPressedCallback;
    private boolean mHasCalledOnSaveInstanceState = false;
    private boolean mHideOverlaysForShowingSettingsActivity = false;
    private Future<ColorInfo> mColorFuture;
    /**
     * Instance of {@link WallpaperConnection} used to bind to the live wallpaper service to show
     * it in this preview fragment.
     *
     * @see IWallpaperConnection
     */
    private WallpaperConnection mWallpaperConnection;
    private WallpaperSurfaceCallback2 mWallpaperSurfaceCallback;
    private SurfaceView mWallpaperSurface;

    @Override
    protected void setWallpaper(int destination) {
        WallpaperPersister.SetWallpaperCallback callback = SetWallpaperViewModel.getCallback(
                mViewModelProvider);
        if (mWallpaperConnection != null) {
            try {
                mWallpaperConnection.setWallpaperFlags(destinationToFlags(destination));
            } catch (RemoteException e) {
                callback.onError(e);
                return;
            }
        }
        mWallpaperSetter.setCurrentWallpaper(
                getActivity(),
                mWallpaper,
                null,
                SET_WALLPAPER_ENTRY_POINT_WALLPAPER_PREVIEW,
                destination,
                0,
                null,
                mWallpaperColors != null ? mWallpaperColors : getColorInfo().getWallpaperColors(),
                callback);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = requireContext();
        mColorFuture = mWallpaper.computeColorInfo(context);
        mSettingsActivityIntent = getSettingsActivityIntent(getContext(),
                mWallpaper.getWallpaperComponent());
        mSettingsOnBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                launchSettingsAsOverlay(/* isLaunched= */ false);
            }
        };
        if (mWallpaper instanceof CreativeWallpaperInfo) {
            mSettingsActivityResult = registerForActivityResult(
                    CreativeWallpaperInfo.getContract(mSettingsActivityIntent),
                    getCreativeWallpaperPreviewResultCallback());
            CreativeWallpaperInfo creativeWallpaper = (CreativeWallpaperInfo) mWallpaper;
            if (creativeWallpaper.canBeShared()) {
                mShareActivityResult = registerForActivityResult(CreativeWallpaperInfo.getContract(
                                creativeWallpaper.getShareIntent()),
                        unused -> mWallpaperControlButtonGroup.setChecked(
                                WallpaperControlButtonGroup.SHARE, /* checked= */ false));
            }
        }
    }

    private ActivityResultCallback<Integer> getCreativeWallpaperPreviewResultCallback() {
        return result -> {
            CreativeWallpaperInfo creativeWallpaper = (CreativeWallpaperInfo) mWallpaper;
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(getContext());
            boolean isCreativeWallpaperApplied = creativeWallpaper.isApplied(
                    wallpaperManager.getWallpaperInfo(FLAG_SYSTEM),
                    wallpaperManager.getWallpaperInfo(FLAG_LOCK));
            if (result == RESULT_OK) {
                if (creativeWallpaper.canBeDeleted() || isCreativeWallpaperApplied) {
                    // When editing an existing wallpaper and pressing "Done" button causing the
                    // overlays to become visible
                    showOverlays();
                    overrideOnBackPressed(new OnBackPressedCallback(true) {
                        @Override
                        public void handleOnBackPressed() {
                            getActivity().finish();
                        }
                    });
                } else {
                    // When in the process of creating a new wallpaper and pressing "Done" button.
                    showOverlays();
                    overrideOnBackPressed(mSettingsOnBackPressedCallback);
                }
            } else {
                // When you initiate the editing process for a wallpaper and then decide to exit
                // by pressing the back button during editing.
                if (creativeWallpaper.canBeDeleted() || isCreativeWallpaperApplied) {
                    showOverlays();
                } else {
                    // Flow where user opens a template (so the settings activity is launched)
                    // but user just simply presses back (without moving on to the next screen)
                    // TODO: This should ideally be a slide transition, but custom slide transition
                    // does not work properly, so having a fade transition for now
                    finishActivityWithFadeTransition();
                }
            }
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        if (view == null) {
            return null;
        }
        // Live wallpaper surface
        Context context = requireContext();
        mWallpaperSurface = view.findViewById(R.id.wallpaper_surface);
        mWallpaperSurfaceCallback = new WallpaperSurfaceCallback2(context, mWallpaperSurface,
                mColorFuture, this::previewLiveWallpaper);
        mWallpaperSurface.getHolder().addCallback(mWallpaperSurfaceCallback);
        mWallpaperSurface.setZOrderMediaOverlay(true);
        setUpLiveWallpaperTouchForwarding(mTouchForwardingLayout);
        showLiveWallpaperControl();
        return view;
    }

    private WallpaperActionsToggleAdapter.WallpaperEffectSwitchListener getEffectSwitchListener(
            Context context, CreativeWallpaperInfo creativeWallpaper) {
        return (checkedItem) -> {
            for (WallpaperAction wallpaperActionToggle : creativeWallpaper.getEffectsToggles()) {
                wallpaperActionToggle.setToggled(false);
            }

            if (checkedItem >= 0) {
                WallpaperAction currentEffect =
                        creativeWallpaper.getEffectsToggles().get(
                                checkedItem);
                currentEffect.setToggled(true);
                creativeWallpaper
                        .setCurrentlyAppliedEffectId(
                                currentEffect.getEffectId());
                creativeWallpaper.applyEffect(context,
                        currentEffect.getApplyActionUri());
                mWallpaperActionSelectionBottomSheetContent.setCurrentlyAppliedEffect(
                        currentEffect.getEffectId());
            } else {
                creativeWallpaper.setCurrentlyAppliedEffectId(null);
                creativeWallpaper.clearEffects(context);
                mWallpaperActionSelectionBottomSheetContent.setCurrentlyAppliedEffect(null);
            }
        };
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setUpLiveWallpaperTouchForwarding(TouchForwardingLayout touchForwardingLayout) {
        touchForwardingLayout.setTargetView(mWallpaperSurface);
        touchForwardingLayout.setForwardingEnabled(true);
        touchForwardingLayout.setOnClickListener(v -> {
            toggleWallpaperPreviewControl();
            mTouchForwardingLayout.announceForAccessibility(
                    getString(mPreviewScrim.getVisibility() == View.VISIBLE
                            ? R.string.show_preview_controls_content_description
                            : R.string.hide_preview_controls_content_description)
            );
        });
        mWallpaperSurface.setOnTouchListener((v, ev) -> {
            dispatchTouchEventOnLiveWallpaperSurface(ev);
            return false;
        });
    }

    private void dispatchTouchEventOnLiveWallpaperSurface(MotionEvent ev) {
        if (mWallpaperConnection != null && mWallpaperConnection.getEngine() != null) {
            int action = ev.getActionMasked();
            MotionEvent dup = MotionEvent.obtainNoHistory(ev);
            dup.setLocation(ev.getX(), ev.getY());
            try {
                mWallpaperConnection.getEngine().dispatchPointer(dup);
                if (action == MotionEvent.ACTION_UP) {
                    mWallpaperConnection.getEngine().dispatchWallpaperCommand(
                            WallpaperManager.COMMAND_TAP,
                            (int) ev.getX(), (int) ev.getY(), 0, null);
                } else if (action == MotionEvent.ACTION_POINTER_UP) {
                    int pointerIndex = ev.getActionIndex();
                    mWallpaperConnection.getEngine().dispatchWallpaperCommand(
                            WallpaperManager.COMMAND_SECONDARY_TAP,
                            (int) ev.getX(pointerIndex), (int) ev.getY(pointerIndex), 0,
                            null);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Remote exception of wallpaper connection");
            }
        }
    }

    protected void showLiveWallpaperControl() {
        mSetWallpaperButton.setVisibility(VISIBLE);
        if (mWallpaper instanceof CreativeWallpaperInfo) {
            CreativeWallpaperInfo creativeWallpaper = (CreativeWallpaperInfo) mWallpaper;
            mWallpaperControlButtonGroup.showButton(
                    WallpaperControlButtonGroup.EDIT,
                    (buttonView, isChecked) -> {
                        if (isChecked) {
                            launchSettingsAsOverlay(/* isLaunched= */ false);
                        }
                        mWallpaperControlButtonGroup.setChecked(
                                WallpaperControlButtonGroup.EDIT, false);
                    });
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(getContext());
            boolean isCreativeWallpaperApplied = creativeWallpaper.isApplied(
                    wallpaperManager.getWallpaperInfo(FLAG_SYSTEM),
                    wallpaperManager.getWallpaperInfo(FLAG_LOCK));
            if (!isCreativeWallpaperApplied && creativeWallpaper.canBeDeleted()) {
                mWallpaperControlButtonGroup.showButton(
                        WallpaperControlButtonGroup.DELETE,
                        (buttonView, isChecked) -> {
                            if (isChecked) {
                                showDeleteConfirmDialog(
                                        () -> creativeWallpaper.requestDelete(requireContext()));
                            }
                        });
            } else {
                mWallpaperControlButtonGroup.hideButton(WallpaperControlButtonGroup.DELETE);
            }
            if (creativeWallpaper.canBeShared() && mShareActivityResult != null) {
                mWallpaperControlButtonGroup.showButton(
                        WallpaperControlButtonGroup.SHARE,
                        (buttonView, isChecked) -> {
                            if (isChecked) {
                                mShareActivityResult.launch(null);
                            }
                        });
            }

            if (creativeWallpaper.doesSupportWallpaperEffects()) {
                showWallpaperEffectsButton();
            }
        } else if (DeletableUtils.canBeDeleted(requireContext(),
                mWallpaper.getWallpaperComponent())) {
            mWallpaperControlButtonGroup.showButton(WallpaperControlButtonGroup.DELETE,
                    (buttonView, isChecked) -> {
                        if (isChecked) {
                            showDeleteConfirmDialog(() -> {
                                        Context context = getContext();
                                        if (context != null) {
                                            DeletableUtils.deleteLiveWallpaper(context, mWallpaper);
                                        }
                                    }
                            );
                        }
                    });
        }
        WallpaperInfo info = mWallpaper.getWallpaperComponent();
        Uri uriSettingsSlice = getSettingsSliceUri(info);
        if (uriSettingsSlice != null) {
            mFloatingSheet.putFloatingSheetContent(FloatingSheet.CUSTOMIZE,
                    new PreviewCustomizeSettingsContent(requireContext(), uriSettingsSlice));
            mWallpaperControlButtonGroup.showButton(WallpaperControlButtonGroup.CUSTOMIZE,
                    getFloatingSheetControlButtonChangeListener(
                            WallpaperControlButtonGroup.CUSTOMIZE, FloatingSheet.CUSTOMIZE));
        } else if (mSettingsActivityIntent != null
                && !(mWallpaper instanceof CreativeWallpaperInfo)) {
            mWallpaperControlButtonGroup.showButton(
                    WallpaperControlButtonGroup.CUSTOMIZE,
                    (buttonView, isChecked) -> startActivity(mSettingsActivityIntent));
        }
        // update button group top margin
        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) mWallpaperControlButtonGroup.getLayoutParams();
        params.topMargin = getResources().getDimensionPixelSize(
                R.dimen.wallpaper_control_button_group_margin_top);
        mWallpaperControlButtonGroup.requestLayout();
    }

    private void showWallpaperEffectsButton() {
        mWallpaperControlButtonGroup.showButton(WallpaperControlButtonGroup.EFFECTS,
                getFloatingSheetControlButtonChangeListener(WallpaperControlButtonGroup.EFFECTS,
                        FloatingSheet.EFFECTS));
    }

    private void launchSettingsAsOverlay(boolean isLaunched) {
        hideOverlays();
        mSettingsOnBackPressedCallback.remove();
        if (!isLaunched) {
            mSettingsActivityResult.launch(null);
        }
    }

    private void hideOverlays() {
        mToolbar.setVisibility(GONE);
        mSetWallpaperButton.setVisibility(GONE);
        mWallpaperControlButtonGroup.setVisibility(GONE);
        // remove callback to prevent overlay from showing again after floating sheet collapses
        mFloatingSheet.removeFloatingSheetCallback(mShowOverlayOnHideFloatingSheetCallback);
        mFloatingSheet.setVisibility(GONE);
        // deselects all control buttons and sets floating sheet to collapse
        mWallpaperControlButtonGroup.deselectAllFloatingSheetControlButtons();
        hideScreenPreviewOverlay(true);
        mHideOverlaysForShowingSettingsActivity = true;
    }

    private void showOverlays() {
        mToolbar.setVisibility(VISIBLE);
        mSetWallpaperButton.setVisibility(VISIBLE);
        mWallpaperControlButtonGroup.setVisibility(VISIBLE);
        mFloatingSheet.addFloatingSheetCallback(mShowOverlayOnHideFloatingSheetCallback);
        mFloatingSheet.setVisibility(VISIBLE);
        hideScreenPreviewOverlay(false);
        mHideOverlaysForShowingSettingsActivity = false;
    }

    protected void overrideOnBackPressed(OnBackPressedCallback onBackPressedCallback) {
        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }
        activity.getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                onBackPressedCallback
        );
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        mHasCalledOnSaveInstanceState = true;
        outState.putBoolean(KEY_TOOLBAR_GONE, mToolbar.getVisibility() == GONE);
        super.onSaveInstanceState(outState);
    }

    private void initializeEffectButton(CreativeWallpaperInfo creativeWallpaper, Context context) {
        if (!creativeWallpaper.doesSupportWallpaperEffects()) {
            return;
        }

        sExecutorService.execute(() -> {
            ArrayList<WallpaperAction> effects = creativeWallpaper.getWallpaperEffects(context);
            if (effects == null) {
                return;
            }

            creativeWallpaper.setEffectsToggles(effects);
            mWallpaperActionSelectionBottomSheetContent =
                    createWallpaperActionSelectionBottomSheetContent(context, creativeWallpaper);
            mFloatingSheet.putFloatingSheetContent(FloatingSheet.EFFECTS,
                    mWallpaperActionSelectionBottomSheetContent);
        });
    }

    private WallpaperActionSelectionBottomSheetContent
            createWallpaperActionSelectionBottomSheetContent(Context context,
            CreativeWallpaperInfo creativeWallpaper) {
        return new WallpaperActionSelectionBottomSheetContent(context,
                creativeWallpaper.getEffectsBottomSheetTitle(),
                creativeWallpaper.getEffectsBottomSheetSubtitle(),
                creativeWallpaper.getClearActionsUri(),
                creativeWallpaper.getEffectsToggles(),
                creativeWallpaper.getCurrentlyAppliedEffectId(),
                getEffectSwitchListener(context, creativeWallpaper));
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        mHasCalledOnSaveInstanceState = false;
        if (mWallpaper instanceof CreativeWallpaperInfo) {
            Context context = requireContext();
            CreativeWallpaperInfo creativeWallpaper = (CreativeWallpaperInfo) mWallpaper;
            boolean isSettingsActivityPresent = savedInstanceState != null
                    && savedInstanceState.getBoolean(KEY_TOOLBAR_GONE, false);
            initializeEffectButton(creativeWallpaper, context);
            if (savedInstanceState == null) {
                // First time at Fragment should initialize wallpaper preview.
                creativeWallpaper.initializeWallpaperPreview(context);
            }

            if (savedInstanceState == null || isSettingsActivityPresent) {
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);
                boolean isCreativeWallpaperApplied = creativeWallpaper.isApplied(
                        wallpaperManager.getWallpaperInfo(FLAG_SYSTEM),
                        wallpaperManager.getWallpaperInfo(FLAG_LOCK));
                // First time at Fragment or settings activity is at present.
                if (!isCreativeWallpaperApplied && !creativeWallpaper.canBeDeleted()) {
                    // If it cannot be deleted, we must be creating a new one, launch settings.
                    // savedInstanceState != null means is rotate state and previous fragment
                    // already launch settings.
                    launchSettingsAsOverlay(/* isLaunched= */ savedInstanceState != null);
                } else if (isSettingsActivityPresent) {
                    hideOverlays();
                    mSettingsOnBackPressedCallback.remove();
                }

            } else {
                showOverlays();
                overrideOnBackPressed(mSettingsOnBackPressedCallback);
            }
        }
        super.onViewStateRestored(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        // Clean up the surface view
        if (mWallpaperConnection != null) {
            mWallpaperConnection.destroy();
            mWallpaperConnection = null;
        }
        mWallpaperSurfaceCallback.cleanUp();
        mWallpaperSurface.getHolder().removeCallback(mWallpaperSurfaceCallback);

        if (mWallpaper instanceof CreativeWallpaperInfo) {
            if (!mHasCalledOnSaveInstanceState) {
                // onDestroy without rotation should clean up wallpaper preview.
                ((CreativeWallpaperInfo) mWallpaper).cleanUpWallpaperPreview(getContext());
            }
        }
        super.onDestroy();
    }

    private void previewLiveWallpaper() {
        mWallpaperSurface.post(() -> {
            ImageView homeImageWallpaper = mWallpaperSurfaceCallback.getHomeImageWallpaper();
            if (homeImageWallpaper != null) {
                loadPreviewImage(homeImageWallpaper);
            }
            setUpLiveWallpaperPreview(mWallpaper);
        });
    }

    protected void loadPreviewImage(ImageView homeImageWallpaper) {
        Context context = getContext();
        Context appContext = context != null ? context.getApplicationContext() : null;
        Activity activity = getActivity();
        if (activity == null || appContext == null) {
            return;
        }
        mWallpaperSurfaceCallback.setHomeImageWallpaperBlur(true);
        ColorInfo colorInfo = getColorInfo();
        Integer placeholderColor = colorInfo.getPlaceholderColor();
        // This is for showing a lower resolution image before the live wallpaper shows
        WallpaperPreviewBitmapTransformation transformation =
                new WallpaperPreviewBitmapTransformation(appContext, RtlUtils.isRtl(context));
        mWallpaper.getThumbAsset(activity.getApplicationContext())
                .loadLowResDrawable(activity,
                        homeImageWallpaper,
                        placeholderColor != null
                                ? placeholderColor
                                : ResourceUtils.getColorAttr(activity,
                                        android.R.attr.colorBackground),
                        transformation);
    }

    private ColorInfo getColorInfo() {
        try {
            return mColorFuture.get(50, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Log.i(TAG, "Couldn't obtain placeholder color", e);
        }
        return new ColorInfo(new WallpaperColors(Color.valueOf(Color.TRANSPARENT),
                /* secondaryColor= */ null, /* tertiaryColor= */ null),
                /* placeholderColor= */ null);
    }

    protected void setUpLiveWallpaperPreview(
            com.android.wallpaper.model.WallpaperInfo homeWallpaper) {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }

        if (mWallpaperConnection != null) {
            mWallpaperConnection.disconnect();
        }

        if (WallpaperConnection.isPreviewAvailable()) {
            android.app.WallpaperInfo info = homeWallpaper.getWallpaperComponent();
            Intent wallpaperIntent = new Intent(WallpaperService.SERVICE_INTERFACE)
                    .setClassName(info.getPackageName(), info.getServiceName());
            mWallpaperConnection = new WallpaperConnection(
                    wallpaperIntent,
                    activity,
                    new WallpaperConnection.WallpaperConnectionListener() {
                        @Override
                        public void onEngineShown() {
                            Activity activity = getActivity();
                            if (activity == null) {
                                return;
                            }
                            ImageView homeImageWallpaper =
                                    mWallpaperSurfaceCallback.getHomeImageWallpaper();
                            if (homeImageWallpaper != null) {
                                homeImageWallpaper.animate()
                                        .setStartDelay(250)
                                        .setDuration(250)
                                        .alpha(0f)
                                        .setInterpolator(PreviewFragment.ALPHA_OUT)
                                        .start();
                            }
                        }

                        @Override
                        public void onWallpaperColorsChanged(WallpaperColors colors,
                                int displayId) {
                            LivePreviewFragment.super.onWallpaperColorsChanged(colors);
                        }
                    },
                    mWallpaperSurface,
                    null,
                    mIsViewAsHome ? FLAG_SYSTEM : FLAG_LOCK,
                    mIsAssetIdPresent ? WallpaperConnection.WhichPreview.EDIT_NON_CURRENT
                            : WallpaperConnection.WhichPreview.EDIT_CURRENT);
            mWallpaperConnection.setVisibility(true);
        } else {
            WallpaperColorsLoader.getWallpaperColors(
                    activity,
                    homeWallpaper.getThumbAsset(activity),
                    this::onWallpaperColorsChanged);
        }

        if (mWallpaperConnection != null && !mWallpaperConnection.connect()) {
            mWallpaperConnection = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mWallpaperConnection != null) {
            mWallpaperConnection.setVisibility(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mWallpaperConnection != null) {
            mWallpaperConnection.setVisibility(mHideOverlaysForShowingSettingsActivity);
        }
    }

    protected void showDeleteConfirmDialog(Runnable deleteRunnable) {
        final AlertDialog alertDialog = new AlertDialog.Builder(getContext())
                .setMessage(R.string.delete_wallpaper_confirmation)
                .setOnDismissListener(dialog -> mWallpaperControlButtonGroup.setChecked(
                        WallpaperControlButtonGroup.DELETE, false))
                .setPositiveButton(R.string.delete_live_wallpaper,
                        (dialog, which) -> {
                            deleteRunnable.run();
                            finishActivityWithFadeTransition();
                        })
                .setNegativeButton(android.R.string.cancel, null /* listener */)
                .create();
        alertDialog.show();
    }

    @Nullable
    private Intent getSettingsActivityIntent(Context context, WallpaperInfo info) {
        String settingsActivity = info.getSettingsActivity();
        if (context == null || settingsActivity == null) {
            return null;
        }
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(info.getPackageName(), settingsActivity));
        intent.putExtra(WallpaperSettingsActivity.EXTRA_PREVIEW_MODE, true);
        PackageManager pm = context.getPackageManager();
        ActivityInfo activityInfo = intent.resolveActivityInfo(pm, 0);
        if (activityInfo == null) {
            Log.i(TAG, "Couldn't find wallpaper settings activity: " + settingsActivity);
            return null;
        }
        return intent;
    }

    private Uri getSettingsSliceUri(android.app.WallpaperInfo info) {
        return info.getSettingsSliceUri();
    }
}
