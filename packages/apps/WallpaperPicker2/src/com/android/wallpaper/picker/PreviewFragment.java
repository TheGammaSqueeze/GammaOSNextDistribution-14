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

import static android.view.View.VISIBLE;

import static com.android.wallpaper.util.LaunchSourceUtils.LAUNCH_SOURCE_LAUNCHER;
import static com.android.wallpaper.util.LaunchSourceUtils.LAUNCH_SOURCE_SETTINGS_HOMEPAGE;
import static com.android.wallpaper.util.LaunchSourceUtils.WALLPAPER_LAUNCH_SOURCE;
import static com.android.wallpaper.widget.FloatingSheet.INFORMATION;

import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperColors;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.Toolbar;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwnerKt;
import androidx.lifecycle.ViewModelProvider;

import com.android.wallpaper.R;
import com.android.wallpaper.model.LiveWallpaperInfo;
import com.android.wallpaper.model.SetWallpaperViewModel;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.WallpaperPersister.Destination;
import com.android.wallpaper.module.WallpaperSetter;
import com.android.wallpaper.module.logging.UserEventLogger;
import com.android.wallpaper.util.PreviewUtils;
import com.android.wallpaper.util.ResourceUtils;
import com.android.wallpaper.widget.DuoTabs;
import com.android.wallpaper.widget.FloatingSheet;
import com.android.wallpaper.widget.WallpaperControlButtonGroup;
import com.android.wallpaper.widget.floatingsheetcontent.WallpaperInfoContent;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.transition.MaterialSharedAxis;

import java.util.List;

import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Dispatchers;

/**
 * Base Fragment to display the UI for previewing an individual wallpaper.
 */
public abstract class PreviewFragment extends Fragment implements WallpaperColorThemePreview {

    public static final Interpolator ALPHA_OUT = new PathInterpolator(0f, 0f, 0.8f, 1f);

    /**
     * User can view wallpaper and attributions in full screen, but "Set wallpaper" button is
     * hidden.
     */
    public static final int MODE_VIEW_ONLY = 0;

    /**
     * User can view wallpaper and attributions in full screen and click "Set wallpaper" to set the
     * wallpaper with pan and crop position to the device.
     */
    public static final int MODE_CROP_AND_SET_WALLPAPER = 1;

    /**
     * Possible preview modes for the fragment.
     */
    @IntDef({
            MODE_VIEW_ONLY,
            MODE_CROP_AND_SET_WALLPAPER})
    public @interface PreviewMode {
    }

    public static final String ARG_IS_NEW_TASK = "is_new_task";
    public static final String ARG_IS_ASSET_ID_PRESENT = "is_asset_id_present";
    public static final String ARG_WALLPAPER = "wallpaper";
    public static final String ARG_VIEW_AS_HOME = "view_as_home";

    private static final String TAG = "PreviewFragment";

    protected WallpaperInfo mWallpaper;
    protected WallpaperSetter mWallpaperSetter;
    protected ViewModelProvider mViewModelProvider;
    protected WallpaperColors mWallpaperColors;
    protected UserEventLogger mUserEventLogger;
    private SetWallpaperViewModel mSetWallpaperViewModel;

    // UI
    private SurfaceView mWorkspaceSurface;
    private WorkspaceSurfaceHolderCallback mWorkspaceSurfaceCallback;
    private SurfaceView mLockSurface;
    private WorkspaceSurfaceHolderCallback mLockSurfaceCallback;
    private View mHideFloatingSheetTouchLayout;
    private DuoTabs mOverlayTabs;
    private @DuoTabs.Tab int mInitSelectedTab;
    private View mExitFullPreviewButton;
    protected View mSetWallpaperButton;
    protected FrameLayout mSetWallpaperButtonContainer;
    protected View mPreviewScrim;
    protected Toolbar mToolbar;
    protected WallpaperControlButtonGroup mWallpaperControlButtonGroup;
    protected FloatingSheet mFloatingSheet;
    protected TouchForwardingLayout mTouchForwardingLayout;

