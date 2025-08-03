/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.app.Activity;
import android.app.WallpaperManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.transition.Transition;

import com.android.settingslib.activityembedding.ActivityEmbeddingUtils;
import com.android.wallpaper.R;
import com.android.wallpaper.config.BaseFlags;
import com.android.wallpaper.model.CustomizationSectionController;
import com.android.wallpaper.model.CustomizationSectionController.CustomizationSectionNavigationController;
import com.android.wallpaper.model.PermissionRequester;
import com.android.wallpaper.model.WallpaperPreviewNavigator;
import com.android.wallpaper.module.CustomizationSections;
import com.android.wallpaper.module.FragmentFactory;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.LargeScreenMultiPanesChecker;
import com.android.wallpaper.picker.customization.ui.binder.CustomizationPickerBinder;
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationPickerViewModel;
import com.android.wallpaper.util.ActivityUtils;
import com.android.wallpaper.util.DisplayUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import kotlinx.coroutines.DisposableHandle;

/** The Fragment UI for customization sections. */
public class CustomizationPickerFragment extends AppbarFragment implements
        CustomizationSectionNavigationController {

    private static final String TAG = "CustomizationPickerFragment";
    private static final String SCROLL_POSITION_Y = "SCROLL_POSITION_Y";
    private static final String KEY_START_FROM_LOCK_SCREEN = "start_from_lock_screen";
    private DisposableHandle mBinding;

    /** Returns a new instance of {@link CustomizationPickerFragment}. */
    public static CustomizationPickerFragment newInstance(boolean startFromLockScreen) {
        final CustomizationPickerFragment fragment = new CustomizationPickerFragment();
        final Bundle args = new Bundle();
        args.putBoolean(KEY_START_FROM_LOCK_SCREEN, startFromLockScreen);
        fragment.setArguments(args);
        return fragment;
    }

    // Note that the section views will be displayed by the list ordering.
    private final List<CustomizationSectionController<?>> mSectionControllers = new ArrayList<>();
    private NestedScrollView mHomeScrollContainer;
    private NestedScrollView mLockScrollContainer;
    @Nullable
    private Bundle mBackStackSavedInstanceState;
    private final FragmentFactory mFragmentFactory;
    @Nullable
    private CustomizationPickerViewModel mViewModel;

    public CustomizationPickerFragment() {
        mFragmentFactory = InjectorProvider.getInjector().getFragmentFactory();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        final int layoutId = R.layout.toolbar_container_layout;
        final View view = inflater.inflate(layoutId, container, false);
        if (ActivityUtils.isLaunchedFromSettingsRelated(getActivity().getIntent())) {
            setUpToolbar(view, !ActivityEmbeddingUtils.shouldHideNavigateUpButton(
                    getActivity(), /* isSecondLayerPage= */ true));
        } else {
            setUpToolbar(view, /* upArrow= */ false);
        }

        final Injector injector = InjectorProvider.getInjector();
        setContentView(view, R.layout.fragment_tabbed_customization_picker);
        mViewModel = new ViewModelProvider(
                this,
                CustomizationPickerViewModel.newFactory(
                        this,
                        savedInstanceState,
                        injector.getUndoInteractor(requireContext(), requireActivity()),
                        injector.getWallpaperInteractor(requireContext()),
                        injector.getUserEventLogger())
        ).get(CustomizationPickerViewModel.class);
        final Bundle arguments = getArguments();
        mViewModel.setInitialScreen(
                arguments != null && arguments.getBoolean(KEY_START_FROM_LOCK_SCREEN));

        setUpToolbarMenu(R.menu.undoable_customization_menu);
        final Bundle finalSavedInstanceState = savedInstanceState;
        if (mBinding != null) {
            mBinding.dispose();
        }
        final List<CustomizationSectionController<?>> lockSectionControllers =
                getSectionControllers(CustomizationSections.Screen.LOCK_SCREEN,
                        finalSavedInstanceState);
        final List<CustomizationSectionController<?>> homeSectionControllers =
                getSectionControllers(CustomizationSections.Screen.HOME_SCREEN,
                        finalSavedInstanceState);
        mSectionControllers.addAll(lockSectionControllers);
        mSectionControllers.addAll(homeSectionControllers);
        mBinding = CustomizationPickerBinder.bind(
                view,
                getToolbarId(),
                mViewModel,
                this,
                isOnLockScreen -> filterAvailableSections(
                        isOnLockScreen ? lockSectionControllers : homeSectionControllers
                ));

        if (mBackStackSavedInstanceState != null) {
            savedInstanceState = mBackStackSavedInstanceState;
            mBackStackSavedInstanceState = null;
        }

        mHomeScrollContainer = view.findViewById(R.id.home_scroll_container);
        mLockScrollContainer = view.findViewById(R.id.lock_scroll_container);

        mHomeScrollContainer.setOnScrollChangeListener(
                (NestedScrollView.OnScrollChangeListener) (scrollView, scrollX, scrollY,
                        oldScrollX, oldScrollY) -> {
                    if (scrollY == 0) {
                        setToolbarColor(android.R.color.transparent);
                    } else {
                        setToolbarColor(R.color.system_surface_container_highest);
                    }
                }
        );
        mLockScrollContainer.setOnScrollChangeListener(
                (NestedScrollView.OnScrollChangeListener) (scrollView, scrollX, scrollY,
                        oldScrollX, oldScrollY) -> {
                    if (scrollY == 0) {
                        setToolbarColor(android.R.color.transparent);
                    } else {
                        setToolbarColor(R.color.system_surface_container_highest);
                    }
                }
        );
        ((ViewGroup) view).setTransitionGroup(true);
        return view;
    }

    private void setContentView(View view, int layoutResId) {
        final ViewGroup parent = view.findViewById(R.id.content_frame);
        if (parent != null) {
            parent.removeAllViews();
        }
        LayoutInflater.from(view.getContext()).inflate(layoutResId, parent);
    }

    private void restoreViewState(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            mHomeScrollContainer.post(() ->
                    mHomeScrollContainer.setScrollY(savedInstanceState.getInt(SCROLL_POSITION_Y)));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        onSaveInstanceStateInternal(savedInstanceState);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected int getToolbarId() {
        return R.id.toolbar;
    }

    @Override
    protected int getToolbarColorId() {
        return android.R.color.transparent;
    }

    @Override
    protected int getToolbarTextColor() {
        return ContextCompat.getColor(requireContext(), R.color.system_on_surface);
    }

    @Override
    public CharSequence getDefaultTitle() {
        return getString(R.string.app_name);
    }

    @Override
    public boolean onBackPressed() {
        // TODO(b/191120122) Improve glitchy animation in Settings.
        if (ActivityUtils.isLaunchedFromSettingsSearch(getActivity().getIntent())) {
            mSectionControllers.forEach(CustomizationSectionController::onTransitionOut);
        }
        return super.onBackPressed();
    }

    @Override
    public void onDestroyView() {
        // When add to back stack, #onDestroyView would be called, but #onDestroy wouldn't. So
        // storing the state in variable to restore when back to foreground. If it's not a back
        // stack case (i,e, config change), the variable would not be retained, see
        // https://developer.android.com/guide/fragments/saving-state.
        mBackStackSavedInstanceState = new Bundle();
        onSaveInstanceStateInternal(mBackStackSavedInstanceState);

        mSectionControllers.forEach(CustomizationSectionController::release);
        mSectionControllers.clear();
        super.onDestroyView();
    }

    @Override
    public void navigateTo(Fragment fragment) {
        prepareFragmentTransitionAnimation();
        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();

        boolean isPageTransitionsFeatureEnabled =
                BaseFlags.get().isPageTransitionsFeatureEnabled(requireContext());

        fragmentManager
                .beginTransaction()
                .setReorderingAllowed(isPageTransitionsFeatureEnabled)
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
        if (!isPageTransitionsFeatureEnabled) {
            fragmentManager.executePendingTransactions();
        }
    }

    @Override
    public void navigateTo(String destinationId) {
        final Fragment fragment = mFragmentFactory.create(destinationId);

        if (fragment != null) {
            navigateTo(fragment);
        }
    }

    @Override
    public void standaloneNavigateTo(String destinationId) {
        final Fragment fragment = mFragmentFactory.create(destinationId);
        prepareFragmentTransitionAnimation();

        boolean isPageTransitionsFeatureEnabled =
                BaseFlags.get().isPageTransitionsFeatureEnabled(requireContext());

        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        fragmentManager
                .beginTransaction()
                .setReorderingAllowed(isPageTransitionsFeatureEnabled)
                .replace(R.id.fragment_container, fragment)
                .commit();
        if (!isPageTransitionsFeatureEnabled) {
            fragmentManager.executePendingTransactions();
        }
    }

    private void prepareFragmentTransitionAnimation() {
        Transition exitTransition = ((Transition) getExitTransition());
        if (exitTransition == null) return;
        exitTransition.addListener(new Transition.TransitionListener() {
            @Override
            public void onTransitionStart(@NonNull Transition transition) {
                setSurfaceViewsVisible(false);
            }

            @Override
            public void onTransitionEnd(@NonNull Transition transition) {
                setSurfaceViewsVisible(true);
            }

            @Override
            public void onTransitionCancel(@NonNull Transition transition) {
                setSurfaceViewsVisible(true);
                // cancelling the transition breaks the preview, therefore recreating the activity
                requireActivity().recreate();
            }

            @Override
            public void onTransitionPause(@NonNull Transition transition) {}

            @Override
            public void onTransitionResume(@NonNull Transition transition) {}
        });
    }

    private void setSurfaceViewsVisible(boolean isVisible) {
        mHomeScrollContainer.findViewById(R.id.preview)
                .setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
        mLockScrollContainer.findViewById(R.id.preview)
                .setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
    }

    /** Saves state of the fragment. */
    private void onSaveInstanceStateInternal(Bundle savedInstanceState) {
        if (mHomeScrollContainer != null) {
            savedInstanceState.putInt(SCROLL_POSITION_Y, mHomeScrollContainer.getScrollY());
        }
        mSectionControllers.forEach(c -> c.onSaveInstanceState(savedInstanceState));
    }

    private List<CustomizationSectionController<?>> getSectionControllers(
            @Nullable CustomizationSections.Screen screen,
            @Nullable Bundle savedInstanceState) {
        final Injector injector = InjectorProvider.getInjector();
        ComponentActivity activity = requireActivity();

        CustomizationSections sections = injector.getCustomizationSections(activity);
        boolean isTwoPaneAndSmallWidth = getIsTwoPaneAndSmallWidth(activity);
        return sections.getSectionControllersForScreen(
                screen,
                getActivity(),
                getViewLifecycleOwner(),
                injector.getWallpaperColorsRepository(),
                getPermissionRequester(),
                getWallpaperPreviewNavigator(),
                this,
                savedInstanceState,
                injector.getCurrentWallpaperInfoFactory(requireContext()),
                injector.getDisplayUtils(activity),
                mViewModel,
                injector.getWallpaperInteractor(requireContext()),
                WallpaperManager.getInstance(requireContext()),
                isTwoPaneAndSmallWidth);
    }

    /** Returns a filtered list containing only the available section controllers. */
    protected List<CustomizationSectionController<?>> filterAvailableSections(
            List<CustomizationSectionController<?>> controllers) {
        return controllers.stream()
                .filter(controller -> {
                    if (controller.isAvailable(getContext())) {
                        return true;
                    } else {
                        controller.release();
                        Log.d(TAG, "Section is not available: " + controller);
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    private PermissionRequester getPermissionRequester() {
        return (PermissionRequester) getActivity();
    }

    private WallpaperPreviewNavigator getWallpaperPreviewNavigator() {
        return (WallpaperPreviewNavigator) getActivity();
    }

    // TODO (b/282237387): Move wallpaper picker out of the 2-pane settings and make it a
    //                     standalone app. Remove this flag when the bug is fixed.
    private boolean getIsTwoPaneAndSmallWidth(Activity activity) {
        DisplayUtils utils = InjectorProvider.getInjector().getDisplayUtils(requireContext());
        LargeScreenMultiPanesChecker multiPanesChecker = new LargeScreenMultiPanesChecker();
        int activityWidth = activity.getDisplay().getWidth();
        int widthThreshold = getResources()
                .getDimensionPixelSize(R.dimen.two_pane_small_width_threshold);
        return utils.isOnWallpaperDisplay(activity)
                && multiPanesChecker.isMultiPanesEnabled(requireContext())
                && activityWidth <= widthThreshold;
    }
}
