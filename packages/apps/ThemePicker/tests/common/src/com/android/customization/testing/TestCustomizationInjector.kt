package com.android.customization.testing

import android.app.WallpaperColors
import android.content.Context
import android.content.res.Resources
import androidx.activity.ComponentActivity
import com.android.customization.model.color.WallpaperColorResources
import com.android.customization.module.CustomizationInjector
import com.android.customization.module.CustomizationPreferences
import com.android.customization.module.logging.ThemesUserEventLogger
import com.android.customization.picker.clock.domain.interactor.ClockPickerInteractor
import com.android.customization.picker.clock.ui.view.ClockViewFactory
import com.android.customization.picker.clock.ui.viewmodel.ClockCarouselViewModel
import com.android.customization.picker.clock.ui.viewmodel.ClockSettingsViewModel
import com.android.customization.picker.color.domain.interactor.ColorPickerInteractor
import com.android.customization.picker.color.ui.viewmodel.ColorPickerViewModel
import com.android.customization.picker.quickaffordance.domain.interactor.KeyguardQuickAffordancePickerInteractor
import com.android.systemui.shared.clocks.ClockRegistry
import com.android.wallpaper.module.logging.UserEventLogger
import com.android.wallpaper.picker.customization.data.repository.WallpaperColorsRepository
import com.android.wallpaper.testing.TestInjector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class TestCustomizationInjector
@Inject
constructor(
    private val customPrefs: TestDefaultCustomizationPreferences,
    private val themesUserEventLogger: ThemesUserEventLogger
) : TestInjector(themesUserEventLogger), CustomizationInjector {
    /////////////////
    // CustomizationInjector implementations
    /////////////////

    override fun getCustomizationPreferences(context: Context): CustomizationPreferences {
        return customPrefs
    }

    override fun getKeyguardQuickAffordancePickerInteractor(
        context: Context
    ): KeyguardQuickAffordancePickerInteractor {
        throw UnsupportedOperationException("not implemented")
    }

    override fun getClockRegistry(context: Context): ClockRegistry? {
        throw UnsupportedOperationException("not implemented")
    }

    override fun getClockPickerInteractor(context: Context): ClockPickerInteractor {
        throw UnsupportedOperationException("not implemented")
    }

    override fun getWallpaperColorResources(
        wallpaperColors: WallpaperColors,
        context: Context
    ): WallpaperColorResources {
        throw UnsupportedOperationException("not implemented")
    }

    override fun getColorPickerInteractor(
        context: Context,
        wallpaperColorsRepository: WallpaperColorsRepository,
    ): ColorPickerInteractor {
        throw UnsupportedOperationException("not implemented")
    }

    override fun getColorPickerViewModelFactory(
        context: Context,
        wallpaperColorsRepository: WallpaperColorsRepository,
    ): ColorPickerViewModel.Factory {
        throw UnsupportedOperationException("not implemented")
    }

    override fun getClockCarouselViewModelFactory(
        interactor: ClockPickerInteractor,
        clockViewFactory: ClockViewFactory,
        resources: Resources,
    ): ClockCarouselViewModel.Factory {
        throw UnsupportedOperationException("not implemented")
    }

    override fun getClockViewFactory(activity: ComponentActivity): ClockViewFactory {
        throw UnsupportedOperationException("not implemented")
    }

    override fun getClockSettingsViewModelFactory(
        context: Context,
        wallpaperColorsRepository: WallpaperColorsRepository,
        clockViewFactory: ClockViewFactory,
    ): ClockSettingsViewModel.Factory {
        throw UnsupportedOperationException("not implemented")
    }

    /////////////////
    // TestInjector overrides
    /////////////////

    override fun getUserEventLogger(): UserEventLogger {
        return themesUserEventLogger
    }
}