    protected ProgressBar mProgressBar;

    protected boolean mIsViewAsHome;

    /**
     * We create an instance of WallpaperInfo from CurrentWallpaperInfo when a user taps on
     * the preview of a wallpapers in the wallpaper picker main screen. However, there are
     * other instances as well in which an instance of the specific WallpaperInfo is created. This
     * variable is used in order to identify whether the instance created has an assetId or not.
     * This is needed for restricting the destination where a wallpaper can be set after editing
     * it.
     */
    protected boolean mIsAssetIdPresent;

    /**
     * True if the activity of this fragment is launched with {@link Intent#FLAG_ACTIVITY_NEW_TASK}.
     */
    private boolean mIsNewTask;

    // The system "short" animation time duration, in milliseconds. This
    // duration is ideal for subtle animations or animations that occur
    // very frequently.
    private int mShortAnimTimeMillis;

    private final BottomSheetBehavior.BottomSheetCallback mStandardFloatingSheetCallback =
            new BottomSheetBehavior.BottomSheetCallback() {
                @Override
                public void onStateChanged(@androidx.annotation.NonNull View bottomSheet,
                        int newState) {
                    if (newState == STATE_EXPANDED) {
                        mHideFloatingSheetTouchLayout.setVisibility(View.VISIBLE);
                        mTouchForwardingLayout.setVisibility(View.GONE);
                    }
                    if (newState == STATE_HIDDEN) {
                        mWallpaperControlButtonGroup.deselectAllFloatingSheetControlButtons();
                        mHideFloatingSheetTouchLayout.setVisibility(View.GONE);
                        mTouchForwardingLayout.setVisibility(VISIBLE);
                        mTouchForwardingLayout.requestFocus();
                    }
                }

                @Override
                public void onSlide(@androidx.annotation.NonNull View bottomSheet,
                        float slideOffset) {
                }
            };

    protected final BottomSheetBehavior.BottomSheetCallback
            mShowOverlayOnHideFloatingSheetCallback =
            new BottomSheetBehavior.BottomSheetCallback() {
                @Override
                public void onStateChanged(@androidx.annotation.NonNull View bottomSheet,
                        int newState) {
                    if (newState == STATE_HIDDEN) {
                        hideScreenPreviewOverlay(/* hide= */false);
                    }
                }

                @Override
                public void onSlide(@androidx.annotation.NonNull View bottomSheet,
                        float slideOffset) {
                }
            };

    /**
     * Sets current wallpaper to the device based on current zoom and scroll state.
     *
     * @param destination The wallpaper destination i.e. home vs. lockscreen vs. both.
     */
    protected abstract void setWallpaper(@Destination int destination);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = requireArguments();
        mWallpaper = args.getParcelable(ARG_WALLPAPER);
        mIsViewAsHome = args.getBoolean(ARG_VIEW_AS_HOME);
        mIsAssetIdPresent = args.getBoolean(ARG_IS_ASSET_ID_PRESENT);
        mIsNewTask = args.getBoolean(ARG_IS_NEW_TASK);
        mInitSelectedTab = mIsViewAsHome ? DuoTabs.TAB_SECONDARY : DuoTabs.TAB_PRIMARY;
        Context appContext = requireContext().getApplicationContext();
        Injector injector = InjectorProvider.getInjector();

        mUserEventLogger = injector.getUserEventLogger();
        mWallpaperSetter = new WallpaperSetter(injector.getWallpaperPersister(appContext),
                injector.getPreferences(appContext), mUserEventLogger,
                injector.getCurrentWallpaperInfoFactory(appContext));
        mViewModelProvider = new ViewModelProvider(requireActivity());
        mSetWallpaperViewModel = mViewModelProvider.get(SetWallpaperViewModel.class);
        mSetWallpaperViewModel.getStatus().observe(requireActivity(), setWallpaperStatus -> {
            switch (setWallpaperStatus) {
                case SUCCESS:
                    onSetWallpaperSuccess();
                    break;
                case ERROR:
                    showSetWallpaperErrorDialog();
                    break;
                default:
                    // Do nothing when UNKNOWN or PENDING
            }
        });

