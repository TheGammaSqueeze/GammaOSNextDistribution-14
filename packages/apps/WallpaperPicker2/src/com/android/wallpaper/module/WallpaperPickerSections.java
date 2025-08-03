package com.android.wallpaper.module;

import android.app.WallpaperManager;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;

import com.android.wallpaper.model.CustomizationSectionController;
import com.android.wallpaper.model.CustomizationSectionController.CustomizationSectionNavigationController;
import com.android.wallpaper.model.PermissionRequester;
import com.android.wallpaper.model.WallpaperPreviewNavigator;
import com.android.wallpaper.picker.customization.data.repository.WallpaperColorsRepository;
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor;
import com.android.wallpaper.picker.customization.ui.section.ScreenPreviewSectionController;
import com.android.wallpaper.picker.customization.ui.section.WallpaperQuickSwitchSectionController;
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationPickerViewModel;
import com.android.wallpaper.util.DisplayUtils;

import java.util.ArrayList;
import java.util.List;

/** {@link CustomizationSections} for the wallpaper picker. */
public final class WallpaperPickerSections implements CustomizationSections {

    @Override
    public List<CustomizationSectionController<?>> getSectionControllersForScreen(
            Screen screen,
            FragmentActivity activity,
            LifecycleOwner lifecycleOwner,
            WallpaperColorsRepository wallpaperColorsRepository,
            PermissionRequester permissionRequester,
            WallpaperPreviewNavigator wallpaperPreviewNavigator,
            CustomizationSectionNavigationController sectionNavigationController,
            @Nullable Bundle savedInstanceState,
            CurrentWallpaperInfoFactory wallpaperInfoFactory,
            DisplayUtils displayUtils,
            CustomizationPickerViewModel customizationPickerViewModel,
            WallpaperInteractor wallpaperInteractor,
            WallpaperManager wallpaperManager,
            boolean isTwoPaneAndSmallWidth) {
        List<CustomizationSectionController<?>> sectionControllers = new ArrayList<>();

        sectionControllers.add(
                new ScreenPreviewSectionController(
                        activity,
                        lifecycleOwner,
                        screen,
                        wallpaperInfoFactory,
                        wallpaperColorsRepository,
                        displayUtils,
                        wallpaperPreviewNavigator,
                        wallpaperInteractor,
                        wallpaperManager,
                        isTwoPaneAndSmallWidth,
                        customizationPickerViewModel));
        sectionControllers.add(
                new WallpaperQuickSwitchSectionController(
                        customizationPickerViewModel.getWallpaperQuickSwitchViewModel(screen),
                        lifecycleOwner,
                        sectionNavigationController,
                        savedInstanceState == null));

        return sectionControllers;
    }
}