        mShortAnimTimeMillis = getResources().getInteger(android.R.integer.config_shortAnimTime);
        setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.X, /* forward */ true));
        setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.X, /* forward */ false));
        setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.X, /* forward */ true));
        setReenterTransition(new MaterialSharedAxis(MaterialSharedAxis.X, /* forward */ false));

    }

    @Override
    @CallSuper
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_wallpaper_preview, container, false);
        // Progress indicator
        mProgressBar = view.findViewById(R.id.action_progress);
        // Toolbar
        mToolbar = view.findViewById(R.id.toolbar);
        setUpToolbar();
        // TouchForwardingLayout
        mTouchForwardingLayout = view.findViewById(R.id.touch_forwarding_layout);
        mTouchForwardingLayout.setOnClickAccessibilityDescription(
                R.string.hide_preview_controls_action);
        // Preview overlay
        mWorkspaceSurface = view.findViewById(R.id.workspace_surface);
        mWorkspaceSurfaceCallback = new WorkspaceSurfaceHolderCallback(
                mWorkspaceSurface,
                new PreviewUtils(
                        requireContext(),
                        getString(R.string.grid_control_metadata_name)),
                shouldApplyWallpaperColors());
        // Hide the work space's bottom row initially to avoid overlapping with the overlay tabs.
        mWorkspaceSurfaceCallback.setHideBottomRow(true);
        mLockSurface = view.findViewById(R.id.lock_screen_overlay_surface);
        mLockSurfaceCallback = new WorkspaceSurfaceHolderCallback(
                mLockSurface,
                new PreviewUtils(
                        requireContext().getApplicationContext(),
                        null,
                        getString(R.string.lock_screen_preview_provider_authority)),
                shouldApplyWallpaperColors());
        setUpScreenPreviewOverlay();
        // Set wallpaper button
        mSetWallpaperButtonContainer = view.findViewById(R.id.button_set_wallpaper_container);
        mSetWallpaperButton = view.findViewById(R.id.button_set_wallpaper);
        mSetWallpaperButtonContainer.setOnClickListener(
                v -> showDestinationSelectionDialogForWallpaper(mWallpaper));
        // Overlay tabs
        mOverlayTabs = view.findViewById(R.id.overlay_tabs);
        mOverlayTabs.setTabText(getString(R.string.lock_screen_message),
                getString(R.string.home_screen_message));
        mOverlayTabs.setOnTabSelectedListener(this::updateScreenPreviewOverlay);
        mOverlayTabs.selectTab(mInitSelectedTab);
        // Floating sheet and button control group
        mFloatingSheet = view.findViewById(R.id.floating_sheet);
        mHideFloatingSheetTouchLayout = view.findViewById(R.id.hide_floating_sheet_touch_layout);
        mWallpaperControlButtonGroup = view.findViewById(R.id.wallpaper_control_button_group);
        boolean shouldShowInformationFloatingSheet = shouldShowInformationFloatingSheet(mWallpaper);
        setUpFloatingSheet(requireContext(), shouldShowInformationFloatingSheet);
        if (shouldShowInformationFloatingSheet) {
            mWallpaperControlButtonGroup.showButton(
                    WallpaperControlButtonGroup.INFORMATION,
                    getFloatingSheetControlButtonChangeListener(
                            WallpaperControlButtonGroup.INFORMATION,
                            INFORMATION));
        }
        mPreviewScrim = view.findViewById(R.id.preview_scrim);
        mExitFullPreviewButton = view.findViewById(R.id.exit_full_preview_button);
        mExitFullPreviewButton.setOnClickListener(v -> toggleWallpaperPreviewControl());
        return view;
    }

    private void setUpToolbar() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        mToolbar.setTitle(R.string.preview);
        mToolbar.setTitleTextColor(getResources().getColor(R.color.preview_toolbar_text_light));
        mToolbar.setBackgroundResource(android.R.color.transparent);
        activity.getWindow().setStatusBarColor(
                getResources().getColor(android.R.color.transparent));
        activity.getWindow().setNavigationBarColor(
                getResources().getColor(android.R.color.transparent));

        // The hosting activity needs to implement AppbarFragment.AppbarFragmentHost
        AppbarFragment.AppbarFragmentHost host = (AppbarFragment.AppbarFragmentHost) activity;
        if (host.isUpArrowSupported()) {
            mToolbar.setNavigationIcon(getToolbarBackIcon());
            mToolbar.setNavigationContentDescription(R.string.bottom_action_bar_back);
            mToolbar.setNavigationOnClickListener(view -> {
                host.onUpArrowPressed();
            });
        }
    }

    @Nullable
    private Drawable getToolbarBackIcon() {
        Drawable backIcon = ResourcesCompat.getDrawable(getResources(),
                R.drawable.material_ic_arrow_back_black_24,
                null);
        if (backIcon == null) {
            return null;
        }
        backIcon.setAutoMirrored(true);
        backIcon.setTint(getResources().getColor(R.color.preview_toolbar_text_light));
        return backIcon;
    }

    private void setUpScreenPreviewOverlay() {
        int placeHolderColor = ResourceUtils.getColorAttr(requireContext(),
                android.R.attr.colorBackground);
        mWorkspaceSurface.setResizeBackgroundColor(placeHolderColor);
        mWorkspaceSurface.setZOrderMediaOverlay(true);
        mWorkspaceSurface.getHolder().addCallback(mWorkspaceSurfaceCallback);
        mLockSurface.setResizeBackgroundColor(placeHolderColor);
        mLockSurface.setZOrderMediaOverlay(true);
        mLockSurface.getHolder().addCallback(mLockSurfaceCallback);
    }

    private boolean shouldShowInformationFloatingSheet(WallpaperInfo wallpaperInfo) {
        List<String> attributions = wallpaperInfo.getAttributions(requireContext());
        String actionUrl = wallpaperInfo.getActionUrl(requireContext());
        boolean showAttribution = false;
        for (String attr : attributions) {
            if (attr != null && !attr.isEmpty()) {
                showAttribution = true;
                break;
            }
        }
        boolean showActionUrl = actionUrl != null && !actionUrl.isEmpty();
        return showAttribution || showActionUrl;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setUpFloatingSheet(Context context, boolean shouldShowInformationFloatingSheet) {
        setHideFloatingSheetLayoutAccessibilityAction();
        mHideFloatingSheetTouchLayout.setContentDescription(
                getString(R.string.preview_screen_description));
        mHideFloatingSheetTouchLayout.setOnClickListener(v -> mFloatingSheet.collapse());
        mHideFloatingSheetTouchLayout.setVisibility(View.GONE);
        mFloatingSheet.addFloatingSheetCallback(mStandardFloatingSheetCallback);
        mFloatingSheet.addFloatingSheetCallback(mShowOverlayOnHideFloatingSheetCallback);
        if (shouldShowInformationFloatingSheet) {
            mFloatingSheet.putFloatingSheetContent(INFORMATION,
                    new WallpaperInfoContent(context, mWallpaper));
        }
    }

    protected CompoundButton.OnCheckedChangeListener getFloatingSheetControlButtonChangeListener(
            @WallpaperControlButtonGroup.WallpaperControlType int wallpaperType,
            @FloatingSheet.Companion.FloatingSheetContentType int floatingSheetType) {
        return (buttonView, isChecked) -> {
            if (isChecked) {
                mWallpaperControlButtonGroup.deselectOtherFloatingSheetControlButtons(
                        wallpaperType);
                if (mFloatingSheet.isFloatingSheetCollapsed()) {
                    if (floatingSheetType == INFORMATION) {
                        hideScreenPreviewOverlayKeepScrim();
                    } else {
                        hideScreenPreviewOverlay(/* hide= */true);
                    }
                    mFloatingSheet.updateContentView(floatingSheetType);
                    mFloatingSheet.expand();
                } else {
                    mFloatingSheet.updateContentViewWithAnimation(floatingSheetType);
                }
            } else {
                if (!mWallpaperControlButtonGroup.isFloatingSheetControlButtonSelected()) {
                    mFloatingSheet.collapse();
                }
            }
        };
    }

    private void setHideFloatingSheetLayoutAccessibilityAction() {
        ViewCompat.setAccessibilityDelegate(mHideFloatingSheetTouchLayout,
                new AccessibilityDelegateCompat() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host,
                        AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    CharSequence description = host.getResources().getString(
                            R.string.hide_wallpaper_info_action);
                    AccessibilityActionCompat clickAction = new AccessibilityActionCompat(
                            AccessibilityNodeInfoCompat.ACTION_CLICK, description);
                    info.addAction(clickAction);
                }
            });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mWallpaperSetter != null) {
            mWallpaperSetter.cleanUp();
        }
        if (mWorkspaceSurfaceCallback != null) {
            mWorkspaceSurfaceCallback.cleanUp();
        }
        if (mLockSurfaceCallback != null) {
            mLockSurfaceCallback.cleanUp();
        }
    }

    protected void onWallpaperColorsChanged(@Nullable WallpaperColors colors) {
        //  Early return to not block the instrumentation test.
        if (InjectorProvider.getInjector().isInstrumentationTest()) {
            return;
        }
        if (!shouldApplyWallpaperColors()) {
            return;
        }
        mWallpaperColors = colors;
        Context context = getContext();
        if (context == null || colors == null) {
            return;
        }
        // Apply the wallpaper color resources to the fragment context. So the views created by
        // the context will apply the given wallpaper color.
        BuildersKt.launch(
                LifecycleOwnerKt.getLifecycleScope(getViewLifecycleOwner()),
                Dispatchers.getIO(),
                CoroutineStart.DEFAULT,
                (coroutineScope, continuation) ->
                        InjectorProvider.getInjector().getWallpaperColorResources(colors,
                                context).apply(
                                    context,
                                    () -> {
                                        requireActivity().runOnUiThread(() -> {
                                            mSetWallpaperButton.setBackground(null);
                                            mSetWallpaperButton.setBackgroundResource(
                                                    R.drawable.set_wallpaper_button_background);
                                            mExitFullPreviewButton.setForeground(
                                                    AppCompatResources.getDrawable(context,
                                                            R.drawable.exit_full_preview_cross));
                                            mWallpaperControlButtonGroup.updateBackgroundColor();
                                            mOverlayTabs.updateBackgroundColor();
                                            mFloatingSheet.setColor(context);
                                        });
                                        return null;
                                    }, continuation
                        )
        );

        // Update the color theme for the home screen overlay
        updateWorkspacePreview(mWorkspaceSurface, mWorkspaceSurfaceCallback, colors,
                /* hideBottomRow= */ mOverlayTabs.getVisibility() == VISIBLE);
        // Update the color theme for the lock screen overlay
        updateWorkspacePreview(mLockSurface, mLockSurfaceCallback, colors,
                /* hideBottomRow= */ mOverlayTabs.getVisibility() == VISIBLE);
    }

    private void updateScreenPreviewOverlay(@DuoTabs.Tab int tab) {
        if (mWorkspaceSurface != null) {
            mWorkspaceSurface.setVisibility(
                    tab == DuoTabs.TAB_SECONDARY ? View.VISIBLE : View.INVISIBLE);
            mWorkspaceSurface.setZOrderMediaOverlay(tab == DuoTabs.TAB_SECONDARY);
        }
        if (mLockSurface != null) {
            mLockSurface.setVisibility(
                    tab == DuoTabs.TAB_PRIMARY ? View.VISIBLE : View.INVISIBLE);
            mLockSurface.setZOrderMediaOverlay(tab == DuoTabs.TAB_PRIMARY);
        }
    }

    protected void toggleWallpaperPreviewControl() {
        boolean wasVisible = mPreviewScrim.getVisibility() == VISIBLE;
        mTouchForwardingLayout.setOnClickAccessibilityDescription(
                wasVisible ? R.string.show_preview_controls_action
                        : R.string.hide_preview_controls_action);
        animateWallpaperPreviewControl(wasVisible);
    }

    private void animateWallpaperPreviewControl(boolean hide) {
        // When hiding the preview control, we should show the workspace bottom row components
        hideBottomRow(!hide);
        mPreviewScrim.animate()
                .alpha(hide ? 0f : 1f)
                .setDuration(mShortAnimTimeMillis)
                .setListener(new ViewAnimatorListener(mPreviewScrim, hide));
        mWallpaperControlButtonGroup.animate().alpha(hide ? 0f : 1f)
                .setDuration(mShortAnimTimeMillis)
                .setListener(new ViewAnimatorListener(mWallpaperControlButtonGroup, hide));
        mOverlayTabs.animate().alpha(hide ? 0f : 1f)
                .setDuration(mShortAnimTimeMillis)
                .setListener(new ViewAnimatorListener(mOverlayTabs, hide));
        mSetWallpaperButtonContainer.animate().alpha(hide ? 0f : 1f)
                .setDuration(mShortAnimTimeMillis)
                .setListener(new ViewAnimatorListener(mSetWallpaperButtonContainer, hide));
        mToolbar.animate().alpha(hide ? 0f : 1f)
                .setDuration(mShortAnimTimeMillis)
                .setListener(new ViewAnimatorListener(mToolbar, hide));
        // The show and hide of the button is the opposite of the wallpaper preview control
        mExitFullPreviewButton.animate().alpha(!hide ? 0f : 1f)
                .setDuration(mShortAnimTimeMillis)
                .setListener(new ViewAnimatorListener(mExitFullPreviewButton, !hide));
    }

    private void hideBottomRow(boolean hide) {
        if (mWorkspaceSurfaceCallback != null) {
            Bundle data = new Bundle();
            data.putBoolean(WorkspaceSurfaceHolderCallback.KEY_HIDE_BOTTOM_ROW, hide);
            mWorkspaceSurfaceCallback.send(WorkspaceSurfaceHolderCallback.MESSAGE_ID_UPDATE_PREVIEW,
                    data);
        }
    }

    protected void hideScreenPreviewOverlay(boolean hide) {
        mPreviewScrim.setVisibility(hide ? View.INVISIBLE : View.VISIBLE);
        mOverlayTabs.setVisibility(hide ? View.INVISIBLE : View.VISIBLE);
        boolean isLockSelected = mOverlayTabs.getSelectedTab() == DuoTabs.TAB_PRIMARY;
        if (isLockSelected) {
            mLockSurface.setVisibility(hide ? View.INVISIBLE : View.VISIBLE);
            mLockSurface.setZOrderMediaOverlay(!hide);
        } else {
            mWorkspaceSurface.setVisibility(hide ? View.INVISIBLE : View.VISIBLE);
            mWorkspaceSurface.setZOrderMediaOverlay(!hide);
        }
    }

    /**
     * Hides or shows the overlay but leaves the scrim always visible.
     */
    private void hideScreenPreviewOverlayKeepScrim() {
        mPreviewScrim.setVisibility(VISIBLE);
        mOverlayTabs.setVisibility(View.INVISIBLE);
        boolean isLockSelected = mOverlayTabs.getSelectedTab() == DuoTabs.TAB_PRIMARY;
        SurfaceView targetSurface = isLockSelected ? mLockSurface : mWorkspaceSurface;
        targetSurface.setVisibility(View.INVISIBLE);
        targetSurface.setZOrderMediaOverlay(false);
    }

    protected void onSetWallpaperSuccess() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        try {
            Toast.makeText(activity, R.string.wallpaper_set_successfully_message,
                    Toast.LENGTH_SHORT).show();
        } catch (NotFoundException e) {
            Log.e(TAG, "Could not show toast " + e);
        }
        activity.setResult(Activity.RESULT_OK);
        finishActivityWithFadeTransition();

        // Start activity to go back to main screen.
        if (mIsNewTask) {
            Intent intent = new Intent(requireActivity(), TrampolinePickerActivity.class);
            intent.putExtra(WALLPAPER_LAUNCH_SOURCE,
                    mIsViewAsHome ? LAUNCH_SOURCE_LAUNCHER : LAUNCH_SOURCE_SETTINGS_HOMEPAGE);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    protected void finishActivityWithFadeTransition() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        activity.finish();
    }

    private void showDestinationSelectionDialogForWallpaper(WallpaperInfo wallpaperInfo) {

        // This logic is implemented for the editing of live wallpapers. The purpose is to
        // restrict users to set the edited creative wallpaper only to the destination from
        // where they originally started the editing process. For instance, if they began editing
        // by clicking on the homescreen preview, they would be allowed to set the wallpaper on the
        // homescreen and both the homescreen and lockscreen. On the other hand, if they initiated
        // editing by clicking on the lockscreen preview, they would only be allowed to set the
        // wallpaper on the lockscreen and both the homescreen and lockscreen. It's essential to
        // note that this restriction only applies when the editing process is started by tapping
        // on the preview available on the wallpaper picker home page.
        boolean isLockOption = true;
        boolean isHomeOption = true;
        if (wallpaperInfo instanceof LiveWallpaperInfo) {
            if (!mIsAssetIdPresent) {
                isHomeOption = mIsViewAsHome;
                isLockOption = !mIsViewAsHome;
            }
        }

        mWallpaperSetter.requestDestination(getActivity(), getParentFragmentManager(),
                destination -> {
                    mSetWallpaperViewModel.setDestination(destination);
                    setWallpaper(destination);
                },
                wallpaperInfo instanceof LiveWallpaperInfo, isHomeOption, isLockOption);
    }

    protected void showSetWallpaperErrorDialog() {
        new AlertDialog.Builder(getActivity(), R.style.LightDialogTheme)
                .setMessage(R.string.set_wallpaper_error_message)
                .setPositiveButton(R.string.try_again, (dialogInterface, i) ->
                        setWallpaper(mSetWallpaperViewModel.getDestination())
                )
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show();
    }

    protected void showLoadWallpaperErrorDialog() {
        new AlertDialog.Builder(getActivity(), R.style.LightDialogTheme)
                .setMessage(R.string.load_wallpaper_error_message)
                .setPositiveButton(android.R.string.ok,
                        (dialogInterface, i) -> finishFragmentActivity())
                .setOnDismissListener(dialog -> finishFragmentActivity())
                .create()
                .show();
    }

    private void finishFragmentActivity() {
        FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.finish();
        }
    }

    private static class ViewAnimatorListener extends AnimatorListenerAdapter {
        final View mView;
        final boolean mHide;

        private ViewAnimatorListener(View view, boolean hide) {
            mView = view;
            mHide = hide;
        }

        @Override
        public void onAnimationStart(Animator animation) {
            mView.setVisibility(VISIBLE);
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            mView.setVisibility(mHide ? View.INVISIBLE : VISIBLE);
        }
    }
}
